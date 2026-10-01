---
name: distributed-metatron
description: Index of metatron's distributed primitives — the dispatch seam, every wire transport, the remote endpoints, the in-VM concurrency motifs, and the wire format. Each row is a composable motif with its witness.
---

# Distributed primitives (Java)

An index of every place metatron crosses a process boundary. Each motif is atomic and user-composable; the witness
column is where to look when a byte moves.

## Package map

```
isa/mach/type/Router.java                     ← the dispatch chokepoint (readFromSpace / writeToSpace)
isa/mach/type/router/BasicRouter.java         ← authority guard, peer roster, foreign dispatch
furi/fURI.java                                ← authority(), localize(), hasAuthority()
isa/m/mInstSet.java                           ← from / to / ref — the language's own deref+ref primitives
isa/web/space/ws/wsSpace.java                 ← wsspace::T  (WebSocket server)
isa/web/space/ws/WebSocketRec.java            ← wsclient::T (WebSocket client, sendRecv)
isa/web/space/ws/handler/mtron_wsHandler.java ← mtron_ws   (remote eval endpoint)
isa/web/space/http/httpSpace.java             ← httpspace::T
isa/web/space/http/handler/*_httpHandler.java ← mtron_http, mcp_*, web_ (REST verbs)
isa/web/space/stdio/handler/mcp_stdioHandler.java ← MCP over stdio
isa/mach/type/thread/                         ← virtual / core threads, FutureObj
isa/sys/type/ThreadExecutor.java              ← the shared virtual-thread executor
isa/mach/type/processor/SwarmProcessor.java   ← monad step-loop, running/barrier/halted queues
isa/mach/type/monad/BasicStatefulMonad.java   ← the work unit (obj, inst, state, code)
isa/mach/io/type/*Serializer.java             ← what actually goes on the wire
```

## 1. The dispatch seam

One function decides local vs remote; everything else is a transport it can call.

| primitive | what it does | witness |
|---|---|---|
| `*x` / `from(x)` | dereference — reads through the Router | `mInstSet.java:146` |
| `x -> y` / `to(x)` | reference — writes through the Router | `mInstSet.java:145` |
| `x.ref(y)` | reference, explicit | `mInstSet.java:147` |
| `Router.readFromSpace` / `writeToSpace` | the only two doors; every deref and ref ends here | `Router.java:81`, `:90` |
| `BasicRouter.getSpaceFor` | picks the space by pattern — most specific wins | `BasicRouter.java:385` |
| `own(vid)` | is this authority mine? (`0.0.0.0`/`127.0.0.1`/`localhost`/`::1` are one service) | `BasicRouter.java:307` |
| `isPeer(vid)` | is this authority in the declared roster? | `BasicRouter.java:340` |
| `dispatchForeign(vid, obj)` | sends `from(localized)` / `ref(localized, obj)` to a peer | `BasicRouter.java:362` |
| `/sys/peer` roster | declared membership: authority → transport inst | `BasicRouter.java:272` |
| `selfAuthorities()` | the authorities this VM answers to, memoized | `BasicRouter.java:283` |
| `fURI.authority()` / `localize()` | `host:port`; strip scheme+authority, keep path | `fURI.java:276`, `:297` |

Order in `read`/`write`: guard **before** `getSpaceFor`. Undeclared authority → falls through (fail-closed), never
emergent membership.

## 2. Wire transports

| type | direction | pattern | witness |
|---|---|---|---|
| `wsspace::T` | server | `ws://#` | `wsSpace.java:74` |
| `wsclient::T` | client | `ws://host:port/path` | `wsSpace.java:77`, `WebSocketRecClient.java:121` |
| `httpspace::T` | server | `http://#` | `httpSpace.java:76` |
| `HttpRec` | client | `http(s)://…` | `HttpRec.java` |
| `mqttspace::T` | both | broker URI | `mqttSpace.java` |
| `serialspace::T` | both | device path | `serialSpace.java` |
| `dckrspace::T` | client | Docker daemon TCP | `dckrSpace.java` |
| `grphspace::T` | client | Gremlin `DriverRemoteConnection` | `grphSpace.java` |
| `vecspace::T` | client | ChromaDB HTTP | `vecSpace.java` |
| `tblespace::T` | client | JDBC | `tbleSpace.java` |
| `fsspace::T` | local | `local:#` | `fsSpace.java` |

Only `wsspace` + `wsclient` carry **metatron-to-metatron** traffic today. The rest are remote *service* access.

## 3. Remote endpoints (what the far side runs)

| handler | transport | what it does | witness |
|---|---|---|---|
| `mtron_ws` | ws | applies the frame's mtron expression, returns the result | `mtron_wsHandler.java:54` |
| `mtron_http` | http | same, over HTTP | `mtron_httpHandler.java` |
| `web_httpHandler` | http | REST verbs → `writeToSpace` (`GET`/`POST`/`PUT`/`PATCH`/`DELETE`) | `web_httpHandler.java` |
| `mcp_wsHandler` / `mcp_httpHandler` / `mcp_stdioHandler` | ws / http / stdio | MCP servers | `isa/web/space/*/handler/` |
| `bin/wsplus` | ws | standalone CLI client (`-e` one-shot) | `bin/wsplus` |

## 4. In-VM concurrency

Concurrent, not remote — but the same monad is the work unit in both cases.

| primitive | what it does | witness |
|---|---|---|
| `/m/mach/thread` | thread base type (`/thread/virtual`, `/thread/core`) | `machInstSet.java:68-70` |
| `/m/mach/thread_executor` | the shared executor; `sys_stat` reports counts | `machInstSet.java:76`, `ThreadExecutor.java:94` |
| `virtual::[code=>…]@/sys/thread/x` | run code on a virtual thread | `VirtualThread.java` |
| `core::[code=>…]@/sys/thread/x` | run code on a platform thread | `CoreThread.java` |
| `FutureObj` | a thread handle whose result is readable | `FutureObj.java` |
| `SwarmProcessor` | monadic step-loop over `running` / `barrier` / `HALTED` queues | `SwarmProcessor.java:100-171` |
| `StatefulMonad` | `lst(obj, inst, state, code)` — the unit of work | `BasicStatefulMonad.java` |
| `/sys/thread/main` | root thread; shutdown waits on it | `BootLoader.java:557` |

## 5. Wire format

| serializer | used for | witness |
|---|---|---|
| `ObjmtronSerializer` | mtron text — the default frame body | `ObjmtronSerializer.java` |
| `ObjByteBufferSerializer` | compact binary | `ObjByteBufferSerializer.java` |
| `ObjJavaSerializer` | Java object graph | `ObjJavaSerializer.java` |
| `ObjJSONSerializer` | JSON | `isa/web/parser/ObjJSONSerializer.java` |
| `ObjSQLSerializer` / `ObjYAMLSerializer` | SQL rows / YAML | `isa/mach/io/type/` |
| `MIME.MIMEType.APPLICATION_MTRON` | negotiates the mtron codec on a transport | `MIME.java` |

## Rules that hold across all of them

- **An authority you own is decoration unless a space serves that address.** `ws://localhost:8555/usr/x` localizes
  to `/usr/x`; but `ws://localhost:8555/<route>/<n>` is a live session of the `wsspace`/`httpspace` whose pattern
  (`ws://#`, `http://#`) claims that authority, so it is handed to the space instead. The literal rule in
  `BasicRouter.dispatchForeign`: localize only when the authority-free path still resolves to a space.
- **A space is not a wire payload.** Asking a peer for a space itself yields no response at all — ask for `.vid()`.
- **A remote dereference returns a value whose tid carries coefficient `*`** (renders `{*}1`), because the reply is
  typed by the transport inst's rng. The value is correct; only the coefficient differs.
- **A multiplicity of dereferences is a program, not data.** `{*A,*B}` holds unapplied `from` insts (`.explain()`
  reports `rng=>from`), so arithmetic applies element-wise to instructions. Chaining works: `*A.plus(*B)`.
- **Evaluating one has a lasting side effect**: it re-types the dereferenced vids to `*`, so later reads of those
  paths render `{*}1`. Do not put one in a test suite.
- **Wildcard reads yield monads**, not scalars: `*</n/#>` → `{**}1,{**}2,…`.

Deeper rationale, defect list, and the staged plan: `docs/design/distributed-computing.md`.