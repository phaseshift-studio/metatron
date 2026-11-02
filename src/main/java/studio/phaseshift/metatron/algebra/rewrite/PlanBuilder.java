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

package studio.phaseshift.metatron.algebra.rewrite;

import studio.phaseshift.metatron.algebra.Law;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.util.MTronException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.math.cat.catInstSet.PLAN_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * PlanBuilder — the fluent entry point of the algebraically-driven rewrite DSL. Declare the {@link Cut cut}
 * (the anchor), let the {@link Span spans} be induced around it, bind them to {@link Placement placements}, and
 * {@link #create()} the {@link Plan} — a value {@link PlanInterpreter} lowers to code.
 *
 * <pre>{@code
 * final Cut  cut  = Cut.reducer(Law.monoidic, Law.commutative);
 * final Span pre  = Span.before().floatable();
 * final Span at   = Span.at();
 * final Span post = Span.after();
 *
 * final Plan shard = PlanBuilder.plan("law_shard")
 *         .cut(cut)
 *         .where(Placement.local(at, post))                                  // home: merge().<combiner>.<tail>
 *         .where(Placement.tree(1, Span.seed().partition(), pre.withoutSeed(), at))  // [to(a), to(b), ...]
 *         .create();
 * }</pre>
 *
 * <p>A decision can also be stated in its axes — {@link #place}, {@link #hop}, {@link #split} — which bind to the
 * {@link #where} that follows them: the place set says where the shares go, the hop says how deep the application tree
 * may run, and the split says what counts as one share. An axis left unstated is read off the placement being bound, so
 * a builder states only what it changes:
 *
 * <pre>{@code
 * final Plan shard = PlanBuilder.plan("law_shard")
 *         .cut(cut)
 *         .where(Placement.local(at, post))
 *         .place(PlaceSet.peer()).hop(1, cInt.ANY()).split(Partition.stream())
 *         .where(Placement.tree(1, Span.seed().partition(), pre.withoutSeed(), at))
 *         .create();
 * }</pre>
 *
 * <p>
 * A plan with a {@code cut} is a semantic rule; a plan with no {@code cut} is a distribution rule — the relocation
 * it authors is its {@code where}, and it has exactly one {@link Placement}. {@link #create()} derives the plan's law
 * obligations from each decision's axes — the rewriter does not trust the author. A cut re-applied behind an
 * off-machine recombine must declare every law the decision's place set, hop and split demand
 * ({@code obligations(place, hop, split) ⊆ cut.laws}, Thm 19's hypothesis), and it refuses the plan when it does not.
 * The atom-level obligations (a {@code floatable} PRE span, a {@code blocked} AT span) are carried on the spans and
 * enforced by the rewriter at match time, not here.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class PlanBuilder {

    private final String name;
    private Cut cut = null;
    private final List<Placement> placements = new ArrayList<>();
    private PlaceSet place = null;
    private Hop hop = null;
    private Partition split = null;

    private PlanBuilder(final String name) {
        this.name = name;
    }

    public static PlanBuilder plan(final String name) {
        return new PlanBuilder(name);
    }

    public PlanBuilder cut(final Cut cut) {
        this.cut = cut;
        return this;
    }

    /**
     * The place axis of the decision the NEXT {@link #where} binds — where its shares may go (§2). An axis stated here
     * and not stated in the placement wins; one left unstated is read off the placement itself.
     */
    public PlanBuilder place(final PlaceSet place) {
        this.place = place;
        return this;
    }

    /**
     * The depth axis of the decision the NEXT {@link #where} binds — the application tree's height and arity (§3).
     * {@code cInt.ANY()} is "the receiver's local out-degree", today's behavior; {@code cInt.ONE()} is a one-child
     * hop, the chain (a sequential forward, so the decision demands {@code monoidic} alone).
     */
    public PlanBuilder hop(final int height, final cInt arity) {
        this.hop = Hop.of(height, arity);
        return this;
    }

    /**
     * The partition axis of the decision the NEXT {@link #where} binds — what counts as one share (§4).
     */
    public PlanBuilder split(final Partition split) {
        this.split = split;
        return this;
    }

    /**
     * Bind a placement's spans to a decision. Pending axes ({@link #place}, {@link #hop}, {@link #split}) belong to
     * this placement and are consumed here, so a plan states each decision's axes immediately before the spans they
     * describe.
     *
     * <p><b>A regrouping place set binds to a FAN-OUT, not to the gather.</b> The origin's tail ({@code AT}/{@code POST})
     * re-applies the cut at home and is a single home place; the placement that factors the seed is the one that
     * carries a {@code SEED} span. Binding a place set that regroups ({@link PlaceSet#regroups()}) to a placement with
     * no seed span would silently turn the plan's gather into a lane, so it is refused here — the axes are stated
     * before the {@code where} they belong to.
     */
    public PlanBuilder where(final Placement placement) {
        if (null == this.place && null == this.hop && null == this.split) {
            this.placements.add(placement);
            return this;
        }
        final PlaceSet place = null == this.place ? placement.place() : this.place;
        if (place.regroups() && placement.spans().stream().noneMatch(span -> Span.Kind.SEED == span.kind()))
            throw MTronException.of("a place set that regroups binds to a decision with no SEED span: the tail "
                    + "(AT/POST) re-applies the cut at home — state the axes before the where() they describe");
        this.placements.add(placement.axes(
                place,
                null == this.hop ? placement.hopAxis() : this.hop,
                null == this.split ? placement.split() : this.split));
        this.place = null;
        this.hop = null;
        this.split = null;
        return this;
    }

    /**
     * Assemble the {@link Plan} value, validating the law obligations the plan's decision demands (§3). Thm 19's
     * hypothesis — the reducer is re-applied over regrouped partials behind an order-free barrier — is DERIVED from
     * each placement's axes ({@link Placement#obligations()}): a cut that appears on a decision's {@code AT} span must
     * declare every law the axes demand ({@code obligations(place, hop, split) ⊆ cut.laws}). A plan with no cut is a
     * distribution rule and carries exactly one placement.
     */
    public Plan create() {
        if (null == this.name || this.name.isBlank())
            throw MTronException.of("plan name is required");
        if (null != this.place || null != this.hop || null != this.split)
            throw MTronException.of("decision axes are stated with no placement to bind: state them before the where() they describe");
        if (this.placements.isEmpty())
            throw MTronException.of("at least one placement is required");
        if (null == this.cut) {
            if (1 != this.placements.size())
                throw MTronException.of("a distribution plan (no cut) has exactly one placement, not %d", this.placements.size());
        } else {
            this.validateObligations();
        }
        final Map<Obj, Obj> jvm = new LinkedHashMap<>();
        jvm.put(uri(NAME), uri(this.name));
        if (null != this.cut)
            jvm.put(uri(CUT), this.cut);
        jvm.put(uri(WHERE), lst(this.placements.stream().map(p -> (Obj) p).toList()));
        return new Plan(jvm, PLAN_TID, null);
    }

    private void validateObligations() {
        for (final Placement placement : this.placements) {
            // the cut is re-applied at home only when the placement carries the AT span; a tail (POST) shipped
            // elsewhere demands nothing of the cut's laws
            if (placement.spans().stream().noneMatch(span -> Span.Kind.AT == span.kind()))
                continue;
            // obligations(place, hop, split) ⊆ cut.laws — the decision's axes, read as data, not hard-coded per target
            final List<Law> missing = placement.obligations().stream()
                    .filter(law -> !this.cut.declares(law))
                    .toList();
            if (!missing.isEmpty())
                throw MTronException.of("cut ships to shape %s but does not declare %s",
                        placement.shape().name().toLowerCase(), missing);
        }
    }
}
