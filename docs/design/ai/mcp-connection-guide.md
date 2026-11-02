# MCP Server Connection Guide

## Quick Start

Your Metatron MCP server is **fully functional** and ready to connect! 🎉

## Starting the Server

1. **Start Metatron with MServer**:
```bash
cd /home/killswitch/software/metatron
mvn exec:java -Dexec.mainClass="studio.phaseshift.metatron.isa.mach.type.net.MServer"
```

2. **Find the WebSocket URL** in the logs:
```
[INFO] [/sys/router/server] starting mtrOn node ws://localhost:<PORT>
```

The port is dynamically assigned. Look for a line like:
```
ws://localhost:21373
```

## Connecting from Claude Desktop

Add this to your Claude Desktop MCP configuration file:

**Location**: `~/Library/Application Support/Claude/claude_desktop_config.json` (macOS)

```json
{
  "mcpServers": {
    "metatron": {
      "command": "websocat",
      "args": ["ws://localhost:PORT"],
      "env": {}
    }
  }
}
```

Replace `PORT` with the actual port from the logs.

**Note**: You'll need `websocat` installed:
```bash
# macOS
brew install websocat

# Linux
cargo install websocat
```

## Available Tools

### 1. evaluate_code
Execute Metatron code and get results.

**Example**:
```json
{
  "name": "evaluate_code",
  "arguments": {
    "code": "1.plus(2)"
  }
}
```

### 2. get_system_info
Get information about the Metatron system.

**Example**:
```json
{
  "name": "get_system_info",
  "arguments": {}
}
```

### 3. list_instructions
List available Metatron instructions.

**Example**:
```json
{
  "name": "list_instructions",
  "arguments": {
    "filter": "arithmetic"
  }
}
```

## Testing the Connection

You can test the connection manually using `websocat`:

```bash
# Connect to the server
websocat ws://localhost:PORT

# Send initialize request (paste this):
{"jsonrpc":"2.0","method":"initialize","id":1,"params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"1.0.0"}}}

# You should receive an initialize response

# Call a tool (paste this):
{"jsonrpc":"2.0","method":"tools/call","id":2,"params":{"name":"get_system_info","arguments":{}}}

# You should receive system information
```

## Example Session

```bash
$ websocat ws://localhost:21373

# Send initialize
{"jsonrpc":"2.0","method":"initialize","id":1,"params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"1.0.0"}}}

# Receive response
{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2024-11-05","capabilities":{"tools":{"listChanged":true}},"serverInfo":{"name":"metatron-mcp","version":"1.0.0"}}}

# Call evaluate_code
{"jsonrpc":"2.0","method":"tools/call","id":2,"params":{"name":"evaluate_code","arguments":{"code":"5.plus(3)"}}}

# Receive result
{"jsonrpc":"2.0","id":2,"result":{"content":[{"type":"text","text":"8"}],"isError":false}}
```

## Architecture

The server uses a **dual-mode protocol handler**:
- **Native Metatron Protocol**: Binary Obj serialization
- **MCP Protocol**: JSON-RPC 2.0 with automatic detection

Messages are automatically routed based on content:
- Contains `"jsonrpc": "2.0"` → MCP handler
- Binary data → Native handler

## Troubleshooting

### Server won't start
- Check if port is already in use
- Ensure Router is properly initialized
- Check logs for errors

### Connection refused
- Verify the port number from logs
- Ensure server is running
- Check firewall settings

### Tools not responding
- Check logs for `[JsonRpcToolDispatcher]` messages
- Verify JSON-RPC format is correct
- Ensure `tools/call` method is used

### Expected log output when tools work:
```
[INFO ] [McpWebSocketTransport] Intercepting tools/call request for custom dispatcher
[INFO ] [JsonRpcToolDispatcher] Dispatching tool call: get_system_info
[INFO ] [MetatronMcpServer] get_system_info tool handler invoked via dispatcher
```

## For AI Assistant Configuration

When configuring an AI assistant to use this MCP server, provide:

1. **Server Name**: `metatron`
2. **Protocol**: WebSocket (JSON-RPC 2.0)
3. **Endpoint**: `ws://localhost:<PORT>` (from logs)
4. **Protocol Version**: `2024-11-05`
5. **Available Tools**:
   - `evaluate_code` - Execute Metatron code
   - `get_system_info` - Get system information
   - `list_instructions` - List available instructions

## Next Steps

1. Start the Metatron server
2. Note the WebSocket URL from logs
3. Configure your AI assistant with the URL
4. Test the connection
5. Start executing Metatron code through your AI assistant!

## Technical Details

- **Implementation**: Custom JSON-RPC dispatcher using `ObjSimpleJSONSerializer`
- **SDK**: MCP Java SDK 1.1.0 (with custom workaround for tool invocation bug)
- **Transport**: WebSocket with dual-mode protocol detection
- **Tests**: 7/7 passing (3 tool tests + 4 protocol tests)

See `docs/ai/mcp-custom-dispatcher-solution.md` for complete technical documentation.
