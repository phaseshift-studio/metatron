# MCP Custom JSON-RPC Tool Dispatcher - Solution

## Status: ✅ WORKING

**Date**: March 24, 2026
**Solution**: Custom JSON-RPC dispatcher using `ObjSimpleJSONSerializer`

## Problem Summary

The MCP Java SDK 1.1.0 has a known bug (GitHub Issue #509) where tool handlers registered with the SDK are never invoked when `tools/call` requests arrive. The SDK successfully:
- ✅ Creates sessions
- ✅ Handles `initialize` requests
- ✅ Parses `tools/call` requests
- ❌ **Fails to invoke registered tool handlers**

## Solution Architecture

We implemented a **custom JSON-RPC 2.0 tool dispatcher** that bypasses the buggy SDK tool invocation layer while still using the SDK for protocol compliance and session management.

### Key Components

1. **`JsonRpcToolDispatcher`** (`src/main/java/studio/phaseshift/metatron/isa/mach/type/net/mcp/JsonRpcToolDispatcher.java`)
   - Custom JSON-RPC 2.0 parser using Metatron's `ObjSimpleJSONSerializer`
   - Maintains registry of tool handlers
   - Manually routes `tools/call` requests to handlers
   - Constructs proper JSON-RPC responses

2. **`McpWebSocketTransport`** (modified)
   - Intercepts incoming messages
   - Checks if message is a `tools/call` request
   - Routes to custom dispatcher if yes, SDK if no

3. **`MetatronMcpServer`** (modified)
   - Registers tools with both SDK (for `tools/list`) and custom dispatcher (for actual invocation)
   - Provides tool implementations for: `evaluate_code`, `get_system_info`, `list_instructions`

4. **`McpWebSocketTransportProvider`** (modified)
   - Passes dispatcher reference to transports

### Message Flow

```
WebSocket Message
    ↓
McpProtocolHandler
    ↓
McpWebSocketTransport.handleIncomingMessage()
    ↓
    ├─→ Is "tools/call"? → JsonRpcToolDispatcher.handleToolCall()
    │                           ↓
    │                      Parse with ObjSimpleJSONSerializer
    │                           ↓
    │                      Invoke registered handler
    │                           ↓
    │                      Build JSON-RPC response
    │                           ↓
    │                      Send directly to WebSocket
    │
    └─→ Other methods → MCP SDK session.handle()
                            ↓
                       SDK handles (initialize, tools/list, etc.)
```

## Implementation Details

### JSON-RPC 2.0 Parsing

The dispatcher uses `ObjSimpleJSONSerializer` to:
1. Parse incoming JSON-RPC request to Metatron `Obj` types
2. Extract `method`, `params`, `id` fields
3. Extract tool `name` and `arguments` from params
4. Convert arguments to Java `Map<String, Object>`

### Response Construction

Responses are built by:
1. Invoking the tool handler with arguments
2. Getting `McpSchema.CallToolResult` from handler
3. Converting to JSON structure with `result.content` array
4. Serializing back to JSON using `ObjSimpleJSONSerializer`
5. Sending directly to WebSocket (bypassing SDK)

### Error Handling

Standard JSON-RPC 2.0 error codes:
- `-32700`: Parse error (invalid JSON)
- `-32600`: Invalid Request
- `-32601`: Method not found (tool not registered)
- `-32602`: Invalid params
- `-32603`: Internal error

## Test Results

All MCP tool tests now pass:

```
✅ testMcpGetSystemInfoTool - PASSING
✅ testMcpListInstructionsTool - PASSING
✅ testMcpEvaluateCodeTool - PASSING
✅ testMcpProtocolDetection - PASSING
✅ testNativeProtocolDetection - PASSING
✅ testProtocolPriority - PASSING
✅ testBinaryMessageRouting - PASSING
```

**Build Status**: ✅ BUILD SUCCESS

## Available MCP Tools

### 1. evaluate_code
**Description**: Evaluate Metatron code and return the result
**Parameters**:
- `code` (string, required): The Metatron code to execute

**Example**:
```json
{
  "jsonrpc": "2.0",
  "method": "tools/call",
  "id": 1,
  "params": {
    "name": "evaluate_code",
    "arguments": {
      "code": "1.plus(2)"
    }
  }
}
```

### 2. get_system_info
**Description**: Get information about the Metatron system
**Parameters**: None

**Example**:
```json
{
  "jsonrpc": "2.0",
  "method": "tools/call",
  "id": 2,
  "params": {
    "name": "get_system_info",
    "arguments": {}
  }
}
```

### 3. list_instructions
**Description**: List available Metatron instruction types
**Parameters**:
- `filter` (string, optional): Filter to search for specific instructions

**Example**:
```json
{
  "jsonrpc": "2.0",
  "method": "tools/call",
  "id": 3,
  "params": {
    "name": "list_instructions",
    "arguments": {
      "filter": "arithmetic"
    }
  }
}
```

## Connection Information

### WebSocket Endpoint
The MCP server runs on the same WebSocket endpoint as the native Metatron protocol:

```
ws://localhost:<port>
```

The port is dynamically assigned when MServer starts. Check the logs for:
```
[INFO] [/sys/router/server] starting mtrOn node ws://localhost:<port>
```

### Protocol Detection
The server automatically detects which protocol to use:
- **MCP**: Messages containing `"jsonrpc": "2.0"`
- **Native**: Binary Obj serialization messages

### MCP Handshake
1. Connect to WebSocket
2. Send `initialize` request:
```json
{
  "jsonrpc": "2.0",
  "method": "initialize",
  "id": 1,
  "params": {
    "protocolVersion": "2024-11-05",
    "capabilities": {},
    "clientInfo": {
      "name": "your-client-name",
      "version": "1.0.0"
    }
  }
}
```
3. Receive `initialize` response with server capabilities
4. Call tools using `tools/call` method

## Adding New Tools

To add a new MCP tool:

1. **Register with dispatcher** in `MetatronMcpServer.registerToolsWithDispatcher()`:
```java
toolDispatcher.registerTool(
    McpSchema.Tool.builder()
        .name("my_new_tool")
        .description("Description of what the tool does")
        .inputSchema(McpJsonDefaults.getMapper(), createMyToolSchemaJson())
        .build(),
    args -> {
        // Tool implementation
        String param = args.get("param_name").toString();

        // Do work...

        return McpSchema.CallToolResult.builder()
            .content(List.of(new McpSchema.TextContent(result)))
            .isError(false)
            .build();
    }
);
```

2. **Register with SDK** (for `tools/list` support) in `MetatronMcpServer.buildMcpServer()`:
```java
.tools(McpServerFeatures.AsyncToolSpecification.builder()
    .tool(McpSchema.Tool.builder()
        .name("my_new_tool")
        .description("Description of what the tool does")
        .inputSchema(McpJsonDefaults.getMapper(), createMyToolSchemaJson())
        .build())
    .callHandler((exchange, request) -> Mono.fromCallable(() -> {
        // Placeholder - won't be called due to SDK bug
        return McpSchema.CallToolResult.builder()
            .content(List.of(new McpSchema.TextContent("SDK handler")))
            .build();
    }))
    .build())
```

3. **Create JSON schema** for input validation:
```java
private String createMyToolSchemaJson() {
    return """
           {
             "type": "object",
             "properties": {
               "param_name": {
                 "type": "string",
                 "description": "Parameter description"
               }
             },
             "required": ["param_name"]
           }
           """;
}
```

## Benefits of This Approach

1. ✅ **Works around SDK bug** - Tool handlers are actually invoked
2. ✅ **Uses existing infrastructure** - Leverages `ObjSimpleJSONSerializer`
3. ✅ **Maintains SDK compatibility** - Still uses SDK for session management
4. ✅ **Proper JSON-RPC 2.0** - Fully compliant with specification
5. ✅ **Easy to extend** - Simple API for adding new tools
6. ✅ **Type-safe** - Uses Metatron's type system
7. ✅ **Well-tested** - All tests passing

## Future Improvements

When MCP Java SDK is fixed (version 1.1.1+):
1. Remove custom dispatcher
2. Use SDK's built-in tool invocation
3. Keep the dual-registration pattern for backward compatibility

## References

- **MCP Specification**: https://modelcontextprotocol.io/
- **JSON-RPC 2.0 Spec**: https://www.jsonrpc.org/specification
- **MCP Java SDK**: https://github.com/modelcontextprotocol/java-sdk
- **SDK Bug Report**: GitHub Issue #509 (StdioServerTransport tool handler bug)
- **Metatron JSON Serializers**:
  - `ObjSimpleJSONSerializer.java`
  - `ObjJSONSerializer.java`

## Log Evidence of Success

```
[INFO ] [McpWebSocketTransport] Intercepting tools/call request for custom dispatcher
[DEBUG] [JsonRpcToolDispatcher] Handling tool call request: {"method":"tools/call",...}
[INFO ] [JsonRpcToolDispatcher] Dispatching tool call: get_system_info
[DEBUG] [JsonRpcToolDispatcher] Invoking tool handler for: get_system_info with arguments: {}
[INFO ] [MetatronMcpServer] get_system_info tool handler invoked via dispatcher
[DEBUG] [JsonRpcToolDispatcher] Created success response: {"result":{"isError":false,...}}
[DEBUG] [McpWebSocketTransport] Sending dispatcher response: {...}
```

**The tool handlers are being invoked!** 🎉
