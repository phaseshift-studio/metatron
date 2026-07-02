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

import studio.phaseshift.metatron.isa.mach.type.ui.console.Highlighter;

import java.util.Arrays;
import java.util.List;

import static studio.phaseshift.metatron.isa.mach.type.ui.Widget.X;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Border {

    String border();

    default String topLeftCorner() {
        return this.border().split(";")[0];
    }

    default String topRightCorner() {
        return this.border().split(";")[1];
    }

    default String bottomLeftCorner() {
        return this.border().split(";")[2];
    }

    default String bottomRightCorner() {
        return this.border().split(";")[3];
    }

    default String leftSide() {
        return this.border().split(";")[4];
    }

    default String rightSide() {
        return this.border().split(";")[5];
    }

    default String topSide() {
        return this.border().split(";")[6];
    }

    default String bottomSide() {
        return this.border().split(";")[7];
    }

    /** Character where a vertical inner divider meets the top border. */
    default String topIntersection() {
        final String[] p = this.border().split(";");
        return p.length > 8 ? p[8] : this.topSide();
    }

    /** Character where a vertical inner divider meets the bottom border. */
    default String bottomIntersection() {
        final String[] p = this.border().split(";");
        return p.length > 9 ? p[9] : this.bottomSide();
    }

    /** Character where a horizontal inner divider meets the left border. */
    default String leftIntersection() {
        final String[] p = this.border().split(";");
        return p.length > 10 ? p[10] : this.leftSide();
    }

    /** Character where a horizontal inner divider meets the right border. */
    default String rightIntersection() {
        final String[] p = this.border().split(";");
        return p.length > 11 ? p[11] : this.rightSide();
    }

    default Border foreground(final String color) {
        final String colorBorder = Arrays.stream(this.border().split(";")).map(b -> color + b).reduce((a, b) -> a + ";" + b).orElseThrow();
        return () -> colorBorder;
    }

    default StringBuilder wrap(final StringBuilder builder) {
        final String rawLeft = Highlighter.unformat(this.leftSide());
        final char divider = rawLeft.isEmpty() ? '|' : rawLeft.charAt(0);

        final List<String> inner = Arrays.asList(builder.toString().split("\n"));
        final int width = inner.stream().map(Highlighter::visualLength).max(Integer::compareTo).orElse(0);

        // Auto-detect vertical divider positions for T-junctions.
        // Skip positions 0 and width-1 (outer dividers sit against corners).
        final java.util.BitSet intersections = new java.util.BitSet(width);
        for (final String row : inner) {
            final String stripped = Highlighter.unformat(row);
            for (int i = 1; i < stripped.length() - 1 && i < width - 1; i++) {
                if (stripped.charAt(i) == divider) {
                    intersections.set(i);
                }
            }
        }

        final StringBuilder sb = new StringBuilder();
        sb.append(X).append(this.topLeftCorner());
        sb.append(buildBorderLine(width, this.topSide(), this.topIntersection(), intersections));
        sb.append(this.topRightCorner()).append(X).append("\n");
        for (final String row : inner) {
            // Hide outer dividers so they don't visually overlap with the border
            // side characters.  Inner dividers stay visible.
            final int first = row.indexOf(divider);
            final int last = row.lastIndexOf(divider);
            final String sanitized;
            if (first >= 0 && last > first) {
                sanitized = row.substring(0, first) + ' '
                          + row.substring(first + 1, last) + ' '
                          + row.substring(last + 1);
            } else {
                sanitized = row;
            }
            sb.append(X).append(this.leftSide()).append(X).append(sanitized).append(X).append(this.rightSide()).append(X).append("\n");
        }
        sb.append(X).append(this.bottomLeftCorner());
        sb.append(buildBorderLine(width, this.bottomSide(), this.bottomIntersection(), intersections));
        sb.append(this.bottomRightCorner()).append(X);
        builder.delete(0, builder.length());
        builder.append(sb);
        return builder;
    }

    /** Build a single border line, placing intersection chars at the given column positions. */
    private static StringBuilder buildBorderLine(final int width, final String normal, final String intersection, final java.util.BitSet positions) {
        final StringBuilder sb = new StringBuilder(width);
        for (int i = 0; i < width; i++) {
            sb.append(positions.get(i) ? intersection : normal);
        }
        return sb;
    }

    default Border margin(int left, int right) {
        final String marginBorder =
                this.topLeftCorner() + this.topSide().repeat(left) + ";" +
                        this.topSide().repeat(right) + this.topRightCorner() + ";" +
                        this.bottomLeftCorner() + this.bottomSide().repeat(left) + ";" +
                        this.bottomSide().repeat(right) + this.bottomRightCorner() + ";" +
                        this.leftSide() + " ".repeat(left) + ";" +
                        " ".repeat(right) + this.rightSide() + ";" +
                        this.topSide() + ";" +
                        this.bottomSide() + ";" +
                        this.topIntersection() + ";" +
                        this.bottomIntersection() + ";" +
                        this.leftIntersection() + ";" +
                        this.rightIntersection();
        return () -> marginBorder;
    }

    // Format: tlCorner;trCorner;blCorner;brCorner;leftSide;rightSide;topSide;bottomSide;topIntersect;bottomIntersect;leftIntersect;rightIntersect

    static Border parse(final String name) {
        if (name == null || name.isEmpty()) return Border.none;
        // Extract the last segment if it's a URI path (e.g. /.../content/continuous → continuous)
        final String shortName = name.contains("/") ? name.substring(name.lastIndexOf('/') + 1) : name;
        return switch (shortName.toLowerCase()) {
            case "simple"     -> Border.simple;
            case "thick"      -> Border.thick;
            case "none"       -> Border.none;
            case "hash"       -> Border.hash;
            case "asterisk"   -> Border.asterisk;
            case "period"     -> Border.period;
            case "rounded"    -> Border.rounded;
            case "continuous" -> Border.continuous;
            default -> Border.none;
        };
    }

    Border simple = () -> "+;+;+;+;|;|;-;-;-;-;|;|";

    Border thick = () -> "[];[];[];[];||;||;=;=;=;=;||;||";

    Border none = () -> " ; ; ; ; ; ; ; ; ; ; ; ";

    Border hash = () -> "#;#;#;#;#;#;#;#;#;#;#;#";

    Border asterisk = () -> "*;*;*;*;*;*;*;*;*;*;*;*";

    Border period = () -> ".;.;.;.;.;.;.;.;.;.;.;.";

    Border rounded = () -> "/;\\;\\;/;|;|;-;-;-;-;|;|";

    Border continuous = () -> "┌;┐;└;┘;│;│;─;─;┬;┴;├;┤";
}
