---
name: type-system-mtron
description: mtron type system fundamentals — vid/tid, base types, coefficients, isa vs non-isa predicates, nominal vs structural types, type definition syntax, pattern/generic types
---

# mtron Type System

## Core concepts

### vid and tid

Every type in mtron is defined by two URIs:

| Component | Meaning | Example |
|---|---|---|
| **tid** | The **type being refined** (its base) | `rec` in `rec::T[?[age=>int::T]]` |
| **vid** | The **type being defined/named** | `person` in `rec::T[?[...]]@person` |

For **values** (instances), the roles are analogous:
- **tid** = the value's type (what kind of thing it is)
- **vid** = the value's location in space (its address/identity)

```
person::[name=>'marko',age=>29]@marko
  ^^^^^                          ^^^^^
  tid (what it is)               vid (where it is)
```

### The `::T` suffix

`::T` lifts an object to the **type-of** that object. `int::T` means "the type of integers." `person::T` means "the type named person."

Without `::T`, `int` is a value (the integer zero). `int::0` is a typed value (an integer zero). `int::T` is the integer type itself.

### Base types (nominal)

The built-in primitive types. Every type ultimately refines one of these. Base types are **nominal** — their tid equals their vid (e.g., `int::T` = `int::T@int`). There is nothing structural distinguishing an `int` from a `str` save the name:

| Type | URI | Cardinality | Description |
|---|---|---|---|
| `int::T` | `/m/int` | 1 | 64-bit signed integer |
| `real::T` | `/m/real` | 1 | 64-bit IEEE 754 float |
| `str::T` | `/m/str` | 1 | UTF-8 string |
| `bool::T` | `/m/bool` | 1 | true / false |
| `uri::T` | `/m/uri` | 1 | fURI reference |
| `bytes::T` | `/m/bytes` | 1 | raw byte array |
| `rec::T` | `/m/rec` | 1 | record (key-value map) |
| `lst::T` | `/m/lst` | 1 | list (ordered collection) |
| `rel::T` | `/m/rel` | 1 | relation (key=>value pair) |
| `inst::T` | `/m/inst` | 1 | instruction (function) |
| `code::T` | `/m/code` | 1 | multi-instruction block |
| `objs::T` | `/m/objs` | any | heterogeneous bag |
| `fail::T` | `/m/fail` | ? | error/failure |
| `noobj::T` | `noobj` | 0 | nothing / empty |

## Coefficients (cardinality)

Every type has a **coefficient** — a `[min,max]` range constraining cardinality. Written with braces: `type{min,max}::T`.

| Syntax | cInt range | Meaning |
|---|---|---|
| `int::T` | `{1,1}` | exactly one (default) |
| `int{2}::T` | `{2,2}` | exactly two integers |
| `int{2,5}::T` | `{2,5}` | two to five integers |
| `int{?}::T` | `{0,1}` | zero or one (maybe) |
| `int{*}::T` | `{0,∞}` | zero or more (maybe some) |
| `int{+}::T` | `{1,∞}` | one or more (some) |
| `int{#}::T` | `{-∞,∞}` | any cardinality |
| `int{0}::T` | `{0,0}` | zero (noobj) |
| `int{**,}::T` | `{-∞,0}` | zero or negative |

Coefficients compose through multiplication (`mult`), addition (`plus`), and spanning (`span`). Two types combine their coefficients when their values are combined — e.g., appending an `int{2}` to an `int{3}` yields `int{5}`.

## Universal type

`#{*}::T` is the **universal type** — the root of the type hierarchy. `#` matches any type VID (polymorphic wildcard), and `{*}` matches any cardinality (0 to ∞). Every value and every type is a `#{*}::T`. It has no predicate and accepts everything.

Shorthand: `#::T` is often used when cardinality is known to be `{1}` (the default). `/+/+::T` is an alternate spelling.

## Type definition

### Defining a named type

The full type syntax is `tid::T[predicate][constructor]@vid`:

```mtron
-- person is a record with age (int) and name (str) --
person -> rec::T[?[age=>int::T,name=>str::T]]@person

-- nat is a positive integer (predicate only, no constructor) --
nat -> int::T[is(gt(0))]@nat

-- nat with absolute-value constructor --
nat -> int::T[?>0][-<|[is(lt(0)) => * -1, _ => _]>>]@nat

-- bignat refines nat, further constraining to > 100 --
bignat -> nat::T[is(gt(100))]@bignat
```

The `->` syntax defines a type in the current space. The right side is the full type definition; the left side is the name under which it is stored.

### Instantiation

```mtron
-- Create a person with named address --
person::[name=>'enoch',age=>365]@enoch

-- Create a value and then as-cast to a type --
23.as(nat::T)

-- Create with explicit tid/vid --
int::42@the_answer
```

## Predicates

A predicate is a **constraint** that values must satisfy to be members of the type. Two families:

### Isa predicates (structural)

Created with `?[...]` — defines a required **record structure**:

```mtron
-- being requires an age field of type int --
being -> rec::T[?[age=>int::T]]

-- person refines being, adding a name field --
person -> being::T[?[name=>str::T]]

-- team requires a flag (2-char str) and at least one member --
team -> rec::T[?[flag=>str{2}::T, member=>being{+}::T]]
```

Field types can be optional with `?`:
```mtron
-- address is optional (maybe present) --
rec::T[?[name=>str::T, address=>str{?}::T]]
```

**Multi-level stacking**: a type inherits all isa constraints from its ancestors:
```mtron
-- mortal inherits being?[age=>int::T] from person --
mortal -> person::T[?<120]  -- adds a non-isa constraint on top
```

The full predicate stack for `mortal` is: `[?<120, isa([age=>int::T,name=>str::T])]`.

### Non-isa predicates

Freeform functional constraints using instructions:

```mtron
-- value must be greater than 0 --
int::T[is(gt(0))]

-- shorthand: ?>0 means "is greater than 0" --
int::T[?>0]

-- value must match exactly 42 --
int::T[?=42]

-- composition: value must be > 0 AND < 120 --
int::T[?>0.?<120]
```

The `.` operator chains predicates: `p1.p2` means "apply p1, then apply p2 to the result." Both must succeed (AND semantics).

**OR semantics** use split/merge:
```mtron
-- value must be > 0 OR < 120 --
int::T[-<[?>0,?<120]>-]
```

### Predicate vs no predicate

A type **without** a predicate is the most general type at its level — it accepts any value with the correct base type and coefficient:

```mtron
int::T        -- accepts any integer
int::T[?>0]   -- only accepts positive integers
```

### Type constructors

A type can also define a **constructor** — an instruction that transforms any value of the base type into a valid value of the defined type. The constructor sits alongside the predicate in the type definition:

```
int::T[?>0][abs]@nat
  │    │     │
  │    │     └── constructor (transforms values to fit)
  │    └──────── predicate (tests if values fit)
  └───────────── tid (type being refined)
```

The predicate **tests** membership; the constructor **produces** membership:

```mtron
nat -> int::T[?>0][-<|[is(lt(0)) => * -1, _ => _]>>]

-- Predicate test: is it > 0?
2.test(nat::T)        -- true
[ERROR] -2.test(nat::T)  -- false

-- Constructor application: coerce to fit
-2.as(nat::T)         -- nat::2  (constructor applied: abs)
```

The `as()` instruction applies the constructor. If the predicate passes, the value is returned as-is. If not, the constructor runs. If the constructor's result passes the predicate, the transformed value is returned. Otherwise, it fails.

A type with no constructor is a pure constraint — values must already satisfy the predicate to be members.

## Nominal vs structural types

The distinction depends solely on the existence of a **predicate**:

| Kind | Has predicate? | Has vid? | Example |
|---|---|---|---|
| **Structural** | yes | optional | `int::T[?>0]@nat` — constraint defines membership |
| **Nominal** | no | yes (tid ≠ vid) | `int::T@age` — label defines membership |
| **Base type** (nominal) | no | yes (tid == vid) | `int::T` (= `int::T@int`) — primitive |

- **Structural** = any type with a predicate. The predicate specifies the structural requirements a value must satisfy. Isa predicates (`?[...]`) constrain record fields; non-isa predicates (`is(gt(0))`) constrain by computation.
- **Nominal** (no predicate) = type distinguished purely by name/vid. When `B::T == A::T` structurally (same values) but have different vids, there exists only a nominal difference. A value of `int::T@age` is not the same type as a value of `int::T@zipcode`.
- **Base types** are nominal: `int::T` = `int::T@int`. An `int` is an `int` because it is named `int`.

A type can carry **both** a predicate and a VID: `rec::T[?[age=>int::T]]@person`. This type is structural (has a predicate) AND named (has a VID). The predicate determines which values qualify; the vid allows nominal discrimination from other structurally-identical types.

### Why nominal types matter

Structural types alone can over-match. A `rec::T` with name and age could represent both a human and a chicken. Nominal types prevent this:

```mtron
being -> rec::T[?[name=>str::T,age=>int::T]]@being
human -> being::T@human
chicken -> being::T@chicken

-- A human is NOT a chicken, despite identical structure --
[ERROR] human::[name=>'marko',age=>29].as(chicken::T)
```

This is the difference between **experiential knowledge** (structural — what can be observed) and **authoritative knowledge** (nominal — what has been declared).

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

| Pattern | Matches |
|---|---|
| `#{*}::T` | everything (universal: any vid × any cardinality) |
| `#::T` | any type vid (default cardinality {1}) |
| `/m/+::T` | any base type under `/m/` |
| `/m/+/+::T` | any type two levels under `/m/` |
| `int{*}::T` | integers of any cardinality |
| `int{?}::T` | zero or one integer |

**Generic types** use polymorphic URIs:
```mtron
-- a function from any type to maybe some of any type --
/m/inst?#{*}<=#{?}(#::T)
```

## Type checking and casting

### `.test()` — predicate membership

Tests whether a value satisfies a type's predicate (and nominal ancestry):

```mtron
-- Value vs type --
1.test(int::T)           -- true
'a string'.test(int::T)  -- false
int::2.test(nat::T)      -- true (2 > 0)
int::-1.test(nat::T)     -- false (-1 is not > 0)

-- Type vs type (refinement check) --
nat::T.test(int::T)      -- true (nat is-a int)
int::T.test(nat::T)      -- false (int is not-a nat)
```

### `.as()` — constructor application

Applies the type's constructor to coerce a value into the type. If the value already satisfies the predicate, it is returned as-is. Otherwise, the constructor transforms it:

```mtron
-- nat has constructor: absolute value --
2.as(nat::T)             -- nat::2  (already fits)
-2.as(nat::T)            -- nat::2  (constructor applied)

-- Without a constructor, .as() is a pure test --
[ERROR] -2.as(int::T[?>0])  -- fails: no constructor to rescue
```

`.as()` is also used for nominal type casting:
```mtron
[name=>'fuzzy feet',age=>2].as(chicken::T)    -- ok: structurally a chicken
human::[name=>'marko',age=>29].as(chicken::T) -- ERROR: nominally not a chicken
```

## LCD (Lowest Common Denominator)

The most specific type that subsumes a set of types. Two types always have an LCD:

```mtron
-- Mono with non-isa predicates: OR the constraints --
int::T[?>0] + int::T[?<120]  →  int::T[-<[?>0,?<120]>-]

-- Record with isa predicates: merge fields structurally --
rec::T[?[age=>int::T,name=>str::T]]@person
+ rec::T[?[age=>int::T]]@artifact
→ rec::T[?[age=>int::T, name=>str{?}::T]]@lcd
  -- name becomes optional (str{?}) since not all inputs require it

-- Disjoint hierarchies: fall back to universal type --
int::T + str::T  →  #{*}::T
```
