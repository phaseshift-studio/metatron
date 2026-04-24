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
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.io.type.ObjSimpleJSONSerializer;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.web.parser.*;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.*;
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
import static studio.phaseshift.metatron.isa.web.space.ws.server.wsmtronServer.WS_MTRON_SERVER_TYPE;
import static studio.phaseshift.metatron.isa.web.space.ws.wsSpace.WS_SERVER_TYPE;
import static studio.phaseshift.metatron.isa.web.space.ws.wsSpace.WS_SPACE_TYPE;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@JREService(vid = "/m/web")
public class webInstSet extends AbstractInstSet {

    public static final fURI WEB_ISA_TID = MTRON_TID.extend("web");
    public static final fURI INST_TID = WEB_ISA_TID.extend("inst");
    public static final fURI XML_TID = WEB_ISA_TID.extend("xml");
    public static final fURI HTML_TID = WEB_ISA_TID.extend("html");
    public static final fURI JSON_TID = WEB_ISA_TID.extend("json");
    public static final fURI JSON_STR_TID = WEB_ISA_TID.extend("json_str");
    public static final fURI CSS_TID = WEB_ISA_TID.extend("css");
    public static final fURI MARKDOWN_TID = WEB_ISA_TID.extend("markdown");

    public static final Type XML_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(XML_TID).create();
    public static final Type HTML_TYPE = Type.Builder.build()
            .tid(XML_TID)
            .vid(HTML_TID)
            .predicate(isa_(rec(uri(HTML), rec(uri(HEAD), rec(uri(TITLE).maybe().asUri(), STR_TYPE), uri(BODY), REC_TYPE))).tryToInst()).create();
    public static final Type JSON_STR_TYPE = Type.Builder.build()
            .tid(STR_TID)
            .vid(JSON_STR_TID).create();
    public static final Type JSON_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(JSON_TID)
            .predicate(isa_(rec(URI_TYPE, is_(or_(
                    eq_(NOOBJ_TYPE),
                    eq_(BOOL_TYPE),
                    eq_(INT_TYPE),
                    eq_(STR_TYPE),
                    eq_(URI_TYPE),
                    eq_(LST_TYPE),
                    eq_(REC_TYPE))))).tryToInst())
            .constructor(instC(INST_TID.dom(ALL.maybe()).rng(JSON_TID), lst(T(STR_TID)),
                    (lhs, inst) -> ObjSimpleJSONSerializer.parse(lhs.asStr().strValue()))).create();
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
                uri(PATTERN), uri(WEB_ISA_TID.extend(ALL)),
                uri(CONSTQ), lst(
                        ObjXMLSerializer.single(),
                        ObjHTMLSerializer.single(),
                        ObjJSONSerializer.single(),
                        ObjMarkdownSerializer.single(),
                        ObjPlainTextSerializer.single()),
                uri(TYPE), lst(
                        XML_TYPE,
                        docWrap(HTML_TYPE, "a rec encoding of an html document"),
                        docWrap(JSON_TYPE, "a rec encoding of a json document"),
                        CSS_TYPE,
                       // docWrap(MARKDOWN_TYPE, "a rec encoding of a markdown document"),
                        JSON_STR_TYPE,
                        docWrap(HTTP_SPACE_TYPE, """
                                                 a space for reading and writing web-related resources. for http://# patterns and remote routes, uri resolution will fetch remote web resources and httpspace will handle nested addresses client-side. for local routes, uri resolution will fetch from local web server backing httpspace. httpspace webserver is furi aware and will perform server-side extraction of nested addresses.
                                                 """,
                                "*<http://phaseshift.studio>            [-- yields a html::T < rec::T --]",
                                "*<http://phaseshift.studio/head/title> [-- client-side extraction of str::T title --]",
                                "*<http://localhost:8777/head/title>    [-- server-side extraction of str::T title --]"),
                        docWrap(WS_SPACE_TYPE, "a space for exposing and managing web socket servers.",
                                "*<ws://localhost:8999>               [-- yields a ws::T < rec::T --]",
                                "*<ws://phaseshift.studio/head/title> [-- client-side extraction of str::T title --]",
                                "*<ws://localhost:8777/head/title>    [-- server-side extraction of str::T title --]"),
                        docWrap(WS_SERVER_TYPE, "an websocket server written in mtron through respective insts"),
                        docWrap(WS_MTRON_SERVER_TYPE, "a simple websocket server accepting mtron expressions and return mtron results")),
                uri(INST), lst(
                        instC(AS_INST_TID.dom(STR_TID).rng(XML_TID), lst(T(XML_TID)), (lhs, inst) -> ObjXMLSerializer.parse(lhs.asStr().strValue())),
                        instC(AS_INST_TID.dom(STR_TID).rng(HTML_TID), lst(HTML_TYPE), (lhs, inst) -> ObjHTMLSerializer.parse(lhs.asStr().strValue())),
                        instC(AS_INST_TID.dom(STR_TID).rng(MARKDOWN_TID), lst(MARKDOWN_TYPE), (lhs, inst) -> ObjMarkdownSerializer.parse(lhs.asStr().strValue())),
                        instC(AS_INST_TID.dom(HTML_TID).rng(STR_TID), lst(STR_TYPE), (lhs, inst) -> str(ObjHTMLSerializer.single().write(lhs).outerHtml())),
                        instC(AS_INST_TID.dom(MARKDOWN_TID).rng(STR_TID), lst(STR_TYPE), (lhs, inst) -> str(ObjMarkdownSerializer.single().write(lhs).toString())),
                        instC(AS_INST_TID.dom(MARKDOWN_TID).rng(HTML_TID), lst(HTML_TYPE), (lhs, inst) -> ObjMarkdownSerializer.single().toHTML(ObjMarkdownSerializer.single().write(lhs))),
                        instC(AS_INST_TID.dom(STR_TID).rng(JSON_TID), lst(JSON_TYPE), (lhs, inst) -> ObjSimpleJSONSerializer.parse(lhs.asStr().strValue())),
                        instC(AS_INST_TID.dom(ALL).rng(STR_TID), lst(JSON_STR_TYPE), (lhs, inst) -> str(ObjSimpleJSONSerializer.single().write(lhs).toString())),
                        instC(INST_TID.extend("doc").dom(STR_TID).rng(STR_TID), lst(), (lhs, inst) -> {
                            try {
                                final String source = lhs.strValue();
                                final Obj result = new ObjmtronSerializer(55).parse(source).apply();
                                final String resultString = result.isObjs() ?
                                        result.stream()
                                                .map(Obj::toCleanString)
                                                //.map(Highlighter::unformat)
                                                .reduce((a, b) -> a + "%%%" + b)
                                                .orElse("") :
                                        result.toCleanString();
                                //Highlighter.unformat(result.toString());
                                return str(resultString);
                            } catch (final Exception e) {
                                return str(fail(e).toString());
                            }
                        }),
                        instC(INST_TID.extend("doc_json").dom(ALL).rng(STR_TID), lst(), (lhs, inst) -> {
                            try {
                                if(lhs.isStr()) {
                                    final String source = lhs.strValue();
                                    final Obj result = new ObjmtronSerializer(55).parse(source).apply();
                                    return str(ObjSimpleJSONSerializer.single().write(result).toString());
                                } else {
                                    return str(lhs.toShortString());
                                }
                            } catch (final Exception e) {
                                return str(fail(e).toString());
                            }
                        }))));
        docWrap(this,
                "the world of the web within the metatron",
                "/usr/idea -> <http://metatron.phaseshift.studio/html/head/meta>");
        super.setup();
    }
}