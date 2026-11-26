# poly `strct` — the backing-structure qparam (`?strct`)

**Status:** design sketch for review; no code. Open decisions are collected in §13.

**One line:** `[...]` says *what a poly holds*; `?strct` says *how the container is backed and what it does under
mutation*. The two axes are expressively disjoint, and a structure may never invalidate the content claim.

---

## 1. The problem

The concrete backing of a `lst`/`rec` is currently fixed in Java. `MObjFactory.toObj(..., Class)` builds `MLst` and
`MRec` unconditionally, and no type can ask for anything else. Two bounded-history classes already exist —
`FIFOLst` and `FIFORec` — but they are selected by Java call sites (`ThreadExecutor`, `BasicSwarmProcessor`), never by
a type.

We want a value's type to be able to say:

```mtron_pre
lst?strct=[fifo,100]::[1,2,3,4,5]        -- a bounded FIFO, cap 100
rec?strct=hash::[a=>b,c=>d]              -- a hashed map
lst?strct=array::[1,2,3,4,5]             -- the default, spelled out
```

and, orthogonally, a bracket claim about the contents:

```mtron_pre
lst[int,str]?strct=[fifo,100]::T
lst[person{*}]?strct=[fifo,100,details/age]::T@my_lst
```

## 2. The surface

`strct` is an ordinary query component on the **poly type's tid**. It has two shapes:

```mtron_pre
strct=<kind>                        -- arity 0
strct=[<kind>,<p1>,<p2>,...]        -- arity >= 1
```

`strct=[<kind>]` is **not** a spelling: the array form exists to carry params, and requires at least one. That
gives exactly one canonical text per kind+params, with no `[fifo]`/`fifo` ambiguity in `qMap` (which matters,
because `fURI.equals` includes the query, so either spelling would otherwise be a distinct type).

Examples:

```mtron_pre
lst?strct=array::[1,2,3,4,5]
lst?strct=fifo::[a,b,c]
lst?strct=[fifo,100]::[a,b,c]
lst?strct=[fifo,100,live,fail]::T
rec?strct=ordered::[a=>b,c=>d]
rec?strct=hash::[a=>b,c=>d]
rec?strct=[fifo,100,live]::T
lst[person{*}]?strct=[fifo,100,details/age]::T@my_lst
```

Constraints inherited from the fURI qparam layer (no new grammar is needed):

- the value is a **flat array** of scalars — `int`, `real`, `uri`/`str` (unquoted), `true`/`false`; no nesting;
- **keys** cannot contain `.` or `/` (`_` is allowed); **values** may contain `/`, which is what makes a nested
  path (`details/age`) expressible;
- `strct` is not in `Q_LEAD` and is not a q-proc name (it does not end in `q`);
- the type reads in the one order `content -> [poly] -> {coefficient} -> ?query` (`m_furi_internal`), so the poly
  bracket and the `strct` value can never be confused for one another.

### Why one key, not `strct=<kind>&param=[...]`

1. `param` is a global, unnamespaced key (keys cannot use `.` or `/`), so it is a collision magnet and says nothing
   about what it parameterizes.
2. Two keys are two things to validate together; `param` without `strct` is meaningless.
3. Two keys create a second spelling (`strct=fifo&param=[]`, `strct=fifo`) for the same type.
4. The qparam grammar cannot name parameters anyway (a flat array of scalars has no room for `cap=>100`), so
   positional is forced — and positional params belong beside their kind.

## 3. What the claim means

- **The tid is the single source of truth.** The claim rides the value's tid; `fURI.equals`, `TypeGraph` and the
  type cache include it, while `basePath()`/`fURI.test` erase it. So a fifo list is still a `lst` for every
  existing test, and the structure claim is visible where it must be (identity, persistence, construction) without
  breaking dispatch.
- **The class is the factory's obligation.** `test` does not inspect the value's Java class in v1 (no `instanceof`
  in the hot path). If a value's backing ever disagrees with its tid, that is a factory bug and must fail loudly.
- **v1 is a construction/representation annotation, not a `test` predicate.** The claim selects and constrains the
  backend; it does not filter values at `test`. (Open decision 1; if it becomes a predicate, `test` must read it and
  the `array` question below changes.)
- **`array` and `ordered` are the defaults, and canonicalize to absence.** So `lst::T = lst?strct=array::T` and
  `rec::T = rec?strct=ordered::T` are literally true: both spellings produce the bare tid. The defaults are
  *construction* rules; the bare poly type carries no structure claim, which is why a fifo value is still a `lst`.
- **Params are positional, and each kind's schema says which are optional and what absence means.** For `fifo`:
  `cap` absent is unbounded, no retention key is "retain nothing". A present param is exact — `[fifo,100]` is cap
  exactly 100.
- **A structure may change an entry's order; it may not change whether an entry the type names is there.** This is
  the invariant that keeps eviction from lying about the type.

## 4. The two axes, and the three seams

`[...]` (the poly bracket on the tid, and the predicate bracket after `::T`) is a **content/schema/vocabulary**
claim: types, runs, field names, optionality, generics. `?strct` is a **container** claim: backing kind, eviction,
ordering, retention. Neither can express the other:

| claim | `[...]` | `?strct` |
|---|---|---|
| elements are ints | `lst[int{*}]` | no type slot |
| pair / head run | `lst[int,str]`, `lst[int{2},str{?},lst]` | — |
| field `age` is a fiber | `rec[age]` | — |
| field optional / typed | `{?}age => datetime::T` | only *reads* it |
| generic slots | `lst[A,B]` | — |
| backed by ArrayList vs FIFO | — | kind |
| cap + eviction end | — | `[fifo,100]` |
| retain entries with `live`/`fail` | — | `[fifo,100,live,fail]` |
| insertion order vs hashed | only *presumes* order | kind |
| comparator / order | a named type can carry it | `[sorted,age]` |
| concurrency / snapshot | — | deferred (neither) |

`strct` has no type slot, so "the elements are ints" is inexpressible there; `[...]` has no kind or policy slot, so
"FIFO, cap 100, retain live" is inexpressible there. Merging them would be worse: structure-in-bracket makes a fifo
of ints and an array of ints different *tensors*, and type-in-`strct` creates two sources of truth for the element
type.

They meet at exactly three seams, and each is **one-way**:

1. **Count.** `{n}`/`{*}` is a content claim about the value's elements; `cap` is a policy bound on what the
   container retains. Never the same sentence. Rule: **the structure must maintain the content claim.** (The exact
   arithmetic depends on open decision 3.)
2. **Position.** A positional pattern presumes stable positions. A kind is *anchored* (`array`), *re-anchoring*
   (`fifo`, `lifo`, `sorted`), or *positionless* (`set`, `hash`). The poly may veto a **class** of kinds, never name
   one: `lst[int,str]?strct=fifo` is rejected; `lst[E{*}]?strct=fifo` is fine.
3. **Vocabulary.** Field names/paths are shared. `[...]` **declares and types** them; `strct` **references** them
   for presence. So `strct` may only name a declared field/path, never introduce one. A miss is a declaration
   error — otherwise a retention key that does not exist silently retains nothing.

The two never-rules:

- **`strct` never introduces a type, a field, or a shape** — only a kind, a scalar, or a declared name.
- **`[...]` never names a structure, a policy, or a retention set** — only types, runs, names, and coefficients.

The grammar already enforces the separation: poly and query are different slots in `m_furi_internal`, so the split
is structural, not a convention.

## 5. Kinds and schemas

Ordered by measured reach (see §9). `base` is the poly the kind applies to.

| kind | base | params | meaning | status |
|---|---|---|---|---|
| `array` | lst | — | indexed sequence; the default | exists |
| `ordered` | rec | — | insertion-ordered map; the default | exists |
| `fifo` | lst, rec | `cap:int?`, `retain:key*` | bounded history, drop the eldest non-retained | implemented internally |
| `lifo` | lst | `cap:int?`, `retain:key*` | stack: push/pop at the top; over cap, evict the oldest non-retained | new |
| `hash` | rec | — | unordered keyed map | new |
| `set` | lst | — | distinct members, insertion-ordered | new (blocked) |
| `sorted` | rec, lst | `order:uri?` | ordered by key (rec) or by an order (lst) | new |

Schemas are positional and each kind owns `(arity, param types, defaults, validator)`. Unknown kind, wrong arity,
a param of the wrong type, or a kind on the wrong base is a declaration error.

**`fifo` schema:**

```mtron_pre
[fifo, cap, retain...]
  cap     int, optional   how many non-retained entries are kept; absent = unbounded
  retain  key*, variadic  entries having ANY of these paths are never evicted
```

Semantics (mirrors `FIFORec`):

- requires **arrival order**: the rec backing is `LinkedHashMap`, the lst backing an ordered list
  (`CopyOnWriteArrayList` today, `ArrayList` acceptable); `fifo` and `hash` are alternative kinds, never two params
  (you cannot find the eldest in a hash map);
- trim runs on write, over the map being built, before it is installed: walk eldest to newest, drop entries whose
  retention fields are all absent, until the count of **non-retained** entries is at most `cap`;
- `cap` bounds the *evictable* population, not the total — retained entries sit outside the budget;
- retention is a **set**: the tail is sorted and deduped on write, so `[fifo,100,live,fail]` and
  `[fifo,100,fail,live]` are one type;
- `fifo` on a `rec` may not evict an entry the container's *type* names (a fiber, or a predicate field): the type
  wins over the policy.

## 6. Position stability

The property that keeps `?strct` and `[...]` from colliding:

| kind | positions | positional poly | run poly |
|---|---|---|---|
| `array` | indices, stable under append/set | yes | yes |
| `fifo` | arrival order, head moves on eviction | no | yes |
| `lifo` | arrival order, top moves on push/pop; the bottom evicts | prefix only (push/pop yes, eviction no) | yes |
| `sorted` | rank, changes on insert | no | yes |
| `set` / `hash` | none | no | yes |

So a container's shape is **one element type plus a coefficient** (`[E{c}]`); a positional shape is legal only on a
position-addressed poly that does not re-anchor (`array`).

Two consequences:

- **Coefficients vs cap.** A bounded run (`A{n}`) may be maintained by a structure only when the structure keeps
  the run inside `n`'s interval — a two-slot ring is legitimate; a run that eviction can shrink is not. The precise
  rule depends on open decision 3.
- **`array` is not exempt.** `skip`, `remove`, `take` re-anchor a positional claim exactly as eviction does, so a
  shape-changing operation on a patterned value must re-check the shape or be refused. Eviction is only the case
  where the re-anchoring happens by itself.

## 7. Retention

Retention is **presence, not truthiness**. A param names a key; the test is `has(key)` (`!at(key).isNoObj()`). This
is the primitive, and it composes with the schema's own optionality:

```mtron_pre
computer = rec::T[?[{?}live => datetime::T, {?}fail => fail::T]]@computer
queue    = rec?strct=[fifo,100,live,fail]::T[?[uri => computer::T]]@queue
```

- `live` is a datetime (when it started), not a bool — presence is the state, absence is "finished".
- **`{?}` is load-bearing.** If the element type *required* `live`, every entry would be retained, the cap would
  never bind, and the "fifo" would be an unbounded history. The optional field and the eviction policy are two
  halves of one design.
- **The key resolves on the eviction unit's value** (`FIFORec` filters `map.values()`), so for `uri => computer` it
  is a computer field.
- **Nested fields are paths, not fibers.** A fiber is about *what instructions apply to* (the lift); existence is
  about keys. `details/age` is the param; a fiber does not rename `details/age` to `age`. Paths must be **relative**
  (a leading `/` reads absolute and never matches).
- **Retention keys must be declared by the element type** (a predicate field or a fiber path). Validate when the
  element type is known; otherwise a typo silently retains nothing.
- **Identity pins stay an API.** `FIFOLst.pinned` is an identity set; no scalar value can name an identity. If
  identity-based retention is needed, it becomes a field on the element so the type can see it.

Line to keep: a param can name a key and the policy tests presence. It cannot test a value (`state == running`),
carry a predicate, or name an identity — those are code, and a field on the element is the type's answer.

## 8. Where it lives in the code

| layer | today | change |
|---|---|---|
| `Tokens` | `FIFO`/`ARRAY`/`TREE`/`HASH` (uncommitted stub) | add `STRCT`; fix the duplicate `HASH` |
| `fURI` | generic query API; `args()` is the array-valued precedent | `hasStrct()`/`strct()`/`strct(List)`; canonicalize defaults and the tail |
| `mParser` | `m_furi_query` already accepts `[fifo,100]` and `details/age` | none |
| `Type`/`MType` | `validatePoly`; type identity includes `qMap` | `validateStrct` (kind, arity, params, path, positional-vs-kind) |
| `MObjFactory` | hardcodes `new MLst`/`new MRec` | the one dispatch point: `backing(tid)` |
| `Obj.Helper.construct` | skips base types, so a registered `Type` can never intercept | the factory must carry this; no registry hook |
| `mParser` lst literal | `new MLst(...)` directly, bypassing `construct` | route through `MObj.of`/`construct` so typed literals are built by the factory |
| `Poly`/`impl` | ~15 sites rebuild a poly as a plain `lst(...)`/`rec(...)` | one `Poly.Helper.rebuild(...)`; a class/tid mismatch is loud |
| `ObjmtronSerializer` | `handleTID` hides base-type tids, so a query-only tid prints as a bare literal | keep a base tid that carries a query |
| spaces | store the instance | the tid carries the claim; the factory rebuilds on read |
| `Compiler`/`Resolver` | — | nothing: `::` is the parser attaching the tid, not an instruction |

The single hard problem is **silent degradation**: a rebuild through a plain `lst(...)` loses the class with no
error. The feature is only as strong as the weakest rebuild site, which is why `rebuild` is a phase-0 deliverable.

## 9. Coverage and implementation order

Measured in `src/main/java` (Java collections; order of magnitude, not precision; the VM's temporaries inflate
`ArrayList`/`HashMap`):

| kind | base | ~uses | note |
|---|---|---|---|
| `array` | lst | 640 | the default |
| `ordered` | rec | 610 | the default |
| `set` | lst | 155 | distinct; blocked on the coefficient rule |
| `hash` | rec | 67 | cheap; order-sensitive consumers need an audit |
| `fifo` | lst+rec | 22 deque + 2 hand-rolled | already implemented; demand signal exceeds the count |
| `lifo` | lst | 14 | one class |
| `sorted` | rec ~7 / lst ~3 | 10 | rec is cheap (`REC_KEY_ORDER`); lst needs an `Obj` order |
| *concurrent/cow* | both | 50 | an axis, not a kind (`StoreRec`) |
| *identity* | any | 16 | inexpressible |
| *heap* | lst | 2 | needs an order |
| *lru/ttl* | rec | ~1 / 0 | read-side mutation / a clock |

**Ship order** (coverage, with readiness where they diverge):

1. **`array`, `ordered`** — the defaults. Not "no work": this is where the defaults canonicalize to absence, and
   where the validator and the factory pass-through are proven to be exact no-ops.
2. **`fifo`** — the first explicit kind, and the right one: it is already implemented, it is the motivating use
   case, and it exercises every new mechanism at once (factory dispatch, rebuild, retention, eviction, serializer).
   Do the shared plumbing on the hardest consumer and every later kind is a small class.
3. **`lifo`** — `ArrayDeque`, the same eviction machinery at the other end.
4. **`hash`** — `HashMap`, one factory branch; audit order-sensitive consumers.
5. **`set`** — highest-reach new kind, blocked on the coefficient rule: a set must refuse `A{n>1}` per distinct
   member and must not let `MObjs.attemptBulk` collapse equal arrivals into `{n}x`.
6. **`sorted`** — split: `rec?strct=sorted` is nearly free (`CommonUtil.REC_KEY_ORDER`); `lst?strct=sorted` needs a
   general `Obj` order and is deferred.

Deferred, with reasons: `lru`/`ttl` (read-side mutation, a clock), `deque`/`heap` (need the order), `concurrent`
(an axis; reuse `StoreRec`), `identity` (inexpressible), `multimap`/`bidirectional`/`trie`/`persistent` (near-zero
uses; coefficients already model bags, and value semantics already give copy-on-write).

One caveat to record: the kinds conflate three dimensions — distinctness (bag vs set), order (insertion vs sorted vs
none), and eviction (none / fifo / lifo / lru). A single `strct` kind names one cell of that cross-product, so
`sorted_set` and `bounded_sorted` do not fit. Keep kinds as the common cells and params as the policies, and add a
combined kind only when a real use appears.

## 10. Roadmap

**P0 — the seam and the defaults.** `STRCT` token (and the `HASH` fix); `fURI` accessors with canonicalization
(`array`/`ordered` erase; retention tail sorted and deduped); `validateStrct` (kind known, arity, param types, cap,
relative path, declared path, positional-vs-kind); `Poly.Helper.backing(tid)`; `Poly.Helper.rebuild(...)` with a
loud class/tid mismatch; the `MObjFactory` branch; lst literals routed through `construct`; the `handleTID` fix.
*Exit:* `lst::T` and `lst?strct=array::T` are one type; `rec?strct=ordered` likewise; the rebuild matrix is green;
no value can silently lose its structure.

**P1 — `fifo`.** Wire `FIFOLst`/`FIFORec`; fix the `pinned` clone; put `offer` on the `at()` write path so mtron
writes evict (today `offer` is a bespoke Java API); retention keys; persistence round trip.
*Exit:* `lst?strct=[fifo,2]::[a,b,c]` keeps `b,c`; `rec?strct=[fifo,2,live]` keeps live entries; `plus`, `at` write,
clone, `objs(...)` and a space write/read all preserve the structure.

**P2 — `lifo`.** `ArrayDeque`; push/pop at the top; over cap, evict the oldest non-retained from the bottom (the same
eviction end as `fifo`, only the pop end differs); retention.
*Exit:* the mirror of P1 at the top: pop reads the newest, the cap keeps the newest N, retained entries survive.

**P3 — `hash`.** `HashMap` backing; order-sensitivity audit (serializer, diff/merge normal forms, fiber iteration).
*Exit:* a hashed rec round-trips and every order-sensitive consumer is either order-agnostic or documented.

**P4 — `set`.** The coefficient rule first (distinct = per-member coefficient at most 1; refuse bulk collapse),
then `LinkedHashSet`.
*Exit:* writes dedupe; a coefficient claim of more than one is rejected at declaration; `MObjs` cannot re-collapse a
set member.

**P5 — `sorted`.** `rec?strct=sorted` via `REC_KEY_ORDER`; then the `Obj` order for `lst?strct=sorted` if it lands.
*Exit:* key order is total and stable; a missing order is a declaration error.

**P6 — deferred kinds** as use appears.

## 11. Pre-work: defects the review found

1. **`Tokens.HASH` is declared twice** (the uncommitted stub at line 43 and the existing constant at line 130). The
   tree does not compile as it stands; reuse the existing constant or rename.
2. **`FIFOLst.pinned` is shared across clones** — `MLst.clone` goes through `Object.clone()`, which copies the
   identity set by reference. A clone's offers leak pins into the original.
3. **`FIFOLst.offer` is not on the write path.** mtron writes go through `at(key,value,op)`; the FIFO behaviour only
   fires if `offer` is called. `FIFORec` already overrides `at`, which is the pattern to copy.
4. **`handleTID` hides base-type tids**, so a query-only tid prints as a bare literal — the claim is lost in logs,
   docs and `parse(write(x))`.
5. **`MObjs.attemptBulk` collapses identical members into coefficients**, which is exactly what a bounded history
   must not do (`{N}x` loses the N arrivals the structure exists to keep).
6. **`fURI.equals` includes `qMap` while `hashCode` does not** — a pre-existing mismatch that becomes load-bearing
   once values are keyed by strct'd tids.

## 12. Test matrix

| construct | expectation |
|---|---|
| `lst?strct=array::[1,2]` | same tid as `lst::[1,2]`; prints bare |
| `lst?strct=[fifo,2]::[a,b,c]` | keeps `b,c`; tid keeps the claim |
| `lst?strct=fifo::[a,b]` | unbounded fifo: no eviction, indistinguishable from array until a consume/poll op exists |
| `rec?strct=[fifo,2,live]::[…]` | keeps live entries; cap bounds the rest |
| `lst[int,str]?strct=fifo::T` | declaration error (positional + re-anchoring) |
| `lst[int{*}]?strct=fifo::T` | accepted (run survives head-drop) |
| `rec[age]?strct=hash::T` | accepted (keys, not positions) |
| `lst?strct=[fifo,100,details/age]::T` | accepted only if the element type declares the path |
| `lst?strct=[fifo,100,fail,live]::T` | canonicalizes to the sorted tail |
| `strct=[fifo]` | declaration error (the array form carries params) |
| `strct=[fifo,0]` | declaration error (`cap` positive) |
| `parse(write(x)) == x` | for every form above |
| rebuild matrix | `plus`, `at` write, clone, `objs`, space write/read preserve kind and class |
| `MObjs` bulk | a bounded history of identical arrivals keeps the arrivals |

## 13. Open decisions

1. **Annotation vs predicate.** v1 treats `strct` as a construction/representation claim, not a `test` filter. If it
   becomes a predicate, `test` must read it, and the `array`/`ordered` erasure question returns.
2. **`array`/`ordered`: erase or real kinds.** This doc erases them, so `lst::T = lst?strct=array::T` is literal.
   The alternative — real kinds — makes the docs say "behaves as" and gives a pinnable `array` that can reject
   fifos. It is the one decision that changes what `strct=array` *does*.
3. **`{n}` semantics: head run or total count.** This decides the count seam's arithmetic: under a head-run reading,
   `{n}` and `cap` can never conflict (only eviction shrinking the run matters); under a total-count reading, the
   validator must reconcile `n` with `cap`.
4. **`cap`: exact or bound.** The doc uses exact (`[fifo,100]` is cap 100). A bound reading ("cap at most 100",
   admitting 50) needs per-kind comparison semantics.
5. **`on_full`.** `fifo` drops the eldest; a `reject` variant would be a third param or a separate kind. Deferred.
6. **Where canonicalization happens.** `array`/`ordered` erasure and tail sorting need one door. The type door is
   the natural place (identity is decided there), but the value's tid must go through the same door or the value and
   its type disagree.
7. **`set` and coefficients.** The rule for distinctness against multiplicity, and how `MObjs` bulk behaves around
   it.
8. **`sorted`'s order surface.** A type? An instruction? A named comparison? Rec keys already have
   `CommonUtil.REC_KEY_ORDER`; a general `Obj` order does not exist.
9. **Persistence.** v1 assumes the tid carries the claim across a space write and the factory rebuilds on read. If a
   structure must survive an out-of-process round trip, the serializer and the read path are part of P0, not P1.

## see also

- `docs/design/distributed-computing-qparams.md` — the qparam axis precedent (`?place`, `?hop`, `?split`), and the
  `args=[X,Y]` array-valued query component.
- `docs/design/inst-resolution-design-insights.md` — the binder/type-check notes that mission 2 (constrained
  generics) will build on.
- `docs/skills/metatron/references/type-system-java.md` — `Type`, `MType`, `TypeGraph`, coefficients, `test`.
- `AGENTS.md` — API rules, the `fURI` slicing vocabulary, `Machine.read/write` as the data access path.
