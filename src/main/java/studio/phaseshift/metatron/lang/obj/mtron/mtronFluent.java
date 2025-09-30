package studio.phaseshift.metatron.lang.obj.mtron;

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.Fluent;
import studio.phaseshift.metatron.lang.obj.Inst;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.util.IteratorUtil;

import java.util.ArrayList;
import java.util.List;

import static studio.phaseshift.metatron.lang.obj.mtron.MInst.instB;
import static studio.phaseshift.metatron.lang.obj.mtron.MLst.lst;

public class mtronFluent<F extends Fluent<F>> extends MCode implements Fluent<F> {

    protected mtronFluent() {
        this(new ArrayList<>(), mtronInstSet.CODE_TID, null);
    }

    protected mtronFluent(final List<Inst> value, final fURI tid, final fURI vid) {
        super(value, tid, vid);
    }

    protected F addInst(final Inst inst) {
        this.codeValue().add(inst);
        return (F) this;
    }

    public F start(final Obj obj) {
        return this.addInst(instB(mtronInstSet.START_TID, lst(obj)));
    }

    public F plus(final Obj obj) {
        return this.addInst(instB(mtronInstSet.PLUS_TID, lst(obj)));
    }

    public F mult(final Obj obj) {
        return this.addInst(instB(mtronInstSet.MULT_TID, lst(obj)));
    }

    public F id() {
        return this.addInst(instB(mtronInstSet.ID_TID, lst()));
    }

    public F isA(final Obj obj) {
        return this.addInst(instB(mtronInstSet.ISA_TID, lst(obj)));
    }

    public F in(final Obj obj) {
        return this.addInst(instB(mtronInstSet.IN_TID, lst(obj)));
    }

    public F split(final Obj obj) {
        return this.addInst(instB(mtronInstSet.SPLIT_TID, lst(obj)));
    }

    public F merge() {
        return this.addInst(instB(mtronInstSet.MERGE_TID, lst()));
    }

    public F e1se(final Obj obj) {
        return this.addInst(instB(mtronInstSet.ELSE_TID, lst(obj)));
    }

    public F from(final Obj obj) {
        return this.addInst(instB(mtronInstSet.FROM_TID, lst(obj)));
    }

    public F count() {
        return this.addInst(instB(mtronInstSet.COUNT_TID, lst()));
    }

    public F sum() {
        return this.addInst(instB(mtronInstSet.SUM_TID, lst()));
    }


    public List<Obj> toList() {
        return IteratorUtil.list(this.iterator());
    }

    @Override
    public mtronFluent<F> clone(Object value, fURI tid, fURI vid) {
        return new mtronFluent<>(new ArrayList<>(this.value()), this.tid, this.vid);
    }

    /// /////////////////////////////////////////////////////////////

    public static class StartLess {

        public static <F extends mtronFluent<F>> F start(final Obj obj) {
            return new mtronFluent<F>().start(obj);
        }

        public static <F extends mtronFluent<F>> F plus(final Obj obj) {
            return new mtronFluent<F>().plus(obj);
        }

        public static <F extends mtronFluent<F>> F mult(final Obj obj) {
            return new mtronFluent<F>().mult(obj);
        }

        public static <F extends mtronFluent<F>> F isA(final Obj obj) {
            return new mtronFluent<F>().isA(obj);
        }

        public static <F extends mtronFluent<F>> F in(final Obj obj) {
            return new mtronFluent<F>().in(obj);
        }

        public static <F extends mtronFluent<F>> F id() {
            return new mtronFluent<F>().id();
        }

        public static <F extends mtronFluent<F>> F split(final Obj obj) {
            return new mtronFluent<F>().split(obj);
        }

        public static <F extends mtronFluent<F>> F merge() {
            return new mtronFluent<F>().merge();
        }

        public static <F extends mtronFluent<F>> F e1se(final Obj obj) {
            return new mtronFluent<F>().e1se(obj);
        }

        public static <F extends mtronFluent<F>> F from(final Obj obj) {
            return new mtronFluent<F>().from(obj);
        }

        public static <F extends mtronFluent<F>> F count() {
            return new mtronFluent<F>().count();
        }

        public static <F extends mtronFluent<F>> F sum() {
            return new mtronFluent<F>().sum();
        }
    }
}
