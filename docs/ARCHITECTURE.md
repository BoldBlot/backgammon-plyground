# Architecture

```
Compose UI (ui/*)  ──>  GameViewModel  ──>  MatchController (core, pure Kotlin)
                                              │      │
                                              │      └── AnalysisEngine interface
                                              ▼             │
                          Repository (Room, data/*)      EngineService (engine/*): PureTdEngine,
                                                            │  GnubgEngine, SageEngine
                                                            │ JNI (libbgjni)
                                                            ▼
                                         engine/puretd, engine/gnubg, engine/sage
```

## Engine

- Three engines share one checker search (`SearchEngine.kt`) over each engine's own evaluation:
  PureTD, GNU Backgammon and Open Sage. Each has its own networks, bear-off database, cube
  handling and match equity table.
- `engine/puretd/bg_engine.c` and `bg_engine.h` are PureTD's move generator and board
  encoding, copied unchanged (MIT licence, version in `PURETD_VERSION`).
- `engine/puretd/ptd.c` is ours: the network evaluation (PureTD's wide networks at full
  precision, `assets/puretd/*.ptw`), the bear-off database, and the cube-aware money and match
  cube searches. The full list of what we added is in `engine/puretd/README.md`.
- Match play for PureTD uses our own match equity table and match cube formulas
  (`core/CubeMath.kt`), not copied from any other program; the match cube search is
  `engine/MatchCubeSearch.kt` over a tree from `ptd_cube_tree`.
- GNU Backgammon (`engine/gnubg`, our C API in `engine/src/bgengine.c`) and Open Sage
  (`engine/sage`) are unchanged; the JNI bridges are in `app/src/main/cpp`.
- Every engine call runs on one dedicated thread (`EngineService`), since the engines keep
  state between calls. GNU Backgammon and Open Sage are loaded the first time they are used.
- The same CMake project builds for Android (via the app's `externalNativeBuild`, with the C++
  runtime linked in statically) and for the host, where a host `libbgjni.so` lets the JVM unit
  tests use the real engines (`scripts/host-tests.sh`).

## Core (pure Kotlin, JVM-testable)

- `Board`: both sides in their own orientation (gnubg layout).
- `MoveGenerator` / `MoveOptions`: legal moves with the max-dice and larger-die
  rules; `MoveOptions` supports step-by-step entry (tap/drag) by matching prefixes
  of all legal step sequences. Cross-checked against gnubg's generator on
  >10,000 random positions in `EngineCrossCheckTest`.
- `GameState` / `MatchState`: immutable state machine (opening roll, cube actions,
  moves, scoring with Jacoby and Crawford). Serialized with kotlinx.serialization
  for resume and for the game records used by Review.
- `MatchController`: drives a turn, asks the engine for the computer's actions and
  analyses **every** decision (yours and the computer's) into `DecisionRecord`s.
- `Analysis.kt`: XG error categories and PR (see `PERFORMANCE_RATING.md`).
- `Stats.kt`: per-game summaries, dice totals, FIBS Elo.
- `Resign.kt`: resignation rules (when the computer accepts, when it offers, which
  wins count at the current score and cube). `MatchController` evaluates the
  position at the analysis depth (1-ply pre-check for the computer's own offers).
  A computer offer is stored in `GameState.resignOffer` so it survives a restart;
  rejected offers are remembered per game so they are not repeated.
- `MatExport.kt`: Jellyfish `.mat` export, laid out like gnubg's own export
  (computer = left column / gnubg player 0, human = right column).

## Data

Room tables: `matches` (one row per match / money session, with final
performance and rating before/after), `games` (summary JSON for statistics and the
full `GameState` JSON for review), `current_match` (the match in progress) and
`blunders` (the human's saved blunders: a `DecisionRecord` JSON per row, unique per
match/game/decision index, inserted as each blunder is made). `matches.inStats` marks matches
left out of statistics and rating (ratings are recomputed along the matches that count) and
`matches.recordsCleared` matches whose game records were deleted (summaries kept for
statistics).
Settings live in DataStore.

## UI

Jetpack Compose with Material 3. `BoardView` draws the board on a Canvas
(`BoardGeometry` maps between screen and point indices) and handles taps and
drags; it can draw move arrows, pip counts on the bar and the computer's last move.
`GameScreen` switches between a portrait layout and a full-screen landscape layout
with a side rail; analysis panels replace the rail in landscape so the board keeps
its size. Debug builds have an "Auto-play" menu item where the engine plays your
side (with random mistakes) for testing.

## Tests

`scripts/host-tests.sh` runs:

- `engine_test`: C tests of the engine API (opening moves at 0–2 ply, cube
  decisions, luck, match equity table loading, move legality);
- JVM unit tests: move generation rules, notation, scoring, Crawford/Jacoby, XG PR
  rules (including the manual's worked example), the Kotlin vs gnubg move
  generator cross-check, and full simulated matches through `MatchController`
  (engine-best play gives PR ≈ 0, random play PR > 30).
- Resignations: the thresholds and the "wins that count" rules (`ResignationTest`),
  the computer's offers and answers on fixed won/lost positions with the real engine,
  and simulated matches with resignations by both sides (`ResignExportSimulationTest`).

The simulated matches are also written as `.mat` files (every game and every match)
to `app/build/mat-export`. `scripts/check-mat.py` imports each of them into the GNU
Backgammon command-line program and compares gnubg's re-export with ours line by line
(moves, cube actions, drops, resignations and points).
