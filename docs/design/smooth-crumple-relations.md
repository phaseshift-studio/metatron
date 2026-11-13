# Paths as values: `smooth` (`~T~`) and `crumple` (`~T~>`) over relation composition

**Status:** design note, written 2026-11-13 on main `83a05cf0c`, then updated for `5f374b0f5` and `f4800f3e9`. Anything
marked **observed** was checked on that day, by a test row, a live probe or a code read, with the commit given. Anything
marked **proposed** is not built. Slice 1, the parser layer, starts next in worktree `friendly-jang-f35798`.
**Grew out of:**
- the join-propagating rel product (main `1ad7f9963`);
- Marko's goal: "I would love to get rid of out()/in() such that out/in data is fetched based on => relation
  composition";
- his reading of what came out of it, quoted in §0.

**See also:** `docs/articles/parametric-identities-in-ring-theory.md` (the rel ring), the Hindsight initiative
*smooth/crumple path DSL: ~T~ and ~T~> over edge types*, and the Hindsight page *Opt-in path tracking — ?path code flag*
(the monad-stamping `path()` this is meant to succeed).

---

## 0. Goal

Make a traversal's history a **value** that the expression builds, instead of **metadata stamped on the monad**.

Marko's reading of the relation work, 2026-11-13:
- "What you are doing is you are computing with paths. Your 'monad' is a path that you are expanding on the right
  handside, while selectively contracting on the left handside."
- "Your => => are the way in which you are maintaining a linked structure over an expression."
- "You manifesting the 'type-oriented' traversal as a 'value-oriented' chain. It's very schema => instance style
  projection."
- "While we do have path() in metatron, I'm not a fan of metadata on monads/traversers (sorta been done). What you are
  doing here is a bit of a next generation of path()."

The syntax has to stay readable, not "assembly-ish". Marko's direction is two instructions with sugar the eye can
read:

```
g:V/+.~knows~.~created~>.>>name
```

- `~knows~` is `smooth(knows)`. It crosses `knows` edges and saves nothing.
- `~created~>` is `crumple(created)`. It crosses `created` edges and saves the element it reaches as a point of the
  path.
- `.>>name` is an ordinary instruction in the run that follows that point.

What has to work in the end:
1. `out()` and `in()` retire. Typed edges, composed as relations, replace them.
2. The user chooses which history is kept, with nothing stored on the monad.
3. Path history works with the algebra. It bulks by value, obeys the ring laws, and travels with the value across
   peers.

---

## 1. What is built *(observed)*

### 1.1 The join-propagating product (main `1ad7f9963`)

`Rel.mult` reads an arrow as the stream function `is(dom)·rng`: it keeps what matches the domain, then applies the range.

- **Value join.** `(a=>b)·(c=>d)` tests the join `b` against `c` and applies `d` to it. The result is `a=>d(b)`, one
  arrow for each object `d` yields, or noobj when `b` fails.
- **Value ranges.** A value range is a constant function, so arrows between values compose as they always have.
- **Code join.** When `b` is code, the step is deferred as code: `a=>b.-<(id()=>id()).mult((c=>d)).rng()`.
- **Return type.** `mult` returns an `Obj`, because a range that fans out yields several arrows.

RelTest `testRelCompositionPropagatesTheJoin` pins these rows:

| row | result |
|---|---|
| `(1=>2) * (2=>3)` | `1=>3` |
| `(a=>2) * (?int::T => plus(1))` | `a=>3` (was `a=>plus(1)`) |
| `(a=>2) * (?>9 => mult(10))` | noobj |
| `(marko=>josh) * (josh => id())` | `marko=>josh` (a partial identity) |
| `(a=>2).mult((?int::T => branch(plus(1),plus(2))))` | `{(a=>3),(a=>4)}` |
| `(1=>2).mult((?int::T => plus(1)).mult((3=>x)))` | `1=>x` (the deferred product regroups) |

`Lst.mult` adds one slot per object a product yields, so a lst of arrows multiplies as a relation.

### 1.2 Labelled adjacency (main `1ad7f9963`)

`VertexRec.at` reads the segment after `OUT`, `IN` or `BOTH` as the edge label, as the space's address walk does.
`*g:V/1.>>OUT/knows/IN/name` gives `{'vadas','josh'}`; before the change, `knows` was looked up as a key of each edge.
TinkerGrphSpaceTest `testRelationTraversals` pins it.

### 1.3 Traversal by composition (live server, main `1ad7f9963`)

```
g.V().out('created').values('name')

*g:V/+.split((id()=>id())) * (id() => >>OUT/created/IN) * (id() => >>name)
==>{josh=>'ripple', josh=>'lop', marko=>'lop', peter=>'lop'}          (each vertex prints in full)
*g:V/+.split((id()=>id())) * (id() => >>OUT/created/IN) * (id() => >>name).rng()
==>{'ripple',{3}'lop'}                                                (the trailing .rng() binds the whole product)

*g:V/+/ * (id() => >>OUT/created/IN) * (id() => >>name)               (x/ is x => *x: the address is the history)
==>{g:V/4=>'ripple',g:V/4=>'lop',g:V/1=>'lop',g:V/6=>'lop'}

g.V().as('a').out('created').as('b').select('a','b').by('name')
*g:V/+.split((>>name => id())) * (?person::T => >>OUT/created/IN) * (id() => >>name)
==>{'josh'=>'ripple','josh'=>'lop','marko'=>'lop','peter'=>'lop'}
```

- **The start arrow chooses the history.** `split((k => id()))` puts `k(v)` on the left. `*x/` is `x => *x`, which
  already exists.
- **Each later factor rewrites the right side only.**
- **A domain type is a schema guard.** `?person::T` drops the software vertices before any edge is walked.

### 1.4 Nested rels are paths (ginger, main `1ad7f9963`)

`=>` is right-associative: `1=>2=>3=>4=>5` is `1=>(2=>(3=>(4=>5)))`, and `.>>` drops the oldest element.

| expression | result |
|---|---|
| `(1=>2=>3) * (id() => >>)` | `1=>3` (main before `1ad7f9963`: `1=>rshift()`) |
| `(1=>2=>3=>4=>5)` with `.mult((id() => >>))` applied three times | `1=>5`, the same as `(1=>2)·(2=>3)·(3=>4)·(4=>5)` |
| `(1=>2).mult((id() => split((id()=>id())).mult((2=>3))))` | `1=>(2=>3)` (the join is kept) |

So a nested rel is a composite that keeps its joins, and `>>` performs one step of it. A path-keeping step gives
Gremlin's `path()`:

```
*/g/V/1.split((id()=>id())).mult((id() => >>OUT/knows/IN))
       .mult((id() => split((id()=>id())).mult((id() => >>OUT/created/IN))))
       .split((dom().>>name => rng().split((dom().>>name => rng().>>name))))
==>{'marko'=>('josh'=>'ripple'),'marko'=>('josh'=>'lop')}
```

The **limit**: a path grows at its innermost range, but the product acts on the outermost join.
- A third hop taken with the same step nests to the left inside the path: `marko=>((josh=>lop)=>'java')`.
- Uniform nesting, `'marko'=>('josh'=>('lop'=>'java'))`, needs the step wrapped as `(id() => mult((id() => step)))`,
  once for each earlier hop.

### 1.5 Expression folding against monad stamping (live server)

- **Stamping** is `StatefulMonad.next()` → `accruePath`. It appends `[inst, obj]` to `state.path` when the code's tid
  carries `?path`.
  - `1.plus(2).path()>>` gives `{{2}1,plus?int<=int(2){<j>},3}`.
  - Graph walks stamp nothing: `*g:V/1.>>OUT/created/IN.>>name.path()>>.count()` is `0`, and so is the `out(created)`
    form.
- **Folding** keeps the history in the value:
  - The user chooses it and it has a type (a name, a vertex, an address, or nothing).
  - Equal histories bulk, and the ring laws apply to it. Associativity means regrouping or fusing steps cannot change
    the start.
  - It is an ordinary value: it can be written to a space, sent to a peer, or used as a key.

### 1.6 What else the product buys (live server, main `1ad7f9963`)

| gain | probe | result |
|---|---|---|
| dispatch: a lst of guarded arrows is a match | `[(a=>2),(b=>'x')] * [(?int::T => plus(1)),(?str::T => id())]` | `[a=>3,b=>'x']` |
| path counting: a coefficient is a number of paths | `*g:V/+.split((>>name => id())) * (id() => >>OUT/+/IN) * (id() => >>IN/+/OUT) * (id() => >>name)` | `{3}'marko'=>'marko'`, `{2}'josh'=>'josh'`, each other pair once |
| alternation: product distributes over sum | `(1=>2).mult((2=>3).plus((2=>4)))` | `{1=>3,1=>4}` |
| the stream ring embeds in the rel ring | `f ↦ (id() => f)` preserves composition; `{1,2,3}` gives `{20,30,40}` for `plus(1)` then `mult(10)` | the article's NOTES question: arrows between instructions compose as guarded stream functions |

### 1.7 A span is an exponent (main `83a05cf0c`)

- **Exact spans.** A finite exact span is unrolled by the parser (`mParser.runs`): `{2}out()` is `out().out()`.
  `*g:V/1.{2}out()>>name` gives `{'ripple','lop'}`.
- **Ranged spans.** A ranged span is computed by `Processor.Helper.apply`, which runs each length and appends the
  results, keeping counts. `1.{0,2}plus(0)` gives `{3}1`, and `*g:V/1.{0,2}out().count()` gives 6, with lop at `{2}`.
- **Open spans.** Marko's 11-13 ruling, which landed as `83a05cf0c`: an open span (`{*}` = `{0,}`, `{+}` = `{1,}`,
  `{m,}`) **loops until noobj**. Each level is appended and nothing is deduplicated. A tree halts when it runs out of
  links, and a cycle loops forever: "that's what it expresses".
  - `*g:V/1.{*}out().count()` is 6, and `{+}` gives 5.
  - `{0,*}` is not coefficient syntax.
- **Negative spans.** Since `f4800f3e9`, a negative span runs the instruction's declared **converse**: its dom and rng
  flipped, with the same arguments. `out()` run backward is `in()`, and `plus(2)` run backward is `minus(2)`.
  - A converse is declared in `?decq`: `InstBuilder.converse`, or `declareConverse` for an instruction built by
    `instC`.
  - `4.{-1}plus(2)` is 2, and `josh.{-1,1}out()` is marko, josh, ripple, lop.
  - An instruction that declares no converse leaves its backward part as held code (limbo).
- So mtron's coefficients already form a Kleene algebra with star, and a negative exponent is the converse, R⁻¹, as in
  relation algebra.

---

## 2. The DSL *(Marko, 2026-11-13)*

**Two instructions.** Their argument is an **edge type**, of the general form `{*}#{*}::T`: the left `{*}` is span, `#`
is anything, and the right `{*}` is cardinality. "Given that edge labels are type labels, you[r] argument is an edge
type and that is what is tested."

| instruction | sugar | meaning |
|---|---|---|
| `smooth({*}#{*}::T)` | `~T~` | cross the edges whose type passes `T`; the element reached is not saved |
| `crumple({*}#{*}::T)` | `~T~>` | cross the same way, then save the element reached as a point. The `>` is "fold up to this point" |

- **A mark is like `as()`.** "~> is like an as('x'). It states this element is a point in the path being constructed.
  then the run of instructions after that till the next ~> fold into step on the path."
- **A type argument can carry a predicate.** `~knows::[weight=>?>0.4]~>` keeps only the knows edges heavier than 0.4.
- **The sugars are WRAP sugars:** a prefix token, the argument in the middle, a postfix token. `within()` was the
  precedent: `[1,2,3,4]._/>-.+3\_` gives `[4,5,6,7]`. `within()` itself is obsolete now that map and filter lift.

---

## 3. Proposed semantics *(proposed, still to be confirmed)*

- **The value is `points => cursor`.** The points are what the marks have saved, oldest first. The cursor is where the
  current run is.
- **The origin is the first point.** Without that, `g:V/+.~knows~.~created~>` would save only the created vertex.
- **`smooth(T)`.** The cursor crosses every edge out of the cursor whose type passes `T`, from the edge's out vertex to
  its in vertex. Nothing is saved.
- **`crumple(T)`.** The same crossing, then the element reached joins the points.
- **Every other instruction in a run** (`.>>name`, a filter) acts on the cursor and leaves the points alone.
  - That is the lift fibered recs already use (`c540e782b`), applied to a rel's cursor: no wrappers, and nothing on
    the monad.
  - This is the part that removes the `(id() => …)` boilerplate of §1.3.

How `g:V/1.~knows~.~created~>.>>name` evolves, with vertices shown by name:

```
g:V/1            marko                                          the origin is the first point
.~knows~         marko=>josh, marko=>vadas                      the cursor moves; nothing is saved
.~created~>      (marko=>ripple)=>ripple, (marko=>lop)=>lop     saved; vadas has no created edge, so it drops
.>>name          (marko=>ripple)=>'ripple', (marko=>lop)=>'lop'
```

**Where the span sits.**
- `~{2}knows~>`: span inside the tildes. knows² folds into one step, with one point at its end.
- `{2}~knows~>`: span on the instruction. This is the existing run semantics: it crumples twice and saves two points.
- `~{0}knows~>`: a zero-length step, which marks the current element in place (`as()` without moving).
- `~{0,1}knows~>`: an optional hop, then a mark.
- `~{*}knows~`: repeat knows until noobj, per §1.7.
- `~{-1}knows~`: knows⁻¹, the converse step. It crosses knows edges from in to out, just as `f4800f3e9` runs
  `{-1}out()` as `in()`.
- For `smooth`, the two positions mean the same thing. The difference only appears with `>`.

**Laws the sugar inherits.**
- Only `>` writes history, so a path's length is fixed by the text: the origin plus one point per mark.
- The runs between marks are compositions, so a compiler may fuse them. `~knows~.~created~` is one composite relation
  that a graph backend could run as one traversal.
- A step that only maps the cursor commutes with one that only reshapes the points.

**A link to the rel bridge.** Marko's 10-21 sketch of rel as a bridge was `person=[knows]=>person`, a rel whose bracket
types the relationship. A crumple folds a run into one step, so the folded step could record the run's edge types in
that bracket: `marko=[knows·created]=>lop`. Nothing needs this yet. It is where typed paths would go.

---

## 4. Open decisions *(Marko to rule)*

1. **Value shape.**
   - Option A, my pick: `(marko=>lop)=>'lop'`, with the points and the cursor kept apart. Every step acts on the
     outer arrow, and an object that is itself a rel is never mistaken for part of the path.
   - Option B: a single chain, `marko=>lop=>'lop'`. It reads like `1=>2=>3=>4=>5`, but every step has to reach the
     innermost range, and a rel-valued object is ambiguous there.
2. **Is the origin always a point?** My pick is yes. If not, it needs an explicit mark such as `~{0}…~>` at the start.
3. **Direction.** `~T~` crosses edges from out to in.
   - Proposed, now that `f4800f3e9` exists: in to out is the converse, `~{-1}T~` (or `{-1}~T~`). So `smooth` and
     `crumple` each declare a converse instruction, as `out` and `in` do.
   - Still open: what the converse instructions are called.
4. **Span and cardinality.**
   - Span is the exponent, per §1.7.
   - What should the right-hand cardinality constrain: the edges taken per element, or the results?
5. **Nominal edge types.** Live: `*g:V/+.outE().?knows::[weight=>?>0.6]` also returns `created::[weight=>1.0]@g:E/10`.
   - `modernSchema.KNOWS_TYPE` and `CREATED_TYPE` are both `rec` with the predicate `[weight=>real]`, so the test is
     structural.
   - Marko: "need to make edge types nominal so this doesn't happen".
   - By the 11-10 ruling, nominal is a name over a base, and these types state a predicate. So the edge test must
     check the name before the predicate. That is a type-system change.

---

## 5. Implementation plan

**Slice 1 is the parser layer, and it does not depend on §4.**
- Tids: `SMOOTH_INST_TID` and `CRUMPLE_INST_TID` under `GRPH_INST_TID` (`/m/db/grph/inst/…`), where the other graph
  instructions live.
- Sugars: `Sugar.wrap("~", "~>", [CRUMPLE], 1)` and `Sugar.wrap("~", "~", [SMOOTH], 1)`.
  - **Registration constraint (observed in code).** The sugar choice, `cachedSugarParser`, is built once in mParser's
    static block, from `mInstSet` and `catInstSet` only. `m_inst()` is read at grammar construction, so a sugar that
    `addSugar` registers later lands in `PARSERS` and never reaches the grammar.
  - Either the two wraps go in `mInstSet.sugars()`, or the choice becomes a settable parser that `addSugar` rebuilds.
    The second would make instruction-set sugars work in general, which is what Marko described.
- **Longest match.** Sugars are combined with `ChoiceParser(FURTHEST)`, so `~T~>` beats `~T~` without any ordering
  trick.
- **The `~` character.** `~` is in `REDUCED_FURI_CHARS` (`~/%$!#_-+:*`), as in `~/processor/fail`.
  - Live: `~knows~` is a bare uri, and `'x'.~knows~` fails with "expected an obj". So nothing claims the instruction
    position today.
  - Inside the wrap, though, the middle (`m_paren_wrap(obj_rel_back_parser)`) would read `knows~` greedily.
  - The fix: a bare furi may contain `~` but not end with one, using a one-character lookahead. `~/processor/fail`
    still parses. The cost is that a bare uri ending in `~`, like `backup~`, no longer parses.
- **Tests.** mParserTest rows parse `x.~knows~.~created~>.>>name` and `x.~knows::[weight=>?>0.4]~>`, plus regression
  rows for `~/processor/fail` and `a~b`. Several mParserTest sources use `~` as the CSV quote character, so the new
  rows need the default quoting.

**Slice 2, nominal edge types**, depends on §4.5.

**Slice 3, the bodies of `smooth` and `crumple` in grphInstSet.**
- They use `VertexRec.edges(direction, labels)` and test each `EdgeRec` against the edge type.
- They implement the §3 value: the cursor lift and the points.
- They declare their converses with `declareConverse`, so a negative span runs them backward.
- They depend on §4.1 to §4.4.

**Slice 4, tests and retirement.**
- TinkerGrphSpaceTest rows for `g:V/+.~knows~.~created~>.>>name`, the weighted form, and the span forms.
- Then, separately, retiring `out()` and `in()`.

**Known gaps met on the way:**
- **The typer and code arguments.** `x.mult((id() => >>OUT/created/IN).mult((id() => >>name)))` fails with "unable to
  determine inst function": the argument becomes code (`map(…).mult(…)`), and the typer cannot type the graph walk
  against the vertex arrow. The same composite written as a literal works, and int code regroups fine.
- **Precedence traps.**
  - `a * (b).rng()` binds as `(a * b).rng()`.
  - `cn -> (a).mult(b)` stores only `a`.
  - `((x) * (y))` used as an operand is a parse error.
- **`>>k` on a nested rel falls back one level only** (`Rel.Helper.rshiftRel` uses `rng.at(k)`).
  `1=>2=>3=>4=>5.>>2` is `3=>(4=>5)`, but `.>>3` is noobj.
- **The live server runs with `~/compiler/typer/check` all false.** With `inst_dom` off, `>>` dropped the lhs's count:
  `{[name=>'lop'],[name=>'lop']}.>>name.count()` was 1 live and 2 in the test VM. Main fixed this in `5f374b0f5` ("the
  dom check checks, it does not count"). Counts are still worth re-checking on ginger.
- **The serializer prints a bulked arrow's count on both ends**, as in `{3}'marko'=>{3}'marko'`.

---

## 6. References

- Commits:
  - `1ad7f9963` feat(rel): composition propagates the join, and a vertex walks its labelled adjacency
  - `cbe5e451c` fix(grph): a span breaks the gremlin pushdown run
  - `83a05cf0c` fix(processor): an open span loops until noobj
  - `5f374b0f5` fix(processor): the dom check checks, it does not count
  - `f4800f3e9` feat(span): a negative span runs backward -- the instruction's declared converse
- Code:
  - `Rel.mult` (`isa/m/type/Rel.java`), `Rel.Helper.rshiftRel`
  - `VertexRec.at`, `VertexRec.edges` (`isa/m/db/grph/space/VertexRec.java`)
  - `Lst.mult`
  - `Processor.Helper.apply` (spans)
  - `StatefulMonad.accruePath` (stamping)
  - `mParser`: `addSugar`, `cachedSugarParser`, `sugar_args`, `REDUCED_FURI_CHARS`
  - `modernSchema`: the edge types
  - `Sugar.wrap`
- Tests: `RelTest.testRelCompositionPropagatesTheJoin`, `TinkerGrphSpaceTest.testRelationTraversals`.
