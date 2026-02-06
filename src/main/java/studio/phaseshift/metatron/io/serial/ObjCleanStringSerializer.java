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
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.sys.type.Router;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicBoolean;

import static studio.phaseshift.metatron.furi.q.DocQ.DOC_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MBytes.bytes;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ObjCleanStringSerializer implements ObjSerializer<String> {

    public static final fURI OBJ_CLEAN_STRING_SERIALIZER_TID = OBJ_SERIAL_TID.extend("clean");
    private static final String NOOBJ_STRING = "noobj";
    protected boolean leftJustify;

    public ObjCleanStringSerializer() {
        this.leftJustify = false;
    }

    public ObjCleanStringSerializer(final boolean leftJustify) {
        this.leftJustify = leftJustify;
    }

    @Override
    public fURI tid() {
        return OBJ_CLEAN_STRING_SERIALIZER_TID;
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
        this.handleTID(sb, obj, !obj.isInst()).append(objString);
        this.handleVID(sb, obj);
        return sb.toString();
    }

    @Override
    public String writeBytes(final Bytes bytes) {
        final StringBuilder sb = new StringBuilder();
        if (bytes.bytesValue().capacity() > CLIP_LENGTH) {
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
        sb.append(handleIds(fail, "[" + fail.message().getMessage() + "]"));
        fail.cause().ifPresent(c -> sb.append("\n    \\_").append(this.writeFail(c)));
        return sb.toString();
    }

    @Override
    public String writeStr(final Str str) {
        final String string = str.jvm();
        final String quotes = string.contains("\n") ? "\"\"\"" : string.contains("'") ? "\"" : "'";
        return handleIds(str, quotes + string + quotes);
    }

    @Override
    public String writeInt(final Int jnt) {
        return handleIds(jnt, jnt.jvm().toString());
    }

    @Override
    public String writeReal(final Real real) {
        return handleIds(real, real.jvm().toString());
    }

    @Override
    public String writeUri(final Uri uri) {
        final String uriString = uri.jvm().toString();
        final char startChar = uriString.isEmpty() ? ' ' : uriString.charAt(0);
        //final char endChar = uriString.isEmpty() ? ' ' : uriString.charAt(uriString.length() - 1);
        final boolean wrap =
                uriString.isEmpty() ||
                        CommonUtil.isInt(uriString.substring(0, 1)) ||
                        uriString.contains(" ") ||
                        startChar == '+' || startChar == '#' ||
                        uriString.contains(".") ||
                        uriString.contains(",") ||
                        uriString.contains("(") ||
                        uriString.contains(")");
        return handleIds(uri, wrap ? ("<" + uriString + ">") : uriString);
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
        return handleIds(rel, sb.toString());
    }

    @Override
    public String writeRec(final Rec rec) {
        return this.generateRec(new StringBuilder(), rec, 0, 0).toString();
    }

    @Override
    public String writeInst(final Inst inst) {
        if (inst.tid().basePath().equals(AUTO_FROM_INST_TID))
            return "!*" + this.write(inst.arg(0));
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
        return "{" + internal.substring(1) + "}";
    }

    @Override
    public String writeType(final Type type) {
        String typeString = (Router.loaded() ? Router.global().rewrite(type.tid(), false) : type.tid()) + "::T";
        if (type.hasPredicate())
            typeString += ("[" + type.predicate() + "]");
        if (type.hasConstructor()) {
            if (!type.hasPredicate())
                typeString += "[]";
            typeString += ("[" + type.constructor() + "]");
        }
        if (type.vid() != null)
            typeString += ("@" + type.vid());
        return typeString;
    }

    @Override
    public String writeMonad(final Monad monad) {
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
            return fail(e);
        }
    }

    private StringBuilder handleTID(final StringBuilder sb, final Obj obj, final boolean hideBaseTID) {
        if (!obj.isFail() && !obj.isCaughtFail() && hideBaseTID && BASE_TYPES.contains(obj.tid()))
            return sb;
        sb.append(Router.loaded() ? Router.global().rewrite(obj.tid(), false) : obj.tid());
        if (!obj.isInst())
            sb.append("::");
        return sb;
    }

    private StringBuilder handleVID(final StringBuilder sb, final Obj obj) {
        if (null == obj.vid())
            return sb;
        return sb.append("@").append(obj.vid());
    }

    private StringBuilder generateLst(final StringBuilder sb, final Lst lst, final int depth) {
        handleTID(sb, lst, true);
        if (lst.isEmpty()) {
            sb.append("[,]");
        } else {
            boolean nested = lst.elements().anyMatch(Obj::isPoly) ||
                    lst.jvm().size() > 4;
            /* lst.jvm().stream().map(this::write).map(String::length).reduce(0, Integer::sum) > (50 - depth);*/
            final boolean isBaseType = lst.type().isBaseType();
            sb.append("[").append(nested && !isBaseType ? "\n" : "");
            final AtomicBoolean first = new AtomicBoolean(isBaseType);
            lst.jvm().forEach(v -> {
                if (nested && !first.getAndSet(false))
                    sb.append(" ".repeat(depth + 2));
                this.processNestedPoly(sb, depth, 0, nested, v);
            });
            if (nested)
                sb.deleteCharAt(sb.length() - 1);
            sb.deleteCharAt(sb.length() - 1);
            sb.append("]");
        }
        return handleVID(sb, lst);
    }

    private StringBuilder generateRec(final StringBuilder sb, final Rec rec, final int depth, final int padding) {
        handleTID(sb, rec, true);
     //   if(rec.tid().basePath().equals(DOC_TID)) // TODO: the concept of toString() needs to exist for metatron
      //      return sb.append(rec.toString());
        if (rec.isEmpty()) {
            sb.append("[=>]");
        } else {
            boolean nested =
                    rec.jvm().values().stream().anyMatch(Obj::isPoly) ||
                            rec.jvm().size() > 4;
            /*rec.jvm().values().stream().filter(o -> !o.isPoly()).map(this::write).map(String::length).reduce(0, Integer::sum) > (75 - depth);*/
            final int maxKeyLength = nested ? rec.jvm().keySet().stream().map(this::write).map(String::length).reduce(0, Integer::max) : 0;
            final boolean isBaseType = rec.type().isBaseType();
            final AtomicBoolean first = new AtomicBoolean(isBaseType);
            sb.append("[").append(nested && !isBaseType ? "\n" : "");
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
                this.processNestedPoly(sb, depth, leftJustify ? 0 : childPadding, nested, v);
            });
            if (nested)
                sb.deleteCharAt(sb.length() - 1);
            sb.deleteCharAt(sb.length() - 1);
            sb.append("]");
        }
        return handleVID(sb, rec);
    }

    public static final int CLIP_LENGTH = 40;

    private StringBuilder writeClip(final StringBuilder sb, final Obj obj) {
        if (obj.isStr() && obj.strValue().length() > CLIP_LENGTH) {
            sb.append(write(str(obj.strValue().substring(0, CLIP_LENGTH - 1))));
            sb.append("...");
        } else if (obj.isBytes() && obj.bytesValue().capacity() > CLIP_LENGTH) {
            byte[] bb = Arrays.copyOf(obj.bytesValue().array(), CLIP_LENGTH - 1);
            sb.append(write(bytes(ByteBuffer.wrap(bb))));
            sb.append("...");
        } else
            sb.append(write(obj));
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
        } else if (!call.isNoObj() && call.isInst()) {
            final Inst inst = call.as();
            sb.append("  ".repeat(depth)).append(this.write(inst)).append("\n");
            if (null != inst.jvm()) {
                inst.args().elements().forEach(arg -> {
                    if (arg.isCall() || arg.isObjs()) {
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
        return new ObjCleanStringSerializer().prettyPrintCode(sb, code, 0).toString();
    }
}
