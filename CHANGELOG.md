# Changelog

## 0.6 — 2026-10-25

Build 32:
- Eight boards to choose from, each shown with a small preview: Matt's (the grey board), Classic,
  Claude's, Walnut & ivory, Navy tournament, Red leather, Olive & cream and High contrast. Every
  board lets you choose your checker colour.
- Checker styles: Rimmed, Flat, Double ring and Domed.
- Settings → Board: "Bear off direction: Left / Right" replaces the mirrored-board switch (the same
  setting, kept as it was).
- Board designer (experimental): a colour for each part of the board and for both sides' checkers;
  save up to four designs, which appear in the board list.

Build 31:
- Settings → Board: "Home board on the left (mirrored board)": the board, its numbers, the tray,
  dice, arrows and touch input all mirror; games and exports are unchanged.
- The end-of-game summary (PR, rating, luck, errors) for saved matches: "Summary" in a match's menu in
  History, and the Summary chip in the review (this game or the whole match).
- The computer is called by its engine and PR everywhere (History, My blunders, the game screen),
  e.g. "PureTD PR 4", as in exported files.

Build 30:
- The engine's networks are stored at full precision (float32) instead of half precision: exactly
  PureTD's published weights. The app is about 4 MB larger.

Build 29:
- Exported .mat files: set your own name in Settings (Exported files); the computer gets a short
  name: its engine and PR, such as "PureTD PR 4". (#1)
- The computer's own moves are no longer analyzed by default: about half the work per move, so the
  phone stays cooler. Turn it on in Settings, or use Analyze again in the review. (#2)

Build 28:

First public release of Backgammon PlyGround.
