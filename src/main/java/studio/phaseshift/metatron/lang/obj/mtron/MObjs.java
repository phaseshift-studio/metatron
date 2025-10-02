package studio.phaseshift.metatron.lang.obj.mtron;

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.NoObj;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.lang.obj.Objs;
import studio.phaseshift.metatron.lang.obj.Type;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.ObjUtil;

import java.util.*;
import java.util.stream.Stream;

public class MObjs implements Objs {

    private fURI vid;
    private final Map<Obj, MCoeff.Int> map = new LinkedHashMap<>();

/*
 private static fURI computeTID(final Iterable<Obj> value) {
        Set<fURI> types = IteratorUtil.stream(value).map(Obj::tid).map(fURI::basePath).collect(Collectors.toSet());
        // TODO: make efficient
        final long minCount = IteratorUtil.stream(value).map(Obj::tid).map(f -> (f.coefficientValue().min() != null) ? f.coefficientValue().min() : 1).reduce(0L, Long::sum);
        final long maxCount = IteratorUtil.stream(value).map(Obj::tid).map(f -> (f.coefficientValue().max() != null) ? f.coefficientValue().max() : 1).reduce(0L, Long::sum);
        //final long count = IteratorUtil.count(this.value());
        if (types.isEmpty() || 0 == maxCount) return fURI.NONE.zero();
        if (types.size() == 1) return types.iterator().next().coefficient(MCoeff.Int.of(minCount,maxCount).toString());
        final fURI temp = types.stream().reduce(fURI::commonRoot).get();
        return temp.coefficient(MCoeff.Int.of(minCount,maxCount).toString());
    }
 */

    public MObjs(final Iterable<Obj> ints, final fURI vid) {
        this.vid = vid;
        ints.forEach(i -> {
            this.map.compute(i.tid(i.tid().coefficientless()), (lng, it) -> null == it ? i.tid().coefficientValue() : it.plus(i.tid().coefficientValue()));
        });
    }

    @Override
    public Objs append(final Obj obj) {
        obj.iterator().forEachRemaining(i -> {
            this.map.compute(i.tid(i.tid().coefficientless()), (lng, it) -> null == it ? i.tid().coefficientValue() : it.plus(i.tid().coefficientValue()));
        });
        return this;
    }

    @Override
    public <O extends Obj> O remove() {
        if (this.map.keySet().iterator().hasNext()) {
            final O key = (O) this.map.keySet().iterator().next();
            final MCoeff.Int value = this.map.remove(key);
            return null == value ? key : (O) key.tid(key.tid().coefficient(value.toString()));
        }
        return null;
    }

    public static Objs of(final Iterable<Obj> objs) {
        return objs(objs);
    }

    public static Objs of(final Obj... objs) {
        return objs(objs);
    }

    public static <O extends Obj> Objs objs(final Iterable<O> os) {
        return new MObjs((Iterable) os, null);
    }

    public static Objs objs(final Obj... objs) {
        return objs(List.of(objs));
    }

    @Override
    public Iterable<Obj> value() {
        return () -> (Iterator) this.map.entrySet().stream().map(kv -> kv.getValue().isZero() ? NoObj.single() : (kv.getValue().isOne() ? kv.getKey() : kv.getKey().tid(kv.getKey().tid().coefficient(kv.getValue().toString())))).iterator();
    }

    @Override
    public fURI tid() {
        return this.map.entrySet().stream().map(kv -> kv.getKey().tid().coefficient(kv.getValue().toString())).reduce(fURI::plus).orElse(fURI.NONE.zero());
    }

    @Override
    public Objs vid(final fURI vid) {
        return new MObjs(this.value(), vid);
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
    public Objs clone(final Object value, final fURI tid, final fURI vid) {
        return new MObjs(IteratorUtil.stream((Iterable) value).toList(), vid);
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
        return other instanceof MObjs && Objects.equals(this.vid, ((MObjs) other).vid) && Objects.equals(this.map, ((MObjs) other).map);
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
