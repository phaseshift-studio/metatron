# Processor / Computer architecture + `analysis::T`

**Status:** design agreed 2026-11-03 — implementation starting.
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

---

## 8. Open / deferred

1. **`fiberedRecApply` / `isFiberedRec` home** — settles with directive #4; keep the meta-apply seam (directive #1) and
   the `?query` discipline (directive #4) coupled.
2. **`analysis::T` wiring** — `Component.analyzer() -> analysis::T` (one method on `Component`, inherited by all).
   Deferred until the meter types (`counter`/`gauge`/`timer`) are registered. `gauge` + `histogram` are the non-monoid /
   advanced kinds.
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
