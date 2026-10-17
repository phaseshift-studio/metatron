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

import studio.phaseshift.metatron.isa.m.math.cat.Law;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.util.MTronException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.MUTABLE;
import static studio.phaseshift.metatron.isa.m.math.cat.catInstSet.PLAN_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * PlanBuilder — the fluent entry point of the algebraically-driven rewrite DSL. Declare the {@link Cut cut}
 * (the anchor), let the {@link Span spans} be induced around it, bind them to {@link Route routes}, and
 * {@link #create()} the {@link Plan} — a value a {@code Rewrite} interpreter lowers to code.
 *
 * <pre>{@code
 * final Cut  cut  = Cut.reducer(Law.monoidic, Law.commutative);
 * final Span pre  = Span.before(cut).floatable();
 * final Span at   = Span.at(cut);
 * final Span post = Span.after(cut);
 *
 * final Plan shard = PlanBuilder.plan("shard")
 *         .cut(cut)
 *         .route(Route.local(at, post))                                        // [to()]
 *         .route(Route.peers(Span.seed().partition(), pre.withoutSeed(), at))   // [to(a), to(b), ...]
 *         .create();
 * }</pre>
 * <p>
 * {@link #create()} derives the plan's law obligations from its own shape — the rewriter does not trust the
 * author. A cut shipped to a non-local target and re-applied at home must declare {@link Law#monoidic} and
 * {@link Law#commutative} (Thm 19's hypothesis), and it refuses the plan when it does not. The atom-level
 * obligations (a {@code floatable} PRE span, a {@code blocked} AT span) are carried on the spans and enforced
 * by the rewriter at match time, not here.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class PlanBuilder {

    private final String name;
    private final List<Cut> cuts = new ArrayList<>();
    private final List<Route> routes = new ArrayList<>();

    private PlanBuilder(final String name) {
        this.name = name;
    }

    public static PlanBuilder plan(final String name) {
        return new PlanBuilder(name);
    }

    public PlanBuilder cut(final Cut cut) {
        this.cuts.add(cut);
        return this;
    }

    public PlanBuilder route(final Route route) {
        this.routes.add(route);
        return this;
    }

    /**
     * Assemble the {@link Plan} value, validating the law obligations the plan's shape demands. Thm 19's
     * hypothesis — the reducer is re-applied over regrouped partials behind an order-free barrier — is read
     * straight off the routes: a cut that appears on a non-local target's {@code AT} span must declare both
     * {@link Law#monoidic} and {@link Law#commutative}.
     */
    public Plan create() {
        if (null == this.name || this.name.isBlank())
            throw MTronException.of("plan name is required");
        if (this.cuts.isEmpty())
            throw MTronException.of("at least one cut is required");
        if (this.routes.isEmpty())
            throw MTronException.of("at least one route is required");
        this.validateDistributedObligations();
        this.resolveCutIndexes();
        final Map<Obj, Obj> jvm = new LinkedHashMap<>();
        jvm.put(uri(NAME), uri(this.name));
        jvm.put(uri(CUT), lst(this.cuts.stream().map(c -> (Obj) c).toList()));
        jvm.put(uri(ROUTE), lst(this.routes.stream().map(r -> (Obj) r).toList()));
        // the concrete plan's tid — the /m/math/cat/inst/rewrite/<name>_plan refinement of plan::T
        //plan.selfTID(CAT_ISA_TID.extend(INST).extend(REWRITE).extend(this.name + "_plan"));
        return new Plan(jvm, PLAN_TID, null);
    }

    /**
     * Resolve each span's inline cut recs to INDEXES into the plan's {@code cut} list: the plan declares
     * each cut once, and every span references it by position instead of repeating a copy. The plan stays
     * as small as its shape allows.
     */
    private void resolveCutIndexes() {
        final Set<Obj> resolved = Collections.newSetFromMap(new IdentityHashMap<>());
        for (final Route route : this.routes) {
            final Obj spans = route.at(SPAN);
            if (spans.isNoObj())
                continue;
            for (final Obj spanObj : spans.asLst().elements().toList()) {
                if (!resolved.add(spanObj))
                    continue;
                final List<Cut> cuts = Span.of(spanObj.asRec()).cuts();
                if (cuts.isEmpty())
                    continue;
                final List<Obj> indexes = cuts.stream().map(c -> (Obj) jnt(this.cuts.indexOf(c))).toList();
                spanObj.asRec().at(uri(CUT), lst(indexes), MUTABLE);
            }
        }
    }

    private void validateDistributedObligations() {
        for (final Cut cut : this.cuts) {
            final boolean distributed = this.routes.stream().anyMatch(route ->
                    Route.Target.LOCAL != route.target()
                            && route.spans().stream().anyMatch(span ->
                            Span.Kind.AT == span.kind() && span.cuts().contains(cut)));
            if (distributed) {
                if (!cut.declares(Law.monoidic))
                    throw MTronException.of("cut %s is distributed but does not declare monoidic", cut.form());
                if (!cut.declares(Law.commutative))
                    throw MTronException.of("cut %s is distributed but does not declare commutative", cut.form());
            }
        }
    }
}
