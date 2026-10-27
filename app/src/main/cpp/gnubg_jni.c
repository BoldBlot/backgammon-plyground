/*
 * JNI bridge between com.boldblot.backgammonplyground.engine.GnubgNative and the bgengine C API
 * (engine/src, over GNU Backgammon's evaluation code).
 *
 * Data is exchanged as primitive arrays to keep the bridge trivial:
 *   board : int[50]  (see bgengine.h)
 *   cube  : int[9]   {matchTo, score0, score1, crawford, jacoby, beavers, cubeValue, cubeOwner, onRoll}
 *   move  : int[8]
 */
#include <jni.h>
#include <stdlib.h>
#include <string.h>

#include "bgengine.h"

#define FN(name) Java_com_boldblot_backgammonplyground_engine_GnubgNative_##name

#define MAX_MOVES 3060          /* gnubg MAX_MOVES */

static int
read_board(JNIEnv *env, jintArray arr, int board[BG_BOARD_INTS])
{
    if (!arr || (*env)->GetArrayLength(env, arr) != BG_BOARD_INTS)
        return -1;
    (*env)->GetIntArrayRegion(env, arr, 0, BG_BOARD_INTS, board);
    return 0;
}

static int
read_cube(JNIEnv *env, jintArray arr, bg_cube_state *cs)
{
    jint v[9];
    if (!arr || (*env)->GetArrayLength(env, arr) != 9)
        return -1;
    (*env)->GetIntArrayRegion(env, arr, 0, 9, v);
    cs->match_to = v[0];
    cs->score[0] = v[1];
    cs->score[1] = v[2];
    cs->crawford = v[3];
    cs->jacoby = v[4];
    cs->beavers = v[5];
    cs->cube_value = v[6];
    cs->cube_owner = v[7];
    cs->on_roll = v[8];
    return 0;
}

JNIEXPORT jint JNICALL
FN(nativeInit)(JNIEnv *env, jclass clazz, jstring dataDir)
{
    const char *dir = (*env)->GetStringUTFChars(env, dataDir, NULL);
    int r = bg_init(dir, NULL);
    (*env)->ReleaseStringUTFChars(env, dataDir, dir);
    return r;
}

JNIEXPORT jintArray JNICALL
FN(nativeLegalMoves)(JNIEnv *env, jclass clazz, jintArray boardArr, jint d0, jint d1)
{
    int board[BG_BOARD_INTS];
    if (read_board(env, boardArr, board))
        return NULL;
    int (*moves)[BG_MOVE_INTS] = malloc(sizeof(int) * BG_MOVE_INTS * MAX_MOVES);
    int n = bg_legal_moves(board, d0, d1, moves, MAX_MOVES);
    if (n > MAX_MOVES)
        n = MAX_MOVES;
    jintArray out = (*env)->NewIntArray(env, n * BG_MOVE_INTS);
    if (out)
        (*env)->SetIntArrayRegion(env, out, 0, n * BG_MOVE_INTS, (const jint *) moves);
    free(moves);
    return out;
}

/* boards: int[50 * n], each after the mover's play, from the mover's point of view.
 * Returns float[6 * n]: {equity, probs[5]} per position, or null on error. */
JNIEXPORT jfloatArray JNICALL
FN(nativeScoreBoards)(JNIEnv *env, jclass clazz, jintArray boardsArr, jintArray cubeArr, jint plies)
{
    bg_cube_state cs;
    if (!boardsArr || read_cube(env, cubeArr, &cs))
        return NULL;
    int len = (*env)->GetArrayLength(env, boardsArr);
    if (len % BG_BOARD_INTS)
        return NULL;
    int n = len / BG_BOARD_INTS;
    int (*boards)[BG_BOARD_INTS] = malloc(sizeof(int) * (size_t) len + 1);
    float *buf = malloc(sizeof(float) * 6 * (size_t) n + 1);
    (*env)->GetIntArrayRegion(env, boardsArr, 0, len, (jint *) boards);
    jfloatArray out = NULL;
    if (bg_score_boards((const int (*)[BG_BOARD_INTS]) boards, n, &cs, plies, buf) == 0) {
        out = (*env)->NewFloatArray(env, 6 * n);
        if (out)
            (*env)->SetFloatArrayRegion(env, out, 0, 6 * n, buf);
    }
    free(buf);
    free(boards);
    return out;
}

/*
 * Cube decision for the player on roll at gnubg depth `plies`.
 * Returns [noDouble, doubleTake, doublePass, optimal, decision, shouldDouble,
 *          shouldTake, optional, cubeAvailable, probs[5]]
 */
JNIEXPORT jfloatArray JNICALL
FN(nativeCubeDecision)(JNIEnv *env, jclass clazz, jintArray boardArr, jintArray cubeArr, jint plies)
{
    int board[BG_BOARD_INTS];
    bg_cube_state cs;
    bg_cube_eval ce;
    if (read_board(env, boardArr, board) || read_cube(env, cubeArr, &cs))
        return NULL;
    if (bg_cube_decision(board, &cs, plies, &ce) < 0)
        return NULL;
    float buf[14] = {
        ce.no_double, ce.double_take, ce.double_pass, ce.optimal, (float) ce.decision,
        (float) ce.should_double, (float) ce.should_take, (float) ce.optional, (float) ce.cube_available,
        ce.probs[0], ce.probs[1], ce.probs[2], ce.probs[3], ce.probs[4]
    };
    jfloatArray out = (*env)->NewFloatArray(env, 14);
    if (out)
        (*env)->SetFloatArrayRegion(env, out, 0, 14, buf);
    return out;
}

JNIEXPORT jfloat JNICALL
FN(nativeLuck)(JNIEnv *env, jclass clazz, jintArray boardArr, jint d0, jint d1, jintArray cubeArr, jboolean firstRoll)
{
    int board[BG_BOARD_INTS];
    bg_cube_state cs;
    if (read_board(env, boardArr, board) || read_cube(env, cubeArr, &cs))
        return 0.0f;
    return bg_luck(board, d0, d1, &cs, firstRoll ? 1 : 0);
}
