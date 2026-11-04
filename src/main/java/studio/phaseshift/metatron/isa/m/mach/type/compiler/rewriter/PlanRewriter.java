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

package studio.phaseshift.metatron.isa.m.mach.type.compiler.rewriter;

import studio.phaseshift.metatron.algebra.Law;
import studio.phaseshift.metatron.algebra.LawTable;
import studio.phaseshift.metatron.algebra.mInstSetLawTable;
import studio.phaseshift.metatron.algebra.mathInstSetLawTable;
import studio.phaseshift.metatron.algebra.rewrite.plan.*;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;

import java.util.*;
import java.util.stream.Collectors;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.MAP_INST_TID;
import static studio.phaseshift.metatron.isa.m.mach.machInstSet.MACH_INST_TID;

/**
 * PlanRewriter — {@code plan_rewriter::T}, the plan engine as a rewriter: a {@link FixPointRewriter} whose rule set is
 * the twelve law-driven plans ({@link Plan} values built by {@link PlanBuilder}, lowered by {@link PlanInterpreter})
 * rather than the rewrites the machine's instsets register. Each plan is the declarative form of one rewrite — its
 * {@code cut} reads the algebra the instructions and types declare (a form and the laws its combiner needs, a theory
 * role, or a process law) and its {@code where} says what the rewrite does with the span it isolates — so no rule names
 * an instruction, and an instruction or type that declares its algebra is optimized, and over live peers distributed
 * ({@code law_shard}), for free.
 * <p>
 * The plans live here, beside the interpreter, so the plan engine is one body of work that needs nothing from the
 * category instset: {@code /m/math/cat} registers the same plans as its rewrite rules (and lifts them onto
 * {@code path::T}) without owning them. Only the plans run — a compiler that installs this rewriter gets the law-driven
 * family and none of the other instsets' rewrites.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class PlanRewriter extends FixPointRewriter {

    // THE LAW ROWS THE PLANS READ: the /m and /m/math process tables (a derived fold's definition, the code ring's
    // operations) register on class initialization — the category instset triggers them when it mounts, and the plan
    // engine must not depend on the category being mounted to read the same laws
    static {
        mInstSetLawTable.INSTANCE.getClass();
        mathInstSetLawTable.INSTANCE.getClass();
    }

    public PlanRewriter(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    /**
     * The plans, as this rewriter's rule set — in the order the category registers them, so the local laws settle the
     * chain before {@code law_shard} distributes it.
     */
    @Override
    public Set<Inst> rewrites() {
        return Rules.RULES;
    }

    /**
     * The one-cut distribution plan — {@code law_shard}, Thm 19 {@code (a+b)·c = (a·c+b·c)·c}: the anchor is a
     * blocked reducer whose partials recombine through a fold declaring {@code monoidic} (partials regroup) and
     * {@code commutative} (order-free merge) — its COMBINER, computed rather than declared
     * ({@link LawTable#combiner}): {@code sum} and {@code prod} recombine through themselves, {@code count} through
     * {@code sum}, {@code order(s)} through {@code kmerge(s)}, and a keyed {@code group([k⇒h])} pointwise — the rec
     * {@code sum} merges its partials by key, then each key folds through {@code h}'s combiner. The master recombines behind the barrier and runs the
     * tail at home; each peer takes one factor of the seed bulk with the floatable run replicated and a local partial
     * reduce. {@link PlanInterpreter#lower} consumes the cut.
     * <p>
     * The placement is a TREE: a peer re-plans the part it is handed over its own dialed peers while budget remains, and
     * recombines its children's partials through the same combiner. The budget is the plan's {@code hop} unless the
     * chain states one ({@code ?hop=n}); the default of one level is the star — the master's peers fold their parts
     * where they stand.
     */
    private static final Cut SHARD_CUT = Cut.reducer(Law.monoidic, Law.commutative);
    private static final Span SHARD_PRE = Span.before().floatable();
    private static final Span SHARD_AT = Span.at();
    private static final Span SHARD_POST = Span.after();
    public static final Plan SHARD_PLAN = PlanBuilder.plan("law_shard")
            .cut(SHARD_CUT)
            // THE ORIGIN'S TAIL, stated in the axes: the home place alone, no depth, strided — so nothing ships and the
            // cut is re-applied in place. It stays a TAIL (the AT/POST spans, no SEED span and no regrouping set), which
            // is what keeps the plan's gather from becoming a lane.
            .place(PlaceSet.local()).hop(0, cInt.ANY()).split(Partition.stream())
            .where(Placement.local(SHARD_AT, SHARD_POST))
            // THE FAN-OUT that factors the seed: the dialed set, one level deep, strided
            .place(PlaceSet.peer()).hop(1, cInt.ANY()).split(Partition.stream())
            .where(Placement.tree(1, Span.seed().partition(), SHARD_PRE.withoutSeed(), SHARD_AT))
            .create();

    /**
     * The local unit-law plan — cut the identity (the {@code id} role) and route it to {@code none}: the span
     * is deleted, the unit law's transform. No {@code law} selector — the theory names the role.
     */
    private static final Cut UNIT_CUT = Cut.role(ID);
    public static final Plan UNIT_PLAN = PlanBuilder.plan("theory_unit_removal")
            .cut(UNIT_CUT)
            .where(Placement.none(Span.at()))
            .create();

    /**
     * The involution plan — cut the self-inverse op ({@code law => [involution]}): the f·f ↦ ε fold.
     */
    private static final Cut INVOLUTION_CUT = Cut.law(Law.involution);
    public static final Plan INVOLUTION_PLAN = PlanBuilder.plan("theory_involution")
            .cut(INVOLUTION_CUT)
            .where(Placement.local(Span.at()))
            .create();

    /**
     * The idempotent plan — cut the idempotent op ({@code law => [idempotent]}): the f·f ↦ f fold.
     */
    private static final Cut IDEMPOTENT_CUT = Cut.law(Law.idempotent);
    public static final Plan IDEMPOTENT_PLAN = PlanBuilder.plan("law_idempotent")
            .cut(IDEMPOTENT_CUT)
            .where(Placement.local(Span.at()))
            .create();

    /**
     * The monoidic plan — cut a monoidic fold ({@code law => [monoidic]}): the f·f ↦ f fold on a reducer/join.
     */
    private static final Cut MONOIDIC_CUT = Cut.law(Law.monoidic);
    public static final Plan MONOIDIC_PLAN = PlanBuilder.plan("law_monoidic")
            .cut(MONOIDIC_CUT)
            .where(Placement.local(Span.at()))
            .create();

    /**
     * The inverse-cancellation plan — cut a declared inverse ({@code role => inverse}): the f·f⁻¹ ↦ ε fold on
     * the declared opposing edge.
     */
    private static final Cut INVERSE_CUT = Cut.role(INVERSE);
    public static final Plan INVERSE_PLAN = PlanBuilder.plan("inverse_cancellation")
            .cut(INVERSE_CUT)
            .where(Placement.local(Span.at()))
            .create();

    /**
     * The like-term plan — cut the additive op ({@code role => add}): the route-as-function form, where the
     * transform IS the ring's own addition (sum like terms).
     */
    private static final Cut BRANCH_CUT = Cut.role(ADD);
    public static final Plan BRANCH_COLLAPSE_PLAN = PlanBuilder.plan("branch_collapse")
            .cut(BRANCH_CUT)
            .where(Placement.local(Span.at()))
            .create();

    /**
     * The one-term plan — cut the additive identity ({@code role => zero}): the single addend is that addend
     * (the implicit zero drops out).
     */
    private static final Cut BRANCH_INLINE_CUT = Cut.role(ZERO);
    public static final Plan BRANCH_INLINE_PLAN = PlanBuilder.plan("branch_inline")
            .cut(BRANCH_INLINE_CUT)
            .where(Placement.local(Span.at()))
            .create();

    /**
     * The poset plan — cut a poset comparison ({@code law => [poset]}): adjacent filters on the same comparison
     * compose to the filter on the stricter bound.
     */
    private static final Cut POSET_CUT = Cut.law(Law.poset);
    public static final Plan POSET_PLAN = PlanBuilder.plan("law_poset")
            .cut(POSET_CUT)
            .where(Placement.local(Span.at()))
            .create();

    /**
     * The map-unwrap plan — cut the stream lift ({@code role => map}): the transparent map unwraps its value.
     */
    private static final Cut MAP_CUT = Cut.role(MAP_INST_TID.name());
    public static final Plan MAP_UNWRAP_PLAN = PlanBuilder.plan("form_map_unwrap")
            .cut(MAP_CUT)
            .where(Placement.local(Span.at()))
            .create();

    /**
     * The absorbing plan — cut an absorbing op ({@code law => [absorbing]}): everything before it is dead code.
     */
    private static final Cut ABSORBING_CUT = Cut.law(Law.absorbing);
    public static final Plan ABSORBING_PLAN = PlanBuilder.plan("law_absorbing")
            .cut(ABSORBING_CUT)
            .where(Placement.local(Span.at()))
            .create();

    /**
     * The derivation plan — cut a derived op ({@code role => derivation}): fold its primitive composition back
     * to the derived operation.
     */
    private static final Cut DERIVATION_CUT = Cut.role(DERIVATION);
    public static final Plan DERIVATION_PLAN = PlanBuilder.plan("derivation_contraction")
            .cut(DERIVATION_CUT)
            .where(Placement.local(Span.at()))
            .create();

    /**
     * The rewrite-name → plan registry — the bridge the path's out-edges look up when a rewrite rule has a
     * declarative plan ({@code cut}/{@code where}) rather than a bare lambda. A Plan is the {@code plan::T} rec
     * itself, so there is no serialization step: the value is handed out as-is. The insertion order is the rule
     * order (the category's registration order): the local laws first, {@code law_shard} last.
     */
    private static final Map<String, Plan> PLANS = new LinkedHashMap<>();

    static {
        PLANS.put(UNIT_PLAN.name(), UNIT_PLAN);
        PLANS.put(BRANCH_COLLAPSE_PLAN.name(), BRANCH_COLLAPSE_PLAN);
        PLANS.put(BRANCH_INLINE_PLAN.name(), BRANCH_INLINE_PLAN);
        PLANS.put(INVOLUTION_PLAN.name(), INVOLUTION_PLAN);
        PLANS.put(IDEMPOTENT_PLAN.name(), IDEMPOTENT_PLAN);
        PLANS.put(ABSORBING_PLAN.name(), ABSORBING_PLAN);
        PLANS.put(MONOIDIC_PLAN.name(), MONOIDIC_PLAN);
        PLANS.put(POSET_PLAN.name(), POSET_PLAN);
        PLANS.put(INVERSE_PLAN.name(), INVERSE_PLAN);
        PLANS.put(MAP_UNWRAP_PLAN.name(), MAP_UNWRAP_PLAN);
        PLANS.put(DERIVATION_PLAN.name(), DERIVATION_PLAN);
        PLANS.put(SHARD_PLAN.name(), SHARD_PLAN);
    }

    /**
     * The plan registered under a rewrite rule's name, or null when the rule is still a bare lambda.
     */
    public static Plan planOf(final String name) {
        return PLANS.get(name);
    }

    /**
     * THE PLAN RULES — one rewrite per registered plan, built once on first use and shared, so the fixpoint's rule-order
     * memo (keyed by rule identity) answers every pass from the same list. Each rule is its plan lowered by the
     * interpreter.
     */
    private static final class Rules {
        private static final Set<Inst> RULES = PLANS.values().stream()
                .map(plan -> InstSet.Helper.rewriter(MACH_INST_TID.extend(REWRITE).extend(plan.name()),
                        code -> PlanInterpreter.lower(plan, code)))
                .collect(Collectors.collectingAndThen(Collectors.toCollection(LinkedHashSet::new), Collections::unmodifiableSet));
    }
}
