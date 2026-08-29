---
name: mtron-language-reference
description: Complete mtron language reference — mono, poly, and call types, operators, expression chaining, and syntax.
---

# mtron Language Reference

A data-flow language over the metatron object graph.  Every value is an **Obj** — int, str, real, bool, uri, rec, lst, inst, code, bytes, etc.  Expressions chain left-to-right: `lhs.inst(rhs)`.

---

## 1. Mono Types

```mtron
mtron> 1           [-- int (64-bit signed) --]
==>1
mtron> 1.0         [-- real (double) --]
==>1.0000
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
mtron> '''mtron''' [-- str (triple single-quoted, multi-line --]
==>fail::[parse error at line 1, col 3:
     '''mtron''' 
       ^
     could not parse at ''']@/sys/fail/394
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
---

## 2. Poly Types

### lst (`[ , ]` — ordered, indexable)

```mtron
mtron> [1, 2, 3]         [-- List of 3 ints --]
==>[1,2,3]
mtron> [1, [2, 3]]       [-- Nested list --]
==>[1,[2,3]]
mtron> [a=>1, b=>2]      [-- Record (key-value pairs) --]
==>[a=>1,b=>2]
mtron> [,]               [-- Empty list --]
==>[,]
```
### rec (`[key=>val, ...]` — unordered, keyed)

```mtron
mtron> [name=>'Alice', age=>30]
==>[name=>'Alice',age=>30]
mtron> [1=>2, 2=>3, 3=>4]
==>[1=>2,2=>3,3=>4]
```
### objs (`{ , }` — unordered, streamed and bulked)

```mtron
mtron> {1,2,3} [-- equivalent to {2,1,3} (unordered) --]
==>1
==>2
==>3
mtron> {1,1,2} [-- becomes {{2}1,2} (duplicates merged on coefficient) --]
==>{2}1
==>2
```
### Indexing / Access

```mtron
mtron> [1,2,3].0         [--
==>fail::[parse error at line 1, col 9:
     [1,2,3].0         
             ^
     could not parse at '0']@/sys/fail/396
mtron> [1,2,3]>>0        [--
==>1
mtron> [a=>1,b=>2].a     [--
==>fail::[parse error at line 1, col 13:
     [a=>1,b=>2].a     
                 ^
     could not parse at 'a']@/sys/fail/406
mtron> [a=>1,b=>2]>>a    [--
==>1
```
### Rel (relation) — key/value pair

```mtron
mtron> a=>1              [-- URI a
==>a=>1
mtron> name=>'Alice'     [-- URI name
==>name=>'Alice'
```
Objs can carry a **vid** (address URI) via `@`:
```mtron
mtron> [a=>1]@myVid            [-- list anchored at uri myVid --]
==>[a=>1]@myVid
mtron> [1,2,3,4]@a              [-- list anchored at *a --]
==>[
    1,
    2,
    3,
    4]@a
```
---

## 3. Coefficients (count / multiplicity)

Every obj has a coefficient. Shorthand is `{n}obj` -- standing for `type{n}::obj`:
```mtron
mtron> 3                      [-- coefficient {1} (default) --]
==>3
mtron> {0}3                   [-- equivalent to noobj (0 3s) --]
mtron> int{5}::3              [-- coefficient {5} represents 5 3s --]
==>{5}3
mtron> {5}3                   [-- shorthand for the previous example (5 3s) --]
==>{5}3
mtron> {1,2,3}.sum{2}()       [-- two parallel sums yields {2}6 --]
==>{2}6
mtron> {1,2,3}.sum{2}().sum() [-- {2}6 merged by sum is 12 --]
==>12
```
Coefficients propagate through arithmetic and affect count, sum, repeat:
```mtron
mtron> {1,2,3}.count()               [-- 3  (sum of coefficients) --]
==>3
mtron> {1,2,{10}3}.count()           [-- 13  (sum of coefficients) --]
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
mtron> 1 + 2                          [-- sugar stynax for previous --]
==>3
mtron> {1,2,3}.plus(2)                [-- {3,4,5} (applied to each obj in the stream) --]
==>3
==>4
==>5
mtron> {1,2,3}.mult(10)               [-- {10,20,30} --]
==>10
==>20
==>30
mtron> 1.plus(1.plus(1))              [-- 3  (nested) --]
==>3
mtron> {1,2,3,4}.prod()               [-- 24 --]
==>24
mtron> {1,2,3}.sum()                  [-- 6 --]
==>6
mtron> {1,2,3,4,5}.reduce(|plus(0))   [-- 15 (| necessary to block evaluation) --]
==>15
```
**Short-circuit:** `_` (underscore) is the identity function — returns the unmodified lhs:
```mtron
mtron> 1.plus(_)                   [-- 2 --]
==>2
mtron> {1,2,3}.map(_).plus(2)      [-- {3,4,5} --]
==>3
==>4
==>5
```
**Compound ops:** `?inst<=type(body)` for inline insts (lambdas):
```mtron
mtron> {1,2,3}.inst?int<=int(a=>plus(2)){ plus(*a) }     [-- {4,8,18} --]
==>4
==>6
==>8
```
### Boolean logic

```mtron
mtron> 3.is(gt(2))                 [-- 3  (filters: passes lhs through if true, noobj if false) --]
==>3
mtron> 3.is(true)                  [-- 3 --]
==>3
mtron> {1,2,3,4,5}.is(gt(2))       [-- {3,4,5} --]
==>3
==>4
==>5
mtron> {1,2,3,4,5}.is(and(gt(2),lt(5)))   [-- {3,4} --]
==>3
==>4
mtron> 1.and(gt(2),lt(5))          [-- false --]
==>false
```
---

## 5. String Operations

```mtron
mtron> "goodbye".plus(" nowhere")    [-- "goodbye nowhere" --]
==>'goodbye nowhere'
mtron> "goodbye" + " nowhere"        [-- sugar sytnax for previous --]
==>'goodbye nowhere'
mtron> 'a b c'.split(' ')            [-- ["a", "b", "c"] --]
==>['a','b','c']
mtron> 'a b c'-<' '                  [-- sugar syntax for previous --]
==>['a','b','c']
mtron> {"a","b","c"}>-' '            [-- "a b c"  (merge with separator) --]
==>'a b c'
mtron> 'ab3cd'.regex('\d+')          [-- ['3'] --]
==>['3']
mtron> 'ab3cd'.regex('\d{2}')        [-- [,]  (no match — empty pair) --]
==>[,]
```
---

## 6. URI Dereferencing (`*` — `from()`)

`*` dereferences a uri to its referent in space:
`@` anchors a uri to its referent in space

```mtron
mtron> */path/to/obj              [-- read obj at uri (detached) --]
==>fail::[apply failure:
   	[lhs]    │ noobj
   	 \_type  │ noobj{0}
   	  \_pred │ []
   	[inst]   │ */path/to/obj
   	 \_dom   │ #{?}::T
   	 \_args  │ [/path/to/obj][MTronException<127>:no active space supports pattern /path/to/obj]][no active space supports pattern /path/to/obj]@/sys/fail/488
mtron> @/path/to/obj              [-- real obj at uri (attached) --]
==>fail::[apply failure:
   	[lhs]    │ noobj
   	 \_type  │ noobj{0}
   	  \_pred │ []
   	[inst]   │ at?rng=B{*}&dom=A{?}(/path/to/obj){<j>}
   	 \_dom   │ A{?}::T
   	 \_args  │ [/path/to/obj][MTronException<127>:no active space supports pattern /path/to/obj]][no active space supports pattern /path/to/obj]@/sys/fail/490
mtron> *local:software/           [-- read with trailing
       *</path/to/obj>            [-- angle-bracket handles special chars --]
==>fail::[apply failure:
   	[lhs]    │ noobj
   	 \_type  │ noobj{0}
   	  \_pred │ []
   	[inst]   │ */path/to/obj
   	 \_dom   │ #{?}::T
   	 \_args  │ [/path/to/obj][MTronException<127>:no active space supports pattern /path/to/obj]][no active space supports pattern /path/to/obj]@/sys/fail/492
```
Wildcards:
```mtron
mtron> */path/+/obj               [-- + matches one segment --]
==>fail::[apply failure:
   	[lhs]    │ noobj
   	 \_type  │ noobj{0}
   	  \_pred │ []
   	[inst]   │ */path/+/obj
   	 \_dom   │ #{?}::T
   	 \_args  │ [/path/+/obj][MTronException<127>:no active space supports pattern /path/+/obj]][no active space supports pattern /path/+/obj]@/sys/fail/494
mtron> */path/+/+                 [-- ++, children at depth 2 --]
==>fail::[apply failure:
   	[lhs]    │ noobj
   	 \_type  │ noobj{0}
   	  \_pred │ []
   	[inst]   │ */path/+/+
   	 \_dom   │ #{?}::T
   	 \_args  │ [/path/+/+][MTronException<127>:no active space supports pattern /path/+/+]][no active space supports pattern /path/+/+]@/sys/fail/496
mtron> */path/#                   [-- [-- matches all remaining segments (recursive) --] --]
==>fail::[parse error at line 1, col 9:
     */path/#                    --]
             ^
     could not parse at ' ']@/sys/fail/498
```
**uri::T** type drives URI-specific operations:
```mtron
mtron> http://abc:123/a/b/c.>>scheme        [-- http --]
==>fail::[apply failure:
   	[lhs]    │ http://abc:123/a/b/c
   	 \_type  │ /m/uri
   	  \_pred │ []
   	[inst]   │ rshift?rng=#{*}&dom=uri(scheme){<j>}@<1>
   	 \_dom   │ uri::T
   	 \_args  │ [scheme][MTronException<127>:no active space supports pattern http://abc:123/a/b/c/scheme]][no active space supports pattern http://abc:123/a/b/c/scheme]@/sys/fail/508
mtron> http://abc:123/a/b/c.>>host          [-- abc --]
==>fail::[apply failure:
   	[lhs]    │ http://abc:123/a/b/c
   	 \_type  │ /m/uri
   	  \_pred │ []
   	[inst]   │ rshift?rng=#{*}&dom=uri(host){<j>}@<1>
   	 \_dom   │ uri::T
   	 \_args  │ [host][MTronException<127>:no active space supports pattern http://abc:123/a/b/c/host]][no active space supports pattern http://abc:123/a/b/c/host]@/sys/fail/518
mtron> http://abc:123/a/b/c.>>port          [-- 123 (noobj if no port) --]
==>fail::[apply failure:
   	[lhs]    │ http://abc:123/a/b/c
   	 \_type  │ /m/uri
   	  \_pred │ []
   	[inst]   │ rshift?rng=#{*}&dom=uri(port){<j>}@<1>
   	 \_dom   │ uri::T
   	 \_args  │ [port][MTronException<127>:no active space supports pattern http://abc:123/a/b/c/port]][no active space supports pattern http://abc:123/a/b/c/port]@/sys/fail/528
mtron> http://abc:123/a/b/c.>>authority     [-- abc:123 --]
==>fail::[apply failure:
   	[lhs]    │ http://abc:123/a/b/c
   	 \_type  │ /m/uri
   	  \_pred │ []
   	[inst]   │ rshift?rng=#{*}&dom=uri(authority){<j>}@<1>
   	 \_dom   │ uri::T
   	 \_args  │ [authority][MTronException<127>:no active space supports pattern http://abc:123/a/b/c/authority]][no active space supports pattern http://abc:123/a/b/c/authority]@/sys/fail/538
mtron> http://abc:123/a/b/c.>>{schema,path} [-- {http,/a/b/c} --]
mtron> /a/b/c>>0                            [-- a --]
==>/a/b/c
mtron> /a/b/c>>2                            [-- b --]
```---

## 7. Type Casting (`.as(type::T)`)

```mtron
mtron> 1.as(str::T)              [-- "1" --]
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
Custom types via `tid::T[predicate][constructor]@vid`:
```mtron
mtron> int::T[is(gt(0))]@nat     [-- type nat, only positive ints --]
mtron> int::T[?>0]@nat           [-- syntax sugar on is(gt(0)) --]
mtron> nat::2                    [-- ok --]
==>nat::2
mtron> nat::-1                   [-- <ERROR> --]
==>fail::[-1 is not a int::T[is(gt(0))]@/m/math/nat]@/sys/fail/548
```
---

## 8. Mapping & Filtering

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
mtron> {1,2,3}.where(gt(1))             [-- {2,3}  (filter: keep if predicate matches) --]
mtron> {1,2,3}.is(gt(1))                [-- {2,3}  (same, filter via is()) --]
==>2
==>3
```
### Select (structural projection)

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
### Where (filter)

```mtron
mtron> {[a=>1],[a=>2],[a=>3]}.where([a=>is(gt(1))])               [-- {[a=>2],[a=>3]} --]
==>[a=>2]
==>[a=>3]
mtron> {[a=>1],[a=>2],[a=>3]}=?=[a=>is(gt(1))]                    [-- syntax sugar for above --]
==>[a=>2]
==>[a=>3]
mtron> [1,2,3]==[_,plus(5),_]=?=[_,is(gt(5)),_]                   [-- [1,7,3] --]
==>[1,7,3]
```
---

## 9. Grouping

```mtron
mtron> {1,2,3}.group([_=>+10])                [-- [1=>11, 2=>12, 3=>13] --]
==>[1=>11,2=>12,3=>13]
mtron> [a=>1,b=>2,c=>3].group([_=>_])          [-- [[a=>1,b=>2,c=>3]=>[a=>1,b=>2,c=>3]] --]
==>[[a=>1,b=>2,c=>3]=>[a=>1,b=>2,c=>3]]
```
---

## 10. Merging & Splitting

### Merge (`>-`)

Unwraps collections: `{1,2,3}>-`   # {1,2,3} (flattens coefficient barriers)

```mtron
mtron> {1,2,3}>-                            [-- {1,2,3} --]
==>1
==>2
==>3
mtron> [1=>2,2=>3,3=>4]>-                  [-- {1=>2,2=>3,3=>4} --]
==>1=>2
==>2=>3
==>3=>4
mtron> {1,2}>-[3,4]                         [-- [1,2,3,4] --]
==>[
    1,
    2,
    3,
    4]
mtron> {1,2,3}>-1                           [-- {1,1,2,3} --]
==>{2}1
==>2
==>3
mtron> [a=>1,b=>2]>-.>-[b=>2]              [-- [a=>1,b=>2]  (merge into existing rec) --]
==>[a=>1,b=>{2}2]
```
### Split (`-<`)

Distributes elements:
```mtron
mtron> 1-<[_,_]                            [-- [1,1] --]
==>[1,1]
mtron> {1,2,3}-<[plus(1),plus(2)]          [-- {2,3,4,5} --]
==>[2,3]
==>[3,4]
==>[4,5]
```
### Conditional branch: `-<|[?pred=>a, _=>b]`

```mtron
mtron> 1-<|[?>1 => +100, _=> +2]          [-- {3,102}  (1
==>1=>3
mtron> {1,2}-<|[?>1 => +100, _=> +2]>>    [-- {3,102} --]
==>3
==>102
```
---

## 11. Right-Shift / Left-Shift (`>>`, `<<`)

Traverse into structures:

### On lst
```mtron
mtron> [1,2,[a=>3],4]<<2                  [-- [[a=>3],4] --]
mtron> [1,2,[a=>3],4]>>2                  [-- [1,2] --]
==>[a=>3]
mtron> [1,2,[a=>3],4]>>(-2)               [-- [a=>3] (negative indicies) --]
==>[a=>3]
mtron> [1,2,[a=>3],4]>>+                  [-- {1,2,[a=>3],4} (selectors are uris) --]
==>1
==>2
==>[a=>3]
==>4
```
### On records
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
```
### On URIs
```mtron
mtron> a/b/c<<                   [-- b/c    (drop leftmost segment) --]
==>a/b
mtron> a/b/c>>                   [-- a/b    (drop rightmost segment) --]
mtron> a/b/c<<1                  [-- b/c --]
==>a/b
mtron> a/b/c>>1                  [-- a/b --]
mtron> a/b/c<<3                  [-- <.>    (empty) --]
==><>
```
---

## 12. Skip / Take

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

## 13. Barrier (`|`)

Delays evaluation — wraps in a monadic barrier:

```mtron
mtron> |(plus(30)).map(20)      [-- 20   (the barrier is not evaluated by map) --]
==>20
mtron> |(plus(30)).swap(20)     [-- 50   (swap applies the barrier's lhs) --]
==>50
```
---

## 14. The `>>=` Update Instruction

Modifies a value at a URI address and writes back:

```mtron
mtron> @xyz >>= [a=>+2]                        [-- merge: add 2 to field 'a' at xyz --]
mtron> @xyz/c/d>>=10                           [-- write 10 to path --]
mtron> [1,2]@a >>= [_,+4]                      [-- [1,6]@a  (second element +4) --]
==>[1,6]
mtron> [a=>1,b=>2] >>= [b=>none]               [-- [a=>1]  (remove field b) --]
==>[a=>1]
mtron> @<people/+>.>>= [name=>"Micky Mouse"]   [-- wildcard update --]
==>[name=>'Micky Mouse',role=>developer]
==>[name=>'Micky Mouse',role=>oracle]
==>[name=>'Micky Mouse',role=>architect]
```
`@` means "anchor the write-back to the VID" (persist).  `*` means "anonymous copy" (no write-back):
```mtron
mtron> @a/b/c>>= +10      [-- modifies *a/b/c and writes back --]
mtron> *a/b/c>>= +10      [-- evaluates but discards the result --]
```
---

## 15. Auto-references (`!*`, `!@`)

Lazy cross-reference via the Router:

```mtron
mtron> [company=>!*db:companies/101]     [-- auto_from — resolved on access --]
==>[company=>!*db:companies/101]
mtron> [company=>!@db:companies/101]     [-- auto_at — resolved on access, with anchor --]
==>[company=>!@db:companies/101]
```
`!*` is sugar for `auto_from(uri)`.  When you `.at(company)` on the record, the Router resolves `db:companies/101` and returns the target Obj.

Chain through auto-refs:
```mtron
mtron> *a>>x>>x           [-- follows !* chain to the final target --]
mtron> *a>>x/x            [-- reads a field after resolving the auto-ref --]
```
---

## 16. Math Instruction

Embedded mathematical expressions:
```mtron
mtron> math('1+2')                           [-- 3.0 --]
==>3.0000
mtron> 10.to(a).math('a^2')                  [-- 100.0 --]
==>100.0000
mtron> 10.to(a).plus(10).to(b).math('a+b')   [-- 30.0 --]
==>30.0000
```
---

## 17. URI Path Operations

```mtron
mtron> a/b/c.split(/)                   [-- [a,b,c] --]
==>[a,b,c]
mtron> {a,b,c}>-/                       [-- a/b/c --]
==>a/b/c
mtron> a/b/c.split(/).merge(/)           [-- a/b/c --]
==>a/b/c
mtron> a/b/c.split(/).merge(/).mult(<.>)  [-- a/b/c  (identity) --]
==>a/b/c
```
---

## 18. Failure & Error Handling

```mtron
mtron> 1.plus('a').catch(34)                    [-- 34 --]
==>34
mtron> 1.plus('a').catch(cause().cause())       [-- noobj --]
==>fail::[]
mtron> 1.plus(mult(throw('bad'))).catch(34).plus(2)  [-- 36 --]
==>36
```
---

## 19. Objs (coefficient collections `{ }`)

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

## 20. Type Annotations

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
mtron> [a=>1,b=>2,c=>3]==[a=>_]     [-- select with pattern match --]
==>[a=>1]
mtron> [a=>1,b=>2,c=>3]==[a=>is(gt(1))]  [-- select with filter --]
```
---

## 21. Pattern Matching (`?=` prefix)

```mtron
mtron> ?=1        [-- check if equal to 1 --]
mtron> ?>1        [-- check if greater than 1 --]
mtron> ?<5        [-- check if less than 5 --]
mtron> ?int::T    [-- check if type is int --]
```
---

## 22. Sugar & Syntax Summary

| Sugar | Expansion | Use |
|-------|-----------|-----|
| `*uri` | `from(uri)` | Dereference URI |
| `!*uri` | `auto_from(uri)` | Lazy cross-ref |
| `@uri` | `at(uri)` | Anchor to URI location |
| `_` | `id()` | Identity function |
| `>>=` | `update()` | Update-and-write-back |
| `>>` | `rshift()` | Right-shift (drop last) |
| `<<` | `lshift()` | Left-shift (drop first) |
| `>-` | `merge()` | Flatten / merge barrier |
| `-<` | `split()` | Distribute into branches |
| `\|` | `barrier()` | Monadic barrier |
| `_/...\\_` | `within()` | Structural within-block |
| `.` | `.plus(1)` | Dot-instruction call |
| `=>` | `rel()` | Key → value relation |
| `->` | `ref()` | Write to URI reference |
| `;` | `end()` | Sequence separator |
| `?pred` | `is(pred)` | Type/condition check |
| `==` | `select()` | Structural select |
| `=?=` | `where()` | Filter after select |

---

## 23. Expression Evaluation Model

1. **Left-to-right chaining:** `lhs.inst(rhs)` — `inst` receives `lhs` and `rhs`
2. **Coefficient propagation:** ops distribute over collections; result coefficients combine
3. **Space reads:** `*uri` fetches from the graph; `!*uri` fetches lazily on `.at()`
4. **Write-back:** `@`-prefixed writes persist through `>>=` back to space
5. **No mutation of existing objects** — operations create new Objs (immutable)

```mtron
mtron> [-- Chaining example (read test data from test file): --]
mtron> {1,2,3,4}.sum{2}().sum?int<=int{1,7}().sum()-<[_,_]>-.sum?int<=int{2}()  #
==>fail::[parse error at line 1, col 74:
     ...,7}().sum()-<[_,_]>-.sum?int<=int{2}()  #
                                                ^
     could not parse at '#' — unclosed '<' — missing '>'?]@/sys/fail/758
```