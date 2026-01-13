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

package studio.phaseshift.metatron.lang.net.web;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.parser.mParser;
import studio.phaseshift.metatron.lang.core.m.type.Inst;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.Type;
import studio.phaseshift.metatron.lang.core.m.type.impl.MInstSet;
import studio.phaseshift.metatron.lang.sys.console.Highlighter;
import studio.phaseshift.metatron.lang.translator.JSONTranslator;
import studio.phaseshift.metatron.lang.translator.WebTranslator;
import studio.phaseshift.metatron.lang.translator.XMLTranslator;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.AS_INST_TID;
import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.STR_TID;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MType.T;
import static studio.phaseshift.metatron.lang.translator.XMLTranslator.XML_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class webInstSet extends MInstSet {

    public static final fURI WEB_INSTSET_TID = f("/web");
    public static final fURI INST_TID = WEB_INSTSET_TID.extend("inst");
    public static final fURI PAGE_TID = WEB_INSTSET_TID.extend("page");
    public static final fURI JSON_TID = WEB_INSTSET_TID.extend("json");
    public static final fURI CSS_TID = WEB_INSTSET_TID.extend("css");
    private static final WebTranslator WEB_TRANSLATOR = new WebTranslator();
    private static final JSONTranslator JSON_TRANSLATOR = new JSONTranslator();

    public webInstSet(final fURI vid) {
        super(WEB_INSTSET_TID, vid);
    }

    public static webInstSet create() {
        return new webInstSet(fURI.fnull);
    }

    @Override
    public Set<Type> types() {
        return Stream.of(
                webSpace.WEB_TYPE,
                T(PAGE_TID, mParser.parse("?[html=>?[head=>_,body=>_]]")),
                T(CSS_TID)).collect(Collectors.toSet());
    }

    @Override
    public Set<Inst> insts() {
        return Set.of(
                instC(AS_INST_TID.dom(STR_TID).rng(XML_TID), lst(T(XML_TID)), (lhs, inst) -> XMLTranslator.parse(lhs.asStr().strValue())),
                instC(AS_INST_TID.dom(STR_TID).rng(PAGE_TID), lst(T(PAGE_TID)), (lhs, inst) -> WebTranslator.parse(lhs.asStr().strValue())), // TODO: T(HTML_TID)
                instC(AS_INST_TID.dom(STR_TID).rng(JSON_TID), lst(T(JSON_TID)), (lhs, inst) -> JSONTranslator.parse(lhs.asStr().strValue())), // TODO: T(HTML_TID)
                instC(INST_TID.extend("doc").dom(STR_TID).rng(STR_TID), lst(), (lhs, inst) -> {
            LOG.info("processing doc request: %s", lhs);
            try {
                final String source = lhs.strValue();
                final Obj result = mParser.parse(source).apply();
                final String resultString = result.isObjs() ?
                        Highlighter.format(result.elements()
                                .map(Obj::toString)
                                .map(Highlighter::unformat)
                                .reduce((a, b) -> a + "%%%" + b)
                                .orElse("")) :
                        Highlighter.unformat(result.toString());
                return str(resultString);
            } catch (final Exception e) {
                return str(Highlighter.unformat(fail(e).toString()));
            }
        }));
    }
}