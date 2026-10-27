/*
 * bgengine: a small, stable C API over the GNU Backgammon evaluation engine.
 *
 * Conventions
 * -----------
 * Players are identified by absolute index 0 or 1 (the app uses 0 = human,
 * 1 = computer). Scores and cube ownership use these absolute indices.
 *
 * A board is always given from the point of view of the player on roll,
 * as 50 ints (gnubg's TanBoard flattened):
 *   board[0..24]  : checkers of the opponent (player NOT on roll)
 *   board[25..49] : checkers of the player on roll
 * Within each half, index i (0..23) is that player's own (i+1)-point and
 * index 24 is that player's bar. Borne-off checkers are implicit.
 *
 * A move is 8 ints (from, to) x 4 from the mover's point of view; points are
 * 0..23, 24 = bar, -1 = off (as "to") / unused pair terminated by from = -1.
 *
 * Equities are normalised (per unit cube; match equities are converted to
 * "equivalent to money game" (EMG) via gnubg's mwc2eq), which is the unit
 * eXtreme Gammon uses for errors and Performance Rating.
 *
 * Thread safety: none. All calls must come from one thread.
 */
#ifndef BG_ENGINE_H
#define BG_ENGINE_H

#ifdef __cplusplus
extern "C" {
#endif

#define BG_BOARD_INTS 50
#define BG_MOVE_INTS 8
#define BG_NUM_PROBS 5           /* win, win gammon, win bg, lose gammon, lose bg */

/* gnubg's predefined player strengths (aecSettings indices) */
enum bg_level {
    BG_LEVEL_BEGINNER = 0,
    BG_LEVEL_CASUAL = 1,         /* gnubg "Casual play" */
    BG_LEVEL_INTERMEDIATE = 2,
    BG_LEVEL_ADVANCED = 3,
    BG_LEVEL_EXPERT = 4,
    BG_LEVEL_WORLDCLASS = 5,
    BG_LEVEL_SUPREMO = 6,
    BG_LEVEL_GRANDMASTER = 7
};

typedef struct {
    int match_to;                /* 0 = money game */
    int score[2];                /* absolute player scores (match play) */
    int crawford;                /* 1 if this is the Crawford game */
    int jacoby;                  /* money only */
    int beavers;                 /* money only */
    int cube_value;              /* 1, 2, 4, ... */
    int cube_owner;              /* -1 = centred, otherwise absolute player index */
    int on_roll;                 /* absolute index of the player on roll */
} bg_cube_state;

typedef struct {
    int move[BG_MOVE_INTS];
    float equity;                /* cubeful normalised equity for the mover */
    float probs[BG_NUM_PROBS];   /* cubeless probabilities for the mover */
    int plies;                   /* ply depth this move was finally evaluated at */
} bg_move_eval;

typedef struct {
    /* normalised equities from the point of view of the player on roll */
    float no_double;
    float double_take;
    float double_pass;
    float optimal;
    int decision;                /* gnubg cubedecision enum value */
    int should_double;           /* 1 if doubling is correct */
    int should_take;             /* 1 if taking is correct (for the opponent) */
    int optional;                /* doubling and not doubling have the same equity */
    int cube_available;          /* 0 if the player on roll may not double */
    float probs[BG_NUM_PROBS];   /* cubeless probabilities for the player on roll */
} bg_cube_eval;

/*
 * Initialise the engine. data_dir must contain gnubg.weights (or gnubg.wd),
 * and optionally gnubg_os0.bd / gnubg_ts0.bd and met/<met_file>.
 * Returns 0 on success.
 */
int bg_init(const char *data_dir, const char *met_file);

/* All legal moves for the given dice (max-dice rules applied). Returns count. */
int bg_legal_moves(const int board[BG_BOARD_INTS], int d0, int d1, int out_moves[][BG_MOVE_INTS], int max_moves);

/*
 * Rank the moves for a roll at analysis depth `plies` (0..3) using gnubg's
 * standard move filters. If played_move is non-NULL that move is guaranteed
 * to be evaluated at full depth and its index in `out` is stored in
 * *played_index (or -1 if it is not a legal move).
 * Returns the number of moves written (sorted best first), 0 if no legal move,
 * or -1 on error.
 */
int bg_rank_moves(const int board[BG_BOARD_INTS], int d0, int d1, const bg_cube_state *cs, int plies,
                  const int played_move[BG_MOVE_INTS], int *played_index, bg_move_eval *out, int max_out);

/*
 * Scores positions right after a move by the player on roll (cs->on_roll), each given from the
 * mover's point of view (as after bg_apply_move), at gnubg depth `plies` (0..3). For each
 * position writes 6 floats to out: the cubeful normalised (EMG) equity for the mover and the
 * mover's 5 cubeless probabilities. Returns 0, or -1 on error.
 */
int bg_score_boards(const int boards[][BG_BOARD_INTS], int n, const bg_cube_state *cs, int plies, float *out);

/* The move the computer plays at the given strength. Returns 0 if a move was
 * found, 1 if there is no legal move, -1 on error. */
int bg_choose_move(const int board[BG_BOARD_INTS], int d0, int d1, const bg_cube_state *cs, int level,
                   int out_move[BG_MOVE_INTS]);

/* Cube decision for the player on roll (before rolling), at `plies` depth. */
int bg_cube_decision(const int board[BG_BOARD_INTS], const bg_cube_state *cs, int plies, bg_cube_eval *out);

/* Cube decision as seen by a computer player of the given strength. */
int bg_cube_decision_level(const int board[BG_BOARD_INTS], const bg_cube_state *cs, int level, bg_cube_eval *out);

/* Luck of a roll (normalised equity, + = lucky for the player on roll).
 * first_roll = 1 for the opening roll of a game (where doubles cannot occur). */
float bg_luck(const int board[BG_BOARD_INTS], int d0, int d1, const bg_cube_state *cs, int first_roll);

/* gnubg position ID (14 chars + NUL) and match ID (12 chars + NUL). */
void bg_position_id(const int board[BG_BOARD_INTS], char out[16]);
void bg_match_id(const bg_cube_state *cs, int d0, int d1, int doubled, int resigned, int game_over,
                 char out[16]);

/* Pip counts: out[0] = opponent, out[1] = player on roll. */
void bg_pip_count(const int board[BG_BOARD_INTS], unsigned int out[2]);

/* Apply a move (from the mover's perspective) to a board; returns 0 if legal. */
int bg_apply_move(int board[BG_BOARD_INTS], const int move[BG_MOVE_INTS]);

#ifdef __cplusplus
}
#endif
#endif /* BG_ENGINE_H */
