# The peer as a mini-instset — capabilities as instruction overrides

*Status: design record, 2026-10-24. Records a working session that started as "funnel all network connections through
`Network.index()`" and ended with a single realization: a peer is not a connection-with-a-transport, it is a **partial
instruction set** — existing instructions (`from`, `to`, `eval`, tools, …) redefined by the peer with custom bodies.
Every "exists" claim below carries a `file:line`; the "should exist" half is the target, not yet implemented.*

---

## 0. Thesis, in one paragraph

A peer's *capability* is not a special object. It is a set of **instruction overrides**: the peer redefines existing
mtron instructions (`from`, `to`, `eval`, and any tool it exposes) with bodies that perform the wire protocol. The
current `transport` field is the leftover of a bad fusion — it was *both* the capability ("ship a message") *and* the
liveness (it closed over the socket). The fix splits those two axes: **capability** becomes a partial instset
(`inst => { tid => typed body }`), and **liveness** stays on the client (`close` / `connected` / `hungUp` / `redial`).
Capability is a pure, typed function you *apply*; liveness is a stateful connection you *hold and release*. Once the
split is made, "is this peer mtron-capable?" stops being a flag and becomes a type query — "does its instset contain
an instruction whose `dom` admits `code::T`?" — and `law_shard`'s already-emitted `to(<peer>)` becomes the first
consumer of a general, authority-keyed instruction dispatch.

---

## 1. What exists today

### 1.1 The Network component: two maps, two laws

`Network` ([`Network.java:51-53`](../../src/main/java/studio/phaseshift/metatron/isa/mach/type/Network.java#L51-L53))
carries two maps, deliberately not one:

- **roster** — what the system *knows*: connectionless endpoints, read at `/network/roster` so every machine reads the
  one listing. *Knowledge is shared.*
- **peer** — what *this* machine has dialed, at `~/network/peer`. *Reach is not shared.*

`NetworkUnion` states the two laws explicitly ([`NetworkUnion.java:22-32`](../../src/main/java/studio/phaseshift/metatron/isa/mach/type/network/NetworkUnion.java#L22-L32)):

1. **knowledge is shared** — every level reads the one roster at the root address;
2. **reach is not** — a connection belongs to exactly the machine that dialed it, so `pop()` retracts only its own
   dials and a sibling never sees them.

### 1.2 `peer::T` — the peer rec

Defined in [`machInstSet.java:426-460`](../../src/main/java/studio/phaseshift/metatron/isa/mach/machInstSet.java#L426-L460),
the `peer::T` type is:

| field       | type        | meaning                                                                 |
|-------------|-------------|-------------------------------------------------------------------------|
| `authority` | `uri::T`    | the peer's service uri, `scheme://host:port` — the connection key        |
| `host`      | `uri::T`    | where to dial it from here, route included (`ws://host:port/mtron`)       |
| `handler`   | `uri::T`    | the protocol surface (`/m/web/ws/mtron_ws`, `/m/web/http/mtron_http`, …) |
| `name`      | `str::T`    | the machine's name in a distributed expression                          |
| `status`    | `rec::T`    | the computed health report                                              |
| `transport` | `inst::T`   | **the reach: an inst that ships a message and answers with the result**  |
| `client`    | (any)       | the protocol client — held so `disconnect` can hang up                   |

### 1.3 The transport is a single, untyped, mtron-oriented arrow

The `transport` field is declared as bare `INST_TYPE` — no `dom`, no `rng` ([`machInstSet.java:455`](../../src/main/java/studio/phaseshift/metatron/isa/mach/machInstSet.java#L455)). So the type system sees "an inst," full stop, and the capability is *unreadable* from the type.

But its *semantics* is unambiguously "evaluate mtron remotely":

- `id_client`'s `send_recv` is literally `inst.arg(0).apply(lhs)` — *take the mtron message and evaluate it*
  ([`machInstSet.java:407`](../../src/main/java/studio/phaseshift/metatron/isa/mach/machInstSet.java#L407)).
- `Network.read(vid)` builds `from_(vid.localize())` and applies the transport
  ([`Network.java:446-449`](../../src/main/java/studio/phaseshift/metatron/isa/mach/type/Network.java#L446-L449)).
- `Network.write(vid, obj)` builds `start_(obj).to_(vid.localize())` and applies it
  ([`Network.java:470-475`](../../src/main/java/studio/phaseshift/metatron/isa/mach/type/Network.java#L470-L475)).

So today there is **one** arrow, effectively `inst?dom=code&rng=obj` — "evaluate whatever mtron program I send you."
`read` and `write` are not separate capabilities; they are two mtron *programs* (`from_`, `start_.to_`) smuggled through
the same eval arrow. `Helper.asTransport` ([`Network.java:742-760`](../../src/main/java/studio/phaseshift/metatron/isa/mach/type/Network.java#L742-L760)) adapts a
protocol session's `send_recv` so the message rides as the arg seat with `?block` — but it is still one untyped inst.

The intended elegance is real but incomplete: the *value* of the peer map was always meant to be transport-agnostic —
`BasicNetwork` says "swapping ws for http, mqtt, a container endpoint or a shared space is a roster change, not a code
change" ([`BasicNetwork.java:44-46`](../../src/main/java/studio/phaseshift/metatron/isa/mach/type/network/BasicNetwork.java#L44-L46)) — but the *contract* was pinned to "the far side evaluates mtron."

### 1.4 Indexing is already a background behavior

A protocol client's constructor indexes itself after a successful construction — `Network.Helper.index(client)`
([`Network.java:581-589`](../../src/main/java/studio/phaseshift/metatron/isa/mach/type/Network.java#L581-L589)). `index` is ensure-peered, idempotent, and
never fails the construction. Both ws and mcp already do it: `wsSpace.java:132`, `mcpClient.java:107`. The peer's
`client` is retained so `disconnect` can hang it up, and a `Link` client (`connected` / `hungUp` / `redial`,
[`Network.java:507-525`](../../src/main/java/studio/phaseshift/metatron/isa/mach/type/Network.java#L507-L525)) is supervised — a dropped connection is redialed
in place, never rebuilt.

### 1.5 `law_shard` already ships through `to(<peer>)`

The distributed rewrite is already instruction-shaped. Applying `law_shard` to a reduction:

```
[start({1,2,3,4,5,6}).plus(1).sum()]==[as(cat:path::T)]==[lower(>>law_shard)]
```

lowers to:

```
start([start({1,2,3,4,5,6}).plus(1).sum?hop=0()]).select([to(<ws://booger.local:8555>)]).merge().sum()
```

Shipping a shard is an *ordinary instruction call* — `to(<peer>)` — composed inside `select(...).merge().sum()`. The
rewrite already composes capabilities as instructions. What it does **not** yet do is pull `to` *from the peer*: today
`to` is a global instruction whose body reaches out through `Network.write` to whatever transport the peer map holds.

---

## 2. The realization

### 2.1 Not every peer speaks mtron — and that's the point

The "protocol problem" is not "how do we ship mtron" (that already works, see §1.5). It is that peers are *not uniformly
mtron*:

- `*<http://markorodriguez>` is a standard HTTP GET — the far side returns HTML, not an evaluated mtron value.
- an MCP server (e.g. codegraph) exposes *tools* (`codegraph_explore`), not an `eval` tool.

The reconciliation is: **the network never learns a protocol** (it already says so,
[`Network.java:489-491`](../../src/main/java/studio/phaseshift/metatron/isa/mach/type/Network.java#L489-L491)). The peer index holds connections keyed by
authority; each peer's *handler* names its surface; and the mtron↔protocol translation lives in the peer's instruction
bodies, not in the Network.

### 2.2 Capability is a typed arrow — then, more precisely, an instruction override

Two steps got us here.

**Step one: capability = typed arrow.** A capability is an `inst` with a `dom` and a `rng`:

```
eval  : inst?dom=code     &rng=obj      // evaluate code, return obj
read  : inst?dom=uri      &rng=obj      // read the obj at a uri
write : inst?dom=obj×uri  &rng=obj      // write obj to a uri
```

`code` is then just *one* `dom` among many. "mtron-capable" is the query "has an arrow whose `dom` admits `code::T`",
not a flag. The `rng` matters as much as the `dom`: `inst?html<=uri` vs `inst?obj<=uri` is the difference between "I
hand back html" and "I hand back something" — a consumer can type the result *without knowing the protocol*.

> **Correction (2026-10-24, with the convention ruling).** In mtron's own signatures the payload is an *argument*
> type, not the `dom` — the `dom` types the lhs. The natives are `eval(code)` and `from(uri)` (each `dom=obj?`) and
> `obj.to(uri)` (`dom=A?`: the obj is the lhs). So "mtron-capable" reads "has an `eval` override", whose argument
> admits code, and an override takes exactly its native's signature (§3.2).

**Step two: the arrow is an instruction body.** The arrow view is the *type-side*; the override view is the *body-side*
of the same object. A peer does not wrap `from()`/`to()` — it **redefines** them:

- `from(x)` over a ws peer → body ships `from_(x)`, the far side evaluates it;
- `from(x)` over an http peer → body issues HTTP GET and reifies the body;
- `from(x)` over the loop peer → body is `x.apply(local)` (the identity).

The capability is *the peer's custom implementation of an instruction*. Any instruction can be overridden — `from`,
`to`, `eval`, `codegraph_explore`, `plus`, anything. The capability space is open because the instruction space is open.

### 2.3 The subsumption lattice — and its one false edge

Capabilities are ordered by `dom`/`rng` subtyping, which is already the tid algebra. "EVAL subsumes READ" is just "`code::T`
is a supertype of `uri::T`-as-a-program," witnessed by the coercion `from_(uri)`.

```
            EVAL  (code → obj)
            /    \
       WRITE      READ
     (obj→uri)  (uri→obj)
```

- EVAL ⇒ READ, EVAL ⇒ WRITE — true: `from_(x)` and `start_(y).to_(x)` are code, so a peer that evaluates code can do both.
- WRITE ⇒ READ — **false**: a telemetry sink writes and can't read; a sensor reads and can't write.

So it is a **lattice**, not a chain — two axes (dom-generality and effect) that don't linearize. Below `READ`, `rng`
precision branches (`html ⊑ obj`). This lattice is not a separate thing to maintain: it is the rewrite's **lowering
rules** — a peer that overrides `eval` but not `from` gets `from` by lowering `from(<peer>, x)` to
`eval(<peer>, from_(x))`.

### 2.4 The split: capability vs liveness

The whole thread collapses into one boundary:

| axis        | what it is                          | where it lives                       |
|-------------|-------------------------------------|--------------------------------------|
| **capability** | pure, typed, declarative — the instruction bodies | `inst => { tid => typed body }` |
| **liveness**   | stateful, mutable, supervised — the socket + `Link` | `client` (`close`/`connected`/`hungUp`/`redial`) |

`transport` was both at once. Splitting them is what makes the peer map "true" under supervision (redial the socket
without disturbing the capability layer) and under `pop` (hang up the connection, not the instruction set).

---

## 3. What should now exist

### 3.1 `peer::T`

```
peer::T = {
  authority, host, name, status,        // identity, addressing, health
  client => {                           // the connection itself (or link):
    close, connected, hungUp, redial,   //   liveness: the live socket + Link
    inst => { inst_tid => override }    //   capability: the mini-instset, read as peer.client.inst
  }
}
```

- **`transport` is gone.** It became the `inst` map. There is no single "ship this" inst; `from`/`to`/`eval`/`…` are
  each their own typed body.
- **`handler` shrinks to a dial-time hint.** Its *dialer* role (which type/inst constructs this client) belongs to
  `connect`/`dial`; its *surface* role (what protocol the peer speaks) is now redundant with the `inst` map — a peer
  whose `inst` is `{from => GET}` *is* a web reader.
- **`client` stays, demoted to liveness.** Its `send_recv` does not disappear — it *moves* into the bodies inside
  `inst` (a ws `from` body is `send_recv` carrying `from_`; a ws `eval` body is `send_recv` carrying code). What
  remains on `client` is the live socket plus `close`/`connected`/`hungUp`/`redial` — with `inst` beside them: the
  client IS the connection, and `inst` is the instructions you talk to it with, never copied onto the peer.

### 3.2 `client::T`

The client is no longer a "protocol client with `send`/`send_recv`"; it is a **lifecycle handle**:

```
client::T = {
  host,                      // where it dialed, kept for the key/redial
  close     : inst,          // hang up (the universal teardown)
  connected : bool,          // is it up right now (Link)
  hungUp    : bool,          // did its owner close it (Link)
  redial    : inst,          // re-establish in place (Link)
}
```

The `id_client` (loop) becomes exactly the minimal case — a single maximal override and a no-op close. The concrete
transformation, from the session:

```java
// BEFORE — a generic send_recv:
uri(SEND_RECV), instLambda((lhs, inst) -> inst.arg(0).apply(lhs))

// AFTER — an instruction override, keyed by its tid: the native eval with its own f()
uri(INST), rec(
    uri(EVAL_INST_TID),
    Network.Helper.override(EVAL_INST_TID, (lhs, inst) -> inst.arg(0).apply(lhs)))
```

The override is **the native instruction with another body** — eval's own `tid` (its `dom`/`rng`) and argument types,
read from instruction memory, with only `f()` replaced — so the signature survives by construction and the subsumption
lattice can read it. Dispatch never repacks (ruling, 2026-10-24): the network builds the call exactly as a program
issues it — `from(<uri>)`, `obj.to(<uri>)`, `eval?block(code)` — binds it to the native definition as resolution
would, and swaps `f()` (`Network.Helper.dispatch`). An inst of any other signature is not accepted as an override
(`Network.Helper.accepts`, by `Inst.test`). Overriding only `eval` is correct, not an omission: it is "declare the maximum,
infer the rest" — the loop peer's one maximal capability is "evaluate code (as identity)," and `from`/`to` derive from it.

### 3.3 The `inst` map is a partial instset, open over tids

| client                | `inst` map                                                                   |
|-----------------------|------------------------------------------------------------------------------|
| `id_client` (loop)    | `{ eval, from, to }` — each the identity of the wire body (2026-10-24; was `{ eval => identity }`) |
| `ws_client` (mtron)   | `{ eval => ship-code, from => ship-from_, to => ship-to_ }` — all three declared (ruling 2026-10-24: no `to` derived from `eval`) |
| `http_client` (web)   | `{ from => GET, to => POST }` — no `eval`: a POST writes an obj and evaluates nothing, so it is no shard target (2026-10-24) |
| `mcpClient` (codegraph)| `{ codegraph_explore => tool-call, codegraph_search => tool-call, … }`      |

The keys are instruction tids, the values are typed insts, and the set is whatever the peer natively does. The
`isaPredicate` should be open (`rec` of `inst_tid => inst`), not pinned to a single key.

### 3.4 Dispatch is authority-keyed instruction resolution

`Network.read`/`write`/`transportOf`/`asTransport` collapse into one rule:

> **Resolve `instr(<authority>)` into that peer's `inst` map and apply it; fall back to the local/imported definition
> when the peer does not override it.**

> **Done 2026-10-24.** `transportOf` and `asTransport` are deleted and `peer::T` carries no `transport`: read, write
> and submit dispatch through the client's own `from`/`to`/`eval`, else stay local; every client and server answers a
> derived `status`; a read never dials ([`peer_instset_plan.md`](peer_instset_plan.md) §5).

Two peers can both override `from`, and they must not collide — so resolution is `(instruction name, authority in the
argument)`, the natural generalization of "the peer map is keyed by authority." The lowering rules supply everything the
peer did not override directly:

```
from(<peer>, x)  →  eval(<peer>, from_(x))        // when the peer has eval but not from
```

`law_shard`'s `to(<peer>)` then becomes "pull `to` from booger.local" — the first already-working consumer of the
general dispatch, and no longer special.

> **Rulings (2026-10-24).** Resolution binds at **compile time**: `Inst.resolve` → `Network.Helper.atPeer` gives a
> resolved `from`/`to` whose address names a dialed peer that peer's body, inside the network's q-proc layer
> (`Network.AtPeer`), so the compiled code shows what runs where. And a protocol client **declares** the instructions
> it serves rather than having them derived — `ws_client` declares `eval`, `from` and `to`, and so does the loop
> `id_client` (each the identity of the wire body, so the loop stays the family's identity element). The
> `from → eval` / `to → eval` lowering above survives only as the run-time ladder's rung for a client that declares
> `eval` alone. Placement reads the same declarations: a shard target must be online and serve its placement's
> `inst` (`to` and `eval`, for every distributing shape) — `Places.serves`. And the compiled code has the bindings:
> law_shard binds each route at plan time, so its emitted `select` holds the target's own `to`.

---

## 4. Why capability must be instruction-shaped

The rewrite composes capabilities as instructions:

```
start(shards).select([to(<peer>)]).merge().sum()
```

`to` has to be an ordinary instruction to sit inside `select`, be mapped over a monad, be merged, be summed. A
`transport`-as-special-call cannot compose that way. **The rewrite engine is what forces capability to be instruction
shaped** — this is the deep reason the override model is not merely cleaner but *necessary* for distribution.

A peer with `{ eval => … }` gets shards via `eval(<peer>)`; a peer with `{ from => GET }` serves reads via
`from(<peer>)`; a codegraph peer serves `codegraph_explore(<peer>)`. The rewrite emits `instr(<peer>)`; the resolver
picks the peer's `instr`; the peer declares only its maximal overrides and the lattice is derived by composition. One
dispatch rule, one index, and "capability" disappears into "which instructions does this peer contribute."

---

## 5. Edges and open questions

1. **The lowering rules.** The `from → eval` / `to → eval` derivations must be pinned as rewrite rules (the residual
   "meat" that used to live in `transport` is now distributed into the override bodies). Where those rules live — the
   resolver, the rewrite planner, or a per-peer declaration — is the one thing left to place.
2. **`handler` after the split.** Confirm it survives only as a dial-time hint and is not stored on the peer once the
   surface is the `inst` map.
3. **`close` as instruction vs field.** The session's `id_client` keeps `CLOSE` as a sibling instruction (not inside
   `inst`), because teardown is liveness, not capability. The `Link` contract (`connected`/`hungUp`/`redial`) is
   fundamentally a Java lifecycle contract polled by `NetworkSupervisor` — it stays on the client, not in the map.
4. **Coefficient of `inst`.** `inst => inst{*}::T` is the general type; the runtime coefficient is the number of
   *maximal, incomparable* overrides (1 for an eval-only or web peer, N for a tool-bearing peer). mtron's `stream()`
   makes one-vs-many invisible to the reader.
5. **Reach inheritance** (pre-existing, unchanged). Whether a child frame reuses the parent's *live* overrides or
   re-dials a *known* authority is the same open question the `NetworkUnion` two-law comment already flags — the
   capability/liveness split does not answer it, but it makes the answer clean: sharing liveness is the question,
   sharing capability is a roster read.
