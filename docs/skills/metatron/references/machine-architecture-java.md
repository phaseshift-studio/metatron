---
name: machine-architecture-java
description: Java-side Machine architecture — Machine, its five components (Memory, Network, InstSet, Compiler, Processor), the frame/ComponentUnion nesting model, push/pop/move, the compile→run pipeline, threads/monads, and the load-bearing invariants of the read path
---

# Machine Architecture (Java)

## What a Machine is

A **Machine** is a *space of spaces* — the container in which address resolution happens. It is itself an address
space (`Machine extends Space`), and its five members (`instset`, `memory`, `network`, `compiler`, `processor`) are rec
entries shaped as `machine::T`. Of the five:

- **Memory** is the facade over the world of spaces — the single read/write surface.
- **Compiler** lowers code; **Processor** runs it; neither is an address space.
- **InstSet** and **Network** are *accumulating* components (a frame wraps them); **Compiler** and **Processor** are
  *constructive* (whole-value, inherited or replaced).

A machine replaced the old `Router` as the root of the address space. `Router.java` is gone; `Machine extends Space`,
and `AbstractMachine extends AbstractSpace` is the one concrete body.

## Package overview

```
isa/mach/type/Machine.java            ← Machine interface + Frame + Component + Machine0 (the contract)
isa/mach/type/Memory.java             ← Memory interface (relative bindings + absolute space index)
isa/mach/type/Network.java            ← Network interface (peer roster + authority arithmetic)
isa/mach/type/Compiler.java           ← Compiler interface (rewrite → resolve → type) + resolve memo
isa/mach/type/Processor.java          ← Processor interface + Processor.Helper (the runtime core)
isa/mach/type/ComponentUnion.java     ← frame wrapper: previous()/current(), read-through, close-current
isa/mach/type/MemoryUnion.java        ← frame memory (binary read-through, pattern-aware at())
isa/mach/type/NetworkUnion.java       ← frame network (binary read-through)
isa/mach/type/InstSetUnion.java       ← frame ISA (binary read-through)
isa/mach/type/machine/AbstractMachine.java  ← the concrete body: routes, authority guard, read/write
isa/mach/type/machine/BasicMachine.java     ← concrete machine; cached compiler/processor; slot templates
isa/mach/type/machine/BasicMemory.java      ← one level of memory (no Java fields)
isa/mach/type/machine/BasicNetwork.java     ← one level of network (roster is the rec)
isa/mach/type/machine/BasicInstSet.java     ← the machine's OWN instset (n-ary union of imports)
isa/mach/type/compiler/…                     ← Compiler impls + rewriter/resolver/typer stages
isa/mach/type/processor/…                    ← Processor impls + Monad/MonadProcessor/SwarmProcessor + monads
isa/mach/type/thread/…                       ← mThread / AbstractThread / VirtualThread / CoreThread / FutureObj
isa/mach/machInstSet.java                    ← type registration (machine::T etc.) + bootstrap + sum_gather rewrite
```

## The five components

| Component | Interface (extends) | One-line job | Frame behaviour |
|---|---|---|---|
| Memory | `Space, Machine.Component, Closeable` | relative bindings (names) + absolute index of spaces | accumulates (`MemoryUnion`) |
| Network | `Machine.Component, Closeable` | peer roster `authority → transport`, `own`/`isPeer` | accumulates (`NetworkUnion`) |
| InstSet | `Space` (via `AbstractInstSet`) | visible types/insts; `import` lands here | accumulates (`InstSetUnion`) |
| Compiler | `Machine.Component, Rec` | `code::T → code::T` in three stages | constructive (inherited/replaced) |
| Processor | `mThread, Machine.Component` | `code::T → obj`; the monadic execution engine | constructive |

`Machine.Component` is a `Rec` that answers `machine()` by walking its `parent()` chain up to the nearest `Machine`
(falling back to `mach0()`). Components are `Rec`s *on purpose*: memory and network must be reachable from mtron
(`>>space`, `>>previous`), not just from Java accessors.

## The three accessor levels — the part that confuses

Every accumulating component has (up to) three ways to reach it, and the distinction is load-bearing:

1. **`own*`** — the machine's own slot, ignoring any frame. `ownMemory()` / `ownInstset()` / `ownNetwork()`. The slot
   holds a *template* (an `instLambda`), and `own*` applies it once and caches.
2. **frame-aware** — `memory()` / `instset()` / `network()`. Returns the current frame's union when a frame is live,
   else the machine's own slot.
3. **resolution** — `resolutionMemory()` / `resolutionNetwork()`. The one the read path uses: the frame's view *if it
   has already been materialized*, else the machine's own. It **never materializes a frame**, because materializing
   constructs a component, constructing an `Obj` runs a type check, and the type check reads through the machine —
   which lands back in `read()`. The non-allocating read-only helpers are `frameMemory()` / `frameNetwork()` and the
   `Frame.*View()` accessors.

`AbstractMachine` caches the three `own*` results in Java fields (`resolvedMemory`/`resolvedNetwork`/
`resolvedInstSet`) so the read path sees a field read, not a template re-apply.

## Frames and the component union (nesting)

A **Frame** (`Machine.Frame`) is one level of nesting: the machine it belongs to, its parent frame, and lazily
materialized `instset`/`memory`/`network` views. Two thread-locals:

- `FRAME` — the cursor (walks *up* only).
- `FRAMES` — the address index `Map<fURI, Frame>` (the downward link the cursor can't provide).

`push()` clones the machine into a **child** whose vid strictly extends the parent's (`descendsFrom`), wraps it in a
new `Frame`, and makes it current. `push(extension)` mints a unique vid from the extension. `pop()` restores the parent
and closes the frame (releasing only what the frame introduced). `move(extension)` unifies push and pop via the
coefficient's sign: positive = descend, negative = ascend one step.

A frame wraps each *accumulating* component in a `ComponentUnion` (`previous()` + `current()`), reads **through**
(own first, then parent), writes only to `current()`, and `close()`s only `current()`. Release is therefore structural:
a popped frame takes its own spaces/peers/imports with it, and can never close what it inherited.

**`BasicInstSet` is the exception — it is n-ary, not binary.** Memory and network are a fixed two-level tree; an ISA is
an *ordered list of imported instsets held by reference*, own structure layered over them. Import order is precedence,
own-first shadows a referenced name, and writes reach only the own structure — which is what makes a shared library
instset safe to import from many machines.

## How one evaluation flows

```
machine.apply(code, start)
  → Machine.current(this)                 // move this thread's perspective
  → push()                                 // a fresh frame
  → processor().code(compiler().apply(code).asCode()).apply(start)
  → pop()                                  // release the frame
```

`BasicMachine.of(...)` seeds the five slots as `instLambda` templates. `compiler()`/`processor()` apply the template,
cache the prototype, and return a `.clone()` (fresh instance per caller). Every read/write funnels through
`AbstractMachine.read/write`:

```
read(vid) → authority guard (dispatchForeign) → alignPrefix → stack check → resolutionMemory().readAbsolute(vid)
```

`Memory.readAbsolute` asks `getSpaceFor(vid)`: the most specific space across the chain, else the machine itself (for
keys it actually holds — `rootRelative` turns `/processor` into `processor`), else fail/noobj. The machine answering for
its own rec keys is what makes `/` the root with no space mounted inside itself.

## The compiler (three stages)

`Compiler.apply` is the default schedule `rewrite → resolve → type` over three overridable stage components:

- **rewrite** — `Rewriter` (default `FixPointRewriter`, fixpoint over the rewrite rules).
- **resolve** — `Resolver` (default `ScoringResolver`; threads the type one inst at a time; generic binding runs inside
  per-candidate selection, so there is no separate binder).
- **type** — `Typer` (default `TypeTyper`, runtime type assertions).

`Compiler.Helper.resolve` memoizes nested-code resolution keyed by `(code identity, runtime lhs type id)`
(`RESOLVE_CACHE`), delegating to `ScoringResolver.resolveCode` + `FixPointRewriter`.

## The processor (monadic execution)

`Processor` is both an `mThread` and an `Obj`. `Processor.Helper` holds the runtime core that used to live on `Inst`:
`apply` (resolve → `invoke`), `invoke` (noobj/domain guards, coefficient compression, range check), `applyArgs`
(computeArgs), `resolveRuntime` (the `TypeCheck.code_resolve`-gated dynamic escape).

The default processor is **`SwarmProcessor`** (extends `VirtualThread`, implements `MonadProcessor`), a monadic step
loop over three queues stored in its rec:

- `run` — the active monad queue (`ListMonad`).
- `barrier` — monads parked at a gather/barrier.
- `halted` — collected results (`RESULT` is an auto-pointer to `halted`).

A **`StatefulMonad`** is a four-tuple `[obj, inst, state, code]` carried as an `Lst`; `apply()` feeds the inst's
`monad_in` lens to `Processor.Helper.apply`, then `next()` walks to the successor inst. `Monad` itself is the minimal
`obj`/`inst` interface with `halted`/`dead`/`zombie` states. `RunningMonads` is an inst-indexed running set (a
coefficient-summing view), and `ListMonad` the list-backed queue.

## Threads

`mThread` is the unified lifecycle contract (`stop`/`pause`/`resume`/`state`/`apply`/`applyAsync`/`result`/`future`).
`AbstractThread` implements it over an `MRec` + `FutureObj` and hands async work to `ThreadExecutor`; `VirtualThread`
and `CoreThread` are the two concrete platform strategies. A worker publishes where it ended via `landedPerspective()`.

## Bootstrap and the type hierarchy

`machInstSet.setup()` registers the whole `machine::T` family under `/m/mach/component/…` — `processor::T`,
`monad_processor::T`, `swarm_processor::T`, `compiler::T`, `default_compiler::T`, the rewriter/resolver/typer families,
`memory::T`, `network::T`, and `machine::T` (whose shape is `instset`/`compiler`/`memory`/`network`/`processor`). The
default machine lives at `/sys/mach/default` (a `const` built by `BasicMachine.of`). `sum_gather` — the rewrite that
turns a monoidic reducer into a distributed gather over `/usr/compute` peers — lives here too.

## Load-bearing invariants (keep these if you simplify)

- **The read path must never allocate.** Constructing an `Obj` resolves a type through a read, which recurses into
  `read()`. That single constraint explains `resolutionMemory()` vs `memory()`, the eager seeding in constructors, the
  `*View()` accessors, and the field-cached `own*` slots. Any simplification must preserve it.
- **Component slots hold templates, not instances.** `at(COMPILER)` returns an `instLambda`; `compiler()` applies +
  caches + clones. `ownInstset()` must `instanceof`-check the resolved value (an mtron type check rejects the seeded
  `BasicInstSet`).
- **`ComponentUnion.previous()` is a rec entry (`>>previous`), never a Java field, and never `Obj.orElse`.** The parent
  fallback must be lazy — `orElse` evaluates the parent unconditionally on every hit.
- **Never write a roster under `/sys/mach`.** The machine claims that address and `AbstractSpace`'s default writer is a
  no-op, so writes there are silently dropped. The peer roster lives at `/sys/peer`.
- **`push()` must mint a child, not the parent.** Use the 3-arg `clone` — `selfVID` *pins* an already-set vid, so a
  2-arg clone would silently keep the parent's address.
- **`read()`/`write()` on a machine are always space-resolving** (use `readAbsolute`/`writeAbsolute`), so a bulk
  wildcard write reaches the space it names, not the argument stack.

## Migration state (why it reads as two systems at once)

The Router→Machine migration is **not finished**. `Router.java` is deleted, but its scaffolding remains:

- `stackSpace` (`+/#`) still exists and is still seeded into the root memory; a board task is open to remove it.
- `Memory.ARG_STACK` (`argFrames`) is marked **temporary** — the old per-instruction arg stack, moved into `Memory`
  so `Router` could be deleted; it is meant to fold into the frame chain.
- `ExecutionStack` still wraps reads/writes and instruction apply.
- `BasicNetwork.transportOf` still falls back to `/sys/peer` (a TODO notes the roster should become a frame-level
  projection).

Any cleanup that lands these three (drop `stackSpace`, fold `argFrames` into the frame chain, retire the `/sys/peer`
fallback) removes the "two scoping mechanisms at once" ambiguity that is the main source of the current complexity.

---

## Post-refactor note — `Frame` class gutted (2026-10-08)

The `Frame` class (and the `FRAMES` address index) was removed in favor of `ThreadLocal<Machine>` (`CURRENT`) as the
frame. Verdict: a good simplification — the frame *is* the machine (the vid encodes nesting, `push()`/`pop()` move the
cursor), and `CURRENT` is a faithful "walk up" pointer.

But `Frame` was carrying three responsibilities that must be rehomed, not deleted:

1. **Per-frame, lazily-materialized union views.** `Frame` held the `instset`/`memory`/`network` views as *per-frame*
   fields. That matters because `push()` clones with `this.jvm()` — **the `jvm()` rec is shared**, so a union view stored
   in the rec would leak between parent and child. The views must live in per-instance Java state: the
   `resolvedMemory`/`resolvedNetwork`/`resolvedInstSet` fields on `AbstractMachine` are that replacement — **not** "old
   artifacts". (Removing `resolvedMemory` re-introduced a `StackOverflowError`: `memory()` → `atDirect(uri(MEMORY))` →
   `uri(MEMORY)` → `MObj.<init>` → `fURI.big()` → `memory()`. The field memoizes the slot and breaks that cycle.)

2. **The upward link.** `Frame` had an explicit `parent`; the simplification leaves `Obj.parent()`, which returns
   `noobj` in the current code. Pick one reliable upward path: wire `parent()` on `push()`, or derive the parent from
   `vid.retract(1)` (machines have globally-unique vids, so the parent is addressable by retraction).

3. **The downward/address index (`FRAMES`).** `FRAMES` was `Map<fURI, Frame>` — cross-frame lookup by vid. It's
   recoverable through the memory's own space index (a machine is a `Space` and auto-registers under its vid via
   `AbstractSpace`'s `Machine.current().memory().addSpace(this)`), so `Machine.current().read(parentVID)` resolves the
   parent without a dedicated map — *provided* every machine registers under its vid.

The invariant to keep: **the read path must never allocate, and component slots are templates.** `memory()` (and
eventually `network()`/`instset()`) must stay a field read on the read path (`resolution*`-style), never
`atDirect(uri(MEMORY))`, which constructs a `Uri` and re-enters `read()` through `big()`.

Net: keep the gutting of `Frame`, but rehome the three responsibilities explicitly — union views → per-instance
`resolved*` fields (done for memory), parent link → `parent()` or `vid.retract(1)`, cross-frame lookup → memory's space
index. That makes the simplification *cleaner* than the original, not just smaller.
