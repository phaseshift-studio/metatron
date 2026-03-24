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
import studio.phaseshift.metatron.isa.mach.type.net.mcp.tool.EvaluateCodeTool;
import studio.phaseshift.metatron.isa.mach.type.net.mcp.tool.GetSystemInfoTool;
import studio.phaseshift.metatron.isa.mach.type.net.mcp.tool.ListInstTool;
import studio.phaseshift.metatron.isa.mach.type.net.mcp.tool.ListSpaceTool;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.Tuple;

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
        this.transportProvider.setToolDispatcher(this.toolDispatcher);
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
                .instructions("metatron mcp server")
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .resources(false, false)
                        .prompts(false)
                        .build())
                .tools(McpServerFeatures.AsyncToolSpecification.builder()
                        .tool(McpSchema.Tool.builder()
                                .name(EvaluateCodeTool.getName())
                                .description(EvaluateCodeTool.getDescription())
                                .inputSchema(McpJsonDefaults.getMapper(), EvaluateCodeTool.getJsonSchema())
                                .build())
                        .callHandler((exchange, request) -> Mono.fromCallable(() -> {
                            // This handler won't be called due to SDK bug, but we keep it for completeness
                            LOG.warn("sdk tool handler called (unexpected - should use dispatcher)");
                            return McpSchema.CallToolResult.builder()
                                    .content(List.of(new McpSchema.TextContent("sdk handler called")))
                                    .build();
                        }))
                        .build())
                .tools(McpServerFeatures.AsyncToolSpecification.builder()
                        .tool(McpSchema.Tool.builder()
                                .name(ListSpaceTool.getName())
                                .description(ListSpaceTool.getDescription())
                                .inputSchema(McpJsonDefaults.getMapper(), ListSpaceTool.getJsonSchema())
                                .build())
                        .callHandler((exchange, request) -> Mono.fromCallable(() -> {
                            // This handler won't be called due to SDK bug, but we keep it for completeness
                            LOG.warn("sdk tool handler called (unexpected - should use dispatcher)");
                            return McpSchema.CallToolResult.builder()
                                    .content(List.of(new McpSchema.TextContent("sdk handler called")))
                                    .build();
                        }))
                        .build())
                .tools(McpServerFeatures.AsyncToolSpecification.builder()
                        .tool(McpSchema.Tool.builder()
                                .name(ListInstTool.getName())
                                .description(ListInstTool.getDescription())
                                .inputSchema(McpJsonDefaults.getMapper(), ListInstTool.getJsonSchema())
                                .build())
                        .callHandler((exchange, request) -> Mono.fromCallable(() -> {
                            // This handler won't be called due to SDK bug, but we keep it for completeness
                            LOG.warn("sdk tool handler called (unexpected - should use dispatcher)");
                            return McpSchema.CallToolResult.builder()
                                    .content(List.of(new McpSchema.TextContent("sdk handler called")))
                                    .build();
                        }))
                        .build())
                // Tool: get_system_info
                .tools(McpServerFeatures.AsyncToolSpecification.builder()
                        .tool(McpSchema.Tool.builder()
                                .name(GetSystemInfoTool.getName())
                                .description(GetSystemInfoTool.getDescription())
                                .inputSchema(McpJsonDefaults.getMapper(), GetSystemInfoTool.getJsonSchema())
                                .build())
                        .callHandler((exchange, request) -> Mono.fromCallable(() -> {
                            LOG.warn("sdk tool handler called (unexpected - should use dispatcher)");
                            return McpSchema.CallToolResult.builder()
                                    .content(List.of(new McpSchema.TextContent("sdk handler called")))
                                    .build();
                        }))
                        .build())
                .build();

        LOG.info("mcp server built successfully, session factory should be set");
        return server;
    }

    /**
     * Register tools with our custom dispatcher (workaround for SDK bug).
     * These handlers will actually be invoked when tools/call requests arrive.
     */
    private void registerToolsWithDispatcher() {
        LOG.info("registering tools with custom dispatcher");
        /// ////////////////////////////////////////////////////////////////////////////////////////////////////////
        final Tuple.Pair<McpSchema.Tool, JsonRpcToolDispatcher.ToolHandler> evaluateCodeTool = EvaluateCodeTool.create();
        toolDispatcher.registerTool(evaluateCodeTool.get0(), evaluateCodeTool.get1());
        /// ////////////////////////////////////////////////////////////////////////////////////////////////////////
        final Tuple.Pair<McpSchema.Tool, JsonRpcToolDispatcher.ToolHandler> listSpaceTool = ListSpaceTool.create();
        toolDispatcher.registerTool(listSpaceTool.get0(), listSpaceTool.get1());
        /// ////////////////////////////////////////////////////////////////////////////////////////////////////////
        final Tuple.Pair<McpSchema.Tool, JsonRpcToolDispatcher.ToolHandler> listInstTool = ListInstTool.create();
        toolDispatcher.registerTool(listInstTool.get0(), listInstTool.get1());
        /// ////////////////////////////////////////////////////////////////////////////////////////////////////////
        final Tuple.Pair<McpSchema.Tool, JsonRpcToolDispatcher.ToolHandler> getSytemInfoTool = GetSystemInfoTool.create();
        toolDispatcher.registerTool(getSytemInfoTool.get0(), getSytemInfoTool.get1());
        /// ////////////////////////////////////////////////////////////////////////////////////////////////////////
        LOG.info("registered %d tools with custom dispatcher", this.toolDispatcher.getTools().size());
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

