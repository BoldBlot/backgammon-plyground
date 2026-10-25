/*
 * ptd.h -- PureTD networks with 1- to 4-ply search (XG ply numbering).
 * Copyright (C) 2026 Bold Blot
 *
 * Networks are PureTD's (Alex Strehl, MIT licence, see LICENSE-PureTD); move generation is
 * PureTD's bg_engine.c. The network evaluation, the bear-off database and the search are ours.
 *
 * Positions use bg_engine's BoardState: state->turn is the player to act next. Probabilities are
 * (win, win gammon, win backgammon, lose gammon, lose backgammon) for a stated player.
 */
#ifndef PTD_H
#define PTD_H

#include "bg_engine.h"

#ifdef __cplusplus
extern "C" {
#endif

typedef struct ptd_net ptd_net;
typedef struct ptd_bearoff ptd_bearoff;

enum { PTD_PROB5 = 0, PTD_CUBEFUL = 1 };
enum { PTD_CENTRED = 0, PTD_MINE = 1, PTD_THEIRS = 2 };   /* cube owner, seen by the player on roll */
enum { PTD_MAX_PLIES = 4 };

/* Loads a .ptw file written by tools/export_puretd.py. NULL on failure, message in err. */
ptd_net *ptd_net_load(const char *path, char *err, int errlen);
void ptd_net_free(ptd_net *net);
int ptd_net_kind(const ptd_net *net);
long ptd_net_params(const ptd_net *net);

/*
 * One-sided bear-off database (all positions of up to 15 checkers on the six home points; the
 * distribution of the number of rolls to bear off, playing to minimise the expected number).
 * Read from cache_path if it holds a valid copy, otherwise built (about a second) and written
 * there (cache_path may be NULL: build, do not save).
 */
ptd_bearoff *ptd_bearoff_open(const char *cache_path);
void ptd_bearoff_free(ptd_bearoff *db);

typedef struct {
    const ptd_net *cubeless;    /* PTD_PROB5: checker play, probabilities */
    const ptd_net *cubeful;     /* PTD_CUBEFUL: money cube decisions (may be NULL) */
    const ptd_bearoff *bearoff; /* exact values for races with both sides bearing off (may be NULL) */
    int threads;                /* worker threads for the search (1 = none) */
} ptd_engine;

/*
 * Probabilities, for the player who just moved, of n positions s[i] (s[i].turn = the opponent,
 * about to roll), at 1 to PTD_MAX_PLIES plies: plies - 1 rolls, each side playing its 1-ply
 * cubeless best move, then the network. out has 5 floats per position. Finished games give their
 * result. Pre-roll probabilities for a player on roll: pass the position as it is (turn = that
 * player) and invert the result.
 */
void ptd_post_probs(const ptd_engine *e, const BoardState *s, int n, int plies, float *out);

/* Equity of a pre-roll position for the player on roll, cubeless, 1-ply. */
float ptd_eval(const ptd_engine *e, const BoardState *s, float probs[5]);

/*
 * Best play for s->turn with dice (d1, d2) at `plies` (cubeless): every move within `within` 1-ply
 * equity of the best is searched (within < 0: every legal move). Returns the index into
 * get_legal_plays()'s order, or -1 when there is no legal play.
 */
int ptd_best_move(const ptd_engine *e, const BoardState *s, int d1, int d2, int plies, float within, float *equity);

/*
 * Money cube decision for s->turn, cube owner `owner` (PTD_*), at 1 to PTD_MAX_PLIES plies:
 * cube-aware search (cube decisions at every inner node), cubeful net at the leaves, moves inside the tree picked by the cubeless net. nd and dt are per
 * unit of the current cube (dt already doubled; with PTD_THEIRS dt = nd). probs (nullable) gets the
 * cubeless pre-roll probabilities for the player on roll at the same depth. Jacoby applies while
 * the cube is centred. Returns 0, or -1 without a cubeful net.
 */
int ptd_cube(const ptd_engine *e, const BoardState *s, int plies, int owner, int jacoby,
             float *nd, float *dt, float probs[5]);

/*
 * Speed test on positions from a fixed self-play game (1-ply play from the start, every third
 * decision timed; a new game when one ends). kind 0 = checker play, 1 = cube decision, 2 = checker
 * play with doubles only; `within` is the checker root filter (see ptd_best_move). Stops early,
 * after the decision that crosses budget_ms (0 = no limit) or when ptd_bench_cancel() is called;
 * *done (nullable) gets the number of decisions timed. Returns milliseconds per decision timed.
 */
double ptd_bench(const ptd_engine *e, int kind, int plies, float within, int n, unsigned seed,
                 double budget_ms, int *done);

/* Asks a running ptd_bench to stop after its current decision (any thread); ptd_bench clears it. */
void ptd_bench_cancel(void);

#ifdef __cplusplus
}
#endif
#endif
