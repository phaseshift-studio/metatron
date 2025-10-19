/*
 * Metatron: A Distributed Computing Language and Virtual Machine
 * Copyright (C) 2025- PhaseShift Studio, LLC
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

package studio.phaseshift.metatron.lang.obj.mgrph;

import org.apache.tinkerpop.gremlin.structure.Vertex;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.space.mem.MSpace;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class VertexSpace extends MSpace<Vertex> {

    public VertexSpace(final Vertex vertex, final fURI pattern, final fURI tid, final fURI vid) {
        super(vertex, pattern, tid, vid);
    }

    @Override
    public Obj read(final fURI vid) {
        return null;
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        return null;
    }
}
