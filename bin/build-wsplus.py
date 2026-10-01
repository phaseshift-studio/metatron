#!/usr/bin/env python3
# build-wsplus.py — bundle bin/wsplus with its bin/lib deps and the mtron.nanorc
# data file into a single self-contained file, for the website skill scripts dir.
#
# The emitted file is the unmodified body of bin/wsplus preceded by a small
# bootstrap that:
#   * registers every `lib.<module>` the body imports into sys.modules (exec'd
#     from a zlib+base64 payload, so no `lib/` package tree is needed at runtime)
#   * patches `lib.nanorc_highlight.parse_nanorc` to fall back to an in-memory
#     parse of the inlined mtron.nanorc when the conf/ tree is not present
# So the bundled file runs identically to bin/wsplus, but needs no files next to it.
#
# Usage:
#   build-wsplus.py <output-dir> [<output-dir> ...]
#
# The bundle is written to <output-dir>/wsplus (chmod +x), and the write is
# skipped when the file already holds the freshly built content (so builds are
# idempotent and do not churn mtimes).
#
# Regenerate the website copies:
#   python3 bin/build-wsplus.py docs/website/skills/mtron/scripts docs/website/skills/metatron/scripts
import base64
import re
import sys
import zlib
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
WSPLUS = REPO / "bin" / "wsplus"
LIB_DIR = REPO / "bin" / "lib"
NANORC = REPO / "conf" / "nanorc" / "mtron.nanorc"
REQUIRED_MODULE = "lib.nanorc_highlight"   # the mtron.nanorc parser wsplus needs

# Detect `from lib.X import ...` / `import lib.X` in the body (full dotted name).
_LOCAL_IMPORT = re.compile(r"^\s*(?:from\s+(lib\.[A-Za-z_]\w*)\s+import|import\s+(lib\.[A-Za-z_]\w*))")

# mtron.nanorc parser regexes, lifted from bin/lib/nanorc_highlight.py so the
# in-memory fallback parse reuses them. Injected into the emitted file via repr()
# (exact, no re-escaping), keeping this script their single source of truth.
_RX_SYNTAX = r'syntax\s+"?([^"\s]+)"?\s+"(.+)"'
_RX_COLOR = r"(i?color)\s+([^\s]+)\s+(.*)"
_RX_STARTEND = r'start="([^"]*)"\s+end="([^"]*)"'
_RX_QUOTED = r'"((?:[^"\\]|\\.)*)"'


def _payload(text):
    """zlib then base64, so the payload survives as a plain ASCII string literal."""
    return base64.b64encode(zlib.compress(text.encode("utf-8"), 9)).decode("ascii")


def _discover(body):
    """Ordered list of (module_name, source_path) for every lib.* import in the body."""
    seen, out = set(), []
    for line in body.splitlines():
        m = _LOCAL_IMPORT.match(line)
        if not m:
            continue
        name = m.group(1) or m.group(2)
        if name in seen:
            continue
        seen.add(name)
        rel = name.split(".", 1)[1].replace(".", "/")
        out.append((name, LIB_DIR / (rel + ".py")))
    return out


def _bootstrap(deps, nanorc_b64):
    """The generated top-of-file bootstrap (requires REQUIRED_MODULE in deps)."""
    lines = [
        "# --- bundler-generated bootstrap (regenerate: python3 bin/build-wsplus.py <dir> ...) ---",
        "import base64 as _b64, sys as _sys, types as _types, zlib as _zlib, re as _re",
        "",
        "def _bundle(name, payload):",
        "    mod = _types.ModuleType(name)",
        "    mod.__dict__[\"__builtins__\"] = __builtins__",
        "    mod.__dict__[\"__package__\"] = name.rsplit(\".\", 1)[0] if \".\" in name else \"\"",
        "    mod.__dict__[\"__spec__\"] = None",
        "    src = _zlib.decompress(_b64.b64decode(payload)).decode(\"utf-8\")",
        "    exec(compile(src, name, \"exec\"), mod.__dict__)",
        "    if \".\" in name:",
        "        head, _, tail = name.rpartition(\".\")",
        "        if head not in _sys.modules:",
        "            _sys.modules[head] = _types.ModuleType(head)",
        "        setattr(_sys.modules[head], tail, mod)",
        "    _sys.modules[name] = mod",
        "    return mod",
        "",
    ]
    for name, path in deps:
        lines.append("_M_%s = _bundle(%r, %r)" % (name.rsplit(".", 1)[-1], name, _payload(path.read_text(encoding="utf-8"))))
    lines += [
        "",
        "RX_SYNTAX = %r" % _RX_SYNTAX,
        "RX_COLOR = %r" % _RX_COLOR,
        "RX_STARTEND = %r" % _RX_STARTEND,
        "RX_QUOTED = %r" % _RX_QUOTED,
        "NANORC_PAYLOAD = %r" % nanorc_b64,
        "",
        "def _parse_nanorc_from_string(text):",
        "    ns = _M_nanorc_highlight.__dict__",
        "    syntaxes = []",
        "    current = None",
        "    for raw in text.splitlines():",
        "        line = raw.strip()",
        "        if not line or line.startswith(\"#\"):",
        "            continue",
        "        if line.startswith(\"include \"):",
        "            raise ValueError(\"bundled mtron.nanorc must not use include directives\")",
        "        m = _re.match(RX_SYNTAX, line)",
        "        if m:",
        "            current = ns[\"Syntax\"](m.group(1), m.group(2))",
        "            syntaxes.append(current)",
        "            continue",
        "        m = _re.match(RX_COLOR, line)",
        "        if m and current is not None:",
        "            ci = (m.group(1) == \"icolor\")",
        "            spec = m.group(2)",
        "            rest = m.group(3)",
        "            fg, bg = spec.split(\",\", 1) if \",\" in spec else (spec, \"\")",
        "            se = _re.search(RX_STARTEND, rest)",
        "            if se:",
        "                current.rules.append(ns[\"Rule\"](fg, bg, [], ci, start=se.group(1), end=se.group(2)))",
        "                continue",
        "            pats = _re.findall(RX_QUOTED, rest)",
        "            if pats:",
        "                current.rules.append(ns[\"Rule\"](fg, bg, pats, ci))",
        "    return syntaxes",
        "",
        "_orig_parse_nanorc = _M_nanorc_highlight.parse_nanorc",
        "def _parse_nanorc_fallback(path):",
        "    try:",
        "        return _orig_parse_nanorc(path)",
        "    except FileNotFoundError:",
        "        return _parse_nanorc_from_string(_zlib.decompress(_b64.b64decode(NANORC_PAYLOAD)).decode(\"utf-8\"))",
        "_M_nanorc_highlight.parse_nanorc = _parse_nanorc_fallback",
        "# --------------------------------------------------------------------------",
        "",
    ]
    return "\n".join(lines)


def _build(body, deps, nanorc_text):
    """Assemble: shebang, bootstrap, then the unmodified body minus its shebang line."""
    body_lines = body.split("\n")
    shebang = body_lines[0] if body_lines[0].startswith("#!") else "#!/usr/bin/env python3"
    rest = "\n".join(body_lines[1:])
    parts = [
        shebang,
        _bootstrap(deps, _payload(nanorc_text)),
        "    ### original bin/wsplus (unmodified; regenerated by bin/build-wsplus.py) ###",
        rest,
    ]
    return "\n".join(parts) + "\n"


def _usage():
    sys.stderr.write(
        "usage: build-wsplus.py <output-dir> [<output-dir> ...]\n"
        "\n"
        "bundle bin/wsplus + its bin/lib deps + conf/nanorc/mtron.nanorc into a\n"
        "self-contained <output-dir>/wsplus (runs without any files next to it)\n")


def main(argv):
    out_dirs = argv[1:]
    if not out_dirs or out_dirs[0] in ("-h", "--help"):
        _usage()
        return 2
    if not WSPLUS.exists():
        sys.stderr.write("==>ERROR: missing source: " + str(WSPLUS) + "\n")
        return 1
    if not NANORC.exists():
        sys.stderr.write("==>ERROR: missing source: " + str(NANORC) + "\n")
        return 1

    body = WSPLUS.read_text(encoding="utf-8")
    deps = _discover(body)
    names = {n for n, _ in deps}
    if REQUIRED_MODULE not in names:
        sys.stderr.write("==>ERROR: expected %s in bin/wsplus import set (got %r)\n"
                         % (REQUIRED_MODULE, sorted(names)))
        return 1
    for name, path in deps:
        if not path.exists():
            sys.stderr.write("==>ERROR: " + name + " resolves to missing file: " + str(path) + "\n")
            return 1

    content = _build(body, deps, NANORC.read_text(encoding="utf-8"))
    compile(content, "<generated:wsplus>", "exec")   # syntax self-check, stdlib-only

    for d in out_dirs:
        d = Path(d)
        d.mkdir(parents=True, exist_ok=True)
        target = d / "wsplus"
        if target.exists() and target.read_text(encoding="utf-8") == content:
            print("unchanged " + str(target))
            continue
        target.write_text(content, encoding="utf-8")
        target.chmod(0o755)
        print("wrote " + str(target) + " (deps: " + (", ".join(sorted(names)) or "none") + ")")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
