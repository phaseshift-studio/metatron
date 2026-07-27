"""
Tool schemas for the metatron plugin.

Each schema is a JSON Schema object that Hermes passes to the LLM
so it knows what tools are available and how to call them.
"""

EVAL_SCHEMA = {
    "name": "eval",
    "description": (
        "Evaluate a mtron expression on the running Metatron instance and return the result. "
        "Use this to query data, explore the metatron space graph, or execute mtron code. "
        "The expression is sent over WebSocket and the result is returned as text."
    ),
    "parameters": {
        "type": "object",
        "properties": {
            "expr": {
                "type": "string",
                "description": "A mtron expression to evaluate (e.g. '/sys/space/', '/m/str/\"hello\"')",
            }
        },
        "required": ["expr"],
    },
}
