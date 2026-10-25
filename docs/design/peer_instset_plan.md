# peer-as-mini-instset — implementation plan & hand-off

*Companion to `peer_instset.md`. That doc records the design; this one records the work. Foundation is DONE and
green; the remaining phases are scoped so a fresh agent can pick any one up without the originating conversation.*

---

## 0. Where the foundation landed (DONE, verified 2026-10-24)

The model's *shape* and *dispatch primitive* are in place, additively, without breaking the read/write path:

| piece | file | what changed |
|---|---|---|
| client `inst` | `machInstSet.java` | the mini-instset `{ inst_tid => override }` lives on the **client** — the connection — and is read through `peer.client.inst`; `peer::T` carries no `inst` field (an earlier draft copied it onto the peer) |
| `id_client` | `machInstSet.java` | migrated off `send_recv`: now declares `inst => { eval => identity }` (+ `close`). The canonical example |
| `Helper.asTransport` | `Network.java` | derives the legacy transport from `inst.eval` (before falling back to `send_recv`), so read/write keep working unchanged |
| `Network.index` | `Network.java` | holds the client on the minted peer — no copy of `inst` — and accepts an `inst`-only client (no eval, no transport) |
| `Network.overrideOf` | `Network.java` | **the dispatch primitive**: `overrideOf(authority, instrTid) -> peer.inst[tid] \| noobj` |
| test | `NetworkConnectTest.java` | `testOverrideOfDispatchesToThePeersOwnBodies` — loop peer declares `eval`, not `from` |

Verified: `NetworkConnectTest` (7), `MachineAsyncFrameTest` (1), `MachineIsolationScriptTest` (10),
`MachinePerspectiveTest` (3) — **21/21 green**, including the pre-existing loop read/write tests, which now flow
through the `eval`-derived transport.

The loop peer is the proof end-to-end: it declares `{ eval => identity }`, `asTransport` derives the transport from
`eval`, `read(loop://self/x)` still answers the local read, and `overrideOf(loop://self, eval)` returns the body.

---

## 1. Phase A — route read/write/eval through `overrideOf`

> **Status: partial — DONE and green.** The **`submit` eval verb** and the **override calling convention** are in
> (`Network.submit(authority, code)` + `Helper.wrapTransport` made public with the convention documented). The
> `read`/`write` **from→eval / to→eval ladder is folded into Phase B**, because its `from`/`to` branch is only
> exercised once a real client declares a native `from`/`to` override — you can't test that ladder against the loop
> peer, which declares only `eval`.

**Goal:** make the data path actually *dispatch* to the peer's overrides, with the from→eval / to→eval lowering,
instead of always going through the derived transport. This is the semantic payoff; today it's still the legacy
transport carrying everything.

**Files:** `Network.java` (`read`, `write`, and a new `submit`/`eval` path).

**Steps:**
1. `read(vid)`: try `overrideOf(authority, FROM_INST_TID)` → apply to `vid.localize()`; else
   `overrideOf(authority, EVAL_INST_TID)` → apply the code `from_(localize)` (the lowering); else fall back to the
   legacy `transport` (what it does today).
2. `write(vid, obj)`: same ladder for `TO_INST_TID` → `EVAL_INST_TID` (`start_(obj).to_(localize)`) → legacy.
3. Add an **eval/submit** verb: `submit(authority, code) = overrideOf(authority, EVAL_INST_TID).apply(code)` (or the
   transport when the peer predates `inst`). This is what `law_shard` will call in Phase D.
4. **The one real mechanic to nail:** how an override body is *applied*. The `eval` override is
   `instLambda((lhs, inst) -> inst.arg(0).apply(lhs))`; the current transport wraps `send_recv` so the message rides
   as arg(0) with `?block` (`wrapTransport`). Decide and document the override's own calling convention — most likely
   the *same* wrap, so `overrideOf(...eval).apply(code)` and the transport stay interchangeable. Write the convention
   down next to `wrapTransport`.

   > **Settled — ruling, 2026-10-24 (supersedes the `send_recv`-shaped `arg(0)` wrap above).** An override has **the
   > same signature as the instruction it replaces** (within the type system's acceptance), and dispatch **never
   > repacks arguments: `Inst.f()` is the one thing swapped.** In code (`Network.Helper`):
   >
   > | piece | rule |
   > |---|---|
   > | `override(tid, body)` | declare: the native definition (read from instruction memory) with `body` as its `f()` — same signature by construction |
   > | `accepts(tid, inst)` | validate: the native definition accepts the inst as itself (`Inst.test`: base, dom, rng, arg types), `f` aside; `overrideOf` answers only accepted overrides |
   > | `dispatch(override, user, lhs)` | call: `user` is the instruction as a program issues it, bound to its native definition by the machine's `Binder` (generics, args, q-procs), then `.f(override.f())` |
   > | `evaluate(eval, code)` | `eval?block(code)` — the only call that blocks: its code is a payload the peer evaluates. `from`/`to` carry an address, which computes to itself |
   >
   > The calls are the natives' own shapes: `from(<uri>)` (the full address in `arg(0)`), `obj.to(<uri>)` (the obj
   > is the **lhs**, the address `arg(0)`), `eval?block(code)` (the code in `arg(0)`, raw). `wrapTransport` survives
   > only as the legacy `send_recv` adapter. Proven by `NetworkConnectTest`
   > (`testAnOverrideIsTheNativeInstructionWithItsOwnF`, `testDispatchIsTheNativeCallWithTheOverridesF`,
   > `testSubmitShipsTheCodeRawThroughEval`, `testAnInstOfAnotherSignatureIsNotAnOverride`).

**Verify:** loop peer read/write still green; add a test that a peer declaring `from` (not `eval`) answers reads via
`from` directly, and one that a peer declaring only `eval` answers reads via the `eval` lowering.

---

## 2. Phase B — migrate the protocol clients to declare `inst`

> **Status: 2 of N migrations done as the two templates.**
> 1. **tool template — `mcpClient`** (`isa/llm/type/mcp/mcpClient.java`): each discovered MCP tool becomes an
>    override, keyed by its instruction tid `LLM_TOOL_TID.extend(toolName)`, with the tool-call body as the value.
>    The `inst` rec is set **before** `Network.Helper.index(this)` so the peer indexes the overrides, not an empty set.
> 2. **resource+eval template — `ws`** (`isa/web/space/ws/WebSocketRec.java`): the client's `send_recv` body is its
>    `eval` override — `inst => { eval => sendRecv }`. `asTransport` then derives the transport from `inst.eval`.
>
> The remaining clients (`http` handlers, `Network.accept`, `PeerSpawn`) follow one of these two shapes, plus the
> read/write from→eval/to→eval ladder (folded in from Phase A).
>
> **Ladder status: DONE and green (both directions).** `read` dispatches `from → eval → transport`; `write`
> dispatches `to → eval → transport` (the `to` override is the native `to`: the obj is the lhs, the dest uri arg(0) —
> see Phase A step 4's ruling). `index` accepts
> an `inst`-only client (no `eval`/transport), so a read-only or write-only peer is reachable through its single
> override. Proven by `NetworkConnectTest.testReadDispatchesToANativeFromOverride` (loop reader) and
> `testWriteDispatchesToANativeToOverride` (loop sink).
>
> **`send_recv` migration: DONE (2026-10-24, Marko: "migrate all the send_recv clients over to providing their own
> custom to() implementation").** `Network.Helper.asTransport` no longer reaches a peer through `send_recv` (and the
> legacy `wrapTransport` adapter is gone): a client reaches the network through its mini-instset alone, and the
> transport is derived from its `eval`. `WebSocketRec` declares `eval`/`from`/`to` unless its spec brings its own
> `inst` (a custom `to()`; `ws_client::T`'s predicate names the field); `send_recv` survives only as the ws client's
> raw wire instruction. The loop `id_client` declares the same three. `AcceptedPeerShardTest`'s fixtures now mint the
> twin and the dialed peer with mini-instsets.
>
> **The http client: `{ from => GET, to => POST }`** — the theory's test. `obj.to(<http://…>)` POSTs the obj as
> httpSpace's directWriter sends it (json unless `?mimeq`) and answers the native `to`'s answer, the obj written: the
> type system holds the override to `to`'s range, so the server's reply is not the answer (a non-2xx is a fail). A
> POST writes and evaluates nothing, so the client serves no `eval` and is never a shard target — the reason
> distributing shapes require `{to, eval}` (Phase D ruling 3). Proven by
> `NetworkConnectTest.testHttpClientPostsTheObjItWrites`.

**Goal:** every client declares its capabilities as an `inst` map; `send_recv` is removed. Until a client is
migrated, it still works through the legacy `send_recv` path (the foundation kept both).

**Files / clients:**
- `isa/web/space/ws/wsSpace.java` + `WebSocketRecClient.java` — declare `inst => { eval => ship-code, from => ship-from_, to => ship-to_ }` (or just `eval`, letting lowering derive from/to). The `send_recv` body moves into `eval`.
- `isa/llm/type/mcp/mcpClient.java` — declare `inst => { <tool-tid> => tool-call body, … }`: one override per MCP tool. This is where "capability = tool" becomes literal; the tool list is already discovered at connect time.
- `isa/web/space/http/handler/*` (`web_httpHandler`, `mtron_httpHandler`, `mcp_httpHandler`) — `web_http` declares `{ from => GET }`; `mtron_http` declares `{ eval => POST-code }`; `mcp_http` declares its tools.
- `Network.accept(remote, session)` (`Network.java`) — inbound connections mint their `inst` from the session, exactly as `index` does outbound.
- `isa/mach/type/network/PeerSpawn.java` — the spawned peer.

**Watch:** `Helper.claimOf`/`Helper.index` and the `Link` supervision must keep working — `client` (liveness) stays;
only the capability moves to `inst`.

**Verify:** the ws/mcp integration suites (`NetworkWireScriptTest`, `MachineSwarmMigrationTest`, mcp client tests)
stay green with `send_recv` deleted and `inst` carrying the bodies.

---

## 3. Phase C — typed override bodies (dom/rng)

> **Status: the typing is DONE, by the convention ruling (Phase A step 4).** Every override is declared on its
> native's signature (`Network.Helper.override`) and validated against it (`Network.Helper.accepts`), so its tid
> carries the native's `dom`/`rng` and its args the native's argument types. Note the coupling this resolves: in
> mtron the `dom` types the **lhs** and the payload is an **argument** type (`eval(code)`, `from(uri)`,
> `obj.to(uri)`), so the lattice reads from argument signatures and `rng`, not from a `dom=code`/`dom=uri`
> vocabulary. What remains is the reading itself: the lowering rules (`from → eval`, `to → eval`) taken from those
> signatures rather than hard-coded in the ladder.

**Goal:** each override carries its own `dom`/`rng`, so the subsumption lattice is readable from the tid, not only
from the key.

**Steps:** replace the bare `instLambda((lhs, inst) -> …)` override bodies with the typed form
`instLambda(tid, dom, body)` (or `instLambda(dom, rng, body)` — check the `MInst.instLambda` overloads). E.g.
`eval` = `instLambda(EVAL_INST_TID, lst(CODE_TID), body)`, `from` = `instLambda(FROM_INST_TID, lst(URI_TID), body)`.

**Why later, not now:** the foundation's dispatch keys on the map key, so the typed body is a refinement for the
lattice/lowering, not a prerequisite. Do it after Phase A's lowering is working so you can read the lattice from the
tid instead of re-deriving it.

---

## 4. Phase D — `law_shard` → `overrideOf`

> **Rulings, 2026-10-24 (Marko), and status.**
> 1. **Compile-time binding** — "it provides better debugging as to what is going on". `Inst.resolve` is the one
>    choke point every resolution passes (both selectors, the runtime escape hatch). After the native resolution it
>    calls `Network.Helper.atPeer`: a resolved `from`/`to` whose literal address names a dialed peer that overrides it
>    keeps the native signature, binding and typing, with `f()` swapped for the peer's body. The body is wrapped in
>    `Network.AtPeer`, which runs it inside the network's q-proc layer (the one place a vid is handed to a source:
>    a local `ws://peer/x/#?subq` must still fire), and explain reports `Network$AtPeer` as the class. An address
>    computed at run time still reaches the peer through the `Network.read`/`write` ladder.
> 2. **law_shard keeps `to(<peer>)`; no derived overrides.** A `to()` that compiles to an `eval()` form is rejected.
>    Instead `ws_client` declares `to` and `from` itself, beside `eval`, each on its native signature with a ws body:
>    `from(<uri>)` ships `from(</path>)`, `obj.to(<uri>)` ships `start(obj).to(</path>)`. law_shard's emitted
>    `to(<ws://…>)` reaches the ws client's `to`. The `to` sits inside `select([…])`, and the compiler keeps a
>    blocking instruction's code arguments raw (`Inst.Helper.resolveArgs`), so nothing after the rewrite would bind
>    it — hence the next ruling.
> 2a. **The compiled code has the bindings** (Marko, 2026-10-24). `PlanInterpreter.shard` binds each route at plan
>    time: it resolves `to(<target>)` against the slave the route carries, so `atPeer` runs there and the emitted
>    `select` holds the native `to` with the target peer's own body (`Network$AtPeer`) — the listing says what runs
>    where, at every level of a tree (an interior peer binds its own leaves' routes when it re-plans). A route that
>    does not resolve stays the bare `to(<target>)`, resolved at run time as before. The compiler's raw-args rule for
>    blocking instructions is unchanged. **Future:** a "strip bindings" tool that keeps code compiled but detaches
>    its bindings, so new ones can be attached (the same listing re-targeted).
>
> Proven by `NetworkConnectTest` (`testResolutionBindsAPeersToAtCompileTime`, `…FromAtCompileTime`,
> `testResolutionLeavesALocalAddressNative`, `testACompileTimeBoundToStillPassesTheNetworksLayer`),
> `NetworkWsDialTest` (`testWsClientDeclaresEvalFromAndTo`, `testCompiledToBindsTheWsClientsTo`) and
> `ShardTargetSelectionTest.testLawShardShipsOnlyToPeersThatServeTo` (the emitted route carries `Network$AtPeer`).
>
> 3. **Shard targets are online AND serve the route** (Marko: "pick by both — online and can to()"). The requirement
>    is `plan::T` data, so every present and future distribution rule inherits it: each `where` placement carries
>    `inst` — the key mirrors the client's `inst` — whose default is its shape's (`Placement.Shape.inst()`), and a
>    placement may add what its shipped chunk needs (`Placement.inst(tid…)`). **The default is `{to, eval}`**: every
>    distributing shape ships its chain through `to(<target>)` (the route), and what the route carries is a program
>    the target must apply (`eval`). Marko's caution: `to` means "write this obj to this address" — law_shard
>    overloads it by shipping code that an mtron far side applies on arrival, while an http POST `to` only writes; so
>    `to` alone would hand a program to a peer that stores it and runs nothing. law_shard's printed plan says
>    `inst=>[</m/inst/to>,</m/inst/eval>]` on its tree placement. One check, `Places.serves(peer, placement)` —
>    online ∧ the peer's own client overrides every required tid — replaces the liveness-only filter in
>    `PlanInterpreter.shardTargets`. The loop `id_client` declares `eval`, `from` and `to` (each the identity of the
>    wire body), so the loop equivalence obligation (`PlacementEquivalenceTest`) still holds. Proven by
>    `ShardTargetSelectionTest` (incl. an online POST writer — `to` without `eval` — refused).
>    The *potential* listing (`catTypes`' reach gate over `Places.reachable()`, roster included) stays optimistic:
>    pruning an undialed candidate needs §4's declared half — scheme signatures stating what a scheme's client
>    serves — which waits on the signature records.

**Goal:** the distribution rewrite's `to(<peer>)` becomes "resolve `to` from the peer", the first real consumer of
the general dispatch.

**Files:** the `law_shard` plan / `algebra/rewrite/PlanInterpreter.java`, and the `sum_gather` rule in `machInstSet`.

**Steps:** where the rewrite emits `to(<peer>)` (and `select`/`merge` plumbing), route it through
`overrideOf(peer, TO_INST_TID)` (or the eval fallback). Confirm the `ws_client`-dialed shard still round-trips.

**Verify:** the `distributed-patterns` / `cat-rewriter-distribution` suites, and a live two-host `law_shard` run
(`ginger` ↔ `booger`).

---

## 5. Phase E — `handler` demotion + test migration

**Goal:** finish the collapse — `handler` becomes a dial-time hint only; the surface is the `inst` map.

**Steps:**
1. Confirm `handler` is no longer *read* for capability decisions anywhere; where a consumer still branches on it,
   switch to "which tids are in `inst`".
2. Migrate the remaining `transport`-shaped assertions in the network tests to `inst`-shaped ones.
3. ~~Delete the legacy `send_recv` path in `asTransport` once Phase B has migrated every client.~~ Done 2026-10-24
   (Phase B's `send_recv` migration).

---

## 6. Open questions to settle during the hand-off (not blocking)

1. **The override calling convention** (Phase A step 4) — the single most important thing to get written down; it is
   the contract every client and every lowering rule shares. **Settled 2026-10-24:** same signature as the native,
   dispatch swaps `Inst.f()` only — see the ruling under Phase A step 4.
2. **Reach inheritance** (pre-existing, unchanged): whether a child frame reuses the parent's *live* overrides or
   re-dials a *known* authority. The capability/liveness split makes this a pure-liveness question now.
3. **Tool→instruction naming** (Phase B, mcp): whether a tool's tid is the tool's own name (`codegraph_explore`) or
   a namespaced form (`/m/mcp/codegraph/explore`). Pick one; it's the key space of `inst`.

---

## Hand-off checklist for a fresh agent

- [ ] read `docs/design/peer_instset.md` (design) and this file (plan)
- [ ] the foundation is green: `NetworkConnectTest`, `MachineAsyncFrameTest`, `MachineIsolationScriptTest`, `MachinePerspectiveTest`
- [ ] start at Phase A (it is the only phase everything else depends on); Phases B–E can then be parallelized
