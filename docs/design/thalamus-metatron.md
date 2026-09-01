# Thalamus × metatron — Persistent Agent Memory, Natively

**Status:** design analysis → **partially implemented and verified
live**. [Thalamus](https://github.com/Ybx-jp/thalamus)'s claims model is now working in metatron: `claim::T` is
registered, `summarize()` distills a session into claims via `Agent.Helper.miniTask`, and the claims read back from the
SQLite `/usr/dr` space with `source => {!*message-vids}` provenance. Grounded in the live `/usr/dr` agent memory we
exercised via MCP (`*dr`, `/usr/dr/message/+`, `/usr/dr/concept/+`, `/usr/dr/model`, `/sys/space/usr/dr.as(str::T)`),
plus a source read of both Thalamus (`harness/extraction.py`) and metatron's `ConceptFeature`. Items marked **OPEN** are
unresolved; items marked **[DONE]** are implemented in `llmInstSet`.

---

## 0. Purpose

Bring metatron's agent memory from *persistent message ledger + keyword concepts* to *distilled claims with provenance
and trust* — the ideas Thalamus is built on — **without adopting its stack**. Thalamus runs a Dockerized Gremlin Server
as an external property graph; metatron already has the substrate (append-only ledger, `grphSpace`, feature hooks)
natively. The question is what to absorb, not what to bridge.

**Thesis:** metatron shouldn't *integrate* Thalamus; it should *re-realize* its remaining ideas (claims, threads, trust
tiers) as spaces-as-data, and throw away the parts that solve problems metatron doesn't have (external graph server,
out-of-band evidence log, per-process scoping). The extraction machinery is the biggest surprise: **metatron already has
a richer version of Thalamus's distill step — three pluggable extractors behind one interface — it just emits concept
nouns instead of claim propositions.**

---

## 1. What Thalamus is

"Persistent, auditable memory for coding agents." Key design bets (from the repo):

| Bet                                                 | Meaning                                                                                                     |
|-----------------------------------------------------|-------------------------------------------------------------------------------------------------------------|
| **Property graph, not vector store**                | Sessions distill into a TinkerPop property graph; no embeddings/chunks                                      |
| **Provenance on every node**                        | Nodes are `Session` / `Claim` / `Thread` / `Source` / `Artifact`, all carrying scope + derivation           |
| **Trust is structural**                             | `DERIVED_FROM` edges; effective trust = *floor* over the derivation chain — "distillation does not launder" |
| **Federation via scopes**                           | Expert subgraphs; a session pins to exactly one scope; scope = schema + permission + trust boundary         |
| **Graph = materialized view over an immutable log** | Evidence archive lives *outside* the graph (content-addressed); "re-extract, never migrate"                 |
| **Distill at session end**                          | Hook turns the retained transcript into claims + open threads; next session resumes where you left off      |
| **No scope-widening**                               | No tool accepts a scope arg — a model can't widen its own view by asking                                    |

Two operational notes: the MCP server is per-process (scope read from env at startup), and an eval loop trace-taps
memory-tool calls to judge used-vs-ignored and price cost.

### 1.1 How Thalamus actually extracts (from source)

The README says "sessions are distilled into a property graph" without saying how. The source
(`harness/extraction.py`, ~1,140 lines) answers it: **one LLM call per session, over a heavily pre-filtered digest,
merged back into a deterministic stage-1 graph.** No rule-based NLP, no regex keywords, no embeddings — a four-stage
pipeline:

**Stage 1 — deterministic recovery (`transcripts.py`, no model).** Files touched, timestamps, which tool calls did
what — recovered exactly from the transcript. Becomes the `SessionGraph` base.

**Stage 2 — digest rendering (`render_digest`).** The archived transcript flattens into a compact exchange log:
`USER: <text>`, `ASSISTANT: <text>`, `tool: <name> <salient input>`, heavily truncated `result: <text>` (user text
capped 2,000 chars, tool results 400, commands 300). Sidechains, meta records, and system-injected noise
(`<system-reminder>` blocks) are dropped. External-ingress results (web fetches, searches) are labeled
`result [EXTERNAL CONTENT]` — the label is decided at render time, not by the model. If the digest exceeds 240k chars (~
60k tokens), the **middle is elided** (openings state intent, endings state outcomes).

**Stage 3 — one prompt, one model call (`build_prompt`).** The model runs headlessly (`claude -p`
for Claude Code, Cursor's `agent -p`, codex for codex), riding the operator's existing auth. The prompt's framing: *"the
deterministic facts are already recorded exactly — do NOT re-derive them. Your job is judgement."* It emits a fenced
YAML block:

```yaml
summary: "<1-3 sentence goal + achievement>"
decisions:  [{description, rationale, outcome, artifacts, external}]   # rationale required
problems:   [{description, category, artifacts, external}]
solutions:  [{description, approach, worked, problem_ref, artifacts}]
threads:    [{id, title, description, status, artifacts}]              # "cold" test
thread_refs: [{id, status, notes}]                                     # resolve, don't duplicate
artifacts:  [{identifier, type}]                                       # orphan-rejected
```

Key prompt rules: decisions **must carry a rationale** ("a decision without a rationale is not worth recording");
threads must pass the **cold test** ("could a session with no access to this transcript act on it?") and most sessions
justify 0-2; the model must **not emit** `session_id`,
`timestamp`, `tool`, `project`, `scope`, `sources`, `touched` — "those are stamped from the record"; anything resting on
fetched web content carries `external: true` ("what a web page asserts is that page's claim, not this session's lived
experience"). The prompt is fed the session's **existing open threads and known claims** so the model re-asserts a claim
by copying its description *exactly* — the convergence mechanism.

**Stage 4 — merge (`merge_extraction`).** The model's YAML merges INTO the deterministic
`SessionGraph`. Fields the model was told not to emit are overridden even if it emitted them. Identity, provenance,
sources, and touched-files come from the record; the model contributes only judgement (summary, claims, threads).

**Operational reality:** extraction takes p50 217 s / max 255 s (measured in `console/distill.py`), runs detached
(`nohup` at SessionEnd) because it outlives the tmux window, and a whole status watchdog module exists just to read the
completion log. Claims converge on content-addressed
`(kind, description)` — identical descriptions merge into one node.

---

## 2. The integration fork

### Option A — Bridge it (Thalamus as an external MCP server)

metatron's agent adds `!*thalamus` via `mcp_client::T` — one rec, done. Works today.

**Cost:** a second process (Docker Gremlin Server), a second source of truth *outside* the router's address space, and
the memory becomes a black box at a URL instead of a walkable subspace. This conflicts with the "everything is a
URI-addressable obj" rule we exercised all session (`mcp_server::T` / `mcp_client::T` are first-class; the `/usr/dr`
ledger is queryable via
`eval_mtron`). Bridging buys Thalamus's *product* but abandons metatron's *substrate* — and, as §3.4 shows, the
extraction it buys is a *subset* of what ConceptFeature already does.

### Option B — Absorb the ideas (metatron-native)

metatron already has ~80% of the model, natively. Map:

| Thalamus concept              | metatron, today (verified)                                                                                                                                                                              |
|-------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Session` node                | `/usr/dr/session/1` rec + `session_feature` (message-window algorithm, max 30)                                                                                                                          |
| `Source` / evidence archive   | **The message ledger** — `/usr/dr/message/0..N`, append-only via `incrq`, every entry typed (`user`/`ai`/`thinking`/`tool_request`/`tool_result`/`system`) + timestamped + session/depth/chat_id tagged |
| `Claim` extraction            | `concept_feature` — **three extractors** (lucene TF-IDF / agent LLM-translator / manual `<<concept:>>` tags) over one `Extractor` seam; adjacency `concept ⇄ message` via `!*` refs. See §3.4           |
| `Artifact` nodes              | The spaces/objs the agent touched (`/usr/dr/model`, files, sql backends) — already first-class URIs                                                                                                     |
| Property graph                | **`grphSpace`** — `g:#` (local) and `h:#` (kg) mounted; TinkerPop in-process, no Docker                                                                                                                 |
| Scopes as permission boundary | **Spaces are the scopes** — `/usr/dr/#`, `/usr/marko/#`, `/sys/#`; the router enforces addressing. No tool needs a scope arg because addressing *is* the boundary                                       |
| Cross-session continuation    | Already works — Dr. Stynx referenced the "scratch project" from a prior conversation; `chat_id`/sessions survived restarts (SQLite-backed `/usr/dr`)                                                    |
| Used-vs-ignored / cost eval   | `audit_feature` + `cost_feature` already trace phases, tool executions, and token cost in `chat_result::T`                                                                                              |

The conclusion of the map: the *storage* half of Thalamus is redundant with metatron. The *workflow* half
(distillation → claims with provenance → trust floor → open threads) is the novel part worth absorbing — and the
extraction engine for it already exists.

---

## 3. What's genuinely new — and how it lands

### 3.1 `claim::T` — distilled statements, not keywords

`concept_feature` extracts *nouns* (three ways) and links them to messages. A Thalamus *claim* is a *proposition*
distilled from the session ("the scratch project's writes don't persist because there's no backing write primitive"),
carrying `DERIVED_FROM` refs to its source messages. **Concept = noun; claim = sentence.**

metatron does NOT need a new extraction engine for this. `ConceptFeature` already exposes the
`Extractor` interface with an **`AgentExtractor`** — a translator agent that LLM-post-processes text into
`<<concept:...>>` tags (see §3.4). A claim layer is *that same seam* with a richer output schema and a session-end
trigger — not a new subsystem.

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

The `AgentExtractor` prompt is *already* a distill prompt ("better to have fewer, highly specific concepts than many
general ones; do not wrap stop words; if no significant concepts, return the text unchanged"). Extending it to emit YAML
claims/loose ends is the natural evolution: keep the lucene index as the fast noun layer, promote the agent-mode
extractor to the slow semantic layer. The seam already exists — `on_complete_response` fires per chat completion and the
ledger is already there to read.

### 3.2 `loose_end::T` — open problems carried across sessions (Thalamus's "Thread")

**Name collision, resolved.** metatron already owns `thread::T` — the *execution* thread at
`/m/mach/thread` (with `core` and `virtual` subtypes; `CoreThread` / `VirtualThread` run the agent's async hooks).
Thalamus's "Thread" is a different thing — an *open problem*, not a unit of execution — so the metatron-native type
takes a synonym: **`loose_end::T`**. A loose end is
"a loose end threaded through history" — the metaphor Thalamus's name reaches for, without clobbering the VM's thread
type.

metatron has `loop_feature` (within-session iteration) but no explicit *open-problem* node that survives a session
boundary. The continuation behavior already *emerges* (the agent remembered the scratch project); making it a
first-class structure turns an emergent behavior into a queryable one.

```mtron
loose_end::T[?[
  title   => str::T,
  desc    => str::T,
  status  => enum::T[open, in_progress, resolved, abandoned],
  claim   => {uri::T},                         [-- the claims that define/resolve it --]
  touched => datetime::T]]@/usr/dr/loose_end
```

Thalamus's reopen rule carries over: a prematurely-closed loose end comes back under the *same*
id (`open` is a settable status) — a duplicate id would hide the reopening, and how often a close does not hold is the
only check on closes being made too easily.

The `session_feature.on_agent_ctor` hook is the natural resume point: on agent construction, surface open loose ends as
a system message — "where you left off," as data. This mirrors how
`concept_feature.onBeforeChat` already injects recent concepts into the system message.

### 3.3 Trust tiers as a structural floor

Thalamus's most original idea: trust tiers with `DERIVED_FROM` making effective trust the *minimum* over the derivation
chain — a low-tier source cannot produce a high-tier claim. In metatron this is a **type predicate**, not storage:

```mtron
claim?tier<=min(sources.tier)     [-- enforced on write, no laundering --]
```

Evaluated structurally at write time (the same mechanism that enforces `?` predicates today). This is a data-flow rule
in the type system, which is exactly where metatron's invariants live. Thalamus's render-time `[EXTERNAL CONTENT]` label
has a direct analog: the digest/render step decides provenance before the model sees the text — metatron would do the
same at the ledger read, tagging `external` on messages whose tool results were web fetches.

### 3.4 The extraction reality — metatron already has three of Thalamus's one

Thalamus extracts with a **single mechanism**: one LLM call per session over a filtered digest (§1.1). metatron's
`ConceptFeature` (`src/.../llm/type/feature/ConceptFeature.java`) is a *cleaner decomposition* — a pluggable `Extractor`
interface with three implementations, switched by `extractor => "lucene" | "agent" | "tag"`:

| mechanism              | how                                                                                                                                    | cost                                                       | role                                                    |
|------------------------|----------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------|---------------------------------------------------------|
| **`LuceneExtractor`**  | in-memory Lucene index (ByteBuffersDirectory, StandardTokenizer → LowerCase → StopFilter); local TF × global IDF, top-10 *per message* | zero (no model)                                            | statistical noun layer; also picks up any explicit tags |
| **`AgentExtractor`**   | a separate translator `agent::T` (own model) rewrites the text wrapping `<<concept:...>>` tags; tags regex-parsed back                 | one LLM call, async on `CoreThread` with a `blocking` knob | LLM-judgement extraction — Thalamus's equivalent        |
| **`TaggingExtractor`** | regex-parses `<<concept:...>>` the agent writes deliberately                                                                           | zero                                                       | manual bookmarks; the only path that touches *thoughts* |

Plus two safeguards Thalamus lacks: **spell-correction** toward existing concept names (`CommonUtil.correctSpelling` —
"inteligence" → "intelligence", so typos don't fragment the graph) and **stopword stripping** on LLM-extracted concepts.
All three funnel into one storage contract — `addConceptsToSpace` builds the `concept ⇄ message` recs with `!*` refs —
so the graph is identical regardless of extractor; only the source of the strings differs.

| axis         | Thalamus                                                          | ConceptFeature                                                   |
|--------------|-------------------------------------------------------------------|------------------------------------------------------------------|
| mechanisms   | one (LLM, per-session)                                            | **three pluggable** (lucene / agent-LLM / tag)                   |
| granularity  | claims+threads+decisions+solutions (YAML)                         | concept terms → co-location graph                                |
| cost control | one model call/session, detached                                  | lucene/tag = zero; agent-mode async with `blocking` knob         |
| convergence  | content-address on `(kind, description)`; copy-exact-wording rule | spell-correct toward existing names + set-merge `conceptLink`    |
| provenance   | `DERIVED_FROM` edges, `external: true` flag                       | `concept ⇄ message` back-refs (`auto_from` to message VIDs)      |
| trigger      | session-end hook, detached                                        | **incremental** — `onBeforeChat` + `onCompleteResponse` per turn |
| graph        | TinkerPop property graph (Gremlin Server/Docker)                  | spaces-as-data, `!*` refs, shared storage contract               |

The honest position: **Thalamus is a summary; ConceptFeature is an index.** Thalamus distills the whole session once
into structured propositions; ConceptFeature extracts every turn into an accumulating co-location graph. They are
complementary altitudes — and the claim layer is where they meet: give `AgentExtractor` a richer output schema
(claims/loose ends with `external`
provenance) and a session-end trigger, reusing the existing `Extractor` seam, the `concept ⇄
message` storage contract, and the blocking/async machinery.

---

## 4. What to explicitly NOT adopt

- **Gremlin Server in Docker.** `grphSpace` is already TinkerPop in-process; an external graph server duplicates the
  store and splits the address space.
- **Out-of-band evidence archive.** Thalamus keeps the log *outside* the graph because its graph is a derived view.
  metatron's ledger is already *inside* the address space and queryable by the same machinery — a strictly stronger
  position (re-extract = re-deref).
- **Per-process scope from env.** metatron's scopes are addressable URIs the router enforces; no process-level pinning
  needed.
- **Vector store** — neither Thalamus nor metatron uses one; no change.
- **Thalamus's distillation pipeline itself** — the four-stage render/prompt/parse/merge is a subset of what
  ConceptFeature already provides, minus the pluggability. Adopt its *schema*
  (claims, threads, `external` provenance), not its *pipeline*.
- **The detached-`nohup` + log-watchdog status machine** (`console/distill.py`) — a byproduct of extraction outliving
  its tmux window. metatron's `CoreThread` + async hooks already handle fire-and-forget distillation in-process.

---

## 5. Design decisions (and why)

| Decision                                            | Why                                                                                                                                           |
|-----------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------|
| Absorb, don't bridge                                | The ledger, concepts, graph space, and feature hooks already provide the substrate; bridging adds a second source of truth outside the router |
| Claims as a *feature hook*, not a service           | `on_complete_response` is the existing distill seam; a feature is a rec, composable like the other 11                                         |
| Claim layer reuses the `Extractor` seam             | `AgentExtractor` already does LLM extraction; claims = richer schema + session-end trigger, not a new engine                                  |
| Trust as a type predicate                           | metatron's invariants live in the type system; `?` predicates already enforce structure at write                                              |
| Keep lucene as the noun layer                       | Fast, indexable, already live; claims are the slow semantic layer above it                                                                    |
| Loose ends anchored per-agent (`/usr/dr/loose_end`) | Same scoping as the rest of the agent's memory; spaces-as-data composition                                                                    |
| Provenance decided at read, not by the model        | Thalamus labels `[EXTERNAL CONTENT]` at render time; metatron tags `external` on the ledger read, same principle                              |

---

## 6. Open questions

- **(A) Distill trigger.** Pure `on_complete_response`, or an explicit `distill()` inst the agent calls at task end?
  Thalamus uses a session-end hook; a hybrid (auto on complete + manual) may serve both conversational and task modes.
- **(B) Per-turn vs session-end.** ConceptFeature extracts *incrementally* per turn; Thalamus distills the *whole
  session once*. A claim layer could do either — incremental claims feed mid-session recall, session-end claims give the
  Thalamus-style "where you left off." Both, with a windowing rule for the session-end pass?
- **(C) Claim output format.** YAML like Thalamus, or mtron-native `<<mtron:claim>>`-style markup that the existing
  `TaggingExtractor` regex + `AgentExtractor` translator can both consume unchanged? The latter reuses more machinery.
- **(D) Trust source.** Where do tiers come from initially — message kind (tool_result > thinking
  > ai?) or explicit annotation? The floor predicate needs a tier assignment rule.
- **(E) `grphSpace` residency.** Claims/loose ends as typed recs in a `memSpace` (like `/usr/dr`), or as vertices in
  `grphSpace` with real `DERIVED_FROM` edges? The former is simplest and matches `/usr/dr`; the latter buys graph
  traversal at the cost of the grphSpace write path. The `loose_end` reopen-under-same-id rule argues for a space
  (write-idempotent) over a graph (edge-heavy).
- **(F) Cross-agent memory.** Currently all under `/usr/dr/`. Thalamus federates by scope — do we want
  `/usr/{agent}/claim` + a shared `/shared/claim` for multi-agent distillation?

---

## 7. Related

- `docs/design/codespaces/agent-harness-spec.md` — the agent IDE harness (disk = source of truth; the same "materialized
  view over a log" principle)
- `src/main/java/studio/phaseshift/metatron/isa/llm/type/feature/ConceptFeature.java` — the three extractors (lucene /
  agent / tag) and the `Extractor` seam claims would extend
- `src/main/java/studio/phaseshift/metatron/isa/llm/type/Agent.java` — feature dispatch, the
  `on_complete_response` hook seam
- `src/main/java/studio/phaseshift/metatron/isa/grph/` — `grphSpace`, the in-process TinkerPop store
- [Thalamus repo](https://github.com/Ybx-jp/thalamus) — reference for the claims/threads/trust model;
  `src/thalamus/harness/extraction.py` is the distillation pipeline (render → prompt → parse → merge)

---

## 8. The SummarizeFeature design

The concrete shape that pulls §3 through §6 into one implementable feature. SummarizeFeature is a feature hook like the
other 11 — a rec of insts on the agent — that adds the two memory types, the `summarize()` trigger, and the retrieval
hook.

### 8.1 The types

```mtron
claim::T[?[
  text     => str::T,                          [-- the proposition --]
  kind     => enum::T[decision, problem, solution, observation],
  source   => {uri::T},                        [-- message vids distilled from (DERIVED_FROM) --]
  concept  => {uri::T},                        [-- links into the concept graph --]
  tier     => nat::T,                          [-- trust floor source --]
  external => bool::T]]@/usr/dr/claim          [-- web-derived provenance --]

loose_end::T[?[
  title   => str::T,
  desc    => str::T,
  status  => enum::T[open, in_progress, resolved, abandoned],
  claim   => {uri::T},                         [-- the claims that define/resolve it --]
  touched => datetime::T]]@/usr/dr/loose_end
```

Two types, not Thalamus's four (decisions/problems/solutions collapse into `claim::T[?kind=>...]`). The session-level
summary is a `summary` field on the existing `session::T`, not a third type. Both types register in the space's
`schema/type` automatically.

### 8.2 The `summarize()` inst

`*dr/session/1.summarize()` — an inst anchored at the session (thus a tool via the MCP route):

```mtron
inst?{uri::T}<=session::T(){
  [-- 1. collect this session's messages: /usr/dr/message/+ filtered by session vid --]
  [-- 2. run AgentExtractor with the distill prompt (§8.4) --]
  [-- 3. parse -> claim::T recs, loose_end::T recs --]
  [-- 4. anchor each claim at /usr/dr/claim/<n>, source => {message vids} --]
  [-- 5. link claims -> concepts (spell-correct toward existing names) --]
  [-- 6. update session::T summary + touched --]
}@summarize
```

Links are four-way: `source` → message vids (provenance), `concept` → the concept graph (so
`concepts(c1)` / `messages(c1)` retrieval finds claims), `loose_end.claim` → defining claims,
`session.summary` → the session. Without the `concept` link, claims and concepts are two disconnected graphs; with it,
the existing retrieval machinery gets richer for free.

### 8.3 The consume side — the point of the feature

- **`on_agent_ctor` retrieval hook:** read `/usr/dr/loose_end` where `status=>open`, inject into the system message (
  "where you left off, as data"). This is Thalamus's "served into the next session's entrypoint" — without it, the
  feature is write-only.
- **Auto-trigger alongside the manual inst:** `on_complete_response` fires `summarize()` on session-close, so the last
  claim gets resolved without the agent remembering to call it.
- **MCP surface:** `claims(c1, c2)`, `loose_ends()`, `summary()` become queryable tools on the drstynx route — usable
  from Claude Code directly.

### 8.4 The metatron-adapted distill prompt

Thalamus's `_PROMPT_TEMPLATE` (§1.1), adapted: digest comes from `/usr/dr/message/+` instead of a rendered archive;
`{known_claims}` / `{open_loose_ends}` are derefs into the spaces; the four YAML sections collapse into a `kind` enum;
`external` is decided at the ledger read (message provenance), not by the model.

```text
You are distilling graph memory from a PAST metatron session. The deterministic facts (which
messages were exchanged, when, which tools ran) are already in the ledger — do NOT re-derive
them. Your job is judgement: what was decided and why, what went wrong, how it was fixed, and
what is still owed. Output ONLY a fenced YAML block conforming to the schema below. Be terse —
1-3 sentences per description.

Rules:
1. claims — propositions. A decision without a rationale is not worth recording.
2. loose_ends — a continuation point a DIFFERENT session could pick up COLD. The test: could
   a session with no access to this transcript act on it? Most sessions justify 0-2. If you
   are writing a third, you are recording rather than continuing.
3. loose_end_refs — if this session continued or resolved an EXISTING OPEN LOOSE END below,
   reference it by its exact id with the new status. Reopen under the same id, never respawn.
4. Convergence: claims are content-addressed on (kind, description). If you re-assert one of
   the KNOWN CLAIMS below, copy its description EXACTLY. Only reword when the assertion differs.
5. Do NOT emit session_id, timestamp, message vids, or tier — those are stamped from the record.
6. Anything resting on fetched external content (messages marked external) carries
   external: true. What a web page asserts is that page's claim, not this session's experience.
   Claims about what the agent DID with such content stay first-party.

Schema:
summary: "<1-3 sentence summary>"
claims:
  - description: "<proposition>"
    kind: "decision|problem|solution|observation"
    rationale: "<why, for decisions>"
    concept: ["<existing concept names>"]
    external: false
loose_ends:
  - id: "<stable-slug>"
    title: "<short actionable title>"
    description: "<what needs to happen and why>"
    status: "open"
loose_end_refs:
  - id: "<existing loose end id>"
    status: "open|in_progress|resolved|abandoned"
    notes: "<progress made>"

### Existing open loose ends
{open_loose_ends}
### Known claims (re-assert by copying the description exactly)
{known_claims}
### Session metadata
Session: {session_vid}  Agent: {agent_vid}
### Ledger digest
{digest}
```

### 8.5 Design decisions (and why)

| Decision                                       | Why                                                                                                                                                     |
|------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------|
| `loose_end::T`, not `thread::T`                | `thread::T` is already the VM's execution thread (`/m/mach/thread`, `CoreThread`/`VirtualThread`); a second `thread::T` would collide in the type space |
| `kind` enum, not four types                    | decisions/problems/solutions are structurally identical; one type + `?kind=>...` filter                                                                 |
| Claims link to concepts, not just messages     | keeps the two memory altitudes on one query surface; `concepts(c1)` finds claims                                                                        |
| Summary is a field on `session::T`, not a type | it's session-scoped metadata; a third type is over-modeling                                                                                             |
| `summarize()` is an inst (→ MCP tool)          | reuses the agent-as-server route; any client can trigger distillation                                                                                   |
| Retrieval hook in `on_agent_ctor`              | the whole point is feeding the next session; write-only memory is not memory                                                                            |

### 8.6 Open questions

- **(G) Distill granularity.** Per-turn incremental (like ConceptFeature) vs session-end one-shot (like Thalamus)?
  Incremental feeds mid-session recall; one-shot gives the "where you left off."
- **(H) Claim output format.** YAML (Thalamus) vs mtron-native `<<mtron:claim>>` markup the existing `TaggingExtractor`
  regex + `AgentExtractor` translator can both consume unchanged? The latter reuses more machinery.
- **(I) `loose_end` status transitions.** Who sets `in_progress` / `resolved`? The summarizing session's `thread_refs`,
  or an explicit agent action mid-session? Thalamus only sets status at distill time; metatron could support live
  transitions via `loose_end` derefs.

---

## 9. Implementation status — what's shipped (2026-08-25)

**This is a verified-live report, not design.** The `claim::T` + `loose_end::T` + `summarize()`
core of the plan is implemented in `llmInstSet.java` and exercised against the running Dr. Stynx agent:

### 9.1 `claim::T` — [DONE]

Registered in `llmInstSet` as `LLM_CLAIM_TYPE`:

```java
docWrap(LLM_CLAIM_TYPE =Type.Builder.build()
        .

tid(REC_TID)
        .

vid(LLM_CLAIM_TID)
        .

isaPredicate(rec(
        uri(TEXT),STR_TYPE,

uri("kind"),union_(

lst(uri("decision"),uri("problem"),

uri("solution"),uri("observation"))).

tryToInst(),

uri(SOURCE).

maybe(),lst(URI_TYPE).

maybe(),

uri(CONCEPT).

maybe(),lst(URI_TYPE).

maybe(),

uri("tier"),isa_(NAT_TYPE).

else_(jnt(1))))
        .

create(), ...)
```

Design decisions that crystallized during implementation:

- **`source`/`concept` are `lst[uri]`, not `uri{*}`.** The type was originally `T(URI_TID.maybeSome())`
  (a coefficient collection). tbleSpace has no `objs` serializer — `baseVidForValue` falls through to
  `/m/rec` for a coefficient collection, which mis-types the column and breaks read-back. The working storage form (same
  as ConceptFeature's `{uri}` collections) is a **`lst` of `!*` auto_from refs**. The type declaration matches the
  storage form: `lst(URI_TYPE).maybe()`.
- **`tier` default lives in the type definition**, not the inst: `isa_(NAT_TYPE).else_(jnt(1))` —
  "a nat, else 1." The summarize inst never stamps tier; coercion supplies it.
- **`kind` is a union coproduct** (`union_(lst(...))`), enforced at write.

### 9.2 `Agent.Helper.miniTask(model, prompt)` — [DONE]

Extracted from `ConceptFeature.AgentExtractor` into `Agent.Helper`. Synchronous; threading is the caller's concern.
`Agent.chat()` now returns `ChatResult` (typed), and `mModel` gained the optional spec accessors (`size`, `quant`,
`cost`, `skill`). `miniChat` constructs a fresh translator agent (a single ChatFeature over the given model), calls
`chat(prompt)`, returns the `ChatResult`.

### 9.3 The `summarize()` inst (claims + loose ends) — [DONE]

```java
instC(LLM_INST_TID.extend("summarize").dom(LLM_SESSION_TID.maybe()).rng(LST_TID),
    rec(uri(SESSION), T(LLM_SESSION_TID.maybe()),
        uri(MODEL), choose_(rec(
                isa_(LLM_MODEL_TYPE).tryToInst(), id_(),
                isa_(LLM_SESSION_TYPE), from_(rshift_(uri(AGENT)).mult_(uri(MODEL)))))
                .rshift_().tryToInst()),
    (lhs, inst) -> { ... })
```

- **Dual-form call**: `@dr/session/1.summarize(_)` (fluent, session as lhs) or `summarize(@dr/session/1)`
  (function, session as arg 0). Resolved via `inst.arg(f(SESSION), 0).orElse(lhs.asRec())`.
- **The `model` arg is a type-directed transform**: pass a `model::T` (kept as-is) or a `session::T`
  (deref `session.agent/model`). The arg definition normalizes whatever arrives to a `model::T`
  before the body runs.
- **Body**: collect the session's messages via the `+/` branch read (rel keys = message vids) → build a digest →
  `miniTask(model, SUMMARIZE_PROMPT)` → parse `<<json:claim>>` blocks off the
  `ChatResult` → coerce `kind` str→uri → stamp `source` as `lst` of `!*` refs → write `claim::T`
  at `<agent>/claim/N` → return the claim vids.

### 9.4 Verified live (Dr. Stynx)

```mtron
@/usr/dr/session/1.summarize()
==> [/usr/dr/claim/0, /usr/dr/claim/1, /usr/dr/claim/2, /usr/dr/claim/3]

*/usr/dr/claim/0
==> claim::[ text=>'The agent dr.stynx operates within the Metatron system and ...',
             kind=>observation,
             source=>[ !*/usr/dr/message/1, !*/usr/dr/message/2, !*/usr/dr/message/3, !*/usr/dr/message/4]]

*/usr/dr/claim/0/source.>>0
==> user::[ text=>'what tools do you have access to?', ... session=>/usr/dr/session/1, ...]
```

The claim's `source` is a **live lazy pointer into the message ledger** — deref it and you get the original typed
message. That is Thalamus's `DERIVED_FROM` provenance, expressed as metatron's native `!*` ref idiom.

### 9.4b `loose_end::T` — [DONE]

The `<<json:loose_end>>` block was added to the same `SUMMARIZE_PROMPT`, and the inst's block-parse loop dispatches on
`claim` vs `loose_end` keys. Loose end writes: coerce `status` str→uri, stamp
`time` via `nowDatetime()`, tag `LLM_LOOSE_END_TID`, anchor at `<agent>/loose_end/N`. The prompt carries the cold-pickup
test and the 0–2 guidance ("if you are writing a third, you are recording rather than continuing"), plus the
non-examples (finished work, current-state observation, operator-queue defects, decision restatements).

**This required a `MTRON_BLOCK` fix in `Agent.java`:** the regex was `$`-end-anchored with a lazy
`.+?`, which silently limited it to parsing **one** block per response (the last one). With two blocks (claim +
loose_end) the second was never found. Removed the `$` anchor — verified with a standalone regex test that both blocks
parse and both strip cleanly. Backward-compatible with the single-block consumers (`LoopFeature`, `LedgerFeature`); it
now enables a general multi-block
`<<json:...>>` response protocol.

Verified live against Dr. Stynx: prompting "Load your concept skill now and a little later, we'll load your loop skill"
produced a claim (the agent's access observation) AND a loose end
`[title=>'Activate loop skill', desc=>"The operator indicated intent to load the 'loop' skill after...",
status=>open, time=>datetime::<...>]` — the model correctly extracted the *deferred intent* as an open continuation
point.

### 9.5 Bugs found and fixed during implementation

- **Java `Obj.dom()` ≠ mtron `dom()`.** Java `dom()` returns the instruction's *domain Type*; mtron `[a=>1,b=>2].dom()`
  returns the keys (an inst, `DOM_INST_TID`). Iterating a rec's entries in Java requires `.elements()` (key=>value
  rels), not `.dom()`. This silently iterated a Type and never found the `claim` block key.
- **`MUTABLE`-at mutates in place; `tid()`/`selfVID()` are pure.** The former is a statement-expression; the latter must
  be reassigned (`rec = rec.tid(...)`). Mixing them without tracking which-is-which silently drops state.
- **`objs` (coefficient collections) are not a tbleSpace column type.** `baseVidForValue` has no
  `isObjs()` branch → mis-types to `/m/rec` → FK-by-convention on the message uris → `parseLong("/usr/dr/message/1")`
  on read-back. Store `{uri}` collections as `lst` of `!*` refs instead.
- **LLM output is JSON, not mtron-rec.** The `<<mtron:claim>>` body failed to parse because the model emitted JSON
  objects. Switched to `<<json:claim>>` (rides the same `MTRON_BLOCK` parse, maps to
  `ObjJSONSerializer`) and coerce `kind` str→uri at the write.

### 9.6 Known debts

- The `[WARN] user message write failed (non-blocking): no incrq query processor attached to
  /m/space/memspace` — the translator agent (a bare ChatFeature) tries to persist its user message to a root it doesn't
  have. Harmless (caught), but noisy. The miniTask translator may want a root or a non-persisting chat feature.
- `take()` on a branch of rels loses the vid keys (renders `noobj`) — the `*/usr/dr/message/+.take(3)`
  observation. Workaround: keep the rels and use `<<`/`pair.first()`. The `take` bug itself is unfixed.

### 9.7 System-message architecture — [DONE]

SystemFeature is now the **gatekeeper** of the agent's system message — state, construction, and lifecycle. `Agent`
holds no system-message state (no blackboard; consistent with the blackboard→
`chat_result::T` redesign).

- **`base` field**: SystemFeature's rec carries the agent's persistent instruction
  (`base => "you are a helpful assistant"`).  `systemMessage()` = `base + "\n" + join(addSystemMessage
  contributions)`.
- **Persist on-change**: `onBeforeChat` writes the composed system message to the ledger as a
  `SYSTEM_MESSAGE_TID` (URI-addressable, part of session memory). Write-on-change via a `last_system_text`
  marker — matches LangChain4j semantics (one system message; new only when content changes). So 3 chats with unchanged
  system text → **1 ledger row**, not 3.
- **No duplicate prompt**: `SpaceChatSessionStore.getMessages()` filters to user/ai/tool_result only —
  `SYSTEM_MESSAGE_TID` never reaches the ChatMemory. So the ledger write is *durability/addressability*, and the
  `systemMessageTransformer` in `Agent.chat()` is the *model delivery* channel. Two purposes, one model path. This
  resolves the old Channel A + Channel B duplicate.
- **Lifecycle**: `onCompleteResponse`/`onError` clear the *contributions* (the `base` persists);
  `Agent.chat()` `finally` clears as an interrupt safety net.
- **Cross-feature requirement helper**: `AbstractFeature.requireFeature(agent, required)` — the standard
  "X requires the agent to have a Y feature" check. Warn-and-degrade (debilitated) when absent;
  `missingFeatureException()` for mandatory. Concept/Skill/Ledger features guard their system-message contribution with
  it.

**Verified live**: all four session backends (Sqlite/PostgreSQL/MariaDB/memSpace) pass
`testTypedCollectionPopulation` with `SYSTEM_MESSAGE_TID → 1 row`; 64 tests green.

### 9.8 Bugs found and fixed during implementation (continued)

- **datetime VARCHAR read-back**: `ObjSQLSerializer.readColumnWithType` wrapped the ISO string via
  `uri(f(raw), DATETIME_TID)` producing invalid `datetime::<2026-08-25 22:34:11.533>` (no host/port). Fixed to
  `parseDatetime(raw)` → canonical `//yyyy.MM:dd/HH/mm/ss/SSS?tz=`.
- **SystemFeature redundant `onBeforeChat`**: overrode the super default with the identical `return noobj()`. Removed —
  the "construction timing" note lives in the class Javadoc.


- The `[WARN] user message write failed (non-blocking): no incrq query processor attached to
  /m/space/memspace` — the translator agent (a bare ChatFeature) tries to persist its user message to a root it doesn't
  have. Harmless (caught), but noisy. The miniTask translator may want a root or a non-persisting chat feature.
- `take()` on a branch of rels loses the vid keys (renders `noobj`) — the `*/usr/dr/message/+.take(3)`
  observation. Workaround: keep the rels and use `<<`/`pair.first()`. The `take` bug itself is unfixed.

---

## 10. NEXT STEPS (handoff)

**Context:** at ~70% context window as of this section. This is the handoff for the next phase.

### What's done

- `claim::T` type (registered, verified reading back from SQLite)
- `loose_end::T` type (registered, verified reading back from SQLite; `source` + `claim` provenance)
- `Agent.Helper.miniTask(Model, String) → ChatResult` (extracted from ConceptFeature)
- `Agent.chat()` → `ChatResult`; `mModel` spec accessors
- `summarize()` inst — dual-form, model-transform arg, `<<json:claim>>` + `<<json:loose_end>>` parse,
  `source` as `!*` refs, `time` stamped
- `MTRON_BLOCK` `$`-anchor removed → multi-block response protocol
- **SystemFeature gatekeeper** — `base` + contributions, persist-on-change to ledger, cross-feature
  `requireFeature` helper (§9.7)
- **datetime VARCHAR read-back fix** (§9.8)
- Full loop verified live: session → model → claims + loose_ends → source → original messages
- 64 tests green across all four session backends + AgentTest + feature tests

### Next increments, in order

1. **SurfacingFeature — the consume side.** **[NEXT — the point of the whole feature]** An
   `on_before_chat` hook that reads open loose_ends (`/usr/dr/loose_end` where `status=>open`) + recent claims, and
   injects them via
   `agent.feature(SYSTEM).<SystemFeature>as().addSystemMessage(...)` (guarded by `requireFeature`). This is the "where
   you left off" — turns claims/loose_ends from write-only data into resumption behavior. The system-message channel is
   now ready for it.
2. **`on_before_chat` surfacing hook.** The consume side — inject open loose ends + recent claims into the next
   session's system message ("where you left off"). Reuse the `CONCEPT_FEATURE_SYSTEM_TEMPLATE`
   injection pattern in `ConceptFeature.onBeforeChat`. Decision: thin mtron feature rec vs. a Java feature (the earlier
   "don't build SummarizeFeature yet" analysis suggests mtron-rec first).
3. **Trust-tier floor.** `claim?tier<=min(source tiers)` — a type predicate or write-time rule. Currently tier defaults
   to `1` via the type. Needs the tier-assignment rule (message kind:
   tool_result > ai > thinking; `external` sources down-tier).
4. **Concept links on claims.** Stamp `concept` with the concepts the claim's source messages link to (reuse the concept
   space). The field is declared; the inst doesn't populate it yet.
5. **MCP query surface.** `claims(c1,c2)`, `loose_ends()`, `summary()` as tools via the existing
   `skill::T → mcp_server::T` reduction.

### Open questions carried forward (from §6, §8.6)

- Distill granularity: per-turn vs session-end (currently session-end via explicit `summarize()`).
- Claim output format: `<<json:claim>>` chosen (LLM-reliable). mtron-block variant rejected.
- `loose_end` status transitions: who sets `in_progress`/`resolved` — the next summarize, or live agent action?
- `take()`-on-branch-rel vid-loss bug (in `from`/`read` path) — fix or keep the `<<`/`pair.first()` workaround.

### Files touched (this phase)

- `src/main/java/studio/phaseshift/metatron/isa/llm/llmInstSet.java` — `claim::T`, `loose_end::T`,
  `summarize()`, `SUMMARIZE_PROMPT`
- `src/main/java/studio/phaseshift/metatron/isa/llm/type/Agent.java` — `Agent.Helper.miniTask`,
  `Agent.chat() → ChatResult`, no system-message state
- `src/main/java/studio/phaseshift/metatron/isa/llm/type/feature/SystemFeature.java` — gatekeeper:
  `base`, contributions, persist-on-change, clear hooks, docs
- `src/main/java/studio/phaseshift/metatron/isa/llm/type/feature/AbstractFeature.java` —
  `requireFeature` / `missingFeatureException` (cross-feature requirement helper)
- `src/main/java/studio/phaseshift/metatron/isa/llm/type/feature/{Concept,Skill,Ledger}Feature.java` —
  `requireFeature(SYSTEM)` guards on system-message contributions
- `src/main/java/studio/phaseshift/metatron/isa/llm/type/feature/{Audit,Chat,Loop}Feature.java` — system-message
  routing / signature updates
- `src/main/java/studio/phaseshift/metatron/isa/llm/type/Model.java` — spec accessors
- `src/main/java/studio/phaseshift/metatron/isa/mach/io/type/ObjSQLSerializer.java` — datetime VARCHAR read-back fix
- `src/test/.../{AgentTest, LedgerFeatureTest, AuditFeatureTest, *LLMSessionIntegrationTest}` — system-message tests,
  write-on-change assertion, debilitated-without-SystemFeature test
- `docs/design/thalamus-metatron.md` — this doc
