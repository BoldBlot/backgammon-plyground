#!/usr/bin/env python3
"""Imports .mat files into GNU Backgammon and compares gnubg's re-export with the original.

Usage: scripts/check-mat.py [DIR]   (default: app/build/mat-export, written by
ResignExportSimulationTest). Needs the gnubg command line program.

Differences that are expected from gnubg's import and therefore allowed:
  - gnubg numbers the games of a file from 1 (a single exported game may be "Game 3");
  - a roll followed by a resignation is written without a move; when that roll had
    exactly one legal play, gnubg fills that play in.
"""
import pathlib, re, subprocess, sys, tempfile

root = pathlib.Path(__file__).resolve().parent.parent
src = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else root / "app/build/mat-export"
problems = re.compile(r"warning|invalid|unrecognised|not a valid|error", re.I)

def body(path):
    lines = path.read_text().replace("\r", "").split("\n")
    return [re.sub(r"^ Game \d+$", " Game", l.rstrip()) for l in lines if l.strip() and not l.startswith(";")]

def same(ours, theirs):
    if ours == theirs:
        return True
    # "66:" (resigned after rolling) vs gnubg's "66: 8/2 8/2" (forced play filled in).
    m = re.match(r"^(.*\d\d:)$", ours)
    return bool(m) and theirs.startswith(m.group(1) + " ")

failed, files = 0, sorted(src.glob("*.mat"))
with tempfile.TemporaryDirectory() as tmp:
    for f in files:
        out = pathlib.Path(tmp) / f.name
        log = subprocess.run(["gnubg", "-t", "-q"], input=f"import mat {f}\nexport match mat {out}\nquit\ny\n",
                             capture_output=True, text=True, timeout=300).stdout
        bad = [l for l in log.splitlines() if problems.search(l)]
        if bad or not out.exists():
            print(f"FAIL {f.name}: gnubg reported: {bad[:3]}")
            failed += 1
            continue
        a, b = body(f), body(out)
        diffs = [(x, y) for x, y in zip(a, b) if not same(x, y)]
        if len(a) != len(b) or diffs:
            print(f"FAIL {f.name}: {len(a)} vs {len(b)} lines; first differences: {diffs[:3]}")
            failed += 1
print(f"{len(files)} files checked, {failed} failed")
sys.exit(1 if failed else 0)
