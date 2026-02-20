/*
 * Metatron: A Distributed Computing Language and Virtual Machine
 *  Copyright (C) 2025- PhaseShift Studio, LLC
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

package studio.phaseshift.metatron.isa.m.type;

import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.lang.sys.router.impl.FutureObj;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;

import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.MTronException.mexcept;
import static studio.phaseshift.metatron.util.Tuple.Triplet;

public interface Inst extends Call {

    String ARGS = "args";
    String DOM = "dom";
    String RNG = "rng";
    String OBJ = "obj";
    
    fURI ARGS_FURI = fURI.f(ARGS);
    Uri ARGS_URI = uri(ARGS_FURI);

    enum Form {
        initial,
        terminal,
        fork,
        join,
        reducer,
        gather,
        scatter,
        catcher,
        filter,
        mapper,
        flatmapper,
        standard;

        public static Form of(final Inst inst) {
            if (inst.isInitial())
                return initial;
            if (inst.isTerminal())
                return terminal;
            if (inst.isBranching())
                return fork;
            if (inst.isJoining())
                return join;
            if (inst.isReducing())
                return reducer;
            if (inst.isGather())
                return gather;
            if (inst.isScatter())
                return scatter;
            if (inst.isCatch())
                return catcher;
            if (inst.isFilter())
                return filter;
            if (inst.isMap())
                return mapper;
            if (inst.isFlatMap())
                return flatmapper;
            return standard;
        }
    }

    private static Poly resolveArgs(final Inst userInst, final Inst apiInst, final Obj lhs) {
        final GraphittyLogger LOG = Graphitty.log(userInst);
        if (apiInst.args().isLst()) {
            LOG.trace("resolving lst args of %s", apiInst);
            final List<Obj> resolvedArgs = new ArrayList<>();
            for (int i = 0; i < apiInst.args().count(); i++) {
                if (userInst.arg(i) instanceof FutureObj)
                    LOG.error(userInst.arg(i) + " is a future");
                if (apiInst.arg(i) instanceof FutureObj)
                    LOG.error(apiInst.arg(i) + " is a future");
                final Obj usrArg = FutureObj.resolveFuture(userInst.arg(i));
                final Obj apiArg = FutureObj.resolveFuture(apiInst.arg(i));
                if (userInst.isBlocking()) {
                    resolvedArgs.add(usrArg);
                } else if (apiArg.isCall() && usrArg.isNoObj()) { // used for default args (when user arg is noobj)
                    final Obj r = apiArg.apply(usrArg).resolve(lhs);
                    if (r.rng().test(apiArg))
                        resolvedArgs.add(r);
                    else return null;
                } else if (usrArg.isObjCall()) {
                    final Inst firstInst = usrArg.<Call>as().insts().get(0);
                    if (!firstInst.hasDomAndRng() && (firstInst.tid().basePath().equals(FROM_INST_TID))) { // from() is a side-effect and the type can't be known unless explicitly specified (need a way to denote side-effect insts).
                        resolvedArgs.add(usrArg.resolve(lhs));
                    } else {
                        final Obj r = usrArg.resolve(lhs);
                        if (r.rng().test(apiArg)) // && userArg.rng().c().within(apiArg.c()))
                            resolvedArgs.add(r);
                        else return null;
                    }
                } else {
                    if (!usrArg.test(apiArg))
                        return null;
                    resolvedArgs.add(usrArg.resolve(lhs));
                }
            }
            return lst(resolvedArgs);
        } else if (apiInst.args().isRec()) {
            LOG.trace("processing rec args of %s", apiInst);
            final AtomicInteger counter = new AtomicInteger(0);
            return rec(apiInst.args().asRec().elements()
                    .map(kv -> {
                        Obj this_arg = userInst.arg(kv.first().uriValue(), counter.getAndIncrement());
                        return rel(kv.first(), kv.second().isCall() ? kv.second().apply(this_arg) : this_arg);
                    }));
        } else
            throw MTronException.of("inst args must be a lst or rec: %s", apiInst);
    }

    @Override
    Inst clone(final Object jvm, final fURI tid, final fURI vid);

    @Override
    Triplet<Poly, f, Obj> jvm();

    /// ////////////////////////////////////////////////////////////
    /// ////////////////////////////////////////////////////////////

    @Override
    default Type dom() {
        final fURI domain = this.tid().dom();
        // return MType.of(domain);
        return T(domain);
    }

    @Override
    default Type rng() {
        final fURI range = this.tid().rng();
        return T(range);
        //return MType.of(range);
    }

    default Poly<?, ?> args() {
        return this.jvm().get0();
    }

    default Inst args(final Poly<?, ?> args) {
        return this.clone(Triplet.with(args, this.f(), this.seed()), this.tid(), this.vid());
    }

    default Obj arg(final int index) {
        return this.args().isLst() ?
                (this.args().lstValue().size() > index ? this.args().lstValue().get(index) : noobj()) :
                IteratorUtil.index(this.args().elements().iterator(), index, noobj()).<Rel>as().second();
    }

    @Override
    default Inst c(final cInt c) {
        return this.tid(this.tid().c(c.toString()));
    }

    default Obj arg(final fURI key) {
        return this.args().<Rec>as().at(key.toUri());
    }

    default Obj arg(final String key) {
        return this.args().<Rec>as().at(key);
    }

    default Obj arg(final fURI key, final int index) {
        return this.args().isRec() ? this.arg(key) : this.arg(index);
    }

    default Inst.f f() {
        return null == this.jvm() ? null : this.jvm().get1();
    }

    default boolean hasf() {
        return null != this.jvm() && null != this.jvm().get1();
    }

    default Obj seed() {
        return null == this.jvm() ? noobj() : this.jvm().get2();
    }

    default boolean isResolved(final boolean nested) {
        boolean resolved = this.hasf();
        return (!nested || !resolved) ? resolved : this.<Inst>as().args().elements().allMatch(c -> c.isResolved(true));
    }

    default boolean isBlocking() {
        return this.tid().basePath().equals(BLOCK_INST_TID) ||
                // this.tid().basePath().equals(AUTO_TID) ||
                this.tid().basePath().equals(ORDER_INST_TID) ||
                this.tid().basePath().equals(AS_INST_TID) ||
                this.tid().basePath().equals(WITHIN_INST_TID) ||
                this.tid().basePath().equals(ISA_INST_TID) ||
                this.tid().basePath().equals(SELECT_INST_TID) ||
                this.tid().basePath().equals(WHERE_INST_TID) ||
                this.tid().basePath().equals(GROUP_INST_TID) ||
                this.tid().basePath().equals(REPEAT_INST_TID) ||
                this.tid().basePath().equals(CATCH_INST_TID);
    }

    @Override
    default Inst resolve(final Obj lhs) {
        if (this.hasf())
            return this;
        final GraphittyLogger LOG = Graphitty.log(lhs);
        try {
            Obj fetched = Router.global().read(this.tid().basePath());
            /// //////////////////////////////////////////////////
            if (fetched.stream().noneMatch(Obj::isInstObj)) {
                fetched = Router.global().read(this.tid().extend("apply"));
                if (fetched.stream().noneMatch(Obj::isInstObj))
                    fetched = Router.global().read(this.type().tid().extend("apply"));
                LOG.debug("apply() insts at: %s => %s", this.tid().extend("apply"), fetched);
            }
            /// //////////////////////////////////////////////////
            LOG.debug("fetched insts: %s => %s", this.tid().basePath(), fetched);
            final Inst resolved = fetched.stream()
                    //.map(i -> i.isCode() ? i.asCode().tryToInst() : i)
                    //.map(i -> i.isInst() ? i.asInst() : instC(this.tid().dom(lhs.tid()).rng(ALL.maybeSome()), this.args(), (lhs2, inst) -> Router.global().write(this.tid(), inst.args())))
                    .filter(Obj::isInst)
                    .map(Obj::asInst)
                    .filter(i -> !this.tid().basePath().equals(AS_INST_TID) || this.arg(0).test(i.arg(0)))
                    .filter(i -> (i.args().isEmpty() && this.arg(0).isNoObj()) || i.args().isRec() || i.args().count() >= this.args().count()) // TODO: check which recs are default
                    .filter(i -> !lhs.isInst() || (i.dom().baseType().equals(INST_TID)))
                    //.sorted(Comparator.comparing(Inst::dom, (a, b) -> lhs.matches(a.dom()) ? -1 : lhs.matches(b.dom()) ? 1 : 0)) // TODO: explore this more fully
                    //.filter(i -> !this.hasDom() || this.dom().matches(i.dom()))
                    //.filter(i -> !this.hasRng() || this.rng().matches(i.rng()))
                    .map(i -> this.hasDom() ? i.dom(this.dom()) : i)
                    .map(i -> this.hasRng() ? i.rng(this.rng()) : i)
                    /*.map(i -> { // TODO: expand this concept
                        if (this.hasRng())
                            return i.rng(this.rng());
                        else if (!this.tid().basePath().equals(AS_INST_TID))
                            return i;
                        else if (this.arg(0).asType().matches(i.rng())) {
                            return i.rng(this.arg(0).asType());
                        } else
                            return null;
                    })*/
                    //.filter(i -> !Objects.isNull(i))
                    .map(i -> lhs.isInst() ? i : Helpers.bindGenerics(lhs, i, this))
                    .filter(i -> !Objects.isNull(i))
                    .filter(i -> lhs.isInst() || lhs.test(i.dom()))
                    //.filter(i -> lhs.matches(i.dom()) || (Form.of(i).equals(Form.mapper) && lhs.unique() && lhs.c(cInt.ONE()).matches(i.dom())))
                    //.map(i -> lhs.isType() && !lhs.isNoObj() && i.tid().dom().hasPattern() ? i.dom(lhs.as()) : i)
                    .map(i -> {
                        final Poly<?, ?> resolvedArgs = resolveArgs(this, i, lhs);
                        if (null == resolvedArgs)
                            return null; // TODO: backtrack the resolution to the outer inst to see if adjusting the coefficient can resolve the internal resolution
                        return i.args(resolvedArgs);
                    })
                    .filter(i -> !Objects.isNull(i))
                    .map(i -> i.isInitial() ? i.rng(i.arg(0).type()) : i) // TODO: only start()?
                    //.map(i -> lhs.isType() ?  i.dom(lhs.c(i.dom().c()).as()).<Inst>as() : i)
                    .map(i -> i.c(this.c()))
                    .findFirst()
                    // .map(i -> i.args().isEmpty() ? i.args(lst(noobj())).resolve(lhs) : i.resolve(lhs))
                    .orElse(null);
            if (null != resolved) {
                LOG.trace("%s => %s is %s resolved", lhs, resolved, CommonUtil.lambda(() -> resolved.isResolved(false) ? "" : "not"));
                return resolved;
            }
        } catch (final Exception e) {
            this.logger().error(e);
        }
        // find all other insts of the same name 
        // if they all have the same domain coefficient as the lhs obj, 
        // then that can be hard coded into the compilation
        Obj resolved2 = Router.readFromSpace(this.tid());
        final List<cInt> uniqueDomains = resolved2.stream().map(v -> v.tid().dom().cV()).distinct().toList();
        final Inst domainInst = (uniqueDomains.size() == 1 && uniqueDomains.get(0).equals(lhs.tid().cV())) ? this.dom(lhs.type()) : this;
        this.logger().trace("performing runtime resolution of %s => %s", lhs, domainInst);
        resolved2 = domainInst.hasDomOrRng() ? resolved2.tid(domainInst.tid()) : resolved2;
        if (resolved2.isNoObj()) {
            LOG.debug("%s could not be resolved in any space", domainInst);
            return noobj();
        } else if (!resolved2.isInst()) {
            LOG.debug("unable to resolve %s to a single inst in %s", domainInst, resolved2);
            final Poly args = resolveArgs(domainInst, domainInst, lhs);
            return null == args ? domainInst : domainInst.args(args);
        } else {
            LOG.debug("resolved %s from global router", resolved2);
            final Inst resolve2 = resolved2.<Inst>as().args(domainInst.args()).c(domainInst.c()); //.resolve(lhs);
            return resolve2.hasRng() ? resolve2 : resolve2.rng(T(ALL_STAR));
        }
    }

    @Override
    default Obj apply(final Obj lhs) {
        Obj clhs = FutureObj.resolveFuture(lhs);
        //boolean reself = !this.args().isEmpty() && this.args().argElements().noneMatch(e -> e.vid() != null || e.isObjCall());
        Inst cinst = this.args().isEmpty() ? this.args(lst(noobj())).resolve(clhs) : this.resolve(clhs); // TODO: this isn't a general solution (multi slotted args won't work).
        //if (false && reself) // TODO: why do type predicates get rewritten?
        //    this.self(Triplet.with(cinst.args(), cinst.f(), cinst.seed()), cinst.tid(), cinst.vid());
        if (lhs.isNoObj() && !cinst.dom().c().isZeroable())
            return noobj();
        if (cinst.isNoObj())
            return mexcept("inst %s could not be resolved for %s", this, clhs).asFail();

        Obj rhs;
        boolean modulateC = false;
        if (BootLoader.TYPE_CHECK && !lhs.isFail() && !lhs.isCaughtFail() && !clhs.test(cinst.dom()) && clhs.unique()) {
            // if (clhs.uniqueC().isOne() && !clhs.c().isOne()) { // && cinst.dom().c().within(cInt.SOME())) {
            clhs = clhs.c(cInt::one);
            cinst = this.resolve(clhs);
            modulateC = true;
            //  }
            if (!clhs.test(cinst.dom()))
                return fail(mexcept("lhs range does not match inst domain: %s => %s [%s]", clhs.rng(), cinst.dom(), cinst));
        }
        if (!clhs.isFail() || cinst.isCatch()) {
            try {
                if (null == cinst.f()) {
                    return fail(mexcept("unable to determine inst function:" +
                            "\n\t%-10s => %-10s  | [inst]" +
                            "\n\t%-10s => %-10s  |  \\_dom" +
                            "\n\t%-10s %s=> %-10s  |  \\_args", clhs, cinst, clhs.type(), cinst.dom(), clhs.type(), cinst.args().elements().allMatch(clhs::test) ? "=" : "X", cinst.args()));
                }
                cinst = Helpers.applyArgs(clhs, cinst);
                Router.stack().push(cinst.args());
                try {
                    rhs = Objs.trySingleton(FutureObj.resolveFuture(cinst.f().apply(clhs, cinst)));
                    Graphitty.log(cinst).trace("%s (lhs) => %s (inst) => %s (rhs) evaluated successfully", clhs, cinst, rhs);
                } catch (final Exception e) {
                    rhs = fail(e, mexcept("apply failure:" +
                            "\n\t[lhs]   | %s" +
                            "\n\t \\_type | %s" +
                            "\n\t  \\_p   | %s" +
                            "\n\t[inst]  | %s" +
                            "\n\t \\_dom  | %s" +
                            "\n\t \\_args | %s" +
                            "\n\t[stack] | %s", clhs, clhs.type(), clhs.type().hasPredicate() ? clhs.type().predicate() : noobj(), cinst, cinst.dom(), cinst.args(), lst(new ArrayList<>(Router.stack().sjvm()))).asFail());
                    // e.printStackTrace();
                } finally {
                    Router.stack().pop();
                }
            } catch (final Exception e) {
                rhs = e instanceof MTronException ? ((MTronException) e).asFail() : mexcept("unable to evaluate inst function: %s", cinst).cause(e).asFail();
            }
            if (BootLoader.TYPE_CHECK && !rhs.isType() && !rhs.isFail() && !lhs.isCaughtFail() && !rhs.test(cinst.rng()))
                rhs = mexcept("inst resolution failure: %s", cinst)
                        .cause(mexcept("rhs does not match inst range:\n%s", Poly.Helper.diffObjRecursion(rhs, cinst.rng())))
                        .asFail();
        } else {
            rhs = clhs; // propagate fail through inst unless it's a catch inst
        }
        final cInt cc = cinst.c();
        return modulateC ? rhs.c(c -> c.mult(lhs.c()).mult(cc)) : rhs.c(c -> c.mult(cc));
    }

    default boolean isCatch() {
        return this.tid().basePath().equals(CATCH_INST_TID);
    }

    default boolean isGather() {
        return /*this.dom().c().min() > 1 ||*/ this.dom().c().max() == null;
    }

    default boolean isBatching() {
        return this.isGather() || this.dom().c().max() > 1;
    }

    default boolean isScatter() {
        return this.dom().c().gt(cInt.ONE()) && this.rng().c().isOne();
    }

    default boolean isInitial() {
        return this.dom().c().isZero();// || this.dom().tid().coefficientValue().isQuestion();
    }

    default boolean isFilter() {
        return this.dom().c().isOne() && this.rng().c().isMaybe() && this.dom().tid().basePath().equals(this.rng().tid().basePath());
    }

    default boolean isMap() {
        return this.dom().c().isOne() && this.rng().c().isOne();
    }

    default boolean isFlatMap() {
        return this.dom().c().isOne() && this.rng().c().isMaybeSome();
    }

    default boolean isTerminal() {
        return this.rng().c().isZero();
    }

    default boolean isReducing() {
        return this.isGather() && this.rng().c().isOne();
    }

    default boolean isBranching() {
        return this.tid().basePath().equals(SPLIT_INST_TID);
    }


    default boolean isJoining() {
        return this.tid().basePath().equals(MERGE_INST_TID);
    }

    @Override
    default Inst tid(final fURI tid) {
        return this.clone(this.jvm(), tid, this.vid());
    }

    final class Helpers {
        private Helpers() {
            // do nothing
        }

        public static Inst applyArgs(final Obj lhs, final Inst inst) {
          /*  if (inst.args().isRec() && inst.args().<Rec>as().elements().noneMatch(r -> r.second() instanceof FutureObj || r.first() instanceof FutureObj || r.first().isObjCall() || r.second().isObjCall() || r.first().isType() || r.second().isType()))
                return inst;
            else if (inst.args().isLst() && inst.args().<Lst>as().elements().noneMatch(e -> e instanceof FutureObj || e.isObjCall() || e.isType()))
                return inst;*/
            final boolean blocking = inst.isBlocking();
            /*if (BootLoader.TYPE_CHECK) {
                if (!blocking && (!lhs.matches(inst.dom()) || !(lhs.take(inst.dom().c()).get0()).matches(inst.dom())))
                    throw MTronException.of("{{m}}lhs obj{{/m}} does not match inst domain (resolve): %s {{r}}=/>{{/r}} %s", lhs, inst);
            }*/
            final Poly cargs = inst.args().isLst() ?
                    lst(inst.args().lstValue()
                            .stream()
                            .map(FutureObj::<Obj>resolveFuture)
                            .map(arg -> {
                                if (blocking)
                                    return arg;
                                else {
                                    final Obj r = Objs.trySingleton(arg.apply(lhs));
                                    if (!arg.isCall() && !r.test(arg)) {
                                        // LOG.error("unmatched inst arg in %s: %s ({{y}}lhs{{/y}}) {{g}}=>{{/g}} %s ({{y}}arg{{/y}}) {{r}}~!>{{/r}} %s ", this, lhs, arg, r);
                                        return arg;
                                    }
                                    //throw MTronException.of("arg obj does not match inst arg: %s: %s {{r}}-/>{{/r}} %s", this, arg, r);
                                    return r;
                                }
                            }).toList()) :
                    rec(inst.args().recValue().entrySet()
                            .stream()
                            .map(kv -> rel(kv.getKey().apply(lhs), blocking ?
                                    kv.getValue() :
                                    kv.getValue().apply(lhs))));
            final Inst resolved = inst.args(cargs);
            //  LOG.trace("resolution ({{m}}%s {{g}}=>{{/g}} %s{{/m}}): %s => %s", currentResolution, resolved.resolution(), lhs, resolved);
            return resolved;
        }

        public static Inst bindGenerics(final Obj lhs, final Inst apiInst, final Obj userInst) {
            final GraphittyLogger LOG = Graphitty.log(lhs);
            final Map<fURI, fURI> generics = new HashMap<>();
            Inst apiInstTemp = apiInst;
            if (apiInstTemp.dom().tid().isCLessGeneric() && lhs.type().c().within(apiInstTemp.dom().c())) {
                generics.put(apiInstTemp.dom().tid().cLess(), lhs.type().tid().cLess());
                apiInstTemp = apiInstTemp.dom(lhs.type().c(apiInstTemp.dom().c()).as());
            }
            if (apiInstTemp.rng().tid().isCLessGeneric() && generics.containsKey(apiInstTemp.rng().tid().cLess())) {
                apiInstTemp = apiInstTemp.rng(T(generics.get(apiInstTemp.rng().tid().cLess()).c(apiInstTemp.rng().c().toString())));
            }
            if (!apiInst.args().isEmpty())
                if (apiInst.args().isRec()) {
                    final Map<Obj, Obj> newArgs = new LinkedHashMap<>();
                    for (final Map.Entry<Obj, Obj> kv : apiInst.args().recValue().entrySet()) {
                        Obj argD = kv.getValue();
                        //  Obj argS = userInst.isInst() ? userInst.<Inst>as().arg(kv.getKey().uriValue()) : userInst;
                        //if (argD.tid().cLess().isGeneric()) {
                        //final fURI lastBinding = generics.get(argD.tid().cLess());
                        //   if (null != lastBinding && !argS.tid().cLess().matches(lastBinding))
                        //  LOG.debug("existing generic doesn't match current usage: [{{m}}generic{{/m}}] %s [{{m}}past{{/m}}] %s [{{m}}present{{/m}}] %s", argS.tid(), lastBinding, argD.tid());
                        // generics.computeIfAbsent(argD.tid().cLess(), k -> argS.tid().cLess()); // beware of int[0] yielding noobj across all bindings
                        //}
                      /* if (argD.isInst()) {
                            argD = Helpers.bindGenerics(lhs, argD.<Inst>as(), argS);
                        } else if (argD.tid().cLess().isGeneric()) {
                            argD = argD.tid(generics.getOrDefault(argD.tid().cLess(), argS.tid())).c(argD.c());
                        }*/
                        newArgs.put(kv.getKey(), argD);
                    }
                    apiInstTemp = apiInstTemp.args(rec(newArgs));
                } else if (apiInst.args().isLst()) {
                    final List<Obj> resolvedArgs = new ArrayList<>();
                    for (int i = 0; i < apiInst.args().count(); i++) {
                        Obj apiArg = apiInst.arg(i);
                        Obj userArg = userInst.isInst() ? userInst.<Inst>as().arg(i) : userInst;
                        if (apiArg.tid().isGeneric()) {
                            final fURI lastBinding = generics.get(apiArg.tid().cLess());
                            if (null != lastBinding && !userArg.tid().matches(lastBinding))
                                LOG.debug("existing generic doesn't match current usage: [{{m}}generic{{/m}}] %s [{{m}}past{{/m}}] %s [{{m}}present{{/m}}] %s", userArg.tid(), lastBinding, apiArg.tid());
                            if (!userArg.isCall()) // TODO: can this be more specialized (currently necessary for when arg is a call and we want the result of the call to be the binding, not the call itself
                                generics.computeIfAbsent(apiArg.tid().cLess(), k -> userArg.tid().cLess()); // beware of int[0] yielding noobj across all bindings
                        }
                        if (apiArg.isInst()) { // todo: isCall()?
                            apiArg = Helpers.bindGenerics(lhs, apiArg.asInst(), userArg);
                        } else {
                            if (apiArg.tid().isCLessGeneric())
                                apiArg = apiArg.tid(generics.getOrDefault(apiArg.tid().cLess(), userArg.tid())).c(apiArg.c());
                            //LOG.warn(apiArg + "----" + userArg);
                            if (null != apiArg && !apiArg.isCall() && !userArg.tid().cLess().isGeneric() && !userArg.test(apiArg)) {
                                // TODO: isClessGeneric() and cLess.isGeneric() behave differently
                                return null;
                            }
                        }
                        resolvedArgs.add(apiArg);
                    }
                    apiInstTemp = apiInstTemp.args(lst(resolvedArgs));
                }

            if (apiInstTemp.rng().tid().cLess().isGeneric()) {
                apiInstTemp = apiInstTemp.rng(T(generics.getOrDefault(apiInstTemp.rng().tid().cLess(), userInst.rng().tid()).c(apiInstTemp.rng().c().toString())));
            }
            ///  hail mary
            if (apiInstTemp.dom().tid().isCLessGeneric()) {
                apiInstTemp = apiInstTemp.dom(lhs.type().c(apiInstTemp.dom().c()).as());
                apiInstTemp = apiInstTemp.tid(Helpers.apiOrUser(apiInstTemp.tid(), userInst.tid(), generics));
            }
            LOG.trace("generic specification mapped %s => %s to %s via %s", lhs, userInst, apiInstTemp, apiInst);
            return apiInstTemp;
        }

        private static fURI apiOrUser(final fURI apiInstTid, final fURI userInstTid, final Map<fURI, fURI> bindings) {
            fURI result = apiInstTid;
            if (userInstTid.hasDom()) {
                if (apiInstTid.dom().isCLessGeneric())
                    bindings.put(apiInstTid.dom().cLess(), userInstTid.dom().cLess());
                result = result.dom(userInstTid.dom());

            } else if (apiInstTid.dom().isCLessGeneric()) {
                result = result.dom(bindings.getOrDefault(apiInstTid.dom().cLess(), apiInstTid.dom())).c(apiInstTid.dom().c());
            }
            /// /////
            if (userInstTid.hasRng()) {
                if (apiInstTid.rng().isCLessGeneric())
                    bindings.put(apiInstTid.rng().cLess(), userInstTid.rng().cLess());
                result = result.rng(userInstTid.rng());

            } else if (apiInstTid.rng().isCLessGeneric()) {
                result = result.rng(bindings.getOrDefault(apiInstTid.rng().cLess(), apiInstTid.rng())).c(apiInstTid.dom().c());
            } else if (result.dom().isCLessGeneric()) {
                result = result.dom(bindings.getOrDefault(result.dom().cLess(), result.dom())).c(result.dom().c());
            }
            return result;
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

        public boolean isLambda() {
            return !(this.func instanceof Obj);
        }

        private f(final Function<Obj, Obj> func) {
            this.bi = false;
            this.func = func;
        }

        private f(final String func) {
            this.bi = false;
            this.func = mParser.parse(func);
        }

        public static f of(final BiFunction<Obj, Inst, Obj> func) {
            return null == func ? null : new f(func);
        }

        public static f of(final String func) {
            return null == func ? null : new f(func);
        }

        public static f of(final Function<Obj, Obj> func) {
            return null == func ? null : new f(func);
        }

        public Obj apply(final Obj lhs, final Inst cinst) {
            return lhs.isFail() && !cinst.isCatch() ?
                    lhs : (this.bi ?
                    ((BiFunction<Obj, Inst, Obj>) this.func).apply(lhs, cinst) :
                    ((Function<Obj, Obj>) this.func).apply(lhs));
        }

        @Override
        public String toString() {
            return this.func instanceof Obj ? this.func.toString() : "<j>";
        }
    }

    public static final class InstType {

        public static Type INST_TYPE = Type.Builder.build().tid(INST_TID).vid(INST_TID).create();

        public static Set<Inst> insts() {
            return new LinkedHashSet<>(List.of(
                    instC(LIFT_INST_TID.dom(ALL).rng(ALL), lst(T(ALL)), (lhs, inst) -> inst.arg(0).<Inst>as().args(lhs.<Poly>as()))
                    //instC(LSHIFT_INST_TID.dom(INST_TID).rng(ALL), lst(), (lhs, inst) -> lhs.dom()),
                    //instC(RSHIFT_INST_TID.dom(INST_TID).rng(ALL), lst(), (lhs, inst) -> lhs.rng())
            ));
        }
    }
}