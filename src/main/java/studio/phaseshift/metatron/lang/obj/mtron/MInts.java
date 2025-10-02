package studio.phaseshift.metatron.lang.obj.mtron;

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.NoObj;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.lang.obj.Objs;
import studio.phaseshift.metatron.lang.obj.Type;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.ObjUtil;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class MInts implements Objs {

    private fURI vid;
    private final Map<Obj, MCoeff.Int> map = new LinkedHashMap<>();

    public MInts(final Iterable<Obj> ints, final fURI vid) {
        this.vid = vid;
        ints.forEach(i -> {
            this.map.compute(i.tid(i.tid().coefficientless()), (lng, it) -> null == it ? i.tid().coefficientValue() : it.plus(i.tid().coefficientValue()));
        });
    }

    public static Objs objs(final Iterable<Obj> os) {
        return MObjs.of(os);
    }

    public static Objs objs(final Obj... objs) {
        return objs(List.of(objs));
    }

    @Override
    public Iterable<Obj> value() {
        return () -> (Iterator) this.map.entrySet().stream().map(kv -> kv.getKey().tid(kv.getKey().tid().coefficient(kv.getValue().toString()))).iterator();
    }

    @Override
    public fURI tid() {
        return this.map.entrySet().stream().map(kv -> kv.getKey().tid().coefficient(kv.getValue().toString())).reduce(fURI::plus).orElse(fURI.NONE.zero());
    }

    @Override
    public <O extends Obj> O remove() {
        return null;
    }

    @Override
    public Objs vid(final fURI vid) {
        return new MInts(this.value(), vid);
    }

    @Override
    public fURI vid() {
        return this.vid;
    }

    @Override
    public Type type() {
        return MType.of(this, this.tid());
    }

    @Override
    public MInts clone(Object value, fURI tid, fURI vid) {
        return this;
    }

    @Override
    public String toString() {
        return ObjUtil.objToString(this);
    }

    @Override
    public int hashCode() {
        return ObjUtil.objHashCode(this);
    }

    @Override
    public boolean equals(final Object other) {
        return ObjUtil.objEquals(this, other);
    }

    public static Obj ofUsage(final Object object) {
        if (null == object)
            return NoObj.single();
        if (object instanceof Stream)
            return ofUsage(((Stream) object).toList()); // TODO: strange....
        if (object instanceof List)
            return ObjUtil.oneNoneOrAll((List) object);
        if (object instanceof Obj)
            return (Obj) object;
        throw MTronException.of("unknown object type: %s", object);

    }
}
