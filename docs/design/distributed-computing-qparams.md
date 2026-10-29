# distributed computing — the qparam axes (`?place`, `?hop`, `?split`)

**Date:** 2026-10-28 **Context:** the menu of distributed patterns (`distributed-patterns-menu.md`) is the rows
A–T, but a reader wants **one expression** and a small amendment that changes *how it runs*. This is the
conclusion of that design pass: three qparams, each naming one axis of a distribution decision, all sharing a
single coefficient reading. **Keys and their value tokens are singular** (repo convention): `?place`, `?hop`,
`?split`; `local`, `peer`, `stream`, `block`. The parser facts here were verified against a running console; the
emitter sites are named so the change is a checklist. **Status: implemented (2026-10-28)** — all three keys are read
by the emitter, the plan is stated in them, and the law is the gate. **Not yet:** `Shape`'s deletion (the enum survives
as the read-compat projection, and the plans no longer write it), the `key`/`owner`/`index` splits (they decline to the
identity until their premises land), bands inside a bracketed place list (`mParser`'s balanced `{}`), and the `m_uri`
suffix refusal. Each ✅ below is a parse fact verified against a running console.

The premise is unchanged: **distribution is a theorem, not a feature.** A qparam may only *place* work — it can
never overrule a law. `mean` does not shard, and no `?place` will make it; `str` `sum` is not commutative, and no
`?hop` fixes that. The law stays the gate; the qparam is the routing.

---

## 1. The axes, derived three ways (not invented)

**From the emitted layout.** Every shard is one shape, and each of its slots is a degree of freedom
(`algebra/rewrite/PlanInterpreter.java:265-292`):

```
start([ lane_1 … lane_k ])      -- WHICH places, HOW MANY shares each
  ==[ route_i ]                 -- how each lane travels (eval() / eval(<peer>) / virtual)
  >- combiner                   -- HOW the shares reunite  (computed, never named)
  .tail                         -- where the answer lands
lane_i = start(part_i)·…·fold?hop=n  -- HOW FAR it recurses (the budget rides the fold), and WHAT a share is
```

**From the decision rec.** `cat-location.md` §13.2 already names almost the same set — `span, shape, target,
thread, workers, loop, hop, law` — with two gaps: the **partition function** (how the data is cut) and an
**arrival policy**. And one entry that should go: `shape`, because a shape is a *consequence* of the other axes
(`tree` = peers + depth > 1, `shuffle` = a keyed cut).

**From the menu itself.** Read A–T against those slots and the rows A–T collapse onto four axes:

| axis | key | the question it answers | the places it shows up |
|---|---|---|---|
| **where** | `?place` | which places work, and how many shares each takes | A B C D E H P Q S |
| **how** | `?hop` | how far the code re-plans itself, and how many children per node | E, and the shape of every distributed pattern |
| **what** | `?split` | what counts as *one place's share* | B C F G M O P Q |
| **when** | — *(mtron `repeat()`)* | how many rounds the layout repeats | N R. **No qparam**: repetition is `repeat()`/`loop()` around the layout, so the rounds axis is the language's, not a key's |

### the menu as axis-tuples

| row | amendment | axes |
|---|---|---|
| **A** homomorphism shard | none — the default | where |
| **B** data-local shard | `?split=owner` (the places follow from the owners) | where + what |
| **C** authority push-down | `?place=//host:8555` | where |
| **D** parallel fan-out | `?place=[local{4},…]` (lanes at one place) | where × coefficient |
| **E** tree reduction | `?hop=2` | how |
| **F** ordered fold | `?split=block` | what |
| **G** keyed reduce | `?split=key` (else: regroup at home, today) | what |
| **H** redundant / hedged | `?place=[//p1{2},//p2{2}]` + an idempotent fold | where × coefficient |
| **I** span/route interpreter | — infrastructure; it *reads* the axes | — |
| **J** algebraic aggregates | — derived from `mean`'s derivation | — |
| **K** limit / top-k | — the chain already says `take(k)`; the engine pushes it | — |
| **L** mergeable sketches | — the type declares its monoid | — |
| **M** matrix multiplication | `?split=[row,col]` | what (a product) |
| **N** iterative / BSP | — `repeat()` around the layout (mtron, no qparam) | when |
| **O** broadcast constants | — free: constants ride with the code | — |
| **P** computed seeds | `?place=peer ?split=index` | where + what |
| **Q** monad migration | `?split=owner` per step, engine | where + what |
| **R** durable results | — the tail's `to(<space>)` (and `repeat()` for rounds) | — |
| **S** master-participates | `?place=[local,peer]` | where |
| **T** frame migration | — `move()` is an instruction, not a plan | — |

Eleven rows are a one-line amendment; nine need no key at all, because the chain or the type already says it.
**That is the whole point: the expression already carries the pattern; the qparam only overrides a placement
decision.**

---

## 2. `?place` — where

The place set, as a value. Two accepted shapes, and the type of the value decides which (a lst is a **set**, a rec
is **weights**):

```mtron_pre
?place=peer                                    -- default: the dialed set, uniform share
?place=[local,peer]                            -- S: the master is a place too
?place=[local,//abc:45,//df:3]                 -- a named set, uniform shares      (lst)
?place=[local=>4,//abc:45=>2,//df:3]           -- weighted; an absent weight is 1  (rec)
?place=#                                       -- every remaining reachable place
?place=//ginger:8555{2,5}                      -- a band on one designator (scalar form)
?place={4}local                                  -- a demand is LEADING, and scalar-only on the q surface
?place=[local=>4,peer=>1]                      -- the weighted LIST is the rec form: place => its share
```

| designator | resolves through |
|---|---|
| `local` | `Places.self()` — this frame's own place, its listeners |
| `peer` | `Env.peers()` — the **dialed** set, host-bearing only (singular, like the network's own `peer` field) |
| `#` | the wildcard segment: every reachable place the other entries did not name |
| `//host[:port]` | `Places.place` — the addressing redirect (roster ∪ dials, deduped by host) |
| `scheme://host:port` | one authority, as written (this is what `Signatures.resolves` reads) |

Rules:

- **A place is a host + authority, not a frame path.** `//host/a/b` has no boundary meaning — *"a wire address
  names a PROCESS boundary, not a place in this tree"* (`machInstSet.java:711`) — so a path is normalized into the
  shipped code's `origin`, never into a target.
- **Reach, never knowledge.** The wildcard's universe is the dialed set ∪ `local`; the roster is *known*, not
  reachable, and shipping there is the accepted-twin failure already documented at `PlanInterpreter.java:339-351`.
  `Places.serves` (online ∧ declares `eval`) stays the final gate.
- **Specificity, not summation.** An explicit address beats an enum beats a pattern: `[//ginger{2},#]` gives
  ginger exactly 2 and the pattern takes the rest.
- **Exclusion is omission.** Leaving `local` out is how you say "I compute nothing"; `local{0}` is the explicit
  spelling of the same thing (and a real guard once `#` is in the set).
- **Demand = shares (parts), and a share is a lane.** `{2}` on a place asks for two of the parts: with
  `[{2}ginger,{1}booger]` ginger takes 2 of the 3 parts — two thirds of the bulk **and** two of the three lanes
  (data weight and concurrency are the same number, because a share is a lane). The counts are **absolute**, so
  `{4}:{2}` is the same 2 : 1 bias over six lanes: write the ratio in lowest terms unless the extra lanes are
  wanted.
- **On the q surface a demand is LEADING, scalar, or a rec** — never a coefficient inside a list. The q-value
  envelope admits `{}` only as the *whole* value's coefficient (`?place={4}local`), not inside `[...]`
  (`?place=[{4}local,peer]` does not parse, and `?place=[…]{4}` binds the `{4}` to the queried uri). So the
  weighted list is the **rec**: `[local=>4,peer=>1]`. It also spells a **zero** demand (`local=>0`), which the
  coefficient cannot: `{0}local` is `noobj` (*measured*, C3). A trailing `{n}` (`//ginger{2}`, `local{4}`) parses
  and reads as **1** — never write it. A *placement rec* in mtron is not under the envelope, so a plan may still
  state `[{2}//host]` there.
- **`?split` may *produce* the place set.** `stream`/`block`/`key`/`copy` consume a place set (k shares over k
  places); `owner` and `index` produce it — the places *are* the owners, or the range holders. So B needs only
  `?split=owner`, and naming places for it would be redundant.

---

## 3. `?hop` — how

`?hop` is **the shape of the application tree: its height `n` and its arity `w`.** It is not a topology
selector. Three equivalent statements of the same number, all true of the current emitter:

1. **rewrite self-application depth** — how many times the distribution rule may re-apply to the code it produced;
   each re-application happens *where the code lands*, so one re-application is one hop;
2. **the radius** of the ball of places the code may occupy in the dial graph;
3. **the number of Thm-19 applications** along a path: each level is one `(a+b)c = (ac+bc)c`.

The arity is the receiver's local out-degree unless the author caps it, which is why `point` / `star` / `tree` are
not three topologies but the same recursive expansion truncated at radii 0, 1, n.

| written | height | arity | realized shape |
|---|---|---|---|
| `?hop=0`, `?hop=n{0}` | 0 / n | 0 | **point** — `D(c) = c`, the identity (see §9) |
| `?hop=1{*}` | 1 | all serving peers | star (today's depth-1 default) |
| `?hop=n{*}` | n | local out-degree | free / irregular tree — **today's behavior** |
| `?hop=n{1}` | n | 1 | **chain / pipeline** (F's shape; T's snake as a distribution) |
| `?hop=n{2}` | n | 2 | binary tree, up to 2^n leaves |
| `?hop={2}4` | 4 | 2 | 4 fan levels, 16 leaves, 31 roles |
| `?hop=n{lo,hi}` | n | elastic | fill what is live up to `hi`, at least `lo` |

Arithmetic worth pinning in a test: with `n` fan levels and arity `w`, roles = Σ w^i = (w^{n+1}−1)/(w−1) and
leaves = w^n — **so `n{w}` is the first sizing knob the pattern has ever had** ("this will use at most 31
machines"). Two caveats: the **data clamps the shape** (a 6-element seed cannot fill 16 leaves, so the realized
tree is ragged), and `n{w}` bounds *roles*, not *machines* — a place can be an interior node twice for two
different parts, until the visited-set feature lands.

---

## 4. `?split` — what

**The partition function: what counts as one place's share.** The third noun the decision rec has never had — the
plan has a cut (how the partials recombine) and a placement (where), but no partition. The algebra already owns
the word: every distributive fold factors as `h = map(g) · split`, and `order(B) ≡ split([_]) · kmerge(B)` names
the same partition.

| `?split` | the partition | legal when | unlocks |
|---|---|---|---|
| `stream` (default) | stride — any exact division | always (a stream has no order) | A E G J K L S |
| `block` / `block{4}` | contiguous runs, recombined in order (4 blocks) | the seed carries **order** (a `lst`) | F |
| `key` | bucket by key; equal keys co-locate | elements carry a key (the chain's `group` spec names it) | G's true shuffle, locality |
| `owner` | one part per address owner | the seed is addressed (`from`, `*<uri>`) | B C Q |
| `index` / `index{4}` | 4 ranges of an index space; **nothing ships** | the body generates the space | P |
| `copy` (= `stream{1}`) | one part = the whole seed, every lane | the fold is **idempotent** | O H |
| `[block,key]` | the **product** of its factors | each factor legal | M (and any staged cut) |

**The coefficient is the part count.** The lanes belong to the place set (§2); `{*}` — the default — means *as
many parts as lanes*, which is what happens today. When a split asks for **fewer** parts than there are lanes, the
surplus lanes receive the same part, and that is exactly hedging:
`?place=[//p1{2},//p2{2}] ?split=stream{2}` is two parts, each present on two places, and `stream{1}` is broadcast
(`copy`). So H and O need no number the other axes do not already have.

**It is the only one of the three keys that can change the answer**, because it is a *denotation*, not a
placement. `?place` and `?hop` are result-preserving (Thm 19) for any fold that shards at all; a split carries a
premise, and the premise must be checked or the shard is quietly wrong:

- `copy` needs `idempotent` — replicated data double-counts a non-idempotent fold;
- `block` needs order — "contiguous" is meaningless on a stream (exactly why F is struck for streams);
- `owner` needs a partitioned store — B's open decision: one owner per address, or the union double-counts;
- `index` needs a generator — the body must produce the space's members purely.

So the split **declares its requirement** and the gate reads like the existing one
(`obligations(shape) ⊆ cut.laws`): `Requires.idempotent`, `.order`, `.addressed`, `.generator` — and it **fails
closed** (declines to the identity) exactly as an undeclared op matches no slot today.

---

## 5. One coefficient reading, three keys

The coefficient is **a demand** — a multiplicity read by a consumer — while a *type's* coefficient is the band a
value must fall within (`Processor.java:120`, `lhs.c().within(type.c())`). Realization is `min(demand,
available)`: **a cap.**

| form | cInt | on `?place` | on `?hop` | on `?split` |
|---|---|---|---|---|
| `{0}` | `0..0` | contributes nothing (exclusion) | no children — a leaf/pin | no part — decline |
| `{0,w}` | `0..w` | up to w shares, by capacity | up to w children | up to w parts |
| `{w}` | `w..w` | w shares; capacity clamps silently | w children | w parts |
| `{lo,hi}` | `lo..hi` | a band: floor lo, cap hi | a band of children | a band of parts |
| `{+}` | `1..∞` | at least one | must fan out | at least one part |
| `{*}` | `0..∞` | every place | the local out-degree — **today** | the lane count — **today** |

Two clamps, only one governed by the band:

- **data clamp** — `min(w, |part|)`; silent always, because the data ran out, not the cluster. A `{2,4}` at the
  last level of a small seed must not "fail its floor".
- **capacity clamp** — fewer serving places than the demand; this is what `{0,w}` and `{lo,hi}` speak to.

**Floor policy (open):** I recommend **step down**, not fail — `{lo,hi}` with too few places degrades (fewer
children, then a leaf, then the local fold) and *records* the shortfall, which is the `cat-location.md` §12.5
`admissible` rule already. A silent clamp would break the declared floor; a hard fail would need a `catch` in
every flaky cluster.

---

## 6. The grammar, as the parser actually has it

A q value is **mtron source**, and three layers decide what a key receives:

| layer | site | what it decides |
|---|---|---|
| lexical envelope | `mParser.java:1200-1215` | *how much text* a value may contain |
| pair grouping (final) | `fURI.java:1584` `Singleton.parseQuery` | `&` splits pairs, the **first** `=` splits key/value, the value is the verbatim remainder, a repeated key merges as `v1;v2` |
| value grammar | the reader | everything else — and it is the *object* grammar, not a bespoke one |

The envelope is a character set, not an object grammar: `REDUCED_FURI_CHARS = ~/%$!#_-@+:*`, `FULL = +". "`, and a
bracketed value may also hold `>,=`. **No parens anywhere** (a uri followed by `(` is an application, and that
ambiguity is exactly what the envelope prevents), and `{}` only in the *scalar* position, where the uri literal
itself carries the coefficient.

The **projection** then parses the text into an object, and the mechanism is one function: `Uri.Helper.parseQ`
(`type/Uri.java:439`) is `ObjmtronSerializer.parse(value)`, falling back to `str(value)` when the parse answers a
`fail`. That is what makes the reader trivial — `>>q/place` is a `lst`, `>>q/split` is a `lst` with
`.type()` = `lst::T`, and a value that does not parse comes back a **`str`**:

| written | result | why |
|---|---|---|
| `?split=[a,b,c]` ✅ | a `lst` of uris | mtron source, and the envelope allows it |
| `?place=[//abc:45,//df:3]` ✅ | a `lst` of place uris | `/` and `:` are uri characters |
| `?place=[local=>4,//abc:45=>2]` ✅ | a `rec` (place → weight) | `=>` makes it a rec literal, not a lst; the envelope allows `>,=` |
| `?split={2}//xxz/dag/3` ✅ | a uri, `c = 2` | a **leading** coefficient on a uri is the uri's coefficient, not a stream |
| `?hop=4` ✅ | an `int` | a plain run |
| `?hop={2}4` ✅ | an `int` 4 with `c = 2` | the leading coefficient is a *value* coefficient |
| `?hop=4{2}` ✗ | a `fail::T` | a number takes no trailing coefficient — refused, never read as 0 |
| `?place=[local{0},peer]` ✗ | — | `{}` is not in the *bracket* charset (one lexer line, deferred) |
| `?split=[key(f)]` ✗ | — | parens are not in the envelope, and should not be |
| `?place=\\abc` ✗ | a `str` | `\` is only a q-charset character; it is not a uri, so it has no coefficient |

**Settled by measurement (it was a guess here):** a number takes **no trailing coefficient** — `4{2}` is a parse
error — so the counted form is the **leading** one, and `{2}4` parses to an `int` 4 with `c = 2` (a uri carries
either position; `{2}//abc:45` and `//abc:45{2}` both work). Consequences: `?hop=4{2}` is **refused** (loudly, with
the key and the text), `?hop={2}4` reads 4 with the arity coefficient, and when we *emit* a counted form we emit
the leading spelling. A counted **uri** (`{2}//abc:45`) is the `?place` shape and is read by the place reader, not
this one — the two readings stay in their own keys.

The reader contract that follows, and it is the whole of it:

```java
final Obj v = tid.qValue(key, Lst.class);                   // fURI.qValue: the parse, not a second parser
final Obj one = v.stream().findFirst().orElse(noobj());     // value via stream(), never shape inspection
final cInt demand = v.c();                                  // {0} | {w} | {lo,hi} | {*}
if (v.isFail() || one.isFail() || one.isNothing())
    throw MTronException.of(...);                           // an unreadable spec is refused, never read as 0
```

`str` ⇒ **refuse loudly**. The bug this replaces is real: `hop()` does `Integer.parseInt(qMap().get(HOP))`,
so `?hop=4{2}` throws, is caught, and answers `0` — **silently no distribution**. A malformed spec must never
degrade into the point topology.

**Deferred, and only if wanted:** balanced `{}` inside `[…]`, so a *list* may carry weighted designators
(`[//ginger:8555{2,5},//abc:45]`). It costs nothing to wait: a band is a property of one designator, and the
scalar and rec forms cover the weighted cases today.

---

## 7. Resolution — seven steps

1. **Exclusions first.** A `{0}` demand removes that place from the candidate set *before* expansion, so a pattern
   or enum can never resurrect it; a place simply left out of an explicit set is never a candidate.
2. **Expand.** `local` → `Places.self()`; `peer` → the dialed set; `//host[:port]` → `Places.place`; `#` → every
   remaining reachable place; `scheme://…` → itself. The coefficient applies **per resolved place**, so
   `peer{2}` ≡ each peer twice.
3. **Specificity, not summation.** explicit address > enum > pattern.
4. **Reach, never knowledge.** the wildcard universe is the dialed set ∪ `local`; then `Places.serves` filters.
5. **No frame paths.** a place's path is normalized into the shipped code's `origin`.
6. **Clamp.** drop zero demands; `k = min(Σ demands, seed.size())`; if `k < |places|`, the lowest-demand tail
   drops (deterministic, so the layout can be pinned in a test); if nothing would ship (`k − keep < 1`),
   **normalize to the decline** and keep the original code.
7. **Emit.** one `virtual::[code=>eval()]` route per share at `local`, one `virtual::[code=>eval(<authority>)]`
   per share elsewhere (lanes at one authority are repeated routes — the fan-out already submits them all), the
   seed strided over the shares (when parts < lanes the stride wraps, so a part's replicas land on *different*
   places), and the **realized** decision written onto `path.where`
   (`[//booger=>2,//ginger=>3]`) so a run is auditable against the declared band.

---

## 8. The layouts

```mtron_pre
start([ start({1,4,5}).plus(10).sum?hop=0(),          -- local lane: a place, dividing no further
        start({2,3}).plus(10).sum?hop=0(),            -- shipped: a leaf, and NOT a place
        start({66}).plus(10).sum?hop=0() ])           -- the budget rides the FOLD, not the seed
  ==[ virtual::[code=>eval()], virtual::[code=>eval(<ws://p1>)], virtual::[code=>eval(<ws://p2>)] ]
  ==[ >>rhs, >>rhs, >>rhs ] >- sum
```

Three properties make this the same mechanism as the existing shard, not a second one:

- **`eval()` is the addressless route.** `/m/inst/eval` already has a local body
  (`Obj.java:1799`, `lhs.apply(noobj())`); the peer form is an *override* installed on the connection
  (`WebSocketRec.java:102`). Home's lane is the same instruction with the address elided.
- **A lane is a non-blocking spawn, and `>>rhs` is the latch.** `select` applies the route *value* to the slave
  (`Poly.java:221`); a thread value applied is `applyAsync(...); return this` (`AbstractThread.java:110`), and
  the await is the `rhs` auto (`AbstractThread.java:142`) read by the right-shift. This is why
  `ParallelFanOutTest` can measure a sub-millisecond dispatch gap between two 500 ms shards. The
  `cat-location.md` §13.5 "lane gap" applies to the *inst* spelling (`lhs.virtual([...])`), not to the emitted
  path — **the lane fan-out needs an arity, not new machinery.**
- **The depth is a leaf at `local`.** A node is an interior node once; re-planning its own share over the same
  peers would only re-ship the same parts. So `?hop=0` on the local lane, always.

So the topologies are one family: point (`?hop=0`), star (`1{*}`), tree (`n{*}`, `n{w}`), chain (`n{1}`), lanes
(`?place=[{4}local]` — a peerless machine shards), and S (`?place=[local,peer]` — a place at distance zero).

---

## 9. The identity case

`?hop=0` means **no more hopping: you are the end of the line. Do the task you were given — and only you — and
return the result.** Formally `D(c) = c`: the distribution rule is the identity at that point, the base case of
the expansion.

| written | outcome |
|---|---|
| `?hop=0`, `?hop=0{0}`, `?hop=n{0}`, `?hop=0{*}` | all four are the **identity**: the original expression, byte for byte |

**Distribution needs a level and a branch; either zero gives the point.** And "do nothing" is a *different*
decision in a *different* vocabulary — `Placement.none()` deletes a span (the unit law's transform). A budget on
fan-out has no authority to delete work; the worst it can do is decline.

---

## 10. What exists today, and what is still deferred

**Today (after C9):** the plan is stated in the axes — `law_shard` is `.cut(...)` plus
`.place(PlaceSet.local()).hop(0, cInt.ANY()).split(Partition.stream())` over the origin's tail and
`.place(PlaceSet.peer()).hop(1, cInt.ANY()).split(Partition.stream())` over the fan-out — and the assembled rec carries
no derived `shape`/`target` (both are read off the axes; the legacy encoding is still *read* for one cycle, and the
`plan::T` predicate admits a rec that states the axes alone).

- **`?place`** is resolved into lanes (`PlaceResolver`): the designators of §2, one lane per share, the home place's
  first, `local` routed by the addressless `eval()`; the emitter's lane count is `k = min(Σ demands, seed)`, then the
  hop's arity caps it.
- **`?hop`** is the tree shape `height{arity}` (§3): the axis is read in chain order (the first statement wins, `?hop`
  preferred over the deprecated `?hops` at the same position, both stripped from every copied instruction), the arity
  caps the fan-out by taking the first `w` lanes of the emitter's own order, and the emitted slave restates the form one
  height down **on its fold** (the reducer) while its seed carries only the part. Height 0 and arity 0 are the identity.
- **`?split`** (§4) lowers `stream` (today's stride) and `copy` (the whole seed on every lane, legal only when the cut's
  combiner declares `idempotent`); `block`, `key`, `owner` and `index` **decline** to the unchanged chain — their
  premises (order, a key, an addressed seed, a generator) are not held yet — and an unreadable value is refused loudly.
  A stated part count (`{2}stream`) is not read yet: a share is already a lane, and a second part count is a second
  knob.
- **The law stays the gate.** `PlanBuilder.create()` derives `obligations(place, hop, split) ⊆ cut.laws`; the emitter
  declines the identity when the chain does not match the plan's one-cut shape; a split that cannot check its premise
  fails closed.

| site | state |
|---|---|
| `mParser.java:1207` | *(deferred)* balanced `{}` inside the bracketed value — a weighted designator list has no spelling yet |
| `PlanInterpreter.hopAxis` / `.split` / `.place` | done: one strict q projection (`strictQValue`), a `str`/`fail`/empty value refused naming the key and the text |
| `PlanInterpreter.shard` | done: lanes from `PlaceResolver`, `k = Σ demands` clamped by the seed then the arity, lane 0 = `eval()`, the budget on the slave's fold |
| `Placement` | done: the axes are the authority, `target`/`shape` derive from them; the recorded enum is read-compat only (C11 deletes it) |
| `catInstSet.realizedWhere` | done (C8): each realized place carries its realized lane count (`[//ginger=>2,//booger=>1]`, the weighted `?place` spelling), and the addressless route is the `local` designator — clamped first, so the count is what actually ran |
| `catTypes.PLAN_TYPE.where` | done: `place`/`hop`/`split` are admitted with their coefficients, and `shape` is optional |
| `Tokens` | done: `PLACE` added, `SPLIT` reused for the q key, `HOPS` → `HOP` with `?hops` as a read-only alias |
| tests | done: `PlaceResolverTest`, `PlaceLaneTest`, `SplitLegalityTest`, `TreeShardTransportTest`, `PlanBuilderTest`/`DecisionModelTest`/`ObligationsTest` pins |

**Deferred (C11, in the order of the plan doc):** `Shape`'s deletion and the `TARGET` compat removal; the `key`,
`owner` and `index` splits (and a stated part count); bands inside a bracketed list; the `m_uri` suffix refusal;
the visited set; the local lane's `timeout(inst)` deadline.

---

## 11. Open decisions

1. **Floor policy** — step down (recommended) or hard fail, when a band's floor cannot be met.
2. **`{lo,hi}` resolution source** — v1 takes `hi` when the place is live and feedable; the honest source is
   `place.capacity` (`cat-location.md` §14).
3. **Split propagation** — does `?split` ride the emitted slave with `n-1{w}`? (Recommended: yes for
   `stream`/`block`/`key`/`owner`, since the partition's premise does not change per level.)
4. **Bands in lists** — the one lexer line, and whether it is worth it before a pattern needs it.
5. ~~The counted form's projection~~ — **settled**: a number takes only a **leading** coefficient (`{2}4`);
   a trailing one (`4{2}`) is a parse error and is refused. We emit the leading form.
6. **The realized deadline** — the local lane now folds inside the `>>rhs` gather's `FutureObj` deadline
   (5 s), where today the master's combiner runs outside any deadline. The standing answer is the menu's
   `timeout(inst)`, not a longer constant.

---

## see also

* `distributed-patterns-menu.md` — the A–T menu these axes compress, with each row's law and motifs.
* `../skills/mtron/references/distributed-computing-mtron.md` — the theorem bindings and the implemented
  layouts, in the reference's own voice.
* `cat-location.md` — the place/lane/resource coordinate the decision rec comes from (§12.5 emitter, §13 lanes,
  §14 capacity).
* `cat-rewriter-distribution.md` — the rewrite that produces all of this.
