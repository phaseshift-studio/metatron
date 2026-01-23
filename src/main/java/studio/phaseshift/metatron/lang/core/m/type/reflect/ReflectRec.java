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

package studio.phaseshift.metatron.lang.core.m.type.reflect;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.type.*;
import studio.phaseshift.metatron.lang.core.m.type.impl.MObjFactory;
import studio.phaseshift.metatron.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.util.MTronException;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

import static studio.phaseshift.metatron.lang.core.m.type.NoObj.noobj;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface ReflectRec extends Rec {

    static ObjFactory FACTORY = MObjFactory.of();

    @Override
    default Rec clone(Object jvm, fURI tid, fURI vid) {
        return null;
    }

    @Override
    default Map<Obj, Obj> jvm() {
        return Map.of();
    }

    @Override
    default <O extends Obj> O at(final Obj key) {
        try {
            Graphitty.log(this).info("fetching %s", key);
            final String k = key.isStr() ? key.<Str>as().strValue() : key.isUri() ? key.<Uri>as().uriValue().toString() : "";
            Graphitty.log(this).info("key transformed into %s", k);
            final Optional<O> v = Arrays.stream(this.getClass().getFields()).filter(f -> f.getName().equals(k)).map(f -> MTronException.wrap(() -> (O) FACTORY.create(f.get(this)))).findFirst();
            Graphitty.log(this).info("value retrieved %s", v);
            if (v.isPresent())
                return v.get();
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
        return (O) noobj();
    }

    @Override
    default Rec self(Object jvm, fURI tid, fURI vid) {
        return this;
    }


    @Override
    default Obj clone() {
        return this;
    }
}
