# Processor / Computer architecture + `analysis::T`

**Status:** design agreed 2026-11-03 — implementation starting. §7 extended 2026-11-13: `analyzer::T`, the metrics
foundation every component follows.
**See also:** `distributed_vid_computing.md` (the plan thread's survey), `machine-ring.md`,
`compilation-execution-strategy.md`, the `mach-instset-mtron.md` skill reference.

---

## 0. Thesis

Processor/Computer is metatron's **schema/instance** distinction, applied to *process*. The machine already splits
`instset` (schema: types, insts, laws) from `memory` (instances: values at addresses). This is the same cut, one axis
over:

- **`Processor`** = the *schema of computation* — how a monad splits on coefficient, how a gather forms a barrier, how
  a halted monad's obj is the result. Registered once with the machine; holds **no** per-computation state.
- **`Computer`** = the *instance of computation* — one code + its working set (`cache`). `apply(code)` is
  instantiation; `cache()` is the instance's value, swappable/persistable exactly like any other value.

The payoff of "few methods, few fields": `Component` gives `machine()`, `Rec` gives introspection, `mThread` gives the
lifecycle. The entire new contract surface is **5 methods and 0 Java fields** — everything else is inherited.

---

## 1. Type hierarchy

```
processor::T                     (interface; extends Component — NOT mThread)
   apply(Code) -> Computer        // instantiate (the universal apply verb)
   computers() -> Lst             // the index of minted computers (a Rec field)
   close()                        // reap every computer it indexes
     └─ swarm_processor::T        // the one architecture today
          (future: message_passing_processor, vertex_centric_processor,
                   bulk_synchronous_processor, spark_dataframe_processor)

computer::T                      (interface; extends mThread — the watchdog)
   add(mThread) -> Computer       // add a worker; the computer instructs it
   cache() -> Rec                 // the swappable/persistable state ("L1 cache")
   analyzer() -> analysis::T      // deferred (one accessor, via Component eventually)
     └─ swarm_computer::T         // cache = { run, barrier, halt }
          (future: message_passing_computer, spark_dataframe_computer, …)

DELETED: monad_processor::T       // overkill — too many interfaces, not needed.
```

Each processor architecture has a corresponding computer; the naming is parallel (`swarm_processor` → `swarm_computer`).

---

## 2. The generic contracts

### `processor::T`

```java
/** processor::T — the architecture. A machine component (never a thread). Schema of computation. */
public interface Processor extends Component {
    Computer apply(Code code);   // instantiate: apply this architecture to code → a computing instance
    Lst computers();             // the computers this architecture has minted (its index)
    void close();                // reap every computer it indexes (dual of apply, like index/drop)
}
```

- `apply(code)` mints a Computer in a dormant (`NEW`/`PAUSE`) state; the caller `start()`s it. The "run to
  completion and hand me the halted result" sugar is a `Processor.Helper` convenience (per the Helper gateway rule),
  not a second submit verb.
- `spawn`/`close` is the same resource-pair shape as `index`/`drop` (network) and `push`/`pop` (frames).

### `computer::T`

```java
/** computer::T — the instance. A thread (the watchdog for the chip) whose cache is the state. */
public interface Computer extends mThread {
    Computer add(mThread worker);   // add a worker; the computer instructs it how to be useful
    Rec cache();                    // the swappable, persistable computation state (the "L1 cache")
}
```

The lifecycle is **inherited from `mThread` verbatim** — no re-minted methods:

| mThread method | the computer's meaning |
|---|---|
| `apply(Obj)` / `applyAsync(Obj)` | start (seed + begin the computation) |
| `stop()` | interrupt |
| `pause()` / `resume()` | pause / resume the computation |
| `state()` | run / pause / stop |
| `result()` / `result(timeout, unit)` | await the halted result |
| `source()` / `future()` | provenance + the backing future |

The Computer adds **only** `add(mThread)` and `cache()`.

---

## 3. The swarm pair

```java
/** swarm_processor::T — the one architecture today: a swarm of monads. */
public interface SwarmProcessor extends Processor {
    @Override
    SwarmComputer apply(Code code);   // covariant: mints a swarm computer
}
```

```java
/** swarm_computer::T — an instance whose cache is three monad pools. */
public interface SwarmComputer extends Computer {
    Obj run();      // the running monad pool (the active mailbox)
    Lst barrier();  // the barrier pool (monads parked at a gather)
    Obj halt();     // the halted results
}
```

The swarm *strategy* (split / barrier / gather / halt semantics — today's `runMonadicLoop` + `MonadProcessor.Helper.split`)
lives in the **computer** (the instance), as the instruction `add(worker)` hands a worker. The architecture just mints
computers.

### Concrete classes

- **`BasicSwarmProcessor`** — the architecture, a plain Rec (`extends MRec implements SwarmProcessor`). Zero fields;
  `COMPUTERS` is a jvm-map key. `apply(code)` → `BasicSwarmComputer.of(code)` + index it; `close()` walks the index.
- **`BasicSwarmComputer`** — the instance, a thread (`extends VirtualThread implements SwarmComputer`). Zero fields;
  two jvm-map keys: `CACHE` (the state) and `THREAD` (the workers). `add(worker)` hands the worker the monadic loop,
  parameterized by this computer's `cache()`.

### How today's `SwarmProcessor` decomposes

| today (one class) | lands in |
|---|---|
| `extends VirtualThread` | `BasicSwarmComputer` (the instance *is* the watchdog thread) |
| `runMonadicLoop` + `split` | the swarm strategy inside `BasicSwarmComputer` |
| `/run` `/barrier` `/halted` | `cache()` = `{run, barrier, halt}` |
| `PROCESSORS` / `PROCESSOR_COUNTER` / `infiniteFailCounter` | `processor.analyzer()` / `computer.analyzer()` (deferred) |
| `MonadProcessor` (interface) | **deleted** |

---

## 4. The cache

`cache()` is a **single explicit key** (`CACHE`) — a known component used by code, agents, and humans, addressed at
`/processor/<vid>/cache/<field>`. It is *not* a computed projection. For the swarm computer the cache Rec is
`{run, barrier, halt}`; other architectures have other cache shapes (a BSP superstep accumulator, a message-passing
mailbox, …).

- **swap** = write the one `CACHE` key.
- **persist** = serialize the `cache()` Rec.
- **ship pieces dynamically** (migration) = the watchdog serializes a slice of `cache()` and hands it to another
  computer/peer.

The "L1 cache" analogy: the cache is the *fast, current working set*, distinct from the machine's `memory` (data).

---

## 5. Watchdog vs worker

`computer::T extends mThread` — the computer's own thread (virtual or core) is the **watchdog for the chip**: it owns
interrupt, pause, timeout, and mid-evaluation shipping. `add(mThread)` adds **workers**, which only pull-from-`run` /
apply / write-back. The computer is *a thread that supervises threads*; the cache is what they share; the barrier is
the synchronization point (one thread fires it when `run` is empty and no worker is mid-apply, then `run` refills).

Thread vids move from `/sys/thread` (`CURRENT_SYS_THREAD`, minted in `AbstractThread`'s constructor) to
`/processor/<computer>/thread/<id>` — threads are partitioned by their computer.

```
/processor
   /a3ffe23
     /cache
        /run  /barrier  /halt
     /thread
        /main  /1  /2  /3
   /bddee3
    ...
```

---

## 6. The seven processor directives

1. **Fan-out (fiber bundles) is a type-system concept.** Rec fibers, lst tensors, rel graphs are the "semantic life"
   of the poly types; they are shielded in `Type`, not `Processor`. *Refined:* `Inst.apply(Obj)` goes straight into
   `Processor.Helper.apply`, which is the **meta-realization seam** where three concepts already co-locate — the monad
   qparam flag (`isMonadicInst`), the fiber fan-out (`bundleAndFibers`/`fanOut`), and the one-member-`objs` barrier
   unwrap. The exact home of `fiberedRecApply` / `isFiberedRec` (vs `isBundle`) is **left to settle** as directive #4
   crystallizes; the principle is "contained, one seam, no if/else zoo."
2. **Coefficient multiply-through.** Every wrapping entity's flatmap multiplies the inner's coefficient by the
   wrapper's coefficient. Centralize in one `Obj.Helper` (replacing the three inlined copies in `StatefulMonad.obj/inst/code`).
   Name must match `Tokens.java`.
3. **`StatefulMonad` optimization.** `state()` reads `rec0()` (not `rec()`); `code` becomes a shrinking cursor (pop
   consumed insts when not under `repeat()`), killing the `Code.nextInst` linear scan.
4. **`?query` discipline.** The monad owns exactly `{monad_in, monad_out}` — a closed term-space with an open
   address-space into `[obj, inst, state, code]`. `block`/`bindQ`/`hop`/`split`/`place` are compiler/rewriter concerns,
   never the monad's. `path`/`loop` collapse into `monad_in=state/<field>`.
5. **`X.Helper` gateway.** All convenience behaviors are `public static` in the owning type's `Helper`.
6. **`analysis::T`.** One statistics type, per component, mergeable (monoid), replacing the scattered
   `StatefulMonad.T_*`/`INST_*` statics + `SwarmProcessor.PROCESSORS` + `Memory.stats().monadicStats()`. See §7.
7. **Per-run cost is two linear walks, not recompilation.** `Inst.resolve` short-circuits on `hasf()`; the cost is the
   resolve walk + the initial/barrier seed walk. Precompute both into a compiled artifact.

---

## 7. `analysis::T` — a mergeable, Grafana-ready statistics type

### The general concept

Every metric system converges on the same meter kinds:

| kind | meaning | merges how | Prometheus type |
|---|---|---|---|
| **counter** | monotonic count | **sum** | `counter` |
| **gauge** | point-in-time value | snapshot (not a monoid) | `gauge` |
| **timer** | duration distribution `{count, total, min, max}` | `count+count`, `total+total`, `min(min)`, `max(max)` | `histogram` (`_count/_sum/_min/_max`) |
| **histogram** | full distribution with buckets | bucket-wise sum | `histogram` |

Counter and timer are the two clean monoids. **Derived metrics** (rates, ratios, averages, percentages — e.g.
`compression = coeff_sum/monads`, `hit% = hits/(hits+misses)`) are **computed on read, never stored** — the same
"derived on read" law as `network/status`. That keeps the stored core to counter + timer, which *is* the mergeable
monoid.

### The scheme

```
analysis::T  (a Rec)
  key   = stat group   (uri: flow, apply, resolve, rewrite_rule, cache, per_instruction)
  value = a Rec of probes
    key   = probe      (uri; may be multi-segment: plus/in_monad, theory_unit_removal/time)
    value = a measurement, one of:
        counter::N                                            — an int, monotonic
        gauge::X                                              — a real, point-in-time (deferred)
        timer::[count=>int, total=>dur, min=>dur, max=>dur]   — a duration distribution
```

The measurement's **tid is its kind**, so both the merge and the serializer dispatch on tid — never a `kind` field, no
if/else zoo.

### The interface

```java
/** analysis::T — a component's statistics: a mergeable Rec keyed group → probe → meter. */
public interface Analysis extends Rec {
    Analysis incr(fURI group, fURI probe, long delta);   // counter
    Analysis record(fURI group, fURI probe, long nanos); // timer (count/total/min/max)
    Analysis set(fURI group, fURI probe, double value);  // gauge (deferred)
}
```

The monoid — the `XXXUnion` merge-up — is `Analysis.Helper.merge(a, b)`: union groups, union probes, combine per **tid**
(counter → `+`; timer → count/total/min/max; gauge → last-write). That one `Helper` is what makes
`computer.analyzer() ⊎ computer₂.analyzer() → processor.analyzer() ⊎ … → machine.analyzer()` fall out — the same walk
as `Component.Prefix` and the InstSet union.

### The `profile()` rec is the prototype

`1.plus(2).mult(5).profile()` re-encodes as:

```
analysis::[
  stage => [ rewrite=>timer::[…], resolve=>timer::[…], apply=>timer::[…] ],
  insts => counter::3,
  resolve => [ inst_resolve=>timer::[…], generic_binding=>timer::[…], inst_composition=>timer::[…] ],
  rewrite_rule => [ theory_unit_removal/in=>counter::60, theory_unit_removal/out=>counter::60,
                    theory_unit_removal/time=>timer::[…], … ],
  apply => [ split=>timer::[…], apply=>timer::[…], next=>timer::[…] ],
  per_instruction => [ plus/in_monad=>counter::1, plus/out_monad=>counter::1, plus/time=>timer::[…], … ],
  flow => [ monads=>counter::15, coeff_sum=>counter::10, processors=>counter::10 ],
  cache => [ hits=>counter::160192, misses=>counter::0 ]
]
```

### Grafana / OpenMetrics mapping (the serializer's contract)

Prometheus is `name{labels} value` + `# TYPE`. The mapping is mechanical:

- metric **name** = `group_probe` (join with `_`): `flow_monads`, `apply_split`, `cache_hits`.
- a **timer** fans into four series: `apply_split_count`, `apply_split_sum`, `apply_split_min`, `apply_split_max`
  (a histogram with `le` buckets later).
- **provenance** (`machine`, `computer`, `timestamp`, per-rule/per-inst names) → **labels**, not names — so
  `rewrite_rule_in{machine=…, rule="theory_unit_removal"}` stays queryable as a dimension.
- `# TYPE <name> counter|gauge|histogram` from the meter's tid.

An `ObjGrafanaSerializer` walks `analysis`, emits `# TYPE` + `name{labels} value` per meter, fanning timers into four
series. Graphite/StatsD fall out of the same walk.

### The analyzer — the metrics foundation (Marko, 2026-11-13)

`analysis::T` is what a component has measured; **`analyzer::T`** is the component that measures it, in the form of the
compiler's typer and the processor's tracer. Every metric in metatron follows these rules.

1. **One shape.** A metric that leaves its producer is an `analysis::T`: `group => [probe => meter]`, a probe nesting
   as deep as its path (`per_inst/0/time` is `per_inst => [0 => [time => timer]]`, so a read walks it). The meter's
   tid is its kind: `counter::T` (int), `gauge::T` (real), `timer::T` (`[count, total, min, max]`, nanoseconds).
   Derived values are computed on read. `Analysis.Helper.merge` is the monoid (counters add, timers combine, a gauge
   keeps the later reading), so a machine's analysis is the merge of its components' and a cluster's of its machines'.
2. **Owner-held, at the owner's address.** A component carries its analyzer under `analyzer`
   (`Component.analyzer()`), and the machine's space answers `<vid>/<component>/analyzer/…` for every component, the
   network's included (`MachineSpace`):

   ```
   analyzer::[analysis => analysis::T, step => int::T, profile => bool::T]

   *~/network/analyzer/analysis/wire/sent           [-- read live: the meters as they stand --]
   ~/processor/analyzer/profile -> true             [-- a write sets a setting in place --]
   ~/network/analyzer -> [step=>1000]               [-- a write AT the analyzer declares settings, never replaces it --]
   ~/processor/analyzer -> noobj                    [-- OFF: the analyzer is removed, and nothing records into it --]
   ~/processor/analyzer -> [profile=>true]          [-- ON: a rec written where there is none installs a new one --]
   ~/processor/analyzer/analysis/time -> noobj      [-- mute one probe and its subtree; any other value lifts it --]
   *~/processor/computer/7/analyzer/analysis        [-- a profiled evaluation's statistics, at its computer --]
   ```

   The analysis is derived as it is read (an `auto` closing over the analyzer, the `~/network/status` form) and takes
   no write. No global registry, no statics.
3. **Recording never writes a rec.** The producer calls `incr` / `record` / `set` in Java, where the thing it counts
   happens. The meters are lock-free cells (`LongAdder`, atomic min/max): a socket frame or a monad step pays an atomic
   add, never a map copy, a type check or a store write — which is what `Rec.at(k, v, MUTABLE)` costs, per event. The
   rec is built from the cells when someone reads it.
4. **Costly instrumentation is off by default; cheap counters are always on.** A component's costly probes run only
   while a run is profiled: `Analyzer.PROFILE` is the analyzer the current thread's run is profiled into, and a probe
   reads it once and measures only when it is set (one thread-local read when it is not). `profile()` scopes a fresh
   analyzer around its own timed runs; a processor whose analyzer has `profile => true` mints each evaluation under an
   analyzer of the evaluation's own; a computer carries the profile it was minted under to whichever thread runs it.
   **A profile is held by the first computer minted under it** (`Analyzer.claim`, 2026-11-23), at
   `~/processor/computer/<n>/analyzer` beside the computer's code and fails: the evaluation's own computer for a
   processor that profiles, the first timed run's for `profile()`. Nested computers record into it without holding it.
   The processor's own analyzer keeps the machine's totals instead, always on: `eval` counts each computer's run as it
   starts, `time` times it to its end, `fail` counts each uncaught fail a run halts with — so, as `eval` counts a nested
   run, a fail a nested evaluation raised counts again in each run it fails, and a boundary that files the answer again
   (the console's, a reply's) counts nothing. A profile is written as a component's analyzer is, at its computer's
   address (`~/processor/computer/<n>/analyzer/analysis/flow -> noobj`). The totals are the processor's, so
   they outlive the computers — a FIFO cap on `~/processor/computer/+` (Marko's, in design) evicts a computer's
   profile and leaves the totals.
5. **Pull is the read; push is `?subq`.** Subscribers use the space's own pubsub:
   `~/network/analyzer/analysis?subq -> sub::[code=>…]`, or `~/+/analyzer/analysis?subq` for every component (one
   registry — the machine's space — answers them all). A change schedules one publication a `step` later (default 250
   ms) and every change until then rides it: a subscriber hears at most one `pub::[<address>, analysis::T]` per step,
   carrying every change before it, and a quiet analyzer publishes nothing. Delivery is `QCollection.publish`, the
   same one a write's broadcast uses. No Java listener lists. **An observer's work is counted, never announced**
   (Marko, 2026-11-23): a code subscriber is evaluated on each delivery, and no publication can include the cost of its
   own delivery, which exists only after it — announcing it published forever, one delivery and one computer per step.
   `Analyzer.OBSERVING` marks a publication's thread and every thread it starts (`ThreadExecutor` carries it beside
   the frame of reference); a marked recording counts and schedules nothing. A subscriber so hears each change something
   else makes once, a delivery behind its own cost, which a pull reads now. `?subq` is untouched: a subscriber that
   writes what it subscribes to wrote its own loop. A reply to an observer's round trip is the observer's work too: it
   arrives on the socket library's own thread, which no executor marked, so the trip's waiter records the mark and,
   while one is outstanding on a socket, every frame arriving there is counted, read and handed on under
   `Analyzer.REPLY`, a mark of that thread alone: an unrelated frame in that window is announced with the next change,
   and what it starts runs unmarked. A reply that arrives after its trip's deadline is no longer the observer's and
   announces, so a peer slower than the deadline costs one publication per deadline. A request another machine sends
   is that machine's work, so two machines whose subscribers push to each other, or a machine that dials itself, still
   trade at most one publication per step each.
6. **Export is a serializer** over `analysis::T`, as mapped above — not a second collection path.
7. **Events are not meters.** A record of individual occurrences (`WireTrace`'s exchanges) is a bounded ring a human
   reads; the counts behind it are the producer's meters, counted at the same tap.

### The recording verbs and `?analq` — the recording side (2026-11-15, revised 2026-11-16 and 2026-11-23)

`Analyzer` is the *instrument*. A component that OWNS one is an `Analyzer.Aggregator` (`Component` extends it; the
network and the processor carry one); a server records into the one its own `?analq` q-proc holds; a profiled run
records into `Analyzer.PROFILE`.

- **`Analyzer.Helper`'s verbs are how a producer records** (Marko, 2026-11-23: no if/else statistics code interleaved
  with an object's work). Each is handed the analyzer to record into and gates itself, so an absent analyzer (`null`:
  none kept, or switched off by a `noobj` write) records nothing and a wrapped block runs bare. **Off is `noobj`**
  (Marko, 2026-11-23): written at the analyzer it removes it — a processor switched off has no `profile` to read, so it
  profiles nothing as well — and a rec written there later installs a new one, whose meters start at zero (a component
  that samples a gauge registers it with every analyzer it is given, `Aggregator.analyzer(Analyzer)`). Written at
  `analysis/<probe>`, it mutes that probe and its subtree: the analyzer skips it, drops what it counted, and hides a
  sampled gauge there rather than dropping it; any other value written at the probe lifts the mute. The mutes are read
  at `*~/processor/analyzer/mute`, derived as the analysis is, and a pushed machine's analyzer starts with its parent's,
  as it starts with its settings. What would change nothing is refused rather than answered: a bool written at the
  analyzer, a mute of a key inside a meter's reading (`analysis/time/count`), a write at a derived field. Which analyzer
  is the gate's choice:
  the owner's for the cheap meters that are always on (`network.analyzer()`, the processor's evaluations, a server's
  `analyzer()`), the run's
  (`Analyzer.PROFILE.get()`) for the costly ones a profile switches on. A probe is one path (`wire/recv`,
  `resolve/inst_resolve`), held in a `static final` so a probe nobody measures costs no `f()`.

  | verb | meter |
  |---|---|
  | `incr(analyzer, probe, delta)` | counter |
  | `set(analyzer, probe, value)` | gauge, set at an event |
  | `record(analyzer, probe, nanos)` | timer, a duration measured elsewhere |
  | `timed(analyzer, probe, () -> work)` | timer around a block, answering what the block answers |
  | `start(analyzer)` … `stop(analyzer, probe, t0)` | timer around a region, with no lambda: a hot path, or one with early exits |
  | `gauge(analyzer, probe, () -> sample)` | gauge sampled each time the analysis is read: it can miss no event, and nothing announces it |

  A probe named by runtime data (`per_inst/<vid>/…`, `rewrite/<rule>/…`) is built only while a run is profiled, so its
  call stays behind the one `null != profile` the path needs. The per-step probes (`StatefulMonad`, the computer's
  split and barrier, `repeat()`) still record directly; they move to the verbs once the per-step cost is measured.
- **A data space** records through the `?analq` q-proc it declares (`QCollection.analQ(cap)`): `QProc.Layer` counts and
  times every read and write handed to the space, deletes as a subset of the writes, and the space answers those
  totals at `*<pattern>/#?analq` (`read => [count, latency], write => [count, latency, delete => [count, latency]]`). An
  access that carries a query is metadata traffic and is not counted.
- **An address asks to be measured** (Marko, 2026-11-23): `/usr/marko/abc?analq -> analysis::[read=>counter::T]`.
  - **A request is the type of its answer.** Its keys are events (read, write, delete, miss) and its values the meter
    each is read as, so `*/usr/marko/abc?analq` answers `analysis::[read=>counter::12]`.
  - **The space's `analq::[…]` is its cap**, the widest request it allows (`analq::[read=>timer::T, max=>100]`). A
    request fits when each event it asks for is one the cap names, measured no more finely (counter <= timer).
  - **Defaults.** An empty cap allows every event as a timer, and an empty request takes the whole cap.
  - **Refusals and withdrawal.** A request beyond the cap, or an address past `max`, gets a `fail::T`. A concrete
    request holds its `max` slot from the start, and a cap that states no event (`analq::[max=>100]`) allows every event
    as a timer. noobj withdraws a request, answered with an empty analysis: what no standing request still covers is
    reset, event by event, and a nested address keeps its own.
  - **The address is the scope.** A pattern request (`abc/#?analq`) measures each address it covers, each on its own.
    The readings nest under `addr/<path>` in the analyzer. The most specific pattern governs an address (more
    segments, then more concrete ones, `+` before `#`), and a reading is always the kind its governing request asks for:
    an event whose kind changes counts again from zero. The cap and the requests are held in the analyzer's rec (`cap`,
    `request`).
  - **Reads.** An address that asked answers its own readings, `<addr>/+?analq` its children's. Any other `?analq`
    read answers the space's whole analysis, as every one did before.
- **Memory is a space whose addresses are its spaces.** Every access memory hands to a covering space passes through
  memory's own q-procs (`Memory.through`), at the space's address.
  - With `/memory/q -> [analq::[=>]]`, a space asks at its mount
    (`/memory/space/logs?analq -> analysis::[read=>counter::T]`) and declares nothing itself.
  - It answers there, the spaces at `/memory/space/+?analq`, and memory's totals at `/memory/#?analq`.
  - An analyzer is read where its holder is: the holder's own (`~/network/analyzer`), else the one its `?analq` holds
    (`Addressable.analq()`): `~/memory/analyzer`, `~/memory/space/<s>/analyzer`.
- **A server** (`http_server`, `ws_server`, `mqtt_server`) takes the q-procs its config declares on a socket it binds (`Listener.declare`, an analq replacing the built-in one; a config that joins a held socket changes nothing). It is an addressable source carrying its own `?analq` from
  construction (`Listener`), so `*<origin>/#?analq` answers that socket's analysis: a request or a received message is
  a read at its wire address (timed over its whole answer and counted with that latency once the answer is given;
  only the wire, `wire/recv`, counts a frame as it arrives), a sent message a write, a hang-up a write of `noobj`, and
  `connections` a gauge set through the verbs. Two servers on one machine never share meters.
- The 2026-11-15 design bound each server to a shared bucket under the network's analyzer
  (`analyzer => ~/network/analyzer/web_analyzer/http`, resolved by `Analyzable.Helper.locateBase`). The per-socket
  `?analq` replaced it on 2026-11-16, and the verbs replaced the `Analyzer.Recorder` role, its `group`, its Helper and
  `locateBaseAnalyzer` on 2026-11-23.

**Producers today.**

| component | probe | kind | where it is counted |
|---|---|---|---|
| network | `wire/sent`, `wire/recv` | counter | `WebSocketObj.send`; a frame as it arrives, before it is read or run (`wsServer.evaluate` for a server's session, `WebSocketRecClient.received` for a client) |
| network | `peer` | gauge, sampled when read | `BasicNetwork`, registered with each analyzer the level is given: the peers it holds |
| processor | `eval`, `time` | counter, timer | `BasicSwarmComputer`'s run: counted as it starts, timed to its end, nested evaluations included |
| processor | `fail` | counter | `BasicSwarmComputer`, each uncaught fail a run halts with, beside its filing in the computer's stack |
| a listener or a data space (`?analq`) | `read/{count, latency}`, `write/{count, latency}`, `write/delete/{count, latency}` | counter, timer | `QProc.Layer`, around the source's own access |
| a listener | `connections` | gauge | `wsServer`, `mqttServer`, as sessions open and close |
| an mqtt server | `messages/{received, sent}`, `packets/{received, sent}` | counter | `mqttServer`, through the verbs (its `$SYS` counters read them) |
| processor (profiled) | `apply/split`, `apply/apply`, `apply/next` | timer | `BasicSwarmComputer.split`, `StatefulMonad.apply/next` |
| processor (profiled) | `flow/monads`, `flow/c_sum`, `flow/processors` | counter | the monad loop, each computer minted |
| processor (profiled) | `per_inst/<vid>/{monad_in, monad_out, c_in, c_out}`, `…/time` | counter, timer | `StatefulMonad`, the barrier |
| processor (profiled) | `resolve/{inst_resolve, generic_binding, inst_composition}` | timer | `SpecificitySelector`, `GenericBinder` |
| processor (profiled) | `rewrite/<rule>/{in, out, time}` | counter, timer | `FixPointRewriter`, per rule |
| processor (profiled) | `repeat/{esc, emit, until, code, state}`, `repeat/loop` | timer, counter | `repeat()` |
| processor (profiled) | `cache/{hits, misses}` | counter | `TypeGraph.memo`, per type lookup |

"Profiled" is the analyzer in `Analyzer.PROFILE`: the one `profile()` scopes around its timed runs, or a fresh one per
evaluation while the processor's `profile` setting is on. Either is held by the first computer minted under it, so its
probes are read at `~/processor/computer/<n>/analyzer/analysis`.

The status line's wire gauge (`↓ ↑`) and the network panel's summary read the network analyzer's `wire/recv` and
`wire/sent`, and the panel repaints as a `?subq` subscriber to it. `profile()` builds its whole `profiling::T` report
from its scoped analyzer, the `cache` section included: the type graph's hits and misses during the timed runs.

**Retired.** The analyzer's `enabled` setting (2026-11-23): off is a `noobj` write, at the analyzer or at one probe of
its analysis. `WireStats` (static atomics with a private listener list); the network analyzer's `wire/trip` and
`wire/timeout` (the raw round-trip timer and deadline-miss counter), retired 2026-11-15 when the servers moved onto the
`web_analyzer` buckets, and on 2026-11-22 their last readers: the status line's `⇄` count and the network panel's
summary `⇄`/`✗` (a lane's own exchange and timeout counts are WireTrace's rows, and stay). The type graph's own
hit/miss/skip counters, which `profile()` zeroed with `resetStats()` for every evaluation on the machine (2026-11-22:
the hits and misses are probes on the profile's analyzer). The process-global profiling accumulators
(`StatefulMonad.T_*`/`INST_*`/`MONADS`/`COEFF_SUM`/`T_REPEAT_*`, `ScoringResolver.T_*`,
`FixPointRewriter.REWRITE_*`, `BasicSwarmComputer.PROCESSORS`), which every evaluation paid for in the monad loop and
every `profile()` reset for everyone; and `Stats`/`MStats`/`IOStats`/`MonadicStats`, which recorded nothing —
`Memory.stats()` minted a fresh `MStats` per call, per monad.

**Next.**

- `by=>peer::T`, a request broken down by who made each access (Marko, 2026-11-23): deferred, because
  `Machine.read`/`write` carry only a uri and an obj, and who is asking is known only by thread;
- the analyzer as a function of addressability, `Addressable.analyzer()` (a thought of Marko's, 2026-11-23). Today
  a component's own analyzer (`Aggregator.analyzer()`) and its `?analq`'s (`Addressable.analq()`) are named apart:
  memory and the network are both;
- an instset and `mqttSpace` run their own q-proc sequence and never count a read;
- in a pushed machine, memory's `?analq` counts at the level that routes the access: a hit on a parent level's space
  is counted by the child's union only, and a child's request for an inherited space never matches, since the space is
  counted at the parent's mount;
- an address with a segment named after an event (`…/read`) is measured in its space's totals only, since its readings
  would nest where its parent's reading is; a wildcard access names no address and counts in the totals alone;
- the OpenMetrics serializer and an `http_server` route over `~/+/analyzer/analysis` (the Grafana path);
- the query-execution timer is the processor's `time` (2026-11-23), and with `eval` it counts every computer's run,
  nested argument evaluations included — open: whether the evaluations a user submits (`Machine.evaluate`, the
  console's executor) want a count of their own;
- an evaluation's uncaught fail is filed twice (review, 2026-11-23): the computer files a stamped copy in its stack and
  halts the unstamped original, so the console, a ws or http reply (`MFail.reported`) files it again in
  `~/processor/fail`, and an enclosing computer files a nested evaluation's fail in its own stack — open: whether a
  computer halts the copy it filed, which would also give a reported fail its computer's address;
- more producers, each at its own analyzer: memory (writes, deletes, reads per space), `http_server` (requests by
  route, status, latency), `tble`/`dcmnt` (query time, index use), the compiler (resolve/rewrite outside a profile);
- a gauge read on demand (open sockets per listener) — a supplier-backed meter, sampled as the analysis is built;
- bytes on the wire (only the send tap holds them today);
- labels: how a probe names a dimension (peer, route, rule) so the serializer emits `name{label=…}` rather than a
  name per value.

---

## 8. Open / deferred

1. **`fiberedRecApply` / `isFiberedRec` home** — settles with directive #4; keep the meta-apply seam (directive #1) and
   the `?query` discipline (directive #4) coupled.
2. ~~**`analysis::T` wiring**~~ — done 2026-11-13: the meter types are registered, `Component.analyzer()` answers
   the component's `analyzer::T`, and the network and processor carry one (see "The analyzer" in §7). `histogram`
   remains the advanced kind.
3. **`apply` auto-start** — does `apply(code)` mint dormant or running? (Dormant + explicit `start()` is the working
   shape.)
4. **Compiled artifact** (directive #7) — the precomputed successor chain + initial/barrier indices; overlaps the
   `compilation-execution-strategy.md` roadmap.
5. **`cache` name collision** — the profile's `cache` field (compilation memoization) vs the computer's L1 `cache()`.
   Distinct names needed.

## 9. Coordination with the plan thread

`distributed_vid_computing.md` (§7) flags processor addressability as its "expressivity wall." This design resolves it:
the addressable unit is the **Computer** (not the Processor), and its `cache` (a Rec) makes the pools addressable
(`to(~/processor/_?incrq)` mints a computer; `to(<computer>/cache/run)` enqueues a monad). Settled shared boundary
(directive #4): `?hop`/`?split`/`?place` are code-level q-procs the plan reads, never the monad — the monad owns
`{monad_in, monad_out}` only.
