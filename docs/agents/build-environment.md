# Build environment, docker loop and tooling — agent reference

Moved out of [AGENTS.md](../../AGENTS.md) on 2026-10-21 so that file holds only the rules every session needs; AGENTS.md links here where it applies.

## Build Environment (this container)

Builds normally run in docker (see **Docker Build Loop** below), where the toolchain lives inside the repo under
`.build/` (git-ignored). That same toolchain — `.build/jdk` (Temurin 25) and the `.build/m2/repository` cache — also
serves **this container** directly, so `./mvnw` works here as-is; on a **full host** a writable `$HOME` with its own
JDK and Maven is used instead. Both cases are worth knowing, because a mismatch between them reads as a broken build:

- **In this container `./mvnw` works — use it, don't work around it.** `$HOME/.mavenrc` (HOME is `/workspace` here)
  points the wrapper at the repo's own toolchain: `JAVA_HOME=/workspace/.build/jdk` (Temurin 25) and
  `MAVEN_OPTS=-Dmaven.repo.local=/workspace/.build/m2/repository` — the same cache the docker loop resolves into.
  Verified 2026-10-04: `./mvnw -v` reports Temurin 25, `./mvnw -o -X validate` prints `Using local repository at
  /workspace/.build/m2/repository`, and `./mvnw -o -q test-compile` exits 0.
  A shell whose `HOME` is **not** `/workspace` breaks twice over: the launcher finds no `java` (nothing is on `PATH`)
  and the wrapper tries to create `$HOME/.m2/wrapper`, which is the read-only `/home/node`. Three variables make the
  wrapper independent of `HOME` — `JAVA_HOME`, `MAVEN_OPTS` (as above) and `MAVEN_USER_HOME=/workspace/.m2` — and the
  container's compose exports all three; with only `.mavenrc`, prefix `HOME=/workspace` instead.
  **Never hand-compile with `.build/jdk/bin/javac` and drive JUnit directly as a substitute for a build.** That
  bypasses the pom — surefire's `argLine`, resource filtering, the test lifecycle — so a green hand-rolled run does
  not mean the build passes. If the wrapper fails, fix the invocation (set the three variables, or `HOME=/workspace`)
  and say so; do not silently substitute a different toolchain.
- **On a full host** `./mvnw` is the stock Maven wrapper and does *not* self-configure: it does *not* read
  `.build/m2/settings.xml`, so with no `~/.m2/settings.xml` the local repository is the default `~/.m2/repository`.
  That is independent of the docker loop, which resolves into `.build/m2/repository` (see below); the two caches do
  not overlap. It also uses the JDK from `JAVA_HOME` (else the `java` on `PATH`), which on a host is
  usually *not* the `.build/jdk` the launcher runs on — the compiled classes are that other JDK's output.
- **`bin/metatron`** prefers an explicit `JAVA_HOME`, else `.build/jdk/bin/java` when present (Temurin 25, matching
  CI), else the `java` on `PATH`. It runs the dev loop from `target/classes` + the cached dependency classpath
  (`.mtron-classpath`), which holds **absolute** jar paths and is validated before use: a sidecar
  (`.mtron-classpath.sha256`) records the `pom.xml` the cache was resolved from, and the first cache entry must
  still exist. A cache from another `$HOME`, or one whose pom has changed, is re-resolved automatically — so a
  bare `NoClassDefFoundError` on some dependency class is a stale-cache symptom to check *here* first, not a
  missing dependency.
- `.build/jdk21` exists for reference, but **tests must run on a JDK 25 build**: the pom's surefire `argLine`
  includes `--sun-misc-unsafe-memory-access=allow`, a JDK 23+ flag that JDK 21 rejects (this is why CI uses JDK 25).
- **`bin/` layout** — commands you *type* stay flat in `bin/` (`bin/metatron` is the launcher — the old
  `bin/metatron-dev` is retired — plus `bin/metatron-docker` for every docker job and `bin/metatron-console` for the
  pty console harness); sourced shell helpers live in `bin/lib/`; assets a command consumes live in `bin/test/`
  (e.g. `bin/test/console-smoke.steps`).

## Docker Build Loop (the agent standard)

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
- `image` and `dev` package the uber-jar **inside** `metatron-build:25` and then build `metatron:dev`, whose
  Dockerfile COPYs `target/metatron-*-jar-with-dependencies.jar` directly — there is no staging copy in the repo root,
  and `.dockerignore` whitelists exactly that one file (plus `boot/`, `conf/`, the skills docs and `bin/wsplus`) into
  the
  build context. So docker is the only host requirement — no host maven/JDK — and the jar comes from the same JDK 25
  image the test loop uses. Order matters for the low-level path: package first (`mvn -DskipTests package`), then
  `docker build`.
- `run` and `dev` publish 8555/8777 (they collide with a live server by design); `NO_PORTS=1` makes `run`
  publish nothing. **`dev` removes and recreates the container named `$METATRON_CONTAINER`
  (default `metatron`)** — never run it while that container is the server under test.
- `DRY_RUN=1 bin/metatron-docker <anything>` prints the docker/mvn commands instead of running them.
- Image `metatron-build:25` auto-builds on first use from `dist/docker/Dockerfile.build`
  (maven + Temurin 25 + **patchelf** — patchelf is mandatory: the uber-jar ships a musl-built tree-sitter `.so` that
  `ObjJavaSerializer` re-points at glibc on load; without it the VM dies with `UnsatisfiedLinkError` and parks forever
  on the `/sys/thread/main` latch instead of exiting — a hung, non-terminating java process is the symptom).
- The repo mounts at `/work`; the container runs as the host user; **no ports are published** — the VM's 8555/8777 exist
  only in the container's netns, never on the host.
- The build loop keeps its Maven cache **separate from the host's**: `Dockerfile.build` sets a local repository and
  `bin/metatron-docker` overrides it with `-Dmaven.repo.local=/work/$MAVEN_REPO`, where `MAVEN_REPO` defaults to
  `.build/m2/repository` (override it to share one cache, e.g. `MAVEN_REPO=~/.m2/repository`). So `.build/m2/repository`
  serves the container only and `~/.m2/repository` serves `./mvnw` on the host; **neither is an orphan, and deleting
  `.build/m2` just makes the next `build …` re-resolve** its dependencies. `.build/m2/settings.xml` is unreferenced —
  the redirect is done with the flag above, not through a settings file. Every run gets a unique container name — never
  hardcode a docker `--name` around these (a stale container from a killed run collides).
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

### Running the loop from inside the dev container (verified 2026-10-12)

Works with **no setup at all**: `bin/metatron-docker` finds the docker CLI itself (it puts
`/opt/dsh-tools/bin` on `PATH` when the binary is not already there) and derives the host repo path
from the mount table, so a plain `bin/metatron-docker build test …` works from inside this container.
The deployment's compose exports both as well — `PATH` and `METATRON_HOST_REPO` — which is the
explicit form. Verified here with a plain invocation, nothing exported:

    bin/metatron-docker build test -Dtest=fURIAwareIndexedSchemaTest
    → 441 main + 270 test sources compiled, Tests run: 24, Failures: 0, Errors: 0, BUILD SUCCESS (13.9 s)

- **Two repo paths, and they are not the same thing here.** `REPO` is local (`$(pwd)` = `/workspace`)
  and is used for the build context, `mkdir` and `cp`; `HOST_REPO` is what the *daemon* resolves
  (`METATRON_HOST_REPO`, `/home/killswitch/software/metatron`) and is what goes into `-v`. Because
  `docker run -v` is resolved by the daemon, one path for both binds a non-existent host directory:
  the loop then compiles an empty tree and can still report BUILD SUCCESS. On a host both are
  `$(pwd)`. `HOST_REPO` is derived from the mount table (field 4 of the `/proc/self/mountinfo` entry
  whose mount point is the repo — field 9 is the backing device, not the path) and `METATRON_HOST_REPO`
  overrides it. **If `DRY_RUN=1` shows `-v /workspace:/work`, the derivation found no bind mount and
  `METATRON_HOST_REPO` must be set by hand — that mount is wrong.**
- **`.mavenrc` is `$HOME`-relative** for the same reason: the loop runs the container with
  `HOME=/work`, so the toolchain shim resolves to `/work/.build/…` — the same tree under a different
  prefix. Absolute `/workspace/…` paths name a directory that does not exist there and the build dies
  before Maven starts. The log line `Picked up JAVA_TOOL_OPTIONS: -Djava.io.tmpdir=/work/.build/m2/tmp`
  is this working.
- **The build/test container also gets the docker socket** (`-v /var/run/docker.sock` plus
  `--group-add <socket gid>`) **and `TESTCONTAINERS_HOST_OVERRIDE`**, so the `tble`/`dcmnt`/`vec`
  TestContainers classes run inside the loop. The nested containers publish to the *host*, so that
  override is what makes their ports reachable — `localhost` would be the build container itself.

Container facts that otherwise read as a broken build:

- **The filesystem is read-only except `/workspace`, `/opt/dsh-tools` and tmpfs.** `mkdir` on a host
  path from inside here fails with `Read-only file system`; that is the container, not a missing
  checkout. Anything a job needs to write must land under the repo or `/opt/dsh-tools`.
- The docker CLI is `/opt/dsh-tools/bin/docker` (static binary on the host-shared mount, added to
  `PATH` by the compose). The socket is mounted and usable as the `node` user (gid 127), so both the
  CLI and the HTTP API work.
- `docker ps` costs nothing — prove the socket before blaming a test.
- `DRY_RUN=1 bin/metatron-docker <anything>` prints the exact `docker run`; read its `-v` paths and
  its `-e` flags before trusting any result from the loop.
- **`Tests run: 0` is not a pass, and neither is an empty method name.** Surefire exits 0 and Maven
  prints BUILD SUCCESS when `-Dtest=` selects nothing — including when the method you named is real
  but is not a test (a `@BeforeAll` such as `setupMySQLDatabase` will do it). Worse, `-Dtest=Class#`
  with an EMPTY method drops the method filter entirely and silently runs the whole suite (1986 tests
  and two minutes here, with unrelated failures). Read the `Tests run:` line, not the exit code.
- **A TestContainers class can fail to acquire the daemon during a full-suite run** with
  `Could not find a valid Docker environment` (the `tble` MariaDB and PostgreSQL classes did, while
  MySQL ran 652 tests against a live container in the same run). The socket and
  `TESTCONTAINERS_HOST_OVERRIDE` are correct — re-run the affected class alone
  (`bin/metatron-docker build test -Dtest=MariaDBTbleSpaceTest`) before calling it a wiring problem.

## Building from a `.claude/worktrees` checkout on the host (verified 2026-10-21)

A worktree has no `.build/` (the docker loop's JDK and Maven cache live in the main checkout), so
`bin/metatron-docker build …` there would re-resolve ~1.1 GB into the worktree. On the host, `./mvnw -o -B test
-Dtest=<Class>` works offline instead (Temurin 25 on `PATH`, `~/.m2` populated). Unit-test VM boots are portless
(`AbstractMetatronTest` calls `BootLoader.load(rec())`), so they cannot collide with the live server; peer/transport
tests and the docs pipeline still belong in docker. Surefire writes a test's stdout to
`target/surefire-reports/<class>-output.txt`.

## Required JVM Flags

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

## CI/CD

|                         `.github/workflows/maven.yml` |                 `.github/workflows/docker.yml` |
|------------------------------------------------------:|-----------------------------------------------:|
|                                     Push/PR to `main` | Push to `main`, `v*` tags, `workflow_dispatch` |
|                                         Oracle JDK 25 |                      Temurin JDK 25 (uber-jar) |
|                `./mvnw install -Dtest='!fsSpaceTest'` |     `mvn package` → build & push image to GHCR |

Docker images are built and published to GHCR (`ghcr.io/phaseshift-studio/metatron`) by the
`docker.yml` workflow — the Maven build itself does not touch Docker.

---

## MCP (Model Context Protocol)

- WebSocket handler: `mcp_mtron_wsHandler`
- Test client: `bin/wsplus` — REPL + `-e` one-shot. **`bin/wsplus` is the only wsplus anyone edits.** The
  copies at `docs/website/skills/{mtron,metatron}/scripts/wsplus` are build products: `bin/build-wsplus.py`
  emits a bootstrap (inlining `bin/lib` and `conf/nanorc/mtron.nanorc`) followed by the unmodified body of
  `bin/wsplus`, because that artifact is downloaded from the website and must run with nothing next to it.
  They are refreshed by **every** build (the pom binds the bundler to `generate-resources`, and it skips
  identical content), and `python3 bin/build-wsplus.py --check <dirs>` reports drift — printing `ok` or
  `STALE` and exiting 1 — without writing a byte; `sh bin/test/build-wsplus.sh` runs it both ways.
  "Bundled" does NOT cover its one third-party dependency: `bin/wsplus` imports `websockets` at runtime.

### Python packages in this container

- **`bin/wsplus` works out of the box here** (verified 2026-10-13). Its dependency `websockets` 17.2 — the
  pure-Python wheel, so there is no ABI to match — is installed in the user site,
  `/workspace/.local/lib/python3.13/site-packages`. It got there without pip, because pip was not
  available and the system site is not writable: **a wheel is a zip**, so
  `python3 -m zipfile -e PKG.whl "$(python3 -c 'import site; print(site.getusersitepackages())')"` is the
  pip-free install. Python finds it automatically (`$HOME` is `/workspace` there and the user site is
  enabled), so nothing needs exporting, and it lives under the repo mount — gitignored — so it survives
  container recreates. Proof: `bin/wsplus -e '1 + 2' ws://192.168.1.61:8555/mtron` → `==>3`.
- **pip is installed too** (26.2.1, same user site): `python3 -m pip install --user PKG`. This image's
  Python is externally managed (PEP 668) and its system site is read-only, so `--user` is **required** and
  both `/workspace/.pip/pip.conf` and `~/.config/pip/pip.conf` set `break-system-packages = true` (pip
  actually reads the first for this container's `HOME`, which resolves `XDG_CONFIG_HOME` to
  `/tmp/.config`). Check with `python3 -m pip config debug`. If a PEP 668 error appears anyway, prefix
  `PIP_BREAK_SYSTEM_PACKAGES=1`.

## Tool Quirks — Write/Edit Truncation Bug (2026-07-03, fixed upstream)

**IMPORTANT**: the following does not apply to DeepSeek Harness. If you are in DeepSeek Harness, skip this section.

The Cowork-mode Edit and Write tools **silently truncate files when the content exceeds the pre-edit file's byte size**.
The tool returns success even though the file is corrupted. Shell heredocs (`cat > f <<HEREDOC`) also fail for large
files with the same symptom.

**Reliable workaround:** Use Python `pathlib` for any file write 200+ lines (or ~4KB+) —
`python3 -c "import pathlib; pathlib.Path('/abs/path').write_text(content)"`. Shows ~100% success in user logs.

**Verification:** Always check `wc -c <file>` after writing/editing large files to confirm integrity. Line counts are
unreliable since truncation can strip content unpredictably (missing loop variables, broken expressions, etc.).

---
