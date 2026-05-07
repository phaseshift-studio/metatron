---
name: mcp-server-architecture
description: >
  Building MCP servers in mtron: type system, WebSocket routing, tool registration,
  mcp_wsServer / mcp_mtron_wsServer architecture, SpaceChatMemoryStore.
  TRIGGER: When building or modifying MCP servers, registering tools, setting up
  wsSpace routes for MCP, or understanding server-side MCP internals.
---

# MCP Server Architecture in mtron

## Overview

MCP servers in metatron are WebSocket-pinned servers that handle JSON-RPC 2.0 protocol. They are built as Java classes extending `mcp_wsServer` or `mcp_mtron_wsServer`, registered as Types, and wired into the wsSpace route table.

## WebSocket Server Routing

In `boot.mtron`, the `wsspace` config has a `route` field:

```mtron
wsspace::[host     => <ws://localhost:8555>,
          pattern  => ws://#,
          route    => [/mtron => mtron_ws,
                       /mcp  => mcp_mtron_ws]]
```

**Route resolution** (wsSpace.createServer()):
1. Client connects to `ws://host:port/path`
2. Route table maps `/path` to type VID (e.g., `MCP_MTRON_WS_TID`)
3. `Router.global().read(typeVID)` reads the Type object
4. A rec is constructed with `{IN, OUT}` → Type constructor fires → server instance created
5. Each connection gets its own instance

**Type registration**: Types are registered via `Type.Builder.build()` → `.create()` in Java static initializers.

## mcp_wsServer (base class)

```java
// src/main/java/.../isa/web/space/ws/server/mcp_wsServer.java
```

Handles JSON-RPC 2.0 protocol. Key struct fields:
- `tool` — rec of tool name → inst (with type signature + implementation)
- `resource` — rec of resource name → resource handler
- `prompt` — rec of prompt name → prompt handler

**Tool dispatch**: On `tools/call`, reads `this.at(TOOL).at(toolName)`, then:
```java
toolEntry.asInst().args(arguments).apply(toolLhs)
```

**Type definition**:
```java
.tid(WS_SERVER_TID)          // type-of = WebSocket server
.vid(MCP_WS_TID)             // stored at /m/web/space/ws/mcp_ws
.isaPredicate(rec(           // shape:
    uri(TOOL).maybe(), ...   //   tool field optional
    uri(RESOURCE).maybe(),   //   resource field optional
    uri(PROMPT).maybe()))    //   prompt field optional
```

## mcp_mtron_wsServer (extends mcp_wsServer)

Adds metatron-native tools (eval, mtron_list_space, mtron_router_info, mtron_list_inst).

```java
// src/main/java/.../isa/web/space/ws/server/mcp_mtron_wsServer.java
```

**Key behavior**: `buildJvm()` checks if TOOL already exists in jvm — if so, preserves user tools instead of overriding:
```java
if (!jvm.containsKey(uri(TOOL))) {
    // add default tools
    jvm.put(uri(TOOL), tools);
}
```

## Creating a Custom MCP Server (Java subclass pattern)

Custom MCP servers follow this pattern:

1. **Extend mcp_wsServer** (or mcp_mtron_wsServer)
2. **Define a Type** with `Type.Builder.build()`, using `WS_SERVER_TID` as tid and a unique vid
3. **Implement buildJvm()** to register tools, resources, prompts in the jvm Map
4. **Register in wsSpace route table**: `route => [/my-path => /custom/type/vid]`

Tool implementations are `Inst` objects with type signatures (dom/rng) and Java lambdas.

## SpaceChatMemoryStore

```java
// src/main/java/.../isa/llm/space/SpaceChatMemoryStore.java
```

Persists chat messages to mtron space:
- `getMessages(memoryId)` — reads from space URI, deserializes JSON → ChatMessage
- `updateMessages(memoryId, messages)` — serializes ChatMessage → JSON, writes as Lst to space URI
- `deleteMessages(memoryId)` — writes `noobj()` to clear

Used by `mModel.agent()` to give agents persistent memory across `.chat()` calls via `MessageWindowChatMemory`.

## Agent Memory Flow

```
agent config: memory => memory::[max=>20, mem=>!*</usr/ai/memory>]
                                    │
                                    ▼
mModel.agent(): memoryVID = this.memory().at("mem").vid()
                → MessageWindowChatMemory.builder()
                    .id(memoryVID)
                    .chatMemoryStore(SpaceChatMemoryStore.single())
                    .maxMessages(max)
                    .build()
                                    │
              .chat() ──► getMessages(memoryVID) ──► conversation context loaded
                                    │
              response ◄── updateMessages(memoryVID, allMessages) ──► persisted to space
```

The `!*` (auto_from) dereferences lazily at `.chat()` call time, reading the latest state from the space URI.
