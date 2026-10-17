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

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.math.cat.Law;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;

/**
 * Plan — a value, not a closure: the assembled result of {@link PlanBuilder#create()}. It is the declarative
 * form of a rewrite — the data a {@code Rewrite} interpreter lowers to {@code [chain...]==[to()...]} — and the
 * Java-side shape of the {@code plan::T} rec (category-native, so a plan is a morphism the {@code ^} lift
 * can carry, introspectable and writable in mtron rather than Java).
 *
 * <p>A Plan is itself the {@code rec}: a local law-driven plan is {@code [name => uri(name), law => uri(law)]},
 * a distribution plan is {@code [name => uri(name), cut => lst(cutRecs), route => lst(routeRecs)]}.
 * The concrete plan's tid is the {@code /m/math/cat/inst/rewrite/<name>_plan} refinement of the generic
 * {@code plan::T}. Every accessor reads {@code this.at(key)}; there are no Java fields.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class Plan extends MRec {

    public Plan(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    public String name() {
        return this.at(NAME).uriValue().name();
    }

    public List<Cut> cuts() {
        final Obj cuts = this.at(CUT);
        return cuts.isNoObj() ? List.of() : cuts.asLst().elements().map(o -> Cut.of(o.asRec())).toList();
    }

    public List<Route> routes() {
        final Obj routes = this.at(ROUTE);
        return routes.isNoObj() ? List.of() : routes.asLst().elements().map(o -> Route.of(o.asRec())).toList();
    }

    /**
     * The process law this local plan embodies — the selector for a non-distribution rewrite — or null when the
     * plan is a distribution (its {@code cut}/{@code route} are the selector/application).
     */
    public Law law() {
        final Obj law = this.at(LAW);
        return law.isNoObj() ? null : Law.valueOf(law.uriValue().name());
    }

    /**
     * Reconstruct a Plan from a raw {@code rec} — the read-side of the same {@code name/cut/route} encoding.
     */
    public static Plan of(final Rec rec) {
        return new Plan(new LinkedHashMap<>(rec.jvm()), rec.tid(), rec.vid());
    }
}
