/*
 * metatron: a distributed virtual machine and language
 *  Copyright (C) 2025- PhaseShift Studio, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.algebra.rewrite.plan;

import studio.phaseshift.metatron.algebra.Law;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.isa.m.mach.type.network.Signatures;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Inst;

import java.util.ArrayList;
import java.util.List;

import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.to_;
import static studio.phaseshift.metatron.isa.m.type.impl.MCode.code;

/**
 * PlanInterpreter — the plan vocabulary's application points: the distribution plan ({@link #SHARD_PLAN}) lowered by
 * {@link Shard}, and the cut-less relocation ({@link #bind}). The lowering body lives in {@link Shard} so it survives
 * the rule engine's move from plans to rewrite-rule lambdas: the pool's {@code law_shard} and the interpreter are two
 * doors onto the same emitter.
 *
 * <p>The one-cut distribution (Thm 19, {@code (a+b)·c = (a·c+b·c)·c}): guard on the live peer roster, match the chain
 * shape {@code [seed, {*}floatable, blocked cut, {*}tail]}, locate the cut, then emit the
 * {@code <seed>.branch?dom=T{*}&split=part(<place> => <run>.<fold>, …).<combiner>.<tail>} layout the processor runs.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class PlanInterpreter {

    /**
     * The one-cut distribution plan — {@code law_shard}, Thm 19 {@code (a+b)·c = (a·c+b·c)·c}: the anchor is a
     * blocked reducer whose partials recombine through a fold declaring {@code monoidic} (partials regroup) and
     * {@code commutative} (order-free merge) — its COMBINER, computed rather than declared
     * ({@link Law.Helper#combiner}): {@code sum} and {@code prod} recombine through themselves, {@code count} through
     * {@code sum}, {@code order(s)} through {@code kmerge(s)}, and a keyed {@code group([k⇒h])} pointwise — the rec
     * {@code sum} merges its partials by key, then each key folds through {@code h}'s combiner. The master recombines
     * behind the barrier and runs the tail at home; each peer takes one factor of the seed bulk with the floatable run
     * replicated and a local partial reduce.
     * <p>
     * The placement is a TREE: a peer re-plans the part it is handed over its own dialed peers while budget remains, and
     * recombines its children's partials through the same combiner. The budget is the plan's {@code hop} unless the
     * chain states one ({@code ?hop=n}); the default of one level is the star — the master's peers fold their parts
     * where they stand.
     */
    public static final Plan SHARD_PLAN = PlanBuilder.plan("law_shard")
            .cut(Cut.reducer(Law.monoidic, Law.commutative))
            // THE ORIGIN'S TAIL, stated in the axes: the home place alone, no depth, strided — so nothing ships and the
            // cut is re-applied in place. It stays a TAIL (the AT/POST spans, no SEED span and no regrouping set), which
            // is what keeps the plan's gather from becoming a lane.
            .place(PlaceSet.local()).hop(0, cInt.ANY()).split(Partition.stream())
            .where(Placement.local(Span.at(), Span.after()))
            // THE FAN-OUT that factors the seed: the dialed set, one level deep, strided
            .place(PlaceSet.peer()).hop(1, cInt.ANY()).split(Partition.stream())
            .where(Placement.tree(1, Span.seed().partition(), Span.before().floatable().withoutSeed(), Span.at()))
            .create();

    private PlanInterpreter() {
    }

    /**
     * Lower the plan onto {@code code} — the single application point, over this machine's {@link Env} snapshot.
     */
    public static Code lower(final Plan plan, final Code code) {
        return apply(plan, code, Env.snapshot());
    }

    /**
     * The one body (§12.1): a plan with a form cut distributes ({@link Shard#apply}); a cut-less plan relocates
     * ({@link #relocate}, the {@link #bind} sibling); a cut that names no form is the identity.
     */
    public static Code apply(final Plan plan, final Code code, final Env env) {
        if (null == plan.cut())
            return relocate(plan, code, env);
        return null == plan.cut().formOrNull() ? code : Shard.apply(plan, code, env);
    }

    /**
     * The cut-less operator (§12.1): a distribution rule applied to code. Requires a plan with no cut; a cut plan is
     * {@link #lower}'s, so it is declined — the code passes through unchanged, the same identity a no-match plan is.
     * The relocation itself is emitted by {@link #relocate}; whether the code is placeable (has an origin) is the
     * lift's concern, not the operator's, so a machine that answers nowhere can still ship to a peer it dials.
     */
    public static Code bind(final Plan plan, final Code code) {
        if (null != plan.cut())
            return code;
        return apply(plan, code, Env.snapshot());
    }

    /**
     * The cut-less distribution rule (§12.1): the site is the decision's span, not a cut. A {@code move} decision
     * (a {@code chain}/{@code star} shape with a {@code target}) ships the whole code to that place — a hop is a
     * re-compilation in a new environment (§6.3), so the receiver re-plans the program on compile. The target's
     * scheme must resolve a dialer in the ISA ({@link Signatures#resolves}); other cut-less shapes, and a target
     * nothing can dial, are the identity: the code stands where it stood.
     */
    private static Code relocate(final Plan plan, final Code code, final Env env) {
        final Placement decision = plan.where().stream().findFirst().orElse(null);
        // an admissible move: the decision must name a target whose scheme resolves a dialer in the ISA — no dial to
        // decide otherwise. Declined as the identity, like every other cut-less shape without a target.
        if (null == decision || null == decision.target() || !Signatures.resolves(decision.target().scheme()))
            return code;
        // a move hop: append the to(<target>) terminal instruction, which ships the WHOLE code as a program — the
        // transport wraps start(code).to(<>) and the receiver re-plans it (§6.3) — never the code's value.
        final List<Inst> insts = new ArrayList<>(code.insts());
        insts.add(to_(decision.target()).tryToInst().asInst());
        return code(insts);
    }
}
