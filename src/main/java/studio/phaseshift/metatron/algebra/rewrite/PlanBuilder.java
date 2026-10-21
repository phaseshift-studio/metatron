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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.math.cat.catInstSet.PLAN_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * PlanBuilder — the fluent entry point of the algebraically-driven rewrite DSL. Declare the {@link Cut cut}
 * (the anchor), let the {@link Span spans} be induced around it, bind them to {@link Route routes}, and
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
 *         .route(Route.local(at, post))                                          // home: merge().<combiner>.<tail>
 *         .route(Route.tree(1, Span.seed().partition(), pre.withoutSeed(), at))  // [to(a), to(b), ...]
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
    private Cut cut = null;
    private final List<Route> routes = new ArrayList<>();

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
        if (null == this.cut)
            throw MTronException.of("a cut is required");
        if (this.routes.isEmpty())
            throw MTronException.of("at least one route is required");
        this.validateDistributedObligations();
        final Map<Obj, Obj> jvm = new LinkedHashMap<>();
        jvm.put(uri(NAME), uri(this.name));
        jvm.put(uri(CUT), this.cut);
        jvm.put(uri(ROUTE), lst(this.routes.stream().map(r -> (Obj) r).toList()));
        return new Plan(jvm, PLAN_TID, null);
    }

    private void validateDistributedObligations() {
        final boolean distributed = this.routes.stream().anyMatch(route ->
                (Route.Target.PEER == route.target() || Route.Target.PEERS == route.target() || Route.Target.TREE == route.target())
                        && route.spans().stream().anyMatch(span -> Span.Kind.AT == span.kind()));
        if (distributed) {
            if (!this.cut.declares(Law.monoidic))
                throw MTronException.of("cut %s is distributed but does not declare monoidic", this.cut.form());
            if (!this.cut.declares(Law.commutative))
                throw MTronException.of("cut %s is distributed but does not declare commutative", this.cut.form());
        }
    }
}
