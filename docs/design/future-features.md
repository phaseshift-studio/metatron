# Future Features & Ideas

> Generated from design sessions with Dr. Stynx, agent self-analysis,
> and architectural exploration.  No timeline — a parking lot for
> well-formed ideas.

## Core Agent Architecture

### Mock StreamingChatModel for Test Harness
**Priority:** High — blocks comprehensive lifecycle tests.

Currently `testFullChatLifecycleWithMockLLM` is `@Disabled` because
`agent.chat()` requires a real LLM connection.  A mock model that fires
canned callbacks (`onPartialResponse`, `onToolExecuted`, `onCompleteResponse`)
would unlock end-to-end tests for every feature without network dependencies.

### Response Format as Feature Config
**Priority:** Medium.

The `responseFormat` parameter on `chat()` could move to the ChatFeature
config.  This would make structured output a first-class feature concern
rather than an ad-hoc parameter.

### CostFeature — Provider pricing API lookup
**Priority:** Medium.

Currently a stub — reads cost from model config if present.  The feature
should scan all feature-mounted models, query each provider's pricing API
(e.g., Ollama model card, OpenAI pricing endpoint), and register a live
`CostCalculator` listener.  This enables `res(COST)` to carry real
monetary values for each execution.

---

## Feature Ideas from Dr. Stynx

### `mtron_crawl` — Recursive URI Discovery
**Priority:** Medium.

Manual discovery with `.<<` and `+` is linear.  A `mtron_crawl` instruction
that recursively maps a directory tree up to N levels would collapse
exploration from multiple turns into one pass.

```
*</sys/space/>.crawl(max=>3)
→ [files=>..., spaces=>..., insts=>...]
```

### Structured Ledger with Key-Value Retrieval
**Priority:** Medium.

Currently the ledger is a flat Lst.  As it grows, the agent must read
the entire thing to find an entry.  A keyed Rec structure:

```
<<mtron:ledger>>
[spaces=>[/sys/space/fs/dr, /sys/space/web/http],
 discovered=>2026-07-08T01:00:00]
<</mtron:ledger>>
```

Combined with a `ledger_get("spaces")` accessor would make it scalable
for large projects.

### Loop Error Handling — Branch/Retry
**Priority:** Medium.

Currently if one iteration fails, the loop breaks.  The LLM should be
able to signal a retry strategy:

```
<<mtron:loop>>
[prompt=>"retry the URI probe",
 retry=>3]
<</mtron:loop>>
```

LoopFeature tracks `retryCount` per iteration and re-invokes `chat()`
on failure up to the specified limit.

### `mtron_bridge` — URI Pipeline
**Priority:** Low — requires URI output→input protocol.

Pipe the output of one mtron expression directly into another:

```
*source.+(mtron_bridge=>*target)
```

The bridge reads `res("bridge", "src")` set by a previous execution
and feeds it as input to the target.

### `ledger_snapshot` — Named State Snapshots
**Priority:** Low.

Save the current ledger state under a name so the agent can roll back:

```
<<mtron:ledger>>
[snapshot=>"Baseline_Discovery"]
<</mtron:ledger>>
```

LoopFeature's `preserve` field handles forward-carry, but snapshot
enables backward recovery if the agent overwrites important data.

### `mtron_watch` — Event-Driven Monitoring
**Priority:** Low — requires push infrastructure.

Instead of polling via `delay=>...` loops, the agent subscribes to a URI
and receives notifications when it changes.  This turns the agent into
an autonomous monitor rather than a periodic checker.

---

## Feature Tweaks

### PromptFeature
**Priority:** Low.

A dedicated feature for system prompts.  Currently "you are helpful"
lives on a ChatFeature config entry.  A PromptFeature would own the
prompt lifecycle: versioned, overridable, with the LLM able to update
its own prompt via `<<mtron:prompt>>` blocks.

### NoteFeature / User Interrupts
**Priority:** Low — exploratory.

User-initiated mid-execution interrupts via a note list that the agent
polls via tool call during response generation.  Orthogonal to LoopFeature
but complementary for interactive workflows.  Could be unified with the
`<<plain:note>>` block mechanism if user writes to `res("note")`.

### Loop Preserve Enhancements
**Priority:** Low.

Currently `preserve=>[time, cost]` carries fields forward.  Could be
extended to support transformations (`preserve=>[cost=>sum]`) or
conditional carry (`preserve=>[stages=>if_present]`).

### Multi-Phase Chat Result
**Priority:** Low — speculative.

The result Rec currently captures a single execution's state.  With
LoopFeature, the accumulated `loop_results` trail is already present.
Could expose a `phases` field that merges per-iteration summaries into
a top-level view, similar to how AuditFeature produces a table from
the lifecycle trail.

---

## Design Principles (from this session)

1. **Features are config-only MRecs.**  No private state, no LC4j imports.
   Agent translates mtron → LC4j at execution time.

2. **Blackboard is single source of truth.**  Features push to Agent JVM.
   Agent reads from self.  Never pull from features.

3. **No feature receives privileged status.**  No named getters on Agent.
   `agent.feature("name")` is the only query path.

4. **Skills for interaction models.**  Features with user-facing behavior
   declare `skill()`.  SkillFeature owns discovery.  Features don't
   self-promote via system messages.

5. **`<<TYPE:KEY>>` blocks for LLM signaling.**  No tool call overhead.
   MIME dispatch via `MIMEType.fromBytes()`.  Stripped from chat text.
