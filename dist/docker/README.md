# metatron container

Self-contained metatron server image: runs the uber-jar headless against a boot file. Built and published to GHCR
(`ghcr.io/phaseshift-studio/metatron`) by
`.github/workflows/docker.yml` on pushes to `main` (tag `main`) and `v*` tags (also tag `latest`).

## Image contract

- **Entrypoint** runs `java <jvm-flags> -jar /app/metatron.jar <boot-args>`. After a console-less boot completes,
  BootLoader parks the main thread on its shutdown latch until SIGTERM, so the boot's web/MCP servers keep the process
  alive (don't pass `--headless` — it isn't a real flag and makes the JVM exit).
- **Ports**: `8555` (ws: mtron/MCP), `8777` (http). The boot must bind ws/http on `0.0.0.0` for the mapped ports to be
  reachable from the host.
- **Healthcheck** probes `http://localhost:8777/` — the boot must serve http on that port or the container reports
  unhealthy (20s start period before checking).
- **`dckrspace::T`** (used by the standard boot to pull sqlite / postgres / janusgraph / etc.) uses the docker CLI baked
  into the image, talking to the HOST docker socket — mount it at runtime with
  `-v /var/run/docker.sock:/var/run/docker.sock`, and add the host's docker group so the non-root user can access the
  socket:
  `--group-add $(stat -c '%g' /var/run/docker.sock)`.
- Runs as non-root user `metatron` (uid 10001); chown host-mounted volumes to that uid.

## Build

```bash
# one command — packages the uber-jar inside metatron-build:25 (docker is the only host
# requirement) and builds this image:
bin/metatron-docker image

# by hand: package first, then build.  The Dockerfile COPYs the packaged jar out of target/.
mvn -DskipTests package
docker build -f dist/docker/Dockerfile -t metatron:0.1-SNAPSHOT .
```

There is no staging copy of the jar: `dist/docker/Dockerfile` COPYs
`target/metatron-*-jar-with-dependencies.jar`, and `.dockerignore` whitelists exactly that file (plus
`boot/`, `conf/`, `dist/docker/`, the two skills trees, and `bin/wsplus` with its sibling `bin/lib` package) into the
build context — so the context stays small even though the repo is huge, and a plain `docker build` works right after
`mvn package`.

## Run

```bash
# default boot (boot/boot.mtron is baked into the image):
docker run -d --name metatron \
  -p 8555:8555 -p 8777:8777 \
  -v /var/run/docker.sock:/var/run/docker.sock \
  ghcr.io/phaseshift-studio/metatron:main

# run a custom boot (mount it read-only, pass boot args as the command):
docker run -d --name metatron \
  -p 8555:8555 -p 8777:8777 \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v "$(pwd)/my.boot.mtron:/app/boot/deploy.mtron:ro" \
  ghcr.io/phaseshift-studio/metatron:main \
  "[boot=><boot/deploy.mtron>,log=>info,console=>false]"

The `boot=>` address (and the `-b` flag / `$METATRON_BOOT`) accepts a bare path, `file:///path`, or an `http(s)://`
URL — a URL boot is fetched and evaluated with no volume mount:

  docker run ... metatron "[boot=><http://host/deploy.mtron>]"

# quick interactive shell (the bin/metatron-docker wrapper does this):
./bin/metatron-docker
```

## Compose

`dist/docker/compose.yaml` is a ready-to-run deployment, and `dist/docker/.env.example` is its configuration surface.
The YAML names only the **boundary** — image, host socket, state volume, ports, limits, healthcheck — plus the
boot-args string. Every knob and its default lives in `.env.example`: copy it, edit, deploy.

```bash
cd dist/docker
cp .env.example .env                              # the one file you edit
docker compose up -d                              # boot (ws 8555 / http 8777) + healthcheck
docker compose logs -f metatron
docker compose exec metatron wsplus ws://localhost:8555/mtron   # the REPL console
docker compose -f compose.yaml -f compose.infra.yaml up -d   # optional mariadb / gremlin / chroma backends
docker compose down                               # keep VM state
```

There are three kinds of knob, and they are **not** interchangeable:

| Layer | Where | Drives |
|-------|-------|--------|
| host-side | `$VAR` in `.env` (or exported from your shell) | ports, mounts, limits, the boot-args string |
| container-side | the service's `environment:` block | the JVM's own options (`JAVA_TOOL_OPTIONS`), `TZ`, ... |
| boot args | the boot-args rec (the container `command`) | the VM itself: `boot`, `log`, `user`, `console`, `data`, the `web/http` + `web/ws` URIs, the `typer/stage` and `tracer/stack` flags |

The boot-args string is where the VM itself is configured; compose assembles it from the host-side variables, so a
whole run can be re-pointed from the shell:

```bash
METATRON_LOG=debug METATRON_USER=marko METATRON_CONSOLE=true docker compose up -d
```

**One rule, or the knobs will lie to you:** a deployment-varying value must be read by the boot file from `boot/args`
(`.else(<dev default>)`). If a boot file hardcodes a key in its own `[== boot args ==]` header, the header wins and the
value set here is silently ignored.

Every port is one variable used on both sides (host mapping, container bind, boot-args URI and healthcheck), so the
four cannot drift apart.

**Commented-out alternatives.** The rest of the surface was moved out of the YAML to keep it lean; each is a
commented-out block in `compose.yaml` to uncomment when you need it:

| alternative | what it does |
|-------------|--------------|
| `build:` (in `metatron`) | build the image from a checkout instead of pulling (needs the uber-jar in `target/`) |
| dev-loop boot/conf mounts | a checkout's `boot/` and `conf/` override the baked copies |
| docker-credentials mount | hand the VM `~/.docker/config.json` instead of the socket |
| `read_only: true` + `tmpfs:` | read-only rootfs — `/tmp` must stay a writable, exec tmpfs (tree-sitter native lib) |
| bind-mounted state volume | a host dir instead of the named volume (chown to `10001:10001`) |
| `expose:` instead of `ports:` | publish nothing — the server is reachable only on the compose network |
| `env_file:` | bulk-load container-side variables from a file |
| `ulimits:` / `sysctls:` / `pids_limit:` / `shm_size:` | kernel/process limits |
| `extra_hosts:` / `dns:` / `dns_search:` | name resolution for boots that dial off-network |
| `gpus:` (in `deploy.resources`) | GPU access (needs the NVIDIA container toolkit) |
| `userns_mode:` / `security_opt: label=...` | user-namespace / AppArmor-SELinux labelling |
| watchtower label | let a watchtower-style updater redeploy this service |

**State.** VM state lives in the named volume `metatron-state` mounted at `/home/metatron` (the sqlite tble space at
`METATRON_DATA`, default `/home/metatron`). The volume root is deliberately the image's `/home/metatron`: docker seeds
an empty named volume from the image path, and that path is owned by uid 10001, so it is writable with no `chown` step.
A fresh volume at an arbitrary path such as `/data` comes up root-owned and the sqlite space fails to open its file.

**The socket.** Compose mounts `/var/run/docker.sock` and `group_add`s `METATRON_DOCKER_GID`
(`export METATRON_DOCKER_GID=$(stat -c '%g' /var/run/docker.sock)`) so `dckrspace::T` can drive the host daemon. The
default boot only uses it to look up the sqlite container, so a socket it cannot open degrades (logged) instead of
failing the boot; a boot that binds mariadb / gremlin / chroma genuinely needs it.

The space backends live in [compose.infra.yaml](compose.infra.yaml) rather than in `compose.yaml`, so the shipped file
stays one service: `docker compose -f compose.yaml -f compose.infra.yaml up -d`. They are the alternative to the boot
modules pulling their own containers — the same images, ports and volume names as `boot/space/*`, so a boot that binds
those spaces finds them instead of composing its own.

## Console

The image includes `wsplus`, a basic mtron REPL for talking to the headless server's `/mtron` endpoint. The connection
is persistent, so session state carries across lines:

```bash
docker exec -it metatron wsplus
# ... or, from a compose deployment (and a GUI like dockge's console/exec):
docker compose exec metatron wsplus

mtron> 1 + 2
==>3
mtron> /usr/alice/x -> [1,2,3]
==>[1,2,3]
mtron> */usr/alice/x/1
==>2
```

`bin/wsplus` from a checkout works the same way against any running metatron.

**NOTE**: to use the more feature rich `console::T`, set the `boot/arg/console=>true`.
