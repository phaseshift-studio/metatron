# MCP Tool Annotation System

## Overview

The annotation-based MCP tool system provides a declarative way to create MCP tools without boilerplate code. Instead of manually creating JSON schemas and handler functions, you simply annotate your tool class and the framework handles the rest.

## Quick Start

### 1. Create a Tool Class

```java
@McpTool(
    name = "my_tool",
    description = "What my tool does",
    category = "optional_category"
)
public class MyTool {

    @McpParameter(
        name = "input",
        description = "Input parameter",
        required = true
    )
    private String input;

    @McpHandler
    public McpSchema.CallToolResult execute() {
        // Your tool logic here
        return McpSchema.CallToolResult.builder()
            .content(List.of(new McpSchema.TextContent("Result")))
            .isError(false)
            .build();
    }
}
```

### 2. Register the Tool

```java
// In MetatronMcpServer.registerToolsWithDispatcher()
final Tuple.Pair<McpSchema.Tool, JsonRpcToolDispatcher.ToolHandler> myTool =
    McpToolRegistry.register(MyTool.class);
toolDispatcher.registerTool(myTool.get0(), myTool.get1());
```

## Annotations

### @McpTool

Marks a class as an MCP tool.

**Attributes:**
- `name` (required) - Tool name (e.g., "evaluate_code")
- `description` (required) - What the tool does
- `category` (optional) - Category for grouping tools

**Example:**
```java
@McpTool(
    name = "evaluate_code",
    description = "Evaluate metatron code and return the result.",
    category = "execution"
)
public class EvaluateCodeTool { ... }
```

### @McpParameter

Marks a field as a tool parameter. The field will be automatically populated from the tool call arguments.

**Attributes:**
- `name` (optional) - Parameter name in JSON (defaults to field name)
- `description` (required) - Parameter description
- `required` (optional) - Whether parameter is required (default: true)
- `defaultValue` (optional) - Default value as string

**Supported Types:**
- `String`
- `int`, `Integer`, `long`, `Long`
- `double`, `Double`, `float`, `Float`
- `boolean`, `Boolean`

**Example:**
```java
@McpParameter(
    name = "code",
    description = "mtron code to evaluate",
    required = true
)
private String code;

@McpParameter(
    name = "limit",
    description = "Maximum results",
    required = false,
    defaultValue = "100"
)
private int limit;
```

### @McpHandler

Marks a method as the tool's handler. The method must return `McpSchema.CallToolResult`.

**Example:**
```java
@McpHandler
public McpSchema.CallToolResult execute() {
    // mTool logic
    return McpSchema.CallToolResult.builder()
        .content(List.of(new McpSchema.TextContent(result)))
        .isError(false)
        .build();
}
```

## How It Works

1. **Schema Generation**: The framework scans `@McpParameter` fields and auto-generates the JSON schema
2. **Parameter Injection**: When a tool is called, arguments are automatically injected into the annotated fields
3. **Handler Invocation**: The `@McpHandler` method is called with all parameters populated
4. **Type Conversion**: Arguments are automatically converted to the field's type

## Comparison: Manual vs Annotated

### Manual Approach (Current)

```java
public class EvaluateCodeTool {
    public static String getName() { return "evaluate_code"; }
    public static String getDescription() { return "..."; }
    public static String getJsonSchema() {
        return """
            {
              "type": "object",
              "properties": {
                "code": {
                  "type": "string",
                  "description": "..."
                }
              },
              "required": ["code"]
            }
            """;
    }

    public static Tuple.Pair<McpSchema.Tool, JsonRpcToolDispatcher.ToolHandler> create() {
        return Tuple.Pair.with(
            McpSchema.Tool.builder()
                .name(getName())
                .description(getDescription())
                .inputSchema(McpJsonDefaults.getMapper(), getJsonSchema())
                .build(),
            args -> {
                String code = args.get("code").toString();
                // ... handler logic
            }
        );
    }
}
```

### Annotated Approach (New)

```java
@McpTool(
    name = "evaluate_code",
    description = "Evaluate metatron code and return the result."
)
public class EvaluateCodeTool {

    @McpParameter(
        name = "code",
        description = "mtron code to evaluate",
        required = true
    )
    private String code;

    @McpHandler
    public McpSchema.CallToolResult execute() {
        // ... handler logic (code is already injected!)
    }
}
```

## Benefits

✅ **Less Boilerplate** - No manual JSON schema strings
✅ **Type Safety** - Parameters are strongly typed
✅ **Auto-Validation** - Required parameters checked automatically
✅ **Self-Documenting** - Annotations serve as documentation
✅ **Easier Testing** - Can instantiate and test tools directly
✅ **Refactoring-Friendly** - Rename fields and annotations update automatically

## Examples

See:
- `AnnotatedEvaluateCodeTool.java` - Simple single-parameter tool
- `AnnotatedQueryTool.java` - Complex multi-parameter tool with defaults

## Future Enhancements

Potential additions:
- `@McpToolScan` - Auto-discover tools in a package
- `@McpValidation` - Custom parameter validation
- `@McpExample` - Example values for documentation
- Support for complex types (arrays, objects)
- Async handler support with `Mono<CallToolResult>`

## Migration Guide

To migrate existing tools:

1. Add `@McpTool` annotation to the class
2. Convert `getJsonSchema()` parameters to `@McpParameter` fields
3. Move handler logic to `@McpHandler` method
4. Update registration to use `McpToolRegistry.register()`
5. Remove old static methods (`getName()`, `getDescription()`, `getJsonSchema()`, `create()`)

Both approaches can coexist - you can gradually migrate tools as needed.
