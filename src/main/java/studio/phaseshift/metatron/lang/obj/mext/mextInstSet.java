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
import studio.phaseshift.metatron.lang.obj.mtron.mtronInstSet;
import studio.phaseshift.metatron.space.Router;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.lang.fURI.ALL;
import static studio.phaseshift.metatron.lang.fURI.f;
import static studio.phaseshift.metatron.lang.obj.mtron.MInst.instC;
import static studio.phaseshift.metatron.lang.obj.mtron.MLst.lst;
import static studio.phaseshift.metatron.lang.obj.mtron.MType.T;
import static studio.phaseshift.metatron.lang.obj.mtron.mtronFluent.StartLess.*;
import static studio.phaseshift.metatron.lang.obj.mtron.mtronInstSet.PLUS_TID;

/*
@author Marko A. Rodriguez (http://markorodriguez.com)
*/
public class mextInstSet extends MInstSet {

    public static final fURI MEXT_TID = fURI.of("/mext");
    public static final fURI COMPLEX_TID = MEXT_TID.extend("cmplx");
    private static Set<fURI> MEXT_TYPES = Set.of(COMPLEX_TID);
    public static final fURI VEC_TID = MEXT_TID.extend("vec");
    public static final fURI MEXT_INST_TID = MEXT_TID.extend("inst");
    public static final fURI ID_TID = MEXT_INST_TID.extend("id");
    //
    public static final fURI DOT_TID = MEXT_INST_TID.extend("dot");


    public mextInstSet(final fURI vid) {
        super(new HashMap<>(), MEXT_TID, vid);
        this.types().forEach(t -> Router.global().registerRewrite(f(t.tid().name()), t.tid()));
        this.insts().forEach(t -> Router.global().registerRewrite(f(t.tid().name()), t.tid().basePath()));
    }

    public static mextInstSet of(final fURI vid) {
        return new mextInstSet(vid);
    }

    @Override
    public Set<Inst> insts() {
        return Stream.of(
                instC(PLUS_TID.dom(VEC_TID).rng(VEC_TID), lst(T(VEC_TID)), (lhs, inst) -> cross(inst.arg(0)).apply(lhs)),
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
        return Stream.of(MType.of(VEC_TID)).collect(Collectors.toSet());
    }
}