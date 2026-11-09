# Fibered `rec::T` — review, recommendations and ideas

**Status:** review written 2026-11-09, after the lift landed on main (`c540e782b`, `cbdac5dc3`, `60a15e326`).
**See also:** `docs/skills/mtron/references/type-system-mtron.md` (*lifting*),
`docs/skills/metatron/references/machine-architecture-java.md` (*the processor*), `FiberedRecTest`, `RecTest`
(delegation rows); Hindsight: *Ruling: a fibered rec keeps its lift*, *Ruling: a type-changing call on a fibered rec is
a projection*, *Findings: fibered rec behavior after the lift landed*.

Throughout, **observed** means a test row or a probe on ginger showed it; **proposed** means it is an idea that has not
been built or run.

---

## 0. Thesis

A fibered rec is a **product**: the base (its fields that are no fibers — `name`, `age`, `currency`) times its fibers
(`role`, `hp`, `amount`). A call on it is about one of two things:

- **the bundle itself** — a typer, a generic domain, the rec form, or the bundle's own name. It runs on the bundle
  alone, as any instruction runs on any rec.
- **its fibers** — any other domain. It runs on every fiber it is about (the *parallel fibers*), and the answer is the
  bundle with those fibers moved (the **lift**), unless a fiber's type changes, in which case the answer is the
  instruction's results (the **projection**).

Before the refactor the processor fanned a call out over `{bundle, fiber₁, fiber₂, …}` and summed the results into a
multiplicity. A sum is a *coproduct* (a stream of alternatives), so the moment one fiber answered, the bundle was gone —
`person[int]::[name=>'marko',age=>29].plus(1)` answered `30`. Rebuilding a product needs to know which result came from
which fiber and what the base is; only the bundle knows that, which is why the lift had to leave the processor.

The functor reading makes the rules predictable. A fibered declaration is a polynomial functor in its fiber types: the
base is the constant part, each fiber a variable position. Mapping `f : A → A` over `person[A]` gives `person[A]` back
(the lift); a type-changing `f : A → B` with `B ≠ A` has no `person[B]` to land in under Marko's ruling, so it projects.

---

## 1. The rules as built

Marko's rulings, 2026-11-09:

> Keep the fibered rec lifted, don't plus(1) return 30 — have it return the fibered rec.
>
> If the fiber changes types, then consider it a projection and don't return the rec::T, return the result of the
> instruction(s).

| a call that … | answers | example (observed) |
|---|---|---|
| is about the bundle itself (typer, generic domain, rec form, own name) | the call on the bundle alone | `*kai>>name` ⇒ `'alpha'`; `*kai>>rank` ⇒ `noobj` (only the fiber has a rank) |
| reaches fibers and every result keeps its fiber's type | **lift**: the bundle with those fibers moved | `person[int]::[…,age=>29].plus(1)` ⇒ `person[int]::[…,age=>30]` |
| reaches fibers and one result changes type (type, multiplicity, or `noobj`) | **projection**: the results, summed | `person[int]::[…].gt(18)` ⇒ `true`; `*twin.rank()` ⇒ `{11,3}` |
| reaches fibers of different types | each fiber runs its **own overload** | `bump` on int and str: `tagged[int,str]::[hits=>1,label=>'hi'].bump()` ⇒ `[hits=>2,label=>'hi!']` |
| is about no fiber | `noobj` (a miss, not a failure) | `ex::[o=>12].plus?<=str('x')` ⇒ `noobj` |
| fails on a fiber it applied to | the fail propagates | a str `power` under `rank: ninja ⇒ int` ⇒ `<ERROR>` |

Further rules, each pinned by a test:

- **One level deep.** A fiber resolves the call against the object it holds, so a bundle held at a fiber is applied to
  as itself and never lifted again. One `>>` makes it the bundle.
- **`@` writes back, `*` does not.** A lift keeps the address its bundle was read with, and a result that carries an
  address is written back, so `@marko.hurt(23)` is a state transition (hp 100 → 77 → 54) while `*marko.hurt(23)` is a
  value. A projection writes nothing back. Fibers moved by one call are written back together (`@px.plus(10)`), in
  one write (§5.5).
- **Fields that are no fibers ride along** (`currency`, `device`, `tag`), and the bundle's multiplicity is kept
  (`{2}person[int]` stays `{2}`).
- **Untyped reads are the bundle's own.** `*marko>>weapon` is `noobj` when only the ninja carries a weapon; naming the
  fiber's type reaches it (`*marko>>?<=ninja(weapon)`). This replaced the old "both levels answer, summed" reads.

---

## 2. Where it lives

```
mtron call on a value ─► Processor.Helper.apply ─► Selector.Helper.resolve ─► FiberedRec.Helper.resolve(Obj, …)
                                │                                                   │
                                └──── invoke(subject, <the bundle's own inst | the LIFT>) ◄─┘

mtron code compiling ─► Resolver.Helper.resolveCode ─► inst.resolve(type token)
                                │                          │ (a binding: hasf())
                                └──────────────────────────┴─► FiberedRec.Helper.resolve(Type, …)
```

- **`FiberedRec`** (`isa.m.type`) — the contract: `fibers()` and `lift(inst)`. Its `Helper` owns the questions:
  `isBundle(Obj)` (a rec value at run time), `isBundle(Type)` (a bundle's type at compile time), `resolve(Obj|Type, …)`
  (the seam), `applicable` (is the call about this subject), and the private `lift(inst)` that builds the lifted
  instruction.
- **`MFiberedRec`** (`isa.m.type.impl`) — the wrapper. `fiberedRec(rec)` wraps a bundle's fields with no copy, no
  check and no write; `lift` runs the fibers, decides lift-or-projection per fiber, and rebuilds the bundle.
- **The processor knows nothing of fibers.** `Processor.Helper.apply` is resolve-then-invoke. The lift calls
  `Processor.Helper.invoke` once per fiber — the dependency points from the fibered rec to the processor, never back.
  It uses `invoke`, not `apply`, because `apply` re-enters resolution and would lift a bundle-valued fiber again.
- **The lifted instruction** is `name?rng=#{*}&dom=#` (built with `instC` from `dom(ALL).rng(ALL_STAR)`), and it
  carries the **call, unbound**:
  - `dom=#` (exactly one obj): a `{*}` domain would make the swarm treat it as a gather (a barrier), and a `{?}` one
    would let dead monads walk into it;
  - `rng=#{*}`: the call answers the bundle or a projection, so later calls resolve at run time;
  - shipped without its Java function, it is resolved again against the bundle where it lands, and the seam lifts it
    again (read from `Selector.Helper.resolve`; not yet run across peers).
- **Compile time asks the same question.** A literal bundle is compiled against its type, which accepts a fiber's
  overload; `Resolver.Helper.resolveCode` passes every binding through `FiberedRec.Helper.resolve(Type, …)`, so such a
  call compiles to the lift instead of to the one overload the type happened to bind. This matters because a bound
  user-defined overload has lost the call: its tid is `/m/inst?dom=/m/int` and binding strips its address, so nothing
  downstream could re-resolve `bump` for a str fiber.

Removed in the refactor: `Processor.Helper.applyFiberedRec` (the candidate loop), `Typer.Helper.bundleAndFibers`,
`fibers` and `supersedes` (the candidate list and the "bundle speaks first" check — now the general rule), the swarm's
`invoke`-instead-of-`apply` halt workaround, and the dead `Type.recFiberIndex`.

---

## 3. What is verified

`FiberedRecTest` (46 rows, all passing on ginger):

| method | pins |
|---|---|
| `testTheLiftKeepsTheBundle` | Marko's example, chains, `{2}` bundles, parallel same-type fibers, money and sensor-reading envelopes |
| `testTheParallelFibersEachTakeTheirOwnOverload` | per-fiber overloads on a read and on a literal, chained, a mixed projection (`{2,true}`), three fibers |
| `testAnAnchoredLiftWritesBack` | `@` versus `*`, a projection writes nothing back, two fibers written in one write |
| `testTheBundleTakesItsOwnInstructions` | a typer (`type()`), the bundle's own name (`muster`), generic reads (`>>name`), the rec form (`plus` of a rec merges into the bundle) |
| `testTheLiftIsOneLevelDeep` | a bundle at a fiber is not lifted again |
| `testATypeChangingCallIsAProjection` | type change, filter that drops one fiber (`noobj` is a type change), fails propagate |
| `testWhereTheLiftDoesNotReachYet` | the open gaps of §4, pinned at today's answer with a `TODO` |

`RecTest` carries Marko's earlier delegation worlds (person/ninja, duo, dojo, herd, human/geo, hiker), updated for the
lift and the bundle-first reads; `TypeTest` covers acceptance, which the refactor did not change.

Verified broadly on ginger after the last change: `FiberedRecTest`, `RecTest`, `TypeTest`, `RelTest`, `catInstSetTest`,
`mInstSetTest`, `InstTest`, `CodeTest`, `LstTest` — 3,462 tests, the only failures being three that main already had
(appendix).

---

## 4. Open gaps

Each is pinned in `testWhereTheLiftDoesNotReachYet`; a fix flips its row.

### 4.1 A bundle passed as an argument is not lowered *(observed)*

`5.plus(*marko)` fails: *unable to convert rec[age]::T[…]@person[int]*. `Type.test`'s fiber acceptance (covariance:
a fibered value is acceptable where its base or any fiber is expected) admits `person[int]` where an int is expected,
but nothing hands the int over, so the body receives the rec.

**Proposed:** lower at resolution, next to the lift — an argument accepted only through a fiber is replaced by that
fiber's value (a projection at the argument boundary). That keeps `Processor.Helper.applyArgs` free of fibered code.
The alternative, refusing such arguments, would contradict the covariance ruling.

### 4.2 An instance without a bracket does not lift an untyped call *(observed)*

`person::[name=>'marko',age=>29].plus(1)` fails while `person::[…].plus?<=int(1)` lifts. `Type.recFibers()` is empty
without the instance bracket, so the call binds no int overload, and `Inst.resolve`'s fallback types the call itself
at the bundle (`this.dom(lhs.type())`, i.e. `plus?dom=person`). The bundle owns its own name, so the call runs on the
bundle as a rec `plus`, where the int argument fails to convert: *unable to convert int::T to rec::T*. (The error is
observed; the path to it is read from the code, not traced.) Typed `?<=int`, the call names a domain the bundle does
not own, so it lifts onto `age`.

**Proposed:** when the instance names no bracket, `recFibers()` falls back to the declaration's field types (the
private `Type.Helper.declaredField` already reads them for `testFibration`), so `person::[…]` behaves like
`person[int]::[…]`. Small and contained.

### 4.3 A fiber holding several values answers `noobj` *(observed)*

`*rambler.plus(1.0)`, with `rambler` a `hiker[real{2}]` whose `rec[geo/+]` fiber holds `{2.0,3.5}`, answers `noobj`
silently. The answer is the empty sum, so the call ran on no value at the path; and had it run, a patterned path is
never written back (`held` requires `!fiber.hasPattern()`), so the call would project rather than lift.

**Proposed:** an elementwise lift — map each value at the path and write the lst back
(`hiker[real{2}]::[…,geo=>trail::[3.0,4.5]]`), the bundle functor composed with the stream functor. At the least, make
it a loud miss rather than a silent `noobj`.

### 4.4 A fiber named like an instruction cannot be reached *(observed)*

`counted[int]::[count=>1].plus(1)` answers `noobj`, and even a plain `counted[int]::[count=>1]>>count` fails: the
declaration's bracket `rec[count]` resolves `count` through the instset prefixes and reads `rec[/m/inst/count]`, so the
bundle has no fiber at `count`. This predates the refactor; it surfaced when a probe used `count` as a field name.

**Proposed:** a rec bracket's entries are field names (and paths), not references, so they should not be resolved
through the prefix table when the declaration is built. Failing that, reject such names loudly at declaration.

### 4.5 Compile-time typing of the lift

The compile-time lift answers `#{*}`, so the next call in a chain resolves at run time. That is correct but blind:
`explain()` cannot say *lift* or *projection*, and type errors after a lift surface late.

**Proposed:** resolve the call against each fiber's TYPE at compile time (`recFibers()`, or the declared field types),
then type the lift as the bundle when every fiber keeps its type and as the sum of the fiber ranges when it projects.

*Correction:* the `c540e782b` commit message states that the compiler types `person[int].plus(1)` as `int`. That was
inferred from reading the resolver, not observed, and `60a15e326` since compiles such calls to the lift (`#{*}`).

### 4.6 `noobj` or the identity when no fiber applies *(design question)*

A call about no fiber answers `noobj`, the multiplicity's identity — a miss, not a failure. For container-like
bundles the identity *on the bundle* may serve better: a `result[int]::[err=>'boom']` with no `ok` fiber would pass the
error through a pipeline unchanged instead of vanishing. Worth deciding before Option/Result-shaped bundles become a
pattern (§6).

### 4.7 A zero-coefficient domain on a bundle *(design question)*

`*marko>>?<=ninja{0}(name)` answered `<ERROR>` before `60a15e326` and answers `noobj` now. Resolved per fiber, a
domain of zero ninjas fits no fiber — a miss, like `ex::[o=>12].plus?<=str('x')`. Before, the compiler's bound
instruction was forced onto the ninja and failed its coefficient check. If a zero-coefficient domain should be loud on
bundles too, that is one rule in `FiberedRec.Helper.resolve`.

---

## 5. Recommendations beyond the gaps

### 5.1 Acceptance is not identity — audit `test()` on a bundle's own shape *(observed in the lift; inferred for construction)*

Covariance makes `bundle.test(T)` true when **any fiber** is a `T`. Two consequences surfaced during this work:

- The first lift implementation checked "can the bundle hold this result" by testing the rebuilt bundle against its
  own type; a `duo_person` whose `role` fiber is itself a `duo_person` passed even with `position=>'outer_title'`
  (its declaration demands a rec). The lift now asks the question per fiber, of the value the fiber held.
- The same `test()` is the construction check (`Obj.Helper.objTypeCheck`, called by `objCheckAndSave`, runs
  `obj.test(obj.type())`), so a bundle whose own fields violate its declaration should be **constructible** when one
  of its fibers happens to be of the bundle's type. *Inferred from the code, not yet observed.*

**Proposed:** when an object is checked against **its own** type, fiber acceptance should not apply — a bundle *is*
its declaration; acceptance is for being passed where a fiber's type is expected. A `RecTest` row constructing that
`duo_person` with `position=>'outer_title'` would first settle whether the construction check is fooled, then pin
the fix.

### 5.2 Fuse consecutive lifts *(proposed)*

The functor law (lifting `f` then `g` equals lifting `g ∘ f`) lets a rewrite turn `.hurt(23).hurt(23)` into one lift
of the composed code: one rebuild and, on an anchored bundle, one write instead of two (one SQL `UPDATE` on a
space-backed row). The fiber's own laws would hold inside the fused code, so `catRewrite`'s rules carry over:
`neg().neg()` (involution) and `plus(0)` (unit) rewrite away there as they do on a bare int (`catInstSetTest`). Caveat
for `@`: fusing merges two writes into one, so a `?subq` listener sees one event instead of two; safe for `*`, a
semantic choice for `@`.

### 5.3 Run the parallel fibers in parallel *(proposed)*

Fibers are independent, so a lift is a fork/join. Today they run one after another inside the lifted instruction. The
swarm could give each fiber its own monad and rebuild the bundle at a barrier; this pays when a fiber's call is
expensive (an LLM call per fiber, a remote read, a fiber that holds a reference to another peer's data).

### 5.4 Show lift and projection in `explain()` *(proposed)*

A compiled lift is an ordinary instruction (`name?rng=#{*}&dom=#`) that says nothing about what it will do. Once §4.5
lands, `explain()` and `profile()` can label each call *own*, *lift* or *projection*, and name the fibers it moved.

### 5.5 State the write guarantee *(worth documenting)*

All fibers moved by one call are written back together (observed: `@px.plus(10)` stores both moved fibers). From the
code, that is one write: the lift moves the fibers in a scratch rec and clones the bundle once at its address. So a
lift is one write per call — one update to a tble, dcmnt or grph space once bundles live there.

---

## 6. Ideas and use cases

- **Units and currency** *(observed)* — `money[real]::[amount=>9.99,currency=>'USD'].mult(2.0)` ⇒
  `money[real]::[amount=>19.98,currency=>'USD']`, and `.gt(5.0)` projects to `true`. Arithmetic cannot strip the
  unit, because the unit is a field that is no fiber.
- **Telemetry with provenance** *(observed)* — `reading[real]::[value=>21.5,device=>'thermo-7'].mult(1.8).plus(32.0)`
  ⇒ `reading[real]::[value=>70.7,device=>'thermo-7']`. MQTT or Home Assistant readings keep `device` and a timestamp
  through every stage of a pipeline; a threshold check projects to a bool.
- **Stateful entities** *(observed for `@`/`*`; `?subq` proposed)* — Marko's console demo: `@marko.hurt(23)` with
  `hurt = |inst?int<=int(int::T){ minus(*0) }` takes `hp` 100 → 77 → 54. `@` is a state transition, `*` a what-if
  preview, `{n}` a squad taking the same hit. A `?subq` on the entity's address turns every write-back into an event
  a HUD or an agent reacts to.
- **Space-backed records** *(proposed)* — anchor a bundle in a tble, dcmnt or grph space and a lift is a typed
  in-place update of that row, document or vertex (`@inventory/sku7.plus(1)` increments `qty`). `grphInstSet` already
  turns a run of instructions into one `gremlin(...)` call; a lift on a vertex property could become one `property()`
  step in it.
- **Agent messages** *(proposed)* — `message[str]::[role=>'user',content=>'…']`: redaction, templating or trimming
  lift onto `content` and keep `role`; a token count projects to an int.
- **The category graph** *(proposed)* — project notes from 2026-10-21 name `liveness[law]` (catInstSet liveness
  keyed by law, `liveness[involution]::T`) as the planned first real user; nothing declares it yet. `object[int]` and
  `morphism[…]` already print with a bracket but are not bundles: in `catTypes`, `category` is
  `rec[obj]::T@category`, while `object` and `morphism` have tid `category`, which is no rec bracket. Separately, a
  morphism graph (the `?morphq` initiative recorded in Hindsight; not in the code) could list **lifted morphisms**:
  every endomorphism of a fiber type induces one on each bundle over it (`hurt : int → int` induces
  `human[int] → human[int]`), so the graph would show what an entity can do through its fibers.
- **Option/Result containers** *(proposed, depends on §4.6)* — `maybe[int]::[value=>5]`, `result[int]::[ok=>5]`:
  the lift is `fmap`, the projection the exit. With `ok{?}` declared optional and §4.6 answered with the identity, an
  error-shaped value passes through untouched.

---

## 7. Lessons from the refactor

- **A sum cannot hold a product.** The old fan-out could only answer a multiplicity; any design that keeps the bundle
  needs the bundle's structure, so the semantics belong on the rec, not in the processor.
- **Acceptance is not dispatch, and not identity.** Covariance answers "may I pass this here"; it must not answer
  "which object does this call run on" (dispatch) or "is this object valid as itself" (§5.1).
- **Lift where the call is still a call.** Once bound, a user-defined overload carries neither its name nor its
  address. Every place that binds a call against a bundle — run-time resolution and compile-time resolution — must
  hand over the lift instead of a fiber's overload.
- **Reads compile against the stored type.** `*x` resolves its range from the stored value
  (`SpecificitySelector`'s fast from/at path, `fromOrAtObj.typeId().maybeSome()`), so compile-time resolution sees a
  bundle's type after a read too.
- **A type cannot be overwritten.** A test world that redefines a type writes `X -> noobj` first; otherwise a
  differently-shaped `X` from another test method in the same class silently wins.

---

## Appendix — failures seen during the work that are not fibered

Present on main before the refactor (Marko's own 09:44 surefire report shows the same three), deterministic, and
failing when run alone:

- `RecTest.testMatches[1]` — `[=>]` versus `[a=>b]` answers `true`; `checkMatches` is a pure `Rec.test`, whose clause
  accepting an absent key with a value on the right lets it through.
- `TypeTest.testTyping[30]` — `[name=>'a',age=>1,b=>noobj].as(person::T[?[b=>is(gt(0))]])` answers `true`.
- `RelTest.testNamedPathType[2]` — long known.
