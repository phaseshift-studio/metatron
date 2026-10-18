# Folding Database Push-Down into the Plan Architecture

**Date:** 2026-10-18
**Context:** The rewrite DSL is now declarative `plan::T` (cut/route/span), driven by the theory's
roles/laws — all twelve cat rules are structural plans, no bare lambdas, no `Law` selector, no
`op` string, no `cut => [0]` index. The database backends still use a *separate* engine —
`RewriteBuilder` (`CommonRewrites`) — that matches a multi-instruction pattern and replaces it with
a native op. This is the overview of how that second engine folds into the first.

---

## 1. Two engines today

**The algebraic engine** (`PlanBuilder` → `plan::T` → `PlanInterpreter`):

- A plan names a **property** — `cut role => add`, `cut law => involution`, `cut form => reducer` —
  and the theory (`object[T].law`) resolves it to the concrete op at rewrite time.
- Routes: `local` (transform) · `peers` (distribute) · `none` (delete).
- The folds (`collapsePairs`, `repeatedTerms`, `contract`, …) are fixed Java bodies; the plan is
  pure declarative data.

**The database engine** (`RewriteBuilder` → `CommonRewrites` → native op):

- Matches a multi-instruction sequence (`from.count`, `from.isa`, `from.take`, `from.>>field`) and
  replaces it with a backend-specific native op (`native_count`, `native_where`, …).
- Parameterized by `spaceType` + a native function, so `tble` (SQL), `dcmnt` (MongoDB), and `grph`
  (Gremlin) each register their own bodies for the *same* rewrites.

The goal: one engine, not two.

## 2. The key insight: DataPath is the database's theory

`object[T].law` normalizes a **type** into a single consistent algebra (`add`/`zero`/`inv`).
`DataPath` normalizes a **URI** into a single consistent address:

```
/db/collection/entry/field/extension...
      │          │     │        └─ sub-field / traversal
      │          │     └─ field   (column / property / field)
      │          └─ entry  (row / document / vertex)
      └─ collection  (table / collection / vertex-label)
```

Three backends, one structure — so a plan written against `DataPath` is backend-agnostic, exactly
as a plan written against a role is type-agnostic. That is the whole reason the three backends
share `CommonRewrites`: the push-down never speaks SQL/MQL/Gremlin, it speaks `DataPath`.

## 3. The push-down as a plan

The push-down is a **delegation**, not a fold:

```
[ source , operation ]   →   [ native_operation ]
  from      count              native_count
  from      isa(pred)          native_where
  from      take(n)            native_limit
```

As a plan: **cut the source (`from`/`at`), span the operation
(`count`/`where`/`take`/`select`/`sum`/`mean`/`prod`), route to `native`** — delegate the span to
the space that owns the `DataPath`.

`route native` is the fourth route target: `local` · `peers` · `none` · **`native`**.

## 4. The three pieces to build

1. **The real span.** The operation is a `POST` region after the source, not an `AT` anchor. The
   span must become a real interval (`[(lo, hi, role)]`) so the plan can name `[source, operation]`
   as a region — the same gap `law_absorbing` (a prefix) already exposed.

2. **The `native` route target.** `route(native)` delegates the span to the space. The `DataPath`
   is the routing address: the source carries a URI → the URI decomposes to a `DataPath` → the
   `DataPath` names the owning space → the operation is delegated there. (The existing unused
   `SPACE`/`TREE` targets are the likely home.)

3. **The space-aware cut.** The push-down fires only when the source resolves to a DB space — a
   *different* predicate than "the op declares a law." The cut reads the `DataPath`, not the theory.

## 5. One pattern, many instances

The push-down is **one** pattern (delegate to the space), with instances in two dimensions —
*operation* × *backend*: `count`/`sum`/`mean`/`limit`/`select`/`where`/`prod` × SQL/MQL/Gremlin.

The plan is the same across all of them; only the resolver (the space's `native_xxx` body) differs.
Solve it once for RDBMS; the other two ride free.

## 6. Normalization vs lowering

The algebraic folds **normalize** — mtron → mtron, same op, better form
(`branch(x,x) → branch(2x)`). The push-down **lowers** — mtron → native, a *different* op that was
not in the chain (`from.count → native_count`). This is the one genuinely new thing: `route(native)`
emits a backend op, not a mtron op.

## 7. The resolver symmetry

| the plan names | normalizes | into | resolved by | the instance |
|---|---|---|---|---|
| `role => add` | a type | a theory (`add`/`zero`/`inv`) | `object[T].law` | `branch` / `plus` |
| `cut(from) · span(count) · route(native)` | a URI | a `DataPath` (`collection`/`entry`/`field`) | the space | SQL / MQL / Gremlin `native_xxx` |

Two resolvers, one declarative language. The plan is a reference into a normalized structure; the
resolver renders the instance.

## 8. Next steps (rough order)

1. Materialize the span as a real region — the `[(lo, hi, role)]` segmentation. This unblocks
   `law_absorbing`'s prefix and the push-down's `POST` span together.
2. Add `route native` (or reuse `SPACE`) with the `DataPath` as its address.
3. Port `CommonRewrites` onto `PlanBuilder`: `cut(from) · span(count|where|take|…) · route(native)`,
   with each space registering its `native_xxx` body as the route payload.
4. Retire `RewriteBuilder` once `tble` / `dcmnt` / `grph` all ride the new engine.
