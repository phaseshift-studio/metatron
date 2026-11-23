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

package studio.phaseshift.metatron.isa.m.mach.type.compiler.resolver;

import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.analyzer.Analyzer;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.Binder;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.Resolver;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.Selector;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Poly;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.util.Tuple;

import java.util.*;

import static studio.phaseshift.metatron.Tokens.MONAD_IN;
import static studio.phaseshift.metatron.Tokens.MONAD_OUT;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.AS_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.NOOBJ_TYPE;
import static studio.phaseshift.metatron.isa.m.mach.machInstSet.MACH_SPECIFICITY_SELECTOR_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * SpecificitySelector — the concrete {@code specificity_selector::T}: the selector the machine has
 * always used. It fetches the candidates a call may resolve to, cheaply pre-filters them, and then
 * scores each by how specific its signature is, taking the highest-scoring candidate its binder can
 * make concrete.
 *
 * Scoring criteria are in {@link #scoreSpecificity}: concrete domain, exact domain base-path match
 * (or, smaller, the lhs's own base form), a non-base dom/rng, argument specificity (with an
 * exact-argument bonus), the large bonus that makes an as() candidate whose range names the
 * requested type win, and a small bonus for carrying a function body.
 *
 * The from/at and as() shortcuts are kept here (they return a finished contract rather than a
 * candidate, so they deliberately bypass the binder).
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class SpecificitySelector extends MRec implements Selector {

    private static final SpecificitySelector INSTANCE = new SpecificitySelector(mutableMap(), MACH_SPECIFICITY_SELECTOR_TID, null);

    public static SpecificitySelector single() {
        return INSTANCE;
    }

    public SpecificitySelector() {
        this(mutableMap(), MACH_SPECIFICITY_SELECTOR_TID, null);
    }

    public SpecificitySelector(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    @Override
    public Inst apply(final Obj lhs, final Inst userInst, final Binder binder) {
        final Inst selected = this.select(lhs, userInst, binder);
        // THE CALL FRAME: a call no signature matches may be its own application written as data, [lhs=>x, args=>a]
        return null != selected || userInst.hasf() || userInst.isNoObj() ? selected
                : Selector.Helper.tryRawRecArgs(userInst);
    }

    private Inst select(final Obj lhs, final Inst userInst, final Binder binder) {
        if (userInst.hasf())
            return userInst;
        if (userInst.isNoObj())
            return null;
        // a frame matches no pattern (Selector.Helper#isRawRecArgs): its lhs is the processor's key, never a parameter
        if (Selector.Helper.isRawRecArgs(userInst))
            return null;
        /////////////////////// FROM/AS FAST RESOLUTION ///////////////////////
        if (!userInst.hasRng()) {
            final Optional<fURI> fromOrAt = Inst.Helper.isFromOrAtInstToUri(userInst);
            if (fromOrAt.isPresent()) {
                if (!fromOrAt.get().hasPattern() && !fromOrAt.get().hasHost()) {
                    final Obj fromOrAtObj = Machine.read(fromOrAt.get());
                    if (!fromOrAtObj.isNothing() && !fromOrAtObj.isCall()) {
                        userInst.logger().debug("fast from/at() resolution: %s", fromOrAt.get());
                        return Inst.Helper.bindQ(lhs, userInst, Inst.Helper.unaddressed(Machine.read(userInst.tid()).asInst()).args(lst(fromOrAt.get().toUri())).rng(T(fromOrAtObj.typeId().maybeSome())));
                    }
                }
                return Inst.Helper.bindQ(lhs, userInst, Inst.Helper.unaddressed(Machine.read(userInst.tid()).asInst()).args(lst(uri(fromOrAt.get()))).rng(T(ALL.maybeSome())));
            }
        }
        // a cast names its target type in its own argument, and the binder rebinds the resolved
        // contract's rng to exactly that type. This fast path serves only calls that name no type;
        // a cast falls through and is resolved, scored and rebound by the general path.
        if (userInst.tid().big().test(AS_INST_TID) && !userInst.args().elements().anyMatch(Obj::isType)) {
            final List<Obj> result = Machine.read(AS_INST_TID
                    .dom(Obj.Helper.specificTypeId(userInst.hasDom() ? userInst.dom() : lhs))
                    .rng(Obj.Helper.specificTypeId(userInst.arg(0)))).stream().toList();
            if (!result.isEmpty()) {
                userInst.logger().debug("fast as() resolution: %s", result);
                return Inst.Helper.bindQ(lhs, userInst, Inst.Helper.unaddressed(result.getFirst().asInst()));
            }
        }
        /////////////////////////////////////////////////////////////////////
        final fURI basePath = userInst.tid().basePath();
        if (lhs.isPoly()) {
            final Obj fetched = Resolver.Helper.getPolyAutoInst(lhs.asPoly(), uri(basePath));
            if (fetched.isObjInst())
                return Inst.Helper.bindQ(lhs, userInst, Inst.Helper.unaddressed(fetched.asInst()));
        }
        final Analyzer profile = Analyzer.PROFILE.get();
        final long t0 = null == profile ? 0L : System.nanoTime();
        final Obj fetched = Machine.read(basePath);
        if (null != profile)
            profile.record(f(Tokens.RESOLVE), f(Tokens.INST_RESOLVE), System.nanoTime() - t0);

        // A GENERIC CAST TARGET NAMES NO RANGE. A cast's target IS its range, so as(A::T) asks for no particular
        // type, and an overload that produces one (rec => json) cannot serve it: only a generic-range cast can,
        // and that cast refuses to type a value generic
        final boolean genericCast = basePath.equals(AS_INST_TID) && userInst.args().count() == 1
                && userInst.arg(0).isType() && userInst.arg(0).asType().isGeneric();
        // Collect viable candidates after cheap pre-filters (before the expensive bind + resolveArgs)
        final List<Inst> viable = fetched.stream()
                .filter(Obj::isObjInst)
                .map(Obj::asInst)
                // a declared span is a run of args: each candidate is read at the call's shape, or is no candidate
                .map(i -> Inst.Helper.unfold(i, userInst))
                .filter(Objects::nonNull)
                .filter(i -> (userInst.tid().basePath().equals(AS_INST_TID) && 1 == i.args().count() && userInst.args().count() == 1) ||
                        (i.tid().hasQ(MONAD_IN) || i.tid().hasQ(MONAD_OUT)) ||
                        this.checkArgs(userInst.args(), i.args()))
                .filter(i -> !lhs.isObjInst() || (i.dom().baseTypeID().equals(Tokens.M_ISA_INST_TID)))
                .filter(i -> !genericCast || Obj.Helper.specificTypeId(i.rng()).isGeneric())
                .toList();

        if (viable.isEmpty())
            return null;

        // a single candidate needs no scoring: the legacy shortcut binds it directly, with the
        // replacement dom/rng hint and without the nominal-domain gate the scored path applies.
        if (viable.size() == 1) {
            final Inst hinted = this.hintReplace(Inst.Helper.unaddressed(viable.getFirst()), userInst);
            return null == hinted ? null : binder.apply(lhs, userInst, hinted);
        }

        // score by specificity, then take the first candidate the binder can make concrete.
        // A CALL THAT NAMES A STREAM DOMAIN NAMES THE STREAM'S SUBJECT ({@code ?dom=real{*}}), so a contract of
        // another type is never a candidate for it — the scored path refuses what the single-candidate shortcut
        // already refuses ({@link #hintReplace}): {@code sum?dom=real{*}} must not land on the rec sum merely
        // because the lhs (a wrapper) is a rec. A unit (coefficient-one) hint is the ordinary typed call and keeps
        // its looser scoring, where the lhs form still steers the pick.
        final boolean streamDomain = userInst.hasDom() && !userInst.dom().c().isOne();
        return viable.stream()
                .filter(apiInst -> apiInst.dom().isGeneric() || !apiInst.dom().isNominal() || Obj.Helper.specificType(lhs).testByID(apiInst.dom()))
                .filter(apiInst -> !streamDomain || Inst.Helper.admitsIgnoringCoefficient(apiInst, userInst.dom()))
                // the candidate's DECLARED argument contract is part of its signature: two instructions that differ
                // only in argument type are told apart here, and one that declares none admits anything (a no-op
                // for an instset minted with MInst.ARGS_IN_TID off)
                .filter(apiInst -> Inst.Helper.admitsArgs(apiInst, userInst))
                .map(apiInst -> Tuple.Pair.with(this.scoreSpecificity(lhs, userInst, apiInst), apiInst))
                .sorted(Comparator.comparingInt((Tuple.Pair<Integer, Inst> scored) -> scored.get0()).reversed())
                .map(scored -> binder.apply(lhs, userInst, this.hintCompose(Inst.Helper.unaddressed(scored.get1()), userInst)))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    /**
     * Shape a candidate with the call's dom/rng hints, keeping the API's declared domain type and
     * taking the user's coefficient — the form the scored, multi-candidate path has always used.
     */
    private Inst hintCompose(final Inst candidate, final Inst userInst) {
        final Analyzer profile = Analyzer.PROFILE.get();
        final long t0 = null == profile ? 0L : System.nanoTime();
        Inst hinted = userInst.hasDom() ? candidate.dom(this.hintedDom(candidate.dom(), userInst.dom())) : candidate;
        hinted = userInst.hasRng() ? hinted.rng(userInst.rng()) : hinted;
        hinted = userInst.tid().basePath().equals(AS_INST_TID)
                ? hinted.rng(userInst.arg(0).isNoObj() ? NOOBJ_TYPE : Obj.Helper.specificType(userInst.arg(0)))
                : hinted;
        if (null != profile)
            profile.record(f(Tokens.RESOLVE), f(Tokens.INST_RESOLVE), System.nanoTime() - t0);
        return hinted;
    }

    /**
     * Shape the declared domain with the call's dom hint. The API's declared type stays -- a hint stands in
     * for a compatible declared domain, and only its coefficient is taken -- EXCEPT that a hint naming the
     * same type family with explicit type arguments instantiates the declaration ({@code ?dom=lst[int]} on a
     * declared {@code lst} is {@code lst[int]}, not bare {@code lst}). Dropping those arguments made every
     * instantiation resolve to its erasure: {@code lst[int].sum()} compiled with {@code dom=/m/lst} where
     * {@code lst[int]} was asked for. A hint naming another family still contributes only its coefficient, as
     * before, and a bare hint contributes nothing new.
     */
    private Type hintedDom(final Type declared, final Type requested) {
        final fURI requestedID = requested.vid();
        if (requestedID.poly().isEmpty() || !requestedID.basePath().equals(declared.vid().basePath()))
            return declared.c(requested.c()).as();
        final fURI instantiated = Type.Helper.instantiate(declared.vid(), requestedID);
        return T(instantiated.c(declared.tid().c())).c(requested.c()).as();
    }

    /**
     * Shape a candidate with the call's dom/rng hints by replacing its domain outright — the form
     * the single-candidate shortcut has always used. The two differ for a call that names an
     * explicit dom; unifying them is a semantic decision in its own right (see the resolver design
     * note), so the split is preserved and named here rather than hidden in a fast path.
     */
    private Inst hintReplace(final Inst candidate, final Inst userInst) {
        // a hint may stand in for a compatible declared domain, never for one the definition refuses
        // ("abc".band?int{?}<=str on an int band): such a candidate is not viable at all
        if (userInst.hasDom() && !Inst.Helper.admitsIgnoringCoefficient(candidate, userInst.dom()))
            return null;
        final Analyzer profile = Analyzer.PROFILE.get();
        final long t0 = null == profile ? 0L : System.nanoTime();
        Inst hinted = userInst.hasDom() ? candidate.dom(userInst.dom()) : candidate;
        hinted = userInst.hasRng() ? hinted.rng(userInst.rng()) : hinted;
        hinted = userInst.tid().basePath().equals(AS_INST_TID)
                ? hinted.rng(userInst.arg(0).isNoObj() ? NOOBJ_TYPE : userInst.arg(0).asType())
                : hinted;
        if (null != profile)
            profile.record(f(Tokens.RESOLVE), f(Tokens.INST_RESOLVE), System.nanoTime() - t0);
        return hinted;
    }

    private boolean checkArgs(final Poly<?, ?> userInstArgs, final Poly<?, ?> instArgs) {
        // OPEN-KEYED ARGS: a TYPE-valued key declares the SHAPE of the caller's keys, not a key set or an
        // arity, so there is nothing positional to compare — the binder keeps the caller's entries whole.
        if (Inst.Helper.isOpenArgs(instArgs))
            return true;
        // A REC-SHAPED CALL HAS NO ORDER TO COMPARE POSITIONALLY — and lstValue() on a rec throws, so an
        // ordered declaration is simply not a candidate for it.
        if (instArgs.isLst() && !userInstArgs.isLst())
            return false;
        if (instArgs.isEmpty()) // user_args and inst_args have no values, easy true
            return userInstArgs.isEmpty();
        if (userInstArgs.count() > instArgs.count()) // user_args can be less dude to {?}-zeroable inst_args
            return false;
        if (instArgs.isLst()) {
            final List<Obj> instList = instArgs.lstValue();
            final List<Obj> userList = userInstArgs.lstValue();
            for (int i = 0; i < instList.size(); i++) {
                final Obj instArg = instList.get(i);
                if (instArg.isObjCall())
                    continue;
                if (userList.size() <= i) {
                    if (!instList.get(i).tid().isZeroable())
                        return false;
                } else {
                    final Obj userArg = userList.get(i);
                    if (userArg.isObjCall())
                        continue;
                    if (!Obj.Helper.specificType(userArg).baseTypeID().bimatches(Obj.Helper.specificType(instArg).baseTypeID()))
                        return false;
                }
            }
        } else {
            int counter = -1; // rec inst_args, but lst user_args (index by counter)
            for (final Map.Entry<Obj, Obj> entry : instArgs.recValue().entrySet()) {
                counter++;
                if (entry.getValue().isObjCall())
                    continue;
                final Obj userValue = userInstArgs.at(entry.getKey()).orElse(userInstArgs.at(jnt(counter)));
                if (userValue.isObjCall())
                    continue;
                if (userValue.isNoObj()) {
                    if (!entry.getKey().tid().isZeroable())
                        return false;
                } else if (!Obj.Helper.specificType(userValue).baseTypeID().bimatches(Obj.Helper.specificType(entry.getValue()).baseTypeID()))
                    return false;
            }
        }
        return true;
    }

    /**
     * Score an API instruction based on how specific its type signature is. Higher scores indicate
     * more specific (preferred) instructions.
     *
     * @param lhs      the left-hand-side object
     * @param userInst the user instruction being resolved
     * @param apiInst  the candidate API instruction
     * @return specificity score (higher is more specific)
     */
    private int scoreSpecificity(final Obj lhs, final Inst userInst, final Inst apiInst) {
        int score = 0;
        final fURI apiDomID = Obj.Helper.specificTypeId(apiInst.dom());
        final fURI apiRngID = Obj.Helper.specificTypeId(apiInst.rng());
        final fURI lhsID = Obj.Helper.specificTypeId(lhs);

        // domain specificity (most important - 1000 points)
        if (!apiDomID.isGeneric() && !apiDomID.hasPattern()) {
            score += 1000;
            // bonus for exact domain match (500 points), and a smaller one (250) for the lhs's own base form: a value of a
            // named type can pass more than one base domain (ex::[o=>12] is a rec, and an int through its fiber o), and
            // the form it is made of must win that tie, or the pick falls to the order the overloads were registered in
            if (lhsID.basePath().equals(apiDomID.basePath())) {
                score += 500;
            } else if (lhs.baseTypeID().basePath().equals(apiDomID.basePath())) {
                score += 250;
            }
            // bonus for more specific dom/rng matches. A REFINED DOMAIN IS A CLAIM ABOUT THE INPUT, so it outranks a base
            // one only for an input known to be in it (inRefinement). An input that says nothing -- the noobj a code is
            // resolved from, a generic #, a bag the type system reads as noobj -- is admitted by every domain, and with
            // the bonus unearned the complex sum (+1000) outranked the exact int sum (+500) for every such input: an
            // inst body's last window {2,3}, the lanes of sum{2}(), sum@3 in a shard (Marko, 2026-11-22: "sum is broken")
            final boolean refinedDom = !apiInst.dom().isBaseType();
            if (!refinedDom || this.inRefinement(lhs, lhsID, userInst, apiInst)) {
                if (refinedDom)
                    score += 500;
                if (!apiInst.rng().isBaseType())
                    score += 500;
            }
        }

        // Argument specificity (500 points for non-generic first arg)
        if (!apiInst.args().isEmpty() && !userInst.args().isEmpty()) {
            final Obj apiFirstArg = apiInst.arg(0);
            final Obj userFirstArg = userInst.arg(0);
            if (apiFirstArg != null && !apiFirstArg.isNoObj() && !Obj.Helper.specificTypeId(apiFirstArg).isGeneric()) {
                score += 500;
                // Bonus for exact argument match (250 points)
                if (userFirstArg != null && !userFirstArg.isNoObj()
                        && Obj.Helper.specificTypeId(userFirstArg).basePath().equals(Obj.Helper.specificTypeId(apiFirstArg).basePath())) {
                    score += 250;
                }
            }

            // Range-to-argument alignment (critical for as() instructions specifically)
            // When user passes a Type argument to as(), heavily favor instructions whose range matches that type
            if (Obj.Helper.specificTypeId(apiInst).basePath().equals(AS_INST_TID) && userFirstArg != null && (userFirstArg.isNoObj() || userFirstArg.isType()) && !apiRngID.isGeneric()) {
                final fURI requestedTypeVid = Obj.Helper.specificTypeId(userFirstArg);
                if (!requestedTypeVid.isGeneric() && apiRngID.basePath().equals(requestedTypeVid.basePath())) {
                    // Huge bonus: the API's output type matches what the user asked for
                    score += 2000;
                }
            }
        }

        // Range specificity (100 points - less important than dom/args)
        if (!apiRngID.isGeneric()) {
            score += 100;
        }

        // Function-body bonus: a candidate with an inst-f is preferable to one without
        if (apiInst.hasf()) {
            score += 50;
        }

        return score;
    }

    /**
     * WHETHER THE INPUT IS KNOWN TO BE IN A CANDIDATE'S REFINED DOMAIN -- the evidence a refined signature's specificity
     * rests on ({@link #scoreSpecificity}). The call may state it ({@code sum?<=cmplx{*}()}), or the lhs may be typed it
     * (a {@code cmplx{2}::T} at compile time, a {@code cmplx::[1.0,2.0]} value). A value is in it when the domain's
     * predicate admits it ({@code 5} is a nat), and a bag when every member is: a bag's own type says nothing (the window
     * {@code {2,3}} reads as noobj), so its members are the evidence, and the first that is not ends the test. A type
     * names no member, so it is evidence only by name ({@code int{3}::T} is not a nat), and noobj is evidence of nothing.
     */
    private boolean inRefinement(final Obj lhs, final fURI lhsID, final Inst userInst, final Inst apiInst) {
        final fURI refined = Obj.Helper.specificTypeId(apiInst.dom()).basePath();
        if (lhsID.basePath().equals(refined)
                || (userInst.hasDom() && Obj.Helper.specificTypeId(userInst.dom()).basePath().equals(refined)))
            return true;
        if (lhs.isType() || lhs.isNoObj())
            return false;
        final Obj unit = apiInst.dom().c(cInt.ONE());
        final Iterator<Obj> members = lhs.stream().iterator();
        if (!members.hasNext())
            return false;
        while (members.hasNext())
            if (!members.next().c(cInt.ONE()).test(unit))
                return false;
        return true;
    }
}
