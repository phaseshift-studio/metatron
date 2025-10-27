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

package studio.phaseshift.metatron.vm;

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.Inst;
import studio.phaseshift.metatron.lang.obj.MInstSet;
import studio.phaseshift.metatron.lang.obj.Type;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.lang.fURI.ALL;
import static studio.phaseshift.metatron.lang.fURI.f;
import static studio.phaseshift.metatron.lang.obj.mtron.MInst.instC;
import static studio.phaseshift.metatron.lang.obj.mtron.MLst.lst;
import static studio.phaseshift.metatron.lang.obj.mtron.MType.T;
import static studio.phaseshift.metatron.lang.obj.mtron.mtronInstSet.INT_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class machInstSet extends MInstSet {

    public static final fURI MACH_TID = f("/mach");
    public static final fURI MACH_MONAD_TID = MACH_TID.extend("monad");
    public static final fURI MACH_INST_TID = MACH_TID.extend("inst");
    public static final fURI DROP_TID = MACH_INST_TID.extend("drop");
    public static final fURI PROJECT_TID = MACH_INST_TID.extend("project"); // proj?
    public static final fURI INJECT_TID = MACH_INST_TID.extend("inject"); // inj ?
    public static final fURI EXPAND_TID = MACH_INST_TID.extend("expand"); // TODO: expand tuple body 

    public machInstSet(final fURI vid) {
        super(MACH_TID, vid);
    }

    public static machInstSet of(final fURI vid) {
        return new machInstSet(vid);
    }

    @Override
    public Set<Type> types() {
        return Stream.of(T(MACH_TID), T(MACH_MONAD_TID)).collect(Collectors.toSet());
    }

    @Override
    public Set<Inst> insts() {
        return new LinkedHashSet<>(Set.of(
                instC(DROP_TID.dom(ALL).rng(MACH_MONAD_TID), lst(), (lhs, inst) -> {
                    throw MTronException.of("placeholder error as machine should handle the drop");
                }),
                instC(PROJECT_TID.dom(ALL).rng(ALL), lst(T(INT_TID)), (lhs, inst) -> {
                    if (lhs.jvm() instanceof Tuple)
                        return lhs.<Tuple>jvmAs().project(inst.arg(0).intValue().intValue());
                    else if (inst.arg(0).intValue() == 0)
                        return lhs;
                    else throw MTronException.of("projection larger than tuple: 1 < %d", inst.arg(0).intValue().intValue());
                }),
                instC(INJECT_TID.dom(ALL).rng(ALL), lst(T(INT_TID),T(ALL)), (lhs, inst) -> {
                    if (lhs.jvm() instanceof Tuple)
                        return lhs.jvm(lhs.<Tuple>jvmAs().inject(inst.arg(0).intValue().intValue(), inst.arg(1)));
                    else if (inst.arg(0).intValue() == 0)
                        return lhs.jvm(inst.arg(1).jvm());
                    else throw MTronException.of("injection larger than tuple: 1 < %d", inst.arg(0).intValue().intValue());
                })
        ));
    }
}
