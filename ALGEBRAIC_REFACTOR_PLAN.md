# Algebraic Interface Refactoring Plan

## Problem Statement

The current implementation has algebraic interfaces (`PlusMonoid`, `MultMonoid`, `Ring`, etc.) implemented at the Java type level. This creates issues with type refinements where algebraic properties may not hold.

**Example Issue:**
- `Int` implements `Ring.O<Int>` with `plus()` and `mult()` methods
- But `nat` (natural numbers) is a refinement of `Int` that doesn't form a ring (no additive inverses)
- The Java type system can't express "this type forms a ring only under certain conditions"

**Solution:** Remove algebraic operations from Java API and handle them exclusively through the instruction layer where runtime type checking and constraint validation can occur.

---

## Types Implementing Algebraic Interfaces

### 1. **Rel** - `Poly<Rel, Tuple.Pair<Obj, Obj>>, MultMonoid.O<Rel>, PlusMonoid.O<Rel>`
   - **Location:** `src/main/java/studio/phaseshift/metatron/isa/m/type/Rel.java:50`
   - **Methods to remove:**
     - `Rel plus(Rel rhs)` (line 243)
     - `Rel mult(Rel rhs)` (line 209)
     - `Rel neg()` (line 233)
     - `Rel one()` (line 167)
     - `Rel zero()` (line 190)
     - `boolean isOne()` (line 176)
     - `boolean isZero()` (line 199)
   - **Keep helper methods:** `one()`, `zero()`, `isOne()`, `isZero()` (useful for instruction implementations)
   - **Instructions already handle:** `PLUS_INST_TID`, `MULT_INST_TID`, `NEG_INST_TID`, `ONE_INST_TID`, `ZERO_INST_TID`

### 2. **Int** - `Mono, Ring.O<Int>`
   - **Location:** `src/main/java/studio/phaseshift/metatron/isa/m/type/Int.java:47`
   - **Methods to remove:**
     - `Int plus(Int rhs)` (line 88)
     - `Int mult(Int rhs)` (line 93)
     - `Int neg()` (line 98)
   - **Keep helper methods:** `zero()` (line 78), `one()` (line 83)
   - **Instructions already handle:** `PLUS_INST_TID`, `MULT_INST_TID`, `NEG_INST_TID` (lines 116-118)

### 3. **Real** - `Mono, Ring.O<Real>, MultGroup.O<Real>`
   - **Location:** `src/main/java/studio/phaseshift/metatron/isa/m/type/Real.java:42`
   - **Methods to remove:**
     - `Real plus(Real rhs)` (line 94)
     - `Real mult(Real rhs)` (line 99)
     - `Real neg()` (line 103)
     - `Real inv()` (line 84)
     - `Real div(Real rhs)` (line 89)
   - **Keep helper methods:** `zero()` (line 74), `one()` (line 79)
   - **Instructions already handle:** `PLUS_INST_TID`, `MULT_INST_TID`, `NEG_INST_TID`, `DIV_INST_TID`, `INV_INST_TID` (lines 120-126)

### 4. **Uri** - `Mono, Ring.O<Uri>`
   - **Location:** `src/main/java/studio/phaseshift/metatron/isa/m/type/Uri.java:50`
   - **Methods to remove:**
     - `Uri plus(Uri rhs)` (line 111)
     - `Uri mult(Uri rhs)` (line 100)
     - `Uri neg()` (line 116)
   - **Keep helper methods:** `zero()` (line 106), `one()` (line 95)
   - **Instructions already handle:** `PLUS_INST_TID`, `MULT_INST_TID` (lines 179-180)

### 5. **Str** - `Mono, PlusMonoid.O<Str>`
   - **Location:** `src/main/java/studio/phaseshift/metatron/isa/m/type/Str.java:49`
   - **Methods to remove:**
     - `Str plus(Str rhs)` (line 105) - **NOTE: No plus() method found in file!**
   - **Keep helper methods:** `zero()` (line 73)
   - **Instructions already handle:** `PLUS_INST_TID` (line 103)

### 6. **Lst** - `Poly<Lst, List<Obj>>, PlusMonoid.O<Lst>`
   - **Location:** `src/main/java/studio/phaseshift/metatron/isa/m/type/Lst.java:53`
   - **Methods to remove:**
     - `Lst plus(Lst rhs)` (line 183)
   - **Keep helper methods:** `zero()` (line 178)
   - **Instructions already handle:** `PLUS_INST_TID` (line 234)

### 7. **Rec** - `Poly<Rec, Map<Obj, Obj>>, PlusMonoid.O<Rec>`
   - **Location:** `src/main/java/studio/phaseshift/metatron/isa/m/type/Rec.java:51`
   - **Methods to remove:**
     - `Rec plus(Rec rhs)` (line 172)
   - **Keep helper methods:** `zero()` (line 62)
   - **Instructions already handle:** `PLUS_INST_TID` (line 252)

### 8. **Bytes** - `Mono, PlusMonoid.O<Bytes>`
   - **Location:** `src/main/java/studio/phaseshift/metatron/isa/m/type/Bytes.java:43`
   - **Methods to remove:**
     - `Bytes plus(Bytes rhs)` (line 76)
   - **Keep helper methods:** `zero()` (line 71)
   - **Instructions already handle:** `PLUS_INST_TID` (line 112)

### 9. **Objs** - `Obj, PlusMonoid.O<Objs>`
   - **Location:** `src/main/java/studio/phaseshift/metatron/isa/m/type/Objs.java:40`
   - **Methods to remove:**
     - `Objs plus(Objs other)` (line 102)
   - **Keep helper methods:** `zero()` (line 97)
   - **Instructions:** No PLUS_INST_TID found (may need to add)

### 10. **Call** - `Obj, Ring<Call>`
   - **Location:** `src/main/java/studio/phaseshift/metatron/isa/m/type/Call.java:36`
   - **Methods to remove:**
     - `Call plus(Call rhs)` (line 137)
     - `Call mult(Call rhs)` (line 149)
     - `Call zero()` (line 132)
     - `Call one()` (line 144)
     - `Call neg()` (line 154)
   - **Instructions:** Need to check if instructions exist

### 11. **Fail** - `Obj, PlusMonoid<Fail>`
   - **Location:** `src/main/java/studio/phaseshift/metatron/isa/m/type/Fail.java:42`
   - **Methods to remove:**
     - `Fail plus(Fail rhs)` (line 52) - declared but implementation not shown
   - **Instructions:** Need to check if instructions exist

### 12. **Bool** - `Mono` (NO algebraic interfaces!)
   - **Location:** `src/main/java/studio/phaseshift/metatron/isa/m/type/Bool.java:39`
   - **Has helper methods:** `zero()` (line 65), `one()` (line 69)
   - **Instructions handle:** `PLUS_INST_TID` (line 81), `MULT_INST_TID` (line 82)
   - **Action:** None needed - already correct!

---

## Code That Uses Algebraic Methods

### Direct Method Calls Found (69 matches):

1. **Rec.java** (line 176): Uses `isPlusMonoid()` check and calls `.plus()`
   ```java
   v.isPlusMonoid() && o.isPlusMonoid() ? (Obj) v.<PlusMonoid.O>as().plus(o.jvm().get1().<PlusMonoid.O>as()) :
   ```
   - **Action:** Replace with instruction-based approach

2. **Objs.java** (line 105): Calls `.plus()` on PlusMonoid
   ```java
   final PlusMonoid.O<?> result = ... ((PlusMonoid.O) first).plus((PlusMonoid.O) second);
   ```
   - **Action:** Replace with instruction-based approach

3. **Call.java** (lines 112, 140, 152): Uses `.plus()` and `.mult()`
   - **Action:** Replace with instruction-based approach

4. **Str.java** (line 109): Uses `.plus()` in WITHIN_INST_TID
   ```java
   .reduce((a, b) -> (PlusMonoid.O) a.plus(b))
   ```
   - **Action:** Replace with instruction-based approach

5. **Multiple c() operations** using `.mult()`, `.plus()`, `.div()` on cInt
   - These are on the coefficient type, not Obj types
   - **Action:** These should remain as they are internal to the coefficient system

6. **SUM_INST_TID and PROD_INST_TID implementations** use `.plus()` and `.mult()`
   - Found in: Int.java, Real.java, Uri.java, Lst.java
   - **Action:** Replace with instruction application instead of direct method calls

---

## Obj.java Helper Methods

**Location:** `src/main/java/studio/phaseshift/metatron/isa/m/type/Obj.java`

These type-checking methods exist:
- `boolean isRing()` (line 422)
- `boolean isPlusMonoid()` (line 426)
- `boolean isMultMonoid()` (line 430)

**Action:** These can remain as they're useful for runtime type checking, but they should be used carefully since removing the interfaces means these will return false.

---

## Refactoring Steps

### Phase 1: Update Type Declarations
1. Remove algebraic interface extensions from each type:
   - `Rel`: Remove `MultMonoid.O<Rel>, PlusMonoid.O<Rel>`
   - `Int`: Remove `Ring.O<Int>`
   - `Real`: Remove `Ring.O<Real>, MultGroup.O<Real>`
   - `Uri`: Remove `Ring.O<Uri>`
   - `Str`: Remove `PlusMonoid.O<Str>`
   - `Lst`: Remove `PlusMonoid.O<Lst>`
   - `Rec`: Remove `PlusMonoid.O<Rec>`
   - `Bytes`: Remove `PlusMonoid.O<Bytes>`
   - `Objs`: Remove `PlusMonoid.O<Objs>`
   - `Call`: Remove `Ring<Call>`
   - `Fail`: Remove `PlusMonoid<Fail>`

### Phase 2: Remove Algebraic Methods
For each type, remove these methods (but keep helper methods):
- `plus()`
- `mult()`
- `neg()`
- `inv()` (Real only)
- `div()` (Real only)
- `minus()` (if explicitly defined)

**Keep these helper methods:**
- `zero()`
- `one()`
- `isZero()`
- `isOne()`

### Phase 3: Update Code Using Algebraic Methods
Replace direct method calls with instruction-based operations:

**Pattern to replace:**
```java
// OLD
obj1.plus(obj2)

// NEW
obj1.apply(inst(PLUS_INST_TID, obj2))
```

**Locations to update:**
1. `Rec.java:176` - Replace `.plus()` call
2. `Objs.java:105` - Replace `.plus()` call
3. `Call.java:112, 140, 152` - Replace `.plus()` and `.mult()` calls
4. `Str.java:109` - Replace `.plus()` in reduce
5. All `SUM_INST_TID` implementations - Replace `.plus()` with instruction application
6. All `PROD_INST_TID` implementations - Replace `.mult()` with instruction application

### Phase 4: Update Type Checking
Review and update code that uses:
- `isRing()`
- `isPlusMonoid()`
- `isMultMonoid()`

These methods will no longer work after removing the interfaces. Consider:
- Removing these methods entirely, OR
- Implementing them based on instruction availability instead of interface checks

### Phase 5: Testing
1. Run all existing tests
2. Verify instruction-based operations work correctly
3. Verify type refinements don't break
4. Check that helper methods (`zero()`, `one()`, etc.) still work

---

## Benefits of This Refactoring

1. **Type Safety:** Java type system won't make false promises about algebraic properties
2. **Flexibility:** Instructions can validate constraints at runtime
3. **Refinement Support:** Type refinements can have specialized instruction implementations
4. **Consistency:** All operations go through the same instruction layer
5. **Correctness:** Prevents invalid operations on refined types (e.g., `nat.neg()`)

---

## Potential Issues

1. **Performance:** Instruction-based calls may be slower than direct method calls
2. **Code Complexity:** More verbose code when performing algebraic operations
3. **Breaking Changes:** Any external code using these methods will break
4. **Helper Methods:** Need to decide if `zero()`, `one()`, etc. should remain

---

## Questions for User

1. Should we keep helper methods (`zero()`, `one()`, `isZero()`, `isOne()`) or remove them too?
2. What should happen to `isRing()`, `isPlusMonoid()`, `isMultMonoid()` methods in Obj?
3. Should we create utility methods to make instruction-based operations easier to write?
4. Are there any performance-critical sections where direct method calls are required?
