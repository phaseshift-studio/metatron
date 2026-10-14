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
# one command — packages the uber-jar inside metatron-build:24 (docker is the only host
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

# quick interactive shell (the bin/metatron-docker wrapper does this):
./bin/metatron-docker
```

## Compose

`dist/docker/compose.yaml` is a ready-to-run deployment template. It names every knob the image honours, with the
default as the value, so it doubles as the reference for what can be changed:

```bash
cd dist/docker
docker compose up -d                              # boot (ws 8555 / http 8777) + healthcheck
docker compose logs -f metatron
docker compose --profile repl run --rm repl       # a wsplus REPL against the server
docker compose -f compose.yaml -f compose.infra.yaml up -d   # optional mariadb / gremlin / chroma backends
docker compose down                               # keep VM state
```

There are two layers of configuration and they are **not** interchangeable:

| Layer | Where | Drives |
|-------|-------|--------|
| host-side | `$VAR` in `compose.yaml`, resolved from your shell or a `.env` beside the file | ports, mounts, the boot-args string |
| container-side | the service's `environment:` block | the JVM's own options (`JAVA_TOOL_OPTIONS`), `TZ`, ... |

The boot-args string (the container `command`) is where the VM itself is configured: `boot`, `log`, `user`, `console`,
`data`, the `web/http` + `web/ws` URIs, the `typer/stage` flags and the `tracer/stack` flags. Compose assembles it from
host-side variables, so a whole run can be re-pointed from the shell:

```bash
METATRON_LOG=debug METATRON_USER=marko METATRON_CONSOLE=true docker compose up -d
```

Every port is one variable used on both sides (host mapping, container bind, boot-args URI and healthcheck), so the
four cannot drift apart. The file also documents the alternatives as commented-out blocks: building from a checkout,
mounting a custom boot file or `conf/`, a read-only rootfs, log rotation, resource limits, watchtower labels, a
bind-mounted state volume, and host aliases/DNS.

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
docker exec -it metatron wsplus ws://localhost:8555/mtron

mtron> 1 + 2
==>3
mtron> /usr/alice/x -> [1,2,3]
==>[1,2,3]
mtron> */usr/alice/x/1
==>2
```

`bin/wsplus` from a checkout works the same way against any running metatron.

**NOTE**: to use the more feature rich `console::T`, set the `boot/arg/console=>true`.
