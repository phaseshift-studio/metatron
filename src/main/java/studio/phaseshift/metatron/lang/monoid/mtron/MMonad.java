package studio.phaseshift.metatron.lang.monoid.mtron;

import org.javatuples.Triplet;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.monoid.Monad;
import studio.phaseshift.metatron.lang.obj.Inst;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.lang.obj.Rec;
import studio.phaseshift.metatron.lang.obj.mtron.MObj;
import studio.phaseshift.metatron.lang.obj.mtron.MRec;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;

import java.util.Objects;

import static studio.phaseshift.metatron.lang.obj.MInstSet.MTRON_TID;

// monoid, obj, inst, state
public class MMonad extends MObj implements Monad {

    public static final fURI MMONAD_TID = MTRON_TID.extend("lang/monad");

    private final GraphittyLogger LOG = Graphitty.log(this);

    public MMonad(final Triplet<Obj, Inst, Rec> value, final fURI tid, final fURI vid) {
        super(value, tid, vid);
    }

    @Override
    public Triplet<Obj, Inst, Rec> value() {
        return (Triplet<Obj, Inst, Rec>) this.value;
    }

    @Override
    public Monad tid(final fURI tid) {
        return (Monad) super.tid(tid);
    }

    @Override
    public Monad vid(final fURI vid) {
        return (Monad) super.vid(vid);
    }

    public Obj plus(final Monad other) {
        return this.tid(this.tid.plus(other.tid()));
    }

    @Override
    public Monad clone(final Object value, final fURI tid, final fURI vid) {
        return new MMonad((Triplet<Obj, Inst, Rec>) value, tid, vid);
    }

    @Override
    public boolean equals(final Object other) {
        return Monad.Helpers.monadEquals(this, other);
    }

    @Override
    public int hashCode() {
        return Monad.Helpers.monadHashCode(this);
    }

    @Override
    public String toString() {
        return Monad.Helpers.monadToString(this);
    }

    public static Monad of(final Obj obj, final Inst inst) {
        return new MMonad(Triplet.with(obj, inst, MRec.EMPTY_REC), MMONAD_TID, fURI.NULL);
    }
}