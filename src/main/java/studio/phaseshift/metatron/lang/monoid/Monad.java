package studio.phaseshift.metatron.lang.monoid;

import org.javatuples.Triplet;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.Inst;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.lang.obj.Rec;
import studio.phaseshift.metatron.lang.obj.Type;
import studio.phaseshift.metatron.ui.Graphitty;

import java.util.Objects;

public interface Monad extends Obj {

    class Helpers {
        public static String monadToString(final Monad monad) {
            return Graphitty.string("{{b}}%s{{g}}::[%s{{g}}<--{{/g}}{{c}}M{{g}}-->{{c}}%s{{g}}]{{X}}", monad.tid(), monad.obj(), monad.inst());
        }

        public static int monadHashCode(final Monad monad) {
            return Objects.hash(monad.tid(), monad.vid(), monad.value());
        }

        public static boolean monadEquals(final Monad monad, final Object other) {
            return other instanceof Monad && Objects.equals(((Monad) other).tid(), monad.tid()) && Objects.equals(((Monad) other).vid(), monad.vid()) && Objects.equals(((Monad) other).value(), monad.value());
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

    @Override
    default Type dom() {
        return this.obj().rng();
    } // TODO: is this what we need?

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