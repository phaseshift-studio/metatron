package studio.phaseshift.metatron.lang.core.m.type.facade;

import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.type.Lst;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.Objs;

import java.util.List;
import java.util.function.Function;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class FObjs extends FObj<Objs> implements Objs {

    public FObjs(final Objs base) {
        super(base);
    }

    @Override
    public Objs clone(final Object jvm, final fURI tid, final fURI vid) {
        return super.clone(jvm, tid, vid);
    }

    @Override
    public Iterable<Obj> jvm() {
        return this.base.jvm();
    }

    @Override
    public Obj append(final Obj obj) {
        return this.jvm(this.base.append(obj));
    }

    @Override
    public cInt uniqueC() {
        return this.base.uniqueC();
    }

    @Override
    public Obj c(final Function<cInt, cInt> func) {
        return this.jvm(this.base.c(func));
    }

    @Override
    public cInt c() {
        return this.base.c();
    }
}