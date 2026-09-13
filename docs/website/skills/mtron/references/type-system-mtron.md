---
name: type-system-mtron
description: mtron type system fundamentals — vid/tid, base types, coefficients, isa vs non-isa predicates, nominal vs structural types, type definition syntax, pattern/generic types
---

# mtron type system

## core concepts

### vid and tid

Every type in mtron is defined by two URIs:

| Component | Meaning                               | Example                             |
|-----------|---------------------------------------|-------------------------------------|
| **tid**   | The **type being refined** (its base) | `rec` in `rec::T[?[age=>int::T]]`   |
| **vid**   | The **type being defined/named**      | `person` in `rec::T[?[...]]@person` |

For **values** (instances), the roles are analogous:

- **tid** = the value's type (what kind of thing it is)
- **vid** = the value's location in space (its address/identity)

```
person::[name=>'marko',age=>29]@marko
  ^^^^^                          ^^^^^
  tid (what it is)               vid (where it is)
```

### the `::T` suffix

`::T` lifts an object to the **type-of** that object. `int::T` means "the type of integers." `person::T` means "the type
named person."

Without `::T`, `int` is a value (the integer zero). `int::0` is a typed value (an integer zero). `int::T` is the integer
type itself.

### base types (nominal)

The built-in primitive types. Every type ultimately refines one of these. Base types are **nominal** — their tid equals
their vid (e.g., `int::T` = `int::T@int`). There is nothing structural distinguishing an `int` from a `str` save the
name:

| Type       | URI        | Cardinality | Description                |
|------------|------------|-------------|----------------------------|
| `int::T`   | `/m/int`   | 1           | 64-bit signed integer      |
| `real::T`  | `/m/real`  | 1           | 64-bit IEEE 754 float      |
| `str::T`   | `/m/str`   | 1           | UTF-8 string               |
| `bool::T`  | `/m/bool`  | 1           | true / false               |
| `uri::T`   | `/m/uri`   | 1           | fURI reference             |
| `bytes::T` | `/m/bytes` | 1           | raw byte array             |
| `rec::T`   | `/m/rec`   | 1           | record (key-value map)     |
| `lst::T`   | `/m/lst`   | 1           | list (ordered collection)  |
| `rel::T`   | `/m/rel`   | 1           | relation (key=>value pair) |
| `inst::T`  | `/m/inst`  | 1           | instruction (function)     |
| `code::T`  | `/m/code`  | 1           | multi-instruction block    |
| `objs::T`  | `/m/objs`  | any         | heterogeneous bag          |
| `fail::T`  | `/m/fail`  | ?           | error/failure              |
| `noobj::T` | `noobj`    | 0           | nothing / empty            |

## coefficients (cardinality)

Every type has a **coefficient** — a `[min,max]` range constraining cardinality. Written with braces:
`type{min,max}::T`.

| Syntax        | cInt range | Meaning                   |
|---------------|------------|---------------------------|
| `int::T`      | `{1,1}`    | exactly one (default)     |
| `int{2}::T`   | `{2,2}`    | exactly two integers      |
| `int{2,5}::T` | `{2,5}`    | two to five integers      |
| `int{?}::T`   | `{0,1}`    | zero or one (maybe)       |
| `int{*}::T`   | `{0,∞}`    | zero or more (maybe some) |
| `int{+}::T`   | `{1,∞}`    | one or more (some)        |
| `int{#}::T`   | `{-∞,∞}`   | any cardinality           |
| `int{0}::T`   | `{0,0}`    | zero (noobj)              |
| `int{**,}::T` | `{-∞,0}`   | zero or negative          |

Coefficients compose through multiplication (`mult`), addition (`plus`), and spanning (`span`). Two types combine their
coefficients when their values are combined — e.g., appending an `int{2}` to an `int{3}` yields `int{5}`.

## universal type

`#{*}::T` is the **universal type** — the root of the type hierarchy. `#` matches any type VID (polymorphic wildcard),
and `{*}` matches any cardinality (0 to ∞). Every value and every type is a `#{*}::T`. It has no predicate and accepts
everything.

Shorthand: `#::T` is often used when cardinality is known to be `{1}` (the default). `/+/+::T` is an alternate spelling.

## type definition

### defining a named type

The full type syntax is `tid::T[predicate][constructor]@vid`:

```mtron
mtron> person -> rec::T[?[age=>int::T,name=>str::T]]@person
==>rec::T[?[age=>int::T,name=>str::T]]@person
mtron> nat -> int::T[is(gt(0))]@nat
==>int::T[is(gt(0))]@/m/math/nat
mtron> nat -> int::T[?>0][-<|[is(lt(0)) => * -1, _ => _]>>]@nat
==>int::T[is(gt(0))][choose([is(lt(0))=>mult(-1),id()=>id()]).rshift()]@/m/math/nat
mtron> bignat -> nat::T[is(gt(100))]@bignat
==>nat::T[is(gt(100))]@bignat
```
The `->` syntax defines a type in the current space. The right side is the full type definition; the left side is the
name under which it is stored.

### instantiation

```mtron
mtron> person::[name=>'enoch',age=>365]@enoch
==>person::[name=>'enoch',age=>365]@enoch
mtron> 23.as(nat::T)
==>nat::23
mtron> int::42@the_answer
==>42@the_answer
```
## predicates

A predicate is a **constraint** that values must satisfy to be members of the type. Two families:

### isa-predicates (structural)

Created with `?[...]` — defines a required **record structure**:

```mtron
mtron> being -> rec::T[?[age=>int::T]]
==>rec::T[?[age=>int::T]]
mtron> person -> being::T[?[name=>str::T]]
==>rec::T[?[name=>str::T]]
mtron> team -> rec::T[?[flag=>str{2}::T, member=>being{+}::T]]
==>rec::T[?[
     flag=>str{2}::T,
     member=>rec{+}::T]]
```
Field types can be optional with `?`:

```mtron
mtron> rec::T[?[name=>str::T, address=>str{?}::T]]
==>rec::T[?[name=>str::T,address=>str{?}::T]]
```
**Multi-level stacking**: a type inherits all isa constraints from its ancestors:

```mtron
mtron> mortal -> person::T[?<120]  [-- adds a non-isa constraint on top --]
==>rec::T[is(lt(120))]
```
The full predicate stack for `mortal` is: `[?<120, isa([age=>int::T,name=>str::T])]`.

### non-isa predicates (nominal)

Freeform functional constraints using instructions:

```mtron
mtron> int::T[is(gt(0))]
==>int::T[is(gt(0))]
mtron> int::T[?>0]
==>int::T[is(gt(0))]
mtron> int::T[?=42]
==>int::T[is(eq(42))]
mtron> int::T[?>0.?<120]
==>int::T[is(gt(0)).is(lt(120))]
```
The `.` operator chains predicates: `p1.p2` means "apply p1, then apply p2 to the result." Both must succeed (AND
semantics).

**OR semantics** use split/merge:

```mtron
mtron> [-- value must be > 0 OR < 120 --]
mtron> int::T[-<[?>0,?<120]>-]
==>int::T[split([is(gt(0)),is(lt(120))]).merge()]
```
### predicate vs no predicate

A type **without** a predicate is the most general type at its level — it accepts any value with the correct base type
and coefficient:

```mtron
mtron> int::T        [-- accepts any integer --]
==>int::T
mtron> int::T[?>0]   [-- only accepts positive integers --]
==>int::T[is(gt(0))]
```
### type constructors

A type can also define a **constructor** — an instruction that transforms any value of the base type into a valid value
of the defined type. The constructor sits alongside the predicate in the type definition:

```
int::T[?>0][abs]@nat
  │    │     │
  │    │     └── constructor (transforms values to fit)
  │    └──────── predicate (tests if values fit)
  └───────────── tid (type being refined)
```

The predicate **tests** membership; the constructor **produces** membership:

```mtron
mtron> nat -> int::T[?>0][-<|[is(lt(0)) => * -1, _ => _]>>]
==>int::T[is(gt(0))][choose([is(lt(0))=>mult(-1),id()=>id()]).rshift()]
mtron> [-- Predicate test: is it > 0? --]
mtron> 2.isa(nat::T)           [-- true --]
==>2
mtron> -2.isa(nat::T)  [-- false --]
mtron> [-- Constructor application: coerce to fit --]
mtron> 2.as(nat::T)          [-- nat::2 --]
==>nat::2
mtron> -2.as(nat::T)         [-- nat::2  (constructor applied: abs) --]
==>fail::[inst apply failure: -2 is not a int::T[is(gt(0))][choose([is(lt(0))=>mult(-1),id()=>id()]).rshift()]@/m/math/nat [structural]]@/sys/fail/256
```
The `as()` instruction applies the constructor. If the predicate passes, the value is returned as-is. If not, the
constructor runs. If the constructor's result passes the predicate, the transformed value is returned. Otherwise, it
fails.

A type with no constructor is a pure constraint — values must already satisfy the predicate to be members.

## nominal vs structural types

The distinction depends solely on the existence of a **predicate**:

| Kind                       | Has predicate? | Has vid?         | Example                                           |
|----------------------------|----------------|------------------|---------------------------------------------------|
| **structural**             | yes            | optional         | `int::T[?>0]@nat` — constraint defines membership |
| **nominal**                | no             | yes (tid ≠ vid)  | `int::T@age` — label defines membership           |
| **base type** (structural) | no             | yes (tid == vid) | `int::T` (= `int::T@int`) — primitive             |

- **structural** = any type with a predicate. The predicate specifies the structural requirements a value must satisfy.
  Isa predicates (`?[...]`) constrain record fields; non-isa predicates (`is(gt(0))`) constrain by computation.
- **nominal** (no predicate) = type distinguished purely by name/vid. When `B::T == A::T` structurally (same values) but
  have different vids, there exists only a nominal difference. A value of `int::T@age` is not the same type as a value
  of `int::T@zipcode`.
- **base types** are structural: `int::T` = `int::T@int`. An `int` is an `int` because of an internal structure outside
  the purview of the metatron vm.

A type can carry **both** a predicate and a VID: `rec::T[?[age=>int::T]]@person`. This type is structural (has a
predicate) AND named (has a VID). The predicate determines which values qualify; the vid allows nominal discrimination
from other structurally-identical types.

### why nominal types matter

Structural types alone can over-match. A `rec::T` with name and age could represent both a human and a chicken. Nominal
types prevent this:

```mtron
mtron> being -> rec::T[?[name=>str::T,age=>int::T]]@being
==>rec::T[?[name=>str::T,age=>int::T]]
mtron> human -> being::T@human
==>being::T@human
mtron> chicken -> being::T@chicken
==>being::T@chicken
mtron> [-- A human is NOT a chicken, despite identical structure --]
mtron> human::[name=>'marko',age=>29].as(chicken::T)
==>fail::[inst apply failure: human::[name=>'marko',age=>29] is not a being::T@chicken [nominal]]@/sys/fail/286
```
This is the difference between **experiential knowledge** (structural — what can be observed) and **authoritative
knowledge** (nominal — what has been declared).

## type hierarchy and refinement

Types form a tree rooted at `#::T` (ALL). Each type has exactly one parent via `parentType()`:

- If `tid == vid` (base type or self-referential): parent is `#::T`
- Otherwise: parent is `T(tid)` — the base type being refined

```
mortal::T  →  person::T  →  being::T  →  rec::T  →  #{*}::T
[?<120]        [?[name=>]]   [?[age=>]]   (base)     (root/universal)
```

## pattern and generic types

URIs with wildcards create **pattern types** that match multiple concrete types:

| Pattern     | Matches                                           |
|-------------|---------------------------------------------------|
| `#{*}::T`   | everything (universal: any vid × any cardinality) |
| `#::T`      | any type vid (default cardinality {1})            |
| `/m/+::T`   | any base type under `/m/`                         |
| `/m/+/+::T` | any type two levels under `/m/`                   |
| `int{*}::T` | integers of any cardinality                       |
| `int{?}::T` | zero or one integer                               |

**Generic types** use polymorphic URIs:

```mtron
mtron> [-- a function from any type to maybe some of any type --]
mtron> /m/inst?#{*}<=#{?}(#::T)
==>fail::[unable to determine inst function:
   	noobj       => inst?rng=#{*}&dom=#{?}(#::T)@<0>   | [inst]
   	noobj       => #{?}::T   |  \_dom
   	noobj      X=> [#::T]   |  \_args]@/sys/fail/296
```
## type checking and casting

### `.test()` — predicate membership

Tests whether a value satisfies a type's predicate (and nominal ancestry):

```mtron
mtron> [-- value vs type --]
mtron> 1.isa(int::T)           [-- true --]
==>1
mtron> 'a string'.isa(int::T)  [-- false --]
mtron> 2.isa(nat::T)           [-- true (2 > 0) --]
==>2
mtron> -1.isa(nat::T)          [-- false (-1 is not > 0) --]
mtron> [-- type vs type (refinement check) --]
mtron> nat::T.isa(int::T)      [-- true (nat is-a int) --]
==>int::T[is(gt(0))][choose([is(lt(0))=>mult(-1),id()=>id()]).rshift()]@/m/math/nat
mtron> int::T.isa(nat::T)      [-- false (int is not-a nat) --]
```
### `.as()` — constructor application

Applies the type's constructor to coerce a value into the type. If the value already satisfies the predicate, it is
returned as-is. Otherwise, the constructor transforms it:

```mtron
mtron> [-- nat has constructor: absolute value --]
mtron> 2.as(nat::T)             [-- nat::2  (already fits) --]
==>nat::2
mtron> -2.as(nat::T)            [-- nat::2  (constructor applied) --]
==>fail::[inst apply failure: -2 is not a int::T[is(gt(0))][choose([is(lt(0))=>mult(-1),id()=>id()]).rshift()]@/m/math/nat [structural]]@/sys/fail/374
mtron> [-- Without a constructor, .as() is a pure test --]
mtron> -2.as(int::T[?>0])  [-- fails: no constructor to rescue --]
==>fail::[inst apply failure: -2 is not a int::T[is(gt(0))] [structural]]@/sys/fail/386
```
`.as()` is also used for nominal type casting:

```mtron
mtron> [name=>'fuzzy feet',age=>2].as(chicken::T)    [-- ok: structurally a chicken --]
==>chicken::[name=>'fuzzy feet',age=>2]
mtron> human::[name=>'marko',age=>29].as(chicken::T) [-- ERROR: nominally not a chicken --]
==>fail::[inst apply failure: human::[name=>'marko',age=>29] is not a being::T@chicken [nominal]]@/sys/fail/402
```
## lowest common denominator

The most specific type that subsumes a set of types. Two types always have an LCD:

```mtron
mtron> [-- mono with non-isa predicates: OR the constraints --]
mtron> int::T[?>0] + int::T[?<120]
==>fail::[inst apply failure: int::T[is(gt(0))] [type] unable to convert int::T]@/sys/fail/414
mtron> [-- rec with isa predicates: merge fields structurally --]
mtron> rec::T[?[age=>int::T,name=>str::T]]@person + rec::T[?[age=>int::T]]@artifact
==>fail::[inst apply failure: unable to convert type to rec::T[Obj<635>:class studio.phaseshift.metatron.isa.m.type.impl.MType cannot be cast to class studio.phaseshift.metatron.isa.m.type.Rec (studio.phaseshift.metatron.isa.m.type.impl.MType and studio.phaseshift.metatron.isa.m.type.Rec are in unnamed module of loader 'app')]]@/sys/fail/426
mtron> [-- Disjoint hierarchies: fall back to universal type --]
mtron> int::T + str::T
==>fail::[inst apply failure: int::T [int::T] unable to convert str::T]@/sys/fail/438
```