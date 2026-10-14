---
name: mach-instset
description: |
  The machine instruction set at `/m/mach`: a machine is metatron's unit of **process** — a `space::T` whose five
  slots are its instruction set, compiler, memory, network and processor.
---

# mach instruction set (`/m/mach`)

A **machine** is metatron's unit of process: everything runs in one, and `/` is the root machine.

`machine::T` carries exactly five components, each one `rec::T` entry:

| slot        | what it holds                                                       |
|-------------|---------------------------------------------------------------------|
| `instset`   | the machine's instruction set architecture (types and insts)        |
| `compiler`  | the source to `code::T` converter                                   |
| `processor` | the runtime that executes `code::T` over the metatron graph         |
| `memory`    | the address space: relative uris (stack) and absolute uris (spaces) |
| `network`   | the machine's own reach — the peers it dialed or accepted           |

Machines **nest**: a pushed child reads through to its parent's **memory**, so a child is a nested frame of reference
rather than a copy of the world. Reach does not nest — see *network*: peers are the pushing machine's own, knowledge
(the roster) is root-shared.

## instset (instruction memory)

`instset` is the frame's **visible vocabulary**: the types, instructions, rewrites and sugars a program compiled here
can see. It is a union in two directions — this frame's own structure over its **imported** sets, and that whole stack
over the **enclosing machine's** instset. Memory and network are a two-level walk (a frame over its parent); the
vocabulary is that *and* an ordered list.

| part          | what it is                                                                                                                                                          |
|---------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| own structure | the `type` / `inst` / `rewrite` / `sugar` entries written in this frame                                                                                             |
| `reference`   | an ORDERED list of the vids of the instruction sets this frame imported — held by reference, never copied                                                           |
| `previous`    | the enclosing machine's instset — inherited **lazily**, so a child that imported nothing still sees what its parent imported, even an import the parent makes later |

Two rules are the whole point:

* **an import is a reference, never a copy** — the library in space is shared by every machine that imported it, and
  mutated by none;
* **resolution is own-first** — a declaration made here outranks a name from any set this one refers to, and among the
  references **import order is precedence**.

The membership lives in the rec under `reference` — singular, like `pattern`, `type` and `inst`, holding a list — so
the vocabulary reads from mtron as itself rather than as an opaque template. Importing is the `import` instruction,
which takes the ISA's uri.

The rec records **vids**; the frame holds the sets **by object**. That split makes membership reflective *and*
independent: printing the rec never inlines a library, and resolution reads the objects, so belonging to a machine
does not depend on the address index at all.

### instruction set architecture (machine schema)

<!-- figure source: assets/tikz/mach-instset.tex — white ink on transparent (the site theme is dark); edit there, re-render with the tikz MCP server, drop the PNG in assets/ -->
<img src="../assets/mach-instset.png" alt="one machine, two memories, one read, drawn as two boxes headed by their types, memory::T and instset::T. An instruction set joins the machine twice: the machine::T vertex sends membership (solid) into instset::T and registration (dashed) into memory::T. memory::T does two things, one row each — a value at an address (/usr/marko/age, value, 29) and a route (the name plus, route, the address /m/inst/plus) — and a dotted line carries that same address across the gap into instset::T. There the query ?nat<=nat admits the int contract (drawn solid) while the address's other candidates, real and str, sit dotted inside a group bracket labelled off nat's path, because the query's domain only reaches down nat's path. Beneath both boxes one read runs — mtron's *id, then network, then memory, then instset (on a miss) — with dotted lines tying each hop to the box it asks. The tags under each header give the two lenses: INSTANCES / data and SCHEMA / inst" width="100%" style="margin:0.3em 0 0 0"/>

An ISA is also a `space::T`, and that is now down to **one remaining dependency**. Belonging to a machine has two
halves:

| half         | what it is                                                                                                                                              |
|--------------|---------------------------------------------------------------------------------------------------------------------------------------------------------|
| membership   | the ISA is held **by object** in the frame that referred it — this is the vocabulary, and it needs no address space at all                             |
| registration | the ISA is also entered in the machine's **address index** — the only reason it is a `space::T`, and the only reason its content is reached by asking for it *as* an address |

Both halves happen in one place, so neither is had without the other by accident. The union needs only membership: a
machine can already hold a member that is **not** one of its spaces, and a read reaches it through instruction memory.
Removing the registration finishes the sentence — it is blocked on three things, each measured rather than assumed:

* **migration writes** — an instruction set's setup hands a declaration that falls outside its own pattern to the set
  that owns it by writing to an *address*, which only works while the owner can be found in the address index;
* **declining spaces** — the catch-all space matches every address, so a space must answer `noobj` for a vid it cannot
  interpret rather than throw, or an odd query string becomes a boot failure;
* **documentation ownership** — a set's setup wraps each declaration in its documentation, and resolving *which space
  owns an id* goes through the address index: with the registration gone that resolution falls through to the
  enclosing space, and a setup goes from ~0.1s to ~4.2s (measured: 4.0s to 200.5s on one suite).

**Not yet**: the vocabulary a frame sees is meant to be the union of the sets it imported *and* the declarations made
under its own `~` — `~/type/friend`, `~/inst/purchase` — for those same four kinds. `const` is deliberately excluded
from that projection, and the reason is the point: `~` is the frame's whole address space, so a projection that
admitted consts would union the frame's **memory** into its instruction set. (A few consts do still sit in the
instruction sets' own tables; that is the one place the schema/instance split is blurred, and gutting them is a
separate pass.)

A frame's own declarations are visible from that frame because the read asks data memory before instruction memory;
moving them into the frame's own ISA structure is the write path that makes the projection real, and it comes first.
The projection also needs `~` to be the current machine — it resolves to the current machine's vid and does **not**
fall through to an enclosing frame, so inheriting a parent's `~`-addressed vocabulary has to be *built* (re-bind `~`
at each level of the walk, or walk frames in the projection); it does not come free with the address walk.

### what a read is

Reading the vocabulary is not a lookup that stops at the first frame that answers. It is a walk of the whole union —
own structure, then each reference in import order, then the enclosing frame — collecting **candidates**, and the
rules are per **identity** rather than per name:

* an identity a nearer level already answered is **shadowed** — own-first, and the reason a local redeclaration wins;
* an identity not yet answered is **added alongside** — a set that overloads a name does not erase the name, so `plus`
  is a family rather than a slot.

Identity is the schema's own: an **inst or rewrite by its `tid`**, a **type or const by its `vid`**.

Because the union is schema, a read is a **query**. The dom/rng in the pattern are type constraints: a candidate whose
domain the query's domain refines is admitted, walking the refinement lattice — `*plus?nat<=nat` answers with the
`int` contract, since nat's path is nat → int → #, while `*plus` answers with the whole family. A query that names a
dom ranks candidates closest-to-the-token first; one that does not is unranked.

A short name is an address in disguise, and it is **data memory** that resolves it — the small→big tables turn
`plus` into `/m/inst/plus` — after which instruction memory answers the address. The division is deliberate: a name is
not something an instruction set resolves about itself.

The vocabulary belongs to the **machine**, so every thread of that machine sees the same one — per-thread state lives
on the stack, not in a component. Three behaviors in particular are pinned rather than assumed: identity shadowing and
adding alongside, including a member that is not a space; dom/rng admission down the refinement lattice; and
isolation — own, inherit, no leak up, no leak across.

## compiler

`compiler::T` is a **composition**, not an algorithm. Four atomic stages, each an optional rec entry with a default:
applying a compiler runs them in order — `parse → rewrite → resolve → type` — handing each the whole of what the last
one produced.

<!-- figure source: assets/tikz/mach-stages.tex — white ink on transparent (the site theme is dark); edit there, re-render with the tikz MCP server, drop the PNG in assets/ -->
<img src="../assets/mach-stages.png" alt="the compiler's four stages, and the processor that runs what they produce" width="100%" style="margin:0.3em 0 0 0"/>

Because the stages are *wiring*, the largest compiler and the smallest are the same type: a bare `compiler::[=>]` is
the default compiler, and naming one entry overrides exactly that one stage.

```mtron
mtron> compiler::[=>]
```
| stage   | rec key    | default                | what it settles                                                       |
|---------|------------|------------------------|-----------------------------------------------------------------------|
| parse   | `parser`   | `mtron_parser::T`      | the obj a program's text denotes — and so the language being compiled |
| rewrite | `rewriter` | `fixpoint_rewriter::T` | the instruction chain itself, before any type is known                |
| resolve | `resolver` | `scoring_resolver::T`  | which concrete instruction each call becomes, per element type        |
| type    | `typer`    | `typer::T`             | the type assertions applied to the result                             |

Only the parser accepts source text (or an already-parsed obj — see *parse*); every stage from there on takes
`code::T` and returns `code::T`. Naming a stage that is not that stage's own type is refused where it is written, not
when the compiler is first used:

```mtron
mtron> compiler::[parser=>mtron_parser::[=>]]
mtron> compiler::[rewriter=>fixpoint_rewriter::[max=>3]]
```
### parse — where a language is hosted

`parser::T` is the seam where a **language** is hosted: any language can be parsed as long as it generates `code::T`.
Given the rich type system, the object-oriented and functional-oriented approach, and turing-completeness, most any
language can map to mtron `code::T`.

Because the stage emits code, and because the three stages after it, the processor, and the distributed runtime all
take code, another surface syntax needs no new instruction set, evaluator or type system — only a `parser::T` wired
into a compiler:

```mtron
compiler::[parser=>a_lang::[=>]]   [-- the shape; a_lang::T is yours to register --]
```

The stage is **total over objs**: an obj that is not a `str` is not source text, so it is not re-read — it is lifted as
it stands. One compiler therefore takes a program's text or the obj it has already been parsed into.

A grammar's own result is not always a code. mtron's reads three shapes, and the stage lifts all three:

| source      | the grammar yields | lifted to                           |
|-------------|--------------------|-------------------------------------|
| `6.plus(1)` | `code::T`          | code as result                      |
| `plus(1)`   | a lone `inst::T`   | code with one-inst (no start added) |
| `5`         | `int::5`           | code that starts with it            |

A lone inst deliberately gets **no start**: a head-first call is a fragment relative to its lhs (`plus(1)` applied to
`noobj` is `noobj`, applied to `6` is `7`), and inventing one would turn a pipeline stage into a program that runs on
nothing.

`mtron_parser::T` is the parser metatron ships — source text written in metatron's mtron language to `code::T`:

```mtron
mtron> mtron_parser::[=>]
```
### rewrite — the chain, before any type

The rewrite stage runs **before resolution**, so the rules see generic dom/rng rather than bound ones. This is where
the shape of the compiled chain is decided: a rule-driven collapse, a native pushdown, and a distributed reduce's
gather point all enter the compiled form here.

| rewriter               | the rule                                                                                                                                                                                                                            |
|------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `fixpoint_rewriter::T` | apply every rewrite rule registered by every instset, in turn, over the whole code, and repeat until the code hash is unchanged for `max` consecutive passes (default `2`) — so a rule re-runs on every pass and must be idempotent |
| `identity_rewriter::T` | return the code unchanged — for a compiler whose rewriting happens elsewhere                                                                                                                                                        |

```mtron
mtron> fixpoint_rewriter::[=>]
mtron> fixpoint_rewriter::[max=>3]
```
### resolve — the walk and its two strategies

Resolution is the compiler's widest-reaching stage: it turns a generic instruction chain into a typed one by threading
each inst's range into the next inst's domain, and **per inst** it asks two questions of two internal components:

| collaborator | the question                                                  | default                |
|--------------|---------------------------------------------------------------|------------------------|
| `selector`   | which candidates may this call resolve to, and in what order? | `specificity_selector` |
| `binder`     | can this candidate be made concrete?                          | `generic_binder`       |

A candidate that cannot be bound must not be selectable, so binding *gates* selection — it runs inside the selector's
candidate walk. That is why the pair is two rec entries of one stage rather than two stages, and why any selector
composes with any binder:

```mtron
mtron> firstfind_resolver::[=>]
mtron> scoring_resolver::[selector=>firstfind_selector::[=>]]
mtron> identity_resolver::[=>]
```
| strategy                  | the rule                                                                                  |
|---------------------------|-------------------------------------------------------------------------------------------|
| `specificity_selector::T` | score candidates by how specific their signatures are; take the first the binder can bind |
| `firstfind_selector::T`   | the first viable candidate in read order, no scoring — selection is order-dependent       |
| `generic_binder::T`       | bind generics to the lhs, gate on the lhs domain, resolve the call's arguments            |
| `identity_resolver::T`    | no walk at all — it returns the code unchanged                                            |

What the walk settles is what the processor then runs: whether an inst is a **filter** (whose `{0,1}` wing is dropped
for compile-time typing only), a **gather/barrier**, or an **initial** inst seeding the element type from its own
argument, and a cast whose range is rebound to the named type. An inst that cannot be resolved is left in place for
**runtime** resolution: a semi-resolved code, not a failed one.

### type — the fourth stage

The last stage is the machine's own type assertion. `typer::T` is where a frame holds *how much* checking it wants, in
the same style as the other three: a rec entry, optional, defaulting.

**Not yet wired**: the typer is the identity today, and enforcement still reads the global registry — flags mirrored
at `/sys/typer/stage`, which a boot profile sets through its `typer/stage` rec. Moving those flags into the machine is
what the `typer::T` stage is for.

## processor

The compiler produces code; the `processor` runs it. The two are siblings rather than stages of one another, which is
why the compiler's last stage hands off instead of invoking: what a program *means* and how it is *run* are separate
axes of a machine.

| type                 | what it is                                                                                                                                                            |
|----------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `processor::T`       | the runtime contract: a machine component that executes a `code::T` and reports a `state` (`stop` / `run` / `pause`) and a `result`                                   |
| `monad_processor::T` | a processor that carries monadic state                                                                                                                                |
| `swarm_processor::T` | the default: it schedules independently executing monads across the code's inst chain; barriers synchronize them, and the objects of the halted monads are the result |

### threads

A machine's work is carried by threads (`/m/mach/thread`), `rec::T`s with a lifecycle:

| key       | what it is                                         |
|-----------|----------------------------------------------------|
| `code`    | the code the thread is executing                   |
| `time`    | when the thread was started                        |
| `runtime` | the thread's current running time                  |
| `loop`    | a delay to repeat the code (absent: evaluate once) |
| `state`   | the thread's current state                         |
| `result`  | the last result the thread produced                |

`thread::T` is the base; `core::T` and `virtual::T` are the two concrete platform strategies
(`core::[code=>…,loop=>second::1.0]@~/thread/…` runs a platform thread, `virtual::` a virtual one). The default
machine's work runs on virtual threads.

## memory (data memory)

`memory::T` is the machine's **data memory**: the address space of *instances*. Its relative bindings *are* the rec,
and the index of absolute spaces is the `space` entry inside it, so a memory reads from mtron like any other rec. A
child machine reads through to its parent's memory; that walk is what makes a machine a frame of reference.

**Two memories, one read.** `memory` holds instances and `instset` holds schema, and a read is the language's single
way in: a read asks three sources in order — the **network** (an address carrying an authority a peer declared
is that peer's to answer), then **data memory**, then **instruction memory** if data memory missed. The division of
labor is clean and worth holding on to: **data memory routes names to addresses** (its small→big tables are what turn
`plus` into `/m/inst/plus`) and **instruction memory answers addresses**. A read made *from inside* an instruction-set
read is a data read — answering a schema query resolves the dom/rng types of the pattern, and resolving a type is
itself a read — so instruction memory is not re-entered from there.

## network — reach and knowledge

`network::T` indexes the machine's **reach**: a map $\mathit{service} \Rightarrow \mathit{peer}$ whose key is the bare
authority (`scheme://host:port`) and whose value is a `peer::T`.

| field       | what it is                                                                            |
|-------------|---------------------------------------------------------------------------------------|
| `authority` | the service key — the one field a peer cannot lack                                    |
| `host`      | where it was dialed, route and `?q` processors included (`ws://h:p/mtron?clientq=me`) |
| `name`      | the identity the dialer announced                                                     |
| `transport` | an inst, $\mathit{message} \mapsto \mathit{answer}$ — the reach itself                |
| `client`    | the protocol client the dial constructed, held for the hang-up                        |

One type, **two honest states**: an `authority` alone is *known, not reachable from here* — the form a peer reference
takes after crossing a wire; an authority with a `transport` is reachable.

**Construction is connection.** There is no connect inst, as there is no add-space inst: constructing a protocol
client *is* the dial, and indexing is a background behavior of a valid construction —

```mtron
ws_client::[pattern=><ws://localhost:8555/mtron?clientq=marko>]
```

The claim (`pattern`, retracted to the dialable address) derives the host; on success the client indexes itself into
the network exactly as `memspace::[pattern=>xyz/#]` indexes into Memory. The lifecycle is one adjoint pair on the
addressable contract: $\mathrm{index} (m)$ — a thing that exists enters the jurisdiction — and its dual
$\mathrm{drop} (id)$ — $id \mapsto \mathrm{noobj}$, the universal close. What index granted, drop retracts; a displaced
value is closed (close-before-clear), so "write noobj" and "hang up" are one act.

**Identity rides the dial.** The `?clientq` processor on the connection uri *is* the identity exchange — no announce
message, no anonymous-then-upgrade: the listener co-mounts a **named** inbound peer the moment the socket opens
(server : peer :: constructor : instance). The service key spends exactly that one processor; every other `?q` rides
through.

**The identity element.** `id_client::T` (`/m/mach/id_client`) is the identity of the protocol-client family, bound
to the scheme `loop:`: its constructor is the dial, its `send_recv` applies the shipped message against the local
machine. A read through a loop peer therefore *equals* the local read — the self-similarity contract in executable
form, with no socket.

**Transport convention.** The message rides as `send_recv`'s argument, and a user's `|` (block) defers evaluation so
code ships as written: `1.plus(2).send_recv(|plus(45))` evaluates `plus(45)` at the far side against the shipped
monad. Insts are total, $P (\mathrm{Obj}) \to P (\mathrm{Obj})$: a transport never errors — it answers a smaller set,
and $\emptyset$ (noobj) is a legal answer, not an exception.

**Two laws across the frame stack** — `push` mints each child its own network level, the same parade as its memory and
its instset:

1. **knowledge is shared** — the roster is one listing at the root address (memory's base frame, machine-global): a
   declaration made standing anywhere is every frame's fact;
2. **reach is not** — peers are neither inherited (not even shadowed) nor shared with children: a connection belongs
   to exactly the machine that dialed it, a pop retracts precisely its dials, and a sibling never sees them.

The frame-stack laws and the wire are pinned by scripted runs over live sockets: wire equalities, `?clientq` naming,
value shapes, retraction, roster and frame laws, and authority aliasing — as are the loop and ws equalities.

The Java-level counterpart — the classes, the package layout, and the load-bearing invariants — lives in
[machine architecture (Java)](../metatron/references/machine-architecture-java.md).

## appendix — the registered types

Everything above is a subtype of `component::T` at `/m/mach/component` — except `machine::T` itself and the
non-component families, which hang directly off `/m/mach`:

| family   | types (`::T`)                                                                                                                                                                                                                                       |
|----------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| compiler | `compiler`, `parser`, `mtron_parser`, `rewriter`, `fixpoint_rewriter`, `identity_rewriter`, `resolver`, `scoring_resolver`, `firstfind_resolver`, `identity_resolver`, `selector`, `specificity_selector`, `firstfind_selector`, `binder`, `generic_binder`, `typer` |
| process  | `processor`, `monad_processor`, `swarm_processor`                                                                                                                                                                                                   |
| state    | `memory`, `network`, `instset`                                                                                                                                                                                                                      |
| machine  | `machine` (at `/m/mach/machine`, a `space::T`)                                                                                                                                                                                                      |
| the rest | `thread` / `virtual` / `core`, `peer`, `id_client`, `cluster`, `monad`, `component` (`/m/mach/component`, the root of every row above)                                                                                                              |

## appendix — writing another language

The whole obligation is a parser that emits `code::T`:

1. Register a `parser::T` subtype in your own instruction set — a `rec::T` under your ISA with a constructor that
   mints your parser. The seam asks for nothing else.
2. Read your surface syntax and build mtron code — a value, an inst, or a `code::T`. Any of the three lifts into a
   program.
3. Leave a non-`str` obj alone (the identity case) so the same compiler still accepts what is already parsed.
4. Wire it: `compiler::[parser=>my_lang::T]`, or put it in a machine's `compiler` slot.
5. Everything else is inherited: rewriting, resolution against the real instruction sets, the type system, the
   processor, and distribution.

The smallest possible instance: a two-word syntax (`add 6 to 1`) whose parser emits `6.plus(1)`, wired into a compiler
and run on mtron's own `plus`. Nothing is added but the parser.

## see also

* [mtron language reference](language-reference-mtron.md) — the surface the default parser reads: sugar, `>>`
  rshift, `.as(type::T)`, coefficients, `.explain()`.
* [mtron type system](type-system-mtron.md) — vid/tid, predicates (isa vs non-isa), nominal vs structural, refinement.
* [the category graph](cat-instset-mtron.md) — the declared rewrites the `rewriter` stage consumes, and the
  `LawTable` that bridges an op address to a declared law.
* [ui instruction set](ui-instset-mtron.md) — `/m/mach/ui`, the widget family carried by the same ISA.
* figure sources — `assets/tikz/mach-*.tex` (TikZ, rendered with the tikz MCP server; the PNGs sit beside them in
  `assets/`).