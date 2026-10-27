---
name: mach-instset
description: |
  The machine instruction set at `/m/mach`: a machine is metatron's unit of **process** — a `rec::T` whose five
  slots are its instruction set, compiler, memory, network and processor. Machines nest as frames (`push`, `move`,
  `pop`) and distribute work by shipping code — workloads of config and code — to their peers.
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
(the roster) is root-shared. *Frames* covers the three instructions that choose where code runs, and *distribution*
covers how work reaches a machine in another process: by shipping code to it.

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

```mtron_pre
compiler::[=>]
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

```mtron_pre
compiler::[parser=>mtron_parser::[=>]]
compiler::[rewriter=>fixpoint_rewriter::[max=>3]]
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

```mtron_pre
mtron_parser::[=>]
```

### rewrite — the chain, before any type

The rewrite stage runs **before resolution**, so the rules see generic dom/rng rather than bound ones. This is where
the shape of the compiled chain is decided: a rule-driven collapse, a native pushdown, and a distributed reduce's
gather point all enter the compiled form here.

| rewriter               | the rule                                                                                                                                                                                                                            |
|------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `fixpoint_rewriter::T` | apply every rewrite rule registered by every instset, in turn, over the whole code, and repeat until the code hash is unchanged for `max` consecutive passes (default `2`) — so a rule re-runs on every pass and must be idempotent |
| `identity_rewriter::T` | return the code unchanged — for a compiler whose rewriting happens elsewhere                                                                                                                                                        |

```mtron_pre
fixpoint_rewriter::[=>]
fixpoint_rewriter::[max=>3]
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

```mtron_pre
firstfind_resolver::[=>]
scoring_resolver::[selector=>firstfind_selector::[=>]]
identity_resolver::[=>]
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

`network::T` indexes the machine's **reach**: a map $\mathit{key} \Rightarrow \mathit{peer}$, one member per
**surface**, whose value is a `peer::T`. The key is the dial's own address as a legal path — scheme, every label of the
host, port, route, and never a `?q`: `ws://localhost:8555/mtron?name=marko` sits at `ws/localhost/8555/mtron`, so the member
*is* an address, `~/network/peer/ws/localhost/8555/mtron`. A legal path carries no dot in any segment (a dotted first
segment reads as a host), so a host is split into its labels: `ws://peer.local:8555/mtron` is
`ws/peer/local/8555/mtron` and `ws://10.0.0.7:8555/mtron` is `ws/10/0/0/7/8555/mtron`. Two routes of one
service (`/mtron` and `/mcp` on one port) are two members; a loopback alias is spelled `localhost`, so `127.0.0.1` keys
the member `localhost` already holds.

| field       | what it is                                                                         |
|-------------|------------------------------------------------------------------------------------|
| `origin` | the service it reaches (`scheme://host:port`) — the one field a peer cannot lack   |
| `host`      | where it was dialed, route and `?q` processors included (`ws://h:p/mtron?name=me`) |
| `name`      | the identity the dialer announced                                                  |
| `transport` | an inst, $\mathit{message} \mapsto \mathit{answer}$ — the reach itself             |
| `client`    | the protocol client the dial constructed, held for the hang-up                     |

One type, **two honest states**: an `origin` alone is *known, not reachable from here* — the form a peer reference
takes after crossing a wire; an origin with a `transport` is reachable.

### prefix — what you call a peer

A fourth field, and the only one that is a **name** rather than an address: `prefix` is
`<label> => <full origin>` (`ginger => ws://ginger.local:8555`). It answers a different question from the other three —
`roster` is what the system *knows*, `peer` is what this machine has *dialed*, `listener` is what it *answers on*, and
`prefix` is what you *call* it.

```mtron
</network/prefix/ginger> -> <ws://ginger.local:8555>;   [-- bind: the name --]
//ginger/mtron?name=marko                               [-- = ws://ginger.local:8555/mtron?name=marko --]
</network/prefix> -> noobj                              [-- unbind every name at once --]
```

**The spelling is the addressed slot**: `//name/a` — a host with **no port and no scheme**. An explicit port is taken
literally (`//ginger:8555/a` always means that host and port), because *explicit always beats named*, and a real scheme
is never rewritten (`loop://self` already says which transport it means). The binding is read at the **root**, so no
frame can change what `//ginger` denotes; an unbound name is answered as written and fails closed, so no name resolves
to a discovered peer merely because one happens to answer.

**The dial is not the declaration.** A connection is *reach* — per frame, retractable, one member per surface — while a
name is *denotation*: one listing, at the root, and a `pop` must not change it. So a name is bound by a write and the
peer is dialed by a client, one act each:

```mtron
</network/prefix/ginger> -> <ws://ginger.local:8555>;
ws_client::[pattern=><ws://ginger.local:8555/mtron?name=marko>];
```

**Alignment happens once, at the gatekeeper**, in `Machine.Helper.align`, which asks each component for the slot it
owns: the instruction set for **scheme** prefixes (`math:cos` → `/m/math/inst/cos`) and the network for **authority**
prefixes (`//ginger/a` → the origin above). The two slots cannot collide, so the order is unobservable and no precedence
rule exists. The same door is what the compiler's move-extractor uses, which is why `move(<//ginger/a>)` ships the tail
to the *resolved* origin rather than to a name the far side has never heard of.

**Construction is connection.** There is no connect inst, as there is no add-space inst: constructing a protocol
client *is* the dial, and indexing is a background behavior of a valid construction —

```mtron
ws_client::[pattern=><ws://localhost:8555/mtron?name=marko>]
```

The claim (`pattern`, retracted to the dialable address) derives the host; on success the client indexes itself into
the network exactly as `memspace::[pattern=>xyz/#]` indexes into Memory. The lifecycle is one adjoint pair on the
addressable contract: $\mathrm{index} (m)$ — a thing that exists enters the jurisdiction — and its dual
$\mathrm{drop} (id)$ — $id \mapsto \mathrm{noobj}$, the universal close. What index granted, drop retracts; a displaced
value is closed (close-before-clear), so "write noobj" and "hang up" are one act — at the member's own address:

```mtron
*</network/peer/ws/localhost/8555/mtron/origin>   [-- a member, field by field --]
</network/peer/ws/localhost/8555/mtron> -> noobj     [-- hang up: the member leaves the map --]
</network/peer> -> noobj                             [-- hang up every member --]
```

**No two equivalent connections.** Dialing a surface the machine already holds closes the connection there and the new
one takes its key; the identity a dial announces is no part of the surface.

**Identity rides the dial.** The `?name` parameter on the connection uri *is* the identity exchange — no announce
message, no anonymous-then-upgrade: the listener co-mounts a **named** inbound peer the moment the socket opens
(server : peer :: constructor : instance). It is a plain session parameter, like `?in=` and `?out=`, rather than a
q-processor; the key carries none of them, while `host` keeps the dial as it was written.

**The identity element.** `id_client::T` (`/m/mach/id_client`) is the identity of the protocol-client family, bound
to the scheme `loop:`: its constructor is the dial, and its mini-instset (`eval`, `from`, `to`) evaluates what it is
sent against the local machine. A read through a loop peer therefore *equals* the local read — the self-similarity
contract in executable form, with no socket.

**The mini-instset.** A client reaches the network through its `inst` — `{instruction tid => override}` — and
nothing else. Each override is the native instruction with the client's own body (the same signature, so a call
reaches it by swapping its function): `from(<uri>)` reads, `obj.to(<uri>)` writes (the obj is the lhs), and
`eval(code)` ships a program for the far side to run. A `ws_client` declares all three; an `http_client` declares
`from` (GET) and `to` (POST) — a write that stores what it is sent and evaluates nothing, so it serves no `eval`. A
shard target must serve both `to` (the route) and `eval` (the routed program must be applied there). `send_recv`
remains a `ws_client`'s raw wire instruction, not a reach: a user's `|` (block) defers evaluation so code ships as
written, `1.plus(2).send_recv(|plus(45))` evaluating `plus(45)` at the far side against the shipped monad. Insts are
total, $P (\mathrm{Obj}) \to P (\mathrm{Obj})$: a reach never errors — it answers a smaller set, and $\emptyset$
(noobj) is a legal answer, not an exception.

**Status.** Every client and every server answers `status` — a bool, true while it is live, derived each time it is
read and never stored. A `ws_client` is live while its socket is open, an `http_client` while its host answers a HEAD,
a loop client always, an `mcp_client` by its health check, an `mcp_server` while its own dispatch answers a ping, and
a server while its socket is bound. `*~/network/status` reports each dialed peer by its client's `status` — `up` or
`down` — and a roster address that was never dialed as `known`. A peer carries no transport of its own: it is talked
to with its client's `inst`. And a read never dials: construction connects (`http_client::[host=><http://h:p>]`),
destruction disconnects, and an address no client was constructed for falls through to memory.

**Supervision keeps the index true.** Between the index that granted a peer and the drop that retracts it, the
network watches every peer whose client holds a live connection — a *link*, `ws_client` today — and keeps the listing
honest. Connection on creation, destruction on deletion, and only the owner deletes:

| what happened                                                              | what the network does                                                                                                  |
|----------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------|
| the far side went away — restart, shutdown, crash, refusal                 | a **failure**: the peer stays listed, offline, and is redialed *in place* on a capped backoff (0.5 s doubling to 10 s) |
| a request needs a peer that is offline                                     | the request redials **on demand** (within its 5 s open wait), so the first request after a restart heals the link      |
| the owner hung up — the client closed or deleted, `drop`, the frame popped | the one **explicit shutdown**: the peer leaves the map at once and is never redialed                                   |

A dial made before its far side is up is a peer too — offline until the far side answers. Deleting the address that
holds a client is the hang-up, by close-before-clear:

```mtron
/usr/peer/local -> ws_client::[pattern=><ws://localhost:8555/mtron?name=marko>]   [-- dial, index, supervise --]
/usr/peer/local -> noobj                                                          [-- hang up: the peer leaves the map --]
```

Redialing in place is the point: the client *is* the connection's identity, so the handle stored at
`/usr/peer/local` stays the live connection across outages, and deleting it hangs up what is actually connected. While
a peer is offline, `*~/network/status` reports it `down` without a round trip, a round trip in flight when its link
dropped is answered at once with a fail rather than waiting out its deadline, and `law_shard` plans only over the peers
that answer — an outage shrinks the fan-out instead of poisoning the fold.

**Two laws across the frame stack** — `push` mints each child its own network level, the same parade as its memory and
its instset:

1. **knowledge is shared** — the roster is one listing at the root address (memory's base frame, machine-global): a
   declaration made standing anywhere is every frame's fact;
2. **reach is not** — peers are neither inherited (not even shadowed) nor shared with children: a connection belongs
   to exactly the machine that dialed it, a pop retracts precisely its dials — its sockets hung up and its supervision
   ended — and a sibling never sees them.

**Who a machine is: what it listens on.** A machine's own authorities — what mDNS announces for it and what the roster
files under its name — are exactly its **listening** surfaces: a server that binds a socket — a `ws_server` or an
`http_server` — is its network `listener` entry, and nothing else counts. A backend the machine *dials* — a `tblespace`, `mqttspace`, `grphspace`
or `vecspace` host — is reach, never identity, so its host, a `?user=…&password=…` login included, never leaves the
machine in an announcement, a roster key or a TXT route; an address that carries userinfo is refused outright rather
than stripped. There is no "is this mine" predicate either: the owner of a connection is always the machine it is
attached to.

The frame-stack laws and the wire are pinned by scripted runs over live sockets: wire equalities, `?name` naming,
value shapes, retraction, roster and frame laws, and authority aliasing — as are the loop and ws equalities, the
supervision rows (a restart redialed, a dial before the far side is up, a deleted handle, a pop's hang-up), and the
addressing rows (a member at its dial's path, deleted there, replaced by a redial, two surfaces side by side, a frame's
members at its own `~`).

The Java-level counterpart — the classes, the package layout, and the load-bearing invariants — lives in
[machine architecture (Java)](../metatron/references/machine-architecture-java.md).

## frames — `push`, `move`, `pop`

The machine nest is a tree of **frames**, and three instructions choose where code runs. They speak one address
dialect, the `..` motif — `~` is the frame you stand in, `a` (or `~/a`) its child, `..` its parent, `../b` the sibling
`b`, and `../../..` walks as far up as asked — and they differ only in what they do with the address:

| instruction | what it does with the address                                                                                                                     |
|-------------|---------------------------------------------------------------------------------------------------------------------------------------------------|
| `push(t)`   | **mints** the machine at `t` and makes it current. Only the leaf is new: $\mathrm{push}(t) = \mathrm{move}(\mathrm{parent}(t)) \cdot \mathrm{push}(\mathrm{name}(t))$, so a sibling is minted from the shared parent |
| `move(t)`   | **walks** to the machine at `t` and closes nothing; `move()` is `move(<..>)`                                                                      |
| `pop()`     | **discards** the current machine and lands on its `..` — a machine is discarded by the one that holds it                                         |

A frame is an address, so the tree is walked like any other part of the metatron graph. A `*`-less `>>` walks the
**uri graph** — a frame's child frames and its component slots, side by side — while `*…>>` walks the **obj graph**:
the two graphs are linked by `->` and crossed by `*`.

```mtron_pre
push(xyz).vid()
/.>>
/xyz>>
pop().vid()
```

Every pushed frame is a fresh **level** over its parent's — its own memory, instruction set and network — so it is a
clean scope: what it writes, imports and dials is its own, and a pop retracts exactly that. Reach in particular starts
empty: a pushed frame holds **no peers** until it dials one, which makes a frame the natural scope of a mini-cluster.

**The authority boundary.** A frame lives in this process. `push(<ws://localhost:8555/worker>)` names another process,
and is refused: code reaches a machine in another process by being *shipped* there (see *distribution*), never through
a frame here that stands in it. Spawning that process — a new peer JVM — is a mechanism still waiting for its
instruction.

**Not yet**: `pop` retracts the frame but not its registration — the machine is gone, yet its structure stays written
in the root's memory, so the tree still lists it:

```mtron_pre
/.>>
```

The same root cause makes a frame re-entered by address (`move(<x>)`, or a later evaluation) impossible to pop: the
registered structure carries no link to its parent, and `pop` answers it unchanged. And all three instructions answer
the whole machine — over a wire that is a large reply rather than a reference.

## distribution — shipping code to peers

Distribution is code shipped over the wire — `ws`, `http`, `mcp` — with results coming back as values. There is one
mechanism, the layout `law_shard` emits (see [the category graph](cat-instset-mtron.md)): pair each code with a route,
and gather the replies.

```mtron
start([w_1,w_2,w_3]).select([to(<s_1>),to(<s_2>),to(<s_3>)]).merge()   [-- zip: code i ships to peer i --]
[w]-<[to(<s_1>),to(<s_2>),to(<s_3>)]                                   [-- broadcast: one code to every peer --]
```

`to(<peer>)` ships its lhs unevaluated; the peer evaluates it and replies with the value. A select (`==`) zips codes
with routes, a split (`-<`) broadcasts one code — reach for the zip only when the payload differs per peer.

### workloads — config and code in one closure

A peer must receive *what to do* and *what to do it with* in one message: a binding made by one evaluation lives on
that thread's frame stack, and is gone by the next message. So the payload is a **workload** — a rec of config and
code — evaluated in a closure that binds it:

```mtron_pre
[db=>{4,5,6},code=>|(*workload/db.plus(2))](workload=>_){ !*workload/code.apply() }
```

`(workload=>_)` binds the rec on the evaluating frame's stack, and `|(…)` keeps the code inert until
`!*workload/code.apply()` runs it inside that scope. A uri template in the code reads the workload when the read
runs, so **one generic code serves every peer** — below, two workloads over one store of the dogs' walks, each pulling
its own dog's partition, computing, and pushing the result back to the store:

```mtron_pre
/usr/walk/mama/1 -> [trail=>'ridge',km=>12]
/usr/walk/mama/2 -> [trail=>'lake',km=>5]
/usr/walk/puppy/1 -> [trail=>'ridge',km=>9]
[dog=>'mama',code=>|(*</usr/walk/${*workload/dog}/+/km>.sum().to(</usr/walk/total/${*workload/dog}>))](workload=>_){ !*workload/code.apply() }
[dog=>'puppy',code=>|(*</usr/walk/${*workload/dog}/+/km>.sum().to(</usr/walk/total/${*workload/dog}>))](workload=>_){ !*workload/code.apply() }
*/usr/walk/total/+
```

**Construction is connection.** The config can carry a space. Constructing a space *is* connecting it, so a workload
whose `db` is a `tblespace::T` rec binds the receiving peer's own connection to the same backend when it is evaluated
there — as though the master's connection had crossed the wire:

```mtron
[db=>tblespace::[pattern=></usr/data/#>,host=><mariadb://…>,driver=><org.mariadb.jdbc.Driver>,route=>[</usr/data>=><>]]@/sys/space/data,
 xyz=>'east',
 code=>|(*</usr/data/${*workload/xyz}/+/amount>.sum().to(</usr/data/result/${*workload/xyz}/total>))](workload=>_){ !*workload/code.apply() }
```

A runtime template has one cost: the receiver compiles the code before the template is filled, so its compiler sees
`${*workload/xyz}` where a table name should be, and a database push-down (`SUM` in SQL) cannot fire — the peer reads
the rows and sums in mtron.

### the latch — gather, refuse fails, count through coefficients

The master's `merge()` is the latch: it completes when every peer has replied. Two properties of the gather decide what
"every peer finished" means.

**Equal replies bulk.** A stream is a bag, so three peers that each answer `34` arrive as *one* obj with coefficient
3. Count through coefficients — mtron's `count()` — never by elements:

```mtron_pre
{34,34,34}
{34,34,34}.count()
```

**A fail in the gather is a failed run, not a finished peer** — and `count()` counts a fail like any other obj, so a
latch that only counts is fooled. Check the gather for fails before counting. A peer that **catches** its own failure
answers the recovery instead, and the master never sees the fail:

```mtron_pre
1.plus(a).catch(34)
```

**Not yet**:

* an uncaught failure does not arrive as itself — the master waits out its 2 s future and holds a timeout fail
  (`future get timed out after 2000 ms`) in that peer's place, and the cause is lost;
* the replies are awaited one at a time: each `to(<peer>)` resolves before the next route is sent, so latency is the
  sum of the round trips. The direction is a virtual thread per route,
  `[virtual::[code=>to(ws://s1)],virtual::[code=>to(ws://s2)]]`.

**Where am I.** `*~/network/host` answers the host the current machine runs on — for a result that says where it was
computed:

```mtron
*~/network/host       [-- localhost --]
```

Peers forked on one host share it; telling them apart takes their service authority.

### the worked example — one backend, three satellites

`AbstractMultiServerSingleSpaceTest` puts the pieces together, and a backend opts in with a subclass —
`MariaDBMultiServerSingleSpaceTest` is the first: one MariaDB in a container, and three satellites forked from the
test JVM.

<!-- figure source: assets/tikz/mach-workload.tex — white ink on transparent (the site theme is dark); edit there, re-render with the tikz MCP server, drop the PNG in assets/ -->
<img src="../assets/mach-workload.png" alt="one master, three satellites, one shared MariaDB, drawn top to bottom. The master ships a workload to each satellite (solid arrows w1, w2, w3) as start([w1,w2,w3]).select([to(s1),to(s2),to(s3)]).merge(), and the acknowledgements come back up (dashed) into that merge, the latch. Each satellite is labelled with its binding — xyz=>'east', 'west', 'north' — and exchanges with the MariaDB below it (pull xyz/+, push result/xyz). A dotted arc from the master to MariaDB marks the master's own steps: 1. creates every table, 3. reads result/+. The legend: solid — a workload [db, xyz, code] ships, and evaluating it constructs the satellite's own space (construction is connection); dashed — the acknowledgements, merge() is the latch (no fail, counted through coefficients); dotted — the master's own writes and reads" width="100%" style="margin:0.3em 0 0 0"/>

1. **The master sets up everything.** It starts the backend, mounts its own space, and creates every table — the
   partitions and one result row per partition — before any satellite exists. A satellite boots with nothing but
   `import(/m/tble)`, so it never runs DDL, and its first look at the backend already sees every table it will read.
2. **One workload per satellite**, shipped in the `law_shard` layout: the backend's space, the satellite's partition,
   and the row's code. The latch is the gather — no fail in it, three replies counted through coefficients — and
   provenance is checked rather than assumed: a satellite holds a space only after evaluating its workload.
3. **The master reads the results back** from the backend. Nothing but an acknowledgement ever travelled home.

Every row of the suite is a computation over one partition — counts, sums, filters, a failure caught on the
satellites and one left uncaught — so a new pattern is one CSV line, and a new backend is one subclass.

## appendix — the registered types

Everything above is a subtype of `component::T` at `/m/mach/component` — except `machine::T` itself and the
non-component families, which hang directly off `/m/mach`:

| family   | types (`::T`)                                                                                                                                                                                                                                       |
|----------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| compiler | `compiler`, `parser`, `mtron_parser`, `rewriter`, `fixpoint_rewriter`, `identity_rewriter`, `resolver`, `scoring_resolver`, `firstfind_resolver`, `identity_resolver`, `selector`, `specificity_selector`, `firstfind_selector`, `binder`, `generic_binder`, `typer` |
| process  | `processor`, `monad_processor`, `swarm_processor`                                                                                                                                                                                                   |
| state    | `memory`, `network`, `instset`                                                                                                                                                                                                                      |
| machine  | `machine` (at `/m/mach/machine`, a `rec::T`)                                                                                                                                                                                                        |
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
