# Testing — agent reference

Moved out of [AGENTS.md](../../AGENTS.md) on 2026-10-21 so that file holds only the rules every session needs; AGENTS.md
links here where it applies. AGENTS.md's "Tests" section is the summary.

## Test Framework

- **JUnit 5** (Jupiter), surefire 3.5.5
- Test logging: `src/test/resources/logback-testing.xml`
- All tests extend `AbstractMetatronTest` which handles boot/shutdown

## Test Styles — `@ParameterizedTest` Preferred

- **Use `@ParameterizedTest` + `@CsvSource`** as the default pattern for new tests. Each CSV row is a self-contained
  scenario using mtron string expressions. This keeps tests data-extensible — corner cases are one CSV line, not new
  Java methods.
- Don't name your `@ParameterizedTest` -- i.e. don't do `(name = "[{index}] {1}")`
- Use standalone `@Test` methods only for multistep orchestration (e.g., concurrency tests, complex setup/teardown) or
  non-tabular scenarios.
- The `%` delimiter avoids collision with mtron syntax which use commas, pipes, semicolons.
- The `@TestData` test method annotation enables preloading data (or configuring metatron state) prior to test
  evaluation.

**Example** — three scenarios, one test method:

```java

@ParameterizedTest()
@TestData(value = {
        "a -> 555",
        "@/sys/thread/executor >>= noobj"
})
@CsvSource(value = {
        "/c1 | \"immutable\" | \"mutated\" | blocks overwrite after constQ",
        "/c2 | \"first\"     | \"second\"  | preserves initial value",
}, delimiter = '%')
void testConstQ(String uri, String initial, String mutate, String desc) { ...}
```

## `@Training` — Template-Based Training Data

`@Training` (and `@Training.SkipTraining(reason)`) marks a `@ParameterizedTest` + `@CsvSource` method for
extraction into the LLM fine-tuning corpus. A row counts only if its `input`, evaluated, reproduces its
`output`, so the annotation carries rules that change with the pipeline — a mandatory `input`, how columns
are composed into an expression, `{{{@TestData}}}` setup, and when to skip a method outright.

See [unsloth-training-mtron.md](../skills/mtron/references/unsloth-training-mtron.md) §1 *Dataset
Extraction* for the annotation contract, the extraction/verification commands, and the corpus format.

- **Leverage static helpers** from `AbstractMetatronTest` for assertion logic:
    - `checkCodeParseApply(LOG, code, expected)` — parse + apply mtron, assert result
    - `checkCodeEvaluate(LOG, evaluate, fetch, expected)` — evaluate, fetch read-back, assert
    - `checkEquality(LOG, a, b, equals)` — compare two objs with log output
    - `<ERROR>` as an expected value triggers the fail-expected assertion path.
    - avoid `assertTrue/False` assertions as they provide little information back to user on failure.

## Canonical Test Examples

The best example of the desired test-style can be found at:
`studio.phaseshift.metatron.isa.m.mInstSetTest`

## Test Bootstrap (Important)

Every test class must extend `AbstractMetatronTest`. In `@BeforeAll`:

1. `Typer.Helper.enableAll()` then `Typer.Helper.set(Typer.CODE_RESOLVE, false)` — every check point on except
   full code resolution (dynamic resolution is 'ok'). The state belongs to the machine's compiler, so a test that
   installs its own compiler sets it AFTER `Machine.current().compiler(...)`.
2. `BootLoader.BOOTING = true` — sets booting state
3. `BootLoader.TESTING = true` — skips shutdown hook headless wait
4. `BootLoader.load(...)` — initializes the VM

## Test Infrastructure

- **Test containers**: MySQL (3306), PostgreSQL (5432), MariaDB (3307) — run in CI
    - a custom container example exists with ChromaDB.
- **In-memory**: MongoDB (`mongo-java-server`), MQTT (`moquette-broker`)
- SQLite JDBC available in both compile and test scope

---
