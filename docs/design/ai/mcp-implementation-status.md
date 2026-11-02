# MCP Server Implementation Status

## Summary

We're building an MCP (Model Context Protocol) server integrated with Metatron's existing MServer WebSocket infrastructure. This will allow AI assistants like Claude to interact with Metatron in real-time.

## Completed

### 1. ✅ Research and Planning
- Studied MCP Java SDK documentation
- Analyzed Metatron's MServer architecture
- Created comprehensive integration plan (`mcp-mserver-integration-plan.md`)

### 2. ✅ Dependencies
- Added MCP SDK dependency to `pom.xml`:
  ```xml
  <dependency>
      <groupId>io.modelcontextprotocol.sdk</groupId>
      <artifactId>mcp</artifactId>
      <version>1.1.0</version>
  </dependency>
  ```

### 3. 🔨 Initial Structure
- Need to study MCP Java SDK API before implementing
- Incorrect initial attempt removed (used Kotlin packages instead of Java)

## Architecture Overview

### Integration Strategy

**Key Insight**: Instead of using MCP's built-in transports (STDIO, SSE, HTTP), we're creating a **custom WebSocket transport** that integrates directly with Metatron's MServer.

**Benefits**:
- ✅ Leverages existing MServer WebSocket infrastructure
- ✅ No dependency on Spring or Servlet containers
- ✅ Dual protocol support (MCP JSON-RPC + native Metatron Obj)
- ✅ Works with Metatron's distributed cluster architecture

### Message Flow

```
AI Client (Claude)
    │
    │ WebSocket (JSON-RPC)
    ▼
MServer (WebSocket Server)
    │
    ├─► MCP Message? ──► McpWebSocketTransport ──► McpServer ──► Tools/Resources
    │                                                                    │
    └─► Metatron Obj? ─► Existing Handler ──────────────────────────────┘
                                                                         │
                                                                         ▼
                                                              Metatron Console/Router
```

## What Needs to Be Built

### Phase 1: Custom WebSocket Transport (CRITICAL)

This is the core integration piece that bridges MCP and MServer.

**Files to Create:**

1. **`McpWebSocketTransportProvider.java`**
   - Implements MCP's `TransportProvider` interface
   - Manages WebSocket connections from MServer
   - Routes JSON-RPC messages to/from MCP server

   **Key Methods:**
   ```java
   public class McpWebSocketTransportProvider implements TransportProvider {
       private final MServer mserver;
       private final Map<WebSocket, McpWebSocketSession> sessions;

       // Called by MServer when WebSocket message arrives
       public void handleMessage(WebSocket conn, String jsonMessage);

       // Called by MCP server to send response
       public void sendMessage(WebSocket conn, String jsonMessage);
   }
   ```

2. **`McpWebSocketSession.java`**
   - Represents a single MCP client session
   - Tracks client capabilities
   - Manages request/response correlation

   **Key Methods:**
   ```java
   public class McpWebSocketSession {
       private final WebSocket connection;
       private final Map<String, CompletableFuture<JsonNode>> pendingRequests;
       private ClientCapabilities clientCapabilities;

       public void handleRequest(JsonNode request);
       public void handleResponse(JsonNode response);
       public void handleNotification(JsonNode notification);
   }
   ```

3. **`JsonRpcCodec.java`**
   - Encodes/decodes JSON-RPC 2.0 messages
   - Validates message format

   **Key Methods:**
   ```java
   public class JsonRpcCodec {
       public static JsonNode decode(String json);
       public static String encode(JsonNode message);
       public static boolean isRequest(JsonNode message);
       public static boolean isResponse(JsonNode message);
       public static boolean isNotification(JsonNode message);
   }
   ```

### Phase 2: MServer Integration

**Modify `MServer.java`** to support MCP:

```java
public class MServer extends WebSocketServer {
    private MetatronMcpServer mcpServer;

    // Enable MCP support
    public void enableMcp() {
        this.mcpServer = new MetatronMcpServer(this);
        this.mcpServer.start();
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        // Detect message type
        if (isMcpMessage(message)) {
            // Route to MCP handler
            mcpServer.getTransport().handleMessage(conn, message);
        } else {
            // Existing Metatron message handling
            super.onMessage(conn, ByteBuffer.wrap(message.getBytes()));
        }
    }

    private boolean isMcpMessage(String message) {
        // MCP messages are JSON-RPC 2.0
        return message.trim().startsWith("{") &&
               message.contains("\"jsonrpc\"") &&
               message.contains("\"2.0\"");
    }
}
```

### Phase 3: Tools Implementation

**Files to Create:**

1. **`EvaluateCodeTool.java`** (PRIORITY)
   ```java
   public class EvaluateCodeTool implements SyncToolSpecification {
       @Override
       public Tool tool() {
           return Tool.builder()
               .name("evaluate_code")
               .description("Execute Metatron code and return results")
               .inputSchema(/* JSON schema */)
               .build();
       }

       @Override
       public CallToolResult call(McpSyncServerExchange exchange, CallToolRequest request) {
           String code = (String) request.arguments().get("code");
           // Execute code using Metatron's parser/compiler
           Obj result = executeMetatronCode(code);
           return CallToolResult.builder()
               .content(List.of(new TextContent(result.toString())))
               .build();
       }
   }
   ```

2. **`GetSystemInfoTool.java`**
   - Returns Metatron version, uptime, memory usage, etc.

3. **`QueryRouterTool.java`**
   - Queries Router for objects at a given path

4. **`ListSpacesTool.java`**
   - Lists available Spaces (HTTP, MQTT, etc.)

### Phase 4: Console Integration

**Files to Create:**

1. **`ConsoleSession.java`**
   - Manages execution context for MCP clients
   - Tracks variables, history, etc.

2. **`ConsoleExecutor.java`**
   - Parses and executes Metatron code
   - Handles errors gracefully
   - Returns formatted results

3. **`ResultFormatter.java`**
   - Converts Obj to human-readable text
   - Formats errors for AI consumption

## Technical Challenges

### Challenge 1: Transport Interface Mismatch

**Problem**: MCP SDK expects specific transport interfaces that may not match MServer's API.

**Solution**: Create adapter layer (`McpWebSocketTransportProvider`) that translates between:
- MServer's `WebSocket` → MCP's session concept
- MServer's message callbacks → MCP's request/response model

### Challenge 2: JSON-RPC Message Correlation

**Problem**: JSON-RPC uses request IDs to correlate requests/responses. MServer doesn't have this concept.

**Solution**: `McpWebSocketSession` maintains a map of pending requests:
```java
Map<String, CompletableFuture<JsonNode>> pendingRequests
```

### Challenge 3: Dual Protocol Support

**Problem**: MServer needs to handle both MCP JSON-RPC and native Metatron Obj messages.

**Solution**: Message type detection in `onMessage`:
- JSON-RPC messages: Start with `{` and contain `"jsonrpc": "2.0"`
- Metatron Obj messages: Binary format (existing)

### Challenge 4: Synchronous vs Asynchronous

**Problem**: MCP SDK supports both sync and async APIs. MServer is event-driven.

**Solution**: Use MCP's **synchronous API** (`McpSyncServer`) for simplicity:
- Handlers block until complete
- Simpler to implement
- Can upgrade to async later if needed

## Next Steps (Priority Order)

### Step 0: Study MCP Java SDK API (CRITICAL FIRST STEP) ✅ DONE

**Findings from studying the SDK source:**

1. **Correct Java Packages** (not Kotlin):
   - `io.modelcontextprotocol.server.McpServer` - Main server factory
   - `io.modelcontextprotocol.spec.McpServerTransportProvider` - Transport interface
   - `io.modelcontextprotocol.spec.McpServerTransportProviderBase` - Base transport interface
   - `io.modelcontextprotocol.spec.McpSchema` - Schema definitions (Tool, Resource, etc.)

2. **Server Creation Pattern**:
   ```java
   McpSyncServer server = McpServer.sync(transportProvider)
       .serverInfo("metatron-mcp", "1.0.0")
       .capabilities(ServerCapabilities.builder()
           .tools(true)
           .resources(false, true)
           .build())
       .toolCall(tool, handler)
       .build();
   ```

3. **Transport Provider Interface**:
   - Must implement `McpServerTransportProvider`
   - Must call `setSessionFactory(McpServerSession.Factory)` before interactions
   - Must implement `notifyClients()`, `closeGracefully()`, etc.
   - Creates `McpServerTransport` instances for each client session

4. **Key Insight**:
   - The transport provider is a **factory** for creating transports
   - Each client connection gets its own `McpServerTransport`
   - The session factory is provided by the MCP server after creation

**Next Steps**: Now we can implement the custom WebSocket transport with correct API

### Immediate (Required for MVP)

1. **Implement `McpWebSocketTransportProvider`**
   - This is the critical bridge between MServer and MCP
   - Without this, nothing else works

2. **Implement `JsonRpcCodec`**
   - Needed to parse/encode JSON-RPC messages

3. **Implement `McpWebSocketSession`**
   - Manages individual client sessions

4. **Modify `MServer.onMessage`**
   - Add message type detection
   - Route MCP messages to transport

5. **Implement `EvaluateCodeTool`**
   - The most important tool for AI interaction
   - Allows executing Metatron code

6. **Test with simple MCP client**
   - Before trying Claude Desktop, test with a simple client
   - Verify handshake, tool discovery, tool execution

### Short-term (Enhanced Functionality)

7. Implement `GetSystemInfoTool`
8. Implement `QueryRouterTool`
9. Implement `ConsoleSession` and `ConsoleExecutor`
10. Test with Claude Desktop

### Medium-term (Full Features)

11. Implement remaining tools (ListSpaces, QuerySpace, etc.)
12. Implement Resources (router://, space://, system://)
13. Implement Prompts (code_explanation, debug_help, etc.)
14. Add authentication/authorization
15. Add rate limiting and sandboxing

## Testing Strategy

### Unit Tests
- Test `JsonRpcCodec` encoding/decoding
- Test `McpWebSocketSession` request correlation
- Test each tool individually

### Integration Tests
- Test MCP handshake (initialize)
- Test tool discovery (tools/list)
- Test tool execution (tools/call)
- Test error handling

### Manual Testing
1. Start Metatron with MCP enabled
2. Connect with simple WebSocket client
3. Send JSON-RPC initialize request
4. Verify capabilities response
5. Call evaluate_code tool
6. Verify result

### Claude Desktop Testing
1. Configure Claude Desktop with Metatron MCP server
2. Ask Claude to execute Metatron code
3. Verify results appear correctly
4. Test error handling

## Example Usage (Future)

**Starting Metatron with MCP:**
```bash
java -jar metatron.jar --mcp-enabled --port 7777
```

**From Claude Desktop:**
```
User: Can you execute this Metatron code: 1 + 2 + 3

Claude: I'll execute that for you.
[Calls evaluate_code tool with code="1 + 2 + 3"]
Result: 6

User: What's the current system status?

Claude: Let me check.
[Calls get_system_info tool]
Metatron v1.0
Uptime: 2 hours
Memory: 512MB / 2GB
Active connections: 3
```

## Key Design Decisions

### 1. WebSocket over STDIO
- **Why**: MServer already has WebSocket infrastructure
- **Benefit**: No need for process spawning, better for distributed systems

### 2. Synchronous API
- **Why**: Simpler to implement and debug
- **Benefit**: Can upgrade to async later if needed

### 3. Dual Protocol Support
- **Why**: Don't break existing Metatron clients
- **Benefit**: MCP and native clients can coexist

### 4. No Spring Dependency
- **Why**: User's preference to avoid Spring
- **Benefit**: Lighter weight, more control

## Resources

- **MCP Specification**: https://spec.modelcontextprotocol.io/
- **MCP Java SDK**: https://github.com/modelcontextprotocol/java-sdk
- **MCP Java SDK Docs**: https://java.sdk.modelcontextprotocol.io/latest/
- **Integration Plan**: `docs/ai/mcp-mserver-integration-plan.md`

---

**Status**: Foundation laid, transport layer implementation needed
**Next Critical Step**: Implement `McpWebSocketTransportProvider`
**Estimated Effort**: 2-3 days for MVP (transport + basic tools)
