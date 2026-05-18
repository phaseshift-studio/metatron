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

package studio.phaseshift.metatron.isa.mach.type.ui.console;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Router;

import java.util.ArrayList;
import java.util.List;

import static studio.phaseshift.metatron.isa.m.mInstSet.M_ISA_INST_TID;

/**
 * InstSelector - A widget for selecting instructions based on domain type.
 * <p>
 * Features:
 * - Navigate rows with up/down arrow keys
 * - Press Enter to select instruction and append to buffer
 * - Press ESC to cancel
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class InstSelector extends SelectorWidget<Inst, InstSelector> {

    private final fURI domType;

    public InstSelector(final Code code, final String originalBufferText) {
        super(originalBufferText, List.of("op", "dom", "rng", "", "op", "dom", "rng"));

        if (!code.codeValue().isEmpty()) {
            final Inst lastInst = code.codeValue().getLast();
            this.domType = lastInst.rng().tid();
            final Obj instructionsObj = Router.global().read(M_ISA_INST_TID.extend("#").dom(domType));
            instructionsObj.stream().forEach(obj -> {
                if (obj.isInst()) {
                    this.items.add(obj.as());
                }
            });
        } else {
            this.domType = null;
        }
        populateRows();
    }

    @Override
    protected List<String> cellsForItem(final Inst item) {
        return new ArrayList<String>((List) List.of(
                item.tid().name(),
                item.dom().tid().small(),
                item.rng().tid().small())
        );
    }

    @Override
    protected String getTitleLine() {
        return studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty.string(
                "{{g}}insts with {{c}}dom={{y}}%s{{X}} {{w}}(%d found){{X}}",
                domType.small(), items.size());
    }

    @Override
    protected void writeSelection(final Inst item) {
        final String instName = item.tid().name();
        final var buf = Console.LOCAL_INSTANCE.getReader().getBuffer();
        buf.clear();
        buf.write(originalBufferText + instName + "(");
        buf.cursor(buf.length());
    }

    @Override
    public String toString() {
        return "InstSelector[dom=" + domType + ", count=" + items.size() + "]";
    }
}
