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

package studio.phaseshift.metatron.isa.mach.io.type;


import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.PCMonad;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;

import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MBytes.bytes;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.mach.io.ioInstSet.OBJ_MTRON_STRING_SERIALIZER_VID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ObjmtronSerializer extends AbstractObjSerializer<String> {
    private static final String NOOBJ_STRING = "noobj";
    protected boolean leftJustify;
    public static final int CLIP_LENGTH = 60;
    protected int clip = CLIP_LENGTH;
    public static String REAL_FORMAT = "%.4f";
    public static final int INDENT_SIZE = 1;
    public static final int NESTED_STRING_THRESHOLD = 35;

    private static final ObjmtronSerializer INSTANCE = new ObjmtronSerializer();
    private static final ObjmtronSerializer NO_CLIP_INSTANCE = new ObjmtronSerializer(Integer.MAX_VALUE);

    public static ObjmtronSerializer single() {
        return INSTANCE;
    }

    public static ObjmtronSerializer singleNoClip() {
        return NO_CLIP_INSTANCE;
    }

    public ObjmtronSerializer() {
        this.leftJustify = true;
    }

    public ObjmtronSerializer(final boolean leftJustify) {
        this.leftJustify = leftJustify;
    }

    public ObjmtronSerializer(final int clipLength) {
        this();
        this.clip = clipLength;
    }

    public fURI vid() {
        return OBJ_MTRON_STRING_SERIALIZER_VID;
    }

    public fURI jvm() {
        return OBJ_MTRON_STRING_SERIALIZER_VID;
    }

    public static <OBJ extends Obj> OBJ parse(final String code) {
        return mParser.parse(code);
    }


    @Override
    public ByteBuffer outputBytes(final Obj obj) {
        return ByteBuffer.wrap(this.write(obj).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Obj inputBytes(final ByteBuffer bytes) {
        return this.read(new String(bytes.array(), StandardCharsets.UTF_8));
    }

    private String handleIds(final Obj obj, final String objString) {
        final StringBuilder sb = new StringBuilder();
        this.handleTID(sb, obj, !obj.isObjInst()).append(objString);
        this.handleVID(sb, obj);
        return sb.toString();
    }

    @Override
    public String writeBytes(final Bytes bytes) {
        final StringBuilder sb = new StringBuilder();
        if (bytes.bytesValue().capacity() > this.clip) {
            this.writeClip(sb, bytes);
        } else {
            sb.append("0x").append(HexFormat.of().formatHex(bytes.<Bytes>as().jvm().array()));
        }
        return this.handleIds(bytes, sb.toString());
    }

    @Override
    public String writeNoObj(final NoObj noobj) {
        return NOOBJ_STRING;
    }

    @Override
    public String writeBool(final Bool dool) {
        return handleIds(dool, dool.jvm().toString());
    }

    @Override
    public String writeFail(final Fail fail) {
        final StringBuilder sb = new StringBuilder();
        if (fail.vid() != null) {
            sb.append(handleIds(fail, "[" + fail.message().getMessage() + "][...]"));
        } else {
            sb.append(handleIds(fail, "[" + fail.message().getMessage() + "]"));
            fail.cause().ifPresent(c -> sb.append("\n    \\_").append(this.writeFail(c)));
        }
        return sb.toString();
    }

    @Override
    public String writeStr(final Str str) {
        final String string = str.jvm();
        if(null == string)
            return null;
        boolean doubleQuote = string.contains("\n") || (string.contains("\"") && string.contains("'"));
        final String quotes = doubleQuote ? "\"\"\"" : (string.contains("'") || string.contains("`") ? "\"" : "'");
        return handleIds(str, quotes + string + quotes);
    }

    @Override
    public String writeInt(final Int jnt) {
        return handleIds(jnt, jnt.jvm().toString());
    }

    @Override
    public String writeReal(final Real real) {
        return handleIds(real, String.format(REAL_FORMAT, real.jvm()));
    }

    private static String wrapUri(final fURI furi) {
        final String uriString = furi.toString();
        final char startChar = uriString.isEmpty() ? ' ' : uriString.charAt(0);
        final boolean wrap =
                uriString.isEmpty() ||
                        furi.hasTemplates() ||
                        CommonUtil.isInt(uriString.substring(0, 1)) ||
                        uriString.contains(" ") ||
                        startChar == 'T' ||
                        startChar == '+' ||
                        startChar == '#' ||
                        uriString.contains(".");
        return wrap ? ("<" + uriString + ">") : uriString;
    }

    @Override
    public String writeUri(final Uri uri) {
        return handleIds(uri, wrapUri(uri.uriValue()));
    }

    @Override
    public String writeLst(final Lst lst) {
        return this.generateLst(new StringBuilder(), lst, 0).toString();
    }

    @Override
    public String writeRel(final Rel rel) {
        final boolean firstRel = rel.jvm().get0().isRel();
        final boolean secondRel = rel.jvm().get1().isRel();
        final StringBuilder sb = new StringBuilder();
        sb.append(firstRel ? "(" : "").append(this.write(rel.jvm().get0())).append(firstRel ? ")" : "");
        sb.append("=>");
        sb.append(secondRel ? "(" : "").append(this.write(rel.jvm().get1())).append(secondRel ? ")" : "");
        return handleIds(rel, this.cleanEnding(sb).toString());
    }

    @Override
    public String writeRec(final Rec rec) {
        return this.generateRec(new StringBuilder(), rec, 0).toString();
    }

    @Override
    public String writeInst(final Inst inst) {
        return this.generateInst(new StringBuilder(), inst, 0, 0, false).toString();
    }

    public StringBuilder generateInst(final StringBuilder sb, final Inst inst, final int depth, final int padding, boolean nested) {
        if (inst.tid().basePath().equals(AUTO_FROM_INST_TID)) {
            sb.append("!*");
            renderInstArg(sb, depth + 1, padding, nested, inst.arg(0));
        } else if (inst.tid().basePath().equals(AUTO_AT_INST_TID) && inst.arg(1).isNoObj()) {
            sb.append("!@");
            renderInstArg(sb, depth + 1, padding, nested, inst.arg(0));

        } else if (inst.tid().basePath().equals(AUTO_INST_TID)) {
            sb.append("!");
            renderInstArg(sb, depth + 1, padding, nested, inst.arg(0));

        } else if (inst.tid().basePath().equals(FROM_INST_TID)) {
            sb.append("*");
            renderInstArg(sb, depth + 1, padding, nested, inst.arg(0));
        } else {
            final String internal = inst.args().elements()
                    .map(o -> {
                        final StringBuilder temp = new StringBuilder();
                        renderInstArg(temp, depth + 1, padding, nested, o);
                        return cleanEnding(temp).toString();
                    })
                    .reduce(",", (a, b) -> a + b + ",");
            sb.append(handleIds(inst, "(" +
                    (inst.args().isEmpty() ? "" : internal.substring(1, internal.length() - 1)) + ")" + (inst.f() == null ? "" : "{" + inst.f() + "}")));
        }
        return cleanEnding(sb);
    }

    @Override
    public String writeCode(final Code code) {
        //  final Obj t = code.tryToInst();
        //  if (t.isInst()) return this.writeInst(t.as());
        final String internal = IteratorUtil.stream(code.insts()).map(this::writeInst).reduce("", (a, b) -> a + "." + b);
        return !internal.isEmpty() ? internal.substring(1) : "";
    }

    @Override
    public String writeObjs(final Objs objs) {
        final String internal = IteratorUtil.stream(objs.jvm()).map(this::write).reduce("", (a, b) -> a + "," + b);
        return "{" + this.cleanEnding(new StringBuilder(internal.substring(1))) + "}";
    }

    @Override
    public String writeType(final Type type) {
        return this.generateType(new StringBuilder(), type, 0).toString();
    }

    @Override
    public String writeMonad(final PCMonad monad) {
        return handleIds(monad, "M[" + this.write(monad.obj()) + "<=M=>" + this.write(monad.inst()));
    }

    @Override
    public String writeMachine(final Machine machine) {
        return handleIds(machine, "M[" + this.write(machine.code()) + "]");
    }

    @Override
    public Obj read(final String data) throws MTronException {
        try {
            return mParser.eval(data);
        } catch (final Exception e) {
            try {
                return mParser.parse(data);
            } catch (final Exception e2) {
                return fail(e2);
            }
        }
    }

    private StringBuilder handleTID(final StringBuilder sb, final Obj obj, final boolean hideBaseTID) {
        if (!obj.isFail() && !obj.isCaughtFail() && hideBaseTID && !obj.tid().hasPoly()) {
            if (BASE_TYPES.contains(obj.tid()))
                return sb;
            else if (BASE_TYPES.contains(obj.tid().basePath())) {
                sb.append('{').append(obj.tid().c()).append('}');
                return sb;
            }
        }
        sb.append(Router.loaded() ? Router.global().redirect(obj.tid(), false) : obj.tid());
        if (!obj.isObjInst())
            sb.append("::");
        return sb;
    }

    private StringBuilder handleVID(final StringBuilder sb, final Obj obj) {
        if (null == obj.vid())
            return sb;
        return sb.append("@").append(wrapUri(Router.loaded() ? Router.global().redirect(obj.vid(), false) : obj.vid()));
    }

    private boolean isNested(final Poly<?, ?> poly) {
        if (!poly.isLst() && !poly.isRec())
            return false;
        final long count = poly.count();
        return count != 1 && (count > 4 || poly.values().anyMatch(o ->
                null != o.vid() ||
                        o.isPoly() ||
                        o.isObjCall() ||
                        (o.isStr() && o.strValue().length() > NESTED_STRING_THRESHOLD) ||
                        (o.isUri() && o.uriValue().toString().length() > NESTED_STRING_THRESHOLD) ||
                        (o.isBytes() && o.bytesValue().capacity() > NESTED_STRING_THRESHOLD) ||
                        isComplexType(o)));
    }

    private StringBuilder generateLst(final StringBuilder sb, final Lst lst, final int depth) {
        handleTID(sb, lst, true);
        if (lst.isEmpty()) {
            sb.append("[,]");
        } else {
            boolean nested = isNested(lst);
            sb.append("[");
            if (nested) sb.append("\n");
            lst.jvm().forEach(v -> {
                if (nested) {
                    sb.append(" ".repeat((depth + 1) * INDENT_SIZE));
                }
                renderValue(sb, nested ? depth + 1 : 0, v);
                sb.append(",");
                if (nested) sb.append("\n");
            });
            cleanEnding(sb);
            sb.append("]");
        }
        return handleVID(sb, lst);
    }

    private StringBuilder cleanEnding(final StringBuilder sb) {
        char last = sb.charAt(sb.length() - 1);
        while (last == ' ' || last == ',' || last == '\n') {
            sb.deleteCharAt(sb.length() - 1);
            last = sb.charAt(sb.length() - 1);
        }
        return sb;
    }

    private StringBuilder generateType(final StringBuilder sb, final Type type, final int depth) {
        sb.append(
                        (Router.loaded() ? Router.global().redirect(type.tid(), false) : type.tid()).toString())
                .append("::T");
        if (type.hasPredicate()) {
            if (type.isIsaPredicate()) {
                sb.append("[?");
                StringBuilder temp = new StringBuilder();
                renderValue(temp, depth + 1, type.isPredicateObj());
                sb.append(temp);
                sb.append("]");
            } else {
                sb.append("[").append(type.predicate()).append("]");
            }
        }
        if (type.hasConstructor()) {
            if (!type.hasPredicate())
                sb.append("[]");
            sb.append("[");
            StringBuilder temp = new StringBuilder();
            // temp.append(" ".repeat((depth + 1) * INDENT_SIZE));
            renderValue(temp, depth + 1, type.constructor());
            cleanEnding(temp);
            sb.append(temp);
            //sb.append("\n");
            //sb.append(" ".repeat(depth * INDENT_SIZE));
            sb.append("]");
        }
        if (type.vid() != null && !type.tid().equals(type.vid()))
            sb.append("@").append(type.vid());
        return sb;
    }

    private boolean isComplexType(final Obj type) {
        return type.isType() && (type.asType().hasPredicate() || type.asType().hasConstructor());
    }

    private StringBuilder generateRec(final StringBuilder sb, final Rec rec, final int depth) {
        handleTID(sb, rec, true);
        if (rec.isEmpty()) {
            sb.append("[=>]");
        } else {
            boolean nested = isNested(rec);
            sb.append("[");
            if (nested) sb.append("\n");
            rec.jvm().forEach((k, v) -> {
                if (nested) {
                    sb.append(" ".repeat((depth + 1) * INDENT_SIZE));
                }
                sb.append(write(k)).append("=>");
                if (v == rec)
                    throw MTronException.of("prevented infinite recursion on nested rec: key %s", k);
                renderValue(sb, nested ? depth + 1 : 0, v);
                sb.append(",");
                if (nested) sb.append("\n");
            });
            cleanEnding(sb);
            sb.append("]");
        }
        return handleVID(sb, rec);
    }


    private StringBuilder writeClip(final StringBuilder sb, final Obj obj) {
        if (obj.isStr() && obj.strValue().length() > this.clip) {
            sb.append(write(str(obj.strValue().substring(0, this.clip - 1) + "...")));
        } else if (obj.isBytes() && obj.bytesValue().capacity() > this.clip) {
            byte[] bb = Arrays.copyOf(obj.bytesValue().array(), this.clip - 1);
            sb.append(write(bytes(ByteBuffer.wrap(bb))));
            sb.append("...");
        } else if (obj.isFail()) {
            if (obj.failValue().get1() != null)
                writeClip(sb, obj.failValue().get1());
            else {
                String message = obj.asFail().message().getMessage().split("\n")[0];
                message = message.length() > this.clip ? (message.substring(0, this.clip - 1) + "...") : message;
                sb.append(writeFail(fail(message)));
            }
        } else {
            sb.append(obj.toShortString());
        }
        return sb;
    }

    private void renderValue(final StringBuilder sb, final int depth, final Obj v) {
        if (v.isRec()) {
            this.generateRec(sb, v.as(), depth);
        } else if (v.isLst()) {
            this.generateLst(sb, v.as(), depth);
        } else {
            this.writeClip(sb, v);
        }
    }

    private void renderInstArg(final StringBuilder sb, final int depth, final int padding, final boolean nested, final Obj arg) {
        if (arg.isRec()) {
            this.generateRec(sb, arg.as(), depth);
        } else if (arg.isLst()) {
            this.generateLst(sb, arg.as(), depth);
        } else {
            if (nested) {
                sb.append(" ".repeat(depth * INDENT_SIZE + padding));
            }
            this.writeClip(sb, arg);
        }
    }

    public StringBuilder prettyPrintCode(final StringBuilder sb, final Obj call, final int depth) {
        if (call.isCode()) {
            for (final Inst inst : call.<Code>as().codeValue()) {
                prettyPrintCode(sb, inst, depth);
            }
        } else if (!call.isNoObj() && call.isObjInst()) {
            final Inst inst = call.as();
            sb.append("  ".repeat(depth)).append(this.write(inst)).append("\n");
            if (null != inst.jvm()) {
                inst.args().elements().forEach(arg -> {
                    if (arg.isObjCall() || arg.isObjs()) {
                        prettyPrintCode(sb, arg, depth + 1);
                    }
                });
            }
        } else if (!call.isNoObj() && call.isObjs()) {
            call.stream().forEach(o -> prettyPrintCode(sb, o, depth + 1));
        }
        return sb;
    }

    public static String prettyPrintCode(final Call code) {
        final StringBuilder sb = new StringBuilder();
        return new ObjmtronSerializer().prettyPrintCode(sb, code, 0).toString();
    }
}
