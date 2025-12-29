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

package studio.phaseshift.metatron.lang.core.m.type;

import studio.phaseshift.metatron.furi.fURI;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.*;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MType.T;

public interface Bool extends Mono {
    @Override
    Bool clone(final Object jvm, final fURI tid, final fURI vid);

    @Override
    Boolean jvm();

    default Bool jvm(final Boolean jvm) {
        return this.clone(jvm, this.tid(), this.vid());
    }

    default Bool tid(final fURI tid) {
        return this.clone(this.jvm(), tid, this.vid());
    }

    default Bool vid(final fURI vid) {
        return this.clone(this.jvm(), this.tid(), vid);
    }

    public static final class BoolType {

        public static Set<Inst> insts() {
            return new LinkedHashSet<>(List.of(
                    instC(AS_INST_TID.dom(BOOL_TID).rng(INT_TID), lst(T(INT_TID)), (lhs, inst) -> jnt(lhs.boolValue() ? 1 : 0, inst.arg(0).tid(), lhs.vid())),
                    instC(PLUS_INST_TID.dom(BOOL_TID).rng(BOOL_TID), lst(T(BOOL_TID)), (lhs, inst) -> lhs.jvm(lhs.boolValue() || inst.arg(0).boolValue())),
                    instC(MULT_INST_TID.dom(BOOL_TID).rng(BOOL_TID), lst(T(BOOL_TID)), (lhs, inst) -> lhs.jvm(lhs.boolValue() && inst.arg(0).boolValue()))
            ));
        }

    }

}