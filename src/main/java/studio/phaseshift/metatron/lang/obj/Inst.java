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
 */

package studio.phaseshift.metatron.lang.obj;

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.mtron.MType;
import studio.phaseshift.metatron.lang.obj.mtron.c.cInt;
import studio.phaseshift.metatron.lang.obj.mtron.mtronInstSet;
import studio.phaseshift.metatron.space.Router;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import static studio.phaseshift.metatron.lang.obj.mtron.MLst.lst;
import static studio.phaseshift.metatron.lang.obj.mtron.MRec.rec;
import static studio.phaseshift.metatron.lang.obj.mtron.MType.T;
import static studio.phaseshift.metatron.lang.obj.mtron.mtronInstSet.FROM_TID;
import static studio.phaseshift.metatron.lang.obj.mtron.mtronInstSet.SPLIT_TID;
import static studio.phaseshift.metatron.util.Tuple.Triplet;

public interface Inst extends Call {

    // /mtron/plus?dom=/mtron/int,rng=/mtron/int

    private static Poly resolveArgs(final Inst source, final Inst target, final Obj lhs) {
        final GraphittyLogger LOG = Graphitty.log(source);
        if (target.args().isLst()) {
            LOG.trace("processing lst args of %s", target);
            List<Obj> resolvedArgs = new ArrayList<>();
            for (int i = 0; i < source.args().count(); i++) {
                final Obj sObj = source.arg(i);
                final Obj tObj = target.arg(i);
                if (sObj.isCall()) {
                    final Inst firstInst = sObj.<Call>as().insts().get(0);
                    if (!firstInst.hasDomOrRng() && firstInst.tid().basePath().equals(FROM_TID)) { // from() is a side-effect and the type can't be known unless explcitly specified (need a way to denote side-effect insts).
                        resolvedArgs.add(sObj.resolve(lhs));
                    } else {
                        // TODO: is this necessary and if so, do the same for lst
                        if (source.tid().name().equals(SPLIT_TID.name()) && sObj.isRec()) {
                            Rec sRecObj = rec(sObj.recValue().entrySet()
                                    .stream()
                                    .map(kv2 -> List.of(kv2.getKey().resolve(lhs), kv2.getValue().resolve(lhs)))
                                    .collect(Collectors.toMap(kv2 -> kv2.get(0), kv2 -> kv2.get(1), Obj::append, LinkedHashMap::new)));
                            final Obj r = sRecObj.resolve(lhs);
                            if (r.rng().matches(tObj))
                                resolvedArgs.add(r);
                            else return null;
                        }
                        final Obj r = sObj.resolve(lhs);
                        if (r.rng().matches(tObj))
                            resolvedArgs.add(r);
                        else return null;
                    }
                } else {
                    if (!sObj.matches(tObj))
                        return null;
                    resolvedArgs.add(sObj.resolve(lhs));
                }
            }
            return lst(resolvedArgs);
        } else if (target.args().isRec()) {
            LOG.trace("processing rec args of %s", target);
            final AtomicInteger counter = new AtomicInteger(0);
            return rec(target.args().recValue().entrySet()
                    .stream()
                    .map(kv -> {
                        // return List.of(kv.getKey(), kv.getValue().resolve(this.arg(kv.getKey().uriValue(), counter.getAndIncrement())));
                        Obj this_arg = source.arg(kv.getKey().uriValue(), counter.getAndIncrement());
                        /*if (source.tid().basePath().equals(SPLIT_TID) && this_arg.isRec()) {
                            final Rec this_rec_arg = rec(this_arg.recValue().entrySet()
                                    .stream()
                                    .map(kv2 -> List.of(kv2.getKey().resolve(lhs), kv2.getValue().resolve(lhs)))
                                    .collect(Collectors.toMap(kv2 -> kv2.get(0), kv2 -> kv2.get(1), Obj::append, LinkedHashMap::new)));
                            return List.of(kv.getKey(), kv.getValue().isCall() ? kv.getValue().apply(this_rec_arg) : this_rec_arg);
                        } else*/
                        return List.of(kv.getKey(), kv.getValue().isCall() ? kv.getValue().apply(this_arg) : this_arg);
                    })
                    .collect(Collectors.toMap(kv -> kv.get(0), kv -> kv.get(1), Obj::append, LinkedHashMap::new)));
        } else
            throw MTronException.of("inst args must be a lst or rec: %s", target);
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
        return MType.of(domain);
    }

    @Override
    default Type rng() {
        final fURI range = this.tid().rng();
        return MType.of(range);
        //return MType.of(range);
    }

    default Poly args() {
        return this.value().get0();
    }

    default Inst args(final Function<Poly, Poly> redefine) {
        return this.args(redefine.apply(this.args()));
    }


    default Inst args(final Poly args) {
        return this.clone(Triplet.with(args, this.f(), this.seed()), this.tid(), this.vid());
    }

    default Obj arg(final int index) {
        return this.args().isLst() ?
                (this.args().lstValue().size() > index ? this.args().lstValue().get(index) : NoObj.single()) :
                IteratorUtil.index(this.args().elements().iterator(), index, NoObj.single());
    }

    @Override
    default Inst c(final cInt c) {
        return this.tid(this.tid().c(c.toString()));
    }

    default Obj arg(final fURI key) {
        return this.args().<Rec>as().at(key.toUri());
    }

    default Obj arg(final fURI key, final int index) {
        return this.args().isRec() ? this.arg(key) : this.arg(index);
    }

    default Inst.f f() {
        return this.value().get1();
    }

    default Obj seed() {
        return this.value().get2();
    }

    default Resolution resolution() {
        return null == this.value() || null == this.f() || this.tid().isGeneric() ? Resolution.A : Resolution.B;
    }

    default boolean isBlocking() {
        return this.tid().basePath().equals(mtronInstSet.BLOCK_TID) ||
                this.tid().basePath().equals(mtronInstSet.WITHIN_TID) ||
                this.tid().basePath().equals(mtronInstSet.ISA_TID) ||
                this.tid().basePath().equals(mtronInstSet.CROSS_TID);
    }

    default Inst specify(final Obj lhs, final Obj spec) {
        final GraphittyLogger LOG = Graphitty.log(lhs);
        final Map<fURI, fURI> generics = new HashMap<>();
        Inst def = this;
        if (def.dom().tid().cLess().isGeneric() && lhs.type().c().within(def.dom().c())) {
            generics.put(def.dom().tid().cLess(), lhs.type().tid().cLess());
            def = def.dom(lhs.type().c(def.dom().c()).as());
        }
        if (def.rng().tid().isGeneric() && generics.containsKey(def.rng().tid().cLess())) {
            def = def.rng(T(generics.get(def.rng().tid().cLess()).c(def.rng().c().toString())));
        }
        if (!this.args().isEmpty() && this.args().isLst()) {
            final List<Obj> newArgs = new ArrayList<>();
            for (int i = 0; i < this.args().count(); i++) {
                Obj argD = this.arg(i);
                Obj argS = spec.isInst() ? spec.<Inst>as().arg(i) : spec;
                if (argD.tid().cLess().isGeneric()) {
                    final fURI lastBinding = generics.get(argD.tid().cLess());
                    if (null != lastBinding && !argS.tid().cLess().matches(lastBinding))
                        LOG.debug("existing generic doesn't match current usage: [{{m}}generic{{/m}}] %s [{{m}}past{{/m}}] %s [{{m}}present{{/m}}] %s", argS.tid(), lastBinding, argD.tid());
                    generics.computeIfAbsent(argD.tid().cLess(), k -> argS.tid().cLess()); // beware of int[0] yielding noobj across all bindings
                }
                if (argD.isInst()) {
                    argD = argD.<Inst>as().specify(lhs, argS);
                } else if (argD.tid().cLess().isGeneric()) {
                    argD = argD.tid(generics.getOrDefault(argD.tid().cLess(), argS.tid())).c(argD.c());
                }
                newArgs.add(argD);
            }
            def = def.args(lst(newArgs));
        }

        if (def.rng().tid().cLess().isGeneric()) {
            def = def.rng(T(generics.getOrDefault(def.rng().tid().cLess(), spec.rng().tid()).c(def.rng().c().toString())));
        }
        LOG.trace("generic specification mapped %s => %s to %s via %s", lhs, spec, def, this);
        return def;
    }

    @Override
    default Inst resolve(final Obj lhs) {
        final GraphittyLogger LOG = Graphitty.log(lhs);
        final Resolution currentResolution = this.resolution();
        LOG.trace("%s => %s in resolution state {{m}}%s{{/m}}", lhs, this, currentResolution);
        if (currentResolution == Resolution.A) {
            try {
                final Inst resolved = Router.global().read(this.tid())
                        .stream()
                        .map(Obj::<Inst>as)
                        .filter(i -> this.args().isRec() || i.args().isRec() || i.args().count() == this.args().count())
                        .map(i -> this.hasDomOrRng() ? i.tid(this.tid()) : i)
                        .map(i -> i.specify(lhs, this))
                        .filter(i -> lhs.matches(i.dom()))
                        .map(i -> {
                            final Poly resolvedArgs = resolveArgs(this, i, lhs);
                            if (null == resolvedArgs)
                                return null; // TODO: backtrack the resolution to the outer inst to see if adjusting the coefficient can resolve the internal resolution
                            return i.args(resolvedArgs);
                        })
                        .filter(i -> !Objects.isNull(i))
                        //.map(i -> i.tid(i.tid().dom(lhs.tid())).vid(this.vid()))
                        .map(Obj::<Inst>as)
                        .map(i -> i.resolve(lhs)) // TODO: return resolve(lhs) if failing
                        .map(i -> i.c(this.c()))
                        .findFirst()
                        .orElse(null);
                if (null != resolved) {
                    LOG.trace("resolution ({{m}}%s {{g}}=>{{/g}} %s{{/m}}): %s => %s", currentResolution, resolved.resolution(), lhs, resolved);
                    return resolved;
                }
            } catch (final Exception e) {
                this.logger().error(e);
            }
            this.logger().trace("searching spaces for runtime resolution of inst %s => %s", lhs, this);
            Obj resolved2 = Router.global().read(this.tid());//.c(this.c());
            resolved2 = this.hasDomOrRng() ? resolved2.tid(this.tid()) : resolved2;
            if (resolved2.isNoObj()) {
                LOG.debug("%s could not be resolved in any space", this);
                return NoObj.single();
            } else if (!resolved2.isInst()) {
                LOG.debug("unable to resolve %s to a single inst in %s", this.dom(lhs.type()), resolved2);
                final Poly args = resolveArgs(this, this, lhs);
                return null == args ? this : this.args(args);
                //return this;//NoObj.single();//.resolve(lhs);
            } else {
                LOG.debug("resolved %s from global router", resolved2);
                return resolved2.<Inst>as().args(this.args()).c(this.c()); //.resolve(lhs);
            }
        } else { // Resolve.B
            final boolean blocking = this.isBlocking();
            if (!blocking && (!lhs.matches(this.dom()) || !(lhs.take(this.dom().c()).get0()).matches(this.dom())))
                throw MTronException.of("{{m}}lhs obj{{/m}} does not match inst domain (resolve): %s {{r}}=/>{{/r}} %s", lhs, this);
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
                            .collect(Collectors.toMap(kv -> kv.get(0), kv -> kv.get(1), Obj::append, LinkedHashMap::new)));
            final Inst resolved = this.args(cargs);
            LOG.trace("resolution ({{m}}%s {{g}}=>{{/g}} %s{{/m}}): %s => %s", currentResolution, resolved.resolution(), lhs, resolved);
            return resolved;
        }
    }

    @Override
    default Obj apply(final Obj lhs) {
        Obj clhs = lhs;
        Inst cinst = this.resolve(clhs);
        boolean modulateC = false;
        if (!cinst.isBlocking() && !clhs.matches(cinst.dom())) {
            if (clhs.uniqueCount().isOne() && !clhs.c().isOne()) { // && cinst.dom().c().within(cInt.SOME())) {
                clhs = clhs.c(cInt::one);
                cinst = this.resolve(clhs);
                modulateC = true;
            }
            if (!clhs.rng().matches(cinst.dom()))
                throw MTronException.of("{{m}}lhs obj{{/m}} does not match inst domain (apply): %s {{r}}=/>{{/r}} %s", clhs.rng(), cinst.dom());
        }
        Router.stack().push(cinst.args());
        Obj rhs = NoObj.single();
        if (null == cinst.f()) {
            throw MTronException.of("unable to resolve %s", cinst);
        }
        try {
            rhs = cinst.f().apply(clhs, cinst);
            Graphitty.log(cinst).trace("%s ({{m}}lhs{{/m}}) => %s ({{m}}inst{{/m}}) => %s ({{m}}rhs{{/m}}) evaluated {{g}}successfully{{/g}}", clhs, cinst, rhs);
        } catch (final Exception e) {
            Graphitty.log(cinst).error("%s => %s evaluation error: %s", clhs, cinst, e.getMessage());
        } finally {
            Router.stack().pop();
        }
        if (!rhs.matches(cinst.rng()))
            throw MTronException.of("{{m}}rhs obj{{/m}} (%s) {{r}}does not match{{/r}} {{m}}inst range{{/m}} (%s): %s", rhs, cinst.rng(), cinst);
        //final cInt cinstc = false && cinst.isReducing() ? cInt.ONE() : cinst.c();
        final cInt cc = cinst.c();
        return false && rhs.isObjs() ? rhs : (modulateC ? rhs.c(c -> c.mult(lhs.c())) : rhs).c(c -> c.mult(cc));

    }

    default boolean isGather() {
        return /*this.dom().c().min() > 1 ||*/ this.dom().c().max() == null;
    }

    default boolean isBatching() {
        return this.isGather() || this.dom().c().max() > 1;
    }

    default boolean isScatter() {
        return this.rng().c().isOne();
    }

    default boolean isInitial() {
        return this.dom().c().isZero();// || this.dom().tid().coefficientValue().isQuestion();
    }

    default boolean isTerminal() {
        return this.rng().c().isZero();
    }

    default boolean isReducing() {
        return this.isGather() && this.rng().c().isOne();
    }

    default boolean isBranching() {
        return this.tid().basePath().equals(SPLIT_TID);
    }

    @Override
    default Inst tid(final fURI newTid) {
        return this.clone(this.value(), newTid, this.vid());
    }

    public enum Resolution {
        A("f"), B("f(a)"), C("f(a)->b");

        final String value;

        Resolution(final String value) {
            this.value = value;
        }

        public String value() {
            return this.value;
        }
    }

    final class f implements BiFunction<Obj, Inst, Obj> {
        public static f UNKNOWN = null;
        final Object func;
        private final boolean bi;


        private f(final BiFunction<Obj, Inst, Obj> func) {
            this.bi = true;
            this.func = func;

        }

        private f(final Function<Obj, Obj> func) {
            this.bi = false;
            this.func = func;
        }

        public static f of(final BiFunction<Obj, Inst, Obj> func) {
            return null == func ? null : new f(func);
        }

        public static f of(final Function<Obj, Obj> func) {
            return null == func ? null : new f(func);
        }

        public Obj apply(final Obj lhs, final Inst cinst) {
            return this.bi ?
                    ((BiFunction<Obj, Inst, Obj>) this.func).apply(lhs, cinst) :
                    ((Function<Obj, Obj>) this.func).apply(lhs);
        }

        @Override
        public String toString() {
            return this.func instanceof Obj ? this.func.toString() : "<j>";
        }
    }
}