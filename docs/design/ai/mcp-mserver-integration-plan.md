# MCP Server Integration with Metatron MServer

## Overview

This document outlines the plan to integrate Model Context Protocol (MCP) with Metatron's existing `MServer` WebSocket infrastructure, creating a seamless way for AI assistants to interact with Metatron in real-time.

## Architecture

### Current Metatron Infrastructure

**MServer** (`studio.phaseshift.metatron.isa.mach.type.net.MServer`):
- Extends `WebSocketServer` from `org.java_websocket`
- Handles WebSocket connections for distributed Metatron nodes
- Uses `ObjSerializer` for binary serialization of Metatron objects
- Processes incoming `Obj` messages via `onObj(WebSocket, Obj)`
- Already integrated with Metatron's Router and cluster management

**Key Components:**
- `MConnection` - Interface for connections (send/receive Obj)
- `MClient` - Client-side WebSocket connection
- `ObjSerializer` - Binary serialization for Metatron objects
- `Router` - Global routing and message handling

### MCP Java SDK Architecture

**Core Components:**
- `McpServer` - Main server interface (sync/async)
- `TransportProvider` - Abstraction for communication layer
- `ServerCapabilities` - Tools, Resources, Prompts, Logging
- `Tool`, `Resource`, `Prompt` specifications with handlers

**Transport Options:**
- STDIO (stdin/stdout for process-based)
- SSE (Server-Sent Events over HTTP)
- Streamable HTTP (bidirectional HTTP streaming)
- **Custom transports** (we'll implement WebSocket)

## Integration Strategy

### Approach: Custom WebSocket Transport for MCP

Instead of using MCP's built-in transports, we'll create a **custom WebSocket transport** that bridges MCP's JSON-RPC protocol with Metatron's existing MServer infrastructure.

**Benefits:**
1. ✅ Leverages existing MServer WebSocket infrastructure
2. ✅ No dependency on Spring or Servlet containers
3. ✅ Integrates naturally with Metatron's architecture
4. ✅ Reuses existing serialization and routing
5. ✅ Maintains Metatron's distributed cluster capabilities

### Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                    AI Client (Claude, etc.)                  │
│                                                               │
│  ┌──────────────────────────────────────────────────────┐   │
│  │           MCP Client (WebSocket)                      │   │
│  └──────────────────────────────────────────────────────┘   │
└───────────────────────────┬─────────────────────────────────┘
                            │ WebSocket (JSON-RPC)
                            │
┌───────────────────────────▼─────────────────────────────────┐
│                    Metatron MServer                          │
│                                                               │
│  ┌──────────────────────────────────────────────────────┐   │
│  │         McpWebSocketTransport                         │   │
│  │  - Handles WebSocket connections                      │   │
│  │  - Translates JSON-RPC ↔ MCP protocol                │   │
│  └──────────────────────────────────────────────────────┘   │
│                            │                                  │
│  ┌──────────────────────────────────────────────────────┐   │
│  │         McpServer (Java SDK)                          │   │
│  │  - Tools, Resources, Prompts                          │   │
│  │  - Capability negotiation                             │   │
│  └──────────────────────────────────────────────────────┘   │
│                            │                                  │
│  ┌──────────────────────────────────────────────────────┐   │
│  │         Metatron Console Integration                  │   │
│  │  - Execute mtron code                                 │   │
│  │  - Query system state                                 │   │
│  │  - Access Router, Spaces, etc.                        │   │
│  └──────────────────────────────────────────────────────┘   │
└───────────────────────────────────────────────────────────────┘
```

## Implementation Plan

### Phase 1: Custom WebSocket Transport (Foundation)

**Goal:** Create a WebSocket transport that implements MCP's transport interface.

**Components to Build:**

1. **`McpWebSocketTransport`** - Implements MCP transport over WebSocket
   - Handles JSON-RPC message framing
   - Manages request/response correlation
   - Supports notifications (no response expected)
   - Integrates with MServer's WebSocket lifecycle

2. **`McpWebSocketSession`** - Represents a single MCP client session
   - Tracks client capabilities
   - Manages message routing
   - Handles session state

3. **`McpMessageHandler`** - Bridges MServer and MCP
   - Intercepts WebSocket messages
   - Routes MCP messages to McpServer
   - Routes Metatron Obj messages to existing handlers

**Key Design Decision:**
- MServer will handle **both** MCP JSON-RPC messages AND native Metatron Obj messages
- Message type detection: JSON-RPC messages start with `{` and contain `"jsonrpc": "2.0"`
- This allows MCP clients and native Metatron clients to coexist

### Phase 2: MCP Server Configuration

**Goal:** Configure McpServer with Metatron-specific tools and resources.

**Tools to Implement:**

1. **`evaluate_code`** - Execute Metatron code
   ```json
   {
     "name": "evaluate_code",
     "description": "Execute Metatron code and return results",
     "inputSchema": {
       "type": "object",
       "properties": {
         "code": {"type": "string", "description": "Metatron code to execute"},
         "context": {"type": "string", "description": "Optional execution context"}
       },
       "required": ["code"]
     }
   }
   ```

2. **`query_router`** - Query Router state
   ```json
   {
     "name": "query_router",
     "description": "Query Metatron Router for objects and state",
     "inputSchema": {
       "type": "object",
       "properties": {
         "path": {"type": "string", "description": "Router path to query"},
         "pattern": {"type": "string", "description": "Optional pattern match"}
       },
       "required": ["path"]
     }
   }
   ```

3. **`list_spaces`** - List available Spaces
4. **`query_space`** - Query a specific Space
5. **`get_system_info`** - Get Metatron system information
6. **`list_instructions`** - List available instruction sets

**Resources to Implement:**

1. **`router://{path}`** - Access Router objects
2. **`space://{space_name}/{path}`** - Access Space data
3. **`system://info`** - System information
4. **`system://stats`** - System statistics

**Prompts to Implement:**

1. **`code_explanation`** - Explain Metatron code
2. **`debug_help`** - Help debug Metatron code
3. **`optimization_suggestion`** - Suggest code optimizations

### Phase 3: Integration with MServer

**Goal:** Seamlessly integrate MCP handling into existing MServer.

**Approach:**

1. **Extend MServer** with MCP support:
   ```java
   public class MServer extends WebSocketServer {
       private McpServer mcpServer;
       private McpWebSocketTransport mcpTransport;

       public void enableMcp(ServerCapabilities capabilities) {
           this.mcpTransport = new McpWebSocketTransport(this);
           this.mcpServer = McpServer.sync(mcpTransport)
               .serverInfo("metatron-mcp", "1.0.0")
               .capabilities(capabilities)
               .build();

           // Register tools, resources, prompts
           registerMetatronTools();
       }
   }
   ```

2. **Message Routing** in `onMessage`:
   ```java
   @Override
   public void onMessage(WebSocket conn, String message) {
       if (isMcpMessage(message)) {
           mcpTransport.handleMessage(conn, message);
       } else {
           // Existing Metatron message handling
           super.onMessage(conn, message);
       }
   }
   ```

3. **Dual Protocol Support**:
   - MCP clients connect and use JSON-RPC
   - Native Metatron clients use binary Obj serialization
   - Both can coexist on same server

### Phase 4: Console Integration

**Goal:** Enable AI assistants to interact with Metatron Console.

**Components:**

1. **`ConsoleSession`** - Manages console state for MCP clients
   - Execution context
   - Variable bindings
   - History

2. **`ConsoleExecutor`** - Executes Metatron code
   - Parses and compiles code
   - Executes in isolated context
   - Returns formatted results

3. **`ResultFormatter`** - Formats results for AI consumption
   - Converts Obj to human-readable text
   - Handles errors gracefully
   - Provides context and explanations

### Phase 5: Client Configuration

**Goal:** Enable Claude Desktop and other MCP clients to connect.

**Claude Desktop Configuration** (`claude_desktop_config.json`):
```json
{
  "mcpServers": {
    "metatron": {
      "command": "java",
      "args": [
        "-jar",
        "/path/to/metatron.jar",
        "mcp-server",
        "--host", "localhost",
        "--port", "7777"
      ]
    }
  }
}
```

**Or WebSocket-based:**
```json
{
  "mcpServers": {
    "metatron": {
      "url": "ws://localhost:7777/mcp",
      "transport": "websocket"
    }
  }
}
```

## Technical Details

### Message Format

**MCP JSON-RPC Request:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/call",
  "params": {
    "name": "evaluate_code",
    "arguments": {
      "code": "1 + 2"
    }
  }
}
```

**MCP JSON-RPC Response:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "content": [
      {
        "type": "text",
        "text": "3"
      }
    ]
  }
}
```

**Metatron Obj Message:**
```
Binary serialized Obj (existing format)
```

### WebSocket Transport Implementation

**Key Classes:**

1. **`McpWebSocketTransportProvider`** - Implements MCP's transport provider interface
2. **`McpWebSocketSession`** - Manages individual client sessions
3. **`JsonRpcCodec`** - Encodes/decodes JSON-RPC messages
4. **`MessageRouter`** - Routes messages between MCP and Metatron

**Threading Model:**
- WebSocket I/O on MServer's thread pool
- MCP handlers execute synchronously (blocking)
- Long-running operations can use async API

### Security Considerations

1. **Authentication:**
   - Optional API key validation
   - Integration with existing Metatron auth (if any)
   - Per-session capability restrictions

2. **Sandboxing:**
   - Execute code in restricted context
   - Limit resource access
   - Timeout protection

3. **Rate Limiting:**
   - Per-client request limits
   - Execution time limits
   - Memory limits

## File Structure

```
src/main/java/studio/phaseshift/metatron/mcp/
├── transport/
│   ├── McpWebSocketTransportProvider.java
│   ├── McpWebSocketSession.java
│   ├── JsonRpcCodec.java
│   └── MessageRouter.java
├── tools/
│   ├── EvaluateCodeTool.java
│   ├── QueryRouterTool.java
│   ├── ListSpacesTool.java
│   └── GetSystemInfoTool.java
├── resources/
│   ├── RouterResourceProvider.java
│   ├── SpaceResourceProvider.java
│   └── SystemResourceProvider.java
├── prompts/
│   ├── CodeExplanationPrompt.java
│   ├── DebugHelpPrompt.java
│   └── OptimizationPrompt.java
├── console/
│   ├── ConsoleSession.java
│   ├── ConsoleExecutor.java
│   └── ResultFormatter.java
└── MetatronMcpServer.java (main integration class)
```

## Dependencies

**Add to pom.xml:**
```xml
<dependency>
    <groupId>io.modelcontextprotocol.sdk</groupId>
    <artifactId>mcp</artifactId>
    <version>1.1.0</version>
</dependency>
```

**Note:** The `mcp` artifact includes:
- `mcp-core` - Core MCP implementation
- `mcp-json-jackson3` - Jackson 3 JSON binding
- STDIO, SSE, and Streamable HTTP transports (we won't use these)

## Testing Strategy

### Unit Tests
- Test each tool individually
- Test resource providers
- Test message routing
- Test JSON-RPC codec

### Integration Tests
- Test full MCP handshake
- Test tool execution
- Test resource access
- Test error handling

### Manual Testing
- Connect with Claude Desktop
- Execute Metatron code
- Query system state
- Test concurrent clients

## Advantages of This Approach

1. **Native Integration** - Uses existing MServer infrastructure
2. **No Framework Lock-in** - No Spring, no Servlet containers
3. **Dual Protocol** - Supports both MCP and native Metatron clients
4. **Distributed Ready** - Works with Metatron's cluster architecture
5. **Flexible** - Can add more tools/resources easily
6. **Performant** - WebSocket is efficient for bidirectional communication

## Next Steps

1. ✅ Add MCP SDK dependency to pom.xml
2. 🔨 Implement `McpWebSocketTransportProvider`
3. 🔨 Implement basic tools (`evaluate_code`, `get_system_info`)
4. 🔨 Integrate with MServer
5. 🔨 Test with Claude Desktop
6. 🔨 Add more tools and resources
7. 🔨 Document usage and configuration

## Example Usage

**Starting Metatron with MCP:**
```bash
java -jar metatron.jar --mcp-enabled --mcp-port 7777
```

**From Claude Desktop:**
```
User: Can you execute this Metatron code: 1 + 2 + 3

Claude: I'll execute that for you using the Metatron MCP server.
[Uses evaluate_code tool]
Result: 6

User: What spaces are available?

Claude: Let me check the available spaces.
[Uses list_spaces tool]
Available spaces:
- /sys/router
- /http
- /mqtt
...
```

---

**Status**: Planning phase
**Priority**: High - enables AI-assisted Metatron development
**Complexity**: Medium - requires custom transport implementation
