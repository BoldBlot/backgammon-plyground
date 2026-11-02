# Changelog

## Known issues

- Long evaluations in endgames: I'm hunting down the bug behind them. I think 0.7.10 fixes it
  (games lost or won whatever is played now stop at 1-ply), but it is still being tested.

## 0.7.12 — 2026-11-02 (build 57, internal test)

- Share everywhere a position is shown, with the same three choices: the XGID, the board picture,
  and the board with the top 5 plays (or the cube actions) with the engine and depth under them.
  New in My positions (a share icon next to ⋮, replacing the file share) and in games (next to
  ⋮). In a game the top 5 comes from an open hint; without one, PlyGround says to open Hint.
- Analyze Position: the board with the top 5 is always offered; without an analysis it analyzes
  the position first. Import moved to ⋮ → Import XGID…, so the title fits on one line.
- My positions: ⋮ → Import XGID… saves a position from an XGID or a GNU Backgammon ID, and it is
  analyzed after saving.
- Analyses name their engine everywhere, also in positions saved before 0.7.11 (taken from the
  position's analysis label). Where an older analysis did not record it: "Engine unknown". Cube
  analyses start with the engine and depth ("PureTD 4-ply · cubeless chances").
- Game: a hint no longer stops play. The dice stay, you can move and finish the turn (or roll,
  double, take or pass) with the hint open, and finishing closes it. Choosing a play in the hint
  shows its arrows on the board; a partly entered move is taken back first, and moving a checker
  clears the arrows.

## 0.7.11 — 2026-11-02 (build 56, internal test)

- Review: the computer's moves played without its analysis no longer show "No legal move." They
  get a quick 1-ply analysis while you play (kept out of the computer's PR), and in older matches
  they say "Not analyzed". Analyze again includes the computer's decisions by default when some
  were never analyzed.
- Review: a "Cube" chip on any checker play shows that turn's cube decision, no doubles included
  (analyzed on the spot when it wasn't recorded).
- Review: the played/best bar stays for cube decisions and positions not analyzed ("[n/a]"), so
  the arrows below no longer jump, and the arrows go on into the previous or next game.
- Review: errors in My positions have a folder mark; with Top X per match, an outlined one marks
  the errors that go in when the match ends.
- Cube analysis: the chances show for both players, each behind a checker in its colour (win,
  gammon, backgammon), with a chances bar; the engine and depth are named under it. Checker plays
  name the engine too ("PureTD 3-ply, pruned"), and so do shared pictures with the top plays.
- The chances bar keeps wins, gammons and backgammons apart with dark (and very light) checkers.
- The checkers beside plays and chances are drawn in your board's checker style.
- Analyze Position: Share, as in the review: the XGID, the board, or the board with the top 5.
- Game: the Max Bearoff chip (Autofinish is in the ⋮ menu): for the rest of the game, once
  contact is broken and all your checkers are home, it bears off as many checkers as possible
  each turn (1-ply picks between plays that take off the same number). These plays count in PR.
- Landscape: Undo is on the board, in the half without the dice, and Done appears next to your
  dice when the move is complete (tapping the dice ends the turn too). Settings → Board brings
  them back to the side panel.
- New errors: a minimum-error slider, from every error up to 0.500 (enormous blunders only),
  replaces "Blunders only / Errors and blunders" (it starts where that was set). Four methods:
  Manual, Fixed threshold, Adaptive (experimental: errors above a share of My positions' average
  error, 90% by default, and at least the minimum; the average shows under the slider) and Top X
  per match (checker and cube together; a money session at its end).
- New errors with the automatic methods: the button stays and holds the errors left out, the
  newest 50 by default, so any of them can still be added by hand. The end-of-game summary says
  how many errors of the match went to My positions.
- Quiz: arrows back through the cards answered this round (view only), a folder button to move
  a card to other folders, and a tap on the dice (or where they would be) goes to the next card.
- Quiz: when it ends, its positions are listed below the results, as in a folder.
- Quiz settings for cube decisions: your own as No double / Double, No double / Double / Too good,
  or the full decision; when you're doubled, take or pass, take or pass then "was the double
  right?" (both must be right), or the board turned for the doubler's full decision.
- Quiz filter: balance chips from below −3 to above +3.

## 0.7.10 — 2026-11-01 (build 55)

- Checker analysis: a game that can no longer be won (or lost), with no gammon at stake between
  the plays, stops at 1-ply: every play is worth the same, and it isn't counted as a decision. A
  pruned search takes at most 16 plays deeper (the best at 1-ply, and always the play made), so big
  doubles in an endgame no longer keep "Analyzing your move…" or the auto bear-off running for
  minutes.
- Automatic labels: positions where both players need one point (a 1-point match too) get the new
  DMP (double match point) label instead of Post-Crawford, positions already saved too.
- My positions: a play tapped in a saved position's analysis draws its arrows in the colour of its
  error level, as in the review.
- The text in the coloured error pills is centred (it sat off-centre in landscape).
- After passing the computer's double, the computer no longer works out a reply in the finished
  game (the next game logged an error at each of its moves).
- The background analysis has its own match equity table, so it can't disturb the game's.

## 0.7.9 — 2026-11-01 (build 54)

- Quick cube check (Settings → Training, with analysis during play and 3- or 4-ply cube decisions; off
  by default): a 1-ply or 2-ply check stands in for the deep one when it finds doubling a blunder, or
  the game hopeless either way, so endgames no longer wait on every roll. Doubling always gets the
  full analysis; Analyze again in the review checks any position deeper.
- Quiz result: the analysis behind the verdict is named ("Analysis: PureTD 3-ply, pruned"), and
  Analyze again redoes it with another engine or depth. The new analysis is kept on the position and
  the result follows it; if right and missed swap, the position's counts and the score do too.
- Quiz: the position's labels show under the board after you answer (Settings → My positions → Quiz
  can turn them off). The Crawford sign stands on the bar in Crawford games.
- Undo after Delete (quiz and New errors) disappears after 5 seconds.
- The match summary can delete the match (at the end of a match and in the Match Library): it leaves
  history, statistics and the Match Library, and with "Also delete its errors" (ticked) its errors
  leave New errors and the positions they made leave My positions.
- Intuition training in landscape: a long error badge wraps instead of squeezing Continue; the Try
  again buttons wrap too.
- The checkers beside the winning chances are always drawn as the board's Rimmed checkers.
- The welcome screen shows only on a new install (or after clearing the app's data), no longer after
  every update. A finished speed test is remembered: the welcome screen, Settings → Analysis and
  About say when it ran and what it recommended.

## 0.7.8 — 2026-11-01 (build 53)

- Errors in the lists are marked by tinted pills around the numbers, no more coloured dots: the
  plays and cube actions of an analysis, the review's move list (the selected decision's pill names
  it, e.g. "Error -0.023") and New errors. The played row is tinted in its error's colour and
  outlined. The error badge has the same look.
- Small checkers in the players' colours replace the dots before the winning chances and in the
  review's move list, so the red player no longer looks like a blunder.
- "Try again" in the review: a decision tried again shows the first try, the one PR counts, as the
  played move (row, PLAYED, arrows, After). The plays tried and taken back are marked "tried", the
  play made at last "final".
- "My answer" in My positions is a tag beside the play instead of a ✓.

## 0.7.7 — 2026-11-01 (build 52)

- Quiz result: tap a play in the analysis to see it on the board, with the Arrows / Board / After
  chips of the review, and share the position (XGID, board picture, board with the top 5).
- Quiz result: Delete takes the position out of My positions and all its folders, with Undo. The
  summary says how many were deleted during the quiz, and how many of them as learned.
- Cube decisions in the quiz are answered as on FlashBack cards: No double, Too good to double,
  Double, take or Double, pass. A wrong guess of the opponent's answer costs what that answer loses.
- Settings → Display → Errors in: Eq (default) or mEq, for every error in the app. The quiz summary
  gives the average error that way ("0.0124 Eq" or "12.4 mEq").
- Errors in 1-point matches show the plain equity lost everywhere, as the list of plays does, and
  the badge notes the weighting: "Error -0.026 (×1.5 in PR)". Severities and PR are unchanged.
- Filters in My positions and the quiz menu show how many positions each option would leave, e.g.
  "Race (12)", and the button how many pass: "Filter: 12 of 40".
- Quiz from a folder with a filter on: the quiz menu opens with that filter too.
- Quiz in landscape: the board takes the full height, centred, with the title beside it.
- New errors: the empty list points to Settings → My positions → New errors.

## 0.7.6 — 2026-10-31 (build 51)

- Quiz: every position counts its right and missed answers, and their balance (right minus missed).
  My positions and the position show them; a position answered before starts with its last answer.
- Filter menus of My positions and the quiz: Quiz answers (Not tried yet, Answered right, Missed)
  and Quiz balance (below 0, 0, above 0).
- Settings → My positions → Quiz: "Delete learned positions" (off by default) deletes a position
  once its balance reaches +2, +3, +4, +5 or +10 after a right answer.
- Delete all positions: Settings → My positions, and Data and help. Folders, autofilters and labels
  stay.
- The computer resigns a backgammon again when one is certain; since 0.7.3 it could offer only a
  gammon there.

## 0.7.5 — 2026-10-31 (build 50)

- New errors settings moved to Settings → My positions, above My autofilters, and say plainly that
  Manual has a New errors button and Automatic doesn't.
- Review in landscape: a thin scroll bar under the game and filter chips shows that the row scrolls.

## 0.7.4 — 2026-10-31 (build 49)

Choose how your mistakes reach My positions, make your own autofilters, enter the dice yourself, and
Intuition training waits less.

- Settings → Training → New errors: Manual (go through them with the New errors button, as before)
  or Automatic (every new error is filed at once, no button). Sort them into no folder or into the
  Checker and Cube folders, made when needed. Add to library asks the same.
- Checker blunders and Cube blunders are now called Checker and Cube (only if you hadn't renamed
  them). If one of them collected new errors, filing is set to Automatic.
- My autofilters (Settings → My positions): a folder that takes every new error matching all its
  conditions, an error level and any labels. Labels that can't both be true (Checker or Cube,
  Contact or Race…) count as either.
- Manual dice (Settings → Play): enter every roll, yours, the computer's and the opening roll, from
  two columns of dice. Such matches are left out of the statistics unless you count them.
- Intuition training: the engine starts on your decision while you think (your roll as soon as the
  dice land, your cube decision as soon as the computer has chosen its move) and works out the
  computer's reply while you read the feedback. Its own analysis depth, if you want it quicker.
- Autofinish strength: 1-ply (now the default, quickest), 2-ply or 3-ply, without noise; the pause
  after each move is 0.3 s by default.
- The side panel setting moved next to Bear off direction (Settings → Display → Board).
- A shared board picture has the match, score and roll above it, like the one with the top five.
- Arrows, Board, After and Save are chips; in landscape they sit beside the board, which keeps its
  size from one decision to the next. After is greyed out for decisions without a move.
- Back from a review to the game's summary works like the back arrow; if the summary ever
  disappears, a Show game summary button brings it back.

## 0.7.3 — 2026-10-31 (build 48)

New errors: the mistakes from your games wait in My positions until you file them or delete them.
Share a position as an XGID or a picture.

- New errors (once My blunders) moved from the Match Library to My positions, next to Quiz. Go
  through them with Delete and Keep, or long-press to delete several; Add to library asks whether to
  split them into Checker blunders and Cube blunders or put them all in All blunders, and takes them
  off the list. Folders that collect new errors by themselves (Settings → My positions) skip it.
- Share in the review and in New errors: the XGID, a picture of the board, or the board with the
  five best plays (or the cube actions) and the XGID.
- Reviews show Arrows, Board (the position alone) or After; After shows the position after the move
  picked in the list, not only the one played. Save is a labelled button.
- Match Library review: tap the open decision again to close its analysis and see the whole list.
- Settings → Display → Side panel: put the column with Undo and Done right of the board in
  landscape, next to Roll and Double.
- The computer no longer thinks before every roll in lost races: it looks deeper into resigning only
  when a quick look says it would resign.
- The ring around a stack of more than five checkers sits on its top checker.
- The CRAWFORD sign lies under the checkers, so a checker on the bar is no longer hidden.

## 0.7.2 — 2026-10-30 (build 39)

Match Library: import matches from eXtreme Gammon or GNU Backgammon and study both players'
mistakes.

Labels, filters and folders that fill themselves: My blunders and My positions sort your mistakes
by what they are, and the quiz asks the ones you pick.

Build 39 — 2026-10-30:
- Match Library (once History): import a match from a .mat file (eXtreme Gammon, GNU Backgammon or
  PlyGround), or open or share one with PlyGround. Choose which player you are and whether the
  opponent's mistakes go on your blunder list too; the match is analyzed in the background and
  left out of your statistics.
- Labels on every blunder and saved position: checker or cube, double or take side, contact, race
  or bear-off, money, match, Crawford or post-Crawford, error or blunder, opening, gammonish, whose
  mistake, and themes by rule of thumb (on the bar, hit, prime vs prime, holding game, backgame).
  Settings → My positions turns labels off and adds your own.
- My blunders: a Filter menu by labels; Clear clears only what the filter shows; the + button
  copies the blunders shown to My positions, to the folder linked to the filter, or split into
  Checker blunders and Cube blunders. A linked folder can collect new blunders by itself
  (Settings → My positions).
- My positions: categories are now folders. Checker blunders and Cube blunders are there from the
  start. Each position shows what was played in the game (set it by hand for others), its labels
  and your last quiz answer. Long-press to select several positions, then remove them from the
  folder or delete them. My positions explains which files Import reads.
- Quiz menu (Quiz… in My positions, or Quiz in a folder): folders, labels, 10, 20 or all questions,
  random or newest first; the result shows what was played in the game.
- In the Crawford game a CRAWFORD sign stands on the bar instead of the cube, in the review too
  (Settings → Display).
- Next mistake moves on to the next game of the match after a game's last mistake.
- Manual: a Manual button on the home screen and in About opens boldblot.com/plyground/manual.
- The welcome screen selects the depths in use (Put me on hold on a new install), and Continue
  keeps what is selected. A new install no longer drops to 1-ply when you just tap Continue.
- New installs show mistakes after the match by default.

## 0.7.1 — 2026-10-30 (build 38)

My positions: a library of positions you save into your own categories and quiz yourself on.

With PureTD analyzing, a second copy of the engine analyzes your game in the background while the
first one plays, so analysis no longer holds up the game.

Build 38 — 2026-10-30:
- My positions (experimental): save a position from your blunder list, a game review or Analyze
  Position into categories of your own, with a note and, if you like, your own right answer (marked
  with a ✓ in the analysis). Quiz yourself on the categories you pick: make the play or the cube
  decision, and see how it compares. Export categories as a file to share by Bluetooth, Quick Share
  or email; PlyGround imports it, and also imports a list of XGIDs.
- Analysis no longer holds up play when nothing is shown during the game ("Show mistakes after the
  match", or immediately with the practice prompt off): with PureTD analyzing it runs on a second
  copy of the engine in the background, or not at all (Settings → Training); with GNU BG or Open
  Sage it runs between games. The summary shows how many decisions are left and about how long,
  with Stop.
- Settings → Advanced engine settings: threads for the game and for the background analysis,
  analyze during play, background priority.
- Settings are grouped into Training, Analysis, Advanced engine settings, Play, Display, and Data
  and help; the game's ⋮ menu opens them during a match.
- Double and Pass ask for confirmation (Settings → Play): a bar across the board with Cancel and
  Double or Pass, and "Don't ask again".
- Hint, Keep rolling, Autofinish and Resign stay in place beside the board and grey out when they
  can't be used.
- Keep rolling: rolls for you even when you could double, until you turn it off, the cube changes
  hands or the game ends.
- Auto-roll no longer skips your cube decision when you are on the bar against a closed board.
- Bear off automatically once contact is broken (Settings → Play, off by default; not counted).
- Arrows for the computer's move: until your move, for 1 second, or off (Settings → Display).
- Winning chances meter: one bar for both sides with gammons and backgammons, in portrait too;
  numbers on request (Settings → Display). Turning it on mid-game shows it at once.
- Blunder list: blunders only, or errors and blunders (Settings → Training).
- When the computer's cube decision is analyzed at the same depth and engine it played with, it is
  worked out once instead of twice.
- About shows the Bold Blot logo.

Build 37 — 2026-10-28:
- Welcome screen, shown once on a new install or after an update, and again for each new version:
  what a ply is, and how long you want to wait for analysis during the game. Zero wait (1-ply),
  Almost zero wait (2-ply), Minimum wait (3-ply) or Put me on hold (4-ply cube decisions, 3-ply
  checker play). Without a choice, a new install analyzes at 1-ply and an update keeps your
  settings. About → Welcome screen opens it again.
- Checker play analysis: pruning is now on or off (Settings → Analysis, Analyze Position). When on,
  moves within 0.16 of the best at 1-ply are searched, and now always at least the five best: the
  filter could cut the best play when it looked more than 0.16 worse at 1-ply.
- About shows the build number.

## 0.7 — 2026-10-27 (build 36)

Three engines: GNU Backgammon and Open Sage join PureTD.
- New match → Engine: play PureTD, GNU Backgammon or Open Sage, at every PR level. The engine is
  saved with the match and shown in its name, e.g. "GNU BG PR <1".
- Settings → Analysis → Engine: the engine that analyzes your games, hints, PR, errors and luck
  (PureTD by default). Analyze again in the review and Analyze Position can use any of the three.
- Each engine uses its own networks, bear-off database, cube handling and match equity table;
  checker play goes through the same depths and move filters.
- PureTD's match play cube decisions use a cube-aware search, like its money ones: every cube
  state is followed through the tree, both sides deciding at every turn. Short races at 2-away and
  the last rolls of a match are now right; at 3-ply its cube error on FlashBack's match-score positions
  fell from 22.8 to 0.6 thousandths of a point.
- Match play: a player too good to double plays on for the gammon (the match cube model's values
  above the cash point now rise to the full win, as in money play).
- About: credits and licences for GNU Backgammon (GPL v3) and Open Sage (MPL 2.0).

Build 35 — 2026-10-27:
- Roll and Double fit between the checkers in portrait: smaller where the board is narrow, and
  kept within the four middle points, clear of the stacks on the 6- and 19-points.
- When the computer doubles: Pass sits in the computer's half beside the offered cube; Take and
  Beaver share one row in yours. The computer's faded last roll is hidden meanwhile. With the home
  board on the left, the offered cube no longer sits in your half.
- After a beaver: Play on and Raccoon on one row (the cube shows the value).

Build 34 — 2026-10-27:
- Game summaries: arrows step through the games of a match or session, in the review, in History
  and at the end of a match.
- Settings → After each game: the switch for the summary after each game now has its own section.
  When it is off, the end of a match offers to turn it back on, and the game's ⋮ menu has "Last
  game's summary".

Build 33 — 2026-10-26:
- Training mode "Show mistakes after the match" (was "after the game"): your errors and blunders
  are shown when the match ends, with "Review my mistakes"; in a money session, when you end it.
- The summary after each game can be turned off: in Settings → Training mode, or with "Don't show
  this after each game" on the summary itself. The end of a match always shows it.
- Mistake prompt: the board shows only your move, its arrows coloured as an error or a blunder;
  pick the best move in the list to see it. Settings → Training mode can show both moves' arrows.
- Opponents: PR <2 now plays at 2-ply (faster), and a new PR <1 plays at 3-ply (the former PR <2).

Build 32 — 2026-10-26:
- Eight boards to choose from, each shown with a small preview: Matt's (the grey board), Classic,
  Claude's, Walnut & ivory, Navy tournament, Red leather, Olive & cream and High contrast. Every
  board lets you choose your checker colour.
- Checker styles: Rimmed, Flat, Double ring and Domed.
- Settings → Board: "Bear off direction: Left / Right" replaces the mirrored-board switch (the same
  setting, kept as it was).
- Board designer (experimental): a colour for each part of the board and for both sides' checkers;
  save up to four designs, which appear in the board list.

Build 31 — 2026-10-26:
- Settings → Board: "Home board on the left (mirrored board)": the board, its numbers, the tray,
  dice, arrows and touch input all mirror; games and exports are unchanged.
- The end-of-game summary (PR, rating, luck, errors) for saved matches: "Summary" in a match's menu in
  History, and the Summary chip in the review (this game or the whole match).
- The computer is called by its engine and PR everywhere (History, My blunders, the game screen),
  e.g. "PureTD PR 4", as in exported files.

Build 30 — 2026-10-26:
- The engine's networks are stored at full precision (float32) instead of half precision: exactly
  PureTD's published weights. The app is about 4 MB larger.

Build 29 — 2026-10-25:
- Exported .mat files: set your own name in Settings (Exported files); the computer gets a short
  name: its engine and PR, such as "PureTD PR 4". (#1)
- The computer's own moves are no longer analyzed by default: about half the work per move, so the
  phone stays cooler. Turn it on in Settings, or use Analyze again in the review. (#2)

## 0.6 — 2026-10-25 (build 28)

First public release of Backgammon PlyGround.
