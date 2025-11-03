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

package studio.phaseshift.metatron.lang.mach;

import studio.phaseshift.metatron.algebra.MultMonoid;
import studio.phaseshift.metatron.algebra.PlusMonoid;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.mtron.mtronInstSet;
import studio.phaseshift.metatron.lang.mtron.type.Inst;
import studio.phaseshift.metatron.lang.mtron.type.Type;
import studio.phaseshift.metatron.lang.mtron.type.impl.MInstSet;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.mtron.mtronInstSet.INT_TID;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MInst.instB;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MInst.instC;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MType.T;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class machInstSet extends MInstSet {

    public static final fURI MACH_TID = f("/mach");
    public static final fURI MACH_INE_TID = MACH_TID.extend("ine");
    public static final fURI MACH_MONAD_TID = MACH_TID.extend("monad");
    public static final fURI INST_TID = MACH_TID.extend("inst");
    public static final fURI DROP_TID = INST_TID.extend("drop");
    public static final fURI PROJECT_TID = INST_TID.extend("project"); // proj?
    public static final fURI INJECT_TID = INST_TID.extend("inject"); // inj ?
    public static final fURI EXPAND_TID = INST_TID.extend("expand"); // TODO: expand tuple body 
    public static final fURI RING_ZERO_TID = INST_TID.extend("ring").extend("const").extend("zero");
    public static final fURI RING_ONE_TID = INST_TID.extend("ring").extend("const").extend("one");
    public static final fURI RING_BINARY = INST_TID.extend("ring").extend("op").extend("+");

    public machInstSet(final fURI vid) {
        super(MACH_TID, vid);
    }

    public static machInstSet create() {
        return new machInstSet(fURI.NULL);
    }

    @Override
    public Set<Type> types() {
        return Stream.of(T(MACH_INE_TID), T(MACH_MONAD_TID)).collect(Collectors.toSet());
    }

    @Override
    public Set<Inst> insts() {
        return new LinkedHashSet<>(List.of(
                instC(RING_ZERO_TID.dom(A).rng(A), lst(), (lhs, inst) -> ((PlusMonoid.O<?>) lhs).zero()),
                instC(RING_ONE_TID.dom(A).rng(A), lst(), (lhs, inst) -> ((MultMonoid.O<?>) lhs).one()),
               // instC(RING_BINARY.dom(A).rng(ALL.dom(A).rng(A)), lst(), (lhs, inst) -> instB(mtronInstSet.INST_TID.extend(inst.tid().name()), lst(lhs.type())).resolve(lhs)),
                //instC(RING_BINARY.dom(A).rng(ALL.dom(A).rng(A)), lst(T(A)), (lhs, inst) -> instB(mtronInstSet.INST_TID.extend(inst.tid().name()), inst.args()).apply(lhs)),
                instC(DROP_TID.dom(ALL).rng(MACH_MONAD_TID), lst(), (lhs, inst) -> {
                    throw MTronException.of("placeholder error as machine should handle the drop");
                }),
                instC(PROJECT_TID.dom(ALL).rng(ALL), lst(T(INT_TID)), (lhs, inst) -> {
                    if (lhs.jvm() instanceof Tuple)
                        return lhs.<Tuple>jvmAs().project(inst.arg(0).intValue().intValue());
                    else if (inst.arg(0).intValue() == 0)
                        return lhs;
                    else
                        throw MTronException.of("projection larger than tuple: 1 < %d", inst.arg(0).intValue().intValue());
                }),
                instC(INJECT_TID.dom(ALL).rng(ALL), lst(T(INT_TID), T(ALL)), (lhs, inst) -> {
                    if (lhs.jvm() instanceof Tuple)
                        return lhs.jvm(lhs.<Tuple>jvmAs().inject(inst.arg(0).intValue().intValue(), inst.arg(1)));
                    else if (inst.arg(0).intValue() == 0)
                        return lhs.jvm(inst.arg(1).jvm());
                    else
                        throw MTronException.of("injection larger than tuple: 1 < %d", inst.arg(0).intValue().intValue());
                })
        ));
    }
}
