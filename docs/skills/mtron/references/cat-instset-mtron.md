---
name: cat-instset
description: |
  The category graph at `/m/math/cat`: every type is an **object** (a vertex) and every inst is a **morphism**
  (an edge). Lift a type with `.as(object::T)` to read the morphisms incident to it (`morphed_to` out-edges,
  `morphed_from` in-edges); lift an inst with `.as(morphism::T)` to read its endpoints (`src` dom, `trgt` rng)
  and its whole-graph analysis (`family`, `contested`, `orbit`, `position`, `inverse`).
  TRIGGER: When reading a type's incident morphisms or a morphism's endpoints, when reasoning about a morphism's
  siblings / inverse / connected component, when classifying an edge's coefficient shape (`form`) or its declared
  process laws (`law`), or when wiring the algebraic theories (`ring` / `group` / `monoid`).
---

# the category graph (`/m/math/cat`)

`/m/math/cat` realizes types and insts as a **category**: types are the objects (the vertices), insts are the
morphisms (the edges), and an inst's dom/rng are its endpoints. The lift is a type constructor, not a query
processor — cast a type to a vertex, cast an inst to an edge:

```mtron_pre
int::T.as(object::T)>>obj
```

```mtron_pre
|plus?int<=int(int::T).as(morphism::T)>>form
```

Every registered inst is a morphism and every type an object — the graph is emergent, not declared edge-by-edge.

## the two blocks

### the object block — `object::T` (a vertex)

| field          | what it is                                                      |
|----------------|-----------------------------------------------------------------|
| `obj`          | the type itself — the down-elevator to the vertex's source type |
| `morphed_to`   | the morphisms **sourced from** this object (the OUT edges)      |
| `morphed_from` | the morphisms **targeted at** this object (the IN edges)        |
| `law`          | the structural theories this object models (see *theories*)     |

### the morphism block — `morphism::T` (an edge)

| field      | what it is                                                                   |
|------------|------------------------------------------------------------------------------|
| `form`     | the n-tid coefficient shape (`mapper`, `filter`, `reducer`, `flatmapper`, …) |
| `src`      | the source object (the morphism's dom)                                       |
| `trgt`     | the target object (the morphism's rng)                                       |
| `law`      | the declared **process** laws the morphism obeys (see *laws*)                |
| `analysis` | a lazy sub-block of whole-graph stats — see below                            |

## incidence — the dual

`src`/`trgt` and `morphed_to`/`morphed_from` are the same relation seen from opposite sides:

| from       | dom side (out) | rng side (in)  |
|------------|----------------|----------------|
| an object  | `morphed_to`   | `morphed_from` |
| a morphism | `src`          | `trgt`         |

`morphed_to(A)` is `{ f : src(f) = A }`; `morphed_from(A)` is `{ f : trgt(f) = A }`. Composing them round-trips —
from an object to its edges and back, or from an edge to its endpoints and back.

## the `analysis` sub-block

`analysis` is one lazy `auto()` — it stays a single `{<j>}` marker at level 1 until you `>>analysis`, so the
morphism block reads small. Inside it, each stat is itself lazy:

| field       | what it is                                                                                   |
|-------------|----------------------------------------------------------------------------------------------|
| `family`    | the same-name siblings: `{ g : op(g) = op(f) }` — every registration of the same operator    |
| `contested` | the witness edges behind the `ambiguous` / `incomparable` labels — the competing siblings    |
| `orbit`     | the reversible-core component of the dom — everything reachable by a chain of opposing edges |
| `position`  | the edge's place among its siblings (a subset of the six kinds below)                        |
| `inverse`   | the opposing edge `f^{-1}` with `f · f^{-1} = 1` (only when a declared inverse exists)       |

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

## the algebraic theories — laws of structure

An object can model several algebraic structures; `object::T>>law` is a rec keyed by the name you give each
instance. `algebraic_theory::T` is the nominal super-type grouping them:

| type            | roles (field → op-tid)      | what it asserts                                      |
|-----------------|-----------------------------|------------------------------------------------------|
| `ring_theory`   | `add`, `mul`, `zero`, `one` | `+` an abelian group, `·` a monoid, `a(b+c) = ab+ac` |
| `group_theory`  | `op`, `id`, `inv`           | `g · g^{-1} = g^{-1} · g = 1`                        |
| `monoid_theory` | `op`, `id`                  | `m · 1 = 1 · m = m` and associativity                |

## process laws — `morphism::T>>law`

The morphism's `law` field is a set of **process** law labels served from `CatLawTable` (the `declared ∩ process`
cell):

```mtron_pre
|plus?int<=int(int::T).as(morphism::T)>>law
```

Examples: `commutative` (`f(x,y) = f(y,x)`), `right_distributive`, `action`, `monoidic`, `involution`, `absorbing`,
`idempotent`. A law has a *provenance* tier (`syntactic` — derived from the n-tid, `declared` — proved once per
family, `semantic` — apply-and-test) and a *kind* (`structure` → the object theories, `process` → these morphism
labels).

## see also

* [mtron type system](type-system-mtron.md) — vid/tid, coefficients, nominal vs structural, casting.
* [mtron language reference](language-reference-mtron.md) — `.as(type::T)`, `>>` rshift, coefficients.
* [math instruction set](math-instset-mtron.md) — `/m/math`, where the category and its theories live.
