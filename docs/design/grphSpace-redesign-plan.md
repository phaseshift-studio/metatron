# grphSpace Redesign Plan

## Goal

Rewrite grphSpace to match the architecture of tbleSpace and dcmntSpace:
- DataPath-based URI decomposition
- Auto-discovered schema from live graph data
- Plain Recs (not mutable Maps) for vertices/edges
- Lazy traversal via `auto_` pointers on OUT/IN fields
- Diff-based writes
- Rewrite infrastructure for pushdown

## Current State (what's broken)

### Structural mismatches with tbleSpace/dcmntSpace

| Concern | tbleSpace / dcmntSpace | grphSpace (current) |
|---------|----------------------|---------------------|
| Row model | Plain Rec from ResultSet | `ElementMap extends AbstractMap<Uri,Obj>` — the vertex IS a Map |
| Schema | Auto-discovered via JDBC / MongoDB sampling | Hand-coded `modernSchema.java` per dataset |
| URI decomposition | `DataPath.ofSpaceRelative()` | Hardcoded `"V"`/`"E"` string checks |
| Write path | Diff-based: read→compare→UPDATE changed | Manual property iteration + special-cased edge creation |
| Traversal | N/A (SQL joins) | Imperative TinkerPop calls inside InstSet lambdas |
| Constructor | `of(Map<Obj,Obj>, fURI)` | `of(Rec, fURI)` — inconsistent signature |

### Current files (18 files, ~3,000 LOC)

```
grph/
  grphInstSet.java          — instruction set with V_V_FUNCTION/V_E_FUNCTION
  grphFluent.java           — fluent builder
space/
  grphSpace.java            — main space (extends AbstractSpace<Graph>)
  ElementMap.java           — AbstractMap<Uri,Obj> wrapping TinkerPop Element
  VertexMap.java            — vertex-specific ElementMap
  EdgeMap.java              — edge-specific ElementMap
  RefVertex.java            — synthetic vertex for cross-space references
  schema/
    modernSchema.java       — hardcoded schema for "modern" dataset
tp3/
  mGraph.java, mVertex.java, mEdge.java, mElement.java,
  mProperty.java, mVertexProperty.java, mFeatures.java,
  mVariables.java, mIoRegistry.java
io/
  ObjTP3Serializer.java     — TinkerPop Element ↔ mtron Obj serializer
```

## Target Architecture

### Schema auto-discovery: `ExistingGraphSchema`

New class following the same pattern as `ExistingTableSchema` / `ExistingCollectionSchema`:

```
EXISTING SCHEMA TEMPLATE (now applied to all three spaces):

initialize(Backend)
  ├── discoverEntities(Backend)                    — WHAT exists (tables, collections, labels)
  │     for each entity:
  ├──     inferPropertyTypes(Backend, entity)      — TYPE of each property
  ├──     buildPropertyMetadata(typeCounts, count) — AGGREGATE
  └──     discoverReferences(Backend, entity)      — CROSS-ENTITY links (FKs, DBRefs, edge directions)
```

**grphSpace slot-in:**

```
ExistingGraphSchema.initialize(Graph):
  discoverEntities(Graph):
    → IteratorUtil.stream(graph.vertices()).map(Element::label).dedup()
    → IteratorUtil.stream(graph.edges()).map(Element::label).dedup()
    → produce LabelMetadata per label

  inferPropertyTypes(Graph, label, ElementType):
    → sample N elements (e.g., 100) by label
    → collect all property keys, read Java types
    → map: String→str::T, Integer→jnt::T, Long→jnt::T, Double→real::T, Boolean→bool::T
    → if property absent in some samples → .maybe()
    → compute probability = observedCount / elementCount

  discoverReferences(Graph, label):   (edges only)
    → sample edges of this label
    → record OUT vertex labels + IN vertex labels
    → produce EdgeDirectionMetadata(Direction.OUT/IN, toLabel)
```

**Records (aligned naming) — all three spaces now share `PropertyMetadata`:**

```java
// === ExistingGraphSchema (new) ===
public record LabelMetadata(String dbName, String label, ElementType elementType,
    List<PropertyMetadata> properties, List<EdgeDirectionMetadata> edgeDirections) {}
public record PropertyMetadata(String path, Class<?> javaType, double probability) {}
public record EdgeDirectionMetadata(Direction direction, String toLabel) {}
public enum ElementType { VERTEX, EDGE }
```

**Naming alignment across spaces (after renames applied):**

| Template method | tbleSpace | dcmntSpace | grphSpace |
|---|---|---|---|
| Schema class | `ExistingTableSchema` | `ExistingCollectionSchema` | `ExistingGraphSchema` |
| Init entry point | `initialize(Connection)` | `initialize(MongoDatabase)` | `initialize(Graph)` |
| WHAT exists | `discoverEntities(Connection)` | `discoverEntities(MongoDatabase)` | `discoverEntities(Graph)` |
| TYPE of each | *(JDBC gives types)* | `inferPropertyTypes(db, coll)` | `inferPropertyTypes(graph, label, type)` |
| AGGREGATE | *(inline in discoverEntities)* | `buildPropertyMetadata(counts, n)` | `buildPropertyMetadata(counts, n)` |
| CROSS-ENTITY | `discoverReferences(conn, catalog, table)` | `discoverReferences(coll, fields)` | `discoverReferences(graph, label)` |

| Metadata record | tbleSpace | dcmntSpace | grphSpace |
|---|---|---|---|
| Entity | `TableMetadata` | `CollectionMetadata` | `LabelMetadata` |
| Property | `ColumnMetadata` | `PropertyMetadata` | `PropertyMetadata` |
| Reference | `ForeignKeyMetadata` | `ReferenceMetadata` | `EdgeDirectionMetadata` |

**Renames already applied:**
- dcmntSpace: `FieldMetadata→PropertyMetadata`, `inferFieldTypes→inferPropertyTypes`, `detectReferences→discoverReferences`, `buildFieldMetadata→buildPropertyMetadata`, extracted `discoverEntities`
- tbleSpace: `discoverTableSchemas→discoverEntities`, extracted `discoverReferences`

### DataPath-based URI model

Labels ARE collection names. This is the key unification:

```
g:person/1           → collection=person, entry=1
g:person/+           → collection=person, entryIsWildcard
g:person/+/name      → collection=person, entry=+, field=name
g:person/+=?=[name=>'marko']  → where() filter
g:knows/7            → collection=knows, entry=7
g:knows/+/IN         → collection=knows, entry=+, field=IN
g:knows/+/IN/name    → traversal: edge → IN vertex → name property
g:person/1/OUT/knows/IN/name  → who does person 1 know?
```

### Vertices and edges as plain Recs

Drop `ElementMap extends AbstractMap<Uri, Obj>`. `directReader()` returns `IdObj(fURI, Rec)`.

A vertex Rec:
```
[
  id=>1,
  label=>'person',
  name=>'marko',
  age=>29,
  OUT=>auto_(() -> vertex.edges(Direction.OUT).map(e -> EdgeMap.edgeToRec(e))),
  IN=>auto_(() -> vertex.edges(Direction.IN).map(e -> EdgeMap.edgeToRec(e)))
]
```

An edge Rec:
```
[
  id=>7,
  label=>'knows',
  weight=>0.5,
  OUT=>auto_from(V/1),    — the source vertex
  IN=>auto_from(V/2)       — the target vertex
]
```

**OUT/IN are `auto_`/`auto_from_` pointers** — the existing lazy resolution infrastructure. A vertex with 200K edges costs nothing unless `rec.at(OUT)` is explicitly accessed. `rec.elements()` (and `plus`, `forEach`, `keys`) skip auto fields. No new visibility convention needed.

### directReader / directWriter

**directReader — follow DataPath, return Recs:**

```java
directReader() → (pattern) → {
    DataPath dp = resolveDataPath(pattern);  // via ExistingGraphSchema
    if (dp.entryIsWildcard())
        → stream all elements of label, produce Recs
        → if dp.hasField() && !dp.fieldIsWildcard()
            → project just that property
    else
        → lookup by ID, produce Rec
        → if dp.hasField()
            → resolve field (property read or OUT/IN traversal)
}
```

**directWriter — diff-based:**

```java
directWriter() → (pattern, obj) → {
    if (obj.isNoObj())
        → read current element, call Element.remove()
    else
        → read current element as Rec
        → diff incoming Rec vs current Rec
        → only write changed properties (add/update/remove)
        → edge additions via addE instruction (separate path)
}
```

### graphWalker — traversal as lazy field resolution

Replace `V_V_FUNCTION`/`V_E_FUNCTION` in grphInstSet with a `graphWalker`:

- `readField(vertexRec, OUT)` → lazy stream of outgoing edge Recs
- `readField(edgeRec, IN)` → lazy auto_from to target vertex
- `readField(vertexRec, OUT/knows)` → filter edges by label
- `readField(edgeRec, IN/name)` → traverse IN + read name property

All lazy — traversal only fires when the stream is consumed.

### Rewrite infrastructure

| Rewrite | Pattern | Pushdown |
|---------|---------|----------|
| vertexCount | `*g:person/+.count()` | `graph.traversal().V().hasLabel('person').count().next()` |
| edgeCount | `*g:knows/+.count()` | `graph.traversal().E().hasLabel('knows').count().next()` |
| labelFilter | `*g:person/+=?=[name=>'marko']` | `.has('name', 'marko')` pushdown |
| propertyProjection | `*g:person/+/name` | only read `name` property |

### Boot config simplification

```
# OLD — hand-crafted routes per dataset:
grphspace::[pattern => /h/#,
      route    => [</h/+/>=><>],
      native   => [factory => mfactory::[=>], load => grateful]]@/sys/space/grateful;

grphspace::[pattern => g:#,
      route    => [g:V/+/mail => </storage/mail/${>>1}>,
                   g:V => V, g:E => E, g:S => /m/grph/schema/modern],
      native   => [factory => mfactory::[=>], load => modern]]@/sys/space/modern;

# NEW — same pattern as tbleSpace/dcmntSpace:
grphspace::[pattern => g:#,
            host    => <tinker:modern>,
            table   => [,],               -- auto-discover labels
            route   => [g: => <>]]
            @/sys/space/modern;
```

## Files to Delete

| File | Reason |
|------|--------|
| `space/ElementMap.java` | Replaced by plain Recs with auto_ fields |
| `space/VertexMap.java` | Same |
| `space/EdgeMap.java` | Same |
| `space/RefVertex.java` | Cross-space refs handled by auto_from |
| `space/schema/modernSchema.java` | Replaced by ExistingGraphSchema auto-discovery |
| `tp3/mGraph.java` through `tp3/mIoRegistry.java` (9 files) | TinkerPop wrappers not needed without ElementMap |

## Files to Create

| File | Purpose |
|------|---------|
| `space/ExistingGraphSchema.java` | Label/property auto-discovery + schema InstSet generation |
| `space/GraphSchema.java` | Interface: `initialize(Graph)`, `read()`, `write()`, `version()` |

## Files to Rewrite

| File | Scope |
|------|-------|
| `space/grphSpace.java` | Full rewrite — same pattern as tbleSpace/dcmntSpace constructors |
| `grphInstSet.java` | Drop V_V_FUNCTION/V_E_FUNCTION; add traversal via auto_ fields + rewrites |
| `io/ObjTP3Serializer.java` | Simplify to vertex/edge → Rec conversion |
| `grphFluent.java` | Update for new types |

## Files to Rename (existing code alignment)

| File | Current name | New name |
|------|-------------|----------|
| dcmntSpace | `inferFieldTypes(...)` | `inferPropertyTypes(...)` |
| dcmntSpace | `detectReferences(...)` | `discoverReferences(...)` |
| dcmntSpace | inline collection scan in `initialize` | extract to `discoverEntities(MongoDatabase)` |
| dcmntSpace | `FieldMetadata` | `PropertyMetadata` |

## Migration Sequence

1. **Rename alignment** — `inferFieldTypes→inferPropertyTypes`, `detectReferences→discoverReferences`, `FieldMetadata→PropertyMetadata` in dcmntSpace. No behavior change.
2. **ExistingGraphSchema** — auto-discovery class. Zero dependency on other changes. Test independently.
3. **New grphSpace constructor** — `initializeSchema(graph)` + `initializeLabelMapping(graph)`, slots into existing AbstractSpace pattern.
4. **New directReader** — DataPath-based, returns Recs via ExistingGraphSchema.
5. **New directWriter** — diff-based property writes.
6. **grphInstSet** — drop ElementMap-dependent instructions, add traversal via auto_ fields, add rewrites.
7. **Update boot.mtron** — simplified config.
8. **Delete dead files** — ElementMap, VertexMap, EdgeMap, RefVertex, tp3/*, modernSchema.
