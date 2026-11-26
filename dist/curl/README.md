# dist/curl — the curl-install distribution

The primary metatron distribution channel:

```bash
curl -fsSL https://metatron.phaseshift.studio/install.sh | bash
```

This directory is the source of `install.sh`. The Maven build copies it to
`docs/website/install.sh` (the `copy-install-script-to-website` execution in
`pom.xml`, bound to `generate-resources`), which is what the URL serves.

## What install.sh does

1. Checks for docker and offers to install it if missing (needed for the
   docker boot profile, not for the build itself).
2. Checks for Java 21+ and Maven (installs via apt if missing, with a prompt).
   Also ensures `patchelf`, needed to fix the jar's musl-built tree-sitter
   native lib on glibc.
3. Clones the repo from GitHub (`phaseshift-studio/metatron`) into `./metatron`.
4. Builds the uber-jar (`mvn clean install -DskipTests`).
5. Bundles the built jar to `<repo>/lib/metatron.jar` so the runtime needs no
   Maven.
6. Tells you to run `bin/metatron`.

## The runtime model

`bin/metatron` is dual-mode:

- **Installed (`lib/metatron.jar` present)**: launches the uber-jar directly
  via `java <flags> -jar lib/metatron.jar` — no Maven, no source tree, no
  recompile.
- **Dev (no `lib/`)**: the classpath loop — resolves dependencies, recompiles
  on source change, launches from `target/classes`.

The install produces the first mode; a developer checkout uses the second.

## Serving / deployment

- The served `install.sh` at `https://metatron.phaseshift.studio/install.sh`
  is the committed `docs/website/install.sh`, kept in sync by the Maven copy
  above.  If you edit `dist/curl/install.sh`, rebuild (`mvn generate-resources`)
  so the website copy updates.
- No release choreography: the installer always pulls the latest `main` and
  builds from source.  Versioned releases just tag `v*` (which also triggers the
  container build — see `dist/docker/README.md`).

## Docker boot profile

`boot/spaces.boot.mtron` is the install's docker profile: every space backend runs as a container aggregated
through dckrspace, so it needs docker on the system, and an ollama for the `embed:#` space's writes (below):

```bash
bin/metatron -b boot/spaces.boot.mtron "[log=>info]"
```

| space      | backend container | endpoint                                |
|------------|-------------------|-----------------------------------------|
| `metatron:#` (tble) | `mariadb:11.2` | `127.0.0.1:3308`                        |
| `g:#` (grph) | `janusgraph/janusgraph:1.1.0` (gremlin ws) | `ws://127.0.0.1:8182/gremlin` |
| `embed:#` (vec) | `chromadb/chroma:1.5.3` | `http://127.0.0.1:8025/api/v2`       |

Ports, images, credentials and more are boot args — see the file's header.

The `embed:#` space embeds each document it stores with an ollama model, named by the boot args `ollama/host`
(default `http://127.0.0.1:11434`) and `ollama/llm` (default `qwen3-embedding:4b`). The boot builds the model at
`/sys/model/embed` when it runs, so the ollama must answer then, with that model pulled (`ollama pull qwen3-embedding:4b`).
The space mounts without one, and a write that brings no embedding fails until a model is written to `/sys/model/embed`.

## Alternatives

- **Container**: `ghcr.io/phaseshift-studio/metatron` — see `dist/docker/`.
- **Remote console**: connect to any running instance via `bin/wsplus` or
  `boot/remote_console.mtron` — see `bin/README.md`.
