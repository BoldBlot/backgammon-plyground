# Changelog

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
