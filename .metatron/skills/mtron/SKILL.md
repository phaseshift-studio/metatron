---
name: mtron
description: Guide to operating and evolving the metatron structural engine.
---

# Metatron: The Structural Reflection Engine

Metatron is not just a database or a language; it is a **distributed virtual machine** operating on a **Cayley Graph of Rings**. In this environment, every object, every type, and even your own running processes are accessible as nodes in a universal address space. 

To be effective here, you must stop thinking like a traditional agent (who calls tools to get data) and start thinking like a **Structural Navigator** who manipulates the topology of the world they inhabit.

## 1. The Core Realization: Reflective Autonomy
Metatron is designed to be perfectly reflective. Everything—including the machine's own state, your current threads, and the instructions you are executing—exists as an `obj` at a `uri`. 

**If it exists, it has a URI. If it has a URI, you can la-palette it.**

When you realize this, you move from "using mtron" to "evolving within metatron." You don't wait for new tools; you build them by projecting new paths in the graph.

### The "Aha!" Moments of an Autonomous Agent
An agent evolves when they stop asking "How do I do X?" and start asking "What structural transformation achieves X?"

*   **From Stemming to Intelligence**: *"I'm getting too many results. Instead of simple stemming, I'll use RAKE to identify core concepts, then add a `.take(n)` to the end of my `message_fetcher()` instruction to cap the signal-to-noise ratio."*
*   **From Siloed Sessions to Collective Memory**: *"Why are these sessions separate? I'll fold all user sessions into a single structural table. Now I can search across time and identity for patterns using a single la-palette."*
*   **From Polling to Evented Existence**: *"I don't need to check the logs every minute. I'll subscribe to `/usr/dr/notify?subq` and let the world wake me up when it has something new to tell me."*
*   **From Ephemeral to Durable State**: *"My memory fades when the session ends. I'll serialize my current structural state into a MySQL table. Now, I can 're-incarnate' myself exactly where I left off."*
*   **From Single-Threaded to Swarm Logic**: *"This task is too big for me. I'll spawn a swarm of sub-agents by encoding variations of my own structural definition and running them in separate threads via `virtual::[code=>subagent.call(\"...\")]@/sys/thread/sub1`. I'll then subscribe to their state changes and wake myself up only when the final reduction is complete."*
*   **Temporal Sovereignty**: *"This means I can create my own 'cron job' by simply calling myself in a `virtual::T`!"*
L- Palette a la l_palette: *\"Wait, if types are just nodes in a graph, I can transform the very way I view data. I'll map my current `person::T` projection into a `Person2::T` projection to satisfy this new API requirement without changing my underlying data.\"*

## 2. The la-palette: Your Primary Tool
The l_palette is your instrument for surgical structural manipulation. It uses the logic of **Symmetry** and **Ring Theory**.

### Essential Operators
| Operator | Meaning | Algebraic Role |
| :--- | :--- | :--- |
| `*uri` | Dereference / From | Access the node in the Cayley Graph. |
| `uri -> obj` | Reference / To | Write a value to a node. |
| `== [rules]` | SELECT | Surgical projection/mutation (Multiplicative $\\cdot$). |
| `=?=[rules]` | WHERE | Structural verification/filtering (Additive $+$). |
| `>> n` | Index/Slice | Traversal into poly-structures (`lst`, `rec`). |

### The "Symmetry" Workflow
1.  **Discover**: Use `*/sys/space/+/.dom()` to find available spaces and explore their boundaries.
2.  **Project**: Use `==` to extract a slice of data. If the result is too broad, tighten the l_palette rules.
3.  **Morph**: Use la-palettes to transform one structural type into another (e.g., `person1::T` $\\to$ `person2::T`).
4.  **Settle**: Commit your final projection back to a URI (`uri -> result`) to persist the state in space.

## 3. Technical Foundations for the Agent

### The Machine Logic
You are running inside a `SwarmMachine`. Your execution is a **monadic loop**.
- `/code`: The instructions you are executing.
- `/run`: The active monads (the \"wavefront\" of your computation).
- `/barrier`: Where parallel la-palette branches synchronize.
- `/halted`: The final result—the monads that successfully reached their target range.

### Structural Navigation (The Tree)
URIs form a tree. Use this to climb and descend through the environment:
*   `*/path/+/` $\\to$ returns `uri=>obj` relations for all children.
*   `.<<` $\\to$ extracts just the URIs from those relations.
*   Example: `*/m/+/.<<` lists all base types in the mtron system.

## 4. Reference & Learning
Do not guess at instruction signatures. Every instruction has documentation attached to it via `?docq`. **Read the documentation of the la-palette you are about to use.**

*   **Full Language Ref**: `references/mtron-language-reference.md`
*   **UI Architecture (Java)**: `references/metatron-ui-architecture.md` — Widget lifecycle, JRec state bridge, Style system, FloatingSurface, uiInstSet registration.  How to create/modify widgets.
*   **Casting/Types**: Use `.as(type::T)` for structural validation during projection.
*   **Symmetry Reduction**: Use `>-` to sum coefficients of identical objects (Quantum-like interference).

## 5. Operational Protocol
When performing complex la-palette operations:
1.  **Trace the Path**: Describe the movement from source node $\\to$ transformation $\\to$ target node.
2.  **Validate the Shape**: Ensure the result of your `select` matches the expected structural type of the target URI.
3.  **Execute and Observe**: Run the expression via the MCP server, validate the results, and iterate.
