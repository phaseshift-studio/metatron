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

import java.util.*;
import java.util.function.Function;

import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.ObjFactory.Helper.containsObjs;
import static studio.phaseshift.metatron.isa.m.type.ObjFactory.Helper.reflectionBasedCreate;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.sys.sysInstSet.FACTORY_TID;
import static studio.phaseshift.metatron.isa.sys.sysInstSet.SYS_ISA_TID;
import static studio.phaseshift.metatron.util.Tuple.Pair;
import static studio.phaseshift.metatron.util.Tuple.Triplet;

public class MObjFactory extends MRec implements ObjFactory {

    private static MObjFactory SINGLETON = null;
    protected final boolean allowReflection = true;
    protected static final fURI OBJ_FACTORY_TID = SYS_ISA_TID.extend("mfactory");

    public static final Type M_FACTORY_TYPE = Type.Builder.build()
            .tid(FACTORY_TID)
            .vid(OBJ_FACTORY_TID)
            .constructor(instC(INST_TID.dom(ALL.maybe()).rng(OBJ_FACTORY_TID), lst(), (lhs, inst) -> MObjFactory.of()))
            .create();

    private final Map<Class<?>, Function> extensions = new HashMap<>();

    public <JVM, O extends Obj> MObjFactory addExtension(final Class<JVM> objClass, final Function<JVM, O> creator) {
        this.extensions.put(objClass, creator);
        return this;
    }

    protected MObjFactory(final Map<Obj,Obj> jvm,final fURI tid,final fURI vid) {
        super(jvm, tid, vid);
    }
    
    protected MObjFactory() {
        super(Map.of(), OBJ_FACTORY_TID, null);
    }

    public static ObjFactory of() {
        if (SINGLETON == null)
            SINGLETON = new MObjFactory();
        return SINGLETON;
    }

    @Override
    public <OBJ extends Obj> OBJ toObj(final Object value, final fURI tid, final fURI vid) {
        switch (value) {
            case null -> {
                return (OBJ) NoObj.noobj();
            }
            case Obj obj -> {
                return (OBJ) value;
            }
            case Boolean b -> {
                return (OBJ) bool(b, tid, vid);
            }
            case Enum anEnum -> {
                return (OBJ) uri(f(anEnum.name()), tid, vid);
            }
            case Long l -> {
                return (OBJ) jnt(l, tid, vid);
            }
            case Integer i -> {
                return (OBJ) jnt(i, tid, vid);
            }
            case Double aDouble -> {
                return (OBJ) real(aDouble, tid, vid);
            }
            case Float aFloat -> {
                return (OBJ) real(aFloat, tid, vid);
            }
            case Character c -> {
                return (OBJ) str(value.toString(), tid, vid);
            }
            case String s -> {
                return (OBJ) str(s, tid, vid);
            }
            case fURI fURI -> {
                return (OBJ) uri(fURI, tid, vid);
            }
            case List list1 when containsObjs(list1) -> {
                return (OBJ) lst((List<Obj>) value, tid, vid);
            }
            case Collection collection -> {
                final List<Obj> list = new ArrayList<>();
                collection.stream().forEach(e -> list.add(toObj(e)));
                return (OBJ) lst(list, tid, vid);
            }
            case Pair pair -> {
                return (OBJ) rel((Pair<Obj, Obj>) value, tid, vid);
            }
            case Map map1 when containsObjs(map1) -> {
                return (OBJ) rec((Map<Obj, Obj>) value, tid, vid);
            }
            case Map map1 -> {
                final Map<Obj, Obj> map = new LinkedHashMap<>();
                map1.forEach((k, v) -> map.put(toObj(k), toObj(v)));
                return (OBJ) rec(map, tid, vid);
            }
            default -> {
                final Optional<OBJ> optional = this.extensions.entrySet().stream()
                        .filter(e -> e.getKey().isAssignableFrom(value.getClass()))
                        .findFirst().map(e -> (OBJ) e.getValue().apply(value));
                if (optional.isPresent())
                    return optional.get();
                else if (this.allowReflection) {
                    return (OBJ) rec(reflectionBasedCreate(this, value), f(value.getClass().getSimpleName().toLowerCase()), vid);
                } else
                    throw MTronException.of("provided jvm object has no corresponding obj: %s", value);
            }
        }
    }

    @Override
    public <OBJ extends Obj> OBJ toObj(final Object value, final fURI tid, final fURI vid, final Class<OBJ> objClass) {
        if (Bool.class.isAssignableFrom(objClass))
            return (OBJ) new MBool((Boolean) value, null == tid ? BOOL_TID : tid, vid);
        else if (Int.class.isAssignableFrom(objClass))
            return (OBJ) new MInt((Long) value, null == tid ? INT_TID : tid, vid);
        else if (Real.class.isAssignableFrom(objClass) && value instanceof Double)
            return (OBJ) new MReal((Double) value, null == tid ? REAL_TID : tid, vid);
        else if (Real.class.isAssignableFrom(objClass) && value instanceof Float)
            return (OBJ) new MReal(((Float) value).doubleValue(), null == tid ? REAL_TID : tid, vid);
        else if (Str.class.isAssignableFrom(objClass) && value instanceof String)
            return (OBJ) new MStr((String) value, null == tid ? STR_TID : tid, vid);
        else if (Str.class.isAssignableFrom(objClass))
            return (OBJ) new MStr(value.toString(), null == tid ? STR_TID : tid, vid);
        else if (Uri.class.isAssignableFrom(objClass))
            return (OBJ) new MUri((fURI) value, null == tid ? URI_TID : tid, vid);
        else if (Lst.class.isAssignableFrom(objClass))
            return (OBJ) new MLst((List<Obj>) value, null == tid ? LST_TID : tid, vid);
        else if (Rel.class.isAssignableFrom(objClass))
            return (OBJ) new MRel((Pair<Obj, Obj>) value, null == tid ? REL_TID : tid, vid);
        else if (Rec.class.isAssignableFrom(objClass))
            return (OBJ) new MRec((Map<Obj, Obj>) value, null == tid ? REC_TID : tid, vid);
        else if (Inst.class.isAssignableFrom(objClass))
            return (OBJ) new MInst((Triplet<Poly, Inst.f, Obj>) value, null == tid ? INST_TID : tid, vid);
        else if (Code.class.isAssignableFrom(objClass))
            return (OBJ) new MCode((List<Inst>) value, null == tid ? CODE_TID : tid, vid);
        else if (Objs.class.isAssignableFrom(objClass))
            return (OBJ) new MObjs((List<Obj>) value, null == vid ? OBJS_TID : vid);
        else if (Type.class.isAssignableFrom(objClass))
            return (OBJ) new MType((Tuple.Pair<Call, Call>) value, null == vid ? TYPE_TID : tid, vid);
        else if (Fail.class.isAssignableFrom(objClass))
            return (OBJ) new MFail((Pair<Throwable, Fail>) value, null == tid ? FAIL_TID : tid, vid);
        else if (NoObj.class.isAssignableFrom(objClass))
            return (OBJ) NoObj.noobj();
        else
            throw MTronException.of("provided class has not obj equivalent: %s", objClass);
    }
}
