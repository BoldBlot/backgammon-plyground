/*
 * Host tests for the bgengine C API. Usage: engine_test <data_dir>
 */
#include "bgengine.h"

#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

static int failures = 0;

#define CHECK(cond, ...) do { \
    if (!(cond)) { failures++; printf("FAIL %s:%d: ", __FILE__, __LINE__); printf(__VA_ARGS__); printf("\n"); } \
} while (0)

static void
initial_board(int b[BG_BOARD_INTS])
{
    memset(b, 0, sizeof(int) * BG_BOARD_INTS);
    for (int side = 0; side < 2; side++) {
        int *h = b + 25 * side;
        h[23] = 2;   /* 24-point */
        h[12] = 5;   /* 13-point */
        h[7] = 3;    /* 8-point */
        h[5] = 5;    /* 6-point */
    }
}

static void
money_state(bg_cube_state *cs, int on_roll)
{
    memset(cs, 0, sizeof(*cs));
    cs->cube_value = 1;
    cs->cube_owner = -1;
    cs->on_roll = on_roll;
}

static void
format_move(const int m[BG_MOVE_INTS], char *out)
{
    out[0] = 0;
    for (int i = 0; i < 4 && m[2 * i] >= 0; i++) {
        char part[16];
        int from = m[2 * i] + 1, to = m[2 * i + 1] + 1;
        if (from == 25)
            snprintf(part, sizeof part, "%sbar/", i ? " " : "");
        else
            snprintf(part, sizeof part, "%s%d/", i ? " " : "", from);
        strcat(out, part);
        if (to <= 0)
            strcat(out, "off");
        else {
            snprintf(part, sizeof part, "%d", to);
            strcat(out, part);
        }
    }
}

static int
same_move_set(const int a[BG_MOVE_INTS], const int b[BG_MOVE_INTS])
{
    /* compare as multisets of (from,to) pairs */
    int used[4] = { 0 };
    for (int i = 0; i < 4; i++) {
        if (a[2 * i] < 0)
            continue;
        int found = 0;
        for (int j = 0; j < 4; j++)
            if (!used[j] && b[2 * j] == a[2 * i] && b[2 * j + 1] == a[2 * i + 1]) {
                used[j] = 1;
                found = 1;
                break;
            }
        if (!found)
            return 0;
    }
    for (int j = 0; j < 4; j++)
        if (b[2 * j] >= 0 && !used[j])
            return 0;
    return 1;
}

static double
now_ms(void)
{
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return ts.tv_sec * 1000.0 + ts.tv_nsec / 1e6;
}

int
main(int argc, char **argv)
{
    const char *dir = argc > 1 ? argv[1] : ".";
    double t0 = now_ms();
    CHECK(bg_init(dir, NULL) == 0, "bg_init failed");
    printf("init: %.0f ms\n", now_ms() - t0);

    int board[BG_BOARD_INTS];
    bg_cube_state cs;
    initial_board(board);
    money_state(&cs, 0);

    char id[16];
    bg_position_id(board, id);
    CHECK(strcmp(id, "4HPwATDgc/ABMA") == 0, "initial position id = %s", id);

    unsigned int pips[2];
    bg_pip_count(board, pips);
    CHECK(pips[0] == 167 && pips[1] == 167, "pip counts %u %u", pips[0], pips[1]);

    int moves[64][BG_MOVE_INTS];
    int n = bg_legal_moves(board, 3, 1, moves, 64);
    printf("legal moves for opening 31: %d\n", n);
    CHECK(n > 10, "too few legal moves: %d", n);

    /* Opening 3-1: 8/5 6/5 at every depth */
    static const int best31[8] = { 7, 4, 5, 4, -1, -1, -1, -1 };
    bg_move_eval evals[64];
    for (int plies = 0; plies <= 2; plies++) {
        int played_idx;
        t0 = now_ms();
        int k = bg_rank_moves(board, 3, 1, &cs, plies, moves[0], &played_idx, evals, 64);
        double dt = now_ms() - t0;
        char s[64];
        format_move(evals[0].move, s);
        printf("31 at %d-ply (%.0f ms): best %s eq %+.3f (plies %d), %d moves, played idx %d\n", plies, dt, s,
               evals[0].equity, evals[0].plies, k, played_idx);
        CHECK(k == n, "rank returned %d moves, expected %d", k, n);
        CHECK(same_move_set(evals[0].move, best31), "best 31 at %d ply is %s", plies, s);
        CHECK(played_idx >= 0, "played move not found");
        /* moves evaluated at the full depth come first, sorted best first */
        for (int i = 1; i < k && evals[i].plies == evals[0].plies; i++)
            CHECK(evals[i].equity <= evals[i - 1].equity + 1e-6f, "moves not sorted at %d", i);
        for (int i = 0; i < k; i++)
            for (int j = 0; j < BG_MOVE_INTS; j++)
                CHECK(evals[i].move[j] >= -1 && evals[i].move[j] <= 24, "bad move entry");
    }

    /* Opening 6-5: 24/13 */
    {
        int k = bg_rank_moves(board, 6, 5, &cs, 2, NULL, NULL, evals, 64);
        char s[64];
        format_move(evals[0].move, s);
        printf("65 at 2-ply: best %s eq %+.3f\n", s, evals[0].equity);
        int b2[BG_BOARD_INTS];
        memcpy(b2, board, sizeof b2);
        CHECK(k > 0 && bg_apply_move(b2, evals[0].move) == 0, "apply failed");
        CHECK(b2[25 + 23] == 1 && b2[25 + 12] == 6, "65 best did not run a back checker to the 13: %s", s);
    }

    /* Computer move at every level must be legal */
    for (int level = BG_LEVEL_BEGINNER; level <= BG_LEVEL_WORLDCLASS; level++) {
        int m[BG_MOVE_INTS];
        t0 = now_ms();
        int r = bg_choose_move(board, 6, 4, &cs, level, m);
        double dt = now_ms() - t0;
        int b2[BG_BOARD_INTS];
        memcpy(b2, board, sizeof b2);
        char s[64];
        format_move(m, s);
        printf("level %d plays 64: %s (%.0f ms)\n", level, s, dt);
        CHECK(r == 0 && bg_apply_move(b2, m) == 0, "level %d produced illegal move", level);
    }

    /* Cube: no double at the start, the cube is available */
    {
        bg_cube_eval ce;
        CHECK(bg_cube_decision(board, &cs, 2, &ce) == 0, "cube decision failed");
        printf("initial cube: nd %+.3f dt %+.3f dp %+.3f decision %d\n", ce.no_double, ce.double_take,
               ce.double_pass, ce.decision);
        CHECK(ce.cube_available && !ce.should_double, "should not double at the start");
        CHECK(fabsf(ce.double_pass - 1.0f) < 1e-4f, "money double/pass should be 1.0, got %f", ce.double_pass);
    }

    /* A crushing position: player on roll has 14 checkers off... use a simple race lead */
    {
        int b[BG_BOARD_INTS] = { 0 };
        /* opponent: 15 checkers on its 24..20 points (far behind) */
        b[23] = 3; b[22] = 3; b[21] = 3; b[20] = 3; b[19] = 3;
        /* player on roll: 15 checkers in home board, low */
        b[25 + 0] = 3; b[25 + 1] = 3; b[25 + 2] = 3; b[25 + 3] = 3; b[25 + 4] = 3;
        bg_cube_eval ce;
        CHECK(bg_cube_decision(b, &cs, 0, &ce) == 0, "cube decision failed");
        printf("race lead cube: nd %+.3f dt %+.3f dp %+.3f decision %d double %d take %d\n", ce.no_double,
               ce.double_take, ce.double_pass, ce.decision, ce.should_double, ce.should_take);
        CHECK(!ce.should_take, "opponent should pass a huge race deficit");
    }

    /* Luck: weighted average over all 36 rolls is zero */
    {
        double sum = 0;
        for (int d0 = 1; d0 <= 6; d0++)
            for (int d1 = 1; d1 <= 6; d1++)
                sum += bg_luck(board, d0, d1, &cs, 0);
        printf("mean luck over 36 rolls: %.6f\n", sum / 36);
        CHECK(fabs(sum / 36) < 1e-4, "luck does not average to zero: %f", sum / 36);
        float l66 = bg_luck(board, 6, 6, &cs, 0), l21 = bg_luck(board, 2, 1, &cs, 0);
        printf("luck 66 %+.3f, 21 %+.3f\n", l66, l21);
        CHECK(l66 > l21, "66 should be luckier than 21");
        /* Opening roll: luck is relative to the average of all 30 possible
         * opening rolls (15 for each player), so 6-5 is lucky and 2-1 is not. */
        float o65 = bg_luck(board, 6, 5, &cs, 1), o21 = bg_luck(board, 2, 1, &cs, 1);
        printf("opening luck 65 %+.3f, 21 %+.3f\n", o65, o21);
        CHECK(o65 > 0 && o65 > o21, "opening 65 should be lucky");
    }

    /* Match play: 5-point match at 0-0, MET is loaded */
    {
        bg_cube_state ms;
        money_state(&ms, 0);
        ms.match_to = 5;
        bg_cube_eval ce;
        CHECK(bg_cube_decision(board, &ms, 0, &ce) == 0, "match cube decision failed");
        printf("5pt match cube: nd %+.3f dt %+.3f dp %+.3f\n", ce.no_double, ce.double_take, ce.double_pass);
        CHECK(ce.double_pass > 0.5f && ce.double_pass <= 1.0f, "match double/pass equity out of range: %f",
              ce.double_pass);
        char mid[16];
        bg_match_id(&ms, 3, 1, 0, 0, 0, mid);
        printf("match id: %s\n", mid);
        int k = bg_rank_moves(board, 3, 1, &ms, 1, NULL, NULL, evals, 64);
        CHECK(k > 0 && same_move_set(evals[0].move, best31), "match 31 best move wrong");
    }

    printf(failures ? "\n%d FAILURE(S)\n" : "\nALL TESTS PASSED\n", failures);
    return failures ? 1 : 0;
}
