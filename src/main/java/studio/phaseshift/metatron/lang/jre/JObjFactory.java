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
import studio.phaseshift.metatron.lang.core.m.type.impl.MObjFactory;
import studio.phaseshift.metatron.lang.sys.fs.fileSpace;
import studio.phaseshift.metatron.util.MTronException;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Map;

import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.furi.fURI.fnull;
import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.STR_TID;
import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.URI_TID;
import static studio.phaseshift.metatron.lang.sys.fs.fsInstSet.FILE_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class JObjFactory extends MObjFactory {

    private static final JObjFactory SINGLETON = new JObjFactory();

    protected JObjFactory() {
        super();
    }

    @Override
    public <O extends Obj> O create(final Object value, final fURI tid, final fURI vid, final Class<O> objClass) {
        return null;
    }

    public static JObjFactory single() {
        return SINGLETON;
    }

    public Obj create(final Field field, final Object value, final fURI vid) {
        final ObjField annotation = field.getAnnotation(ObjField.class);
        final fURI tid = (null == annotation || annotation.tid().equals("noobj")) ? f(value.getClass().getCanonicalName().replace(".", "/")) : f(annotation.tid());
        //final fURI basetid = annotation.basetid().equals("noobj") ? null : f(annotation.basetid());
        Object newValue = value;
        if (tid.equals(STR_TID)) {
            newValue = value.toString();
        } else if (tid.equals(URI_TID)) {
            newValue = f(value.toString());
        } else if (tid.equals(FILE_TID))
            return fileSpace.makeFile(Path.of(value.toString()));
        return create(newValue, tid, vid);
    }

    @Override
    public Obj create(final Object value, final fURI tid, final fURI vid) {
        try {
            return super.create(value, tid, vid);
        } catch (final MTronException e) {
            // do nothing
        }
        return new JRec(value, Map.of(), null == tid ? f(value.getClass().getCanonicalName().replace(".", "/")) : tid, fnull);
    }
}
