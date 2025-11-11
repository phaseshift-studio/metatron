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

package studio.phaseshift.metatron.lang.db.vec;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.type.Inst;
import studio.phaseshift.metatron.lang.core.m.type.Lst;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.Type;
import studio.phaseshift.metatron.lang.core.m.type.impl.MInstSet;
import studio.phaseshift.metatron.lang.net.web.JSONTranslator;
import studio.phaseshift.metatron.util.MTronException;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.core.m.inst.mFluent.StartLess.*;
import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.*;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MType.T;

/*
@author Marko A. Rodriguez (http://markorodriguez.com)
*/
public class vecInstSet extends MInstSet {

    public static final fURI MVEC_TID = f("/vec");
    public static final fURI VEC_TID = MVEC_TID.extend("vec");
    //public static final fURI RVEC_TID = MEXT_TID.extend("rvec");
    public static final fURI MTRX_TID = MVEC_TID.extend("mtrx");
    public static final fURI CMPLX_TID = MVEC_TID.extend("cmplx");
    public static final fURI IMG_TID = MVEC_TID.extend("img");
    /// ////////////////////////////////////////////////////////////
    public static final fURI INST_TID = MVEC_TID.extend("inst");
    public static final fURI DOT_TID = INST_TID.extend("dot");
    public static final fURI SQRT_TID = INST_TID.extend("sqrt");
    public static final fURI JSON_TID = INST_TID.extend("json");
    private static final JSONTranslator JSON_TRANSLATOR = new JSONTranslator();

    public vecInstSet(final fURI vid) {
        super(MVEC_TID, vid);
    }

    public static vecInstSet create() {
        return new vecInstSet(fURI.NULL);
    }

    @Override
    public Set<Obj> consts() {
        return Stream.of(real(Math.sqrt(-1.0d), IMG_TID, IMG_TID)).collect(Collectors.toSet());
    }

    @Override
    public Set<Inst> insts() {
        return Stream.of(
                instC(JSON_TID.dom(STR_TID).rng(ALL), lst(), (lhs, inst) -> JSON_TRANSLATOR.translateString(lhs.strValue())),
                instC(PLUS_TID.dom(VEC_TID).rng(VEC_TID), lst(T(VEC_TID)), (lhs, inst) -> cross_(inst.arg(0)).apply(lhs)),
                //  instC(PLUS_TID.dom(RVEC_TID).rng(RVEC_TID), lst(T(RVEC_TID)), (lhs, inst) -> lhs.value(lhs.<MRealVec>as().value().add(inst.arg(0).<MRealVec>as().value()))),
                instC(SQRT_TID.dom(REAL_TID).rng(REAL_TID), lst(), (lhs, inst) -> lhs.jvm(Math.sqrt(lhs.realValue()))),
                instC(DOT_TID.dom(VEC_TID).rng(ALL), lst(T(VEC_TID)), (lhs, inst) -> {
                            Obj result = null;
                            if (lhs.<Lst>as().count() != inst.arg(0).<Lst>as().count())
                                throw MTronException.of("dot product requires equal length vecs: %d != %d", lhs.<Lst>as().count(), inst.arg(0).<Lst>as().count());
                            for (int i = 0; i < lhs.lstValue().size(); i++) {
                                Obj pairwise = mult_(inst.arg(0).lstValue().get(i)).apply(lhs.lstValue().get(i));
                                result = result == null ? pairwise : plus_(result).apply(pairwise);
                            }
                            return result;
                        }
                )).collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
    }

    @Override
    public Set<Type> types() {
        return Stream.of(T(VEC_TID, isa_(T(LST_TID))), T(MTRX_TID), T(CMPLX_TID)).collect(Collectors.toSet());
    }
}