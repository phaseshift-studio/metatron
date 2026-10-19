# Dual-output parser — `Pair<Obj, Rec>` from one grammar

A hand-off brief. The scope is **stage 1 only**: the parser. Everything below states the invariants
that have already cost time, the design to build, and the acceptance gates.

---

## Mission

`mParser` currently has one builder: its grammar rules construct live mtron objs
(`start(1).plus(2)…`). Add a **second, independent output** so a single parse can produce:

```java
Pair<Obj, Rec>   // obj = what the compiler consumes; ast = introspectable syntax tree (a rec::T)
```

…with either half switchable off. The AST is the editor/analysis artifact: pure data, spans, no
compilation side effects. The obj half stays exactly what it is today.

## Non-negotiable invariants (learned the hard way — do not re-litigate)

1. **`mInstSetTest` is the gate.** Any grammar change must leave it at 665 tests, 0 failures.
   `mParserTest` plus the fault corpus are necessary but not sufficient: an over-broad chain rule
   once passed both and broke 247 gate tests; a coefficient tightening broke 34. Run the gate after
   every grammar edit.
2. **`;` is sugar for an `end()` Inst**, not a separator token. The expression preceding it consumes
   it. So a source's statement boundaries are `end()` instructions:
   `1.plus(2).plus(5); 244.plus(30)` parses to one chain
   `start(1).plus(2).plus(5).end().start(244).plus(30)` — verifiable by equality against that
   explicit form.
3. **`parse` must never throw.** Failures are `fail::T` objs carrying `mParser.ParseFault` (source,
   line, column, position), reachable by `mParser.faultOf(obj)`. `cInt.of(String)` throws on a
   malformed coefficient — it is the authority on coefficient bodies; guard it
   (`coefficientOrOne` exists).
4. **Do not touch `Compiler`, `Rewriter`, `Resolver`, `Typer`, `Processor`.** Stage 1 only.
   `Parser.apply` returns `Obj` (a code, a value, or a fail); `Compiler.apply` lifts with
   `Parser.Helper.toCode`.
5. **Do not change the boot runner.** `Machine.evaluate(File, Consumer<FileParseError>)` must keep
   interleaving parse→apply per statement (source-split, quote/comment-aware). Four `end()`-based
   variants raced past the console and broke boot; the reason is unmeasured. If you want to revisit
   it, first add a file trace in the console's mount path (`target/console-mount.log`) and diff the
   two runners — do not ship a variant on reasoning alone.
6. **`m_call_prefix`'s optional structure is load-bearing.** Two structural changes there each cost
   247 gate failures. Change it only with the gate as the oracle, one edit at a time.
7. Tests use `@ParameterizedTest` + `@CsvSource` with **`%` as the delimiter**, 10+ rows per table,
   CSV rows living with their method, and the body calling **one**
   `AbstractMetatronTest` static checker where one exists (`checkCodeParseApply`, `checkEquality`).
   No test-method-per-example.

## Current shape (read these before editing)

`src/main/java/studio/phaseshift/metatron/isa/m/parser/mParser.java` (~1,400 lines):

| Element | Notes |
|---|---|
| `public static Obj parse(String mtron)` | the one door; door-level type-coefficient check; loops over `step(...)`; returns `assemble(statements)` |
| `private record Step(Obj statement, int next, Obj fault)` + `private static Step step(Prepared, int position, Obj previous)` | the shared walk: parse one statement, adjacency rule, zero-progress probe |
| `public static final class Incremental { hasNext(); next(); fault(); }` + `incremental(String)` | the same walk as a cursor (currently unused by boot) |
| `private record Prepared(String original, String text, int[] offsets, int endOffset)` + `prepare(String)` | comment-stripped text + index map back to the source |
| `ParseFault extends MTronException` + `faultOf(Obj)` | positioned failure, structured |
| `assemble(List<Obj>)` | one statement → itself; several → a code |
| `labeled(parser, msg)` / `labeledCloser(parser, msg)` / `LABELS` / `CLOSERS` / `FURTHEST` | expectation labels; `FURTHEST` prefers a declared expectation over a closer at equal offset |
| `m_chain_element()` | inst (dot optional) · `.` branch `{…}` → `branch(...)` · `.` value → `map(value)` |
| `m_call_prefix`, `m_code`, `m_obj*`, `m_furi*`, `m_type`, `m_inst_b/c` | ordered-choice grammar; `opt()` swallows failures (no commit/cut yet) |

## The design to build

**One grammar, two builders.** Add an actions interface; every rule calls it instead of calling
`instB`/`rec`/`lst` directly.

```java
interface ParseActions {
    Obj inst(Span span, fURI tid, List<Obj> args);
    Obj value(Span span, Obj value);            // literals, uris, types
    Obj chain(Span span, List<Obj> parts);
    Obj branch(Span span, List<Obj> branches);
    Obj paren(Span span, Obj inner);
    Obj typeOverride(Span span, Obj dom, Obj rng, Obj declaredType);
}
```

- `ObjActions` — today's behaviour, byte-for-byte (this is the regression bar).
- `AstActions` — builds nested `rec::T` nodes.
- `PairActions` — delegates to both, returns `Pair<Obj, Rec>`; either delegate may be muted (that is
  the "flat to turn off a side", and it is how syntax-only parsing is expressed).

**Spans.** PetitParser's `Result` carries positions; add a `spanned(rule)` combinator that reports
`(start, end)` to the builder. **Record position pairs, not object identity** — memoized rules
replay their cached `Result` without re-running your action, so an identity table loses entries
exactly where the grammar backtracks.

**Node schema** — uniform core keys `kind`, `span`, plus one kind-specific child list. Kind names
come from `Tokens` vocabulary (`inst`, `chain`, `branch`, `int`, `real`, `str`, `bool`, `bytes`,
`lst`, `rec`, `rel`, `uri`, `type`, `paren`). For `1.plus(2).{mult(2),minus(20)}.plus(100)`
(39 chars, offsets 0-based, end-exclusive):

```
[ kind  => 'chain',
  span  => [start=>0, end=>39],
  parts => [
    [ kind=>'int',  span=>[start=>0,  end=>1],  text=>'1', value=>1 ],
    [ kind=>'inst', span=>[start=>2,  end=>9],  name=>plus,
      args=>[ [kind=>'int', span=>[start=>7,end=>8], text=>'2', value=>2] ] ],
    [ kind=>'branch', span=>[start=>10, end=>29],
      branches=>[
        [ kind=>'inst', span=>[start=>11,end=>18], name=>mult,
          args=>[ [kind=>'int', span=>[start=>16,end=>17], text=>'2', value=>2] ] ],
        [ kind=>'inst', span=>[start=>19,end=>28], name=>minus,
          args=>[ [kind=>'int', span=>[start=>25,end=>27], text=>'20', value=>20] ] ] ] ],
    [ kind=>'inst', span=>[start=>30, end=>39], name=>plus,
      args=>[ [kind=>'int', span=>[start=>35,end=>38], text=>'100', value=>100] ] ] ] ]
```

Rules: **the AST reflects source syntax, not desugar** — no `start`, no `end`, no synthesized
`branch` inst; a written `{…}` is a `branch` node; `text` only on leaves; a parenthesised expression
is a `paren` node; an inline type override (`to?A{5}<=A{5}(x)`) is a `typeOverride` node carrying
dom/rng as written. Trivia (comments/whitespace) is **out of scope for v1** — record it as a
follow-up, not a schema change.

**Ordering gotcha to fix while you are in there (do not fix by widening the grammar):**
`1.{plus(2),plus(4)}` fails; it needs `1..{…}` because `m_call_prefix` consumes the head dot and
`m_chain_element`'s branch alternative requires the dot itself. The fix is a "first element after
the head dot" variant that accepts a branch without a second dot. Mid-chain (`.plus(2).{…}`)
already works.

## Phases and acceptance

1. **Seam, no behaviour change.** Introduce `ParseActions`, `Span`, and thread an `ObjActions`
   through every rule. `parse(String)` behaviour must be identical: `mParserTest`, corpus,
   `mtronParserTest`, and the gate all green; additionally add an equivalence test that parses a
   20-row source table through the actions path and compares objs.
2. **Spans.** Add `spanned(...)`; assert on a handful of rows that `(start,end)` slices of the
   source equal the node's `text` where present.
3. **`AstActions`.** Implement the schema above for: literals, uris/types, `inst` calls, chains,
   branches, parens, lst/rec/rel. Add `mParserAstTest` — `%`-delimited, 10+ rows, body a single
   static checker that takes the source and the expected AST rec (compare structurally), e.g.
   `checkAst(LOG, source, expectedAst)`. Cover at minimum: the example above, `1.445` (a real, one
   literal node), `[a=>1,b=>2]`, `lst[1,2,3]`, `1.plus(2)`, `a.x(); y()` (two statements — decide and
   document how the top-level list is shaped).
4. **`PairActions`.** Public entry: `Pair<Obj, Rec> parsePair(String source)` (or the name the owner
   prefers). A row-level test that `pair.obj()` equals `parse(source)` for every corpus source.
5. **Purity proof.** With only `AstActions` active, parsing the boot file
   `.metatron/skills/drstynx/assets/drstynx.boot.mtron` must produce an AST and **mount nothing** —
   no console, no spaces, no exceptions. Assert this (the obj half is null; the parse returns an AST
   of the expected shape). This is the property `metatron-lsp` depends on.

## Verification

```bash
bin/metatron-docker build test -Dtest=mInstSetTest,mParserTest,mParserFaultCorpusTest,mtronParserTest
bin/metatron-docker build test -Dtest=mParserAstTest
HOME=/workspace timeout 25 bin/drstynx 2>&1 | sed 's/\x1b\[[0-9;]*m//g' | grep -oE 'processed boot input|successfully booted'
# expect: no output (blocked in boot evaluation = console up). Never ship a change that prints
# 'successfully booted' here.
```

Build in the docker loop, not by hand. `Tests run: 0` is not a pass.

## Report back with

- The final API (`parse`, `parsePair`, mode selection) and the node schema, as shipped.
- Test counts for each suite and the gate.
- Every place the grammar had to change, with the gate result for each.
- What you did **not** do: boot runner, message tie-breaking (`opt` swallows failures — needs
  commit/cut), the mid-chain head-dot fix if you left it, trivia.

## Caveat on this brief

It encodes one agent's understanding of the current state, not a guarantee. If the code disagrees
with it, **trust the code and the gate**, and say so in your report.
