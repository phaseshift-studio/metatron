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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.isa.AbstractInstSetTest;
import studio.phaseshift.metatron.isa.m.math.cat.CatTheoryCompiler;
import studio.phaseshift.metatron.isa.m.Law;
import studio.phaseshift.metatron.isa.m.math.cat.catInstSet;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.util.MTronException;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;

/**
 * The §3 obligations table — both directions. {@code obligations(shape)} is the forward traversal (the laws a shape
 * demands of a cut shipped to it); {@code admissible(laws)} is the reverse (the shapes a law set permits), and they
 * are adjoint: {@code shape ⊨ laws ⟺ laws ⊒ obligations(shape)}. {@link PlanBuilder#create()} validates a cut plan by
 * the same table, replacing the hard-coded "distributed ⇒ monoidic + commutative" coupling.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ObligationsTest extends AbstractInstSetTest {

    public ObligationsTest() {
        super(catInstSet::new);
    }

    @BeforeAll
    public static void importAllInstSets() {
        InstSet.importInstSet(f("#"));
        InstSet.importInstSet(f("/m/math"));
    }

    @BeforeEach
    public void installCategoryCompiler() {
        Machine.current().compiler(CatTheoryCompiler.single());
    }

    /**
     * The forward traversal — the §3 table, one row per shape. Each row: the shape % its obligation laws (sorted,
     * empty for the obligation-free shapes).
     */
    @ParameterizedTest
    @CsvSource(value = {
            "local   % ",
            "none    % ",
            "space   % ",
            "peer    % commutative,monoidic",
            "peers   % commutative,monoidic",
            "tree    % commutative,monoidic",
            "shuffle % monoidic",
            "chain   % monoidic",
            "star    % commutative,monoidic",
    }, delimiter = '%')
    public void testShapeObligations(final String shape, final String expectedLaws) {
        final Set<Law> obligations = Placement.Shape.valueOf(shape.toUpperCase()).obligations();
        final String rendered = obligations.stream().map(Law::name).sorted().collect(Collectors.joining(","));
        assertEquals(null == expectedLaws ? "" : expectedLaws, rendered, "obligations(" + shape + ")");
    }

    /**
     * The reverse traversal — {@code admissible(laws) = { shape : obligations(shape) ⊆ laws }}. No laws admit only
     * the obligation-free shapes; monoidic adds the monoidic-only shapes; monoidic + commutative admits all.
     */
    @Test
    public void testAdmissibleReverseTraversal() {
        assertEquals(Set.of(Placement.Shape.LOCAL, Placement.Shape.SPACE, Placement.Shape.NONE),
                Set.copyOf(Placement.Shape.admissible(Set.of())), "no laws admit only the free shapes");
        assertEquals(Set.of(Placement.Shape.LOCAL, Placement.Shape.SPACE, Placement.Shape.SHUFFLE, Placement.Shape.CHAIN, Placement.Shape.NONE),
                Set.copyOf(Placement.Shape.admissible(Set.of(Law.monoidic))), "monoidic admits the monoidic-only shapes");
        assertEquals(Set.of(Placement.Shape.values()),
                Set.copyOf(Placement.Shape.admissible(Set.of(Law.monoidic, Law.commutative))),
                "monoidic + commutative admits every shape");
    }

    /**
     * {@link PlanBuilder#create()} validates by the table: a tree shape's AT span demands monoidic + commutative; a
     * shuffle demands monoidic only; local demands nothing; and a POST-only placement demands nothing even on a tree.
     */
    @Test
    public void testPlanValidationUsesShapeRecs() {
        assertThrows(MTronException.class, () ->
                PlanBuilder.plan("shard").cut(Cut.reducer()).where(Placement.tree(1, Span.at())).create());
        assertThrows(MTronException.class, () ->
                PlanBuilder.plan("shard").cut(Cut.reducer(Law.monoidic)).where(Placement.tree(1, Span.at())).create());
        assertNotNull(PlanBuilder.plan("shard").cut(Cut.reducer(Law.monoidic, Law.commutative))
                .where(Placement.tree(1, Span.at())).create(), "a tree cut with both laws is admitted");
        assertNotNull(PlanBuilder.plan("shuffle").cut(Cut.reducer(Law.monoidic))
                .where(Placement.shuffle(Span.at())).create(), "a shuffle cut demands monoidic only");
        assertNotNull(PlanBuilder.plan("local").cut(Cut.reducer())
                .where(Placement.local(Span.at())).create(), "a local cut demands nothing");
        assertNotNull(PlanBuilder.plan("tail").cut(Cut.reducer())
                .where(Placement.tree(1, Span.after())).create(), "a POST-only placement demands nothing");
    }

    /**
     * An addressed placement round-trips through the renamed {@code shape}/{@code target} keys and remains a valid
     * {@code plan::T}.
     */
    @Test
    public void testAddressedPlacementIsPlanT() {
        final Plan plan = PlanBuilder.plan("move").cut(Cut.reducer(Law.monoidic, Law.commutative))
                .where(Placement.peer(f("ws://ginger.local:8555"), Span.at())).create();
        assertEquals(Placement.Shape.PEER, plan.where().get(0).shape());
        assertEquals(f("ws://ginger.local:8555"), plan.where().get(0).target());
        assertTrue(plan.test(catInstSet.PLAN_TYPE), "an addressed placement is a plan::T: " + plan);
    }
}
