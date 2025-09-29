package studio.phaseshift.metatron.lang.obj.mgrph;

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.Code;
import studio.phaseshift.metatron.lang.obj.Fluent;
import studio.phaseshift.metatron.lang.obj.Inst;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.lang.obj.mtron.MInstSet;
import studio.phaseshift.metatron.lang.obj.mtron.mtronFluent;
import studio.phaseshift.metatron.util.IteratorUtil;

import java.util.ArrayList;
import java.util.List;

import static studio.phaseshift.metatron.lang.obj.mtron.MInst.instB;
import static studio.phaseshift.metatron.lang.obj.mtron.MLst.lst;

public class mgrphFluent<F extends Fluent<F>> extends mtronFluent<F> implements Code, Fluent<F> {

    protected mgrphFluent() {
        this(new ArrayList<>(), MInstSet.CODE_TID, null);
    }

    protected mgrphFluent(final List<Inst> value, final fURI tid, final fURI vid) {
        super(value, tid, vid);
    }

    @Override
    public List<Inst> value() {
        return (List<Inst>) this.value;
    }

    private F addInst(final Inst inst) {
        this.codeValue().add(inst);
        return (F) this;
    }

    public F start(final Obj obj) {
        return this.addInst(instB(MInstSet.START_TID, lst(obj)));
    }

    public F g(final Obj obj) {
        return this.addInst(instB(GrphInstSet.G_TID, lst(obj)));
    }

    public F V() {
        return this.addInst(instB(GrphInstSet.V_TID, lst()));
    }

    public F out(final Obj... obj) {
        return this.addInst(instB(GrphInstSet.OUT_TID, lst(obj)));
    }

    public F outE(final Obj... obj) {
        return this.addInst(instB(GrphInstSet.OUTE_TID, lst(obj)));
    }


    public List<Obj> toList() {
        return IteratorUtil.list(this.iterator());
    }

    @Override
    public Code clone(Object value, fURI tid, fURI vid) {
        return new mgrphFluent<F>(new ArrayList<>(this.value()), this.tid, this.vid);
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
