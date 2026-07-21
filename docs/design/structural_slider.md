# The Metatron Structural Slider & Universal Projection Engine

Metatron employs a **Universal Projection Engine** based on the "la-palette" concept. Instead of disparate getters, setters, and filter functions, all data interaction is unified under two primary operators: `==` (Select/Transform) and `=?=` (Where/Verify). 

The system treats every object—whether it's a Record, String, URI, List, or Graph Node—as a projectable structure.

---

## 1. The Structural Slider
The l_palette varies in complexity depending on the "Symmetry" required. A user slides across these levels of interaction without changing the operator.

### 🟢 Level 1: Scalar Projection (Identity)
**Mode:** Fetch/Extract.
*   **Intent:** Retrieve a specific value via a key or index.
*   **Syntax:** `Object == Key`
*   **Example:** `[name=>'marko', age=>29] == name` $\rightarrow$ `'marko'`

### 🟡 Level 2: Linear Projection (Surgical)
**Mode:** Rewrite/Prune.
*   **Intent:** Mutate specific slots while preserving the rest of the structural envelope.
*   **Syntax:** `Object == [ RuleSet ]`
*   **Examples:**
    *   **Mutation:** `[a=>1, b=>2] == [ a => +4, b => 7 ]` $\rightarrow$ `[a=>5, b=>7]`
    *   **Pruning:** `[a=>1, b=>2] == [ b => none ]` $\rightarrow$ `[a=>1]`
    *   **Verification:** `[a=>1] =?= [ a => ?>0 ]` $\rightarrow$ `[a=>1]` (Pass)

### 🟠 Level 3: Dimensional Projection (Path-based)
**Mode:** Tunneling/Traversing.
*   **Intent:** Reach deep targets in nested structures using consolidated paths or wildcards (`+`).
*   **Examples:**
    *   **Fixed Path:** `[[[[\"deep\"]]]]. == <0/0/0/0>` $\rightarrow$ `"deep"`
    *   **Wildcard Tunnel:** `[[[[\"deep\"]]]]. == <0/+/+/0>` $\rightarrow$ `"deep"`
    *   **Slicing result:** `[[[[\"deep\",\"seek\"]]]]. == <0/0/0/+>` $\rightarrow$ `{"deep", "seek"}`

### 🔴 Level 4: Recursive Projection (Fractal)
**Mode:** Orchestration.
*   **Intent:** Chain projections where a rule triggers another projection on the resulting slice.
*   **Example:** `@usr1 == [ knows => == [ age => +100 ] == age ]` $\rightarrow$ `102`
    *(Traverse link $\to$ Mutate target's age $\to$ Extract result)*

### 🟣 Level 5: Categorical Projection (Predicated)
**Mode:** Functional Mapping.
*   **Intent:** Apply rules to all elements that satisfy a predicate (Regex for strings, Index-ranges for lists).
*   **Examples:**
    *   **String:** `str == [ '.*ug' => false ]` (Regex match $\to$ transform)
    *   **List:** `lst == [ ?<3 => +X, ?>=3 => +Y ]` (Index range $\to$ transform)

### ⚪ Level 6: Hyper-Structural Projection (Topology)
**Mode:** Graph Orchestration.
*   **Intent:** Navigate and mutate the topology of a space using relational facets (`OUT`, `IN`).
*   **Symmetry:** Relational links are just another type of la-palette projection.
*   **The Hyper-Edge:** `@v1 == [ OUT => [ knows => { @v2, @v3 } ] ]` (A single relationship projecting to a set of entities).

---

## 2. The Hybrid Type System: Nominal & Structural

Metatron unifies **Structural identity** (shape) and **Nominal identity** (tags).

### Nominal Types (`T@name`)
Defined by a name/tag. An object is a nominal type if it has the corresponding tag in its lineage.  l_palette rules for nominal types are treats as "Closed Sets"—they require explicit identification.

### Structural Types (`T[?predicate]`)
Defined by a la-palette predicate. If an object satisfies the shape, it *is* that type. This allows for **Automatic Classification**.

**Refinement Logic:**
- A Nominal type can refine a Structural type (e.g., `rec::T [?p] @person`).
- A structural type can refine a nominal type.
- A nominal type can refine another nominal type.
- Base Types are defined where `vid == tid` and the ID is in the `BASE_TYPES` registry (`int::T`, `str::T`, etc.).

---

## 3. State, Persistence, and Anchors

### The la-palette as a State Machine
The `==` operator handles both views and mutations based on the target's identity:

1.  **Ephemeral Values (Stack)**: Objects with only a `tid`. Projections return new values on the stack.
2.  **Anchored Entities (Space)**: Objects with both `tid` and `vid` (e.g., `@abc`). 
    *   **Selection (`==`)**: If the result propagates the original `vid`, it triggers an automatic write-back to space, making la-palette projections "live" updates.
    *   **Binding (`>>=`)**: Explicitly commits a projection to the state of the anchor.

### Relational Facets
Links between anchored objects are stored as hidden facets (`OUT` and `IN`). 
- **Surgical Linking**: `@v1 == [ OUT => [ knows => @v2 ] ]` creates an edge from v1 to v2.
- **Lazy Materialization**: These facets are "Ghost Facets"—they only trigger database lookups when explicitely projected by a la-palette.

---

## Summary Table: The Unified Operator `==`

| Slider Level | l_palette Input | Target Logic | Result Meaning |
| :--- | :--- | :--- | :--- |
| **Scalar** | `Key` | $\text{Identity} \to \text{Value}$ | Single Field Access |
| **Linear** | `[ la-palette ]` | $\text{Slot} \to \text{Transform}$ | Surgical Mutation / Filter |
| **Dimensional** | `< Path >` | $\text{Coordinate} \to \text{Slice}$ | Deep Traversal/Extraction |
| **Recursive** | `[ k => == [...] ]` | $\text{Step}_n \to \text{Step}_{n+1}$ | Multi-stage Pipeline |
| **Categorical** | `[ Predicate $\to$ Rule ]` | $\text{Trait} \to \text{Transform}$ | Functional Mapping/Filtering |
| **Hyper** | `[ OUT $\to$ [ k $\to$ Set ] ]` | $\text{Topology} \to \text{Symmetry}$ | Hypergraph Orchestration |
