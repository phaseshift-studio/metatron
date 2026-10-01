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

> **Deleted.** `clstrSpace` and its whole framework are gone (see §10). This section is kept as the record of
> what was there and why its intent was right — the intent now lives in the Router guard (§5).

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

### 1.7 The monad is a stored-program machine — there is no continuation stack, and it does not need one

§1.6 gave the queues; this is the unit that travels through them.
A `StatefulMonad` is `lst(obj, inst, state, code)` (`BasicStatefulMonad.java:72-74`), and the fourth slot is the
**whole code**. `next()` passes that code through unchanged, advancing only the instruction
(`StatefulMonad.java:104`):

```java
return this.jvm(lst(CommonUtil.arrayList(obj, this.code().nextInst(this.inst()), this.state(), this.code())));
```

And `Code.nextInst` is a program-counter advance (`Code.java:68-76`):

```java
default Inst nextInst(final Inst inst) {
    if (inst.isNoObj()) return noobj();
    int i = Integer.parseInt(inst.vid().toString()) + 1;   // inst.vid() IS the address
    for (final Inst in : this.jvm())
        if (Integer.parseInt(in.vid().toString()) == i) return in;
    return noobj();                                        // past the end == halt
}
```

So: **`inst.vid()` is the program counter, and halting is `nextInst` returning `noobj`**
(`Monad.halted()` is `inst().isNoObj()`, `Monad.java:38-40`). The step is (`StatefulMonad.java:291-320`):

```java
apply():
    if (halted()) return this;                              // PC ran off the end
    input  = inst.tid().hasQ(MONAD_IN)  ? component(in) : obj()   // read lens
    result = Processor.Helper.apply(input, this.inst())
    if (inst.tid().hasQ(MONAD_OUT) && out == "+") return result   // terminal: no successor
    return this.next(result)                                // advance the PC
```

**This is the opposite of a stack machine, and that is exactly why a monad is shippable.** A stack machine needs
a return-address stack because the program lives elsewhere and a frame must record where to resume. A metatron
monad carries its own `code` and its own PC, so **resumption is always recoverable from the monad alone.** There
is nothing to reconstruct — which is a large part of why the unit of work is portable (§4.1's only blocker is
that a *resolved* inst renders as `{lambda@…}`; the structure is already self-contained).

**The one real stack is `state/loop`** — and it is not a return stack. `state` is a `Rec`, and it holds:

| state field | what it is |
|---|---|
| `loop` | a **stack of loop counters** — `loopStack()`, `loop()`, `incrLoop()`, `pushLoop()`, `popLoop()` (`StatefulMonad.java:135-169`) |
| `path` | the **accreted walked path** `[morph, obj, morph, obj, …]`, opt-in via the `?path` code flag (`accruePath`, `:130-132`) |
| `loopback` | a boolean continuation marker for `repeat` |

`pushLoop`/`popLoop` exist only so nested `repeat` invocations each get their own counter — the sole nesting the
flat instruction chain has.

**Aggregation is therefore not on the monad at all — it is on the processor**, in two forms, and both already
exist:

| form | mechanism | code |
|---|---|---|
| **atomic result unit** ("iterated out") | a terminal monad's obj is fed to `onHalt`, which appends to the processor's `HALTED` (a `MOBjs`); `RESULT` is an auto-pointer to it | `SwarmProcessor.java:182-190`, `:356-363`; `:102` |
| **final combine / reduction** | a `gather` inst (`isGather()` = dom coefficient max is `null`) gets its **own barrier monad** in a second queue; data monads append their objs to it, and it is applied **once** | `Inst.java:364-369`; `SwarmProcessor.java:257-261`, `:378-411`; `Processor.Helper.apply(barrier.obj(), barrier.inst())` at `:383` |

The `HALTED` collection is an `Objs` whose **tid coefficient is its multiplicity** (`int{4}::T`), and `objs(...)`
collapses to `noobj` when empty and to the element itself when single — so "one result" and "many results" are
one code path on both the Java and mtron sides.

**Two consequences for distribution:**

1. **Because aggregation is ambient (processor-owned) rather than carried (monad-owned), a monad cannot actually
   leave the machine and come back on its own.** Only its obj's *provenance* leaves. That is fine for §9's
   option A (distribute the read, fold locally) and is precisely why §9's in-flight reservation is required for
   option B: a returning result must be re-admitted to *this* processor's `running` or `barriers`, and nothing
   on the remote side can do that.
2. **The container for carried aggregation already exists, and it costs no arity.** `state` is a URI-keyed `Rec`
   and `component(fURI)` is a read lens that defaults to `this.state().at(uri(field))` for any unrecognised
   field (`StatefulMonad.java:210-228`), with `static StatefulMonad of(Obj contents)` as its inverse
   (`:236`) — documented as *"the only way a `monad_in=+` lens hands an instruction the monad's parts (as a plain
   list) without exposing the PCMonad."* So `state/origin`, `state/request`, `state/shard` are ordinary state
   fields, readable by URI and round-trippable through a plain `lst`. **A "where to return to" record is a state
   field, not a new monad slot.**

In short: the answer to "does a migrating monad carry a stack of where to go when it halts" is **no — and the
design choice it exposes is *ambient* aggregation (today, the processor's queues) versus *carried* aggregation
(`state/<field>`, for which the machinery is already in place).** The monad needs nothing new to hold a return
address; the processor needs a way to re-admit it.

#### If you carry it: `state/home`

A carried return address is the right move, and the codebase already has the exact pattern — on **threads**, not
monads. `AbstractThread`'s constructor records its spawner as `SOURCE` (`AbstractThread.java:104-112`):

```java
if (!jvm.containsKey(uri(SOURCE))) {
    final AbstractThread parent = BootLoader.CURRENT_THREAD.get();
    if (null != parent && null != parent.vid())
        jvm.put(uri(SOURCE), auto_from_(parent.vid()).tryToInst());
}
```

with `source()` and `sourceVid()` (`= Obj.Helper.getAutoPointer(source())`, `:142-155`) as the accessors. So the
precedent says **three** things, two of which correct the bare form of the proposal:

1. **Not a bare `uri::T` — an auto-pointer.** `auto_from_(vid).tryToInst()` is a lazy dereference resolving
   through the Router (`AUTO_FROM_INST_TID` → `Router.readFromSpace(...).autoResolve(lhs)`, `Obj.java:1349`), and
   `getAutoPointer` is the unwrap. That is exactly the semantics wanted: *go home and append*.
2. **It must be absolute — authority-bearing — or it dangles.** A bare `home => !*/sys/processor/17` reads, on
   the remote, as *the remote's own* `/sys/processor/17`, and the halt lands in the wrong VM with no error. This
   is §6's rule ("a reference is portable iff it keeps the authority") in its most concrete form, and it is the
   single easiest way to get this wrong.
3. **Name it `home`; do not reuse `source`.** `source` already carries two established meanings — the spawn
   parent on threads (`:104-112`) and *derivation provenance* in the claims graph (`llmInstSet.java:333`:
   *"message vids and/or external uris the claim derives from"*). `home` is a **destination**, not a derivation.
   A third meaning would be an overload, so this is a case where a new token is justified.

**One field is not enough — a correlation id is forced, and §4.1 is why.** The shipped code is *source*, so the
remote **recompiles** it and inst vids are regenerated. A returning monad therefore **cannot be matched to its
barrier by `(code, inst)` identity** — those addresses do not survive the trip. The processor vid identifies the
*run* (each run gets a clone with a freshly minted vid, `/sys/processor/<n>` — `Machine.java:150-155`,
`SwarmProcessor.java:95`, and one processor runs one code at a time), but it cannot deduplicate a retry. So
carried aggregation needs `home` **and** a request id — the same id that §9 item 3 needs for exactly-once.

**Gate it, and the precedent is `?path`.** Per-monad state is hot-path cost, and `split` mints monads by the
thousand. The existing answer is a code-level flag: `SwarmProcessor.computesPath(code)` scans the compiled code
for an inst reading `monad_in=state/path`, and only then tags the code `?path`; `StatefulMonad.next` accrues the
path only when `code.tid().hasQ(PATH)` (`SwarmProcessor.java:248-251`, `:269-271`; `StatefulMonad.java:109`).
**A local swarm pays nothing.** `home` should ride the same gate — set only for a run that is actually
distributed. Note the prerequisite: `computesPath` hardcodes the string `"path"`, so it needs generalizing to a
`computesState(code, field)` before it can gate a second field.

**And one piece is free:** because `state` is a URI-keyed `Rec` and `component(fURI)` falls through to
`this.state().at(uri(field))` for any unrecognised name (`StatefulMonad.java:210-228`), **`monad_in=state/home`
already reads correctly with no change to the lens.**

Finally, put it on the **monad**, not in a transport envelope. An envelope is cheaper and works for a one-hop
dispatch, but the moment a remote monad scatters onward the envelope is gone and the grandchild cannot find its
way home. On the monad, multi-hop composes — which is the whole reason to carry rather than rely on the ambient
queue.

### 1.8 Threads and machines are already URI-addressable and lifecycle-managed

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
| 4 | **bind a peer** | `peer` on a cluster space | exists as a rec field, no live client | new `Peer` registry + client (§5) |
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

> **Resolved by deletion.** `clstrSpace`, `LocalNode`/`LocalCluster` and the `@Disabled` suite are gone. The
> fragility it names was real and is the reason the new guard delegates to `Router.Helper.sameAuthority`
> (alias-aware: `0.0.0.0`/`127.0.0.1`/`localhost`/`::1` are one service) instead of pattern-testing two
> authority strings. See §5.

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
prototype. Verified empirically against a live VM (`vid.test(pattern)` — the direction `getSpaceFor` uses, via
the `.has()` inst, `Uri.java:337`):

| probe | result | what it proves |
|---|---|---|
| `<ws://localhost:21001/usr/x>.has(<ws://#>)` | matches | `#` in `<ws://#>` is the **host** wildcard (it short-circuits at `AbstractfURI.java:354-355`), not a path wildcard |
| `<ws://localhost:21001/usr/x>.has(<ws://+/cluster/#>)` | **no match** | **the capture failure** |
| `<ws://localhost:21001/cluster/x>.has(<ws://+/cluster/#>)` | matches | the cluster space is reachable only via the reserved `/cluster/` path |
| `<ws://localhost:21001/usr/x>.has(<ws://localhost:21001/#>)` | matches | an authority-bearing pattern *does* capture that authority |
| `</usr/x>.has(<ws://localhost:21001/#>)` | **no match** | an authority-bearing pattern **cannot** capture a local authority-less path |
| `<ws://localhost:21001/usr/x>.has(<ws://localhost:9999/#>)` | **no match** | `host:port` is the peer identity; a different port is a different peer |
| `<http://example.com/x>.has(<http://#>)` | matches | `httpSpace` is *already* a wildcard-authority proxying space (§5) |

The mounted-space inventory on the same live VM confirms the other half: **every real space registers an
authority-less path pattern** — `memspace::[pattern=>/usr/#]`, `tblespace::[pattern=>/usr/dr/#]`,
`fsspace::[pattern=>mfs:#]`, `wsspace::[pattern=>ws://#]`, `httpspace::[pattern=>http://#]`.

So peer *data* traffic never reaches the dispatch code in `clstrSpace.readWrite` (`:125-156`) at all. The cluster
space is reachable only by an operator who already knows to address `/cluster/`, and the authority comparison
inside it is consequently dead code in practice.

**This is the finding that rules out the space pattern as the dispatch vehicle.** Because `wsspace` claims
`ws://#`, *no* path-shaped pattern the cluster space can adopt will capture peer data traffic — and a pattern
that looks like an authority (`ws://host:port/#`) would collide with the existing wildcard claimant, decided only
by `fURI.compareTo`, which is asymmetric and not a total order (`AbstractfURI.java:278-292`). Rather than
re-engineer pattern matching to carry authority, §5 puts the check at the Router, where the authority is already
in hand and no pattern is consulted at all. The capture failure is not a bug to fix — it is the reason the space
is the wrong vehicle.

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
| `docs/design/distributed-testing-framework.md` documented `setupDistributedEnvironment`, `checkDistributedOperation`, `verifyCrossHostRouting`, `testDistributedMachineEvaluation` and `getHostFromUri` | none ever existed; `AbstractDistributedMetatronTest` had `createCluster`, `assertFullTopology`, `assertPeerCount`, `assertSendCount`, `waitForVisible`, `assertEventuallyEquals`. **Resolved: the framework and that doc are both deleted — see §10.** |
| `docs/design/ai/mcp-mserver-integration-plan.md:11-22,180` builds on a package `isa.mach.type.net` with `MServer`, `MConnection`, `MClient`; `:453` "Status: Planning phase" | the package does not exist; the real MCP server is `isa/web/type/mcpServer.java` + `mcp_wsHandler` / `mcp_httpHandler` / `mcp_stdioHandler` |
| `docs/design/ai/README.md:84` marks that plan "**✅ Implemented**"; `:199-205` lists "Files Created" under `isa/mach/type/net/…`; `:276` "**MServer**: WebSocket server for distributed computing" | all false as to those paths |
| `docs/design/training/02-language/02-metatron-environment.md:111-117` and `docs/design/training/02-examples/02-advanced-patterns.md:49-78` document a `meta::` distributed/cluster space with `peers => !*boot/peers` | there is no `metaSpace`; there is no `boot/peers` file or boot path; the real key is singular `peer` on `clstrspace`, and `boot/dev.mtron:71-74` is the only profile that sets it |
| `index.adoc:240-254` and `SWEEP_NOTES_STREAM_RING_THEORY.md:127-135,215-217` advertise `OLTPMachine` / `OLAPMachine` / `OLRPMachine` and a vector/BSP machine | no such classes; there is no VECTOR framework; the only engine is the local barrier-based `SwarmProcessor` |
| `compilation-execution-strategy.md:6,148,218,249` refers to `SwarmMachine` | stale name — the class is `SwarmProcessor` (`SwarmProcessor.java:74`) |
| Hindsight's `Machine architecture` page claims concrete `SwarmExecutor`, `MapReduceExecutor` and `BSPExecutor` classes | **none exist in `src/main/java`** (verified by grep). They are aspirational names. |

Two of these are worth acting on rather than merely noting:

- **`distributed-testing-framework.md` is deleted.** It specified an API that never existed. It is replaced by §10
  below, which describes the harness that does.
- **`docs/design/ai/README.md:84`'s "✅ Implemented" is a false status** on a plan whose foundational package is
  absent.

Also worth recording: the only *other* genuinely remote features in the tree are **unrelated to clustering** —
`dckrSpace` over a TCP Docker daemon (`dckrspace-mtron.md:46,347`) and TinkerPop's `DriverRemoteConnection`
(`grph.adoc:82`). Both are remote *service access*, not metatron-to-metatron distribution. And "federation" is
used for two different things with no disambiguation anywhere: cross-**harness** memory on one VM
(`memory-landscape-review.md:285-314` G3, `memory-server-architecture.md:357-381` M5) — explicitly
"unimplemented" — and host clustering. A reader searching "federat" will conflate them.

---

## 5. Where the boundary lives: the Router, as a guard *before* `getSpaceFor`

An earlier draft of this section put the peer registry **inside `getSpaceFor`**, on the reasoning that "everything
addressable is a space, and `getSpaceFor` is the single selection point". That was wrong, and the reason is
concrete rather than stylistic:

> **`getSpaceFor` is a widely-called local primitive, not an I/O entry point.**

It has ~20 call sites outside the Router, in two classes:

- **Compile-time rewrite rules.** `CommonRewrites` calls it 18 times (`:141, :334, :490, :764, :1052, :1199,
  :1319, :1459, :1598, :1711, :1813, :1914, :2018, :2124`) plus `RewriteBuilder.java:285`. **A compiler pass must
  never open a socket.**
- **Type-system structure.** `Obj.Helper.inInstSet` and `vidToTid` branch on
  `getSpaceFor(tid) instanceof InstSet` (`Obj.java:914, :928`); `Uri.Helper.readChildUris` enumerates a directory
  spine via `getSpaceFor(base).directReader()` (`Uri.java:453-467`).

A peer proxy returned from any of those would (a) fail an `instanceof InstSet` check that type resolution depends
on, and (b) perform network I/O inside a rewrite and inside URI enumeration. So `getSpaceFor` **keeps its precise
meaning** — "which space *of mine* serves this URI" — and authority dispatch is a guard that runs *before* it.

**Recommendation: Router-level.** Which is also where the prototype put it (`BasicRouter.java:312-314`, `:337-340`).

### The shape

```
BasicRouter:
    peer      : authority -> peer::T   // DECLARED roster (durable rec slot) — membership only
    selfAuth  : Set<fURI>              // from /sys/mach/host, 0.0.0.0 aliased
    clients   : authority -> Peer      // TRANSIENT live sockets — not a pattern claimant, not in spaces()

    read(vid):
        if (vid.hasAuthority()) {
            auth = vid.authority()
            if (own(auth))            return this.read(vid.localize())            // mine → resolve locally
            cfg  = peerConfig(auth)   // this machine → parent machine → … (declared only)
            if (cfg != null)          return clients.getOrConnect(cfg)
                                                    .send_recv(from(vid.localize()))
            // else fall through — a wildcard space may still claim it (httpspace, wsspace)
        }
        ... existing body (alignPrefix → getSpaceFor → space.read) ...

    write(vid, obj):  symmetric, delegating to_(vid.localize()) / ref_(obj)@vid.localize()
    readStream/writeStream: the same guard, factored into one predicate
```

Three funnels, one policy: `read`, `write`, and the stream pair. All three already exist
(`Router.java:70-101`; `BasicRouter.java:307`, `:336`).

### The index: declared membership, transient connections

"Is the peer index durable, or transient — built solely from the URIs that pass by the Router?" is really **three**
questions, and separating them is what makes the answer obvious:

| question | answer | why |
|---|---|---|
| **Membership** — which authorities am I allowed to talk to *at all*? | **durable, declared** — never emergent | a URI must not be able to create a peer |
| **Connection** — is there a live socket to that peer *right now*? | **transient, lazy, pooled** | a socket is a resource: it expires, fails, and must be bounded |
| **Scope** — how long does a membership last? | **per-machine, walking up the parent chain** | machine-as-boundary (below) |

**Emergent membership must be rejected**, and the reason is not tidiness — it is fail-open. Under a
"first URI to mention a foreign authority creates a peer" rule, `*<ws://anything:1234/x>` causes an **outbound
dial to an arbitrary host**. A typo becomes indistinguishable from a peer, and any value that reaches the Router
becomes network authority. Declared membership is fail-closed: a host you did not name is simply not a peer, and
the read falls through to the wildcard spaces (or to `no active space supports pattern`).

**The codebase's own convention agrees.** Every Router index is a *declared, introspectable rec* populated by
**registration**, not observation: `smallToBigRoutes` (`small_route`), `bigToSmallRoutes` (`big_route`) and
`prefixToVID` are `@JRecElement ObjectMap`s on `BasicRouter` (`:62-66`), and `prefixToVID` is filled by
`Router.registerPrefix` called from `InstSet.setup` (`InstSet.java:150`) at construction time. Nothing in the
Router is learned by watching traffic. Even live runtime state follows the convention — `ThreadExecutor` exposes
running threads as a rec at `/sys/thread/{run,stop}` (`ThreadExecutor.java:52-90`), so the *transient* index has
a URI too, it is just a different slot.

And `clstrSpace`'s own documented peer shape already anticipates the split
(`clstrSpace.java:58`) — config and status side by side, keyed by authority:

```
peer => [xyz:1234 => [host    => ws://xyz:1234/mtron,   -- declared: membership + transport
                      handler => mtron_ws,
                      state   => OK,                     -- transient: runtime status
                      stat    => [in => bytes::0.0, out => bytes:0.0]]]
```

So the shape is:

```
declared  (durable)   /sys/mach/peer      authority -> peer::T      host, handler, timeout, q procs
                      — read by the guard; walked up the machine parent chain like `instset` and type lookup
runtime   (transient) /sys/peer/client    authority -> Peer         live socket, correlation table, in-flight n
                      — lazily created on first dispatch to a *member*, LRU-bounded, idle-expiring
```

Three consequences worth stating plainly:

- **The URIs that pass by the Router *do* drive the transient index — they just do not create membership.** First
  contact with a declared member opens the connection; first contact with an undeclared authority opens nothing.
  That is exactly the split you are feeling for, made explicit.
- **Membership is per-machine; connections are global.** Because `AbstractMachine extends BasicRouter`, each
  machine can declare its own peer set — a BSP machine for job J names its workers, a different machine names a
  different cluster. That scoping needs no new concept: it is the existing machine-as-boundary rule, and
  connections stay shared at the root because a socket to host B is a socket to host B, whoever is using it.
  Sharing the transport does not leak membership, because each machine looks up its *own* roster.
- **Discovery is a protocol, not a side effect of addressing.** Membership grows by (a) being declared, or (b)
  being *learned from a trusted peer* over a handshake. (b) is where the stubbed `startDiscovery`
  (`clstrSpace.java:97-121`) actually belongs, and where gossip/consensus would later plug in — note it learned
  nothing, because it was never clear that learning is a protocol.

**One more requirement the durable index creates:** the *inbound* side needs the same gate. Today `wsspace`
accepts any connection that resolves a route — `createServer` looks up the route table and constructs a handler
with no membership check at all (`wsSpace.java:264-306`). A declared roster is also what an inbound handshake
would check against, which means the index is not merely an outbound dispatch table.

### Why this is the right seam

- **It is the chokepoint that already exists.** `Router.readFromSpace`/`writeToSpace` (`Router.java:81-101`) is
  the language's only read/write funnel — `*x` (`Obj.java:1349`), `x->y` (`Obj.java:1425`) and `x.ref(y)`
  (`Obj.java:1437`) all land there. One guard covers the whole language.
- **It is loop-safe, twice over.** The receiving side gets an authority-less vid, so it cannot re-dispatch. And if
  a peer forgets to `localize()`, the receiver's `own(auth)` catches the round trip and resolves locally.
  `own()` is a **loop guard**, not a nicety.
- **`http://example.com` still works.** Not own, not a registered peer → falls through → `httpspace` claims
  `http://#` (host wildcard) → Jsoup fetch. A stranger `ws://` host → `wsspace` → `noobj`. **Fail-closed:** you
  only ever talk to hosts you were configured with. (This is the case that would have broken a naive
  "any foreign authority is a metatron peer" rule.)
- **The capture failure and the tie-break vanish.** With no new pattern claimants, `wsspace` (`ws://#`),
  `LocalNode`'s patterns and the `min(pattern)` contest are all untouched. §4.5's findings were **artifacts of
  choosing the space-as-claimant vehicle** — not independent defects.
- **Self-address and peer-address are the same operation modulo transport.** Both branches strip the authority
  with `localize()`; only the second one uses a socket. One predicate, one stripping, two transports (loopback and
  socket). That symmetry is the strongest signal the seam is right — and it is what makes `localize()` the
  load-bearing operation rather than a convenience.
- **…but only when the authority is decoration.** Implemented, then corrected against reality (§10): an
  own-authority vid is localized *only if the authority-free path still resolves to a space*. A space whose pattern
  names a host — `wsspace` on `ws://#`, `httpspace` on `http://#` — is **serving** that address and keeps live
  sessions at `ws://localhost:PORT/<route>/<n>`; localizing one of those strips the authority, hands the vid to a
  different space, and the write vanishes silently. The symptom is not an error but a socket that is never
  answered (MCP handshakes dying at 1006 after a 15s timeout). Any rule shaped "an authority we own is always ours
  to strip" is wrong for exactly this case.
- **Hot-path cost is nil.** `vid.hasAuthority()` is `null != host() && -1 != port()` (`fURI.java:176-178`) — two
  checks on an already-materialized fURI, short-circuiting *before* the space scan.

### Transport as a service — and why the guard still stays

The natural objection to the guard is: *if the Router already dispatches by pattern to spaces, why not just hand
`ws://remote.com:1234/…` to `wsspace` and let it handle the transport?* Generalizing: mqtt, http, `file://` can
each serve as a transport. **This is right, it is already the design, and the guard is still required** — because
the two mechanisms answer orthogonal questions.

**Scheme already selects the space.** Every one of these is live:

| scheme / pattern | space | role |
|---|---|---|
| `ws://#` | `wsspace` | WebSocket **server** (host wildcard) |
| `http://#` | `httpspace` | HTTP **server** + Jsoup fetch (host wildcard) |
| `z2m:#` | `mqttSpace` | MQTT bridge, routed `z2m: => z2m/` |
| `mfs:#`, `local:#`, `root:#` | `fsSpace` | filesystem, routed `mfs: => /home/…` |
| `docker:#` | `dckrSpace` | Docker socket |

Note there are **two spellings** of the same idea, and a client registry should treat them uniformly:
`scheme://#` (a **host wildcard** — matches any authority) and `scheme:#` (a **scheme-prefixed namespace**,
localized through the space's `route` table). `webspace.md` §8.6 already prescribes where this goes: *"carriers as
the last hop"*, with route matching and protocol selection owned by the carrier.

**But scheme tells you nothing about authority or membership.**

1. **`ws://#` is a host wildcard on a *server*.** `wsspace`'s claim is about *its own listening address space*
   (`ws://0.0.0.0:8555`) — it is not a client of `ws://remote.com`. Hand it `ws://remote.com:1234/usr/x` today and
   its `directReader` looks in the **local session cache** and returns `noobj`. **Silently wrong.** The guard is
   what makes a remote `ws://` read possible at all.
2. **Same scheme, opposite direction.** `ws://0.0.0.0:8555` is my listener; `ws://hostB:8555` is a peer. Only
   `own(auth)` separates them.
3. **Policy belongs to the machine; protocol belongs to the scheme.** Membership is scoped (`peer` walks the
   machine parent chain, §"The index"); transport is global per scheme. Keeping them apart is what lets a BSP
   machine scope its workers without reconfiguring the WebSocket transport.

So the layering is a *composition*, and the Router guard stays tiny because it delegates upward:

```
Peer            authority-keyed: membership, correlation table, timeouts, bounded queues
  └── surface    what the peer speaks: mtron_ws / mcp / …                    ← the roster's `handler`
        └── transport   ws::client::T / http::client::T / mqtt::client::T    ← the roster's `host` URI scheme
```

**The transport is chosen by the *roster entry's* URI scheme, not the vid's.** `clstrSpace`'s peer shape already
encodes this decoupling (`clstrSpace.java:58`): the key is the authority, and the value carries `host => <ws://…>`
(transport + endpoint) and `handler => mtron_ws` (surface). A peer need not be reachable over the scheme you
happen to write.

**The gap this exposes: there are no client spaces.** `wsspace` and `httpspace` are *servers*. `webspace.md` §8.6
says the client half must split out into `http::client::T` / `ws::client::T`, and both types already exist as
declarations with no implementation — `HTTP_CLIENT_TYPE` throws `"http client not implemented"`
(`httpSpace.java:114-119`); `WS_CLIENT_TYPE` is the mostly-complete `WebSocketRecClient`. So *"let the scheme
space handle transport"* is not a smaller job than the `Peer` client — **it is the same job, registered per
scheme instead of hardcoded.** That is strictly better: adding mqtt/gRPC/ssh as a peer transport becomes
registering a client type, not editing the Router.

**One thing to keep straight: transport is not computation.** A transport space must not *compute* the
dereferenced URI — it must ship the request and let the **owner** compute it (host B's `memSpace` resolves
`/usr/x`). This is exactly the `httpSpace` disease in §4.6: it holds a local route table *and* does remote fetch,
its write path has the local branch commented out, and reads and writes to the same space are asymmetric. A
transport with a wildcard claim that substitutes its own computation for the owner's is how you get silent wrong
answers.

### Peers as mounted routes — the route table *is* the index

This is the strongest form of the design, and the mechanism is already written down. The `route` tables in
`drstynx.boot.mtron` show what a route value can actually be — and it is not just a path:

| route entry | value is | meaning |
|---|---|---|
| `wsspace`: `/mtron => mtron_ws` | a handler **type** | serve this path with that protocol engine |
| `wsspace`: `/drstynx => *dr.as(skill::T).as(mcp_server::T)` | a **code** | materialize the target per connection |
| `httpspace`: `/docker => docker:`, `/mfs => mfs:` | another space's **scheme** | mount that space's namespace here |
| `httpspace`: `/usr => /usr`, `/ => mfs:docs/website/` | another space's **path** | mount, or serve a root |
| `fsspace`: `mfs: => <~/software/metatron>` | the **backing store** | localize the scheme to a real path |
| `tblespace`/`dckrspace`/`vecspace`: `x: => <>` | **identity** | the external and internal addresses coincide |

So the route table is **already a cross-space mount table**. `/docker => docker:` is a mount; `/usr => /usr` is
a mount. Peers are the same move with a remote target.

**And the hand-off that makes it general is already written — commented out** (`AbstractSpace.java:64-70`):

```java
public Obj read(final fURI vid) {
    // LOG.warn("reading %s => %s", vid, Space.Helper.routeFromSpace(vid, this.routes()));
    /*final fURI routedVID = Space.Helper.routeFromSpace(vid, this.routes());
    if (!routedVID.test(this.pattern()))
        return Router.readFromSpace(routedVID);*/
    ...
```

That single `if` is the whole idea: **if the routed address leaves this space, hand it back to the Router.** A
route then stops meaning "where my backing store lives" and starts meaning "a mount point into another space" —
and a mount point into a *client* space is a peer.

**Why it is commented out, and what that requires.** `tblespace` (`/usr/dr/ => <>`), `dckrspace` (`docker: => <>`),
`vecspace` (`embed: => <>`) and `fsspace` use **identity** routes. For those, `routeFromSpace` strips the prefix
and yields a vid that *leaves* the space's pattern — so a global re-dispatch would bounce or loop. The hand-off
therefore has to be an **opt-in lane** on the route (as `wsSpace`/`httpSpace` already classify route values into
lanes: `MCP`, `CONSTRUCT`, `RETAG`, `HANDLER`, `WEB`, `ADDRESSED`), never an `AbstractSpace` default.

**It is also read-only today.** `AbstractSpace.write` has only a commented *log* (`:112-113`) — there is no route
hand-off on the write path at all. Making routes a symmetric peer mount means adding it.

#### What this gives that the Router guard does not

- **The index question from earlier is answered: the route table *is* the peer index.** Durable, declared,
  introspectable at a URI, per-space, and already walked for `routeFromSpace` on every read. Membership lives
  there; connections stay transient. No new structure — and it satisfies every constraint I argued membership
  must satisfy.
- **A remote namespace gets a local name.** `route => [/remote/dr => <ws://hostB:8555/usr/dr/>]` plus the
  hand-off means `*</remote/dr/x>` reaches host B. Aliasing, sharding and per-job namespaces all fall out of one
  mechanism, and the remote's authority never has to be spelled at the use site.
- **`routeToSpace` is the inverse, and it is the answer to the open semantic decision.** The "resolve vs
  reference at the transport boundary" hole (`webspace.md` §0.5), and the "re-own on the way back" gotcha from
  §5 — a remote read returns host B's *local* vid (`/usr/dr/x`), and `routeToSpace` is exactly the function that
  turns it back into the portable public name. **The resolve-vs-reference policy is a route table.**
- **Transport selection becomes a route value**, not a Router branch: the value's scheme (`ws://`, `http://`,
  `mqtt:`) names the client space, exactly as `/docker => docker:` names a space today.

#### What it does not do — the two things that remain

1. **A route is a rewrite, not an action.** It cannot *initiate* a connection. Something must still be the
   client, and that is the `ws::client::T` / `http::client::T` half that does not exist yet
   (`HTTP_CLIENT_TYPE` throws `"http client not implemented"`, `httpSpace.java:114-119`). Routes decide *where*;
   the client space does the *work*. The two are complements, not alternatives.
2. **`own(auth)` still needs an entry.** Reading `ws://localhost:8555/usr/x` (my own authority) would otherwise
   hit `wsspace`'s session cache. Under this design that is one more route — which is arguably the right answer:
   **self and peer become the same mechanism**, and the loop guard becomes a property of the route table rather
   than a special case in the Router.

### Convergence: what survives, and what is primary

Three placements have been considered in this section. To avoid leaving three competing designs, the parts that
**survive all of them** are:

1. **The authority is the routing key** (`scheme://host:port`).
2. **The boundary is one function** — `own` + `localize` + resolve/reference, applied at one place.
3. **Membership is declared, never emergent** (never derived from traffic).
4. **Transport is per-scheme, and the client halves do not exist yet.**
5. **Transport is not computation** — the owner computes, the transport moves.

What changed is only *where the dispatch is written down*, and the ordering of preference is now:

1. **Primary — a route lane on a client space.** `route` already exists on every space; the hand-off is already
   written (`AbstractSpace.java:67-69`); `routeToSpace` supplies the inverse; membership is the route table.
   This adds **no new concept**, which is the signal it is right.
2. **Fallback — the Router guard** (§"The shape"), for an authority that no space claims at all.
3. **Rejected — a pattern-claimant proxy space** (§4.5): it cannot capture peer data traffic, and it poisons
   `getSpaceFor` for ~20 local call sites.

### What a space still buys, and how to keep it

The space approach's genuine merit was affordances: `?docq`, `q` procs, `close()`, stats, reflection. Keep all of
them by making `Peer` a **space-shaped rec that is not a pattern claimant** — reached by authority from the
registry, listed under a peer slot for introspection, but never in `spaces()` and never returned by
`getSpaceFor`. The affordances are preserved; the ~20 local call sites are not poisoned.

### Gotchas to decide before writing code

1. **`hasAuthority()` requires an explicit port** — `null != host() && -1 != port()` (`fURI.java:176-178`). So
   `ws://hostB/usr/x` (host, no port) would **not** dispatch. Either require explicit ports in peer URIs, or gate
   on `hasHost()` instead.
2. **Peer identity is `host:port`** — a peer reached on a different port is a different peer (verified §4.5).
3. **`localize()` must re-own on the way back.** A remote read returns the *remote's* local vid (`/usr/x`), which
   is ambiguous about whose. The delegating Router should re-attach its own authority so the returned reference is
   the portable `!*ws://hostB:8555/usr/x` (see *The open semantic decision*).
4. **The `own(auth)` branch needs somewhere to land**: `read(vid.localize())` → `/usr/#` memSpace. That works
   **only because local spaces are authority-less** — which is the corollary below, now a consequence of the
   design rather than a workaround for it.
5. **Is the roster keyed by authority or by service URI?** `clstrSpace` keys by `host:port` (authority only), with
   the transport in the value. That implies one mtron endpoint per node — so `*<http://hostB:8555/x>` would ride
   the peer's **ws** transport, which is surprising. Keying by `scheme://host:port` instead is strictly more
   expressive (a node can expose mtron over ws *and* http as two roster entries) and less surprising; a vid whose
   scheme does not match any entry simply falls through to the scheme space. **Recommend the service URI.**

Remaining requirement: **one peer client** with a correlation table, timeouts resolving to `fail(...)`, bounded
queues, and liveness (4.2, 4.3). This is the only genuinely new component in the whole story. And
**generalize the Remote Console** (§1.5) rather than starting over — `wsclient` + `mtron_ws` + `send_recv` is a
working round trip today; the work is to make it concurrent, bounded, and addressed by authority instead of
configured by hand at a console.

#### Corollary: local spaces should stay authority-less — and one place currently isn't

Requirement 2 only holds if the two regimes are disjoint: **peers claim authorities; local spaces claim paths.**
Today they are *not* disjoint, because the Tier-1 test scaffolding registers a **local** `memSpace` under a
peer-shaped authority pattern (`LocalNode.java:117-120`):

```java
uri(PATTERN), uri("ws://localhost:" + port + "/#")
```

That is how one `Router` fakes a second host in-process.

Under Router-level dispatch this is no longer a **collision** — the guard strips the authority via `localize()`
before the space scan ever runs, so `own(auth)` never competes with a pattern. It is now simply **redundant and
misleading**: an authority-bearing local pattern asserts a claim the Router has already resolved, and it invites
the reader to think authority routing happens in `getSpaceFor` when it does not.

It should go for two reasons. First, `own(auth)` **supersedes it** — the self-addressed read it was faking
(`ws://localhost:<port>/usr/x` → that node's store) is now a first-class branch. Second, and independently, it is
a **shared-Router simulation that tests nothing that matters** (§4.4): its own comment concedes it "evaluates via
Router rather than live WebSocket", so it can never catch an authority, serialization or correlation bug.

In real distribution, host B's store is registered *on host B* as a plain `/usr/#` — exactly as every real space
on the live VM is (`memspace::[pattern=>/usr/#]`, `tblespace::[pattern=>/usr/dr/#]`, `fsspace::[pattern=>mfs:#]`).
I reach it as `ws://hostB:8555/usr/x`; the guard strips the authority and host B resolves `/usr/x` against its own
authority-less spaces.

**This is what makes `localize()` load-bearing rather than a convenience.** It is the operation that converts
"an address in the global space" into "an address in *that host's* space", and it is why the boundary is an
authority prefix and not a namespace merge.

#### The residual cost — now much smaller

A peer claims a namespace it **cannot enumerate**: unlike `memSpace` (`/usr/#`), `ws://hostB:8555/#` has no
listing. Under the earlier space-as-claimant design that distorted two Router predicates — `hasSpaceFor` would
answer true for a foreign namespace and `spaces()` would list it as local. **Under Router-level dispatch that cost
mostly disappears**: peers are not pattern claimants, so `hasSpaceFor`, `spaces()` and `getSpaceFor` keep answering
about *local* spaces only, and every one of the ~20 local call sites stays correct.

What remains is discoverability, and it is mild: a peer lives in a registry rather than in `spaces()`, so it needs
its own introspection slot (the `peer` rec on the cluster space, or a `/sys/space/peer` listing). That is a
visibility question, not a correctness one — which is the whole reason to prefer this seam.

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

**And the mechanism for it is already in the codebase: `routeToSpace`** (§5, "Peers as mounted routes"). A
remote read returns host B's *local* vid (`/usr/dr/x`), which is ambiguous about whose; `routeToSpace` is the
function that turns it back into the portable public name. So this "open decision" is not unmade so much as
unplaced: the policy is a route table, and the missing piece is applying `routeToSpace` to a returned vid at the
transport boundary.

---

## 7. Staged plan

### The first step, concretely

> **Done — see §10.** Implemented and green: two VMs, two OS processes, one socket. The findings that changed
> this plan (the roster belongs at `/sys/peer`; a broken roster fails *silently and looks correct*) are recorded
> there, and every item below stands as the rationale.

**Prove one value crosses between two real VMs, synchronously.** Not a framework, not collectives — one read and
one write, from a second OS process, asserted.

This is the first step because it is the only design claim here that can be **falsified cheaply**: *"a peer read
is an ordinary `*x`."* Every other decision in this document is downstream of it. And it is deliberately
**synchronous**, which means §9's pre-termination hazard cannot bite yet — the read inst blocks the monadic loop
until the data is in hand, so `running`/`barriers` are never momentarily empty mid-flight. Async dispatch and the
counting barrier must therefore land *together*, later, with a failing test to drive them.

**The edits** (all small; no new subsystem):

1. **Restore `fURI.localize()`** — strip the authority, keep the path (`ws://hostB:8555/usr/x` → `/usr/x`).
2. **One self-authority** — `/sys/mach/host`, with `0.0.0.0` aliasing (see trap 1).
3. **The guard in `BasicRouter.read`/`write`**, *before* `getSpaceFor`:
   `own(auth)` → `read(vid.localize())` / `write(vid.localize(), obj)`; declared peer →
   `client.sendRecv(from(vid.localize()), TIMEOUT)`; otherwise **fall through** to the existing body.
4. **A declared roster.** Not the route lane yet — a plain rec, but with the *final data shape* so moving it into
   a route slot later is a lookup change, not a rewrite: `<authority> => peer::[host => <ws://…/mtron>,
   handler => mtron_ws]`, mirroring `clstrSpace.java:58`.
5. **Reuse `WebSocketRecClient.sendRecv` as-is.** It is single-shot and racy (§4.2) — fine for exactly one request
   in flight, which is all this step allows. Pass the timeout overload so a dead peer fails instead of hanging.

**Definition of done** — four assertions, all from a *second process*:

- `*<ws://hostB:8555/usr/x>` returns host B's value. *(authority dispatch + `localize` + transport)*
- `x -> <ws://hostB:8555/usr/y>` is readable at `/usr/y` **on host B**, in host B's own store. *(the write
  direction, and proof the boundary strips correctly)*
- `*<http://example.com/>` still Jsoup-fetches. *(the fall-through: a foreign authority that is not a peer)*
- The Remote Console (§1.5) still works. *(no regression on the one capability already known good)*

**Trap 1 — `0.0.0.0` vs `localhost`.** The boot binds `ws://0.0.0.0:8555` (`boot/boot.mtron`), but a peer — and
the Remote Console — addresses `ws://localhost:8555`. If `own()` compares authority strings, `localhost:8555`
is *not* `0.0.0.0:8555`, so the guard forwards the request **to itself** and loops. `own()` must treat the port
plus a host-alias set (`0.0.0.0`, `127.0.0.1`, `localhost`, the host's own addresses) as one authority. This is
the single most likely cause of a mystifying failure in this step.

**Trap 2 — `hasAuthority()` requires an explicit port** (`fURI.java:176-178`). `ws://hostB/usr/x` — host, no
port — will **not** dispatch. Require explicit ports in the roster, or gate on `hasHost()`.

**Trap 3 — do not build on `LocalCluster`/`LocalNode`.** They share one Router (§4.4), so they *cannot* fail on
an authority, serialization or correlation bug — the three classes this step exists to catch. Use forked JVMs
(Tier 2), which `AbstractDistributedMetatronTest`'s javadoc already anticipates.

**Explicitly deferred:** the correlation table, pooling, liveness, route lanes and enumeration, `state/home`,
async dispatch, the counting barrier, collectives, and every motif. Each is additive on a proven transport, and
building any of them before this step means building on an unverified seam.

---

**Stage 0 — make the address complete (small, unconditional).**
Restore `fURI.localize()`. Establish one self-authority (`/sys/mach/host`), with `0.0.0.0` aliasing, and make
`own(authority)` total. Decide the `hasAuthority()` no-port gotcha (§5). *Testable with no network at all:* `own`,
`localize` and the guard's fall-through are pure functions of an fURI.

**Stage 1 — declared roster, one peer, one request, correlated.**
Express the roster as what it already is — a **route table** (§5, "Peers as mounted routes") — and build the
`Peer` client with a request-id → `FutureObj` table, a per-request timeout resolving to `fail(...)`, and a bounded
outbound queue. Enable the route hand-off as an **opt-in lane** on `read` (and add the missing one on `write`).
Add the Router guard **only as the fallback** for an authority no space claims — **before `getSpaceFor`, never
inside it** — with two branches: `own(auth)` → `read(vid.localize())`, and declared peer →
`send_recv(from(vid.localize()))`. **No membership from traffic.** Then: `*<ws://hostB:8555/usr/x>`,
`x -> <ws://hostB:8555/usr/y>`, and the aliased form `*</remote/dr/x>` all reach host B across two real JVMs.
*This is the first honest test: two processes, two VMs, no shared Router.* The Remote Console (§1.5) is the
acceptance walkthrough — it should keep working, and then work concurrently.

**Stage 2 — revive the suite honestly.** ✅ *done, §10.*
The in-JVM simulation (`clstrSpace`, `LocalCluster`, `LocalNode`, `AbstractDistributedMetatronTest`,
`DistributedTestUtils`, `DistributedMachineEvaluationTest`, `SampleDistributedMachineTest`, `clstrSpaceTest`,
the `clstrspace::` boot block, and the doc that described the API it never had) is **deleted**, replaced by
forked-JVM nodes under `studio.phaseshift.metatron.distributed`.

**Stage 3 — machine handshake.**
Exchange instset digests before shipping code; fail closed on mismatch. This is what makes operation (5) safe
(§4.1).

**Stage 4 — collectives, and the counting barrier first.**
Before any motif: fix the **pre-termination hazard** (§9). `runMonadicLoop` breaks when `running` and `barriers`
are both empty — sound synchronously, **a silent partial result** under async remote dispatch. The break must
also consult an outstanding-request count, with remote monads reserved before dispatch, and a timeout must
resolve to `fail(...)` rather than hanging (`WebSocketObj.java:141-143`). *This is the one defect in the story
that produces a wrong answer rather than an error, so it precedes everything else here.*
Then `scatter`/`gather` over peer queues, driven by the dom coefficient as it already is locally. Replace
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

## 9. Worked example — a sharded fold, traced

```mtron
*/data/set/+/+.has(name).select(neighbor/age => +20).sum()
```
```mtron
memspace::[ pattern => /data/#,
   route => [ /data/set/1/ => /data/set/1/,
              /data/set/2/ => <ws://remoteA:1234/data/set/2/>,
              /data/set/3/ => <ws://remoteB:1234/data/set/3/>]]
```

(The `/mtron//data/...` double-slash spelling conflates the peer *endpoint* with the *data* path. The clean form
is `ws://remoteA:1234/data/set/2/` — the authority plus the data address — with the endpoint supplied by the
roster/route entry, exactly as `clstrSpace`'s shape separates them: `key = authority`, `value = wsclient::[host =>
<…/mtron>]`. Third shard is `remoteB`, presumably a typo.)

### Step 1 — does `*/data/set/+/+` reach this memspace? **Yes.**

`*` is `from(...)` (`Obj.java:1428-1431`) → `Router.readFromSpace` → `BasicRouter.read` → `getSpaceFor`.
`getSpaceFor` tests `vid.basePath().test(space.pattern())`. Verified live:

```
</data/set/+/+>.has(</data/#>)   =>  /data/set/+/+      ✓
```

No authority, so no peer dispatch; `alignPrefix` finds no scheme prefix; the pattern matches. **The read lands on
the memspace.**

### Step 2 — does the route table fire? **No. Two independent reasons.**

**(a) `read` does not consult `route` at all.** The hand-off is commented out (`AbstractSpace.java:64-70`), so
`read` goes straight to `q` procs → `Space.Helper.resolveRead(this, vid, directReader())`. The route table is
never read on the read path. *Today, all three bindings are inert.*

**(b) Even with the hand-off enabled, the keys cannot match.** `routeFromSpace` tests
`vid.hasPrefix(routeKey)` — the vid is a **wildcard**, the key is **concrete**. Verified live:

```
</data/set/+/+>.has(</data/set/1/>)   =>  (no match)    ✗
</data/set/1/>.has(</data/set/+/+>)   =>  /data/set/1/  ✓   ← the direction that DOES work
```

So a concrete route key is not found by a wildcard read. **The route table as written is a concrete-address alias
table, not a shard map.** Your intuition — "memspace would have 3 bindings, then rewrite the URI" — is the right
model, but it needs the *reverse* test: for each route key `K`, `K.test(readPattern)`. That is the same
direction `getSpaceFor` already uses, and turning it around is the whole fix.

### Step 3 — what a correct binding enumeration must do

Two missing pieces, both concrete:

1. **The route table must be part of the space's directory.** A branch read currently enumerates `directReader()`
   (the in-memory store) only. If nothing is *stored* at `/data/set/1`, the binding is invisible — even though
   the route declares it. `readStream` must union the route keys (those passing `K.test(pattern)`) with the store.
2. **The rewrite must be per-binding, not a prefix strip.** Fan-out is: enumerate keys → for each, form the
   shard address from its own route value → dispatch. `routeFromSpace`'s prefix-strip is the wrong operation for
   fan-out; it is right for resolving *one* already-concrete address.

Note the route table's shape here is genuinely good and worth keeping: key 1 is
**identity** (local shard), keys 2 and 3 are **remote** — so local and remote shards are *uniform* in the same
table. That is exactly the "self and peer are the same mechanism" property (§5), and it is what lets a shard move
between machines without the code changing.

### Step 4 — the fan-out, the barrier, and the lock question

**Where the barrier is: local, and it needs no distributed lock.** The reason is structural rather than
hopeful. `SwarmProcessor.runMonadicLoop` owns one `barriers` queue, and the gather is applied by
*that* processor: `Processor.Helper.apply(barrier.obj(), barrier.inst())` (`SwarmProcessor.java:383`). Halt
collects into *that* processor's `HALTED` via its own `onHalt` (`:182-190`). So **the aggregation point is
defined by who ran the code** — and that is the submitter, localhost, because the code is running in *its*
processor. "Aggregate back to the source of the code" is not a coordination problem; it is where the processor
is. `MonadProcessor`'s javadoc states the shape: *"each shard owns its own run/barrier/halt, and a coordinator
aggregates the `halt` of its children."*

Two ways to distribute, and only the second ships code:

- **A — distribute the read, fold locally (default).** The three shards' data is fetched and concatenated into
  the coordinator's `Objs`; `.has`, `.select`, `.sum` then run locally. Distribution is purely data movement.
  **No barrier problem at all.**
- **B — distribute the fold.** Each shard runs a partial and returns it; the coordinator merges. This requires
  shipping *source* code (a resolved inst renders as `{lambda@…}` and does not re-parse —
  `ObjmtronSerializer.java:353-363`, §4.1), so it needs the machine handshake, and it requires the reduction to
  be **associative** — `sum` qualifies, `mean` does not (ship `(sum, count)` instead). metatron already models
  this: `Inst.isReducing()` is `isGather() && rng().c().isOne()` (`Inst.java:396-398`).

### The hazard that replaces the lock — and it is a silent-wrong-answer bug

Your instinct that *something* must coordinate is right; the form is not a mutex but a **counting barrier**, and
the reason is concrete. `runMonadicLoop` breaks on:

```java
final StatefulMonad m = (StatefulMonad) this.running().take();
if (null != m) { ... }
else if (!this.barriers().isEmpty()) { ... }
else { break; }                       // "monad processing completed"
```

Under **synchronous** execution that is sound: nothing is outstanding when both queues are empty. Under
**asynchronous remote dispatch** it is not. If the three shard requests are in flight and `running` and
`barriers` are momentarily empty, **the loop terminates and returns `objs(halted())` — a partial `sum`.** No
error, no fail, no warning: a wrong number.

So the requirements are:

1. **In-flight reservation.** The loop's emptiness test must also consult an outstanding count
   (`running.isEmpty() && barriers.isEmpty() && inflight == 0`). Remote monads must be *reserved* in the queue
   before dispatch, or counted. **This is the "distributed lock" you sensed — and it is a local counter, not a
   lock**, because there is still exactly one aggregator.
2. **Timeout → `fail(...)`, never hang.** `sendRecv` with the default `timeoutMs <= 0` waits forever
   (`WebSocketObj.java:141-143`); a dead shard would block the coordinator's loop permanently, and
   `MAX_FAILS`/`infiniteFailCounter` cannot help because nothing fails. A gather that cannot complete must fail
   loudly — a *partial sum returned as a result* is the unacceptable outcome.
3. **Exactly-once, or idempotent shards.** A timed-out call that is retried while the first response is still in
   flight contributes the shard twice, and `sum` double-counts. Correlated request ids plus idempotent
   (read-only) shard access; write fan-out is where this actually gets hard.
4. **Associativity**, only for option B.

Items 1 and 2 are the ones with no analogue today, and item 1 is the one that fails *silently*, which is why it
belongs in Stage 4 before any motif is written.

---

## 10. First slice — implemented, and what it proved

The first step (§7) is built and green. `DistributedPeerTest` runs **two metatron VMs in two OS processes** — the
test JVM plus a forked `PeerNode` — with one socket between them and no shared `Router`. Eight tests, and the
cross-host claims are asserted against a value the peer **seeded at boot and the test never writes**, so a broken
roster cannot fake a pass.

What was added:

| piece | where |
|---|---|
| `fURI.localize()` — strip scheme+authority | `fURI.java` |
| `Router.Helper.sameAuthority` / `hostOf` / `portOf` / `isLoopbackHost` | `Router.java` |
| `Router.own(fURI)` + `Router.isPeer(fURI)` on the contract | `Router.java` |
| `selfAuthorities()`, `own()`, `isPeer()`, `peerTransport()`, `dispatchForeign()` and the guard in `read`/`write` | `BasicRouter.java` |
| the roster path `/sys/peer` | `BasicRouter.peerRosterPath()` |
| the forked peer VM | `src/test/java/.../distributed/PeerNode.java` |
| the two-process test | `src/test/java/.../distributed/DistributedPeerTest.java` |

### The finding that mattered most: a broken roster fails *silently, and looks correct*

With no roster entry for the peer, `ws://localhost:peerPort/usr/x` did **not** fail. It fell through to the
local `wsspace` — whose pattern `ws://#` is a **host** wildcard that also wildcards the *port* — and returned
that space's session cache. The first version of this test wrote to `ws://peer/usr/y` and read it back
successfully **without a single packet crossing the socket.**

That is the §4.5 capture failure demonstrated in anger, and it is the single most important thing this slice
taught: **on this codebase, a distributed test written the obvious way passes when the distribution is broken.**
Every cross-host assertion must therefore be constructed so that a missing/failed peer *fails* the test — hence
the boot-seeded probe value, which the local VM has never held and cannot manufacture.

Corollary for future work: `wsspace`'s `ws://#` claim conflates every ws authority (port included) into one
namespace, so "did it reach the peer?" is not answerable by "did I get a value back?" — only by "did I get a value
only the peer could have?"

### Findings that changed the design

1. **The roster lives at `/sys/peer`, not `/sys/mach/peer`.** The design said `/sys/mach/host` for the
   self-authority and implied a roster under `/sys/mach`. In practice a write to `/sys/mach/peer` is **silently
   dropped**: the Machine claims `/sys/mach` and `AbstractSpace`'s default `directWriter` is the no-op
   `(k, v) -> v`, so the write "succeeds" and stores nothing. `/sys/peer` (beside `/sys/thread`) is stored by the
   `/sys/#` memSpace. The general lesson: new Router state must be placed where a *writer* exists, and its
   persistence needs its own assertion — `testDeclaredRosterPersists` is now a regression guard for exactly this.
2. **No `/sys/mach/host` is needed.** `selfAuthorities()` derives the machine's own authorities from the `host`
   of every mounted space that declares one, so the answer comes from existing declared config. That is strictly
   better than a second, separately-maintained declaration — and it keeps the "declared, never emergent" rule.
3. **The `0.0.0.0` / `localhost` alias was load-bearing, as predicted.** `selfAuthorities()` returns
   `0.0.0.0:<port>` from the binding while a caller addresses `localhost:<port>`; without the alias set,
   `own()` says "not mine", the guard delegates to the local authority — i.e. to itself. Verified working:
   `own(ws://localhost:<selfPort>/a/x)` is true and the write lands in the local store.
4. **A live lambda transport survives storage.** The roster value is an `Inst` whose `f` lives in the inst's jvm
   triplet, so it round-trips through construction, a memRec write and a read-back intact. That is what keeps
   `BasicRouter` free of any transport dependency — the Router never imports web, ws or http, and swapping the
   transport is a roster change. (It also confirms why this is *local* config: a lambda is not portable, and the
   roster is about how *this* machine reaches a peer.)
5. **The guard is safe for the local path.** 1661 tests green across `mInstSetTest` (654), `memSpaceTest` (498),
   `mqttSpaceTest` (449), `SwarmProcessorTest` (11), `BasicMachineTest`, `BasicRouterTest`, `miotSpaceTest` —
   zero failures. The guard short-circuits on `!vid.hasHost()`, so authority-less local reads and writes
   (`/sys/...`, `/m/...`, `/usr/...`) never reach the roster.
6. **`*<ws://host/...>` works at the language level.** The mtron dereference path reaches the peer through the
   same `Router.readFromSpace` funnel, so this is not a Java-only capability.
7. **An undeclared foreign authority falls through to `noobj`** — no dial, no throw. Fail-closed confirmed
   behaviourally, and `isPeer(http://example.com/)` is false, so a web fetch cannot be mistaken for a peer.

### Still open (unchanged by this slice)

The correlation table, pooling, liveness, bounded queues, route lanes and enumeration, `state/home`, async
dispatch and the counting barrier are all untouched — this slice is single-request and synchronous, which is what
makes its result deterministic. The `ListMonad` O(n) `take`, the `sendRecv` one-shot handler swap, and
`LocalCluster`/`LocalNode` (still `@Disabled`, still sharing one Router) remain as listed in §4.

Also noted in passing, and **pre-existing** (it appears in `BasicRouterTest`, which this work did not touch):
`[ERROR] [ObjFactory] underlying jvm object is immutable: java.util.ImmutableCollections$MapN` twice at boot.

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
| ~~distributed tests disabled~~ | ✅ deleted — `clstrSpaceTest`, `DistributedMachineEvaluationTest`, `SampleDistributedMachineTest` are gone |
| ~~Tier 1 shares the Router~~ | ✅ deleted — `LocalNode`/`LocalCluster` are gone; peers are forked JVMs (§10) |
| ~~stale testing doc~~ | ✅ deleted — `docs/design/distributed-testing-framework.md` is gone; §10 is its replacement |
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
| `#` in `<http://#>` / `<ws://#>` is the HOST wildcard | live probe `<http://example.com/x>.has(<http://#>)` → matches; `AbstractfURI.java:354-355` |
| authority-bearing pattern cannot capture a local path | live probe `</usr/x>.has(<ws://localhost:21001/#>)` → no match |
| peer data address fails the cluster pattern | live probe `<ws://localhost:21001/usr/x>.has(<ws://+/cluster/#>)` → no match |
| `host:port` is the peer identity | live probe `<ws://localhost:21001/usr/x>.has(<ws://localhost:9999/#>)` → no match |
| `.has()` is the `test()` direction probe | `Uri.java:337` |
| every real space is authority-less | live `/sys/space/#` inventory: `memspace::[pattern=>/usr/#]`, `tblespace::[pattern=>/usr/dr/#]`, `fsspace::[pattern=>mfs:#]` |
| `httpSpace` is already a wildcard-authority proxy | `httpSpace.java:423-493`; `boot.mtron` pattern `<http://#>` |
| `LocalNode` registers a LOCAL authority-bearing pattern | `LocalNode.java:117-120` (`ws://localhost:<port>/#`) |
| concrete host beats `#` deterministically in `compareTo` | `AbstractfURI.java:280-281` |
| `getSpaceFor` is called from compile-time rewrites | `CommonRewrites.java:141,334,490,764,1052,1199,1319,1459,1598,1711,1813,1914,2018,2124`; `RewriteBuilder.java:285` |
| `getSpaceFor` backs type-system structure | `Obj.java:914` (`instanceof InstSet`), `:928`; `Uri.java:453-467` (`directReader`) |
| `hasAuthority()` requires an explicit port | `fURI.java:176-178` |
| machines are Routers, so all inherit the guard | `AbstractMachine.java:35` (`extends BasicRouter`); `Machine.java:49`, `:170` |
| the three read/write funnels | `Router.java:70-101`; `BasicRouter.java:307`, `:336` |
| Router indexes are declared recs, populated by registration | `BasicRouter.java:62-66` (`small_route`, `big_route`, `prefixToVID`); `InstSet.java:150` → `registerPrefix` |
| live runtime state is also exposed as a rec | `ThreadExecutor.java:52-90` (`/sys/thread/{run,stop}`) |
| the peer shape already separates config from status | `clstrSpace.java:58` (`host`/`handler` vs `state`/`stat`) |
| inbound ws accepts any route-resolving connection | `wsSpace.java:264-306` (`createServer`) |
| scheme already selects the space | `ws://#`→`wsspace`, `http://#`→`httpspace`, `z2m:#`→`mqttSpace`, `mfs:#`→`fsSpace`, `docker:#`→`dckrSpace` (live inventory) |
| two spellings: host-wildcard vs scheme-prefix | `boot/space/web/http.mtron:5` (`http://#`) vs `boot/boot.mtron:38` (`local:#`, routed) |
| client types exist but are stubs | `httpSpace.java:114-119` (throws "http client not implemented"); `WS_CLIENT_TYPE` `wsSpace.java:102` |
| carriers own route matching + protocol selection | `docs/design/webspace.md` §8.6 ("carriers as the last hop") |
| the route hand-off is written and commented out | `AbstractSpace.java:64-70` (`if (!routedVID.test(this.pattern())) return Router.readFromSpace(routedVID);`) |
| the write path has no route hand-off at all | `AbstractSpace.java:112-113` (commented log only) |
| route values span handler types, codes, and other spaces' schemes | `drstynx.boot.mtron:93-99` (`/docker => docker:`, `/mfs => mfs:`, `/drstynx => <code>`); `:86` (`mfs: => <~/…>`) |
| identity routes are why the hand-off is commented out | `drstynx.boot.mtron:106,135,166` (`docker: => <>`, `/usr/dr/ => <>`, `embed: => <>`) |
| route values are classified into lanes | `wsSpace.java:124-176` (`RouteLane`); `httpSpace.java:301-375` |
| routes are applied per-space on the IO path, not by `AbstractSpace` | `AbstractDataPathSpace.java:181`; `grphSpace.java:511,612,639`; `dckrSpace.java:139`; `rdfSpace.java:54` |
| `redirect` is compile-time, not hot-path | `CommonRewrites.java:142,346,519,795,1336,2035,2141`; `RewriteBuilder.java:297`; `tbleInstSet.java:91,101,870` |
| `read` does not consult `route` (hand-off commented out) | `AbstractSpace.java:64-70` |
| a wildcard vid does not match a concrete route key | live: `</data/set/+/+>.has(</data/set/1/>)` → no match; `hasPrefix` literal compare `AbstractfURI.java:452-457` |
| the reverse direction does match (concrete key under wildcard read) | live: `</data/set/1/>.has(</data/set/+/+>)` → match |
| the wildcard read does reach a `/data/#` space | live: `</data/set/+/+>.has(</data/#>)` → match |
| gather is applied by the coordinator's own processor | `SwarmProcessor.java:383`; halt at `:182-190`; loop break at `:412-415` |
| `sendRecv` waits forever by default | `WebSocketObj.java:141-143` |
| `isReducing()` marks an associative gather | `Inst.java:396-398` |
| `inst.vid()` is the program counter; `noobj` = halt | `Code.java:68-76`; `Monad.java:38-40` |
| the monad carries its whole code, unchanged | `StatefulMonad.java:104` (`next()`); `:189-191` (`code()`) |
| step = read lens → apply → write lens → advance PC | `StatefulMonad.java:291-320` |
| the only stack is `state/loop` | `StatefulMonad.java:135-169` |
| `state/path` is an accreted trace, opt-in | `StatefulMonad.java:130-132`, `:198-200` |
| monad is introspectable and round-trippable by URI | `StatefulMonad.java:210-228` (`component`), `:236` (`of`) |
| halt collects into the processor's `HALTED` | `SwarmProcessor.java:182-190`, `:356-363`; `RESULT` auto-pointer `:102` |
| gather = a second monad in the `barriers` queue | `SwarmProcessor.java:257-261`, `:378-411` |
| the spawn-source precedent is on threads, not monads | `AbstractThread.java:104-112` (`SOURCE` → `auto_from_(parent.vid())`), `:142-155` (`source()`, `sourceVid()`, `getAutoPointer`) |
| `source` also means derivation provenance | `llmInstSet.java:333`; `SummarizeFeature.java:295,413-431` |
| one run per processor, fresh vid per run | `Machine.java:150-155` (clone); `SwarmProcessor.java:95` (`/sys/processor/<n>`) |
| `?path` is the opt-in per-monad-state precedent | `SwarmProcessor.java:248-251`, `:269-271` (`computesPath`, hardcodes `"path"`); `StatefulMonad.java:109` |
| `monad_in=state/<field>` needs no lens change | `StatefulMonad.java:218-219` (default branch) |
