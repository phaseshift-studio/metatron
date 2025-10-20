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

package studio.phaseshift.metatron.io.net;

import studio.phaseshift.metatron.lang.obj.*;
import studio.phaseshift.metatron.lang.translate.ObjParser;
import studio.phaseshift.metatron.ui.ObjSerializer;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.nio.ByteBuffer;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ObjByteBufferSerializer implements ObjSerializer<ByteBuffer> {
    @Override
    public ByteBuffer write(final Obj obj) throws MTronException {
        if (obj.isNoObj())
            return ByteBuffer.wrap("noobj".getBytes());
        if (obj.isBool())
            return this.writeBool(obj.as());
        if (obj.isInt())
            return this.writeInt(obj.as());
        if (obj.isReal())
            return this.writeReal(obj.as());
        if (obj.isStr())
            return this.writeStr(obj.as());
        if (obj.isUri())
            return this.writeUri(obj.as());
        if (obj.isLst())
            return this.writeLst(obj.as());
        if (obj.isRec())
            return this.writeRec(obj.as());
        if (obj.isRel())
            return this.writeRel(obj.as());
        if (obj.isInst())
            return this.writeInst(obj.as());
        if (obj.isCode())
            return this.writeCode(obj.as());
        if (obj.isObjs())
            return this.writeObjs(obj.as());
        throw MTronException.of("unknown obj type: ", obj);
    }

    private String handleIds(final Obj obj, final String objString) {
        return obj.tid() + "::" + objString + ((obj.vid() == null) ? "" : ("@" + obj.vid()));
    }

    @Override
    public ByteBuffer writeBool(final Bool dool) {
        return ByteBuffer.wrap(handleIds(dool, dool.jvm().toString()).getBytes());
    }

    @Override
    public ByteBuffer writeStr(final Str str) {
        return ByteBuffer.wrap(handleIds(str, "'" + str.jvm() + "'").getBytes());
    }

    @Override
    public ByteBuffer writeInt(final Int jnt) {
        return ByteBuffer.wrap(handleIds(jnt, jnt.jvm().toString()).getBytes());
    }

    @Override
    public ByteBuffer writeReal(final Real real) {
        return ByteBuffer.wrap(handleIds(real, real.jvm().toString()).getBytes());
    }

    @Override
    public ByteBuffer writeUri(final Uri uri) {
        return ByteBuffer.wrap(handleIds(uri, "<" + uri.jvm() + ">").getBytes());
    }

    @Override
    public ByteBuffer writeLst(final Lst lst) {
        if(lst.isEmpty())
            return ByteBuffer.wrap(handleIds(lst,"[,]").getBytes());
        final String internal = IteratorUtil.stream(lst.elements()).map(o -> new String(this.write(o).array())).reduce(",", (a, b) ->  a + b + ",");
        return ByteBuffer.wrap(handleIds(lst, "[" + internal.substring(1,internal.length()-1) + "]").getBytes());
    }

    @Override
    public ByteBuffer writeRel(final Rel rel) {
        return ByteBuffer.wrap(handleIds(rel, new String(this.write(rel.first()).array()) + "=>" + new String(this.write(rel.second()).array())).getBytes());
    }

    @Override
    public ByteBuffer writeRec(final Rec rec) {
        if(rec.isEmpty())
            return ByteBuffer.wrap(handleIds(rec,"[=>]").getBytes());
        final String internal = IteratorUtil.stream(rec.elements())
                .map(o -> new String(this.write(o.first()).array()) + " => " + new String(this.write(o.second()).array()))
                .reduce(",", (a, b) ->  a + b + ",");

        return ByteBuffer.wrap(handleIds(rec, "[" + internal.substring(1,internal.length()-1)+ "]").getBytes());
    }

    @Override
    public ByteBuffer writeInst(final Inst inst) {
        final String internal = IteratorUtil.stream(inst.args().elements())
                .map(o -> new String(this.write(o).array()))
                .reduce(",", (a, b) -> a + b + ",");
        return ByteBuffer.wrap(handleIds(inst, "(" +
                (internal.length() == 1 ? "" : internal.substring(1,internal.length()-1))+ ")" + (inst.f() == null ? "" : "{" + inst.f() + "}")).getBytes());
    }

    @Override
    public ByteBuffer writeCode(final Code code) {
        final String internal = IteratorUtil.stream(code.insts()).map(i -> new String(this.writeInst(i).array())).reduce(".", (a, b) -> a + b + ".");
        return ByteBuffer.wrap(handleIds(code, "|[" + internal.substring(1,internal.length()-1) + "]|").getBytes());
    }

    @Override
    public ByteBuffer writeObjs(final Objs objs) {
        final String internal = IteratorUtil.stream(objs.objsValue()).map(o -> new String(this.write(o).array())).reduce(",", (a, b) ->  a + b + ",");
        return ByteBuffer.wrap(("{" + internal.substring(1,internal.length()-1) + "}").getBytes());
    }

    @Override
    public Obj read(final ByteBuffer data) throws MTronException {
       // System.out.println(new String(data.array()));
        return ObjParser.m_obj().parse(new String(data.array())).get();
    }
}
