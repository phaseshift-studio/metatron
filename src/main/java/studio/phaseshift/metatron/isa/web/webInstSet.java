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

package studio.phaseshift.metatron.isa.web;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.io.type.ObjSimpleJSONSerializer;
import studio.phaseshift.metatron.isa.web.parser.*;
import studio.phaseshift.metatron.isa.web.type.Content;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.inside_;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Int.INT_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Lst.LST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.NOOBJ_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.space.http.httpSpace.HTTP_SPACE_TYPE;
import static studio.phaseshift.metatron.isa.web.space.http.mcp_httpHandler.MCP_HTTP_TYPE;
import static studio.phaseshift.metatron.isa.web.space.http.mcp_mtron_httpHandler.MCP_MTRON_HTTP_TYPE;
import static studio.phaseshift.metatron.isa.web.space.ws.server.mcp_mtron_wsHandler.WS_MCP_MTRON_HANDLER_TYPE;
import static studio.phaseshift.metatron.isa.web.type.mcp_Server.MCP_SERVER_TYPE;
import static studio.phaseshift.metatron.isa.web.space.ws.server.mcp_wsHandler.WS_MCP_HANDLER_TYPE;
import static studio.phaseshift.metatron.isa.web.space.ws.server.mtron_wsServer.WS_MTRON_SERVER_TYPE;
import static studio.phaseshift.metatron.isa.web.space.ws.wsSpace.*;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@JREService(vid = "/m/web")
public class webInstSet extends AbstractInstSet {

    public static final fURI WEB_ISA_TID = M_ISA_TID.extend("web");
    public static final fURI INST_TID = WEB_ISA_TID.extend("inst");
    public static final fURI XML_TID = WEB_ISA_TID.extend("xml");
    public static final fURI HTML_TID = WEB_ISA_TID.extend("html");
    public static final fURI JSON_TID = WEB_ISA_TID.extend("json");
    public static final fURI JSON_STR_TID = WEB_ISA_TID.extend("json_str");
    public static final fURI CSS_TID = WEB_ISA_TID.extend("css");
    public static final fURI MARKDOWN_TID = WEB_ISA_TID.extend("markdown");
    public static final fURI CONTENT_TYPE_TID = WEB_ISA_TID.extend("content_type");

    public static final Type CONTENT_TYPE = Type.Builder.build()
            .tid(URI_TID)
            .vid(CONTENT_TYPE_TID)
            .isaPredicate(inside_(lst(
                    uri(Content.ContentType.TEXT_PLAIN.value),
                    uri(Content.ContentType.TEXT_HTML.value),
                    uri(Content.ContentType.TEXT_CSS.value),
                    uri(Content.ContentType.TEXT_MARKDOWN.value),
                    uri(Content.ContentType.TEXT_JAVASCRIPT.value),
                    uri(Content.ContentType.APPLICATION_MTRON.value),
                    uri(Content.ContentType.TEXT_X_SHELLSCRIPT.value),
                    uri(Content.ContentType.APPLICATION_JSON.value))))
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
    public static final Type JSON_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(JSON_TID)
            .isaPredicate(rec(URI_TYPE, inside_(lst(
                    NOOBJ_TYPE,
                    BOOL_TYPE,
                    INT_TYPE,
                    STR_TYPE,
                    URI_TYPE,
                    LST_TYPE,
                    REC_TYPE)))).create();
    public static final Type CSS_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(CSS_TID).create();
    public static final Type MARKDOWN_TYPE = Type.Builder.build().tid(REC_TID).vid(MARKDOWN_TID).create();

    public webInstSet() {
        super(mutableMap(uri(PATTERN), uri(WEB_ISA_TID.extend(ALL))), INSTSET_TID, WEB_ISA_TID);
    }

    @Override
    public void setup() {
        this.jvm().putAll(mutableMap(
                uri(CONST), lst(
                        ObjXMLSerializer.single(),
                        ObjHTMLSerializer.single(),
                        ObjJSONSerializer.single(),
                        ObjMarkdownSerializer.single(),
                        ObjPlainTextSerializer.single()),
                uri(TYPE), lst(
                        docWrap(CONTENT_TYPE, "indicates the media type of the data as specified by RFC-9110"),
                        docWrap(XML_TYPE, "a rec encoding of an xml document"),
                        docWrap(HTML_TYPE, "a rec encoding of an html document",
                        "*<http://metatron.phaseshift.studio> [-- yields an html::T --]",
                        """
                        html::[html=>
                               [head=>
                                [title=>\"metatron\"]],
                                body=>
                                 [out=>[
                                  [tag=>a,href=>...],
                                  [tag...]]]]"""),
                        docWrap(JSON_TYPE, "a rec encoding of a json document"),
                        docWrap(CSS_TYPE, "a rec encoding of a css document"),
                        docWrap(MARKDOWN_TYPE, "a rec encoding of a markdown document"),
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
                        docWrap(WS_WEBSOCKET_TYPE, "a generic websocket obj which can be refined with useful behaviors"),
                        docWrap(WS_SERVER_TYPE, "a websocket server which should be refined to implement protocol specs"),
                        docWrap(WS_CLIENT_TYPE, "an websocket client which should be refined to implement protocol specs"),
                        docWrap(WS_MTRON_SERVER_TYPE, "a simple websocket server accepting mtron expressions and return mtron results","mtron_ws::[=>]"),
                        docWrap(WS_MCP_HANDLER_TYPE, "an abstract mcp websocket server providing necessary json-rpc infrastructure for other mcp servers to leverage"),
                        docWrap(WS_MCP_MTRON_HANDLER_TYPE, "an mcp websocket server with built-in metatron eval, space listing, router info and instruction listing tools"),
                        docWrap(MCP_SERVER_TYPE, "transport-agnostic MCP JSON-RPC protocol handler"),
                        docWrap(MCP_HTTP_TYPE, "MCP Streamable HTTP transport handler"),
                        docWrap(MCP_MTRON_HTTP_TYPE, "MCP Streamable HTTP transport handler with built-in metatron tools")),
                uri(INST), lst(
                        instC(AS_INST_TID.dom(STR_TID).rng(XML_TID), lst(T(XML_TID)), (lhs, inst) -> ObjXMLSerializer.parse(lhs.asStr().strValue())),
                        instC(AS_INST_TID.dom(STR_TID).rng(HTML_TID), lst(HTML_TYPE), (lhs, inst) -> ObjHTMLSerializer.parse(lhs.asStr().strValue())),
                        instC(AS_INST_TID.dom(STR_TID).rng(MARKDOWN_TID), lst(MARKDOWN_TYPE), (lhs, inst) -> ObjMarkdownSerializer.parse(lhs.asStr().strValue())),
                        instC(AS_INST_TID.dom(HTML_TID).rng(STR_TID), lst(STR_TYPE), (lhs, inst) -> str(ObjHTMLSerializer.single().write(lhs).outerHtml())),
                        instC(AS_INST_TID.dom(MARKDOWN_TID).rng(STR_TID), lst(STR_TYPE), (lhs, inst) -> str(ObjMarkdownSerializer.single().write(lhs).getChars().toString())),
                        instC(AS_INST_TID.dom(MARKDOWN_TID).rng(HTML_TID), lst(HTML_TYPE), (lhs, inst) -> ObjMarkdownSerializer.single().toHTML(ObjMarkdownSerializer.single().write(lhs))),
                        instC(AS_INST_TID.dom(STR_TID).rng(JSON_TID), lst(JSON_TYPE), (lhs, inst) -> ObjSimpleJSONSerializer.parse(lhs.asStr().strValue())),
                        instC(AS_INST_TID.dom(ALL).rng(STR_TID), lst(JSON_STR_TYPE), (lhs, inst) -> str(ObjSimpleJSONSerializer.single().write(lhs).toString())))));
        docWrap(this,
                "the world of the web within the metatron",
                "/usr/idea -> *<http://metatron.phaseshift.studio/html/head/title>");
        super.setup();
    }
}