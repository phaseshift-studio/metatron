# metatron — AGENTS.md

A distributed data-oriented computing language and virtual machine built in Java. Two key terms:

- **metatron** (lowercase): the runtime system / VM environment
- **mtron** (lowercase): the functional programming language (like Java to JVM)

---

## Strict Rules

* Under no circumstance should you perform any git operations. Do not check out, do not stash, do not merge, do not
  commit, and do not push. Git is off limits.

## Build & Test

### Commands

```bash
# Build with tests
./mvnw install

# Skip tests (fast dev loop)
./mvnw install -DskipTests

# Run all tests
./mvnw test

# Single test class
./mvnw test -Dtest=memSpaceTest

# Exclude tests (CI pattern)
./mvnw test -Dtest='!httpSpaceTest,!fsSpaceTest'

# Package uber-jar
./mvnw clean package
# Output: target/metatron-0.1-SNAPSHOT-jar-with-dependencies.jar
```

### Requirements

- **Java**: JDK 21+ to compile and run (CI uses Oracle JDK 24, jDeploy uses Temurin JDK 25)

### Build Environment (this container)

The root filesystem is read-only (`HOME=/root`, `/usr` not writable), so the toolchain lives inside the repo under
`.build/` (git-ignored):

- **`./mvnw` is patched to self-configure — just run it.** When `JAVA_HOME` is unset it falls back to `.build/jdk`
  (Temurin 24, matching CI), keeps Maven caches under `.build/m2/`, and passes `.build/m2/settings.xml` which redirects
  the local repository into the workspace. No env setup needed.
- **`bin/metatron` is patched the same way** (uses `./mvnw` and
  `.build/jdk/bin/java`).
- `.build/jdk21` exists for reference, but **tests must run on the default JDK 24**: the pom's surefire `argLine`
  includes `--sun-misc-unsafe-memory-access=allow`, a JDK 23+ flag that JDK 21 rejects (this is why CI uses JDK 24).
- **`bin/` layout** — commands you *type* stay flat in `bin/` (`bin/metatron` is the launcher — the old
  `bin/metatron-dev` is retired — plus `bin/metatron-docker` for every docker job and `bin/metatron-console` for the
  pty console harness); sourced shell helpers live in `bin/lib/`; assets a command consumes live in `bin/test/`
  (e.g. `bin/test/console-smoke.steps`).

### Docker Build Loop (the agent standard)

Agents doing builds, tests, or docs-pipeline work should run them in docker rather than on the host:
a live server (host VM or the `metatron` dev container) owns the host's 8555/8777 and gets rebuilt/restarted constantly,
so host-side VM boots collide with it. The loop is isolated — no published ports, ephemeral unique-named containers,
artifacts land back host-owned.

```bash
bin/metatron-docker build                     # mvn package (skip tests)
bin/metatron-docker build test -Dtest=memSpaceTest  # test run in the container
bin/metatron-docker build docs [dir|file.md]        # MarkdownRunner --html (site html via HTMLMarkdownSerializer)
bin/metatron-docker build console --steps f.steps   # drive a real console in a pty (see below)
bin/metatron-docker build shell '<cmd>'               # raw shell in the container
bin/metatron-docker build image                     # rebuild the build image
bin/metatron-docker help                            # the full reference

# the other two jobs — neither is isolated, know what you are touching
bin/metatron-docker                                 # run a boot file in a disposable container (ports published)
bin/metatron-docker dev [--no-build]                # dev loop: package in docker -> app image -> run
```

- Every docker entry point is one script, `bin/metatron-docker`; the job is its subcommand. The three
  jobs were formerly `bin/metatron-docker` / `bin/metatron-dev-docker` / `bin/metatron-build-docker`; the
  latter two are **retired** — `bin/metatron-docker dev` and `bin/metatron-docker build …` replace them (a note that
  still names them is stale).
- **`build …` is the agent-safe set**: no published ports, throwaway containers.
- `image` and `dev` package the uber-jar **inside** `metatron-build:24` and then build `metatron:dev`, whose
  Dockerfile COPYs `target/metatron-*-jar-with-dependencies.jar` directly — there is no staging copy in the repo root,
  and `.dockerignore` whitelists exactly that one file (plus `boot/`, `conf/`, the skills docs and `bin/wsplus`) into
  the
  build context. So docker is the only host requirement — no host maven/JDK — and the jar comes from the same JDK24
  image the test loop uses. Order matters for the low-level path: package first (`mvn -DskipTests package`), then
  `docker build`.
- `run` and `dev` publish 8555/8777 (they collide with a live server by design); `NO_PORTS=1` makes `run`
  publish nothing. **`dev` removes and recreates the container named `$METATRON_CONTAINER`
  (default `metatron`)** — never run it while that container is the server under test.
- `DRY_RUN=1 bin/metatron-docker <anything>` prints the docker/mvn commands instead of running them.
- Image `metatron-build:24` auto-builds on first use from `dist/docker/Dockerfile.build`
  (maven + Temurin 24 + **patchelf** — patchelf is mandatory: the uber-jar ships a musl-built tree-sitter `.so` that
  `ObjJavaSerializer` re-points at glibc on load; without it the VM dies with `UnsatisfiedLinkError` and parks forever
  on the `/sys/thread/main` latch instead of exiting — a hung, non-terminating java process is the symptom).
- The repo mounts at `/work`; the container runs as the host user; **no ports are published** — the VM's 8555/8777 exist
  only in the container's netns, never on the host.
- Maven cache is shared with `.build/m2/repository`; every run gets a unique container name — never hardcode a docker
  `--name` around these (a stale container from a killed run collides).
- Docker builds **never touch the repo's `target/`**: the container's `/work/target` is bound to `.docker-target/` at
  the repo root (auto-ignored by .gitignore's `.*`; override with `METATRON_BUILD_TARGET`). Fs (host) builds and
  docker builds can therefore run without clobbering each other's artifacts. Jobs that hand an artifact off (`build`'s
  package verb, `image`, `dev`) copy the uber jar back into `target/` afterwards — the Dockerfile COPY
  keeps working. Docker test reports land in `.docker-target/surefire-reports/`.
- Docs pipeline (`docs` subcommand): `MarkdownRunner` evaluates the ` ```mtron_pre ` blocks of
  `docs/skills/*` and writes the single processed copy via `.metatron/skills`, which is a **symlink to
  `docs/website/skills`**; `MarkdownRunner --html` renders the site HTML (body conversion via
  `HTMLMarkdownSerializer`, page chrome assembled in the runner — this replaced the deleted
  `docs/SkillHtmlRenderer`). `docs` also takes a single `.md` file (probe mode): a
  `docs/skills/...` file lands in its `.metatron/skills/` slot, any other file goes to `_probe/`. Rule: every example in
  a skill doc must be verified through this pipeline before shipping —
  `fail::` lines in the processed output are broken examples.
- Never kill the live server's java process or the `metatron` dev container.

### Driving the console in a pty (`bin/metatron-console`)

Some behaviour only exists on a terminal: raw-mode keystrokes (`alt+b`, `ctrl-c`, arrows), the jline
keymap, history, type-ahead, prompts, widgets, and the console's input handoff. A pty is the only way
to exercise it, so `bin/metatron-console` boots a metatron VM with a console **in a pty** (python
`pty.fork` — no `docker -t` needed), plays a step script at it, asserts on what came back, and prints
a timestamped transcript.

```bash
# isolated — the agent standard, same container as the build/test loop (no ports)
bin/metatron-docker build console --steps bin/test/console-smoke.steps
bin/metatron-docker build console --run 'WAIT_CONSOLE; SEND 1; WAIT ==>1'

# on the host (needs python3)
bin/metatron-console --steps bin/test/console-smoke.steps
bin/metatron-console --help           # every flag + the full step reference
bin/metatron-console --list-keys      # KEY names: alt+b, ctrl+c, up, tab, shift+tab, ...
```

**What it gives you**

| Capability | Detail                                                                                                                                                                                                                    |
|------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| boot       | default profile `boot/console.boot.mtron` — console-only, **no ports bound**, ~3s boot, safe beside the live server (`--boot` for any other profile)                                                                      |
| safety     | the harness *refuses* a boot file that mentions 8555/8777 unless `--allow-ports`; the VM runs in a throwaway temp cwd (`--cwd repo` to override) so the user's `.metatron.history` and other repo state are never touched |
| versions   | `--from jar` (default, the uber jar from `bin/metatron-docker build`) or `--from classes` (target/classes + `.mtron-classpath` → no jar build needed for a dev loop)                                                      |
| keys       | `KEY alt+b`, `KEY ctrl+c`, `KEY up`, `TYPE` (no Enter), `SEND` (with Enter), `RAW b"\x1bb"` escape hatch                                                                                                                  |
| assertions | `WAIT_CONSOLE`, `WAIT <text> [s]`, `WAIT_SINCE <mark> <text> [s]`, `SINCE <mark> <text>`, `SILENT <mark> <text> [s]`, `MARK`, `SLEEP`, `ECHO`, `NOTE`, `ELAPSED`                                                          |
| matching   | case- **and** ANSI-insensitive, so `WAIT background` matches a colourised banner; failures print the transcript since the nearest mark                                                                                    |
| output     | timestamped transcript, `--transcript FILE` (ANSI-stripped), `--raw FILE`, `--quiet`, `--cols/--rows`, `--timeout`; **exit code is non-zero if any check failed**                                                         |

`bin/test/console-smoke.steps` is the worked example and the console regression suite — evaluation,
history recall via `KEY up`, type-ahead kept while a job runs, `alt+b` backgrounding (asserting the
prompt returns *before* the job finishes and the job's result still lands afterwards), ctrl-c
stopping a job, and the `[q]` cancel offer. Run it after any console/UI change, and copy it as the
starting point for your own scenario (`--steps my.steps`).

**Cautions**

- An image built before python3 was added cannot drive a console — the wrapper says so; rebuild with
  `bin/metatron-docker build image`.
- `--from classes` needs `target/classes`, `src/main/resources` and `.mtron-classpath` (run
  `bin/metatron` once, or `./mvnw compile` + `./mvnw dependency:build-classpath -Dmdep.outputFile=.mtron-classpath`).
- Assert on text, never on cursor position or colours (the pty is 110x40 by default; the console
  renders differently at other sizes).
- The harness never publishes ports and boots a portless profile by default — keep it that way, and
  never point `--boot` at a profile that binds the live server's ports.

### Code Style

- In general, adopt existing patterns in the codebase.
- Use American English spelling in variables and documentation (e.g. color not colour).
- `final` all variables and method arguments. Rarely should non-final variables be used.
- `this.` should be used when referencing fields.
- Short text lines in log messages, terminal output, etc. should NOT capitalize the first letter of the sentence. In
  general, lowercase text.
- Leverage existing `XXXUtil`, `XXX.Helper`, etc. style static method providers for common algorithms.
- Don't use "Hello World" or "foo bar" in examples and test cases. Come up with something more clever and unique.
- Don't create new terms for rec keys unless you absolutely have to. Instead, review `Tokens.java` as that is our
  running list of terms used across the code base. Best to reuse existing terms instead of creating an ever-growing set
  of synonyms.

### Test Framework

- **JUnit 5** (Jupiter), surefire 3.5.5
- Test logging: `src/test/resources/logback-testing.xml`
- All tests extend `AbstractMetatronTest` which handles boot/shutdown

### Test Styles — `@ParameterizedTest` Preferred

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

### `@Training` — Multi-Map CSV Mappings

The `@Training` annotation enables a single `@CsvSource` row to produce multiple training data entries by mapping
different column pairs as lhs→rhs (expression→result). This is used by `UnslothTrainingDatasetExtractor` to generate LLM
fine-tuning data.

**When to use:** a test method that evaluates the same expression under different mappings (e.g., parsed vs rendered,
code vs value, mtron vs JSON).

```java
@Training(
        value = "Evaluate this mtron expression",      // description prefix
        mapDesc = {"lhs evaluates to rhs", "mtron evaluates to JSON"}, // per-map descriptions
        map1 = {0, 1},   // columns 0→lhs, 1→rhs  (first mapping)
        map2 = {2, 3}    // columns 2→lhs, 3→rhs  (second mapping)
)
```

Each CSV row with `delimiter='%'` produces one entry per active map (`map1`, `map2`, `map3`). A map is active when its
first element is not `-1` (the default). With `map1={0,1}` and
`map2={2,3}`, a row like `a%1%b%2%comment` generates:

```jsonld
{
  "instruction": "Evaluate this mtron expression: lhs evaluates to rhs",
  "input": "a",
  "output": "1"
}
{
  "instruction": "Evaluate this mtron expression: mtron evaluates to JSON",
  "input": "b",
  "output": "2"
}
```

Up to three maps (`map1`, `map2`, `map3`) are supported. See `UnslothTrainingDatasetExtractor`
for the extraction logic and `.metatron/skills/mtron/references/unsloth-training-mtron.md`
for the full training pipeline.

- **Leverage static helpers** from `AbstractMetatronTest` for assertion logic:
    - `checkCodeParseApply(LOG, code, expected)` — parse + apply mtron, assert result
    - `checkCodeEvaluate(LOG, evaluate, fetch, expected)` — evaluate, fetch read-back, assert
    - `checkEquality(LOG, a, b, equals)` — compare two objs with log output
    - `<ERROR>` as an expected value triggers the fail-expected assertion path.
    - avoid `assertTrue/False` assertions as they provide little information back to user on failure.

### Canonical Test Examples

The best example of the desired test-style can be found at:
`studio.phaseshift.metatron.isa.m.mInstSetTest`

### Test Bootstrap (Important)

Every test class must extend `AbstractMetatronTest`. In `@BeforeAll`:

1. `TypeCheck.disable(TypeCheck.code_resolve)` — disables full code resolution requirement in tests (i.e. dynamic
   resolution is 'ok')
2. `BootLoader.BOOTING = true` — sets booting state
3. `BootLoader.TESTING = true` — skips shutdown hook headless wait
4. `BootLoader.load(...)` — initializes the VM

### Test Infrastructure

- **Test containers**: MySQL (3306), PostgreSQL (5432), MariaDB (3307) — run in CI
    - a custom container example exists with ChromaDB.
- **In-memory**: MongoDB (`mongo-java-server`), MQTT (`moquette-broker`)
- SQLite JDBC available in both compile and test scope

---

## Running

### Run metatron

```bash
# bin/metatron script (recommended)
bin/metatron "[boot=><boot/boot.mtron>,log=>info]"
```

`bin/metatron` supplies the required JVM flags and picks the launch mode: a dev checkout runs `BootLoader` from
`target/classes` plus the cached dependency classpath, an installed deployment (`lib/metatron.jar`) runs the uber-jar.
Both loop on `EXIT_RESET`, so `:reset` reboots the VM in place. Do not run the jar directly without these flags.

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
│   ├── dckr/               # Docker              
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

**Spaces** — fundamental data containers, registered in `Router` upon construction. Registration is automatic when
extending `AbstractSpace`. Spaces are indirectly read/written from/to via the JVM global `Router`. `*<uri>` dereference
and `<uri> -> <obj>` reference).

**InstSet** (Instruction Sets) — discovered via `META-INF/services/` SPI. New sets go under
`isa/<domain>/<domain>InstSet.java`.

**Obj** — universal type system.

- mono types: `bool`, `bytes`, `int`, `real`, `str`, `uri`.`
- poly types: `rec` (record/map), `lst` (list),
- call types: `inst`, `code`

**IMPORTANT** `tid` vs `vid`:

- for a type: `vid` is the type's name and `tid` is the type's refinement.
    - `int::T[?>0]@nat`. `vid = nat`,
      `tid = int`.
- for a value: `vid` is the value's location in space and `tid` is the type constraining the value.
    - `nat::29@/usr/marko/age`. The `int` is a `nat::T` (tid) and it's located at `/usr/marko/age` (vid).
    - `*/usr/marko/age` returns `nat::29`.

**URI components**:

- wildcards: `+` = single segment, `#` = multi-segment (MQTT-style)
- q procs: `?incq` (auto-increment), `?subq` (pubsub), `?docq` (documentation associated with uri), etc.
- dom/rng: `inst?a<=b()` is an instruction that maps objs of type `b` to objs of type `a` (note reverse arrow `<=`).
  ultimately compiles to the URI `inst?dom=b&rng=a`.

**mtron language**

- Don't get overwhelmed by the barrage of syntax sugar shortcuts used in examples, docs, and test cases. Realize that
  every mtron expression (when desugar'd) is a fluent chain of nested functions (called instructions). e.g.
  `f(a(b(1),2).g().h(3)`.
- Append `.explain()` to any expression to see it's unsugar'd form.

### Boot Loader Lifecycle

1. `BootLoader.main()` → parses args, optionally loads boot file
2. `BootLoader.load()` → creates `/sys` space, loads base instruction sets (`/m`, `/m/mach`)
3. `BootLoader.close()` → teardown hook

---

## CI/CD

|                         `.github/workflows/maven.yml` |                 `.github/workflows/docker.yml` |
|------------------------------------------------------:|-----------------------------------------------:|
|                                     Push/PR to `main` | Push to `main`, `v*` tags, `workflow_dispatch` |
|                                         Oracle JDK 24 |                      Temurin JDK 25 (uber-jar) |
| `./mvnw install -Dtest='!httpSpaceTest,!fsSpaceTest'` |     `mvn package` → build & push image to GHCR |

Docker images are built and published to GHCR (`ghcr.io/phaseshift-studio/metatron`) by the
`docker.yml` workflow — the Maven build itself does not touch Docker.

---

## Conventions

1. **Packages**: `studio.phaseshift.metatron.isa.<domain>`
2. **New instruction sets**: `isa/<domain>/<domain>InstSet.java`
3. **New spaces**: Extend `AbstractSpace` or `Space`, register with router
4. **Tests**: Mirror main packages; prefer `@ParameterizedTest` + `@CsvSource` with `%` delimiter; use mtron string
   expressions in CSV rows; leverage `checkCodeParseApply`/`checkCodeEvaluate`/`checkEquality` from
   `AbstractMetatronTest`; use `@SkipInheritedTests` for selective skipping
5. **Docker**: Image built & published to GHCR via `.github/workflows/docker.yml` (not part of the Maven build).

---

## MCP (Model Context Protocol)

- WebSocket handler: `mcp_mtron_wsHandler`
- Test client: `.metatron/skills/mtron/scripts/mtron_ws_client.py`

## Skill Reference Docs

The project maintains two skill sets under `.metatron/skills/`:

| Skill        | Path                         | Purpose                                                               |
|--------------|------------------------------|-----------------------------------------------------------------------|
| **metatron** | `.metatron/skills/metatron/` | VM architecture, type system, boot process, MCP, UI architecture      |
| **mtron**    | `.metatron/skills/mtron/`    | Language reference, examples, training pipeline, data-source adapters |

Each skill has:

- `SKILL.md` — index with a **References** section listing all `.md` docs
- `references/*.md` — detailed topic docs (architecture, patterns, language spec)
- `scripts/` — Python utilities (MCP client, training)
- `assets/` — datasets, READMEs

### When to Update

Whenever you create, rename, or significantly change a Java class that is documented in a reference `.md`, update the
corresponding file:

| You changed…                                                              | Update…                                                                                                    |
|---------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------|
| A widget or UI class (`uiInstSet`, `TreeWidget`, `PanelWidget`, any tool) | `.metatron/skills/metatron/references/ui-instset-java.md`                                                  |
| A type system class (`Type`, `MType`, `Inst`, `Code`)                     | `.metatron/skills/metatron/references/type-system-java.md` or `…-mtron.md`                                 |
| A rewrite class (`Rewriter`, `RewriteBuilder`)                            | `.metatron/skills/metatron/references/rewrite-system-java.md`                                              |
| A space class (`tbleSpace`, `fsSpace`)                                    | `.metatron/skills/metatron/references/tble-space-java.md` or create a new space doc                        |
| mtron language syntax or semantics                                        | `.metatron/skills/mtron/references/mtron-language-reference.md`                                            |
| An MCP server/client class                                                | `.metatron/skills/metatron/references/mcp-mtron.md` or `.metatron/skills/mtron/references/mcp-server-*.md` |
| A new data-source integration (`dckrSpace`, etc.)                         | `.metatron/skills/mtron/references/` — create a new doc                                                    |

### How to Update

1. **Add new classes to the package map** — every reference doc has a tree listing of Java files in the relevant
   package. Add new files there.
2. **Add new types to relevant tables** — e.g., the Tool→Widget dependency table in
   `ui-instset-java.md`, or type registration tables elsewhere.
3. **Update counts, examples, or signatures** if the change invalidates them.
4. **Create a new `references/<topic>.md`** when adding a substantial new subsystem (a new space backend, a new
   instruction set family, etc.). Follow the `---` YAML frontmatter convention with `name` and `description` fields. Add
   the new file to the **References** list in the parent `SKILL.md`.
5. **User-directed:** only create or update `.md` files when the user explicitly asks you to, or when the change is
   clearly mechanical (adding a new class to an existing package map). For substantive documentation rewrites, confirm
   with the user first.

### Existing Docs

- **metatron skill**: `references/` covers the type system (Java + mtron), UI architecture, rewrite system, tbleSpace,
  and MCP client/servers.
- **mtron skill**: `references/` covers the language reference, MCP server architecture, math instructions, HTTP
  fetching, dckrSpace, answer-questions patterns, and the Unsloth training pipeline.

---

## Tool Quirks — Write/Edit Truncation Bug (2026-07-03, fixed upstream)

The Cowork-mode Edit and Write tools **silently truncate files when the content exceeds the pre-edit file's byte size**.
The tool returns success even though the file is corrupted. Shell heredocs (`cat > f <<HEREDOC`) also fail for large
files with the same symptom.

**Reliable workaround:** Use Python `pathlib` for any file write 200+ lines (or ~4KB+) —
`python3 -c "import pathlib; pathlib.Path('/abs/path').write_text(content)"`. Shows ~100% success in user logs.

**Verification:** Always check `wc -c <file>` after writing/editing large files to confirm integrity. Line counts are
unreliable since truncation can strip content unpredictably (missing loop variables, broken expressions, etc.).

---

## Before Submitting

**Read `CONTRIBUTING.md`** — it's the single source of truth for PR expectations, coding standards, and contribution
guidelines. Key points:

- New code **must** have test cases (`@ParameterizedTest` + `@CsvSource` preferred)
- Reuse existing helpers (`XXX.Helper`, `fURI` methods, `CommonUtils`) — no algorithm duplication
- Use `MTronException.of()` for all exceptions — never raw `RuntimeException`
- Follow naming conventions (`tble`, `dcmnt`, `grph`, `vec` — not full words)
- Conventional commit format: `type(scope): description`
- Reference a GitHub issue (`Closes #N` or `Refs #N`)

See [CONTRIBUTING.md](./CONTRIBUTING.md) for full details.
