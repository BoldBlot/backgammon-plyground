#!/usr/bin/env bash
# Builds the engines and their JNI bridge for the host, then runs the app's JVM unit tests
# against the real engine.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
BUILD="${BUILD_DIR:-$ROOT/build/engine-host}"
JAVA_HOME="${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")}"
export JAVA_HOME

cmake -S "$ROOT/engine" -B "$BUILD" -G Ninja -DCMAKE_BUILD_TYPE=Release >/dev/null
cmake --build "$BUILD"
# The GPU parity tests run when GPU_TESTS=1, or when the GPU code, the C engine or the games search changed since the
# last release (or there is none); otherwise they are skipped (about 30 s).
GPU="${GPU_TESTS:-}"
if [ -z "$GPU" ]; then
  GPU=1
  # The last release (release.sh commits "0.9.23 (build 104, internal): ..."), else the last tag.
  TAG=$(git -C "$ROOT" log -1 --format=%h -E --grep='^[0-9]+\.[0-9]+\.[0-9]+ \(build [0-9]+, ' 2>/dev/null)
  [ -n "$TAG" ] || TAG=$(git -C "$ROOT" describe --tags --abbrev=0 2>/dev/null) || TAG=
  if [ -n "$TAG" ]; then
    if git -C "$ROOT" diff --quiet "$TAG" -- engine/vkgames engine/puretd app/src/main/cpp \
        app/src/main/java/com/boldblot/backgammonplyground/engine/GpuGames.kt \
        app/src/main/java/com/boldblot/backgammonplyground/engine/VkGamesNative.kt \
        app/src/main/java/com/boldblot/backgammonplyground/engine/GamesSearch.kt; then GPU=0; fi
  fi
fi
if [ "$GPU" = 1 ]; then echo "GPU parity tests: on"; else echo "GPU parity tests: off (no GPU or engine change since ${TAG:-the last tag})"; fi
"$ROOT/gradlew" -p "$ROOT" :app:testDebugUnitTest -PbgHostLib="$BUILD/libbgjni.so" -PbgGpuTests=$([ "$GPU" = 1 ] && echo true || echo false) --console=plain
