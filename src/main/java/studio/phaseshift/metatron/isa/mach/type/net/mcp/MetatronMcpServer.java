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
import io.modelcontextprotocol.spec.McpSchema;
import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.DocQ;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Poly;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.io.type.ObjSimpleJSONSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.net.mcp.annotation.McpToolRegistry;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.Tuple;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.type.net.MServer.MSERVER_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

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
public class MetatronMcpServer extends MRec {

    public static final String MACH_SERVER_MCP_SERVER_TID = MSERVER_TID.extend("mcp/server").toString();

    private static final String SERVER_NAME = "metatron-mcp";
    private static final String SERVER_VERSION = "1.0.0";

    private final McpAsyncServer mcpServer;
    private final McpWebSocketTransportProvider transportProvider;
    private final JsonRpcToolDispatcher toolDispatcher;
    private final GraphittyLogger LOG;
    private final ObjSimpleJSONSerializer jsonSerializer;

    public MetatronMcpServer(final fURI vid) {
        super(mutableMap(), f(MACH_SERVER_MCP_SERVER_TID), vid);
        this.LOG = Graphitty.log(this);
        this.jsonSerializer = ObjSimpleJSONSerializer.single();
        // Set the tool dispatcher in the transport provider so it can intercept tools/call requests
        this.transportProvider = new McpWebSocketTransportProvider(createObjectMapper());
        this.toolDispatcher = new JsonRpcToolDispatcher();
        this.mcpServer = buildMcpServer();
        this.transportProvider.setToolDispatcher(this.toolDispatcher);
        // register the eval inst as tool (the foundational tool by which all other tools can be created)
        // this is the barebones necessity for an agent to then have full control of the metatron environment
        // with eval, they can then create spaces, register new instructions, create instructions, etc.
        final Obj evalInst = Router.global().read(studio.phaseshift.metatron.isa.m.mInstSet.EVAL_INST_TID);
        if (evalInst.isNoObj() || !evalInst.isObjInst())
            LOG.error("could not find eval inst at %s", studio.phaseshift.metatron.isa.m.mInstSet.EVAL_INST_TID);
        else register(evalInst);
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
                // Tools registered via annotation system
                // SDK tool definitions kept for tools/list support
                // Actual invocation handled by custom dispatcher
                .build();

        LOG.info("mcp server built successfully, session factory should be set");
        return server;
    }

    /**
     * Register tools with our custom dispatcher (workaround for SDK bug).
     * These handlers will actually be invoked when tools/call requests arrive.
     * <p>
     * Tools are automatically discovered by scanning the annotated package.
     */
    private void registerToolsWithDispatcher() {
        LOG.info("registering tools with custom dispatcher (annotation-based)");

        // Scan for all @McpTool annotated classes in the tool package
        final List<Tuple.Pair<McpSchema.Tool, JsonRpcToolDispatcher.ToolHandler>> tools =
                McpToolRegistry.scanPackage("studio.phaseshift.metatron.isa.mach.type.net.mcp.tool.annotated");

        // Register all discovered tools
        for (final Tuple.Pair<McpSchema.Tool, JsonRpcToolDispatcher.ToolHandler> tool : tools) {
            toolDispatcher.registerTool(tool.get0(), tool.get1());
        }

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

    /**
     * Register a tool from any metatron Obj.
     * <p>
     * For Insts:
     * - Uses inst.tid() for the tool name
     * - Queries inst.tid().q("doc") for metadata (description, arg descriptions)
     * - Derives JSON schema from inst.dom(), inst.rng(), inst.args()
     * - Executes via inst.apply(args)
     * <p>
     * For Recs (legacy/fallback):
     * - Expects rec(name, desc, args, eval) structure
     *
     * @param toolObj The tool definition (preferably an Inst)
     */
    public void register(final Obj toolObj) {
        // Primary path: Register an Inst directly
        if (toolObj.isInst()) {
            registerInst((Inst) toolObj);
            return;
        }
        this.jvm(this.at(Tokens.TOOL, this.at(Tokens.TOOL).append(toolObj), MUTABLE).jvm());
        
        
       /* // Fallback path: Register from a Rec wrapper
        if (toolObj.isRec()) {
            registerFromRec(toolObj);
            return;
        }*/

        LOG.error("cannot register tool: must be an inst, got %s", toolObj);
    }

    /**
     * Register an Inst as an MCP tool using its type signature and ?doc metadata.
     */
    private void registerInst(final Inst inst) {
        // Get tool name from tid
        final String toolName = inst.tid().name();
        LOG.info("registering inst as an mcp tool: %s", toolName);
        // Query for documentation metadata
        final Obj docObj = Router.global().read(inst.tid().q("doc", null));
        // rebuild the doc object just in case the rec is not a true Doc Java class doc (e.g. rec stored in a database)
        final DocQ.Doc doc = docObj.isNoObj() ? DocQ.Doc.empty(inst) : new DocQ.Doc(docObj.asRec());
        // Extract description
        final String description = doc.description() != null
                ? doc.description()
                : "no description provided";
        // Build JSON schema from Inst signature
        final String argsSchema = buildSchemaFromInst(inst, doc);
        LOG.debug("tool schema for %s: %s", toolName, argsSchema);

        // Create MCP tool definition
        final McpSchema.Tool tool = McpSchema.Tool.builder()
                .name(toolName)
                .description(description)
                .inputSchema(McpJsonDefaults.getMapper(), argsSchema)
                .build();

        // Create handler that executes the inst
        final JsonRpcToolDispatcher.ToolHandler handler = args -> {
            try {
                LOG.debug("executing Inst tool '%s' with args: %s", toolName, args);

                // Convert args Map to Obj matching inst's expected args structure
                final Obj argsObj = convertArgsForInst(inst, args);

                // Execute the inst
                final Obj result = inst.apply(argsObj);
                if (result.isFail())
                    throw result.asFail().asException();

                // Convert result to CallToolResult
                return convertObjToResult(result);

            } catch (final Exception e) {
                LOG.error("error executing Inst tool '%s': %s", toolName, e.getMessage());
                return McpSchema.CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent(e.getMessage())))
                        .isError(true)
                        .build();
            }
        };

        // Register with dispatcher
        toolDispatcher.registerTool(tool, handler);
        LOG.info("successfully registered Inst tool: %s", toolName);
    }

    /**
     * Build JSON schema from an Inst's type signature and doc metadata.
     * Follows the OLLM pattern for handling both Lst (positional) and Rec (named) args.
     */
    private String buildSchemaFromInst(final Inst inst, final DocQ.Doc doc) {
        final Poly<?, ?> args = inst.args().orElse(studio.phaseshift.metatron.isa.m.type.impl.MRec.rec0());

        if (args.isNoObj() || args.isEmpty()) {
            return createDefaultArgsSchema();
        }

        final Poly<?, ?> docArgs = doc.args();
        final com.google.gson.JsonObject schema = new com.google.gson.JsonObject();
        schema.addProperty("type", "object");

        final com.google.gson.JsonObject properties = new com.google.gson.JsonObject();
        final com.google.gson.JsonArray required = new com.google.gson.JsonArray();

        // Handle Lst args (positional) - use index as property name
        if (args.isLst()) {
            args.asLst().indexedStream().forEach(indexedArg -> {
                final long indexLong = indexedArg.first().intValue();
                final int index = (int) indexLong;
                final Obj argType = indexedArg.second();
                final String argName = String.valueOf(index);

                final com.google.gson.JsonObject propSchema = new com.google.gson.JsonObject();
                propSchema.addProperty("type", mapTypeToJsonSchemaType(argType.tid().toString()));

                // Get description from doc
                final String argDesc = docArgs.isRec()
                        ? docArgs.asRec().at(studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt(index)).orElse(str("")).toString()
                        : "";
                propSchema.addProperty("description", argDesc);

                properties.add(argName, propSchema);

                // Add to required if not zeroable
                if (!argType.c().isZeroable()) {
                    required.add(argName);
                }
            });
        }
        // Handle Rec args (named) - use uri as property name
        else if (args.isRec()) {
            args.asRec().elements().forEach(rel -> {
                final String argName = rel.first().toString();
                final Obj argType = rel.second();

                final com.google.gson.JsonObject propSchema = new com.google.gson.JsonObject();
                propSchema.addProperty("type", mapTypeToJsonSchemaType(argType.tid().toString()));

                // Get description from doc
                final String argDesc = docArgs.isRec()
                        ? docArgs.asRec().at(rel.first()).orElse(str("")).toString()
                        : "";
                propSchema.addProperty("description", argDesc);

                properties.add(argName, propSchema);

                // Add to required if not zeroable
                if (!argType.c().isZeroable()) {
                    required.add(argName);
                }
            });
        }
        schema.add("properties", properties);
        schema.add("required", required);
        return schema.toString();
    }

    /**
     * Map Metatron type names to JSON Schema types.
     */
    private String mapTypeToJsonSchemaType(final String metatronType) {
        // Simple mapping - can be enhanced
        if (metatronType.contains("str") || metatronType.contains("uri")) {
            return "string";
        } else if (metatronType.contains("int") || metatronType.contains("jnt")) {
            return "integer";
        } else if (metatronType.contains("real")) {
            return "number";
        } else if (metatronType.contains("bool")) {
            return "boolean";
        } else if (metatronType.contains("lst")) {
            return "array";
        } else if (metatronType.contains("rec")) {
            return "object";
        }
        return "string"; // default fallback
    }

    /**
     * Convert JSON args to the format expected by an Inst.
     * Follows the OLLM pattern for handling both Lst (positional) and Rec (named) args.
     */
    private Obj convertArgsForInst(final Inst inst, final Map<String, Object> args) {
        final Poly<?, ?> instArgs = inst.args().orElse(studio.phaseshift.metatron.isa.m.type.impl.MRec.rec0());

        if (instArgs.isNoObj() || instArgs.isEmpty()) {
            return lst();
        }

        // Handle Lst args (positional) - convert from {"0": val, "1": val} to lst(val, val)
        if (instArgs.isLst()) {
            final java.util.List<Obj> argsList = new java.util.ArrayList<>();
            // Sort by index to maintain order
            args.entrySet().stream()
                    .sorted(java.util.Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        argsList.add(convertJavaToObj(entry.getValue()));
                    });
            return lst(argsList);
        }

        // Handle Rec args (named) - convert from {"key": val} to rec(uri(key), val)
        if (instArgs.isRec()) {
            return convertMapToRec(args);
        }

        // Fallback
        return convertMapToRec(args);
    }

    /**
     * Register a tool from a Rec wrapper (legacy approach).
     */
   /* private void registerFromRec(final Obj toolRec) {
        final Map<Obj, Obj> toolMap = toolRec.recValue();

        // Extract tool name (required)
        final Obj nameObj = toolMap.get(uri(Tokens.NAME));
        if (nameObj == null || !nameObj.isStr()) {
            LOG.error("Cannot register tool from Rec: missing or invalid 'name' field");
            return;
        }
        final String toolName = nameObj.strValue();

        // Extract description (required)
        final Obj descObj = toolMap.get(uri(Tokens.DESC));
        final String description = (descObj != null && descObj.isStr())
                ? descObj.strValue()
                : "No description provided";

        // Extract the eval inst (required)
        final Obj evalObj = toolMap.get(uri("eval"));
        if (evalObj == null || !evalObj.isInst()) {
            LOG.error("Cannot register tool '%s': missing or invalid 'eval' field", toolName);
            return;
        }
        final Inst evalInst = (Inst) evalObj;

        // Extract args schema (optional)
        final Obj argsObj = toolMap.get(uri("args"));
        final String argsSchema = (argsObj != null && argsObj.isRec())
                ? jsonSerializer.write(argsObj).toString()
                : createDefaultArgsSchema();

        LOG.info("Registering tool from Rec: %s", toolName);

        // Create MCP tool definition
        final McpSchema.Tool tool = McpSchema.Tool.builder()
                .name(toolName)
                .description(description)
                .inputSchema(McpJsonDefaults.getMapper(), argsSchema)
                .build();

        // Create handler that executes the inst
        final JsonRpcToolDispatcher.ToolHandler handler = args -> {
            try {
                LOG.debug("Executing tool '%s' with args: %s", toolName, args);

                // Convert args Map to Obj
                final Obj argsRec = convertMapToRec(args);

                // Execute the inst
                final Obj result = evalInst.apply(argsRec);

                // Convert result to CallToolResult
                return convertObjToResult(result);

            } catch (final Exception e) {
                LOG.error("Error executing tool '%s': %s", toolName, e.getMessage());
                return McpSchema.CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent("Error: " + e.getMessage())))
                        .isError(true)
                        .build();
            }
        };

        // Register with dispatcher
        toolDispatcher.registerTool(tool, handler);
        LOG.info("Successfully registered tool: %s", toolName);
    }*/

    /**
     * Register the existing eval instruction as an MCP tool.
     * The eval instruction is already defined in Obj with documentation.
     */
    private void registerEvaluateCodeTool() {
        LOG.info("Registering existing eval instruction as mcp tool");

        // Get the eval instruction from the global router
        final Obj evalInst = Router.global().read(studio.phaseshift.metatron.isa.m.mInstSet.EVAL_INST_TID).orElse(null);
        if (evalInst == null || !evalInst.isInst()) {
            LOG.error("could not find eval instruction at %s", studio.phaseshift.metatron.isa.m.mInstSet.EVAL_INST_TID);
            return;
        }
        // Register the existing Inst directly - it already has docWrap metadata!
        register(evalInst);
    }

    /**
     * Create a default args schema for tools without explicit schema.
     */
    private String createDefaultArgsSchema() {
        return """
               {
                 "type": "object",
                 "properties": {},
                 "required": []
               }
               """;
    }

    /**
     * Convert a Map<String, Object> to a metatron Rec.
     */
    private Obj convertMapToRec(final Map<String, Object> map) {
        final Map<Obj, Obj> recMap = new HashMap<>();
        for (final Map.Entry<String, Object> entry : map.entrySet()) {
            recMap.put(uri(entry.getKey()), convertJavaToObj(entry.getValue()));
        }
        return rec(recMap);
    }

    /**
     * Convert a Java value to a metatron Obj.
     */
    private Obj convertJavaToObj(final Object value) {
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
            return str((String) value);
        } else if (value instanceof List) {
            final List<Obj> list = new java.util.ArrayList<>();
            for (final Object item : (List<?>) value) {
                list.add(convertJavaToObj(item));
            }
            return lst(list);
        } else if (value instanceof Map) {
            return convertMapToRec((Map<String, Object>) value);
        } else {
            return str(value.toString());
        }
    }

    /**
     * Convert an Obj to a CallToolResult.
     * - If Obj is a Fail, sets isError(true)
     * - Otherwise, converts to JSON using ObjSimpleJSONSerializer
     */
    private McpSchema.CallToolResult convertObjToResult(final Obj obj) {
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
                final com.google.gson.JsonElement jsonElement = jsonSerializer.write(obj);
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

    public void close() {
        LOG.info("Closing metatron MCP Server");
        transportProvider.closeGracefully().subscribe();
    }
}

