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

package studio.phaseshift.metatron.lang.sys.console;

import studio.phaseshift.metatron.ui.Graphitty;

import java.util.Arrays;
import java.util.List;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class Box {

    protected final String body;
    protected final List<String> lrtb;

    public static final List<String> NO_BORDER = List.of(" ", " ", " ", " ", "", "", "", "");
    public static final List<String> BASIC_BORDER = List.of("|", "|", "-", "-", "+", "+", "+", "+");
    public static final List<String> DOUBLE_BORDER = List.of("||", "||", "=", "=", "//", "\\\\", "//", "\\\\");

    public static List<String> coloredBorder(final List<String> border, final String color) {
        return border.stream().map(s -> Graphitty.string("{{%s}}%s{{X}}", color, s)).toList();
    }

    public Box(final String body, final List<String> lrtb) {
        this.body = body;
        this.lrtb = lrtb;
    }

    public int width() {
        return Graphitty.strip(this.toString().split("\n")[0]).length();
    }

    public int height() {
        return this.toString().split("\n").length;
    }

    public String row(final int r) {
        return this.toString().split("\n")[r];
    }

    public Box bottom(final Box box) {
        return new Box(this.toString().trim() + "\n" + box.toString().trim(), box.lrtb);
    }

    public Box right(final Box box) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.max(this.height(), box.height()); i++) {
            if (i < this.height()) {
                sb.append(this.row(i));
            } else {
                sb.append(" ".repeat(this.width()));
            }
            if (i < box.height()) {
                sb.append(" ").append(box.row(i));
            }
            sb.append("\n");
        }
        sb.deleteCharAt(sb.length() - 1);
        return new Box(sb.toString(), box.lrtb);
    }

    public String toString() {
        final List<String> lines = Arrays.asList(this.body.replace("\\n", "\n").split("\\r?\\n", -1));
        final int maxLen = lines.stream()
                .map(Graphitty::strip)
                .mapToInt(String::length)
                .max()
                .orElse(0);

        final StringBuilder sb = new StringBuilder();
        sb.append("%s%s%s".formatted(this.lrtb.get(4), this.lrtb.get(2).repeat(maxLen), this.lrtb.get(5))).append('\n');
        for (final String line : lines) {
            sb.append(this.lrtb.get(0))
                    .append(line)
                    .append(" ".repeat(maxLen - Graphitty.strip(line).length()))
                    .append(this.lrtb.get(1))
                    .append('\n');
        }
        sb.append("%s%s%s".formatted(this.lrtb.get(6), this.lrtb.get(3).repeat(maxLen), this.lrtb.get(7))).append("\n");
        return sb.toString();
    }

}
