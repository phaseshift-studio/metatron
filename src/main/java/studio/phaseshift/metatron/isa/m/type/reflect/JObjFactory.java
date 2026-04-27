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

package studio.phaseshift.metatron.isa.m.type.reflect;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.type.impl.MObjFactory;
import studio.phaseshift.metatron.isa.mach.io.space.fs.fsSpace;

import java.lang.reflect.Field;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.STR_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.URI_TID;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Bytes.BYTES_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Int.INT_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Lst.LST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.NOOBJ_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Real.REAL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.mach.machInstSet.FILE_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class JObjFactory extends MObjFactory {

    private static final JObjFactory SINGLETON = new JObjFactory();

    protected JObjFactory() {
        super();
    }

    @Override
    public <O extends Obj> O toObj(final Object value, final fURI tid, final fURI vid, final Class<O> objClass) {
        return null;
    }

    public static JObjFactory single() {
        return SINGLETON;
    }

    public Obj create(final Field field, final Object value, final fURI vid) {
        final JRecElement annotation = field.getAnnotation(JRecElement.class);
        final fURI tid = (null == annotation || annotation.rng().equals("noobj")) ? f(value.getClass().getCanonicalName().replace(".", "/")) : f(annotation.rng());
        //final fURI basetid = annotation.basetid().equals("noobj") ? null : f(annotation.basetid());
        Object newValue = value;
        if (value instanceof Obj)
            return (Obj) value;
        if (tid.equals(STR_TID)) {
            newValue = value.toString();
        } else if (tid.equals(URI_TID)) {
            newValue = f(value.toString());
        } else if (tid.equals(FILE_TID)) {
            return fsSpace.makeFile((Path) value);
        } else if (tid.equals(f("/m/inst"))) {
            return instC(f(field.getName()), lst(), (BiFunction<Obj, Inst, Obj>) value);
        }
        return this.toObj(newValue, tid, vid);
    }

    public Type createType(final Class<?> clazz) {
        if (null == clazz) return NOOBJ_TYPE;
        if (Obj.class.isAssignableFrom(clazz)) return T(ALL);
        if (Integer.class.isAssignableFrom(clazz)) return INT_TYPE;
        if (Long.class.isAssignableFrom(clazz)) return INT_TYPE;
        if (Double.class.isAssignableFrom(clazz)) return REAL_TYPE;
        if (Float.class.isAssignableFrom(clazz)) return REAL_TYPE;
        if (ByteBuffer.class.isAssignableFrom(clazz)) return BYTES_TYPE;
        if (fURI.class.isAssignableFrom(clazz)) return URI_TYPE;
        if (Boolean.class.isAssignableFrom(clazz)) return BOOL_TYPE;
        if (String.class.isAssignableFrom(clazz)) return STR_TYPE;
        if (URI.class.isAssignableFrom(clazz)) return URI_TYPE;
        if (List.class.isAssignableFrom(clazz)) return LST_TYPE;
        if (Map.class.isAssignableFrom(clazz)) return REC_TYPE;
        return T(f(clazz.getCanonicalName().replace(".", "/")));
    }

    @Override
    public Obj toObj(final Object value, final fURI tid, final fURI vid) {
        return super.toObj(value, tid, vid);
    }
}
