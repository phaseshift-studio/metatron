# Feature / Agent API Refactor

## Purpose

Make the agent a minimal, space-native substrate — `prompt -> features -> chatresult` — with every
ability supplied by features and every state object addressable in space. Remove the type-unsafe
`agent.feature(TID).<X>as()` cross-feature access, consolidate per-turn state into a `Frame`, and
derive the hook and order contracts instead of hand-maintaining them.

## Principles

1. **Minimal core.** The agent types only `prompt` and `result`. Tools, skills, thinking,
   compaction, todo, memory are feature concepts; the core never names them (this is the trap other
   agent harnesses fall into).
2. **mtron-first.** Observable state lives in space (a rec at a URI); Java is a thin typed veil. No
   new mtron-blind Java fields. A Feature may be a pure mtron rec (tid + hook insts) with no Java class,
   so every Java method on a Feature must reduce to a `Rec.at()` / space write — never Java-only state.
   `feature(fURI)` is the universal lookup; `feature(Class)` is a Java-only convenience on top of it.
3. **Declare once, derive everywhere.** Hooks and ordering are derived from declarations (Java
   overrides, `requires()`), never re-stated by hand.
4. **Address = identity.** No UUIDs. Objects are addressed by the URI path that encodes their place
   in the tree.

## The address scheme

```
<agent_root>                                        the agent rec (name, desc, root, feature)
<agent_root>/feature                                lst::T of features, graph-ordered
<agent_root>/feature/<tid>                          one feature (filter(tid().has(...)))
<agent_root>/frame/s<sid>/c<cid>/d<depth>/...            a frame (live) — positional key + locals extension
```

The feature list is a `lst::T` **because the list is the evaluation order**. The dependency graph
writes the canonical (topologically sorted) order back into the list.

## One tblespace, two storage tiers

- **Typed tables** — the flat, column-queryable truth. Every schema type is a table (field ->
  column, `auto_from` -> `ref_table` FK, via `_mtron_meta`). The frame identity (`session`,
  `chat_id`, `depth`) mirrors columns already on `message` / `think`; the completed turn is a
  `chat_result` row plus FK refs to feature outputs (`think`, `audit`, `cost`, `loop_results`).
- **`kv_store`** — the schema-less tier for arbitrary nested writes: `furi` (key) -> a typed scalar
  (`int_val` / `str_val` / ...) or a serialized `complex_val`. The agent rec, feature config, and the
  frame live here.

The frame needs no separate memspace and no hand-built nested rec: each leaf is written **flat to
its URI** (`kv_store` key → value), and the space **derives the nested tree on read** — a non-int
path segment becomes a rec key, an int segment a `lst` index (which is why the `s`/`c`/`d` prefixes
are load-bearing).

```
write:  frame/s1/c2/d5/prompt -> str        frame/s1/c2/d5/result -> chat_result
        frame/s1/c2/d5/tool/orphans -> lst
read:   *dr/frame/s1/c2/d5  ==>  rec[prompt, result, state, parent, tool => [orphans => …], …]
```

Addressing is poly path traversal (`*dr/frame/s1/c2/d5`), all in the same SQLite backend.  The
path is positional (DataPath: meaning from schema, not inline tags) and typed-prefixed — `s`/`c`/`d`
mark the segment role so a bare int is never misread as a `lst` index.  The rec itself is labeled:
its keys (`session`, `chat_id`, `depth`, `result`, …) are the same names as the typed columns, so
the two tiers share one vocabulary.

## Frame

The per-evaluation "method frame" — an activation record keyed positionally by
`(session, chat_id, depth)` and addressed `frame/s<sid>/c<cid>/d<depth>`.  The path is
positional (DataPath) and typed-prefixed (`s`/`c`/`d` mark the role so a bare int is never
misread as a `lst` index); the rec itself is labeled.  Written flat (each leaf to its URI), the
nested tree is derived on read.

Two types:

- **`Frame`** — the generic spine: `session`, `chat_id`, `depth`, `parent` (return address),
  `state` (`run` | `complete`), `prompt` (args), plus the open locals extension.  Reusable for
  any function-like computation.
- **`ChatFrame`** — `Frame` + the chat result fields `chat`, `user`, `time`, `thinking`, and the
  feature-projected outputs (`tool/`, `thoughts/`, `concepts/`, …).  **`ChatFrame` replaces
  `ChatResult`**: it is both the activation record while the turn runs and the answer when it is
  popped back up — one type, not two.

The stack-frame analogy is exact: `prompt` is the argument, `ChatFrame` (its `chat`/`user`/`time`
fields) the return value, `state` + the in-flight `feature × stage` the program counter, `parent`
the return address, and the extension the locals.  A Java field that is transient per-iteration
scratch (`orphanToolRequests`, `conceptRecommendations`, `knownConceptNames`, `currentResult`,
`currentHook`) becomes a locals key — the field-state sweep.

The flow:

```
ChatFrame f = stack.push(ChatFrame.of(prompt, parent));   // chat() entry — state=run
… the turn fills it: features write locals + chat/user/time/thinking …
ChatFrame done = stack.pop();                             // chat() exit — state=complete
return done;                                              // to the user
```

Resolution borrows `stackSpace`: a stable root frame per turn (`d0`); recursion pushes a shadow
(`d<n>`); a read resolves shadow → root; a write projects to the current frame and to the root
(overwrite in place).  The shadow stack pops (recursion unwinds), the root frame never pops, and
no frame is ever deleted — it just stops being top — so the whole tree is durable, queryable
"compute memory".

### FrameService + ChatStack

- **`FrameService`** — the capability (tid + interface), offered by anyone — including a **space**
  (the frame store is space-native, so a space itself can provide it):

  ```java
  public interface FrameService {
      fURI  current();                       // frame/s<sid>/c<cid>/d<depth>
      Frame push(Frame frame);               // allocate + link parent, depth+1
      Frame pop();                           // mark complete, return the top frame
      Obj   at(fURI key);                    // read, shadow → root
      void  locals(fURI key, Obj value);     // flat write (project to root)
      fURI  parent();                        // the return address
  }
  ```

- **`ChatStack`** — the concrete spine replacing today's plumbing: `s<sid>` from the session VID's
  trailing id, `c<cid>` from the session `chat_id` counter (MessageFeature), `d<depth>` replacing
  the static `depthMap` (`ConcurrentHashMap<session, AtomicInteger>`); `push()` links `parent`,
  `pop()` returns the frame without deleting it, `locals()` is a flat write, `at()` resolves
  shadow → root.

- **`AbstractFrameFeature`** — the feature hierarchy, mirroring concept/message: owns the lifecycle
  (push the frame on `onBeforeChat`, pop on `onCompleteResponse`), `requires()` `message_service`
  for `session`/`chat_id`, and stores frames under the feature's own `root` — the root *is* the
  address space (no separate space reference).  Two providers, the seam being what `pop()` does:
  **`PersistedFrameFeature`** (mark complete, leave the frame — introspectable) and
  **`TransientFrameFeature`** (remove the frame — an ephemeral call stack).

## Service registry

A **service** is a capability identified by a tid, with two faces — a Java interface (the stub) and
type-level insts (the mtron face). A **provider** is anything that `offers()` the tid; a **consumer**
is anything that `requires()`/`uses()` it. Providers and consumers need not be features.

```
offers()                    <- anyone (feature, web service, agent, library, space)
requires() / uses()         <- anyone (a consumer asks agent.service(tid))
lifecycle hooks             <- features only (the agent drives them)
```

The only thing that makes something a *Feature* is the lifecycle: the agent composes and steps it
through a turn. Providing and consuming services is open to any chunk of mtron — a web service can
`offers(concept_service)` and `agent.service(concept_service)` exactly as a feature would.  A
provider can be *any* obj — including a **space**: `FrameService` is the first such case, since the
frame store is space-native (write flat, derive rec on read), so a space itself can `offers()` it —
alongside the `PersistedFrameFeature` / `TransientFrameFeature` the agent attaches for lifecycle.

Resolution: `agent.service(fURI)` finds the provider whose `offers()` contains the tid;
`agent.service(Class)` is the Java-typed spelling (`instanceof`). `feature(Class)` maps class -> tid
-> `filter(tid().has(tid))` -> `Rec.wrap(...)` — the tid is the registry, there is no parallel Java
registry. A service that bundles several algorithms (e.g. ConceptFeature's three extractors) should
be one service with multiple providers, selected from config.

## Dependency graph + ordering

`requires()` / `uses()` / `offers()` all speak **service tids** (not feature tids) — a feature
declares which capabilities it needs, uses, and provides, never which other *feature* it is coupled
to.

Two consumption kinds:

- `requires()` — **hard**: the feature won't work without it. Validated at construction (missing /
  cyclic -> composition error) and an ordering edge (the dependency fires first). Access via
  `agent.service(tid)` non-null.
- `uses()` — **soft**: the feature works but in a degraded (less rich) state without it. NOT
  validated, no ordering edge. Access via `agent.service(tid)` and check `isPresent()`.

`chatPhase()` (`REGISTRANT` | `COMPOSER` | `CONSUMER`) layers the stages. The agent topologically
sorts the `requires()` edges, validates missing/cyclic hard deps, and **writes the result back** into
the `feature` list so stored order == evaluation order.

## Hook + capability contract

A feature's whole public surface is instructions, at two levels:

- **hooks** are **value-level instructions** — per-instance, stored on the rec (materialized from a
  Java method or an mtron config inst by `createStageLambdas` / a hand-declared `on_before_chat =>
  inst(...)`),
- **capabilities** are **type-level instructions** — registered in the inst set with a declared
  `dom`/`rng`/typed `args`, called fluently `feature.cap(args)` or in function form `cap(feature,
  args)` — cf. the `summary` / `compact` insts at `/m/llm/inst/*`.

A Java method is an inst *body*, wrapped by `MInst.instLambda(...)`; the inst is the mtron API.
Java -> Java calls the method directly; mtron -> Java calls the inst, which redirects into the
method. A capability's Java face is its **service interface** (the feature implements it); its mtron
face is the type-level inst. The base `feature::T` declares the hook schema once; concrete features
never re-state it.

## No Java field state

Feature data lives in the rec map — `kv_store` for nested/opaque state, typed tables for
flat/queryable state. LC4j / I-O handles (`ChatMemory`, `ToolProvider`, `SpaceChatSessionStore`) stay
opaque and retained, documented as transient — the exception, not the rule.

## Test fixture

`AgentFixture.Builder` (test scope) assembles an agent from features, auto-resolves the `requires()`
closure for the config-less gateways (reusing graph validation), and lets a `RecordingAgent` / fake
context assert emissions without `chat()` internals. Replaces `gatekeepersFor()` and hand-built recs.

## Migration (state)

Done:

- P1 — typed `feature(Class)` / `require(Class)` + `FEATURE_TID`, full `.as()` sweep.
- P6 — `AgentFixture.Builder` (auto-resolves `requires()`), `gatekeepersFor()` deleted.
- `requires()` / `uses()` split (hard/soft).
- service interfaces (`ToolService`, `SkillService`, `SystemService`, `MessageService`,
  `ConceptService`, `ChatService`, `ThinkService`) + features implement + `agent.service(Class)`.
- roll out `service(Class)` / `requireService(Class)` across the remaining cross-feature call sites.
- service tids (`LLM_*_SERVICE_TID`) + `Feature.offers()` on the seven providers +
  `agent.service(fURI)` resolution + `requires()` / `uses()` now speak service tids, and
  `validateFeatures` / `AgentFixture` resolve against the union of `offers()`.
- `ConceptFeature` pilot — `AbstractConceptFeature` + three provider-features
  (`TaggingConceptFeature`, `AgentConceptFeature`, `LuceneConceptFeature`), each with its
  own feature tid (`LLM_*_CONCEPT_FEATURE_TID`) and all `offers()`ing
  `LLM_CONCEPT_SERVICE_TID`; selection = which one is attached (no config key, no switch).
  The shared storage/recommendation/skill logic lives on the abstract base; the two seams
  are `extract()` and `systemMessage()`.  (`MessageIndexer` remains in `feature.concept`.)
- `MessageFeature` split — `AbstractMessageFeature` + `TokenMessageFeature` /
  `WindowMessageFeature` (own tids `LLM_TOKEN/WINDOW_MESSAGE_FEATURE_TID`, both `offers()`ing
  `LLM_MESSAGE_SERVICE_TID`); the aggregation algorithm is the single `buildMemory()` seam
  (plus `algorithmName()`).  Every cross-feature `hasFeature(LLM_MESSAGE_FEATURE_TID)` /
  `feature(...).at(SESSION)` site migrated to `service(MessageService.class)`, with a new
  `MessageService.max()` / `sessionVID()` vocabulary; the store now sizes its window from
  `service().max()`.  Both abstract bases override `getRoot()` to keep the namespace stable
  across providers (`message` / `concept`, not the provider's own tid segment).
- `ChatFrame` is the result — `Agent.chat()` returns `ChatFrame`; the pushed frame is the
  `currentResult` (filled with `chat`/`user`/`time` + feature outputs in `onCompleteResponse`,
  written back into the frame URI, popped complete, and returned); `ChatResult` is retired
  (`put`/`putRef`/`watermark`/`watermarks` absorbed into `ChatFrame`), and
  `Feature.onCompleteResponse(Agent, ChatFrame)` replaces the `ChatResult` spelling.
- `ChatStack` carries the recursion level — `push()` stamps `d<depth>` = `agent.chatDepth()` +
  shadow position, so a top-level frame lands at `d1` and agrees with the message ledger's
  `depth` field (`SpaceChatSessionStore.sessionRels`); `depthMap` remains the cross-agent recursion
  seed (recursive sub-agents such as `miniChat` carry no frame feature), pending 1b parent-threading.
- Frame pushed before `onBeforeChat` — the message feature's turn-id advance is split out as
  `MessageService.advanceChatId(Agent)` and the frame feature's stack build as
  `AbstractFrameFeature.prepare(Agent)`, so `Agent.chat()` runs both (then pushes the frame) *before*
  the `onBeforeChat` loop; `userMessage()`/`chatId()` now read the frame (`prompt`/`chat_id`) with the
  Agent fields as the no-provider fallback.
- `TransientFrameFeature` — the persisted/transient provider pair is complete; the `onPopped(Frame,
  fURI)` seam clears the frame for the transient provider and leaves it for the persisted one.
- `summary`/`compact` bodies moved into their features — `SummarizeFeature` owns `summarizeSession`
  (+ `withinScope`, `SUMMARIZE_PROMPT`) and `CompactionFeature` owns `compactSession` (+
  `writeCompaction`, `COMPACT_PROMPT`); the `summary`/`compact` insts now delegate to
  `SummarizeFeature.summarizeSession` / `CompactionFeature.compactSession`.
- Named union dispatch as mtron `choose()` — `session_or_agent::T` is resolved by a
  `choose_(rec(isa_(agent) => instLambda(...), isa_(session) => instLambda(...)))` block (the
  accessor table as data, in the spirit of `Type.Builder`), read once via
  `LedgerUtil.addressOf(Obj) → SessionAddress(sessionVID, agentHome)`.  `rootFor` = `.agentHome()`,
  the `summary`/`compact` insts both accept `session_or_agent` and read the resolved
  `sessionVID` + `agentHome` (fixing `summary`'s non-nullable dom + eager `lhs.asRec()`), and a
  `Function<Obj,Obj>` overload was added to `MInst.instLambda` so an accessor arm is `in -> ...`.
  The arms carry no `.tryToInst()` — only the last instruction in the chain does.
- Ledger ownership consolidated in `AbstractMessageFeature` — the standalone `LedgerUtil` is
  deleted; its session resolution (`addressOf`/`rootFor`/`rootOf`/`SessionAddress`), ledger fsck
  (`sweep`/`sweepSession`/`clean` + the `duplicate`/`orphan`/`misplaced`/`misscoped`/
  `orphan_result` findings vocabulary) and their private helpers are now static members of the
  feature that owns the ledger subgraph.  The `sweep` inst delegates to
  `AbstractMessageFeature.sweep`, mirroring `summary`/`compact`.

Remaining:

1. type-level insts — the mtron face of each capability (like `summary`/`compact`).
2. `Frame` + `ChatFrame` + `FrameService` (spec'd above): the positional `s<sid>/c<cid>/d<depth>`
   frame tree, `Frame` (spine) + `ChatFrame` (replaces `ChatResult`), `FrameService.push/pop/at/
   locals`, `AbstractFrameFeature`/`PersistedFrameFeature`/`TransientFrameFeature`, and the
   `ChatStack` over the existing `depthMap`/`chat_id` plumbing — which is also the field-state
   sweep (transient per-iteration Java fields → locals keys).

## Precedents

- `stackSpace` — root frame + shadow layers, project-on-write, never-pop
- `/sys/machine/+`, `/sys/thread/+` — a frame is a code/state/result rec in space
- `iteration::T` — the durable turn spine (session + index + prev/next)
- tblespace `_mtron_meta` — type -> table -> columns -> FKs
