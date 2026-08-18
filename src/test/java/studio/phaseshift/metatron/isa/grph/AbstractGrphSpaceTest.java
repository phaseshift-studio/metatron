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

package studio.phaseshift.metatron.isa.grph;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import studio.phaseshift.metatron.AbstractDataPathSpaceTest;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.TestData;
import studio.phaseshift.metatron.algebra.rewrite.CommonRewritesTestContract;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.IncrQTest;
import studio.phaseshift.metatron.isa.AbstractSpaceTest;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.grph.space.GraphLoader;
import studio.phaseshift.metatron.isa.grph.space.grphSpace;
import studio.phaseshift.metatron.isa.grph.space.schema.modernSchema;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.Tuple;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.grph.grphInstSet.GRPH_ISA_TID;
import static studio.phaseshift.metatron.isa.grph.space.GraphLoader.MODERN;
import static studio.phaseshift.metatron.isa.grph.space.schema.modernSchema.MODERN_SCHEMA_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * Abstract base test suite for {@link grphSpace} with graph-backend-agnostic tests.
 * <p>
 * Subclasses provide backend-specific configuration via
 * {@link #perTestConfigRec} and {@link #rewriteTestConfigRec},
 * set in their {@code @BeforeAll} methods.
 * <p>
 * The "modern" TinkerPop dataset (6 vertices, 6 edges) is seeded
 * into each per-test space via {@link #seedModernGraph(grphSpace)}.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public abstract class AbstractGrphSpaceTest extends AbstractDataPathSpaceTest implements CommonRewritesTestContract {

    // ========================================================================
    //  Backend configuration — set by subclass @BeforeAll
    // ========================================================================

    /**
     * Configuration record for per-test spaces (pattern {@code /g/#}).
     * Must include {@code PATTERN}, {@code ROUTE}.  For remote backends
     * (JanusGraph), also include {@code HOST} and {@code CONFIG}.
     */
    protected static Rec perTestConfigRec;

    /**
     * Configuration record for the rewrite test space (pattern {@code /grt/#}).
     * Same structure as {@link #perTestConfigRec}.
     */
    protected static Rec rewriteTestConfigRec;

    // ========================================================================
    //  Common rewrite test infrastructure
    // ========================================================================

    protected static final fURI REWRITE_TEST_SPACE_URI = f("/grt");
    protected static final fURI REWRITE_TEST_SPACE_VID = f("/sys/space/grph/rewrite_test");

    /**
     * Create the rewrite test space from {@link #rewriteTestConfigRec}.
     * Called by subclass {@code @BeforeAll} after the backend is ready.
     */
    protected static void createRewriteTestSpace() {
        final grphSpace space = grphSpace.of(rewriteTestConfigRec, REWRITE_TEST_SPACE_VID);
        // Seed the 10 vertices CommonRewritesTestContract expects
        // (id/value 1-10, name item1-item10, active alternating true/false).
        for (int i = 1; i <= 10; i++) {
            space.sjvm().addV("item")
                    .property("id", i)
                    .property("value", i)
                    .property("name", "item" + i)
                    .property("active", (i % 2 == 1)).next();
        }
    }

    @AfterAll
    public static void cleanupRewriteTestSpace() {
        Router.global().removeSpace(REWRITE_TEST_SPACE_URI);
    }

    // ========================================================================
    //  CommonRewritesTestContract — shared rewrite URIs
    // ========================================================================

    @Override
    public fURI getTestDataUriPrefix() {
        return f("/grt/V");
    }

    @Override
    public String getNativeInstructionPrefix() {
        return "gremlin_";
    }

    @Override
    public fURI getRewriteInstUri() {
        return f("/m/grph");
    }

    // ========================================================================
    //  AbstractDataPathSpaceTest — flat namespace wiring
    // ========================================================================

    @Override
    protected fURI deducedBaseUri() {
        return f("/g/kv_scratch");
    }

    @Override
    protected boolean supportsLosslessFlatMigration() {
        // A graph vertex is a labeled Rec — a bare mono/list flat entry can't be
        // promoted to it (unlike a schemaless document).  Migration of monos is
        // therefore not lossless; a grphSpace-specific migration test lands in
        // Phase 3 alongside edge writes.
        return false;
    }

    @Override
    protected void dropDeducedCollection(final String collectionName) {
        final Space space = Router.global().getSpaceFor(f("/g"));
        if (space instanceof grphSpace gs)
            gs.sjvm().V().hasLabel(collectionName).drop().iterate();
    }

    @Override
    protected fURI testUri(final String suffix) {
        return f("/g/kv_store/test/" + suffix);
    }

    // ========================================================================
    //  Lifecycle
    // ========================================================================

    @BeforeAll
    public static void importISAs() {
        InstSet.importInstSet(GRPH_ISA_TID);
        InstSet.importInstSet(MODERN_SCHEMA_TID);
    }

    @AfterAll
    public static void cleanupISAs() {
        Router.global().removeSpace(GRPH_ISA_TID);
    }

    /**
     * Constructor.
     * <p>
     * Each test invocation creates a fresh space (via {@code @BeforeEach}
     * in {@link AbstractSpaceTest}), seeds the modern dataset, and closes
     * the space after the test.
     *
     * @param baseURI the base URI for test expressions (e.g., {@code /g})
     */
    protected AbstractGrphSpaceTest(final fURI baseURI) {
        super(baseURI, () -> {
            if (perTestConfigRec == null) {
                throw new IllegalStateException(
                        "perTestConfigRec not initialized. @BeforeAll method must run first.");
            }
            final fURI vid = f("/sys/space/test_" + System.nanoTime());
            final grphSpace graph = grphSpace.of(perTestConfigRec, vid);
            GraphLoader.get(MODERN).accept(graph);
            return graph;
        });
    }

    @Override
    public void testUpdateWrite(final UpdateTestCase test) {
        // do nothing
    }

    /*@Override
    public fURI incrQBaseURI() {
        return f("/g/V");
    }*/

    // ========================================================================
    //  Shared tests — property-based traversals (work on both backends)
    // ========================================================================

    /**
     * Instruction-call traversals (outE, out, inV, outV, inE, in, etc.).
     * Delegates to the route-based directReader via Router.readFromSpace using the vertex/edge VID.
     */
    @ParameterizedTest
    @CsvSource(value = {
            // ── wildcard vertex set ──
            "*/g/V/+.count()                                                       % 6",
            "*/g/V/+.>>name                                                        % {'marko','lop','vadas','josh','peter','ripple'}",
            "*/g/V/+.has(lang)>>lang                                               % {2}'java'",
            "*/g/V/+=?=(>>name.?='marko')>>age                                     % 29",
            "*/g/V/+=?=(>>name.?='marko')>>{age,name}                              % {'marko',29}",
            "*/g/V/+.?person::[name=>'marko',age=>_].count()                       % 1",
            "*/g/V/+.where([name=>'marko'])>>name                                        % \"marko\"",
            "*/g/V/+.where([name=>'marko'])>>age                                         % 29",
            "*/g/V/+.where([age=>?<30]).count()                                    % 2",
            // ── outE (vertex → edges) via property lookup ──
            "*/g/V/+.where([name=>'marko']).outE().count()                           % 3",
            "*/g/V/+.where([name=>'marko']).outE(knows).count()                       % 2",
            "*/g/V/+.where([name=>'marko']).outE(created).count()                     % 1",
            "*/g/V/+.where([name=>'marko']).outE(nonexistent).count()                 % 0",
            // ── inE (vertex → incoming edges) ──
            "*/g/V/+.where([name=>'vadas']).inE().count()                             % 1",
            "*/g/V/+.where([name=>'vadas']).inE(knows).count()                         % 1",
            // ── bothE ──
            "*/g/V/+.where([name=>'marko']).bothE(+).count()                           % 3",
            "*/g/V/+.where([name=>'marko']).bothE().count?int<=#{*}()                     % 3",
            "*/g/V/+.where([name=>'marko']).bothE(knows).count?int<=#{*}()                % 2",
            // ── out (vertex → adjacent vertices) ──
            "*/g/V/+.where([name=>'marko']).out().count?int<=#{*}()                      % 3",
            "*/g/V/+.where([name=>'marko']).out(knows).count?int<=#{*}()                 % 2",
            "*/g/V/+.where([name=>'marko']).out(knows)>>name                              % {\"vadas\",\"josh\"}",
            "*/g/V/+.where([name=>'marko']).out(created)>>name                            % \"lop\"",
            // ── in (vertex → incoming adjacent vertices) ──
            "*/g/V/+.where([name=>'vadas']).in().count()                                  % 1",
            "*/g/V/+.where([name=>'vadas']).in(knows).count()                              % 1",
            // ── both ──
            "*/g/V/+.where([name=>'marko']).both().count?int<=#{*}()                       % 3",
            "*/g/V/+.where([name=>'marko']).both(knows).count?int<=#{*}()                  % 2",
            // ── inV / outV (vertex → edge → endpoint vertex) ──
            "*/g/V/+.where([name=>'marko']).outE(knows).where([weight=>0.5]).inV()>>name   % \"vadas\"",
            "*/g/V/+.where([name=>'marko']).outE(knows).where([weight=>0.5]).outV()>>name  % \"marko\"",
            "*/g/V/+.where([name=>'marko']).outE(knows).?[weight=>0.5].inV()>>age    % 27",
            // ── edge property access via instruction chain ──
            "*/g/V/+.where([name=>'marko']).outE(created)>>weight                         % 0.4000",
            "*/g/V/+.where([name=>'marko']).outE(knows)>>weight                           % {0.5000,1.0000}",
            // ── instruction-call chain (outE → inV) ──
            "*/g/V/+.where([name=>'marko']).outE(knows).inV()>>name                           % {\"vadas\",\"josh\"}",
            // ── wildcard vertex set with outE ──
            "*/g/V/+.outE(+).count()                                                    % 6",
            "*/g/V/+.outE(knows).count()                                                % 2",
            "*/g/V/+.outE(created).count()                                              % 4",
            // ── wildcard vertex set with out ──
            "*/g/V/+.out().count?int<=#{*}()                                                      % 6",
            "*/g/V/+.out(knows)>>name                                                             % {\"vadas\",\"josh\"}",
            // ── double walk (vertex → vertices → vertices) ──
            "*/g/V/+.where([name=>'marko']).out(knows).out(created)>>name                                                 % {\"ripple\",\"lop\"}",
            "*/g/V/+.where([name=>'marko']).outE(knows).inV().outE(created).inV()>>name                                   % {\"ripple\",\"lop\"}",
            "*/g/V/+.where([name=>'marko']).outE(knows).has(weight).inV().outE(created).has(weight).inV()>>name           % {\"ripple\",\"lop\"}",
            // ── from testBasicTraversals (instruction-call equivalents) ──
            "*/g/V/+.where([name=>'josh']).inE().count()                                                % 1",
            "*/g/V/+.where([name=>'marko']).out(created)>>lang                                           % \"java\"",
            "*/g/V/+.where([name=>'marko']).outE(created)>>weight.sum?real<=real{*}()                    % 0.4000",
            "*/g/V/+.where([name=>'marko']).out()>>name                                                  % {\"lop\",\"vadas\",\"josh\"}",
    }, delimiter = '%')
    public void testInstructionCallTraversals(final String code, final String expected) {
        LOG.warn(ObjmtronSerializer.parse(code).resolve(space));
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    /**
     * Isolated edge-property where() test.  Creates a single edge with
     * int, real, string, and bool properties and verifies that {@code where()}
     * predicates filter correctly on each type.
     */
    @Test
    public void testEdgePropertyWhere() {
        final grphSpace gs = (grphSpace) Router.global().getSpaceFor(f("/g"));
        // Create alice and bob vertices (match constructor seeding pattern)
        final Vertex alice = gs.sjvm().addV("testVertex").property("name", "alice").next();
        final Vertex bob = gs.sjvm().addV("testVertex").property("name", "bob").next();
        // Find vertices by property to get live traversal refs, then add edge
        final Vertex outV = gs.sjvm().V().has("name", "alice").next();
        final Vertex inV = gs.sjvm().V().has("name", "bob").next();
        final Edge edge = gs.sjvm().V(outV).addE("testEdge").to(__.V(inV.id()))
                .property("rank", 42)
                .property("score", 0.75)
                .property("tag", "trusted")
                .property("active", true)
                .next();
        final String aliceId = alice.id().toString();
        try {
            // ── int property ──
            AbstractMetatronTest.checkCodeParseApply(LOG,
                    "*/g/V/" + aliceId + ".outE(testEdge).where([rank=>42]).count()", "1");
            AbstractMetatronTest.checkCodeParseApply(LOG,
                    "*/g/V/" + aliceId + ".outE(testEdge).where([rank=>99]).count()", "0");
            // ── real property ──
            AbstractMetatronTest.checkCodeParseApply(LOG,
                    "*/g/V/" + aliceId + ".outE(testEdge).where([score=>0.75]).count()", "1");
            AbstractMetatronTest.checkCodeParseApply(LOG,
                    "*/g/V/" + aliceId + ".outE(testEdge).where([score=>?>0.5]).count()", "1");
            AbstractMetatronTest.checkCodeParseApply(LOG,
                    "*/g/V/" + aliceId + ".outE(testEdge).where([score=>?>0.9]).count()", "0");
            // ── string property ──
            AbstractMetatronTest.checkCodeParseApply(LOG,
                    "*/g/V/" + aliceId + ".outE(testEdge).where([tag=>'trusted']).count()", "1");
            AbstractMetatronTest.checkCodeParseApply(LOG,
                    "*/g/V/" + aliceId + ".outE(testEdge).where([tag=>'wrong']).count()", "0");
            // ── bool property ──
            AbstractMetatronTest.checkCodeParseApply(LOG,
                    "*/g/V/" + aliceId + ".outE(testEdge).where([active=>true]).count()", "1");
            AbstractMetatronTest.checkCodeParseApply(LOG,
                    "*/g/V/" + aliceId + ".outE(testEdge).where([active=>false]).count()", "0");
        } finally {
            assertFalse(gs.sjvm().E(edge.id()).drop().hasNext());
            assertFalse(gs.sjvm().V(alice.id()).drop().hasNext());
            assertFalse(gs.sjvm().V(bob.id()).drop().hasNext());
        }
    }

    /**
     * Verify addE() creates edges via the GraphTraversalSource API.
     * Uses {@code @CsvSource} for individual test-cases, and the
     * for-loop + String[] technique for multi-step stateful sequences
     * that build on previous rows.
     */
    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource(value = {
            // ── basic edge creation + verification ──
            "basic addE then outE count                                                         % " +
                    "@$$/V/+.?[name=>'marko'].addE(likes,*$$/V/+.?[name=>'lop'])                % " +
                    "*$$/V/+.?[name=>'marko'].outE(likes).count()                               % 1",

            "addE returns edge with correct label                                              % " +
                    "@$$/V/+.?[name=>'marko'].addE(rates,*$$/V/+.?[name=>'lop']).tid()         % " +
                    "*$$/V/+.?[name=>'marko'].outE(rates).tid()                                % rates",

            // ── edge with property ──
            "addE with property then read property                                               % " +
                    "@$$/V/+.?[name=>'marko'].addE(rated,*$$/V/+.?[name=>'lop'],[score=>5])      % " +
                    "*$$/V/+.?[name=>'marko'].outE(rated)>>score.count?int<=int{*}()             % 1",

            "addE with property then count edge                                                  % " +
                    "@$$/V/+.?[name=>'marko'].addE(rated,*$$/V/+.?[name=>'lop'],[score=>5])      % " +
                    "*$$/V/+.?[name=>'marko'].outE(rated).count?int<=rec{*}()                    % 1",

            // ── edge between same vertex (self-loop) ──
            "addE self-loop                                                                     % " +
                    "@$$/V/+.?[name=>'marko'].addE(self,*$$/V/+.?[name=>'marko'])               % " +
                    "*$$/V/+.?[name=>'marko'].outE(self).tid()                                  % self",

            // ── two edges between same pair (multi-edge) ──
            "multi-edge between same vertices                                                   % " +
                    "@$$/V/+.?[name=>'josh'].addE(collab,*$$/V/+.?[name=>'peter'])              % " +
                    "*$$/V/+.?[name=>'josh'].outE(collab).count()                               % 1",
    }, delimiter = '%')
    public void testAddEdge(final String description, final String updateExpression, final String readExpression, final String expected) {
        // Execute the addE
        final Obj updateResult = ObjmtronSerializer.parse(make(updateExpression)).apply();
        assertFalse(updateResult.isNoObj(), () -> description + ": addE should not return noobj");

        // Verify the expected result
        AbstractMetatronTest.checkCodeParseApply(LOG, make(readExpression), expected);
    }

    /**
     * Multi-step edge sequence: add multiple edges to the same vertex
     * and verify cumulative counts.  Uses the for-loop pattern because
     * each step depends on state from the previous step.
     */
    @Test
    public void testAddEdgeChain() {
        final String[] steps = {
                // add a likes edge + verify outE count
                "@$$/V/+.?[name=>'marko'].addE(likes,*$$/V/+.?[name=>'lop'])  % *$$/V/+.?[name=>'marko'].outE(likes).count()  % 1",
                // add a second likes edge to a different vertex
                "@$$/V/+.?[name=>'marko'].addE(likes,*$$/V/+.?[name=>'josh']) % *$$/V/+.?[name=>'marko'].outE(likes).count()  % 2",
                // add edge with property
                "@$$/V/+.?[name=>'marko'].addE(trusts,*$$/V/+.?[name=>'vadas'], [weight=>10]) % *$$/V/+.?[name=>'marko'].outE(trusts).where([weight=>10]).count()      % 1",
                // total outE for marko: 2 (knows) + 1 (created) + 2 (likes) + 1 (trusts) = 6
                ".  %  *$$/V/+.?[name=>'marko'].outE(+).count()                               % 6",
        };
        for (int i = 0; i < steps.length; i++) {
            LOG.warn("TEST[%d]", i);
            final String[] parts = steps[i].split("%");
            final String expression = make(parts[0].trim());
            final String read = make(parts[1].trim());
            final String expected = make(parts[2].trim());
            final int stepNum = i + 1;

            if (!expression.equals("."))
                assertFalse(ObjmtronSerializer.parse(expression).apply().isNoObj(),
                        () -> "step " + stepNum + ": addE should succeed");
            AbstractMetatronTest.checkCodeParseApply(LOG, read, expected);
        }
    }

    /**
     * Distributed edges: vertex lives in grphSpace, edges live in separate memSpaces.
     * Uses the for-loop + String[] pattern to avoid CSV quoting and lifecycle issues.
     */
    @Test
    public void testDistributedEdges() {
        final memSpace hot = memSpace.of(f("/te/hot/#"), f("/sys/space/dist/hot_" + System.nanoTime()));
        final memSpace cold = memSpace.of(f("/te/cold/#"), f("/sys/space/dist/cold_" + System.nanoTime()));
        try {
            // seed: hot edges for vertex 1, cold edges for vertex 1
            // external edges with auto_from pointers back to grphSpace
            hot.write(f("/te/hot/1/a"), rec(uri("item"), str("laptop"), uri("total"), real(1200.0),
                    uri("owner"), ObjmtronSerializer.parse("!(*/g/V/+.?[name=>'vadas'])").apply(noobj())));
            hot.write(f("/te/hot/1/b"), rec(uri("item"), str("mouse"), uri("total"), real(25.0)));
            // also seed hot edges for vertex 4 to test multi-vertex external reads
            hot.write(f("/te/hot/4/c"), rec(uri("item"), str("monitor"), uri("total"), real(300.0)));
            cold.write(f("/te/cold/1/x"), rec(uri("item"), str("book"), uri("total"), real(15.0),
                    uri("seller"), ObjmtronSerializer.parse("!(*/g/V/+.?[name=>'josh'])").apply(noobj())));

            // verify memSpace data is accessible directly
            assertTrue(hot.readStream(f("/te/hot/1/+")).toList().size() == 2,
                    "memSpace should have 2 hot edges for vertex 1");
            assertTrue(hot.readStream(f("/te/hot/4/+")).toList().size() == 1,
                    "memSpace should have 1 hot edge for vertex 4");
            final String[] tests = {
                    // ── hot edges: grphSpace vertex 1 → OUT via scheme label te:hot ──
                    "*/g/V/+.?[name=>'marko'].outE(te:hot).count()                                               % 2",
                    "*/g/V/+.?[name=>'marko'].outE(te:hot/a)>>item                                                    % \"laptop\"",
                    "*/g/V/+.?[name=>'marko'].outE(te:hot/a)>>total                                                   % 1200.0",
                    "*/g/V/+.?[name=>'marko'].outE(te:hot/b)>>item                                                    % \"mouse\"",
                    "*/g/V/+.?[name=>'marko'].outE(te:hot/b)>>total                                                   % 25.0",
                    // ── cold edges ──
                    "*/g/V/+.?[name=>'marko'].outE(te:cold).count()                                               % 1",
                    "*/g/V/+.?[name=>'marko'].outE(te:cold/x)>>item                                                    % \"book\"",
                    "*/g/V/+.?[name=>'marko'].outE(te:cold/x)>>total                                                   % 15.0",
                    // ── passthrough: grphSpace → external → back to grphSpace ──
                    "*/g/V/+.?[name=>'marko'].outE(te:hot/a)>>owner>>name                                             % \"vadas\"",
                    "*/g/V/+.?[name=>'marko'].outE(te:cold/x)>>seller>>name                                            % \"josh\"",
                    // ── local edges still coexist ──
                    "*/g/V/+.?[name=>'marko'].outE(knows).count()                                                   % 2",
                    "*/g/V/+.?[name=>'marko'].out(created)>>name                                                  % \"lop\"",
                    // ── multi-vertex: vertex 4 has both local and external hot edges ──
                    "*/g/V/+.?[name=>'josh'].outE(te:hot).count()                                                % 1",
                    "*/g/V/+.?[name=>'josh'].outE(te:hot/c)>>item                                                     % \"monitor\"",
                    "*/g/V/+.?[name=>'josh'].outE(te:hot/c)>>total                                                    % 300.0",
                    // ── instruction-call syntax equivalents ──
                    "*/g/V/+.?[name=>'marko'].outE(te:hot).count()                                                   % 2",
                    "*/g/V/+.?[name=>'marko'].outE(te:hot/a)>>item                                                    % \"laptop\"",
                    "*/g/V/+.?[name=>'marko'].outE(te:hot/a)>>total                                                   % 1200.0",
                    "*/g/V/+.?[name=>'marko'].outE(te:hot/a)>>owner>>name                                            % \"vadas\"",
                    "*/g/V/+.?[name=>'marko'].outE(te:cold/x)>>total                                                   % 15.0",
                    "*/g/V/+.?[name=>'marko'].outE(knows).count()                                                    % 2",
            };
            for (final String expression : tests) {
                final String[] parts = expression.split("%");
                AbstractMetatronTest.checkCodeParseApply(LOG, parts[0].trim(), parts[1].trim());
            }
        } finally {
            Router.global().removeSpace(f(hot.vid().toString()));
            Router.global().removeSpace(f(cold.vid().toString()));
        }
    }

    @ParameterizedTest
    @CsvSource(value = {
          //  "*/g/+                                                   % true",
            "*/g/+.count()                                                                   % 2",
            "*/g/+                                                                           % {edge::T@E,vrtx::T@V}",
            "*/g/S                                                                           % modern::T",
            //"*/g/S.vid().*(_)                                                             % 4",
            //"*/g/S>>pattern.*_.count()                                                       % 5",
           // "*/g/S>>pattern.*(_).vid()                                                        % {/m/grph/schema/modern/person,/m/grph/schema/modern/software,/m/grph/schema/modern/created,/m/grph/schema/modern/knows,/m/grph/schema/modern}",
    }, delimiter = '%')
    public void testSchemaTraversal(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "*/g/V/+.where([name=>'marko'])                                                % person::T    % true",
            "*/g/V/+=?=[name=>'marko']                                                % rec::T       % true",
            "*/g/V/+=?=[name=>'marko']                                                % rec::T       % true",
            "*/g/V/+=?=[name=>'vadas']                                                % person::T    % true",
            "*/g/V/+=?=[name=>'vadas']                                                % software::T  % false",
            "*/g/V/+=?=[name=>'lop']                                                  % software::T  % true",
            "*/g/V/+=?=[name=>'lop']                                                  % created::T   % false",
            "*/g/V/+=?=[name=>'marko']                                                % created::T   % false",
            "*/g/V/+                                                                  % #{+}::T   % true",
            "*/g/V/+=?=[name=>'marko'].id{2}()                                        % int{2}::T  % false",
            "*/g/V/+=?=[name=>'marko'].-<[_,_]>-                                      % person{2}::T  % true",
            "*/g/V/+=?=[name=>'marko'].-<[_,_]>-                                      % vrtx{2}::T  % true",
            "*/g/V/+=?=[name=>'marko'].-<[_,_]>-                                      % rec{2}::T  % true",
            "*/g/V/+=?=[name=>'marko'].-<[_,_]>-                                      % #{2}::T  % true",
            "*/g/V/+=?=[name=>'marko'].-<[_,_]>-                                      % str{2}::T  % false",
            "*/g/V/+=?=[name=>'marko'].id{2}()                                        % elmt{2}::T  % true",
            "*/g/V/+=?=[name=>'marko'].id{2}()                                        % person{2}::T % true",
            "*/g/V/+=?=[name=>'marko']                                                % rec{2}::T   % false",
            "*/g/V/+=?=[name=>'marko'].-<[_,_]>-                                      % rec{3}::T   % false",
    }, delimiter = '%')
    public void testTypeInheritance(final String lhs, final String type, final boolean matches) {
        new grphInstSet().setup();
        new modernSchema().setup();
        final Obj obj = ObjmtronSerializer.parse(lhs).apply(jnt(1));
        StringBuilder sb = new StringBuilder("lhs type: ").append(obj);
        Type current = obj.type();
        while (!current.isRootType()) {
            sb.append("=>").append(current.vid());
            current = current.parentType();
        }
        LOG.warn(sb.toString());
        AbstractSpaceTest.checkMatchesByID(LOG, lhs, type, matches);
    }

    @Test
    @Disabled
    public void testProfiling() {
        final Tuple.Pair<Obj, Long> mtronResult = CommonUtil.clock(() -> {
            final Obj result = ObjmtronSerializer.parse("*/g/V/+.out().>|.out().>|.out().>|.out().count()").apply();
            final String s = result.toString();
            return result;
        });
        LOG.error("mtron>   %s [%s ms]", mtronResult.get0(), mtronResult.get1());
        final Tuple.Pair<Obj, Long> gremlinResult = CommonUtil.clock(() -> {
            final Obj result = ObjmtronSerializer.parse("*</sys/space/test>.gremlin?#<=#('g.V().out().out().out().out().count().next()')").apply();
            final String s = result.toString();
            return result;
        });
        LOG.error("gremlin> %s [%s ms]", gremlinResult.get0(), gremlinResult.get1());
    }

    // ========================================================================
    //  AbstractDataPathTest — collection→Type contract
    // ========================================================================

    @Override
    @ParameterizedTest
    @CsvSource(value = {
            "*<$$/+>            % collection",
            "*<$$/+/+>.take(1)  % entry",
            "*<$$/+/+>.take(2)  % entry",
    }, delimiter = '%')
    public void testDataPathSegmentTypes(final String code, final String segmentType) {
        super.testDataPathSegmentTypes(code, segmentType);
    }

    // ========================================================================
    //  Disabled abstract test overrides
    //  (kept: tests needing wildcard/container/traversal/field-mutation
    //   semantics not yet supported by the flat store — Phase 3+)
    // ========================================================================

    @Override
    @Test
    @Disabled("container aggregation + wildcard reads not yet supported by the flat store")
    public void testMonoRootlessReadWrites() {
        super.testMonoRootlessReadWrites();
    }

    @Override
    @Disabled
    public void testMonoReadWrite(String writeExpression, String readExpression, String expectedExpression) {
    }

    @Override
    @Disabled
    public void testMonoUpdate() {
    }

    @Override
    @Disabled
    public void testMonoDepth(String writeExpression, String readExpression) {
    }

    @Override
    @Disabled
    public void testMultiFieldUpdates(int fieldCount) {
    }

    // ========================================================================
    //  CommonRewritesTestContract — parameterized rewrite tests
    // ========================================================================

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("provideAllRewriteTestCases")
    public void testRewriteOptimizations(String description, String code, Obj expected) throws Exception {
        runRewriteTest(description, code, expected);
    }

    static Stream<Arguments> provideAllRewriteTestCases() {
        return new AbstractGrphSpaceTest(f("/g")) {
        }.generateAllRewriteTestCases();
    }

    // ========================================================================
    //  Serialization round-trip — URI <> wrapping
    // ========================================================================

    @ParameterizedTest(name = "[{index}] {3}")
    @TestData("int::T[?>0]@nat")
    @CsvSource(value = {
            "/g/V/_?incrq -> dog::[test_uri=>a/b/c, test_str=>hello, test_nat=>nat::23]  %  */g/V/+.?[test_uri=>uri::T]>>test_uri  %   uri   %  uri::T round-trip",
            "/g/V/_?incrq -> dog::[test_uri=>a/b/c, test_str=>hello, test_nat=>nat::23]  %  */g/V/+.has(test_str)>>test_str        %   str   %  str::T round-trip",
            "/g/V/_?incrq -> dog::[test_uri=>a/b/c, test_str=>hello, test_nat=>nat::23]  %  */g/V/+.has(test_nat)>>test_nat        %   nat   %  nat::T round-trip",
    }, delimiter = '%')
    public void testSerializationRoundTrip(final String writeExpr,
                                           final String readExpr,
                                           final String expectedKind,
                                           final String description) {
        final Obj writeResult = ObjmtronSerializer.parse(writeExpr).apply();
        LOG.warn("write result: %s", writeResult);
        LOG.warn("DB: %s", ObjmtronSerializer.parse("*/g/V/+").apply());
        LOG.warn("schema: %s", space.at(SCHEMA));
        if (this.sleepBetweenReads > 0)
            CommonUtil.sleepThread(this.sleepBetweenReads);
        final Obj result = ObjmtronSerializer.parse(readExpr).apply();
        LOG.warn("read result: %s", result);
        assertFalse(result.isNoObj(),
                description + ": read should not be noobj: " + make(readExpr));
        switch (expectedKind) {
            case "uri" -> assertTrue(result.isUri(),
                    description + ": expected Uri, got " + result.tid());
            case "str" -> assertTrue(result.isStr(),
                    description + ": expected Str, got " + result.tid());
            case "nat" -> assertEquals(f("nat"),
                    result.tid().basePath(),
                    description + ": expected nat::T TID, got " + result.tid());
        }
    }
}
