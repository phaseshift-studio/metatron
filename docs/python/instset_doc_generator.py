#  Metatron: A Distributed Computing Language and Virtual Machine
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
Instruction Set Documentation Generator for Metatron

Generates HTML documentation for metatron instruction sets using highlight.js
with the custom mtron.min.js language grammar for syntax highlighting.

Usage:
    python instset_doc_generator.py /m/mach -o docs/html/
    python instset_doc_generator.py /m /m/mach /m/tble /m/doc /m/grph /m/web /m/llm -o docs/html/

The generator connects to a running metatron instance via WebSocket and uses:
    - '/*/instset/+/'./m/web/inst/doc()   : Types with pretty formatting
    - '/*/instset/inst/+/'./m/web/inst/doc() : Instructions with pretty formatting

Output uses highlight.js with mtron.min.js for syntax coloring.
"""

from __future__ import annotations

import argparse
import asyncio
import html
import logging
import re
import shutil
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from typing import Optional, List

import websockets

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)


# ============================================================================
# Metatron WebSocket Client
# ============================================================================

class MetatronClient:
    """WebSocket client for communicating with a running metatron instance."""

    def __init__(self, host: str = "ws://127.0.0.1:8999", timeout: int = 30):
        self.host = host
        self.timeout = timeout
        self._ws = None

    async def connect(self):
        """Establish WebSocket connection."""
        logger.info(f"Connecting to metatron at {self.host}")
        self._ws = await websockets.connect(self.host)
        logger.info("Connected successfully")

    async def close(self):
        """Close WebSocket connection."""
        if self._ws:
            await self._ws.close()
            logger.info("Connection closed")

    async def eval(self, expression: str) -> str:
        """Evaluate a metatron expression and return the raw result."""
        if not self._ws:
            raise RuntimeError("Not connected. Call connect() first.")

        logger.debug(f"Evaluating: {expression}")
        await self._ws.send(expression)
        result = await asyncio.wait_for(self._ws.recv(), timeout=self.timeout)
        result_str = result if isinstance(result, str) else result.decode('utf-8')
        logger.debug(f"Result: {result_str[:200]}..." if len(result_str) > 200 else f"Result: {result_str}")
        return result_str.strip()

    async def eval_doc(self, expression: str) -> List[str]:
        """
        Evaluate expression via /m/web/inst/doc() for pretty-printed output.

        Returns a list of entries split by '%%%' separator.
        Each entry is in mtron syntax ready for highlight.js.
        """
        # Wrap expression in quotes and call doc()
        query = f"'{expression}'./m/web/inst/doc()"
        result = await self.eval(query)

        if not result or result == "noobj" or result.startswith("</m/fail>"):
            return []

        # Result format: </m/str>::'entry1%%%entry2%%%...'
        # Strip the </m/str>:: prefix and quotes
        if result.startswith("</m/str>::"):
            result = result[len("</m/str>::"):]
        if result.startswith("'") and result.endswith("'"):
            result = result[1:-1]

        # Split on %%% separator
        if not result:
            return []

        entries = result.split("%%%")
        return [e.strip() for e in entries if e.strip()]


# ============================================================================
# Data Models
# ============================================================================

@dataclass
class TypeInfo:
    """Information about a type defined in an instruction set."""
    vid: str  # Full path (e.g., /m/mach/machine)
    name: str  # Short name (e.g., machine)
    definition: str  # Pretty-printed definition in mtron syntax
    raw: str = ""  # Raw entry string


@dataclass
class InstInfo:
    """Information about an instruction."""
    vid: str  # Full path with signature (e.g., /m/mach/inst/run?rng=...&dom=...)
    name: str  # Short name (e.g., run)
    signature: str  # Pretty-printed signature in mtron syntax
    raw: str = ""  # Raw entry string


@dataclass
class SpaceInfo:
    """Information about a sub-space/sub-instruction set."""
    vid: str
    name: str
    raw: str = ""


@dataclass
class InstSetInfo:
    """Complete information about an instruction set."""
    vid: str  # e.g., /m/mach
    name: str  # e.g., mach
    parent: Optional[str] = None  # Super instruction set
    children: List[str] = field(default_factory=list)  # Sub instruction sets
    types: List[TypeInfo] = field(default_factory=list)
    insts: List[InstInfo] = field(default_factory=list)
    spaces: List[SpaceInfo] = field(default_factory=list)
    raw_metadata: str = ""


# ============================================================================
# Documentation Fetcher
# ============================================================================

class InstSetDocFetcher:
    """Fetches instruction set documentation from a running metatron instance."""

    def __init__(self, client: MetatronClient):
        self.client = client

    async def fetch_instset(self, vid: str) -> InstSetInfo:
        """Fetch complete documentation for an instruction set."""
        logger.info(f"Fetching instruction set: {vid}")

        name = vid.split('/')[-1] if '/' in vid else vid
        info = InstSetInfo(vid=vid, name=name)

        # Fetch the instruction set space metadata
        try:
            info.raw_metadata = await self.client.eval(f"*{vid}/")
        except Exception as e:
            logger.warning(f"Could not fetch metadata for {vid}: {e}")

        # Fetch types via doc() for pretty formatting
        info.types = await self._fetch_types(vid)

        # Fetch instructions via doc() for pretty formatting
        info.insts = await self._fetch_instructions(vid)

        # Fetch spaces (sub-instruction sets)
        info.spaces = await self._fetch_spaces(vid)

        # Check for parent/child relationships in metadata
        self._parse_hierarchy(info)

        logger.info(f"Fetched {vid}: {len(info.types)} types, {len(info.insts)} instructions, {len(info.spaces)} spaces")
        return info

    async def _fetch_types(self, vid: str) -> List[TypeInfo]:
        """Fetch all types defined by this instruction set using doc() for pretty output."""
        types = []
        try:
            # Use doc() for pretty-printed output
            entries = await self.client.eval_doc(f"*{vid}/+/")
            for entry in entries:
                type_info = self._parse_type_entry(entry)
                if type_info:
                    types.append(type_info)
                    logger.debug(f"  Found type: {type_info.name}")
        except Exception as e:
            logger.warning(f"Could not fetch types for {vid}: {e}")
        return types

    async def _fetch_instructions(self, vid: str) -> List[InstInfo]:
        """Fetch all instructions defined by this instruction set using doc() for pretty output."""
        insts = []
        try:
            # Use doc() for pretty-printed output
            entries = await self.client.eval_doc(f"*{vid}/inst/+/")
            for entry in entries:
                inst_info = self._parse_inst_entry(entry)
                if inst_info:
                    insts.append(inst_info)
                    logger.debug(f"  Found instruction: {inst_info.name}")
        except Exception as e:
            logger.warning(f"Could not fetch instructions for {vid}: {e}")
        return insts

    async def _fetch_spaces(self, vid: str) -> List[SpaceInfo]:
        """Fetch all sub-spaces defined by this instruction set."""
        spaces = []
        try:
            entries = await self.client.eval_doc(f"*{vid}/space/+/")
            for entry in entries:
                # Entry format: /path/to/space=>...
                if "=>" in entry:
                    space_vid = entry.split("=>")[0].strip()
                else:
                    space_vid = entry.strip()
                if space_vid:
                    name = space_vid.split('/')[-1]
                    spaces.append(SpaceInfo(vid=space_vid, name=name, raw=entry))
                    logger.debug(f"  Found space: {space_vid}")
        except Exception as e:
            logger.warning(f"Could not fetch spaces for {vid}: {e}")
        return spaces

    def _parse_hierarchy(self, info: InstSetInfo):
        """Parse parent/child instruction set relationships from metadata."""
        try:
            if "super" in info.raw_metadata:
                match = re.search(r'super\s*=>\s*[<!\*]*([^>,\]\s]+)', info.raw_metadata)
                if match:
                    info.parent = match.group(1)
            if "sub" in info.raw_metadata:
                matches = re.findall(r'sub\s*=>\s*[<!\*]*([^>,\]\s]+)', info.raw_metadata)
                info.children = matches
        except Exception as e:
            logger.warning(f"Could not parse hierarchy: {e}")

    def _parse_type_entry(self, entry: str) -> Optional[TypeInfo]:
        """
        Parse a type entry from doc() output.

        Format: /m/mach/machine=>rec::T[isa([...])]@/m/mach/machine
        """
        if "=>" not in entry:
            return None

        # Split on first => to get vid and definition
        arrow_pos = self._find_top_level_arrow(entry)
        if arrow_pos < 0:
            return None

        vid = entry[:arrow_pos].strip()
        definition = entry[arrow_pos + 2:].strip()

        # Extract simple name from vid
        name = vid.split('/')[-1].split('?')[0]

        # Convert dom=&rng= to rng<=dom in definition
        definition = self._convert_signature_shorthand(definition)

        return TypeInfo(vid=vid, name=name, definition=definition, raw=entry)

    def _parse_inst_entry(self, entry: str) -> Optional[InstInfo]:
        """
        Parse an instruction entry from doc() output.

        Format: /m/mach/inst/run?rng=core&dom=core=>run?rng=core&dom=core(){<j>}
        """
        if "=>" not in entry:
            return None

        # Split on first => to get vid and signature
        arrow_pos = self._find_top_level_arrow(entry)
        if arrow_pos < 0:
            return None

        vid = entry[:arrow_pos].strip()
        signature = entry[arrow_pos + 2:].strip()

        # Extract simple name from vid (before ?)
        base_path = vid.split('?')[0]
        name = base_path.split('/')[-1]

        # Convert dom=&rng= to rng<=dom in both vid and signature
        vid = self._convert_signature_shorthand(vid)
        signature = self._convert_signature_shorthand(signature)

        return InstInfo(vid=vid, name=name, signature=signature, raw=entry)

    def _find_top_level_arrow(self, text: str) -> int:
        """Find the position of the first => at top level (not inside brackets)."""
        depth = 0
        i = 0
        while i < len(text):
            char = text[i]
            if char in '[{(<':
                depth += 1
            elif char in ']})>':
                depth -= 1
            elif depth == 0 and text[i:i+2] == '=>':
                return i
            i += 1
        return -1

    def _convert_signature_shorthand(self, text: str) -> str:
        """
        Convert dom=X&rng=Y or rng=X&dom=Y to X<=Y shorthand.

        Examples:
            ?rng=core&dom=core -> ?core<=core
            ?dom=#{?}&rng=uri  -> ?uri<=#{?}
            ?rng=noobj{0}&dom=str -> ?noobj{0}<=str
        """
        result = []
        i = 0
        while i < len(text):
            if text[i] == '?':
                # Found query start - find where it ends
                query_start = i + 1
                query_end = len(text)

                # Find end of query (first ( or [ after ?)
                j = query_start
                while j < len(text):
                    if text[j] in '([':
                        query_end = j
                        break
                    j += 1

                query = text[query_start:query_end]

                # Parse rng= and dom= from query
                # They can be in any order: rng=X&dom=Y or dom=X&rng=Y
                rng_val = None
                dom_val = None

                # Find rng= value
                rng_pos = query.find('rng=')
                if rng_pos >= 0:
                    rng_start = rng_pos + 4
                    # Value ends at & or end of query
                    rng_end = query.find('&', rng_start)
                    if rng_end < 0:
                        rng_end = len(query)
                    rng_val = query[rng_start:rng_end]

                # Find dom= value
                dom_pos = query.find('dom=')
                if dom_pos >= 0:
                    dom_start = dom_pos + 4
                    # Value ends at & or end of query
                    dom_end = query.find('&', dom_start)
                    if dom_end < 0:
                        dom_end = len(query)
                    dom_val = query[dom_start:dom_end]

                # Build shorthand
                if rng_val and dom_val:
                    result.append(f"?{rng_val}<={dom_val}")
                elif rng_val:
                    result.append(f"?{rng_val}")
                elif dom_val:
                    result.append(f"?<={dom_val}")
                else:
                    # No rng/dom found, keep original
                    result.append('?' + query)

                i = query_end
            else:
                result.append(text[i])
                i += 1

        return ''.join(result)




# ============================================================================
# HTML Generator
# ============================================================================

# Path to the external CSS file (relative to this script)
CSS_FILE_PATH = Path(__file__).parent / "instset_doc.css"

# Path to the website includes directory (header.html, footer.html)
INCLUDES_PATH = Path(__file__).parent.parent / "website" / "includes"

# highlight.js CDN URLs (fallback if local files not available)
HIGHLIGHT_JS_CDN = "https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0"


def load_css() -> str:
    """Load CSS from the external file."""
    if CSS_FILE_PATH.exists():
        return CSS_FILE_PATH.read_text(encoding='utf-8')
    else:
        logger.warning(f"CSS file not found: {CSS_FILE_PATH}, using minimal fallback")
        return """
        body { font-family: monospace; background: #1e1e2e; color: #cdd6f4; padding: 2rem; }
        .container { max-width: 1200px; margin: 0 auto; }
        a { color: #89b4fa; }
        pre code.hljs { background: #0d0d14; padding: 1rem; border-radius: 4px; }
        """


def load_website_header(relative_depth: str = "..", current_subdir: str = "instset") -> str:
    """
    Load the website header.html and adjust relative paths for subdirectory usage.

    Args:
        relative_depth: Path prefix to get back to website root (e.g., ".." for instset/)
        current_subdir: The subdirectory the generated file will be in (e.g., "instset")

    Returns:
        Header HTML with adjusted paths
    """
    header_path = INCLUDES_PATH / "header.html"
    if not header_path.exists():
        logger.warning(f"Header file not found: {header_path}")
        return ""

    content = header_path.read_text(encoding='utf-8')

    # Adjust relative paths for subdirectory
    # These patterns match paths that are relative to website root
    path_adjustments = [
        ('href="images/', f'href="{relative_depth}/images/'),
        ('src="images/', f'src="{relative_depth}/images/'),
        ('href="./images/', f'href="{relative_depth}/images/'),
        ('src="./images/', f'src="{relative_depth}/images/'),
        ('href="css/', f'href="{relative_depth}/css/'),
        ('href="./css/', f'href="{relative_depth}/css/'),
        ('href="lib/', f'href="{relative_depth}/lib/'),
        ('href="./lib/', f'href="{relative_depth}/lib/'),
        ('src="./highlight/', f'src="{relative_depth}/highlight/'),
        ('src="lib/', f'src="{relative_depth}/lib/'),
        ('src="./lib/', f'src="{relative_depth}/lib/'),
        ('src="js/', f'src="{relative_depth}/js/'),
        ('src="./js/', f'src="{relative_depth}/js/'),
        ('href="index.html"', f'href="{relative_depth}/index.html"'),
        ('href="tractatus.html"', f'href="{relative_depth}/tractatus.html"'),
        ("location.href='./articles/", f"location.href='{relative_depth}/articles/"),
        # onclick patterns for links that were converted to use location.href
        ("location.href='tractatus.html'", f"location.href='{relative_depth}/tractatus.html'"),
        ("location.href='index.html'", f"location.href='{relative_depth}/index.html'"),
    ]

    for old, new in path_adjustments:
        content = content.replace(old, new)

    # If we're inside a subdirectory (e.g., instset/), adjust links TO that subdirectory
    # to be relative within the same directory (remove the subdirectory prefix)
    if current_subdir:
        # href="instset/m.html" becomes href="m.html" when viewing from instset/
        content = content.replace(f'href="{current_subdir}/', 'href="')
        # onclick="location.href='instset/m.html'" becomes onclick="location.href='m.html'" when viewing from instset/
        content = content.replace(f"location.href='{current_subdir}/", "location.href='")

    return content


def load_website_footer(relative_depth: str = "..") -> str:
    """
    Load the website footer.html and adjust relative paths for subdirectory usage.

    Args:
        relative_depth: Path prefix to get back to website root (e.g., ".." for instset/)

    Returns:
        Footer HTML with adjusted paths
    """
    footer_path = INCLUDES_PATH / "footer.html"
    if not footer_path.exists():
        logger.warning(f"Footer file not found: {footer_path}")
        return ""

    content = footer_path.read_text(encoding='utf-8')

    # Footer typically has fewer path references, but adjust any that exist
    path_adjustments = [
        ('href="images/', f'href="{relative_depth}/images/'),
        ('src="images/', f'src="{relative_depth}/images/'),
    ]

    for old, new in path_adjustments:
        content = content.replace(old, new)

    return content


class HTMLDocGenerator:
    """Generates HTML documentation from InstSetInfo using highlight.js for syntax coloring."""

    def __init__(self, instset: InstSetInfo, embed_css: bool = True, css_path: str = "instset_doc.css",
                 use_website_template: bool = False, relative_depth: str = ".."):
        """
        Initialize the generator.

        Args:
            instset: The instruction set info to generate docs for
            embed_css: If True, embed CSS inline. If False, link to external CSS file.
            css_path: Path to CSS file (used when embed_css=False)
            use_website_template: If True, use website header.html/footer.html for consistent styling
            relative_depth: Path prefix to website root (e.g., ".." for instset/ subdirectory)
        """
        self.instset = instset
        self.embed_css = embed_css
        self.css_path = css_path
        self.use_website_template = use_website_template
        self.relative_depth = relative_depth

    def generate(self) -> str:
        """Generate complete HTML documentation."""
        if self.use_website_template:
            return self._generate_with_website_template()
        else:
            return self._generate_standalone()

    def _generate_with_website_template(self) -> str:
        """Generate HTML using the website header/footer template."""
        header = load_website_header(self.relative_depth)
        footer = load_website_footer(self.relative_depth)

        if not header or not footer:
            logger.warning("Website template not available, falling back to standalone")
            return self._generate_standalone()

        # Inject instset-specific CSS into the header (before </head>)
        instset_css = f'<link rel="stylesheet" href="{self.css_path}">'
        header = header.replace('</head>', f'    {instset_css}\n</head>')

        # Update the page title in the header
        title = f"{html.escape(self.instset.name)} - Metatron Instruction Set"
        header = re.sub(r'<title>.*?</title>', f'<title>{title}</title>', header)

        # Generate the main content (goes inside <main> tag)
        content = f"""
        <div class="instset-doc">
            {self._generate_instset_header()}
            {self._generate_nav()}
            {self._generate_stats()}
            {self._generate_hierarchy()}
            {self._generate_toc()}
            {self._generate_types_section()}
            {self._generate_instructions_section()}
            {self._generate_spaces_section()}
            {self._generate_instset_footer()}
        </div>"""

        return header + content + footer

    def _generate_standalone(self) -> str:
        """Generate standalone HTML (original behavior)."""
        # Use website's existing CSS and highlight.js (relative paths from instset/ subdirectory)
        css_links = f'''
    <link rel="stylesheet" href="{self.relative_depth}/css/metatron.css">
    <link rel="stylesheet" href="{self.css_path}">''' if not self.embed_css else f"<style>{load_css()}</style>"

        highlight_js = f'''
    <script src="{self.relative_depth}/highlight/highlight.min.js"></script>
    <script src="{self.relative_depth}/highlight/languages/mtron.min.js"></script>
    <script>hljs.highlightAll();</script>'''

        return f"""<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>{html.escape(self.instset.name)} - Metatron Instruction Set Documentation</title>
    {css_links}
</head>
<body>
    <div class="container">
        {self._generate_instset_header()}
        {self._generate_nav()}
        {self._generate_stats()}
        {self._generate_hierarchy()}
        {self._generate_toc()}
        {self._generate_types_section()}
        {self._generate_instructions_section()}
        {self._generate_spaces_section()}
        {self._generate_instset_footer()}
    </div>
    {highlight_js}
</body>
</html>"""

    def _generate_instset_header(self) -> str:
        parent_path = '/'.join(self.instset.vid.split('/')[:-1]) or '/'
        return f"""
        <div class="container-xxl py-4">
            <div class="text-center mb-4">
                <h1 class="text-primary glow-text">
                    <span class="text-light">{html.escape(parent_path)}/</span>{html.escape(self.instset.name)}
                </h1>
                <p class="subtitle text-light">Instruction Set Reference</p>
            </div>
        </div>"""

    def _generate_nav(self) -> str:
        return """
        <div class="container-xxl mb-4">
            <div class="d-flex justify-content-center gap-3 flex-wrap">
                <a href="#types" class="btn btn-outline-primary">Types</a>
                <a href="#instructions" class="btn btn-outline-primary">Instructions</a>
                <a href="#spaces" class="btn btn-outline-primary">Spaces</a>
            </div>
        </div>"""

    def _generate_stats(self) -> str:
        return f"""
        <div class="container-xxl mb-4">
            <div class="row g-3 justify-content-center">
                <div class="col-auto">
                    <div class="card text-center" style="min-width: 120px;">
                        <div class="card-body py-2">
                            <h3 class="text-primary mb-0">{len(self.instset.types)}</h3>
                            <small class="text-light">Types</small>
                        </div>
                    </div>
                </div>
                <div class="col-auto">
                    <div class="card text-center" style="min-width: 120px;">
                        <div class="card-body py-2">
                            <h3 class="text-primary mb-0">{len(self.instset.insts)}</h3>
                            <small class="text-light">Instructions</small>
                        </div>
                    </div>
                </div>
                <div class="col-auto">
                    <div class="card text-center" style="min-width: 120px;">
                        <div class="card-body py-2">
                            <h3 class="text-primary mb-0">{len(self.instset.spaces)}</h3>
                            <small class="text-light">Spaces</small>
                        </div>
                    </div>
                </div>
            </div>
        </div>"""

    def _generate_hierarchy(self) -> str:
        items = []
        if self.instset.parent:
            items.append(f'''
                <a href="{self._make_filename(self.instset.parent)}" class="btn btn-sm btn-outline-secondary">
                    <i class="bi bi-arrow-up"></i> {html.escape(self.instset.parent)}
                </a>''')
        for child in self.instset.children:
            items.append(f'''
                <a href="{self._make_filename(child)}" class="btn btn-sm btn-outline-secondary">
                    <i class="bi bi-arrow-down"></i> {html.escape(child)}
                </a>''')

        if not items:
            return ""

        return f"""
        <div class="container-xxl mb-4">
            <div class="card">
                <div class="card-header">
                    <h5 class="mb-0 text-primary">Hierarchy</h5>
                </div>
                <div class="card-body d-flex flex-wrap gap-2">
                    {''.join(items)}
                </div>
            </div>
        </div>"""

    def _generate_toc(self) -> str:
        type_items = []
        for t in sorted(self.instset.types, key=lambda x: x.name):
            type_items.append(f'''
                <a href="#type-{html.escape(t.name)}" class="list-group-item list-group-item-action d-flex justify-content-between align-items-center bg-dark text-light border-secondary">
                    <span class="code">{html.escape(t.name)}</span>
                    <span class="badge bg-info">T</span>
                </a>''')

        inst_items = []
        # Get unique instruction names
        inst_names = sorted(set(i.name for i in self.instset.insts))
        for name in inst_names:
            inst_items.append(f'''
                <a href="#inst-{html.escape(name)}" class="list-group-item list-group-item-action d-flex justify-content-between align-items-center bg-dark text-light border-secondary">
                    <span class="code">{html.escape(name)}</span>
                    <span class="badge bg-success">I</span>
                </a>''')

        if not type_items and not inst_items:
            return ""

        type_list = f'''
            <div class="col-md-6">
                <h6 class="text-primary mb-2">Types</h6>
                <div class="list-group list-group-flush">
                    {''.join(type_items)}
                </div>
            </div>''' if type_items else ""

        inst_list = f'''
            <div class="col-md-6">
                <h6 class="text-primary mb-2">Instructions</h6>
                <div class="list-group list-group-flush">
                    {''.join(inst_items)}
                </div>
            </div>''' if inst_items else ""

        return f"""
        <div class="container-xxl mb-4">
            <div class="card">
                <div class="card-header">
                    <h5 class="mb-0 text-primary">Index</h5>
                </div>
                <div class="card-body">
                    <div class="row">
                        {type_list}
                        {inst_list}
                    </div>
                </div>
            </div>
        </div>"""

    def _generate_types_section(self) -> str:
        if not self.instset.types:
            return ""

        items = []
        for t in sorted(self.instset.types, key=lambda x: x.name):
            # Type definition with highlight.js mtron syntax
            definition = ""
            if t.definition:
                definition = f'''
                    <div class="card-body p-2">
                        <pre class="mb-0"><code class="language-mtron">{html.escape(t.definition)}</code></pre>
                    </div>'''

            items.append(f"""
                <div class="card mb-3" id="type-{html.escape(t.name)}">
                    <div class="card-header d-flex justify-content-between align-items-center py-2">
                        <span class="code text-primary fw-bold">{html.escape(t.name)}::T</span>
                        <small class="text-muted code">{html.escape(t.vid)}</small>
                    </div>
                    {definition}
                </div>""")

        return f"""
        <div class="container-xxl mb-4" id="types">
            <h3 class="text-primary mb-3">
                Types <span class="badge bg-info">{len(self.instset.types)}</span>
            </h3>
            {''.join(items)}
        </div>"""

    def _generate_instructions_section(self) -> str:
        if not self.instset.insts:
            return ""

        items = []
        # Group instructions by name to handle multiple signatures
        from collections import defaultdict
        inst_groups = defaultdict(list)
        for inst in self.instset.insts:
            inst_groups[inst.name].append(inst)

        for name in sorted(inst_groups.keys()):
            insts = inst_groups[name]

            # Generate signature blocks for each variant
            signatures = []
            for inst in insts:
                signatures.append(f'<pre class="mb-1"><code class="language-mtron">{html.escape(inst.signature)}</code></pre>')

            all_sigs = '\n'.join(signatures)

            # Use first inst for the header vid
            first_inst = insts[0]

            items.append(f"""
                <div class="card mb-3" id="inst-{html.escape(name)}">
                    <div class="card-header d-flex justify-content-between align-items-center py-2">
                        <span class="code text-primary fw-bold">{html.escape(name)}</span>
                        <small class="text-muted code">{html.escape(first_inst.vid)}</small>
                    </div>
                    <div class="card-body p-2">
                        {all_sigs}
                    </div>
                </div>""")

        return f"""
        <div class="container-xxl mb-4" id="instructions">
            <h3 class="text-primary mb-3">
                Instructions <span class="badge bg-success">{len(self.instset.insts)}</span>
            </h3>
            {''.join(items)}
        </div>"""

    def _generate_spaces_section(self) -> str:
        if not self.instset.spaces:
            return ""

        items = []
        for space in sorted(self.instset.spaces, key=lambda x: x.name):
            items.append(f"""
                <a href="{self._make_filename(space.vid)}" class="list-group-item list-group-item-action d-flex justify-content-between align-items-center bg-dark text-light border-secondary" id="space-{html.escape(space.name)}">
                    <span class="code">{html.escape(space.name)}</span>
                    <small class="text-muted code">{html.escape(space.vid)}</small>
                </a>""")

        return f"""
        <div class="container-xxl mb-4" id="spaces">
            <h3 class="text-primary mb-3">
                Spaces <span class="badge bg-warning text-dark">{len(self.instset.spaces)}</span>
            </h3>
            <div class="card">
                <div class="list-group list-group-flush">
                    {''.join(items)}
                </div>
            </div>
        </div>"""

    def _generate_instset_footer(self) -> str:
        timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        return f"""
        <div class="container-xxl py-3 text-center">
            <hr class="border-secondary">
            <small class="text-muted">
                Generated by Metatron InstSet Doc Generator on {timestamp}<br>
                © PhaseShift Studio, LLC
            </small>
        </div>"""

    def _make_filename(self, vid: str) -> str:
        """Convert a vid to a safe filename."""
        return vid.replace('/', '_').strip('_') + '.html'


# ============================================================================
# Index Page Generator
# ============================================================================

def generate_index_page(instsets: List[InstSetInfo], embed_css: bool = True, css_path: str = "instset_doc.css",
                        use_website_template: bool = False, relative_depth: str = "..") -> str:
    """Generate an index page linking to all instruction set docs."""
    items = []
    for info in sorted(instsets, key=lambda x: x.vid):
        filename = info.vid.replace('/', '_').strip('_') + '.html'
        items.append(f"""
                <div class="col-md-6 col-lg-4">
                    <a href="{filename}" class="text-decoration-none">
                        <div class="card h-100">
                            <div class="card-body">
                                <h5 class="card-title code text-primary">{html.escape(info.vid)}</h5>
                                <div class="d-flex gap-2 flex-wrap">
                                    <span class="badge bg-info">{len(info.types)} types</span>
                                    <span class="badge bg-success">{len(info.insts)} instructions</span>
                                    <span class="badge bg-warning text-dark">{len(info.spaces)} spaces</span>
                                </div>
                            </div>
                        </div>
                    </a>
                </div>""")

    content = f"""
        <div class="container-xxl py-4">
            <div class="text-center mb-5">
                <h1 class="text-primary glow-text">Metatron</h1>
                <p class="subtitle text-light">Instruction Set Documentation</p>
            </div>

            <div class="row g-3">
                {''.join(items)}
            </div>

            <div class="py-3 text-center mt-5">
                <hr class="border-secondary">
                <small class="text-muted">
                    Generated by Metatron InstSet Doc Generator on {datetime.now().strftime("%Y-%m-%d %H:%M:%S")}<br>
                    © PhaseShift Studio, LLC
                </small>
            </div>
        </div>"""

    if use_website_template:
        header = load_website_header(relative_depth)
        footer = load_website_footer(relative_depth)

        if header and footer:
            # Inject instset-specific CSS into the header (before </head>)
            instset_css = f'<link rel="stylesheet" href="{css_path}">'
            header = header.replace('</head>', f'    {instset_css}\n</head>')

            # Update the page title
            header = re.sub(r'<title>.*?</title>', '<title>Metatron Instruction Sets</title>', header)

            return header + content + footer
        else:
            logger.warning("Website template not available, falling back to standalone")

    # Standalone mode
    css_links = f'''
    <link rel="stylesheet" href="{relative_depth}/css/metatron.css">
    <link rel="stylesheet" href="{css_path}">''' if not embed_css else f"<style>{load_css()}</style>"

    return f"""<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Metatron Instruction Set Documentation</title>
    {css_links}
</head>
<body>
    <div class="container">
        {content}
    </div>
</body>
</html>"""


# ============================================================================
# Main
# ============================================================================

async def main_async(args):
    """Main async function."""
    client = MetatronClient(host=args.host, timeout=args.timeout)
    fetcher = InstSetDocFetcher(client)

    output_dir = Path(args.output)
    output_dir.mkdir(parents=True, exist_ok=True)

    embed_css = not args.link_css
    css_filename = "instset_doc.css"
    use_website_template = args.website_template
    relative_depth = args.relative_depth

    # Copy instset-specific CSS file to output directory if using linked CSS
    # (highlight.js and metatron.css are referenced from ../highlight/ and ../css/)
    if not embed_css or use_website_template:
        if CSS_FILE_PATH.exists():
            dest_css = output_dir / css_filename
            shutil.copy2(CSS_FILE_PATH, dest_css)
            logger.info(f"Copied CSS to: {dest_css}")
        else:
            logger.warning(f"CSS file not found: {CSS_FILE_PATH}")

    try:
        await client.connect()

        instsets = []
        for vid in args.instsets:
            try:
                info = await fetcher.fetch_instset(vid)
                instsets.append(info)

                # Generate HTML
                generator = HTMLDocGenerator(
                    info,
                    embed_css=embed_css,
                    css_path=css_filename,
                    use_website_template=use_website_template,
                    relative_depth=relative_depth
                )
                html_content = generator.generate()

                # Write to file
                filename = vid.replace('/', '_').strip('_') + '.html'
                filepath = output_dir / filename
                filepath.write_text(html_content, encoding='utf-8')
                logger.info(f"Generated: {filepath}")

            except Exception as e:
                logger.error(f"Failed to process {vid}: {e}")
                if args.verbose:
                    import traceback
                    traceback.print_exc()

        # Generate index page
        if len(instsets) > 1:
            index_html = generate_index_page(
                instsets,
                embed_css=embed_css,
                css_path=css_filename,
                use_website_template=use_website_template,
                relative_depth=relative_depth
            )
            index_path = output_dir / 'index.html'
            index_path.write_text(index_html, encoding='utf-8')
            logger.info(f"Generated index: {index_path}")

    finally:
        await client.close()


def main():
    """Entry point."""
    parser = argparse.ArgumentParser(
        description="Generate HTML documentation for Metatron instruction sets.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
    python instset_doc_generator.py /m/mach
    python instset_doc_generator.py /m /m/mach /m/tble -o docs/html/
    python instset_doc_generator.py /m/llm --host ws://localhost:8999
    python instset_doc_generator.py /m/mach --link-css  # Use external CSS file
    python instset_doc_generator.py /m/mach -o docs/website/instset --website-template  # With website navbar/footer
        """
    )
    parser.add_argument(
        'instsets',
        nargs='+',
        help='Instruction set VIDs to document (e.g., /m, /m/mach, /m/llm)'
    )
    parser.add_argument(
        '-o', '--output',
        default='./instset_docs',
        help='Output directory for generated HTML files (default: ./instset_docs)'
    )
    parser.add_argument(
        '--host',
        default='ws://127.0.0.1:8999',
        help='Metatron WebSocket host (default: ws://127.0.0.1:8999)'
    )
    parser.add_argument(
        '--timeout',
        type=int,
        default=30,
        help='WebSocket timeout in seconds (default: 30)'
    )
    parser.add_argument(
        '--link-css',
        action='store_true',
        help='Link to external CSS file instead of embedding (copies instset_doc.css to output dir)'
    )
    parser.add_argument(
        '--website-template',
        action='store_true',
        help='Use website header.html/footer.html for consistent site styling (navbar, footer, etc.)'
    )
    parser.add_argument(
        '--relative-depth',
        default='..',
        help='Relative path to website root from output directory (default: ".." for instset/ subdirectory)'
    )
    parser.add_argument(
        '-v', '--verbose',
        action='store_true',
        help='Enable verbose output'
    )

    args = parser.parse_args()

    if args.verbose:
        logging.getLogger().setLevel(logging.DEBUG)

    asyncio.run(main_async(args))


if __name__ == '__main__':
    main()
