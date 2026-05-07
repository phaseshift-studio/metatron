---
name: mtron
description: metatron knowledge to assist users
---

# mtron assistance

Help users be effective with metatron: answering questions, connecting data sources, writing expressions, providing statistics.

**Tip:** "metatron" (always lower cased) refers to the system environment while, while "mtron" (always lower cased) refers to the functional programming language used to manipulate the metatron environment. (analogous to the JVM and Java).

## Using the metatron MCP Server or WebSocket Client

Execute mtron expressions via the `eval-mcp-metatron` tool:

```
Tool: eval-mcp-metatron
Parameter: code = "<your mtron expression>"
```

Examples:
```mcp
mtron_eval("*/sys/space/+/")                     [-- list spaces --]
mtron_eval("*/sys/console/history")              [-- get history --]
mtron_eval("*acme:customers.*(_).take(5)")       [-- query data  --]
```

If the MCP server is not available, you can use the script `scripts/mtron_ws_client.py`.

Examples::
```python
from mtron_ws_client import mtronWebSocketClient
client = mtronWebSocketClient(host="<the users metatron websocket endpoint>")
result = client.eval(code="<an mtron expression>")
```

Either of the two `eval()` options above can be used for **all** mtron expression execution — reads, writes, queries, introspection, etc.

## Step 1: Gather Context

```mtron
*/sys/space/+/                                    # List user's spaces
*/sys/space/${space}                              # View space config (pattern, route, etc.)
*/sys/console                                     # Console info (version, etc.)
*/sys/console/history                             # User's command history (structured: time, entry)
```

### Important Concepts

#### Space Patterns

Each space has a `pattern` (what URIs it handles) and `route` (how URIs map internally).
Don't assume prefixes — discover them from the space config first.

#### Dereferencing URIs

To retrieve the objs at a particular URI, simply dereference the URI with the `*` (`from()`) instruction. Note that URIs support MQTT-style wildcard semantics (e.g. `+` (single-level) and `#` (multi-level) wildcards). Also, if the dereferenced URI has a trailing `/`, the result is a stream of relations `uri=>obj`. If no trailing `/` exists, then the result is just a stream of `obj` (same obj data in both situations save the trailing `/` usage provides the uri of the obj as well).

```mtron
*/path/to/obj/  # returns stream of uri=>obj
*/path/to/obj   # returns stream of obj
```

#### Walking/Traversing URI Spaces (Tree Navigation)

URIs in mtron form a tree structure. To traverse this tree programmatically (e.g., for building a tree browser or exploring a space):

**Key Concepts:**
- **Node URI** (no trailing `/`): Points to an obj attached at that location
- **Branch URI** (trailing `/`): Returns a `uri=>obj` relation for all matches
- **`<<` (left-shift)**: Applied to a relation, returns just the keys (URIs)
- **`+` wildcard**: Matches exactly one path segment (breadth-first expansion)

**Breadth-First Tree Traversal Pattern:**

```mtron
*/m/.<<                    # Get URI at exact path → /m
*/m/+/.<<                  # Get direct children → {/m/bool, /m/int, /m/str, ...}
*/m/+/+/.<<                # Get grandchildren → {/m/mach/info, /m/web/route, ...}
*/m/str/+/.<<              # Children of specific node → {/m/str/split, /m/str/join, ...}
```

**For JSON output (useful for web clients):**

```mtron
*/m/+/.<<.as(json_str::T)  # Returns: {'/m/bool', '/m/int', '/m/str', ...}
```

**Why this works:**
1. `*/path/+/` - dereferences all URIs one level below `path`, returning `uri=>obj` relations
2. `.<<` - extracts just the keys (URIs) from those relations
3. Result is a set of child URIs that can be used for the next level of expansion

**Scheme-based URIs work the same way:**

```mtron
*local:software/+/.<<      # Children under local:software/
*netflix:movie/+/.<<       # Children under netflix:movie/
```

**Depth-first (all descendants):**

```mtron
*/m/#/.<<                  # All URIs at any depth under /m (use with caution - can be large)
```

#### Obj Documentation

Any obj can have documentation. Use `?docq` suffix to retrieve it:
```mtron
*/path/to/obj           # the obj referred to by the uri
*/path/to/obj?docq      # documentation associated with the obj (dom, rng, args, desc, examples)
```

This is useful for learning about instructions.

```mtron
*from
```

## Step 2: Load Reference (one only)

| Goal | Reference |
|------|-----------|
| Questions | [answer-questions.md](references/answer-questions.md) |
| Data sources | [connecting-datasources.md](references/connecting-datasources.md) |
| Expressions | [writing-mtron-expressions.md](references/writing-mtron-expressions.md) |
| Statistics | [providing-data-statistics.md](references/providing-data-statistics.md) |
| MCP server push / subq | [mcp-server-notifications.md](references/mcp-server-notifications.md) |
| HTTP page fetching | [http-page-fetching.md](references/http-page-fetching.md) |
| MCP server architecture | [mcp-server-architecture.md](references/mcp-server-architecture.md) |
| Agent memory / chat | [mtron-agent-architecture.md](../../../.claude/projects/-home-killswitch-software-metatron/memory/team/mtron-agent-architecture.md) |

**Load only the relevant reference file, not all.**

## Step 3: Confirm Plan (if needed)

Required for: writes, expensive reads, or destructive operations.

```
I'll help you <goal>. Steps:
1. <step> [complexity: low|medium|high]
2. <step> [complexity: low|medium|high]

Proceed?
```

## Step 4: Execute

1. Run expressions
2. Validate results after each step
3. On errors: explain + propose alternatives
4. Iterate as needed

## Step 5: Summarize

```
Done. Summary:
- What was done: <actions>
- Results: <outcomes>
- Suggestions: <next steps>
```