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

package studio.phaseshift.metatron.isa.m.sys.ui.widget;

import studio.phaseshift.metatron.isa.m.sys.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.m.type.Obj;

import java.util.List;
import java.util.Optional;

/**
 * MINI-WIDGETS — the line-sized patterns a full {@link studio.phaseshift.metatron.isa.m.sys.ui.Widget} draws inside
 * its own box, without any of the machinery a widget owns.
 * <p>
 * A widget is heavy because it owns its rec (title, body, expanded, style), its border and title bar, its word-wrap,
 * and its floating-surface lifecycle. A row of content needs none of that: it is a rendered line and, when something
 * on it is clickable, the column ranges a pointer may hit. This class is those two things and nothing else —
 * stateless, rec-free, style-free — so a host widget can compose accordion headers, segmented rows, meters, and
 * whatever comes next while keeping its state, geometry, and repaint loop where they already live.
 * <p>
 * THE HOST KEEPS ITS STATE. A fold's open/closed flag, a selected slot, a meter's value — those are the host's rec
 * data (see {@code NetworkPanelTool.foldedPeers()}), never a field here: a Java field would be lost by the
 * re-hydration every render performs, and the rec is what a user can script.
 * <p>
 * COLUMNS ARE LINE-RELATIVE. A span's {@code [start, end)} is measured from the line's first VISIBLE column, so the
 * host adds its own box inset when it maps a pointer click (a panel's body starts two columns in). A span also
 * describes the line AS DRAWN: a host that clips a line should keep its spans inside what survived the clip (the
 * marker and buttons such a line leads with are always in the head).
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class MiniWidget {

    /**
     * the accordion's two markers, each two display columns: ▼ open, ▶ folded to its header
     */
    private static final String OPEN_MARKER = "{{c}}▼{{w}} ";
    private static final String FOLDED_MARKER = "{{c}}▶{{w}} ";
    /**
     * the display columns a marker occupies, so a host can place the span that follows it
     */
    public static final int MARKER_COLUMNS = 2;

    private MiniWidget() {
        // static only
    }

    /**
     * The accordion marker: {@code ▼} while open, {@code ▶} while folded to its header.
     *
     * @param open whether the section is open
     * @return the marker markup, {@link #MARKER_COLUMNS} columns wide
     */
    public static String marker(final boolean open) {
        return open ? OPEN_MARKER : FOLDED_MARKER;
    }

    /**
     * The span of a line's spans covering a line-relative column, first-written wins. A host whose rendered line
     * is built in pieces (the status bar) holds only the spans the parts produced, so this is the same hit test
     * without a {@link LineUI} to carry them.
     *
     * @param spans the line's hot spots, in draw order
     * @param col   the line-relative column (0 is the line's first visible column)
     * @return the span a pointer at {@code col} is over, or empty
     */
    public static Optional<SpanUI> at(final List<SpanUI> spans, final int col) {
        for (final SpanUI span : spans)
            if (col >= span.start() && col < span.end())
                return Optional.of(span);
        return Optional.empty();
    }

    /**
     * A RENDERED LINE and the hot spots its pointer should honour — none when the line is inert. The markup is
     * Graphitty markup, ready for the host's own clip/render pass.
     */
    public record LineUI(String markup, List<SpanUI> spans) {

        public LineUI {
            spans = List.copyOf(spans);
        }

        /**
         * An inert line: drawn, but nothing on it answers a click.
         *
         * @param markup the line's markup
         */
        public LineUI(final String markup) {
            this(markup, List.of());
        }

        /**
         * The span covering a line-relative column, if any. Spans are tested in the order the host wrote them, so
         * overlapping spans resolve first-written-wins.
         *
         * @param col the line-relative column (0 is the line's first visible column)
         * @return the span a pointer at {@code col} is over, or empty
         */
        public Optional<SpanUI> at(final int col) {
            return MiniWidget.at(this.spans, col);
        }
    }

    /**
     * A CLICKABLE COLUMN RANGE on one line — half-open {@code [start, end)}, line-relative.
     * <p>
     * {@code key} and {@code about} are the HOST's: this class never interprets them, so a pointer hit hands the
     * host back the meaning it put in. A panel keys its fold {@code "fold"} and its clear {@code "clear"}, each
     * carrying the peer the click is about; a segmented bar keys each segment with what it selects.
     *
     * @param start the first column the span covers (inclusive)
     * @param end   the first column past the span (exclusive)
     * @param key   the host's word for what a click means
     * @param about the host's subject for the click
     */
    public record SpanUI(int start, int end, String key, Obj about) {
    }

    /**
     * Clip a markup line to {@code columns} visible columns, keeping the styling of what survives.
     * <p>
     * A line that fits is returned untouched. A line that must be cut is handed back as PLAIN text (so a cut line
     * can never end inside a markup tag), sliced at a column boundary so a wide glyph is never split in half.
     *
     * @param markup  the line's markup
     * @param columns the display columns available; {@code <= 0} means no limit
     * @return the line as it will be drawn, ending in "…" when it was cut
     */
    public static String clip(final String markup, final int columns) {
        if (columns <= 0)
            return markup;
        // measure VISUAL COLUMNS — the true terminal width, markup and ANSI stripped, a wide glyph counted for
        // its real width — never markup characters, which is exactly the measure the accordion's word-wrap uses
        if (Graphitty.viewLength(markup) <= columns)
            return markup;
        return Graphitty.viewPrefix(Graphitty.strip(Graphitty.string(markup)), Math.max(1, columns - 1)) + "…";
    }
}
