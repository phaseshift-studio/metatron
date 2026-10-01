# Distributed computing — review and consolidated perspective

*Status: design framing. No implementation proposed here is committed code; every "exists" claim carries a
`file:line` witness, and every "missing" claim was checked against `src/main/java`.*

## 0. The thesis, in one paragraph

Metatron should **not** grow a distributed scheduler, a cluster manager, or a new programming model. It should
make the monadic loop it already has **authority-partitioned**. Three facts are already true and already
sufficient:

1. **The unit of work is already a portable mtron value.** A `StatefulMonad` is literally
   `lst(obj, inst, state, code)` (`BasicStatefulMonad.java:72-74`).
2. **The address already names its owner.** `fURI` carries `scheme://authority/path`, and the Router already
   dispatches reads and writes exclusively through that address (`Router.java:81-101`).
3. **The degree of parallelism is already declared in the type.** An inst's dom coefficient *is* its
   scatter/bulk/barrier shape (`Inst.java:364-374`: `isGather`, `isBatching`, `isScatter`, `isInitial`), and the
   processor already splits monads by coefficient and collects them at a barrier
   (`SwarmProcessor.java:459-475`, `:335-411`).

Distribution is what you get when those three facts are allowed to cross a socket. The gap is not conceptual —
it is that **read, write, and the machine's queues are not yet authority-aware**, and the prototype that made
them so was shelved mid-stride.

---

## 1. What already exists

### 1.0 The project's own framing already names the three machines

Before anything else: this review is not proposing a new model. The website publishes the model already
(`docs/website/adoc/index.adoc:229-254`), as a triptych of machines:

| machine | published description |
|---|---|
| **OLTP** — *monad iterator* | "speedy traversals over localized regions of space" — the sequential monad stream |
| **OLRP** — *monad swarm* | "asynchronous, parallel, **distributed** traversals over large regions of space … hundreds of thousands of monads can traverse vast swathes of the data landscape, **across different physical machines**" |
| **OLAP** — *monad vector* | "**stateful bulk synchronous processing** … a series of parallelly executing stages are demarcated by **serializing stateful barriers**" |

The first exists (`SwarmProcessor`). The second and third are the same engine with the topology widened — and
the OLAP description *is* BSP, in the project's own words. `start.adoc:245` likewise labels the fURI host
component "physical location (**authority**)", so the address-level framing is also already the project's.

The gap between the published model and the code is not a missing idea. It is that the swarm's monads cannot
yet leave the machine they were minted on.

### 1.1 Four address spaces already reach across the wire

| surface | what it is | witness |
|---|---|---|
| `wsspace::T` | a WebSocket server, pattern `ws://#`, routes `/mtron` → `mtron_ws`, `/mcp` → `mcp_mtron` | `wsSpace.java:74`, `boot/boot.mtron` |
| `httpspace::T` | an HTTP server, pattern `http://#`, routes `/mcp` and a web root | `httpSpace.java:74-86`, `boot/space/web/http.mtron` |
| `mtron_ws` / `mtron_http` | a **console-like eval endpoint**: the frame's mtron expression is applied and the result sent back | `mtron_wsHandler.java:66-91`, `mtron_httpHandler.java:66-96` |
| `httpspace` REST verbs | `GET`/`POST`/`PUT` → `Router.writeToSpace`; `PATCH` → the `>>=` update algebra; `DELETE` → write `noobj` | `web_httpHandler.java:245-292`, `:294-317`, `:318-361`, `:362-383` |

The important consequence: **a remote evaluation endpoint and a remote resource protocol already exist, and both
already terminate in `Router.readFromSpace` / `Router.writeToSpace`.** There is no missing transport.

### 1.2 The Router is the single dispatch chokepoint

`Router` **is** a `Space` (`Router.java:44`), and every read and write in the system funnels through two static
methods (`Router.java:81-101`):

```java
static Obj readFromSpace(fURI vid)  { ... BootLoader.ROUTER.read(vid) ... }
static Obj writeToSpace(fURI vid, Obj obj) { ... BootLoader.ROUTER.write(vid, obj) ... }
```

Those are called from the language's own dereference and reference primitives — `*x` / `from(x)`
(`Obj.java:1349`, `:1428-1431`), `x->y` / `to(x)` (`Obj.java:1425`), and `x.ref(y)` (`Obj.java:1437`) — and from
`Space.Helper.resolveRead`/`resolveWrite` on every concrete space. **One seam, already load-bearing.** That is
the whole reason this story is cheap.

### 1.3 Authority is already part of address matching

`BasicRouter.getSpaceFor` selects the space a URI belongs to by pattern, choosing the **most specific** match
(`BasicRouter.java:253-269`, `min(Comparator.comparing(Space::pattern))`). `fURI.compareTo` sorts a `#` host
last, so a concrete authority beats a wildcard (`AbstractfURI.java:278-292`). And `fURI.test` /
`hasPrefix` compare scheme, host and port — with `#`/`+` wildcards — including an explicit authority check
(`AbstractfURI.java:350-359`, `:442-445`).

So **an authority-claiming space already wins the routing contest.** No new dispatch machinery is required to
route by authority; a space whose `pattern()` names an authority already receives everything addressed there.

### 1.4 The prototype exists, and it is shelved, not absent

The intent is written into the type system. `CLSTR_SPACE_TYPE` (`machInstSet.java:254-264`) documents:

> a peer is a wsclient to a mtron_ws handler. `*x` and `x->y` are the respective read/write insts sent to the
> peer for evaluation.

And `clstrSpace.java:125-167` implements exactly that: read/write dispatch that compares `vid.authority()`
against its own `localHost`, routes locally through `Router`, and otherwise applies a peer's `send`/`send_recv`
with `from_(vid)` or `start_(vid).ref_(obj)`.

Its original home was the Router itself — `BasicRouter.java:312-314` and `:337-340` still hold the commented-out
authority branches:

```java
// if (vid.hasAuthority())
//   return this.server().sendRecv((a, b) -> a.authority().matches(b.remoteHost().authority()), vid, from_(vid.localize().toUri()).tryToInst());
```

Note `vid.localize()` — **that method no longer exists on `fURI`.** The prototype has been decaying in place.

And the prototype is not merely unexercised — it is **unreachable as configured**. `clstrspace` is mounted in
exactly one boot profile, `boot/dev.mtron:71-74` (not in `boot.mtron`, `docker.boot.mtron` or
`console.boot.mtron`):

```mtron
clstrspace::[pattern => <ws://+/cluster/#>,
             host    => <ws://localhost:8555/cluster>,
             route   => [<ws://localhost:8555/cluster> => <>],
             peer    => [<localhost:${*boot/args/port2}> => wsclient::[host=><ws://localhost:${*boot/args/port2}/mtron>]]]@/sys/space/cluster;
```

The pattern's host is a **wildcard (`+`)** and the path is the reserved segment `/cluster/`. So the cluster space
claims *a path on any host*, never *a host*. Meanwhile `wsspace` claims `ws://#`. Any peer **data** address —
`ws://hostB:8555/usr/x` — fails `<ws://+/cluster/#>` (wrong path) and falls through to `wsspace` (`ws://#`), i.e.
to the **local** WebSocket server space, which will look in its own session cache and find nothing.

**That is the precise reason the prototype stalled.** Authority routing cannot happen through the space pattern
as configured; it could only ever happen in the Router — and that branch is the commented-out code above. The
design is not missing; it is *disconnected*.

### 1.5 The seed that does work: the Remote Console

One cross-host capability is not merely designed but **documented and executed** — `web.adoc:121-146`
("Remote Console"), whose examples are evaluated when the docs are built:

```mtron
wsclient::[host=><ws://localhost:8555/mtron>]@/usr/ui/console/remote
mtron> :redirect/input inst?#{*}<=#{?}(to_send=>#{?}::T){*/usr/ui/console/remote.send_recv(*to_send)}
```

> At this point, all console input is sent to the remote metatron instance, evaluated at that instance via the
> `mtron_ws::T` endpoint, where the results are returned to the local instance and displayed in the local
> console UI.

This is the whole distributed story in miniature and it is already true: a `wsclient::T` is a peer, `mtron_ws` is
a remote *machine* willing to apply code, `send_recv` is the request/response edge, and the console's
`redirect/input` is the seam. Everything below generalizes exactly this — it is the one place to start from,
because it is the one place known to work.

### 1.6 The work unit is a plain mtron value, and the queues are the topology

`MonadProcessor`'s own javadoc states the design thesis (`MonadProcessor.java:24-33`):

> A distributed monadic system is the same triple one level up — each shard owns its own run/barrier/halt, and a
> coordinator aggregates the `halt` of its children.

The `SwarmProcessor` loop (`SwarmProcessor.java:298-428`) is:

```
while (running not empty or barriers not empty):
    m = running.take()
    x = split(m)                       // split by dom coefficient  → scatter
    for each y in x.apply():
        if y.inst().isBatching():
            if y.inst().isGather(): barriers.peek().obj().append(y.obj())   // → gather
            else:                   running.append(y)                       // → map
        else if y.halted():        onHalt(y.obj())                          // → collect
        else:                      running.append(y.next...)
    if running empty and barriers non-empty:
        result = Processor.Helper.apply(barrier.obj(), barrier.inst())
        → next inst is gather? append to next barrier : scatter result to running
```

That is BSP. `running` is the worker set, `barriers` is the superstep boundary, `halted` is the reduction sink.
**The machine already implements the whole model; it just does it with one processor.**

### 1.7 Threads and machines are already URI-addressable and lifecycle-managed

- `mThread` defines `stop` / `pause` / `resume` / `state` / `apply` / `applyAsync` / `result` / `future` /
  `source` (`mThread.java:39-128`) — a complete remote-controllable contract.
- `SwarmProcessor extends VirtualThread implements MonadProcessor` (`SwarmProcessor.java:74`) — **the execution
  engine is already a thread**.
- `ThreadExecutor` exposes every thread as mtron-readable state at `/sys/thread/{run,stop}`
  (`ThreadExecutor.java:52-90`) — the pool is already introspectable *by URI*, hence already remotable.
- `machine::T` is a rec of `(instset, compiler, processor)` (`machInstSet.java:216-223`) and
  `Machine.apply(code, start)` is compile-then-run (`Machine.java:100-102`). A machine is already a value.
- Lifecycle insts already exist: `stop` / `pause` / `resume` on a thread (`machInstSet.java:275-286`).

---

## 2. The fundamental distribution operations

The honest answer to "what operations do we expose" is: **six, and four of them already have syntax.** The work
is to make them authority-complete, not to invent them.

| # | operation | mtron surface | status | where the seam is |
|---|---|---|---|---|
| 1 | **name the owner** | `x.authority()`, `x.localize()` | half: `authority()` exists (`fURI.java:276`), **`localize()` was deleted** | `fURI` |
| 2 | **resolve** (read across) | `*x`, `from(x)` | exists, **not authority-aware** | `Router.readFromSpace` / `BasicRouter.read` |
| 3 | **reference** (write across) | `x->y`, `to(y)`, `x.ref(y)` | exists, **not authority-aware** | `Router.writeToSpace` / `BasicRouter.write` |
| 4 | **bind a peer** | `peer` on a cluster space | exists as a rec field, no live client | new `peerSpace` + client registry |
| 5 | **apply code there** | `machine::T`, `thread`, `code` | exists locally; **non-portable as compiled** | `Machine.apply` / `Processor.applyAsync` |
| 6 | **scatter / gather** | coefficient `{k}` (bulk) and `#` (barrier) | exists **locally only** | `SwarmProcessor.split` + the barrier drain |

### The four operations in detail

**(1) `authority` — the ownership total function.** `fURI.hasAuthority()` (`fURI.java:176`) and
`authority()` (`fURI.java:276`) exist. What does not exist is a *single canonical answer to "what is my own
authority?"*. Today it is derivable from each server space's `host` config (`boot/boot.mtron` binds
`http://0.0.0.0:8777` and `ws://0.0.0.0:8555`), and `clstrSpace` carries a private `localHost`. **This is a real
gap and it is tiny**: one address, e.g. `/sys/mach/host`, plus the rule that `0.0.0.0` aliases to the local
authority. Without it, "is this address mine?" has no answer, and every dispatch site invents its own.

**(2) `localize` — strip the authority, keep the path.** The deleted method. It is a pure total function
`ws://host:8555/usr/x  →  /usr/x`, and it is the *entire* requirement for the transport boundary: what crosses
the wire is the *destination-relative* address, and the receiver writes it into its own Router. Its absence is
why the prototype rotted.

**(3) `resolve` / `reference` — the boundary-aware read and write.** Today
`Router.readFromSpace(vid)` has exactly one behaviour: find a local space by pattern and read it. The
distribution version is a two-branch total function, and it belongs at *one* place:

```
read(vid)  = own(vid.authority()) ? localRead(vid)
                                  : peerFor(vid.authority()).send_recv(from(vid.localize()))
write(vid, o) = own(vid.authority()) ? localWrite(vid, o)
                                     : peerFor(vid.authority()).send_recv(to(vid.localize(), o))
```

**(4) `peer` — the client, which is a space, not a Router branch.** This is the one real design decision
(§5).

**(5) `machine` / `thread` — apply code there.** Because `Machine.apply(code, start)` is compile-then-run, and
because `SwarmProcessor` *is* a `VirtualThread`, "run this code on host B" is already expressible. With one
hard constraint (§4.1): **you cannot ship compiled code.**

**(6) `scatter` / `gather` — the collectives.** `SwarmProcessor.split` (`:459-475`) is the scatter: it takes a
monad whose obj coefficient exceeds the inst's dom coefficient and re-enqueues the remainder. The barrier path
(`:378-411`) is the gather. Both are already coefficient-driven. Distributed variants are the *same two moves*
with the peer's queues as the other end of the wire.

---

## 3. Why the three facts compose: a worked proof

Metatron need not add a MapReduce instruction, because the coefficient already *is* the parallelism policy.

```mtron
usr/rows{1000000}.*score.reduce(sum)          [-- 1M-way map, then a reduction --]
```

- `score`'s dom is `{1} => {1}` (a map) — `split()` hands each element its own monad
  (`Inst.isMap`, `SwarmProcessor.java:459-475`).
- `reduce(sum)` has a `#` dom (unbounded) — `Inst.isGather()` is `dom().c().max() == null`
  (`Inst.java:364-369`), so its monads park in `barriers` and the processor applies the gather once to the
  collected `Objs` (`SwarmProcessor.java:378-411`).

Nothing in that expression knows whether the million monads are spread over 1 processor or 8. **The distribution
decision is a policy over the coefficient and the authority — not a different program.** This is the whole
opportunity: the same expression becomes distributed by *where its monads are allowed to run*.

Agent-style message passing falls out the same way. An agent is a machine with a mailbox, and a mailbox is a
space: `?subq` (pub/sub), the MCP outbox (`mcpServer.java:403-465`), and the ledger read idiom
(`SpaceChatSessionStore.sessionRels`) already implement "append a message, notify subscribers". A *remote*
agent is a peer whose mailbox URI carries an authority — i.e. operation (3), not a new subsystem.

BSP falls out too, because the `barriers` queue *is* the superstep. A distributed superstep is: scatter to the
peers' `running` queues → each peer drains locally → the coordinator reads each peer's `halted`/`barriers`
and applies the gather locally → next superstep.

---

## 4. What is broken or missing — the honest defect list

These are ordered by how much they block the story.

### 4.1 The compile boundary is the distribution boundary (a hard constraint, not a bug)

`ObjmtronSerializer.generateInst` renders a **resolved** inst as
`name(args){lambda$xyz$1234@5678}` — it writes `inst.f()`'s Java `toString()`
(`ObjmtronSerializer.java:353-363`). That text does not re-parse. Therefore:

> **Compiled code does not cross the mtron wire. Only unresolved source does.**

This is not incidental — it is the correct design, and it is why the prototype executed
`host1:1223/abc.to(x)` as *text* for the peer to parse and compile. Two consequences that must be designed for,
not worked around:

- **The peer must be able to compile the code**, which means the peer's machine must hold the same instruction
  set. Distribution therefore requires **machine identity negotiation** (an instset handshake), not merely code
  transport. `machine::T` already carries the instset; the handshake is what is missing.
- Any "send a monad" operation must send the monad's **`code` in source form**, not the monad as compiled. The
  monad's `lst(obj, inst, state, code)` shape means `code` is one of the four slots — read it, and the same
  `ObjmtronSerializer.writeCode` (`:368`) produces shippable text.

There is already a designed (unimplemented) answer to the provenance half of this, in the newest design doc —
`docs/design/compilation-execution-strategy.md:150-157`: strategies become first-class objs at `/strategy/*`, and
*"artifacts carry provenance: `CompiledCode` records the strategy URI + epoch. Executing re-resolves its own
named strategy — coherent for **'compile on one node, execute on another'**."* Note that doc self-declares
"**not yet implemented**" (`:3`) and that `Strategy`, `CompiledCode` and `Framework` do not exist in `src`. Its
v4 line ("persistent/AOT compiled artifacts; **remote execution** — compile once, ship the plan", `:274`) is the
same idea taken further. Worth reading before designing the handshake, because it names the epoch/provenance
problem this section raises.

### 4.2 `sendRecv` cannot carry concurrent requests — it is not a fan-out primitive

`WebSocketObj.sendRecv` implements request/response by **mutating the shared `on_message` field** to a one-shot
capture, sending, awaiting a latch, and restoring the previous handler (`WebSocketObj.java:159-185`). Two
in-flight requests on one socket will cross wires, and `sendRecv(msg)` with the default `timeoutMs <= 0`
(`:141-143`) **waits forever** — a dead peer hangs the calling monad with no timeout and no failure.

Any map-reduce or fan-out motif needs N outstanding requests per peer. That requires a **correlation table**
(request id → `FutureObj`) and a per-request timeout that resolves to `fail(...)`. The wire already has the
ingredient — MCP carries `id` (`mcpServer.java:131`) — and `FutureObj` already exists
(`AbstractThread.java:68`).

### 4.3 No peer lifecycle: no pool, no liveness, no backpressure

- `clstrSpace.startDiscovery` (`:97-121`) is a 30-second loop whose body is a `.filter()`, not a `.forEach()`
  — it calls nothing, and its own comment says *"In a real implementation, this would: 1. Check connectivity
  ... 4. Maintain consistent cluster view."* There is no health check, no reconnect, no eviction.
- `ListMonad`'s running queue is an unbounded `ArrayList`, and `take()` calls `removeFirst()`
  (`ListMonad.java:60-65`) — **O(n) per take on an `ArrayList`**, i.e. O(n²) in queue length. For a large local
  swarm that is a hot-path defect; for a scatter to many peers it is an OOM waiting to happen, because there is
  no bounded queue and no flow control.
- `RunningMonads` (`:56-87`) is an alternative queue that **coalesces monads by inst** — a batching/collapsing
  optimisation that would be very valuable for distribution. It is currently unused.

### 4.4 The authority comparison is fragile, and its tests are disabled

`clstrSpace.readWrite` matches peers with `f(e.first().uriValue().authority()).test(authority)`
(`clstrSpace.java:137`) — comparing two authority strings by building URIs out of them and pattern-testing.
`clstrSpaceTest`'s own header documents that this misbehaves: *"determines locality via `vid.authority()`, which
in Tier 1 requires careful URI scheme/authority alignment"* (`clstrSpaceTest.java:45-50`).

And **the entire distributed suite is class-level `@Disabled`**: `clstrSpaceTest.java:54`,
`DistributedMachineEvaluationTest.java:41`, `SampleDistributedMachineTest.java:39` — the last of which also has
its `@Test` annotations individually commented out (`:61-116`).

Worse, `LocalNode`'s "Tier 1" peer send **evaluates through the same shared `Router`**
(`LocalNode.java:189-205`, and its own comment: *"Tier 1 simulation — evaluates via Router rather than live
WebSocket"*; `clstrSpaceTest.java:134-136`: *"all clusterSpaces share the same Router"*). A simulation that
shares the Router **can never catch an authority, serialization, or correlation bug** — exactly the three
classes of bug that matter. Tier 1 as written has negative value.

### 4.5 The cluster space cannot capture a peer data address (the capture failure)

Restating §1.4 as a defect, because it is the single thing standing between the current state and a working
prototype:

| address | matches `<ws://+/cluster/#>`? | matches `<ws://#>`? | goes to |
|---|---|---|---|
| `ws://hostB:8555/cluster/x` | yes | yes | `clstrspace` (more specific path) |
| `ws://hostB:8555/usr/x` | **no** (path is not `cluster/…`) | yes | **`wsspace` — the local server** |

So peer *data* traffic never reaches the dispatch code in `clstrSpace.readWrite` (`:125-156`) at all. The cluster
space is reachable only by an operator who already knows to address `/cluster/`, and the authority comparison
inside it is consequently dead code in practice.

The secondary issue, which will matter once capture is fixed: `wsspace` claims `ws://#` while a peer space would
claim `ws://host:port/#`. Both match, `getSpaceFor` picks the `min` pattern
(`BasicRouter.java:253-269`), and `fURI.compareTo` is asymmetric and not a total order — it returns `-1` for many
incomparable pairs (`AbstractfURI.java:278-292`). It happens to give the right answer here, but nothing
*guarantees* it. **If peers ride pattern matching, the tie-break must be made explicit** rather than incidental.

### 4.6 `httpSpace` is a server and a client wearing one coat

`httpSpace.directReader` tries the local route table then falls back to a remote Jsoup fetch
(`:423-493`); `httpSpace.directWriter`'s local-route branch is **commented out** and it unconditionally POSTs
(`:504-540`) — so reads and writes to the same `http://` space are asymmetric, and a locally-mounted route is
unreachable by write. `docs/design/webspace.md` §8.6 already prescribes the fix, and it is the same fix
distribution needs:

> remote fetch (`*<http://x>`, Jsoup, POST) → **out of the space, into `http::client::T` / `ws::client::T`**

### 4.7 The stale artefacts — a documented-distributed-fiction layer

The docs describe a great deal of distributed machinery that verifiably does not exist. This matters because it
is *findable*: a reader who greps `distributed` lands here first, not on `clstrSpace`.

| the doc says | reality |
|---|---|
| `docs/design/distributed-testing-framework.md:26-31` documents `setupDistributedEnvironment`, `checkDistributedOperation`, `verifyCrossHostRouting`, `testDistributedMachineEvaluation`; `:47` `getHostFromUri` | none exist. `AbstractDistributedMetatronTest` has `createCluster`, `assertFullTopology`, `assertPeerCount`, `assertSendCount`, `waitForVisible`, `assertEventuallyEquals` — a different API entirely |
| `docs/design/ai/mcp-mserver-integration-plan.md:11-22,180` builds on a package `isa.mach.type.net` with `MServer`, `MConnection`, `MClient`; `:453` "Status: Planning phase" | the package does not exist; the real MCP server is `isa/web/type/mcpServer.java` + `mcp_wsHandler` / `mcp_httpHandler` / `mcp_stdioHandler` |
| `docs/design/ai/README.md:84` marks that plan "**✅ Implemented**"; `:199-205` lists "Files Created" under `isa/mach/type/net/…`; `:276` "**MServer**: WebSocket server for distributed computing" | all false as to those paths |
| `docs/design/training/02-language/02-metatron-environment.md:111-117` and `docs/design/training/02-examples/02-advanced-patterns.md:49-78` document a `meta::` distributed/cluster space with `peers => !*boot/peers` | there is no `metaSpace`; there is no `boot/peers` file or boot path; the real key is singular `peer` on `clstrspace`, and `boot/dev.mtron:71-74` is the only profile that sets it |
| `index.adoc:240-254` and `SWEEP_NOTES_STREAM_RING_THEORY.md:127-135,215-217` advertise `OLTPMachine` / `OLAPMachine` / `OLRPMachine` and a vector/BSP machine | no such classes; there is no VECTOR framework; the only engine is the local barrier-based `SwarmProcessor` |
| `compilation-execution-strategy.md:6,148,218,249` refers to `SwarmMachine` | stale name — the class is `SwarmProcessor` (`SwarmProcessor.java:74`) |
| Hindsight's `Machine architecture` page claims concrete `SwarmExecutor`, `MapReduceExecutor` and `BSPExecutor` classes | **none exist in `src/main/java`** (verified by grep). They are aspirational names. |

Two of these are worth acting on rather than merely noting:

- **`distributed-testing-framework.md` should be rewritten** to describe `AbstractDistributedMetatronTest` as it
  actually is, or deleted. A doc that specifies an API which does not exist is worse than no doc.
- **`docs/design/ai/README.md:84`'s "✅ Implemented" is a false status** on a plan whose foundational package is
  absent.

Also worth recording: the only *other* genuinely remote features in the tree are **unrelated to clustering** —
`dckrSpace` over a TCP Docker daemon (`dckrspace-mtron.md:46,347`) and TinkerPop's `DriverRemoteConnection`
(`grph.adoc:82`). Both are remote *service access*, not metatron-to-metatron distribution. And "federation" is
used for two different things with no disambiguation anywhere: cross-**harness** memory on one VM
(`memory-landscape-review.md:285-314` G3, `memory-server-architecture.md:357-381` M5) — explicitly
"unimplemented" — and host clustering. A reader searching "federat" will conflate them.

---

## 5. The one design decision: where the boundary lives

Two candidate seams, and they are not equivalent.

**(A) Router-level** — add the authority branch inside `BasicRouter.read`/`write`, as the commented prototype
did (`BasicRouter.java:312-314`, `:337-340`).

**(B) Space-level** — a peer is a `Space` whose `pattern()` names the peer's authority.

**Recommendation: (B), reached from (A)'s lookup point.** Reasons:

- Metatron's own convention is that *everything addressable is a space* — `AbstractSpace` self-registers on
  construction (`AbstractSpace.java:60-61`), and `getSpaceFor` is the single selection point. A peer-space
  composes with `?docq`, `q` procs, `close()`, stats, and reflection for free; a Router branch does not.
- A peer-space is *introspectable at a URI*, which is what makes the whole thing debuggable in the same way
  everything else in this VM is (this is the property `ThreadExecutor` already exploits).
- It keeps the network out of the type system's hot path: a local read still takes exactly the local path.

The concrete shape — small and surgical, at one place:

```
getSpaceFor(vid):
    ... existing space scan ...
    else if (vid.hasAuthority()) return peerRegistry.spaceFor(vid.authority())   // mount on first contact
    else throw "no active space supports pattern"
```

with `peerSpace.pattern() == ws://that-host:that-port/#`, so every subsequent lookup is specific and wins
outright. **This is precisely the inversion the current `clstrspace` gets wrong** (§4.5): it claims a *path*
(`<ws://+/cluster/#>`) instead of an *authority*, so it can never see peer data traffic. The peer space must
claim the authority and nothing else.

Three hard requirements fall out:

1. **One canonical self-authority** (`/sys/mach/host`, operation 1) so `own(...)` is total. `0.0.0.0` must alias.
2. **One peer client with a correlation table, timeouts, bounded queues, and liveness** (4.2, 4.3). This is the
   only genuinely new component in the whole story.
3. **Generalize the Remote Console** (§1.5) rather than starting over. `wsclient` + `mtron_ws` + `send_recv` is a
   working round trip today; the work is to make it *concurrent, bounded, and addressed by authority* instead of
   configured by hand at a console.

---

## 6. Proposed layering

```
motifs        map-reduce · BSP · agent message passing · bulk scatter-gather      ← mtron, no new Java
              ────────────────────────────────────────────────────────────────
collectives   scatter(coeff) · gather · broadcast · barrier                       ← coefficient rules
              ────────────────────────────────────────────────────────────────
work unit     monad = lst(obj, inst, state, code)                exists
              ────────────────────────────────────────────────────────────────
machine       machine::T = (instset, compiler, processor)        exists + instset handshake  ← NEW (small)
              ────────────────────────────────────────────────────────────────
peer          authority → client (correlated, bounded, live)     ← NEW (the real work)
              ────────────────────────────────────────────────────────────────
boundary      own(x) · localize(x) · resolve(*x) · reference(x->y)   half exists; localize is NEW
              ────────────────────────────────────────────────────────────────
address       scheme://authority/path                            exists
```

### The open semantic decision

`docs/design/webspace.md` §0.5 already names the one hole it could not close:

> *resolve-vs-reference at the transport boundary is genuinely unmade.*

For distribution this is **the** decision, not a detail. When a value crosses a boundary, is it a **copy** or a
**pointer**? And metatron is unusually well placed to answer it well, because `!*vid` auto-pointers are
*syntactically* references: a read that returns `!*ws://hostB:8555/usr/x` is a **portable** reference — the
authority makes it globally resolvable, unlike a bare `!*/usr/x`, which would dangle.

Recommended rule: **the wire carries a reference when the value has a vid whose authority is preserved; it
carries a value when the read is explicit (`*x` with a `?value` marker) or when the value has no vid.** This is
what makes "move code to data" cheap and agent message-passing possible — and it must be decided *before* the
serializer is touched, because it changes what a remote read returns.

---

## 7. Staged plan

**Stage 0 — make the address complete (small, unconditional).**
Restore `fURI.localize()`. Establish one self-authority (`/sys/mach/host`), with `0.0.0.0` aliasing. Make the
`getSpaceFor` tie-break explicit rather than incidental (§4.5). *Testable with no network at all.*

**Stage 1 — one peer, one request, correlated.**
Build `peerSpace` + a client with a request-id → `FutureObj` table, a per-request timeout resolving to
`fail(...)`, and a bounded outbound queue. Splice `peerRegistry.spaceFor(authority)` into `getSpaceFor` — which
is also the fix for the capture failure (§4.5): a peer space must claim an **authority** (`ws://host:port/#`),
not a path. Then: `*<ws://hostB:8555/usr/x>` and `x -> <ws://hostB:8555/usr/y>` work across two real JVMs.
*This is the first honest test: two processes, two VMs, no shared Router.* The Remote Console (§1.5) is the
acceptance walkthrough — it should keep working, and then work concurrently.

**Stage 2 — revive the suite honestly.**
Un-`@Disable` `clstrSpaceTest` / `SampleDistributedMachineTest` and **delete `LocalNode`'s shared-Router
simulation** — it cannot fail the way reality fails (§4.4). Replace it with forked-JVM nodes (Tier 2, already
sketched in `AbstractDistributedMetatronTest`'s javadoc) and rewrite
`docs/design/distributed-testing-framework.md` to describe what actually exists.

**Stage 3 — machine handshake.**
Exchange instset digests before shipping code; fail closed on mismatch. This is what makes operation (5) safe
(§4.1).

**Stage 4 — collectives.**
`scatter`/`gather` over peer queues, driven by the dom coefficient as it already is locally. Replace
`ListMonad`'s `ArrayList.removeFirst` (`:60-65`) with a `Deque`, and evaluate `RunningMonads` (`:56-87`) as the
coalescing queue — both are prerequisites for fan-out, not optimisations.

**Stage 5 — motifs.**
Only now write map-reduce / BSP / agent patterns as mtron — and only if Stage 4 does not already make them
one-liners.

---

## 8. What to *not* build

- **A distributed scheduler.** The `running`/`barriers`/`halted` triple is the scheduler; the topology is the
  coefficient.
- **A new transport.** `ws`/`http` + `mtron_ws`/`mtron_http` + REST verbs already terminate in the Router.
- **A `MapReduceExecutor` / `BSPExecutor` class.** If a motif needs a class, the coefficient algebra is
  incomplete. (These names appear in Hindsight memory as if real; they are not in the codebase.)
- **A shared-Router "cluster".** It tests nothing that matters.
- **Compiled-code shipping.** The serializer proves it impossible; ship source and negotiate the machine.

---

## Appendix — evidence index

| claim | witness |
|---|---|
| Router is the read/write chokepoint | `Router.java:81-101`; `BasicRouter.java:307-353` |
| Router is a Space; spaces self-register | `Router.java:44`; `AbstractSpace.java:60-61` |
| most-specific pattern wins; `#` sorts last | `BasicRouter.java:253-269`; `AbstractfURI.java:278-292` |
| authority participates in matching | `AbstractfURI.java:350-359`, `:442-445`; `fURI.java:176`, `:276` |
| `localize()` was deleted | grep: no match in `furi/`; referenced `BasicRouter.java:313-314`, `:338` |
| read/write insts are `*x` and `x->y` | `Obj.java:1349`, `:1425`, `:1428-1431`, `:1437` |
| monad is `lst(obj, inst, state, code)` | `BasicStatefulMonad.java:72-74` |
| coefficient drives scatter/barrier | `Inst.java:364-374`; `SwarmProcessor.java:459-475`, `:335-411` |
| processor is a VirtualThread | `SwarmProcessor.java:74`; `mThread.java:39-128` |
| distributed triple, stated in code | `MonadProcessor.java:24-33` |
| machine = (instset, compiler, processor) | `machInstSet.java:216-223`; `Machine.java:100-102` |
| pool is URI-addressable | `ThreadExecutor.java:52-90` |
| peer semantics documented in the type | `machInstSet.java:254-264` |
| prototype dispatch | `clstrSpace.java:125-167` |
| shelved Router branches | `BasicRouter.java:312-314`, `:337-340` |
| resolved insts are non-portable | `ObjmtronSerializer.java:353-363` |
| `sendRecv` is a one-shot handler swap | `WebSocketObj.java:141-143`, `:159-185` |
| `startDiscovery` does nothing | `clstrSpace.java:97-121` |
| `take()` is O(n) on ArrayList | `ListMonad.java:60-65` |
| unused coalescing queue | `RunningMonads.java:56-87` |
| `httpSpace` client/server conflation | `httpSpace.java:423-493` (read), `:504-540` (write); `webspace.md` §8.6 |
| REST verbs → Router | `web_httpHandler.java:245-383` |
| distributed tests disabled | `clstrSpaceTest.java:54`; `DistributedMachineEvaluationTest.java:41`; `SampleDistributedMachineTest.java:39` |
| Tier 1 shares the Router | `LocalNode.java:189-205`; `clstrSpaceTest.java:134-136` |
| stale testing doc | `docs/design/distributed-testing-framework.md:26-31` vs `AbstractDistributedMetatronTest.java` |
| the open semantic hole | `docs/design/webspace.md` §0.5 |
| the published three-machine triptych | `docs/website/adoc/index.adoc:229-254` |
| fURI host = "physical location (authority)" | `docs/website/adoc/start.adoc:245` |
| the one working cross-host capability | `docs/website/adoc/web.adoc:121-146` |
| cluster mounted in dev only, and by path not authority | `boot/dev.mtron:71-74`; contrast `boot.mtron` |
| compile-on-one-node, execute-on-another | `docs/design/compilation-execution-strategy.md:150-157`, `:274` (self-declared not implemented, `:3`) |
| phantom `meta::` space and `boot/peers` | `docs/design/training/02-language/02-metatron-environment.md:111-117`; `.../02-examples/02-advanced-patterns.md:49-78` |
| phantom `MServer` / `MClient` / `isa.mach.type.net` | `docs/design/ai/mcp-mserver-integration-plan.md:11-22`; false "✅ Implemented" at `ai/README.md:84` |
| phantom `OLTP`/`OLAP`/`OLRP` machine classes | `index.adoc:240-254`; `SWEEP_NOTES_STREAM_RING_THEORY.md:215-217` |
| `SwarmMachine` is a stale name | `compilation-execution-strategy.md:6,148` vs `SwarmProcessor.java:74` |
| unrelated remote features (not clustering) | `dckrspace-mtron.md:46,347`; `grph.adoc:82` |
