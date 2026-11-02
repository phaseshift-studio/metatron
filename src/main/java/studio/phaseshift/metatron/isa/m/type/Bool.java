/*
 * metatron: a distributed virtual machine and language
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

package studio.phaseshift.metatron.isa.m.type;

import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.fURI;

import java.nio.ByteBuffer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MBytes.bytes;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;

public interface Bool extends Mono {

    Bool BOOL_TRUE = bool(true);
    Bool BOOL_FALSE = bool(false);

    // Bool with coefficient {0}: noobj typed as a Bool, for a method that answers a Bool or nothing.
    Bool BOOL_NOOBJ = bool(null, Tokens.BOOL_TID.zero(), null);

    static Bool bool_noobj() {
        return BOOL_NOOBJ;
    }

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

    Bool self(final Boolean jvm, final fURI tid, final fURI vid);

    default Bool zero() {
        return BOOL_FALSE;
    }

    default Bool one() {
        return BOOL_TRUE;
    }

    /*
     * WHAT REMAINS HERE, and why: the ROW-LESS `as` conversions (bool->bool/bytes/int/real). None of them carries
     * a law-table row, so none carries a claim or an inverse -- nothing retires here, and they move to
     * mInsts.bools() when their bodies' imports travel with them. THE ROWED PAIR IS GONE: bool->str is the SOUND
     * direction ("true"/"false" reads back to the same bool) and is declared WITH its inverse beside the Boolean
     * algebra's operations in mInsts.bools(); str->bool is LOSSY (it answers false for every str that does not
     * spell true) and declares no inverse anywhere.
     */
    final class BoolType {

        public static Set<Inst> insts() {
            return new LinkedHashSet<>(List.of(
                    instC(AS_INST_TID.dom(Tokens.BOOL_TID).rng(Tokens.BOOL_TID), lst(BOOL_TYPE), (lhs, inst) -> lhs.tid(inst.arg(0).vidOrTid())),
                    instC(AS_INST_TID.dom(Tokens.BOOL_TID).rng(Tokens.BYTES_TID), lst(BYTES_TYPE), (lhs, inst) -> bytes(lhs.boolValue() ? ByteBuffer.wrap(new byte[]{1}) : ByteBuffer.wrap(new byte[]{0}), inst.arg(0).vidOrTid(), lhs.vid())),
                    instC(AS_INST_TID.dom(Tokens.BOOL_TID).rng(Tokens.INT_TID), lst(T(Tokens.INT_TID)), (lhs, inst) -> jnt(lhs.boolValue() ? 1 : 0, inst.arg(0).vidOrTid(), lhs.vid())),
                    instC(AS_INST_TID.dom(Tokens.BOOL_TID).rng(Tokens.REAL_TID), lst(T(Tokens.REAL_TID)), (lhs, inst) -> real(lhs.boolValue() ? 1.0d : 0.0d, inst.arg(0).vidOrTid(), lhs.vid()))
            ));
        }
    }

}
