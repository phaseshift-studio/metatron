---
name: mtron
description: understanding the language used to control the metatron
---

# mtron: traversing the metatron graph

mtron is a functional, fluent, monadic language that manipulates the metatron environment.

To understand mtron, it's necessary to first understand how structures and processes are organized in metatron.

metatron's **structure** forms a [split graph](https://en.wikipedia.org/wiki/Split_graph) with two vertex sets:
`V = U + O`. The set `U` is the set of uris (**uniform resource identifies**) and they maintain an
intra-edge set defined solely by path uri **path adjacency**. The set `O` is the set of objs (objects) whose
intra-edge set denote **shared/coupled state**. Finally, there exists an **inter-edge** set linking vertices in `U` to
vertices in `O` by according to a **reference/referent** relationship.

**IMPORTANT**: the metatron uri/obj split graph will more conveniently be referred to as **the metatron graph**.

metatron's **process** is realized as a swarm of monadic traversers whose abstract path through the graph is defined by
a functional ringoid -- a collection of functions structured using * (serial compose) and + (parallel branch). The
functional language is called **mtron**.

## space: storage

A system that exposes a subset of the metatron graph is called a `space`. In the example below, a simple in-memory space
implementation is used to maintain a subset of the uri address space that matches `/a/#` (`#` is recursive wildcard).

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

To construct the graph, a path syntax is used to denote a unique uri address. The primitive `->` (sugar'd
`ref`) instruction writes the **rhs** (right-hand side) obj to the **lhs** (left-hand side) uri.

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

To retrieve stored objs, dereference their uris. The uri is the **reference**, the obj is the **referent** and the
process of moving from one to the other is called **dereferencing** (also known as **resolving**).

```mtron_pre
*/a
*/a/x
*/a/b
*/a/b/c
*/a/b/d
```

Of particular significance is the result of `*/a/b`: polys (`lst`, `rec`, `rel`)
maintain an internal uri scheme that interacts with the outer space's uri scheme. That interplay recurs throughout
mtron.

### uri categories

. **absolute**: a uri with a `/` prefix -- `/a/b`. . **relative**: with no `/` prefix -- `a/b`.

. **branch** : a uri with a `/` suffix -- `a/b/`. . **node** : with no `/` suffix -- `a/b`.

## graph crud cheat-sheet

|     do      | mtron sugar      | mtron inst                | what it does                                     |
|:-----------:|------------------|---------------------------|--------------------------------------------------|
|    write    | `a -> 5`         | `ref?A{?}<=uri(A{?}::T)`  | store `int::5` at address `a`                    |
|  read copy  | `*a`             | `from?A{?}<=#{?}(uri::T)` | clone (dereference) the obj stored at `a`        |
| read anchor | `@a`             | `at?A{?}<=#{?}(uri::T)`   | couple (main reference) to the obj stored at `a` |
|   update    | `@/a >>= [d=>5]` | `update?A{?}<=#(A{?}::T)` | update the obj (from/at) `a`                     |

`*` is a **clone reference**. `*a` copies the referent, where subsequent mutations do not affect the source obj.

```mtron_pre
a -> 5
*a + 6 
*a
```

`@` is an **anchor reference**. `@a` couples the referent, where edits propagate back to the source obj.

```mtron_pre
a -> 5
@a + 6
*a
```

### obj types

#### mono types

|    type    | examples                             |
|:----------:|--------------------------------------|
| `bool::T`  | `true` or `false`                    |
| `bytes::T` | `0xa5`, `0x[0-9a-f]`                 |
|  `int::T`  | `...,-2,-1,0,1,2,...`                |
| `real::T`  | `...,-1.012,0.0,1.134,2.377,...`     |
|  `str::T`  | `"a b"`, `'a b'`, `"""multi line"""` |
|  `uri::T`  | `mtron://host:8555/a/b/c?x=1&y=2`    |

#### poly types

| type  | examples              |
|:-----:|-----------------------|
| `rel` | `(k=>v)`              |
| `lst` | `[1,2,3,...]`         |
| `rec` | `[k1=>v1,k2=>v2,...]` |

#### call types

|  type  | examples                                           |
|:------:|----------------------------------------------------|
| `inst` | `inst?rng<=dom(arg0=>A::T,arg1=>B::T){ inst* }@op` |

Finally, within the base types, there is `noobj` which is a mono/poly/call.

#### space types

A `space::T` refines `rec::T`. Common spaces include:

|     type     | description                                                    |
|:------------:|----------------------------------------------------------------|
|  `memspace`  | in-memory trie data structure                                  |
| `httpspace`  | the web as a metatron space                                    |
|  `wsspace`   | web sockets as a space                                         |
|  `fsspace`   | file system with nested directories and symlinks as a space    |
| `mqttspace`  | mqtt broker with nested topics and topic references as a space |
| `tblespace`  | relational database with foreign key edges as a space          |
| `grphspace`  | graph database with native edges as a space                    |
| `dcmntspace` | document `$DBRef`/JSON nested edges as a space                 |
| `dckrspace`  | docker images, containers, volumes, networks, etc. as a space  |

## processing: the fluent chain

mtron is built on **chained/nested function composition** with attention to each function's domain, range, and argument
types. Any expression, desugar'd, is a fluent chain of nested instruction calls. Append `.explain()` to any expression
to see its unsugar'd form:

```mtron_pre
start(/a).rshift().rshift().rshift().explain()
/a.>>.>>.>>.explain()
```

Traversing the graph non-sugar'd vs sugar'd (`>>` = `rshift`):

```mtron_pre
start(/a).rshift()
start(/a).rshift().rshift()
start(/a).rshift().rshift().rshift()
/a.>>
/a.>>.>>
/a.>>.>>.>>
```

For a language to be universal it needs state, loop, and branch. State lives in space. Loop and branch are first-class,
as in this repeated traversal:

```mtron_pre
/a.repeat(code=>>>, emit=>true)
/a.repeat(code=>>>, until=>loop()?>2, emit=>false)
```

where `loop()` yields the current iteration count of the enclosing `repeat`.

## a taste: multisets, symmetry, types

**Coefficients** -- `{n}v` reads as "n copies of v"; they propagate through arithmetic.

```mtron_pre
{1,1,1,2,2,3}.plus(2)
{1,1,1,2,2,3}.plus(2).sum()
```

**Symmetry reduction** -- `>-` sums the coefficients of identical objs; a negative
coefficient is the interference:

```mtron_pre
{1,1,1,2,2,3}.plus(2).sum?int<=int{1,3}()
```

**Map, filter, projection, and reduce** (language reference section 8):

```mtron_pre
{1,2,3,4}.map(+2)
{1,2,3}.is(gt(1))
[a=>1,b=>2,c=>3]==[a=>+10]
{1,2,3,4}.reduce(|plus(0))
{1,2,3,4}.sum()             [-- shorthand for the above --]
{1,2,3,4}.prod()            [-- product instead of sum --]
```

**The barrier** -- look at the dom of `*reduce`: `reduce?rng=A&dom=A{*}(#{?}::T)`.
The `*` in `dom=A{*}` is a **barrier** that greedily aggregates the dom, and the rng
being `A` (i.e. `A{1}`) means the barrier is *reducing* -- it takes many to one.
`sum`, `prod`, and `reduce` are the same instruction in different clothing.

**Types and validation** -- define a type, validate a value against it (`.as(type::T)`
is structural validation during projection):

```mtron_pre
int::T[?>0]@nat
rec::T[?[name=>str::T, age=>nat::T]]@person
person::[name=>'marko', age=>29]
```

## docq: read the code you are about to run

Do not guess at instruction signatures. Every instruction ships with documentation attached via the `?docq` query
processor. **Read the documentation of the code you are about to execute.**

```mtron_pre
*plus?docq
*select?docq
*/a/b?docq
```

## references

The entry doc above is deliberately brief. These are the deep dives, keyed by task. Point: every one of them begins with
`?docq` of the things they cover -- start there before re-reading the prose.

**Language & types**

* [Full Language Reference](references/language-reference_mtron.md) -- types, operators, instruction sets, expression
  model.
* [Type System](references/type-system-mtron.md) -- vid/tid, nominal vs structural, pattern types.
* [Math Instruction Set](references/math_instset_mtron.md) -- `/m/math` constants, unit types, the `mathInstSet`.
* [System Instruction Set](references/sys-instset-mtron.md) -- `/m/sys` `bash`/`native`/`sleep`/`stdout`/`stdin`; the
  guarded shell.

**Spaces & data sources**

* [fsSpace](references/sys_instset_mtron.md) -- file system as space; MIME, `?mimeq`, file I/O.
* [Connecting Data Sources](references/connecting-datasources.md) -- the pattern + route model for external sources;
  `!*` references.
* [dckrSpace](references/dckrspace_mtron.md) -- Docker: containers, images, volumes, compose as a space.
* [httpPage Fetching](references/web_instset_mtron.md) -- HTTP pages, HTML parse trees, traversal.

**Protocol & services**

* [MCP Server Architecture](references/mcp-server-architecture.md) -- building MCP servers in mtron; tool registration.
* [MCP Server Notifications](references/mcp-server-notifications.md) -- server-to-client push via subq on a WebSocket
  space.
* [DSH Memory Bus](references/dsh-mtron.md) -- the first inter-harness memory adapter (DSH transcripts into a metatron
  agent tree).

**Practice**

* [Answering Questions](references/answer-questions.md) -- how to answer mtron/metatron questions and troubleshoot.
* [Unsloth Training](references/unsloth-training-mtron.md) -- fine-tuning an LLM on mtron; the training pipeline;
  `@Training` extraction.

**Coming soon** (the deep dives this entry doc points at but which are not yet split out):

* `update` -- the full `>>=` update algebra: overlay, `+N` numeric add, `+[v]` set promotion, `none` delete; `@` anchor
  vs `*` clone; the HTTP PATCH door.
* `qprocs` -- the `?q` family: `?docq`, `?incq` (auto-increment), `?subq` (pubsub), `?hasq`, `?statq` (address-level
  read/write heat), and how a qproc's data space is independent of the obj's.
