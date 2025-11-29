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

package studio.phaseshift.metatron.lang.core.m.type.impl;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.obj.NoObj;
import studio.phaseshift.metatron.lang.core.m.type.*;

import studio.phaseshift.metatron.util.MTronException;

import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.lang.core.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MType.T;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.Tuple.Pair;
import static studio.phaseshift.metatron.util.Tuple.Triplet;

public class MObjFactory implements ObjFactory {

    private static final MObjFactory SINGLETON = new MObjFactory();

    private MObjFactory() {
    }

    public static ObjFactory of() {
        return SINGLETON;
    }

    @Override
    public Obj create(final Object value) {
        if (null == value)
            return NoObj.noobj();
        if (value instanceof Obj)
            return (Obj) value;
        if (value instanceof Boolean)
            return bool((Boolean) value);
        else if (value instanceof Long)
            return jnt((Long) value);
        else if (value instanceof Integer)
            return jnt((Integer) value);
        else if (value instanceof Double)
            return real((Double) value);
        else if (value instanceof Float)
            return real((Float) value);
        else if (value instanceof String)
            return str((String) value);
        else if (value instanceof fURI)
            return uri((fURI) value);
        else if (value instanceof List)
            return lst((List<Obj>) value);
        else if (value instanceof Pair)
            return rel((Pair<Obj, Obj>) value);
        else if (value instanceof Map)
            return rec((Map<Obj, Obj>) value);
            //else if (value instanceof Triplet)
            //    return new MInst((Triplet<Poly, Inst.f, Obj>) value);
            // else if (Code.class.isAssignableFrom(objClass))
            //    return (O) new MCode((List<Inst>) value, tid, vid);
            //else if (Objs.class.isAssignableFrom(objClass))
            //    return (O) new MObjs((Iterable<Obj>) value, tid, vid);
            // else if (Type.class.isAssignableFrom(objClass))
            //     return (O) new MType((Obj) value, tid);
        else
            throw MTronException.of("provided jvm object has no corresponding obj: %s", value);
    }

    @Override
    public <O extends Obj> O create(final Object value, final fURI tid, final fURI vid, final Class<O> objClass) {
        if (Bool.class.isAssignableFrom(objClass))
            return (O) new MBool((Boolean) value, tid, vid);
        else if (Int.class.isAssignableFrom(objClass))
            return (O) new MInt((Long) value, tid, vid);
        else if (Real.class.isAssignableFrom(objClass))
            return (O) new MReal((Double) value, tid, vid);
        else if (Str.class.isAssignableFrom(objClass))
            return (O) new MStr((String) value, tid, vid);
        else if (Uri.class.isAssignableFrom(objClass))
            return (O) new MUri((fURI) value, tid, vid);
        else if (Lst.class.isAssignableFrom(objClass))
            return (O) new MLst((List<Obj>) value, tid, vid);
        else if (Rel.class.isAssignableFrom(objClass))
            return (O) new MRel((Pair<Obj, Obj>) value, tid, vid);
        else if (Rec.class.isAssignableFrom(objClass))
            return (O) new MRec((Map<Obj, Obj>) value, tid, vid);
        else if (Inst.class.isAssignableFrom(objClass))
            return (O) new MInst((Triplet<Poly, Inst.f, Obj>) value, tid, vid);
        else if (Code.class.isAssignableFrom(objClass))
            return (O) new MCode((List<Inst>) value, tid, vid);
        else if (Objs.class.isAssignableFrom(objClass))
            return (O) new MObjs((Iterable<Obj>) value, vid);
        else if (Type.class.isAssignableFrom(objClass))
            return (O) T(tid, null, (Call) value);
        else if (NoObj.class.isAssignableFrom(objClass))
            return (O) NoObj.noobj();
        else
            throw MTronException.of("provided class has not obj equivalent: %s", objClass);
    }
}
