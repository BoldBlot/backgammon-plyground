/*
 * JNI bridge for com.boldblot.backgammonplyground.engine.PureTdNative (engine/puretd).
 * Copyright (C) 2026 Bold Blot
 *
 * Positions travel as int[29] each: points[24] (positive = WHITE), bar[WHITE], bar[BLACK],
 * off[WHITE], off[BLACK], turn.
 */
#include <jni.h>
#include <stdint.h>
#include <stdlib.h>

#include "ptd.h"

#define FN(name) Java_com_boldblot_backgammonplyground_engine_PureTdNative_##name
#define STATE_INTS 29

typedef struct {
    ptd_net *cubeless, *cubeful;
    ptd_bearoff *bearoff;
    ptd_engine e;
    int shared;                 /* made by nativeShare: the networks belong to another handle */
} handle;

static void to_state(const jint *v, BoardState *s)
{
    for (int i = 0; i < 24; i++) s->points[i] = v[i];
    s->bar[0] = v[24]; s->bar[1] = v[25];
    s->off[0] = v[26]; s->off[1] = v[27];
    s->turn = v[28];
}

JNIEXPORT jlong JNICALL
FN(nativeOpen)(JNIEnv *env, jclass cls, jstring cubeless, jstring cubeful, jstring bearoff, jint threads)
{
    char err[256];
    const char *a = (*env)->GetStringUTFChars(env, cubeless, NULL);
    const char *b = (*env)->GetStringUTFChars(env, cubeful, NULL);
    const char *c = bearoff ? (*env)->GetStringUTFChars(env, bearoff, NULL) : NULL;
    handle *h = calloc(1, sizeof *h);
    h->cubeless = ptd_net_load(a, err, sizeof err);
    h->cubeful = h->cubeless ? ptd_net_load(b, err, sizeof err) : NULL;
    if (h->cubeful && c) h->bearoff = ptd_bearoff_open(c);
    (*env)->ReleaseStringUTFChars(env, cubeless, a);
    (*env)->ReleaseStringUTFChars(env, cubeful, b);
    if (c) (*env)->ReleaseStringUTFChars(env, bearoff, c);
    if (!h->cubeless || !h->cubeful) {
        ptd_net_free(h->cubeless);
        free(h);
        return 0;
    }
    h->e.cubeless = h->cubeless;
    h->e.cubeful = h->cubeful;
    h->e.bearoff = h->bearoff;
    h->e.threads = threads;
    return (jlong)(intptr_t)h;
}

JNIEXPORT void JNICALL
FN(nativeClose)(JNIEnv *env, jclass cls, jlong ptr)
{
    handle *h = (handle *)(intptr_t)ptr;
    if (!h) return;
    if (h->shared) {
        free(h);
        return;
    }
    ptd_net_free(h->cubeless);
    ptd_net_free(h->cubeful);
    ptd_bearoff_free(h->bearoff);
    free(h);
}

/*
 * A second engine on ptr's networks and bear-off database (read-only, so both can search at the
 * same time on different threads) with its own thread count. Close it before ptr.
 */
JNIEXPORT jlong JNICALL
FN(nativeShare)(JNIEnv *env, jclass cls, jlong ptr, jint threads)
{
    handle *h = (handle *)(intptr_t)ptr;
    if (!h) return 0;
    handle *c = calloc(1, sizeof *c);
    *c = *h;
    c->shared = 1;
    c->e.threads = threads;
    return (jlong)(intptr_t)c;
}

/* Worker threads for ptr's next searches (call between searches, on the thread that searches). */
JNIEXPORT void JNICALL
FN(nativeSetThreads)(JNIEnv *env, jclass cls, jlong ptr, jint threads)
{
    handle *h = (handle *)(intptr_t)ptr;
    if (h) h->e.threads = threads < 1 ? 1 : threads;
}

/* Probabilities for the player who just moved, 5 per position (see ptd_post_probs). */
JNIEXPORT jfloatArray JNICALL
FN(nativePostProbs)(JNIEnv *env, jclass cls, jlong ptr, jintArray states, jint plies)
{
    handle *h = (handle *)(intptr_t)ptr;
    jsize len = (*env)->GetArrayLength(env, states);
    if (!h || len % STATE_INTS) return NULL;
    int n = len / STATE_INTS;
    jint *v = (*env)->GetIntArrayElements(env, states, NULL);
    BoardState *s = malloc((n ? n : 1) * sizeof *s);
    for (int k = 0; k < n; k++) to_state(v + k * STATE_INTS, &s[k]);
    (*env)->ReleaseIntArrayElements(env, states, v, JNI_ABORT);
    float *out = malloc((n ? n : 1) * 5 * sizeof(float));
    ptd_post_probs(&h->e, s, n, plies, out);
    jfloatArray res = (*env)->NewFloatArray(env, n * 5);
    (*env)->SetFloatArrayRegion(env, res, 0, n * 5, out);
    free(s);
    free(out);
    return res;
}

/* Money cube decision: {nd, dt, probs[5]} for the player on roll (see ptd_cube). */
JNIEXPORT jfloatArray JNICALL
FN(nativeCube)(JNIEnv *env, jclass cls, jlong ptr, jintArray state, jint plies, jint owner, jboolean jacoby)
{
    handle *h = (handle *)(intptr_t)ptr;
    if (!h || (*env)->GetArrayLength(env, state) != STATE_INTS) return NULL;
    jint v[STATE_INTS];
    (*env)->GetIntArrayRegion(env, state, 0, STATE_INTS, v);
    BoardState s;
    to_state(v, &s);
    float out[7];
    if (ptd_cube(&h->e, &s, plies, owner, jacoby, &out[0], &out[1], out + 2) != 0) return NULL;
    jfloatArray res = (*env)->NewFloatArray(env, 7);
    (*env)->SetFloatArrayRegion(env, res, 0, 7, out);
    return res;
}

/* Resulting positions of every legal play (int[29] each; empty when there is none). */
JNIEXPORT jintArray JNICALL
FN(nativeLegalStates)(JNIEnv *env, jclass cls, jintArray state, jint d1, jint d2)
{
    if ((*env)->GetArrayLength(env, state) != STATE_INTS) return NULL;
    jint v[STATE_INTS];
    (*env)->GetIntArrayRegion(env, state, 0, STATE_INTS, v);
    BoardState s;
    to_state(v, &s);
    Play *plays = malloc(4096 * sizeof *plays);
    int n = get_legal_plays(&s, d1, d2, plays, 4096);
    if (n < 0) n = 0;
    jint *out = malloc((n ? n : 1) * STATE_INTS * sizeof(jint));
    for (int k = 0; k < n; k++) {
        const BoardState *r = &plays[k].resulting_state;
        jint *o = out + k * STATE_INTS;
        for (int i = 0; i < 24; i++) o[i] = r->points[i];
        o[24] = r->bar[0]; o[25] = r->bar[1]; o[26] = r->off[0]; o[27] = r->off[1]; o[28] = r->turn;
    }
    jintArray res = (*env)->NewIntArray(env, n * STATE_INTS);
    (*env)->SetIntArrayRegion(env, res, 0, n * STATE_INTS, out);
    free(out);
    free(plays);
    return res;
}

/* Speed test: {milliseconds per decision, decisions timed} (see ptd_bench). */
JNIEXPORT jdoubleArray JNICALL
FN(nativeBench)(JNIEnv *env, jclass cls, jlong ptr, jint kind, jint plies, jfloat within, jint n, jint threads, jdouble budgetMs)
{
    handle *h = (handle *)(intptr_t)ptr;
    if (!h) return NULL;
    ptd_engine e = h->e;
    e.threads = threads;
    int done = 0;
    jdouble out[2];
    out[0] = ptd_bench(&e, kind, plies, within, n, 12345u, budgetMs, &done);
    out[1] = done;
    jdoubleArray res = (*env)->NewDoubleArray(env, 2);
    (*env)->SetDoubleArrayRegion(env, res, 0, 2, out);
    return res;
}

JNIEXPORT void JNICALL
FN(nativeBenchCancel)(JNIEnv *env, jclass cls)
{
    ptd_bench_cancel();
}

/* The match cube search tree (ptd_cube_tree): float[nodes + 7 * leaves], the points of every node
 * first, then the leaves' data. */
JNIEXPORT jfloatArray JNICALL
FN(nativeCubeTree)(JNIEnv *env, jclass cls, jlong ptr, jintArray state, jint levels)
{
    handle *h = (handle *)(intptr_t)ptr;
    if (!h || levels < 1 || levels > PTD_MAX_PLIES - 1 || (*env)->GetArrayLength(env, state) != STATE_INTS) return NULL;
    jint v[STATE_INTS];
    (*env)->GetIntArrayRegion(env, state, 0, STATE_INTS, v);
    BoardState s;
    to_state(v, &s);
    long nodes = 0, leaves = 1;
    for (int k = 0; k < levels; k++) { leaves *= 21; nodes += leaves; }
    int *pts = malloc(sizeof(int) * (size_t)nodes);
    float *leaf = malloc(sizeof(float) * 7 * (size_t)leaves);
    ptd_cube_tree(&h->e, &s, levels, pts, leaf);
    jfloatArray out = (*env)->NewFloatArray(env, (jsize)(nodes + 7 * leaves));
    if (out) {
        float *buf = malloc(sizeof(float) * (size_t)nodes);
        for (long i = 0; i < nodes; i++) buf[i] = (float)pts[i];
        (*env)->SetFloatArrayRegion(env, out, 0, (jsize)nodes, buf);
        (*env)->SetFloatArrayRegion(env, out, (jsize)nodes, (jsize)(7 * leaves), leaf);
        free(buf);
    }
    free(pts);
    free(leaf);
    return out;
}
