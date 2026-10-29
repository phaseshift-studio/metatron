# Distributed Patterns — the Menu

**Date:** 2026-10-21 **Context:** The working list of distributed-computing patterns for metatron. Each entry states the
algebraic law it
rests on, the code a slave and the master run, the plumbing it still needs, and its status. The premise is
`distributed-patterns-through-algebra.md` ("distribution is a theorem, not a feature"); the first realization is
`law_shard` (`cat-rewriter-distribution.md`); the machine/network substrate and its gaps are
`distributed-computing.md`. This doc is the menu: what to build next, and why each item is sound.

---

## 0. Ground truth

**The mechanism.** Distribution is code shipped over the wire — `ws`, `http`, `mcp` — with results coming back as
streamed values or `!*` pointers. A plan (`plan::T`: `cut`, `route`, `span`) is lowered by `PlanInterpreter` to a
layout the processor runs:

```
start([slave_1, …, slave_k]).select([to(p_1), …, to(p_k)]).merge().<combiner>.<tail>
```

`select` pairs slave *i* with route *i*. `to(peer)` — bound at plan time to the target client's own `to` — ships the slave
unquoted as `start(slave).to(<>)`; the peer evaluates the frame on receipt and replies. `merge()`
streams the replies and the combiner recombines them. `clstrSpace` is dead code and `RemoteSpace` is going away;
neither is part of this path. `RemoteMachine` and `RemoteProcessor` (a frame that stood in a peer and shipped every
code application made in it) were relics and are removed (2026-10-21): the `law_shard` layout is the distribution
architecture, and every entry here lowers to it. `PeerSpawn` (forking a peer JVM) stays, waiting for its instruction.

**Facts every entry has to respect.**

| fact                                                                                                                                                                                                                                                                                                                                                                                                       | consequence                                                                                                                                                                                                                                            |
|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| a stream is a **bag**, bulked by coefficient: `{1,2,1}` is `{2}1, 2`                                                                                                                                                                                                                                                                                                                                       | a fold over a stream must be commutative to exist at all; order lives in `lst`, so ship a `lst` when order matters                                                                                                                                     |
| a `lst` is **slot-oriented, never a bag**: a coefficient on a `lst` goes into its slots (`{2}[1,2]` is `[{2}1,{2}2]`); only the expression bags a `lst`'s slots (`[1,2,3]>-`); inside a rec merge a `lst` is an **atom** — colliding `lst`s make a bag of `lst`s, each whole (`[a=>[1,2,3]]+[a=>[2,3]]` is `[a=>{[1,2,3],[2,3]}]`), and concatenating them is a select's `+` (`[a=>[1,2,3]]==[a=>+[2,3]]`) | the rec merge is commutative, so rec `sum` is a defined fold over a bag; `lst` partials (G's `group([k⇒order(s)])`) reach their combiner as a bag of runs                                                                                              |
| a fold reads a coefficient through its monoid: `c·x = x ⊕ … ⊕ x`                                                                                                                                                                                                                                                                                                                                           | `merge()` bulks equal partials (`{2}6`), so a fold that misreads `c` makes the sharded answer differ from the local one                                                                                                                                |
| the fan-out is **parallel** — one virtual thread per route, awaited at the gather                                                                                                                                                                                                                                                                                                                            | `select([virtual::[code=>eval(p)]])` submits every round trip at once and `select([>>rhs])` awaits them: latency is max RTT, not Σ (see D)                                                                                                            |
| targets are **dialed** peers only                                                                                                                                                                                                                                                                                                                                                                          | accepted twins (keyed by the dialer's ephemeral port, no `host`) never receive shards                                                                                                                                                                  |
| `Route.peer`, `Route.space`, `Route.tree` have no lowering                                                                                                                                                                                                                                                                                                                                                 | each entry that needs one adds its emitter; a generic interpreter is entry I                                                                                                                                                                           |
| the a-priori pruner fails open on bulk seeds                                                                                                                                                                                                                                                                                                                                                               | `path::T` lists every rule for `start({…})…` chains (fixed: a bulk seed's carrier is its member type); compilation is unaffected                                                                                                                       |
| a deref in a shipped slave's ARGUMENT reads nothing on the peer                                                                                                                                                                                                                                                                                                                                            | on a peer, `start(5).plus(*/n/a)` reads the peer's store, but the nested `start(start({1,2}).plus(*/n/a).sum()).to(<>)` does not — data reads in shipped code belong in chain steps (`from(...)` seeds, B), not in arguments, until this is understood |

---

## 1. The menu at a glance

|   | pattern                   | law                                                      | status                                               |
|---|---------------------------|----------------------------------------------------------|------------------------------------------------------|
| A | homomorphism shard        | `h(a ⊎ b) = h(a) ⊕ h(b)`                                 | **done**: `sum`, `prod`, `count`, `order`, rec `sum` |
| B | data-local shard          | the store is the sum of its owners' stores               | needs a denotation decision                          |
| C | authority push-down       | delegation (Thm 5 + a pure chain)                        | open                                                 |
| D | parallel fan-out          | Thm 5                                                    | **done** (2026-10-26): virtual-threaded routes, max RTT |
| E | tree reduction            | associativity + commutativity: any bracketing, any shape | **v1 done**: `?hop=n`                               |
| F | ordered fold              | associativity alone                                      | struck for streams; `lst` only                       |
| G | keyed reduce              | Thm 19 per key                                           | **v1 done**: pointwise, `lst` values too             |
| H | redundant / hedged shards | idempotence                                              | open                                                 |
| I | span/route interpreter    | —                                                        | infrastructure                                       |
| J | algebraic aggregates      | a product monoid followed by a finalizer                 | open                                                 |
| K | limit / top-k push-down   | `take(k)·⊕ = take(k)·⊕·take(k)`                          | open                                                 |
| L | mergeable sketches        | approximate holistic aggregates as commutative monoids   | open                                                 |
| M | matrix multiplication     | `·` distributes over a commutative `+`                   | note only                                            |
| N | iterative / BSP           | `kleene`: iterate a sharded superstep                    | open; composes G, J, O                               |
| O | broadcast constants       | a constant floats with the code                          | free today                                           |
| P | computed seeds            | partition an index space, not data                       | open                                                 |
| Q | monad migration           | ship the monad to the address's authority                | open                                                 |
| R | durable results           | result identity in addressed mailboxes                   | open                                                 |
| S | master-participates       | §7 re-association over `{master} ∪ peers`                | **done 2026-10-28** — `?place=[local,peer]`          |

---

## 2. The entries

### A. Homomorphism shard — done (2026-10-21)

**Law.** A reducer `r` distributes iff it is a monoid homomorphism from the bag (the free commutative monoid ℕ[X])
to a commutative monoid `(M, ⊕, e)`: `r(a ⊎ b) = r(a) ⊕ r(b)`. By the universal property every such `r` is a
`map(g)` followed by the fold of `M`, and the partials recombine through that fold: the **combiner**. Thm 19 is the
case where `r` is its own combiner.

**Realized.** One `law_shard` plan. The combiner is computed, never declared (`LawTable.combiner`). A fold that
declares `monoidic` + `commutative` is its own combiner. A fold defined by its cat-graph `derivation` as a pointwise
map into such a fold recombines through that fold, looked up on its target object.

| fold          | combiner    | why                                                                                                                                                                        |
|---------------|-------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `sum`, `prod` | itself      | declares `monoidic` + `commutative`                                                                                                                                        |
| `count`       | `sum`       | `count ≡ map(1)·sum`                                                                                                                                                       |
| `order(s)`    | `kmerge(s)` | `order(B) ≡ split([_])·kmerge(B)`, and `kmerge` declares `monoidic` + `commutative`                                                                                        |
| rec `sum`     | itself      | the deep merge's fold declares `monoidic` + `commutative`: a `lst` is an atom of the merge, so colliding values (lsts included) bag in any order — distributed rec merging |

```
slave_i:  start(<part_i>).plus(1).count?hop=0()
master:   start([s_1..s_k]).select([to(p_1)..to(p_k)]).merge().sum()
```

The cat graph answers lazily: `|count?int<=#{*}().as(morphism::T)>>analysis>>combiner` gives
`/m/inst/sum?rng=/m/int&dom=/m/int{*}`.

**Left open.** Arg-less `order()` does not shard, because the derivation binds `order(B)`. `mean` (magmadic) and
`str` `sum` (not commutative) correctly do not shard.

### B. Data-local shard — move the code, not the data

**Law.** The cluster's store is the sum of its owners' stores, `from(p) = Σ_a from_a(p)`, so Thm 19 pushes the
floatable run and the reducer into each summand.

**Shape.** One path-segment pattern names the partitions, e.g. `/usr/data/+/current`:

```
source:  */usr/data/+/current.plus(1).sum()
slave:   from?hop=0(</usr/data/+/current>).plus(1).sum()        [-- the same code to every owner --]
master:  start([s, s]).select([to(p_1), to(p_2)]).merge().sum()
```

**Needs.**

- **Denotation.** `Network`'s javadoc says scoping never changes what an absolute URI means. On one machine,
  `*/usr/data/+/current` means that machine's matches, so a silent fan-out would change its meaning. Something must
  declare the pattern to be the cluster's: for example a cluster space mounted at `/usr/data/#`, whose read is the
  owners' union. B is then that space's `native` push-down (`db-pushdown-plan-architecture.md`).
- **Ownership.** One owner per address makes the union a bag sum. Replicas double-count, because a partial reduce
  erases vids and nothing can be deduplicated afterwards.
- A seed matcher on `from` (the shard plan demands `start`), with the seed span replicated rather than partitioned.

### C. Authority push-down — the network's `native` route

**Law.** Delegation: the db push-down (`from·count ↦ native_count`) with a peer as the backend, sound by Thm 5 for
a pure chain.

```
source:  *<ws://booger.local:8555/n/+>.plus(1).sum()
today:   ships from(/n/+); every value crosses; plus·sum run at home
emit:    start([from?hop=0(</n/+>).plus(1).sum()]).select([to(<ws://booger.local:8555>)]).merge()
```

**Needs.** `Route.peer` lowered, with the address taken from the seed's authority, and a pushable-span scan that
stops at side effects and master-local names (`to`, `print`, `~`, relative names). B is C with many owners: one
emitter, two seed shapes.

### D. Parallel fan-out — done (2026-10-26)

**Law.** Thm 5: no required order of apply or merge.

**Realized.** One virtual thread per route, awaited at the gather. `law_shard` now emits
`select([virtual::[code=>eval(p_1)], …]).select([>>rhs, …])`: the first `select` submits every round trip at once —
`virtual::[code=>eval(p_i)]` applied to its slave returns the thread immediately, each waiting on its own virtual
thread — and the second reads each thread's `rhs` (an `auto` that resolves the thread's future), so the master pays
max RTT, not Σ RTT. The ship is `eval` (the client's `inst` mini-instset override), and the thread's result hangs off
`rhs` as a lazy `auto` over the future — not the future itself — so the emitted rec stays serializable and
type-checkable. Witnessed by `ParallelFanOutTest` (two slow loop peers dispatched together, never one delay apart) and
the forked-peer suites (`DistributedRewriteTransportTest`, `TreeShardTransportTest`).

**Left open.** The `virtual` + `>>rhs` spelling is the "easiest way": it confines the change to the emitter and leaves
the transport's eager future-resolution (`Processor.Helper.applyFunction`'s `resolveFuture`) untouched. The
paradigmatic form is an async `eval`/`to` returning a `future::T` with `merge()` as the await — collapsing the layout
back to `select([eval(p_i)]).merge().<combiner>` and making the thread invisible (see §5). S (the master's own k+1
lane) landed as `?place=[local,peer]` — §S.

### E. Tree reduction — `?hop=height{arity}` — v1 done (2026-10-21, axes 2026-10-28)

**Law.** Associativity licenses any bracketing and commutativity any shape, so a fold over a tree of partials is the
fold over the bag:

```
(a + b + c + d)·r = ((a·r + b·r)·C + (c·r + d·r)·C)·C          [-- C = combiner(r), at every interior node --]
```

This is the current shard applied recursively: every interior node runs the master's shape (`merge().<C>`) over its
children's partials, and every leaf runs the slave's. Because A computes the combiner, E applies to every A-fold.

**Realized (v1).** `law_shard`'s fan-out decision states the axes — `.place(PlaceSet.peer()).hop(1, cInt.ANY())
.split(Partition.stream())` — and the budget is `?hop=height{arity}`: the height, and as a leading coefficient the
**arity**, how many children one node may fan out to (`[h,1]` is the chain, the pair form because a coefficient of one
normalizes away). A peer re-plans the part it is handed because applying a code compiles it (`Machine.applyCode`), so
the rule fires again where the part lands; at a height of zero the part is a leaf, and a zero arity is the identity.

```
root:      {1,…,8}.plus(1).sum?hop={2}2()                        [-- height 2, at most 2 children per node --]
        ⇒  start([start({1,…,8}).plus(1).sum?hop={2}1()]).select([to(p_1)]).merge().sum()
on p_1:    start([start({1,3,5,7}).plus(1).sum?hop={2}0(),
                  start({2,4,6,8}).plus(1).sum?hop={2}0()])
             .select([to(p_2), to(p_3)]).merge().sum()                 [-- p_1's own dialed peers --]
```

The axis resolves in chain order — the first statement wins: a `?hop` on any instruction of the chain (the reducer
under the `{…}` sugar, `sum?hop=2()`), else the plan's tree route. The default of height 1, arity the local
out-degree, is the star the shard always was. The arity caps the fan-out (the first `w` lanes of the emitter's lane
order, home-first then the resolution order — deterministic), still clamped by `min(shares, seed)`. The emitted slave
restates the FORM, never the number — the same arity one height down, on the slave's **fold** (its reducer, not its
seed): `{2}2 ⇒ {2}1 ⇒ 0`. `?hop` is stripped from every copied instruction (`?hops`, the deprecated pre-singular
spelling, is read as an alias for one cycle and stripped under either spelling). Witnessed by
`TreeShardTransportTest`: this machine dials p_1, p_1 dials p_2 and p_3, and p_1's own compiler plans the leaves.

**Still open.**

- **A machine-level default.** The plan carries the default today. A machine-wide one could sit on the compiler's
  rewriter rec (it already takes parameters, `fixpoint_rewriter::[max=>N]`), or plans could live at an address so a
  machine reconfigures one by writing it.
- **Visited-authority exclusion.** In the live two-host setup each console dials the other, so at `hop ≥ 2` a part
  re-planned on booger bounces back to ginger. That is correct (every level partitions its part exactly) and bounded
  by the budget, but wasteful. The fix is for the slave to carry the authorities already in the tree.
- **An interior node's own share.** v1 forwards everything and only recombines; keeping a part is S, which landed as
  the `local` designator of `?place` (`?place=[local,peer]`) — the arity is orthogonal to it (`?hop={2}3` at the
  master takes two children, `?place` decides whether one of the master's shares stays home).
- The dial graph is the reduction tree, so a machine reachable only through an intermediate host is reached.
  `MachineSwarmMigrationTest` hand-writes the same interior node with barriers (`barrier(uri).sum().to(uri)`).

### F. Ordered fold — struck for streams

**Law.** Associativity alone: contiguous blocks, recombined in block order. A stream is a bag, so a non-commutative
fold over a stream is undefined; order lives in `lst`. What remains is a `lst`-partitioned fold: split a `lst` value
into contiguous chunks, fold each, and concatenate in index order, for associative ops over ordered values. Low
priority.

### G. Keyed reduce — group-by — v1 done (2026-10-21)

**Law.** Thm 19 per key. `group([k ⇒ h])` is a homomorphism from the bag into the recs of `h`'s monoid, keyed by
`k`: the partials merge by key, and each key's partials fold through `h`'s combiner.

**Realized (v1).** The combiner is POINTWISE, computed from `h`'s and never named (`LawTable.pointwise`):

```
combiner(group([k⇒h])) = sum()==[_=>combiner(h)]
slave_i:  start(<part_i>).group([mod(3)=>sum()])?hop=0                     [-- [1=>1,0=>3,2=>5] --]
master:   start([s_1,s_2]).select([to(p_1),to(p_2)]).merge().sum()==[_=>sum()]   [-- [1=>{1,4},…] → [1=>5,…] --]
```

The rec `sum` is the deep merge: colliding partials append into a bag, and a bulked partial (`merge()` bulks equal
ones) reads as its copies — `Rec.plus` now reads coefficients through the merge (`Poly.Helper.distributed`). `==[_=>C]`
is `select`, the per-key fold. `LawTable.keyed` types a group's spec folds with the carrier, so the combiner is legible
before resolution. A nested `group` shards (its combiner is a chain, recursively), and E applies unchanged
(`group?hop=2(…)`). Witnessed by `DistributedRewriteTransportTest` (scalar, uri- and lst-keyed, nested) and
`TreeShardTransportTest` (`testKeyedTreeOverTransport`); `catInstSetTest#testAprioriPruning` pins what is declined.
`group` itself now keys a bulked member by the member, not its multiplicity (`{1,1,2}.group([_=>count()])` is
`[1=>2,2=>1]`, no longer `[{2}1=>2,2=>1]`).

**Declined (no shard).** A value with no combiner (`mean`, a constant, `_`); a spec whose entries fold differently (a
key keeps no record of the entry that claimed it, and the claim falls differently per part).

**`lst` values shard too.** A `lst` is an atom of the deep merge, so an `order`'s sorted partials bag whole per key and
`kmerge` merges the runs (`[a=>[1,2,3]]+[a=>[2,3]]` is the bag `[a=>{[1,2,3],[2,3]}]`; concatenating is a select's
`+`, `[a=>[1,2,3]]==[a=>+[2,3]]`). `Lst.plus` now reads each operand's coefficient into its slots once (it squared it).

**Left open.** The cat graph names no combiner for `group` (`combinerOf` answers single edges only). A true shuffle
(keys hash-routed peer to peer) needs slave-to-slave reach and pays only when the key space outgrows the master.

### H. Redundant / hedged shards

**Law.** Idempotence, `x ⊕ x = x` (`or`, `and`, `dedup`, and max/min once they exist). A duplicate partial is
harmless, so a partition can go to r peers and every reply folds: at-least-once delivery is correct. For
non-idempotent folds (`sum`, `count`), hedge instead, and the first reply per partition wins.

**Needs.** Per-partition identity (each reply's minted tag already binds its partial in the master's base frame),
and a fold that tolerates a failed replica. That is sound only under idempotence; today `sendRecv`'s contract is
"a lost reply must poison a reduction". A shard is pure code over a fixed part, so it is its own lineage: a failed
shard can be re-run elsewhere.

### I. Span/route interpreter

`PlanInterpreter` lowers from `plan.routes()` and spans instead of hand-built emitters, with the real span
`[(lo, hi, role)]` and multi-cut chains. This is the gating piece for pipeline, shuffle and native
(`distributed-patterns-through-algebra.md` §6). Worth it once two or three more emitters exist.

### J. Algebraic aggregates — mean, variance

**Law.** Not a homomorphism, but a homomorphism into a product monoid followed by a finalizer:
`mean ≡ (sum, count)·div`, `var ≡ (sum, sum of squares, count)·…`. The partials are tuples recombined pointwise, and
the finalizer runs once at the master.

**Needs.** A form for several folds over one stream in one pass (its mtron spelling is open), and a derivation whose
tail is fold-then-map, so the combiner is the pointwise fold and the finalizer lands in the master's tail. `mean`
also ignores coefficients today (its own TODO).

### K. Limit / top-k push-down

**Law.** A tail op `t` with `t·C = t·C·(t on each partial)` can be replicated into the slaves. Top-k: the slaves run
`order(s).take(k)` and the master `kmerge(s).take(k)`. On a bag, `take(n)` ("any n") pushes down the same way.

**Needs.** A post-cut push law beside the combiner, so the plan's `post` span becomes partly shippable.

### L. Mergeable sketches

**Law.** A holistic aggregate (distinct count, quantiles, frequencies, membership) has no finite homomorphism, but an
approximate one does. HyperLogLog, KLL / t-digest, count-min and bloom filters are commutative (often idempotent)
monoids. Declare their theories and A, E and H apply unchanged; the idempotent ones get H's at-least-once delivery
for free.

**Needs.** Sketch types with declared monoid theories; the combiner then computes like any other.

### M. Matrix multiplication — note

`/m/vec` carries `vec::T` and `mtrx::T` (both `lst` refinements) with `dot` and `transpose`. There are three
decompositions, by which index is split:

1. **The rows of A.** `C_i = A_i·B` is a floatable map over rows, with `B` a broadcast constant (O). The rows
   reassemble by index: an addressed union, no fold.
2. **The inner dimension k.** `C = Σ_k A_{:,k}·B_{k,:}`: each peer computes a full-size partial product and the
   master sums matrices. This is A with matrix `sum`, once matrix addition is declared a commutative monoid and `·`
   distributive over it (`left_distributive`, `right_distributive`).
3. **2-D blocks.** `C_{ij} = Σ_k A_{ik}·B_{kj}`: block products keyed by `(i, j)` and summed, which is G over block
   keys. SUMMA and Cannon are E- and pipeline-shaped schedules of it.

Not designed further yet.

### N. Iterative / BSP

**Law.** `kleene`: iterate a sharded superstep to a fixpoint, with the combiner as the superstep boundary. k-means:
assign each point to its nearest centroid (a floatable map with the centroids broadcast, O), take (sum, count) per
centroid (G + J), broadcast the new centroids, and repeat. PageRank and label propagation (min) have the same shape.

**Needs.** G and J; a loop whose body is a sharded plan (`repeat`/`loop` around the layout); and per-round identity (R),
so a late partial cannot fold into the wrong round.

### O. Broadcast constants

**Law.** A constant floats with the code (Thm 6). Code shipping carries its literals, so a broadcast value is just an
argument of the floatable run, such as a small rec joined against, or `·B` in M (1). Free today. A large broadcast
value is better read from a replicated address on each peer.

### P. Computed seeds

**Law.** Partition an index space instead of data. Monte Carlo is the example: each peer gets a sample count and its
own RNG seed, and the master sums the hits. One O (1) message per peer, and no data shipped.

**Needs.** A seed matcher for a generator form (the shard plan reads a literal `start` bulk today), and a partition
of the index space (a count or a range).

### Q. Monad migration

**Law.** Address-first: ship the monad (its lhs plus the rest of the chain) to the authority of the next address it
reads. This is C applied per step rather than once. With bulking, monads that land on the same address in the same
state merge their coefficients, so the network carries counts, not copies. That is the shape of vertex-centric
message passing and of distributed graph traversal.

**Needs.** At a remote read, ship the continuation instead of pulling the value, as a `law_shard` layout (C's, once per
step), and per-step routing by owner.

### R. Durable results — `Route.space`

**Law.** Result identity. A partial written to an addressed mailbox (round, shard) can be gathered by a barrier
whenever it arrives, and a duplicate overwrites rather than double-counts. This gives fire-and-forget jobs that
survive a master restart, and it is the substrate for N's rounds and H's retries (`distributed-computing.md`,
Stage 3).

**Needs.** A `Route.space` lowering and a `shard_result::[authority, round, status, value]`.

### S. Master-participates — the local lane (ruled 2026-10-24, done 2026-10-28)

**Law.** The master is a place at distance zero, so the fan-out is over `{master} ∪ peers`: `k = peers + 1` slices,
the master folding one locally (its own lane) and shipping the other `k-1`. Re-association is result-preserving (§7),
so the fold over master-local + peer partials is the fold over the bag.

**Why.** In v1 `shard` set `k = min(peers, seed)` and shipped **all `k` slices to peers**; the master only ran
`merge().<combiner>` and folded nothing, so with one peer the whole seed left the master. Keeping a slice earns the
master its share and shrinks every hop's payload.

```
master:  start([ start(own_slice).<run>.<C>,       [-- folded in place: a local lane --]
                 start(slice_1).<run>.<C>,          [-- shipped --]
                 … ])
           .select([ <identity>, to(p_1), … ])
           .merge().<C>
```

**Realized (2026-10-28).** The `local` designator of `?place` is the mechanism: `?place=[local,peer]` asks for one home
share and one per dialed peer, and the lanes are the design's — **lane 0 is the addressless `eval()`** (the home place
at distance zero, gathered by the same `>>rhs`), then the peers in resolution order. Nothing about the fan-out is
special-cased: the share is strided over the seed like any other, the local lane is part of `k` in `shard`'s partition
arithmetic, and the master re-applies the same combiner to its own partial. `[{4}local]` is the same mechanism with
four home lanes and no peer at all, and the decision still demands the off-machine merge's laws because a home lane is
a concurrent partial gathered after the fact (`distributed-computing-qparams.md` §2, `PlaceLaneTest`).

---

## 3. How the entries compose

- **E applies to every A-fold**, and to G, J and L: any combiner recombines at any interior node.
- **D multiplies everything** — done: its absence made distribution slower, not wrong.
- **S applies to every fan-out** — A, E, G, and any shipped partial: the master (and every interior node) folds its own
  slice instead of only recombining.
- **N is G + J + O under `kleene`**, with R for rounds.
- **M (1)** is O plus an addressed union, **M (2)** is A with matrix `sum`, and **M (3)** is G over block keys.
- **L plugs into A, E and H** by declaring theories, with no new lowering.
- **C is B with one owner, and Q is C per step.**

## 4. Open decisions

1. **B's denotation.** A mounted cluster space at `/usr/data/#`, or another declaration.
2. **Distribution parameters.** Where a machine-wide default lives (E's `hop` first): the compiler's rewriter rec, or
   plans written at an address. The expression (`?hop` on any instruction) and the plan's route are the other two
   layers, nearest first.
3. **E's visited set.** Carried authorities, the origin only, or the dial graph's own direction.
4. **G's parametric combiner.** Settled (v1): computed from the value inst directly (`LawTable.pointwise`); a `lst` is
   an atom of the deep merge, so `lst`-valued partials bag whole (a bag of runs) and recombine too.
5. **J's spelling.** How mtron writes several folds over one stream.
6. **Exact-tid lookups.** `morphism::T` reads laws by exact tid, so poly-registered ops (`order`, `kmerge`) need their
   registered spelling on the cat-graph surface; the planner is unaffected.

## 5. Instructions the patterns want (Marko, 2026-10-21)

Surfaced by `AbstractMultiServerSingleSpaceTest`: a master, three forked satellites, one MariaDB.

- **`timeout(inst)`** is a wrapper, not a parameter of each instruction: `a.timeout(inst).catch(_).b.c`. It applies
  `inst` under a deadline, and on expiry emits a `fail::T` that the next instruction can `catch()`. That makes a
  deadline explicit and catchable. Today the only deadline is the hidden 2 s `FutureObj` wait, which turns any
  workload longer than 2 s into a timeout fail. Open: how the duration is spelled (a `?ms` on `timeout`, per the
  `?query` rule, or a time-typed argument).
- **`>|`, fold-apply** — implemented (Marko, 2026-10-21): one instruction, written prefix (`>|[1,+2,+3]`), postfix
  (`[1,+2,+3].>|`) or infix (`1.>|[+2,+3]`) — all `6`, and `10.>|[+3,+2,+3]` is `18`. The seed is the lhs, or the
  lst's head when there is none, and the members apply left to right. Merge wedges a seed in without firing anything
  — `1>-[+2,+3]` is the inert lst `[1,plus(2),plus(3)]` — and `1>-[+2,+3]>|` is `6`: the infix form is
  wedge-then-fold, `x.>|L` is `x>-L>|`, and the lst between the two is a program held as data, open to ordinary lst
  operations. A lst of instructions is inert until it is folded, so a workload's code can be a lst (`>|*workload/code`)
  instead of a quoted code in a closure.
- **`<|`, the backwards cascade** — Marko wanted it (2026-10-21), and it is expressible today with `swap`, his
  "backwards apply": it "makes the lhs the arg and the arg the lhs", so `P.swap(x)` runs `P` on `x`.
  `[plus(2).plus(2)]==[as(inst::T)]>-.swap(8)` is `12`, `[plus(2)]>-.swap(8)` is `10`, and the expression that wanted
  `<|` is `[plus(2).plus(2)]==[as(lst::T)]==[as(code::T)]==[as(inst::T)]>-.swap(1)`, `5`. The program stays inside the
  outer lst through the `==` chain and `>-` releases it only for `swap` to use (Marko: "work inside polys"). So `<|`
  would be sugar for that tail — `P.<|x` for `P` as a lambda swapped onto `x`; Marko calls swap "super useful, but
  arcane in semantics". The seed-first spellings stay closed to a computed program: parentheses after `>|` or `>-` do
  not group (`1.>|(P)` and `1>-(P)` both stop at the `)` with "the expression is incomplete"), and without them the
  operators chain left to right — `1.>|[plus(2).plus(2)]` is already `5` when `==[as(lst::T)]` sees it, and `1>-[…]`
  wedges before `==` applies `as(lst::T)` to the `1`, which it refuses because it expects instructions (`1 is not a
  lst::T`). `apply` is not it either: it takes a rec of bindings (`expected /m/rec (got [1])`).
  Open: a program headed by `start` takes no input (`dom=noobj{0}`), so `swap(100)` is a type error, and Marko's
  spelling for feeding it nothing is `swap(100;)` — `;` is `end()`, the terminal instruction of the inst ring. It
  does not work yet: on the live server the `100` still reaches `start` as `{1}`, and in-process (all type checks
  on) `end` fails its domain check with a `fail::T` lhs range. `swap(noobj)` answers nothing.
- **Broadcast and keyed scatter over the peers.** `xyz.to(*/network/peers)` ships to every peer the current frame
  holds, and `[booger.local=>…, chibi.local=>…, ginger.local=>…]==*/network/peers` pairs each payload with a peer by
  KEY (a select), so no program names a port. Needs: `to` over a bag of addresses; `peer::T` applicable as its own
  transport; peers keyed by name (today they are keyed by service authority, `ws://host:port`).
- **Code as data** — mostly there (Marko, 2026-10-21): `[plus(2).plus(2)]==[as(lst::T)]` is
  `[[plus(2),plus(2)]]`, `==[as(code::T)]` puts it back, and `[plus(2).plus(2)]==[as(inst::T)]` wraps the code in a
  lambda, `inst(#{*}::T){plus(2).plus(2)}`, one instruction. The code rides in a lst so that `==` applies `as` to it
  as a value; appended to the chain, `as` would become one more of its instructions. A templated read decomposes
  too — `[*</usr/data/${*workload/xyz}/+>.count()]==[as(lst::T)]` is `[[*</usr/data/${*workload/xyz}/+>,count()]]` —
  but the template sits inside slot 0's uri, one level further down, and that level is still to come: `as(lst::T)`
  on an inst answers a `docs::` rec for `as?rng=lst&dom=inst` (`<no description>`), not a lst. A quote `|(…)`
  (`/m/inst/block`) and a lone `plus(2)` (`/m/inst/plus`) are insts, not codes. Filling the template before the
  code ships is what would let the receiver push the read down to SQL. Next (Marko): an obj that is not a
  `call::T` converts as `map(obj)`, the constant instruction that answers it, so a wedged seed is simply a program's
  first instruction. Open: what an inst's slots are (its arguments?) and where the op goes on the way back (its tid?).
  On the wire, keep programs inside a poly: the ws handler applies a received result once more
  (`mtron_wsHandler.java:70`, after the read has evaluated it), so a bare lambda arrives run — over wsplus
  `[start(7).plus(1)]==[as(inst::T)]>-` is `8` where the console shows the lambda. A lst protects its members.
- **Where am I** needs no instruction: `*~/network/host` (`booger`). Forked satellites on one host share that name,
  though, so telling them apart needs the service authority.
