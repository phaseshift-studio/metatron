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

package studio.phaseshift.metatron.lang.util.serial;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.obj.NoObj;
import studio.phaseshift.metatron.lang.core.m.type.*;


import studio.phaseshift.metatron.util.MTronException;

import java.nio.ByteBuffer;

import static studio.phaseshift.metatron.furi.fURI.f;

public interface ObjSerializer<T> {

    public static final fURI OBJ_SERIAL_TID = f("/sys/serial");
    
    ByteBuffer writeBytes(final Obj obj) throws MTronException;

    Obj readBytes(final ByteBuffer bytes) throws MTronException;

    fURI tid();

    default T write(final Obj obj) throws MTronException {
        if (obj instanceof NoObj)
            return this.writeNoObj(obj.as());
        else if (obj instanceof Fail)
            return this.writeFail(obj.as());
        else if (obj instanceof Bool)
            return this.writeBool(obj.as());
        else if (obj instanceof Int)
            return this.writeInt(obj.as());
        else if (obj instanceof Real)
            return this.writeReal(obj.as());
        else if (obj instanceof Str)
            return this.writeStr(obj.as());
        else if (obj instanceof Uri)
            return this.writeUri(obj.as());
        else if (obj instanceof Rel)
            return this.writeRel(obj.as());
        else if (obj instanceof Lst)
            return this.writeLst(obj.as());
        else if (obj instanceof Rec)
            return this.writeRec(obj.as());
        else if (obj instanceof Inst)
            return this.writeInst(obj.as());
        else if (obj instanceof Code)
            return this.writeCode(obj.as());
        else if (obj instanceof Objs)
            return this.writeObjs(obj.as());
        else
            throw MTronException.of("unknown obj class: %s", obj.getClass());
    }

    Obj read(final T data) throws MTronException;

    /// //////////////////////////////

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


    /// ////////////////////////////////

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
