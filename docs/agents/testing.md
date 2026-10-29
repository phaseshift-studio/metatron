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

The `@Training` annotation turns a `@ParameterizedTest` + `@CsvSource` method into LLM fine-tuning data for
`UnslothTrainingDatasetExtractor`. It is `@Repeatable`; each of `instruction`, `input`, and `output` is a template
whose `{{{param}}}` holes are replaced by that column's row value. A hole substitutes only when its content is an
exact match for a method parameter name (needs the `-parameters` compiler flag); anything else (e.g. an mtron
`{{{expr}}}` template) is emitted verbatim.

```java

@Training(
        instruction = "when the {{{rec}}} rec is rshifted by the {{{key}}} key, what is the result?",
        input = "{{{rec}}}>>{{{key}}}",
        output = "{{{value}}}")
@ParameterizedTest
@CsvSource(value = {
        "[a=>b]   % c   % noobj",
        "[a=>b]   % a   % b",
}, delimiter = '%')
void testKeyValue(String rec, String key, String value) { ...}
```

Each CSV row emits one `{instruction, input, output}` entry. Repeat the annotation to emit several entries per row.
Methods with no `@Training` fall back to a two-column `expression → result` mapping (`?docq` operator context is
woven into the instruction).

**`{{{@TestData}}}` accessor** — when the method carries `@TestData`, the hole `{{{@TestData}}}` is replaced by
the annotation's `value()` strings joined with newlines, so an entry can cite its preloaded setup data:

```java

@Training(instruction = "given {{{@TestData}}}, what is the result of the mtron expression {{{code}}}?",
        output = "{{{expected}}}")
@TestData(value = {
        "x -> [address/home/city=>\"santa fe\",address/work/city=>\"nomansland\"]",
        "y -> [address/home/city=>\"santa fe\",address/work/city=>\"santa fe\"]"})
@CsvSource(value = {
        "[a=>1,b=>2,c=>3].select([a=>_,b=>_])   % [a=>1,b=>2]",
}, delimiter = '%')
void testSelect(String code, String expected) { ...}
```

Rendering throws `MTronException("attempting to access non-existent @TestData")` if the method has no `@TestData`.

**`@Training.SkipTraining(reason)`** — nested annotation. When present on a method, the extractor emits no entries
for it; use it for tests that don't map cleanly to `expression → result`:

```java
@Training.SkipTraining(reason = "too complicated to express easily")
```

See `UnslothTrainingDatasetExtractor` for the extraction logic and
`.metatron/skills/mtron/references/unsloth-training-mtron.md` for the full training pipeline.

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

1. `TypeCheck.disable(TypeCheck.code_resolve)` — disables full code resolution requirement in tests (i.e. dynamic
   resolution is 'ok')
2. `BootLoader.BOOTING = true` — sets booting state
3. `BootLoader.TESTING = true` — skips shutdown hook headless wait
4. `BootLoader.load(...)` — initializes the VM

## Test Infrastructure

- **Test containers**: MySQL (3306), PostgreSQL (5432), MariaDB (3307) — run in CI
    - a custom container example exists with ChromaDB.
- **In-memory**: MongoDB (`mongo-java-server`), MQTT (`moquette-broker`)
- SQLite JDBC available in both compile and test scope

---
