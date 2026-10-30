/*
 * JNI bridge between com.boldblot.backgammonplyground.engine.SageNative and the Open Sage engine
 * (engine/sage, MPL 2.0, by Mark Higgins). Mirrors Open Sage's own mobile interface
 * (bgsage cpp/src/mobile.cpp) without its depth and time limits.
 *
 * Positions are int[26] in Open Sage's layout, from the point of view of the player it concerns:
 *   [1..24] that player's points (positive = its checkers, negative = the opponent's),
 *   [25] its bar, [0] the opponent's bar.
 * cube: int[7] {value, owner (0 centred, 1 the player, 2 the opponent), away1, away2, crawford,
 *   jacoby, beaver}, for the player on roll (away 0/0 = money).
 */
#include <jni.h>

#include <algorithm>
#include <memory>
#include <string>
#include <vector>

#include "bgbot/bearoff.h"
#include "bgbot/board.h"
#include "bgbot/cube.h"
#include "bgbot/multipy.h"
#include "bgbot/neural_net.h"
#include "bgbot/pubeval.h"

#define FN(name) Java_com_boldblot_backgammonplyground_engine_SageNative_##name

using namespace bgbot;

namespace {

struct Engine {
    BearoffDB db;
    std::shared_ptr<Strategy> base;
    std::shared_ptr<Strategy> strategy;
    int threads = 1;
};

/* Open Sage's own filter for the moves inside a multi-ply search (as in its mobile interface). */
const MoveFilter INNER{5, 0.08f};

std::array<float, 5> invert(const std::array<float, 5>& p) { return {1.0f - p[0], p[3], p[4], p[1], p[2]}; }

bool read_board(JNIEnv* env, jintArray arr, jsize at, Board& b) {
    if (!arr || env->GetArrayLength(arr) < at + 26) return false;
    env->GetIntArrayRegion(arr, at, 26, b.data());
    return true;
}

bool read_cube(JNIEnv* env, jintArray arr, CubeInfo& ci) {
    jint v[7];
    if (!arr || env->GetArrayLength(arr) != 7) return false;
    env->GetIntArrayRegion(arr, 0, 7, v);
    ci.cube_value = v[0];
    ci.owner = v[1] == 1 ? CubeOwner::PLAYER : v[1] == 2 ? CubeOwner::OPPONENT : CubeOwner::CENTERED;
    ci.match = {v[2], v[3], v[4] != 0};
    ci.jacoby = ci.match.is_money() && v[5] != 0;
    ci.beaver = ci.match.is_money() && v[6] != 0;
    return true;
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
FN(nativeOpen)(JNIEnv* env, jclass, jobjectArray pathsArr, jintArray hiddenArr, jstring bearoff, jint threads) {
    try {
        const jsize n = env->GetArrayLength(pathsArr);
        if (n < 5 || env->GetArrayLength(hiddenArr) != n) return 0;
        std::vector<std::string> paths;
        std::vector<int> hidden(n);
        env->GetIntArrayRegion(hiddenArr, 0, n, hidden.data());
        for (jsize i = 0; i < n; ++i) {
            auto s = static_cast<jstring>(env->GetObjectArrayElement(pathsArr, i));
            const char* c = env->GetStringUTFChars(s, nullptr);
            paths.emplace_back(c);
            env->ReleaseStringUTFChars(s, c);
            env->DeleteLocalRef(s);
        }
        const char* bo = env->GetStringUTFChars(bearoff, nullptr);
        std::string bearoffPath(bo);
        env->ReleaseStringUTFChars(bearoff, bo);

        auto e = std::make_unique<Engine>();
        e->threads = std::max(1, static_cast<int>(threads));
        // Open Sage's production model (stage 11): the backgame-aware phased pair layout.
        e->base = std::make_shared<BackgameAwarePairStrategy>(paths, hidden, true);
        if (!e->db.load(bearoffPath)) return 0;
        e->strategy = std::make_shared<BearoffStrategy>(e->base, &e->db);
        return reinterpret_cast<jlong>(e.release());
    } catch (...) {
        return 0;
    }
}

extern "C" JNIEXPORT void JNICALL
FN(nativeClose)(JNIEnv*, jclass, jlong handle) {
    delete reinterpret_cast<Engine*>(handle);
}

/* Worker threads for the next searches (call between searches, on the thread that searches). */
extern "C" JNIEXPORT void JNICALL
FN(nativeSetThreads)(JNIEnv*, jclass, jlong handle, jint threads) {
    auto* e = reinterpret_cast<Engine*>(handle);
    if (e) e->threads = std::max(1, static_cast<int>(threads));
}

/*
 * Positions right after the mover's play ([posts], int[26] each from the mover's point of view;
 * [pre] the position before it) at XG depth [plies]. Returns float[6 * n]: the mover's cubeful
 * equity (normalized) and its 5 cubeless probabilities, or null on error.
 */
extern "C" JNIEXPORT jfloatArray JNICALL
FN(nativeScore)(JNIEnv* env, jclass, jlong handle, jintArray preArr, jintArray postsArr, jintArray cubeArr, jint plies) {
    auto* e = reinterpret_cast<Engine*>(handle);
    Board pre{};
    CubeInfo ci;
    if (!e || !read_board(env, preArr, 0, pre) || !read_cube(env, cubeArr, ci) || !postsArr) return nullptr;
    const jsize n = env->GetArrayLength(postsArr) / 26;
    try {
        std::vector<float> out(6 * static_cast<size_t>(n));
        for (jsize k = 0; k < n; ++k) {
            Board b{};
            read_board(env, postsArr, 26 * k, b);
            std::array<float, 5> p{};
            float eq;
            if (plies <= 1) {
                p = e->strategy->evaluate_probs(b, pre);
                auto [pp, op] = pip_counts(b);
                eq = cl2cf(p, ci, cube_efficiency(p, is_race(b), pp, op));
            } else {
                // The opponent is on roll: evaluate from its side and turn the result around.
                auto v = cubeful_probs_and_equity_nply(flip(b), flip_cube_perspective(ci), *e->strategy, plies, INNER,
                                                       e->threads, nullptr, &pre);
                p = invert(v.probs);
                eq = -v.equity;
            }
            out[6 * k] = eq;
            std::copy(p.begin(), p.end(), out.begin() + 6 * k + 1);
        }
        jfloatArray r = env->NewFloatArray(static_cast<jsize>(out.size()));
        if (r) env->SetFloatArrayRegion(r, 0, static_cast<jsize>(out.size()), out.data());
        return r;
    } catch (...) {
        return nullptr;
    }
}

/*
 * Cube decision for the player on roll at XG depth [plies]. Returns float[13]: {no double,
 * double/take, double/pass, optimal, should double, should take, beaver, cube available,
 * 5 cubeless probabilities} (equities normalized), or null on error.
 */
extern "C" JNIEXPORT jfloatArray JNICALL
FN(nativeCube)(JNIEnv* env, jclass, jlong handle, jintArray boardArr, jintArray cubeArr, jint plies) {
    auto* e = reinterpret_cast<Engine*>(handle);
    Board board{};
    CubeInfo ci;
    if (!e || !read_board(env, boardArr, 0, board) || !read_cube(env, cubeArr, ci)) return nullptr;
    try {
        std::array<float, 5> p{};
        CubeDecision cd{};
        if (plies <= 1) {
            p = invert_probs(e->strategy->evaluate_probs(flip(board), is_race(board)));
            auto [pp, op] = pip_counts(board);
            cd = cube_decision_1ply(p, ci, cube_efficiency(p, is_race(board), pp, op));
        } else {
            PubEval prefilter;
            cd = cube_decision_nply(board, ci, *e->strategy, plies, INNER, e->threads, &prefilter);
            MultiPlyStrategy multipy(e->strategy, plies, INNER, false, false, e->threads);
            multipy.set_bearoff_db(&e->db);
            const Board flipped = flip(board);
            p = invert_probs(multipy.evaluate_probs(flipped, flipped));
            multipy.clear_cache();
        }
        float out[13] = {
            cd.equity_nd, cd.equity_dt, cd.equity_dp, cd.optimal_equity, cd.should_double ? 1.0f : 0.0f,
            cd.should_take ? 1.0f : 0.0f, cd.is_beaver ? 1.0f : 0.0f, can_double(ci) ? 1.0f : 0.0f,
            p[0], p[1], p[2], p[3], p[4],
        };
        jfloatArray r = env->NewFloatArray(13);
        if (r) env->SetFloatArrayRegion(r, 0, 13, out);
        return r;
    } catch (...) {
        return nullptr;
    }
}
