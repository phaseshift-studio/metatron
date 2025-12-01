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

package studio.phaseshift.metatron.lang.util.serial;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.obj.NoObj;
import studio.phaseshift.metatron.lang.core.m.type.*;


import studio.phaseshift.metatron.lang.core.m.parser.mParser;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ObjByteBufferSerializer implements ObjSerializer<ByteBuffer> {

    private static final ByteBuffer NOOBJ_BYTES = ByteBuffer.wrap("noobj".getBytes());

    @Override
    public fURI tid() {
        return OBJ_SERIAL_TID.extend("bytes");
    }

    @Override
    public ByteBuffer writeBytes(final Obj obj) {
        return this.write(obj);
    }

    @Override
    public Obj readBytes(final ByteBuffer bytes) {
        return this.read(bytes);
    }

    private String handleIds(final Obj obj, final String objString) {
        return (obj.tid() + "::" + objString + ((obj.vid() == null) ? "" : ("@<" + obj.vid() + ">"))).trim();
    }

    @Override
    public ByteBuffer writeNoObj(final NoObj noobj) {
        return NOOBJ_BYTES;
    }

    @Override
    public ByteBuffer writeBool(final Bool dool) {
        return ByteBuffer.wrap(handleIds(dool, dool.jvm().toString()).getBytes());
    }

    @Override
    public ByteBuffer writeFail(final Fail fail) {
        return ByteBuffer.wrap(handleIds(fail, "['" + Graphitty.strip(fail.jvm().getMessage()) + "']").getBytes());
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
        if (lst.isEmpty())
            return ByteBuffer.wrap(handleIds(lst, "[,]").getBytes());
        final String internal = lst.elements().map(o -> new String(this.write(o).array())).reduce(",", (a, b) -> a + b + ",");
        return ByteBuffer.wrap(handleIds(lst, "[" + internal.substring(1, internal.length() - 1) + "]").getBytes());
    }

    @Override
    public ByteBuffer writeRel(final Rel rel) {
        return ByteBuffer.wrap(handleIds(rel, new String(this.write(rel.first()).array()) + "=>" + new String(this.write(rel.second()).array())).getBytes());
    }

    @Override
    public ByteBuffer writeRec(final Rec rec) {
        if (rec.isEmpty())
            return ByteBuffer.wrap(handleIds(rec, "[=>]").getBytes());
        final String internal = rec.elements().map(Obj::<Rel>as)
                .map(o -> new String(this.write(o.first()).array()) + " => " + new String(this.write(o.second()).array()))
                .reduce(",", (a, b) -> a + b + ",");

        return ByteBuffer.wrap(handleIds(rec, "[" + internal.substring(1, internal.length() - 1) + "]").getBytes());
    }

    @Override
    public ByteBuffer writeInst(final Inst inst) {
        final String internal = inst.args().elements()
                .map(o -> new String(this.write(o).array()))
                .reduce(",", (a, b) -> a + b + ",");
        return ByteBuffer.wrap(handleIds(inst, "(" +
                (internal.length() == 1 ? "" : internal.substring(1, internal.length() - 1)) + ")" + (inst.f() == null ? "" : "{" + inst.f() + "}")).getBytes());
    }

    @Override
    public ByteBuffer writeCode(final Code code) {
        final Obj t = code.tryToInst();
        if (t.isInst()) return this.writeInst(t.as());
        final String internal = IteratorUtil.stream(code.insts()).map(i -> new String(this.writeInst(i).array())).reduce(".", (a, b) -> a + b + ".");
        return ByteBuffer.wrap(handleIds(code, "{{" + internal.substring(1, internal.length() - 1) + "}}").getBytes());
    }

    @Override
    public ByteBuffer writeObjs(final Objs objs) {
        final String internal = IteratorUtil.stream(objs.objsValue()).map(o -> new String(this.write(o).array())).reduce(",", (a, b) -> a + b + ",");
        return ByteBuffer.wrap(("{" + internal.substring(1, internal.length() - 1) + "}").getBytes());
    }

    @Override
    public Obj read(final ByteBuffer data) throws MTronException {
        // System.out.println(new String(data.array()));
        return mParser.m_obj().parse(new String(data.array())).get();
    }
}
