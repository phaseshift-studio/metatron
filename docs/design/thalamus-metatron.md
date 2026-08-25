# Thalamus × metatron — Persistent Agent Memory, Natively

**Status:** design analysis — how (and whether) to fold [Thalamus](https://github.com/Ybx-jp/thalamus)'s
memory architecture into metatron. Grounded in the live `/usr/dr` agent memory we exercised via MCP
(`*dr`, `/usr/dr/message/+`, `/usr/dr/concept/+`, `/usr/dr/model`, `/sys/space/usr/dr.as(str::T)`).
Items marked **OPEN** are unresolved.

---

## 0. Purpose

Bring metatron's agent memory from *persistent message ledger + keyword concepts* to
*distilled claims with provenance and trust* — the ideas Thalamus is built on — **without
adopting its stack**. Thalamus runs a Dockerized Gremlin Server as an external property graph;
metatron already has the substrate (append-only ledger, `grphSpace`, feature hooks) natively.
The question is what to absorb, not what to bridge.

**Thesis:** metatron shouldn't *integrate* Thalamus; it should *re-realize* its remaining ideas
(claims, threads, trust tiers) as spaces-as-data, and throw away the parts that solve problems
metatron doesn't have (external graph server, out-of-band evidence log, per-process scoping).

---

## 1. What Thalamus is

"Persistent, auditable memory for coding agents." Key design bets (from the repo):

| Bet | Meaning |
|---|---|
| **Property graph, not vector store** | Sessions distill into a TinkerPop property graph; no embeddings/chunks |
| **Provenance on every node** | Nodes are `Session` / `Claim` / `Thread` / `Source` / `Artifact`, all carrying scope + derivation |
| **Trust is structural** | `DERIVED_FROM` edges; effective trust = *floor* over the derivation chain — "distillation does not launder" |
| **Federation via scopes** | Expert subgraphs; a session pins to exactly one scope; scope = schema + permission + trust boundary |
| **Graph = materialized view over an immutable log** | Evidence archive lives *outside* the graph (content-addressed); "re-extract, never migrate" |
| **Distill at session end** | Hook turns the retained transcript into claims + open threads; next session resumes where you left off |
| **No scope-widening** | No tool accepts a scope arg — a model can't widen its own view by asking |

Two operational notes: the MCP server is per-process (scope read from env at startup), and an eval
loop trace-taps memory-tool calls to judge used-vs-ignored and price cost.

---

## 2. The integration fork

### Option A — Bridge it (Thalamus as an external MCP server)

metatron's agent adds `!*thalamus` via `mcp_client::T` — one rec, done. Works today.

**Cost:** a second process (Docker Gremlin Server), a second source of truth *outside* the
router's address space, and the memory becomes a black box at a URL instead of a walkable
subspace. This conflicts with the "everything is a URI-addressable obj" rule we exercised all
session (`mcp_server::T` / `mcp_client::T` are first-class; the `/usr/dr` ledger is queryable via
`eval_mtron`). Bridging buys Thalamus's *product* but abandons metatron's *substrate*.

### Option B — Absorb the ideas (metatron-native)

metatron already has ~80% of the model, natively. Map:

| Thalamus concept | metatron, today (verified) |
|---|---|
| `Session` node | `/usr/dr/session/1` rec + `session_feature` (message-window algorithm, max 30) |
| `Source` / evidence archive | **The message ledger** — `/usr/dr/message/0..N`, append-only via `incrq`, every entry typed (`user`/`ai`/`thinking`/`tool_request`/`tool_result`/`system`) + timestamped + session/depth/chat_id tagged |
| `Claim` extraction | `concept_feature` — lucene extraction at `/usr/dr/concept`, adjacency index `concept ⇄ message` via `!*` lazy refs |
| `Artifact` nodes | The spaces/objs the agent touched (`/usr/dr/model`, files, sql backends) — already first-class URIs |
| Property graph | **`grphSpace`** — `g:#` (local) and `h:#` (kg) mounted; TinkerPop in-process, no Docker |
| Scopes as permission boundary | **Spaces are the scopes** — `/usr/dr/#`, `/usr/marko/#`, `/sys/#`; the router enforces addressing. No tool needs a scope arg because addressing *is* the boundary |
| Cross-session continuation | Already works — Dr. Stynx referenced the "scratch project" from a prior conversation; `chat_id`/sessions survived restarts (SQLite-backed `/usr/dr`) |
| Used-vs-ignored / cost eval | `audit_feature` + `cost_feature` already trace phases, tool executions, and token cost in `chat_result::T` |

The conclusion of the map: the *storage* half of Thalamus is redundant with metatron. The
*workflow* half (distillation → claims with provenance → trust floor → open threads) is the
novel part worth absorbing.

---

## 3. What's genuinely new — and how it lands

### 3.1 `claim::T` — distilled statements, not keywords

`concept_feature` extracts *nouns* (lucene keywords) and links them to messages. A Thalamus
*claim* is a *proposition* distilled from the session ("the scratch project's writes don't
persist because there's no backing write primitive"), carrying `DERIVED_FROM` refs to its source
messages. **Concept = noun; claim = sentence.**

Proposal — a `claim::T` anchored in a claim space:

```mtron
claim::T[?[
  text   => str::T,
  source => {uri::T},              [-- message vids it was distilled from --]
  tier   => nat::T]]@/m/llm/memory/claim
```

Distillation is a **feature hook**, same shape as the others (a rec of insts on the agent):

```mtron
distill_feature::[
  root  => /usr/dr/claim,
  on_complete_response => inst(a=>str::T){
    [-- walk /usr/dr/message/+, LLM-distill into claims, link DERIVED_FROM --] }]
```

This is the natural evolution of the current `concept_feature`: keep the lucene index as the
fast noun layer, add a slow, model-driven claim layer on top. The seam already exists —
`on_complete_response` fires per chat completion and the ledger is already there to read.

### 3.2 `thread::T` — open problems carried across sessions

metatron has `loop_feature` (within-session iteration) but no explicit *open-problem* node that
survives a session boundary. The continuation behavior already *emerges* (the agent remembered
the scratch project); making it a first-class structure turns an emergent behavior into a
queryable one.

```mtron
thread::T[?[
  title      => str::T,
  status     => enum::T[open, closed],
  last_claim => {uri::T},
  touched    => datetime::T]]@/m/llm/memory/thread
```

The `session_feature.on_agent_ctor` hook is the natural resume point: on agent construction,
surface open threads as a system message — "where you left off," as data.

### 3.3 Trust tiers as a structural floor

Thalamus's most original idea: trust tiers with `DERIVED_FROM` making effective trust the
*minimum* over the derivation chain — a low-tier source cannot produce a high-tier claim.
In metatron this is a **type predicate**, not storage:

```mtron
claim?tier<=min(sources.tier)     [-- enforced on write, no laundering --]
```

Evaluated structurally at write time (the same mechanism that enforces `?` predicates today).
This is a data-flow rule in the type system, which is exactly where metatron's invariants live.

---

## 4. What to explicitly NOT adopt

- **Gremlin Server in Docker.** `grphSpace` is already TinkerPop in-process; an external graph
  server duplicates the store and splits the address space.
- **Out-of-band evidence archive.** Thalamus keeps the log *outside* the graph because its graph
  is a derived view. metatron's ledger is already *inside* the address space and queryable by the
  same machinery — a strictly stronger position (re-extract = re-deref).
- **Per-process scope from env.** metatron's scopes are addressable URIs the router enforces;
  no process-level pinning needed.
- **Vector store** — neither Thalamus nor metatron uses one; no change.

---

## 5. Design decisions (and why)

| Decision | Why |
|---|---|
| Absorb, don't bridge | The ledger, concepts, graph space, and feature hooks already provide the substrate; bridging adds a second source of truth outside the router |
| Claims as a *feature hook*, not a service | `on_complete_response` is the existing distill seam; a feature is a rec, composable like the other 11 |
| Trust as a type predicate | metatron's invariants live in the type system; `?` predicates already enforce structure at write |
| Keep `concept_feature` as the noun layer | Fast, indexable, already live; claims are the slow semantic layer above it |
| Threads anchored per-agent (`/usr/dr/thread`) | Same scoping as the rest of the agent's memory; spaces-as-data composition |

---

## 6. Open questions

- **(A) Distill trigger.** Pure `on_complete_response`, or an explicit `distill()` inst the agent
  calls at task end? Thalamus uses a session-end hook; a hybrid (auto on complete + manual) may
  serve both conversational and task modes.
- **(B) Claim extraction model.** The 47 s local Ollama model is fine for a background distill,
  but a session with 100+ messages wants batching/dedup. Window the ledger, or distill per-thread?
- **(C) Trust source.** Where do tiers come from initially — message kind (tool_result > thinking
  > ai?) or explicit annotation? The floor predicate needs a tier assignment rule.
- **(D) `grphSpace` residency.** Claims/threads as typed recs in a `memSpace` (like `/usr/dr`),
  or as vertices in `grphSpace` with real `DERIVED_FROM` edges? The former is simplest and
  matches `/usr/dr`; the latter buys graph traversal at the cost of the grphSpace write path.
- **(E) Cross-agent memory.** Currently all under `/usr/dr/`. Thalamus federates by scope — do we
  want `/usr/{agent}/claim` + a shared `/shared/claim` for multi-agent distillation?

---

## 7. Related

- `docs/design/codespaces/agent-harness-spec.md` — the agent IDE harness (disk = source of truth;
  the same "materialized view over a log" principle)
- `src/main/java/studio/phaseshift/metatron/isa/llm/type/feature/conceptFeature.java` — current
  noun extraction; the layer claims would sit above
- `src/main/java/studio/phaseshift/metatron/isa/llm/type/Agent.java` — feature dispatch, the
  `on_complete_response` hook seam
- `src/main/java/studio/phaseshift/metatron/isa/grph/` — `grphSpace`, the in-process TinkerPop store
- [Thalamus repo](https://github.com/Ybx-jp/thalamus) — reference for the claims/threads/trust model
