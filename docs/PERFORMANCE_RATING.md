# Errors and Performance Rating (XG definition)

Backgammon PlyGround measures mistakes exactly as described in the eXtreme Gammon 2
manual (sections *Analyze › Categories* and *Performance*). The implementation is
`app/src/main/java/com/boldblot/backgammonplyground/core/Analysis.kt` (`XgRules`, `DecisionMath`,
`PerformanceTotals`) and is covered by `XgRulesTest`.

## Unit: normalized equity

Every error is a loss of **normalized equity**: equity per unit cube. In money
games that is the cubeful equity divided by the cube value; in match play
match-winning-chance losses are converted to "equivalent to money game" (EMG) equity with
the app's match equity table. The engine reports move rankings and cube equities in this
unit.

## Categories

| Error (normalized) | Category |
| --- | --- |
| more than 0.080 | Blunder |
| more than 0.020 | Error |
| above 0, up to 0.020 | Inaccuracy (XG calls it "Different") |
| 0 | Best |

In **1-point matches** every error is multiplied by **1.5** before it is
categorized and before it enters PR (so a 0.054 mistake is already a blunder).

## Which decisions count

A *decision* is a checker play or cube action that is **not obvious**.
Obvious, and therefore excluded from PR, are:

- forced moves (one legal play, or none);
- checker plays where the best and the worst legal play differ by less than 0.001;
- "no double" decisions where doubling and not doubling have the same equity
  (optional double);
- "no double" decisions where the equity before doubling exceeds the equity after
  the double by 0.200 or more (obvious no double);
- "no double" decisions where the equity before doubling exceeds the equity after a
  pass by 0.200 or more (obvious too good to double);
- "no double" decisions where the equity before and after a double is very
  negative (trailing far behind in a match). XG does not publish this threshold;
  Backgammon PlyGround uses −0.900 for both.

Never obvious: any decision that **results in a double**, and every **take or pass**.

Decisions made after asking for a hint are recorded but not counted.

## Formulas

```
performance = total equity lost / number of decisions
PR          = 500 × performance
```

PR is shown overall, for checker play only and for cube decisions only
(doubles + takes). The XG rating names are used for PR bands: World Champ
(< 2.5), World Class (< 5), Expert (< 7.5), Advanced (< 12.5), Intermediate
(< 17.5), Casual Player (< 22.5), Beginner (< 30), Distracted.

The statistics screen also shows XG's Elo equivalent of a PR,
`2000 / (1 + e^(−performance × 40 − 1.12)) + 732` with performance = −PR/500.

Worked example from the XG manual (a unit test): one blunder of 0.105 and one
error of 0.035 in 13 decisions → performance 0.0108 → PR 5.38 (Expert).

## Error per decision type

| Decision | Error |
| --- | --- |
| Checker play | equity(best play) − equity(played play), plays ranked at the analysis depth; the played play is always re-evaluated at full depth |
| Double | max(no double, min(double/take, double/pass)) − min(double/take, double/pass) |
| No double | max(no double, min(double/take, double/pass)) − no double |
| Take | max(−double/take, −double/pass) − (−double/take) |
| Pass | max(−double/take, −double/pass) − (−double/pass) |

## Other numbers on the statistics screen

- **NJ / GNUBG error rate**: 1000 × your total error / (your moves + the computer's
  moves), in millipoints per move, as defined by Backgammon NJ.
- **Luck** of a roll: equity of the best play with that roll minus the average over
  all 36 rolls (30 for the opening roll), at 1-ply, as GNU Backgammon defines it.
- **Luck rating** (game summary): GNU Backgammon's, from the average luck per roll
  × 10: above 0.6 "Go to Las Vegas", above 0.3 "Good dice, man!", below −0.6 "Go to
  bed", below −0.3 "Bad dice, man!", otherwise "None".
- **Rating**: FIBS formula. P(win) = 1 − 1/(10^(D·√N/2000) + 1); the winner gains
  4·√N·(1 − P), the loser loses 4·√N·P, times max(1, 5 − experience/100) for new
  players. The computer levels have fixed ratings (Beginner 1400 … Grandmaster 2050),
  which are estimates.

## Analysis depth

Decisions are analyzed with the PureTD engine, counting plies as XG does (1-ply = the
neural network alone). By default checker plays are searched to 3-ply for every move within
±0.16 of the best at 1-ply, and cube decisions to 4-ply; both are set in Settings (1- to
4-ply, and for checker play which moves are searched: ±0.04, ±0.08, ±0.16 or all). XG itself
usually analyzes deeper (rollouts), so individual equities can differ slightly from XG's; the
method of turning equities into errors and PR is the same.

The computer's decisions are analyzed at the same depths, or not at all (Settings): the
computer then has no PR. A game or a whole match can be analyzed again at other depths from
the review ("Analyze again").
