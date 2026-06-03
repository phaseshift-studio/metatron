# Contributing to metatron

This document defines PR expectations, coding standards, and contribution guidelines for both humans and agents.

---

## Table of Contents

- [Testing Requirements](#testing-requirements)
- [Reuse Existing Helpers](#reuse-existing-helpers)
- [Exception Handling](#exception-handling)
- [Logging](#logging)
- [Naming Conventions](#naming-conventions)
- [Spaces and InstSets](#spaces-and-instsets)
- [Commit Conventions](#commit-conventions)
- [PR Expectations](#pr-expectations)
- [Architecture Awareness](#architecture-awareness)

---

## Testing Requirements

- **New code MUST have test cases** — no exceptions
- **`@ParameterizedTest` + `@CsvSource` is the default pattern** — each CSV row is a self-contained scenario using mtron string expressions. This keeps tests data-extensible: corner cases are one CSV line, not new Java methods
- Use `%` as CSV delimiter (avoids collision with mtron syntax: commas, pipes, semicolons)
- Leverage static helpers from `AbstractMetatronTest`:
  - `checkCodeParseApply(LOG, code, expected)` — parse + apply mtron, assert result
  - `checkCodeEvaluate(LOG, evaluate, fetch, expected)` — evaluate, fetch read-back, assert
  - `checkEquality(LOG, a, b, equals)` — compare two objs with log output
  - Use `<ERROR>` as expected value to trigger the fail-expected assertion path
- **Avoid `assertTrue/false`** — they provide no useful info on failure
- Standalone `@Test` only for multi-step orchestration (concurrency, complex setup/teardown)

**Example:**

```java
@ParameterizedTest(name = "[{index}] {4}")
@CsvSource(value = {
    "/c1 | \"immutable\" | \"mutated\" | blocks overwrite after constQ",
    "/c2 | \"first\"     | \"second\"  | preserves initial value",
}, delimiter = '%')
void testConstQ(String uri, String initial, String mutate, String desc) { ... }
```

---

## Reuse Existing Helpers

New code should leverage existing utility/Helper classes instead of re-implementing algorithms:

- **`XXX.Helper`** — every major type class has a nested `Helper` with static utilities (e.g., `Type.Helper`, `Rec.Helper`, `Obj.Helper`, `Code.Helper`, `InstSet.Helper`, `Uri.Helper`, `Inst.Helper`)
- **`fURI`** — rich in methods for URI manipulation, path extension, segment access, wildcard matching; use these instead of string concatenation
- **`CommonUtils`** — shared utilities for common operations
- **`AbstractMetatronTest`** — test infrastructure (bootstrap, assertions, skip extensions)

---

## Exception Handling

- Use **`MTronException.of()`** — never throw raw `RuntimeException` or `IllegalArgumentException`
- Overloads available:
  - `MTronException.of("message")` — simple message
  - `MTronException.of("format %s", arg)` — formatted message
  - `MTronException.of(cause)` — wrap existing exception
  - `MTronException.of(cause, "format %s", arg)` — wrap with context
  - `MTronException.of(fURI source, "format %s", arg)` — include source URI
- `MTronException` auto-annotates messages with ANSI color codes via `Graphitty.string()`

---

## Logging

Every `Obj` extending class has a `logger()` method (defined in `Feature` interface) that returns a `GraphittyLogger`. Use it so logging can be routed based on the Obj publishing the log:

```java
// Instance logging — log is associated with this specific obj
this.logger().info("processing %s", someValue);
this.logger().warn("unexpected state: %s", state);
this.logger().error(e, "failed to process %s", input);
```

For **static logging** not associated with a particular Obj instance, create a `protected static final` field:

```java
public class MySpace extends AbstractSpace {
    protected static final GraphittyLogger LOG = Graphitty.log(MySpace.class);

    // ... use LOG.info(), LOG.warn(), LOG.debug(), etc.
}
```

Log levels: `trace()`, `debug()`, `info()`, `warn()`, `error()`, `none()`

---

## Naming Conventions

### Spaces and InstSets: lowercase with abbreviated first word

- `tbleSpace`, `tbleInstSet` (not `table`)
- `dcmntSpace`, `dcmntInstSet` (not `document`)
- `grphSpace`, `grphInstSet` (not `graph`)
- `vecSpace`, `vecInstSet` (not `vector`)
- `iotInstSet`, `llmInstSet`, `webInstSet`, `machInstSet`

### Packages

- `studio.phaseshift.metatron.isa.<domain>`

### New instruction sets

- `isa/<domain>/<domain>InstSet.java`

---

## Spaces and InstSets

### Space Registration

Spaces should extend `AbstractSpace`. Doing so automatically adds the space to the router at construction time (via `Router.global().addSpace(this)` in the `AbstractSpace` constructor).

### The Three Pillars: `SPACE_TYPE`, `Space.class`, `SPACE_TID`

Nearly every exposed `Obj`-extending class has three related constants:

1. **`SPACE_TID`** (`fURI`) — the universal type ID, identifies the type in the URI namespace
2. **`SPACE_TYPE`** (`Type`) — has the predicate and constructor instructions for creating instances of the space
3. **`Space.class`** (Java class) — the runtime implementation

**Example pattern from `memSpace`:**

```java
public static final fURI MEM_SPACE_TID = M_ISA_TID.extend("space").extend("memspace");

public static final Type MEM_SPACE_TYPE = Type.Builder.build()
    .tid(SPACE_TID)
    .vid(MEM_SPACE_TID)
    .predicate(instC(...))
    .constructor(instC(...));
```

- `SPACE_TID` is the parent type's TID (e.g., `SPACE_TID` for spaces, `M_ISA_TID` for instruction sets)
- `.vid()` sets the specific type's video ID (unique identifier)
- The predicate defines what operations are valid on instances
- The constructor defines how to create new instances

---

## Commit Conventions

- Use conventional commit format: `type(scope): description`
- Types: `feat`, `fix`, `refactor`, `docs`, `test`, `chore`
- Scope is optional but encouraged (e.g., `refactor(schema): ...`)
- Branch naming: `fix/description`, `feat/description`, `docs/description`

---

## PR Expectations

- Every PR should reference a GitHub issue (`Closes #N` or `Refs #N`)
- PR description should explain the **why**, not just the **what**
- Changes should be minimal and focused — one concern per PR
- All CI checks must pass before merge
- Self-review your diff before requesting review

---

## Architecture Awareness

- **Spaces** — fundamental data containers, auto-registered via `AbstractSpace` constructor
- **InstSets** — discovered via `META-INF/services/` SPI
- **Obj** — universal type system (`Int`, `Str`, `Rec`, `Lst`, `Type`, `Code`, etc.)
- **URI wildcards**: `+` = single segment, `#` = multi-segment (MQTT-style)
- **DataPath** — understand how graph, relational, and document DBs structure their URI paths (same pattern across all three)
- **Types** — the `Type` class is the single source of truth for schema; column types, FK references, and other metadata live in the Type's `isaPredicate`

---

## References

- [AGENTS.md](./AGENTS.md) — build, test, run instructions
- [README.md](./README.md) — project overview
