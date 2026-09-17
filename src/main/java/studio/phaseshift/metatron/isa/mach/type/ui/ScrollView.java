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

package studio.phaseshift.metatron.isa.mach.type.ui;

import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;

import java.util.ArrayList;
import java.util.List;

/**
 * The windowing math behind widget scrolling: given the full content a widget
 * <em>could</em> draw (every body line — the ones that are alive in the
 * widget's body string but off the viewport) and the viewport it has to draw
 * them in, produce exactly the lines that are visible.
 *
 * <p>It is deliberately a pure function library — no terminal, no widget, no
 * state — because scrolling is a property of <em>viewports</em>, not of any
 * particular widget.  {@code FloatingSurface} uses it for every pinned widget;
 * a widget that draws its own viewport (e.g. a widget using
 * {@code WidgetCanvas}) uses the same calls, and therefore scrolls with
 * identical semantics.
 *
 * <p>Three rules make the model behave the way a reader expects:
 *
 * <ul>
 *   <li><b>Chrome is pinned.</b>  A viewport may reserve the first
 *       {@code chrome} rows (a border/title bar — see
 *       {@link Stylable#chromeLines()}).  Those rows never scroll; only the
 *       body below them does.</li>
 *   <li><b>Tail by default.</b>  A viewport that is not scrolled shows the
 *       <em>newest</em> content ({@code follow}), which is what a live
 *       console wants: text appended while you watch stays visible.</li>
 *   <li><b>Nothing is lost.</b>  Scrolling back up shows the older lines, which
 *       were never discarded — the widget's body string still holds them.</li>
 * </ul>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class ScrollView {

    private ScrollView() {
        // do nothing
    }

    /**
     * The largest vertical offset a viewport of {@code viewport} rows can be
     * scrolled to, preserving {@code chrome} pinned rows.  0 = the content
     * already fits.
     *
     * @param content  total content rows (chrome included)
     * @param chrome   pinned leading rows
     * @param viewport visible rows (chrome included)
     */
    public static int maxY(final int content, final int chrome, final int viewport) {
        return maxY(content, chrome, 0, viewport);
    }

    /**
     * As {@link #maxY(int, int, int)} but also preserving {@code footer} pinned
     * trailing rows (the bottom border) — the body scrolls between them.
     */
    public static int maxY(final int content, final int chrome, final int footer, final int viewport) {
        final int pinned = clampChrome(chrome, content);
        final int trailing = clampChrome(footer, Math.max(0, content - pinned));
        final int body = Math.max(0, content - pinned - trailing);
        final int visible = Math.max(0, viewport - pinned - trailing);
        return Math.max(0, body - visible);
    }

    /**
     * The largest horizontal offset a viewport {@code viewportWidth} columns
     * wide can be scrolled to.  0 = the content already fits.
     *
     * @param contentWidth  widest content line, in visual columns
     * @param viewportWidth visible columns
     */
    public static int maxX(final int contentWidth, final int viewportWidth) {
        return Math.max(0, contentWidth - Math.max(0, viewportWidth));
    }

    /**
     * Window {@code lines} to a viewport of {@code viewport} rows, keeping the
     * first {@code chrome} rows pinned and showing {@code offset} body rows
     * down from the top of the body.
     *
     * <p>The offset is clamped into range (0 … {@link #maxY}), an oversize (or
     * zero) viewport returns the content untouched, and a viewport larger than
     * the content returns the content untouched.  In particular an offset of 0
     * is the <em>first</em> body row — "show the newest" is not implicit, it is
     * {@code offset = maxY}, which is how a live widget asks to follow its tail.
     *
     * @param lines    every line the widget has (chrome included)
     * @param chrome   pinned leading rows (title/border); never scrolled
     * @param offset   0-based body row shown at the top of the body viewport
     * @param viewport visible rows (chrome included); &le; 0 = no cap
     * @return the visible lines
     */
    public static List<String> windowVertically(final List<String> lines, final int chrome,
                                                final int offset, final int viewport) {
        return windowVertically(lines, chrome, 0, offset, viewport);
    }

    /**
     * As {@link #windowVertically(List, int, int, int)} but also pinning the
     * last {@code footer} rows (the bottom border) at the viewport's bottom, so
     * scrolling never overwrites them.
     */
    public static List<String> windowVertically(final List<String> lines, final int chrome, final int footer,
                                                final int offset, final int viewport) {
        if (null == lines || lines.isEmpty()) return List.of();
        if (viewport <= 0 || viewport >= lines.size()) return List.copyOf(lines);
        final int pinned = clampChrome(chrome, lines.size());
        final int trailing = clampChrome(footer, Math.max(0, lines.size() - pinned));
        final int bodySlots = viewport - pinned - trailing;
        if (bodySlots >= lines.size() - pinned - trailing) return List.copyOf(lines);
        final int from = pinned + Math.max(0, Math.min(offset,
                maxY(lines.size(), pinned, trailing, viewport)));
        final List<String> window = new ArrayList<>(viewport);
        final int bodyEnd = lines.size() - trailing;
        for (int i = 0; i < pinned; i++)
            window.add(lines.get(i));
        for (int i = from; i < bodyEnd && window.size() < viewport - trailing; i++)
            window.add(lines.get(i));
        for (int i = bodyEnd; i < lines.size(); i++)
            window.add(lines.get(i));
        return window;
    }

    /**
     * Window one line to {@code width} columns starting at column
     * {@code offset}.  A line that already fits is returned untouched — color
     * codes and all — so the common case is byte-identical to no scrolling at
     * all; only a shifted or overlong line is stripped to its text (the same
     * stripping the surface does when it clips a wide line).
     */
    public static String windowHorizontally(final String line, final int offset, final int width) {
        if (null == line) return "";
        final int w = Math.max(0, width);
        if (offset <= 0 && visualLength(line) <= w) return line;
        final String text = Graphitty.strip(line);
        if (offset >= text.length()) return "";
        final int from = Math.min(Math.max(0, offset), text.length());
        return text.substring(from, Math.min(text.length(), from + w));
    }

    /** Widest line, in visual columns (Graphitty codes ignored). */
    public static int contentWidth(final List<String> lines) {
        if (null == lines) return 0;
        int width = 0;
        for (final String line : lines)
            width = Math.max(width, visualLength(line));
        return width;
    }

    /** Visual width of a single (possibly Graphitty-colored) line. */
    public static int visualLength(final String line) {
        return null == line ? 0 : Graphitty.viewLength(line);
    }

    private static int clampChrome(final int chrome, final int content) {
        return Math.max(0, Math.min(chrome, content));
    }
}
