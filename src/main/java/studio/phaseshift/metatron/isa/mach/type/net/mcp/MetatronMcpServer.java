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
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
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
import studio.phaseshift.metatron.isa.m.type.impl.MObjFactory;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.net.mcp.annotation.McpToolRegistry;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.Tuple;

import java.util.List;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.EVAL_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.Str.str0;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.type.net.MServer.MSERVER_TID;
import static studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty.sillyPrint;
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

    public static final String MACH_SERVER_MCP_SERVER_TID = MSERVER_TID.extend("mcp").extend("server").toString();
    private static final String SERVER_NAME = "metatron-mcp";
    private static final String SERVER_VERSION = "1.0.0";

    private final McpWebSocketTransportProvider transportProvider;
    private final JsonRpcToolDispatcher toolDispatcher;
    private final GraphittyLogger LOG;

    public MetatronMcpServer(final fURI vid) {
        super(mutableMap(), f(MACH_SERVER_MCP_SERVER_TID), vid);
        this.LOG = Graphitty.log(this);
        // Set the tool dispatcher in the transport provider so it can intercept tools/call requests
        this.transportProvider = new McpWebSocketTransportProvider(createObjectMapper());
        this.toolDispatcher = new JsonRpcToolDispatcher();
        buildMcpServer();
        this.transportProvider.setToolDispatcher(this.toolDispatcher);
        // register the eval inst as tool (the foundational tool by which all other tools can be created)
        // this is the barebones necessity for an agent to then have full control of the metatron environment
        // with eval, they can then create spaces, register new instructions, create instructions, etc.
        final Obj evalInst = Router.global().read(EVAL_INST_TID);
        if (evalInst.isNoObj() || !evalInst.isObjInst())
            LOG.error("could not find eval inst at %s", EVAL_INST_TID);
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
        LOG.info("building mcp server with tool handlers");

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
                McpToolRegistry.scanPackage("studio.phaseshift.metatron.isa.mach.type.net.mcp.tool");

        // Register all discovered tools
        for (final Tuple.Pair<McpSchema.Tool, JsonRpcToolDispatcher.ToolHandler> tool : tools) {
            toolDispatcher.registerTool(tool.get0(), tool.get1());
        }

        LOG.info("registered %d tools with custom dispatcher", this.toolDispatcher.getTools().size());
    }

    public McpWebSocketTransportProvider getTransportProvider() {
        return transportProvider;
    }

    /**
     * Register a tool from any metatron Obj.
     * <p>
     * For Insts:
     * - Uses inst.tid() for the tool name
     * - Queries inst.tid().q("doc") for metadata (description, arg descriptions)
     * - Derives JSON schema from inst.dom(), inst.rng(), inst.args()
     * - Executes via inst.args(args).apply(lhs)
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
        LOG.debug("registering inst as an mcp tool: %s", toolName);
        // Query for documentation metadata
        final Obj docObj = Router.global().read(inst.tid().q("doc", null));
        // rebuild the doc object just in case the rec is not a true Java class doc (e.g. rec stored in a database)
        final DocQ.Doc doc = docObj.isNoObj() ? DocQ.Doc.empty(inst) : new DocQ.Doc(docObj.asRec());
        // Extract description
        final String description = doc.description()!= null || doc.description().isEmpty()
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
                LOG.debug("executing inst tool %s with args: %s", toolName, args);
                final Obj lhs = MObjFactory.single().toObj(args.remove("lhs"));
                // execute the inst as lhs => inst(args) => rhs
                final Obj rhs = inst.args(args
                                .entrySet()
                                .stream()
                                .map(kv -> rel(uri(kv.getKey()), MObjFactory.single().toObj(kv.getValue())))
                                .collect(new CommonUtil.RecCollector())).apply(lhs);
                return convertObjToResult(rhs);
            } catch (final Exception e) {
                LOG.error("error executing inst tool %s: %s", toolName, e.getMessage());
                return McpSchema.CallToolResult
                        .builder()
                        .content(List.of(new McpSchema.TextContent(e.getMessage())))
                        .isError(true)
                        .build();
            }
        };
        // register with dispatcher
        this.toolDispatcher.registerTool(tool, handler);
        LOG.info("successfully registered inst tool: %s", toolName);
    }

    /**
     * Build JSON schema from an Inst's type signature and doc metadata.
     * Follows the OLLM pattern for handling both Lst (positional) and Rec (named) args.
     */
    private String buildSchemaFromInst(final Inst inst, final DocQ.Doc doc) {
        final Poly<?, ?> args = inst.args().orElse(rec0());
        if (args.isNoObj() || args.isEmpty()) 
            return createDefaultArgsSchema();

        final Poly<?, ?> docArgs = doc.args();
        final JsonObject jsonSchema = new JsonObject();
        jsonSchema.addProperty(TYPE, Tokens.OBJECT);

        final JsonObject jsonArgs = new JsonObject();
        final JsonArray jsonRequired = new JsonArray();
        if (!inst.dom().isNoObj()) {
            final JsonObject propSchema = new JsonObject();
            propSchema.addProperty(TYPE, mapTypeToJsonSchemaType(inst.dom()));
            if (!inst.dom().c().isZeroable())
                jsonRequired.add(LHS);
            docArgs.at(DOM).ifPresent(domDoc -> propSchema.addProperty("description", domDoc.strValue()));
            jsonArgs.add(LHS, propSchema);
        }
        // handle lst args (positional) - use index as property name
        if (args.isLst()) {
            args.asLst().indexedStream().forEach(indexedArg -> {
                final int index = indexedArg.first().intValue().intValue();
                final Obj argType = indexedArg.second();
                final String argName = String.valueOf(index);
                final JsonObject propSchema = new JsonObject();
                propSchema.addProperty(TYPE, mapTypeToJsonSchemaType(argType));
                // Get description from doc
                final String argDesc = docArgs.isRec()
                        ? docArgs.asRec().at(jnt(index)).orElse(str0()).strValue()
                        : "";
                propSchema.addProperty("description", argDesc);
                jsonArgs.add(argName, propSchema);
                // Add to required if not zeroable
                if (!argType.c().isZeroable())
                    jsonRequired.add(argName);
            });
        }
        // Handle Rec args (named) - use uri as property name
        else if (args.isRec()) {
            args.asRec().elements().forEach(rel -> {
                final String argName = rel.first().uriValue().toString();
                final Obj argType = rel.second();

                final JsonObject propSchema = new JsonObject();
                propSchema.addProperty(TYPE, mapTypeToJsonSchemaType(argType));

                // Get description from doc
                final String argDesc = docArgs.isRec()
                        ? docArgs.asRec().at(rel.first()).orElse(str0()).strValue()
                        : "";
                propSchema.addProperty("description", argDesc);

                jsonArgs.add(argName, propSchema);

                // Add to required if not zeroable
                if (!argType.c().isZeroable()) {
                    jsonRequired.add(argName);
                }
            });
        }
        jsonSchema.add("properties", jsonArgs);
        jsonSchema.add(Tokens.REQUIRED, jsonRequired);
        return jsonSchema.toString();
    }

    /**
     * Map Metatron type names to JSON Schema types.
     */
    private String mapTypeToJsonSchemaType(final Obj obj) {
        if (obj.isStr() || obj.isUri()) {
            return "string";
        } else if (obj.isInt()) {
            return "integer";
        } else if (obj.isReal()) {
            return "number";
        } else if (obj.isBool()) {
            return "boolean";
        } else if (obj.isLst()) {
            return "array";
        } else if (obj.isRec()) {
            return "object";
        }
        return "string"; // default fallback
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
     * Convert an Obj to a CallToolResult.
     * Fail objects are valid Metatron objects and should be returned as successful results.
     */
    private McpSchema.CallToolResult convertObjToResult(final Obj obj) {
        try {
            // Convert Obj to string using its toString() method
            // This works for all Obj types including fail::T
            final String resultString = obj.toString();
            return McpSchema.CallToolResult.builder()
                    .content(List.of(new McpSchema.TextContent(resultString)))
                    .isError(false)
                    .build();
        } catch (final Exception e) {
            LOG.error("Error converting Obj to result: %s", e.getMessage());
            return McpSchema.CallToolResult.builder()
                    .content(List.of(new McpSchema.TextContent(e.getMessage())))
                    .isError(true)
                    .build();
        }
    }

    public void close() {
        LOG.info("closing metatron %s server", sillyPrint("mcp", true, true));
        transportProvider.closeGracefully().subscribe();
    }
}

