package studio.phaseshift.metatron.lang.core.m.type.facade;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.type.Lst;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.Rec;

import java.util.List;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class FLst extends FObj<Lst> implements Lst {

    public FLst(final Lst base) {
        super(base);
    }

    @Override
    public Lst clone(final Object jvm, final fURI tid, final fURI vid) {
        return super.clone(jvm, tid, vid);
    }

    @Override
    public List<Obj> jvm() {
        return this.base.jvm();
    }
}