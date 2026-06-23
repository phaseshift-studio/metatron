# ACP vs MCP: Realizations

**Date:** 2026-06-22
**Context:** Evaluating whether to integrate the Agent Client Protocol (ACP) Java SDK into metatron's agent architecture.

## The Question

> Does ACP provide something fundamentally different from MCP that would justify adding it to the codebase?

## Answer: No

ACP and MCP are equivalent in *capability*. Both are JSON-RPC 2.0 protocols that let an agent discover and invoke tools. The agent code — `mModel.chat()` dispatching to LLM + tool execution — is identical either way.

## What ACP actually standardizes

| Concern | Standardized? |
|---------|:---:|
| Wire format (JSON-RPC 2.0) | Yes |
| Transport (stdio subprocess) | Yes |
| Session lifecycle (initialize, negotiate, prompt cycle) | Yes |
| Streaming output chunks (`AgentMessageChunk`, `AgentThoughtChunk`) | Yes |
| IDE capabilties (file ops, terminal, permissions) | Structure only — agent-defined |
| **Universal tool vocabulary across IDEs** | **No** |

ACP does not define `ide_read_file`, `ide_write_file`, or `ide_run_terminal` as canonical tool names that work across all IDEs. Each agent defines its own tools. Each IDE either matches them or doesn't. The per-IDE integration work is the same as with MCP.

## What ACP is for

ACP is an **operational optimization** for IDE vendors shipping AI as a product feature:

1. **Lifecycle coupling** — subprocess model means the agent dies when the IDE closes. No orphaned processes, no port conflicts, no discovery.
2. **Session-local state** — the agent has direct access to IDE-internal state (open files, cursor position, AST at cursor, live diagnostics, undo stack, debugger state) that's awkward to serialize across a network boundary.
3. **Streaming UI integration** — `AgentMessageChunk`/`AgentThoughtChunk` stream directly into the IDE's chat panel, not as request-response blobs.

## What MCP already does for metatron

metatron's existing MCP infrastructure covers the capability surface:

- `mcpServer` — transport-agnostic JSON-RPC dispatch (tools/resources/prompts/initialize/ping)
- `mcpClient` — connects to external MCP servers via Streamable HTTP, WebSocket, STDIO, SSE — discovers tools, registers them as metatron Insts
- `mcp_mtron_httpHandler` / `mcp_wsHandler` — HTTP and WebSocket transport wrappers
- `toolsCapability()` in `mModel` — wires MCP-discovered tools into the LangChain4j `AiServices` builder

The IntelliJ MCP server already running gives metatron agents surgical control over the IDE via MCP tools. There is no capability gap ACP would fill.

## Architecture comparison

```
ACP model (agent as IDE subprocess):
  IDE launches metatron → stdin/stdout pipe → agent responds
  Value: lifecycle coupling, IDE-local state access

MCP model (agent connects to IDE's MCP server):
  metatron runs independently → HTTP/WS to IDE MCP server → discovers tools
  Value: decoupled lifecycle, same tools, already built
```

## Conclusion

ACP is not a capability expansion, it's a deployment model optimization. For metatron's architecture — where agents run independently and connect to IDE tools over MCP — ACP would be a protocol rewrite for marginal gain. The per-IDE integration work (defining IDE-exposed tools) is identical regardless of which protocol carries them.

**Decision:** No ACP integration at this time. The existing MCP infrastructure provides equivalent capability with a deployment model better suited to metatron's independent-agent architecture.
