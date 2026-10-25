/*
 * ptd.c -- PureTD network evaluation and search (see ptd.h).
 * Copyright (C) 2026 Bold Blot
 */
#include "ptd.h"

#include <math.h>
#include <pthread.h>
#include <stdatomic.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#define MAX_LAYERS 8
#define ROWS 16            /* rows per forward block: each weight row is reused this many times */
#define MAX_KIDS 2048      /* legal plays per roll; the most seen is about 1200 */

enum { C = 0, M = 1, O = 2 };   /* cube perspective of the player on roll: centred, mine, theirs */

struct ptd_net {
    int kind, n_layers, in_size, max_dim;
    int dim_in[MAX_LAYERS + 1], dim_out[MAX_LAYERS + 1];
    float *wt[MAX_LAYERS + 1];      /* transposed: [in][out] */
    float *bias[MAX_LAYERS + 1];
};

/* ── loading ──────────────────────────────────────────────────────── */

static float half_to_float(unsigned short h) {
    unsigned sign = (h >> 15) & 1, exp = (h >> 10) & 0x1f, man = h & 0x3ff;
    float v;
    if (exp == 0) v = ldexpf((float)man, -24);                       /* subnormal */
    else if (exp == 31) v = man ? NAN : INFINITY;
    else v = ldexpf((float)(man | 0x400), (int)exp - 25);
    return sign ? -v : v;
}

/* Reads n weights stored as float32 or float16. */
static int read_floats(FILE *f, float *out, size_t n, int half) {
    if (!half) return fread(out, sizeof(float), n, f) == n;
    unsigned short *h = malloc(n * sizeof *h);
    int ok = fread(h, sizeof *h, n, f) == n;
    for (size_t i = 0; ok && i < n; i++) out[i] = half_to_float(h[i]);
    free(h);
    return ok;
}

ptd_net *ptd_net_load(const char *path, char *err, int errlen) {
    FILE *f = fopen(path, "rb");
    if (!f) { snprintf(err, errlen, "cannot open %s", path); return NULL; }
    char magic[4];
    int hdr[3];
    ptd_net *n = calloc(1, sizeof *n);
    int half = 0;
    if (fread(magic, 1, 4, f) != 4 || (memcmp(magic, "PTW1", 4) && !(half = !memcmp(magic, "PTW2", 4))) ||
        fread(hdr, sizeof(int), 3, f) != 3 ||
        hdr[1] < 1 || hdr[1] > MAX_LAYERS + 1) {
        snprintf(err, errlen, "%s: not a .ptw file", path); goto fail;
    }
    n->kind = hdr[0]; n->n_layers = hdr[1]; n->in_size = hdr[2];
    int in = n->in_size;
    n->max_dim = in;
    for (int l = 0; l < n->n_layers; l++) {
        int out;
        if (fread(&out, sizeof(int), 1, f) != 1 || out < 1 || out > 4096) { snprintf(err, errlen, "%s: bad layer", path); goto fail; }
        n->dim_in[l] = in; n->dim_out[l] = out;
        float *w = malloc((size_t)in * out * sizeof(float));
        n->wt[l] = malloc((size_t)in * out * sizeof(float));
        n->bias[l] = malloc(out * sizeof(float));
        if (!read_floats(f, w, (size_t)in * out, half) || !read_floats(f, n->bias[l], out, half)) {
            free(w); snprintf(err, errlen, "%s: truncated", path); goto fail;
        }
        for (int o = 0; o < out; o++)             /* file is [out][in] like torch */
            for (int i = 0; i < in; i++) n->wt[l][(size_t)i * out + o] = w[(size_t)o * in + i];
        free(w);
        if (out > n->max_dim) n->max_dim = out;
        in = out;
    }
    fclose(f);
    return n;
fail:
    fclose(f);
    ptd_net_free(n);
    return NULL;
}

void ptd_net_free(ptd_net *n) {
    if (!n) return;
    for (int l = 0; l <= MAX_LAYERS; l++) { free(n->wt[l]); free(n->bias[l]); }
    free(n);
}

int ptd_net_kind(const ptd_net *n) { return n->kind; }

long ptd_net_params(const ptd_net *n) {
    long p = 0;
    for (int l = 0; l < n->n_layers; l++) p += (long)n->dim_in[l] * n->dim_out[l] + n->dim_out[l];
    return p;
}

/* ── forward pass ─────────────────────────────────────────────────── */

typedef struct {
    float a[ROWS * 1024], b[ROWS * 1024];   /* activations of one block (max_dim <= 1024) */
    float feats[MAX_KIDS * 200];
    float out[MAX_KIDS * 5];
    Play plays[MAX_KIDS];
    BoardState live[MAX_KIDS];      /* kid_probs: unfinished resulting states */
    float q5[MAX_KIDS * 5];         /* kid_probs: their on-roll probabilities */
    float kp[MAX_KIDS * 5];         /* best_1ply: probabilities of every play */
} scratch;

/* Rows X[n][in_size] -> raw outputs Y[n][last dim]. Hidden layers ReLU, output linear. */
static void forward(const ptd_net *net, scratch *sc, const float *X, int n, float *Y) {
    int last = net->n_layers - 1, out_dim = net->dim_out[last];
    for (int r0 = 0; r0 < n; r0 += ROWS) {
        int rows = n - r0 < ROWS ? n - r0 : ROWS;
        const float *x = X + (size_t)r0 * net->in_size;
        int x_stride = net->in_size;
        for (int l = 0; l < net->n_layers; l++) {
            int in = net->dim_in[l], out = net->dim_out[l];
            float *y = (l % 2 == 0) ? sc->a : sc->b;
            for (int r = 0; r < rows; r++) memcpy(y + r * out, net->bias[l], out * sizeof(float));
            for (int i = 0; i < in; i++) {
                const float *w = net->wt[l] + (size_t)i * out;
                for (int r = 0; r < rows; r++) {
                    float v = x[r * x_stride + i];
                    if (v == 0.0f) continue;              /* inputs and ReLU outputs are mostly zero */
                    float *yr = y + r * out;
                    for (int o = 0; o < out; o++) yr[o] += v * w[o];
                }
            }
            if (l < last)
                for (int k = 0; k < rows * out; k++) if (y[k] < 0.0f) y[k] = 0.0f;
            x = y;
            x_stride = out;
        }
        memcpy(Y + (size_t)r0 * out_dim, x, (size_t)rows * out_dim * sizeof(float));
    }
}

static inline float sigm(float v) { return 1.0f / (1.0f + expf(-v)); }

/* prob5 raw outputs -> nested probabilities (PureTD's post-processing) and cubeless equity. */
static float prob5(const float *raw, float p[5]) {
    float w = sigm(raw[0]), wg = fminf(w, sigm(raw[1])), lg = fminf(1.0f - w, sigm(raw[3]));
    p[0] = w; p[1] = wg; p[2] = fminf(wg, sigm(raw[2])); p[3] = lg; p[4] = fminf(lg, sigm(raw[4]));
    return 2.0f * p[0] - 1.0f + p[1] + p[2] - p[3] - p[4];
}

/* ── positions ────────────────────────────────────────────────────── */

static const int D1[21] = {1,1,1,1,1,1,2,2,2,2,2,3,3,3,3,4,4,4,5,5,6};
static const int D2[21] = {1,2,3,4,5,6,2,3,4,5,6,3,4,5,6,4,5,6,5,6,6};
static inline float roll_w(int r) { return (D1[r] == D2[r] ? 1.0f : 2.0f) / 36.0f; }

/* Points won by the winner of a finished game (1/2/3), 0 if not finished. */
static int game_points(const BoardState *s) {
    int w = s->off[WHITE] == NUM_CHECKERS ? WHITE : s->off[BLACK] == NUM_CHECKERS ? BLACK : -1;
    if (w < 0) return 0;
    int loser = 1 - w;
    if (s->off[loser] > 0) return 1;
    if (s->bar[loser] > 0) return 3;
    for (int p = 0; p < 6; p++) {
        int q = w == WHITE ? p : 23 - p;                  /* the winner's home board */
        if (w == WHITE ? s->points[q] < 0 : s->points[q] > 0) return 3;
    }
    return 2;
}

/* Probabilities of a win by `pts` points (for the winner) and of the matching loss. */
static void win_probs(int pts, float p[5]) { p[0] = 1; p[1] = pts >= 2; p[2] = pts >= 3; p[3] = p[4] = 0; }
static void loss_probs(int pts, float p[5]) { p[0] = p[1] = p[2] = 0; p[3] = pts >= 2; p[4] = pts >= 3; }
static void invert5(const float a[5], float b[5]) { float t[5] = {1 - a[0], a[3], a[4], a[1], a[2]}; memcpy(b, t, sizeof t); }
static inline float equity5(const float p[5]) { return 2 * p[0] - 1 + p[1] + p[2] - p[3] - p[4]; }

/* ── bear-off database ────────────────────────────────────────────── */

#define BO_N 54264          /* positions of 0..15 checkers on 6 points */
#define BO_ROLLS 32         /* P(off in exactly k rolls), k = 1..32 */

struct ptd_bearoff {
    unsigned keys[BO_N];            /* 4 bits per point, sorted */
    float dist[BO_N][BO_ROLLS];
    float mean[BO_N];
};

static unsigned bo_key(const int c[6]) {
    unsigned k = 0;
    for (int i = 0; i < 6; i++) k |= (unsigned)c[i] << (4 * i);
    return k;
}

static int bo_index(const ptd_bearoff *db, unsigned key) {
    int lo = 0, hi = BO_N - 1;
    while (lo <= hi) {
        int mid = (lo + hi) / 2;
        if (db->keys[mid] == key) return mid;
        if (db->keys[mid] < key) lo = mid + 1; else hi = mid - 1;
    }
    return -1;
}

static int cmp_u(const void *a, const void *b) {
    unsigned x = *(const unsigned *)a, y = *(const unsigned *)b;
    return x < y ? -1 : x > y;
}

static int pips_of(unsigned key) {
    int p = 0;
    for (int i = 0; i < 6; i++) p += ((key >> (4 * i)) & 15) * (i + 1);
    return p;
}

static int cmp_pips(const void *a, const void *b) {
    int x = pips_of(*(const unsigned *)a), y = pips_of(*(const unsigned *)b);
    return x - y;
}

static void bo_build(ptd_bearoff *db) {
    int n = 0, c[6];
    for (c[0] = 0; c[0] <= 15; c[0]++) for (c[1] = 0; c[0] + c[1] <= 15; c[1]++)
    for (c[2] = 0; c[0] + c[1] + c[2] <= 15; c[2]++) for (c[3] = 0; c[0] + c[1] + c[2] + c[3] <= 15; c[3]++)
    for (c[4] = 0; c[0] + c[1] + c[2] + c[3] + c[4] <= 15; c[4]++)
    for (c[5] = 0; c[0] + c[1] + c[2] + c[3] + c[4] + c[5] <= 15; c[5]++) db->keys[n++] = bo_key(c);
    qsort(db->keys, BO_N, sizeof(unsigned), cmp_u);
    unsigned *order = malloc(BO_N * sizeof *order);              /* fewest pips first */
    memcpy(order, db->keys, BO_N * sizeof *order);
    qsort(order, BO_N, sizeof *order, cmp_pips);
    Play *plays = malloc(MAX_KIDS * sizeof *plays);
    for (int t = 0; t < BO_N; t++) {
        int idx = bo_index(db, order[t]);
        memset(db->dist[idx], 0, sizeof db->dist[idx]);
        if (order[t] == 0) { db->mean[idx] = 0; continue; }
        BoardState s;
        memset(&s, 0, sizeof s);
        int on = 0;
        for (int i = 0; i < 6; i++) { s.points[i] = (order[t] >> (4 * i)) & 15; on += s.points[i]; }
        s.off[WHITE] = NUM_CHECKERS - on;
        s.off[BLACK] = NUM_CHECKERS;
        s.turn = WHITE;
        double mean = 1;
        for (int r = 0; r < 21; r++) {
            int k = get_legal_plays(&s, D1[r], D2[r], plays, MAX_KIDS), best = -1;
            float bm = 1e9f;
            for (int j = 0; j < k; j++) {
                int cc[6];
                for (int i = 0; i < 6; i++) cc[i] = plays[j].resulting_state.points[i];
                int b = bo_index(db, bo_key(cc));
                if (db->mean[b] < bm) { bm = db->mean[b]; best = b; }
            }
            float w = roll_w(r);
            if (best < 0) best = idx;                             /* cannot happen with checkers home */
            mean += w * db->mean[best];
            if (db->keys[best] == 0) db->dist[idx][0] += w;
            else for (int q = 0; q + 1 < BO_ROLLS; q++) db->dist[idx][q + 1] += w * db->dist[best][q];
        }
        db->mean[idx] = (float)mean;
    }
    free(plays);
    free(order);
}

ptd_bearoff *ptd_bearoff_open(const char *cache_path) {
    ptd_bearoff *db = malloc(sizeof *db);
    if (cache_path) {
        FILE *f = fopen(cache_path, "rb");
        if (f) {
            char magic[4];
            int ok = fread(magic, 1, 4, f) == 4 && !memcmp(magic, "PBO1", 4) && fread(db, sizeof *db, 1, f) == 1;
            fclose(f);
            if (ok) return db;
        }
    }
    bo_build(db);
    if (cache_path) {
        FILE *f = fopen(cache_path, "wb");
        if (f) { fwrite("PBO1", 1, 4, f); fwrite(db, sizeof *db, 1, f); fclose(f); }
    }
    return db;
}

void ptd_bearoff_free(ptd_bearoff *db) { free(db); }

/* Exact probabilities for the player on roll when both sides are bearing off, no contact, and
 * both have borne off at least one checker (no gammons possible). Returns 0 if not such a race. */
static int bo_probs(const ptd_bearoff *db, const BoardState *s, float p[5]) {
    if (!db || s->bar[0] || s->bar[1] || s->off[0] == 0 || s->off[1] == 0) return 0;
    int me = s->turn, ca[6] = {0}, cb[6] = {0};
    for (int i = 0; i < 24; i++) {
        int v = s->points[i];
        if (!v) continue;
        int white = v > 0, n = white ? v : -v;
        int pt = white ? i : 23 - i;                              /* point index from its owner's side */
        if (pt > 5) return 0;
        if ((white ? WHITE : BLACK) == me) ca[pt] = n; else cb[pt] = n;
    }
    int a = bo_index(db, bo_key(ca)), b = bo_index(db, bo_key(cb));
    if (a < 0 || b < 0) return 0;
    double win = 0, b_done = 0;                                   /* P(on roll finishes first) */
    for (int k = 0; k < BO_ROLLS; k++) {
        win += db->dist[a][k] * (1 - b_done);
        b_done += db->dist[b][k];
    }
    p[0] = (float)win; p[1] = p[2] = p[3] = p[4] = 0;
    return 1;
}

/* ── evaluation ───────────────────────────────────────────────────── */

/* Probabilities for the player on roll of n unfinished positions. */
static void onroll_probs(const ptd_engine *e, scratch *sc, const BoardState *s, int n, float *out) {
    int m = 0, idx[MAX_KIDS];
    for (int k = 0; k < n; k++) {
        if (bo_probs(e->bearoff, &s[k], out + 5 * k)) continue;
        encode_state(&s[k], sc->feats + (size_t)m * NUM_FEATURES_196);
        idx[m++] = k;
    }
    if (!m) return;
    forward(e->cubeless, sc, sc->feats, m, sc->out);
    for (int j = 0; j < m; j++) prob5(sc->out + j * 5, out + 5 * idx[j]);
}

/* Probabilities for the mover of each play's resulting state (opponent on roll). */
static void kid_probs(const ptd_engine *e, scratch *sc, const Play *kids, int n, float *p) {
    BoardState *live = sc->live;
    int m = 0, idx[MAX_KIDS];
    for (int k = 0; k < n; k++) {
        int pts = game_points(&kids[k].resulting_state);
        if (pts) { win_probs(pts, p + 5 * k); continue; }
        live[m] = kids[k].resulting_state;
        idx[m++] = k;
    }
    if (!m) return;
    onroll_probs(e, sc, live, m, sc->q5);
    for (int j = 0; j < m; j++) invert5(sc->q5 + 5 * j, p + 5 * idx[j]);
}

/* Best play by 1-ply cubeless equity; *next gets the resulting state (s with the turn switched on a
 * forced pass) and probs (nullable) its probabilities for the mover. Returns the index or -1. */
static int best_1ply(const ptd_engine *e, scratch *sc, const BoardState *s, int d1, int d2, BoardState *next, float probs[5]) {
    int n = get_legal_plays(s, d1, d2, sc->plays, MAX_KIDS);
    if (n <= 0) {
        *next = *s;
        board_switch_turn(next);
        if (probs) { float q[5]; onroll_probs(e, sc, next, 1, q); invert5(q, probs); }
        return -1;
    }
    float *p = sc->kp;
    kid_probs(e, sc, sc->plays, n, p);
    int b = 0;
    for (int k = 1; k < n; k++) if (equity5(p + 5 * k) > equity5(p + 5 * b)) b = k;
    *next = sc->plays[b].resulting_state;
    if (probs) memcpy(probs, p + 5 * b, 5 * sizeof(float));
    return b;
}

float ptd_eval(const ptd_engine *e, const BoardState *s, float probs[5]) {
    scratch *sc = malloc(sizeof *sc);
    float p[5];
    onroll_probs(e, sc, s, 1, p);
    if (probs) memcpy(probs, p, sizeof p);
    free(sc);
    return equity5(p);
}

/* ── worker pool for the search ────────────────────────────────────────── */

typedef struct pool pool;
typedef void (*task_fn)(pool *, scratch *, int task);
struct pool {
    const ptd_engine *e;
    task_fn fn;
    int n_tasks;
    atomic_int next;
    const BoardState *roots;
    float *out;                 /* per task results */
    int jacoby;
    int plies;
};

static void *worker(void *arg) {
    pool *p = arg;
    scratch *sc = malloc(sizeof *sc);
    for (int t; (t = atomic_fetch_add(&p->next, 1)) < p->n_tasks;) p->fn(p, sc, t);
    free(sc);
    return NULL;
}

static void run_pool(pool *p) {
    atomic_store(&p->next, 0);
    int n = p->e->threads < 1 ? 1 : p->e->threads;
    if (n > p->n_tasks) n = p->n_tasks;
    if (n > 32) n = 32;
    pthread_t th[32];
    pthread_attr_t at;
    pthread_attr_init(&at);
    pthread_attr_setstacksize(&at, 1 << 20);
    for (int i = 1; i < n; i++) pthread_create(&th[i], &at, worker, p);
    pthread_attr_destroy(&at);
    worker(p);
    for (int i = 1; i < n; i++) pthread_join(th[i], NULL);
}

/* ── n-ply probabilities of post-move positions ──────────────────── */

static void post_rec(const ptd_engine *e, scratch *sc, const BoardState *s, int plies, float out[5]);

/* Probabilities for the roller (s->turn) after rolling r at s and playing its 1-ply best move,
 * the resulting position searched plies - 1 deep. */
static void roll_probs(const ptd_engine *e, scratch *sc, const BoardState *s, int r, int plies, float q[5]) {
    BoardState s1;
    if (plies <= 2) { best_1ply(e, sc, s, D1[r], D2[r], &s1, q); return; }
    best_1ply(e, sc, s, D1[r], D2[r], &s1, NULL);
    post_rec(e, sc, &s1, plies - 1, q);
}

/* Probabilities for the player who just moved of s (s->turn about to roll), plies deep: plies - 1
 * rolls, each side playing its 1-ply best move, then the network. */
static void post_rec(const ptd_engine *e, scratch *sc, const BoardState *s, int plies, float out[5]) {
    int pts = game_points(s);
    if (pts) { win_probs(pts, out); return; }
    float acc[5] = {0}, q[5];
    if (plies <= 1) {
        onroll_probs(e, sc, s, 1, acc);
    } else {
        for (int r = 0; r < 21; r++) {
            roll_probs(e, sc, s, r, plies, q);
            for (int i = 0; i < 5; i++) acc[i] += roll_w(r) * q[i];
        }
    }
    invert5(acc, out);
}

/* Task (position c, roll r of the side to move): its probabilities weighted by r's chance. */
static void post_task(pool *p, scratch *sc, int t) {
    int c = t / 21, r = t % 21;
    float q[5];
    roll_probs(p->e, sc, &p->roots[c], r, p->plies, q);
    for (int i = 0; i < 5; i++) p->out[5 * t + i] = roll_w(r) * q[i];
}

void ptd_post_probs(const ptd_engine *e, const BoardState *s, int n, int plies, float *out) {
    if (plies > PTD_MAX_PLIES) plies = PTD_MAX_PLIES;
    scratch *sc = malloc(sizeof *sc);
    for (int k = 0; k < n; k++) {
        int pts = game_points(&s[k]);
        if (pts) { win_probs(pts, out + 5 * k); continue; }
        if (plies < 2) post_rec(e, sc, &s[k], 1, out + 5 * k);
    }
    free(sc);
    if (plies < 2 || n == 0) return;
    float *part = malloc((size_t)n * 21 * 5 * sizeof(float));
    pool p = {.e = e, .fn = post_task, .n_tasks = n * 21, .roots = s, .out = part, .plies = plies};
    run_pool(&p);
    for (int k = 0; k < n; k++) {
        if (game_points(&s[k])) continue;
        float acc[5] = {0};
        for (int i = 0; i < 5; i++)
            for (int r = 0; r < 21; r++) acc[i] += part[(k * 21 + r) * 5 + i];
        invert5(acc, out + 5 * k);
    }
    free(part);
}

int ptd_best_move(const ptd_engine *e, const BoardState *s, int d1, int d2, int plies, float within, float *equity) {
    scratch *sc = malloc(sizeof *sc);
    int n = get_legal_plays(s, d1, d2, sc->plays, MAX_KIDS);
    if (n <= 0) { free(sc); return -1; }
    float *p = malloc((size_t)n * 5 * sizeof(float)), *v = malloc(n * sizeof(float));
    kid_probs(e, sc, sc->plays, n, p);
    for (int k = 0; k < n; k++) v[k] = equity5(p + 5 * k);
    int best = 0;
    for (int k = 1; k < n; k++) if (v[k] > v[best]) best = k;
    if (plies >= 2 && n > 1) {
        int *cand = malloc(n * sizeof *cand), nc = 0;
        for (int k = 0; k < n; k++) if (within < 0 || v[k] >= v[best] - within) cand[nc++] = k;
        BoardState *roots = malloc(nc * sizeof *roots);
        float *q = malloc((size_t)nc * 5 * sizeof(float));
        for (int i = 0; i < nc; i++) roots[i] = sc->plays[cand[i]].resulting_state;
        ptd_post_probs(e, roots, nc, plies, q);
        best = cand[0];
        for (int i = 0; i < nc; i++) {
            v[cand[i]] = equity5(q + 5 * i);
            if (v[cand[i]] > v[best]) best = cand[i];
        }
        free(cand); free(roots); free(q);
    }
    if (equity) *equity = v[best];
    free(p); free(v); free(sc);
    return best;
}

/* ── cube-aware n-ply money cube decisions (cubeful net) ──────────── */

/* Cubeful per-unit equity of pre-roll states for the player on roll, cube perspective `persp`,
 * may-double flag `act`. */
static void cubeful_values(const ptd_net *net, scratch *sc, const BoardState *s, int n, int persp, int act, float *v) {
    for (int k = 0; k < n; k++) {
        float *x = sc->feats + (size_t)k * 200;
        encode_state(&s[k], x);
        x[196] = x[197] = x[198] = 0.0f;
        x[196 + persp] = 1.0f;
        x[199] = act ? 1.0f : 0.0f;
    }
    forward(net, sc, sc->feats, n, v);
}

/* PureTD's cubeful net was trained without Jacoby: its centred values count gammons and can go
 * above 1 ("too good"). With Jacoby a centred cube is worth at most 1, so the side on roll cashes
 * instead; the cap sits just below 1 so that ties go to doubling. */
#define JACOBY_CAP (1.0f - 1e-4f)
static inline float jacoby_centred(float v, int jacoby) { return jacoby && v > JACOBY_CAP ? JACOBY_CAP : v; }

static inline float collapse(float nd, float dt) {
    float best = dt < 1.0f ? dt : 1.0f;
    return best > nd ? best : nd;
}

/* Values of a finished game for the side that just won `pts` points: v[C], v[M], v[O] per unit. */
static void won_values(int pts, int jacoby, float v[3]) {
    v[C] = jacoby ? 1.0f : (float)pts;
    v[M] = v[O] = (float)pts;
}

/* Turns the next player's values (and probabilities) into the previous player's. */
static void flip_values(const float in[3], const float pin[5], float out[3], float pout[5]) {
    float c = -in[C], m = -in[O], o = -in[M];
    out[C] = c; out[M] = m; out[O] = o;
    invert5(pin, pout);
}

static void cube_nd(const ptd_engine *e, scratch *sc, const BoardState *s, int plies, int jacoby, float nd[3], float probs[5]);

/* Cube-aware values for the player on roll at s, cube decision included: plies 1 = cubeful net;
 * more = no-double values one roll deeper, then the doubling choice. */
static void cube_values(const ptd_engine *e, scratch *sc, const BoardState *s, int plies, int jacoby, float v[3], float probs[5]) {
    if (plies <= 1) {
        cubeful_values(e->cubeful, sc, s, 1, PTD_CENTRED, 1, &v[C]);
        v[C] = jacoby_centred(v[C], jacoby);
        cubeful_values(e->cubeful, sc, s, 1, PTD_MINE, 1, &v[M]);
        cubeful_values(e->cubeful, sc, s, 1, PTD_THEIRS, 0, &v[O]);
        onroll_probs(e, sc, s, 1, probs);
        return;
    }
    float nd[3];
    cube_nd(e, sc, s, plies, jacoby, nd, probs);
    v[C] = collapse(nd[C], 2 * nd[O]);
    v[M] = collapse(nd[M], 2 * nd[O]);
    v[O] = nd[O];
}

/* The roller's values after rolling r at s and playing its 1-ply cubeless best move (the
 * cubeful net picks moves clearly worse: cube error 10.4 vs 3.7 on the hard FlashBack positions);
 * the opponent's position searched plies - 1 deep. */
static void cube_roll(const ptd_engine *e, scratch *sc, const BoardState *s, int r, int plies, int jacoby, float v[3], float probs[5]) {
    BoardState s1;
    best_1ply(e, sc, s, D1[r], D2[r], &s1, NULL);
    int pts = game_points(&s1);
    if (pts) { won_values(pts, jacoby, v); win_probs(pts, probs); return; }
    float w[3], q[5];
    cube_values(e, sc, &s1, plies - 1, jacoby, w, q);
    flip_values(w, q, v, probs);
}

/* No-double values for the player on roll at s (average over its rolls), plies deep. At plies 2
 * the 21 leaves go through the networks in one batch. */
static void cube_nd(const ptd_engine *e, scratch *sc, const BoardState *s, int plies, int jacoby, float nd[3], float probs[5]) {
    float v[21][3], pr[21][5];
    if (plies == 2) {
        BoardState s1[21];
        int live[21], m = 0;
        for (int r = 0; r < 21; r++) {
            best_1ply(e, sc, s, D1[r], D2[r], &s1[m], NULL);
            int pts = game_points(&s1[m]);
            if (pts) { won_values(pts, jacoby, v[r]); win_probs(pts, pr[r]); live[r] = -1; }
            else live[r] = m++;
        }
        float vc[21], vm[21], vo[21], q[21 * 5];
        if (m) {
            cubeful_values(e->cubeful, sc, s1, m, PTD_CENTRED, 1, vc);
            cubeful_values(e->cubeful, sc, s1, m, PTD_MINE, 1, vm);
            cubeful_values(e->cubeful, sc, s1, m, PTD_THEIRS, 0, vo);
            onroll_probs(e, sc, s1, m, q);
        }
        for (int r = 0; r < 21; r++) {
            int j = live[r];
            if (j < 0) continue;
            float w[3] = {jacoby_centred(vc[j], jacoby), vm[j], vo[j]};
            flip_values(w, q + 5 * j, v[r], pr[r]);
        }
    } else {
        for (int r = 0; r < 21; r++) cube_roll(e, sc, s, r, plies, jacoby, v[r], pr[r]);
    }
    memset(nd, 0, 3 * sizeof(float));
    memset(probs, 0, 5 * sizeof(float));
    for (int r = 0; r < 21; r++) {
        for (int i = 0; i < 3; i++) nd[i] += roll_w(r) * v[r][i];
        for (int i = 0; i < 5; i++) probs[i] += roll_w(r) * pr[r][i];
    }
}

/* Task r (our roll at the root): 8 floats, our values v[C], v[M], v[O] and our cubeless
 * probabilities (5), unweighted. */
static void cube_task(pool *p, scratch *sc, int r) {
    float *o = p->out + 8 * r;
    cube_roll(p->e, sc, p->roots, r, p->plies, p->jacoby, o, o + 3);
}

int ptd_cube(const ptd_engine *e, const BoardState *s, int plies, int owner, int jacoby,
             float *nd, float *dt, float probs[5]) {
    if (!e->cubeful) return -1;
    if (plies > PTD_MAX_PLIES) plies = PTD_MAX_PLIES;
    if (plies < 2) {
        scratch *sc = malloc(sizeof *sc);
        float a, b;
        cubeful_values(e->cubeful, sc, s, 1, owner, 0, &a);
        cubeful_values(e->cubeful, sc, s, 1, PTD_THEIRS, 0, &b);
        if (probs) onroll_probs(e, sc, s, 1, probs);
        free(sc);
        *nd = owner == PTD_CENTRED ? jacoby_centred(a, jacoby) : a;
        *dt = owner == PTD_THEIRS ? a : 2 * b;
        return 0;
    }
    float part[21 * 8];
    pool p = {.e = e, .fn = cube_task, .n_tasks = 21, .roots = s, .out = part, .jacoby = jacoby, .plies = plies};
    run_pool(&p);
    float h[3] = {0}, pr[5] = {0};
    for (int r = 0; r < 21; r++) {
        for (int i = 0; i < 3; i++) h[i] += roll_w(r) * part[8 * r + i];
        for (int i = 0; i < 5; i++) pr[i] += roll_w(r) * part[8 * r + 3 + i];
    }
    *nd = owner == PTD_CENTRED ? h[C] : owner == PTD_MINE ? h[M] : h[O];
    *dt = owner == PTD_THEIRS ? *nd : 2 * h[O];
    if (probs) memcpy(probs, pr, sizeof pr);
    return 0;
}

/* ── speed test ───────────────────────────────────────────────────── */

static double now_ms(void) {
    struct timespec t;
    clock_gettime(CLOCK_MONOTONIC, &t);
    return t.tv_sec * 1e3 + t.tv_nsec / 1e6;
}

static atomic_int bench_cancelled;

void ptd_bench_cancel(void) { atomic_store(&bench_cancelled, 1); }

double ptd_bench(const ptd_engine *e, int kind, int plies, float within, int n, unsigned seed,
                 double budget_ms, int *done_out) {
    scratch *sc = malloc(sizeof *sc);
    BoardState s;
    board_init(&s);
    double total = 0;
    int done = 0, step = 0;
    unsigned r = seed ? seed : 1;
    atomic_store(&bench_cancelled, 0);
    while (done < n && !atomic_load(&bench_cancelled) && !(budget_ms > 0 && total >= budget_ms)) {
        r = r * 1103515245u + 12345u; int d1 = (r >> 16) % 6 + 1;
        r = r * 1103515245u + 12345u; int d2 = (r >> 16) % 6 + 1;
        if (++step % 3 == 0) {
            if (kind == 2) d2 = d1;
            double t = now_ms();
            float a, b;
            if (kind == 1) ptd_cube(e, &s, plies, PTD_CENTRED, 1, &a, &b, NULL);
            else ptd_best_move(e, &s, d1, d2, plies, within, NULL);
            total += now_ms() - t;
            done++;
        }
        BoardState next;
        best_1ply(e, sc, &s, d1, d2, &next, NULL);
        s = next;
        if (game_points(&s)) board_init(&s);
    }
    free(sc);
    if (done_out) *done_out = done;
    return done ? total / done : 0;
}
