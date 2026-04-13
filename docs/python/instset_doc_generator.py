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

Generates HTML documentation for metatron instruction sets by connecting to a running
metatron instance via WebSocket. Uses doc_json() for documentation retrieval and
highlight.js with mtron.min.js for syntax highlighting.

Usage:
    python instset_doc_generator.py /m/mach -o docs/website/instset --website-template
    python instset_doc_generator.py /m /m/mach /m/tble -o docs/html/
"""

from __future__ import annotations

import argparse
import asyncio
import html
import logging
import re
import shutil
import websockets
import json
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from typing import Optional, List
from collections import defaultdict

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)


# ============================================================================
# metatron WebSocket Client
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

    async def rewrite(self, uri: str) -> tuple:
        """Call rewrite(uri) to get short and long forms. Returns (short, long) tuple."""
        try:
            result = await self.eval(f"rewrite({uri})")
            short_match = re.search(r'short\s*=>\s*([^,\]]+)', result)
            long_match = re.search(r'long\s*=>\s*([^,\]]+)', result)
            short_val = short_match.group(1).strip() if short_match else uri
            long_val = long_match.group(1).strip() if long_match else uri
            return (short_val, long_val)
        except Exception as e:
            logger.debug(f"rewrite({uri}) failed: {e}")
            return (uri, uri)

    async def eval_doc(self, expression: str) -> List[str]:
        """Evaluate via doc() for pretty-printed output. Returns list of entries split by '%%%'."""
        query = f"'{expression}'./m/web/inst/doc()"
        result = await self.eval(query)

        if not result or result == "noobj" or result.startswith("</m/fail>"):
            return []

        # Strip </m/str>:: wrapper and quotes
        if result.startswith("</m/str>::"):
            result = result[len("</m/str>::"):]
        if result.startswith("'") and result.endswith("'"):
            result = result[1:-1]

        if not result:
            return []

        return [e.strip() for e in result.split("%%%") if e.strip()]

    async def fetch_obj_doc(self, vid: str) -> List[DocInfo]:
        """
        Fetch documentation for an object using doc_json() instruction.

        Returns JSON format: {"obj":"...", "desc":"...", "dom":"...", "rng":"...", "args":{...}, "example":[...]}
        or a list of such objects if there are multiple documentation instances.
        Response wrapper: </m/str>::'json' or </m/str>::'''json''' (triple quotes if json has quotes)

        Returns a list of DocInfo with parsed fields.
        """
        try:
            # Build doc query: "*vid?docq"./m/web/inst/doc_json()
            vid_escaped = vid.replace("'", "\\'")
            doc_query = f'"*{vid_escaped}&docq"./m/web/inst/doc_json()' if '?' in vid else f'"*{vid_escaped}?docq"./m/web/inst/doc_json()'

            logger.debug(f"Fetching doc: {doc_query}")
            result = await self.eval(doc_query)

            if not result or result == "noobj" or result.startswith("</m/fail>"):
                return []

            # Extract JSON from response: </m/str>::'...' or </m/str>::"""..."""
            json_match = re.search(r'</m/str>::"""(.+)"""', result, re.DOTALL) or re.search(r"</m/str>::'(.+)'", result,
                                                                                            re.DOTALL)
            if not json_match:
                return []

            json_str = json_match.group(1)
            if json_str == 'null':
                return []

            # Parse JSON
            doc_data = json.loads(json_str)

            # Ensure we got a dict or a list
            if isinstance(doc_data, dict):
                doc_entries = [doc_data]
            elif isinstance(doc_data, list):
                doc_entries = doc_data
            else:
                logger.debug(f"doc_json() returned unexpected type for {vid}: {type(doc_data)}")
                return []

            docs = []
            for entry in doc_entries:
                if not isinstance(entry, dict):
                    continue

                # Build DocInfo
                doc_info = DocInfo(
                    raw=result,
                    desc=entry.get('desc', ''),
                    dom=entry.get('dom', ''),
                    rng=entry.get('rng', ''),
                    obj=entry.get('obj', '')
                )

                # Handle args dict
                args_data = entry.get('args', {})
                if isinstance(args_data, dict) and args_data:
                    doc_info.args = json.dumps(args_data)

                # Handle examples list
                examples_data = entry.get('example', [])
                if isinstance(examples_data, list):
                    doc_info.example = examples_data
                elif isinstance(examples_data, str):
                    doc_info.example = [examples_data]

                if doc_info.desc:
                    docs.append(doc_info)

            return docs

        except json.JSONDecodeError as e:
            logger.warning(f"JSON parse error for {vid}: {e}")
            return []

        except Exception as e:
            logger.error(f"Error fetching doc for {vid}: {e}")
            return []


# ============================================================================
# Data Models
# ============================================================================

@dataclass
class DocInfo:
    """Documentation information fetched via doc_json()."""
    obj: str = ""
    dom: str = ""
    rng: str = ""
    args: str = ""  # JSON string of args dict
    desc: str = ""
    example: List[str] = field(default_factory=list)
    raw: str = ""


@dataclass
class TypeInfo:
    """Information about a type defined in an instruction set."""
    vid: str
    name: str
    definition: str
    raw: str = ""
    super_type: str = ""
    super_type_short: str = ""
    super_type_instset: str = ""
    docs: List[DocInfo] = field(default_factory=list)


@dataclass
class InstInfo:
    """Information about an instruction."""
    vid: str
    name: str
    signature: str
    raw: str = ""
    domain: str = ""
    range: str = ""
    domain_full: str = ""
    range_full: str = ""
    docs: List[DocInfo] = field(default_factory=list)


@dataclass
class SpaceInfo:
    """Information about a sub-space/sub-instruction set."""
    vid: str
    name: str
    raw: str = ""
    type_spec: str = ""
    docs: List[DocInfo] = field(default_factory=list)


@dataclass
class ConstInfo:
    """Information about a constant."""
    vid: str
    name: str
    definition: str = ""
    raw: str = ""
    docs: List[DocInfo] = field(default_factory=list)


@dataclass
class RewriteInfo:
    """Information about a rewrite rule."""
    vid: str
    name: str
    signature: str = ""
    raw: str = ""
    domain: str = ""
    range: str = ""
    domain_full: str = ""
    range_full: str = ""
    docs: List[DocInfo] = field(default_factory=list)


@dataclass
class InstSetInfo:
    """Complete information about an instruction set."""
    vid: str  # e.g., /m/mach
    name: str  # e.g., mach
    desc: str = ""  # Instruction set description
    parent: Optional[str] = None  # Super instruction set
    children: List[str] = field(default_factory=list)  # Sub instruction sets
    types: List[TypeInfo] = field(default_factory=list)
    insts: List[InstInfo] = field(default_factory=list)
    rewrites: List[RewriteInfo] = field(default_factory=list)
    spaces: List[SpaceInfo] = field(default_factory=list)
    consts: List[ConstInfo] = field(default_factory=list)
    json: Optional[dict] = None
    raw_metadata: str = ""
    full: str = ""


# ============================================================================
# Documentation Fetcher
# ============================================================================

async def getJSON(client: MetatronClient, query: str, endpoint: str = "/m/web/inst/doc_json()") -> Optional[str]:
    result = await client.eval(query + '.' + endpoint)
    if not result or result == "noobj" or result.startswith("</m/fail>"):
        logger.debug(f"Could not fetch JSON for query: {query}")
        return None
    match = re.search(r"</m/str>::'(.+)'", result, re.DOTALL)
    if match:
        return json.loads(match.group(1))
    return None


async def getNative(client: MetatronClient, query: str, endpoint: str = "/m/web/inst/doc()") -> Optional[str]:
    result = await client.eval(query + '.' + endpoint)
    if not result or result == "noobj" or result.startswith("</m/fail>"):
        logger.debug(f"Could not fetch result for query: {query}")
        return None
    match = re.search(r"</m/str>::'(.+)'", result, re.DOTALL)
    if match:
        return match.group(1)
    return None


class InstSetDocFetcher:
    """Fetches instruction set documentation from a running metatron instance."""

    def __init__(self, client: MetatronClient):
        self.client = client

    async def fetch_instset(self, vid: str) -> InstSetInfo:
        """Fetch complete documentation for an instruction set."""
        logger.info(f"Fetching instruction set: {vid}")
        name = vid.split('/')[-1] if '/' in vid else vid
        info = InstSetInfo(vid=vid, name=name)
        info.json = await getJSON(self.client, f"'*{vid}'")
        logger.debug(f"complete: {info.full}")
        info.desc = info.json['desc'] if 'desc' in info.json else ""
        subspaces = info.json['space'] if 'space' in info.json else dict()
        if "sub" in subspaces:
            info.children = subspaces['sub']  # TODO: ghetto (using string matching to remove sub instsets from instset)
        else:
            info.children = []
        info.full = await getNative(self.client, f"'*{vid}'")
        try:
            info.raw_metadata = await self.client.eval(f"*{vid}/")
        except Exception as e:
            logger.warning(f"Could not fetch metadata for {vid}: {e}")
        logger.debug(f"""
            vid: {info.vid}
            name: {info.name}
            children: {info.children}
            desc: {info.desc}
            full: {info.full}
            raw_metadata: {info.raw_metadata}
        """)
        # Fetch types via doc() for pretty formatting
        info.types = await self._fetch_types(vid)
        # TODO: ghetto (using string matching to remove sub instsets from instset)
        info.types = list(filter(lambda item: item.vid not in str(info.children), info.types))

        info.insts = await self._fetch_instructions(vid)

        # Fetch spaces (sub-instruction sets)
        info.spaces = await self._fetch_spaces(vid)

        # Fetch rewrites
        info.rewrites = await self._fetch_rewrites(vid)

        # Fetch consts
        info.consts = await self._fetch_consts(vid)

        # Check for parent/child relationships in metadata
        self._parse_hierarchy(info)

        logger.info(f"Fetched {vid}: {len(info.types)} types, {len(info.insts)} instructions, "
                    f"{len(info.rewrites)} rewrites, {len(info.spaces)} spaces, {len(info.consts)} consts")
        return info

    async def _fetch_types(self, vid: str) -> List[TypeInfo]:
        """Fetch all types defined by this instruction set using doc() for pretty output."""
        types = []
        try:
            # Use doc() for pretty-printed output
            entries = await self.client.eval_doc(f"*{vid}/+/")
            for entry in entries:
                type_info = self._parse_type_entry(entry)
                logger.debug(f"processing {type_info.vid}")
                # print(type_info)
                if type_info and ("space" not in type_info.vid) and ("::T" in type_info.raw):
                    # Query raw type info to get super type
                    await self._fetch_type_super(type_info)
                    # Fetch documentation
                    type_info.docs = await self.client.fetch_obj_doc(type_info.vid)
                    types.append(type_info)
                    logger.debug(f"  Found type: {type_info.name} (refines {type_info.super_type})")
        except Exception as e:
            logger.warning(f"Could not fetch types for {vid}: {e}")
        return types

    async def _fetch_type_super(self, type_info: TypeInfo):
        """Fetch super type information by querying the raw type."""
        try:
            raw_result = await self.client.eval(f"*{type_info.vid}")
            if raw_result and "::" in raw_result:
                super_short = raw_result.split("::")[0].strip()
                if super_short and super_short != type_info.name:
                    type_info.super_type_short = super_short
                    super_raw = await self.client.eval(f"*{super_short}")
                    if super_raw and "@" in super_raw:
                        vid_part = super_raw.split("@")[-1].strip()
                        if vid_part.startswith("/"):
                            type_info.super_type = vid_part
                            type_info.super_type_instset = self._extract_instset(type_info.super_type)
        except Exception as e:
            logger.debug(f"Could not fetch super type for {type_info.name}: {e}")

    async def _fetch_instructions(self, vid: str) -> List[InstInfo]:
        """Fetch all instructions defined by this instruction set using doc() for pretty output."""
        insts = []
        try:
            # Use doc() for pretty-printed output
            entries = await self.client.eval_doc(f"*{vid}/inst/+/")
            for entry in entries:
                inst_info = self._parse_inst_entry(entry)
                if inst_info:
                    # Get short forms for domain and range using rewrite()
                    await self._fetch_inst_short_forms(inst_info)
                    # Fetch documentation
                    inst_info.docs = await self.client.fetch_obj_doc(inst_info.vid)
                    insts.append(inst_info)
                    logger.debug(f"  Found instruction: {inst_info.name} ({inst_info.domain}=>{inst_info.range})")
        except Exception as e:
            logger.warning(f"Could not fetch instructions for {vid}: {e}")
        return insts

    async def _fetch_inst_short_forms(self, inst_info: InstInfo):
        """Convert domain/range to short forms using rewrite()."""
        inst_info.domain_full = inst_info.domain
        inst_info.range_full = inst_info.range

        if inst_info.domain:
            _, inst_info.domain = await self.client.rewrite(inst_info.domain)
        if inst_info.range:
            _, inst_info.range = await self.client.rewrite(inst_info.range)

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
                    space_info = SpaceInfo(vid=space_vid, name=name, raw=entry)
                    # Fetch type specification for the space
                    await self._fetch_space_type(space_info)
                    # Fetch documentation
                    space_info.docs = await self.client.fetch_obj_doc(space_vid)
                    spaces.append(space_info)
                    logger.debug(f"  Found space: {space_vid}")
        except Exception as e:
            logger.warning(f"Could not fetch spaces for {vid}: {e}")
        return spaces

    async def _fetch_space_type(self, space_info: SpaceInfo):
        """Fetch type specification for a space."""
        try:
            raw_result = await self.client.eval(f"*{space_info.vid}")
            if raw_result:
                space_info.type_spec = raw_result
        except Exception as e:
            logger.debug(f"Could not fetch type spec for {space_info.vid}: {e}")

    async def _fetch_rewrites(self, vid: str) -> List[RewriteInfo]:
        """Fetch all rewrites defined by this instruction set."""
        rewrites = []
        try:
            # Rewrites are at /{base}/inst/rewrite/+/
            entries = await self.client.eval_doc(f"*{vid}/inst/rewrite/+/")
            for entry in entries:
                if "=>" in entry:
                    arrow_pos = self._find_top_level_arrow(entry)
                    if arrow_pos >= 0:
                        rewrite_vid = entry[:arrow_pos].strip()
                        signature = entry[arrow_pos + 2:].strip()
                        base_path = rewrite_vid.split('?')[0]
                        name = base_path.split('/')[-1]

                        # Extract domain and range from vid query string
                        domain, range_type = self._extract_dom_rng(rewrite_vid)

                        # Convert dom=&rng= to rng<=dom in both vid and signature
                        rewrite_vid = self._convert_signature_shorthand(rewrite_vid)
                        signature = self._convert_signature_shorthand(signature)

                        rewrite_info = RewriteInfo(vid=rewrite_vid, name=name, signature=signature, raw=entry,
                                                   domain=domain, range=range_type)
                        # Get short forms for domain and range using rewrite()
                        await self._fetch_rewrite_short_forms(rewrite_info)
                        # Fetch documentation using ORIGINAL vid (before conversion to shorthand)
                        original_vid = entry[:arrow_pos].strip()
                        rewrite_info.docs = await self.client.fetch_obj_doc(original_vid)
                        rewrites.append(rewrite_info)
                        logger.debug(f"  Found rewrite: {name} ({rewrite_info.domain}=>{rewrite_info.range})")
        except Exception as e:
            logger.debug(f"Could not fetch rewrites for {vid}: {e}")
        return rewrites

    async def _fetch_rewrite_short_forms(self, rewrite_info: RewriteInfo):
        """Convert domain/range to short forms using rewrite()."""
        rewrite_info.domain_full = rewrite_info.domain
        rewrite_info.range_full = rewrite_info.range

        if rewrite_info.domain:
            _, rewrite_info.domain = await self.client.rewrite(rewrite_info.domain)
        if rewrite_info.range:
            _, rewrite_info.range = await self.client.rewrite(rewrite_info.range)

    async def _fetch_consts(self, vid: str) -> List[ConstInfo]:
        """Fetch all constants defined by this instruction set."""
        consts = []
        try:
            # Consts are at /{base}/+/ - we need to filter for non-type entries
            # Types are also at /{base}/+/ so we fetch all and filter
            entries = await self.client.eval_doc(f"*{vid}/+/")
            for entry in entries:
                if "=>" in entry:
                    arrow_pos = self._find_top_level_arrow(entry)
                    if arrow_pos >= 0:
                        const_vid = entry[:arrow_pos].strip()
                        definition = entry[arrow_pos + 2:].strip()
                        name = const_vid.split('/')[-1]
                        # Skip if this looks like a type definition (contains ::T)
                        if "::T" in definition or "::T@" in entry:
                            continue
                        const_info = ConstInfo(vid=const_vid, name=name, definition=definition, raw=entry)
                        # Fetch documentation
                        const_info.docs = await self.client.fetch_obj_doc(const_vid)
                        consts.append(const_info)
                        logger.debug(f"  Found const: {name}")
        except Exception as e:
            logger.debug(f"Could not fetch consts for {vid}: {e}")
        return consts

    def _extract_instset(self, type_uri: str) -> str:
        """Extract instset from type URI: /m/tble/lrow -> /m/tble"""
        if not type_uri:
            return ""
        parts = type_uri.strip('/').split('/')
        if len(parts) <= 1:
            return "/" + parts[0] if parts else ""
        return "/" + "/".join(parts[:-1])

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
        """Parse type entry: /m/mach/machine=>rec::T[...]@/m/mach/machine"""
        if "=>" not in entry:
            return None

        arrow_pos = self._find_top_level_arrow(entry)
        if arrow_pos < 0:
            return None

        vid = entry[:arrow_pos].strip()
        definition = self._convert_signature_shorthand(entry[arrow_pos + 2:].strip())
        name = vid.split('/')[-1].split('?')[0]

        return TypeInfo(vid=vid, name=name, definition=definition, raw=entry)

    def _parse_inst_entry(self, entry: str) -> Optional[InstInfo]:
        """Parse instruction entry: /m/mach/inst/run?rng=core&dom=core=>run..."""
        if "=>" not in entry:
            return None

        arrow_pos = self._find_top_level_arrow(entry)
        if arrow_pos < 0:
            return None

        vid = entry[:arrow_pos].strip()
        signature = entry[arrow_pos + 2:].strip()
        name = vid.split('?')[0].split('/')[-1]
        domain, range_type = self._extract_dom_rng(vid)

        vid = self._convert_signature_shorthand(vid)
        signature = self._convert_signature_shorthand(signature)

        return InstInfo(vid=vid, name=name, signature=signature, raw=entry,
                        domain=domain, range=range_type)

    def _extract_dom_rng(self, vid: str) -> tuple:
        """Extract domain and range from vid query string."""
        if '?' not in vid:
            return "", ""
        query = vid.split('?', 1)[1]
        rng_match = re.search(r'rng=([^&]+)', query)
        dom_match = re.search(r'dom=([^&]+)', query)
        return (dom_match.group(1) if dom_match else "",
                rng_match.group(1) if rng_match else "")

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
            elif depth == 0 and text[i:i + 2] == '=>':
                return i
            i += 1
        return -1

    def _convert_signature_shorthand(self, text: str) -> str:
        """Convert ?rng=X&dom=Y to ?X<=Y shorthand."""
        result = []
        i = 0
        while i < len(text):
            if text[i] == '?':
                query_start = i + 1
                query_end = len(text)

                # Find end of query (first ( or [ after ?)
                j = query_start
                while j < len(text) and text[j] not in '([':
                    j += 1
                query_end = j

                query = text[query_start:query_end]

                # Extract rng= and dom= values
                rng_val = None
                dom_val = None

                rng_pos = query.find('rng=')
                if rng_pos >= 0:
                    rng_start = rng_pos + 4
                    rng_end = query.find('&', rng_start)
                    rng_val = query[rng_start:rng_end] if rng_end >= 0 else query[rng_start:]

                dom_pos = query.find('dom=')
                if dom_pos >= 0:
                    dom_start = dom_pos + 4
                    dom_end = query.find('&', dom_start)
                    dom_val = query[dom_start:dom_end] if dom_end >= 0 else query[dom_start:]

                # Build shorthand
                if rng_val and dom_val:
                    result.append(f"?{rng_val}<={dom_val}")
                elif rng_val:
                    result.append(f"?{rng_val}")
                elif dom_val:
                    result.append(f"?<={dom_val}")
                else:
                    result.append('?' + query)

                i = query_end
            else:
                result.append(text[i])
                i += 1

        return ''.join(result)


# ============================================================================
# HTML Generator
# ============================================================================

# Path to the external CSS file (in website css directory)
CSS_FILE_PATH = Path(__file__).parent.parent / "website" / "css" / "instset_doc.css"

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

    def __init__(self, instset: InstSetInfo, embed_css: bool = True, css_path: str = "../css/instset_doc.css",
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

    def _extract_instset(self, type_uri: str) -> str:
        """Extract instset from type URI: /m/tble/lrow -> /m/tble"""
        if not type_uri:
            return ""
        parts = type_uri.strip('/').split('/')
        if len(parts) <= 1:
            return "/" + parts[0] if parts else ""
        return "/" + "/".join(parts[:-1])

    def generate(self, build_number: int = 0) -> str:
        """Generate complete HTML documentation."""
        if self.use_website_template:
            return self._generate_with_website_template(build_number=build_number)
        else:
            return self._generate_standalone()

    def _generate_with_website_template(self, build_number: int = 0) -> str:
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
        title = f"{html.escape(self.instset.name)} - metatron Instruction Set"
        header = re.sub(r'<title>.*?</title>', f'<title>{title}</title>', header)

        # Generate the main content (goes inside <main> tag)
        content = f"""
        <div class="instset-doc">
            {self._generate_instset_header()}
            {self._generate_nav()}
            {self._generate_hierarchy()}
            {self._generate_toc()}
            {self._generate_consts_section()}
            {self._generate_types_section()}
            {self._generate_instructions_section()}
            {self._generate_spaces_section()}
            {self._generate_rewrites_section()}
            {self._generate_instset_footer(build_number=build_number)}
        </div>"""

        return header + content + footer

    def _generate_standalone(self, build_number: int = 0) -> str:
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
    <title>{html.escape(self.instset.name)} - metatron instruction set reference</title>
    {css_links}
</head>
<body>
    <div class="container">
        {self._generate_instset_header()}
        {self._generate_nav()}
        {self._generate_hierarchy()}
        {self._generate_toc()}
        {self._generate_types_section()}
        {self._generate_instructions_section()}
        {self._generate_rewrites_section()}
        {self._generate_spaces_section()}
        {self._generate_consts_section()}
        {self._generate_instset_footer(build_number=build_number)}
    </div>
    {highlight_js}
</body>
</html>"""

    def _generate_instset_header(self) -> str:
        parent_path = '/'.join(self.instset.vid.split('/')[:-1]) or ''

        # Add description if available (skip if empty or "null")
        desc_html = ""
        if self.instset.desc and self.instset.desc != "null":
            desc_html = f'<p class="text-light mt-3 mb-0" style="line-height:2.5em; max-width: 1000px; margin-left: auto; margin-right: auto;">{self.instset.desc.replace("\\n", "")}</p>'

        return f"""
        <div class="container-xxl py-4">
            <div class="text-center mb-4">
                <h1 class="text-primary glow-text">
                    <span class="text-light">{html.escape(parent_path)}/</span>{html.escape(self.instset.name)}
                </h1>
                <p style="line-height:0.1rem;" class="subtitle text-light">instruction set reference</p>
            </div>
            <div class="text-light">
                {desc_html}
            </div>
            <div class="accordion" id="accordianInstSet">
                <div class="accordion-item">
                    <h2 class="accordion-header" id="headingOne">
                        <button class="accordion-button collapsed" type="button" data-bs-toggle="collapse" data-bs-target="#flush-collapseOne" aria-expanded="false" aria-controls="flush-collapseOne">
                            instset obj
                        </button>
                    </h2>
                    <div id="flush-collapseOne" class="accordion-collapse collapse" aria-labelledby="flush-headingOne" data-bs-parent="#accordianInstSet">
                        <div class="accordion-body">
                               <pre><code>{self.instset.full}</code></pre>
                        </div>
                    </div>
                </div>
            <div>
        </div>"""

    def _generate_nav(self) -> str:
        """Generate navigation bar with all 5 categories (disabled if empty)."""

        def nav_btn(href: str, label: str, count: int) -> str:
            if count > 0:
                return f'<a href="#{href}" class="btn btn-outline-primary">{label} <span class="badge bg-secondary">{count}</span></a>'
            else:
                return f'<button class="btn btn-outline-secondary" disabled>{label} <span class="badge bg-dark">0</span></button>'

        return f"""
        <div class="container-xxl mb-4">
            <div class="d-flex justify-content-center gap-2 flex-wrap">
                {nav_btn("consts", "Consts", len(self.instset.consts))}
                {nav_btn("types", "Types", len(self.instset.types))}
                {nav_btn("instructions", "Insts", len(self.instset.insts))}
                {nav_btn("spaces", "Spaces", len(self.instset.spaces))}
                {nav_btn("rewrites", "Rewrites", len(self.instset.rewrites))}
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
        const_items = []
        for c in sorted(self.instset.consts, key=lambda x: x.name):
            const_items.append(f'''
                <a href="#const-{html.escape(c.name)}" class="list-group-item list-group-item-action d-flex justify-content-between align-items-center bg-dark text-light border-secondary py-1 px-2 small">
                    <span class="code">{html.escape(c.name)}</span>
                    <span class="badge bg-secondary">C</span>
                </a>''')

        type_items = []
        for t in sorted(self.instset.types, key=lambda x: x.name):
            type_items.append(f'''
                <a href="#type-{html.escape(t.name)}" class="list-group-item list-group-item-action d-flex justify-content-between align-items-center bg-dark text-light border-secondary py-1 px-2 small">
                    <span class="code">{html.escape(t.name)}</span>
                    <span class="badge bg-primary">T</span>
                </a>''')

        inst_items = []
        # Get unique instruction names
        inst_names = sorted(set(i.name for i in self.instset.insts))
        for name in inst_names:
            inst_items.append(f'''
                <a href="#inst-{html.escape(name)}" class="list-group-item list-group-item-action d-flex justify-content-between align-items-center bg-dark text-light border-secondary py-1 px-2 small">
                    <span class="code">{html.escape(name)}</span>
                    <span class="badge bg-success">I</span>
                </a>''')

        space_items = []
        for s in sorted(self.instset.spaces, key=lambda x: x.name):
            space_items.append(f'''
                <a href="#space-{html.escape(s.name)}" class="list-group-item list-group-item-action d-flex justify-content-between align-items-center bg-dark text-light border-secondary py-1 px-2 small">
                    <span class="code">{html.escape(s.name)}</span>
                    <span class="badge bg-info">S</span>
                </a>''')

        rewrite_items = []
        for r in sorted(self.instset.rewrites, key=lambda x: x.name):
            rewrite_items.append(f'''
                <a href="#rewrite-{html.escape(r.name)}" class="list-group-item list-group-item-action d-flex justify-content-between align-items-center bg-dark text-light border-secondary py-1 px-2 small">
                    <span class="code">{html.escape(r.name)}</span>
                    <span class="badge bg-warning text-dark">R</span>
                </a>''')

        if not type_items and not inst_items and not rewrite_items and not space_items and not const_items:
            return ""

        const_list = f'''
            <div class="mb-3">
                <h6 class="text-primary mb-2">Constants</h6>
                <div class="index-columns">
                    {''.join(const_items)}
                </div>
            </div>''' if const_items else ""

        type_list = f'''
            <div class="mb-3">
                <h6 class="text-primary mb-2">Types</h6>
                <div class="index-columns">
                    {''.join(type_items)}
                </div>
            </div>''' if type_items else ""

        inst_list = f'''
            <div class="mb-3">
                <h6 class="text-primary mb-2">Instructions</h6>
                <div class="index-columns">
                    {''.join(inst_items)}
                </div>
            </div>''' if inst_items else ""

        space_list = f'''
            <div class="mb-3">
                <h6 class="text-primary mb-2">Spaces</h6>
                <div class="index-columns">
                    {''.join(space_items)}
                </div>
            </div>''' if space_items else ""

        rewrite_list = f'''
            <div class="mb-3">
                <h6 class="text-primary mb-2">Rewrites</h6>
                <div class="index-columns-rewrites">
                    {''.join(rewrite_items)}
                </div>
            </div>''' if rewrite_items else ""

        return f"""
        <div class="container-xxl mb-4">
            <div class="card">
                <div class="card-header">
                    <h5 class="mb-0 text-primary">Index</h5>
                </div>
                <div class="card-body">
                    {const_list}
                    {type_list}
                    {inst_list}
                    {space_list}
                    {rewrite_list}
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

            # Generate "refines" link if super type exists
            refines_html = ""
            if t.super_type_short:
                super_name = t.super_type.split('/')[-1] if t.super_type else t.super_type_short
                display_name = f"{t.super_type_short}::T"
                if t.super_type_instset:
                    instset_file = self._make_filename(t.super_type_instset)
                    refines_html = f'''
                        <span class="ms-2 text-muted instset-doc-small-code">
                            refines <a href="{instset_file}#type-{html.escape(super_name)}" class="instset-doc-small-code text-info code">{html.escape(display_name)}</a>
                        </span>'''
                else:
                    refines_html = f'<span class="ms-2 text-muted instset-doc-small-code">refines <span class="code instset-doc-small-code text-info">{html.escape(display_name)}</span></span>'
        
            # Generate full documentation
            doc_group_id = f"type-{html.escape(t.name)}"
            doc_html = self._format_multi_documentation(t.docs, doc_group_id)
            
            items.append(f"""
                <div class="card mb-3" id="{doc_group_id}">
                    <div class="card-header d-flex justify-content-between align-items-center py-2">
                        <span>
                            <span class="code text-primary fw-bold">{html.escape(t.name)}::T</span>
                            {refines_html}
                        </span>
                        <small class="text-muted code">{html.escape(t.vid)}</small>
                    </div>
                    {definition}
                    {doc_html}
                </div>""")

        return f"""
        <div class="container-xxl mb-4" id="types">
            <h3 class="text-primary mb-3">
                Types <span class="badge bg-primary">{len(self.instset.types)}</span>
            </h3>
            {''.join(items)}
        </div>"""

    def _generate_instructions_section(self) -> str:
        if not self.instset.insts:
            return ""

        items = []
        # Group instructions by name to handle multiple signatures
        inst_groups = defaultdict(list)
        for inst in self.instset.insts:
            inst_groups[inst.name].append(inst)

        for name in sorted(inst_groups.keys()):
            insts = inst_groups[name]

            # Generate signature blocks for each variant
            signatures = []

            # Map each variant to its unique documentation index
            variant_doc_map = {}
            unique_docs = []
            doc_raws = {}  # raw_doc -> index

            for inst in insts:
                for doc in inst.docs:
                    if doc.raw not in doc_raws:
                        doc_raws[doc.raw] = len(unique_docs)
                        unique_docs.append(doc)
                    variant_doc_map[inst.signature] = doc_raws[doc.raw]

            doc_group_id = f"inst-{html.escape(name)}"

            for idx, inst in enumerate(insts):
                doc_idx = variant_doc_map.get(inst.signature, 0)
                tab_id = f"{doc_group_id}-doc-{doc_idx}"

                signatures.append(
                    f'<pre class="mb-1 clickable-signature" style="font-size: 0.85rem; cursor: pointer;" '
                    f'onclick="document.getElementById(\'{tab_id}-tab\').click(); document.getElementById(\'{tab_id}\').scrollIntoView({{behavior: \'smooth\', block: \'center\'}});">'
                    f'<code class="language-mtron">{html.escape(inst.signature)}</code></pre>')

            all_sigs = '\n'.join(signatures)

            # Use first doc/inst for the initial header
            initial_sig = self._format_inst_type_signature(insts[0])
            initial_vid = html.escape(insts[0].vid)

            # Generate full documentation
            doc_html = self._format_multi_documentation(unique_docs, doc_group_id)

            items.append(f"""
                <div class="card mb-3" id="{doc_group_id}">
                    <div class="card-header d-flex justify-content-between align-items-center py-2 text-light border-bottom border-secondary">
                        <span>
                            <span class="code text-primary fw-bold">{html.escape(name)}</span>
                            {initial_sig}
                        </span>
                        <small class="text-muted code">{initial_vid}</small>
                    </div>
                    <div class="card-body p-2">
                        {all_sigs}
                    </div>
                    {doc_html}
                </div>""")

        return f"""
        <div class="container-xxl mb-4" id="instructions">
            <h3 class="text-primary mb-3">
                Instructions <span class="badge bg-success">{len(self.instset.insts)}</span>
            </h3>
            {''.join(items)}
        </div>"""

    def _format_multi_documentation(self, docs: List[DocInfo], group_id: str) -> str:
        """Format multiple documentation variations with a toggle UI."""
        if not docs:
            return ""

        if len(docs) == 1:
            return self._format_inst_documentation(docs[0])

        # Multiple variations - generate pills and tab content
        pills = []
        contents = []

        for i, doc in enumerate(docs):
            active_class = "active" if i == 0 else ""
            show_class = "show active" if i == 0 else ""
            tab_id = f"{group_id}-doc-{i}"

            pills.append(f'''
                <li class="nav-item" role="presentation">
                    <button class="nav-link py-1 px-2 {active_class} text-start h-100" id="{tab_id}-tab" data-bs-toggle="pill" 
                        data-bs-target="#{tab_id}" type="button" role="tab" aria-controls="{tab_id}" 
                        aria-selected="{"true" if i == 0 else "false"}" style="font-size: 0.75rem; max-width: 60px;">
                        <i class="fas fa-info-circle me-1"></i> {i+1}
                    </button>
                </li>''')

            contents.append(f'''
                <div class="tab-pane fade {show_class}" id="{tab_id}" role="tabpanel" aria-labelledby="{tab_id}-tab">
                    {self._format_inst_documentation(doc)}
                </div>''')

        return f'''
            <div class="card-body border-top p-0">
                <ul class="nav nav-pills p-2 bg-dark" id="{group_id}-pills" role="tablist">
                    <li class="nav-item disabled me-2"><span class="nav-link disabled py-1 px-0 text-muted" style="font-size: 0.75rem;">polymorph:</span></li>
                    {''.join(pills)}
                </ul>
                <div class="tab-content" id="{group_id}-content">
                    {''.join(contents)}
                </div>
            </div>'''

    def _format_type_signature(self, domain: str, range_type: str, domain_full: str = "", range_full: str = "") -> str:
        """Format a type signature (domain => range) with subscripts and clickable links."""
        if not domain and not range_type:
            return ""

        parts = []
        if domain:
            # Make domain clickable - link to type section
            domain_html = self._make_type_link(domain, domain_full or domain, "text-info", "domain")
            parts.append(f'<span class="instset-doc-small-code">{domain_html}</span>')

        if domain or range_type:
            parts.append('<span class="text-muted mx-1">=&gt;</span>')

        if range_type:
            # Make range clickable - link to type section
            range_html = self._make_type_link(range_type, range_full or range_type, "text-success", "range")
            parts.append(f'<span class="instset-doc-small-code">{range_html}</span>')

        return f'<span class="ms-1">{"".join(parts)}</span>'

    def _format_inst_type_signature(self, inst: InstInfo) -> str:
        """Format instruction type signature as name_{domain} => _{range} with subscripts and clickable links."""
        return self._format_type_signature(inst.domain, inst.range, inst.domain_full, inst.range_full)

    def _make_type_link(self, type_short: str, type_full: str, css_class: str, tooltip: str = "") -> str:
        """Create a clickable link to a type definition."""
        if not type_full:
            return f'<span class="code {css_class}">{html.escape(type_short)}</span>'

        # Extract type name from full URI (e.g., /m/tble/space/tble -> tble)
        type_name = type_full.split('/')[-1]
        qless_name = type_name.split('?')[0]
        cless_name = qless_name.split('{')[0]
        cardinality = type_name.split('{')[1].split('}')[0] if '{' in type_name else None
        if cardinality:
            if cardinality == "*":
                cardinality = "maybe some "
            elif cardinality == "?":
                cardinality = "maybe "
            elif cardinality == "+":
                cardinality = "some "
            elif cardinality == "0":
                cardinality = "noobj "
            else:
                cardinality = ""
        else:
            cardinality = ""
        generic = cless_name.isupper()
        # Extract instset from full URI
        type_instset = self._extract_instset(type_full)

        if generic:
            return f'<a href="#" data-bs-toggle="tooltip" title="{cardinality}generic {tooltip}" class="code {css_class}">{html.escape(type_short)}</a>'
        if type_instset and type_instset != self.instset.vid:
            # Link to different instset file
            instset_file = self._make_filename(type_instset)
            return f'<a href="{instset_file}#type-{html.escape(cless_name)}" data-bs-toggle="tooltip" title="{cardinality}{tooltip}" class="code {css_class}">{html.escape(type_short)}</a>'
        else:
            # Link within same document
            return f'<a href="#type-{html.escape(cless_name)}" data-bs-toggle="tooltip" title="{cardinality}{tooltip}" class="code {css_class}">{html.escape(type_short)}</a>'

    def _format_inst_documentation(self, doc: Optional[DocInfo]) -> str:
        """
        Format instruction documentation with special layout:
        desc / domain => range [args] / examples
        """
        if not doc:
            return ""

        parts = []

        # Description
        if doc.desc:
            parts.append(f'''
                    <div class="card-body border-top py-2">
                        <p class="mb-0 text-light">{html.escape(doc.desc)}</p>
                    </div>''')

        # Domain => Range with Arguments
        if doc.dom or doc.rng or doc.args:
            signature_parts = []

            if doc.dom or doc.rng:
                dom_text = html.escape(doc.dom) if doc.dom else '?'
                rng_text = html.escape(doc.rng) if doc.rng else '?'
                signature_parts.append(f'"{dom_text}" <span class="text-light">=&gt;</span> "{rng_text}"')

            if doc.args:
                args_html = self._format_args_map(doc.args)
                if args_html:
                    signature_parts.append(f'\n   {args_html}')

            if signature_parts:
                signature_html = ''.join(signature_parts)
                parts.append(f'''
                    <div class="card-body py-2">
                        <pre class="mb-0 text-bright" style="font-family: monospace; font-size: 0.8em;">{signature_html}</pre>
                    </div>''')

        # Examples
        examples_html = self._format_examples(doc)
        if examples_html:
            parts.append(examples_html)
        return ''.join(parts)

    def _format_args_map(self, args_str: str) -> str:
        """Parse JSON args dict and format as multi-line display. Returns empty string if no args."""
        if not args_str:
            return ""

        try:
            args_dict = json.loads(args_str)
            if not args_dict or not isinstance(args_dict, dict):
                return ""

            arg_lines = [f'{html.escape(str(k))} <span class="text-light">=&gt;</span> "{html.escape(str(v))}"'
                         for k, v in args_dict.items()]
            return '[' + '\n    '.join(arg_lines) + ']'

        except (json.JSONDecodeError, ValueError):
            logger.debug(f"Args parse error: {args_str[:100]}")
            return ""

    def _format_examples(self, doc: Optional[DocInfo]) -> str:
        """Format examples from doc info as HTML code blocks."""
        if not doc or not doc.example:
            return ""

        examples_html = []
        for example in doc.example:
            examples_html.append(html.escape(example))

        return f'''
        <div class="card-body border-top py-2">
            <small class="text-muted fw-bold">examples:</small>
            <pre><code class="language-mtron" style="padding:0 0.75rem 0 !important">{'\n'.join(examples_html)}</code></pre>
        </div>'''

    def _generate_rewrites_section(self) -> str:
        if not self.instset.rewrites:
            return ""

        items = []
        for rewrite in sorted(self.instset.rewrites, key=lambda x: x.name):
            signature_html = ""
            if rewrite.signature:
                signature_html = f'''
                    <div class="card-body p-2">
                        <pre class="mb-0"><code class="language-mtron">{html.escape(rewrite.signature)}</code></pre>
                    </div>'''

            # Generate full documentation
            doc_group_id = f"rewrite-{html.escape(rewrite.name)}"

            # Use first doc/rewrite for the initial header
            initial_sig = self._format_rewrite_type_signature(rewrite)
            initial_vid = html.escape(rewrite.vid)

            doc_html = self._format_multi_documentation(rewrite.docs, doc_group_id)

            items.append(f"""
                <div class="card mb-3" id="{doc_group_id}">
                    <div class="card-header d-flex justify-content-between align-items-center py-2">
                        <span>
                            <span class="code text-warning fw-bold">{html.escape(rewrite.name)}</span>
                            {initial_sig}
                        </span>
                        <small class="text-muted code">{initial_vid}</small>
                    </div>
                    {signature_html}
                    {doc_html}
                </div>""")

        return f"""
        <div class="container-xxl mb-4" id="rewrites">
            <h3 class="text-primary mb-3">
                Rewrites <span class="badge bg-warning text-dark">{len(self.instset.rewrites)}</span>
            </h3>
            {''.join(items)}
        </div>"""

    def _format_rewrite_type_signature(self, rewrite: RewriteInfo) -> str:
        """Format rewrite type signature as name_{domain} => _{range} with subscripts and clickable links."""
        return self._format_type_signature(rewrite.domain, rewrite.range, rewrite.domain_full, rewrite.range_full)

    def _generate_spaces_section(self) -> str:
        if not self.instset.spaces:
            return ""

        items = []
        for space in sorted(self.instset.spaces, key=lambda x: x.name):
            # Show type spec if available
            type_spec_html = ""
            if space.type_spec:
                type_spec_html = f'''
                    <div class="mt-2">
                        <pre class="mb-0"><code class="language-mtron">{html.escape(space.type_spec)}</code></pre>
                    </div>'''

            # Generate full documentation
            doc_group_id = f"space-{html.escape(space.name)}"
            doc_html = self._format_multi_documentation(space.docs, doc_group_id)

            items.append(f"""
                <div class="card mb-3" id="{doc_group_id}">
                    <div class="card-header d-flex justify-content-between align-items-center py-2">
                        <a href="{self._make_filename(space.vid)}" class="code text-primary fw-bold text-decoration-none">{html.escape(space.name)}</a>
                        <small class="text-muted code">{html.escape(space.vid)}</small>
                    </div>
                    {type_spec_html if type_spec_html else ''}
                    {doc_html}
                </div>""")

        return f"""
        <div class="container-xxl mb-4" id="spaces">
            <h3 class="text-primary mb-3">
                Spaces <span class="badge bg-info">{len(self.instset.spaces)}</span>
            </h3>
            {''.join(items)}
        </div>"""

    def _generate_consts_section(self) -> str:
        if not self.instset.consts:
            return ""

        items = []
        for const in sorted(self.instset.consts, key=lambda x: x.name):
            definition_html = ""
            if const.definition:
                definition_html = f'''
                    <div class="card-body p-2">
                        <pre class="mb-0"><code class="language-mtron">{html.escape(const.definition)}</code></pre>
                    </div>'''

            # Generate full documentation
            doc_group_id = f"const-{html.escape(const.name)}"
            doc_html = self._format_multi_documentation(const.docs, doc_group_id)

            items.append(f"""
                <div class="card mb-3" id="{doc_group_id}">
                    <div class="card-header d-flex justify-content-between align-items-center py-2">
                        <span class="code text-secondary fw-bold">{html.escape(const.name)}</span>
                        <small class="text-muted code">{html.escape(const.vid)}</small>
                    </div>
                    {definition_html}
                    {doc_html}
                </div>""")

        return f"""
        <div class="container-xxl mb-4" id="consts">
            <h3 class="text-primary mb-3">
                Constants <span class="badge bg-secondary">{len(self.instset.consts)}</span>
            </h3>
            {''.join(items)}
        </div>"""

    def _generate_instset_footer(self, build_number: int = 0) -> str:
        timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        return f"""
        <div class="container-xxl py-3 text-center">
            <hr class="border-secondary">
            <small class="text-muted">
                generated by metatron instset doc generator on build {build_number}-{timestamp}<br>
                © PhaseShift Studio, LLC
            </small>
        </div>"""

    def _make_filename(self, vid: str) -> str:
        """Convert a vid to a safe filename."""
        return vid.replace('/', '_').strip('_') + '.html'


# ============================================================================
# Index Page Generator
# ============================================================================

def generate_index_page(instsets: List[InstSetInfo], embed_css: bool = True, css_path: str = "../css/instset_doc.css",
                        use_website_template: bool = False, relative_depth: str = "..", build_number: int = 0) -> str:
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
                                    <span class="badge bg-primary">{len(info.types)} types</span>
                                    <span class="badge bg-success">{len(info.insts)} instructions</span>
                                    <span class="badge bg-info text-dark">{len(info.spaces)} spaces</span>
                                </div>
                            </div>
                        </div>
                    </a>
                </div>""")

    content = f"""
        <div class="container-xxl py-4">
            <div class="text-center mb-5">
                <h1 class="text-primary glow-text">metatron</h1>
                <p class="subtitle text-light">instruction set documentation</p>
            </div>

            <div class="row g-3">
                {''.join(items)}
            </div>

            <div class="py-3 text-center mt-5">
                <hr class="border-secondary">
                <small class="text-muted">
                    metatron instset doc generator on build {build_number}-{datetime.now().strftime("%Y-%m-%d %H:%M:%S")}<br>
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
            header = re.sub(r'<title>.*?</title>', '<title>metatron instruction sets</title>', header)

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
    <title>metatron instruction set reference</title>
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
    # CSS file is now in website/css/ directory, referenced via relative path
    css_path = "../css/instset_doc.css"
    use_website_template = args.website_template
    relative_depth = args.relative_depth

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
                    css_path=css_path,
                    use_website_template=use_website_template,
                    relative_depth=relative_depth
                )
                html_content = generator.generate(args.build)

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
                css_path=css_path,
                use_website_template=use_website_template,
                relative_depth=relative_depth,
                build_number=args.build
            )
            index_path = output_dir / 'index.html'
            index_path.write_text(index_html, encoding='utf-8')
            logger.info(f"Generated index: {index_path}")

    finally:
        await client.close()


def main():
    """Entry point."""
    parser = argparse.ArgumentParser(
        description="Generate HTML documentation for metatron instruction sets.",
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
        help='metatron WebSocket host (default: ws://127.0.0.1:8999)'
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
        help='Link to external CSS file instead of embedding (references ../css/instset_doc.css)'
    )
    parser.add_argument(
        '--build',
        type=int,
        default=0,
        help='Build number for generated docs'
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
