# Entities Redirect Plan — Container Recs & Distributed Schema

## Goal

When dereferencing a collection/table/label prefix with no wildcard, return a **container Rec**
with metadata and a lazy `+` field that streams the actual entities.  This unifies how all spaces
present their collections: the bare prefix is a self-describing object, not `noobj`.

## The entity principle

Today:
```
*cg:nodes    → table::cg:nodes         (a typed URI — nothing valuable)
*cg:nodes/+  → [rows...]               (separate dereference for data)
```

Target:
```
*cg:nodes    → [
  name       => 'nodes',                 // collection identity
  count      => 14762,                   // lazy SQL COUNT(*) pushdown
  schema     => [id=>str::T, kind=>str::T, ...],  // auto-discovered type info
  +          => auto_(() => *cg:nodes/+)  // lazy entity stream
]

*cg:nodes/+  → [rows...]                // same as today — wildcard triggers data
*cg:nodes/1  → specific row             // same as today
*cg:nodes/name → 'nodes'               // field access on container
*cg:nodes/count → 14762                 // field access triggers lazy count
```

**Key properties:**
- `+` is an `auto_` field — never accessed unless explicitly read
- `count` is an `auto_` field — triggers the SQL pushdown rewrite
- `schema` is a static Rec populated at space construction
- `rec.elements()` skips `auto_` fields — `*cg:nodes.plus(_)` doesn't merge the entity stream
- No new field names, no parser ambiguity, no extra path segments

## Container fallback in directReader

Implemented first in `memSpace.directReader()`: when an exact node lookup returns null,
try one level of wildcard expansion (`pattern/+`) and aggregate matching children into
a Rec.  This is the mechanism that produces the container Rec from leaf-level data.

```
*cg:nodes → exact lookup "nodes" → null → try "nodes/+" → find a,b,c → return [a=>1,b=>2,c=>3]
```

**Depth:** 1 level only.  "What values live directly under this prefix?" is a clean contract.
Deeper aggregation belongs at a higher layer (traversal, `repeat()`).

## Distributed schema

When every collection's container Rec carries its own `schema` field, a space listing
becomes self-describing:

```
*cg:+ → [
  nodes => [name=>'nodes', count=>14762, schema=>[...], + => auto_(rows)],
  edges => [name=>'edges', count=>49068, schema=>[...], + => auto_(rows)],
  files => [name=>'files', count=>468,   schema=>[...], + => auto_(rows)]
]
```

Downstream consumers can introspect without out-of-band schema lookups:
- A UI tree browser renders `*/sys/space/+/+` → labels and row counts without touching data
- A query planner reads `schema` to validate field access before executing
- A federated query engine joins across spaces using type-compatible schemas

## Per-space implementation

### memSpace (done)

`private Iterator<IdObj> readContainer(fURI pattern)` — depth=1 trie match, builds a
Rec from matched children.  Called from `directReader` exact-lookup branch when
`sjvm().get(pattern)` returns null.

### tbleSpace (in progress)

Same pattern but needs to use `schema.read(conn, wildcard)` directly (SQL-level access)
rather than going through `resolveRead`/`directReader` which creates recursion.
The `SimpleKeyValueSchema` returns all rows for pattern queries, so `collectResults`
filters by the external wildcard pattern.

### dcmntSpace (planned)

Same pattern using `collection.find()` with regex or BSON field-path matching.
MongoDB collections map 1:1 with the container concept — a collection listing
already returns metadata.

### grphSpace (planned, see redesign plan)

Vertex/edge LABELS are collections.  Each label's container Rec carries:
- `schema` — auto-discovered property types
- `+` — lazy vertex/edge iterator
- `count` — lazy `g.V().hasLabel(...).count()`

## Migration from current behavior

1. **memSpace** — done.  Container fallback in `directReader`.
2. **tbleSpace** — add container fallback using `schema.read()` directly.
3. **dcmntSpace** — add container fallback using `collection.find()`.
4. **AbstractSpace.readStream()** — add `readContainer` call as default last-resort
   fallback when `read()` returns `noobj`.  Only fires for non-pattern URIs.

## Test coverage

`AbstractSpaceTest.testMonoRootlessReadWrites()` — writes leaf values, reads bare
prefix, verifies container aggregation.  Has `finally` cleanup to avoid polluting
shared DB backends.

Tests:
- Single child → container `[a=>1]`
- Two siblings → container `[a=>1,b=>2]`
- Immediate child under prefix → `[p=>10]` (depth=1)
