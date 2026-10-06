---
name: cat-instset
description: |
  The category graph at `/m/math/cat`: every type is an **object** (a vertex) and every inst is a **morphism**
  (an edge). Lift a type with `.as(object::T)` to read the morphisms incident to it (`morphed_to` out-edges,
  `morphed_from` in-edges); lift an inst with `.as(morphism::T)` to read its endpoints (`src` dom, `trgt` rng)
  and its whole-graph analysis (`family`, `contested`, `orbit`, `position`, `inverse`).
  TRIGGER: When reading a type's incident morphisms or a morphism's endpoints, when reasoning about a morphism's
  siblings / inverse / connected component, when classifying an edge's coefficient shape (`form`), set-theoretic
  class (`class`), or declared process laws (`law`), or when wiring the algebraic theories (`ring` / `field` /
  `rig` / `group` / `monoid` / `boolean` / `lattice`).
---

# the category graph (`/m/math/cat`)

`/m/math/cat` realizes types and insts as a **category**: types are the objects (the vertices), insts are the
morphisms (the edges), and an inst's dom/rng are its source and target endpoints.

```mtron_pre
[NO_OUTPUT] import(/m/math/cat)
```

The lift is a type constructor. An _object_ is constructed from a type and a _morphism_ from an inst. The cat-graph is
dynamically generated, not apriori defined.

**IMPORTANT**: when referencing an instruction directly in a `console::T`, a `block` inst (sugar'd `|`) prevents the
instruction from being evaluated.

```mtron_pre
int::T.as(object::T)
|plus?int<=int(int::T).as(morphism::T)
```

## the two blocks

### the object block — `object::T` (a vertex)

| field          | what it is                                                       |
|----------------|------------------------------------------------------------------|
| `obj`          | the type this object wraps (the vertex itself)                   |
| `morphed_to`   | the morphisms **sourced from** this object (the outgoing edges)  |
| `morphed_from` | the morphisms **targeted at** this object (the incoming edges)   |
| `law`          | the structural theories this object models (see *theories*)      |

```mtron_pre
int::T.as(object::T).morphed_to()
```

### the morphism block — `morphism::T` (an edge)

| field      | what it is                                                                     |
|------------|--------------------------------------------------------------------------------|
| `form`     | the n-tid coefficient shape (`mapper`, `filter`, `reducer`, `flatmapper`, …)   |
| `class`    | the set-theoretic class (`endo`, `iso`, `auto`, `mono`, `epi`, `section`, `retraction`) |
| `src`      | the source object (the morphism's dom)                                         |
| `trgt`     | the target object (the morphism's rng)                                         |
| `law`      | the declared **process** laws the morphism obeys (see *process laws*)          |
| `analysis` | a lazy sub-block of whole-graph stats — see below                              |

## incidence — the dual

`src`/`trgt` and `morphed_to`/`morphed_from` are the same relation seen from opposite sides:

| from       | dom side (out) | rng side (in)  |
|------------|----------------|----------------|
| an object  | `morphed_to`   | `morphed_from` |
| a morphism | `src`          | `trgt`         |

`morphed_to(A)` is $\{ f \mid \mathrm{src}(f) = A \}$; `morphed_from(A)` is $\{ f \mid \mathrm{trgt}(f) = A \}$.
Composing them round-trips — from an object to its edges and back, or from an edge to its endpoints and back.

## the `analysis` sub-block

`analysis` is one lazy `auto()` — it stays a single `{<j>}` marker at level 1 until you `>>analysis`, so the
morphism block reads small. Inside it, each stat is itself lazy:

| field       | what it is                                                                                   |
|-------------|----------------------------------------------------------------------------------------------|
| `family`    | the same-name siblings: $\{ g \mid \mathrm{op}(g) = \mathrm{op}(f) \}$ — every registration of the same operator |
| `contested` | the witness edges behind the `ambiguous` / `incomparable` labels — the competing siblings     |
| `orbit`     | the reversible-core component of the dom — everything reachable by a chain of opposing edges  |
| `position`  | the edge's place among its siblings (a subset of the six kinds below)                         |
| `inverse`   | the opposing edge $f^{-1}$ with $f \cdot f^{-1} = 1$ (only when a declared inverse exists)    |

## the six edge kinds

`position` classifies an edge against the rest of the graph into up to six labels:

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

| class        | what it asserts                                                             |
|--------------|-----------------------------------------------------------------------------|
| `endo`       | self-mapping — $\mathrm{dom}(f) = \mathrm{rng}(f)$                          |
| `auto`       | an invertible endomorphism — `endo` and `iso`                               |
| `iso`        | invertible — currently a self-inverse (involution)                          |
| `mono`       | left-cancellable — $f \cdot g = f \cdot h \Rightarrow g = h$ (here: a split monomorphism) |
| `epi`        | right-cancellable — $g \cdot f = h \cdot f \Rightarrow g = h$ (here: a split epimorphism) |
| `section`    | split mono — $f$ has a left inverse                                         |
| `retraction` | split epi — $f$ has a right inverse                                         |

## the algebraic theories — laws of structure

An object can model several algebraic structures; `object::T>>law` is a rec keyed by the name you give each
instance. `algebraic_theory::T` is the nominal super-type grouping them:

| type                | roles (fields)                    | what it asserts                                                                                              |
|---------------------|-----------------------------------|--------------------------------------------------------------------------------------------------------------|
| `ring_theory`       | `add`, `mul`, `zero`, `one`       | $\langle R, +, \cdot, 0, 1 \rangle$: $+$ an abelian group, $\cdot$ a monoid, $a \cdot (b + c) = a \cdot b + a \cdot c$ |
| `field_theory`      | `add`, `mul`, `zero`, `one`, `inv` | $\langle F, +, \cdot, 0, 1, {}^{-1} \rangle$: a commutative ring where every non-$0$ element has a multiplicative inverse, $x \cdot x^{-1} = 1$ |
| `rig_theory`        | `add`, `mul`, `zero`, `one`       | $\langle S, +, \cdot, 0, 1 \rangle$: additive and multiplicative monoids, $\cdot$ distributive over $+$, no additive inverses |
| `group_theory`      | `op`, `id`, `inv`                 | $\langle G, \cdot, 1, {}^{-1} \rangle$: $g \cdot g^{-1} = g^{-1} \cdot g = 1$                                |
| `monoid_theory`     | `op`, `id`                        | $\langle M, \cdot, 1 \rangle$: $m \cdot 1 = 1 \cdot m = m$ and $(m \cdot n) \cdot p = m \cdot (n \cdot p)$    |
| `boolean_theory`    | `or`, `and`, `not`, `zero`, `one` | $\langle B, \lor, \land, \lnot, 0, 1 \rangle$: a complemented distributive lattice, $b \lor \lnot b = 1$ and $b \land \lnot b = 0$ |
| `lattice_theory`    | `meet`, `join`, `bottom`, `top`   | $\langle L, \sqcap, \sqcup, \bot, \top \rangle$: every pair of elements has a meet and a join, bounded by $\bot$ and $\top$ |
| `semilattice_theory`| `op`, `id`                        | $\langle S, \sqcup, 0 \rangle$: an idempotent commutative monoid (one side of a lattice)                      |
| `near_ring_theory`  | `add`, `mul`, `zero`              | $\langle N, +, \cdot, 0 \rangle$: $+$ a group, $\cdot$ a monoid, one-sided distributivity, no multiplicative $1$ |

## process laws — `morphism::T>>law`

The morphism's `law` field is a set of **process** law labels served from `mInstSetLawTable` (the declared process
laws):

```mtron_pre
|plus?int<=int(int::T).as(morphism::T)>>law
```

| law                  | what it asserts                                                                  |
|----------------------|----------------------------------------------------------------------------------|
| `commutative`        | $a \cdot b = b \cdot a$                                                          |
| `left_distributive`  | $a \cdot (b + c) = a \cdot b + a \cdot c$                                        |
| `right_distributive` | $(a + b) \cdot c = a \cdot c + b \cdot c$                                        |
| `monoidic`           | an associative binary op with an identity                                        |
| `magmadic`           | a non-associative binary op with an identity                                     |
| `unit`               | an identity element — $e \cdot x = x \cdot e = x$                                |
| `absorbing`          | an annihilator — $a \cdot x = a$ for every $x$                                   |
| `action`             | a monoid/group acting on a carrier — $f(gh)x = f(g)f(h)x$                        |
| `involution`         | self-inverse — $f(f(x)) = x$ (period two)                                        |
| `idempotent`         | $f(f(x)) = f(x)$                                                                 |
| `nilpotent`          | some power is zero — $f^n = 0$                                                   |
| `floatable`          | the coefficient commutes through the stream ring's parallel/serial structure     |
| `blocked`            | cannot float past a barrier — a reduce anchors the coefficient                    |
| `partial`            | pass-through-or-drop $\{0,1\}$ — the optional (maybe) lift of a value            |
| `kleene`             | the reflexive-transitive iterate $a^{*}$ — a Kleene closure                      |
| `prime`              | atomic — no nontrivial factorization                                             |
| `zero_divisor`       | a nonzero $a$ with $a \cdot b = 0$ for some nonzero $b$                          |
| `cyclic`             | generated by one element — every value is a power of a single generator          |
| `poset`              | the carrier is partially ordered — these insts are its comparisons               |

A law is either **structural** (one of the object theories above) or **process** (these morphism labels). The former
provenance axis (syntactic / declared / semantic) is dropped: the table serves the declared process laws, and the
rest are derived from the n-tid or computed on demand.

## see also

* [mtron type system](type-system-mtron.md) — vid/tid, coefficients, nominal vs structural, casting.
* [mtron language reference](language-reference-mtron.md) — `.as(type::T)`, `>>` rshift, coefficients.
* [math instruction set](math-instset-mtron.md) — `/m/math`, where the category and its theories live.
