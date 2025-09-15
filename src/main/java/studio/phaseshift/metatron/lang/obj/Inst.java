/*
 *   Metatron: A Distributed Virtual Machine
 *   Copyright (c) 2024 PhaseShift Studio, LLC
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.lang.obj;

import org.javatuples.Triplet;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.base.furi.TypefURI;
import studio.phaseshift.metatron.lang.obj.mtron.MRel;
import studio.phaseshift.metatron.lang.obj.mtron.MType;
import studio.phaseshift.metatron.lang.obj.mtron.MInstSet;
import studio.phaseshift.metatron.space.Router;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import static studio.phaseshift.metatron.lang.obj.mtron.MInstSet.LST_TID;

public interface Inst extends Obj {

    // /mtron/plus?dom=/mtron/int,rng=/mtron/int

    public enum Resolve {
        A("f"), B("f(a)"), C("f(a)->b");

        final String value;

        Resolve(final String value) {
            this.value = value;
        }

        public String value() {
            return this.value;
        }
    }

    @Override
    Inst clone(final Object value, final fURI tid, final fURI vid);

    @Override
    Triplet<Poly, f, Obj> value();

    /// ////////////////////////////////////////////////////////////
    /// ////////////////////////////////////////////////////////////

    @Override
    default Type dom() {
        return MType.of(Router.global().read(TypefURI.dom(this.tid())),TypefURI.dom(this.tid())).orElseGet(() -> MType.of(TypefURI.dom(this.tid())));
    }

    @Override
    default Type rng() {
        final fURI range = TypefURI.rng(this.tid());
        return range.equals(fURI.ANY) ? MType.of(range) : MType.of(Router.global().read(range),TypefURI.rng(this.tid())).orElseGet(() -> MType.of(TypefURI.rng(this.tid())));
    }

    default Poly args() {
        return this.value().getValue0();
    }

    default Obj arg(final int index) {
        return IteratorUtil.index(this.args().elements().iterator(), index, NoObj.single());
    }

    default Inst.f f() {
        return this.value().getValue1();
    }

    default Obj seed() {
        return this.value().getValue2();
    }

    default Rel signature() {
        final fURI domain = this.tid().queryValue(fURI.DOM,fURI.class,fURI.NULL);
        final fURI range = this.tid().queryValue(fURI.RNG,fURI.class,fURI.NULL);
        return MRel.of(domain.toUri(),range.toUri());
    }

    default Resolve resolution() {
        if (null == this.value() || null == this.f())
            return Resolve.A;
        for (Obj arg : this.args()) {
            if (!arg.tid().equals(arg.vid())) {
                return Resolve.B;
            }
        }
        return Resolve.C;
    }

    default Inst resolve(final Resolve desiredResolution, final Obj lhs) {
        final GraphittyLogger LOG = Graphitty.log(lhs);
        final Resolve currentResolution = this.resolution();
        if (currentResolution.compareTo(desiredResolution) == 0 ||
                currentResolution.compareTo(desiredResolution) > 0) {
            LOG.trace("resolution ({{m}}%s {{g}}<=>{{/g}} %s{{/m}}): %s", currentResolution, desiredResolution, lhs);
            return this;
        } else {
            if (currentResolution == Resolve.A) {
                final Inst resolved = new MInstSet().resolve(lhs, this);
                LOG.trace("resolution ({{m}}%s {{g}}=>{{/g}} %s{{/m}}): %s", currentResolution, resolved.resolution(), resolved);
                return resolved.resolve(desiredResolution, lhs);
            } else { // Resolve.B
                final boolean blocking = this.tid().equals(MInstSet.BLOCK_TID) || this.tid().equals(MInstSet.WITHIN_TID);
                if (!blocking && !lhs.matches(this.dom()))
                    throw MTronException.of("lhs obj does not match inst domain: %s: %s {{r}}-/>{{/r}} %s", this, lhs, this.dom());
                final List<Obj> cargs = new ArrayList<>();
                for (final Obj arg : args().elements()) {
                    cargs.add(blocking ? arg : arg.apply(lhs));
                }
                final Inst resolved = (Inst) this.value(Triplet.with(this.args().clone(cargs, LST_TID, fURI.NULL), this.f(), this.seed()));
                LOG.trace("resolution ({{m}}%s {{g}}=>{{/g}} %s{{/m}}): %s", currentResolution, resolved.resolution(), resolved);
                return resolved;
            }
        }
    }

    @Override
    default Obj apply(final Obj lhs) {
        final Inst cinst = this.resolve(Resolve.C, lhs);
        final Obj rhs = cinst.f().apply(lhs, cinst);
        if (!rhs.matches(cinst.rng()))
            throw MTronException.of("rhs obj does not match inst range: %s: %s {{r}}-/>{{/r}} %s",this, rhs, cinst.rng());
        return rhs;
    }

    final class f {
        public enum Form {
            ZERO_TO_ZERO,
            ZERO_TO_ONE,
            ZERO_TO_MANY,
            ONE_TO_ZERO,
            ONE_TO_ONE,
            ONE_TO_MANY,
            MANY_TO_ZERO,
            MANY_TO_ONE,
            MANY_TO_MANY;

            //  manys
            public boolean isGather() {
                return this == MANY_TO_MANY || this == MANY_TO_ONE || this == MANY_TO_ZERO;
            }

            public boolean isScatter() {
                return this == MANY_TO_MANY || this == ZERO_TO_MANY || this == ONE_TO_MANY;
            }


            // zeros
            public boolean isInitial() {
                return this == ZERO_TO_ONE || this == ZERO_TO_MANY || this == ZERO_TO_ZERO;
            }

            public boolean isTerminal() {
                return this == ONE_TO_ZERO || this == MANY_TO_ZERO || this == ZERO_TO_ZERO;
            }


            // ones
            public boolean isMapping() {
                return this == ONE_TO_ONE || this == ONE_TO_ZERO || this == ONE_TO_MANY;
            }

            public boolean isReducing() {
                return this == MANY_TO_ONE || this == ZERO_TO_ONE || this == ONE_TO_ONE;
            }

        }

        private final boolean bi;
        final Object func;
        final Form form = Form.ONE_TO_ONE;


        private f(final BiFunction<Obj, Inst, Obj> func) {
            this.bi = true;
            this.func = func;

        }

        private f(final Function<Obj, Obj> func) {
            this.bi = false;
            this.func = func;
        }

        public Form form() {
            return this.form;
        }

        public Obj apply(final Obj lhs, final Inst cinst) {
            return this.bi ?
                    ((BiFunction<Obj, Inst, Obj>) this.func).apply(lhs, cinst) :
                    ((Function<Obj, Obj>) this.func).apply(lhs);
        }

        public static f of(final BiFunction<Obj, Inst, Obj> func) {
            return null == func ? null : new f(func);
        }

        public static f of(final Function<Obj, Obj> func) {
            return null == func ? null : new f(func);
        }

        @Override
        public String toString() {
            return this.func instanceof Obj ? this.func.toString() : "<j>";
        }
    }
}