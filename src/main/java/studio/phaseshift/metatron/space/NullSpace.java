package studio.phaseshift.metatron.space;

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.*;

import java.util.Map;
import java.util.Set;

import static studio.phaseshift.metatron.lang.obj.mtron.MType.T;
import static studio.phaseshift.metatron.lang.obj.mtron.mtronInstSet.NOOBJ_TID;

public final class NullSpace implements Space, InstSet {

    private static final NullSpace INSTANCE = new NullSpace();

    public static <S extends Space> S single() {
        return (S) INSTANCE;
    }

    private NullSpace() {

    }

    @Override
    public Map value() {
        return Map.of();
    }

    @Override
    public fURI pattern() {
        return NOOBJ_TID.zero();
    }

    @Override
    public Set<Type> types() {
        return Set.of(T(NOOBJ_TID.zero()));
    }

    @Override
    public Set<Inst> insts() {
        return Set.of();
    }

    @Override
    public Set<Inst> rewrites() {
        return Set.of();
    }

    @Override
    public Obj read(final fURI vid) {
        return NoObj.single();
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        return obj;
    }

    @Override
    public void append(final fURI addr, final Obj... obj) {

    }

    @Override
    public fURI tid() {
        return NOOBJ_TID;
    }

    @Override
    public fURI vid() {
        return NOOBJ_TID;
    }

    @Override
    public NullSpace clone(final Object value, final fURI tid, final fURI vid) {
        return this;
    }

    @Override
    public String toString() {
        return Space.Helpers.spaceToString(this);
    }

    @Override
    public int hashCode() {
        return Space.Helpers.spaceHashCode(this);
    }

    @Override
    public boolean equals(final Object other) {
        return Space.Helpers.spaceEquals(this, other);
    }

    @Override
    public void close() {

    }
}
