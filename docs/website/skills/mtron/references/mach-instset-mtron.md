---
name: mach-instset
description: |
  The machine instruction set at `/m/mach`: a machine is metatron's unit of **process** — a `space::T` whose five
  slots are its instruction set, compiler, memory, network and processor.
  TRIGGER: When wiring or replacing a compile stage (`compiler::[rewriter=>…,resolver=>…]`), writing a parser for a
  surface syntax other than mtron, choosing a resolution strategy (`scoring_resolver` vs `firstfind_resolver`,
  specificity vs first-match selection), asking what a machine's `compiler` / `processor` / `memory` / `network` slot
  holds, or tracing why an instruction chain compiled the way it did.
---

# mach instruction set (`/m/mach`)

A **machine** is metatron's unit of process: everything runs in one, and `/` is the root machine.

`machine::T` carries exactly five components, and each is one rec entry:

| slot        | what it holds                                                       |
|-------------|---------------------------------------------------------------------|
| `instset`   | the instruction sets this frame can see (an n-ary union of imports) |
| `compiler`  | the source to `code::T` converter, in stages                        |
| `memory`    | the address space: relative uris (stack) and absolute uris (spaces) |
| `network`   | the peers a machine indexes and maintains shared state              |
| `processor` | the runtime that executes `code::T` over the metatron graph         |

Machines **nest**: a pushed child machine reads through to its parent's state, so a child is a nested frame of
reference rather than a copy of the world. What follows is the compiler, then the processor, then the other slots.

## the compiler — four stages

`compiler::T` is a **composition**, not an algorithm. Four atomic stages, each an optional rec entry with a default:
`compiler.apply` runs them in order — `parse → rewrite → resolve → type` — handing each the whole of what the last one
produced.

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

Only the first stage accepts anything but code: `parse` takes source text (or an already-parsed obj — see *parse*),
and every stage from there on takes `code::T` and returns `code::T`. Naming a stage that is not the stage's own type
is refused where it is written, not when the compiler is first used:

```mtron
mtron> compiler::[parser=>mtron_parser::[=>]]
mtron> compiler::[rewriter=>fixpoint_rewriter::[max=>3]]
```
## parse — where a language is hosted

`parser::T` is the seam where a **language** is hosted: any language can be parsed as long as it generates `code::T`.
Given the rich type system, the object-oriented and functional-oriented approach, and turing-completeness, most any
language can map to mtron `code::T`.

That is what makes the stage worth isolating. Because it emits code, and because the three stages after it, the
processor, and the distributed runtime all take code, another surface syntax needs no new instruction set, no new
evaluator and no new type system — only a `parser::T` wired into a compiler:

```mtron
compiler::[parser=>a_lang::[=>]]   [-- the shape; a_lang::T is yours to register --]
```

The stage is **total over objs**: an obj that is not a `str` is not
source text, so it is not re-read — it is lifted as it stands. One compiler therefore takes a program's text or the
obj it has already been parsed into.

A grammar's own result is not always a code. mtron's reads three shapes, and the stage lifts all three:

| source      | the grammar yields | lifted to                           |
|-------------|--------------------|-------------------------------------|
| `6.plus(1)` | `code::T`          | code as result                      |
| `plus(1)`   | a lone `inst::T`   | code with one-inst (no start added) |
| `5`         | `int::5`           | code that starts with it            |

A lone inst deliberately gets **no start**: a head-first call is a fragment relative to its lhs (`plus(1)` applied to
`noobj` is `noobj`, applied to `6` is `7`), and inventing one would turn a pipeline stage into a program that runs on
nothing.

`mtron_parser::T` is the parser metatron ships — it converts source text written in metatron's mtron language to
`code::T`:

```mtron
mtron> mtron_parser::[=>]
```
## rewrite — the chain, before any type

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
## resolve — the walk and its two strategies

Resolution is the compiler's widest-reaching stage: it turns a generic instruction chain into a typed one by threading
each inst's range into the next inst's domain, and **per inst** it asks two questions of two internal components:

| collaborator | the question                                                  | default                |
|--------------|---------------------------------------------------------------|------------------------|
| `selector`   | which candidates may this call resolve to, and in what order? | `specificity_selector` |
| `binder`     | can this candidate be made concrete?                          | `generic_binder`       |

A candidate that cannot be bound must not be selectable, so binding *gates* selection —
it runs inside the selector's candidate walk. That is why the pair is two rec entries of one stage rather than two
stages, and why any selector composes with any binder:

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

## type — the fourth stage

The last stage is the machine's own type assertion. `typer::T` is where a frame holds *how much* checking it wants,
in the same style as the other three: a rec entry, optional, defaulting.

**Not yet wired**: `typer.apply` is the identity today, and enforcement still reads the global registry — the
`TypeCheck` flags mirrored at `/sys/typer/stage`, which a boot profile sets through its `typer/stage` rec. Moving
those flags into the machine is what the `typer::T` stage is for.

## the processor — the sibling

The compiler produces code; the `processor` runs it. The two are siblings rather than stages of one another, which is
why the compiler's last stage hands off instead of invoking: what a program *means* and how it is *run* are separate
axes of a machine.

| type                 | what it is                                                                                                                                                            |
|----------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `processor::T`       | the runtime contract: a machine component that executes a `code::T` and reports a `state` (`stop` / `run` / `pause`) and a `result`                                   |
| `monad_processor::T` | a processor that carries monadic state                                                                                                                                |
| `swarm_processor::T` | the default: it schedules independently executing monads across the code's inst chain; barriers synchronize them, and the objects of the halted monads are the result |

## the other slots

* **`memory::T`** — the address space. Its relative bindings *are* the rec, and the index of absolute spaces is the
  `space` entry inside it, so a memory reads from mtron like any other rec. A child machine reads through to its
  parent's memory; that walk is what makes a machine a frame of reference.
* **`network::T`** — the peers a frame can reach. A `peer::T` is one roster entry — an `authority`, an optional
  `name`, and an optional `transport` — and a peer that crossed a wire is *known but unreachable*: one type, two
  honest states.
* **`instset`** — the frame's visible instruction sets, imported as an n-ary union.

## threads

A machine's work is carried by threads (`/m/mach/thread`), which are `rec::T`s with a lifecycle:

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

## the registered types

Everything above is a subtype of `component::T` at `/m/mach/component` — except `machine::T` itself and the
non-component families, which hang directly off `/m/mach`:

| family   | types (`::T`)                                                                                                                                                                                                                                                        |
|----------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| compiler | `compiler`, `parser`, `mtron_parser`, `rewriter`, `fixpoint_rewriter`, `identity_rewriter`, `resolver`, `scoring_resolver`, `firstfind_resolver`, `identity_resolver`, `selector`, `specificity_selector`, `firstfind_selector`, `binder`, `generic_binder`, `typer` |
| process  | `processor`, `monad_processor`, `swarm_processor`                                                                                                                                                                                                                    |
| state    | `memory`, `network`                                                                                                                                                                                                                                                  |
| machine  | `machine` (at `/m/mach/machine`, a `space::T`)                                                                                                                                                                                                                       |
| the rest | `thread` / `virtual` / `core`, `peer`, `cluster`, `monad`, `component` (`/m/mach/component`, the root of every row above)                                                                                                                                            |

## writing another language

The whole obligation is a parser that emits `code::T`:

1. Register a `parser::T` subtype in your own instruction set — a `rec::T` under your ISA with a constructor that
   mints your parser. The seam asks for nothing else.
2. Read your surface syntax and build mtron code — `Obj` for a value, an inst, or a `code::T`; the serializer's parse
   is the mtron half, and `Parser.Helper.toCode` lifts any of the three into a program.
3. Leave a non-`str` obj alone (the identity case) so the same compiler still accepts what is already parsed.
4. Wire it: `compiler::[parser=>my_lang::T]`, or put it in a machine's `compiler` slot.
5. Everything else is inherited: rewriting, resolution against the real instruction sets, the type system, the
   processor, and distribution.

The test harness carries a worked example of the smallest possible instance — a two-word syntax (`add 6 to 1`) whose
parser emits `6.plus(1)`, wired into a compiler and run on mtron's own `plus`. Nothing was added but the parser.

## see also

* [mtron language reference](language-reference-mtron.md) — the surface the default parser reads: sugar, `>>`
  rshift, `.as(type::T)`, coefficients, `.explain()`.
* [mtron type system](type-system-mtron.md) — vid/tid, predicates (isa vs non-isa), nominal vs structural, refinement.
* [the category graph](cat-instset-mtron.md) — the declared rewrites the `rewriter` stage consumes, and the
  `LawTable` that bridges an op address to a declared law.
* [ui instruction set](ui-instset-mtron.md) — `/m/mach/ui`, the widget family carried by the same ISA.
* [machine architecture (Java)](../metatron/references/machine-architecture-java.md) — the same machine read from the
  Java side: `BasicMachine`, `BasicCompiler`, the stage classes, and the load-bearing invariants.
* figure sources — `assets/tikz/mach-*.tex` (TikZ, rendered with the tikz MCP server; the PNGs sit beside them in
  `assets/`).