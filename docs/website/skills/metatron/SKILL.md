---
name: metatron
description: common patterns in metatron
---

# Effective Metatron Patterns

This skill is a practical guide to handling common use cases patterns. Each secondary header (`==`)  is a particular
pattern and each is independent of the other.

**IMPORTANT**: If the user wants the agent to execute the associated mtron expressions, then the agent must be provided
access to an mtron virtual machine. The `tool_feature::T` of the `/m/llm` instset enables agents to use tools -- mtron
instructions and MCP servers. A general purpose instruction is `eval?#{*}<=#{?}(#::T)`. The following agent definition
snippet is sufficient for the spawned agent to execute mtron code.

```mtron
mtron> ... [-- larger agent definition --]
==>fail::[parse error at line 1, col 2:
     ... 
      ^
     could not parse at '.']@/sys/fail/44
mtron> feature=>[
        tool_feature::[tool=>[!*eval]]
        ... [-- other features attached to agent --]
==>fail::[parse error at line 1, col 8:
     feature=>[
           tool_feature::[tool=>[!*eval...
            ^
     could not parse at '=' — unclosed '[' — missing ']'?]@/sys/fail/46
mtron> ]
==>fail::[parse error at line 1, col 1:
     ]
     ^
     unexpected ']' — missing opening '[' or extra ']'?]@/sys/fail/48
```
## References

* **MCP Client and Servers (mtron)**: `references/mcp-mtron.md` — Using existing MCP server tools, dynamically creating
  MCP servers, HTTP/STDIO/WebSocket transports.
* **Type System (mtron)**: `../mtron/references/type-system-mtron.md` — vid/tid concepts, base types, coefficients, isa
  vs non-isa predicates, nominal vs structural types, type definition syntax, pattern/generic types, LCD.
* **Type System (Java)**: `references/type-system-java.md` — Type interface, MType/T () factory, Fluent/StartLess API,
  predicates, isRefinementOf vs test, generateLCD, coefficients (cInt/C), Call/Inst/Code, common gotchas.
* **UI Architecture (Java)**: `references/ui-instset-java.md` — Widget lifecycle, JRec state bridge, Style system,
  FloatingSurface, Anchor positioning, uiInstSet type registration. How to create, modify, and float widgets.
* **Rewrite System (Java)**: `references/rewrite-system-java.md` — Rewriter fixed-window matching, RewriteBuilder
  space-aware native pushdown, custom Function<Code,Code> rewrites, Code.rewrite () loop, registration via InstSet.setup
  (), and concrete examples from all instset spaces.
* **tbleSpace (Java)**: `references/tble-space-java.md` — Relational-database space architecture, dual-path reads
  (table-mapped + KV store), ExistingTableSchema lifecycle, SQL rewrite pushdown (count, sum, limit, offset, where,
  select, KV), dialect handling, VID stamping, schema generation.