"""
Tool handlers for the metatron plugin.

Uses a module-level persistent WebSocket client — the connection
survives across tool invocations within a session.
"""

import json
import logging

from .mtron_ws_client import mtronWebSocketClient

logger = logging.getLogger(__name__)

# Persistent connection — created once, reused for all eval calls
_client: mtronWebSocketClient | None = None


def _get_client() -> mtronWebSocketClient:
    """Return the module-level client, connecting on first use."""
    global _client
    if _client is None:
        _client = mtronWebSocketClient()
    return _client


def shutdown_client() -> None:
    """Close the persistent connection. Call on session end."""
    global _client
    if _client:
        _client.close()
        _client = None


# ------------------------------------------------------------------
# Tool handler
# ------------------------------------------------------------------


def handle_eval(params: dict, **kwargs) -> str:
    """Evaluate a mtron expression on the running Metatron instance."""
    del kwargs
    expr = params.get("expr", "")

    if not expr.strip():
        return json.dumps({"success": False, "error": "expr must not be empty"})

    try:
        client = _get_client()
        result = client.eval(expr)
        return json.dumps({"success": True, "result": result})
    except Exception as exc:
        # Drop the cached client so the next call reconnects fresh —
        # a stale socket (e.g. after VM restart) must not poison future evals.
        shutdown_client()
        logger.exception("eval failed for expr=%r", expr)
        return json.dumps({"success": False, "error": str(exc)})
