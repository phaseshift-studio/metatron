# Unsloth Training for mtron — End-to-End Guide

This file explains how to fine-tune an LLM on the mtron language — from training data extraction via test-suite
reflection to Ollama deployment, using Unsloth Studio. Tested on a 64GB machine with 2× RTX 3090 (48 GB VRAM).

---

## 1. Dataset Extraction

### Source

`src/test/java/studio/phaseshift/metatron/util/UnslothTrainingDatasetExtractor.java`

Scans JUnit 5 `@ParameterizedTest` + `@CsvSource` methods across ~20 test classes. Each CSV row becomes one
or more training entries, depending on the `@Training` annotation's column mappings. Entry generation is
delegated to `Training.Extractor.from()` which handles:

- **Annotated methods** (`@Training` with `map1/map2/map3`): each CSV row produces multiple entries
  (e.g. `code → result`, `code → desugared`, `desugared → result`).
- **Fallback methods** (no `@Training`): classic two-column `expression → result` with 10 rotating
  instruction templates.
- **Operator context enrichment**: every instruction in the expression is resolved via `?docq>>desc`
  from the metatron VM to weave semantic descriptions into the instruction field.

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

`.metatron/skills/mtron/assets/mtron_training_dataset.jsonl` (~2,600+ entries)

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
| Expression evaluation | ~2,300 | `@CsvSource` rows from test classes |
| Meta-knowledge | ~50 | Hardcoded facts about mtron |
| Sugar reference + pairs | ~75 | Auto-generated from `mInstSet.sugars()` |

### Key Architecture: `Training.java`

The `@Training` annotation lives at `src/test/java/studio/phaseshift/metatron/Training.java`. It contains:

- **`record Entry`** — the data carrier (instruction, input, output, sourceMethod) with `toJson()`.
- **`record Run`** — column-pair mapping: `map1={0,1}` means `col0→lhs, col1→rhs`.
- **`final class Extractor`** — `from(Method, CsvSource)` produces `List<Entry>` for both annotated
  and fallback methods.
- **`extractOperatorContext()`** — parses the expression, resolves instruction types via
  `resolveCode()` (Code chains) or direct `insts()` (single Inst), then queries
  `Router.readFromSpace(inst.tid().addQ("docq"))` for each instruction's `desc` field.
- `map1` can carry 3 elements (`{lhs, rhs, context}`) for entries needing additional input
  context with `<<lhs>>`/`<<rhs>>` templating.

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
| `Qwen/Qwen3-14B` | 14B | ~8.4 GB | 0.23 | Best reasoning quality |

### Multimodal Models (❌ Do Not Use)

Models with vision components (`image-text-to-text` pipeline) fail entirely — template parsing
errors, confabulated output, or near-random loss (~14). Only **text-only** Qwen variants work
for pure DSL fine-tuning with the current dataset.

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
  "model_name": "Qwen/Qwen3-14B",
  "training_type": "LoRA/QLoRA",
  "load_in_4bit": true,
  "gpu_ids": [0, 1],
  "max_seq_length": 2048,
  "learning_rate": "0.00005",
  "lr_scheduler_type": "cosine",
  "batch_size": 2,
  "gradient_accumulation_steps": 4,
  "max_steps": 600,
  "save_steps": 150,
  "warmup_steps": 10,
  "lora_r": 32,
  "lora_alpha": 8,
  "target_modules": ["q_proj","k_proj","v_proj","o_proj","gate_proj","up_proj","down_proj"],
  "gradient_checkpointing": "unsloth",
  "optim": "adamw_8bit"
}
```

Key notes:
- `cosine` > `linear` for runs >200 steps — avoids LR bottoming out early.
- `batch_size=2` + `gradient_accumulation_steps=4` = effective batch 8.
- 4-bit models cannot do data-parallel multi-GPU (bitsandbytes limitation). Dual GPU with
  `load_in_4bit: true` still helps by splitting the model across GPUs.
- Last-step loss spike is normal — cosine LR approaches near-zero at step 600.

---

## 4. Export Pipeline

### From Checkpoint (Unsloth Python API)

The Studio API `export/merged` + `export/gguf` endpoints can be unreliable for large models.
Use the Unsloth Python API directly from the checkpoint:

```bash
ssh ginger.local "docker exec unsloth python3 -c '
from unsloth import FastLanguageModel
m, t = FastLanguageModel.from_pretrained(
    \"/workspace/studio/outputs/<run>/checkpoint-600\",
    load_in_4bit=False
)
m.save_pretrained_gguf(\"mtron-qwen-XXb-gguf\", t, quantization_method=\"q4_k_m\")
print(\"DONE\")
'"
```

Output: `/workspace/studio/exports/mtron-qwen-XXb-gguf/` containing `<ModelName>.Q4_K_M.gguf`

---

## 5. Ollama Deployment

### Copy GGUF Out

```bash
ssh ginger.local "docker cp unsloth:/workspace/studio/exports/mtron-qwen-XXb_gguf/<file>.gguf /tmp/mtron-qwen-XXb.Q4_K_M.gguf"
```

### Modelfile

```dockerfile
FROM /tmp/mtron-qwen-XXb.Q4_K_M.gguf
TEMPLATE """<|im_start|>system
{{ .System }}<|im_end|>
<|im_start|>user
{{ .Prompt }}<|im_end|>
<|im_start|>assistant
{{ .Response }}<|im_end|>"""
PARAMETER temperature 0.7
PARAMETER stop "<|im_end|>"
```

### Register

```bash
sudo systemctl start ollama
ollama create mtron-qwen-XXb -f /tmp/Modelfile
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
| 14B | Qwen3-14B (text-only) | 600 | 0.23 | 0.69 | 31 min | ✅ |
| 2B ❌ | Qwen3.5-2B (multimodal) | 600 | 0.19 | 0.23 | — | Confabulates |
| 27B ❌ | Qwen3.6-27B (vision-language) | 600 | 0.25 | 0.50 | — | Template failures |

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

# 3. Upload dataset
TOKEN=$(cat /tmp/ustok.txt)
curl -X POST http://ginger.local:8882/api/datasets/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@.metatron/skills/mtron/assets/mtron_training_dataset.jsonl"

# 4. Start training (adjust model_name)
curl -X POST http://ginger.local:8882/api/train/start \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"model_name":"Qwen/Qwen3-14B","training_type":"LoRA/QLoRA","load_in_4bit":true,"gpu_ids":[0,1],...}'

# 5. Export GGUF (from checkpoint via Python)
ssh ginger.local "docker exec unsloth python3 -c '
from unsloth import FastLanguageModel
m,t=FastLanguageModel.from_pretrained(\"/workspace/studio/outputs/<run>/checkpoint-600\",load_in_4bit=False)
m.save_pretrained_gguf(\"mtron-gguf\",t,quantization_method=\"q4_k_m\")
'"

# 6. Deploy to Ollama
ssh ginger.local "docker cp unsloth:/workspace/studio/exports/mtron-gguf/<file>.gguf /tmp/m.gguf"
sudo systemctl start ollama
ollama create mtron -f Modelfile
```
