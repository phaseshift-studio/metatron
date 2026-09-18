---
name: unsloth-training-mtron
description: Fine-tuning an LLM on mtron with Unsloth — training-data extraction from tests, Unsloth Studio, and Ollama deployment.
---

# Unsloth Training for mtron — End-to-End Guide

This file explains how to fine-tune an LLM on the mtron language — from training data extraction via test-suite
reflection to Ollama deployment, using Unsloth Studio. Tested on a 64GB machine with 2× RTX 3090 (48 GB VRAM).

---

## 1. Dataset Extraction

### Source

`src/test/java/studio/phaseshift/metatron/util/UnslothTrainingDatasetExtractor.java`

Scans JUnit 5 `@ParameterizedTest` + `@CsvSource` methods across ~25 test classes. Each CSV row becomes one
or more training entries. Entry generation is delegated to `Training.Extractor.from()` which handles:

- **Annotated methods** (`@Training`): `instruction`/`input`/`output` are templates whose `{{{param}}}`
  holes are substituted with the matching column's row value (any other `{{{...}}}` is left verbatim).
  Repeat the annotation to emit multiple entries per row.
- **Fallback methods** (no `@Training`): classic two-column `expression → result` with 10 rotating
  instruction templates.
- **Operator context enrichment**: every instruction in the expression is resolved via `?docq>>desc`
  from the metatron VM to weave semantic descriptions into the instruction field.
- **Declarative reference knowledge**: the extractor also scans 7 evaluated reference docs
  (`language-reference`, `type-system`, the `tble`/`math`/`sys`/`web` instset docs, and `SKILL.md`),
  emitting one "verbal knowledge" entry per `##` section — so the model can *explain* mtron, not just
  evaluate it.

### Instruction Templates

The fallback rotates through 10 templates to prevent the model from pattern-matching on prefix:

```
"evaluate: %s"     "what does %s yield?"   "compute: %s"
"%s = ?"           "the result of %s is:"   "solve: %s"
"evaluate %s:"     "what is %s?"            "compute %s ="
"%s evaluates to:"
```

### Run

```bash
cd metatron
./mvnw test -Dtest=UnslothTrainingDatasetExtractorTest
```

The test extends `AbstractMetatronTest` and boots the VM — required for `?docq` resolution.

### Output

`.metatron/skills/mtron/assets/mtron_training_dataset.jsonl` (~3,000 entries)

### Assistant Persona (Directive Disabled by Default)

The model is trained as a **general mtron/metatron assistant**, not an eval-only calculator. The
extractor's `instruction` directive (a system-style prefix prepended to every entry) therefore defaults
to **empty** — no "answer nothing else" / "do not think" text is baked into the data. The assistant
persona is supplied at inference time via the Modelfile/request system prompt instead. Override with
`-Dmtron.training.directive="..."` only for a dedicated eval model.

### Input-Strip Rule

When a method's `instruction` template already carries the full expression, don't duplicate it in
`input` — keep `input` only when it reconstructs something the `instruction` omits (e.g.
`input="{{{lhs}}}.{{{chain}}}"`). Redundant `input` fields dilute the training signal by repeating the
expression twice in one entry.

### Mixing General Data (Abandoned ❌)

An 80/20 mix of mtron + Alpaca was tried to mitigate catastrophic forgetting, but it **diluted mtron** —
best loss 0.405 vs 0.093 for the mtron-only run. The LoRA already touches only ~0.99% of weights, so base
general capability survives a mtron-only fine-tune anyway. Mixing is abandoned; train mtron-only.

### Dataset Format

Alpaca-style JSONL:

```json
{
  "instruction": "evaluate: 1.plus(2) (plus: add the argument int to the lhs int)",
  "input": "1.plus(2)",
  "output": "3"
}
```

### Entry Types

| Type | Count | Source |
|------|-------|--------|
| Expression evaluation | ~2,800 | `@CsvSource` rows from test classes |
| Meta-knowledge | ~50 | Hardcoded facts about mtron |
| Sugar reference + pairs | ~75 | Auto-generated from `mInstSet.sugars()` |
| Declarative reference knowledge | ~100 | One entry per `##` section of 7 reference docs (evaluated) |

### Key Architecture: `Training.java`

The `@Training` annotation lives at `src/test/java/studio/phaseshift/metatron/Training.java`. It contains:

- **`@Training(instruction, input, output)`** — `@Repeatable`; each field is a template whose `{{{param}}}`
  holes are substituted with the matching column's value. A hole substitutes when its content is an
  exact match for a method parameter name (or the special `{{{@TestData}}}` accessor — see below);
  anything else (e.g. an mtron `{{{expr}}}` template) is emitted verbatim. Example:
  `@Training(instruction = "when the {{{rec}}} rec is rshifted by the {{{key}}} key, what is the result?", input = "{{{rec}}}>>{{{key}}}", output = "{{{value}}}")`.
- **`@Training.SkipTraining(reason)`** — nested annotation. When present on a method, `Extractor.from()`
  short-circuits to `List.of()` before anything else, so the method contributes **no** entries. Use it for
  tests that don't map cleanly to an `expression → result` pair, and put the reason in `reason()`:
  `@Training.SkipTraining(reason = "too complicated to express easily")`.
- **`record Entry`** — the data carrier (instruction, input, output, sourceMethod) with `toJson()`.
- **`final class Extractor`** — `from(Method, CsvSource)` renders the templates via `render()` and
  produces `List<Entry>` for both annotated and fallback methods; `paramNameToColumn()` maps parameter
  names to column indices (requires the `-parameters` compiler flag in `pom.xml`).
- **`{{{@TestData}}}` accessor** — a special template hole (`Training.TEST_DATA_ACCESSOR`). When the
  method carries `@TestData`, `{{{@TestData}}}` is replaced by the annotation's `value()` strings joined
  with newlines, so an entry can cite the preloaded test data as setup context:
  `@Training(instruction = "given {{{@TestData}}}, what is the result of the mtron expression {{{code}}}?", output = "{{{expected}}}")`.
  Rendering throws `MTronException("attempting to access non-existent @TestData")` if the method has no
  `@TestData`.
- **`extractOperatorContext()`** — parses the expression, resolves instruction types via
  `resolveCode()` (Code chains) or direct `insts()` (single Inst), then queries
  `Router.readFromSpace(inst.tid().addQ("docq"))` for each instruction's `desc` field.

### Dataset Sanitization

The `?docq` descriptions may contain raw tabs or backslashes. These must be escaped for JSON:

```java
private static String escapeJson(String s) {
    return "\"" + s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t") + "\"";
}
```

---

## 2. Model Selection

### Text-Only Models (✅ Verified Working)

| Model | Params | Size (Q4_K_M) | Best Loss | Notes |
|-------|--------|:---:|:---:|-------|
| `Qwen/Qwen3-4B` | 4B | ~2.5 GB | 0.27 | Fast, good for testing |
| `Qwen/Qwen3-8B` | 8B | ~5.0 GB | 0.22 | First successful model |
| `Qwen/Qwen3-14B` | 14B | ~8.4 GB | 0.093 | mtron-only assistant (current best) |

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

| Loss | Meaning |
|------|---------|
| 3.0–4.0 | Initial random guessing |
| 1.0–2.0 | Learning syntax |
| 0.3–0.5 | Decent fluency (~70% confidence) |
| 0.2–0.3 | Strong recall (~85% confidence) |
| <0.2 | Near-memorization |

---

## 3. Training

### Hyperparameters (2× RTX 3090)

```json
{
  "model_name": "Qwen/Qwen3.8-27B",
  "model_snapshot_path": "/workspace/work/.hf/hub/models--Qwen--Qwen3.8-27B/snapshots/<hash>",
  "training_type": "LoRA/QLoRA",
  "load_in_4bit": true,
  "gpu_ids": [0, 1],
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
- **`load_in_4bit=True` is required** — `False` dequantizes the base to bf16 (~54 GB for 27B) and OOMs
  ("We need an `offload_dir`… layers.61-63, lm_head"). The base is already bnb-4bit; keep it 4-bit.
- The GGUF is named after the model (`qwen3.8-27b.Q4_K_M.gguf`), and Unsloth skips the auto-Modelfile
  ("No Ollama template mapping found") — write the Modelfile manually (§5).

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

| File | Size | Description |
|------|------|-------------|
| `mtron-qwen-4b.Q4_K_M.gguf` | ~2.5 GB | Qwen3-4B fine-tune |
| `mtron-qwen-8b.Q4_K_M.gguf` | ~5.0 GB | Qwen3-8B fine-tune |
| `mtron-qwen-14b.Q4_K_M.gguf` | ~8.4 GB | Qwen3-14B fine-tune |
| `training-*.png` | ~70 KB | Training plots per variant |

Upload with:

```bash
HF_TOKEN=*** hf upload phaseshift-studio/mtron-qwen \
  /tmp/mtron-qwen-XXb.Q4_K_M.gguf mtron-qwen-XXb.Q4_K_M.gguf \
  /tmp/training-XXb.png training-XXb.png
```

The `hf` CLI handles LFS >5GB transparently. Web UI uploads silently cap at 5GB.

---

## 7. Training History

| Variant | Base Model | Steps | Best Loss | Final Loss | Time | Verified |
|---------|-----------|:---:|:---:|:---:|------|:---:|
| 4B | Qwen3-4B (text-only) | 600 | 0.27 | 0.81 | ~15 min | 🆕 |
| 8B | Qwen3-8B (text-only) | 600 | 0.22 | — | ~20 min | ✅ |
| 14B | Qwen3-14B (text-only, v2) | 600 | 0.23 | 0.69 | 31 min | ✅ |
| 2B ❌ | Qwen3.5-2B (multimodal) | 600 | 0.19 | 0.23 | — | Confabulates |
| 27B ❌ | Qwen3.6-27B (vision-language, *as VLM*) | 600 | 0.25 | 0.50 | — | Template failures |
| 27B ⚠️ | Qwen3.8-27B (text-only strip) | 600 | 0.12 | 0.50 | ~50 min | Rambles (thinking mode) |
| 27B ❌ | Qwen3.8-27B (text-only + 80/20 mix) | 600 | 0.405 | — | — | Mix diluted mtron |
| 14B ✅ | Qwen3-14B (mtron-only, latest dataset) | 600 | 0.093 | 0.550 | ~32 min | Deployed `mtron-qwen-14b:assistant` |

---

## 8. Quick-Start

```bash
# 1. Regenerate dataset
cd metatron
./mvnw test -Dtest=UnslothTrainingDatasetExtractorTest

# 2. Authenticate to Studio
curl -s -X POST http://ginger.local:8882/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"unsloth","password":"<pw>"}' \
  | python3 -c "import json,sys; print(json.load(sys.stdin)['access_token'])" > /tmp/ustok.txt

# 3. Upload dataset — capture the returned UUID-prefixed filename; use it in `local_datasets` below
TOKEN=$(cat /tmp/ustok.txt)
curl -X POST http://ginger.local:8882/api/datasets/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@.metatron/skills/mtron/assets/mtron_training_dataset.jsonl"
# -> {"filename":"mtron_training_dataset.jsonl","stored_path":".../uploads/<uuid>_mtron_training_dataset.jsonl"}

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