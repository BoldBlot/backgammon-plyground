/*
 * vk_mlp.c -- see vk_mlp.h.
 * Copyright (C) 2026 Bold Blot.
 *
 * One compute pipeline (mlp.comp) run once per layer; the activations ping-pong between two device
 * buffers. The rows go up in a host-visible buffer and the raw outputs come back in another.
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <vulkan/vulkan.h>

#include "vk_mlp.h"
#include "mlp_spv.h"

#define MAXL 9

#include <time.h>
static double ms_now(void) { struct timespec ts; clock_gettime(CLOCK_MONOTONIC, &ts); return ts.tv_sec * 1e3 + ts.tv_nsec / 1e6; }

typedef struct { VkBuffer b; VkDeviceMemory m; VkDeviceSize size; void *map; } buf;

struct vkmlp {
    VkInstance inst;
    VkPhysicalDevice pd;
    VkDevice dev;
    VkQueue q;
    uint32_t qf;
    buf w, xs, x, a, b, y;
    VkDescriptorSetLayout dsl;
    VkPipelineLayout pl;
    VkShaderModule sm;
    VkPipeline pipe;
    VkDescriptorPool dp;
    VkDescriptorSet ds[MAXL];
    VkCommandPool cp;
    VkCommandBuffer cb;
    VkFence fence;
    VkQueryPool qp;
    double t_layer[MAXL];
    double tperiod, t_in, t_copy, t_net, t_wait, t_out; /* ms: host copy in, GPU copy, GPU layers, submit to done, host copy out */
    uint32_t lutoff, rt, ot;
    int nl, in0, max_rows, din[MAXL], dout[MAXL];
    uint32_t woff[MAXL], boff[MAXL];
    char name[256];
};

typedef struct { uint32_t rows, nin, nout, relu, woff, boff, packed, lutoff; } push;

#define CK(call) do { VkResult r_ = (call); if (r_ != VK_SUCCESS) { snprintf(err, errlen, "%s failed (%d)", #call, r_); goto fail; } } while (0)

static int mem_type(VkPhysicalDevice pd, uint32_t bits, VkMemoryPropertyFlags want) {
    VkPhysicalDeviceMemoryProperties mp;
    vkGetPhysicalDeviceMemoryProperties(pd, &mp);
    for (uint32_t i = 0; i < mp.memoryTypeCount; i++)
        if ((bits & (1u << i)) && (mp.memoryTypes[i].propertyFlags & want) == want) return (int)i;
    return -1;
}

static int make_buf(vkmlp *m, buf *o, VkDeviceSize size, VkBufferUsageFlags use, VkMemoryPropertyFlags want, VkMemoryPropertyFlags fallback) {
    VkBufferCreateInfo bi = {VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO, .size = size, .usage = use, .sharingMode = VK_SHARING_MODE_EXCLUSIVE};
    if (vkCreateBuffer(m->dev, &bi, NULL, &o->b) != VK_SUCCESS) return -1;
    VkMemoryRequirements mr;
    vkGetBufferMemoryRequirements(m->dev, o->b, &mr);
    int t = mem_type(m->pd, mr.memoryTypeBits, want);
    if (t < 0) t = mem_type(m->pd, mr.memoryTypeBits, fallback);
    if (t < 0) return -1;
    VkMemoryAllocateInfo ai = {VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO, .allocationSize = mr.size, .memoryTypeIndex = (uint32_t)t};
    if (vkAllocateMemory(m->dev, &ai, NULL, &o->m) != VK_SUCCESS) return -1;
    if (vkBindBufferMemory(m->dev, o->b, o->m, 0) != VK_SUCCESS) return -1;
    o->size = size;
    if (want & VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT)
        if (vkMapMemory(m->dev, o->m, 0, VK_WHOLE_SIZE, 0, &o->map) != VK_SUCCESS) return -1;
    return 0;
}

static void free_buf(vkmlp *m, buf *o) {
    if (o->map) vkUnmapMemory(m->dev, o->m);
    if (o->b) vkDestroyBuffer(m->dev, o->b, NULL);
    if (o->m) vkFreeMemory(m->dev, o->m, NULL);
    memset(o, 0, sizeof *o);
}

static int submit_wait(vkmlp *m) {
    VkSubmitInfo si = {VK_STRUCTURE_TYPE_SUBMIT_INFO, .commandBufferCount = 1, .pCommandBuffers = &m->cb};
    if (vkResetFences(m->dev, 1, &m->fence) != VK_SUCCESS) return -1;
    if (vkQueueSubmit(m->q, 1, &si, m->fence) != VK_SUCCESS) return -1;
    if (vkWaitForFences(m->dev, 1, &m->fence, VK_TRUE, UINT64_MAX) != VK_SUCCESS) return -1;
    return 0;
}

vkmlp *vkmlp_create(int n_layers, int in_size, const int *dim_out, float *const *wt, float *const *bias,
                    int max_rows, int device, int use_fma, char *err, int errlen) {
    vkmlp *m = calloc(1, sizeof *m);
    buf stage = {0};
    if (n_layers < 1 || n_layers > MAXL) { snprintf(err, errlen, "bad layer count"); goto fail; }
    m->nl = n_layers; m->in0 = in_size; m->max_rows = max_rows;
    VkApplicationInfo app = {VK_STRUCTURE_TYPE_APPLICATION_INFO, .pApplicationName = "vkgames", .apiVersion = VK_API_VERSION_1_1};
    VkInstanceCreateInfo ii = {VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO, .pApplicationInfo = &app};
    CK(vkCreateInstance(&ii, NULL, &m->inst));
    uint32_t nd = 0;
    CK(vkEnumeratePhysicalDevices(m->inst, &nd, NULL));
    if (!nd) { snprintf(err, errlen, "no Vulkan device"); goto fail; }
    VkPhysicalDevice pds[16];
    if (nd > 16) nd = 16;
    CK(vkEnumeratePhysicalDevices(m->inst, &nd, pds));
    int pick = -1;
    if (device >= 0 && device < (int)nd) pick = device;
    for (uint32_t i = 0; pick < 0 && i < nd; i++) {
        VkPhysicalDeviceProperties pp; vkGetPhysicalDeviceProperties(pds[i], &pp);
        if (pp.deviceType == VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU) pick = (int)i;
    }
    if (pick < 0) pick = 0;
    m->pd = pds[pick];
    VkPhysicalDeviceProperties pp; vkGetPhysicalDeviceProperties(m->pd, &pp);
    snprintf(m->name, sizeof m->name, "%s", pp.deviceName);
    m->tperiod = pp.limits.timestampPeriod;
    uint32_t nq = 0; vkGetPhysicalDeviceQueueFamilyProperties(m->pd, &nq, NULL);
    VkQueueFamilyProperties qp[32]; if (nq > 32) nq = 32;
    vkGetPhysicalDeviceQueueFamilyProperties(m->pd, &nq, qp);
    m->qf = UINT32_MAX;
    for (uint32_t i = 0; i < nq; i++) if (qp[i].queueFlags & VK_QUEUE_COMPUTE_BIT) { m->qf = i; break; }
    if (m->qf == UINT32_MAX) { snprintf(err, errlen, "no compute queue"); goto fail; }
    float prio = 1.0f;
    VkDeviceQueueCreateInfo qi = {VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO, .queueFamilyIndex = m->qf, .queueCount = 1, .pQueuePriorities = &prio};
    VkDeviceCreateInfo di = {VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO, .queueCreateInfoCount = 1, .pQueueCreateInfos = &qi};
    CK(vkCreateDevice(m->pd, &di, NULL, &m->dev));
    vkGetDeviceQueue(m->dev, m->qf, 0, &m->q);

    /* weights: [layer] wt then bias, uploaded once through a staging buffer */
    size_t total = 0; int in = in_size, max_dim = in_size;
    for (int l = 0; l < n_layers; l++) {
        m->din[l] = in; m->dout[l] = dim_out[l];
        m->woff[l] = (uint32_t)total; total += (size_t)in * dim_out[l];
        m->boff[l] = (uint32_t)total; total += dim_out[l];
        if (dim_out[l] > max_dim) max_dim = dim_out[l];
        in = dim_out[l];
    }
    m->lutoff = (uint32_t)total; total += VKMLP_CODES;
    VkMemoryPropertyFlags HV = VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
    VkMemoryPropertyFlags DL = VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT;
    VkBufferUsageFlags SB = VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
    if (make_buf(m, &m->w, total * 4, SB | VK_BUFFER_USAGE_TRANSFER_DST_BIT, DL, 0) ||
        make_buf(m, &stage, total * 4, VK_BUFFER_USAGE_TRANSFER_SRC_BIT, HV, HV) ||
        make_buf(m, &m->xs, (VkDeviceSize)max_rows * in_size * 4, VK_BUFFER_USAGE_TRANSFER_SRC_BIT, HV, HV) ||
        make_buf(m, &m->x, (VkDeviceSize)max_rows * in_size * 4, SB | VK_BUFFER_USAGE_TRANSFER_DST_BIT, DL, 0) ||
        make_buf(m, &m->a, (VkDeviceSize)max_rows * max_dim * 4, SB, DL, 0) ||
        make_buf(m, &m->b, (VkDeviceSize)max_rows * max_dim * 4, SB, DL, 0) ||
        make_buf(m, &m->y, (VkDeviceSize)max_rows * dim_out[n_layers - 1] * 4, SB, HV | VK_MEMORY_PROPERTY_HOST_CACHED_BIT, HV)) {
        snprintf(err, errlen, "buffer allocation failed"); goto fail;
    }
    for (int l = 0; l < n_layers; l++) {
        memcpy((float *)stage.map + m->woff[l], wt[l], (size_t)m->din[l] * m->dout[l] * 4);
        memcpy((float *)stage.map + m->boff[l], bias[l], (size_t)m->dout[l] * 4);
    }
    {
        float lut[VKMLP_CODES];
        vkmlp_code_table(lut);
        memcpy((float *)stage.map + m->lutoff, lut, sizeof lut);
    }

    VkDescriptorSetLayoutBinding bd[3];
    for (int i = 0; i < 3; i++) bd[i] = (VkDescriptorSetLayoutBinding){.binding = (uint32_t)i, .descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, .descriptorCount = 1, .stageFlags = VK_SHADER_STAGE_COMPUTE_BIT};
    VkDescriptorSetLayoutCreateInfo dli = {VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO, .bindingCount = 3, .pBindings = bd};
    CK(vkCreateDescriptorSetLayout(m->dev, &dli, NULL, &m->dsl));
    VkPushConstantRange pr = {VK_SHADER_STAGE_COMPUTE_BIT, 0, sizeof(push)};
    VkPipelineLayoutCreateInfo pli = {VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO, .setLayoutCount = 1, .pSetLayouts = &m->dsl, .pushConstantRangeCount = 1, .pPushConstantRanges = &pr};
    CK(vkCreatePipelineLayout(m->dev, &pli, NULL, &m->pl));
    const char *tile = getenv("VKG_TILE");
    int v = 0;
    if (!tile) tile = pp.vendorID == 0x10DE ? "88" : "48"; /* NVIDIA: 8 x 8; elsewhere 8 x 8 spills (Adreno 830: 0.04 against 0.32 M rows/s) */
    for (int i = 0; i < (int)(sizeof mlp_variants / sizeof mlp_variants[0]); i++)
        if (tile && !strcmp(tile, mlp_variants[i].name)) v = i;
    m->rt = 16 * mlp_variants[v].tm; m->ot = 16 * mlp_variants[v].tn;
    VkShaderModuleCreateInfo smi = {VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO, .codeSize = mlp_variants[v].size, .pCode = mlp_variants[v].code};
    CK(vkCreateShaderModule(m->dev, &smi, NULL, &m->sm));
    VkBool32 fma = use_fma ? VK_TRUE : VK_FALSE;
    VkSpecializationMapEntry me = {0, 0, sizeof fma};
    VkSpecializationInfo spi = {1, &me, sizeof fma, &fma};
    VkComputePipelineCreateInfo ci = {VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO,
        .stage = {VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO, .stage = VK_SHADER_STAGE_COMPUTE_BIT, .module = m->sm, .pName = "main", .pSpecializationInfo = &spi},
        .layout = m->pl};
    CK(vkCreateComputePipelines(m->dev, VK_NULL_HANDLE, 1, &ci, NULL, &m->pipe));
    VkDescriptorPoolSize ps = {VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, 3 * MAXL};
    VkDescriptorPoolCreateInfo dpi = {VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO, .maxSets = MAXL, .poolSizeCount = 1, .pPoolSizes = &ps};
    CK(vkCreateDescriptorPool(m->dev, &dpi, NULL, &m->dp));
    VkDescriptorSetLayout ls[MAXL];
    for (int l = 0; l < n_layers; l++) ls[l] = m->dsl;
    VkDescriptorSetAllocateInfo dai = {VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO, .descriptorPool = m->dp, .descriptorSetCount = (uint32_t)n_layers, .pSetLayouts = ls};
    CK(vkAllocateDescriptorSets(m->dev, &dai, m->ds));
    for (int l = 0; l < n_layers; l++) {
        const buf *src = l == 0 ? &m->x : (l % 2 ? &m->a : &m->b);
        const buf *dst = l == n_layers - 1 ? &m->y : (l % 2 ? &m->b : &m->a);
        VkDescriptorBufferInfo bi[3] = {{m->w.b, 0, VK_WHOLE_SIZE}, {src->b, 0, VK_WHOLE_SIZE}, {dst->b, 0, VK_WHOLE_SIZE}};
        VkWriteDescriptorSet wr[3];
        for (int i = 0; i < 3; i++) wr[i] = (VkWriteDescriptorSet){VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET, .dstSet = m->ds[l], .dstBinding = (uint32_t)i, .descriptorCount = 1, .descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, .pBufferInfo = &bi[i]};
        vkUpdateDescriptorSets(m->dev, 3, wr, 0, NULL);
    }
    VkCommandPoolCreateInfo cpi = {VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO, .flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT, .queueFamilyIndex = m->qf};
    CK(vkCreateCommandPool(m->dev, &cpi, NULL, &m->cp));
    VkCommandBufferAllocateInfo cai = {VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO, .commandPool = m->cp, .level = VK_COMMAND_BUFFER_LEVEL_PRIMARY, .commandBufferCount = 1};
    CK(vkAllocateCommandBuffers(m->dev, &cai, &m->cb));
    VkFenceCreateInfo fi = {VK_STRUCTURE_TYPE_FENCE_CREATE_INFO};
    CK(vkCreateFence(m->dev, &fi, NULL, &m->fence));
    VkQueryPoolCreateInfo qpi = {VK_STRUCTURE_TYPE_QUERY_POOL_CREATE_INFO, .queryType = VK_QUERY_TYPE_TIMESTAMP, .queryCount = 3 + MAXL};
    CK(vkCreateQueryPool(m->dev, &qpi, NULL, &m->qp));

    VkCommandBufferBeginInfo bgi = {VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO, .flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT};
    CK(vkBeginCommandBuffer(m->cb, &bgi));
    VkBufferCopy cp = {0, 0, total * 4};
    vkCmdCopyBuffer(m->cb, stage.b, m->w.b, 1, &cp);
    CK(vkEndCommandBuffer(m->cb));
    if (submit_wait(m)) { snprintf(err, errlen, "weight upload failed"); goto fail; }
    free_buf(m, &stage);
    return m;
fail:
    if (m->dev) free_buf(m, &stage);
    vkmlp_free(m);
    return NULL;
}

void vkmlp_code_table(float *lut) {
    for (int k = 0; k < VKMLP_CODES; k++) lut[k] = 0.0f;
    for (int k = 0; k < 32; k++) lut[k] = k * 0.5f;
    for (int j = 0; j <= 15; j++) lut[32 + j] = j / 15.0f;
}

static int forward(vkmlp *m, const void *X, int packed, long n, float *Y);
int vkmlp_forward(vkmlp *m, const float *X, long n, float *Y) { return forward(m, X, 0, n, Y); }
int vkmlp_forward_codes(vkmlp *m, const uint8_t *X, long n, float *Y) { return (m->in0 & 3) ? -1 : forward(m, X, 1, n, Y); }

static int forward(vkmlp *m, const void *X, int packed, long n, float *Y) {
    size_t rb = packed ? (size_t)m->in0 : (size_t)m->in0 * 4; /* bytes per input row */
    int last = m->dout[m->nl - 1];
    for (long r0 = 0; r0 < n; r0 += m->max_rows) {
        uint32_t rows = (uint32_t)(n - r0 < m->max_rows ? n - r0 : m->max_rows);
        double c0 = ms_now();
        memcpy(m->xs.map, (const char *)X + (size_t)r0 * rb, (size_t)rows * rb);
        double c1 = ms_now();
        VkCommandBufferBeginInfo bgi = {VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO, .flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT};
        if (vkBeginCommandBuffer(m->cb, &bgi) != VK_SUCCESS) return -1;
        vkCmdResetQueryPool(m->cb, m->qp, 0, 3 + MAXL);
        vkCmdWriteTimestamp(m->cb, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, m->qp, 0);
        VkBufferCopy xc = {0, 0, (VkDeviceSize)rows * rb};
        vkCmdCopyBuffer(m->cb, m->xs.b, m->x.b, 1, &xc);
        VkMemoryBarrier tb = {VK_STRUCTURE_TYPE_MEMORY_BARRIER, .srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT, .dstAccessMask = VK_ACCESS_SHADER_READ_BIT};
        vkCmdPipelineBarrier(m->cb, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, 0, 1, &tb, 0, NULL, 0, NULL);
        vkCmdWriteTimestamp(m->cb, VK_PIPELINE_STAGE_TRANSFER_BIT, m->qp, 1);
        vkCmdBindPipeline(m->cb, VK_PIPELINE_BIND_POINT_COMPUTE, m->pipe);
        VkMemoryBarrier mb = {VK_STRUCTURE_TYPE_MEMORY_BARRIER, .srcAccessMask = VK_ACCESS_SHADER_WRITE_BIT, .dstAccessMask = VK_ACCESS_SHADER_READ_BIT};
        for (int l = 0; l < m->nl; l++) {
            push pc = {rows, (uint32_t)m->din[l], (uint32_t)m->dout[l], l < m->nl - 1, m->woff[l], m->boff[l], l == 0 && packed, m->lutoff};
            vkCmdBindDescriptorSets(m->cb, VK_PIPELINE_BIND_POINT_COMPUTE, m->pl, 0, 1, &m->ds[l], 0, NULL);
            vkCmdPushConstants(m->cb, m->pl, VK_SHADER_STAGE_COMPUTE_BIT, 0, sizeof pc, &pc);
            vkCmdDispatch(m->cb, (pc.nout + m->ot - 1) / m->ot, (rows + m->rt - 1) / m->rt, 1);
            vkCmdWriteTimestamp(m->cb, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, m->qp, 3 + l);
            if (l < m->nl - 1)
                vkCmdPipelineBarrier(m->cb, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, 0, 1, &mb, 0, NULL, 0, NULL);
        }
        VkMemoryBarrier hb = {VK_STRUCTURE_TYPE_MEMORY_BARRIER, .srcAccessMask = VK_ACCESS_SHADER_WRITE_BIT, .dstAccessMask = VK_ACCESS_HOST_READ_BIT};
        vkCmdWriteTimestamp(m->cb, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, m->qp, 2);
        vkCmdPipelineBarrier(m->cb, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, VK_PIPELINE_STAGE_HOST_BIT, 0, 1, &hb, 0, NULL, 0, NULL);
        if (vkEndCommandBuffer(m->cb) != VK_SUCCESS || submit_wait(m)) return -1;
        double c2 = ms_now();
        memcpy(Y + (size_t)r0 * last, m->y.map, (size_t)rows * last * 4);
        double c3 = ms_now();
        uint64_t ts[3 + MAXL];
        if (vkGetQueryPoolResults(m->dev, m->qp, 0, 3 + m->nl, sizeof ts, ts, 8, VK_QUERY_RESULT_64_BIT) == VK_SUCCESS) {
            m->t_copy += (double)(ts[1] - ts[0]) * m->tperiod / 1e6; m->t_net += (double)(ts[2] - ts[1]) * m->tperiod / 1e6;
            for (int l = 0; l < m->nl; l++) m->t_layer[l] += (double)(ts[3 + l] - (l ? ts[2 + l] : ts[1])) * m->tperiod / 1e6;
        }
        m->t_in += c1 - c0; m->t_wait += c2 - c1; m->t_out += c3 - c2;
    }
    return 0;
}

const char *vkmlp_device(const vkmlp *m) { return m->name; }

void vkmlp_layer_times(const vkmlp *m, double *t) { for (int l = 0; l < m->nl; l++) t[l] = m->t_layer[l]; }

void vkmlp_times(const vkmlp *m, double t[5]) { t[0] = m->t_in; t[1] = m->t_copy; t[2] = m->t_net; t[3] = m->t_wait; t[4] = m->t_out; }

void vkmlp_free(vkmlp *m) {
    if (!m) return;
    if (m->dev) {
        vkDeviceWaitIdle(m->dev);
        if (m->qp) vkDestroyQueryPool(m->dev, m->qp, NULL);
        if (m->fence) vkDestroyFence(m->dev, m->fence, NULL);
        if (m->cp) vkDestroyCommandPool(m->dev, m->cp, NULL);
        if (m->dp) vkDestroyDescriptorPool(m->dev, m->dp, NULL);
        if (m->pipe) vkDestroyPipeline(m->dev, m->pipe, NULL);
        if (m->sm) vkDestroyShaderModule(m->dev, m->sm, NULL);
        if (m->pl) vkDestroyPipelineLayout(m->dev, m->pl, NULL);
        if (m->dsl) vkDestroyDescriptorSetLayout(m->dev, m->dsl, NULL);
        free_buf(m, &m->w); free_buf(m, &m->xs); free_buf(m, &m->x); free_buf(m, &m->a); free_buf(m, &m->b); free_buf(m, &m->y);
        vkDestroyDevice(m->dev, NULL);
    }
    if (m->inst) vkDestroyInstance(m->inst, NULL);
    free(m);
}
