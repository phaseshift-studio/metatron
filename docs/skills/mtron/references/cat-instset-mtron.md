---
name: cat-instset
description: |
  The category graph at `/m/math/cat`: every type is an **object** (a vertex) and every inst is a **morphism**
  (an edge). Lift a type with `.as(object::T)` to read the morphisms incident to it (`morphed_to` out-edges,
  `morphed_from` in-edges); lift an inst with `.as(morphism::T)` to read its endpoints (`src` dom, `trgt` rng)
  and its whole-graph analysis (`family`, `contested`, `orbit`, `position`, `inverse`).
  TRIGGER: When reading a type's incident morphisms or a morphism's endpoints, when reasoning about a morphism's
  siblings / inverse / connected component, when classifying an edge's coefficient shape (`form`), set-theoretic
  class (`class`), or declared process laws (`law`), when wiring the algebraic theories (`ring` / `field` /
  `rig` / `group` / `monoid` / `boolean` / `lattice`), or when adding a law-driven rewrite — the category *is*
  the rewrite table.
---

# the category graph (`/m/math/cat`)

`/m/math/cat` realizes types and insts as a **category**: types are the objects (the vertices), insts are the
morphisms (the edges), and an inst's dom/rng are its source and target endpoints.

```mtron_pre
[NO_OUTPUT] import(/m/math/cat)
```

The lift is a type constructor. An _object_ is constructed from a type and a _morphism_ from an inst. The cat-graph is
dynamically generated, not apriori defined.

<!-- figure source: assets/tikz/cat-lift.tex — white ink on transparent (the site theme is dark); edit there, re-render with the tikz MCP server, drop the PNG in assets/ -->
<img src="../assets/cat-lift.png" alt="the lift: a type is a vertex, an inst is an edge" width="40%" style="float:right; margin:0 0 0.9em 1.5em"/>

Every morphism is classified on three axes — `form` (its n-tid coefficient shape), `position` (its place in the
graph), and `law` (the algebraic laws it obeys) — and every object carries the structural theories it models. Those
same declarations are what the rewriter stage consumes (see *the declared rewrites*). A vertex is a type lifted to
`object::T` (`obj`, `law`, `morphed_to`, `morphed_from`); an edge is an inst lifted to `morphism::T`
(`src` = dom, `trgt` = rng, `form`, `class`, `law`, `derivation`, `analysis`).

**IMPORTANT**: when referencing an instruction directly in a `console::T`, a `block` inst (sugar'd `|`) prevents the
instruction from being evaluated.

```mtron_pre
int::T.as(object::T)
|plus?int<=int(int::T).as(morphism::T)
```

## the two blocks

### the object block — `object::T` (a vertex)

| field          | what it is                                                      |
|----------------|-----------------------------------------------------------------|
| `obj`          | the type this object wraps (the vertex itself)                  |
| `morphed_to`   | the morphisms **sourced from** this object (the outgoing edges) |
| `morphed_from` | the morphisms **targeted at** this object (the incoming edges)  |
| `law`          | the structural theories this object models (see *theories*)     |

```mtron_pre
[MAXOUTPUT 20] int::T.as(object::T).morphed_to()
```

### the morphism block — `morphism::T` (an edge)

| field        | what it is                                                                                               |
|--------------|----------------------------------------------------------------------------------------------------------|
| `name`       | the operation's name                                                                                     |
| `obj`        | the inst this morphism wraps (the edge itself)                                                           |
| `form`       | the n-tid coefficient shape (`mapper`, `filter`, `reducer`, `flatmapper`, …)                             |
| `class`      | the set-theoretic class (`endo`, `iso`, `auto`, `mono`, `epi`, `section`, `retraction`)                  |
| `src`        | the source object (the morphism's dom)                                                                   |
| `trgt`       | the target object (the morphism's rng)                                                                   |
| `derivation` | the declared defining equation, `lhs ↦ rhs`, when the operation is derived (see *the declared rewrites*) |
| `law`        | the declared **process** laws the morphism obeys (see *process laws*)                                    |
| `analysis`   | a lazy sub-block of whole-graph stats — see below                                                        |

## incidence — the dual

`src`/`trgt` and `morphed_to`/`morphed_from` are the same relation seen from opposite sides:

| from       | dom side (out) | rng side (in)  |
|------------|----------------|----------------|
| an object  | `morphed_to`   | `morphed_from` |
| a morphism | `src`          | `trgt`         |

`morphed_to(A)` is $\{ f \mid \mathrm{src} (f) = A \}$; `morphed_from(A)` is $\{ f \mid \mathrm{trgt} (f) = A \}$.
Composing them round-trips — from an object to its edges and back, or from an edge to its endpoints and back.

## the `analysis` sub-block

`analysis` is one lazy `auto()` — it stays a single `{<j>}` marker at level 1 until you `>>analysis`, so the
morphism block reads small. Inside it, each stat is itself lazy:

| field       | what it is                                                                                                       |
|-------------|------------------------------------------------------------------------------------------------------------------|
| `family`    | the same-name siblings: $\{ g \mid \mathrm{op}(g) = \mathrm{op}(f) \}$ — every registration of the same operator |
| `contested` | the witness edges behind the `ambiguous` / `incomparable` labels — the competing siblings                        |
| `orbit`     | the reversible-core component of the dom — everything reachable by a chain of opposing edges                     |
| `position`  | the edge's place among its siblings (a subset of the six kinds below)                                            |
| `inverse`   | the opposing edge $f^{-1}$ with $f \cdot f^{-1} = 1$ (only when a declared inverse exists)                       |

## the six edge kinds

`position` classifies an edge against the rest of the graph into up to six labels:

<!-- figure source: assets/tikz/cat-positions.tex (same recipe) -->
<img src="../assets/cat-positions.png" alt="the six position kinds: duplicate, ambiguous, incomparable, coupling, isochain, retract" width="100%" style="margin:0.3em 0 0 0"/>

| kind           | the relation to the graph                                                        |
|----------------|----------------------------------------------------------------------------------|
| `duplicate`    | another morphism of the same family maps the same dom to the same rng            |
| `ambiguous`    | contested by an incomparable sibling that *overlaps* it — dispatch has no winner |
| `incomparable` | contested by an incomparable, *disjoint* sibling — dispatch stays total          |
| `coupling`     | an opposing edge exists — a candidate inverse pair                               |
| `isochain`     | endpoints linked by a chain of couplings — one orbit of the reversible core      |
| `retract`      | mutually reachable and round-trip idempotent — a subobject retraction            |

`orbit` is the transitive closure of `coupling` — the connected component of the *reversible core* (the subgraph of
edges that have an opposing edge). It is a reachability class, coarser than isomorphism (which needs the round trip
to be idempotent → `retract`) and finer than raw incidence.

## the class axis

`class` classifies a morphism set-theoretically — whether it is self-mapping, invertible, or split. Only the sound
implications are computed; a proper (non-split) mono/epi awaits composition, which is not wired yet:

| class        | what it asserts                                                                           |
|--------------|-------------------------------------------------------------------------------------------|
| `endo`       | self-mapping — $\mathrm{dom}(f) = \mathrm{rng}(f)$                                        |
| `auto`       | an invertible endomorphism — `endo` and `iso`                                             |
| `iso`        | invertible — currently a self-inverse (involution)                                        |
| `mono`       | left-cancellable — $f \cdot g = f \cdot h \Rightarrow g = h$ (here: a split monomorphism) |
| `epi`        | right-cancellable — $g \cdot f = h \cdot f \Rightarrow g = h$ (here: a split epimorphism) |
| `section`    | split mono — $f$ has a left inverse                                                       |
| `retraction` | split epi — $f$ has a right inverse                                                       |

## the algebraic theories — laws of structure

An object can model several algebraic structures; `object::T>>law` is a rec keyed by the name you give each
instance, and each role holds a `!*` (`auto_from`) pointer to the *live* operation rather than a copy — the graph is
the references, and a role dereferences transparently on `>>` (`...>>law>>ring>>add` yields the inst). `theory::T`
is the nominal super-type grouping them:

| type                 | roles (fields)                     | what it asserts                                                                                                                                 |
|----------------------|------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------|
| `ring_theory`        | `add`, `mul`, `zero`, `one`        | $\langle R, +, \cdot, 0, 1 \rangle$: $+$ an abelian group, $\cdot$ a monoid, $a \cdot (b + c) = a \cdot b + a \cdot c$                          |
| `field_theory`       | `add`, `mul`, `zero`, `one`, `inv` | $\langle F, +, \cdot, 0, 1, {}^{-1} \rangle$: a commutative ring where every non-$0$ element has a multiplicative inverse, $x \cdot x^{-1} = 1$ |
| `rig_theory`         | `add`, `mul`, `zero`, `one`        | $\langle S, +, \cdot, 0, 1 \rangle$: additive and multiplicative monoids, $\cdot$ distributive over $+$, no additive inverses                   |
| `group_theory`       | `op`, `id`, `inv`                  | $\langle G, \cdot, 1, {}^{-1} \rangle$: $g \cdot g^{-1} = g^{-1} \cdot g = 1$                                                                   |
| `monoid_theory`      | `op`, `id`                         | $\langle M, \cdot, 1 \rangle$: $m \cdot 1 = 1 \cdot m = m$ and $(m \cdot n) \cdot p = m \cdot (n \cdot p)$                                      |
| `boolean_theory`     | `or`, `and`, `not`, `zero`, `one`  | $\langle B, \lor, \land, \lnot, 0, 1 \rangle$: a complemented distributive lattice, $b \lor \lnot b = 1$ and $b \land \lnot b = 0$              |
| `lattice_theory`     | `meet`, `join`, `bottom`, `top`    | $\langle L, \sqcap, \sqcup, \bot, \top \rangle$: every pair of elements has a meet and a join, bounded by $\bot$ and $\top$                     |
| `semilattice_theory` | `op`, `id`                         | $\langle S, \sqcup, 0 \rangle$: an idempotent commutative monoid (one side of a lattice)                                                        |
| `near_ring_theory`   | `add`, `mul`, `zero`               | $\langle N, +, \cdot, 0 \rangle$: $+$ a group, $\cdot$ a monoid, one-sided distributivity, no multiplicative $1$                                |

## process laws — `morphism::T>>law`

The morphism's `law` field is a set of **process** law labels served from `mInstSetLawTable` (the declared process
laws):

```mtron_pre
|plus?int<=int(int::T).as(morphism::T)>>law
```

| law                  | what it asserts                                                              |
|----------------------|------------------------------------------------------------------------------|
| `commutative`        | $a \cdot b = b \cdot a$                                                      |
| `left_distributive`  | $a \cdot (b + c) = a \cdot b + a \cdot c$                                    |
| `right_distributive` | $(a + b) \cdot c = a \cdot c + b \cdot c$                                    |
| `monoidic`           | an associative binary op with an identity                                    |
| `magmadic`           | a non-associative binary op with an identity                                 |
| `unit`               | an identity element — $e \cdot x = x \cdot e = x$                            |
| `absorbing`          | an annihilator — $a \cdot x = a$ for every $x$                               |
| `action`             | a monoid/group acting on a carrier — $f(gh)x = f(g)f(h)x$                    |
| `involution`         | self-inverse — $f(f(x)) = x$ (period two)                                    |
| `idempotent`         | $f(f(x)) = f(x)$                                                             |
| `nilpotent`          | some power is zero — $f^n = 0$                                               |
| `floatable`          | the coefficient commutes through the stream ring's parallel/serial structure |
| `blocked`            | cannot float past a barrier — a reduce anchors the coefficient               |
| `partial`            | pass-through-or-drop $\{0,1\}$ — the optional (maybe) lift of a value        |
| `kleene`             | the reflexive-transitive iterate $a^{*}$ — a Kleene closure                  |
| `prime`              | atomic — no nontrivial factorization                                         |
| `zero_divisor`       | a nonzero $a$ with $a \cdot b = 0$ for some nonzero $b$                      |
| `cyclic`             | generated by one element — every value is a power of a single generator      |
| `poset`              | the carrier is partially ordered — these insts are its comparisons           |

A law is either **structural** (one of the object theories above) or **process** (these morphism labels). The former
provenance axis (syntactic / declared / semantic) is dropped: the table serves the declared process laws, and the
rest are derived from the n-tid or computed on demand.

## the declared rewrites — laws as rules

Everything above is also the rewriter's input. A rule fires only when the operand's type **declares** the law it
consumes, so the category *is* the rewrite table: an algebra registered on a type immediately licenses its rules, and
a type that models no such structure is left alone.

<!-- figure source: assets/tikz/cat-rewrites.tex (same recipe) -->
<img src="../assets/cat-rewrites.png" alt="declarations funnel through the law table into the rewrite families" width="100%" style="margin:0.3em 0 0 0"/>

| rewrite                  | reads                                                              | rule                                                                                                                 |
|--------------------------|--------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------|
| `theory_unit_removal`    | every theory instance of the operand's type                        | $\mathrm{op}(\mathrm{id}) \leadsto \varepsilon$                                                                      |
| `theory_involution`      | $\mathrm{law} \ni \mathrm{involution}$, or the declared `inv` role | $f \cdot f \leadsto \varepsilon$                                                                                     |
| `law_idempotent`         | $\mathrm{law} \ni \mathrm{idempotent}$                             | $f \cdot f \leadsto f$                                                                                               |
| `law_absorbing`          | $\mathrm{law} \ni \mathrm{absorbing}$                              | $g \cdot f \leadsto f$                                                                                               |
| `law_monoidic`           | $\mathrm{law} \ni \mathrm{monoidic}$ + the `reducer`/`join` form   | $f \cdot f \leadsto f$                                                                                               |
| `law_poset`              | $\mathrm{law} \ni \mathrm{poset}$                                  | $\mathrm{gt}(a) \cdot \mathrm{gt}(b) \leadsto \mathrm{gt}(\max(a, b))$                                               |
| `inverse_cancellation`   | `analysis.inverse`                                                 | $f(a) \cdot f^{-1}(a) \leadsto \varepsilon$                                                                          |
| `form_map_unwrap`        | the stream lift `map`                                              | $\mathrm{map}(f) \leadsto f$                                                                                         |
| `derivation_contraction` | `derivation`                                                       | $\mathrm{plus} \cdot \mathrm{neg} \leadsto \mathrm{minus}$, $\mathrm{mult} \cdot \mathrm{inv} \leadsto \mathrm{div}$ |

**One declared law has no rule.** `commutative` is recorded on many insts, but canonicalizing a commutative
operand is a no-op where it would be legal: a stream is a *bag* (`{3,1,2}` already equals `{1,2,3}`), while a
`lst`'s order is semantic, so reordering one would be unsound. The law stays declared (it is what licenses the
inverse-pair search) without a rewrite of its own.

The first two generalise a *ring's* unit and a *group's* inverse to every declared structure: the unit law is read
for each `(op, id)` role pair a theory names (`add`/`zero`, `mul`/`one`, `op`/`id`, `or`/`zero`, `and`/`one`,
`join`/`bottom`, `meet`/`top`), so $x + 0$, $x \cdot 1$, $x \cdot \langle\rangle$ (uri), $x \cdot .$ (machine) and
$x \cdot \mathrm{id}$ (code) all drop, and a bare identity *operation* (`code`'s $\mathrm{id}$) is the identity
morphism. The involution law holds for every operation declared `involution` — `neg`, `inv`, `not`, `reverse`,
`conjugate` — not only the additive group's.

```mtron_pre
5.plus(0)
5.id()
5.neg().neg()
"ab".reverse().reverse()
```

The process laws act on the chain, each gated by the operand type's declaration:

```mtron_pre
"ab".ucase().ucase()
2.zero().zero()
{1,2,3}.sum().sum()
5.plus(3).zero()
"m".gt("a").gt("z")
```

and the two *relational* declarations — the opposing edge and the defining equation — close the loop:

```mtron_pre
5.plus(3).minus(3)
6.plus(3.neg())
6.0.mult(2.0.inv())
{1,2,3,4}.inst(_,+1,+2){ map(*0).plus(*1).plus(*2) }
```

**A worked compression.** One expression, four of the rules above firing, and the whole chain collapses from eight
operations to two. `explain()>>format` prints the *compiled* code — the op, its dom/rng, its arg and its coefficient
shape per line — so you can always see what a rewrite did:

```mtron_pre
6.plus(0).mult(1).neg().neg().plus(3.neg()).mult(2).div(2)
6.plus(0).mult(1).neg().neg().plus(3.neg()).mult(2).div(2).explain()>>format
```

Reading the two rows that survive: `plus(0)` and `mult(1)` are the int ring's units (`theory_unit_removal`),
`neg().neg()` is the additive group's involution (`theory_involution`), `plus(3.neg())` folds to `minus(3)`
(`derivation_contraction`), and `mult(2).div(2)` is a declared inverse pair (`inverse_cancellation`). What is left is
the seed and one operation.

**Direction.** Every rule above removes or preserves chain length, so a rule set that is only ever applied in that
direction settles under any length-stable `rewriter::T` — the shipped `fixpoint_rewriter` is one such stage, not the
only one. The dual of `derivation_contraction` — `derivation_expansion`
($\mathrm{minus} \leadsto \mathrm{plus} \cdot \mathrm{neg}$) — is deliberately **not** registered: it trades the
length back, so the pair would alternate forever and no stage could settle. Lowering to a target that lacks the
derived operation is a policy, not a law.

**Resolution.** The stage order is the compiler's to choose, and the default schedule rewrites *before* it resolves,
so a chain instruction still carries only its op address while the law tables are keyed by the full n-tid.
`LawTable.declared(op, operand)` bridges the two — it resolves the declared entry by address, disambiguated by the
operand's type, and returns nothing rather than guessing, so a law never fires for a type that does not declare it.
A schedule that resolves first (see `compiler::T`) lets the rewriter read the endpoints directly. `form_map_unwrap`
is the one rule kept keyed to its operation rather than to a shape, because $\{?\} \to \{?\}$ is also `is`'s
coefficient shape and a shape-only test would unwrap a predicate.

**Not yet wired** — the graph/relation family: `inverse`-pair elision ($x.\mathrm{as} (B).\mathrm{as} (A) \leadsto x$
when the round trip is a `coupling`), `as`-cast fusion through a refinement, and `section`/`retraction` cancellation.
It needs the whole cast subgraph (`check()`, `implicit()`) rather than one instruction, so it is a separate slice.

## see also

* [mtron type system](type-system-mtron.md) — vid/tid, coefficients, nominal vs structural, casting.
* [mtron language reference](language-reference-mtron.md) — `.as(type::T)`, `>>` rshift, coefficients.
* [math instruction set](math-instset-mtron.md) — `/m/math`, where the category and its theories live.
* [rewrite system](../metatron/references/rewrite-system-java.md) — the `rewriter::T` stage and rewrite instructions.
* figure sources — `assets/tikz/cat-*.tex` (TikZ, rendered with the tikz MCP server; the PNGs sit beside them in
  `assets/`).
