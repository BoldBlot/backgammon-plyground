"""Writes a PureTD checkpoint (.pt) as a .ptw file for engine/puretd/ptd.c.

  python export_puretd.py model.pt model.ptw

Format (little endian): "PTW1" (float32 weights) or "PTW2" (float16 weights, the default), int32 kind (0 prob5, 1 cubeful equity), int32 layer count,
int32 input size; per layer int32 outputs, float32 weights [out][in], float32 bias [out].
The checkpoint is read with weights_only=True (a downloaded file is never executed).
"""
import struct
import sys

import torch


def main(src, dst, precision="16"):
    ck = torch.load(src, map_location="cpu", weights_only=True)
    sd = ck["state_dict"]
    assert ck["activation"] == "relu"
    if ck.get("model_type") == "prob5":
        kind = 0
        names = [f"trunk.{i}" for i in sorted({int(k.split(".")[1]) for k in sd if k.startswith("trunk.")})] + ["head"]
    else:
        assert ck["output_mode"] == "equity" and ck["input_size"] == 200
        kind = 1
        names = [f"hidden_layers.{i}" for i in range(len(ck["hidden_sizes"]))] + ["fc_output"]
    with open(dst, "wb") as f:
        half = precision == "16"
        f.write((b"PTW2" if half else b"PTW1") + struct.pack("<3i", kind, len(names), ck["input_size"]))
        for n in names:
            w, b = sd[f"{n}.weight"].float().contiguous(), sd[f"{n}.bias"].float().contiguous()
            f.write(struct.pack("<i", w.shape[0]))
            f.write((w.half() if half else w).numpy().tobytes())
            f.write((b.half() if half else b).numpy().tobytes())
    print(f"{dst}: kind {kind}, layers {[sd[n + '.weight'].shape[0] for n in names]}")


if __name__ == "__main__":
    main(*sys.argv[1:])
