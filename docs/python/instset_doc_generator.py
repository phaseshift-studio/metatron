#  metatron: A Distributed Computing Language and Virtual Machine
#   Copyright (C) 2025- PhaseShift Studio, LLC
#
#  This program is free software: you can redistribute it and/or modify
#  it under the terms of the GNU Affero General Public License as published by
#  the Free Software Foundation, either version 3 of the License, or
#  (at your option) any later version.
#
#  This program is distributed in the hope that it will be useful,
#  but WITHOUT ANY WARRANTY; without even the implied warranty of
#  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
#  GNU Affero General Public License for more details.
#
#  You should have received a copy of the GNU Affero General Public License
#  along with this program.  If not, see <http://www.gnu.org/licenses/>.

"""
Instruction Set Documentation Generator for metatron

Generates HTML documentation for metatron instruction sets by connecting to a
running metatron instance via WebSocket.  Uses the JSON endpoint for all
structured data queries (clean arrays / dicts, no hand-rolled string parsing)
and the native endpoint only for display strings.

Usage:
    python instset_doc_generator.py /m/mach -o docs/website/instset --website-template
    python instset_doc_generator.py /m /m/mach /m/tble -o docs/html/
"""

from __future__ import annotations

import argparse
import asyncio
import html
import json
import logging
import re
from collections import defaultdict
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from typing import Any, Optional

from runner.mytron import MtronSession

logging.basicConfig(level=logging.INFO, format="%(asctime)s - %(levelname)s - %(message)s")
logger = logging.getLogger(__name__)


# ============================================================================
# Data models
# ============================================================================

@dataclass
class DocInfo:
    obj: str = ""
    dom: str = ""
    rng: str = ""
    args: Any = ""
    desc: str = ""
    example: list[str] = field(default_factory=list)
    raw: str = ""


@dataclass
class TypeInfo:
    vid: str
    name: str
    definition: str
    raw: str = ""
    super_type: str = ""
    super_type_short: str = ""
    super_type_instset: str = ""
    docs: list[DocInfo] = field(default_factory=list)


@dataclass
class InstInfo:
    vid: str
    name: str
    signature: str
    raw: str = ""
    domain: str = ""
    range: str = ""
    domain_full: str = ""
    range_full: str = ""
    docs: list[DocInfo] = field(default_factory=list)


@dataclass
class SpaceInfo:
    vid: str
    name: str
    raw: str = ""
    type_spec: str = ""
    docs: list[DocInfo] = field(default_factory=list)


@dataclass
class ConstInfo:
    vid: str
    name: str
    definition: str = ""
    raw: str = ""
    docs: list[DocInfo] = field(default_factory=list)


@dataclass
class RewriteInfo:
    vid: str
    name: str
    signature: str = ""
    raw: str = ""
    domain: str = ""
    range: str = ""
    domain_full: str = ""
    range_full: str = ""
    docs: list[DocInfo] = field(default_factory=list)


@dataclass
class InstSetInfo:
    vid: str
    name: str
    desc: str = ""
    parent: Optional[str] = None
    children: list[str] = field(default_factory=list)
    types: list[TypeInfo] = field(default_factory=list)
    insts: list[InstInfo] = field(default_factory=list)
    rewrites: list[RewriteInfo] = field(default_factory=list)
    spaces: list[SpaceInfo] = field(default_factory=list)
    consts: list[ConstInfo] = field(default_factory=list)
    full: str = ""


# ============================================================================
# Shared helpers
# ============================================================================

def _extract_instset(uri: str) -> str:
    """Extract parent instset from a type URI: /m/tble/lrow → /m/tble."""
    if not uri:
        return ""
    parts = uri.strip("/").split("/")
    if len(parts) <= 1:
        return "/" + parts[0] if parts else ""
    return "/" + "/".join(parts[:-1])


def _extract_dom_rng(uri: str) -> tuple[str, str]:
    """Extract (domain, range) from a URI query string like ?rng=X&dom=Y."""
    if "?" not in uri:
        return "", ""
    query = uri.split("?", 1)[1]
    rng_m = re.search(r"rng=([^&]+)", query)
    dom_m = re.search(r"dom=([^&]+)", query)
    return dom_m.group(1) if dom_m else "", rng_m.group(1) if rng_m else ""


def _convert_shorthand(text: str) -> str:
    """Convert ?rng=X&dom=Y signatures to the ?X<=Y shorthand form."""
    result: list[str] = []
    i = 0
    while i < len(text):
        if text[i] != "?":
            result.append(text[i])
            i += 1
            continue

        # Scan to end of the query segment (stops at ( or [)
        j = i + 1
        while j < len(text) and text[j] not in "([":
            j += 1
        query = text[i + 1 : j]

        rng_m = re.search(r"rng=([^&]+)", query)
        dom_m = re.search(r"dom=([^&]+)", query)
        rng_v = rng_m.group(1) if rng_m else None
        dom_v = dom_m.group(1) if dom_m else None

        if rng_v and dom_v:
            result.append(f"?{rng_v}<={dom_v}")
        elif rng_v:
            result.append(f"?{rng_v}")
        elif dom_v:
            result.append(f"?<={dom_v}")
        else:
            result.append("?" + query)

        i = j

    return "".join(result)


def _resolve_space_ref(ref: str) -> str:
    """Strip !* or * prefixes from space/super/sub reference strings."""
    ref = ref.strip()
    while ref and ref[0] in "!*":
        ref = ref[1:]
    return ref


def _docq_to_info(docq: dict) -> list[DocInfo]:
    """Convert a ?docq response dict into a DocInfo list (empty if no useful doc)."""
    if not docq or not isinstance(docq, dict):
        return []
    desc = str(docq.get("desc", "")).strip()
    if not desc or desc == "no documentation available":
        return []
    return [DocInfo(
        obj=str(docq.get("obj", "")),
        desc=desc,
        dom=str(docq.get("dom", "")),
        rng=str(docq.get("rng", "")),
        args=docq.get("args", ""),
        example=list(docq.get("example", [])),
        raw=json.dumps(docq),
    )]


# ============================================================================
# Instruction-set fetcher
# ============================================================================

class InstSetDocFetcher:
    """Fetches instruction-set documentation from a running metatron instance."""

    def __init__(self, session: MtronSession):
        self.s = session

    async def fetch(self, vid: str) -> InstSetInfo:
        """Fetch complete documentation for one instruction set."""
        logger.info(f"Fetching instruction set: {vid}")
        name = vid.split("/")[-1] if "/" in vid else vid
        info = InstSetInfo(vid=vid, name=name)

        # ── instset metadata ─────────────────────────────────────────────────
        obj = await self.s.fetch_instset_obj(vid)
        info.desc = str(obj.get("desc", ""))

        space_meta = obj.get("space", {})

        # `super` may be a plain "!*/m" string OR a full space-config dict
        # (when the parent instset's space is inlined).  In the dict case,
        # the parent VID lives in the `pattern` field as e.g. "/m/#".
        super_raw = space_meta.get("super", "")
        if isinstance(super_raw, dict):
            parent_pattern = str(super_raw.get("pattern", ""))
            # strip trailing wildcard segment: "/m/#" → "/m"
            parent_vid = parent_pattern.rstrip("/").removesuffix("/#").removesuffix("/*")
            info.parent = parent_vid or None
        else:
            info.parent = _resolve_space_ref(str(super_raw)) or None

        sub = space_meta.get("sub", [])
        if isinstance(sub, list):
            info.children = [_resolve_space_ref(str(s)) for s in sub]
        elif sub:
            info.children = [_resolve_space_ref(str(sub))]

        # Native full representation (used for the raw accordion in the page header)
        info.full = await self.s.eval_native(f"|*<{vid}>")

        # ── content ──────────────────────────────────────────────────────────
        info.types    = await self._fetch_types(vid, set(info.children))
        info.insts    = await self._fetch_insts(vid)
        info.rewrites = await self._fetch_rewrites(vid)
        info.spaces   = await self._fetch_spaces(vid)
        info.consts   = await self._fetch_consts(vid, info.types, set(info.children))

        logger.info(
            f"  {vid}: {len(info.types)} types, {len(info.insts)} insts, "
            f"{len(info.rewrites)} rewrites, {len(info.spaces)} spaces, "
            f"{len(info.consts)} consts"
        )
        return info

    # ── type fetching ────────────────────────────────────────────────────────

    async def _fetch_types(self, vid: str, children: set[str]) -> list[TypeInfo]:
        """Fetch all types registered with this instset using .>>type>-.

        This captures types at any depth in the instset hierarchy (e.g. wsserver,
        wsclient under /m/web/space/ws/…) that *{vid}/+/ would miss entirely.
        Each server response item is "TypeDef@/path/to/vid"; fetch_type_defs
        parses that into (vid, definition) pairs.
        """
        types = []
        try:
            pairs = await self.s.fetch_type_defs(f"*{vid}.>>type>-")
            for uri, definition in pairs:
                # skip sub-instsets and spaces — spaces have their own section
                if uri in children or "instset::" in definition or "space::" in definition:
                    continue

                name = uri.split("/")[-1].split("?")[0]
                type_info = TypeInfo(
                    vid=uri,
                    name=name,
                    definition=_convert_shorthand(definition),
                    raw=definition,
                )
                await self._resolve_super_type(type_info)
                type_info.docs = _docq_to_info(await self.s.fetch_docq(uri))
                types.append(type_info)
                logger.debug(f"    type: {name}")
        except Exception as e:
            logger.warning(f"_fetch_types({vid}): {e}")
        return types

    async def _resolve_super_type(self, ti: TypeInfo) -> None:
        """Parse the super type from the definition string and resolve its URI."""
        try:
            # Definition looks like:  rec::T[…][…]@/m/mach/console
            # The token before the first ::T is the super type short name.
            if "::T" not in ti.raw:
                return
            super_short = ti.raw.split("::T")[0].strip()
            if not super_short or super_short == ti.name:
                return
            ti.super_type_short = super_short

            # Ask the server to resolve the short name to a full type definition.
            # |*<name> blocks the implicit apply(noobj) so we get the type obj.
            raw = await self.s.eval_native(f"|*<{super_short}>")
            if raw and "@" in raw:
                vid_part = raw.split("@")[-1].strip()
                if vid_part.startswith("/"):
                    ti.super_type = vid_part
                    ti.super_type_instset = _extract_instset(vid_part)
        except Exception as e:
            logger.debug(f"_resolve_super_type({ti.name}): {e}")

    # ── instruction fetching ─────────────────────────────────────────────────

    async def _fetch_insts(self, vid: str) -> list[InstInfo]:
        insts = []
        try:
            pairs = await self.s.fetch_relations(f"*{vid}/inst/+/")
            for uri, definition in pairs:
                # Skip rewrite sub-collections (they appear under inst/rewrite/)
                if "/rewrite/" in uri or "instset::" in definition:
                    continue

                name = uri.split("?")[0].split("/")[-1]
                domain, range_type = _extract_dom_rng(uri)

                inst = InstInfo(
                    vid=_convert_shorthand(uri),
                    name=name,
                    signature=_convert_shorthand(definition),
                    raw=definition,
                    domain=domain,
                    range=range_type,
                    domain_full=domain,
                    range_full=range_type,
                )
                inst.docs = _docq_to_info(await self.s.fetch_docq(uri))
                insts.append(inst)
                logger.debug(f"    inst: {name}")
        except Exception as e:
            logger.warning(f"_fetch_insts({vid}): {e}")
        return insts

    # ── rewrite fetching ─────────────────────────────────────────────────────

    async def _fetch_rewrites(self, vid: str) -> list[RewriteInfo]:
        rewrites = []
        try:
            pairs = await self.s.fetch_relations(f"*{vid}/inst/rewrite/+/")
            for uri, definition in pairs:
                name = uri.split("?")[0].split("/")[-1]
                domain, range_type = _extract_dom_rng(uri)

                rw = RewriteInfo(
                    vid=_convert_shorthand(uri),
                    name=name,
                    signature=_convert_shorthand(definition),
                    raw=definition,
                    domain=domain,
                    range=range_type,
                    domain_full=domain,
                    range_full=range_type,
                )
                rw.docs = _docq_to_info(await self.s.fetch_docq(uri))
                rewrites.append(rw)
                logger.debug(f"    rewrite: {name}")
        except Exception as e:
            logger.debug(f"_fetch_rewrites({vid}): {e}")
        return rewrites

    # ── space fetching ───────────────────────────────────────────────────────

    async def _fetch_spaces(self, vid: str) -> list[SpaceInfo]:
        spaces = []
        try:
            uris = await self.s.fetch_uris(f"*{vid}/space/+/.dom()")
            for uri in uris:
                name = uri.split("/")[-1]
                type_spec = await self.s.eval_native(f"|*<{uri}>")
                sp = SpaceInfo(vid=uri, name=name, raw=uri, type_spec=type_spec)
                sp.docs = _docq_to_info(await self.s.fetch_docq(uri))
                spaces.append(sp)
                logger.debug(f"    space: {uri}")
        except Exception as e:
            logger.warning(f"_fetch_spaces({vid}): {e}")
        return spaces

    # ── const fetching ───────────────────────────────────────────────────────

    async def _fetch_consts(
        self, vid: str, types: list[TypeInfo], children: set[str]
    ) -> list[ConstInfo]:
        consts = []
        try:
            type_uris = {t.vid for t in types}
            pairs = await self.s.fetch_relations(f"*{vid}/+/")
            for uri, definition in pairs:
                # Skip types, sub-instsets, spaces, and instruction collections
                if "::T" in definition:
                    continue
                if uri in type_uris or uri in children:
                    continue
                if "instset::" in definition or "/space/" in uri or "/inst/" in uri:
                    continue

                name = uri.split("/")[-1].split("?")[0]
                ci = ConstInfo(
                    vid=uri,
                    name=name,
                    definition=_convert_shorthand(definition),
                    raw=definition,
                )
                ci.docs = _docq_to_info(await self.s.fetch_docq(uri))
                consts.append(ci)
                logger.debug(f"    const: {name}")
        except Exception as e:
            logger.debug(f"_fetch_consts({vid}): {e}")
        return consts


# ============================================================================
# HTML generator — assets / templates
# ============================================================================

CSS_FILE_PATH  = Path(__file__).parent.parent / "website" / "css" / "instset_doc.css"
INCLUDES_PATH  = Path(__file__).parent.parent / "website" / "includes"
HIGHLIGHT_CDN  = "https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0"


def load_css() -> str:
    if CSS_FILE_PATH.exists():
        return CSS_FILE_PATH.read_text(encoding="utf-8")
    logger.warning(f"CSS not found: {CSS_FILE_PATH}")
    return """
    body { font-family: monospace; background: #1e1e2e; color: #cdd6f4; padding: 2rem; }
    .container { max-width: 1200px; margin: 0 auto; }
    a { color: #89b4fa; }
    pre code.hljs { background: #0d0d14; padding: 1rem; border-radius: 4px; }
    """


def load_website_header(depth: str = "..", subdir: str = "instset") -> str:
    header_path = INCLUDES_PATH / "header.html"
    if not header_path.exists():
        return ""

    content = header_path.read_text(encoding="utf-8")

    for old, new in [
        ('href="images/',   f'href="{depth}/images/'),
        ('src="images/',    f'src="{depth}/images/'),
        ('href="./images/', f'href="{depth}/images/'),
        ('src="./images/',  f'src="{depth}/images/'),
        ('href="css/',      f'href="{depth}/css/'),
        ('href="./css/',    f'href="{depth}/css/'),
        ('href="lib/',      f'href="{depth}/lib/'),
        ('href="./lib/',    f'href="{depth}/lib/'),
        ('src="./highlight/', f'src="{depth}/highlight/'),
        ('src="lib/',       f'src="{depth}/lib/'),
        ('src="./lib/',     f'src="{depth}/lib/'),
        ('src="js/',        f'src="{depth}/js/'),
        ('src="./js/',      f'src="{depth}/js/'),
        ('href="index.html"',     f'href="{depth}/index.html"'),
        ('href="tractatus.html"', f'href="{depth}/tractatus.html"'),
        ("location.href='./articles/",  f"location.href='{depth}/articles/"),
        ("location.href='tractatus.html'", f"location.href='{depth}/tractatus.html'"),
        ("location.href='index.html'",     f"location.href='{depth}/index.html'"),
    ]:
        content = content.replace(old, new)

    if subdir:
        content = content.replace(f'href="{subdir}/', 'href="')
        content = content.replace(f"location.href='{subdir}/", "location.href='")

    return content


def load_website_footer(depth: str = "..") -> str:
    footer_path = INCLUDES_PATH / "footer.html"
    if not footer_path.exists():
        return ""
    content = footer_path.read_text(encoding="utf-8")
    for old, new in [
        ('href="images/', f'href="{depth}/images/'),
        ('src="images/',  f'src="{depth}/images/'),
    ]:
        content = content.replace(old, new)
    return content


# ============================================================================
# HTML generator — page generation
# ============================================================================

class HTMLDocGenerator:
    """Generates HTML documentation from an InstSetInfo."""

    def __init__(
        self,
        instset: InstSetInfo,
        *,
        embed_css: bool = True,
        css_path: str = "../css/instset_doc.css",
        use_website_template: bool = False,
        relative_depth: str = "..",
    ):
        self.instset = instset
        self.embed_css = embed_css
        self.css_path = css_path
        self.use_website_template = use_website_template
        self.depth = relative_depth

    def generate(self, build_number: int = 0) -> str:
        if self.use_website_template:
            return self._with_template(build_number)
        return self._standalone(build_number)

    # ── page wrappers ─────────────────────────────────────────────────────────

    def _with_template(self, build_number: int) -> str:
        header = load_website_header(self.depth)
        footer = load_website_footer(self.depth)
        if not header or not footer:
            logger.warning("Website template unavailable — falling back to standalone")
            return self._standalone(build_number)

        header = header.replace("</head>", f'    <link rel="stylesheet" href="{self.css_path}">\n</head>')
        title  = f"{html.escape(self.instset.name)} - metatron Instruction Set"
        header = re.sub(r"<title>.*?</title>", f"<title>{title}</title>", header)

        return header + self._body_content(build_number) + footer

    def _standalone(self, build_number: int) -> str:
        css = (f"<style>{load_css()}</style>" if self.embed_css else
               f'<link rel="stylesheet" href="{self.depth}/css/metatron.css">\n'
               f'    <link rel="stylesheet" href="{self.css_path}">')
        hljs = (
            f'<script src="{self.depth}/highlight/highlight.min.js"></script>\n'
            f'    <script src="{self.depth}/highlight/languages/mtron.min.js"></script>\n'
            f'    <script>hljs.highlightAll();</script>'
        )
        return f"""<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>{html.escape(self.instset.name)} - metatron instruction set reference</title>
    {css}
</head>
<body>
    <div class="container">
        {self._body_content(build_number)}
    </div>
    {hljs}
</body>
</html>"""

    def _body_content(self, build_number: int) -> str:
        sections = [
            self._section_header(),
            self._section_nav(),
            # self._section_hierarchy(),
            self._section_toc(),
            self._section_consts(),
            self._section_types(),
            self._section_insts(),
            self._section_spaces(),
            self._section_rewrites(),
            self._section_footer(build_number),
        ]
        return '\n'.join(s for s in sections if s)

    # ── page-level sections ───────────────────────────────────────────────────

    def _section_header(self) -> str:
        parent_path = "/".join(self.instset.vid.split("/")[:-1]) or ""
        desc_html = ""
        if self.instset.desc and self.instset.desc != "null":
            desc_html = (
                f'<p class="text-light mt-3 mb-0" '
                f'style="line-height:2.5em;max-width:1000px;margin:0 auto;">'
                f'{self.instset.desc.replace(chr(10), "")}</p>'
            )

        return f"""
<div class="container-xxl py-4">
    <div class="text-center mb-4">
        <h1 class="text-primary glow-text">
            <span class="text-light">{html.escape(parent_path)}/</span>{html.escape(self.instset.name)}
        </h1>
        <p style="line-height:5rem;" class="subtitle text-light">instruction set reference</p>
    </div>
    <div class="text-light">{desc_html}</div>
    <div class="accordion" id="accordianInstSet">
        <div class="accordion-item">
            <h2 class="accordion-header" id="headingOne">
                <button class="accordion-button collapsed" type="button"
                    data-bs-toggle="collapse" data-bs-target="#flush-collapseOne"
                    aria-expanded="false" aria-controls="flush-collapseOne">
                    instset obj
                </button>
            </h2>
            <div id="flush-collapseOne" class="accordion-collapse collapse"
                aria-labelledby="flush-headingOne" data-bs-parent="#accordianInstSet">
                <div class="accordion-body">
                    <pre><code>{html.escape(self.instset.full)}</code></pre>
                </div>
            </div>
        </div>
    </div>
</div>"""

    def _section_nav(self) -> str:
        def btn(href: str, label: str, count: int) -> str:
            if count > 0:
                return (f'<a href="#{href}" class="btn btn-outline-primary">'
                        f'{label} <span class="badge bg-secondary">{count}</span></a>')
            return (f'<button class="btn btn-outline-secondary" disabled>'
                    f'{label} <span class="badge bg-dark">0</span></button>')

        buttons = [
            btn("consts",       "Consts",   len(self.instset.consts)),
            btn("types",        "Types",    len(self.instset.types)),
            btn("instructions", "Insts",    len(self.instset.insts)),
            btn("spaces",       "Spaces",   len(self.instset.spaces)),
            btn("rewrites",     "Rewrites", len(self.instset.rewrites)),
        ]
        return f"""
<div class="container-xxl mb-4">
    <div class="d-flex justify-content-center gap-2 flex-wrap">
        {''.join(buttons)}
    </div>
</div>"""

    def _section_hierarchy(self) -> str:
        items = []
        if self.instset.parent:
            items.append(
                f'<a href="{self._vid_to_filename(self.instset.parent)}" '
                f'class="btn btn-sm btn-outline-secondary">'
                f'<i class="bi bi-arrow-up"></i> {html.escape(self.instset.parent)}</a>'
            )
        for child in self.instset.children:
            items.append(
                f'<a href="{self._vid_to_filename(child)}" '
                f'class="btn btn-sm btn-outline-secondary">'
                f'<i class="bi bi-arrow-down"></i> {html.escape(child)}</a>'
            )
        if not items:
            return ""
        return f"""
<div class="container-xxl mb-4">
    <div class="card">
        <div class="card-header"><h5 class="mb-0 text-primary">Hierarchy</h5></div>
        <div class="card-body d-flex flex-wrap gap-2">{''.join(items)}</div>
    </div>
</div>"""

    def _section_toc(self) -> str:
        def toc_pill(href: str, name: str, badge_class: str, badge_letter: str) -> str:
            """Compact fixed-width pill: [  name   ][X] arranged in a flex-wrap grid."""
            return (
                f'<a href="#{href}" '
                f'class="d-inline-flex justify-content-between align-items-center '
                f'bg-dark text-light border border-secondary text-decoration-none '
                f'px-2 py-1 me-1 mb-1" '
                f'style="min-width:180px;font-size:0.8rem;">'
                f'<span class="code">{html.escape(name)}</span>'
                f'<span class="badge {badge_class} ms-2">{badge_letter}</span>'
                f'</a>'
            )

        def type_branch(t: TypeInfo) -> str:
            """Return the full parent path (relative to root) as the group key.

            VID structure examples (root = /m/web):
              /m/web/content_type              → root ""
              /m/web/space/httpspace           → "space"
              /m/web/space/wsspace             → "space"
              /m/web/space/wsspace/wsserver    → "space/wsspace"
              /m/web/space/wsspace/mtron_ws    → "space/wsspace"
            For /m:
              /m/instset                       → root ""
              /m/space/memspace                → "space"
              /m/space/qproc/subq              → "space/qproc"
              /m/space/qproc/subq/sub          → "space/qproc/subq"
            """
            if not t.vid:
                return ""
            root = self.instset.vid.rstrip("/")
            remainder = t.vid[len(root):].lstrip("/")
            parts = [p for p in remainder.split("/") if p]
            # direct child of root → root group; deeper → full parent path
            return "" if len(parts) <= 1 else "/".join(parts[:-1])

        def types_section() -> str:
            if not self.instset.types:
                return ""
            # group by branch, root first then sorted child names
            from collections import defaultdict as _dd
            branch_map: dict[str, list] = _dd(list)
            for t in sorted(self.instset.types, key=lambda x: x.name):
                branch_map[type_branch(t)].append(t)

            root_types = branch_map.pop("", [])
            # sort by depth first, then lexicographically → natural tree order
            child_branches = sorted(branch_map.keys(), key=lambda x: (x.count("/"), x))

            def branch_label(branch: str) -> str:
                """Vertical breadcrumb: dim parent path on top, bold terminal segment below."""
                parts = [p for p in branch.split("/") if p]
                col = 'style="min-width:6rem;max-width:6rem;text-align:right;padding-right:0.6rem;flex-shrink:0;"'
                if len(parts) <= 1:
                    seg = html.escape(parts[0]) if parts else ""
                    return (
                        f'<div {col}>'
                        f'<small class="fw-bold text-secondary" style="font-family:monospace;">{seg}</small>'
                        f'</div>'
                    )
                # multi-segment: stack parent dim above terminal bold
                parent = html.escape("/".join(parts[:-1]))
                terminal = html.escape(parts[-1])
                return (
                    f'<div {col} style="min-width:6rem;max-width:6rem;text-align:right;'
                    f'padding-right:0.6rem;flex-shrink:0;line-height:1.25;">'
                    f'<div style="font-size:0.6rem;opacity:0.45;font-family:monospace;'
                    f'white-space:nowrap;">{parent}&thinsp;/</div>'
                    f'<div><small class="fw-bold text-secondary" '
                    f'style="font-family:monospace;">{terminal}</small></div>'
                    f'</div>'
                )

            rows_html = []
            # root-level types
            if root_types:
                pills = "".join(toc_pill(f"type-{t.name}", t.name, "bg-primary", "T") for t in root_types)
                if child_branches:
                    rows_html.append(
                        f'<div class="d-flex align-items-start mb-1">'
                        f'{branch_label(self.instset.name)}'
                        f'<div class="d-flex flex-wrap">{pills}</div></div>'
                    )
                else:
                    rows_html.append(f'<div class="d-flex flex-wrap">{pills}</div>')

            for branch in child_branches:
                pills = "".join(toc_pill(f"type-{t.name}", t.name, "bg-primary", "T") for t in branch_map[branch])
                rows_html.append(
                    f'<div class="d-flex align-items-start mb-1">'
                    f'{branch_label(branch)}'
                    f'<div class="d-flex flex-wrap">{pills}</div></div>'
                )

            return f"""
        <div class="mb-3">
            <h6 class="text-primary mb-2">Types</h6>
            {''.join(rows_html)}
        </div>"""

        flat_groups = [
            ("Constants",    "bg-secondary",         "C",
             [toc_pill(f"const-{c.name}",   c.name, "bg-secondary",        "C") for c in sorted(self.instset.consts,   key=lambda x: x.name)]),
            ("Instructions", "bg-success",           "I",
             [toc_pill(f"inst-{n}",         n,      "bg-success",          "I") for n in sorted({i.name for i in self.instset.insts})]),
            ("Spaces",       "bg-info text-dark",    "S",
             [toc_pill(f"space-{s.name}",   s.name, "bg-info text-dark",   "S") for s in sorted(self.instset.spaces,   key=lambda x: x.name)]),
            ("Rewrites",     "bg-warning text-dark", "R",
             [toc_pill(f"rewrite-{r.name}", r.name, "bg-warning text-dark","R") for r in sorted(self.instset.rewrites, key=lambda x: x.name)]),
        ]

        column_divs = [types_section()]
        for title, _, _, pills in flat_groups:
            if not pills:
                continue
            column_divs.append(f"""
        <div class="mb-3">
            <h6 class="text-primary mb-2">{title}</h6>
            <div class="d-flex flex-wrap">{''.join(pills)}</div>
        </div>""")

        column_divs = [d for d in column_divs if d]
        if not column_divs:
            return ""
        return f"""
<div class="container-xxl mb-4">
    <div class="card">
        <div class="card-header"><h5 class="mb-0 text-primary">Index</h5></div>
        <div class="card-body">{''.join(column_divs)}</div>
    </div>
</div>"""

    def _section_types(self) -> str:
        if not self.instset.types:
            return ""

        cards = []
        for t in sorted(self.instset.types, key=lambda x: x.name):
            definition_html = ""
            if t.definition:
                definition_html = (
                    f'<div class="card-body p-2">'
                    f'<pre class="mb-0"><code class="language-mtron">'
                    f'{html.escape(t.definition)}</code></pre></div>'
                )

            refines_html = ""
            if t.super_type_short:
                super_name   = t.super_type.split("/")[-1] if t.super_type else t.super_type_short
                display_name = f"{t.super_type_short}::T"
                if t.super_type_instset:
                    target = f'{self._vid_to_filename(t.super_type_instset)}#type-{html.escape(super_name)}'
                    refines_html = (
                        f'<span class="ms-2 text-muted instset-doc-small-code">refines '
                        f'<a href="{target}" class="instset-doc-small-code text-info code">'
                        f'{html.escape(display_name)}</a></span>'
                    )
                else:
                    refines_html = (
                        f'<span class="ms-2 text-muted instset-doc-small-code">refines '
                        f'<span class="code instset-doc-small-code text-info">'
                        f'{html.escape(display_name)}</span></span>'
                    )

            gid      = f"type-{html.escape(t.name)}"
            doc_html = self._multi_doc(t.docs, gid)
            cards.append(f"""
<div class="card mb-3" id="{gid}">
    <div class="card-header d-flex justify-content-between align-items-center py-2">
        <span>
            <span class="code text-primary fw-bold">{html.escape(t.name)}::T</span>
            {refines_html}
        </span>
        <small class="text-muted code">{html.escape(t.vid)}</small>
    </div>
    {definition_html}
    {doc_html}
</div>""")

        return f"""
<div class="container-xxl mb-4" id="types">
    <h3 class="text-primary mb-3">Types <span class="badge bg-primary">{len(self.instset.types)}</span></h3>
    {''.join(cards)}
</div>"""

    def _section_insts(self) -> str:
        if not self.instset.insts:
            return ""

        groups: dict[str, list[InstInfo]] = defaultdict(list)
        for inst in self.instset.insts:
            groups[inst.name].append(inst)

        cards = []
        for name in sorted(groups):
            insts = groups[name]
            gid   = f"inst-{html.escape(name)}"

            # De-duplicate docs by raw content, build variant → tab index map
            unique_docs: list[DocInfo] = []
            seen_raw: dict[str, int] = {}
            variant_tab: dict[str, int] = {}
            for inst in insts:
                for doc in inst.docs:
                    if doc.raw not in seen_raw:
                        seen_raw[doc.raw] = len(unique_docs)
                        unique_docs.append(doc)
                    variant_tab[inst.signature] = seen_raw[doc.raw]

            sigs = []
            for inst in insts:
                tab_id = f"{gid}-doc-{variant_tab.get(inst.signature, 0)}"
                sigs.append(
                    f'<pre class="mb-1 clickable-signature" style="font-size:0.85rem;cursor:pointer;" '
                    f'onclick="document.getElementById(\'{tab_id}-tab\').click();'
                    f'document.getElementById(\'{tab_id}\').scrollIntoView({{behavior:\'smooth\',block:\'center\'}});">'
                    f'<code class="language-mtron">{html.escape(inst.signature)}</code></pre>'
                )

            type_sig = self._type_signature(insts[0].domain, insts[0].range,
                                            insts[0].domain_full, insts[0].range_full)
            doc_html = self._multi_doc(unique_docs, gid)

            cards.append(f"""
<div class="card mb-3" id="{gid}">
    <div class="card-header d-flex justify-content-between align-items-center py-2
                text-light border-bottom border-secondary">
        <span>
            <span class="code text-primary fw-bold">{html.escape(name)}</span>
            {type_sig}
        </span>
        <small class="text-muted code">{html.escape(insts[0].vid)}</small>
    </div>
    <div class="card-body p-2">{''.join(sigs)}</div>
    {doc_html}
</div>""")

        return f"""
<div class="container-xxl mb-4" id="instructions">
    <h3 class="text-primary mb-3">Instructions <span class="badge bg-success">{len(self.instset.insts)}</span></h3>
    {''.join(cards)}
</div>"""

    def _section_rewrites(self) -> str:
        if not self.instset.rewrites:
            return ""

        cards = []
        for rw in sorted(self.instset.rewrites, key=lambda x: x.name):
            gid  = f"rewrite-{html.escape(rw.name)}"
            sig  = (f'<div class="card-body p-2"><pre class="mb-0">'
                    f'<code class="language-mtron">{html.escape(rw.signature)}</code>'
                    f'</pre></div>') if rw.signature else ""
            type_sig = self._type_signature(rw.domain, rw.range, rw.domain_full, rw.range_full)
            doc_html = self._multi_doc(rw.docs, gid)

            cards.append(f"""
<div class="card mb-3" id="{gid}">
    <div class="card-header d-flex justify-content-between align-items-center py-2">
        <span>
            <span class="code text-warning fw-bold">{html.escape(rw.name)}</span>
            {type_sig}
        </span>
        <small class="text-muted code">{html.escape(rw.vid)}</small>
    </div>
    {sig}
    {doc_html}
</div>""")

        return f"""
<div class="container-xxl mb-4" id="rewrites">
    <h3 class="text-primary mb-3">Rewrites <span class="badge bg-warning text-dark">{len(self.instset.rewrites)}</span></h3>
    {''.join(cards)}
</div>"""

    def _section_spaces(self) -> str:
        if not self.instset.spaces:
            return ""

        cards = []
        for sp in sorted(self.instset.spaces, key=lambda x: x.name):
            gid      = f"space-{html.escape(sp.name)}"
            spec_html = (f'<div class="mt-2"><pre class="mb-0">'
                         f'<code class="language-mtron">{html.escape(sp.type_spec)}</code>'
                         f'</pre></div>') if sp.type_spec else ""
            doc_html = self._multi_doc(sp.docs, gid)

            cards.append(f"""
<div class="card mb-3" id="{gid}">
    <div class="card-header d-flex justify-content-between align-items-center py-2">
        <a href="{self._vid_to_filename(sp.vid)}"
           class="code text-primary fw-bold text-decoration-none">{html.escape(sp.name)}</a>
        <small class="text-muted code">{html.escape(sp.vid)}</small>
    </div>
    {spec_html}
    {doc_html}
</div>""")

        return f"""
<div class="container-xxl mb-4" id="spaces">
    <h3 class="text-primary mb-3">Spaces <span class="badge bg-info">{len(self.instset.spaces)}</span></h3>
    {''.join(cards)}
</div>"""

    def _section_consts(self) -> str:
        if not self.instset.consts:
            return ""

        cards = []
        for ci in sorted(self.instset.consts, key=lambda x: x.name):
            gid  = f"const-{html.escape(ci.name)}"
            defn = (f'<div class="card-body p-2"><pre class="mb-0">'
                    f'<code class="language-mtron">{html.escape(ci.definition)}</code>'
                    f'</pre></div>') if ci.definition else ""
            doc_html = self._multi_doc(ci.docs, gid)

            cards.append(f"""
<div class="card mb-3" id="{gid}">
    <div class="card-header d-flex justify-content-between align-items-center py-2">
        <span class="code text-secondary fw-bold">{html.escape(ci.name)}</span>
        <small class="text-muted code">{html.escape(ci.vid)}</small>
    </div>
    {defn}
    {doc_html}
</div>""")

        return f"""
<div class="container-xxl mb-4" id="consts">
    <h3 class="text-primary mb-3">Constants <span class="badge bg-secondary">{len(self.instset.consts)}</span></h3>
    {''.join(cards)}
</div>"""

    def _section_footer(self, build_number: int) -> str:
        ts = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        return f"""
<div class="container-xxl py-3 text-center">
    <hr class="border-secondary">
    <small class="text-muted">
        generated by metatron instset doc generator on build {build_number}-{ts}<br>
        © PhaseShift Studio, LLC
    </small>
</div>"""

    # ── documentation formatting ──────────────────────────────────────────────

    def _multi_doc(self, docs: list[DocInfo], gid: str) -> str:
        """Render one or more DocInfo objects; tabbed pills when multiple."""
        if not docs:
            return ""
        if len(docs) == 1:
            return self._single_doc(docs[0])

        pills    = []
        contents = []
        for i, doc in enumerate(docs):
            tab_id  = f"{gid}-doc-{i}"
            active  = "active" if i == 0 else ""
            show    = "show active" if i == 0 else ""
            pills.append(f"""
<li class="nav-item" role="presentation">
    <button class="nav-link py-1 px-2 {active} text-start h-100"
        id="{tab_id}-tab" data-bs-toggle="pill" data-bs-target="#{tab_id}"
        type="button" role="tab" aria-selected="{'true' if i == 0 else 'false'}"
        style="font-size:0.75rem;max-width:60px;">
        <i class="fas fa-info-circle me-1"></i> {i + 1}
    </button>
</li>""")
            contents.append(f"""
<div class="tab-pane fade {show}" id="{tab_id}" role="tabpanel" aria-labelledby="{tab_id}-tab">
    {self._single_doc(doc)}
</div>""")

        return f"""
<div class="card-body border-top p-0">
    <ul class="nav nav-pills p-2 bg-dark" id="{gid}-pills" role="tablist">
        <li class="nav-item disabled me-2">
            <span class="nav-link disabled py-1 px-0 text-muted" style="font-size:0.75rem;">polymorph:</span>
        </li>
        {''.join(pills)}
    </ul>
    <div class="tab-content" id="{gid}-content">
        {''.join(contents)}
    </div>
</div>"""

    def _single_doc(self, doc: Optional[DocInfo]) -> str:
        """Render one DocInfo block: description, dom/rng signature, args, examples."""
        if not doc:
            return ""

        parts = []

        # ── description ───────────────────────────────────────────────────────
        if doc.desc:
            parts.append(
                f'<div class="card-body border-top py-2">'
                f'<p class="mb-0 text-light">{html.escape(doc.desc)}</p></div>'
            )

        # ── signature + args (one visual unit, no internal separator) ───────────
        has_sig = bool(doc.dom or doc.rng)
        has_args = bool(doc.args and isinstance(doc.args, dict))
        if has_sig or has_args:
            inner = []
            if has_sig:
                dom_txt = html.escape(doc.dom) if doc.dom else "?"
                rng_txt = html.escape(doc.rng) if doc.rng else "?"
                sig_line = f'"{dom_txt}" <span class="text-light">=&gt;</span> "{rng_txt}"'
                sig_label = '<small class="text-muted fw-bold">sig:</small>\n    ' if has_args else ""
                inner.append(
                    f'{sig_label}<pre class="mb-0 text-bright" style="font-family:monospace;font-size:0.8em;">'
                    f'{sig_line}</pre>'
                )
            if has_args:
                rows = "\n".join(
                    f'  {html.escape(str(k))} <span class="text-light">=&gt;</span>'
                    f' <span class="text-info">"{html.escape(str(v))}"</span>'
                    for k, v in doc.args.items()
                )
                inner.append(
                    f'<small class="text-muted fw-bold{" mt-2 d-block" if has_sig else ""}">args:</small>\n'
                    f'    <pre class="mb-0 text-bright" style="font-family:monospace;font-size:0.8em;">{rows}</pre>'
                )
            parts.append(
                f'<div class="card-body py-2">\n    ' + "\n    ".join(inner) + "\n</div>"
            )

        # ── examples ──────────────────────────────────────────────────────────
        if doc.example:
            examples = "\n".join(html.escape(str(e)) for e in doc.example)
            parts.append(f"""
<div class="card-body border-top py-2">
    <small class="text-muted fw-bold">examples:</small>
    <pre><code class="language-mtron" style="padding:0 0.75rem 0 !important">{examples}</code></pre>
</div>""")

        return "".join(parts)

    # ── type-signature helpers ────────────────────────────────────────────────

    def _type_signature(
        self,
        domain: str,
        range_type: str,
        domain_full: str = "",
        range_full: str = "",
    ) -> str:
        if not domain and not range_type:
            return ""
        parts = []
        if domain:
            parts.append(
                f'<span class="instset-doc-small-code">'
                f'{self._type_link(domain, domain_full or domain, "text-info", "domain")}'
                f'</span>'
            )
        parts.append('<span class="text-muted mx-1">=&gt;</span>')
        if range_type:
            parts.append(
                f'<span class="instset-doc-small-code">'
                f'{self._type_link(range_type, range_full or range_type, "text-success", "range")}'
                f'</span>'
            )
        return f'<span class="ms-1">{"".join(parts)}</span>'

    def _type_link(
        self, short: str, full: str, css_class: str, tooltip: str = ""
    ) -> str:
        if not full:
            return f'<span class="code {css_class}">{html.escape(short)}</span>'

        type_name  = full.split("/")[-1]
        qless_name = type_name.split("?")[0]
        cless_name = qless_name.split("{")[0]

        cardinality = ""
        if "{" in type_name:
            card = type_name.split("{")[1].split("}")[0]
            cardinality = {"*": "maybe some ", "?": "maybe ", "+": "some ", "0": "noobj "}.get(card, "")

        if cless_name.isupper():
            return (
                f'<a href="#" data-bs-toggle="tooltip" '
                f'title="{cardinality}generic {tooltip}" class="code {css_class}">'
                f'{html.escape(short)}</a>'
            )

        type_instset = _extract_instset(full)
        if type_instset and type_instset != self.instset.vid:
            target = f'{self._vid_to_filename(type_instset)}#type-{html.escape(cless_name)}'
        else:
            target = f'#type-{html.escape(cless_name)}'

        return (
            f'<a href="{target}" data-bs-toggle="tooltip" '
            f'title="{cardinality}{tooltip}" class="code {css_class}">'
            f'{html.escape(short)}</a>'
        )

    # ── utilities ─────────────────────────────────────────────────────────────

    def _vid_to_filename(self, vid: str) -> str:
        return vid.replace("/", "_").strip("_") + ".html"


# ============================================================================
# Index-page generator
# ============================================================================

def generate_index_page(
    instsets: list[InstSetInfo],
    *,
    embed_css: bool = True,
    css_path: str = "../css/instset_doc.css",
    use_website_template: bool = False,
    relative_depth: str = "..",
    build_number: int = 0,
) -> str:
    cards = []
    for info in sorted(instsets, key=lambda x: x.vid):
        filename = info.vid.replace("/", "_").strip("_") + ".html"
        cards.append(f"""
<div class="col-md-6 col-lg-4">
    <a href="{filename}" class="text-decoration-none">
        <div class="card h-100">
            <div class="card-body">
                <h5 class="card-title code text-primary">{html.escape(info.vid)}</h5>
                <div class="d-flex gap-2 flex-wrap">
                    <span class="badge bg-primary">{len(info.types)} types</span>
                    <span class="badge bg-success">{len(info.insts)} instructions</span>
                    <span class="badge bg-info text-dark">{len(info.spaces)} spaces</span>
                </div>
            </div>
        </div>
    </a>
</div>""")

    ts      = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    content = f"""
<div class="container-xxl py-4">
    <div class="text-center mb-5">
        <h1 class="text-primary glow-text">metatron</h1>
        <p class="subtitle text-light">instruction set documentation</p>
    </div>
    <div class="row g-3">{''.join(cards)}</div>
    <div class="py-3 text-center mt-5">
        <hr class="border-secondary">
        <small class="text-muted">
            metatron instset doc generator on build {build_number}-{ts}<br>
            © PhaseShift Studio, LLC
        </small>
    </div>
</div>"""

    if use_website_template:
        header = load_website_header(relative_depth)
        footer = load_website_footer(relative_depth)
        if header and footer:
            header = header.replace("</head>", f'    <link rel="stylesheet" href="{css_path}">\n</head>')
            header = re.sub(r"<title>.*?</title>", "<title>metatron instruction sets</title>", header)
            return header + content + footer

    css = (f"<style>{load_css()}</style>" if embed_css else
           f'<link rel="stylesheet" href="{relative_depth}/css/metatron.css">\n'
           f'    <link rel="stylesheet" href="{css_path}">')

    return f"""<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>metatron instruction set reference</title>
    {css}
</head>
<body>
    <div class="container">{content}</div>
</body>
</html>"""


# ============================================================================
# Entry points
# ============================================================================

async def main_async(args: argparse.Namespace) -> None:
    session = MtronSession(host=args.host, timeout=args.timeout)
    fetcher = InstSetDocFetcher(session)

    output_dir = Path(args.output)
    output_dir.mkdir(parents=True, exist_ok=True)

    await session.connect()
    try:
        instsets = []
        for vid in args.instsets:
            try:
                info = await fetcher.fetch(vid)
                instsets.append(info)

                gen  = HTMLDocGenerator(
                    info,
                    embed_css=not args.link_css,
                    css_path="../css/instset_doc.css",
                    use_website_template=args.website_template,
                    relative_depth=args.relative_depth,
                )
                page     = gen.generate(args.build)
                filename = vid.replace("/", "_").strip("_") + ".html"
                filepath = output_dir / filename
                filepath.write_text(page, encoding="utf-8")
                logger.info(f"Generated: {filepath}")

            except Exception as e:
                logger.error(f"Failed to process {vid}: {e}")
                if args.verbose:
                    import traceback
                    traceback.print_exc()

        if len(instsets) > 1:
            index_html = generate_index_page(
                instsets,
                embed_css=not args.link_css,
                css_path="../css/instset_doc.css",
                use_website_template=args.website_template,
                relative_depth=args.relative_depth,
                build_number=args.build,
            )
            (output_dir / "index.html").write_text(index_html, encoding="utf-8")
            logger.info(f"Generated: {output_dir / 'index.html'}")

    finally:
        await session.close()


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Generate HTML documentation for metatron instruction sets.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
    python instset_doc_generator.py /m/mach
    python instset_doc_generator.py /m /m/mach /m/tble -o docs/html/
    python instset_doc_generator.py /m/mach --link-css
    python instset_doc_generator.py /m/mach -o docs/website/instset --website-template
        """,
    )
    parser.add_argument("instsets", nargs="+",
                        help="Instruction set VIDs to document (e.g. /m, /m/mach, /m/llm)")
    parser.add_argument("-o", "--output",   default="./instset_docs",
                        help="Output directory (default: ./instset_docs)")
    parser.add_argument("--host",           default="ws://localhost:8555/mtron",
                        help="metatron WebSocket host (default: ws://localhost:8555/mtron)")
    parser.add_argument("--timeout",        type=int, default=30,
                        help="WebSocket timeout in seconds (default: 30)")
    parser.add_argument("--link-css",       action="store_true",
                        help="Link to external CSS instead of embedding it")
    parser.add_argument("--build",          type=int, default=0,
                        help="Build number stamped into generated docs")
    parser.add_argument("--website-template", action="store_true",
                        help="Use website header.html/footer.html for consistent site styling")
    parser.add_argument("--relative-depth", default="..",
                        help='Relative path to website root from output dir (default: "..")')
    parser.add_argument("-v", "--verbose",  action="store_true",
                        help="Enable verbose/debug output")

    args = parser.parse_args()
    if args.verbose:
        logging.getLogger().setLevel(logging.DEBUG)

    asyncio.run(main_async(args))


if __name__ == "__main__":
    main()
