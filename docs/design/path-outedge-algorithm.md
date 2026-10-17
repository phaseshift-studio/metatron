# path::T out-edge generation — the selector algorithm

A `path::T`'s out-edges are the plans (rewrite rules) whose `cut`/`span` selector matches the path's
`abstraction::T` (its morphism list). Each out-edge's result is `lower(plan)` — the single application
instruction. This note specifies the algorithm; it is independent of the `Plan`/`Cut`/`Span`/`Route`
record→rec conversion.

## The pipeline

```
path::T (code) ──α──▶ abstraction::T (morphism list: form/law/class per inst)
                       │
                       │  for each registered plan: derive selector, match against the morphism list
                       ▼
path::T out-edges = { plan.name → plan::T  :  selector matched }
                       │
                       │  out-edge result = lower(plan)
                       ▼
lower(plan)(path) = the rewritten path
```

## Step 1 — α-lift (already exists)

`ABSTRACTION_TYPE.constructor().apply(code)` lifts the code to a morphism list. Each `morphism::T` carries:

- `name` (uri) — the op.
- `form` (uri) — the classification (`mapper`/`filter`/`flatmapper`/`scatter` = floatable; `reducer`/`join`/`gather` = blocked).
- `law` (lst) — the declared process laws.
- `obj` (inst) — the ORIGINAL instruction (lossless α, so γ = walk `obj` in order).

This is the search surface the selector reads — never a re-derivation from the insts.

## Step 2 — the selector (derived from the plan's cut/span)

A plan declares its selector in two parts:

- `cut = [form => reducer, law => [monoidic, commutative]]` — the ANCHOR: a morphism whose `form` equals
  `cut.form` and whose `law ⊇ cut.laws`.
- `span` — the induced regions: `pre` (the floatable run before the cut), `at` (the cut), `post` (the tail).

The selector is a regex over the morphism list whose alphabet is the morphism fields (`form`/`law`).
Concretely, `law_shard`:

```
[ floatable.star(),                                  // pre:  zero-or-more floatable morphisms
  form == reducer ∧ {monoidic, commutative} ⊆ law,   // at:   the anchor
  any.star() ]                                        // post: the tail
```

## Step 3 — the match (selector against the morphism list)

Walk the morphism list and locate the anchor:

```
find cut index i:  morphism[i].form == cut.form  ∧  cut.laws ⊆ morphism[i].laws
pre  = morphisms[0..i):  every form ∈ floatable       // the span's atom obligation
post = morphisms[i+1..]                               // arbitrary
```

The plan fires iff the anchor exists and the pre-run is floatable. This **is** the liveness — a precise
shape match, replacing `catLiveness.Profile`'s per-rule over-approximation. Note `pre` is `{*}` (zero-or-more),
so a bare `start(x).sum()` shards: anchor at index 1, empty pre-run.

## Step 4 — the out-edge

For each plan whose selector matched, materialize `path[plan.name] = plan::T`. The plan rec is pure data
(`name`/`cut`/`route`); its execution is the `lower` instruction:

```
lower?path<=path(plan::T) { interpret(plan).apply(lhs) }
```

`lower` reads the plan rec's `cut`/`route`, re-locates the anchor (or reuses the liveness match), applies the
route (factor seed → ship → gather → re-reduce), and returns the rewritten path re-lifted to `path::T`.
No lambda is stored in the plan. `lower` is a **type method** — registered in `catInstSet.insts()`, one
instruction shared by every `plan::T` value, taking the plan as its argument and the path as its lhs. The plan
is pure data; `lower` has all the state it needs in the plan (cut/route) and the path (code) (non-recursive:
`path → plan → lower → path`).

## Key principles

1. The selector reads the abstraction's morphism FIELDS (`form`/`law`), never re-derives from insts. This is
   the whole point of `abstraction::T`.
2. Liveness (selector match) and execution (route) are the plan's two halves; `lower` owns the second.
3. The out-edge result is `lower(plan)`, so the rewrite *schedule* (`catRewrite.rules()`) and the plan's
   *out-edge* both collapse to `lower(plan::T)` — "the rewrite table is the category."

## Open decisions

1. **Match site.** The selector should match the morphism recs' fields, but the current `InstPattern` matches
   against `Inst` (re-deriving `Form.of` / `LawTable.declares`). Either adapt `InstPattern` to match morphism
   recs, or add a morphism-selector that reads `form`/`law` directly.
2. **Double match.** Out-edge generation (liveness) and `lower` both need the selector match. Cache the cut
   index from the liveness pass into the plan rec, or accept the re-match inside `lower`.
3. **`{*}` shape.** `law_shard`'s pre is `{*}` (zero-or-more), so a bare reduction shards — this is the
   `2 > insts.size()` guard, not `3 >`.
