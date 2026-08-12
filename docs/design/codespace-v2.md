# CodeSpace v2: Redirect-Indexed CST Agent Environment

CodeSpace is a **redirect index** — a space mapping structural code URIs to TreeSitter CST rec::T nodes via `!*` redirects. It is not a new space type. All mechanics are existing metatron primitives composed.

## 1. Three-Layer Architecture

```
Layer 1: Redirect Index (codespace)
    cs:com.example.mTool/methods/toSkill/body/text → !*<cst:.../method/body/text>
    A flat map of structural URI → CST URI. Query params pass through.

Layer 2: CST Canonical (TreeSitter parse)
    <cst:.../method/text> = "public Skill toSkill() { ... }"
    The single source of truth. Parse creates it, serializer flattens it.

Layer 3: Annotations (durable agent links)
    cs:com.example.mTool/methods/toSkill/notes → agent notes
    cs:com.example.mTool/methods/toSkill/fixes/0001 → linked spike
    Stable URIs survive unrelated edits because paths are structural.
```

## 2. URI Scheme

DataPath-compatible: `db:collection:entry:field`. Any database-backed space can host the CST, enabling rewrite-rule optimization (`sql_count`, etc.).

```
cs:<qualified-classname>/
  class/text              — class declaration + annotations
  class/javadoc           — class-level javadoc
  imports/text            — all imports as str::T
  fields/<name>/text      — field declaration
  fields/<name>/javadoc   — field javadoc
  methods/<name>/text     — method signature + body
  methods/<name>/javadoc  — method javadoc
  methods/<name>/lastEdit — timestamp
  methods/<name>/notes    — agent annotations
  methods/<name>/fixes/+  — linked fixes
```

## 3. Read/Write/Concurrency Primitives

| Primitive | Role |
|---|---|
| `!*` redirect | Transparent CST resolution via redirect index; agent is blind to TreeSitter encoding |
| `lineq` | Range-based read/write on any `str::T` field within a CST node |
| `lockq` | Recursive (`#`) URI-pattern locking; conflicting writes throw |
| `subq` | Subscription on structural patterns fires on CST mutation |
| `>>=` | Structural rec mutation through the CST |

**Example — read/write flow:**

```
mtron> /usr/marko/codespace/mTool/methods/toSkill/text -> |!*<cst:mTool/methods/toSkill/text>
==>!*<cst:mTool/methods/toSkill/text>

mtron> */usr/marko/codespace/mTool/methods/toSkill/text?lineq=1-5
==>'    public Skill toSkill() {'
     '        DefaultSkill.Builder skill = new DefaultSkill.Builder();'
     '        if (this.has(NAME))'
     '            skill = skill.name(this.at(NAME).uriValue().toString());'
     '        if (this.has(DESC))'
```

Lock and subscribe at any granularity:

```
mtron> cs:com.example.mTool/methods/#?lockq -> lock::[usr=>/usr/agent1,expire=>datetime://...]
mtron> cs:com.example.mTool/methods/#?subq -> sub::[code=>print('changed: ', >>0)]
```

## 4. TreeSitter CST Format

Universal rec::T node format, identical across languages:

```
Node → rec(
  type_  = <node_type>,        // e.g. "method_declaration", "class_declaration"
  text   = <source text>,      // the full text of this node
  out    = [<child nodes>],    // named children
  name   = rec(type_="identifier", text="toSkill")  // optional, on named nodes
)
```

## 5. Serialization

CST rec::T → source file: walk root's top-level `out` array, join each child's `text` field.

```
root.out.map(child → child.text).join('\n') → .java file
```

Not `.sum()` (which would double-count nested text). One-way — serialize only. The reverse path is always TreeSitter parse triggered by file change.

## 6. Multi-Language

`cs:com.example.rust_module/methods/parse/text` works identically. `.rs` files parse through TreeSitter Rust grammar, producing the same CST rec::T format. The redirect index is language-agnostic. A `.as(rust::T)` caster at the CST level is the only language-specific artifact.

```
<fs:Foo.java>.as(java::T).as(java_class::T)
 ^str::T        ^CST rec::T    ^validated class metadata

<fs:pom.xml>.as(xml::T).as(java_project::T)
 ^str::T        ^xml rec::T   ^dependencies, source roots, module list
```

TreeSitter grammar management via `:treesitter pull <lang>` (like `docker pull` or `ollama pull`): downloads, caches, and registers grammars in metatron space for parser use.

## 7. What It Is NOT

- Not a version control system (use git)
- Not a semantic analyzer (TreeSitter is structural; LSP bridge can be added later via `.as(java_semantic::T)`)
- Not a build system (`bash -c 'mvn clean install'` handles that, with dckrSpace for containerized builds)
- Not a multi-database aggregator (vecSpace/grphSpace are optional enrichment bolted on later via `.as()` chains)

## 8. Extraction: `redirectSpace`

Codespace is the first test of a more general primitive: **`redirectSpace`** — a space that maintains a mapping between two URI subspaces and mediates read/write/lock/subscribe across them.

```
redirectSpace(
  pattern = "cs:{class}/{member}/{field}"  →  target = "cst:{path}/{member}/{field}",
  mapping = <bidirectional URI resolver>
)
```

| Concern | Mechanism |
|---|---|
| Read-through | `*src_uri` → deref `!*target_uri` transparently |
| Write-through | Write to source URI → follows redirect → writes to target |
| Re-index on change | `subq` on target pattern → re-evaluate mapping → update redirects |
| Lock translation | `lockq` on source pattern → translated to locks on target URIs |
| Query passthrough | `?lineq=3-7` on source → applied at target |

The mapping is a **functor** between two URI categories: objects (URIs) in the source subspace map to objects in the target subspace, and the structural relationships — containment (`/`), recursion (`#`), query (`?`) — are preserved by the mapping. A **homomorphism** preserves the operations: `read(src)` = `read(map(src))`, `lock(src)` = `lock(map(src))`, `subscribe(src)` = `subscribe(map(src))`.

Codespace is the motivating instance: a projection from the narrow structural subspace (`cs:com.Foo/methods/bar/text`) into the full CST namespace. But the same space type serves entity aliases, API versioning, shard routing — any scenario where one URI subspace is a structured view onto another.

## 9. Future Enrichment Ideas

Cherry-picked from the original `codespace_design.md`, adapted for the redirect-index model.

### 9.1 `project::T` — Project-Level Container

A typed rec representing the project as a first-class metatron object:

```
project::[
  path     => <file:///home/user/myproject>,
  language => java,
  status   => indexed,
  exclude  => ['target/', '.git/'],
  metrics  => [classes => 0, methods => 0, loc => 0]
]@/sys/codespace/projects/myproject;
```

Instantiated via `.as()` chain: `<fs:pom.xml>.as(xml::T).as(java_project::T)` or manually. Holds project-level metadata, exclude patterns, and indexing status.

### 9.2 Initial Index via Background Thread

`subq` handles reactive re-indexing on file change, but the initial project load is a batch operation. A `thread::T` worker walks the directory tree, parses all `.java` files through TreeSitter, and populates the redirect index and CST space. Status tracked in `project::T.status` (initializing → indexing → ready → stale).

### 9.3 Semantic Search (`?q=`)

A `?q=` query parameter on codespace URIs resolving through vecSpace embeddings. Agents describe intent rather than matching names:

```
mtron> */sys/codespace/projects/myproject/search?q="cache eviction policy"
```

Returns a list of method URIs ranked by semantic similarity. Requires a vecSpace populated with method bodies and javadoc — enrichment bolted on via `.as()` chain after the base codespace is stable. Not in scope for v1.

### 9.4 Exclude Patterns

Source roots contain `target/`, `.git/`, generated code. The project container carries an `exclude` field — list of glob patterns filtered during directory walk and watch. Simple, practical, needed from day 1.

### 9.5 Agent Navigation Patterns

Concrete examples of how agents navigate the codespace URI namespace:

```
# From semantic intent to source
*> /sys/codespace/projects/myproject/search?q="cache eviction"
*> ./methods/evict/text                                 # read the method

# From method to its callers (requires grphSpace enrichment)
*> /sys/codespace/projects/myproject/classes/Cache/methods/evict/callers

# Structural grep — all parse() methods across the project
*> /sys/codespace/projects/myproject/classes/+/methods/parse/text

# Annotate a finding
*> /sys/codespace/projects/myproject/classes/Cache/methods/evict/notes << rec(
     agent=@agent,
     severity=warn,
     text="race condition in eviction loop")
```

The URI namespace is the query language. Structural patterns (`+`, `#`) replace grep.

## 10. `redirectSpace` as Inst Composition

The core insight: a redirect is an **Inst**. Not a static URI mapping — a computation from one subspace to another. Every codespace URI resolves to an Inst whose `code` dereferences target URIs and transforms the results.

```mtron
# 1:1 passthrough
cs:com.Foo/methods/bar/text = instLambda(() =>
  *<cst:/project/Foo/methods/bar/text>
)

# 1:1 with transform
cs:com.Foo/methods/bar/signature = instLambda(() =>
  *<cst:/project/Foo/methods/bar/text>.as(java_method::T).at(signature)
)

# Multi-source composition
cs:com.Foo/summary = instLambda(() => rec(
  name       => *<cst:Foo/class/name>,
  methodCt   => *<cst:Foo/methods/#>.count(),
  imports    => *<cst:Foo/imports/text>,
  lastChange => *<cst:Foo/.>.max(.at(lastEdit))
))
```

`redirectSpace.read(uri)` evaluates the Inst at that URI. `redirectSpace.write(uri, value)` applies the inverse. The space is a container of Insts, not a container of data.

### 10.1 URI Template Binding

Source URI path components are bound to Inst parameters via URI templates:

```mtron
cs:${pkg}/${class}/methods/${method}/text = instLambda(() =>
  *<cst:/project/src/${pkg}/${class}.java/methods/${method}/text>
)
```

`marko.map(/usr/${_ * java}/MyClass)` → `/usr/marko/java/MyClass`. The template captures path segments as variables, passed to the Inst body.

### 10.2 Inverse (`invq`)

An Inst that projects source → target can also declare how to project writes back. The inverse is stored as a query parameter on the Inst, analogous to `docq` for documentation:

```mtron
*/m/inst/sum?docq
==>docs::[
  obj=>sum?int<=int{*}(),
  dom=>'zero or more ints',
  rng=>'a single plus reduced int',
  desc=>'adds the lhs int stream into a single int',
  example=>['{1,2,3,4}.sum() [-- 10 --]']]

# invq specifies the write-back operation
cs:com.Foo/methods/+/text?invq
==>inv::[
  obj=>...,
  write=>(lhs, result) -> @target.?lineq=lhs.?lineq >>= result]
```

`invq` is self-describing — it answers "how do I write through this Inst back to the source?" The inverse can be:
- A 1:1 passthrough: write to source → write to target
- A transform: write to source → apply inverse transform → write to target
- A deconstruction: write to source → distribute pieces to multiple targets

### 10.3 Memoization (`memoizeq`)

Expensive Insts (multi-source composition, counts, scans) can self-declare their caching policy:

```mtron
cs:com.Foo/summary?memoizeq
==>memoization::[
  inst=>cs:com.Foo/summary,
  args=>${class},
  lhs=>#{?}::T,      // the URI pattern being read
  rhs=>#{*}::T]      // the cached result
```

The memoization collection stores `[inst, args, lhs, rhs]` entries. A read checks the collection first; a cache hit returns `rhs` without re-evaluating the Inst. Cache invalidation is driven by `subq` on the target URIs — when a dependency changes, the memoized entry is evicted.

The three query parameters — `docq`, `invq`, `memoizeq` — compose on any Inst. An Inst in redirectSpace isn't just a redirect; it's an **invertible, documented, memoized computation** between subspaces. No new syntax. No new type system. Just Insts with self-describing annotations.
