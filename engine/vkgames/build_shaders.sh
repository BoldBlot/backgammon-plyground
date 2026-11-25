#!/usr/bin/env bash
# Regenerates mlp_spv.h from mlp.comp (only needed when the shader changes): one SPIR-V variant per tile size.
set -euo pipefail
cd "$(dirname "$0")"
NDK=${NDK:-$HOME/Android/Sdk/ndk/29.0.14206865}
GLSLC=$NDK/shader-tools/linux-x86_64/glslc
VARIANTS="8 8 4 8 8 4 4 4"
mkdir -p out
set -- $VARIANTS
while [ $# -gt 0 ]; do $GLSLC -O --target-env=vulkan1.1 -DTM=$1 -DTN=$2 mlp.comp -o out/mlp_$1$2.spv; shift 2; done
python3 -I gen_spv.py $VARIANTS
