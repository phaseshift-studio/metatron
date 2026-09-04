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

package studio.phaseshift.metatron.isa.mach.io.type;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.mach.type.PCMonad;
import studio.phaseshift.metatron.isa.mach.type.thread.FutureObj;
import studio.phaseshift.metatron.util.MTronException;

import java.nio.ByteBuffer;

import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;

public interface ObjSerializer<T> extends Rec {

    fURI OBJ_SERIAL_TID = fURI.Singleton.f("/m/mach/io");
    fURI OBJ_MTRON_SERIALIZER_TID = OBJ_SERIAL_TID.extend("serializer").extend("mtron");
    fURI OBJ_BYTE_BUFFER_SERIALIZER_TID = OBJ_SERIAL_TID.extend("serializer").extend("bytebuffer");
    fURI OBJ_SIMPLE_JSON_SERIALIZER_TID = OBJ_SERIAL_TID.extend("serializer").extend("json").extend("simple");
    fURI OBJ_MTRON_STRING_SERIALIZER_VID = OBJ_SERIAL_TID.extend("serializer").extend("string").extend("clean");
    fURI OBJ_BYTE_BUFFER_SERIALIZER_VID = OBJ_SERIAL_TID.extend("serializer").extend("bytebuffer");
    fURI OBJ_SIMPLE_JSON_SERIALIZER_VID = OBJ_SERIAL_TID.extend("serializer").extend("json").extend("simple");

    ByteBuffer outputBytes(final Obj obj) throws MTronException;

    Obj inputBytes(final ByteBuffer bytes) throws MTronException;

    default Obj inputBytes(final byte[] bytes) {
        return this.inputBytes(ByteBuffer.wrap(bytes));
    }

    default Obj inputBytes(final String stringToBytes) {
        return this.inputBytes(ByteBuffer.wrap(stringToBytes.getBytes()));
    }

    default T write(final Obj obj) throws MTronException {
        try {
            if (null == obj || obj.isNoObj())
                return this.writeNoObj(noobj());
            return switch (obj) {
                case NoObj objs -> this.writeNoObj(objs);
                case Bytes objs -> this.writeBytes(objs);
                case Fail objs -> this.writeFail(objs);
                case Bool objs -> this.writeBool(objs);
                case Int objs -> this.writeInt(objs);
                case Real objs -> this.writeReal(objs);
                case Str objs -> this.writeStr(objs);
                case Uri objs -> this.writeUri(objs);
                case Rel objs -> this.writeRel(objs);
                case PCMonad objs -> this.writeMonad(objs);
                case Lst objs -> this.writeLst(objs);
                case Rec objs -> this.writeRec(objs);
                case Inst objs -> this.writeInst(objs);
                case Code objs -> this.writeCode(objs);
                case Objs objs -> this.writeObjs(objs);
                case Type objs -> this.writeType(objs);

                case FutureObj<?> objs -> this.write(objs.get(5000));
                default -> throw MTronException.of("unknown obj class: %s", obj.getClass());
            };
        } catch (final Exception e) {
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

    default T writeMonad(final PCMonad m) {
        return this.write(m);
    }


    /// ////////////////////////////////

    default Fail readFail(final T t) {
        return (Fail) this.read(t);
    }

    private static <O extends Obj> O castOrName(final Obj read, final String expected) {
        // a fail read back must surface its own reason — a raw cast would
        // surface as a bare ClassCastException with the original cause lost
        if (read instanceof Fail fail)
            throw MTronException.of("read expected %s, got fail: %s", expected, fail.message());
        return (O) read;
    }

    default Bytes readBytes(final T t) {
        return castOrName(this.read(t), "bytes");
    }


    default Bool readBool(final T t) {
        return castOrName(this.read(t), "bool");
    }


    default Objs readObjs(final T t) {
        return castOrName(this.read(t), "objs");
    }

    default Int readInt(final T t) {
        return castOrName(this.read(t), "int");
    }

    default Real readReal(final T t) {
        return castOrName(this.read(t), "real");
    }

    default Str readStr(final T t) {
        return castOrName(this.read(t), "str");
    }

    default Uri readUri(final T t) {
        return castOrName(this.read(t), "uri");
    }

    default Rel readRel(final T t) {
        return castOrName(this.read(t), "rel");
    }

    default Lst readLst(final T t) {
        return castOrName(this.read(t), "lst");
    }

    default Rec readRec(final T t) {
        return castOrName(this.read(t), "rec");
    }

    default Inst readInst(final T t) {
        return castOrName(this.read(t), "inst");
    }

    default Code readCode(final T t) {
        return castOrName(this.read(t), "code");
    }
}
