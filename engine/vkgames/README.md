# vkgames: played-out games with the network on the GPU (Vulkan)

Experimental (0.9.21): T-Roller's short played-out games with PureTD's network evaluated on the GPU by a Vulkan 1.1 compute
shader, bit for bit the same as the CPU code (moves are generated and picked on the CPU threads, only the network rows go to
the GPU). Used only after the app's GPU check passed on the phone (Settings, Advanced engine settings).

- `mlp.comp`: one dense layer per dispatch; tile variants 88 / 48 / 84 / 44 (rows x outputs per thread); sums in ptd.c's
  order, plain multiply-add or `fma()` by a specialization constant. Compiled to SPIR-V into `mlp_spv.h` by `gen_spv.py`
  (glslc from the NDK: see the comment in gen_spv.py).
- `vk_mlp.c` / `vk_mlp.h`: Vulkan setup and the forward pass.
- `vkgames.c`: includes `../puretd/ptd.c`; `vkg_step` is one turn of many games (the 21 rolls' replies and the roll thrown).
- `vkg_selftest.c`: the GPU check (every shader setting against the CPU code, bit for bit, and its speed), and the JNI
  (`engine/VkGamesNative.kt`). The library `libvkgames.so` is built from this file.
- `include/`: the Khronos Vulkan headers (Apache-2.0) for hosts without them.

Exactness on arm64 needs PureTD's C code built with `-ffp-contract=off` (see engine/CMakeLists.txt): some GPUs' `fma()` is
not a true fused multiply-add.
