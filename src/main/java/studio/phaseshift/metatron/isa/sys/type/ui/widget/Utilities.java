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

package studio.phaseshift.metatron.isa.sys.type.ui.widget;

import studio.phaseshift.metatron.isa.sys.type.console.Highlighter;
import studio.phaseshift.metatron.isa.sys.type.ui.Widget;
import studio.phaseshift.metatron.isa.sys.type.ui.graphitty.Graphitty;

import java.util.Arrays;
import java.util.List;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class Utilities {

    private Utilities() {
        //do nothing
    }

    public static final CharSequence esc_key = "\u001b";
    public static final CharSequence tab_key = "\t";
    public static final CharSequence enter_key = "\r";
    public static final String up_key = "{{^1}}";
    public static final String down_key = "{{v1}}";
    public static final CharSequence left_key = "\u2190";
    public static final CharSequence right_key = "\02192";
    public static int maxWidth(final List<String> strings) {
        return strings.stream().flatMap(s -> Arrays.stream(s.split("\n"))).map(Highlighter::visualLength).max(Integer::compareTo).orElse(0);
    }

    public static void runCursorLessWidget(final Widget<?> widget, final boolean close) {
        Graphitty.log(Widget.class).none("{{.}}");
        widget.run();
        Graphitty.log(Widget.class).none("{{*}}");
        if (close)
            widget.close();
    }

}
