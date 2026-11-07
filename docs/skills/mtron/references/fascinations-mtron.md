---
name: fascinations
description: |
  The surprising things mtron does, one section each: the phenomenon, a block the docs pipeline runs, and the few
  rules it falls out of. Sections so far: shipping a socket (a built obj travels as its data, so a ws_client sent to
  another machine makes that machine dial).
  TRIGGER: When showing what makes mtron different from other languages; when looking for a striking, runnable
  example; when explaining why a shipped client dials from where it lands, or why a built obj's wire form is only its
  data.
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

```mtron_pre
[-- a space for this page, with the mimeq q-proc so a read can choose its rendering --]
memspace::[pattern=>/fascination/#, q=>[mimeq::[=>]]]@/sys/space/fascination
[-- a built websocket rec: its constructor derives status, send, close and the overrides in Java --]
ws_handler::[host=><ws://localhost:8555/mtron>]@/fascination/socket
[-- the derived status, computed on read from a socket that is not open --]
*</fascination/socket/status>
[-- the wire form: what a machine sends when it ships this obj --]
*</fascination/socket?mimeq=application/x-mtron>
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
