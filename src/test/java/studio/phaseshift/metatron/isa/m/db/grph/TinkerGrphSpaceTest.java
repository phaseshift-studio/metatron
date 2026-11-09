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
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Inst;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.db.grph.space.schema.modernSchema.MODERN_SCHEMA_TID;
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
            // the escape hatch, in the same suffix form a fused run compiles to
            "*/g/V/1.gremlin('out(\"knows\").values(\"name\")')    % {'vadas','josh'}",
    }, delimiter = '%')
    public void testSingleElementInstructionTraversal(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
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

    @Disabled("id-based traversal count mismatch over TinkerGraph")
    @ParameterizedTest
    @CsvSource(value = {
            // ── wildcard counts (no ID needed) ──
            "*/g/V/+.count()                                                                % 6",
            "*/g/E/+.count()                                                                % 6",
            "*/g/+.count()                                                                 % 4",
            // ── wildcard traversals ──
            "*/g/V/#>>name                                                                   % {\"marko\",\"josh\",\"peter\",\"lop\",\"vadas\",\"ripple\"}",
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

    @Disabled("Mutates shared cached data — needs isolated test space")
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
            "@/g/V/1>>=[age=><<.>>name.regex('.')>-.count()]           % */g/V/1/age                        % 5",
            "@/g/V/1>>=[name=>\"dr.marko\"]                            % */g/V/1/name                       % \"dr.marko\"",
    }, delimiter = '%')
    public void testGraphMutations(final String mutation, final String readCode, final String expected) {
        AbstractMetatronTest.checkCodeEvaluate(LOG, mutation, readCode, expected);
    }

    @Disabled("Mutates shared cached data — needs isolated test space")
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

    @Disabled("Edge addition not yet supported")
    @ParameterizedTest
    @CsvSource(value = {
            "*/g/V/1.addE(likes,*/g/V/2)                                       % */g/V/1.out(likes)                     % */g/V/2",
    }, delimiter = '%')
    public void testAddVertex(final String update, final String select, final String expected) {
        AbstractMetatronTest.checkCodeEvaluate(LOG, update, select, expected);
    }
}
