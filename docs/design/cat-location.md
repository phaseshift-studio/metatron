# cat-location — the fourth lift: places, lanes, resources, located code

**Date:** 2026-10-23 **Context:** The cat graph has three lifts — types as vertices (`object::T`), insts as edges
(`morphism::T`), code
as a vertex (`path::T` below, `abstraction::T` above) — and `plan::T` lifts a rewrite rule into the same graph as
data. Nothing here is implemented; it is a proposal for the **location** coordinate of the same graph, so that
"where to execute" is navigable, prunable and plannable with the machinery the rewrite side already has. **Status:**
proposal. The six types below do not exist; the examples are `mtron_pre` (not executed by the docs
pipeline) because they describe shapes that are not yet registered. **Authority:** this is a design note under
`docs/design/` — a record of a proposal, not a specification. Nothing in
`docs/design/` states what the codebase currently does; the authoritative state is `docs/skills/*` (the agent
references) and the Java sources.

---

## 0. The thesis

Two rules, one obligation.

| rule             | projection | the rule is                   | input → output        | the move         | the obligation           |
|------------------|------------|-------------------------------|-----------------------|------------------|--------------------------|
| **semantic**     | execution  | a `plan::T` **with a cut**    | `path::T` → `path::T` | `lift` / `lower` | the result is equivalent |
| **distribution** | location   | a `plan::T` **without a cut** | `path::T` → `path::T` | `lift` / `bind`  | the result is equivalent |

Both are **equivalence-preserving `code::T` → `code::T` transforms**, and they are one type with one operator (§12):
a cut selects the semantic rule, its absence selects the distribution rule. The semantic rule changes the *shape* of
a computation (`plus(0)` disappears, `sum` becomes a shard tree); the distribution rule changes *where* it runs (a
chain is cut, a tail is shipped, a satellite answers). Neither may change the result set — that is the whole contract,
and it is the
contract the existing shard tests already assert (`checkEquality` against the local evaluation).

The graph already half-implements the second rule's coupling: `PlanBuilder.validateDistributedObligations()`
refuses a plan whose cut is shipped to a `PEER`/`PEERS`/`TREE` target unless it declares `monoidic` and
`commutative`. That is **location → execution constraint propagation**, hard-coded, one direction, two laws. This
document gives it a vocabulary and both directions.

---

## 1. The foldings: every new node is an existing one

The first pass proposed seven new types. Re-read as "this is just a that", six of them turn out to be the graph's
existing types wearing a location coordinate, and only one is genuinely new.

| proposed                       | folds into                                          | why it is the same thing                                                                                                                               | the cost                                                                                  |
|--------------------------------|-----------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------|
| `placement::T`, `rule::T`      | **`plan::T` with no cut**                           | a rule is (site, where); `plan.cut` was already optional, so a cut-less plan *is* the distribution rule — and `rule::T` existed only to unite the two  | the word "plan" now covers both; two preconditions, one type                              |
| `itinerary::T`                 | **`path::T` + `origin?`**                           | a deployment is code plus where it starts; a path with no origin is simply not placeable yet                                                           | `path`'s predicate gains `origin?`, `where?`, `peer?`                                     |
| `geometry::T`                  | **`abstraction::T` + `where?`**                     | the α-word of locations is the same lift as the α-word of instructions — one word per code                                                             | an abstraction gains one optional place-class word                                        |
| `link::T`                      | **`peer::T`**                                       | a dial *is* a peer — `authority`, `host`, `transport`, `client` — already addressable at `~/network/peer/#`; `src`, `scheme` and `state` are derivable | the edge is validated in `/m/mach`, not in the cat graph                                  |
| `scheme::T`, `shape::T`        | **role recs at `/m/math/cat/signature/<token>`**    | both are "a name maps to its roles" — the `theory::T` idea as data; the graph cites them, the resolver never needs them as types                       | no predicate validation (the `Span`/`Route` precedent); promote to a type later if wanted |
| `thread::T`, `virtual`, `core` | **the `thread`/`workers` fields of a decision**     | a thread shares the machine (the executor carries the frame in and publishes it out), so it is the *lane* of a place, not another place                | no new type; the constructors' blocking/await gap must be closed (§13.5)                  |
| a capacity type                | **`place.capacity` + `plan` demand + `env` budget** | capacity is the place's, demand is derived, availability is the env's — no vertex describes them (§14)                                                 | the rewrite filters, the executor admits (§14.5)                                          |
| `env::T`                       | —                                                   | a snapshot value, never a vertex                                                                                                                       | none                                                                                      |

Three refusals, and they are deliberate:

- **`place::T` is *not* `object::T`.** `object::T`'s predicate demands `obj: TYPE_TYPE`; relaxing it so a host fits
  would let every category vertex — a path, an abstraction — test as an object. A place is a `category::T` vertex
  that is *structurally* an object (`obj`, `instset`, derived in/out edges) without claiming the nominal relation.
- **a link is *not* `morphism::T`.** `morphism::T`'s `src`/`trgt` are `T(OBJECT_TID)`; admitting places would weaken a
  central predicate for a type we do not need — the peer already is the edge.
- **a thread is *not* a `place::T`.** It has no authority and no origin of its own; it is the lane *of* a place, so
  the decision carries it (`thread`), and the realized `where` records the thread vids (§13).

The six-sibling table becomes a fold table:

| execution               | location                                   | the fold                              |
|-------------------------|--------------------------------------------|---------------------------------------|
| code vertex `path::T`   | `path::T` + `origin`/`where`               | one vertex; location optional         |
| α-word `abstraction::T` | `abstraction::T` + `where` (place classes) | one lift, two words                   |
| rule `plan::T`          | `plan::T` with **no cut**                  | one rule, two cuts (`lower` / `bind`) |
| edge `morphism::T`      | `peer::T`                                  | the edge set is the peer map          |
| vertex `object::T`      | `place::T` (`category::T`)                 | structural, not nominal               |
| laws `theory::T`        | signature recs                             | role bundles as data                  |

`Route.Target` (LOCAL, PEER, PEERS, TREE, SHUFFLE, NONE) becomes the `shape` token of a `plan.where` decision;
`Route.url`/`hops` become that decision's target and hop budget; `Route.spans` stay the span description, unchanged —
and the Java `Route` class survives as the *builder* of those decisions (a helper, not a type). The reduced record
schema — every field, every reference, what is derived, and the thirteen new tokens — is §11.

## 2. One fibration, two coordinates

The cat doc's planning paragraph already names the structure: an abstract walk computes "not code but a **route** …,
then replayed on the path's code (`path::T.obj`, the γ anchor), where the values and sites exist (**the
path-lifting property of a fibration**)." Location is the **other fiber coordinate** of the same lifts, not a second
graph:

- total objects: `(type, place)`; total morphisms: `(inst, peer)`;
- the projection functor to the execution side is the graph that exists today;
- the fibers are the concrete places, exactly as a fiber over an abstraction is the set of concrete chains.

This is why location is a *coordinate*, not a parallel graph: the one code vertex `path::T` carries it when `origin`
is present, and the one α-word `abstraction::T` carries it in `where`; the place-class word stands for the fiber of
concrete deployments. It is the same lift at every step, `as(cat:X::T)`:

```mtron_pre
[start({1,2,3,4,5}).plus(10).sum()] == [as(cat:path::T)] == [as(cat:abstraction::T)]
   [-- code → α-word; on a located path, abstraction.where is the place-class word --]
```

---

## 3. The coupling: obligations and admissible are two traversals of one edge set

The relation `r ⊨ ℓ` ("shape `r` is sound under laws `ℓ`") is the whole coupling. Its two fibers are queries over
the **same** edge family:

- `obligations(shape)` = the laws a shape demands — forward traversal `plan.where.shape >> law`;
- `admissible(laws)` = the shapes those laws permit — reverse traversal `law// >> plan.where.shape`.

They are monotone and adjoint (`r ⊨ ℓ ⇔ ℓ ⊒ obligations(r)`), which is the precise form of "constraints follow in
both directions". It is not a bijection: several shapes share obligations, and several law sets admit several
shapes — the relation is the object, the projections are its fibers.

Proposed rows (the whole theory is these, and nothing else):

| shape     | obligations                                         | why                                                                                |
|-----------|-----------------------------------------------------|------------------------------------------------------------------------------------|
| `local`   | none                                                | no regrouping, no reordering, no duplication                                       |
| `none`    | `involution` ∨ `idempotent` ∨ `absorbing` ∨ `poset` | the isolated span is *deleted* — the unit law's transform                          |
| `peer`    | `monoidic`, `commutative` (for a recombined cut)    | one route is a one-slave shard                                                     |
| `peers`   | `monoidic`, `commutative`                           | order-free merge across a set                                                      |
| `tree`    | `monoidic`, `commutative` + a hop budget            | recursive re-association of the same fold                                          |
| `chain`   | `monoidic` only                                     | **order-preserving**: a non-commutative monoid is admissible here and nowhere else |
| `shuffle` | `monoidic` + a keyed law (the fold's `derivation`)  | keys route, the value is the per-key fold                                          |

And the second edge family, from the *value* side to the *place* side:

| morphism property     | distribution obligation                                              |
|-----------------------|----------------------------------------------------------------------|
| `pure`                | may be moved, dropped or replicated — any shape                      |
| effectful (no `pure`) | may not be replicated or re-run — `local` for any span it appears in |
| `floatable`           | must be co-located with its input's owner                            |

A third family, for realizability, joins `morphism::T` to a **scheme signature** (this inst may travel only over a
persistent interface, say) and `place.instset` (this chunk resolves only where its instruction sets are mounted).

**Consequence today.** `law_shard`'s lowering checks `Machine.current().network().peers().isEmpty()`, i.e. it gates
distribution on *dialed* peers — a liveness test that dials. Planned over the roster instead (see §4), `shard`
becomes prunable and declines only for want of an *admissible shape*, not for want of an eager dial. And a
non-commutative `monoidic` fold (`str` concatenation) stops being "correctly not sharded" and becomes *admissible on
`chain`* — decline becomes re-route, which is the missing direction of the Galois pair.

---

## 4. Liveness: potential peers, a-priori pruning, may/must

The network has two tables with two meanings, and the location algebra needs both:

- `/network/roster` — **potential** edges: a place, one or more interface addresses, and the *scheme* of each (e.g.
  `<ginger.local> => {<http://192.168.1.250:8777>, <http://192.168.1.72:8777>}`). Knowledge, root-scoped.
- `~/network/peer` — **realized** edges: `peer::T` with `authority`, `host`, `transport` (an inst) and `client`
  (`ws_client::[host, send, send_recv, close, on_message]`). Reach, machine-scoped.

A **candidate dial** is live under a **declared** test, mirroring the rewrite side's type-part/value-residue split. The
test
is a type check, not an address comparison — the required interface class against the place's schemes (which is why
no `where` instruction is needed, §8):

> **live (dial) ⟺ the required interface class is among the place's `authority` schemes, that scheme signature's
> `dialer` resolves in `place.instset`, and the place is in the roster with a state that is not `down`.** No dial is
> attempted to decide liveness.

That is the same bargain the rewriter already strikes: prune on declarations, then match on values. It is what makes
planning over *potential* peers sound, and it makes `bind` (§5) the only step that dials.

The edge state is a lattice — a **candidate** is `known` (roster only); once dialed it is a **peer**, the edge, whose
state is `dialed` (transport present) → `up`/`down` (probe), with `hung up` terminal — which is exactly what
`Network.status()` computes. Feasibility reads `down` as not-a-candidate; cost reads the rest.

The abstract side gets the doc's modality for free: a place-class word whose guard reads no value is a **must**
(every endpoint of the class), one that reads `status`/`cost` is a **may**. The degree ordering becomes
`edges(path) ⊆ edges(abstraction.where)`, and a deployment's remaining ◇-count is the gap between it and that word —
the same accounting the rewrite side already states.

---

## 5. `bind` — the lower-analogue

`lower(plan::T)` is a type method registered in `catInstSet.insts()`: it matches the cut against the path's code,
applies the rewrite, and re-lifts to `path::T`. The location sibling is the same shape:

> **`bind(plan::T)`** — for a path with an `origin` and a plan with **no cut**: resolve the relocation decisions
> against the live topology, dial the peers it needs, emit the located code, and re-lift to `path::T` with the new
> `origin`/`where`. It runs over `path::T.obj`, exactly as γ does.

```
lower(plan with a cut)   : path::T × env → path::T
bind(plan with no cut)   : path::T × env → path::T        [-- requires path.origin --]
```

Both are data-driven and idempotent per `(rule, env)`: the same rule applied twice against the same environment
snapshot yields the same layout, which is what lets a located path be cached or replayed.

---

## 6. The environment: what a rewrite is a function of

The compiler today composes `parse → rewrite → resolve → type` with `Machine.current()` as an implicit argument;
only the ISA is a first-class input. The machine is five components, so the honest signature is

```
rewrite : code::T × (ISA, Network, Processor, Memory) → code::T
```

with the InstSet supplying the algebra of **values** and the Network the algebra of **actions** — its transports and
client callbacks are already `inst`s, merely untyped. Three consequences:

1. **Snapshotted, not live.** The compiler takes one immutable env per `apply()`: the ISA union, the roster, the
   peer map + states, processor capabilities, the data-ownership view. A rewrite must not hold the network open or
   read a status that changes mid-derivation.
2. **Fingerprinted.** The produced code carries the env fingerprint it was planned under, so the receiver's
   "applying a code compiles it, and the rule runs where the part lands" acquires a condition: **re-plan iff the
   fingerprint differs.** Same ISA, same roster, same states → the plan is already valid. That is the receiver-re-plans
   rule stated as a fact about a function, not a habit.
3. **"Where the code starts" is the machine.** The current machine *is* the env; `push` mints a child with its own
   ISA, memory and network levels so that the same source compiled there is a different plan. A hop is a
   re-compilation in a new environment, which is why the tail must ship as source and never as compiled code.

---

## 7. `move()` as the law-free distribution rule

`move(<authority>)` is a placement whose obligations are empty — identity, no combiner — so **every** shape is
admissible and the only constraints left are reachability and vocabulary. That is why it drops into this machinery
without a special case, and it is the first placement to build.

Its emitted layout, per hop, is

```
move(</path>).map(<gathered value>).<tail>          [-- map is the mid-expression start --]
```

inside the transport's `start(P).to(<> )` wrapper. The two topologies from the earlier discussion are then two
*bracketings of the same link composite*:

|            | star                                               | nest                                                       |
|------------|----------------------------------------------------|------------------------------------------------------------|
| binding    | `o→A`, `A→o`, `o→B` — each hop bound by the origin | `o→A`, `A→B` — each hop bound where the computation stands |
| requires   | origin reaches every hop                           | each node reaches the next                                 |
| value path | one round trip per hop through the origin          | values bubble back up the nesting                          |

**Equivalence obligation.** Re-association is result-preserving iff every intermediate `link` exists, the laws hold
at each cut, and intermediate values are serialization-idempotent (strip vids, deterministic re-typing). Where those
fail — local moves after a hop, failure handling location, deadlines, the extra serialization — the difference is a
*documented property of the bracketing*, not an accident, and it is testable as the shard/local equality already is.

---

## 8. Naming

`where` is free, and precisely because **location is not an instruction**: the machine's location is realized as a
type check — `isa(type::T)` — and the URI-filter `where` inst is a leftover of the pre-`isa` world. It is already
disabled (its implementation sits inside a `/* … */` block
in [Uri.java](../../src/main/java/studio/phaseshift/metatron/isa/m/type/Uri.java#L449-L468),
with only the `WHERE_INST_TID` constant and a stale blocking-list entry remaining), and it is being deleted outright.
That leaves the key unambiguous, and it makes the placement guard a type test rather than an address comparison: "is
this place an endpoint of the required interface class?" is an `isa` over the place's type, answered without dialing.
Rec keys are uris in any case — `to`, `from`, `map`, `select`, `route`, `span`, `law` all coexist as keys and,
several of them, as instructions — so the name never needed to be free; it is now also apt. So:

- `plan::T` becomes `[name, cut, where]`, and **`where` holds relocation recs** — a list, one per span group,
  mirroring today's `route` list (each decision is the `Placement` builder's rec: `span`, `shape`, `target`,
  `interface?`, `hops?`, `law?`);
- a decision's `target` is a place (or a place class), so "where to execute" is navigable (`plan>>where>>target`) rather
  than buried;
- a decision's *guard* is `isa` over the place/interface type (§4's declaration-only test), the same distinction the
  rewrite side draws between the type-part and the value-residue of a guard;
- the word *route* is left to the rewrite side, where the cat doc already uses it for the sequence of rule names
  chosen in the abstraction. `plan.where.shape` carries the network meaning.

---

## 9. Increments

1. **`plan.where`.** Rename the `Route` builder to `Placement` (keeping `Route.Target` → `shape`, spans unchanged),
   make the cut optional in the constructor's validation, and have `plan.where` hold a list of relocation recs. No
   new type.
2. **`place::T`, read-through.** Materialize places from `Network.Helper.authorities()`, the roster and the peer map —
   the peer map *is* the edge set, and `linked_to`/`linked_from` are derived over it. A view, like `PeerSpace`.
3. **Shape signatures + `obligations`/`admissible`.** Store the seven shape recs at
   `/m/math/cat/signature/<token>`; move `validateDistributedObligations()` into the traversal
   `cut.law ⊇ shape.law`, and add the reverse traversal.
4. **Pruning.** Replace `shard`'s `peers().isEmpty()` gate with the declared liveness test (roster × scheme
   signature × place.instset), so candidates are plannable and `bind` does the dialing.
5. **Located `path` + `abstraction.where` + `bind`.** Extend the two existing types and register
   `bind?path<=path(plan)` beside `lower`.
6. **The `move` rule.** A cut-less plan; star first (origin-orchestrated, one decision per hop, `map(<value>)` per
   seam), then nest as a per-node policy.
7. **Env snapshot + fingerprint.** Thread the env through the compiler stages; stamp it on produced code.
8. **Cost + chooser.** Weight peers with RTT/bytes/serializations and choose the cheapest admissible shape —
   `PlanBuilder` becomes cost-based rather than rule-ordered.
9. **Lanes.** Add the `lanes` shape rec and the `thread`/`workers` decision fields; make the emitter's lane arm spawn
   non-blocking and the latch await; derive the lane kind from `pure` (§13.3).
10. **Resources.** `place.capacity` with discovery + a boot override; the `env` budget; the demand vector, the
    feasibility filter and the `workers` clamp; admission + release in `ThreadExecutor`; daemon reservation with
    `loop`; then cost v0 (unit model) and the constrained chooser (§14).

**Tests.** (a) star / nest / local over one chain, equal modulo vids — the functoriality check; (b) the pruner is
declaration-only: a candidate is plannable without a dial, and `bind` is the only step that connects; (c) the alias
quotient: `0.0.0.0`, `127.0.0.1`, `::1` and the interface address denote one place (`isLoopbackHost` /
`sameService`); (d) `obligations`/`admissible` are adjoint — the same rows read both ways.

---

## 10. Open questions

- **Is a signature worth a type?** Schemes and shapes are recs now; promoting them to one `signature::T`
  (`tid(theory::T)`, a map predicate) buys predicate validation and `docWrap` at the cost of one type. Leaning: no
  for now — the obligations traversal does not need it.
- **Should `place` ever become an `object::T`?** That needs `object::T`'s `obj: TYPE_TYPE` relaxed, and it would
  admit every category vertex as an object. Leaning: keep the structural, not nominal, relation (§1).
- **Cost semiring.** RTTs, bytes, serializations — one semiring or a lexicographic order? The star/nest difference
  shows serialization count is a real term, not a rounding error.
- **Fingerprint granularity.** Whole-env hash, or per-coordinate (ISA set, roster set, states)? Per-coordinate lets a
  plan be partially reusable; whole-env is simpler and safe.
- **Lane cost terms.** A spawn plus a core/memory bound — one cost semiring with the wire terms, or a second one? And
  does a core lane draw from `place.cost.cores` at create or at apply?
- **Where the quota is enforced.** The frame's processor carrying `resource`, or only the executor's live lease? The
  processor makes the quota part of the machine tree (push divides it), the executor makes it enforceable. Leaning:
  both — the processor declares, the executor enforces.
- **Cgroup-aware capacity.** `OperatingSystemMXBean` reports the host, not the container. Does the place declaration
  win, or the cgroup file? Leaning: the smaller, with a boot warning.
- **The vocabulary coordinate** is not yet an algebra. A chunk is realizable only where its instructions resolve;
  until `place.instset` is joined at binding, "admissible" is optimistic. This is the third space, and it is the one
  that bites in practice.

---

## 11. The record schema (reduced)

**One new type (`place::T`), three extensions (`path`, `abstraction`, `plan`), and everything else already exists.**
Field by field, so it can be transcribed into `catInstSet` / `catTypes` without re-deciding anything.

### 11.0 Rules the schema obeys

1. **Every type is a `rec::T` with no Java fields.** State lives in the rec's map; accessors read `this.at(key)` /
   `this.atDirect(key)`, as `morphism::T`, `object::T` and `path::T` do.
2. **Keys are uris drawn from `Tokens`.** Reuse first: `obj`, `authority`, `instset`, `source`, `cost`, `scheme`,
   `state`, `transport`, `status`, `cut`, `law`, `span`, `flags`, `target`, `hops`, `interface`, `name`, `result`.
   Thirteen are new: `where`, `origin`, `shape`, `dialer`, `capability`, `linked_to`, `linked_from`, `thread`,
   `workers`,
   `capacity`, `cores`, `memory`, `budget` (§11.9).
3. **A reference is an embedded rec, a token uri (resolved through the graph), or a vid.**
4. **Derived sets are `auto` insts, never stored** — the `object::T.morphed_to/from` precedent. `place.linked_to`,
   `place.linked_from`, `place.status`, `place.cost` are computed; no index to maintain, no staleness.
5. **The network component is the truth.** A place is a **read-through projection** of the roster and the peer map (the
   `PeerSpace` precedent), and the **edges are the peer map itself** — no edge type is introduced.
6. **No new families.** `place::T` is `tid(CATEGORY_TID)`; `path::T`, `abstraction::T` and `plan::T` keep their tids
   and vids. `plan.cut` was already optional, which is what lets the distribution rule be a plan.
7. **Nothing that is a query becomes a type; nothing that is a peer becomes an edge type; nothing that is a role map
   becomes a type** (§11.10).

### 11.1 The reference map

```
place::T ──authority{+}────────▶ interface uris (the scheme is the uri's scheme)
     │  ──instset{*}───────────▶ InstSet vids (the vocabulary there)
     │  ◀─linked_to / linked_from─  the peer map (derived; out- and inbound dials)

path::T ──obj──▶ code::T            path::T ──origin? / where?──▶ place::T
      │  ──‹rule name›─────────────▶ plan::T   (out-edges: semantic or distribution)
      │  ──peer{*}─────────────────▶ the peer recs the deployment used

abstraction::T ──obj──▶ [ morphism… ]     abstraction::T ──where?──▶ [ place patterns… ]

plan::T ──cut?──▶ (form | law | role)     plan::T ──where{+}──▶ { span, shape, target, interface?, hops?, law? }
                                                    │              │
                                                    │              └── shape token ──▶ signature rec ──law──▶ law
                                                    └── target ──▶ place::T (or a place pattern)
```

| from.field                        | to                | kind            | meaning                                         |
|-----------------------------------|-------------------|-----------------|-------------------------------------------------|
| `place.obj`                       | host              | embed           | the vertex: a machine name, not an alias        |
| `place.authority`                 | interface uris    | value           | every address the place answers at              |
| `place.instset`                   | instset vids      | token           | the ISAs resolvable there                       |
| `place.linked_to` / `linked_from` | peer recs         | **derived**     | the dialed edges, out and in                    |
| `path.obj`                        | `code::T`         | embed           | the chain                                       |
| `path.origin`                     | `place::T`        | embed           | where the code starts (absent ⇒ not placeable)  |
| `path.where`                      | span → `place::T` | embed           | the realized placement                          |
| `path.peer`                       | peer recs         | embed           | the edges the deployment used                   |
| `abstraction.obj`                 | `morphism::T{*}`  | embed           | the α instruction word                          |
| `abstraction.where`               | place patterns    | embed           | the α place-class word                          |
| `plan.cut`                        | a cut selector    | embed           | form/law/role; **absent ⇒ a distribution rule** |
| `plan.where`                      | relocation recs   | embed           | span + shape + target (+ interface, hops, law)  |
| `plan.where.shape`                | a signature rec   | token           | the topology and its obligations                |
| `plan.where.interface`            | a signature rec   | token           | the required interface class                    |
| `plan.where.target`               | `place::T`        | embed / pattern | an authority, or a place class                  |

### 11.2 `place::T` — the only new type

`tid(category::T)`, `vid(/m/math/cat/category/place)`.

```mtron_pre
place::[obj       => //ginger.local/a/b,      [-- the machine: host + frame path --]
        authority => {<ws://ginger.local:8555>, <http://192.168.1.250:8777>},
        instset   => {</m>, </m/web>, </m/tble>},
        source    => <mdns>,
        mounts    => {</usr/dr>, </usr/notes>},          [-- the data coordinate, §14.7 --]
        linked_to   => {peer::[…]},       [-- derived: outbound peers whose service is this place --]
        linked_from => {peer::[…]},       [-- derived: inbound peers from this place --]
        status    => <up>,                [-- derived: aggregate over the dials --]
        cost      => [rtt=>12]]           [-- derived: min over the dials --]
```

`obj` is a **machine address**, `//host[/path]`, read as `obj.host()` (the roster key) and `obj.path()` (the frame
vid; empty = the host's root machine `/.`). It is the machine that evaluates — *not* a separate `machine` field, and
not the transport. `authority` is the set of **transports** that reach that machine (the scheme-carrying, plural
side); `obj` is scheme-less identity, `authority` is scheme-carrying reach.

| field         | type     | presence    | meaning                                                                                           |
|---------------|----------|-------------|---------------------------------------------------------------------------------------------------|
| `obj`         | `uri`    | required    | the machine address `//host[/path]` — who evaluates (`host()` = roster key, `path()` = frame vid) |
| `authority`   | `uri{+}` | required    | the transports that reach it; two aliases of one machine are **values**, never two places         |
| `instset`     | `uri{*}` | optional    | ISAs resolvable here (the vocabulary coordinate)                                                  |
| `source`      | `uri`    | optional    | who declared it (`mdns`, boot profile, a dial)                                                    |
| `mounts`      | `uri{*}` | optional    | the spaces **owned** here (the data coordinate, §14.7)                                            |
| `linked_to`   | `rec{*}` | **derived** | outbound peers to this place                                                                      |
| `linked_from` | `rec{*}` | **derived** | inbound peers from this place                                                                     |
| `status`      | `uri`    | **derived** | `up` / `down` / `known`                                                                           |
| `cost`        | `rec`    | **derived** | `[rtt, bytes]`, the price, the minimum over the dials                                             |
| `capacity`    | `rec`    | optional    | `[cores, memory]` — the **effective** bound (cgroup/`ActiveProcessorCount` aware), §14            |

The edge values are the network's own `peer::T` recs — typed and validated in `/m/mach`, reachable at
`~/network/peer/<key>` — so the cat graph never declares an edge type and never depends on mach.

### 11.3 `path::T` — extended (code vertex, located)

`tid(category::T)`, vid unchanged; **this absorbs `itinerary::T`.** The three optional fields turn a path into a
deployment; a path without `origin` is simply a program that cannot be placed.

```mtron_pre
path::[obj    => start([start?hops=0({1,2,3}).plus(10).sum()]).select([to(ws://ginger.local:8555)]).merge().sum(),
       origin => place::[obj=>//booger.local, …],
       where  => [[span=>at] => place::[obj=>//ginger.local, …]],
       peer   => {peer::[…]},
       law_shard => plan::[…]        [-- out-edge per live rule, the path::T pattern --]
      ]
```

| field         | type       | presence | meaning                                       |
|---------------|------------|----------|-----------------------------------------------|
| `obj`         | `code::T`  | required | the chain (unchanged)                         |
| `origin`      | `place::T` | optional | where the code starts; **`bind` requires it** |
| `where`       | `rec`      | optional | span → place; the realized placement          |
| `peer`        | `rec{*}`   | optional | the peers the deployment used                 |
| `‹rule name›` | `plan::T`  | out-edge | one per live rule — semantic or distribution  |

### 11.4 `abstraction::T` — extended (the α-word, located)

`obj` is unchanged (the pointwise `morphism::T` word). Add one optional word:

| field   | type             | presence | meaning                                                                                   |
|---------|------------------|----------|-------------------------------------------------------------------------------------------|
| `obj`   | `morphism::T{*}` | required | the α instruction word (unchanged)                                                        |
| `where` | `category{*}`    | optional | the α **place-class** word: `place::T` patterns — `any ws peer`, `any place with /m/tble` |

```mtron_pre
abstraction::[obj   => [morphism[start]::…, morphism[plus]::…, morphism[sum]::…],
              where => [place::[obj=>'*', authority=>{<ws://*>}, instset=>{</m>}]]]
```

The plan guard reads `obj`; the placement guard reads `where`; there is one lift, `as(cat:abstraction::T)`.

### 11.5 `plan::T` — the one rule (extended)

`tid(rec::T)`, vid unchanged. `name`, `result?`, and:

- **`cut?`** — a selector `[form?, law?, role?]`. **Present ⇒ a semantic rule** (`lower`); **absent ⇒ a distribution
  rule** (`bind`).
- **`where{+}`** — the relocation decisions, each an inline rec (the `Route` shape, not a type):
  `{span: [{span, flags?}], shape, target, interface?, hops?, thread?, workers?, law?, inst?}`. A distribution rule
  has exactly one. **`inst?`** (2026-10-24) — the instructions the target must serve, keyed as a client's `inst`
  names them: the shape supplies the default (every distributing shape ships a program through `to(<target>)`, so it
  requires `/m/inst/to` for the route and `/m/inst/eval` to apply what the route carries — an http POST `to` writes
  and evaluates nothing) and a placement may add what its shipped chunk needs. A dialed peer takes the placement when it is
  online and its client overrides each one (`Places.serves`) — the realized half of §4's liveness test.

```mtron_pre
[-- semantic + distribution, today's law_shard --]
plan::[name  => <law_shard>,
       cut   => [form=>reducer, law=>[monoidic, commutative]],
       where => [[span=>[[span=>seed, flags=>[partition]]],      shape=><tree>,  target=><ws://ginger.local:8555>],
                 [span=>[[span=>at],[span=>post]],               shape=><local>]]]

[-- distribution only: a cut-less plan (the old placement::T) --]
plan::[name  => <move_star>,
       where => [[span=>[[span=>at]], shape=><star>, interface=><ws>, target=><ws://*>, hops=>1]]]

[-- distribution over lanes: one machine, four virtual lanes (no target) --]
plan::[name  => <shard_lanes>,
       cut   => [form=>reducer, law=>[monoidic, commutative]],
       where => [[span=>[[span=>seed, flags=>[partition]],[span=>pre],[span=>at]],
                  shape=><lanes>, thread=><virtual>, workers=>4]]]
```

The `isaPredicate` gains one row, next to `uri(CUT)`:

```java
uri(WHERE).

maybe().

asUri(),lst(

rec(
        uri(SPAN),lst(

rec(uri(SPAN),URI_TYPE,uri(FLAGS).

maybe(),lst(URI_TYPE))),

uri(SHAPE),URI_TYPE,

uri(TARGET),ALL_TYPE,

uri(INTERFACE).

maybe().

asUri(),URI_TYPE,

uri(HOPS).

maybe().

asUri(),JNT_TYPE,

uri(THREAD).

maybe().

asUri(),URI_TYPE,

uri(WORKERS).

maybe().

asUri(),JNT_TYPE,

uri(LAW).

maybe().

asUri(),lst(LAW_TYPE)))
```

### 11.6 signatures — schemes and shapes as role recs

Both are "a name maps to its roles", which is what `theory::T` already means; they are **data the plan cites**, not
types the resolver needs. Store them at `/m/math/cat/signature/<token>` and register the short-name aliases (`star`,
`ws`, …) the plans use.

```mtron_pre
[-- at /m/math/cat/signature/star --]   signature::[law => [monoidic, commutative]]
[-- at /m/math/cat/signature/chain --]  signature::[law => [monoidic]]
[-- at /m/math/cat/signature/none --]   signature::[law => [involution, idempotent, absorbing, poset]]
[-- at /m/math/cat/signature/lanes --]  signature::[law => [monoidic, commutative]]

[-- at /m/math/cat/signature/ws --]     signature::[scheme     => <ws>,
                                                    dialer     => </m/web/ws/ws_client>,
                                                    handler    => </m/web/ws/mtron_ws>,
                                                    flow       => <session>,
                                                    capability => [push, subq, correlation],
                                                    cost       => [handshake=>ms, per_message=>bytes]]
[-- at /m/math/cat/signature/http --]   signature::[scheme => <http>, flow => <request_response>, …]
```

The seven shape rows are the seven shape recs — the obligation table is data. If predicate validation is ever
wanted, promote `signature` to a registered type (`tid(theory::T)` with a `rec(URI, ALL)` map predicate); that is an
addition, not a schema change.

### 11.7 `env` — a snapshot, not a type

One **value** per compilation (§6), never a vertex: a plain rec `[instset => {…}, place => {place…}, signature => {…},
origin => place::[…], capacity => [cores=>{total,free}, memory=>{total,free}, lanes=>{leased,cap}],
env => <fingerprint>]`. The fingerprint rides the produced code as a q-processor
(`code.tid().addQ(ENV, uri(fingerprint))`), the `?hops`/`?block`/`?path` mechanism. "Re-plan iff the fingerprint
differs" is then a q comparison.

### 11.8 The lifts and the operators

| registration                   | meaning                                                                     |
|--------------------------------|-----------------------------------------------------------------------------|
| `as(…)` → `cat:place::T`       | a roster row / host → a place                                               |
| `as(…)` → `cat:path::T`        | code → a path (unchanged); an origin is attached when the machine has one   |
| `as(…)` → `cat:abstraction::T` | a path → its α-word (instruction word + place-class word)                   |
| `lower?path<=path(plan)`       | apply a rule **with a cut**: locate, place, emit, re-lift                   |
| `bind?path<=path(plan)`        | apply a **cut-less** rule to a path with an origin: re-route, emit, re-lift |

`lower` and `bind` are the *same body* (§12.1) with different preconditions; they stay two names because they are two
kinds of equivalence (semantic and distribution).

### 11.9 New tokens (thirteen)

| token         | for                                             | analogue       |
|---------------|-------------------------------------------------|----------------|
| `where`       | `plan.where`, `path.where`, `abstraction.where` | `cut`          |
| `origin`      | `path.origin`                                   | `src`          |
| `shape`       | a relocation decision's topology                | `target`       |
| `dialer`      | a scheme signature's client type                | `op`           |
| `capability`  | a scheme signature's features                   | `law`          |
| `linked_to`   | `place.linked_to`                               | `morphed_to`   |
| `linked_from` | `place.linked_from`                             | `morphed_from` |
| `thread`      | a decision's lane kind (`virtual`, `core`)      | `shape`        |
| `workers`     | a decision's fan-out width                      | `hops`         |
| `capacity`    | `place.capacity`                                | `cost`         |
| `cores`       | capacity / quota / budget                       | `hops`         |
| `memory`      | capacity / budget                               | `hops`         |
| `budget`      | `env.capacity`                                  | —              |

Everything else is an existing key. `instset` on a place is the existing `category::T.instset` with its original
meaning — the ISAs from which morphisms are generated *here* — which is exactly the vocabulary coordinate.

### 11.10 Deliberately not a type

- **The edges.** The peer map is the edge set (`~/network/peer/#`), typed in `/m/mach`, read through by place.
- **A candidate dial.** The query *place × scheme ∈ place.instset* (§4); a dial that materializes is a peer.
- **Schemes, shapes, signatures.** Role recs at well-known addresses (the `Span`/`Route` precedent).
- **Status, cost, order, arity.** Derived (§11.0 rule 4), so no two nodes can disagree.
- **The fingerprint.** A q-value on the code, not a node.
- **`env`.** A snapshot of one compilation, not the world.
- **A thread.** Existing types (`thread::T`/`virtual`/`core`); a decision field, not a vertex (§13).
- **A resource.** Capacity is a place field, demand is derived, availability is the env's; no vertex (§14).
- **The old names.** `itinerary`, `geometry`, `placement`, `scheme`, `shape`, `link`, `rule` are *coordinate names* in
  this document, not types: each resolved into the node above it (§1).

### 11.11 Registration sketch

```java
// catInstSet — the only new TID
public static final fURI PLACE_TID = CATEGORY_TID.extend("place");
public static Type PLACE_TYPE;

// catTypes — place (the one new block), plus three predicate deltas on existing types
PLACE_TYPE =Type.Builder.

build()
        .

tid(CATEGORY_TID)
        .

vid(PLACE_TID)
        .

isaPredicate(rec(
        uri(OBJ),union_(STR_TYPE,URI_TYPE),

uri(AUTHORITY),lst(URI_TYPE),

uri(INSTSET).

maybe(),lst(URI_TYPE),

uri(SOURCE).

maybe(),URI_TYPE,

uri(COST).

maybe(),REC_TYPE,           // derived

uri(LINKED_TO).

maybe(),lst(REC_TYPE), // derived

uri(LINKED_FROM).

maybe(),lst(REC_TYPE),// derived

uri(STATUS).

maybe(),URI_TYPE))        // derived
        .

constructor(arg ->

place(arg))
        .

create();

// PATH_TYPE      += uri(ORIGIN).maybe().asUri(), T(PLACE_TID),
//                    uri(WHERE).maybe().asUri(), rec(...),
//                    uri(PEER).maybe().asUri(), lst(REC_TYPE)
// ABSTRACTION_TYPE += uri(WHERE).maybe().asUri(), lst(T(CATEGORY_TID))
// PLAN_TYPE      += uri(WHERE).maybe().asUri(), lst(rec(span, shape, target, interface?, hops?, law?))
//                    (cut already optional — nothing else changes)
```

The constructors keep the three obligations this family has: normalize (`arg.isRec() && has(OBJ)` ? arg : `rec(OBJ,
arg)`), derive the auto insts, `selfTID`. `path::T`, `object::T` and `morphism::T` are the templates.

## 12. PlanBuilder, PlanInterpreter, and the plan/path commingling

### 12.1 One type, two cuts

A rewrite rule answers two questions: **which site** it rewrites, and **where the piece goes**. A plan names the site
*legally* (a cut: form + laws + role, Thm 17); with no cut it is the **distribution rule** — the span boundary is its
site. Same type, same operator:

```
plan::T  [name, cut?, where{+}, result?]
 ├── cut present  → a semantic rule      lower(plan)
 └── cut absent   → a distribution rule  bind(plan)
```

`apply(plan, env)` is the one body: **locate** the site (Thm 17 for a cut, the decision's span for a distribution
rule) → **resolve** `where` over the env (prune, cost, choose) → **emit** through the shared emitter → **retype** →
**re-lift** and **record** the realized `where`. `lower` requires a cut; `bind` requires a path with an origin and a
cut-less plan. They stay two names because they are two kinds of equivalence, but there is one interpreter and one
record.

### 12.2 One code vertex, one α-word, one rule

|             | semantic (execution)             | distribution (location)                             |
|-------------|----------------------------------|-----------------------------------------------------|
| rule        | `plan::T` with a `cut`           | `plan::T` with no `cut`                             |
| code vertex | `path::T` (`obj` only)           | `path::T` with `origin`/`where`/`link`              |
| α-word      | `abstraction::T.obj` (morphisms) | `abstraction::T.where` (place classes)              |
| operator    | `lower`                          | `bind`                                              |
| lift        | `as(cat:path::T)`                | `as(cat:path::T)` + the machine's place as `origin` |

Three observations that make the commingling precise:

- **Location is optional on the one code vertex.** A path without `origin` is a program that cannot be placed; the
  location lift attaches the machine's own place, and `bind` then requires it. There is no second vertex type.
- **`where` is one key in three tenses.** `plan.where` is *authored* (the relocation decisions the rule ships);
  `path.where` is *realized* (span → place, written by the pass that applied the rule); `abstraction.where` is
  *abstracted* (place classes). Same coordinate, same key, three stages — which is why one lift can produce both.
- **A placed sub-path carries its own origin.** When `apply` ships a piece to a target, the emitted code's `origin`
  is that target place — so if the piece itself contains a cut-less plan, the next hop knows where it stands. The
  realization is data on the code, not a side table.

### 12.3 They do not commute — `lower` invalidates `bind`

`bind ∘ lower ≠ lower ∘ bind` in general, and the asymmetry is the useful part.

A plan's spans are authored in the **pre-lower** chain: `seed`, `pre`, `at` and `post` name regions of the chain the
cut was found in (`{seed, floatable, reducer}`), and the emitter turns those regions into the post-lower layout
(`start([…]).select([to(…)]).merge()`). A cut-less plan's span names a boundary in the **current** chain. So a
distribution decision is valid only for the code it was authored against:

> **`lower` invalidates `bind`; `bind` does not invalidate `lower`.** Re-routing does not change the code; rewriting
> the code changes the spans.

Implementation: a path carries its `obj` plus the code fingerprint its `where` was computed for (`?env`, §11.7);
`bind` recomputes when the fingerprint differs; and `apply` always consumes `where` *after* the cut, in the same pass,
so a plan's decisions can never be pre-lower. This is also the authoring rule: plan spans are pre-lower coordinates,
target decisions are post-cut regions.

### 12.4 `PlanBuilder` — what changes

Today `create()` assembles a jvm map and calls `new Plan(...)` directly, and `validateDistributedObligations()`
hard-codes "distributed ⇒ monoidic + commutative". After:

1. **`create()` mints through `PLAN_TYPE`**, not `new Plan(…)` — the builder accumulates, the constructor normalizes,
   derives and `selfTID`s (`morphism::T` / `object::T` are the templates).
2. **`route(Route…)` → `where(Placement…)`.** `Placement` survives as a *builder* of the relocation rec (it is the
   old `Route`, renamed), not as a registered type; the deprecated `route` overload builds one for read-compat.
3. **`validateDistributedObligations()` disappears.** `create()` checks: `cut?` present means the shape recs'
   `law` ⊆ `cut.law` (the §3 table is now the shape recs, read as data); every `shape`/`interface` token resolves to a
   signature rec; span kinds are well formed; `hops` appears only on a `tree`/`chain` shape; `workers` only on a
   `lanes` decision and never above `place.cost.cores` for a `core` lane; a cut-less plan has exactly one decision.
4. **No reachability at create.** Peers are an environment fact (§6), so `create()` is pure over
   `(definition, registry)` and `apply` performs the env join.
5. The registry is unchanged in shape: `catRewrite` holds `PLANS` by name, `planOf(name)` answers one, and each
   rewrite inst is `rewriter(rewriteTID(name), code -> PlanInterpreter.apply(plan, Env.snapshot()))`.

```java
final Plan shard = PlanBuilder.plan("law_shard")
        .cut(Cut.reducer(Law.monoidic, Law.commutative))
        .where(Placement.of(SHAPE_TREE).
interface(SCHEME_WS).

hops(1)
                        .

spans(Span.seed().

partition(),Span.

before().

floatable(),Span.

at()),
        Placement.

of(SHAPE_LOCAL).

spans(Span.at(),Span.

after()))
        .

create();

// the intra-machine sibling: the same fold, no peers required
final Plan lanes = PlanBuilder.plan("law_shard_lanes")
        .cut(Cut.reducer(Law.monoidic, Law.commutative))
        .where(Placement.of(SHAPE_LANES).thread(THREAD_VIRTUAL).workers(4)
                .spans(Span.seed().partition(), Span.before().floatable(), Span.at()))
        .create();
```

### 12.5 `PlanInterpreter` — what changes

| today                                                                                                                                             | after                                                                                                                                                                    |
|---------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `lowerCut(plan, code)` switches on `cut.role()` / `cut.form()`                                                                                    | `locate(plan, code)` keeps that switch (Thm 17 unchanged); a cut-less plan's site is its decision's span                                                                 |
| `shard(plan, code)` reads `Machine.current().network().peers()`, `hops(plan, code)`, and builds `start([…]).select([to(…)]).merge()` + the retype | `resolve(where, env)` prunes over the declared topology and picks a shape; `emit(shape, regions, targets)` builds the layout — the *same* builder, callable by any shape |
| a `Route` per plan                                                                                                                                | the decision recs: `span`, `shape`, `target`, `interface`, `hops`, `thread`, `workers`, `law`                                                                            |
| the peer gate inside `shard`                                                                                                                      | `env.candidates(decision)` — declared liveness (§4), no dial                                                                                                             |
| the interpreter reads `Machine.current()`                                                                                                         | `apply(plan, env)` takes the snapshot; nothing inside reads the machine                                                                                                  |

- **One emitter.** `shard`'s assembly generalizes: `tree`/`peers` → `select` + `merge`; `chain`/`peer` →
  sequential forwards; `lanes` → spawn one `thread(|part)` per worker and fold at the latch; `local`/`none` →
  keep/unit. `law_shard` becomes `emit(tree)`, `move` `emit(chain|star)`, `shard_lanes` `emit(lanes)`, sharing
  `withoutHops` and the `carriers` retype.
- **The lane arm needs an await.** The spawn must be non-blocking (`thread(code)` today; the typed constructors
  resolve their future and would serialize the fan-out) and the latch must await each thread's `future`/`result`
  before folding — `merge` currently only unwraps the list (§13.5). The candidate gate becomes
  `peers ∪ lanes`, which is what lets a peerless machine shard.
- **One check.** `obligations`/`admissible` are traversals (§3), so the interpreter can *step down* a shape instead of
  declining: non-commutative → `chain`, no monoid → `local`.
- **One pass, both halves.** After emission the interpreter writes the realized `where` (span → place, peers used) and
  the new `origin` onto the resulting path. That write *is* `bind`; it belongs to the same pass because it consumes
  the regions the cut produced (§12.3).
- **Two insts.** `lower?path<=path(plan)` requires a cut; `bind?path<=path(plan)` requires an origin and a cut-less
  plan. Both call `apply`.

### 12.6 Risks

- **No central predicate is relaxed.** `place::T` is the only new type; `path`/`abstraction`/`plan` gain optional
  fields. The type-system risk the earlier draft carried (`rule::T` re-parenting `plan::T`, `object`/`morphism`
  predicate relaxation) is gone with the foldings.
- `path::T`'s predicate gains three optional fields; test that `as(cat:path::T)` still lifts and that out-edge
  liveness is unchanged.
- The emitter refactor touches the tested `shard` path; the shard-vs-local equality tests are the guard.
- The pre-lower span coordinate must be documented for placement authors, or plans will be authored against
  post-lower spans and silently capture nothing.
- One key, three tenses (`plan.where`, `path.where`, `abstraction.where`) must be stated where each is read.
- The lane arm is blocked on §13.5: the constructors block and `merge` does not await. Either the constructors return
  the handle (and the latch awaits), or the emitter uses `thread(code)` and adds an await step.
- The lane rule (`pure` → `core`, effectful → `virtual`) must be validated: a part that looks pure but blocks (a
  space read that is a database hop) would pin a core thread.

### 12.7 Delta on the increments (§9)

§9's increment 1 now reads: `plan.where` + `Route`→`Placement` builder + drop the placement/marker types (already
folded). Increment 5 (`bind`) no longer adds `itinerary`/`geometry` — it extends `path` and `abstraction` and wires
`bind` to the existing `apply`. The emitter factor and env threading (§12.5) precede it.

---

## 13. Threads: the lane coordinate (a place at distance zero)

### 13.1 What a thread is, location-wise

`thread::T` is `[code, source?, time?, runtime?, yield?, loop?, state?, result?]`, refined by `virtual::T` and `core::T`
(`machInstSet` lines 134-147). The executor is the point: `ThreadExecutor.execute(AbstractThread)` captures the
caller's `Machine.current()` and sets it on the worker, then publishes the worker's landing back (`ThreadExecutor` lines
221-242). So a thread **inherits the frame** — memory, instset, network, origin — and differs
only in the executing processor.

That makes a thread the **place at distance zero**: the same site, another lane. It is *not* a `place::T` — no
authority, not network-reachable, no origin of its own — so it needs no location type of its own. It is the **lane**
of a place.

### 13.2 The third axis of a decision

A relocation decision now reads: *which region* (`span`), *combined how* (`shape`), *at which place* (`target`,
absent = the origin), *on which lane* (`thread`), *how many* (`workers`), *repeating* (`loop`).

| field     | type   | meaning                                                                                         |
|-----------|--------|-------------------------------------------------------------------------------------------------|
| `thread`  | `uri`  | a lane kind: `<virtual>` (IO-bound), `<core>` (CPU-bound); absent = stay on the master's thread |
| `workers` | `int`  | the fan-out width at one place; clamped by parts and by `place.cost.cores` for core lanes       |
| `loop`    | `time` | the **daemon** variant: a persistent stage, not a fan-out (§13.7)                               |

A new shape rec, `lanes`, is the intra-place fan-out: fan out over the origin's lanes (or over `target`'s lanes),
combine through a latch. Its laws are exactly `peers`' — `{monoidic, commutative}` — because the parts are
re-associated into one fold, only without leaving the machine:

```mtron_pre
[-- at /m/math/cat/signature/lanes --]  signature::[law => [monoidic, commutative]]
```

### 13.3 What the planner gains

1. **A peerless machine can shard.** The candidate set becomes *peer places ∪ local lanes*; today's
   `shard` declines on `peers().isEmpty()`, which is exactly the case a lane fan-out is for.
2. **Menu entry D becomes a lowering, not a spelling.** Its hand-written
   `[virtual::[code=>to(s1)], virtual::[code=>to(s2)]]` is what the emitter now produces: an IO-bound peer hop is a
   `virtual` lane, a CPU-bound part a `core` lane.
3. **The lane rule is derived, not declared.** A `pure` chunk is CPU-bound → `core`; an effectful chunk (a network or
   DB hop) is IO-bound → `virtual`; a mixed part takes `virtual`. This reads the existing `pure` law (§3), the same
   way `obligations` reads the shape's laws — no new law, no new declaration.
4. **Cost changes shape.** A lane has no RTT, no bytes and no serialization; it has a spawn and a core/memory bound.
   `place.cost` gains `cores` and `spawn`, the env snapshots the capacity, and the chooser compares lanes against
   peers instead of assuming one is cheaper.
5. **One lane level.** A part that itself fans out over lanes should be flattened by the planner; nested lane fan-out
   is a thread explosion, not a plan.

### 13.4 Obligations

`lanes` needs what `peers` needs — `{monoidic, commutative}` — **plus `pure`**, because the parts interleave effects
in one address space. A `blocked` inst must stay on the master's lane. The fidelity is *better* than a peer hop: no
serialization boundary, so no vid/tid re-homing and no round-trip loss. The hazard is the opposite one: shared memory
means a part that writes an address the master reads is a race, so `pure` is load-bearing here rather than decorative.
Failures behave as everywhere else: a thread's future can hold a fail or a timeout, and the latch must surface it — a
`STOP`/interrupt is a fail, not an empty result.

### 13.5 The gather needs an await — a real gap

The typed constructors **block**. `core::[code=>…]` and `virtual::[…]` are type constructors whose body returns
`applyAsync(lhs)`; `Processor.Helper.applyFunction` resolves every returned `FutureObj`, so the constructor waits for
the thread's first result (`FutureObj.resolveFuture`, lines 287-293). A list of such constructors is therefore built
*sequentially*.

The non-blocking spawn is the `thread(code)` instruction (`machInstSet` lines 622-629): it `applyAsync`s and returns the
thread handle at once. So a lane fan-out needs two things the current emitter does not have:

- a **non-blocking spawn** per part — `thread(|part)` today, or the typed constructors changed to return the handle;
- an **awaiting latch** — `merge` only unwraps a list (`objs(lhs.elements())`); it does not resolve thread futures. The
  gather must await each thread's `future`/`result` (and surface a fail) before folding.

That is the one implementation gap between this design and a working lane fan-out.

### 13.6 Frames vs lanes

Placements never `push` and never `move`: a lane shares the frame. The three axes stay separate — **frame** (the
code's `move`/`push`), **site** (the decision's `target`), **lane** (the decision's `thread`). A lane is *below* the
frame; a place is *above* it. A part that moves inside its own code changes its own perspective only, and publishes
its landing — the master's frame is untouched.

### 13.7 `loop` is the daemon variant

With `loop` the thread is a persistent stage: its `result` is a stream (one per iteration), not a value to latch, and
the enclosing expression sees the thread handle rather than the computed result. That makes a `loop` decision a
*scheduling* placement, not a parallelism one: a daemon rule, not a fan-out, and it cannot be the last decision of a
`lower` whose value the chain continues with.

### 13.8 No new type

`thread::T`, `virtual::T` and `core::T` already exist. The cat graph cites the lane kind as a token (`virtual`,
`core`) and the realized lane as thread vids; the decision carries the kind, `path.where` the vids, and each thread's
`source` the master thread — a provenance chain from plan to lane. §1's fold table gains the row, and no cat type is
added.

## 14. Resources: capacity, demand, cost — what the machine can actually do

### 14.1 Two quantities, not one

- **capacity** is a hard bound: what a place can actually do (cores, memory, effective limits).
- **cost** is a price: what a plan is predicted to spend (RTT, bytes, serializations, spawns).
- **demand** is a third thing: what a plan *requires*, derived from the decision and the chunk, never authored.

The planner **filters by capacity and ranks by cost** — in that order. `place.cost` already exists and is a derived
price over the dials; it does not bound anything. Capacity is a separate field, and demand is computed.

### 14.2 Where each lives

| quantity                  | home                                                                                                    | why                                                                                                                                                                                            |
|---------------------------|---------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **capacity**              | `place::T.capacity => [cores, memory]`                                                                  | it is the *host's* number, not a frame's; a host runs many machine frames, and putting the host's cores on every one of them is wrong                                                          |
| **quota**                 | the frame's **processor** (`processor.resource => [lanes, cores]`), or the thread executor              | the machine type's own doc already promises that "a parent defines the scope of a child machine's access to resources"; the promise is a *quota*, and the processor is the axis that spends it |
| **availability / budget** | the `env` snapshot (`env.capacity => [cores=>{total,free}, memory=>{total,free}, lanes=>{leased,cap}]`) | it changes at runtime; a declared field would go stale                                                                                                                                         |

**Do not add cores/RAM to `machine::T`.** A machine is a *frame*; capacity is the *place*. What the frame needs is a
quota, and even that only for what the planner allocates: **lanes and core slots**. RAM cannot be quotad — there is
no per-frame memory accounting in the runtime — so memory stays an *observed feasibility input*, never a promise.

Capacity must be the **effective** number, not the nameplate: a container's cgroup limit,
`-XX:ActiveProcessorCount`, offline CPUs, `jdk.virtualThreadScheduler.parallelism`. Discovery
(`Runtime.availableProcessors()`, `OperatingSystemMXBean`) is a *fallback*; a boot-profile declaration overrides it,
and a boot check compares the two (declare 8, cgroup says 2 → warn and clamp).

### 14.3 The demand algebra

A decision contributes a demand **vector**:

| decision                           | demand                                |
|------------------------------------|---------------------------------------|
| `lanes`, `core`, `workers = k`     | k core slots                          |
| `lanes`, `virtual`, `workers = k`  | k carrier/memory terms, no core slots |
| a peer hop (`peer`/`peers`/`tree`) | RTTs + bytes + serializations         |
| `chain`, `local`                   | nothing beyond the caller's lane      |

Composition is the shape's business: **parallel placement adds** (the fan-out holds k lanes at once), **sequential
placement maxes** (a chain holds one at a time). So demand lives in a commutative monoid — pointwise `⊕` for
parallel, pointwise `⊔` for sequential — with a scalar bound `≤` against the budget. This is the **third projection**
of a plan, alongside execution and location: a *quantity*, where they are a law and a topology.

**Feasibility is a filter, not a failure.** `feasible(decision, env) ⟺ demand ≤ budget` pointwise, where the budget
is the env's availability minus resident daemons minus other plans' reservations. Over budget, the planner **clamps**:
reduce `workers` until it fits; if it still does not, **step down** the shape (`lanes` → `chain` → `local`). It never
turns an over-budget plan into a runtime crash.

### 14.4 Cost

Cost is a **valuation** of a feasible plan, in the same units as the demand plus measured prices:

- v0 is a static unit model: count the chunk's I/O morphisms from its abstraction, multiply by the number of parts
  and the lane term, and add a measured peer RTT when one is known. That is enough to *rank*, which is all the
  chooser needs.
- cost and demand are coupled but not interchangeable: a plan can be **cheap and infeasible** (8 lanes on a 2-core
  box) or **feasible and expensive** (a chain over 8 peers). The chooser is
  `argmin cost` over `{ p : laws(p) hold ∧ demand(p) ≤ budget }`.

### 14.5 Enforcement: two gates, because one cannot hold

1. **Static, in the rewrite.** The feasibility filter and the clamp use the `env` snapshot. A snapshot ages.
2. **Dynamic, in the executor.** At lane spawn (and at dial), an **admission check** against a live budget — a
   semaphore/lease in `ThreadExecutor`, which already owns the run/stop lists and is the gateway for every thread.
   Under contention: wait (backpressure) or degrade to the caller's lane. Release on latch completion and on
   stop/interrupt (`finally`).

A daemon (`loop`) is the exception: it **reserves permanently** at start and is **refused** if it cannot — a
persistent stage must be bounded — and releases on stop/close. Degrading a daemon silently would be the one wrong
answer.

### 14.6 What this means for the plan

- A plan is chosen by **laws (qualitative) × topology (spatial) × demand (quantitative)**: the first two say what is
  *sound*, the third says what *fits*. All three are projections of the same rule.
- The rewrite cannot *guarantee* no over-extension alone; the guarantee is the pair (static filter, runtime
  admission). The rewrite's job is to make the plan *fit when it can* and *degrade when it cannot*.
- Capacity is the place's, the quota is the frame's, availability is the environment's — three homes, because they
  change at three different rates.

### 14.7 The data coordinate — resources as addresses (the "third space")

The resource a chunk needs is a **space**, and a space is a resource of the machine's `memory` component — not a new
type. Two facts about a space answer the whole question:

- its **`pattern`** is the address it owns in the uri graph (`/usr/dr/#`) — the resource's *identity*;
- its **`host`** is the backend it reaches (`sqlite:/…/drstynx.sqlite`, `postgres://db.local:5432`) — the resource's
  *location*.

A resource has **two addresses, one path**: locally it is its `pattern` (`/usr/dr/…`, read by `*/usr/dr/…` from
memory); globally it is the owning machine's `authority` prefixed onto that same path
(`ws://booger.local:8555/usr/dr/…`,
read by `*<…>` from the network first). The `authority` *is* the machine's network projection of its memory. The
observable that tells the planner it chose the non-local form is the **remote vid** (`…@<6989dc99>`) the result comes
back tagged with — the serialization-idempotency cost (§7) of not co-locating.

**Owned vs reached** is read off `host`'s scheme: a local scheme (`sqlite:`, `file:`, `loop:`) means this machine
**owns** the resource (it lives on its disk and does not ship); a network scheme (`postgres:`, `ws:`, …) means it
**reaches** it through a client, and the owner is the place whose `authority` matches `host`. That yields two *opposite*
locality constraints:

1. **local resource** → the reading chunk **must stay at `origin`** — a *negative* constraint (don't ship away from the
   data).
2. **remote resource** → the reading chunk **should ship to the owner** — a *positive* constraint (ship toward the
   data).

Co-location is therefore an address-match, the same Galois-pair shape as §3's `obligations`/`admissible` but over
addresses:

> **admissible (place, chunk) ⟺ the chunk's read-set ⊆ place.mounts** — run where every `/usr/dr/…` is a local
> address, not an authority-prefixed one.

`place.mounts` is the data analog of `place.instset`: `instset` is the *instruction* vocabulary (which ops resolve
here), `mounts` the *data* vocabulary (which spaces resolve here). For `origin` the mounts are read straight off
`memory().spaces()`; for a **peer** they must be **advertised** (roster/discovery), because a peer can *read* your
`/usr/dr/…` through the wire but cannot *know* you own it unless you say so — the roster today advertises
`host => reach`,
and the resource coordinate needs `host => [reach, mounts]`.

This is the *qualitative* resource; §14's capacity/demand/cost is the *quantitative* one. They are orthogonal — "does
the data live here?" vs "can it run here?" — and a plan is feasible only when **both** hold.

## 15. Integration plan

The order is dependency-driven, and **every phase leaves the suite green**. No phase is a big-bang rename; the risky
one (the emitter) sits behind tests that already exist.

### 15.1 The guards

These must not move, and they are the acceptance test for every phase that touches the rewrite:

| test                                         | what it pins                                                                                                                               |
|----------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------|
| `DistributedRewriteTransportTest`            | `law_shard` fires for **real** over a forked peers: the sharded fold equals the expected value, and the rewrite genuinely emitted `select` |
| `TreeShardTransportTest`                     | the `tree`/`?hops` path                                                                                                                    |
| `catInstSetTest`, `m_math_cat_Test`          | the `path::T` / `abstraction::T` lifts and the `lower` chain                                                                               |
| `NetworkWsDialTest`                          | the dial/peer path a placement targets                                                                                                     |
| **new**: an emitted-`Code` snapshot per plan | a phase-1 guard: the rename must be **inert**, so the emitted code is compared before/after                                                |
| **new**: `PlacementEquivalenceTest`          | star / nest / local over one chain, equal modulo vids (phase 4)                                                                            |

### 15.2 Phases

**Status (2026-10-24): Phases 0–5 implemented and green.** Phase 3 landed shape signatures + `obligations`/`admissible`
(`Target`→`Shape`, `url`→`target` / `target`→`shape`, `validateDistributedObligations()` deleted). Phase 4 landed `Env`

+ the emitter reading `env` (not `Machine.current()`), `path::T` `origin`/`where`/`peer`, and `bind` (cut-less, identity
  until `move`). Phase 5 landed `abstraction::T.where` (the α place-class word, computed from the machine's declared
  roster machines ∪ dialed peers — not the origin) plus the `law_shard` liveness prune (no reach ⇒ the distribution rule
  is not a live morphism). The realized-`where` write is done (`path.where` = `{[span=>at] => [place…]}`, written by the
  `lower`/`bind` pass), and `reachable()` now reads the roster too (a discovered-but-undialed machine is a candidate).
  The shape vocabulary stayed the 7 pre-existing shapes (`chain`/`star`/`lanes` deferred to 6/7);
  obligations live on the `Shape` enum; the `/m/math/cat/signature` space recs and the scheme-signature ×
  `place.instset`
  prune (§4's declared-liveness) remain deferred to `bind`/`move`. See the cat-location Phase 3/4/5 notes in Hindsight.

**Phase 0 — record the decisions (no code).** Signatures are **recs**; the rule type keeps the name/vid `plan` with
`cut?`; the key is `where`; the edge is `peer`; resources are deferred. Write these into §10 and Hindsight, and name the
guards.

**Phase 1 — one rule: `plan.where`, cut optional (S; no behavior change).**
`Tokens.WHERE`; `Plan` gains `where` (a deprecated `routes()` returns the same list); rename the builder `Route` →
`Placement` with a deprecated alias for one cycle; `PlanBuilder.route(…)` → `where(…)`; `create()` accepts a cut-less
plan with exactly one decision (the distribution rule) and keeps `cut.laws ⊇ obligations` when a cut is present;
migrate `catRewrite`'s ~10 definitions. Tests: a new `PlanBuilderTest` (refused without laws, cut-less accepted,
`where` list preserved, `plan.test(plan::T)`) plus the emitted-code snapshot. **If the diff is not almost all rename,
stop and re-slice.**

**Phase 2 — `place::T`, the only new type (M; additive).**
`PLACE_TID`/`PLACE_TYPE` (`tid(category::T)`, §11.2); `LINKED_TO`/`LINKED_FROM` tokens; a `Places` helper building a
place from the roster + peer map with **derived auto insts** and the alias quotient; `as(…) → cat:place::T`.
Test: `PlaceTest` — roster host → authorities, a declared peer → `linked_to`, two loopback aliases → one place,
`known`/`up`. Nothing consumes places yet.

**Phase 3 — signatures + obligations/admissible (M).**
`SHAPE`/`DIALER`/`CAPABILITY` tokens; the shape and scheme recs at `/m/math/cat/signature/<token>` (data, no type);
`Placement.obligations()` reads the shape rec and `admissible(laws)` is the reverse traversal; `Plan.create()` uses
them; delete `validateDistributedObligations()`; rename the decision keys `target` → `shape`, `url` → `target` (compat
reads for one cycle); the interpreter sources `hops`/targets from the decision. Tests: `ObligationsTest` (the table,
both directions) plus the shard guards.

**Phase 4 — one emitter + `Env` (L; the risky one).**
`Env` (a small Java value first, a rec projection later): ISA union, roster, peers+states, origin place, fingerprint.
Factor `emit(shape, regions, targets, env)` out of `shard` (`tree`/`peers` → `select` + `merge`; `chain`/`peer` →
sequential; `local`/`none` → keep/unit); `shard` becomes `emit(tree)`. `apply(plan, env)` = `locate` (Thm 17,
unchanged) → `resolve` → `emit` → retype → re-lift, with **no `Machine.current()` inside**. Extend `path::T` with
`origin?`/`where?`/`peer?`, attach the origin on `as(cat:path::T)`, write the realized `where` after emission, and add
`bind?path<=path(plan)` (cut-less + origin). Guards: the two shard tests, the emitted-code snapshot, and the new
`PlacementEquivalenceTest`.

**Phase 5 — geometry (S; parallel with 6).**
`ABSTRACTION_TYPE` gains `where?` (the place-class word); the lift computes it from the located path; a placement guard
reads it. Test: the word is the pointwise class of the realized `where`.

**Phase 6 — `move(authority)` as the cut-less plan (M; the payoff).**
The rewriter cuts at an authority move and emits a cut-less `plan` with `chain`/`star` decisions; the shipped program
is `move(</path>).map(<seed>).<tail>` with the tail **quoted** (`Code.wrapStart`), and the receiver re-plans on
compile. Tests: a two-hop star/nest result equality, and the **quine regression** — the payload is a program, never a
callable type.

**Phase 7 — lanes (M; after 4).**
`THREAD`/`WORKERS` tokens, the `lanes` signature, the decision fields, the emitter lane arm with the **non-blocking
spawn** and an **awaiting latch**, and the lane kind derived from `pure` (§13). Tests: a one-machine 4-lane shard
equals the local fold; STOP/interrupt surfaces a fail.

**Phase 8 — resources (deferred).**
`place.capacity` + discovery/override, the `env` budget, the demand vector + feasibility/clamp, executor
admission/lease, daemon reservation, cost v0 + the constrained chooser (§14).

### 15.3 Out of scope, deliberately

- **PeerSpawn / `push(authority)`** — an availability/provider concern, orthogonal to the place algebra. Phase 6's cut
  works over already-declared peers; the spawn lands as its own track.
- **`signature::T`**, a resources field on `machine::T`, `place` as an `object::T`, a `link` as a `morphism::T` — the
  recorded refusals (§1).
- **Cost beyond v0** and any resource quota — §14 is a design, not a phase.

### 15.4 Working agreements

- No git operations from the agent; the owner commits.
- The authoritative state is `docs/skills/*` and the Java sources; this document is a note (§11's authority line).
- Every phase updates `docs/skills/mtron/references/cat-instset-mtron.md` for the type/field changes it lands, and the
  design doc's §11/§12 for any decision that moves.
- Tests: `@ParameterizedTest` + `@CsvSource` where a case is a row; focused classes in the build container, the full
  suite before merge.

---

## see also

* **authoritative** — [the category instruction set](../skills/mtron/references/cat-instset-mtron.md): the three
  lifts this document extends (`object::T`, `morphism::T`, `path::T` / `abstraction::T` / `plan::T`).
* **authoritative** — [mach instruction set](../skills/mtron/references/mach-instset-mtron.md): `machine::T`'s five
  components, the network's two laws (knowledge shared, reach not), and the `move`/`push` address dialect.
* context only — [distributed patterns — the menu](distributed-patterns-menu.md): the shard and tree patterns whose
  laws §3's table must reproduce, and entry B/C's denotation question. A design note, historical.
* context only — [distributed computing](distributed-computing.md): the machine nest as topology, the authority
  boundary, and Stage 5's provisioning-on-push. A design note, historical.
* source of the current coupling — `PlanBuilder.validateDistributedObligations()`; source of the current route
  vocabulary — `Route.Target`, `Span.Kind`; source of the current dial path — `Network.Helper.dial`.
