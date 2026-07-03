# Multi-Dimensional Ontological Codebase Space in Metatron

This design document outlines a conceptual framework for representing a software codebase as a multi-dimensional, ontological address space inside the **metatron** VM. 

Instead of treating code purely as raw text files, this architecture projects the codebase across multiple semantic layers using native metatron spaces (`fsSpace`, `grphSpace`, `mqttSpace`, `tbleSpace`, `vecSpace`, etc.). Agents can traverse this multi-dimensional space, shifting between file paths, AST nodes, call graph edges, Git history, and dynamic execution traces using query processors (`qprocs`) and standard URI dereferencing.

---

## 1. Unified Space Architecture

```mermaid
graph TD
    subgraph URI Entry Point: /sys/code/
        uri[code URI e.g. /sys/code/myproject/src/main/MyClass.java]
    end

    subgraph Ontological Projections (qprocs / MIME-types)
        fs[fsSpace: application/x-java-source]
        ast[astSpace / tree-sitter: application/x-ast]
        grph[grphSpace: application/x-callgraph]
        git[gitSpace: application/x-git-history]
        vec[vecSpace: application/x-embeddings]
        dbg[debugSpace: application/x-trace]
    end

    uri -->|?mime=text/plain| fs
    uri -->|?mime=application/x-ast| ast
    uri -->|?mime=application/x-callgraph| grph
    uri -->|?mime=application/x-git-history| git
    uri -->|?mime=application/x-embeddings| vec
    uri -->|?mime=application/x-trace| dbg
```

---

## 2. Multi-Space Semantic Backends

By distributing the codebase across different space backends, metatron leverages the unique capabilities of each data model:

### 💾 `fsSpace` (File Representation)
*   **Role:** Raw text and directory hierarchy.
*   **Behavior:** Serves as the base spatial reference point.
*   **URIs:** `/sys/code/project/src/main/studio/phaseshift/MyClass.java`

### 🕸️ `grphSpace` (Call Graph, Inheritance, & Dependency Ontology)
*   **Role:** Representing the structural and semantic edges of the code.
*   **Behavior:** Vertices are classes, methods, and packages. Edges represent calls, overrides, implementation, and package imports.
*   **Example Traversal:** Start at a method vertex and walk incoming call edges (`<<`) or outgoing call edges (`>>`) to find callers/callees.
*   **URIs:** `/sys/code/project/graph/methods/studio.phaseshift.MyClass#myMethod/`

### 🧠 `vecSpace` (Semantic & Concept Search)
*   **Role:** High-dimensional vector index for searching code by meaning, natural language, or conceptual similarity.
*   **Behavior:** Methods and class docstrings are chunked, embedded, and updated on file writes.
*   **Example Traversal:** Search for implementations of concepts rather than exact text strings.
*   **URIs:** `*</sys/code/project/vector?near="connection pooling leak">`

### ⚡ `mqttSpace` (Reactive Event & Agent Coordination)
*   **Role:** Real-time event broker for code changes, compiler states, and agent orchestration.
*   **Behavior:** Local IDE changes, test run failures, or compiler alerts publish events to MQTT topics.
*   **Example Traversal:** An agent subscribes to `mtron:editor:active:change` via `subq` and wakes up automatically to analyze the code the user is typing.
*   **URIs:** `*<mqtt:mtron/editor/change/event>`

### 📊 `tbleSpace` (Relational Analytics & Metrics)
*   **Role:** Indexing signatures, complexity metrics, LOC count, and historical change counts.
*   **Behavior:** Fast tabular queries to discover complex files, security vulnerabilities, or code hotspots.
*   **URIs:** `/sys/code/project/metrics/`

---

## 3. Query Procs & Ontological Transitions

Using query processors, an agent can dereference the same URI in different formats or traverse along ontological boundaries.

### AST Projection (`?ast` or `?mime=application/x-ast`)
Converts the source code into a structured syntax tree representation (via Tree-sitter or JVM Compiler API):
```mtron
# Read the AST of a method
ast = *</sys/code/project/src/main/MyClass.java?ast>;
# Find all parameter nodes of the method:
params = ast.query("//parameter_list/formal_parameter")
```

### Call Graph Projection (`?callgraph` or `?mime=application/x-callgraph`)
Projects the file or method into its callers/callees network:
```mtron
# Find all methods called by MyClass.java
callees = *</sys/code/project/src/main/MyClass.java?callgraph/callees>
```

### Git Churn & History (`?git` or `?mime=application/x-git-history`)
Exposes version control metadata:
```mtron
# Fetch git blame metadata for the selection
blame_info = *</sys/code/project/src/main/MyClass.java?git/blame/selection>
```

---

## 4. Additional Useful Semantic Spaces

Beyond the core database spaces, we envision these high-value additions to the codebase landscape:

### A. ⏳ `debugSpace` (Dynamic Trace Space)
Mapping running JVM runtime traces to the URI space.
*   **Concept:** When a debugger runs, it streams active frame stacks, execution paths, and memory objects to `/sys/debug/`.
*   **Usage:**
    *   **Hotspot Overlay:** Overlay actual dynamic execution counts onto the static `grphSpace` to show active hot paths.
    *   **Dynamic Inspection:** Let an agent read the exact variable state of a crash frame:
        ```mtron
        *</sys/debug/threads/main/frames/0/locals/connectionPool>
        ```

### B. 🧪 `testSpace` (Test Execution & Coverage)
Representing test runs, suites, and assertion states.
*   **Concept:** A space that executes JUnit tests upon read/write requests.
*   **Usage:**
    *   Writing to `/sys/test/active` runs the test class matching the active editor.
    *   Reading `/sys/test/coverage/studio/phaseshift/.../` returns statement and branch coverage percentages.
    *   **Auto-fix loops:**
        ```mtron
        # If test suite fails, read trace and feed to fix agent
        result = *</sys/test/suite/MemSpaceTest>;
        if (result/status == 'fail') {
            result/trace -> </sys/agents/debugger/input>
        }
        ```

### C. 📖 `docSpace` (Documentation & Reference Mapping)
A unified indexing space linking external docs (Javadoc, Markdown, MCP resources, Web specs) to the code structure.
*   **Concept:** Dereferencing a library import fetches the relevant API docs.
*   **Usage:**
    ```mtron
    # Resolve third-party library Javadoc automatically
    *</sys/code/imports/org.zeroturnaround.exec.ProcessExecutor?docs>
    ```

---

## 5. Architectural Alignment

This multi-dimensional layout enforces a clear separation of concerns:
1.  **IntelliJ** serves as the **editor interface** (the client of the developer's actions and the writer of code changes).
2.  **Metatron** serves as the **semantic knowledge graph and execution engine** where LLM agents reside.
3.  By mapping the codebase as a tree of URIs, we remove the impedance mismatch between files, syntax trees, graphs, and databases. To the agent, everything is just **data** addressed by a URI.
