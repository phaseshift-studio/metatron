# Lst compression: a value's run kept as a span (`[3,3,3,3,3]` is `[3{5}]`)

**Status:** design note, written 2026-11-17 on main `2ed234151`. Anything marked **observed** was checked that day, by
a code read, a probe, a test row or Marko's console transcript. Anything marked **proposed** is not built.
**Grew out of:** the span and point coefficient work of 2026-11-17 (main `8e1d6185d`, `f3f6ad76f`). There a lst literal
lays a value's span, so `[3{2}]` is `[3,3]`, and an argument list lays it too: `1.xxx(2{2})` is `1.xxx(2,2)`. Marko's
answer is to keep the compressed form instead.
**See also:** `docs/articles/span-and-point-coefficient-algebra.md` (a point is mass at an address, a span is extent
along an axis), and the Hindsight documents *Span rulings 2026-11-17 (part 2)* and *Marko 2026-11-17: extra arguments
are fine (open world), fewer are not*.

---

## 0. The idea

Marko, 2026-11-17:

```
mtron> [3{5}]
==>[3,3,3,3,3]
```

- "The purpose of [3{5}] is compression. While it is true that [3{5}] is [3,3,3,3,3], I would prefer to keep it encoded
  as [3{5}]."
- "Moreover, should [3,3,3,3,3] be typed, like point coefficients in a stream, it would be good to have it encoded as
  [3{5}]."

So the following is the goal:

```
mtron> [3,3,3,3,3]
==>[3{5}]
```

A stream and a lst compress the same way, each along what it has:

- A **stream** has no axis and no order. It bulks equal objs, wherever they are, into a **point**: copies at one
  address. `{3,3,3}` is `{3}3`.
- A **lst** is an axis. It may only merge **adjacent** equal values, and it merges them into a **span**: a run of
  indices. `[3,3,3,3,3]` is `[3{5}]`.

Either way the encoding changes and the obj does not. Every spelling of one lst is that lst:
`[3{2},3,3{2}]` = `[3{5}]` = `[3,3,3,3{2}]`.

## 1. Where it stands today (observed)

- **The lst literal lays a value's span.** The lst parser builds `new MLst(Lst.Helper.laid(...))`
  (`mParser.java:299`, `Lst.java:324`), so `[3{5}]` parses to `[3,3,3,3,3]`. Compression is the reverse of this step.
- **An argument list lays it too.** `mParser.laidArgs` (`mParser.java:448`) does it for every instruction except
  `start`, whose argument is the stream it starts: `start(2{3})` keeps its curled span.
- **A lst is its Java list.** `MLst` keeps its entries in a plain `List<Obj>`. `Obj.lstValue()` (`Obj.java:831`) hands
  out that list itself, `jvm()`, and `MLst.clone` wraps a `mutableList`, so callers can mutate a lst in place.
- **`at()`, `atDirect()` and `has()` index the raw list.** `Lst.atDirect` goes to `Lst.Helper.atToggle`
  (`Lst.java:123`). `has()` compares against `jvm().size()`, and the indexed write `at(key, value, op)` calls `set` or
  `remove` on a copy of `lstValue()`.
- **The lst tests already read runs.** `Lst.Helper.testIndices` (`Lst.java:349`) lays both sides before it compares
  index by index. `Lst.Helper.testRuns` (`Lst.java:395`) is the bracket reading, and it walks runs.
- **Who reads `lstValue()`.** In `src/main/java`:

| pattern                                                         | hits |
|-----------------------------------------------------------------|-----:|
| `lstValue().get(`                                               |   17 |
| `lstValue().size()`                                             |   19 |
| `lstValue().stream()`                                           |   44 |
| for-each over `.lstValue()`                                     |   17 |
| `lstValue().isEmpty()`                                          |    3 |
| `lstValue().getFirst()` / `getLast()`                           |    1 |
| `lstValue().iterator()`                                         |    1 |
| in-place `lstValue().add(` / `.set(` / `.remove(`               |   16 |
| in-place `jvm().add(` (not every one is a lst)                  |    5 |
| files that call `lstValue()`                                    |   62 |

## 2. Code areas to consider

Each note is followed by Marko's comment on it, 2026-11-17, verbatim. A **follow-up** is a later remark of mine.

### 2.1 Argument lists are lsts

`Inst.arg(i)` (`Inst.java:222`) and the arity check `Inst.Helper.argsFillSlots` (`Inst.java:1019`) both read
`args().lstValue()`. A compressed `branch(6,6)` would be one argument, `6{2}`, and a two-slot call such as `band(2,2)`
would fail resolution for a missing slot. `start(2{3})` has to keep its curled span. Signatures change too: the
`args=` component of a resolved tid would read `[{2}/m/int]` instead of `[/m/int,/m/int]`, so lookups keyed on the
full tid would miss.

**Marko:** "args().lstValue() should be args().count()."

### 2.2 What compresses

*Revised 2026-11-18.* This note first said only values compress, because an instruction's span was read as its
composition power and a type's as a run of its filter (P·P = P), one slot either way. Marko ruled that a span on an
axis is extension for every obj ("an obj that performs a computation" and "an obj that is used in a computation"
alike), and that composition is the reading with no axis. So `[{2}plus(1)]` is `[plus(1),plus(1)]` and `[{2}int::T]`
is `[int::T,int::T]`, and both compress: a lst of instructions or types runs the way a lst of values does. Each
writes its span on its own literal's side (`3{2}`, `{2}int::T`, `{2}plus(1)`). Compression applies to the entries
`Lst.Helper.laid` lays: values, types and instructions with an exact span. A range (`{*}int::T`) never lays, so it
never comes out of a compressed run either. The postfix slot is no span on a call or a type: `plus(1){2}` is a body,
and `int::T{2}` does not parse.

**Marko:** "branch(6,6) already collapses:"

```
mtron> branch(6,6)
==>{2}6
mtron> branch(6,6).explain()>>format
==>"""
    op   dom      rng        args    f    desc      c_dom  c_rng
    map  A{?}::T  int{*}::T  [{2}6]  <j>  standard  {?}    {*}
   """
```

**Follow-up:** the rewriter already compresses a branch's like members into a point. `branch_collapse` sums equal
members into `branch({2}6)`, and `branch_inline` makes a one-member branch a `map`, whose argument lst is `[{2}6]`.

### 2.3 Equal is not identical: addressed values

Values read from a space carry their addresses as vids. Compressing `[x@/a, x@/b]` into `[x{2}]` would lose an
address, so two entries form a run only if neither is addressed.

**Marko:** "ah. addressed slots can not be span encoded. which is how point compression works today."

```
mtron> {1,1@a}
==>1
==>1@a
```

### 2.4 Points and noobj

Adjacent entries form a run only if both the value and the point match: `[{2}3,{2}3]` is `[{2}3{2}]`, but `[{2}3,3]`
stays as it is. An empty index is real: `[noobj,3]` has two indices, and `testIndices` reads a mass of zero there.
A span on noobj is fragile, because a zero point tends to collapse to plain noobj wherever it appears, so
`[noobj{2}]` could lose its run. noobj entries do not compress.

**Marko:** "Yes."

### 2.5 Poly entries

`[[1,2],[1,2]]` would compress to `[[1,2]{2}]`. That needs a span on a poly value, and `[1,2]{2}` does not parse today
(observed: a parse error). The printer and the parser have to round-trip it before polys can compress.

**Marko:** "Would need to fix the parser."

### 2.6 Canonical form and equality

`[3{5}]` and `[3,3,3,3,3]` must be equal and hash equal. Compressing only in the literal parser, where laying happens
now, misses lsts built in Java (`lst(jnt(3), jnt(3))`) and lsts mutated in place: 16 `lstValue().add/set/remove` calls
and up to 5 `jvm().add` calls in `src/main/java` (§1). So either equality compares a canonical form, or every
construction and mutation path normalizes.

**Marko:** "Good realization. Yea, we would need a hash algorithm that maps all potential encodings to the same int.
[3{2},3,3{2}] = [3{5}] = [3,3,3,3{2}] = ...."

**Follow-up (proposed):** two ways to get that hash.

- **Canonical runs.** Merge adjacent equal, unaddressed values into maximal runs, summing their spans and dropping zero
  spans, then hash the run list and compare run lists for equality. The maximal-run form of a lst is unique, so every
  encoding maps to the same int.
- **A closed-form rolling hash.** Hash the decoded sequence without building it: a run of k copies of x contributes a
  geometric series in the hash base. This avoids allocating a canonical list.

### 2.7 Index alignment

Several algorithms walk two lsts position by position:

- select: `Poly.Helper.selectLstLstRecursionRaw` (`Poly.java:213`) reads `at(jnt(i))`, but bounds its zip by
  `lstValue().size()`;
- the lst slot-wise product, `[x,y]·[a,b]`;
- DataPath navigation into a lst, such as `lst/2`;
- intra-poly references by index, such as `<../2>`;
- `testIndices`. It lays the runs out first, so `[3{1000000}]` would allocate a million entries and give the
  compression back.

When a run leaves the axis it must come out right too. `[3,3,3,3,3]>-` is `{5}3` today, and `Lst.elements()`
(`Lst.java:108`) streams the raw entries, so `[3{5}]>-` would emit `3{5}` (see §4).

**Marko:** "This is where using at() and atDirect() is crucial and should be used throughout the codebase but various
"laziness" reasons have led to the proliferation of lstValue().xxx()."

**Follow-up:** `at()` and `atDirect()` are themselves written over the raw list (§1). So moving every
`lstValue().xxx()` call onto them gives the run arithmetic one home: index to run, and the offset within it.

### 2.8 External boundaries

These all expect plain arrays:

- JSON (`json::T`), BSON document stores, SQL and TinkerPop list properties;
- MCP tool results and LLM prompts;
- the web panels (`mtron-obj-panels.js`) and SchemaSpace inference.

Each would need to decode on the way out and normalize on the way in.

**Marko:** "A Lst.compress() and Lst.uncompress() would need to exist for use at transport boundaries."

### 2.9 Typing

`[3,3,3,3,3].type()` would be `lst[int{5}]`, which is five int slots: a bracket reads its entries as values are read
(Marko, 2026-11-18), and on an axis a span is extension.

**Marko:** "That will work."

```
mtron> lst[int{5}]::[3,3,3,3,3].type()
==>lst[int{5}]::T
```

### 2.10 Expected outputs

Every test row and rendered doc that prints adjacent equal values would print them compressed: `[1,1]` would print
`[1{2}]`, including LstTest rows written on 2026-11-17.

**Marko:** "In theory, you will be able to write in compressed or uncompressed form without any changes in functional
behavior. so the tests wouldn't need updating."

**Follow-up:** that holds for the rows that compare objs. `checkCodeParseApply` parses the expected side, so `[1,1]`
and `[1{2}]` compare equal. Rows that compare printed text, such as `ObjmtronSerializerPurityTest`, and the rendered
`mtron_pre` outputs of the docs would show the compressed spelling.

## 3. A representation that limits the change (proposed)

Keep the runs as `MLst`'s internal representation, and have `lstValue()` hand out a decoded, read-only view:

- an `AbstractList<Obj>` whose `size()` is the sum of the runs' spans;
- whose `get(i)` binary-searches the cumulative spans and answers the run's value without its span;
- whose iterator walks the runs and repeats each value span times.

The roughly 100 read sites in §1 would then keep working unchanged. Only the printer, the typer, `Lst.compress()` /
`Lst.uncompress()` and the lst tests would read the runs. The cost moves elsewhere:

- the 16+ in-place mutations would need a builder, since the view is read-only;
- an argument list (§2.1) would read as laid, which is what `laidArgs` does already, except for `start(2{3})`.

## 4. Open

- **The view or compression everywhere** (§3).
- **The hash** (§2.6): canonical runs, or a closed-form rolling hash.
- **The parser** for spans on poly values (§2.5).
- **Argument lists** (§2.1): `args().count()` for arity, and how `start` keeps its curled span.
- **A run leaving the axis.** Should `[3{5}]>-` be `{5}3`, a point (copies at one address, as `[3,3,3,3,3]>-` is
  today), or `3{5}`, a span curled in the stream (as Marko reads `[1,2]{2}` there)? If compressed and uncompressed
  lsts are to behave the same (§2.10), both spellings must split to the same obj.
