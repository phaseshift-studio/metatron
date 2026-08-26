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
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.tool.ModalTool;
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

    @Test
    public void shouldResolveMiddleAnchorViaMtron() {
        final Obj styleObj = ObjmtronSerializer.parse("style::[anchor=>middle,width=>40]");
        assertTrue(styleObj instanceof Stylable.Style, "style::[anchor=>middle,...] should construct: " + styleObj);
        final Stylable.Style<?> style = (Stylable.Style<?>) styleObj;
        assertEquals(FloatingSurface.Anchor.MIDDLE, style.anchor(),
                "anchor=>middle should resolve to MIDDLE");
        assertTrue(style.hasFloat(), "style with anchor=>middle should have float");
    }

    // ── Accordion type ─────────────────────────────────────────────

    @Test
    public void shouldConstructAbstractWidgetsHeadless() {
        // AbstractWidget subclasses (swipe_panel, menu_bar) must construct to a
        // Widget even without a terminal (headless eval / MCP), so the display/as
        // insts' (Widget<?>) cast can never hit a bare MRec.
        for (final String code : new String[]{
                "swipe_panel::[obj=>[1,2,3,4]]",
                "menu_bar::[height=>1,lines=>[]]",
                "modal::[title=>'hello',body=>'world']"
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

    // ── Modal type ─────────────────────────────────────────────────

    @Test
    public void shouldCreateModalViaIsa() {
        final ModalTool modal = (ModalTool) ObjmtronSerializer
                .parse("modal::[title=>'ISA Test',body=>'Created via ISA type']");
        final String formatted = modal.format();
        assertTrue(formatted.contains("ISA Test"), "modal format should carry the title: " + formatted);
        assertTrue(formatted.contains("Created via ISA type"),
                "modal format should carry the body: " + formatted);
        assertTrue(formatted.contains("┌"), "modal should render with a visible border: " + formatted);
    }

    @Test
    public void shouldSetModalZIndexFromMtron() {
        final ModalTool modal = (ModalTool) ObjmtronSerializer.parse(
                "modal::[title=>'x',body=>'y',style=>style::[anchor=>middle,zIndex=>100]]");
        assertEquals(100, modal.getStyle().zIndex(),
                "the modal's own style should carry zIndex so the FloatingSurface sorts it on top");
        assertEquals(FloatingSurface.Anchor.MIDDLE, modal.getStyle().anchor(),
                "the modal's own style should mirror the anchor too");
    }

    @Test
    public void shouldRenderStyledModalColors() {
        final ModalTool modal = (ModalTool) ObjmtronSerializer.parse(
                "modal::[title => 'agent response',\n" +
                        "        body  => 'x',\n" +
                        "        style => style::[border    =>continuous,\n" +
                        "                         background=>\"{{[k]}}\",\n" +
                        "                         foreground=>\"{{b}}\",\n" +
                        "                         zIndex    => 100,\n" +
                        "                         anchor    =>middle]]");
        // 1. the raw style rec survives in the modal's jvm
        final Obj styleRec = modal.at(uri("style"));
        assertTrue(styleRec != null && styleRec.isRec(), "style rec should survive: " + styleRec);
        // 2. the parsed style carries the user's fields
        final Stylable.Style<?> s = Stylable.Style.from(styleRec.asRec());
        assertEquals("{{[k]}}", s.background());
        assertEquals("{{b}}", s.foreground());
        assertEquals(100, s.zIndex());
        assertEquals(FloatingSurface.Anchor.MIDDLE, s.anchor());
        // 3. format() carries the color codes and they render to ANSI color escapes
        final String format = modal.format();
        assertTrue(format.contains("{{[k]}}{{b}}"), "format should carry bg+fg codes: " + format);
        final String ansi = Graphitty.string(format);
        assertTrue(ansi.contains("[40"), "background {{[k]}} should render ANSI black bg: " + ansi.replace("", "<ESC>"));
        assertTrue(ansi.contains("[34"), "foreground {{b}} should render ANSI blue fg: " + ansi.replace("", "<ESC>"));
    }

    @Test
    public void shouldReadZIndexFromMtronStyle() {
        final Obj styleObj = ObjmtronSerializer.parse("style::[zIndex=>100]");
        assertTrue(styleObj instanceof Stylable.Style, "style::[zIndex=>...] should construct: " + styleObj);
        assertEquals(100, ((Stylable.Style<?>) styleObj).zIndex());
    }

    @Test
    public void shouldApplyModalStyleFromMtron() {
        for (final String code : new String[]{
                "modal::[title=>'x',body=>'y',style=>style::[border=>continuous,anchor=>middle]]",
                "modal::[title=>'x',body=>'y',style=>[border=>continuous,anchor=>middle]]"
        }) {
            final Obj obj = ObjmtronSerializer.parse(code);
            assertTrue(obj instanceof ModalTool,
                    "style form should construct to a ModalTool: " + code + " -> " + obj);
            final Obj style = ((ModalTool) obj).at(uri("style"));
            assertTrue(style != null && style.isRec(),
                    "style field should survive construction: " + code + " -> " + style);
            final Stylable.Style<?> parsed = Stylable.Style.from(style.asRec());
            assertEquals(FloatingSurface.Anchor.MIDDLE, parsed.anchor(),
                    "anchor=>middle should resolve to MIDDLE: " + code);
        }
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
