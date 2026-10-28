# PureTD engine

- `bg_engine.c`, `bg_engine.h`: PureTD's move generator and board encoding, copied unchanged from
  github.com/alexstrehl/backgammon-ai-engine at the commit in `PURETD_VERSION`
  (MIT licence, `LICENSE-PureTD`).
- `ptd.c`, `ptd.h`: our network evaluation, bear-off database and search, written for the app.
- Networks: `app/src/main/assets/puretd/*.ptw`, PureTD's weights at full precision (float32),
  exported from its checkpoints with `engine/tools/export_puretd.py`.

## What Bold Blot added to PureTD

PureTD comes with its networks, its move generator and its own C engine: network evaluation,
checker play up to 3-ply with a root move filter, money cube decisions up to 2-ply with its cubeful
network or a Janowski layer, and one- and two-sided bear-off tables (plies in XG numbering; PureTD
counts one less). It plays money games and 1-point matches; longer matches are planned there.

`ptd.c` is a separate implementation of the parts the app needs (PureTD's C library is not used).
What it and the app add:

- **4-ply search**, for checker play and cube decisions, spread over all cores.
- **Cube-aware money cube search**: cube decisions at every node of the tree, both sides deciding
  at every turn, PureTD's cubeful network at the leaves, moves inside the tree picked by the
  cubeless network.
- **Match play**, all of it:
  - our own match equity table and match Janowski (`core/CubeMath.kt`), computed from a model,
    not copied from another program; checker play in matches uses them to turn the network's
    probabilities into match equity;
  - a cube-aware match cube search (`ptd_cube_tree` here, `engine/MatchCubeSearch.kt` in the
    app): the money search's approach in match-winning chance, every cube state followed through
    the tree, our match Janowski at the leaves. It took PureTD's match cube error on FlashBack's
    match-score positions from 22.8 to 0.6 thousandths of a point at 3-ply;
  - too good to double: above the cash point the match cube model's value rises to the full win,
    gammons included, so a player too good to double plays on for the gammon.

Checked against Bold Blot's GPU research implementation: network values agree within 0.0024
equity (the research code runs in fp16), and 3-ply checker play and cube decisions give the same
errors on the FlashBack test positions.
