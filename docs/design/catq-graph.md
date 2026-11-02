# catq — the category of the object graph (`?catq`)

**2026-09-23** · companions: `docs/design/webspace.md`, `docs/design/as-graph-checker.md`

## 0. The ask

`?asq` reads any `as` edge as a property-graph edge — its kinds (`duplicate`, `ambiguous`, `incomparable`,
`coupling`, `isochain`, `retract`) are the relations the edge participates in, derived from the endpoints' place in
the graph. Generalize the mechanism out of `as` and onto every instruction: a **category** whose objects are types,
whose morphisms are insts, and whose query is `?catq`.

The organizing fact: **every inst is a morphism** — objects are types, arrows are insts, and the arrow's address is
the n-tid `(op, dom, rng, arg1::T, …)`. `as` was just the family whose arg slot *is* its rng type, which is why
endpoint-pair identity sufficed for `?asq`; the full catalog needs the op in the address, because `Hom(int, int)`
holds `plus`, `mult`, `minus`, `gt`, … all at once.

## 1. Two domains of discourse

The central realization: there are **two domains**, and conflating them is the one mistake to avoid.

| domain | the things | the discourse |
|---|---|---|
| the **obj graph** | types, insts, values | data and computation — apply it, read it, write it |
| the **category** | objects, morphisms | relation and structure — form, position, law |

The two are coupled by a one-to-one **lift** — an obj-vid maps to a category-vid:

```
/m/plus?dom=int&rng=int   →   /m/algebra/morphism_plus?dom=object_int&rng=object_int
/m/str                    →   /m/algebra/object_str
```

The lift is total — even an edge's *endpoints* lift, because in the category the vertices are `object` nodes, not
raw types. Two **elevators** cross the boundary, both pure functions (the lift is deterministic naming, never a search):

- **up** — `*<obj-vid>&catq` → the object/morphism block;
- **down** — the block's `obj => <obj-vid>` field → the raw inst/type.

Every other field stays in category-space: `inverse`, the law-structure roles, `in`/`out` — they name *morphs*, not
objs. That is what keeps the algebraic domain a connected, navigable thing instead of a scatter of annotations bolted
onto obj addresses. The "see-saw" alternative (`lift · traverse · lift · …`) works but demotes a morphism to an
ephemeral projection of an object; the lift keeps it a first-class citizen of its own domain.

## 2. The graph is emergent, not declared

A `!*` (auto_from) has the type of what it resolves to — it is transparent. So the type system only ever sees a tree:
a field resolves to a value whose fields resolve further, recursively, forever. The **graph** appears only because two
`!*` refs can name the same vid — the space stores the obj once, and the refs alias it. That aliasing is the entire
difference between the infinite adjacency tree (theory) and the finite graph (implementation).

Three pointer kinds decide what a field *is*:

| field holds | is | example |
|---|---|---|
| `!*<vid>` | an **edge** — deref to a node | `add => !*morphism_plus?dom=object_int&rng=object_int` |
| `!<inst>` | a **derived property** — compute on touch | `position => !compute_position(*tid)` |
| value | a **static property** | `form => mapper`, `law => commutative` |

The morph blocks are therefore property graphs in the literal sense: fields holding `!*` refs are edges, fields holding
values are properties, and the field *name* is only the edge label.

## 3. The block types

All registered under `/m/algebra`, in `Morphism.java`, docWrap'd, and mounted in `mInstSet`:

| type | role | shape |
|---|---|---|
| `object::T` | a vertex | `rec[obj=>#::T, {?}in=>[morphism], {?}out=>[morphism]]` |
| `morphism::T` | an edge | `rec[obj=>inst::T, form=>union(12), {?}position=>lst(union{some}(6)), {?}contested=>lst(inst), {?}orbit=>lst(#), {?}family=>lst(inst), {?}law=>lst(union{some}(23)), {?}inverse=>uri]` |
| `algebra::T` | nominal super of the law-structures | no predicate — tests by vid |
| `ring::T` | a ring structure | `rec[add=>uri, mul=>uri, zero=>uri, one=>uri]` |
| `group::T` | a group structure | `rec[op=>uri, id=>uri, inv=>uri]` |

`object` and `morphism` are the two kinds of thing; there is deliberately no "object-or-morphism" superconcept
(category theory has none). `obj` is the elevator and belongs on both blocks.

## 4. The classification space — three axes

| axis | the question | source |
|---|---|---|
| **form** | what is this morphism's local shape? | the n-tid — dom/rng coefficient classes |
| **position** | where does it sit in the graph? | reachability over the edge set |
| **law** | which algebraic laws does it obey? | apply-and-test, or declared per family/type |

### 4.1 form

`Inst.Form` (twelve values), load-bearing in the resolver and machine.

| form | signature | algebra reading |
|---|---|---|
| `initial` | dom {0} | the unique arrow out of the initial object |
| `terminal` | rng {0} | the 0-function; the annihilator `a0 = 0` |
| `fork` / `join` | split / merge tids | the diagonal / codiagonal of the `+` abelian group |
| `reducer` | gather ∧ rng 1 | the reduce near-ring member — many-to-one, temporal |
| `gather` | dom unbounded | the barrier near-ring — many-to-many |
| `scatter` | dom > 1 → rng 1 | the bulk axiom's fold |
| `catcher` | catch tid | fail recovery |
| `filter` | dom 1, rng {0,1}, same base | the maybe monad; the Boolean algebra member |
| `mapper` | dom 1, rng 1 | an endomorphism of its dom |
| `flatmapper` | dom 1, rng > 1 | the Kleene-star morphism |
| `standard` | — | outside the vocabulary |

### 4.2 position

The six `?asq` kinds, generalized from the `as` family to the whole catalog (`Morphism.Position`):

| position | definition | mtron instance |
|---|---|---|
| `duplicate` | another inst of the same family maps the same dom→rng | redundant registration — drop one |
| `ambiguous` | contested by an incomparable sibling that overlaps | dispatch has no most-specific winner — refine a dom |
| `incomparable` | contested by an incomparable, disjoint sibling | dispatch stays total — no action |
| `coupling` | an opposing inst exists — a candidate inverse pair | `metric ↔ imperial`; the `from_`/`to_` read-write pair |
| `isochain` | endpoints linked by a chain of couplings — one orbit of the reversible core G∩G⁻¹ | `bytes ⇄ … ⇄ int` |
| `retract` | mutually reachable, the round trip idempotent — `a ≅ im(e)`, a subobject | self-loops; lossy round trips |

Computed over the *full* graph (every inst is an edge), so `coupling` means "some opposing edge exists (any op)",
not "an opposing cast exists". The kind and the label decouple.

### 4.3 law

`Morphism.Law` — 23 labels in three tiers:

| tier | computation | labels |
|---|---|---|
| **syntactic** | from the n-tid — free | `absorbing`, `kleene`, `zeroable`, `floatable`, `blocked` |
| **declared** | proved once, registered per family/type | `monoidic`, `semilattice`, `boolean`, `near_ring`, `ring`, `group`, `action`, `prime`, `left_distributive`, `right_distributive`, `commutative`, `poset` |
| **semantic** | apply-and-test on canonical objs | `unit`, `involution`, `idempotent`, `nilpotent`, `zero_divisor`, `cyclic` |

(`zeroable` was `maybe`; renamed because the label collided with `cInt.maybe()` and the codebase's own `isZeroable()`
term for the `{0,1}` coefficient. `boolean` is `boolean_` in Java — reserved word — with mtron label `boolean`.)

### 4.4 law is (set, ops) — the declared tier is structural

An algebra is a **structure** `(set, ops)`, not a unary label. `int` is not "a ring" in the abstract; it is "`int`
together with `+`, `*`, `0`, `1`". So the declared tier splits into two shapes:

| declared law | shape | lives on |
|---|---|---|
| per-operation (`commutative`, `prime`) | property of ONE op | `morphism::T` |
| algebra (`ring`, `group`, `monoidic`, `semilattice`, `boolean`, `near_ring`, distributive) | `(type, named ops)` | `object::T` |

Each algebra law is a rec type whose **fields are its roles** — the rec's own tid is the law, the fields name the ops
that witness it:

```
int?catq => object::T[
  ...
  law => [
    ring::T[add=>!*morphism_plus, mul=>!*morphism_mult, zero=>!*morphism_zero, one=>!*morphism_one],
    group::T[op=>!*morphism_plus, id=>!*morphism_zero, inv=>!*morphism_neg]
  ]
]
```

A type lives in **many** algebras, and the same op appears in many of them (`plus` is both `ring.add` and
`group.op`) — that sharing *is* the substructure relation, made explicit by the graph instead of restated per law.
The `ring::T`/`group::T` family is the algebra analog of `http::T`/`ws::T`/`mcp::T`: sibling "theory" types a thing
instantiates. The role vocabulary is a small fixed set (`add`, `mul`, `op`, `id`, `inv`, `zero`, `one`, `join`,
`meet`, `and`, `or`, `not`), and each algebra law's arity + role names is its signature.

## 5. The mtron surface

`?catq` is a standalone q-proc (the `?docq` pattern), not an axis bolted onto `?docq`. `morphWrap` composes the
morphism block at registration time — the form baked in, four lazy `!compute_*` pointers (`position`, `contested`,
`orbit`, `family`), and the declared `law`/`inverse` — and stores it keyed by the inst's n-tid.

- **read an edge** — `*plus?int<=int&catq` → the `morphism::T` block.
- **read a vertex** — `*int&catq` → the `object::T` block (computed: `in`/`out` neighborhoods, orbit).
- **walk** — from the block, plain mtron: `>>inverse`, `>>law`, `>>out`, `map`/`filter`. The q-proc only hands you
  the graph object at the address; traversal is the language's own `!*`/`>>` vocabulary.
- **declare** — `morphWrap(inst, [law=>[…], inverse=>…])`, and `morphWrap(type, [ring=>[add=>…, mul=>…, …]])`
  for the algebra structures. Any inst/type-registration write invalidates the derived maps (the generalized
  `AS_INST_TID` postWrite).

## 6. What is implemented

- `Morphism.java` — the axes (`Law`, `Position`), the graph engine (`insts`, `graph`, `position`, `contested`,
  `orbit`, `family`, `vertex`, `reversible`, `reaches`), the compute insts under `/m/algebra`
  (`compute_position`/`contested`/`orbit`/`family` — the target arrives as a uri arg, never applied), and the block
  types (`object`, `morphism`, `algebra`, `ring`, `group`).
- `MorphQ.java` — `?catq` (memspace store), `morphWrap` (nests with `docWrap`), registered per-instset via
  `AbstractInstSet`, `BootLoader`, `stackSpace`.
- Wrapped so far: `Int` and `Obj` (earlier), then `Str` and `Rec` per the base-type tables — per-op laws
  (`commutative`, `idempotent`, `involution`, `unit`, `boolean`, `poset`, `monoidic`) and inverse pairs
  (the five `as` casts, `reverse`, `split`↔`merge`).

## 7. Open questions

- the algebra declarations (`ring`/`group`/…) are typed but not yet authored — `morphWrap(Type, …)` is the next
  slice; the per-op laws already on insts (`monoidic`, distributive) may migrate to the type once the role recs land.
- exact category-vid naming scheme (the lift's `morphism_<op>?dom=object_<dom>…` form) — settled in principle,
  spelling still open.
- composition certification (`a·a⁻¹ = 1`) — canonical objs per type, or symbolic?
- arg-level inverses (`plus(n)` vs `minus(n)`) — the n-tid carries arg *types*, not values.
- cost of all-pairs position analysis over thousands of insts — per-family scoping?
