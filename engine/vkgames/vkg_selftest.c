/* vkg_selftest.c -- which GPU settings work on this device (2026-11-25).
 *
 * Tries every setting of the network shader: multiply-add mode (fma 0 = plain multiply then add, as ptd.c on x86-64 or with
 * -ffp-contract=off; fma 1 = fma(), as clang's default contraction on arm64) x tile size (rows x outputs per thread: 88, 48, 84,
 * 44). For each: are the GPU's outputs bit for bit the CPU's (ptd.c compiled into this same library), and how fast is it.
 * Then, for the fastest exact setting, a full game turn (the 21 rolls' replies and the roll thrown, vkg_step) against the CPU
 * path (ptd_cube_tree + ptd_play). Known cases: Adreno 830 (Galaxy S25): fma() is not a true fused multiply-add there (fma 1 off
 * by up to 6e-4), plain is exact; the 8 x 8 tile spills registers (0.04 against 0.32 M rows/s with 4 x 8).
 *
 * The app runs it once (a "GPU check" in Settings / Speed test), keeps the chosen setting, and uses the GPU only if a setting is
 * exact; otherwise it stays on the CPU. Library: build this file instead of vkgames.c (it includes it).
 * CLI (VKG_SELFTEST_MAIN): vkg_selftest NET.ptw BEAROFF_CACHE [device] [positions]; exit 0 when a setting is exact.
 */
#include "vkgames.c"

static const char *ST_TILES[] = {"88", "48", "84", "44"};
static unsigned st_rs = 20261125u;
static int st_rnd(int n) { st_rs = st_rs * 1103515245u + 12345u; return (int)((st_rs >> 8) % (unsigned)n); }

/* Report into rep (text, one line per setting, the last line "BEST fma=F tile=T" or "BEST none"). Returns 10 * fma + tile
 * index of the best exact setting, -1 if none is exact or the GPU is no faster than 4 CPU threads, -2 if there is no GPU. */
int vkg_selftest(const char *netf, const char *bearoff, int device, int npos, char *rep, int replen) {
    char err[256]; int used = 0;
#define REP(...) do { if (used < replen) used += snprintf(rep + used, (size_t)(replen - used), __VA_ARGS__); } while (0)
    ptd_net *net = ptd_net_load(netf, err, sizeof err);
    if (!net) { REP("net: %s\nBEST none\n", err); return -2; }
    if (npos < 100) npos = 100;
    /* positions from random games, every position on roll kept */
    BoardState *S = malloc(sizeof(BoardState) * npos);
    Play *plays = malloc(sizeof(Play) * MAX_KIDS);
    BoardState s; board_init(&s);
    for (int k = 0; k < npos;) {
        if (game_points(&s)) { board_init(&s); continue; }
        S[k++] = s;
        int n = get_legal_plays(&s, 1 + st_rnd(6), 1 + st_rnd(6), plays, MAX_KIDS);
        if (n <= 0) board_switch_turn(&s); else s = plays[st_rnd(n)].resulting_state;
    }
    float *X = malloc(sizeof(float) * NF * npos), *Yc = malloc(sizeof(float) * 5 * npos), *Yg = malloc(sizeof(float) * 5 * npos);
    for (int k = 0; k < npos; k++) encode_state(&S[k], X + (size_t)NF * k);
    scratch *sc = malloc(sizeof(scratch));
    double c0 = now(); forward(net, sc, X, npos, Yc); double c1 = now();
    double cpu1 = c1 > c0 ? npos / (c1 - c0) / 1e3 : 1e9;   /* M rows/s, one CPU thread */
    const long NB = 32768;
    uint8_t *C = malloc((size_t)NB * net->in_size);
    float *YB = malloc(sizeof(float) * 5 * NB);
    for (long i = 0; i < NB * net->in_size; i++) C[i] = (st_rnd(4) == 0) ? (uint8_t)st_rnd(48) : 0;
    int best = -1; double bestr = 0; int any = 0; double start = now();
    for (int fma = 0; fma < 2; fma++) for (int t = 0; t < 4; t++) {
        if (now() - start > 30000) { REP("fma=%d tile=%s: skipped (30 s time budget used: a slow or software GPU)\n", fma, ST_TILES[t]); continue; }
        setenv("VKG_TILE", ST_TILES[t], 1);
        vkmlp *m = vkmlp_create(net->n_layers, net->in_size, net->dim_out, net->wt, net->bias, 16384, device, fma, err, sizeof err);
        if (!m) { REP("fma=%d tile=%s: no GPU (%s)\n", fma, ST_TILES[t], err); continue; }
        any = 1;
        int bad = 0; double maxd = 0;
        if (vkmlp_forward(m, X, npos, Yg) != 0) bad = -1;
        else for (int k = 0; k < 5 * npos; k++) if (memcmp(&Yc[k], &Yg[k], 4)) { bad++; double d = fabs((double)Yc[k] - Yg[k]); if (d > maxd) maxd = d; }
        vkmlp_forward_codes(m, C, 4096, YB); /* warm up */
        double t0 = now(); int ok = vkmlp_forward_codes(m, C, NB, YB) == 0; double t1 = now();
        double mrows = ok && t1 > t0 ? NB / (t1 - t0) / 1e3 : 0;
        REP("fma=%d tile=%s: %s (%d of %d outputs differ, max %.2g), %.2f M rows/s [%s]\n", fma, ST_TILES[t],
            bad == 0 ? "EXACT" : "not exact", bad, 5 * npos, maxd, mrows, vkmlp_device(m));
        if (bad == 0 && mrows > bestr) { bestr = mrows; best = 10 * fma + t; }
        vkmlp_free(m);
    }
    REP("CPU: %.3f M rows/s on one thread\n", cpu1);
    /* The games use the CPU's threads for the moves either way; the GPU only pays when its network is well ahead of what the
     * CPU threads would do (a software or very weak GPU is not). */
    if (best >= 0 && bestr < 4 * cpu1) { REP("best exact setting fma=%d tile=%s: %.2f M rows/s, under 4 CPU threads' worth\n", best / 10, ST_TILES[best % 10], bestr); best = -3; }
    if (!any) { REP("BEST none (no Vulkan GPU)\n"); best = -2; }
    else if (best == -3) { REP("BEST none (the GPU is slower than the CPU: use the CPU)\n"); best = -1; }
    else if (best >= 0) {
        /* a full game turn with the best setting, against the CPU path */
        int fma = best / 10; setenv("VKG_TILE", ST_TILES[best % 10], 1);
        vkg *g = vkg_open(netf, bearoff, 4, 16384, device, fma, 0, err, sizeof err);
        int nt = npos < 300 ? npos : 300, sbad = -1;
        if (g) {
            float *W = malloc(sizeof(float) * 10 * nt); int *D = malloc(sizeof(int) * 2 * nt);
            for (int k = 0; k < nt; k++) {
                for (int c = 0; c < 2; c++) { float *w = W + 10 * k + 5 * c;
                    w[0] = 0.3f + st_rnd(100) / 250.0f; w[1] = st_rnd(100) / 500.0f; w[2] = st_rnd(100) / 2000.0f; w[3] = -(st_rnd(100) / 500.0f); w[4] = -(st_rnd(100) / 2000.0f); }
                D[2 * k] = 1 + st_rnd(6); D[2 * k + 1] = 1 + st_rnd(6);
            }
            float *res = malloc(sizeof(float) * VKG_OUT * nt);
            if (vkg_step(g, S, W, D, nt, res) == 0) {
                sbad = 0;
                for (int k = 0; k < nt; k++) {
                    ptd_engine e = {.cubeless = g->net, .bearoff = g->bo, .threads = 1, .match_w = W + 10 * k};
                    int pts[21]; float leaf[147], out[7]; BoardState nx; float ref[VKG_OUT];
                    ptd_cube_tree(&e, &S[k], 1, pts, leaf);
                    int pp = ptd_play(&e, &S[k], D[2 * k], D[2 * k + 1], &nx, out);
                    for (int j = 0; j < 21; j++) ref[j] = (float)pts[j];
                    memcpy(ref + 21, leaf, sizeof leaf); ref[168] = (float)pp; memcpy(ref + 169, out, sizeof out);
                    for (int q = 0; q < 24; q++) ref[176 + q] = (float)nx.points[q];
                    ref[200] = nx.bar[0]; ref[201] = nx.bar[1]; ref[202] = nx.off[0]; ref[203] = nx.off[1]; ref[204] = nx.turn;
                    if (memcmp(ref, res + (size_t)VKG_OUT * k, sizeof ref)) sbad++;
                }
            }
            free(W); free(D); free(res); vkg_close(g);
        }
        REP("game turns with the best setting: %s (%d of %d differ)\n", sbad == 0 ? "EXACT" : "FAILED", sbad, nt);
        if (sbad != 0) best = -1;
        if (best >= 0) REP("BEST fma=%d tile=%s\n", best / 10, ST_TILES[best % 10]); else REP("BEST none (use the CPU)\n");
    } else REP("BEST none (no exact setting: use the CPU)\n");
    free(S); free(plays); free(X); free(Yc); free(Yg); free(sc); free(C); free(YB); ptd_net_free(net);
    return best;
#undef REP
}

#ifdef VKG_JNI
/* Report text; its last line is "BEST fma=F tile=T" or "BEST none ...". */
JNIEXPORT jstring JNICALL FN(nativeSelfTest)(JNIEnv *env, jclass cls, jstring net, jstring bearoff, jint device) {
    const char *a = (*env)->GetStringUTFChars(env, net, NULL), *b = (*env)->GetStringUTFChars(env, bearoff, NULL);
    char *rep = calloc(1, 8192);
    vkg_selftest(a, b, device, 2000, rep, 8192);
    (*env)->ReleaseStringUTFChars(env, net, a); (*env)->ReleaseStringUTFChars(env, bearoff, b);
    jstring r = (*env)->NewStringUTF(env, rep); free(rep); return r;
}
/* The tile used by the next nativeOpen ("88", "48", "84" or "44"; the self-test's choice). */
JNIEXPORT void JNICALL FN(nativeSetTile)(JNIEnv *env, jclass cls, jstring tile) {
    const char *t = (*env)->GetStringUTFChars(env, tile, NULL); setenv("VKG_TILE", t, 1); (*env)->ReleaseStringUTFChars(env, tile, t);
}
#endif

#ifdef VKG_SELFTEST_MAIN
int main(int argc, char **argv) {
    if (argc < 3) { fprintf(stderr, "usage: vkg_selftest NET BEAROFF [device] [positions]\n"); return 2; }
    char rep[8192];
    int r = vkg_selftest(argv[1], argv[2], argc > 3 ? atoi(argv[3]) : -1, argc > 4 ? atoi(argv[4]) : 2000, rep, sizeof rep);
    fputs(rep, stdout);
    return r >= 0 ? 0 : 1;
}
#endif
