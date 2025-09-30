package studio.phaseshift.metatron.lang.obj.mgrph;

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.Call;
import studio.phaseshift.metatron.lang.obj.Fluent;
import studio.phaseshift.metatron.lang.obj.Inst;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.lang.obj.mtron.mtronFluent;
import studio.phaseshift.metatron.lang.obj.mtron.mtronInstSet;

import java.util.ArrayList;
import java.util.List;

import static studio.phaseshift.metatron.lang.obj.mtron.MInst.instB;
import static studio.phaseshift.metatron.lang.obj.mtron.MLst.lst;

public class mgrphFluent<F extends Fluent<F>> extends mtronFluent<F> {

    protected mgrphFluent() {
        this(new ArrayList<>(), mtronInstSet.CODE_TID, null);
    }

    protected mgrphFluent(final List<Inst> value, final fURI tid, final fURI vid) {
        super(value, tid, vid);
    }

    public F start(final Obj obj) {
        return this.addInst(instB(mtronInstSet.START_TID, lst(obj)));
    }

    public F g(final MGraph graph) {
        return this.addInst(instB(mgrphInstSet.G_TID, lst(graph)));
    }

    public F g(final Call call) {
        return this.addInst(instB(mgrphInstSet.G_TID, lst(call)));
    }

    public F V() {
        return this.addInst(instB(mgrphInstSet.V_TID, lst()));
    }

    public F out(final Obj... obj) {
        return this.addInst(instB(mgrphInstSet.OUT_TID, lst(obj)));
    }

    public F outE(final Obj... obj) {
        return this.addInst(instB(mgrphInstSet.OUTE_TID, lst(obj)));
    }

    @Override
    public mgrphFluent<F> clone(Object value, fURI tid, fURI vid) {
        return new mgrphFluent<>(new ArrayList<>(this.value()), this.tid, this.vid);
    }

    /// /////////////////////////////////////////////////////////////

    public static class StartLess {

        public static <F extends mgrphFluent<F>> F g(final MGraph obj) {
            return (F) new mgrphFluent<F>().start(obj);
        }

        public static <F extends mgrphFluent<F>> F out(final Obj obj) {
            return new mgrphFluent<F>().out(obj);
        }

        public static <F extends mgrphFluent<F>> F outE(final Obj obj) {
            return new mgrphFluent<F>().outE(obj);
        }


    }
}
