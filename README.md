# Backgammon PlyGround

An Android backgammon trainer with three engines: **PureTD** (with Bold Blot's 4-ply search,
cube-aware cube decisions and match play; see [what we added](engine/puretd/README.md#what-bold-blot-added-to-puretd)),
**GNU Backgammon** and **Open Sage**.
The name is written exactly so: "Backgammon PlyGround", capital P and G.
Play against the computer, get prompted when you make a mistake, see how big
the mistake was and what the best plays were, track your statistics, and
review every game move by move afterwards.

Errors and the Performance Rating (PR) follow the definitions published by
**eXtreme Gammon** (see [docs/PERFORMANCE_RATING.md](docs/PERFORMANCE_RATING.md)).

## Features

- **Play the computer** (PureTD, GNU Backgammon or Open Sage) at a chosen PR, from PR <2 (World class) to
  PR 15, or a custom mistake rate; money sessions (Jacoby, beavers and raccoons optional) or
  matches of 1–99 points (Crawford rule), with the doubling cube.
- **Practice prompts.** After each of your checker plays and cube decisions the
  game analyses it with the engine chosen in Settings (XG ply numbering: checker play 3-ply with pruning, searching the 5 best moves and any within 0.16, and cube
  decisions 4-ply by default, 1- to 4-ply selectable; the computer's decisions can be left
  unanalyzed so it moves sooner). If it is an XG *Error* (> 0.020) or
  *Blunder* (> 0.080), a panel shows the error, your play versus the best play as
  arrows on the board, and the ranked alternatives with win/gammon/backgammon
  chances. You don't have to correct anything; tap *Continue* and play on.
  The threshold is configurable (blunders only / errors / any non-best move / off).
  A cube mistake also puts a button-shaped sign in the middle of the board (yellow *Cube
  error*, red *Cube blunder*; tap it for the cube analysis), so it can't be missed while
  you play on.
- **Game and match summary** at the end of each game: result and score, your PR and the
  computer's with XG's rating, luck with GNU Backgammon's luck rating, and checker and
  cube errors / blunders for both sides, for the game or for the whole match / session
  so far (games won and, at the end of a match, the rating change).
- **Analyze Position**: set up any position by tapping the board (or import an XGID / GNU ID),
  choose who is on roll, the dice (ranked best moves with arrows) or none (cube decision,
  with the take / pass view when your opponent is on roll), money or match score, Crawford
  and the cube, choose the engine (PureTD, GNU Backgammon or Open Sage) and the depths, and
  analyze it: the engines often disagree, and seeing where is part of the training.
- **Autofinish**: the engine (3-ply, moves within ±0.04) plays your side to the end of the game
  (pause per move configurable); tap anywhere to stop. Not counted in statistics.
- **Hint** button for checker plays and cube decisions (hinted decisions are not
  counted in statistics).
- **Resign** a single game, a gammon or a backgammon on your turn. The computer
  accepts unless it has at least a 1% chance of winning more (a gammon when you
  offer a single game, a backgammon when you offer a gammon). The computer itself
  offers to resign when it has no chance of winning (0.0%) and the size of its loss
  is certain: a backgammon at 100% backgammon, a gammon at 100% gammon and 0%
  backgammon, a single game at 0% gammon. Wins that would not count (Jacoby rule
  with the cube in the middle, or more points than needed to win the match) are
  ignored in both directions.
- **Review** any finished game decision by decision: a list of all decisions with
  coloured severity dots, *Next mistake*, played vs best arrows (curved so they never
  hide each other), full candidate
  analysis and cube equities (No double / Double-take / Double-pass). *Analyze again*
  re-runs the game or the whole match at another engine and depth.
- **My blunders** (History tab): every blunder you make (> 0.080, hinted decisions
  excluded) is saved there with its position, played vs best arrows and the full
  analysis, sorted by biggest error or newest, until you remove it with *Remove from
  list*. Blunders stay when their match is deleted. A match can be left out of the
  statistics (game menu, or its menu in History). Settings → Data (or "Clear…" in
  History): clear game records but keep the statistics, clear statistics but keep the
  games, delete all matches, clear the blunder list.
- **Export to eXtreme Gammon / GNU Backgammon**: from Review, share or save a game
  or the whole match as a `.mat` file (the Jellyfish match format both programs
  import) for cross-evaluation. For a game played after the Crawford game, export the
  whole match: a `.mat` file cannot otherwise say that the Crawford game was played.
- **Statistics** (filterable by type and period):
  XG PR overall / checker / cube, error and blunder counts per decision type,
  NJ-style error rate, XG Elo equivalent, PR per match chart (total, checker or cube), FIBS-formula rating
  with history chart, match/game/gammon/backgammon results, money results,
  luck per game and per roll, and dice statistics (doubles, pips per roll,
  distribution of every roll for you and the computer).
- **Board**: tap a checker to move it by the left die (the other die if that one
  can't be played; the dice flash red if neither can), drag-and-drop for a specific
  destination, tap the dice to swap them or to finish the move, undo. Dice are always
  shown with the higher value first (on the board and in move lists). Used dice dim
  (half a die per move on doubles); the computer's last roll stays greyed on its side
  until you roll or double, and its last move is shown as arrows. With no legal move
  your roll stays visible for 0.5 s and the turn passes. Forced moves (only one way to
  play the roll) are played for you (Settings → Play, on by default). *You can't move*,
  *Computer can't move* and *Forced move* show as signs in the middle of the board. Pip counts on the bar. Grey board with black and white points
  and warm-yellow / red checkers by default (classic green theme available).
- **Portrait and landscape.** Landscape is full screen with a side rail
  (players, match info, controls), in the style of Backgammon Galaxy. The board keeps
  its size and place when the practice prompt or a hint opens (they replace the rail).
- Matches are saved continuously and resume after the app is closed.

## Install

A signed release APK is copied to `dist/BackgammonPlyGround-<version>-build<N>.apk`.
Copy it to the phone and open it (allow installing from that source), or:

```sh
adb install dist/BackgammonPlyGround-0.6-build26.apk
```

Requires Android 8.0 (API 26) or newer. Native code is built for `arm64-v8a`,
`armeabi-v7a` and `x86_64`, so it runs on current 64-bit-only phones.

## Build

Prerequisites: JDK 17+ (builds were tested with JDK 25), Android SDK with
platform 37, build-tools 37.0.0, NDK 29.0.14206865 and CMake 4.1.2
(`local.properties` must point `sdk.dir` at the SDK).

```sh
./gradlew :app:assembleDebug          # debug APK
./gradlew :app:assembleRelease        # release APK (see signing below)
./gradlew :app:bundleRelease          # Play App Bundle: app/build/outputs/bundle/release/app-release.aab
scripts/host-tests.sh                 # engine C tests + all JVM unit tests against the real engine
scripts/check-mat.py                  # import the .mat files written by the tests into gnubg and compare
./gradlew :app:connectedDebugAndroidTest   # database migration tests on a device/emulator
```

**Signing.** The release build reads `signing.properties` (not committed):

```
storeFile=keystore/release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

Keep `keystore/release.jks` and `signing.properties` safe: Android only installs
updates signed with the same key. Without `signing.properties` the release build
is signed with the debug key. On Google Play this key is the upload key: Play App
Signing re-signs the bundle with Google's app signing key.

**16 KB pages.** Google Play requires 64-bit native libraries aligned to 16 KB pages. The
only native library is the engines' JNI bridge (`libbgjni`, with the C++ runtime linked in
statically for Open Sage). After
changing native code, check with `llvm-readelf -lW` that every arm64-v8a and x86_64 library
has `LOAD` alignment `0x4000`.

**Engine data.** `app/src/main/assets/puretd` holds PureTD's two wide networks at full
(float32) precision: `prob5_wide.ptw` (cubeless win / gammon / backgammon chances) and
`cubeful_wide.ptw` (cubeful equity, used at the leaves of the cube search). They are exported
from PureTD's checkpoints with `engine/tools/export_puretd.py`. The bear-off database is built
on the phone at first start and cached; the match equity table is computed by the app
(`core/CubeMath.kt`). `assets/gnubg` holds GNU Backgammon's networks (`gnubg.wd`), one-sided
bear-off database and the Kazaross XG2 match equity table; the two-sided bear-off database is
left out to keep the app small. `assets/sage` holds Open Sage's production model (stage 11: 21
network files, some shared between game plans) and its one-sided bear-off database, unchanged
from its release.

## Project layout

| Path | Contents |
| --- | --- |
| `engine/puretd/` | PureTD's move generator and board encoding (unchanged, MIT) and our network evaluation and search (`ptd.c`); see its README |
| `engine/gnubg/`, `engine/compat/`, `engine/src/` | GNU Backgammon's evaluation code (unchanged, GPL v3), a small glib shim, and our C API over it (`bgengine.c`) |
| `engine/sage/` | Open Sage's engine library (unchanged, MPL 2.0, from bgsage commit 98a6d84) |
| `engine/tools/` | `export_puretd.py`: PureTD checkpoints to the app's `.ptw` network files |
| `app/src/main/cpp/` | JNI bridges for the three engines (one library, `libbgjni`) |
| `app/src/main/java/com/boldblot/backgammonplyground/core/` | Pure Kotlin: board, move generation, rules, match state machine, XG analysis and PR, statistics |
| `app/src/main/java/com/boldblot/backgammonplyground/engine/` | Kotlin side of the engines (one search over each engine's evaluation), engine threads (the game's, and the background analysis' with PureTD) |
| `app/src/main/java/com/boldblot/backgammonplyground/data/` | Room database, settings, statistics queries |
| `app/src/main/java/com/boldblot/backgammonplyground/ui/` | Jetpack Compose screens |
| `docs/` | Design notes |

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for details and
[CHANGELOG.md](CHANGELOG.md) for the change history.

## Licence

Backgammon PlyGround is free software under the GNU General Public License v3 or later (see
[LICENSE](LICENSE)). It includes:

- the PureTD engine by Alexander Strehl (MIT licence, `engine/puretd/LICENSE-PureTD`);
- GNU Backgammon's evaluation code, networks, bear-off database and match equity table, by
  the GNU Backgammon authors (GPL v3 or later, `engine/gnubg/COPYING`, `engine/gnubg/AUTHORS`);
- the Open Sage engine, networks and bear-off database by Mark Higgins (Mozilla Public
  License 2.0, `engine/sage/LICENSE-OpenSage`; source: github.com/markbgsage/bgsage);
- the Archivo typeface (SIL Open Font License,
`app/src/main/assets/licenses/archivo-OFL.txt`). eXtreme Gammon is a trademark of GameSite
2000 Ltd.; this project is not affiliated with it.
