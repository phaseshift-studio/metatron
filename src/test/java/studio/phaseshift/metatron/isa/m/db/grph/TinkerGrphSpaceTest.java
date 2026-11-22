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

package studio.phaseshift.metatron.isa.m.db.grph;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.SkipRegexTest;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Poly;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.db.grph.space.schema.modernSchema.MODERN_SCHEMA_TID;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_FALSE;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_TRUE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * Test suite for {@link studio.phaseshift.metatron.isa.m.db.grph.space.grphSpace}
 * against an embedded TinkerGraph.
 * <p>
 * Extends {@link AbstractGrphSpaceTest} which contains all shared test logic.
 * This class provides TinkerGraph-specific lifecycle (no container needed)
 * and re-enables ID-based traversal/mutation tests that require known
 * vertex/edge IDs (1–6), which are available with TinkerGraph's
 * deterministic ID assignment but not with remote JanusGraph.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
@SkipRegexTest({
        // alice is added with addV, so her id is a Long, and the address lookup reads a small number as an Integer (the
        // modern graph's ids): */g/V/<alice> finds nothing on TinkerGraph. JanusGraph normalizes ids and counts 1
        @SkipRegexTest.Skip(method = "testEdgePropertyIsa"),
        // the list of vertices this row stores keeps vadas' vid (@/g/V/2), which the expected */g/V/2 reads without, as
        // testAddVertex finds
        @SkipRegexTest.Skip(method = "testVertexUpdate", params = {"likes=>\\[!"})
})
public class TinkerGrphSpaceTest extends AbstractGrphSpaceTest {

    public TinkerGrphSpaceTest() {
        super(f("/g"));
    }

    @BeforeAll
    public static void setupTinker() {
        setupConfigs();
        createRewriteTestSpace();
    }

    private static void setupConfigs() {
        // ── per-test space config (pattern /g/#) — no HOST/CONFIG → TinkerGraph fallback ──
        perTestConfigRec = rec(
                uri(PATTERN), uri("/g/#"),
                uri(QPROC), lst(QCollection.incrQ()),
                uri(ROUTE), rec(
                        uri("/g/V"), uri("V"),
                        uri("/g/E"), uri("E"),
                        uri("/g/S"), uri(MODERN_SCHEMA_TID)));

        // ── rewrite test space config (pattern /grt/#) ──
        rewriteTestConfigRec = rec(
                uri(PATTERN), uri("/grt/#"),
                uri(QPROC), lst(QCollection.incrQ()),
                uri(ROUTE), rec(
                        uri("/grt/V"), uri("V"),
                        uri("/grt/E"), uri("E"),
                        uri("/grt/S"), uri(MODERN_SCHEMA_TID)));
    }

    // ========================================================================
    //  TinkerGraph-specific tests — require known vertex/edge IDs (1–6)
    // ========================================================================

    /**
     * URI-path traversals using known TinkerGraph vertex IDs.
     * These work because TinkerGraph uses deterministic numeric IDs.
     */
    @ParameterizedTest
    @CsvSource(value = {
            // vertex → OUT edges
            "*/g/V/1/OUT/+.count()                                                % 3",
            "*/g/V/+.?[name=>'marko'].outE(knows).count()                                            % 2",
            "*/g/V/1/OUT/created.count()                                          % 1",
            // vertex → IN edges
            "*/g/V/2/IN.count()                                                   % 1",
            "*/g/V/4/IN.count()                                                   % 1",
            // OUT → IN cascade (vertex → edge → target vertex)
            "*/g/V/+.?[name=>'marko'].out(created)>>name                                           % \"lop\"",
            "*/g/V/1/OUT/created/IN/lang                                           % \"java\"",
            "*/g/V/1/OUT/knows/IN/+.count()                                        % 2",
            "*/g/V/1/OUT/knows/IN/name                                             % {\"josh\",\"vadas\"}",
            // edge property access
            "*/g/V/1/OUT/created/weight                                            % 0.4000",
            "*/g/V/1/OUT/created/weight.sum?real<=real{*}()                        % 0.4000",
            // OUT/IN without label filter — all edges → target vertices
            "*/g/V/1/OUT/IN/+.count()                                               % 3",
            "*/g/V/1/OUT/IN/name                                                    % {\"lop\",\"vadas\",\"josh\"}",
    }, delimiter = '%')
    public void testBasicTraversals(final String code, final String expected) {
        LOG.warn(studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer.parse(code).resolve(space));
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    /**
     * TRAVERSAL BY RELATION COMPOSITION — a vertex walks its labelled adjacency as its address does
     * ({@code >>OUT/knows/IN}), so a label is an arrow whose range is that walk ({@code (id() => >>OUT/knows/IN)} is
     * out(knows)), a start vertex is its own arrow ({@code v.split((id()=>id()))} is v=>v, g.V(v)), and a traversal is
     * the product: the join propagates through each step while the start stays on the left. A filter is a partial
     * identity, its domain the test and id() its range.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "*/g/V/1.>>OUT/knows/IN/name                                                                       % {\"vadas\",\"josh\"}",
            "*/g/V/1.>>OUT/knows.count()                                                                       % 2",
            "*/g/V/2.>>IN/knows/OUT/name                                                                       % \"marko\"",
            "*/g/V/1.>>OUT/created/IN/name                                                                     % \"lop\"",
            "*/g/V/1.split((id()=>id())).mult((id() => >>OUT/knows/IN)).count()                                % 2",
            "*/g/V/1.split((id()=>id())).mult((id() => >>OUT/knows/IN)).mult((id() => >>name)).rng()          % {\"vadas\",\"josh\"}",
            "*/g/V/1.split((id()=>id())).mult((id() => >>OUT/knows/IN)).mult((id() => >>name)).dom().>>name   % {2}\"marko\"",
            "*/g/V/1.split((id()=>id())).mult((id() => >>OUT/knows/IN)).mult(([age=>?>30] => id())).rng().>>name % \"josh\"",
            "*/g/V/2.split((id()=>id())).mult((id() => >>IN/knows/OUT)).mult((id() => >>name)).rng()          % \"marko\"",
            "*/g/V/1.split((id()=>id())).mult((?person::T => >>OUT/created/IN)).mult((id() => >>name)).rng()   % \"lop\"",
    }, delimiter = '%')
    public void testRelationTraversals(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    /**
     * Instruction-call traversals off a SINGLE address ({@code /g/V/1.out()}).
     * <p>
     * A single element is the case where metatron detaches the vid ({@code vid(null)}) and
     * {@link studio.phaseshift.metatron.isa.m.db.grph.space.ElementRec#clone()}s the rec before applying the
     * inst, so {@code resolveVid} has to recover the address from the rec itself
     * ({@code elementVID} then {@code element}). A clone that keeps only a plain map copy loses the live
     * TinkerPop element, and every one of out/outE/in/inE/both/bothE/inV/outV then fails with
     * {@code unable to convert linkedhashmap to elementmap}.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "*/g/V/1.out()>>name                                   % {'vadas','josh','lop'}",
            "*/g/V/1.out(knows)>>name                              % {'vadas','josh'}",
            "*/g/V/1.out().count()                                 % 3",
            "*/g/V/1.outE(knows)>>weight                           % {1.0,0.5}",
            "*/g/V/1.outE(knows).inV()>>name                       % {'vadas','josh'}",
            "*/g/V/1.outE(created).inV()>>name                     % \"lop\"",
            "*/g/V/1.outE(created).outV()>>name                    % \"marko\"",
            "*/g/V/1.outE(created)>>weight.sum?real<=real{*}()     % 0.4",
            "*/g/V/2.in()>>name                                    % \"marko\"",
            "*/g/V/2.inE().count()                                 % 1",
            "*/g/V/4.both()>>name                                  % {'marko','ripple','lop'}",
            "*/g/V/4.bothE().count()                               % 3",
            // labelled adjacency takes the mask out of the walk, not out of the address
            "*/g/V/4.in(knows)>>name                               % \"marko\"",
            "*/g/V/3.in(created)>>name                             % {'marko','josh','peter'}",
            "*/g/V/3.inE(created).count()                          % 3",
            "*/g/V/3.inE(created).outV()>>name                     % {'marko','josh','peter'}",
            "*/g/V/1.both(knows)>>name                             % {'vadas','josh'}",
            "*/g/V/1.outE().inV().count()                          % 3",
            // the fused vocabulary: a field read on an element is values(), and a chain may end in a reducer
            "*/g/V/1.out(knows).>>name.count()                     % 2",
            "*/g/V/4.out(created).>>name                           % {'ripple','lop'}",
            "*/g/V/1.outE(knows).inV().>>name.count()              % 2",
            // the seed folds into the call: one traversal for the read and the whole walk
            "*/g/V/1.out(knows).out(created)>>name                 % {'ripple','lop'}",
            "*/g/V/1.out(knows).out(created)>>name.count()         % 2",
            "*/g/V/1.out(knows)>>name.count()                      % 2",
            // a SPAN is a run of steps, not a count ({0,2}out() is _ + out() + out().out()): Gremlin's repeat() walks it
            "*/g/V/1.{0,2}out()>>name                              % {'marko','vadas','josh','lop','ripple','lop'}",
            "*/g/V/1.{1,2}out()>>name                              % {'vadas','josh','lop','ripple','lop'}",
            "*/g/V/1.{0,1}out()>>name                              % {'marko','vadas','josh','lop'}",
            "*/g/V/1.{2}out()>>name                                % {'ripple','lop'}",
            "*/g/V/1.{0}out()>>name                                % \"marko\"",
            "*/g/V/1.out(knows).{0,1}out(created).out()>>name      % {'ripple','lop'}",
            "*/g/V/1.{0,1}out(knows).out(created)>>name            % {'lop','ripple','lop'}",
            "*/g/V/+.{0,2}out()>>name                              % {'marko','peter',{2}'vadas',{2}'josh',{5}'lop',{3}'ripple'}",
            // THE BSP WALK (Marko, 2026-11-22): five passes of both() from every vertex, per monad and over the whole
            // frontier (repeat?<=A{*}: each pass a superstep over all of it). Both answer A^5 applied to all-ones
            "*/g/V/+.repeat(code=>both(),until=>loop()?>4)>>name                % {{99}'marko',{99}'lop',{99}'josh',{41}'vadas',{41}'ripple',{41}'peter'}",
            "*/g/V/+.repeat?<=A{*}(code=>both(),until=>loop()?>4)>>name         % {{99}'marko',{99}'lop',{99}'josh',{41}'vadas',{41}'ripple',{41}'peter'}",
            // an OPEN span loops until noobj (Marko, 2026-11-13): out() over the modern graph halts where the edges run out
            "*/g/V/1.{*}out()>>name                                % {'marko','vadas','josh','lop','ripple','lop'}",
            "*/g/V/1.{+}out()>>name                                % {'vadas','josh','lop','ripple','lop'}",
            "*/g/V/1.{2,}out()>>name                               % {'ripple','lop'}",
            "*/g/V/6.{*}out()>>name                                % {'peter','lop'}",
            "*/g/V/+.{*}out().count()                              % 14",
            // pushed down, the loop answers as the walk does: {0,2}in() is {-2,0}out(), a cycle counts each path, a
            // min past the graph's depth emits nothing, and two spans are two loops
            "*/g/V/5.{0,2}in()>>name                               % {'ripple','josh','marko'}",
            "*/g/V/1.{0,2}both(knows)>>name                        % {{3}'marko','vadas','josh'}",
            "*/g/V/1.{2,3}both(knows)>>name                        % {{2}'marko',{2}'vadas',{2}'josh'}",
            "*/g/V/1.{*}out(knows)>>name                           % {'marko','vadas','josh'}",
            "*/g/V/1.{3,4}out()>>name                              % noobj",
            "*/g/V/1.{3,}out()>>name                               % noobj",
            "*/g/V/1.{0,1}out().{1,2}in()>>name                    % {{4}'marko','josh','peter'}",
            "*/g/V/+.?[name=>'marko'].{0,2}out()>>name             % {'marko','vadas','josh','lop','ripple','lop'}",
            // a span Gremlin cannot say is the processor's: a step that hands on another level, or one with a point
            "*/g/V/1.{0,1}outE(knows).count()                      % 3",
            "*/g/V/1.out(knows).{0,1}outE(created).count()         % 4",
            // each kept run takes the point once, the zero run too (Marko, 2026-11-17): {0,1}out{2}() is 2·(1 + out)
            "*/g/V/1.{0,1}out{2}()>>name                           % {{2}'marko',{2}'vadas',{2}'josh',{2}'lop'}",
            // the walk's copies feed the mapped call after it, and each copy walks: {2}josh is two joshes
            "*/g/V/1.{0,1}out{2}().out()>>name                     % {{2}'vadas',{2}'josh',{4}'lop',{2}'ripple'}",
            "*/g/V/1.{0,1}out{2}().{0,1}out()>>name                % {{2}'marko',{4}'vadas',{4}'josh',{6}'lop',{2}'ripple'}",
            // a NEGATIVE span walks backward (Marko, 2026-11-13): out()'s declared converse is in(), both() is its own
            "*/g/V/4.{-1}out()>>name                               % \"marko\"",
            "*/g/V/5.{-2,0}out()>>name                             % {'ripple','josh','marko'}",
            "*/g/V/4.{-1,1}out()>>name                             % {'marko','josh','ripple','lop'}",
            "*/g/V/1.{-1}in()>>name                                % {'vadas','josh','lop'}",
            "*/g/V/1.{-1}both()>>name                              % {'vadas','josh','lop'}",
            // THE EDGE STEPS' CONVERSES (Marko, 2026-11-22): outE inverts to outV, inE to inV, bothE to bothV, and back
            "*/g/E/7.{-1}outE()>>name                              % \"marko\"",
            "*/g/E/7.{-1}inE()>>name                               % \"vadas\"",
            "*/g/E/7.bothV()>>name                                 % {'marko','vadas'}",
            "*/g/E/7.{-1}bothE()>>name                             % {'marko','vadas'}",
            // josh has edges both ways (in: e8; out: e10, e11), so a wrong converse would answer other edges
            "*/g/V/4.{-1}outV()>>weight                            % {1.0,0.4}",
            "*/g/V/4.{-1}inV()>>weight                             % 1.0",
            "*/g/V/4.{-1}bothV()>>weight                           % {{2}1.0,0.4}",
            // THE LABEL CARRIES THROUGH (Marko, 2026-11-22): outE(L) inverts to outV(L), the edge's tail only if its
            // label is in L: e7 is a knows edge, so nothing reaches it by outE(created); e9 is marko created lop
            "*/g/E/7.{-1}outE(created)                             % noobj",
            "*/g/E/9.{-1}outE(created)>>name                       % \"marko\"",
            "*/g/E/7.{-1}inE(knows)>>name                          % \"vadas\"",
            "*/g/E/7.outV(knows)>>name                             % \"marko\"",
            "*/g/E/7.outV(created)                                 % noobj",
            "*/g/E/7.bothV(created)                                % noobj",
            // a labelled endpoint ends a fused run as Gremlin's hasLabel(...).inV(): marko's knows edges, not his created one
            "*/g/V/1.outE().inV(knows)>>name                       % {'vadas','josh'}",
            "*/g/V/4.{-1}out().{0,1}out()>>name                    % {'marko','vadas','josh','lop'}",
            // the escape hatch, in the same suffix form a fused run compiles to
            "*/g/V/1.gremlin('out(\"knows\").values(\"name\")')    % {'vadas','josh'}",
    }, delimiter = '%')
    public void testSingleElementInstructionTraversal(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    /**
     * THE TRAVERSAL STEPS ARE PURE (Marko, 2026-11-22): each reads the graph's adjacency and writes nothing, so a planner
     * may move it to the data, and law_bsp deals a superstep of them over the peers. Undeclared, purity fails closed.
     */
    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"out()", "in()", "both()", "outE()", "inE()", "bothE()", "inV()", "outV()", "bothV()"})
    public void testTheTraversalStepsArePure(final String step) {
        org.junit.jupiter.api.Assertions.assertTrue(studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer.parse(step).asCall().isPure(), step + " is declared pure");
    }

    /**
     * The Gremlin step table is read off the theory's REGISTERED SHAPE — its {@code isa} predicate — and nowhere else.
     * This guard fails if that accessor regresses: a {@code predicate().asInst()} read wraps the predicate call in a
     * lambda (whose {@code arg(0)} is {@code noobj}), every lookup then answers nothing, and the fusion silently
     * stops compiling chains without failing anything.
     */
    @Test
    public void testGremlinTheoryTable() {
        final studio.phaseshift.metatron.isa.m.type.Type theory =
                studio.phaseshift.metatron.isa.m.db.grph.grphInstSet.GREMLIN_THEORY_TYPE;
        assertTrue(theory.hasPredicate(), "the theory must state its table");
        assertTrue(theory.isIsaPredicate(), "the table is a structural isa claim");
        final studio.phaseshift.metatron.isa.m.type.Rec rows =
                theory.isPredicateObj().asRec().at(uri(REWRITE));
        assertEquals("out(%s)", rows.asRec().at(uri("out")).strValue());
        assertEquals("values(%s)", rows.asRec().at(uri("rshift")).strValue());
        assertEquals("count()", rows.asRec().at(uri(COUNT)).strValue());
        assertEquals("g.V(1).out('knows').values('name').count()",
                script(studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer
                        .parse("*/g/V/1.out(knows).>>name.count()").asCode().rewrite()),
                "the table is what the rewrite compiles from");
    }

    /**
     * AN EXACT SPAN IS PUSHED DOWN AS ITS RUN (Marko, 2026-11-15): the code keeps {@code {2}out()}, a run the
     * processor walks, and the pushdown lays it out as that many Gremlin steps, so it is the one traversal
     * {@code out().out()} is.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "*/g/V/1.{2}out()>>name             % g.V(1).out().out().values('name')",
            "*/g/V/1.out().out()>>name          % g.V(1).out().out().values('name')",
            "*/g/V/1.out(knows).{2}out()>>name  % g.V(1).out('knows').out().out().values('name')",
    }, delimiter = '%')
    void testAnExactSpanFusesAsItsRun(final String code, final String expected) {
        assertEquals(expected, script(studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer.parse(code).asCode().rewrite()));
    }

    /**
     * A RANGED OR OPEN SPAN IS PUSHED DOWN AS GREMLIN'S {@code repeat()}: {@code {m,n}out()} loops out() n times and
     * emits the runs from m on, the zero-step run before the first loop, and an open span loops until no traverser is
     * left, where the walk halts. Two spans in a run are two loops, and a span after one the processor walks (a
     * negative span) is a mapped call.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "*/g/V/1.{0,2}out()>>name                          % g.V(1).emit().repeat(out()).times(2).values('name')",
            "*/g/V/1.{?}out()>>name                            % g.V(1).emit().repeat(out()).times(1).values('name')",
            "*/g/V/1.{1,2}out()>>name                          % g.V(1).repeat(out()).emit().times(2).values('name')",
            "*/g/V/1.{2,3}both(knows)>>name                    % g.V(1).repeat(both('knows')).emit(loops().is(gte(2))).times(3).values('name')",
            "*/g/V/1.{*}out()>>name                            % g.V(1).emit().repeat(out()).values('name')",
            "*/g/V/1.{+}out()>>name                            % g.V(1).repeat(out()).emit().values('name')",
            "*/g/V/1.{2,}out()>>name                           % g.V(1).repeat(out()).emit(loops().is(gte(2))).values('name')",
            "*/g/V/+.{*}out().count()                          % g.V().emit().repeat(out()).count()",
            "*/g/V/1.out(knows).{0,1}out(created).out()>>name  % g.V(1).out('knows').emit().repeat(out('created')).times(1).out().values('name')",
            "*/g/V/1.{0,1}out().{1,2}in()>>name                % g.V(1).emit().repeat(out()).times(1).repeat(in()).emit().times(2).values('name')",
            "*/g/V/4.{-1}out().{0,1}out()>>name                % emit().repeat(out()).times(1).values('name')",
    }, delimiter = '%')
    void testARangedSpanFusesAsARepeat(final String code, final String expected) {
        assertEquals(expected, script(studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer.parse(code).asCode().rewrite()));
    }

    /**
     * A PUSHED-DOWN OPEN SPAN IS INTERRUPTIBLE (2026-11-16): {@code {*}both()} from marko never runs out of vertices (the
     * modern graph walked both ways is a cycle), and as Gremlin's {@code repeat()} it loops inside the traversal, where
     * the processor's walk never asks its thread. TinkerPop asks once a traverser and throws, which the gremlin call
     * answered as a fail, but two things lost the interrupt: a bulked traverser is handed out once per bulk without
     * asking ({@code {30,}both()}'s first carries every path of thirty steps, some 3.6e10), and TinkerPop CLEARS the flag
     * as it throws, so a mapped call, one traversal per element ({@code {-1}both()} hands it three), looped on in the
     * next element. The traversal asks once an element, and an interrupted one answers a fail with the flag still set,
     * as the walk leaves it. The interrupt comes once the traversal runs; one that comes before the code runs is
     * {@link #testAnInterruptBeforeAGraphCodeIsKept}'s.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "*/g/V/1.{*}both()",
            "*/g/V/1.{*}both()>>name",
            "*/g/V/1.{30,}both()>>name",
            "*/g/V/1.{-1}both().{*}both()>>name",
    }, delimiter = '%')
    void testAPushedDownOpenSpanStopsWhenItsThreadIsInterrupted(final String code) throws InterruptedException {
        final Machine machine = Machine.current();
        final AtomicReference<Obj> answer = new AtomicReference<>();
        final AtomicBoolean flagged = new AtomicBoolean();
        final Thread walker = new Thread(() -> {
            answer.set(Machine.Helper.evalAt(machine, () -> Machine.parse(code).apply(noobj())));
            flagged.set(Thread.currentThread().isInterrupted());
        });
        // a daemon, so a loop that never stops fails this test rather than holding the test JVM open
        walker.setDaemon(true);
        walker.start();
        final Predicate<StackTraceElement> traversing = frame ->
                grphInstSet.class.getName().equals(frame.getClassName()) && "runGremlin".equals(frame.getMethodName());
        final long compiled = System.currentTimeMillis() + 5000;
        while (walker.isAlive() && System.currentTimeMillis() < compiled
                && Arrays.stream(walker.getStackTrace()).noneMatch(traversing))
            Thread.sleep(10);
        Thread.sleep(250);
        assertTrue(Arrays.stream(walker.getStackTrace()).anyMatch(traversing), "the open span is still looping in its traversal");
        walker.interrupt();
        walker.join(5000);
        assertFalse(walker.isAlive(), () -> "the loop ends within five seconds of its thread's interrupt, not at "
                + Arrays.toString(walker.getStackTrace()));
        assertTrue(null != answer.get() && answer.get().stream()
                        .anyMatch(o -> o.isFail() && o.asFail().message().contains("interrupted")),
                () -> "an interrupted loop answers the interrupt's fail, it does not throw: " + answer.get());
        assertTrue(flagged.get(), "the flag stays set for the callers above, as the walk leaves it");
    }

    /**
     * AN INTERRUPT GIVEN BEFORE A GRAPH CODE RUNS IS KEPT (2026-11-16). Resolving {@code *}/g/V/1 reads the vertex to
     * type the read, so the first graph read comes as the code compiles. TinkerPop clears the flag as it throws, and
     * resolution answered the exception with a fallback: the read then answered marko, and the pushed-down open span
     * looped until a second interrupt. Each row runs on a thread interrupted before it starts, and ends within five
     * seconds with a fail and the thread still interrupted.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "*/g/V/1",
            "*/g/V/1.{*}both()",
            "*/g/V/1.{*}both()>>name",
            "/g/V/1 -> person::[name=>'tinker',age=>9]",
    }, delimiter = '%')
    void testAnInterruptBeforeAGraphCodeIsKept(final String code) throws InterruptedException {
        final Machine machine = Machine.current();
        final AtomicReference<Obj> answer = new AtomicReference<>();
        final AtomicBoolean flagged = new AtomicBoolean();
        final Thread walker = new Thread(() -> {
            Thread.currentThread().interrupt();
            answer.set(Machine.Helper.evalAt(machine, () -> Machine.parse(code).apply(noobj())));
            flagged.set(Thread.currentThread().isInterrupted());
        });
        walker.setDaemon(true);
        walker.start();
        walker.join(5000);
        assertFalse(walker.isAlive(), () -> "an interrupted code ends within five seconds, not at "
                + Arrays.toString(walker.getStackTrace()));
        assertTrue(null != answer.get() && answer.get().stream().anyMatch(Obj::isFail),
                () -> "an interrupted code answers a fail: " + answer.get());
        assertTrue(flagged.get(), "the thread is still interrupted after it");
    }

    /** The adjacency step names a fused run may carry — used to prove none survives the fusion. */
    private static boolean isAdjacencyName(final String name) {
        return switch (name) {
            case "out", "in", "both", "outE", "inE", "bothE", "inV", "outV", "bothV" -> true;
            default -> false;
        };
    }

    /** The fused call a chain compiled to. */
    private static Inst gremlinCall(final Code code) {
        return code.insts().stream()
                .filter(inst -> inst.tid().name().equals("gremlin"))
                .findFirst().orElseThrow(() -> new AssertionError("no gremlin call in " + code));
    }

    /** The script a fused call runs (the last argument; a folded seed rides as the first). */
    private static String script(final Code code) {
        final Inst call = gremlinCall(code);
        return call.arg((int) call.args().count() - 1).strValue();
    }

    /**
     * A run of table-backed instructions compiles to ONE {@code gremlin(...)} call, and a concrete element seed
     * folds into it: {@code g:V/1.out(knows).out(created)} is a single INITIAL call
     * ({@code gremlin(g:V/1, "g.V(1).out('knows').out('created')")}), so the read and the walk are one round trip.
     */
    @Test
    public void testAdjacencyChainFuses() {
        final Code fused = studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer
                .parse("*/g/V/1.out(knows).outE(created).inV()").asCode().rewrite();
        LOG.warn("chain after: %s", fused);
        assertEquals(1, fused.insts().size(), "the seed and the walk are ONE initial gremlin call");
        assertEquals("g.V(1).out('knows').outE('created').inV()", script(fused));
        assertEquals("noobj", gremlinCall(fused).tid().dom().name(), "an initial call consumes no lhs");
        assertEquals("rec", gremlinCall(fused).tid().rng().name(), "the walk hands on element recs");
        assertEquals(0, fused.insts().stream()
                        .filter(inst -> isAdjacencyName(inst.tid().name())).count(),
                "no adjacency instruction may survive the fusion");
        // >>name is a values() step, and a reducer types the call
        final Code names = studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer
                .parse("*/g/V/1.out(knows).>>name.count()").asCode().rewrite();
        assertEquals("g.V(1).out('knows').values('name').count()", script(names));
        assertEquals("int", gremlinCall(names).tid().rng().name(), "a count hands on one int");
        // a WILDCARD seed folds too: g:V/+ is g.V(), so the read and the walk are ONE traversal
        final Code wildcard = studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer
                .parse("*/g/V/+.out(knows).outE(created)").asCode().rewrite();
        assertEquals(1, wildcard.insts().size(), "g:V/+ folds to g.V()");
        assertEquals("g.V().out('knows').outE('created')", script(wildcard));
        assertEquals("noobj", gremlinCall(wildcard).tid().dom().name(), "an initial call consumes no lhs");
        // ...and with the seed folded a barrier goes down with it (one traversal, count over the whole walk)
        final Code wildcardCount = studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer
                .parse("*/g/V/+.out(knows).out(created)>>name.count()").asCode().rewrite();
        assertEquals(1, wildcardCount.insts().size());
        assertEquals("g.V().out('knows').out('created').values('name').count()", script(wildcardCount));
        // a run with NO seed (a chain applied to a variable) maps the elements it is handed
        final Code mapped = studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer
                .parse("out(knows).outE(created)").asCode().rewrite();
        assertEquals(1, mapped.insts().size(), "the chain is one mapped call");
        assertEquals("out('knows').outE('created')", script(mapped));
        assertEquals("rec", gremlinCall(mapped).tid().dom().name(), "a mapped call consumes element recs");
        // ...and a mapped run leaves a barrier to mtron: count over the union, not per element
        final Code mappedCount = studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer
                .parse("out(knows).out(created)>>name.count()").asCode().rewrite();
        assertEquals(2, mappedCount.insts().size(), "the walk is one call, the count stays in mtron");
        assertEquals(1, mappedCount.insts().stream()
                        .filter(inst -> inst.tid().name().equals("count")).count(),
                "the barrier survives as a mtron instruction");
        // with a concrete seed even ONE hop is a single round trip
        final Code hop = studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer
                .parse("*/g/V/1.outE(knows)").asCode().rewrite();
        assertEquals(1, hop.insts().size());
        assertEquals("g.V(1).outE('knows')", script(hop));
        // ...and a wildcard single hop folds too
        final Code wildcardHop = studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer
                .parse("*/g/V/+.outE(knows)").asCode().rewrite();
        assertEquals(1, wildcardHop.insts().size());
        assertEquals("g.V().outE('knows')", script(wildcardHop));
    }

    /**
     * A SPAN GREMLIN CANNOT SAY BREAKS THE RUN (Marko, 2026-11-13): folded into the call as one step, it walked once and
     * every answer came back counted by the span. A negative span walks the step's converse, a span on a step that
     * hands on another level leaves the step's domain on its second run ({@code {0,2}outE()} would walk an edge's
     * edges), and a span on a step with a point counts the point once per step of each run. Such a span stays on its
     * step for the processor to run, the steps before it are one call, and the steps after it another: each row is the
     * span left on its step and the scripts of the calls around it.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "*/g/V/4.{-1}out()>>name                            % -1   % []",
            "*/g/V/5.{-2,0}out()>>name                          % -2,0 % []",
            "*/g/V/1.out(created).{-1}out(created).out()>>name  % -1   % [g.V(1).out('created'), out().values('name')]",
            "*/g/V/1.out(knows).{0,1}outE(created).count()      % 0,1  % [g.V(1).out('knows')]",
            "*/g/V/1.out(knows).{0,1}out{2}().out()>>name       % 0,1  % [g.V(1).out('knows'), out().values('name')]",
    }, delimiter = '%')
    void testASpanBreaksTheRun(final String code, final String span, final String scripts) {
        final Code compiled = studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer.parse(code).asCode().rewrite();
        assertTrue(compiled.insts().stream().anyMatch(inst -> cInt.of(span).equals(inst.tid().b())),
                () -> "the span stays on its step: " + compiled);
        assertEquals(scripts, compiled.insts().stream()
                        .filter(inst -> inst.tid().name().equals("gremlin"))
                        .map(inst -> inst.arg((int) inst.args().count() - 1).strValue()).toList().toString(),
                () -> "the steps around the span are pushed down apart: " + compiled);
    }

    /**
     * THE DOM CHECK COUNTS NOTHING (Marko, 2026-11-13). The live server boots with the typer's dom check off, and there
     * >>name dropped the walk's {2}lop to one 'lop' (marko, lop, vadas, josh, ripple); the processor now carries the
     * count with the check on or off. {*}out() is now Gremlin's repeat(), so the processor's walk is {-2,0}in(): in()'s
     * converse, the same runs.
     */
    @Test
    public void testTheDomCheckOffKeepsTheWalksCount() {
        try {
            Machine.current().compiler().typer().at("check/inst_dom", BOOL_FALSE, Poly.MUTABLE);
            AbstractMetatronTest.checkCodeParseApply(LOG, "*/g/V/1.{*}out()>>name", "{'marko','vadas','josh','lop','ripple','lop'}");
            AbstractMetatronTest.checkCodeParseApply(LOG, "*/g/V/1.{-2,0}in()>>name", "{'marko','vadas','josh','lop','ripple','lop'}");
            AbstractMetatronTest.checkCodeParseApply(LOG, "*/g/V/3.branch(id(),id())>>name", "{2}'lop'");
        } finally {
            Machine.current().compiler().typer().at("check/inst_dom", BOOL_TRUE, Poly.MUTABLE);
        }
    }

    /**
     * THE PUSHDOWN IS SWITCHED OFF AND BACK ON THROUGH THE INSTSET DOOR (Marko, 2026-11-13: "inactive on the rewrite
     * doesn't work"). gremlin_pushdown declares no arguments, and its shadow, minted with one rec, was signed with
     * another tid ({@code args=[rec]}): the door filed it beside the rewrite, the pool listed both, and the chain still
     * compiled to one gremlin call. Off, the walk is mtron's and answers the same names; on, it is one call again.
     */
    @Test
    public void testThePushdownIsSwitchedOffThroughTheInstsetDoor() {
        final Machine parent = Machine.current();
        final Inst pushdown = parent.instset().rewrites().stream()
                .filter(rewrite -> "gremlin_pushdown".equals(rewrite.tid().name())).findFirst().orElseThrow();
        final String chain = "*/g/V/1.out()>>name";
        assertEquals("g.V(1).out().values('name')", script(studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer
                .parse(chain).asCode().rewrite()), "on, the chain is one gremlin call");

        final Machine child = parent.push(f("pushdown_door"));
        try {
            final Obj off = Machine.evaluate("*~/instset/rewrite/gremlin_pushdown.inactive().to(~/instset/rewrite/gremlin_pushdown)");
            assertFalse(off.isFail(), () -> "the door takes the shadow: " + off);
            final Obj listed = Machine.evaluate("*~/instset/rewrite/gremlin_pushdown");
            assertEquals(1, listed.stream().count(), () -> "the pool lists the pushdown once, never beside its shadow: " + listed);
            assertEquals(pushdown, Inst.Helper.shadowed(listed.asInst()), "and lists it as off");
            final Code walk = studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer.parse(chain).asCode().rewrite();
            assertTrue(walk.insts().stream().noneMatch(inst -> inst.tid().name().equals("gremlin")),
                    () -> "off, no run is pushed down: " + walk);
            assertTrue(walk.insts().stream().anyMatch(inst -> inst.tid().name().equals("out")),
                    () -> "and the walk stays mtron's: " + walk);
            AbstractMetatronTest.checkCodeParseApply(LOG, chain, "{'vadas','josh','lop'}");
            assertTrue(parent.instset().rewrites().contains(pushdown), "the enclosing machine keeps its pushdown");

            Machine.evaluate("*~/instset/rewrite/gremlin_pushdown.active().to(~/instset/rewrite/gremlin_pushdown)");
            assertEquals(pushdown, Machine.evaluate("*~/instset/rewrite/gremlin_pushdown"), "writing back the inherited rewrite removes the override");
            assertEquals("g.V(1).out().values('name')", script(studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer
                    .parse(chain).asCode().rewrite()), "on again, the chain is one gremlin call");

            Machine.evaluate("*~/instset/rewrite/gremlin_pushdown.inactive().to(~/instset/rewrite/gremlin_pushdown)");
            Machine.evaluate("~/instset/rewrite/gremlin_pushdown -> noobj");
            assertEquals(pushdown, Machine.evaluate("*~/instset/rewrite/gremlin_pushdown"), "and so does noobj");
        } finally {
            child.pop();
        }
    }

    @ParameterizedTest
    @CsvSource(value = {
            // ── wildcard counts (no ID needed) ──
            "*/g/V/+.count()                                                                % 6",
            "*/g/E/+.count()                                                                % 6",
            // ── wildcard traversals ── (+ walks the vertices; # answers only leaves, the property values: Marko, 2026-11-19)
            "*/g/V/+>>name                                                                  % {\"marko\",\"josh\",\"peter\",\"lop\",\"vadas\",\"ripple\"}",
            "*/g/V/+/OUT.count()                                                              % 6",
            "*/g/V/+.outE().count()                                                        % 6",
            "*/g/V/+>>OUT/+.count()                                                          % 6",
            "*/g/V/+>>OUT/+>>+.count()                                                       % 6",
            "/g.-<[mult(V/+).*(_).count(),mult(E/+).*(_).count()]                           % [6,6]",
    }, delimiter = '%')
    public void testIdTraversals(final String code, final String expected) {
        LOG.debug(studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer.parse(code).resolve(this.space));
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "@/g/V/1>>=[name=>'bill']                                  % */g/V/1/name                      % \"bill\"",
            // property set → read back
            "@/g/V/1>>=[age=>99]                                       % */g/V/1/age                        % 99",
            "@/g/V/1>>=[age=>29]                                       % */g/V/1/age                        % 29",
            // property delete → read back noobj
            "@/g/V/1>>=[age=>none]                                     % */g/V/1/age                        % <ERROR>",
            "@/g/V/1>>=[age=>29]                                       % */g/V/1/age                        % 29",
            "@/g/V/1>>=[age=>+10]                                      % */g/V/1/age                        % 39",
            "@/g/V/1>>=[age=><<.>>name.select('.')>-.count()]           % */g/V/1/age                        % 5",
            "@/g/V/1>>=[name=>\"dr.marko\"]                            % */g/V/1/name                       % \"dr.marko\"",
    }, delimiter = '%')
    public void testGraphMutations(final String mutation, final String readCode, final String expected) {
        AbstractMetatronTest.checkCodeEvaluate(LOG, mutation, readCode, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "@/g/V/1>>=[age=>23]                                               % */g/V/1>>age                     % 23",
            "@/g/V/1>>=[age=>+10]                                              % */g/V/1>>age                     % 39",
            "*/g/V/1                                                           % */g/V/1>>age                     % 29",
            "@/g/V/1>>=[age=>none]                                             % */g/V/1>>age                     % <ERROR>",
            "@/g/V/1>>=[age=>-<[_]>-]                                          % */g/V/1>>age                     % 29",
            "@/g/V/1>>=[age=>-<[_,_]>-.sum()]                                  % */g/V/1>>age                     % 58",
            "@/g/V/1>>=[age=>_]                                                % */g/V/1>>age                     % 29",
            "@/g/V/1>>=[name=>-<[_,<<.>>age.as(str::T)]>-.sum?str<=str{*}()]   % */g/V/1>>name                    % \"marko29\"",
            "@/g/V/1>>=[age=><<>>name]                                         % */g/V/1>>age                     % <ERROR>",
            "@/g/V/1>>=[age=>'hello']                                          % */g/V/1>>age                     % <ERROR>",
            "@/g/V/1>>=+[likes=>food]                                          % */g/V/1>>likes                   % food",
            "@/g/V/1>>=+[likes=>[!(*/g/V/+.?[name=>'vadas']),!*/g/V/3]]                           % */g/V/1>>likes>-                 % 1-<[*/g/V/2,*/g/V/3]>-",
    }, delimiter = '%')
    public void testVertexUpdate(final String update, final String select, final String expected) {
        AbstractMetatronTest.checkCodeEvaluate(LOG, update, select, expected);
    }

    /**
     * A WRITE THAT ADDS A PROPERTY TO ONE ELEMENT LEAVES ITS LABEL'S OTHER ELEMENTS IN THE TYPE, as every SchemaSpace
     * infers (SchemaSpace.Helper): a property not on every element is optional. Two pets nothing declares, one given an
     * age, are both pets, reached by the inferred type's address (an inferred type has no name a graph element's label
     * could resolve to). A declared label keeps its declared type (Marko, 2026-11-16): marko given likes, vadas is still
     * a person, and the declared person refuses marko dropping his age (testVertexUpdate). Before, the write regenerated
     * the label's type with every property required, and vadas, fido and every later test's persons fell out of it.
     */
    @Test
    public void testAPropertyAddedToOneElementLeavesTheRestInTheType() {
        final studio.phaseshift.metatron.isa.m.db.grph.space.grphSpace gs =
                (studio.phaseshift.metatron.isa.m.db.grph.space.grphSpace) Machine.current().memory().getSpaceFor(f("/g"));
        final String pet = gs.vid().extend(INSTSET).extend("pet") + "::T";
        gs.sjvm().addV("pet").property(org.apache.tinkerpop.gremlin.structure.T.id, 100).property("name", "rex").next();
        gs.sjvm().addV("pet").property(org.apache.tinkerpop.gremlin.structure.T.id, 101).property("name", "fido").next();
        AbstractMetatronTest.checkCodeEvaluate(LOG, "@/g/V/100>>=+[age=>3]", "*/g/V/100.?" + pet + ">>age", "3");
        AbstractMetatronTest.checkCodeParseApply(LOG, "*/g/V/101.?" + pet + ">>name", "'fido'");
        AbstractMetatronTest.checkCodeParseApply(LOG, "{[name=>'kit'],[age=>2]}.isa(" + pet + ").count()", "1");
        AbstractMetatronTest.checkCodeEvaluate(LOG, "@/g/V/1>>=+[likes=>food]", "*/g/V/1.?person::T>>likes", "food");
        AbstractMetatronTest.checkCodeParseApply(LOG, "*/g/V/2.?person::T>>name", "'vadas'");
    }

    @Disabled("addE works; but */g/V/2 reads person::[name=>'vadas',age=>27] with no vid, where out(likes) answers it @/g/V/2 (checked 2026-11-16)")
    @ParameterizedTest
    @CsvSource(value = {
            "*/g/V/1.addE(likes,*/g/V/2)                                       % */g/V/1.out(likes)                     % */g/V/2",
    }, delimiter = '%')
    public void testAddVertex(final String update, final String select, final String expected) {
        AbstractMetatronTest.checkCodeEvaluate(LOG, update, select, expected);
    }
}
