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

package studio.phaseshift.metatron.lang.jre;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.Rec;
import studio.phaseshift.metatron.lang.core.m.type.Str;
import studio.phaseshift.metatron.lang.core.m.type.Uri;
import studio.phaseshift.metatron.lang.core.m.type.impl.MObj;
import studio.phaseshift.metatron.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.MTronException;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.lang.core.m.type.impl.MObjs.objs;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class JRec extends MObj implements Rec {

    protected final GraphittyLogger LOG = Graphitty.log(this);

    public JRec(final Object jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }


    @Override
    public JRec clone(final Object jvm, final fURI tid, final fURI vid) {
        return super.clone(jvm, tid, vid);
    }

    @Override
    public <O extends Obj> O at(final Obj key) {
        return (O) objs(this.findField(key).stream().map(f -> JObjFactory.single().create(f, MTronException.wrap(() -> f.get(this.jvm)), null)));
    }

    @Override
    public JRec put(final Obj key, final Obj value) {
        try {
            this.findField(key).forEach(f -> MTronException.wrap(() -> f.set(this.jvm, value.jvm())));
            return this;
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }

    @Override
    public Map<Obj, Obj> jvm() {
        return Map.of();
    }

    @Override
    public JRec self(Object jvm, fURI tid, fURI vid) {
        return super.self(jvm, tid, vid);
    }

    protected final List<Field> findField(final Obj key) {
        String javaName = key.isStr() ? key.strValue() : key.isUri() ? key.uriValue().toString() : null;
        if (null == javaName)
            return List.of();
        boolean allWildcard = javaName.endsWith("#");
        if (allWildcard)
            javaName = javaName.substring(0, javaName.length() - 2);
        //javaName = javaName.replace('.', '/');
        final String finalJavaName = javaName;
        return Arrays.stream(this.getClass().getFields()).filter(f -> allWildcard ? f.getName().startsWith(finalJavaName) : f.getName().equals(finalJavaName)).toList();
    }
}
