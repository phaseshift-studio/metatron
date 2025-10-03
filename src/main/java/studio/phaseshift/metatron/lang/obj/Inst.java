/*
 * Metatron: A Distributed Computing Language and Virtual Machine
 * Copyright (C) 2025- PhaseShift Studio, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

package studio.phaseshift.metatron.lang.obj;

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.mtron.MType;
import studio.phaseshift.metatron.lang.obj.mtron.mtronInstSet;
import studio.phaseshift.metatron.space.Router;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import static studio.phaseshift.metatron.lang.obj.mtron.MLst.lst;
import static studio.phaseshift.metatron.lang.obj.mtron.MRec.rec;
import static studio.phaseshift.metatron.util.Tuple.Triplet;

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
        // return MType.of(domain);
        return MType.of(Router.global().read(domain), domain);
    }

    @Override
    default Type rng() {
        final fURI range = this.tid().rng();
        return MType.of(Router.global().read(range), range);
        //return MType.of(range);
    }

    default Poly args() {
        return this.value().getValue0();
    }

    default Inst args(final Poly args) {
        return this.clone(Triplet.with(args, this.f(), this.seed()), this.tid(), this.vid());
    }

    default Obj arg(final int index) {
        return this.args().isLst() ?
                (this.args().lstValue().size() > index ? this.args().lstValue().get(index) : NoObj.single()) :
                IteratorUtil.index(this.args().elements().iterator(), index, NoObj.single());
    }

    default Obj arg(final fURI key) {
        return this.args().<Rec>as().at(key.toUri());
    }

    default Obj arg(final fURI key, final int index) {
        return this.args().isRec() ? this.arg(key) : this.arg(index);
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
       /* for (Obj arg : this.args()) {
            if (!arg.tid().equals(arg.vid())) { // TODO: this is not a fool proof way of determining if a resolution has happened
                return Resolve.B;
            }
        }*/
        return Resolve.B;
    }

    default boolean isBlocking() {
        return this.tid().basePath().equals(mtronInstSet.BLOCK_TID) || this.tid().basePath().equals(mtronInstSet.WITHIN_TID);
    }

    @Override
    default Inst resolve(final Obj lhs) {
        final GraphittyLogger LOG = Graphitty.log(lhs);
        final Resolve currentResolution = this.resolution();
        if (currentResolution == Resolve.A) {
            try {
                final Inst resolved = Router.global().read(this.tid())
                        .stream()
                        .map(Obj::<Inst>as)
                        .filter(i -> lhs.matches(i.dom()))
                        .map(i -> {
                            if (i.args().isLst()) {
                                LOG.trace("processing lst args of %s", i);
                                final AtomicInteger counter = new AtomicInteger(0);
                                return i.args().lstValue()
                                        .stream()
                                        .anyMatch(arg -> !this.arg(counter.getAndIncrement()).matches(arg)) ?
                                        null :
                                        i.args(lst(this.args().lstValue().stream().map(a -> a.isCall() ? a.resolve(lhs.type()) : a).toList()));
                            } else if (i.args().isRec()) {
                                LOG.trace("processing rec args of %s", i);
                                final AtomicInteger counter = new AtomicInteger(0);
                                return i.args(rec(i.args().recValue().entrySet()
                                        .stream()
                                        .map(kv -> {
                                            // return List.of(kv.getKey(), kv.getValue().resolve(this.arg(kv.getKey().uriValue(), counter.getAndIncrement())));
                                            Obj this_arg = this.arg(kv.getKey().uriValue(), counter.getAndIncrement());
                                            return List.of(kv.getKey(), kv.getValue().isCall() ? kv.getValue().apply(this_arg) : this_arg);
                                        })
                                        .collect(Collectors.toMap(kv -> kv.get(0), kv -> kv.get(1), (a, b) -> b, LinkedHashMap::new))));
                            } else
                                throw MTronException.of("inst args must be a lst or rec: %s", i);
                        })
                        .filter(i -> !Objects.isNull(i))
                        //.map(i -> i.tid(i.tid().dom(lhs.tid())).vid(this.vid()))
                        .map(Obj::<Inst>as)
                        .findFirst()
                        .orElseThrow(() -> this.logger().except("unable to resolve %s => %s in instruction set %s", lhs, this));
                LOG.trace("resolution ({{m}}%s {{g}}=>{{/g}} %s{{/m}}): %s => %s", currentResolution, resolved.resolution(), lhs, resolved);
                return (this.tid().hasDom() || this.tid().hasRng()) ? resolved.tid(this.tid()).resolve(lhs) : resolved.resolve(lhs);
            } catch (Exception e) { // TODO: this is sloppy -- using exception handling for flow control
                final Obj resolved = Router.global().read(this.tid());
                if (resolved.isNoObj()) {
                    LOG.error("unresolved %s across all known spaces", this);
                    return NoObj.single();
                } else if (!resolved.isInst()) {
                    LOG.error("unable to resolve %s to a single inst", resolved);
                    return NoObj.single();//.resolve(lhs);
                } else {
                    LOG.warn("resolved %s from global router", resolved);
                    return resolved.<Inst>as().args(this.args()); //.resolve(lhs);
                }
            }
        } else { // Resolve.B
            final boolean blocking = this.isBlocking();
            if (!blocking && !lhs.matches(this.dom()))
                throw MTronException.of("lhs obj does not match inst domain: %s: %s {{r}}-/>{{/r}} %s", this, lhs, this.dom());
            final Poly cargs = this.args().isLst() ?
                    lst(this.args().lstValue()
                            .stream()
                            .map(arg -> {
                                if (blocking)
                                    return arg;
                                else {
                                    final Obj r = arg.apply(lhs);
                                    if (!arg.isCall() && !r.matches(arg)) {
                                        LOG.error("unmatched inst arg in %s: %s ({{y}}lhs{{/y}}) {{g}}=>{{/g}} %s ({{y}}arg{{/y}}) {{r}}~!>{{/r}} %s ", this, lhs, arg, r);
                                        return arg;
                                    }
                                    //throw MTronException.of("arg obj does not match inst arg: %s: %s {{r}}-/>{{/r}} %s", this, arg, r);
                                    return r;
                                }
                            }).toList()) :
                    rec(this.args().recValue().entrySet()
                            .stream()
                            .map(kv -> List.of(kv.getKey(), blocking ?
                                    kv.getValue() :
                                    kv.getValue().apply(lhs)))
                            .collect(Collectors.toMap(kv -> kv.get(0), kv -> kv.get(1), (a, b) -> b, LinkedHashMap::new)));
            final Inst resolved = this.args(cargs);
            LOG.trace("resolution ({{m}}%s {{g}}=>{{/g}} %s{{/m}}): %s", currentResolution, resolved.resolution(), resolved);
            return resolved;
        }
    }

    @Override
    default Obj apply(final Obj lhs) {
        final Inst cinst = this.resolve(lhs);
        if (!this.isBlocking() && !lhs.matches(cinst.dom()))
            throw MTronException.of("{{m}}lhs obj{{/m}} (%s) does not match {{m}}inst domain{{/m}} (%s): %s", lhs, cinst.dom(), this);
        Router.stack().push(cinst.args());
        Obj rhs = NoObj.single();
        try {
            rhs = cinst.f().apply(lhs, cinst);
            Router.stack().pop();
        } catch (final Exception e) {
            Graphitty.log(this).error("%s => %s evaluation error: %s (reverting stack)", lhs, cinst, e.getMessage());
            Router.stack().pop();
        }
        if (!rhs.matches(cinst.rng()))
            throw MTronException.of("{{m}}rhs obj{{/m}} (%s) does not match {{m}}inst range{{/m}} (%s): %s", rhs, cinst.rng(), this);
        return rhs;

    }

    default boolean isGather() {
        return this.dom().tid().cV().min() > 1 || this.dom().tid().cV().max() == null;
    }

    default boolean isScatter() {
        return this.rng().tid().cV().isOne();
    }

    default boolean isInitial() {
        return this.dom().tid().cV().isZero();// || this.dom().tid().coefficientValue().isQuestion();
    }

    default boolean isTerminal() {
        return this.rng().tid().cV().isZero();
    }

    @Override
    default Inst tid(final fURI newTid) {
        return this.clone(this.value(), newTid, this.vid());
    }


    final class f implements BiFunction<Obj, Inst, Obj> {
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