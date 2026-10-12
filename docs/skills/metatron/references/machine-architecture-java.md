---
name: machine-architecture-java
description: |
  Java-side Machine architecture — Machine, its five components (Memory, Network, InstSet, Compiler, Processor), the
  frame/ComponentUnion nesting model (the frame *is* the machine), push/pop/move, the compile→run pipeline,
  threads/monads, and the load-bearing invariants of the read path: data memory, instruction memory, and the hop
  between them.
---

# Machine Architecture (Java)

## What a Machine is

A **Machine** is a *space of spaces* — the container in which address resolution happens. It is itself an address
space: the interface is `Machine extends Rec, AutoCloseable` and the concrete body is `AbstractMachine extends
AbstractSpace`. Its five members (`instset`, `memory`, `network`, `compiler`, `processor`) are rec entries shaped as
`machine::T`. Of the five:

- **Memory** is the facade over the world of spaces — the single read/write surface.
- **Compiler** lowers code; **Processor** runs it; neither is an address space.
- **InstSet** and **Network** are *accumulating* components (a frame wraps them); **Compiler** and **Processor** are
  *constructive* (whole-value, inherited or replaced).

A machine replaced the old `Router` as the root of the address space. `Router.java` is gone, and
`AbstractMachine extends AbstractSpace` is the one concrete body — which is what makes a machine a `Space` that
answers addresses, while `Machine` the interface stays a `Rec`.

## Package overview

```
isa/mach/type/Machine.java            ← Machine interface + Component + Machine0 (the contract)
isa/mach/type/Memory.java             ← Memory interface (relative bindings + absolute space index)
isa/mach/type/Network.java            ← Network interface (peer roster + authority arithmetic)
isa/mach/type/Compiler.java           ← Compiler interface (rewrite → resolve → type) + resolve memo
isa/mach/type/Processor.java          ← Processor interface + Processor.Helper (the runtime core)
isa/mach/type/ComponentUnion.java     ← frame wrapper: previous()/current(), read-through, close-current
isa/mach/type/memory/MemoryUnion.java ← the ONE implementation today: frame memory (pattern-aware at())
                                        (there is no NetworkUnion or InstSetUnion — see "The five components")
isa/mach/type/machine/AbstractMachine.java  ← the concrete body: routes, authority guard, read/write
isa/mach/type/machine/BasicMachine.java     ← concrete machine; cached compiler/processor; slot templates
isa/mach/type/machine/BasicMemory.java      ← one level of memory (no Java fields)
isa/mach/type/machine/BasicNetwork.java     ← one level of network (roster is the rec)
isa/mach/type/machine/BasicInstSet.java     ← one machine's ISA LEVEL: own structure over its imports, over the
                                        enclosing machine's (via `previous`)
isa/mach/type/compiler/…                     ← Compiler impls + parser/rewriter/resolver/typer stages
isa/m/type/parser/Parser.java                ← the parse-stage contract (the language seam) + Parser.Helper.toCode
isa/mach/type/processor/…                    ← Processor impls + Monad/MonadProcessor/SwarmProcessor + monads
isa/mach/type/thread/…                       ← mThread / AbstractThread / VirtualThread / CoreThread / FutureObj
isa/mach/machInstSet.java                    ← type registration (machine::T etc.) + bootstrap + sum_gather rewrite
```

## The five components

| Component | Interface (extends)                   | One-line job                                         | Frame behaviour                   |
|-----------|---------------------------------------|------------------------------------------------------|-----------------------------------|
| Memory    | `Space, Machine.Component, Closeable` | relative bindings (names) + absolute index of spaces | accumulates (`MemoryUnion`)       |
| Network   | `Machine.Component, Closeable`        | peer roster `authority → transport`, `own`/`isPeer`  | **shared** today: the roster lives at `/sys/peer`, so a child's declaration is the parent's. The union is intended, and needs the roster moved onto the component plus a deny arm |
| InstSet   | `Space` (via `AbstractInstSet`)       | the vocabulary: types/insts/rewrites/sugars; `import` lands here | accumulates — `BasicInstSet` is own structure over its imports over the enclosing machine's ISA; membership in the rec under `reference` |
| Compiler  | `Machine.Component, Rec`              | `code::T → code::T` in three stages                  | constructive (inherited/replaced) |
| Processor | `mThread, Machine.Component`          | `code::T → obj`; the monadic execution engine        | constructive                      |

`Machine.Component` is a `Rec` that answers `machine()` by walking its `parent()` chain up to the nearest `Machine`
(falling back to `mach0()`). Components are `Rec`s *on purpose*: memory and network must be reachable from mtron
(`>>space`, `>>previous`), not just from Java accessors.

## Reaching a component

One accessor per component, and it returns the component — this machine's own level:

- `memory()`, `instset()`, `network()` — the accumulating components. The union with the enclosing machine lives
  *inside* the component (`previous`), not in a wrapper this call builds.
- `compiler()`, `processor()` — the machine's own; `processor()` clones in its non-call arm, so a caller gets a fresh
  runtime.
- Each setter binds the slot, and for memory and instset that is **field only**, never `at()`: a rec write on a machine
  rec fails its own type check (`objCheckAndSave` → `Lst.test`). `push()` writes the child's memory and instset into
  the child's own rec directly (`jvm().put`), so an ADDRESS read of a slot answers with the same object the accessor
  returns.

`AbstractMachine` memoizes the three accumulating components in `resolvedMemory` / `resolvedNetwork` /
`resolvedInstSet`, so the read path sees a field read — and that field is also what breaks the
`atDirect(uri(MEMORY))` → `MObj.<init>` → `fURI.big()` → `memory()` cycle.

**Why it is shaped this way: the read path must never allocate.** Materializing a component constructs an `Obj`, whose
type check reads through the machine, which lands back in `read()`. That one constraint is what the memo fields and
the instance-seeded slots are for.

## Frames and the component union (nesting)

**The frame is the machine.** `push()` clones this machine into a **child** whose vid strictly extends the parent's
(`descendsFrom`), makes it current (`CURRENT`, a thread-local) and gives it its own rec; `push(extension)` mints the
child's vid from the extension. `pop()` restores the parent and releases what the frame opened; `move(extension)`
unifies the two through the coefficient's sign (positive = descend, negative = ascend one step). A push carrying a
**host** is a process boundary instead, not a frame: `Extension.hasHost()` spawns a peer with its own vid, memory and
lifetime.

Nesting is **read-through via `previous`**, built at push time, one level per component:

| component | the child's level                                                                            |
|-----------|-----------------------------------------------------------------------------------------------|
| memory    | `MemoryUnion(this.memory())` — the child's own spaces over the enclosing memory's               |
| instset   | `BasicInstSet(this.instset())` — the child's own declarations over the enclosing vocabulary     |
| network   | **shared** today: the roster lives at `/sys/peer`, so a child's declaration is the parent's — the union is intended, and needs the roster moved onto the component plus a deny arm |

Reads go **through** (own first, then `previous`), writes reach only the own level, and a popped frame takes its own
spaces and imports with it: it can never close what it inherited. Release is structural, not bookkeeping.

**`BasicInstSet` is a union in two directions, not a binary chain.** Own structure over an *ordered list of imports*,
and that whole stack over the enclosing machine's ISA. Two things about how the imports are held:

* the rec records the **vids** (`reference`, in import order — so the membership is legible from mtron, and printing it
  never inlines a library), while the frame holds the sets **by object**; resolution reads the objects, so belonging
  to a machine does not depend on the address index;
* precedence is **per identity**, not per name: an inst or rewrite by its `tid`, a type or const by its `vid`. A nearer
  level wins that identity; a *different* identity under the same name is added alongside — which is why `plus` is a
  family rather than a slot. Import order breaks ties.

Writes reach only the own structure, which is what makes a shared library safe to import from many machines. And a
read of the vocabulary is a **schema query** — dom/rng admission down the refinement lattice, ranked
closest-to-the-token — reached through `Machine.read`: a caller reaching for `instset()` directly is an optimization of
that read, never the door.

## How one evaluation flows

```
machine.apply(code, start)
  → Machine.current(this)                 // move this thread's perspective
  → push()                                 // a fresh frame
  → processor().code(compiler().apply(code).asCode()).apply(start)
  → pop()                                  // release the frame
```

`BasicMachine.of(...)` binds each of the five slots **as the component itself** — an instance needs nothing resolved,
which is what stopped the slots being deferred seeds; the accessors still tolerate a seed (`isInst()`/`isCall()` →
`apply()`).

Two different reads, and the distinction matters:

```
Machine.read(vid)         → network dispatch → data memory → instruction memory (only on a miss)
BasicMemory.read(vid)     → ~ resolution → alignPrefix → stack check → readAbsolute → small→big fallback
```

`Machine.read` is the language's one way in, and it asks **instruction memory** as its last source — guarded against
re-entry by `Machine.Helper.READING_INSTRUCTION_MEMORY`, because answering a schema query resolves the pattern's
dom/rng types and resolving a type is itself a read. It asks with the **declared** form (`readDeclared`): the tables,
and nothing more, because a fallback must not cause work. A member's `read` may do more than answer — a schema instset
populates a table's schema on demand, which is right for a *deliberate* read of that schema — and asking that from an
unrelated lookup walked into a catalog during a `tbleSpace`'s own construction and hit a database connection that was
not open yet. `AbstractInstSet` therefore keeps one private table read that both forms reach, so overriding either
cannot make the other re-enter it. `BasicMemory.read` is the address-space read, and it is where a
name becomes an address: `~` first, then the prefix alignment, the stack, and the space index — and a miss escalates
small→big.

`BasicMemory.readAbsolute` asks `findSpace(vid)`: the most specific space across the chain. When no space covers the
address the machine answers with **its own rec keys** (`at`, never `read` — a read would come straight back here and
re-enter), which is what makes `/` the root with no space mounted inside itself.

## The compiler (four stages)

`Compiler.apply` is the default schedule `parse → rewrite → resolve → type` over four overridable stage components:

- **parse** — `Parser` (default `mtronParser`, `mtron_parser::T`; the language seam — see below).
- **rewrite** — `Rewriter` (default `FixPointRewriter`, fixpoint over the rewrite rules).
- **resolve** — `Resolver` (default `ScoringResolver`; threads the type one inst at a time; generic binding runs inside
  per-candidate selection, so there is no separate binder).
- **type** — `Typer` (default `TypeTyper`, runtime type assertions).

Stages are rec entries, not Java fields: `BasicCompiler.defaultStages()` supplies all four and `BasicCompiler.stages`
merges a caller's over them, so `compiler::[=>]` mints the default compiler *with* its stages and
`compiler::[rewriter=>fixpoint_rewriter::[max=>3]]` overrides one.

`Parser` is where a **language is hosted**. Its contract is `Code apply(Obj source)`: read source text, emit
`code::T`. Because the three stages after it, the processor and the distributed runtime all take code, a parser that
emits mtron code *is* another language on metatron — no new instruction set, no new evaluator, no new type system;
wire it as `compiler::[parser=>my_parser::T]`. mtron is a deliberate substrate for this (Turing complete, rich type
system, object-oriented and functional), so a new surface syntax lowers into a language worth lowering into rather
than into an IR. A language whose syntax is elaborate but whose semantics are mtron's is a parser and nothing else:
the default rewriter already runs on the code it produced. The stage is total over objs — an obj that is not a `str`
is not re-read, only lifted as it stands — so one compiler takes a program's text or its parsed obj; re-reading the
latter would parse a *rendering* and lose the vid and non-default tid. A grammar's own result is not always a code
(mtron reads `6.plus(1)` as code, `plus(1)` as a lone inst, and `5` as the int itself), so `Parser.Helper.toCode`
lifts all three: a code passes through, an inst becomes a one-inst code, anything else becomes a code that starts
with it. `Compiler.parse` is that delegation; the `code::T` assertion the schedule used to open with is now the
parser's contract.

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
`monad_processor::T`, `swarm_processor::T`, `compiler::T` (with its `BasicCompiler` constructor, so
`compiler::[parser=>…,rewriter=>…,resolver=>…,typer=>…]` mints one), the parser family (`parser::T`,
`mtron_parser::T`), the rewriter/resolver/typer families,
`memory::T`, `network::T`, and `machine::T` (whose shape is `instset`/`compiler`/`memory`/`network`/`processor`). The
default machine lives at `/sys/mach/default` (a `const` built by `BasicMachine.of`). `sum_gather` — the rewrite that
turns a monoidic reducer into a distributed gather over `/usr/compute` peers — lives here too.

## Load-bearing invariants (keep these if you simplify)

- **The read path must never allocate.** Constructing an `Obj` resolves a type through a read, which recurses into
  `read()`. That single constraint explains the instance-seeded slots, the eager seeding in constructors, and the
  `resolvedMemory` / `resolvedNetwork` / `resolvedInstSet` memo fields. Any simplification must preserve it.
- **A component slot holds the component itself** — `BasicMachine.of` binds all five as instances. The accessors still
  apply a deferred seed if one is present (`isInst()`/`isCall()` → `apply()`), and `instset()` is strict: an `InstSet`
  in the slot, else a fresh `BasicInstSet` (an mtron type check rejects a seeded `BasicInstSet`).
- **`Machine.read` is the one way in**: network → data memory → instruction memory, the last only on a miss, and never
  re-entered from inside an ISA read.
- **An ISA is a member by object, not by address.** `refer` holds the ISA itself beside the vid recorded in the rec,
  so membership and resolution need no address index. The index entry (an ISA also being a space) is the separate half
  that is slated to go: an instruction set is vocabulary, not a place.
- **`ComponentUnion.previous()` is a rec entry (`>>previous`), never a Java field, and never `Obj.orElse`.** The parent
  fallback must be lazy — `orElse` evaluates the parent unconditionally on every hit.
- **Never write a roster under `/sys/mach`.** The machine claims that address and `AbstractSpace`'s default writer is a
  no-op, so writes there are silently dropped. The peer roster lives at `/sys/peer`.
- **`push()` must mint a child, not the parent.** Use the 3-arg `clone` — `selfVID` *pins* an already-set vid, so a
  2-arg clone would silently keep the parent's address.
- **A machine is a space, so its own read is a space read** (`AbstractSpace.read` → `Space.Helper.resolveRead`, q-procs
  and all). Address *resolution* belongs to memory: `BasicMemory.read`/`write` route an address to the space that covers
  it (`readAbsolute`/`writeAbsolute`) — which is why a bulk wildcard write reaches the space it names rather than the
  argument stack.

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

1. **Per-frame, lazily-materialized union views — rehomed, and the premise behind it has since changed.** The views
   live in per-instance Java state: `resolvedMemory` / `resolvedNetwork` / `resolvedInstSet` on `AbstractMachine` —
   **not** "old artifacts". The reason is no longer a shared rec map (`push()` now clones with `null`, so the child
   owns its own map — see the 2026-10-12 note below); it is that a slot may hold a deferred seed and the read path
   must not allocate. (Removing `resolvedMemory` re-introduced a `StackOverflowError`: `memory()` → `atDirect(uri(MEMORY))` →
   `uri(MEMORY)` → `MObj.<init>` → `fURI.big()` → `memory()`. The field memoizes the slot and breaks that cycle.)

2. **The upward link — done two ways, deliberately.** `push()` wires it explicitly (`child.parent(this)`) for the
   components' `Component.machine()` walk, and a machine's address still carries its ancestry (`vid.retract(1)`-style
   arithmetic), so an enclosing frame is reachable without a map. `Obj.parent()` on a machine that was never pushed
   remains a `noobj`-returning stub.

3. **The downward/address index (`FRAMES`) — rehomed into memory, and stronger than the map was.** `push()` registers
   the child rec in the **root's** space (`Machine.root().memory().write(childVID, child)`), so `read(childVID)` from
   *any* machine reaches the child — a sibling at any depth, not just the parent — while the child's data stays in its
   own infra, so isolation holds.

The invariant to keep: **the read path must never allocate, and component slots are templates.** `memory()` (and
`network()`, `instset()`) must stay a field read on the read path, never
`atDirect(uri(MEMORY))`, which constructs a `Uri` and re-enters `read()` through `big()`.

Net: keep the gutting of `Frame`, but rehome the three responsibilities explicitly — union views → per-instance
`resolved*` fields, parent link → wired at `push()` with address arithmetic as the other path, cross-frame lookup →
memory's space index. That makes the simplification *cleaner* than the original, not just smaller.

---

## Instruction memory and the read path (2026-10-12)

The components became **machine-scoped instances** rather than per-frame views, and instruction memory took its place
in the read path:

* **`Machine.read` reads instruction memory.** Its sources are `network` (authority dispatch) → `memory` (data memory:
  spaces, instances, and the routing tables) → **`instset`**, the last only on a miss. The hop is guarded by
  `Machine.Helper.READING_INSTRUCTION_MEMORY`, because answering a schema query resolves the pattern's dom/rng types
  and resolving a type is itself a read — without the guard the hop re-entered itself until the stack died (measured:
  seven `StackOverflowError`s in `MachPushTest`). A read made *from inside* an ISA read is a data read.
* **A short name is routed by data memory.** An ISA's tables are keyed by the full address, so the instset read asks
  `Machine.current().memory().redirect(pattern, true)` to turn `plus` into `/m/inst/plus` first. Names are data
  memory's job; addresses are instruction memory's.
* **`push()` gives the child its own rec and its own component levels.** `clone(null, …)` keeps the child's own map —
  while the map was shared, an address read of a slot (`*</xyz/instset`) reported the *enclosing* machine's component
  while the accessor reported the child's: the same slot disagreeing with itself. The child's memory is
  `MemoryUnion(this.memory())` and its ISA is `BasicInstSet(this.instset())`, both written straight into the child's
  rec (`jvm().put`), because a rec write on a machine rec fails its own type check.
* **`BasicInstSet.refer` holds the ISA by object** beside the vid recorded in the rec, so a member resolves without
  the address index — and a machine can hold a member that is **not** one of its spaces (`InstSetUnionReadTest`).
* **The registration — an ISA also being a space — is the last artifact of that era.** Removing it is blocked on three
  measured things: migration writes that find the owning ISA by address, spaces that must decline a vid they cannot
  interpret instead of throwing, and documentation ownership, which resolves the owning space through the address index
  (with the registration gone a setup goes from ~0.1s to ~4.2s; a 13-test class went from 4.0s to 200.5s). The
  mtron-side doc, `mach-instset-mtron.md`, carries the same list from the language's side.
