# MCP Tool Registration - Raw Rec Approach

## Overview

This document demonstrates how to create and register MCP tools as raw Metatron Recs, without needing Java annotations or compilation.

## The Raw Approach

Tools are simply Recs with the following structure:

```java
rec(
  uri(Tokens.NAME), str("tool_name"),
  uri(Tokens.DESC), str("tool description"),
  uri("args"), rec(...),  // JSON schema as rec (optional)
  uri("eval"), instObj    // The Inst to execute
)
```

## Example: evaluate_code Tool

Here's how the `evaluate_code` tool is created in `MetatronMcpServer.java`:

```java
// 1. Create the eval inst - the actual logic
final Inst evalInst = instC(
    INST_TID,
    lst(T(REC_TID)),  // Takes one argument: a Rec
    (lhs, inst) -> {
        try {
            // inst.arg(0) is the args Rec passed to the tool
            final Obj argsRec = inst.arg(0);

            // Extract the 'code' parameter
            final Obj codeObj = argsRec.recValue().get(uri(Tokens.CODE));
            if (codeObj == null || !codeObj.isStr()) {
                return str("Error: 'code' parameter is required and must be a string");
            }

            final String code = codeObj.strValue();

            // Parse and execute the code
            final Obj result = parser.parse(code);

            return result;

        } catch (final Exception e) {
            return str("Error: " + e.getMessage());
        }
    }
);

// 2. Create args schema (maps to JSON Schema for MCP protocol)
final Obj argsSchema = rec(
    uri(Tokens.TYPE), str("object"),
    uri("properties"), rec(
        uri(Tokens.CODE), rec(
            uri(Tokens.TYPE), str("string"),
            uri(Tokens.DESC), str("The metatron code to evaluate")
        )
    ),
    uri("required"), str("[\"code\"]")
);

// 3. Create the tool Rec using Tokens vocabulary
final Obj evaluateCodeTool = rec(
    uri(Tokens.NAME), str("evaluate_code"),
    uri(Tokens.DESC), str("Evaluate metatron code and return the result."),
    uri("args"), argsSchema,
    uri("eval"), evalInst
);

// 4. Register it
mcpServer.register(evaluateCodeTool);
```

## Key Concepts

### Using Tokens Vocabulary

The `Tokens` class provides common keys to ensure consistency:
- `Tokens.NAME` - "name"
- `Tokens.DESC` - "desc"
- `Tokens.CODE` - "code"
- `Tokens.TYPE` - "type"
- `Tokens.TOOL` - "tool"

### Creating Instructions with instC

`instC` creates an instruction with:
1. **tid** - Type ID (e.g., `INST_TID`)
2. **args** - Argument specification as a Poly (e.g., `lst(T(REC_TID))`)
3. **function** - BiFunction<Obj, Inst, Obj> where:
   - `lhs` - Left-hand side (the object the inst is applied to)
   - `inst` - The instruction itself (use `inst.arg(0)`, `inst.arg(1)`, etc.)

### The register() Method

The `register(Obj toolRec)` method:
1. Extracts `name`, `desc`, `args`, and `eval` from the Rec
2. Converts the args schema to JSON Schema for MCP
3. Creates an MCP tool definition
4. Wraps the `eval` Inst in a handler that:
   - Converts JSON args to Metatron Objs
   - Executes the Inst
   - Converts the result back to JSON

## Future: Dynamic Tool Creation

In the future, tools can be created entirely in Metatron without Java:

```metatron
// Create a tool in Metatron space
rec(
  name: "my_custom_tool",
  desc: "Does something cool",
  args: rec(
    type: "object",
    properties: rec(
      input: rec(type: "string", desc: "Input value")
    )
  ),
  eval: [code: "args>>input.ucase()"]
) => /m/sys/server/mcp/tool/my_custom_tool

// The MCP server queries */m/sys/server/mcp/tool/+ to discover all tools
```

## Benefits of This Approach

1. **Everything is an Obj** - Tools are first-class objects in Metatron's referential space
2. **No Java compilation needed** - Tools can be created dynamically
3. **Consistent with Metatron philosophy** - Uses existing patterns (Recs, Insts, Tokens)
4. **Flexible** - Tools can be stored, queried, and manipulated like any other Obj
5. **Protocol agnostic** - The same tool Rec can be exposed via different protocols

## Next Steps

1. Store tools in `/m/sys/server/mcp/tool` space
2. Query `*/m/sys/server/mcp/tool/+` to discover tools at runtime
3. Add `@McpTool` annotation as convenience (following `@JREService` pattern)
4. Enable ServiceLoader discovery for Java-based tools
