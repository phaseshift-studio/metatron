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

package studio.phaseshift.metatron.isa.grph.tp3.graph;

import org.apache.tinkerpop.gremlin.structure.io.AbstractIoRegistry;
import org.apache.tinkerpop.gremlin.structure.io.gryo.GryoIo;
import studio.phaseshift.metatron.furi.fURI;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mIoRegistry extends AbstractIoRegistry {

    private static final mIoRegistry INSTANCE = new mIoRegistry();

    private mIoRegistry() {
        //try {
        super.register(GryoIo.class, fURI.class, null);
        // } catch (final ClassNotFoundException e) {
        //    throw new IllegalStateException(e);
        //}
    }

    public static mIoRegistry instance() {
        return INSTANCE;
    }
}
