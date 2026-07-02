package studio.phaseshift.metatron.isa.mach.type.ui.widget;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.impl.MLst;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.m.type.impl.MStr;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

public class AccordionWidgetTest extends AbstractMetatronTest {

    private static final fURI TID = f("/m/mach/ui/widget/accordion");

    @Test
    public void shouldRenderTitleFromJvm() {
        final Map<Obj, Obj> jvm = Map.of(
                uri("title"), MStr.str("Test Title"),
                uri("body"),  MStr.str("Body line")
        );
        final AccordionWidget a = new AccordionWidget(jvm, TID, null);
        assertTrue(a.format().contains("Test Title"));
    }

    @Test
    public void shouldRenderBodyFromJvm() {
        final Map<Obj, Obj> jvm = Map.of(
                uri("title"), MStr.str("T"),
                uri("body"),  MStr.str("Body line")
        );
        final AccordionWidget a = new AccordionWidget(jvm, TID, null);
        assertTrue(a.format().contains("Body line"));
    }

    @Test
    public void shouldRenderBodyAsObjs() {
        final Map<Obj, Obj> jvm = Map.of(
                uri("title"), MStr.str("T"),
                uri("body"),  MLst.lst(MStr.str("line1"), MStr.str("line2"))
        );
        final AccordionWidget a = new AccordionWidget(jvm, TID, null);
        final String r = a.format();
        assertTrue(r.contains("line1"), "body should contain line1 in: " + r);
        assertTrue(r.contains("line2"), "body should contain line2 in: " + r);
    }

    @Test
    public void shouldRenderEmptyBodyWhenNotProvided() {
        final AccordionWidget a = new AccordionWidget(Map.of(uri("title"), MStr.str("X")), TID, null);
        assertTrue(a.format().contains("X"));
    }

    @Test
    public void shouldRenderBasicFormat() {
        final AccordionWidget a = new AccordionWidget(Map.of(uri("title"), MStr.str("Hi")), TID, null);
        final String r = a.format();
        assertTrue(r.contains("[-]"));
        assertTrue(r.contains("Hi"));
    }

    @Test
    public void shouldToggleState() {
        final AccordionWidget a = new AccordionWidget(Map.of(), TID, null);
        assertTrue(a.isExpanded());
        a.toggle();
        assertFalse(a.isExpanded());
        a.toggle();
        assertTrue(a.isExpanded());
    }

    @Test
    public void shouldCollapseAndExpand() {
        final AccordionWidget a = new AccordionWidget(Map.of(), TID, null);
        a.collapse();
        assertFalse(a.isExpanded());
        a.expand();
        assertTrue(a.isExpanded());
    }

    @Test
    public void shouldShowCollapseIndicatorAfterToggle() {
        final Map<Obj, Obj> jvm = Map.of(uri("title"), MStr.str("C"));
        final AccordionWidget a = new AccordionWidget(jvm, TID, null);
        a.collapse();
        assertTrue(a.format().contains("[+]"));
        a.expand();
        assertTrue(a.format().contains("[-]"));
    }

    @Test
    public void shouldAppendLines() {
        final AccordionWidget a = new AccordionWidget(Map.of(), TID, null);
        a.appendLine("first");
        a.appendLine("second");
        final String r = a.format();
        assertTrue(r.contains("first"));
        assertTrue(r.contains("second"));
    }

    @Test
    public void shouldApplyStyleRec() {
        final Map<Obj, Obj> s = Map.of(uri("foreground"), MStr.str("{{y}}"));
        final Rec styleRec = new MRec(s, null, null);
        final Map<Obj, Obj> jvm = Map.of(
                uri("title"), MStr.str("S"),
                uri("body"),  MStr.str("c"),
                uri("style"), styleRec
        );
        final AccordionWidget a = new AccordionWidget(jvm, TID, null);
        assertEquals("{{y}}", a.getStyle().foreground);
    }

    @Test
    public void shouldBeIdempotentOnMultipleFormatCalls() {
        final Map<Obj, Obj> jvm = Map.of(
                uri("title"), MStr.str("S"),
                uri("body"),  MStr.str("t")
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
