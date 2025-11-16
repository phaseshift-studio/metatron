package studio.phaseshift.metatron.lang.util.serial;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.obj.NoObj;
import studio.phaseshift.metatron.lang.core.m.parser.mParser;
import studio.phaseshift.metatron.lang.core.m.type.*;
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

    public String writeFail(final Fail f) {
        return tidVid(f, f.jvm().toString());
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
        return tidVid(u, "<" + u.uriValue() + ">");
    }

    public String writeRel(final Rel r) {
        return tidVid(r, r.first() + "=>" + r.second());
    }

    public String writeLst(final Lst l) {
        return tidVid(l, l.isEmpty() ? "[,]" : l.toString());
    }

    public String writeRec(final Rec r) {
        return tidVid(r, "[" + r.jvm().entrySet().stream().map(kv -> kv.getKey() + "=>" + kv.getValue()).reduce((a, b) -> a + "," + b).orElse("=>") + "]");
    }

    public String writeInst(final Inst i) {
        final String args = i.args().toString();
        return tidVid(i, "(" + args.substring(1, args.length() - 2) + ")" + (null == i.f() ? "" : "{" + i.f() + "}"));
    }

    public String writeCode(final Code c) {
        return tidVid(c, c.jvm().stream().map(Obj::toString).reduce((a, b) -> a + "." + b).orElse(""));
    }

    public String writeObjs(final Objs o) {
        return tidVid(o, "{" + IteratorUtil.stream(o.jvm()).map(Obj::toString).reduce((a, b) -> a + "," + b).orElse("") + "}");
    }


}
