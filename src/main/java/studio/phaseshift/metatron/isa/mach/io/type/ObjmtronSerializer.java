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


import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.MCode;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.PCMonad;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.machine.SwarmMachine;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;

/**
 * The mtron string serializer: the de facto computer-to-computer format of the language.
 * <p>
 * This class is deliberately <em>not configurable</em> — there is one access point,
 * {@link #single()}, and everything it reads and writes is the one thing: mtron code.
 * The contract is
 * <ul>
 *   <li><b>every {@code write(obj)} yields legal mtron</b> — complete (no clipping of
 *       recs, lsts, str, bytes or fail messages), unindented, and parseable back to an
 *       equal obj, so a serialized value can travel — a database row, an http body, a
 *       message between spaces — and be read back without loss;</li>
 *   <li><b>every {@code read(data)} is parse-driven</b> — the same parser mtron itself
 *       uses, so what goes out on one machine comes back on any other;</li>
 *   <li>there are <b>no display concerns here</b> — no indentation, no clipping, no
 *       {@code {{link}}} markup, no pager.  Rendering for a person is the job of
 *       {@link ObjmtronUISerializer}; this is what a machine reads.
 * </ul>
 * The static {@code parse}/{@code parseMulti}/{@code splitCodeAtEnd}/{@code eval}
 * helpers are the same parsing service, exposed for callers that only need one side.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ObjmtronSerializer extends AbstractObjSerializer<String> {
    private static final String NOOBJ_STRING = "noobj";

    /**
     * The one instance.  This serializer is not configured per-instance: clipping,
     * indentation and links are display features and live in {@link ObjmtronUISerializer},
     * so there is nothing to hold here but the tid and vid.
     */
    private static final ObjmtronSerializer INSTANCE = new ObjmtronSerializer();

    /**
     * The canonical instance.
     */
    public static ObjmtronSerializer single() {
        return INSTANCE;
    }

    public ObjmtronSerializer() {
        super(OBJ_MTRON_SERIALIZER_TID, OBJ_MTRON_STRING_SERIALIZER_VID);
    }

    protected ObjmtronSerializer(final fURI vid) {
        super(OBJ_MTRON_SERIALIZER_TID, vid);
    }

    protected ObjmtronSerializer(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    // ── Identity ─────────────────────────────────────────────────

    @Override
    public fURI vid() {
        return OBJ_MTRON_STRING_SERIALIZER_VID;
    }

    // ── Parsing ──────────────────────────────────────────────────

    public static <OBJ extends Obj> OBJ parse(final String code) {
        return mParser.parse(code);
    }

    public static <OBJ extends Obj> OBJ parseMulti(final String code) {
        return mParser.parseMulti(code);
    }

    public static List<Code> splitCodeAtEnd(final Code code) {
        return mParser.splitCodeAtEnd(code);
    }

    public static Obj eval(final String expression) {
        // 1. Parse the full input — the parser natively handles ; via end() sugar
        final Obj parsed = ObjmtronSerializer.parseMulti(expression);
        if (null == parsed || parsed.isNoObj()) return noobj();
        // 2. Split into independently executable segments at end() boundaries
        final List<Code> segments;
        if (parsed.isCode()) {
            segments = ObjmtronSerializer.splitCodeAtEnd(parsed.asCode());
        } else {
            // Single expression (bare value or single instruction) — wrap as a one-instruction code
            segments = List.of(MCode.of(List.of(
                    parsed.isInst() ? parsed.as() : instB(START_INST_TID, lst(parsed)))));
        }
        if (segments.isEmpty()) return noobj();
        Obj running = noobj();
        for (final Code segment : segments) {
            try {
                final Obj resolvedResult = segment.resolve(running);
                final Machine mach = SwarmMachine.of(resolvedResult.as());
                running = mach.apply(noobj());
            } catch (final Exception e) {
                throw MTronException.of(e);
            }
        }
        return running;
    }

    // ── Byte I/O ─────────────────────────────────────────────────

    @Override
    public ByteBuffer outputBytes(final Obj obj) {
        return ByteBuffer.wrap(this.write(obj).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Obj inputBytes(final ByteBuffer bytes) {
        return this.read(new String(bytes.array(), StandardCharsets.UTF_8));
    }

    // ── ID rendering ─────────────────────────────────────────────

    private String handleIds(final Obj obj, final String objString) {
        final StringBuilder sb = new StringBuilder();
        this.handleTID(sb, obj, !obj.isObjInst()).append(objString);
        this.handleVID(sb, obj);
        return sb.toString();
    }

    // ── Scalar writers ───────────────────────────────────────────

    @Override
    public String writeBytes(final Bytes bytes) {
        // the full value, always: this output is data, and a truncated byte value is not the byte value
        final StringBuilder sb = new StringBuilder();
        sb.append("0x").append(java.util.HexFormat.of().formatHex(bytes.<Bytes>as().jvm().array()));
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
        // Serializing a fail IS the report boundary — the console result
        // line, a headless -e print, an MCP/WS reply, a log line. This is
        // where the tracer fires: the deepest mtron stack capture of the
        // chain, once per chain (each failure chain emits exactly one
        // trace, no matter how many retries died before it).
        MTronException.emitStackTrace(fail.jvm());
        // Walk the cause chain (outermost first, reading right gets to the
        // root) and serialize each level's OWN message as one bracket. A
        // level whose text is already embedded in the level above's message
        // — e.g. the root's "inst apply failure: <detail>" — is not
        // re-emitted as a second bracket; genuinely distinct levels (even
        // two identical "args do not match" levels) each keep their bracket.
        Fail current = fail;
        String above = null;
        while (current != null) {
            final String msg = current.message() != null ? current.message() : "";
            if (null == above || !(msg.length() > 0 && above.length() > msg.length() && above.contains(msg)))
                sb.append("[").append(msg).append("]");
            above = msg;
            current = current.cause().orElse(null);
        }
        return handleIds(fail, sb.toString());
    }

    @Override
    public String writeStr(final Str str) {
        final String string = str.jvm();
        if (null == string)
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
        // lossless: the machine reading this back must get the exact double it wrote
        return handleIds(real, Double.toString(real.jvm()));
    }

    // ── URI writer ───────────────────────────────────────────────

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

    // ── Composite writers ────────────────────────────────────────

    @Override
    public String writeLst(final Lst lst) {
        final StringBuilder sb = new StringBuilder();
        this.handleTID(sb, lst, true);
        if (lst.isEmpty()) {
            sb.append("[,]");
        } else {
            // the full list, always: this output is data, and a clipped list is not the list
            sb.append("[");
            lst.jvm().forEach(v -> {
                this.renderValue(sb, v);
                sb.append(",");
            });
            this.cleanEnding(sb);
            sb.append("]");
        }
        return this.handleVID(sb, lst).toString();
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
        final StringBuilder sb = new StringBuilder();
        this.handleTID(sb, rec, true);
        if (rec.isEmpty()) {
            sb.append("[=>]");
        } else {
            sb.append("[");
            // the full rec, always: this output is data, and a clipped rec is not the rec
            rec.jvm().forEach((k, v) -> {
                if (v == rec)
                    throw MTronException.of("prevented infinite recursion on nested rec: key %s", k);
                sb.append(this.write(k)).append("=>");
                this.renderValue(sb, v);
                sb.append(",");
            });
            this.cleanEnding(sb);
            sb.append("]");
        }
        return this.handleVID(sb, rec).toString();
    }

    @Override
    public String writeInst(final Inst inst) {
        return this.generateInst(new StringBuilder(), inst, 0).toString();
    }

    public StringBuilder generateInst(final StringBuilder sb, final Inst inst, final int depth) {
        if (inst.isNoObj()) {
            sb.append(this.writeNoObj(noobj()));
            return sb;
        }
        if (null == inst.tid()) {
            sb.append("inst");
            this.renderInstArg(sb, depth + 1, inst.arg(0));
        } else if (inst.tid().basePath().equals(AUTO_FROM_INST_TID)) {
            sb.append("!*");
            this.renderInstArg(sb, depth + 1, inst.arg(0));
        } else if (inst.tid().basePath().equals(AUTO_AT_INST_TID) && inst.arg(1).isNoObj()) {
            sb.append("!@");
            this.renderInstArg(sb, depth + 1, inst.arg(0));
        } else if (inst.tid().basePath().equals(AUTO_INST_TID)) {
            sb.append("!");
            this.renderInstArg(sb, depth + 1, inst.arg(0));
        } else if (inst.tid().basePath().equals(FROM_INST_TID)) {
            sb.append("*");
            this.renderInstArg(sb, depth + 1, inst.arg(0));
        } else {
            final String internal = inst.args().elements()
                    .map(o -> {
                        final StringBuilder temp = new StringBuilder();
                        this.renderInstArg(temp, depth + 1, o);
                        return this.cleanEnding(temp).toString();
                    })
                    .reduce(",", (a, b) -> a + b + ",");
            sb.append(this.handleIds(inst, "(" +
                    (inst.args().isEmpty() ? "" : internal.substring(1, internal.length() - 1)) + ")" + (inst.f() == null ? "" : "{" + inst.f() + "}")));
        }
        return this.cleanEnding(sb);
    }

    @Override
    public String writeCode(final Code code) {
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
        return this.generateType(new StringBuilder(), type).toString();
    }

    @Override
    public String writeMonad(final PCMonad monad) {
        return handleIds(monad, "M[" + this.write(monad.obj()) + "<=M=>" + this.write(monad.inst()));
    }

    // ── Read ─────────────────────────────────────────────────────

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

    // ── TID / VID rendering helpers ──────────────────────────────

    protected StringBuilder handleTID(final StringBuilder sb, final Obj obj, final boolean hideBaseTID) {
        if (!obj.isFail() && !obj.isCaughtFail() && hideBaseTID && !obj.tid().hasPoly()) {
            if (Tokens.BASE_TYPES.contains(obj.tid()))
                return sb;
            else if (Tokens.BASE_TYPES.contains(obj.tid().basePath())) {
                sb.append('{').append(obj.tid().c()).append('}');
                return sb;
            }
        }
        sb.append(Router.loaded() ? Router.global().redirect(obj.tid(), false) : obj.tid());
        if (!obj.isObjInst())
            sb.append("::");
        return sb;
    }

    protected StringBuilder handleVID(final StringBuilder sb, final Obj obj) {
        if (!obj.hasVID())
            return sb;
        // through writeUri, not wrapUri: this is a uri written into the output, and a renderer tags
        // uris where the serializer writes them.  Going around it left every vid -- and every type
        // named inside a refinement or a collection -- unclickable while plain uri values were fine
        final fURI vid = Router.loaded() ? Router.global().redirect(obj.vid(), false) : obj.vid();
        return sb.append("@").append(this.writeUri(vid.toUri()));
    }

    // ── Value rendering ──────────────────────────────────────────

    /**
     * Render one value (a poly or a scalar) into the stream.  Dispatches through the virtual
     * writers, so a subclass — the UI one, chiefly — gets to draw every leaf and branch in its
     * own way.
     */
    protected void renderValue(final StringBuilder sb, final Obj v) {
        if (null == v) {
            sb.append(this.writeNoObj(noobj()));
        } else {
            this.writeClip(sb, v);
        }
    }

    protected StringBuilder renderInstArg(final StringBuilder sb, final int depth, final Obj arg) {
        if (arg.isRec()) {
            sb.append(this.writeRec(arg.asRec()));
        } else if (arg.isLst()) {
            sb.append(this.writeLst(arg.asLst()));
        } else {
            this.writeClip(sb, arg);
        }
        return sb;
    }

    /**
     * Write a scalar leaf.  The plain form is a straight dispatch to the value's own writer —
     * complete and legal — with the toShortString fallback for whatever has no writer here.
     * Clipping is a display decision and belongs to the UI serializer, which overrides this.
     */
    protected StringBuilder writeClip(final StringBuilder sb, final Obj obj) {
        if (obj.isRec()) {
            sb.append(this.writeRec(obj.asRec()));
        } else if (obj.isLst()) {
            sb.append(this.writeLst(obj.asLst()));
        } else if (obj.isInst()) {
            // through the virtual writeInst, not toShortString(): a uri or type inside the
            // instruction is written by the serializer, and only a serializer-written uri is
            // one a renderer can tag
            sb.append(this.writeInst(obj.asInst()));
        } else if (obj.isUri()) {
            sb.append(this.writeUri(obj.asUri()));
        } else if (obj.isFail()) {
            sb.append(this.writeFail(obj.asFail()));
        } else if (obj.isType()) {
            sb.append(this.writeType(obj.asType()));
        } else if (obj.isStr()) {
            sb.append(this.writeStr(obj.asStr()));
        } else if (obj.isBytes()) {
            sb.append(this.writeBytes(obj.asBytes()));
        } else {
            sb.append(obj.toShortString());
        }
        return sb;
    }

    // ── String cleaning ──────────────────────────────────────────

    protected StringBuilder cleanEnding(final StringBuilder sb) {
        char last = sb.charAt(sb.length() - 1);
        while (last == ' ' || last == ',' || last == '\n') {
            sb.deleteCharAt(sb.length() - 1);
            last = sb.charAt(sb.length() - 1);
        }
        return sb;
    }

    // ── Type generation ──────────────────────────────────────────

    private StringBuilder generateType(final StringBuilder sb, final Type type) {
        // the type's own name is a uri too, so it is written through writeUri: a renderer tags uris
        // where the serializer writes them, and appending the raw string left every type named in a
        // result (inst::T, union(…), #::T, uri::T) unclickable while the plain uri values beside it
        // were fine
        final fURI name = Router.loaded() ? Router.global().redirect(type.tid(), false) : type.tid();
        sb.append(this.writeUri(name.toUri())).append("::T");
        if (type.hasPredicate()) {
            if (type.isIsaPredicate()) {
                sb.append("[?");
                final StringBuilder temp = new StringBuilder();
                this.renderValue(temp, type.isPredicateObj());
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
            final StringBuilder temp = new StringBuilder();
            this.renderValue(temp, type.constructor());
            this.cleanEnding(temp);
            sb.append(temp);
            sb.append("]");
        }
        if (type.hasVID()) {
            if (type.vid().basePath().equals(Tokens.TYPE_TID) && !type.vid().c().isOne()) {
                sb.append("{").append(type.vid().c()).append('}');
            } else if (!type.tid().basePath().equals(type.vid().basePath()))
                this.handleVID(sb, type);
        }
        return sb;
    }

    // ── Pretty print ─────────────────────────────────────────────
    // legal mtron, laid out for a reader of code: every line is still parseable.

    public StringBuilder prettyPrintCode(final StringBuilder sb, final Obj call, final int depth) {
        if (call.isCode()) {
            for (final Inst inst : call.<Code>as().codeValue()) {
                this.prettyPrintCode(sb, inst, depth);
            }
        } else if (!call.isNoObj() && call.isObjInst()) {
            final Inst inst = call.as();
            sb.append("  ".repeat(depth)).append(this.write(inst)).append("\n");
            if (null != inst.jvm()) {
                inst.args().elements().forEach(arg -> {
                    if (arg.isObjCall() || arg.isObjs()) {
                        this.prettyPrintCode(sb, arg, depth + 1);
                    }
                });
            }
        } else if (!call.isNoObj() && call.isObjs()) {
            call.stream().forEach(o -> this.prettyPrintCode(sb, o, depth + 1));
        }
        return sb;
    }

    public static String prettyPrintCode(final Call code) {
        final StringBuilder sb = new StringBuilder();
        return ObjmtronSerializer.single().prettyPrintCode(sb, code, 0).toString();
    }
}
