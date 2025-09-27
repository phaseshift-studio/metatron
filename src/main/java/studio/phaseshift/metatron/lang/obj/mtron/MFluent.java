package studio.phaseshift.metatron.lang.obj.mtron;

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.Code;
import studio.phaseshift.metatron.lang.obj.Inst;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.util.IteratorUtil;

import java.util.ArrayList;
import java.util.List;

import static studio.phaseshift.metatron.lang.obj.mtron.MInst.instB;
import static studio.phaseshift.metatron.lang.obj.mtron.MLst.lst;

public class MFluent extends MObj implements Code {

    protected MFluent() {
        this(new ArrayList<>(), MInstSet.CODE_TID, null);
    }

    protected MFluent(final List<Inst> value, final fURI tid, final fURI vid) {
        super(value, tid, vid);
    }

    @Override
    public List<Inst> value() {
        return (List<Inst>) this.value;
    }

    private MFluent addInst(final Inst inst) {
        this.codeValue().add(inst);
        return this;
    }

    public MFluent start(final Obj obj) {
        return this.addInst(instB(MInstSet.START_TID, lst(obj)));
    }

    public MFluent plus(final Obj obj) {
        return this.addInst(instB(MInstSet.PLUS_TID, lst(obj)));
    }

    public MFluent mult(final Obj obj) {
        return this.addInst(instB(MInstSet.MULT_TID, lst(obj)));
    }

    public MFluent id() {
        return this.addInst(instB(MInstSet.ID_TID, lst()));
    }

    public MFluent isA(final Obj obj) {
        return this.addInst(instB(MInstSet.ISA_TID, lst(obj)));
    }

    public MFluent in(final Obj obj) {
        return this.addInst(instB(MInstSet.IN_TID, lst(obj)));
    }

    public MFluent split(final Obj obj) {
        return this.addInst(instB(MInstSet.SPLIT_TID, lst(obj)));
    }

    public MFluent merge() {
        return this.addInst(instB(MInstSet.MERGE_TID, lst()));
    }

    public MFluent e1se(final Obj obj) {
        return this.addInst(instB(MInstSet.ELSE_TID, lst(obj)));
    }

    public MFluent from(final Obj obj) {
        return this.addInst(instB(MInstSet.FROM_TID, lst(obj)));
    }

    public List<Obj> toList() {
        return IteratorUtil.list(this.iterator());
    }

    @Override
    public Code clone(Object value, fURI tid, fURI vid) {
        return new MFluent(new ArrayList<>(this.value()), this.tid, this.vid);
    }

    /// /////////////////////////////////////////////////////////////

    public static class StartLess {

        public static MFluent start(final Obj obj) {
            return new MFluent().start(obj);
        }

        public static MFluent plus(final Obj obj) {
            return new MFluent().plus(obj);
        }

        public static MFluent mult(final Obj obj) {
            return new MFluent().mult(obj);
        }

        public static MFluent isA(final Obj obj) {
            return new MFluent().isA(obj);
        }

        public static MFluent in(final Obj obj) {
            return new MFluent().in(obj);
        }

        public static MFluent id() {
            return new MFluent().id();
        }

        public static MFluent split(final Obj obj) {
            return new MFluent().split(obj);
        }

        public static MFluent merge() {
            return new MFluent().merge();
        }

        public static MFluent e1se(final Obj obj) {
            return new MFluent().e1se(obj);
        }

        public static MFluent from(final Obj obj) {
            return new MFluent().from(obj);
        }
    }
}
