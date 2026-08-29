---
name: mtron
description: a guide to the mtron language
---

# defining structures and processes in metatron

metatron integrates various technologies, protocols, and standards within a single unified storage and processing
framework. At the highest level, metatron **storage** is a uri (uniform resource identifier) graph where any vertex in
the graph can have an associated obj (object). metatron **processing** is founded on a fluent, monadic, data flow model
aimed at graph traversal and obj manipulation.

metatron's approach to computing yields a **reflective computing environment** where every denoted object can be reified
and referred to and thus, manipulated.

**if it exists, it has a uri. if it has a uri, it can be accessed.**

## space: storage

A system that supports the encoding of a uri/obj-graph is called a `space`. An example uri/obj-graph maintained by a
`space` responsible for the address pattern `/a/#` is diagrammed below.

```mtron_pre
memspace::[pattern=>/a/#]@/sys/space/a
```

```mtron
     1  2  3
 0   ⋮  ⋮  ⋮ 
 ⋮ ┌─x──y──z
─a─┤      
   └─b─┬─c ⋯ |plus(2) 
    ⋰  └─d──e
[q=>r]   ⋮    ⋱
        'm'   [1.0,0xa5,true]
```

The most straightforward (albeit verbose) means of constructing the graph above is to denote uri vertices using path
syntax, objs using obj syntax, and connecting them via the `ref` instruction (sugar'd `->`). This convention is
presented below.

```mtron_pre
/a       -> 0
/a/x     -> 1
/a/x/y   -> 2 
/a/x/y/z -> 3
/a/b     -> [q=>r]
/a/b/c   -> |plus(2)
/a/b/d   -> 'm'
/a/b/d/e -> [1.0,0xa5,true]
```

To retrieve stored objs, dereference their respective uris. In technical parlance, the uri is the **reference**, the obj
is the **referent**. The process of moving from the reference to the referent is called **dereferencing** (also, known
as **resolving**).

```mtron_pre
*/a      
*/a/x    
*/a/x/y  
*/a/x/y/z
*/a/b    
*/a/b/c  
*/a/b/d  
*/a/b/d/e
```

The `*` operator is the sugar form of the `from()` instruction and it takes a uri argument and yields the obj coupled to
that uri. Of particular importance, and of fundamental significance to metatron, is the captured in the result of
`*/a/b`. The ability for "polys" (`lst`,`rec`,and `rel` objs) to maintain an internal uri scheme that interacts with the
outer space's uri scheme is a reoccurring theme throughout metatron.

### uri categories

. absolute: a uri with a / prefix -- `/a/b`. . relative: a uri with no / prefix -- `a/b`.

. branch  : a uri with a / suffix -- `a/b/`. . node    : a uri with no / suffix -- `a/b`.

An absolute branch is `/a/b/`. An absolute node is `/a/b`. A relative branch is `a/b/`. A relative node is `a/b`.

### obj types

#### mono types

|    type    | examples                             |
|:----------:|--------------------------------------|
| `bool::T`  | `true` or `false`                    |                    
| `bytes::T` | `0x[a-f\|A-F\|0-9]`                  |                  
|  `int::T`  | `...,-2,-1,0,1,2,...`                |
| `real::T`  | `...-2.0,-1.012,0.0,1.134,2.377,...` |
|  `str::T`  | `"a b", 'a b', """multi line a b"""` |
|  `uri::T`  | `mtron://host:8555/a/b/c?x=1&y=2`    |

#### poly types

| type  | examples              |
|:-----:|-----------------------|
| `rel` | `(k=>v)`              |
| `lst` | `[1,2,3,...]`,        |
| `rec` | `[k1=>v1,k2=>v2,...]` |

#### call types

|  type  | examples                                           |
|:------:|----------------------------------------------------|
| `inst` | `inst?rng<=dom(arg0=>A::T,arg1=>B::T){ inst* }@op` |
| `code` | `inst().inst(inst(a,b)).inst()`                    |

Finally, within the base types, there is `noobj` which is a mono/poly/call.

#### space types

A `space::T` refines `rec::T`. Common spaces include:

|     type     | description                                                    |
|:------------:|----------------------------------------------------------------|
|  `memspace`  | in-memory trie data structure                                  |
| `httpspace`  | the web as a metatron space                                    |
|  `wsspace`   | web sockets as a space                                         | 
|  `fsspace`   | file system as with nested directories and symlinks as a space |
| `mqttspace`  | mqtt broker with nested topics and topic references as a space |
| `tblespace`  | relational database with foreign key edges as a space          | 
| `grphspace`  | graph database with native edges as a space                    |
| `dcmntspace` | document $DBRef/JSON nest edges as a space                     |
| `dckrspace`  | docker images, containers, volumes, networks, etc. as a space  |

## machine: process

The uri/obj-graph formed by the aggregate of all supporting spaces is processed using **ring-oriented machines**.
Machine behavior is defined by the mtron language -- a ring-based language composed of a `*` monoid and a `+` groupoid.
Practically speaking, mtron supports **chained/nested function composition**, where special attention is put to a
function's domain, range, and argument types.

### traversing the uri graph splice

Given the previously constructed graph, a non-sugar'd brute force approach to uri graph traversal is presented below.

```mtron_pre
start(/a)
start(/a).rshift()
start(/a).rshift().rshift()
start(/a).rshift().rshift().rshift()
start(/a).rshift().rshift().rshift().rshift()
start(/a).rshift().rshift().rshift().rshift().rshift()
```

Now, the the more terse, sugar'd way of expressing the same constructs above.

```mtron_pre
/a.>>
/a.>>.>>
/a.>>.>>.>>
/a.>>.>>.>>.>>
/a.>>.>>.>>.>>.>>
```

```mtron_pre
start(/a).rshift().rshift().rshift().rshift().rshift().explain()
/a.>>.>>.>>.>>.>>.explain()
```

### state transformation, branch selection, and loop iteration

For a programming language to be considered "universal" (able to express any type of computation), it must support:
state, looping, and branching. Storing objs in uri space is satisfies the state requirement. For looping:

```mtron_pre
/a.repeat(code=>>>,emit=>true)
/a.repeat(code=>>>,until=>loop()?>2, emit=>true)
/a.repeat(code=>>>,until=>loop()?>2, emit=>false)
```

## mtron language examples

```mtron_pre
{1,1,1,2,2,3}.plus(2)
{1,1,1,2,2,3}.plus(2).sum()
{1,1,1,2,2,3}.plus(2).sum?int<=int{1,3}()
```

```mtron_pre
{1,2,3}-<[?>2 => '${_} is greater 2', ?<=2 => '${_} is less than 2']>-
```

```mtron_pre
[-- type definitons --]
int::T[?>0]@nat
rec::T[?[name=>str::T,age=>nat::T]]@person
[-- value definitions --]
person::[name=>'marko',age=>29]
person::[name=>'unnamed',age=>-1]
```

### docq query processor to access documentation

Any obj can have associated documentation of type `docs::T`. A good way to learn about an instruction is to resolve it
with a `?docq` query processor.

```mtron_pre
*select?docq
*where?docq
*group?docq
*as?rng=int&docq
*as?dom=int&docq
```

## references

Do not guess at instruction signatures. Every instruction has documentation attached to it via `?docq`. **Read the
documentation of the code you are about to execute.**

* **Full Language Ref**: `references/mtron-language-reference.md`
* **UI Architecture (Java)**: `references/metatron-ui-architecture.md` — Widget lifecycle, JRec state bridge, Style
  system, FloatingSurface, uiInstSet registration. How to create/modify widgets.
* **DSH Memory Bus**: `references/dsh-mtron.md` — The first inter-harness memory adapter: DSH zstd-JSONL transcripts to
  typed message recs (`user`, `system`, `thinking`, `ai`, `tool_result`) via `assets/dsh_memory_loader.py`, loaded in a
  live VM with `*<mfs:file>.parse()` and written `.to(/usr/<agent>/message)`.
* **Casting/Types**: Use `.as(type::T)` for structural validation during projection.
* **Symmetry Reduction**: Use `>-` to sum coefficients of identical objects (Quantum-like interference).
