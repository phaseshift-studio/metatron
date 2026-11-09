# Theories beyond one carrier — multi-sorted and diagrammatic structure in `?decq`

**Status:** design note, written 2026-11-09 on main `ad1a7f2a9`. Nothing here is built unless it is marked
**observed** (a test row, a probe, or code read on that commit); everything else is **proposed**.
**Grew out of:** `docs/design/fibered_rec_review.md`, and Marko's question: the theories `?decq` declares are
single-element (one carrier), so what do multi-element, graphical structures look like — their abstract forms, and the
rules and rewrites that constraints on their topology would license?
**See also:** Hindsight — *Design note: rec select (==), update (>>=) and merge (+) as keyed joins*, *Findings: select
(==) and update (>>=) on fibered recs*, *Algebraic laws on object::T for base types*, *ring axiom rewrites in
catRewrite*.

---

## 0. Thesis

`?decq` declares single-sorted equational theories: one carrier, operations on it, and equations written as rewrite
rules. A rewriter needs more than that. It rewrites *routes* — chains of instructions that cross several types — and the
laws it needs say which routes through a graph of types mean the same thing.

The general form of such a theory is a **sketch** (Ehresmann; Barr & Wells): a graph of sorts and arrows, equations
between paths (commuting diagrams), and declared limits and colimits (cones and cocones). metatron already has the
graph — `object::T`, `morphism::T` and `path::T` in `catTypes` — so the step is to let `?decq` declare path equations
and universal properties, and let `catRewrite` read them.

---

## 1. The ladder

| rung | what a theory can state | example | in metatron |
|---|---|---|---|
| Lawvere theory | one sort, total operations, equations | group, ring, lattice | `?decq` today *(observed)* |
| multi-sorted theory | several sorts, operations between them | a monoid acting on a set; a lens; a graph | `Law.action` names the first, as a flag *(observed)* |
| essentially algebraic theory (GAT) | partial operations, defined on a pullback | a category: `f.g` only when `rng(f) = dom(g)` | the resolver enforces it; nothing declares it |
| limit/colimit sketch | "this node is the pullback / the sum of …" | joins, unions, quotients; a field (`x = 0` or `x` invertible) | `div(0)` fails *(observed)*; no declaration says why |
| 2-dimensional | functors, naturality, adjunctions, monads | the lift; `>>age` as a natural projection | the lift, implicitly *(observed)* |
| higher (HoTT) | paths between paths, equivalences, quotient types | rewrites as 2-cells; `as` round trips | `path::T` relates code to its rewritten forms *(observed)* |

Each rung keeps every rewrite of the rungs below it and adds rewrites of its own.

---

## 2. What metatron has today *(observed)*

- **`?decq`** answers what is declared about a type or inst — "like `?docq`, but metadata about the algebraic
  structure" (Marko). A theory is a type whose fields name its roles and whose rewrites are pairs of paths:

  ```
  mtron> *group_theory
  ==>theory::T[?[op=>inst::T, id=><#>::T, inv=>inst::T,
                 law=>[monoidic,unit,involution],
                 rewrite=>[[op(a::T).op(inv(a::T))=>],
                           [op(inv(a::T)).op(a::T)=>]]]]@/m/theory/group_theory
  mtron> *int?decq
  ==>decs::[obj=>int::T,
            theory=>[ring=>ring_theory::[add=>plus…, mul=>mult…, zero=>0, one=>1],
                     add_group=>group_theory::[op=>plus…, id=>0, inv=>neg…],
                     add_monoid=>monoid_theory::[op=>plus…, id=>0],
                     mul_monoid=>monoid_theory::[op=>mult…, id=>1]]]
  ```
- **Ten single-sorted theories** registered in `mInstSet`: monoid, commutative monoid, group, ring, rig, near-ring,
  field, boolean, lattice, semilattice.
- **Process laws** on instructions (`Law`): `monoidic`, `commutative`, `idempotent`, `involution`, `unit`, `pure`,
  `floatable`, `blocked`, `kleene`, `action` ("a monoid/group acting on a carrier"), `semi_right_distributive`, and
  more.
- **The category graph** in `catTypes`: `object::T` (a type), `morphism::T` (an inst from its dom to its rng),
  `path::T` ("code as a categorical path incident to iso-semantic rewritten forms"), `abstraction::T`, `place::T`.
- **`catRewrite`**: `branch_collapse`, `branch_inline`, `law_absorbing`, `law_poset`, `law_shard`, plus the theory
  rewrites read from `?decq`.

**The stream ring is already two-dimensional.** In a category with biproducts, the sum of two morphisms is
f + g = ∇ ∘ (f ⊕ g) ∘ Δ: copy the input, run both, merge. That is `-<[f,g]>-`: `m_math_cat_Test` rewrites
`5-<[plus(1).plus(1), plus(1).plus(2)]>-` to `5.plus(1)-<[plus(1),plus(2)]>-` and answers `{7,8}`. So:

- the stream ring's `+` is biproduct addition;
- Thm 18 — `⟨F_r, +, ·⟩` is a near-ring, left distributive only — says reducers are not *additive* maps
  ((a+b)·c ≠ a·c + b·c for a reducer c), while mappers and filters are;
- `-<` is the diagonal Δ, and sums and products are its two adjoints, (+) ⊣ Δ ⊣ (×).

---

## 3. Worked model: the fibered rec

The fibered rec is the first multi-sorted structure metatron has: a base (the fields that are no fibers) with fibers
over it. Pinned on 2026-11-09 *(observed, `FiberedRecTest`, 73 rows)*:

- `>>=` applies a rec of instructions key by key and keeps the bundle. Two fibers of one type can take two
  instructions (`pair[int,int]::[…] >>= [a=>plus(1),b=>mult(2)]`), which a single call's lift cannot.
- The rule recs act on the bundle as a **monoid**: `[=>]` is the identity, two updates equal one update with both
  rules, rules on disjoint keys commute, rules on one key do not, and a literal is idempotent with the later one
  winning.
- A type-changing keyed update fails the bundle's type (`>>= [age=>gt(18)]`), while `==` answers a plain rec typed by
  its pattern: a select projects, an update lifts.

What follows *(proposed)*:

- **Laws carry over field by field.** An equation holds in a product exactly when it holds in every factor
  (Birkhoff: varieties are closed under products). A bundle's theory is the **meet** of its fibers' theories, so it can
  be derived instead of declared. `pair[int,int]?decq` would answer an `add_group` whose `op` and `inv` are `plus` and
  `neg` over each fiber; `tagged[int,str]` would keep only the roles both of its fibers declare.
- **The support grades the theory.** For the keys S a rule names, Th(S) = ⋂_{k∈S} Th(k), which shrinks as S grows. In
  `tagged[int,str]`, rules on `{hits}` form a group, rules on `{hits,label}` only a monoid. A rewriter can use the laws
  of a rule's own keys rather than the weakest laws of the whole bundle.
- **Group bundles.** A bundle whose fibers carry an algebra operates fiber by fiber over one base point. `money`
  amounts add only when the currencies agree (USD + EUR is undefined), and the identity is a **zero section** — zero
  in whichever currency the base names — not a constant.
- **Elements act on themselves.** By Cayley's theorem, `[a=>plus(1),b=>plus(2)]` is the element `[a=>1,b=>2]` of
  `add_group` acting by translation, so `group_theory`'s rewrites carry over key by key:
  `>>=[a=>plus(x)] >>=[a=>plus(neg(x))]` ⇝ `>>=[=>]`, which the unit law then removes.
- **Concurrency.** Rules on disjoint keys commute, so the rule monoid is a **trace monoid** (Cartier–Foata;
  Mazurkiewicz). Its Foata normal form packs a chain of updates into the fewest parallel steps.
- **Coordination.** A rule's laws tell a peer what delivery it needs:

  | the rule's components are | example | delivery needed |
  |---|---|---|
  | associative, commutative, idempotent | `max`, `or` | any order, any number of times (a CRDT; the CALM theorem) |
  | commutative, not idempotent | `plus(1)` | any order, exactly once |
  | idempotent, not commutative | a literal | duplicates are harmless, order matters (last writer wins) |
  | neither | `plus('!')` on a str | a total order (a log) |

- **Cross-talk** (Marko, 2026-11-09). An instruction typed on the fiber tuple — for `person[age,name,gender]`,
  `inst?lst[int,str,str]<=lst[int,str,str](){ … >>0 … >>1 … >>2 … }` — reads every fiber by projection out of the
  incoming lst and answers all three. Any map from a product into a product is a pairing ⟨p,q,r⟩ of maps that each read
  every fiber; the rule rec is the special case p = f∘π₀, q = g∘π₁, r = h∘π₂.

  The instance bracket already parses as that lst: `Type.recFibers()` reads `vid().polyParsed()` as a lst of types. So
  the dispatch would be: the call's domain is the lst of the fiber types → gather the fibers in bracket order → apply
  once → scatter the answer's slots back, with the existing per-slot test (same type, same multiplicity) deciding lift
  or projection. Today such a call reaches no fiber (read from the code, not run).

  | form | in the matrix picture | sees | fibers in parallel | laws |
  |---|---|---|---|---|
  | `bundle.plus(1)` | one instruction per fiber type | its own fiber | yes | the fiber's |
  | `>>= [a=>f,b=>g]` | diagonal | its own key | yes | derived, field by field |
  | `inst?lst[…]<=lst[…]` | full, on the fibers | every fiber | no: it waits for all | declared on the instruction |
  | `inst?person<=person` | full, on the whole rec | fibers and base | no | declared on the instruction |

  The tuple form is structural — any bundle whose fibers are `[int,str,str]` takes it (person, robot, …) — and it
  cannot touch the base. It costs a dependence on bracket order, a barrier, and derived laws. Between the diagonal and
  the full matrix lies the triangular case, where a fiber reads only the fibers before it: a cascade. Krohn–Rhodes
  theory decomposes any finite automaton into cascades of simple groups and resets.

---

## 4. Eight abstractions worth making explicit *(proposed)*

### 4.1 Naturality — free theorems

An instruction generic in its element type commutes with any map; parametricity gives the law, so nothing has to be
declared (Wadler, *Theorems for free!*).

- `{1,2,3}.plus(1).count()` ⇝ `{1,2,3}.count()` — counting never looks at the elements.
- `x.plus(1).take(2)` ⇝ `x.take(2).plus(1)` — the map runs twice instead of on everything.
- `bundle.plus(1)>>age` ⇝ `bundle>>age.plus(1)` — `>>age` is natural in the fiber, so no bundle is rebuilt; and
  `bundle.plus(1)>>name` ⇝ `bundle>>name`, because a lift never touches the base.

`Law.floatable` is the hand-declared cousin. The precondition is that the body never inspects the element type.

### 4.2 Lenses

Two sorts (source, view), two operations (`get`, `put`), three laws (GetPut, PutGet, PutPut). Every address is a lens.

- Read your write: after `x -> 5`, `*x` ⇝ `5`, with no trip to the space — what matters for remote spaces.
- Write coalescing: two writes to one address ⇝ the last. A `?subq` listener then sees one event, so this is a choice
  for `@`.
- A write of what was just read vanishes.

Lenses compose, so a nested path (`@a/b/c`) inherits the laws. AGENTS.md's "the round trip must be a no-op" is GetPut;
the database form is Bancilhon & Spyratos's constant complement.

### 4.3 Actions

Two sorts — a monoid M and a carrier X — with act(m·n, x) = act(m, act(n, x)) and act(e, x) = x.

- Update fusion: `x >>= F >>= G` ⇝ `x >>= F;G`, one rebuild and one write.
- `x >>= [=>]` ⇝ `x`.
- Updates on disjoint keys reorder freely.

Promote `Law.action` from a flag to a two-sorted theory; `>>=` on bundles is its first model (§3).

### 4.4 Fibrations

Two sorts (total, base), a projection p, and p ∘ f = p for every vertical map f — the law behind "fields that are no
fiber ride along".

- Base reads hoist past lifts (§4.1).
- Moving the base point is **reindexing** — converting `money` from USD to EUR is a map between fibers along an
  exchange-rate arrow — and reindexing composes ((u∘v)* ≅ v*∘u*), so two conversions fuse into one.

### 4.5 Limits and colimits as declared shapes

A product is a rec, a coproduct a union, an equalizer `is(f == g)` — these exist. A **pullback** is a join on a shared
key (`person → company ← office`); a **pushout** glues two structures along a shared part (`+` is the variant that
keeps conflicts as multiplicities). Declaring a type as the pullback of A → C ← B makes joins first-class:

- joins reassociate: (A ×_C B) ×_D E ≅ A ×_C (B ×_D E);
- a filter on one leg commutes with the pullback;
- the join pushes down into tble, dcmnt and grph spaces.

CQL is a worked system: joins are pullbacks, and data migration is three adjoint functors, Σ ⊣ Δ ⊣ Π.

### 4.6 Graph rewriting — the double pushout

A rule is a span L ← K → R (match L, keep K, produce R) applied by two pushouts. Its side conditions are topological —
no dangling edges, no conflicting identifications — and critical-pair analysis says whether two rules commute. This
fits grph spaces and code as a graph. AlgebraicRewriting.jl does it on C-sets (functors from a schema category to
Set), which is what a space of typed, linked recs is.

### 4.7 Relations — allegories and Kleene algebra with tests

Relations (`rel::T`, traversals) compose and have converses and intersections (Freyd & Ščedrov).

- `(R;S)° = S°;R°`: reverse a traversal and start from the smaller end.
- The modular law `R;S ∩ T ⊆ R;(S ∩ R°;T)`: push a filter into a path.
- `Law.kleene` exists; with tests (KAT, Kozen), `repeat().until()` is a guarded Kleene star, and KAT equivalence is
  decidable.

Gremlin's traversal strategies, derived from declared laws instead of written by hand.

### 4.8 HoTT, lightly

- **Equivalences and transport.** Declare the `as` round trips that are equivalences (`x.as(B).as(A) = x`, both ways).
  Every theory and rewrite on A then transports to B, and computation can move to whichever representation is cheaper
  or native to a space. (The as-graph — Hindsight's *as-graph encoded in space* initiative, not in the code today — is
  where such edges would live.)
- **Rewriting as homotopy.** `path::T` relates code to its iso-semantic rewritten forms: paths between paths.
  Confluence says two rewrite paths from one term close into a square; **Knuth–Bendix completion** turns equations
  into a confluent, terminating rewrite system mechanically — the three group axioms complete to the classic ten
  rules. Deriving the stream-ring theorems mechanically is a pending task of the algebraic-laws initiative.
- **Quotients and adjunctions.** List → bag → set are the free monoid, the free commutative monoid and the free
  semilattice, each left adjoint to forgetting the structure. A reducer is an algebra for that monad, and its laws say
  which quotient the stream upstream of it may live in: `max` (associative, commutative, idempotent) may reorder,
  deduplicate and distribute; `count` (associative, commutative) may reorder and distribute but never deduplicate; a
  fold with a non-commutative operation keeps the order.

---

## 5. Encoding in `?decq` *(proposed; syntax not run)*

The same shape as `group_theory`, with more than one sort and rewrites between paths:

```
lens_theory::T[?[source=>type::T, view=>type::T,
                 get=>inst::T, put=>inst::T,
                 law=>[get_put, put_get, put_put],
                 rewrite=>[
                  [get().put(_)          =>],              [-- GetPut: a no-op write --]
                  [put(v::T).get()       => v::T],          [-- PutGet: read your write --]
                  [put(v::T).put(w::T)   => put(w::T)]]]]   [-- PutPut: last write wins --]

action_theory::T[?[monoid=>monoid_theory::T, carrier=>type::T, act=>inst::T,
                   law=>[action],
                   rewrite=>[[act(m::T).act(n::T) => act(m::T.op(n::T))]]]]
```

A commuting square is a rule with two non-empty sides; the rewriter orients it by cost. A declared limit would be a
role naming its legs — for a pullback, the two arrows and their common target — which joins and push-down read.

---

## 6. Where to start

1. **Naturality for generic instructions.** No declarations; `count`/`take` push-down and the fiber-projection
   rewrite. Pin each as a before ⇒ after row in `catInstSetTest`.
2. **`action_theory` and `lens_theory`.** Two small two-sorted theories: update fusion, read-your-write, write
   coalescing.
3. **Name the stream ring as biproduct addition.** Classify instructions as additive or not; branch networks then get
   one normal form instead of a rule per shape.
4. **Derived `?decq` for bundles.** The meet of the fibers' theories, graded by the keys a rule names.
5. **Pullbacks as declared join shapes**, feeding push-down.
6. **Knuth–Bendix completion** over `?decq` theories.

---

## References

- Barr & Wells, *Toposes, Triples and Theories* (1985) and *Category Theory for Computing Science* (1990) — sketches.
- Birkhoff, *On the structure of abstract algebras* (1935) — varieties are closed under products (HSP).
- Cartmell, *Generalised algebraic theories and contextual categories* (1986).
- Adámek & Rosický, *Locally Presentable and Accessible Categories* (1994) — what limit and limit-colimit sketches
  express.
- Wadler, *Theorems for free!* (1989).
- Bancilhon & Spyratos, *Update semantics of relational views* (1981); Foster, Greenwald, Moore, Pierce & Schmitt,
  *Combinators for bidirectional tree transformations* (2005/2007) — lenses.
- Pickering, Gibbons & Wu, *Profunctor optics: modular data accessors* (2017).
- Spivak, *Functorial data migration* (2014); Schultz, Spivak, Vasilakopoulou & Wisnesky, *Algebraic databases*
  (2017) — CQL.
- Ehrig, Ehrig, Prange & Taentzer, *Fundamentals of Algebraic Graph Transformation* (2006); Brown, Patterson, Hanks &
  Fairbanks, *Computational category-theoretic rewriting* (2022) — AlgebraicRewriting.jl.
- Freyd & Ščedrov, *Categories, Allegories* (1990).
- Kozen, *Kleene algebra with tests* (1997).
- Knuth & Bendix, *Simple word problems in universal algebras* (1970).
- Cartier & Foata (1969); Mazurkiewicz (1977) — trace monoids.
- Shapiro, Preguiça, Baquero & Zawirski, *Conflict-free replicated data types* (2011); Hellerstein & Alvaro,
  *Keeping CALM* (2020).
- Krohn & Rhodes, *Algebraic theory of machines* (1965).
- The Univalent Foundations Program, *Homotopy Type Theory* (2013).
- Spivak & Niu, *Polynomial Functors: A Mathematical Theory of Interaction*.
