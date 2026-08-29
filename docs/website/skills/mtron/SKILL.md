---
name: mtron
description: a guide to the mtron language
---

# mtron: defining processes in metatron

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

```mtron
mtron> memspace::[pattern=>/a/#]@/sys/space/a
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

```mtron
mtron> /a       -> 0
==>0
mtron> /a/x     -> 1
==>1
mtron> /a/x/y   -> 2
==>2
mtron> /a/x/y/z -> 3
==>3
mtron> /a/b     -> [q=>r]
==>[q=>r]
mtron> /a/b/c   -> |plus(2)
mtron> /a/b/d   -> 'm'
==>'m'
mtron> /a/b/d/e -> [1.0,0xa5,true]
==>[1.0000,0xa5,true]
```
To retrieve stored objs, dereference their respective uris. In technical parlance, the uri is the **reference**, the obj
is the **referent**. The process of moving from the reference to the referent is called **dereferencing** (also, known
as **resolving**).

```mtron
mtron> */a
==>0
mtron> */a/x
==>1
mtron> */a/x/y
==>2
mtron> */a/x/y/z
==>3
mtron> */a/b
==>[q=>r,c=>plus(2),d=>[e=>[1.0000,0xa5,true]]]
mtron> */a/b/c
mtron> */a/b/d
==>[e=>[1.0000,0xa5,true]]
mtron> */a/b/d/e
==>[1.0000,0xa5,true]
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

```mtron
mtron> start(/a)
==>/a
mtron> start(/a).rshift()
==>/a/b
==>/a/x
mtron> start(/a).rshift().rshift()
==>/a/b/q
==>/a/b/c
==>/a/b/d
==>/a/x/y
mtron> start(/a).rshift().rshift().rshift()
==>/a/b/d/e
==>/a/x/y/z
mtron> start(/a).rshift().rshift().rshift().rshift()
==>/a/b/d/e/0
==>/a/b/d/e/1
==>/a/b/d/e/2
mtron> start(/a).rshift().rshift().rshift().rshift().rshift()
```
Now, the the more terse, sugar'd way of expressing the same constructs above.

```mtron
mtron> /a.>>
==>/a/b
==>/a/x
mtron> /a.>>.>>
==>/a/b/q
==>/a/b/c
==>/a/b/d
==>/a/x/y
mtron> /a.>>.>>.>>
==>/a/b/d/e
==>/a/x/y/z
mtron> /a.>>.>>.>>.>>
==>/a/b/d/e/0
==>/a/b/d/e/1
==>/a/b/d/e/2
mtron> /a.>>.>>.>>.>>.>>
```
```mtron
mtron> start(/a).rshift().rshift().rshift().rshift().rshift().explain()
==>"""
    op      dom          rng      args   f    desc      c_dom  c_rng 
    start   noobj{0}::T  uri::T   /a     <j>  initial   {0}    {1}   
    rshift  uri::T       #{*}::T  noobj  <j>  standard  {1}    {*}   
    rshift  A::T         B{*}::T         <j>  standard  {1}    {*}   
    rshift  A::T         B{*}::T         <j>  standard  {1}    {*}   
    rshift  A::T         B{*}::T         <j>  standard  {1}    {*}   
    rshift  A::T         B{*}::T         <j>  standard  {1}    {*}   
   """
mtron> /a.>>.>>.>>.>>.>>.explain()
==>"""
    op      dom          rng      args   f    desc      c_dom  c_rng 
    start   noobj{0}::T  uri::T   /a     <j>  initial   {0}    {1}   
    rshift  uri::T       #{*}::T  noobj  <j>  standard  {1}    {*}   
    rshift  A::T         B{*}::T         <j>  standard  {1}    {*}   
    rshift  A::T         B{*}::T         <j>  standard  {1}    {*}   
    rshift  A::T         B{*}::T         <j>  standard  {1}    {*}   
    rshift  A::T         B{*}::T         <j>  standard  {1}    {*}   
   """
```
### state transformation, branch selection, and loop iteration

For a programming language to be considered "universal" (able to express any type of computation), it must support:
state, looping, and branching. Storing objs in uri space is satisfies the state requirement. For looping:

```mtron
mtron> /a.repeat(code=>>>,emit=>true)
==>/a
==>/a/b
==>/a/x
==>/a/b/q
==>/a/b/c
==>/a/b/d
==>/a/x/y
==>/a/b/d/e
==>/a/x/y/z
==>/a/b/d/e/0
==>/a/b/d/e/1
==>/a/b/d/e/2
mtron> /a.repeat(code=>>>,until=>loop()?>2, emit=>true)
==>/a
==>/a/b
==>/a/x
==>/a/b/q
==>/a/b/c
==>/a/b/d
==>/a/x/y
==>/a/b/d/e
==>/a/x/y/z
mtron> /a.repeat(code=>>>,until=>loop()?>2, emit=>false)
==>/a/b/d/e
==>/a/x/y/z
```
## mtron language examples

```mtron
mtron> {1,1,1,2,2,3}.plus(2)
==>{3}3
==>{2}4
==>5
mtron> {1,1,1,2,2,3}.plus(2).sum()
==>22
mtron> {1,1,1,2,2,3}.plus(2).sum?int<=int{1,3}()
==>9
==>12
==>5
==>{-1}4
```
```mtron
mtron> {1,2,3}-<[?>2 => '${_} is greater 2', ?<=2 => '${_} is less than 2']>-
==>1=>'1 is less than 2'
==>2=>'2 is less than 2'
==>3=>'3 is greater 2'
```
```mtron
mtron> [-- type definitons --]
mtron> int::T[?>0]@nat
mtron> rec::T[?[name=>str::T,age=>nat::T]]@person
mtron> [-- value definitions --]
mtron> person::[name=>'marko',age=>29]
==>person::[name=>'marko',age=>29]
mtron> person::[name=>'unnamed',age=>-1]
==>fail::[obj does not match person::T
     [name=>'unnamed',age=>-1]
         X=>
     rec::T[?[
       name=>str::T,
       age=>nat::T]]@person
     --------------------------------------------------------------------
     [name=>(==>name)=>'unnamed'=>==>str::T,age=>(/=>age)=>-1=>X=>nat::T]]@/sys/fail/158
```
### docq query processor to access documentation

Any obj can have associated documentation of type `docs::T`. A good way to learn about an instruction is to resolve it
with a `?docq` query processor.

```mtron
mtron> select?docq
==>select?docq
mtron> where?docq
==>where?docq
mtron> group?docq
==>group?docq
mtron> as?rng=int&docq
==>as?rng=int&docq
mtron> as?dom=int&docq
==>as?dom=int&docq
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