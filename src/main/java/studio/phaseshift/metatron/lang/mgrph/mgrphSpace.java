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

package studio.phaseshift.metatron.lang.mgrph;

import org.apache.tinkerpop.gremlin.structure.Graph;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.mtron.type.Obj;
import studio.phaseshift.metatron.space.MSpace;

import java.util.Map;

import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.mtron.mtronInstSet.MTRON_SPACE_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mgrphSpace extends MSpace<Graph> {

    /**
     * /root/v/+                       => vertices
     * /root/v/{id}                    => vertex by id
     * /root/v/{id}/outE               => vertex outgoing edges
     * /root/v/{id}/outE/{label}       => vertex outgoing labeled edges
     * /root/v/{id}/out/{id2}          => vertex outgoing adjacent vertices
     * /root/v/{id}/vp/{key}           => vertex property by key
     * /root/v/{id}/vp/{key}/{key2}    => vertex property property by key
     */
    public static final fURI GRAPHSPACE_TID = MTRON_SPACE_TID.extend("grph");
    protected static final fURI V_PATTERN = f("/v/+");
    protected static final fURI E_V_ADJ_PATTERN = f("/v/+/");


    public mgrphSpace(final Graph graph, final fURI pattern, final fURI tid, final fURI vid) {
        super(graph, Map.of(), pattern, tid, vid);
    }

    @Override
    public Obj read(final fURI vid) {
        final fURI subset = vid.removeSubpath(pattern.retractPattern());
        if (subset.equals(V_PATTERN)) {  // g.V()

        } else if (subset.matches(V_PATTERN)) { // g.V(1,2,3)

        }
        return null;
    }

    @Override
    public Obj write(fURI vid, Obj obj) {
        return null;
    }

}
