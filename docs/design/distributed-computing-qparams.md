# distributed computing — the qparam axes (`?place`, `?hop`, `?split`)

**Date:** 2026-10-28 **Context:** the menu of distributed patterns (`distributed-patterns-menu.md`) is the rows
A–T, but a reader wants **one expression** and a small amendment that changes *how it runs*. This is the
conclusion of that design pass: three qparams, each naming one axis of a distribution decision, all sharing a
single coefficient reading. **Keys and their value tokens are singular** (repo convention): `?place`, `?hop`,
`?split`; `local`, `peer`, `stream`, `block`. The parser facts here were verified against a running console; the
emitter sites are named so the change is a checklist. **Status:** proposal. Everything marked ✅ parses today;
everything else is specified but not yet read by anything.

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
lane_i = start?hop=n(part_i)   -- HOW FAR it recurses, and WHAT a share is
```

**From the decision rec.** `cat-location.md` §13.2 already names almost the same set — `span, shape, target,
thread, workers, loop, hops, law` — with two gaps: the **partition function** (how the data is cut) and an
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
- **Demand = the coefficient.** Read the designator's value with `stream()` and its demand with `c()`; both
  coefficient positions are legal (`{2}//abc:45`, `//abc:45{2}`), so the reader never cares which one was written.
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
| `?hop=4{2}` | 4 | 2 | 4 fan levels, 16 leaves, 31 roles |
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
| `?hop=4{2}` ✅ (text) | `4{2}` | the envelope's uri value is coefficient-enabled, so the text is kept whole — *but* see below |
| `?place=[local{0},peer]` ✗ | — | `{}` is not in the *bracket* charset (one lexer line, deferred) |
| `?split=[key(f)]` ✗ | — | parens are not in the envelope, and should not be |
| `?place=\\abc` ✗ | a `str` | `\` is only a q-charset character; it is not a uri, so it has no coefficient |

**The one loose thread:** a *trailing* coefficient on a number (`4{2}`) is kept as text by the envelope, but the
projection's object parser may read it as a **uri** (`4` with `c = 2` — the uri literal is what accepts a trailing
coefficient, since `m_int()` takes none). The reader should not care: the `uri → int` conversion is a declared,
sound row (`type/Uri.java:366`, `Integer.parseInt` of the uri's text), so a numeric value may arrive as an `int`
**or** as a numeric uri and both coerce to the same number. When *we* emit a counted form, prefer the leading
`{2}4` (a bulked value, unambiguous in every position); accept `4{2}` for symmetry.

The reader contract that follows, and it is the whole of it:

```java
final Obj v = qValue(tid, key);                             // the projected object (Uri.Helper.parseQ)
final Obj one = v.stream().findFirst().orElse(noobj());     // value via stream(), never shape inspection
final cInt demand = v.c();                                  // {0} | {w} | {lo,hi} | {*}
if (v.isStr()) throw MTronException.of(...);                // an unparsed value is a malformed spec — refuse
final int count = (one.isUri() ? one.asInt() : one).intValue().intValue();   // 4{2} may arrive as a numeric uri
```

`str` ⇒ **refuse loudly**. The bug this replaces is real: `hops()` does `Integer.parseInt(qMap().get(HOPS))`,
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
start([ start?hop=0({1,4,5}).plus(10).sum(),          -- local lane: a place, dividing no further
        start?hop=0({2,3}).plus(10).sum(),           -- shipped: a leaf, and NOT a place
        start?hop=0({66}).plus(10).sum() ])
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
(`?place=[local{4}]` — a peerless machine shards), and S (`?place=[local,peer]` — a place at distance zero).

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

## 10. What exists today, and what the change touches

**Today:** `?hops` — an int, depth only; the rename to `?hop` is the first chunk, with the old key kept as an alias
for one cycle — read nearest-first off any instruction (`PlanInterpreter.java:301`), with the plan's `tree(1)` as the
default (`catRewrite.java:73-78`). `?place` and `?split` are read by nothing; their
values already parse and project (§6) and are simply inert.

| site | change |
|---|---|
| `mParser.java:1207` | *(deferred)* balanced `{}` inside the bracketed value |
| a `qValue(key)` reader | project the q text, `str` ⇒ refuse, demand from `c()` |
| `PlanInterpreter.hop` (`:301`) | read `(n, w)`; `{*}` = unbounded; **restate the form, not the number**, on the slave |
| `PlanInterpreter.shard` (`:199`) | `resolvePlaces` replaces the hard-coded peer scan (`shardTargets`, `:352`); `k = Σ demands`; lane 0 = `eval()`; depth on the fold of the slave, the place claim on the seed |
| `Placement` | `target` + most of `Shape` collapse into the place set + `hop` + `split` |
| `catInstSet.realizedWhere` (`:226`) | skip the addressless route, and record `local` as a realized place |
| `catTypes.PLAN_TYPE.where` (`:292`) | add `place` and `split`, and make the counted form legible (note: `hop` is missing from that predicate today, and a plan carrying it still passes, so extra keys are tolerated) |
| `Tokens` | add `PLACE`; reuse `SPLIT` for the q key (it is already the profiling rec's key, and one word is better than two); rename `HOPS` → `HOP` |
| tests | `Reduction.routes`/`slaves` need the lane notion (`null` designator = `local`); `DistributedComputation.of` evaluates the local lane as its own run |

**Test rows** (`@CsvSource`, one line each): `0`, `1{*}`, `n{*}`, `n{1}`, `n{2}`, `4{2}`, `n{0}`, `0{0}`;
`peer`, `[local,peer]`, `[local,//abc:45,//df:3]`, `[local=>4,//abc:45=>2]`, `#`,
`//ginger:8555{2,5}`; the `str` refusals; and for each, the emitted op chain plus the value equal to the
undistributed fold. The chain row (`n{1}`) is the one to prove first — it is the shape that does not exist
today, and if it holds, `{w}` is real.

---

## 11. Open decisions

1. **Floor policy** — step down (recommended) or hard fail, when a band's floor cannot be met.
2. **`{lo,hi}` resolution source** — v1 takes `hi` when the place is live and feedable; the honest source is
   `place.capacity` (`cat-location.md` §14).
3. **Split propagation** — does `?split` ride the emitted slave with `n-1{w}`? (Recommended: yes for
   `stream`/`block`/`key`/`owner`, since the partition's premise does not change per level.)
4. **Bands in lists** — the one lexer line, and whether it is worth it before a pattern needs it.
5. **The counted form's projection** — `4{2}` may project as a numeric uri. The reader tolerates both (see §6);
   the open question is only which form we *emit* when restating `n-1{w}` on a slave: the leading `{2}4` is
   unambiguous, the trailing `4{2}` matches how it was authored.
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
