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

import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpRoot;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.mcp.client.transport.websocket.WebSocketMcpTransport;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.type.Bool.*;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class MCPServer extends MRec {

    public static final Type MCP_SERVER_TYPE = docWrap(Type.Builder.build().tid(REC_TID).vid(MCP_SERVER_TID)
            .isaPredicate(rec(
                    uri(HOST), URI_TYPE,
                    uri(TOOL).maybe(), rec(URI_TYPE, T(LLM_TOOL_TID)).maybe(),
                    uri(STATUS).maybe(), isa_(BOOL_TYPE).else_(BOOL_FALSE)))
            .constructor(instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(MCP_SERVER_TID), lst(T(REC_TID)),
                    (x, inst) -> new MCPServer(inst.arg(0).asRec().jvm(), MCP_SERVER_TID, inst.arg(0).vid())))
            .create(),"a mcp server specification","creates a connection to an existing mcp server",
            Map.of(
                    uri(HOST), "the mcp server endpoint",
                    uri(TOOL).maybe(), "the tools/functions available for use on the mcp server",
                    uri(STATUS).maybe(), "the current status of the mcp server"), 
            "a server implementing the model content protocol used by llms for the acquisition of tools and access to extenal software systems",
            "mcp::[host => <http://127.0.0.1:29170/index-mcp/streamable-http>]@/usr/ai/mcp/intellij [-- connection populates tool and status      --]",
            "mcp::[host => <ws://localhost:8999>]@/usr/ai/mcp/mtron                                 [-- mtron router server exposes an mcp server --]");

    /*public static final Type MCP_TOOL_TYPE = Type.Builder.build().tid(REC_TID).vid(MCP_TOOL_TID)
            .isaPredicate(rec(
                    uri(NAME), URI_TYPE,
                    uri(DESC), STR_TYPE,
                    uri(ARG).maybe(), rec(URI_TYPE, T(ALL)).maybe()))
            .create();*/

    protected final McpClient client;

    public MCPServer(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
        this.client = DefaultMcpClient.builder()
                .clientName(METATRON)
                .clientVersion(METATRON_VERSION)
                //.roots(List.of(new McpRoot("metatron", "http://localhost:8999")))
                .logHandler(message -> as().logger().debug("mcp log: %s", message))
                .transport(createTransport(
                        jvm.get(uri(TRANSPORT)),
                        jvm.getOrDefault(uri(HEADERS), rec0()).jvm(),
                        jvm.get(uri(HOST))))
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

        this.jvm().put(uri(TOOL), auto_(instC(
                M_ISA_INST_TID.dom(ALL.maybe()).rng(REC_TID), lst(),
                (lhs, inst) -> this.client.listTools().stream()
                        .map(t -> {
                            try {
                                return rec(
                                        uri(NAME), uri(t.name()),
                                        uri(DESC), str(t.description()),
                                        uri(ARG), Optional.ofNullable(t.parameters())
                                                .map(JsonObjectSchema::properties)
                                                .map(p -> p.entrySet().stream()
                                                        .map(kv -> rel(uri(kv.getKey()), str(kv.getValue().description())))
                                                        .collect(new CommonUtil.RecCollector())).orElse(rec()));
                            } catch (final Exception e) {
                                throw MTronException.of(e, "error build server: " + t.name());
                            }
                        })
                        .reduce(rec(), (a, b) -> a.at(b.at(uri(NAME)).asUri(), b)))));
    }

    @Override
    public MCPServer clone() {
        return this;
    }

    public McpClient client() {
        return this.client;
    }

    protected static McpTransport createTransport(final Obj transport, final Map<Obj, Obj> headers, final Obj host) {
        if (null != transport) {
            if (f(STREAMABLE_HTTP).equals(transport.uriValue())) {
                return StreamableHttpMcpTransport.builder()
                        //  .logRequests(true)
                        //  .logResponses(true)
                        .customHeaders(headers.keySet().stream().collect(Collectors.toMap(k -> k.uriValue().toString(), v -> v.uriValue().toString())))
                        .url(host.uriValue().toString())
                        .build();
            }
        } else {
            if (host.uriValue().scheme().equals(WS) || host.uriValue().scheme().equals(WSS))
                return WebSocketMcpTransport.builder()
                        //  .logRequests(true)
                        //  .logResponses(true)
                        .url(host.uriValue().toString())
                        .build();
            if (host.uriValue().scheme().equals(HTTP) || host.uriValue().scheme().equals(HTTPS)) {
                return StreamableHttpMcpTransport.builder()
                        //  .logRequests(true)
                        //  .logResponses(true)
                        .url(host.uriValue().toString())
                        .build();
            } else
                throw MTronException.of("unsupported scheme: " + host.uriValue().scheme());
        }
        throw MTronException.of("unsupported transport for %s: %s %s", host, transport, headers);
    }

}
