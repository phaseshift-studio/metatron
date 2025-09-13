package studio.phaseshift.metatron.lang.obj.mtron;

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.base.NoObj;
import studio.phaseshift.metatron.lang.obj.base.Obj;
import studio.phaseshift.metatron.lang.obj.base.Type;
import studio.phaseshift.metatron.util.MTronException;


public class MType extends MObj implements Type {
    public MType(final Obj value, final fURI tid) {
        super(value, tid, tid);
    }

    @Override
    public Type clone(final Object value, final fURI tid, final fURI vid) {
        if (!tid.equals(vid))
            throw MTronException.of("a tid and vid of a type must be the same: %s != %s", tid, vid);
        return new MType((Obj) value, tid);
    }

    @Override
    public Obj value() {
        return (Obj) this.value;
    }

    public static MType of(final Obj value, final fURI tid) {
        return new MType(value, tid);
    }

    public static MType of(final fURI tid) {
        return new MType(NoObj.single(), tid);
    }
}
