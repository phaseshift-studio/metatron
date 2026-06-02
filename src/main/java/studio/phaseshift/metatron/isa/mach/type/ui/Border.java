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

    default Border foreground(final String color) {
        final String colorBorder = Arrays.stream(this.border().split(";")).map(b -> color + b).reduce((a, b) -> a + ";" + b).orElseThrow();
        return () -> colorBorder;
    }

    default StringBuilder wrap(final StringBuilder builder) {
        final StringBuilder sb = new StringBuilder();
        List<String> inner = Arrays.asList(builder.toString().split("\n"));
        int width = inner.stream().map(Highlighter::visualLength).max(Integer::compareTo).orElse(0);
        sb.append(X).append(this.topLeftCorner()).append(this.topSide().repeat(width)).append(this.topRightCorner()).append(X).append("\n");
        for (final String row : inner) {
            sb.append(X).append(this.leftSide()).append(X).append(row).append(X).append(this.rightSide()).append(X).append("\n");
        }
        sb.append(X).append(this.bottomLeftCorner()).append(this.bottomSide().repeat(width)).append(this.bottomRightCorner()).append(X);
        builder.delete(0, builder.length());
        builder.append(sb);
        return builder;
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
                        this.bottomSide();
        return () -> marginBorder;
    }

    Border simple = () -> "+;+;+;+;|;|;-;-";

    Border thick = () -> "[];[];[];[];||;||;=;=";

    Border none = () -> " ; ; ; ; ; ; ; ";

    Border clean = () -> "┌;┐;└;┘;│;│;─;─";

    Border hash = () -> "#;#;#;#;#;#;#;#";

    Border asterisk = () -> "*;*;*;*;*;*;*;*";

    Border period = () -> ".;.;.;.;.;.;.;.";

    Border rounded = () -> "/;\\;\\;/;|;|;-;-";

}
