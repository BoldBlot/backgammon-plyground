/*
 * bgengine: implementation of the C API declared in bgengine.h on top of the
 * vendored GNU Backgammon engine (engine/gnubg, GPLv3).
 */
#include "config.h"
#include "bgengine.h"

#include <string.h>
#include <math.h>

#include "backgammon.h"
#include "eval.h"
#include "positionid.h"
#include "matchid.h"
#include "matchequity.h"
#include "multithread.h"
#include "util.h"

/* ---- glue required by the vendored gnubg files ------------------------ */

/* (ThreadData td is defined by gnubg's mtsupport.c) */
static char *data_dir = NULL;

char *
getPkgDataDir(void)
{
    return data_dir ? data_dir : (char *) ".";
}

void
PrintError(const char *message)
{
    g_printerr("%s", message);
}

/* gnubg's output.c is not vendored; route its error output to our logger. */
void
outputerrf(const char *sz, ...)
{
    va_list ap;
    va_start(ap, sz);
    char *s = g_strdup_vprintf(sz, ap);
    va_end(ap);
    g_printerr("%s", s);
    g_free(s);
}

/* ---- helpers ----------------------------------------------------------- */

/* 0-ply cubeful evaluation, as used by gnubg for luck analysis */
static evalcontext ec_luck = { TRUE, 0, FALSE, TRUE, 0.0f };

/* XG's "error" threshold; moves worse than this are re-scored at full depth */
static const float RESCORE_THRESHOLD = 0.020f;

/* Index of gnubg's "normal" move filter (used by World Class) */
#define MOVEFILTER_NORMAL 2

static void
to_tanboard(const int b[BG_BOARD_INTS], TanBoard an)
{
    for (int i = 0; i < 25; i++) {
        an[0][i] = (unsigned int) b[i];
        an[1][i] = (unsigned int) b[25 + i];
    }
}

static void
from_tanboard(const TanBoard an, int b[BG_BOARD_INTS])
{
    for (int i = 0; i < 25; i++) {
        b[i] = (int) an[0][i];
        b[25 + i] = (int) an[1][i];
    }
}

static int
make_cubeinfo(const bg_cube_state *cs, cubeinfo *ci)
{
    int score[2] = { cs->score[0], cs->score[1] };
    return SetCubeInfo(ci, cs->cube_value, cs->cube_owner, cs->on_roll, cs->match_to, score,
                       cs->crawford, cs->jacoby, cs->beavers, VARIATION_STANDARD);
}

static evalcontext
analysis_context(int plies)
{
    evalcontext ec = { TRUE, 0, FALSE, TRUE, 0.0f };
    if (plies < 0)
        plies = 0;
    if (plies > 3)
        plies = 3;
    ec.nPlies = (unsigned int) plies;
    ec.fUsePrune = plies > 0;
    return ec;
}

static int
clamp_level(int level)
{
    if (level < BG_LEVEL_BEGINNER)
        return BG_LEVEL_BEGINNER;
    if (level > BG_LEVEL_GRANDMASTER)
        return BG_LEVEL_GRANDMASTER;
    return level;
}

/* gnubg terminates a move with a single -1; make every unused slot -1. */
static void
copy_move(int dst[BG_MOVE_INTS], const int src[BG_MOVE_INTS])
{
    int ended = 0;
    for (int i = 0; i < 4; i++) {
        if (src[2 * i] < 0)
            ended = 1;
        dst[2 * i] = ended ? -1 : src[2 * i];
        dst[2 * i + 1] = ended ? -1 : src[2 * i + 1];
    }
}

static void
move_key(const TanBoard an, const int move[BG_MOVE_INTS], positionkey *key)
{
    TanBoard tmp;
    memcpy(tmp, an, sizeof(TanBoard));
    ApplyMove(tmp, move, FALSE);
    PositionKey((ConstTanBoard) tmp, key);
}

/* ---- API ----------------------------------------------------------------- */

int
bg_init(const char *dir, const char *met_file)
{
    static int initialised = FALSE;
    if (initialised)
        return 0;

    g_free(data_dir);
    data_dir = g_strdup(dir);

    char *weights = g_build_filename(dir, "gnubg.weights", NULL);
    char *weights_bin = g_build_filename(dir, "gnubg.wd", NULL);
    EvalInitialise(weights, weights_bin, FALSE, NULL);
    g_free(weights);
    g_free(weights_bin);

    /* EvalInitialise reports failures only through output; verify the nets loaded. */
    if (nnContact.cInput == 0 || nnRace.cInput == 0) {
        g_printerr("bg_init: neural net weights could not be loaded from %s", dir);
        return -1;
    }

    MT_InitThreads();

    char *met = g_build_filename(dir, "met", met_file ? met_file : "Kazaross-XG2.xml", NULL);
    InitMatchEquity(met);
    g_free(met);

    initialised = TRUE;
    return 0;
}

int
bg_legal_moves(const int board[BG_BOARD_INTS], int d0, int d1, int out_moves[][BG_MOVE_INTS], int max_moves)
{
    TanBoard an;
    movelist ml;
    to_tanboard(board, an);
    GenerateMoves(&ml, (ConstTanBoard) an, d0, d1, FALSE);
    int n = (int) ml.cMoves;
    for (int i = 0; i < n && i < max_moves; i++)
        copy_move(out_moves[i], ml.amMoves[i].anMove);
    return n;
}

int
bg_rank_moves(const int board[BG_BOARD_INTS], int d0, int d1, const bg_cube_state *cs, int plies,
              const int played_move[BG_MOVE_INTS], int *played_index, bg_move_eval *out, int max_out)
{
    TanBoard an;
    cubeinfo ci;
    movelist ml;
    positionkey key;
    evalcontext ec = analysis_context(plies);

    to_tanboard(board, an);
    if (make_cubeinfo(cs, &ci) < 0)
        return -1;
    if (played_move)
        move_key((ConstTanBoard) an, played_move, &key);
    if (played_index)
        *played_index = -1;

    if (FindnSaveBestMoves(&ml, d0, d1, (ConstTanBoard) an, played_move ? &key : NULL, RESCORE_THRESHOLD, &ci,
                           &ec, aaamfMoveFilterSettings[MOVEFILTER_NORMAL]) < 0) {
        g_free(ml.amMoves);
        return -1;
    }

    int n = (int) ml.cMoves;
    int written = 0;
    for (int i = 0; i < n; i++) {
        const move *m = &ml.amMoves[i];
        if (played_move && played_index && EqualKeys(key, m->key))
            *played_index = i < max_out ? i : -1;
        if (i >= max_out)
            continue;
        copy_move(out[i].move, m->anMove);
        out[i].equity = m->rScore;
        for (int j = 0; j < BG_NUM_PROBS; j++)
            out[i].probs[j] = m->arEvalMove[j];
        out[i].plies = (int) m->esMove.ec.nPlies;
        written++;
    }
    g_free(ml.amMoves);
    return written;
}

int
bg_score_boards(const int boards[][BG_BOARD_INTS], int n, const bg_cube_state *cs, int plies, float *out)
{
    cubeinfo ci;
    evalcontext ec = analysis_context(plies);
    if (make_cubeinfo(cs, &ci) < 0)
        return -1;
    for (int i = 0; i < n; i++) {
        TanBoard an;
        move m;
        memset(&m, 0, sizeof(m));
        to_tanboard(boards[i], an);
        PositionKey((ConstTanBoard) an, &m.key);
        if (ScoreMove(NULL, &m, &ci, &ec, (int) ec.nPlies) < 0)
            return -1;
        out[6 * i] = m.rScore;
        for (int j = 0; j < BG_NUM_PROBS; j++)
            out[6 * i + 1 + j] = m.arEvalMove[j];
    }
    return 0;
}

int
bg_choose_move(const int board[BG_BOARD_INTS], int d0, int d1, const bg_cube_state *cs, int level,
               int out_move[BG_MOVE_INTS])
{
    TanBoard an;
    cubeinfo ci;
    level = clamp_level(level);
    evalcontext ec = aecSettings[level];
    int filter = aiSettingsMoveFilter[level];

    to_tanboard(board, an);
    if (make_cubeinfo(cs, &ci) < 0)
        return -1;

    int raw[BG_MOVE_INTS];
    for (int i = 0; i < BG_MOVE_INTS; i++)
        raw[i] = -1;
    int result = FindBestMove(raw, d0, d1, an, &ci, &ec,
                              aaamfMoveFilterSettings[filter < 0 ? MOVEFILTER_NORMAL : filter]);
    if (result < 0)
        return -1;
    copy_move(out_move, raw);
    return out_move[0] < 0 ? 1 : 0;
}

static int
cube_decision(const int board[BG_BOARD_INTS], const bg_cube_state *cs, evalcontext *ec, bg_cube_eval *out)
{
    TanBoard an;
    cubeinfo ci;
    float aarOutput[2][NUM_ROLLOUT_OUTPUTS];
    float arDouble[4];
    evalsetup es;

    memset(out, 0, sizeof(*out));
    to_tanboard(board, an);
    if (make_cubeinfo(cs, &ci) < 0)
        return -1;

    out->cube_available = GetDPEq(NULL, NULL, &ci) ? 1 : 0;

    es.et = EVAL_EVAL;
    es.ec = *ec;
    if (GeneralCubeDecisionE(aarOutput, (ConstTanBoard) an, &ci, ec, &es) < 0)
        return -1;

    cubedecision cd = FindCubeDecision(arDouble, aarOutput, &ci);

    out->optimal = arDouble[OUTPUT_OPTIMAL];
    out->no_double = arDouble[OUTPUT_NODOUBLE];
    out->double_take = arDouble[OUTPUT_TAKE];
    out->double_pass = arDouble[OUTPUT_DROP];
    out->decision = (int) cd;
    for (int j = 0; j < BG_NUM_PROBS; j++)
        out->probs[j] = aarOutput[0][j];

    switch (cd) {
    case DOUBLE_TAKE:
    case DOUBLE_PASS:
    case DOUBLE_BEAVER:
    case REDOUBLE_TAKE:
    case REDOUBLE_PASS:
        out->should_double = 1;
        break;
    default:
        out->should_double = 0;
        break;
    }
    out->optional = cd == OPTIONAL_DOUBLE_TAKE || cd == OPTIONAL_REDOUBLE_TAKE || cd == OPTIONAL_DOUBLE_BEAVER
        || cd == OPTIONAL_DOUBLE_PASS || cd == OPTIONAL_REDOUBLE_PASS;
    /* Taking is right when the opponent loses less by taking than by passing. */
    out->should_take = arDouble[OUTPUT_TAKE] <= arDouble[OUTPUT_DROP] ? 1 : 0;
    if (!out->cube_available)
        out->should_double = 0;
    return 0;
}

int
bg_cube_decision(const int board[BG_BOARD_INTS], const bg_cube_state *cs, int plies, bg_cube_eval *out)
{
    evalcontext ec = analysis_context(plies);
    return cube_decision(board, cs, &ec, out);
}

int
bg_cube_decision_level(const int board[BG_BOARD_INTS], const bg_cube_state *cs, int level, bg_cube_eval *out)
{
    evalcontext ec = aecSettings[clamp_level(level)];
    return cube_decision(board, cs, &ec, out);
}

/*
 * Luck of a roll: equity after the best play with this roll minus the
 * average over all rolls (ported from gnubg analysis.c LuckNormal/LuckFirst).
 */
static float
best_after_roll(const TanBoard anBoard, int d0, int d1, const cubeinfo *ci, const cubeinfo *ciOpp)
{
    TanBoard tmp;
    movelist ml;
    float ar[NUM_ROLLOUT_OUTPUTS];
    float r;

    memcpy(tmp, anBoard, sizeof(TanBoard));
    if (FindnSaveBestMoves(&ml, d0, d1, (ConstTanBoard) tmp, NULL, 0.0f, ci, &ec_luck, defaultFilters) < 0) {
        g_free(ml.amMoves);
        return NAN;
    }
    if (!ml.cMoves) {
        /* no legal move: evaluate the position with the opponent on roll */
        SwapSides(tmp);
        if (GeneralEvaluationE(ar, (ConstTanBoard) tmp, (cubeinfo *) ciOpp, &ec_luck) < 0)
            return NAN;
        r = ci->nMatchTo ? -mwc2eq(ar[OUTPUT_CUBEFUL_EQUITY], ciOpp) : -ar[OUTPUT_CUBEFUL_EQUITY];
    } else {
        r = ml.amMoves[0].rScore;
        g_free(ml.amMoves);
    }
    return r;
}

float
bg_luck(const int board[BG_BOARD_INTS], int d0, int d1, const bg_cube_state *cs, int first_roll)
{
    TanBoard an;
    cubeinfo ci, ciOpp;
    float aar[6][6];
    float mean = 0.0f;
    int i, j;

    to_tanboard(board, an);
    if (make_cubeinfo(cs, &ci) < 0)
        return NAN;
    memcpy(&ciOpp, &ci, sizeof(cubeinfo));
    ciOpp.fMove = !ci.fMove;

    int n0 = d0 - 1, n1 = d1 - 1;
    if (n0 < n1) {
        int t = n0;
        n0 = n1;
        n1 = t;
    }

    if (first_roll && n0 != n1) {
        /* Opening roll: 30 possible non-double rolls, either player may get it. */
        TanBoard swapped;
        memcpy(swapped, an, sizeof(TanBoard));
        SwapSides(swapped);
        for (i = 0; i < 6; i++)
            for (j = 0; j < i; j++) {
                aar[i][j] = best_after_roll((ConstTanBoard) an, i + 1, j + 1, &ci, &ciOpp);
                mean += aar[i][j];
            }
        for (i = 0; i < 6; i++)
            for (j = i + 1; j < 6; j++) {
                /* the opponent wins the opening roll and plays it */
                aar[i][j] = -best_after_roll((ConstTanBoard) swapped, i + 1, j + 1, &ciOpp, &ci);
                mean += aar[i][j];
            }
        return aar[n0][n1] - mean / 30.0f;
    }

    for (i = 0; i < 6; i++)
        for (j = 0; j <= i; j++) {
            aar[i][j] = best_after_roll((ConstTanBoard) an, i + 1, j + 1, &ci, &ciOpp);
            mean += (i == j) ? aar[i][j] : aar[i][j] * 2.0f;
        }
    return aar[n0][n1] - mean / 36.0f;
}

void
bg_position_id(const int board[BG_BOARD_INTS], char out[16])
{
    TanBoard an;
    to_tanboard(board, an);
    strncpy(out, PositionID((ConstTanBoard) an), 15);
    out[15] = 0;
}

void
bg_match_id(const bg_cube_state *cs, int d0, int d1, int doubled, int resigned, int game_over, char out[16])
{
    unsigned int dice[2] = { (unsigned int) d0, (unsigned int) d1 };
    int score[2] = { cs->score[0], cs->score[1] };
    gamestate gs = game_over ? GAME_OVER : GAME_PLAYING;
    /* fTurn = player to act, fMove = player on roll */
    int turn = doubled ? !cs->on_roll : cs->on_roll;
    strncpy(out, MatchID(dice, turn, resigned, doubled, cs->on_roll, cs->cube_owner, cs->crawford, cs->match_to,
                         score, cs->cube_value, cs->jacoby, gs), 15);
    out[15] = 0;
}

void
bg_pip_count(const int board[BG_BOARD_INTS], unsigned int out[2])
{
    TanBoard an;
    to_tanboard(board, an);
    PipCount((ConstTanBoard) an, out);
}

int
bg_apply_move(int board[BG_BOARD_INTS], const int move[BG_MOVE_INTS])
{
    TanBoard an;
    to_tanboard(board, an);
    if (ApplyMove(an, move, TRUE) < 0)
        return -1;
    from_tanboard((ConstTanBoard) an, board);
    return 0;
}
