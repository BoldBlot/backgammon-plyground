# PureTD engine

- `bg_engine.c`, `bg_engine.h`: PureTD's move generator and board encoding, copied unchanged from
  github.com/alexstrehl/backgammon-ai-engine at the commit in `PURETD_VERSION`
  (MIT licence, `LICENSE-PureTD`).
- `ptd.c`, `ptd.h`: our network evaluation and search (1-ply and 3-ply checker play with the
  usual 5-move filter; cube-aware 3-ply money cube decisions with PureTD's cubeful network at the
  leaves, moves inside the tree picked by the cubeless network).
- Networks: `app/src/main/assets/puretd/*.ptw`, PureTD's weights at full precision (float32),
  exported from its checkpoints with `engine/tools/export_puretd.py`.

Checked against Bold Blot's GPU research implementation: network values agree within 0.0024
equity (the research code runs in fp16), and 3-ply checker play and cube decisions give the same
errors on the FlashBack test positions. The bear-off database is built by the engine on first
start (about a second) and cached in the app's files.
