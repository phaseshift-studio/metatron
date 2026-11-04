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

package studio.phaseshift.metatron.isa.m.math.geo;

import studio.phaseshift.metatron.algebra.Law;
import studio.phaseshift.metatron.algebra.LawTable;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;

import static studio.phaseshift.metatron.isa.m.math.geo.geoInstSet.*;
import static studio.phaseshift.metatron.Tokens.BOOL_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.HAS_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec0;

/**
 * The law table of {@code /m/math/geo} — the spatial family's declared process laws, collocated.
 *
 * <p>A sibling to {@code mInstSetLawTable} and {@code mathInstSetLawTable}: a table is a singleton that registers the
 * PROCESS laws of its family's instructions in its constructor, and those declarations are what the rewriter stage and
 * {@code morphism::T} read. The point of declaring them is not documentation — it is that a rewrite may only move,
 * drop, replicate or exchange an instruction whose laws license it, so an undeclared instruction is an instruction the
 * optimizer must leave alone.
 *
 * <h2>What the geo family actually obeys</h2>
 *
 * <ul>
 *   <li><b>{@code geo:distance}</b> — {@code commutative} (the geodesic distance to \\(b\\) is the distance to \\(a\\)),
 *       {@code pure}. Symmetry is what lets a native pushdown canonicalize the argument order, so two callers with the
 *       operands swapped reach the same native operator (and the same index probe) instead of two.</li>
 *   <li><b>{@code geo:intersects}</b> — {@code commutative}, {@code idempotent} (\\(a \cap a = a\\)), {@code pure}: a
 *       semilattice-flavored predicate, which is what makes a conjunction of geo predicates order-insensitive — the
 *       property a planner needs to reorder them freely.</li>
 *   <li><b>{@code geo:contains} / {@code geo:inside}</b> — each other's INVERSE as relations,
 *       \\(a \\sqsupset b \\iff b \\sqsubset a\\). This is the one DECLARED inverse row in the table, and it is the one
 *       that pays: a space that implements only {@code ST_Within} answers {@code geo:contains} by swapping the
 *       operands, and the rewrite that does the swap is licensed by exactly this row.</li>
 *   <li><b>{@code geo:envelope}</b> — {@code pure}. It is the bbox projection every exact predicate performs first;
 *       declaring it pure is what allows the rewriter to fuse a redundant envelope-guard into the predicate.</li>
 *   <li><b>{@code geo:kind}, {@code geo:coordinates}, {@code geo:geometries}</b> — {@code pure}: structural
 *       projections, no effect, freely reorderable around the measures.</li>
 *   <li><b>{@code geo:area}, {@code geo:length}</b> — {@code pure}, and <b>{@code blocked}</b>: they are REDUCERS
 *       (many positions \\(\to\\) one number), and a reduce anchors a coefficient in place rather than letting it float
 *       past. Declaring them floatable would license a shard that measures each partition and sums the measures, which
 *       is only true for a PARTITION of the geometry (additivity), not for an arbitrary regroup.</li>
 * </ul>
 *
 * <h2>What is deliberately NOT declared</h2>
 *
 * <p>No carrier algebra. A geometry is not a ring, a monoid or a lattice: there is no {@code plus} of two geometries in
 * this family, and the measures are homomorphisms <em>to</em> the real field
 * (\\((a \\uplus b).\\mathrm{area} = a.\\mathrm{area} + b.\\mathrm{area}\\)), which is a statement about a UNION
 * instruction this family does not yet have. So {@link #typeLawsUncached(Type)} answers the empty rec, and the
 * additivity law stays documented here rather than declared — a table must not assert an operation nothing implements.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class geoInstSetLawTable extends LawTable {

    /**
     * The singleton — the geo family has exactly one law table. Its construction self-registers the table under
     * {@link geoInstSet#GEO_ISA_TID}, which is how {@code LawTable.lawsOf} dispatches a geo instruction to it.
     */
    public static final geoInstSetLawTable INSTANCE = new geoInstSetLawTable();

    private geoInstSetLawTable() {
        super(GEO_ISA_TID);
        loadMeasures();
        loadPredicates();
        loadProjections();
    }

    private void loadMeasures() {
        // a distance is symmetric: the geodesic separation of a and b does not care which was written first
        entry(DISTANCE_INST_TID.dom(GEOMETRY_TID).rng(studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_METER_TID),
                null, Law.commutative, Law.pure);
        // the two measures are REDUCERS over a geometry's positions: pure, but not floatable (additivity holds only
        // over a partition of the geometry, which is a union law this family does not yet declare)
        entry(AREA_INST_TID.dom(GEOMETRY_TID).rng(studio.phaseshift.metatron.Tokens.REAL_TID),
                null, Law.pure, Law.blocked);
        entry(LENGTH_INST_TID.dom(GEOMETRY_TID).rng(studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_METER_TID),
                null, Law.pure, Law.blocked);
    }

    private void loadPredicates() {
        // ONE predicate: `has` answers the container when the arg is inside it, and noobj otherwise. A filter, so a
        // rewrite may move or duplicate it; there is no inverse row any more, because there is no second verb —
        // `a.has(b)` and `b` being inside `a` are the same question, and a space that implements only ST_Within
        // answers it by swapping the operands, which is a pushdown's business, not a law's.
        entry(HAS_INST_TID.dom(GEOMETRY_TID).rng(GEOMETRY_TID.maybe()), INSIDE_INST_TID.dom(GEOMETRY_TID).rng(GEOMETRY_TID.maybe()),
                Law.pure);
        entry(INSIDE_INST_TID.dom(GEOMETRY_TID).rng(GEOMETRY_TID.maybe()), HAS_INST_TID.dom(GEOMETRY_TID).rng(GEOMETRY_TID.maybe()),
                Law.pure);
    }

    private void loadProjections() {
        // the structural projections: no effect, so a rule may move them across a measure
        entry(COORDINATES_INST_TID.dom(GEOMETRY_TID).rng(studio.phaseshift.metatron.Tokens.LST_TID), null, Law.pure);
        entry(WKT_INST_TID.dom(GEOMETRY_TID).rng(studio.phaseshift.metatron.Tokens.STR_TID), null, Law.pure);
    }

    /**
     * NO CARRIER ALGEBRA. A geometry is not a ring, a monoid or a lattice, and this family implements no union of
     * geometries — so there is no theory instance to declare on the carrier. The measures' additivity over a partition
     * is stated in the class notes and waits for the union instruction that would make it true.
     */
    @Override
    protected Rec typeLawsUncached(final Type type) {
        return rec0();
    }
}
