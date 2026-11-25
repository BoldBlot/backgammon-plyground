/*
 * vk_mlp.h -- a PureTD network's forward pass on a GPU through Vulkan compute (1.1).
 * Copyright (C) 2026 Bold Blot. GPU played-out games.
 */
#ifndef VK_MLP_H
#define VK_MLP_H

#include <stdint.h>

/* Packed input: one byte per input, the code of its value in vkmlp_code_table (k / 2 for k < 32; 32 + j for j / 15). */
#define VKMLP_CODES 64
void vkmlp_code_table(float *lut);

typedef struct vkmlp vkmlp;

/* Weights as ptd.c keeps them: per layer wt [in][out] and bias [out]; hidden layers ReLU, the last linear.
 * max_rows: rows per GPU pass. device: index into the Vulkan devices, -1 = the first discrete GPU.
 * use_fma: fuse multiply-add (the arm64 build of ptd.c) or not (x86-64). NULL on failure, message in err. */
vkmlp *vkmlp_create(int n_layers, int in_size, const int *dim_out, float *const *wt, float *const *bias,
                    int max_rows, int device, int use_fma, char *err, int errlen);
/* Raw outputs Y[n][last dim] of rows X[n][in_size]. Returns 0, or -1 on a Vulkan error. */
int vkmlp_forward(vkmlp *m, const float *X, long n, float *Y);
int vkmlp_forward_codes(vkmlp *m, const uint8_t *X, long n, float *Y);
const char *vkmlp_device(const vkmlp *m);
/* ms so far: host copy in, GPU copy, GPU layers, submit to done, host copy out. */
void vkmlp_times(const vkmlp *m, double t[5]);
/* ms so far in each layer on the GPU. */
void vkmlp_layer_times(const vkmlp *m, double *t);
void vkmlp_free(vkmlp *m);

#endif
