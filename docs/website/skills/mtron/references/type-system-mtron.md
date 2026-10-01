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

### creating a type

A type is created with `tid::T[predicate][constructor]@vid` — a bare statement; the predicate, the constructor, and the
vid are each optional, and a type without a vid is a **lambda** (ephemeral) type:

```mtron
creating types
mtron> import(/m/math,math)      [-- module import, namespaced math — its types read math:nat, math:minute, ... --]
mtron> int::T[is(gt(0))]@posint  [-- a new member of the hierarchy: integers greater than zero --]
mtron> posint::T[is(gt(100))]@hundreds
mtron> int::T[is(gt(0))][mult(10)]@int2x
mtron> rec::T[?[name=>str::T,age=>int::T]]@being
```
A created type is first-class: it refines (`posint` refines `int`), is refined (`hundreds` refines `posint`), builds
values under its own name, and other values can be cast onto it. The stock types the VM ships with (base types,
`person`, and the module types the boot loads) are created the same way — you just don't spell the creation.

One distinction that matters: `xxx -> <type>` is *not* type creation. The arrow writes a **type value** to the uri
`/xxx` — a reference (an alias) that takes over the short name for value lookups (`*xxx`) — while type references
(`xxx::T`) still resolve to the real type when one exists. A refinement built on a bare reference with no type behind
it (`xxx::T[?]@yyy`) lands off the type hierarchy; types refine types, not references.

### instantiation

```mtron
mtron> hundreds::150                  [-- 150, checked against the whole predicate stack, stamped --]
==>hundreds::150
mtron> 23.as(posint::T)               [-- posint::23 --]
==>posint::23
mtron> being::[name=>'marko',age=>29] [-- a rec built under its type --]
==>being::[
    name=>'marko',
    age=>29]
mtron> int::42@the_answer             [-- a plain value, addressed in the space --]
==>42@the_answer
```
## predicates

A predicate is a **constraint** that values must satisfy to be members of the type. Two families:

### isa-predicates (structural)

Created with `?[...]` — defines a required **record structure** (`being`, created above, is one of these):

```mtron
mtron> rec::T[?[age=>int::T]]                              [-- a lambda type: a rec whose age field is an int --]
mtron> rec::T[?[name=>str::T, address=>str{?}::T]]         [-- address optional (str{?}::T) --]
mtron> rec::T[?[flag=>str{2}::T, member=>being{+}::T]]@roster
```
A field's type can be any type — including a lambda type or a created one like `being`.

**Multi-level stacking**: every refinement inherits the **whole predicate stack** of its ancestors, and the stack is
checked level by level:

```mtron
mtron> [-- hundreds::T = [is(gt(100))] on top of posint's [is(gt(0))] on top of int --]
mtron> hundreds::150                [-- 150  (passes both levels) --]
==>hundreds::150
mtron> hundreds::50         [-- dies at posint's level — the ancestor stack is enforced, not just the top --]
==>fail::[50 is not a posint::T[is(gt(100))]@hundreds
   	while parsing: hundreds::50]@/sys/fail/640
```
### non-isa predicates (nominal)

Freeform functional constraints using instructions:

```mtron
mtron> int::T[is(gt(0))]
mtron> int::T[?>0]
mtron> int::T[?=42]
mtron> int::T[?>0.?<120]
```
The `.` operator chains predicates: `p1.p2` means "apply p1, then apply p2 to the result." Both must succeed (AND
semantics).

**OR semantics** use split/merge:

```mtron
mtron> [-- value must be > 0 OR < 120 --]
mtron> int::T[-<[?>0,?<120]>-]
```
### predicate vs no predicate

A type **without** a predicate is the most general type at its level — it accepts any value with the correct base type
and coefficient:

```mtron
mtron> int::T        [-- accepts any integer --]
mtron> int::T[?>0]   [-- only accepts positive integers --]
```
### type constructors

A type can also carry a **constructor** — an instruction the type applies to values. The constructor sits alongside
the predicate in the type definition:

```
int::T[is(gt(0))][abs]@intabs
  │    │            │
  │    │            └── constructor (an instruction, applied to the value)
  │    └───────────── predicate (tests whether a value fits)
  └────────────────── tid (type being refined)
```

Two doors take a value through a type, and they treat the constructor differently.

**Construction applies the constructor.** `type::value` builds the value by running the constructor, so at this door
a negative is welcome — it is simply made positive on the way in (created below with an absolute-value constructor):

```mtron
construction: type::value runs the constructor
mtron> int::T[is(gt(0))][-<|[is(lt(0)) => * -1, _ => _]>>]@intabs
mtron> intabs::-2                       [-- intabs::2  (the constructor ran on the way in) --]
==>intabs::2
mtron> intabs::2                        [-- intabs::2 --]
==>intabs::2
```
**Casting tests first.** `.as(type)` checks the predicate before the constructor gets a turn, so a value that cannot
fit is refused rather than coerced; a value that does fit still goes through the constructor:

```mtron
casting: the predicate gates before the constructor
mtron> 2.isa(intabs::T)                 [-- 2  (the predicate admits it) --]
==>2
mtron> -2.isa(intabs::T)                [-- noobj  (the filter drops it: the verdict doubles as the output) --]
mtron> 2.as(intabs::T)                  [-- intabs::2  (admitted; the id branch of the ctor leaves it as-is) --]
==>intabs::2
mtron> -2.as(intabs::T)         [-- refused: the predicate tests before the constructor could run --]
==>fail::[inst apply failure: -2 is not a int::T[is(gt(0))][choose([is(lt(0))=>mult(-1),id()=>id()]).rshift()]@intabs [structural] (at /m/inst/as@1)]@/sys/fail/642
```
A converting constructor is visible the same way — it runs on the admitted value, and on nothing else:

```mtron
a converting constructor (int2x, created above)
mtron> int2x::2                         [-- int2x::20  (the constructor mult(10) ran on the way in) --]
==>int2x::20
mtron> 2.as(int2x::T)                   [-- int2x::20  (admitted at the gate, then converted) --]
==>int2x::20
mtron> -2.as(int2x::T)          [-- refused for the same reason — the gate runs first --]
==>fail::[inst apply failure: -2 is not a int::T[is(gt(0))][mult(10)]@int2x [structural] (at /m/inst/as@1)]@/sys/fail/644
```
A type with no constructor is a pure constraint — values must already satisfy the predicate to be members; there is
nothing to run them through.

## nominal vs structural types

The distinction depends solely on the existence of a **predicate**:

| Kind                       | Has predicate? | Has vid?         | Example                                           |
|----------------------------|----------------|------------------|---------------------------------------------------|
| **structural**             | yes            | optional         | `int::T[?>0]@posint` — constraint defines membership |
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
mtron> being::T@human                 [-- nominal siblings under being --]
mtron> being::T@chicken
mtron> [-- A human is NOT a chicken, despite identical structure --]
mtron> human::[name=>'marko',age=>29].as(chicken::T)
==>fail::[inst apply failure: human::[name=>'marko',age=>29] is not a being::T@chicken [nominal] (at /m/inst/as)]@/sys/fail/650
```
This is the difference between **experiential knowledge** (structural — what can be observed) and **authoritative
knowledge** (nominal — what has been declared).

## type hierarchy and refinement

Types form a tree rooted at `#{*}::T` (ALL). Each type has exactly one parent — the type its tid refines:

- If `tid == vid` (base type or self-referential): parent is `#{*}::T`
- Otherwise: parent is `T(tid)` — the base type being refined

```
hundreds::T  →  posint::T  →  int::T      (the int-branch, built above)
[is(gt(100))]   [is(gt(0))]   (base)
```

You can walk the hierarchy back to the root:

```mtron
[-- the hierarchy walk — verified under a production VM boot --]
hundreds::T.type()                  [-- posint — the parent --]
hundreds::T.type().type()           [-- int --]
hundreds::T.repeat?<=int(code=>type(),until=>vid().?=#,emit=>true)
                                    [-- the whole chain, emitted one type per line, until the universal root --]
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

**Generic types** use polymorphic URIs — `#{*}::T` is the universal type that every value belongs to, which is why
it shows up in every contract: `inst?rng=#{*}&dom=#{?}(<#>::T)` reads "an instruction from maybe some of *any*
input to any output," and it is the notation `?docq` prints for the insts you see everywhere in these docs:

```mtron
mtron> 1.isa(#{*}::T)                   [-- 1  (every value is a citizen of the universal type) --]
==>1
mtron> 'mtron'.isa(#{*}::T)             [-- 'mtron' --]
==>'mtron'
mtron> int::T.isa(#{*}::T)              [-- noobj  (the `isa` rows admit values, not types) --]
```
Note the direction: values test against `#{*}::T` and pass, while type-vs-type — "is `posint` a refinement of
`int`?" — is asked of the type *hierarchy* (see *type hierarchy and refinement* above), not of a value.

## type checking and casting

### `.test()` — predicate membership

Tests whether a value satisfies a type's predicate (and nominal ancestry):

```mtron
mtron> 1.isa(int::T)           [-- 1  (admitted: the value is printed) --]
==>1
mtron> 'a string'.isa(int::T)  [-- noobj  (refused: the filter prints nothing) --]
mtron> 2.isa(posint::T)        [-- 2  (admitted: 2 > 0) --]
==>2
mtron> -1.isa(posint::T)       [-- noobj  (refused: -1 is not > 0) --]
```
So the filter's verdict doubles as its output: admit and the value comes back, refuse and there is nothing to
print. (Refining *type against type* — "is `hundreds` a refinement of `posint`?" — is a question for the type
hierarchy, seen in *type hierarchy and refinement*.)

### `.as()` — the cast

`.as(type)` is the cast: the type's predicate tests the value first, and only an admitted value is re-stamped
with the type's vid (and, if the type carries one, run through its constructor — see *type constructors* above):

```mtron
mtron> [-- intabs has a constructor (absolute value), but the gate still runs first --]
mtron> 2.as(intabs::T)          [-- intabs::2  (admitted, re-stamped) --]
==>intabs::2
mtron> -2.as(intabs::T) [-- refused: -2 is not an intabs — construction (intabs::-2) is the door that coerces --]
==>fail::[inst apply failure: -2 is not a int::T[is(gt(0))][choose([is(lt(0))=>mult(-1),id()=>id()]).rshift()]@intabs [structural] (at /m/inst/as@1)]@/sys/fail/652
mtron> [-- Without a constructor the cast is a pure test --]
mtron> -2.as(int::T[?>0])  [-- fails: a lambda type, no constructor, nothing to coerce --]
==>fail::[inst apply failure: -2 is not a int::T[is(gt(0))] [structural] (at /m/inst/as@1)]@/sys/fail/654
```
`.as()` is also used for nominal type casting — a rec is admitted to a nominal rec type when it nominally fits,
and the stamp records the fit:

```mtron
mtron> [name=>'fuzzy feet',age=>2].as(chicken::T)    [-- chicken::[name=>'fuzzy feet',age=>2]  (nominally a being: the stamp goes on) --]
==>chicken::[
    name=>'fuzzy feet',
    age=>2]
mtron> human::[name=>'marko',age=>29].as(chicken::T)  [-- refused: a human is not a chicken, nominally --]
==>fail::[inst apply failure: human::[name=>'marko',age=>29] is not a being::T@chicken [nominal] (at /m/inst/as)]@/sys/fail/660
```
Note the asymmetry: an anonymous rec has no lineage to contradict it, so its fields alone decide the fit — the
`chicken` demands `age=>int::T`, and the rec supplies it. A stamped `human`, though, carries a lineage — `human`
and `chicken` are *siblings* under `being`, and a sibling does not refine a sibling. Nominal fit follows the actual
refinement path, not structural overlap.

## lowest common denominator

The most specific type that subsumes a set of types. The VM computes LCDs on the way: a mapped result over mixed
values, a rec whose fields carry different types, a poly whose members disagree — each settles on the LCD of the
pieces it is combining.

The `lcd()` instruction asks for one on demand over a set of related types — verified under a production VM boot:

```mtron
int::T[?>1]@aa
aa::T[?>2]@bb
bb::T[?>3]@cc
aa::T[?>22]@aabb
aabb::T[?>33]@cccc
lcd(aabb::T,bb::T,cccc::T)      [-- aabb, the set's denominator --]
lcd(bb::T,cc::T)
```

`type + type` itself has no instruction — summing types is refused (the fail text you get today, shown not run):

```mtron
posint::T + hundreds::T         [-- fail: int::T[is(gt(0))]@posint [type] unable to convert int::T (at plus) --]
```