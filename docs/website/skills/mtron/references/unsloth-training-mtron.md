---
name: unsloth-training-mtron
description: Fine-tuning an LLM on mtron with Unsloth — verified training-data extraction from the test suite, Unsloth Studio, and Ollama deployment.
---


# Unsloth Training for mtron — End-to-End Guide

This file explains how to fine-tune an LLM on the mtron language — from training data extraction via test-suite
reflection to Ollama deployment, using Unsloth Studio. Tested on a 64GB machine with 2× RTX 3090 (48 GB VRAM).

---

## 1. Dataset Extraction

### Source

`src/test/java/studio/phaseshift/metatron/util/UnslothTrainingDatasetExtractor.java` reflects over the
JUnit 5 `@ParameterizedTest` + `@CsvSource` methods of 26 test classes. Each CSV row becomes one or more
entries; entry generation is delegated to `Training.Extractor.from()`.

### What a Row Claims

A corpus row claims exactly one thing:

> `input`, parsed as mtron and applied, yields `output`.

Nothing else is assumed. A test's intention is often not `a == b` — a serialization round trip, a rejected
write, a peer layout — so a method that is not an `(expression → value)` pair must be annotated with
`@Training.SkipTraining`. Every row is checked against this claim by the verifier (see **Verification**
below); rows that do not verify are excluded from the file you train on.

### Annotations

- **`@Training(instruction, input, output)`** — `@Repeatable`. Each field is a template: `{{{param}}}` is
  replaced by that column's value and `{{{@TestData}}}` by the method's preloaded `@TestData` lines.
  Repeat the annotation to emit several entries per row.
  - **`input` is mandatory.** A template with no `input` emits a row whose input is empty; empty parses to
    `noobj` and can never verify.
  - `input` must be the *one* expression the test evaluates. `checkCodeParseApply(LOG, lhs, code, expected)`
    evaluates `lhs.code`, so `input = "{{{lhs}}}.{{{code}}}"`.
  - A boolean assertion becomes `a.matches(b)` (that is exactly `bool(lhs.test(arg))`):
    `input = "{{{instance}}}.matches({{{type}}})"`, `output = "{{{matches}}}"`.
  - A type literal as receiver needs the `|` block so it is not applied:
    `input = "|{{{type}}}.matches({{{other}}})"`.
  - Setup-dependent rows prefix the setup with `;`, which sequences statements:
    `input = "{{{@TestData}}}; {{{code}}}"`. The model then sees the state the answer depends on.
  - Expected failure: `output = "<ERROR>"`.
- **`@Training.SkipTraining(reason)`** — contributes no entries. Use it for anything that is not an
  `expression → value` pair, and say why:
  `@Training.SkipTraining(reason = "the row asserts the tid after a serialization round trip")`.

### Fallback (unannotated methods)

An unannotated method falls back to a two-column `expression → result` mapping, with the instruction
chosen by a stable hash of `method#row`. A row whose field count does not match the method's parameter
count is **dropped and counted**, never silently misaligned.

The fallback cannot know which column is the asserted value, so an unannotated method with more than two
columns is almost always mislabeled — annotate it or skip it. `@Disabled` methods are skipped outright.

- **Operator context enrichment**: every instruction in a fallback expression is resolved via `?docq>>desc`
  from the VM to weave semantic descriptions into the instruction field.
- **Declarative reference knowledge**: 7 evaluated reference docs contribute one entry per `##` section,
  so the model can *explain* mtron, not just evaluate it.

### Run

```bash
bin/metatron-docker build test -Dtest=UnslothTrainingDatasetExtractorTest -Dmtron.training.regenerate=true
```

`-Dmtron.training.regenerate=true` rewrites the corpus and verifies it in the same JVM; omit it to verify
the corpus already on disk. The test extends `AbstractMetatronTest` and boots the VM.

### Output

All of these are build products and are gitignored (see `docs/website/.gitignore`). They live under
`.metatron/skills/mtron/assets/`, a symlink to `docs/website/skills/`:

| File | What it is |
|------|------------|
| `mtron_training_dataset.jsonl` | everything that extracted — for diagnosis, **not** for training |
| `mtron_training_dataset.manifest.json` | sha256, byte size, entry count, timestamp |
| `mtron_training_dataset.verification.txt` | per-row failures grouped by `source`, tab-separated |
| **`mtron_training_dataset.verified.jsonl`** | **only rows that verified — this is the training file** |
| `mtron_training_dataset.verified.manifest.json` | identity of the file above |

### Verification

`UnslothTrainingDatasetExtractorTest.verifyExtractedCorpus()` reads the corpus and, for each row, parses
`input`, applies it, and compares the result to `output` with the same rules as
`AbstractMetatronTest.checkCodeParseApplyRaw` — the `<ERROR>` sentinel, real tolerance, operation-tid
comparison for addresses, and `hasPoly` → `test()`. It reports verified / mismatched / evaluation-error /
not-an-expression counts, the **failing sources** (`Class.method`, worst first) as a worklist, and fails
the build with `-Dmtron.training.verify.strict=true` if any row does not verify.

Every row carries `source`. A row that cannot name the test it came from cannot be triaged or repaired.

### Known Limitations

- `?docq` documentation is still woven into the *instruction* of fallback rows. It is useful context but
  noisy (LaTeX, `start: [initial]` boilerplate); moving it to a separate `explain` task is planned.
- A row can verify yet still be poor training data — e.g. an answer that is a vid into transient space
  (`*/t/a`). Verification is necessary, not sufficient.

### Assistant Persona (Directive Disabled by Default)

The model is trained as a **general mtron/metatron assistant**, not an eval-only calculator. The
extractor's `instruction` directive (a system-style prefix prepended to every entry) therefore defaults
to **empty** — no "answer nothing else" / "do not think" text is baked into the data. The assistant
persona is supplied at inference time via the Modelfile/request system prompt instead. Override with
`-Dmtron.training.directive="..."` only for a dedicated eval model.

### Instruction vs Input

The expression belongs in `input`; the `instruction` is the operation in words ("what does the mtron
expression evaluate to?"). Putting the expression in both fields dilutes the signal, and putting it only
in `instruction` leaves `input` empty — a row that can never verify.

### Mixing General Data (Abandoned ❌)

An 80/20 mix of mtron + Alpaca was tried to mitigate catastrophic forgetting, but it **diluted mtron** —
best loss 0.405 vs 0.093 for the mtron-only run. The LoRA already touches only ~0.99% of weights, so base
general capability survives a mtron-only fine-tune anyway. Mixing is abandoned; train mtron-only.

### Dataset Format

Alpaca-style JSONL, plus `source` for provenance:

```json
{
  "instruction": "what does the mtron expression 1.plus(2) evaluate to?",
  "input": "1.plus(2)",
  "output": "3",
  "source": "IntTest.testCode"
}
```

### Entry Types

Measured on the current suite (counts drift as tests change):

| Type | Count | Source |
|------|------:|--------|
| Expression evaluation | ~2,670 | `@CsvSource` rows from the 26 targeted classes |
| — of which verify | ~2,270 | re-evaluated to their own `output` |
| Meta-knowledge + reference knowledge | ~200 | hardcoded facts; `##` sections of 7 evaluated docs |
| **Extracted total** | **~2,870** | before filtering |

### Key Architecture: `Training.java`

The `@Training` annotation lives at `src/test/java/studio/phaseshift/metatron/Training.java`:

- **`@Training(instruction, input, output)`** — `@Repeatable`; `{{{param}}}` holes are substituted by
  parameter name, and `{{{@TestData}}}` is the special accessor for the method's preloaded data.
- **`@Training.SkipTraining(reason)`** — `Extractor.from()` returns no entries for the method.
- **`record Entry`** — instruction, input, output and `sourceMethod`; `toJson()` serializes the three
  Alpaca fields plus `source`.
- **`final class Extractor`** — `from(Method, CsvSource)` returns `Extraction(entries, skipped)`;
  `skipped` counts rows whose field count did not match the method's parameter count.
- **`extractOperatorContext()`** — parses the expression, resolves instruction types via
  `resolveCode()` (Code chains) or `insts()` (single Inst), then reads each instruction's `?docq` `desc`
  and appends it to the fallback instruction.

### Serialization

Entries are serialized by Gson with HTML escaping **disabled** — mtron is full of `>`, `<` and `&`, and
the default would render an arrow as `-\u003e`. Escaping happens exactly once, at serialization; field
values must not be pre-escaped.

---

## 2. Model Selection

### Text-Only Models (✅ Verified Working)

| Model            | Params | Size (Q4_K_M) | Best Loss | Notes                               |
|------------------|--------|:-------------:|:---------:|-------------------------------------|
| `Qwen/Qwen3-4B`  | 4B     |    ~2.5 GB    |   0.27    | Fast, good for testing              |
| `Qwen/Qwen3-8B`  | 8B     |    ~5.0 GB    |   0.22    | First successful model              |
| `Qwen/Qwen3-14B` | 14B    |    ~8.4 GB    |   0.093   | mtron-only assistant (current best) |

### Vision-Language Models (Text-Only Strip)

Models with vision components (`image-text-to-text` pipeline) fail when trained *as* VLMs — template
parsing errors, confabulated output, or near-random loss (~14). They **do** load correctly in text-only
mode, however: pass `finetune_vision_layers=false` + `is_dataset_image=false` in Studio (or
`text_only=True` with the Unsloth Python `FastModel` API), which skips the vision/audio towers and loads
the pure language model. This is how `Qwen/Qwen3.8-27B` (27B, gated-deltanet hybrid) is trained.

### Reasoning / Thinking Mode ⚠️

`Qwen3` and `Qwen3.8` are *hybrid thinking* models: they can emit a `<think>…</think>` chain-of-thought
preamble before the answer, toggled by `/think` and `/no_think` control tokens (in Ollama, the `think`
request option). A 600-step fine-tune preserves this from the base model. Ollama 0.32 **defaults
`think=true` for thinking-capable models**, and metatron's LangChain4j client calls `/api/chat` without a
`think` flag — so a single tag cannot be "direct by default, thinking on demand" (unset and explicit
`think:true` are indistinguishable in the template). Two tags resolve this (§5):

- **`:assistant`** — direct: the template force-appends `/no_think` to the last user message and prefills
  an empty `<think></think>`, so every answer is a bare result. This is the tag metatron uses.
- **`:assistant-thinking`** — the stock thinking-aware template; emits a `<think>` preamble for interactive
  reasoning.

Do **not** bake a "no-thinking" directive into the training data — that produced an eval-only calculator,
not an assistant (see §1).

### Loss Interpretation

| Loss    | Meaning                          |
|---------|----------------------------------|
| 3.0–4.0 | Initial random guessing          |
| 1.0–2.0 | Learning syntax                  |
| 0.3–0.5 | Decent fluency (~70% confidence) |
| 0.2–0.3 | Strong recall (~85% confidence)  |
| <0.2    | Near-memorization                |

---

## 3. Training

### Hyperparameters (2× RTX 3090)

```json
{
  "model_name": "Qwen/Qwen3.8-27B",
  "model_snapshot_path": "/workspace/work/.hf/hub/models--Qwen--Qwen3.8-27B/snapshots/<hash>",
  "training_type": "LoRA/QLoRA",
  "load_in_4bit": true,
  "gpu_ids": [
    0,
    1
  ],
  "max_seq_length": 2048,
  "format_type": "alpaca",
  "learning_rate": "5e-5",
  "lr_scheduler_type": "cosine",
  "batch_size": 2,
  "gradient_accumulation_steps": 4,
  "max_steps": 600,
  "save_steps": 150,
  "warmup_steps": 10,
  "lora_r": 32,
  "lora_alpha": 8,
  "gradient_checkpointing": "unsloth",
  "optim": "adamw_8bit",
  "train_on_completions": true,
  "finetune_vision_layers": false,
  "finetune_language_layers": true,
  "is_dataset_image": false
}
```

For the 27B hybrid (gated-deltanet) architecture, Studio auto-installs the `causal-conv1d` kernel for
fast linear attention before loading — the message "Installing causal-conv1d for faster training…" is
expected, not an error.

Key notes:

- `cosine` > `linear` for runs >200 steps — avoids LR bottoming out early.
- `batch_size=2` + `gradient_accumulation_steps=4` = effective batch 8.
- 4-bit models cannot do data-parallel multi-GPU (bitsandbytes limitation). Dual GPU with
  `load_in_4bit: true` still helps by splitting the model across GPUs.
- Last-step loss spike is normal — cosine LR approaches near-zero at step 600.
- **Unload Ollama before training**: a resident `mtron-qwen3.8-27b` (~24 GB) in VRAM leaves the 27B train
  nowhere to place its weights — Studio fails with "Cannot place the model… slack after weights: -0.714 GiB".
  Run `ollama stop <model>` (or `keep_alive=0`) first, then retrain.
- **Train mtron-only** — do not mix general data (see §1); the LoRA's ~0.99% footprint preserves base
  general capability on its own.

---

## 4. Export Pipeline

### From Checkpoint (Unsloth Python API)

The Studio API `export/merged` + `export/gguf` endpoints can be unreliable for large models.
Use the Unsloth Python API directly from the checkpoint via `scripts/export_gguf.py` (copy it into the
container's `/workspace/work/` first):

```bash
scp docs/website/skills/mtron/scripts/export_gguf.py ginger.local:/opt/stacks/unsloth/work/export_gguf.py
ssh ginger.local "docker exec unsloth env \
  CHECKPOINT=/opt/unsloth-studio/outputs/<run>/checkpoint-600 \
  OUT=/workspace/work/mtron-qwen-14b-gguf \
  TEXT_ONLY=0 \
  python3 /workspace/work/export_gguf.py"
```

Set `TEXT_ONLY=1` for a VLM base (`Qwen3.8-*`); `save_pretrained_gguf` appends `_gguf` to `OUT`.

Notes:

- **`load_in_4bit=True` is required** — `False` dequantizes the base to bf16 (~54 GB for 27B) and OOMs ("We need an
  `offload_dir`… layers.61-63, lm_head"). The base is already bnb-4bit; keep it 4-bit.
- The GGUF is named after the model (`qwen3.8-27b.Q4_K_M.gguf`), and Unsloth skips the auto-Modelfile ("No Ollama
  template mapping found") — write the Modelfile manually (§5).

---

## 5. Ollama Deployment

### Copy GGUF Out

```bash
ssh ginger.local "docker cp unsloth:/workspace/studio/exports/mtron-qwen-XXb_gguf/<file>.gguf /tmp/mtron-qwen-XXb.Q4_K_M.gguf"
```

### Modelfile

Two tags share one GGUF; they differ only in the `TEMPLATE`. Both use the assistant `SYSTEM` prompt:

```dockerfile
FROM /opt/stacks/unsloth/work/mtron-qwen-14b-gguf_gguf/qwen3-14b.Q4_K_M.gguf

SYSTEM """You are an expert assistant for metatron, a distributed data-oriented computing language and
virtual machine. You are fluent in mtron, the functional programming language that runs on the metatron VM.
When asked to evaluate an mtron expression, give the correct result. When asked about mtron or metatron,
explain clearly and accurately. You are also a helpful general assistant."""
```

- **`:assistant`** (direct — what metatron uses): `scripts/Modelfile.mtron-qwen-14b-assistant`. The
  template force-appends `/no_think` to the last user message and prefills an empty `<think></think>`, so
  answers are bare (no preamble). Based on the stock Qwen3 template from
  `ollama show qwen3:latest --modelfile`.
- **`:assistant-thinking`** (reasoning preamble): `scripts/Modelfile.mtron-qwen-14b-assistant-thinking`.
  The stock thinking-aware template unmodified — emits a `<think>` preamble before the answer.

```bash
sudo systemctl start ollama
ollama create mtron-qwen-14b:assistant          -f scripts/Modelfile.mtron-qwen-14b-assistant
ollama create mtron-qwen-14b:assistant-thinking -f scripts/Modelfile.mtron-qwen-14b-assistant-thinking
```

### System Info

Ollama runs via systemd on ginger.local as user `ollama`, models stored at
`/usr/share/ollama/.ollama/models/`. Use `sudo systemctl restart ollama` after
manually copying blob files. Kill rogue user-level instances with `pkill ollama`
if port 11434 is conflicting.

### Extending Context Window

Qwen3 models natively support 32K context but can be extended with RoPE scaling (YaRN) to 128K+
via Ollama's `num_ctx` parameter. Create a new tag rather than overwriting the original:

```bash
# Show current model (check baked-in context length vs num_ctx)
ollama show mtron-qwen-14b:latest

# Dump Modelfile, append num_ctx, create new tag
ollama show mtron-qwen-14b:latest --modelfile > /tmp/Modelfile-128k
echo 'PARAMETER num_ctx 131072' >> /tmp/Modelfile-128k
ollama create mtron-qwen-14b:128k -f /tmp/Modelfile-128k
```

`ollama show` will still display the native GGUF `context length` (e.g. 40960), but
`num_ctx` in the Parameters section is what Ollama actually uses at runtime.
The `OLLAMA_NUM_CTX` environment variable sets a system-wide default but per-model
`num_ctx` takes precedence.

To use the extended context without a rebuild, pass `num_ctx` per-request:

```bash
curl http://ginger.local:11434/api/generate -d '{
  "model": "mtron-qwen-14b:latest",
  "prompt": "...",
  "options": {"num_ctx": 131072}
}'
```

---

## 6. HuggingFace Deployment

Models are published under `phaseshift-studio/mtron-qwen`:

| File                         | Size    | Description                |
|------------------------------|---------|----------------------------|
| `mtron-qwen-4b.Q4_K_M.gguf`  | ~2.5 GB | Qwen3-4B fine-tune         |
| `mtron-qwen-8b.Q4_K_M.gguf`  | ~5.0 GB | Qwen3-8B fine-tune         |
| `mtron-qwen-14b.Q4_K_M.gguf` | ~8.4 GB | Qwen3-14B fine-tune        |
| `training-*.png`             | ~70 KB  | Training plots per variant |

Upload with:

```bash
HF_TOKEN=*** hf upload phaseshift-studio/mtron-qwen \
  /tmp/mtron-qwen-XXb.Q4_K_M.gguf mtron-qwen-XXb.Q4_K_M.gguf \
  /tmp/training-XXb.png training-XXb.png
```

The `hf` CLI handles LFS >5GB transparently. Web UI uploads silently cap at 5GB.

---

## 7. Training History

> ⚠️ Every run below predates corpus verification. The losses are **training** loss on a corpus that was
> never checked, and "best loss" is a minimum read off a noisy curve — they are not comparable across runs
> and not evidence of quality. Re-baseline against a verified corpus before drawing conclusions.

| Variant | Base Model                              | Steps | Best Loss | Final Loss | Time    |              Verified               |
|---------|-----------------------------------------|:-----:|:---------:|:----------:|---------|:-----------------------------------:|
| 4B      | Qwen3-4B (text-only)                    |  600  |   0.27    |    0.81    | ~15 min |                 🆕                  |
| 8B      | Qwen3-8B (text-only)                    |  600  |   0.22    |     —      | ~20 min |                 ✅                  |
| 14B     | Qwen3-14B (text-only, v2)               |  600  |   0.23    |    0.69    | 31 min  |                 ✅                  |
| 2B ❌   | Qwen3.5-2B (multimodal)                 |  600  |   0.19    |    0.23    | —       |            Confabulates             |
| 27B ❌  | Qwen3.6-27B (vision-language, *as VLM*) |  600  |   0.25    |    0.50    | —       |          Template failures          |
| 27B ⚠️  | Qwen3.8-27B (text-only strip)           |  600  |   0.12    |    0.50    | ~50 min |       Rambles (thinking mode)       |
| 27B ❌  | Qwen3.8-27B (text-only + 80/20 mix)     |  600  |   0.405   |     —      | —       |          Mix diluted mtron          |
| 14B ✅  | Qwen3-14B (mtron-only, latest dataset)  |  600  |   0.093   |   0.550    | ~32 min | Deployed `mtron-qwen-14b:assistant` |

---

## 8. Quick-Start

```bash
# 1. Regenerate and verify the corpus (writes both files; see §1)
bin/metatron-docker build test -Dtest=UnslothTrainingDatasetExtractorTest -Dmtron.training.regenerate=true
#    -> .metatron/skills/mtron/assets/mtron_training_dataset.verified.jsonl   (train on this)
#    -> .metatron/skills/mtron/assets/mtron_training_dataset.verification.txt (worklist of failures)

# 2. Authenticate to Studio
curl -s -X POST http://ginger.local:8882/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"unsloth","password":"<pw>"}' \
  | python3 -c "import json,sys; print(json.load(sys.stdin)['access_token'])" > /tmp/ustok.txt

# 3. Upload the VERIFIED corpus — capture the returned UUID-prefixed filename; use it in `local_datasets`
TOKEN=$(cat /tmp/ustok.txt)
curl -X POST http://ginger.local:8882/api/datasets/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@.metatron/skills/mtron/assets/mtron_training_dataset.verified.jsonl"
# -> {"filename":"mtron_training_dataset.verified.jsonl","stored_path":".../uploads/<uuid>_mtron_training_dataset.verified.jsonl"}

# 4. Start training — text-only via finetune_vision_layers=false; point at the local model snapshot
curl -X POST http://ginger.local:8882/api/train/start \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"model_name":"Qwen/Qwen3.8-27B","model_snapshot_path":"/workspace/work/.hf/hub/models--Qwen--Qwen3.8-27B/snapshots/<hash>","training_type":"LoRA/QLoRA","load_in_4bit":true,"gpu_ids":[0,1],"format_type":"alpaca","local_datasets":["<uuid>_mtron_training_dataset.jsonl"],"learning_rate":"5e-5","lr_scheduler_type":"cosine","batch_size":2,"gradient_accumulation_steps":4,"max_steps":600,"save_steps":150,"warmup_steps":10,"lora_r":32,"lora_alpha":8,"gradient_checkpointing":"unsloth","optim":"adamw_8bit","train_on_completions":true,"finetune_vision_layers":false,"finetune_language_layers":true,"is_dataset_image":false}'

# 5. Export GGUF (scripts/export_gguf.py, copied into /workspace/work)
scp docs/website/skills/mtron/scripts/export_gguf.py ginger.local:/opt/stacks/unsloth/work/export_gguf.py
ssh ginger.local "docker exec unsloth env CHECKPOINT=/opt/unsloth-studio/outputs/<run>/checkpoint-600 \
  OUT=/workspace/work/mtron-qwen-14b-gguf python3 /workspace/work/export_gguf.py"

# 6. Deploy to Ollama
ssh ginger.local "docker cp unsloth:/workspace/studio/exports/mtron-gguf/<file>.gguf /tmp/m.gguf"
sudo systemctl start ollama
ollama create mtron -f Modelfile
```

## Qwen3 Fine-Tuning for Custom Programming Languages (advice from Brave agent)

### Symptoms & Root Causes

| Symptom                                | Likely Cause                                             |
|----------------------------------------|----------------------------------------------------------|
| Outputs pure garbage / random tokens   | LR too high, or `embed_tokens` not in `modules_to_save`  |
| Repeats the same phrase endlessly      | LR too high (model forgot EOS), or too many epochs       |
| Can generate but ignores your language | LR too low, or LoRA rank too low                         |
| Works for 2–3 lines then degrades      | Context length too short during training, or overfitting |

### Hyperparameter Recommendations

| Parameter          | Value                                                           | Notes                                                                       |
|--------------------|-----------------------------------------------------------------|-----------------------------------------------------------------------------|
| `learning_rate`    | `2e-5` (or `1e-5`)                                              | **Critical.** Unsloth default `2e-4` is too aggressive for new token spaces |
| `num_train_epochs` | `1` (max `3`)                                                   | More than 3 = overfitting = loops                                           |
| `lora_r`           | `16`                                                            | Rank 8 works for 8B; 16 for 14B                                             |
| `lora_alpha`       | `16`                                                            | Must equal `lora_r`                                                         |
| `lora_dropout`     | `0`                                                             | —                                                                           |
| `target_modules`   | `q_proj, k_proj, v_proj, o_proj, gate_proj, up_proj, down_proj` | —                                                                           |
| `modules_to_save`  | `["lm_head", "embed_tokens"]`                                   | **Mandatory** when extending vocab                                          |
| General data mix   | `10–20%` of training set                                        | English prose / general code as regularizer                                 |

### Vocabulary Extension Procedure

Qwen uses byte-level BPE (151,669 tokens). You **cannot** just append tokens to the vocab file.

1. Prepare a plain-text file: one token per line as `<token>\t<frequency>`
2. Run Qwen's `add_merges.py` script with the base `qwen.tiktoken` + your extra vocab file
3. New tokens get indices starting at **151,851** by default
4. **Resize the embedding layer** in the model to match the new vocab size
5. Fine-tune — new embedding rows start random; the model learns their meaning during training

> ⚠️ **Known bug:** `Qwen3-8B`'s `config.json` reports `vocab_size: 151936` but the tokenizer reports `151669`. Fix
> before resizing embeddings or you'll hit shape mismatch errors.

### Recommended Stack

- **Framework:** Unsloth (cuts VRAM ~50% vs vanilla Transformers + PEFT)
- **Base model:** Use the **base** (non-instruct) checkpoint
- **Quantization:** 4-bit QLoRA for 8B/14B; BF16 only if you have ≥70 GB VRAM

### VRAM Budget (2× RTX 3090, 48 GB total)

| Model       | Precision   | VRAM @ batch 4 / 2048 ctx                   | Fits?                |
|-------------|-------------|---------------------------------------------|----------------------|
| Qwen3-8B    | 4-bit QLoRA | ~16 GB                                      | Single 3090          |
| Qwen3-14B   | 4-bit QLoRA | ~24 GB                                      | Single 3090          |
| Qwen3-8B    | BF16 LoRA   | ~20 GB                                      | Single 3090          |
| Qwen3.8-27B | 4-bit QLoRA | **Broken** (DeltaNet `in_proj_z` shape bug) | ❌                   |
| Qwen3.8-27B | BF16        | ~70 GB                                      | ❌ (needs H100 80GB) |

### Debugging Checklist

When a trained model "can't talk" or "goes in loops":

1. Open the training script — check `learning_rate`, `num_train_epochs`, `modules_to_save`, `lora_r` / `lora_alpha`
2. Verify `embed_tokens` and `lm_head` are in `modules_to_save`
3. Confirm `lora_alpha == lora_r`
4. Check whether general data was mixed in
5. Re-run with: `lr=2e-5`, `epochs=1`, `r=16`, `alpha=16`, `modules_to_save=["lm_head", "embed_tokens"]`, 15% general
   data