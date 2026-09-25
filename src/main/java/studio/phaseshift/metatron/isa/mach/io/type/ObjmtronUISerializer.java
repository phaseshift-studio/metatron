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

import org.jline.builtins.Commands;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.docs.NanorcUtil;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Console;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Highlighter;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.util.MTronException;

import java.io.ByteArrayInputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.charset.StandardCharsets.UTF_8;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MBytes.bytes;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * The UI serializer: {@link ObjmtronSerializer} plus everything a human gets out of it.
 * This is the one that renders for whoever is looking — the console, a log line, a test,
 * another UI — where the console is only one of them:
 * <ul>
 *   <li><b>clipping</b> — recs, lsts, str, bytes, fail and real are clipped to a readable
 *       size.  Every limit is a key in the instance's own rec: {@code clip->rec},
 *       {@code clip->lst}, {@code clip->str}, {@code clip->uri}, {@code clip->real},
 *       {@code clip->bytes}, {@code clip->fail} (each an int), so a call site tunes the
 *       display without touching the class;</li>
 *   <li><b>indentation</b> — a nested poly longer than {@link #NESTED_STRING_THRESHOLD}
 *       columns breaks across indented lines, the way a reader of the console expects;</li>
 *   <li><b>{@code {{link}}} handling</b> — every uri the serializer writes is tagged as a
 *       link, which is how a renderer that can draw links (graphitty turns the rule into
 *       an OSC 8 hyperlink a click can resolve) learns the value is a uri.  Tagging uris
 *       here rather than in one consumer is what keeps {@code Graphitty} from reaching
 *       into console code to format an {@code int}:</li>
 *   <li><b>the pager</b> — when a terminal exists the render opens a pager instead of
 *       scrolling the terminal out from under the reader (see {@link #page(String)});</li>
 *   <li><b>pointer style</b> — an auto-pointer instruction ({@code !*} / {@code !@}) is
 *       drawn either as its <em>address</em> (the {@code !*<vid>} the console REPL shows)
 *       or with its <em>body</em> wrapped in one link to that address (the log style),
 *       per the instance's {@code pointer} key ({@code address} — the default — or
 *       {@code body}).
 * </ul>
 * The two access points are the ready-made instances:
 * <ul>
 *   <li>{@link #single()} — the console default (clipped, indented, linked, paged,
 *       pointer drawn as its address);</li>
 *   <li>{@link #linkBodies()} — the log style (pointer drawn as a linked body, no pager);</li>
 * </ul>
 * and {@link #of(Rec, fURI)} builds one configured from a rec — the mtron
 * constructor's job.  Note what is <em>not</em> here: no legal-mtron guarantee — this
 * output carries {@code {{link}}} markup and clip ellipses, neither of which mtron
 * parses.  Reading is inherited from the plain serializer and is still parse-driven.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ObjmtronUISerializer extends ObjmtronSerializer {

    public static final int INDENT_SIZE = 1;
    public static final int NESTED_STRING_THRESHOLD = 40;

    // ── Config key URIs ───────────────────────────────────────────
    private static final fURI KEY_CLIP = f("clip");
    private static final fURI KEY_POINTER = f("pointer");
    private static final fURI KEY_PAGER = f("pager");

    // ── Identity ──────────────────────────────────────────────────
    private static final fURI VID = OBJ_MTRON_STRING_SERIALIZER_VID.extend("ui");

    // ── Instances ─────────────────────────────────────────────────
    // This class's instances, declared rather than inherited, because a factory that is
    // inherited hands out the wrong type: a factory on the base answers with a plain
    // serializer whose writeUri emits the uri as text, so a subclass that only overrides
    // writeUri is never the one asked to write it.
    private static final ObjmtronUISerializer CONSOLE_INSTANCE = new ObjmtronUISerializer(str("address"), true);
    private static final ObjmtronUISerializer BODIES_INSTANCE = new ObjmtronUISerializer(str(Tokens.BODY), false);

    /**
     * The console instance: clipped, indented, linked, paged where a terminal is present,
     * pointer drawn as its address.
     */
    public static ObjmtronUISerializer single() {
        return CONSOLE_INSTANCE;
    }

    /**
     * The log instance: pointer drawn as a linked body, no pager.
     */
    public static ObjmtronUISerializer linkBodies() {
        return BODIES_INSTANCE;
    }

    /**
     * A serializer configured by the rec it is given — the mtron constructor's
     * ({@code obj_mtron::[...])} job.  Its own keys — clip, pointer, pager — are read
     * straight out of it, with the defaults above filling in whatever it leaves out.
     */
    public static ObjmtronUISerializer of(final Rec rec, final fURI vid) {
        return new ObjmtronUISerializer(rec.jvm(), OBJ_MTRON_SERIALIZER_TID, null == vid ? VID : vid);
    }

    // ── Constructors ──────────────────────────────────────────────

    public ObjmtronUISerializer() {
        this(str("address"), true);
    }

    private ObjmtronUISerializer(final Obj pointer, final boolean pager) {
        super(VID);
        this.at(KEY_CLIP, defaultClip(), MUTABLE);
        this.at(KEY_POINTER, pointer, MUTABLE);
        this.at(KEY_PAGER, bool(pager), MUTABLE);
    }

    protected ObjmtronUISerializer(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    @Override
    public fURI vid() {
        return VID;
    }

    // ── Config helpers ───────────────────────────────────────────

    /**
     * The default clip config method.
     */
    private static Rec defaultClip() {
        return rec(
                "rec", jnt(10),
                "lst", jnt(10),
                "str", jnt(60),
                "uri", jnt(Integer.MAX_VALUE),
                "real", jnt(4),
                "bytes", jnt(60),
                "fail", jnt(60)
        );
    }

    /**
     * The clip for one leaf: read out of the instance's own {@code clip->...} rec — a key
     * by the leaf's name ("lst") or by its full tid ("/m/lst", the mtron constructor's
     * shape) — with the default filling in whatever the config leaves out.  A single-segment
     * {@code clip} read, then a key-name match: multi-segment rec keys do not look the same
     * as flat ones to the serializer's own rec view.
     */
    private int clipVal(final String leaf, final int defaultValue) {
        final Obj sub = this.at(KEY_CLIP).orElse(noobj());
        if (sub.isNoObj() || !sub.isRec())
            return defaultValue;
        for (final Map.Entry<Obj, Obj> entry : sub.asRec().recValue().entrySet()) {
            final Obj key = entry.getKey();
            if (!key.isUri())
                continue;
            final fURI k = key.uriValue();
            final java.util.List<String> path = k.path();
            final String leafName = path.isEmpty() ? k.name() : path.get(path.size() - 1);
            if (leafName.equals(leaf) && entry.getValue().isInt())
                return entry.getValue().intValue().intValue();
        }
        return defaultValue;
    }

    private int clipBytes() {
        return this.clipVal("bytes", 60);
    }

    private int clipStr() {
        return this.clipVal("str", 60);
    }

    private int clipLst() {
        return this.clipVal("lst", 10);
    }

    private int clipRec() {
        return this.clipVal("rec", 10);
    }

    private int clipUri() {
        return this.clipVal("uri", Integer.MAX_VALUE);
    }

    private int clipReal() {
        return this.clipVal("real", 4);
    }

    private int clipFail() {
        return this.clipVal("fail", 60);
    }

    /**
     * Whether auto-pointer instructions are drawn as a linked body ({@code body}) or
     * as their address ({@code address} — the default, the console REPL style).
     */
    private boolean pointerIsBody() {
        return this.at(KEY_POINTER).orElse(str("address")).strValue().equals(Tokens.BODY);
    }

    /**
     * Whether a render longer than the terminal opens the pager (console default).
     */
    private boolean pagerOn() {
        return this.at(KEY_PAGER).orElse(bool(false)).boolValue();
    }

    // ── Top-level write: the pager is this class's, and only when a terminal is here ──

    @Override
    public String write(final Obj obj) {
        final String written = super.write(obj);
        return this.pagerOn() ? this.page(written) : written;
    }

    /**
     * Nothing to scroll: there is no terminal to scroll it in.  Reading a terminal
     * height to decide whether a value needs a pager is console business, so it happens
     * only where a terminal exists; without one the text is written as it is.
     */
    private String page(final String string) {
        if (null == Console.getTerminal() || BootLoader.TESTING)
            return string;
        if (string.split("\n").length <= Console.getTerminal().getHeight())
            return string;
        try {
            Commands.less(Console.getTerminal(), new ByteArrayInputStream(Highlighter.format(string).getBytes(UTF_8)), new PrintStream(Console.getTerminal().output()), System.err, Paths.get(""), new String[]{"--ignorercfiles"});
            return string;
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }

    // ── Real writer ──────────────────────────────────────────────
    // the display quantizes reals to clip->real decimals (the console shows 1.2346, not
    // 1.23456789); the plain serializer keeps the exact double, because it is data.

    @Override
    public String writeReal(final Real real) {
        final StringBuilder sb = new StringBuilder();
        this.handleTID(sb, real, true);
        sb.append(String.format("%." + this.clipReal() + "f", real.jvm()));
        this.handleVID(sb, real);
        return sb.toString();
    }

    private String generateString(final Str str, boolean clip) {
        if (clip) {
            final String text = str.strValue();
            final int max = this.clipStr();
            if (null != text && text.length() > max)
                return super.writeStr(str(text.substring(0, max - 1) + "..."));
        }
        final String language = str.tid().basePath().name();
        if (!NanorcUtil.supportedLanguages().contains(language))
            return super.writeStr(str);
        return "{{syntax:" + language + "}}\n" +
                super.writeStr(str) +
                "\n{{/syntax:" + language + "}}\n";
    }

    @Override
    public String writeStr(final Str str) {
        return this.generateString(str, false);
    }

    // ── Inst writer: the pointer styles ──────────────────────────

    /**
     * An auto-pointer instruction is drawn in one of two ways, per this instance's
     * {@code pointer} key:
     * <ul>
     *   <li><b>address</b> (the console REPL default) — the pointer itself is the
     *       readable: {@code !*<vid>} (the body is what the click reveals);</li>
     *   <li><b>body</b> (the log style) — the body is rendered by the PLAIN serializer
     *       and the one link wraps all of it, whose target is the pointer's vid.  Rendering
     *       the body here would tag the uri and type inside the instruction, and a link
     *       inside a link ends the outer one (graphitty's rule): the first inner link
     *       closed this wrapper, so the instruction was not clickable while its arguments were.
     * </ul>
     */
    @Override
    public String writeInst(final Inst inst) {
        if (inst.isNoObj())
            return super.writeInst(inst);
        if (Obj.Helper.isAutoPointer(inst)) {
            final fURI pointer = Obj.Helper.getAutoPointer(inst).get();
            if (this.pointerIsBody())
                return "{{link:" + pointer + "}}" + super.writeInst(inst) + "{{/link}}";
            return (inst.isAutoFrom() ? "!*" : "!@") + this.writeUri(pointer.toUri());
        }
        return super.writeInst(inst);
    }

    // ── URI / VID: the link tagging ──────────────────────────────

    @Override
    protected StringBuilder handleVID(final StringBuilder sb, final Obj obj) {
        if (!obj.hasVID())
            return sb;
        // through writeUri, not wrapUri: this is a uri written into the output, and a renderer tags
        // uris where the serializer writes them.  Going around it left every vid -- and every type
        // named inside a refinement or a collection -- unclickable while plain uri values were fine
        final fURI vid = Router.loaded() ? Router.global().redirect(obj.vid(), false) : obj.vid();
        return sb.append("{{y}}@{{/y}}").append(this.writeUriExtension(vid.toUri(), "y", true));
    }

    protected String writeUriExtension(final Uri uri, final String color, final boolean big) {
        final String uriString = super.writeUri(uri);
        final boolean quoted = uriString.startsWith("<") && uriString.endsWith(">");
        final StringBuilder sb = new StringBuilder();
        this.handleTID(sb, uri, true);
        sb.append(quoted ? "<" : "");
        sb.append("{{").append(color).append("}}{{link}}").append(big ? uri.uriValue().one().big() : uri.uriValue().one()).append("{{/link}}{{/").append(color).append("}}");
        sb.append(quoted ? ">" : "");
        return sb.toString();
    }

    @Override
    public String writeUri(final Uri uri) {
        return this.writeUriExtension(uri, "b", false);
    }

    // ── Nesting detection ────────────────────────────────────────

    private boolean isNested(final Poly<?, ?> poly) {
        if (!poly.isLst() && !poly.isRec())
            return false;
        final long count = poly.count();
        if (count < 2) return false;
        if (Graphitty.viewLength(poly.jvm().toString()) > NESTED_STRING_THRESHOLD)
            return true;
        // a rec whose entries hold values with structure — a type predicate rec (uri::T,
        // [inst::T], ...), chiefly — reads as a flat wall at full width; entries like these
        // get their own lines even when the entries themselves are short
        return poly.elements().anyMatch(x -> x.isPoly() || x.isType() || x.isInst() || x.isCode());
    }

    // ── List generation (clipped, nested) ────────────────────────

    @Override
    public String writeLst(final Lst lst) {
        return this.generateLst(new StringBuilder(), lst, 0).toString();
    }

    private StringBuilder generateLst(final StringBuilder sb, final Lst lst, final int depth) {
        this.handleTID(sb, lst, true);
        if (lst.isEmpty()) {
            sb.append("[,]");
        } else {
            final int lstClip = this.clipLst();
            final boolean nested = this.isNested(lst);
            sb.append("[");
            if (lst.count() > lstClip) {
                if (nested) sb.append("\n");
                for (int i = 0; i < lstClip; i++) {
                    if (nested) {
                        sb.append(" ".repeat((depth + 1) * INDENT_SIZE));
                    }
                    this.renderValue(sb, depth, lst.lstValue().get(i));
                    sb.append(",");
                    if (nested) sb.append("\n");
                }
                sb.append("...(").append(lst.count() - lstClip).append(" more)]");
            } else {
                if (nested) sb.append("\n");
                lst.jvm().forEach(v -> {
                    if (nested) {
                        sb.append(" ".repeat((depth + 1) * INDENT_SIZE));
                    }
                    this.renderValue(sb, nested ? depth + 1 : 0, v);
                    sb.append(",");
                    if (nested) sb.append("\n");
                });
                this.cleanEnding(sb);
                sb.append("]");
            }
        }
        return this.handleVID(sb, lst);
    }

    // ── Rec generation (clipped, nested) ─────────────────────────

    @Override
    public String writeRec(final Rec rec) {
        return this.generateRec(new StringBuilder(), rec, 0).toString();
    }

    private StringBuilder generateRec(final StringBuilder sb, final Rec rec, final int depth) {
        this.handleTID(sb, rec, true);
        if (rec.isEmpty()) {
            sb.append("[=>]");
        } else {
            final int recClip = this.clipRec();
            final boolean nested = this.isNested(rec);
            sb.append("[");
            if (rec.count() > recClip) {
                final AtomicInteger counter = new AtomicInteger(0);
                rec.indexedStream().forEach(kv -> {
                    if (counter.getAndIncrement() < recClip) {
                        if (nested) {
                            sb.append(" ".repeat((depth + 1) * INDENT_SIZE));
                        }
                        sb.append(this.write(kv.jvm().get0())).append("=>");
                        if (kv.jvm().get1() == rec)
                            throw MTronException.of("prevented infinite recursion on nested rec: key %s", kv.jvm().get0());
                        this.renderValue(sb, nested ? depth + 1 : 0, kv.jvm().get1());
                        sb.append(",");
                        if (nested) sb.append("\n");
                    }
                });
                if (nested) {
                    sb.append(" ".repeat((depth + 1) * INDENT_SIZE));
                }
                sb.append("...(").append(rec.count() - recClip).append(" more)]");
            } else {
                if (nested) sb.append("\n");
                rec.jvm().forEach((k, v) -> {
                    if (nested) {
                        sb.append(" ".repeat((depth + 1) * INDENT_SIZE));
                    }
                    sb.append(this.write(k)).append("=>");
                    if (v == rec)
                        throw MTronException.of("prevented infinite recursion on nested rec: key %s", k);
                    this.renderValue(sb, nested ? depth + 1 : 0, v);
                    sb.append(",");
                    if (nested) sb.append("\n");
                });
                this.cleanEnding(sb);
                sb.append("]");
            }
        }
        return this.handleVID(sb, rec);
    }

    // ── Value rendering (the nested path) ────────────────────────

    private void renderValue(final StringBuilder sb, final int depth, final Obj v) {
        if (null == v) {
            this.writeClip(sb, noobj());
        } else if (v.isStr()) {
            final String language = v.tid().basePath().name();
            if (NanorcUtil.supportedLanguages().contains(language)) {
                v.logger().warn("LANGUAGE FOUND: " + language);
                sb.append("{{syntax:").append(language).append("}}\n");
            }
            sb.append(this.generateString(v.asStr(), true));
            if (NanorcUtil.supportedLanguages().contains(language))
                sb.append("\n{{/syntax:").append(language).append("}}\n");
        } else if (v.isRec()) {
            this.generateRec(sb, v.as(), depth);
        } else if (v.isLst()) {
            this.generateLst(sb, v.as(), depth);
        } else {
            this.writeClip(sb, v);
        }
    }

    @Override
    protected StringBuilder renderInstArg(final StringBuilder sb, final int depth, final Obj arg) {
        if (arg.isRec()) {
            this.generateRec(sb, arg.asRec(), depth);
        } else if (arg.isLst()) {
            this.generateLst(sb, arg.asLst(), depth);
        } else {
            this.writeClip(sb, arg);
        }
        return sb;
    }

    // ── Clip writer (the UI one) ─────────────────────────────────

    /**
     * Write a scalar leaf, clipped for the reader.  An inst and a uri always go through
     * the virtual writers — a value written any other way is one no renderer can tag —
     * everything else is drawn at its clip limit, with the exact value where the limit
     * holds.
     */
    @Override
    protected StringBuilder writeClip(final StringBuilder sb, final Obj obj) {
        if (obj.isInst()) {
            sb.append(this.writeInst(obj.asInst()));
        } else if (obj.isReal()) {
            sb.append(this.writeReal(obj.asReal()));
        } else if (obj.isStr()) {
            final int max = this.clipStr();
            if (obj.strValue().length() > max)
                sb.append(this.write(str(obj.strValue().substring(0, max - 1) + "...")));
            else
                sb.append(this.writeStr(obj.asStr()));
        } else if (obj.isBytes()) {
            final int max = this.clipBytes();
            if (obj.bytesValue().capacity() > max) {
                final byte[] bb = Arrays.copyOf(obj.bytesValue().array(), max - 1);
                sb.append(this.write(bytes(ByteBuffer.wrap(bb))));
                sb.append("...");
            } else {
                sb.append(this.writeBytes(obj.asBytes()));
            }
        } else if (obj.isRec()) {
            // the same nested, clipped shape any other rec gets — this is what a type's
            // predicate rec goes through, and without it a type rendered flat while a
            // rec value of the same shape on its knees indented its entries
            this.generateRec(sb, obj.asRec(), 0);
        } else if (obj.isLst()) {
            this.generateLst(sb, obj.asLst(), 0);
        } else if (obj.isUri()) {
            final int max = this.clipUri();
            final String uriStr = obj.uriValue().toString();
            if (uriStr.length() > max)
                sb.append(this.writeUri(uri(obj.uriValue().toString().substring(0, max - 1) + "...")));
            else
                sb.append(this.writeUri(obj.asUri()));
        } else if (obj.isFail()) {
            final int max = this.clipFail();
            String message = obj.asFail().message().split("\n")[0];
            message = message.length() > max ? (message.substring(0, max - 1) + "...") : message;
            sb.append(this.writeFail(fail(message)));
            if (obj.asFail().jvm().getCause() != null)
                sb.append("[...]");
        } else if (obj.isType()) {
            // through the type renderer, not toShortString(): a type written as a pre-rendered string
            // is a type the serializer never "wrote", so a renderer could not tag it -- which is why
            // a type was clickable on its own but not inside a rec or an lst
            sb.append(this.writeType(obj.asType()));
        } else {
            sb.append(obj.toShortString());
        }
        return sb;
    }
}
