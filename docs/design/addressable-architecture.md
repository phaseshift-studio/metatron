# Addressable — one contract under the Machine's sources

*Status: design record, 2026-10-13. Companion to `distributed-computing.md` (the distribution thesis and its
staged plan) and `docs/skills/metatron/references/machine-architecture-java.md` (the Machine contract). This
document records the architectural deduction that fell out of the instruction-memory/data-memory split, and the
development plan that lands the split without the codebase devolving mid-flight. Slice 1 (the `Addressable`
interface, decline semantics, consumer rerouting) is in the working tree as of this writing.*

---

## 0. The thesis, in one paragraph

Every source a Machine dispatches to — its network, its data memory, its instruction memory — is the same thing:
an **Addressable**, something a vid can be read from and written to, with a **pattern** saying which addresses it
answers for and a **q** axis modulating which aspect of a resource is being asked. Memory, Network and the
machine's instset are then not three mechanisms but three *routers over Addressables*, differing only in the
shape of their members' patterns (path, authority, tid-jurisdiction) and their precedence rule. The Machine is
the router over the routers — "a space of spaces" generalizes to *a source of sources* — and `Machine.read` /
`Machine.write` is the one gatekeeper that decides which source answers. The InstSet/Memory split is the first
consequence: an instruction set is vocabulary, not a place, so it stops being a `Space` and remains what it
always was — an Addressable with a jurisdiction.

## 1. The contract

```java
public interface Addressable {
    Obj read(final fURI vid);              // noobj = "not mine" — decline, never throw
    Obj write(final fURI vid, final Obj obj);
    fURI pattern();                        // the jurisdiction: what addresses this source answers for
}
```

- **Decline convention.** A source that cannot answer for a vid returns `noobj`, so the gatekeeper falls through
  to the next source. An uncovered address is an ordinary answer, never a control-flow event
  (`Memory.getSpaceFor` returns the `noobjSpace` sentinel on a miss — landed in slice 1).
- **`pattern()` is jurisdiction**, not location. A `Space`'s pattern is a path (`/usr/#`), a peer's is an
  authority (`ws://host:port/#`), an ISA's is a tid prefix (`/m/math/cat/#`). `fURI` already expresses all
  three, which is what makes one dispatch algorithm possible.
- **`q` is the aspect axis**, orthogonal to the address axis: the value, its documentation (`?docq`), its
  subscription stream (`?subq`), its representation (`?mimeq`), its counter (`?incq`). Q-procs are today a
  `Space` attachment only by accident of where `qs()` lives; the contract hoists to a small `Queryable`
  (`qs()`/`addQ()`) that `Space` and `InstSet` share, and eventually `Network` — `?mimeq` over the wire *is*
  content negotiation, evaluated by the far side.
- **The q routing law**: a q belongs to the source that answers the address. A data address's docq runs in the
  covering space; a vocabulary address's docq runs in instruction memory; a peer's q ships and runs peer-side.
  Qs that are local coordination (`?lockq`) must declare themselves non-forwarding — one flag on the QProc,
  not case law in transports.

## 2. The unification

| router      | indexes                         | members' patterns                | precedence rule                      |
|-------------|---------------------------------|----------------------------------|--------------------------------------|
| Memory      | spaces (local Addressables)     | path-shaped (`/usr/#`)           | `mostSpecific`                       |
| Network     | peers (remote Addressables)     | authority-shaped (`ws://h:p/#`)  | `sameService` on authority           |
| InstSet     | ISAs (vocabulary Addressables)  | tid-jurisdiction (`/m/mach/#`)   | own → imports → previous, by identity |

All three are *a union of Addressables with a precedence rule*. Two corollaries:

1. **The gatekeeper's order is specificity, not convention.** `Machine.read`'s network → memory → instset is
   "authority patterns > path patterns > the vocabulary fallback" — and `Memory.mostSpecific`'s comparator
   already sorts scheme-carrying patterns first. The dispatch order is that comparator, written out by hand.
2. **`NetworkUnion` (D9 in `distributed-computing.md`) stops being bespoke.** If Network is the same
   union-of-Addressables shape as `MemoryUnion` and `BasicInstSet`, its frame-scoping arm is the shape the
   other two already have — plus the deny arm membership needs, which is where the shapes legitimately differ
   (membership is a capability; an address is not).

The frame nest is an Addressable tree. (`RemoteMachine`, a machine that was nothing but a peer-backed Addressable,
was removed on 2026-10-21: code reaches a peer by being shipped there in a `law_shard` plan, never through a frame
that stands in it.)

## 3. Documentation is vocabulary

**A doc lives where its subject lives.** An instruction's documentation is vocabulary metadata; a datum's
documentation is data metadata. So instruction docs become a fifth kind in instruction memory — `DOC_TABLE`
beside `INST_TABLE` / `TYPE_TABLE` / `CONST_TABLE` / `REWRITE_TABLE`, keyed by the same identities (an inst's
tid, a type's/const's vid) — and `?docq` keeps one spelling at the surface while the *source* differs by memory
kind, decided where source-dispatch already lives: the gatekeeper.

What this buys beyond parity:

- **Docs compose like vocabulary.** The union merges DOC entries by the same identity rule as everything else:
  a child frame that shadows `plus` answers `plus?docq` with its own doc; an import's docs arrive with the
  import and die with the frame on `pop()`.
- **`docWrap`'s owner lookup** becomes `instset().owner(objID)` — the member whose pattern covers the id —
  memoized per level in `BasicInstSet`. This retires the measured doc-setup regression (0.1s → 4.2s when the
  address-index route was removed) rather than working around it.

Rejected alternatives, for the record: docs inline on the Obj/Type (ships on every wire crossing; a doc should
stay addressable, not embedded) and a global `/sys/doc/<id>` data space (makes `?docq` a rewrite rule in
`Machine.read` — genuinely special-cased — and loses frame scoping: no shadowing, orphaned docs on pop).

## 4. The network half (recorded now, built later)

- **wsSpace/httpSpace conflate three roles.** The *listener* (binds the port) is this machine's presence —
  the source of `Network.authorities()`/`own()`, today derived by scraping `host` off mounted spaces. The
  *dialer* (wsclient, HttpRec) is a peer: an entry in `network.peer`. The *live sessions*
  (`ws://h:p/<route>/<n>`) are connection state — `connections()`. The "localize only if the path still
  resolves" trap in dispatch exists because sessions-as-space blurs the authority boundary; homing them in the
  network removes the trap rather than guarding it. `webInstSet` becomes the protocol/handler library the
  roster composes — transports, not places.
- **A peer need not be metatron.** A REST API is an Addressable: `read` = GET, `write` = POST, pattern = its
  base URL, `?mimeq` = Accept, `?docq` = its API description. The existing "only wsspace+wsclient carry
  metatron-to-metatron traffic; the rest are remote service access" becomes a **degree**, not a kind — a
  capability lattice declared on the roster entry:

  | capability   | contract                         | planner move it licenses                          |
  |--------------|----------------------------------|----------------------------------------------------|
  | `value`      | read/write only                  | move data to code                                  |
  | `pushdown`   | accepts a query algebra          | rewrite the floatable prefix into that algebra (the tble rewrite pushdown, generalized) |
  | `eval`       | full mtron (`mtron_ws`)          | ship monads; host frames                           |

  Declared, never emergent — same rule as membership. This is the fact the distribution rewrites consume at
  binding time: laws license a move, capability bounds it.
- **What stays a `Space`**: genuinely local storage with ownership of its store — memSpace, fsSpace, the
  machine's infra. The taxonomy settles as `Addressable` { `Space` (local store), `InstSet` (vocabulary),
  peer (remote), and the unions that route over them }.
- **Do not migrate tble/dcmnt/grph/vec now.** The reframing pays off in the planner (capability-aware binding)
  without touching their space registrations; re-homing them is a later slice, after the listener/dialer split
  and `NetworkUnion` exist.

## 5. Invariants that must survive the unification

1. **Fail-closed membership.** A peer's pattern is per-declared-authority; never a `ws://#` catch-all in the
   dispatch path. (Today's wsSpace pattern *is* such a catch-all in the data index — moving it out removes a
   hazard; uniform dispatch must not reintroduce it.)
2. **The read path must never allocate** (unchanged from the Machine contract) — the Addressable hoist adds
   interfaces, never construction on the read path.
3. **The catch-all must decline, not answer.** The root machine's `/#` space carries q-procs; a vocabulary
   `?docq` read reaches it in data memory *before* the instset hop, so an empty q answer there must count as a
   miss (`noobj` → fall through) or it shadows instruction memory forever.
4. **Writes mirror reads at the gatekeeper.** `Machine.write` is network → memory only today; once ISAs leave
   the index, a vocabulary write (a doc, a migrated registration) would land in the root catch-all *silently* —
   the known `AbstractSpace`-writer-is-a-no-op failure mode. Instruction memory needs its write arm.
5. **Deliberate vs. fallback reads.** The instset hop takes `readDeclared` (no lazy materialization). First
   entry is deliberate (full read, q-procs included); a read made under `READING_INSTRUCTION_MEMORY` is nested
   type-resolution and stays declared-only. Schema instsets keep their materialize-on-deliberate-read behavior
   through this rule.

## 6. Development plan — the InstSet/Memory split lands first

Primary goal: finish separating instruction memory from data memory. The Addressable/Queryable consequences
land only as far as that goal needs; the network half is recorded above and deferred. One slice = one green
suite (`./mvnw install`, or `bin/metatron-docker build test`); interface moves before class moves; no
long-lived dual paths — an alias introduced by a slice is retired by that slice's end.

**Slice 1 — the contract and the consumers (LANDED 2026-10-13, in working tree).**
`isa/Addressable.java` (read/write + decline convention); `Space extends Addressable`; `Network extends
Addressable`; `Memory.getSpaceFor` declines to `noobjSpace` instead of throwing; `InstSet.members()` +
`BasicInstSet.members()` (own → imports → previous, cycle-guarded); `FixPointRewriter` collects rules from
`Machine.current().instset().rewrites()`; `catInstSet.instructions()` sources from the union. *DoD: full suite
green (verified by the user's run); rewrite profiling counters may shift pass counts, results identical.*

**Slice 2 — pattern() and Queryable (pure hoists).**
`Addressable.pattern()` (Space already has it; InstSet already declares it; Memory's is `ALL`; Network derives
its own from the declared roster later — a default `ALL` with an authority guard suffices now). `Queryable`
(`qs()`/`addQ()`): `Space extends Queryable`; `AbstractInstSet` keeps its q list through it. No behavior
change anywhere. *DoD: suite green; no call-site changes outside the two interfaces.*

**Slice 3 — docs become vocabulary.**
`DOC_TABLE` in `AbstractInstSet`; the docq attachment's backing store moves from its hidden sub-spaces to the
table. `instset().owner(fURI)` on `BasicInstSet`, memoized per level with a registration counter — the first
per-machine cache, and the memo home everything later reuses. `QCollection.internalDocWrap` resolves the owner
via `owner()`, not `getSpaceFor`. Read side: the gatekeeper's instset hop serves `?docq` under the
deliberate-read rule (§5.5); the catch-all decline (§5.3) gets its test. *DoD: docs pipeline
(`bin/metatron-docker build docs`) green with no `fail::` lines; doc-gen setup back at the ~0.1s baseline;
`*/m/inst/plus?docq` answers from instruction memory.*

**Slice 4 — the write gatekeeper + migration check.**
`Machine.write` gains the instruction-memory arm. Design decision to settle here: writes route by *jurisdiction*
(`instset().owner(vid)` answers → instruction memory takes the write) rather than by miss-fallthrough, because
data memory's root catch-all never declines a write. Then test whether `AbstractInstSet.checkPattern`'s
migration writes are dead — the union read resolves by tid identity regardless of which member's table holds a
registration, so migration may delete outright; if a case survives, it re-routes through `owner()`. *DoD:
a vocabulary write from mtron reaches the owning ISA's table; `mInstSetTest` green with migration deleted or
re-routed.*

**Slice 5 — the cut.**
`InstSet.mount()` becomes `Machine.current().instset().refer(isa)`; the `instanceof InstSet` arm leaves
`BasicMemory.addSpace`; the five hand-mount sites (tbleSpace ×2, dcmntSpace, vecSpace, ExistingGraphSchema,
sysInstSet, DocsUtil) call `mount()`. ISAs leave the address index. `AbstractInstSet` rebases off
`AbstractSpace` onto its own base (MRec + Addressable + Queryable): keeps `pattern` as a field, the q list,
and one private direct table read both read forms reach; sheds routes/stats/space registration.
`InstSet extends Addressable, Machine.Component` (+ Queryable). `BasicInstSet.resolve`'s index-walk fallback
becomes `Machine.read(vid)` (the ISA still writes itself at its vid as a *value* — reflection, not routing).
`noobjSpace` stays a class implementing both `Space` and `InstSet` — legal and correct after the cut, since
both are interfaces. *DoD: no `instanceof InstSet` in memory/space code outside `noobjSpace`;
`MemoryIsolationScriptTest`, `InstSetUnionReadTest`, `mInstSetTest`, the docs pipeline all green; re-measure
the previously-regressing 13-test class (4.0s baseline) and boot setup — parity or better, on the strength of
slice 3's `owner()` memo.*

**Slice 6 — per-machine caches (the payoff).**
`catInstSet`'s static memos (`CACHED_INSTS`, POSITION/CONTESTED/ORBIT/FAMILY) move onto `BasicInstSet` per
level, invalidated by the registration counter, dying with the frame on `pop()`. `rewrites()` cached the same
way, so `FixPointRewriter`'s per-compile collection is a cached read. *DoD: a child frame's import never
answers another frame's graph query; compile-time profiling shows the rewriter collection flat per pass.*

**Deferred (recorded in §4, not scheduled):** listener/dialer split of ws/http, `NetworkUnion`, `capability`
on roster entries, q-procs over the wire, the pushdown-peer reframing of tble/dcmnt/grph/vec, and the
distribution planner that consumes all of it (`distributed-computing.md` Stages 0–7 and the law-driven rewrite
family design).

## 7. Soup-avoidance rules

- **One slice, one green suite.** No slice begins until the previous one passed the full suite on a real
  build (`./mvnw install` or the docker loop).
- **Interfaces before classes.** A contract hoist (Addressable, Queryable, pattern) lands and proves inert
  before any class is rebased onto it.
- **No dual paths across slices.** Where a slice introduces a new route beside an old one (docq table beside
  docq sub-spaces, owner() beside getSpaceFor), the old route is deleted in the *same* slice.
- **The invariants in §5 are the review checklist** for every slice touching the gatekeeper.
- **Skill docs move with the code**: `machine-architecture-java.md` (the five components, the read path) and
  `rewrite-system-java.md` (rule collection) get their mechanical updates in the slice that invalidates them.
