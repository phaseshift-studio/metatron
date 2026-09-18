# FineTuneFeature — the distillation family's third sibling

Status: **design** (no implementation). This doc realizes `FineTuneFeature` in terms of what already
exists, then generalizes the two ledger-distilling features (`CompactionFeature`, `SummarizeFeature`)
so all three share one ledger-reading substrate and one distillation template. It ends with the open
questions we should iterate on.

---

## 1. The realization: `FineTuneFeature`

`FineTuneFeature` is a passive observer of the agent that turns *usage* into the same Alpaca triplet the
`UnslothTrainingDatasetExtractor` turns *tests* into:

```
train::[ instruction => str::T, input => #::T, output => #::T ]
```

`input`/`output` are deliberately poly (`#::T`): a tool-using turn's `input` is a folded transcript, not a
string. This is the one shape that matters — everything else is plumbing we already have.

### 1.1 Two capture paths (the key divergence from its siblings)

Compaction and Summarize are **always-lossy** — they distill every time, via a mini-chat. FineTune needs a
**lossless** primary path plus an optional lossy curation:

| path | trigger | cost | product |
|---|---|---|---|
| **raw capture** | every `onCompleteResponse` | ~0 (a space write) | verbatim `(instruction, input, output)` |
| **curated distill + augment** | `<<mtron:fine_tune>>` watermark, or `fine_tune()` inst, or a pool-cap auto-trigger | one mini-chat | vetted/merged rows **+ synthetic in-distribution pairs** |

Raw capture is what makes it "silent, behind the scenes". The curated pass is two jobs at once: it *cleans*
(dedup/vet/annotate the raw rows — the acceptance gate, §1.4) and it **augments** — the mini-chat synthesizes
additional `(instruction, input, output)` pairs that follow the same patterns as the real usage (the
"synthetic augmentation" method). Which half dominates is the `model::T` knob: the default (the inspected
session's model) is local and private — the "local LLM generation" method — while an override to a stronger
teacher buys higher-quality synthesis. The mini-chat uses the **same** skeleton as
`summarizeSession`/`compactSession`, which is exactly why §4–§5 exist.

### 1.2 Hook mapping

- `onCompleteResponse(agent, ChatFrame)` — write one raw `train::T` row: `instruction = result.user()`,
  `output = result.chat()`, `input =` the turn's folded tool transcript (accumulated in `onToolExecuted`,
  cleared in `onBeforeChat`), plus `source => [!*msg vids]` and `session`/`time`.
- `onToolExecuted(agent, Obj toolRec)` — append `[name, arguments, result]` to the per-turn transcript.
  This is what turns a chat row into an *agentic* trajectory row.
- `onError(agent, Fail)` / the interrupted-turn close — route the turn to a **rejected** subspace
  (`train/rejected/`), not the accepted pool.
- `onCompleteResponse` watermark `<<mtron:fine_tune>>` — the model nominates a turn as exemplary (self-approval).
- `fine_tune()` inst (operator) — the human-side approve/reject/curate/export entry point.

### 1.3 Where it lands + provenance

Rows write under `getRoot(agent).extend("train").extend("_").addQ(INCRQ)` (the `CostFeature` pattern).
Every row carries `source =>` a `!*` lst of the message vids that produced it — the same `claim.source`
mechanism `SummarizeFeature` already uses. Provenance gives us, for free: **dedup** (never mine a message
twice), **audit** (which turns made this row), and **retract** (drop rows whose source was edited).

### 1.4 Acceptance / rejection — RLHF without the H

The watermark protocol is already bidirectional: the *model* emits blocks to *features*, and the mtron
`inst` layer is the *human* → feature channel. FineTune inverts the usual polarity to capture **preference**:

- interrupted / regenerated / edited turns → *rejected* example (the pre-edit answer).
- the edited or re-issued answer → *chosen* example.

That is a DPO/RLHF pair with zero annotation, riding the exact `<<mtron:…>>` + `inst` machinery the other
two features use. The `train/rejected/` subspace mirrors how `summarizeSession` splits `claim/` vs
`loose_end/`.

### 1.5 Export bridge

`FineTuneFeature.offers(LLM_TRAIN_DATASET_SERVICE_TID)`. An `export_training()` inst reads the pool, dedups,
and writes the JSONL — but the marshaling is **not Java**: the train row is a first-class mtron rec, and
`.as(json::T)` (the `as()` graph — the auto-mapping `markdown → html → rec → json → …`) does the
serialization. The export `op` is `*<train pool> .as(json::T) >>= <file>`, not a hand-rolled `escapeJson()`
like the extractor's `Entry.toJson()`. One format, two sources (reflective + usage); the Unsloth path
(`scripts/export_gguf.py` → GGUF → Ollama → redeploy) is unchanged.

### 1.6 Anti-collapse

This is the only sibling that can poison its own input (train → deploy → capture → train). Mitigations:
accept-only-when-acknowledged, cap self-generated rows, and **re-anchor on the reflective dataset each
round** — which also respects the earlier finding that mixing diluted the mtron-only model.

### 1.7 Prior art: usage-based generation (and how this maps)

Industry calls this "user-based human-generated" data (e.g. InstructWild's ~110K instructions scraped from
shared prompts). The four standard methods are already in our design, under better names:

| industry method | FineTuneFeature mechanism |
|---|---|
| **public platform aggregation** | the agent's ledger *is* the platform — every turn is a shared prompt+response |
| **synthetic augmentation** | the curated mini-chat (§1.1) generates additional in-pattern pairs |
| **local LLM generation** (privacy/cost) | default `model::T` = the inspected session's model; data never leaves the VM |
| **tool-assisted curation** (topic-trees, human guidance) | acceptance/rejection (§1.4), concept features as topic-trees, `export_training()` |

The one nuance worth carrying forward: synthetic augmentation is a *generation* step, not a cleaning step, so
the curated path must cap and provenance-stamp its synthetic rows exactly like raw rows (§1.6) — otherwise
"augment" quietly becomes "collapse".

### 1.8 Reactive capture: the space is the bus (`?subq`)

The capture trigger need not be a lifecycle hook — it can be **pub/sub**. Teach the model to *post* a train
rec to a URI, and subscribe to writes on that URI with `?subq` (`QCollection.subq()`, a Java q-proc):

```mtron
/sys/tmp/finetune_feature/train#?subq -> sub::[code=> <dedup/cap/append>]
```

The skill's instruction — "whenever you learn something new about mtron, post an alpaca `train::T` rec to
`/sys/tmp/finetune_feature/train`" — makes the **model the author**: it posts when it has learned (the model's
write goes through a tool/watermark, then a space write). The `?subq` subscription (registered by
`FineTuneFeature` in Java) is the reactive processor: every write to the train URI — whether from the
**retrospective mini-chat** (the distiller surveys the whole ledger and emits rows) or the **online proactive
post** (the model submits as it learns) — fans through the one `sub::[code=>…]`. The two capture paths are
**two writers to one bus**, and dedup/cap/append live in exactly one place.

The §5.2 boundary still holds: `?subq` is a Java q-proc, so using it is core-in-Java; only the `code=>`
reaction is mtron, and even that can be a Java-backed `instLambda` calling back into the feature. The pub/sub
surface is mtron; the processing stays Java.

---

## 2. The ledger today: three divergent readers

The same "read a session's messages" operation exists three ways:

1. **`SpaceChatSessionStore.Query`** — the fluent builder
   (`query(vid).maxFromCurrent(n).stopAt(tid).include(…).exclude(…).apply() → List<Rec>`). Store-bound
   (a `Query` is built off a per-turn `SpaceChatSessionStore` instance), materializes to `List<Rec>`.
2. **`SpaceChatSessionStore.sessionRels(vid)`** — the private read core: `List<Rel>` (vid ⇒ rec), scoped by
   session/depth/chatId, sorted by ledger id. `Query.apply()` is built on top of it.
3. **Inline reads in `compactSession`/`summarizeSession`** — `Router.readFromSpace(agentHome/message/+/`
   plus *hand-rolled* session filter, scope filter, `Graphitty.strip`, `.replace("%", "")`, ledger-id sort,
   and digest join. Neither goes through `Query`.

So the two features that *are* the distillation family do **not** use the query API that already exists for
the store — they re-derive it, slightly differently each time (`summarize` keeps `vid ==> text`; `compact`
drops vids and joins with `-----`).

### 2.1 Why the features bypass `Query`

The background distillation thread has an `agentHome` + `sessionVID` (from `SessionAddress`), **not** the
turn's `SpaceChatSessionStore` instance (which is created per-turn in `AbstractMessageFeature.onBeforeChat`
and lives on the agent). `Query` is an inner class of the store, so it is structurally unavailable to that
thread. That is the real reason the features read raw rels. Fixing this means moving the *query* from the
**store** to the **ledger address** — a read you can open from `(agentHome, sessionVID)` alone.

---

## 3. The stream is mtron

We don't need a new Java `LedgerStream` — the lazy ledger stream **is mtron**. The three divergent readers
(`Query`, `sessionRels`, the inline reads in the two distill features) are three *Java re-implementations of
a subset of mtron* — the exact anti-pattern the codebase already warns against ("every mtron expression is a
fluent chain of instructions"; "don't shape-inspect a read — `stream()` it"). A read of a space region is
already lazy, filterable, projectable, and windowable in the language itself:

```mtron
*/usr/dr/message/+.?[session => /usr/dr/session/1,
                     time    => ?<datetime_now() .?> datetime_now()-date::1.0].take(3)
```

One expression is the whole §2 taxonomy: deref (`*`), scope + kind + time filter (`.?[rec]` — the **`isa`
(`?`) pattern filter**, with predicate-valued fields), window (`take(n)`/`skip(n)`), and — applied to the
result — the digest projection (`.map`, `==[vid=>…]`).

The filter is `isa`, not `where`: `isa(rec)` is `lhs.test(rec) ? lhs : noobj()`, and `where(rec)` / `=?=`
was the *same* pattern-match computation with a second name — it is being removed in `isa`'s favor. So the
canonical ledger query is written in `isa` form, and the distill features should follow suit rather than
resurrecting `where`.

### 3.1 The stage vocabulary, already in the language

| stream stage | mtron |
|---|---|
| scope (session / depth / chatId) | fields in the filter rec: `.?[session=>…, depth=>…, chat_id=>…]` |
| kind projection (include/exclude) | filter on `tid`: `.?[tid=>…]` (the `isa` filter); a sentinel bound is `.?[tid=>compaction_message::T]` |
| time scope (`withinScope`) | predicate field: `time => ?<now .?> now-1day` |
| window cap / tail | `.take(n)` / `.skip(n)` (ledger is append-ordered) |
| digest | `.map(…)` / `==[vid=>…]` — a projection, not a hand-rolled `String.join` |
| pair-safety | **the exception — §3.2** |

### 3.2 The one thing that is not a clean mtron operator: pair-safety

`adjustSkipToPreservePairs` is a *correctness invariant* (never tear an `ai` message from its `tool_result`s,
never start a window on anything but a user message) — a ~150-line Java algorithm, not a point-free operator.
It is a **window-boundary** concern, not a ledger concern: the ledger is already contiguous (`ToolPairGate`
writes a tool group as one unit), so the invariant only bites when a window *cuts* a group. It therefore
stays a **post-step** applied to the query result — either plain Java, or a registered mtron `inst`
(`?pairsafeq`-style) so it can ride the same chain.

### 3.3 Reconcile with `Query`

`Query`/`sessionRels`/`getMessages`/`busWindow` don't go away; they become **one canonical mtron query**
(parameterized with the session/depth/chatId holes) that the LC4j store and the distill features both
*evaluate*. `Query` is then a thin typed wrapper that *emits* that query, not a parallel implementation.
The three readers collapse to one query string plus the pair-safe post-step — and, because the query is
data, it becomes **overridable from a boot file**, which no Java class was.

### 3.4 Fold-left: the query compiles to the backend

"Lazy" undersells it. The `isa`/`select`/`take` chain is *rewritten* — a left-fold of the instruction chain
into one native query — by the `algebra/rewrite` system (`Rewriter`, `RewriteBuilder`, `CommonRewrites`,
wired into `tbleInstSet` SQL and `dcmntInstSet` Mongo; see `docs/design/rewrite-planner.md`). So
`*<ledger> .?[pattern] ==[projection] .take(n)` becomes `SELECT <projection> … WHERE <pattern> … LIMIT n`
(or the Mongo/TinkerPop equivalent): the backend filters, projects, and windows, and the JVM never
materializes the full ledger. The projection `==[time=>_,text=>_]` is *column selection* pushed into the
same query — the "digest" is what the backend returns, not a Java `String.join` over a materialized list.
Today's `Router.readFromSpace` + Java stream + filter reads everything then discards most of it in Java;
the fold-left makes the ledger's storage engine do that work.

---

## 4. The `op` is the stream — not the whole pipeline

The read+project is a mtron expression (the `op`, defaulting to the standard ledger stream), but the
mini-chat is **not** part of it — `DistillFeature` constructs the distiller agent in Java (§5). The user
specifies at most a `model::T` and an `op`; both default:

```mtron
distill_feature::T@summarize_feature                    // nominal — no fields required
distill_feature::T[op => *<ledger> .?[session=>…, time=>…] == [time=>_,text=>_],
                   model => <some model>]                // override only what you need
```

The default `op` is the standard stream: deref the ledger (`*`), filter by session/time (`.?[…]` — `isa`,
folding left into the backend, §3.4), project to the fields the distiller needs (`==[time=>_,text=>_]`),
window (`.take(n)`). The projected stream is what `DistillFeature` hands to the mini-chat (inlined, or
spilled + paged per §4.1). So **"gather → digest" is the `op`; "mini-chat → parse → write" is the Java.**

The two `if/else` beasts die with the Java they lived in: the `for [claim, loose_end]` write-loop becomes a
second projection that stamps `!*` refs, and the duplicated gather/digest is one shared query string.

Everything downstream of the stream is the `as()` graph: the mini-chat's markdown/watermarks `.as(rec::T)`
into the typed artifacts, and the train rec `.as(json::T)` into JSONL (§1.5). `as()` — the auto-mapping
`markdown → html → rec → json → …` — already moves types between representations, so no step needs a
bespoke serializer.

### 4.1 Spill, don't inline (memory)

`chat(prompt + *stream)` inlines the whole projected ledger into context. For a large ledger, spill instead:
`.map(text) >>= /sys/tmp/stream_01` and let the agent page `/sys/tmp/stream_01/+` — compaction's sentinel
idea, but the *stream* is the paged artifact rather than a summary. Same `op`; the spill is a different body.

---

## 5. `distill_feature::T` — shared machinery, nominal names

The three features share a base *type* and a base *Java class*, but their names are **nominal** — the type
system enforces them rather than leaving them to convention:

```
feature::T[?[op{?}=>code::T, model{?}=>model::T, prompt{?}=>str::T]]@distill_feature   // the base
distill_feature::T@summarize_feature      // nominal — no predicate, just the name
distill_feature::T@compaction_feature     // nominal
distill_feature::T@finetune_feature       // nominal
```

`distill_feature::T` carries the shared machinery: the optional `op` (default stream), `model` (default:
the inspected session's model), and `prompt` (default per refinement). The three are **nominal
refinements** — no predicate — so "any old rec" cannot become a `finetune_feature::T`: the type check is
`Type.Helper.nominalTypeChecker`, i.e. `obj.vid().test(finetune_feature)`. The rec must be typed explicitly
at construction (`distill_feature::T@finetune_feature`), else any later conversion fails with "nominally not
a finetune_feature::T".

### 5.1 The Java: `DistillFeature` builds the distiller agent

The one Java class (`DistillFeature extends AbstractFeature`) does the agent construction behind the scenes,
so the user never names a feature beyond the model:

- construct a **minimal** agent — the `model::T` (default = the inspected session's model) + `MessageFeature`
  (the ledger store) + `ToolFeature` (the paging tool) + `SkillFeature` (the artifact skill) — and nothing
  else; it is as small as it needs to be;
- evaluate the `op` → the stream, hand `prompt + stream` to that agent (or spill + `page`, §4.1);
- parse the artifact watermarks and write the typed recs (`claim`/`loose_end`, the sentinel, `train`).

The three nominal types are the same class with different defaults — `op`, `prompt`, the artifact skill, the
paging tool — but the **name** is what the type system checks, and the name is what carries the intent.

### 5.2 The Java/mtron boundary: core in Java, data in mtron

The reason `DistillFeature` is Java — not a mtron rec — is the tooling: Java gives IDE refactoring, type
checking, and autocomplete; mtron has none, and it is still mutating (`where()` → `isa()` is a
find-replace-across-the-codebase exercise, with no compiler to catch a missed instance). Rule of thumb:

- **author the core in Java** — the default `op` (built from `Query`/TID constants, not a query string), the
  prompt constants, the agent construction, the skill/tool registration. When the language renames a token,
  the Java side renames `WHERE_INST_TID` → `ISA_INST_TID` in the IDE, not a string in a config.
- **author the data in mtron** — the user's `op`/`model`/`prompt` overrides, the training rows, the spilled
  stream. These are *values the user supplies*, not code the system maintains; mtron's brevity is the point,
  and the user owns the language-mutation risk for their own config.

So §4's `op` defaults to a Java method (via `Query` + TID constants); the `code::T` field is the *override*,
not the source of truth. The mtron one-liner is the escape hatch and the documentation, not the
implementation.

---

## 6. The memory trichotomy + the loop

The sharpest framing: the three features are **one machine pointed at three memories**, all distilled from
the same ledger:

| feature | memory kind | artifact | what it buys |
|---|---|---|---|
| `CompactionFeature` | **context** | resume summary | continuity *within* a window |
| `SummarizeFeature` | **episodic** | claims / loose ends | salience *across* sessions |
| `FineTuneFeature` | **parametric** | train rows | competence *in the weights* |

Context and episodic memory are monotonic — they can't poison their own input. Parametric memory is a
**closed loop** (capture → fine-tune → redeploy → capture), which is both its power and its one unique
risk (model collapse). The design should therefore lean on the same two levers the siblings never needed:
acceptance-gated capture, and a hard cap on self-generated data re-anchored to the reflective set.

One more loop worth naming: once weights improve, `compact`/`summarize` distillations improve, which
changes the data `FineTuneFeature` captures — the three memories co-evolve through the same ledger.

---

## 7. Open questions (for iteration)

1. **Paging tool contract** — `page(location, start, end)`; should it also return total-length / a "has more"
   flag so the agent knows when to stop? One tool or a `list_stream`/`page` pair?
2. **Spill location** — `/sys/tmp/stream_01` (ephemeral) vs a typed, stable `stream::T` rec under the
   feature's `root`.
3. **Fold-left coverage** — which `isa`/`select`/`take` shapes push down (SQL vs Mongo vs TinkerPop) vs fall
   back to a JVM read? Decides whether the spill is even needed for large ledgers.
4. **The default `op` per refinement** — is it literally shared (one query string with `session`/`time`
   holes), or does each refinement narrow it (compact's `stopAt`, finetune's `take(n)`)?
5. **Minimal-agent feature set** — is `MessageFeature + ToolFeature + SkillFeature` exactly the minimum, or
   does the distiller also need `SystemFeature` (skill-table prompt) / `ThinkFeature`?
6. **FineTune `input` semantics** — folded tool transcript, prior context, or the projected stream; does the
   default `op` already produce the right `input` string?
7. **Rejected set** — `train/rejected/` as DPO pairs vs a filter; one JSONL or a chosen/rejected pair file?
8. **Pair-safety** — `?pairsafeq` inst vs a Java post-step on every `op` evaluation.
