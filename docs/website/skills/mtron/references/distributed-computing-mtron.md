---
name: distributed-computing
description: |
  The distributed-computing pattern menu for metatron — distribution is a theorem, not a feature. The stream-ring
  laws (Thm 5 / 6 / 16 / 17 / 19) decide *which* fold can ship, and the `/m/math/cat` machinery realizes it: the
  declared theories and process laws on `object::T` / `morphism::T`, `LawTable`'s *computed* combiner, the `plan::T`
  DSL (`cut` / `where` / spans) built by `PlanBuilder` and lowered by `PlanInterpreter`. One row per pattern (A–S):
  its algebraic law (the latex), the mtron you write **before** the rewrite (the pre-write motif), and the sharded
  layout the rewrite emits (the rewrite motif). A ✅ marks a pattern implemented today.
  TRIGGER: When reasoning about distribution, sharding, or fan-out in metatron; when adding or explaining a
  distributed-computing pattern; when you need the law and the pre-write / rewrite motifs for one of the menu's
  entries (A–S); when wiring a `plan::T` / `PlanBuilder` / `PlanInterpreter` / `LawTable` combiner.
---

# distributed computing — the pattern menu

Distribution in metatron is not a subsystem you add: it is what a fold's *declared algebra* already licenses. A
reducer ships, splits, and recombines **because its carrier declares a law**, never because a feature was turned on.
The working list below — the menu, entries A–S — is the catalogue of those laws: each entry is a pattern, its algebraic
law is the reason it is sound, its *pre-write motif* is the mtron you write, and its *rewrite motif* is the sharded
layout the rewriter emits. The premise is `docs/design/distributed-patterns-through-algebra.md` ("distribution is a
theorem, not a feature"); the first realization is `law_shard`; the substrate and its gaps live in
`docs/design/distributed-computing.md`. This doc is the reference: what to build next, and why each item is sound.

## the substrate — the constructions a pattern rests on

A distribution pattern is a small amount of *declaration* on top of machinery that already exists. Before reading the
menu, fix the five pieces a row names:

| type | where | the role it plays |
|---|---|---|
| `machine::T` | `~/` | the topology. `push`/`pop` is a call stack; a child machine is a subproblem; a *mini-cluster* is a child machine + the peers its network declares + the URI space they share. The authority is the boundary — an address names work this machine owns or work a peer owns. |
| `network::T` | `~/network` | the reach. `roster` is what the system **knows** (connectionless endpoints); `peer` is what **this** machine has **dialed**. Knowledge is inherited, reach is not. A `peer::T` carries a `host` (where to dial it back) — the marker that makes it a fan-out target; an inbound accept has none and never receives a shard. |
| `rewriter::T` | `~/compiler/rewriter` | the stage. The compiler's first atomic pass lowers `code::T → code::T` to a fixpoint; `fixpoint_rewriter::[max=>N]` is one such stage. A distribution rule is one of its rewrite rules, applied in place, and every emitted layout is itself re-planned where it lands. |
| `instset::T` | `/m/math/cat` | the declaration surface. `object::T` holds a type's structural theories; `morphism::T` holds an inst's process laws, `form`, `class`, and `analysis`; `path::T` lifts *code* to a vertex whose out-edges are the live rewrite rules, each a `plan::T`. `lower(plan::T)` and `bind(plan::T)` are the two applications. |
| `law::T`, `plan::T`, `path::T` | — | the engine. A `plan::T` assembles `[name, cut, where]` (a value, validated against its shapes' law obligations) and lowers to a layout; the combiner a fold's partials recombine through is read off the `law::T` declarations — never named, always computed. |

`LawTable.combiner(keyed, laws)` is the one function that decides a fold's recombination:

- a fold that declares `monoidic` + `commutative` is **its own** combiner (`sum`, `prod`);
- a fold defined by its `derivation` recombines through the fold that defines it (`count ↦ sum`, `order(s) ↦ kmerge(s)`);
- a keyed fold `group([k ⇒ h])` recombines **pointwise** — `sum()==[_=>combiner(h)]` — which no single edge names.

## the theorem bindings

"Which law the anchor obeys" is the whole decision. The stream-ring theorems are the implementation sites:

| theorem | statement | meaning for distribution |
|---|---|---|
| Thm 5 | streams are atemporal | no required order of apply/merge — remote placement is always safe, and the fan-out may be parallel |
| Thm 6 | $ca \cdot 1b = 1a \cdot 1b \cdot c1$ | the coefficient floats through non-reduce chains — elementwise work is independent, replicate it |
| Thm 16 | reducers are temporal | the fold is the one synchronization point (the barrier) |
| Thm 17 | $c1 \cdot 1a \neq 1a \cdot c1$ for $a \in \mathcal{F}_r$ | floating stops at a reducer — the fold is the cut |
| Thm 18 | $\langle\mathcal{F}_r, +, \cdot\rangle$ is a near-ring (left distributive) | a reducer distributes over a fan-out, $c(a+b) = ca + cb$ — the combiner's algebraic home |
| Thm 19 | monoidic reducers are idempotent and semi-right distributive, $(a+b)c = (ac+bc)c$ | a commutative-monoid fold splits, merges, and re-applies — the shard is sound, not merely partitioned |
| Thm 21 | $\langle\mathcal{F}_r, \cdot\rangle$ is functionally closed | serial folds compose — a fold after a fold is a fold, so `merge().combiner.tail` and J's finalizer stay reducers |

These seven are the bindings that matter for distribution; the rest of the paper's theorems describe the function
rings the shapes live in. The full paper is [Stream Ring Theory](../../../articles/stream-ring-theory.html).

## the route vocabulary — the distribution shape

The plan's `where` names the shape, and the shape is the law's consequence. `Placement.Shape` today:

| shape | the law's consequence | obligations on a cut shipped to it |
|---|---|---|
| `local` | transform in place (the algebraic folds) | — |
| `peers` | star / scatter-gather — the commutative-monoid fold | `monoidic`, `commutative` |
| `tree` | the same fold, recursively, at every interior node | `monoidic`, `commutative` |
| `shuffle` | keyed partition — the join | `monoidic` |
| `space` | write to a spatial address | — |
| `none` | delete (the unit law's transform) | — |

`PlanBuilder.create()` validates $\texttt{obligations}(\texttt{shape}) \subseteq \texttt{cut.laws}$; a cut shipped to a
shape and re-applied at home must declare every law that shape demands, or the plan refuses to build.

---

## the menu

The centerpiece: every pattern, its law, and the two motifs. The **pre-write motif** is the mtron before the rewrite
fires; the **rewrite motif** is the emitted layout (`s_i` a slave, `p_i` a peer, `part_i` one factor of the seed bulk).
✅ = implemented today; an empty box = the law is sound but the lowering is not yet written. `s_i`, `p_i`, `part_i`
are placeholders — the schemas below are motifs, not literal runs.

| pattern | law (latex) | mtron pre-write motif | mtron rewrite motif |
|---|---|---|---|
| **A** homomorphism shard ✅ | $h(a \uplus b) = h(a) \oplus h(b)$ | `{1,2,3,4}.plus(1).count()` | `start([start?hops=0(part_i).plus(1).count(), …]).select([to(p_i), …]).merge().sum()` |
| **B** data-local shard | $\mathrm{from}(p) = \sum_a \mathrm{from}_a(p)$ | `*/usr/data/+/current.plus(1).sum()` | `start([from?hops=0(</usr/data/+/current>).plus(1).sum(), …]).select([to(p_i), …]).merge().sum()` |
| **C** authority push-down | $\mathrm{from} \cdot \mathrm{count} \mapsto \mathrm{native\_count}$ (Thm 5 + a pure chain) | `*<ws://host:8555/n/+>.plus(1).sum()` | `start([from?hops=0(</n/+>).plus(1).sum()]).select([to(<ws://host:8555>)]).merge()` |
| **D** parallel fan-out ✅ | $\langle x\rangle(a+b) = \langle x\rangle a + \langle x\rangle b$ (Thm 5) | any shard; the transport, not a new fold | `start([s_i, …]).select([virtual::[code=>eval(p_i)], …]).select([>>rhs, …]).merge().combiner` |
| **E** tree reduction ✅ | $(a+b+c+d)\cdot r = ((a\cdot r + b\cdot r)\cdot C + (c\cdot r + d\cdot r)\cdot C)\cdot C$ | `{1,…,8}.plus(1).sum?hops=2()` | nested `start?hops=n-1(part_i)…` at each interior node — the dial graph is the tree |
| **F** ordered fold | $a \cdot (b \cdot c) = (a \cdot b) \cdot c$ (associativity alone) | a `lst`-partitioned fold (streams never shard) | pipeline — carry the running total, neighbor-to-neighbor |
| **G** keyed reduce ✅ | $\mathrm{combiner}(\mathrm{group}([k \Rightarrow h])) = \mathrm{sum} \cdot \mathrm{select}([\_ \Rightarrow \mathrm{combiner}(h)])$ | `{1,2,3,4,5,6}.group([mod(3)=>sum()])` | `start([start?hops=0(part_i).group([mod(3)=>sum()]), …]).select([to(p_i), …]).merge().sum()==[_=>sum()]` |
| **H** redundant / hedged shards | $x \oplus x = x$ | a partition shipped to r peers | every reply folds (at-least-once); non-idempotent → hedge, first reply wins |
| **I** span/route interpreter | $\mathrm{plan} \mapsto \mathrm{layout}$ (infrastructure) | `[start({1,2,3}).plus(1).sum()]==[as(cat:path::T)]==[lower(>>law_shard)]` | `PlanInterpreter` lowers `plan.routes()` + a real span `[(lo, hi, role)]` |
| **J** algebraic aggregates | $\mathrm{mean} \equiv (\mathrm{sum}, \mathrm{count}) \cdot \mathrm{div}$ | `{...}.mean()` | pointwise tuple partials; the finalizer runs once at the master |
| **K** limit / top-k push-down | $\mathrm{take}(k) \cdot \oplus = \mathrm{take}(k) \cdot \oplus \cdot \mathrm{take}(k)$ | `{...}.order(s).take(k)` | slaves `order(s).take(k)`; master `kmerge(s).take(k)` |
| **L** mergeable sketches | $\hat h(a \uplus b) = \hat h(a) \oplus \hat h(b)$ | `{...}.hll()` (a sketch fold) | A / E / H once the sketch declares its monoid theory |
| **M** matrix multiplication | $A \cdot (B + C) = A \cdot B + A \cdot C$ | `mtrx::[...].mult(mtrx::[...])` | O + addressed union (rows); A with matrix sum (inner k); G over block keys (2-D) |
| **N** iterative / BSP | $x^{*} = x \vee f(x) \vee f^{2}(x) \vee \cdots$ | a sharded superstep repeated | `repeat`/`loop` around the layout; the combiner is the superstep boundary |
| **O** broadcast constants ✅ | $ca \cdot 1b = 1a \cdot 1b \cdot c1$ (Thm 6) | `{...}.plus(B).sum()` (B a constant) | the constant rides as an argument of the floatable run — no fan-out change |
| **P** computed seeds | $[0, n) = \uplus_i \mathrm{index}_i$ | Monte Carlo — each peer gets a count + its own RNG seed | one O(1) message per peer; nothing shipped |
| **Q** monad migration | $\mathrm{authority}(m)$ — ship the monad, not the value | a remote read mid-chain | `law_shard` per step — the continuation ships instead of the value |
| **R** durable results | $\mathrm{id} : \mathrm{partial} \mapsto \mathrm{address}$ | `to(</usr/job/round/shard>)` | `Route.space` + `shard_result::[authority, round, status, value]` |
| **S** master-participates | $\bigoplus_{i=0}^{k} a_i \cdot r$ over $\{\mathrm{master}\} \cup \mathrm{peers}$ | `{1,…,8}.plus(1).sum()` | the master folds its own slice (a local lane) and ships k−1 |

**Legend.** ✅ = implemented (`A` done; `D` done 2026-10-26; `E`, `G` v1 done; `O` free today — it needs no plumbing).
An empty box is *not* "unsound": the law holds, the lowering is unwritten. `F` is struck for streams (order lives in
`lst`, never a bag); `S` is ruled (2026-10-24), deferred to Phase 7; `M` is a note; `I` is infrastructure.

## the taxonomy — the menu as a tree

The nineteen entries are not a flat list: they fall out of **two axes** — the *data-side* law the anchor obeys (which
fixes the route shape), and the *location-side* question of where the work lives — plus a set of **orthogonal** rules
that multiply any fan-out and a set of **composites** that build on the rest. The table above keeps the A–S lettering
stable; this is the lay of the land.

```
distribution — the menu (A–S)
├─ by the anchor's law            (the data-side theorem → the route shape)
│  ├─ free · floatable (Thm 6)        → O ✅
│  ├─ star · comm. monoid (Thm 19)    → A ✅  E ✅  G ✅  J  K  L  H
│  ├─ pipeline · associative          → F
│  └─ shuffle · keyed                 → (G's hash-route; M(3))
├─ by the location                (native · delegation — where the work lives)
│  └─ B  C  Q  P
├─ orthogonal                     (multiply any fan-out)
│  └─ D ✅  S
└─ composite & substrate          (built from the above)
   └─ N  R  M  I
```

<!-- figure source: assets/tikz/distributed-patterns.tex (same recipe) -->
<img src="../assets/distributed-patterns.png" alt="the menu (A–S) as a taxonomy tree: by the anchor's law (free → O, star → A E G J K L H, pipeline → F, shuffle), by the location (B C Q P), orthogonal (D S), composite (N R M I); green = implemented" width="100%" style="margin:0.3em 0 0 0"/>

The four top branches read the two axes of `docs/design/distributed-patterns-through-algebra.md`:

- **the anchor's law** is the *data-side* theorem, and it *is* the route shape: `free` (floatable, Thm 6) replicates
  the run with no coordination; `star` (commutative monoid, Thm 19) scatters, partial-reduces, and gathers — `A` is
  the one engine, and `E` / `G` / `J` / `K` / `L` / `H` are its refinements; `pipeline` (associative) carries the
  running total; `shuffle` (keyed) co-locates the key (G's future hash-route, and M(3)).
- **the location** is the *machine-side* question — native / delegation: `B` moves the code to the data, `C` is `B`
  with one owner, `Q` is `C` per step, `P` partitions the index space instead of data.
- **orthogonal** multiplies any fan-out: `D` ships every round trip in parallel (max RTT), `S` keeps a slice home.
- **composite & substrate** builds on the rest: `N` iterates a sharded superstep, `R` writes results to a spatial
  address, `M` decomposes into O / A / G over three index splits, `I` lowers `plan.routes()` to a layout.

Green = implemented (the ✅ in the table); white = the law holds, the lowering is unwritten.

## the implemented layouts, spelled out

The four done patterns, with the slave/master forms written in full. `law_shard` (A) is the one engine they all lower
to; the rest specialize it.

### A — homomorphism shard (`sum`, `prod`, `count`, `order(s)`, rec `sum`)

```
slave_i:  start?hops=0(<part_i>).plus(1).count()
master:   start([s_1..s_k]).select([to(p_1)..to(p_k)]).merge().sum()
```

The combiner is computed, never declared — a fold that declares `monoidic` + `commutative` is its own, and a fold
defined by its `derivation` recombines through the fold that defines it:

| fold | combiner | why |
|---|---|---|
| `sum`, `prod` | itself | declares `monoidic` + `commutative` |
| `count` | `sum` | `count ≡ map(1)·sum` |
| `order(s)` | `kmerge(s)` | `order(s) ≡ split([_])·kmerge(s)`, and `kmerge` declares `monoidic` + `commutative` |
| rec `sum` | itself | the deep merge's fold declares `monoidic` + `commutative` — a `lst` is its atom, so colliding values bag in any order |

The cat graph answers lazily: `|count?int<=#{*}().as(morphism::T)>>analysis>>combiner` is
`/m/inst/sum?rng=/m/int&dom=/m/int{*}`. `mean` (magmadic) and `str` `sum` (not commutative) correctly do not shard.

**`order` is the distributed sort.** The slave sorts its shard and the master k-way-merges the runs — the same shard,
with `kmerge` as the combiner instead of `sum`:

```
slave_i:  start?hops=0(<part_i>).order(>>)                     [-- each shard sorts by its key --]
master:   start([s_1..s_k]).select([to(p_1)..to(p_k)]).merge().kmerge(>>)
```

`order(>>)` on a stream of single-key recs sorts by value, and `kmerge(>>)` merges the sorted partial runs — a k-way
merge that re-reads the multiplicity, so two `1`s in different shards merge into `{2}1`, never `[1,1]`. The same
`kmerge` recombines a keyed sort: under `group([k⇒order(>>)])` each key's sorted partials bag whole and merge per key
(entry G).

### D — parallel fan-out (virtual-threaded routes, max RTT)

The emitted routes are `select([virtual::[code=>eval(p_i)], …]).select([>>rhs, …])`: the first `select` submits every
round trip at once — each `virtual::[code=>eval(p_i)]` returns its thread immediately and waits on its own virtual
thread — and the second reads each thread's future off its `rhs` slot. The master pays **max RTT, not Σ RTT**. The ship
is `eval` (the client's mini-instset override), and the result hangs off `rhs` as a lazy `auto` over the future, so the
emitted rec stays serializable.

### E — tree reduction (`?hops=n`)

```
root:      {1,…,8}.plus(1).sum?hops=2()
        ⇒  start([start?hops=1({1,…,8}).plus(1).sum()]).select([to(p_1)]).merge().sum()
on p_1:    start([start?hops=0({1,3,5,7}).plus(1).sum(), start?hops=0({2,4,6,8}).plus(1).sum()])
              .select([to(p_2), to(p_3)]).merge().sum()
```

The budget resolves nearest first — a `?hops` on any instruction, else the plan's tree route (default 1, the star). A
peer re-plans its part because applying a code compiles it (`Machine.applyCode`); at `hops = 0` the part is a leaf.

### G — keyed reduce (`group([k ⇒ h])`)

```
slave_i:  start?hops=0(<part_i>).group([mod(3)=>sum()])              [-- [1=>1,0=>3,2=>5] --]
master:   start([s_1,s_2]).select([to(p_1),to(p_2)]).merge().sum()==[_=>sum()]   [-- [1=>{1,4},…] → [1=>5,…] --]
```

The combiner is pointwise, computed from `h`'s (`LawTable.pointwise`). The rec `sum` is the deep merge: colliding
partials append into a bag, and a bulked partial reads as its copies (`merge()` bulks equal ones). `==[_=>C]` is
`select`, the per-key fold. A `lst` is an atom of the merge, so an `order`'s sorted partials bag whole per key and
`kmerge` merges the runs.

## how the entries compose

- **E applies to every A-fold**, and to G, J and L: any combiner recombines at any interior node.
- **D multiplies everything** — its absence made distribution slower, not wrong.
- **S applies to every fan-out** — the master (and every interior node) folds its own slice instead of only recombining.
- **N is G + J + O under `kleene`**, with R for rounds.
- **M (1)** is O plus an addressed union; **M (2)** is A with matrix `sum`; **M (3)** is G over block keys.
- **L plugs into A, E and H** by declaring theories, with no new lowering.
- **C is B with one owner**, and **Q is C per step**.

## how to add a pattern

A pattern is a declaration, not a new subsystem. The recipe, in the order the machinery consumes it:

1. **Declare the law.** A fold declares `monoidic` + `commutative` (or a `derivation`) in its instset's `LawTable`
   (`mInstSetLawTable`, `mathInstSetLawTable`): `entry(inst, inverse, laws…)`, or `entry(inst, inverse, derivation,
   laws…)` for a derived fold.
2. **Let the combiner compute.** `LawTable.combiner` reads it back — self-combining, derivation-defined, or pointwise
   (`group`) — so no combiner is ever named.
3. **Author the plan.** `PlanBuilder.plan(name).cut(Cut.reducer(Law.monoidic, Law.commutative))` with a `local(at,
   post)` placement (the home `merge().<combiner>.<tail>`) and a `tree(1, seed.partition(), pre.withoutSeed(), at)`
   placement (the fan-out). `create()` validates the shape obligations.
4. **Register it.** The plan joins `catRewrite.rules()`, wired into `catInstSet`'s `REWRITE` key — a `plan::T` out-edge
   on every `path::T` it can move.
5. **The interpreter lowers it.** `PlanInterpreter.shard` matches the one-cut shape
   `[initial, {*}floatable, blocked(reducer ∧ laws), {*}tail]`, factors the seed bulk, and emits
   `start([slaves]).select([to(p_i)]).merge().<combiner>`.
6. **Liveness gates it.** The plan is a live out-edge only over reach (`Places.reachable()` — roster ∪ dialed peers) and
   ships only to targets that `serve` the route (`Places.serves`: online, and the client overrides the placement's
   insts). An outage *shrinks* the fan-out; it never poisons the reduction.

## see also

* [the category graph](cat-instset-mtron.md) — `object::T` / `morphism::T` / `path::T` / `plan::T` / `place::T`, the
  lifts a pattern is authored and matched against.
* [rewrite system](../metatron/references/rewrite-system-java.md) — the `rewriter::T` stage the plans run in.
* [mtron language reference](language-reference-mtron.md) — `start` / `select` / `merge`, coefficients, the `==`
  per-key fold.
* [Stream Ring Theory](../../../articles/stream-ring-theory.html) — the coefficient algebra: `floatable` / `blocked`,
  Thm 19 which `law_shard` realizes.
* `docs/design/distributed-patterns-through-algebra.md` — the premise, in one page: the patterns are the laws, not the
  ops.
* `docs/design/distributed-patterns-menu.md` — the working list this reference distills, with the open decisions and
  the instructions the patterns still want.