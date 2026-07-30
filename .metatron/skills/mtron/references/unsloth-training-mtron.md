# Unsloth Training for mtron — End-to-End Guide

This file explains how to fine-tune an LLM on the mtron language -- from training data set extraction via test-suite
reflection to Ollama deployment, using Unsloth Studio. This tutorial was run on a 64GB machine with 2x RTX 3090 at a
total of 48 GB VRAM.

---

## 1. Dataset Extraction

### Source

`src/test/java/studio/phaseshift/metatron/util/UnslothTrainingDatasetExtractor.java`

Scans JUnit 5 `@ParameterizedTest` + `@CsvSource` methods across 25 test classes in the metatron codebase. Each CSV row
becomes one training entry.

### Run

```bash
cd metatron
./mvnw test -Dtest=UnslothTrainingDatasetExtractorTest
```

### Output

`.metatron/skills/mtron/assets/mtron_training_dataset.jsonl` (~1,957 entries)

### Dataset Format

Alpaca-style JSONL:

```json
{
  "instruction": "Evaluate this mtron expression involving grphspace operations: lhs evaluates to rhs",
  "input": "*/g/V/+.count()",
  "output": "6"
}
```

Three types of entries are generated:

| Type                    | Count  | Source                                                                                                                         |
|-------------------------|--------|--------------------------------------------------------------------------------------------------------------------------------|
| Expression evaluation   | ~1,839 | `@CsvSource` rows from test classes                                                                                            |
| Meta-knowledge          | ~8     | Hardcoded facts about mtron (what is mtron, types, tid/vid, spaces, etc.)                                                      |
| Sugar reference + pairs | ~110   | Auto-generated from `mInstSet.sugars()` — one reference entry per sugar operator, plus training pairs (sugar ↔ desugared form) |

### Meta-Knowledge Entries

Hardcoded in `addMetaKnowledge()` — covers:

- What is mtron? What is the Metatron VM?
- Type system (mono/poly/call, tid vs vid)
- Expression evaluation, common operators
- Spaces architecture
- Capability confirmation ("Can you help me write mtron code?")

Format: `instruction` = "Answer this question about the mtron language…", `input` = question, `output` = answer.

### Sugar Entries (Auto-Generated)

Generated programmatically from `new mInstSet().sugars()` using the Sugar object's metadata:

| Sugar position                  | Example                     |
|---------------------------------|-----------------------------|
| PREFIX, argCount≥1              | `a + b` ↔ `a.plus(b)`       |
| PREFIX, argCount=0 (standalone) | `_` ↔ `id()`                |
| PREFIX, argCount=0 (postfix)    | `a ;` ↔ `a.end()`           |
| PREFIX, true prefix (`*`, `\|`) | `* a` ↔ `mult(a)`           |
| INFIX                           | `a & b` ↔ `a.and(b)`        |
| WRAP                            | `a._/ b \_` ↔ `a.within(b)` |

Multi-instruction chains (e.g., `?~` → `is`+`matches`) only get sugar→desugar pairs (ambiguous to reverse).

The algorithm is in `addMetaKnowledge()` — modify there to add/change entries.

---

## 2. Unsloth Studio

### Server

Running as a Docker container on `ginger.local`:

```bash
ssh ginger.local "docker ps | grep unsloth"
# unsloth/unsloth — ports 8882→8000 (UI), 8881→8888 (API), 2222→22 (SSH)
```

### API Access

Base URL: `http://ginger.local:8882`
Auth: POST `/api/auth/login` with `{"username":"unsloth","password":"<password>"}` → returns JWT `access_token`.

A Python helper is available at `.metatron/skills/mtron/scripts/unsloth_studio.py`:

```python
from unsloth_studio import Studio

s = Studio('http://ginger.local:8882', 'unsloth', '<password>')
# s.upload_dataset(...), s.train(...), s.status(), s.wait_for_training(), s.export_gguf(...)
```

### Key Endpoints

| Method | Path                          | Use                                             |
|--------|-------------------------------|-------------------------------------------------|
| POST   | `/api/datasets/upload`        | Upload JSONL dataset (multipart file)           |
| GET    | `/api/train/status`           | Check training phase/step/loss                  |
| POST   | `/api/train/start`            | Start training job (see payload below)          |
| POST   | `/api/train/stop`             | Graceful stop                                   |
| POST   | `/api/train/reset`            | Clear state for new job                         |
| GET    | `/api/train/hardware/visible` | Per-GPU VRAM/util/temp                          |
| GET    | `/api/models/loras`           | List trained adapters                           |
| POST   | `/api/export/load-checkpoint` | Load a checkpoint for export                    |
| POST   | `/api/export/export/merged`   | Merge LoRA into base (16-bit FP16 or 4-bit FP4) |
| POST   | `/api/export/export/gguf`     | Convert merged model to GGUF (Q4_K_M, etc.)     |

### Training Payload

```json
{
  "model_name": "unsloth/Qwen3-8B",
  "training_type": "LoRA/QLoRA",
  "load_in_4bit": false,
  "max_seq_length": 2048,
  "local_datasets": [
    "/workspace/studio/assets/datasets/uploads/<uuid>_mtron.jsonl"
  ],
  "format_type": "alpaca",
  "num_epochs": 10,
  "learning_rate": "0.00005",
  "batch_size": 2,
  "gradient_accumulation_steps": 4,
  "warmup_steps": 10,
  "max_steps": 600,
  "save_steps": 150,
  "weight_decay": 0.001,
  "random_seed": 3407,
  "packing": false,
  "train_on_completions": true,
  "gradient_checkpointing": "unsloth",
  "optim": "adamw_8bit",
  "lr_scheduler_type": "cosine",
  "lora_r": 32,
  "lora_alpha": 8,
  "lora_dropout": 0,
  "target_modules": [
    "q_proj",
    "k_proj",
    "v_proj",
    "o_proj",
    "gate_proj",
    "up_proj",
    "down_proj"
  ],
  "use_rslora": false,
  "use_loftq": false,
  "gpu_ids": [
    0,
    1
  ]
}
```

Key hyperparameter notes:

- `lr_scheduler_type`: `cosine` > `linear` for longer runs (avoids LR bottoming out early).
- `batch_size=2` + `gradient_accumulation_steps=4` = effective batch size of 8.
- `gpu_ids: [0, 1]` enables dual-GPU (only works with non-4bit models; bitsandbytes 4-bit is single-device).
- `save_steps: 150` creates checkpoints at 150, 300, 450, 600 for resumption.

---

## 3. Model Selection

### What Works

| Model                                     | Params | Precision | Dual GPU | Notes                           |
|-------------------------------------------|--------|-----------|----------|---------------------------------|
| `unsloth/Qwen3-8B`                        | 8B     | BF16      | ✓       | ~15 GB VRAM per GPU with LoRA   |
| `unsloth/Qwen3.5-2B`                      | 2B     | BF16      | ✓       | Fast, good for testing pipeline |
| `unsloth/gemma-4-e2b-it-unsloth-bnb-4bit` | 2B     | 4-bit     | ✗       | Pre-quantized, single GPU only  |

### What Doesn't

- **4-bit bitsandbytes models** (`-bnb-4bit` suffix) cannot train on multiple GPUs — the quantized weights are
  device-locked.
- **GGUF models** are inference-only, not trainable.
- Full 20B+ models in BF16 need >48 GB VRAM without 4-bit quantization (use QLoRA with `load_in_4bit: true` instead).

### Loss Interpretation

Cross-entropy loss for next-token prediction:

| Loss    | Meaning                                             |
|---------|-----------------------------------------------------|
| 3.0–8.0 | Random guessing (untrained model on novel language) |
| 2.0–3.0 | Beginning to learn syntax                           |
| 1.0–2.0 | Basic patterns emerging                             |
| 0.3–0.5 | Decent fluency, ~60–75% token confidence            |
| 0.1–0.2 | Strong recall, ~80–90% token confidence             |
| <0.1    | Near-memorization of training set (may overfit)     |

---

## 4. Export Pipeline

### Merge LoRA into Base

```python
api('POST', '/api/export/load-checkpoint', {
    'checkpoint_path': '/workspace/studio/outputs/<run_dir>/checkpoint-600'
})
api('POST', '/api/export/export/merged', {
    'save_directory': 'mtron-qwen-8b',  # relative name
    'format_type': '16-bit (FP16)',
    'push_to_hub': False
})
```

Output lands in `/workspace/studio/exports/mtron-qwen-8b/`.

### Convert to GGUF

```python
api('POST', '/api/export/export/gguf', {
    'save_directory': 'mtron-qwen-8b-gguf',
    'quantization_method': 'Q4_K_M',
    'push_to_hub': False
})
```

Output: `/workspace/studio/exports/mtron-qwen-8b-gguf/<ModelName>.Q4_K_M.gguf`

---

## 5. Ollama Deployment

### Copy GGUF out of container

```bash
ssh ginger.local "docker cp unsloth:/workspace/studio/exports/mtron-qwen-8b-gguf/<file>.gguf /tmp/mtron.gguf"
```

### Create Modelfile

```dockerfile
FROM /tmp/mtron.gguf
PARAMETER temperature 0.7
PARAMETER top_p 0.9
TEMPLATE """{{ if .System }}<|im_start|>system
{{ .System }}<|im_end|>
{{ end }}{{ if .Prompt }}<|im_start|>user
{{ .Prompt }}<|im_end|>
{{ end }}<|im_start|>assistant
"""
SYSTEM """You are an expert mtron language assistant. You evaluate mtron expressions and return the correct result."""
```

### Register

```bash
ollama create mtron-qwen-8b -f /tmp/Modelfile.mtron
ollama run mtron-qwen-8b "Evaluate: false.as(int::T)"
```

---

## 6. Ollama on ginger.local

Ollama runs via systemd:

```bash
sudo systemctl start ollama      # if stopped
ollama list                       # show all models
ollama rm <name>                  # delete a model
```

---

## 7. Training History

Previous runs for reference:

| Run             | Model | Steps | GPUs | Best Loss | Notes                                 |
|-----------------|-------|-------|------|-----------|---------------------------------------|
| Gemma E2B 4-bit | 2B    | 30    | 1    | 1.52      | Old format (desc/input/output), poor  |
| Gemma E2B 4-bit | 2B    | 300   | 1    | 0.12      | New format (instruction/input/output) |
| Qwen3.5-2B      | 2B    | 300   | 2    | 0.23      | LR 2e-4 linear                        |
| Qwen3.5-2B      | 2B    | 600   | 2    | 0.19      | LR 5e-5 cosine, best dual-GPU result  |

---

## 8. Quick-Start Cheat Sheet

Use the bundled helper script at `.metatron/skills/mtron/scripts/unsloth_studio.py`:

```bash
# 1. Regenerate dataset
cd metatron
./mvnw test -Dtest=UnslothTrainingDatasetExtractorTest

# 2. Upload + train (one command)
export UNSLOTH_PASSWORD="<password>"
cd .metatron/skills/mtron/scripts
python3 unsloth_studio.py train \
  ../assets/mtron_training_dataset.jsonl \
  unsloth/Qwen3-8B 600

# 3. Monitor
python3 unsloth_studio.py status
python3 unsloth_studio.py hw

# 4. Export to GGUF (use the Studio class programmatically)
python3 -c "
from unsloth_studio import Studio
s = Studio('http://ginger.local:8882', 'unsloth', '<password>')
s.load_checkpoint('/workspace/studio/outputs/<run>/checkpoint-600')
s.export_merged('mtron-model')
s.export_gguf('mtron-model-gguf')
"

# 5. Deploy
ssh ginger.local
docker cp unsloth:/workspace/studio/exports/mtron-model-gguf/<file>.gguf /tmp/mtron.gguf
ollama create mtron -f /tmp/Modelfile.mtron
```

### Script API

```python
from unsloth_studio import Studio

s = Studio('http://ginger.local:8882', 'unsloth', '<password>')

# Dataset
path = s.upload_dataset('dataset.jsonl')

# Train
job_id = s.train(model_name='unsloth/Qwen3-8B', dataset_path=path, steps=600, gpus=[0, 1])
s.wait_for_training()  # blocks until done

# Export
s.load_checkpoint('/workspace/studio/outputs/<run>/checkpoint-600')
s.export_merged('mtron-model')
s.export_gguf('mtron-model-gguf')
```
