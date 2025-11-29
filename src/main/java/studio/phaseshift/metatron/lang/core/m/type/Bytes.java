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

import studio.phaseshift.metatron.algebra.PlusMonoid;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.type.impl.MBytes;

import java.nio.ByteBuffer;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static studio.phaseshift.metatron.lang.core.m.inst.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.*;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MBytes.bytes;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MType.T;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Bytes extends Mono, PlusMonoid<Bytes> {
    @Override
    Bytes clone(final Object jvm, final fURI tid, final fURI vid);

    @Override
    ByteBuffer jvm();

    default Bytes jvm(final Long jvm) {
        return this.clone(jvm, this.tid(), this.vid());
    }

    default Bytes tid(final fURI tid) {
        return this.clone(this.jvm(), tid, this.vid());
    }

    default Bytes vid(final fURI vid) {
        return this.clone(this.jvm(), this.tid(), vid);
    }

    @Override
    default Bytes c(cInt c) {
        return (Bytes) Mono.super.c(c);
    }

    @Override
    default Bytes zero() {
        return bytes(ByteBuffer.wrap(new byte[0]));
    }

    @Override
    default Bytes plus(final Bytes rhs) {
        final ByteBuffer buffer = ByteBuffer.allocate(this.jvm().remaining() + rhs.jvm().remaining());
        buffer.put(this.jvm().duplicate());
        buffer.put(rhs.jvm().duplicate());
        buffer.flip();
        return this.jvm(buffer);
    }

    default String toHexString() {
        return "0x" + HexFormat.of().formatHex(this.jvm().array());
    }

    default Bytes shift(final Bytes rhs) {
        final ByteBuffer buffer = ByteBuffer.allocate(this.jvm().capacity());
        if (rhs.c().isPos()) {
            buffer.put(rhs.jvm());
            buffer.put(this.jvm().duplicate().slice(0, this.jvm().capacity() - rhs.jvm().array().length));
        } else if (rhs.c().isNeg()) {
            buffer.put(this.jvm().duplicate().position(rhs.bytesValue().remaining()));
            buffer.put(rhs.jvm());
        } else {
            return this;
        }
        buffer.flip();
        return this.jvm(buffer);
    }

    public static final class BytesType {

        public static Set<Inst> insts() {
            return new LinkedHashSet<>(List.of(
                    instC(LSHIFT_INST_TID.dom(BYTES_TID).rng(BYTES_TID), lst(isa_(T(BYTES_TID)).else_(MBytes.bytes(ByteBuffer.wrap(new byte[]{0}))).tryToInst()), (lhs, inst) -> lhs.<Bytes>as().shift(inst.arg(0).c(cInt::neg).as())),
                    instC(RSHIFT_INST_TID.dom(BYTES_TID).rng(BYTES_TID), lst(isa_(T(BYTES_TID)).else_(MBytes.bytes(ByteBuffer.wrap(new byte[]{0}))).tryToInst()), (lhs, inst) -> lhs.<Bytes>as().shift(inst.arg(0).as())),
                    instC(PLUS_INST_TID.dom(BYTES_TID).rng(BYTES_TID), lst(T(BYTES_TID)), (lhs, inst) -> lhs.<Bytes>as().plus(inst.arg(0).as())),
                    instC(AS_INST_TID.dom(BYTES_TID).rng(STR_TID), lst(T(STR_TID)), (lhs, inst) -> str(new String(lhs.bytesValue().array())))
            ));
        }
    }


}
