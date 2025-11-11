package studio.phaseshift.metatron.lang.core.m.type.facade;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.Rec;

import java.util.Map;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class FRec extends FObj<Rec> implements Rec {

    public FRec(final Rec base) {
        super(base);

    }

    public static final FRec frec(final Rec rec) {
        return new FRec(rec);
    }

    @Override
    public Rec put(final Obj key, final Obj value) {
        return this.clone(this.base.put(key, value), this.tid(), this.vid());
    }

    @Override
    public Rec plus(final Rec rhs) {
        return this.clone(this.base.plus(rhs), this.tid(), this.vid());
    }

    @Override
    public Rec clone(Object jvm, fURI tid, fURI vid) {
        return super.clone(jvm, tid, vid);
    }

    @Override
    public Map<Obj, Obj> jvm() {
        return this.base.jvm();
    }
}
