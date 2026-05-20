# metatron — AGENTS.md

## What is metatron?
A JVM-based distributed computing language and VM. Two key terms:
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
- **Java**: JDK 21+ to compile, JDK 24+ to run tests (CI uses Oracle JDK 24, jdeploy uses Temurin JDK 25)
- **surefire `jvm` path**: Hardcoded to `${user.home}/.sdkman/candidates/java/current/bin/java` in `pom.xml` — non-SDKMAN setups may need to edit the pom or override

### Test Framework
- **JUnit 5** (Jupiter), surefire 3.5.5
- Test logging: `src/test/resources/logback-testing.xml`
- All tests extend `AbstractMetatronTest` which handles boot/shutdown

### Test Bootstrap (Important)
Every test class must extend `AbstractMetatronTest`. In `@BeforeAll`:
1. `TypeCheck.disable(TypeCheck.code_resolve)` — disables code resolution in tests
2. `BootLoader.BOOTING = true` — sets booting state
3. `BootLoader.TESTING = true` — skips shutdown hook headless wait
4. `BootLoader.load(...)` — initializes the VM

### Test Annotations
- **`@SkipInheritedTests(methods = {...})`** — skip specific inherited method names
- **`@SkipInheritedTests(tags = {...})`** — skip by test category tag
- **`@ExtendWith(TestSkip.TestSkipExtension.class)`** — enables skip behavior
- **`@ExtendWith(TestData.TestDataExtension.class)`** — provides test data fixtures
- **`@TestCategory.X`** — categorize tests (`Crud`, `Type`, `Boundary`, `Concurrent`, `ReadWrite`, `Nested`, `List`, `Special`)

### Test Infrastructure
- **Test containers**: MySQL (3306), PostgreSQL (5432), MariaDB (3307) — run in CI
- **In-memory**: MongoDB (`mongo-java-server`), MQTT (`moquette-broker`)
- SQLite JDBC available in both compile and test scope

---

## Running

### Run Metatron
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
| Installs ijhttp for HTTP testing | Creates native installers |

Docker build is **disabled by default** (`skipDocker=true` in pom). Enable with `-DskipDocker=false`.

---

## Conventions
1. **Packages**: `studio.phaseshift.metatron.isa.<domain>`
2. **New instruction sets**: `isa/<domain>/<domain>InstSet.java`
3. **New spaces**: Extend `AbstractSpace` or `Space`, register with router
4. **Tests**: Mirror main packages; use `@SkipInheritedTests` for selective skipping
5. **Docker**: Skipped by default. Run `-DskipDocker=false` to build.

---

## MCP (Model Context Protocol)
- WebSocket handler: `mcp_mtron_wsHandler`
- Test client: `scripts/mtron_ws_client.py`

## References
- mtron language skills: `.metatron/skills/mtron/`
- Agent memory: `.claude/agent-memory/metatron-developer/`
