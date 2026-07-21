# DESIGN SPEC: Metatron Structural Unification & Projection

## 1. Objective
Achieve total structural symmetry between raw data formats, internal la-palette representations, and final documentation artifacts. This is accomplished by unifying the entry and exit points of the system into a single operation: `.as()`.

The goal is to replace all fragmented rendering logic (e.g., space modules, `mimeq` handlers) with a consistent "Roundtrip" model where moving between data densities is a lossless projection.

## 2. Thread 1: The Representation Ring (Casting $\rightleftharpoons$ Casting)
**Goal:** Establish a bidirectional pipeline where casting into and out of structural formats is a single, unified operation.

### 2.1 The "Typed String" Primitive
Metatron introduces **Tagged Formats** as first-class types in the ISA to avoid premature parsing and support lazy evaluation.

*   **The Hierarchy**: `str::T` $\to$ `(json::T | adoc::T | html::T | xml::T | bytes::T)`.
*   **Symmetry of Density**: 
    - **Lifting (Entry)**: `RawString` $\xrightarrow{.as(\text{format::T})}$ `Tagged Representation`. This assigns a structural identity to the string without necessarily inflating it into a JVM object.
    - **Inflation (Internal)**: `Tagged Representation` $\xrightarrow{.as(\text{rec::T/lst::T})}$ `Structural Record`. Full parse into la-palette objects.

### 2.2 The Bidirectional Roundtrip (No-Op)
The system must ensure that "casting out" of a structural state returns the object to its original representation without loss. For any format $F$:
$$\text{RawString} \xrightarrow{.as(F::T)} \text{StructuralObj} \xrightarrow{.as(\text{str::T})} \text{RawString}$$

**Implementation Requirements:**
1.  **Lossless Preservation**: The la-palette for `.as(str::T)` must preserve non-semantic data (whitespace, key order in JSON, comments in Adoc) to ensure the roundtrip is a no-op.
2.  **Unified Interface**: All representation changes occur via `.as()`. There are no separate `render()` or `serialize()` functions.
3.  **Integration with `mimeq`**: The `?mimeq=...` query on any Space is simplified to a la-palette projection:
    *   `Value` $\xrightarrow{.as(\text{TargetFormat::T})}$ $\xrightarrow{.as(\text{str::T})}$ `Rendered String`.

---

## 3. Thread 2: The Documentation Loom (Scope-Aware Processor)
**Goal:** Turn the `.adoc` source into a distributed, executable knowledge base using nested VM scopes to aggregate intelligence across files.

### 3.1 The Multi-Tiered VM Architecture
The `MtronDocProcessor` operates as a hierarchy of nested VMs where state accumulates upward during the build process.

| Scope | Lifetime | Visibility | Primary Purpose |
| :--- | :--- | :--- | :--- |
| **`scope=line`** | Single Expression | Local Cursor | Transient substitutions and inline projections. |
| **`scope=doc`** | Single `.adoc` file | Page-level state | Document attributes, local la-palettes, section anchors. |
| **`scope=docs`** | Full Build Process | Global Knowledge Graph | Aggregating `skill::T`, `tool::T`, and `asset::T` across all files. |

### 3.2 The "Symmetry of Aggregation" Pipeline
1.  **Extraction**: The processor scans `.adoc` files for roles (`[.mtron scope=...]`) and HTML comments (`<!-- ... -->`).
2.  **Execution**: mtron expressions are executed in the appropriate scope. Global state is built using the merge operator: `>>= +`.
    *   Example: `/docs/skills/basic-algo` defined in `page1.adoc` is appended to in `page5.adoc`.
3.  **Casting (The Final Projection)**: After all files are processed, the global structural state is projected into final artifacts using la-palette casts.
    *   `Global_State` $\xrightarrow{.as(\text{markdown::T})}$ $\to$ `SKILL.md` files.
    *   `Global_State` $\xrightarrow{.as(\text{html::T})}$ $\to$ Integrated Knowledge Map on the website.

### 3.3 The "Invisible Tunnel" Logic
To maintain standard AsciiDoc compatibility while enabling executive logic:
- **Human Layer**: Standard `.adoc` syntax with roles (e.g., `[.mtron-skill]`) for visible cues.
- **Logic Layer**: Executable `mtron` code wrapped in `<!-- ... -->` comments, invisible to the renderer but processed by the VM.

---

## 4. Summary of Invariants
- **Symmetry of Entry/Exit**: If you can `.as()` into a type, you must be able to `.as()` back out of it losslessly.
- **Purity of Representation**: Space modules (`mimeq`, etc.) are routers to l_palettes, not providers of rendering logic.
- **Contextual Continuity**: A skill's definition can span multiple documents via `scope=docs` and the merge operator.
