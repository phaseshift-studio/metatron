---
name: metatron-mcp
description: Reference for agents requiring tools
---

# MCP Clients and Servers

## Adding an MCP Server

To connect to an MCP server, a `mcp_client::T` must be created. `mcp_client::T` requires the MCP server support either websocket, http, or stdio transport. For most situations, the following pattern suffices -- simply convert the MCP server's published `mcpServer` JSON snippet into an `mcp_client::T` via the transformation path `str::T => json::T => mcp_client::T`.

```mtron_pre
"""/
{/
 "type": "streamable-http",/
 "url": "http://127.0.0.1:64342/stream",/
 "headers": {/
  "IJ_MCP_SERVER_PROJECT_PATH": "~/software/metatron"/
 }/
}/
""".as(json::T).as(mcp_client::T).to(/usr/marko/mcp/intellij)
```

If the snippet provided has an `mcpServer` outer wrapping, then do:

```mtron_pre
"""/
{"mcpServers": {/
 "intellij" : {/
   "type": "streamable-http",/
   "url": "http://127.0.0.1:64342/stream",/
   "headers": {/
    "IJ_MCP_SERVER_PROJECT_PATH": "~/software/metatron"/
   }/
 }}}/
""".as(json::T).as(rec::T)>>mcpServers/intellij.as(json::T).as(mcp_client::T)
```

Moreover, if the `mcpServer` snippet has multiple inner servers endpoints defined, to load all of them, do:

```mtron_pre
{"mcpServers": {/
  "intellij": {/
  }
}}
```

For `STDIO` transport MCP servers, the same process works:

```
mcp_client::[command=>[</home/killswitch/.local/bin/codegraph>,'serve', '--mcp']]@codegraph;
``` 

do

```mtron_pre
*mcp_server

```
### Using tools from the client

After connecting, `mcp_client::T` populates its `tool` field with `tool::T` entries keyed by `mTool.toolName(tid)` — the flattened instruction tid (e.g. `m_inst_eval_mtron`). Each entry carries `inst`, `name`, `desc`, and `arg`:

```mtron_pre
mcp_client::[host=>http://localhost:8777/mcp]@a
*a>>tool
[-- => [m_inst_eval_mtron=>tool::[inst=>..., name=>m_inst_eval_mtron, desc=>..., arg=>...], ...] --]

[-- invoke a tool by applying its inst field --]
a/tool/m_inst_eval_mtron/inst("1+2")
[-- => 3 --]
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

`add_message` returns a **receipt**: the message it built, plus `kind`, `location`
(the appended vid — absent when nothing was written) and `status` —
`published` (in the ledger now), `parked` (held: its tool group is incomplete) or
`unpaired` (refused: nothing can ever pair with it).

### Ledger layout

- **root** — any owned space, *absolute*, and one that serves `incrq`: ledger
  records are appended at `<root>/message/_?incrq`, so a space that receives
  ledger messages must be registered with `addQ(incrq())` in Java (a routed
  prefix such as `myspaceprefix:` is fine too — the route rewrites it to the
  absolute space). A *relative* root falls through to the ephemeral stack space
  and the write fails with `no incrq query processor attached`. DSH harness
  conversations are mirrored under `/usr/dsh` by the `dsh-plugins/metatron-mirror` plugin
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

### Tool group pairing — the ledger is never half a turn

A `tool_requests` ai message and its `tool_result`s form one **group**, and the
ledger only ever holds complete groups. Both writers — the native loop
(`SpaceChatSessionStore.updateMessages` / `ToolFeature.onToolExecuted`) and this
bus — write through the same gate (`ToolPairGate`):

- an `ai` with `tool_requests` is **held** until *every* request has a
  `tool_result`; the group then lands ai-first, its results immediately after, in
  request order. Until then the receipt says `status => parked`, so a client that
  wrote the ai side knows its results are still owed;
- a `tool_result` is held until its `ai` arrives — an out-of-order client is
  tolerated, not corrupt — and a `tool_result` with no `contents` can never pair
  and is refused (`status => unpaired`) rather than written orphaned;
- a group the client leaves unanswered is closed by the next **turn boundary**: a
  `user` message of a later (or unknown) `chat_id`, or a `compaction` sentinel.
  The ai message is then written with `name => lost_tool_result` and the call id
  as `contents` for each unanswered request, and a held result that never found
  its ai message is dropped. A `user` message of the *same* `chat_id` is not a
  boundary — its group's results may still be arriving.

So a reader never meets an assistant message with `tool_calls` whose tool messages
are missing, and never meets an orphan tool message.

### Recipe: reading a dsh harness conversation

```
m_llm_mcp_mcp_message_get_messages(root='/usr/dsh', session='/usr/dsh/session/<sessionId>', max=20)
m_llm_mcp_mcp_message_search_messages(root='/usr/dsh', pattern='probe', session='…same…', max=10)
```

Ledger order follows the conversation (the `system` prompt first; `thinking` precedes the `ai` turn it belongs to). Only message material is mirrored — dsh structural events (turns, steps, chunks) are not stored as ledger records.
