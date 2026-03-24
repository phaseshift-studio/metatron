al# MCP Tool Annotation System

## Status: ✅ IMPLEMENTED

**Date**: March 25, 2026
**Purpose**: Declarative annotation-based MCP tool creation

## Overview

Created an annotation framework for defining MCP tools without boilerplate code. Instead of manually writing JSON schemas and handler functions, developers can now use annotations to declaratively define tools.

## Components Created

### Annotations

1. **`@McpTool`** - Marks a class as an MCP tool
   - `name` - Tool name
   - `description` - Tool description
   - `category` - Optional category for grouping

2. **`@McpParameter`** - Marks a field as a tool parameter
   - `name` - Parameter name (defaults to field name)
   - `description` - Parameter description
   - `required` - Whether parameter is required
   - `defaultValue` - Default value as string

3. **`@McpHandler`** - Marks a method as the tool handler
   - Must return `McpSchema.CallToolResult`
   - Parameters are auto-injected before invocation

### Registry

**`McpToolRegistry`** - Reflection-based tool registration
- Scans classes for annotations
- Auto-generates JSON schemas from `@McpParameter` fields
- Creates tool definitions
- Wires up handlers with parameter injection
- Handles type conversion (String, int, boolean, etc.)

### Examples

1. **`AnnotatedEvaluateCodeTool`** - Simple single-parameter tool
2. **`AnnotatedQueryTool`** - Complex multi-parameter tool with defaults

## Usage

### Before (Manual Approach)

```java
public class EvaluateCodeTool {
    public static String getName() { return "evaluate_code"; }

    public static String getJsonSchema() {
        return """
            {
              "type": "object",
              "properties": {
                "code": { "type": "string", "description": "..." }
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
                // handler logic
            }
        );
    }
}
```

### After (Annotation Approach)

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
        // handler logic - code is already injected!
    }
}
```

### Registration

```java
// In MetatronMcpServer.registerToolsWithDispatcher()
final Tuple.Pair<McpSchema.Tool, JsonRpcToolDispatcher.ToolHandler> tool =
    McpToolRegistry.register(MyTool.class);
toolDispatcher.registerTool(tool.get0(), tool.get1());
```

## Benefits

✅ **Less Boilerplate** - No manual JSON schema strings
✅ **Type Safety** - Parameters are strongly typed
✅ **Auto-Validation** - Required parameters checked automatically
✅ **Self-Documenting** - Annotations serve as documentation
✅ **Easier Testing** - Can instantiate and test tools directly
✅ **Refactoring-Friendly** - Rename fields and annotations update automatically

## How It Works

1. **Schema Generation**:
   - Scans `@McpParameter` fields
   - Determines JSON type from Java type
   - Builds JSON schema with properties and required fields

2. **Parameter Injection**:
   - Creates instance of tool class
   - Extracts arguments from tool call
   - Converts to field types
   - Injects into annotated fields

3. **Handler Invocation**:
   - Calls `@McpHandler` method
   - Returns `McpSchema.CallToolResult`

4. **Type Conversion**:
   - String → String
   - int/Integer/long/Long → integer
   - double/Double/float/Float → number
   - boolean/Boolean → boolean

## Files Created

### Annotations
- `src/main/java/studio/phaseshift/metatron/isa/mach/type/net/mcp/annotation/McpTool.java`
- `src/main/java/studio/phaseshift/metatron/isa/mach/type/net/mcp/annotation/McpParameter.java`
- `src/main/java/studio/phaseshift/metatron/isa/mach/type/net/mcp/annotation/McpHandler.java`

### Registry
- `src/main/java/studio/phaseshift/metatron/isa/mach/type/net/mcp/annotation/McpToolRegistry.java`

### Examples
- `src/main/java/studio/phaseshift/metatron/isa/mach/type/net/mcp/tool/annotated/AnnotatedEvaluateCodeTool.java`
- `src/main/java/studio/phaseshift/metatron/isa/mach/type/net/mcp/tool/annotated/AnnotatedQueryTool.java`
- `src/main/java/studio/phaseshift/metatron/isa/mach/type/net/mcp/tool/annotated/README.md`

## Compilation Status

✅ All files compile without errors

## Future Enhancements

Potential additions:
- `@McpToolScan` - Auto-discover tools in a package
- `@McpValidation` - Custom parameter validation
- `@McpExample` - Example values for documentation
- Support for complex types (arrays, objects, nested structures)
- Async handler support with `Mono<CallToolResult>`
- Conditional parameters (show param B only if param A has certain value)

## Migration Strategy

Both approaches can coexist:
1. Keep existing manual tools working
2. New tools can use annotations
3. Gradually migrate existing tools as needed
4. No breaking changes to existing code

## Testing

To test the annotation system:
1. Register `AnnotatedEvaluateCodeTool` in `MetatronMcpServer`
2. Call via MCP: `{"method":"tools/call","params":{"name":"evaluate_code_annotated","arguments":{"code":"5.plus(3)"}}}`
3. Should return: `{"result":{"content":[{"type":"text","text":"8"}]}}`

## Comparison to Other Frameworks

Similar to:
- **Spring MVC** `@Controller`, `@RequestMapping`, `@RequestParam`
- **JAX-RS** `@Path`, `@GET`, `@QueryParam`
- **mcp-annotated-java-sdk** (but simpler and Metatron-specific)

Our approach is:
- Lighter weight (no Spring dependency)
- Tailored to Metatron's needs
- Works with our custom JSON-RPC dispatcher
- Integrates seamlessly with existing tool pattern

## Conclusion

The annotation system provides a clean, declarative way to create MCP tools while maintaining compatibility with the existing manual approach. It reduces boilerplate, improves type safety, and makes tools easier to write and maintain.
