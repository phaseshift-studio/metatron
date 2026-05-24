#  Metatron: A Distributed Computing Language and Virtual Machine
#  Copyright (C) 2025- PhaseShift Studio, LLC
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
from __future__ import annotations

import asyncio
import json
import logging
from typing import Any

import websockets

logger = logging.getLogger(__name__)

# Default WebSocket endpoint
DEFAULT_HOST = "ws://localhost:8555/mtron"


# ============================================================================
# Mytron — single WebSocket connection
# ============================================================================

class Mytron:
    """WebSocket client for communicating with a running metatron instance.

    Connects to the mtron_wsServer endpoint (ws://host:8555/mtron).  Each
    connection is a dedicated session instance of the mtron_ws type, so the
    server evaluates metatron expressions and returns clean metatron-syntax
    results (no %%% separators, no </m/type>:: prefixes for base types).

    Args:
        host:         WebSocket URL (default: ws://localhost:8555/mtron)
        content_type: Dict with 'in' and 'out' MIME types.  Omit for native
                      mtron text; use {"out": "application/json"} for JSON.
        timeout:      Seconds to wait for a single eval response.  Increase for
                      complex/slow expressions.  Default: 5 s.
    """

    def __init__(
        self,
        host: str = DEFAULT_HOST,
        content_type: dict | None = None,
        timeout: int = 5,
    ):
        self.content_type = content_type or {"in": "application/mtron", "out": "application/mtron"}
        self.host    = host
        self.timeout = timeout
        self._ws     = None

    # ── lifecycle ────────────────────────────────────────────────────────────

    async def connect(self) -> None:
        """Establish WebSocket connection."""
        url = f"{self.host}?in={self.content_type['in']}&out={self.content_type['out']}"
        print(f"[mytron] connecting to {url} ...")
        try:
            self._ws = await websockets.connect(url)
            print(f"[mytron] connected ok")
        except Exception as e:
            raise RuntimeError(f"[mytron] connection failed: {e}")

    async def close(self) -> None:
        """Close WebSocket connection."""
        if self._ws:
            await self._ws.close()
            logger.info("Connection closed")

    async def _reconnect(self) -> None:
        """Close the current connection and open a fresh one."""
        try:
            if self._ws:
                await self._ws.close()
        except Exception:
            pass
        self._ws = None
        await self.connect()

    # ── evaluation ───────────────────────────────────────────────────────────

    async def eval(self, expression: str) -> str | Any:
        """Evaluate a metatron expression and return the result.

        For native (application/mtron) connections returns a str.
        For JSON (application/json) connections returns a parsed Python object.
        Returns "" on timeout or empty result.
        """
        if not self._ws:
            raise RuntimeError("Not connected. Call connect() first.")

        print(f"send: {expression}")
        await self._ws.send(expression)
        try:
            raw = await asyncio.wait_for(self._ws.recv(), timeout=self.timeout)
        except (asyncio.TimeoutError, TimeoutError):
            print(f"[mytron] recv timed out ({self.timeout}s): {expression!r} — reconnecting")
            await self._reconnect()
            return ""
        except websockets.exceptions.ConnectionClosedError as e:
            print(f"[mytron] connection closed during eval: {e} — reconnecting")
            await self._reconnect()
            return ""

        raw_str = (raw if isinstance(raw, str) else raw.decode("utf-8")).strip()

        if self.content_type["out"] == "application/json":
            parsed = json.loads(raw_str)
            print(f"recv: {str(parsed)[:200]}")
            return parsed

        print(f"recv: {raw_str[:200]}" if len(raw_str) > 200 else f"recv: {raw_str}")

        if raw_str in ("", "noobj"):
            return ""
        return raw_str

    async def eval_hidden(self, expression: str, timeout: int = 120) -> None:
        """Evaluate an expression and discard the result.

        Uses a longer timeout than eval() since hidden expressions (e.g. fail
        clears) can produce large responses.  Reconnects automatically on
        timeout so subsequent evals are unaffected.
        """
        if not self._ws:
            raise RuntimeError("Not connected. Call connect() first.")
        print(f"send [hidden]: {expression}")
        await self._ws.send(expression)
        try:
            raw = await asyncio.wait_for(self._ws.recv(), timeout=timeout)
            raw_str = raw if isinstance(raw, str) else raw.decode("utf-8")
            print(f"recv [hidden]: {len(raw_str)} chars")
        except (asyncio.TimeoutError, TimeoutError):
            print(f"[mytron] hidden eval timed out ({timeout}s): {expression!r} — reconnecting")
            await self._reconnect()
        except websockets.exceptions.ConnectionClosedError as e:
            print(f"[mytron] connection closed during hidden eval: {e} — reconnecting")
            await self._reconnect()


# ============================================================================
# MtronSession — coordinated multi-connection session
# ============================================================================

class MtronSession:
    """Coordinated session with three Mytron connections.

    Provides:
      native     — application/mtron in+out  (text display, doc code eval)
      json       — application/mtron in / application/json out  (structured queries)
      json_docq  — same as json but with a short timeout for ?docq probes that
                   can hang on URIs containing mtron special characters (#, ^, …)

    Args:
        host:         WebSocket URL (default: ws://localhost:8555/mtron)
        timeout:      Seconds to wait per eval on native and json connections.
        docq_timeout: Seconds to wait on the json_docq connection (default: 6 s).
    """

    DEFAULT_DOCQ_TIMEOUT = 6

    def __init__(
        self,
        host: str = DEFAULT_HOST,
        timeout: int = 5,
        docq_timeout: int = DEFAULT_DOCQ_TIMEOUT,
    ):
        _json_ct = {"in": "application/mtron", "out": "application/json"}
        self.native    = Mytron(host=host, timeout=timeout)
        self.json      = Mytron(host=host, content_type=_json_ct, timeout=timeout)
        self.json_docq = Mytron(host=host, content_type=_json_ct, timeout=docq_timeout)

    async def connect(self) -> None:
        await self.native.connect()
        await self.json.connect()
        await self.json_docq.connect()

    async def close(self) -> None:
        await self.native.close()
        await self.json.close()
        await self.json_docq.close()

    # ── convenience query helpers (used by instset_doc_generator) ────────────

    async def eval_native(self, expr: str) -> str:
        return await self.native.eval(expr)

    async def eval_json(self, expr: str) -> Any:
        return await self.json.eval(expr)

    async def fetch_uris(self, expr: str) -> list[str]:
        """Run a .dom() query; returns a list of URI strings.

        The server returns a JSON array for multiple results but a bare string
        when there is exactly one result — normalise both to a list.
        """
        result = await self.eval_json(expr)
        if isinstance(result, list):
            return [str(u) for u in result]
        if isinstance(result, str) and result:
            return [result]
        return []

    async def fetch_relations(self, expr: str) -> list[tuple[str, str]]:
        """Run a /+/ relation query; returns (uri, obj) pairs."""
        result = await self.eval_json(expr)
        if not isinstance(result, list):
            return []
        return [
            (str(item[0]), str(item[1]))
            for item in result
            if isinstance(item, (list, tuple)) and len(item) >= 2
        ]

    async def fetch_instset_obj(self, vid: str) -> dict:
        """Fetch instset metadata via |*<vid>; returns a dict."""
        result = await self.eval_json(f"|*<{vid}>")
        return result if isinstance(result, dict) else {}

    async def fetch_type_defs(self, expr: str) -> list[tuple[str, str]]:
        """Run a .>>type>- query; returns (vid, definition) pairs for every type
        registered with an instset — including deeply nested ones missed by /+/.

        Each item in the server response is a string of the form:
            "TypeDefinition@/path/to/vid"
        We split on the LAST '@' followed by '/' to extract (vid, definition).
        """
        result = await self.eval_json(expr)
        if not isinstance(result, list):
            return []
        pairs: list[tuple[str, str]] = []
        for item in result:
            s = str(item)
            # Find the last @/ — that marks the start of the VID
            at = s.rfind("@/")
            if at >= 0:
                pairs.append((s[at + 1:], s[:at]))
        return pairs

    async def fetch_docq(self, uri: str) -> dict:
        """Fetch ?docq documentation for a URI; returns a dict.

        Uses the short-timeout json_docq connection so problematic URIs (those
        containing mtron special chars like # or ^) fail fast.
        """
        sep    = "&" if "?" in uri else "?"
        result = await self.json_docq.eval(f"*<{uri}{sep}docq>")
        return result if isinstance(result, dict) else {}
