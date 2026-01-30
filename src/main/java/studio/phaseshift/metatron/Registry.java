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

package studio.phaseshift.metatron;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class Registry {

    public static final Registry SINGLETON = new Registry();
    private static final Map<fURI, Function<fURI, Obj>> REGISTRATION = new HashMap<>();

    private Registry() {
        // do nothing
    }

    public static Registry open() {
        return SINGLETON;
    }

    public Lst registrants() {
        return lst(REGISTRATION.keySet().stream().map(fURI::toUri).map(Obj::<Obj>as).toList());
    }


    public void register(final fURI tid, final Function<fURI, Obj> obj) {
        REGISTRATION.put(tid, obj);
    }

    public boolean has(final fURI tid) {
        return REGISTRATION.keySet().stream().anyMatch(tid::matches);
    }

    public <O extends Obj> O load(final fURI module, final fURI vid) {
        return objs(REGISTRATION.entrySet().stream().filter(kv -> kv.getKey().matches(module)).map(kv -> kv.getValue().apply(vid))).as();
    }
}
