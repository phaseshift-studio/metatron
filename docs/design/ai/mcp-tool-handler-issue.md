# MCP Tool Handler Invocation Issue

## STATUS: ✅ RESOLVED WITH CUSTOM DISPATCHER

**Update (March 24, 2026)**: We've successfully worked around the MCP Java SDK 1.1.0 bug by implementing a **custom JSON-RPC tool dispatcher** using Metatron's `ObjSimpleJSONSerializer`. The dispatcher intercepts `tools/call` requests and manually invokes tool handlers, bypassing the buggy SDK layer.

**See**: `docs/ai/mcp-custom-dispatcher-solution.md` for complete implementation details.

### Original Issue
After extensive testing with both sync and async APIs, we confirmed this is a **known bug in MCP Java SDK 1.1.0**. The SDK successfully receives and parses `tools/call` requests but **fails to invoke registered tool handlers**. This is documented in GitHub Issue #509 for StdioServerTransport, but appears to affect custom transports as well.

### Evidence
- ✅ MCP session created successfully
- ✅ `initialize` request handled correctly
- ✅ `tools/call` request received and parsed
- ✅ Tools registered with both `SyncToolSpecification` and `AsyncToolSpecification`
- ❌ **Tool handlers never invoked** (no log output from handlers)
- ❌ No response sent back to client

### What We Tried
1. ✅ Sync API with `McpServerFeatures.SyncToolSpecification`
2. ✅ Async API with `McpServerFeatures.AsyncToolSpecification` and `Mono<CallToolResult>`
3. ✅ Builder pattern matching working examples
4. ✅ Custom WebSocket transport (not STDIO)
5. ✅ Proper `CallToolResult` structure with `content` array

All approaches fail at the same point - the SDK doesn't dispatch to our handlers.

### Workarounds
1. **Wait for SDK fix** - Monitor MCP Java SDK releases for bug fixes
2. **Use mcp-annotated-java-sdk** - Different architecture, runs own server (not integrated with MServer)
3. **Implement custom MCP server** - Parse JSON-RPC ourselves, bypass SDK

## ORIGINAL INVESTIGATION ✅

### RECOMMENDED APPROACH: Use mcp-annotated-java-sdk

The **best solution** is to use the **mcp-annotated-java-sdk** instead of the official SDK directly:

**Repository**: https://github.com/thought2code/mcp-annotated-java-sdk

**Key Benefits:**
- 🚫 **No Spring Framework Required** - Pure Java, lightweight and fast
- ⚡ **Instant MCP Server** - Get your server running with just 1 line of code
- 🎉 **Zero Boilerplate** - No need to write low-level MCP SDK code
- 👏 **No JSON Schema** - Forget about complex and lengthy JSON definitions
- 🎯 **Focus on Logic** - Concentrate on your core business logic
- 📦 **Type-Safe** - Leverage Java's type system for compile-time safety

**Code Comparison:**
- Official SDK: ~50-100 lines per tool
- Annotated SDK: ~5-10 lines per tool

### Alternative: Official SDK Pattern

If you prefer to use the official SDK directly, here are the correct patterns:

#### Key Insights

1. **Use `SyncToolSpecification.builder()` pattern** - The working examples use the builder pattern correctly
2. **Handler signature** - The `callHandler` takes `(exchange, request)` parameters
3. **Return type** - Must return `McpSchema.CallToolResult` directly (not wrapped in anything)
4. **Declarative annotations** - Can use `@McpTool` and `@McpToolParam` annotations for cleaner code (via mcp-annotated-java-sdk)

### Working Example Pattern

```java
public static McpServerFeatures.SyncToolSpecification evaluateCode() throws IOException {
    // Step 1: Load or create JSON schema for tool input arguments
    String schema = "{ \"type\": \"object\", \"properties\": { \"code\": { \"type\": \"string\" } }, \"required\": [\"code\"] }";

    // Step 2: Create tool with name, description, and schema
    McpSchema.Tool tool = McpSchema.Tool.builder()
        .name("evaluate_code")
        .title("Evaluate Metatron Code")
        .description("Execute Metatron code and return results")
        .inputSchema(McpJsonMapper.getDefault(), schema)
        .build();

    // Step 3: Create tool specification with handler
    return McpServerFeatures.SyncToolSpecification.builder()
        .tool(tool)
        .callHandler((exchange, request) -> {
            // Step 4: Extract arguments and execute
            Map<String, Object> arguments = request.arguments();
            Object code = arguments.get("code");

            if (code == null || code.toString().isBlank()) {
                return error("Please provide valid code to evaluate.");
            }

            try {
                // Execute code and return result
                String result = executeMetatronCode(code.toString());
                return success(result);
            } catch (Exception e) {
                return error("Error executing code: " + e.getMessage());
            }
        })
        .build();
}

private static McpSchema.CallToolResult success(String result) {
    McpSchema.Content content = new McpSchema.TextContent(result);
    return McpSchema.CallToolResult.builder()
        .content(List.of(content))
        .isError(false)
        .build();
}

private static McpSchema.CallToolResult error(String result) {
    McpSchema.Content content = new McpSchema.TextContent(result);
    return McpSchema.CallToolResult.builder()
        .content(List.of(content))
        .isError(true)
        .build();
}
```

### Recommended: Annotated SDK Approach (Simplest)

Using **mcp-annotated-java-sdk** (version 0.13.0):

**Step 1: Add dependency to pom.xml**
```xml
<dependency>
    <groupId>io.github.thought2code</groupId>
    <artifactId>mcp-annotated-java-sdk</artifactId>
    <version>0.13.0</version>
</dependency>
```

**Step 2: Create configuration file** (`src/main/resources/mcp-server.yml`)
```yaml
enabled: true
mode: STREAMABLE  # or STDIO for CLI
name: metatron-mcp-server
version: 1.0.0
type: SYNC
request-timeout: 20000
capabilities:
  resource: true
  prompt: true
  tool: true
change-notification:
  resource: true
  prompt: true
  tool: true
streamable:
  mcp-endpoint: /mcp/message
  port: 8080
```

**Step 3: Create main server class**
```java
@McpServerApplication
public class MetatronMcpServer {
    public static void main(String[] args) {
        McpApplication.run(MetatronMcpServer.class, args);
    }
}
```

**Step 4: Define tools with annotations**
```java
public class MetatronTools {

    @McpTool(description = "Execute Metatron code and return results")
    public String evaluateCode(
        @McpToolParam(
            name = "code",
            description = "The Metatron code to execute",
            required = true
        )
        String code
    ) {
        if (code == null || code.isBlank()) {
            return "Please provide valid code to evaluate.";
        }

        try {
            // Execute Metatron code
            return executeMetatronCode(code);
        } catch (Exception e) {
            return "Error executing code: " + e.getMessage();
        }
    }

    @McpTool(description = "Get information about the Metatron system")
    public Map<String, String> getSystemInfo() {
        Map<String, String> info = new HashMap<>();
        info.put("version", "1.0.0");
        info.put("instructions", String.valueOf(getInstructionCount()));
        return info;
    }

    @McpTool(description = "List available Metatron instructions")
    public List<String> listInstructions(
        @McpToolParam(
            name = "filter",
            description = "Optional filter pattern",
            required = false
        )
        String filter
    ) {
        // Return list of instructions
        return getInstructions(filter);
    }
}
```

**Step 5: Define resources (optional)**
```java
public class MetatronResources {

    @McpResource(
        uri = "metatron://system/info",
        description = "Metatron system information"
    )
    public Map<String, Object> getSystemInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("name", "Metatron");
        info.put("version", "1.0.0");
        info.put("capabilities", List.of("relations", "instructions", "types"));
        return info;
    }
}
```

**That's it!** No JSON schemas, no manual handler registration, no boilerplate.

### Integration with Metatron's MServer

To integrate with the existing WebSocket infrastructure, you can:

1. **Option A: Run as separate service** - Run the annotated MCP server on a different port and proxy requests from MServer
2. **Option B: Embed in MServer** - Use the annotated SDK's components but integrate with MServer's WebSocket handling
3. **Option C: Dual mode** - Support both native Metatron protocol and MCP protocol on the same WebSocket

### References

- **Annotated SDK Repository**: https://github.com/thought2code/mcp-annotated-java-sdk
- **Annotated SDK Documentation**: https://thought2code.github.io/mcp-annotated-java-sdk-docs
- **Official SDK Implementation**: https://github.com/thought2code/mcp-java-sdk-examples/blob/main/mcp-server-filesystem/mcp-server-filesystem-official-sdk-implementation/src/main/java/com/github/mcp/server/filesystem/official/Tools.java
- **Annotated SDK Example**: https://github.com/thought2code/mcp-java-sdk-examples/blob/main/mcp-server-filesystem/mcp-server-filesystem-annotated-sdk-implementation/src/main/java/com/github/mcp/server/filesystem/annotated/Tools.java

---

## Original Issue Documentation

# MCP Tool Handler Invocation Issue

## Problem Summary

The MCP server successfully receives `tools/call` requests from clients, but the registered tool handlers are **never being invoked**. The test times out waiting for a response that never comes.

## What Works

1. ✅ **Protocol Detection**: MCP messages are correctly identified and routed to MCP handler
2. ✅ **Session Creation**: MCP sessions are created successfully via the transport provider
3. ✅ **Initialize Handshake**: The `initialize` request/response works perfectly
4. ✅ **Message Parsing**: `tools/call` requests are correctly parsed as `JSONRPCRequest`
5. ✅ **Session Handling**: `session.handle(jsonRpcMessage)` is called without errors
6. ✅ **Tool Registration**: Tools are registered using `McpServerFeatures.SyncToolSpecification`

## What Doesn't Work

❌ **Tool Handler Invocation**: When a `tools/call` request is received, the registered tool handler function is never called.

## Evidence

### Test Logs Show:
```
[DEBUG] Received request: JSONRPCRequest[jsonrpc=2.0, method=tools/call, id=2, params={name=get_system_info, arguments={}}]
```

### But NO Log From Tool Handler:
```
[INFO ] [MetatronMcpServer] get_system_info tool handler invoked  // <-- NEVER APPEARS
```

## Attempts Made

### 1. Fixed ClassCastException Issues
- Changed `(String) args.get("code")` to `args.get("code").toString()`
- Changed `conn.getAttachment()` to `conn.getAttachment().toString()`
- **Result**: Fixed the exceptions, but tool handlers still not invoked

### 2. Changed API from `.toolCall()` to `.tools()`
Based on MCP Java SDK documentation, changed from:
```java
.toolCall(tool, handler)
```
To:
```java
.tools(new McpServerFeatures.SyncToolSpecification(tool, handler))
```
- **Result**: No change, tool handlers still not invoked

### 3. Tried Different Reactive Approaches
- Tried `.block()` on `session.handle()` - caused InterruptedException
- Tried `.subscribe()` on `session.handle()` - no invocation
- **Result**: Neither approach causes tool handlers to be invoked

### 4. Added Extensive Logging
- Added logging in transport, server, and tool handlers
- Confirmed message flow reaches `session.handle()`
- Confirmed tool handlers have logging that would fire if called
- **Result**: Logs confirm handlers are never reached

## Current Code Structure

### Tool Registration (MetatronMcpServer.java)
```java
private McpSyncServer buildMcpServer() {
    return McpServer.sync(transportProvider)
        .serverInfo(SERVER_NAME, SERVER_VERSION)
        .capabilities(ServerCapabilities.builder()
            .tools(true)
            .build())
        .tools(new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("get_system_info")
                .description("...")
                .inputSchema(McpJsonDefaults.getMapper(), schemaJson)
                .build(),
            (exchange, request) -> {
                LOG.info("get_system_info tool handler invoked");  // NEVER CALLED
                // ... handler implementation
                return McpSchema.CallToolResult.builder()
                    .content(List.of(new McpSchema.TextContent(info)))
                    .build();
            }
        ))
        .build();
}
```

### Message Handling (McpWebSocketTransport.java)
```java
public void handleIncomingMessage(final String message) {
    if (session != null) {
        // Parse message to JSONRPCRequest
        final McpSchema.JSONRPCMessage jsonRpcMessage = ...;

        // Pass to session - this executes without error
        session.handle(jsonRpcMessage)
            .doOnSuccess(v -> LOG.debug("Session handled message successfully"))
            .subscribe();
    }
}
```

## Hypothesis

The MCP Java SDK's `McpServerSession` is receiving the `tools/call` request but is not routing it to the registered tool handlers. This could be due to:

1. **API Mismatch**: We might be using the wrong API for tool registration
2. **SDK Bug**: The GitHub issue "Tool call requests intermittently stuck in SDK" (#770) suggests this is a known problem
3. **Missing Step**: There might be a required initialization or configuration step we're missing
4. **Version Issue**: The SDK version (1.1.0) might have this bug

## Questions for Investigation

1. Is there a different method to register tools that actually works?
2. Do we need to manually route `tools/call` requests to handlers?
3. Is there an example of working MCP server tool registration in Java?
4. Should we be using `McpAsyncServer` instead of `McpSyncServer`?
5. Is there a way to inspect what tools the session thinks are registered?

## Test Results

- **Total Tests**: 30
- **Passing**: 27 (90%)
- **Failing**: 3
  - `testMcpGetSystemInfoTool` - Tool handler not invoked
  - `testMcpListInstructionsTool` - Tool handler not invoked
  - `testNativeProtocolErrorHandling` - Different issue

## Next Steps

Need to either:
1. Find the correct API for tool registration that actually works
2. Manually implement tool routing if the SDK doesn't support it
3. File a bug report with the MCP Java SDK team
4. Consider using a different MCP SDK version or implementation
