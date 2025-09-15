package studio.phaseshift.metatron.lang.monoid.mtron;

import org.apache.commons.collections.IteratorUtils;
import org.javatuples.Quartet;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.monoid.Monad;
import studio.phaseshift.metatron.lang.monoid.Monoid;
import studio.phaseshift.metatron.lang.obj.Inst;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.lang.obj.Rec;
import studio.phaseshift.metatron.lang.obj.mtron.MObj;
import studio.phaseshift.metatron.lang.obj.mtron.MObjs;
import studio.phaseshift.metatron.lang.obj.mtron.MRec;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;
import studio.phaseshift.metatron.util.IteratorUtil;

import java.util.LinkedList;
import java.util.List;

import static studio.phaseshift.metatron.lang.obj.mtron.MInstSet.COUNT_TID;
import static studio.phaseshift.metatron.lang.obj.mtron.MInstSet.START_TID;

// monoid, obj, inst, state
public class MMonad extends MObj implements Monad {

    private static final GraphittyLogger LOG = Graphitty.log(MMonad.class);

    public MMonad(final Quartet<Monoid, Obj, Inst, Rec> value, final fURI tid, final fURI vid) {
        super(value, tid, vid);
    }

    @Override
    public Quartet<Monoid, Obj, Inst, Rec> value() {
        return (Quartet<Monoid, Obj, Inst, Rec>) this.value;
    }

    @Override
    public Monad halt() {
       this.monoid().halted().<LinkedList<Obj>>valueAs().push(this.obj());
        return this;
    }

    @Override
    public Monad clone(final Object value, final fURI tid, final fURI vid) {
        return new MMonad((Quartet<Monoid, Obj, Inst, Rec>) value, tid, vid);
    }

    @Override
    public Monad apply(final Obj nextInst) {
        if(this.halted())
           return this;
       return this.obj(this.inst().apply(this.obj())).inst(nextInst.as());
    }

    public boolean equals(final Object other) {
        return other instanceof MMonad &&
                this.obj().equals(((MMonad) other).obj()) &&
                this.inst().equals(((MMonad) other).inst());
    }

    public String toString() {
        return Graphitty.string("{{b}}M{{g}}[%s{{g}}]{{r}}@%s{{X}}".formatted(this.obj(), this.inst()));
    }

    public static Monad of(final Monoid monoid, final Obj obj, final Inst inst) {
        return new MMonad(Quartet.with(monoid, obj, inst, MRec.of()), fURI.of("monad:abc"), fURI.NULL);
    }

    public static Monad of(final Monoid monoid, final Obj obj) {
        return new MMonad(Quartet.with(monoid, obj, monoid.code().inst(0), MRec.of()), fURI.of("monad:abc"), fURI.NULL);
    }
}