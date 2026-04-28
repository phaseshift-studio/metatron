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
import asyncio
import websockets
import logging

logger = logging.getLogger(__name__)

class Mytron:
    """WebSocket client for communicating with a running metatron instance.

    Connects to the mtron_wsServer endpoint (ws://host:8555/mtron).  Each
    connection is a dedicated session instance of the mtron_ws type, so the
    server evaluates metatron expressions and returns clean metatron-syntax
    results (no %%% separators, no </m/type>:: prefixes for base types).
    """

    def __init__(self, host: str = "ws://localhost:8555/mtron", timeout: int = 30):
        self.host = host
        self.timeout = timeout
        self._ws = None

    async def connect(self):
        """Establish WebSocket connection."""
        print(f"[mytron] connecting to {self.host} ...")
        try:
            self._ws = await websockets.connect(self.host)
            print(f"[mytron] connected ok")
        except Exception as e:
            raise RuntimeError(f"[mytron] connection failed: {e}")

    async def close(self):
        """Close WebSocket connection."""
        if self._ws:
            await self._ws.close()
            logger.info("Connection closed")

    async def eval_hidden(self, expression: str, timeout: int = 120) -> None:
        """Evaluate a hidden expression, discarding the result.

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
            raw_str = raw if isinstance(raw, str) else raw.decode('utf-8')
            print(f"recv [hidden]: {len(raw_str)} chars")
        except (asyncio.TimeoutError, TimeoutError):
            print(f"[mytron] hidden eval timed out ({timeout}s): {expression!r} — reconnecting")
            await self._reconnect()
        except websockets.exceptions.ConnectionClosedError as e:
            print(f"[mytron] connection closed during hidden eval: {e} — reconnecting")
            await self._reconnect()

    async def _reconnect(self):
        """Close the current connection and open a fresh one."""
        try:
            if self._ws:
                await self._ws.close()
        except Exception:
            pass
        self._ws = None
        await self.connect()

    async def eval(self, expression: str) -> str:
        """Evaluate a metatron expression and return the formatted result."""
        if not self._ws:
            raise RuntimeError("Not connected. Call connect() first.")

        print(f"send: {expression}")
        await self._ws.send(expression)
        try:
            raw = await asyncio.wait_for(self._ws.recv(), timeout=self.timeout)
        except (asyncio.TimeoutError, TimeoutError):
            print(f"[mytron] recv timed out for: {expression!r} — reconnecting")
            await self._reconnect()
            return ""
        except websockets.exceptions.ConnectionClosedError as e:
            print(f"[mytron] connection closed during eval: {e} — reconnecting")
            await self._reconnect()
            return ""

        raw_str = (raw if isinstance(raw, str) else raw.decode('utf-8')).strip()
        print(f"recv: {raw_str}")

        # mtron_wsServer uses ObjmtronSerializer (APPLICATION_MTRON) — one response
        # per send, clean metatron syntax, no %%% separators, no type prefixes for
        # base types.  noobj means the expression produced no meaningful result.
        if raw_str == "" or raw_str == "noobj":
            return ""
        return f"==>{raw_str}"
