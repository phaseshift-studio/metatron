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

import studio.phaseshift.metatron.TypeCheck;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.resolver.InstResolver;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.thread.FutureObj;
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
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.util.Tuple.Triplet;

public interface Inst extends Call {

    public record ArgsFunction(Poly<?, ?> args, f f) {
    }

    fURI ARGS_FURI = fURI.Singleton.f(ARGS);

    /**
     * Cache for fully resolved instructions when ALL arguments are literals (non-call objects).
     * Key: "lhsType|instBasePath|args" - includes lhs type, instruction name, and literal args
     * Value: The fully resolved instruction (safe to reuse since literal args don't depend on lhs)
     * Call args (inst, code, type) are NOT cached because they depend on the lhs value.
     */
    // Map<String, Inst> RESOLUTION_CACHE = new ConcurrentHashMap<>();
    // ThreadLocal<Boolean> REWRITE_MODE = ThreadLocal.withInitial(() -> false);

    // Uri ARGS_URI = uri(ARGS_FURI);
    Type INST_TYPE = Type.Builder.build().tid(M_ISA_INST_TID).vid(M_ISA_INST_TID).create();

    enum Form {
        initial("generates objs from nothing; domain coefficient is zero"),
        terminal("renders objs to nothing; range coefficient is zero"),
        fork("splits objs across parallel streams"),
        join("merges parallel streams of objs into a single stream"),
        reducer("gathers multiple inputs and produces a single output (range coefficient is one)"),
        gather("accepts multiple inputs; domain max coefficient is unbounded"),
        scatter("distributes a single input across multiple outputs (range > 1)"),
        catcher("handles fail objs, intercepting fail propagation"),
        filter("conditionally passes or drops objs (range coefficient is maybe)"),
        mapper("one-to-one obj transformation (domain coefficient is one, range is one)"),
        flatmapper("one-to-many obj transformation (domain coefficient is one, range > 1)"),
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
                // base.equals(AUTO_TID) ||
                base.equals(MAPP_INST_TID) ||
                base.equals(FORK_INST_TID) ||
                base.equals(THREAD_INST_TID) ||
                base.equals(ORDER_INST_TID) ||
                base.equals(AS_INST_TID) ||
                base.equals(WITHIN_INST_TID) ||
                base.equals(ISA_INST_TID) ||
                //base.equals(SELECT_INST_TID) ||
                base.equals(UPDATE_INST_TID) ||
                //base.equals(WHERE_INST_TID) ||
                base.equals(GROUP_INST_TID) ||
                base.equals(REPEAT_INST_TID) ||
                base.equals(ELSE_INST_TID) ||
                base.equals(CATCH_INST_TID);
    }

    @Override
    default Inst resolve(final Obj lhs) {
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
            final Inst resolved = InstResolver.get().resolveInst(lhs, this);
            if (null != resolved) {
                LOG.trace("%s => %s is %s resolved", lhs, resolved, CommonUtil.lambda(() -> resolved.isResolved(false) ? "" : "not"));
                // Cache disabled - see comment above
                /*if (cacheKey != null) {
                    RESOLUTION_CACHE.putIfAbsent(cacheKey, resolved);
                }*/
                return resolved;
            } else {
                LOG.debug("unable to resolve: %s", this);
            }
        } catch (final Exception e) {
            this.logger().error(e);
        }
        // find all other insts of the same name
        // if they all have the same domain coefficient as the lhs obj,
        // then that can be hard coded into the compilation
        Obj resolved2 = Router.readFromSpace(this.tid());
        final List<cInt> uniqueDomains = resolved2.stream().map(v -> v.tid().dom().c()).distinct().toList();
        final Inst domainInst = (uniqueDomains.size() == 1 && uniqueDomains.getFirst().equals(lhs.tid().c())) ? this.dom(lhs.type()) : this;
        this.logger().trace("performing runtime resolution of %s => %s", lhs, domainInst);
        resolved2 = domainInst.hasDomOrRng() ? resolved2.tid(domainInst.tid()) : resolved2;
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
        final boolean isMonadicInst = this.tid().hasQ(MONAD);
        //final String monadUpDown = this.tid().queryValue(fURI.of(MONAD), String.class);
        Obj clhs = lhs;
        //boolean reself = !this.args().isEmpty() && this.args().argElements().noneMatch(e -> e.vid() != null || e.isObjCall());
        Inst cinst = this.resolve(clhs); // TODO: this isn't a general solution (multi slotted args won't work).
        //if (false && reself) // TODO: why do type predicates get rewritten?
        //    this.self(Triplet.with(cinst.args(), cinst.f(), cinst.seed()), cinst.tid(), cinst.vid());
        if (cinst.isNoObj())
            return fail(MTronException.of("unable to locate inst-f of %s", this, clhs));
        if (lhs.isNoObj() && !cinst.dom().c().isZeroable())
            return noobj();
        // return fail(MTronException.of("lhs range does not match inst domain: %s => %s [%s]", clhs.rng(), cinst.dom(), cinst));

        Obj rhs;
        boolean modulateC = false;
        if (TypeCheck.inst_dom.enabled() && !isMonadicInst && !lhs.isFail() && !lhs.isCaughtFail()
                && !instDomRngMatch(clhs, cinst.dom()) && clhs.unique()) {
            // if (clhs.uniqueC().isOne() && !clhs.c().isOne()) { // && cinst.dom().c().within(cInt.SOME())) {
            clhs = clhs.c(cInt::one);
            cinst = this.resolve(clhs);
            modulateC = true;
            //  }
            if (!instDomRngMatch(clhs, cinst.dom()))
                return fail("lhs range does not match inst domain: %s => %s [%s]", clhs.rng(), cinst.dom(), cinst);
        }
        if (!clhs.isFail() || cinst.isCatch()) {
            try {
                if (null == cinst.f()) {
                    if (cinst.tid().basePath().equals(AS_INST_TID)) {
                        cinst = cinst.f(Inst.f.of((x, y) -> x.tid(y.arg(0).vid())));
                    } else
                        throw MTronException.of("unable to determine inst function:" +
                                "\n\t%-10s  => %s   | [inst]" +
                                "\n\t%-10s  => %s   |  \\_dom" +
                                "\n\t%-10s %s=> %s   |  \\_args", clhs, cinst, clhs.type(), cinst.dom(), clhs.type(), cinst.args().elements().allMatch(clhs::test) ? "=" : "X", cinst.args());
                }
                cinst = Helper.applyArgs(clhs, cinst);
                Router.stack().push(cinst.args());
                //Router.stack().push(rec("lhs",clhs));
                try {
                    rhs = Objs.trySingleton(FutureObj.resolveFuture(cinst.f().apply(clhs, cinst)));
                    rhs = null == rhs ? noobj() : rhs;
                    if (rhs.isUncaughtFail())
                        return rhs;
                    Graphitty.log(cinst).trace("%s (lhs) => %s (inst) => %s (rhs) evaluated successfully", clhs, cinst, rhs);
                } catch (final Exception e) {
                    throw MTronException.of(e, "apply failure:" +
                                    "\n\t[lhs]    │ %s" +
                                    "\n\t \\_type  │ %s" +
                                    "\n\t  \\_pred │ %s" +
                                    "\n\t[inst]   │ %s" +
                                    "\n\t \\_dom   │ %s" +
                                    "\n\t \\_args  │ %s",
                            clhs, clhs.tid(), clhs.type().predicateStack(), cinst, cinst.dom(), cinst.args());
                    // e.printStackTrace();
                } finally {
                    Router.stack().pop();
                    //  Router.stack().pop();
                }
            } catch (final Exception e) {
                rhs = fail(e);
            }
            if (TypeCheck.inst_rng.enabled() && !isMonadicInst && !rhs.isType() && !rhs.isFail() && !clhs.isCaughtFail()
                    && !instDomRngMatch(rhs, cinst.rng()))
                //rhs = fail(MTronException.of("inst resolution failure: %s", cinst, fail(MTronException.of("rhs does not match inst range:\n\t%s", Poly.Helper.diffObjRecursion(rhs, cinst.rng())))));
                rhs = fail(MTronException.of("rhs does not match inst range:\n\t%s", Poly.Helper.diffObjRecursion(rhs, cinst.rng())));
        } else {
            rhs = clhs; // propagate fail through inst unless it's a catch inst
        }
        final cInt cc = cinst.c();
        return modulateC ? rhs.c(c -> c.mult(lhs.c()).mult(cc)) : rhs.c(c -> c.mult(cc));
    }

    /**
     * inst_dom/inst_rng dispatch check. Fast-out when the lhs's tid/vid label
     * matches the target type (specificTypeId resolves the coefficient via
     * fURI.test) and the coefficient/poly are consistent — trust construction-time
     * validation. Otherwise fall back to the full structural test. Construction
     * validation is unaffected (it routes through testObjs directly).
     */
    private static boolean instDomRngMatch(final Obj lhs, final Type type) {
        if (type.tid().poly().isEmpty()
                && Obj.Helper.specificTypeId(lhs).test(Obj.Helper.specificTypeId(type))
                && lhs.c().within(type.c()))
            return true;
        return lhs.test(type);
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
        return this.clone(this.jvm(), tid, this.vid());
    }

    final class Helper {
        private Helper() {
            // do nothing
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
                                    if (null == r)
                                        return null;
                                    // Allow template expansion: if arg is Uri or Str with templates, always return expanded result
                                    final boolean isTemplateExpansion = (arg.isUri() && arg.asUri().hasTemplates()) ||
                                            (arg.isStr() && (arg.strValue().contains("${") || arg.strValue().contains("{{{")));
                                    if (!arg.isObjCall() && !isTemplateExpansion && !r.test(arg)) {
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
                                    kv.getValue().apply(lhs))))
                            .plus(rec(LHS, lhs));
            final Inst resolved = inst.args(cargs);
            //  LOG.trace("resolution ({{m}}%s {{g}}=>{{/g}} %s{{/m}}): %s => %s", currentResolution, resolved.resolution(), lhs, resolved);
            return resolved;
        }

        public static Inst bindGenerics(final Obj lhs, final Inst apiInst, final Obj userInst) {
            final GraphittyLogger LOG = Graphitty.log(lhs);
            final Map<fURI, fURI> generics = new HashMap<>();
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
            if (apiInstTemp.dom().tid().one().isGeneric()) {
                apiInstTemp = apiInstTemp.dom(lhs.type().c(apiInstTemp.dom().c()).as());
                apiInstTemp = apiInstTemp.tid(Helper.apiOrUser(apiInstTemp.tid(), userInst.tid(), generics));
            }
            LOG.trace("generic specification mapped %s => %s to %s via %s", lhs, userInst, apiInstTemp, apiInst);
            return apiInstTemp;
        }

        private static fURI apiOrUser(final fURI apiInstTid, final fURI userInstTid, final Map<fURI, fURI> bindings) {
            fURI result = apiInstTid;
            if (userInstTid.hasDom()) {
                if (apiInstTid.dom().one().isGeneric())
                    bindings.put(apiInstTid.dom().one(), userInstTid.dom().one());
                result = result.dom(userInstTid.dom());

            } else if (apiInstTid.dom().one().isGeneric()) {
                result = result.dom(bindings.getOrDefault(apiInstTid.dom().one(), apiInstTid.dom())).c(apiInstTid.dom().c());
            }
            /// /////
            if (userInstTid.hasRng()) {
                if (apiInstTid.rng().one().isGeneric())
                    bindings.put(apiInstTid.rng().one(), userInstTid.rng().one());
                result = result.rng(userInstTid.rng());

            } else if (apiInstTid.rng().one().isGeneric()) {
                result = result.rng(bindings.getOrDefault(apiInstTid.rng().one(), apiInstTid.rng())).c(apiInstTid.dom().c());
            } else if (result.dom().one().isGeneric()) {
                result = result.dom(bindings.getOrDefault(result.dom().one(), result.dom())).c(result.dom().c());
            }
            return result;
        }

        /**
         * An {@code as}-graph finding: the offending instructions, the violation kind, and a human-readable reason.
         */
        public record Violation(List<Inst> insts, Type type, String reason) {

            public enum Type {
                /**
                 * two {@code as} instructions cast the same dom to the same rng — a redundant mapping
                 */
                DUPLICATE("two `as` instructions cast the same dom to the same rng — a redundant mapping:  f, g : A → B,  f ≠ g"),
                /**
                 * same-rng doms are incomparable yet overlap — a future input could match both with no most-specific winner
                 */
                AMBIGUOUS("same-rng doms are incomparable yet overlap — a future input could match both with no most-specific winner:  ¬(A ≤ B) ∧ ¬(B ≤ A) ∧ ∃T. T ≤ A ∧ T ≤ B"),
                /**
                 * same-rng doms are incomparable and disjoint — no input matches both, so dispatch stays total
                 */
                INCOMPARABLE("same-rng doms are incomparable and disjoint — no input matches both, so dispatch stays total:  ¬(A ≤ B) ∧ ¬(B ≤ A) ∧ ∄T. T ≤ A ∧ T ≤ B"),
                /**
                 * two types are directly mutually castable — a pair of opposing casts, a candidate isomorphism/retraction
                 */
                COUPLING("two types are directly mutually castable (A ⇄ B) — a pair of opposing casts, a candidate isomorphism/retraction:  f : A → B,  g : B → A"),
                /**
                 * two types are linked by a chain of couplings — connected in the reversible core of the as-graph
                 */
                ISOCHAIN("two types are linked by a chain of couplings (A ⇄ … ⇄ B) — connected in the reversible core G∩G⁻¹, each hop an opposing pair:  a chain of candidate-isomorphisms"),
                /**
                 * two types are mutually reachable — the round-trip is an idempotent, so each is a retract of the other
                 */
                RETRACT("two types are mutually reachable (A ⇒ B ∧ B ⇒ A) — the round-trip A⇒B⇒A is an idempotent, so A ≅ im(e), a subobject of A, not necessarily A itself:  e = A⇒B⇒A,  e∘e = e,  A ≅ { a ∈ A : e(a) = a }");

                private final String description;

                Type(final String description) {
                    this.description = description;
                }

                public String description() {
                    return this.description;
                }
            }
        }

        /**
         * Checks the {@code as}-graph for the requested {@link Violation.Type}s.
         * <p>
         * Dispatch resolves {@code as} by matching the input against each candidate's dom and choosing the
         * most-specific (most-refined) dom that accepts it. The full check exposes four layers of potential
         * ambiguity, from cheap syntactic facts up to graph reachability:
         * <ol>
         *   <li>{@link Violation.Type#DUPLICATE} — two {@code as} instructions sharing the same dom and rng;</li>
         *   <li>{@link Violation.Type#AMBIGUOUS} / {@link Violation.Type#INCOMPARABLE} — same-rng doms that are
         *   neither {@code A ≤ B} nor {@code B ≤ A}, split by whether they overlap;</li>
         *   <li>{@link Violation.Type#COUPLING} / {@link Violation.Type#ISOCHAIN} / {@link Violation.Type#RETRACT}
         *   — mutually castable type pairs (reversibility at three strengths).</li>
         * </ol>
         * Refinement ({@code A ≤ B}) is {@code A.testNominally(B) && A.c().within(B.c())} — predicate-blind, so it
         * certifies dispatch, not type-checking.
         *
         * @param types the violation kinds to check (empty means all)
         * @return the violations found, each at its tightest {@link Violation.Type}
         */
        public static Set<Violation> checkAsGraph(final Violation.Type... types) {
            final Set<Violation> violations = new HashSet<>();
            if (!Router.loaded())
                return violations;
            final Set<Violation.Type> requested = 0 == types.length
                    ? EnumSet.allOf(Violation.Type.class)
                    : EnumSet.copyOf(Arrays.asList(types));
            final List<Inst> asInsts = asInsts();
            if (asInsts.isEmpty())
                return violations;
            // same-rng ambiguity (DUPLICATE / AMBIGUOUS / INCOMPARABLE)
            if (requested.contains(Violation.Type.DUPLICATE) || requested.contains(Violation.Type.AMBIGUOUS) || requested.contains(Violation.Type.INCOMPARABLE)) {
                // undirected type graph of every non-identity as edge (dom <-> rng), keyed by basePath — used to
                // decide whether two same-base sibling doms "capture the same values" (reachable via a cross-over path)
                final Map<fURI, Set<fURI>> graph = new HashMap<>();
                for (final Inst inst : asInsts) {
                    final fURI dom = inst.tid().dom().basePath();
                    final fURI rng = inst.tid().rng().basePath();
                    graph.computeIfAbsent(dom, k -> new HashSet<>()).add(rng);
                    graph.computeIfAbsent(rng, k -> new HashSet<>()).add(dom);
                }
                final Map<fURI, List<Inst>> byRng = new LinkedHashMap<>();
                for (final Inst inst : asInsts)
                    byRng.computeIfAbsent(inst.tid().rng(), k -> new ArrayList<>()).add(inst);
                for (final List<Inst> group : byRng.values()) {
                    for (int i = 0; i < group.size(); i++) {
                        for (int j = i + 1; j < group.size(); j++) {
                            final Type domA = group.get(i).dom();
                            final Type domB = group.get(j).dom();
                            final Type rngType = group.get(i).rng();
                            if (group.get(i).tid().dom().equals(group.get(j).tid().dom())) {
                                if (requested.contains(Violation.Type.DUPLICATE))
                                    violations.add(new Violation(group, Violation.Type.DUPLICATE, "duplicate as: dom " + group.get(i).dom() + " -> rng " + rngType.namedType() + " appears twice"));
                            } else if (incomparable(domA, domB)) {
                                final boolean sameBase = domA.tid().basePath().equals(domB.tid().basePath());
                                final boolean overlapping = sameBase && reaches(graph, group.get(i).tid().dom().basePath(), group.get(j).tid().dom().basePath());
                                final Violation.Type type = overlapping ? Violation.Type.AMBIGUOUS : Violation.Type.INCOMPARABLE;
                                if (requested.contains(type))
                                    violations.add(new Violation(group, type,
                                            (overlapping ? "ambiguous" : "incomparable") + " as: dom " + group.get(i).dom() + " vs dom " + group.get(j).dom() + " for rng " + rngType.namedType()));
                            }
                        }
                    }
                }
            }
            // reversible/retract graph relations (COUPLING / ISOCHAIN / RETRACT)
            if (requested.contains(Violation.Type.COUPLING) || requested.contains(Violation.Type.ISOCHAIN) || requested.contains(Violation.Type.RETRACT))
                violations.addAll(retracts(asInsts, requested));
            return violations;
        }

        private static List<Inst> asInsts() {
            return Router.readFromSpace(AS_INST_TID).stream()
                    .filter(Obj::isObjInst)
                    .map(Obj::asInst)
                    .filter(inst -> !inst.tid().dom().equals(inst.tid().rng())) // identity casts (as?X<=X) are trivially true — not part of the graph
                    .sorted(Comparator.comparing(inst -> inst.tid().toString()))
                    .toList();
        }

        private static Set<Violation> retracts(final List<Inst> asInsts, final Set<Violation.Type> requested) {
            final Set<Violation> retracts = new HashSet<>();
            // directed type graph G (dom -> rng), keyed by basePath
            final Map<fURI, Set<fURI>> graph = new HashMap<>();
            for (final Inst inst : asInsts)
                graph.computeIfAbsent(inst.tid().dom().basePath(), k -> new HashSet<>()).add(inst.tid().rng().basePath());
            // reversible core G ∩ G⁻¹ — the undirected edges that exist in both directions
            final Map<fURI, Set<fURI>> reversible = new HashMap<>();
            for (final Map.Entry<fURI, Set<fURI>> edge : graph.entrySet()) {
                final fURI dom = edge.getKey();
                for (final fURI rng : edge.getValue()) {
                    if (graph.getOrDefault(rng, Set.of()).contains(dom)) {
                        reversible.computeIfAbsent(dom, k -> new HashSet<>()).add(rng);
                        reversible.computeIfAbsent(rng, k -> new HashSet<>()).add(dom);
                    }
                }
            }
            final List<fURI> nodes = new ArrayList<>(graph.keySet());
            nodes.sort(Comparator.comparing(fURI::toString));
            for (int i = 0; i < nodes.size(); i++) {
                for (int j = i + 1; j < nodes.size(); j++) {
                    final fURI a = nodes.get(i);
                    final fURI b = nodes.get(j);
                    final Violation.Type type;
                    if (graph.getOrDefault(a, Set.of()).contains(b) && graph.getOrDefault(b, Set.of()).contains(a))
                        type = Violation.Type.COUPLING;                              // direct opposing pair
                    else if (reaches(reversible, a, b))
                        type = Violation.Type.ISOCHAIN;                              // chain of opposing pairs
                    else if (reaches(graph, a, b) && reaches(graph, b, a))
                        type = Violation.Type.RETRACT;                               // mutual reachability only
                    else
                        continue;
                    if (requested.contains(type))
                        retracts.add(new Violation(List.of(), type, typeName(a) + " ⇄ " + typeName(b)));
                }
            }
            return retracts;
        }

        /**
         * Manifests the implicit {@code as} instructions that arise from the subtype hierarchy of the
         * types already present in the explicit as-graph.
         * <p>
         * A type {@code T} that is a nominal or structural refinement of {@code A} ({@code T.test(A)} holds)
         * needs no explicit {@code as?A<=T}: the cast is just the removal of the constraint that
         * distinguishes {@code T} from {@code A}, realized by re-tagging the value with {@code A}'s vid. Only
         * direct refinements among the harvestable types are emitted — sibling subtypes (e.g. {@code json} vs
         * {@code yaml}) and unrelated base types never satisfy {@code test}. Generics ({@code A}, {@code B})
         * are per-instruction bindings and are excluded, as are the root and {@code noobj} types.
         *
         * @return the manifested implicit {@code as} instructions (empty when nothing is registered)
         */
        public static List<Inst> implicitAsGraph() {
            final List<Inst> implicit = new ArrayList<>();
            if (!Router.loaded())
                return implicit;
            final List<Type> types = Router.readFromSpace(AS_INST_TID).stream()
                    .filter(Obj::isObjInst)
                    .map(Obj::asInst)
                    .flatMap(inst -> List.of(inst.dom().vid(), inst.rng().vid()).stream())
                    .map(fURI::basePath)
                    .distinct()
                    .sorted(Comparator.comparing(fURI::toString))
                    .map(vid -> T(vid))
                    .filter(t -> !t.isGeneric() && !t.isRootType() && !t.vid().basePath().equals(NOOBJ_TID))
                    .toList();
            for (final Type src : types) {
                for (final Type dst : types) {
                    if (src.vid().equals(dst.vid()))
                        continue;
                    if (src.test(dst))
                        implicit.add(instC(AS_INST_TID.dom(src.vid()).rng(dst.vid()), lst(dst), (lhs, inst) -> lhs.vid(inst.arg(0).vid())));
                }
            }
            return implicit;
        }

        private static boolean incomparable(final Type a, final Type b) {
            if (a.isRootType() || a.isGeneric() || b.isRootType() || b.isGeneric())
                return false;
            return !refines(a, b) && !refines(b, a);
        }

        private static boolean refines(final Type a, final Type b) {
            return a.testNominally(b) && a.c().within(b.c());
        }

        private static boolean reaches(final Map<fURI, Set<fURI>> graph, final fURI a, final fURI b) {
            final Set<fURI> seen = new HashSet<>();
            final ArrayDeque<fURI> stack = new ArrayDeque<>();
            stack.push(a);
            while (!stack.isEmpty()) {
                final fURI node = stack.pop();
                if (node.equals(b))
                    return true;
                if (!seen.add(node))
                    continue;
                graph.getOrDefault(node, Set.of()).forEach(stack::push);
            }
            return false;
        }

        private static String typeName(final fURI f) {
            final cInt c = f.c();
            return f.name() + (null == c || c.isOne() ? "" : "{" + c + "}");
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
            return new LinkedHashSet<>(List.of(instC(ARGS_INST_TID.dom(M_ISA_INST_TID).rng(LST_TID), lst(), (lhs, inst) -> inst.args())));
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