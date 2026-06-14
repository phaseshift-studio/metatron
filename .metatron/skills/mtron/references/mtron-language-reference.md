# mtron Language Reference

A data-flow language over the metatron object graph.  Every value is an **Obj** — int, str, real, bool, uri, rec, lst, inst, code, bytes, etc.  Expressions chain left-to-right: `lhs.inst(rhs)`.

---

## 1. Literals & Primitives

```mtron
1           # Int (64-bit signed)
1.0         # Real (double)
true        # Bool
false       # Bool
"hello"     # Str (double-quoted)
'hello'     # Str (single-quoted, alternative)
<hello>     # Uri (fURI, angle-bracket)
/foo/bar    # Uri (path literal)
a           # Uri (bare name — no angle brackets if alphanumeric)
0x0F        # Bytes (hex literal)
noobj       # The empty / absent value
```

---

## 2. Collections

### Lists (`[ , ]` — ordered, indexable)

```mtron
[1, 2, 3]         # List of 3 ints
[1, [2, 3]]       # Nested list
[a=>1, b=>2]      # Record (key-value pairs)
[,]               # Empty list
```

### Records (`[key=>val, ...]` — unordered, keyed)

```mtron
[name=>'Alice', age=>30]
[1=>2, 2=>3, 3=>4]
```

### Objs / Sets (`{ , }` — multi-value coefficient collection)

```mtron
{1, 2, 3}         # Set-like objs collection
{1, 1, 2}         # Duplicates preserved
{a=>1, b=>2}      # Rel (relation) collection
```

### Indexing / Access

```mtron
[1,2,3].0         # → 1
[1,2,3]>>0        # → 1  (via right-shift)
[a=>1,b=>2].a     # → 1
[a=>1,b=>2]>>a    # → 1  (via right-shift)
```

### Rel (relation) — key/value pair

```mtron
a=>1              # URI a → int 1
name=>'Alice'     # URI name → str Alice
```

Objs can carry a **vid** (address URI) via `@`:
```mtron
[a=>1]@myVid            # list anchored at uri myVid
[1,2,3,4]@a              # list anchored at *a
```

---

## 3. Coefficients (count / multiplicity)

Every Obj has a coefficient.  Displayed as `{n}`:
```mtron
3               # coefficient {1} (default)
int{5}::3       # coefficient {5}
{1,2,3}.sum{2}()   # sum, then coefficient becomes {2}
```

Coefficients propagate through arithmetic and affect count, sum, repeat:
```mtron
{1,2,3}.count()             # 3  (sum of coefficients)
int{50}::10.mult(10)        # int{50}::100 (coefficient preserved)
int{2}::1,int{3}::2.sum()   # int{2}::3 (coefficient depends on op)
```

---

## 4. Arithmetic & Logic

```mtron
1.plus(2)                   # 3
1.plus(_)                   # 2  (underscore is the identity function = lhs)
{1,2,3}.plus(2)             # {3,4,5}
{1,2,3}.mult(10)            # {10,20,30}
1.plus(1.plus(1))           # 3  (nested)
{1,2,3,4}.prod()            # 24
{1,2,3}.sum()               # 6
{1,2,3,4,5}.reduce(|plus(0))   # 15
```

**Short-circuit:** `_` (underscore) is the identity function — returns the unmodified lhs:
```mtron
1.plus(_)                   # 2
{1,2,3}.map(_).plus(2)      # {3,4,5}
```

**Compound ops:** `?inst<=type(body)` for inline insts:
```mtron
{1,2,3}.inst?int<=int(a=>plus(2)){ plus(*a) }     # {4,8,18}
```

### Boolean logic

```mtron
3.is(gt(2))                 # 3  (filters: passes lhs through if true, noobj if false)
3.is(true)                  # 3
{1,2,3,4,5}.is(gt(2))       # {3,4,5}
{1,2,3,4,5}.is(and(gt(2),lt(5)))   # {3,4}
1.and(gt(2),lt(5))          # false
```

---

## 5. String Operations

```mtron
"hello".plus(" world")        # "hello world"
'a b c'.split(' ')            # ["a", "b", "c"]
{"a","b","c"}>-' '            # "a b c"  (merge with separator)
'ab3cd'.regex('\d+')           # ['3']
'ab3cd'.regex('\d{2}')         # [,]  (no match — empty pair)
```

---

## 6. URI Dereferencing (`*` — `from()`)

`*` reads an Obj from the Router by URI:

```mtron
*/path/to/obj              # read Obj at that URI
*local:software/           # read with trailing / → uri=>obj relation
*</path/to/obj>            # angle-bracket handles special chars
```

Wildcards:
```mtron
*/path/+/obj               # + matches one segment
*/path/+/+                 # ++, children at depth 2
*/path/#                   # # matches all remaining segments (recursive)
```

**uri::T** type drives URI-specific operations:
```mtron
a/b/c.dom()                # a  (first segment)
a/b/c.rng()                # b/c  (remaining segments)
a/b/c.name()               # c  (last segment)
```

---

## 7. Type Casting (`.as(type::T)`)

```mtron
1.as(str::T)              # "1"
1.0.as(int::T)            # 1
"abc".as(bytes::T)        # 0x616263
"/a/b/c".as(uri::T)       # /a/b/c
true.as(int::T)           # 1
[a=>1,b=>2].as(lst::T)    # [(0=>(a=>1)),(1=>(b=>2))]
[a,b].as(rec::T)          # [0=>a,1=>b]
```

Custom types via `?predicate`:
```mtron
int::T[is(gt(0))]@nat     # type nat, only positive ints
nat::2                    # ok
nat::-1                   # <ERROR>
```

---

## 8. Mapping & Filtering

```mtron
{1,2,3,4}.map(+2)                # {3,4,5,6}
{1,2,3,4}.map(_).plus(2)         # same
{1,2,3,4}.map(map(+2))           # nested
{1,2,3}.where(gt(1))             # {2,3}  (filter: keep if predicate matches)
{1,2,3}.is(gt(1))                # {2,3}  (same, filter via is())
```

### Select (structural projection)

```mtron
[a=>1,b=>2,c=>3].select([_=>_])                            # [a=>1,b=>2,c=>3]
[a=>1,b=>2,c=>3].select([a=>_])                            # [a=>1]
[a=>1,b=>2,c=>3].select([a=>+10])                          # [a=>11]
{[a=>1],[a=>2],[a=>3]}.select([a=>?>=2.+10])               # {[a=>12],[a=>13]}
[1,2,3].select([_,plus(5),_])                               # [1,7,3]
```

### Where (filter)

```mtron
{[a=>1],[a=>2],[a=>3]}.where([a=>is(gt(1))])             # {[a=>2],[a=>3]}
[1,2,3].select([_,plus(5),_]).where([_,is(gt(5)),_])       # [1,7,3]
```

---

## 9. Grouping

```mtron
"{1,2,3}.group([_=>+10])                # [1=>11, 2=>12, 3=>13]
"[a=>1,b=>2,c=>3].group([_=>_])          # [[a=>1,b=>2,c=>3]=>[a=>1,b=>2,c=>3]]
```

---

## 10. Merging & Splitting

### Merge (`>-`)

Unwraps collections: `{1,2,3}>-`   # {1,2,3} (flattens coefficient barriers)

```mtron
{1,2,3}>-                            # {1,2,3}
[1=>2,2=>3,3=>4]>-                  # {1=>2,2=>3,3=>4}
{1,2}>-[3,4]                         # [1,2,3,4]
{1,2,3}>-1                           # {1,1,2,3}
[a=>1,b=>2]>-.>-[b=>2]              # [a=>1,b=>2]  (merge into existing rec)
```

### Split (`-<`)

Distributes elements:
```mtron
1-<[_,_]                            # [1,1]
{1,2,3}-<[plus(1),plus(2)]          # {2,3,4,5}
```

### Conditional branch: `-<|[?pred=>a, _=>b]`

```mtron
1-<|[?>1 => +100, _=> +2]          # {3,102}  (1→2 via default, then filtered)
{1,2}-<|[?>1 => +100, _=> +2]>>   # {3,102}
```

---

## 11. Right-Shift / Left-Shift (`>>`, `<<`)

Traverse into structures:

### On lists
```mtron
[1,2,[a=>3],4]<<                   # [2,[a=>3],4]   (drop first)
[1,2,[a=>3],4]>>                   # [1,2,[a=>3]]   (drop last)
[1,2,[a=>3],4]<<2                  # [[a=>3],4]
[1,2,[a=>3],4]>>2                  # [1,2]
```

### On records
```mtron
[a=>1,b=>2,c=>[d=>3]]<<            # {a,b,c}           (extract keys)
[a=>1,b=>2,c=>[d=>3]]>>            # {1,2,[d=>3]}      (extract values)
[a=>1,b=>2,c=>[d=>3]]>>2           # {3,[d=>3]}        (deeper)
```

### On URIs
```mtron
a/b/c<<                   # b/c    (drop leftmost segment)
a/b/c>>                   # a/b    (drop rightmost segment)
a/b/c<<1                  # b/c
a/b/c>>1                  # a/b
a/b/c<<3                  # <.>    (empty)
```

---

## 12. Skip / Take

```mtron
{1,2,3,4}.take(2)        # {1,2}
{1,2,3,4}.skip(2)        # {3,4}
{1,2,3,4,5}.skip(2).take(2)   # {3,4}
```

---

## 13. Barrier (`|`)

Delays evaluation — wraps in a monadic barrier:
```mtron
|(plus(30)).map(20)      # 20   (the barrier is not evaluated by map)
|(plus(30)).swap(20)     # 50   (swap applies the barrier's lhs)
```

---

## 14. The `>>=` Update Instruction

Modifies a value at a URI address and writes back:

```mtron
@xyz >>= [a=>+2]                      # merge: add 2 to field 'a' at xyz
@xyz/c/d>>=10                         # write 10 to path
[1,2]@a >>= [_,+4]                   # [1,6]@a  (second element +4)
[a=>1,b=>2] >>= [b=>none]            # [a=>1]  (remove field b)
@<people/+>.>>= [name=>"Micky Mouse"]   # wildcard update
```

`@` means "anchor the write-back to the VID" (persist).  `*` means "anonymous copy" (no write-back):
```mtron
@a/b/c>>= +10      # modifies *a/b/c and writes back
*a/b/c>>= +10      # evaluates but discards the result
```

---

## 15. Auto-references (`!*`, `!@`)

Lazy cross-reference via the Router:

```mtron
[company=>!*db:companies/101]     # auto_from — resolved on access
[company=>!@db:companies/101]     # auto_at — resolved on access, with anchor
```

`!*` is sugar for `auto_from(uri)`.  When you `.at(company)` on the record, the Router resolves `db:companies/101` and returns the target Obj.

Chain through auto-refs:
```mtron
*a>>x>>x           # follows !* chain to the final target
*a>>x/x            # reads a field after resolving the auto-ref
```

---

## 16. Math Instruction

Embedded mathematical expressions:
```mtron
math('1+2')                    # 3.0
10.to(a).math('a^2')           # 100.0
10.to(a).plus(10).to(b).math('a+b')   # 30.0
```

---

## 17. URI Path Operations

```mtron
a/b/c.split(/)                   # [a,b,c]
{a,b,c}>-/                       # a/b/c
a/b/c.split(/).merge(/)           # a/b/c
a/b/c.split(/).merge(/).mult(<.>)  # a/b/c  (identity)
```

---

## 18. Failure & Error Handling

```mtron
1.plus('a').catch(34)                    # 34
1.plus('a').catch(cause().cause())       # noobj
1.plus(mult(throw('bad'))).catch(34).plus(2)  # 36
```

---

## 19. Objs (coefficient collections `{ }`)

Braces create a multi-value Objs collection (coefficient barrier):

```mtron
{1,2,3}                        # three ints
>-.sum()                       # sum flattens the coefficient barrier
```

Collections distribute operations:
```mtron
{1,2,3}.plus(2)                # {3,4,5}
{1,2,3}>-                      # unmerge — flattens barriers
```

---

## 20. Type Annotations

Inline type predicates:
```mtron
?int::T                        # test: is this an int?
?uri::T                        # is this a uri?
isa(uri::T)                    # same, sugar
?int{5}::3                     # coefficient-aware check
int{?}::10                     # wildcard coefficient
```

`==`
```mtron
[a=>1,b=>2,c=>3]==[a=>_]     # select with pattern match
[a=>1,b=>2,c=>3]==[a=>is(gt(1))]  # select with filter
```

---

## 21. Pattern Matching (`?=` prefix)

```mtron
?=1        # check if equal to 1
?>1        # check if greater than 1
?<5        # check if less than 5
?int::T    # check if type is int
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
3. **Router reads:** `*uri` fetches from the graph; `!*uri` fetches lazily on `.at()`
4. **Write-back:** `@`-prefixed writes persist through `>>=` back to the Router
5. **No mutation of existing objects** — operations create new Objs (immutable)

```mtron
# Chaining example (read test data from test file):
{1,2,3,4}.sum{2}().sum?int<=int{1,7}().sum()-<[_,_]>-.sum?int<=int{2}()  # → int::40
```
