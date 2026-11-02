# Task: write an `as`-graph ambiguity checker in `Inst.Helper`

## Context

metatron is a data-oriented language (Java, package `studio.phaseshift.metatron`). An instruction is a named object with
a **domain type** (dom, the input) and a **range type**
(rng, the output), encoded as `inst?rng<=dom`. The `as` instruction (`AS_INST_TID`) is heavily overloaded — there are ~
70 `as` instances, each a distinct `(dom, rng)` pair that casts one type to another (`as?int<=bool`, `as?rec<=str`, …).

At runtime, dispatch resolves which `as` instance applies by matching the input's type against each candidate's `dom`
and choosing the **most specific** (most-refined) `dom` that still accepts the input. That "most specific match" is only
a **total function** if, for every `rng`, the set of candidate `dom`s is a **chain** (totally ordered by refinement). If
two `dom`s are incomparable, some input could match both and "most specific" is undefined.

## Goal

Write a function in `Inst.Helper` that enumerates every `as` instruction and reports every ambiguity. An ambiguity is
either:

1. **duplicate** — two `as` instructions with the *same* dom **and** the *same* rng (literally "more than one mapping to
   the same type"), or
2. **incomparable** — two `as` instructions with the *same* rng whose doms are neither
   `A ≤ B` nor `B ≤ A` (a future input could match both, with no "most specific" winner).

## The ≤ relation (the full check)

"a is a refinement of b" (`a ≤ b`) is exactly:

```
a.testNominally(b) && a.c().within(b.c())
```

- `testNominally` — nominal (label-chain) check; works across value/type, but strips coefficients.
- `c().within` — coefficient sub-range check; **directional** (`int{2} ≤ int{*}`, not the reverse).

Two doms `A`, `B` are **incomparable** iff neither direction holds:

```
!( A.testNominally(B) && A.c().within(B.c()) )
&& !( B.testNominally(A) && B.c().within(A.c()) )
```

Do **not** use `Type.isStructuralRefinementOf` — it pulls in structural predicates, which is a *type-checking* concern,
not a *dispatch* concern; this checker certifies dispatch, so it must be predicate-blind. Do **not** use
`Type.isRefinementOf` either — the name is misleading (conceptually "canNominallyBe") and its nominal part is the old
chain-walk; `testNominally` is the fresher primitive.

Edge case: the universal type `#` (with coefficient `{**}`) is the top of the lattice — it should be "comparable with
everything" and never create an incomparable pair. Confirm
`testNominally` / `within` naturally return true for `#`; if they don't, treat a `#` dom as always-comparable.

## How to find the as-instructions and their dom/rng

- `AS_INST_TID` (`mInstSet.java:154`) is the `as` instruction's tid.
- Enumerate instructions reachable via the `Router` / `InstSet` / space (follow the existing pattern the codebase uses
  to walk registered instructions), keeping those whose tid resolves to `AS_INST_TID`.
- Read each instruction's dom and rng via `Inst.dom()` and `Inst.rng()` (they return `Type`
  objects — see their use in `Inst.java`, e.g. `this.dom().test(...)`).

## Output contract

Return a list of violations; empty list means the graph is unambiguous. Each violation should identify the two
conflicting `as` instructions (their dom and rng), e.g.:

```
"ambiguous as: dom 'int{2}' vs dom 'int{*}' for rng 'str' (incomparable)"
"duplicate as: dom 'bool' -> rng 'int' appears twice"
```

If the codebase convention is to throw on invariant violations, throw instead — but returning a list is preferred so the
caller can decide.

## Constraints

- Read-only verification: do not mutate the instruction space.
- Deterministic: same input → same output.
- ~70 instructions, so O (n²) pairwise comparison is fine; no optimization needed.

## Why this matters

The checker certifies the precondition for a future "most specific match" dispatcher: dispatch is only a total function
when each rng's doms form a chain. Without this verification, a naive dispatcher would be silently order-dependent (or
wrong) on ambiguous inputs.

## Pointers

- `AS_INST_TID` — `mInstSet.java:154`
- `testNominally` — nominal check (on `Obj`/`Type`; strips coefficients)
- coefficient sub-range — `c().within(...)`
- dom/rng accessors — `Inst.java` (see `this.dom()`, `this.rng()`)
- Reference for how the lattice is walked — `Type.Helper.findLCD` (`Type.java:259`) and
  `Type.Helper.generateLCD` (`Type.java:291`)
- For awareness only (do NOT use): `Type.isRefinementOf` (`Type.java:130`),
  `Type.isStructuralRefinementOf` (`Type.java:110`)
