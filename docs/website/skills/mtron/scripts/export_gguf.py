#!/usr/bin/env python3
"""Export an Unsloth LoRA checkpoint to a GGUF for Ollama.

Run inside the unsloth container, e.g.:
  docker exec unsloth env CHECKPOINT=/opt/unsloth-studio/outputs/<run>/checkpoint-600 \
        OUT=/workspace/work/mtron-qwen-14b-gguf TEXT_ONLY=0 \
        python3 /workspace/work/export_gguf.py

Env:
  CHECKPOINT  checkpoint-* directory (required)
  OUT         GGUF output dir — save_pretrained_gguf appends `_gguf` (default: /workspace/work/mtron-gguf)
  TEXT_ONLY   1 to skip vision/audio towers (VLM bases like Qwen3.8-*)
  QUANT       quantization (default q4_k_m)
"""
import os

from unsloth import FastModel

CHECKPOINT = os.environ["CHECKPOINT"]
OUT = os.environ.get("OUT", "/workspace/work/mtron-gguf")
TEXT_ONLY = os.environ.get("TEXT_ONLY", "0") == "1"
QUANT = os.environ.get("QUANT", "q4_k_m")

print(f"[1/2] loading checkpoint: {CHECKPOINT} (text_only={TEXT_ONLY})")
model, tokenizer = FastModel.from_pretrained(
    CHECKPOINT,
    text_only=TEXT_ONLY,   # skip vision/audio towers for VLM bases (Qwen3.8-*)
    load_in_4bit=True,     # base is already bnb-4bit; False dequantizes to bf16 and OOMs
    max_seq_length=2048,
    device_map="auto",
)

print(f"[2/2] saving GGUF ({QUANT}) -> {OUT}")
model.save_pretrained_gguf(OUT, tokenizer, quantization_method=QUANT)
print("GGUF EXPORT DONE")
