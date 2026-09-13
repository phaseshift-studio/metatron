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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.llm.type.ChatResult;
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Str;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * The watermark protocol: scanning, decoding, and stripping.
 *
 * <p>The matrix below is the contract — for each text, what the user is left
 * with, which watermarks were found, and which of those decoded.  The last two
 * columns differ exactly when a body failed to decode: such a watermark is
 * still <b>found and stripped</b> (it is control markup, not user text) but is
 * deliberately absent from {@link WatermarkUtil.Scan#collect()}, so consumers see
 * a no-op rather than a {@code fail} where they expect an argument rec.
 */
public class WatermarkUtilTest extends AbstractMetatronTest {

    private static final String NONE = "<none>";
    private static final String PROMPT = "prompt";

    @ParameterizedTest
    @CsvSource(value = {
            // no watermark at all — the text passes through untouched
            "the answer is four"
                    + " % the answer is four"
                    + " % <none> % <none>",
            // a plain watermark: decoded, collected, and removed from the text
            "4 is the answer <<mtron:loop>>[prompt=>go]<</mtron:loop>>"
                    + " % 4 is the answer"
                    + " % loop % loop",
            // a prose body rides the plain-text codec — the midchat shape
            "<<txt:midchat>>I am on it<</txt:midchat>>done"
                    + " % done"
                    + " % midchat % midchat",
            // an EMPTY body is still a watermark: a feature's zero-arg call
            "<<txt:empty>><</txt:empty>>visible"
                    + " % visible"
                    + " % empty % empty",
            // UNDECODABLE (prose under the mtron codec): stripped and recorded,
            // never collected — the leak this class exists to prevent
            "prose <<mtron:midchat>>I am on it<</mtron:midchat>> tail"
                    + " % 'prose  tail'"
                    + " % midchat % <none>",
            // a json body decodes through the json codec
            "<<json:claim>>[1,2]<</json:claim>>ok"
                    + " % ok"
                    + " % claim % claim",
            // two watermarks are BOTH found, in order; the rec collection
            // is keyed, so each distinct key appears once
            "a <<mtron:one>>[x=>1]<</mtron:one>> b <<mtron:two>>[y=>2]<</mtron:two>> c"
                    + " % 'a  b  c'"
                    + " % one+two % one+two",
    }, delimiter = '%', quoteCharacter = '\'')
    void testScanContract(final String text, final String visible, final String found, final String collected) {
        final WatermarkUtil.Scan scan = WatermarkUtil.scan(text);
        assertEquals(visible, scan.visible(), "visible text after stripping");
        assertEquals(found, keysOfHits(scan), "every watermark found in the text");
        assertEquals(collected, keysOfCollected(scan), "watermarks that decoded");
    }

    @Test
    void testUndecodableBodyIsRecordedAsFail() {
        final WatermarkUtil.Scan scan = WatermarkUtil.scan("prose <<mtron:midchat>>I am on it<</mtron:midchat>> tail");
        assertEquals(1, scan.hits().size(), "the watermark is found even though its body is not mtron");
        assertTrue(scan.hits().get(0).decoded().isFail(), "an undecodable body surfaces as a fail on the hit");
        assertTrue(scan.has("midchat"), "has() reports the watermark was there");
        assertTrue(scan.get("midchat").isNoObj(), "get() withholds a fail — it answers only for decoded bodies");
        assertEquals(NONE, keysOfCollected(scan), "a fail is never collected as an argument");
    }

    @Test
    void testEmptyBodyUnderMtronCodecIsStillFound() {
        // what it DECODES to is the mtron serializer's business; that the span is
        // recognized and removed is the scanner's — and that is the fix
        final WatermarkUtil.Scan scan = WatermarkUtil.scan("<<mtron:nothing>><</mtron:nothing>>visible");
        assertEquals("visible", scan.visible(), "an empty body no longer leaves the markup in the text");
        assertEquals("nothing", keysOfHits(scan), "an empty body is a watermark");
    }

    @Test
    void testDecodePerTag() {
        final WatermarkUtil.Scan mtron = WatermarkUtil.scan("<<mtron:loop>>[prompt=>go]<</mtron:loop>>");
        assertTrue(mtron.get("loop").isRec(), "an mtron body decodes to a rec");
        assertEquals("go", Str.Helper.cleanString(mtron.get("loop").asRec().at(uri(PROMPT))),
                "the decoded rec carries the model's argument");

        final WatermarkUtil.Scan json = WatermarkUtil.scan("<<json:claim>>[1,2]<</json:claim>>");
        assertTrue(json.get("claim").isLst(), "a json body decodes to a lst");

        final WatermarkUtil.Scan txt = WatermarkUtil.scan("<<txt:midchat>>I am on it<</txt:midchat>>");
        assertTrue(txt.get("midchat").isStr(), "a prose body decodes to a str");
        assertEquals("I am on it", txt.get("midchat").strValue(), "and keeps its text verbatim");
    }

    @Test
    void testCollectKeepsLastValueForRepeatedKey() {
        final WatermarkUtil.Scan scan = WatermarkUtil.scan(
                "<<mtron:loop>>[prompt=>first]<</mtron:loop>> <<mtron:loop>>[prompt=>second]<</mtron:loop>>");
        assertEquals(2, scan.hits().size(), "both watermarks are found");
        assertEquals("first", Str.Helper.cleanString(scan.hits().get(0).decoded().asRec().at(uri(PROMPT))),
                "hits preserve request order");
        assertEquals("second", Str.Helper.cleanString(scan.get("loop").asRec().at(uri(PROMPT))),
                "the collected view is last-wins");
    }

    @Test
    void testHitsCarryTheirSpans() {
        final String text = "before <<txt:midchat>>hi<</txt:midchat>> after";
        final WatermarkUtil.Scan scan = WatermarkUtil.scan(text);
        final WatermarkUtil.Hit hit = scan.hits().get(0);
        assertEquals("<<txt:midchat>>hi<</txt:midchat>>", text.substring(hit.start(), hit.end()),
                "a hit's span covers exactly the markup, so a caller can locate it in the original text");
        assertEquals("before  after", scan.visible(), "the retained segments keep their original spacing");
    }

    @Test
    void testTrailingWhitespaceIsStrippedFromVisibleText() {
        assertEquals("the answer", WatermarkUtil.scan("the answer  \n\n   ").visible(),
                "visible text is stripTrailing-ed");
        assertEquals("the answer", WatermarkUtil.scan("the answer <<txt:note>>x<</txt:note>>\n").visible(),
                "and still is when a watermark preceded the whitespace");
    }

    @Test
    void testUnterminatedWatermarkSwallowsTheNextOne() {
        // KNOWN LIMITATION, pinned so it is visible rather than surprising: the
        // scan is a single lazy regex, not a nesting-aware scanner, so an opener
        // with no closer reaches forward to the NEXT closer and takes that span
        // with it.  A model that loses one closer therefore costs two signals.
        // A stack-based scanner is the fix when this bites.
        final WatermarkUtil.Scan scan = WatermarkUtil.scan(
                "a <<mtron:loop>>[x=>1] b <<mtron:loop>>[y=>2]<</mtron:loop>> c");
        assertEquals(1, scan.hits().size(), "one span, not two — the unterminated opener absorbed the terminated one");
        assertEquals("a  c", scan.visible(), "and both spans are removed from the visible text");
    }

    @Test
    void testScanIsExceptionFreeForArbitraryText() {
        // the scanner runs inside a streaming callback: it must never throw
        for (final String text : List.of("", "<", "<<", "<<mtron:", "<<mtron:loop>>", "<<a:b>>", "<</a:b>>",
                "<<mtron:loop>><</mtron:other>>", "<<:>>", "<<mtron:loop>>\n<</mtron:loop>>"))
            assertNotNull(WatermarkUtil.scan(text), "scan is total for: " + text);
    }

    @Test
    void testNoWatermarkLeavesTextAlone() {
        final WatermarkUtil.Scan scan = WatermarkUtil.scan("a plain answer, with commas, and 'quotes'.");
        assertTrue(scan.isEmpty(), "text without watermarks reports no hits");
        assertEquals("a plain answer, with commas, and 'quotes'.", scan.visible(), "and passes through verbatim");
        assertEquals(NONE, keysOfHits(scan), "nothing found");
    }

    // ── the published shape: lst(watermark::T) ─────────────────────

    @Test
    void testListCarriesEveryWatermarkInOrder() {
        final WatermarkUtil.Scan scan = WatermarkUtil.scan(
                "<<mtron:loop>>[prompt=>go]<</mtron:loop>> mid <<txt:midchat>>on it<</txt:midchat>>");
        final Lst list = scan.list();
        assertEquals(2L, list.count(), "one watermark rec per marker");
        final Rec first = list.at(jnt(0)).asRec();
        assertEquals("loop", first.at(uri(KEY)).strValue(), "the key the model addressed");
        assertEquals("mtron", first.at(uri(TAG)).strValue(), "the codec it named");
        assertEquals("[prompt=>go]", first.at(uri(BODY)).strValue(), "the raw body, trimmed");
        assertEquals(0, first.at(uri(INDEX)).intValue().intValue(), "index is the emission ordinal");
        assertEquals(uri(ON_COMPLETE_RESPONSE), first.at(uri(STAGE)), "stage is where it was harvested");
        assertEquals("midchat", list.at(jnt(1)).asRec().at(uri(KEY)).strValue(), "order is the model's own");
    }

    @Test
    void testListKeepsAnUndecodableWatermark() {
        final Lst list = WatermarkUtil.scan("<<mtron:midchat>>I am on it<</mtron:midchat>>").list();
        assertEquals(1L, list.count(), "a failed signal is still evidence, so it keeps its place");
        final Rec watermark = list.at(jnt(0)).asRec();
        assertTrue(watermark.at(uri(OBJ)).isNoObj(), "there is no decoded argument rec");
        assertTrue(watermark.at(uri(ERROR)).isFail(), "the failure rides alongside it instead");
    }

    @Test
    void testGetReadsTheAddressedArgumentRec() {
        final Lst list = WatermarkUtil.scan("<<mtron:loop>>[prompt=>go]<</mtron:loop>>").list();
        assertEquals("go", Str.Helper.cleanString(WatermarkUtil.get(list, "loop").asRec().at(uri(PROMPT))),
                "the rec the model addressed to the feature");
        assertTrue(WatermarkUtil.get(list, "summarize").isNoObj(), "an unaddressed key reads noobj");
        assertTrue(WatermarkUtil.get(null, "loop").isNoObj(), "and so does a null collection");
        assertTrue(WatermarkUtil.has(list, "loop"), "has() answers presence");
        assertFalse(WatermarkUtil.has(list, "summarize"), "and absence");
    }

    @Test
    void testChatResultResolvesTheFourWatermarkCases() {
        assertTrue(ChatResult.chatResult().watermark("loop").isNoObj(), "not addressed reads noobj");

        final ChatResult empty = ChatResult.chatResult()
                .put(WATERMARK, WatermarkUtil.scan("<<mtron:loop>><</mtron:loop>>").list());
        assertTrue(empty.watermark("loop").isRec(), "an empty body is a zero-arg call, not an absent one");
        assertTrue(empty.watermark("loop").asRec().isEmpty(), "and reads as an empty argument rec");

        final ChatResult decoded = ChatResult.chatResult()
                .put(WATERMARK, WatermarkUtil.scan("<<mtron:loop>>[prompt=>go]<</mtron:loop>>").list());
        assertEquals("go", Str.Helper.cleanString(decoded.watermark("loop").asRec().at(uri(PROMPT))),
                "a decoded body is handed back as the call's argument rec");

        final ChatResult broken = ChatResult.chatResult()
                .put(WATERMARK, WatermarkUtil.scan("<<mtron:loop>>I am on it<</mtron:loop>>").list());
        assertTrue(broken.watermark("loop").isNoObj(), "an undecodable body leaves no argument to act on");
        assertTrue(WatermarkUtil.failed(broken.watermarks(), "loop").isRec(), "though the failure is still recorded");

        assertTrue(ChatResult.chatResult().watermarks().isEmpty(), "a chat_result with no watermarks reads empty");
    }

    @Test
    void testAnEmptyBodyIsAZeroArgCallNotAFailure() {
        final Lst list = WatermarkUtil.scan("<<mtron:compaction>><</mtron:compaction>>").list();
        assertEquals(1L, list.count(), "the watermark is recognized and recorded");
        assertTrue(WatermarkUtil.has(list, "compaction"), "the model did address the key");
        assertTrue(WatermarkUtil.get(list, "compaction").isNoObj(), "with no argument rec of its own");
        assertTrue(WatermarkUtil.failed(list, "compaction").isNoObj(),
                "and nothing is reported back — an empty body is not a mistake");
    }

    // ── the feature declaration and the shared skill prose ────────

    @ParameterizedTest
    @CsvSource(value = {
            "mtron % loop % <<mtron:loop>>",
            "txt % midchat % <<txt:midchat>>",
            "json % claim % <<json:claim>>",
    }, delimiter = '%', quoteCharacter = '\'')
    void testMarkerShape(final String tag, final String key, final String expected) {
        assertEquals(expected, WatermarkUtil.marker(tag, key), "a marker is always <<tag:key>>");
        assertEquals("<</" + tag + ":" + key + ">>", WatermarkUtil.closer(tag, key), "and its closer mirrors it");
    }

    @Test
    void testBuiltMarkerIsOneTheScannerFinds() {
        // The point of building the literal: an author who mistypes it writes
        // <<mtron::todo>>, which matches nothing, so the marker is neither
        // decoded nor stripped.  Built here, the two always agree.
        final String marker = WatermarkUtil.marker("mtron", "loop");
        final String closer = WatermarkUtil.closer("mtron", "loop");
        final WatermarkUtil.Scan scan = WatermarkUtil.scan("before " + marker + "[prompt=>go]" + closer + " after");
        assertEquals("before  after", scan.visible(), "the built marker is found and stripped");
        assertEquals("loop", keysOfHits(scan), "and registers under the key the author named");
    }

    @Test
    void testDeclarationOverridesKeyAndCodec() {
        final Rec declared = rec(mutableMap(uri(WATERMARK), rec(mutableMap(
                uri(KEY), str("looping"),
                uri(TAG), str("txt")))));
        assertEquals("looping", WatermarkUtil.key(declared, "loop"), "a declared key wins over the default");
        assertEquals("txt", WatermarkUtil.codec(declared, "mtron"), "so does a declared codec");
        assertEquals("loop", WatermarkUtil.key(rec(), "loop"), "an undeclared key falls back");
        assertEquals("mtron", WatermarkUtil.codec(null, "mtron"), "and so does an undeclared codec");
    }

    @Test
    void testInstructionsAppendTheSharedDisclaimer() {
        final String composed = WatermarkUtil.instructions("mtron", "loop", "use <<mtron:loop>> to continue");
        assertTrue(composed.startsWith("use <<mtron:loop>>"), "the feature's own prose comes first");
        assertTrue(composed.contains("not calling a function"), "the shared disclaimer is appended");
        assertTrue(composed.contains("stripped from what the user sees"), "including what happens to the markup");
    }

    @Test
    void testFailedFindsOnlyAnUndecodableBody() {
        final Lst decoded = WatermarkUtil.scan("<<mtron:loop>>[prompt=>go]<</mtron:loop>>").list();
        assertTrue(WatermarkUtil.failed(decoded, "loop").isNoObj(), "a decoded watermark is not a failure");
        final Lst broken = WatermarkUtil.scan("<<mtron:loop>>I am on it<</mtron:loop>>").list();
        assertTrue(WatermarkUtil.failed(broken, "loop").isRec(), "an undecodable one is");
        assertTrue(WatermarkUtil.failed(broken, "summarize").isNoObj(), "and only under its own key");
        assertTrue(WatermarkUtil.failed(null, "loop").isNoObj(), "no collection, no failure");
    }

    @Test
    void testReportNamesTheMarkerAndQuotesTheBody() {
        final Lst broken = WatermarkUtil.scan("<<mtron:loop>>I am on it<</mtron:loop>>").list();
        final String report = WatermarkUtil.report("mtron", "loop", WatermarkUtil.failed(broken, "loop").asRec());
        assertTrue(report.startsWith("<<mtron:loop>> was not applied"), "the report names the marker to correct");
        assertTrue(report.contains("body was:"), "and quotes what the model actually wrote");
    }

    // ── streaming: hold back what could still become a watermark ──

    @ParameterizedTest
    @CsvSource(value = {
            "a plain answer % <none>",
            "a < % <",
            "a << % <<",
            "a <<mtron:loo % <<mtron:loo",
            "a <<mtron:loop>>[x=>1]<</mtron:loop>> b % <none>",
            "a <<mtron:loop>>[x=>1]<</mtron:loop>> b <<txt: % <<txt:",
            "a <<mtron:loop>>[x=>1]<</mtron:loo % <<mtron:loop>>[x=>1]<</mtron:loo",
            "a <</mtron:loop>> b % <none>",
    }, delimiter = '%', quoteCharacter = '\'')
    void testPendingTail(final String buffered, final String expected) {
        final String held = WatermarkUtil.pendingTail(buffered);
        assertEquals("<none>".equals(expected) ? "" : expected, held, "held-back tail of: " + buffered);
    }

    @Test
    void testHarvestHoldsAMarkerSplitAcrossChunks() {
        final StringBuilder hold = new StringBuilder();
        final List<String> relayed = new ArrayList<>();
        final StringBuilder shown = new StringBuilder();
        final WatermarkUtil.Sink sink = hit -> relayed.add(hit.key());
        shown.append(WatermarkUtil.harvest(hold, "I am on it", ON_PARTIAL_THINKING, sink));
        shown.append(WatermarkUtil.harvest(hold, ". <<txt:mid", ON_PARTIAL_THINKING, sink));
        shown.append(WatermarkUtil.harvest(hold, "chat>>still here<</txt:mid", ON_PARTIAL_THINKING, sink));
        shown.append(WatermarkUtil.harvest(hold, "chat>>. carry on", ON_PARTIAL_THINKING, sink));
        assertEquals("midchat", String.join("+", relayed), "the split marker is relayed exactly once");
        assertEquals("I am on it. . carry on", shown.toString(), "and never rendered as raw markup");
        assertEquals("", hold.toString(), "with nothing left held back");
    }

    @Test
    void testHarvestOfPlainTextEmitsEverything() {
        final StringBuilder hold = new StringBuilder();
        final List<String> relayed = new ArrayList<>();
        final String shown = WatermarkUtil.harvest(hold, "no markers here", ON_PARTIAL_THINKING, hit -> relayed.add(hit.key()));
        assertEquals("no markers here", shown, "plain text is shown immediately");
        assertEquals(0, relayed.size(), "and nothing is relayed");
        assertEquals("", hold.toString(), "and nothing is held");
    }

    // ── helpers ────────────────────────────────────────────────────

    private static String keysOfHits(final WatermarkUtil.Scan scan) {
        final List<String> keys = scan.hits().stream().map(WatermarkUtil.Hit::key).toList();
        return keys.isEmpty() ? NONE : String.join("+", keys);
    }

    private static String keysOfCollected(final WatermarkUtil.Scan scan) {
        final Collection<Obj> keys = scan.collect().keySet();
        if (keys.isEmpty())
            return NONE;
        return String.join("+", keys.stream()
                .map(k -> k.isUri() ? k.uriValue().name() : Str.Helper.cleanString(k))
                .toList());
    }
}
