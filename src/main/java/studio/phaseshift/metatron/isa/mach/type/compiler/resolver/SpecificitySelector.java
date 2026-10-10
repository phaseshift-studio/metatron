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

package studio.phaseshift.metatron.isa.mach.type.compiler.resolver;

import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Poly;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.m.type.resolver.Binder;
import studio.phaseshift.metatron.isa.m.type.resolver.Resolver;
import studio.phaseshift.metatron.isa.m.type.resolver.Selector;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.util.Tuple;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static studio.phaseshift.metatron.Tokens.MONAD_IN;
import static studio.phaseshift.metatron.Tokens.MONAD_OUT;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.m.mInstSet.AS_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.NOOBJ_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_SPECIFICITY_SELECTOR_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * SpecificitySelector — the concrete {@code specificity_selector::T}: the selector the machine has
 * always used. It fetches the candidates a call may resolve to, cheaply pre-filters them, and then
 * scores each by how specific its signature is, taking the highest-scoring candidate its binder can
 * make concrete.
 *
 * Scoring criteria are in {@link #scoreSpecificity}: concrete domain, exact domain base-path match,
 * a non-base dom/rng, argument specificity (with an exact-argument bonus), the large bonus that
 * makes an as() candidate whose range names the requested type win, and a small bonus for carrying
 * a function body.
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
        if (userInst.hasf())
            return userInst;
        if (userInst.isNoObj())
            return null;
        /////////////////////// FROM/AS FAST RESOLUTION ///////////////////////
        if (!userInst.hasRng()) {
            final Optional<fURI> fromOrAt = Inst.Helper.isFromOrAtInstToUri(userInst);
            if (fromOrAt.isPresent()) {
                if (!fromOrAt.get().hasPattern()) {
                    final Obj fromOrAtObj = Machine.read(fromOrAt.get());
                    if (!fromOrAtObj.isNothing() && !fromOrAtObj.isCall()) {
                        userInst.logger().debug("fast from/at() resolution: %s", fromOrAt.get());
                        return Inst.Helper.bindQ(lhs, userInst, Machine.read(userInst.tid()).asInst().args(lst(fromOrAt.get().toUri())).rng(T(fromOrAtObj.typeId().maybeSome())));
                    }
                }
                return Inst.Helper.bindQ(lhs, userInst, Machine.read(userInst.tid()).asInst().args(lst(uri(fromOrAt.get()))).rng(T(ALL.maybeSome())));
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
                return Inst.Helper.bindQ(lhs, userInst, result.getFirst().asInst());
            }
        }
        /////////////////////////////////////////////////////////////////////
        final fURI basePath = userInst.tid().basePath();
        if (lhs.isPoly()) {
            final Obj fetched = Resolver.Helper.getPolyAutoInst(lhs.asPoly(), uri(basePath));
            if (fetched.isObjInst())
                return Inst.Helper.bindQ(lhs, userInst, fetched.asInst());
        }
        final long t0 = System.nanoTime();
        final Obj fetched = Machine.read(basePath);
        ScoringResolver.T_RESOLVE.addAndGet(System.nanoTime() - t0);

        // Collect viable candidates after cheap pre-filters (before the expensive bind + resolveArgs)
        final List<Inst> viable = fetched.stream()
                .filter(Obj::isObjInst)
                .map(Obj::asInst)
                .filter(i -> (userInst.tid().basePath().equals(AS_INST_TID) && 1 == i.args().count() && userInst.args().count() == 1) ||
                        (i.tid().hasQ(MONAD_IN) || i.tid().hasQ(MONAD_OUT)) ||
                        this.checkArgs(userInst.args(), i.args()))
                .filter(i -> !lhs.isInst() || (i.dom().baseTypeID().equals(Tokens.M_ISA_INST_TID)))
                .toList();

        if (viable.isEmpty())
            return null;

        // a single candidate needs no scoring: the legacy shortcut binds it directly, with the
        // replacement dom/rng hint and without the nominal-domain gate the scored path applies.
        if (viable.size() == 1)
            return binder.apply(lhs, userInst, this.hintReplace(viable.getFirst(), userInst));

        // score by specificity, then take the first candidate the binder can make concrete.
        return viable.stream()
                .filter(apiInst -> apiInst.dom().isGeneric() || !apiInst.dom().isNominal() || Obj.Helper.specificType(lhs).testByID(apiInst.dom()))
                .map(apiInst -> Tuple.Pair.with(this.scoreSpecificity(lhs, userInst, apiInst), apiInst))
                .sorted(Comparator.comparingInt((Tuple.Pair<Integer, Inst> scored) -> scored.get0()).reversed())
                .map(scored -> binder.apply(lhs, userInst, this.hintCompose(scored.get1(), userInst)))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    /**
     * Shape a candidate with the call's dom/rng hints, keeping the API's declared domain type and
     * taking the user's coefficient — the form the scored, multi-candidate path has always used.
     */
    private Inst hintCompose(final Inst candidate, final Inst userInst) {
        final long t0 = System.nanoTime();
        Inst hinted = userInst.hasDom() ? candidate.dom(candidate.dom().c(userInst.dom().c()).as()) : candidate;
        hinted = userInst.hasRng() ? hinted.rng(userInst.rng()) : hinted;
        hinted = userInst.tid().basePath().equals(AS_INST_TID)
                ? hinted.rng(userInst.arg(0).isNoObj() ? NOOBJ_TYPE : Obj.Helper.specificType(userInst.arg(0)))
                : hinted;
        ScoringResolver.T_RESOLVE.addAndGet(System.nanoTime() - t0);
        return hinted;
    }

    /**
     * Shape a candidate with the call's dom/rng hints by replacing its domain outright — the form
     * the single-candidate shortcut has always used. The two differ for a call that names an
     * explicit dom; unifying them is a semantic decision in its own right (see the resolver design
     * note), so the split is preserved and named here rather than hidden in a fast path.
     */
    private Inst hintReplace(final Inst candidate, final Inst userInst) {
        final long t0 = System.nanoTime();
        Inst hinted = userInst.hasDom() ? candidate.dom(userInst.dom()) : candidate;
        hinted = userInst.hasRng() ? hinted.rng(userInst.rng()) : hinted;
        hinted = userInst.tid().basePath().equals(AS_INST_TID)
                ? hinted.rng(userInst.arg(0).isNoObj() ? NOOBJ_TYPE : userInst.arg(0).asType())
                : hinted;
        ScoringResolver.T_RESOLVE.addAndGet(System.nanoTime() - t0);
        return hinted;
    }

    private boolean checkArgs(final Poly<?, ?> userInstArgs, final Poly<?, ?> instArgs) {
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
            // bonus for exact domain match (500 points)
            if (lhsID.basePath().equals(apiDomID.basePath())) {
                score += 500;
            }
            // bonus for more specific dom/rng matches
            if (!apiInst.dom().isBaseType())
                score += 500;
            if (!apiInst.rng().isBaseType())
                score += 500;
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
}
