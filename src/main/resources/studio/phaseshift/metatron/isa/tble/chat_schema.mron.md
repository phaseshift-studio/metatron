# Chat/Memory Schema — mtron type definitions

*Inline model: typed message Recs stored directly in the `mem` Lst (`{mem: [system::[...], user::[...], ...], max: N}`).
TID discrimination (system::T, user::T, ai::T, tool_result::T) provides type-aware readback.
Single read, single write per conversation — compatible with every space backend.*

---

```mtron
*acme:+

-- memory: conversation header with inline typed message Recs
rrow::T[?[
  id=>int::T,
  agent_id=>str::T,
  name=>str::T,
  mem=>lst::T,      -- [system::[...], user::[...], ai::[...], tool_result::[...]]
  max=>int::T,
  created_at=>str::T,
  updated_at=>str::T]]@/sys/space/acme/schema/instset/memory

-- system::T — type discriminator via TID = /m/llm/system
rrow::T[?[
  id=>int::T,
  text=>str::T]]@/sys/space/acme/schema/instset/system_messages

-- user::T — type discriminator via TID = /m/llm/user
-- Single-text: contents => [text => "hello"]
-- Multi-modal: contents => [[text=>"..."], [image=>[mime_type=>"image/png", url=>"..."]]]
rrow::T[?[
  id=>int::T,
  name=>str::T,
  contents=>lst::T]]@/sys/space/acme/schema/instset/user_messages

-- ai::T — type discriminator via TID = /m/llm/ai
rrow::T[?[
  id=>int::T,
  name=>str::T,
  text=>str::T,
  thinking=>int::T,
  tool_requests=>lst::T,
  attrs=>rec::T]]@/sys/space/acme/schema/instset/ai_messages

-- tool_result::T — type discriminator via TID = /m/llm/tool_result
-- boundary: NAME token → LC4j ToolExecutionResultMessage.toolName()
rrow::T[?[
  id=>int::T,
  name=>str::T,      -- tool name (reuses NAME token)
  text=>str::T]]@/sys/space/acme/schema/instset/tool_result_messages

rrow::T[?[
  id=>int::T,
  name=>str::T,
  label=>str::T,
  info=>rec::T,
  features_json=>lst::T,
  metadata_json=>rec::T]]@/sys/space/acme/schema/instset/models

rrow::T[?[
  id=>int::T,
  name=>str::T,
  description=>str::T,
  instructions=>rec::T,
  metadata=>rec::T]]@/sys/space/acme/schema/instset/skills

rrow::T[?[
  id=>int::T,
  name=>str::T,
  description=>str::T,
  args_schema=>lst::T,
  memory_id=>isa(memory/+/id).!*id()]]@/sys/space/acme/schema/instset/tools
```

---

## Mapping notes

| rrow | SQL table | TID (type discriminator) | Fields |
|------|-----------|--------------------------|--------|
| system_messages | `llm_message_system` | `system::T` (`/m/llm/system`) | text (Str) |
| user_messages | `llm_message_user` | `user::T` (`/m/llm/user`) | name (Str?), contents (Rec \| Lst) |
| ai_messages | `llm_message_ai` | `ai::T` (`/m/llm/ai`) | name (Str?), text (Str?), thinking (Int), tool_requests (Lst), attrs (Rec) |
| tool_result_messages | `llm_message_tool_result` | `tool_result::T` (`/m/llm/tool_result`) | name (Str), text (Str) |
| memory | `llm_memory` | `memory::T` (`/m/llm/memory`) | agent_id (Str), name (Str), mem (Lst<Uri>), max (Int) |
| models | `llm_model` | `model::T` (`/m/llm/model`) | name, label, info (Rec), features_json (Lst), metadata_json (Rec) |
| skills | `llm_skill` | `skill::T` (`/m/llm/skill`) | name, description, instructions (Rec), metadata (Rec) |
| tools | `llm_tool` | `tool::T` (`/m/llm/tool`) | name, description, args_schema (Lst), memory_id (FK) |

### Inline memory model

Messages are typed Recs stored directly in the `mem` Lst — a single read/write per conversation:

```mtron
memory::[
  mem  => [system::[text=>"You are helpful."], user::[name=>"marko", contents=>[text=>"hello"]], ai::[text=>"Hi!"]],
  max  => 20
]
```

- Each element carries its own TID (`system::T`, `user::T`, `ai::T`, `tool_result::T`)
- Ordering is Lst index — no per-message `position` field
- Single atomic write per `updateMessages()` — compatible with all space backends
- Message-type tables (`llm_message_system`, etc.) exist for ad-hoc SQL querying


### Token vocabulary & API boundaries

A small, reusable token vocabulary is shared across message types:

| Token | Used in | Boundary (LC4j) |
|-------|---------|------------------|
| `name` | user, ai, tool_result, tool_request | `UserMessage.name()`, `ToolExecutionResultMessage.toolName()`, `ToolExecutionRequest.name()` |
| `text` | system, user (contents), ai, tool_result | `SystemMessage.text()`, `AiMessage.text()`, `ToolExecutionResultMessage.text()` |
| `contents` | user | `UserMessage.contents()` — single Rec for text, Lst for multi-modal |
| `tool_requests` | ai | `AiMessage.toolExecutionRequests()` |
| `id` | tool_result, tool_request | `ToolExecutionResultMessage.id()`, `ToolExecutionRequest.id()` |
| `args` | tool_request | `ToolExecutionRequest.arguments()` |
| `mimeType` | content parts | `Image.mimeType()`, `Audio.mimeType()`, etc. |
| `url` | content parts | `Image.url()`, `Audio.url()`, etc. |
| `data` | content parts | `Image.base64Data()` → `bytes::T` |
| `max` | memory | `MessageWindowChatMemory.maxMessages` |

### JSON/dcmnt compatibility

For MongoDB or dcmntSpace backends, each type maps directly to a document — no transformation needed since the field types (Str→String, Int→Number, Lst/Rec→array/object, Bytes→Binary) are identity across JSON/BSON.
