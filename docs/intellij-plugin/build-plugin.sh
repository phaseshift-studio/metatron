#!/usr/bin/env bash
#
# build-plugin.sh — compile the metatron docs IntelliJ plugin against a LOCAL IntelliJ
# IDEA installation. No Gradle, no SDK download, nothing published — a self-contained
# plugin zip that you load with "Install Plugin from Disk".
#
# The action is pure Java + core IntelliJ APIs, so compiling against any recent IDEA
# works. The produced zip is portable across the same-family IDEs.
#
# Usage:
#   docs/intellij-plugin/build-plugin.sh
#   IDEA_HOME=/path/to/IDEA  docs/intellij-plugin/build-plugin.sh   # override detection
#
set -euo pipefail
cd "$(dirname "$0")"
echo "[plugin] metatron docs plugin — locating IntelliJ IDEA ..."

# ── 1. Locate an IntelliJ IDEA home (a dir containing lib/*.jar) ──────────
# Does the home's lib/ hold the IntelliJ PLATFORM (some jar with com/intellij/openapi)?
has_platform_jars() {
    local libdir j
    for libdir in "${1%/}/lib" "${1%/}/Contents/lib"; do
        [ -d "$libdir" ] || continue
        for j in "$libdir"/*.jar; do
            [ -e "$j" ] || continue
            if jar tf "$j" 2>/dev/null | grep -q "com/intellij/openapi"; then return 0; fi
        done
    done
    return 1
}

# Decisive: a REAL IDE install is the one that owns a bin/idea launcher next to its lib/.
# A plugin dir (Kotlin/kotlinc.ide, python-ce, …) may carry com/intellij in a jar but has no
# top-level bin/idea — so this excludes them.
has_idea_launcher() {
    local base="${1%/}"
    [ -e "$base/bin/idea" ] || [ -e "$base/bin/idea.sh" ] || [ -e "$base/bin/idea.bat" ]
}

# A usable IDEA home: holds lib/*.jar, that lib is the platform, AND it owns a bin/idea launcher.
looks_like_idea_home() {
    local c="${1%/}"
    [ -d "$c" ] || return 1
    if [ -d "$c/lib" ] && ls "$c"/lib/*.jar >/dev/null 2>&1 \
            && has_platform_jars "$c" && has_idea_launcher "$c"; then
        printf '%s\n' "$c"; return 0
    fi
    if [ -d "$c/Contents/lib" ] && ls "$c"/Contents/lib/*.jar >/dev/null 2>&1 \
            && has_platform_jars "$c" && has_idea_launcher "$c"; then
        printf '%s\n' "$c/Contents"; return 0
    fi
    return 1
}

find_idea_home() {
    local c
    # 1) explicit env (IDEA_HOME / INTELLIJ_HOME) — may be the app, the Contents dir, or the lib dir.
    for v in IDEA_HOME INTELLIJ_HOME; do
        c="${!v:-}"
        [ -n "${c:-}" ] || continue
        looks_like_idea_home "$c" && return 0
        [ -d "$c/../../../lib" ] && looks_like_idea_home "$c/../../.." && return 0   # .../bin or .../lib given
    done
    # 2) macOS app bundles (Contents holds lib/).
    for c in \
        "/Applications/IntelliJ IDEA.app" \
        "/Applications/IntelliJ IDEA Ultimate.app" \
        "/Applications/IntelliJ IDEA CE.app" \
        "$HOME/Applications/IntelliJ IDEA.app" \
        ; do
        looks_like_idea_home "$c" && return 0
    done
    # 3) JetBrains Toolbox (mac: .../IDEA-U/ch-*/IntelliJ IDEA.app; linux: .../IDEA-U/ch-*/IntelliJ IDEA).
    for c in "$HOME/.local/share/JetBrains/Toolbox/apps/"IDEA*"/ch-"*/; do
        looks_like_idea_home "${c%/}/IntelliJ IDEA.app" && return 0
        looks_like_idea_home "${c%/}/IntelliJ IDEA"    && return 0
        looks_like_idea_home "${c%/}"                   && return 0
    done
    # 4) Linux snap: /snap/intellij-idea-<flavor>/<rev>/{lib,bin}.
    for c in /snap/intellij-idea-*/*/; do
        looks_like_idea_home "${c%/}" && return 0
    done
    # 5) /opt and /usr/share installs.
    for c in /opt/*idea* /usr/share/*idea* /opt/*IDEA*; do
        looks_like_idea_home "$c" && return 0
    done
    # 6) walk up from the `idea` launcher if present.
    if command -v idea >/dev/null 2>&1; then
        local real d up
        real="$(readlink -f "$(command -v idea)" 2>/dev/null || true)"
        d="$(dirname "$real" 2>/dev/null || true)"; up=0
        while [ -n "${d:-}" ] && [ "$up" -lt 6 ]; do
            looks_like_idea_home "$d" && return 0
            looks_like_idea_home "$d/../../.." && return 0
            d="$(dirname "$d")"; up=$((up + 1))
        done
    fi
    return 1
}

# IDEA path can come from $1 (preferred), $IDEA_HOME, or auto-detect.
IDEA_HOME="${1:-${IDEA_HOME:-$(find_idea_home || true)}}"
if [ -z "${IDEA_HOME:-}" ]; then
    echo "" >&2
    echo "ERROR: could not find IntelliJ IDEA, so there is nothing to compile against." >&2
    echo "" >&2
    echo "       Pass the directory that CONTAINS lib/ (the one with lib/*.jar), e.g.:" >&2
    echo "         mac:     $0 '/Applications/IntelliJ IDEA.app/Contents'" >&2
    echo "                 $0 '/Applications/IntelliJ IDEA.app'            (app dir also works)" >&2
    echo "         linux:   $0 /snap/intellij-idea-ultimate/123" >&2
    echo "                 $0 ~/.local/share/JetBrains/Toolbox/apps/IDEA-U/ch-xxx/IntelliJ\ IDEA" >&2
    exit 1
fi
if ! has_platform_jars "$IDEA_HOME" || ! has_idea_launcher "$IDEA_HOME"; then
    echo "WARNING: $IDEA_HOME does not look like an IntelliJ platform" >&2
    echo "         (no lib/*.jar containing com/intellij/openapi). The compile will likely fail" >&2
    echo "         with 'package com.intellij.* does not exist'. Point it at the real IDE home," >&2
    echo "         the directory that CONTAINS lib/ (the one holding app.jar / util.jar)." >&2
    echo "" >&2
fi
echo "[plugin] using IDEA_HOME=$IDEA_HOME"

BUILD="build"
STAGE="$BUILD/stage"
ROOTDIR="metatron-docs-plugin"   # 262 needs the archive top level to be ONE root folder
rm -rf "$BUILD"
mkdir -p "$BUILD/classes" "$STAGE/$ROOTDIR/META-INF" "$STAGE/$ROOTDIR/lib"

# ── 2. Compile against the local platform jars (superset, compile-only) ───
CP="$(ls "$IDEA_HOME"/lib/*.jar 2>/dev/null | tr '\n' ':')"
[ -z "$CP" ] && { echo "ERROR: no lib/*.jar under $IDEA_HOME" >&2; exit 1; }

SOURCES="$(find src -name '*.java' | tr '\n' ' ')"
echo "[plugin] compiling: $SOURCES"
javac -encoding UTF-8 -proc:none -nowarn -cp "$CP" -d "$BUILD/classes" $SOURCES

# ── 3. Package as a canonical IntelliJ plugin ─────────────────────────────
#   metatron-docs-plugin.zip
#     META-INF/plugin.xml          (the descriptor IntelliJ reads)
#     lib/metatron-docs-plugin.jar (the compiled classes)
cp resources/META-INF/plugin.xml "$STAGE/$ROOTDIR/META-INF/plugin.xml"

# Guard: the descriptor must be pure ASCII (non-ASCII has burned "Install Plugin
# from Disk" before). Fail loudly, not with a mysterious 'Fail to load plugin descriptor'.
if LC_ALL=C grep -nq '[^[:print:][:space:]]' "$STAGE/$ROOTDIR/META-INF/plugin.xml"; then
    echo "ERROR: META-INF/plugin.xml contains non-ASCII characters (intellij can reject that)." >&2
    echo "       Replace with ASCII (e.g. use '->', not the unicode arrow)." >&2
    exit 1
fi

jar cf "$STAGE/$ROOTDIR/lib/metatron-docs-plugin.jar" -C "$BUILD/classes" studio
( cd "$STAGE" && jar -cfM ../metatron-docs-plugin.zip . )
echo "[plugin] built  →  $(cd "$BUILD" && pwd)/metatron-docs-plugin.zip"
echo ""
echo "  install:  Settings → Plugins → ⚙ → 'Install Plugin from Disk' → pick that zip → restart IDEA"
echo "  requires: metatron already built once (./mvnw install -DskipTests) so target/*.jar exists"
