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
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.ServiceMetadata;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.type.impl.AbstractInstSet;
import studio.phaseshift.metatron.isa.web.parser.HTMLTranslator;
import studio.phaseshift.metatron.isa.web.parser.JSONTranslator;
import studio.phaseshift.metatron.isa.web.parser.XMLTranslator;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.sys.sysInstSet.SYS_SPACE_TID;
import static studio.phaseshift.metatron.isa.web.space.http.httpSpace.HTTP_SPACE_TYPE;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@ServiceMetadata(tid = "/mtron/web")
public class webInstSet extends AbstractInstSet {

    public static final fURI WEB_ISA_TID = MTRON_TID.extend("web");
    public static final fURI INST_TID = WEB_ISA_TID.extend("inst");
    public static final fURI XML_TID = WEB_ISA_TID.extend("xml");
    public static final fURI HTML_TID = WEB_ISA_TID.extend("html");
    public static final fURI JSON_TID = WEB_ISA_TID.extend("json");
    public static final fURI CSS_TID = WEB_ISA_TID.extend("css");

    public static final Type XML_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(XML_TID).create();
    public static final Type HTML_TYPE = Type.Builder.build()
            .tid(XML_TID)
            .vid(HTML_TID)
            .predicate(isa_(rec(uri("html"), rec(uri("head"), REC_TYPE, uri("body"), REC_TYPE))).tryToInst()).create();
    public static final Type JSON_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(JSON_TID).create();
    public static final Type CSS_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(CSS_TID).create();

    public webInstSet(final fURI vid) {
        super(WEB_ISA_TID, vid);
    }

    public webInstSet() {
        this(SYS_SPACE_TID.extend("web"));
    }

    @Override
    public Set<Type> types() {
        return Stream.of(
                HTTP_SPACE_TYPE,
                XML_TYPE,
                HTML_TYPE,
                JSON_TYPE,
                CSS_TYPE).collect(Collectors.toSet());
    }

    @Override
    public Set<Inst> insts() {
        return Set.of(
                instC(AS_INST_TID.dom(STR_TID).rng(XML_TID), lst(T(XML_TID)), (lhs, inst) -> XMLTranslator.parse(lhs.asStr().strValue())),
                instC(AS_INST_TID.dom(STR_TID).rng(HTML_TID), lst(T(HTML_TID)), (lhs, inst) -> HTMLTranslator.parse(lhs.asStr().strValue())),
                instC(AS_INST_TID.dom(STR_TID).rng(JSON_TID), lst(T(JSON_TID)), (lhs, inst) -> JSONTranslator.parse(lhs.asStr().strValue())),
                instC(INST_TID.extend("doc").dom(STR_TID).rng(STR_TID), lst(), (lhs, inst) -> {
                    LOG.trace("processing doc request: %s", lhs);
                    try {
                        final String source = lhs.strValue();
                        final Obj result = mParser.parse(source).apply();
                        final String resultString = result.isObjs() ?
                                result.elements()
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
                }));
    }
}