# Mtron Data Model Encoding Strategies

## Executive Summary

**The Discovery:** Path navigation for nested mtron objects is **already implemented** in `Space.Helper.unrollPoly()`. We don't need to build new infrastructure - we just need to use what exists!

**The Goal:** Get `tbleSpace` and `docSpace` to pass `AbstractSpaceTest.testMonoReadWrite()` by adding one pattern to their `directReader()` methods.

**The Fix:** Add poly unrolling (5 lines of code per space):
```java
if (kv.obj().isPoly()) {
    results.addAll(Space.Helper.unrollPoly(kv.furi(), kv.obj().as(), pattern.asNode()));
}
```

**The Payoff:** Once this works, redirects become trivial - just store `[target => !*uri]` and the Router handles the rest. No special redirect code needed.

---

## Problem Statement

**Core Question:** How to encode mtron's rich data model (nested polys with mono leafs, cross-references, and code) in different database backends?

The fundamental challenge is creating a **universal encoding strategy** that works across:
- Graph databases (TinkerPop)
- Relational databases (SQL)
- Document databases (MongoDB)
- Key-value stores

Once we solve universal encoding, many higher-level features become natural consequences:
- **Cross-database references** (`!*` resolution)
- **Redirects** (just a `redirect::T` rec with a `target` field)
- **Path navigation** (`*xyz/a/d`)
- **Bidirectional traversal** (`>>` and `<<`)
- **Code storage** (instructions as data)

### Key Insight

Rather than building redirect-specific infrastructure, we should:
1. **Solve universal encoding first** - Get mtron data structures working uniformly across all backends
2. **Redirect becomes just data** - A `redirect => [target => !*uri]` is just a regular mtron Rec
3. **Leverage existing !* resolution** - The router already knows how to follow cross-references
4. **No special cases needed** - No Q processors, no redirect-specific tables, no backend-specific redirect logic

This approach means redirect is an **emergent property** of good encoding, not a separate feature to maintain.

## Mtron Data Model

```mtron
// Mono (atomic)
abc -> 324
xyz -> 'hello'
def -> 2.5

// Poly (collections)
rec -> [a=>1, b=>2]
lst -> [1, 2, 3]
rel -> a=>b

// Nested
nested -> [a=>[b=>[c=>2], d=>'abc']]

// References
ref -> !*other/path
```

---

## Strategy 1: Graph Database (TinkerPop)

### Current Implementation: Colon-Prefix Convention

**Key Insight:** TinkerPop vertices already support properties (key-value pairs). Mtron leverages this by using a **colon prefix convention** to distinguish between simple values and complex mtron objects.

**Encoding Rules:**
- **Simple properties** (mono, non-call): Store directly as vertex properties
- **Complex properties** (poly, call): Prefix key with `:` and serialize value using `ObjCleanStringSerializer`

**Example from `ElementMap.java`:**
```java
// Writing properties
public Object key() {
    final Obj obj = (Obj) value;
    if (obj.isMono() && !obj.isCall())
        return ((Uri) key).uriValue().toString();  // Simple: "name"
    return makeMeta(((Uri) key).uriValue().toString());  // Complex: ":address"
}

public Object value() {
    final Obj obj = (Obj) value;
    if (obj.isMono() && !obj.isCall())
        return obj.jvm();  // Store raw value: "marko"
    return SERIALIZER.write(obj);  // Serialize: "[home=>[city=>'santa fe']]"
}

// Reading properties
final Obj value = key.toString().startsWith(":")
    ? SERIALIZER.read(p.value().toString())  // Deserialize complex
    : MObjFactory.of().toObj(p.value());     // Use raw value
```

### Two Encoding Modes

#### Mode 1: Hierarchical Properties (Flat Storage)

Store nested paths as separate vertex properties:

```mtron
*g:V/1>>=[address/home/city => 'santa fe',
          address/work/city => 'nomansland']
```

**TinkerPop Storage:**
```java
V[id=1,
  "address/home/city" = "santa fe",      // Simple property
  "address/work/city" = "nomansland"]    // Simple property
```

**Benefits:**
- ✅ Direct property access: `vertex.property("address/home/city")`
- ✅ Pattern matching: `*g:V/+.>>address/+/city`
- ✅ No serialization overhead for leaf values

#### Mode 2: Nested Properties (Serialized Storage)

Store nested structure as a single serialized property:

```mtron
*g:V/1>>=[address=>[home => [city =>'santa fe', state=>NM],
                    work => !*<../home>]]
```

**TinkerPop Storage:**
```java
V[id=1,
  ":address" = "[home=>[city=>'santa fe',state=>NM],work=>!*<../home>]"]
```

**Benefits:**
- ✅ Preserves structure
- ✅ Supports references (`!*<../home>`)
- ✅ Single property for entire nested object

### Approach: Vertex-per-Value with Structural Edges (Alternative)

**Note:** This is an alternative approach not currently implemented, but documented for comparison.

Every mtron value (mono or poly) becomes a vertex. Structure is encoded in edges.

### Encoding Rules

**1. Root Object (Top-level)**
```mtron
*xyz -> [a=>[b=>[c=>2], d=>'abc']]
```

```
V[id=xyz, _type=rec, _root=true]
```

**2. Nested Records**
```
V[xyz] --[_key:a]--> V[xyz/a, _type=rec]
         --[_key:b]--> V[xyz/a/b, _type=rec]
                       --[_key:c]--> V[xyz/a/b/c, _type=int, _value=2]
         --[_key:d]--> V[xyz/a/d, _type=str, _value='abc']
```

**3. Lists**
```mtron
*lst -> [1, 2, 3]
```

```
V[lst, _type=lst] --[_idx:0]--> V[lst/0, _type=int, _value=1]
                  --[_idx:1]--> V[lst/1, _type=int, _value=2]
                  --[_idx:2]--> V[lst/2, _type=int, _value=3]
```

**4. Relations**
```mtron
*rel -> a=>b
```

```
V[rel, _type=rel] --[_dom]--> V[rel/dom, _type=str, _value='a']
                  --[_rng]--> V[rel/rng, _type=str, _value='b']
```

**5. Cross-References**
```mtron
*doc1 -> [ref => !*doc2/path]
```

```
V[doc1] --[_key:ref]--> V[doc1/ref, _type=uri, _ref=true, _target='doc2/path']
```

When traversed, the `_ref=true` flag triggers resolution via Router.

### Vertex Properties Schema

```java
// Container vertex (poly)
{
  id: "xyz/a",           // Path-based ID
  _type: "rec",          // mtron type
  _root: false,          // Is this a root object?
  _mtron_tid: "/m/rec",  // Type ID
  _mtron_vid: "xyz/a"    // Virtual ID
}

// Leaf vertex (mono)
{
  id: "xyz/a/b/c",
  _type: "int",
  _value: 2,             // Actual value
  _mtron_tid: "/m/int",
  _mtron_vid: "xyz/a/b/c"
}

// Reference vertex
{
  id: "doc1/ref",
  _type: "uri",
  _ref: true,            // This is a reference
  _target: "doc2/path",  // Target URI
  _mtron_tid: "/m/uri"
}
```

### Edge Labels

- `_key:fieldname` - Record field
- `_idx:0` - List index
- `_dom` - Relation domain
- `_rng` - Relation range
- Custom labels - User-defined edges (for graph data)

### Implementation

```java
// In grphSpace.java
public void put(fURI vid, Obj obj) {
    Vertex root = getOrCreateVertex(vid);
    encodeObject(root, vid, obj);
}

private void encodeObject(Vertex parent, fURI path, Obj obj) {
    parent.property("_type", obj.tid().toString());
    parent.property("_mtron_tid", obj.tid().toString());
    parent.property("_mtron_vid", path.toString());

    if (obj.isMono()) {
        // Store value directly
        parent.property("_value", obj.jvm());
    } else if (obj.isRec()) {
        // Create child vertices for each field
        obj.asRec().jvm().forEach((key, value) -> {
            fURI childPath = path.extend(key.uriValue().toString());
            Vertex child = getOrCreateVertex(childPath);
            parent.addEdge("_key:" + key.uriValue(), child);
            encodeObject(child, childPath, value);
        });
    } else if (obj.isLst()) {
        // Create child vertices for each element
        int idx = 0;
        for (Obj elem : obj.asLst().lstValue()) {
            fURI childPath = path.extend(String.valueOf(idx));
            Vertex child = getOrCreateVertex(childPath);
            parent.addEdge("_idx:" + idx, child);
            encodeObject(child, childPath, elem);
            idx++;
        }
    } else if (obj.isRel()) {
        // Create domain and range vertices
        Vertex dom = getOrCreateVertex(path.extend("dom"));
        Vertex rng = getOrCreateVertex(path.extend("rng"));
        parent.addEdge("_dom", dom);
        parent.addEdge("_rng", rng);
        encodeObject(dom, path.extend("dom"), obj.asRel().first());
        encodeObject(rng, path.extend("rng"), obj.asRel().second());
    }
}

public Obj read(fURI vid) {
    Vertex vertex = getVertex(vid);
    if (vertex == null) return noobj();
    return decodeObject(vertex);
}

private Obj decodeObject(Vertex vertex) {
    String type = vertex.value("_type");

    switch (type) {
        case "int":
            return jnt((Long) vertex.value("_value"));
        case "str":
            return str((String) vertex.value("_value"));
        case "real":
            return real((Double) vertex.value("_value"));
        case "rec":
            Map<Obj, Obj> map = new LinkedHashMap<>();
            vertex.edges(Direction.OUT).forEachRemaining(edge -> {
                if (edge.label().startsWith("_key:")) {
                    String key = edge.label().substring(5);
                    Obj value = decodeObject(edge.inVertex());
                    map.put(uri(key), value);
                }
            });
            return rec(map);
        case "lst":
            List<Obj> list = new ArrayList<>();
            vertex.edges(Direction.OUT).forEachRemaining(edge -> {
                if (edge.label().startsWith("_idx:")) {
                    Obj value = decodeObject(edge.inVertex());
                    list.add(value);
                }
            });
            return lst(list);
        case "rel":
            Obj dom = null, rng = null;
            Iterator<Edge> edges = vertex.edges(Direction.OUT);
            while (edges.hasNext()) {
                Edge e = edges.next();
                if (e.label().equals("_dom")) dom = decodeObject(e.inVertex());
                if (e.label().equals("_rng")) rng = decodeObject(e.inVertex());
            }
            return rel(dom, rng);
        case "uri":
            if (vertex.property("_ref").isPresent()) {
                // This is a reference - resolve it
                String target = vertex.value("_target");
                return Router.global().read(uri(target).uriValue());
            }
            return uri((String) vertex.value("_value"));
        default:
            return noobj();
    }
}
```

### Path Navigation

```mtron
*xyz/a/d  // Navigate to nested field
```

```java
// Direct vertex lookup by path
Vertex vertex = graph.vertices("xyz/a/d").next();
return decodeObject(vertex);
```

### Pros & Cons

**Pros:**
- ✅ Natural graph representation
- ✅ Efficient path navigation (direct vertex lookup)
- ✅ Can mix mtron structure with native graph edges
- ✅ Supports arbitrary nesting depth

**Cons:**
- ❌ Vertex explosion (one per value)
- ❌ More complex queries for simple data
- ❌ Overhead for flat structures

---

## Strategy 2: Relational Database (SQL)

### Approach: JSON Columns with Path Indexing

Store mtron objects as JSON with generated columns for path-based queries.

### Schema

```sql
-- Main objects table
CREATE TABLE mtron_objects (
    vid VARCHAR PRIMARY KEY,           -- *xyz
    type VARCHAR,                      -- rec, lst, int, etc.
    data JSONB,                        -- Full object as JSON
    created_at TIMESTAMP DEFAULT NOW()
);

-- Path index for nested navigation
CREATE TABLE mtron_paths (
    vid VARCHAR,                       -- Root object ID
    path VARCHAR,                      -- Path like 'a/b/c'
    value_type VARCHAR,                -- Type at this path
    value_text TEXT,                   -- String representation
    value_num NUMERIC,                 -- Numeric value (if applicable)
    FOREIGN KEY (vid) REFERENCES mtron_objects(vid)
);

CREATE INDEX idx_paths ON mtron_paths(vid, path);

-- References table (for cross-document links)
CREATE TABLE mtron_refs (
    source_vid VARCHAR,
    source_path VARCHAR,
    target_vid VARCHAR,
    target_path VARCHAR,
    FOREIGN KEY (source_vid) REFERENCES mtron_objects(vid)
);
```

### Encoding

```mtron
*xyz -> [a=>[b=>[c=>2], d=>'abc']]
```

**mtron_objects:**
```sql
INSERT INTO mtron_objects VALUES (
    'xyz',
    'rec',
    '{"a": {"b": {"c": 2}, "d": "abc"}}'
);
```

**mtron_paths:**
```sql
INSERT INTO mtron_paths VALUES
    ('xyz', 'a', 'rec', NULL, NULL),
    ('xyz', 'a/b', 'rec', NULL, NULL),
    ('xyz', 'a/b/c', 'int', '2', 2),
    ('xyz', 'a/d', 'str', 'abc', NULL);
```

### Path Navigation

```sql
-- *xyz/a/d
SELECT value_text
FROM mtron_paths
WHERE vid = 'xyz' AND path = 'a/d';
```

### Implementation

```java
// In SQL space implementation
public void put(fURI vid, Obj obj) {
    // Store full object as JSON
    String json = objToJson(obj);
    String sql = "INSERT INTO mtron_objects (vid, type, data) VALUES (?, ?, ?::jsonb)";
    stmt.execute(sql, vid.toString(), obj.tid().toString(), json);

    // Generate path index
    generatePathIndex(vid, "", obj);
}

private void generatePathIndex(fURI vid, String basePath, Obj obj) {
    if (obj.isRec()) {
        obj.asRec().jvm().forEach((key, value) -> {
            String path = basePath.isEmpty() ? key.toString() : basePath + "/" + key;
            insertPath(vid, path, value);
            generatePathIndex(vid, path, value);
        });
    } else if (obj.isLst()) {
        int idx = 0;
        for (Obj elem : obj.asLst().lstValue()) {
            String path = basePath + "/" + idx;
            insertPath(vid, path, elem);
            generatePathIndex(vid, path, elem);
            idx++;
        }
    } else {
        // Leaf node - already inserted by parent
    }
}

private void insertPath(fURI vid, String path, Obj value) {
    String sql = "INSERT INTO mtron_paths (vid, path, value_type, value_text, value_num) VALUES (?, ?, ?, ?, ?)";
    stmt.execute(sql,
        vid.toString(),
        path,
        value.tid().toString(),
        value.toString(),
        value.isInt() ? value.intValue() : null
    );
}

public Obj read(fURI vid) {
    if (vid.hasPath()) {
        // Path navigation: *xyz/a/d
        String path = vid.path().stream().skip(1).collect(Collectors.joining("/"));
        String sql = "SELECT value_text, value_type FROM mtron_paths WHERE vid = ? AND path = ?";
        ResultSet rs = stmt.executeQuery(sql, vid.basePath(), path);
        if (rs.next()) {
            return parseValue(rs.getString("value_text"), rs.getString("value_type"));
        }
        return noobj();
    } else {
        // Full object read
        String sql = "SELECT data FROM mtron_objects WHERE vid = ?";
        ResultSet rs = stmt.executeQuery(sql, vid.toString());
        if (rs.next()) {
            return jsonToObj(rs.getString("data"));
        }
        return noobj();
    }
}
```

### Pros & Cons

**Pros:**
- ✅ Compact storage (one row per object)
- ✅ Fast path queries (indexed)
- ✅ Standard SQL tools work
- ✅ JSONB supports native queries

**Cons:**
- ❌ Path index maintenance overhead
- ❌ Deep nesting can be slow
- ❌ JSON parsing overhead

---

## Strategy 3: Document Database (MongoDB)

### Approach: Native Document Mapping

Mtron's model maps almost 1:1 to MongoDB documents.

### Encoding

```mtron
*xyz -> [a=>[b=>[c=>2], d=>'abc']]
```

```javascript
{
    _id: "xyz",
    _type: "rec",
    _mtron_tid: "/m/rec",
    a: {
        _type: "rec",
        b: {
            _type: "rec",
            c: {_type: "int", _value: 2}
        },
        d: {_type: "str", _value: "abc"}
    }
}
```

**Simplified (without type annotations):**
```javascript
{
    _id: "xyz",
    a: {
        b: {c: 2},
        d: "abc"
    }
}
```

### Path Navigation

```javascript
// *xyz/a/d
db.mtron.findOne({_id: "xyz"}, {"a.d": 1})
```

### Cross-References

```javascript
{
    _id: "doc1",
    ref: {
        _type: "uri",
        _ref: true,
        _target: "doc2/path"
    }
}
```

### Implementation

```java
public void put(fURI vid, Obj obj) {
    Document doc = objToDocument(obj);
    doc.put("_id", vid.toString());
    collection.replaceOne(
        Filters.eq("_id", vid.toString()),
        doc,
        new ReplaceOptions().upsert(true)
    );
}

private Document objToDocument(Obj obj) {
    Document doc = new Document();
    doc.put("_type", obj.tid().toString());

    if (obj.isRec()) {
        obj.asRec().jvm().forEach((key, value) -> {
            doc.put(key.toString(), objToDocument(value));
        });
    } else if (obj.isLst()) {
        List<Document> list = new ArrayList<>();
        obj.asLst().lstValue().forEach(elem -> list.add(objToDocument(elem)));
        doc.put("_value", list);
    } else {
        doc.put("_value", obj.jvm());
    }

    return doc;
}

public Obj read(fURI vid) {
    if (vid.hasPath()) {
        // Path navigation
        String path = vid.path().stream().skip(1).collect(Collectors.joining("."));
        Document doc = collection.find(Filters.eq("_id", vid.basePath()))
            .projection(Projections.include(path))
            .first();
        return documentToObj(doc.get(path));
    } else {
        Document doc = collection.find(Filters.eq("_id", vid.toString())).first();
        return documentToObj(doc);
    }
}
```

### Pros & Cons

**Pros:**
- ✅ Most natural mapping
- ✅ Native path queries
- ✅ Minimal overhead
- ✅ Flexible schema

**Cons:**
- ❌ Document size limits (16MB)
- ❌ Deep nesting performance

---

## Comparison Matrix

| Feature | Graph (Current) | Graph (Alt) | SQL | Document |
|---------|-----------------|-------------|-----|----------|
| Nested structures | Colon-prefix serialization | Vertex explosion | JSON + index | Native |
| Path navigation | Property lookup or deserialize | Direct vertex lookup | Indexed paths | Native queries |
| Cross-references | Serialized `!*` refs | Native edges | Join tables | DBRef or manual |
| Type preservation | Prefix convention | Vertex properties | Type columns | Embedded |
| Storage overhead | Low (serialized strings) | High (many vertices) | Medium (index) | Low |
| Query complexity | Low | Medium | High (joins) | Low |
| Best for | **Current implementation** | Graph-heavy data | Relational + mtron | Pure mtron |

---

## Recommended Hybrid Approach

**The current `grphSpace` implementation already uses an optimal hybrid strategy:**

### Current Implementation: Colon-Prefix Convention

```java
// Simple properties: Store directly (no prefix)
vertex.property("name", "marko");
vertex.property("age", 29);
vertex.property("address/home/city", "santa fe");  // Hierarchical property

// Complex properties: Prefix with ':' and serialize using ObjmtronSerializer
vertex.property(":address", "[home=>[city=>'santa fe',state=>NM],work=>!*<../home>]");

// Graph relationships: Use native edges
vertex.addEdge("knows", otherVertex);
```

**This gives you:**
- ✅ Fast property access for simple values (no deserialization)
- ✅ Compact storage for nested data (serialized as string)
- ✅ Native graph traversal for relationships
- ✅ Support for cross-references (`!*` in serialized properties)
- ✅ Hierarchical properties (multi-segment URIs)
- ✅ Nested properties (serialized records)
- ✅ Best of both worlds

### Path Navigation Strategy

**For hierarchical properties:**
```mtron
*g:V/1>>=[address/home/city => 'santa fe']
*g:V/1/address/home/city  // Direct property lookup
```

**For nested properties:**
```mtron
*g:V/1>>=[address=>[home=>[city=>'santa fe']]]
*g:V/1/address/home/city  // Deserialize ":address" then navigate
```

The system automatically chooses based on whether the property key starts with `:`.

---

---

## Future Applications: Cross-Database Redirects

### Why This Section Exists

This section documents a **potential future application** of universal encoding, not a separate feature to implement. It shows how solving the encoding problem naturally enables cross-database references without special infrastructure.

### The Elegant Solution

Once we have universal encoding, cross-database redirects become trivial:

**Store a redirect as a regular mtron Rec:**
```mtron
*g:V/1>>consulted => [target => !*acme:customers/357, since => 2023]
```

**Access the target:**
```mtron
*g:V/1>>consulted>>target  // Router automatically resolves !*acme:customers/357
```

**That's it.** No Q processors, no special tables, no redirect-specific code.

### How It Works

1. **Write**: Store `consulted => [target => !*acme:customers/357]` using whatever encoding that backend uses for Recs
2. **Read**: Retrieve the Rec, access `.target` field
3. **Resolve**: Router sees `!*acme:customers/357` and resolves it automatically
4. **Navigate**: You're now at the customer record in the SQL database

### Why This Is Better Than Special Infrastructure

**What we were considering (complex):**
- Q processor: `?redirect` parameter
- Graph: Special redirect vertex handling
- SQL: Special `_redirects` table
- Document: Special `_redirects` array
- Maintenance: Redirect-specific code in every backend

**What we actually need (simple):**
- Universal encoding: Store mtron Recs uniformly
- Router: Already handles `!*` resolution
- Result: Redirect is just data, not infrastructure

### Potential Generalizations

By waiting to implement redirect, we might discover it's actually an instance of:
- **Lazy object loading** - Any `!*` reference could be lazy-loaded
- **Distributed object graphs** - Objects spanning multiple databases
- **Cross-reference patterns** - General patterns for linking heterogeneous data
- **Something we haven't thought of yet** - The real abstraction might be more powerful

### Example: Bidirectional References

Even bidirectional references become simple:

```mtron
// Forward reference (graph → SQL)
*g:V/1>>consulted => [target => !*acme:customers/357]

// Reverse reference (SQL → graph)
*acme:customers/357>>consultedBy => [target => !*g:V/1]
```

Both are just regular mtron Recs stored using universal encoding. The `>>` operator:
1. Looks up the edge/property
2. Finds a Rec with a `target` field
3. Resolves the `!*` reference
4. Returns the target object

No special redirect logic needed.

---

## Next Steps: Focus on Universal Encoding

The priority is solving **universal encoding** across all backends. Once this works, features like redirects, cross-database references, and complex traversals will emerge naturally.

### Concrete Goal: Pass `AbstractSpaceTest.testMonoReadWrite()`

**Current Status:**
- ✅ **memSpaceTest** - Passes (reference implementation)
- ❌ **tp3SpaceTest** - All tests `@Disabled` (lines 184-257)
- ❌ **tbleSpaceTest** - Has own tests, but doesn't run `testMonoReadWrite()`
- ❌ **docSpaceTest** - All abstract tests `@Disabled` (lines 88-146)

**The Test:** `AbstractSpaceTest.testMonoReadWrite()` (lines 217-238)
- Parameterized test with 50+ test cases
- Tests nested records: `$$ -> [a=>[b=>2,c=>3],d=>4]`
- Tests lists: `$$ -> [a,b,c]`
- Tests path navigation: `*$$/a/b` → `2`
- Tests wildcard patterns: `*$$/+` → all values
- Tests deep nesting: `$$ -> [a,[b,[c,d],e],f]`
- Tests path with wildcards: `*$$/+/+/+` → `{c,d}`

**Why This Matters:**
If all three spaces (grphSpace, tbleSpace, docSpace) can pass this test, it proves:
1. Universal encoding works across graph, SQL, and document backends
2. Path navigation is consistent
3. Nested structures are preserved
4. Type information is maintained
5. Redirects will "just work" as regular mtron objects

### Phase 1: Enable grphSpace to Pass testMonoReadWrite()

**Current State:**
- ✅ Colon-prefix convention implemented in `ElementMap.java`
- ✅ `ObjCleanStringSerializer` for complex objects
- ✅ Two modes: hierarchical (flat paths) and nested (serialized)
- ❌ All `AbstractSpaceTest` tests disabled

**What grphSpace Needs:**

The test writes objects like `*g:V/1 -> [a=>[b=>2,c=>3],d=>4]` and expects to read:
- `*g:V/1` → full record
- `*g:V/1/a` → `[b=>2,c=>3]`
- `*g:V/1/a/b` → `2`
- `*g:V/1/d` → `4`

**Current Problem:**
grphSpace is designed for **graph traversals** (vertices, edges, properties), not general key-value storage. The test expects:
```mtron
*g:V/1 -> [a=>[b=>2,c=>3],d=>4]  // Write a record to a vertex
*g:V/1/a/b                        // Read nested path
```

But grphSpace currently treats `/1` as a vertex ID and `/a/b` as property paths.

**Two Approaches:**

**Option A: Make grphSpace Support General Storage**
- Treat vertex properties as a general key-value store
- Store nested records using colon-prefix convention
- Enable path navigation through nested structures
- **Pro:** grphSpace becomes a general-purpose space
- **Con:** Blurs the line between graph semantics and document storage

**Option B: Keep grphSpace Graph-Specific**
- Keep tests disabled (current state)
- Focus on tbleSpace and docSpace for universal encoding
- grphSpace remains specialized for graph operations
- **Pro:** Clear separation of concerns
- **Con:** Doesn't prove universal encoding works for graphs

**Recommendation:** Start with **Option B** for now, focus on tbleSpace and docSpace first.

### Phase 2: Enable tbleSpace to Pass testMonoReadWrite()

**Current State:**
- ✅ Has comprehensive SQL-specific tests (lines 149-1000+)
- ✅ Supports table mapping (existing tables)
- ✅ Supports key-value storage (`TypedKeyValueSchema`)
- ❌ Doesn't run `testMonoReadWrite()`

**What tbleSpace Needs:**

The test expects to write arbitrary mtron objects to URIs like:
```mtron
*tble/test/x1 -> [a=>[b=>2,c=>3],d=>4]
*tble/test/x1/a/b  // Should return 2
```

**Current Implementation:**
tbleSpace has two modes:
1. **Table mode**: Maps to existing SQL tables (`db:users/1/name`)
2. **Key-value mode**: Uses `TypedKeyValueSchema` for arbitrary storage

**The key-value mode already exists!** (See `testTypedStoragePreservation` lines 821-876)

**What's Missing:**
1. **Path navigation through nested structures**
   - Currently: `*tble/test/rec1` returns full record
   - Needed: `*tble/test/rec1/name` returns just the `name` field
   - Solution: Implement poly unrolling in `tbleSpace.read()`

2. **Wildcard pattern support**
   - Currently: `*tble/test/+` might not work for key-value mode
   - Needed: `*tble/test/+` returns all objects matching pattern
   - Solution: Pattern matching in key-value schema

**Action Items:**
1. Remove `@Disabled` from `testMonoReadWrite()` in `tbleSpaceTest`
2. Run the test and see what fails
3. Implement missing path navigation
4. Implement wildcard pattern support
5. Verify all test cases pass

### Phase 3: Enable docSpace to Pass testMonoReadWrite()

**Current State:**
- ✅ Has comprehensive MongoDB-specific tests (lines 219-999)
- ✅ Stores nested documents naturally
- ✅ Handles all mtron types (Rec, Lst, Int, Str, Real, Bool)
- ❌ All abstract tests `@Disabled` (lines 88-146)

**What docSpace Needs:**

The test expects to write arbitrary mtron objects to URIs like:
```mtron
*mongo:test/x1 -> [a=>[b=>2,c=>3],d=>4]
*mongo:test/x1/a/b  // Should return 2
```

**Current Implementation:**
docSpace already handles nested documents well (see `testNestedDocuments` line 368):
```java
rec(
    uri("name"), str("Eve"),
    uri("address"), rec(
        uri("street"), str("123 Main St"),
        uri("city"), str("Springfield")
    )
)
```

**What's Missing:**
1. **Path navigation through nested structures**
   - Currently: `*mongo:users/user5` returns full document
   - Needed: `*mongo:users/user5/address/city` returns just `"Springfield"`
   - Solution: Implement poly unrolling in `docSpace.read()`

2. **Wildcard pattern support**
   - Currently: `*mongo:users/+` returns all documents in collection
   - Needed: `*mongo:test/+` returns all objects matching pattern
   - Likely already works, just needs testing

**Action Items:**
1. Remove `@Disabled` from `testMonoReadWrite()` in `docSpaceTest`
2. Run the test and see what fails
3. Implement missing path navigation (poly unrolling)
4. Verify wildcard patterns work
5. Verify all test cases pass

**Advantage:**
docSpace should be the **easiest** to get working because:
- MongoDB's document model is almost 1:1 with mtron's Rec/Lst model
- Nested structures are native
- Type preservation is natural
- No impedance mismatch like SQL

### Phase 4: Understanding How memSpace Passes the Tests

**Key Discovery:** The path navigation is **already implemented** in `Space.Helper`!

**How memSpace Works:**

1. **Storage:** Simple `ConcurrentHashMap<fURI, Obj>` (line 78)
   - Stores objects directly at their full URI
   - Example: `*t/test/x1 -> [a=>[b=>2,c=>3],d=>4]`

2. **Reading with Path Navigation:** Uses `Space.Helper.unrollPoly()` (lines 116-118)
   ```java
   // In memSpace.directReader()
   kv.getValue().isPoly() ?
       Space.Helper.unrollPoly(kv.getKey(), kv.getValue().as(), pattern.asNode()).stream() :
       Stream.empty()
   ```

3. **The Magic:** `Space.Helper.unrollPoly()` (Space.java lines 182-203)
   - Takes a poly (Rec or Lst) and a pattern
   - Recursively navigates nested structures
   - Returns matching values at nested paths
   - Example: `unrollPoly(*t/test/x1, [a=>[b=>2]], *t/test/x1/a/b)` → `2`

4. **Wildcard Support:** Built into `unrollPoly()`
   - Pattern `*t/test/+` matches all children
   - Pattern `*t/test/+/+` matches all grandchildren
   - Recursively descends through nested polys

**Why memSpace Passes All Tests:**
- ✅ Stores full objects at base URI
- ✅ Uses `Space.Helper.unrollPoly()` for path navigation
- ✅ Wildcard patterns work automatically
- ✅ No special encoding needed - stores mtron objects directly

**The Pattern for Other Spaces:**

To pass `testMonoReadWrite()`, a space needs to:
1. **Store full mtron objects** at base URIs (not just primitives)
2. **Use `Space.Helper.unrollPoly()`** in `directReader()` for nested path access
3. **Let AbstractSpace handle the rest** via `Space.Helper.resolveRead()`

**What This Means for tbleSpace and docSpace:**

**Current Implementation Analysis:**

**tbleSpace** (lines 298-328):
```java
public Function<fURI, Iterator<IdObj>> directReader() {
    return (pattern) -> {
        // Returns results from schema.read() or existingTableSchema.read()
        return this.schema.read(this.sjvm(), pattern);
    };
}
```
- ✅ Already stores full mtron objects via `TypedKeyValueSchema`
- ❌ Does NOT call `Space.Helper.unrollPoly()` for nested path navigation
- ❌ Schema returns raw objects, no poly unrolling

**docSpace** (lines 242-278):
```java
public Function<fURI, Iterator<IdObj>> directReader() {
    return (pattern) -> {
        // Returns documents from MongoDB
        return Stream.of(IdObj.of(docVID, this.serializer.readRec(doc.toBsonDocument())));
    };
}
```
- ✅ Already stores full mtron objects (Rec) from MongoDB
- ❌ Does NOT call `Space.Helper.unrollPoly()` for nested path navigation
- ❌ Only returns full documents, no nested path access

**The Fix:**

Both spaces need to add the same pattern memSpace uses:

```java
// After getting the base object from storage
if (kv.getValue().isPoly()) {
    // Add unrolled poly results
    results.addAll(Space.Helper.unrollPoly(kv.getKey(), kv.getValue().as(), pattern.asNode()));
}
```

This will enable:
- `*tble:test/obj1/a/b` → navigates to nested value
- `*mongo:users/user1/address/city` → navigates to nested value
- `*tble:test/+` → returns all children (already works)
- `*tble:test/+/+` → returns all grandchildren (will work after fix)

### Phase 5: Validation and Success Criteria

**Success Criteria:**

Universal encoding is complete when:
- ✅ tbleSpace passes all `testMonoReadWrite()` test cases
- ✅ docSpace passes all `testMonoReadWrite()` test cases
- ✅ Path navigation works: `*space:obj/a/b/c` returns nested value
- ✅ Wildcard patterns work: `*space:obj/+` returns all children
- ✅ Type preservation: Round-trip maintains types
- ✅ Nested structures: Deep nesting (5+ levels) works
- ✅ Mixed types: Records containing lists containing records works

**At This Point:**
- Redirects will work without special code: `[target => !*other/obj]`
- Cross-database references will work via Router
- No need for redirect-specific Q processors or tables
- Universal encoding is proven across SQL and document backends

**Future Work (Not Required Now):**
- grphSpace general storage support (if needed)
- Key-value space implementations
- Performance optimization
- Schema migration tools
- Cross-database transaction support

## Summary: The Path Forward

### Immediate Goal
Get **tbleSpace** and **docSpace** to pass `AbstractSpaceTest.testMonoReadWrite()`.

### Why This Matters
1. **Proves universal encoding works** across different backend types (SQL and document)
2. **Validates the approach** before building redirect infrastructure
3. **Identifies common patterns** that can be extracted to base classes
4. **Enables redirects naturally** - once encoding works, `[target => !*uri]` is just data

### What Success Looks Like
```mtron
// Write nested structure to SQL
*tble:test/obj1 -> [a=>[b=>2,c=>3],d=>4]

// Navigate paths
*tble:test/obj1/a/b  // Returns: 2
*tble:test/obj1/d    // Returns: 4

// Wildcard patterns
*tble:test/+         // Returns: all objects in test

// Same thing works in MongoDB
*mongo:test/obj1 -> [a=>[b=>2,c=>3],d=>4]
*mongo:test/obj1/a/b // Returns: 2

// And redirects just work
*tble:test/redirect1 -> [target => !*mongo:test/obj1]
*tble:test/redirect1>>target  // Router resolves !* automatically
```

### Non-Goals (For Now)

These are **not** priorities until universal encoding is solid:
- ❌ Redirect-specific Q processors
- ❌ Special redirect tables or properties
- ❌ Bidirectional edge creation helpers
- ❌ grphSpace general storage (keep it graph-specific)
- ❌ Optimization for specific data shapes
- ❌ Schema migration tools

Once encoding works, we can revisit these and see if they're even needed.

### Next Action: Concrete Implementation Steps

**Step 1: Update tbleSpace.directReader()**

Add poly unrolling after line 323:

```java
@Override
public Function<fURI, Iterator<IdObj>> directReader() {
    return (pattern) -> {
        try {
            // ... existing code ...

            // Get raw results from schema
            Iterator<IdObj> rawResults = this.schema.read(this.sjvm(), pattern);

            // Add poly unrolling for nested path navigation
            List<IdObj> allResults = new ArrayList<>();
            rawResults.forEachRemaining(kv -> {
                allResults.add(kv);  // Add the base object
                if (kv.obj().isPoly()) {
                    // Add unrolled nested paths
                    allResults.addAll(Space.Helper.unrollPoly(kv.furi(), kv.obj().as(), pattern.asNode()));
                }
            });
            return allResults.iterator();
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    };
}
```

**Step 2: Update docSpace.directReader()**

Add poly unrolling after line 275:

```java
@Override
public Function<fURI, Iterator<IdObj>> directReader() {
    return (pattern) -> {
        // ... existing code to get documents ...

        return collectionStream.map(c -> this.database.getCollection(c)).flatMap(collection -> {
            // ... existing code ...

            // Get document stream
            Stream<IdObj> docStream = /* existing document retrieval code */;

            // Add poly unrolling for nested path navigation
            return docStream.flatMap(idObj -> {
                List<IdObj> results = new ArrayList<>();
                results.add(idObj);  // Add the base document
                if (idObj.obj().isPoly()) {
                    // Add unrolled nested paths
                    results.addAll(Space.Helper.unrollPoly(idObj.furi(), idObj.obj().as(), pattern.asNode()));
                }
                return results.stream();
            });
        }).iterator();
    };
}
```

**Step 3: Enable Tests**

In `tbleSpaceTest.java` and `docSpaceTest.java`:
- Remove `@Disabled` from `testMonoReadWrite()`
- Run the tests
- Verify all 50+ test cases pass

**Step 4: Verify Success**

Once tests pass, verify these work:
```mtron
// Nested path navigation
*tble:test/obj1 -> [a=>[b=>2,c=>3],d=>4]
*tble:test/obj1/a/b  // Should return: 2

// Deep nesting
*mongo:test/obj2 -> [a,[b,[c,d],e],f]
*mongo:test/obj2/1/1/0  // Should return: c

// Wildcard patterns
*tble:test/+         // All objects in test
*tble:test/+/+       // All nested values
```

**Step 5: Prove Redirects Work**

Once encoding works, test redirects:
```mtron
// Create redirect
*tble:test/redirect1 -> [target => !*mongo:test/obj1]

// Access target
*tble:test/redirect1>>target  // Router resolves !* automatically
```

No special redirect code needed - it just works!
