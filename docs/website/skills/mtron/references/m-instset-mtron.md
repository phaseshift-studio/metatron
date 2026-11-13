---
name: mtron-language-reference
description: a practical mtron language reference — mono, poly, and call types, operators, expression chaining, and syntax.
---

# mtron language reference

A data-flow language over the metatron object graph. Every value is an **Obj** — int, str, real, bool, uri, rec, lst,
inst, code, bytes, etc. Expressions chain left-to-right: `lhs.inst(rhs)`.

---

## 1. mono types

```mtron
mtron> 1           [-- int (64-bit signed) --]
==>1
mtron> 1.0         [-- real (double) --]
==>1.0
mtron> true        [-- bool --]
==>true
mtron> false       [-- bool --]
==>false
mtron> "metatron"  [-- str (double-quoted) --]
==>'metatron'
mtron> 'mtron'     [-- str (single-quoted) --]
==>'mtron'
mtron> """mtron""" [-- str (triple double-quoted, multi-line) --]
==>'mtron'
mtron> <a.b.c>     [-- uri (angle-bracket necessary of uri has . or space) --]
==><a.b.c>
mtron> /foo/bar    [-- uri (path literal) --]
==>/foo/bar
mtron> a           [-- uri (bare name — no angle brackets if alphanumeric) --]
==>a
mtron> 0x0F        [-- bytes (hex literal) --]
==>0x0f
mtron> noobj       [-- "no obj" (empty, none) --]
```
A `'…'` or `"…"` str decodes exactly three escapes — `\\`, `\'` and `\"` — and keeps every other `\x` as written
(`'\s'` is backslash-s, a regex; `'C:\temp'` keeps its backslash). The writer uses them only when no delimiter is
safe: it quotes with the character the value does NOT hold, and falls back to escaped `'…'` when the value holds both
quote kinds and its own `"""` run would close the triple-quoted form early. `"""…"""` is raw, the form for code and
documents, which carry backslashes by the dozen.

### the four ways a spelling becomes an address

They are distinct, and knowing which is which is most of the uri grammar:

| spelling     | resolved by                                                         | declared by                                                                                         |
|--------------|---------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------|
| `math:cos`   | an **isa prefix** — a scheme with no host names a vocabulary        | `import(/m/math, math)`; the table is `*</instset/prefix>`                                          |
| `//ginger/a` | a **network name** — a host with no scheme and no port names a peer | `/network/prefix -> [//ginger => <ws://ginger.local:8555>]`, or `</network/prefix/ginger>` by label |
| `cos`        | a **redirect** — the short name an address already has              | derived from what is mounted; `*</memory/redirect>`                                                 |
| `/mcp`       | a **route** — a path prefix a space serves                          | the space's own `route` field, e.g. `http_server::[route=>[/mcp => mcp_mtron]]`                     |

`big()` / `small()` covers the first three: a primordial uri helper at metatron's own level, for humans. A space's
`route` is **not** part of it — a space routes the addresses it serves, on its own behalf.

And a **rewrite** is none of these: it is the compiler's code-transform rule (`instSet.rewrites()`), a different word
for
a different thing. When in doubt: a rewrite changes *code*, a redirect changes *what a name means*.

### the words, one line each

The vocabulary above is small and every term is load-bearing, so the confusions are specific. These are the ones that
have actually cost someone an afternoon.

**addresses**

| term            | means                                                                                                  |
|-----------------|--------------------------------------------------------------------------------------------------------|
| `vid`           | where a value lives (`nat::29@/usr/marko/age` → the vid is `/usr/marko/age`)                           |
| `tid`           | the type constraining it (the same value's tid is `int`)                                               |
| `origin`        | a service's full address: `scheme://host:port`. The unit peers are keyed by and dialed at              |
| `authority`     | `host:port` — an origin without the scheme. `hasAuthority()` demands **both** host and port            |
| `host`          | the host alone. The wire's gate: an address with a host is *someone else's to answer* (`Network.read`) |
| `isPrefixed()`  | scheme, no host — the vocabulary's slot (`math:cos`)                                                   |
| `isAddressed()` | just `hasHost()` — says nothing about ports or schemes                                                 |
| `isHosted()`    | host, **no scheme, no port** — the network name slot (`//ginger/a`)                                    |
| `noobj`         | the empty answer. A read that matches nothing returns it; a write of it is the universal close         |

**reach and knowledge**

| term               | means                                                                              |
|--------------------|------------------------------------------------------------------------------------|
| `roster`           | what the system **knows**: `<host> => uri{+}`, connectionless. Shared at the root  |
| `peer`             | what this machine has **dialed**: one member per surface, keyed by the dial's path |
| `listener`         | what it **answers on**: the sockets it serves, and therefore what mDNS announces   |
| `prefix` (network) | what you **call** it: `<label> => origin`, one listing at the root                 |
| reach              | dialed peers — per frame, retractable, **not** inherited                           |
| denotation         | what a spelling means — machine-global, inherited, never scoped                    |

**structure and process**

| term               | means                                                                                             |
|--------------------|---------------------------------------------------------------------------------------------------|
| `space`            | a subset of the graph one address pattern serves (`memspace::[pattern=>/usr/#]`)                  |
| `route`            | a space's own path→target table, applied when **it** serves a read or write                       |
| `mount` / `import` | making a space or an instruction set available (`InstSet.mount`, `@vid` writes)                   |
| `frame`            | a child machine: `push`/`pop` are the call stack, `move` chooses a machine without closing one    |
| `redirect`         | a short name's home, `big()`/`small()`. Exposed at `*</memory/redirect>`, rewritable deliberately |
| `prefix` (isa)     | a vocabulary's short scheme, bound by the import that mounted it                                  |
| `rewrite`          | a **compiler** rule over code. Not a name, not an address — it transforms programs                |

The rule of thumb: an address is *declared* (a prefix, a name, a route's key), a redirect is *derived* from what was
mounted, and a rewrite is *code*.

---

## 2. poly types

### lst::T (`[,]` — ordered, indexable)

```mtron
mtron> [1, 2, 3]             [-- lst of 3 int::T --]
==>[1,2,3]
mtron> [1, [2, 3]]           [-- nested lst --]
==>[1,[2,3]]
mtron> [,]                   [-- empty lst --]
==>[,]
mtron> [1,2,3]>>0
==>1
mtron> [1,2,3]==[?>1 => +2]  [-- select with rec::T for predicate keys --]
==>[noobj,noobj,5]
```
### rec::T (`[key=>val, ...]` — unordered, keyed)

```mtron
mtron> [name=>'marko', age=>29]
==>[name=>'marko',age=>29]
mtron> [1=>2, 2=>3, 3=>4]
==>[1=>2,2=>3,3=>4]
mtron> [a=>1,b=>2]>>a
==>1
mtron> [a=>1,b=>[c=>2,d=>[e=>4]]]>>a/b
mtron> [a=>1,b=>[c=>2,d=>[e=>4]]]>>a/b/d
mtron> [a=>1,b=>[c=>2,d=>[e=>4]]]>>a/b/d/e
```
### rel::T (relation) — key/value pair

```mtron
mtron> name=>'marko'
==>name=>'marko'
mtron> name=>'marko'=>age=>29
==>name=>('marko'=>(age=>29))
mtron> name=>'marko'=>age=>29.>>.>>.>>
==>29
```
### objs (`{,}` — unordered, streamed and bulked)

```mtron
mtron> {1,2,3}                                  [-- equivalent to {2,1,3} (unordered) --]
==>1
==>2
==>3
mtron> {1,2,3}.isa({1,2,3})                     [-- wrong assumption: checks 1, then 2, then 3 against isa arg --]
mtron> {1,2,3}.isa?int{*}<=int{*}({1,2,3})      [-- isa now a barrier and checks stream against stream --]
==>1
==>2
==>3
mtron> {1,2,3}.isa?int{*}<=int{*}({3,1,2})      [-- streams are unordered --]
==>1
==>2
==>3
mtron> {3,1,2,3}.isa?int{*}<=int{*}({1,{2}3,2}) [-- coefficients accounted for --]
==>{2}3
==>1
==>2
```
---

## 3. coefficients (count/multiplicity)

Every obj has a coefficient. Shorthand is `{n}obj` -- standing for `type{n}::obj`:

```mtron
mtron> 3                                        [-- coefficient {1} (default) --]
==>3
mtron> {0}3                                     [-- equivalent to noobj (0 3s) --]
mtron> int{5}::3                                [-- coefficient {5} represents 5 3s --]
==>{5}3
mtron> {5}3                                     [-- shorthand for the previous example (5 3s) --]
==>{5}3
mtron> {1,2,3}.sum{2}()                         [-- two parallel sums yields {2}6 --]
==>{2}6
mtron> {1,2,3}.sum{2}().sum()                   [-- {2}6 merged by sum is 12 --]
==>12
```
Coefficients propagate through arithmetic and affect count, sum, repeat:

```mtron
mtron> {1,2,3}.count()               [-- 3  (sum of coefficients) --]
==>3
mtron> {1,2,{10}3}.count()           [-- 12  (sum of coefficients) --]
==>12
mtron> int{50}::10.mult(10)          [-- int{50}::100 (coefficient account for) --]
==>{50}100
mtron> {int{2}::1,int{3}::2}.sum()   [-- int::8 (coefficients account for) --]
==>8
```
---

## 4. Arithmetic & Logic

```mtron
mtron> 1.plus(2)                      [-- 3 --]
==>3
mtron> 1.plus(_)                      [-- 2  (underscore is the identity function = lhs) --]
==>2
mtron> 1 + 2                          [-- sugar sytnax for previous --]
==>3
mtron> {1,2,3}.plus(2)                [-- {3,4,5} (applied to each obj in the stream) --]
==>3
==>4
==>5
mtron> {1,2,3}.mult(10)               [-- {10,20,30} --]
==>10
==>20
==>30
mtron> {1,2,3} * 10                   [-- sugar syntax for previous --]
==>10
==>20
==>30
mtron> 1.plus(1.plus(1))              [-- 3 (nested) --]
==>3
mtron> {1,2,3,4}.prod()               [-- 24 --]
==>24
mtron> {1,2,3}.sum()                  [-- 6 --]
==>6
mtron> {1,2,3,4,5}.reduce(|plus(0))   [-- 15 (| necessary to block evaluation) --]
==>15
```
**lhs arguments** `_` (underscore) is the identity function — returns the unmodified lhs:

```mtron
mtron> 1.plus(_)                   [-- 2 --]
==>2
mtron> {1,2,3}.map(_).plus(2)      [-- {3,4,5} --]
==>3
==>4
==>5
```
**compound ops:** `inst?rng<=dom(args){body}` for inline insts (lambdas):

```mtron
mtron> {1,2,3}.inst?int<=int(a=>plus(2)){ plus(*a) }      [-- {4,8,18} --]
==>4
==>6
==>8
mtron> {1,2,3}.(a=>+2){ +*a }                             [-- sugar syntax for the above --]
==>4
==>6
==>8
mtron> {1,2,3}.inst?int<=int(plus(2)@a){ plus(*a) }       [-- an argument named by its own vid is read by that name --]
==>4
==>6
==>8
```
A call that names every argument with `@name` keys the body's argument frame by those names, so `(plus(2)@a)` reads
as `(a=>plus(2))` does. The instruction's own arguments stay positional.

### Boolean logic

```mtron
mtron> 3.is(gt(2))                        [-- 3  (filters: passes lhs through if true, noobj if false) --]
==>3
mtron> 3.is(eq(3))                        [-- 3  (filter based on equality) --]
==>3
mtron> 3.isa(3)                           [-- 3  (filter based on value-value testing) --]
==>3
mtron> 3.isa(int::T)                      [-- 3  (filter based on value-type testing) --]
==>3
mtron> 3.is(true)                         [-- 3 --]
==>3
mtron> {1,2,3,4,5}.is(gt(2))              [-- {3,4,5} --]
==>3
==>4
==>5
mtron> {1,2,3,4,5}.is(and(gt(2),lt(5)))   [-- {3,4} --]
==>3
==>4
mtron> {1,2,3,4,5}.?>2.?<5                [-- {3,4} (using sugar'd syntax)--]
==>3
==>4
mtron> 1.and(gt(2),lt(5))                 [-- false --]
==>false
```
---

## 5. String Operations

```mtron
mtron> "goodbye".plus(" nowhere")                    [-- "goodbye nowhere" --]
==>'goodbye nowhere'
mtron> "goodbye" + " nowhere"                        [-- sugar sytnax for previous --]
==>'goodbye nowhere'
mtron> 'a b c'.split(' ')                            [-- ["a", "b", "c"] --]
==>['a','b','c']
mtron> 'a b c'-<' '                                  [-- sugar syntax for previous --]
==>['a','b','c']
mtron> {'a','b','c'}>-'-'                            [-- 'a-b-c'  (merge with separator) --]
==>'a-b-c'
mtron> 'ab3cd'.select('\d+')                         [-- ['3'] --]
==>['3']
mtron> 'ab3cd'.select('\d{2}')                       [-- [,]  (no match — empty pair) --]
==>[,]
mtron> 'abbc'>>=['a'=>'A','b{2}'=>'X']               [-- 'AXc' (update sugar syntax) --]
==>'AXc'
mtron> 'abbc'>>=['a'=>'A','b{2}'=>'X']=='[A-Z]{2,3}' [-- ['AX'] (update and select sugar syntax) --]
==>['AX']
```
---

## 6. uri dereferencing (`*` — `from()`)

`*` dereferences a uri to its referent in space:
`@` anchors a uri to its referent in space

```mtron
*/path/to/obj              [-- read obj at uri (detached) --]
@/path/to/obj              [-- real obj at uri (attached) --]
*local:software/           [-- the relation local:software=>obj --]
*</pa.th/to/o b .j>        [-- angle-bracket handles special chars --]
```

uri wildcards:

```mtron
*/path/+/obj               [-- + matches one segment --]
*/path/+/+                 [-- ++, children at depth 2 --]
*/path/#                   [-- matches all remaining segments (recursive) --]
```

**uri::T** type drives URI-specific operations:

```mtron
mtron> http://abc:123/a/b/c.as(rec::T)>>scheme        [-- http --]
==>http
mtron> http://abc:123/a/b/c.as(rec::T)>>host          [-- abc --]
==>abc
mtron> http://abc:123/a/b/c.as(rec::T)>>port          [-- 123 (noobj if no port) --]
==>123
mtron> http://abc:123/a/b/c.as(rec::T)>>authority     [-- abc:123 --]
==>abc:123
mtron> http://abc:123/a/b/c.as(rec::T)>>{schema,path} [-- {http,/a/b/c} --]
==>[<>,a,b,c]
```
objs anchored to their address are specified with `@`.

```mtron
mtron> [a=>1]@a                 [-- rec anchored at uri myVid --]
==>[a=>1]@a
mtron> [1,2,3,4]@b              [-- lst anchored at *a --]
==>[1,2,3,4]@b
mtron> *a + [b=>2]
==>[a=>1,b=>2]
mtron> *a
==>[a=>1]
mtron> @b + [5,6]
==>[1,2,3,4,5,6]@b
mtron> *b
==>[1,2,3,4,5,6]
```
---

## 7. type casting (`as(type::T)`)

```mtron
mtron> 1.as(str::T)              [-- '1' --]
==>'1'
mtron> 1.0.as(int::T)            [-- 1 --]
==>1
mtron> "abc".as(bytes::T)        [-- 0x616263 --]
==>0x616263
mtron> "/a/b/c".as(uri::T)       [-- /a/b/c --]
==>/a/b/c
mtron> true.as(int::T)           [-- 1 --]
==>1
mtron> [a=>1,b=>2].as(lst::T)    [-- [(0=>(a=>1)),(1=>(b=>2))] --]
==>[0=>a=>1,1=>b=>2]
mtron> [a,b].as(rec::T)          [-- [0=>a,1=>b] --]
==>[0=>a,1=>b]
```
Custom types, created with `tid::T[predicate][constructor]@vid` (see the *type system* doc):

```mtron
mtron> int::T[is(gt(0))]@posint     [-- a new type: positive integers --]
mtron> posint::2                    [-- posint::2  (admitted: the predicate lets it through) --]
==>posint::2
mtron> posint::-1           [-- refused: a predicate-only type has no constructor to rescue the value --]
==>fail::[parse error at line 1, col 1: -1 is not a int::T[/m/inst/is(/m/inst/gt(0))]@posint
   	while parsing: posint::-1]
```
---

## 8. mapping & filtering

```mtron
mtron> {1,2,3,4}.map(+2)                [-- {3,4,5,6} --]
==>3
==>4
==>5
==>6
mtron> {1,2,3,4}.map(_).plus(2)         [-- same --]
==>3
==>4
==>5
==>6
mtron> {1,2,3,4}.map(map(+2))           [-- nested --]
==>3
==>4
==>5
==>6
mtron> {1,2,3}.is(gt(1))                [-- {2,3}  (filter: keep the members the predicate admits) --]
==>2
==>3
```
### select (structural projection)

```mtron
mtron> [a=>1,b=>2,c=>3].select([_=>_])                            [-- [a=>1,b=>2,c=>3] --]
==>[a=>1,b=>2,c=>3]
mtron> [a=>1,b=>2,c=>3]==[_=>_]                                   [-- syntax sugar for above --]
==>[a=>1,b=>2,c=>3]
mtron> [a=>1,b=>2,c=>3]==[a=>_]                                   [-- [a=>1] --]
==>[a=>1]
mtron> [a=>1,b=>2,c=>3]==[a=>+10]                                 [-- [a=>11] --]
==>[a=>11]
mtron> {[a=>1],[a=>2],[a=>3]}==[a=>?>=2.+10]                      [-- {[a=>12],[a=>13]} --]
==>[a=>12]
==>[a=>13]
mtron> [1,2,3]==[_,plus(5),_]                                     [-- [1,7,3] --]
==>[1,7,3]
```
### filtering (`?` and `is()`)

`lhs ? predicate` is the filter: it keeps the lhs when the predicate admits it and yields `noobj` when it does
not. A rec predicate matches structurally — the value's field is checked against the field's type:

```mtron
mtron> {[a=>1],[a=>2],[a=>3]}?[a=>is(gt(1))]   [-- {[a=>2],[a=>3]}  (the rec predicate matches each rec's a-field) --]
==>[a=>2]
==>[a=>3]
mtron> {[a=>1],[a=>2],[a=>3]}?[a=>is(lt(3))]   [-- {[a=>1],[a=>2]}  (the same shape admits the low fields) --]
==>[a=>1]
==>[a=>2]
```
**`isa()` is for uris, not values.** Its live row is `where?uri<=uri(rec)`: the arg is a profile over the uri's
fields, the uri is projected against it, and the uri passes through only when the projection agrees with the
original — a profile matcher, and `noobj` when the profile is contradicted:

```mtron
mtron> <//2024.12:25/09/00/00/000?tz=-0500>?isa([host=>2024.12, port=>25])  [-- the uri (the profile matches) --]
mtron> <//2024.12:25/09/00/00/000?tz=-0500>?isa([host=>2024.12, port=>26])  [-- noobj  (the port contradicts the profile) --]
```
---

## 9. grouping

```mtron
mtron> {1,2,3}.group([_=>+10])                 [-- [1=>11, 2=>12, 3=>13] --]
==>[1=>11,2=>12,3=>13]
mtron> [a=>1,b=>2,c=>3].group([_=>_])          [-- [[a=>1,b=>2,c=>3]=>[a=>1,b=>2,c=>3]] --]
==>[[a=>1,b=>2,c=>3]=>[a=>1,b=>2,c=>3]]
```
---

## 10. Merging & Splitting

### merge (`>-`)

Unwraps collections: `{1,2,3}>-`   # {1,2,3} (flattens coefficient barriers)

```mtron
mtron> {1,2,3}>-                            [-- {1,2,3}  (the bare members, unbarriered) --]
==>1
==>2
==>3
mtron> [1=>2,2=>3,3=>4]>-                  [-- the rels, unwrapped --]
==>1=>2
==>2=>3
==>3=>4
mtron> {1,2}>-[3,4]                         [-- [1,2,3,4]  (the barrier joins into the existing lst) --]
==>[1,2,3,4]
mtron> {1,2,3}>-1                           [-- {1,1,2,3}  (1 is added to the stream) --]
==>{2}1
==>2
==>3
mtron> [a=>1,b=>2]>-.>-[b=>2]              [-- [a=>1, b=>{2}2]  (the incoming 2 stacks under the existing 2 as a coefficient) --]
==>[a=>1,b=>{2}2]
```
### split (`-<`)

Distributes elements:

```mtron
mtron> 1-<[_,_]                            [-- [1,1]  (duplicated along the branches) --]
==>[1,1]
mtron> {1,2,3}-<[plus(1),plus(2)]          [-- {[2,3],[3,4],[4,5]}  (each member against each branch) --]
==>[2,3]
==>[3,4]
==>[4,5]
```
### conditional branch: `-<|[?pred=>a, _=>b]`

```mtron
mtron> 1-<|[?>1 => +100, _=> +2]          [-- 1=>3  (1 fails ?>1, so the default branch +2 takes it) --]
==>1=>3
mtron> {1,2}-<|[?>1 => +100, _=> +2]>>    [-- 1=>3, 2=>102  (each member takes its own branch) --]
==>3
==>102
```
---

## 11. right-shift / left-shift (`>>`, `<<`)

`>>` descends into an obj and `<<` ascends out of it. On a poly, `>>` selects children: by index on a `lst`, by key on
a `rec`, the second of a `rel`. An obj reached by `>>` keeps a link to the poly it came from, and `<<` follows that link
back. A `rec`'s keys and a `rel`'s first are its `dom()`, not its parent, so a literal has no parent.

### lsts

```mtron
mtron> [1,2,[a=>3],4]>>2                  [-- [a=>3]            (by index) --]
==>[a=>3]
mtron> [1,2,[a=>3],4]>>(-2)               [-- [a=>3]            (negative indices) --]
==>[a=>3]
mtron> [1,2,[a=>3],4]>>+                  [-- {1,2,[a=>3],4}    (selectors are uris) --]
==>1
==>2
==>[a=>3]
==>4
mtron> [1,2,[a=>3],4]>>2.<<               [-- [1,2,[a=>3],4]    (back to the lst) --]
==>[1,2,[a=>3],4]
```
### recs

```mtron
mtron> [a=>1,b=>2,c=>[d=>3]].dom()        [-- {a,b,c}           (extract keys) --]
==>a
==>b
==>c
mtron> [a=>1,b=>2,c=>[d=>3]].rng()        [-- {1,2,[d=>3]}      (extract values) --]
==>1
==>2
==>[d=>3]
mtron> [a=>1,b=>2,c=>[d=>[e=>3]]]>>c      [-- [d=>[e=>3]]       (access by key) --]
==>[d=>[e=>3]]
mtron> [a=>1,b=>2,c=>[d=>[e=>3]]]>>c/d/e  [-- 3                 (walk nested structure) --]
==>3
mtron> [a=>1,b=>2,c=>[d=>[e=>3]]]>>c>>d.<<.<<  [-- [a=>1,b=>2,c=>[d=>[e=>3]]]  (ascend twice) --]
==>[a=>1,b=>2,c=>[d=>[e=>3]]]
```
### rels

```mtron
mtron> (1=>(2=>3))>>                      [-- (2=>3)            (the second) --]
==>2=>3
mtron> (1=>(2=>3)).dom()                  [-- 1                 (the first is the dom, not the parent) --]
==>1
mtron> (1=>(2=>3))>>1.<<                  [-- (1=>(2=>3))       (back to the rel) --]
==>1=>(2=>3)
```
### uris

On a uri both shifts are address arithmetic, and nothing resolves: `>>` enumerates the child uris in the address space,
and `<<` drops trailing segments (a uri carries its parent in its segments).

```mtron
mtron> a/b/c <<                  [-- a/b    (drop the rightmost segment) --]
==>a/b
mtron> a/b/c << 2                [-- a --]
==>a
mtron> a/b/c << 3                [-- <>     (empty) --]
==><>
mtron> a/b/c/d << c/d            [-- a/b    (retract a matching postfix) --]
==>a/b
```
---

## 12. skip / take

```mtron
mtron> {1,2,3,4}.take(2)        [-- {1,2} --]
==>1
==>2
mtron> {1,2,3,4}.skip(2)        [-- {3,4} --]
==>3
==>4
mtron> {1,2,3,4,5}.skip(2).take(2)   [-- {3,4} --]
==>3
==>4
```
---

## 13. block (`|`)

Delays evaluation — wraps in a monadic barrier:

```mtron
mtron> |(plus(30)).map(20)      [-- 20   (the barrier is not evaluated by map) --]
==>20
mtron> |(plus(30)).swap(20)     [-- 50   (swap applies the barrier's lhs) --]
==>50
```
---

## 14. update (`>>=`)

Modifies a value at a uri address (or on stack if not addressed):

```mtron
mtron> @xyz >>= [a=>+2]                        [-- merge: add 2 to field 'a' at xyz --]
mtron> @xyz/c/d>>=10                           [-- write 10 to path --]
mtron> [1,2]@a >>= [_,+4]                      [-- [1,6]@a  (second element +4) --]
==>[1,6]
mtron> [a=>1,b=>2] >>= [b=>none]               [-- [a=>1]  (remove field b) --]
==>[a=>1]
mtron> @<people/+>.>>= [name=>"Micky Mouse"]   [-- wildcard update --]
```
`@` means "anchor the write-back to the VID" (persist).  `*` means "anonymous copy" (no write-back):

```mtron
mtron> @a/b/c>>= +10      [-- modifies *a/b/c and writes back   --]
mtron> *a/b/c>>= +10      [-- evaluates but discards the result --]
```
---

## 15. auto-references (`!*`, `!@`)

Lazy cross-reference via the Router:

```mtron
mtron> [company=>!*db:companies/101]     [-- auto_from — resolved on access            --]
==>[company=>!*db:companies/101]
mtron> [company=>!@db:companies/101]     [-- auto_at — resolved on access, with anchor --]
==>[company=>!@db:companies/101]
```
`!*` is sugar for `auto_from(uri)`. When you `.at(company)` on the record, the Router resolves `db:companies/101` and
returns the target Obj.

Chain through auto-refs:

```mtron
mtron> *a>>x>>x           [-- follows !* chain to the final target       --]
mtron> *a>>x/x            [-- reads a field after resolving the auto-ref --]
```
---

## 16. math instruction

Embedded mathematical expressions:

```mtron
mtron> math('1+2')                           [-- 3.0   --]
==>3.0
mtron> 10.to(a).math('a^2')                  [-- 100.0 --]
==>100.0
mtron> 10.to(a).plus(10).to(b).math('a+b')   [-- 30.0  --]
==>30.0
```
---

## 17. uri path operations

```mtron
mtron> a/b/c.split(/)                     [-- [a,b,c] --]
==>[a,b,c]
mtron> {a,b,c}>-/                         [-- a/b/c --]
==>a/b/c
mtron> a/b/c.split(/).merge(/)            [-- a/b/c --]
==>a/b/c
mtron> a/b/c.split(/).merge(/).mult(<.>)  [-- a/b/c  (identity) --]
==>a/b/c
```
---

## 18. failure & error handling

```mtron
mtron> 1.plus('a').catch(34)                         [-- 34 --]
==>34
mtron> 1.plus('a').catch(cause().cause())            [-- noobj --]
mtron> 1.plus(mult(throw('bad'))).catch(34).plus(2)  [-- 36 --]
==>36
```
---

## 19. objs (coefficient collections `{ }`)

Braces create a multi-value Objs collection (coefficient barrier):

```mtron
mtron> {1,2,3}                        [-- three ints --]
==>1
==>2
==>3
mtron> >-.sum()                       [-- sum flattens the coefficient barrier --]
==>0
```
Collections distribute operations:

```mtron
mtron> {1,2,3}.plus(2)                [-- {3,4,5} --]
==>3
==>4
==>5
mtron> {1,2,3}>-                      [-- unmerge — flattens barriers --]
==>1
==>2
==>3
```
---

## 20. type annotations

Inline type predicates:

```mtron
mtron> ?int::T                        [-- test: is this an int? --]
mtron> ?uri::T                        [-- is this a uri? --]
mtron> isa(uri::T)                    [-- same, sugar --]
mtron> ?int{5}::3                     [-- coefficient-aware check --]
mtron> int{?}::10                     [-- optional coefficient {0,1} --]
==>{?}10
```
`==`

```mtron
mtron> [a=>1,b=>2,c=>3]==[a=>_]          [-- select with pattern match --]
==>[a=>1]
mtron> [a=>1,b=>2,c=>3]==[a=>is(gt(1))]  [-- select with filter --]
```
---

## 21. pattern matching (`?=` prefix)

```mtron
mtron> ?=1        [-- check if equal to 1 --]
mtron> ?>1        [-- check if greater than 1 --]
mtron> ?<5        [-- check if less than 5 --]
mtron> ?int::T    [-- check if type is int --]
```
---

## 22. code pattern matching

Code is data. A `poly` holds code without running it (`[plus(1).mult(2)]`), and `==[...]` applies an instruction to that
code as a value. `update()` (sugar'd `>>=`) to code changes the runs of instructions a **pattern** matches, as
`update()` on a str changes the sections a regex matches.

A code pattern is code whose parts are tests. The code tests the pattern, as a value tests a type:

* an instruction's **name** is a uri pattern: `</m/inst/{plus,mult}>` matches either (**branching uris**), `#` matches
  any.
* an **argument** is the type it must be: `int::T` is one int, `int{*}::T` a bag of them, `int::T[?>0]` a positive int.
* a predicate guards an argument (`plus(?>0)`), a literal matches itself (`plus(2)`), and an instruction pattern
  nests (`mult(#(mult(_)))` matches a `mult` whose argument is any instruction holding a `mult`).

```mtron
mtron> [plus(1).mult(2).plus(3)]==[>>=[plus(int::T)=>mult(5)]]                              [-- every match changes --]
==>[mult(5).mult(2).mult(5)]
mtron> [plus(1).plus(2).mult(3)]==[>>=[plus(int::T).plus(int::T)=>plus(3)]]                 [-- a run matches as a whole --]
==>[plus(3).mult(3)]
mtron> [plus(1).mult(2).plus(3)]==[>>=[</m/inst/{plus,mult}>(int::T)=>id()]]                [-- a uri pattern names the inst --]
==>[id().id().id()]
mtron> [start({1,2}).plus(1)]==[>>=[start(int::T)=>start(0)]]                               [-- int{2}::T is not int::T unchanged --]
==>[start({1,2}).plus(1)]
mtron> [start({1,2}).plus(1)]==[>>=[start(int{*}::T)=>start(0)]]                            [-- int{*} takes the bag --]
==>[start(0).plus(1)]
mtron> [mult(1).plus(2).mult(3)]==[>>=[plus(int::T).mult(int::T)=>[mult(int::T)=>mult(7)]]] [-- a rec changes inside the run --]
==>[mult(1).plus(2).mult(7)]
```
A pattern matches runs of consecutive instructions, left to right, never overlapping. A code change replaces the run,
and a rec applies its own rules inside the run. The rules run in order, each over what the last one left, and a change
is not matched again.

### in place: an instruction's args

A rule keyed by `args` (a uri, not a pattern) updates the arguments of each instruction in place, as `>>=` updates a
lst: a call transforms, a literal or a blocked `|x` replaces, `_` keeps a value, `#::T` keeps an argument that is itself
an instruction, and the rule's lst gives the new arity. An instruction updates itself the same way
(`update?inst<=inst(rec::T)`).

```mtron
mtron> [plus(5)]==[>>=[args=>[+10]]]                                    [-- an inst updates its own args --]
==>[plus(15)]
mtron> [branch(plus(1),mult(2))]==[>>=[args=>[#::T,|mult(3)]]]          [-- keep one, replace the other --]
==>[branch(plus(1),mult(3))]
mtron> [plus(5).mult(2)]==[>>=[plus(int::T)=>[args=>[+10]]]]            [-- in place, in the run a pattern matched --]
==>[plus(15).mult(2)]
```
### names and templates

`@name` on a pattern element names what it matched, at any depth, the instructions of a code argument included. A run
binds the pattern as any instruction binds its declaration, so a name met twice must meet the same value, and a
generic is pinned for the whole run. A name in all capitals is a generic, which is a type, so it names no value:
`int::T@A` binds `A` to `int`, and `int::T@a` captures the int. In a change, a `${...}` argument is a **template parameter**: it is evaluated against what the run
holds in that place, it reads the names with `*name`, and its value (not its text) is the argument. Nest one template
inside another by alternating `${...}` and `{{{...}}}`. A named hole in a change is what its name matched
(`minus(#::T@b)` reads `${*b}`), so a rule and its reverse are one equation. Everything else in a change is literal:
`plus(+5)` stays `plus(plus(5))`.

A pattern's argument is matched as a value tests a type: a hole (`int::T`) takes what fits it, and a code argument is
typed by its range, so a value of that range meets it. A rule fires only when every name its pattern declares binds,
and a name inside a code argument binds only against code: `mult(2.0)` fits `mult(map(#::T@b).inv())` but binds no
`b`, so the rule passes it by. An argument the call leaves out is noobj, which only a hole admitting zero takes:
`order(#{?}::T@s)` takes a bare `order()`.

```mtron
mtron> [plus(5).mult(2)]==[>>=[plus(int::T)=>plus(${+10})]]                                             [-- evaluated against the 5 --]
==>[plus(15).mult(2)]
mtron> [plus(5).mult(2)]==[>>=[plus(int::T@a)=>plus(${*a.plus(10)})]]                                   [-- a named group --]
==>[plus(15).mult(2)]
mtron> [plus(2).plus(3).mult(4)]==[>>=[plus(int::T@a).plus(int::T@b)=>plus(${*a.plus(*b)})]]            [-- the plus monoid law, as data --]
==>[plus(5).mult(4)]
mtron> [mult(plus(mult(3))).plus(1)]==[>>=[mult(#(mult(int::T@k)))=>mult(plus(mult(${*k.mult(2)})))]]   [-- a name bound at depth --]
==>[mult(plus(mult(6))).plus(1)]
mtron> [plus(2).plus(3).mult(4)]==[>>=[plus(#::T@g).plus(#::T@h)=>plus(${*g.mult(10).plus(*h)})]]       [-- a g and an h that are anything --]
==>[plus(23).mult(4)]
mtron> [plus(map(3).neg()).mult(2)]==[>>=[plus(map(#::T@u).neg())=>minus(#::T@u)]]                    [-- a named hole reads its binding --]
==>[minus(3).mult(2)]
mtron> [minus(3).mult(2)]==[>>=[minus(#::T@u)=>plus(map(#::T@u).neg())]]                              [-- the rule reversed: the same equation --]
==>[plus(map(3).neg()).mult(2)]
mtron> [plus(2).mult(3).plus(2).mult(2)]==[>>=[plus(int::T@n).mult(int::T@n)=>mult(${*n.mult(*n)})]]   [-- n met twice: only plus(2).mult(2) --]
==>[plus(2).mult(3).mult(4)]
mtron> [mult(2.0).mult(map(3).inv())]==[>>=[mult(map(#::T@v).inv())=>div(#::T@v)]]                   [-- the 2.0 fits but binds no v: only the code folds --]
==>[mult(2.0).div(3)]
```
The names are the change's own argument uris: a frame on the thread's argument stack, pushed for the change and popped
after it, so threads never see each other's names. A name on a **typed hole** is also a type of the machine.
`int::T@a` defines `a::T` in the machine's root frame (the scope of relative uris), and a later plain `int::T@a` refers
to whatever `a` already is. A hole over `#` is the exception: `#::T@b` is a b that is anything, which constrains
nothing, so it defines no type. So:

* within one machine, a name means one type: reuse `@a` only for the same type, and give a different refinement its
  own name.
* threads that pattern-match concurrently each work in a machine of their own (`push`, then `pop`). Their types live
  and die there; in one shared machine their definitions collide.
* a name the argument stack itself answers (such as `q`) cannot be read back as a capture.

---

## 23. sugar & syntax summary

| Sugar     | Expansion        | Use                      |
|-----------|------------------|--------------------------|
| `*uri`    | `from(uri)`      | dereference uri          |
| `!*uri`   | `auto_from(uri)` | lazy cross-ref           |
| `@uri`    | `at(uri)`        | anchor to uri location   |
| `_`       | `id()`           | identity function        |
| `>>=`     | `update()`       | update-and-write-back    |
| `>>`      | `rshift()`       | descend to the children  |
| `<<`      | `lshift()`       | ascend to the parent     |
| `>-`      | `merge()`        | flatten / merge barrier  |
| `-<`      | `split()`        | distribute into branches |
| `\|`      | `block()`        | block/prevent evaluation |
| `_/...\_` | `within()`       | structural within-block  |
| `.`       | `.plus(1)`       | dot-instruction call     |
| `=>`      | `rel()`          | key → value relation     |
| `->`      | `ref()`          | write to URI reference   |
| `;`       | `end()`          | sequence separator       |
| `?type`   | `isa(type)`      | type/condition check     |
| `==`      | `select()`       | structural select        |

---

## 24. expression evaluation model

1. **left-to-right chaining:** `lhs.inst(rhs)` — `inst` receives `lhs` and `rhs`
2. **coefficient propagation:** ops distribute over collections; result coefficients combine
3. **space reads:** `*uri` fetches from the graph; `!*uri` fetches lazily on `.at()`
4. **write-back:** `@`-prefixed writes persist through `>>=` back to space
5. **no mutation of existing objects** — operations create new Objs (immutable)

```mtron
chains: every inst's output is the next inst's input
mtron> {1,2,3,4}.sum()                     [-- 10 --]
==>10
mtron> {1,2,3,4}.sum{2}()                  [-- {2}10  (the same job, run twice in parallel) --]
==>{2}10
mtron> {1,2,3,4}.sum{2}().sum()            [-- 20  (the outer sum folds the coefficient in) --]
==>20
mtron> {1,2,3,4}-<[plus(1), plus(10)]>-    [-- 2, 11, 3, 12, 4, 13, 5, 14  (distribute to each function, then merge) --]
==>2
==>11
==>3
==>12
==>4
==>13
==>5
==>14
mtron> {1,2,3,4}.skip(1).take(2)           [-- {2,3}  (window: drop the first, keep two) --]
==>2
==>3
```