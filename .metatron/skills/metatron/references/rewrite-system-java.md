---
name: rewrite-system-java
description: Java-side rewrite infrastructure — Rewriter, RewriteBuilder, InstSet.Helper.rewriter, Code.rewrite() loop, space-aware native pushdown, custom rewrite functions, and registration patterns
---

# Rewrite System (Java)

## Package map

```
algebra/rewrite/Rewriter.java       ← fixed-window pattern matcher + replacement engine
algebra/rewrite/RewriteBuilder.java ← fluent builder for space-aware native rewrites
algebra/rewrite/CommonRewrites.java ← shared rewrite utilities
isa/m/type/InstSet.java             ← InstSet interface, Helper.rewriter() factory
isa/m/type/Code.java                ← Code.rewrite() — the rewrite loop
isa/m/mInstSet.java                 ← /m rewrites (id_removal, map_nest, explain_profile, ...)
isa/tble/tbleInstSet.java           ← SQL rewrites (sql_count, sql_where, sql_limit, ...)
isa/grph/grphInstSet.java           ← Gremlin rewrites (gremlin_count, gremlin_where, ...)
isa/dcmnt/dcmntInstSet.java         ← MongoDB rewrites (mql_count, mql_where, ...)
```

## Lifecycle — when rewrites fire

```
expression.parse()        → Call / Code
        │
        ▼
Code.resolve(lhs)                       [Code.java:98]
        │
        ├─ this.rewrite()               [Code.java:102]
        │   │
        │   ├─ iterate ALL InstSet spaces in Router
        │   │   └─ r.apply(currentCode) for every rewrite Inst
        │   │       └─ replace if result.isCode()
        │   │
        │   └─ repeat until fixed point (hash stabilizes)
        │
        └─ InstResolver.resolveCode()   [InstResolver.java:105]
            └─ resolve each Inst in the rewritten list
```

Rewrites run **before** instruction resolution. This means domains/ranges in the instruction
list are still generic (`#::T`) when rewrites fire — call `.resolve(noobj())` on extracted
code if you need concrete types.

## Registration

Rewrites are registered in an `InstSet` under the `uri(REWRITE)` key during `setup()`.
Every `InstSet` space contributes its rewrites to the global pool — the `Code.rewrite()`
loop iterates all of them:

```java
Router.global().spaces()
    .elements()
    .filter(r -> r.second() instanceof InstSet)
    .flatMap(r -> r.second().<InstSet>as().rewrites().stream())
    .forEach(r -> {
        final Obj rewritten = r.apply(rewrittenCode.get());
        if (rewritten.isCode()) {
            rewrittenCode.set(rewritten.asCode());
        }
    });
```

A rewrite is an `Inst` whose function takes a `Code` and returns a `Code`. If the result
is a `Code`, it replaces the current code for the next iteration. If the result is not a
`Code` (e.g., `noobj`, a single `Inst`), it is discarded and the code is left unchanged.

## Rewrite styles

### 1. `Rewriter.search().match().rewrite()` — fixed-window pattern replacement

Matches a concrete sequence of instruction TIDs against a sliding window, then replaces
the matched slice with a new instruction list. The rewrite function receives a `Map<Inst, Inst>`
mapping pattern instructions to matched source instructions.

```java
// Remove identity instructions
InstSet.Helper.rewriter(M_ISA_REWRITE_TID.extend("id_removal"),
    code -> code.selfJVM(
        Rewriter.search(code.insts())
            .match(instA(ID_INST_TID).insts())   // match [_]
            .rewrite(x -> List.of())              // replace with nothing
    ).asCode())
```

**`match(List<Inst>)`** — pattern instructions. `instA(tid)` creates a bare match instruction
(no args, no f). Matching uses `instsMatch()`: TIDs must be compatible via `tid.test()`,
args are compared by position, and `?.test(source)` tests unresolved args against source.

**`match(List<Inst>, Predicate<List<Inst>>)`** — adds a runtime guard. The function receives
the matched source instructions; return `false` to skip the rewrite.

**`repeat()`** — keep applying the rewrite until the instruction list stabilizes.

**`matchCC()`** — also match coefficients (disabled by default — only TIDs and args).

**`allow` / `disallow`** — per-rewrite URI-based gate lists.

**Rewrite function** — `Function<Map<Inst, Inst>, List<Inst>>`. The map keys are the
match pattern instructions; values are the matched source instructions. Return the
replacement instruction list.

**Key limitation:** fixed-length matching only. The pattern `[A, B]` matches exactly
two instructions. There is no variable-length prefix/suffix capture.

### 2. `RewriteBuilder` — space-aware native operation pushdown

A fluent builder for rewrites that replace generic instruction chains with native
database operations. The key difference: the rewrite checks **which space** the data
lives in, and only fires when it's the right backend.

```java
RewriteBuilder.forDatabase(tbleSpace.class)
    .tid(TBLE_ISA_REWRITE_TID.extend("sql_count"))
    .match(FROM_INST_TID, COUNT_INST_TID)        // match [from, count]
    .optimize("from_count", (space, dp, coeff) -> {
        String table = dp.collection();
        try (Statement stmt = space.sjvm().createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT COUNT(*) FROM " + table)) {
            return rs.next() ? jnt(rs.getInt(1)).c(c -> c.mult(coeff)) : jnt(0);
        }
    })
    .build();
```

**Builder methods:**

| Method | Purpose |
|---|---|
| `forDatabase(Class<S>)` | Restrict to a specific space type |
| `tid(fURI)` | Rewrite instruction TID |
| `rng(fURI)` | Result type (e.g., `INT_TID`) |
| `match(fURI...)` | Sequence of instruction TIDs to match |
| `matchPredicate(Predicate)` | Runtime guard on matched instructions |
| `matchSpacePredicate(BiPredicate)` | Guard with access to the typed space (e.g., check table exists) |
| `optimize(name, NativeOptimization)` | Native execution lambda `(space, dataPath, coeff) -> Obj` |
| `optimizeWithURI(name, NativeOptimizationWithURI)` | Native execution with expanded fURI |
| `build()` | Produce the `Inst` for registration |

**Execution flow:**
1. `Rewriter.search().match().rewrite()` finds a matching instruction sequence
2. The rewrite function checks `spaceType.isInstance(space)` — wrong space type → skip
3. `matchPredicate` and `matchSpacePredicate` guards run — fail → return original unchanged
4. Native lambda executes against the database, receiving the coefficient for cardinality tracking
5. The matched sequence is replaced with a single native instruction

### 3. Custom `Function<Code, Code>` — full AST inspection

When the `Rewriter` API's fixed-window matching doesn't fit (variable-length prefix
capture, conditional restructuring), use `InstSet.Helper.rewriter()` directly with
a custom function:

```java
InstSet.Helper.rewriter(M_ISA_REWRITE_TID.extend("explain_profile"),
    code -> {
        final List<Inst> insts = code.insts();
        if (insts.isEmpty() || insts.size() < 2) return code;
        final Inst last = insts.getLast();
        if (!last.tid().basePath().equals(EXPLAIN_INST_TID)) return code;
        // Extract everything before explain() as a Code argument
        final List<Inst> preceding = new ArrayList<>(insts.subList(0, insts.size() - 1));
        final Code precedingCode = MCode.of(preceding).resolve(noobj());
        // Replace the whole chain with a single compute instruction
        return code.selfJVM(List.of(
            instC(computeTID, lst(block_(precedingCode).tryToInst()),
                (lhs, inst) -> str(explainTable(inst.arg(0).asCode())))
        )).asCode();
    })
```

**Pattern:** inspect the full instruction list, decide whether to transform, return
either the original `code` (no-op) or `code.selfJVM(newInsts).asCode()` (replacement).

**Key points:**
- `code.selfJVM(newList).asCode()` wraps a new instruction list into the same Code frame
- The inline lambda receives the rewritten code as `inst.arg(0)` — resolved and concrete
- Keep replacement logic terminal-free; rewrites execute before any Console exists
- Dynamic dispatch is normal for inline instructions — they resolve at runtime

## Common patterns

### Returning the original unchanged

If the rewrite condition isn't met, return the input `code` directly. The rewrite loop
sees no change and moves on:

```java
if (!last.tid().basePath().equals(EXPLAIN_INST_TID)) return code;
```

### Replacing instructions inline

```java
return code.selfJVM(newInstructionList).asCode();
```

### Creating a native instruction with domain/range

For native operations, set `dom(NOOBJ_TID.zero())` (zero cardinality) so the instruction
never fires on an empty stream — it either runs natively (one-shot) or not at all:

```java
instC(tid.dom(NOOBJ_TID.zero()).rng(INT_TID), lst(uri(fURI)),
    (lhs, inst) -> executeNative(inst.arg(0).uriValue()))
```

### Resolving extracted code for concrete types

Extracted code still has generic domains. Resolve it before passing to formatters:

```java
final Code precedingCode = MCode.of(preceding).resolve(noobj());
```

## The rewrite instruction type

`InstSet.Helper.rewriter()` creates the instruction:

```java
public static Inst rewriter(final fURI tid, Function<Code, Code> rewrite) {
    return instC(tid.dom(CODE_TID).rng(CODE_TID.maybe()), lst(),
        (lhs, inst) -> rewrite.apply(lhs.asCode()));
}
```

- **Domain:** `CODE_TID` — receives a Code
- **Range:** `CODE_TID.maybe()` — returns an optional Code (noobj = no rewrite)
- **Body:** calls the provided `Function<Code, Code>`

The returned `Inst` is registered under `uri(REWRITE)` in the instset. During
`Code.rewrite()`, it's applied with the current code as `lhs`.

## Convenience: `InstSet.Helper.rewriter()` with Rewriter

The common `docWrap(InstSet.Helper.rewriter(...), ...)` pattern:

```java
docWrap(
    InstSet.Helper.rewriter(tid, code -> code.selfJVM(
        Rewriter.search(code.insts())
            .match(pattern)
            .rewrite(replaceFunc)
    ).asCode()),
    "description"
)
```

`docWrap` registers the rewrite under `uri(REWRITE)` and attaches the description string
as the `?docq` query parameter on the rewrite TID.

## See also

- `type-system-java.md` — Call/Inst/Code type hierarchy
- `metatron-ui-architecture.md` — ExplainTool, ProfileTool (interactive consumers of
  code introspection)
