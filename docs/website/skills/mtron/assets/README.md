---
name: mtron_training
description: The mtron training dataset (JSONL) for fine-tuning LLMs on mtron code generation
license: agpl-3.0
language:
  - mtron
  - code
tags:
  - mtron
  - metatron
  - code-generation
  - programming-language
  - qwen
  - lora
base_model: Qwen/Qwen3-8B
pipeline_tag: text-generation
---

<p align="center">
  <img src="https://raw.githubusercontent.com/PhaseShiftStudio/metatron/main/docs/website/images/icons/mtron-icon.svg" width="120" alt="mtron" />
</p>

<h1 align="center">mtron-qwen-8b</h1>

<p align="center"><strong>Qwen3-8B fine-tuned on the mtron programming language</strong></p>

<p align="center">
  <a href="https://github.com/PhaseShiftStudio/metatron"><img src="https://img.shields.io/badge/metatron-VM-blue" /></a>
  <a href="https://github.com/PhaseShiftStudio/metatron"><img src="https://img.shields.io/badge/mtron-language-green" /></a>
  <img src="https://img.shields.io/badge/model-8B-orange" />
</p>

---

## Model Description

`mtron-qwen-8b` is a LoRA fine-tune of **Qwen3-8B** on the **mtron** functional programming language of
the [Metatron VM](https://github.com/PhaseShiftStudio/metatron). It can evaluate mtron expressions, explain language
concepts, and translate between mtron sugar operators and their desugared instruction forms.

## Training

| Metric                | Value                                     |
|-----------------------|-------------------------------------------|
| **Base model**        | `Qwen/Qwen3-8B`                           |
| **Training type**     | LoRA (BF16, full precision)               |
| **Dataset**           | 1,957 mtron expression→result pairs       |
| **Hardware**          | 2× NVIDIA RTX 3090 (48 GB VRAM)           |
| **Training steps**    | 600                                       |
| **Best loss**         | 0.22 (step 556)                           |
| **Final loss**        | 0.70                                      |
| **Learning rate**     | 5e-5, cosine schedule                     |
| **Batch size**        | 2 × 4 gradient accumulation = 8 effective |
| **LoRA rank / alpha** | r=32, α=8                                 |
| **LoRA dropout**      | 0                                         |
| **Precision**         | BF16 (training), FP16 (merged export)     |
| **Training time**     | ~15 minutes                               |

### Loss Curve

| Step | Loss        |
|------|-------------|
| 50   | 0.76        |
| 150  | 0.50        |
| 250  | 0.27        |
| 350  | 0.30        |
| 450  | 0.31        |
| 556  | **0.22**    |
| 600  | 0.70 (eval) |

Starting from a baseline of ~3.5 (random guessing on a novel language), the model converges to strong token-level
accuracy by step 250 and continues improving through step 550.

## Dataset

The training dataset was automatically extracted from the Metatron test suite via `UnslothTrainingDatasetExtractor`,
which scans `@ParameterizedTest` + `@CsvSource` annotated methods across 25 test classes. Each CSV row is one training
entry in Alpaca format:

```json
{
  "instruction": "Evaluate this mtron expression involving grphspace operations: lhs evaluates to rhs",
  "input": "*/g/V/+.count()",
  "output": "6"
}
```

The dataset includes:

| Type                  | Count              | Source                                                  |
|-----------------------|--------------------|---------------------------------------------------------|
| Expression evaluation | 1,839              | `@CsvSource` rows from test classes                     |
| Meta-knowledge        | 8                  | What is mtron? What is Metatron? Types, tid/vid, spaces |
| Sugar operators       | 39 refs + 71 pairs | Auto-generated from `mInstSet.sugars()`                 |

## Usage

### Ollama

```bash
ollama run okrammarko/mtron-qwen-8b "Evaluate this mtron expression: false.as(int::T)"
```

### Transformers

```python
from transformers import AutoModelForCausalLM, AutoTokenizer

model = AutoModelForCausalLM.from_pretrained("okrammarko/mtron-qwen-8b")
tokenizer = AutoTokenizer.from_pretrained("okrammarko/mtron-qwen-8b")

prompt = "Evaluate this mtron expression: {int{10}::1}.plus(_)"
inputs = tokenizer(prompt, return_tensors="pt")
outputs = model.generate(**inputs, max_new_tokens=50)
print(tokenizer.decode(outputs[0]))
# int{10}::2
```

## About mtron

mtron is the functional programming language of the [Metatron VM](https://github.com/PhaseShiftStudio/metatron), a
distributed data-oriented computing system built in Java. mtron expressions navigate typed spaces via URI-based paths
with sugar operators for concise syntax:

| Sugar     | Desugared     | Meaning                 |
|-----------|---------------|-------------------------|
| `a == b`  | `a.select(b)` | Structural selection    |
| `a =?= b` | `a.where(b)`  | Structural verification |
| `a + b`   | `a.plus(b)`   | Addition                |
| `*uri`    | `from(uri)`   | Dereference             |
| `a -> b`  | `a.ref(b)`    | Reference (write)       |
| `a >> b`  | `a.rshift(b)` | Projection              |
| `_`       | `id()`        | Identity                |

---

<p align="center">
  <sub>Built with <a href="https://unsloth.ai">Unsloth</a> · Trained by <a href="https://github.com/PhaseShiftStudio">PhaseShift Studio</a></sub>
</p>
