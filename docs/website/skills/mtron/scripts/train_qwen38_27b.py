#!/usr/bin/env python3
"""
Fine-tune Qwen/Qwen3.8-27B (text-only) on the mtron language with Unsloth QLoRA.

The base model is a vision-language model (Qwen3_5ForConditionalGeneration) with
a hybrid text backbone (gated-deltanet linear attention + gated attention + FFN).
We load it TEXT-ONLY (skip the vision tower via Unsloth's `text_only=True`) and
SFT on the alpaca-format mtron dataset.

Requirements / gotchas:
  * bf16 is REQUIRED -- the gated-deltanet layers produce NaN grad norms under fp16.
  * target_modules="all-linear" because the hybrid layer names are non-standard.

Env knobs (all optional):
  BITS=4|8          quantization (default 4)
  MODEL             HF repo (default Qwen/Qwen3.8-27B)
  DATASET           jsonl path (default /workspace/work/mtron_training_dataset.jsonl)
  OUT               output prefix (default /workspace/work/mtron-qwen3.8-27b)
  MAX_SEQ / MAX_STEPS / LR / BATCH / GRAD_ACCUM

Run inside the unsloth container, e.g.:
  HF_HOME=/workspace/work/.hf BITS=4 python3 /workspace/work/train_qwen38_27b.py
"""
import os

import torch

# ----------------------------------------------------------------------------
# Config
# ----------------------------------------------------------------------------
BITS = int(os.environ.get("BITS", "4"))  # 4 or 8
MODEL = os.environ.get("MODEL", "Qwen/Qwen3.8-27B")
DATASET = os.environ.get("DATASET", "/workspace/work/mtron_training_dataset.jsonl")
OUT = os.environ.get("OUT", "/workspace/work/mtron-qwen3.8-27b")
MAX_SEQ = int(os.environ.get("MAX_SEQ", "2048"))
MAX_STEPS = int(os.environ.get("MAX_STEPS", "600"))
LR = float(os.environ.get("LR", "5e-5"))
BATCH = int(os.environ.get("BATCH", "2" if BITS == 4 else "1"))
GRAD_ACCUM = int(os.environ.get("GRAD_ACCUM", "4"))

assert BITS in (4, 8), "BITS must be 4 or 8"

from unsloth import FastModel  # noqa: E402
from datasets import load_dataset  # noqa: E402
from trl import SFTTrainer, SFTConfig  # noqa: E402

# ----------------------------------------------------------------------------
# Load base model (text-only)
# ----------------------------------------------------------------------------
load_kwargs = dict(
    max_seq_length=MAX_SEQ,
    text_only=True,  # skip the vision tower
    dtype=None,  # Unsloth auto-selects bf16 on this GPU
    device_map="auto",
    use_cache=False,
)
if BITS == 8:
    load_kwargs["load_in_8bit"] = True
else:
    load_kwargs["load_in_4bit"] = True

print(f"[load] {MODEL}  text_only=True  BITS={BITS}  max_seq={MAX_SEQ}")
model, tokenizer = FastModel.from_pretrained(MODEL, **load_kwargs)

# ----------------------------------------------------------------------------
# LoRA adapters
# ----------------------------------------------------------------------------
model = FastModel.get_peft_model(
    model,
    r=32,
    lora_alpha=32,
    target_modules="all-linear",  # hybrid arch -> cover every linear layer
    lora_dropout=0,
    bias="none",
    use_gradient_checkpointing="unsloth",
    random_state=3407,
)

# ----------------------------------------------------------------------------
# Chat template (Qwen text-only) + dataset formatting
# ----------------------------------------------------------------------------
SYSTEM = (
    "You are an expert on mtron, the functional programming language of the "
    "metatron VM. Answer each mtron question with exactly the correct result and "
    "nothing else."
)


def format_chat(example: dict) -> str:
    instruction = (example.get("instruction") or "").strip()
    inp = (example.get("input") or "").strip()
    out = (example.get("output") or "").strip()
    user = instruction + ("\n" + inp if inp else "")
    return (
        f"<|im_start|>system\n{SYSTEM}<|im_end|>\n"
        f"<|im_start|>user\n{user}<|im_end|>\n"
        f"<|im_start|>assistant\n{out}<|im_end|>"
    )


# ----------------------------------------------------------------------------
# Dataset
# ----------------------------------------------------------------------------
ds = load_dataset("json", data_files=DATASET, split="train")
print(f"[data] {len(ds)} examples from {DATASET}")

# ----------------------------------------------------------------------------
# Trainer
# ----------------------------------------------------------------------------
trainer = SFTTrainer(
    model=model,
    tokenizer=tokenizer,
    train_dataset=ds,
    formatting_func=format_chat,
    args=SFTConfig(
        output_dir=f"{OUT}-ckpt",
        per_device_train_batch_size=BATCH,
        gradient_accumulation_steps=GRAD_ACCUM,
        warmup_steps=10,
        max_steps=MAX_STEPS,
        learning_rate=LR,
        lr_scheduler_type="cosine",
        fp16=False,
        bf16=True,  # required for gated-deltanet
        logging_steps=5,
        save_steps=150,
        save_total_limit=2,
        seed=3407,
        optim="adamw_8bit",
        report_to="none",
        max_seq_length=MAX_SEQ,
        dataset_num_proc=2,
        dataloader_num_workers=0,
    ),
)

print(f"[train] steps={MAX_STEPS} lr={LR} batch={BATCH} accum={GRAD_ACCUM}")
trainer.train()
print("[train] done")

# ----------------------------------------------------------------------------
# Save LoRA adapter + merged 16-bit + GGUF
# ----------------------------------------------------------------------------
model.save_pretrained(OUT)
tokenizer.save_pretrained(OUT)
print(f"[save] adapter -> {OUT}")

model.save_pretrained_merged(f"{OUT}-merged", tokenizer, save_method="merged_16bit")
print(f"[save] merged 16-bit -> {OUT}-merged")

try:
    model.save_pretrained_gguf(
        f"{OUT}-gguf", tokenizer, quantization_method="q4_k_m"
    )
    print(f"[save] gguf -> {OUT}-gguf")
except Exception as exc:  # noqa: BLE001 - GGUF is best-effort
    print(f"[save] gguf SKIPPED: {exc}")

print("[done]")
