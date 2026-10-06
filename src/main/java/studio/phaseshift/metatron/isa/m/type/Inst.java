/*
 * metatron: a distributed virtual machine and language
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

import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.impl.MInst;
import studio.phaseshift.metatron.isa.m.type.resolver.InstSelector;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.Processor;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MCode.code;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.Tuple.Triplet;

public interface Inst extends Call {

    public record ArgsFunction(Poly<?, ?> args, f f) {
    }

    fURI ARGS_FURI = fURI.Singleton.f(ARGS);

    enum Form {
        initial("maps nothing to objs (dom_c = 0)"),
        terminal("maps objs to nothing (rng_c = 0)"),
        fork("splits objs across parallel streams"),
        join("merges parallel streams of objs into a single stream"),
        reducer("gathers objs and produces a single output (rng_c = 1)"),
        gather("maps objs to objs (dom_c = {0,})"),
        scatter("maps an obj to objs (rng_c = {0,})"),
        catcher("intercepts fail obj for recovery"),
        filter("conditionally passes or drops an obj (dom_c = 1, rng_c = {0,1})"),
        mapper("one-to-one obj transformation (dom_c = rng_c = 1)"),
        flatmapper("one-to-many obj transformation (dom_c = 1, rng_c > 1)"),
        standard("an instruction with no well-defined classification");

        public final String description;

        Form(final String description) {
            this.description = description;
        }

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

    // resolveArgs moved to Helper class for use by InstResolver implementations

    @Override
    Inst clone(final Object jvm, final fURI tid, final fURI vid);

    @Override
    Triplet<Poly, f, Obj> jvm();

    /// ////////////////////////////////////////////////////////////
    /// ////////////////////////////////////////////////////////////

    @Override
    default Type dom() {
        //if(!this.tid().hasDom())
        //    return T(ALL.maybe());
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
        return null == this.jvm().get0() ? lst() : this.jvm().get0();
    }

    default Inst args(final Poly<?, ?> args) {
        return this.clone(Triplet.with(args, this.f(), this.seed()), this.tid(), this.vid());
    }

    default Obj arg(final int index) {
        return this.args().isLst() ?
                (this.args().lstValue().size() > index ? this.args().lstValue().get(index) : noobj()) :
                IteratorUtil.index(this.args().elements().iterator(), index, noobj()).orElse(rel(noobj(), noobj())).second();
    }

    @Override
    default Inst c(final cInt c) {
        return this.tid(this.tid().c(c));
    }

    default Obj arg(final fURI key, final int index) {
        return this.args().isRec() ? this.args().<Rec>as().at(key.toUri()) : this.arg(index);
    }

    default Obj arg(final String key, final int index) {
        return this.arg(fURI.Singleton.f(key), index);
    }

    default Inst.f f() {
        return null == this.jvm() ? null : this.jvm().get1();
    }

    default Inst f(final Inst.f func) {
        return this.clone(Triplet.with(this.args(), func, this.seed()), this.tid(), this.vid());
    }

    default boolean hasf() {
        return null != this.jvm() && null != this.jvm().get1();
    }

    /**
     * Returns true if this instruction's function is a native Java lambda
     * (as opposed to mtron code). Requires {@link #hasf()} to be true.
     */
    default boolean isJavaFunction() {
        return this.hasf() && this.f().isLambda();
    }

    /**
     * Returns the mtron code Obj stored in this instruction's function,
     * or {@code noobj()} if the function is a Java lambda or absent.
     */
    default Obj getMtronFunctionObj() {
        if (!this.hasf() || this.f().isLambda()) return noobj();
        return (Obj) this.f().func;
    }

    /**
     * Returns the class name of the underlying function object.
     * For Java lambdas this is the synthetic lambda class name.
     * Returns null if no function is present.
     */
    default String functionClassName() {
        if (!this.hasf()) return null;
        return this.f().func.getClass().getName();
    }

    default Obj seed() {
        return null == this.jvm() ? noobj() : this.jvm().get2();
    }

    default boolean isResolved(final boolean nested) {
        boolean resolved = this.hasf();
        return (!nested || !resolved) ? resolved : this.<Inst>as().args().elements().allMatch(c -> c.isResolved(true));
    }

    default boolean isBlocking() {
        if (this.tid().hasQ(BLOCK))
            return true;
        final fURI base = this.tid().basePath();
        return base.equals(BLOCK_INST_TID) ||
                // these store thunks (callables) as args and apply them lazily in their
                // function — they must be blocking so applyArgs does NOT eagerly apply them:
                //   auto/auto_from/auto_at:  inst.arg(0).apply(lhs)  (lazy payload)
                //   map:                     inst.arg(0).apply(lhs)  (x.map(f) = f(x))
                //   select/where:            rec arg whose VALUES are per-element projections
                base.equals(AUTO_INST_TID) ||
                base.equals(AUTO_FROM_INST_TID) ||
                base.equals(AUTO_AT_INST_TID) ||
                base.equals(MAP_INST_TID) ||
                base.equals(SELECT_INST_TID) ||
                base.equals(WHERE_INST_TID) ||
                base.equals(FILTER_INST_TID) ||
                base.equals(FORK_INST_TID) ||
                base.equals(THREAD_INST_TID) ||
                base.equals(ORDER_INST_TID) ||
                base.equals(AS_INST_TID) ||
                base.equals(WITHIN_INST_TID) ||
                base.equals(ISA_INST_TID) ||
                base.equals(UPDATE_INST_TID) ||
                base.equals(GROUP_INST_TID) ||
                base.equals(REPEAT_INST_TID) ||
                base.equals(ELSE_INST_TID) ||
                base.equals(CATCH_INST_TID);
    }

    @Override
    default boolean isAuto() {
        if (this.isNoObj())
            return false;
        final fURI base = this.tid().basePath();
        return base.equals(AUTO_FROM_INST_TID) || base.equals(AUTO_AT_INST_TID) || base.equals(AUTO_INST_TID);
    }

    @Override
    default Inst resolve(final Obj lhs) {
        return this.resolve(lhs, InstSelector.get());
    }

    /**
     * Resolve against an explicit {@link InstSelector} — the seam the compiler's resolver stages
     * ({@code scoring_resolver::T}, {@code firstfind_resolver::T}) use to pin per-instruction
     * selection without touching the active global selector. The one-arg overload delegates to
     * the active selector.
     */
    default Inst resolve(final Obj lhs, final InstSelector sel) {
        if (this.hasf())
            return this;
        final GraphittyLogger LOG = Graphitty.log(lhs);

        // Resolution cache: DISABLED
        //
        // Attempted to enable cache only after rewrites complete, but still causes hangs.
        // The issue is complex - rewrites call apply() which triggers resolution, and even
        // with the REWRITE_MODE flag, there are circular dependencies or infinite loops.
        //
        // The parser optimization (500ms -> 3ms) is the main performance win.
        // Execution time (308ms) is acceptable for an interpreted language with rewrites.
        //
        // To re-enable, would need deeper investigation of the rewrite->apply->resolve cycle.
        /*
        final boolean inRewriteMode = REWRITE_MODE.get();
        final boolean allArgsLiteral = this.args().stream().noneMatch(Obj::isObjCall);
        final fURI basePath = this.tid().basePath();
        final boolean isDynamicInst = basePath.equals(FROM_INST_TID)
                || basePath.equals(AUTO_FROM_INST_TID)
                || basePath.toString().contains("rewrite")
                || basePath.equals(AS_INST_TID);
        final boolean canUseCache = !inRewriteMode
                && allArgsLiteral
                && !lhs.tid().isGeneric() && !this.tid().isGeneric()
                && !isDynamicInst
                && !this.hasDom() && !this.hasRng();
        final String cacheKey = canUseCache ? lhs.tid() + "|" + this.tid().basePath() + "|" + this.args() : null;
        if (cacheKey != null) {
            final Inst cached = RESOLUTION_CACHE.get(cacheKey);
            if (cached != null) {
                return cached.c(this.c());
            }
        }
        */

        try {
            final Inst resolved = sel.resolveInst(lhs, this);
            if (null != resolved) {
                LOG.trace("%s => %s is %s resolved", lhs, resolved, CommonUtil.lambda(() -> resolved.isResolved(false) ? "" : "not"));
                // Cache disabled - see comment above
                /*if (cacheKey != null) {
                    RESOLUTION_CACHE.putIfAbsent(cacheKey, resolved);
                }*/
                // copy over any api query maps not already on the user instruction
                return resolved.selfTID(resolved.tid().copyQ(this.tid())).as();
            } else {
                LOG.debug("unable to resolve: %s", this);
            }
        } catch (final Exception e) {
            this.logger().error(e);
        }
        // find all other insts of the same name
        // if they all have the same domain coefficient as the lhs obj,
        // then that can be hard coded into the compilation
        Obj resolved2 = Machine.readFromSpace(this.tid());
        final List<cInt> uniqueDomains = resolved2.stream().map(v -> v.tid().dom().c()).distinct().toList();
        final Inst domainInst = (uniqueDomains.size() == 1 && uniqueDomains.getFirst().equals(lhs.tid().c())) ? this.dom(lhs.type()) : this;
        this.logger().trace("performing runtime resolution of %s => %s", lhs, domainInst);
        resolved2 = domainInst.hasDomOrRng() ? resolved2.tid(domainInst.tid()) : resolved2;
        // copy over any api query maps not already on the user instruction
        resolved2 = resolved2.selfTID(resolved2.tid().copyQ(this.tid()));
        if (resolved2.isNoObj()) {
            LOG.debug("%s could not be resolved in any space", domainInst);
            return noobj();
        } else if (!resolved2.isObjInst()) {
            LOG.debug("unable to resolve %s to a single inst in %s", domainInst, resolved2);
            final Poly args = Helper.resolveArgs(domainInst, domainInst, lhs);
            return null == args ? domainInst : domainInst.args(args);
        } else {
            LOG.debug("resolved %s from global router", resolved2);
            final Inst resolve2 = resolved2.<Inst>as().args(domainInst.args()).c(domainInst.c()); //.resolve(lhs);
            return resolve2.hasRng() ? resolve2 : resolve2.rng(T(ALL_STAR));
        }
    }

    @Override
    default boolean test(final Obj other) {
        if (!other.isInst())
            return Obj.Helper.testObjs(this, other);
        else {
            if (!this.tid().basePath().test(other.tid().basePath()))
                return false;
            if (!this.tid().dom().isGeneric() && !other.tid().dom().isGeneric()) {
                if (!this.dom().test(other.dom()))
                    return false;
            }
            if (!this.tid().rng().isGeneric() && !other.rng().dom().isGeneric()) {
                if (!this.rng().test(other.rng()))
                    return false;
            }
            final Inst otherInst = other.asInst();
            final int maxArgs = (int) Math.max(this.args().count(), otherInst.args().count());
            for (int i = 0; i < maxArgs; i++) {
                final Obj aArg = this.arg(i);
                final Obj bArg = otherInst.arg(i);
                if (!aArg.test(bArg))
                    return false;
            }
            if (this.hasf() && otherInst.hasf())
                return Objects.equals(this.f(), otherInst.f());
            return true;
        }
    }

    @Override
    default Obj apply(final Obj lhs) {
        // [Compiler] resolution + [Processor] computeArgs + f().apply + runtime check/coefficient
        // all live on the machine now (Processor.Helper); the type layer's apply is a handoff.
        return Processor.Helper.apply(lhs, this);
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
        return this.dom().c().isOne() && this.rng().c().gt(this.rng().c().one());
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
        return this.clone(this.jvm(), tid/*tid.rng(this.tid().rng().c(c -> c.mult(tid.c())))*/, this.vid());
    }

    final class Helper {
        private Helper() {
            // do nothing
        }

        public static Inst idInst(final fURI vid) {
            return MInst.instC(ID_INST_TID, lst(), lhs -> lhs).selfVID(vid).as();
        }

        /**
         * The inst's address for origin context in fail messages: the vid if
         * it is a clean full address (no query), otherwise the tid base with
         * the vid's index appended ("plus@1" — the inst plus its slot in the
         * resolution). The inst query can carry sensitive state
         * (?env=[...=>...]) so it is never rendered into a failure text.
         */
        public static String instContext(final Inst cinst) {
            final String vid = Optional.ofNullable(cinst.vid()).map(Object::toString).orElse("");
            if (vid.startsWith("/") && !vid.contains("?"))
                return vid;
            final String tid = Optional.ofNullable(cinst.tid()).map(Object::toString).orElse("");
            final int q = tid.indexOf('?');
            final String base = q < 0 ? tid : tid.substring(0, q);
            return vid.isEmpty() ? base : base + "@" + vid;
        }

        public static Optional<fURI> isFromOrAtInstToUri(final Inst inst) {
            return Optional.ofNullable((inst.tid().equals(FROM_INST_TID) || inst.tid().equals(AT_INST_TID)) && inst.arg(0).isUri() ? inst.arg(0).uriValue() : null);
        }

        public static Rec rectifyLstArgs(final Lst lstArgs, final Rec recArgs) {
            final AtomicInteger counter = new AtomicInteger(0);
            return recArgs.elements().map(r -> rel(r.first(), lstArgs.at(counter.getAndIncrement()))).collect(new CommonUtil.RecCollector());
        }

        public static boolean filterOnDomainAllowUnique(final Obj lhs, final Inst apiInst) {
            if (lhs.isNoObj())
                return true; // noobj lhs means "any domain is acceptable"
            return (lhs.testByID(apiInst.dom()) || lhs.test(apiInst.dom())) || (apiInst.dom().c().isOne() && lhs.c().gt(cInt.ONE()) && lhs.c(cInt.ONE()).testByID(apiInst.dom()));
        }


        public static <O extends Obj> Optional<O> alignLHSType(final Obj lhs, final O rhs) {
            if (!lhs.c().within(rhs.c()))
                return Optional.empty();
            if (lhs.type().equals(rhs.type()))
                return Optional.of((O) lhs);
            else {
                try {
                    return Optional.of((O) lhs.as(rhs.type()));
                } catch (final Exception e) {
                    return Optional.empty();
                }
            }
        }

        public static <O extends Obj> O alignRHSType(final O lhs, final Obj rhs) {
            return (O) (lhs.type().equals(rhs.type()) ? rhs : rhs.as(lhs.type()));


        }

        /**
         * Resolve arguments from the user instruction against the API instruction signature.
         * Used by InstResolver implementations during instruction resolution.
         *
         * @param userInst the user instruction containing actual arguments
         * @param apiInst  the API instruction containing expected argument types
         * @param lhs      the left-hand-side object for argument resolution
         * @return resolved arguments as Poly, or null if arguments don't match
         */
        public static Poly resolveArgs(final Inst userInst, final Inst apiInst, final Obj lhs) {
            final GraphittyLogger LOG = Graphitty.log(userInst);
            if (apiInst.args().isLst()) {
                LOG.trace("resolving lst args of %s", apiInst);
                final List<Obj> resolvedArgs = new ArrayList<>();
                for (int i = 0; i < apiInst.args().count(); i++) {
                    final Obj usrArg = Optional.ofNullable(userInst.arg(i)).orElse(noobj());
                    final Obj apiArg = Optional.ofNullable(apiInst.arg(i)).orElse(noobj());
                    // Coefficient quick-reject REMOVED for nested calls: executor
                    // handles uniqueC() compression (e.g., {2}2.plus(x) runs once
                    // and multiplies). Type compatibility checked per-branch below.
                    if (userInst.isBlocking()) {
                        resolvedArgs.add(usrArg);
                    } else if (apiArg.isObjCall() && usrArg.isNoObj()) { // used for default args (when user arg is noobj)
                        final Obj r = apiArg.apply(usrArg).resolve(lhs);
                        if (typeCompatibleIgnoreCoefficient(r.rng(), apiArg))
                            resolvedArgs.add(r);
                        else return null;
                    } else if (usrArg.isObjCall()) {
                        final Inst firstInst = usrArg.<Call>as().insts().getFirst();
                        if (!firstInst.hasDomAndRng() && (firstInst.tid().basePath().equals(FROM_INST_TID))) {
                            resolvedArgs.add(usrArg.resolve(lhs));
                        } else {
                            final Obj r = usrArg.resolve(lhs);
                            if (typeCompatibleIgnoreCoefficient(r.rng(), apiArg))
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
                            return rel(kv.first(), kv.second().isObjCall() ? kv.second().apply(this_arg) : this_arg);
                        }));
            } else
                throw MTronException.of("inst args must be a lst or rec: %s", apiInst);
        }

        /**
         * Check type compatibility, ignoring coefficient. Unbound generics match
         * any type. Executor handles uniqueC() compression.
         */
        private static boolean typeCompatibleIgnoreCoefficient(final Obj a, final Obj b) {
            if (a.isNoObj() || b.isNoObj()) return true;
            // Use specificTypeId for isGeneric check — avoids a.type() which
            // triggers MType.T() construction and can cause StackOverflow with
            // typed values like nat::2.
            final fURI aId = Obj.Helper.specificTypeId(a);
            final fURI bId = Obj.Helper.specificTypeId(b);
            if (aId.isGeneric() || bId.isGeneric()) return true;
            if (a.testByID(b) || b.testByID(a)) return true;
            if (a.c().within(b.c()) || b.c().within(a.c())) return a.test(b);
            final Obj aNorm = a.c(cInt.ONE());
            final Obj bNorm = b.isType() ? b.asType().c(cInt.ONE()) : b.c(cInt.ONE());
            return aNorm.test(bNorm);
        }

        public static Inst bindQ(final Obj lhs, final Inst userInst, final Inst apiInst) {
            if (userInst.tid().hasQ(BLOCK))
                return apiInst.tid(apiInst.tid().addQ(BLOCK));
            return apiInst;
        }

        public static Inst bindGenerics(final Obj lhs, final Inst apiInst, final Obj userInst) {
            return bindGenerics(lhs, apiInst, userInst, new HashMap<>());
        }

        public static Inst bindGenerics(final Obj lhs, final Inst apiInst, final Obj userInst, final Map<fURI, fURI> generics) {
            final GraphittyLogger LOG = Graphitty.log(lhs);
            Inst apiInstTemp = apiInst;
            if (apiInstTemp.dom().tid().one().isGeneric() && !lhs.isNoObj() && lhs.type().c().within(apiInstTemp.dom().c())) {
                generics.put(apiInstTemp.dom().tid().one(), lhs.type().tid().one());
                apiInstTemp = apiInstTemp.dom(lhs.type().c(apiInstTemp.dom().c()).as());
            }
            if (apiInstTemp.rng().tid().one().isGeneric() && generics.containsKey(apiInstTemp.rng().tid().one())) {
                apiInstTemp = apiInstTemp.rng(T(generics.get(apiInstTemp.rng().tid().one()).c(apiInstTemp.rng().c())));
            }
            if (!apiInst.args().isEmpty())
                if (apiInst.args().isRec()) {
                    final Map<Obj, Obj> newArgs = new LinkedHashMap<>();
                    for (final Map.Entry<Obj, Obj> kv : apiInst.args().recValue().entrySet()) {
                        Obj argD = kv.getValue();
                        //  Obj argS = userInst.isInst() ? userInst.<Inst>as().arg(kv.getKey().uriValue()) : userInst;
                        //if (argD.tid().one().isGeneric()) {
                        //final fURI lastBinding = generics.get(argD.tid().one());
                        //   if (null != lastBinding && !argS.tid().one().matches(lastBinding))
                        //  LOG.debug("existing generic doesn't match current usage: [{{m}}generic{{/m}}] %s [{{m}}past{{/m}}] %s [{{m}}present{{/m}}] %s", argS.tid(), lastBinding, argD.tid());
                        // generics.computeIfAbsent(argD.tid().one(), k -> argS.tid().one()); // beware of int[0] yielding noobj across all bindings
                        //}
                      /* if (argD.isInst()) {
                            argD = Helpers.bindGenerics(lhs, argD.<Inst>as(), argS);
                        } else if (argD.tid().one().isGeneric()) {
                            argD = argD.tid(generics.getOrDefault(argD.tid().one(), argS.tid())).c(argD.c());
                        }*/
                        newArgs.put(kv.getKey(), argD);
                    }
                    apiInstTemp = apiInstTemp.args(rec(newArgs));
                } else if (apiInst.args().isLst()) {
                    final List<Obj> resolvedArgs = new ArrayList<>();
                    for (int i = 0; i < apiInst.args().count(); i++) {
                        Obj apiArg = apiInst.arg(i);
                        Obj userArg = userInst.isObjInst() ? userInst.<Inst>as().arg(i) : userInst;
                        if (apiArg.tid().isGeneric()) {
                            final fURI lastBinding = generics.get(apiArg.tid().one());
                            if (null != lastBinding && !userArg.tid().test(lastBinding))
                                LOG.debug("existing generic doesn't match current usage: [{{m}}generic{{/m}}] %s [{{m}}past{{/m}}] %s [{{m}}present{{/m}}] %s", userArg.tid(), lastBinding, apiArg.tid());
                            if (!userArg.isObjCall()) // TODO: can this be more specialized (currently necessary for when arg is a call and we want the result of the call to be the binding, not the call itself
                                generics.computeIfAbsent(apiArg.tid().one(), k -> userArg.tid().one()); // beware of int[0] yielding noobj across all bindings
                        }
                        if (apiArg.isObjInst()) { // todo: isCall()?
                            apiArg = Helper.bindGenerics(lhs, apiArg.asInst(), userArg);
                        } else {
                            if (apiArg.tid().one().isGeneric())
                                apiArg = apiArg.tid(generics.getOrDefault(apiArg.tid().one(), userArg.tid())).c(apiArg.c());
                            if (null != apiArg && !apiArg.isObjCall() && !userArg.tid().one().isGeneric() && !userArg.test(apiArg)) {
                                // TODO: isClessGeneric() and cLess.isGeneric() behave differently
                                return null;
                            }
                        }
                        resolvedArgs.add(apiArg);
                    }
                    apiInstTemp = apiInstTemp.args(lst(resolvedArgs));
                }

            if (apiInstTemp.rng().tid().one().isGeneric()) {
                apiInstTemp = apiInstTemp.rng(T(generics.getOrDefault(apiInstTemp.rng().tid().one(), userInst.rng().tid()).c(apiInstTemp.rng().c())));
            }
            ///  hail mary
            if (apiInstTemp.dom().tid().one().isGeneric() || apiInstTemp.dom().tid().one().equals(ALL)) {
                apiInstTemp = apiInstTemp.dom(lhs.type().c(apiInstTemp.dom().c()).as());
                apiInstTemp = apiInstTemp.tid(Helper.apiOrUser(apiInstTemp.tid(), userInst.tid(), generics));
            }
            LOG.trace("generic specification mapped %s => %s to %s via %s", lhs, userInst, apiInstTemp, apiInst);
            return apiInstTemp;
        }

        /**
         * Substitute the generic fURIs ({@code A}…{@code G}) in {@code obj} with their bound values. A generic is an
         * all-caps fURI carried as a uri value ({@code uri(A)}); a bound generic resolves to its value, while anything
         * else passes through untouched. Nested insts (and their lst/rec args) and codes are walked, so a binding
         * reaches a nested arg like {@code inv(<B>)} inside {@code mult(...)}.
         */
        public static Obj substituteGenerics(final Obj obj, final Map<fURI, Obj> bindings) {
            if (obj.isUri() && obj.uriValue().isGeneric() && bindings.containsKey(obj.uriValue()))
                return bindings.get(obj.uriValue());
            if (obj.isInst()) {
                final Inst inst = obj.asInst();
                final Poly<?, ?> args = inst.args();
                if (args.isLst())
                    return inst.args(lst(args.lstValue().stream().map(a -> substituteGenerics(a, bindings)).toList()));
                if (args.isRec()) {
                    final Map<Obj, Obj> newArgs = new LinkedHashMap<>();
                    args.recValue().forEach((k, v) -> newArgs.put(k, substituteGenerics(v, bindings)));
                    return inst.args(rec(newArgs));
                }
                return inst;
            }
            if (obj.isCode())
                return code(obj.asCode().insts().stream().map(i -> (Inst) substituteGenerics(i, bindings)).toList());
            return obj;
        }

        private static fURI apiOrUser(final fURI apiInstTid, final fURI userInstTid, final Map<fURI, fURI> bindings) {
            fURI result = apiInstTid;
            if (userInstTid.hasDom()) {
                if (apiInstTid.dom().one().isGeneric())
                    bindings.put(apiInstTid.dom().one(), userInstTid.dom().one());
                result = result.dom(userInstTid.dom());

            } else if (apiInstTid.dom().one().isGeneric()) {
                result = result.dom(bindings.getOrDefault(apiInstTid.dom().one(), apiInstTid.dom()).c(apiInstTid.dom().c()));
            }
            /// /////
            if (userInstTid.hasRng()) {
                if (apiInstTid.rng().one().isGeneric())
                    bindings.put(apiInstTid.rng().one(), userInstTid.rng().one());
                result = result.rng(userInstTid.rng());

            } else if (apiInstTid.rng().one().isGeneric()) {
                result = result.rng(bindings.getOrDefault(apiInstTid.rng().one(), apiInstTid.rng()).c(apiInstTid.rng().c()));
            } else if (result.dom().one().isGeneric()) {
                result = result.dom(bindings.getOrDefault(result.dom().one(), result.dom()).c(result.dom().c()));
            }
            return result;
        }

        /**
         * The projection surface of an inst as a rec — what a bare {@code inst>>} yields. This is
         * the shape an as-graph edge record mirrors, so {@code edge>>rng} and {@code edge>>inst>>rng}
         * agree.
         */
        public static Obj descriptor(final Obj inst) {
            return rec(uri(DOM), inst.dom(),
                    uri(RNG), inst.rng(),
                    uri(ARGS), inst.asInst().args(),
                    uri(TID), uri(inst.tid()),
                    uri(VID), null == inst.vid() ? noobj() : uri(inst.vid()));
        }

        /**
         * One field of {@link #descriptor(Obj)}, selected by an rshift key ({@code inst>>rng}). A
         * noobj key yields the whole descriptor and an unknown key yields noobj — {@code >>} is a
         * projection, not an assertion, so a bad key must not throw.
         */
        public static Obj project(final Obj inst, final Obj key) {
            if (null == key || key.isNoObj())
                return descriptor(inst);
            if (!key.isUri())
                return noobj();
            return switch (key.uriValue().name()) {
                case DOM -> inst.dom();
                case RNG -> inst.rng();
                case ARGS -> inst.asInst().args();
                case TID -> uri(inst.tid());
                case VID -> null == inst.vid() ? noobj() : uri(inst.vid());
                default -> noobj();
            };
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
            this.func = ObjmtronSerializer.parse(func);
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

        @Override
        public int hashCode() {
            return Objects.hash(this.bi, this.func);
        }

        @Override
        public boolean equals(final Object other) {
            return other != null && (other.hashCode() == this.hashCode());
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

        public static Set<Inst> insts() {
            return new LinkedHashSet<>(List.of(
                    instC(ARGS_INST_TID.dom(M_ISA_INST_TID).rng(LST_TID), lst(), (lhs, inst) -> inst.args()),
                    instC(AS_INST_TID.dom(INST_TID).rng(CODE_TID), lst(CODE_TYPE), (lhs, inst) -> code(List.of(lhs.asInst())))));
            //instC(LSHIFT_INST_TID.dom(INST_TID).rng(ALL), lst(), (lhs, inst) -> lhs.dom()),
                    /*instC(RSHIFT_INST_TID.dom(INST_TID).rng(ALL.maybeSome()), lst(T(URI_TID.maybeSome())), (lhs, inst) -> objs(inst.arg(0).orElse((Obj) uri(ONE_WILD_STRING)).stream().map(u ->
                            rec(uri(ARGS), lhs.asInst().args(),
                                    uri(DOM), lhs.dom(),
                                    uri(RNG), lhs.rng(),
                                    uri("f"), (lhs.asInst().f() != null && lhs.asInst().f().func instanceof Obj) ?
                                            (Obj) lhs.asInst().f().func :
                                            noobj()).at(u))))));*/
        }
    }
}