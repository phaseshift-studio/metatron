# syntax=docker/dockerfile:1
# metatron: a distributed data-oriented computing language and virtual machine
#   PhaseShift Studio, LLC
#
# Self-contained server image.  Runs the metatron uber-jar headlessly against a
# boot file (default boot/boot.mtron; mount your own boot and pass its args as
# the container command).  The standard deployed boot is a variation of
# drstynx.boot.mtron and uses dckrspace::T — which pulls its infra (sqlite,
# postgres, janusgraph, ...) through the HOST docker socket, mounted at runtime.
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
# ObjJavaSerializer.loadNativeLibrary — it patches libc.musl -> libc.so.6).
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl tzdata patchelf \
    && rm -rf /var/lib/apt/lists/*

# non-root runtime user (fixed uid so host-mounted volumes can be chowned)
RUN groupadd --gid 10001 metatron \
    && useradd --uid 10001 --gid 10001 --create-home --home-dir /home/metatron metatron

WORKDIR /app
COPY --chown=metatron:metatron metatron.jar /app/metatron.jar
COPY --chown=metatron:metatron boot/ /app/boot/
COPY --chown=metatron:metatron conf/ /app/conf/

USER metatron

# ws (mtron/MCP) and http — the standard boot binds both on 0.0.0.0
EXPOSE 8555 8777

# metatron needs module access to run.  After a console-less boot completes,
# BootLoader parks the main thread on its shutdown latch (BootLoader.java ~540)
# until SIGTERM, so the boot's web/mcp servers keep the process alive.  Do NOT
# pass --headless: it is not a recognized flag — the parser swallows it as a
# bare mtron expression (mtron comments start with --) and ONE_SHOT exits.
# The boot-args string (the CMD) is passed straight to BootLoader.
ENTRYPOINT ["java", \
    "--enable-native-access=ALL-UNNAMED", \
    "--add-modules", "jdk.incubator.vector", \
    "--add-opens", "java.base/java.lang=ALL-UNNAMED", \
    "--add-opens", "java.base/java.lang.invoke=ALL-UNNAMED", \
    "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED", \
    "--add-opens", "java.base/java.util=ALL-UNNAMED", \
    "--add-opens", "java.base/java.util.concurrent.atomic=ALL-UNNAMED", \
    "--add-opens", "java.base/java.io=ALL-UNNAMED", \
    "--add-opens", "java.base/java.nio=ALL-UNNAMED", \
    "--add-opens", "java.base/java.net=ALL-UNNAMED", \
    "--add-opens", "java.base/sun.nio.cs=ALL-UNNAMED", \
    "--sun-misc-unsafe-memory-access=allow", \
    "-jar", "/app/metatron.jar"]

# Default boot.  The standard deployed boot is still being settled — mount your
# own and pass its boot args as the command:
#   docker run ... metatron "[boot=><boot/deploy.mtron>,log=>info]"
CMD ["[boot=><.metatron/skills/drstynx/assets/drstynx.boot.mtron>,log=>info]"]

# Assumes the boot serves http on 0.0.0.0:8777 (the container's healthcheck port).
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
    CMD curl -fsS http://localhost:8777/ >/dev/null || exit 1
