# Metatron MCP Server

The Metatron MCP (Model Context Protocol) Server enables AI assistants like Claude, ChatGPT, and others to interact with Metatron's console and capabilities in real-time.

## What is MCP?

MCP (Model Context Protocol) is an open standard that allows AI applications to connect to external tools and data sources. Think of it as a "USB-C port for AI" - it provides a standardized way for AI assistants to access your Metatron environment.

## Features

The Metatron MCP Server provides:

### Tools
- **`eval`** - Evaluate Metatron code and get results
- **`system_info`** - Get system information about the Metatron environment
- **`list_instructions`** - List all available Metatron instructions
- **`instruction_help`** - Get detailed help for specific instructions

### Prompts
- **`explore_metatron`** - Interactive guide to exploring Metatron
- **`debug_code`** - Help debug Metatron code

### Resources
- **`metatron://docs/readme`** - Main README documentation
- **`metatron://docs/ring-theory`** - Computational Ring Theory paper

## Installation

### Prerequisites
- Java 22 or higher
- Metatron built and ready (`mvn clean install`)
- An MCP-compatible AI client (Claude Desktop, ChatGPT Desktop, etc.)

### Building

The MCP server is built automatically when you build Metatron:

```bash
mvn clean install
```

## Usage

### Running Standalone

You can run the MCP server directly:

```bash
java -cp target/metatron-0.1-SNAPSHOT.jar studio.phaseshift.metatron.mcp.MetatronMcpServer
```

The server will start and communicate via STDIO (standard input/output), waiting for MCP client connections.

### Connecting to Claude Desktop

1. **Locate your Claude Desktop config file:**
   - **macOS**: `~/Library/Application Support/Claude/claude_desktop_config.json`
   - **Linux**: `~/.config/Claude/claude_desktop_config.json`
   - **Windows**: `%APPDATA%\Claude\claude_desktop_config.json`

2. **Add Metatron to your config:**

```json
{
  "mcpServers": {
    "metatron": {
      "command": "java",
      "args": [
        "-cp",
        "/ABSOLUTE/PATH/TO/metatron/target/metatron-0.1-SNAPSHOT.jar",
        "studio.phaseshift.metatron.mcp.MetatronMcpServer"
      ]
    }
  }
}
```

**Important**: Replace `/ABSOLUTE/PATH/TO/metatron` with the actual absolute path to your Metatron directory.

3. **Restart Claude Desktop**

4. **Verify the connection:**
   - Look for the 🔌 icon in Claude Desktop
   - Click it to see available tools
   - You should see "metatron" listed with its tools

### Connecting to Other MCP Clients

The Metatron MCP Server works with any MCP-compatible client:

- **ChatGPT Desktop** - Similar configuration to Claude
- **VS Code with Copilot** - Configure in VS Code settings
- **Cursor** - Add to Cursor's MCP configuration
- **Custom clients** - Use the Java MCP SDK to build your own

## Example Usage

Once connected, you can interact with Metatron through your AI assistant:

### Evaluating Code

**You**: "Can you evaluate this Metatron code: `[1,2,3].plus([4,5,6])`"

**Claude**: *Uses the `eval` tool*
```
Result: [1,2,3,4,5,6]
```

### Getting System Info

**You**: "What's the status of the Metatron system?"

**Claude**: *Uses the `system_info` tool*
```
Metatron System Information
===========================
Version: 0.1-SNAPSHOT
Java Version: 22.0.1
OS: Linux
Architecture: amd64
Available Processors: 16
Max Memory: 4096 MB
```

### Exploring Instructions

**You**: "What instructions are available for working with relations?"

**Claude**: *Uses the `list_instructions` tool with filter "rel"*
```
Available Metatron Instructions:
================================
- /m/inst/rel
- /m/inst/rel/dom
- /m/inst/rel/rng
- /m/inst/rel/mult
- /m/inst/rel/neg
...
```

### Using Prompts

**You**: "I want to learn about Metatron"

**Claude**: *Uses the `explore_metatron` prompt*
"Great! Let me help you explore Metatron's capabilities. Metatron is a distributed computing language with powerful algebraic structures..."

## Architecture

The Metatron MCP Server is built using the official Java MCP SDK (no Spring dependencies):

```
MetatronMcpServer
├── Tools (executable functions)
│   ├── eval - Execute Metatron code
│   ├── system_info - Get system status
│   ├── list_instructions - Browse available instructions
│   └── instruction_help - Get instruction documentation
├── Prompts (guided workflows)
│   ├── explore_metatron - Learning guide
│   └── debug_code - Debugging assistant
└── Resources (documentation access)
    ├── metatron://docs/readme
    └── metatron://docs/ring-theory
```

### Communication Flow

```
AI Assistant (Claude/ChatGPT)
    ↕ (JSON-RPC over STDIO)
MCP Client (in AI app)
    ↕ (MCP Protocol)
Metatron MCP Server
    ↕ (Direct Java calls)
Metatron Parser & Runtime
```

## Security Considerations

### User Consent
- AI assistants will ask for permission before executing tools
- You can review what code will be executed before approving
- Tool execution is logged for audit purposes

### Sandboxing
- The MCP server runs in the same JVM as Metatron
- Code executed via `eval` has full access to the Metatron environment
- **Important**: Only connect trusted AI clients to your MCP server

### Best Practices
1. **Review code before execution** - Always check what the AI wants to run
2. **Use read-only tools when possible** - Prefer `system_info` and `list_instructions` over `eval`
3. **Monitor logs** - Check MCP server logs for unexpected activity
4. **Limit network exposure** - STDIO transport keeps communication local

## Troubleshooting

### Server won't start

**Problem**: `ClassNotFoundException` or similar errors

**Solution**: Make sure you've built Metatron with `mvn clean install` and the JAR path in your config is correct.

### Claude Desktop doesn't show the server

**Problem**: No 🔌 icon or "metatron" not listed

**Solution**:
1. Check that `claude_desktop_config.json` is valid JSON
2. Verify the absolute path to the JAR file
3. Restart Claude Desktop completely
4. Check Claude's logs: `~/Library/Logs/Claude/` (macOS) or `~/.config/Claude/logs/` (Linux)

### Tools fail to execute

**Problem**: "Error executing tool" messages

**Solution**:
1. Check the MCP server logs for detailed error messages
2. Verify that Metatron's parser is initialized correctly
3. Try running the code directly in Metatron REPL first

### Performance issues

**Problem**: Slow responses or timeouts

**Solution**:
1. Increase JVM memory: Add `-Xmx4g` to the `args` in your config
2. Check if Metatron code is computationally expensive
3. Consider adding caching for frequently accessed resources

## Development

### Adding New Tools

To add a new tool to the MCP server:

1. **Add the tool definition** in `handleListTools()`:
```java
Tool.builder()
    .name("my_new_tool")
    .description("Description of what it does")
    .inputSchema(Map.of(
        "type", "object",
        "properties", Map.of(
            "param1", Map.of(
                "type", "string",
                "description", "Parameter description"
            )
        ),
        "required", List.of("param1")
    ))
    .build()
```

2. **Add the handler** in `handleCallTool()`:
```java
case "my_new_tool" -> handleMyNewTool(args);
```

3. **Implement the handler method**:
```java
private CompletableFuture<CallToolResult> handleMyNewTool(Map<String, Object> args) {
    String param1 = (String) args.get("param1");
    // Your implementation here
    return CompletableFuture.completedFuture(CallToolResult.builder()
        .content(List.of(TextContent.builder()
            .text("Result: " + result)
            .build()))
        .build());
}
```

### Adding New Resources

Resources provide read-only access to documentation and data:

1. **Add resource definition** in `handleListResources()`:
```java
Resource.builder()
    .uri("metatron://docs/my-doc")
    .name("My Documentation")
    .description("Description")
    .mimeType("text/markdown")
    .build()
```

2. **Implement reading** in `handleReadResource()`:
```java
if (uri.equals("metatron://docs/my-doc")) {
    String content = loadDocumentation();
    // Return content
}
```

### Testing

Test your MCP server using the MCP Inspector:

```bash
npx @modelcontextprotocol/inspector java -cp target/metatron-0.1-SNAPSHOT.jar studio.phaseshift.metatron.mcp.MetatronMcpServer
```

This opens a web UI where you can test tools, prompts, and resources interactively.

## Future Enhancements

Planned features for the Metatron MCP Server:

- [ ] **Streaming results** - For long-running computations
- [ ] **Session management** - Maintain REPL state across tool calls
- [ ] **Graph visualization** - Export graph structures for AI analysis
- [ ] **Code completion** - Provide context-aware code suggestions
- [ ] **Debugging tools** - Step through code execution
- [ ] **Remote transport** - HTTP/SSE for network access
- [ ] **Authentication** - OAuth/API key support for remote access
- [ ] **Resource templates** - Dynamic documentation access
- [ ] **Sampling support** - Allow server to request LLM completions

## References

- [Model Context Protocol Specification](https://modelcontextprotocol.io/specification)
- [MCP Java SDK Documentation](https://modelcontextprotocol.io/sdk/java)
- [MCP Server Examples](https://github.com/modelcontextprotocol/servers)
- [Metatron Documentation](http://metatron.phaseshift.studio)

## License

The Metatron MCP Server is part of Metatron and is licensed under the GNU Affero General Public License v3.0.

## Support

For issues or questions:
- GitHub Issues: [metatron/issues](https://github.com/phaseshift-studio/metatron/issues)
- Email: marko@markorodriguez.com
- Website: http://metatron.phaseshift.studio

---

**Built with ❤️ by PhaseShift Studio**
