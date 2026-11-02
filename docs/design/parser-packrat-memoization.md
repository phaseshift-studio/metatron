# Parser Packrat Memoization — Design

**Date:** 2026-08-17
**Status:** implemented (working tree, uncommitted)
**Owner:** Marko A. Rodriguez + Dr. Stynx
**Scope:** make `mParser` (mtron's PEG parser) *linear* on nested `rec`/`lst` literals — eliminating exponential backtracking — without changing the language or the parser's rule order.

---

## 1. TL;DR

- The pain: parsing a nested `doc` rec (`out=>[[…]]`, ~9.8 KB, depth ~7) took **~3 minutes**; a 17-character `[[[[[[[1,2,3]]]]]]]` took ~2 minutes.
- The cause: **exponential backtracking** — ~**10× per nesting level** — from three overlapping PEG ambiguities that petitparser resolves by recursive trial-and-error.
- The fix: **manual packrat memoization**. petitparser-java 2.4.0 has no built-in `.memoized()` (that API is Dart-PetitParser), so we wrap the recursive rules in a memoizing `SettableParser` subclass that caches `(position) → Result` per thread, per parse run.
- The result: **3 min → 86 ms**, depth scaling goes flat, and `mInstSetTest` stays **707/707 green** — language and parser order are byte-for-byte unchanged.

---

## 2. The problem, concretely

`ObjmtronSerializer.read(String)` → `mParser.eval/parse` on a mtron literal that is deeply nested:

```
[type=>doc, out=>[ [type=>p, out=>[[…],[…]]], [type=>b_list, out=>[ … entry … ]] , … ]]
```

Parsing cost grew as `pick() = 10 × n + 3` per nesting level (measured by counting successful `.map()` actions):

| input | len | successful sub-parses |
|---|---|---|
| `[[[[1,2,3]]]]` | 11 | 123,933 |
| `[[[[[1,2,3]]]]]` | 13 | 1,239,333 |
| `[[[[[[1,2,3]]]]]]` | 15 | 12,393,333 |

That's a textbook PEG blowup: each nested `[` forces the parser to re-derive the entire inner structure ~10 times.

---

## 3. Root cause — three overlapping ambiguities

The grammar (`mParser.java`) is a recursive-descent PEG built from `choice(...)`/`seq(...)`/`separatedBy(...)`. Three constructs are ambiguous, and the resolution is "try `m_rec` first, backtrack when it fails":

1. **`[a=>b]`** — a rec `{a:b}` vs a list of a rel `[a=>b]`.
2. **`[[a,b]=>c]`** — a rec with a *list key* `{[a,b]:c}` vs a list of a rel.
3. **`[k=>a=>b]`** — a rec with a *bare rel value* vs a rec with a nested-rel key.

Each ambiguity makes a recursive rule (`rec_parser`'s key parser `obj_rel_back_parser`, or `rel_parser`'s LHS) re-descend into the nested `[` before failing, and the cost compounds multiplicatively with depth.

---

## 4. Why not fix the grammar?

We tried the obvious grammar-level "fixes" first; each removed **one** ambiguity but broke a tested construct and exposed the **next** 3×/depth source behind it:

| attempt | what it broke |
|---|---|
| scalar-only rec keys | `group()` rec-keys and instruction keys (`is(eq(1))=>…`) |
| scalar rel LHS | poly rel domains (`([a,b]=>[c,d])`, `[]=>[]`) — `RelTest` |
| force `m_rel` parens in `obj_no_code_parser` | bare rel values / list elements — 18 `mInstSetTest` errors |
| reorder `m_lst` before `m_rec` | recs parsed as lists (`[k0=>1]` → `MLst`) |

The lesson: the ambiguity is **inherent to the language's expressiveness** (poly keys, poly rel domains are real, tested features). You can't remove it by reordering; you have to **stop re-deriving the same answer** — which is exactly what memoization does.

---

## 5. The fix — packrat memoization

A minimal packrat layer in `mParser.java` (no new files, no grammar edits):

```java
public static boolean MEMOIZE = true;                       // toggle, for benchmarking

private static final ThreadLocal<Long> MEMO_RUN = ThreadLocal.withInitial(() -> 0L);
private static void beginParse() { MEMO_RUN.set(MEMO_RUN.get() + 1L); }

private static final class MemoizedSettableParser extends SettableParser {
    private final ThreadLocal<State> state = ThreadLocal.withInitial(State::new);

    @Override public Result parseOn(Context context) {
        if (!MEMOIZE) return super.parseOn(context);        // off = old parser
        State s = state.get();
        if (MEMO_RUN.get() != s.runId) { s.cache.clear(); s.runId = MEMO_RUN.get(); }
        Result c = s.cache.get(context.getPosition());
        if (c != null) return c;
        Result r = super.parseOn(context);
        s.cache.put(context.getPosition(), r);
        return r;
    }
    static final class State { Map<Integer,Result> cache = new HashMap<>(); long runId = -1L; }
}
```

Design points:

- **Memoize the recursive rules only** — 9 of the 10 `SettableParser`s (`obj_parser`, `lst_parser`, `rec_parser`, `rel_parser`, `obj_no_code_parser`, `obj_no_call_parser`, `obj_rel_back_parser`, `inst_parser`, `and_or_parser`). They're the rules that re-enter themselves.
- **Leave `obj_rel_back_parser2` un-memoized** — it is the sugar-argument rule (the only one whose `choice` uses `m_inst()` rather than `m_code()`). Memoizing it made the parser's result context-sensitive: an instruction *definition* (`|inst?…{…}@band`) rides in through the `|` block-sugar's argument onto `obj_rel_back_parser2`, whose `m_inst` path parses the `else(3)`/`else(8)` default args — and caching that rule returned a stale/default-arg-less result. Because the exponential is in the *structural* descent (`lst`/`rec`/`rel`/`obj_no_code`), dropping this one rule keeps the linear-time win while restoring correctness (`InstTest` 13/13).
- **Keyed by position within one run** — the grammar is deterministic and the input buffer is immutable, so `(rule, position)` has exactly one answer. `beginParse()` bumps `MEMO_RUN` at the top of `parse()`, `parseDiagnose()`, and each `parseMulti()` iteration (each uses a different substring buffer), so the cache clears between runs.
- **Per-thread cache** — metatron's MCP/HTTP servers parse concurrently; a shared `HashMap` would let two threads' independent run-ids collide and serve each other's stale results.
- **Observational, not semantic** — "off" and "on" explore the identical alternatives in the identical order and return the identical `Result`; memoization only collapses redundant re-evaluations.

---

## 6. Results

| input | before (off) | after (on) |
|---|---|---|
| `[[[[1,2,3]]]]` | 130 ms | < 1 ms |
| `[[[[[1,2,3]]]]]` | 959 ms | < 1 ms |
| `[[[[[[1,2,3]]]]]]` | 10,465 ms | < 1 ms |
| `[[[[[[[1,2,3]]]]]]]` | ~2 min (extrapolated) | 1 ms |
| `doc` rec (9,884 chars) | ~3 min | **86 ms** |

Verification: `mInstSetTest` — **707 run, 0 failures, 0 errors**; `TypeTest` — **475 run, 0 failures, 0 errors**; `InstTest` — **13 run, 0 failures, 0 errors**. The suites got faster (mInstSetTest 143 s → 79 s) because the test expressions themselves contain nested recs. Nine tricky edge cases (`[[a,b]=>c]` list-keys, `[k=>[a,b]=>c]` rel-values, `[is(eq(1))=>plus(10)]` instruction-keys, `([a,b]=>[c,d])` poly-rel-domains, …) parse to byte-identical `MRec`/`MLst`/`MRel` results.

`furi_parser` was `public` but unreferenced outside `mParser.java` → made `private` (narrowing the surface for the memoized set).

---

## 7. Trade-offs / notes

- **Memory**: memoization stores one `Result` per `(rule, position)` per run. Bounded by `rules × input length`, freed when the run ends (cache is per-thread and cleared per run).
- **Left-recursion caveat**: packrat memoization does **not** fix left recursion (it actually makes left-recursive grammars loop with cached failures). mtron's grammar avoids left recursion via `SettableParser` indirection and the `m_rel`/`m_rec` structure; no left-recursive rule was observed. If one is ever introduced, memoization will surface it as a stale-failure loop — that's a grammar bug, not a memoization bug.
- **Context-sensitivity caveat**: `(position) → Result` caching is only valid for *context-free* rules. `obj_rel_back_parser2` (the sugar-argument rule) is context-sensitive because its `m_inst` path dispatches to sugars and resolves instructions, so it is deliberately left un-memoized. If a future change adds memoization to a new rule, confirm that rule's result at a position does not depend on the enclosing parse.
- **`MEMOIZE` toggle** exists purely to benchmark on/off (and as a kill-switch); it should default `true` and is expected to stay `true` in production.
