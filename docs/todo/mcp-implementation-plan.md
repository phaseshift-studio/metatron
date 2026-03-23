# Metatron MCP Server Implementation Plan

## Current Status

We've started implementing an MCP (Model Context Protocol) server for Metatron to enable AI assistants to interact with the Metatron console in real-time.

### What's Been Done

1. ✅ **Added MCP SDK Dependency** to `pom.xml`
   - Dependency: `io.modelcontextprotocol.sdk:mcp:0.15.0`
   - No Spring dependencies (as requested)

2. ✅ **Created Documentation** (`docs/mcp-server.md`)
   - Comprehensive guide on what MCP is
   - Usage instructions for Claude Desktop and other clients
   - Architecture overview
   - Security considerations
   - Development guide

3. ⚠️ **Initial Server Implementation** (needs work)
   - Created package: `studio.phaseshift.metatron.mcp`
   - Attempted implementation hit API compatibility issues
   - Need to study the actual MCP Java SDK API

## The Challenge

The official Java MCP SDK API is different from what I initially expected based on the documentation. We need to:

1. **Study the actual SDK** - Look at real examples and the actual Java classes
2. **Build a minimal working server** - Start simple, then expand
3. **Test incrementally** - Make sure each piece works before adding more

## Recommended Next Steps

### Option 1: Use Official Examples (Recommended)

The best approach is to look at working examples from the MCP Java SDK repository:

```bash
# Clone the official examples
git clone https://github.com/modelcontextprotocol/java-sdk.git /tmp/mcp-java-sdk

# Look at the samples
ls /tmp/mcp-java-sdk/samples/

# Study a working server implementation
cat /tmp/mcp-java-sdk/samples/*/src/main/java/**/*Server.java
```

Then adapt one of these working examples to Metatron's needs.

### Option 2: Start with JSON-RPC Directly

Since MCP is built on JSON-RPC 2.0, we could implement it more directly:

1. **Use a simple JSON-RPC library** (like `jsonrpc4j`)
2. **Implement the MCP protocol manually** following the spec
3. **Full control** over the implementation
4. **More work** but clearer understanding

### Option 3: Wait for Better Documentation

The MCP Java SDK is relatively new (released in late 2024). We could:

1. **Wait for more examples** to be published
2. **Ask the community** for Java implementation guidance
3. **Use TypeScript/Python** examples as reference

## What We Need from the MCP SDK

At minimum, the server needs to:

### 1. Transport Layer
```java
// STDIO transport for local communication
StdioServerTransport transport = new StdioServerTransport();
```

### 2. Server Creation
```java
// Create server with capabilities
McpServer server = McpServer.builder()
    .transport(transport)
    .serverInfo("metatron", "0.1.0")
    .capabilities(...)
    .build();
```

### 3. Tool Registration
```java
// Register tools that AI can call
server.addTool("eval",
    "Evaluate Metatron code",
    inputSchema,
    (args) -> {
        String code = args.get("code");
        return parser.eval(code).toString();
    });
```

### 4. Server Lifecycle
```java
// Start server (blocks until shutdown)
server.start();
```

## Proposed Metatron MCP Tools

Once we get the server working, here are the tools we should expose:

### Core Tools

1. **`eval`** - Evaluate Metatron code
   ```
   Input: { "code": "[1,2,3].plus([4,5,6])" }
   Output: "[1,2,3,4,5,6]"
   ```

2. **`parse`** - Parse code without evaluating
   ```
   Input: { "code": "a=>b" }
   Output: "Rel(a, b)"
   ```

3. **`type_check`** - Check types of expressions
   ```
   Input: { "code": "[1,2,3]" }
   Output: "Lst<Int>"
   ```

### System Tools

4. **`system_info`** - Get system status
5. **`list_instructions`** - Browse available instructions
6. **`instruction_help`** - Get help for specific instructions
7. **`list_types`** - Show available types

### Advanced Tools (Future)

8. **`debug`** - Step through code execution
9. **`profile`** - Performance profiling
10. **`visualize_graph`** - Export graph structures
11. **`search_docs`** - Search documentation

## Resources to Expose

Resources provide read-only access to documentation:

1. **`metatron://docs/readme`** - Main README
2. **`metatron://docs/ring-theory`** - Computational Ring Theory paper
3. **`metatron://docs/rel-enhancements`** - Relation enhancements roadmap
4. **`metatron://examples/*`** - Code examples
5. **`metatron://tests/*`** - Test cases as examples

## Prompts to Provide

Prompts guide users through common workflows:

1. **`explore_metatron`** - Interactive introduction
2. **`debug_code`** - Help debug failing code
3. **`learn_relations`** - Tutorial on relation algebra
4. **`build_graph`** - Guide to building graph structures
5. **`optimize_code`** - Performance optimization tips

## Architecture Decisions

### Why No Spring?

As requested, we're avoiding Spring because:
- ✅ **Lighter weight** - No framework overhead
- ✅ **More control** - Direct access to MCP SDK
- ✅ **Simpler** - No magic, just straightforward Java
- ✅ **Faster startup** - No Spring context initialization

### Transport Choice: STDIO

We're using STDIO (Standard Input/Output) transport because:
- ✅ **Local only** - Secure by default (no network exposure)
- ✅ **Simple** - No HTTP server needed
- ✅ **Fast** - Direct process communication
- ✅ **Standard** - Works with all MCP clients

Future: We can add HTTP/SSE transport for remote access later.

### Session Management

Current plan: **Stateless**
- Each tool call is independent
- Parser/InstSet created per request
- Simple and reliable

Future: **Stateful sessions**
- Maintain REPL state across calls
- Remember variables and context
- More complex but more powerful

## Security Considerations

### Current Approach
- **Local only** - STDIO transport keeps it on the same machine
- **User approval** - AI clients ask permission before executing tools
- **Full access** - Tools have complete access to Metatron

### Future Enhancements
- **Sandboxing** - Limit what code can do
- **Rate limiting** - Prevent abuse
- **Audit logging** - Track all tool executions
- **Authentication** - For remote access
- **Authorization** - Fine-grained permissions

## Testing Strategy

### Phase 1: Manual Testing
1. Build the server
2. Run it standalone
3. Use MCP Inspector to test tools
4. Verify responses

### Phase 2: Integration Testing
1. Connect to Claude Desktop
2. Test each tool through conversation
3. Verify error handling
4. Check performance

### Phase 3: Automated Testing
1. Unit tests for each tool
2. Integration tests for MCP protocol
3. Performance benchmarks
4. Security tests

## Timeline Estimate

### Minimal Working Server (1-2 days)
- Study working examples
- Implement basic server
- Add `eval` tool
- Test with Claude Desktop

### Full Feature Set (1 week)
- All core tools
- Resources
- Prompts
- Documentation
- Testing

### Production Ready (2 weeks)
- Error handling
- Logging
- Performance optimization
- Security hardening
- User documentation

## How to Proceed

### Immediate Next Steps

1. **Study the SDK**
   ```bash
   # Download and examine working examples
   git clone https://github.com/modelcontextprotocol/java-sdk.git
   cd java-sdk/samples
   # Find a working server example
   ```

2. **Build Minimal Server**
   - Just STDIO transport
   - Just one tool (`eval`)
   - Get it working end-to-end

3. **Test with Claude Desktop**
   - Add to config
   - Verify connection
   - Test the `eval` tool

4. **Iterate and Expand**
   - Add more tools one at a time
   - Test each addition
   - Build up to full feature set

### Questions to Answer

1. **What's the correct way to create a server?**
   - Need to see working example
   - Understand builder pattern
   - Learn capability registration

2. **How do we register tools?**
   - What's the API?
   - How to define schemas?
   - How to handle responses?

3. **How do we handle errors?**
   - Exception handling
   - Error responses
   - Logging

4. **How do we test?**
   - MCP Inspector?
   - Unit tests?
   - Integration tests?

## Resources

### Official Documentation
- [MCP Specification](https://modelcontextprotocol.io/specification)
- [MCP Java SDK](https://github.com/modelcontextprotocol/java-sdk)
- [MCP Server Examples](https://github.com/modelcontextprotocol/servers)

### Community Resources
- [MCP Discord](https://discord.gg/modelcontextprotocol)
- [GitHub Discussions](https://github.com/modelcontextprotocol/specification/discussions)
- [Example Servers](https://github.com/modelcontextprotocol/servers)

### Related Projects
- [Claude Desktop](https://claude.ai/download)
- [MCP Inspector](https://github.com/modelcontextprotocol/inspector)
- [ChatGPT Desktop](https://openai.com/chatgpt/desktop/)

## Conclusion

We've made good progress on the MCP server for Metatron:
- ✅ Dependency added
- ✅ Documentation written
- ⚠️ Implementation needs work

The main blocker is understanding the actual MCP Java SDK API. Once we have a working minimal example, we can quickly expand it to include all the features we want.

**Recommendation**: Let's study the official examples first, then build a minimal working server, then iterate from there.

---

**Next Session Goals**:
1. Find and study a working Java MCP server example
2. Create minimal Metatron MCP server with just `eval` tool
3. Test it with Claude Desktop
4. Document what we learned

