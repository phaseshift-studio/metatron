# the qparam axes — implementation plan

**Date:** 2026-10-28 **Context:** the design is settled in `distributed-computing-qparams.md` (`?place`, `?hop`,
`?split`, one coefficient reading). This is how it lands: the token/API delta, the atomic chunks (each one a
stable commit point), and the parameter sweep that proves the axes are orthogonal. **Status:** plan.

**Conventions this plan obeys:** keys and their value tokens are **singular** — `?place`, `?hop`, `?split`;
`local`, `peer`, `stream`, `block`, `key`, `owner`, `index`, `copy`. mtron's own words win over new ones (`stream`,
not "bag"). And there is **no `?loop`**: repetition is mtron's `repeat()`/`loop()` around the layout, so the rounds
axis (N, R) is the language's job, not a key's.

Method: the Lead writes each chunk's brief, one agent does one chunk, the Lead verifies the diff and runs the
named suites, the human reviews, the human commits. **No agent performs any git operation** (repo rule).

---

## 1. The token and API delta

| token | today | after | chunk |
|---|---|---|---|
| `HOP` | `HOPS = "hops"` | **renamed** to `HOP = "hop"`; `?hops` kept as a read-only alias for one cycle | C1 |
| `PLACE` | — | **added.** The place set on a `Placement`, and the `?place` q key | C1, C3 |
| `SPLIT` | the profiling rec key (`resolve/apply/split/flow`) | **reused** for the q key and the decision — one word, two surfaces; no second token for the same term | C1, C3 |
| `TARGET` | the single address on a `Placement` | superseded by `PLACE`; read-only in `Placement.of(rec)` for one cycle | C3, gone in C11 |
| `SHAPE` | the topology enum (`local/peer/peers/space/tree/shuffle/chain/star/none`) | **derived** from `(place, hop, split)`; the enum survives only as a read-compat mapping | C3 (derive), C11 (delete) |
| `SPAN`, `FLAGS` | a region + its atom obligation | unchanged | — |
| `INST` | what a target must serve (`eval`) | unchanged; the **local** place demands nothing | C5 |
| `CUT`, `WHERE`, `LAW`, `COMBINER` | the selector, the decision list, the laws, the computed combiner | unchanged — the combiner stays computed | — |

`Shape` is really three decisions in one enum, and the split is what the chunks buy:

| shape member | what it really is | where it goes |
|---|---|---|
| `PEER`, `PEERS`, `TREE`, `SHUFFLE` | fan-out topology | `(place, hop, split)` — and `PEERS` becomes the value token `peer` |
| `CHAIN`, `STAR` | the cut-less **move** plan | `place = [<uri>]`, no cut — `relocate` already does this |
| `NONE` | the **unit transform** (delete the isolated span) | stays: `Placement.none(...)`, not a place set |
| `SPACE` | a **landing** (durable results, R) | deferred: the tail's `to(<space>)`, not a fan-out |
| `LOCAL` | **two things today**: the origin's *tail* decision (`merge().<combiner>.<tail>` runs here) **and** the home place in a fan-out | the tail decision stays on the origin and becomes implicit; the home *place* is the `local` designator. C3 must keep them apart, or the plan's second placement silently becomes a lane |

Java surface, after C3 (additive; the old accessors still answer, derived):

```java
// the decision
Placement.of(PlaceSet.peer(), Hop.of(1, cInt.ANY()), Partition.stream())   // cInt.ANY() = {*}
Placement.none(...)          // stays: the unit transform, not a place set
placement.place()            // PlaceSet: designators + demands
placement.hop()              // Hop: height() int, arity() cInt
placement.split()            // Partition: kind + part count (cInt.ANY() = the lane count)
placement.obligations()      // derived from the three axes, not declared

// the builder
PlanBuilder.plan("law_shard").cut(Cut.reducer(Law.monoidic, Law.commutative))
        .place(PlaceSet.peer()).hop(1, cInt.ANY()).split(Partition.stream())
        .where(...)          // spans stay as they are
```

---

## 2. The parameter sweep

**The oracle is the law, not the layout:** `distribute(c) ≡ c` — every axis is result-preserving (Thm 19) except a
split, which *declines* when its premise fails. So each row asserts **two things**: the emitted *structure* and
the *value*, and the sweep is really the **test of the design's orthogonality claim**: if an interaction between
axes surprises, the axes are wrong.

**Hermetic by default.** The emitter is a function of `(plan, code, env)`, `PlanInterpreter.apply(plan, code,
env)` is already public, and `catRewrite.planOf(name)` hands out the plan — so a sweep row can lower a chain
against a **synthetic `Env`** with no rewriter dispatch, no dial and no fork (milliseconds). Only the value block
needs peers, and `loop://` in-process peers are enough (the `PlacementEquivalenceTest` pattern); forked
`PeerCluster`s stay for the two end-to-end suites. `Env` needs a factory for this (`Env.of(peers, machines,
authorities)`) — chunk C4.

**The harness** (`src/test/java/.../network/DistributedSweep.java`):

```java
record Lane(boolean local, fURI authority, List<Obj> part, int height, cInt arity) {}
record Layout(List<Lane> lanes, List<fURI> routes, Obj combiner, List<Obj> tail, Code emitted) {}

static Code   lower(String expr, Env env);            // PlanInterpreter.apply(SHARD_PLAN, parse(expr), env)
static Layout layout(Code emitted);                   // lanes, parts, routes, combiner, tail
static Obj    run(String expr);                       // evaluate as-is (the local fold) — the oracle
static Env    env(fURI... authorities);               // synthetic peers: host, inst overrides, status
static Eval   eval(String expr, Env env);             // lower + layout + value: one row's worth
```

**Five blocks, each its own class, `@CsvSource(delimiter = '%')`, one row per scenario:**

| block | class | hermetic? | what it proves |
|---|---|---|---|
| **S1 layout** | `PlaceSweepTest`, `HopSweepTest`, `SplitSweepTest` | yes | the emitted structure per axis value |
| **S2 resolution** | `PlaceResolutionTest` | yes | specificity, exclusion, wildcard, reach-not-knowledge, offline/no-`eval` drops |
| **S3 law** | `SplitLegalityTest` | yes | the gate: `copy` × non-idempotent **declines**; `block` × stream declines; `key` × group emits |
| **S4 value** | `PlacementEquivalenceTest` rows + a new `ChainShardTest` | loop peers | point/star/tree/**chain**/S all equal the local fold, with the partials |
| **S5 identity** | `IdentityShardTest` | yes | `0`, `n{0}`, `0{0}`, `0{*}` are **byte-identical** to the original chain |

**Coverage = one-factor-at-a-time + pairwise, never the full cube.** OFAT localises a failure to one axis;
pairwise catches the interactions the design claims do not exist. 5 values × 3 axes = 125; pairwise is ~21 rows.

```csv
# S1: place sweep (hop=1{*}, split=stream) — what the lanes are
peer                % 2 % [1,3,5|2,4,6]     % 27
[local,peer]        % 3 % [1,4|2,5|3,6]     % 27
[local{4}]          % 4 % 4 parts           % 27     [-- peerless: lanes --]
[#]                 % 2 % all reachable     % 27     [-- the wildcard --]
[//a{2},//b{1}]     % 3 % a:2, b:1 parts    % 27     [-- weighted --]

# S1: hop sweep (place=peer, split=stream) — the shape of the tree
1{*} % star        4{2} % 4 fan levels     2{1} % chain
2{*} % tree        2{2,4} % elastic        n{0} % point

# S1: split sweep (place=peer, hop=1{*})
stream % stride parts   stream{2} % 2 parts, replicas   copy  % whole seed each lane
block  % contiguous     key       % bucketed            {0}   % decline
```

**Per-commit sweeps.** A chunk's brief names the block it must turn green and the suites it must not break:
`PlacementEquivalenceTest`, `DistributedRewriteTransportTest`, `TreeShardTransportTest`, `ParallelFanOutTest`,
`AcceptedPeerShardTest`, `ShardTargetSelectionTest`, `LawShardOfflinePeerTest`, `catInstSetTest`,
`PlanBuilderTest`, `ObligationsTest`.

**The menu sweep** — one row per menu pattern, asserting the *kind* of layout, so the reference's table becomes
executable: A `star`, D `lanes`, E `tree`, F `block`, H `stream{2}` with `[p{2},q{2}]`, S `[local,peer]`; B/C/Q
(`owner`), G (`key`), M (`[row,col]`) and P (`index`) are `@Disabled` with the reason until their chunks land —
never silently absent. **N/R are not sweep rows**: `repeat()` around the layout is the engine's, and its
composition with a shard is its own test if it turns out to need one.

---

## 3. The atomic chunks

Each chunk ends **stable**: it compiles, its own tests are green, and the suites named in its brief are green. A
chunk never contains a behaviour change plus a test migration unless the migration *is* the guard (C0).

### C0 — the layout reader and the sweep harness *(tests only)*
**Why first:** every later chunk edits the emitter; without one reader, test drift becomes mysticism.
**Files:** new `DistributedSweep.java` (`Layout`, `Lane`, `layout(...)`); `DistributedRewriteTransportTest`,
`ParallelFanOutTest`, `TreeShardTransportTest`, `PlacementEquivalenceTest`, `AcceptedPeerShardTest`,
`ShardTargetSelectionTest`, `LawShardOfflinePeerTest` re-expressed through it **asserting the same things**.
**Acceptance:** `Tests run:` unchanged and green; no assertion weakened (a reviewer must be able to diff the
expectations one for one).
**Commit:** `test(distributed): read an emitted layout as data, and pin the current layouts`

### C1 — the singular key convention *(behaviour-preserving)*
**Files:** `Tokens` (`HOP = "hop"` replacing `HOPS`; `PLACE` added; `SPLIT` reused); `PlanInterpreter.hop(...)`
reads `hop` then falls back to `hops` (deprecated alias, one cycle); `Placement.hops()` → `hop()`; the docs and
tests that spell `?hops` re-spelled `?hop` (the reference's E section, the menu doc's §E, the javadoc in
`catRewrite`/`PlanInterpreter`/`Placement`, `TreeShardTransportTest`, `catInstSetTest`, `ObligationsTest`).
**Acceptance:** every existing test green with `?hops` and with `?hop`; the alias proven by one row each way; no
behaviour change (C0's layout pins unchanged); a `grep -rn "hops"` in `src`/`docs/skills`/`docs/design` returns
only the alias's own sites.
**Commit:** `refactor: singular distribution keys — ?hop (?hops aliased), ?place, ?split`

### C2 — the q-value read *(no behaviour change, and no new class)*
**The read already exists: `fURI.qValue`.** `qValue(String, Class)` (`furi/form/AbstractfURI.java:965`) and its
`fURI`-key delegate (`furi/fURI.java:852`) are the projection; for `Lst`/`Rec` they both return
`ObjmtronSerializer.parse(text)`, so one call is the generic object read. **Do not add a `QParams` class, a second
parser, or anything that re-splits `qMap()`.**

**Files:** the two rules `qValue` lacks, as free functions at the emitter's point of use (a private static in
`PlanInterpreter`, or the `XXX.Helper` it belongs to — not a type):
- *malformed?* — a `fail::T` from the projection (or a raw `str`, where a value was required) means the author's spec
  cannot be read ⇒ **refuse loudly**;
- *demand* — the coefficient via `c()`; and the count, where a numeric value may arrive as an `int` **or** as a
  numeric uri (`4{2}`), coerced through the declared `uri → int` row.
Plus a test class (`QParamReadTest`, or rows on `PlanBuilderTest`) over the console-verified values.
**Acceptance:** the console-verified rows (`4`, `4{2}`, `{2}4`, `[a,b,c]`, `{2}//xxz/dag/3`, `[local=>4,
//abc:45=>2]`, a backslash value → refusal, a `;`-joined value → refusal); no new class in `src/main`; `grep -rn
"qMap()" src/main/java/.../algebra/rewrite/` shows no new read site.
**Commit:** `feat(cat): read q parameters strictly — via fURI.qValue, refusing what cannot be read`

### C3 — the decision model, expressed in the axes *(no behaviour change)*
**Files:** `Placement` (+`place()`, `split()`, `hop()`→`Hop`; `shape()/target()` derived); new `PlaceSet`,
`Hop`, `Partition`; `catTypes` (`PLAN_TYPE.where` gains `place`, `split`, `hop`); `PlanBuilder` (`.place`,
`.hop(n, w)`, `.split`, obligations derived); tests `DecisionModelTest` + `ObligationsTest` rows.
**Acceptance:** a table test proving `derivedObligations(axes) == Shape.obligations()` for **all nine** shape
members, so the derivation is a refactor, not a guess; `Placement.of(rec)` still reads the old `shape/target`
encoding; **`LOCAL`'s two meanings stay apart** (the origin's tail vs the home place).
**Commit:** `refactor(cat): the distribution decision as places, depth and partition`

### C4 — an injectable `Env`, and resolution *(no production behaviour change)*
**Files:** `Env` (`of(peers, machines, authorities)`; `snapshot()` unchanged); the resolution logic extracted from
`shardTargets` into `PlaceResolver` (designator → `Places.place`/`self`/`reachable` → `serves`); new
`PlaceResolutionTest` (block S2).
**Acceptance:** every resolution rule has a row: specificity, exclusion-by-omission, wildcard universe = dials ∪
local, offline drop, no-`eval` drop, `//host:port` (port not a selector), `scheme://` authority.
**Commit:** `refactor(cat): resolve fan-out targets from an Env, not the live machine`

### C5 — `?place` at the emitter *(S lands; opt-in only)*
**Files:** `PlanInterpreter.shard` (resolve the place set; `k = Σ demands`; lane 0 = `local` with `eval()`;
`k − keep < 1` ⇒ decline); `catInstSet.realizedWhere` (skip the addressless route, record `local`).
**Acceptance:** S rows (`[local,peer]`, `[local{4}]`, `[local{0,4},peer{2}]`), the value equals the local fold,
and the **default plan emits the identical layout to today** (C0's pins unchanged).
**Commit:** `feat(cat): ?place — the place set, and the master as a place at distance zero`

### C6 — `?hop` as a tree shape *(default unchanged)*
**Files:** `PlanInterpreter` (`hop()` → `(height, arity)`; the deterministic `w`-of-`m` window; restate the form
on the slave; the depth marker moves to the **fold** of the emitted slave; `{0}`/`0` ⇒ identity).
**Acceptance:** the hop family (`1{*}`, `n{*}`, `n{1}` chain, `n{2}`, `4{2}`, `n{2,4}`, `n{0}`), the identity
byte-equality block (S5), and the chain row as the milestone: **it is the shape that does not exist today.**
**Commit:** `feat(cat): ?hop as a tree shape — height and arity`

### C7 — `?split`, the partition seam *(default unchanged)*
**C7a** the seam + `stream` (= today's stride) + `copy` (= `stream{1}`) + the `Requires` gate +
decline-on-unsound + `SplitLegalityTest` (S3). **C7b** `block` (+ the `order` requirement) and its sweep rows.
**Acceptance:** default `stream{*}` emits today's layout; `copy` × `mean` **declines**; `block` × stream
**declines**; `stream{2}` over 4 demanded lanes produces replicas.
**Commit:** `feat(cat): ?split — the partition seam, with its law gate`

### C8 — introspection
**Files:** `realizedWhere`/`realized` (the place set with realized demands; `local` recorded); `PlaceTest` rows.
**Acceptance:** `<expr>.as(cat:path::T)>>where` shows `[//booger=>2, //ginger=>3]`, and a `{2,5}` band reports
what it actually realized.
**Commit:** `feat(cat): record the realized place set on path::T`

### C9 — the plans speak the axes
**Files:** `catRewrite` (`SHARD_PLAN`; the local/theory plans stay as they are); `PlanBuilderTest`,
`catInstSetTest` rows.
**Acceptance:** no plan rec carries `shape`/`target` any more; every emitted layout is byte-identical to C0's
pins for the default schedule.
**Commit:** `refactor(cat): law_shard declared in the axes`

### C10 — docs
**Files:** `docs/skills/mtron/references/distributed-computing-mtron.md` (the S row gains ✅ once C5 is in; the
route vocabulary table gains the place set), `docs/design/distributed-patterns-menu.md` (§S resolved, §E's
"interior node's own share" resolved), `docs/design/distributed-computing-qparams.md` (**status** → implemented,
"what exists today" rewritten). Run `bin/metatron-docker build docs` and fix every `fail::` line.
**Commit:** `docs: the qparam axes`

### C11 — deferred, in order of appetite
`Shape` deletion and the `TARGET` compat removal; `key` / `owner` / `index` partitions; the bands-in-lists lexer
rule; the visited set (so `n{w}` means *n machines*, not n roles); the `timeout(inst)` deadline fix for the local
lane.

---

## 4. Orchestration

- **Serial by default.** C5–C7 all edit `shard`; C3 edits the rec the emitter reads. Only C0 ∥ C1 (disjoint files)
  can run together.
- **One agent, one chunk, one brief.** The brief names: the design doc section to read; the files to touch; the
  files **not** to touch; the test rows to add; the suites to run; and the report format.
- **The build is docker.** `bin/metatron-docker build test -Dtest=A,B` (never the host `./mvnw` while the live
  server owns 8555/8777), and read `Tests run:`, never the exit code.
- **No git, ever** — the human commits at the stable point. The Lead's hand-off is a diff summary + the test
  output; nothing is staged.
- **Verification is two-sided.** The agent runs its named suites; a second, verifier agent runs the wider set
  (`PlacementEquivalenceTest`, `DistributedRewriteTransportTest`, `TreeShardTransportTest`, `ParallelFanOutTest`,
  `AcceptedPeerShardTest`, `ShardTargetSelectionTest`, `catInstSetTest`, `PlanBuilderTest`) in the background
  while the Lead reads the diff. A chunk is not done until both agree.
- **Rollback is a chunk boundary.** Because each chunk is stable, a rejected chunk is reverted whole and the brief
  is revised — never patched forward.

---

## 5. Risks

| risk | mitigation |
|---|---|
| the emitted-layout tests drift faster than the emitter changes | C0 exists exactly for this: one reader, one diff per chunk |
| the `?hops` → `?hop` rename collides with stale docs, space tips, or test data | C1 reads both keys for one cycle and updates every site in the same commit; the `grep` is part of its acceptance |
| `Shape` deletion breaks the law gate | C3 proves the derivation equals the enum's obligations *before* anything depends on it; deletion is C11 |
| the `?hop` form's projection (`4{2}` vs `{2}4`) | C2's reader accepts both (numeric uri coercion); C6 emits the leading form |
| hermetic sweeps diverge from the real rewriter path | the value block (S4) and the two forked-peer suites stay on the real path; the hermetic blocks only assert structure |
| a chunk silently weakens a test to pass | the brief forbids touching an existing assertion except in C0, C9 (which pins byte-identity), and the migration named in the chunk |
| the local lane's deadline (5 s) bites a slow local slice | named in C5's acceptance as a known risk; the fix is C11 |
