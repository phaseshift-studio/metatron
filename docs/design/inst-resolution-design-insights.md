# InstResolver Design Insights (2026-06-11)

## Session Summary

Rewrote the instruction resolver from scratch. The old `ScoringInstResolver` relied
on a runtime fallback in `Inst.resolve()` that caught ~60% of resolutions. Our new
`V2InstResolver` handles ~88% directly (76 failures from 618 tests without fallback,
vs the old probably ~250+). The remaining failures cluster around metaprogramming
patterns (`_`→`id()` sugar, `|` inst-creation, `reduce(|...)`).

## Root Causes of Resolution Failure

### 1. Domain checking: no good middle ground

The three approaches tried, in order of strictness:

| Approach | What it does | Problem |
|---|---|---|
| `isRefinementOf()` | Walks type hierarchy via VID matching | Triggers `MType.T()` → router → fURI regex → StackOverflow for typed values |
| Concrete domain gate | Requires `basePath` match for concrete domains | Rejects valid cross-type conversions (inst→int for `sum()`) |
| `testByID \|\| test()` (current) | Nominal VID walk then structural check | `test()` passes any value against any base type without predicate — too lenient |

**Insight**: There is no single fast-and-correct domain check in the current type
system. `testByID` short-circuits for value-vs-type. `test()` checks predicates
but base types without predicates match everything. `isRefinementOf` requires
`lhs.type()` which triggers recursive type construction.

**Future**: The type system needs a lightweight refinement check that doesn't
construct new Type objects. Something like `fURI.isRefinementOf(fURI)` that walks
the parent chain without creating `MType` instances. Or `lhs.tid()` could carry
enough info to compare with `dom` without going through `type()`.

### 2. Generic binding: noobj LHS breaks domain generics

`bindGenerics` skips domain generic binding when LHS is `noobj`:
```java
if (domGeneric && !lhs.isNoObj()) { ... }
```

This means `print?A<=A(str::T)` with noobj LHS leaves `A` unbound. The range
stays generic. Downstream instructions lose type info.

**Future**: `noobj` should bind generics to `noobj::T` or `#::T`. The LHS is
known even if it's zero-valued. Or: instructions that accept noobj should use
explicit `#` (universal) domains instead of generics.

### 3. Arg resolution: test() is too permissive for Type args

When an instruction takes a Type argument (like `as(int::T)`), `resolveArgs`
checks `int::T.test(str::T)` via `test()` — which passes because both are base
types without predicates. The generic `as?A<=B` candidate gets the same score as
`as?int<=str`, and insertion order determines the winner.

**Current V2 fix**: as()-specific scoring: +2000 for exact range match, +500 for
refinement, -10000 for concrete mismatch. This scoped penalty works well.

**Future**: `resolveArgs` should use a stricter Type-vs-Type check for args that
are types. `isRefinementOf` or base path comparison would work. The `test()`
fallback should only apply to value args, not Type args.

### 4. Coefficient cardinality: resolver vs executor boundary

`resolveArgs` had a `c().within()` quick-reject that blocked valid cases because
the executor handles `uniqueC()` compression transparently. `{2}2.plus(x)` runs
`2.plus(x)` once — the resolver shouldn't reject on coefficient mismatch.

**Current V2 fix**: Removed the quick-reject for `isObjCall()` args; kept original
`test()` for literal args. Added `typeCompatibleIgnoreCoefficient` for call args.

**Future**: The resolver should check TYPE compatibility only. Coefficient is
the executor's concern. The `test()` method itself checks `c().within()` — this
should be relaxed or moved entirely to the executor layer.

### 5. Code-chain resolution: gather vs initial ordering

The token threading in `Code.resolve()` checked `isInitial()` before `isGather()`.
Instructions that are both gather AND initial (like `>-.sum()`) had their token
overwritten to the arg type instead of keeping the aggregate result type.

**Fix**: Moved `isGather()` check first. A gather's range IS the correct token
for the next instruction.

### 6. Scoring tiebreakers: insertion order decides

When multiple candidates have identical scores (e.g., all `as?int<=*` candidates
get identical domain+arg scores), `max(Comparator.comparingInt(score))` picks
whichever appears first in the stream. This is non-deterministic across resolver
implementations.

**Current V2 fix**: User-specified dom/rng gets +3000 bonus. This breaks ties
correctly for `sum?real<=real{*}` vs `sum?int<=int{*}`.

**Future**: Add a deterministic tiebreaker: prefer candidates with matching range,
then matching arg types, then insertion order. Or: use a more granular scoring
system with more dimensions.

### 7. The fallback: metaprogramming patterns

The old `Inst.resolve()` fallback handles patterns that the resolver can't match:

- `_` → `id()` sugar: `print(_)` rewrites to `print(id())`. The `id()` arg
  resolves against noobj LHS, producing an unbound generic range. `resolveArgs`
  can't validate it against the API's arg type.
- `|` inst-creation: `|(plus(30)).map(20)` creates an inst inline. The inst's
  type doesn't match `map`'s domain cleanly.
- `reduce(|...)`: similar inst-creation in args.

The fallback works by reading candidates with the FULL `this.tid()` (not just
basePath) and resolving args directly via `Helper.resolveArgs(domainInst,
domainInst, lhs)` — using the user inst as both user and API template.

**Future**: These patterns need first-class support in the resolver. Options:
- Teach the resolver to recognize `id()` calls and resolve them against the LHS
  context before type-checking
- Add a "metaprogramming mode" that relaxes type checking for inst body args
- Move `_` sugar directly into the parser so `id()` never appears in args

## Design Recommendations for v3

1. **Add `fURI.isRefinementOf(fURI)`** — lightweight parent-chain walk without
   Type construction. Use this for domain checking to avoid the MType.T() recursion.

2. **Split arg type checking**: Type args (when the user passes `X::T`) should
   use base-path comparison or `isRefinementOf`. Value args can keep `test()`.

3. **Normalize coefficients before type checking**: Create a `typeCompatible(a, b)`
   that strips coefficients before comparing. The executor handles compression.

4. **Score on more dimensions**: Domain match quality, range match quality, arg
   match quality, coefficient alignment, has-body, user-specified hints — with
   enough granularity that ties are rare.

5. **Handle `noobj` LHS generics**: Bind domain generics to `noobj::T` or `#::T`
   when LHS is noobj, so ranges get resolved.

6. **Move fallback patterns into the resolver**: The resolver should handle
   `_`→`id()` and `|` inst-creation natively. The fallback should be a last
   resort, not catching >10% of cases.

7. **Test the type system independently**: Many resolution failures are actually
   type system issues (`test()` leniency, `testByID()` short-circuit, MType.T()
   recursion). A separate type-checking test suite would catch these before they
   surface in resolver tests.
