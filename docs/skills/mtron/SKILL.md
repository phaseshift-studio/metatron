---
name: mtron
description: >
  understanding the language used to control the metatron
---

# mtron: the basics on a uri/obj graph

mtron is a functional, fluent, monadic language that manipulates the metatron environment. metatron **storage** is a uri
(uniform resource identifier) graph where any vertex can hold an associated obj (object). metatron **processing** is a
data-flow model aimed at graph traversal and obj manipulation.

Every denoted object can be reified, referred to, and manipulated -- a **reflective computing environment**.

**if it exists, it has a uri. if it has a uri, it can be accessed.**

## the three verbs: write, read, update

|   do   | mtron            | what it does                               |
|:------:|------------------|--------------------------------------------|
| write  | `/a -> 5`        | store `5` at address `/a`                  |
|  read  | `*/a`            | fetch (dereference) the obj stored at `/a` |
| update | `@/a >>= [d=>5]` | evolve the obj *in place* at `/a`          |

**`*` is a clone reference, `@` is an anchor reference.** `*/a` yields a copy -- edits to it never reach the address.
`@/a` returns the obj bound to `/a`, and
`>>=` (the `update` instruction) writes the change back to the address. An update delta is mtron: a plain value
replaces, a rec overlays, `+N` adds numerically,
`+[v]` promotes to a set, and `none` deletes a key. The full algebra is chapter 14 of the language reference -- and
`*update?docq`.

## space: storage

A system that supports the encoding of a uri/obj-graph is called a `space`. An example uri/obj-graph maintained by a
`space` responsible for the address pattern
`/a/#` is diagrammed below.

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

To construct the graph, denote uri vertices with path syntax, objs with obj syntax, and connect them with `->` (sugar'd
`ref`).

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

To retrieve stored objs, dereference their uris. The uri is the **reference**, the obj is the **referent**; moving from
one to the other is **dereferencing**
(also, **resolving**).

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

* [Full Language Reference](references/mtron-language-reference.md) -- types, operators, instruction sets, expression
  model.
* [Type System](references/type-system-mtron.md) -- vid/tid, nominal vs structural, pattern types.
* [Math Instruction Set](references/math_instset.md) -- `/m/math` constants, unit types, the `mathInstSet`.

**Spaces & data sources**

* [fsSpace](references/mtron-fsspace.md) -- file system as space; MIME, `?mimeq`, file I/O.
* [Connecting Data Sources](references/connecting-datasources.md) -- the pattern + route model for external sources;
  `!*` references.
* [dckrSpace](references/dckrspace.md) -- Docker: containers, images, volumes, compose as a space.
* [httpPage Fetching](references/http-page-fetching.md) -- HTTP pages, HTML parse trees, traversal.

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
