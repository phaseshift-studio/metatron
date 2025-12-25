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
public interface Dimensions {

    default int width() {
        return Arrays.stream(this.toString().split("\n")).map(Graphitty::strip).map(String::length).max(Integer::compareTo).orElse(0);
    }

    default int height() {
        return this.toString().split("\n").length;
    }

    default String rowString(int i) {
        return this.toString().split("\n")[i];
    }

    public static class StringDimensions implements Dimensions {
        final String string;

        public StringDimensions(final String string) {
            this.string = string;
        }

        @Override
        public String toString() {
            return this.string;
        }
    }
}
