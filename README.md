# metatron

<a href="http://metatron.phaseshift.studio"><img src="http://metatron.phaseshift.studio/images/metatron-character.png" width="200px" align="right"></a>

**Interconnect Heterogeneous Data Sources and Processes**

metatron is a distributed, data-oriented computing language and virtual machine that integrates data sources
using a universal uri address space that is processed by a distributed vm controlled by an object-oriented/functional
language called mtron.

**metatron** is the runtime (VM, machines, spaces, instruction sets); **mtron** is the language that drives it. A value
is an **obj**, an obj lives at a **uri**, and code is built from **inst** with the ring operators `*` (serial
composition) and `+` (parallel branching).

| Language                                        | Runtime       | License  | Version        |
|-------------------------------------------------|---------------|----------|----------------|
| mtron (`isa/m`), functional + fluent, type-safe | jvm, java 21+ | agpl-3.0 | `0.1-SNAPSHOT` |

## Core Concepts

| Concept       | Role                                                                                                                                                               |
|---------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **furi**      | *functional* uri — any datum can reference any other regardless of storage; query params even encode instruction domain/range and space modulators called qprocs   |
| **obj**       | universal value: mono (`bool`, `bytes`, `int`, `real`, `str`, `uri`), poly (`rec`, `lst`, `rel`), call (`inst`, `code`). `noobj` is the 0-object, `#` the 1-object |
| **vid / tid** | for a type, the name / its refinement (`int::T[?>0]@nat`); for a value, its location / constraining type (`nat::29@/usr/marko/age`)                                |
| **machine**   | a computing context containing memory, network, compiler, processor — nested by frame push/pop with multi-threading and resource isolation                         |
| **space**     | a system exposing a subset of the uri address space; registered with machine memory and resolved by uri patterns (`+` and `#` are furi segment wildcards)          |
| **instset**   | an instruction set, discovered at boot via `META-INF/services/` spi, imported when needed, isolated to importing machine                                           |

## The Spaces

Every backend is a `space`; the same language, types, and uri algebra apply to them all.

| Package                          | Backend / domain                                                                  |
|----------------------------------|-----------------------------------------------------------------------------------|
| `isa/m`, `isa/mach`              | core memory + q-procs; machine, compiler, processor, `console::T` ui, serializers |
| `isa/sys`, `isa/web`             | filesystems, serial ports; http; websockets; mcp                                  |
| `isa/tble`, `isa/dcmnt`          | sql (sqlite, mysql, maria, postgresql, rewrite pushdown); mql mongodb             |
| `isa/grph`, `isa/vec`,           | gremlin (tinkerpop/janusgraph); chromadb embeddings                               |
| `isa/iot`, `isa/llm`, `isa/dckr` | mqtt, zigbee2mqtt, llm and decision agents; docker                                |

## Quick Start

```bash
curl -fsSL https://metatron.phaseshift.studio/install.sh | bash   # clone, build, bundle lib/
cd metatron
bin/metatron "[boot=><boot/boot.mtron>,log=>info]"                # boots mach/math/web/llm/tble + console::[=>]
```

There also exists a container image:

```bash 
docker run -p 8555:8555 -p 8777:8777 ghcr.io/phaseshift-studio/metatron:main
````

## Build and Test

JDK 21+ (tests need JDK 23+; CI uses Oracle JDK 26), Maven via the wrapper.

```bash
./mvnw install                    # build + test (~230 JUnit 5 test classes)
./mvnw install -DskipTests        # fast dev loop
./mvnw test -Dtest=memSpaceTest   # one class
./mvnw clean package              # uber-jar: target/metatron-0.1-SNAPSHOT-jar-with-dependencies.jar
```

Agents build in Docker — isolated, no published ports, artifacts land host-owned:

```bash
bin/metatron-docker build [test -Dtest=X | docs [file] | console --steps f]   # agent-safe verbs
bin/metatron-docker image                                                     # runtime image
```

## Running & Connecting

`bin/metatron` runs `lib/metatron.jar` when installed, else compiles and runs `target/classes`. Boot profiles live in
`boot/`. A running VM serves `ws://<host>:8555/mtron` (mtron REPL), `ws://<host>:8555/mcp` and
`http://<host>:8777/mcp` (MCP), and `http://<host>:8777/` (HTTP space root). `bin/wsplus <ws-url>` is a minimal REPL;
`bin/metatron-console` drives a console in a pty to test raw-mode behaviour (arrows, `alt+b`, ctrl-c, history).

## Repository Map

```
src/main/java/studio/phaseshift/metatron/
├── isa/        # instruction-set architecture: m (core language), mach (machine + UI +
│               #   serializers), and sys/web/tble/dcmnt/grph/vec/rdf/iot/llm/dckr
├── furi/       # functional URI (fURI, DataPath, QProc, forms)
├── algebra/    # rewriting (Rewriter, RewriteBuilder, CommonRewrites)
├── docs/ util/ # doc generation; shared utilities
boot/           # boot profiles: instruction sets + spaces mounted in-language
bin/            # launcher, docker loop, console harness, wsplus REPL
docs/           # website adoc sources, design notes, articles; docs/skills = agent skills
dist/           # docker image + curl installer
```

Boot lifecycle: `BootLoader.main` → `load()` creates `/sys`, loads the base instruction sets (`/m`, `/m/mach`), and
ServiceLoader registers every InstSet with the Router → the boot profile mounts spaces and instruction sets in mtron.

## Documentation & Contributing

- **website** — <http://metatron.phaseshift.studio>. only `docs/website/adoc/`, `docs/skills/`, and the instset
  reference (derived from source) are hand-authored; everything else under `docs/website/` is generated.
- **skills** — `docs/skills/metatron/` (vm, type system, ui, rewrite system, machine architecture, distributed
  primitives) and `docs/skills/mtron/` (language reference, instset guides).
- **Contributing** — [CONTRIBUTING.md](./CONTRIBUTING.md), [AGENTS.md](./AGENTS.md): new code must ship
  `@ParameterizedTest` + `@CsvSource` tests extending `AbstractMetatronTest`, reuse existing helpers, and use
  conventional commits. CI runs `mvn install` on `main`; `.github/workflows/docker.yml` publishes the GHCR image.
  licensed under **AGPL-3.0** — see [LICENSE](./LICENSE).
