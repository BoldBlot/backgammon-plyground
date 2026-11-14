# Changelog

## 0.8.14 — 2026-11-14 (build 77, internal test)

- Match Library: an .xg file with a checker borne off by a larger die than it needed (a 6 from the
  5-point) failed to import with "a roll of … without a move". Such plays are now read correctly.

## 0.8.13 — 2026-11-14 (build 76, internal test)

- Quiz result: the verdict chip (Right, Neutral, Wrong) is back in the top row, beside Share, Folder,
  Delete and Next; the buttons sit a little closer so it fits in landscape too. The trend chip
  stays in the row with Learned and Favourite.
- Quiz trend: the first quiz answer to a position is compared with the play made in the match or
  game, so it gets Improved, Same, Worse or Well done too. Positions set up by hand still get a
  trend from their second answer.

## 0.8.12 — 2026-11-14 (build 75, internal test)

- Quiz result: the verdict is now a coloured chip, Right (green), Neutral (yellow) or Wrong (red;
  was "Not this time"), followed by the quiz trend chip, Learned and Favourite. The chips wrap onto
  a new line when space is short, so the trend shows in landscape too and never pushes the Next
  button aside. Neutral answers are marked in yellow in the lists too.
- Quiz trend: a new label, Well done, for an answer that is right and as good as the last one;
  Same is now for an equal answer that is neutral or wrong.
- Quiz trend: a position asked again in the same session (Repeat until all are right, Repeat,
  Another) is compared with the answer just given, not with the one from before the quiz, so a
  worse second try now says Worse.
- Quiz trend: when the last answer was a play outside the analysis's candidates, the engine now
  values it, so the trend is no longer left out.
- The lists after the quiz (Positions in this quiz, Learned) show each position's trend label.

## 0.8.11 — 2026-11-14 (build 74, internal test)

- Quiz: a neutral answer. An answer that is not right but stays within the neutral level counts
  neither as right nor as missed, so the position's balance stays as it is; it never moves a
  position to Learned and does not come back with "Repeat until all are right". By default an
  error is neutral and only a blunder is missed. Settings → Quiz, "A neutral answer is": Not an
  error, Not a blunder, Fixed level or Adaptive, also under a training plan. With the neutral level
  at or below the level for a right answer, no answer is neutral. Positions count their neutral
  answers, and the Filter menus have "Neutral".
- Settings → Quiz, "A right answer is": the choices now run from the strictest to the most lax
  (Best play only, Not an error, Fixed level, Adaptive). Adaptive starts at 20% of the library's
  average error for a right answer and 50% for a neutral one (a share you had already set stays),
  and the sliders go down to 10%.
- Quiz: each answer is compared with your last answer to the same position. The position gets the
  label Improved, Same or Worse (group "Quiz trend", in green, grey and red), shown beside the
  verdict and with the position's labels; the Filter menus can pick positions by it.
- Match Library: eXtreme Gammon's .xg files can be imported directly, from the import button or by
  opening the file with PlyGround. The match comes with the analysis in the file, named in the
  analysis ("eXtreme Gammon 3-ply", "eXtreme Gammon XG Roller++", "from the .xg file"); decisions
  the file has no analysis for are analyzed in the background. After Analyze again, "Restore
  original" in the review puts the file's analysis back.

## 0.8.10 — 2026-11-12 (build 73, internal test)

- Matches with PureTD: new, experimental analysis depths that end in short played-out games, named
  X/YY/Z: X-ply first, then YY games of Z turns each. Checker play: 3/36/7, 3/108/7, 3/144/7 and
  3/288/7 (the games are played for the best 3 to 5 plays, on the same dice), and "4-ply, fast"
  (2-ply and 3-ply narrow the plays before 4-ply). Cube decisions: 3/44/7, 3/48/11 and 3/288/11
  (played out when 3-ply leaves the decision closer than 0.1). In each game both sides play 1-ply
  moves by match-winning chance and handle the cube; the luck of every roll is taken off the result,
  which is why so few games are enough. The same position always gives the same values. On
  406 cube decisions and 186 checker plays that XG rolled out, the equity lost per
  decision fell from 0.0029 (4-ply) to 0.0015 (3/44/7, about the same time) and
  0.0009 (3/288/11); for checker plays from 0.0051 (4-ply) to 0.0030 (3/144/7).
  Timed on a PC only so far.
- Settings → Analysis now has separate depths for money games (as before) and for match play (the
  plies, plus the new depths). Match play starts with your money settings.
- A search you wait for now shows a progress bar that says what it is doing ("3-ply on 16 plays",
  "Played-out games: 96 of 144"). Past a time you set (5, 10, 15 or 30 s, or any number of seconds;
  15 s for an analysis, 5 s during play) it offers to keep waiting, to stop, or to stop and open
  the settings. It never stops by itself. A stopped search shows what it has, marked "incomplete":
  the games played so far, or the last ply that finished. Leaving the screen stops it too.
- Settings → Analysis, "Played-out games only for errors" (on): when whole games are analyzed
  (during play, in the background, Analyze again), games are played out only for the checker plays
  that 3-ply calls an error. An analysis of one position always plays them out.
- Settings → Analysis, "± beside cube decisions from played-out games" (off): a small ± under the
  cube table opens the standard error of the games.
- New opponent "New Best (experimental)": in matches with PureTD it plays 3/36/7 and doubles by
  3/44/7; in money games and with the other engines it plays as PR <1.
- Intuition training and autofinish can use 3/36/7 (and Intuition 3/144/7) in matches; in money
  games they use 3-ply.
- Speed test: also times the new depths and recommends match settings: the deepest whose average
  stays within half the time an analysis may take before it offers to stop.

## 0.8.9 — 2026-11-09 (build 72)

- PureTD, matches: inside its search, each side now picks its moves by its match-winning chance at
  the score, not by money equity (where gammons always count double). In a Crawford game, for
  example, the leader no longer plays for gammons that are worth nothing and leaves fewer shots.
  Errors fell by about 10% on 6,692 rolled-out decisions from 5-point matches (by almost a quarter
  once the cube is dead). Money games are unchanged.
- Training: when the mistake shown is a missed double, the dice are rolled only after Continue (or
  Try again), so no move can be started under the card. The mark button now works while a mistake
  is shown: it marks the decision the card is about.
- Speed in endgames, on the computer's turn: its own cube analysis stops at 2-ply when that settles
  the decision by a wide margin (as the background analysis does since 0.8.8); its roll is shown
  before its move is worked out; Grandmaster's search is no longer done a second time for the
  analysis of its move; and once both sides are bearing off with a checker off each, checker plays
  are not searched deeper than 1-ply, where PureTD's bear-off table is already exact.
- Import: .mat files with "Cannot Move" after a roll, or BackgammonGalaxy's "Losses 1 point" on a
  game's last line, now import (one such line used to fail the whole file with "Unreadable move").
- Error report: the attached file now starts with all your settings (your name left out). "Send
  error report" is also in About.

## 0.8.8 — 2026-11-08 (build 71)

- Analysis: the background analysis no longer lags behind at the end of a game. A cube decision that
  a 2-ply search settles by a wide margin (about 0.3 or more between the two options) is not
  searched to the full cube depth; its analysis then says 2-ply. "Analyze again" always uses the
  depth you pick. The log now records the time of each background analysis.
- Game: a tap on the bear-off tray now bears off as many checkers as possible (the best by 1-ply
  when several plays take the same number), as Max Bearoff does.
- Game: making a point is now a plain tap on an empty point or an opponent's blot (the long press
  is gone); a tap on your own blot or point moves that checker as before. It keeps the moves
  already made, adds only the checkers that make the point (two, with a double) and leaves the
  other checkers where they are.
- Settings → Data: "Me first in .mat files" is now on by default (your choice stays if you set it).

## 0.8.7 — 2026-11-07 (build 70, internal test)

- PureTD: the new networks of November 2026 (a small gain over the earlier ones in 80,000 games of
  self-play), and a second, compact network pair: Settings → Advanced engine settings, "PureTD
  network", Wide 5L (the default, strongest) or Compact 4L (about 1.9 times faster, but weaker in
  full games). The network in use is named in each analysis ("PureTD 4L"). Analyses made with the
  earlier network may differ slightly from a new analysis of the same position.
- Money games: checker plays are now scored with the cubeful network (the cube is part of the choice)
  for the computer's moves, hints and analysis. Settings → Advanced engine settings, "Cubeful checker
  play (money games)" turns it off (about 25% slower when on). Matches are unchanged.
- Library: a match that is being analyzed after an import shows a progress bar on its tile.
- Game: a tap on the bear-off tray sets up a play that bears off two checkers (the best by 1-ply
  when several do; the most there is when two is not possible).
- Game: a long press on a point sets up a play that makes that point (replaced by a tap in 0.8.8).
- Settings → Data: "Me first in .mat files" writes you as Player 1 (left column) when exporting, and
  preselects the first player as you when importing.

## 0.8.6 — 2026-11-06 (build 69)

- Landscape game screen: Max Bearoff (without its icon here) and Keep rolling now share one row.
- My positions: the cog is gone from the top bar. The shortcut to Settings → My positions is now
  in the ⋮ menu of a position (open a folder, pick a position).

## 0.8.5 — 2026-11-06 (build 68, internal test)

- Landscape game screen: the side panel no longer needs scrolling. Resign takes Keep rolling's
  place beside Hint, the back and forward arrows sit at the end of that row (under the blot), and
  Max Bearoff and Keep rolling share the row below.
- Game: the "Back to game" button on the board is wider, so its text is no longer cut.
- Game: with "Match score as away" on (Settings → Display), the players' scores read 2-away,
  3-away during play too.
- My positions: a cog in the top bar opens Settings → My positions.
- Settings → My positions: the duplicate "Method" heading in New errors is gone.

## 0.8.4 — 2026-11-06 (build 67, internal test)

- Fixed a crash in 0.8.3: starting a match closed the app whenever the Match Library held fewer
  games than its limit (the limit check counted a negative number of games to delete).

## 0.8.3 — 2026-11-06 (build 66, internal test)

- Game: before your roll, the Undo and Done chips become Roll and Double (the buttons that are
  on the board), so the chips stay in one place.
- Game: the blot (mark) chip is lit in negative colours when it is on.
- Game: back and forward arrows left of the chips step through the positions of your earlier
  turns in the game, with their dice and cube. The arrows keep their place, and the position you
  look at has a "Back to game" chip (and button on the board) in place of the usual chips. The
  blot marks that position, or takes its mark off, and the marked position goes to New Marked.
  Settings → Display, at the top, turns the arrows off.
- Settings → Display: "Match score as away" shows 2-away, 3-away instead of the score and the
  length of the match in My positions, the quiz and shared pictures.
- Settings: Quiz and My positions are two pages now. New errors, with the training plan, is at
  the top of My positions.
- Quiz: the summary lists the positions that went to Learned in the round, under "Learned"; tap
  one to put it back in My positions.
- Putting a position back from Learned (in Learned, on the position, or in the quiz) asks
  whether to reset its right, missed and balance counters.
- Quiz: after an answer, Played and Best are the same boxes as in the match review, and tapping
  one shows that play on the board and in the list.
- Quiz: when you lower the Learned balance (Settings → Quiz), you are asked whether to go through
  My positions now and move the positions at the new balance to Learned.
- Quiz menu: 10, 20, 30 or All questions, 10 first.
- My positions: the average error of the saved positions is shown under the buttons.
- Match Library: it keeps at most 100 games by default (Settings → Data and help: any number;
  0 is no limit, with a warning for 0 and for more than 1000). The oldest game is deleted when a
  new one is saved. The Library shows how many games it holds, with a cog to the setting. Positions
  you saved from a deleted match stay.
- Every saved position has an ID tag such as #AB012, the first tag shown. My positions and the
  quiz filter has an "ID#" part to show a position by its ID.
- New errors and New Marked: Keep and Delete are chips, midway between the Played / Best boxes and
  the arrows row (portrait), or between the arrows row and the edge of the screen (landscape).
- Three new labels for a blunder: Missed hit, Missed point and Missed running, when the best play
  hit, made a point or moved a checker out of the opponent's home board, you did not, and no
  play without it was acceptable. Positions saved before this version don't get them.

## 0.8.2 — 2026-11-06 (build 65)

- Played and Best in review: always two boxes side by side, so the board and buttons no longer
  jump. Their small labels sit on a line above each box, and long moves shrink a little before
  they are cut.
- Review: Next mistake is now Next, with a Previous beside it. Both stop at your mistakes (and
  marked positions); the arrows step through every decision.
- New errors and New Marked: cube errors, and errors without analysis, show Played and Best too.
- New errors: Keep and Delete replace "Keep, next". Keep files the error into My positions the way
  the automatic methods do (Settings → Sort into: no folder, or the Checker and Cube folders) and
  shows the next one; Delete removes it.
- My positions: the New Marked and Learned buttons stay on one line (Marked, Learned). A ? button
  shows or hides the help text.
- Folders in My positions have a sort button: Newest first, or Biggest first (the error you made in
  the game; positions without one come last).

## 0.8.1 — 2026-11-05 (build 64, internal test)

- Winning chances bar: fixed. It always worked out the chances as if you were on roll, so during
  the computer's turn it was wrong (it could call you the favourite when the computer was doubling
  in a bearoff it was winning). It now evaluates whoever is on roll and, once the dice are down,
  the best play of that roll, so it matches the review.
- Marked positions: a blot chip next to Undo and Done marks your next decision (roll or double,
  take or pass, or a move).
  - The game and match summary has "Review marked" next to Review, showing only the marked
    positions: that game's, or the whole match's at its end.
  - Review has a Marked chip, on when you open it from the summary. With All, every decision
    stays listed and Next mistake also stops at marked ones. With Mine or My mistakes, or on its
    own, only marked positions are listed. Opening a finished match later from the Match Library
    has no Marked chip.
  - My positions has a New Marked button, between New errors and Learned. Marked positions wait
    there instead of in New errors, even when they weren't errors, and are not filed
    automatically. Saved from there or from the review, they keep a Marked label.
- Training plan (Settings → My positions): Error (0.02+), Blunder (0.08+) or Big blunder (0.12+)
  set both the level for My positions and what the quiz accepts. Custom, the default, keeps your
  settings and sets them separately.
- Quiz: a right answer can now also be a Fixed level, or Adaptive: a share of My positions'
  average error, never below 0.02.
- Favourites: a star on a saved position, and on the quiz card after answering, puts it in a
  Favourites folder. A favourite does not move to Learned.
- Quiz: a Learned chip on the card after answering, Finish is a chip, and the results show a green
  check or a red X on each position.
- Double match point: a DMP sign stands on the bar instead of the cube, like the Crawford sign
  (the same setting turns both off).
- Played and Best chips in review: moves are no longer cut short ("Doubl…"). When they don't fit
  side by side, the chips stack.

## 0.8.0 — 2026-11-03 (build 63)

- The first closed-test (alpha) release since 0.7.10: everything from 0.7.11 to 0.7.17 below.
  The app is the same as 0.7.17; only the version number changed.

## 0.7.17 — 2026-11-03 (build 62, internal test)

- Quiz: the chip back from the answered cards is now "Back to Quiz", so it fits.
- A folder's Quiz button (top right) is a chip in negative colours, like Undo and Done.

## 0.7.16 — 2026-11-03 (build 61, internal test)

- Checkers: two new styles, flat with a pattern of their own on every checker. Catalin has soft
  forking veins in a deeper shade of the checker's colour (tan on white and ivory), like old
  catalin checkers; Swirl has smoky folds, like swirled resin. Catalin replaces Marble: if you
  had Marble or Domed, you now have Catalin.
- A saved position: a Learned chip next to Labels sends it to Learned (the chip lights up) and,
  pressed again, puts it back in My positions, in the folders it was in. It is lit on every
  position in Learned.
- My positions: the Experimental tags are gone.
- Quiz menu: "Start the quiz" is now Start, in the top bar.
- Quiz: "Back to the quiz" is a chip, in negative colours like Undo and Done.
- The ⚡ Quick quiz button is in negative colours too.
- The Labels and Folders lists, and the label filter menu, show a scroll bar when they scroll.

## 0.7.15 — 2026-11-03 (build 60, internal test)

- Move arrows: a checker that hits on its way is drawn as two arrows, cut at the hit (24/18*/13).
  One long arrow now always means no hit on the way. The computer's arrows do the same.
- Checkers: the Domed style is gone; the new Marble style (flat, with marble veins) takes its
  place, and anyone who had Domed now has Marble.
- Quick quiz: a small ⚡ button beside My positions on the main screen starts a quiz right
  away: all positions, 10 of them, in random order. In the quiz menu, "Save as quick quiz" makes
  the menu's choices the quick quiz, and "Load quick quiz" brings them back. Settings → My
  positions → Quick quiz puts the button on the left or the right.
- Quiz: "Finish" in the top bar ends the quiz early and shows its summary. At the end, "Again"
  is now "Repeat" (the same cards again), and with 10 or 20 cards "Another 10" starts a new quiz
  with the same settings, using cards not yet asked since you opened the quiz.
- Quiz order: "Worst blunders" next to Random and Newest first: the biggest error in the game
  first.
- Learned: "Delete learned positions" is now "Move to Learned". A position that reaches the
  balance goes to My positions → Learned instead of being deleted: out of the quiz and the
  folder counts, and you can put it back (it returns to its folders) or delete it. Learned keeps
  the newest 50 (Settings → My positions → Quiz → "Learned keeps"). Turn "Learned" off and
  learned positions are deleted at once, as before. Saving a learned position again puts it back.
- New errors: saving an error to My positions from its own screen now takes it off New errors,
  as "Add to My positions" does.

## 0.7.14 — 2026-11-02 (build 59, internal test)

- My positions filters: the quiz balance options are now "≤ −3 … ≤ 0, 0, ≥ 0 … ≥ +3" and include
  their bound (≤ −1 takes in −1). A saved "Below 0" filter becomes "≤ 0", so it now also takes in
  a balance of 0.
- A saved position: "Analyze again" moved into ⋮ (in place of Copy XGID, which Share already
  does) and asks for the engine and depths, as in the quiz. A folder's ⋮ in My positions has
  "Analyze again" too: every position in it, one after another, with the progress and a Stop
  button at the top. It goes on if you leave the screen.
- Shared board pictures show a checker in each player's colour by the score ("● 2–1 ●").
- Undo and Done: always there during play, greyed out until you can use them. Portrait now uses
  the same chips as landscape, and they are centred in the panel. The quiz board does the same.

## 0.7.13 — 2026-11-02 (build 58, internal test)

- Winning chances bar: single wins are now in the checker colour, gammons a shade brighter and
  backgammons brighter still (light checkers get darker instead).
- Scores show a checker in each player's colour on either side ("● 3–5 ●", your score by your
  checker): in the quiz, My positions, a saved position and New errors.
- Landscape: Undo and Done are back in the side panel, as small chips in negative colours (bright
  in dark mode, dark in light mode). Undo appears once a checker has moved, Done once the move is
  complete. Settings → Board → "Undo and Done on the board" puts them on the board as in 0.7.11
  (off for everyone after this update). The quiz uses the same chips in landscape.
- Quiz: a Settings button in the title bar; changes apply when you come back. The card arrows and
  "Back to the quiz" sit under the position line, above the answer.
- New errors: the Filter shows how many errors pass it ("Filter: 12 of 40") and how many each
  option would leave.

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
