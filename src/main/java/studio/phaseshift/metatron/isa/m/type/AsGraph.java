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

package studio.phaseshift.metatron.isa.m.type;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.mach.type.Machine;

import java.util.HashMap;
import java.util.Map;

import static studio.phaseshift.metatron.isa.m.mInstSet.AS_INST_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class AsGraph {

    public static final AsGraph INSTANCE = new AsGraph();

    public static final AsGraph single() {
        return INSTANCE;
    }

    public final Map<fURI, Map<fURI, Inst>> cache = new HashMap<>();

    public void addAs(final Inst inst) {
        this.cache.getOrDefault(inst.tid().dom(), new HashMap<>()).put(inst.tid().rng(), inst);
    }

    public Inst getAs(final fURI tid) {
        return this.cache.getOrDefault(tid.dom(), Map.of()).get(tid.rng());
    }

    public void refillCache() {
        Machine.current().memory().read(AS_INST_TID).stream().filter(Obj::isInst).map(Obj::asInst).forEach(this::addAs);
    }
}
