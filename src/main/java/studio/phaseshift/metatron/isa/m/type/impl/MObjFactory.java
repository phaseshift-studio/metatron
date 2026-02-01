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

package studio.phaseshift.metatron.isa.m.type.impl;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.*;

import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.Tuple.Pair;
import static studio.phaseshift.metatron.util.Tuple.Triplet;

public class MObjFactory implements ObjFactory {

    private static final MObjFactory SINGLETON = new MObjFactory();

    protected MObjFactory() {
    }

    public static ObjFactory of() {
        return SINGLETON;
    }

    @Override
    public <O extends Obj> O create(final Object value, final fURI tid, final fURI vid) {
        if (null == value)
            return (O) NoObj.noobj();
        if (value instanceof Obj)
            return (O) value;
        if (value instanceof Boolean)
            return (O) bool((Boolean) value, tid, vid);
        else if (value instanceof Long)
            return (O) jnt((Long) value, tid, vid);
        else if (value instanceof Integer)
            return (O) jnt((Integer) value, tid, vid);
        else if (value instanceof Double)
            return (O) real((Double) value, tid, vid);
        else if (value instanceof Float)
            return (O) real((Float) value, tid, vid);
        else if (value instanceof String)
            return (O) str((String) value, tid, vid);
        else if (value instanceof fURI)
            return (O) uri((fURI) value, tid, vid);
        else if (value instanceof List)
            return (O) lst((List<Obj>) value, tid, vid);
        else if (value instanceof Pair)
            return (O) rel((Pair<Obj, Obj>) value, tid, vid);
        else if (value instanceof Map)
            return (O) rec((Map<Obj, Obj>) value, tid, vid);
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
            return (O) new MBool((Boolean) value, null == tid ? BOOL_TID : tid, vid);
        else if (Int.class.isAssignableFrom(objClass))
            return (O) new MInt((Long) value, null == tid ? INT_TID : tid, vid);
        else if (Real.class.isAssignableFrom(objClass))
            return (O) new MReal((Double) value, null == tid ? REAL_TID : tid, vid);
        else if (Str.class.isAssignableFrom(objClass))
            return (O) new MStr((String) value, null == tid ? STR_TID : tid, vid);
        else if (Uri.class.isAssignableFrom(objClass))
            return (O) new MUri((fURI) value, null == tid ? URI_TID : tid, vid);
        else if (Lst.class.isAssignableFrom(objClass))
            return (O) new MLst((List<Obj>) value, null == tid ? LST_TID : tid, vid);
        else if (Rel.class.isAssignableFrom(objClass))
            return (O) new MRel((Pair<Obj, Obj>) value, null == tid ? REL_TID : tid, vid);
        else if (Rec.class.isAssignableFrom(objClass))
            return (O) new MRec((Map<Obj, Obj>) value, null == tid ? REC_TID : tid, vid);
        else if (Inst.class.isAssignableFrom(objClass))
            return (O) new MInst((Triplet<Poly, Inst.f, Obj>) value, null == tid ? INST_TID : tid, vid);
        else if (Code.class.isAssignableFrom(objClass))
            return (O) new MCode((List<Inst>) value, null == tid ? CODE_TID : tid, vid);
        else if (Objs.class.isAssignableFrom(objClass))
            return (O) new MObjs((List<Obj>) value, null == vid ? OBJS_TID : vid);
        else if (Type.class.isAssignableFrom(objClass))
            return (O) new MType((Tuple.Pair<Call, Call>) value, null == vid ? TYPE_TID : tid, vid);
        else if (Fail.class.isAssignableFrom(objClass))
            return (O) new MFail((Pair<Throwable, Fail>) value, null == tid ? FAIL_TID : tid, vid);
        else if (NoObj.class.isAssignableFrom(objClass))
            return (O) NoObj.noobj();
        else
            throw MTronException.of("provided class has not obj equivalent: %s", objClass);
    }
}
