package studio.phaseshift.metatron.lang.monoid;

import org.javatuples.Quartet;
import org.javatuples.Triplet;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.*;
import studio.phaseshift.metatron.space.Space;
import studio.phaseshift.metatron.ui.Graphitty;

import java.util.Objects;

public interface Monad extends Obj {

    class Helpers {
        public static String monadToString(final Monad monad) {
            return Graphitty.string("{{b}}" + monad.tid() + "{{g}}::[" + monad.obj() + "{{g}}<--{{/g}}{{c}}M{{g}}-->{{c}}" + monad.inst() + "{{g}}]{{X}}");
        }

        public static int monadHashCode(final Monad monad) {
            return Objects.hash(monad.tid(), monad.vid(), monad.value());
        }

        public static boolean monadEquals(final Monad monad, final Object other) {
            return other instanceof Monad && ((Monad) other).tid().equals(monad.tid()) && ((Monad) other).vid().equals(monad.vid()) && ((Monad)other).value().equals(monad.value());
        }
    }

    @Override
    Monad clone(final Object value, final fURI tid, final fURI vid);


    @Override
    Triplet<Obj, Inst, Rec> value();

    default boolean halted() {
        return this.inst().isNoObj();
    }

    default boolean dead() {
        return this.obj().isNoObj();
    }

    default boolean zombie() {
        return this.dead() && !this.halted();
    }

    default Rec state() {
        return this.value().getValue2();
    }

    default Inst inst() {
        return this.value().getValue1();
    }

    default Obj obj() {
        return this.value().getValue0();
    }

    default Monad obj(final Obj obj) {
        return this.clone(Triplet.with(obj, this.inst(), this.state()), this.tid(), this.vid());
    }

    default Monad inst(final Inst inst) {
        return this.clone(Triplet.with(this.obj(), inst, this.state()), this.tid(), this.vid());
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

    @Override
    default Monad apply(final Obj inst) {
        if (this.halted())
            return this;
        return this.obj(this.inst().apply(this.obj())).inst(inst.as());
    }

}