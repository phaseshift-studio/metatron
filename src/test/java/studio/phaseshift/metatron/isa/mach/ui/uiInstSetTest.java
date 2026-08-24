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

package studio.phaseshift.metatron.isa.mach.ui;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.AbstractInstSetTest;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.ui.Stylable;
import studio.phaseshift.metatron.isa.mach.type.ui.Widget;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.AccordionWidget;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.FloatingSurface;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class uiInstSetTest extends AbstractInstSetTest {

    public uiInstSetTest() {
        super(uiInstSet::new);
    }

    /** Wrap a map in a mutable-backed MRec so Style.from() can copy it. */
    private static MRec wrap(final Map<Obj, Obj> jvm) {
        return new MRec(new LinkedHashMap<>(jvm), null, null);
    }

    // ── Anchor type ────────────────────────────────────────────────

    @Test
    public void shouldParseTopLeftAnchor() {
        assertEquals(FloatingSurface.Anchor.TOP_LEFT, FloatingSurface.Anchor.parse("top_left"));
    }

    @Test
    public void shouldParseBottomRightAnchor() {
        assertEquals(FloatingSurface.Anchor.BOTTOM_RIGHT, FloatingSurface.Anchor.parse("bottom_right"));
    }

    @Test
    public void shouldResolveAllFourAnchors() {
        assertNotNull(FloatingSurface.Anchor.TOP_LEFT);
        assertNotNull(FloatingSurface.Anchor.TOP_RIGHT);
        assertNotNull(FloatingSurface.Anchor.BOTTOM_LEFT);
        assertNotNull(FloatingSurface.Anchor.BOTTOM_RIGHT);
    }

    // ── Style type ─────────────────────────────────────────────────

    @Test
    public void shouldCreateStyleWithAnchor() {
        final Stylable.Style<?> style = Stylable.Style.from(wrap(Map.of(
                uri("anchor"), uri("bottom_right"),
                uri("width"), jnt(40)
        )));
        assertEquals(FloatingSurface.Anchor.BOTTOM_RIGHT, style.anchor());
        assertEquals(40, style.width());
        assertTrue(style.hasFloat());
    }

    @Test
    public void shouldCreateStyleWithTopOffset() {
        final Stylable.Style<?> style = Stylable.Style.from(wrap(Map.of(
                uri("anchor"), uri("bottom_left"),
                uri("width"), jnt(30),
                uri("top"), jnt(-3),
                uri("left"), jnt(2)
        )));
        assertEquals(FloatingSurface.Anchor.BOTTOM_LEFT, style.anchor());
        assertEquals(30, style.width());
        assertEquals(-3, style.top());
        assertEquals(2, style.left());
    }

    @Test
    public void shouldCreateStyleWithAllFloatProperties() {
        final Stylable.Style<?> style = Stylable.Style.from(wrap(Map.of(
                uri("anchor"), uri("top_right"),
                uri("width"), jnt(50),
                uri("top"), jnt(5),
                uri("left"), jnt(-2),
                uri("foreground"), str("{{y}}"),
                uri("background"), str("{{b}}")
        )));
        assertEquals(FloatingSurface.Anchor.TOP_RIGHT, style.anchor());
        assertEquals(50, style.width());
        assertEquals(5, style.top());
        assertEquals(-2, style.left());
        assertEquals("{{y}}", style.foreground());
        assertEquals("{{b}}", style.background());
    }

    @Test
    public void shouldDefaultStyleAnchorToNull() {
        // An empty style has no anchor key → anchor() returns null, hasFloat() is false
        final Stylable.Style<?> style = Stylable.Style.from(wrap(Map.of()));
        assertNull(style.anchor(), "empty style should have no anchor");
        assertFalse(style.hasFloat(), "empty style should not have float");
        assertEquals(0, style.width());
        assertEquals(0, style.top());
        assertEquals(0, style.left());
    }

    @Test
    public void shouldDetectFloatStyle() {
        final Stylable.Style<?> noFloat = Stylable.Style.from(wrap(Map.of(
                uri("foreground"), str("{{r}}")
        )));
        assertFalse(noFloat.hasFloat(), "style without anchor should not have float");

        final Stylable.Style<?> hasFloat = Stylable.Style.from(wrap(Map.of(
                uri("anchor"), uri("bottom_left"),
                uri("width"), jnt(40)
        )));
        assertTrue(hasFloat.hasFloat(), "style with anchor and width should have float");
    }

    // ── Accordion type ─────────────────────────────────────────────

    @Test
    public void shouldConstructAbstractWidgetsHeadless() {
        // AbstractWidget subclasses (swipe_panel, menu_bar) must construct to a
        // Widget even without a terminal (headless eval / MCP), so the display/as
        // insts' (Widget<?>) cast can never hit a bare MRec.
        for (final String code : new String[]{
                "swipe_panel::[obj=>[1,2,3,4]]",
                "menu_bar::[height=>1,lines=>[]]"
        }) {
            final Obj cd = ObjmtronSerializer.parse(code);
            assertTrue(cd instanceof Widget, code + " should construct to a Widget without a terminal");
        }
        // format() and the widget-as-str inst must also be terminal-free
        // for embedding (widget-as-str).
        final Obj swipe = ObjmtronSerializer.parse("swipe_panel::[obj=>[1,2,3,4]]");
        assertNotNull(((Widget) swipe).format());
        final Obj asStr = ObjmtronSerializer.parse("swipe_panel::[obj=>[1,2,3,4]].as(str::T)").apply(noobj());
        assertTrue(asStr.isStr(), "swipe_panel.as(str::T) should produce a str headless: " + asStr);
    }

    @Test
    public void shouldCreateAccordionViaIsa() {
        final AccordionWidget a = new AccordionWidget(
                new LinkedHashMap<>(Map.of(
                        uri("title"), str("ISA Test"),
                        uri("body"), str("Created via ISA type")
                )),
                uiInstSet.UI_ACCORDION_TID, null);
        assertEquals("ISA Test", a.title());
        assertTrue(a.isExpanded());
        assertTrue(a.format().contains("ISA Test"));
        assertTrue(a.format().contains("Created via ISA type"));
    }

    @Test
    public void shouldToggleAccordionState() {
        final AccordionWidget a = new AccordionWidget("Toggle Test", "body");
        assertTrue(a.isExpanded(), "new accordion should start expanded");
        assertTrue(a.format().contains("[-]"), "expanded accordion should show collapse indicator");

        a.toggle();
        assertFalse(a.isExpanded(), "after toggle should be collapsed");
        assertTrue(a.format().contains("[+]"), "collapsed accordion should show expand indicator");

        a.toggle();
        assertTrue(a.isExpanded(), "after second toggle should be expanded again");
    }

    @Test
    public void shouldCollapseAccordionHidesBody() {
        final AccordionWidget a = new AccordionWidget("C", "secret content");
        assertTrue(a.format().contains("secret content"), "expanded should show body");

        a.collapse();
        assertFalse(a.format().contains("secret content"), "collapsed should NOT show body");
    }

    @Test
    public void shouldTrackHeightChanges() {
        final AccordionWidget a = new AccordionWidget("H", "line1\nline2\nline3");
        final int expandedHeight = a.height();
        assertTrue(expandedHeight > 2, "expanded height should be > 2 (title + 3 body lines + border)");

        a.collapse();
        assertEquals(2, a.height(), "collapsed height should be exactly 2 (title + border)");
    }

    @Test
    public void shouldExpandAndCollapseViaApi() {
        final AccordionWidget a = new AccordionWidget("API");
        assertTrue(a.isExpanded());
        a.collapse();
        assertFalse(a.isExpanded());
        a.expand();
        assertTrue(a.isExpanded());
    }

    @Test
    public void shouldCreateBareAccordion() {
        final AccordionWidget a = new AccordionWidget();
        assertTrue(a.isExpanded());
        assertEquals("", a.title());
        assertTrue(a.format().contains("[-]"), "bare accordion should render");
    }

    // ── Style to Anchor round-trip ─────────────────────────────────

    @Test
    public void shouldRoundTripAllAnchorsThroughStyle() {
        for (final FloatingSurface.Anchor anchor : FloatingSurface.Anchor.values()) {
            final String name = anchor.name().toLowerCase();
            final Stylable.Style<?> style = Stylable.Style.from(wrap(Map.of(
                    uri("anchor"), uri(name),
                    uri("width"), jnt(40)
            )));
            assertEquals(anchor, style.anchor(),
                    "anchor " + name + " should round-trip through Style");
            assertTrue(style.hasFloat());
        }
    }
}
