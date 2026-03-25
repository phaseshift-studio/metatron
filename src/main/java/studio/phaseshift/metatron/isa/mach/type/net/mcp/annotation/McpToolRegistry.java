/*
 * metatron: a distributed virtual machine and language
 *  Copyright (C) 2025- PhaseShift Studio, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.isa.mach.type.net.mcp.annotation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpSchema;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjSimpleJSONSerializer;
import studio.phaseshift.metatron.isa.mach.type.net.mcp.JsonRpcToolDispatcher;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.Tuple;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Registry for MCP tools using annotation-based discovery.
 * <p>
 * Scans for classes annotated with @McpTool and automatically:
 * - Generates JSON schemas from @McpParameter annotations
 * - Creates tool definitions
 * - Wires up handlers with parameter injection
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class McpToolRegistry {

    private static final GraphittyLogger LOG = Graphitty.log(McpToolRegistry.class);
    private static final ObjSimpleJSONSerializer JSON_SERIALIZER = ObjSimpleJSONSerializer.single();

    /**
     * Scan a package for classes annotated with @McpTool and register them all.
     *
     * @param packageName The package to scan (e.g., "studio.phaseshift.metatron.isa.mach.type.net.mcp.tool.annotated")
     * @return List of registered tool pairs
     */
    public static List<Tuple.Pair<McpSchema.Tool, JsonRpcToolDispatcher.ToolHandler>> scanPackage(final String packageName) {
        final List<Tuple.Pair<McpSchema.Tool, JsonRpcToolDispatcher.ToolHandler>> tools = new ArrayList<>();

        try {
            final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            final String path = packageName.replace('.', '/');
            final URL resource = classLoader.getResource(path);

            if (resource == null) {
                LOG.warn("package not found: %s", packageName);
                return tools;
            }

            final File directory = new File(resource.getFile());
            if (!directory.exists()) {
                LOG.warn("package directory does not exist: %s", packageName);
                return tools;
            }

            // Scan all .class files in the directory
            final File[] files = directory.listFiles((dir, name) -> name.endsWith(".class"));
            if (files == null) {
                return tools;
            }

            for (final File file : files) {
                final String className = packageName + '.' + file.getName().substring(0, file.getName().length() - 6);
                try {
                    final Class<?> clazz = Class.forName(className);
                    if (clazz.isAnnotationPresent(McpTool.class)) {
                        LOG.info("found @McpTool annotated class: %s", className);
                        final Tuple.Pair<McpSchema.Tool, JsonRpcToolDispatcher.ToolHandler> tool = register(clazz);
                        tools.add(tool);
                    }
                } catch (final ClassNotFoundException e) {
                    LOG.warn("could not load class: %s", className);
                }
            }

            LOG.info("scanned package %s and found %d tools", packageName, tools.size());

        } catch (final Exception e) {
            LOG.error("error scanning package %s: %s", packageName, e.getMessage());
        }

        return tools;
    }

    /**
     * Register a tool class annotated with @McpTool.
     *
     * @param toolClass The class annotated with @McpTool
     * @return Tuple of Tool definition and handler
     */
    public static Tuple.Pair<McpSchema.Tool, JsonRpcToolDispatcher.ToolHandler> register(final Class<?> toolClass) {
        final McpTool toolAnnotation = toolClass.getAnnotation(McpTool.class);
        if (toolAnnotation == null) {
            throw new IllegalArgumentException("Class " + toolClass.getName() + " is not annotated with @McpTool");
        }

        // Find the handler method
        Method handlerMethod = null;
        for (final Method method : toolClass.getDeclaredMethods()) {
            if (method.isAnnotationPresent(McpHandler.class)) {
                handlerMethod = method;
                break;
            }
        }

        if (handlerMethod == null) {
            throw new IllegalArgumentException("Class " + toolClass.getName() + " has no method annotated with @McpHandler");
        }

        // Collect parameter fields
        final List<Field> parameterFields = new ArrayList<>();
        for (final Field field : toolClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(McpParameter.class)) {
                field.setAccessible(true);
                parameterFields.add(field);
            }
        }

        // Generate JSON schema
        final String jsonSchema = generateJsonSchema(parameterFields);

        // Create tool definition
        final McpSchema.Tool tool = McpSchema.Tool.builder()
                .name(toolAnnotation.name())
                .description(toolAnnotation.description())
                .inputSchema(McpJsonDefaults.getMapper(), jsonSchema)
                .build();

        // Create handler with parameter injection
        final Method finalHandlerMethod = handlerMethod;
        finalHandlerMethod.setAccessible(true);

        final JsonRpcToolDispatcher.ToolHandler handler = args -> {
            try {
                // Create instance of tool class
                final Object toolInstance = toolClass.getDeclaredConstructor().newInstance();

                // Inject parameters
                for (final Field field : parameterFields) {
                    final McpParameter param = field.getAnnotation(McpParameter.class);
                    final String paramName = param.name().isEmpty() ? field.getName() : param.name();

                    if (args.containsKey(paramName)) {
                        final Object value = convertValue(args.get(paramName), field.getType());
                        field.set(toolInstance, value);
                    } else if (param.required()) {
                        throw new IllegalArgumentException("Required parameter '" + paramName + "' not provided");
                    } else if (!param.defaultValue().isEmpty()) {
                        final Object defaultVal = convertValue(param.defaultValue(), field.getType());
                        field.set(toolInstance, defaultVal);
                    }
                }

                // Invoke handler method
                final Object result = finalHandlerMethod.invoke(toolInstance);

                // Convert result to CallToolResult
                if (result instanceof McpSchema.CallToolResult) {
                    // Already a CallToolResult, use directly
                    return (McpSchema.CallToolResult) result;
                } else if (result instanceof Obj) {
                    // Convert Obj to CallToolResult
                    return convertObjToResult((Obj) result);
                } else {
                    throw new IllegalStateException("Handler must return Obj or McpSchema.CallToolResult");
                }

            } catch (final Exception e) {
                LOG.error("Error executing tool %s: %s", toolAnnotation.name(), e.getMessage());
                return McpSchema.CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent("Error: " + e.getMessage())))
                        .isError(true)
                        .build();
            }
        };

        LOG.info("Registered annotated tool: %s", toolAnnotation.name());
        return Tuple.Pair.with(tool, handler);
    }

    /**
     * Generate JSON schema from parameter fields.
     */
    private static String generateJsonSchema(final List<Field> parameterFields) {
        final JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        final JsonObject properties = new JsonObject();
        final List<String> required = new ArrayList<>();

        for (final Field field : parameterFields) {
            final McpParameter param = field.getAnnotation(McpParameter.class);
            final String paramName = param.name().isEmpty() ? field.getName() : param.name();

            final JsonObject property = new JsonObject();
            property.addProperty("type", getJsonType(field.getType()));
            property.addProperty("description", param.description());

            properties.add(paramName, property);

            if (param.required()) {
                required.add(paramName);
            }
        }

        schema.add("properties", properties);

        if (!required.isEmpty()) {
            final com.google.gson.JsonArray requiredArray = new com.google.gson.JsonArray();
            required.forEach(requiredArray::add);
            schema.add("required", requiredArray);
        }

        return schema.toString();
    }

    /**
     * Get JSON type string for Java type.
     */
    private static String getJsonType(final Class<?> type) {
        if (type == String.class) {
            return "string";
        } else if (type == int.class || type == Integer.class || type == long.class || type == Long.class) {
            return "integer";
        } else if (type == double.class || type == Double.class || type == float.class || type == Float.class) {
            return "number";
        } else if (type == boolean.class || type == Boolean.class) {
            return "boolean";
        } else if (type.isArray() || List.class.isAssignableFrom(type)) {
            return "array";
        } else {
            return "object";
        }
    }

    /**
     * Convert an Obj to a CallToolResult.
     * - If Obj is a Fail, sets isError(true) and extracts error message
     * - Otherwise, converts to JSON using ObjSimpleJSONSerializer
     */
    private static McpSchema.CallToolResult convertObjToResult(final Obj obj) {
        try {
            // Check if it's a Fail (error)
            final boolean isError = obj.getClass().getSimpleName().contains("Fail");

            if (isError) {
                // Extract error message from Fail
                final String errorMsg = obj.toString();
                return McpSchema.CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent(errorMsg)))
                        .isError(true)
                        .build();
            } else {
                // Convert Obj to JSON
                final JsonElement jsonElement = JSON_SERIALIZER.write(obj);
                final String jsonString = jsonElement.toString();

                return McpSchema.CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent(jsonString)))
                        .isError(false)
                        .build();
            }
        } catch (final Exception e) {
            LOG.error("Error converting Obj to result: %s", e.getMessage());
            return McpSchema.CallToolResult.builder()
                    .content(List.of(new McpSchema.TextContent("Error: " + e.getMessage())))
                    .isError(true)
                    .build();
        }
    }

    /**
     * Convert argument value to target type.
     */
    private static Object convertValue(final Object value, final Class<?> targetType) {
        if (value == null) {
            return null;
        }

        final String stringValue = value.toString();

        if (targetType == String.class) {
            return stringValue;
        } else if (targetType == int.class || targetType == Integer.class) {
            return Integer.parseInt(stringValue);
        } else if (targetType == long.class || targetType == Long.class) {
            return Long.parseLong(stringValue);
        } else if (targetType == double.class || targetType == Double.class) {
            return Double.parseDouble(stringValue);
        } else if (targetType == float.class || targetType == Float.class) {
            return Float.parseFloat(stringValue);
        } else if (targetType == boolean.class || targetType == Boolean.class) {
            return Boolean.parseBoolean(stringValue);
        } else {
            return value;
        }
    }
}
