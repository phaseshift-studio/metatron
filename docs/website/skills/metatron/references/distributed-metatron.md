---
name: distributed-metatron
description: Index of metatron's distributed primitives — the dispatch seam, every wire transport, the remote endpoints, the in-VM concurrency motifs, and the wire format. Each row is a composable motif with its witness.
---

# Distributed primitives (Java)

An index of every place metatron crosses a process boundary. Each motif is atomic and user-composable; the witness
column is where to look when a byte moves.

## Package map

```
isa/mach/type/Machine.java                    ← the dispatch chokepoint (read / write)
isa/mach/type/Network.java                    ← the network component: peer roster, listeners, authority dispatch
isa/mach/type/Memory.java                     ← data memory + the space index (findSpace)
isa/mach/type/machine/MachineSpace.java       ← the machine's own sphere (routes ~/network to Network)
furi/fURI.java                                ← origin(), authority(), localize()
isa/m/mInstSet.java                           ← from / to / ref — the language's own deref+ref primitives
isa/web/space/ws/wsServer.java                ← ws_server::T (a machine's ws presence: a Listener on a socket)
isa/web/space/ws/WebSocketRec.java            ← wsclient::T (WebSocket client, sendRecv)
isa/web/space/ws/handler/mtron_wsHandler.java ← mtron_ws   (remote eval endpoint)
isa/web/space/http/httpServer.java            ← http_server::T (a machine's http(s) presence: a Listener on a socket)
isa/web/space/http/httpClient.java            ← http_client::T (from = GET, to = POST; http://# is the web gateway)
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

| primitive                               | what it does                                                                      | witness                 |
|-----------------------------------------|-----------------------------------------------------------------------------------|-------------------------|
| `*x` / `from(x)`                        | dereference — reads through the Machine                                            | `mInstSet.java:146`     |
| `x -> y` / `to(x)`                      | reference — writes through the Machine                                             | `mInstSet.java:145`     |
| `x.ref(y)`                              | reference, explicit                                                               | `mInstSet.java:147`     |
| `Machine.read` / `Machine.write`               | the only two doors; every deref and ref ends here                          | `Machine.java:154`, `:224` |
| `Network.read` / `Network.write`               | authority dispatch — a dialed peer's origin answers, else fall through       | `Network.java:526`, `:556` |
| `Network.peerOf`                               | the member that serves an origin (exact match, then wildcard)                | `Network.java:350`         |
| `Network.overrideOf`                           | the peer's override for an instruction (`from` / `to` / `eval`)              | `Network.java:373`         |
| `Network.index`                                | construct = connect: index a client as a peer                                | `Network.java:303`         |
| `Memory.findSpace`                             | picks the space by pattern — most specific wins                              | `Memory.java`              |
| `Network.Helper.isLoopbackHost`                | `0.0.0.0`/`127.0.0.1`/`localhost`/`::1` are one service                      | `Network.java:1112`        |
| `Network.Helper.sameService`                   | origin equality, loopback-aliased                                            | `Network.java:909`         |
| `~/network/peer`                               | dialed peers: origin → peer::T (the connection)                              | `Network.java:333`         |
| `~/network/listener`                           | bound sockets: origin → listener::T (a machine's presence)                   | `Network.java:199`         |
| `Network.Helper.authorities()`                 | the origins this VM answers on (its listeners)                               | `Network.java:695`         |
| `fURI.origin()` / `authority()` / `localize()` | `scheme://host:port`; `host:port`; strip scheme+origin, keep path            | `fURI.java:215`, `:319`, `:353` |

Order in `read`/`write`: **authority dispatch before memory**. An address carrying an origin a peer has declared is
that peer's to answer; memory only ever sees an address already localized. An undeclared origin → falls through
(fail-closed), never emergent membership.

## 2. Wire transports

| type             | direction | pattern                                               | witness                                                       |
|------------------|-----------|-------------------------------------------------------|---------------------------------------------------------------|
| `ws_server::T`   | server    | `ws://host:port`, routes per machine                  | `wsServer.java`                                               |
| `ws_client::T`   | client    | `ws://host:port/path`                                 | `wsServer.java` (`WS_CLIENT_TYPE`), `WebSocketRecClient.java` |
| `http_server::T` | server    | `http(s)://host:port`, routes per machine             | `httpServer.java`                                             |
| `http_client::T` | client    | `http(s)://host:port`, or `http(s)://#` (the gateway) | `httpClient.java`                                             |
| `mqttspace::T`   | both      | broker URI                                            | `mqttSpace.java`                                              |
| `serialspace::T` | both      | device path                                           | `serialSpace.java`                                            |
| `dckrspace::T`   | client    | Docker daemon TCP                                     | `dckrSpace.java`                                              |
| `grphspace::T`   | client    | Gremlin `DriverRemoteConnection`                      | `grphSpace.java`                                              |
| `vecspace::T`    | client    | ChromaDB HTTP                                         | `vecSpace.java`                                               |
| `tblespace::T`   | client    | JDBC                                                  | `tbleSpace.java`                                              |
| `fsspace::T`     | local     | `local:#`                                             | `fsSpace.java`                                                |

Only `ws_server` + `ws_client` carry **metatron-to-metatron** traffic today. The rest are remote *service* access.

## 3. Remote endpoints (what the far side runs)

| handler                                                  | transport         | what it does                                                                        | witness                    |
|----------------------------------------------------------|-------------------|-------------------------------------------------------------------------------------|----------------------------|
| `mtron_ws`                                               | ws                | evaluates each frame's mtron expression once, returns the answer                    | `mtron_wsHandler.java`     |
| `mtron_http`                                             | http              | declared `mtron::T`, but does not evaluate yet: answers the request rec it received | `mtron_httpHandler.java`   |
| `web_httpHandler`                                        | http              | REST verbs → `writeToSpace` (`GET`/`POST`/`PUT`/`PATCH`/`DELETE`)                   | `web_httpHandler.java`     |
| `mcp_wsHandler` / `mcp_httpHandler` / `mcp_stdioHandler` | ws / http / stdio | MCP servers                                                                         | `isa/web/space/*/handler/` |
| `bin/wsplus`                                             | ws                | standalone CLI client (`-e` one-shot)                                               | `bin/wsplus`               |

## 4. In-VM concurrency

Concurrent, not remote — but the same monad is the work unit in both cases.

| primitive                          | what it does                                                   | witness                                         |
|------------------------------------|----------------------------------------------------------------|-------------------------------------------------|
| `/m/mach/thread`                   | thread base type (`/thread/virtual`, `/thread/core`)           | `machInstSet.java:68-70`                        |
| `/m/mach/thread_executor`          | the shared executor; `sys_stat` reports counts                 | `machInstSet.java:76`, `ThreadExecutor.java:94` |
| `virtual::[code=>…]@/sys/thread/x` | run code on a virtual thread                                   | `VirtualThread.java`                            |
| `core::[code=>…]@/sys/thread/x`    | run code on a platform thread                                  | `CoreThread.java`                               |
| `FutureObj`                        | a thread handle whose result is readable                       | `FutureObj.java`                                |
| `SwarmProcessor`                   | monadic step-loop over `running` / `barrier` / `HALTED` queues | `SwarmProcessor.java:100-171`                   |
| `StatefulMonad`                    | `lst(obj, inst, state, code)` — the unit of work               | `BasicStatefulMonad.java`                       |
| `/sys/thread/main`                 | root thread; shutdown waits on it                              | `BootLoader.java:557`                           |

## 5. Wire format

| serializer                               | used for                                  | witness                                 |
|------------------------------------------|-------------------------------------------|-----------------------------------------|
| `ObjmtronSerializer`                     | mtron text — the default frame body       | `ObjmtronSerializer.java`               |
| `ObjByteBufferSerializer`                | compact binary                            | `ObjByteBufferSerializer.java`          |
| `ObjJavaSerializer`                      | Java object graph                         | `ObjJavaSerializer.java`                |
| `ObjJSONSerializer`                      | JSON                                      | `isa/web/parser/ObjJSONSerializer.java` |
| `ObjSQLSerializer` / `ObjYAMLSerializer` | SQL rows / YAML                           | `isa/mach/io/type/`                     |
| `MIME.MIMEType.APPLICATION_MTRON`        | negotiates the mtron codec on a transport | `MIME.java`                             |

## Rules that hold across all of them

- **An origin you own is decoration unless a space serves that address.** `ws://localhost:8555/usr/x` localizes
  to `/usr/x`. A server's sessions and handlers are no addresses: a ws session is held by its socket and reached
  through the peer it mints, and an http handler is held by its server. The literal rule in
  `Network.read` / `Network.write`: localize only when the origin-free path still resolves to memory.
- **A space is not a wire payload.** Asking a peer for a space itself yields no response at all — ask for `.vid()`.
- **A remote dereference returns a value whose tid carries coefficient `*`** (renders `{*}1`), because the reply is
  typed by the transport inst's rng. The value is correct; only the coefficient differs.
- **A multiplicity of dereferences is a program, not data.** `{*A,*B}` holds unapplied `from` insts (`.explain()`
  reports `rng=>from`), so arithmetic applies element-wise to instructions. Chaining works: `*A.plus(*B)`.
- **Evaluating one has a lasting side effect**: it re-types the dereferenced vids to `*`, so later reads of those
  paths render `{*}1`. Do not put one in a test suite.
- **Wildcard reads yield monads**, not scalars: `*</n/#>` → `{**}1,{**}2,…`.

Deeper rationale, defect list, and the staged plan: `docs/design/distributed-computing.md`.