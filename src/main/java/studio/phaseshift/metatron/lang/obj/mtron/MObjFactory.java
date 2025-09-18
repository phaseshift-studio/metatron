package studio.phaseshift.metatron.lang.obj.mtron;

import org.javatuples.Pair;
import org.javatuples.Triplet;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.*;
import studio.phaseshift.metatron.util.MTronException;

import java.util.List;
import java.util.Map;

public class MObjFactory implements ObjFactory {

    private static final MObjFactory SINGLETON = new MObjFactory();

    private MObjFactory() {
    }

    @Override
    public Obj create(final Object value) {
        if (null == value)
            return NoObj.single();
        if(value instanceof Obj)
            return (Obj)value;
        if (value instanceof Boolean)
            return MBool.of((Boolean) value);
        else if (value instanceof Long)
            return new MInt((Long) value);
        else if (value instanceof Double)
            return  new MReal((Double) value);
        else if (value instanceof String)
            return  new MStr((String) value);
        else if (value instanceof fURI)
            return  new MUri((fURI) value);
        else if (value instanceof List)
            return new MLst((List<Obj>) value);
        else if (value instanceof Pair)
            return new MRel((Pair<Obj, Obj>) value);
        else if (value instanceof Rec)
            return new MRec((Map<Obj, Obj>) value);
        //else if (value instanceof Triplet)
        //    return new MInst((Triplet<Poly, Inst.f, Obj>) value);
       // else if (Code.class.isAssignableFrom(objClass))
        //    return (O) new MCode((List<Inst>) value, tid, vid);
        //else if (Objs.class.isAssignableFrom(objClass))
        //    return (O) new MObjs((Iterable<Obj>) value, tid, vid);
       // else if (Type.class.isAssignableFrom(objClass))
       //     return (O) new MType((Obj) value, tid);
        else
            throw MTronException.of("provided value has no corresponding obj: %s", value);
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
            return (O) new MObjs((Iterable<Obj>) value, tid, vid);
        else if (Type.class.isAssignableFrom(objClass))
            return (O) new MType((Obj) value, tid);
        else if (NoObj.class.isAssignableFrom(objClass))
            return (O) NoObj.single();
        else
            throw MTronException.of("provided class has not obj equivalent: %s", objClass);
    }

    public static ObjFactory of() {
        return SINGLETON;
    }
}
