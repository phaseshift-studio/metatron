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

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Poly;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.m.type.resolver.Binder;

import java.util.Map;

import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_GENERIC_BINDER_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * GenericBinder — the concrete {@code generic_binder::T}: the binding algorithm the machine has
 * always run, lifted out of the resolver's per-candidate pipeline. Given one candidate (already
 * shaped with the call's dom/rng hints by the selector) it binds the candidate's generics to the
 * lhs, gates it on the lhs domain, resolves its arguments, seeds an initial inst's range, carries
 * the coefficient, and copies the user's api query maps — returning null at any step that rejects
 * the candidate.
 *
 * The resolve-stage sub-timings (T_RESOLVE/T_BIND/T_COMPOSE) stay on ScoringResolver so the
 * profile() report is unchanged; T_RESOLVE is advanced by the selector (the hint step) and this
 * class advances T_BIND and T_COMPOSE.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class GenericBinder extends MRec implements Binder {

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
    public Inst apply(final Obj lhs, final Inst userInst, final Inst candidate) {
        // 1. generics: bind the candidate's unresolved types to the lhs, and reject the candidate
        //    when an argument cannot be reconciled with the user's.
        final long t1 = System.nanoTime();
        Inst bound = lhs.isInst() ? candidate : Inst.Helper.bindGenerics(lhs, candidate, userInst);
        if (null == bound)
            return null;
        if (!lhs.isInst() && !Inst.Helper.filterOnDomainAllowUnique(lhs, bound))
            return null;
        // 2. arguments: resolve the user's args against the bound candidate (this is where nested
        //    code arguments are compiled), then seed an initial inst's range from its own argument
        //    and carry the call's coefficient onto the result.
        final long t2 = System.nanoTime();
        final Poly<?, ?> resolvedArgs = Inst.Helper.resolveArgs(userInst, bound, lhs);
        if (null == resolvedArgs)
            return null;
        bound = bound.args(resolvedArgs);
        bound = bound.isInitial() ? bound.rng(bound.arg(0).type()) : bound;
        bound = bound.c(userInst.c());
        final long t3 = System.nanoTime();
        ScoringResolver.T_BIND.addAndGet(t2 - t1);
        ScoringResolver.T_COMPOSE.addAndGet(t3 - t2);
        // 3. the user's api query maps (block, ...) ride onto the bound contract.
        return Inst.Helper.bindQ(lhs, userInst, bound);
    }
}
