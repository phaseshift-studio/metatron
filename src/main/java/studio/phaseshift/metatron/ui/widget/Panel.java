/*
 * Metatron: A Distributed Computing Language and Virtual Machine
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

package studio.phaseshift.metatron.ui.widget;

import studio.phaseshift.metatron.lang.sys.console.Highlighter;
import studio.phaseshift.metatron.ui.Border;
import studio.phaseshift.metatron.ui.Stylable;
import studio.phaseshift.metatron.ui.Widget;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class Panel extends AbstractWidget<Panel> {

    protected final String title;
    protected final String body;
    
    public Panel(final String body) {
        this(null, body);
    }
    
    public Panel(final String title, final String body) {
        this.title = title;
        this.body = body;
    }
    
    public Panel bottom(final Widget dims) {
        return new Panel(null, this + "\n" + dims.toString());
    }

    public Panel right(final Widget dims) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.max(this.height(), dims.height()); i++) {
            if (i < this.height()) {
                sb.append(this.rowString(i));
            } else {
                sb.append(" ".repeat(this.width()));
            }
            if (i < dims.height()) {
                sb.append(" ").append(dims.rowString(i));
            }
            sb.append("\n");
        }
        sb.deleteCharAt(sb.length() - 1);
        return new Panel(sb.toString()).style().border(this.style.border).apply();
    }

    public String format() {
        final List<String> lines = Arrays.asList(this.body.replace("\\n", "\n").split("\\r?\\n", -1));
        final int maxLen = Stream.concat(Stream.of(this.title).filter(Objects::nonNull), lines.stream())
                .map(Highlighter::visualLength)
                .max(Integer::compareTo)
                .orElse(0);

        final StringBuilder sb = new StringBuilder();
        final String top = "%s%s".formatted(null == this.title ? "" : this.title, this.style.border.topSide().repeat(null == this.title ? maxLen : maxLen - Highlighter.visualLength(this.title))).stripTrailing();
        if (!top.isEmpty())
            sb.append(this.style.border.topLeftCorner()).append(top).append(this.style.border.topRightCorner()).append('\n');
        for (final String line : lines) {
            sb.append(this.style.border.leftSide())
                    .append(line)
                    .append(" ".repeat(maxLen - Highlighter.visualLength(line)))
                    .append(this.style.border.rightSide())
                    .append("{{X}}\n");
        }
        final String bottom = this.style.border.bottomSide().repeat(maxLen).stripTrailing();
        if (!bottom.isEmpty())
            sb.append(this.style.border.bottomLeftCorner()).append(bottom).append(this.style.border.bottomRightCorner()).append("\n");
        return sb.toString();
    }
}
