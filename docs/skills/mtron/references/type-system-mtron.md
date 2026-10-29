---
name: type-system-mtron
description: mtron type system fundamentals — vid/tid, base types, coefficients, isa vs non-isa predicates, nominal vs structural types, type definition syntax, pattern/generic types, poly types and their three readings (tensor, fibered, path), lifting and covariance
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

```mtron_pre
[HEADER] creating types
import(/m/math,math)      [-- module import, namespaced math — its types read math:nat, math:minute, ... --]
int::T[is(gt(0))]@posint  [-- a new member of the hierarchy: integers greater than zero --]
posint::T[is(gt(100))]@hundreds
int::T[is(gt(0))][mult(10)]@int2x
rec::T[?[name=>str::T,age=>int::T]]@being
```

A created type is first-class: it refines (`posint` refines `int`), is refined (`hundreds` refines `posint`), builds
values under its own name, and other values can be cast onto it. The stock types the VM ships with (base types,
`person`, and the module types the boot loads) are created the same way — you just don't spell the creation.

One distinction that matters: `xxx -> <type>` is *not* type creation. The arrow writes a **type value** to the uri
`/xxx` — a reference (an alias) that takes over the short name for value lookups (`*xxx`) — while type references
(`xxx::T`) still resolve to the real type when one exists. A refinement built on a bare reference with no type behind
it (`xxx::T[?]@yyy`) lands off the type hierarchy; types refine types, not references.

### instantiation

```mtron_pre
hundreds::150                  [-- 150, checked against the whole predicate stack, stamped --]
23.as(posint::T)               [-- posint::23 --]
being::[name=>'marko',age=>29] [-- a rec built under its type --]
int::42@the_answer             [-- a plain value, addressed in the space --]
```

## predicates

A predicate is a **constraint** that values must satisfy to be members of the type. Two families:

### isa-predicates (structural)

Created with `?[...]` — defines a required **record structure** (`being`, created above, is one of these):

```mtron_pre
rec::T[?[age=>int::T]]                              [-- a lambda type: a rec whose age field is an int --]
rec::T[?[name=>str::T, address=>str{?}::T]]         [-- address optional (str{?}::T) --]
rec::T[?[flag=>str{2}::T, member=>being{+}::T]]@roster
```

A field's type can be any type — including a lambda type or a created one like `being`.

**Multi-level stacking**: every refinement inherits the **whole predicate stack** of its ancestors, and the stack is
checked level by level:

```mtron_pre
[-- hundreds::T = [is(gt(100))] on top of posint's [is(gt(0))] on top of int --]
hundreds::150                [-- 150  (passes both levels) --]
[ERROR] hundreds::50         [-- dies at posint's level — the ancestor stack is enforced, not just the top --]
```

### non-isa predicates (nominal)

Freeform functional constraints using instructions:

```mtron_pre
int::T[is(gt(0))]
int::T[?>0]
int::T[?=42]
int::T[?>0.?<120]
```

The `.` operator chains predicates: `p1.p2` means "apply p1, then apply p2 to the result." Both must succeed (AND
semantics).

**OR semantics** use split/merge:

```mtron_pre
[-- value must be > 0 OR < 120 --]
int::T[-<[?>0,?<120]>-]
```

### predicate vs no predicate

A type **without** a predicate is the most general type at its level — it accepts any value with the correct base type
and coefficient:

```mtron_pre
int::T        [-- accepts any integer --]
int::T[?>0]   [-- only accepts positive integers --]
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

```mtron_pre
[HEADER] construction: type::value runs the constructor
int::T[is(gt(0))][-<|[is(lt(0)) => * -1, _ => _]>>]@intabs
intabs::-2                       [-- intabs::2  (the constructor ran on the way in) --]
intabs::2                        [-- intabs::2 --]
```

**Casting tests first.** `.as(type)` checks the predicate before the constructor gets a turn, so a value that cannot
fit is refused rather than coerced; a value that does fit still goes through the constructor:

```mtron_pre
[HEADER] casting: the predicate gates before the constructor
2.isa(intabs::T)                 [-- 2  (the predicate admits it) --]
-2.isa(intabs::T)                [-- noobj  (the filter drops it: the verdict doubles as the output) --]
2.as(intabs::T)                  [-- intabs::2  (admitted; the id branch of the ctor leaves it as-is) --]
[ERROR] -2.as(intabs::T)         [-- refused: the predicate tests before the constructor could run --]
```

A converting constructor is visible the same way — it runs on the admitted value, and on nothing else:

```mtron_pre
[HEADER] a converting constructor (int2x, created above)
int2x::2                         [-- int2x::20  (the constructor mult(10) ran on the way in) --]
2.as(int2x::T)                   [-- int2x::20  (admitted at the gate, then converted) --]
[ERROR] -2.as(int2x::T)          [-- refused for the same reason — the gate runs first --]
```

A type with no constructor is a pure constraint — values must already satisfy the predicate to be members; there is
nothing to run them through.

## nominal vs structural types

The distinction depends solely on the existence of a **predicate**:

| Kind                       | Has predicate? | Has vid?         | Example                                              |
|----------------------------|----------------|------------------|------------------------------------------------------|
| **structural**             | yes            | optional         | `int::T[?>0]@posint` — constraint defines membership |
| **nominal**                | no             | yes (tid ≠ vid)  | `int::T@age` — label defines membership              |
| **base type** (structural) | no             | yes (tid == vid) | `int::T` (= `int::T@int`) — primitive                |

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

```mtron_pre
being::T@human                 [-- nominal siblings under being --]
being::T@chicken

[-- A human is NOT a chicken, despite identical structure --]
[ERROR] human::[name=>'marko',age=>29].as(chicken::T)
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

```mtron_pre
1.isa(#{*}::T)                   [-- 1  (every value is a citizen of the universal type) --]
'mtron'.isa(#{*}::T)             [-- 'mtron' --]
int::T.isa(#{*}::T)              [-- noobj  (the `isa` rows admit values, not types) --]
```

Note the direction: values test against `#{*}::T` and pass, while type-vs-type — "is `posint` a refinement of
`int`?" — is asked of the type *hierarchy* (see *type hierarchy and refinement* above), not of a value.

## poly types: three readings of the bracket

A type's bracket — the `[...]` after its name — has exactly one meaning per base type. The general category is **poly
types**; `lst`, `rec` and `rel` are its *readings*, not three words for the same construct.

### the three readings

| construct    | name         | poly denotes                                                                                   | verb     |
|--------------|--------------|------------------------------------------------------------------------------------------------|----------|
| `lst[A,B]`   | TENSOR TYPE  | the position types — the coordinates of an A × B (rank = bracket length, extent = the coefficient) | CONTAINS |
| `rec[F1,F2]` | FIBERED TYPE | the FIBERS (named, distinct objects)                                                           | EXPOSES  |
| `rel[A,B,C]` | PATH TYPE    | the vertices of the right-nested chain `A => (B => C)`                                         | CHAINS   |

**graded** says something about rank × extent — how many coordinates, and how far each one runs. It is a property of a
tensor, never a synonym for *poly*.

### content vs object: why the readings do not collapse

The reading decides what the bracket's entries *are*, and that decides what a call does to the value.

**A tensor's positions are the object's content.** `lst[int]` is a list whose positions hold ints — it is not an
`int`. Position is the coordinate, so the type speaks about the value's own contents, one entry per position, and its
entries are not separate objects a call can reach: `[29,'94103']` satisfies `lst[age,zipcode]::T` (position 0 is an
age, position 1 a zipcode) and fails `lst[zipcode,age]::T`, where the coordinates are swapped.
Two consequences, and they are the same consequence twice: there is no lifting *into* a tensor's positions (`lst[int]`
hands a call no second candidate), and a tensor's entries name no other type (`lst[int]` is never an `int` — a list of
ints is not an int). The coefficient is where extent lives, not another coordinate: `lst[int{4}]` is ONE position
holding four ints, while `lst[int,int,int,int]` is four positions.

**A fibered type's entries are distinct objects.** `person[ninja]` is a person *and* a ninja; the ninja is not the
wrapper's content but an object the wrapper exposes. So a value of that type is acceptable where `ninja::T` is
expected (its base or any fiber — see *acceptance and covariance*), and an instruction declared on `ninja` applies to
the fiber's value as if it had been declared top-level.

### a bracket entry's coefficient: the multiplicity of the value AT that position or vertex

A bracket entry is a *position* (a tensor position, a path vertex), so a **suffix coefficient is the multiplicity of the
value at that position** — not another position, and not another hop. `lst[int{2},str]` is a two-position list whose
FIRST position holds two ints; `rel[int,person{2}]` is a two-vertex chain whose SECOND vertex holds two people
(`1=>{bob,marko}`, two people at that vertex — not two hops):

```mtron_pre
[HEADER] a suffix coefficient is the multiplicity of the value AT that position / vertex
wayfarer -> rec::T@wayfarer
lst[int{2},str]::[{1,2},'a']              [-- position 0 is an int{2}: one position, two ints --]
rel[int,wayfarer{2}]::(1=>{wayfarer::[name=>'bob'],wayfarer::[name=>'marko']})   [-- the second vertex holds two wayfarers --]
[ERROR] lst[int{2},str]::[1,'a']          [-- one int is an int{1}: too few for an int{2} position --]
[ERROR] rel[int,wayfarer{2}]::(1=>wayfarer::[name=>'bob'])   [-- one wayfarer is too few for a wayfarer{2} vertex --]
```

The coefficient forms say how many values that position admits, exactly as they do anywhere else in a type:

| entry         | value at that position                                  |
|---------------|---------------------------------------------------------|
| `T` / `T{1}`  | exactly one                                             |
| `T{n}`        | exactly n (`lst[int{2},str]` ⇒ `[{1,2},'a']`)           |
| `T{n,m}`      | an inclusive range — n, …, m                            |
| `T{+}`        | at least one, so the position is REQUIRED               |
| `T{?}`        | zero or one — ABSENT, or one that is still its type     |
| `T{*}`        | zero or more — absent is legal, and so is a multiplicity |

**Absence** is the zero end of the range, and it reads differently in the two readings. In a tensor the value is a
list, so a zero-admitting TRAILING position is one the shorter list does not fill — `lst[str,int{?}]` accepts `['a']`. In
a path the value is a chain, so a zero-admitting VERTEX is one the chain omits — `rel[int,wayfarer{?},str]` accepts
`1=>'tally'` (the middle vertex skipped) while `rel[int,wayfarer{+},str]` does not. A present value must still be the
entry's type at both: `{?}` is zero-or-one, not "anything when present".

**Not yet supported: a PREFIX coefficient, and repetition.** `lst[{4}int]` — "the list is composed of four ints",
spelled before the entry rather than after it — is not matchable today. Neither is a position *repetition*: a suffix
coefficient judges the multiplicity of the value already at that position, and nothing reads it as "this entry
repeats, so the chain is longer" (whether a repeated vertex should lengthen a path is not settled). A NEGATIVE (anti)
coefficient is not a multiplicity at all, so a path reports it as not yet matched rather than judging it.

### declaring a fibered type vs instantiating one

A fibered type is spelled twice, and the two brackets say different things:

```
rec[role,position]::T[?[name=>str::T, role=>rec::T, position=>rec::T]]@person
    ^^^^^^^^^^^^^^ the DECLARATION's poly NAMES the fibers (field names)

person[ninja,position]::[...]
       ^^^^^^^^^^^^^^^ the INSTANCE's poly gives their TYPES (their contents)
```

- the DECLARATION's poly is a list of **field names** — the fibers this wrapper exposes;
- the INSTANCE's poly is a list of **type arguments** — what each fiber's value is.

The declaration's entries are parameters and the instance's are arguments, so resolution substitutes one for the
other positionally: `person[role]` + `[ninja]` → `person[ninja]`, an unclaimed parameter keeps its declared entry, and
a request that names nothing leaves the declaration as it stands (`Type.Helper.instantiate`).

This asymmetry is why `fURI.test` — which compares **names**, never types — answers `person[ninja].test(person[role])`
as false: the lhs is a specialization and `role` is not the name `ninja`. The two are still related, at the type
layer, by that bound substitution rather than by the bracket claim.

### lifting (dispatch into a fiber)

An instruction declared on a fiber's type applies to the fiber's VALUE as if it were top-level: `fight?<=ninja()` on a
`person[ninja]` wrapper reaches the `ninja` object inside it, not the wrapper.

Mechanically there is nothing trait-aware anywhere in the processor. The candidate set is the wrapper **plus the
values of the fields the declaration's poly names**, and each candidate is resolved and invoked on its own — so the
"selection" is ordinary resolution, not a lookup table keyed by the declaration:

- an **untyped call lifts over all fibers**; `?<=T` selects one candidate, because a named domain is the subject the
  call is about.
- `noobj` is the multiplicity identity, so `{noobj, 29}` = `29`: a candidate the instruction is not about contributes
  nothing. A `fail::` is not the identity — it PROPAGATES — or an instruction that broke on one part of the wrapper
  would read as a success.
- candidates are **one level deep**: a candidate that is itself a wrapper is applied to as the object it is, never
  expanded again.
- `Inst.Form.typer` — `as`, `::`, `type`, `vid`, `tid` — **never lifts**. A typer retypes the one object it is given,
  so it is opaque: `as(person::T)` is both the narrowing escape from a fan-out and the exception to it (it changes the
  basis, so it cannot be distributed over the old one).
- ref-writes (`<uri> -> obj`) are address-precise and never lift. `update` (`>>=`) does fan out, since its domain is
  any obj; `as(T)` first, or `?<=T` on the call, is what narrows it back to one basis.
- a fan-out result is an **unordered multiplicity**. When order or provenance matters the user writes the keyed rec
  explicitly — one key per fiber, each narrowing its own call (`[role => *marko.describe?<=ninja(), position =>
  *marko.describe?<=position()]`). There is no keyed projection that does it for you.
- a wrapper on which **no candidate** applies yields `noobj` — a miss is not a failure. The same miss on a non-wrapper
  value is still a loud `unable to locate inst-f`, because there is nothing else to try.

### acceptance and covariance

Two questions, and they must not be confused: *may I pass this value here* (acceptance) and *which object does this
instruction run on* (dispatch).

- **A fiber named at coefficient one must be there.** The value is tested against the declaration, and a field it
  does not have fails that test. Absence is legal only where the fiber's coefficient admits zero —
  `rec[role,position{?}]` — and reading a missing key still yields `noobj`. `noobj` is a value, not a hole.
- **A predicated fiber type is satisfied by the FIBER'S VALUE.** A poly entry types one of the wrapper's fibers
  (`person[ninja]` puts a ninja at the field the declaration calls `role`), so the object a trait's shape is about
  (`ninja::T[?[name=>str::T]]`) is the value AT that fiber — `wrapper>>role` — not the wrapper's declaration. A
  *shaped nominal* trait therefore accepts a wrapper that only exposes the trait-carrying fiber: the trait's shape is
  asked of `wrapper>>role`, which is why `kestrel[falconer]` is a `falconer` although the wrapper itself declares no
  `name`. The two fallbacks: with no value at hand (a type-vs-type question) the fiber's DECLARED type answers for its
  value, and a field the wrapper's declaration itself types incompatibly (`name=>str` where the trait wants
  `name=>int`) is a veto. A field the declaration stays silent about is not a contradiction — that is exactly the case
  the fiber's value exists to answer.
- **Covariance: a fibered value is acceptable where its base OR ANY fiber is expected.** `person[ninja]` is a
  `person` and a `ninja`. The scope guard matters as much as the rule: this is `rec` only — `lst[int]` is not an
  `int`, and `object[int]` is not an `int`.
- **Covariance is type acceptance only, never dispatch.** A wrapper that merely *borrows* a fiber by covariance does
  not thereby fire that fiber's instructions; the object the definition was written for is the object it runs on.
- **Dispatch is an identity question**: a named domain selects the candidate that IS that type — the fiber whose own
  type is that name, or the wrapper when the name is the wrapper's. Borrowed names are not identities. |

A shaped nominal trait asks the value at the fiber, not the wrapper:

```mtron_pre
[HEADER] acceptance is asked of the fiber's value, not the wrapper's declaration
[-- the wrapper declares ONLY the fiber it exposes --]
kestrel -> rec[role]::T[?[role=>rec::T]]@kestrel
[-- the shaped nominal trait declares the shape of the fiber's value --]
falconer -> rec::T[?[name=>str::T]]@falconer
jarl -> kestrel[falconer]::[role=>falconer::[name=>'sigrid']]@jarl
[-- the wrapper declares no name; the falconer at `role` carries one, so jarl is a falconer --]
*jarl.isa(falconer::T)
[-- a fiber value that CONTRADICTS the trait's field type is refused, however the poly names it --]
skald -> kestrel[falconer]::[role=>[name=>7]]@skald
*skald.isa(falconer::T)
```

The claim rule is what keeps the declaration/instance asymmetry valid at the uri layer:

| lhs             | rhs             | test  |
|-----------------|-----------------|-------|
| `person`        | `person[ninja]` | true  |
| `person[ninja]` | `person`        | false |
| `person[ninja]` | `person[A]`     | true  |
| `person[ninja]` | `person[role]`  | false |

### worked example: two fibers

The declaration names both fibers; the instance types them. `position` is declared **bare** (`rec::T@position`) on
purpose — a trait declared *with* a shape asks the wrapper's declaration to pass its test, and this wrapper declares
`name`, `age`, `role` and `position`, not a position's `title`/`rank`:

```mtron_pre
[HEADER] a fibered type: the declaration names the fibers, the instance types them
person -> rec[role,position]::T[?[name=>str::T,age=>nat::T,role=>rec::T,position=>rec::T]]@person
ninja -> rec::T@ninja
position -> rec::T@position
marko -> person[ninja,position]::[name=>'marko',age=>29,role=>ninja::[name=>'dark_assassin'],position=>position::[title=>'sniper',rank=>5]]@marko
[-- untyped: both the wrapper and the role fiber carry a name --]
*marko>>name
[-- only the position fiber carries a title; noobj is the identity --]
*marko>>title
[-- a wrapper's own field reads alone --]
*marko>>age
```

The instantiation is carried by the value's own type, so `*marko.type()` reports the DECLARATION instantiated with
both arguments — `rec[role,position]::T[?[…]]@person[ninja,position]` — while `*marko` still prints the value under
that type.

A named domain picks the candidate that IS that type — the role fiber, or the position fiber, never the wrapper that
merely borrows them. A body that reads a key its own fiber does not carry has nothing to answer:

```mtron_pre
[HEADER] dispatch is identity: a named domain reaches the fiber whose own type is that name
aim -> |inst?<=position(){ >>title }
shadow -> |inst?<=ninja(){ >>name }
*marko.aim()                    [-- dom=position: the position fiber's title --]
*marko.aim?<=position()         [-- the same call with its type named at the call site --]
*marko.shadow()                 [-- dom=ninja: the role fiber's own name, never the position's title --]
```

An untyped call is about every candidate, so an instruction both fibers declare answers twice; `?<=T` narrows it, and
a typer stays on the wrapper alone:

```mtron_pre
[HEADER] untyped fans out, ?<=T selects, a typer does not lift
describe?dom=ninja -> |inst?<=ninja(){ 'a shadow' }
describe?dom=position -> |inst?<=position(){ 'sniper report' }
*marko.describe()               [-- both fibers declare a body: an unordered multiplicity --]
*marko.describe?<=position()    [-- one fiber, one answer --]
*marko.as(person::T)            [-- a typer is opaque: the wrapper alone, the instantiation erased --]
```

## type checking and casting

### `.test()` — predicate membership

Tests whether a value satisfies a type's predicate (and nominal ancestry):

```mtron_pre
1.isa(int::T)           [-- 1  (admitted: the value is printed) --]
'a string'.isa(int::T)  [-- noobj  (refused: the filter prints nothing) --]
2.isa(posint::T)        [-- 2  (admitted: 2 > 0) --]
-1.isa(posint::T)       [-- noobj  (refused: -1 is not > 0) --]
```

So the filter's verdict doubles as its output: admit and the value comes back, refuse and there is nothing to
print. (Refining *type against type* — "is `hundreds` a refinement of `posint`?" — is a question for the type
hierarchy, seen in *type hierarchy and refinement*.)

### `.as()` — the cast

`.as(type)` is the cast: the type's predicate tests the value first, and only an admitted value is re-stamped
with the type's vid (and, if the type carries one, run through its constructor — see *type constructors* above):

```mtron_pre
[-- intabs has a constructor (absolute value), but the gate still runs first --]
2.as(intabs::T)          [-- intabs::2  (admitted, re-stamped) --]
[ERROR] -2.as(intabs::T) [-- refused: -2 is not an intabs — construction (intabs::-2) is the door that coerces --]

[-- Without a constructor the cast is a pure test --]
[ERROR] -2.as(int::T[?>0])  [-- fails: a lambda type, no constructor, nothing to coerce --]
```

`.as()` is also used for nominal type casting — a rec is admitted to a nominal rec type when it nominally fits,
and the stamp records the fit:

```mtron_pre
[name=>'fuzzy feet',age=>2].as(chicken::T)    [-- chicken::[name=>'fuzzy feet',age=>2]  (nominally a being: the stamp goes on) --]
[ERROR] human::[name=>'marko',age=>29].as(chicken::T)  [-- refused: a human is not a chicken, nominally --]
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
