#!/usr/bin/env bash
# Builds the PureTD engine and its JNI bridge for the host, then runs the app's JVM unit tests
# against the real engine.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
BUILD="${BUILD_DIR:-$ROOT/build/engine-host}"
JAVA_HOME="${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")}"
export JAVA_HOME

cmake -S "$ROOT/engine" -B "$BUILD" -G Ninja -DCMAKE_BUILD_TYPE=Release >/dev/null
cmake --build "$BUILD"
"$ROOT/gradlew" -p "$ROOT" :app:testDebugUnitTest -PbgHostLib="$BUILD/libptdjni.so" --console=plain
