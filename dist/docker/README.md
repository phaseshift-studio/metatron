# metatron container

Self-contained metatron server image: runs the uber-jar headless against a boot
file.  Built and published to GHCR (`ghcr.io/phaseshift-studio/metatron`) by
`.github/workflows/docker.yml` on pushes to `main` (tag `main`) and `v*` tags
(also tag `latest`).

## Image contract

- **Entrypoint** runs `java <jvm-flags> -jar /app/metatron.jar <boot-args>`.
  After a console-less boot completes, BootLoader parks the main thread on its
  shutdown latch until SIGTERM, so the boot's web/MCP servers keep the process
  alive (don't pass `--headless` — it isn't a real flag and makes the JVM exit).
- **Ports**: `8555` (ws: mtron/MCP), `8777` (http).  The boot must bind ws/http
  on `0.0.0.0` for the mapped ports to be reachable from the host.
- **Healthcheck** probes `http://localhost:8777/` — the boot must serve http on
  that port or the container reports unhealthy (20s start period before checking).
- **`dckrspace::T`** (used by the standard boot to pull sqlite / postgres /
  janusgraph / etc.) uses the docker CLI baked into the image, talking to the
  HOST docker socket — mount it at runtime with
  `-v /var/run/docker.sock:/var/run/docker.sock`, and add the host's docker
  group so the non-root user can access the socket:
  `--group-add $(stat -c '%g' /var/run/docker.sock)`.
- Runs as non-root user `metatron` (uid 10001); chown host-mounted volumes to
  that uid.

## Build

```bash
# from the repo root, after `mvn package`:
cp target/metatron-0.1-SNAPSHOT-jar-with-dependencies.jar metatron.jar
docker build -f dist/docker/Dockerfile -t metatron:0.1-SNAPSHOT .
```

The build context is whitelisted by `.dockerignore` (jar + `boot/` + `conf/`),
so the context stays small even though the repo is huge.

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
  "[boot=><boot/deploy.mtron>,log=>info]"

# quick interactive shell (the bin/metatron-docker wrapper does this):
./bin/metatron-docker
```

## Console

The image includes `wsplus`, a basic mtron REPL for talking to the headless
server's `/mtron` endpoint. The connection is persistent, so session state
carries across lines:

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
