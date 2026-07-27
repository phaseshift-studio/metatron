"""
Persistent synchronous WebSocket client for Metatron mtron evaluation.

Holds a single long-lived connection to the metatron instance.
Reconnects automatically if the connection drops.

Protocol: send a raw mtron expression as a WebSocket text frame,
receive the mtron-serialized result as a text frame.
"""

import logging

import websocket

logger = logging.getLogger(__name__)

DEFAULT_HOST = "ws://127.0.0.1:8555/mtron"


class mtronWebSocketClient:
    """Persistent WebSocket client — one connection, many eval() calls."""

    def __init__(self, host: str = DEFAULT_HOST):
        self.host = host
        self._ws: websocket.WebSocket | None = None
        self._connect()

    # ------------------------------------------------------------------
    # Connection lifecycle
    # ------------------------------------------------------------------

    def _connect(self) -> None:
        """Open (or reopen) the WebSocket connection."""
        logger.info("connecting to %s", self.host)
        self._ws = websocket.create_connection(
            self.host,
            timeout=10,
            enable_multithread=True,
        )
        logger.info("connected to %s", self.host)

    def close(self) -> None:
        """Close the connection. Safe to call multiple times."""
        if self._ws:
            self._ws.close()
            self._ws = None
            logger.info("connection closed")

    # ------------------------------------------------------------------
    # Eval
    # ------------------------------------------------------------------

    def eval(self, code: str) -> str:
        """
        Evaluate a raw mtron expression. Reconnects once if the socket is dead.

        The expression is sent as-is — no doc() wrapper needed.
        The server returns the mtron-serialized result as a text frame.
        """
        logger.info("eval: %s", code)

        try:
            self._ws.send(code)
            raw = self._ws.recv()
        except websocket.WebSocketException:
            logger.warning("connection lost, reconnecting…")
            self._connect()
            self._ws.send(code)
            raw = self._ws.recv()

        return raw.strip()
