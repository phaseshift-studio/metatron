---
name: metatron-mcp
description: Reference for agents requiring tools
---

# MCP Clients and Servers

## Adding an MCP Server

To connect to an MCP server, a `mcp_client::T` must be created. `mcp_client::T` requires the MCP server support either websocket, http, or stdio transport. For most situations, the following pattern suffices -- simply convert the MCP server's published `mcpServer` JSON snippet into an `mcp_client::T` via the transformation path `str::T => json::T => mcp_client::T`.

```mtron
"""
{
 "type": "streamable-http",
 "url": "http://127.0.0.1:64342/stream",
 "headers": {
  "IJ_MCP_SERVER_PROJECT_PATH": "~/software/metatron"
 }
}
""".as(json::T).as(mcp_client::T).to(/usr/marko/mcp/intellij)
```

If the snippet provided has an `mcpServer` outer wrapping, then do:

```mtron
"""
{"mcpServers": {
 "intellij" : {
   "type": "streamable-http",
   "url": "http://127.0.0.1:64342/stream",
   "headers": {
    "IJ_MCP_SERVER_PROJECT_PATH": "~/software/metatron"
   }
 }}}
""".as(json::T).as(rec::T)>>mcpServers/intellij.as(json::T).as(mcp_client::T)
```

Moreover, if the `mcpServer` snippet has multiple inner servers endpoints defined, to load all of them, do:

```mtron
{"mcpServers": {
  "intellij": {
  }
}}
```

For `STDIO` transport MCP servers, the same process works:

```
mcp_client::[command=>[</home/killswitch/.local/bin/codegraph>,'serve', '--mcp']]@codegraph;
``` 

do

```mtron
*mcp_server

```
### Using tools from the client

After connecting, `mcp_client::T` populates its `tool` field with `tool::T` entries keyed by `mTool.toolName(tid)` — the flattened instruction tid (e.g. `m_inst_eval_mtron`). Each entry carries `inst`, `name`, `desc`, and `arg`:

```mtron
mcp_client::[host=>http://localhost:8777/mcp]@a
*a>>tool
# => [m_inst_eval_mtron=>tool::[inst=>..., name=>m_inst_eval_mtron, desc=>..., arg=>...], ...]

# invoke a tool by applying its inst field
a/tool/m_inst_eval_mtron/inst("1+2")
# => 3
```

### WebSocket

```
mcp_client::[host => <http://127.0.0.1:29170/index-mcp/streamable-http>]@/usr/ai/mcp/index-mcp;
```


### HTTP

#### HTTP Stream

Given the `mcpServers` JSON snippet below:

```json
{
 "type": "streamable-http",
 "url": "http://127.0.0.1:64342/stream",
 "headers": {
  "IJ_MCP_SERVER_PROJECT_PATH": "/home/killswitch/software/metatron"
 }
}
```

```
mcp_client::[host => <http://127.0.0.1:64342/stream>]@/usr/ai/mcp/intellij; 
```
