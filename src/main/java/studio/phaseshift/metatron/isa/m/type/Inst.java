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

import studio.phaseshift.metatron.algebra.Law;
import studio.phaseshift.metatron.algebra.PendingWrites;
import studio.phaseshift.metatron.algebra.TheoryHelper;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.Network;
import studio.phaseshift.metatron.isa.m.mach.type.Processor;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.Binder;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.Resolver;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.Selector;
import studio.phaseshift.metatron.isa.m.sys.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.m.sys.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.isa.m.type.impl.InstBuilder;
import studio.phaseshift.metatron.isa.m.type.impl.MCode;
import studio.phaseshift.metatron.isa.m.type.impl.MInst;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.mach.machInstSet.CUT_INST_TID;
import static studio.phaseshift.metatron.isa.m.mach.machInstSet.REPLY_INST_TID;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.TIMEOUT_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MCode.code;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
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
        typer("retypes the obj's type/identity, not its value"),
        standard("an instruction with no well-defined classification");

        public final String description;

        Form(final String description) {
            this.description = description;
        }

        public static Form of(final Inst inst) {
            // A DECLARED FORM WINS OVER THE STRUCTURAL ONE. A typer's subject is the OBJECT'S TYPE/IDENTITY, not its
            // value, and no coefficient shape tells it apart from an ordinary type-changing instruction
            // (as?dom=A&rng=B is shaped like any mapper), so it is DECLARED through InstBuilder, never derived.
            final Form declared = InstBuilder.declaredForm(inst.tid());
            if (null != declared)
                return declared;
            if (inst.isInitial())
                return initial;
            if (inst.isTerminal())
                return terminal;
            if (inst.isBranching())
                return fork;
            // MERGE IS A FLATMAPPER (Marko, 2026-11-18): it unrolls the lst a split built (or any lst) into the objs its
            // indices hold, one obj to many, so a pure merge floats with a run like any flatmapper
            if (inst.isJoining())
                return flatmapper;
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

        /**
         * Project this form onto the stream-ring's function subrings — the article's five sets
         * {@code F_m ∪ F_f ∪ F_fm ∪ F_r ∪ F_b}, the smaller set the rewrite algebra operates on. A form
         * already in the subrings is its own image; the structural near-misses collapse onto their set
         * ({@code scatter} is the linearized one-to-many = {@code flatmapper}; {@code join} is the
         * many-to-many merge = {@code gather}); a form with no subring (the stream endpoints, the {@code fork}
         * split, error recovery, unclassified) is its own image — it lies outside {@code F}.
         *
         * <p>{@code fork} stays its own image: it is the structural fan-out of {@code +} into parallel streams,
         * not a data-transforming flatmap, so a coefficient does not float through it. {@code gather} is the
         * code's name for the barrier near-ring {@code F_b} ({@code X* → Y*}), so {@code join} and
         * {@code gather} share one image — no separate {@code barrier} form is needed. {@code typer} too is its
         * own image and lies outside {@code F}: it retypes the ONE obj, so it is neither a leg of the replicable
         * run nor a cut that drains one — the rewriter must not hoist it across a coefficient change or merge two
         * of them.
         */
        public Form subring() {
            return switch (this) {
                case scatter -> flatmapper;
                case join -> gather;
                default -> this;
            };
        }

        /**
         * Thm 6 — the universal functional commutativity of coefficients holds across this form: the
         * coefficient floats through it ({@code ca = a·c}). The replicable run, {@code F_m ∪ F_f ∪ F_fm}.
         * {@code typer} is excluded: {@code ca.as(B) ≠ a.as(B)·c}, since {@code as} decides what the value IS.
         */
        public boolean isFloatable() {
            return switch (this.subring()) {
                case mapper, filter, flatmapper -> true;
                default -> false;
            };
        }

        /**
         * Thm 17 — the coefficient cannot commute past this form: a reduce/gather drains the stream and
         * anchors the coefficient in place ({@code c1·1a ≠ 1a·c1}). The cut, {@code F_r ∪ F_b}.
         * {@code typer} is excluded: it neither floats nor drains, and two typers never merge.
         */
        public boolean isBlocked() {
            return switch (this.subring()) {
                case reducer, gather -> true;
                default -> false;
            };
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

    /**
     * The instruction's argument POLY — a positionally ordered {@code lst} or a named {@code rec}.
     * <p>
     * ONE REPRESENTATION FOR "NO ARGUMENTS": an inst minted with a null argument slot reads an EMPTY lst. It reads
     * {@code lst_noobj()} today — a one-element lst holding {@code noobj} — which is not "no arguments" at any call
     * site that asks: {@code isEmpty()} answers false for an instruction that takes none, {@code fullSignature} reads
     * the noobj slot as an unreadable argument and claims nothing, and two instructions that agree on everything
     * compare unequal (a parsed call carries an empty lst, a sugar-minted one carries the noobj wrapper).
     */
    default Poly<?, ?> args() {
        final Poly<?, ?> args = this.jvm().get0();
        return null == args ? lst() : args;
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
     * Returns true if this instruction's function is a native Java lambda ({@link #isJavaFunction()}), or if one of its
     * arguments is an instruction or a code that contains one, at any depth. A chain-built instruction may carry no
     * function of its own while its argument does (a status is {@code auto_(instLambda(...))}), so
     * {@code isJavaFunction()} implies {@code containsJavaFunction()}, never the reverse.
     */
    @Override
    default boolean containsJavaFunction() {
        return this.isJavaFunction() || this.args().elements().anyMatch(arg -> arg.isObjInst()
                ? arg.asInst().containsJavaFunction()
                : arg.isCode() && arg.asCode().containsJavaFunction());
    }

    /**
     * PURE, DECLARED OR DERIVED ({@link Law#pure}): the result is a function of the lhs and the args alone, and applying
     * the instruction changes nothing else. The instruction itself answers in three ways:
     * <ul>
     *   <li>it names a contract that declares its laws beside itself ({@code ?decq}): pure exactly when that contract
     *       declares {@code pure}, and a Java body is opaque, so its purity is only ever declared;</li>
     *   <li>it declares nothing and has an mtron body: pure when that body is, because a composition of pure
     *       instructions is pure;</li>
     *   <li>it names no contract (a bare instruction, before resolution): pure when every contract its op declares is,
     *       since dispatch may land on any of them; an undeclared op fails closed.</li>
     * </ul>
     * Either way every argument must be pure too ({@link Helper#isPure(Obj)}): an instruction that runs code is only as
     * pure as that code, so {@code is(gt(1))} is pure and {@code is(to(x).gt(1))} is not.
     */
    @Override
    default boolean isPure() {
        final Rec declared = Law.Helper.lawsOf(this.tid());
        final Obj body = this.getMtronFunctionObj();
        // noobj is a call too (the code ring's zero) and has no body of its own, so only a body that is there is read
        final boolean itself = !declared.isNoObj() ? Law.Helper.declares(declared, Law.pure)
                : !body.isNoObj() && body.isCall() ? body.<Call>as().isPure()
                : Law.Helper.declaresAtEveryContract(this.tid(), Law.pure);
        return itself && this.args().elements().allMatch(Helper::isPure);
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

    /**
     * Whether this instruction is bound to a body, and with {@code nested}, every instruction in its arguments too.
     * A BLOCKING INSTRUCTION'S ARGUMENTS ARE NOT THIS RESOLUTION'S: it applies them itself, where and when they run (a
     * cut's tail in the machine it lands in, a branch's lanes at their places, a profile's program on each timed run),
     * so it is resolved once it is bound itself, and the console reports no dynamic resolution for them.
     */
    default boolean isResolved(final boolean nested) {
        final boolean resolved = this.hasf();
        return !nested || !resolved || this.isBlocking()
                ? resolved
                : this.<Inst>as().args().elements().allMatch(c -> c.isResolved(true));
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
                //   select:                  rec arg whose VALUES are per-element projections
                base.equals(AUTO_INST_TID) ||
                base.equals(CUT_INST_TID) ||
                base.equals(REPLY_INST_TID) ||
                // the anchor stores the SHIPPED PROGRAM as arg(0) and evaluates it in its function: applying
                // it here would evaluate the payload before the anchor could wrap its answer (and a second
                // time after), which is the double apply the mtron frame handler documents
                base.equals(AUTO_FROM_INST_TID) ||
                base.equals(AUTO_AT_INST_TID) ||
                // a profile's minted instruction carries the program it times as its argument and runs it itself, at
                // home (profileTable): applied here, the program ran once more, untimed and unscoped, and a machine
                // change in it left the caller in another machine
                base.equals(PROFILE_ANALYSIS_TID) ||
                base.equals(PROFILE_COLLATE_TID) ||
                // higher order instructions are intended to have code that operates on lhs structures.
                // thus, their args should not pre-process the structure (their args should operate in the inst body)
                base.equals(MAP_INST_TID) ||
                base.equals(FILTER_INST_TID) ||
                base.equals(ORDER_INST_TID) ||
                base.equals(KMERGE_INST_TID) ||
                base.equals(ISA_INST_TID) ||
                // args are projections and should operate in the inst body.
                base.equals(SUP_INST_TID) ||
                base.equals(INF_INST_TID) ||
                base.equals(SELECT_INST_TID) ||
                base.equals(FORK_INST_TID) ||
                base.equals(UPDATE_INST_TID) ||
                base.equals(GROUP_INST_TID) ||
                ////////////////
                base.equals(AS_INST_TID) ||
                base.equals(WITHIN_INST_TID) ||
                base.equals(SORTA_INST_TID) ||
                base.equals(REPEAT_INST_TID) ||
                base.equals(TIMEOUT_INST_TID) ||
                base.equals(BRANCH_INST_TID) ||
                // a partition applies its function and runs its lanes itself, one member at a time
                base.equals(PARTITION_INST_TID) ||
                base.equals(ELSE_INST_TID) ||
                base.equals(CATCH_INST_TID);
    }

    /**
     * A SPAN OF ZERO IS id() (Marko, 2026-11-16), AND A ZERO POINT ANNIHILATES WHATEVER THE SPAN (Marko, 2026-11-17):
     * {@code {n}f{c}} is c·fⁿ, so {@code {0}f} is f⁰, the identity, and {@code {0}f{0}} is 0·f⁰, nothing. An instruction
     * is the zero exactly when its point is. {@link Obj#isNoObj()} tests c(), which is span x point, and read
     * {@code {0}f} as noobj, so a {@code {0}f} built after parsing (a lane narrowed to [0,0]) was taken for nothing.
     */
    @Override
    default boolean isNoObj() {
        return this.tid().d().isZero();
    }

    @Override
    default boolean isAuto() {
        if (this.isNoObj())
            return false;
        final fURI base = this.tid().basePath();
        return base.equals(AUTO_FROM_INST_TID) || base.equals(AUTO_AT_INST_TID) || base.equals(AUTO_INST_TID);
    }

    /**
     * RUNTIME RESOLUTION GOES THROUGH THE MACHINE (Marko, 2026-11-10): the current machine's resolver, its selector and
     * binder ({@code ~/compiler/resolver}), so an instruction resolved at run time meets the strategy its compile did.
     */
    @Override
    default Inst resolve(final Obj lhs) {
        final Resolver resolver = Machine.current().compiler().resolver();
        return this.resolve(lhs, resolver.selector(), resolver.binder());
    }

    /**
     * Resolve against an explicit {@link Selector} and {@link Binder}: one step of a resolver's walk, which hands each
     * instruction its own two sub-stages.
     */
    default Inst resolve(final Obj lhs, final Selector sel, final Binder binder) {
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

        Inst selected = null;
        try {
            selected = sel.apply(lhs, this, binder);
            if (null == selected)
                LOG.debug("unable to resolve: %s", this);
        } catch (final Exception e) {
            this.logger().error(e);
        }
        if (null != selected) {
            final Inst resolved = selected;
            LOG.trace("%s => %s is %s resolved", lhs, resolved, CommonUtil.lambda(() -> resolved.isResolved(false) ? "" : "not"));
            // Cache disabled - see comment above
            /*if (cacheKey != null) {
                RESOLUTION_CACHE.putIfAbsent(cacheKey, resolved);
            }*/
            // A PEER'S INSTRUCTION BINDS HERE, AT COMPILE TIME: a from/to whose address names a dialed peer that
            // overrides it keeps this resolution's signature and binding, with f() swapped for the peer's body. It binds
            // OUTSIDE THE SELECTOR'S CATCH (Marko, 2026-11-26: "the user should know that their expression fails"): a
            // selector that cannot choose falls back below, but an address no peer answers is a refusal, not a miss, so
            // it leaves resolve() and the caller's code answers it as a fail::T -- caught, it ran the native from, which
            // read nothing here and answered as if nothing were there
            final Inst bound = Network.Helper.atPeer(resolved);
            // the call's own query rides onto the contract, the contract's keys winning — set on a COPY (tid
            // clones), never written into the contract: a definition without a vid is bound as itself
            final Map<String, String> query = new HashMap<>(this.tid().qMap());
            query.putAll(bound.tid().qMap());
            return bound.tid(bound.tid().q(query));
        }
        // find all other insts of the same name
        // if they all have the same domain coefficient as the lhs obj,
        // then that can be hard coded into the compilation
        Obj resolved2 = Machine.read(this.tid());
        // re-typed and re-argued below: an unaddressed copy, or the re-typed definition writes itself back
        if (resolved2.isObjInst()) {
            resolved2 = Helper.unaddressed(resolved2.asInst());
            // a declared domain is a contract: this fallback adapts a coefficient or binds an undeclared domain, but
            // it never re-types a declared one to an lhs that domain refuses ("abc".band on an int band)
            if (!Helper.admitsIgnoringCoefficient(resolved2.asInst(), lhs))
                return noobj();
        }
        final List<cInt> uniqueDomains = resolved2.stream().map(v -> v.tid().dom().c()).distinct().toList();
        final Inst domainInst = (uniqueDomains.size() == 1 && uniqueDomains.getFirst().equals(lhs.tid().c())) ? this.dom(lhs.type()) : this;
        this.logger().trace("performing runtime resolution of %s => %s", lhs, domainInst);
        resolved2 = domainInst.hasDomOrRng() ? resolved2.tid(domainInst.tid()) : resolved2;
        // the call's own query rides onto the definition read here, the definition's keys winning — set on a COPY
        // (tid clones): unaddressed() hands back a vid-less definition AS ITSELF, so writing the query into it in
        // place (selfTID) left one call's ?hop on the stored sum, and every later sum resolved to that budget
        final Map<String, String> query = new HashMap<>(this.tid().qMap());
        query.putAll(resolved2.tid().qMap());
        resolved2 = resolved2.tid(resolved2.tid().q(query));
        if (resolved2.isNoObj()) {
            LOG.debug("%s could not be resolved in any space", domainInst);
            return noobj();
        } else if (!resolved2.isObjInst()) {
            LOG.debug("unable to resolve %s to a single inst in %s", domainInst, resolved2);
            final Poly args = Helper.resolveArgs(domainInst, domainInst, lhs);
            return null == args ? domainInst : domainInst.args(args);
        } else {
            LOG.debug("resolved %s from global router", resolved2);
            // the closest match relaxes kind and type, but nothing fills a slot that takes something: the selector's
            // binder refuses a zero operand, and attaching it here would hand the definition the zero's payload
            if (!Helper.argsFillSlots(resolved2.asInst(), domainInst))
                return noobj();
            // A NAMED ARG WRAPPER STILL TAKES AN ORDERED CALL: when the definition declares its parameters by name
            // ({@code (amt=>?real::T)}) and the caller provides no keys ({@code f(5.6)}), the parameters are bound in
            // declaration order — {@code [amt=>5.6]} — the same binding the selector's binder performs. Attaching
            // the caller's list verbatim would leave the definition's own parameter names unbound, so a body that
            // reads one by name ({@code *amt}) would find nothing.
            final Poly callArgs = resolved2.asInst().args().isRec()
                    ? Helper.resolveArgs(domainInst, resolved2.asInst(), lhs)
                    : domainInst.args();
            if (null == callArgs)
                return noobj();
            // the call's span and point ride on as written: c(count) would fold a span into the point
            final Inst argued = resolved2.<Inst>as().args(callArgs);
            final Inst resolve2 = argued.tid(argued.tid().b(domainInst.tid().b()).d(domainInst.tid().d()));
            return resolve2.hasRng() ? resolve2 : resolve2.rng(T(ALL_STAR));
        }
    }

    @Override
    default boolean test(final Obj other) {
        // AN INST TESTS A CODE AS THE INSTRUCTION THE CODE IS, and fails when the code is not one
        if (other.isCode()) {
            final Call inst = other.asCode().tryToInst();
            return inst.isObjInst() && this.test(inst);
        }
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
            if (this.args().isLst() && otherInst.args().isLst() && otherInst.args().lstValue().stream().anyMatch(arg -> !arg.tid().b().isOne())) {
                // AN ARG'S SPAN IS A RUN OF ARGS (Marko 11-08: plus({2}str::T) is plus(str::T,str::T)), laid over the
                // args as a lst type's entries are, but closed: an instruction's arity is its own, so every arg is claimed
                if (!Type.Helper.runs(this.args().lstValue(), otherInst.args().lstValue()).contains(this.args().lstValue().size()))
                    return false;
            } else {
                final int maxArgs = (int) Math.max(this.args().count(), otherInst.args().count());
                for (int i = 0; i < maxArgs; i++) {
                    final Obj aArg = this.arg(i);
                    final Obj bArg = otherInst.arg(i);
                    if (!aArg.test(bArg))
                        return false;
                }
            }
            if (this.hasf() && otherInst.hasf())
                return Objects.equals(this.f(), otherInst.f());
            return true;
        }
    }

    @Override
    default Obj apply(final Obj lhs) {
        // [Compiler] resolution + [Processor] computeArgs + f().apply + runtime check/coefficient
        // all live on the machine now (Processor.Helper); the type layer's apply is a handoff. A SPANNED instruction
        // is the sum of its runs (Helper.runs), and each step of a run is that handoff
        final cInt span = this.tid().b();
        return span.isOne() || null == span.min() ? Processor.Helper.apply(lhs, this) : Helper.runs(lhs, this);
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
        return this.dom().c().isOne() && this.rng().c().max() == null;   // one → {0,}
    }

    default boolean isInitial() {
        return this.dom().c().isZero();// || this.dom().typeId().coefficientValue().isQuestion();
    }

    default boolean isFilter() {
        return this.rng().c().lte(this.dom().c()) && this.rng().c().isMaybe() && this.dom().typeId().basePath().equals(this.rng().tid().basePath());
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

    /**
     * The instruction's {@link Form} — the declared facet when the operation declared one (a {@code typer}),
     * otherwise the form its n-tid coefficient shape derives.
     */
    default Form form() {
        return Form.of(this);
    }

    @Override
    default Inst tid(final fURI tid) {
        return this.clone(this.jvm(), tid/*tid.rng(this.tid().rng().c(c -> c.mult(tid.c())))*/, this.vid());
    }

    final class Helper {

        /**
         * Two chain elements are the same operation, whatever their operands — the guard for a law that
         * relates an operation to itself (poset comparison composition).
         */
        public static boolean sameOp(final Inst a, final Inst b) {
            return a.tid().basePath().equals(b.tid().basePath());
        }

        /**
         * Two chain elements are the same operation AND the same operands, as {b}inst{d} instructions (Marko,
         * 2026-11-10): the span b is part of the function, so {@code {?}plus(1)} is not {@code plus(1)}. The point d is
         * not: it is the multiplicity the like-term law sums, so {@code plus{4}(2)} and {@code plus(2)} are like terms.
         */
        public static boolean same(final Inst a, final Inst b) {
            return sameOp(a, b) && a.tid().b().equals(b.tid().b()) && a.args().equals(b.args());
        }

        private Helper() {
            // do nothing
        }

        public static Inst idInst(final fURI vid) {
            return MInst.instC(ID_INST_TID, lst(), lhs -> lhs).selfVID(vid).as();
        }

        /**
         * THE FULL SIGNATURE — the inst with its declared argument types written into its tid ({@code args=[...]}, or
         * the bare {@code args} key when it declares none), so its contract is complete: {@code A x B -> C}.
         * <p>
         * EXPLICIT AND FLAG-FREE: it applies {@code MInst.signature}'s rule regardless of
         * {@code MInst.ARGS_IN_TID}, which is the mint-time convenience switch — so a caller can complete a signature
         * where it matters without encoding every tid in the VM. Idempotent: an inst whose tid already carries
         * {@code args} is returned as it stands, and an inst with no argument Poly states no claim and is unchanged.
         *
         * @param inst the instruction to complete
         * @return the inst carrying its declared argument types, or {@code inst} when it claims no arguments
         */
        /**
         * THE OPERATION'S ADDRESS — the tid with its {@code args} component removed. The component states the DECLARED
         * argument types (a rendering of the args poly), and an instruction's IDENTITY is its operation: matching,
         * equality and hashing compare through this, while the contract itself is read from {@code args()}. A tid
         * that declares no arguments answers itself, so the call is free with {@code MInst.ARGS_IN_TID} off.
         *
         * @param tid the instruction's tid
         * @return the tid without its {@code args} component
         */
        public static fURI operationTid(final fURI tid) {
            return null == tid || !tid.hasQ(ARGS) ? tid : tid.removeQ(ARGS);
        }

        /**
         * A SPAN IS A RUN, COMPUTED, NEVER WRITTEN INTO THE CODE (Marko, 2026-11-10), AND WALKED ONCE (Marko,
         * 2026-11-15): {@code {m,n}inst} is the sum of the runs of lengths m to n of the inst without its span, each
         * run one step past the last, so a step runs once however many runs share it and {@code {1,1000}f} costs a
         * thousand steps. The zero-step run is the lhs, {@code {2,3}plus(1)} at 1 is {@code {3,4}}, and an exact span is
         * the same walk with one run kept. The code keeps the span, so a code pattern still reads {@code {?}neg()} as a
         * run of none or one.
         * <p>
         * A POINT IS PARALLEL, A SPAN SERIAL (Marko, 2026-11-17): {@code {n}a{c}} is c lanes of the n-step run, c·aⁿ,
         * never (ca)ⁿ. {@code 5.{2}plus{3}(1)} is {@code 5.branch({2}plus(1),{2}plus(1),{2}plus(1))}, so {@code {3}7}: the
         * walk steps without the point, and each kept run takes it once, the zero-step run too ({@code 5.{0}plus{3}(1)} is
         * {@code {3}5}, {@code 5.{0,1}plus{3}(1)} is {@code {{3}5,{3}6}}). It is the point as map carries it, floated to
         * the end of the run ({@code map{3}(plus(1).plus(1))}). Only a run of a sum compounds, its every step a branch:
         * {@code 1.{2}branch(plus(0),plus(0))} is {@code {4}1}.
         * <p>
         * A NEGATIVE SPAN RUNS BACKWARD (Marko, 2026-11-13): a run of -k steps is k steps of the instruction's CONVERSE,
         * the relation it states reversed: its dom and rng flipped, the same arguments ({@code plus(2)} backward is
         * {@code minus(2)}, {@code out()} is {@code in()}), as its ?decq claim names it for the contract the lhs
         * dispatches to. An instruction that declares none has no backward run to compute, so that part of the span
         * stays as code on its lhs, {@code start(x).{m,-1}inst}: in limbo.
         */
        public static Obj runs(final Obj lhs, final Inst inst) {
            final cInt span = inst.tid().b();
            final cInt point = inst.tid().d();
            if (point.isZero())
                return noobj();
            final Inst step = inst.tid(inst.tid().b(null).cLess());
            Obj limbo = noobj();
            Obj lanes = noobj();
            final Obj sample = lhs.stream().findFirst().orElse(null);
            if (span.min() < 0 && null != sample) {
                final long nearest = null == span.max() ? 1L : Math.max(1L, -span.max());
                final Inst contract = step.hasf() ? step : Selector.Helper.resolve(sample, step);
                final Obj claim = contract.isNoObj() ? noobj() : Machine.read(PendingWrites.decq(Helper.operationTid(contract.tid())));
                final fURI converse = claim.isRec() ? TheoryHelper.roleAddress(claim.asRec(), CONVERSE) : null;
                // the limbo is BLOCKED: run, it answers itself, so a stream that iterated it would never end
                // (Code.iterator). Its instruction is built as the parser builds one (instB): a retyping clone reads
                // its new tid from memory, and a tid with a negative span is no address that read can take. It keeps
                // the instruction whole, its point too, so the limbo takes the point when it runs, and only then
                if (null == converse)
                    limbo = objs(lhs.stream().map(o -> (Obj) MCode.of(List.of(instB(START_INST_TID, lst(o)),
                            instB(inst.tid().b(cInt.of(span.min(), -nearest)), inst.args())), CODE_TID.addQ(BLOCK), null)));
                else
                    // the converse is declared as its contract (minus?int<=int(int)), and called as any call is
                    // keyed: that operation, with the call's own arguments
                    lanes = walk(lhs, instB(Helper.operationTid(converse), step.args()), nearest, -span.min(), lanes);
            }
            if (null == span.max() || 0L <= span.max())
                lanes = walk(lhs, step, Math.max(0L, span.min()), span.max(), lanes);
            // each kept run takes the point once, as the processor puts an inst's point on its answer
            return objs(Stream.concat(limbo.stream(), pointed(lanes, point).stream()));
        }

        /**
         * AN INSTRUCTION'S POINT ON ITS ANSWER: copies of each obj it answered, as the processor puts an inst's point on
         * its answer (Processor.Helper.apply). The span of a mono value, curled on its tid, stays its span, so such a
         * value takes the point on its own point. A point of one is the answer as it is.
         */
        public static Obj pointed(final Obj answer, final cInt point) {
            return point.isOne() ? answer : objs(answer.stream().map(o ->
                    o.tid().b().isOne() || !o.isMono() ? o.c(c -> c.mult(point)) : o.d(o.tid().d().mult(point))));
        }

        /**
         * The runs of lengths {@code from..to} of one step ({@code to} null: open), walked from the lhs: each run one
         * step past the last, kept from {@code from} on, and appended after what came {@code before} in a FRESH stream.
         * An Objs appends in place, and the zero-step run is the lhs itself, a barrier's bag the next step reads:
         * appended into, {1,2,3}.{0,2}sum() was {1,2,3,6,12}.
         * <p>
         * A RUN THAT LEAVES THE STEP'S DOMAIN HALTS (Marko, 2026-11-15), as out() halts on noobj where a tree's links
         * run out. noobj steps on only into a step whose dom admits {0} (count() counts it, start(x) starts from it),
         * and halts there too when the step answers nothing again. A FAIL IS NO LINK TO FOLLOW: stepped again it answers
         * itself, never noobj, so it ends the walk and surfaces once, even short of the first kept run. A cycle in the
         * data loops, as the expression says.
         */
        private static Obj walk(final Obj lhs, final Inst step, final long from, final Long to, final Obj before) {
            Obj result = before;
            Obj current = lhs;
            for (long k = 0; ; k++) {
                // A WALK IS INTERRUPTIBLE (2026-11-16): a run that never leaves the step's domain walks on inside one
                // monad's apply, where the swarm's STOP check never comes round, so the walk asks once a run whether
                // its thread was interrupted (a machine's stop() interrupts it, as a console ctrl-c does). The flag
                // stays set for the callers above it
                if (Thread.currentThread().isInterrupted())
                    return fail("span walk interrupted at run %s of %s", k, step);
                final boolean kept = k >= from;
                result = objs(Stream.concat(result.stream(), current.stream().filter(o -> kept || o.isUncaughtFail())));
                if ((null != to && k == to) || current.stream().anyMatch(Obj::isUncaughtFail))
                    return result;
                if (current.isNoObj()) {
                    // a step left to run time is resolved against nothing, to say whether it takes it
                    final Inst taker = step.hasDom() ? step : Selector.Helper.resolve(current, step);
                    if (!taker.hasDom() || !taker.dom().c().isZeroable())
                        return result;
                    current = taker.apply(current);
                    if (current.isNoObj())
                        return result;
                } else {
                    // HOW MUCH OF A RUN A STEP TAKES IS ITS DOM'S COEFFICIENT (Marko, 2026-11-15), as the swarm feeds it:
                    // inst?A<=A{*}() takes the run whole ({1,2,3}.{0,1}sum() is {1,2,3,6}), as does a dom of more than one,
                    // and plus?int<=int() takes one obj at a time, so a flatmap's stream feeds the next step obj by obj
                    final cInt takes = step.dom().c();
                    current = null == takes.max() || 1L < takes.max()
                            ? admitted(current, step)
                            : current.stream().map(o -> admitted(o, step)).reduce(noobj(), Obj::append);
                }
            }
        }

        /**
         * One step on what a run holds. A step that takes one obj at a time takes each copy of a bulked one alone
         * ({@code {3}35} is three 35s, as invoke compresses it), so its dom is asked of one copy and it resolves for one.
         * The step was compiled for the type the run came in with, so what its dom refuses is resolved again for what it
         * is, as each copy of an unrolled run was (Marko, 2026-11-15): its f set to null and its dom kept, and where that
         * resolves nothing, its dom dropped. {@code 1.{2}as(str)} compiles {@code as?uri<=int}, and its second run is the
         * {@code as?uri<=uri} the first never was. What no overload of the call takes has left the step's domain: its
         * walk halts there. A step left to run time has no domain to leave, and resolves, or fails, as any call does.
         */
        private static Obj admitted(final Obj obj, final Inst step) {
            final cInt takes = step.dom().c();
            final Obj one = null != takes.max() && 1L >= takes.max() ? obj.c(cInt.ONE()) : obj;
            if (!step.hasDom() || Helper.filterOnDomainAllowUnique(one, step.dom()))
                return step.apply(obj);
            final Inst call = step.f(null);
            final Inst kept = Selector.Helper.resolve(one, call);
            final Inst again = kept.hasf() ? kept : Selector.Helper.resolve(one, call.tid(call.tid().removeQ(DOM)));
            return again.hasf() ? again.apply(obj) : noobj();
        }

        public static Inst fullSignature(final Inst inst) {
            if (null == inst || null == inst.tid() || inst.args().isNoObj())
                return inst;
            final fURI keyed = MInst.signature(inst.tid(), inst.args());
            return keyed.equals(inst.tid()) ? inst : ((Inst) inst.clone()).selfTID(keyed).asInst();
        }

        /**
         * A DECLARED SPAN IS A RUN OF ARGS (Marko, 2026-11-08: args unfold, so {@code plus({2}str::T)} is
         * {@code plus(str::T,str::T)}): the declaration read at the call's shape, one declared arg for each arg the call
         * holds ({@link Type.Helper#unfold}), with the tid's {@code args} signed to agree. Every positional reading after
         * it (a selector's pre-filter, {@link #admitsArgs}, {@link #bindGenerics}, {@link #resolveArgs}) is then the one
         * it always was. A declaration that spans nothing, or a call that is not ordered, is the declaration itself.
         *
         * @return the declaration at the call's shape, or null when no runs of its args claim the call's args
         */
        public static Inst unfold(final Inst declaration, final Inst call) {
            if (!declaration.args().isLst() || !call.args().isLst() || declaration.args().lstValue().stream().allMatch(arg -> arg.tid().b().isOne()))
                return declaration;
            final List<Obj> unfolded = Type.Helper.unfold(call.args().lstValue(), declaration.args().lstValue());
            if (null == unfolded)
                return null;
            final Inst shaped = declaration.args(lst(unfolded));
            return declaration.tid().hasQ(ARGS) ? fullSignature(shaped) : shaped;
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
            return Optional.ofNullable((inst.tid().basePath().equals(FROM_INST_TID) || inst.tid().basePath().equals(AT_INST_TID)) && inst.arg(0).isUri() ? inst.arg(0).uriValue() : null);
        }

        public static Rec rectifyLstArgs(final Lst lstArgs, final Rec recArgs) {
            final AtomicInteger counter = new AtomicInteger(0);
            return recArgs.elements().map(r -> rel(r.first(), lstArgs.at(counter.getAndIncrement()))).collect(new CommonUtil.RecCollector());
        }

        /**
         * THE DOMAIN GATE: whether {@code lhs} is in {@code dom}, a bulk lhs of a one-obj domain asked one copy at a time
         * ({@link #appliesPerObject}), so {@code {3}5.plus(_)} operates as {@code 5.plus(_)} does (Marko, 2026-11-17). A
         * type applied as a filter asks its membership here too ({@link Type#apply}), as a lst asks each of its indices.
         */
        public static boolean filterOnDomainAllowUnique(final Obj lhs, final Obj dom) {
            if (lhs.isNoObj())
                return true; // noobj lhs means "any domain is acceptable"
            return (lhs.testByID(dom) || lhs.test(dom)) || (Helper.appliesPerObject(lhs, dom) && lhs.c(cInt.ONE()).testByID(dom));
        }

        /**
         * Whether a one-obj domain ({@code {1}}) meets a bulk lhs ({@code c > 1}): the processor applies the inst to
         * the lhs one obj at a time (or once and multiplies, for a bulked obj), so the inst sees the lhs's ELEMENT
         * type. The domain gate ({@link #filterOnDomainAllowUnique}) and the generic binder ({@link #bindGenerics})
         * must agree on this. When only the gate admitted it, a generic {@code A => A} inst (side, filter, ...) right
         * after a seed resolved with {@code A} unbound, its rng fell back to {@code #}, and the reducer after it could
         * not resolve, so it ran per obj: {@code {1,2,3}.side(plus(1)).sum()} answered {@code {1,2,3}}, not {@code 6}.
         * <p>
         * A STREAM THAT MAY BE EMPTY IS LEFT TO RUNTIME. {@code gt(1)} holds for an lhs of at least one obj that may
         * hold more ({@code {2}}, {@code {+}}, {@code {1,7}}), not for {@code {*}} or a page {@code {0,500}}, so the
         * inst after such a stream (a read emits {@code #{*}}) resolves when its objs arrive and their own types pick
         * the overload. Reading those ranges as bulk here commits at compile time against their declared element
         * type, and 2026-11-07 that broke 36 tests (fibered recs, an inst body's {@code *1.as(int::T)}).
         */
        public static boolean appliesPerObject(final Obj lhs, final Obj dom) {
            return dom.c().isOne() && lhs.c().gt(cInt.ONE());
        }

        /**
         * Whether an argument is pure ({@link Inst#isPure()}): an instruction or a code answers for itself, a lst or a rec
         * for everything it holds (a keyed spec carries code as keys and values: {@code group([mod(3)=>sum()])}), and a
         * value (noobj included) runs nothing, so it is pure.
         */
        public static boolean isPure(final Obj arg) {
            if (arg.isNoObj())
                return true;
            if (arg.isObjInst())
                return arg.asInst().isPure();
            if (arg.isCode())
                return arg.asCode().isPure();
            if (arg.isLst())
                return arg.asLst().lstValue().stream().allMatch(Helper::isPure);
            if (arg.isRec())
                return arg.asRec().jvm().entrySet().stream().allMatch(entry -> isPure(entry.getKey()) && isPure(entry.getValue()));
            // A KEYED ARGUMENT IS A REL: a rec's elements() are its key=>value pairs, so an instruction with keyed args
            // (repeat's code=>, a branch's //0=>) hands each pair here. Read as a plain value, the code a pair holds was
            // never checked, and repeat(code=>plus(1).print(_)) answered pure (Marko, 2026-11-22: repeat is pure)
            if (arg.isRel())
                return isPure(arg.asRel().first()) && isPure(arg.asRel().second());
            return true;
        }


        public static <O extends Obj> Optional<O> alignLHSType(final Obj lhs, final O rhs) {
            if (!lhs.c().within(rhs.c()))
                return Optional.empty();
            if (lhs.type().equals(rhs.type()))
                return Optional.of((O) lhs);
            else {
                if (!lhs.baseTypeID().equals(rhs.baseTypeID()))
                    return Optional.empty();
                try {
                    return Optional.of((O) lhs.as(rhs.type()));
                } catch (final Exception e) {
                    return Optional.empty();
                }
            }
        }

        public static <O extends Obj> O alignRHSType(final O lhs, final Obj rhs) {
            return (O) (lhs.type().equals(rhs.type()) ? rhs : lhs.baseTypeID().equals(rhs.baseTypeID()) ? rhs.as(lhs.type()) : noobj());


        }

        /**
         * Whether a declared argument list is OPEN-KEYED: a rec whose KEY is a TYPE rather than a name —
         * {@code branch(uri::T => B)}. The declaration states the SHAPE of the caller's keys (every key
         * conforms to the key type), never a key SET: the caller supplies the keys, keeps every one, and
         * may supply more than the contract names (a rec is open-world). An ordered call is admitted too,
         * since the same declaration is the positional branch's contract.
         */
        public static boolean isOpenArgs(final Poly<?, ?> args) {
            return null != args && args.isRec() && args.asRec().elements().anyMatch(kv -> kv.first().isType());
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
                // OPEN KEYED ARGS: a KEY THAT IS A TYPE names the shape of the caller's keys, not their names.
                // The caller owns the key set (open world), so every entry is kept and only its value is bound.
                if (isOpenArgs(apiInst.args())) {
                    final Obj valueSpec = apiInst.args().asRec().elements()
                            .filter(kv -> kv.first().isType()).findFirst().map(Rel::second).orElse(noobj());
                    if (!userInst.args().isRec())
                        // AN ORDERED CALL HAS NO KEYS: it is the positional branch, resolved slot-wise against
                        // the declaration's value spec, exactly as an ordered declaration's call is resolved.
                        return resolveArgs(userInst, apiInst.args(lst(Collections.nCopies((int) userInst.args().count(), valueSpec))), lhs);
                    return rec(userInst.args().asRec().elements()
                            .map(kv -> rel(kv.first(), Helper.bindParam(kv.first(), valueSpec, kv.second()))));
                }
                // NAMED PARAMS: bind the declared entries and KEEP the caller's undeclared keys — a rec is
                // open-world, so a caller may carry more than the contract names.
                final Map<Obj, Obj> given = userInst.args().isRec()
                        ? new LinkedHashMap<>(userInst.args().asRec().recValue())
                        : new LinkedHashMap<>();
                final AtomicInteger counter = new AtomicInteger(0);
                apiInst.args().asRec().elements().forEach(kv -> {
                    final Obj thisArg = userInst.arg(kv.first().uriValue(), counter.getAndIncrement());
                    // THE BOUND VALUE IS FILED UNDER THE PARAMETER'S NAME, never under the declared key: an optional
                    // key is declared with its coefficient ({?}until), and filed under it the bound value sat BESIDE
                    // the caller's own entry instead of over it, so every compiled repeat carried until and {?}until
                    // (Marko, 2026-11-22: "this is strange. until and {?}until.")
                    given.put(uri(kv.first().uriValue()), Helper.bindParam(kv.first(), kv.second(), thisArg));
                });
                return rec(given);
            } else
                throw MTronException.of("inst args must be a lst or rec: %s", apiInst);
        }

        /**
         * Bind one argument to its named parameter. A filter ({@code ?T}), a default ({@code else(x)}) or any other
         * call parameter is applied to the argument, as it always was. A CONVERSION ({@code as(T)}) is strict, since
         * the argument must convert:
         * <ul>
         * <li>after a code argument it runs on the code's value, not on the code itself: {@code as(T)} applied to
         * {@code _} only relabeled the code, so {@code -5.band(_,_)} bound -5 as a nat;</li>
         * <li>a missing argument is a failure, not a noobj that every later step reads as nothing.</li>
         * </ul>
         */
        private static Obj bindParam(final Obj name, final Obj param, final Obj arg) {
            if (!param.isObjCall())
                return arg;
            if (!Helper.isConversion(param))
                return param.apply(arg);
            if (arg.isNoObj())
                return fail("missing argument %s for %s", name, param);
            return arg.isObjCall() ? arg.<Call>as().mult(param.as()) : param.apply(arg);
        }

        private static boolean isConversion(final Obj param) {
            final List<Inst> insts = param.<Call>as().insts();
            return !insts.isEmpty() && insts.getFirst().tid().basePath().equals(AS_INST_TID);
        }

        /**
         * Whether a definition's declared domain admits an lhs, or a caller's domain hint, coefficients aside. A
         * declared domain is a contract: resolution may adapt a coefficient or bind a generic, but re-typing a declared
         * domain to something it refuses runs the definition on a value it was never written for ({@code "abc".band}
         * on an int band). Undeclared and generic domains admit anything, and so do the lhs kinds resolution passes
         * through on their way elsewhere: noobj (the empty set), a fail or the fail type (resolved only so the fail
         * can propagate through the inst) and code (higher-order).
         *
         * @param definition the definition as declared
         * @param lhs        the lhs, or the domain a caller hinted
         * @return false only when the declared domain refuses it outright
         */
        /**
         * Whether the user's literal arguments can fill the definition's declared slots. A slot that takes something
         * cannot be filled by nothing: {@code plus(int::T)} takes one int, and a {@code {0}2} (or noobj) brings none,
         * whatever payload the zero carries, and nor does a call that leaves the slot out (Marko, 2026-11-17:
         * {@code 1.plus()} fails, there is no plus of zero arguments; it ran the int plus on noobj's 0). An argument past
         * the last slot passes: a call is open, as a lst is ({@code [2,2]?[int::T]}). This is existence, not type
         * checking, which stays on kind and type: only nothing against a declared, non-zeroable type slot is refused.
         * Computed args (calls), call-shaped slots and rec-shaped (named) args pass, and a {@code {?}} slot still opts
         * into absence.
         *
         * @param definition the definition as declared
         * @param userInst   the call whose args would fill its slots
         * @return false only when nothing, a literal zero or a missing argument, would fill a slot that takes something
         */
        public static boolean argsFillSlots(final Inst definition, final Inst userInst) {
            if (!definition.args().isLst() || !userInst.args().isLst())
                return true;
            final List<Obj> slots = definition.args().lstValue();
            final List<Obj> given = userInst.args().lstValue();
            for (int i = 0; i < slots.size(); i++) {
                final Obj slot = slots.get(i);
                final Obj arg = i < given.size() ? given.get(i) : noobj();
                if (arg.isNoObj() && !arg.isObjCall() && slot.isType() && !slot.c().isZeroable())
                    return false;
            }
            return true;
        }

        /**
         * Does this candidate's DECLARED argument contract admit the user's arguments? A candidate that declares no
         * argument types claims nothing and admits anything. One that declares them claims an arity and a base type
         * per position: the user's argument at that position must have that base form; a generic or undeclared
         * declared type admits anything there, and an argument whose type cannot be read (a nested call) admits.
         * <p>
         * The declaration is the {@code args} query component of the candidate's tid ({@code inst?…&args=[int,rec]},
         * written when it was minted under {@code MInst.ARGS_IN_TID}); an inst whose tid carries none declares
         * nothing.
         * <p>
         * A SLOT ADMITS WHAT CONFORMS TO IT, by the type hierarchy and then by structure — never by name. A name is
         * the carrier's own spelling and states nothing about refinement, so name comparison refused exactly the
         * arguments a contract exists to accept: {@code plus(cmphx::[...])} against a slot declaring {@code cmplx}
         * (cmphx IS a cmplx), a {@code datetime} against a slot declaring {@code time}, a {@code meter} against one
         * declaring {@code metric}. Each refusal fell through to the ancestor's operation — {@code lst} plus
         * concatenated two complex numbers. The rule is: the argument's type tests by ID against the declared type
         * (nominal subsumption), or the argument itself satisfies it (structural, so a value carrying an unresolvable
         * label is still admitted when its shape conforms). BASE-NAME equality survives only as the last resort, for
         * a declaration that cannot be resolved as a type at all.
         *
         * @param candidate the candidate definition as declared
         * @param user      the call whose arguments would fill its slots
         * @return false only when the declaration claims an arity or a base type the call's arguments contradict
         */
        public static boolean admitsArgs(final Inst candidate, final Inst user) {
            // hasQ first: args() PARSES the query body, and an instset minted with the flag off (the common case) has
            // no key at all, so the gate must cost one lookup, not a parse, per candidate
            if (null == candidate || null == user || null == candidate.tid() || !candidate.tid().hasQ(ARGS))
                return true;
            // OPEN-KEYED ARGS admit any caller shape and arity: the keys are the caller's, and the declaration
            // states only their type (see {@link #isOpenArgs(Poly)}).
            if (isOpenArgs(candidate.args()))
                return true;
            // a BARE `args` key DECLARES ZERO arguments — a claim, which is why the gate is hasQ and not isEmpty: no
            // key at all is "no argument claim" and admits anything (the early return above)
            final List<fURI> declared = candidate.tid().args();
            final Poly<?, ?> given = user.args();
            if (null == given || !given.isLst())
                return true;                        // a named (rec-shaped) call states no order to read
            final List<Obj> values = given.lstValue();
            // A CALL MAY SUPPLY FEWER ARGUMENTS THAN THE CONTRACT DECLARES: the contract's remaining positions are
            // the binder's to fill (that is what makes a zero-argument call of a one-argument operation legal), so
            // only the positions the caller actually supplies are held to the declaration. Supplying MORE than the
            // contract declares is still a refusal.
            if (values.size() > declared.size())
                return false;
            for (int i = 0; i < values.size(); i++) {
                final fURI wanted = declared.get(i);
                if (null == wanted || wanted.isGeneric())
                    continue;                       // a generic slot accepts anything
                final String wantedName = baseName(wanted);
                if (wantedName.isEmpty() || "#".equals(wantedName) || "+".equals(wantedName))
                    continue;                       // an undeclared endpoint accepts anything
                final Obj arg = values.get(i);
                if (null == arg || arg.isNoObj() || arg.isObjCall())
                    continue;                       // a nested call's type cannot be read here
                final fURI actual = Obj.Helper.specificTypeId(arg);
                if (null == actual || actual.isGeneric())
                    continue;
                // A DECLARED SLOT ADMITS WHAT CONFORMS TO IT — the TYPE HIERARCHY, not the name. A name is the
                // carrier's own spelling and says nothing about refinement: `plus(cmphx::[...])` must satisfy a slot
                // declaring `cmplx` (cmphx IS a cmplx), a `datetime` must satisfy a slot declaring `time`, and a
                // `meter` must satisfy one declaring `metric`. Comparing base names refused all of those and sent
                // the call to the ancestor's operation (lst plus concatenated two cmplx values).
                if (conforms(arg, wanted))
                    continue;
                if (!wantedName.equals(baseName(actual)))
                    return false;
            }
            return true;
        }

        /**
         * Whether {@code arg} is an instance of the DECLARED type {@code declared} — the conformance test a slot
         * makes, by the hierarchy rather than by name ({@code cmphx} conforms to {@code cmplx}, {@code datetime} to
         * {@code time}, {@code meter} to {@code metric}). A declaration that cannot be resolved as a type states
         * nothing, so it refuses nothing and the name comparison decides.
         */
        private static boolean conforms(final Obj arg, final fURI declared) {
            try {
                final Type declaredType = T(declared);
                if (declaredType.isRootType())
                    return true;                    // a slot declaring the root type admits anything
                // NOMINAL first (cheap: the hierarchy walk), then STRUCTURAL — a value whose own label states no
                // useful type (an unknown name, `cmphx::[0.0,0.0]`) is still a cmplx if it satisfies cmplx's
                // predicate, and the slot is about what the argument IS, not what it is called.
                return Obj.Helper.specificType(arg).testByID(declaredType) || arg.test(declaredType);
            } catch (final Exception e) {
                return false;                       // an unresolvable declaration: let the caller fall back to names
            }
        }

        /**
         * The type name a declared contract and an argument are compared by: a declaration is written as a type's
         * own address ({@code /m/int}) while a value's specific type id may name the same carrier elsewhere, so the
         * base PATH's name is what both spellings share. Empty for a uri with no base (an endpoint that states
         * nothing, and therefore refuses nothing).
         */
        private static String baseName(final fURI uri) {
            final fURI base = uri.basePath();
            if (null == base)
                return "";
            final String name = base.name();
            return null == name ? "" : name;
        }

        public static boolean admitsIgnoringCoefficient(final Inst definition, final Obj lhs) {
            if (!definition.hasDom() || lhs.isNoObj() || lhs.isFail() || lhs.isCall())
                return true;
            // an initial (dom {0}) consumes nothing, so resolution refuses it no lhs: a code headed by an initial applies
            // to any lhs (a template's nested {{{ 1 }}} under 1.map(...)). its domain is the noobj type, which rightly
            // admits only nothing -- the admission is the initial's, not the type's. (mid-chain, the processor still
            // refuses to split a {1} obj into a {0} dom: x.start(1) is not a chain step.)
            if (definition.isInitial())
                return true;
            final fURI lhsId = Obj.Helper.specificTypeId(lhs);
            if (lhsId.isGeneric() || lhsId.basePath().equals(FAIL_TID) || Obj.Helper.specificTypeId(definition.dom()).isGeneric())
                return true;
            // coefficients aside means on BOTH sides: #{*} is the wildcard a gather hints, and # still admits it
            final Obj lhsOne = lhs.isType() ? lhs.asType().c(cInt.ONE()) : lhs.c(cInt.ONE());
            return typeCompatibleIgnoreCoefficient(lhsOne, definition.dom().c(cInt.ONE()));
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

        /**
         * The definition dispatch is about to change, without the address it was read from. Hinting, binding and
         * re-typing all copy the definition, and a copy that kept the definition's vid would write itself back there
         * as it is constructed ({@link Obj.Helper#objCheckAndSave(Obj)}), so every call would add its own variant to
         * the instruction table. A call site is not the definition: it must never claim the definition's address.
         *
         * @param candidate the definition as read from its space
         * @return the same inst with no vid ({@code candidate} itself when it has none)
         */
        public static Inst unaddressed(final Inst candidate) {
            return null == candidate.vid() ? candidate : ((Inst) candidate.clone()).selfVID(null).as();
        }

        public static Inst bindQ(final Obj lhs, final Inst userInst, final Inst apiInst) {
            if (userInst.tid().hasQ(BLOCK))
                return apiInst.tid(apiInst.tid().addQ(BLOCK));
            return apiInst;
        }

        /**
         * A PATTERN'S DECLARATIONS, BOUND BEFORE ANY INSTRUCTION BINDS: an argument written X::T@A ("A is X", a generic
         * name over a concrete type) pins A, so every use of A, earlier or later in the sequence, answers to it. A bare
         * A::T is a label (its tid is its own generic name) and declares nothing. The binding {@link #bindGenerics}
         * starts from, for the compiler's binder and for {@link Code#test} alike.
         *
         * @return the generics the pattern declares, or null when it declares one generic as two types
         */
        public static Map<fURI, fURI> declarations(final List<Inst> pattern) {
            return declarations(pattern, new HashSet<>());
        }

        /**
         * {@link #declarations(List)}, with the VALUE NAMES the pattern declares gathered into {@code names} by the same
         * walk: the names a binding must bind, every one of them, for the pattern to match ({@link Code.Helper#bind}).
         */
        public static Map<fURI, fURI> declarations(final List<Inst> pattern, final Set<fURI> names) {
            final Map<fURI, fURI> generics = new HashMap<>();
            final Deque<Obj> pending = new ArrayDeque<>(pattern);
            while (!pending.isEmpty()) {
                final Obj next = pending.pop();
                final fURI name = Helper.name(next);
                if (null != name)
                    names.add(name);
                if (next.isObjInst())
                    next.asInst().args().elements().forEach(pending::push);
                else if (next.isCode())
                    next.asCode().insts().forEach(pending::push);
                else if (next.isType() && null != next.vid() && next.vid().one().isGeneric() && !next.tid().one().isGeneric()) {
                    // ONE LETTER, ONE MEANING: a pattern declaring one generic as two types contradicts itself
                    final fURI prior = generics.putIfAbsent(next.vid().one(), next.tid().one());
                    if (null != prior && !prior.equals(next.tid().one()))
                        return null;
                }
            }
            return generics;
        }

        /**
         * THE VALUE NAME AN ELEMENT CARRIES, or null (Marko, 2026-11-08): a vid that resolves as a binding (relative, as
         * {@code Memory} routes it), not all caps (a generic names a type) and not a pattern ({@code #}, {@code +}).
         * {@code int::T@a} and {@code plus(2)@a} name {@code a}; {@code int::T} ({@code /m/int}), {@code int::T@A} and
         * {@code #::T} name nothing.
         */
        public static fURI name(final Obj element) {
            final fURI vid = element.vid();
            return null == vid || vid.isAbsolute() || vid.hasScheme() || vid.hasHost() || vid.isGeneric() || vid.hasPattern() ? null : vid;
        }

        /**
         * The value names a call's positional arguments carry when every one carries one ({@code (plus(2)@a,
         * mult(10)@b)}), or null: a call with an unnamed argument stays positional.
         */
        public static List<fURI> slotNames(final Poly<?, ?> args) {
            // every instruction application asks, so an unnamed first argument answers before anything is allocated
            if (null == args || !args.isLst() || args.isEmpty() || null == Helper.name(args.lstValue().getFirst()))
                return null;
            final List<fURI> names = args.lstValue().stream().map(Helper::name).toList();
            return names.contains(null) ? null : names;
        }

        /**
         * Record what an element's value name matched, false when the name already holds another obj: a name met twice
         * must meet the same obj, as a generic met twice must meet the same type.
         */
        private static boolean named(final Map<fURI, Obj> names, final Obj element, final Obj matched) {
            final fURI name = Helper.name(element);
            if (null == name)
                return true;
            final Obj prior = names.putIfAbsent(name, matched);
            return null == prior || prior.equals(matched);
        }

        /**
         * COEFFICIENT VARIABLES BIND (Marko, 2026-11-09) — a pattern endpoint whose coefficient names its bounds
         * ({@code X{a,b}::T} a minimum and a maximum, {@code X{a}} a point) binds each name to what the call states there,
         * into {@code names} as a value name binds, so a name met twice must meet what it bound: {@code inst?X{a}<=Y{a}()}
         * holds where the dom and rng are one point ({@code plus?int<=int}), not where they differ
         * ({@code is?int{?}<=int}) or are no point. A variable binds only an endpoint the call states, and an unresolved
         * instruction states none.
         */
        private static boolean coefficients(final Map<fURI, Obj> names, final Obj pattern, final Obj call) {
            if (!pattern.isInst() || !call.isInst())
                return true;
            final fURI declared = pattern.tid();
            final fURI stated = call.tid();
            return coefficient(names, declared.hasDom() ? declared.dom().c() : null, stated.hasDom() ? stated.dom().c() : null)
                    && coefficient(names, declared.hasRng() ? declared.rng().c() : null, stated.hasRng() ? stated.rng().c() : null);
        }

        /**
         * One endpoint's coefficient variable bound to the coefficient the call states there ({@link #coefficients}).
         */
        private static boolean coefficient(final Map<fURI, Obj> names, final cInt variable, final cInt stated) {
            if (null == variable || !variable.isVariable())
                return true;
            if (null == stated)
                return false;
            final BiPredicate<String, Long> bind = (name, bound) -> {
                final Obj value = null == bound ? noobj() : jnt(bound);
                final Obj prior = names.putIfAbsent(fURI.Singleton.f(name), value);
                return null == prior || prior.equals(value);
            };
            // a point names both bounds alike, and binds only a point
            if (null != variable.minName() && variable.minName().equals(variable.maxName()))
                return null != stated.min() && stated.min().equals(stated.max()) && bind.test(variable.minName(), stated.min());
            return (null == variable.minName() || bind.test(variable.minName(), stated.min()))
                    && (null == variable.maxName() || bind.test(variable.maxName(), stated.max()));
        }

        /**
         * THE REWRITE A SHADOW SWITCHES OFF, or noobj when the rewrite is no shadow (Marko, 2026-11-10). A shadow is the
         * identity on code under a rewrite's own tid, carrying that rewrite as its one argument, {@code [inactive=>rewrite]}:
         * a pool keys its rewrites by tid, and a machine's own level wins over what it imports, so a shadow written there
         * stands in the rewrite's place, listed and switched off.
         */
        public static Obj shadowed(final Inst rewrite) {
            return 1 == rewrite.args().count() && rewrite.arg(0).isRec() ? rewrite.arg(0).asRec().<Obj>atDirect(uri(INACTIVE)) : noobj();
        }

        public static Inst bindGenerics(final Obj lhs, final Inst apiInst, final Obj userInst) {
            return bindGenerics(lhs, apiInst, userInst, new HashMap<>(), new HashMap<>());
        }

        /**
         * BIND A CALL AGAINST A DECLARATION, slot by slot: the one binding walk of dispatch ({@code GenericBinder}), of a
         * code tested against a code pattern ({@link Code.Helper#bind}), and so of {@code update()} and the derivations.
         * An all-caps generic binds a TYPE into {@code generics}; an element carrying a value name binds what it matched
         * into {@code names} (Marko, 2026-11-08: names in any instruction), at any depth, a code argument's instructions
         * included. A name binds only a place the call holds (a value holds none), and a name or generic met twice
         * must meet what it bound.
         *
         * @return the declaration with its generics bound, or null when the call does not bind it
         */
        public static Inst bindGenerics(final Obj lhs, final Inst apiInst, final Obj userInst, final Map<fURI, fURI> generics, final Map<fURI, Obj> names) {
            final GraphittyLogger LOG = Graphitty.log(lhs);
            if (!Helper.named(names, apiInst, userInst) || !Helper.coefficients(names, apiInst, userInst))
                return null;
            Inst apiInstTemp = apiInst;
            if (apiInstTemp.dom().typeId().one().isGeneric() && !lhs.isNoObj()
                    && (lhs.type().c().within(apiInstTemp.dom().c()) || Helper.appliesPerObject(lhs.type(), apiInstTemp.dom()))) {
                // A GENERIC IS PINNED for the length of its binding: a dom naming one already bound (by this signature's
                // other slots, or by an earlier candidate of a sequence) must be what it bound
                final fURI prior = generics.putIfAbsent(apiInstTemp.dom().typeId().one(), lhs.type().typeId().one());
                if (null != prior && !lhs.type().typeId().one().test(prior))
                    return null;
                apiInstTemp = apiInstTemp.dom(lhs.type().c(apiInstTemp.dom().c()).as());
            }
            // A VARIABLE A LST BRACKET NAMES binds to the type of the lhs's indices its run claims, read the way the
            // bracket tests them (Lst.LstType.bindings): merge's A{*}<=lst[{**}A{*}] over order's lst[{*}int{*}] binds
            // A to int, so the objs it unrolls are typed (Marko, 2026-11-18). A contradiction binds nothing here: the
            // domain gate is what refuses it
            if (!lhs.isNoObj() && apiInstTemp.tid().hasDom() && apiInstTemp.tid().dom().hasPoly()) {
                final Map<fURI, fURI> indexed = Lst.LstType.bindings(lhs, apiInstTemp.tid().dom());
                if (null != indexed)
                    indexed.forEach(generics::putIfAbsent);
            }
            if (apiInstTemp.rng().typeId().one().isGeneric() && generics.containsKey(apiInstTemp.rng().typeId().one())) {
                apiInstTemp = apiInstTemp.rng(T(generics.get(apiInstTemp.rng().typeId().one()).c(apiInstTemp.rng().c())));
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
                        // a name binds what the call holds in its place, and a call that is no instruction holds no places:
                        // mult(neg(#::T@b)) against mult(2) leaves b unbound, never bound to the 2
                        if (userInst.isObjInst() && !Helper.named(names, apiArg, userArg))
                            return null;
                        if (apiArg.typeId().isGeneric()) {
                            final fURI lastBinding = generics.get(apiArg.typeId().one());
                            if (null != lastBinding && !userArg.typeId().test(lastBinding))
                                LOG.debug("existing generic doesn't match current usage: [{{m}}generic{{/m}}] %s [{{m}}past{{/m}}] %s [{{m}}present{{/m}}] %s", userArg.tid(), lastBinding, apiArg.tid());
                            if (!userArg.isObjCall()) // TODO: can this be more specialized (currently necessary for when arg is a call and we want the result of the call to be the binding, not the call itself
                                generics.computeIfAbsent(apiArg.typeId().one(), k -> userArg.typeId().one()); // beware of int[0] yielding noobj across all bindings
                        }
                        if (apiArg.isObjInst()) { // todo: isCall()?
                            // a nested instruction is part of the same signature, so its generics and names are the same variables
                            apiArg = Helper.bindGenerics(lhs, apiArg.asInst(), userArg, generics, names);
                        } else if (apiArg.isCode() && userArg.isCall()) {
                            // so is a code argument, instruction by instruction, walked for its names and generics; each
                            // instruction's lhs is the one before it, never the call's, so none binds a domain. Like a
                            // nested instruction, it refuses nothing: a declared spec such as is's isa(bool::T).else(false)
                            // is walked against the caller's eq(10) and binds nothing, which is no refusal
                            final List<Inst> userInsts = userArg.asCall().insts();
                            for (int k = 0; k < Math.min(apiArg.asCode().insts().size(), userInsts.size()); k++)
                                Helper.bindGenerics(noobj(), apiArg.asCode().inst(k), userInsts.get(k), generics, names);
                        } else {
                            if (apiArg.typeId().one().isGeneric()) {
                                // A BOUND GENERIC IS ENFORCED. Its binding is written into the argument as the declaration
                                // int::T@A ("A is int"), but the generic NAME still matches anything, so the user's argument
                                // is tested against what the binding names. A call is exempt (resolveArgs compiles it later,
                                // so its result is not known here), and a type argument binds by its name, compared below.
                                final fURI binding = generics.get(apiArg.typeId().one());
                                if (null != binding && !userArg.isObjCall() && !userArg.isType()
                                        && !userArg.typeId().one().isGeneric() && !userArg.test(T(binding.c(apiArg.c()))))
                                    return null;
                                apiArg = apiArg.tid(generics.getOrDefault(apiArg.typeId().one(), userArg.typeId())).c(apiArg.c());
                            }
                            // A TYPE-NAMED ARGUMENT IS COMPARED AS A TYPE: when both the declared argument and the
                            // caller's argument are TYPES (`as(rec::T)`), the same type NAME is the same request —
                            // object identity is too strict, since an instset constructed a second time re-registers
                            // its types (AbstractInstSetTest builds one per test) and the registered contracts then
                            // hold the earlier Type objects. Otherwise the ordinary test decides.
                            // A TYPE-NAMED ARGUMENT IS COMPARED AS A TYPE: when both the declared argument and the
                            // caller's argument are TYPES (`as(rec::T)`), the same type NAME is the same request --
                            // object identity is too strict, since an instset constructed a second time re-registers
                            // its types (AbstractInstSetTest builds one per test) and the registered contracts then
                            // hold the earlier Type objects. Otherwise the ordinary test decides.
                            final boolean sameTypeName = apiArg.isType() && userArg.isType()
                                    && Obj.Helper.specificTypeId(userArg).basePath().equals(Obj.Helper.specificTypeId(apiArg).basePath());
                            if (null != apiArg && !apiArg.isObjCall() && !userArg.typeId().one().isGeneric()
                                    && !sameTypeName && !userArg.test(apiArg)) {
                                // TODO: isClessGeneric() and cLess.isGeneric() behave differently
                                return null;
                            }
                        }
                        resolvedArgs.add(apiArg);
                    }
                    apiInstTemp = apiInstTemp.args(lst(resolvedArgs));
                }

            if (apiInstTemp.rng().typeId().one().isGeneric()) {
                apiInstTemp = apiInstTemp.rng(T(generics.getOrDefault(apiInstTemp.rng().typeId().one(), userInst.rng().typeId()).c(apiInstTemp.rng().c())));
            }
            // A VARIABLE INSIDE A LST RANGE is the variable outside it (Lst.LstType.bound): order's lst[{*}A{*}] over
            // an int stream answers lst[{*}int{*}], so the instruction after it reads the index type, not an open A
            if (apiInstTemp.tid().hasRng() && apiInstTemp.tid().rng().hasPoly()) {
                final fURI range = Lst.LstType.bound(apiInstTemp.tid().rng(), generics);
                if (range != apiInstTemp.tid().rng())
                    apiInstTemp = apiInstTemp.rng(T(range));
            }
            ///  hail mary
            if (apiInstTemp.dom().typeId().one().isGeneric() || apiInstTemp.dom().typeId().one().equals(ALL)) {
                // a zero lhs binds nothing (the first branch above refuses it too): binding here would mint a noobj
                // type present once, which nothing inhabits, so the generic stays open for the runtime lhs to bind.
                // the call's own ?rng<=dom hints still apply
                if (!lhs.isNoObj())
                    apiInstTemp = apiInstTemp.dom(lhs.type().c(apiInstTemp.dom().c()).as());
                apiInstTemp = apiInstTemp.tid(Helper.apiOrUser(apiInstTemp.typeId(), userInst.typeId(), generics));
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

        public Object func() {
            return this.func;
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
                    instC(AS_INST_TID.dom(INST_TID).rng(CODE_TID), lst(CODE_TYPE), (lhs, _) -> code(List.of(lhs.asInst()))),
                    // an inst updates its own args as a code updates a run (Call.Helper.update): plus(5) >>= [args=>[+10]]
                    instC(UPDATE_INST_TID.dom(INST_TID).rng(INST_TID), lst(REC_TYPE), (lhs, inst) -> code(Call.Helper.update(List.of(lhs.asInst()), inst.arg(0))).tryToInst()),
                    // A REWRITE SWITCHED OFF STAYS IN THE POOL (Marko, 2026-11-10): inactive answers the rewrite's shadow
                    // (Helper.shadowed), and a shadow is already off. Written where the rewrite is, it switches the rewrite
                    // off there; active answers the rewrite a shadow carries, and an active rewrite as it is
                    instC(INACTIVE_INST_TID.dom(INST_TID).rng(INST_TID), lst(), (lhs, inst) -> {
                        final Inst rewrite = lhs.asInst();
                        // noobj is the zero inst, so a shadow is told by isObjInst
                        if (Helper.shadowed(rewrite).isObjInst())
                            return rewrite;
                        if (Machine.current().instset().rewrites().stream().noneMatch(pooled -> pooled.tid().equals(rewrite.tid())))
                            throw MTronException.of("only a rewrite of the pool is switched off, not %s", rewrite);
                        // THE SHADOW IS THE REWRITE'S OWN TID, EXACTLY: instC signs a tid with the args it mints
                        // (MInst.ARGS_IN_TID), so the shadow's one rec read args=[rec], another identity than a rewrite that
                        // declares none (gremlin_pushdown, branch_collapse), and the door filed it beside the rewrite, which
                        // kept firing. Only a rewrite that carries one rec of rules (law_absorbing) agreed, by accident
                        return instC(rewrite.tid(), lst(rec(uri(INACTIVE), rewrite)), (code, shadow) -> code).selfTID(rewrite.tid());
                    }),
                    instC(ACTIVE_INST_TID.dom(INST_TID).rng(INST_TID), lst(), (lhs, inst) -> {
                        final Obj carried = Helper.shadowed(lhs.asInst());
                        return carried.isObjInst() ? carried : lhs;
                    })));
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