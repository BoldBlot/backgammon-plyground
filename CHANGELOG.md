# Changelog

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
