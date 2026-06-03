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

package studio.phaseshift.metatron.isa.llm.type;

import com.google.gson.JsonParser;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import dev.langchain4j.mcp.client.transport.websocket.WebSocketMcpTransport;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.service.tool.ToolExecutionResult;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Str;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.io.type.ObjSimpleJSONSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.slf4j.event.Level.WARN;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.BOOL_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.M_ISA_INST_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_FALSE;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_TRUE;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mcpClient extends MRec {

    protected static final GraphittyLogger LOG = Graphitty.log(mcpClient.class);

    protected final McpClient client;

    public mcpClient(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
        this.client = DefaultMcpClient.builder()
                .clientName(METATRON)
                .clientVersion(METATRON_VERSION)
                .protocolVersion("2024-11-05")
                //.roots(List.of(new McpRoot("metatron", "http://localhost:8999")))
                .logHandler(message -> as().logger().debug("mcp log: %s", message))
                .transport(createTransport(
                        this.at(uri(TRANSPORT)),
                        this.at(uri(HEADERS)).orElse(rec()).jvm(),
                        this.at(uri(HOST)),
                        this.at(COMMAND).orElse(lst()).jvm()))
                //.autoHealthCheck(true)
                .cacheToolList(true)
                .build();
        this.jvm().put(uri(STATUS), auto_(instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(BOOL_TID), lst(), (lhs, inst) -> {
            try {
                this.client.checkHealth();
                return BOOL_TRUE;
            } catch (final Exception e) {
                return BOOL_FALSE;
            }
        })));
        final Rec tools = rec();
        this.client.listTools().stream().forEach(t -> {
            try {
                final Map<Obj, String> documentationArgs = Optional.ofNullable(t.parameters())
                        .map(JsonObjectSchema::properties)
                        .map(p -> p.entrySet().stream()
                                .map(kv -> new AbstractMap.SimpleEntry<Obj, String>(uri(kv.getKey()), kv.getValue().description()))
                                .collect(Collectors.toMap(kv -> kv.getKey(), kv -> kv.getValue()))).orElse(null);
                final Rec evaluationArgs = Optional.ofNullable(t.parameters())
                        .map(JsonObjectSchema::properties)
                        .map(p -> p.entrySet().stream()
                                .map(kv -> rel(uri(kv.getKey()), str(kv.getValue().description())))
                                .collect(new CommonUtil.RecCollector())).orElse(rec());
                //////////////////////////////////////////////////////////////////////////////////////
                final Inst toolInst = instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(ALL.maybe()),
                        evaluationArgs, (lhs2, inst2) -> {
                            final ToolExecutionResult result =
                                    this.client.executeTool(ToolExecutionRequest.builder()
                                            .name(t.name())
                                            .arguments(inst2.args().isEmpty() ?
                                                    null :
                                                    inst2.args().isRec() ?
                                                    new String(ObjSimpleJSONSerializer.single()
                                                            .outputBytes(inst2.args())
                                                            .array()) :
                                                    new String(ObjSimpleJSONSerializer.single()
                                                            .outputBytes(Inst.Helper.rectifyLstArgs(inst2.args().asLst(), evaluationArgs))
                                                            .array()))
                                            .build());
                            if (result.isError())
                                return fail(result.resultText());
                            else {
                                try {
                                    return ObjSimpleJSONSerializer.parse(result.resultText());
                                } catch (final Exception e1) {
                                    final Obj parsed = ObjmtronSerializer.parse(result.resultText());
                                    if (parsed.isFail())
                                        return str(result.resultText());
                                    return parsed;
                                }
                            }
                        });
                tools.at(f(t.name()), toolInst, MUTABLE);
                if (vid != null && Router.global().getSpaceFor(vid).hasQ(DOCQ_PATTERN)) {
                    final fURI toolDocQ = vid.extend("tool").extend(t.name()).addQ(DOCQ);
                    Router.writeToSpace(toolDocQ, Docs.doc(toolInst, null, null, documentationArgs, t.description()));
                    this.logger().info("tool documentation available at %s", toolDocQ);
                }
            } catch (final Exception e) {
                throw MTronException.of(e, "error build server: " + t.name());
            }
        });
        if (!tools.isEmpty())
            this.jvm().put(uri(TOOL), tools);
    }

    @Override
    public mcpClient clone() {
        return this;
    }

    public McpClient client() {
        return this.client;
    }

    protected static McpTransport createTransport(final Obj transport, final Map<Obj, Obj> headers, final Obj host, final List<Obj> command) {
        final Map<String, String> stringHeaders = headers.entrySet().stream()
                .map(e -> new AbstractMap.SimpleEntry<>(Str.Helper.cleanString(e.getKey()), Str.Helper.cleanString(e.getValue())))
                .collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue));

        if (null != transport && !transport.isNoObj() && f(STREAMABLE_HTTP).equals(transport.uriValue())) {
            return StreamableHttpMcpTransport.builder()
                    .logRequests(true)
                    .logResponses(true)
                    .logger(LOG.logger(WARN))
                    .customHeaders(stringHeaders)
                    .url(host.uriValue().toString())
                    .executor(BootLoader.getExecutor())
                    .build();
        } else if (!command.isEmpty()) {  // STDIO
            return new StdioMcpTransport.Builder()
                    .command(command.stream().map(Str.Helper::cleanString).toList())
                    //.logEvents(false)
                    .logger(LOG.logger(WARN))
                    .executorService(BootLoader.getExecutor())
                    .environment(stringHeaders)
                    .build();
        } else if (host.uriValue().scheme().equals(WS) || host.uriValue().scheme().equals(WSS)) {
            return WebSocketMcpTransport.builder() // WEBSOCKET
                    .logRequests(true)
                    .logResponses(true)
                    .logger(LOG.logger(WARN))
                    .url(host.uriValue().toString())
                    .executor(BootLoader.getExecutor())
                    .build();
        } else if (host.uriValue().name().equals("sse")) {  // TODO: remove when sse is no longer supported
            return HttpMcpTransport.builder() // SSE
                    .sseUrl(host.uriValue().toString())
                    .logger(LOG.logger(WARN))
                    .logRequests(false)
                    .logResponses(false)
                    .customHeaders(stringHeaders)
                    .build();
        } else if (host.uriValue().scheme().equals(HTTP) || host.uriValue().scheme().equals(HTTPS)) {
            return StreamableHttpMcpTransport.builder() // HTTP
                    .logRequests(true)
                    .logResponses(true)
                    .logger(LOG.logger(WARN))
                    .customHeaders(stringHeaders)
                    .url(host.uriValue().toString())
                    .executor(BootLoader.getExecutor())
                    .build();

        }
        throw MTronException.of("unsupported transport for %s: %s %s", host, transport, headers);
    }

}
