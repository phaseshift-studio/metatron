package studio.phaseshift.metatron.isa.mach.type.ui.widget;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Console;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.ui.uiInstSet.UI_ACCORDION_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

public class AccordionWidgetTest extends AbstractMetatronTest {

    private static final fURI TID = f("/m/mach/ui/widget/accordion");

    @Test
    public void shouldRenderTitleFromJvm() {
        final AccordionWidget a = new AccordionWidget(mutableMap(
                uri("title"), str("Test Title"),
                uri("body"), str("Body line")), TID, null);
        assertTrue(a.format().contains("Test Title"));
    }

    @Test
    public void shouldRenderBodyFromJvm() {
        final AccordionWidget a = new AccordionWidget(mutableMap(uri("title"), str("T"),
                uri("body"), str("Body line")), TID, null);
        assertTrue(a.format().contains("Body line"));
    }

    @Test
    public void shouldRenderBodyAsObjs() {
        final AccordionWidget a = new AccordionWidget(mutableMap(uri("title"), str("T"),
                uri("body"), objs(str("line1"), str("line2"))), UI_ACCORDION_TID, null);
        final String r = a.format();
        assertTrue(r.contains("line1"), "body should contain line1 in: " + r);
        assertTrue(r.contains("line2"), "body should contain line2 in: " + r);
    }

    @Test
    public void shouldRenderEmptyBodyWhenNotProvided() {
        final AccordionWidget a = new AccordionWidget(mutableMap(uri("title"), str("X")), TID, null);
        assertTrue(a.format().contains("X"));
    }

    @Test
    public void shouldRenderBasicFormat() {
        final AccordionWidget a = new AccordionWidget(mutableMap(uri("title"), str("Hi")), TID, null);
        final String r = a.format();
        assertTrue(r.contains("[-]"));
        assertTrue(r.contains("Hi"));
        new Console(rec(), f("/sys/console")); // TODO: move to AbstractWidgetTest and force all widgets to test run()
        a.run();
    }

    @Test
    public void shouldPadBodyToTheStyleHeightWhenTallerThanItsContent() {
        final AccordionWidget a = new AccordionWidget("notes", "one\ntwo");
        a.expand();
        assertEquals(4, a.format().split("\n", -1).length, "natural height: title + 2 body + border");
        a.style().height(8).applyStyle();
        assertEquals(8, a.format().split("\n", -1).length,
                "an explicit height grows the box past its content — a resize that only clips reads as locked");
    }

    @Test
    public void shouldCollapseToTheHeaderEvenAfterAResize() {
        final AccordionWidget a = new AccordionWidget("notes", "one\ntwo");
        a.expand();
        a.style().height(8).applyStyle();   // resized tall
        assertEquals(8, a.format().split("\n", -1).length, "expanded: padded to the height");
        a.collapse();
        assertEquals(2, a.format().split("\n", -1).length,
                "collapsed: shrinks to just the header — no residual height padding");
    }

    @Test
    public void shouldRespectTheStyleWidth() {
        final AccordionWidget a = new AccordionWidget("notes", "one");
        a.expand();
        a.style().width(40).applyStyle();
        assertEquals(40, Graphitty.viewLength(a.format().split("\n", -1)[0]),
                "an explicit width makes the box that wide — padding, not a content-sized box with a gap");
    }

    @Test
    public void shouldToggleState() {
        final AccordionWidget a = new AccordionWidget(mutableMap(), TID, null);
        assertTrue(a.isExpanded());
        a.toggle();
        assertFalse(a.isExpanded());
        a.toggle();
        assertTrue(a.isExpanded());
    }

    @Test
    public void shouldCollapseAndExpand() {
        final AccordionWidget a = new AccordionWidget(mutableMap(), TID, null);
        a.collapse();
        assertFalse(a.isExpanded());
        a.expand();
        assertTrue(a.isExpanded());
    }

    @Test
    public void shouldShowCollapseIndicatorAfterToggle() {
        final AccordionWidget a = new AccordionWidget(mutableMap(uri("title"), str("C")), TID, null);
        a.collapse();
        assertTrue(a.format().contains("[+]"));
        a.expand();
        assertTrue(a.format().contains("[-]"));
    }

    @Test
    public void shouldAppendLines() {
        final AccordionWidget a = new AccordionWidget(mutableMap(), TID, null);
        a.appendLine("first");
        a.appendLine("second");
        final String r = a.format();
        assertTrue(r.contains("first"));
        assertTrue(r.contains("second"));
    }

    @Test
    public void shouldApplyStyleRec() {
        final Map<Obj, Obj> s = mutableMap(uri("foreground"), str("{{y}}"));
        final Rec styleRec = new MRec(s, null, null);
        final Map<Obj, Obj> jvm = mutableMap(
                uri("title"), str("S"),
                uri("body"), str("c"),
                uri("style"), styleRec
        );
        final AccordionWidget a = new AccordionWidget(jvm, TID, null);
        assertEquals("{{y}}", a.getStyle().foreground());
    }

    @Test
    public void shouldBeIdempotentOnMultipleFormatCalls() {
        final Map<Obj, Obj> jvm = mutableMap(
                uri("title"), str("S"),
                uri("body"), str("t")
        );
        final AccordionWidget a = new AccordionWidget(jvm, TID, null);
        assertEquals(a.format(), a.format());
    }

    @Test
    public void shouldServeBareConstructorWithoutBodyAndToggle() {
        final AccordionWidget a = new AccordionWidget();
        assertTrue(a.isExpanded());
        a.toggle();
        assertFalse(a.isExpanded());
    }

    // ── the pointer works the indicator ────────────────────────────

    /**
     * The title bar is the toggle target: one border cell, a space, the title,
     * a space, the {@code [-]} / {@code [+]} glyph, all on the widget's first
     * rendered row.
     */
    private static int indicatorColumn(final String title) {
        return 3 + title.length();
    }

    @ParameterizedTest()
    @CsvSource(value = {
            "0  % 8  % handled     % the first [-]/[+] cell folds",
            "0  % 9  % handled     % the middle cell folds",
            "0  % 10 % handled     % the last cell folds",
            "0  % 7  % not-handled % the space before the glyph is the title bar, not the glyph",
            "0  % 11 % not-handled % the space after the glyph is the title bar, not the glyph",
            "0  % 2  % not-handled % the title text focuses the widget — it must not fold it",
            "0  % 0  % not-handled % the header's own corner focuses, it does not fold",
            "0  % 20 % not-handled % the header's trailing padding focuses, it does not fold",
            "1  % 8  % not-handled % the body is not the glyph",
            "2  % 8  % not-handled % the bottom border is not the glyph",
            "-1 % 8  % not-handled % a click above the box is not this widget's at all",
    }, delimiter = '%')
    void testIndicatorClickRegion(final int row, final int col, final String expectation, final String description) {
        final AccordionWidget a = new AccordionWidget("notes", "one\ntwo");
        a.expand();
        final boolean handled = a.onClick(row, col);
        if ("handled".equals(expectation.trim())) {
            assertTrue(handled, "the glyph should consume the click (row %d, col %d): %s".formatted(row, col, description));
            assertFalse(a.isExpanded(), "a click on the glyph folds the accordion");
        } else {
            assertFalse(handled, "a click off the glyph must fall through to the console: " + description);
            assertTrue(a.isExpanded(), "an unhandled click changes nothing (a header click focuses instead)");
        }
    }

    @Test
    public void shouldToggleBothWaysFromTheIndicator() {
        final AccordionWidget a = new AccordionWidget("notes", "one\ntwo");
        a.expand();
        assertTrue(a.onClick(0, indicatorColumn("notes") + 1), "the indicator is live");
        assertFalse(a.isExpanded(), "clicked [-] → collapsed");
        assertTrue(a.format().contains("[+]"), "the collapsed box shows [+]: " + a.format());
        assertTrue(a.onClick(0, indicatorColumn("notes") + 1), "the indicator is live while collapsed too");
        assertTrue(a.isExpanded(), "clicked [+] → expanded");
        assertTrue(a.format().contains("[-]"), "the expanded box shows [-]: " + a.format());
    }

    @Test
    public void shouldSayHowMuchAFoldedHeaderHides() {
        final AccordionWidget a = new AccordionWidget("notes", "one\ntwo\nthree");
        a.expand();
        assertTrue(a.format().contains("[-]"), "an expanded header shows [-]: " + a.format());

        a.collapse();
        final String folded = a.format();
        assertTrue(folded.contains("[+] 3"), "a folded header reports the lines it is holding: " + folded);
        assertFalse(folded.contains("one"), "and draws none of them: " + folded);

        final AccordionWidget empty = new AccordionWidget("notes", "");
        empty.collapse();
        assertTrue(empty.format().contains("[+]"), "an empty folded box still shows the toggle: " + empty.format());
        assertFalse(empty.format().contains("[+] 0"),
                "an empty body has nothing to report — no '0' to read as a count: " + empty.format());
    }
}
