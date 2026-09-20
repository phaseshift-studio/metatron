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

* [mtron language reference](references/language-reference-mtron.md) -- the whole surface in 23 sections: mono/poly/call
  types, coefficients, arithmetic and strings, `*` dereference, `.as(type::T)` casting, map/filter/group, merge/split,
  `>>`/`<<`, the `|` barrier, `>>=` update, `!*`/`!@` auto-references, path ops, failure handling, and the expression
  evaluation model.
* [mtron type system](references/type-system-mtron.md) -- vid/tid, coefficients, the universal type, defining types,
  predicates (isa vs non-isa), nominal vs structural, refinement, pattern/generic types, casting, lowest common
  denominator.

**Spaces & data sources**

* [sys instruction set + fsSpace](references/sys-instset-mtron.md) -- one file, two docs: `/m/sys` (the guarded `bash`
  and its security modulators, `sleep`/`stdout`/`stdin`, mounted state) and the full `fsspace::T` tour (configuration,
  MIME detection and the mime-to-tid map, `?mimeq` typed strings, file read/write, binary and structural reads,
  pattern-based access, `lineq` line-level editing).
* [math instruction set](references/math-instset-mtron.md) -- `/m/math`: the unit types (`time`, `datasize`,
  `currency`), the `datetime` uri and its construction/arithmetic, `normalize` and the trig/rounding instructions, and
  the `pi`/`e` constants.
* [web instruction set](references/web-instset-mtron.md) -- `/m/web`: the protocol surfaces and MIME document types,
  `route::T` mount tables and the literal-prefix rule, templated route values, mounting an obj, and what is not
  available yet.
* [tble instruction set](references/tble-instset-mtron.md) -- `/m/tble` and `tblespace::T`: a JDBC database as a space
  -- tables that appear from the first rec write, typed rows, the SQL rewrite family that pushes reads down into the
  backend, the key/value fall-through, `!*` foreign keys, and native `sql()`.
* [ui instruction set](references/ui-instset-mtron.md) -- `/m/mach/ui`: a widget as a rec whose state is its own
  map, `as?str<=widget(str::T)` to render one inline anywhere a str fits, the `style::T` keys (border, width,
  anchor, `top`/`left`, the viewport), the anchor arithmetic and the pointer vocabulary (chevron to move, corner
  marker to reshape), and the three insts `display`/`nano`/`less`.
* [dckrSpace](references/dckrspace-mtron.md) -- Docker as a space: containers, images, volumes, networks, compose,
  remote hosts, and an end-to-end SQLite container + tbleSpace walkthrough.
* [Connecting Data Sources](references/connecting-datasources.md) -- the pattern + route model, discovering a space's
  prefixes from `*/sys/space/...`, `!*` lazy references, validation, and common failures.

**Protocol & services**

* [MCP Server Architecture](references/mcp-server-architecture.md) -- building MCP servers in mtron: `mcp_wsHandler` /
  `mcp_mtron_wsHandler`, websocket routing, tool registration, `SpaceChatMemoryStore`, and the agent memory flow.
* [MCP Server Notifications](references/mcp-server-notifications.md) -- server-to-client push via a `?subq` subscription
  on a WebSocket space; boot integration and the development pitfalls.
* [DSH Memory Bus](references/dsh-mtron.md) -- the first inter-harness memory adapter: a DSH zstd JSONL transcript
  becomes typed message recs, loaded and written into a native metatron agent memory tree.

**Practice**

* [Unsloth Training](references/unsloth-training-mtron.md) -- fine-tuning an LLM on mtron: dataset extraction via
  `@Training`, model selection, training, GGUF export, and Ollama/HuggingFace deployment.

**Not yet split out** (deep dives this entry doc points at, but which have no reference doc of their own):

* `qprocs` -- the `?q` family as a whole: `?docq` (each instruction's own doc), `?incq` (auto-increment), `?hasq`,
  `?statq` (address-level read/write heat), `?subq` (pubsub), `?asq` (an `as` instruction read as a property-graph
  edge: label `as`, outV its dom, inV its rng, and `?asq` the kinds of as-graph relation that edge participates in --
  `*as?nat<=int&asq`, narrowed by `?asq=[kind,...]`), and the fact that a qproc's data space is independent of
  the obj's. Only two corners are written down today: `?subq` in
  [MCP Server Notifications](references/mcp-server-notifications.md), and `?mimeq`/`?lineq` in the fsSpace half of
  [sys instruction set](references/sys-instset-mtron.md).
* the rest of the `>>=` update algebra -- `+[v]` set promotion and the HTTP PATCH door. Overlay merge, `+N`, `none`
  delete, and anchor-vs-clone *are* covered (language reference section 14); the door is specified in
  `docs/design/memory-server-architecture.md`.
* `httpPage` fetching -- the client side of the web carrier: `http://` dereference, HTML parse trees, traversal. The web
  doc's *see also* still points at a sibling `web_instset_mtron.md`, but that file is gone; the only surviving copy is
  a stale build artifact under `docs/website/skills/mtron/references/http-page-fetching.md`.
