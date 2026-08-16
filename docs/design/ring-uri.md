# Ring and URI: The Theory of Reference

The session notes of 2026-08-16 — the working theory behind metatron's URI space, the reference/referent duality, and the ring that unifies them. Companion to `docs/website/adoc/uri.adoc` (the uri ring) and `docs/website/articles/stream-ring-theory.pdf` (the stream ring).

## 1. The Address-First Philosophy

**metatron is address-first: the uri (the address) is the primary citizen; the value is the inhabitant.**

```
mtron> a          ==> a      [-- the address, default view --]
mtron> *a         ==> 5      [-- the value, deliberately fetched --]
```

Most languages make the address an opaque implementation detail (`a` *is* `5`). Metatron inverts it: the address space is visible, walkable, composable, restructured — and the value is what you fetch when you want it. The types are **address shapes** filled with values: `person::T[?[name=>str::T, age=>int::T]]` is a shape of keys; a directory is the shape of the filesystem's addresses — its deref is its own branch uri, walked with `>>` and `/`.

Marko's reaction to `>>` walking six levels of pure address structure:

```
/usr/marko.>>.>>.>>.>>.>>.>>    ==>   /usr/marko/console/menu/console/pane/0/stream
                                       ... every result a uri, nothing resolved ...
```

> "FINALLY!!!!!! For how many years have I struggled with (uri=>obj) pairs."

## 2. The Reference and the Referent — Two Walks

Every address is a **reference** (uri) coupled to a **referent** (obj). `>>` is a single operator with a dual nature — you choose which side you walk:

| walk | operand | what `>>` does | cost |
|---|---|---|---|
| **reference** | uri | **extends / descends** the address tree | obj-free, nothing resolves |
| **referent** | poly (rec/lst) | **selects a branch** — keyed or indexed | values materialize |

Walking the reference is free and side-effect-free — no objs wake, no instructions fire. Stepping onto the referent is a deliberate choice.

## 3. The Three Bridge Modes

`*` crosses the bridge; `@` collapses it; `!*` defers it.

| mode | meaning | python analogy |
|---|---|---|
| `*a` | deref — reference → referent, one-way | pass by value |
| `@a` | anchor — the referent **carries its address** (`5@a`), so writes flow back | pass by reference |
| `!*<uri>` | deferred reference — the bridge not yet crossed | reference only |

The `@` form is the ring's fused unit made concrete: `5@a` is `5 · 1_a` — the value multiplied by its multiplicative identity. `@a + 2` yields `7@a` (still anchored), so the result writes through to `a`. That is why the update language needs both: `@a >>= x` is a write-through; `*a >>= x` is a muted write.

## 4. The Navigation Grammar

```
uri/child          descend the path by name        (reference, named)
uri >>             the child uris                  (reference, branch — obj-less)
uri >> <int>       the descendants exactly N deep  (reference, walk)
uri >> <path>      the target uri                  (reference, navigate)
uri <<             the parent uri                  (reference, retract one)
uri << <int>       retract N levels
uri << <path>      retract a matching postfix      (a/b/c/d << c/d => a/b)
*<uri>             the referent                    (the bridge)
```

`<<` is **pure uri arithmetic** — retract/pretract over the uri's own segments, never an obj parent link. The uri carries its parent in its structure.

## 5. Depth, Origin, and the Three Address Spaces

`>> N` means "descend N", and each structure resolves the integer in its own address space:

| structure | address space | `>>0` selects | why |
|---|---|---|---|
| **rec** | key (name) | the field named "0" | no origin — unordered → noobj |
| **lst** | index (position) | the 0-th element | ordered → the head |
| **uri** | extension (depth) | the uri itself | descend 0 = the root, the identity |

Ordered structures (lst, uri) have an origin; unordered (rec) do not. `>>0` is not a numerical question — it asks "where is your origin?" and each shape points at a different place. The clean unification: **"0" = the identity of `>>`** (zero applications, the obj itself) on all three, with positional access moving to its own glyph.

## 6. The Stream Ring

The streams are **weighted multi-sets** — the coefficient `c` is the multiplicity, and union is coefficient-wise addition (`{m}x + {n}x = {m+n}x`). Coefficients are signed, so the merge is linear:

- **constructive interference** — `{2}x + {3}x = {5}x`
- **destructive interference** — `{2}x + {-2}x = {0}x` (annihilation)
- **`noobj{0}`** — the terminal of all `{0}#`: the multiplicative zero that absorbs anything, so a delete needs no read of the current magnitude

The ring has two delete doors: the **additive** (the inverse `{-n}x` — you must know the magnitude, read-then-write) and the **multiplicative** (`x·0 = 0` — free, no knowledge needed). `noobj` is the multiplicative zero made universal.

## 7. Split and Merge

```
a -< [_=>c, _=>d]      ==>  [a=>{c,d}]    (distribute, merge → additive)
a -< [-<(_=>c),-<(_=>d)]  ==>  [a=>c, a=>d]  (distribute, keep factored → multiplicative)
```

- **`-<`** (split) is multiplication distributing over the poly — the poly is the **additive container**, the *parentheses* of the operand ring.
- **`>-`** (merge) is the application of the addition — turning the parentheses into a stream.

`[a,b,c]` and `[1,2,3]` are the same branch of rels, relabeled: `lst` is the indexed branch, `rec` is the named branch, and both are `rel{*}::T`.

## 8. Filters as Conditions and Types

There is no `if/else` — a conditional is a **filter in an additive container**, distributed by `-<` and merged by `>`:

```
{1,43,5} -< [?>2 => 'big', ?<=2 => 'small'] >>    ==>  'small', {2}'big'
```

`?>2` and `?<=2` are annihilators (`ā = 1 - a`) — mutual exclusivity falls out of the filter ring, not a branch statement.

And the type system is the same filter:

```
obj * type = obj{?}     (a type is a ?-predicate; the product is the filter applied)

(marko + 29) * (str::T + int::T)
  = marko·str::T + marko·int::T + 29·str::T + 29·int::T
  = marko        + noobj       + noobj       + 29
```

The distributive law makes type-checking compositional — the rec's check decomposes into per-field checks, and failure is **annihilation, not error** (`marko * int::T = noobj`).

## 9. The Write-Base Question: COLOCATE vs FACTOR

The generic write/update path asks of a located poly: **is this a multiplicative referent (a colocated base to cascade into and rewrite) or an additive container (parentheses over independent referents)?**

- **COLOCATE** — a serial, colocated poly (`rec` in a file) → a merge base.
- **FACTOR** — a directory → **never a base**; writes land on the leaves.

A directory derefs to its own uri (a branch, trailing `/`), and a uri is not a poly — so `locateBasePoly` structurally cannot see a directory as a merge target. The directory's additive nature is expressed by the type system itself, not by a guard.

## 9a. Directories are branches — the trailing-slash invariants

A directory derefs to its uri **as a branch** (trailing `/`); a file derefs to content. The walk (`>>`, `/`) marks directory children with the trailing slash, so the structure self-describes:

```
*<dir>        →  dir/                (a branch — the directory's own uri)
dir >>        →  {dir/sub/, dir/file} (directories are branches, files are nodes)
dir/sub >> 2  →  {dir/sub/deep/}      (the depth-N leaves)
```

The invariants are carried by the fURI operations themselves:

- **extend** a branch → branch; **retract** a branch → branch (`a/b/c/ << 2 => a/`)
- **extend** an absolute → absolute; **retract** an absolute → absolute (`/a/b/c >> d => /a/b/c/d`)
- `a/b/c/ << 2 => a/`; `/a/b/c >> d/ => /a/b/c/d/`

The trailing `/` is the branch-ness; the leading `/` is the absolute-ness — both sticky through navigation.

## 9b. Navigation never invents an address

`uri >> <path>` returns the child **only if it exists** — the target resolves through the space, otherwise `noobj`. The old behavior blindly extended the uri, fabricating addresses like `.../BootLoader.java/Tokens.java`. A reference that doesn't exist isn't a reference — it's a lie. Navigation finds addresses; it never builds them.

## 9c. The whole arc in one line

```
<mfs:../src>>>main/java>>3.>><Tracer.java>.*_.as(ide_java::T)
```

walk the branch (`src`) → descend the path (`main/java`) → walk 3 levels → navigate to a *real* child (`Tracer.java`) → step onto the referent (`*`) → parse into the coarse IDE schema (`.as(ide_java::T)`). The reference walk, the existence check, the bridge, and the language bridge compose into the agent IDE's core primitive — one expression, editable Java structure out.

## 10. Open Design Decisions

- **Unify `rec >> N` as depth.** Today `uri >> N` is a depth-walk but `rec >> N` is a positional index. Unifying (rec `>> N` = broadcast N, index access to its own glyph) makes the `rshift_chain` rewrite (`>>.>>.>> => >> 3`) sound everywhere. The `rewriteChain` API in `Rewriter` (`match((inst, minLen))`) is built and verified.
- **The `rshift_chain` rewrite** is currently disabled: it is referentially correct on uris but type-blind, and it would compress the rec value-broadcast `>>.>>.>>.>>` (≠ an index).
