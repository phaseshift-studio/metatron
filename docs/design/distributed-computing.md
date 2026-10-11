# Distributed metatron — current state, the machine nest, and the road ahead

*Status: design record, re-witnessed 2026-10-10 against `src/main/java` after the Router→Machine refactor. Every
"exists" claim carries a `file:line`; every "missing" claim was checked by grep. Where the previous revision of this
document cited `Router.java`, `BasicRouter.java` or `clstrSpace.java`, those files are **deleted** — the code moved to
`Machine` / `Memory` / `Network` / `AbstractMachine`, and two of its properties were lost in the move (see D1 and the
resolved R1).*

---

## 0. The thesis, in one paragraph

Metatron should **not** adopt a distributed programming paradigm. It should make the monadic machine it already has
**authority-partitioned**, and let the paradigm *emerge* from three things that are already declared: the coefficient
shape of an instruction (`Inst.Form`), the algebraic laws its carrier declares (`catInstSet`), and the address that
says who owns the work. Vertex-centric, map-reduce, BSP, agent message passing, remote lambda execution and
distributed matrix computation are then not features but *consequences* of which laws an operand declares — which is
the only version of this that stays mathematically coherent as the instruction set grows.

Two structural commitments make that concrete:

1. **The Machine nest is the topology.** `push`/`pop` is a call stack; a child machine is a subproblem; `pop` is its
   garbage collection. A *mini-cluster* is a child machine plus the peers its `Network` declares plus the URI space
   they share.
2. **The authority is the boundary.** An address either names work this machine owns or work a peer owns, and exactly
   one function decides which. Everything else — transports, containers, hosts — is downstream of that function.

---

## 1. The model: four scopes

The distribution story is a story about *scope*. Four of them exist in the code today, and they must not be confused
(confusing the first two is what produced the bug fixed in R1):

| scope       | what it is                                                            | where it lives                                                                                                                                                    | lifetime                     |
|-------------|-----------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------|
| **name**    | a machine-local binding (`who`, `blah`)                               | the thread's arg stack, rooted at *this* machine's root frame — `Memory.stack()` (`Memory.java:266`), `Memory.argStack()` (`:141`), `Memory.rootFrame()` (`:254`) | the machine                  |
| **address** | an absolute / scheme'd / authority'd URI                              | the space that covers it — `Memory.readAbsolute` (`Memory.java:167`), `Memory.writeAbsolute` (`:223`)                                                             | the space                    |
| **machine** | the frame of reference: memory, instset, compiler, processor, network | `Machine` (`Machine.java:78`); `current()` is per-thread (`:104`, `:120`)                                                                                         | push → pop                   |
| **cluster** | the peers this frame can reach                                        | `Network`, roster at `/sys/peer` (`Network.java:284`)                                                                                                             | the machine that declared it |

**A name is not an address.** It is bound in the machine's own frame and answered there and nowhere else; a name is
never claimed by an enclosing level's space. **An address walks the levels**, nearest first, and falls through to the
enclosing one — which is what gives child-sees-parent its spaces while keeping a child's names private.

---

## 2. What exists, and works

### 2.1 The machine nest, and machine isolation

A machine is pushed with `Machine.push(extension)` (`Machine.java:311`) and discarded with `pop()` (`:346`). `push`
mints a child whose memory is a `MemoryUnion` over the parent's (`:332-334`), registers it in the root so `move(vid)`
reaches it from any sibling, and makes it current. `pop` closes the child and everything it opened.

`MemoryIsolationScriptTest` proves the invariants as stateful scripts — thread-count scoping, `~` tracking the
current machine, write isolation (no leak up, no leak across), a single global thread executor, and instruction-set
scoping across push/pop. `testHierarchyMoves` — the 7-machine hierarchy, which asserts each machine reads *its own*
frame binding — is re-enabled and passing as of this revision (it had been `@Disabled`; the regression behind it is
R1).

**Known pre-existing failure:** `BasicMachineTest.testCloseSpace` fails independently of the work above (verified by
reverting and re-running). It asserts `hasSpaceFor("/test/a")` after registering a space at `/m/test/#`, and the
root's catch-all `#` space covers the former either way.

### 2.2 The distributed rewrite

`machInstSet`'s `sum_gather` rule (`machInstSet.java:543-676`) distributes a monoidic reduction. With peers declared
as keys under `/usr/compute/a/barrier/*`, the rule:

- slices the `start(...)` data contiguously across `peers + 1` machines (`sliceOf`, `:113`; slicing at `:600-625`);
- ships each peer's **worker form** — its own slice as an `isInitial` start, then the reducer, then a report — by
  writing it to `/usr/compute/<peer>/recv` (`:627-639`);
- gives the home its own slice and inserts one `barrier(<home-box>)` per peer before the reducer (`:649-671`);
- is inert with no peers declared (`:556-560`), and idempotent across the fixpoint rewriter (`:578-581`).

`DistributedRewriteTest` (`DistributedRewriteTest.java:44`) pins the end-to-end behaviour in CSV rows; its harness
`checkDistributedCode` (`AbstractMachineTest.java:106`) **impersonates the peers** — for each declared key it reads
that peer's shipped worker out of its `recv` box and runs it on a plain thread (`:139-168`). The
`AbstractMultiServerSingleSpaceTest` suite (`AbstractMultiServerSingleSpaceTest.java:37`) adds the plumbing
assertions: the rule is inert without peers (`:124`), one gather per peer addressed at that peer's mailbox (`:131`),
and each peer is shipped a code carrying its own data (`:141`).

`MachineSwarmMigrationTest` (`MachineSwarmMigrationTest.java:42`) covers the shape the rewrite cannot generate: a
**middle machine that is both waiter and reporter** (`barrier(uri).sum().to(uri)`), so a tree of any depth is built
from the same two instructions (`:86-128`). It is also the witness for the mailbox being a registered `subq` space
rather than machinery (`:59-74`).

### 2.3 The gather and the report

- `barrier(uri)` (`Obj.java:1289-1314`): subscribe to the mailbox (`:1296`), **read before waiting** so a
  report that arrived first is not lost (`:1304`), then `latch.await()` (`:1309`). `barrier()` with no argument is a
  passthrough (`:1316`), so the rewrite must mint the address form.
- `to(uri)` (`Obj.java:1450`) is `Machine.write(uri, lhs)` — the report. Fire and forget.
- A gather is *syntactic*: `Inst.isGather()` is `dom().c().max() == null` (`Inst.java:374`), so the processor gives
  any unbounded-dom inst its own barrier monad (`SwarmProcessor.java:253-262`) and drains the queue when `running`
  empties (`:378-411`). The loop's termination test is the emptiness of `running` **and** `barriers` (`:412-415`).
- `MonadProcessor`'s own javadoc states the distributed shape: *"each shard owns its own run/barrier/halt, and a
  coordinator aggregates the `halt` of its children"* (`MonadProcessor.java:28-33`).

### 2.4 The Network component

`Network` (`Network.java`) is a `Machine.Component` holding the roster `authority → transport`, plus:

- `cluster()` — a live view (`:98`), whose `peer` field is an auto-pointer at the roster (`:101`) and whose `status`
  is an `auto` inst closing over *this* component (`:119`);
- `status()` / `Helper.probe` — one report per declared peer, a silent peer reported `down` rather than omitted
  (`:131-139`, `:254`);
- `Helper.sameAuthority` / `hostOf` / `portOf` / `isLoopbackHost` — the authority algebra, alias-aware (`:192-244`);
- `peerRosterPath()` = `/sys/peer` (`:284`), chosen because `/sys/mach` is claimed by the Machine and its writer is a
  no-op.

The roster is read **only by `Network` itself** — nothing else in `src/main` reads `/sys/peer`. And `Network.read` /
`write` (`:147-175`) — the authority dispatch: resolve a declared peer's transport and ship
`from(<localized>)` / `obj.to(<localized>)` — **have no callers.** See D1.

### 2.5 The container axis

`dckrSpace` (`dckrSpace.java`) bridges a docker daemon into URI space. What is implemented, verified:

- `docker run -d` with `user`, `ports`, `environment`, `volumes` (auto-creating named volumes), `network`, `command`
  (`:592-664`);
- `docker compose … up -d --wait --timeout 600` — **healthcheck-gated readiness** — and `down` with cleanup
  (`:547-590`);
- network/volume create/remove, `image ls`, `docker version` availability, a remote daemon via `-H` (`tcp://`,
  `unix://`, `ssh://`);
- graph navigation: container ↔ image/network/volume as lazy `auto_from` refs, so traversal does not materialise the
  graph;
- live pull progress through a `progress_table` widget.

The important part is not the CLI surface but that **the pattern is already implemented and working**, in
`boot/space/`. `boot/space/tble/mariadb.mtron` is a mtron function that checks whether the container exists
(`from(docker:container/metatron_mariadb)`), otherwise brings it up via `docker:compose/metatron_mariadb` with a
`healthcheck`, and then mounts a **space** on its endpoint (`.as(tblespace::T)`). `boot/spaces.boot.mtron:1-10`
states the model outright: *"every space backend runs as a container aggregated through dckrspace, and each backend
gets its space registered on top."*

So "a Machine provisions its own container with tooling" is not a new mechanism — it is that function, run at a
different scope. See §5.3.

### 2.6 The category laws, as distributed permissions

`/m/math/cat` models types as objects and insts as morphisms, and the same declarations feed the rewriter
(`docs/skills/mtron/references/cat-instset-mtron.md`). Two of its laws were written for exactly this and are **declared
in the enum but used by nothing**:

- `floatable` — *"commutes through the stream ring's parallel/serial structure"* (`catInstSet.java:851`);
- `blocked` — *"cannot float past a barrier — a reduce anchors the coefficient in place"* (`:855`).

`catInstSet.isFold(inst, carrier)` already computes the right predicate for a distributable reduction: `reducer` or
`join` **form** *and* a declared `monoidic` law (`:663-668`). And the law table already knows which operations
qualify — `sum` is `monoidic` (`mInstSetLawTable.java:251`), `mean` is `magmadic` (`:250`) and therefore correctly
not distributable. The type system already encodes the answer the distribution rewrite hardcodes.

---

## 3. What is broken or missing — the honest list

Ordered by how much they block the story.

### D1 — `Network`'s authority dispatch is orphaned, and its test is red

`Network.read` / `write` (`Network.java:147-175`) have **no callers**. `Machine.read`/`write` (`Machine.java:154-175`)
go straight to `memory()`, and `Memory.readAbsolute` / `writeAbsolute` (`Memory.java:167`, `:223`) never consult an
authority. `AbstractMachine` was `BasicRouter` and its javadoc still says it holds *"the authority guard"*
(`AbstractMachine.java:44-52`) — the guard is gone. `own()` and `selfAuthorities()` do not exist anywhere.

Measured now:

```
NetworkTest  —  Tests run: 4, Failures: 3
  testReadAcrossPeer[1]  a ws:// read dispatches to the peer's store  expected <1>  but was <noobj>
  testReadAcrossPeer[2]  … expected <10> but was <noobj>
  testReadAcrossPeer[3]  … expected <100> but was <noobj>
```

The fourth test (`testWriteAcrossPeer`) passes — and it is the false positive this codebase specialises in: an
undeclared-authority write falls through to a local space and reads back locally, **with no packet crossing the
socket**. This is a regression from a green slice, not an unfinished idea.

### D2 — Two memberships, no reconciliation

The distribution rewrite's topology is the box keys under `/usr/compute/a/barrier/*` (`machInstSet.java:556-589`).
`Network`'s membership is `/sys/peer`. Neither is derived from the other; "who is in the cluster" has two answers,
and `cluster().status()` reports on a roster the compute path never reads.

### D3 — Topology is resolved at compile time, and the rewrite is not pure

The rule reads mutable space state during a rewrite pass (`Machine.read(COMPUTE)`, `machInstSet.java:556`) and
**writes** the worker forms (`:637`). A compiler pass must never open a socket, and the cat rewriter families are
pure `code -> code`. Consequences: the same source compiles differently per roster; the home form and worker forms
are different programs, so compiled code is *not* portable; and the compile cache can serve a plan minted for a
different cluster. It also forces the two heuristics that exist only because the rule inspects its own output — the
"worker form is terminal" guard (`:570-573`) and the idempotence scan (`:578-581`).

### D4 — The prefix is replicated unconditionally, and that is unsound

The rule copies everything between `start` and the reducer into every worker (`machInstSet.java:632`) with no check.
The law that licenses that code motion is `floatable`; the law that forbids it is `blocked`; **neither is declared
on any operation and neither is consumed anywhere.** Counterexample: `{1,1,2,1,1,3}.dedup().sum()` — `dedup` has no
law entry at all and is not a homomorphism across a partition. Globally the result is `6`; sliced two ways it is
`3 + 4 = 7`.

### D5 — The barrier blocks a processor thread, has no deadline, and carries no round

`barrier(uri)` parks on a `CountDownLatch` with no timeout (`Obj.java:1309`) **on the processor thread** — the loop
applies the barrier inline (`SwarmProcessor.java:378-411`), so the whole home machine is unavailable while gathering,
and a dead peer hangs it forever. Because the mailbox is a persistent space read *before* waiting (`:1304`), a second
run of the same expression folds the previous run's partials: there is no round or epoch in the address or the
report. The tests bound this externally (`future.get(30s)`); the runtime does not.

### D6 — Concurrent reports lose data

`AbstractMachineTest.java:154-168` documents a real VM bug: two shards writing different keys under one parent
(`/usr/compute/a/barrier/<peer>`) lose one of them. The suite works around it by joining each shard before starting
the next. Concurrent reporting is the normal case for a cluster, so this is a correctness blocker, not a test wart.

### D7 — No result identity

Aggregation is `objs(this.halted())` and an anonymous append into a mailbox. There is no per-shard result, no
provenance, no partial-failure state: the coordinator cannot say whose halt it aggregated. `MonadProcessor`'s
javadoc already names the shape it needs (`MonadProcessor.java:28-33`).

### D8 — The executing frame is the caller's, not the processor's machine

`ThreadExecutor` captures `Machine.current()` on the *calling* thread and installs it on the worker
(`ThreadExecutor.java:221-241`); `AbstractThread.createTask()` sets no perspective (`AbstractThread.java:192`). So
"run this on machine B" resolves addresses against whoever submitted. Machine identity is currently nominal for
execution — which is why the swarm test must register the same mailbox space into every machine
(`MachineSwarmMigrationTest.java:59-74`) rather than relying on whose memory a report lands in.

### D9 — `Network` is not the accumulating component it claims to be

Its javadoc says a frame *"wraps it with a `ComponentUnion` rather than replacing it"* (`Network.java:49-51`), and
`MemoryUnion` implements `ComponentUnion` — but **`NetworkUnion` does not exist**. On `push`, the child shallow-clones
the rec and shares the parent's cached `resolvedNetwork` (`AbstractMachine.java:90-94`); `push` never re-parents or
unions it. A child therefore cannot scope its own peer set, and a child's peer edit would land on the parent's
network. Since membership must be per-machine while connections stay global, this is the single most important gap
for nested clusters.

### D10 — The container axis is global, unowned, and has no tool-invocation primitive

- **`docker exec` does not exist.** Grep confirms no `"exec"` subcommand anywhere; `exec(...)` is the host-process
  helper. Container output reaches mtron only through a bind-mounted file (the SQLite walkthrough), i.e. out of band.
- **No ownership, so no reclaim.** `boot/space/*` deliberately *"leave a running container in place"* — right for
  boot-time infrastructure, wrong for a job-scoped mini-cluster. `dckrSpace` is global; containers, networks and
  volumes belong to nobody, so `pop` cannot release them.
- **Provisioning is boot-scoped.** `boot/spaces.boot.mtron` registers space backends on the *root* machine at boot.
  Running the same template *inside* a pushed machine is the actual new capability.
- **`host` is per-space, not per-address.** Several daemons means several `dckrSpace` registrations, rather than
  `docker://host:2375/...` being one more authority the boundary already understands.
- **No `build`.** A machine can pull an image but not build one, so "its own tooling" means "someone else's published
  image".

### R1 (resolved) — A bare relative name was published to the root machine

`MemoryUnion` had lost the name/address split that `Memory.write` makes. A relative vid went through `findSpace`,
where an enclosing level's catch-all `#` space claimed it, so `who -> 11` written in a child was readable from the
root and from siblings; and `read` consulted `previous` **before** the current level's own frame, so a child's name
could be shadowed by its parent's. Fixed in `MemoryUnion.read`/`write` (`MemoryUnion.java:118-166`): a name binds and
reads in this level's own frame and only falls through on a miss (so a short name that is really an address —
`count` → `/m/inst/count` via `big()` — still resolves); an address keeps the level walk. `testHierarchyMoves`
re-enabled and green.

---

## 4. Laws as distributed permissions

This is the core of "paradigms are epiphenomenal". Each declared law licenses exactly one distributional move, and
the moves compose into the paradigms:

| declared law              | what it licenses                                                                           |
|---------------------------|--------------------------------------------------------------------------------------------|
| `monoidic`                | **grouping** — arbitrary shard sizes; associativity + identity make the sharded fold sound |
| `commutative`             | **reordering** — partials may merge in completion order, so arrival order is irrelevant    |
| `idempotent`              | **retry** — at-least-once transport is safe; a duplicate partial cannot change the result  |
| `floatable`               | **code motion** — the operation may be replicated *into* the shard                         |
| `blocked`                 | **anchoring** — the operation must *not* move; it defines a superstep boundary             |
| `action`                  | **message passing** — `f(gh)x = f(g)f(h)x`; accumulation on a carrier                      |
| `kleene`                  | **iteration to closure** — the reflexive-transitive iterate (vertex/fixpoint programs)     |
| `left/right_distributive` | **blocked fan-out** — matrix-multiply-shaped work                                          |
| `partial`                 | **pass-or-drop** — the eligibility predicate on a channel                                  |

And the paradigms fall out rather than being designed:

| you call it                     | it is, here                                                                                           |
|---------------------------------|-------------------------------------------------------------------------------------------------------|
| map-reduce / monoidic fold      | element-wise (`mapper`/`filter` form, `floatable`) prefix ∘ `reducer` declaring `monoidic`            |
| bulk synchronous (BSP)          | a `blocked` reducer; supersteps are runs of floatable operations between anchors                      |
| vertex-centric                  | the cat graph itself — iterate a morphism over a vertex's `morphed_to`/`morphed_from` to closure      |
| agent (non-LLM) message passing | `partial` + `action` over a `?subq` URI pattern                                                       |
| remote lambda execution         | `floatable` code motion — the address carries the distribution, not the coefficient                   |
| distributed matrix              | a `ring_theory`/`field_theory` carrier with `left/right_distributive`, blocked at the inner dimension |

**Where the distribution rewrite belongs:** as a **pure** family in `catInstSet`, gated on `Inst.Form`
(`reducer`/`join`/`gather`) plus the laws above — the position the family- (4) comment already reserves for
relation-driven rules (`catInstSet.java:338-378`). A pure rewrite can only reshape code; the peer set and the
addresses must be bound **at run time** by the processor against `Machine.current().network()`. That is the same move
that makes compiled code portable and makes nested clusters composable, because each machine resolves the plan
against its own network.

**Testability follows from the same declarations.** Once the gate is a law, the distributed suite stops being CSV
rows of hand-computed mailboxes and becomes a property over the law table: *for every `(op, carrier)` where `isFold`
and `commutative`, `local fold == sharded fold` for random slices* — and *sharding is legal iff every replicated
operation is `floatable` and no `blocked` operation intervenes*. That is "known and tested distributed semantics"
generated from the declarations rather than asserted by hand.

---

## 5. The vision horizon

### 5.1 A mini-cluster is a machine, a roster, and a workspace

The type documentation already states the target (`machInstSet.java:455-463`):

> *"lateral machine communication made possible through the machine network where the peer group forms a shared
> computing workspace that is garbage collected when machine is popped off the parent stack."*

Concretely:

- **child machines** = subproblems, nested like a call stack (`push`/`pop`);
- **the shared workspace** = a URI space the members can all reach — a space mounted at a *common ancestor*, since a
  machine's own namespace is not addressable by path from a parent or sibling (verified live, and pinned by
  `MemoryIsolationScriptTest`);
- **membership** = the roster, per machine, walking the nest;
- **cleanup** = `pop`, which must now also release what the machine *provisioned* (D10), not only what it mounted.

This is what makes big OLAP jobs and many small OLTP operations coexist: the OLAP job is a root with a `blocked`
reducer whose children are shards or supersteps, each child's local spaces are the OLTP work, and inter-cluster
traffic is monad routing between sibling mini-clusters.

### 5.2 One boundary, one membership, one workspace

The three commitments that make the above real, in order:

1. **Restore the boundary.** A single guard, where an address becomes a space — `Memory.readAbsolute` /
   `writeAbsolute` (`Memory.java:167`, `:223`), *before* `findSpace`, short-circuiting on `!hasHost()` so local
   traffic is untouched. `own(authority)` and `selfAuthorities()` are the missing halves (derived from the `host` of
   every mounted space that declares one, `0.0.0.0`/`127.0.0.1`/`localhost`/`::1` aliased — `Network.Helper`
   already has the alias test). Trap to carry forward: an own-authority vid is localized **only if the
   authority-free path still resolves to a space**, otherwise `wsspace`/`httpspace` sessions
   (`ws://localhost:PORT/<route>/<n>`) are stripped and vanish silently.
2. **Unify membership.** The roster is the topology. The barrier branch's keys should *be* roster entries (or carry
   an authority), and the rewrite should ask `Machine.current().network()` rather than reading `/usr/compute`.
   `cluster().status()` then tells the fold whether a declared peer is up, and a `down` peer produces `fail(...)`
   rather than a hang.
3. **Make the workspace explicit.** `/usr/compute` becomes the Network's shared computing workspace, provisioned
   with the frame and torn down with it. Sharing must be *declared* (a mounted space) and never accidental — which is
   exactly what R1 was about.

### 5.3 The container axis: three ways a container extends metatron

A machine need not implement everything. A container can supply any of three things, and each already has a plug
point:

| what you are adding | how the container provides it             | existing witness                                                                  |
|---------------------|-------------------------------------------|-----------------------------------------------------------------------------------|
| **data**            | a service mounted as a **space**          | mariadb → `tblespace`, chroma → `vecspace`, gremlin → `grphSpace` (`boot/space/`) |
| **tools**           | an **MCP server** inside the container    | `mcp_ws` / `mcp_http` / `mcp_stdio` handlers                                      |
| **compute**         | a **peer VM**, reached through the roster | `wsclient` + `mtron_ws`; `NetworkTest`, `PeerCluster`                             |

**The alignment that matters most: a docker service name *is* a peer authority.** A user-defined bridge network
gives container-name DNS, so `ws://peer:8555/mtron` resolves with no IP bookkeeping, and `peer:8555` is precisely the
shape the roster keys on (`Network.java:284`) and what `sameAuthority` compares (`:232`). `--network-alias` then
gives a container a **stable identity across re-creation**. So:

> **the compose file is the cluster declaration, the network is the workspace boundary, and the service names are the
> peer authorities.**

Mini-cluster formation becomes `compose up --wait` (already implemented, healthcheck-gated, `dckrSpace.java:547-576`),
and `Network.cluster().status()` already probes members for liveness.

**Gaps to close, in order:** (1) `docker exec`, the primitive that turns a container from *a thing that runs* into *a
callable tool* — pair it with MCP for hot paths, since exec is a process spawn per call; (2) **ownership**, so
provisioned containers/networks/volumes are released on `pop`; (3) **machine-scoped provisioning**, running the
`boot/space/*` templates inside a pushed machine; (4) a **roster read** over a docker network or compose stack, so
membership can be sourced from the stack; (5) **authority-qualified daemons** (`docker://host:2375/...`), so
multi-host is the same mechanism as multi-peer; (6) `build`, so "its own tooling" can mean an image it made; (7)
**streaming** (`logs -f`, exec output) into a `?subq` pattern, which is the same pub/sub primitive the
mini-cluster workspace needs.

### 5.4 Transports are roster entries

The rewrite must know an **address**, never a scheme — that part of the current design is right and worth keeping
(`COMPUTE` is deliberately scheme-less, `machInstSet.java:90-97`). Deployment then chooses the transport, and the
same computation runs with peers that are:

- pushed child machines in one JVM (shared `memSpace` / shared subspace — the current tests),
- containers on a bridge network (socket),
- VMs across a host (socket),
- rows in a shared table (`AbstractMultiServerSingleSpaceTest`'s per-space subclasses already probe this).

One address form; the roster decides what answers it. That is what "self and peer are the same operation modulo
transport" (`Network.localize`, `fURI.localize()` at `fURI.java:324`) buys.

---

## 6. Staged plan

Each stage is independently verifiable, and the order is load-bearing: nothing later is meaningful on an unverified
boundary.

**Stage 0 — the boundary (small, unblocks everything).**
Restore `Network.own()` / `selfAuthorities()`; wire the guard into `Memory.readAbsolute` / `writeAbsolute` before
`findSpace`; carry the "localize only if the path still resolves" rule. *DoD:* `NetworkTest` green (4/4); a
declared-but-absent peer yields `noobj` rather than reaching a local space; the
`http://example.com` fall-through and the Remote Console still work.

**Stage 1 — one membership.**
The barrier branch's keys become roster authorities; `MACH_HOME` comes from `own()` rather than the literal `"a"`;
`cluster().status()` is consulted by the gather. Keep the shared-space transport so `DistributedRewriteTest` stays
green as the regression witness. *DoD:* declaring a cluster once declares both membership and mailboxes; a `down` peer
fails the fold loudly.

**Stage 2 — a barrier that can be waited on.**
Replace the latch with a **barrier registry**: `barrier(uri)` registers a waiter (address, expected count, deadline)
and parks the monad; a write to the address decrements; at zero the barrier monad is released back into `barriers()`.
The loop's termination test must consult the outstanding count — `running.isEmpty() && barriers.isEmpty() &&
inflight == 0` (`SwarmProcessor.java:412-415`). **This is the silent-wrong-answer bug**: under async dispatch the loop
currently terminates mid-flight and returns a *partial sum* as a result. It precedes any async fan-out. *DoD:* a peer
that never reports produces `fail(...)`, not a hang; a partially-arrived gather never yields a result.

**Stage 3 — reports with identity.**
`shard_result::[authority, round, status, value]`, with the round in the mailbox address. Kills the stale-fold in D5,
makes partial failure reportable, and makes "aggregate to a result set" typed rather than an anonymous monoid fold.
*DoD:* running the same expression twice does not fold the first run's partials.

**Stage 4 — the distribution rewrite becomes a cat family.**
Pure `code -> code`, gated on `Inst.Form` + declared laws; declare `floatable`/`blocked` on the operations that earn
them; the processor binds the peer set and addresses at run time. Delete the name check (`SUM_INST_TID`), the I/O,
and the terminal/idempotence heuristics. *DoD:* `prod`/`merge`/any declared-monoidic reducer distributes;
`dedup().sum()` no longer mis-distributes; the
law-driven property test passes.

**Stage 5 — nested clusters.**
`NetworkUnion`, so a pushed child inherits and extends the roster; per-machine membership, global connections;
workspace provisioning on push and teardown on pop; the child's halt delivered to the parent through the fork/join
coefficient. *DoD:* a depth-2 mini-cluster of mini-clusters completes; `pop` leaves no container, space, or peer behind.

**Stage 6 — the container axis.**
`docker exec`; resource ownership and reclaim; machine-scoped provisioning from the `boot/space/*` templates; a
roster read over a docker network; authority-qualified daemons. *DoD:* a pushed machine provisions a container, mounts a
space on it, forms a cluster with a peer container on the
same bridge network, and `pop` tears all of it down.

**Stage 7 — motifs.**
Only now write map-reduce / BSP / agent patterns as mtron — and only where Stages 0–6 have not already made them
one-liners.

---

## 7. What not to build

- **A distributed scheduler.** `running`/`barriers`/`halted` *is* the scheduler; the topology is the coefficient.
- **A `MapReduceExecutor` / `BSPExecutor` class.** If a motif needs a class, the coefficient algebra is incomplete.
  (These names appear in memory as if real; they are not in `src/main/java`.)
- **A new transport.** `ws` + `mtron_ws` + `send_recv` is the working round trip; the work is making it correlated,
  bounded, and addressed by authority.
- **A shared-Router "cluster".** An in-process simulation sharing one machine cannot fail on an authority,
  serialization or correlation bug — the three classes that matter. Fork JVMs, or fork containers.
- **Compiled-code shipping.** A resolved inst renders as a lambda and does not re-parse. Ship source and negotiate
  the machine.
- **A paradigm, as such.** Every one of them is a different set of declared laws over the same engine.

---

## 8. Open decisions

1. **Resolve vs reference at the transport boundary.** When a value crosses, is it a copy or a pointer? A read that
   returns `!*ws://hostB:8555/usr/x` is a *portable* reference, unlike a bare `!*/usr/x` which dangles. This changes
   what a remote read returns, so it must be decided before any serializer is touched.
2. **Relative-name scope.** Settled by R1 for the machine case, but the *intended* rule should be stated once and
   pinned: a bare name is machine-local and dies with the machine; sharing is always a declared address.
3. **Roster key: authority or service URI.** `host:port` implies one endpoint per node; `scheme://host:port` allows a
   node to expose mtron over `ws` and `http` as two entries. The service URI is strictly more expressive and less
   surprising.
4. **Where a container-membership read lives.** Whose job is it to turn "the members of this compose stack" into a
   roster — `dckrSpace` (a space read) or `Network` (a component method)?
5. **Correlation identity.** D7/D5 both need a request id, and the design must say what identifies a run across a
   recompile — the processor vid identifies the run, but not a retry.

---

## Appendix — evidence index

| claim                                                     | witness                                                                                         |
|-----------------------------------------------------------|-------------------------------------------------------------------------------------------------|
| machine nest: push/pop, unioned memory, root registration | `Machine.java:303`, `:311-341`, `:346`                                                          |
| `current()` is per-thread                                 | `Machine.java:104`, `:120`; zero machine `:439`                                                 |
| memory-isolation invariants (stateful scripts)            | `MemoryIsolationScriptTest.java`                                                                |
| network-isolation invariants (stateful scripts)           | `NetworkIsolationScriptTest.java`; `NetworkIdentityTest.java`; `[CAPS]` directives in `AbstractMetatronTest.script` |
| name vs address, and the fix                              | `MemoryUnion.java:118-166`; `Memory.java:194`, `:167`, `:223`, `:254`, `:266`, `:141`           |
| distributed rewrite                                       | `machInstSet.java:543-676`; `COMPUTE` `:97`; `MACH_HOME` `:82`; `sliceOf` `:113`                |
| rewrite reads topology + ships code (impure)              | `machInstSet.java:556`, `:637`                                                                  |
| prefix replicated unconditionally                         | `machInstSet.java:632`                                                                          |
| the impersonating harness                                 | `AbstractMachineTest.java:106`, `:139-168`; workaround `:154-168`                               |
| per-space distributed contract                            | `AbstractMultiServerSingleSpaceTest.java:37`, `:124-153`                                        |
| generated rows                                            | `DistributedRewriteTest.java:44`                                                                |
| cascade / middle machine                                  | `MachineSwarmMigrationTest.java:42`, `:86-128`, mailbox `:59-74`                                |
| barrier: subscribe, read-before-wait, await               | `Obj.java:1289-1314`, `:1316`                                                                   |
| `to(uri)` = `Machine.write`                               | `Obj.java:1450`                                                                                 |
| gather is syntactic; loop termination                     | `Inst.java:374`; `SwarmProcessor.java:253-262`, `:378-415`                                      |
| the distributed triple, stated in code                    | `MonadProcessor.java:28-33`                                                                     |
| Network roster, cluster, status, authority algebra        | `Network.java:284`, `:98`, `:119-139`, `:192-244`                                               |
| Network dispatch has no callers                           | `Network.java:147-175` (grep: none)                                                             |
| `own()` / `selfAuthorities()` absent                      | grep `src/main/java`                                                                            |
| NetworkTest red (current)                                 | `Tests run: 4, Failures: 3` — `testReadAcrossPeer` → `noobj`                                    |
| Network claims a union that does not exist                | `Network.java:49-51`; no `NetworkUnion` (grep)                                                  |
| child shares the parent's resolved network                | `AbstractMachine.java:90-94`; `Machine.java:332-334`                                            |
| executing frame comes from the caller                     | `ThreadExecutor.java:221-241`; `AbstractThread.java:192`                                        |
| `hasAuthority()` needs an explicit port                   | `fURI.java:176-178`                                                                             |
| `localize()` exists                                       | `fURI.java:324`                                                                                 |
| Inst.Form coefficient shapes                              | `Inst.java:60-72`                                                                               |
| `isFold` = reducer/join + `monoidic`                      | `catInstSet.java:663-668`                                                                       |
| `floatable` / `blocked` declared, unconsumed              | `catInstSet.java:851`, `:855` (no consumers)                                                    |
| `sum` monoidic, `mean` magmadic                           | `mInstSetLawTable.java:251`, `:250`                                                             |
| cat family (4) reserved for relation-driven rules         | `catInstSet.java:338-378`                                                                       |
| container run / compose --wait / no exec                  | `dckrSpace.java:592`, `:547-576`; grep: no `"exec"`                                             |
| every backend a container                                 | `boot/spaces.boot.mtron:1-10`; `boot/space/tble/mariadb.mtron`; `boot/space/vec/chromadb.mtron` |
| shared computing workspace, GC on pop                     | `machInstSet.java:455-463`                                                                      |
| `BasicMachineTest.testCloseSpace` pre-existing failure    | verified by reverting this work and re-running                                                  |
