/*
 * vkgames.c -- one turn of many played-out games at once, the network on the GPU (Vulkan).
 * Copyright (C) 2026 Bold Blot. Licensed under the GNU AGPL v3 or later (see LICENSE). The GPU version of the app's games search
 * (code 63, engine/GamesSearch.kt), reusable on the phone.
 *
 * Built with the app's ptd.c included (its static functions and its exact arithmetic), so that a turn here
 * gives bit for bit what GamesSearch gets from ptd_cube_tree(levels 1) and ptd_play: the moves are generated,
 * filtered and picked on the CPU, only the network rows go to the GPU (vk_mlp.c, summed in ptd.c's order).
 *
 * vkg_step, per game i (state s[i], its 10 match weights w[i], the roll it throws dice[i]), writes VKG_OUT floats:
 *   [0, 21)     points won by the move for each of the 21 rolls (as ptd_cube_tree's level 1)
 *   [21, 168)   7 per roll: on-roll probabilities after the move, race flag, pips (zeros if the game ended)
 *   [168]       points won by the move for the roll thrown (as ptd_play)
 *   [169, 176)  ptd_play's out: the mover's 5 probabilities after its move, race flag, pips
 *   [176, 205)  the position after the roll thrown, as int[29] (the app's JNI layout)
 */
#include "ptd.c"

#include <pthread.h>
#include <stdatomic.h>
#include <stdint.h>

#include "vk_mlp.h"

#define VKG_OUT 205
#define NSLOT 22
#define NF NUM_FEATURES_196

typedef struct {
    ptd_net *net;
    ptd_bearoff *bo;
    vkmlp *gpu;              /* NULL: the CPU forward pass (ptd.c's), for comparisons */
    scratch *sc;             /* CPU forward pass */
    int threads;
    double ms_moves, ms_net, ms_pick, ms_wait;  /* time spent, for tuning */
    long rows, cand;
    pthread_mutex_t lock;    /* the GPU and the stats: vkg_step may run on several threads at once */         /* rows evaluated; positions asked for before merging equal ones */
} vkg;

typedef struct {
    int nk, pass, nrows;
    long row0;
    BoardState *kids;
    int *fin, *row;          /* row: -1 none, -2 wanted (after phase 0), else the row in X/Y (shared by equal positions) */
    uint64_t *hash;
    float *q5;
} task;

typedef struct {
    vkg *h;
    const BoardState *s;
    const float *w;
    const int *dice;
    int n;
    float *res;
    int *leaves;             /* optional (63D online): the position after each of the 21 rolls' moves, int[29] each, zeros if the game ended */
    task *t;
    float *X, *Y;
    uint8_t *Xc;             /* the GPU's input: X packed as codes (vk_mlp.h) */
    atomic_int bad;          /* an input with no code */
    const BoardState **uniq; /* the position of each row */
    long rows;
    atomic_int next;
    int phase;
} job;

static uint64_t pos_hash(const BoardState *b) {
    const int *v = (const int *)b;
    uint64_t hv = 0x9E3779B97F4A7C15ull;
    for (size_t q = 0; q < sizeof(BoardState) / sizeof(int); q++) { hv ^= (uint32_t)v[q]; hv *= 0xff51afd7ed558ccdull; hv ^= hv >> 29; }
    return hv;
}

static double now(void) { struct timespec ts; clock_gettime(CLOCK_MONOTONIC, &ts); return ts.tv_sec * 1e3 + ts.tv_nsec / 1e6; }

/* Per worker thread; freed when the worker ends (workers live for one phase). */
static _Thread_local Play *tl_plays;
static _Thread_local float *tl_p, *tl_mv;

static void phase_moves(job *J, int ti) {
    if (!tl_plays) tl_plays = malloc(sizeof(Play) * MAX_KIDS);
    Play *plays = tl_plays;
    task *t = &J->t[ti];
    int i = ti / NSLOT, j = ti % NSLOT;
    const BoardState *s = &J->s[i];
    int d1 = j < 21 ? D1[j] : J->dice[2 * i], d2 = j < 21 ? D2[j] : J->dice[2 * i + 1];
    int n = get_legal_plays(s, d1, d2, plays, MAX_KIDS);
    t->pass = n <= 0;
    t->nk = t->pass ? 1 : n;
    t->kids = malloc(sizeof(BoardState) * t->nk);
    t->fin = malloc(sizeof(int) * t->nk);
    t->row = malloc(sizeof(int) * t->nk);
    t->q5 = malloc(sizeof(float) * 5 * t->nk);
    t->hash = malloc(sizeof(uint64_t) * t->nk);
    t->nrows = 0;
    if (t->pass) { t->kids[0] = *s; board_switch_turn(&t->kids[0]); }
    else for (int k = 0; k < n; k++) t->kids[k] = plays[k].resulting_state;
    for (int k = 0; k < t->nk; k++) {
        t->fin[k] = t->pass ? 0 : game_points(&t->kids[k]);
        t->row[k] = -1;
        if (t->fin[k]) continue;
        if (bo_probs(J->h->bo, &t->kids[k], t->q5 + 5 * k)) continue;
        t->row[k] = -2;
        t->hash[k] = pos_hash(&t->kids[k]);
        t->nrows++;
    }
}

#define ENC_CHUNK 256
/* Rows ti*ENC_CHUNK onwards. */
static void phase_encode(job *J, int ti) {
    long r0 = (long)ti * ENC_CHUNK, r1 = r0 + ENC_CHUNK < J->rows ? r0 + ENC_CHUNK : J->rows;
    if (!J->Xc) { for (long r = r0; r < r1; r++) encode_state(J->uniq[r], J->X + (size_t)r * NF); return; }
    float f[NF];
    for (long r = r0; r < r1; r++) {
        encode_state(J->uniq[r], f);
        uint8_t *c = J->Xc + (size_t)r * NF;
        for (int q = 0; q < NF; q++) {
            float v = f[q];
            int k = (int)(v * 2.0f), j = (int)(v * 15.0f + 0.5f);
            if (k >= 0 && k < 32 && k * 0.5f == v) c[q] = (uint8_t)k;
            else if (j >= 0 && j <= 15 && j / 15.0f == v) c[q] = (uint8_t)(32 + j);
            else { c[q] = 0; atomic_store(&J->bad, 1); }
        }
    }
}

/* One row per distinct position over all games and rolls of the step (equal positions give equal rows: exact). */
static long dedup(job *J) {
    long cand = 0;
    int nt = J->n * NSLOT;
    for (int i = 0; i < nt; i++) cand += J->t[i].nrows;
    size_t cap = 64;
    while (cap < 2 * (size_t)cand) cap <<= 1;
    long *slot = malloc(sizeof(long) * cap);
    memset(slot, 0xff, sizeof(long) * cap);
    uint64_t *sh = malloc(sizeof(uint64_t) * cap);
    J->uniq = malloc(sizeof(BoardState *) * (cand ? cand : 1));
    long rows = 0;
    for (int i = 0; i < nt; i++) {
        task *t = &J->t[i];
        for (int k = 0; k < t->nk; k++) {
            if (t->row[k] != -2) continue;
            uint64_t hv = t->hash[k];
            size_t m = (size_t)hv & (cap - 1);
            for (;;) {
                if (slot[m] < 0) { slot[m] = rows; sh[m] = hv; J->uniq[rows] = &t->kids[k]; t->row[k] = (int)rows++; break; }
                if (sh[m] == hv && !memcmp(J->uniq[slot[m]], &t->kids[k], sizeof(BoardState))) { t->row[k] = (int)slot[m]; break; }
                m = (m + 1) & (cap - 1);
            }
        }
    }
    free(slot); free(sh);
    return rows;
}

static void phase_pick(job *J, int ti) {
    task *t = &J->t[ti];
    int i = ti / NSLOT, j = ti % NSLOT;
    const BoardState *s = &J->s[i];
    for (int k = 0; k < t->nk; k++)
        if (t->row[k] >= 0) prob5(J->Y + (size_t)t->row[k] * 5, t->q5 + 5 * k);
    ptd_engine e = {0};
    e.match_w = J->w + 10 * i;
    int b = 0;
    float probs[5];
    if (t->pass) invert5(t->q5, probs);
    else {
        if (!tl_p) { tl_p = malloc(sizeof(float) * 5 * MAX_KIDS); tl_mv = malloc(sizeof(float) * MAX_KIDS); }
        float *p = tl_p, *mv = tl_mv;
        for (int k = 0; k < t->nk; k++) {
            if (t->fin[k]) win_probs(t->fin[k], p + 5 * k); else invert5(t->q5 + 5 * k, p + 5 * k);
        }
        /* best_1ply's choice, as written there */
        for (int k = 0; k < t->nk; k++) {
            mv[k] = mover_value(&e, s->turn, p + 5 * k);
            if (mv[k] > mv[b]) b = k;
        }
        if (e.match_w) {
            float bv = mv[b], be = equity5(p + 5 * b);
            for (int k = 0; k < t->nk; k++)
                if (mv[k] >= bv - 1e-6f && equity5(p + 5 * k) > be) { b = k; be = equity5(p + 5 * k); }
        }
        memcpy(probs, p + 5 * b, sizeof probs);
    }
    const BoardState *nx = &t->kids[b];
    int pts = game_points(nx);
    float *o = J->res + (size_t)VKG_OUT * i;
    if (j < 21) {
        o[j] = (float)pts;
        float *lf = o + 21 + 7 * j;
        memset(lf, 0, 7 * sizeof(float));
        if (!pts) { memcpy(lf, t->q5 + 5 * b, 5 * sizeof(float)); race_and_pips(nx, &lf[5], &lf[6]); }
        if (J->leaves) {
            int *L = J->leaves + ((size_t)i * 21 + j) * 29;
            if (pts) memset(L, 0, 29 * sizeof(int));
            else { for (int q = 0; q < 24; q++) L[q] = nx->points[q]; L[24] = nx->bar[0]; L[25] = nx->bar[1]; L[26] = nx->off[0]; L[27] = nx->off[1]; L[28] = nx->turn; }
        }
    } else {
        o[168] = (float)pts;
        memcpy(o + 169, probs, sizeof probs);
        race_and_pips(nx, &o[174], &o[175]);
        float *v = o + 176;
        for (int q = 0; q < 24; q++) v[q] = (float)nx->points[q];
        v[24] = (float)nx->bar[0]; v[25] = (float)nx->bar[1]; v[26] = (float)nx->off[0]; v[27] = (float)nx->off[1]; v[28] = (float)nx->turn;
    }
}

static void *vkg_worker(void *arg) {
    job *J = arg;
    int total = J->phase == 1 ? (int)((J->rows + ENC_CHUNK - 1) / ENC_CHUNK) : J->n * NSLOT;
    for (;;) {
        int ti = atomic_fetch_add(&J->next, 1);
        if (ti >= total) break;
        if (J->phase == 0) phase_moves(J, ti); else if (J->phase == 1) phase_encode(J, ti); else phase_pick(J, ti);
    }
    free(tl_plays); free(tl_p); free(tl_mv);
    tl_plays = NULL; tl_p = tl_mv = NULL;
    return NULL;
}

static void run_phase(job *J, int phase) {
    J->phase = phase;
    atomic_store(&J->next, 0);
    int nt = J->h->threads < 1 ? 1 : J->h->threads;
    if (nt > 64) nt = 64;
    pthread_t th[64];
    for (int k = 1; k < nt; k++) pthread_create(&th[k], NULL, vkg_worker, J);
    vkg_worker(J);
    for (int k = 1; k < nt; k++) pthread_join(th[k], NULL);
}

static int vkg_step2(vkg *h, const BoardState *s, const float *w, const int *dice, int n, float *res, int *leaves);
int vkg_step(vkg *h, const BoardState *s, const float *w, const int *dice, int n, float *res) { return vkg_step2(h, s, w, dice, n, res, NULL); }
static int vkg_step2(vkg *h, const BoardState *s, const float *w, const int *dice, int n, float *res, int *leaves) {
    job J = {.h = h, .s = s, .w = w, .dice = dice, .n = n, .res = res, .leaves = leaves};
    J.t = calloc((size_t)n * NSLOT, sizeof(task));
    double t0 = now();
    run_phase(&J, 0);
    long rows = J.rows = dedup(&J);
    long cand = 0;
    for (int k = 0; k < n * NSLOT; k++) cand += J.t[k].nrows;
    if (h->gpu) J.Xc = malloc((size_t)NF * (rows ? rows : 1)); else J.X = malloc(sizeof(float) * NF * (rows ? rows : 1));
    J.Y = malloc(sizeof(float) * 5 * (rows ? rows : 1));
    run_phase(&J, 1);
    double t1 = now();
    int rc = 0;
    pthread_mutex_lock(&h->lock);
    double tg = now();
    if (rows) {
        if (h->gpu) rc = atomic_load(&J.bad) ? -2 : vkmlp_forward_codes(h->gpu, J.Xc, rows, J.Y);
        else forward(h->net, h->sc, J.X, (int)rows, J.Y);
    }
    double t2 = now();
    h->ms_net += t2 - tg; h->ms_wait += tg - t1;
    pthread_mutex_unlock(&h->lock);
    if (!rc) run_phase(&J, 2);
    double t3 = now();
    pthread_mutex_lock(&h->lock);
    h->ms_moves += t1 - t0; h->ms_pick += t3 - t2; h->rows += rows; h->cand += cand;
    pthread_mutex_unlock(&h->lock);
    for (int k = 0; k < n * NSLOT; k++) { free(J.t[k].kids); free(J.t[k].fin); free(J.t[k].row); free(J.t[k].q5); free(J.t[k].hash); }
    free(J.t); free(J.uniq); free(J.Xc); free(J.X); free(J.Y);
    return rc;
}

vkg *vkg_open(const char *net, const char *bearoff, int threads, int max_rows, int device, int fma, int cpu, char *err, int errlen) {
    vkg *h = calloc(1, sizeof *h);
    h->threads = threads;
    pthread_mutex_init(&h->lock, NULL);
    h->net = ptd_net_load(net, err, errlen);
    if (!h->net) { free(h); return NULL; }
    h->bo = ptd_bearoff_open(bearoff);
    if (cpu) h->sc = malloc(sizeof(scratch));
    else {
        h->gpu = vkmlp_create(h->net->n_layers, h->net->in_size, h->net->dim_out, h->net->wt, h->net->bias, max_rows, device, fma, err, errlen);
        if (!h->gpu) { ptd_net_free(h->net); ptd_bearoff_free(h->bo); free(h); return NULL; }
    }
    return h;
}

void vkg_close(vkg *h) {
    if (!h) return;
    vkmlp_free(h->gpu);
    free(h->sc);
    ptd_net_free(h->net);
    ptd_bearoff_free(h->bo);
    free(h);
}

#ifdef VKG_JNI
#include <jni.h>
#define FN(name) Java_com_boldblot_backgammonplyground_engine_VkGamesNative_##name

JNIEXPORT jlong JNICALL FN(nativeOpen)(JNIEnv *env, jclass cls, jstring net, jstring bearoff, jint threads, jint maxRows, jint device, jboolean fma, jboolean cpu) {
    char err[256];
    const char *a = (*env)->GetStringUTFChars(env, net, NULL);
    const char *b = (*env)->GetStringUTFChars(env, bearoff, NULL);
    vkg *h = vkg_open(a, b, threads, maxRows, device, fma, cpu, err, sizeof err);
    (*env)->ReleaseStringUTFChars(env, net, a);
    (*env)->ReleaseStringUTFChars(env, bearoff, b);
    if (!h) fprintf(stderr, "vkgames: %s\n", err);
    return (jlong)(intptr_t)h;
}

JNIEXPORT jstring JNICALL FN(nativeDevice)(JNIEnv *env, jclass cls, jlong ptr) {
    vkg *h = (vkg *)(intptr_t)ptr;
    return (*env)->NewStringUTF(env, h && h->gpu ? vkmlp_device(h->gpu) : "CPU");
}

/* states int[29 n], weights float[10 n], dice int[2 n] -> float[VKG_OUT n], or null on a GPU error. */
JNIEXPORT jfloatArray JNICALL FN(nativeStep)(JNIEnv *env, jclass cls, jlong ptr, jintArray states, jfloatArray weights, jintArray dice, jint n) {
    vkg *h = (vkg *)(intptr_t)ptr;
    if (!h || n <= 0) return NULL;
    jint *v = (*env)->GetIntArrayElements(env, states, NULL);
    BoardState *s = malloc(sizeof(BoardState) * n);
    for (int i = 0; i < n; i++) {
        const jint *q = v + 29 * i;
        for (int k = 0; k < 24; k++) s[i].points[k] = q[k];
        s[i].bar[0] = q[24]; s[i].bar[1] = q[25]; s[i].off[0] = q[26]; s[i].off[1] = q[27]; s[i].turn = q[28];
    }
    (*env)->ReleaseIntArrayElements(env, states, v, JNI_ABORT);
    float *w = malloc(sizeof(float) * 10 * n);
    int *d = malloc(sizeof(int) * 2 * n);
    (*env)->GetFloatArrayRegion(env, weights, 0, 10 * n, w);
    (*env)->GetIntArrayRegion(env, dice, 0, 2 * n, d);
    float *res = malloc(sizeof(float) * VKG_OUT * n);
    jfloatArray out = NULL;
    if (vkg_step(h, s, w, d, n, res) == 0) {
        out = (*env)->NewFloatArray(env, VKG_OUT * n);
        if (out) (*env)->SetFloatArrayRegion(env, out, 0, VKG_OUT * n, res);
    }
    free(s); free(w); free(d); free(res);
    return out;
}

/* nativeStep plus the 21 reply positions of each row in leaves (int[21 * 29 n]), for 63D online. */
JNIEXPORT jfloatArray JNICALL FN(nativeStepLeaves)(JNIEnv *env, jclass cls, jlong ptr, jintArray states, jfloatArray weights, jintArray dice, jint n, jintArray leavesOut) {
    vkg *h = (vkg *)(intptr_t)ptr;
    if (!h || n <= 0 || (*env)->GetArrayLength(env, leavesOut) < 21 * 29 * n) return NULL;
    jint *v = (*env)->GetIntArrayElements(env, states, NULL);
    BoardState *s = malloc(sizeof(BoardState) * n);
    for (int i = 0; i < n; i++) {
        const jint *q = v + 29 * i;
        for (int k = 0; k < 24; k++) s[i].points[k] = q[k];
        s[i].bar[0] = q[24]; s[i].bar[1] = q[25]; s[i].off[0] = q[26]; s[i].off[1] = q[27]; s[i].turn = q[28];
    }
    (*env)->ReleaseIntArrayElements(env, states, v, JNI_ABORT);
    float *w = malloc(sizeof(float) * 10 * n);
    int *d = malloc(sizeof(int) * 2 * n);
    int *L = malloc(sizeof(int) * 21 * 29 * n);
    (*env)->GetFloatArrayRegion(env, weights, 0, 10 * n, w);
    (*env)->GetIntArrayRegion(env, dice, 0, 2 * n, d);
    float *res = malloc(sizeof(float) * VKG_OUT * n);
    jfloatArray out = NULL;
    if (vkg_step2(h, s, w, d, n, res, L) == 0) {
        out = (*env)->NewFloatArray(env, VKG_OUT * n);
        if (out) (*env)->SetFloatArrayRegion(env, out, 0, VKG_OUT * n, res);
        (*env)->SetIntArrayRegion(env, leavesOut, 0, 21 * 29 * n, (const jint *)L);
    }
    free(s); free(w); free(d); free(res); free(L);
    return out;
}

/* ms in move generation, the network and picking; rows evaluated (since open). */
JNIEXPORT jdoubleArray JNICALL FN(nativeStats)(JNIEnv *env, jclass cls, jlong ptr) {
    vkg *h = (vkg *)(intptr_t)ptr;
    jdoubleArray out = (*env)->NewDoubleArray(env, 6);
    double v[6] = {h->ms_moves, h->ms_net, h->ms_pick, (double)h->rows, (double)h->cand, h->ms_wait};
    (*env)->SetDoubleArrayRegion(env, out, 0, 6, v);
    return out;
}

JNIEXPORT void JNICALL FN(nativeClose)(JNIEnv *env, jclass cls, jlong ptr) { vkg_close((vkg *)(intptr_t)ptr); }
#endif
