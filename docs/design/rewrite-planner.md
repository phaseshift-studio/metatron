# Rewrite Planner Design

> **Status**: Idea / Design Phase
> **Date**: 2025-01-XX
> **Authors**: Marko, Claude

## Overview

This document captures ideas for evolving the rewrite system from a fixed-point iteration model to an explicit **plan-directed** model, where rewrite topology is expressed as first-class mtron data.

---

## Current Approach: Fixed-Point Iteration

```java
// Code.rewrite()
while (done != 0) {
    Router.global().spaces()
        .flatMap(space -> space.rewrites().stream())
        .forEach(r -> rewrittenCode.set(r.apply(rewrittenCode.get())));
    if (hash == rewrittenCode.get().hashCode())
        done--;
}
```

**Characteristics:**
- All rewrites run every iteration
- Iterates until code stops changing (hash stable for 2 rounds)
- Rewrite composition is **implicit** - rewrites discover each other via TID matching
- No explicit ordering control

**Example of implicit linking:**
```java
// whereRewrite outputs TID: sql_where
// whereCountRewrite matches TID: sql_where, then count
CommonRewrites.whereCountRewrite(
    tabledbSpace.class,
    TBLE_ISA_REWRITE_TID.extend("sql_where"),  // ← matches output of whereRewrite
    TBLE_ISA_REWRITE_TID.extend("sql_where_count"),
    ...
)
```

---

## Proposed Approach: Explicit Plan as Data

### Core Insight

mtron's **relation syntax** is perfect for expressing rewrite graphs:

```mtron
// Linear chain
a => b => c

// Fan-out (try multiple rewrites)
from_table => {sql_where, sql_limit, sql_count}

// Conditional composition (if sql_where succeeds, try these)
sql_where => {sql_where_count, sql_where_limit}

// Full plan as nested structure
id_removal => {
  sql_where => {sql_where_count, sql_where_limit},
  sql_limit => sql_limit_count,
  sql_count
}
```

### The `>>` Operator (Shift Right / Traverse)

The `>>` operator traverses relations, making plan navigation trivial:

```mtron
mtron> plan = id_removal => {
     |   sql_where => {sql_where_count, sql_where_limit},
     |   sql_limit => sql_limit_count,
     |   sql_count
     | }

// One level deep - get candidates after id_removal
mtron> plan.>>
==>sql_where=>{sql_where_count,sql_where_limit}
==>sql_limit=>sql_limit_count
==>sql_count

// Two levels deep - get leaf rewrites
mtron> plan.>>.>>
==>sql_where_count
==>sql_where_limit
==>sql_limit_count
```

### Visual Representation

```
id_removal
    │
    ├── sql_where ──┬── sql_where_count
    │               └── sql_where_limit
    │
    ├── sql_limit ───── sql_limit_count
    │
    └── sql_count (leaf)
```

---

## Benefits of Plan-Directed Rewriting

| Aspect | Fixed-Point | Plan-Directed |
|--------|-------------|---------------|
| **Control** | Run all, iterate until stable | Directed graph traversal |
| **Short-circuit** | No - all rewrites checked | Yes - stop when path completes |
| **Customization** | Same plan for all queries | Different plans per context |
| **Cost-based** | Hard to add | Natural - pick cheapest branch |
| **Debuggability** | "Why did this fire?" | Plan is inspectable data |
| **Composition** | Implicit via TID | Explicit in plan structure |

---

## Potential Algorithm

```
apply_plan(code, plan):
  1. Extract rewrite at plan head (e.g., id_removal)
  2. Try to apply rewrite to code
  3. If matched:
     - Get next candidates via plan.>>
     - For each candidate: recursively apply_plan(rewritten_code, candidate)
     - Return best result (or all results for exploration)
  4. If not matched:
     - Skip to next sibling in plan (fan-out branch)
  5. Return final rewritten code
```

### In mtron pseudo-code:

```mtron
// Hypothetical syntax
code.rewrite(plan) :=
  plan.head.apply(code).is(rewritten)
    ? plan.>>.fold(rewritten, [c,p] -> c.rewrite(p))
    : code
```

---

## Plan Storage: Schema-Level Plans

Plans live in the **schema** of each space:

```
/sys/space/netflix/schema          # Table definitions, etc.
/sys/space/netflix/schema/plan     # Custom optimization plan for this dataset
```

**Inheritance model:**
1. Check `/sys/space/{name}/schema/plan` for dataset-specific plan
2. Fall back to `/m/tble/inst/rewrite/plan` (general-purpose tble plan)
3. Each dataset can customize optimizations for its specific schema/access patterns

**Benefits:**
- OLTP vs OLAP datasets can have different plans
- Heavily-indexed tables can prioritize index-aware rewrites
- Experimental plans can be tested on specific datasets

---

## Rewrites and Router

Currently, rewrites are **not registered** in `Router.redirects()`:
- They're available but require full URI to access
- Not intended to be user-facing
- See `AbstractInstSet` (two constructors - migrating to `setup()` model)

**Future option:** Could add rewrites to Router redirects if we want them more discoverable, but current model keeps them as internal optimization machinery.

---

## Advanced Plan Traversal

### Keyed Branch Selection with `>>`

Navigate to specific branches using keys:

```mtron
mtron> [a=>b, c=>[d=>[e=>f, g=>h]]] >> c
==>[d=>[e=>f,g=>h]]

mtron> [a=>b, c=>[d=>[e=>f, g=>h]]] >> c >> d
==>[e=>f,g=>h]

mtron> [a=>b, c=>[d=>[e=>f, g=>h]]] >> c >> d >> {g, e}
==>h
==>f
```

This means a planner can **choose which rewrite branch** to follow based on what matched!

### Lookahead with `select` (Tree Pruning)

Use `select` to prune the plan tree before traversal:

```mtron
// Original plan tree
plan = [a=>[b=>[c=>[e=>2, g=>4], d=>[f=>5, h=>10]]]]

// Prune to only branches where leaf value = 2
mtron> plan.select([a=>[b=>[_=>[_=>?=2]]]])
==>[a=>[b=>[c=>[e=>2]]]]

// Then continue traversal on pruned tree
mtron> plan.select([a=>[b=>[_=>[_=>?=2]]]]).>>.>>b
==>[c=>[e=>2]]
```

**Use case:** Before applying rewrites, prune plan to only branches that could match the current code pattern. Avoids wasted work on irrelevant branches.

---

## Open Questions

### 1. Cost Model
How to choose between branches in `{a, b, c}`?
- First match wins? (simple)
- Estimate cost and pick cheapest? (like Volcano/Cascades)
- Run all and pick best result? (expensive but optimal)
- Use `select` to prune based on code structure first?

### 2. Rewrite Identity
Currently rewrites are identified by TID. In plan-directed model:
- Are rewrites still referenced by TID in the plan?
- Or are they first-class objects in the plan structure?

```mtron
// Option A: TIDs as references
plan = id_removal => sql_where => sql_count

// Option B: Inline rewrite definitions (more expressive?)
plan = [_._._ -> _.] => [from.where -> sql_where] => [_.count -> sql_count]
```

### 3. Backwards Compatibility
Can we support both models?
- `code.rewrite()` - current fixed-point (default)
- `code.rewrite(plan)` - plan-directed (explicit)

The stub already exists in Code.java:
```java
/*default Code rewrite(final Code queryPlan) {
   // TODO!!!
}*/
```

### 4. Plan Versioning
If plans live in schema, how to handle:
- Schema migrations that invalidate plan assumptions?
- A/B testing different plans?
- Rolling back to previous plan if new one causes issues?

---

## Example: SQL Optimization Plan

```mtron
sql_plan = {
  // Aggregations - try to push to SQL
  from => count -> sql_count,
  from => sum -> sql_sum,
  from => mean -> sql_mean,

  // Filtering - with follow-up optimizations
  from => where -> sql_where => {
    _ => count -> sql_where_count,
    _ => take -> sql_where_limit,
    _ => has -> sql_where_has
  },

  // Limiting
  from => take -> sql_limit => {
    _ => count -> sql_limit_count
  },

  // Existence
  from => has -> sql_has
}
```

---

## Next Steps

1. **Validate the model** - Try expressing current rewrites as a plan
2. **Prototype `Code.rewrite(plan)`** - Simple recursive implementation
3. **Test composition** - Does `>>` traversal work correctly?
4. **Benchmark** - Is plan-directed faster than fixed-point for complex queries?
5. **Decide on plan storage** - Where should plans live?

---

## References

- `Code.rewrite()` - `/src/main/java/studio/phaseshift/metatron/isa/m/type/Code.java`
- `CommonRewrites` - `/src/main/java/studio/phaseshift/metatron/algebra/rewrite/CommonRewrites.java`
- `RewriteBuilder` - `/src/main/java/studio/phaseshift/metatron/algebra/rewrite/RewriteBuilder.java`
- `tbleInstSet` rewrites - `/src/main/java/studio/phaseshift/metatron/isa/tble/tbleInstSet.java`
