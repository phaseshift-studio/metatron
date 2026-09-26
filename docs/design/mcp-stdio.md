# mcpStdio — metatron as an MCP server over the process's own stdio

**2026-09-25** · design note, no code written yet · companions: `docs/design/webspace.md`,
`docs/skills/metatron/references/mcp-mtron.md`, `docs/design/acp-mcp-realizations.md`

The goal in one line: `bin/metatron --mcp` serves MCP on stdin/stdout — no ports, no boot server, a VM per
session — using the *same* `mcp_server::T` values and the *same* tool names that `ws://…/mcp` and
`http://…/mcp` already serve.

---

## 1. The review: what is already there

Read against the code, the stdio server is an **assembly job**, not a protocol job. Every layer above the
carrier and every layer below it already exists.

| the design needs | what already is | where |
|---|---|---|
| JSON-RPC dispatch, transport-agnostic | `mcpServer` — `handleMessage(Obj)`, plus the schema-aware `handleMessage(String rawJson)` two-phase parse | `isa/web/type/mcpServer.java` |
| the metatron tool set | `mcpMetatronBuilder.build()` — `eval_mtron`, `write_memory`, `read_memory`, `list_space`, `router_info`, `find_inst`, `spawn_wsclient`, `spawn_wshandler` + skill resources | `isa/web/type/mcpMetatronBuilder.java` |
| a server from any skill | `mcpServer.of(skill)` and the `as?mcp_server<=skill` row | `mcpServer.java`, `webInstSet.java` |
| two carriers to imitate | `mcp_wsHandler` (per connection) and `mcp_httpHandler` (Streamable HTTP + SSE) — both *compose* `mcpServer`, neither subclasses the other | `isa/web/space/{ws,http}/handler/` |
| out-of-band delivery, already proven | `mcp_httpHandler.doGet` opens an `SseStream` and pushes from a `?subq` outbox | `mcp_httpHandler.java`, `SseStream.java` |
| the process-spawn protocol | `mcpClient.createTransport` picks `StdioMcpTransport` when `command` is present — metatron already *speaks* stdio MCP in the other direction | `isa/llm/type/mcp/mcpClient.java:194` |
| tokens | `STDIO` (declared, unused in server code), `SERVER`, `IN`, `OUT` | `Tokens.java` |
| handler-as-type (not space) | `HTTP_MCP_HANDLER_TYPE` / `WS_MCP_HANDLER_TYPE` are handler *types*; `httpSpace` / `wsSpace` own only the socket + route table | `webInstSet.setup()` |
| "a host owns the process's streams" | `mSystem.in(stream)` / `mSystem.out()`, `Console.installStdoutCapture()`, `ScreenOutputStream` | `isa/sys/mSystem.java`, console |
| somewhere for a blocking transport to live | `BootLoader.load()` parks the main thread on `SHUTDOWN_LATCH` for headless boots; the console runs its blocking REPL on a virtual thread | `BootLoader.java:508-557` |
| a portless profile precedent | `boot/console.boot.mtron` — "no ports are bound … safe to boot beside a live server" | `boot/` |
| declarative config idiom | JSON snippet → `mcp_client::T` (`mcpServers` unwrapping, `args`→`command`, `env`→`headers`) | `webInstSet.java:582-616` |

**The missing piece is a carrier.** One class, plus the stream discipline that makes stdout trustworthy.

---

## 1.5 Process lifetime — one JVM per session, not per call

Three models are easy to conflate, so name them:

| model | invocation | JVM boots | state |
|---|---|---|---|
| one-shot | `bin/metatron -e "1+2"` | per expression (`ONE_SHOT = true`, so `load()` skips the park) | none |
| **session** | `bin/metatron --mcp` | **once per MCP session** | accumulates in the VM |
| per-call | a client shelling out per tool call | every call | none |

A stdio MCP server is a **long-lived subprocess**: the client spawns it when the session starts (some clients
lazily, on first tool use), sends `initialize`, then pushes every `tools/call` down the same pipe until it
closes stdin. One JVM boots, parks on `SHUTDOWN_LATCH`, and answers many calls. This is precisely why `--mcp`
must **not** set `ONE_SHOT`: the headless park in `load()` is what keeps the process alive after the boot file
has been evaluated, and the carrier's reader is what it is parked for.

Consequences the design has to own:

- **Cost is paid once, at attach.** First-call latency ≈ JVM + VM boot (+ the launcher's compile check in a dev
  checkout; an installed `lib/metatron.jar` skips maven). Steady-state call latency is tool execution only.
  `bin/metatron-console`'s portless profile is documented at ~3s; `boot/mcp.boot.mtron` adds `import(/m/llm)`
  and the skill-resource scan in `mcpMetatronBuilder.build()`, so measure it rather than assume it.
- **State is session-scoped and accumulates** — memories written by `write_memory`, spaces created by
  `eval`, subscriptions, router state, all alive until EOF. That is the point of a VM per harness, and it
  also means a tool call can leave the VM mutated.
- **A hung tool call hangs the session**, not merely the call — §7's per-request dispatch is what keeps the
  rest of the surface answering.
- **N harnesses ⇒ N JVMs.** Fine for one developer's editor; worth stating before wiring it into a fleet.
- **`RESET` is the only mid-session restart** (§6, §10): it swaps the JVM under a live pipe, which is why the
  recommendation is to leave it off in stdio mode rather than treating it as an ordinary metatron feature.

What "session" scopes, exactly — because it is *not* per agent:

| the harness does this | the stdio VM sees |
|---|---|
| starts up and attaches the server | one boot, one `initialize` |
| runs agent A, then agent B, then a subagent | the same VM and the same state throughout |
| fires several tool calls concurrently (parallel subagents) | concurrent requests into one VM — §7's dispatch + the synchronized write side |
| restarts (editor restart, server crash/restart policy) | a fresh VM; in-memory state is gone, sqlite/fs-backed state is not |
| exits | EOF → shutdown |

### 1.6 One world or many islands is a property of the boot's spaces, not of the transport

A stdio session is a separate JVM from any server already listening on 8555/8777. Whether that makes it a
*different world* or merely **another door into the same world** is decided entirely by which spaces the boot
profile mounts — the carrier has no opinion, and should have none:

| the profile mounts | N VMs behave as |
|---|---|
| `tblespace` (sqlite / MySQL / Postgres), `grphspace`, `dckrspace`, `mqttspace`, `fsspace` — anything with a host | **one world with several entrances.** An agent on stdio and the agent on 8555 are reading and writing the same rows; state outlives both |
| `memspace::T` only | **isolated ephemeral islands.** Each VM has its own state, gone at EOF, and two agents cannot see each other's memories at all |

Which means this is not a design axis to choose between — it is a consequence of the boot being used, and it
should be documented as such rather than as a mode of the transport.

Two consequences that are worth writing down because they are easy to get wrong:

- **Coherence across VMs holds where the backend supplies it.** `tbleIncrQ` inserts through
  `existingTableSchema().ensureTableAndInsert(...)` and takes the **database-generated key** as the record's vid,
  so an append-only ledger keeps coherent ids and order when several JVMs are writing it. A `memspace`
  increment is per-JVM and has no such guarantee.
- **Advisory locks do not cross processes.** `lockQ` keeps its registry in an in-VM `Lst`, so `?lockq` only
  coordinates threads inside one JVM. Two stdio VMs (or a stdio VM and the live server) writing the same sqlite
  file are coordinated by the *database*, not by metatron's lockq — worth knowing before pointing two agents at
  one store with write-heavy tools.

Note the asymmetry this creates for the ledger case: the mirrored dsh conversation
(`metatron-mirror` → `ws://127.0.0.1:8555/message`, root `/usr/dsh`) is a `tblespace` over
`.metatron/dsh.sqlite` in the drstynx profile, so a stdio profile that mounts that file reads the same ledger by
construction — whereas the same tools against a `memspace` root would read nothing at all. Same tools, same
code, different answer, decided by one line of boot data.

---

## 2. The shape

| # | piece | where | why there |
|---|---|---|---|
| 1 | `mcp_stdioHandler` — the carrier | `isa/web/space/stdio/handler/mcp_stdioHandler.java` | third sibling of `mcp_wsHandler` / `mcp_httpHandler`; composes `mcpServer`, owns only transport detail |
| 2 | `StdioProtocol` — claim fd 1 once, hand everything else to stderr | `isa/web/space/stdio/StdioProtocol.java` | process-global, must be installable *once*, before boot |
| 3 | `STDIO_MCP_HANDLER_TYPE` registration | `webInstSet.setup()` | makes `mcp_stdio` a resolvable short name, like `mcp_mtron` / `mcp_http` |
| 4 | `boot/mcp.boot.mtron` — portless profile | `boot/` | the `console.boot.mtron` sibling: capability as data |
| 5 | `--mcp` flag | `BootLoader.main` | the API the user asked for; sugar over 4, guarantee over any profile |
| 6 | launcher output discipline | `bin/metatron`, `bin/lib/utility.sh` | the shell must not write to fd 1 before the JVM owns it |
| 7 | **tool pull-out** — the tool instructions get registered as instructions, and `mcp_server`'s `tool` field accepts a collection of references (§5.3) | `mcpMetatronBuilder`, `webInstSet`, `mTool` | turns a Java-hardcoded tool set into a boot-data selection; the reason `!*eval` works |

No `stdioSpace`. A space owns an addressable carrier and a route table; stdio's carrier *is* the process, and
there is no path to route on (one connection, no `Mcp-Session-Id`). This is exactly the split that already
exists: `mcp_httpHandler` is a handler type while `httpSpace` is the socket.

---

## 3. The carrier

```java
public class mcp_stdioHandler extends MRec {          // parallel to HttpRec / WebSocketRec

    public static final fURI STDIO_MCP_HANDLER_TID = WEB_ISA_TID.extend(MCP).extend("mcp_stdio");

    public static final Type STDIO_MCP_HANDLER_TYPE = Type.Builder.build()
            .tid(webInstSet.MCP_TID)                  // an mcp::T surface, like mcp_http / mcp_ws
            .vid(STDIO_MCP_HANDLER_TID)
            .isaPredicate(rec(uri(SERVER).maybe(), T(MCP_SERVER_TID)))
            .constructor(instC(INST_CTOR_TID.rng(STDIO_MCP_HANDLER_TID), lst(T(REC_TID)), (lhs, inst) ->
                    mcp_stdioHandler.of(inst.arg(0).asRec())))
            .create();

    private final mcpServer   mcp;   // the protocol
    private final InputStream in;    // default: System.in  (the transport is the only reader in this mode)
    private final PrintStream out;   // default: StdioProtocol.out() — fd 1, claimed, never closed
    private final boolean     started;

    public void run()  { ... }                            // blocking: one line in, one line out
    public void send(final Obj response) { ... }          // synchronized, compact, single line, flushed
    public Obj  handle(final String line) {               // the schema-aware entry point
        return this.mcp.handleMessage(line);
    }
}
```

Design decisions inside the carrier:

- **Streams are injected** with production defaults, so the unit test drives the *exact* loop over
  `ByteArrayInputStream`/`ByteArrayOutputStream`. Precedent: `WebSocketRec` takes a `WebSocket`, `HttpRec`
  takes an `HttpExchange`.
- **`handle(String)` is used, not `handle(Obj)`** — the schema-aware parse is what keeps a `code::T` argument
  from being mangled before the tool sees it (`mcpServer.handleMessage(String rawJson)` documents why). The
  stdio carrier must not repeat the mistake the WS frame path had to override.
- **`initialize` is answered inline**, before any dispatched work, so the handshake can never be overtaken by a
  slow tool call.
- **A malformed line yields a JSON-RPC parse error with a null id** and the loop continues. A stdio server that
  dies on a bad line is worse than one that reports it.
- **The response serializer is the HTTP carrier's** (`ObjJSONSerializer.simple()`), so stdio, ws and http emit
  byte-identical JSON for the same call.
- **No state in Java fields — the `jvm()` is the object.** metatron's primary principle is that everything
  lives in the rec's jvm map, which is what makes mtron reflective: an agent can `eval` its way into a live
  object and read what it is doing. So the carrier's state (`serving`, request count, the server it holds, the
  resource root, the last error) goes into the `jvm()` — written through the unchecked path where the predicate
  does not declare it (§5.3.2), exactly as `mcp_wsHandler` adds `on_message`.
- **A handle is a Java field, but what it stands for is still data.** The pointer is unavoidable for a live
  `Socket`, `HttpServer` or `PrintStream` — `AbstractSpace<SJVM>` keeps `protected SJVM sjvm` and
  `WebSocketRec` keeps `this.socket` for exactly that reason. That does not exempt the state from the data
  model: it is *transient*, not invisible, and transient state you can see is transient state you can save.
  The existing shape is the model to copy — the transport's IO description lives in the jvm as `in`/`out` MIME
  uris and is read back through `getIO()` (`WebSocketObj.IO.of(rec, default)`; `mcp_wsHandler`'s constructor
  writes `in`/`out => application/json`, so a route can override it; `mcpServer.getIO()` already answers the
  same rec for the protocol). Everything else about a live connection is a jvm entry too, exposed as data the
  way `WebSocketObj.state(conn)` exposes liveness. So the stdio carrier should be queryable while it serves —
  `*<...>/stdio` shows what it is doing — rather than reporting its condition only through Java accessors.

---

## 4. The keystone: stdout *is* the protocol

This is where the feature silently fails, so it gets designed first. Every writer that can reach fd 1 during a
boot or a tool call:

| writer | path to stdout | handled by |
|---|---|---|
| logback, dev mode | `conf/logback.xml` has `<target>System.err</target>` | already safe |
| logback, **installed mode** | `lib/metatron.jar`'s `src/main/resources/logback.xml` has **no `<target>`** → `System.out`, and `bin/metatron` passes no `-Dlogback.configurationFile` in that branch | must be re-pointed explicitly — see below |
| logback appender start | `ConsoleAppender.start()` calls `ConsoleTarget.getStream()` → `setOutputStream(...)` **once** (confirmed in logback-core 1.6.3), so a later `System.setOut` does **not** move it | same |
| `GraphittyLogger.none(...)` | `System.out.print` when no pane is attached | the swap |
| `mSystem.out()` | returns `System.out` ("a host wanting something else replaces the stream rather than calling around it") | the swap |
| boot-file `print`, banners, `.display()` | Graphitty → stdout | the swap |
| `CommonUtil.Spinner` | `\r` frames on `System.out` | the swap |
| `bin/metatron` itself | spinner, maven echo, restart banner | launcher discipline |

`StdioProtocol.install()`, called once before `BootLoader.load()`:

```java
final PrintStream protocol =
        new PrintStream(new FileOutputStream(FileDescriptor.out), false, StandardCharsets.UTF_8);

// everything that is not the protocol goes where the logs already go
System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8));

// logback holds the stream it started with — re-point it, do not trust the swap
for (final Logger l : ((LoggerContext) LoggerFactory.getILoggerFactory()).getLoggerList())
    for (final Appender a : l.iteratorForAppenders())
        if (a instanceof ConsoleAppender ca) ca.setOutputStream(System.err);
```

- Claim **`FileDescriptor.out`** directly, not the `System.out` object: fd 1 is the protocol channel no matter
  who wrapped it, and the transport never closes it.
- Never touch `System.err` — that is the diagnostic channel the client surfaces (`dsh` shows it as server logs).
- `mSystem.in` stays `System.in`; a stdio session has exactly one reader and it is the carrier.

**Ordering trap found while reviewing.** `--mcp` must not quiet logging the way `-e` does. `BootLoader.main:234`
calls `LogObj.setSLF4J("error")` for eval/pipe/quiet mode, *before* any mtron parse has initialized the parser
classes. In this checkout that ordering dies in class initialization, deterministically and from both
`target/classes` and the uber-jar:

```
LogObj.<clinit> → InstSet.<clinit> → mInstSet.<clinit>:94 → MObjFactory.<clinit>:57
Caused by: NullPointerException: machInstSet.MACH_ISA_TID is null
```

(`machInstSet.<clinit>` → `M_ISA_TID` → `mInstSet.<clinit>` → `MObjFactory.<clinit>` → reads
`machInstSet.MACH_ISA_TID` while that class is mid-initialization on the same thread → the field is still
null.) The boot path is fine — `bin/metatron "[log=>error]"` parses its args *before* touching `LogObj`, and
then `MObjFactory` is already initialized. Two other agents were rebuilding this tree while I looked, so the
failure should be re-verified once it settles; the *rule* holds either way: **let the stdio log level arrive as
a boot arg (data, parsed with everything else), never through an up-front `LogObj` call.**

`bin/metatron` in `--mcp` mode: spinner, maven-echo and the `EXIT_RESET` restart banner all go to stderr
(`bin/lib/utility.sh`'s `spinner` takes a stream, or the calls are run with `>&2`). The first byte the client
reads must be `{`.

**The invariant test** (the one that actually protects this): *every line written to fd 1 parses as exactly one
JSON object* — with a boot file that prints a banner, and with a tool that renders a widget.

---

## 5. Entry points

### 5.1 `bin/metatron --mcp`

- Mutually exclusive with `-e/--eval`, `-f/--file`, `-p/--pipe`, `-c/--chat`, `-w/--ws`: each either claims
  stdin/stdout or exits after one shot. `--mcp` refuses with a message rather than half-working.
- Default boot file `boot/mcp.boot.mtron`, used only when neither `-b` nor `$METATRON_BOOT` is given.
- `ONE_SHOT` stays false → the existing headless park keeps the process alive; EOF at the end of the session
  releases it (§6).
- **Guarantee independent of the profile**: after the boot file has been evaluated and before the park,
  `load()` calls `attachStdio(ARGS)` — if no stdio host has started, it materializes `mcp_mtron` and starts
  one. A `STARTED` guard makes it idempotent, so a profile that already mounted a host wins and `--mcp` is
  pure sugar over the profile rather than a competing code path.
- `log => error` is set as a boot-arg default (data), overridable.
- Optional and cheap: warn on stderr if an `httpSpace`/`wsspace` is constructed while in `--mcp` mode — the
  whole point is not to bind the live server's ports.

### 5.2 The profile — `boot/mcp.boot.mtron`

A sibling of `boot/console.boot.mtron`: no ports, no console, no shared backend — i.e. **a minimal island**
(§1.6: the checkout via `mfs:` is shared, everything under `/usr/#` is this JVM's alone).

```mtron
[== mcp.boot.mtron — a portless VM whose only surface is the process's own stdio ==]
[space => /sys/space,
 log   => error,
 typer/stages => [code_resolve => false]]
[===============]
import(/m/mach/io);
import(/m/web,web);
import(/m/llm);
[== the checkout, so tools can read and write source ==]
fsspace::[pattern => mfs:#,
          q       => [mimeq::[=>],lineq::[=>]],
          route   => [mfs: => <.>]]@/sys/space/fs/metatron;
memspace::[pattern => </usr/#>,
           q       => [docq::[=>],subq::[=>],incrq::[=>],mimeq::[=>]]]@/sys/space/usr;
[== the server: a selection over instructions ==]
mcp_server::[tool => [!*eval, !*read_memory, !*write_memory]]@/sys/space/mcp/basic_server;
[== the host: that server, on the process's own fds ==]
mcp_stdio::[server => basic_server]@/sys/io/mcp/stdio;
```

For the "it is just a big calculator" end of §5.5, the server line collapses to one tool:

```mtron
mcp_server::[tool => [!*eval]]@/sys/space/mcp/eval_server;
mcp_stdio::[server => eval_server]@/sys/io/mcp/stdio;
```

Swap the `memspace` line for a durable backend and the *same file* becomes a door into the live world instead
of an island — no code changes, no transport change:

```mtron
[== ...or: the same rows the 8555 server is reading ==]
tblespace::[pattern => </usr/dsh/#>,
            host    => <sqlite:/home/killswitch/software/metatron/.metatron/dsh.sqlite>,
            driver  => <org.sqlite.JDBC>,
            table   => [,],
            q       => [incrq::[=>],subq::[=>],mimeq::[=>]],
            route   => [/usr/dsh/ => <>]]@</sys/space/usr/dsh>;
```

### 5.3 Defining a server from instructions

The tools in `mcpMetatronBuilder` are not really tools — they are **instructions that were never registered as
instructions**. Pull them out into a space and a server becomes a pure selection over them:

```mtron
mcp_server::[tool => [!*eval, !*read_memory, !*write_memory],
            resource => [...]]@/sys/space/mcp/basic_server;
```

and the "just `bin/metatron -e`" server is the same line with one tool:

```mtron
mcp_server::[tool => [!*eval]]@/sys/space/mcp/eval_server;
```

Three findings from the code make this cheap:

- **`!*eval` already resolves today.** `/m/inst/eval` is registered in `Obj.java:1524` as
  `instC(EVAL_INST_TID…, lst(ALL_TYPE), (lhs, inst) -> inst.arg(0))` — it returns its argument, which is the
  whole trick, because a `code::T` argument is already evaluated on the way in. So the minimal eval server
  needs **no new Java at all**. `mcpMetatronBuilder`'s `eval_mtron` is a clone of it plus two extras worth
  deciding on: the `native` stringify toggle, and fail-passthrough (which the carrier already does —
  `handleToolsCall` turns a fail into a JSON-RPC error).
- **The tool-collection ladder already exists** — in `ToolFeature` (`ToolFeature.java:120-138`), which loops
  `tool.elements()` through `mTool | mcpClient | mTool.tool(t)`, and `mTool.tool(Inst)` derives the name with
  `mTool.toolName(inst.tid())`. `mcpServer.of(skill)` does the same normalization by hand. What is missing is
  that `mcp_server` accepts it — and the fix is **in the constructor, not the predicate**: `Obj.Helper.construct`
  (`Obj.java:1180-1206`) tests only the input's *base type* (`protoObj.baseTypeID().test(type.baseTypeID())`),
  hands the raw rec to the type's constructor, then re-tags whatever comes back. So
  `new mcpServer(normalize(config.jvm()), …)` reshapes `tool => [!*eval, …]` into the name-keyed rec the type
  already declares, and `handleToolsList` / `handleToolsCall` stay untouched.
  The predicate must stay **strict** (`tool => rec(URI_TYPE, INST_TYPE)`): `wsSpace:163` and `httpSpace:365`
  classify a route value with `Obj.Helper.specificType(target).test(MCP_SERVER_TYPE)`, so widening it to accept
  a lst would blunt the very test routing depends on. Precedent for reshaping in-constructor is already in this
  family — `mcpMessageServer.generateMcpConfig` and `mcpMetatronBuilder.build` both do exactly that.
- **The drift is documented in the code.** `mcpMetatronBuilder.toolTid()`'s javadoc says "Tools live under the
  `/m/inst` instruction namespace (disambiguated by name) so that `mTool` can map tid → name → docs without
  collision, and so the docq QProc mounted on the instruction space resolves them" — but the implementation is
  `MCP_MTRON_SERVER_TID.extend(name)`, i.e. `/m/web/mcp/mcp_mtron/<name>`. The pull-out is a **return to the
  documented intent**, not a new direction.

What this buys, beyond tidiness:

- **One tool vocabulary.** After the normalization, `mcp_server` accepts exactly what a `tool_feature` accepts:
  instructions, `mcp_client`s, skills, `tool::T` recs. Same shapes, same names, agent or server.
- **A server can carry another server's tools.** `tool => [!(mcp_client::[host=><http://…>])]` becomes legal,
  so a stdio metatron can mount a remote MCP server's tools for its own clients. Today only the agent can do
  that.
- **The tools become usable from mtron**, not just over MCP: `eval(...)` in a REPL or a drstynx agent works the
  same as `eval` over the wire.

### 5.3.1 The name is derived, not typed

You write the **collection**; the names come from the instructions:

```mtron
mcp_server::[tool => [!*eval, !*read_memory, !*write_memory]]@/sys/space/mcp/basic_server;   [-- write this --]
mcp_server::[tool => [m_inst_eval => !*eval, ...]]@/sys/space/mcp/basic_server;             [-- do NOT write this --]
```

The second form is not merely redundant, it is **broken**, and the code says exactly why:

- `handleToolsList` advertises an inst entry's name as `spec.name()`, which `mTool.mtronInstToolSpecification`
  builds from `toolName(inst.tid())` (`mTool.java:198`) — the rec **key is ignored** for inst entries.
- `handleToolsCall` looks the tool up **by that rec key**: `this.at(TOOL).orElse(rec0()).at(uri(toolName))`.
- So a key that disagrees with the derived name yields a tool that is *listed* as `m_inst_eval` and is
  *uncallable*: `tools/call` misses the key and answers `-32601 tool not found`.

Every existing construction site already obeys the derived-name rule — `MCPServerUtil:65`,
`mcpMessageServer:160-162`, `mcpMetatronBuilder:131+`, and `mcpServer.of(skill)` all key with
`mTool.toolName(inst.tid())`. The invariant is real but currently held up by convention in four places; the
normalization in §5.3 is what makes it structural. The name-keyed rec remains the internal representation
`mcpServer` consumes, and it is still the right form for a **non-inst** entry (`handleToolsList`'s `else`
branch names those by key, since there is no tid to flatten).

### 5.3.2 The constructor contract: reshape, or `fail::T`

`Obj.Helper.objTypeCheck` (`Obj.java:1120-1150`) runs on **every `MObj` construction** (`MObj`'s constructor
calls `objCheckAndSave`) and on the checked-write path. It always enforces the base type, and — when the
`TypeCheck.type_pred` stage is enabled, which is the default — enforces `obj.test(obj.type())`. So a typed rec
is **locked** the moment its tid is applied: an `mcp_server::T` cannot be mutated out of its predicate form
afterwards. Three consequences for the pull-out:

1. **`new mcpServer(jvm, MCP_SERVER_TID, vid)` *is* the check.** Constructing a server from a rec whose `tool`
   is still an unnormalized list throws at construction, not at first `tools/list`. Normalization therefore has
   exactly one legal home: the `Type`'s constructor lambda.
2. **That lambda receives the untagged protoObj.** `Obj.Helper.construct` builds it with a **null tid**
   (`MObjFactory.of().toObj(jvm, null, vid, clazz)`) and re-tags only the result
   (`constructedObj.self(constructedObj.jvm(), bigTID, vid)`). So the reshape runs *before* any predicate is in
   force, and whatever shape it returns is the shape that gets locked.
3. **Failing is part of the contract.** `construct()` turns a `fail` return into
   `MTronException.of("unable to construct %s::T: %s", tid, constructedObj)`, so a reshape should return a
   **named fail** ("tool entry %s is not an instruction, an mcp_client, a skill or a tool rec") rather than
   throwing — the reason then survives as the construction error instead of a bare abort.

Lazy alternatives are closed, not merely discouraged: there is no patching a live server's jvm on the first
`tools/list`, because by then the rec is tagged and may already have been written to a space.

One nuance for the implementer — the unchecked family, and why the reshape may use it while the carrier may not:
`objCheckAndSave` runs only where a caller asks for it (the constructors, and `objClone`'s clone path), and the
accessors that route through `clone(...)` are the checked ones. The bypasses:

| unchecked | checked counterpart | effect |
|---|---|---|
| `jvm()` + `.put(...)` | `at(key, value, MUTABLE)` | writes the raw map — no predicate, no save |
| `selfJVM(...)` / `selfTID(...)` / `selfVID(...)` / `self(...)` | `jvm(...)` / `tid(...)` / `vid(...)` | mutates **in place**: no clone, no type check, no space save |
| `atDirect(key)` | `at(key)` | reads the raw stored value, with no auto-pointer resolution — which is why `mTool.tool` reaches for `atDirect(OBJ)` |

So the reshape legitimately uses the raw family — it runs *before* tagging — and `mcp_wsHandler`'s constructor
uses `jvm().put(...)` to add an `on_message` its predicate never declares. After tagging, the typed rec is
read-only in practice: a checked mutation that violates the predicate throws, and an unchecked one silently
produces an object that is not the type it claims to be.

Note what the unchecked path is *for*, given metatron's "no Java fields" principle: it is how an object gains
**state after construction** — the jvm map *is* the object, so runtime state belongs there, and the predicate is
the construction contract rather than a permanent cage. The carrier's `serving`, request counts and last error
are jvm entries like any other, which is what lets mtron reflect on a running server.

(A side finding worth a separate look: `type_pred` is a toggleable typer stage whose default is ON, while
`BootLoader` reads `typer/stage` and every boot file writes `typer/stages` — so those stage lists look inert,
which is what leaves the lock on.)

### 5.3.3 The `server` field

```mtron
mcp_stdio::[server => mcp_mtron]@/sys/io/mcp/stdio;                                  [-- a type that builds one --]
mcp_stdio::[server => basic_server]@/sys/io/mcp/stdio;                              [-- by name, from its vid --]
mcp_stdio::[server => !*</sys/space/mcp/basic_server>]@/sys/io/mcp/stdio;            [-- by reference --]
mcp_stdio::[server => *dr.as(skill::T).as(mcp_server::T)]@/usr/dr/mcp_stdio;        [-- any skill --]
mcp_stdio::[server => mcp_server::[tool=>[!*eval]]]@/my/mcp_stdio;                  [-- inline --]
```

`server` accepts what a ws/http route value accepts: a type with a constructor, an `mcpServer` instance, or a
uri that reads back to one. **Recommendation**: lift that ladder out of `wsSpace.classify` and
`httpSpace.classify` (it is currently duplicated) into one `mcpServerHelper.resolve(Obj)` and let all three
carriers share it, so stdio cannot drift from ws/http on what a "server" is.

### 5.4 JSON → server, the mirror of JSON → client

drstynx configures clients from a published snippet
(`{"mcpServers":{"codegraph":{"command":"codegraph","args":[…]}}}` → `.as(mcp_client::T)`). The same idiom
should configure a server:

```
"""{ "server": "mcp_mtron", "resource_root": "docs/skills/mtron" }""".as(json::T).as(mcp_stdio::T)@/sys/io/mcp/stdio
```

Two facts from the code that this path must respect:

- `as?mcp_client<=json` uses `ObjJSONSerializer.simple()`, whose untyped strings are handed to the **mtron
  parser** — which is exactly why drstynx had to write `"--mcp"` as `"<--mcp>"` (bare `--mcp` parses as
  `minus(minus(mcp))`). `ObjJSONSerializer.literal()` exists for this ("keeps JSON strings as they are
  written"), and is already used by `ObjDockerSerializer`. Any `command`/`args`/`env` crossing a JSON
  typecast should use the literal reader, or flags keep getting mangled.
- `as?mcp_client<=json` unwraps `mcpServers`, merges `args` into `command` and `env` into `headers`. The
  server-side row should mirror it (`server`, `tool`, `resource`, `prompt`, `resource_root`).

### 5.5 The profile spectrum — from big calculator to shared world

Three named points on the same axis, all reached by editing the boot file and nothing else:

| point | the boot mounts | what the agent experiences |
|---|---|---|
| **big calculator** | `fsspace::T`, `bash()`, `memspace::T` — basic system orchestration | `-e` with a live VM: compute, orchestrate the machine, remember nothing. State is scratch and is *meant* to be scratch |
| **island workbench** | the above + `/m/ide`, per-project sqlite, extra inst sets | a real workspace with state that outlives the session, still its own world |
| **shared world** | the same backends the live server mounts (§1.6) | one world, several entrances — the stdio agent and the 8555 agent see the same rows |

The design test: **nothing in the carrier may key off which one was chosen.** Same class, same `mcp_server`
rec, same tool names; the boot decides. If a future change makes the transport aware of persistence, the
carrier has drifted.

Two consequences worth stating:

- **`write_memory` / `read_memory` exist in all three.** That is correct, not a leak: they write to whatever
  space backs the memory vid, so in the calculator they are scratch, in the workbench per-project, in the
  shared world the live server's. *Same tools, three meanings* — §1.6's rule applied to memory specifically.
  A profile that wants no memory story at all can simply not mount a durable space; the tools stay honest.
- **`bash()` already carries its guard in data.** Its type queries are `allow`, `reject`, `dir`, `env` and
  `timeout`, read straight off the instruction's own tid, so `bash?reject=['\brm\b','\bgit\b']` is a *profiled*
  bash rather than a wrapper. That is also where AGENTS.md's "git is off limits" belongs — a reject pattern in
  the profile, not prose the agent may not read. The calculator end is the most powerful and least guarded
  point on the spectrum; the guard belongs in the profile, and the carrier should add no hidden policy of its
  own.

---

## 6. Lifecycle

| event | behavior |
|---|---|
| attach | the client spawns the process once per session and sends `initialize`; no re-spawn, no re-boot per call (§1.5) |
| EOF on stdin | `run()` returns → `BootLoader.close()` → latch release → main returns → exit 0. EOF is how a client shuts a stdio server down. |
| SIGTERM / SIGINT | the shutdown hook `load()` already registers (`BootLoader::close`); the reader is a daemon thread and dies with the JVM. |
| `initialize` | answered; **no session id** — `Mcp-Session-Id` is a Streamable-HTTP concept, and stdio is one session by construction. |
| client dies without EOF | stdin EOF or a failed stdout write ends the loop; both are terminal for the session. |
| `:reset` / `RESET` | see §10 — the one genuinely load-bearing decision. |

---

## 7. Concurrency and long calls

`eval` is unbounded: it can run `mvn`, docker, or an entire agent turn. A single-threaded reader makes
every other request wait behind it, and the client's timeout becomes the failure mode.

- One reader thread; each **request** dispatched to `ThreadExecutor.instance()` — the single funnel that also
  establishes `BootLoader.CURRENT_THREAD`, so mtron thread semantics hold inside a tool call.
- `initialize` and `notifications/*` answered inline.
- `send()` synchronized on the protocol stream: the write side is shared, and a `?subq` subscription's code can
  fire on any thread.
- Phase 2: `notifications/cancelled` → interrupt the worker (the request id is already in the message), and
  progress via `logging` notifications. The HTTP carrier already proves out-of-band delivery works
  (`SseStream` + outbox `?subq`); stdio can push the same notifications **on the same fd, with no second
  connection** — a genuine capability gain over the POST-only path.

---

## 8. Compatibility details worth getting right the first time

- **Framing**: newline-delimited JSON (one message per line, UTF-8, no embedded newlines). Some clients still
  emit LSP-style `Content-Length` headers; detect the prefix and answer with a JSON-RPC error naming the
  expected framing instead of looping on parse errors.
- **Protocol version**: `handleInitialize` always answers `2025-03-26` and ignores `params.protocolVersion`. A
  client pinned to a newer revision may disconnect. Recommend: echo the client's version when we can speak it,
  else answer with our maximum — and include `instructions` (the tool contract: "`eval` is the foundational
  tool").
- **The tool names change, once, at the pull-out (§5.3).** `mTool.toolName` flattens the instruction's *full
  path*, so a tool defined as `!*eval` (`/m/inst/eval`) becomes `m_inst_eval`, and `/sys/inst/bash` becomes
  `sys_inst_bash` — against today's `m_web_mcp_mcp_mtron_eval_mtron`, which encodes the server's vid instead of
  the instruction's. That is a rename for any harness config, prompt, or allowlist naming a tool, including
  this repo's own DSH wiring. It is worth doing once, deliberately: names derived from the instruction are
  stable across servers and profiles, whereas a name derived from the server means the same tool is called
  something different on stdio, ws and http. The naming *rule* is unchanged; only what it is applied to is.
- **Resource paths are cwd-relative**: `mcpMetatronBuilder.build()` reads `Path.of(".metatron/skills/mtron")`. A
  stdio server is spawned by a client with an arbitrary cwd (and `bin/metatron` cds to the repo root), so
  resources silently vanish outside a metatron checkout. Make it a boot arg (`resource_root`) registered in the
  type's `isaPredicate`.

---

## 9. Files

**New**

| file | contents |
|---|---|
| `src/main/java/.../isa/web/space/stdio/handler/mcp_stdioHandler.java` | the carrier |
| `src/main/java/.../isa/web/space/stdio/StdioProtocol.java` | fd-1 claim + logback re-point |
| `boot/mcp.boot.mtron` | the portless profile |
| `src/test/java/.../isa/web/space/mcp_stdioHandlerTest.java` | unit, extends `AbstractMcpHandlerTest`, in-memory streams |
| `bin/test/mcp-stdio-smoke.py` | scripted session over a real pipe (run via `bin/metatron-docker build shell`) |

**Changed**

| file | change |
|---|---|
| `BootLoader.java` | `--mcp` flag + exclusivity + default profile + `attachStdio()` before the park + help text |
| `webInstSet.java` | register `STDIO_MCP_HANDLER_TYPE`; **`MCP_SERVER_TYPE`'s predicate is unchanged** — its constructor does the reshaping (§5.3); later, the `as?mcp_stdio<=json` row |
| `mcpServer.java` | normalize `tool` in the constructor path (the shared ladder); `handleToolsList` / `handleToolsCall` unchanged |
| `mTool.java` | extract the collection ladder `ToolFeature` uses — `mTool.tools(Obj)` → the name-keyed rec `mcpServer` consumes (fixes the hand-rolled copy in `mcpServer.of(skill)`) |
| `mcpMetatronBuilder.java` | pull the tool instructions out into a registered space (whose tid decides the tool names); `eval_mtron` → `!*eval` + the `native`/fail question; `resource_root` as config rather than cwd |
| `bin/metatron`, `bin/lib/utility.sh` | mcp-aware output discipline (spinner/maven/banner → stderr) |
| `wsSpace.java`, `httpSpace.java` | (optional) shared `mcpServerHelper.resolve` |
| `.metatron/skills/metatron/references/mcp-mtron.md` | transports already claim STDIO — make it true; tool names in the tables |
| `.metatron/skills/mtron/references/mcp-server-architecture.md` | still describes the retired `mcp_mtron_wsHandler` and the old tool-name namespace |

---

## 10. The workbench question (tools, not transport)

The transport is the easy half of the ask; "edit code, build" is a **profile** question:

- `import(/m/ide)` gives `project::T` — a semantic java index (`idx/classes/members`), `read_file`, a
  `code/#?subq` auto-save subscription that writes edited fields back to the file, plus `ide:find`,
  `ide:search` and `ide:command('mvn -f … compile')` through `CommandRunner`.
- `import(/m/web)` gives fs spaces (`mfs:` over the checkout) for everything that is not java.
- `CommandRunner.run` is an unguarded `sh -c`. A shell/build tool exposed over MCP should carry the reject
  idiom drstynx already uses (`bash?reject=['\brm\b','\bgit\b']`) — which also encodes AGENTS.md's "git is off
  limits" for the agent, in data rather than in prose.
- Keep the MCP **tool list** small and stable (`!*eval` + friends) and let the **profile** widen what mtron
  can reach. One tool contract then serves codebase work and everything else, and the tool names keep matching
  the ws/http surfaces.
- AGENTS.md's sanctioned build loop is docker (`bin/metatron-docker build`); a workbench profile should expose
  that rather than host-side maven.

---

## 11. Testing

- **Unit** — the carrier's loop over in-memory streams: framing, blank lines, malformed JSON, unknown method,
  `initialize` ordering, notification push, EOF. Extends `AbstractMcpHandlerTest` like `mcp_wsHandlerTest`.
- **Invariant** — "stdout is pure JSON": boot with a printing boot file, call a tool that renders a widget,
  assert every line of fd 1 parses and that stderr still carries the diagnostics.
- **Invariant** — "everything listed is callable": for a server built from `tool => [!*eval, …]`, every `name`
  in the `tools/list` response must resolve on `tools/call`. This is the test that pins §5.3.1, and it fails
  today for any server whose keys were hand-written rather than derived.
- **End to end** — `printf` a scripted session into `bin/metatron --mcp` inside the build container
  (`bin/metatron-docker build shell`), then have a tool edit a scratch file and run a build.
- **Regression** — `mcp_mtronTest`, `mcpEmulatorTest`, `mcpHttpHandlerSseTest`, `mcpMessageServerTest` must keep
  passing; the carrier composes `mcpServer`, so the protocol layer should not change at all.

---

## 12. Phasing

1. **Tool pull-out + a usable server** — the tool instructions get registered, `mcp_server` accepts a `tool`
   collection, carrier + `StdioProtocol` + `--mcp` + `boot/mcp.boot.mtron` + unit and invariant tests.
2. **Faithful transport** — per-request dispatch, notification push from the outbox, cancellation, framed-input
   diagnostics.
3. **Config and convergence** — `as?mcp_stdio<=json` (with the literal reader), `resource_root`, shared
   route-value resolver, protocol-version echo, doc updates, workbench profile.

The pull-out moved into phase 1 because everything downstream is expressed in terms of it: a profile names its
tools (`tool => [!*eval, …]`), and so does every example in §5. Building the carrier against the *hardcoded*
tool set would mean writing the boot files twice.

---

## 13. Open questions

1. **Reset under stdio.** `System.exit(EXIT_RESET)` makes `bin/metatron` swap the JVM under a live pipe:
   in-flight requests hang, and a strict client kills a server that never re-answers `initialize`.
   *Recommendation:* off by default in stdio mode (log to stderr), opt in with a boot arg; if it is ever
   enabled, write a JSON-RPC error for the triggering request and flush *before* exiting.
2. **Flag vs wrapper.** `--mcp` on `bin/metatron` (matches the ask, one API) vs a thin `bin/metatron-mcp`
   wrapper that only sets the boot file (matches the `bin/drstynx` / `bin/agent-ide` convention). Both can
   coexist; the flag should be the contract.
3. **Which tool set ships by default.** The idiom is now `tool => [...]`, so this is one line of boot data:
   `[!*eval, !*read_memory, !*write_memory]` (safe, matches today's `/mcp`) vs a workbench set that also
   exposes `mfs:` writes, `/m/ide` and a guarded `bash?reject=['\bgit\b', …]`. *Recommendation:* the small set
   by default; ship the workbench as a second profile selected with `--mcp -b`.
4. **Where the pulled-out tools live** — this decides their names, so it should be chosen deliberately:
   eval-shaped ones belong with the language (`/m/inst/*` → `m_inst_eval`); the memory chain tools write to
   Router spaces, which argues for `/sys/inst/*` → `sys_inst_read_memory`. The alternative — leaving them in
   `mcpMetatronBuilder` and registering *that* namespace — keeps today's names but keeps the tools tied to a
   server, which is the coupling we are trying to remove.
5. **Does `eval_mtron`'s `native` flag survive?** `!*eval` returns the obj; `eval_mtron` can stringify it
   (`native => false`) and passes fails through. The carrier already converts a fail to a JSON-RPC error, so
   the question is only whether the stringify toggle is worth a second instruction.
6. **Where the carrier lives.** `isa/web/space/stdio/` keeps it beside its two siblings, but MCP is not really
   web. A future `isa/mcp/` consolidation would be the honest home for `mcpServer` + all three carriers — out
   of scope here, worth a note.
