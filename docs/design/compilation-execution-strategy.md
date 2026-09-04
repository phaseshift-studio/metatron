# Compilation & Execution Strategy — Design Hand-off

**Date:** 2026-08-15 **Status:** design agreed, **not yet implemented**
**Owner:** the user — pulled into `ideInstSet`; this doc exists to bring a future agent (or the user) up to speed so
work can resume. **Scope:** separate mtron *compilation* from *execution*, make instruction resolution / code
compilation faster, and eliminate nested `SwarmMachine` overhead.

---

## 1. TL;DR

- The pain: instruction resolution runs on every compile **and** every execution; `SwarmMachine` recompiles code on
  every apply; and nested call args (`a.small.computation()` inside a big pipeline) each spawn a whole thread-based
  machine.
- The direction: **separate compilation from execution** (javac/JVM or DB-planner style). `Code` (description) →
  `CompiledCode` (artifact) → execution. A `Strategy` composes compiler + executor + caches; strategies live **in the
  Router as first-class objs** at `/strategy/*` (a URI-addressed map, not a static singleton); selection happens at
  submit-time via `using(uri, code)`.
- Nested call args get a cheap **ITERATOR** execution path (a direct inst-by-inst fold on the calling thread) instead of
  a `SwarmMachine`, selected by a conservative, shape-based stamping rule at compile time.
- A **minimal v1** is deliberately small: 2 interfaces (`Strategy`, `CompiledCode`), 1 enum (`Framework`), the Router as
  registry, and one new execution path. Everything else (caches, cost models, plan trees) is internal enrichment that
  adds *no* new interfaces later.

---

## 2. The problem, concretely

1. **Compilation runs on every execution.** `SwarmMachine.runMonadicLoop()` → `this.resolve(START)` →
   `Code.resolve(lhs)` → full `rewrite()` + `resolveCode()` — every time a code block is applied, even in a hot loop
   applying the same code.
2. **Nested machines.** `Helper.applyArgs` applies call args via `arg.apply(lhs)`; `Code.apply` does `tryToInst()` →
   `SwarmMachine.of(...)`. So a small non-monadic value computation inside a call arg spawns a full thread +
   barrier-queue machine. Massive constant factor for zero semantic need.
3. **No memoization anywhere.** The `RESOLUTION_CACHE` in `Inst.java` is commented out (see §4) — value-level keys + the
   rewrite→apply→resolve cycle caused hangs. Candidate fetch, per-signature resolution, and type-compat checks all
   repeat work with no cache.
4. **Costly selection.** For N candidates, the resolver runs the expensive elaboration (`bindGenerics` + `resolveArgs`
   with nested-call recursion) on *every* candidate, every time — never just on the winner, and never memoized.

---

## 3. Current architecture review (files to read first)

### `isa/m/type/Inst.java`

The instruction object. Key facts:

- `Inst.resolve(lhs)` (line ~231) is the resolution entry: delegates to `InstResolver.get().resolveInst(...)`, and on
  failure does a **second router read** with the full tid (`Router.readFromSpace(this.tid())`, line ~286) and returns a
  *semi-resolved* inst (args bound, no `f`) — which then fails or re-resolves at apply time.
- Disabled cache: `RESOLUTION_CACHE` / `REWRITE_MODE` commented out at lines ~63-64 and ~236-266, with a detailed note
  about why (rewrite→apply→resolve circularity; the parser optimization 500ms→3ms is credited as the main win).
- `Inst.apply(lhs)` (line ~335) calls `this.resolve(clhs)` **again at runtime** even for compiled code, plus
  `Helper.applyArgs` re-applies every arg.
- `Helper.resolveArgs` (line ~511): per-arg `test()` + **recursive resolution of nested call args**
  (`usrArg.resolve(lhs)`) — the deep hot path.
- `Helper.bindGenerics` (line ~620): HashMap + `T()` reconstructions per candidate per resolution.

### `isa/m/type/Code.java`

The instruction chain.

- `Code.resolve(lhs)` (line ~99) = `rewrite()` then `InstResolver.get().resolveCode(...)`.
- `Code.rewrite()` (line ~69): walks **all** spaces, takes every InstSet's `rewrites()`, applies **every rule to the
  whole code**, fixpoint via `done = 2` + hash-change detection. Rule bodies themselves `apply()` → can resolve nested
  code → recursion.
- `Code.apply(lhs)` (line ~147): `tryToInst()` → multi-inst goes to `SwarmMachine.of(lhs, code).apply(...)`; single-inst
  path resolves + applies directly (this is the pattern ITERATOR generalizes).

### `isa/m/type/resolver/InstResolver.java`

Strategy holder + the default compile loop.

- `INSTANCE` AtomicReference, `get()/set()` (the static-singleton pattern we're moving away from — see §5.2).
- `resolveCode(lhs, code)` (line ~102): threads a type token (`lhs.type() → inst.rng() → next`), per-inst
  `resolve(token)`, clones with `selfVID` per slot; on exception keeps the inst unresolved (semi-resolution). **This
  whole method migrates into the `Strategy`/compiler in the new design.**

### `isa/m/type/resolver/ScoringInstResolver.java`

The default resolver (active in `InstResolver.INSTANCE`).

- Fast paths: `from`/`at` URI-form, and `as()` — note the `as()` fast path reads with *exact* `specificTypeId` dom/rng,
  so it **misses refinements** (nat vs int) and falls through to slow path.
- Slow path: fetch `Router.readFromSpace(basePath)` → filter "viable" (cheap) → if 1 candidate, cheap
  `transformCandidate`; if N, for **every** candidate: `scoreSpecificity` + dom/rng clones + `bindGenerics` +
  `filterOnDomainAllowUnique` (structural `test()`) + `resolveArgs` → `.max(score)`.

### `isa/m/type/resolver/FirstFindInstResolver.java`, `V2InstResolver.java`

- `FirstFind`: findFirst over candidates — cheap, order-dependent (the original behavior).
- `V2`: two-pass tiered scorer (strict then lenient) — *doubles* per-candidate transform work on misses. Prior session's
  work, documented in `inst-resolution-design-insights.md`.

### Supporting pieces

- `isa/mach/type/Router.java`: `readFromSpace(vid)` → `BootLoader.ROUTER.read(vid)`.
- `isa/AbstractInstSet.java`: insts stored in `INST_TABLE: Map<fURI, Set<Inst>>` keyed by basePath; `read(pattern)` does
  a **linear scan** with fURI pattern `test()`, dom/rng filters, `objs()` wrapping, QProc hooks — per resolution.
- `isa/mach/type/machine/SwarmMachine.java`: the monadic engine (a `VirtualThread`). The monadic loop (line ~274)
  handles split/barrier/batching/halted/zombie/fail-counting. ITERATOR must only be chosen where none of that machinery
  is needed.
- `isa/Space.java` `Helper.resolveApply` (line ~228): the dispatch seam — `rhs.isCode()` →
  `SwarmMachine.of(rhs.as()).apply()`. This is where strategy dispatch hooks in.
- `isa/Sugar.java` + `isa/m/parser/mParser.java` `addSugar`: the mechanism for `using(...)` sugar.

---

## 4. Where the time actually goes (cost model)

1. **Recompilation per execution** (biggest multiplier) — rewrite fixpoint + per-inst resolution each apply.
2. **Candidate fetch is unscalable** — linear scan + pattern tests + QProc hooks per resolution; the basePath read has
   no dom/rng so no narrowing.
3. **Elaboration on every candidate, every time** — bindGenerics + resolveArgs (with nested recursion) for all N
   candidates, zero memoization across identical (lhsType, name, arg-signature) triples.
4. **Expensive primitives in the loop** — `Obj.test()`/`testByID` structural tests with predicate evaluation; `T()`/
   `Type` reconstruction in bindGenerics; `specificTypeId` 2-6× per candidate; stream/record allocation per candidate.
5. **Rewrite fixpoint** — R rules × spaces applied to the full code, with re-entry into resolution inside rule bodies,
   re-run per execution.
6. **Double work at apply time** — even for compiled code, `apply()` re-resolves and re-applies args
   (`Helper.applyArgs`).

---

## 5. The architecture we converged on

### 5.1 Three-layer model

```
Code (description)                 — pure data: inst chain, args, tids
        │
        │  Strategy.compile(inputType, code)
        ▼
CompiledCode (artifact)            — resolved insts + per-slot metadata; immutable; later serializable
        │
        │  Strategy.execute(lhs, compiledCode)   (dispatches per framework)
        ▼
Obj (result)
```

The key move: a distinct **artifact** between description and execution. It gives compilation a cacheable, pure,
shareable result, and gives the caches a *structural home* (inside the strategy) instead of a commented-out static map.

### 5.2 Strategy selection: the Router is the registry (not a static singleton, not ThreadLocal)

- `InstResolver.get()/set()` is one mutable global: races, tests stomp each other, and `set()` can't compose.
  **ThreadLocal is worse**: execution hops threads (`SwarmMachine` spawns at barriers), so ambient state is lost.
  Selection must travel with the request as **data**.
- Therefore: strategies are **first-class objs in the Router** at `/strategy/*` (a `Rec` with compiler/executor/config
  fields). A `Map<fURI, Strategy>` *is* the Router, expressed in metatron's own substrate.
- Defaults: `/strategy/default` is a normal rebindable entry (registered by `BootLoader`; boot code may rebind it).
  Selection order: explicit URI on the request → default.
- Binding point = submit boundary (`Code.apply`, `Space.resolveApply`, or an explicit submit). Lookup is read-only →
  parallel workloads on different strategies with zero contention.
- Artifacts carry provenance: `CompiledCode` records the strategy URI + epoch. Executing re-resolves *its own* named
  strategy — coherent for "compile on one node, execute on another".

### 5.3 `using(uri, code)`

- `using(/strategy/abc).do(1.plus(2))` is **sugar / an ordinary inst**, not a keyword: it attaches the URI to the code
  obj and submits. `using(uri, code)` two-arg inst is the simplest v1; `.do()` sugar can wrap it.
- v1: whole-block granularity. Per-segment selection (nested `using` inside a larger block) is a v2 concern via the
  rewrite engine; the sugar must not promise segment-level control it doesn't have yet.

### 5.4 Frameworks vs strategies (the two-level model)

- **Strategy** (`/strategy/*`): full composition — compiler config, rewrite policy, caches, available frameworks.
  Selected per submit.
- **Framework** (`/framework/*` or enum constants): the execution unit for a *subtree* — `ITERATOR`, `SWARM` (current
  SwarmMachine semantics), later `VECTOR`, `DISTRIBUTED`. Stamped per node at compile time.
- **ExecutionStrategy shrinks to a dispatcher**: walk the artifact, hand each node to its stamped framework.
- Long-term: the artifact becomes a **tree** (`PlanNode` with compiled arg-subtrees), and the compiler becomes a
  **physical planner** (cost model: coefficients, barrier presence, nesting position, native-vs-interpreted, user
  annotations; validity constraints: a subtree with barriers/monads *must not* be stamped ITERATOR). This is the
  DB-planner analogy: rewrites = logical optimizer, framework stamping = physical planner, artifact = compiled plan.

### 5.5 Why the plan-tree/frameworks is sound

Observational equivalence: ITERATOR and SWARM must produce identical `Obj`s for any subtree where ITERATOR is valid (no
barriers, no monadic insts, no unbounded coefficients). Wrong guesses then cost performance, never correctness —
differential tests across (strategy × framework) enforce it.

---

## 6. Minimal v1 — signature-only spec

The v1 trim rule: **only seams are interfaces; mechanisms are internals.** The two seams are (a) strategy selection at
submit-time, (b) framework choice per subtree.

```java
/** One selectable composition. Immutable. Holds caches/rewrite policy as internals. */
public interface Strategy {
    fURI uri();                                    // /strategy/default, /strategy/seq, ...

    CompiledCode compile(Type inputType, Code code);

    Obj execute(Obj lhs, CompiledCode code);
}

/** The artifact: resolved code + per-slot framework stamps + provenance. */
public interface CompiledCode {
    Code source();

    List<Slot> slots();        // Slot: { Inst inst; fURI vid; Type dom; Type rng; Framework framework; }

    Type inputType();

    Type outputType();

    long epoch();              // router mutation version

    fURI strategyUri();
}

/** Execution unit per subtree. Enum, not interface — add a constant to add a framework. */
public enum Framework {
    ITERATOR,   // direct inst-by-inst loop on the calling thread; no machine, no threads
    SWARM;      // current SwarmMachine semantics
}

// Selection — no registry class; the Router IS the registry:
//   Strategy.at(fURI uri)  → Router.readFromSpace(uri) as rec with compiler/executor fields
//   /strategy/default      → rebindable entry, used when code carries no uri
//   using(uri, code)       → inst/sugar that attaches the uri to the code obj and submits
```

Notes:

- `Strategy` merges compile+execute (the `CompilationStrategy`/`ExecutionStrategy` split was the main overkill). Split
  them later via accessors; v1 callers only use the two methods.
- ITERATOR stamping in v1 is **purely shape-based, conservative**: a resolved call-arg subtree with no `isInitial()`, no
  gather/barrier, no monadic (`hasQ(MONAD)`) insts → ITERATOR, else SWARM. No cost model, no coefficients, no
  thresholds. When in doubt, SWARM.
- The ITERATOR fold is `obj = inst.apply(obj)` over the chain — `Inst.apply` already handles coefficients, fail
  propagation, and args. Must also handle `needsArgReapply` args and fail/catch semantics; stage it: start with
  trivially-safe subtrees (all-literal args), widen later.
- The stamp must ride on the *Code obj itself* (e.g., a field on `MCode`, default SWARM) so nested call args carry it
  naturally; `Code.apply` dispatches on the stamp. **Watch clone semantics** — the stamp must survive `MCode.clone`/
  `self`.
- `resolveCode` (from `InstResolver`) migrates into the default `Strategy.compile`, producing `CompiledCode`.

---

## 7. Staged implementation plan

| Stage                                                  | What                                                                                                                                                                                                                                                 | Effort (human)     | Risk                                                       |
|--------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------|------------------------------------------------------------|
| 1. Interfaces + default strategy, zero behavior change | `Strategy`, `CompiledCode`, `Framework`; `DefaultStrategy` wrapping the *existing* pipeline (rewrite + resolveCode + SwarmMachine); register `/strategy/default` in `BootLoader`; point `Space.Helper.resolveApply` and `Code.apply` at the dispatch | 2-3 days           | Low — pure extraction; the suite proves no behavior change |
| 2. Router-based selection + `using`                    | `Strategy.at(uri)`; `using(uri, code)` inst (+ optional `.do()` sugar)                                                                                                                                                                               | 1-1.5 days         | Low                                                        |
| 3. ITERATOR + stamping                                 | fold executor; stamp field on `MCode`; conservative stamping pass; `Code.apply` dispatch                                                                                                                                                             | 2-3 days           | Medium — clone semantics, monadic edge cases               |
| 4. Tests + hardening                                   | `using()` tests; ITERATOR↔SWARM equivalence matrix; default-passthrough; full-suite green                                                                                                                                                            | 1.5-2 days         | Low                                                        |
| **Total**                                              |                                                                                                                                                                                                                                                      | **~7-10 dev-days** |                                                            |

**Fully agent-driven:** ~4-10 h wall-clock engineering (optimistic 3-5 h, pessimistic 1-2 days if ITERATOR semantics
cause thrash); the verification loop dominates (compile 1-3 min, targeted tests 2-5 min, full suite 10-20+ min).
Parallelize: agent A does Stage 1 extraction while agent B writes the ITERATOR contract spec (from
`SwarmMachine.runMonadicLoop`, `Inst.apply`, coefficient/fail semantics) and agent C implements Stage 3 against it;
agent D does Stage 2 + Stage 4 tests. **But note §10: in this container the build env must exist first (it now does).**

**Cheapest possible v1** (interfaces + default strategy only): 3-4 days. **v1.1 caches** (artifact cache keyed by
code+inputType+epoch inside the strategy, router-version invalidation): +2-3 days — this is the actual "compilation
faster" payoff.

---

## 8. Future growth (no interface churn)

| Version | Adds                                                                                                                                         | Interface impact                                                                  |
|---------|----------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------|
| v1.1    | artifact cache inside the default strategy (code+inputType+epoch, epoch-guarded)                                                             | none                                                                              |
| v2      | per-node plan tree + `chooseFramework` cost model (coefficients, inst counts, native-vs-interpreted, nesting, user annotations)              | none — tree is a richer artifact; `compile` signature unchanged                   |
| v3      | framework as interface (stateful: pools, vector, distributed); stats/controllable as capability interfaces; per-segment `using` via rewrites | `Framework` enum → interface is local to strategies; `Strategy` surface untouched |
| v4      | persistent/AOT compiled artifacts; remote execution ("compile once, ship the plan")                                                          | `serialize()` on `CompiledCode`                                                   |

---

## 9. Related prior work & docs

- `docs/design/inst-resolution-design-insights.md` — prior session on V2InstResolver: domain-check tradeoffs,
  generics-binding with noobj, as () scoring, metaprogramming failure clusters. Read before touching the resolver
  internals.
- `docs/design/rewrite-planner.md`, `docs/design/rewrite-roadmap.md` — rewrite engine; relevant when the rewrite policy
  becomes a strategy-owned sub-component.
- Disabled cache history lives in `Inst.java` (comment at ~line 236): the rewrite→apply→resolve cycle is the thing any
  cache design must structurally avoid (compile-depth guard: never cache results produced at compile-depth > 0;
  type-level keys, never value-level; invalidate on router write via an epoch/version counter).

---

## 10. Build environment (this container — verified working 2026-08-15)

The container's root filesystem is **read-only** (`/` is `ro`; `/usr`, `/opt`, `/root`, `/home/killswitch` unwritable;
`/tmp` is ephemeral — files vanish between commands). The toolchain therefore lives inside the repo under `.build/`
(git-ignored by the `.*` rule):

- **`./mvnw` is patched to self-configure — just run it.** Falls back to `.build/jdk` (Temurin 24, matching CI) when
  `JAVA_HOME` unset; Maven caches under `.build/m2/`; passes `.build/m2/settings.xml` (local repo redirected into the
  workspace). No env setup needed.
- **JDK 24, not 21, is the default** — the pom's surefire `argLine` includes `--sun-misc-unsafe-memory-access=allow`
  (JDK 23+ only); JDK 21 rejects it and test forks crash. `.build/jdk21` exists for reference. This is why CI uses JDK
  24 / jDeploy 25.
- **`bin/metatron` is patched** the same way (uses `./mvnw` and `.build/jdk/bin/java`).
- Verified: `./mvnw install -DskipTests` → BUILD SUCCESS (2:24 min); targeted test classes green
  (InstTest/CodeTest/cIntTest = 201 tests; resolver tests = 147 tests); `bin/metatron --headless` launches.
- **Full-suite runs need exclusions**: container-dependent tests (MySQL/PostgreSQL/MariaDB) need Docker; iterate with
  `-Dtest='!httpSpaceTest,!fsSpaceTest'` + targeted classes.
- **Background bash jobs are unreliable** and `/tmp` doesn't persist — stage downloads inside the workspace, prefer
  foreground with generous timeouts.

---

## 11. Open decisions for the user (resume here)

1. **v1 scope**: interfaces-only (3-4 days) vs full v1 incl. `using` + ITERATOR (7-10 days)? Recommend the latter —
   ITERATOR is the win the user actually wants.
2. **`using` syntax**: `using(/strategy/abc, code)` two-arg inst vs `using(/strategy/abc).do(code)` sugar (or both:
   inst + sugar).
3. **Missing-URI policy**: explicit URI that doesn't resolve → fail loudly; default fallback → warn. Decide once,
   document in the inst's docq.
4. **Epoch mechanism**: router mutation version counter bumped on writes; all caches keyed by it. Confirm where the
   counter lives (Router static vs BootLoader).
5. **JDK 21 as default?** Requires dropping `--sun-misc-unsafe-memory-access` from the pom (a project change) or
   accepting JDK 24 as the container standard (recommended, matches CI).
6. **V2 cost model thresholds** — defer until v1 is shipping and profiled.

---

## 12. Validation strategy

- **No-behavior-change proof**: the default strategy must pass the entire existing suite (131 test files, 433+
  `@CsvSource` blocks) unchanged.
- **Equivalence matrix**: same mtron expression compiled under different strategies / stamped with different valid
  frameworks must produce identical `Obj`s — a perfect fit for the existing `@ParameterizedTest`/`@CsvSource` style.
- **Structural regression guard, not just wall-clock**: add counters (compiles per code identity, resolves per inst,
  framework executions) so tests can assert e.g. "same code applied 1000× ⇒ 1 compile, 1000 executes" and "nested
  non-monadic arg ⇒ 0 SwarmMachine creations".
- Existing `InstResolverBenchmarkTest` (currently `@Disabled`; run manually) is the wall-clock baseline.
