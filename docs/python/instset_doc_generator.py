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

    async def rewrite(self, uri: str) -> tuple:
        """
        Call rewrite(uri) to get short and long forms.

        Returns (short, long) tuple where:
        - short: shortened form like /m/inst/plus
        - long: full expanded form like plus

        Example: rewrite(plus) returns [short=>/m/inst/plus,long=>plus]
        """
        try:
            # Call rewrite instruction
            result = await self.eval(f"rewrite({uri})")
            # Parse result: [short=>/m/inst/plus,long=>plus]
            short_match = re.search(r'short\s*=>\s*([^,\]]+)', result)
            long_match = re.search(r'long\s*=>\s*([^,\]]+)', result)
            short_val = short_match.group(1).strip() if short_match else uri
            long_val = long_match.group(1).strip() if long_match else uri
            return (short_val, long_val)
        except Exception as e:
            logger.debug(f"rewrite({uri}) failed: {e}")
            return (uri, uri)

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

    async def fetch_obj_doc(self, vid: str) -> Optional[DocInfo]:
        """
        Fetch documentation for an object via xyz?doc.

        Example response when doc exists:
        doc::[
         obj =>start?rng=A{**}&dom=noobj{0}(A{**}::T){<j>},
         dom =>'noobj',
         rng =>'initial objs',
         args=>[0=>'initial objs'],
         desc=>'the initial function f()->x']

        When no doc exists, returns either:
        - "noobj"
        - The object itself (not a doc record)

        Returns DocInfo with parsed fields, or None if no doc available.
        """
        try:
            # Build the doc query:
            # - If vid has query params (dom/rng), append &doc to get specific polymorphic form
            # - If vid has no query params, use ?doc
            # e.g., plus?rng=int&dom=int → *plus?rng=int&dom=int&doc
            # e.g., /m/lst → */m/lst?doc
            if '?' in vid:
                doc_query = f"*{vid}&doc"
            else:
                doc_query = f"*{vid}?doc"

            logger.debug(f"Fetching doc with query: {doc_query}")

            # Query the doc for this object
            result = await self.eval(doc_query)

            logger.debug(f"Doc query result for {vid}: {result[:200] if result else 'None'}...")

            if not result or result == "noobj" or result.startswith("</m/fail>"):
                return None

            # Check if this is actually a doc record
            # WebSocket format: </m/space/q/docq/doc>::[</m/uri>::<desc> => </m/str>::'...']
            # Console format: ==>doc::[desc=>'...']
            if not ("doc>::[" in result or "<desc>" in result or "desc=>" in result):
                logger.debug(f"No doc record for {vid}, got object back")
                return None

            # Parse the doc record
            doc_info = DocInfo(raw=result)

            # Extract desc field - handle both WebSocket and console formats:
            # WebSocket: </m/uri>::<desc> => </m/str>::'the initial function f()->x'
            # Console: desc=>'the initial function f()->x'
            desc_match = re.search(r"<desc>\s*=>\s*</m/str>::'([^']*)'", result)
            if not desc_match:
                desc_match = re.search(r"desc\s*=>\s*'([^']*)'", result)
            if desc_match:
                doc_info.desc = desc_match.group(1)

            # Extract dom field
            # WebSocket: </m/uri>::<dom> => </m/str>::'noobj'
            dom_match = re.search(r"<dom>\s*=>\s*</m/str>::'([^']*)'", result)
            if not dom_match:
                dom_match = re.search(r"dom\s*=>\s*'([^']*)'", result)
            if dom_match:
                doc_info.dom = dom_match.group(1)

            # Extract rng field
            # WebSocket: </m/uri>::<rng> => </m/str>::'initial objs'
            rng_match = re.search(r"<rng>\s*=>\s*</m/str>::'([^']*)'", result)
            if not rng_match:
                rng_match = re.search(r"rng\s*=>\s*'([^']*)'", result)
            if rng_match:
                doc_info.rng = rng_match.group(1)

            # Extract obj field
            # WebSocket: </m/uri>::<obj> => </m/inst/start?rng=A{**}&dom=noobj{0}>(A{**}::T){<j>}
            obj_match = re.search(r"<obj>\s*=>\s*([^,\]]+)", result)
            if not obj_match:
                obj_match = re.search(r"obj\s*=>\s*([^,\]]+)", result)
            if obj_match:
                doc_info.obj = obj_match.group(1).strip()

            # Extract args field
            # WebSocket: </m/uri>::<args> => </m/rec>::[...]
            args_match = re.search(r"<args>\s*=>\s*</m/rec>::(\[[^\]]*\])", result)
            if not args_match:
                args_match = re.search(r"args\s*=>\s*(\[[^\]]*\])", result)
            if args_match:
                doc_info.args = args_match.group(1)

            # Return if we got at least desc
            if doc_info.desc:
                return doc_info

            return None

        except Exception as e:
            logger.debug(f"Could not fetch doc for {vid}: {e}")
            return None


# ============================================================================
# Data Models
# ============================================================================

@dataclass
class DocInfo:
    """Documentation information fetched via xyz?doc."""
    obj: str = ""  # The object this doc is for
    dom: str = ""  # Domain description
    rng: str = ""  # Range description
    args: str = ""  # Arguments description (raw string)
    desc: str = ""  # Description text
    raw: str = ""  # Raw doc response


@dataclass
class TypeInfo:
    """Information about a type defined in an instruction set."""
    vid: str  # Full path (e.g., /m/mach/machine)
    name: str  # Short name (e.g., machine)
    definition: str  # Pretty-printed definition in mtron syntax
    raw: str = ""  # Raw entry string
    super_type: str = ""  # Full URI of super type (e.g., /m/lst)
    super_type_short: str = ""  # Short name of super type (e.g., lst)
    super_type_instset: str = ""  # Instruction set containing super type (e.g., /m)
    doc: Optional[DocInfo] = None  # Documentation from xyz?doc


@dataclass
class InstInfo:
    """Information about an instruction."""
    vid: str  # Full path with signature (e.g., /m/mach/inst/run?rng=...&dom=...)
    name: str  # Short name (e.g., run)
    signature: str  # Pretty-printed signature in mtron syntax
    raw: str = ""  # Raw entry string
    domain: str = ""  # Domain type (e.g., str, lst)
    range: str = ""  # Range type (e.g., int, bool)
    domain_full: str = ""  # Full domain URI (e.g., /m/str)
    range_full: str = ""  # Full range URI (e.g., /m/int)
    doc: Optional[DocInfo] = None  # Documentation from xyz?doc


@dataclass
class SpaceInfo:
    """Information about a sub-space/sub-instruction set."""
    vid: str
    name: str
    raw: str = ""
    type_spec: str = ""  # Type specification from querying */path/to/space
    doc: Optional[DocInfo] = None  # Documentation from xyz?doc


@dataclass
class ConstInfo:
    """Information about a constant defined in an instruction set."""
    vid: str
    name: str
    definition: str = ""
    raw: str = ""
    doc: Optional[DocInfo] = None  # Documentation from xyz?doc


@dataclass
class RewriteInfo:
    """Information about a rewrite rule defined in an instruction set."""
    vid: str
    name: str
    signature: str = ""
    raw: str = ""
    domain: str = ""  # Domain type short form
    range: str = ""  # Range type short form
    domain_full: str = ""  # Full domain URI
    range_full: str = ""  # Full range URI
    doc: Optional[DocInfo] = None  # Documentation from xyz?doc


@dataclass
class InstSetInfo:
    """Complete information about an instruction set."""
    vid: str  # e.g., /m/mach
    name: str  # e.g., mach
    parent: Optional[str] = None  # Super instruction set
    children: List[str] = field(default_factory=list)  # Sub instruction sets
    types: List[TypeInfo] = field(default_factory=list)
    insts: List[InstInfo] = field(default_factory=list)
    rewrites: List[RewriteInfo] = field(default_factory=list)
    spaces: List[SpaceInfo] = field(default_factory=list)
    consts: List[ConstInfo] = field(default_factory=list)
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

        # Fetch rewrites
        info.rewrites = await self._fetch_rewrites(vid)

        # Fetch spaces (sub-instruction sets)
        info.spaces = await self._fetch_spaces(vid)

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
                if type_info:
                    # Query raw type info to get super type
                    await self._fetch_type_super(type_info)
                    # Fetch documentation
                    type_info.doc = await self.client.fetch_obj_doc(type_info.vid)
                    types.append(type_info)
                    logger.debug(f"  Found type: {type_info.name} (refines {type_info.super_type})")
        except Exception as e:
            logger.warning(f"Could not fetch types for {vid}: {e}")
        return types

    async def _fetch_type_super(self, type_info: TypeInfo):
        """Fetch the super type for a type by querying *typename (raw, not doc())."""
        try:
            # Query raw type info: *lrow returns something like lst::T@/m/tble/lrow
            # The first part before :: is the super type short name
            raw_result = await self.client.eval(f"*{type_info.vid}")
            if raw_result and "::" in raw_result:
                # Parse: lst::T@/m/tble/lrow
                # The first part (lst) is the short super type name
                parts = raw_result.split("::")
                if len(parts) >= 1:
                    super_short = parts[0].strip()
                    # Query the super type to get its full URI
                    if super_short and super_short != type_info.name:
                        type_info.super_type_short = super_short
                        super_raw = await self.client.eval(f"*{super_short}")
                        # Result format: rec::T@/m/rec or similar
                        # Extract the VID after the @ symbol
                        if super_raw and "@" in super_raw:
                            vid_part = super_raw.split("@")[-1].strip()
                            if vid_part.startswith("/"):
                                type_info.super_type = vid_part
                                # Extract instset from super type (e.g., /m/lst -> /m)
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
                    inst_info.doc = await self.client.fetch_obj_doc(inst_info.vid)
                    insts.append(inst_info)
                    logger.debug(f"  Found instruction: {inst_info.name} ({inst_info.domain}=>{inst_info.range})")
        except Exception as e:
            logger.warning(f"Could not fetch instructions for {vid}: {e}")
        return insts

    async def _fetch_inst_short_forms(self, inst_info: InstInfo):
        """Fetch short forms for domain and range using rewrite()."""
        # Store full URIs
        inst_info.domain_full = inst_info.domain
        inst_info.range_full = inst_info.range

        # Get short forms using rewrite()
        if inst_info.domain:
            _, long_form = await self.client.rewrite(inst_info.domain)
            inst_info.domain = long_form

        if inst_info.range:
            _, long_form = await self.client.rewrite(inst_info.range)
            inst_info.range = long_form

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
                    space_info.doc = await self.client.fetch_obj_doc(space_vid)
                    spaces.append(space_info)
                    logger.debug(f"  Found space: {space_vid}")
        except Exception as e:
            logger.warning(f"Could not fetch spaces for {vid}: {e}")
        return spaces

    async def _fetch_space_type(self, space_info: SpaceInfo):
        """Fetch the type specification for a space by querying *spacepath."""
        try:
            # Query raw space info: */m/tble/space/tble
            raw_result = await self.client.eval(f"*{space_info.vid}")
            if raw_result:
                space_info.type_spec = raw_result
        except Exception as e:
            logger.debug(f"Could not fetch type spec for space {space_info.vid}: {e}")

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
                        # Fetch documentation
                        rewrite_info.doc = await self.client.fetch_obj_doc(rewrite_vid)
                        rewrites.append(rewrite_info)
                        logger.debug(f"  Found rewrite: {name} ({rewrite_info.domain}=>{rewrite_info.range})")
        except Exception as e:
            logger.debug(f"Could not fetch rewrites for {vid}: {e}")
        return rewrites

    async def _fetch_rewrite_short_forms(self, rewrite_info: RewriteInfo):
        """Fetch short forms for domain and range using rewrite()."""
        # Store full URIs
        rewrite_info.domain_full = rewrite_info.domain
        rewrite_info.range_full = rewrite_info.range

        # Get short forms using rewrite()
        if rewrite_info.domain:
            _, long_form = await self.client.rewrite(rewrite_info.domain)
            rewrite_info.domain = long_form

        if rewrite_info.range:
            _, long_form = await self.client.rewrite(rewrite_info.range)
            rewrite_info.range = long_form

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
                        const_info.doc = await self.client.fetch_obj_doc(const_vid)
                        consts.append(const_info)
                        logger.debug(f"  Found const: {name}")
        except Exception as e:
            logger.debug(f"Could not fetch consts for {vid}: {e}")
        return consts

    def _extract_instset(self, type_uri: str) -> str:
        """Extract the instruction set from a type URI (e.g., /m/lst -> /m, /m/tble/lrow -> /m/tble)."""
        if not type_uri:
            return ""
        parts = type_uri.strip('/').split('/')
        if len(parts) <= 1:
            return "/" + parts[0] if parts else ""
        # InstSet is all but the last part (which is the type name)
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

        # Extract domain and range from vid query string before conversion
        domain, range_type = self._extract_dom_rng(vid)

        # Convert dom=&rng= to rng<=dom in both vid and signature
        vid = self._convert_signature_shorthand(vid)
        signature = self._convert_signature_shorthand(signature)

        return InstInfo(vid=vid, name=name, signature=signature, raw=entry,
                        domain=domain, range=range_type)

    def _extract_dom_rng(self, vid: str) -> tuple:
        """Extract domain and range type names from a vid with query string."""
        domain = ""
        range_type = ""
        if '?' in vid:
            query = vid.split('?', 1)[1]
            # Parse rng= value
            rng_match = re.search(r'rng=([^&]+)', query)
            if rng_match:
                range_type = rng_match.group(1)
            # Parse dom= value
            dom_match = re.search(r'dom=([^&]+)', query)
            if dom_match:
                domain = dom_match.group(1)
        return domain, range_type

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
        """Extract the instruction set from a type URI (e.g., /m/lst -> /m, /m/tble/lrow -> /m/tble)."""
        if not type_uri:
            return ""
        parts = type_uri.strip('/').split('/')
        if len(parts) <= 1:
            return "/" + parts[0] if parts else ""
        # InstSet is all but the last part (which is the type name)
        return "/" + "/".join(parts[:-1])

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
            {self._generate_hierarchy()}
            {self._generate_toc()}
            {self._generate_types_section()}
            {self._generate_instructions_section()}
            {self._generate_rewrites_section()}
            {self._generate_spaces_section()}
            {self._generate_consts_section()}
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
        {self._generate_hierarchy()}
        {self._generate_toc()}
        {self._generate_types_section()}
        {self._generate_instructions_section()}
        {self._generate_rewrites_section()}
        {self._generate_spaces_section()}
        {self._generate_consts_section()}
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
        """Generate navigation bar with all 5 categories (disabled if empty)."""
        def nav_btn(href: str, label: str, count: int) -> str:
            if count > 0:
                return f'<a href="#{href}" class="btn btn-outline-primary">{label} <span class="badge bg-secondary">{count}</span></a>'
            else:
                return f'<button class="btn btn-outline-secondary" disabled>{label} <span class="badge bg-dark">0</span></button>'

        return f"""
        <div class="container-xxl mb-4">
            <div class="d-flex justify-content-center gap-2 flex-wrap">
                {nav_btn("types", "Types", len(self.instset.types))}
                {nav_btn("instructions", "Insts", len(self.instset.insts))}
                {nav_btn("rewrites", "Rewrites", len(self.instset.rewrites))}
                {nav_btn("spaces", "Spaces", len(self.instset.spaces))}
                {nav_btn("consts", "Consts", len(self.instset.consts))}
            </div>
        </div>"""

    def _generate_stats(self) -> str:
        """Generate stats cards for all 5 categories."""
        def stat_card(count: int, label: str, color: str = "primary") -> str:
            text_class = "text-muted" if count == 0 else f"text-{color}"
            return f'''
                <div class="col-auto">
                    <div class="card text-center" style="min-width: 100px;">
                        <div class="card-body py-2">
                            <h3 class="{text_class} mb-0">{count}</h3>
                            <small class="text-light">{label}</small>
                        </div>
                    </div>
                </div>'''

        return f"""
        <div class="container-xxl mb-4">
            <div class="row g-2 justify-content-center">
                {stat_card(len(self.instset.types), "Types", "info")}
                {stat_card(len(self.instset.insts), "Insts", "success")}
                {stat_card(len(self.instset.rewrites), "Rewrites", "warning")}
                {stat_card(len(self.instset.spaces), "Spaces", "primary")}
                {stat_card(len(self.instset.consts), "Consts", "secondary")}
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
                <a href="#type-{html.escape(t.name)}" class="list-group-item list-group-item-action d-flex justify-content-between align-items-center bg-dark text-light border-secondary py-1 px-2 small">
                    <span class="code">{html.escape(t.name)}</span>
                    <span class="badge bg-info">T</span>
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

        if not type_items and not inst_items:
            return ""

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

        return f"""
        <div class="container-xxl mb-4">
            <div class="card">
                <div class="card-header">
                    <h5 class="mb-0 text-primary">Index</h5>
                </div>
                <div class="card-body">
                    {type_list}
                    {inst_list}
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
                        <span class="ms-2 text-muted">
                            refines <a href="{instset_file}#type-{html.escape(super_name)}" class="text-info code">{html.escape(display_name)}</a>
                        </span>'''
                else:
                    refines_html = f'<span class="ms-2 text-muted">refines <span class="code text-info">{html.escape(display_name)}</span></span>'

            # Generate description from doc if available
            desc_html = ""
            if t.doc and t.doc.desc:
                desc_html = f'''
                    <div class="card-body border-top py-2">
                        <p class="mb-0 text-light">{html.escape(t.doc.desc)}</p>
                    </div>'''
                # Commented out: other doc fields available
                # if t.doc.dom: dom_desc = t.doc.dom
                # if t.doc.rng: rng_desc = t.doc.rng
                # if t.doc.args: args_desc = t.doc.args
                # if t.doc.obj: obj_desc = t.doc.obj

            items.append(f"""
                <div class="card mb-3" id="type-{html.escape(t.name)}">
                    <div class="card-header d-flex justify-content-between align-items-center py-2">
                        <span>
                            <span class="code text-primary fw-bold">{html.escape(t.name)}::T</span>
                            {refines_html}
                        </span>
                        <small class="text-muted code">{html.escape(t.vid)}</small>
                    </div>
                    {definition}
                    {desc_html}
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

            # Use first inst for the header - show domain => range with subscripts
            first_inst = insts[0]
            type_sig_html = self._format_inst_type_signature(first_inst)

            # Generate description from doc if available (use first inst's doc)
            desc_html = ""
            if first_inst.doc and first_inst.doc.desc:
                desc_html = f'''
                    <div class="card-body border-top py-2">
                        <p class="mb-0 text-light">{html.escape(first_inst.doc.desc)}</p>
                    </div>'''
                # Commented out: other doc fields available
                # if first_inst.doc.dom: dom_desc = first_inst.doc.dom
                # if first_inst.doc.rng: rng_desc = first_inst.doc.rng
                # if first_inst.doc.args: args_desc = first_inst.doc.args
                # if first_inst.doc.obj: obj_desc = first_inst.doc.obj

            items.append(f"""
                <div class="card mb-3" id="inst-{html.escape(name)}">
                    <div class="card-header d-flex justify-content-between align-items-center py-2">
                        <span>
                            <span class="code text-primary fw-bold">{html.escape(name)}</span>
                            {type_sig_html}
                        </span>
                        <small class="text-muted code">{html.escape(first_inst.vid)}</small>
                    </div>
                    <div class="card-body p-2">
                        {all_sigs}
                    </div>
                    {desc_html}
                </div>""")

        return f"""
        <div class="container-xxl mb-4" id="instructions">
            <h3 class="text-primary mb-3">
                Instructions <span class="badge bg-success">{len(self.instset.insts)}</span>
            </h3>
            {''.join(items)}
        </div>"""

    def _format_inst_type_signature(self, inst: InstInfo) -> str:
        """Format instruction type signature as name_{domain} => _{range} with subscripts and clickable links."""
        if not inst.domain and not inst.range:
            return ""

        parts = []
        if inst.domain:
            # Make domain clickable - link to type section
            domain_html = self._make_type_link(inst.domain, inst.domain_full, "text-info")
            parts.append(f'<sub>{domain_html}</sub>')

        if inst.domain or inst.range:
            parts.append('<span class="text-muted mx-1">=&gt;</span>')

        if inst.range:
            # Make range clickable - link to type section
            range_html = self._make_type_link(inst.range, inst.range_full, "text-success")
            parts.append(f'<sub>{range_html}</sub>')

        return f'<span class="ms-1">{"".join(parts)}</span>'

    def _make_type_link(self, type_short: str, type_full: str, css_class: str) -> str:
        """Create a clickable link to a type definition."""
        if not type_full:
            return f'<span class="code {css_class}">{html.escape(type_short)}</span>'

        # Extract type name from full URI (e.g., /m/tble/space/tble -> tble)
        type_name = type_full.split('/')[-1].split('?')[0]

        # Extract instset from full URI
        type_instset = self._extract_instset(type_full)

        if type_instset and type_instset != self.instset.vid:
            # Link to different instset file
            instset_file = self._make_filename(type_instset)
            return f'<a href="{instset_file}#type-{html.escape(type_name)}" class="code {css_class}">{html.escape(type_short)}</a>'
        else:
            # Link within same document
            return f'<a href="#type-{html.escape(type_name)}" class="code {css_class}">{html.escape(type_short)}</a>'

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

            # Format domain/range with subscripts and clickable links (same as instructions)
            type_sig_html = self._format_rewrite_type_signature(rewrite)

            # Generate description from doc if available
            desc_html = ""
            if rewrite.doc and rewrite.doc.desc:
                desc_html = f'''
                    <div class="card-body border-top py-2">
                        <p class="mb-0 text-light">{html.escape(rewrite.doc.desc)}</p>
                    </div>'''
                # Commented out: other doc fields available
                # if rewrite.doc.dom: dom_desc = rewrite.doc.dom
                # if rewrite.doc.rng: rng_desc = rewrite.doc.rng
                # if rewrite.doc.args: args_desc = rewrite.doc.args
                # if rewrite.doc.obj: obj_desc = rewrite.doc.obj

            items.append(f"""
                <div class="card mb-3" id="rewrite-{html.escape(rewrite.name)}">
                    <div class="card-header d-flex justify-content-between align-items-center py-2">
                        <span>
                            <span class="code text-warning fw-bold">{html.escape(rewrite.name)}</span>
                            {type_sig_html}
                        </span>
                        <small class="text-muted code">{html.escape(rewrite.vid)}</small>
                    </div>
                    {signature_html}
                    {desc_html}
                </div>""")

        return f"""
        <div class="container-xxl mb-4" id="rewrites">
            <h3 class="text-warning mb-3">
                Rewrites <span class="badge bg-warning text-dark">{len(self.instset.rewrites)}</span>
            </h3>
            {''.join(items)}
        </div>"""

    def _format_rewrite_type_signature(self, rewrite: RewriteInfo) -> str:
        """Format rewrite type signature as name_{domain} => _{range} with subscripts and clickable links."""
        if not rewrite.domain and not rewrite.range:
            return ""

        parts = []
        if rewrite.domain:
            # Make domain clickable - link to type section
            domain_html = self._make_type_link(rewrite.domain, rewrite.domain_full, "text-info")
            parts.append(f'<sub>{domain_html}</sub>')

        if rewrite.domain or rewrite.range:
            parts.append('<span class="text-muted mx-1">=&gt;</span>')

        if rewrite.range:
            # Make range clickable - link to type section
            range_html = self._make_type_link(rewrite.range, rewrite.range_full, "text-success")
            parts.append(f'<sub>{range_html}</sub>')

        return f'<span class="ms-1">{"".join(parts)}</span>'

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

            # Generate description from doc if available
            desc_html = ""
            if space.doc and space.doc.desc:
                desc_html = f'''
                    <div class="card-body border-top py-2">
                        <p class="mb-0 text-light">{html.escape(space.doc.desc)}</p>
                    </div>'''
                # Commented out: other doc fields available
                # if space.doc.dom: dom_desc = space.doc.dom
                # if space.doc.rng: rng_desc = space.doc.rng
                # if space.doc.args: args_desc = space.doc.args
                # if space.doc.obj: obj_desc = space.doc.obj

            items.append(f"""
                <div class="card mb-3" id="space-{html.escape(space.name)}">
                    <div class="card-header d-flex justify-content-between align-items-center py-2">
                        <a href="{self._make_filename(space.vid)}" class="code text-primary fw-bold text-decoration-none">{html.escape(space.name)}</a>
                        <small class="text-muted code">{html.escape(space.vid)}</small>
                    </div>
                    {type_spec_html if type_spec_html else ''}
                    {desc_html}
                </div>""")

        return f"""
        <div class="container-xxl mb-4" id="spaces">
            <h3 class="text-primary mb-3">
                Spaces <span class="badge bg-primary">{len(self.instset.spaces)}</span>
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

            # Generate description from doc if available
            desc_html = ""
            if const.doc and const.doc.desc:
                desc_html = f'''
                    <div class="card-body border-top py-2">
                        <p class="mb-0 text-light">{html.escape(const.doc.desc)}</p>
                    </div>'''
                # Commented out: other doc fields available
                # if const.doc.dom: dom_desc = const.doc.dom
                # if const.doc.rng: rng_desc = const.doc.rng
                # if const.doc.args: args_desc = const.doc.args
                # if const.doc.obj: obj_desc = const.doc.obj

            items.append(f"""
                <div class="card mb-3" id="const-{html.escape(const.name)}">
                    <div class="card-header d-flex justify-content-between align-items-center py-2">
                        <span class="code text-secondary fw-bold">{html.escape(const.name)}</span>
                        <small class="text-muted code">{html.escape(const.vid)}</small>
                    </div>
                    {definition_html}
                    {desc_html}
                </div>""")

        return f"""
        <div class="container-xxl mb-4" id="consts">
            <h3 class="text-secondary mb-3">
                Constants <span class="badge bg-secondary">{len(self.instset.consts)}</span>
            </h3>
            {''.join(items)}
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

def generate_index_page(instsets: List[InstSetInfo], embed_css: bool = True, css_path: str = "../css/instset_doc.css",
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
                css_path=css_path,
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
        help='Link to external CSS file instead of embedding (references ../css/instset_doc.css)'
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
