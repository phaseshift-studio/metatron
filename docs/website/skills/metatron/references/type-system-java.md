---
name: type-system-java
description: Java-side type system API — Type interface, MType factory, Fluent/StartLess, predicates, isRefinementOf vs test, generateLCD, coefficients, base types, Call/Inst/Code, gotchas
---

# Java Type System API

## Package overview

```
furi/c/cInt.java               ← coefficient type [min,max]
furi/C.java                    ← coefficient interface (span, within, plus, mult)
isa/m/type/Type.java           ← Type interface + Type.Helper + Type.Builder
isa/m/type/Obj.java            ← Obj interface (base of all metatron objects)
isa/m/type/Call.java           ← Call interface (instruction chain, Ring<Call>)
isa/m/type/Inst.java           ← Inst interface (single instruction)
isa/m/type/Code.java           ← Code interface (multi-inst Call)
isa/m/type/impl/MType.java     ← MType: concrete Type implementation
isa/m/type/impl/MObj.java      ← MObj: concrete Obj implementation
isa/m/type/impl/MCode.java     ← MCode: concrete Code / mFluent base
isa/m/type/impl/MInst.java     ← MInst: concrete Inst + factory methods
isa/m/type/impl/MRec.java      ← MRec: concrete Rec + rec() factories
isa/m/type/impl/MLst.java      ← MLst: concrete Lst + lst() factories
isa/m/type/impl/MUri.java      ← MUri: concrete Uri + uri() factories
isa/m/parser/mFluent.java      ← Fluent builder + StartLess entry points
isa/m/mInstSet.java            ← ISA definitions (/m namespace)
TypeCheck.java                 ← Global type-checking stage configuration
```

## Creating types

### The `T()` factory

```java
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;

// Bare type from a URI
Type intType = T(f("/m/int"));                     // int::T

// Type with VID (named type)
Type personType = T(f("/m/rec"), f("person"), null, null);  // rec::T@person

// Type with a predicate
Type natType = T(f("/m/int"), f("nat"),
    isa_(rec(uri("gt"), jnt(0))).tryToInst(),   // predicate Call
    null);                                        // no constructor
```

The `T()` factory caches by VID in the Router — subsequent calls with the same VID return the cached type. **Clear the cache** if you need to redefine a type:

```java
Router.writeToSpace(myVID, noobj());   // clear existing
T(..., myVID, ...);                    // create fresh
```

### The `Type.Builder` API

```java
Type myType = Type.Builder.build()
    .tid(REC_TID)
    .vid(f("myType"))
    .isaPredicate(rec(
        uri("name"), STR_TYPE,
        uri("age"), INT_TYPE
    ))
    .create();
```

Key builder methods:
- `.tid(fURI)` — base type TID
- `.vid(fURI)` — type name VID
- `.predicate(Call)` — raw predicate
- `.isaPredicate(Obj)` — shorthand for `?[...]` structural predicates
- `.constructor(Call)` — value constructor (for value types)
- `.zero(Obj)` / `.one(Obj)` / `.plus(Inst)` / `.mult(Inst)` — ring operations
- `.inst(fURI, Poly, BiFunction)` — register a custom instruction
- `.create()` — build and register

## The Fluent API (`StartLess`)

Fluent is the DSL for building instruction chains. Entry points are in `mFluent.StartLess`:

```java
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.*;

// Build a predicate: is(gt(0))
Call pred = is_(gt_(jnt(0))).tryToInst();

// Chaining predicates via map(): ?>0.map(?<120)
Call chained = is_(gt_(jnt(0))).map_(is_(lt_(jnt(120)))).tryToInst();

// OR-ing predicates via split/merge: -<[?>0,?<120]>-
Call orPred = split_(lst(
    is_(gt_(jnt(0))).tryToInst(),
    is_(lt_(jnt(120))).tryToInst()
)).merge_().tryToInst();

// Structural predicate: ?[age=>int::T, name=>str::T]
Call isaPred = isa_(rec(
    uri("age"), INT_TYPE,
    uri("name"), STR_TYPE
)).tryToInst();
```

**Important**: Always call `.tryToInst()` to exit the Fluent chain back to `Call`. A single-inst chain returns the raw `Inst`; multi-inst returns a `Code`.

### Key Fluent methods

| Method | Inst TID | Purpose |
|---|---|---|
| `start_(obj)` | START | Begin chain with a value |
| `isa_(obj)` | ISA | Structural record constraint |
| `is_(obj)` | IS | Predicate from a call |
| `map_(obj)` | MAP | Feed LHS result through RHS predicate |
| `split_(obj)` | SPLIT | Branch into parallel alternatives (OR) |
| `merge_()` | MERGE | Rejoin branches |
| `gt_(obj)` / `lt_(obj)` / `eq_(obj)` | GT/LT/EQ | Comparison predicates |
| `and_(objs)` / `or_(objs)` | AND/OR | Logical combinators |
| `apply_(obj)` | APPLY | Apply an instruction |
| `block_(obj)` | BLOCK | Scoped code block |
| `where_(obj)` | WHERE | Conditional filter |
| `else_(obj)` | ELSE | Else branch |
| `repeat_(obj)` | REPEAT | Loop |
| `end_()` | END | End block/loop |
| `filter_(obj)` | FILTER | Filter elements |
| `get_(obj)` | GET | Field access |
| `update_(obj)` | UPDATE | Mutation |

## Predicate and constructor representation

A type stores both its predicate and constructor as `Call` objects in its JVM pair:

```java
Tuple.Pair<Call, Call> jvm = type.jvm();
Call predicate = jvm.get0();   // tests values (null or noobj if none)
Call constructor = jvm.get1(); // transforms values (null or noobj if none)
```

- **Predicate** (`jvm.get0()`): Tests whether a value qualifies as this type. Applied by `test()` and the first stage of `as()`.
- **Constructor** (`jvm.get1()`): Transforms a value to fit the type. Applied by `as()` when the predicate fails. If absent, `as()` is a pure test (fails on non-members).

A structural type has a predicate; a nominal type does not. Either can have a constructor.

### Predicate stack

A type accumulates predicates from each level of its ancestry:

```java
List<Call> stack = type.predicateStack();
// For mortal -> person::T[is(lt(120))]@mortal:
// stack = [is(lt(120)), isa([age=>int::T,name=>str::T])]
```

Each element is a `Call` that can be an `Inst` (single) or `Code` (multiple).

### Isa vs non-isa detection

```java
// Check if top-level predicate is structural (isa)
if (type.isIsaPredicate()) {
    // Extract the record constraint: [age=>int::T, name=>str::T]
    Obj recordConstraint = type.isPredicateObj();
}

// Check if top-level predicate has poly bindings
Poly<?,?> poly = Type.Helper.polyTypePredicateObj(type);
```

### Building predicates directly (bypassing Fluent)

```java
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;

// Single ISA inst
Inst isaInst = instB(ISA_INST_TID, lst(myRecordConstraint));

// MAP inst wrapping a predicate
Inst mapInst = instB(MAP_INST_TID, lst(innerPredicate));

// SPLIT/MERGE for OR
Inst splitInst = instB(SPLIT_INST_TID, lst(lst(branch1, branch2)));
Inst mergeInst = instB(MERGE_INST_TID, lst());

// Assemble into a Call
Call predicate = Call.from(List.of(splitInst, mergeInst));
```

Use `Call.from(List<Inst>)` to build multi-inst chains. Single-inst chains return the raw `Inst`.
Use `Call.tryToInst()` to normalize (Code→Inst if single, otherwise keep as Code).

**Gotcha**: Raw `mFluent` types have unusable erasure — `F extends Fluent<F>` erases to `Fluent`, not `Call`. When building predicates programmatically, use `instB()` + `Call.from()` rather than the raw Fluent API.

## Type hierarchy traversal

### parentType()

```java
Type parent = type.parentType();
// For non-base types (tid != vid): parent = T(tid)  — the type being refined
// For base types (tid == vid): parent = ALL_TYPE.c(this.c())
// For root type: returns this
```

### isRefinementOf()

**Predicate-blind** nominal ancestry check. Walks `this`'s parentType chain looking for a node whose VID matches `other`'s TID or VID:

```java
// True if this type is in the same nominal branch as other
boolean refines = natType.isRefinementOf(intType);  // true

// Also true for siblings (both extend rec, match at rec level)
personType.isRefinementOf(artifactType);  // true (by design)
```

Does NOT compare predicates. Uses coefficient `within()` check.

**Limitation**: Cannot match types whose only common ancestor is ALL_TYPE, because the while loop exits on root types before checking ALL's VID against other's TID.

### test()

Full type checking including predicates. Type-vs-type `test()`:

1. Same VID → coefficient check + exact predicate equality
2. Other is root type → coefficient check only
3. VID pattern match → coefficient + exact predicate equality
4. Walk parent chain recursively
5. Base type check
6. **Exact predicate equality at top level** — composite predicates never match simple ones

```java
// Works: inputs have different VIDs, same TID, no predicates
intType.test(lcdType);  // true (if lcd has same TID and no predicate)

// Fails: predicates differ
natType.test(lcdWithOrPred);  // false — is(gt(0)) != split/merge
```

**Key insight**: `test()` requires exact `Objects.equals()` on predicates when the right-hand type has one. Composite/merged predicates (from LCD) will never pass `test()` against simple input predicates. Use `isRefinementOf()` for nominal verification of generated types.

### Ancestry chain

```java
// Collect all VIDs from type to root
List<fURI> chain = Type.Helper.vidAncestryChain(type);
// For mortal::T extending person::T extending rec::T:
// [mortal, person, rec]
// Used by generateLCD to find the deepest common VID via set intersection
```

## Coefficients (cInt / C)

### `C<Long, cInt>` interface

```java
cInt c = type.c();                    // get coefficient
type = type.c(cInt.of(2, 5));         // set coefficient range

// Key operations
c.within(other)     // this is tighter than or equal to other
c.contains(other)   // this is broader than or equal to other
c.plus(other)       // ring addition (sums mins and maxes)
c.mult(other)       // ring multiplication (products)
c.span(other)       // narrowest interval containing both (NEW)
c.isZero()          // [0,0]
c.isMaybe()         // [0,1]
c.isSome()          // [1,∞)
c.isMaybeSome()     // [0,∞)
c.isZeroable()      // 0 is within this range
```

### `span()` semantics

The narrowest coefficient interval that contains both operands. Used by LCD to compute the coefficient that subsumes all input coefficients:

```java
cInt.of(2,2).span(cInt.of(3,3))       // [2,3]  — not [5,5] (what plus would give)
cInt.of(0,null).span(cInt.of(2,2))    // [0,∞)  — null means unbounded
cInt.of(null,5).span(cInt.of(2,7))    // (-∞,7]
```

## generateLCD vs findLCD

### `findLCD(List<Type>)` — original, nominal-only

- Finds the most specific nominal ancestor via `isRefinementOf` walk
- Does NOT merge predicates (loses all structural info)
- Uses `cInt::plus` for coefficient (bug: should use `span`)
- Preserved for backward compatibility

### `generateLCD(Set<Type>, fURI lcdVID)` — new, structural

Six-step algorithm:

1. **Find common TID**: Intersect VID ancestry chains → deepest common VID
2. **Separate predicates**: Split each type's `predicateStack()` into isa records and non-isa calls
3. **Merge isa records**: Field-by-field structural merge with recursive LCD on field types. Fields in all → LCD'd. Fields in some → LCD'd then coefficient relaxed to `{0, max}`.
4. **Build combined predicate**: All branches OR'd via split/merge. Isa records become ISA insts. Non-isa calls become bare Insts. All wrapped in `SPLIT([...])/MERGE`.
5. **Compute span coefficient**: `reduce(cInt::span)` across all input coefficients
6. **Assemble**: Clear Router cache for lcdVID, create new `T()` with combined predicate

**Disjoint hierarchies**: If common TID is ALL, returns `ALL_TYPE` directly (so `isRefinementOf` can reach the root).

```java
Set<Type> types = Set.of(personType, artifactType);
Type lcd = Type.Helper.generateLCD(types, f("lcd"));
// → rec::T[?[age=>int::T, name=>str{?}::T]]@lcd
```

## Base types and constants

```java
// From mInstSet (import static studio.phaseshift.metatron.isa.m.mInstSet.*)
BASE_TYPES   // Set<fURI>: FAIL, BOOL, BYTES, INT, REAL, STR, URI, REL, LST, REC, INST, CODE, OBJS, NOOBJ
ALL_TYPE     // Type: root/universal type (#{*}::T — any type VID × any cardinality)
INT_TYPE     // Type: /m/int::T
STR_TYPE     // Type: /m/str::T
REC_TYPE     // Type: /m/rec::T
BOOL_TYPE    // Type: /m/bool::T
// ... etc for all base types

// From fURI.Singleton
ALL          // fURI: # or /+/+ (universal wildcard)
```

## Record predicates as data

Isa predicate records are plain `Rec` objects mapping field URIs to Types:

```java
// The predicate ?[age=>int::T, name=>str::T]
// is stored as: rec(uri("age"), INT_TYPE, uri("name"), STR_TYPE)

// Extract from a type:
Obj predObj = type.isPredicateObj();  // → Rec {age→int::T, name→str::T}

// Iterate fields:
if (predObj.isRec()) {
    predObj.asRec().keys().forEach(key -> {
        Obj fieldType = predObj.asRec().at(key);
        // fieldType is a Type (e.g., int::T)
    });
}
```

## TypeCheck

Global type-checking configuration. Five levels, all enabled by default:

```java
TypeCheck.code_resolve   // require code fully resolved pre-evaluation
TypeCheck.inst_dom       // require inst domain type match pre-evaluation
TypeCheck.inst_rng       // require inst range type match post-evaluation
TypeCheck.type_ctor      // require type constructor match for obj creation
TypeCheck.obj_write      // require obj type match for space write

// Toggle
TypeCheck.enable(TypeCheck.inst_dom);
TypeCheck.disable(TypeCheck.type_ctor);

// Query
TypeCheck.check(TypeCheck.obj_write);  // true if enabled
TypeCheck.level();                     // count of enabled stages (0-5)
TypeCheck.getEnabled();                // returns Set<TypeCheck>
```

## Common gotchas

1. **`T()` caches by VID**: Two calls to `T(..., "foo", ...)` in the same JVM return the same type. Call `Router.writeToSpace(f("foo"), noobj())` first to clear.

2. **`.test()` requires exact predicate equality**: `typeWithPredA.test(typeWithPredB)` returns false unless predicates are object-equal. Use `isRefinementOf()` for purely nominal checks, or `isStructuralRefinementOf()` when you need predicate-stack inclusion (this's stack must contain all of other's predicates).

3. **`isRefinementOf` can't reach ALL**: The while loop exits on root types before matching types whose only common ancestor is the universal type. For disjoint hierarchies, return `ALL_TYPE` directly (as `generateLCD` does). A fix was attempted but reverted — advancing past the `isBaseType()` break subtly changed nominal typing behavior in existing tests.

4. **Raw `mFluent` erases to `Fluent`**: `mFluent<?>` method chaining returns `Fluent`, not `Call`. Build predicates directly with `instB()` + `Call.from()`, or use `.tryToInst()` to exit the Fluent chain as `generateLCD` does with the `isa_` branch.

5. **Predicate stack order**: `predicateStack()` returns predicates from most-specific (current type) to most-general (ancestor). The topmost predicate is at index 0. Use `combinedPredicate()` to get a single chained `Call` instead of iterating manually.
