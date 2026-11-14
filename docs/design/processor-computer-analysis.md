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
   analyzer around its own timed runs; a processor whose analyzer has `profile => true` mints every computer under
   its own analyzer; a computer carries the profile it was minted under to whichever thread runs it.
5. **Pull is the read; push is `?subq`.** Subscribers use the space's own pubsub:
   `~/network/analyzer/analysis?subq -> sub::[code=>…]`, or `~/+/analyzer/analysis?subq` for every component (one
   registry — the machine's space — answers them all). A change schedules one publication a `step` later (default 250
   ms) and every change until then rides it: a subscriber hears at most one `pub::[<address>, analysis::T]` per step,
   carrying every change before it, and a quiet analyzer publishes nothing. Delivery is `QCollection.publish`, the
   same one a write's broadcast uses. No Java listener lists.
6. **Export is a serializer** over `analysis::T`, as mapped above — not a second collection path.
7. **Events are not meters.** A record of individual occurrences (`WireTrace`'s exchanges) is a bounded ring a human
   reads; the counts behind it are the producer's meters, counted at the same tap.

**Producers today.**

| component | probe | kind | where it is counted |
|---|---|---|---|
| network | `wire/sent`, `wire/recv` | counter | `WebSocketObj.send`, `WebSocketRec.onMessage` |
| network | `wire/trip` | timer | `WebSocketObj.sendRecv`, when the reply lands |
| network | `wire/timeout` | counter | `WebSocketObj.sendRecv`, when the deadline passes |
| processor (profiled) | `apply/split`, `apply/apply`, `apply/next` | timer | `BasicSwarmComputer.split`, `StatefulMonad.apply/next` |
| processor (profiled) | `flow/monads`, `flow/c_sum`, `flow/processors` | counter | the monad loop, each computer minted |
| processor (profiled) | `per_inst/<vid>/{monad_in, monad_out, c_in, c_out}`, `…/time` | counter, timer | `StatefulMonad`, the barrier |
| processor (profiled) | `resolve/{inst_resolve, generic_binding, inst_composition}` | timer | `SpecificitySelector`, `GenericBinder` |
| processor (profiled) | `rewrite/<rule>/{in, out, time}` | counter, timer | `FixPointRewriter`, per rule |
| processor (profiled) | `repeat/{esc, emit, until, code, state}`, `repeat/loop` | timer, counter | `repeat()` |

The status line's wire gauge and the network panel's summary read the network analyzer, and the panel repaints as a
`?subq` subscriber to it. `profile()` builds its `profiling::T` report from its scoped analyzer.

**Retired.** `WireStats` (static atomics with a private listener list); the process-global profiling accumulators
(`StatefulMonad.T_*`/`INST_*`/`MONADS`/`COEFF_SUM`/`T_REPEAT_*`, `ScoringResolver.T_*`,
`FixPointRewriter.REWRITE_*`, `BasicSwarmComputer.PROCESSORS`), which every evaluation paid for in the monad loop and
every `profile()` reset for everyone; and `Stats`/`MStats`/`IOStats`/`MonadicStats`, which recorded nothing —
`Memory.stats()` minted a fresh `MStats` per call, per monad.

**Next.**

- the OpenMetrics serializer and an `http_server` route over `~/+/analyzer/analysis` (the Grafana path);
- a query-execution timer — open: whether nested argument evaluations (each a computer) belong in it, or only the
  evaluations a user submits (`Machine.evaluate`, the console's executor);
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
