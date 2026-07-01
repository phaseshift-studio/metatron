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

package studio.phaseshift.metatron.isa.mach.type.ui.widget;

import studio.phaseshift.metatron.isa.mach.type.ui.Border;
import studio.phaseshift.metatron.isa.mach.type.ui.Widget;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Highlighter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class Accordion extends AbstractWidget<Accordion> {

    private String title;
    private final List<String> body = new ArrayList<>();
    private boolean expanded = true;

    private static final String EXPAND_INDICATOR  = "[-]";
    private static final String COLLAPSE_INDICATOR = "[+]";

    public Accordion() {
        this.title = "";
    }

    public Accordion(final String title) {
        this.title = title;
    }

    public Accordion(final String title, final String body) {
        this(title);
        this.body.addAll(Arrays.asList(body.split("\n")));
    }

    public Accordion(final String title, final List<String> body) {
        this(title);
        this.body.addAll(body);
    }

    // -- state --

    public boolean isExpanded() {
        return this.expanded;
    }

    public Accordion expand() {
        this.expanded = true;
        return this;
    }

    public Accordion collapse() {
        this.expanded = false;
        return this;
    }

    public Accordion toggle() {
        this.expanded = !this.expanded;
        return this;
    }

    // -- content --

    public Accordion title(final String title) {
        this.title = title;
        return this;
    }

    public String title() {
        return this.title;
    }

    public Accordion body(final String text) {
        this.body.clear();
        if (text != null && !text.isEmpty()) {
            this.body.addAll(Arrays.asList(text.split("\n")));
        }
        return this;
    }

    /** Append text, splitting on newlines into individual body lines. */
    public Accordion appendBody(final String text) {
        if (text != null && !text.isEmpty()) {
            this.body.addAll(Arrays.asList(text.split("\n", -1)));
        }
        return this;
    }

    /** Append a single line as-is — no newline splitting.  Use for real-time streaming. */
    public Accordion appendLine(final String line) {
        if (line != null && !line.isEmpty()) {
            this.body.add(line);
        }
        return this;
    }

    public Accordion clearBody() {
        this.body.clear();
        return this;
    }

    public List<String> bodyLines() {
        return List.copyOf(this.body);
    }

    // -- sizing --

    @Override
    public int height() {
        if (this.expanded && !this.body.isEmpty()) {
            // top border + body lines + bottom border = body.size() + 2
            return this.body.size() + 2;
        }
        // collapsed: top border + bottom border = 2
        return 2;
    }

    @Override
    public int width() {
        int contentWidth = Highlighter.visualLength(this.title)
                + Highlighter.visualLength(this.indicator())
                + 3; // spaces around title
        if (this.expanded) {
            final int bodyWidth = this.body.stream()
                    .map(Highlighter::visualLength)
                    .max(Integer::compareTo)
                    .orElse(0);
            contentWidth = Math.max(contentWidth, bodyWidth + 2); // +2 for left/right padding
        }
        return contentWidth;
    }

    // -- rendering --

    private String indicator() {
        return this.expanded ? EXPAND_INDICATOR : COLLAPSE_INDICATOR;
    }

    @Override
    public String format() {
        final int bodyWidth = this.body.stream()
                .map(Highlighter::visualLength)
                .max(Integer::compareTo)
                .orElse(0);
        final String indicator = this.indicator();
        final int titleWidth = Highlighter.visualLength(this.title)
                + Highlighter.visualLength(indicator)
                + 3; // " Title [-] " → space + title + space + indicator + space

        final int width = Math.max(titleWidth, bodyWidth + 2);

        final Border border = this.style.border == Border.none
                ? Border.simple
                : this.style.border;

        final StringBuilder sb = new StringBuilder();

        if (this.expanded && !this.body.isEmpty()) {
            // Expanded with body
            // ┌─ Title [-] ───────────────────────────┐
            // │ Body line 1                           │
            // │ Body line 2                           │
            // └───────────────────────────────────────┘
            buildTitleBar(sb, border, indicator, width);
            sb.append("\n");
            for (final String line : this.body) {
                sb.append(Widget.X).append(border.leftSide()).append(Widget.X)
                        .append(this.style.foreground)
                        .append(" ").append(line)
                        .append(" ".repeat(Math.max(0, width - Highlighter.visualLength(line) - 1)))
                        .append(Widget.X)
                        .append(border.rightSide()).append(Widget.X).append("\n");
            }
            sb.append(Widget.X).append(border.bottomLeftCorner())
                    .append(border.bottomSide().repeat(width))
                    .append(border.bottomRightCorner()).append(Widget.X);
        } else {
            // Collapsed or empty body: just the title bar
            // ┌─ Title [+/-] ─────────────────────────┐
            // └───────────────────────────────────────┘
            buildTitleBar(sb, border, indicator, width);
            sb.append("\n");
            sb.append(Widget.X).append(border.bottomLeftCorner())
                    .append(border.bottomSide().repeat(width))
                    .append(border.bottomRightCorner()).append(Widget.X);
        }

        return sb.toString();
    }

    private void buildTitleBar(final StringBuilder sb, final Border border,
                               final String indicator, final int width) {
        final String titleText = " " + this.title + " " + indicator + " ";

        sb.append(Widget.X).append(border.topLeftCorner());
        sb.append(titleText);
        final int remaining = width - Highlighter.visualLength(titleText);
        if (remaining > 0) {
            sb.append(border.topSide().repeat(remaining));
        }
        sb.append(border.topRightCorner()).append(Widget.X);
    }

    @Override
    public String toString() {
        return this.format();
    }

    @Override
    public Accordion style(final Style<Accordion> style) {
        super.style(style);
        if (this.style.border == Border.none)
            this.style.border = Border.simple;
        if (this.style.foreground.isEmpty())
            this.style.foreground = "{{g}}";
        return this;
    }
}
