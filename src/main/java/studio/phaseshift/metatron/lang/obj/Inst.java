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
import studio.phaseshift.metatron.lang.obj.mtron.MInstSet;
import studio.phaseshift.metatron.lang.obj.mtron.MLst;
import studio.phaseshift.metatron.lang.obj.mtron.MRec;
import studio.phaseshift.metatron.lang.obj.mtron.MType;
import studio.phaseshift.metatron.space.Router;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

public interface Inst extends Call {

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
        final fURI domain = this.tid().dom();
        return MType.of(Router.global().read(domain), domain).orElseGet(() -> MType.of(domain));
    }

    @Override
    default Type rng() {
        final fURI range = this.tid().rng();
        return MType.of(Router.global().read(range), range).orElseGet(() -> MType.of(range));
        // return range.equals(fURI.ANY) ? MType.of(range) : MType.of(Router.global().read(range),range).orElseGet(() -> MType.of(range));
    }

    default Poly args() {
        return this.value().getValue0();
    }

    default Inst args(final Poly args) {
        return this.clone(Triplet.with(args, this.f(), this.seed()), this.tid(), this.vid());
    }

    default Obj arg(final int index) {
        return IteratorUtil.index(this.args().elements().iterator(), index, NoObj.single());
    }


    default Obj arg(final fURI key) {
        return this.args().<Rec>as().at(key.toUri());
    }

    default Inst.f f() {
        return this.value().getValue1();
    }

    default Obj seed() {
        return this.value().getValue2();
    }

    default Resolve resolution() {
        if (null == this.value() || null == this.f())
            return Resolve.A;
        for (Obj arg : this.args()) {
            if (!arg.tid().equals(arg.vid())) { // TODO: this is not a fool proof way of determining is a resolution has happened
                return Resolve.B;
            }
        }
        return Resolve.C;
    }

    default Inst resolve(final Resolve desiredResolution, final Obj lhs) {
        final GraphittyLogger LOG = Graphitty.log(lhs);
        final Resolve currentResolution = this.resolution();
       /* if (false &&(currentResolution.compareTo(desiredResolution) == 0 ||
                currentResolution.compareTo(desiredResolution) > 0)) {
            LOG.trace("resolution ({{m}}%s {{g}}<=>{{/g}} %s{{/m}}): %s", currentResolution, desiredResolution, lhs);
            return this;
        } else {*/
            if (currentResolution == Resolve.A) {
                //final Inst resolved = new MInstSet(fURI.of("/mnt/mtron")).resolve(lhs, this);
                final Inst resolved = Router.global().<InstSet>getSpace(fURI.of("/mtron/#")).resolve(lhs, this); // TODO: generalize for any instruction set
                LOG.trace("resolution ({{m}}%s {{g}}=>{{/g}} %s{{/m}}): %s => %s", currentResolution, resolved.resolution(), lhs, resolved);
                return resolved.resolution().equals(desiredResolution) || resolved.resolution().compareTo(desiredResolution) >= 0 ? resolved : resolved.resolve(desiredResolution, lhs);
            } else { // Resolve.B
                final boolean blocking = this.tid().basePath().equals(MInstSet.BLOCK_TID) || this.tid().basePath().equals(MInstSet.WITHIN_TID);
                if (!blocking && !lhs.matches(this.dom()))
                    throw MTronException.of("lhs obj does not match inst domain: %s: %s {{r}}-/>{{/r}} %s", this, lhs, this.dom());
                final Poly cargs = this.args().isLst() ?
                        MLst.of(this.args().lstValue().stream().map(arg -> blocking ? arg : arg.apply(lhs)).toList()) :
                        MRec.of(this.args().recValue().entrySet().stream().map(kv -> List.of(kv.getKey(), blocking ? kv.getValue() : kv.getValue().apply(lhs))).collect(Collectors.toMap(kv -> kv.get(0), kv -> kv.get(1))));
                final Inst resolved = this.value(Triplet.with(cargs, this.f(), this.seed()));
                LOG.trace("resolution ({{m}}%s {{g}}=>{{/g}} %s{{/m}}): %s", currentResolution, resolved.resolution(), resolved);
                return resolved;
            }
     //   }
    }

    @Override
    default Obj apply(final Obj lhs) {
        final Inst cinst = this.resolve(Resolve.C, lhs);
        Router.stack().push(cinst.args());
        if(!lhs.matches(cinst.dom()))
            throw MTronException.of("{{m}}lhs obj{{/m}} (%s) does not match {{m}}inst domain{{/m}} (%s): %s", lhs, cinst.dom(), this);
        final Obj rhs = cinst.f().apply(lhs, cinst);
        Router.stack().pop();
        if (!rhs.matches(cinst.rng()))
            throw MTronException.of("{{m}}rhs obj{{/m}} (%s) does not match {{m}}inst range{{/m}} (%s): %s", rhs, cinst.rng(), this);
        return rhs;
    }

    default boolean isGather() {
        return this.dom().tid().coefficientValue().min() > 1 || this.dom().tid().coefficientValue().max() == null;
    }

    default boolean isScatter() {
        return this.rng().tid().coefficientValue().isOne();
    }

    default boolean isInitial() {
        return this.dom().tid().coefficientValue().isZero();// || this.dom().tid().coefficientValue().isQuestion();
    }

    default boolean isTerminal() {
        return this.rng().tid().coefficientValue().isZero();
    }


    final class f {
        public static f UNKNOWN = null;

        private final boolean bi;
        final Object func;


        private f(final BiFunction<Obj, Inst, Obj> func) {
            this.bi = true;
            this.func = func;

        }

        private f(final Function<Obj, Obj> func) {
            this.bi = false;
            this.func = func;
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