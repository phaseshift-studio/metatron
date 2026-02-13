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

package studio.phaseshift.metatron.isa.grph.tp3.parser;

import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Element;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.grph.tp3.space.EdgeMap;
import studio.phaseshift.metatron.isa.grph.tp3.space.VertexMap;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.AbstractObjSerializer;
import studio.phaseshift.metatron.util.MTronException;

import java.nio.ByteBuffer;

import static studio.phaseshift.metatron.isa.mach.io.ioInstSet.OBJ_SERIALIZER_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ObjTP3Serializer extends AbstractObjSerializer<Element> {

    public static final fURI OBJ_TP3_SERIALIZER_VID = OBJ_SERIALIZER_TID.extend("tp3");

    public ObjTP3Serializer() {

    }

    @Override
    public ByteBuffer outputBytes(final Obj obj) throws MTronException {
        return null;
    }

    @Override
    public Obj inputBytes(final ByteBuffer bytes) throws MTronException {
        return null;
    }

    @Override
    public Obj read(final Element data) throws MTronException {
        return data instanceof Vertex ? VertexMap.vertexToRec((Vertex) data) : EdgeMap.edgeToRec((Edge) data);
    }

    @Override
    public fURI vid() {
        return OBJ_TP3_SERIALIZER_VID;
    }
}
