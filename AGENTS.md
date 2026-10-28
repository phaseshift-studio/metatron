# metatron — AGENTS.md

A distributed data-oriented computing language and virtual machine built in Java. Two key terms:

- **metatron** (lowercase): the runtime system / VM environment
- **mtron** (lowercase): the functional programming language (like Java to JVM)

This file holds the rules every session needs. The long references live in `docs/agents/` and are linked where they apply.

---

## Strict Rules

* Under no circumstance should you perform any git operations. Do not check out, do not stash, do not merge, do not
  commit, and do not push. Git is off limits.

## Memory — Hindsight is the shared, harness-agnostic store

This project is worked on from several agent harnesses (Claude Code, the DeepSeek harness, Codex, …). **Hindsight is
the one project memory they all share** (MCP server `hindsight`, bank `coding-agent::metatron`). A harness's own
memory — Claude Code's `~/.claude/projects/*/memory/`, `.remember/`, Codex notes — is visible to that harness alone.
Treat Hindsight as the source of truth, and write to it.

* **Read before acting.** Call `hindsight_search_knowledge_pages(query)` first: before fixing a reported bug, writing a
  test, building something new, explaining why something is the way it is, or committing. Use
  `hindsight_read_knowledge_page(id)` for a page worth reading in full, and `hindsight_reflect(query)` for the
  reasoning behind a past decision. What it returns is a past record: check a claim that something works against
  the code before relying on it.
* **Write what you learn.** Ingest design rulings, findings, conventions and environment facts with
  `hindsight_ingest_document(title, content)`: one document per topic, a specific title, dated, exact values kept.
  Search first so you extend rather than duplicate. When you verify that a Hindsight fact is stale, ingest a
  `Correction: <topic>` document stating what it claimed, what is true now, and the evidence.
* **Track features.** Call `hindsight_capture_initiative(title, summary)` right before implementing a new feature or
  capability. Call it again with `relates_to_page_id` whenever the feature's goal or scope changes. Skip bug fixes
  and chores.
* **Keep your harness's own memory too** if it has one — just never only there. Session transcripts may be captured
  automatically, but a distilled ruling or finding needs an explicit write.
* **Credit it.** When Hindsight content shapes a reply, say so (a `🧠 From Hindsight memory (<page>)` note), so the
  user can tell recalled facts from fresh reading.
* Some harnesses load the `hindsight_*` tools on demand (in Claude Code, via `ToolSearch`). If they are missing, the
  server is not connected: say so rather than silently skipping memory.

## Build & Test

**JDK 25+** is required to compile and run, and tests must run on JDK 25: the pom's surefire `argLine` uses
`--sun-misc-unsafe-memory-access=allow`, a JDK 23+ flag. CI uses Oracle JDK 25.

```bash
./mvnw install                                    # build with tests
./mvnw install -DskipTests                        # fast dev loop
./mvnw test                                       # run all tests
./mvnw test -Dtest=memSpaceTest                   # single test class
./mvnw test -Dtest='!fsSpaceTest'                 # exclude tests (CI pattern)
./mvnw clean package                              # uber-jar: target/metatron-0.1-SNAPSHOT-jar-with-dependencies.jar
```

**Agents build and test in docker** with `bin/metatron-docker build …` (no published ports, throwaway containers):
a live server owns the host's 8555/8777 and is rebuilt constantly, so host-side VM boots collide with it.

Those builds are **load balanced** across this workstation and the ginger build server. With
`METATRON_BUILD_REMOTE=1` the isolated `build …` verbs are routed through `bin/metatron-remote`,
which picks the least-loaded machine (ginger on a tie) and caps concurrent remote builds. `run`,
`dev` and `image` always stay local — they publish ports or need the jar in this tree. Force a
local build with `METATRON_BUILD_REMOTE=0`.

Hosts, the cap and the delegation flag live in **`bin/test/metatron-remote.env`** (copy it from
`bin/test/metatron-remote.env.example`; gitignored). Both scripts read it, so editing that one file changes
behaviour for every session on this checkout — no exports, no recreate.

Pin one machine when you want a specific box (it skips the load comparison):
`bin/metatron-remote --host ginger.local mvn clean install`. Any command instead of a verb runs
in the build container, so it gets JDK 25 and the dependency cache.

`bin/metatron-remote status` shows what is running on both machines (with each build's phase),
the recent history and the load on each. History lands in `~/.metatron-builds.log`.

```bash
bin/metatron-docker build                             # package (skip tests)
bin/metatron-docker build test -Dtest=memSpaceTest    # tests in the container
bin/metatron-docker build docs [dir|file.md]          # docs pipeline (MarkdownRunner --html)
bin/metatron-docker build console --steps f.steps     # drive a real console in a pty
bin/metatron-docker help                              # the full reference
```

Rules that bite:

* **Read `Tests run:`, not the exit code.** `Tests run: 0` is not a pass, and `-Dtest=Class#` with an empty method
  silently runs the whole suite.
* **Never hand-compile with `javac` and drive JUnit as a substitute for a build.** That bypasses the pom (surefire's
  `argLine`, resource filtering, the test lifecycle). If `./mvnw` fails, fix the invocation and say so.
* **Never kill the live server's java process or the `metatron` dev container.** `bin/metatron-docker dev` recreates
  the container named `$METATRON_CONTAINER`, so never run it while that container is the server under test.
* `DRY_RUN=1 bin/metatron-docker <anything>` prints the docker/mvn commands it would run; check its `-v` paths before
  trusting a result from inside the dev container.

The toolchain in detail (container vs host, the dev container, the docker loop's mounts and caches, JVM flags) is in
[docs/agents/build-environment.md](docs/agents/build-environment.md). The pty console harness and file-trace debugging
of a live console are in [docs/agents/console-harness.md](docs/agents/console-harness.md).

## Code Style

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
- **Never shape-inspect a read result.** `Objs` — a *Java* notion; in mtron a multiplicity is a tid coefficient, e.g.
  `int{4}::T` — is a stream, and `stream()` is defined on `Obj` itself, so `obj.stream().forEach(...)` covers one match
  and many identically with no `isLst()`/`isRec()`/`isRel()`/`instanceof Poly` branching. Sort a read yourself when
  position matters. See the `stream()` note under Key Concepts.

## Tests

JUnit 5. Every test class extends `AbstractMetatronTest`, which boots and shuts down the VM.

* **Prefer `@ParameterizedTest` + `@CsvSource` with `delimiter = '%'`.** Each row is a self-contained mtron
  scenario, so a corner case is one CSV line. Don't name the parameterized test. Use a plain `@Test` only for
  multi-step orchestration (concurrency, complex setup/teardown).
* `@TestData` preloads data or configures metatron state before the test method runs.
* Assert with the `AbstractMetatronTest` helpers — `checkCodeParseApply`, `checkCodeEvaluate`, `checkEquality`, and
  `<ERROR>` as an expected value for the fail path — rather than `assertTrue/False`, which tell the reader nothing.
* The canonical example of the style is `studio.phaseshift.metatron.isa.m.mInstSetTest`.

The full style guide, the `@Training` annotation (tests as LLM fine-tuning data), the test bootstrap and the test
containers are in [docs/agents/testing.md](docs/agents/testing.md).

## Running

```bash
bin/metatron "[boot=><boot/boot.mtron>,log=>info]"
```

`bin/metatron` supplies the required JVM flags and picks the launch mode: a dev checkout runs `BootLoader` from
`target/classes` plus the cached dependency classpath, and an installed deployment (`lib/metatron.jar`) runs the
uber-jar. Both loop on `EXIT_RESET`, so `:reset` reboots the VM in place. Do not run the jar directly without the
flags (they are listed in [docs/agents/build-environment.md](docs/agents/build-environment.md)).

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

**`stream()` — one or many, the same code, on the Java side**

A wildcard space read that matched several things hands you an `Objs` in Java. It is not a collection to be unwrapped:
`stream()` is defined on `Obj` itself, and for `Objs` it yields the members while for **every other type it yields the
obj alone**. So one code path covers "one match" and "many matches" with no shape inspection:

```java
objs(jnt(1), jnt(2), jnt(3)).stream().forEach(...);   // many
jnt(1).stream().forEach(...);                         // one — the same code
```

That uniformity is not luck: `MObjs.objs(...)` collapses an empty collection to `noobj()` and **a single element to that
element itself**, so a one-member `Objs` is never constructed.

**`objs` is a Java notion, not an mtron one.** There is no `objs` in the mtron vocabulary: a multiplicity there is a
**coefficient in the tid** — `int{4}::T` is four ints — and *that* is what an `Objs` is in Java (its tid is the sum of
its members' tids, `tid.big()`). So when reading mtron, look for the coefficient, not a collection type.

Reach for `stream()` whenever you are unsure of the source, rather than branching on `isLst()` / `isRec()` / `isRel()` /
`instanceof Poly`. Three things that follow, each of which has bitten:

- An `Objs` from a read can **inherit a member's `tid` and `vid`**, so dispatching on `tid` after a read is unsafe —
  dispatch on the fields the rec actually carries, or let `stream()` do the walking.
- A `+` read's leaves are **rels** (`vid => obj`), so the ledger read idiom unwraps both layers:
  `Router.readFromSpace(path.extend("+/")).stream().map(Obj::asRel).map(Rel::second)`
  (see `SpaceChatSessionStore.sessionRels`, `LedgerUtil.readMessages`).
- A read does **not** promise order. When position matters — e.g. a tool result must sit immediately after the request
  it answers — sort by ledger id yourself.

**URI components**:

- wildcards: `+` = single segment, `#` = multi-segment (MQTT-style)
- q procs: `?incq` (auto-increment), `?subq` (pubsub), `?docq` (documentation associated with uri), etc.
- dom/rng: `inst?a<=b()` is an instruction that maps objs of type `b` to objs of type `a` (note reverse arrow `<=`).
  ultimately compiles to the URI `inst?rng=a&dom=b`: query components render in the canonical order `rng`, `dom`,
  `args`, then the remaining keys in reverse alphabetical order, so one fURI has exactly one spelling.

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

## Conventions

1. **Packages**: `studio.phaseshift.metatron.isa.<domain>`
2. **New instruction sets**: `isa/<domain>/<domain>InstSet.java`
3. **New spaces**: Extend `AbstractSpace` or `Space`, register with router
4. **Tests**: Mirror main packages; prefer `@ParameterizedTest` + `@CsvSource` with `%` delimiter; use mtron string
   expressions in CSV rows; leverage `checkCodeParseApply`/`checkCodeEvaluate`/`checkEquality` from
   `AbstractMetatronTest`; use `@SkipInheritedTests` for selective skipping
5. **Docker**: Image built & published to GHCR via `.github/workflows/docker.yml` (not part of the Maven build).

---

## Skills and docs

* The agent skills live in `docs/skills/` (markdown; `.metatron/skills` links there). The hand-authored doc sources
  are exactly three: `docs/skills/`, the human-oriented adocs in `docs/website/adoc/`, and the instruction-set API
  reference derived from the instset source. **Everything else under `docs/website/` is generated and overwritten on
  every build — never edit it by hand.**
* When you create, rename or significantly change a Java class documented in a skill reference, update that
  reference. Every example in a skill doc must pass the docs pipeline (`bin/metatron-docker build docs <file.md>`);
  `fail::` lines in the processed output are broken examples.
* Create or rewrite `.md` docs only when asked, or when the change is mechanical (a new class in a package map).

Which reference to update for which class, and how: [docs/agents/skill-docs.md](docs/agents/skill-docs.md).

## MCP, CI and tooling

* `bin/wsplus` is the WebSocket REPL and one-shot client (`bin/wsplus -e '<expr>' ws://<host>:8555/mtron`). It is the
  only wsplus anyone edits; the copies under `docs/website/skills/*/scripts/` are build products.
* CI: `.github/workflows/maven.yml` builds and tests pushes and PRs to `main`; `.github/workflows/docker.yml` builds
  the image and publishes it to GHCR.

The MCP handler, wsplus bundling, Python packages in the dev container, CI and tool quirks are detailed in
[docs/agents/build-environment.md](docs/agents/build-environment.md).

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

## XERJ (code search)

This project uses XERJ for reference coding: when you are about to write code in
unfamiliar territory, retrieve how somebody already solved it instead of guessing.

```sh
sh .xerj/start-node.sh &        # the node first — xerj mcp does NOT start one
xerj search --prefix metatron "<plain words>"   # this repo's own sources
xerj search --prefix ref      "<plain words>"   # the cloned reference projects
xerj def --prefix metatron "<Symbol>"           # exact go-to-definition
```

`--prefix` is not optional here: `metatron-*` is this repository and `ref-*` is
`ref/` (Graal/Truffle, JikesRVM, TinkerPop, RDF4J, LangChain4j); the tool's default
prefix `ax` is empty, and neither `xerj search` nor `xerj def` will tell you that.
Cite file:line for anything you rely on. Setup, corpora and caveats:
[.xerj/README.md](./.xerj/README.md).
