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
    protected final List<String> lrtb;

    public static final List<String> NO_BORDER = List.of("", "", "", "", "", "", "", "");
    public static final List<String> BASIC_BORDER = List.of("|", "|", "-", "-", "+", "+", "+", "+");
    public static final List<String> DOUBLE_BORDER = List.of("||", "||", "=", "=", "//", "\\\\", "//", "\\\\");

    public static List<String> coloredBorder(final List<String> border, final String color) {
        return border.stream().map(s -> Graphitty.string("{{%s}}%s{{X}}", color, s)).toList();
    }

    public Box(final String body, final List<String> lrtb) {
        this(null, body, lrtb);
    }

    public Box(final String title, final String body, final List<String> lrtb) {
        this(title, null, body, lrtb);
    }

    public Box(final String title, final List<String> options, final String body, final List<String> lrtb) {
        this.title = title;
        this.body = body;
        this.lrtb = lrtb;
        this.options = options;
    }

    public Box bottom(final Dimensions dims, final List<String> border) {
        return this.bottom(null, dims, border);
    }

    public Box bottom(final List<String> options, final Dimensions dims, final List<String> border) {
        return new Box(null, options, this.toString() + "\n" + dims.toString(), border);
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
        return new Box(sb.toString(), this.lrtb);
    }

    public String toString() {
        final List<String> lines = Arrays.asList(this.body.replace("\\n", "\n").split("\\r?\\n", -1));
        final int maxLen = Stream.concat(Stream.of(this.title).filter(Objects::nonNull), lines.stream())
                .map(Graphitty::strip)
                .mapToInt(String::length)
                .max()
                .orElse(0);

        final StringBuilder sb = new StringBuilder();
        final String top = "%s%s".formatted(null == this.title ? "" : this.title, this.lrtb.get(2).repeat(null == this.title ? maxLen : maxLen - Graphitty.strip(this.title).length())).trim();
        if (!top.isEmpty())
            sb.append(this.lrtb.get(4)).append(top).append(this.lrtb.get(5)).append('\n');
        if (null != options) {
            sb.append(this.lrtb.get(0)).append(this.options).append(" ".repeat(maxLen - Graphitty.strip(this.options.toString()).length())).append(this.lrtb.get(1)).append('\n');
        }
        for (final String line : lines) {
            sb.append(this.lrtb.get(0))
                    .append(line)
                    .append(" ".repeat(maxLen - Graphitty.strip(line).length()))
                    .append(this.lrtb.get(1))
                    .append('\n');
        }
        final String bottom = this.lrtb.get(3).repeat(maxLen).trim();
        if (!bottom.isEmpty())
            sb.append(this.lrtb.get(6)).append(bottom).append(this.lrtb.get(7)).append("\n");
        return sb.toString();
    }
}
