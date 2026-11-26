package com.boldblot.backgammonplyground.core

/** How much work a reference position is for the engine. */
enum class RefDifficulty(val label: String) { EASY("Easy"), AVERAGE("Average"), HARD("Hard") }

/** A position the speed test times; [cube]: a cube decision (no dice), else a checker play. */
class ReferencePosition(val cube: Boolean, val difficulty: RefDifficulty, val xgid: String, val note: String) {
    val setup: PositionSetup by lazy { PositionIds.parseXgid(xgid) }
    val title: String get() = "${difficulty.label} ${if (cube) "cube decision" else "checker play"}"
}

/**
 * The speed test's reference positions, from real tournament matches: of a sample of positions timed on a PC (3-ply fast and
 * T-Roller 48), the 10th, 50th and 90th percentile by time. Checker play by its number of legal plays and how close the best
 * are; cube decisions by how close the decision is and how much contact is left. Unlimited games use them at a money score.
 */
object ReferencePositions {
    val all: List<ReferencePosition> = listOf(
        ReferencePosition(false, RefDifficulty.EASY, "XGID=-b--B-D-C----D-b-d-dc-B---:0:0:1:53:0:8:1:9:10", "6 legal plays"),
        ReferencePosition(false, RefDifficulty.AVERAGE, "XGID=dCCB-CB--A--A----b-bbcb---:0:0:1:42:14:13:1:15:10", "19 legal plays"),
        ReferencePosition(false, RefDifficulty.HARD, "XGID=-b-a--EaB--AbDAa-c-e----AA:0:0:1:44:13:2:0:15:10", "a double, 58 legal plays"),
        ReferencePosition(true, RefDifficulty.EASY, "XGID=-aCaCDCB------------bbbdc-:0:0:1:00:0:0:0:11:10", "a clear decision"),
        ReferencePosition(true, RefDifficulty.AVERAGE, "XGID=-bBBBBCA-----a---b-bCbbbb-:0:0:1:00:9:6:0:11:10", "close"),
        ReferencePosition(true, RefDifficulty.HARD, "XGID=-aaB-BB-B-B-cC---cbe----B-:0:0:1:00:2:3:0:11:10", "close, with contact on both sides"),
    )

    fun of(cube: Boolean): List<ReferencePosition> = all.filter { it.cube == cube }
}
