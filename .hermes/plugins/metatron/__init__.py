"""
Hermes plugin for Metatron — registers a single `eval` tool.

Connects to a running Metatron instance at ws://127.0.0.1:8555/mtron
and evaluates mtron expressions on demand.  The WebSocket connection
is persistent: it is opened on the first tool call and survives
across invocations for the lifetime of the Hermes session.
"""

from .schemas import EVAL_SCHEMA
from .tools import handle_eval, shutdown_client


def register(ctx):
    """Register the eval tool and lifecycle hooks with Hermes."""
    ctx.register_tool(
        name="eval",
        toolset="metatron",
        schema=EVAL_SCHEMA,
        handler=handle_eval,
        description="Evaluate a mtron expression on a running Metatron instance via WebSocket",
    )

    # Lifecycle — clean up the persistent WebSocket when the session ends
    def on_session_end():
        shutdown_client()

    ctx.register_hook("on_session_end", on_session_end)

    # Audit hook — log every tool invocation for debugging
    def on_tool_call(tool_name: str, params: dict, result: str):
        print(f"[metatron] tool={tool_name} params={params} result_preview={result[:200]}")

    ctx.register_hook("post_tool_call", on_tool_call)
