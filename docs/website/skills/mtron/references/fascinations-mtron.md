---
name: fascinations
description: |
  The surprising things mtron does, one section each: the phenomenon, a block the docs pipeline runs, and the few
  rules it falls out of. Sections so far: shipping a socket (a built obj travels as its data, so a ws_client sent to
  another machine makes that machine dial); rewriting code by pattern (a program is a value: code patterns find runs
  of instructions, update rewrites them, and as(lst::T) edits a program as a list before it runs).
  TRIGGER: When showing what makes mtron different from other languages; when looking for a striking, runnable
  example; when explaining why a shipped client dials from where it lands, or why a built obj's wire form is only its
  data; when showing code as data, pattern matching on code, or a rewrite rule written in mtron.
---

# fascinations — the surprising things mtron does

Each section is one thing mtron does that most languages cannot, or can only with a framework: the phenomenon, a
`mtron_pre` block the docs pipeline runs on every build, and the rules it falls out of. None of them was built as a
feature. Each is what rules that were already true imply together.

## shipping a socket

A `ws_client` is about as unshippable as an obj gets: an open socket, a supervisor that redials it, a `status` derived
from that socket on every read, and a mini-instruction-set of `eval`/`from`/`to` overrides whose bodies are Java. Yet
one machine can send a `ws_client` to another, and the receiver ends up connected to whatever the client names. What
crosses the wire is an address.

The block builds a websocket rec here, with no far side (`ws_handler` is `ws_client` without the dial), and reads it
twice:

```mtron
mtron> [-- a space for this page, with the mimeq q-proc so a read can choose its rendering --]
mtron> memspace::[pattern=>/fascination/#, q=>[mimeq::[=>]]]@/sys/space/fascination
mtron> [-- a built websocket rec: its constructor derives status, send, close and the overrides in Java --]
mtron> ws_handler::[host=><ws://localhost:8555/mtron>]@/fascination/socket
==>ws_handler::[host=>ws://localhost:8555/mtron,
                send=>λ(){<j>},
                send_recv=>λ(){<j>},
                inst=>[
                 /m/inst/eval=>eval?A{*}<=code(uri::T){<j>},
                 /m/inst/from=>*uri::T,
                 /m/inst/to=>to?A{?}<=A{?}(uri::T){<j>}],
                status=>!λstatus?bool<=(){<j>},
                close=>λ(){<j>}]@/fascination/socket
mtron> [-- the derived status, computed on read from a socket that is not open --]
mtron> *</fascination/socket/status>
==>false
mtron> [-- the wire form: what a machine sends when it ships this obj --]
mtron> *</fascination/socket?mimeq=application/x-mtron>
==>'ws_handler::[host=>ws://localhost:8555/mtron]@/fascination/socket'
```
The first result is the obj as this machine holds it: the `host` it was built from, plus five fields its constructor
derived (`send`, `send_recv`, the `inst` overrides, `status`, `close`), their Java bodies marked `{<j>}`. `status` is
computed from the socket on every read, so here it is `false`. The wire form is the data alone: the serializer leaves
out every field whose value contains a Java function, which is exactly what the constructor derived, and keeps the
rest. The machine that reads the text *constructs* it, because reading a typed literal runs its type's constructor.
The constructor then runs again on that machine: it derives a status from that machine's socket, re-aligns `host` to
that machine's view, dials, and indexes the far side into that machine's network. The connection is not moved; it is
rebuilt where it lands.

Four rules, each true before this worked, make it work:

- **A connection's life is its client's.** A client connects on construction and disconnects on destruction, and a
  read never dials.
- **A parse constructs.** `ws_client::[host=>…]` is built wherever it is read (`Obj.Helper.construct`), on any
  machine that has the type, including the sender if it parsed the literal itself.
- **A constructor fills only what is absent.** Each derived field is guarded by `!containsKey`, so a caller can bring
  its own `to()`. A field left off the wire is a field the receiver derives.
- **Names travel, bodies bind where they land.** `ObjmtronSerializer.writeRec` drops the fields of a built obj that
  `Call.containsJavaFunction()` flags: a Java lambda in an instruction, or in any instruction or code among its
  arguments. Shipped code works the same way: a step travels as its tid, and the receiver binds the body.

The same move builds topologies. `TreeShardTransportTest` sends peer 1 one client per child; peer 1 dials peers 2 and
3, and a sharded fold handed to peer 1 then plans one branch across both.

Two consequences come with it:

- Reading the text is enough to dial. Whoever can send a machine mtron can make it open a connection.
- A network keeps no two equivalent connections, so a shipped client replaces the one the receiver already holds for
  that host.

## rewriting code by pattern

In most languages a program is text to parse or an opaque function to call. In mtron a program is a value: held in a
poly it stays unevaluated, it is tested like data, it is rewritten by the same `update` (`>>=`) that rewrites a str or a
rec, and it runs again afterwards. The pattern a rewrite looks for is code too. Its instruction names are uri patterns,
its arguments are types, and the code tests the pattern exactly as a value tests a type.

The block rewrites four programs, then takes a fifth apart as a list, edits it, and runs it:

```mtron
mtron> [-- a name is a uri pattern, so one rule rewrites every plus and every mult --]
mtron> [plus(1).mult(2).plus(3)]==[update([</m/inst/{plus,mult}>(int::T)=>id()])]
==>[id().id().id()]
mtron> [-- an argument is a type, its coefficient included: a bag of ints is an int{*}, never an int --]
mtron> [start({1,2}).plus(1)]==[update([start(int{*}::T)=>start(0)])]
==>[start(0).plus(1)]
mtron> [-- an argument rewritten in place, as an update rewrites a lst --]
mtron> [plus(5).mult(2)]==[update([plus(int::T)=>[args=>[+10]]])]
==>[plus(15).mult(2)]
mtron> [-- named groups and a template: the monoid law of plus, written as data --]
mtron> [plus(2).plus(3).mult(4)]==[update([plus(int::T@a).plus(int::T@b)=>plus(${*a.plus(*b)})])]
==>[plus(5).mult(4)]
mtron> [-- the same program as a list of instructions: replace the seed, keep the rest, rebuild the code, run it --]
mtron> [start({1,2,3}).plus(10).order(mult(-1))]==[as(lst::T)]==[==[|start({-9,-8,-7}),#::T,#::T]]==[as(code::T)]>-
==>[3,2,1]
```
The first four answers are programs, rewritten and still unevaluated. The fifth is numbers. The program was taken apart
as a `lst::T` of instructions, its seed replaced (`|start(...)`, the bar holding the replacement as data) and its other
instructions kept (`#::T`), and `as(code::T)` put it back together. The merge `>-` then ran it: the new seed plus 10,
ordered by its negation.

Five rules, each true before this worked, make it work:

- **A poly holds code without running it.** Only `at()` fires an initial or an auto, and `update` walks its rules with
  `elements()`, so a pattern such as `start(int{*}::T)` reaches the matcher as code.
- **An instruction is tested like a value.** `Inst.test` matches a name as a uri (`{plus,mult}`, `#`) and each argument
  with `test`. So a type is a hole (`int::T`), a coefficient is a shape (`int{*}`), a predicate is a guard (`?>0`), and an
  instruction pattern nests (`#(mult(_))`).
- **`update` has one philosophy.** A str's keys are regexes that select sections; a code's are patterns that select runs
  of instructions. The value says what each becomes, and a key of `args` updates in place, as `>>=` updates a lst.
- **A name is an argument uri.** `@a` binds what its pattern element matched, in a frame on the running thread's
  argument stack that is pushed for the change and popped after. A template parameter (`${...}`, a str's own template
  syntax, which already parses as a uri in an argument) reads it as `*a`.
- **Iterating code runs it.** `as(lst::T)` and `as(code::T)` are two views of one program, and `>-` iterates the code,
  which evaluates it.

Two consequences come with it:

- A rewrite rule is data. A rec of `pattern => change` can be stored, shipped, and documented. The compiler's Java
  rewrites (the move extractor, the law folds) are this shape compiled.
- A name on a typed hole is also a type of the machine: `int::T@a` defines `a::T` in the machine's root frame. Threads
  that rewrite at once each work in a machine of their own (`push`, then `pop`), where those types live and die.