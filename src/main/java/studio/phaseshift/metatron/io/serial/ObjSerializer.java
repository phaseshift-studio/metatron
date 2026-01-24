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

package studio.phaseshift.metatron.io.serial;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.*;

import studio.phaseshift.metatron.isa.m.type.Machine;
import studio.phaseshift.metatron.isa.m.type.Monad;
import studio.phaseshift.metatron.lang.sys.router.impl.FutureObj;
import studio.phaseshift.metatron.util.MTronException;

import java.nio.ByteBuffer;

import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;

public interface ObjSerializer<T> {

    fURI OBJ_SERIAL_TID = f("/sys/serial");

    ByteBuffer outputBytes(final Obj obj) throws MTronException;

    Obj inputBytes(final ByteBuffer bytes) throws MTronException;

    fURI tid();

    default T write(final Obj obj) throws MTronException {
        try {
            return switch (obj) {
                case null -> this.writeNoObj(noobj());
                case NoObj objs -> this.writeNoObj(obj.as());
                case Bytes objs -> this.writeBytes(obj.as());
                case Fail objs -> this.writeFail(obj.as());
                case Bool objs -> this.writeBool(obj.as());
                case Int objs -> this.writeInt(obj.as());
                case Real objs -> this.writeReal(obj.as());
                case Str objs -> this.writeStr(obj.as());
                case Uri objs -> this.writeUri(obj.as());
                case Rel objs -> this.writeRel(obj.as());
                case Lst objs -> this.writeLst(obj.as());
                case Rec objs -> this.writeRec(obj.as());
                case Inst objs -> this.writeInst(obj.as());
                case Code objs -> this.writeCode(obj.as());
                case Objs objs -> this.writeObjs(obj.as());
                case Type objs -> this.writeType(obj.as());
                case Monad objs -> this.writeMonad(obj.as());
                case Machine objs -> this.writeMachine(obj.as());
                case FutureObj<?> objs -> this.write(objs.get(5000));
                default -> throw MTronException.of("unknown obj class: %s", obj.getClass());
            };
        } catch(final Exception e) {
            throw MTronException.of(e);
        }
    }

    Obj read(final T data) throws MTronException;

    /// //////////////////////////////

    default T writeBytes(final Bytes b) {
        return this.write(b);
    }

    default T writeNoObj(final NoObj n) {
        return this.write(n);
    }

    default T writeFail(final Fail f) {
        return this.write(f);
    }

    default T writeBool(final Bool b) {
        return this.write(b);
    }

    default T writeInt(final Int i) {
        return this.write(i);
    }

    default T writeReal(final Real r) {
        return this.write(r);
    }

    default T writeStr(final Str s) {
        return this.write(s);
    }

    default T writeUri(final Uri u) {
        return this.write(u);
    }

    default T writeRel(final Rel r) {
        return this.write(r);
    }

    default T writeLst(final Lst l) {
        return this.write(l);
    }

    default T writeRec(final Rec r) {
        return this.write(r);
    }

    default T writeInst(final Inst i) {
        return this.write(i);
    }

    default T writeCode(final Code c) {
        return this.write(c);
    }

    default T writeObjs(final Objs o) {
        return this.write(o);
    }

    default T writeType(final Type t) {
        return this.write(t);
    }

    default T writeMonad(final Monad m) {
        return this.write(m);
    }

    default T writeMachine(final Machine m) {
        return this.write(m);
    }


    /// ////////////////////////////////

    default Fail readFail(final T t) {
        return (Fail) this.read(t);
    }

    default Bytes readBytes(final T t) {
        return (Bytes) this.read(t);
    }


    default Bool readBool(final T t) {
        return (Bool) this.read(t);
    }

    default Int readInt(final T t) {
        return (Int) this.read(t);
    }

    default Real readReal(final T t) {
        return (Real) this.read(t);
    }

    default Str readStr(final T t) {
        return (Str) this.read(t);
    }

    default Uri readUri(final T t) {
        return (Uri) this.read(t);
    }

    default Rel readRel(final T t) {
        return (Rel) this.read(t);
    }

    default Lst readLst(final T t) {
        return (Lst) this.read(t);
    }

    default Rec readRec(final T t) {
        return (Rec) this.read(t);
    }

    default Inst readInst(final T t) {
        return (Inst) this.read(t);
    }

    default Code readCode(final T t) {
        return (Code) this.read(t);
    }
}
