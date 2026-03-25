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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.modelcontextprotocol.spec.McpSchema;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Custom JSON-RPC 2.0 tool dispatcher for MCP.
 * <p>
 * This dispatcher works around a known bug in MCP Java SDK 1.1.0 where tool handlers
 * are not invoked. It intercepts "tools/call" requests, manually invokes the registered
 * handlers, and constructs proper JSON-RPC responses.
 * <p>
 * Uses metatron's ObjSimpleJSONSerializer for JSON parsing, providing a lightweight
 * alternative to the SDK's internal routing mechanism.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class JsonRpcToolDispatcher {

    private static final GraphittyLogger LOG = Graphitty.log(JsonRpcToolDispatcher.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

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
     * @param tool    The tool definition (name, description, schema)
     * @param handler The handler function to invoke when the tool is called
     */
    public void registerTool(final McpSchema.Tool tool, final ToolHandler handler) {
        toolHandlers.put(tool.name(), handler);
        toolDefinitions.put(tool.name(), tool);
        LOG.info("registered tool: %s", tool.name());
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
            final Map<String, Object> parsed = OBJECT_MAPPER.readValue(message, new com.fasterxml.jackson.core.type.TypeReference<>() {
            });
            final Object method = parsed.get("method");
            return method != null && "tools/call".equals(method.toString());
        } catch (final Exception e) {
            LOG.debug("failed to check if message is tool call: %s", e.getMessage());
        }
        return false;
    }

    /**
     * Check if a JSON-RPC message is a tools/list request.
     *
     * @param message The raw JSON message string
     * @return true if this is a tools/list request
     */
    public boolean isToolListRequest(final String message) {
        try {
            final Map<String, Object> parsed = OBJECT_MAPPER.readValue(message, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
            });
            final Object method = parsed.get("method");
            return method != null && "tools/list".equals(method.toString());
        } catch (final Exception e) {
            LOG.debug("failed to check if message is tool list: %s", e.getMessage());
        }
        return false;
    }

    /**
     * Handle a tools/list request and return a JSON-RPC response.
     *
     * @param message The raw JSON-RPC request message
     * @return The JSON-RPC response as a JSON string
     */
    public String handleToolList(final String message) {
        try {
            LOG.debug("Handling tools/list request");

            // Parse the JSON-RPC request using Jackson
            final Map<String, Object> requestMap = OBJECT_MAPPER.readValue(message, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
            });

            // Extract request ID
            final Object requestId = requestMap.get("id");

            // Build tools list from registered tools
            final List<Map<String, Object>> toolsList = new ArrayList<>();
            final ObjectMapper mapper = new ObjectMapper();

            for (final McpSchema.Tool tool : toolDefinitions.values()) {
                final Map<String, Object> toolMap = new HashMap<>();
                toolMap.put("name", tool.name());
                toolMap.put("description", tool.description());

                // Convert JsonSchema to a proper object
                // The JsonSchema is likely a wrapper around JsonNode or similar
                final McpSchema.JsonSchema schema = tool.inputSchema();
                try {
                    // Try to serialize and deserialize to get a proper Map/Object
                    final String schemaJson = mapper.writeValueAsString(schema);
                    final Object schemaObj = mapper.readValue(schemaJson, Object.class);
                    toolMap.put("inputSchema", schemaObj);
                } catch (final Exception e) {
                    LOG.warn("failed to convert inputSchema for tool %s: %s", tool.name(), e.getMessage());
                    // Fallback to empty schema
                    toolMap.put("inputSchema", Map.of("type", "object", "properties", Map.of()));
                }

                toolsList.add(toolMap);
            }

            LOG.debug("returning %d tools", toolsList.size());

            // Create success response
            final Map<String, Object> result = new HashMap<>();
            result.put("tools", toolsList);

            final Map<String, Object> response = new HashMap<>();
            response.put("jsonrpc", "2.0");
            if (null != requestId)
                response.put("id", requestId);
            response.put("result", result);

            // Convert to JSON directly using Jackson (skip ObjSimpleJSONSerializer to avoid conversion errors)
            return OBJECT_MAPPER.writeValueAsString(response);

        } catch (final Exception e) {
            LOG.error("Error handling tools/list: %s", e.getMessage());
            return createErrorResponse(null, -32603, "Internal error", e.getMessage());
        }
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

            // Parse the JSON-RPC request using Jackson (NOT ObjSimpleJSONSerializer which evaluates code)
            final Map<String, Object> requestMap = OBJECT_MAPPER.readValue(message, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
            });

            // Extract request ID (required for response)
            final Object requestId = requestMap.get("id");

            // Extract params
            final Object paramsObj = requestMap.get("params");
            if (!(paramsObj instanceof Map)) {
                return createErrorResponse(requestId, -32602, "Invalid params", "params must be an object");
            }

            @SuppressWarnings("unchecked") final Map<String, Object> paramsMap = (Map<String, Object>) paramsObj;

            // Extract tool name
            final Object nameObj = paramsMap.get("name");
            if (nameObj == null) {
                return createErrorResponse(requestId, -32602, "Invalid params", "Missing 'name' field");
            }

            final String toolName = nameObj.toString();
            LOG.debug("dispatching tool call: %s", toolName);

            // Get the tool handler
            final ToolHandler handler = toolHandlers.get(toolName);
            if (handler == null) {
                return createErrorResponse(requestId, -32601, "Method not found",
                        "Tool '" + toolName + "' not found");
            }

            // Extract arguments as a Map (keep as raw Java objects, don't convert through Metatron)
            final Map<String, Object> arguments = new HashMap<>();
            final Object argumentsObj = paramsMap.get("arguments");
            if (argumentsObj instanceof Map) {
                @SuppressWarnings("unchecked") final Map<String, Object> argsMap = (Map<String, Object>) argumentsObj;
                arguments.putAll(argsMap);
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
     * Create a JSON-RPC success response.
     */
    private String createSuccessResponse(final Object id, final McpSchema.CallToolResult result) {
        try {
            final Map<String, Object> response = new HashMap<>();
            response.put("jsonrpc", "2.0");
            response.put("id", id);

            // Convert CallToolResult to a map
            final Map<String, Object> resultMap = new HashMap<>();

            // Convert content array
            final List<Map<String, Object>> contentList = new ArrayList<>();
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

            // Convert to JSON directly using Jackson (skip ObjSimpleJSONSerializer to avoid conversion errors)
            final String jsonResponse = OBJECT_MAPPER.writeValueAsString(response);

            LOG.debug("created success response: %s", jsonResponse);
            return jsonResponse;

        } catch (final Exception e) {
            LOG.error("failed to create success response: %s", e.getMessage());
            return createErrorResponse(id, -32603, "internal error",
                    "failed to serialize response: " + e.getMessage());
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

            // Convert to JSON directly using Jackson (skip ObjSimpleJSONSerializer to avoid conversion errors)
            return OBJECT_MAPPER.writeValueAsString(response);

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

}
