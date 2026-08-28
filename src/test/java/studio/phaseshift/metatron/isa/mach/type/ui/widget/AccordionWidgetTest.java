package studio.phaseshift.metatron.isa.mach.type.ui.widget;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Console;

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
}
