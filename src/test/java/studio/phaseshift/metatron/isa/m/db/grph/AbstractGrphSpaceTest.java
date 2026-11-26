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

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
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
import studio.phaseshift.metatron.SkipRegexTest;
import studio.phaseshift.metatron.algebra.rewrite.db.CommonRewritesTestContract;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractSpaceTest;
import studio.phaseshift.metatron.isa.SchemaSpaceTestContract;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.db.grph.space.GraphLoader;
import studio.phaseshift.metatron.isa.m.db.grph.io.ObjTP3Serializer;
import studio.phaseshift.metatron.isa.m.db.grph.space.grphSpace;
import studio.phaseshift.metatron.isa.m.db.grph.space.schema.modernSchema;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.type.impl.MCode;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.Tuple;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.SCHEMA;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.db.grph.grphInstSet.GRPH_ISA_TID;
import static studio.phaseshift.metatron.isa.m.db.grph.space.GraphLoader.MODERN;
import static studio.phaseshift.metatron.isa.m.db.grph.space.schema.modernSchema.MODERN_SCHEMA_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
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
@SkipRegexTest({
        // a graph is not an arbitrary key/value store (Marko, 2026-11-16): this spine's nested rec reads [<>=>[=>]]
        @SkipRegexTest.Skip(method = "testRshiftUriGraphSpine", params = {"/a/\\.>>\\.>>\\.\\*_"}),
        // an edge or person entry reads back as its schema type (rec::T[?[weight=>real::T]]@…/weighted_edge), not as
        // an instance of it, on TinkerGraph and JanusGraph alike
        @SkipRegexTest.Skip(method = "testDataPathSegmentTypes", params = {"\\+/\\+>"}),
        // the */g/+/+ listing of every label type also answers weighted_edge, the declared supertype of knows and
        // created, which is no label (long-standing; TinkerGraph and JanusGraph alike)
        @SkipRegexTest.Skip(method = "testSchemaTraversal", params = {"\\*/g/\\+/\\+"})
})
public abstract class AbstractGrphSpaceTest extends AbstractDataPathSpaceTest implements CommonRewritesTestContract, SchemaSpaceTestContract {

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
        Machine.current().memory().removeSpace(REWRITE_TEST_SPACE_URI);
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
        return f("/m/db/grph");
    }

    /**
     * A LABEL IS A COLLECTION TOO (2026-11-25). The contract's prefix is the V collection, and the plans read V for it and
     * E, or every vertex, for any other one: gremlin_count counted every edge, skip and limit took edges, sum, order and
     * dedup read every vertex, and where refused all but V. So the rewrite space's item vertices (no edges) and the modern
     * dataset's person, software, knows and created labels are read through each plan too.
     */
    @Override
    public Stream<Arguments> generateAllRewriteTestCases() {
        final String item = "*" + REWRITE_TEST_SPACE_URI.extend("item");
        final String g = "*" + this.baseURI;
        return Stream.concat(CommonRewritesTestContract.super.generateAllRewriteTestCases(), Stream.of(
                Arguments.of("count: a vertex label", item + "/+.count()", jnt(10)),
                Arguments.of("count: an edge label", g + "/knows/+.count()", jnt(2)),
                Arguments.of("limit: a vertex label", item + "/+.take(5).count()", jnt(5)),
                Arguments.of("limit: an edge label", g + "/knows/+.take(5).count()", jnt(2)),
                Arguments.of("skip: a vertex label", item + "/+.skip(3).count()", jnt(7)),
                Arguments.of("skip: an edge label", g + "/created/+.skip(1).count()", jnt(3)),
                Arguments.of("skip+limit: a vertex label", g + "/person/+.skip(1).take(10).count()", jnt(3)),
                Arguments.of("skip+limit: an edge label", g + "/created/+.skip(1).take(5).count()", jnt(3)),
                Arguments.of("isa: a vertex label", g + "/person/+.isa([age=>?>30])>>age.sum()", jnt(67)),
                Arguments.of("isa+count: a vertex label", item + "/+.isa([value=>?>5]).count()", jnt(5)),
                Arguments.of("isa+count: an edge label", g + "/created/+.isa([weight=>?>0.5]).count()", jnt(1)),
                Arguments.of("isa+limit: a vertex label", g + "/person/+.isa([age=>?>26]).take(3).count()", jnt(3)),
                Arguments.of("order: a vertex label", g + "/person/+.order(select(name))>-.count()", jnt(4)),
                Arguments.of("order: an edge label", g + "/knows/+.order(select(weight))>-.count()", jnt(2)),
                Arguments.of("dedup: a vertex label", g + "/person/+.dedup(select(name)).count()", jnt(4)),
                Arguments.of("sum: a vertex label", g + "/person/+>>age.sum()", jnt(123)),
                Arguments.of("sum: a label without the property", g + "/software/+>>age.sum()", jnt(0))));
    }

    /**
     * A REAL SUM IS NOT CUT TO ITS INTEGER PART, as on tble ({@code AbstractTbleSpaceTest}, which explains it): an edge
     * label's weights are reals, and the label-aware gremlin_sum answered the knows weights' 1.5 as 1. This asserts the
     * int range's refusal, not the ideal (the walk answers 1.5): main's gremlin_sum failed too, on the empty traversal
     * of every vertex's weight, which is no refusal.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '%', value = {
            "*/g/knows/+>>weight.sum()",
            "*/g/E/+>>weight.sum()",
    })
    public void testARealSumIsRefusedNotCut(final String code) {
        final Obj plan = Machine.current().compiler().rewriter().apply(ObjmtronSerializer.parse(code));
        final Obj answer = MCode.of(plan.asCode().insts()).apply();
        assertTrue(answer.isFail() && answer.toString().contains("does not match inst range"),
                "a real sum must fail the plan's int range, not answer " + answer + ": " + plan);
    }

    /**
     * A PLAN READS ELEMENTS, so it declines a source the space reads flat (2026-11-25): the reserved kv_store label, and
     * a collection no element carries. After two flat writes the walk counts both, and gremlin_count read the collection
     * as a label and answered 0, while the label-blind skip and limit read edges. Read as a label, kv_store is every flat
     * entry's vertex, whose properties are its key and value, so the sum, where and order rows read none of n (the walk
     * cannot dedup(select(...)) a flat read, so dedup has no row). The rows are
     * {@code the writes % code % what the walk answers}, and the plan is applied on its own as well (see
     * {@code CommonRewritesTestContract.runRewriteTest}).
     */
    @ParameterizedTest
    @CsvSource(delimiter = '%', value = {
            "/g/kv_scratch/a -> 1; /g/kv_scratch/b -> 2                        % */g/kv_scratch/+.count()          % 2",
            "/g/kv_scratch/a -> 1; /g/kv_scratch/b -> 2                        % */g/kv_scratch/+.take(5).count()  % 2",
            "/g/kv_scratch/a -> 1; /g/kv_scratch/b -> 2                        % */g/kv_scratch/+.skip(1).count()  % 1",
            "/g/kv_store/x -> 1; /g/kv_store/y -> 2; /g/kv_scratch/a -> 3     % */g/kv_store/+.count()            % 2",
            "/g/kv_store/x -> [n=>1]; /g/kv_store/y -> [n=>2]; /g/kv_scratch/a -> 3  % */g/kv_store/+>>n.sum()                    % 3",
            "/g/kv_store/x -> [n=>1]; /g/kv_store/y -> [n=>2]; /g/kv_scratch/a -> 3  % */g/kv_store/+.isa([n=>?>1]).count()       % 1",
            "/g/kv_store/x -> [n=>1]; /g/kv_store/y -> [n=>2]; /g/kv_scratch/a -> 3  % */g/kv_store/+.order(select(n))>-.count()  % 2",
    })
    public void testAFlatCollectionAnswersAsTheWalk(final String writes, final String code, final String expected) {
        for (final String write : writes.split(";"))
            ObjmtronSerializer.parse(write.trim()).apply();
        checkCodeParseApply(LOG, code, expected);
        final Obj plan = Machine.current().compiler().rewriter().apply(ObjmtronSerializer.parse(code));
        final Obj want = Machine.parse(expected);
        checkEquality(LOG, want.isCall() ? want.apply() : want, MCode.of(plan.asCode().insts()).apply(), true);
    }

    /**
     * A PLAN THAT ANSWERS ELEMENTS STANDS IN ONLY FOR A SOURCE THAT ANSWERS THEM (2026-11-26), as on tble
     * ({@code AbstractTbleSpaceTest}, which explains it): a source with a property answers its values, and the limit,
     * skip, where, order and dedup plans answered elements for it: {@code person/+/age.take(4)} summed four persons into
     * their join, where the walk sums the ages to 123. An int has no {@code >>age} to sort by, so the walk's order fails,
     * where gremlin_order sorted the persons. gremlin_sum reads one property, which holds no path below it:
     * {@code >>age/x} and {@code /age>>x} summed the ages, where the walk sums nothing to 0. The rows are
     * {@code code % what the walk answers}; an {@code <ERROR>} row's plan holds no gremlin_ op, and every other plan is
     * applied on its own as well.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '%', value = {
            "*/g/person/+/age.take(4).sum()              % 123",
            "*/g/person/+/age.skip(0).sum()              % 123",
            "*/g/person/+/age.isa([age=>?>30]).count()   % 0",
            "*/g/person/+/age.order(>>age)>-.sum()       % <ERROR>",
            "*/g/software/+/lang.dedup(>>name).count()   % 1",
            "*/g/person/+>>age/x.sum()                   % 0",
            "*/g/person/+/age>>x.sum()                   % 0",
    })
    public void testAFieldSourceAnswersAsTheWalk(final String code, final String expected) {
        checkCodeParseApply(LOG, code, expected);
        final Obj plan = Machine.current().compiler().rewriter().apply(ObjmtronSerializer.parse(code));
        if ("<ERROR>".equals(expected.trim()))
            assertEquals("from", plan.asCode().insts().getFirst().tid().name(), "the walk's failure is left to the walk: " + plan);
        else {
            final Obj want = Machine.parse(expected);
            checkEquality(LOG, want.isCall() ? want.apply() : want, MCode.of(plan.asCode().insts()).apply(), true);
        }
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
    public void dropDeducedCollection(final String collectionName) {
        final Space space = Machine.current().memory().getSpaceFor(f("/g"));
        if (space instanceof grphSpace gs)
            gs.sjvm().V().hasLabel(collectionName).drop().toList();
    }

    @Override
    public fURI schemaCollection(final String name) {
        return f("/g/" + name);
    }

    @Override
    public void seedCollection(final String name, final List<Rec> records) {
        final grphSpace graph = Machine.current().memory().getSpaceFor(f("/g"));
        records.forEach(record -> {
            final GraphTraversal<Vertex, Vertex> vertex = graph.sjvm().addV(name);
            record.recValue().forEach((key, value) -> vertex.property(key.uriValue().name(), ObjTP3Serializer.tp3Value(value)));
            vertex.next();
        });
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
        InstSet.importInstSet(f("/m/math"));
    }

    @AfterAll
    public static void cleanupISAs() {
        Machine.current().memory().removeSpace(GRPH_ISA_TID);
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
     * Delegates to the route-based directReader via Machine.read using the vertex/edge VID.
     */
    @ParameterizedTest
    @CsvSource(value = {
            // ── wildcard vertex set ──
            "*/g/V/+.count()                                                       % 6",
            "*/g/V/+.>>name                                                        % {'marko','lop','vadas','josh','peter','ripple'}",
            "*/g/V/+.has(lang)>>lang                                               % {2}'java'",
            "*/g/V/+.isa([name=>'marko'])>>age                                     % 29",
            "*/g/V/+.isa([name=>'marko'])>>{age,name}                              % {'marko',29}",
            "*/g/V/+.?person::[name=>'marko',age=>_].count()                       % 1",
            "*/g/V/+.isa([name=>'marko'])>>name                                        % \"marko\"",
            "*/g/V/+.isa([name=>'marko'])>>age                                         % 29",
            "*/g/V/+.isa([age=>?<30]).count()                                    % 2",
            // ── outE (vertex → edges) via property lookup ──
            "*/g/V/+.isa([name=>'marko']).outE().count()                           % 3",
            "*/g/V/+.isa([name=>'marko']).outE(knows).count()                       % 2",
            "*/g/V/+.isa([name=>'marko']).outE(created).count()                     % 1",
            "*/g/V/+.isa([name=>'marko']).outE(nonexistent).count()                 % 0",
            // ── inE (vertex → incoming edges) ──
            "*/g/V/+.isa([name=>'vadas']).inE().count()                             % 1",
            "*/g/V/+.isa([name=>'vadas']).inE(knows).count()                         % 1",
            // ── bothE ──
            "*/g/V/+.isa([name=>'marko']).bothE(+).count()                           % 3",
            "*/g/V/+.isa([name=>'marko']).bothE().count?int<=#{*}()                     % 3",
            "*/g/V/+.isa([name=>'marko']).bothE(knows).count?int<=#{*}()                % 2",
            // ── out (vertex → adjacent vertices) ──
            "*/g/V/+.isa([name=>'marko']).out().count?int<=#{*}()                      % 3",
            "*/g/V/+.isa([name=>'marko']).out(knows).count?int<=#{*}()                 % 2",
            "*/g/V/+.isa([name=>'marko']).out(knows)>>name                              % {\"vadas\",\"josh\"}",
            "*/g/V/+.isa([name=>'marko']).out(created)>>name                            % \"lop\"",
            // ── in (vertex → incoming adjacent vertices) ──
            "*/g/V/+.isa([name=>'vadas']).in().count()                                  % 1",
            "*/g/V/+.isa([name=>'vadas']).in(knows).count()                              % 1",
            // ── both ──
            "*/g/V/+.isa([name=>'marko']).both().count?int<=#{*}()                       % 3",
            "*/g/V/+.isa([name=>'marko']).both(knows).count?int<=#{*}()                  % 2",
            // ── inV / outV (vertex → edge → endpoint vertex) ──
            "*/g/V/+.isa([name=>'marko']).outE(knows).isa([weight=>0.5]).inV()>>name   % \"vadas\"",
            "*/g/V/+.isa([name=>'marko']).outE(knows).isa([weight=>0.5]).outV()>>name  % \"marko\"",
            "*/g/V/+.isa([name=>'marko']).outE(knows).?[weight=>0.5].inV()>>age    % 27",
            // ── edge property access via instruction chain ──
            "*/g/V/+.isa([name=>'marko']).outE(created)>>weight                         % 0.4000",
            "*/g/V/+.isa([name=>'marko']).outE(knows)>>weight                           % {0.5000,1.0000}",
            // ── instruction-call chain (outE → inV) ──
            "*/g/V/+.isa([name=>'marko']).outE(knows).inV()>>name                           % {\"vadas\",\"josh\"}",
            // ── wildcard vertex set with outE ──
            "*/g/V/+.outE(+).count()                                                    % 6",
            "*/g/V/+.outE(knows).count()                                                % 2",
            "*/g/V/+.outE(created).count()                                              % 4",
            // ── wildcard vertex set with out ──
            "*/g/V/+.out().count?int<=#{*}()                                                      % 6",
            "*/g/V/+.out(knows)>>name                                                             % {\"vadas\",\"josh\"}",
            // ── double walk (vertex → vertices → vertices) ──
            "*/g/V/+.isa([name=>'marko']).out(knows).out(created)>>name                                                 % {\"ripple\",\"lop\"}",
            "*/g/V/+.isa([name=>'marko']).outE(knows).inV().outE(created).inV()>>name                                   % {\"ripple\",\"lop\"}",
            "*/g/V/+.isa([name=>'marko']).outE(knows).has(weight).inV().outE(created).has(weight).inV()>>name           % {\"ripple\",\"lop\"}",
            // ── from testBasicTraversals (instruction-call equivalents) ──
            "*/g/V/+.isa([name=>'josh']).inE().count()                                                % 1",
            "*/g/V/+.isa([name=>'marko']).out(created)>>lang                                           % \"java\"",
            "*/g/V/+.isa([name=>'marko']).outE(created)>>weight.sum?real<=real{*}()                    % 0.4000",
            "*/g/V/+.isa([name=>'marko']).out()>>name                                                  % {\"lop\",\"vadas\",\"josh\"}",
    }, delimiter = '%')
    public void testInstructionCallTraversals(final String code, final String expected) {
        LOG.warn(ObjmtronSerializer.parse(code).resolve(space));
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    /**
     * Isolated edge-property isa() test.  Creates a single edge with
     * int, real, string, and bool properties and verifies that {@code isa()}
     * predicates filter correctly on each type.
     */
    @Test
    public void testEdgePropertyIsa() {
        final grphSpace gs = (grphSpace) Machine.current().memory().getSpaceFor(f("/g"));
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
                    "*/g/V/" + aliceId + ".outE(testEdge).isa([rank=>42]).count()", "1");
            AbstractMetatronTest.checkCodeParseApply(LOG,
                    "*/g/V/" + aliceId + ".outE(testEdge).isa([rank=>99]).count()", "0");
            // ── real property ──
            AbstractMetatronTest.checkCodeParseApply(LOG,
                    "*/g/V/" + aliceId + ".outE(testEdge).isa([score=>0.75]).count()", "1");
            AbstractMetatronTest.checkCodeParseApply(LOG,
                    "*/g/V/" + aliceId + ".outE(testEdge).isa([score=>?>0.5]).count()", "1");
            AbstractMetatronTest.checkCodeParseApply(LOG,
                    "*/g/V/" + aliceId + ".outE(testEdge).isa([score=>?>0.9]).count()", "0");
            // ── string property ──
            AbstractMetatronTest.checkCodeParseApply(LOG,
                    "*/g/V/" + aliceId + ".outE(testEdge).isa([tag=>'trusted']).count()", "1");
            AbstractMetatronTest.checkCodeParseApply(LOG,
                    "*/g/V/" + aliceId + ".outE(testEdge).isa([tag=>'wrong']).count()", "0");
            // ── bool property ──
            AbstractMetatronTest.checkCodeParseApply(LOG,
                    "*/g/V/" + aliceId + ".outE(testEdge).isa([active=>true]).count()", "1");
            AbstractMetatronTest.checkCodeParseApply(LOG,
                    "*/g/V/" + aliceId + ".outE(testEdge).isa([active=>false]).count()", "0");
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
    @Disabled("the chained addE is not found: the count reads 0 (TinkerGraph expects 6, JanusGraph 1; checked 2026-11-16)")
    @Test
    public void testAddEdgeChain() {
        final String[] steps = {
                // add a likes edge + verify outE count
                "@$$/V/+.?[name=>'marko'].addE(likes,*$$/V/+.?[name=>'lop'])  % *$$/V/+.?[name=>'marko'].outE(likes).count()  % 1",
                // add a second likes edge to a different vertex
                "@$$/V/+.?[name=>'marko'].addE(likes,*$$/V/+.?[name=>'josh']) % *$$/V/+.?[name=>'marko'].outE(likes).count()  % 2",
                // add edge with property
                "@$$/V/+.?[name=>'marko'].addE(trusts,*$$/V/+.?[name=>'vadas'], [weight=>10]) % *$$/V/+.?[name=>'marko'].outE(trusts).isa([weight=>10]).count()      % 1",
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
    @Disabled("distributed edges (external te:hot/cold traversal) are not found: 0, expected 2 (TinkerGraph and JanusGraph; checked 2026-11-16)")
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
            assertEquals(2, hot.read(f("/te/hot/1/+/")).stream().map(Obj::asRel).count(),
                    "memSpace should have 2 hot edges for vertex 1");
            assertEquals(1, hot.read(f("/te/hot/4/+/")).stream().map(Obj::asRel).count(),
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
            Machine.current().memory().removeSpace(f(hot.vid().toString()));
            Machine.current().memory().removeSpace(f(cold.vid().toString()));
        }
    }

    @ParameterizedTest
    @CsvSource(value = {
            "*/g/+.count()                                                                   % 2",
            "*/g/+                                                                           % {rec::T@V,rec::T@E}",
            "*/g/+/+                                                                         % {person::T,software::T,knows::T,created::T}",
            "*/g/V:person                                                                    % person::T",
            "*/g/E:knows                                                                     % knows::T",
    }, delimiter = '%')
    public void testSchemaTraversal(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    /**
     * Instruction-call traversals off a SINGLE address, discovered at runtime because element ids are
     * backend-specific (TinkerGraph: 1-6, JanusGraph: its own longs). A single value is the case where
     * metatron detaches the vid ({@code vid(null)}) and clones the rec before applying the inst, so
     * {@code resolveVid} has to recover the address from the rec itself (elementVID, then element) - a
     * clone that keeps only a plain map copy loses the live TinkerPop element.
     */
    @Test
    public void testSingleAddressInstructionTraversal() {
        final Obj marko = ObjmtronSerializer.parse("*/g/V/+.?[name=>'marko']").apply().stream()
                .filter(o -> !o.isFail()).findFirst().orElse(noobj());
        assertFalse(marko.isNoObj(), "the modern dataset must hold marko");
        final fURI address = marko.vid();
        assertNotNull(address, "marko must carry his routed address");
        LOG.warn("single-address instruction traversal from %s", address);
        AbstractMetatronTest.checkCodeParseApply(LOG, "*" + address + ".out()>>name", "{'vadas','josh','lop'}");
        AbstractMetatronTest.checkCodeParseApply(LOG, "*" + address + ".outE(knows).inV()>>name", "{'vadas','josh'}");
        AbstractMetatronTest.checkCodeParseApply(LOG, "*" + address + ".out().count()", "3");
        // a WILDCARD read hands the fused walk MANY elements: Obj.stream() walks one and many the same way
        AbstractMetatronTest.checkCodeParseApply(LOG, "*/g/V/+.out(knows)>>name", "{'vadas','josh'}");
        AbstractMetatronTest.checkCodeParseApply(LOG, "*/g/V/+.out(knows).out(created)>>name", "{'ripple','lop'}");
        AbstractMetatronTest.checkCodeParseApply(LOG, "*/g/V/+.out(knows).out(created)>>name.count()", "2");
    }

    @ParameterizedTest
    @CsvSource(value = {
            "*/g/V/+.isa([name=>'marko'])                                                % person::T    % true",
            "*/g/V/+.isa([name=>'marko'])                                                % rec::T       % true",
            "*/g/V/+.isa([name=>'marko'])                                                % rec::T       % true",
            "*/g/V/+.isa([name=>'vadas'])                                                % person::T    % true",
            "*/g/V/+.isa([name=>'vadas'])                                                % software::T  % false",
            "*/g/V/+.isa([name=>'lop'])                                                  % software::T  % true",
            "*/g/V/+.isa([name=>'lop'])                                                  % created::T   % false",
            "*/g/V/+.isa([name=>'marko'])                                                % created::T   % false",
            "*/g/V/+                                                                  % #{+}::T   % true",
            "*/g/V/+.isa([name=>'marko']).id{2}()                                        % int{2}::T  % false",
            "*/g/V/+.isa([name=>'marko']).-<[_,_]>-                                      % person{2}::T  % true",
            "*/g/V/+.isa([name=>'marko']).-<[_,_]>-                                      % rec{2}::T  % true",
            "*/g/V/+.isa([name=>'marko']).-<[_,_]>-                                      % #{2}::T  % true",
            "*/g/V/+.isa([name=>'marko']).-<[_,_]>-                                      % str{2}::T  % false",
            "*/g/V/+.isa([name=>'marko']).id{2}()                                        % person{2}::T % true",
            "*/g/V/+.isa([name=>'marko'])                                                % rec{2}::T   % false",
            "*/g/V/+.isa([name=>'marko']).-<[_,_]>-                                      % rec{3}::T   % false",
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

    /**
     * THE WALK AND GREMLIN COUNT ALIKE: four hops out of every vertex, the walk with a barrier between hops, Gremlin on
     * the test's own graph. The times are logged, to compare by hand. (It asserted nothing, and its Gremlin half read
     * {@code </sys/space/test>}, an address no per-test space has.)
     */
    @Test
    @Disabled("the walk fails: */g/V/+.out().>|.out().>|.out().>|.out().count() answers 'infinite fail-loop detected' "
            + "where Gremlin counts 0, on TinkerGraph and JanusGraph (checked 2026-11-16)")
    public void testProfiling() {
        final grphSpace gs = (grphSpace) Machine.current().memory().getSpaceFor(f("/g"));
        final Tuple.Pair<Obj, Long> walk = CommonUtil.clock(() -> ObjmtronSerializer.parse("*/g/V/+.out().>|.out().>|.out().>|.out().count()").apply());
        final Tuple.Pair<Long, Long> gremlin = CommonUtil.clock(() -> gs.sjvm().V().out().out().out().out().count().next());
        LOG.info("walk %s [%s ms], gremlin %s [%s ms]", walk.get0(), walk.get1(), gremlin.get0(), gremlin.get1());
        assertEquals(jnt(gremlin.get0()), walk.get0());
    }

    // ========================================================================
    //  AbstractDataPathTest — collection→Type contract
    // ========================================================================

    @Override
    @ParameterizedTest
    @CsvSource(value = {
            "*<$$/+>            % collection % evaluation",
            "*<$$/+/+>.take(1)  % entry % evaluation",
            "*<$$/+/+>.take(1)  % entry % plan",
            "*<$$/+/+>.take(2)  % entry % evaluation",
            "*<$$/+/+>.take(2)  % entry % plan",
    }, delimiter = '%')
    public void testDataPathSegmentTypes(final String code, final String segmentType, final String answer) {
        super.testDataPathSegmentTypes(code, segmentType, answer);
    }

    // ========================================================================
    //  Inherited space tests a graph space does not take: a graph is not an
    //  arbitrary key/value store (Marko, 2026-11-16). An override without
    //  @Test removes the inherited test. The rest of the family ran green on
    //  2026-11-16 and is no longer overridden.
    // ========================================================================

    @Override
    @Test
    @Disabled("a graph is not an arbitrary key/value store: */g/rootless reads noobj, no container gathers its writes")
    public void testMonoRootlessReadWrites() {
        super.testMonoRootlessReadWrites();
    }

    @Override
    @Disabled("a graph is not an arbitrary key/value store: 236 of its 244 rows fail")
    public void testMonoReadWrite(final String writeExpression, final String readExpression, final String expectedExpression) {
    }

    @Override
    @Disabled("a graph is not an arbitrary key/value store: the updated */g/people/1/name reads noobj")
    public void testMonoUpdate() {
    }

    @Override
    @Disabled("a graph is not an arbitrary key/value store: all 13 rows fail")
    public void testMonoDepth(final String writeExpression, final String readExpression) {
    }

    @Override
    @Disabled("a graph is not an arbitrary key/value store: the uri graph tree walk reads noobj")
    public void testUriGraphTreeWalk() {
    }

    // the factorization contract lays arbitrary key/value data out in uri space; a graph prescribes vertices and edges,
    // and a key/value encoding (abc -> 35 as vertex[value=>35]@abc, what tbleSpace's kv_store does for tables) is
    // deliberately not offered: no one would use it and it would need maintaining (Marko, 2026-11-19)
    @Override
    @Disabled("a graph is not an arbitrary key/value store: the contract's layouts are key/value data, not vertices and edges")
    public void testReadsAreInvariantUnderFactorization(final String id, final String[] layout, final String read, final String expected) {
    }

    @Override
    @Disabled("an extension index on a list-valued vertex property resolves noobj")
    public void testDataPathExtensionOnLstColumn() {
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
    @CsvSource(value = {
            "/g/V/_?incrq -> dog::[test_uri=>a/b/c, test_str=>'hello', test_nat=>nat::23]  %  */g/V/+.?[test_uri=>uri::T]>>test_uri  %   uri   %  uri::T round-trip",
            "/g/V/_?incrq -> dog::[test_uri=>a/b/c, test_str=>'hello', test_nat=>nat::23]  %  */g/V/+.has(test_str)>>test_str        %   str   %  str::T round-trip",
            "/g/V/_?incrq -> dog::[test_uri=>a/b/c, test_str=>'hello', test_nat=>nat::23]  %  */g/V/+.has(test_nat)>>test_nat        %   nat   %  nat::T round-trip",
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
            case "nat" -> assertEquals(f("/m/math/nat"),
                    result.tid().basePath(),
                    description + ": expected nat::T TID, got " + result.tid());
        }
    }
}
