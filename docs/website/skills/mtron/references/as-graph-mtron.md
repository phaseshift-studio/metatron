---
name: as-graph
description: |
  The `as`-graph: every `as` instruction is an edge — label `as`, outV its dom, inV its rng — and `?asq` reads an
  edge's labels within `duplicate`, `ambiguous`, `incomparable`, `coupling`, `isochain`, `retract`). The graph reads
  like a space: `*as` is the whole graph, `*as?rng<=dom` a single edge, `*as?rng<=` the in-neighborhood,
  `*as?<=dom` the out-neighborhood.
  TRIGGER: When casting or parsing an obj through `as(type::T)`, when a cast seems to do nothing, when adding an `as`
  edge (a dom → rng morphism), when asking whether two casts to the same rng can collide, when traversing the graph
  (whole-graph, single-edge, or neighborhood reads), or when reading an edge with `?asq`.
---

# the as-graph (`?asq`)

`as` is a single instruction family whose declared casts say which obj types may be cast to which:
`as?int<=bool` maps a `bool` to an `int`. Read that family as a **property graph** and the casts stop being a
list:

* the **label** is `as` — every cast is the same instruction, so the family is one edge label;
* the **outV** is the edge's dom — what goes in;
* the **inV** is the edge's rng — what comes out;
* the **edge property map** is `?asq`: the kinds of relation that edge takes part in.

The organizing idea is that **an edge is named by its endpoints, not by an address**. `*as?int<=str&asq` asks
about the edge *str → int*, and the map is *derived from the endpoints' place in the graph* — opposing edges,
contested siblings, reachability — not read from any one cast. An undeclared pair reads as noobj: the graph only
speaks for edges that exist.

## reading the graph

`inst?rng<=dom` reads rng first, then `<=`, then dom — so `as?int<=str` is the edge **str → int**. Because the
graph lives in space, every read shape works on it:

* **the whole graph** — `*as` is the edge set, every declared morphism at once.
* **one edge** — the endpoint-pair query:

```mtron
mtron> *as?int<=str
==>as?rng=int&dom=str(int::T){<j>}
```
* **one edge with its map** — `?asq` hands the edge back carrying its kind labels:

```mtron
mtron> *as?int<=str&asq
==>fail::[inst apply failure: no asq query processor attached to /m/space/memspace [+/#] (at /m/inst/from@0)]@/sys/fail/578
```
* **the in-neighborhood** — every edge *into* an rng (`as?int<=` reads rng `int`, any dom):

```mtron
mtron> *as?int<=
==>as?rng=int&dom=bool(int::T){<j>}
==>as?rng=int&dom=real(int::T){<j>}
==>as?rng=int&dom=str(int::T){<j>}
==>as?rng=int&dom=uri(int::T){<j>}
==>as?rng=int&dom=datetime(int::T){<j>}
```
* **the out-neighborhood** — every edge *out of* a dom (`as?<=str` reads dom `str`, any rng):

```mtron
mtron> *as?<=str
==>as?rng=bytes&dom=str(bytes::T){<j>}
==>as?rng=bool&dom=str(bool::T){<j>}
==>as?rng=int&dom=str(int::T){<j>}
==>as?rng=real&dom=str(real::T){<j>}
==>as?rng=uri&dom=str(uri::T){<j>}
==>as?rng=rec&dom=str(rec::T){<j>}
==>as?rng=datetime&dom=str(uri::T[/m/inst/pred?rng=#{?}&dom=#{?}(<#{*}>::T){<j>}]@datetime){<j>}
==>as?rng=java&dom=str(str::T[/m/inst/pred?rng=#{?}&dom=#{?}(<#{*}>::T){<j>}]@java){<j>}
==>as?rng=xsv&dom=str(str::T[/m/inst/pred?rng=#{?}&dom=#{?}(<#{*}>::T){<j>}]@xsv){<j>}
==>as?rng=csv&dom=str(xsv::T[/m/inst/pred?rng=#{?}&dom=#{?}(<#{*}>::T){<j>}]@csv){<j>}
==>as?rng=noobj{0}&dom=A(){<j>}
==>as?rng=str&dom=A(str::T){<j>}
==>as?rng=B&dom=A(<#>::T){<j>}
==>as?rng=B&dom=A(B::T){<j>}
```
* **a neighborhood with its maps** — `?asq` on the set:

```mtron
mtron> *as?int<=&asq
==>fail::[inst apply failure: no asq query processor attached to /m/space/memspace [+/#] (at /m/inst/from@0)]@/sys/fail/582
```
```mtron
mtron> *as?<=str&asq
==>fail::[inst apply failure: no asq query processor attached to /m/space/memspace [+/#] (at /m/inst/from@0)]@/sys/fail/586
```
Two things the neighborhood reads reveal. First, **endpoint queries resolve through refinement** — there is no edge
declared for `nat`, yet `*as?uri<=nat` walks `nat`'s parent and returns the `int → uri` edge:

```mtron
mtron> *as?uri<=nat
==>as?rng=uri&dom=int(uri::T){<j>}
```
Second, **the generic casts appear in every neighborhood**. The tag casts (`as?rng=B&dom=A`, `as?rng=str&dom=A`,
`as?rng=noobj{0}&dom=A`) have generic endpoints, so `*as?<=str` returns them alongside str's concrete out-edges —
their maps say `duplicate` (the two `B←A` casts do the same work) and `incomparable` (a tag is a conversion, never
a re-tag).

A read never writes back: what comes back is a plain uri whose `?asq=` says what the edge is. The map is derived,
not stored — it is recomputed from the graph and dropped when a new `as` cast is written, so a live graph never
hands out a stale answer.

## the edge kinds

| kind           | the relation between the edge and the graph                                               | what it means for you                                                                                          |
|----------------|-------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------|
| `duplicate`    | another `as` cast maps this edge's outV to its inV                                        | a redundant edge — two casts do the same work; drop one                                                  |
| `ambiguous`    | this edge's outV (or inV) is contested by an incomparable sibling that *overlaps* it      | a future input could match both with no most-specific winner: dispatch is not deterministic, so refine one dom |
| `incomparable` | this edge's outV (or inV) is contested by a sibling that is incomparable and **disjoint** | benign — no input matches both, so dispatch stays total; no action                                             |
| `coupling`     | an opposing edge exists: this edge's inV casts back to its outV                           | a candidate isomorphism/retraction — the round trip is available                                               |
| `isochain`     | no direct opposing edge, but the endpoints are linked by a chain of them                  | the endpoints sit in the reversible core of the graph: you can navigate between them                           |
| `retract`      | the endpoints are mutually reachable (a round trip exists, not a pair)                    | the round trip is an *idempotent*: the cast lands in a subobject, so it is lossy rather than reversible        |

The three relation kinds are exclusive and reported at the tightest one — a coupling is not also reported as a
retract, though it certainly is one. The contest kinds are what to watch when adding a cast: `incomparable` is
the graph telling you the new cast is safe, `ambiguous` is it telling you an input can reach two instructions.

## one edge per kind

### a coupling — `metric` and `imperial`

Metric and imperial each cast to the other, so each edge is a candidate isomorphism:

```mtron
mtron> *as?metric<=imperial&asq
==>fail::[inst apply failure: no asq query processor attached to /m/space/memspace [+/#] (at /m/inst/from@0)]@/sys/fail/590
```
### an incomparable sibling — and a map that carries two kinds

`json` casts to `yaml`, and `json` also casts to `mcp_client`. Those two rngs are incomparable with each other and
disjoint, so no input reaches both — dispatch stays total and the map says exactly that:

```mtron
mtron> *as?yaml<=json&asq
==>fail::[inst apply failure: no asq query processor attached to /m/space/memspace [+/#] (at /m/inst/from@0)]@/sys/fail/594
```
`int` is the busier example: it casts to `bool`, `bytes`, `real`, `str` and `uri`. Every one of those edges also has
an opposing edge, so the map reports the contest *and* the coupling — one kind for each relation the edge takes part
in:

```mtron
mtron> *as?real<=int&asq
==>fail::[inst apply failure: no asq query processor attached to /m/space/memspace [+/#] (at /m/inst/from@0)]@/sys/fail/598
```
### an isochain — `bytes` and `int`

No single cast maps `bytes` to `int` and back, yet they sit in the *reversible core* of the graph: a chain of
opposing pairs links them, so a round trip exists even though no direct one does. The map names that shape
`isochain`:

```mtron
mtron> *as?bytes<=int&asq
==>fail::[inst apply failure: no asq query processor attached to /m/space/memspace [+/#] (at /m/inst/from@0)]@/sys/fail/602
```
### a retract — the self-loop

An edge whose endpoints are the same type is the identity, and the identity is idempotent, so the edge is a retract
of itself (not a coupling — that needs two types). Its outV is still contested by int's other casts, which is why
the map carries both kinds:

```mtron
mtron> *as?int<=int&asq
==>fail::[inst apply failure: no asq query processor attached to /m/space/memspace [+/#] (at /m/inst/from@0)]@/sys/fail/606
```
## narrowing a map

`?asq=[kind,…]` asks for the edge's map *as far as those kinds are concerned*: the answer is the intersection, and
when the intersection is empty there is no such edge property to read, so the read yields nothing at all.

```mtron
mtron> *as?str<=int&asq=[coupling]
==>fail::[inst apply failure: no asq query processor attached to /m/space/memspace [+/#] (at /m/inst/from@0)]@/sys/fail/610
```
```mtron
mtron> *as?str<=int&asq=[duplicate]
==>fail::[inst apply failure: no asq query processor attached to /m/space/memspace [+/#] (at /m/inst/from@0)]@/sys/fail/614
```
## declared casts, and the tag that no one declares

A declared cast *does work*: `as?rec<=json` parses a JSON document into a rec, `as?json<=rec` serializes one back.
The str-refinement document types under `/m/web/mime/+` are declared in pairs like that (`json`, `yaml`, `xml`,
`html`, `markdown`, `csv`, `xsv`).

A cast to a type nobody declares is still legal — it is a **tag**, and it is served by one fully generic cast whose
whole job is *"the lhs obj as the arg type"*:

```java
instC(AS_INST_TID.dom(A).rng(B), lst(ALL_TYPE), (lhs, inst) -> inst.arg(0).isType()
        ? lhs.as(inst.arg(0).asType())
        : fail(MTronException.of("%s is not a %s", lhs, inst.arg(0))))
```

`'{"a":1}'.as(json::T)` retags the text as JSON without touching it, and adding `.as(rec::T)` then hits
the declared parse cast:

```mtron
mtron> '{"a":1}'.as(json::T)                                 [-- a tag: the generic arg-type cast --]
==>json::'{"a":1}'
```
```mtron
mtron> '{"a":1}'.as(json::T).as(rec::T)                      [-- the tag, then the declared parse cast --]
==>[a=>1]
```
That division is worth knowing: **the tag is generic and structural, the conversion is declared per pair** -- which
is why `.as(html::T).as(rec::T)` parses HTML and `.as(rec::T).as(html::T)` serializes it, while a tag on its own
changes only what the obj claims to be.

## the implicit as-graph

Most casts are never written down. If `stockholmare` refines `human`, then a `stockholmare` *is* a `human` and the
cast is just dropping the constraint that distinguishes them — no edge needed. `AsQ.implicit()` manifests those
ancestor casts from the hierarchy of the types already in the graph, which is the set of edges the as-graph would
carry if every refinement were written out.

## caveats

* **an endpoint's identity is its address, not its base type.** `json::T` is a *str* refinement living at
  `/m/web/mime/json`; the cast that implements it names that address. A comparison that walks the tid's ancestry
  instead of the address will miss the cast — which is why an exact endpoint match is always admitted.
* **the relation analysis is nominal.** Predicates do not split an edge: `int -> nat` and `int -> int` differ by
  vertex name only.
* **`ambiguous` overlap is decided by graph reachability.** Two same-rng doms count as overlapping when they
  share a base path and are linked by a cross-over path in the undirected type graph — the implementation's
  proxy for "captures the same values" (the intent is `∃T` refining both).
* **the refinement check is nominal *plus coefficient*, and predicate-blind.** `refines(A, B)` is
  `A.testNominally(B) && A.c().within(B.c())`, and root/generic endpoints never count as incomparable — which is
  why the generic tag casts are contested only by concrete siblings. (Edge *identity* remains coefficient-blind:
  `{4}int -> int` and `int -> int` are one edge.)
* **a predicated variant of a named type shares its name's address.** Constructing `nat::T[is(gt(100))]` registers
  it at nat's own address, so it *replaces* nat rather than sitting beside it; give a distinct type its own base
  (`int::T[…]`) if it needs its own identity.
* **coefficients are not part of an edge's identity yet**, so `{4}int -> int` and `int -> int` are one edge today.
* **surfaces have no edges yet.** `as(mcp::T)` fails, which is why the projection chain is spelled
  `.as(skill::T).as(mcp_server::T)` — see [web instruction set](web-instset-mtron.md).

## see also

* [web instruction set](web-instset-mtron.md) — the MIME document types, their parse/serialize casts, and the
  projection chains that ride them.
* [mtron type system](type-system-mtron.md) — vid/tid, coefficients, refining predicates, nominal vs structural.