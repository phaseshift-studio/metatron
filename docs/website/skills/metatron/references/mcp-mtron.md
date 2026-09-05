---
name: metatron-mcp
description: Reference for agents requiring tools
---

# MCP Clients and Servers

## Adding an MCP Server

To connect to an MCP server, a `mcp_client::T` must be created. `mcp_client::T` requires the MCP server support either websocket, http, or stdio transport. For most situations, the following pattern suffices -- simply convert the MCP server's published `mcpServer` JSON snippet into an `mcp_client::T` via the transformation path `str::T => json::T => mcp_client::T`.

```mtron
mtron> """
       {
        "type": "streamable-http",
        "url": "http://127.0.0.1:64342/stream",
        "headers": {
         "IJ_MCP_SERVER_PROJECT_PATH": "~/software/metatron"
        }
       }
       """.as(json::T).as(mcp_client::T).to(/usr/marko/mcp/intellij)
==>fail::[apply failure:
   	[lhs]    │ json::"""
   {
   "type": "streamable-http",
   "url": "http://127.0.0.1:64342/stream",
   "headers": {
   "IJ_MCP_SERVER_PROJECT_PATH": "~/software/metatron"
   }
   }
   """
   	 \_type  │ /m/web/mime/json
   	  \_pred │ [/m/inst/pred?rng=#{?}&dom=#{?}(#{*}::T){<j>}]
   	[inst]   │ as?rng=mcp_client{+}&dom=json(mcp_client::T){<j>}@<2>
   	 \_dom   │ str::T[/m/inst/pred?rng=#{?}&dom=#{?}(#{*}::T){<j>}]@/m/web/mime/json
   	 \_args  │ [mcp_client::T][SocketChannelImpl<204>:java.util.concurrent.ExecutionException: java.net.ConnectException[SocketChannelImpl<204>:java.util.concurrent.ExecutionException: java.net.ConnectException ← java.net.ConnectException ← (ConnectException) ← (ClosedChannelException)] ← java.util.concurrent.ExecutionException: java.net.ConnectException ← java.net.ConnectException ← (ConnectException) ← ...]][java.util.concurrent.ExecutionException: java.net.ConnectException[SocketChannelImpl<204>:java.util.concurrent.ExecutionException: java.net.ConnectException ← java.net.ConnectException ← (ConnectException) ← (ClosedChannelException)]][java.util.concurrent.ExecutionException: java.net.ConnectException][java.net.ConnectException][][]@/sys/fail/160
```
If the snippet provided has an `mcpServer` outer wrapping, then do:

```mtron
mtron> """
       {"mcpServers": {
        "intellij" : {
          "type": "streamable-http",
          "url": "http://127.0.0.1:64342/stream",
          "headers": {
           "IJ_MCP_SERVER_PROJECT_PATH": "~/software/metatron"
          }
        }}}
       """.as(json::T).as(rec::T)>>mcpServers/intellij.as(json::T).as(mcp_client::T)
==>fail::[apply failure:
   	[lhs]    │ json::'{"type":"streamable-http","url":"http://127.0.0.1:64342/stream","headers":{"IJ_MCP_SERVER_PROJECT_PATH":"~/software/metatron"}}'
   	 \_type  │ /m/web/mime/json
   	  \_pred │ [/m/inst/pred?rng=#{?}&dom=#{?}(#{*}::T){<j>}]
   	[inst]   │ as?rng=mcp_client{+}&dom=json(mcp_client::T){<j>}@<5>
   	 \_dom   │ str::T[/m/inst/pred?rng=#{?}&dom=#{?}(#{*}::T){<j>}]@/m/web/mime/json
   	 \_args  │ [mcp_client::T][SocketChannelImpl<204>:java.util.concurrent.ExecutionException: java.net.ConnectException[SocketChannelImpl<204>:java.util.concurrent.ExecutionException: java.net.ConnectException ← java.net.ConnectException ← (ConnectException) ← (ClosedChannelException)] ← java.util.concurrent.ExecutionException: java.net.ConnectException ← java.net.ConnectException ← (ConnectException) ← ...]][java.util.concurrent.ExecutionException: java.net.ConnectException[SocketChannelImpl<204>:java.util.concurrent.ExecutionException: java.net.ConnectException ← java.net.ConnectException ← (ConnectException) ← (ClosedChannelException)]][java.util.concurrent.ExecutionException: java.net.ConnectException][java.net.ConnectException][][]@/sys/fail/170
```
Moreover, if the `mcpServer` snippet has multiple inner servers endpoints defined, to load all of them, do:

```mtron
mtron> {"mcpServers": {
         "intellij": {
         }
==>fail::[parse error at line 1, col 1:
     {"mcpServers": {
            "intellij": {
   ...
     ^
     unclosed '{' — missing '}'?]@/sys/fail/172
mtron> }}
==>fail::[parse error at line 1, col 1:
     }}
     ^
     unexpected '}' — missing opening '{' or extra '}'?]@/sys/fail/174
```
For `STDIO` transport MCP servers, the same process works:

```
mcp_client::[command=>[</home/killswitch/.local/bin/codegraph>,'serve', '--mcp']]@codegraph;
``` 

do

```mtron
mtron> *mcp_server
```### Using tools from the client

After connecting, `mcp_client::T` populates its `tool` field with `tool::T` entries keyed by `mTool.toolName(tid)` — the flattened instruction tid (e.g. `m_inst_eval_mtron`). Each entry carries `inst`, `name`, `desc`, and `arg`:

```mtron
mtron> mcp_client::[host=>http://localhost:8777/mcp]@a
==>fail::[unable to construct mcp_client::T: fail::[apply failure:
   	[lhs]    │ [host=>http://localhost:8777/mcp]@a
   	 \_type  │ /m/rec
   	  \_pred │ []
   	[inst]   │ ctor?rng=mcp_client&dom=#{?}([host=>http://localhost:8777/mcp]@a){<j>}
   	 \_dom   │ #{?}::T
   	 \_args  │ [[host=>http://localhost:8777/mcp]@a][SocketChannelImpl<204>:java.util.concurrent.ExecutionException: java.net.ConnectException[SocketChannelImpl<204>:java.util.concurrent.ExecutionException: java.net.ConnectException ← java.net.ConnectException ← (ConnectException) ← (ClosedChannelException)] ← java.util.concurrent.ExecutionException: java.net.ConnectException ← java.net.ConnectException ← (ConnectException) ← ...]][java.util.concurrent.ExecutionException: java.net.ConnectException[SocketChannelImpl<204>:java.util.concurrent.ExecutionException: java.net.ConnectException ← java.net.ConnectException ← (ConnectException) ← (ClosedChannelException)]][java.util.concurrent.ExecutionException: java.net.ConnectException][java.net.ConnectException][][]@/sys/fail/178]@/sys/fail/180
mtron> *a>>tool
mtron> [-- => [m_inst_eval_mtron=>tool::[inst=>..., name=>m_inst_eval_mtron, desc=>..., arg=>...], ...] --]
mtron> [-- invoke a tool by applying its inst field --]
mtron> a/tool/m_inst_eval_mtron/inst("1+2")
==>fail::[unable to locate inst-f of a/tool/m_inst_eval_mtron/inst('1+2')]@/sys/fail/182
mtron> [-- => 3 --]
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

## metatron's own MCP: the message ledger (`mcp_message`)

Unlike an external server, metatron's message ledger (`mcpMessageServer`) needs no `mcp_client` — it rides the runtime itself (on the live host, `ws://localhost:8555/message`) and, for DSH-backed agents, it is **already visible**: the dsh web profile registers it through `dsh-mcp-client`, so the tools surface as-is as `mcp__metatron_message__…`.

### Tools (flattened tids)

| tool | arguments | purpose |
| --- | --- | --- |
| `m_llm_mcp_mcp_message_add_message` | `root`, `kind`, `text`, `session?`, `name?`, `contents?`, `chat_id?`, `time?`, `tool_requests?`, `attributes?` | append one message to a ledger |
| `m_llm_mcp_mcp_message_get_messages` | `root`, `session`, `max?` | read the tail of a ledger |
| `m_llm_mcp_mcp_message_search_messages` | `root`, `pattern`, `session`, `max?` | search ledger text |

### Ledger layout

- **root** — any owned space; DSH harness conversations are mirrored under `/usr/dsh` by the `dsh-plugins/metatron-mirror` plugin
- **session** — envelope uri `<root>/session/<sessionId>` (one uri-safe segment); records are scoped by it
- **records** — appended at `<root>/message/_?incrq`; the `vid` carries the increment; the ledger is append-only
- **kinds** — `system` | `user` | `ai` | `thinking` | `tool_result` | `compaction`

### Record shape (what `get_messages` returns)

```
ai::[
  kind          => ai
  text          => 'i will now call the probe tool'
  tool_requests => [tool_request::[
    name       => probe_tool,                <- uri, the tool
    args       => '{"0":"hello"}',           <- str, the call arguments (json)
    contents   => call_42,                   <- str, the tool call id — the join key
    text       => probe_tool({"0":"hello"})  <- str, formatted summary
  ]]
  session       => /usr/dsh/session/ws-probe
  depth         => 1
  chat_id       => 4
]
```

- `user` — `name` (when present) is the sender identity; harness-injected context arrives as `plugin:<name>`
- `tool_result` — `name` is the tool (uri), `contents` the tool call id, `text` the tool output
- `compaction` — the compaction **sentinel** (the `message/compaction` record): `text` is the resume summary; optional `in`/`out`/`compression` statistics ride the `attributes` argument. It bounds the live window — a reader takes the suffix after the newest sentinel (`SpaceChatSessionStore` `stopAt`) — and native `compactSession` writes exactly this shape (summary sentinel + pair-safe tail)
- **join rule** — pair a `tool_result` to its request by `ai.tool_requests[i].contents == tool_result.contents`; the request's `name`/`args` complete the picture

### Recipe: reading a dsh harness conversation

```
m_llm_mcp_mcp_message_get_messages(root='/usr/dsh', session='/usr/dsh/session/<sessionId>', max=20)
m_llm_mcp_mcp_message_search_messages(root='/usr/dsh', pattern='probe', session='…same…', max=10)
```

Ledger order follows the conversation (the `system` prompt first; `thinking` precedes the `ai` turn it belongs to). Only message material is mirrored — dsh structural events (turns, steps, chunks) are not stored as ledger records.