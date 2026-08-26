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

package studio.phaseshift.metatron.isa.mach.type.ui.tool;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.mach.type.ui.Border;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.PanelWidget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ModalToolTest extends AbstractMetatronTest {

    @BeforeAll
    static void setUp() {
        AbstractMetatronTest.begin();
    }

    @Test
    public void testModalToolFormatTitleAndBody() {
        final ModalTool modal = new ModalTool("hello", "world");
        assertEquals(
                "┌hello┐{{X}}\n" +
                        "│world│{{X}}\n" +
                        "└─────┘{{X}}\n",
                modal.format());
    }

    @Test
    public void testModalToolWrapsPanel() {
        final ModalTool modal = new ModalTool(new PanelWidget("title", "body"));
        assertEquals(
                "┌title┐{{X}}\n" +
                        "│body │{{X}}\n" +
                        "└─────┘{{X}}\n",
                modal.format());
    }

    @Test
    public void testPanelWidgetFormatEndsWithColorReset() {
        // A colored panel must end with a {{X}} reset — otherwise the leaked
        // background paints the FloatingSurface's erase/buffer spaces on the
        // next render, showing up as a stray colored blank line above widgets.
        final PanelWidget panel = new PanelWidget("hello", "world");
        panel.style().background("{{[k]}}").foreground("{{b}}").applyStyle();
        assertTrue(panel.format().endsWith("{{X}}\n"),
                "format should end with a color reset: " + panel.format());
    }

    @Test
    public void testPanelWidgetAppliesStyleColors() {
        final PanelWidget panel = new PanelWidget("hello", "world");
        panel.style().background("{{[k]}}").foreground("{{b}}").applyStyle();
        final String formatted = panel.format();
        assertTrue(formatted.startsWith("{{[k]}}{{b}}┌"),
                "format should lead with bg+fg then the top border: " + formatted);
        assertTrue(formatted.contains("{{[k]}}{{b}}│world{{[k]}}{{b}}│{{X}}"),
                "each body line should re-apply bg+fg (and re-assert it for the right border): " + formatted);
        assertTrue(formatted.contains("{{[k]}}{{b}}└"),
                "bottom border should re-apply bg+fg: " + formatted);
    }

    @Test
    public void testPanelWidgetPropagatesBodyColorAcrossLines() {
        // A leading color code in the body is the body's text color, kept
        // distinct from the style/border color, and re-applied after the
        // per-line {{X}} reset so a multi-line body stays one color.
        final PanelWidget panel = new PanelWidget("title", "{{y}}hello\nworld");
        panel.style().border(Border.continuous).applyStyle();
        final String formatted = panel.format();
        assertTrue(formatted.contains("│{{y}}hello│{{X}}"),
                "line 0 keeps its own leading color: " + formatted);
        assertTrue(formatted.contains("│{{y}}world│{{X}}"),
                "line 1 should re-apply the body's leading color: " + formatted);
    }
}
