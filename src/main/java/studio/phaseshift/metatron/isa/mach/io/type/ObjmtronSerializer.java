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
import java.util.concurrent.atomic.AtomicBoolean;

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
    public static final int CLIP_LENGTH = 40;
    protected int clip = CLIP_LENGTH;
    public static String REAL_FORMAT = "%.4f";

    private static final ObjmtronSerializer INSTANCE = new ObjmtronSerializer();

    public static ObjmtronSerializer single() {
        return INSTANCE;
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
        boolean doubleQuote = string.contains("\n") || (string.contains("\"") && string.contains("'"));
        final String quotes = doubleQuote ? "\"\"\"" : string.contains("'") ? "\"" : "'";
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
        return this.generateRec(new StringBuilder(), rec, 0, 0).toString();
    }

    @Override
    public String writeInst(final Inst inst) {
        if (inst.tid().basePath().equals(AUTO_FROM_INST_TID))
            return "!*" + this.write(inst.arg(0));
        if (inst.tid().basePath().equals(AUTO_AT_INST_TID) && inst.arg(1).isNoObj())
            return "!@" + this.write(inst.arg(0));
        if (inst.tid().basePath().equals(AUTO_INST_TID))
            return "!" + this.write(inst.arg(0));
        if (inst.tid().basePath().equals(FROM_INST_TID))
            return "*" + this.write(inst.arg(0));
        final String internal = inst.args().elements()
                .map(this::write)
                .reduce(",", (a, b) -> a + b + ",");
        return handleIds(inst, "(" +
                (inst.args().isEmpty() ? "" : internal.substring(1, internal.length() - 1)) + ")" + (inst.f() == null ? "" : "{" + inst.f() + "}"));
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
        return sb.append("@").append(wrapUri(obj.vid()));
    }

    private boolean isNested(final Poly<?, ?> poly) {
        if (!poly.isLst() && !poly.isRec())
            return false;
        final long count = poly.count();
        return count != 1 && (count > 4 ||
                (poly.isLst() ? poly.lstValue().stream() : poly.recValue().values().stream()).anyMatch(o ->
                        null != o.vid() ||
                                o.isPoly() ||
                                o.isObjCall() ||
                                (o.isStr() && o.strValue().length() > 15) ||
                                (o.isUri() && o.uriValue().toString().length() > 15) ||
                                (o.isBytes() && o.bytesValue().capacity() > 15) ||
                                isComplexType(o)));
    }

    private StringBuilder generateLst(final StringBuilder sb, final Lst lst, final int depth) {
        handleTID(sb, lst, true);
        if (lst.isEmpty()) {
            sb.append("[,]");
        } else {
            boolean nested = isNested(lst);
            sb.append("[").append(nested ? "\n" : "");
            lst.jvm().forEach(v -> {
                if (nested)
                    sb.append(" ".repeat(depth + 2));
                this.processNestedPoly(sb, depth + 1, 0, nested, v);
            });
            this.cleanEnding(sb);
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
        StringBuilder typeString = new StringBuilder(
                (Router.loaded() ? Router.global().redirect(type.tid(), false) : type.tid()).toString())
                .append("::T");
        if (type.hasPredicate()) {
            if (type.predicate().isObjInst() && type.predicate().tid().basePath().equals(ISA_INST_TID) && type.predicate().asInst().arg(0).isPoly()) {
                typeString.append("[?");
                processNestedPoly(sb, depth + 1, 0, true, type.predicate().asInst().arg(0));
                typeString.append(sb, 0, sb.length() - 2); // remove ,\n
                typeString.append("]");
            } else {
                typeString.append("[").append(type.predicate()).append("]");
            }
        }
        if (type.hasConstructor()) {
            if (!type.hasPredicate())
                typeString.append("[]");
            typeString.append("[").append(type.constructor()).append("]");
        }
        if (type.vid() != null && !type.tid().equals(type.vid()))
            typeString.append("@").append(type.vid());
        return typeString;
    }

    private boolean isComplexType(final Obj type) {
        return type.isType() && (type.asType().hasPredicate() || type.asType().hasConstructor());
    }

    private StringBuilder generateRec(final StringBuilder sb, final Rec rec, final int depth, final int padding) {
        handleTID(sb, rec, true);
        //   if(rec.tid().basePath().equals(DOC_TID)) // TODO: the concept of toString() needs to exist for metatron
        //      return sb.append(rec.toString());
        if (rec.isEmpty()) {
            sb.append("[=>]");
        } else {
            boolean nested = isNested(rec);
            final int maxKeyLength = nested ? rec.jvm().keySet().stream().map(this::write).map(String::length).reduce(0, Integer::max) : 0;
            final AtomicBoolean first = new AtomicBoolean(false);
            sb.append("[").append(nested ? "\n" : "");
            rec.jvm().forEach((k, v) -> {
                int indent = nested ? (first.getAndSet(false) ?
                        (depth * 2) - (padding + 4) :
                        (depth * 2) + (padding + 1)) : 0;
                if (indent < 0) {
                    indent = (depth * 2) + (padding + 1);
                    sb.append("\n");
                }
                sb.append(" ".repeat(indent));
                final String keyString = write(k);
                final int childPadding = nested ? (maxKeyLength - keyString.length()) : 0;
                sb.append(" ".repeat(nested && !leftJustify ? childPadding : 0))
                        .append(keyString)
                        .append(" ".repeat(nested && leftJustify ? childPadding : 0)).append("=>");
                if (v == rec)
                    throw MTronException.of("prevented infinite recursion on nested rec: key %s", k);
                this.processNestedPoly(sb, depth, leftJustify ? 0 : childPadding, nested, v);
            });
            this.cleanEnding(sb);
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
                sb.append(writeFail(fail(obj.asFail().message().getMessage().split("\n")[0])));
            }
        } else {
            sb.append(obj.toShortString());
        }
        return sb;
    }

    private void processNestedPoly(final StringBuilder sb, final int depth, final int padding, final boolean nested, final Obj v) {
        if (v.isRec()) {
            this.generateRec(sb, v.as(), depth + 1, padding);
        } else if (v.isLst()) {
            this.generateLst(sb, v.as(), depth + 1);
        } else {
            this.writeClip(sb, v);
        }
        sb.append(",");
        if (nested)
            sb.append("\n");
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
