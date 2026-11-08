# Handoff — fURI coefficients: the model, the encoding, the rulings, the open seams

> Date: 2026-11-08. Main at `11e50b25f`. **Agents never run git**; Marko does (commit in the worktree with a message
> file in `.docker-target/`, `rebase main` there, `merge --ff-only` on main). Build and test only on ginger:
> `bin/metatron-remote --host ginger.local build test -Dtest=…`, focused classes, read `Tests run:`.

## Why this exists — read first

Marko opened the 2026-11-08 session to **discuss an addition to the specification of coefficients**, after an agent
had studied them. The study turned up real errors in the coefficient arithmetic, and the session went into fixing them
(two commits, below), so **the addition was never discussed**. Start the next session by asking Marko what the addition
is. Do not guess it. Two directions he raised along the way, for context only:

- **Complex coefficients and quantum physics.** He asked what the product algorithm would be for ℂ coefficients
  modelling superposition and interference, and required the coefficient math to be "consistent with quantum physics"
  (§2, §9.4).
- **Negative coefficients.** "We have not scratched the surface of using negative coefficients in mtron yet. 99% of the
  code assumes positive coefficients." He deferred that work ("let's deal with `#{**}` another time", §9.1).

What landed on 2026-11-08:

| commit      | what                                                                                                                   |
|-------------|------------------------------------------------------------------------------------------------------------------------|
| `5ebf0c7bf` | `cInt.mult` is the interval product; `plus` keeps unbounded bounds unbounded; set-wise `abs`; `ANTIMAYBE()` = `{-1,0}`; `div` by a zero bound throws `MTronException`; `Obj.take` rule |
| `11e50b25f` | `lt/gt/lte/gte` are the each-bound order (in `C`); `compareTo` is a total order; `div` by a negative divisor; `C.mirror` fix; SKILL.md example |

## 1. The model

Stream Ring Theory (`docs/website/articles/stream-ring-theory.md`, Definition 2 and §"The Coefficient Ring") gives
every stream object (`cx`) and every function (`ca`) a coefficient from a ring with unity — ℤ in the examples, but ℝ,
ℂ and matrices are allowed (its quantum-computing section uses ℂ^{2×2}). Three axioms do the work: **bulk**
`⟨cx,dx⟩ = ⟨(c+d)x⟩`, **apply** `⟨cx⟩da = ⟨(c·d)a(x)⟩`, **zero** `⟨0x⟩ = ⟨⟩`. Reducers are coefficient-aware and drain
the stream (Thm 16); a coefficient floats through map/filter/flatmap (Thm 6) and not through a reducer (Thm 17).

metatron implements that ring as `cInt` and **lifts it to ranges over ℤ ∪ {±∞}**. One slot, two roles:

- an **exact** `{n}` is a value's multiplicity — the ring element (`{3}5` is three 5s);
- a **range** `{a,b}` is a type's set of admissible multiplicities (`int{2,5}::T`).

Type membership is containment (`within`). Everything else — absence, instruction forms, barriers, optional arguments,
poly positions — is read off that one range.

## 2. The algebra: `C<T,D>` and `cInt`

`C<T extends Comparable<T>, D>` (`furi/C.java`) is the coefficient interface; `cInt` (`furi/c/cInt.java`, `Long`
bounds, `null` = unbounded) is its **only** implementation. Notation (`docs/website/adoc/c.adoc`):

| spelling | range | name | | spelling | range | name |
|---|---|---|---|---|---|---|
| `{0}` | [0,0] | noobj | | `{**}` | (−∞,∞) | polar maybe some — the universal coefficient |
| `{1}` (never written) | [1,1] | an obj | | `{??}` | [−1,1] | polar maybe |
| `{-1}` | [−1,−1] | anti-obj | | `{-?}` | [−1,0] | anti-maybe |
| `{?}` | [0,1] | maybe | | `{-}` | (−∞,−1] | anti-some |
| `{+}` | [1,∞) | some | | `{-*}` | (−∞,0] | anti-maybe some |
| `{*}` | [0,∞) | maybe some | | `{n}` `{n,m}` `{,m}` `{n,}` | exact, range, half-unbounded | |

Operations on main (`11e50b25f`):

| op | semantics | examples |
|---|---|---|
| `plus` | interval addition; unbounded stays unbounded | `{**}+{1}` = `{**}`, `{*}+{-*}` = `{**}`, `{3}+{-3}` = `{0}` |
| `neg` | `[−max, −min]` | `{-}` ↔ `{+}` |
| `mult` | **interval product**: the four bound products, min to max, `0·∞ = 0` | `{2,3}·{-3,-2}` = `{-9,-4}`, `{-}·{-}` = `{+}`, `{+}·{0}` = `{0}` |
| `div` | **mult's inverse** (the X with X·rhs = this); a negative divisor goes through `this.neg().div(rhs.neg())`; a zero bound throws; pinned conventions `0/0 = 0`, `∞/∞ = 1` | `{50,100}/{5,10}` = `{10}`, `{4,10}/{-2}` = `{-5,-2}` |
| `abs` | set-wise | `{-}`→`{+}`, `{-*}`→`{*}`, `{??}`→`{?}`, `{-2,3}`→`{0,3}` |
| `within` / `contains` | subset / superset — **the typing relation**; `isZeroable()` = 0 ∈ c | |
| `span` | convex hull (used by LCD) | |
| `lt/gt/lte/gte` | **each-bound partial order** (`C.lte`): A ≤ B when A.min ≤ B.min and A.max ≤ B.max | `{0,10}` vs `{1}`: neither |
| `compareTo` | lexicographic total order (min, then max), for sorting only | |

Why the interval product (Marko, "consistent with quantum physics"): two coefficients that meet are independent, so
every pairing occurs — the cross terms of a tensor product — and a range must hold every product. Bound-wise
multiplication keeps only the diagonal, which describes an *entangled* (correlated) pair, not a product. On
non-negative ranges the two agree. A quantum product expands every cross term and then **adds** same-object terms
(interference can cancel); an interval product expands every cross term and then takes the **hull**. In polar terms
(magnitude × phase), ℤ is the slice of ℂ with phases {0, π}: magnitudes multiply bound-wise, phases add.

Algebraic status (`AbstractCTest` flags in `cIntTest`): exact `{n}` = the ring ℤ; non-negative ranges = a semiring;
mixed-sign ranges are not distributive (interval arithmetic). Quirks worth knowing: `C.inv()` is the additive anti
(−[a,b]), not a multiplicative inverse; `isExact()` is true for `{**}` (`null == null`); `most()` of an unbounded max
is `{**}`.

## 3. Encoding in fURI

- **One `cInt` per fURI, a suffix after the path and poly:** `scheme://host:port/path[poly]{c}?rng<=dom&query`
  (`fURI.java` `COEFFICIENT_BODY` :1414, `FURI_PATTERN` :1420). `{1}` is never rendered and is stored as null (the
  coefficient-less form classes in `furi/form/`); a bare `{0}` parses to `NOOBJ` = `noobj{0}` (:1451).
- **Nesting:** dom, rng, args, poly elements and branch elements are fURIs with their own coefficients
  (`order?rng=/m/lst[/m/int{*}]&dom=/m/int{*}`).
- **Segment positions:** `a/{2}c` is a *coefficient-marked atom* (branch bookkeeping, absorbed when branches expand,
  `fURI.Helper.branch` :1291); `a/{{2}c,d}` is an *in-branch element* (expands to n copies, `expandElement` :1345);
  `<{0}a>` leads a quoted uri.
- **fURI is a module over `cInt`** (`AbstractfURI`): `neg` negates c; `plus` (:689) adds c on equal paths and factors
  unequal ones into a branch (`a/b/c{2,3} + a/d/c{-3,-2}` = `a/{{2,3}b,{-3,-2}d}/c`; different schemes give `#{sum}`);
  `mult` (:664) concatenates paths and multiplies c; a `{0}` factor gives `NOOBJ`.
- **`fURI.test` checks the coefficient first** (`testHarness` :478): `c.within(d)`, and a zero lhs passes any zeroable
  rhs without looking at the path. `basePath()` = `cLess()` + `qLess()` + `polyLess()`; `equals`/`hashCode` include c.
- **Three grammars must agree:** `cInt.of(String)` (:105) / `cInt.toString()` (:326), `COEFFICIENT_BODY`, and
  `mParser.m_furi_coefficient()` (:1199). `m_furi_poly_type()` (:1121) stores a poly element's leading `{n}` as a
  marked atom; `TypeCoefficientParser` validates `X{…}::T`.

## 4. Where an obj's coefficient lives

`Obj.c()` is `tid().c()` (`Obj.java` :134); setting `{1}` stores null on the tid.

| obj | spelled | where c sits |
|---|---|---|
| value | `{3}5` ≡ `int{3}::5` | tid `/m/int{3}` |
| base type | `int{3}::T` | tid = vid = `/m/int{3}` |
| named type | `nat{2}::T` | tid `int{2}`; the vid (the name) stays bare |
| inst | `plus{2}(1)` | inst tid; multiplies the output (`5.plus{2}(1)` = `{2}6`) |
| code | — | nested in the tid's `dom=` (first inst) and `rng=` (last inst) |
| uri value | `{4}e` vs `<e{4}>` | **two slots**: obj c (`cc()` = 4) vs the address content's c (`cc()` = 1) |
| bag (`Objs`) | `{1,1,2}` | members bulk (`{{2}1,2}`); tid = Σ member tids (`MObjs.tid()` :304), c = Σ member c |
| zero | any `X{0}` | `isNoObj()` (:398); all zeros are equal and hash alike |

`type()` is a homomorphism: `{3}5.type()` = `int{3}::T` ("three copies of `int::T`" and "the type of three ints" are
one obj). Only a canonical (c = 1) type auto-registers (`MType` :48); `T()` of a `{0}` tid or vid is `NOOBJ_TYPE`.

## 5. Type checking

- `Obj.Helper.testObjs` (:963): noobj passes any zeroable type; a nominal type checks `within` then `testNominally`;
  otherwise `lhs.c().within(rhs.c())` (:1031) before `Type.Helper.typeCheck` (`Type.java` :889).
- `{3}5` passes `int{*}`, `int{2,4}`, `int{+}`, `int{3}` and fails `int`, `int{4}`, `int{?}`; `5` passes `int`,
  `int{?}`, `int{*}`, `int{+}`, `int{-1,1}`.
- LCD takes the `span`; a field present in only some records relaxes to `{0,max}` (`Type.java` :657).
- Generics: `A{*}` binds A **without** its coefficient and keeps the API's (`Inst.Helper.apiOrUser` :1173).
- Argument dispatch is coefficient-blind (`1.plus({2}3)` = 4). Defaults are coefficient admission: `order(B{?})`
  admits `order()`.

## 6. Instructions

- **Form comes from dom/rng coefficients alone** (`Inst.java` :501–535): initial dom `{0}`; terminal rng `{0}`; map
  1→1; filter 1→`{?}` (same base); flatmap 1→ more than 1; scatter 1→ unbounded max; gather dom max unbounded (a
  barrier); reducer gather→`{1}`. Floatable = map/filter/flatmap; blocked = reducer/gather.
- **Runtime = the apply axiom** (`Processor.Helper.invoke` :201): a unique bulked lhs whose c doesn't match the dom is
  compressed to c = 1, applied once, and multiplied by `lhs.c()` and the inst's c (`{3}5.plus(1)` = `{3}6`).
- **Compile time** (`Resolver.Helper.resolveCode` :177): the token is the resolved rng (a filter's `{0,1}` trimmed) ×
  the inst's c, so after a per-object map the token has lost the upstream count (`.explain()` shows `c_dom`/`c_rng`).
- `appliesPerObject` (`Inst.java` :676) = dom `{1}` and `lhs.c().gt(ONE)`: `{2}`, `{+}`, `{1,7}` are bulk; the
  may-be-empty `{*}`, `{?}`, `{0,500}` defer to runtime. **Ruling 11-07: the deferral is load-bearing** — making it
  range-aware broke 36 tests.
- `split` (`BasicSwarmComputer` :467) takes `dom.most()` when `obj.c().gte(dom.c())`, else everything. Since
  `11e50b25f` a `{2}` no longer "fills" a `{1,3}` batch (it used to take 3 and leave a `{-1}` anti-object:
  `{2,2}.prod?int<=int{1,3}()` gave `{8, fail::['negative exponent']}`; now 4).
- Reducers read coefficients: `count` sums `c.max()`, `{3}5.sum()` = 15, `{2}3.prod()` = 9.

## 7. Polys

- **lst is slot-oriented** (ruling 10-21): `{2}[1,2]` is `[{2}1,{2}2]` (`Lst.elements()` :102); `>-` is the only
  bagging of a lst; inside a rec merge a lst is an atom.
- **rec:** `elements()` multiplies by the rec's c; a key's c multiplies the value it reads (`[a=>1]>>{3}a` = `{3}1`,
  `Rec.java` :219); merges distribute through `Poly.Helper.distributed` (:687).
- **Bracket entries:** a suffix c is the multiplicity at that position or vertex (`lst[int{2},str]::[{1,2},'a']`);
  a zero-admitting entry may be absent (trailing lst positions, skipped rel vertices `Type.java` :765, `rec[k{?}]`
  fibers :714).
- **Left coefficients — ruled 11-07, NOT implemented:** in a lst type `{n}T{m}` is a run of n slots each holding
  `T{m}`; lst types are open-world and close explicitly with a `{*}#{0}` tail (`lst[{10}int,{*}#{0}]` = exactly ten
  ints); `lst[{*}int]` = only ints. Today the parser stores `{2}int` as a marked atom with c = 1, so
  `[1,'a'].?lst[{2}int]::T` passes.
- **Bags are unslotted** (ruling 11-07): a left coefficient on a bag member means nothing, yet `{1,2,3,'abc'}` gets the
  branched tid `/m/{{3}int,str}{4}`. Marko pinned that row in TypeTest on purpose as a placeholder for the branch-fURI
  pass — do not "fix" it.
- **Root extent `::T{n}`** (Hindsight "Uber poly — tensor types as rank × extent": `lst[{2}str{3}]::T{2}` is a [2,2,3]
  tensor) is half-built: `int::T{3}` renders `int{3}::T{3}` (the vid becomes `/m/type{3}`).

## 8. Rulings (Marko), oldest first

- **10-21** lst is slot-oriented; nothing bags a lst implicitly; a lst is an atom in a rec merge.
- **10-22** `{0}` is the only zero (`int{0}` == `str{0}` == `noobj{0}`); `noobj` with c = 1 is an ordinary uri. A bare
  `{0}` is the zero type only in a `?rng<=dom` signature; in a value position it is the stream holding one 0.
- **10-22** argument dispatch is coefficient-blind; `{0}` is the instruction ring's zero.
- **11-07** the `{*}` deferral in `appliesPerObject` is load-bearing; `X::T@A` binds A exactly.
- **11-07** lst types are open-world; left coefficient = slots, right = values per slot; `{*}#{0}` closes; bags are
  unslotted.
- **11-08** defaults are coefficient admission.
- **11-08 (this session)** a `{0}` factor annihilates, bounded or not · `{0}` is the plus identity and unbounded stays
  unbounded · `abs` is set-wise · `ANTIMAYBE()` = `{-1,0}` · division by a zero bound throws `MTronException` · `mult` is
  the interval product, "consistent with quantum physics" · `Obj.take` leaves nothing when the take contains every
  multiplicity there is (`c.contains(this.c())`), keeping take's pinned "take it all" rows · **`#{**}` is the universal
  type**, not `#{*}` · `lt/gt/lte/gte`/`compareTo` as in §2.

## 9. Open seams (verified, not done)

1. **`#{**}` is not yet applied.** `MObjs.objs(List)` checks a bag against `ALL_STAR` = `#{*}` (:170), so a negative or
   two-way-unbounded total fails to parse: `{{-1}0,{-1}1}` (a global phase of π) gives "tid does not match objs tid:
   #{*} != /m/int{-2}". `rshift`'s declared range `#{*}` refuses negative results when every typer check is on (tests
   widen it: `>>?#{**}<=rec(...)`). `type-system-mtron.md` still calls `#{*}::T` universal. `ALL_STAR` also means
   "zero or more" (reads, `A{*}` doms), so classify each use rather than flipping the constant. Deferred by Marko.
2. **Lone anti or range-valued values can't resolve a one-obj instruction:** `{-2}5.plus(1)` and `{?}5.plus(1)` fail at
   compile time (`appliesPerObject`'s `gt(1)`), while anti members inside a bag work at runtime
   (`{{-1}5,{2}6}.plus(1)` = `{{-1}6,{2}7}`). Part of the negative-coefficient phase; respect ruling 11-07.
3. **`>>` drops a bulked poly's coefficient:** `{2}[1,2]>>0` = `1`, while `{2}[1,2]>-` = `{{2}1,{2}2}`. Not traced.
4. **`C<?,?>` is generic in name only.** `fURI.c()` and `Obj.c()` return `cInt`, the form classes cast to it, and 58
   main files import it. `docs/design/machine-ring.md` (§"the coefficient ring is the modality") calls the
   `cInt → C<?,?>` generalization *the* parameter: ℕ = multiplicity, ℂ = amplitude, the machine ring = location. A ℂ
   coefficient would also force **cardinality** (what dispatch and types read: one object, a barrier) apart from the
   **ring coefficient** (what values carry): `(1/√2)x` is one object. `uniqueC()` already counts distinct objects.
5. **Skill-doc drift:** `type-system-mtron.md`'s coefficient table has `int{#}::T` (a parse error) and `int{**,}::T`
   (not a coefficient body); `c.adoc` is right.
6. **Suspicious, unverified:** `LazyObjs.multObjsC` *sets* a member's coefficient to `objsC` (`obj.c(this.objsC)`)
   instead of multiplying it.
7. Edge: `dedup` of a `{**}` value gives `{-1}` (no test).

## 10. Pointers

**Code** (line numbers at `11e50b25f`, under `src/main/java/studio/phaseshift/metatron/`):
`furi/C.java` (:24 interface, :62 span, :90 lte, :114 mirror) · `furi/c/cInt.java` (:105 of(String), :161 plus,
:190 div, :213 mult, :267 abs, :326 toString, :365 compareTo) · `furi/fURI.java` (:715 c(), :1414, :1420, :1451,
:1755 of(...)) · `furi/form/AbstractfURI.java` (:333 c(cInt), :448 basePath, :478 testHarness, :664 mult, :689 plus,
:725 mergePaths) · `isa/m/type/Obj.java` (:134 c(), :152 take, :398 isNoObj, :963 testObjs, :2060 `cc`) ·
`isa/m/type/Type.java` (:451 findLCD, :889 typeCheck) · `isa/m/type/impl/MType.java` (:48, :80, :162) ·
`isa/m/type/impl/MObjs.java` (:112 attemptBulk, :170, :304 tid) · `isa/m/type/impl/LazyObjs.java` ·
`isa/m/type/Inst.java` (:501–535 forms, :676 appliesPerObject, :1173 apiOrUser) ·
`isa/m/mach/type/Processor.java` (:201 invoke) · `isa/m/mach/type/compiler/Resolver.java` (:177 resolveCode) ·
`isa/m/mach/type/processor/BasicSwarmComputer.java` (:467 split) · `isa/m/parser/mParser.java` (:1121, :1170, :1199,
:1497 m_type) · `isa/m/type/Rec.java` :219 · `Lst.java` :102 · `Poly.java` :687 · `Uri.java` :120.

**Tests:** `furi/c/cIntTest` (testPlus, testMult, testAbs, testOrder, testCompareTo, testDiv, testMirror, testRewrites)
and `AbstractCTest` (algebraic structure) · `furi/fURITest` (testNeg, testMult, testPlus) · `isa/m/type/IntTest.testTake`
· `ObjsTest.testTake` · `RecTest.testAt` (key and rec coefficients, anti-coefficient rows) · `TypeTest` ·
`InstTest.testAppliesPerObject` / `testGenericBinding` · `mInstSetTest` (batching rows near "COUNT/SUM/PROD").
A coefficient change should run at least: cIntTest, fURITest, IntTest, ObjsTest, LazyObjsTest, NoObjTest, InstTest,
TypeTest, RecTest, LstTest, RelTest, UriTest, CodeTest, mInstSetTest, PlanRewriterTest, BasicSwarmComputerTest
(~4,600 tests; `RelTest.testNamedPathType[2]` is a known failure).

**Docs:** `docs/website/adoc/c.adoc` (notation) · `docs/skills/mtron/references/type-system-mtron.md` (coefficients,
universal type, bracket-entry coefficients) · `docs/skills/metatron/references/type-system-java.md` (`cInt`/`C` API) ·
`docs/skills/mtron/SKILL.md` ("a taste: multisets, symmetry, types") · `docs/skills/mtron/references/fascinations-mtron.md`
("a coefficient is a shape") · `docs/skills/mtron/references/distributed-computing-mtron.md` (Thm 6/17; `?hop={2}3`
reuses a value's coefficient as arity) · `docs/website/articles/stream-ring-theory.md` (definitions, axioms, reduce
axioms, quantum computing with ℂ^{2×2}) · `docs/design/machine-ring.md` · `docs/design/ring-uri.md`.

**Hindsight** (bank `coding-agent::metatron`): "Finding 2026-11-08: coefficient survey…", "Ruling 2026-11-08: cInt
arithmetic…", "Ruling 2026-11-08: cInt.mult is the interval product…", "Implemented 2026-11-08: cInt rulings
2/5/6/7…", "Ruling 2026-11-08: Obj.take leaves nothing…", "Ruling 2026-11-08: #{**} is the universal type…",
"Implemented 2026-11-08: coefficient each-bound order…", "Landed 2026-11-08 … 5ebf0c7bf / 11e50b25f"; older pages
"noobj is {0}…", "Uber poly — tensor types as rank ([]) × extent ({})", "Resolver coefficient reconciliation…".

## 11. Working notes

- **Probing:** the mtron MCP eval tool froze on 2026-11-08; ask Marko to run probes in his console, or write a test.
  Useful probes: `{3}5.cc()` (read c), `{3}5.?int{2,4}::T` (type test), `.explain()` (per-inst `c_dom`/`c_rng`),
  `{1,2,3}.type()`, `[{2}5,{-2}5,7]>-` (cancellation).
- **Spelling traps:** a value `{3}5` ≡ `int{3}::5`; a type `int{3}::T`; an inst `plus{2}(1)`; a uri obj `{4}e` vs its
  address `<e{4}>`; a bare `{0}` in a value position is the stream holding one 0 (spell nothing `noobj` or `{0}2`).
- **Tests with negative coefficients** run with every typer check on: a negative result needs a widened range
  (`>>?#{**}<=rec(...)`), and a key's coefficient must stay inside its argument type (`C{*}`) — put the anti
  coefficient on the value. A bag literal whose total is negative does not parse (§9.1).
- **Style** (AGENTS.md): no private static used from one place; plain `@Test` only for multi-step orchestration;
  CSV rows with `%`; docs only when asked; `MTronException.of`. Marko sent the interval-product change back once for
  these rules (a one-call-site helper, an exhaustive plain `@Test`, an unasked doc section).
