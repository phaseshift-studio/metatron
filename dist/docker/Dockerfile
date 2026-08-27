# syntax=docker/dockerfile:1
# metatron: a distributed data-oriented computing language and virtual machine
#   PhaseShift Studio, LLC
#
# Self-contained server image.  Runs the metatron uber-jar headlessly against a
# boot file (default boot/docker.boot.mtron; mount your own boot and pass its
# args as the container command).  The boot uses dckrspace::T — which pulls its
# infra (sqlite, postgres, janusgraph, ...) through the HOST docker socket,
# mounted at runtime.
#
# Build context must contain `metatron.jar` (the assembled uber-jar); CI copies
# target/metatron-<version>-jar-with-dependencies.jar -> metatron.jar first.

ARG METATRON_VERSION="0.1-SNAPSHOT"

FROM eclipse-temurin:25-jre-jammy

ARG METATRON_VERSION

# OCI metadata (replaces the deprecated MAINTAINER)
LABEL org.opencontainers.image.title="metatron"
LABEL org.opencontainers.image.description="a distributed data-oriented computing language and virtual machine"
LABEL org.opencontainers.image.licenses="AGPL-3.0"
LABEL org.opencontainers.image.source="https://github.com/phaseshift-studio/metatron"
LABEL org.opencontainers.image.vendor="PhaseShift Studio, LLC"
LABEL org.opencontainers.image.version="${METATRON_VERSION}"

# curl for the healthcheck; tzdata for correct timestamps; patchelf to fix the
# jar's musl-built tree-sitter .so for this glibc base (see
# ObjJavaSerializer.loadNativeLibrary — it patches libc.musl -> libc.so.6);
# python3 + websockets for the wsplus console (a basic REPL client for the
# headless server's /mtron endpoint).  The apt python3-websockets (9.x) breaks
# on Python 3.10+ (asyncio.Lock loop param), so pip-install a current one.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl tzdata patchelf python3 python3-pip \
    && rm -rf /var/lib/apt/lists/* \
    && pip3 install --no-cache-dir --quiet websockets

# docker CLI so dckrspace::T can orchestrate infra containers (sqlite, postgres,
# janusgraph, ...) through the HOST docker socket, which must be mounted at
# runtime:  -v /var/run/docker.sock:/var/run/docker.sock
# (see dist/docker/README.md for the --group-add socket-permission note).
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl \
    && install -m 0755 -d /etc/apt/keyrings \
    && curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc \
    && chmod a+r /etc/apt/keyrings/docker.asc \
    && echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" > /etc/apt/sources.list.d/docker.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends docker-ce-cli docker-compose-plugin \
    && rm -rf /var/lib/apt/lists/*

# non-root runtime user (fixed uid so host-mounted volumes can be chowned)
RUN groupadd --gid 10001 metatron \
    && useradd --uid 10001 --gid 10001 --create-home --home-dir /home/metatron metatron

WORKDIR /app
COPY --chown=metatron:metatron metatron.jar /app/metatron.jar
COPY --chown=metatron:metatron boot/ /app/boot/
COPY --chown=metatron:metatron conf/ /app/conf/
# wsplus console client — talk to the headless server's /mtron endpoint:
#   docker exec -it metatron wsplus ws://localhost:8555/mtron
COPY --chown=metatron:metatron bin/wsplus /app/bin/wsplus
ENV PATH="/app/bin:${PATH}"
# entrypoint: `docker run <image> --help` prints usage, otherwise runs the VM
COPY --chown=metatron:metatron dist/docker/entrypoint.sh dist/docker/usage.txt /app/
RUN chmod +x /app/entrypoint.sh
# skills — the MCP handlers load SKILL.md from .metatron/skills (a symlink to
# docs/website/skills in the repo; bake the two skill sets directly here)
COPY --chown=metatron:metatron docs/website/skills/mtron /app/.metatron/skills/mtron
COPY --chown=metatron:metatron docs/website/skills/metatron /app/.metatron/skills/metatron

USER metatron

# ws (mtron/MCP) and http — the standard boot binds both on 0.0.0.0
EXPOSE 8555 8777

# The entrypoint runs the VM with the JVM flags it needs (in entrypoint.sh).
# After a console-less boot completes, BootLoader parks the main thread on its
# shutdown latch (BootLoader.java ~540) until SIGTERM, so the boot's web/mcp
# servers keep the process alive.  Do NOT pass --headless: it is not a
# recognized flag — the parser swallows it as a bare mtron expression (mtron
# comments start with --) and ONE_SHOT exits.  The boot-args string (the CMD)
# is passed straight to BootLoader.
ENTRYPOINT ["/app/entrypoint.sh"]

# Default boot: the thin container profile (boot/docker.boot.mtron), which pulls
# the boot/space modules.  Mount your own boot and pass its boot args as the
# command:
#   docker run ... metatron "[boot=><boot/deploy.mtron>,log=>info]"
CMD ["[boot=><boot/docker.boot.mtron>,log=>info]"]

# Assumes the boot serves http on 0.0.0.0:8777 (the container's healthcheck port).
# The http / route serves an index from the fs: web root — a plain GET must be 2xx.
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
    CMD curl -fsS http://localhost:8777/ >/dev/null || exit 1
