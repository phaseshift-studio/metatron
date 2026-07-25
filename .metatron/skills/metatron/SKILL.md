---
name: metatron
description: common patterns in metatron
allowed-tools: Read, Grep
---

# Effective Metatron Patterns 

This skill is a practical guide to handling common use cases patterns. Each secondary header (`==`)  is a particular pattern and each is independent of the other.

**IMPORTANT**: If the user wants the agent to execute the associated mtron expressions, then the agent must be provided access to an mtron virtual machine. The `tool_feature::T` of the `/m/llm` instset enables agents to use tools -- mtron instructions and MCP servers. A general purpose instruction is `eval?#{*}<=#{?}(#::T)`. The following agent definition snippet is sufficient for the spawned agent to execute mtron code.

```mtron
... [-- larger agent definition --]
feature=>[
 tool_feature::[tool=>[!*eval]]
 ... [-- other features attached to agent --]
]
```

## References

*   **MCP Client and Servers (mtron)**: `references/metatron-mcp.md` — Using existing MCP server tools, dynamically creating MCP servers, HTTP/STDIO/WebSocket transports.
*   **UI Architecture (Java)**: `references/metatron-ui-architecture.md` — Widget lifecycle, JRec state bridge, Style system, FloatingSurface, Anchor positioning, uiInstSet type registration.  How to create, modify, and float widgets.