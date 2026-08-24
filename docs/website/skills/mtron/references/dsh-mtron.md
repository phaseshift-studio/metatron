# dhs→mtron — the harness memory bus, first adapter

`--- name: dhs-mtron
description: >
  Migrate DSH harness session memory into a native metatron agent memory tree.
  The first adapter on the inter-harness memory bus: a zstd JSONL transcript
  becomes a lst of typed message recs (user, system, thinking, ai, tool_result)
  that load into a live VM and are written with a single .to() -- after which
  the migrated agent's history is addressable, queryable, and assertable in
  metatron.
---`

## What this is

AI harnesses each keep agent memory in their own format (DSH: zstd-compressed
JSONL; others: markdown dirs, API JSON, ...). A harness-bound agent cannot
query, assert over, or carry its memory in another tool. This adapter is the
first link on a **memory bus**:

```
DSH transcript          mtron text (asset output)        the universal space
~/.dsh/sessions/        [ user::[=>], thinking::[=>],    /usr/<agent>/message
  */session.jsonl.zstd   system::[=>], ai::[=>],           (one typed rec per
  (JSONL, one event      tool_result::[=>] ]              conversation unit)
    per line)
      │  assets/dsh_memory_loader.py
      │  (zstd -dc + JSON → map → emit)
      ▼
   *<mfs:...>.parse()   -- the file EVALUATES to the lst in a live VM --
      ▼
   .to(/usr/dr/message) -- and the agent is resident in metatron --
```

**Design principle:** the `as`-map (here, the python asset + `parse`) is the
unit of an adapter. N harnesses need N adapters — not N×N bridges — because
the middle is the metatron message model, a typed contract that lives in the
space. Anything that can produce the message recs lands; anything that can
read them consumes.

## The DSH side (source of truth)

DSH stores its memory under `DSH_HOME` (default `~/.dsh`):

```
~/.dsh/
├── settings.yaml
├── storages/workspace.json                [-- workspace → sessions index --]
├── storages/session_projcache.json        [-- per-session identity/stats  --]
└── sessions/<workspace-slug>*/session.jsonl.zstd   [-- the memory: one JSON event per line --]
```

Key env vars (read them, do not assume): `DSH_HOME`, `DSH_SESSION_ID`,
`DSH_SESSION_JSONL` (the exact bundle of the running session),
`DSH_SHELL=1`.

`session.jsonl.zstd` is **zstd-compressed JSONL** — every line a JSON object
with `type`, `seq` (monotonic), `time` (epoch ms), `data`. A live census of
one session (42,956 events):

> **Source formats (as of 2026-08):** the installed DSH persists to this
> zstd-JSONL (verified live — the bundle keeps appending while running), with
> a supplementary TUI layer under `~/.dsh-tui/` (`session-index.json` derived
> index over the same session ids, `history.jsonl`, `effect-ledger.jsonl`).
> DSH has also been described as moving to a **SQLite** primary backend.
> The bus is unaffected by which reader is canonical: the pinned surface is
> the *emitted contract* (five TIDs, envelope, `contents` correlation), and a
> SQLite backend is simply a second reader (`sqlite rows → event dict → the
> same load()`) — nothing downstream changes.

| events | type | migration |
|---:|---|---|
| 1 | `session` | folded into the synthesized `system::` (id, cwd, preset, createdAt) |
| 109 | `user/message` | → `user::` (content[].text joined) |
| 378 | `assistant/message` | → `thinking::` (reasoning blocks) + `ai::` (text blocks) |
| 32,282 | `reasoning-chunks` | dropped — superseded by the final `assistant/message` reasoning |
| 4,214 + 3,961 | `assistant/chunk`, `text-chunks` | dropped — streaming deltas of finals that exist |
| 361 | `tool/call` | → `ai::` with `tool_requests=>[tool_request::]` (name, args, contents=call-id) |
| 377 | `tool/result` | → `tool_result::` (name, contents=same call-id, text) |
| 388+387 | `step/start`, `step/end` | dropped — grouping carried in `chat_id` |
| 72+71 | `turn/start`, `turn/end` | dropped — `chat_id` = turn |
| 266 | `agent/inbox/spliced` | dropped — harness message edits, not conversation |
| 22 | `compaction/*`, `request/*`, `permission/*`, `sandbox/*`, `command/*`, `llm/retry*`, `session/title*` | dropped bookkeeping; **unknown future types are NOT dropped** — they emit as `system::[text=>"[unmapped event] …"]` so nothing is silently lost |

## The metatron side (the contract, learned from live memory)

The target shape was **read from the live VM**, not invented — `*/usr/dr/message/+`
shows the contract; `*/usr/dr/message/+.tid()` gives the registry census:

```
/m/llm/message/user         × 4
/m/llm/message/system       × 3
/m/llm/message/thinking     × 4
/m/llm/message/ai           × 6
/m/llm/message/tool_result  × 2
```

Envelope (all variants): `text` (str, multi-line), `time`, `session` (uri, e.g.
`/usr/dr/session/1`), `depth` (int), `chat_id` (int).
Variant extras: `ai::` may carry `tool_requests => [ tool_request::[ name, args,
contents, text ] ]`; `tool_result::` carries `name`, `contents` — where **`contents`
is the call-id** that correlates an emitted `tool_request` with its `tool_result`.

## The asset — `assets/dsh_memory_loader.py`

```
python3 assets/dsh_memory_loader.py \
  [--bundle PATH]...        [-- one or more session.jsonl.zstd (default: all under DSH_HOME/sessions) --]
  [--out FILE]              [-- default <assets>/dsh_memory.mtron --]
  [--agent NAME]            [-- target agent; session uri /usr/<agent>/session/N (default dr) --]
  [--session N]             [-- metatron session number (default 1; bundles load N, N+1, ...) --]
  [--min-chunks N]          [-- >0: reconstruct thinking from streamed chunks if a final lacks it --]
```

Behavior and invariants (the loader is an assertion, printed each run):

1. **Unwraps** with `zstd -dc` (CLI; falls back to the `zstandard` module).
2. **Joins** reasoning into finals (chunks are deltas of messages that already exist as events).
3. **Correlates** `tool/call` → `tool/result` by call-id (`contents`); an unmatched
   result still loads (id falls back to the harness id).
4. **Synthesizes exactly one `system::`** per session: provenance (session id,
   workspace, model(s) from `request/context`, agent preset, mapping note).
5. **Drops nothing silently** — every unknown event type is preserved as a
   `system::` "[unmapped event]" record instead of discarded.
6. **Audits** — prints per-bundle: events in, messages out, dropped bookkeeping,
   target session uri, last chat_id.
7. **Time** — epoch-ms becomes an ISO-8601 UTC `str`. (The live messages carry
   `datetime::` values; if the message types are ever re-typed to require a
   datetime, switch `iso()` to emit metatron datetime literals.)

**Contract tests** — `assets/tests/test_dsh_memory_loader.py` (stdlib unittest,
no VM needed; needs the `zstd` CLI) pins the mapping against a synthetic
bundle: variant census, call-id correlation, unknown-event preservation,
bare-`name` emission, and the no-bare-`'…'`-strings rule.

```
python3 .metatron/skills/mtron/assets/tests/test_dsh_memory_loader.py   # 3/3 green
```

The emitted file is **mtron source**: a single `[ … ]` of typed recs
(`user::[…], thinking::[…], system::[…], ai::[…], tool_result::[…],
ai::[…, tool_requests=>[…]]`). Nothing in the file is data-about-data — the
file **is** the program that produces the memory list.

**String rule (verified live, do not regress):** mtron `'…'` strings have **no
escape mechanism** — both `\'` and `''` fail to parse. Every emitted string is
therefore a triple-quoted literal (three double quotes around the text), which
provably carries apostrophes, backslashes, newlines, and quotes; in-text
triple-quote runs are defused to smart quotes. `name` fields emit as **bare uri
tokens** (the type is `uri::T`, never a str), and the nested `tool_request`
carries the full `message::T` envelope (`text` + `session` required — it is a
message refinement, not a bare sub-rec).

**`to` semantics (from `Obj.java`): `to(uri)` = `Router.writeToSpace(uri, lhs)`
— it writes whatever the LHS evaluated to, with no internal materialization.**
So `*<mfs:file>.to(path)` writes the parsed+type-checked lst when the deref
evaluates (as in the interactive console), and the one documented ambiguity is
the `from` TODO ("only resolves when explicit mono args (not code args)") —
detect a wrong write immediately after with `*/path/+.tid()` (a stray `str::`
entry means the raw text was written; recover by re-writing).

## The workflow (the actual run)

```
# 1) zstd must be available (CLI or the python module) — one-time
zstd -V

# 2) emit the memory file (dry run, no VM touched)
python3 .metatron/skills/mtron/assets/dsh_memory_loader.py \
       --bundle ~/.dsh/sessions/<slug>*/<session-id>/session.jsonl.zstd \
       --out target/dsh_memory.mtron

# 3) load in metatron — the file evaluates to the lst
mtron> *<mfs:target/dsh_memory.mtron>.parse()

# 4) the write — the agent becomes resident (confirm before firing:
#    append vs overwrite of /usr/<agent>/message is the user's call)
mtron> *<mfs:target/dsh_memory.mtron>.parse().to(/usr/dr/message)

# 5) verify — the memory should now be queryable like any other space
mtron> */usr/dr/message/+.tid()                       [-- variant census (expect new user/ai/thinking counts) --]
mtron> */usr/dr/message/+.(text=>?has("WidgetCanvas"))  [-- search the migrated history --]
mtron> *<mfs:target/dsh_memory.mtron>.parse().range(0,1)  [-- spot-check provenance system:: --]
```

Verified mechanics (live, session 2026-08): `parse("5+5")→10`;
`parse('lst::[5,6]').range(0,1).count()→1`;
`parse('user::[text=>hi,time=>6]').range(0,1).count()→1` (typed literals
resolve against the live type registry; `rec(...)` is not a constructor — use
`tid::[…]` literals); a 2.5 MB / 1,491-record file parses through the same
pipe. Parse-failure is observable: the `rec(…)` control returned count 0.

## Why this matters (the bus)

An agent's memory becomes **addressable data**: `session`, `depth`, `chat_id`
are traversal keys, `text` is a regex surface, `contents` is a join key.
Consequences that follow without new infrastructure:

- **Cross-tool identity** — the migration writes to `/usr/<agent>/message`;
  the agent's name and history are the same address in metatron as anywhere
  else. A DSH session id becomes a `session` field, not a home-directory path.
- **Self-query** — "what did I say about X?" is a `has(...)` over the space,
  the same idiom a build system uses over its own logs.
- **Assertable migration** — counts in = counts out, printed per run; an
  adapter that cannot be checked is not an adapter, it is a copy.
- **N adapters, one contract** — Claude `~/.claude/projects/*/memory/*.md`,
  OpenAI chat-completions JSONL, the `@Training` datasets already in
  `assets/` — each is another thin emitter into the same five TIDs.

## Caveats / open calls

- **Append vs overwrite** on `.to(/usr/dr/message)` — confirm before firing; a
  dedicated tree (`/usr/dsh/message` or `/usr/<agent>/session/dsh-<short>`) is
  the less invasive default.
- **`time` is a str** in the emitted recs (see invariant 7).
- **User-message system-reminders** (harness injections spliced into user
  turns) are preserved verbatim — faithful, but noisy for LLM re-consumption;
  a `filter` pass can strip them at read time without touching the data.
- **`depth`** — DSH `delegationDepth` 0 → emitted as 1 (matching the live
  convention) for top-level sessions; multi-deep DSH delegations are not
  exercised yet.
- **Live append** — a running DSH session keeps growing; snapshot the bundle
  (copy the `.zstd`) before migrating if a stable picture is required.
