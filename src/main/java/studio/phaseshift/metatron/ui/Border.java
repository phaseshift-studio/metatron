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

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Border extends Stylable<Border> {

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

    default Border style(final Style style) {
        final String colorBorder = Arrays.stream(border().split(";")).map(b -> style.foreground + b + "{{X}}").reduce((a, b) -> a + ";" + b).orElseThrow();
        return () -> colorBorder;
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
                        this.bottomSide() + ";";
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
