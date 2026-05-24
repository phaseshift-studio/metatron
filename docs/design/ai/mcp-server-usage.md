# Metatron MCP Server - Usage Guide

## Overview

The Metatron MCP (Model Context Protocol) Server enables AI assistants to interact with Metatron programmatically. It exposes Metatron's capabilities through a standardized JSON-RPC interface that AI models can use to execute code, query system state, and explore available instructions.

## Architecture

The MCP server is **fully integrated** with MServer's WebSocket infrastructure:

- **Dual Protocol Support**: MServer automatically detects and routes both native Metatron protocol and MCP JSON-RPC messages
- **Session Management**: Each WebSocket client can have an MCP session for AI interactions
- **No Separate Port**: MCP runs on the same WebSocket port as native Metatron communication
- **Transparent Integration**: Existing Metatron clients are unaffected

## Connection

### WebSocket Endpoint

Connect to the same WebSocket endpoint as native Metatron:

```
ws://localhost:8080
```

### Protocol Detection

MServer automatically detects MCP messages by checking for JSON-RPC format:
- Messages starting with `{` and containing `"jsonrpc"` are routed to MCP
- All other messages are handled as native Metatron protocol

### MCP Initialization Sequence

1. **Client connects** via WebSocket
2. **Client sends** `initialize` request:
   ```json
   {
     "jsonrpc": "2.0",
     "id": 1,
     "method": "initialize",
     "params": {
       "protocolVersion": "2024-11-05",
       "capabilities": {},
       "clientInfo": {
         "name": "my-client",
         "version": "1.0.0"
       }
     }
   }
   ```
3. **Server responds** with capabilities:
   ```json
   {
     "jsonrpc": "2.0",
     "id": 1,
     "result": {
       "protocolVersion": "2024-11-05",
       "capabilities": {
         "tools": {}
       },
       "serverInfo": {
         "name": "metatron-mcp",
         "version": "1.0.0"
       },
       "instructions": "Metatron MCP Server - Execute Metatron code..."
     }
   }
   ```
4. **Client sends** `initialized` notification:
   ```json
   {
     "jsonrpc": "2.0",
     "method": "notifications/initialized"
   }
   ```

## Available Tools

### 1. evaluate_code

Execute Metatron code and return the result.

**Request:**
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "tools/call",
  "params": {
    "name": "evaluate_code",
    "arguments": {
      "code": "1.plus(2)"
    }
  }
}
```

**Response:**
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "result": {
    "content": [
      {
        "type": "text",
        "text": "3"
      }
    ],
    "isError": false
  }
}
```

**How it works:**
1. Code is read via `Router.global().read(code)`
2. Result is evaluated via `codeObj.apply()`
3. Result is converted to string and returned

### 2. get_system_info

Query Metatron system state including router information and server status.

**Request:**
```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "method": "tools/call",
  "params": {
    "name": "get_system_info",
    "arguments": {}
  }
}
```

**Response:**
```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "result": {
    "content": [
      {
        "type": "text",
        "text": "=== Metatron System Information ===\n\nRouter VID: /router\nRouter TID: /m/router\nServer Host: ws://localhost:8080\nServer Running: true\n\nStatistics:\n  I/O Stats: ..."
      }
    ],
    "isError": false
  }
}
```

### 3. list_instructions

List available Metatron instruction types.

**Request:**
```json
{
  "jsonrpc": "2.0",
  "id": 4,
  "method": "tools/call",
  "params": {
    "name": "list_instructions",
    "arguments": {
      "filter": "arithmetic"
    }
  }
}
```

**Response:**
```json
{
  "jsonrpc": "2.0",
  "id": 4,
  "result": {
    "content": [
      {
        "type": "text",
        "text": "=== Metatron Instructions ===\n\nCore Instructions:\n  - Arithmetic: plus, mult, neg, minus\n  - Logic: and, or, not\n  - Relations: id, compose, domain, range\n  - Collections: lst, objs, map\n  - Control: if, loop, apply\n\nUse evaluate_code to execute instructions.\n"
      }
    ],
    "isError": false
  }
}
```

## Error Handling

When an error occurs, the tool returns `isError: true`:

```json
{
  "jsonrpc": "2.0",
  "id": 5,
  "result": {
    "content": [
      {
        "type": "text",
        "text": "Error: Invalid syntax in code"
      }
    ],
    "isError": true
  }
}
```

## Session Management

- **Session Creation**: Automatically created when first MCP message is received
- **Session ID**: Derived from WebSocket attachment or timestamp
- **Session Cleanup**: Automatically removed when WebSocket closes
- **Multiple Sessions**: Each WebSocket connection can have one MCP session

## Integration with Claude Desktop

To use with Claude Desktop, add to your `claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "metatron": {
      "command": "wscat",
      "args": ["-c", "ws://localhost:8080"]
    }
  }
}
```

Or use a custom MCP client that connects to the WebSocket endpoint.

## Future Enhancements

### Planned Features

1. **Resource Support**: Expose Metatron spaces as MCP resources
2. **Prompt Templates**: Pre-defined prompts for common Metatron operations
3. **Instruction Discovery**: Dynamic listing from `mInstSet`
4. **Space Manipulation**: Tools to read/write to specific spaces
5. **Streaming Results**: Support for long-running computations

### Making MServer a Space

As noted by the user, a future enhancement is to make MServer (and its protocols including MCP) a Space. This would enable:

- **Configuration via Metatron**: Configure MCP server settings from within Metatron
- **Dynamic Tool Registration**: Add/remove tools at runtime
- **Space-based Access Control**: Control which clients can access which tools
- **Introspection**: Query MCP server state as Metatron objects

## Implementation Details

### Files

- `src/main/java/studio/phaseshift/metatron/isa/mach/type/net/mcp/MetatronMcpServer.java`
- `src/main/java/studio/phaseshift/metatron/isa/mach/type/net/mcp/McpWebSocketTransport.java`
- `src/main/java/studio/phaseshift/metatron/isa/mach/type/net/mcp/McpWebSocketTransportProvider.java`
- `src/main/java/studio/phaseshift/metatron/isa/mach/type/net/MServer.java` (modified)

### Dependencies

- MCP Java SDK 1.1.0 (`io.modelcontextprotocol.sdk:mcp-core`)
- Jackson for JSON serialization
- Reactor for reactive programming (from MCP SDK)

### Design Principles

1. **Minimal Intrusion**: MCP integration doesn't affect existing Metatron functionality
2. **Protocol Coexistence**: Native and MCP protocols share the same WebSocket
3. **Clean Separation**: MCP code is isolated in `mcp` package
4. **Metatron-First**: Tools execute actual Metatron code, not simulations
5. **Future-Proof**: Architecture supports making MServer a Space later

## Troubleshooting

### Connection Issues

**Problem**: Client can't connect
**Solution**: Ensure MServer is running and Router is loaded

### Protocol Detection Issues

**Problem**: MCP messages treated as native protocol
**Solution**: Ensure messages are valid JSON starting with `{` and contain `"jsonrpc": "2.0"`

### Tool Execution Errors

**Problem**: `evaluate_code` returns errors
**Solution**: Check that code is valid Metatron syntax and Router is properly initialized

### Session Not Found

**Problem**: Session errors after reconnection
**Solution**: Re-initialize with `initialize` request after reconnecting

## Example: Complete Interaction

```json
// 1. Initialize
{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"1.0"}}}

// 2. Initialized notification
{"jsonrpc":"2.0","method":"notifications/initialized"}

// 3. List tools
{"jsonrpc":"2.0","id":2,"method":"tools/list"}

// 4. Execute code
{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"evaluate_code","arguments":{"code":"1.plus(2)"}}}

// 5. Get system info
{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"get_system_info","arguments":{}}}
```

## References

- [Model Context Protocol Specification](https://modelcontextprotocol.io/)
- [MCP Java SDK](https://github.com/modelcontextprotocol/java-sdk)
- Metatron MCP Implementation Status: `docs/ai/mcp-implementation-status.md`
