#!/usr/bin/env bash
# Release steps for Backgammon PlyGround in one command. Quiet: each step's full output goes to
# build/release-logs/<step>.log and only results (or the tail of a failing log) are printed.
#
#   scripts/release.sh next
#       Print the next version name and build (= Play versionCode).
#
#   scripts/release.sh internal "<one-line summary for the commit>" [--dry-run]
#       Before: CHANGELOG.md's first version heading is
#       "## <version> — <date> (build <code>, internal test)" for the next build, and Known issues
#       is up to date. Sets app/build.gradle.kts to that version (safe to rerun), runs the engine
#       tests (scripts/host-tests.sh, fresh build dir), builds the bundle, uploads it to internal,
#       commits and pushes main (no tag), then rebuilds, deploys and commits the website changelog.
#
#   scripts/release.sh alpha --notes-file <file> --manual-done [--dry-run]
#       Promotes the current build (already on internal) to alpha without rebuilding: drops the
#       "internal test" marker from CHANGELOG.md, uploads the notes (en-CA, max 500 chars), commits,
#       tags v<version>, pushes main and the tag, then deploys and commits the website. Update the
#       manual (MANUAL_BODY in website/tools/make_pages.py) first; --manual-done confirms that.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
SITE=${BOLDBLOT_SITE:-$HOME/code/BoldBlot}
GRADLE=$ROOT/app/build.gradle.kts
CHANGELOG=$ROOT/CHANGELOG.md
AAB=$ROOT/app/build/outputs/bundle/release/app-release.aab
LOGS=$ROOT/build/release-logs
CO_AUTHOR=${CO_AUTHOR:-"Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"}
DRY=0
mkdir -p "$LOGS"

die() { echo "ERROR: $*" >&2; exit 1; }
step() { echo "▸ $*"; }
# run <log name> <command...>: quiet unless it fails.
run() {
  local log="$LOGS/$1.log"; shift
  if [ "$DRY" = 1 ]; then echo "  (dry run) $*"; return; fi
  "$@" >"$log" 2>&1 || { echo "  FAILED. Last lines of $log:"; tail -25 "$log"; exit 1; }
}

cur_code() { grep -oP 'versionCode = \K[0-9]+' "$GRADLE"; }
cur_name() { grep -oP 'versionName = "\K[^"]+' "$GRADLE"; }
bump_patch() { awk -F. -v OFS=. '{$NF = $NF + 1; print}' <<<"$1"; }
# First version heading of CHANGELOG.md (skips "## Known issues").
top_heading() { grep -m1 -E '^## [0-9]' "$CHANGELOG"; }
# Body of the first version entry, as plain text, cut to 500 characters (Play's limit).
top_entry_notes() {
  python3 -I - "$CHANGELOG" <<'PY'
import re, sys
text = open(sys.argv[1], encoding="utf-8").read()
m = re.search(r"^## \d.*?\n(.*?)(?=^## |\Z)", text, re.S | re.M)
body = re.sub(r"\s+", " ", m.group(1)).strip() if m else ""
print(body if len(body) <= 500 else body[:497].rsplit(" ", 1)[0] + "…")
PY
}

check_repo() {
  [ "$(git -C "$ROOT" branch --show-current)" = main ] || die "not on main"
  local untracked
  untracked=$(git -C "$ROOT" ls-files --others --exclude-standard)
  [ -z "$untracked" ] || die "untracked files (git add or ignore them first):
$untracked"
}

test_summary() {
  python3 -I - "$ROOT/app/build/test-results/testDebugUnitTest" <<'PY'
import glob, sys, xml.etree.ElementTree as ET
n = bad = skipped = 0; failed = []
for p in glob.glob(sys.argv[1] + "/*.xml"):
    r = ET.parse(p).getroot()
    n += int(r.get("tests", 0)); skipped += int(r.get("skipped", 0))
    for c in r.iter("testcase"):
        if c.find("failure") is not None or c.find("error") is not None:
            failed.append(f"{c.get('classname')}.{c.get('name')}")
print(f"  {n} tests, {len(failed)} failed, {skipped} skipped")
for f in failed[:20]: print("  FAILED:", f)
sys.exit(1 if failed else 0)
PY
}

website() { # website <commit message>
  step "Website: changelog pages, deploy, local commit"
  run make-pages bash -c "cd '$SITE' && python3 website/tools/make_pages.py"
  run deploy "$SITE/website/deploy.sh"
  if [ "$DRY" = 1 ]; then echo "  (dry run) commit website in $SITE: $1"; return; fi
  git -C "$SITE" add website
  git -C "$SITE" diff --cached --quiet || git -C "$SITE" commit -q -m "$1" -m "$CO_AUTHOR"
}

cmd=${1:-}; shift || true
args=()
notes_file=""; manual_done=0
while [ $# -gt 0 ]; do
  case "$1" in
    --dry-run) DRY=1 ;;
    --notes-file) notes_file=$2; shift ;;
    --manual-done) manual_done=1 ;;
    *) args+=("$1") ;;
  esac
  shift
done

case "$cmd" in
next)
  echo "current: $(cur_name) (build $(cur_code))"
  echo "next:    $(bump_patch "$(cur_name)") (build $(( $(cur_code) + 1 )))"
  ;;

internal)
  summary=${args[0]:-}; [ -n "$summary" ] || die 'usage: release.sh internal "<summary>"'
  check_repo
  head=$(top_heading)
  [[ $head =~ ^##\ ([0-9.]+)\ .*\(build\ ([0-9]+),\ internal\ test\)$ ]] \
    || die "first CHANGELOG entry isn't an internal test build: $head"
  name=${BASH_REMATCH[1]}; code=${BASH_REMATCH[2]}
  cc=$(cur_code)
  [ "$code" = $((cc + 1)) ] || [ "$code" = "$cc" ] \
    || die "CHANGELOG says build $code, build.gradle.kts has $cc (expected $((cc + 1)))"
  echo "Internal release $name (build $code)"

  step "Version in app/build.gradle.kts"
  if [ "$DRY" = 1 ]; then echo "  (dry run) versionCode $code, versionName $name"
  else sed -i -E "s/versionCode = [0-9]+/versionCode = $code/; s/versionName = \"[^\"]+\"/versionName = \"$name\"/" "$GRADLE"
  fi

  step "Engine tests (host engine)"
  if [ "$DRY" = 1 ]; then echo "  (dry run) scripts/host-tests.sh"
  else
    tdir=$(mktemp -d); rc=0
    BUILD_DIR="$tdir" "$ROOT/scripts/host-tests.sh" >"$LOGS/host-tests.log" 2>&1 || rc=$?
    rm -rf "$tdir"
    test_summary || die "engine tests failed (log: $LOGS/host-tests.log)"
    [ "$rc" = 0 ] || { tail -25 "$LOGS/host-tests.log"; die "host-tests.sh failed (log: $LOGS/host-tests.log)"; }
  fi

  step "Bundle"
  run bundle "$ROOT/gradlew" -p "$ROOT" :app:bundleRelease --console=plain
  [ "$DRY" = 1 ] || echo "  $(du -h "$AAB" | cut -f1) $AAB"

  step "Upload to Play internal"
  notes=$(top_entry_notes)
  run upload "$ROOT/scripts/play-upload.py" "$AAB" --name "$name ($code)" --lang en-CA --notes "$notes"
  [ "$DRY" = 1 ] || tail -1 "$LOGS/upload.log" | sed 's/^/  /'

  step "Commit and push main"
  if [ "$DRY" = 1 ]; then echo "  (dry run) commit: $name (build $code, internal): $summary"
  else
    git -C "$ROOT" add -u
    git -C "$ROOT" commit -q -m "$name (build $code, internal): $summary" -m "$CO_AUTHOR"
    run push git -C "$ROOT" push -q origin main
    echo "  $(git -C "$ROOT" log --oneline -1)"
  fi

  website "Website: PlyGround changelog with $name (build $code, internal)"
  echo "Done: $name (build $code) on internal."
  ;;

alpha)
  [ -n "$notes_file" ] && [ -f "$notes_file" ] || die "--notes-file <file> needed (en-CA, everything since the last alpha)"
  [ "$manual_done" = 1 ] || die "update the manual (MANUAL_BODY in $SITE/website/tools/make_pages.py), then pass --manual-done"
  notes=$(cat "$notes_file")
  [ "${#notes}" -le 500 ] || die "notes are ${#notes} characters; Play allows 500"
  check_repo
  name=$(cur_name); code=$(cur_code)
  head=$(top_heading)
  [[ $head == *"(build $code, internal test)" ]] || die "first CHANGELOG entry isn't build $code (internal test): $head"
  echo "Alpha release $name (build $code)"

  step "CHANGELOG: drop the internal-test marker"
  [ "$DRY" = 1 ] || sed -i "0,/(build $code, internal test)/s//(build $code)/" "$CHANGELOG"

  step "Promote build $code to Play alpha"
  run upload "$ROOT/scripts/play-upload.py" --code "$code" --track alpha --name "$name ($code)" --lang en-CA --notes "$notes"
  [ "$DRY" = 1 ] || tail -1 "$LOGS/upload.log" | sed 's/^/  /'

  step "Commit, tag v$name, push"
  if [ "$DRY" = 1 ]; then echo "  (dry run) commit + tag v$name + push main and tag"
  else
    git -C "$ROOT" add -u
    git -C "$ROOT" commit -q -m "$name (build $code) on alpha: changelog without the internal-test marker" -m "$CO_AUTHOR"
    git -C "$ROOT" tag -a "v$name" -m "$name (build $code)"
    run push git -C "$ROOT" push -q origin main "v$name"
    echo "  $(git -C "$ROOT" log --oneline -1)"
  fi

  website "Website: PlyGround $name (build $code) on alpha: changelog and manual"
  echo "Done: $name (build $code) on alpha (in Google's review)."
  ;;

*) sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'; exit 1 ;;
esac
