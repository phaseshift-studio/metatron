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

package studio.phaseshift.metatron.ui;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class Box implements Dimensions {

    protected final String title;
    protected final List<String> options;
    protected final String body;
    protected final Border border;

    public Box(final String body, final Border border) {
        this(null, body, border);
    }

    public Box(final String title, final String body, final Border border) {
        this(title, null, body, border);
    }

    public Box(final String title, final List<String> options, final String body, final Border border) {
        this.title = title;
        this.body = body;
        this.border = border;
        this.options = options;
    }

    public Box bottom(final Dimensions dims, final Border border) {
        return this.bottom(null, dims, border);
    }

    public Box bottom(final List<String> options, final Dimensions dims, final Border border) {
        return new Box(null, options, this + "\n" + dims.toString(), border);
    }

    public Box right(final Dimensions dims) {
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
        return new Box(sb.toString(), this.border);
    }

    public String toString() {
        final List<String> lines = Arrays.asList(this.body.replace("\\n", "\n").split("\\r?\\n", -1));
        final int maxLen = Stream.concat(Stream.of(this.title).filter(Objects::nonNull), lines.stream())
                .map(Graphitty::strip)
                .mapToInt(String::length)
                .max()
                .orElse(0);

        final StringBuilder sb = new StringBuilder();
        final String top = "%s%s".formatted(null == this.title ? "" : this.title, this.border.topSide().toString().repeat(null == this.title ? maxLen : maxLen - Graphitty.strip(this.title).length())).trim();
        if (!top.isEmpty())
            sb.append(this.border.topLeftCorner()).append(top).append(this.border.topRightCorner()).append('\n');
        if (null != options) {
            sb.append(this.border.leftSide()).append(this.options).append(" ".repeat(maxLen - Graphitty.strip(this.options.toString()).length())).append(this.border.rightSide()).append('\n');
        }
        for (final String line : lines) {
            sb.append(this.border.leftSide())
                    .append(line)
                    .append(" ".repeat(maxLen - Graphitty.strip(line).length()))
                    .append(this.border.rightSide())
                    .append('\n');
        }
        final String bottom = this.border.bottomSide().toString().repeat(maxLen).trim();
        if (!bottom.isEmpty())
            sb.append(this.border.bottomLeftCorner()).append(bottom).append(this.border.bottomRightCorner()).append("\n");
        return sb.toString();
    }
}
