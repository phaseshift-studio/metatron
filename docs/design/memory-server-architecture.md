# metatron as an external memory server — deep dive

> **Question** — metatron, running as `docker pull metatron && docker run metatron
> [boot=>external-memory.mtron]`, becomes a **headless memory service for existing
> agent harnesses** (Claude Code, Codex, DSH, Hermes, …). It must (a) *inject*
> memory into each harness's context and (b) *receive* all thoughts/responses for
> storage and processing — with the **least possible per-harness custom code**.
>
> **Date** — 2026-08-29  ·  **Companion doc** — [`memory-landscape-review.md`](memory-landscape-review.md)

---

## 1. The direct answer: yes — one protocol + one file convention, and one honest boundary

There *is* a standard surface nearly every mainstream agent harness respects, and
it is not one but a **three-layer stack**. metatron already speaks the top layer
natively.

| Layer | Standard | What it gives the memory server | Harness-side cost |
|-------|----------|--------------------------------|-------------------|
| **L1 — transport + tools** | **MCP** (Model Context Protocol; 2025-11-25 spec revision; Streamable HTTP + `Mcp-Session-Id`) | Tools, resources, prompts, sampling, elicitation, roots — the full server↔client vocabulary | A 5-line config entry. **Zero custom code.** |
| **L2 — content channel** | **`AGENTS.md` / `CLAUDE.md`** file convention (30+ tools read it in 2026) | The harness injects the file's text into context at session start — *fully implicit* injection with zero harness cooperation | None — the harness already reads it |
| **L3 — timing glue** | Harness **hooks** (Claude Code `SessionStart`/`UserPromptSubmit`/`Stop`; Codex `notify`; …) | Deterministic *capture* events: "session started", "prompt received", "turn ended" — POSTed to metatron's REST surface | ~10 lines of declarative config per harness, **no metatron code** |

The honest boundary: **no standard exists yet for "intercept every thought".**
MCP deliberately has no transcript-streaming primitive — sampling/elicitation go
server→client for *generation* and *input*, not for *observation*. So fully
implicit capture always converges through one of three channels, and the design
makes all three work against **one** contract:

1. **Cooperative** — standing instructions in the memory file + MCP tools make
   the agent itself call `remember()` (universal, LLM-cooperative);
2. **Hook-driven** — the harness's own hook POSTs the event (deterministic,
   per-harness declarative);
3. **Emitter-driven** — a thin adapter tails the harness's transcript file into
   the contract (full fidelity; the DSH adapter is already in the repo).

Everything harness-specific lives in **L3 artifacts that sit outside metatron's
core** (a config entry, a hook snippet, one ~100-line Python emitter). metatron
core stays harness-agnostic; the *contract* (five message TIDs + `claim` /
`loose_end` + briefing JSON) is what's stable — the same "N adapters, one
contract" principle as the existing DSH memory bus
(`.metatron/skills/mtron/references/dsh-mtron.md`).

---

## 2. The standards landscape, precisely

### 2.1 MCP — the only protocol the whole field converged on

State of play (2026):

- **Spec**: latest revision [2025-11-25](https://modelcontextprotocol.org/specification/2025-11-25/changelog)
  builds on 2025-06-18 (elicitation, structured output) and 2025-03-26
  (Streamable HTTP superseding SSE). The stable core a memory server needs:
  - **Server → client**: `tools/list`, `tools/call`, `resources/list/read`,
    `prompts/list/get`, notifications (`resources/updated`, …).
  - **Client → server (server-initiated, per spec)**: `sampling/createMessage`
    (*the harness's LLM generates text on request*), `elicitation/create`
    (*ask the human for input*), `roots/list` (*the workspace roots*).
  - **Identity**: `initialize` carries `clientInfo` (name/version) + client
    capabilities; Streamable HTTP carries `Mcp-Session-Id`.
- **Who speaks it**: Claude Code (stdio/SSE/HTTP + hooks + `CLAUDE.md`),
  OpenAI Codex CLI (`[mcp_servers]` in `~/.codex/config.toml`, incl. `url`
  HTTP servers), Cursor, Windsurf, Cline, Zed, and — telling signal —
  **every serious memory product ships an MCP server first** (Mem0, Letta,
  Zep, Memobase, Supermemory; see e.g.
  [this server comparison](https://github.com/provos/ironcurtain/blob/HEAD/docs/designs/memory-server-comparison.md)
  and [`claude-memory`](https://github.com/codenamev/claude_memory)).
- **What metatron already has** (verified in-tree):
  - `mcpServer` — transport-agnostic JSON-RPC dispatch:
    `tools/list|call`, `resources/list|read`, `prompts/list|get`,
    `initialize`, capabilities (all five handled in one class,
    `isa/web/type/mcpServer.java`).
  - `mcp_wsHandler` — WebSocket transport; `mcp_httpHandler` — **Streamable
    HTTP with `Mcp-Session-Id` session management**
    (`isa/web/space/http/handler/mcp_httpHandler.java`) — exactly the transport
    Claude Code / Codex configure as `type: http`.
  - `mcp_mtron_wsHandler` + `mcpMetatronBuilder` — native tools already exposed:
    `write_memory`, `read_memory`, `eval_mtron`, `list_space`, `router_info`,
    `find_inst`, `spawn_wsclient`, `spawn_wshandler` — and resources built from
    skill packs.
  - `type/mcpClient.java` — a metatron-side MCP *client* (needed to drive
    sampling out to the harness).
  - **Docker image already published**
    (`.github/workflows/docker.yml` → `ghcr.io/phaseshift-studio/metatron`) and
    a `boot/docker.boot.mtron` precedent for the `external-memory.mtron` boot
    file.

  What's missing is small and specific: the **memory tool family** (below), a
  `/briefing` **resource** + `recall` **prompt**, capture of `clientInfo` /
  capabilities at `initialize`, and the two server-initiated lanes
  (sampling, elicitation) — plus the L2/L3 glue.

### 2.2 The file convention — the universal *implicit* injection channel

`AGENTS.md` is the 2026 cross-harness context file (Codex, Cursor, Cline,
Aider, … — a 2026 survey cites 30+ tools reading it:
[AGENTS.md spec & adoption guide](https://www.morphllm.com/agents-md-guide));
`CLAUDE.md` is the lineage Anthropic ships in Claude Code. Both are read at
context construction — **no tool call, no user action, no harness code**: this
is the only truly *100% implicit* injection channel available to a memory
server on any harness. The strategic move is therefore: **metatron owns the
file's memory section** (regenerated from live `claim`/`loose_end` state),
while the harness does what it already always does — put it in context.

### 2.3 Hooks — the only place harness-specificity survives

Deterministic capture ("every thought and response") cannot ride on tool
calls — the agent might forget to call one. Harnesses that expose lifecycle
hooks solve this declaratively:

| Harness | Hook/observer surface | What metatron consumes |
|---------|----------------------|------------------------|
| Claude Code | `hooks` config: `SessionStart`, `UserPromptSubmit`, `PreToolUse`, `PostToolUse`, `Stop` | JSON events (cwd, prompt, tool I/O, stop reason) → `remember()` / `close_session()` |
| Codex CLI | `notify` program + rollout files under `~/.codex/sessions/` | events; or transcript tailing (L3C) |
| DSH | native — the memory bus adapter already reads `session.jsonl.zstd` | zero glue; DSH is the reference harness |
| Hermes / others | whatever MCP + file access they have | T0–T1 + one thin emitter if full fidelity is needed (T2) |

The key: hooks are **harness config that talks to metatron's standard REST
surface** — the per-harness artifact is a snippet in *their* repo, not code in
metatron's. That inverts the usual dependency direction of "memory vendor
writes an SDK per harness."

### 2.4 What does *not* exist (and what that implies)

- **No standard "transcript tap"** — every harness's transcript lives in its
  own format/location (JSONL, SQLite, rollout files). The contract is the
  answer: a five-TID normalized envelope (the memory bus already proved it),
  not protocol-level observation.
- **No standard for sampling on all clients** — it is in the spec, but client
  support varies; capability negotiation at `initialize` is the arbiter
  (clients advertise `sampling`/`roots`; metatron probes and degrades).
- **Existing memory MCP servers do only half the job** — Mem0/Letta/Zep/Memobase
  expose `memory.add/search`-style tool façades over a DB. None (a) make memory
  an *executable, queryable address space* (metatron's `eval` tool already
  does this — the harness's agent can run arbitrary mtron over its own memory),
  (b) ship a **proposition layer** (`claim`/`loose_end` with provenance and
  trust `tier`) rather than a flat fact list, or (c) can federate memory
  across harnesses *and* machines because their space is a distributable
  tble-backed VM.

---

## 3. The architecture — metatron-as-memory (MaM)

```
        ┌─────────────────── harness (Claude Code / Codex / DSH / Hermes) ───────────────────┐
        │  context build: reads AGENTS.md ▸ memory section          │  agent loop, tools, …  │
        └──────────────△────────────────────────────────────────────┴───────────┬────────────┘
                       │ L2: file content (implicit injection, universal)        │
                       │                                    MCP (L1)             │
                       │            tools: remember/recall/status/close         │
                       │                  resources: /briefing  prompts: recall │
                       │                  sampling: distill w/ harness model ◂──┼── server-initiated
                       │                                                        │
   ┌───────────────────▼────────────────────────────────────────────────────────▼─────────────┐
   │  docker run ghcr.io/phaseshift-studio/metatron [boot=>external-memory.mtron]             │
   │                                                                                          │
   │   mcp_httpHandler (Streamable HTTP,/mcp)   mcp_wsHandler (ws,/mcp)   http /memory/* REST │
   │        └───────────────┬────────────────────────┬──────────────────────┬──────────────────┘
   │                 memory tool family (insts)      │              briefing writer (AGENTS.md)
   │                        │                        │                        │
   │              message ledger  /usr/<harness>/<workspace>/message   concept/claim/loose_end trees
   │              (five TIDs: user, system, thinking, ai, tool_result)  (claim::T, loose_end::T)
   │                        │                        │                        │
   │                 SummarizeSession-distill (async miniTask — or MCP sampling)
   │                        │                        │                        │
   │                 tbleSpace / memSpace / fsSpace …  (persist to volume, or cluster)
   └──────────────────────────────────────────────────────────────────────────────────────────┘
```

### 3.1 The contract (the standard part — harness-agnostic, owned by metatron)

**Envelope** — exactly the five TIDs and fields already used by the DSH bus and
the native session store, so *native* memory and *migrated* memory are the same
type:

```
message::T            base:  { session => uri, depth => int, chat_id => int, time => datetime }
   user::T            { text, name? }
   system::T          { text }
   thinking::T        { text }
   ai::T              { text, tool_requests => [ tool_request::T{name, args, contents(call-id), text} ] }
   tool_result::T     { name, contents(call-id), text, chat? }
```

**Proposition layer** — distilled above the ledger (already built):
`claim::T { text, kind, source, concept, tier }` and `loose_end::T { title,
desc, status, source, claim, time }` (`llmInstSet`).

**Briefing JSON** — what L2 file and `/briefing` resource contain (already
built by `SummarizeFeature.buildBriefing`):
`{ claim: [{text, location}], loose_end: [{text, location}] }` plus standing
instructions ("call `remember` durable facts as you go; open threads below").

**Tool family** — the closed vocabulary a harness's agent is taught (small on
purpose; `eval` is the power tool):

| Tool | Purpose | Maps to |
|------|---------|---------|
| `remember(text, kind?, concepts?, scope?, tier?)` | store a durable fact / note in the session memory tree | message ledger (`user`/`system` variant or a `note` rec) + optional `claim::*` |
| `recall(query-or-concepts, limit?)` | search + ranked hit with briefing-style pointers | message/concept/claim search (vec when G1 lands); returns text + `!*` pointers |
| `status()` | standing reminders — open `loose_end`s + last claims for this workspace | `buildBriefing` (existing) |
| `close_session(scope?)` | explicit distill now (claims + loose ends) | `summarizeSession` (existing) |
| `eval(code)` | *any* mtron over the memory space — "what did I say about X?" = `*/usr/<h>#/.#(text=>?has("X"))` | existing `eval_mtron` (already exposed; the differentiator) |

Every tool's arguments/returns are the same objects the *native* agent uses —
there is one memory API, two doors.

### 3.2 Where each implicit requirement is satisfied

| Requirement | Channel | Mechanism | Standard? |
|-------------|---------|-----------|-----------|
| **Inject at session start** (any harness) | L2 file | metatron's briefing writer regenerates `<workspace>/AGENTS.md` (plus `CLAUDE.md` shim if the harness is Anthropic-lineage) from live open loose-ends + claims | yes — file convention |
| **Inject mid-conversation** (any harness) | L1 tool result | `status()` / `recall()` answers ride in context as tool output — universal, zero cooperation | yes — MCP tools |
| **Push on change** (capable clients) | L1 notification + resource | `/briefing` resource + `resources/updated`; clients surface when they want | yes — MCP |
| **Use the harness's own model** (distill etc.) | L1 sampling | MCP `sampling/createMessage` to `initialize`-negotiated clients; fall back to a container model | yes — MCP (client-dependent) |
| **Capture every turn** (hook-capable harness) | L3 hooks | `SessionStart/UserPromptSubmit/Stop` → POST `/memory/ingest` | harness-specific but declarative |
| **Capture everything, offline/backfill** | L3 emitters | tail transcripts (jsonl/rollout/zstd) → envelope → `.to(/usr/<h>/message)` | harness-specific, thin Python |
| **Identify session/workspace** | L1 handshake | `initialize.clientInfo` + `Mcp-Session-Id` + (when offered) `roots` → `/usr/<harness>/<workspace>/session/<n>` | yes — MCP |

### 3.3 Capability tiers — graceful degradation by design

A harness gets **as much implicit behavior as it is willing to declare**:

| Tier | Needs | Memory experience |
|------|-------|-------------------|
| **T0 — MCP + file** (default; Claude Code, Codex, Cursor, Cline, Hermes, DSH) | one server config entry | full remember/recall/status/close + `AGENTS.md` briefing + cooperative capture |
| **T1 — + hooks** (Claude Code, Codex) | ~10-line hook config | deterministic per-turn/turn-end capture, no dependence on the agent remembering |
| **T2 — + emitter** (DSH today; Claude/Codex files) | run `python harness/<name>_emit.py` | full-fidelity transcript, offline backfill, unattended long sessions |
| **T3 — + sampling** (clients that advertise it) | nothing extra | metatron distills with the *harness's billed model* — no API key in the container |

Core behavior requires only T0 — which is exactly "as little custom harness
tweaking on the metatron side" as the standard allows: **metatron ships the
server; harness differences are 0–10 lines of their config.**

### 3.4 Identity, tenancy, security

- **Identity** — `Mcp-Session-Id` (already parsed in `mcp_httpHandler`) +
  `clientInfo.name/version` from `initialize` + `roots` (when advertised) →
  deterministic session URI. Same harness+workspace over time = same memory
  tree; different harnesses on the same repo = sibling trees that can share
  the claim graph (§4 M5).
- **Provenance & trust** — every ingested rec is stamped
  `origin => <harness>/<client version>`; native metatron memory earns
  `tier` higher than harness-provided memory; briefing ranks by
  tier × recency × relevance. The `claim::T` `tier` field ("bounded by
  min(source tiers)") is designed for exactly this.
- **Secrets** — redaction pass at ingest (`secret://` patterns, API keys,
  private keys → `redacted::` placeholders) is a memory-bus concern, not a
  harness concern; hooks can pre-filter, but the server must not trust them.
- **Auth** — `Mcp-Auth` bearer (or header token) per harness; the memory
  contract is tenant-scoped by session URI, keys map tenants → readable trees.
  Read-only tokens for T0 harnesses that should only `recall`, write tokens
  for T1/T2.
- **Volume** — the space persists on a Docker volume (`tbleSpace`/`fsSpace`
  backend, or the cluster); "docker run" stays stateless.

---

## 4. The build — geodesics M1–M5

Continuing the geodesic numbering after `memory-landscape-review.md`
(G1–G3). Each step rides existing metatron structure; nothing in M1–M4
requires a new space or a new protocol object.

### M1 — The memory tool family (the standard surface)

**What.** Replace the experimental `write_memory`/`read_memory` with the
closed tool family of §3.1 on the existing handlers
(`mcp_mtron_wsHandler` + `mcp_httpHandler`), plus:

- a **`/briefing` resource** (markdown rendering of `buildBriefing` output)
  and a **`recall` prompt** — both from existing mtron, no new storage
  (the markdown leg depends on the obj→document-structure intermediate map
  listed below);
- **`initialize` handshake**: record `clientInfo` + client capabilities at
  `/sys/web/clients/<session>` (a space, like everything else) and bind
  `Mcp-Session-Id → session URI` (mapping table in the space);
- **`/memory/*` over the existing `httpSpace`** — a *route redirect* exposing
  the memory tree as HTTP resources (the same pattern as a live
  `http://…:8777/docker/+?mimeq=text/plain` serving a space over verbs),
  **not** a per-endpoint handler class: `GET = branch read` (`+/+`) plus
  qprocs (`?hasq`, …) as the retrieval surface. Measured live, the **verb**
  matrix:

  | Verb | Semantics (implemented, 2026-08-29) |
  |------|--------|
  | `GET /path` (+`?+` branches, `?out=` rendering, qproc filters) | ✅ **read** — the default door |
  | `PUT /path` | ✅ **replace** — body (IN serializer, default `application/json`, and the raw string handed the mtron data grammar as a fallback) written via `Router.writeToSpace`. `403 read-only` gates it per-route |
  | `POST /path` | ✅ **replace** — alias of PUT, so mtron's `->` write idiom (which sends POST) works unchanged |
  | `PATCH /path` | ✅ **update** — the `>>=` update algebra: body is a mtron *expression* (overlay `{d=>5}`, numeric add `{d=>+10}`, set promotion `+[d=>100]`, key delete `{b=>none}`, or a plain value for wholesale replace), read **raw** (a data serializer mangles the operators: `+[d=>100]` → the bare uri `<+>`) and applied to the existing object. `404` when the address is absent |
  | `DELETE /path` | ✅ **unlink** — the metatron clear idiom: `writeToSpace(uri, noobj())`. `204` |

  Two bugs closed here: (1) `ON_PUT` re-read the exchange stream after
  `HttpRec.buildRequest()` had already consumed it → `IOException("Stream is
  closed")` → 500 on every write; the write verbs now take the body from the
  request rec. (2) the update delta, if deserialized as data, silently
  degraded (`+[d=>100]` → `<+>`); PATCH now reads the body raw and runs the
  real `update_` (`>>=`) operator.  Both pinned by `web_httpHandlerTest`.

  A note on fidelity: `ObjJSONSerializer.simple()` is **not** 1-to-1 with
  mtron — the more faithful, reversible translation is `application/x-mtron`.
  Complex types (sets, uris, insts, code) round-trip through the x-mtron
  rendering but not through JSON; read those back with `?out=application/x-mtron`.

  Remaining M1 policy work: envelope stamping of §3.4 at the write door
  (`session`/`origin`/`tier` from the client identity, not the body) and auth.
  Response renderings are selected by the output MIME
  (`MIME.MIMEType.serializer()`):

  | `?out=` | Renderer | Status |
  |---------|----------|--------|
  | `text/plain` | the obj's `.toString()` | ✅ works |
  | `application/json` | `ObjJSONSerializer.web()` | ✅ works (pointers flattened to strings — parseable anywhere) |
  | `application/x-mtron` | `ObjmtronSerializer` | ✅ works (typed recs, live `!*` inst pointers) — but browsers download it as an octet stream: for programs, not tabs |
  | `text/markdown` / `text/html` / `application/yaml` | `Obj*Serializer.single()` | 🚧 **landmine** — they expect the obj already shaped into the document-typed rec structure (`markdown::T`/`html::T`/`yaml::T`); that intermediate map is not built yet, so they currently return **empty strings** |

  Building that **obj → document-structure intermediate map** is a real M1
  work item — and it pays three renderers at once. Until it lands, the
  "pick the output MIME" adapter rule holds only for plain/json/mtron; T1
  harnesses and ad-hoc scripts hit one surface either way;
- **auth**: bearer token → allowed session trees (read/write scope).

**Effort:** small–medium. Dispatch, types, docWrap are all in place
(`mcpServer`, `mcpMetatronBuilder`); the work is the five insts, one
httpSpace route entry, and the mapping table.

### M2 — The briefing writer (the 100%-implicit injection channel)

**What.** A `brief` inst + boot-file loop (or a subq on the claim/loose_end
trees) that regenerates the **memory section of `AGENTS.md`** (and a
`CLAUDE.md` shim) inside the mounted workspace:

```mtron
<!-- memory:metatron (do not edit — regenerated from live state) -->
## Memory (metatron)
- Open: "wire the mcp_stdio transport" — `/usr/dr/loose_end/3`
- Decided: "scratch writes don't persist — no backing write primitive" — `/usr/dr/claim/7`
- Standing: use `remember` (MCP tool) for durable facts; `status()` for the full list.
```

**What's needed:** one feature (`BriefFeature`, ~`LedgerFeature`-sized), the
**M1 obj→document-structure intermediate map** as its renderer (no new
rendering path — `markdown::T` is the target shape, same map as
`?out=text/markdown`), and a write rule: only touch the fenced section,
preserve the harness's own file content (never rewrite a human-maintained
`AGENTS.md` wholefile). Plus the **`external-memory.mtron`** boot file (precedent:
`boot/docker.boot.mtron`) wiring `httpSpace` + `wsspace` routes
(`/mcp → mcp_http`, `/memory → memory_rest`), a `tbleSpace` or persisted
`memspace` under `/usr/#`, and the brief loop. And the **docker profile**:
volume for the space, ports for http/ws, `healthz`.

**Effort:** small. This is the highest-leverage piece — it is *the* channel
that works on every harness with zero harness code.

### M3 — Emitter adapters (T2 capture)

**What.** `harness/` directory (assets, not Java — the bus pattern):
`dsh_emit.py` (generalizes the existing `dsh_memory_loader.py`),
`claude_emit.py` (`~/.claude/projects/*/…jsonl`), `codex_emit.py`
(`~/.codex/sessions/rollout-*.jsonl`), each:

```
<transcript events> ──map──► five-TID envelope (+ origin stamp) ──emit──► mtron lst
                                                                 │
                        *<mfs:...>.parse().to(/usr/<harness>/<workspace>/message)   ◄─ contract already proven
```

Contract tests per emitter (the DSH loader's `test_dsh_memory_loader.py` is
the template: variant census, call-id correlation, unknown-event
preservation, counts-in = counts-out). Each emitter ships a one-liner mode
for T1 hooks (`--event stdin`) and a tail mode for T2 (`--watch`).

**Effort:** medium for all three; the contract + loader already exist, so
each new one is a mapping table plus tests.

### M4 — Sampling-based distillation (T3: the harness's model does metatron's thinking)

**What.** Extend `summarizeSession`'s model-resolution: when the config's
model is `provider => client` (or the `model` rec's protocol is `mcp`), the
`miniTask` runs through **MCP `sampling/createMessage`** to the connected
harness client instead of a container LLM (`type/mcpClient.java` already
exists for the client leg). Consequences:

- no API key in the metatron container; the harness's own billed model
  distills its own transcript;
- metatron stays model-agnostic *and* model-optional: sampling when
  advertised at `initialize`, container/OLLAMA model otherwise, and
  `close_session` degrades to "queued, will distill with default model."

**Guardrails:** sampling request is bounded (digest ≤ N tokens, single
response, no tools), result is parsed exactly like the miniTask result —
the `<<json:claim>>`/`<<json:loose_end>>` block machinery is unchanged.

**Effort:** medium (new `model` provider kind + the server→client request
path in `mcp_wsHandler`/`mcp_httpHandler`) — but it converts metatron from
"needs its own LLM" to "uses yours," which is a big adoption unlock.

### M5 — Federation (the moat; after G2)

**What.** One metatron server, many harnesses, one claim graph. With M1–M3
in place, the native next step is the cross-harness case: Claude Code, Codex,
and DSH sessions on *the same repo* write sibling trees under
`/usr/<workspace>/…` but **share one `claim`/`loose_end` graph** (the
proposition layer is workspace-scoped, not harness-scoped). Then:

- `recall()` merges hits across harnesses, ranked by `tier` (native >
  harness) × recency × relevance — "what did *my other agent* decide here?"
- M5 composes with **G2** (typed/temporal edges) so cross-harness recall is
  a *query over a typed graph*, not a union of search hits, and with
  `tbleSpace` backends (Postgres/SQLite over the wire) for the
  multi-process/multi-machine case the field still mostly solves with vendor
  SaaS.

**Effort:** medium; mostly policy + the workspace-scoping rule, then G2's
edges make it more than the sum of its parts.

### Sequence

| Order | Step | Effort | Unlocks |
|-------|------|--------|---------|
| **1** | M1 — tool family + handshake + REST | S–M | T0 works for *any* MCP client today |
| **2** | M2 — briefing writer + `external-memory.mtron` + docker profile | S | 100%-implicit injection on *every* harness |
| **3** | M3 — emitters (dsh→claude→codex) + tests | M | T2 full-fidelity capture |
| **4** | M4 — sampling distillation | M | zero-in-LLM-container operation |
| **5** | M5 — federation (with G2) | M | cross-harness shared mind; the moat |

### `external-memory.mtron` sketch (shape, not final)

```mtron
[space => /sys/space,
 web   => [http/host => <http://0.0.0.0:8777>,
           ws/host   => <ws://0.0.0.0:8555>],
 boot/args/memroot => /usr
]
import(/m/llm);
import(/m/tble);
// durable space for memory (volume-backed)
 tbleSpace::[pattern => /usr/#, db => sqlite, file => <mem:memory.sqlite>]@/sys/space/usr;
httpSpace::[host => <http://0.0.0.0:8777>,
            route  => [/mcp    => mcp_http,
                       /memory => memory_rest,
                       /healthz => health]]@/sys/space/web/http;
wsspace::[host   => <ws://0.0.0.0:8555>,
          route  => [/mcp      => mcp_mtron_ws,
                     /mtron    => mtron_ws]]@/sys/space/web/ws;
// the memory service itself
 agent::[name => memory,
         model => <ollama>+[llm => nomic-embed-text],
         feature => [summarize=>[scope => hour::48.0],
                     brief    => [target => AGENTS.md, marker => "memory:metatron"]]]
  @/usr/memory;
```

---

## 5. Risks and open questions

1. **`AGENTS.md` ownership collisions** — many tools now read *and* write
   project files. The fenced-section rule + a stable marker is the
   mitigation, but metatron must never clobber harness-specific sections;
   ship a `--dry-run` mode and keep the file diff small.
2. **Sampling fragility** — client implementations vary; treat as optional
   sugar (M4 always has a non-sampling fallback) and pin the request shape
   in a contract test with a mock client.
3. **Transcript format drift** — harnesses change file formats; emitters are
   version-pinned (`origin => claude-code/1.x`) and the
   `[unmapped event]` invariant (already in the DSH loader) prevents silent
   loss.
4. **Cooperative capture is best-effort** — T0 agents forget `remember()`.
   The standing instruction in the briefing section is the mitigation;
   T1/T3 close the gap. Honest framing: "as implicit as the standard allows"
   — and that standard, right now, is file + tools + hooks.
5. **Secrets in tool outputs** — `recall` must not echo secrets back into a
   context window a different model will see; redaction at read as well as
   write.
6. **Naming/term discipline** — new tokens (`brief`, `remember`, `recall`,
   `status`, `close_session`, `origin`) go through `Tokens.java` review per
   project rule; no free-form vocabulary.

## 6. References

- MCP spec — [2025-11-25 changelog](https://modelcontextprotocol.org/specification/2025-11-25/changelog)
  (2025-11-25 revision; 2025-06-18 added elicitation; 2025-03-26 introduced Streamable HTTP)
- [Codex CLI MCP servers config](https://mintlify.wiki/openai/codex/configuration/mcp-servers);
  [Codex CLI MCP deep-dive (2026)](https://www.tembo.io/blog/codex-cli-mcp)
- [Claude Code setup guide — MCP, hooks, memory (2026)](https://github.com/AlexandrG539/claude-code-setup-guide)
- [AGENTS.md — spec & cross-tool adoption (2026)](https://www.morphllm.com/agents-md-guide);
  [Measuring AGENTS.md (AAIF)](https://aaif.io/blog/measuring-agents-md-what-five-runs-show-that-one-doesn-t)
- [Memory-server comparison — mem0/letta/zep class](https://github.com/provos/ironcurtain/blob/HEAD/docs/designs/memory-server-comparison.md);
  [claude-memory (MCP memory server, prior art)](https://github.com/codenamev/claude_memory)
- [Claude Code MCP sampling integration (client-side evidence)](https://github.com/HolobiomicsLab/Perspicacite-AI/commit/05ecc82a7111e970ff329b3421479b0dd3f514fe)
- In-repo: `memory-landscape-review.md` (taxonomy + G1–G3),
  `.metatron/skills/mtron/references/dsh-mtron.md` (memory-bus contract),
  `isa/web/type/mcpServer.java`, `isa/web/space/http/handler/mcp_httpHandler.java`,
  `isa/web/type/mcpMetatronBuilder.java`, `boot/docker.boot.mtron`
