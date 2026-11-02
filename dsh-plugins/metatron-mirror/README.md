# metatron-mirror

A deepseek-harness (cordis) plugin that routes the dsh chat lifecycle into
**metatron's message ledger**, so the harness conversation (user / ai / system /
thinking / tool request / tool result / compaction) is stored in metatron's uri
space. Delivery is **durable** (at-least-once, watermark-tracked) — metatron
downtime or a plugin restart never loses the conversation, it just delays it.

## What gets mirrored

dsh emits a `session/event` hook for every session-log entry. The plugin maps
the message-bearing ones (per session, in log order) to metatron
`add_message` calls on metatron's message MCP server (`mcp_message`):

| dsh event             | metatron `kind`     | notes                                                        |
| --------------------- | ------------------- | ------------------------------------------------------------ |
| `request/header`      | `system`            | `header.system`, sent once per session (deduped by text)     |
| `user/message`        | `user`              | injected context carries `name: plugin:<name>`               |
| `assistant/message`   | `ai`                | text blocks; tool-call blocks become `tool_requests`         |
| `assistant/message`   | `thinking`          | reasoning blocks                                             |
| `tool/result`         | `tool_result`       | `name` = tool (joined from call id), `contents` = call id    |
| `compaction/summary`  | `compaction`        | the compaction SENTINEL — `text` is dsh's resume summary     |

Structural events (`turn/*`, `step/*`, `assistant/chunk`, `todo/write`,
`request/context`, `session/end-seed`, `compaction/start`, `compaction/end`,
`compaction/prune`) are not ledger material in v1 and are skipped (but their
seqs still advance the watermark, so they are never retried).

The `compaction` row keeps the mirrored ledger aligned with metatron's own
compaction model: a `message/compaction` sentinel bounds the live window
(`SpaceChatSessionStore` reads the suffix after the newest sentinel via
`stopAt`), and dsh's shadowed raw events were already delivered before the
sentinel — exactly the append-only shape metatron's native
`compactSession` produces.

Records land at `<root>/message/_?incrq`, scoped by the session envelope
`<root>/session/<sessionId>` — readable back through the same MCP tools
(`get_messages`, `search_messages`).

## Reading the conversation back (agent recipe)

The mirror is write-only, but the conversation stays in metatron's uri space, and any
metatron-capable reader — including a DSH agent with the `metatron-message` MCP server
registered (`mcp__metatron_message__…` tools) — can fetch it:

- **root** `/usr/dsh`, session envelope `/usr/dsh/session/<sessionId>`
- **tail**: `m_llm_mcp_mcp_message_get_messages(root, session, max?)`
- **search**: `m_llm_mcp_mcp_message_search_messages(root, pattern, session, max?)`
- **join**: `ai.tool_requests[i].contents == tool_result.contents` (call id); `tool_result.name` is the tool
- ledger order follows the conversation; append-only

Full record shape and semantics: `.metatron/skills/metatron/references/mcp-mtron.md` →
"metatron's own MCP: the message ledger".

## Durable delivery

Every dsh session event carries a monotonic per-session `seq`. The mirror uses
it as a **watermark**:

- a per-session "last acked seq" persists in
  `<stateDir>/watermark.json` (default `~/.metatron/metatron-mirror`),
  written atomically (tmp + rename) and advanced **only after metatron acks
  the write** (`add_message` returns the written record),
- a failed write (outage, timeout, tool error) leaves the watermark behind —
  the event is never dropped, only replayed later,
- on recovery (handshake ack) and on a timer (default 15s) the mirror
  replays the gap, **in seq order**, reading the session's durable log
  (`session.events` — a resumed session carries its full stored log as its
  seed), so first contact with a session bootstraps everything not yet
  mirrored,
- every write is stamped `attributes {source: "dsh-mirror", dsh_seq: <n>}`
  (the session id is already in the ledger's session envelope) — an
  at-least-once redelivery is recognizable and dedupe-able,
- the mirror stays fire-and-forget from the chat loop's view: a per-session
  delivery queue serializes writes, and a failing session pauses until a
  wake resumes it — dsh never blocks or rejects because of metatron.

Guarantees: **at-least-once, never lost, seq-ordered per session.** The one
known wart: a `request/header` inside a replayed window after a process
restart re-sends the `system` line (in-process text dedupe does not survive a
restart) — the stamp makes the duplicate recognizable.

## Transport

Plain JSON-RPC over WebSocket against metatron's message MCP server (`mcp_message`)
(default `ws://127.0.0.1:8555/message`) — the same handler `dsh-mcp-client`
bridges through `websocat`. The client:

- queues sends through a single promise chain (ledger order preserved),
- reconnects with exponential backoff and re-handshakes (`initialize`),
- is fire-and-forget from the listener's view — the chat loop never blocks on
  or rejects because of metatron,
- closes its socket when the plugin fiber unloads (`ctx.effect` disposer).

## Loading

Disperable (per-invocation overlay):

```bash
pnpm dsh web --patch /home/killswitch/software/metatron/dsh-plugins/metatron-mirror/cordis.yml
```

Permanent (profile):

```bash
dsh plugin --profile web add /home/killswitch/software/metatron/dsh-plugins/metatron-mirror
```

## Config

`apply(ctx, config)` — all keys optional:

| key              | env fallback                     | default                        |
| ---------------- | -------------------------------- | ------------------------------ |
| `url`            | `METATRON_MIRROR_WS`             | `ws://127.0.0.1:8555/message` |
| `root`           | `METATRON_MIRROR_ROOT`           | `/usr/dsh`                    |
| `enabled`        | —                                | `true`                        |
| `stateDir`       | `METATRON_MIRROR_STATE_DIR`      | `~/.metatron/metatron-mirror` |
| `readyTimeoutMs` | `METATRON_MIRROR_READY_TIMEOUT_MS` | `30000`                    |
| `retryEveryMs`   | —                                | `15000`                       |

## Verifying

```bash
node offline-check.mjs   # fake local mcp server + real plugin (no metatron needed):
                         #   phase 1 — live path: order, joins, stamps, watermark
                         #   phase 2 — metatron down → hold → recover → in-order replay
                         #             with the compaction sentinel, exactly once
node roundtrip.mjs       # live metatron: write a synthetic chat, read it back
node compaction-probe.mjs # live metatron: does the running binary accept kind=compaction?
```

## Files

- `metatron-mirror.ts` — the plugin (name + apply, plus the exported mapper,
  watermark store, and client used by the checks)
- `cordis.yml` — patch overlay registering the plugin
- `offline-check.mjs` — offline end-to-end check (local fake MCP server,
  live path + two outage/recovery rounds)
- `roundtrip.mjs` — live end-to-end check
- `compaction-probe.mjs` — live probe of the `compaction` kind
