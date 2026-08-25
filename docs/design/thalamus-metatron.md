# Thalamus × metatron — Persistent Agent Memory, Natively

**Status:** design analysis — how (and whether) to fold [Thalamus](https://github.com/Ybx-jp/thalamus)'s
memory architecture into metatron. Grounded in the live `/usr/dr` agent memory we exercised via MCP
(`*dr`, `/usr/dr/message/+`, `/usr/dr/concept/+`, `/usr/dr/model`, `/sys/space/usr/dr.as(str::T)`),
plus a source read of both Thalamus (`harness/extraction.py`) and metatron's `ConceptFeature`.
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
The extraction machinery is the biggest surprise: **metatron already has a richer version of
Thalamus's distill step — three pluggable extractors behind one interface — it just emits
concept nouns instead of claim propositions.**

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

### 1.1 How Thalamus actually extracts (from source)

The README says "sessions are distilled into a property graph" without saying how. The source
(`harness/extraction.py`, ~1,140 lines) answers it: **one LLM call per session, over a heavily
pre-filtered digest, merged back into a deterministic stage-1 graph.** No rule-based NLP, no
regex keywords, no embeddings — a four-stage pipeline:

**Stage 1 — deterministic recovery (`transcripts.py`, no model).** Files touched, timestamps,
which tool calls did what — recovered exactly from the transcript. Becomes the `SessionGraph` base.

**Stage 2 — digest rendering (`render_digest`).** The archived transcript flattens into a compact
exchange log: `USER: <text>`, `ASSISTANT: <text>`, `tool: <name> <salient input>`, heavily
truncated `result: <text>` (user text capped 2,000 chars, tool results 400, commands 300).
Sidechains, meta records, and system-injected noise (`<system-reminder>` blocks) are dropped.
External-ingress results (web fetches, searches) are labeled `result [EXTERNAL CONTENT]` — the
label is decided at render time, not by the model. If the digest exceeds 240k chars (~60k
tokens), the **middle is elided** (openings state intent, endings state outcomes).

**Stage 3 — one prompt, one model call (`build_prompt`).** The model runs headlessly (`claude -p`
for Claude Code, Cursor's `agent -p`, codex for codex), riding the operator's existing auth. The
prompt's framing: *"the deterministic facts are already recorded exactly — do NOT re-derive them.
Your job is judgement."* It emits a fenced YAML block:

```yaml
summary: "<1-3 sentence goal + achievement>"
decisions:  [{description, rationale, outcome, artifacts, external}]   # rationale required
problems:   [{description, category, artifacts, external}]
solutions:  [{description, approach, worked, problem_ref, artifacts}]
threads:    [{id, title, description, status, artifacts}]              # "cold" test
thread_refs: [{id, status, notes}]                                     # resolve, don't duplicate
artifacts:  [{identifier, type}]                                       # orphan-rejected
```

Key prompt rules: decisions **must carry a rationale** ("a decision without a rationale is not
worth recording"); threads must pass the **cold test** ("could a session with no access to this
transcript act on it?") and most sessions justify 0-2; the model must **not emit** `session_id`,
`timestamp`, `tool`, `project`, `scope`, `sources`, `touched` — "those are stamped from the
record"; anything resting on fetched web content carries `external: true` ("what a web page
asserts is that page's claim, not this session's lived experience"). The prompt is fed the
session's **existing open threads and known claims** so the model re-asserts a claim by copying
its description *exactly* — the convergence mechanism.

**Stage 4 — merge (`merge_extraction`).** The model's YAML merges INTO the deterministic
`SessionGraph`. Fields the model was told not to emit are overridden even if it emitted them.
Identity, provenance, sources, and touched-files come from the record; the model contributes only
judgement (summary, claims, threads).

**Operational reality:** extraction takes p50 217 s / max 255 s (measured in `console/distill.py`),
runs detached (`nohup` at SessionEnd) because it outlives the tmux window, and a whole status
watchdog module exists just to read the completion log. Claims converge on content-addressed
`(kind, description)` — identical descriptions merge into one node.

---

## 2. The integration fork

### Option A — Bridge it (Thalamus as an external MCP server)

metatron's agent adds `!*thalamus` via `mcp_client::T` — one rec, done. Works today.

**Cost:** a second process (Docker Gremlin Server), a second source of truth *outside* the
router's address space, and the memory becomes a black box at a URL instead of a walkable
subspace. This conflicts with the "everything is a URI-addressable obj" rule we exercised all
session (`mcp_server::T` / `mcp_client::T` are first-class; the `/usr/dr` ledger is queryable via
`eval_mtron`). Bridging buys Thalamus's *product* but abandons metatron's *substrate* — and, as
§3.4 shows, the extraction it buys is a *subset* of what ConceptFeature already does.

### Option B — Absorb the ideas (metatron-native)

metatron already has ~80% of the model, natively. Map:

| Thalamus concept | metatron, today (verified) |
|---|---|
| `Session` node | `/usr/dr/session/1` rec + `session_feature` (message-window algorithm, max 30) |
| `Source` / evidence archive | **The message ledger** — `/usr/dr/message/0..N`, append-only via `incrq`, every entry typed (`user`/`ai`/`thinking`/`tool_request`/`tool_result`/`system`) + timestamped + session/depth/chat_id tagged |
| `Claim` extraction | `concept_feature` — **three extractors** (lucene TF-IDF / agent LLM-translator / manual `<<concept:>>` tags) over one `Extractor` seam; adjacency `concept ⇄ message` via `!*` refs. See §3.4 |
| `Artifact` nodes | The spaces/objs the agent touched (`/usr/dr/model`, files, sql backends) — already first-class URIs |
| Property graph | **`grphSpace`** — `g:#` (local) and `h:#` (kg) mounted; TinkerPop in-process, no Docker |
| Scopes as permission boundary | **Spaces are the scopes** — `/usr/dr/#`, `/usr/marko/#`, `/sys/#`; the router enforces addressing. No tool needs a scope arg because addressing *is* the boundary |
| Cross-session continuation | Already works — Dr. Stynx referenced the "scratch project" from a prior conversation; `chat_id`/sessions survived restarts (SQLite-backed `/usr/dr`) |
| Used-vs-ignored / cost eval | `audit_feature` + `cost_feature` already trace phases, tool executions, and token cost in `chat_result::T` |

The conclusion of the map: the *storage* half of Thalamus is redundant with metatron. The
*workflow* half (distillation → claims with provenance → trust floor → open threads) is the
novel part worth absorbing — and the extraction engine for it already exists.

---

## 3. What's genuinely new — and how it lands

### 3.1 `claim::T` — distilled statements, not keywords

`concept_feature` extracts *nouns* (three ways) and links them to messages. A Thalamus *claim* is
a *proposition* distilled from the session ("the scratch project's writes don't persist because
there's no backing write primitive"), carrying `DERIVED_FROM` refs to its source messages.
**Concept = noun; claim = sentence.**

metatron does NOT need a new extraction engine for this. `ConceptFeature` already exposes the
`Extractor` interface with an **`AgentExtractor`** — a translator agent that LLM-post-processes
text into `<<concept:...>>` tags (see §3.4). A claim layer is *that same seam* with a richer
output schema and a session-end trigger — not a new subsystem.

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

The `AgentExtractor` prompt is *already* a distill prompt ("better to have fewer, highly specific
concepts than many general ones; do not wrap stop words; if no significant concepts, return the
text unchanged"). Extending it to emit YAML claims/threads is the natural evolution: keep the
lucene index as the fast noun layer, promote the agent-mode extractor to the slow semantic layer.
The seam already exists — `on_complete_response` fires per chat completion and the ledger is
already there to read.

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
surface open threads as a system message — "where you left off," as data. This mirrors how
`concept_feature.onBeforeChat` already injects recent concepts into the system message.

### 3.3 Trust tiers as a structural floor

Thalamus's most original idea: trust tiers with `DERIVED_FROM` making effective trust the
*minimum* over the derivation chain — a low-tier source cannot produce a high-tier claim.
In metatron this is a **type predicate**, not storage:

```mtron
claim?tier<=min(sources.tier)     [-- enforced on write, no laundering --]
```

Evaluated structurally at write time (the same mechanism that enforces `?` predicates today).
This is a data-flow rule in the type system, which is exactly where metatron's invariants live.
Thalamus's render-time `[EXTERNAL CONTENT]` label has a direct analog: the digest/render step
decides provenance before the model sees the text — metatron would do the same at the ledger
read, tagging `external` on messages whose tool results were web fetches.

### 3.4 The extraction reality — metatron already has three of Thalamus's one

Thalamus extracts with a **single mechanism**: one LLM call per session over a filtered digest
(§1.1). metatron's `ConceptFeature` (`src/.../llm/type/feature/ConceptFeature.java`) is a
*cleaner decomposition* — a pluggable `Extractor` interface with three implementations, switched
by `extractor => "lucene" | "agent" | "tag"`:

| mechanism | how | cost | role |
|---|---|---|---|
| **`LuceneExtractor`** | in-memory Lucene index (ByteBuffersDirectory, StandardTokenizer → LowerCase → StopFilter); local TF × global IDF, top-10 *per message* | zero (no model) | statistical noun layer; also picks up any explicit tags |
| **`AgentExtractor`** | a separate translator `agent::T` (own model) rewrites the text wrapping `<<concept:...>>` tags; tags regex-parsed back | one LLM call, async on `CoreThread` with a `blocking` knob | LLM-judgement extraction — Thalamus's equivalent |
| **`TaggingExtractor`** | regex-parses `<<concept:...>>` the agent writes deliberately | zero | manual bookmarks; the only path that touches *thoughts* |

Plus two safeguards Thalamus lacks: **spell-correction** toward existing concept names
(`CommonUtil.correctSpelling` — "inteligence" → "intelligence", so typos don't fragment the
graph) and **stopword stripping** on LLM-extracted concepts. All three funnel into one storage
contract — `addConceptsToSpace` builds the `concept ⇄ message` recs with `!*` refs — so the
graph is identical regardless of extractor; only the source of the strings differs.

| axis | Thalamus | ConceptFeature |
|---|---|---|
| mechanisms | one (LLM, per-session) | **three pluggable** (lucene / agent-LLM / tag) |
| granularity | claims+threads+decisions+solutions (YAML) | concept terms → co-location graph |
| cost control | one model call/session, detached | lucene/tag = zero; agent-mode async with `blocking` knob |
| convergence | content-address on `(kind, description)`; copy-exact-wording rule | spell-correct toward existing names + set-merge `conceptLink` |
| provenance | `DERIVED_FROM` edges, `external: true` flag | `concept ⇄ message` back-refs (`auto_from` to message VIDs) |
| trigger | session-end hook, detached | **incremental** — `onBeforeChat` + `onCompleteResponse` per turn |
| graph | TinkerPop property graph (Gremlin Server/Docker) | spaces-as-data, `!*` refs, shared storage contract |

The honest position: **Thalamus is a summary; ConceptFeature is an index.** Thalamus distills the
whole session once into structured propositions; ConceptFeature extracts every turn into an
accumulating co-location graph. They are complementary altitudes — and the claim layer is where
they meet: give `AgentExtractor` a richer output schema (claims/threads with `external`
provenance) and a session-end trigger, reusing the existing `Extractor` seam, the `concept ⇄
message` storage contract, and the blocking/async machinery.

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
- **Thalamus's distillation pipeline itself** — the four-stage render/prompt/parse/merge is a
  subset of what ConceptFeature already provides, minus the pluggability. Adopt its *schema*
  (claims, threads, `external` provenance), not its *pipeline*.
- **The detached-`nohup` + log-watchdog status machine** (`console/distill.py`) — a byproduct of
  extraction outliving its tmux window. metatron's `CoreThread` + async hooks already handle
  fire-and-forget distillation in-process.

---

## 5. Design decisions (and why)

| Decision | Why |
|---|---|
| Absorb, don't bridge | The ledger, concepts, graph space, and feature hooks already provide the substrate; bridging adds a second source of truth outside the router |
| Claims as a *feature hook*, not a service | `on_complete_response` is the existing distill seam; a feature is a rec, composable like the other 11 |
| Claim layer reuses the `Extractor` seam | `AgentExtractor` already does LLM extraction; claims = richer schema + session-end trigger, not a new engine |
| Trust as a type predicate | metatron's invariants live in the type system; `?` predicates already enforce structure at write |
| Keep lucene as the noun layer | Fast, indexable, already live; claims are the slow semantic layer above it |
| Threads anchored per-agent (`/usr/dr/thread`) | Same scoping as the rest of the agent's memory; spaces-as-data composition |
| Provenance decided at read, not by the model | Thalamus labels `[EXTERNAL CONTENT]` at render time; metatron tags `external` on the ledger read, same principle |

---

## 6. Open questions

- **(A) Distill trigger.** Pure `on_complete_response`, or an explicit `distill()` inst the agent
  calls at task end? Thalamus uses a session-end hook; a hybrid (auto on complete + manual) may
  serve both conversational and task modes.
- **(B) Per-turn vs session-end.** ConceptFeature extracts *incrementally* per turn; Thalamus
  distills the *whole session once*. A claim layer could do either — incremental claims feed
  mid-session recall, session-end claims give the Thalamus-style "where you left off." Both, with
  a windowing rule for the session-end pass?
- **(C) Claim output format.** YAML like Thalamus, or mtron-native `<<mtron:claim>>`-style markup
  that the existing `TaggingExtractor` regex + `AgentExtractor` translator can both consume
  unchanged? The latter reuses more machinery.
- **(D) Trust source.** Where do tiers come from initially — message kind (tool_result > thinking
  > ai?) or explicit annotation? The floor predicate needs a tier assignment rule.
- **(E) `grphSpace` residency.** Claims/threads as typed recs in a `memSpace` (like `/usr/dr`),
  or as vertices in `grphSpace` with real `DERIVED_FROM` edges? The former is simplest and
  matches `/usr/dr`; the latter buys graph traversal at the cost of the grphSpace write path.
- **(F) Cross-agent memory.** Currently all under `/usr/dr/`. Thalamus federates by scope — do we
  want `/usr/{agent}/claim` + a shared `/shared/claim` for multi-agent distillation?

---

## 7. Related

- `docs/design/codespaces/agent-harness-spec.md` — the agent IDE harness (disk = source of truth;
  the same "materialized view over a log" principle)
- `src/main/java/studio/phaseshift/metatron/isa/llm/type/feature/ConceptFeature.java` — the three
  extractors (lucene / agent / tag) and the `Extractor` seam claims would extend
- `src/main/java/studio/phaseshift/metatron/isa/llm/type/Agent.java` — feature dispatch, the
  `on_complete_response` hook seam
- `src/main/java/studio/phaseshift/metatron/isa/grph/` — `grphSpace`, the in-process TinkerPop store
- [Thalamus repo](https://github.com/Ybx-jp/thalamus) — reference for the claims/threads/trust model;
  `src/thalamus/harness/extraction.py` is the distillation pipeline (render → prompt → parse → merge)
