package studio.phaseshift.metatron.lang.monoid;

import org.javatuples.Quartet;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.*;

public interface Monad extends Obj {

    @Override
    Monad clone(final Object value, final fURI tid, final fURI vid);


    @Override
    Quartet<Monoid, Obj, Inst, Rec> value();

    Monad halt();

    default boolean halted() {
        return this.inst().isNoObj();
    }

    default boolean dead() {
        return this.obj().isNoObj();
    }

    default Rec state() {
        return this.value().getValue3();
    }

    default Inst inst() {
        return this.value().getValue2();
    }

    default Obj obj() {
        return this.value().getValue1();
    }

    default Monad obj(final Obj obj) {
        return this.clone(Quartet.with(this.monoid(), obj, this.inst(), this.state()), this.tid(), this.vid());
    }

    default Monad inst(final Inst inst) {
        return this.clone(Quartet.with(this.monoid(), this.obj(), inst, this.state()), this.tid(), this.vid());
    }

    default Monoid monoid() {
        return this.value().getValue0();
    }

    default long bulk() {
        return this.state().value().get(fURI.of("bulk").toUri()).intValue();
    }

    @Override
    default Type dom() {
        return this.inst().dom();
    }

    @Override
    default Type rng() {
        return this.inst().rng();
    }

    //long loops();

    @Override
    Monad apply(final Obj inst);
    /*{
        return this.clone(Quartet.with(this.monoid(), inst.apply(this.obj()), inst, this.state()), this.tid(), this.vid());
    }*/
}