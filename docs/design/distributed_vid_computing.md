# Distributed VID Computing — a survey

A survey of metatron's **machine** across its four faces — *code*, *distribution*, *addressability*, and the
*`+`/`·` ring* — as worked through in a long design conversation (2026-11-02 → 2026-11-03). This is a **survey**,
not a plan: it states what is understood, points at the existing documents, and marks the loose ends as
**TODO**s so the eventual development plan can pick them up.

---

## 0. The one-line thesis

Distribution is the **four-way vid applied to code**: factor a lawful fold into `branch` (the `+`), write each lane
to a processor uri (`to(uri)` — the vid), and let the machine at that vid **compile** (bind signature → function) and
**evolve** (run monads). The reference ring is the floor; the theorem is the license; the vid is the placement; the
processor is the runtime.

---

## 1. The rings — one law, three domains

`+` and `·` are metatron's universal operators. The same distributive law `a·(b+c) = a·b + a·c` holds in three
domains, and that law *is* the coupling between them.

| ring | `·` | `+` | the domain | doc |
|------|-----|-----|-----------|-----|
| **uri (data)** | extension — `x·y = x/y` | branching — `x+y = {x,y}` | references | `docs/design/ring-uri.md`, `docs/website/adoc/uri.adoc` |
| **stream (process)** | serial compose of instructions | parallel branch of code | instructions | `docs/website/articles/stream-ring-theory` |
| **machine (placement)** | `move` (navigate, identity `stay()`) | fan-out / union (distribute) | places | `docs/design/machine-ring.md` |

### 1.1 The uri ring — `·` is colocation, `+` is distribution

The deepest fact: **`·` (extension/nesting) is a *dependent, colocational* relationship; `+` (branching/stream) is an
*independent, distributable* one.** `[a=>[b=>1,c=>2]]` (form A, `a·(b+c)`) requires `a` to couple `b` and `c` into one
structure; `{[a=>[b=>1]],[a=>[c=>2]]}` (form B, `(a·b)+(a·c)`) does not — `a/b` can live in a document store in Japan
and `a/c` in a `.txt` file in Ohio, and the same `{1,2}` comes back. The distributivity `a·(b+c) = a·b + a·c` is the
*licence to move a colocation cut*: it turns dependent `·` into independent `+`.

**Referential factoring** is the payoff: references form a ring, so references can be *factored*. A matrix embedded
entry-wise (fully branched), row-wise (`·` factored per row), or row-column-wise (fully nested) is *the same ring term
factored differently* — the identical read `*m/+/0` resolves identically, only the time/space table changes. This is
exactly what `?split` is (see §6.1): a choice of factorization, referentially equivalent, physically different.

The **type level** is the same ring lifted: `[]` (list) is `·` — rank, `lst[{2}str]` = two `str` slots at `…/0`,
`…/1`; `{}` (stream) is `+` — extent, `str{3}` = three `str`s. Invariant: **no `{{ }}`** — addition flattens, so a
stream binds to a list slot, never another stream. `lst[{2}str{3}]::T{2}` is the term
`((s+s+s)·(s+s+s)) + ((s+s+s)·(s+s+s))`.

### 1.2 The obj ring — a musing that turned out to be load-bearing

The *obj ring* (`a+b = {a,b}`, `a·b = a=>b = b(a)`, "every obj is a `Function<Obj,Obj>`") is **too general to be of
practical use as an abstraction**. But its one fact — *every obj is a function* — is what makes the whole placement
scheme work with **no special placement type**: the processor is a function, the peer's `eval` is a function, the
compiler is a function. "Place this lane" is literally "apply the lane's code to the right function."

> **TODO** — decide whether the obj ring's axioms (ring vs semiring/near-ring at value level vs type level) ever need
> to be stated formally, or whether it stays a convenient observation.

### 1.3 The conjecture, grounded

*Every form of distributed evaluation is possible iff (and only at the point where) there is a `+` (branch).* The
uri ring grounds this: the machine boundary is a **colocation cut**, and a colocation cut is sound only where the data
has *no colocational requirement* — i.e. where the term is additive (`+`), not multiplicative (`·`). The `+` is the
only operator under which the referents are independent. `move()` survives as the arity-1 case (§5.4): a branch of one
lane is still a `+`.

---

## 2. The vid — addressability as placement

`PlatonicObj` is the atom of "a thing": a `jvm()` (the Java grounding), a `tid` (an fURI saying *what* it is), and a
`vid` (an fURI saying *where* it lives). The vid has **four precise meanings**, and they *are* the placement:

| vid | lives | placement reading |
|-----|-------|-------------------|
| **absolute** (`/foo`) | in space (local) | local space |
| **absolute + remote authority** (`ws://host/foo`) | remote | a dialed peer |
| **relative** (no `/` prefix) | the machine's variable stack (shared across threads) | a machine-local slot |
| **null** | ephemeral, thread-local, detached | the stream itself |

The uri graph is **not a tree** — it is a cyclic, arbitrary-degree graph, and the `!*` (auto-from) edge is what makes
it cyclic: `x -> [a=>[b=>2, c=>!*x]]` lets `*x/a/c/a/c` traverse the cycle back to `x`. The three bridge modes —
`*` (deref), `@` (anchor, value *with* address), `!*` (deferred reference) — are the graph's edges
(`docs/design/ring-uri.md`).

---

## 3. The machine — a frame of reference

A `machine::T` is a **frame**: its own `memory`, `network`, `instset`, `compiler`, `processor` (plus `pattern`, `q`).
Machines exist in a **stack** (`push`/`pop`), each with its own infrastructure. The `Machine.current()` `ThreadLocal`
says which machine a thread is *at*; `move(/abc)` re-homes it — **no respawn, just a change of frame of reference**:

```
*~/+/.<<
==> pattern  q  memory  network  instset  compiler  processor
```

- **`push` / `pop`** are the resource (linear) pair *outside* the ring: `push` mints, `pop` reaps. `move` only
  navigates.
- **`push(<host>)` is refused** — a host names a *peer*, reachable by shipping code in a plan, never by descending.
  A machine is a frame; a peer is a network concept.

### 3.1 Compilation — bind signature → function

What makes code executable: an instruction *by signature* is resolved to an instruction *with* a signature **and** an
`Inst.f` (a function). `plus?int<=int(1)` is a uri (sugar for `<=` and `()`); its `tid()` is
`/m/inst/plus?rng=/m/int&dom=/m/int&args=[/m/int]` — a *denotation* with no function. The compiler binds it.

The compiler is a pipeline of **four addressable stages** — `parser`, `rewriter`, `resolver`, `typer` — composed by
`Compiler.apply` as `typer(resolver(rewriter(parser(source))))`. Each stage is a first-class obj you can dereference
and apply individually:

```
["start(1).plus(0).mult(354)"]                              [-- a string --]
  == [!*/compiler/parser]     →  [start(1).plus(0).mult(354)]  [-- parsed --]
  == [!*/compiler/resolver]   →  [start?int<={0}(1){<j>} …]    [-- bound: {<j>} = Inst.f --]
  == [!*/compiler/rewriter]   →  [start(1){<j>}.mult(354){<j>}] [-- plus(0) removed --]
  >-                          →  354
```

`{<j>}` is **compilation made visible**: before the resolver the insts are bare signatures; after, they carry their
function. `plus(0)` vanishing is `theory_unit_removal` — a declared law firing as a rewrite, not a hard-coded
optimization. Rewrites run before resolution in the default schedule; law-driven rewrites read `LawTable.declared` to
re-type the bare addresses.

### 3.2 The runtime — monad, computer, thread, barrier

- A **monad** = (instruction pointer, data pointer) — the program counter and data pointer, unified. The atom of
  computation.
- A **computer** = a **pool of monads** — its `cache` = `{run, barrier, halt}`. (A **processor** is the *architecture*
  — stateless, registered once; a **computer** is the *instance* it mints. See
  `processor-computer-analysis.md`.)
- A **thread** = a *worker*: grab a monad, apply the data to the instruction, route the result —
  - PC → `noobj` → **halted** (into `halt`),
  - data → `noobj` → **killed** (GC'd),
  - both alive → back into `run`.
- The computation's state lives in the *pool*, not the thread — threads are **interchangeable workers** (add more to
  speed up, remove to balance).
- The **barrier** is `+`'s join at runtime: a monad can't evolve past a `barrier()` instruction (dom `#{*}`) until the
  `run` pool is empty; then the barrier's monad pool is applied to the barrier instruction (the *combiner*), and the
  `run` pool refills.

`Machine.current().processor().apply(Machine.current().compiler().apply(code))` is the *definition* of "a thread of
execution" — compilation followed by execution, identical on every machine.

### 3.3 The `Inst` Triplet — where the plan Rec lives

```java
public MInst(final Triplet<Poly, Inst.f, Obj> jvm, final fURI tid, final fURI vid);
```

`(args, f, seed)`. The **seed** slot was meant for reducers (a `0` for `sum`) but became unnecessary once the algebra
became introspectable (`zero()` is read off the type). It is **unused**, and the proposal is to repurpose it for the
`branch` instruction's **plan Rec** (§6.3) — so the plan is an *obj* (projectable, manipulable, placeable-back), not a
Java field shielded from metatron's reflection.

> **TODO** — confirm the seed slot is unused everywhere before repurposing; keep the plan in the seed slot, not a Java
> field, so it participates in the obj graph.

---

## 4. Distribution today — the two mechanisms

### 4.1 `law_shard` — the declarative fold shard

`PlanBuilder` assembles a `plan::T` = `[name, cut, where]`; `PlanInterpreter.shard` lowers it. The three axes:

| axis | meaning | today |
|------|---------|-------|
| `?place` | where (the place set) | `local` / `peer` / `#` / `//host` / `scheme://host:port` |
| `?hop=height{arity}` | how deep / how wide the tree | arity caps the fan-out; `[n,1]` = chain |
| `?split` | what counts as one share | `stream` (stride) and `copy` lower; `block`/`key`/`owner`/`index` decline |

The emit: `start([slave…]).select([virtual::[code=>eval(p_i)]…]).select([>>rhs…]).merge().<combiner>`. The combiner
is **computed** (`LawTable.combiner`: `count↦sum`, `order(s)↦kmerge(s)`, `group([k⇒h])↦pointwise`), never named. The
emitted `start([…]).select([…])` **is a `branch` spelled as transport** — the decision (serial/parallel/distributed)
is *collapsed* into `virtual::[eval(p_i)]` at plan time.

### 4.2 `move` / `extractMove` / `cut` — the imperative scatter

`extractMove` scans code to the first **host-bearing** `move`, splits head/tail, ships the tail via `cut(authority,
tail)`. The receiver **re-compiles the shipped tail**, finds the next move, and tails-and-ships again — the chain of
machines *is* the recursion. A host-less `move` (e.g. `move(<a>)`) re-homes locally and does **not** cut; only a
host-crossing move isolates frame state (`Network.Helper.boundary`).

`cut()` is a **barrier**: dom `A{*}` (Kleene), "gather the monads up to this point and ship them"; `cut?<=A{5}` is a
lazy/paging barrier. It implements "ship code to a peer and await a response."

### 4.3 The peer — a mini-instset

A `peer::T` = `{origin, host, client}`, minted from a client. The client declares a **mini-instset** — per-tid
overrides — `eval`/`from`/`to`:

| client | overrides | can it run a shipped program? |
|--------|-----------|-------------------------------|
| `ws_client::T` | `eval`, `from`, `to` | ✅ the one real metatron→metatron transport |
| `http_client::T` | `from` (GET), `to` (POST), no `eval` | ❌ read/write only |
| `mcp_client::T` | one override per discovered tool | ⚠️ only if it exposes an `eval` tool |

The liveness lattice gates distribution: `reachable → admissible (declared: scheme has a dialer) → serves (realized:
overrides the required insts AND online) → shardTargets`. Only `shardTargets` receive a shard; an outage *shrinks* the
fan-out, never poisons the reduction. The category graph (`docs/skills/mtron/references/cat-instset-mtron.md`) is the
rewrite table: types are objects, insts are morphisms, `path::T` is code-as-vertex, `plan::T` is a rule-as-value.

### 4.4 The pattern layer — `?decq` and the cat-graph

The plan's `cut`/`span`/`LawTable` are all **predicates over algebraic metadata** that metatron already serves through
the **`?decq`** q-proc (the algebra twin of `?docq`):

| `?decq` on | returns | the cat-graph read |
|---|---|---|
| a **type** (`*int?decq`) | the structural theories — `ring`/`group`/`monoid` with roles (`add`/`mul`/`zero`/`one`/`id`/`inv`) | `object::T>>law` |
| an **inst** (`*plus?decq`) | the process laws + `form` + bound obj, one per registration | `morphism::T>>law` + `>>form` + `>>obj` |

So the plan's Java types are a re-encoding of this one door:

| Java today | the `?decq` query it re-implements |
|---|---|
| `Cut.reducer(Law.monoidic, Law.commutative)` | `form == reducer` and `law ⊇ {monoidic, commutative}` |
| `Cut.role("id")` (the unit law) | the theory role `id` off `*<type>?decq` |
| `Span.floatable()` / `.blocked` | `form ∈ {mapper, filter, flatmapper}` vs `{reducer, gather}` |
| `LawTable.combiner(keyed, laws)` | `?decq>>law` (monoidic+commutative → self; `derivation` → the defining fold) |

The cat-graph takes `?decq` from *local* to *global*: types are objects, insts are morphisms, and the structure answers
*which rewrite is live and unique* for a given code — **iso-clusters** (`analysis.orbit` / `coupling` / `retract`, the
reversible core), **unique transformation paths** (`path::T` as a vertex whose out-edges are the live rewrites; a normal
form prints as its own certificate; a traversal is a derivation witness), and **lifting code to a rewrite graph**
(`path::T`/`abstraction::T`, the third lift). So "find the fold and rewrite it" is a *navigation of the rewrite graph*,
not a scan: `code.as(path::T)` → the live out-edges (guarded by `?decq` through a-priori pruning) → `lower(>>plan)`.

---

## 5. The unification — one instruction, one primitive

### 5.1 `eval` ≡ `move` ≡ `cut`

```
move(//remote).tail()  ≡  tail shipped to //remote and run there
                       ≡  eval(tail @ //remote)
                       ≡  cut(//remote, tail)
```

All three are **one act**: take a chunk of signature-code, ship it to a place, await the response. `eval` is the
peer's mini-instset *override* of that act; `move` is its arity-1 form (one lane, the tail); `cut` is the barrier.

### 5.2 The compile/cut rule

**The branch never compiles anything.** It ships every lane as *signature* (unbound), and the machine at the lane's
vid binds it. "Compile" is one act — bind signature → Inst.f — always at the *target*. Local vs remote is only *which
machine's compiler runs*, never *whether* compilation happens. "Ship raw" is "ship signatures (portable uris), never
functions (JVM-bound lambdas)." The `lst::T` is the **containment glovebox** — code in a `lst` doesn't fire, it's just
uris.

### 5.3 Placement = "which `Function<Obj,Obj>` to apply the lane to"

| placement | the function | which machine | how code arrives |
|-----------|--------------|---------------|------------------|
| `virtual` | `~/processor` (the local swarm) | this machine | by reference |
| `move(/abc)` | re-home `Machine.current()`, then `/abc`'s `compiler().apply()` | local machine `/abc` | by reference |
| `peer>>eval` | the peer's `eval` (ultimately `send_recv()`) | remote machine | by wire |

All three are the *same shape*: `code → thread-of-execution`. The placement is a `Function<Obj,Obj>`; no special
placement type, because every obj is a function.

### 5.4 `move` = branch arity-1

`move(//remote).tail()` is a `branch` of **one lane** whose program is the tail. Parallel execution and serial
migration are the *same thing* — the terminology is a function of branch arity. The chain shape `?hop=[n,1]` is
exactly this (one child per node, `monoidic` alone). So `move` and `law_shard` are one rule at two arities.

### 5.5 `thread::T` — virtual / core / current / machine

`virtual::[=>]`, `core::[=>]`, and `/abc` should all be **`thread::T` pointed at different processor locations**. The
`virtual`/`core` distinction is thread *kind* (scheduling); the location is *which processor's monad pool*. `move()`
becomes "move *where*" — to a barrier (join), to the run pool (parallel), or to a new processor (distribute).

> **TODO** — is `move()`'s signature generalized to a processor-location argument (barrier / run / processor), or does
> it stay machine-address-shaped with the barrier/run targets a separate concern? This is one API decision.

---

## 6. The target design — the refactor

### 6.1 Two phases, meeting only at `branch`

| phase | ring | reads | outputs |
|-------|------|-------|---------|
| **factor** | stream | the **laws** (`LawTable`, combiner) + the **data ring** (`?split`) | `branch(lane₁,…,laneₙ).<combiner>` |
| **plan** | machine | the **network** (`reach` / `serves` / `shardTargets`) | `planRec[i] = place` |

They share **no overlapping behavior**: one decides *where the code factors* (algebraic cuts), the other decides
*where the factored code goes* (physical cuts). The `branch` node is the interface — phase 1's output *is* phase 2's
input. The organic tree is the two phases *alternating machine by machine*: phase 2 ships a lane raw, the landing
machine's compiler runs phase 1 (factor again) then phase 2 (place again).

**The hard line is already drawn in the code.** In `PlanInterpreter.shard`, everything up to and including the
combiner + seed factoring is stream ring (laws, coefficient shape, `LawTable.combiner`, `?split`); everything from
`resolve()`/`shares()` onward is machine ring (place resolution, lane order, `virtual`/`eval`/`to(uri)`). And
`PlaceResolver` is already a **separate class** — the machine-ring side is already extracted (the design's C4). So the
refactor is to *finish* that extraction: `factor` (emit `branch`) and `place` (emit `to(uri)`), meeting only at the
`branch` node.

### 6.2 `branch()` is the single primitive

`branch` is currently an ordinary instruction (`BRANCH_INST_TID = /m/branch`, glyph `-<[]>-`), not part of the
distribution machinery. The proposal: make it the **material seam** — the only form the distributed rewrites see.
Rewrite rules use the algebraic laws to *generate* branch-rich expressions; the placement rules then only concern
themselves with *where the branches go* — no law re-checking.

> **TODO** — determine whether `branch` is currently lowered to `start/select/merge` or is a distinct processor
> instruction; that decides whether phase 1 can emit `branch` directly and let a *lowering* realize the three modes.

### 6.3 The branch — `Triplet(args, f, route)`; the kernel is the route

`branch` is a `Triplet<Poly, Inst.f, Obj>` = `(args, f, seed)`:

```
args = [ plus(1), plus(2).mult(5), mult(2) ]          // the lanes (programs)
f    = the body (mtron or Java)                       // eval_i(lane_i)
seed = [ route => [                                   // the kernel = the ROUTE
  0 => *<~/network/peer/ws/remote/8555/mtron/instset/m/inst/eval>,
  1 => *<~/processor/345acd/instset/m/inst/eval>,
  2 => *</abc/instset/m/inst/eval> ]]
```

The kernel is the **route** — index → the *component's* `eval` instruction, dereferenced. The body is one line:

```java
objs(route.elements().map(rel -> read(rel.second()).apply(args[rel.first()])))
```

i.e. **for each lane, apply the route's `eval` to the lane's code.** `eval` is the *single* ship-code verb, and every
component overrides it with its mechanical interpretation — `ws_client` (send_recv, remote), `computer` (spawn a
thread), `machine` (move, re-home the frame). So **"peer as mini-instset" generalizes to "every component is a
mini-instset"**: each component's `instset/m/inst/eval` (and `/to`, `/from`) is its own transport, and the branch never
learns a protocol — it hands each lane to the component's own `eval`.

The body is expressible two ways — a **Java lambda**, or **mtron** (the "plan, non-lambda"):

```
inst(args=>rec::T route=>rec::T){ *args.zip(*route).map(-<[>>0, *(*(route.>>(>>1)))]) }
```

— it *queries the args (branches), then queries the route (evals), then applies each branch to its eval*. So the
branch's `Inst.f` **is** the plan, written in mtron, introspectable and rewriteable.

Two sugar / introspection conventions:

- **`branch(...)`** = bare (no route); **`branch::(...)`** = with route. Same tid — `::` is toString sugar for "the seed
  slot holds a route."
- **`>>route`** reveals it: `[code]==[as(lst::T)]==[>>2>>route]` reads the branch's route — the `to`/`from`/`eval`
  wiring, hidden by `::` but one `>>` away, as a *structured rec*, never spaghetti.

### 6.4 The peer = a server-spawned handler thread

Remote evaluation is "spawn a processor at the remote machine's address, bind the code to a thread there." The nuance
is *who spawns the thread*: it is **not** the client pointing a handler — it is the **remote server spawning one on the
incoming request**. The local `ws_client` `send_recv`s the code; the remote `ws_server` receives it and **spawns a
handler thread**, whose `Machine.current()` is *already* the remote machine; that handler thread computes the code it
received (`Machine.current().processor().apply(Machine.current().compiler().apply(code))`) and sends the answer back.
In every case the computing thread is **born where the code lands, with `Machine.current()` already set** — a virtual
thread born on the current machine, a handler thread born on the remote machine, a re-homed thread after `move`. So
distance (wire vs reference) really is the *only* difference: the handler is just another worker thread, home from
birth, no different in kind from a virtual thread.

### 6.5 The plan is metatron code — no mini-DSL

The plan is already a metatron rec (`plan::T` = `[name, cut, where]`), its axes are already coefficient values
(`{2}4`, `{2}block`, `[local, peer]`), and `Placement`'s enum→axes migration proves the "mini-DSL" is already collapsing
into metatron values. The whole plan is **three metatron-native mechanisms composed**:

| part of a plan | metatron-native | replaces the Java |
|---|---|---|
| **pattern** (find the fold) | `?decq` (form / law / theory) | `Cut`, `Span`, `InstPattern` in `shard` |
| **transform** (factor + emit) | `as(rec::T)` + `branch` (`-<[]>-`) + `>>=` (index / duplicate / splice) | the `shard` emit (factor + `start/select/merge`) |
| **route** (place) | `to(uri)` (the vid) | `Placement`, `PlaceSet`, `PlaceResolver` |

The transform is the demo in one line:

```
code.as(rec::T).branch(n copies).map(copy_i => copy_i >>= [0 => |start(part_i)]).sum()
```

`as(rec::T)` indexes the chain (the `cut`/`span` *positions*); `branch` duplicates (the `+`); `>>=` splices the seed
factor (the `?split`); `.sum()` is the combiner. **No new `cut`/`span` instruction is needed** — they are *derived*
operations on the code rec. The only residue that stays Java is the orchestration — find the fold by law (`?decq`),
hand the index to the splice — and that is a **plain `code → code` rewrite rule**, registered like `move_extract`.
(*Not* `lower(plan)` on a `path::T`: the rewrite graph — `path::T`, `lower`, `catRewrite`, the a-priori prune — is
itself built on `PlanBuilder`/`PlanInterpreter`, so leaning on it to replace them would be circular. `path::T` is
optional introspection, retireable or re-baseable, never a prerequisite for the planner.)

**And there is no plan interpreter at all.** The plan is the branch's mtron `f` (§6.3), so the rewrite rules just *aim
at* `branch()`: find the fold (`?decq` guard), factor the seed (`?split`), emit `branch(lanes, route)`. No rec to
interpret, no Java to lower it — the rewrite *is* the plan. What remains as optional "plan data" is a **deducible
block-diff**: wrap the rewrite in `inspect(rewrite())`, and when code goes in as lhs and out as rhs, `inspect` deduces
the `[block@vid_a => block@vid_b, …]` substitution table — "this block became this block," keyed by the instructions'
vids (their location in the code). That table is the introspection answer to "what did this rewrite do?" (for
`explain()`), and it says *what changed*, not *why* — the *why* is the `?decq` guard. Without `inspect()`, you just
execute `rewrite()`, the network populates `[route => …]`, the processor evaluates, and the branch fires when a monad
reaches it.

---

## 7. Addressability — resolved by the Processor/Computer split

The compiler already has what the processor (architecture) used to lack: **addressability**. `!*/compiler/parser`,
`resolver`, `rewriter` are individually dereferenceable and applicable. The **Processor/Computer design**
(`processor-computer-analysis.md`) resolves the wall: the addressable unit is the **Computer**, and its `cache()` — a
Rec keyed `{run, barrier, halt}` — makes the pools first-class addresses. `to(~/processor/_?incrq)` mints a computer;
`to(<computer>/cache/run)` enqueues a monad; `*<computer>/cache/halt` reads the answer. The watchdog/worker split (the
computer is *a thread that supervises threads*) is what turns "add a thread to a pool" into a write, not a mechanism.

> **TODO** — thread migration. `/sys/thread` is the *old* architecture; threads migrate to
> `/processor/<computer>/thread/<id>` (partitioned by their computer).

---

## 8. Consolidated TODOs

1. ~~**Processor addressing**~~ **resolved** — the Processor/Computer split makes `cache/{run,barrier,halt}` addressable
   (the Computer's `cache()` Rec); landed in `processor-computer-analysis.md` (§7).
2. **`?split` kinds** — `block`/`key`/`owner`/`index` decline today; each needs its seed's data-ring structure (an
   ordered `[]`, a key, an addressed seed, a generated range) before it can factor (§4.1).
3. ~~**Cut-less plans**~~ **moot** — `bind`/`relocate` die with `PlanInterpreter`; the rewrites just emit `branch`.
4. ~~**`branch` lowering**~~ **resolved** — `branch` is a `Triplet(args, mtron-f, route)` (§6.3), not `start/select/merge`.
5. **`?hop` budget** — currently stamped on the fold's `q(HOP)`; the shipped lane must preserve the decremented budget (§4.1).
6. **`move()` signature** — does it generalize to processor-location targets, or stay machine-address-shaped (§5.5)?
7. ~~**`@` anchor on code**~~ **moot** — the route holds `eval` instructions, not `@`-anchored places.
8. ~~**Seed-slot reuse**~~ **resolved** — the seed slot holds the route (§6.3).
9. **`cut()` dom** — currently `ALL.maybeSome()`; target is the Kleene `A{*}` barrier, with `?<=A{n}` paging (§4.2).
10. **`move` as arity-1 branch** — fold `extractMove` into the branch placement, `cut()` as its lowering (§5.4).
11. **`thread::T` unification** — virtual / core / current / machine as `thread::T` at processor locations (§5.5).
12. **Machine-ring `0` and `+`** — the explicit empty machine and the fan-out operator are sketched, not encoded
    (`docs/design/machine-ring.md`).
13. ~~**`planRec` ↔ `plan::T.where`**~~ **moot** — no `plan::T` remains.
14. ~~**`plan()` immutability**~~ **moot** — no `plan()` method remains.
15. **obj-ring axioms** — state them or leave them a musing (§1.2).
16. ~~**Delete the mini-DSL**~~ **resolved by design** — no `PlanInterpreter`/`PlanBuilder`/`Cut`/`Span`/`Placement`; the
    plan is the branch's mtron `f`, and the "plan data" is a deducible block-diff via `inspect(rewrite())` (§6.5).
17. **Delete `PlanInterpreter`/`PlanBuilder`** — the interpreter and mini-DSL are superseded by rewrite-rules-that-emit-
    `branch`; slated for deletion, not refactor (§6.3, §6.5).

---

## 9. One paragraph, for the record

metatron is address-first: the uri is the primary citizen, the obj the inhabitant. Its data is a cyclic graph of
`uri → obj` pairs, made cyclic by `!*`. The uri ring — `·` is `x/y`, `+` is `{x,y}` — is the floor, and distributivity
is what moves a colocation cut. The stream ring reads that algebra over *instructions* (floatable/blocked, the
coefficient), the machine ring over *places* (`move`/`+`), and Thm 19 glues them. A machine is a frame of reference —
memory, network, instset, compiler, processor — selected by the thread's `Machine.current()`. Code is signatures until
a compiler binds them to functions; shipping is moving signatures, compilation is binding them, and the monad pool is
where the bound code evolves under interchangeable worker threads, gated by the barrier that *is* the `+`'s join.
Distribution, finally, is `branch` — a `Triplet(args, f, route)`: the rewrite rules aim at `branch()` (find the fold
via `?decq`, factor the seed, emit `branch`), the route maps each lane to a component's `eval` (the ship-code verb), and
the branch's mtron `f` applies each lane to its eval. There is no plan interpreter and no mini-DSL — the plan *is* the
branch's mtron body, and the "plan data" is a deducible block-diff (`inspect(rewrite())` → `[lhs => rhs]` by vid).
Addressability is resolved by the Processor/Computer split (the Computer's `cache` makes the pools first-class
addresses). `to`/`from`/`eval` are the imperative floor; `branch` is the declarative noun that names a sound pattern of
them; Thm 19 is *why* the pattern is sound.
