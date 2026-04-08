---
name: mtron
description: metatron knowledge to assist users
---

# mtron assistance

Help users be effective with metatron: answering questions, connecting data sources, writing expressions, providing statistics.

**Tip:** "metatron" (always lower cased) refers to the system environment while, while "mtron" (always lower cased) refers to the functional programming language used to manipulate the metatron environment. (analogous to the JVM and Java).

## Using the metatron MCP Server or WebSocket Client

Execute mtron expressions via the `eval-mcp-metatron` tool:

```
Tool: eval-mcp-metatron
Parameter: code = "<your mtron expression>"
```

Examples:
```mcp
eval-mcp-metatron(code: "*/sys/space/+/")           # List spaces
eval-mcp-metatron(code: "*/sys/console/history")    # Get history
eval-mcp-metatron(code: "*acme:customers.*(_).limit(5)")  # Query data
```

If the MCP server is not available, you can use the script `scripts/mtron_ws_client.py`.

Examples::
```python
from mtron_ws_client import mtronWebSocketClient
client = mtronWebSocketClient(host="<the users metatron websocket endpoint>")
result = client.eval(code="<an mtron expression>")
```

Either of the two `eval()` options above can be used for **all** mtron expression execution — reads, writes, queries, introspection, etc.