/*
 * Metatron: A Distributed Computing Language and Virtual Machine
 * Copyright (C) 2025- PhaseShift Studio, LLC
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

package studio.phaseshift.metatron.lang.mweb;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.mtron.type.Inst;
import studio.phaseshift.metatron.lang.mtron.type.Type;
import studio.phaseshift.metatron.lang.mtron.type.impl.MInstSet;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.mtron.mtronFluent.StartLess.id_;
import static studio.phaseshift.metatron.lang.mtron.mtronFluent.StartLess.isa_;
import static studio.phaseshift.metatron.lang.mtron.mtronInstSet.URI_TID;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MInst.instC;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MRec.rec;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MType.T;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MUri.uri;
import static studio.phaseshift.metatron.lang.mweb.mwebSpace.WEB_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mwebInstSet extends MInstSet {

    public static final fURI MWEB_TID = f("/mweb");
    public static final fURI MWEB_INST_TID = MWEB_TID.extend("inst");
    public static final fURI PAGE_TID = MWEB_TID.extend("page");
    public static final fURI CSS_TID = MWEB_TID.extend("css");
    private static final WebTranslator WEB_TRANSLATOR = new WebTranslator();
    private static final JSONTranslator JSON_TRANSLATOR = new JSONTranslator();

    public mwebInstSet(final fURI vid) {
        super(MWEB_TID, vid);
    }

    public static mwebInstSet of(final fURI vid) {
        return new mwebInstSet(vid);
    }

    @Override
    public Set<Inst> insts() {
        return Set.of(instC(MWEB_INST_TID.extend("mweb").dom(URI_TID).rng(WEB_TID),
                rec(uri("authority"), T(URI_TID), uri("pattern"), T(URI_TID)), (lhs, inst) -> {
                    return mwebSpace.of(inst.arg("authority").uriValue(), inst.arg("pattern").uriValue(), fURI.NULL);
                }));//Stream.of().collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
    }

    @Override
    public Set<Type> types() {
        return Stream.of(T(PAGE_TID, isa_(rec(uri("html"), rec(uri("head"), id_().tryToInst(), uri("body"), id_().tryToInst())))), T(CSS_TID)).collect(Collectors.toSet());
    }
}