# Memory Landscape Review

> **Purpose** — a working review of (a) the state of agent memory systems in the
> broader ecosystem and (b) where metatron's current memory architecture sits in
> that landscape, with proposed directions (geodesics) for growing it.
>
> **Date** — 2026-08-29
> **Scope** — web research (part 1) + codebase survey of `isa/llm` (part 2)

---

## 1. The landscape — forms agent memory takes

The field has settled into two overlapping lenses: a **cognitive-science taxonomy** (working / episodic / semantic /
procedural) borrowed from the Park, Shinn, MemGPT, and Hu et al. survey literature, and a **systems/architecture
taxonomy** (vector, graph, OS-style, sleep-time, parametric). Real systems are hybrids — they map *multiple forms* onto
*multiple storage substrates*.

### 1.1 Form → implementation table

| Form                                                | What it is                                                                                                                                                                                  | State / how it's done                                                                | Representative implementations & references                                                                                                                                                                                                                                                                                                |
|-----------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Working / in-context memory**                     | The finite context window; sliding window or compaction of the live conversation.                                                                                                           | Everywhere; the base case. Managed by windowing, summarization, or "memory pages."   | Claude Code (`AGENTS.md`/`CLAUDE.md`), [LlamaIndex short-term memory](https://www.llamaindex.ai/blog/improved-long-and-short-term-memory-for-llamaindex-agents), [Letta](https://letta.com/) core context, LangGraph checkpointer                                                                                                          |
| **Episodic memory**                                 | Time-ordered stream of *experiences* (what happened, with timestamps, importance). Retrieval typically weights **recency × importance × relevance**.                                        | Mature in research; productionized as event stores with decay.                       | [Reflexion](https://github.com/noahshinn/reflexion) (episodic self-reflection buffer), [Generative Agents](https://github.com/joonspk-research/generative_agents) (memory stream), [MemoryBank](https://github.com/zhongwanjun/MemoryBank-SiliconFriend) (Ebbinghaus forgetting curve), [LangMem](https://github.com/langchain-ai/langmem) |
| **Semantic / knowledge memory**                     | Extracted *facts & entities* decoupled from specific episodes (who/what/where).                                                                                                             | Often stored as structured facts or a knowledge graph; queried rather than streamed. | [Mem0](https://github.com/mem0ai/mem0) ([how Mem0 uses embeddings](https://mem0.ai/blog/how-mem0-uses-embeddings-and-why-we-are-evaluating-nvidia-nemotron-3-embed)), [Cognee](https://github.com/topoteretes/cognee), [LlamaIndex KG](https://github.com/run-llama/llama_index), [LangMem](https://github.com/langchain-ai/langmem)       |
| **Graph / knowledge-graph memory**                  | Memory as entities + edges, enabling multi-hop traversal and **temporal reasoning** (facts with validity windows).                                                                          | 2024–2026's big differentiator — temporal KGs are the current frontier.              | [Zep / Graphiti](https://github.com/getzep/graphiti), [HippoRAG / HippoRAG 2](https://github.com/OSU-NLP-Group/HippoRAG) (hippocampal indexing + PageRank), [Cognee](https://github.com/topoteretes/cognee), [Mem0 graph memory](https://docs.mem0.com.cn/open-source/features/graph-memory)                                               |
| **Procedural memory**                               | *How* — learned skills, strategies, workflows; the "skill library" as memory.                                                                                                               | Skill libraries / self-edited code; less common than episodic/semantic in OSS.       | [Voyager](https://github.com/MineDo/Voyager) (skill library), [Letta](https://letta.com/) (self-editing memory/behavior), Reflexion (procedural self-improvement)                                                                                                                                                                          |
| **Vector / embedding-store memory**                 | Memory as embeddings in a vector DB (pgvector, Qdrant, Milvus, Chroma); similarity search + optional rerank.                                                                                | De-facto default substrate for episodic+semantic hybrids.                            | [Mem0](https://github.com/mem0ai/mem0), [Letta](https://github.com/letta-ai/letta), [LlamaIndex](https://github.com/run-llama/llama_index), [Zep](https://github.com/getzep/zep), [Memobase](https://www.memobase.com/)                                                                                                                    |
| **OS-style / "memory operating system"**            | LLM context treated like an **OS virtual-memory layer**: paging between a small "core" and large "external" memory, page-table-style scheduling, memory as schedulable first-class objects. | The 2025 conceptual shift; the most architecturally ambitious form.                  | **MemGPT / [Letta](https://github.com/letta-ai/letta)** (pioneered it), [MemOS (MemTensor)](https://github.com/MemTensor/MemOS) (arXiv:2507.03724), [MemoryOS](https://github.com/BAI-LAB/MemoryOS) (EMNLP 2025 Oral)                                                                                                                      |
| **Sleep-time compute / asynchronous consolidation** | Background agents *reorganize, compress, and consolidate* memory *outside* the active turn (the "sleep" phase), so the interactive run stays fast.                                          | The most architecturally interesting 2025 advance per the survey analyses.           | [Letta sleep-time compute](https://www.letta.com/blog/sleep-time-compute) (arXiv:2504.13171), MemOS, MemoryOS                                                                                                                                                                                                                              |
| **Parametric / in-weights memory**                  | Memory baked into model weights via fine-tuning / LoRA / continual pretraining — *vs.* in-context + in-database (non-parametric).                                                           | Active research: when to write memory to weights vs. to a retrieval store.           | [From RAG to Memory: Non-Parametric Continual Learning (ICML 2025)](https://proceedings.mlr.press/v267/gutierrez25a.html), LoRA / continued-PT tooling                                                                                                                                                                                     |
| **Distributed / shared multi-agent memory**         | A single memory substrate **shared across multiple agents** (and harnesses), correlated by message/call-id, so agents collaborate on one memory tree.                                       | Emerging; driven by "memory-as-bus" and shared-memory designs.                       | [Memobase](https://www.memobase.com/), [Letta shared memory](https://letta.com/), and metatron's DSH→metatron memory bus (`.metatron/skills/mtron/references/dsh-mtron.md`)                                                                                                                                                                |

### 1.2 Cross-cutting take-aways (2025–26 surveys)

- **The dominant production stack is the vector+graph hybrid** — a similarity store *plus* a temporal knowledge graph,
  with an extractor that decides what to write, drop, or update. Mem0, Zep, and Cognee all converge on this. See the
  Letta community thread comparing
  [Letta vs Mem0 vs Zep vs Cognee](https://forum.letta.com/t/agent-memory-solutions-letta-vs-mem0-vs-zep-vs-cognee/85).
- **The OS-style framing won** the conceptual argument. MemGPT/Letta opened it; MemOS and MemoryOS (EMNLP 2025 Oral)
  formalized memory as schedulable, paged, first-class resources.
- **Sleep-time compute** (Letta, arXiv:2504.13171) is the standout 2025 architecture: do the expensive consolidation
  work asynchronously, not inline with the user's turn.
- **Where to write memory** — in-context (cheap, fast, forgettable), in-database (durable, queryable), or in-weights
  (permanent, expensive) — is *the* open decision, per the ICML 2025 "From RAG to Memory" line of work.

### 1.3 Reference list

- [MemOS docs (arXiv:2507.03724) — agentic-memory repo](https://github.com/lhl/agentic-memory/blob/main/references/hu-memory-age-ai-agents.md)
- [Agent memory survey analysis (Hu et al., "Memory in the Age of AI Agents")](https://raw.githubusercontent.com/lhl/agentic-memory/main/ANALYSIS-arxiv-2512.13564-memory-age-ai-agents.md)
- [Memory for Autonomous LLM Agents — mechanisms & evaluation (Semantic Scholar)](https://www.semanticscholar.org/paper/Memory-for-Autonomous-LLM-Agents%3AMechanisms%2C-and-Du/1598278f0941bc2b4be2e7abeac47e8288a14e93/figure/0)
- [Agent Memory Research 2026: taxonomy (Zenodo)](https://zenodo.org/records/20780690)
- [MemoryOS (EMNLP 2025 Oral)](https://github.com/BAI-LAB/MemoryOS)

---

## 2. Where metatron sits — codebase survey of the memory stack

All file references are relative to the repo root under
`src/main/java/studio/phaseshift/metatron/isa/llm/`.

### 2.1 The stack: features, hooks, and space

An `Agent` is a rec (`llm_agent::T`) enriched with an ordered list of **features**. Each feature refines
`llm_feature::T` and can override the chat-lifecycle hooks (`onAgentCtor`, `onBeforeChat`, `onPartialResponse`,
`onPartialThinking`, `onPartialToolCall`, `beforeToolExecution`,
`onToolExecuted`, `onCompleteResponse`, `onError`) — wired reflectively by
`createStageLambdas`/`STAGE_DEFS` in `llmInstSet.java`. Memory in metatron is not a side service; it is **typed data
living in the universal URI space**, mutated by features and queryable with mtron.

Current feature roster and memory relevance:

| Class                                                             | Registered type                                          | Memory role                                                                         |
|-------------------------------------------------------------------|----------------------------------------------------------|-------------------------------------------------------------------------------------|
| `feature/ChatFeature`                                             | `llm_chat_feature`                                       | Lifecycle plumbing; carries `lastMessage()`                                         |
| `feature/SessionFeature` + `space/SpaceChatSessionStore`          | `llm_session_feature`                                    | **Working memory** — session policy + windowed read view over an append-only ledger |
| `feature/ConceptFeature`                                          | `llm_concept_feature`                                    | **Semantic memory** — concept co-location graph + message back-refs                 |
| `feature/SummarizeFeature` (+ `summarizeSession` in `llmInstSet`) | `llm_summarize_feature`                                  | **Sleep-time compute** — async distill into `claim::T` / `loose_end::T`             |
| `feature/LedgerFeature`                                           | `llm_ledger_feature`                                     | **Working scratchpad** — persistent k/v, never cleared                              |
| `feature/IterationFeature`                                        | `llm_iteration_feature`                                  | **Episodic overlay** — linked iteration graph on the ledger                         |
| `feature/EmbedFeature`, `feature/SimilarityRecallFeature`         | `llm_embed_feature` (unregistered), `llm_recall_feature` | **Vector recall — stubs** (empty or one `embed()` call)                             |
| `feature/NarrativeFeature`                                        | — (dormant, unregistered)                                | Reserved for narrative/episodic memory                                              |
| `feature/SkillFeature`, `mSkill`                                  | `llm_skill_feature`                                      | **Procedural memory** — SKILL.md packs, agent-as-skill                              |
| `space/SpaceContentRetriever`                                     | —                                                        | **RAG** — minimal `ContentRetriever` over a URI pattern                             |
| `feature/AuditFeature`, `CostFeature`                             | audit / cost                                             | Memory-adjacent: audit trail, token cost ledger                                     |

### 2.2 Working memory — `SessionFeature` + `SpaceChatSessionStore`

`SessionFeature` (`type/feature/SessionFeature.java`) owns a LangChain4j
`ChatMemory`. `onBeforeChat()` resolves the session policy rec from the space
(`session => {agent, user, algorithm: {name, max}}`), bumps a **monotonic `chat_id`** that survives restarts, and
constructs either a
`TokenWindowChatMemory` (with a 4-chars≈1-token estimate) or a
`MessageWindowChatMemory`, both over a custom `ChatMemoryStore`:
`SpaceChatSessionStore`.

`SpaceChatSessionStore` (`space/SpaceChatSessionStore.java`) is the load bearing piece:

- **Ledger semantics.** The store is *read-time windowing over an append-only ledger*: `.../llm_message/_?incrq` holds
  every message ever written, as a polymorphic `message::T` rec discriminated by one of six TIDs —
  `user::`, `ai::`, `system::`, `tool_request::`, `tool_result::`,
  `thinking::` (`LLM_*_TID` in `llmInstSet`). The sliding window (`message_window` / `token_window`) is a **view**, not
  a mutation — nothing is ever dropped from the ledger.
- **Envelope.** Every message rec carries `session` (uri), `depth` (int),
  `chat_id` (int), `time`. These are the traversal keys — the exact contract the DSH memory bus migrates to
  (`.metatron/skills/mtron/references/dsh-mtron.md`).
- **Pair-aware windowing.** `adjustSkipToPreservePairs` +
  `pullInPairedAiMessage` / `pullInPrecedingUserMessage` /
  `pullInAllOrphanedToolResults` guarantee a window never starts with an orphaned `tool_result` or an `ai::` without its
  user turn — a real gap most window implementations have; here it's enforced (and
  `SpaceChatSessionStoreTest` pins it case by case).
- **Sub-agent isolation.** Reads filter by `depth`; at `depth >= 2` they also filter by `chat_id`, so a previous turn's
  recursive sub-agent messages can't leak into the current turn's sub-agent context (`agent.setCurrentChatId`).
- **Dedup discipline.** `updateMessages` only writes `ai::` messages not already persisted, using the `_w` marker that
  rides LC4j attributes through
  `getMessages` — because LC4j re-sends the full list on every chat.

This is the "in-database" cell of the in-context / in-database / in-weights trilemma, solved the way metatron does
everything: memory as addressable space data, with the windowing algorithm being a *view*.

### 2.3 Semantic memory — `ConceptFeature`

`type/feature/ConceptFeature.java` maintains a **concept co-location graph in the space**, with a pluggable `Extractor`
(config: `extractor => tag | agent
| lucene`):

- `TaggingExtractor` — parses inline `<<concept:…>>` tags from agent output (and, by design, *only* from explicit tags
  in thinking blocks).
- `AgentExtractor` — a background mini-task LLM rewrites the response tagging key concepts (async by default; `blocking`
  in `onBeforeChat`).
- `LuceneExtractor` — TF-IDF over an in-memory
  `ByteBuffersDirectory` Lucene index (`MessageIndexer`): local-TF × corpus-IDF scoring for per-message concept picks.

Storage shape: each concept becomes a rec at `<concept-root>/<concept>` with two auto_from-linked lists — `concept`
(co-occurring peers ⇒ the graph edges)
and `message` (back-refs to the ledger messages that used the term). Ingestion runs **spell-check/canonicalization**
against existing concept names (`CommonUtil.correctSpelling`) and stop-word filtering so the graph doesn't fragment.

Recall path: after a response, `injectConceptRecommendations` finds concepts that now have *known historic messages*
and, via `SystemFeature`, injects a system message listing them plus two mtron instructions the agent can call —
`messages(<concept>)` (fetch historic message texts) and `concepts(<concept>)`
(fetch adjacent concepts — the "spreading activation" one-hop). This is agent-initiated recall: the agent decides
whether to dig, not the harness.

### 2.4 Sleep-time compute — `SummarizeFeature` + `claim::T` / `loose_end::T`

`type/feature/SummarizeFeature.java` + `summarizeSession` in `llmInstSet`:

- **Trigger** — the agent appends a `<<mtron:summarize>>` block (scope / kind / concept) to a response, or the user
  evaluates
  `@dr/session/1.summarize(_)` — "the block is exactly a deferred
  `summary()` call." `onCompleteResponse` queues it on a `CoreThread`
  (`applyAsync`) — **the turn returns immediately**; this is genuine sleep-time compute, not inline summarization.
- **Distill.** `summarizeSession` collects the session's ledger messages (optionally scope-filtered by time), renders
  them as a
  `vid==>text` digest, and runs a `miniTask` with `SUMMARIZE_PROMPT`: the model emits `<<json:claim>>` /
  `<<json:loose_end>>` blocks. *Provenance is stamped by the inst, not the model* — the model picks message vids from
  the digest, and the inst converts them to `!*` auto_from refs under `source`.
- **Outputs** — `claim::T` (`text, kind∈{decision, problem, solution,
  observation}, source, concept, tier`) anchored at `<agent>/claim/_?incrq`, and `loose_end::T` (`title, desc, status∈{open, in_progress, resolved,
  abandoned}, source, claim, time`) at `<agent>/loose_end/_?incrq` — the loose end links *forward* to its justifying
  claims. `tier` models a provenance-bounded trust level ("bounded by min (source tiers)").
- **Recall.** Next chat, `onBeforeChat` polls the `FutureObj`: if the distill finished, `buildBriefing` filters claims
  by kind/concept (via the concept graph's `message` back-refs) and injects `[claim=>[…location…], loose_end=>…]`
  with `!*` deref pointers the agent can follow. Separately, an **always-on loose-end reminder** is injected every turn.
- The "cold test" in the prompt ("could a session with no access to this transcript act on it?") is an unusually good
  operational definition of a cross-session memory unit.

### 2.5 Working scratchpad & episodic overlay

- **`LedgerFeature`** — a persistent keyed k/v scratchpad at a space URI:
  read/write/search (a `search` inst defined *in mtron, in the space*) + archive/restore; key list injected via system
  message each turn. Never cleared across chats.
- **`IterationFeature`** — overlays a linked iteration graph (`LLM_ITERATION_TYPE`:
  `session, index, prev, next, message, time`) on the ledger, one node per chat turn.
- **RAG** — `SpaceContentRetriever` (a pattern+max `ContentRetriever` over
  `/sys/docs/#`-style URIs) and the `embed()` inst (`LLM_INST_TID.extend("embed") → vec::T`, `vec/MVec`).
- **Stubs on purpose** — `EmbedFeature` is an empty class (TID declared but *not* registered in the type list);
  `SimilarityRecallFeature.onBeforeChat`
  computes the query vector then stops — "simQ pending" (the similarity query is unimplemented); `NarrativeFeature` is a
  dormant shell. The vector recall lane is consciously unfinished.

### 2.6 The memory bus (cross-harness)

`.metatron/skills/mtron/references/dsh-mtron.md` +
`assets/dsh_memory_loader.py` implement the **first adapter on an inter-harness memory bus**: DSH zstd-JSONL
transcripts → the five message TIDs (envelope: `text, time, session, depth, chat_id`; `contents` call-id as the join
key) → `.to(/usr/dr/message)` in a live VM. The design principle:
*N adapters, one contract* — the metatron message model is the middle, so Claude's markdown memory dirs or OpenAI JSONL
are just other emitters. Memory becomes "addressable data": `session/depth/chat_id` are traversal keys, `text` is a
regex surface, `contents` is a join key — assertable migrations (counts in = counts out) instead of copies.

### 2.7 Positioning in the taxonomy

| Form (§1.1)          | metatron today                           | Notes                                                                                                       |
|----------------------|------------------------------------------|-------------------------------------------------------------------------------------------------------------|
| Working / in-context | ✅ strong                                | ledger-backed window, pair-aware skip, scratchpad, sub-agent isolation                                      |
| Episodic memory      | ✅ strong                                | append-only ledger + 6 message TIDs + iteration graph + provenance refs                                     |
| Semantic / knowledge | 🟡 partial                               | concept nouns + claims; no entity/instance model, no typed relations yet                                    |
| Graph / KG memory    | 🟡 partial                               | *three-level* graph (message → concept → claim → loose_end) with auto_from edges; co-location only, untimed |
| Procedural memory    | 🟡 partial                               | skills + agent self-editable recs; no auto-harvested skill library                                          |
| Vector / embedding   | 🔴 stub                                  | `embed()` inst + `MVec` exist; `EmbedFeature`/`SimilarityRecallFeature` empty; "simQ pending"               |
| OS-style framing     | 🟡 ahead on concept, behind on machinery | memory-as-addressable-space is philosophically MemGPT-plus; no paging scheduler yet                         |
| Sleep-time compute   | ✅ present (trigger-based)               | async distill + next-turn briefing; agent-tripped, not scheduled                                            |
| Parametric           | ⛔ n/a                                   | metatron is a VM, not a model — by design                                                                   |
| Distributed / shared | 🟡 unique seed                           | only player with a native memory bus + tble-backed spaces; multi-agent federation unimplemented             |

**One-line position:** metatron already implements the *episodic + semantic

+ sleep-time* spine with a durability and addressability that vector-first systems (Mem0, Zep) only get by bolting a
  graph on — and it is missing exactly two tiles its architecture makes cheap: **vector recall** (stubs already sit in
  the right classes) and **typed, temporal edges** (the auto_from link substrate is already the edge layer).

---

## 3. Geodesics — where metatron can go

A "geodesic" here means: a direction that is short in *conceptual* distance — it rides substrate metatron already has
(URI space, auto_from links,
`CoreThread`, subq, `miniTask`, `MVec`) rather than importing a new infrastructure. The three geodesics below are
ordered by payoff-per-new-concept.

### G1 — Vector recall: finish the lane that is already started

**What.** Real embeddings + similarity recall as a first-class memory surface. This is the clearest capability gap (🔴 in
§2.7) and the substrate is 80% present: `embed()` inst → `vec::T` / `MVec`, `EmbedFeature` and
`SimilarityRecallFeature` stubs sit in the exact classes where the logic belongs.

**How (concrete).**

1. **`EmbedFeature`** — on `onCompleteResponse` (or `onPartialResponse`
   batches), embed the new message texts with the session's model and persist them as `MVec` alongside each ledger
   message (a sibling key, e.g.
   `<agent>/message/N/embed`, or an embedded field if the `message` type grows an `embed{?}` slot — re-type, don't fork
   the type). Provide a
   `?simq`/`nearest(k, simq)` query over the ledger URI so a *vector* is just another address in the space, not a
   foreign DB.
2. **`SimilarityRecallFeature`** — `onBeforeChat` already computes the query vector; finish it: top-k over the ledger +
   concept root, merge with the ConceptFeature co-location hits, and hand the union to
   `SystemFeature.addSystemMessage` (the injection channel already exists).
3. **`SpaceContentRetriever`** — swap the pattern-match for a `simq` rank when the queried URIs carry embeddings.
4. **Model-agnostic.** Embeddings come from whatever `model` rec the agent holds (OLLAMA / OpenAI / Anthropic protocols
   already exist in
   `LLMFactory`/`mModel`) — no new dependency, and it stays local-first, which Mem0-Zep-class stacks cannot do on-prem.

**Why it is a geodesic.** Zero new storage model; the only new object is an
`MVec` next to an existing message URI. Everything downstream (recall injection, RAG, ranking) reuses §2.4's briefing
machinery verbatim.

**Risk.** Token cost + latency per message; mitigated by batching in
`onCompleteResponse` and a cheap local embedding model by default.

### G2 — Temporal + typed edges on the concept/claim graph

**What.** Move the semantic layer from *co-location* to a *typed, validity- windowed* graph — the HippoRAG/Graphiti
capability — using the auto_from link substrate that is already the edge layer.

**How (concrete).**

1. **Typed edges.** The claim/recall types already declare `concept` and
   `claim` link fields. Generalize to a small, *closed* vocabulary of typed edges (not a free-form ontology):
   `resolves(claim→loose_end)`,
   `supports(claim→claim)`, `supersedes(claim→claim)`, `about(concept→
   claim)`, `part_of(concept→concept)`. Each is an `auto_from` pair at a URI named for the edge — edges become
   *queryable space data* (e.g.
   `*/claim/+/resolves/+.from()`), not an opaque graph DB.
2. **Validity windows.** `loose_end.status` (`open|in_progress|resolved|
   abandoned`) is already a 4-state lifecycle. Add `since/valid_until`
   (datetime) to claims and let the summarizer close a claim when a later claim `supersedes` it — a minimal, *readable*
   temporal model, not TARDiS-style full retraction.
3. **Extraction.** Extend `SUMMARIZE_PROMPT` to emit the typed edge list alongside claims (the distill mini-task already
   runs; adding one output block is prompt + a parse branch in `summarizeSession`, mirroring the existing `claim`/
   `loose_end` branches).
4. **Ranking.** With edges + validity, `buildBriefing` can re-rank by recency × relevance (validity-weighted) instead of
   flat kind/concept filters — closing to the Generative-Agents retrieval formula but on a typed graph.

**Why it is a geodesic.** No new type-system concept — `auto_from`, `rec`,
`lst`, `datetime` are all in the vocabulary. The graph stays *addressable*
(the differentiator vs. Mem0/Zep, where the graph is a service you cannot
`*<addr>`).

**Risk.** Typed-edge vocabulary sprawl. Mitigate by keeping it a small **closed** set governed by `Tokens.java` (per the
project's term-discipline rule), and refusing an "anything-to-anything" generic edge.

### G3 — Federated / shared memory: make the bus the moat

**What.** The DSH→metatron adapter (§2.6) is a prototype of the most differentiating capability in the whole landscape:
**memory that is a portable, addressable, assertable resource, not a per-harness silo**. No production system in §1.1
offers "bring *any* other tool's memory into my address space and query it" because they aren't an address-space-native
VM.

**How (concrete).**

1. **N adapters, one contract — make it a real ISA.** Each adapter is a thin emitter producing the five message TIDs +
   envelope. Ship adapters as mtron *insts* (`as(memory::T)`) plus the loader assets already under
   `.metatron/skills/mtron/assets/`, so adding a harness is an *expression*, not a service.
2. **Provenance + trust.** Every migrated rec already carries a `session` (a foreign session id) — add a `harness`/
   `origin` tag and a *claim*
   (from the DSH session) as the unit of trust. This is what the `tier`
   field on `claim::T` is reaching for; make it explicit: migrated memory gets a `tier` the agent can weight.
3. **Two-way** where the source allows it (Claude markdown, OpenAI JSONL are append-friendly); read-only where it
   doesn't (DSH live bundle). Snapshots keep a stable picture against a live bundle, and a wrong write is detectable
   immediately via `</path>/+.tid()` — so a failed adapter is recoverable by re-emitting, per the documented `to(uri)`
   semantics.
4. **Shared recall.** With G2's typed edges, *another* agent's claims can feed *this* agent's briefing — cross-agent
   memory federation on one space. This is the "multi-agent shared memory" row of §1.1, done metatron-natively where the
   rest of the field bolted it on as a feature flag.

**Why it is a geodesic.** The hard 80% — an addressable, typed, asserted memory substrate and one working adapter — is
*done*. G3 is "turn a prototype into a contract + a couple more emitters."

**Risk.** Format drift in source harnesses. Mitigated by the
`[unmapped event]` preservation invariant and the contract tests (`assets/tests/test_dsh_memory_loader.py`), plus
pinning each emitter to a versioned bundle snapshot.

---

## 4. Suggested sequence

| Order | Geodesic                  | Effort  | Payoff                                                  | Depends on                                |
|-------|---------------------------|---------|---------------------------------------------------------|-------------------------------------------|
| **1** | G1 — vector recall        | **S–M** | Closes the 🔴 gap; unlocks RAG quality + recall ranking | nothing (stubs exist)                     |
| **2** | G2 — typed/temporal edges | **M**   | The strategic differentiator; makes recall *right*      | G1 (rank by vec recency), or stands alone |
| **3** | G3 — memory bus as ISA    | **M**   | The unique moat; compounds G2 (cross-agent recall)      | G2 (trust/edges) helps but not required   |

**Do G1 first** — it is the only fully-blocked capability row, the substrate is present, and it is a prerequisite for
doing G2's ranking and G3's relevance weighting well. **G2 is the strategic bet** and the reason to keep building a
*space-native* graph instead of wrapping an external one. **G3 is the compounding endgame** that no Mem0/Zep/Letta-class
system can match, because they are not address-space-native.

---

## Appendix — code map of the surveyed memory components

| Component                   | Path (relative to `studio/phaseshift/metatron/`)                                                          |
|-----------------------------|-----------------------------------------------------------------------------------------------------------|
| Feature/hook wiring         | `isa/llm/llmInstSet.java` (`STAGE_DEFS`, `createStageLambdas`, `summarizeSession`)                        |
| Session policy + window     | `isa/llm/type/feature/SessionFeature.java`                                                                |
| Custom `ChatMemoryStore`    | `isa/llm/space/SpaceChatSessionStore.java`                                                                |
| Concept graph + extractors  | `isa/llm/type/feature/ConceptFeature.java`                                                                |
| Sleep-time distill          | `isa/llm/type/feature/SummarizeFeature.java`                                                              |
| Scratchpad (k/v)            | `isa/llm/type/feature/LedgerFeature.java`                                                                 |
| Vector recall (stub)        | `isa/llm/type/feature/SimilarityRecallFeature.java`, `EmbedFeature.java`                                  |
| RAG retriever               | `isa/llm/space/SpaceContentRetriever.java`                                                                |
| Memory bus (adapter + docs) | `.metatron/skills/mtron/references/dsh-mtron.md`, `.metatron/skills/mtron/assets/dsh_memory_loader.py`    |
| Type definitions            | `isa/llm/llmInstSet.java` (`LLM_CLAIM_TID`, `LLM_LOOSE_END_TID`, `LLM_MESSAGE_TYPE`, `LLM_*_MESSAGE_TID`) |
