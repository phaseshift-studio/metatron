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

package studio.phaseshift.metatron.isa.web;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.dcmnt.schema.storage.ObjBSONSerializer;
import studio.phaseshift.metatron.isa.llm.type.mcpClient;
import studio.phaseshift.metatron.isa.m.type.impl.MObj;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.io.type.ObjJavaSerializer;
import studio.phaseshift.metatron.isa.mach.io.type.ObjByteBufferSerializer;
import studio.phaseshift.metatron.isa.web.parser.ObjJSONSerializer;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.web.parser.*;
import studio.phaseshift.metatron.isa.web.type.MIME;
import studio.phaseshift.metatron.isa.web.type.mcpServer;
import studio.phaseshift.metatron.util.CommonUtil;

import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_TOOL_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_MILLIS_TID;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_TIME_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.inside_;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_FALSE;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_TRUE;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Inst.INST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Int.INT_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Lst.LST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.NOOBJ_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.space.http.handler.mcp_emulator_httpHandler.HTTP_MCP_EMULTATOR_TYPE;
import static studio.phaseshift.metatron.isa.web.space.http.handler.mcp_httpHandler.HTTP_MCP_HANDLER_TYPE;
import static studio.phaseshift.metatron.isa.web.space.http.handler.mcp_mtron_httpHandler.HTTP_MCP_MTRON_TYPE;
import static studio.phaseshift.metatron.isa.web.space.http.handler.mtron_httpHandler.HTTP_MTRON_HANDLER_TYPE;
import static studio.phaseshift.metatron.isa.web.space.http.handler.web_httpHandler.WEB_HTTP_HANDLER_TYPE;
import static studio.phaseshift.metatron.isa.web.space.http.httpSpace.*;
import static studio.phaseshift.metatron.isa.web.space.ws.handler.mcp_emulator_wsHandler.WS_MCP_EMULTATOR_TYPE;
import static studio.phaseshift.metatron.isa.web.space.ws.handler.mcp_mtron_wsHandler.WS_MCP_MTRON_HANDLER_TYPE;
import static studio.phaseshift.metatron.isa.web.space.ws.handler.mcp_wsHandler.WS_MCP_HANDLER_TYPE;
import static studio.phaseshift.metatron.isa.web.space.ws.handler.mtron_wsHandler.WS_MTRON_HANDLER_TYPE;
import static studio.phaseshift.metatron.isa.web.space.ws.wsSpace.*;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@JREService(vid = "/m/web")
public class webInstSet extends AbstractInstSet {

    public static final fURI WEB_ISA_TID = M_ISA_TID.extend("web");
    public static final fURI INST_TID = WEB_ISA_TID.extend("inst");
    public static final fURI MIME_TYPE_TID = WEB_ISA_TID.extend("mime");
    public static final fURI XML_TID = MIME_TYPE_TID.extend("xml");
    public static final fURI HTML_TID = MIME_TYPE_TID.extend("html");
    public static final fURI JSON_TID = MIME_TYPE_TID.extend("json");
    public static final fURI JSON_STR_TID = MIME_TYPE_TID.extend("json_str");
    public static final fURI CSS_TID = MIME_TYPE_TID.extend("css");
    public static final fURI MARKDOWN_TID = MIME_TYPE_TID.extend("markdown");
    public static final fURI JAVA_TID = MIME_TYPE_TID.extend("java");

    // ── Serializer types ────────────────────────────────────────────
    public static final fURI OBJ_SERIALIZER_TID = WEB_ISA_TID.extend("serializer");
    public static final fURI OBJ_MTRON_SERIALIZER_TID = OBJ_SERIALIZER_TID.extend("obj_mtron");
    public static final fURI OBJ_SIMPLE_JSON_SERIALIZER_TID = OBJ_SERIALIZER_TID.extend("obj_simple_json");
    public static final fURI OBJ_BSON_SERIALIZER_TID = OBJ_SERIALIZER_TID.extend("obj_bson");
    public static final fURI OBJ_BYTE_BUFFER_SERIALIZER_TID = OBJ_SERIALIZER_TID.extend("obj_bytebuffer");
    public static final fURI OBJ_TP3_SERIALIZER_TID = OBJ_SERIALIZER_TID.extend("obj_tp3");
    public static final fURI OBJ_JSON_SERIALIZER_TID = OBJ_SERIALIZER_TID.extend("obj_json");
    public static final fURI OBJ_HTML_SERIALIZER_TID = OBJ_SERIALIZER_TID.extend("obj_html");
    public static final fURI OBJ_XML_SERIALIZER_TID = OBJ_SERIALIZER_TID.extend("obj_xml");
    public static final fURI OBJ_MARKDOWN_SERIALIZER_TID = OBJ_SERIALIZER_TID.extend("obj_markdown");
    public static final fURI OBJ_JAVA_SERIALIZER_TID = OBJ_SERIALIZER_TID.extend("ojb_java");
    public static final fURI OBJ_RDF_SERIALIZER_TID = OBJ_SERIALIZER_TID.extend("obj_rdf");
    public static final fURI OBJ_PLAINTEXT_SERIALIZER_TID = OBJ_SERIALIZER_TID.extend("obj_text");
    
    public static Type MIME_OBJ_TYPE = Type.Builder.build()
            .tid(URI_TID)
            .vid(MIME_TYPE_TID)
            .isaPredicate(inside_(Stream.of(MIME.MIMEType.values()).map(m -> uri(m.value)).collect(new CommonUtil.LstCollector())))
            .create();

    public static final Type XML_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(XML_TID).create();
    public static final Type HTML_TYPE = Type.Builder.build()
            .tid(XML_TID)
            .vid(HTML_TID)
            .isaPredicate(rec(uri(HTML), rec(uri(HEAD), rec(uri(TITLE).maybe().asUri(), STR_TYPE), uri(BODY), REC_TYPE))).create();
    public static final Type JSON_STR_TYPE = Type.Builder.build()
            .tid(STR_TID)
            .vid(JSON_STR_TID).create();
    public static final Type JAVA_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(JAVA_TID)
            .create();
    public static final Type JSON_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(JSON_TID)
            .isaPredicate(rec(URI_TYPE, inside_(lst(
                    isa_(NOOBJ_TYPE),
                    isa_(BOOL_TYPE),
                    isa_(INT_TYPE),
                    isa_(STR_TYPE),
                    isa_(URI_TYPE),
                    isa_(LST_TYPE),
                    isa_(REC_TYPE))))).create();
    public static final Type CSS_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(CSS_TID).create();
    public static final Type MARKDOWN_TYPE = Type.Builder.build().tid(REC_TID).vid(MARKDOWN_TID).create();

    // ── Serializer type definitions ──────────────────────────────────
    public static Type OBJ_SERIALIZER_TYPE;
    public static Type OBJ_MTRON_SERIALIZER_TYPE;
    public static Type OBJ_SIMPLE_JSON_SERIALIZER_TYPE;
    public static Type OBJ_BSON_SERIALIZER_TYPE;
    public static Type OBJ_BYTE_BUFFER_SERIALIZER_TYPE;
    public static final fURI MCP_SERVER_TID = WEB_ISA_TID.extend("mcp").extend("mcp_server");
    public static final fURI MCP_CLIENT_TID = WEB_ISA_TID.extend("mcp").extend("mcp_client");
    public static final fURI CLIENT_TID = WEB_ISA_TID.extend("client");
    public static Type CLIENT_TYPE;
    public static final fURI SERVER_TID = WEB_ISA_TID.extend("server");
    public static Type SERVER_TYPE;
    public static Type MCP_CLIENT_TYPE;
    public static Type MCP_SERVER_TYPE;


    public webInstSet() {
        super(mutableMap(uri(PATTERN), uri(WEB_ISA_TID.extend(ALL))), INSTSET_TID, WEB_ISA_TID);
    }

    @Override
    public void setup() {
        this.jvm().putAll(mutableMap(
                uri(CONST), lst(
                        docWrap(rec(mutableMap(
                                                uri("remote_console"), webHelper.remoteConsole()),
                                        REC_TID, WEB_ISA_TID.extend("helper")),
                                "a collection of web related utilities"),
                        ObjXMLSerializer.single(),
                        ObjHTMLSerializer.single(),
                        ObjJSONSerializer.single(),
                        ObjMarkdownSerializer.single(),
                        //ObjJavaSerializer.single(),
                        ObjPlainTextSerializer.single(),
                        ObjmtronSerializer.single(),
                        ObjByteBufferSerializer.singleton(),
                       ObjJSONSerializer.simple(),
                       ObjBSONSerializer.single()),
                uri(TYPE), lst(
                        docWrap(MIME_OBJ_TYPE, "indicates the media type of the data as specified by RFC-9110"),
                        docWrap(XML_TYPE, "a rec encoding of an xml document"),
                        docWrap(HTML_TYPE, "a rec encoding of an html document",
                                "*<http://metatron.phaseshift.studio> [-- yields an html::T --]",
                                """
                                html::[html=>
                                       [head=>
                                        [title=>"metatron"]],
                                        body=>
                                         [out=>[
                                          [tag=>a,href=>...],
                                          [tag...]]]]"""),
                        docWrap(JSON_TYPE, "a rec encoding of a json document"),
                        docWrap(CSS_TYPE, "a rec encoding of a css document"),
                        docWrap(MARKDOWN_TYPE, "a rec encoding of a markdown document"),
                        docWrap(JAVA_TYPE, "a rec encoding of a java source file"),
                        docWrap(OBJ_SERIALIZER_TYPE = Type.Builder.build()
                                        .tid(OBJ_SERIALIZER_TID).vid(OBJ_SERIALIZER_TID).create(),
                                "a serializer for converting objs to/from external formats"),
                        docWrap(OBJ_MTRON_SERIALIZER_TYPE = Type.Builder.build()
                                        .tid(OBJ_SERIALIZER_TID)
                                        .vid(OBJ_MTRON_SERIALIZER_TID)
                                        .isaPredicate(rec(
                                                uri(CLIP), rec(
                                                        uri(REC_TID).maybe().asUri(), isa_(INT_TYPE).orElse(jnt(7)),
                                                        uri(LST_TID).maybe(), isa_(INT_TYPE).orElse(jnt(10)),
                                                        uri(STR_TID).maybe(), isa_(INT_TYPE).orElse(jnt(60)),
                                                        uri(URI_TID).maybe(), isa_(INT_TYPE).orElse(jnt(Integer.MAX_VALUE)),
                                                        uri(REAL_TID).maybe(), isa_(INT_TYPE).orElse(jnt(4)),
                                                        uri(BYTES_TID).maybe(), isa_(INT_TYPE).orElse(jnt(60)),
                                                        uri(FAIL_TID).maybe(), isa_(INT_TYPE).orElse(jnt(60))),
                                                uri(JUSTIFY).maybe(), isa_(BOOL_TYPE).orElse(BOOL_TRUE)))
                                        .constructor(instC(INST_CTOR_TID.rng(OBJ_MTRON_SERIALIZER_TID), lst(T(OBJ_MTRON_SERIALIZER_TID)), (lhs, inst) -> ObjmtronSerializer.of(inst.arg(0).asRec(), inst.arg(0).vid())))
                                        .create(), "mtron string serializer",
                                "a serializer with configurable clipping for console display and data marshalling",
                                mutableMap(
                                        uri(f(CLIP).extend(REC_TID)).maybe().asUri(), "the max number of relations",
                                        uri(f(CLIP).extend(LST_TID)).maybe().asUri(), "the max number of elements",
                                        uri(f(CLIP).extend(STR_TID)).maybe().asUri(), "the max number of characters",
                                        uri(f(CLIP).extend(URI_TID)).maybe().asUri(), "the max number of characters for a URI",
                                        uri(f(CLIP).extend(REAL_TID)).maybe().asUri(), "the max number of significant decimal places",
                                        uri(f(CLIP).extend(BYTES_TID)).maybe().asUri(), "the max number of bytes to display",
                                        uri(f(CLIP).extend(FAIL_TID)).maybe().asUri(), "the max number of characters for a fail message",
                                        // uri(f(CLIP).extend(INST_TID)).maybe().asUri(), "the max number of instructions to display",
                                        // uri(f(CLIP).extend(CODE_TID)).maybe().asUri(), "the max number of code statements to display",
                                        uri(JUSTIFY).maybe(), "whether to justify the text left"),
                                "a serializer for converting objs to/from mtron string format",
                                "obj_mtron::[clip=>[m=>[rec=>10]]]"),
                        docWrap(OBJ_SIMPLE_JSON_SERIALIZER_TYPE = Type.Builder.build()
                                        .tid(OBJ_SERIALIZER_TID)
                                        .vid(OBJ_SIMPLE_JSON_SERIALIZER_TID)
                                        .isaPredicate(rec(
                                                uri("wrap_uri").maybe().asUri(), isa_(BOOL_TYPE).orElse(BOOL_TRUE),
                                                uri("bias_towards_uri").maybe(), isa_(BOOL_TYPE).orElse(BOOL_TRUE),
                                                uri("bias_towards_objs").maybe(), isa_(BOOL_TYPE).orElse(BOOL_FALSE),
                                                uri("embed_candq").maybe(), isa_(BOOL_TYPE).orElse(BOOL_FALSE)))
                                        .constructor(instC(INST_CTOR_TID.rng(OBJ_SIMPLE_JSON_SERIALIZER_TID), lst(T(OBJ_SIMPLE_JSON_SERIALIZER_TID)), (lhs, inst) -> MObj.of(inst.arg(0).asRec().jvm(), OBJ_JSON_SERIALIZER_TID, inst.arg(0).vid(), ObjJSONSerializer.class)))
                                        .create(), "simple json serializer",
                                "a serializer for converting objs to/from a simple json format",
                                mutableMap(
                                        uri("wrap_uri").maybe().asUri(), "whether to wrap uris in angle brackets",
                                        uri("bias_towards_uri").maybe().asUri(), "whether to bias ambiguous values towards URI",
                                        uri("bias_towards_objs").maybe().asUri(), "whether to parse arrays as objs instead of lst",
                                        uri("embed_candq").maybe().asUri(), "whether to embed tid coefficient and quality metadata"),
                                "obj_simple_json::[wrap_uri=>true]"),
                        docWrap(OBJ_BSON_SERIALIZER_TYPE = Type.Builder.build()
                                        .tid(OBJ_BSON_SERIALIZER_TID).vid(OBJ_BSON_SERIALIZER_TID).create(),
                                "a serializer for converting objs to/from bson format"),
                        docWrap(OBJ_BYTE_BUFFER_SERIALIZER_TYPE = Type.Builder.build()
                                        .tid(OBJ_SERIALIZER_TID)
                                        .vid(OBJ_BYTE_BUFFER_SERIALIZER_TID)
                                        .constructor(instC(INST_CTOR_TID.rng(OBJ_BYTE_BUFFER_SERIALIZER_TID), lst(T(OBJ_BYTE_BUFFER_SERIALIZER_TID)), (lhs, inst) -> ObjByteBufferSerializer.of(inst.arg(0).asRec(), inst.arg(0).vid())))
                                        .create(), "byte buffer serializer",
                                "a serializer for converting objs to/from raw byte buffers",
                                "obj_bytebuffer::[=>]"),
                        docWrap(HTTP_SPACE_TYPE, """
                                                 a space for reading and writing web-related resources. 
                                                 for http://# patterns and remote routes, uri resolution will fetch remote web resources and httpspace will handle nested addresses client-side. 
                                                 for local routes, uri resolution will fetch from local web server backing httpspace. 
                                                 httpspace webserver is furi aware and will perform server-side extraction of nested addresses.
                                                 """,
                                "*<http://phaseshift.studio>                 [-- yields a html::T < rec::T              --]",
                                "*<http://phaseshift.studio/head/html/title> [-- client-side extraction of str::T title --]",
                                "*<http://localhost:8777/head/html/title>    [-- server-side extraction of str::T title --]"),
                        docWrap(WS_SPACE_TYPE, "a space for exposing and managing web socket servers.",
                                "*<ws://localhost:8999/mtron>               [-- creates a wsmtron server session    --]",
                                "<ws://localhost:8999/mtron/0/send>('ping') [-- sends str to wsmtron server session --]"),
                        /////////////////////////////////////////////////////////////////////////////////////////////////////
                        docWrap(CLIENT_TYPE = Type.Builder.build().tid(REC_TID).vid(CLIENT_TID).create(),
                                "a generic web client which can be refined with useful behaviors"),
                        docWrap(SERVER_TYPE = Type.Builder.build().tid(REC_TID).vid(SERVER_TID).create(),
                                "a generic web server which can be refined with useful behaviors"),
                        docWrap(WS_WEBSOCKET_TYPE, "a generic websocket obj which can be refined with useful behaviors"),
                        docWrap(WS_HANDLER_TYPE, "a websocket server which should be refined to implement protocol specs"),
                        docWrap(WS_CLIENT_TYPE, "an websocket client which should be refined to implement protocol specs"),
                        docWrap(WS_MCP_HANDLER_TYPE, "an abstract mcp websocket handler providing necessary json-rpc infrastructure for mcp servers to leverage"),
                        docWrap(WS_MCP_EMULTATOR_TYPE, "a webLsocket mcp emulator server that provides an agent a stateful environment of mcp server access"),
                        docWrap(WS_MCP_MTRON_HANDLER_TYPE, "an mcp handler server with built-in mtron eval, space listing, router info and instruction listing tools"),
                        docWrap(WS_MTRON_HANDLER_TYPE, "a simple websocket handler accepting mtron expressions and return mtron results", "mtron_ws::[=>]"),
                        /// //////////////////////////////
                        docWrap(HTTP_SOCKET_TYPE, "a generic http obj which can be refined with useful behaviors"),
                        docWrap(HTTP_HANDLER_TYPE, "a http server which should be refined to implement protocol specs"),
                        docWrap(HTTP_CLIENT_TYPE, "an http client which should be refined to implement protocol specs"),
                        docWrap(HTTP_MCP_EMULTATOR_TYPE, "an http mcp emulator server that provides an agent a stateful environment of mcp server access"),
                        docWrap(HTTP_MTRON_HANDLER_TYPE, "a simple http handler accepting mtron expressions and return mtron results", "mtron_http::[=>]"),
                        docWrap(HTTP_MCP_HANDLER_TYPE, "an abstract mcp http handler providing necessary json-rpc infrastructure for mcp servers to leverage"),
                        docWrap(HTTP_MCP_MTRON_TYPE, "mcp streamable http transport handler with built-in metatron tools"),
                        docWrap(WEB_HTTP_HANDLER_TYPE, "a http handler serving web content from a router-backed space"),
                        /// //////////////////////////////
                        docWrap(MCP_SERVER_TYPE = Type.Builder.build()
                                        .tid(SERVER_TID)
                                        .vid(MCP_SERVER_TID)
                                        .isaPredicate(rec(
                                                uri(TOOL).maybe().asUri(), rec(URI_TYPE, INST_TYPE).maybe(),
                                                uri(RESOURCE).maybe().asUri(), T(ALL),
                                                uri(PROMPT).maybe().asUri(), T(ALL)))
                                        .constructor(instC(INST_CTOR_TID.dom(ALL.maybe()).rng(MCP_SERVER_TID), lst(T(REC_TID)), (lhs, inst) ->
                                                new mcpServer(new LinkedHashMap<>(inst.arg(0).asRec().jvm()), MCP_SERVER_TID, inst.arg(0).vid()))).create(),
                                "transport-agnostic mcp json-rpc protocol handler"),
                        docWrap(MCP_CLIENT_TYPE = Type.Builder.build()
                                        .tid(CLIENT_TID)
                                        .vid(MCP_CLIENT_TID)
                                        .isaPredicate(rec(
                                                uri(HOST).maybe().asUri(), URI_TYPE,
                                                uri(TRANSPORT).maybe(), URI_TYPE,
                                                uri(COMMAND).maybe(), LST_TYPE,
                                                uri(ENV).maybe(), rec(URI_TYPE, ALL_TYPE).maybe(),
                                                uri(TOOL).maybe(), rec(URI_TYPE, T(LLM_TOOL_TID)).maybe(),
                                                uri(STATUS).maybe(), isa_(BOOL_TYPE).else_(BOOL_FALSE)))
                                        .constructor(instC(INST_CTOR_TID.dom(ALL.maybe()).rng(MCP_CLIENT_TID), lst(T(REC_TID)),
                                                (x, inst) -> new mcpClient(inst.arg(0).asRec().jvm(), MCP_CLIENT_TID, inst.arg(0).vid())))
                                        .create(), "an mcp client type specification", "a connection to an existing mcp server",
                                Map.of(
                                        uri(HOST).maybe(), "the mcp server endpoint (optional for stdio)",
                                        uri(TRANSPORT).maybe(), "specify the transport if host schema resolution isn't sufficient",
                                        uri(COMMAND).maybe(), "only necessary for stdio mcp servers, where the client is the server",
                                        uri(ENV).maybe(), "environment variables for the stdio server process",
                                        uri(TOOL).maybe(), "the tools/functions available for use on the mcp server",
                                        uri(STATUS).maybe(), "the current status of the mcp client/server connection"),
                                "a client implementing the model content protocol used by llms for the acquisition of tools and access to external software systems",
                                "mcp_client::[host => <http://127.0.0.1:29170/index-mcp/streamable-http>]@/usr/ai/mcp/intellij [-- connection populates tool and status      --]",
                                "mcp_client::[host => <ws://localhost:8999>]@/usr/ai/mcp/mtron                                 [-- mtron router server exposes an mcp server --]")),
                uri(INST), lst(
                        docWrap(instC(WEB_ISA_TID.extend("inst/ping").dom(ALL.maybe()).rng(MATH_TIME_TID), lst(URI_TYPE), (lhs, inst) -> {
                                    final fURI host = inst.arg(0).uriValue().hasScheme() ? inst.arg(0).uriValue() : f("http://" + inst.arg(0).uriValue());
                                    long start = System.currentTimeMillis();
                                    try (final SocketChannel sc = SocketChannel.open()) {
                                        sc.connect(new InetSocketAddress(host.host(), host.port()));
                                        long latency = System.currentTimeMillis() - start;
                                        LOG.info("%s available with latency %d ms", sc.getRemoteAddress(), latency);
                                        return real(Long.valueOf(latency).doubleValue(), MATH_MILLIS_TID, null);
                                    } catch (final Exception e) {
                                        LOG.error("%s unavailable", inst.arg(0).uriValue());
                                        return real(-1.0d, MATH_MILLIS_TID, null);
                                    }
                                }), "maybe an obj", "the mean ping time", Map.of(jnt(0), "the host machine and port to ping"), "ping a machine via tcp",
                                "<http://metatron.phaseshift.studio>.ping(_)",
                                "ping(localhost:8777)",
                                "virtual::[code=>ping(localhost:8777)-<{@x+*0,@y+1},loop=>second::2.0]"),
                        instC(WEB_ISA_TID.extend("inst/format").dom(MARKDOWN_TID).rng(STR_TID), lst(), (lhs, inst) -> str(ObjMarkdownSerializer.format(ObjMarkdownSerializer.single().write(lhs).getChars().toString()))),
                        instC(AS_INST_TID.dom(STR_TID).rng(JSON_TID), lst(JSON_TYPE), (lhs, inst) -> ObjJSONSerializer.parse(lhs.asStr().strValue())),
                        instC(AS_INST_TID.dom(STR_TID).rng(XML_TID), lst(T(XML_TID)), (lhs, inst) -> ObjXMLSerializer.parse(lhs.asStr().strValue())),
                        instC(AS_INST_TID.dom(STR_TID).rng(HTML_TID), lst(HTML_TYPE), (lhs, inst) -> ObjHTMLSerializer.parse(lhs.asStr().strValue())),
                        instC(AS_INST_TID.dom(STR_TID).rng(MARKDOWN_TID), lst(MARKDOWN_TYPE), (lhs, inst) -> ObjMarkdownSerializer.parse(lhs.asStr().strValue())),
                        instC(AS_INST_TID.dom(STR_TID).rng(JAVA_TID), lst(JAVA_TYPE), (obj, inst) -> ObjJavaSerializer.single().inputBytes(obj.strValue().getBytes())),
                        instC(AS_INST_TID.dom(JAVA_TID).rng(STR_TID), lst(STR_TYPE), (obj, inst) -> str(new String(ObjJavaSerializer.single().outputBytes(obj).array()))),
                        instC(AS_INST_TID.dom(HTML_TID).rng(STR_TID), lst(STR_TYPE), (lhs, inst) -> str(ObjHTMLSerializer.single().write(lhs).outerHtml())),
                        instC(AS_INST_TID.dom(MARKDOWN_TID).rng(STR_TID), lst(STR_TYPE), (lhs, inst) -> str(ObjMarkdownSerializer.single().write(lhs).getChars().toString())),
                        instC(AS_INST_TID.dom(MARKDOWN_TID).rng(HTML_TID), lst(HTML_TYPE), (lhs, inst) -> ObjMarkdownSerializer.single().toHTML(ObjMarkdownSerializer.single().write(lhs))),
                        instC(AS_INST_TID.dom(JSON_TID).rng(MCP_CLIENT_TID), lst(MCP_CLIENT_TYPE), (lhs, inst) -> {
                            final Rec next = lhs.clone().asRec();
                            // ── command (str or list) + args (list) → command list ──
                            final List<Obj> merged = new ArrayList<>();
                            if (next.has(COMMAND)) {
                                final Obj cmd = next.at(COMMAND);
                                if (cmd.isStr() || cmd.isUri()) merged.add(cmd);
                                else if (cmd.isLst()) merged.addAll(cmd.lstValue());
                                next.jvm().remove(uri(COMMAND));
                            }
                            if (next.has(ARGS)) {
                                next.at(ARGS).elements().forEach(merged::add);
                                next.jvm().remove(uri(ARGS));
                            }
                            if (!merged.isEmpty())
                                next.at(COMMAND, lst(merged), MUTABLE);
                            // ── env → headers merge (for stdio) ─────────────
                            if (next.has(ENV) && !next.has(HEADERS)) {
                                next.jvm().put(uri(HEADERS), next.jvm().remove(uri(ENV)));
                            } else if (next.has(ENV) && next.has(HEADERS)) {
                                next.at(HEADERS).asRec().jvm().putAll(next.jvm().remove(uri(ENV)).asRec().jvm());
                            }
                            if (next.has(URL)) {
                                next.jvm().put(uri(HOST), next.at(URL));
                            }
                            final mcpClient client = new mcpClient(next.asRec().jvm(), MCP_CLIENT_TID, lhs.vid());
                            return client;
                        }),
                        instC(AS_INST_TID.dom(ALL).rng(STR_TID), lst(JSON_STR_TYPE), (lhs, inst) -> str(ObjJSONSerializer.simple().write(lhs).toString())))))
        ;
        docWrap(this,
                "the world of the web within the metatron",
                "/usr/idea -> *<http://metatron.phaseshift.studio/html/head/title>");

        super.setup();
    }
}