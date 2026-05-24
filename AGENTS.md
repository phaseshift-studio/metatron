# metatron — AGENTS.md

## What is metatron?
A distributed data-oriented computing language and virtual machine built in Java. Two key terms:
- **metatron** (lowercase): the runtime system / VM environment
- **mtron** (lowercase): the functional programming language (like Java to JVM)

---

## Build & Test

### Commands
```bash
# Build with tests
mvn install

# Skip tests (fast dev loop)
mvn install -DskipTests

# Run all tests
mvn test

# Single test class
mvn test -Dtest=MemSpaceTest

# Exclude tests (CI pattern)
mvn test -Dtest='!httpSpaceTest,!fsSpaceTest'

# Package uber-jar
mvn clean package
# Output: target/metatron-0.1-SNAPSHOT-jar-with-dependencies.jar
```

### Requirements
- **Java**: JDK 21+ to compile and run (CI uses Oracle JDK 24, jDeploy uses Temurin JDK 25)

### Test Framework
- **JUnit 5** (Jupiter), surefire 3.5.5
- Test logging: `src/test/resources/logback-testing.xml`
- All tests extend `AbstractMetatronTest` which handles boot/shutdown

### Test Styles — `@ParameterizedTest` Preferred
- **Use `@ParameterizedTest` + `@CsvSource`** as the default pattern for new tests. Each CSV row is a self-contained scenario using mtron string expressions. This keeps tests data-extensible — corner cases are one CSV line, not new Java methods.
- Use standalone `@Test` methods only for multi-step orchestration (e.g., concurrency tests, complex setup/teardown) or non-tabular scenarios.
- The `%` delimiter avoids collision with mtron syntax (commas, pipes, semicolons).

**Example** — three scenarios, one test method:
```java
@ParameterizedTest(name = "[{index}] {4}")
@CsvSource(value = {
    "/c1 | \"immutable\" | \"mutated\" | blocks overwrite after constQ",
    "/c2 | \"first\"     | \"second\"  | preserves initial value",
}, delimiter = '%')
void testConstQ(String uri, String initial, String mutate, String desc) { ... }
```

- **Leverage static helpers** from `AbstractMetatronTest` for assertion logic:
  - `checkCodeParseApply(LOG, code, expected)` — parse + apply mtron, assert result
  - `checkCodeEvaluate(LOG, evaluate, fetch, expected)` — evaluate, fetch read-back, assert
  - `checkEquality(LOG, a, b, equals)` — compare two objs with log output
  - `<ERROR>` as an expected value triggers the fail-expected assertion path.
  - avoid `assertTrue/False` assertions as they provide information back to user on failure.

### Test Bootstrap (Important)
Every test class must extend `AbstractMetatronTest`. In `@BeforeAll`:
1. `TypeCheck.disable(TypeCheck.code_resolve)` — disables code resolution in tests
2. `BootLoader.BOOTING = true` — sets booting state
3. `BootLoader.TESTING = true` — skips shutdown hook headless wait
4. `BootLoader.load(...)` — initializes the VM

### Test Annotations
- **`@SkipInheritedTests(methods = {...})`** — skip specific inherited method names
- **`@SkipInheritedTests(tags = {...})`** — skip by test category tag (use `TestTag.CRUD`, `TestTag.BOUNDARY`, etc.)
- **`@ExtendWith(TestSkip.TestSkipExtension.class)`** — enables skip behavior
- **`@ExtendWith(TestData.TestDataExtension.class)`** — provides test data fixtures
- **`@TestCategory.Crud`** etc. — nested annotations for categorizing tests (`@Crud`, `@Type`, `@Boundary`, `@Concurrent`, `@ReadWrite`, `@Nested`, `@List`, `@Special`); defined in `TestCategory` class but currently unused in the test suite

### Test Infrastructure
- **Test containers**: MySQL (3306), PostgreSQL (5432), MariaDB (3307) — run in CI
- **In-memory**: MongoDB (`mongo-java-server`), MQTT (`moquette-broker`)
- SQLite JDBC available in both compile and test scope

---

## Running

### Run metatron
```bash
# bin/metatron script (recommended)
bin/metatron "[boot=><boot/boot.mtron>,log=>info]"
```
The `bin/metatron` script wraps the jar with JVM flags. Do not run the jar directly without these flags.

### Required JVM Flags
```
--enable-native-access=ALL-UNNAMED
--add-modules jdk.incubator.vector
--add-opens java.base/java.lang=ALL-UNNAMED
--add-opens java.base/java.lang.invoke=ALL-UNNAMED
--add-opens java.base/java.lang.reflect=ALL-UNNAMED
--add-opens java.base/java.util=ALL-UNNAMED
--add-opens java.base/java.util.concurrent.atomic=ALL-UNNAMED
--add-opens java.base/java.io=ALL-UNNAMED
--add-opens java.base/java.nio=ALL-UNNAMED
--add-opens java.base/java.net=ALL-UNNAMED
--add-opens java.base/sun.nio.cs=ALL-UNNAMED
```

---

## Architecture

### Directory Structure
```
src/main/java/studio/phaseshift/metatron/
├── isa/                    # Instruction Set Architecture (most code)
│   ├── m/                  # Core language (memSpace, mInstSet, types)
│   ├── mach/               # Machine/IO (fs, serial)
│   ├── web/                # HTTP/WebSocket + MCP
│   ├── grph/               # Graph DB (TinkerPop)
│   ├── tble/               # Table/SQL (SQLite, MariaDB, PostgreSQL, MySQL)
│   ├── dcmnt/              # Document stores
│   ├── vec/                # Vector types
│   ├── iot/                # IoT (MQTT, HomeAssistant, Zigbee2MQTT)
│   └── llm/                # AI/LLM (LangChain4j)
├── furi/                   # URI handling
├── algebra/                # Algebraic abstractions
└── util/                    # Shared utilities
```

### Key Concepts

**Spaces** — fundamental data containers, registered via `Router.global().addSpace()`. URI queries use `*<uri>` dereference (e.g., `*/sys/space/+/`).

**InstSet** (Instruction Sets) — discovered via `META-INF/services/` SPI. New sets go under `isa/<domain>/<domain>InstSet.java`.

**Obj** — universal type system. Subtypes: `Int`, `Str`, `Rec` (record/map), `Lst` (list), `Type`, `Code`, etc.

**URI wildcards**: `+` = single segment, `#` = multi-segment (MQTT-style)

### Boot Loader Lifecycle
1. `BootLoader.main()` → parses args, optionally loads boot file
2. `BootLoader.load()` → creates `/sys` space, loads base ISA (`/m`, `/m/mach`)
3. `BootLoader.close()` → teardown hook

---

## CI/CD

|.github/workflows/maven.yml| `.github/workflows/jdeploy.yml`
|--:|--:
| Push/PR to `main` | Push to `*-snapshot` branches, `v*` tags |
| Oracle JDK 24 | Temurin JDK 25 |
| `mvn install -Dtest='!httpSpaceTest,!fsSpaceTest'` | `mvn package` + jDeploy bundler |

Docker build is **disabled by default** (`skipDocker=true` in pom). Enable with `-DskipDocker=false`.

---

## Conventions
1. **Packages**: `studio.phaseshift.metatron.isa.<domain>`
2. **New instruction sets**: `isa/<domain>/<domain>InstSet.java`
3. **New spaces**: Extend `AbstractSpace` or `Space`, register with router
4. **Tests**: Mirror main packages; prefer `@ParameterizedTest` + `@CsvSource` with `%` delimiter; use mtron string expressions in CSV rows; leverage `checkCodeParseApply`/`checkCodeEvaluate`/`checkEquality` from `AbstractMetatronTest`; use `@SkipInheritedTests` for selective skipping
5. **Docker**: Skipped by default. Run `-DskipDocker=false` to build.

---

## MCP (Model Context Protocol)
- WebSocket handler: `mcp_mtron_wsHandler`
- Test client: `.metatron/skills/mtron/scripts/mtron_ws_client.py`

## References
- mtron language skills: `.metatron/skills/mtron/`
- Agent memory: `.claude/memory/`
