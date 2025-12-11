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

    private Box() {

    }


    public static String wrap(final String text, final List<String> lrtb) {
        final List<String> lines = Arrays.asList(text.replace("\\n","\n").split("\\r?\\n", -1));
        final int maxLen = lines.stream()
                .map(Graphitty::strip)
                .mapToInt(String::length)
                .max()
                .orElse(0);

        final StringBuilder sb = new StringBuilder();
        sb.append("+%s+".formatted(lrtb.get(2).repeat(maxLen))).append('\n');
        for (final String line : lines) {
            sb.append(lrtb.get(0))
                    .append(line)
                    .append(" ".repeat(maxLen - Graphitty.strip(line).length()))
                    .append(lrtb.get(1))
                    .append('\n');
        }
        sb.append("+%s+".formatted(lrtb.get(3).repeat(maxLen))).append("\n");
        return sb.toString();
    }

}
