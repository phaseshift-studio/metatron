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

package studio.phaseshift.metatron.lang.util.serial;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.type.Rec;
import studio.phaseshift.metatron.util.Common;

import java.util.HashMap;
import java.util.Map;

import static studio.phaseshift.metatron.lang.core.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class Serializers {

    final Map<fURI, ObjSerializer<?>> serializers;

    public Serializers() {
        this.serializers = new HashMap<>();
        this.add(new ObjCleanStringSerializer());
        this.add(new ObjByteBufferSerializer());
        this.add(ObjStringSerializer.build().simpleColon(false).prettyPrint(false).create());
    }

    public ObjSerializer<?> get(final fURI tid) {
        return this.serializers.get(tid);
    }

    public void add(final ObjSerializer<?> serializer) {
        this.serializers.put(serializer.tid(), serializer);
    }

    @Override
    public String toString() {
        return this.serializers.toString();
    }

    public Rec getSerializers() {
        return this.serializers.entrySet().stream().map(kv -> rel(kv.getKey().toUri(), uri(kv.getValue().getClass().getSimpleName()))).collect(new Common.RecCollector());
    }

}
