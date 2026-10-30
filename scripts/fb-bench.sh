#!/usr/bin/env bash
# Quick checker-play check against FlashBack cards' eXtreme Gammon figures, before a long test.
#
# Builds the engines for this computer (-march=native: AVX2/FMA on a current x86 laptop, several
# times faster than the portable build the unit tests use; equities can differ from the phone's in
# the last decimals), runs the chosen engines on the chosen positions, and prints the average error
# per position in mEq with a 95% range, how often each engine found the card's best play, and the time.
#
#   scripts/fb-bench.sh                          # PureTD on all 172 Boot Camp positions
#   scripts/fb-bench.sh --limit 40               # the first 40 only (a minute or so)
#   scripts/fb-bench.sh --engines ptd,sage --set Backgames
#   scripts/fb-bench.sh --filter ALL             # pruning off: every play at full depth
#   scripts/fb-bench.sh --set "Opening Replies" --plies 2
#   scripts/fb-bench.sh --portable               # the unit tests' build (same numbers as the phone's code)
#
# The FlashBack card data is not in this repository (the cards are FlashBack's, by Jeremy Bagai /
# The Fortuitous Press). Point FB_DATA at a folder with plays_bc_enc.json and plays_replies.json.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
FB_DATA="${FB_DATA:-$HOME/code/BoldBlot/engine/sage/runs/flashback}"
ENGINES=ptd SET="Boot Camp" LIMIT="" PLIES=3 FILTER=WIDE FAST=1 OUT=""
while [ $# -gt 0 ]; do
    case "$1" in
        --engines) ENGINES=$2; shift 2 ;;
        --set) SET=$2; shift 2 ;;
        --all) SET=""; shift ;;
        --limit) LIMIT=$2; shift 2 ;;
        --plies) PLIES=$2; shift 2 ;;
        --filter) FILTER=$2; shift 2 ;;
        --portable) FAST=0; shift ;;
        --out) OUT=$2; shift 2 ;;
        -h|--help) sed -n '2,20p' "$0"; exit 0 ;;
        *) echo "unknown option $1 (see --help)" >&2; exit 2 ;;
    esac
done
JAVA_HOME="${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")}"
export JAVA_HOME

if [ "$FAST" = 1 ]; then
    BUILD="$ROOT/build/engine-host-native"
    cmake -S "$ROOT/engine" -B "$BUILD" -G Ninja -DCMAKE_BUILD_TYPE=Release \
        -DCMAKE_C_FLAGS="-march=native" -DCMAKE_CXX_FLAGS="-march=native" >/dev/null
else
    BUILD="$ROOT/build/engine-host"
    cmake -S "$ROOT/engine" -B "$BUILD" -G Ninja -DCMAKE_BUILD_TYPE=Release >/dev/null
fi
cmake --build "$BUILD" >/dev/null

WORK="$ROOT/build/fb-bench"
mkdir -p "$WORK"
INPUT="$WORK/checker.txt"
python3 - "$FB_DATA" "$INPUT" <<'PY'
import json, os, sys
data, out = sys.argv[1], sys.argv[2]
with open(out, "w") as f:
    for name in ("plays_bc_enc.json", "plays_replies.json"):
        d = json.load(open(os.path.join(data, name)))
        for k, pos in d["positions"].items():
            plays = d["plays"].get(k)
            if plays:
                f.write("C\t%s\t%s|%s\t%s\t%s\n" % (k, pos["set"], pos["deck"], pos["xgid"], ";".join("%s=%s" % (p[1], p[2]) for p in plays)))
PY
OUT="${OUT:-$WORK/result-$(date +%Y%m%d-%H%M%S).tsv}"

echo "Running: engines $ENGINES, ${SET:-all sets}${LIMIT:+, first $LIMIT}, ${PLIES}-ply, filter $FILTER, $([ "$FAST" = 1 ] && echo native || echo portable) build"
FB_INPUT="$INPUT" FB_OUT="$OUT" FB_ENGINES="$ENGINES" FB_SET="$SET" FB_LIMIT="$LIMIT" FB_PLIES="$PLIES" FB_FILTER="$FILTER" \
    "$ROOT/gradlew" -p "$ROOT" -q :app:testDebugUnitTest --rerun -PbgHostLib="$BUILD/libbgjni.so" \
    --tests '*engine.FlashbackBench*' >/dev/null

python3 - "$OUT" <<'PY'
import math, statistics as st, sys
rows = [l.rstrip("\n").split("\t") for l in open(sys.argv[1])]
head, rows = rows[0], rows[1:]
engines = [h[:-4] for h in head if h.endswith(".err")]
print(f"{len(rows)} positions  (results: {sys.argv[1]})")
print(f"{'engine':8} {'error mEq':>14} {'best play':>11} {'not listed':>11} {'median s':>9} {'total s':>8}")
for e in engines:
    i = head.index(e + ".err")
    err = [1000 * float(r[i]) for r in rows]
    sec = [float(r[i + 2]) for r in rows]
    ci = 1.96 * st.stdev(err) / math.sqrt(len(err)) if len(err) > 1 else 0
    best = sum(1 for v in err if v < 0.5)
    out = sum(1 for r in rows if r[i + 1] == "out")
    print(f"{e:8} {st.mean(err):7.2f} ± {ci:4.2f} {best:5}/{len(rows):<5} {out:11} {st.median(sec):9.2f} {sum(sec):8.0f}")
if len(engines) > 1:
    a = head.index(engines[0] + ".err")
    for e in engines[1:]:
        b = head.index(e + ".err")
        d = [1000 * (float(r[a]) - float(r[b])) for r in rows]
        ci = 1.96 * st.stdev(d) / math.sqrt(len(d)) if len(d) > 1 else 0
        print(f"{engines[0]} - {e}: {st.mean(d):+.2f} ± {ci:.2f} mEq per position (negative: {engines[0]} better)")
PY
