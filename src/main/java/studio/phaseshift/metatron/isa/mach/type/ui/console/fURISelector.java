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

package studio.phaseshift.metatron.isa.mach.type.ui.console;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Router;

import java.util.List;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class fURISelector extends SelectorWidget<fURI, fURISelector> {

    public fURISelector(final String originalBufferText) {
        super(originalBufferText, List.of("furi", "", "furi"));

        if (!originalBufferText.isEmpty()) {
            final Obj rels = Router.readFromSpace(f(originalBufferText.substring(1) + "+/"));
            rels.stream().forEach(rel -> {
                if (rel.isRel()) {
                    this.items.add(rel.asRel().first().uriValue());
                }
            });
        }
        populateRows();
    }

    @Override
    protected List<String> cellsForItem(final fURI item) {
        return List.of(item.toString());
    }

    @Override
    protected String getTitleLine() {
        return studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty.string(
                "{{g}}%d fURIs found{{X}}", items.size());
    }

    @Override
    protected void writeSelection(final fURI item) {
        final String furiName = item.toString();
        final var buf = Console.LOCAL_INSTANCE.getReader().getBuffer();
        buf.clear();
        buf.write("*" + furiName + "/");
        buf.cursor(buf.length());
    }

    @Override
    public String toString() {
        return "fURISelector[count=" + items.size() + "]";
    }
}
