---
name: metatron
description: common patterns in metatron
allowed-tools: Read, Grep
---

= Effective Metatron Patterns 

This skill is a practical guide to handling common use cases patterns. Each secondary header (`==`)  is a particular pattern and each is independent of the other.

IMPORTANT: If the user wants the agent to execute the associated mtron expressions, then the agent must be provided access to an mtron virtual machine. The `tool_feature::T` of the `/m/llm` instset enables agents to use tools -- mtron instructions and MCP servers. A general purpose instruction is `eval?#{*}<=#{?}(#::T)`. The following agent definition snippet is sufficient for the spawned agent to execute mtron code.

```mtron
... [-- larger agent definition --]
feature=>[
 tool_feature::[tool=>[!*eval]]
 ... [-- other features attached to agent --]
]
```

== Adding an MCP Server

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
""".as(json::T).>>mcpServers/intellij.as(mcp_client::T).to(/usr/marko/mcp/intellij)
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



=== WebSocket

```
mcp_ws::[host => <http://127.0.0.1:29170/index-mcp/streamable-http>]@/usr/ai/mcp/index-mcp;
```


=== HTTP 

==== HTTP Stream

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

The correspo

```
mcp_http::[host => <http://127.0.0.1:64342/stream>]@/usr/ai/mcp/intellij; 
```

=== STDIO



== References

*   **UI Architecture (Java)**: `references/metatron-ui-architecture.md` — Widget lifecycle, JRec state bridge, Style system, FloatingSurface, Anchor positioning, uiInstSet type registration.  How to create, modify, and float widgets.