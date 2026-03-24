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
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpAsyncServer;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import reactor.core.publisher.Mono;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * metatron MCP Server - Exposes metatron functionality via Model Context Protocol.
 * <p>
 * This server allows AI assistants to interact with metatron by:
 * - Evaluating metatron code
 * - Querying system information
 * - Listing available instructions
 * - Accessing router state
 * <p>
 * The server integrates with MServer's WebSocket infrastructure and provides
 * a standardized interface for AI model interactions.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class MetatronMcpServer {

    private static final String SERVER_NAME = "metatron-mcp";
    private static final String SERVER_VERSION = "1.0.0";

    private final McpAsyncServer mcpServer;
    private final McpWebSocketTransportProvider transportProvider;
    private final JsonRpcToolDispatcher toolDispatcher;
    private final GraphittyLogger LOG;

    public MetatronMcpServer() {
        this.LOG = Graphitty.log(this);
        this.toolDispatcher = new JsonRpcToolDispatcher();
        this.transportProvider = new McpWebSocketTransportProvider(createObjectMapper());
        // Set the tool dispatcher in the transport provider so it can intercept tools/call requests
        this.transportProvider.setToolDispatcher(toolDispatcher);
        this.mcpServer = buildMcpServer();
        LOG.info("metatron mcp server initialized");
    }

    private ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // Configure mapper for MCP JSON-RPC compatibility
        mapper.findAndRegisterModules();
        return mapper;
    }

    private McpAsyncServer buildMcpServer() {
        LOG.info("Building MCP server with tool handlers");

        // Register tools with our custom dispatcher (workaround for SDK bug)
        registerToolsWithDispatcher();

        // Build the server - this will automatically call setSessionFactory on the transport provider
        // We still register tools with the SDK for tools/list support, but actual invocation
        // will be handled by our custom dispatcher
        final McpAsyncServer server = McpServer.async(transportProvider)
                .serverInfo(SERVER_NAME, SERVER_VERSION)
                .instructions("metatron mcp server - Execute metatron code and query system state. " +
                        "Use 'evaluate_code' to run metatron expressions, 'get_system_info' for router details, " +
                        "and 'list_instructions' to discover available instruction types.")
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .resources(false, false)
                        .prompts(false)
                        .build())
                // Tool: evaluate_code
                .tools(McpServerFeatures.AsyncToolSpecification.builder()
                        .tool(McpSchema.Tool.builder()
                                .name("evaluate_code")
                                .description("Evaluate metatron code and return the result. " +
                                        "The code is executed in the context of the global router.")
                                .inputSchema(McpJsonDefaults.getMapper(), createEvaluateCodeSchemaJson())
                                .build())
                        .callHandler((exchange, request) -> Mono.fromCallable(() -> {
                            // This handler won't be called due to SDK bug, but we keep it for completeness
                            LOG.warn("SDK tool handler called (unexpected - should use dispatcher)");
                            return McpSchema.CallToolResult.builder()
                                    .content(List.of(new McpSchema.TextContent("SDK handler called")))
                                    .build();
                        }))
                        .build())
                // Tool: get_system_info
                .tools(McpServerFeatures.AsyncToolSpecification.builder()
                        .tool(McpSchema.Tool.builder()
                                .name("get_system_info")
                                .description("Get information about the metatron system including router state, " +
                                        "server information, and system statistics.")
                                .inputSchema(McpJsonDefaults.getMapper(), createSystemInfoSchemaJson())
                                .build())
                        .callHandler((exchange, request) -> Mono.fromCallable(() -> {
                            LOG.warn("SDK tool handler called (unexpected - should use dispatcher)");
                            return McpSchema.CallToolResult.builder()
                                    .content(List.of(new McpSchema.TextContent("SDK handler called")))
                                    .build();
                        }))
                        .build())
                // Tool: list_instructions
                .tools(McpServerFeatures.AsyncToolSpecification.builder()
                        .tool(McpSchema.Tool.builder()
                                .name("list_instructions")
                                .description("List available metatron instruction types and their descriptions. " +
                                        "Optionally filter by category or search term.")
                                .inputSchema(McpJsonDefaults.getMapper(), createListInstructionsSchemaJson())
                                .build())
                        .callHandler((exchange, request) -> Mono.fromCallable(() -> {
                            LOG.warn("SDK tool handler called (unexpected - should use dispatcher)");
                            return McpSchema.CallToolResult.builder()
                                    .content(List.of(new McpSchema.TextContent("SDK handler called")))
                                    .build();
                        }))
                        .build())
                .build();

        LOG.info("MCP server built successfully, session factory should be set");
        return server;
    }

    /**
     * Register tools with our custom dispatcher (workaround for SDK bug).
     * These handlers will actually be invoked when tools/call requests arrive.
     */
    private void registerToolsWithDispatcher() {
        LOG.info("Registering tools with custom dispatcher");

        // Tool: evaluate_code
        toolDispatcher.registerTool(
                McpSchema.Tool.builder()
                        .name("evaluate_code")
                        .description("Evaluate metatron code and return the result. " +
                                "The code is executed in the context of the global router.")
                        .inputSchema(McpJsonDefaults.getMapper(), createEvaluateCodeSchemaJson())
                        .build(),
                args -> {
                    try {
                        LOG.info("evaluate_code tool handler invoked via dispatcher");
                        String code = args.get("code").toString();
                        LOG.debug("Evaluating code: %s", code);

                        // Execute code through Router - read and apply
                        Obj codeObj = mParser.parse(code);
                        Obj result = codeObj.apply();

                        String resultStr = result.toString();
                        LOG.debug("Evaluation result: %s", resultStr);

                        return McpSchema.CallToolResult.builder()
                                .content(List.of(new McpSchema.TextContent(resultStr)))
                                .isError(false)
                                .build();
                    } catch (Exception e) {
                        LOG.error("Error evaluating code: %s", e.getMessage());
                        return McpSchema.CallToolResult.builder()
                                .content(List.of(new McpSchema.TextContent("Error: " + e.getMessage())))
                                .isError(true)
                                .build();
                    }
                }
        );

        // Tool: get_system_info
        toolDispatcher.registerTool(
                McpSchema.Tool.builder()
                        .name("get_system_info")
                        .description("Get information about the metatron system including router state, " +
                                "server information, and system statistics.")
                        .inputSchema(McpJsonDefaults.getMapper(), createSystemInfoSchemaJson())
                        .build(),
                args -> {
                    try {
                        LOG.info("get_system_info tool handler invoked via dispatcher");
                        StringBuilder info = new StringBuilder();
                        info.append("=== Metatron System Information ===\n\n");

                        if (Router.loaded()) {
                            Router router = Router.global();
                            info.append("Router VID: ").append(router.vid()).append("\n");
                            info.append("Router TID: ").append(router.tid()).append("\n");

                            if (router.server() != null) {
                                info.append("Server Host: ").append(router.server().host()).append("\n");
                                info.append("Server Running: ").append(router.server().isRunning()).append("\n");
                            }

                            if (router.stats() != null) {
                                info.append("\nStatistics:\n");
                                info.append("  I/O Stats: ").append(router.stats().ioStats()).append("\n");
                            }
                        } else {
                            info.append("Router not loaded\n");
                        }

                        return McpSchema.CallToolResult.builder()
                                .content(List.of(new McpSchema.TextContent(info.toString())))
                                .isError(false)
                                .build();
                    } catch (Exception e) {
                        LOG.error("Error getting system info: %s", e.getMessage());
                        return McpSchema.CallToolResult.builder()
                                .content(List.of(new McpSchema.TextContent("Error: " + e.getMessage())))
                                .isError(true)
                                .build();
                    }
                }
        );

        // Tool: list_instructions
        toolDispatcher.registerTool(
                McpSchema.Tool.builder()
                        .name("list_instructions")
                        .description("List available metatron instruction types and their descriptions. " +
                                "Optionally filter by category or search term.")
                        .inputSchema(McpJsonDefaults.getMapper(), createListInstructionsSchemaJson())
                        .build(),
                args -> {
                    try {
                        LOG.info("list_instructions tool handler invoked via dispatcher");
                        String filter = args.containsKey("filter") ? args.get("filter").toString() : null;

                        StringBuilder result = new StringBuilder();
                        result.append("=== Metatron Instructions ===\n\n");

                        // TODO: Implement instruction listing from mInstSet
                        // For now, provide basic instruction categories
                        result.append("Core Instructions:\n");
                        result.append("  - Arithmetic: plus, mult, neg, minus\n");
                        result.append("  - Logic: and, or, not\n");
                        result.append("  - Relations: id, compose, domain, range\n");
                        result.append("  - Collections: lst, objs, map\n");
                        result.append("  - Control: if, loop, apply\n");
                        result.append("\nUse evaluate_code to execute instructions.\n");

                        return McpSchema.CallToolResult.builder()
                                .content(List.of(new McpSchema.TextContent(result.toString())))
                                .isError(false)
                                .build();
                    } catch (Exception e) {
                        LOG.error("Error listing instructions: %s", e.getMessage());
                        return McpSchema.CallToolResult.builder()
                                .content(List.of(new McpSchema.TextContent("Error: " + e.getMessage())))
                                .isError(true)
                                .build();
                    }
                }
        );

        LOG.info("Registered %d tools with custom dispatcher", toolDispatcher.getTools().size());
    }

    private String createEvaluateCodeSchemaJson() {
        return """
               {
                 "type": "object",
                 "properties": {
                   "code": {
                     "type": "string",
                     "description": "metatron code to evaluate"
                   }
                 },
                 "required": ["code"]
               }
               """;
    }

    private String createSystemInfoSchemaJson() {
        return """
               {
                 "type": "object",
                 "properties": {}
               }
               """;
    }

    private String createListInstructionsSchemaJson() {
        return """
               {
                 "type": "object",
                 "properties": {
                   "filter": {
                     "type": "string",
                     "description": "Optional filter to search for specific instructions"
                   }
                 }
               }
               """;
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> extractArguments(final McpSchema.CallToolRequest request) {
        final Object args = request.arguments();
        if (args instanceof Map) {
            return (Map<String, Object>) args;
        }
        return new HashMap<>();
    }

    public McpWebSocketTransportProvider getTransportProvider() {
        return transportProvider;
    }

    public McpAsyncServer getMcpServer() {
        return mcpServer;
    }

    public JsonRpcToolDispatcher getToolDispatcher() {
        return toolDispatcher;
    }

    public void close() {
        LOG.info("Closing metatron MCP Server");
        transportProvider.closeGracefully().subscribe();
    }
}

