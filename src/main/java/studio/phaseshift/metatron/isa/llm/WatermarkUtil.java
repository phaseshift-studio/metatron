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

package studio.phaseshift.metatron.isa.llm;

import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Str;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.isa.web.type.MIME;
import studio.phaseshift.metatron.util.CommonUtil;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.LLM_WATERMARK_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * The in-band marker protocol an LLM uses to talk to the runtime.
 *
 * <p>A watermark is a span of the model's own output shaped
 * {@code <<TAG:KEY>>body<</TAG:KEY>>}.  It is three things at once, and this
 * class is the single place that owns all three:
 * <ol>
 *   <li><b>a signal</b> — {@code KEY} names the feature the model is addressing
 *       ({@code loop}, {@code summarize}, {@code compaction}, {@code embed},
 *       {@code midchat}, ...);</li>
 *   <li><b>a payload</b> — {@code body} is decoded by {@code TAG} into an
 *       {@link Obj}, so a watermark is a deferred instruction call whose
 *       argument rec the model wrote inline;</li>
 *   <li><b>an instruction to disappear</b> — the markup is stripped from the
 *       text, because it is a control channel, not something the user reads.</li>
 * </ol>
 *
 * <p><b>This is not a provenance watermark.</b> It marks nothing about
 * authorship; it is a control marker that happens to ride inside the token
 * stream.
 *
 * <h3>Stripping is unconditional</h3>
 * A watermark is removed whether or not its body decoded.  Stripping only on a
 * successful decode leaked the raw {@code <<TAG:KEY>>...} markup into the
 * visible response — and, through the persisted {@code chat_result}, into the
 * next chat's context.  A body that fails to decode is recorded as a
 * {@link studio.phaseshift.metatron.isa.m.type.Fail} on {@link Hit#decoded()},
 * so the failure is diagnosable without being user-visible.
 */
public final class WatermarkUtil {

    private static final GraphittyLogger LOG = Graphitty.log(WatermarkUtil.class);

    private WatermarkUtil() {
        // static gateway
    }

    /**
     * Matches {@code <<TAG:KEY>>...<</TAG:KEY>>}.  The body is lazy so the
     * first matching closer ends the span, and {@code (.*?)} (not {@code .+?})
     * so an empty body is still a watermark — one that carries no argument rec,
     * which is how a feature's zero-arg call is spelled.
     *
     * <p>Attribute-free tags ({@code \w+} on both sides of the colon) only, and
     * the closer must repeat the opener's tag and key.
     */
    private static final Pattern WATERMARK_PATTERN =
            Pattern.compile("<<(\\w+):(\\w+)>>\\s*(.*?)\\s*<</\\1:\\2>>", Pattern.DOTALL);

    /**
     * The body codec named by a watermark's tag.  Anything unrecognized is
     * mtron — the default a feature declaring no codec gets.
     *
     * <p>Note the consequence for prose: mtron is a structural parse, so a
     * plain-English body under the mtron codec does not decode (verified:
     * {@code parse('I am on it')} is a parse error).  A watermark that carries
     * prose must declare {@code txt}, which resolves to the plain-text
     * serializer.
     */
    public static MIME.MIMEType mimeOf(final String tag) {
        return switch (tag) {
            case "mtron" -> MIME.MIMEType.APPLICATION_MTRON;
            case "json" -> MIME.MIMEType.APPLICATION_JSON;
            case "html" -> MIME.MIMEType.TEXT_HTML;
            case "md" -> MIME.MIMEType.TEXT_MARKDOWN;
            case "xml" -> MIME.MIMEType.APPLICATION_XML;
            case "txt", "plain" -> MIME.MIMEType.TEXT_PLAIN;
            case "bson" -> MIME.MIMEType.APPLICATION_BSON;
            default -> MIME.MIMEType.APPLICATION_MTRON;
        };
    }

    // ── the declaration a feature makes about its own watermark ─────

    /**
     * The closing marker for {@code tag} + {@code key}.
     */
    public static String closer(final String tag, final String key) {
        return "<</%s:%s>>".formatted(tag, key);
    }

    /**
     * The opening marker for {@code tag} + {@code key}.
     *
     * <p>Marker syntax is positional and easy to mistype by hand — a doubled
     * colon ({@code <<mtron::todo>>}) matches no watermark at all, so it is
     * neither decoded nor stripped and leaks into the response.  Building the
     * literal here is what keeps the prose and the scanner agreeing.
     */
    public static String marker(final String tag, final String key) {
        return "<<%s:%s>>".formatted(tag, key);
    }

    /**
     * A feature's declared watermark key — {@code watermark => [key=>...]} on the
     * feature's own config rec, else {@code fallback}.  Declaring it lets an
     * mtron-authored config remap the key without touching Java.
     */
    public static String key(final Rec feature, final String fallback) {
        final String declared = declared(feature, KEY);
        return declared.isBlank() ? fallback : declared;
    }

    /**
     * A feature's declared watermark codec — {@code watermark => [tag=>...]} on
     * the feature's own config rec, else {@code fallback}.
     */
    public static String codec(final Rec feature, final String fallback) {
        final String declared = declared(feature, TAG);
        return declared.isBlank() ? fallback : declared;
    }

    /**
     * Read one field of a feature's {@code watermark} declaration, or {@code ""}.
     */
    private static String declared(final Rec feature, final String field) {
        if (null == feature)
            return "";
        final Obj declaration = feature.at(uri(WATERMARK));
        if (!declaration.isRec())
            return "";
        final Obj value = declaration.asRec().at(uri(field));
        return value.isStr() ? value.strValue() : "";
    }

    /**
     * What every watermark skill must say, written once.  Composed as
     * {@code body + "\n\n" + SHARED} so each feature owns only the part that is
     * actually about its own watermark.
     */
    private static final String SHARED_INSTRUCTIONS = """
                                                      **IMPORTANT**: this is about formatting your response, not calling a function.
                                                      The watermark is stripped from what the user sees — write it for the runtime, not the reader.
                                                      """;

    /**
     * Compose a feature's skill content: its own prose, then the shared
     * disclaimer every watermark skill owes the model.
     *
     * <p>Warns when the prose never shows the marker it is asking for — the
     * check that would have caught a hand-typed {@code <<mtron::todo>>} at
     * registration instead of in production.
     *
     * @param tag  the codec the feature declared
     * @param key  the key the feature declared
     * @param body the feature's own instructions
     */
    public static String instructions(final String tag, final String key, final String body) {
        if (!body.contains(marker(tag, key)))
            LOG.warn("skill prose never shows a %s marker — the model cannot learn the syntax it is asked to emit",
                    marker(tag, key));
        return body.stripTrailing() + "\n\n" + SHARED_INSTRUCTIONS;
    }

    /**
     * Decode a watermark body by its tag.  Never throws: a malformed body is a
     * {@code fail}, because a model-authored protocol has to survive the model
     * getting it wrong — the alternative is an exception escaping the streaming
     * callback that received it.
     *
     * <p>An <b>empty</b> body is not malformed — it is a zero-arg call, and
     * decodes to {@code noobj} under every codec.  So
     * {@code <<mtron:compaction>><</mtron:compaction>>} means "compact() with your
     * defaults" and needs no {@code [=>]} placeholder to be recognized.
     */
    public static Obj decode(final String tag, final String key, final String body) {
        if (null == body || body.isBlank())
            return noobj();
        try {
            return mimeOf(tag).fromBytes(body.getBytes(StandardCharsets.UTF_8));
        } catch (final Exception e) {
            LOG.warn("undecodable <<%s:%s>> watermark: %s", tag, key, e.getMessage());
            return fail(e);
        }
    }

    /**
     * One watermark found in a stream, with the span it occupied.
     *
     * @param tag     the body codec ({@code mtron}, {@code json}, ...)
     * @param key     the feature the watermark addresses ({@code loop}, ...)
     * @param body    the raw payload text, whitespace-trimmed by the pattern
     * @param decoded the body as an obj — a {@code fail} when it did not decode
     * @param start   span start in the scanned text (the first {@code <})
     * @param end     span end in the scanned text (one past the last {@code >})
     */
    public record Hit(String tag, String key, String body, Obj decoded, int start, int end) {
    }

    /**
     * The result of scanning one stream of text: what is left for the user to
     * read, every watermark that was taken out of it, and the lifecycle stage
     * the text came from (which decides how a consumer may use it).
     */
    public record Scan(String visible, List<Hit> hits, String stage) {

        /**
         * True when the text carried no watermarks at all.
         */
        public boolean isEmpty() {
            return this.hits.isEmpty();
        }

        /**
         * The decoded body of the last watermark under {@code key} — see
         * {@link WatermarkUtil#get(Obj, String)} for what the returned {@code noobj}
         * does and does not distinguish.
         */
        public Obj get(final String key) {
            return WatermarkUtil.get(this.list(), key);
        }

        /**
         * Whether the text carried a watermark under {@code key} at all —
         * decoded or not.  Together with {@link #get(String)} this distinguishes
         * the three cases a consumer cares about: absent, present-and-decoded,
         * and present-but-undecodable (so the failure can be reported back to
         * the model rather than silently ignored).
         */
        public boolean has(final String key) {
            return this.hits.stream().anyMatch(hit -> hit.key().equals(key));
        }

        /**
         * Every successfully decoded watermark as a {@code key => obj} map — the
         * shape features read.  An undecodable body is deliberately absent (its
         * failure is still on {@link #hits()}), so a malformed watermark stays a
         * no-op for consumers rather than handing them a {@code fail} where they
         * expect an argument rec.  A repeated key keeps its last value; the full
         * ordered list, duplicates included, is {@link #hits()}.
         */
        public Map<Obj, Obj> collect() {
            final Map<Obj, Obj> collected = new LinkedHashMap<>();
            for (final Hit hit : this.hits)
                if (!hit.decoded().isFail())
                    collected.put(uri(hit.key()), hit.decoded());
            return collected;
        }

        /**
         * The watermarks as an ordered {@code lst(watermark::T)} — the shape a
         * chat_result publishes.  The model's own order is kept, duplicates
         * included, and an undecodable body keeps its place with {@code error}
         * set rather than vanishing: a failed signal is still evidence.
         */
        public Lst list() {
            final List<Obj> watermarks = new ArrayList<>();
            for (int i = 0; i < this.hits.size(); i++) {
                final Hit hit = this.hits.get(i);
                watermarks.add(rec(mutableMap(
                        uri(TAG), str(hit.tag()),
                        uri(KEY), str(hit.key()),
                        uri(BODY), str(hit.body()),
                        uri(OBJ), hit.decoded().isFail() ? noobj() : hit.decoded(),
                        uri(ERROR), hit.decoded().isFail() ? hit.decoded() : noobj(),
                        uri(INDEX), jnt(i),
                        uri(STAGE), uri(this.stage)), LLM_WATERMARK_TID, null));
            }
            return lst(watermarks);
        }
    }

    /**
     * The watermark under {@code key} whose body did not decode, or
     * {@code noobj}.  Absent both when the model emitted no such marker and when
     * it decoded cleanly — {@link Scan#has(String)} tells those apart when it
     * matters.
     */
    public static Obj failed(final Obj watermarks, final String key) {
        if (null == watermarks || !watermarks.isLst())
            return noobj();
        Obj rejected = noobj();
        for (final Obj watermark : watermarks.asLst().elements().toList()) {
            if (!watermark.isRec() || !Str.Helper.cleanString(watermark.asRec().at(uri(KEY))).equals(key))
                continue;
            final Rec candidate = watermark.asRec();
            rejected = candidate.at(uri(ERROR)).isNoObj() ? noobj() : candidate; // last wins
        }
        return rejected;
    }

    /**
     * A report on a watermark that did not decode, for feeding back to the model
     * on its next pass.  Without this the model is told nothing, so it repeats
     * the same malformed marker indefinitely.
     */
    public static String report(final String tag, final String key, final Rec watermark) {
        return "%s was not applied: %s. body was: %s".formatted(
                marker(tag, key),
                Str.Helper.cleanString(watermark.at(uri(ERROR))),
                CommonUtil.clipString(Str.Helper.cleanString(watermark.at(uri(BODY))), 120, true));
    }

    /**
     * The decoded body of the last watermark under {@code key} in a published
     * watermark lst — {@code noobj} when the key is absent, when the model wrote
     * an empty body (a zero-arg call), or when the body did not decode.
     *
     * <p>That is three different situations collapsed into one return value, so
     * ask {@link #has(Obj, String)} whether the model addressed the key at all
     * and {@link #failed(Obj, String)} whether it did so badly.  A consumer that
     * wants "the call's argument rec, or an empty one" should read
     * {@link studio.phaseshift.metatron.isa.llm.type.ChatResult#watermark(String)},
     * which resolves all four cases.
     */
    public static Obj get(final Obj watermarks, final String key) {
        if (null == watermarks || !watermarks.isLst())
            return noobj();
        Obj found = noobj();
        for (final Obj watermark : watermarks.asLst().elements().toList()) {
            if (!watermark.isRec() || !Str.Helper.cleanString(watermark.asRec().at(uri(KEY))).equals(key))
                continue;
            final Obj body = watermark.asRec().at(uri(OBJ));
            found = body.isFail() ? noobj() : body; // last wins; a fail is not an argument
        }
        return found;
    }

    /**
     * Whether the model addressed {@code key} at all — decoded, empty, or
     * undecodable.  This is the presence test that makes an empty body usable:
     * without it a zero-arg watermark is indistinguishable from no watermark.
     */
    public static boolean has(final Obj watermarks, final String key) {
        if (null == watermarks || !watermarks.isLst())
            return false;
        return watermarks.asLst().elements()
                .anyMatch(watermark -> watermark.isRec()
                        && Str.Helper.cleanString(watermark.asRec().at(uri(KEY))).equals(key));
    }

    /**
     * Scan a completed response — the stage every response watermark arrives at.
     */
    public static Scan scan(final String text) {
        return scan(text, ON_COMPLETE_RESPONSE, true);
    }

    /**
     * Scan {@code text} for watermarks from the given lifecycle stage.
     */
    public static Scan scan(final String text, final String stage) {
        return scan(text, stage, true);
    }

    /**
     * Scan {@code text} for watermarks — one pass, no exceptions.
     *
     * <p>The visible text is rebuilt from the retained spans rather than mutated
     * in place, so stripping is correct regardless of what the bodies decoded to.
     *
     * @param stripTrailing whether to trim trailing whitespace off the visible
     *                      text.  True for a completed response, where whatever
     *                      follows the last watermark is noise.  <b>False
     *                      mid-stream</b>: a chunk boundary is not the end of the
     *                      sentence, so trimming per chunk eats the space between
     *                      a watermark and the words that follow it.
     */
    public static Scan scan(final String text, final String stage, final boolean stripTrailing) {
        final List<Hit> hits = new ArrayList<>();
        final StringBuilder visible = new StringBuilder();
        final Matcher matcher = WATERMARK_PATTERN.matcher(text);
        int cursor = 0;
        while (matcher.find()) {
            final String tag = matcher.group(1);
            final String key = matcher.group(2);
            final String body = matcher.group(3);
            visible.append(text, cursor, matcher.start());
            cursor = matcher.end();
            hits.add(new Hit(tag, key, body, decode(tag, key, body), matcher.start(), matcher.end()));
        }
        visible.append(text, cursor, text.length());
        final String stripped = visible.toString();
        return new Scan(stripTrailing ? stripped.stripTrailing() : stripped, List.copyOf(hits), stage);
    }

    /**
     * The pattern this class scans with — exposed so tests can assert its shape.
     */
    public static Pattern pattern() {
        return WATERMARK_PATTERN;
    }

    // ── streaming: harvesting a watermark as it arrives ────────────

    /**
     * Where a harvested watermark goes — whether to relay it belongs to the caller.
     */
    @FunctionalInterface
    public interface Sink {
        void accept(Hit hit);
    }

    /**
     * The suffix of {@code value} that could still become a watermark, and so must
     * not be shown to anyone yet.
     *
     * <p>A stream arrives in chunks that do not respect marker boundaries, so an
     * opener can land in one chunk and its closer several chunks later.  Everything
     * from the last opener that has not resolved is held back and prepended to the
     * next chunk — the same contract as
     * {@code Str.pendingTemplateTail(String)}, which does this for {@code ${...}}
     * templates, generalised to {@code <<tag:key>>...}.
     */
    public static String pendingTail(final String value) {
        if (null == value || value.isEmpty())
            return "";
        // past everything that already resolved into a watermark
        int cursor = 0;
        final Matcher matcher = WATERMARK_PATTERN.matcher(value);
        while (matcher.find())
            cursor = matcher.end();
        // the first opener after that which has not resolved — a closer is not one
        for (int i = value.indexOf("<<", cursor); i >= 0; i = value.indexOf("<<", i + 1))
            if (i + 2 >= value.length() || value.charAt(i + 2) != '/')
                return value.substring(i);
        // a trailing lone '<' may be the first half of an opener
        final int last = value.length() - 1;
        if (last >= cursor && value.charAt(last) == '<' && (last == 0 || value.charAt(last - 1) != '<'))
            return value.substring(last);
        return "";
    }

    /**
     * Feed one streaming chunk through the watermark scan, holding back whatever
     * could still become one.
     *
     * <p>{@code hold} is the caller's carry buffer: the chunk is appended, the
     * settled prefix is scanned, each watermark is handed to {@code sink}, and the
     * unresolved tail is left in {@code hold} for the next chunk.  So a marker split
     * across two chunks is neither rendered as raw text nor reported twice.
     *
     * @return the text that is now safe to show — everything settled except the
     * watermarks themselves
     */
    public static String harvest(final StringBuilder hold, final String chunk,
                                 final String stage, final Sink sink) {
        hold.append(chunk);
        final String buffered = hold.toString();
        final String tail = pendingTail(buffered);
        // mid-stream: never trim, a chunk boundary is not the end of a sentence
        final Scan scan = scan(buffered.substring(0, buffered.length() - tail.length()), stage, false);
        hold.setLength(0);
        hold.append(tail);
        scan.hits().forEach(sink::accept);
        return scan.visible();
    }
}
