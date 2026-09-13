# webSpace — exposing mtron objs through http/ws

**2026-09-11** · design note, no code written yet · companions: `docs/design/as-graph-checker.md`,
`docs/design/inst-resolution-design-insights.md`

## 0. The asks

1. **Mount any mtron obj to a route.** Mounting `/my/docs/#` should expose the objs matching that path over
   http like a classic webserver. MIME handling already exists.
2. **Any mtron obj should be able to be an mcp server.** Start with `InstSet`: `insts => tools`,
   `consts => resources`, `types => rest path to instances` (`http://localhost/person/1` for `person::T`).
3. Implied third: the ws/http-agnostic mounting that `mcpServer` already achieves should generalize — a thin
   handler per transport rather than a handler family per (protocol × transport).

The user's framing that drives everything below: **the `as` graph is the projection registry**, `as` takes a
**path** (`obj.as(rec::T => doc::T => inst::T)`, with `<...>` meaning "the graph supplies the legal middle"),
and **`httpSpace` / `wsSpace` may not be two things at all** — probably one `webSpace`.

---

## 0.5 Identity ledger — the design, consolidated

Read the note end to end and nearly every proposal turned out to be something that already exists. **That is the
initiative: naming, re-parenting, deleting and deciding — not building.** The ledger, grouped.

**Exposure**

| the draft wanted to build | it already is | consequence |
|---|---|---|
| a projection registry over (obj kind × protocol) | the `/m/inst/as` graph — `?dom<=rng` rows contributed by every type, with a scored single-read lookup | requirement 2 is mostly *writing rows* |
| an override / derive / degrade ladder | row specificity (`scoreSpecificity`: dom 1000, +500 exact base, +2000 rng match) | no dispatch code; the generic row is the last resort |
| "become a server" as a new operation | an `as`-call — the boot file already spells it `*dr.as(skill::T).as(mcp_server::T)` | `/drstynx => *dr.as(mcp::T)` |
| a protocol/carrier dispatch | two re-parentings — `mcp_server::T <: mcp::T`, `httprec::T <: web::T` | `test` then `as` replaces the class-sniffing ladder |
| a transport binder | the last hop of an `as`-path — `(mcp::T => ws::T).>>` is `ws::T` | a listener pops the head it serves |
| protocol negotiation logic | "which rngs does the graph emit for this dom" | existing boot files keep working |
| a `<...>` path search | the as-graph audit — `checkAsGraph`'s six violation classes *are* the unambiguity rules | the audit is the path resolver's proof obligation |
| a new language feature | repeated hops — `x.as(a => b => c) ≡ x.as(a).as(b).as(c)` | the design ships before the parser does |
| protocol types | `markdown::T` / `html::T` / `java::T` — "one str, many renderings", MIME-bridged | `java::T → rec::T` is live proof |
| the handler family | rows — `mcp_httpHandler` = `as?http<=mcp`, `mtron_wsHandler` = three verb rows, `web_httpHandler` = the default `web::T` body | six classes become rows plus two dumb carriers |
| two session recs and two IO records | one contract — both are `MRec`s of `on_*` verbs; `WebSocketRec.IO` and `HttpRec.HttpIO` are literally the same record | one `IO`, one session shape |
| two web spaces | one implementation, N carriers — same config parse, ladder, session cache, MIME pair | `webSpace`; the client half leaves for `http::client::T` |

**Data**

| the draft wanted to build | it already is | consequence |
|---|---|---|
| a web read | `Space.Helper.resolveRead` — direct read → branch nesting → ancestor-poly unroll | ON_GET is a weaker copy; ~120 lines → ~10 |
| an index / directory page | branch nesting — a `rec` of children at `uri(pattern)` | `DEFAULT_PAGE` survives only for `fsSpace`-style roots |
| mount wildcard capture | the space's own `#`/`+` plus `unrollPoly` | no capture machinery; `locateBasePoly` supersedes `locateBaseObj` |
| a path grammar for the web face | `DataPath` — `/db/collection/entry/field/extension…`, `#` cascade, role→`Type` schema map | mount targets are `DataPath` addresses |
| `type::T` → rest path to instances | `DataPath(collection = <type name>)` + `SchemaSpace.resolveCollectionSchema` | `person::T → /person/1`, no router scan |
| instance enumeration | `dp.entryIsWildcard()` → the native store read | `/person/#`; only non-schema spaces still need a convention |
| a query-parameter dialect | the traversal vocabulary + existing pushdown rewrites (`from` + `take` / `where` / `count` / `sum` … → SQL / Mongo / Gremlin) | the web face needs no query parser |
| a PATCH merge strategy | `DataPath.expandStructural` → per-leaf `resolveWrite` | per-field native writes, pushdown preserved |
| DELETE semantics | write `noobj` → `writeComplete` closes the value's streams | unlink *is* the resource-release hook |
| per-request content negotiation | `DataPath.typeOf(ROLE_FIELD)` + `MIME.MIMEType.fromType` | retires the extension-hint loop |
| "mount a space, get a webserver" | `AbstractDataPathSpace`'s flat/structured duality + migration | arbitrary paths already work; no web policy needed |
| a collection whose membership is a type predicate | `grphSpace`'s `V` / `E` meta-collections (`isRefinementOf(VRTX_TYPE)`) | exactly the shape of `as?web<=/m/type` |

**Catalog**

| the draft wanted to build | it already is | consequence |
|---|---|---|
| a universal exposure IR | a five-slot catalog appearing three times — `const/type/inst/rewrite` = `field/constructor/method` = `resource/collection/tool` (+ docs = prompt) | one row body, many surfaces |
| a database's type index | an `InstSet` — `SchemaSpace.schema()` returns `at(SCHEMA)` as one | a DB space *is* an InstSet plus data: `as?mcp<=space` ≡ `as?mcp<=instset` |
| an overload strategy for tools | one tool per base path, overloads resolved by the VM | corroborated live by the code index (`handleMessage` → a lst of three variants) |

**The one hole.** Every row above is an identity except one: *resolve-vs-reference at the transport boundary* is
genuinely unmade. Reads can carry auto pointers (Appendix A's probe returned `!@…` for a class rec) — correct for
a metatron client, opaque to a stock browser or MCP client. It needs a policy (§12.11, §13.14), and it is the only
item in this design that is a new decision rather than a rediscovery.

---

## 1. What exists today

### 1.1 The mount table

`route` is a config key on every space: `AbstractSpace.routes()` returns `at(route).jvmAs()`
(`AbstractSpace.java:151-153`), a `Map<Uri, Obj>`. Route keys are paths (`/mcp`, `/marko`, `/`); route
**values are objs** — a uri, a handler `Type`, or an `mcpServer` instance, or a code expression that evaluates
to one.

`drstynx.boot.mtron` shows two independent tables:

```
httpspace::[host => <http://localhost:8777>, ..., route => [/mcp     => mcp_mtron,
                                                           /docker  => docker:,
                                                           /dr      => /usr/dr,
                                                           /marko   => /usr/marko,
                                                           /message => mcp_message,
                                                           /        => mfs:docs/website/]]  @/sys/space/web/http
wsspace::[host => <ws://0.0.0.0:8555>,      ..., route => [/drstynx => *dr.as(skill::T).as(mcp_server::T),
                                                           /mcp     => mcp_mtron,
                                                           /message => mcp_message,
                                                           /mtron   => mtron_ws]]            @/sys/space/web/ws
```

`/mcp` and `/message` are duplicated across the two tables; `/drstynx` exists only on ws.

**The `as` projection is already there, spelled by hand, and only for mcp.** `/drstynx` is
`*dr.as(skill::T).as(mcp_server::T)` — an explicit conversion chain evaluated by the route walker
(`Space.Helper.resolveApply`, `Space.java:244-252`). This is the single most important observation in this
note: generalized exposure is not a new mechanism, it is naming and totalizing an operation already
performed inline in the boot file.

### 1.2 The two spaces, and the duplicated ladder

`httpSpace` (`httpSpace.java:127-172`) and `wsSpace.mWebSocketServer.createServer`
(`wsSpace.java:201-251`) each walk the route table and run the *same* three-lane dispatch by hand:

| lane | http | ws |
|---|---|---|
| resolve the RHS | `Space.Helper.resolveApply` + `Router.global().read` on a uri (`:139-141`) | same (`:213-215`) |
| materialize an `mcp_server` type | constructor sniff `specificType(...).test(MCP_SERVER_TYPE)` (`:143-148`) | same (`:216-222`) |
| handler `Type` | `createHandlerRoute(server, left, target.vid())` (`:149-152`) | `constructor().apply(config)` then `handler.self(...)` (`:233-239`) |
| `mcpServer` instance | wrap in `mcp_httpHandler` (`:153-156`) | wrap in `mcp_wsHandler` (`:231-232`) |
| uri (not a type) | assume web root, `createWebHandlerRoute` (`:157-161`) | not supported (throws `websocket handler type required`) |

Both spaces also duplicate: config parsing of host/pattern/route/q, a private session `memSpace` cache
(`httpSpace.java:129`, `wsSpace.java:124`), session keying, `close()`/shutdown-hook handling, and an
`(input, output)` MIME pair — `HttpRec.HttpIO` (`HttpRec.java:376-386`) and `WebSocketRec.IO`
(`WebSocketObj.java:47-53`) are the same record declared twice.

### 1.3 The session recs

`HttpRec` and `WebSocketRec` are both `MRec`s whose jvm keys are **protocol verbs**:

- `HttpRec`: `on_get`, `on_post`, `on_put`, `on_delete`, `on_patch`, `on_head`, `on_options`, `on_error`,
  plus defaults for `send`/`close` (`HttpRec.java:94-114`); `handle()` switches on the HTTP method
  (`:124-144`); each `do*` delegates to `dispatchToMtron(key, exchange)` (`:151-195`).
- `WebSocketRec`: `on_open`, `on_message`, `on_close`, `on_error`, plus `send`, `send_recv`, `close`
  (`WebSocketRec.java:49-85`); `WebSocketObj` declares the interface (`WebSocketObj.java:45-181`).

The substrate differs: `HttpRec` holds a `ThreadLocal<HttpExchange>` because handlers are cached and shared
across a thread pool (the comment at `HttpRec.java:72-78` documents cross-wiring bugs), while
`WebSocketRec` holds one `WebSocket` per session. Sessions are cached in each space's `memSpace` — http keys
by the `Mcp-Session-Id` header (`httpSpace.java:193-194`), ws keys by the socket attachment + an
`AtomicInteger` counter (`wsSpace.java:226`).

### 1.4 The handler family

| class | role |
|---|---|
| `mcpServer` | the one real protocol engine; transport-agnostic JSON-RPC dispatch (`mcpServer.java:119-158`) |
| `mcp_wsHandler` | composes an `mcpServer`; ws transport (`mcp_wsHandler.java:95-141`) |
| `mcp_httpHandler` | composes an `mcpServer`; streamable-http transport, incl. session-id (`mcp_httpHandler.java`) |
| `mtron_wsHandler` | 30 lines of verbs: `on_message => eval` (`mtron_wsHandler.java:70-110`) |
| `mtron_httpHandler` | http twin |
| `web_httpHandler` | 440 lines: the web-root GET policy + the write-verb algebra (`web_httpHandler.java`) |
| `mcp_emulator_wsHandler` / `mcp_emulator_httpHandler` | builder-produced tool/resource/prompt recs (`mcpEmulatorBuilder`) |

### 1.5 Materials that already exist (nothing here needs inventing)

- **MIME** — `MIME.MIMEType.of/fromType/fromExtension/toTid/serializer/fromBytes/toBytes`
  (`MIME.java:92-266`). Full content negotiation, both directions, tid↔media-type.
- **docq** — `docWrap` in every inst set; `mTool.mtronInstToDocs` reads `?docq` for descriptions
  (`mTool.java:206-219`). `find_inst` searches the family (`mcpMetatronBuilder.java:193-214`).
- **subq** — live change notification, already wired for MCP:
  `mcpServer.subscribeResource` writes a `?subq` sub whose code captures
  `notifications/resources/updated` into a per-server outbox (`mcpServer.java:403-465`).
- **Other qprocs** — `incrq` (append), `mintq` (server-side id minting), `constq`/`lockq` (immutability),
  `lineq` (line ranges), `embedq` (semantic search), `typeq`/`?T` (the declared type at a vid),
  `mimeq` (`QCollection.java`).
- **The `as` graph** — see §4.
- **The catalog shape** — see §7.

---

## 2. Findings (the seams)

Each finding is a reason the current shape resists both asks.

1. **The projection is hand-written in the boot file and mcp-only.** `/drstynx => *dr.as(skill::T).as(mcp_server::T)`.
   There is no `protocol` axis; "become a server" is spelled per-route, and only one protocol has a target
   type to spell it with.

2. **The route table is duplicated and protocol-local** (`drstynx.boot.mtron:81-86` vs `:152-155`), and so is
   the dispatch ladder (`httpSpace.java:131-162` vs `wsSpace.java:201-251`). Every mounting feature must be
   built twice.

3. **The route RHS is "handler-shaped".** The lane ladder sniffs `isUri` / `isType` / `instanceof mcpServer`
   and falls back to "assume web root" (`httpSpace.java:139-161`). It is a `switch` on Java class pretending
   to be a dispatch — and it is where the `rec() -> construct -> big()` workarounds and the
   "same root cause as the wsSpace ClassCastException fix" comments come from.

4. **`createContext(path.toString(), ...)` cannot express `/my/docs/#`** (`httpSpace.java:192`). JDK context
   paths match by *literal* prefix, so a wildcard route key registers a path that never matches;
   `web_httpHandler` then derives the mount-relative path from `exchange.getHttpContext().getPath()`
   (`web_httpHandler.java:100-108`, `:405-415`). Ask #1 is a genuine platform gap, not just a missing
   feature.

5. **Session identity is three mechanisms.** http: `Mcp-Session-Id` header → composite `sessionVid` → handler
   cached in a private `memSpace` (`httpSpace.java:193-218`). ws: socket attachment + counter
   (`wsSpace.java:226`). mcp-http: a `sessions` `ConcurrentHashMap` **plus** a mutable `sessionId` field on a
   handler instance the space shares across requests (`mcp_httpHandler.java:83-84`, `:120-129`) — the same bug
   class `HttpRec`'s `ThreadLocal` was added to fix. `initialize` also mints a fresh id even when the client
   sent one, and `sessions` only shrinks on `DELETE` (`:186-188`). `doGet` is a 501 SSE stub (`:158-162`).

6. **Protocol is chosen by mount point, never negotiated.** `/mcp` is mcp because `mcp_mtron` is the value.
   `Accept:`, the JSON-RPC body, and the ws subprotocol are unused.

7. **Handler constructors run at type-check time**, so every mtron verb body begins with
   `if (this.exchange() == null) return noobj();` (`web_httpHandler.java:86`, `:265-268`, `:333-336`,
   `:375-377`). That guard repeated six times is the tax for describing a protocol surface with Java methods
   and mtron insts in one object.

8. **Resources are snapshots although consts are live addresses.** `mcpMetatronBuilder.build` copies
   `.metatron/skills/mtron` into `text`/`reference` strings at construction time (`:248-272`, threshold
   `:71`), and `mcpServer.handleResourcesRead` reads those fields (`:290-303`). A const's vid is a real
   address with `?docq` attached and `?subq` subscribable — the liveness is available and unused.

9. **InstSet tools will collide.** `INST_TABLE` is `Map<fURI basePath, Set<Inst>>` — deliberately, for
   overloads (`AbstractInstSet.java:50`, `:130`) — while `mTool.toolName(tid)` flattens the tid
   (`mTool.java:134-136`), so `plus?int<=int` and `plus?real<=real` both become `m_math_plus`. MCP requires
   unique names.

10. **`httpSpace` is both the web server and the `http://` client address space, and the server half is
    reading through the client half.** `directReader` step 1 ("try the local route table first") reads
    `this.cache.directReader()` (`httpSpace.java:294-298`) — the **session cache**, which holds handler
    sessions keyed `/sys/space/web/http/<route>/<session>`; a mounted target at `/usr/dr/foo` is never there,
    so the branch never hits and every local read falls through to Jsoup (`:307-349`) — a real HTTP round trip
    to itself. Meanwhile the client half is already designed as a *type*: `HTTP_CLIENT_TYPE` exists and throws
    `"http client not implemented"` (`:118-124`), and `WebSocketRecClient` already exists on the ws side.

---

## 3. The reframe

**Exposure is conversion. The protocol is an `rng`. The mount is an `as`-call.**

- mount → projection → carrier, with **no new registry**:
  `route: <pattern> ⇒ <obj>.as(<path to a protocol rng>)`
- the mount config (`web_root`, `default_page`, `read_only`) is the **as-row's argument**, validated by
  `ScoringInstResolver.checkArgs` against the row's `lst(T(...))` spec and documented by `docWrap`'s map —
  not a new `route::T` config rec.
- the transport is the **last hop of the path**: `(mcp::T => ws::T).>>` is `ws::T`, i.e. "here is the surface,
  here is the rest of the plan". A listener receives a path, pops the head it serves, and binds the tail.

This is the smallest version of the proposal that satisfies both asks, because the override/derive/degrade
ladder — the part I originally wanted to write as code — is **row specificity** in a table that already
exists and is already scored.

### 3.1 Protocols are types — consistent with markdown/html/java

The user's instinct is right, and `java::T` proves it: the live code index converts Java source into `rec::T`
through an `as` projection (§7). Three families, distinguished by the base type, which is also what makes the
graph's audit meaningful for each:

| family | base | members | nature |
|---|---|---|---|
| **encoding** | `str::T` | markdown, html, xml, yaml, java, xsv/csv, css, json | how a `str` is structured; MIME-bridged (`toTid`/`fromType`); round-trippable |
| **surface** | `rec::T` | `mcp::T` (tool/resource/prompt), `web::T` (`on_get`…`on_put`), `mtron::T` (`on_message` ⇒ eval) | one obj, many faces |
| **carrier** | socket-backed | `http::T` (`HTTP_SOCKET_TYPE`, `/m/web/http/http_socket`), `ws::T` (`WS_WEBSOCKET_TYPE`, `/m/web/ws/web_socket`), `stdio` | how bytes arrive |

Re-parenting is the whole implementation of two of the three: `mcp_server::T <: mcp::T` and
`httprec::T <: web::T`. After that the transport asks `target.test(mcp::T)` and then `as` — the constructor
sniffing in both spaces (finding 3) disappears, and `implicitAsGraph()` supplies `as?mcp<=mcp_server` for free.

**Hypothesis to verify (do not build on it yet):** the audit's violation classes appear to *classify* these
families. `COUPLING`/`ISOCHAIN` mean a reverse path exists (candidate isomorphism) — what an *encoding*
protocol should look like (`html ⇄ markdown` via `HTMLMarkdownSerializer`). `RETRACT` means the round trip is
an idempotent — a lossy *view*, which is what a *surface* projection is (`rec ⇒ mcp ⇒ rec` cannot carry keys
the surface has no slot for). Calibration pairs already in the graph: `html ⇄ markdown`, `rec ⇄ web_json`,
`mcp_client ⇄ web_json` (`webInstSet.java:482-534`).

---

## 4. The `as` graph is the registry

`AS_INST_TID = /m/inst/as` (`mInstSet.java:154`); `mFluent.as_(obj)` is
`instB(AS_INST_TID, lst(obj))` (`mFluent.java:328-330`). Every type contributes rows keyed `?dom<=rng`:

| capability the design needs | already provided |
|---|---|
| dispatch keyed by (what you have × what you want) | the family itself — ~70 rows; `Str`/`Int`/`Bool`/`Real`/`Uri`/`Lst`/`Rec`/`Rel`/`Code`/`Objs` plus `web`/`llm`/`ide`/`math`/`mach`/`ui` |
| lookup in one call | `ScoringInstResolver` fast path: `Router.readFromSpace(as?dom=specificTypeId(lhs).rng=specificTypeId(arg))` (`:89-97`) |
| specificity resolution | `scoreSpecificity` — dom 1000 (+500 exact basePath), args 500 (+250), **+2000 when the rng matches the requested type** (`:242-299`) |
| wildcards / generics | dom and rng are `fURI` patterns — `ALL.maybeSome()`, `TID.some()`, `TID.maybe()`; per-instruction `A`/`B` generics |
| refinement-aware matching | `Inst.instDomRngMatch` and `refines` = `testNominally && c().within()` (`Inst.java:445-451`, `:971-973`) |
| typed + documented config | `checkArgs` against `lst(T(...))` arg specs (`:110-150`); `docWrap`; `find_inst` |
| lattice audit | `Inst.Helper.checkAsGraph()` — `DUPLICATE`, `AMBIGUOUS`, `INCOMPARABLE`, `COUPLING`, `ISOCHAIN`, `RETRACT` (`Inst.java:775-925`) |
| closure under subtyping | `implicitAsGraph()` manifests `as?src<=dst` for every refinement pair already present (`:940-963`) |
| extension from mtron | rows are declared in an inst set's `inst` table, i.e. writable in a boot file |

Rows that already do the relevant work: `as?tool<=/m/inst` (`llmInstSet.java:716`), `as?tool<=docs`
(`:708`), `as?mcp_server<=skill` (`webInstSet.java:535`), `as?mcp_client<=json` / `as?json<=mcp_client`
(`:482-534`), `as?skill<=markdown`/`uri`/`agent`/`project`.

**Consequence for the design:** requirement 2 is mostly "write rows", and requirement 3 is "re-parent two
types". That is the cheapest possible shape for a refactor of this size.

---

## 5. The path form (`=>`, `>>`, `<...>`)

Planned realization (user's note): the `as` argument is nested relations that pop off with `>>`, and `<...>`
refers to the as-graph for the legal path:

```
obj.as(rec::T => doc::T => inst::T)      -- explicit path
obj.as(rec::T => <...> => inst::T)       -- graph supplies the middle
a => b => c        ==> a => (b => c)
a => b => c.>>     ==> b => c
a => b => c.>>.>>  ==> c
```

### 5.1 What is live today (probed against the running VM)

| piece | state | evidence |
|---|---|---|
| relation nesting | **live** | `a => b => c` → `["<a>",["<b>","<c>"]]` i.e. `a => (b => c)` |
| left pop | **live** | `a => b => c.>>.>>` → `"<c>"` |
| type as a path operand | **not live** | `rec::T => doc::T => inst::T` → parse error, line 1 col 7 (`could not parse at ' '`) |
| `<...>` token | **not live** | no token |
| single-hop `as` lookup by (dom, rng) | **live** | `ScoringInstResolver.java:89-97` |
| `as` specificity scoring | **live** | `+2000` on rng alignment (`:279-286`) |
| subtype closure of the graph | **live** | `implicitAsGraph` (`Inst.java:940-963`) |
| as-graph audit | **live but ungated** | `AsGraphTest.java:45-59` logs everything except `INCOMPARABLE` |
| `as()` accepting a relation path | **not live** | the argument form above |

Consistent with "the AsGraph work is not integrated into the main body of code yet".

### 5.2 Integration checklist, in dependency order

1. **parser** — allow a *type*-valued left operand in path position (`T => T`), and add a `<...>` token.
   (Today `rec::T =>` fails at parse, so this is the first blocker.)
2. **resolver** — interpret a rel-valued `as` argument as a recursive plan: hop to the head type (existing
   single-hop path), then recurse on the tail. This makes the path form **sugar over repeated hops**, so
   `x.as(a => b => c) ≡ x.as(a).as(b).as(c)`. Nothing else in `as` changes.
3. **`<...>` search** — path-find over `Inst.Helper.asInsts()` between two type nodes, using the six
   violation classes as validity rules (a `DUPLICATE` or `AMBIGUOUS` hop makes the path non-unique; a
   `RETRACT` hop means the path is a view, not an isomorphism — which may be exactly what a surface
   conversion is).
4. **audit gate** — run `checkAsGraph` over protocol rngs and assert zero `DUPLICATE`/`AMBIGUOUS`; harden
   `AsGraphTest` from "log" to "fail" for those two classes only, tolerating the pre-existing
   `INCOMPARABLE` noise.

### 5.3 De-risking consequence

Because paths are sugar over hops, **the design can land before paths exist**: mounts written as explicit
chains (`*dr.as(skill::T).as(mcp_server::T)`, or later `*dr.as(mcp::T)`) work today, and `<...>` becomes
elision once step 3 lands. Do not block the mount/projection work on the parser.

---

## 6. The audit is the path-uniqueness proof

The user's six violation types are the crisp version of the concern the projection lattice raises. Restated
as path obligations:

- `DUPLICATE` — two rows for the same hop; the fast path returns `getFirst()` with no arg check and no
  scoring (`ScoringInstResolver.java:89-97`), so this is a **correctness** precondition, not hygiene.
- `AMBIGUOUS` — two same-rng doms that overlap with no most-specific winner: the path is not unique. This is
  exactly the "when the path is unambiguous" case in the user's note.
- `INCOMPARABLE` — disjoint, so dispatch stays total. Benign.
- `COUPLING` / `ISOCHAIN` / `RETRACT` — reversibility at three strengths; see the §3.1 hypothesis.

Discipline for new protocol/carrier rows: **one row per (kind, protocol)**; variants go in via the **argument**
(config), not by adding near-duplicate rows; and a specific dom should be a **nominal refinement** of the
generic dom, or `checkAsGraph` reports `AMBIGUOUS`/`INCOMPARABLE` for the same-rng pair.

---

## 7. The catalog is the universal IR

The strongest corroboration of the "re-key a catalog" reading is that the same four-to-five slot shape appears
independently at three levels — including one that is live in the running VM:

| catalog slot | `InstSet` | code index (live) | MCP face |
|---|---|---|---|
| payloaded values | `const` | `field` | `resource` |
| types | `type` | `constructor` | collection / `resourceTemplate` |
| behavior | `inst` | `method` — key maps to one vid, or a lst of signature variants | `tool` |
| rewrites | `rewrite` | not represented | internal — never exposed |
| documentation | `docq` / `docWrap` | `comment` | `prompt` and description |

Live probe: `/usr/marko/metatron/src/mcpServer()` returns a rec keyed
`field` / `comment` / `constructor` / `method`, where non-overloaded methods point at a single vid
(`"of": "!@.../members/6/of"`) and `handleMessage` is a **lst of three method recs**, each carrying
`signature` / `header` / `body` / `text`. `*/usr/marko/metatron/idx/mcpServer/method/handleMessage` returns
that three-element lst directly.

That single probe settles two design questions:

- **overloads** — the code index already represents them the way `INST_TABLE` does (one name → variants with
  signatures), so the mcp projection should do the same: **one tool per instruction base path**, with
  overload selection left to the VM's own resolution (`checkArgs` + `scoreSpecificity`), not encoded in the
  tool name. This supersedes the earlier "suffix by dom/rng" idea.
- **the protocol-type question** — `java::T → rec::T` is a live `as` projection from a *protocol type* to a
  structural rec. A protocol type carrying a structural projection is therefore already demonstrated.

---

## 8. Design

### 8.0 The back-space substrate: `DataPath` + `SchemaSpace`

Read after the first draft; it changes §8.1, §8.3 and §8.4. **The addressing half of requirement 2 already
exists, uniformly, across every database-backed space** — so the web layer must not invent a path grammar.

**`DataPath`** (`furi/DataPath.java`) is a structural decomposition of a uri into
`/db/collection/entry/field/extension…`, where `#` cascades to all descendants and `+` applies only to its own
segment, plus a **parallel schema map**: `typeOf(ROLE_DB | ROLE_COLLECTION | ROLE_ENTRY | ROLE_FIELD)` returns
the metatron `Type` each segment resolved to (annotated by the space's `resolveDataPath`), `type(role, type)`
sets it, and equality deliberately ignores it (the schema is an annotation, not address identity). Also useful:
`of(furi)` / `withoutDB(furi)`, `spaceURI()` (container reconstruction), `vid(spacePattern)` (fully-qualified
vid from a space pattern, db excluded), `fieldPathStr()` (dot notation), `navigateWithin(stream, extension,
detached)` (type-dispatched descent over Space / Poly / Rec / Lst / Uri / Rel), and
`expandStructural(base, rec | lst)` (decompose a structural delta into leaf `(uri, value)` operations,
unwrapping `+[rec]` into per-field sets).

**`AbstractDataPathSpace`** (`isa/AbstractDataPathSpace.java`) is their shared contract: seven hooks —
`isReservedFlatName`, `isStructuredCollection`, `writeFlat`, `readFlat`, `writeStructured`, `scanFlat`,
`removeFlat` — plus the shared routing rule (`isFlatRead` / `isFlatWrite`: a reserved first segment, or an
unknown collection; a 1-segment path is always a collection root, never a flat key), `migrateFlatToStructured`
(a Rec write creates the collection and promotes parked flat entries, keeping schema-violating entries for
manual repair), and an `enforceRootConstraint` override so flat writes escape the document/row Rec requirement.

**`SchemaSpace`** (`isa/SchemaSpace.java`), implemented by `tbleSpace`, `dcmntSpace` and `grphSpace`, is the
piece that matters most:

- `schema()` returns `at(SCHEMA)` **as an `InstSet`** — a database's schema *is an inst set*.
- `resolveCollectionSchema(collectionName)` resolves a collection segment to the matching `Type` by the last
  segment of each schema type's vid (all types for `#` / `+`), documented as "the single, shared entry point for
  collection-level type resolution across all `SchemaSpace` implementations — table spaces, document spaces,
  graph spaces, and future database-backed spaces". When no schema is wired, a collection deref falls back to a
  `COLLECTION_TID` uri.

Consequences — each one replaces something the draft proposed to build:

1. **Collection roots already resolve to types.** `GET /person` on any back space yields `person::T` from its
   schema. "Types → REST path to instances" therefore needs **no new addressing scheme and no router scan**: it
   is `DataPath(collection = <type name>)` with the schema InstSet as the index.
2. **Enumeration is not a gap for schema spaces.** `dp.entryIsWildcard()` (`/person/#`) enumerates through the
   native store (`collection.find()`, table read, `g.V()`). The draft's exact-read-then-pattern-scan concern
   applies only to types living in *non*-schema spaces (`memSpace`, `fsSpace`).
3. **A `SchemaSpace` is an InstSet plus data**, so `as?mcp<=instset` and `as?mcp<=space` share one row body
   (`types ⇒ collections / resources`, its `insts ⇒ tools`). One correction to §8.3: for a DB space the
   `rewrite` slot is **not** merely internal — the pushdown rewrites *are* the query optimizer.
4. **The type annotations replace the web layer's content negotiation.** `typeOf(ROLE_FIELD)` + `fromType` gives
   the response MIME; `typeOf(ROLE_COLLECTION)` gives write validation. That retires `web_httpHandler`'s
   extension-hint walk-up loop and its `isNoobjOrDir` heuristic.
5. **Query parameters should be the traversal vocabulary, not a REST filter dialect.** `CommonRewrites` builds
   pushdown rewrites with `RewriteBuilder.forDatabase(spaceType)` matching native insts — `from` plus `take`,
   `skip`, `order`, `count`, `where`, `sum`, `mean`, `prod`, `dedup`, `at`, `rshift` — and optimizes them to
   SQL / Mongo / Gremlin. So `/person/#?take=10&where=…` should compile to `from(person).where(…).take(10)`,
   which gives the web face filtering, paging, sorting and aggregation that is **already portable across all
   three backends**, instead of a bespoke query-param language per store.
6. **Writes should be `expandStructural`, not a whole-document merge.** The spaces accept field-level operations
   (`writeField`, `writeRecDecomposed`) and `expandStructural` produces exactly those leaves.
   `web_httpHandler`'s whole-doc `>>=` read-modify-write is coarser and loses the pushdown.
7. **Sub-path reads are `navigateWithin`**, and the two mount-mapping directions are `spaceURI()` and
   `vid(pattern)`. `locateBaseObj` + `at(subPath)` is a coarser approximation of the same walk.
8. **"Collection" can be a type category.** `grphSpace` treats `V` / `E` as meta-collections by filtering schema
   types on `isRefinementOf(VRTX_TYPE | EDGE_TYPE)` — precedent for a collection root whose membership is a
   *type predicate* rather than a physical container, which is exactly the shape of `as?web<=/m/type`.

**The core data algorithm: `resolveRead` / `unrollPoly` / `resolveWrite`** — read last, and it is the
capstone: it means **the web layer is not a data layer**. `Space.Helper.resolveRead` takes only
`(space, pattern, directReader)` — it is space-agnostic, and `AbstractSpace.read` already wraps it in the qproc
pre/post read. Its three tiers:

1. **direct read** — `directReader.apply(pattern)`, the space's native lookup.
2. **branch nesting / child fallback** — when nothing came back: for a *branch* pattern (`…/`, no wildcard) it
   reads `pattern +/+` and materializes a `rec` of the children keyed by their relative path (integer-named
   children stay separate listings instead of nesting — `CommonUtil.isInt`); otherwise it reads the *branch
   form* of the node. That `rec` at `uri(pattern)` **is the collection/directory index**, produced by the
   space, not by any web policy.
3. **ancestor-poly unroll** — when still empty (or the pattern contains `#`), `locateBasePoly` walks *up* the
   path for a stored `Poly` and `unrollPoly(base.furi(), poly, pattern)` satisfies the pattern against the
   nested structure, recursing into nested polys and returning concrete `(uri, value)` pairs. This is why
   metatron never needs deep keys materialized: a value stored at an ancestor answers any descendant pattern.

The result shape is already the REST shape: a **node** pattern yields `objs` of values (a document); a
**branch** yields `objs` of `rel(uri, obj)` (a uri→value collection). `readStream` guarantees concrete,
wildcard-free fURIs for every element.

`resolveWrite` is the mirror with the same three-tier logic: write directly when the target exists or no
ancestor poly exists; **fan out per key/index** for a branch write of a poly value (`vid.extend(key)`); or,
when an ancestor poly exists, **mutate that poly and write it back at the base** — storage-granularity
read-modify-write. `writeStream(pattern, obj)` expands a wildcard target (via `readStream`) and writes each.
`writeComplete` closes the replaced value's streams when the new value is `noobj` — which is why *unlink =
write noobj* is the idiom: it is the resource-release hook, not a tombstone.

Consequences:

1. **The web read is one call.** `web_httpHandler`'s ON_GET body is a weaker hand-rolled copy of those three
   tiers — a direct read (step 1), a `DEFAULT_PAGE` + `isNoobjOrDir` approximation of branch nesting (step 2),
   and `locateBaseObj` + `at(subPath)` + the extension-hint loop in place of ancestor-poly unroll (step 3).
   Replace it with one read plus a serializer: ~120 lines become ~10, and behaviors the handler cannot express
   today (pattern unrolling into a stored poly, automatic index nesting) arrive for free.
2. **The index page is already specified.** §8.2's "auto index page" *is* branch nesting; no `index.html`
   fallback policy is needed for spaces that nest — `DEFAULT_PAGE` survives only for `fsSpace`-style roots.
3. **Mount capture is the same vocabulary.** A mount tail passed through as a pattern (`/my/docs/#` → the
   space's `#`) is satisfied by the same `#`/`+` matching and unrolling — no new capture machinery, and
   `locateBasePoly` (poly semantics) supersedes `locateBaseObj` (first non-noobj ancestor).
4. **PATCH should expand, not merge.** A structural delta → `DataPath.expandStructural` → per-leaf
   `resolveWrite` uses the ancestor-poly path and the DB spaces' per-field native writes; today's
   whole-document `update_(delta).apply(base)` reads, merges in memory and rewrites, losing the pushdown.
   `PUT` is already correct (`writeToSpace` → `resolveWrite`), and `DELETE` must keep going through `noobj` so
   `writeComplete` can close.
5. **Resolve-vs-reference is a transport decision, and it is currently unmade.** The node path calls
   `autoResolve`, but values can remain auto pointers: this session's own probe returned
   `"!@/usr/marko/metatron/code/1/classes/mcpServer/0/members/0/LOG"` for a five-field class rec. That is
   correct for a metatron client and useless to a stock browser or MCP client. `autoResolve`,
   `recursivelyStripVID` and `navigateWithin(…, detached)` are the existing hooks; every projection needs a
   stated policy, and probably a per-request override, because it changes both payload size and whether the
   response is readable outside metatron.
6. **The division of labor, finally stated** — the web layer is *only* transport: parse the request into
   (pattern, method, body, Accept) → call the space → serialize under the resolve policy → choose the status
   code. Every data behavior this design needs already lives in `Space.Helper`.

**Phase impact:** P0's write verbs should use `DataPath` + `expandStructural`; P1's mounts should express
targets as `DataPath`-shaped addresses and delete `web_httpHandler`'s string surgery; P3's instset row body
covers the DB spaces at no extra cost.

---

### 8.1 Mounts — one table, patterns with capture, two flavors

Keep `route`. One table, shared by all carriers (transports filter by `transport` if declared). Two flavors in
one table:

- **rewrite mount** — `pattern ⇒ pattern`, e.g. `[/my/docs/# ⇒ <mfs:docs/website/#>]`. Wildcards on both sides
  capture and substitute; this generalizes `Space.Helper.routeFromSpace`'s prefix rewrite
  (`Space.java:224-242`) from "strip prefix" to "capture", and subsumes `fsspace`'s `route: [root: => /usr/bin]`.
- **obj mount** — `pattern ⇒ <obj>.as(<path to protocol>)`, e.g. `[/dr/+ ⇒ *dr.as(mcp::T)]`.

A rewrite mount is an obj mount whose target is a space and whose protocol is `web`, so one implementation
covers both. Captures (`+` = one segment, `#` = rest) should be bound into the request rec as named path vars,
the way `inst.args()` binds instruction args — after which `directReader`'s "walk up until something matches"
fallback becomes the slow path rather than the only path.

Platform pragmatics: register the JDK context at the route key's **static prefix** (retract at the first
wildcard segment) and do full pattern matching + capture inside. That keeps JDK longest-prefix priority and
fixes finding 4 with low risk. A single `/` context doing all dispatch is the general end state, needed only
for mounts like `/#/docs/+`.

Mount targets should be expressed in `DataPath` terms (§8.0): `spaceURI()` and `vid(pattern)` give both
mapping directions, and the request path decomposes into collection / entry / field rather than being
string-appended to a web root.

Protocol resolution: declared at the mount when it matters; otherwise negotiated per request (Accept,
JSON-RPC body, ws subprotocol) with a **registry-driven fallback** — if exactly one protocol accepts the
obj's kind (the set of rngs emitted from its dom in the as-graph), use it. `mcp_mtron` → mcp only;
`mfs:docs/website/` → web only; `*dr` → both, negotiate. That replaces the class-sniffing ladder with a
principled rule and keeps existing boot files working.

### 8.2 Projections — structural, with declared overrides

Rows, not code. The ladder becomes row specificity: `dom(instset).rng(mcp::T)` outscores `dom(A).rng(mcp::T)`
by the 1000-point dom term.

| target kind | `mcp::T` | `web::T` |
|---|---|---|
| rec declaring `tool`/`resource`/`prompt` | used verbatim | declared `on_*` if present, else one document |
| any rec with inst-valued keys | inst keys ⇒ tools, other keys ⇒ resources | the rec as one document; `?mimeq` picks the rendering |
| `instset::T` | insts ⇒ tools, consts ⇒ live resources, types ⇒ collections | `/<name>` per tid/vid via Router redirects; `?docq` = docs page |
| `type::T` | one resource per instance + its insts as tools | `/person/#` collection; `PUT` validated by `test`/ctor |
| `space::T` | its pattern/routes ⇒ resources | its pattern as a document root + the read/replace/update/unlink algebra; the index is branch nesting (§8.0) |
| `skill::T` | already works | its references as a doc tree |
| `inst::T` | one tool (`as?tool<=/m/inst`) | `/<name>` ⇒ the inst |
| `lst::T` / `objs::T` | one resource per element | auto index page — branch nesting, §8.0 |
| **generic fallback** | a trivial server: no tools, one live resource at the obj's vid | the obj as one document |

Reserve **one generic row per protocol** as the last resort so "any obj can be an mcp server" is a rule rather
than an error. Rows to add, versus rows that already exist:

| row | state |
|---|---|
| `as?tool<=/m/inst`, `as?tool<=docs`, `as?mcp_server<=skill`, `as?mcp_client<=json`, `as?json<=mcp_client` | exist |
| `protocol::T` with `web::T` / `mcp::T` / `mtron::T` / `stream::T` | new (types only, no logic) |
| `mcp_server::T <: mcp::T`, `httprec::T <: web::T` | new (re-parenting) |
| `as?mcp<=instset` | new — requirement 2 |
| `as?web<=/m/type` | new — requirement 2 (REST paths) |
| `as?web<=space`, `as?mcp<=space` | new — absorbs `web_httpHandler`'s mount policy |
| `as?mcp<=A`, `as?web<=A` | new — generic last resort |
| `as?http<=mcp`, `as?ws<=mcp`, `as?http<=web`, … | new — the carrier bindings (today's `mcp_httpHandler` / `mcp_wsHandler`) |

### 8.3 InstSet → mcp

One row, `as?mcp<=instset()`, whose body walks the catalog with conversions that already exist:

- **insts ⇒ tools** — `inst.as(tool::T)` (exists, `llmInstSet.java:716`), descriptions from `?docq`; one tool
  per base path, overloads resolved by the VM (§7). `mcpServer.handleToolsList` currently calls
  `mTool.mtronInstToolSpecification` directly (`mcpServer.java:214-229`), bypassing the graph — route it
  through the row so users can override inst⇒tool for their own kinds.
- **consts ⇒ resources** — keyed by vid, **live**: `Router.read` + `?mimeq` for the representation, `?docq`
  for the description, `?subq` for `resources/subscribe` (outbox machinery already written,
  `mcpServer.java:435-465`). Replaces the snapshot approach (finding 8).
- **types ⇒ collections** — `as?web<=/m/type` (§8.4); in the mcp face, one resource per instance plus the
  type's insts as tools.
- **rewrites ⇒ internal** — `dom` is `code::T`; excluded by construction, stated so the rule covers all four
  tables.
- **prompts** — from `docq`; `as?tool<=docs` exists. No new declaration.
- **a DB space is already an InstSet plus data** (§8.0), so this row body covers `tbleSpace` / `dcmntSpace` /
  `grphSpace` unchanged — and the `rewrite` bullet above becomes *the* query-optimizer hook rather than a
  hidden corner.

### 8.4 Types as REST paths

For `person::T`:

- **path root = the type's vid minus `::T`**: `person::T → /person`. No registration needed; the type's vid is
  its address space.
- `GET /person` → the collection; `GET /person/1` → exact `Router.read(/person/1)` first, then a pattern read
  with a `tid().test(type)` filter for the scattered case (`BasicRouter.read` returns `T(pattern)` for a
  generic vid, `BasicRouter.java:317-318`).
- `PUT /person/1` → validate with `obj.test(person::T)` and normalize via the type's predicate/constructor:
  **the type becomes the REST write validator and constructor**, which is the strongest argument for
  type-addressed mounts.
- `PATCH` → the `>>=` update algebra; `DELETE` → write `noobj`. All three already exist in
  `web_httpHandler.writeValue/updateValue/deleteValue` (`:259-389`) — **hoist them into `web::T`** so every
  mount inherits read/replace/update/unlink instead of only the web-root mount, and the `read_only` flag
  becomes the `constq`/`lockq` gate rather than a hand-rolled boolean (`:394-400`).
- representation via `?mimeq` / extension / `fromType`; docs via `?docq`.
- **Enumeration is solved for schema spaces** (§8.0): `resolveCollectionSchema` turns the collection segment
  into the type, and `/person/#` enumerates through the native store. The gap the first draft flagged applies
  only to types whose instances live in *non*-schema spaces (`memSpace`, `fsSpace`), where a convention or a
  declared index is still needed. `/person` itself should deref to the schema type (as back spaces already
  do), with `/person/#` for the instances.

### 8.5 Carriers as the last hop; two dumb sessions; one IO

- Collapse the handler family to **one `http` session and one `ws` session** that know nothing about mcp,
  mtron, emulation, or web roots: wire → request rec (`method`, `uri`, `headers`, `body`, negotiated
  in/out MIME, mount captures) → `surface.apply(request)` → serialize. `mcp_wsHandler`, `mcp_httpHandler`,
  `mtron_wsHandler`, `mtron_httpHandler`, `web_httpHandler` and the emulators become **rows or protocol
  configurations**, not transport variants.
- Unify `WebSocketRec.IO` and `HttpRec.HttpIO` into one record (they are identical).
- Keep `send` / `close` / `on_*` as the verb names, so nothing in mtron-land changes.
- Move the `EXCHANGE` `ThreadLocal` **inside the carrier** — request-scoped session state is the carrier's
  business; once the carrier owns the session key, `mcp_httpHandler`'s `sessionId` field is structurally
  impossible rather than fixed by hand.
- The `exchange() == null` guards disappear once a protocol surface is only instantiated by a request, never
  by `isaPredicate` evaluation (finding 7).

### 8.6 `webSpace` — one implementation, N carriers (plus a split)

They are not two things. Both are `AbstractSpace<Server>`, both parse host/pattern/route/q, both walk the route
table with a hand-written ladder, both wrap the server callback surface in an `MRec` of verbs, both cache
sessions in a `memSpace` keyed by a carrier-supplied value, and both declare an `(input, output)` MIME pair.

**But `httpSpace` holds two jobs, and that is the thing to split** (finding 10): it is the web *server* space
and the `http://` *client* address space. The client half should move out into the client types that already
exist as stubs/types — `HTTP_CLIENT_TYPE` (`httpSpace.java:118-124`, currently `throw "http client not
implemented"`) and `WS_CLIENT_TYPE` / `WebSocketRecClient`.

| concern | owner after the merge |
|---|---|
| the mount table | `webSpace` — one table for both carriers (kills the drstynx duplication) |
| route matching + wildcard capture | `webSpace` |
| mount config (`web_root`, `read_only`, `default_page`) | the as-row argument, not the space |
| session cache and its keying | `webSpace`; the key is supplied by the carrier |
| as-path evaluation, protocol selection | `webSpace` |
| accept, frames, connection lifecycle | the carrier (`http` / `ws`) |
| wire → request rec, MIME in/out | the carrier |
| mcp session-id, SSE, `DELETE` teardown | the `mcp::T => http::T` row, not the space |
| remote fetch (`*<http://x>`, Jsoup, POST) | out of the space, into `http::client::T` / `ws::client::T` |

Practical shape: **one class, one mount table, N carriers.** Not one Router space — `pattern()` is singular,
and `*<http://x>` and `*<ws://x>` legitimately are different address spaces — so keep two registrations but
one implementation with a `carrier` config key
(`webspace::[carrier => ws, host => <ws://0.0.0.0:8555>, route => [...]]`).

---

## 9. Cross-protocol affordances: qprocs are the shared capability layer

| qproc | over http / web | over mcp |
|---|---|---|
| `docq` | `?docq` ⇒ the docs page for a vid | tool/resource description (already read by `mTool.mtronInstToDocs`) |
| `mimeq` | `Accept` / `Content-Type` negotiation, per request | `resources/read` mimeType (today guessed from the extension) |
| `subq` | subscribe, then push (SSE or a ws frame) | `resources/subscribe` — outbox code already exists |
| `incrq` | `POST` to a collection address | an append tool / growing resource |
| `constq` / `lockq` | the read-only gate (today hardcoded, `web_httpHandler.java:394-400`) | the same gate, refusing the write tool |
| `lineq` | range requests, partial line PATCH | a line-range edit tool |
| `embedq` | a semantic search endpoint | a search tool |
| `typeq` (`?T`) | the declared type at a vid, for collection write validation | the resource schema (`outputSchema`) |

Theses: http and ws are two renderings of the same capability layer, not two subsystems.

---

## 10. What dies

- the three-lane ladder in both spaces (findings 2, 3)
- `web_httpHandler`'s ON_GET body — a weaker copy of `Space.Helper.resolveRead`'s three tiers (§8.0)
- `mcp_wsHandler` / `mcp_httpHandler` as *transport* variants (they become `as?ws<=mcp` / `as?http<=mcp`)
- `mtron_wsHandler` / `mtron_httpHandler` as classes (three verb definitions survive as rows)
- `web_httpHandler` as a handler (its write algebra moves into `web::T`; its GET policy becomes the default
  `web::T` body)
- `WebSocketRec.IO` or `HttpRec.HttpIO` (one survives)
- the duplicate route tables and the per-space session-key divergence
- ~six `exchange() == null` type-check guards

---

## 11. Migration phases

| phase | work | gate |
|---|---|---|
| **P0** | one `IO` record; one session shape; move the REST verb algebra + `constq` gate into `web::T`; move the exchange ThreadLocal into the carrier | no behavior change; existing web tests pass |
| **P1** | `route::T`-shaped entries; one shared mount table; static-prefix contexts + pattern matching with `+`/`#` capture | `/my/docs/#` serves; `/dr` reachable on both carriers |
| **P2** | protocol/carrier types + `as` rows (incl. the two re-parentings); delete the ladder and the transport-variant handlers; registry-driven protocol fallback | `/drstynx` keeps working via `as?mcp_server<=skill`; `/mtron` via `as?mtron<=…` |
| **P3** | `as?mcp<=instset`, `as?web<=/m/type`; live const resources with `mimeq`/`docq`/`subq`; one tool per base path | an inst set and a user type are both served over both protocols from one mount |
| **P4** | protocol negotiation (Accept, JSON-RPC body, ws subprotocol); SSE/`GET` on the mcp-http path (today a 501) | a stock mcp client connects over streamable-http |
| **P5** | `webSpace` merge (carrier config key) + the client/server split (`http::client::T` / `ws::client::T`) | both carriers on one code path; `*<http://…>` no longer round-trips through the server space |

Path integration (§5.2) is **orthogonal** — it can land at any point and is not a blocker, because paths are
sugar over hops.

---

## 12. Risks and open questions

1. **Generic rows and the audit.** `Obj.insts()` declares two generic rows with identical `dom(A).rng(B)` and
   different arg specs (`lst(ALL_TYPE)` → `lhs.as(type)` at `Obj.java:1189`; `lst(T(B))` → `lhs.tid(vid)` at
   `:1203`). `asInsts()` filters only `dom != rng` (`Inst.java:883`), so both enter the graph and
   `checkAsGraph`'s `DUPLICATE` check would group them. Resolution matters: it decides whether protocol
   defaults may be **split by argument type** or must be a **single row per (kind, protocol)**. (5-minute
   check.)
2. **The fast path bypasses scoring.** `result.getFirst()` with no arg check and no scoring
   (`ScoringInstResolver.java:89-97`). So one row per (dom, rng) is a correctness precondition — variants
   must ride the argument, and `(dom, rng)` uniqueness must hold for every protocol row.
3. **The audit is ungated.** `AsGraphTest` logs; it does not fail (`:45-59`). Recommend gating
   `DUPLICATE`/`AMBIGUOUS` for protocol rngs only.
4. **JDK context semantics** (§8.1) — the static-prefix workaround should be verified against `getHttpContext()`
   -derived mount-relative paths before relying on it.
5. **Router pattern vs one webSpace** (§8.6) — merging the *address spaces* may not be desirable; merging the
   *implementation* is. Confirm before P5.
6. **Enumeration** for `as?web<=/m/type` (§8.4) — solved for `SchemaSpace` spaces via
   `resolveCollectionSchema`; still open for non-schema spaces (`memSpace`, `fsSpace`).
7. **The encoding-vs-surface classification hypothesis** (§3.1) — verify against calibration pairs before
   designing on it.
8. **`webSpace` naming/tids** — today `/m/web/space/httpspace` and `/m/web/space/wsspace`; a merged space
   wants one vid plus a carrier key, and `HTTP_SOCKET_TID` / `WS_WEBSOCKET_TID` already exist as the carrier
   object types.
9. **The web query vocabulary** (§8.0.5) — adopt the traversal insts (`from` + `take` / `where` / `order` /
   `count` / `sum` …) as URL query parameters and let the rewrite layer push down, or expose a REST-shaped
   filter dialect? The former is portable across SQL / document / graph with no new code; the latter is more
   conventional for non-metatron clients.
10. **Two shapes for `SCHEMA`** — `SchemaSpace.schema()` reads `at(SCHEMA)` as an `InstSet` while
    `AbstractDataPathSpace.getCollectionType` navigates `at(SCHEMA).at("type")`. Both work (the instset rec
    form carries a `type` key) but the web face should pick one accessor.
11. **Resolve-vs-reference at the transport boundary** (§8.0.5) — reads can carry `!@` auto pointers (the
    probe above returned them for a class rec). Without a stated policy, a stock browser or MCP client
    receives metatron-internal references.

---

## 13. Decisions needed

1. **Which target should `mcp::T` and `web::T` be?** `webInstSet` (owns the existing mcp/web types) or
   `mInstSet` (owns `as`, `type`, `space`)? *Recommendation:* `webInstSet` for `web`/`mcp`/`stream`; argue
   `mtron::T` separately.
2. **Overloads.** One tool per instruction base path with VM-resolved overloads (recommended, corroborated by
   the code index), or per-signature suffixed names?
3. **Instance enumeration** for type collections: convention, declared index, or scan?
4. **`prompt`** — derive from `docq` docs (recommended), or only from explicitly declared `mcp_server` recs?
5. **Writes on an instset mount** — `PUT /m/math/plus` allowed by default, or read-only unless declared?
   (`constq` suggests read-only by default for inst sets, opt-in for spaces.)
6. **Mount config** — the as-row argument (recommended), a `route::T` rec, or both?
7. **One mount table across carriers** — shared with an optional `transport` tag (recommended), or per-carrier
   tables?
8. **Handler-vid route targets** — `/mcp => mcp_mtron` keeps working untouched via `mcp_server::T <: mcp::T`;
   confirm `/mtron => mtron_ws` becomes a protocol row rather than a type.
9. **`webSpace`** — one implementation with a `carrier` key (recommended) vs two classes sharing an abstract
   base; and does the client half move out in the same phase or later?
10. **Audit gating** — should `checkAsGraph` fail the build for protocol rngs (recommended), and should the
    protocol lattice be checked at boot for mount validation?
11. **Mount targets** — `DataPath`-shaped addresses with collection / entry / field capture (recommended,
    §8.0), or raw fURI patterns?
12. **URL query parameters** — traversal insts with rewrite pushdown (recommended, §8.0.5), or a REST filter
    dialect for the web face?
13. **One projection row or two** — should `as?mcp<=space` for a `SchemaSpace` delegate to the
    `as?mcp<=instset` body (recommended, since a schema *is* an instset), or stay a separate row?
14. **Response resolution** — resolve auto pointers at the web/mcp boundary by default (larger payloads,
    readable outside metatron), or emit references by default with an opt-in to resolve (recommended, since
    metatron-native clients dereference natively)?
15. **PATCH granularity** — per-leaf `expandStructural` + `resolveWrite` (recommended, §8.0), or the
    whole-document `>>=` merge `web_httpHandler` uses today?

---

## Appendix A — live probe transcript (running VM, 2026-09-11)

```
/usr/marko/metatron/src/mcpServer()
  ==> rec with keys: field, comment, constructor, method
      field.LOG                     => !@/usr/marko/metatron/code/1/classes/mcpServer/0/members/0/LOG
      method.of                     => !@.../members/6/of                      (single vid)
      method.handleMessage          => [ 3 method recs ]                        (overloaded -> lst)
      method.handleToolsList        => !@.../members/17/handleToolsList         (single vid)

*/usr/marko/metatron/idx/mcpServer/method/handleMessage
  ==> [ {kind:<method>, name:handleMessage, signature:"Obj handleMessage(final Obj message)",
         header, body, text},
        {..., signature:"Obj handleMessage(final String rawJson)", ...},
        {..., signature:"Obj handleMessage(final Obj blind, final String rawJson)", ...} ]

a => b => c                     ==> ["<a>",["<b>","<c>"]]        i.e. a => (b => c)
a => b => c.>>.>>               ==> "<c>"

(rec::T => doc::T => inst::T).>>
  ==> Error -32603: invalid arguments: Cannot invoke "fURI.big()" because "vid" is null

rec::T => doc::T => inst::T
  ==> parse error at line 1, col 7:  could not parse at ' '

*/usr/marko/metatron/idx/mcpServer/method/handleMessage.>>.>>.>>.at(name)
  ==> null
```

## Appendix B — evidence index

| claim | evidence |
|---|---|
| the mount table is `route` on every space | `AbstractSpace.java:151-153`; `Space.java:79` |
| two independent route tables | `drstynx.boot.mtron:81-86`, `:152-155` |
| the route RHS is resolved/applied | `Space.Helper.resolveApply`, `Space.java:244-252` |
| duplicated three-lane ladder | `httpSpace.java:131-162`; `wsSpace.java:201-251` |
| `createContext` is literal-prefix | `httpSpace.java:192`; `web_httpHandler.java:100-108`, `:405-415` |
| `httpSpace` reads its session cache locally | `httpSpace.java:287-298`; cache written at `:196-218` |
| `httpSpace.directWriter` POSTs remotely | `httpSpace.java:368-399` |
| `HTTP_CLIENT_TYPE` is a throwing stub | `httpSpace.java:118-124` |
| sessions: header key vs socket attachment | `httpSpace.java:193-194`; `wsSpace.java:226` |
| `mcp_httpHandler` session field / map | `mcp_httpHandler.java:83-84`, `:120-129`, `:186-188`; `doGet` 501 at `:158-162` |
| one `IO` record declared twice | `WebSocketObj.java:47-53`; `HttpRec.java:376-386` |
| `HttpRec` exchange ThreadLocal + rationale | `HttpRec.java:72-78` |
| type-check guards in verb bodies | `web_httpHandler.java:86`, `:265-268`, `:333-336`, `:375-377` |
| web write-verb algebra | `web_httpHandler.java:259-292`, `:303-367`, `:372-389`; gate `:394-400` |
| `mcpServer` is transport-agnostic | `mcpServer.java:70-158`; `handleMessage(String)` `:174-176`, schema-aware `:183-204` |
| resource snapshots | `mcpMetatronBuilder.java:71`, `:248-272`; read at `mcpServer.java:290-303` |
| subq → resources/updated outbox | `mcpServer.java:403-465` |
| `as?tool<=/m/inst`, `as?tool<=docs` | `llmInstSet.java:716`, `:708` |
| `as?mcp_server<=skill` | `webInstSet.java:535` |
| `AS_INST_TID` | `mInstSet.java:154`; `mFluent.java:328-330` |
| `as` fast path + scoring | `ScoringInstResolver.java:89-97`, `:242-299` (`+2000` at `:279-286`) |
| refinement relation | `Inst.java:445-451`, `:971-973` |
| audit API + violation classes | `Inst.java:775-806`, `:809-877`, `:879-886`, `:888-925`, `:940-963` |
| audit ungated | `AsGraphTest.java:45-59` |
| generic `as` rows in `Obj` | `Obj.java:1187-1203` |
| InstSet tables + overload grouping | `AbstractInstSet.java:50-53`, `:97-148`, `:150-168`, `:180-213` |
| `INSTSET_TYPE` / `importInstSet` | `InstSet.java:55-69`, `:133-150` |
| tool naming flattens the tid | `mTool.java:134-136`; docs via `?docq` `:206-219` |
| MIME bridge | `MIME.java:92-266` |
| generic vid read yields a type | `BasicRouter.java:307-333` (`:317-318`) |
| space pattern registration/redirects | `AbstractInstSet.setup` `:108`, `:120`, `:131`; `BasicRouter.java:229`, `:353-361` |
| qproc catalogue | `QCollection.java:74-194`; `mimeQ` `:313-346`; `constQ` `:367-384`; `typeQ` `:390-413` |
| `DataPath` structure, schema map, wildcard cascade | `furi/DataPath.java:74-300`; `expandStructural` `:369-395`; `navigateWithin` `:312-335` |
| the back-space contract hooks | `isa/AbstractDataPathSpace.java:78-143`; migration `:153-173`; root-constraint override `:179-185` |
| collection segment → `Type` | `isa/SchemaSpace.java` (`schema()`, `resolveCollectionSchema`); call sites `dcmntSpace.java:593-596`, `grphSpace.java:426-429` |
| native query pushdown vocabulary | `algebra/rewrite/RewriteBuilder.java:102`; `CommonRewrites.java` matches `from` + `take` / `skip` / `order` / `count` / `where` / `sum` / `mean` / `prod` / `dedup` |
| graph meta-collections `V` / `E` | `grphSpace.java:444-459` (`isRefinementOf(VRTX_TYPE \| EDGE_TYPE)`) |
| the core read algorithm | `Space.java:284-321` (`resolveRead`), `:261-282` (`unrollPoly`), `:404-416` (`locateBasePoly`) |
| the write mirror | `Space.java:332-387` (`resolveWrite`), `:323-330` (`writeComplete` closes streams on `noobj`) |
| read/write stream guarantees | `Space.java:101-128` (concrete fURIs; a wildcard write = expand then write each) |
| the web layer's weaker copy of it | `web_httpHandler.java:83-200` (direct read → `DEFAULT_PAGE` → `locateBaseObj` → 404) |
| flat / structured duality | `tbleSpace.java:671-698`; `dcmntSpace.java:854-909`; `grphSpace.java:690-786` |
| relevant tokens | `Tokens.java` — `route` `:173`, `transport` `:233`, `protocol` `:234`, `tool` `:115`, `resource` `:128`, `prompt` `:127`, `web_root` `:312`, `default_page` `:313`, `read_only` `:314`, `on_get`… `:221-227` |

## Appendix C — next steps when work resumes

1. Answer §13 (15 decisions) — most block the row table.
2. Resolve risk 12.1 (the `Obj` generic-duplicate question) — it fixes the shape of the generic rows.
3. Write the row table: `dom / rng / arg-type / body` for the protocol and carrier rows, then run
   `Inst.Helper.checkAsGraph()` over the protocol rngs and record the violations before and after.
4. Write the mount-table shape in `as`-path vocabulary, plus the revised web section of
   `drstynx.boot.mtron` in the same vocabulary.
5. Decide §12.9 (URL query vocabulary) before writing the `web::T` body — it determines whether the web face
   needs any query parsing at all.
6. Only then: P0 (one `IO`, one session shape, the REST verbs into `web::T` — built on `Space.Helper.resolveRead` /
   `resolveWrite`, `DataPath` + `expandStructural`, with no new read logic in the web layer).
