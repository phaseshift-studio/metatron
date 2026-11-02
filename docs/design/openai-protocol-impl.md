# OpenAI protocol — exposing metatron as a drop-in model

**2026-09-15** · design note — the transport primitives it builds on are landed (`sse::T`), the endpoint itself is
not · companions: `docs/design/webspace.md`, `docs/skills/mtron/references/mcp-server-architecture.md`,
`docs/skills/mtron/references/web-instset-mtron.md`

## 0. The ask

Point an existing agent harness — OpenWebUI, LiteLLM, a LangChain/OpenAI SDK — at a running metatron and have it
treat metatron as a model. `POST /v1/chat/completions`, `GET /v1/models`, streamed responses, tool calls. No harness
changes.

## 1. What already exists

metatron is already the "model" — the missing piece is the *inbound* OpenAI wire format. `LLMFactory`
(`isa/llm/LLMFactory.java`) speaks OpenAI today only as a *client* (`protocol=>openai|ollama|anthropic|localai`).
The server side has all the raw material:

* **the model** — `Agent.chat(String, Rec) → ChatResult` (`isa/llm/type/Agent.java`), LangChain4j streaming
  underneath, `interrupt()` for cancellation, and `SpaceChatSessionStore` (`isa/llm/space/SpaceChatSessionStore.java`)
  as the durable chat ledger.
* **the transport** — `httpSpace` and its route ladder (`httpSpace.classify()`), the `HttpRec` base handler, and the
  `sse::T` primitive (`SseStream` + `HttpRec.openSse()`) for a chunked `text/event-stream` response.
* **the template** — `mcp_httpHandler` (`isa/web/space/http/handler/mcp_httpHandler.java`) is the exact shape: a
  transport-agnostic `mcpServer` composed by an `HttpRec` subclass, registered as a Type in `webInstSet`, mounted as
  `route => [/mcp => mcp_mtron]` in `boot/space/web/http.mtron`.
* **the streaming precedent** — the MCP subscription outbox, drained over SSE by the GET, is "a server pushes events
  to a client over one held-open stream" already working.

## 2. The shape — mirror MCP

Three pieces, exactly analogous to `mcpServer` / `mcp_httpHandler`:

1. **`openai::T` surface** — a protocol surface in `webInstSet` (like `mcp::T`), refining `protocol::T`.
2. **`openaiServer`** — transport-agnostic translation. OpenAI request → `Agent.chat`; `ChatResult` → OpenAI
   response. No HTTP knowledge; testable by direct dispatch, like `mcpServer.handleMessage`.
3. **`openai_httpHandler extends HttpRec`** — the transport. `doPost` = `/v1/chat/completions`, `doGet` =
   `/v1/models`. Registered as an `openai_http` Type with a constructor, like `HTTP_MCP_HANDLER_TYPE`.

Mount:

```mtron
route => [/v1/chat/completions => openai_http,
          /v1/models           => openai_http]
```

The ladder (`httpSpace.classify()`) already dispatches on `instanceof mcpServer` and on a `Type` target; add an
`instanceof openaiServer` branch (or materialize an `openai_server::T` type target) the same way it wraps `mcpServer`.

## 3. Request/response mapping

### `POST /v1/chat/completions` → `Agent.chat`

| OpenAI | metatron |
|---|---|
| `messages[]` (system/user/assistant/tool) | the chat ledger (`SpaceChatSessionStore`) or `Agent` memory |
| `messages[last].content` | the `Agent.chat(message)` arg |
| `model` | which `Agent`/model config (a mount, or a model registry) |
| `temperature`, `max_tokens`, `top_p` | model/chat config knobs — map what `Agent` already honors |
| `tools[]` | metatron `mTool`s — `mTool.mtronInstToolSpecification` already emits a JSON `ToolSpecification` |
| `response_format` | `Agent.chat(msg, responseFormat)` — structured output already exists |
| `stream` | choose the SSE path vs a one-shot body |

### non-stream response (`stream: false`)

```json
{"id":"chatcmpl-…","object":"chat.completion","created":…,"model":"metatron",
 "choices":[{"index":0,"message":{"role":"assistant","content":"…"},"finish_reason":"stop"}],
 "usage":{"prompt_tokens":…,"completion_tokens":…,"total_tokens":…}}
```

This is basically free: `Agent.chat(msg)` → `ChatResult` → `HttpRec.sendJsonString(200, …)`. Land it first.

### `GET /v1/models`

```json
{"object":"list","data":[{"id":"metatron","object":"model","created":…,"owned_by":"metatron"}]}
```

The id is the metatron model/agent name the harness echoes back in `model`.

### errors

OpenAI errors are `{"error":{"message":…,"type":…,"code":…}}` with the right status — 400 `invalid_request_error`,
404 `model_not_found`, 500 `server_error`. `mcp_httpHandler`'s error path is the template; add an
`sendOpenAiError` helper.

## 4. The hard part — streaming

`Agent.chat` does **not** return a stream: it subscribes
`.onPartialResponse(s -> dispatchHook(f, ON_PARTIAL_RESPONSE, str(s)))` internally, then `.start()` +
`latch.await()` and returns one final `ChatResult`. The per-token callbacks are routed to **features**, not to a
transport. Two problems, two fixes:

1. **expose the tokens.** Either
   * a **streaming feature** — a `Feature` whose `onPartialResponse(agent, Str)` writes the SSE chunk. No `Agent`
     change; most mtron-native.
   * a **`chatStream(String, Consumer<String> sink)`** (or return the `TokenStream`) on `Agent` — cleaner for a
     transport, but a small API addition.
2. **threading.** LangChain4j callbacks fire on background threads; `HttpRec`'s `HttpExchange` is thread-local to the
   request thread. The channel must be passed per-request, not read from the thread-local. `SseStream` is already
   `synchronized` and built for exactly this cross-thread push; the handler must still block until the stream ends
   (`openSse()` + `handle()`'s finally guard already own that lifecycle).

Chunk framing — one event per delta, then the terminal marker:

```text
data: {"id":"chatcmpl-…","object":"chat.completion.chunk","created":…,"model":"metatron",
       "choices":[{"index":0,"delta":{"content":"tok"},"finish_reason":null}]}

data: [DONE]
```

Reasoning deltas ride `onPartialThinking` → `delta.reasoning_content` (non-standard, but the common convention).
**Streaming tool-call deltas are the hard sub-piece**: the model emits partial JSON fragments that must be
accumulated, indexed, and re-emitted as `delta.tool_calls[{index,id,function:{name,arguments}}]`. Defer; content
streaming is the 90% case.

## 5. Work items (ordered)

1. `openai::T` surface + `openai_http` handler Type, registered in `webInstSet`.
2. `openaiServer` — request→`Agent` translation and response framing (non-stream).
3. `openai_httpHandler.doPost` (non-stream) + `GET /v1/models`, mounted in the boot route table.
4. tests — request/response mapping against a stubbed `Agent`, then a real `HttpServer` GET/POST (the
   `mcpHttpHandlerSseTest` pattern).
5. streaming — the token bridge (feature or `chatStream`) + chunk framing + `[DONE]`.
6. disconnect → `agent.interrupt()` (wire the `SseStream` write failure to the existing interrupt flag).
7. tool calls — non-stream first, then streaming tool-call deltas.

## 6. Open questions

* **model routing** — one `openai_http` handler serving one `Agent`, or a `model` registry mapping the OpenAI
  `model` field to an `Agent` vid? MCP does per-mount identity; start with one mount = one agent.
* **auth** — harnesses send `Authorization: Bearer …`; ignore, or gate? A no-op accept keeps drop-in simple.
* **`finish_reason`/`usage`** — `ChatResult` carries `chat`/`user`/`time`/`stop`/`error` today; token counts come
  from `CostFeature`. Map what exists, leave the rest null/estimated.
* **streaming feature vs `chatStream`** — the feature keeps everything in mtron-land but fights the thread-local;
  `chatStream` is a small `Agent` addition. Decide before step 5.
