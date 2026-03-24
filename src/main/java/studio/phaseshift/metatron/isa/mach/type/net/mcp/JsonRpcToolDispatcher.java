/*
 * Metatron: A Distributed Computing Language and Virtual Machine
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

package studio.phaseshift.metatron.isa.mach.type.net.mcp;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.modelcontextprotocol.spec.McpSchema;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjSimpleJSONSerializer;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * Custom JSON-RPC 2.0 tool dispatcher for MCP.
 *
 * This dispatcher works around a known bug in MCP Java SDK 1.1.0 where tool handlers
 * are not invoked. It intercepts "tools/call" requests, manually invokes the registered
 * handlers, and constructs proper JSON-RPC responses.
 *
 * Uses metatron's ObjSimpleJSONSerializer for JSON parsing, providing a lightweight
 * alternative to the SDK's internal routing mechanism.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class JsonRpcToolDispatcher {

    private static final GraphittyLogger LOG = Graphitty.log(JsonRpcToolDispatcher.class);
    private static final ObjSimpleJSONSerializer JSON_SERIALIZER = ObjSimpleJSONSerializer.single();

    /**
     * Tool handler function that takes arguments and returns a CallToolResult.
     */
    @FunctionalInterface
    public interface ToolHandler {
        McpSchema.CallToolResult handle(Map<String, Object> arguments) throws Exception;
    }

    private final Map<String, ToolHandler> toolHandlers = new ConcurrentHashMap<>();
    private final Map<String, McpSchema.Tool> toolDefinitions = new ConcurrentHashMap<>();

    public JsonRpcToolDispatcher() {
        LOG.info("JSON-RPC tool dispatcher initialized");
    }

    /**
     * Register a tool with its handler.
     *
     * @param tool The tool definition (name, description, schema)
     * @param handler The handler function to invoke when the tool is called
     */
    public void registerTool(final McpSchema.Tool tool, final ToolHandler handler) {
        toolHandlers.put(tool.name(), handler);
        toolDefinitions.put(tool.name(), tool);
        LOG.info("Registered tool: %s", tool.name());
    }

    /**
     * Get all registered tool definitions (for tools/list responses).
     */
    public List<McpSchema.Tool> getTools() {
        return List.copyOf(toolDefinitions.values());
    }

    /**
     * Check if a JSON-RPC message is a tools/call request.
     *
     * @param message The raw JSON message string
     * @return true if this is a tools/call request
     */
    public boolean isToolCallRequest(final String message) {
        try {
            final Obj parsed = JSON_SERIALIZER.parse(message);
            if (parsed.isRec()) {
                final Obj method = parsed.recValue().get(uri("method"));
                return method != null && "tools/call".equals(method.toString());
            }
        } catch (final Exception e) {
            LOG.debug("Failed to check if message is tool call: %s", e.getMessage());
        }
        return false;
    }

    /**
     * Handle a tools/call request and return a JSON-RPC response.
     *
     * @param message The raw JSON-RPC request message
     * @return The JSON-RPC response as a JSON string
     */
    public String handleToolCall(final String message) {
        try {
            LOG.debug("Handling tool call request: %s", message);

            // Parse the JSON-RPC request using ObjSimpleJSONSerializer
            final Obj requestObj = JSON_SERIALIZER.parse(message);

            if (!requestObj.isRec()) {
                return createErrorResponse(null, -32600, "Invalid Request", "Request must be a JSON object");
            }

            // Extract request fields
            final Map<Obj, Obj> requestMap = requestObj.recValue();
            final Obj idObj = requestMap.get(uri("id"));
            final Obj paramsObj = requestMap.get(uri("params"));

            // Extract request ID (required for response)
            final Object requestId = idObj != null ? extractJsonValue(idObj) : null;

            if (paramsObj == null || !paramsObj.isRec()) {
                return createErrorResponse(requestId, -32602, "Invalid params", "params must be an object");
            }

            // Extract tool name and arguments from params
            final Map<Obj, Obj> paramsMap = paramsObj.recValue();
            final Obj nameObj = paramsMap.get(uri("name"));
            final Obj argumentsObj = paramsMap.get(uri("arguments"));

            if (nameObj == null) {
                return createErrorResponse(requestId, -32602, "Invalid params", "Missing 'name' field");
            }

            final String toolName = nameObj.toString();
            LOG.info("Dispatching tool call: %s", toolName);

            // Get the tool handler
            final ToolHandler handler = toolHandlers.get(toolName);
            if (handler == null) {
                return createErrorResponse(requestId, -32601, "Method not found",
                    "Tool '" + toolName + "' not found");
            }

            // Extract arguments as a Map
            final Map<String, Object> arguments = new HashMap<>();
            if (argumentsObj != null && argumentsObj.isRec()) {
                for (final Map.Entry<Obj, Obj> entry : argumentsObj.recValue().entrySet()) {
                    arguments.put(entry.getKey().toString(), extractJsonValue(entry.getValue()));
                }
            }

            LOG.debug("Invoking tool handler for: %s with arguments: %s", toolName, arguments);

            // Invoke the handler
            final McpSchema.CallToolResult result = handler.handle(arguments);

            // Create success response
            return createSuccessResponse(requestId, result);

        } catch (final Exception e) {
            LOG.error("Error handling tool call: %s", e.getMessage());
            return createErrorResponse(null, -32603, "Internal error", e.getMessage());
        }
    }

    /**
     * Extract a Java value from a metatron Obj for JSON serialization.
     */
    private Object extractJsonValue(final Obj obj) {
        if (obj.isNoObj()) {
            return null;
        } else if (obj.isBool()) {
            return obj.boolValue();
        } else if (obj.isInt()) {
            return obj.intValue();
        } else if (obj.isReal()) {
            return obj.realValue();
        } else if (obj.isStr()) {
            return obj.strValue();
        } else if (obj.isUri()) {
            return obj.uriValue().toString();
        } else if (obj.isLst()) {
            final java.util.List<Object> list = new java.util.ArrayList<>();
            for (final Obj item : obj.<Iterable<Obj>>jvm()) {
                list.add(extractJsonValue(item));
            }
            return list;
        } else if (obj.isRec()) {
            final Map<String, Object> map = new HashMap<>();
            for (final Map.Entry<Obj, Obj> entry : obj.recValue().entrySet()) {
                map.put(entry.getKey().toString(), extractJsonValue(entry.getValue()));
            }
            return map;
        } else {
            return obj.toString();
        }
    }

    /**
     * Create a JSON-RPC success response.
     */
    private String createSuccessResponse(final Object id, final McpSchema.CallToolResult result) {
        try {
            final Map<String, Object> response = new HashMap<>();
            response.put("jsonrpc", "2.0");
            response.put("id", id);

            // Convert CallToolResult to a map
            final Map<String, Object> resultMap = new HashMap<>();

            // Add content array
            final java.util.List<Map<String, Object>> contentList = new java.util.ArrayList<>();
            for (final McpSchema.Content content : result.content()) {
                final Map<String, Object> contentItem = new HashMap<>();
                if (content instanceof McpSchema.TextContent) {
                    contentItem.put("type", "text");
                    contentItem.put("text", ((McpSchema.TextContent) content).text());
                } else if (content instanceof McpSchema.ImageContent) {
                    contentItem.put("type", "image");
                    contentItem.put("data", ((McpSchema.ImageContent) content).data());
                    contentItem.put("mimeType", ((McpSchema.ImageContent) content).mimeType());
                } else if (content instanceof McpSchema.EmbeddedResource) {
                    contentItem.put("type", "resource");
                    final McpSchema.EmbeddedResource resource = (McpSchema.EmbeddedResource) content;
                    // Add resource fields as needed
                }
                contentList.add(contentItem);
            }
            resultMap.put("content", contentList);

            // Add isError if present
            if (result.isError() != null) {
                resultMap.put("isError", result.isError());
            }

            response.put("result", resultMap);

            // Convert to JSON using ObjSimpleJSONSerializer
            final Obj responseObj = convertToObj(response);
            final JsonElement jsonElement = JSON_SERIALIZER.write(responseObj);
            final String jsonResponse = jsonElement.toString();

            LOG.debug("Created success response: %s", jsonResponse);
            return jsonResponse;

        } catch (final Exception e) {
            LOG.error("Failed to create success response: %s", e.getMessage());
            return createErrorResponse(id, -32603, "Internal error",
                "Failed to serialize response: " + e.getMessage());
        }
    }

    /**
     * Create a JSON-RPC error response.
     */
    private String createErrorResponse(final Object id, final int code, final String message, final String data) {
        try {
            final Map<String, Object> response = new HashMap<>();
            response.put("jsonrpc", "2.0");
            response.put("id", id);

            final Map<String, Object> error = new HashMap<>();
            error.put("code", code);
            error.put("message", message);
            if (data != null) {
                error.put("data", data);
            }
            response.put("error", error);

            // Convert to JSON using ObjSimpleJSONSerializer
            final Obj responseObj = convertToObj(response);
            final JsonElement jsonElement = JSON_SERIALIZER.write(responseObj);
            return jsonElement.toString();

        } catch (final Exception e) {
            // Fallback to manual JSON construction if serialization fails
            LOG.error("Failed to create error response: %s", e.getMessage());
            return String.format(
                "{\"jsonrpc\":\"2.0\",\"id\":%s,\"error\":{\"code\":%d,\"message\":\"%s\"}}",
                id != null ? "\"" + id + "\"" : "null",
                code,
                message.replace("\"", "\\\"")
            );
        }
    }

    /**
     * Convert a Java Map/List structure to a metatron Obj for serialization.
     */
    private Obj convertToObj(final Object value) {
        if (value == null) {
            return studio.phaseshift.metatron.isa.m.type.NoObj.noobj();
        } else if (value instanceof Boolean) {
            return studio.phaseshift.metatron.isa.m.type.impl.MBool.bool((Boolean) value);
        } else if (value instanceof Integer) {
            return studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt((Integer) value);
        } else if (value instanceof Long) {
            return studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt((Long) value);
        } else if (value instanceof Double) {
            return studio.phaseshift.metatron.isa.m.type.impl.MReal.real((Double) value);
        } else if (value instanceof String) {
            return studio.phaseshift.metatron.isa.m.type.impl.MStr.str((String) value);
        } else if (value instanceof java.util.List) {
            final java.util.List<Obj> list = new java.util.ArrayList<>();
            for (final Object item : (java.util.List<?>) value) {
                list.add(convertToObj(item));
            }
            return studio.phaseshift.metatron.isa.m.type.impl.MLst.lst(list);
        } else if (value instanceof Map) {
            final Map<Obj, Obj> map = new java.util.LinkedHashMap<>();
            for (final Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                map.put(
                    uri(entry.getKey().toString()),
                    convertToObj(entry.getValue())
                );
            }
            return studio.phaseshift.metatron.isa.m.type.impl.MRec.rec(map);
        } else {
            return studio.phaseshift.metatron.isa.m.type.impl.MStr.str(value.toString());
        }
    }
}
