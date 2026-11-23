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

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mach.type.analyzer.Analyzer;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.Binder;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Poly;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.GENERIC_BINDING;
import static studio.phaseshift.metatron.Tokens.INST_COMPOSITION;
import static studio.phaseshift.metatron.Tokens.RESOLVE;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mach.machInstSet.MACH_GENERIC_BINDER_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * GenericBinder — the concrete {@code generic_binder::T}: the binding algorithm the machine has
 * always run, lifted out of the resolver's per-candidate pipeline. Given one candidate (already
 * shaped with the call's dom/rng hints by the selector) it binds the candidate's generics to the
 * lhs, gates it on the lhs domain, resolves its arguments, seeds an initial inst's range, carries
 * the coefficient, and copies the user's api query maps — returning null at any step that rejects
 * the candidate.
 *
 * While a run is profiled ({@link Analyzer#PROFILE}) it times its two steps into the profile's resolve group —
 * {@code resolve/generic_binding} and {@code resolve/inst_composition} — beside the selector's
 * {@code resolve/inst_resolve}.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class GenericBinder extends MRec implements Binder {

    /**
     * the binder's two steps, timed while a run is profiled
     */
    private static final fURI GENERIC_BINDING_PROBE = f(RESOLVE).extend(GENERIC_BINDING);
    private static final fURI INST_COMPOSITION_PROBE = f(RESOLVE).extend(INST_COMPOSITION);
    private static final GenericBinder INSTANCE = new GenericBinder(mutableMap(), MACH_GENERIC_BINDER_TID, null);

    public static GenericBinder single() {
        return INSTANCE;
    }

    public GenericBinder() {
        this(mutableMap(), MACH_GENERIC_BINDER_TID, null);
    }

    public GenericBinder(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    @Override
    public List<Inst> apply(final Obj lhs, final List<Inst> users, final List<Inst> candidates) {
        // ONE BINDING FOR THE LENGTH OF THE SEQUENCE, starting from the pattern's declarations: a generic a candidate binds
        // holds for every candidate after it
        final Map<fURI, fURI> generics = Inst.Helper.declarations(candidates);
        if (null == generics)
            return null;
        final List<Inst> sequence = new ArrayList<>(candidates.size());
        Obj input = lhs;
        final Analyzer profile = Analyzer.PROFILE.get();
        for (int i = 0; i < candidates.size(); i++) {
            final Inst userInst = users.get(i);
            // 1. generics: bind the candidate's unresolved types to the lhs, and reject the candidate
            //    when an argument cannot be reconciled with the user's.
            final long t1 = Analyzer.Helper.start(profile);
            // binding copies the candidate (generics, args, coefficient): never at the definition's own address
            final Inst unaddressed = Inst.Helper.unaddressed(candidates.get(i));
            // an instruction lhs (higher-order) binds nothing; noobj is an Inst too (the ring's zero) but binds like any obj
            Inst bound = input.isObjInst() ? unaddressed : Inst.Helper.bindGenerics(input, unaddressed, userInst, generics, new HashMap<>());
            if (null == bound)
                return null;
            if (!input.isObjInst() && !Inst.Helper.filterOnDomainAllowUnique(input, bound.dom()))
                return null;
            // 2. arguments: resolve the user's args against the bound candidate (this is where nested
            //    code arguments are compiled), then seed an initial inst's range from its own argument
            //    and carry the call's coefficient onto the result.
            final long t2 = Analyzer.Helper.start(profile);
            final Poly<?, ?> resolvedArgs = Inst.Helper.resolveArgs(userInst, bound, input);
            if (null == resolvedArgs)
                return null;
            bound = bound.args(resolvedArgs);
            // AN INITIAL INST EMITS ITS ARGUMENT (start(x) answers x), so its range is read off it: a value's type, or —
            // since a call argument is computed before the inst runs — the type of what the call computes, any number of
            // it: start(5.plus(1)) answers an int, not a code, and start({1,2,3}.plus(1)) three of them (a call's rng is
            // per input, so its count is unknown here). Under ?block the arguments ride raw, so the call itself is what
            // it answers. A KEYED initial inst answers what its lanes answer — a branch of seeded lanes,
            // branch?<={0}(<p> => start(…)…) — which no single argument states, so its declared range stands
            bound = !bound.isInitial() || !bound.args().isLst() ? bound
                    : bound.rng(bound.arg(0).isObjCall() && !userInst.isBlocking()
                    ? T(bound.arg(0).rng().typeId().maybeSome())
                    : bound.arg(0).type());
            // THE CALL'S COEFFICIENT RIDES ON AS WRITTEN, {b}inst{d}: c(count) writes the whole count into the point and
            // drops a span it does not divide, so {2,3}plus(1) ran once at the point {2,3} instead of as its run
            bound = bound.tid(bound.tid().b(userInst.tid().b()).d(userInst.tid().d()));
            // THE RESOLVED CONTRACT IS THE CALL'S. The candidate's tid states its DECLARED argument types — `args=[#]`
            // for the generic cast, since it admits any target — while the inst now carries the arguments this call
            // actually resolved (`lst::T`). Complete the signature so the tid agrees with the args poly: the parser
            // does the same for an unspecialized call (Inst.Helper.fullSignature), and the frame a fail reports is
            // this tid, so a stale `[#]` names no contract at all.
            bound = Inst.Helper.fullSignature(bound);
            Analyzer.Helper.record(profile, GENERIC_BINDING_PROBE, t2 - t1);
            Analyzer.Helper.stop(profile, INST_COMPOSITION_PROBE, t2);
            // 3. the user's api query maps (block, ...) ride onto the bound contract.
            bound = Inst.Helper.bindQ(input, userInst, bound);
            sequence.add(bound);
            // the next call operates on what this one emits
            input = bound.rng();
        }
        return sequence;
    }
}
