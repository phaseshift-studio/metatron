# CodeSpace: IDE-Agnostic Ontological Agent Environment

This document defines the architecture for **CodeSpace** (`codespace` / `CodeSpace`), a general-purpose, IDE-agnostic metatron Space that integrates diverse semantic representations of a codebase. 

Instead of an agent directly querying raw databases, **CodeSpace** acts as a unified portal, orchestrating and aggregating data from `fsSpace`, `grphSpace`, `vecSpace`, and `tbleSpace`. The coordinating container for this integration is the `project::T` type, which spawns background analysis workers (`thread::T`) to parse, index, and populate these databases when instantiated.

---

## 1. Conceptual Architecture

```mermaid
graph TD
    %% CodeSpace Unified Interface
    subgraph CodeSpace Portal [/sys/codespace/]
        proj[project::T]
        backbone[High-Level Backbone Structure]
    end

    %% Analysis Pipelines
    subgraph Indexing Workers [thread::T Spawns]
        ast_t[AST & Symbol Crawler]
        call_t[Call Graph Builder]
        vec_t[Vector Indexer]
    end

    %% Databases
    subgraph Semantic Storage Layer
        fs[fsSpace: Source Code Files]
        grph[grphSpace: Call & Inheritance Graphs]
        tble[tbleSpace: Signatures & Metrics Database]
        vec[vecSpace: Vector Embeddings]
    end

    %% Wiring
    proj -->|Spawns| Indexing Workers
    
    ast_t -->|Parses files & writes| fs
    ast_t -->|Populates signatures| tble
    call_t -->|Populates calls & overrides| grph
    vec_t -->|Generates embeddings| vec

    backbone -->|Aggregate Reads| fs
    backbone -->|Aggregate Reads| grph
    backbone -->|Aggregate Reads| tble
    backbone -->|Aggregate Reads| vec
```

---

## 2. The `project::T` Container

The `project::T` type coordinates the indexing lifecycle. When registered in CodeSpace, it starts the parsing pipeline.

### Type Definition (mtron Concept)
```mtron
project::[
    path    => <file:///home/user/myproject>,
    db      => [
        graph  => <grph:myproject_callgraph>,
        table  => <tble:myproject_signatures>,
        vector => <vec:myproject_embeddings>
    ],
    exclude => ['target/', '.git/', 'node_modules/'],
    status  => 'indexing',
    metrics => [
        classes => 0,
        methods => 0,
        loc     => 0
    ]
]@/sys/codespace/projects/myproject;
```

---

## 3. Worker Threads (`thread::T` Pipelines)

When a `project::T` is initialized, it spawns specialized `thread::T` processes that run concurrently:

1.  **AST & Symbol Crawler:**
    *   Scans the directory tree.
    *   Extracts structural symbols (class names, interfaces, methods, annotations, and parameters) using a compiler/AST parser.
    *   Inserts signatures and metadata into `tbleSpace`.
2.  **Call Graph Builder:**
    *   Analyzes call references inside method blocks.
    *   Resolves polymorphic call sites using the type inheritance tree.
    *   Populates vertices (methods) and edges (calls, overrides) in `grphSpace`.
3.  **Vector Embeddings Indexer:**
    *   Extracts Javadocs, comments, and method bodies.
    *   Computes semantic embeddings.
    *   Populates `vecSpace` for high-dimensional semantic search.

---

## 4. The High-Level Backbone Layout

CodeSpace exposes a virtual URI namespace that aggregates the underlying databases. Under the hood, dereferencing these paths triggers automated `!*` resolution and database lookups.

### Target URI Layout

| URI Address | Read (`*`) Return Type | Underlying Database Resolution |
| :--- | :--- | :--- |
| `/sys/codespace/projects/+` | `project::T` (Record) | Read index status, metrics, paths. |
| `/sys/codespace/projects/+/packages/#` | `Relation (Uri => Rec)` | Packages matching wildcard structure from `tbleSpace`. |
| `/sys/codespace/projects/+/classes/+` | `Rec` (Class properties, fields, methods) | Integrates class metadata from `tbleSpace` & source from `fsSpace`. |
| `/sys/codespace/projects/+/classes/+/methods/+` | `Rec` (Signature, body, callers, callees) | Combines source (`fsSpace`), calls (`grphSpace`), and docs (`vecSpace`). |
| `/sys/codespace/projects/+/search?q=...` | `Lst (Rec)` of matches | Resolves semantic query via `vecSpace`. |

---

## 5. Agent Navigation via Auto-From Resolution

By aggregating these databases behind a virtual CodeSpace, agents can navigate relationships in a fluid, unified way.

### Querying Method Details & Callers (Syntax Concept):
```mtron
# 1. Fetch method record
method_info = *</sys/codespace/projects/myproject/classes/MemSpace/methods/read>;

# 2. Get its callers directly from the record (delegated to grphSpace by CodeSpace)
callers = method_info/callers;

# 3. Read the source code of the first caller
first_caller_source = *<first_caller/path>;
```

### Semantic-to-Graph Jump:
An agent can find a method semantically and immediately trace its downstream dependencies:
```mtron
# 1. Search for caching logic:
results = *</sys/codespace/projects/myproject/search?q="cache eviction policy">;

# 2. Grab the best match method URI:
best_match = results.take(1)/method_uri;

# 3. Find what calls this method:
callers = *<best_match>/callers;
```

---

## 6. Implementation Strategy

To implement this:
1.  **Define `codespace` as a Space:**
    Create `studio.phaseshift.metatron.isa.mach.io.space.codespace` (extending `AbstractSpace`).
2.  **Define Class/Method Types:**
    Create native types for `project::T` and the structural schema components.
3.  **Background Thread Integration:**
    Use `java.util.concurrent` to orchestrate crawler workers. We can use Tree-sitter Java bindings or direct AST parsing (e.g. using JavaParser or the built-in Compiler API) to populate the databases.
