# The Categorical Rewriter and the Distribution Rewrite

**Date:** 2026-10-14
**Context:** Design session following the Network component's completion (peers, ?clientq identity,
construction-is-connection, `Addressable.index`/`drop`, `NetworkUnion`). The goal: the first
distribution rewrite (`law_shard`), and the rewriter framework it should be authored in. The
foundation is *Stream Ring Theory* (Rodriguez 2019); the implementation substrate is `/m/math/cat`
and `algebra/rewrite/{RewriterBuilder, RewriteBuilder}`. Hindsight holds a companion onboarding
summary of the paper ("Stream Ring Theory — practical onboarding summary").

---

## 1. The theorem bindings

Distribution is a theorem, not a feature. The implementation sites:

| theorem (Stream Ring Theory) | statement | implementation site |
|---|---|---|
| Thm 5 | streams are atemporal: no required order of apply/merge/bulk | the Network wire — remote placement adds latency and interleaving, which the semantics never depended on |
| Thm 6 | coefficients float through non-reduce chains: $ca \cdot 1b = 1a \cdot 1b \cdot c1$ | the `floatable` process law (cat LawTable) |
| Thm 16 | reducers are temporal: all prior applies/merges complete first | `processor.barrier[?syncq=k]` — the one synchronization point |
| Thm 17 | floating stops at a reducer: $c1 \cdot 1a \neq 1a \cdot c1,\ a \in \mathcal{F}_r$ | the `blocked` process law — the shard cut |
| Thm 19 | monoidic reducers are semi-right-distributive: $(a+b)\,c = (ac+bc)\,c$ | `law_shard` itself, read left-to-right |

Admission requires exactly three declared facts: the operand is a **bag** (`lst` order is semantic,
so lists never shard — the same fact that denies `commutative` a canonicalization rule), the prefix
**floats**, the anchor is a **declared commutative monoid** $\langle X, \oplus, e \rangle$
(associativity recombines shards, commutativity frees arrival order, identity covers empty shards).
A reducer failing the monoid test may still shard via a declared **homomorphic decomposition**
($\mathrm{avg} \leadsto (\mathrm{sum}, \mathrm{count}) \cdot \mathrm{div}$) — a `derivation`-style
entry to add.

## 2. The abstract rule: a regular expression over the category

A rewrite rule generalizes from inst-specific (`[start, {*}plus(int::T), sum]`) to categorical
(`[initial, {*}floatable, blocked-reducer]`) by making each pattern slot a predicate over the
`morphism::T` lift (`form`, `class`, `law`, endpoints) instead of a tid. The slot sequence with
Kleene multiplicities is a regular expression whose alphabet is morphism classifications.

Key reduction: **the Kleene star need not be matched — it is computed.** The shard cut is the
float walk: push the shard coefficient $\{c\}$ rightward while each morphism carries `floatable`;
the first `blocked` morphism is the anchor. The laws are executed, not pattern-matched.

The combiner is never guessed: the anchor's op resolves against the operand object's declared
theory roles (`int::T.as(object::T)>>law>>add_monoid` = `[op=>!*plus, id=>0]`) through
`LawTable.declared(op, operand)` — pre-resolution safe, answers nothing rather than guessing.

## 3. Patterns are code: the mtron surface

No pattern language is needed — the language is the pattern language:

```
|start(A).commutative{*}?A<=A(A).reducer?A<=A{*}()
```

- **multiplicity is the coefficient**: `{*}` Kleene, `{2,}` two-or-more. `RewriterBuilder`'s chain
  form (`match(pair(inst, min))`, `rewriteChain`) is this semantics in Java already; the mtron
  surface compiles onto the existing engine, once, at rule registration (the hot path stays
  `instsMatch`).
- **form is structural**: `?A<=A{*}` (dom coefficient `{*}`) *is* `reducer`/`isGather`; `?A<=A` is
  the endo mapper shape. Only the **law** (`commutative`, `floatable`) needs a name, resolved in
  cat's own vocabulary.
- **generics are the unification**: `A` binds on first occurrence, checks thereafter.
- `|` keeps the pattern as shipped code — a pattern evaluated would be a program.

This belongs to **catInstSet**, not a new instset: cat already owns the two lifts
(`type -> object::T`, `inst -> morphism::T`); this adds the third, **`code -> walk`** — a pattern
is a constrained walk in the category, and matching is checking the chain's image under the lift
satisfies the walk. New cat vocabulary: `match(|walk)` (answer bindings or noobj), `subst`
(fill template holes), `float({c})`/`cut()` (the Thm 6/17 walk), and `rule::T`.

## 4. `rule::T` — the third block, with witnesses

```
rule::[match  => |start(A).commutative{*}?A<=A(A).reducer?A<=A{*}(),
       master => |barrier?syncq={{k}}().{{3}}(),
       worker => |{{shard}}.{{2}}.{{3}}?syncq={{k}}()]
```

- A rule is a **2-cell**: match-walk and template share endpoints (`src`/`trgt` objects), so a
  rewrite is an endpoint-preserving deformation; `fixpoint_rewriter` is normalization along
  2-cells. HoTT reading: morphisms are paths, rules are paths-between-paths.
- **Construction is validation** (the same move as construction-is-connection): the `rule::T`
  constructor refuses a distribute-rule whose anchor slot does not require `monoidic` (or a
  homomorphic decomposition), and refuses length-increasing algebra rules (the Direction
  discipline moves from documentation into the type).
- A rule **carries its witness**: lift `morphism::T.derivation` to `rule::T` — `law_shard`'s
  derivation is Thm 19's equality. Rules ship with their proofs.
- Rules are objs: storable in a space, importable as vocabulary, shippable over the Network —
  the rules that produce distributed code distribute by the same machinery.
- `{{k}}` (the syncq key) is minted **per application**, never per rule — concurrent folds must
  not share a mailbox. Template holes use the existing template-expansion arm in `computeArgs`.

## 5. The Java fluent (compiles to the same engine)

```java
DistributedRewriteBuilder.build()
    .tid(MACH_ISA_REWRITE_TID.extend("shard_fold"))
    .match(InstPattern.form(INITIAL),
           InstPattern.form(MAPPER).law(FLOATABLE).star(),
           InstPattern.form(REDUCER).monoidic())
    .distribute(Distribution.build()
        .master(barrier(SYNCQ), slot(REDUCER))
        .worker(slot(SHARD), slot(MAPS), sendq(REDUCER))
        .create())
    .create()
```

- `InstPattern` adds one arm to `instsMatch`: a slot that consults the lift instead of the tid.
  Concrete rules are the same mechanism with tighter predicates (the refinement-lattice relation:
  generalize by deleting constraints).
- `.network(...)` binds at **apply time**, never build time (rules are built in static instset
  registration; the network is per-machine, per-moment). No peers standing => the rule is the
  identity. Shard count at apply: $\min(|\mathrm{peers}|, c)$.
- Workers address the master by `?clientq` name; worker placement is function-ring **addition** —
  the sharded form $\sum_i \mathrm{shard}_i \cdot F^{*} \cdot r$ is an ordinary stream-ring
  expression whose summands live on peers, so correctness is ring algebra, not distributed-systems
  reasoning.

## 6. `generic_path_binder::T`

Pattern generics are **rule-scoped, not inst-scoped**: in `A.A.A.A` the first occurrence binds,
later occurrences check. The current `generic_binder::T` is inst-scoped; the new binder threads one
binding environment through the walk. Placement: built as a component the cat `match` inst
*borrows* (the matcher runs at the rewrite stage, pre-resolution), and wireable as a compiler
binder (`scoring_resolver::[binder=>generic_path_binder::[=>]]`) — one class, two call sites.
Perf note: `generic-binding` is measured at ~87% of resolve time; walk-scoped binding amortizes
(bind once, check O(1) thereafter) and must stay compiled-once-per-rule.

## 7. Observability, already built

- `.path()` — compilation and evaluation as a **path** (the paper's §V.G object thread): vertices
  are code states, edges are stages/rules. Addition when `rule::T` lands: each hop carries the tid
  of the rule that produced it — the 2-cell audit trail. A sharded fold's provenance is a path
  *bundle* reconverging at the barrier hop.
- `.profile()>>format` — per-rule insts.in/out and time; `law_shard` will be born instrumented,
  and the `flow` line (monads, coeff-sum, compression, processors) reports the distribution factor.
- Perf flag from tonight's profile: `theory_unit_removal` is ~90% of rewrite time (likely a
  per-operand theory scan); index `(type -> (op,id) pairs)` at theory registration before rule
  authorship opens to users.

## 8. Build order

1. **`processor.barrier[?syncq=k]`** — workers' halted monads drain into the master's keyed
   barrier (Thm 16 given a mailbox; mind the registry's concurrency).
2. **`law_shard`** as cat's first `rule::T` — Thm 19 + Thm 5, authored in mtron, validated at
   construction, witnessed by its derivation.
3. Then: `homomorphic` decomposition entries; the filter total-preorder as a cost-reordering rule
   family; `generic_path_binder` perf pass; the frame-reach read-through question settles itself
   in whichever direction the first working fold demands (workers' own dials vs granted reach).
