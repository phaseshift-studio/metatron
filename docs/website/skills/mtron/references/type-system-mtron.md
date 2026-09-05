---
name: type-system-mtron
description: mtron type system fundamentals — vid/tid, base types, coefficients, isa vs non-isa predicates, nominal vs structural types, type definition syntax, pattern/generic types
---

# mtron Type System

## Core concepts

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

### The `::T` suffix

`::T` lifts an object to the **type-of** that object. `int::T` means "the type of integers." `person::T` means "the type
named person."

Without `::T`, `int` is a value (the integer zero). `int::0` is a typed value (an integer zero). `int::T` is the integer
type itself.

### Base types (nominal)

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

## Coefficients (cardinality)

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

## Universal type

`#{*}::T` is the **universal type** — the root of the type hierarchy. `#` matches any type VID (polymorphic wildcard),
and `{*}` matches any cardinality (0 to ∞). Every value and every type is a `#{*}::T`. It has no predicate and accepts
everything.

Shorthand: `#::T` is often used when cardinality is known to be `{1}` (the default). `/+/+::T` is an alternate spelling.

## Type definition

### Defining a named type

The full type syntax is `tid::T[predicate][constructor]@vid`:

```mtron
mtron> [-- person is a record with age (int) and name (str) --]
mtron> person -> rec::T[?[age=>int::T,name=>str::T]]@person
mtron> [-- nat is a positive integer (predicate only, no constructor) --]
mtron> nat -> int::T[is(gt(0))]@nat
mtron> [-- nat with absolute-value constructor --]
mtron> nat -> int::T[?>0][-<|[is(lt(0)) => * -1, _ => _]>>]@nat
mtron> [-- bignat refines nat, further constraining to > 100 --]
mtron> bignat -> nat::T[is(gt(100))]@bignat
```
The `->` syntax defines a type in the current space. The right side is the full type definition; the left side is the
name under which it is stored.

### Instantiation

```mtron
mtron> [-- Create a person with named address --]
mtron> person::[name=>'enoch',age=>365]@enoch
==>person::[name=>'enoch',age=>365]@enoch
mtron> [-- Create a value and then as-cast to a type --]
mtron> 23.as(nat::T)
==>nat::23
mtron> [-- Create with explicit tid/vid --]
mtron> int::42@the_answer
==>42@the_answer
```
## Predicates

A predicate is a **constraint** that values must satisfy to be members of the type. Two families:

### Isa predicates (structural)

Created with `?[...]` — defines a required **record structure**:

```mtron
mtron> [-- being requires an age field of type int --]
mtron> being -> rec::T[?[age=>int::T]]
mtron> [-- person refines being, adding a name field --]
mtron> person -> being::T[?[name=>str::T]]
mtron> [-- team requires a flag (2-char str) and at least one member --]
mtron> team -> rec::T[?[flag=>str{2}::T, member=>being{+}::T]]
```
Field types can be optional with `?`:

```mtron
mtron> [-- address is optional (maybe present) --]
mtron> rec::T[?[name=>str::T, address=>str{?}::T]]
```
**Multi-level stacking**: a type inherits all isa constraints from its ancestors:

```mtron
mtron> [-- mortal inherits being?[age=>int::T] from person --]
mtron> mortal -> person::T[?<120]  [-- adds a non-isa constraint on top --]
```
The full predicate stack for `mortal` is: `[?<120, isa([age=>int::T,name=>str::T])]`.

### Non-isa predicates

Freeform functional constraints using instructions:

```mtron
mtron> [-- value must be greater than 0 --]
mtron> int::T[is(gt(0))]
mtron> [-- shorthand: ?>0 means "is greater than 0" --]
mtron> int::T[?>0]
mtron> [-- value must match exactly 42 --]
mtron> int::T[?=42]
mtron> [-- composition: value must be > 0 AND < 120 --]
mtron> int::T[?>0.?<120]
```
The `.` operator chains predicates: `p1.p2` means "apply p1, then apply p2 to the result." Both must succeed (AND
semantics).

**OR semantics** use split/merge:

```mtron
mtron> [-- value must be > 0 OR < 120 --]
mtron> int::T[-<[?>0,?<120]>-]
```
### Predicate vs no predicate

A type **without** a predicate is the most general type at its level — it accepts any value with the correct base type
and coefficient:

```mtron
mtron> int::T        [-- accepts any integer --]
mtron> int::T[?>0]   [-- only accepts positive integers --]
```
### Type constructors

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
mtron> [-- Predicate test: is it > 0? --]
mtron> 2.test(nat::T)        [-- true --]
==>fail::[unable to locate inst-f of test(nat::T)@<1>]@/sys/fail/788
mtron>  -2.test(nat::T)  [-- false --]
==>fail::[unable to locate inst-f of test(nat::T)@<1>]@/sys/fail/790
mtron> [-- Constructor application: coerce to fit --]
mtron> -2.as(nat::T)         [-- nat::2  (constructor applied: abs) --]
==>fail::[apply failure:
   	[lhs]    │ -2
   	 \_type  │ /m/int
   	  \_pred │ []
   	[inst]   │ as?rng=nat&dom=int(nat::T){<j>}@<1>
   	 \_dom   │ int::T
   	 \_args  │ [nat::T][MTronException<137>:-2 is not a int::T[is(gt(0))][choose([is(lt(0))=>mult(-1),id()=>id()]).rshift()]@/m/math/nat [structural]]][-2 is not a int::T[is(gt(0))][choose([is(lt(0))=>mult(-1),id()=>id()]).rshift()]@/m/math/nat [structural]]@/sys/fail/792
```
The `as()` instruction applies the constructor. If the predicate passes, the value is returned as-is. If not, the
constructor runs. If the constructor's result passes the predicate, the transformed value is returned. Otherwise, it
fails.

A type with no constructor is a pure constraint — values must already satisfy the predicate to be members.

## Nominal vs structural types

The distinction depends solely on the existence of a **predicate**:

| Kind                    | Has predicate? | Has vid?         | Example                                           |
|-------------------------|----------------|------------------|---------------------------------------------------|
| **Structural**          | yes            | optional         | `int::T[?>0]@nat` — constraint defines membership |
| **Nominal**             | no             | yes (tid ≠ vid)  | `int::T@age` — label defines membership           |
| **Base type** (nominal) | no             | yes (tid == vid) | `int::T` (= `int::T@int`) — primitive             |

- **Structural** = any type with a predicate. The predicate specifies the structural requirements a value must satisfy.
  Isa predicates (`?[...]`) constrain record fields; non-isa predicates (`is(gt(0))`) constrain by computation.
- **Nominal** (no predicate) = type distinguished purely by name/vid. When `B::T == A::T` structurally (same values) but
  have different vids, there exists only a nominal difference. A value of `int::T@age` is not the same type as a value
  of `int::T@zipcode`.
- **Base types** are nominal: `int::T` = `int::T@int`. An `int` is an `int` because it is named `int`.

A type can carry **both** a predicate and a VID: `rec::T[?[age=>int::T]]@person`. This type is structural (has a
predicate) AND named (has a VID). The predicate determines which values qualify; the vid allows nominal discrimination
from other structurally-identical types.

### Why nominal types matter

Structural types alone can over-match. A `rec::T` with name and age could represent both a human and a chicken. Nominal
types prevent this:

```mtron
mtron> being -> rec::T[?[name=>str::T,age=>int::T]]@being
mtron> human -> being::T@human
mtron> chicken -> being::T@chicken
mtron> [-- A human is NOT a chicken, despite identical structure --]
mtron>  human::[name=>'marko',age=>29].as(chicken::T)
==>fail::[apply failure:
   	[lhs]    │ human::[name=>'marko',age=>29]
   	 \_type  │ human
   	  \_pred │ [isa([name=>str::T,age=>int::T])]
   	[inst]   │ as?rng=chicken&dom=rec(chicken::T){<j>}@<1>
   	 \_dom   │ rec::T
   	 \_args  │ [chicken::T][MTronException<137>:human::[name=>'marko',age=>29] is not a being::T@chicken [nominal]]][human::[name=>'marko',age=>29] is not a being::T@chicken [nominal]]@/sys/fail/794
```
This is the difference between **experiential knowledge** (structural — what can be observed) and **authoritative
knowledge** (nominal — what has been declared).

## Type hierarchy and refinement

Types form a tree rooted at `#::T` (ALL). Each type has exactly one parent via `parentType()`:

- If `tid == vid` (base type or self-referential): parent is `#::T`
- Otherwise: parent is `T(tid)` — the base type being refined

```
mortal::T  →  person::T  →  being::T  →  rec::T  →  #{*}::T
[?<120]        [?[name=>]]   [?[age=>]]   (base)     (root/universal)
```

## Pattern and generic types

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
   	noobj       => inst?rng=#{*}&dom=#{?}(#::T)   | [inst]
   	noobj       => #{?}::T   |  \_dom
   	noobj      X=> [#::T]   |  \_args]@/sys/fail/796
```
## Type checking and casting

### `.test()` — predicate membership

Tests whether a value satisfies a type's predicate (and nominal ancestry):

```mtron
mtron> [-- value vs type --]
mtron> 1.is(int::T)           [-- true --]
==>fail::[apply failure:
   	[lhs]    │ 1
   	 \_type  │ /m/int
   	  \_pred │ []
   	[inst]   │ is?rng=int{?}&dom=int{?}(1){<j>}@<1>
   	 \_dom   │ int{?}::T
   	 \_args  │ [1][Obj$ObjType<1270>:unable to convert int::T to bool[Obj$ObjType<1270>:class studio.phaseshift.metatron.isa.m.type.impl.MInt cannot be cast to class studio.phaseshift.metatron.isa.m.type.Bool (studio.phaseshift.metatron.isa.m.type.impl.MInt and studio.phaseshift.metatron.isa.m.type.Bool are in unnamed module of loader 'app')] ← class studio.phaseshift.metatron.isa.m.type.impl.MInt cannot be cast to class studio.phaseshift.metatron.isa.m.type.Bool (studio.phaseshift.metatron.isa.m.type.impl.MInt and studio.phaseshift.metatron.isa.m.type.Bool are in unnamed module of loader 'app')]][unable to convert int::T to bool[Obj$ObjType<1270>:class studio.phaseshift.metatron.isa.m.type.impl.MInt cannot be cast to class studio.phaseshift.metatron.isa.m.type.Bool (studio.phaseshift.metatron.isa.m.type.impl.MInt and studio.phaseshift.metatron.isa.m.type.Bool are in unnamed module of loader 'app')]][class studio.phaseshift.metatron.isa.m.type.impl.MInt cannot be cast to class studio.phaseshift.metatron.isa.m.type.Bool (studio.phaseshift.metatron.isa.m.type.impl.MInt and studio.phaseshift.metatron.isa.m.type.Bool are in unnamed module of loader 'app')]@/sys/fail/798
mtron> 'a string'.is(int::T)  [-- false --]
==>fail::[apply failure:
   	[lhs]    │ 'a string'
   	 \_type  │ /m/str
   	  \_pred │ []
   	[inst]   │ is?rng=str{?}&dom=str{?}(int::T){<j>}@<1>
   	 \_dom   │ str{?}::T
   	 \_args  │ [int::T][Obj$ObjType<1270>:unable to convert type to bool[Obj$ObjType<1270>:class studio.phaseshift.metatron.isa.m.type.impl.MType cannot be cast to class studio.phaseshift.metatron.isa.m.type.Bool (studio.phaseshift.metatron.isa.m.type.impl.MType and studio.phaseshift.metatron.isa.m.type.Bool are in unnamed module of loader 'app')] ← class studio.phaseshift.metatron.isa.m.type.impl.MType cannot be cast to class studio.phaseshift.metatron.isa.m.type.Bool (studio.phaseshift.metatron.isa.m.type.impl.MType and studio.phaseshift.metatron.isa.m.type.Bool are in unnamed module of loader 'app')]][unable to convert type to bool[Obj$ObjType<1270>:class studio.phaseshift.metatron.isa.m.type.impl.MType cannot be cast to class studio.phaseshift.metatron.isa.m.type.Bool (studio.phaseshift.metatron.isa.m.type.impl.MType and studio.phaseshift.metatron.isa.m.type.Bool are in unnamed module of loader 'app')]][class studio.phaseshift.metatron.isa.m.type.impl.MType cannot be cast to class studio.phaseshift.metatron.isa.m.type.Bool (studio.phaseshift.metatron.isa.m.type.impl.MType and studio.phaseshift.metatron.isa.m.type.Bool are in unnamed module of loader 'app')]@/sys/fail/800
mtron> 2.is(nat::T)           [-- true (2 > 0) --]
==>fail::[apply failure:
   	[lhs]    │ 2
   	 \_type  │ /m/int
   	  \_pred │ []
   	[inst]   │ is?rng=int{?}&dom=int{?}(2){<j>}@<1>
   	 \_dom   │ int{?}::T
   	 \_args  │ [2][Obj$ObjType<1270>:unable to convert int::T to bool[Obj$ObjType<1270>:class studio.phaseshift.metatron.isa.m.type.impl.MInt cannot be cast to class studio.phaseshift.metatron.isa.m.type.Bool (studio.phaseshift.metatron.isa.m.type.impl.MInt and studio.phaseshift.metatron.isa.m.type.Bool are in unnamed module of loader 'app')] ← class studio.phaseshift.metatron.isa.m.type.impl.MInt cannot be cast to class studio.phaseshift.metatron.isa.m.type.Bool (studio.phaseshift.metatron.isa.m.type.impl.MInt and studio.phaseshift.metatron.isa.m.type.Bool are in unnamed module of loader 'app')]][unable to convert int::T to bool[Obj$ObjType<1270>:class studio.phaseshift.metatron.isa.m.type.impl.MInt cannot be cast to class studio.phaseshift.metatron.isa.m.type.Bool (studio.phaseshift.metatron.isa.m.type.impl.MInt and studio.phaseshift.metatron.isa.m.type.Bool are in unnamed module of loader 'app')]][class studio.phaseshift.metatron.isa.m.type.impl.MInt cannot be cast to class studio.phaseshift.metatron.isa.m.type.Bool (studio.phaseshift.metatron.isa.m.type.impl.MInt and studio.phaseshift.metatron.isa.m.type.Bool are in unnamed module of loader 'app')]@/sys/fail/802
mtron> -1.is(nat::T)          [-- false (-1 is not > 0) --]
==>fail::[apply failure:
   	[lhs]    │ -1
   	 \_type  │ /m/int
   	  \_pred │ []
   	[inst]   │ is?rng=int{?}&dom=int{?}(nat::T){<j>}@<1>
   	 \_dom   │ int{?}::T
   	 \_args  │ [nat::T][Obj$ObjType<1270>:unable to convert type to bool[Obj$ObjType<1270>:class studio.phaseshift.metatron.isa.m.type.impl.MType cannot be cast to class studio.phaseshift.metatron.isa.m.type.Bool (studio.phaseshift.metatron.isa.m.type.impl.MType and studio.phaseshift.metatron.isa.m.type.Bool are in unnamed module of loader 'app')] ← class studio.phaseshift.metatron.isa.m.type.impl.MType cannot be cast to class studio.phaseshift.metatron.isa.m.type.Bool (studio.phaseshift.metatron.isa.m.type.impl.MType and studio.phaseshift.metatron.isa.m.type.Bool are in unnamed module of loader 'app')]][unable to convert type to bool[Obj$ObjType<1270>:class studio.phaseshift.metatron.isa.m.type.impl.MType cannot be cast to class studio.phaseshift.metatron.isa.m.type.Bool (studio.phaseshift.metatron.isa.m.type.impl.MType and studio.phaseshift.metatron.isa.m.type.Bool are in unnamed module of loader 'app')]][class studio.phaseshift.metatron.isa.m.type.impl.MType cannot be cast to class studio.phaseshift.metatron.isa.m.type.Bool (studio.phaseshift.metatron.isa.m.type.impl.MType and studio.phaseshift.metatron.isa.m.type.Bool are in unnamed module of loader 'app')]@/sys/fail/804
mtron> [-- type vs type (refinement check) --]
mtron> nat::T.is(int::T)      [-- true (nat is-a int) --]
==>fail::[MAcHInE faIlEd][infinite fail-loop detected][obj/inst coefficients yielding unsolvable monad]@/sys/fail/830
mtron> int::T.is(nat::T)      [-- false (int is not-a nat) --]
==>fail::[apply failure:
   	[lhs]    │ int::T
   	 \_type  │ /m/int
   	  \_pred │ []
   	[inst]   │ is?rng=int{?}&dom=int{?}(nat::T){<j>}@<1>
   	 \_dom   │ int{?}::T
   	 \_args  │ [nat::T][Obj$ObjType<1270>:unable to convert type to bool[Obj$ObjType<1270>:class studio.phaseshift.metatron.isa.m.type.impl.MType cannot be cast to class studio.phaseshift.metatron.isa.m.type.Bool (studio.phaseshift.metatron.isa.m.type.impl.MType and studio.phaseshift.metatron.isa.m.type.Bool are in unnamed module of loader 'app')] ← class studio.phaseshift.metatron.isa.m.type.impl.MType cannot be cast to class studio.phaseshift.metatron.isa.m.type.Bool (studio.phaseshift.metatron.isa.m.type.impl.MType and studio.phaseshift.metatron.isa.m.type.Bool are in unnamed module of loader 'app')]][unable to convert type to bool[Obj$ObjType<1270>:class studio.phaseshift.metatron.isa.m.type.impl.MType cannot be cast to class studio.phaseshift.metatron.isa.m.type.Bool (studio.phaseshift.metatron.isa.m.type.impl.MType and studio.phaseshift.metatron.isa.m.type.Bool are in unnamed module of loader 'app')]][class studio.phaseshift.metatron.isa.m.type.impl.MType cannot be cast to class studio.phaseshift.metatron.isa.m.type.Bool (studio.phaseshift.metatron.isa.m.type.impl.MType and studio.phaseshift.metatron.isa.m.type.Bool are in unnamed module of loader 'app')]@/sys/fail/840
```
### `.as()` — constructor application

Applies the type's constructor to coerce a value into the type. If the value already satisfies the predicate, it is
returned as-is. Otherwise, the constructor transforms it:

```mtron
mtron> [-- nat has constructor: absolute value --]
mtron> 2.as(nat::T)             [-- nat::2  (already fits) --]
==>nat::2
mtron> -2.as(nat::T)            [-- nat::2  (constructor applied) --]
==>fail::[apply failure:
   	[lhs]    │ -2
   	 \_type  │ /m/int
   	  \_pred │ []
   	[inst]   │ as?rng=nat&dom=int(nat::T){<j>}@<1>
   	 \_dom   │ int::T
   	 \_args  │ [nat::T][MTronException<137>:-2 is not a int::T[is(gt(0))][choose([is(lt(0))=>mult(-1),id()=>id()]).rshift()]@/m/math/nat [structural]]][-2 is not a int::T[is(gt(0))][choose([is(lt(0))=>mult(-1),id()=>id()]).rshift()]@/m/math/nat [structural]]@/sys/fail/850
mtron> [-- Without a constructor, .as() is a pure test --]
mtron>  -2.as(int::T[?>0])  [-- fails: no constructor to rescue --]
==>fail::[apply failure:
   	[lhs]    │ -2
   	 \_type  │ /m/int
   	  \_pred │ []
   	[inst]   │ as?rng=int&dom=int(int::T){<j>}@<1>
   	 \_dom   │ int::T
   	 \_args  │ [int::T][MTronException<137>:-2 is not a int::T[is(gt(0))] [structural]]][-2 is not a int::T[is(gt(0))] [structural]]@/sys/fail/852
```
`.as()` is also used for nominal type casting:

```mtron
mtron> [name=>'fuzzy feet',age=>2].as(chicken::T)    [-- ok: structurally a chicken --]
==>chicken::[name=>'fuzzy feet',age=>2]
mtron> human::[name=>'marko',age=>29].as(chicken::T) [-- ERROR: nominally not a chicken --]
==>fail::[apply failure:
   	[lhs]    │ human::[name=>'marko',age=>29]
   	 \_type  │ human
   	  \_pred │ [isa([name=>str::T,age=>int::T])]
   	[inst]   │ as?rng=chicken&dom=rec(chicken::T){<j>}@<1>
   	 \_dom   │ rec::T
   	 \_args  │ [chicken::T][MTronException<137>:human::[name=>'marko',age=>29] is not a being::T@chicken [nominal]]][human::[name=>'marko',age=>29] is not a being::T@chicken [nominal]]@/sys/fail/854
```
## LCD (Lowest Common Denominator)

The most specific type that subsumes a set of types. Two types always have an LCD:

```mtron
mtron> [-- Mono with non-isa predicates: OR the constraints --]
mtron> int::T[?>0] + int::T[?<120]
==>fail::[apply failure:
   	[lhs]    │ int::T[is(gt(0))]
   	 \_type  │ /m/int
   	  \_pred │ [is(gt(0))]
   	[inst]   │ plus?rng=int&dom=int(int::T){<j>}@<1>
   	 \_dom   │ int::T
   	 \_args  │ [int::T][MTronException<137>:int::T[is(gt(0))] [type] unable to convert int::T]][int::T[is(gt(0))] [type] unable to convert int::T]@/sys/fail/856
mtron> [-- Record with isa predicates: merge fields structurally --]
mtron> rec::T[?[age=>int::T,name=>str::T]]@person
mtron> + rec::T[?[age=>int::T]]@artifact
mtron> [-- name becomes optional (str{?}) since not all inputs require it --]
mtron> [-- Disjoint hierarchies: fall back to universal type --]
mtron> int::T + str::T [-- {*}::T --]
==>fail::[apply failure:
   	[lhs]    │ int::T
   	 \_type  │ /m/int
   	  \_pred │ []
   	[inst]   │ plus?rng=str&dom=int(str::T){<j>}
   	 \_dom   │ int::T
   	 \_args  │ [str::T][MTronException<137>:int::T [int::T] unable to convert str::T]][int::T [int::T] unable to convert str::T]@/sys/fail/858
```