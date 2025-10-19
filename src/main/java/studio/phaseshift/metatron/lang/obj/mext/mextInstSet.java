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

package studio.phaseshift.metatron.lang.obj.mext;

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.Inst;
import studio.phaseshift.metatron.lang.obj.MInstSet;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.lang.obj.Type;
import studio.phaseshift.metatron.lang.obj.mtron.MType;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.lang.fURI.ALL;
import static studio.phaseshift.metatron.lang.obj.mtron.MInst.instC;
import static studio.phaseshift.metatron.lang.obj.mtron.MLst.lst;
import static studio.phaseshift.metatron.lang.obj.mtron.MReal.real;
import static studio.phaseshift.metatron.lang.obj.mtron.MType.T;
import static studio.phaseshift.metatron.lang.obj.mtron.mtronFluent.StartLess.*;
import static studio.phaseshift.metatron.lang.obj.mtron.mtronInstSet.*;

/*
@author Marko A. Rodriguez (http://markorodriguez.com)
*/
public class mextInstSet extends MInstSet {

    public static final fURI MEXT_TID = fURI.of("/m/ext");
    public static final fURI VEC_TID = MEXT_TID.extend("vec");
    //public static final fURI RVEC_TID = MEXT_TID.extend("rvec");
    public static final fURI MTRX_TID = MEXT_TID.extend("mtrx");
    public static final fURI CMPLX_TID = MEXT_TID.extend("cmplx");
    public static final fURI IMG_TID = MEXT_TID.extend("img");
    /// ////////////////////////////////////////////////////////////
    public static final fURI MEXT_INST_TID = MEXT_TID.extend("inst");
    public static final fURI DOT_TID = MEXT_INST_TID.extend("dot");
    public static final fURI SQRT_TID = MEXT_INST_TID.extend("sqrt");


    public mextInstSet(final fURI vid) {
        super(MEXT_TID, vid);
    }

    public static mextInstSet of(final fURI vid) {
        return new mextInstSet(vid);
    }

    @Override
    public Set<Obj> consts() {
        return Stream.of(real(Math.sqrt(-1.0d), IMG_TID, IMG_TID)).collect(Collectors.toSet());
    }

    @Override
    public Set<Inst> insts() {
        return Stream.of(
                instC(PLUS_TID.dom(VEC_TID).rng(VEC_TID), lst(T(VEC_TID)), (lhs, inst) -> cross(inst.arg(0)).apply(lhs)),
              //  instC(PLUS_TID.dom(RVEC_TID).rng(RVEC_TID), lst(T(RVEC_TID)), (lhs, inst) -> lhs.value(lhs.<MRealVec>as().value().add(inst.arg(0).<MRealVec>as().value()))),
                instC(SQRT_TID.dom(REAL_TID).rng(REAL_TID), lst(), (lhs, inst) -> lhs.jvm(Math.sqrt(lhs.realValue()))),
                instC(DOT_TID.dom(VEC_TID).rng(ALL), lst(T(VEC_TID)), (lhs, inst) -> {
                            Obj result = null;
                            for (int i = 0; i < lhs.lstValue().size(); i++) {
                                Obj pairwise = mult(inst.arg(0).lstValue().get(i)).apply(lhs.lstValue().get(i));
                                result = result == null ? pairwise : plus(result).apply(pairwise);
                            }
                            return result;
                        }
                )).collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
    }

    @Override
    public Set<Type> types() {
        return Stream.of(T(VEC_TID,isA(T(LST_TID))), MType.of(MTRX_TID), MType.of(CMPLX_TID)).collect(Collectors.toSet());
    }
}