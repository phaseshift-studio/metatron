---
name: mtron-language-reference
description: a practical mtron language reference — mono, poly, and call types, operators, expression chaining, and syntax.
---

# mtron language reference

A data-flow language over the metatron object graph. Every value is an **Obj** — int, str, real, bool, uri, rec, lst,
inst, code, bytes, etc. Expressions chain left-to-right: `lhs.inst(rhs)`.

---

## 1. mono types

```mtron_pre
1           [-- int (64-bit signed) --]
1.0         [-- real (double) --]
true        [-- bool --]
false       [-- bool --]
"metatron"  [-- str (double-quoted) --]
'mtron'     [-- str (single-quoted) --]
"""mtron""" [-- str (triple double-quoted, multi-line) --]
<a.b.c>     [-- uri (angle-bracket necessary of uri has . or space) --]
/foo/bar    [-- uri (path literal) --]
a           [-- uri (bare name — no angle brackets if alphanumeric) --]
0x0F        [-- bytes (hex literal) --]
noobj       [-- "no obj" (empty, none) --]
```

### the four ways a spelling becomes an address

They are distinct, and knowing which is which is most of the uri grammar:

| spelling | resolved by | declared by |
|---|---|---|
| `math:cos` | an **isa prefix** — a scheme with no host names a vocabulary | `import(/m/math, math)`; the table is `*</instset/prefix>` |
| `//ginger/a` | a **network name** — a host with no scheme and no port names a peer | `</network/prefix/ginger> -> <ws://ginger.local:8555>` |
| `cos` | a **redirect** — the short name an address already has | derived from what is mounted; `*</memory/redirect>` |
| `/mcp` | a **route** — a path prefix a space serves | the space's own `route` field, e.g. `http_server::[route=>[/mcp => mcp_mtron]]` |

`big()` / `small()` covers the first three: a primordial uri helper at metatron's own level, for humans. A space's
`route` is **not** part of it — a space routes the addresses it serves, on its own behalf.

And a **rewrite** is none of these: it is the compiler's code-transform rule (`instSet.rewrites()`), a different word for
a different thing. When in doubt: a rewrite changes *code*, a redirect changes *what a name means*.

### the words, one line each

The vocabulary above is small and every term is load-bearing, so the confusions are specific. These are the ones that
have actually cost someone an afternoon.

**addresses**

| term | means |
|---|---|
| `vid` | where a value lives (`nat::29@/usr/marko/age` → the vid is `/usr/marko/age`) |
| `tid` | the type constraining it (the same value's tid is `int`) |
| `origin` | a service's full address: `scheme://host:port`. The unit peers are keyed by and dialed at |
| `authority` | `host:port` — an origin without the scheme. `hasAuthority()` demands **both** host and port |
| `host` | the host alone. The wire's gate: an address with a host is *someone else's to answer* (`Network.read`) |
| `isPrefixed()` | scheme, no host — the vocabulary's slot (`math:cos`) |
| `isAddressed()` | just `hasHost()` — says nothing about ports or schemes |
| `isHosted()` | host, **no scheme, no port** — the network name slot (`//ginger/a`) |
| `noobj` | the empty answer. A read that matches nothing returns it; a write of it is the universal close |

**reach and knowledge**

| term | means |
|---|---|
| `roster` | what the system **knows**: `<host> => uri{+}`, connectionless. Shared at the root |
| `peer` | what this machine has **dialed**: one member per surface, keyed by the dial's path |
| `listener` | what it **answers on**: the sockets it serves, and therefore what mDNS announces |
| `prefix` (network) | what you **call** it: `<label> => origin`, one listing at the root |
| reach | dialed peers — per frame, retractable, **not** inherited |
| denotation | what a spelling means — machine-global, inherited, never scoped |

**structure and process**

| term | means |
|---|---|
| `space` | a subset of the graph one address pattern serves (`memspace::[pattern=>/usr/#]`) |
| `route` | a space's own path→target table, applied when **it** serves a read or write |
| `mount` / `import` | making a space or an instruction set available (`InstSet.mount`, `@vid` writes) |
| `frame` | a child machine: `push`/`pop` are the call stack, `move` chooses a machine without closing one |
| `redirect` | a short name's home, `big()`/`small()`. Exposed at `*</memory/redirect>`, rewritable deliberately |
| `prefix` (isa) | a vocabulary's short scheme, bound by the import that mounted it |
| `rewrite` | a **compiler** rule over code. Not a name, not an address — it transforms programs |

The rule of thumb: an address is *declared* (a prefix, a name, a route's key), a redirect is *derived* from what was
mounted, and a rewrite is *code*.

---

## 2. poly types

### lst::T (`[,]` — ordered, indexable)

```mtron_pre
[1, 2, 3]             [-- lst of 3 int::T --]
[1, [2, 3]]           [-- nested lst --]
[,]                   [-- empty lst --]
[1,2,3]>>0  
[1,2,3]==[?>1 => +2]  [-- select with rec::T for predicate keys --]
```

### rec::T (`[key=>val, ...]` — unordered, keyed)

```mtron_pre
[name=>'marko', age=>29]
[1=>2, 2=>3, 3=>4]            
[a=>1,b=>2]>>a   
[a=>1,b=>[c=>2,d=>[e=>4]]]>>a/b
[a=>1,b=>[c=>2,d=>[e=>4]]]>>a/b/d
[a=>1,b=>[c=>2,d=>[e=>4]]]>>a/b/d/e
```

### rel::T (relation) — key/value pair

```mtron_pre             
name=>'marko'
name=>'marko'=>age=>29
name=>'marko'=>age=>29.>>.>>.>>
```

### objs (`{,}` — unordered, streamed and bulked)

```mtron_pre
{1,2,3}                                  [-- equivalent to {2,1,3} (unordered) --]
{1,2,3}.isa({1,2,3})                     [-- wrong assumption: checks 1, then 2, then 3 against isa arg --]
{1,2,3}.isa?int{*}<=int{*}({1,2,3})      [-- isa now a barrier and checks stream against stream --]
{1,2,3}.isa?int{*}<=int{*}({3,1,2})      [-- streams are unordered --]
{3,1,2,3}.isa?int{*}<=int{*}({1,{2}3,2}) [-- coefficients accounted for --]
```

---

## 3. coefficients (count/multiplicity)

Every obj has a coefficient. Shorthand is `{n}obj` -- standing for `type{n}::obj`:

```mtron_pre
3                                        [-- coefficient {1} (default) --]
{0}3                                     [-- equivalent to noobj (0 3s) --]
int{5}::3                                [-- coefficient {5} represents 5 3s --]
{5}3                                     [-- shorthand for the previous example (5 3s) --]
{1,2,3}.sum{2}()                         [-- two parallel sums yields {2}6 --]
{1,2,3}.sum{2}().sum()                   [-- {2}6 merged by sum is 12 --]
```

Coefficients propagate through arithmetic and affect count, sum, repeat:

```mtron_pre
{1,2,3}.count()               [-- 3  (sum of coefficients) --]
{1,2,{10}3}.count()           [-- 12  (sum of coefficients) --]
int{50}::10.mult(10)          [-- int{50}::100 (coefficient account for) --]
{int{2}::1,int{3}::2}.sum()   [-- int::8 (coefficients account for) --]
```

---

## 4. Arithmetic & Logic

```mtron_pre
1.plus(2)                      [-- 3 --]
1.plus(_)                      [-- 2  (underscore is the identity function = lhs) --]
1 + 2                          [-- sugar sytnax for previous --]
{1,2,3}.plus(2)                [-- {3,4,5} (applied to each obj in the stream) --]
{1,2,3}.mult(10)               [-- {10,20,30} --]
{1,2,3} * 10                   [-- sugar syntax for previous --]
1.plus(1.plus(1))              [-- 3 (nested) --]
{1,2,3,4}.prod()               [-- 24 --]
{1,2,3}.sum()                  [-- 6 --]
{1,2,3,4,5}.reduce(|plus(0))   [-- 15 (| necessary to block evaluation) --]
```

**lhs arguments** `_` (underscore) is the identity function — returns the unmodified lhs:

```mtron_pre
1.plus(_)                   [-- 2 --]
{1,2,3}.map(_).plus(2)      [-- {3,4,5} --]
```

**compound ops:** `inst?rng<=dom(args){body}` for inline insts (lambdas):

```mtron_pre
{1,2,3}.inst?int<=int(a=>plus(2)){ plus(*a) }      [-- {4,8,18} --]
{1,2,3}.(a=>+2){ +*a }                             [-- sugar syntax for the above --]
```

### Boolean logic

```mtron_pre
3.is(gt(2))                        [-- 3  (filters: passes lhs through if true, noobj if false) --]
3.is(eq(3))                        [-- 3  (filter based on equality) --]
3.isa(3)                           [-- 3  (filter based on value-value testing) --]
3.isa(int::T)                      [-- 3  (filter based on value-type testing) --]
3.is(true)                         [-- 3 --]
{1,2,3,4,5}.is(gt(2))              [-- {3,4,5} --]
{1,2,3,4,5}.is(and(gt(2),lt(5)))   [-- {3,4} --]
{1,2,3,4,5}.?>2.?<5                [-- {3,4} (using sugar'd syntax)--]
1.and(gt(2),lt(5))                 [-- false --]
```

---

## 5. String Operations

```mtron_pre
"goodbye".plus(" nowhere")                    [-- "goodbye nowhere" --]
"goodbye" + " nowhere"                        [-- sugar sytnax for previous --]
'a b c'.split(' ')                            [-- ["a", "b", "c"] --]
'a b c'-<' '                                  [-- sugar syntax for previous --]
{'a','b','c'}>-'-'                            [-- 'a-b-c'  (merge with separator) --]
'ab3cd'.select('\d+')                         [-- ['3'] --]
'ab3cd'.select('\d{2}')                       [-- [,]  (no match — empty pair) --]
'abbc'>>=['a'=>'A','b{2}'=>'X']               [-- 'AXc' (update sugar syntax) --]
'abbc'>>=['a'=>'A','b{2}'=>'X']=='[A-Z]{2,3}' [-- ['AX'] (update and select sugar syntax) --]
```

---

## 6. uri dereferencing (`*` — `from()`)

`*` dereferences a uri to its referent in space:
`@` anchors a uri to its referent in space

```mtron
*/path/to/obj              [-- read obj at uri (detached) --]
@/path/to/obj              [-- real obj at uri (attached) --]
*local:software/           [-- read with trailing --]
*</pa.th/to/o b .j>        [-- angle-bracket handles special chars --]
```

uri wildcards:

```mtron
*/path/+/obj               [-- + matches one segment --]
*/path/+/+                 [-- ++, children at depth 2 --]
*/path/#                   [-- matches all remaining segments (recursive) --]
```

**uri::T** type drives URI-specific operations:

```mtron_pre
http://abc:123/a/b/c.as(rec::T)>>scheme        [-- http --]
http://abc:123/a/b/c.as(rec::T)>>host          [-- abc --]
http://abc:123/a/b/c.as(rec::T)>>port          [-- 123 (noobj if no port) --]
http://abc:123/a/b/c.as(rec::T)>>authority     [-- abc:123 --]
http://abc:123/a/b/c.as(rec::T)>>{schema,path} [-- {http,/a/b/c} --]
```

objs anchored to their address are specified with `@`.

```mtron_pre
[a=>1]@a                 [-- rec anchored at uri myVid --]
[1,2,3,4]@b              [-- lst anchored at *a --]
*a + [b=>2]
*a
@b + [5,6]
*b
```

---

## 7. type casting (`as(type::T)`)

```mtron_pre
1.as(str::T)              [-- '1' --]
1.0.as(int::T)            [-- 1 --]
"abc".as(bytes::T)        [-- 0x616263 --]
"/a/b/c".as(uri::T)       [-- /a/b/c --]
true.as(int::T)           [-- 1 --]
[a=>1,b=>2].as(lst::T)    [-- [(0=>(a=>1)),(1=>(b=>2))] --]
[a,b].as(rec::T)          [-- [0=>a,1=>b] --]
```

Custom types, created with `tid::T[predicate][constructor]@vid` (see the *type system* doc):

```mtron_pre
int::T[is(gt(0))]@posint     [-- a new type: positive integers --]
posint::2                    [-- posint::2  (admitted: the predicate lets it through) --]
[ERROR] posint::-1           [-- refused: a predicate-only type has no constructor to rescue the value --]
```

---

## 8. mapping & filtering

```mtron_pre
{1,2,3,4}.map(+2)                [-- {3,4,5,6} --]
{1,2,3,4}.map(_).plus(2)         [-- same --]
{1,2,3,4}.map(map(+2))           [-- nested --]
{1,2,3}.is(gt(1))                [-- {2,3}  (filter: keep the members the predicate admits) --]
```

### select (structural projection)

```mtron_pre
[a=>1,b=>2,c=>3].select([_=>_])                            [-- [a=>1,b=>2,c=>3] --]
[a=>1,b=>2,c=>3]==[_=>_]                                   [-- syntax sugar for above --]
[a=>1,b=>2,c=>3]==[a=>_]                                   [-- [a=>1] --]
[a=>1,b=>2,c=>3]==[a=>+10]                                 [-- [a=>11] --]
{[a=>1],[a=>2],[a=>3]}==[a=>?>=2.+10]                      [-- {[a=>12],[a=>13]} --]
[1,2,3]==[_,plus(5),_]                                     [-- [1,7,3] --]
```

### filtering (`?` and `is()`)

`lhs ? predicate` is the filter: it keeps the lhs when the predicate admits it and yields `noobj` when it does
not. A rec predicate matches structurally — the value's field is checked against the field's type:

```mtron_pre
{[a=>1],[a=>2],[a=>3]}?[a=>is(gt(1))]   [-- {[a=>2],[a=>3]}  (the rec predicate matches each rec's a-field) --]
{[a=>1],[a=>2],[a=>3]}?[a=>is(lt(3))]   [-- {[a=>1],[a=>2]}  (the same shape admits the low fields) --]
```

**`isa()` is for uris, not values.** Its live row is `where?uri<=uri(rec)`: the arg is a profile over the uri's
fields, the uri is projected against it, and the uri passes through only when the projection agrees with the
original — a profile matcher, and `noobj` when the profile is contradicted:

```mtron_pre
<//2024.12:25/09/00/00/000?tz=-0500>?isa([host=>2024.12, port=>25])  [-- the uri (the profile matches) --]
<//2024.12:25/09/00/00/000?tz=-0500>?isa([host=>2024.12, port=>26])  [-- noobj  (the port contradicts the profile) --]
```

---

## 9. grouping

```mtron_pre
{1,2,3}.group([_=>+10])                 [-- [1=>11, 2=>12, 3=>13] --]
[a=>1,b=>2,c=>3].group([_=>_])          [-- [[a=>1,b=>2,c=>3]=>[a=>1,b=>2,c=>3]] --]
```

---

## 10. Merging & Splitting

### merge (`>-`)

Unwraps collections: `{1,2,3}>-`   # {1,2,3} (flattens coefficient barriers)

```mtron_pre
{1,2,3}>-                            [-- {1,2,3}  (the bare members, unbarriered) --]
[1=>2,2=>3,3=>4]>-                  [-- the rels, unwrapped --]
{1,2}>-[3,4]                         [-- [1,2,3,4]  (the barrier joins into the existing lst) --]
{1,2,3}>-1                           [-- {1,1,2,3}  (1 is added to the stream) --]
[a=>1,b=>2]>-.>-[b=>2]              [-- [a=>1, b=>{2}2]  (the incoming 2 stacks under the existing 2 as a coefficient) --]
```

### split (`-<`)

Distributes elements:

```mtron_pre
1-<[_,_]                            [-- [1,1]  (duplicated along the branches) --]
{1,2,3}-<[plus(1),plus(2)]          [-- {[2,3],[3,4],[4,5]}  (each member against each branch) --]
```

### conditional branch: `-<|[?pred=>a, _=>b]`

```mtron_pre
1-<|[?>1 => +100, _=> +2]          [-- 1=>3  (1 fails ?>1, so the default branch +2 takes it) --]
{1,2}-<|[?>1 => +100, _=> +2]>>    [-- 1=>3, 2=>102  (each member takes its own branch) --]
```

---

## 11. right-shift / left-shift (`>>`, `<<`)

Traverse into structures:

### lsts

```mtron_pre
[1,2,[a=>3],4]<<2                  [-- [[a=>3],4] --]
[1,2,[a=>3],4]>>2                  [-- [1,2] --]
[1,2,[a=>3],4]>>(-2)               [-- [a=>3] (negative indicies) --]
[1,2,[a=>3],4]>>+                  [-- {1,2,[a=>3],4} (selectors are uris) --]
```

### recs

```mtron_pre
[a=>1,b=>2,c=>[d=>3]].dom()        [-- {a,b,c}           (extract keys) --]
[a=>1,b=>2,c=>[d=>3]].rng()        [-- {1,2,[d=>3]}      (extract values) --]
[a=>1,b=>2,c=>[d=>[e=>3]]]>>c      [-- [d=>[e=>3]]       (access by key) --]
[a=>1,b=>2,c=>[d=>[e=>3]]]>>c/d/e  [-- 3                 (walk nested structure) --]
```

### uris

```mtron_pre
a/b/c<<                   [-- b/c    (drop leftmost segment) --]
a/b/c>>                   [-- a/b    (drop rightmost segment) --]
a/b/c<<1                  [-- b/c --]
a/b/c>>1                  [-- a/b --]
a/b/c<<3                  [-- <.>    (empty) --]
```

---

## 12. skip / take

```mtron_pre
{1,2,3,4}.take(2)        [-- {1,2} --]
{1,2,3,4}.skip(2)        [-- {3,4} --]
{1,2,3,4,5}.skip(2).take(2)   [-- {3,4} --]
```

---

## 13. block (`|`)

Delays evaluation — wraps in a monadic barrier:

```mtron_pre
|(plus(30)).map(20)      [-- 20   (the barrier is not evaluated by map) --]
|(plus(30)).swap(20)     [-- 50   (swap applies the barrier's lhs) --]
```

---

## 14. update (`>>=`)

Modifies a value at a uri address (or on stack if not addressed):

```mtron_pre
@xyz >>= [a=>+2]                        [-- merge: add 2 to field 'a' at xyz --]
@xyz/c/d>>=10                           [-- write 10 to path --]
[1,2]@a >>= [_,+4]                      [-- [1,6]@a  (second element +4) --]
[a=>1,b=>2] >>= [b=>none]               [-- [a=>1]  (remove field b) --]
@<people/+>.>>= [name=>"Micky Mouse"]   [-- wildcard update --]
```

`@` means "anchor the write-back to the VID" (persist).  `*` means "anonymous copy" (no write-back):

```mtron_pre
@a/b/c>>= +10      [-- modifies *a/b/c and writes back   --]
*a/b/c>>= +10      [-- evaluates but discards the result --]
```

---

## 15. auto-references (`!*`, `!@`)

Lazy cross-reference via the Router:

```mtron_pre
[company=>!*db:companies/101]     [-- auto_from — resolved on access            --]
[company=>!@db:companies/101]     [-- auto_at — resolved on access, with anchor --]
```

`!*` is sugar for `auto_from(uri)`. When you `.at(company)` on the record, the Router resolves `db:companies/101` and
returns the target Obj.

Chain through auto-refs:

```mtron_pre
*a>>x>>x           [-- follows !* chain to the final target       --]
*a>>x/x            [-- reads a field after resolving the auto-ref --]
```

---

## 16. math instruction

Embedded mathematical expressions:

```mtron_pre
math('1+2')                           [-- 3.0   --]
10.to(a).math('a^2')                  [-- 100.0 --]
10.to(a).plus(10).to(b).math('a+b')   [-- 30.0  --]
```

---

## 17. uri path operations

```mtron_pre
a/b/c.split(/)                     [-- [a,b,c] --]
{a,b,c}>-/                         [-- a/b/c --]
a/b/c.split(/).merge(/)            [-- a/b/c --]
a/b/c.split(/).merge(/).mult(<.>)  [-- a/b/c  (identity) --]
```

---

## 18. failure & error handling

```mtron_pre
1.plus('a').catch(34)                         [-- 34 --]
1.plus('a').catch(cause().cause())            [-- noobj --]
1.plus(mult(throw('bad'))).catch(34).plus(2)  [-- 36 --]
```

---

## 19. objs (coefficient collections `{ }`)

Braces create a multi-value Objs collection (coefficient barrier):

```mtron_pre
{1,2,3}                        [-- three ints --]
>-.sum()                       [-- sum flattens the coefficient barrier --]
```

Collections distribute operations:

```mtron_pre
{1,2,3}.plus(2)                [-- {3,4,5} --]
{1,2,3}>-                      [-- unmerge — flattens barriers --]
```

---

## 20. type annotations

Inline type predicates:

```mtron_pre
?int::T                        [-- test: is this an int? --]
?uri::T                        [-- is this a uri? --]
isa(uri::T)                    [-- same, sugar --]
?int{5}::3                     [-- coefficient-aware check --]
int{?}::10                     [-- optional coefficient {0,1} --]
```

`==`

```mtron_pre
[a=>1,b=>2,c=>3]==[a=>_]          [-- select with pattern match --]
[a=>1,b=>2,c=>3]==[a=>is(gt(1))]  [-- select with filter --]
```

---

## 21. pattern matching (`?=` prefix)

```mtron_pre
?=1        [-- check if equal to 1 --]
?>1        [-- check if greater than 1 --]
?<5        [-- check if less than 5 --]
?int::T    [-- check if type is int --]
```

---

## 22. sugar & syntax summary

| Sugar     | Expansion        | Use                      |
|-----------|------------------|--------------------------|
| `*uri`    | `from(uri)`      | dereference uri          |
| `!*uri`   | `auto_from(uri)` | lazy cross-ref           |
| `@uri`    | `at(uri)`        | anchor to uri location   |
| `_`       | `id()`           | identity function        |
| `>>=`     | `update()`       | update-and-write-back    |
| `>>`      | `rshift()`       | right-shift (drop last)  |
| `<<`      | `lshift()`       | left-shift (drop first)  |
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

## 23. expression evaluation model

1. **left-to-right chaining:** `lhs.inst(rhs)` — `inst` receives `lhs` and `rhs`
2. **coefficient propagation:** ops distribute over collections; result coefficients combine
3. **space reads:** `*uri` fetches from the graph; `!*uri` fetches lazily on `.at()`
4. **write-back:** `@`-prefixed writes persist through `>>=` back to space
5. **no mutation of existing objects** — operations create new Objs (immutable)

```mtron_pre
[HEADER] chains: every inst's output is the next inst's input
{1,2,3,4}.sum()                     [-- 10 --]
{1,2,3,4}.sum{2}()                  [-- {2}10  (the same job, run twice in parallel) --]
{1,2,3,4}.sum{2}().sum()            [-- 20  (the outer sum folds the coefficient in) --]
{1,2,3,4}-<[plus(1), plus(10)]>-    [-- 2, 11, 3, 12, 4, 13, 5, 14  (distribute to each function, then merge) --]
{1,2,3,4}.skip(1).take(2)           [-- {2,3}  (window: drop the first, keep two) --]
```
