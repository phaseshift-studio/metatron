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
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;

/**
 * Plan — a value, not a closure: the assembled result of {@link PlanBuilder#create()}. It is the declarative
 * form of a rewrite — the data {@link PlanInterpreter} lowers to code — and the Java-side shape of the
 * {@code plan::T} rec: category-native, the morphism of the rewrite category ({@code path → path}),
 * introspectable and writable in mtron rather than Java.
 *
 * <p>A Plan is itself the {@code rec}, and every plan has the one shape
 * {@code [name => uri(name), cut => cutRec, route => lst(routeRecs)]}. What distinguishes a local plan from a
 * distribution is what its cut names — a form and laws, a theory role, or process laws — and where its routes
 * send it. Its tid is {@code plan::T}. Every accessor reads {@code this.at(key)}; there are no Java fields.
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

    public Cut cut() {
        final Obj cut = this.at(CUT);
        return cut.isNoObj() ? null : Cut.of(cut.asRec());
    }

    public List<Route> routes() {
        final Obj routes = this.at(ROUTE);
        return routes.isNoObj() ? List.of() : routes.asLst().elements().map(o -> Route.of(o.asRec())).toList();
    }

    /**
     * Reconstruct a Plan from a raw {@code rec} — the read-side of the same {@code name/cut/route} encoding.
     */
    public static Plan of(final Rec rec) {
        return new Plan(new LinkedHashMap<>(rec.jvm()), rec.tid(), rec.vid());
    }
}
