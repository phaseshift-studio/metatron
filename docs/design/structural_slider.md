# The Metatron Structural Slider: From Scalars to Hypergraphs

In Metatron, the `==` (Select) and `=?=` (Where) operators are not mere functions; they are **Universal Projection Engines**. By varying the "complexity" of the la-palette (the projector record), the user can slide across different levels of structural interaction without ever changing the operator.

This is the **Structural Slider**: a progression of dimensionality and intent.

---

## 🟢 Level 1: Scalar Projection (Identity)
**The "Fetch" Mode.**
At this level, the projection is a simple coordinate lookup. The la-palette is implicit or degenerate.

*   **Intent:** "Give me a specific value."
*   **Syntax:** `Object == Key`
*   **Example:** `[name=>'marko', age=>29] == name` $\rightarrow$ `'marko'`
*   **Symmetry:** This is the base case. In a la-palette, this is equivalent to `{ key => identity }`.

## 🟡 Level 2: Linear Projection (Surgical)
**The "Rewrite" Mode.**
The la-palette introduces explicit rules for specific slots. It allows for simultaneous mutation and pruning.

*   **Intent:** "Change these specific parts; preserve the rest."
*   **Symmetry:** The same rule-set can be used to **Mutate** (`==`) or **Verify** (`=?=`).
*   **Examples:**
    *   **Mutation:** `[a=>1, b=>2] == [ a => +4, b => 7 ]` $\rightarrow$ `[a=>5, b=>7]`
    *   **Pruning:** `[a=>1, b=>2] == [ b => none ]` $\rightarrow$ `[a=>1]`
    *   **Verification:** `[a=>1] =?= [ a => ?>0 ]` $\rightarrow$ `[a=>1]` (Pass)

## 🟠 Level 3: Dimensional Projection (Path-based)
**The "Tunneling" Mode.**
Projection moves from the surface of an object into its nested dimensions. The la-palette now supports paths and wildcards (`+`).

*   **Intent:** "Navigate through layers to reach a target."
*   **Symmetry:** Navigation is treated as a coordinated projection across depths.
*   **Examples:**
    *   **Fixed Path:** `[[[[\"deep\"]]]]. == <0/0/0/0>` $\rightarrow$ `"deep"`
    *   **Wildcard Tunnel:** `[[[[\"deep\"]]]]. == <0/+/+/0>` $\rightarrow$ `"deep"`
    *   **Slicing result:** `[[[[\"deep\",\"seek\"]]]]. == <0/0/0/+>` $\rightarrow$ `{"deep", "seek"}`

## 🔴 Level 4: Recursive Projection (Fractal)
**The "Orchestration" Mode.**
Projections are nested. A la-palette rule can trigger another la-palette projection on the resulting slice.

*   **Intent:** "Transform a target, then transform a part of that transformation."
*   **Symmetry:** Seamless transition from structural change to scalar extraction.
*   **Example:** 
    `@usr1 == [ knows => == [ age => +100 ] == age ]` $\rightarrow$ `102`
    *(Traverse link $\to$ Mutate target's age $\to$ Extract result)*

## 🟣 Level 5: Categorical Projection (Predicated)
**The "Functional" Mode.**
The la-palette moves from **Identity Keys** (this exact key) to **Predicates** (any key that matches this pattern). This applies to Strings (Regex), URIs (Components), and Lists (Index Ranges).

*   **Intent:** "Apply a rule to all elements that satisfy a condition."
*   **Symmetry:** The l_palette acts as a high-level Mapper/Filter.
*   **Examples:**
    *   **String:** `str == [ '.*ug' => false ]` (Regex match $\to$ transform)
    *   **List:** `lst == [ ?<3 => +X, ?>=3 => +Y ]` (Index range $\to$ transform)

## ⚪ Level 6: Hyper-Structural Projection (Topology)
**The "Graph" Mode.**
The l_palette is used to project relations between objects in space. Relationships are treated as hidden facets (`OUT`, `IN`).

*   **Intent:** "Navigate and mutate the topology of the system."
*   **Symmetry:** Relational links are just another type of la-palette projection.
*   **The Hyper-Edge:** 
    `@v1 == [ OUT => [ knows => { @v2, @v3 } ] ]`
    *(A single relationship projecting to a set of entities)*

---

## Summary Table: The Unified Operator `==`

| Slider Level | la-palette Input | Target Logic | Result Meaning |
| :--- | :--- | :--- | :--- |
| **Scalar** | `Key` | $\text{Identity} \to \text{Value}$ | Single Field Access |
| **Linear** | `[ la-palette ]` | $\text{Slot} \to \text{Transform}$ | Surgical Mutation / Filter |
| **Dimensional** | `< Path >` | $\text{Coordinate} \to \text{Slice}$ | Deep Traversal/Extraction |
| **Recursive** | `[ k => == [...] ]` | $\text{Step}_n \to \text{Step}_{n+1}$ | Multi-stage Pipeline |
| **Categorical** | `[ Predicate $\to$ Rule ]` | $\text{Trait} \to \text{Transform}$ | Functional Mapping/Filtering |
| **Hyper** | `[ OUT $\to$ [ k $\to$ Set ] ]` | $\text{Topology} \to \text{Symmetry}$ | Hypergraph Orchestration |

**Final Conclusion:** 
By unifying these levels under a single operator, Metatron collapses the distinction between "Getting data," "Changing data," and "Filtering data." Everything is reduced to **Structural Projection**.
