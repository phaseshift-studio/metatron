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

package studio.phaseshift.metatron.isa.m.web.type;

import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;

import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ObjByteBufferSerializer extends AbstractObjSerializer<ByteBuffer> {

    private static final ByteBuffer NOOBJ_BYTES = ByteBuffer.wrap("noobj".getBytes());

    private static final ObjByteBufferSerializer INSTANCE = new ObjByteBufferSerializer();

    public static ObjByteBufferSerializer singleton() {
        return INSTANCE;
    }

    protected ObjByteBufferSerializer(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    public ObjByteBufferSerializer() {
        super(OBJ_BYTE_BUFFER_SERIALIZER_TID, OBJ_BYTE_BUFFER_SERIALIZER_VID);
    }

    public static ObjByteBufferSerializer of(final Rec rec, final fURI vid) {
        return new ObjByteBufferSerializer(rec.jvm(), OBJ_BYTE_BUFFER_SERIALIZER_TID, vid);
    }

    @Override
    public fURI vid() {
        return OBJ_BYTE_BUFFER_SERIALIZER_VID;
    }

    @Override
    public ByteBuffer outputBytes(final Obj obj) {
        return this.write(obj);
    }

    @Override
    public Obj inputBytes(final ByteBuffer bytes) {
        return this.read(bytes);
    }

    private String handleIds(final Obj obj, final String objString) {
        // AN INSTRUCTION'S HEAD IS WRITTEN AS THE MTRON SERIALIZER WRITES IT ({span}name{point}?typing&query), without the
        // tid's args=[...] signature, which the parser mints again from the arguments. Written whole, <tid>(...), the head
        // did not parse once a tid carried that signature (</m/inst/plus?args=[/m/int]>), so no instruction read back
        // A SCALAR IS {point}value{span}: m_int, m_real, m_str, m_bool and m_bytes read a value's span from the brace
        // after it, ONE when nothing trails, so a span written into the tid, <{3}/m/int>::2, read back as 2. A named
        // type's scalar trails its span too (the mtron serializer trails one only for a base type), and an
        // instruction's span is in its head. A uri keeps its whole tid: a brace after a uri's brackets is its power
        // (m_uri), and a ranged span stays on its tid (MUri.clone)
        final boolean trails = obj.isMono() && !obj.isUri() && !obj.tid().b().isOne();
        final String head = obj.isInst() ? ObjmtronSerializer.single().generateInstHead(new StringBuilder(), obj.asInst()).toString()
                : "<" + (trails ? obj.tid().b(cInt.ONE()) : obj.tid()) + ">::";
        return (head + objString + (trails ? "{" + obj.tid().b() + "}" : "") + ((obj.vid() == null) ? "" : ("@<" + obj.vid() + ">"))).trim();
    }

    @Override
    public ByteBuffer writeNoObj(final NoObj noobj) {
        return NOOBJ_BYTES;
    }

    @Override
    public ByteBuffer writeBytes(final Bytes bytes) {
        return ByteBuffer.wrap(handleIds(bytes, "0x" + HexFormat.of().formatHex(bytes.asBytes().jvm().array())).getBytes());
    }

    @Override
    public ByteBuffer writeBool(final Bool dool) {
        return ByteBuffer.wrap(handleIds(dool, dool.jvm().toString()).getBytes());
    }

    @Override
    public ByteBuffer writeFail(final Fail fail) {
        // A FAIL IS WRITTEN AS THE MTRON SERIALIZER WRITES IT, fail::[level][cause]…@vid with each level's message a str,
        // which m_fail reads. m_fail opens only on fail:: or /m/fail::, so </m/fail>::['message,cause'] read back as a
        // parse error, its brackets a lst typed fail ("[arraylist] is not a fail::T")
        return ByteBuffer.wrap(ObjmtronSerializer.single().writeFail(fail).getBytes());
    }

    @Override
    public ByteBuffer writeStr(final Str str) {
        // QUOTED AS THE MTRON SERIALIZER QUOTES A STR, its delimiter one the value does not hold and an escape where none
        // is safe: wrapped in ' and escaped nothing, an apostrophe or a closing backslash ended the str early
        return ByteBuffer.wrap(handleIds(str, ObjmtronSerializer.single().writeStr(str(str.strValue()))).getBytes());
    }

    @Override
    public ByteBuffer writeInt(final Int jnt) {
        return ByteBuffer.wrap(handleIds(jnt, jnt.intValue().toString()).getBytes());
    }

    @Override
    public ByteBuffer writeReal(final Real real) {
        return ByteBuffer.wrap(handleIds(real, real.realValue().toString()).getBytes());
    }

    @Override
    public ByteBuffer writeUri(final Uri uri) {
        return ByteBuffer.wrap(handleIds(uri, "<" + uri.uriValue() + ">").getBytes());
    }

    @Override
    public ByteBuffer writeLst(final Lst lst) {
        if (lst.isEmpty())
            return ByteBuffer.wrap(handleIds(lst, "[,]").getBytes());
        final String internal = lst.lstValue().stream().map(o -> new String(this.write(o).array())).reduce(",", (a, b) -> a + b + ",");
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
        final String internal = rec.recValue().entrySet().stream()
                .map(o -> new String(this.write(o.getKey()).array()) + " => " + new String(this.write(o.getValue()).array()))
                .reduce(",", (a, b) -> a + b + ",");

        return ByteBuffer.wrap(handleIds(rec, "[" + internal.substring(1, internal.length() - 1) + "]").getBytes());
    }

    @Override
    public ByteBuffer writeInst(final Inst inst) {
        // A KEYED ARGUMENT IS WRITTEN <name>=>value, as the parser reads it: its key is a name, a bare fURI
        // (mParser.m_inst_arg_key), so a typed key, </m/uri>::<a>=>v, typed the whole pair as a uri (a rel, not a uri::T)
        // and failed, and a rel value, </m/rel>::k=>v, read back as a positional rel, a lst of rels for the rec
        final String internal = inst.args().elements()
                .map(o -> inst.args().isRec()
                        ? "<" + o.asRel().first().uriValue() + ">=>" + new String(this.write(o.asRel().second()).array())
                        : new String(this.write(o).array()))
                .reduce(",", (a, b) -> a + b + ",");
        return ByteBuffer.wrap(handleIds(inst, "(" +
                (inst.args().isEmpty() ? "" : internal.substring(1, internal.length() - 1)) + ")" + (inst.f() == null ? "" : "{" + inst.f() + "}")).getBytes());
    }

    @Override
    public ByteBuffer writeCode(final Code code) {
        //  final Obj t = code.tryToInst();
        //  if (t.isInst()) return this.writeInst(t.as());
        // an empty code is the unit, id() (Marko, 2026-11-24), its coefficient the unit's point; its joined chain had no
        // remainder to slice
        if (code.jvm().isEmpty())
            return this.write(code.one().c(code.c()));
        // A CODE IS ITS BARE CHAIN, as the mtron serializer writes it: the parser reads /m/code::/m/inst/start(...) as the
        // uri /m/inst/start typed /m/code, which is not a code::T, and fails at the first column
        final String internal = IteratorUtil.stream(code.insts()).map(i -> new String(this.writeInst(i).array())).reduce(".", (a, b) -> a + b + ".");
        return ByteBuffer.wrap(internal.substring(1, internal.length() - 1).getBytes());
    }

    @Override
    public ByteBuffer writeObjs(final Objs objs) {
        // zero members is noobj (MObjs.isNoObj is jvm.isEmpty()) -- there is no remainder to slice
        if (objs.isNoObj())
            return NOOBJ_BYTES;
        final String internal = IteratorUtil.stream(objs.objsValue()).map(o -> new String(this.write(o).array())).reduce(",", (a, b) -> a + b + ",");
        return ByteBuffer.wrap(("{" + internal.substring(1, internal.length() - 1) + "}").getBytes());
    }

    @Override
    public ByteBuffer writeType(final Type type) {
        String typeString = (Machine.loaded() ? Machine.current().memory().redirect(type.tid(), false) : type.tid()) + "::T";
        if (type.hasPredicate())
            typeString += ("[" + type.predicate() + "]");
        if (type.hasConstructor()) {
            if (!type.hasPredicate())
                typeString += "[]";
            typeString += ("[" + type.constructor() + "]");
        }
        if (type.vid() != null && !type.tid().equals(type.vid()))
            typeString += ("@" + type.vid());
        return ByteBuffer.wrap(typeString.getBytes());
    }

    @Override
    public Obj read(final ByteBuffer data) throws MTronException {
        //Machine.authority().logger().info("received %s", new String(data.array(), StandardCharsets.UTF_8));
        return ObjmtronSerializer.parse(new String(data.array(), StandardCharsets.UTF_8));
    }

    @Override
    public Objs readObjs(final ByteBuffer data) throws MTronException {
        //Machine.authority().logger().info("received %s", new String(data.array(), StandardCharsets.UTF_8));
        return ObjmtronSerializer.parse(new String(data.array(), StandardCharsets.UTF_8));
    }
}
