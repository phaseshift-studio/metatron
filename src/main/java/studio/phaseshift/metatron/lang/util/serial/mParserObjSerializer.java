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
import studio.phaseshift.metatron.lang.core.m.parser.mParser;
import studio.phaseshift.metatron.lang.core.m.type.*;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.nio.ByteBuffer;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mParserObjSerializer implements ObjSerializer<String> {

    @Override
    public fURI tid() {
        return OBJ_SERIAL_TID.extend("mparser");
    }


    @Override
    public Obj read(final String source) throws MTronException {
        return mParser.parse(source);
    }

    @Override
    public ByteBuffer writeBytes(final Obj obj) {
        return ByteBuffer.wrap(this.write(obj).getBytes());
    }

    @Override
    public Obj readBytes(final ByteBuffer bytes) {
        return this.read(new String(bytes.array()));
    }

    private String tidVid(final Obj obj, final String jvm) {
        final String tidObj = obj.tid() + "::" + jvm;
        return obj.vid() == null ? tidObj : tidObj + "@" + obj.vid();
    }

    public String writeNoObj(final NoObj n) {
        return tidVid(n, "noobj");
    }

    public String writeType(final Type t) {
        String temp = t.tid() + "::T";
        if (t.predicate() != null || t.constructor() != null) {
            if (t.predicate() != null)
                temp += temp + "[" + this.write(t.predicate()) + "]";
            else
                temp += temp + "[]";
        }
        if (t.constructor() != null)
            temp += temp + "[" + this.write(t.constructor()) + "]";
        return temp;
    }

    public String writeFail(final Fail f) {
        return Graphitty.strip(f.toString());
    }

    public String writeBool(final Bool b) {
        return tidVid(b, b.jvm().toString());

    }

    public String writeInt(final Int i) {
        return tidVid(i, i.jvm().toString());
    }

    public String writeReal(final Real r) {
        return tidVid(r, r.jvm().toString());
    }

    public String writeStr(final Str s) {
        return tidVid(s, "'" + s.jvm() + "'");
    }

    public String writeUri(final Uri u) {
        return tidVid(u, "<" + u.uriValue().toString() + ">");
    }

    public String writeRel(final Rel r) {
        return tidVid(r, this.write(r.first()) + "=>" + this.write(r.second()));
    }

    public String writeLst(final Lst l) {
        return tidVid(l, "[" + l.jvm().stream().map(this::write).reduce((a, b) -> a + "," + b).orElse(",") + "]");
    }

    public String writeRec(final Rec r) {
        return tidVid(r, "[" + r.jvm().entrySet().stream().map(kv -> this.write(kv.getKey()) + "=>" + this.write(kv.getValue())).reduce((a, b) -> a + "," + b).orElse("=>") + "]");
    }

    public String writeInst(final Inst i) {
        final String args = i.args().toString();
        return tidVid(i, "(" + args.substring(1, args.length() - 2) + ")" + (null == i.f() ? "" : "{" + i.f() + "}"));
    }

    public String writeCode(final Code c) {
        return tidVid(c, c.jvm().stream().map(this::write).reduce((a, b) -> a + "." + b).orElse(""));
    }

    public String writeObjs(final Objs o) {
        return tidVid(o, "{" + IteratorUtil.stream(o.jvm()).map(this::write).reduce((a, b) -> a + "," + b).orElse("") + "}");
    }


}
