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

package studio.phaseshift.metatron.lang.core.m.type.wrap;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.ObjFactory;
import studio.phaseshift.metatron.lang.core.m.type.Rec;
import studio.phaseshift.metatron.lang.core.m.type.impl.MObjFactory;

import java.util.LinkedHashMap;
import java.util.Map;

import static studio.phaseshift.metatron.lang.core.m.type.NoObj.noobj;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class WrapRec implements Rec {

    protected Map<Object, Object> map;
    protected fURI tid;
    protected fURI vid;
    private static final ObjFactory FACTORY = MObjFactory.of();

    public WrapRec(final Map<Object, Object> map, final fURI tid, final fURI vid) {
        this.map = map;
        this.tid = tid;
        this.vid = vid;
    }

    @Override
    public Rec clone(Object jvm, fURI tid, fURI vid) {
        return this;
    }

    @Override
    public Map<Obj, Obj> jvm() {
        final Map<Obj, Obj> objMap = new LinkedHashMap<>();
        this.map.forEach((k, v) -> {
            final Obj key = FACTORY.create(k);
            final Obj value = FACTORY.create(v);
            objMap.put(key, value);
        });
        return objMap;
    }

    @Override
    public <O extends Obj> O at(final Obj key) {
        Object v = this.map.get(key);
        if (null == v)
            v = this.map.get(key.jvm());
        if (null == v)
            return (O) noobj();
        else return (O) FACTORY.create(v);
    }

    @Override
    public Rec self(final Object jvm, final fURI tid, final fURI vid) {
        return null;
    }

    @Override
    public fURI tid() {
        return this.tid;
    }

    @Override
    public fURI vid() {
        return this.vid;
    }

    @Override
    public Obj clone() {
        return this;
    }
}
