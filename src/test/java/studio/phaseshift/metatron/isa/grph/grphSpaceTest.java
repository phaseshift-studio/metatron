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

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.AbstractSpaceTest;
import studio.phaseshift.metatron.isa.grph.space.grphSpace;
import studio.phaseshift.metatron.isa.grph.space.schema.modernSchema;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.type.impl.MObjFactory;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.Tuple;

import static studio.phaseshift.metatron.furi.fURI.Singleton.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.INSTSET_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

import java.util.Map;

import static org.apache.tinkerpop.gremlin.LoadGraphWith.GraphData.MODERN;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.grph.grphInstSet.EDGE_TID;
import static studio.phaseshift.metatron.isa.grph.grphInstSet.GRPH_ISA_TID;
import static studio.phaseshift.metatron.isa.grph.space.schema.modernSchema.MODERN_SCHEMA_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * Test suite for grphSpace demonstrating support for any TinkerPop3-compliant graph database.
 * <p>
 * The tests use TinkerGraph with the "modern" dataset, but the same configuration pattern
 * works with any TP3-enabled graph (JanusGraph, Neo4j, Neptune, etc.).
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class grphSpaceTest extends AbstractSpaceTest {

    public grphSpaceTest() {
        super(f("/g"), () -> {
            return grphSpace.of(rec(
                            PATTERN, uri("/g/#"),
                            ROUTE, rec(
                                    uri("/g/V"), uri("V"),
                                    uri("/g/E"), uri("E"),
                                    uri("/g/S"), uri(MODERN_SCHEMA_TID)),
                            GRAPH, rec(
                                    uri("gremlin.graph"),
                                    uri("org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph")),
                            NATIVE, rec(
                                    uri("factory"), MObjFactory.single(),
                                    uri(LOAD), uri(MODERN.name().toLowerCase()))),
                    f("/sys/space/test_" + System.nanoTime()));
        });
    }

    @BeforeAll
    public static void setupAll() {
        InstSet.importInstSet(GRPH_ISA_TID);
        InstSet.importInstSet(MODERN_SCHEMA_TID);
    }
    

    @AfterAll
    public static void cleanupSchema() {
        Router.global().removeSpace(GRPH_ISA_TID);
    }

    /**
     * Instruction-call traversals (outE, out, inV, outV, inE, in, etc.).
     * Delegates to the route-based directReader via Router.readFromSpace using the vertex/edge VID.
     */
    @ParameterizedTest
    @CsvSource(value = {
            // ── outE (vertex → edges) ──
            "*/g/V/1.outE().count()                                                % 3",
            "*/g/V/1.outE(knows).count()                                            % 2",
            "*/g/V/1.outE(created).count()                                          % 1",
            "*/g/V/1.outE(nonexistent).count()                                      % 0",
            // ── inE (vertex → incoming edges) ──
            "*/g/V/2.inE().count()                                                   % 1",
            "*/g/V/2.inE(knows).count()                                               % 1",
            // ── bothE ──
            "*/g/V/1.bothE(+).count()                                                  % 3",
            "*/g/V/1.bothE(+).count?int<=#{*}()                                                  % 3",
            // "*/g/V/1.bothE().count?int<=#{*}()                                                  % 3",
            "*/g/V/1.bothE(knows).count?int<=#{*}()                                              % 2",
            // ── out (vertex → adjacent vertices, skipping edges) ──
            "*/g/V/1.out().count?int<=#{*}()                                                     % 3",
            "*/g/V/1.out(knows).count?int<=#{*}()                                                % 2",
            "*/g/V/1.out(knows)>>name                                                   % {\"vadas\",\"josh\"}",
            "*/g/V/1.out(created)>>name                                                 % \"lop\"",
            // ── in (vertex → incoming adjacent vertices) ──
            "*/g/V/2.in().count()                                                       % 1",
            "*/g/V/2.in(knows).count()                                                   % 1",
            // ── both ──
            "*/g/V/1.both().count?int<=#{*}()                                                     % 3",
            "*/g/V/1.both(knows).count?int<=#{*}()                                                % 2",
            // ── inV / outV / bothV (edge → endpoint vertices) ──
            "*/g/E/7.inV()>>name                                                        % \"vadas\"",
            "*/g/E/7.outV()>>name                                                       % \"marko\"",
            "*/g/E/7.inV()>>age                                                         % 27",
            // ── edge property access via instruction chain ──
            "*/g/V/1.outE(created)>>weight                                              % 0.4000",
            "*/g/V/1.outE(knows)>>weight                                                % {0.5000,1.0000}",
            // ── mixed route + instruction ──
            "*/g/V/1/OUT/knows.inV()>>name                                              % {\"vadas\",\"josh\"}",
            "*/g/V/1.outE(knows).inV()>>name                                                % {\"vadas\",\"josh\"}",
            // ── wildcard vertex set with outE ──
            "*/g/V/+.outE(+).count()                                                    % 6",
            "*/g/V/+.outE(+).count?int<=#{*}()                                                    % 6",
            // "*/g/V/+.outE().count?int<=#{*}()                                                    % 6",
            "*/g/V/+.outE(knows).count()                                                % 2",
            "*/g/V/+.outE(created).count()                                              % 4",
            // ── wildcard vertex set with out ──
            "*/g/V/+.out().count?int<=#{*}()                                                      % 6",
            "*/g/V/+.out(knows)>>name                                                   % {\"vadas\",\"josh\"}",
    }, delimiter = '%')
    public void testInstructionCallTraversals(final String code, final String expected) {
        LOG.warn(ObjmtronSerializer.parse(code).resolve(space));
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @Override
    @Test
    @Disabled("grphSpace stores labeled vertices/edges under V/E collections; arbitrary KV paths not supported")
    public void testMonoRootlessReadWrites() {
        super.testMonoRootlessReadWrites();
    }

    /**
     * Demonstrates distributed vertex edges: the vertex lives in grphSpace,
     * but its "purchases" edges live in a separate memSpace, assembled
     * via scheme-prefixed label routing ({@code mem_purchases:}).
     */
    @ParameterizedTest
    @CsvSource(value = {
            // vertex → OUT edges
            "*/g/V/1/OUT/+.count()                                                % 3",
            "*/g/V/1/OUT/knows.count()                                            % 2",
            "*/g/V/1/OUT/created.count()                                          % 1",
            // vertex → IN edges
            "*/g/V/2/IN.count()                                                   % 1",
            "*/g/V/4/IN.count()                                                   % 1",
            // edge → endpoint vertex
            "*/g/E/7/IN/name                                                       % \"vadas\"",
            "*/g/E/7/OUT/name                                                      % \"marko\"",
            // OUT → IN cascade (vertex → edge → target vertex)
            "*/g/V/1/OUT/created/IN/name                                           % \"lop\"",
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
        LOG.warn(ObjmtronSerializer.parse(code).resolve(this.space));
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
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
                    uri("owner"), ObjmtronSerializer.parse("!*/g/V/2").apply(noobj())));
            hot.write(f("/te/hot/1/b"), rec(uri("item"), str("mouse"), uri("total"), real(25.0)));
            // also seed hot edges for vertex 4 to test multi-vertex external reads
            hot.write(f("/te/hot/4/c"), rec(uri("item"), str("monitor"), uri("total"), real(300.0)));
            cold.write(f("/te/cold/1/x"), rec(uri("item"), str("book"), uri("total"), real(15.0),
                    uri("seller"), ObjmtronSerializer.parse("!*/g/V/4").apply(noobj())));

            // verify memSpace data is accessible directly
            assertTrue(hot.readStream(f("/te/hot/1/+")).toList().size() == 2,
                    "memSpace should have 2 hot edges for vertex 1");
            assertTrue(hot.readStream(f("/te/hot/4/+")).toList().size() == 1,
                    "memSpace should have 1 hot edge for vertex 4");
            final String[] tests = {
                    // ── hot edges: grphSpace vertex 1 → OUT via scheme label te:hot ──
                    "*/g/V/1/OUT/te:hot/+.count()                                               % 2",
                    "*/g/V/1/OUT/te:hot/a/item                                                    % \"laptop\"",
                    "*/g/V/1/OUT/te:hot/a/total                                                   % 1200.0",
                    "*/g/V/1/OUT/te:hot/b/item                                                    % \"mouse\"",
                    "*/g/V/1/OUT/te:hot/b/total                                                   % 25.0",
                    // ── cold edges ──
                    "*/g/V/1/OUT/te:cold/+.count()                                               % 1",
                    "*/g/V/1/OUT/te:cold/x/item                                                    % \"book\"",
                    "*/g/V/1/OUT/te:cold/x/total                                                   % 15.0",
                    // ── passthrough: grphSpace → external → back to grphSpace ──
                    "*/g/V/1/OUT/te:hot/a/owner/name                                             % \"vadas\"",
                    "*/g/V/1/OUT/te:cold/x/seller/name                                            % \"josh\"",
                    // ── local edges still coexist ──
                    "*/g/V/1/OUT/knows.count()                                                   % 2",
                    "*/g/V/1/OUT/created/IN/name                                                  % \"lop\"",
                    // ── multi-vertex: vertex 4 has both local and external hot edges ──
                    "*/g/V/4/OUT/te:hot/+.count()                                                % 1",
                    "*/g/V/4/OUT/te:hot/c/item                                                     % \"monitor\"",
                    "*/g/V/4/OUT/te:hot/c/total                                                    % 300.0",
                    // ── instruction-call syntax equivalents ──
                    "*/g/V/1.outE(te:hot).count()                                                   % 2",
                    "*/g/V/1.outE(te:hot/a)>>item                                                    % \"laptop\"",
                    "*/g/V/1.outE(te:hot/a)>>total                                                   % 1200.0",
                    "*/g/V/1.outE(te:hot/a)>>owner>>name                                            % \"vadas\"",
                    "*/g/V/1.outE(te:cold/x)>>total                                                   % 15.0",
                    "*/g/V/1.outE(knows).count()                                                    % 2",
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


    @Test
    @Disabled
    public void testProfiling() {
        //  BootLoader.TYPE_CHECK = false;
        final Tuple.Pair<Obj, Long> mtronResult = CommonUtil.clock(() -> {
            final Obj result = ObjmtronSerializer.parse("*/g/V/+.out().>|.out().>|.out().>|.out().count()").apply();
            // Force any lazy evaluation by consuming the result
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
        // BootLoader.TYPE_CHECK = true;
    }

    @ParameterizedTest
    @CsvSource(value = {
            "*/g/V/1                                                                  % person::T    % true",
            //  "*/g/V/1                                                                  % rec::T       % true",
            "*/g/V/1                                                                  % rec::T       % true",
            "*/g/V/2                                                                  % person::T    % true",
            "*/g/V/2                                                                  % software::T  % false",
            "*/g/V/3                                                                  % software::T  % true",
            "*/g/V/3                                                                  % created::T   % false",
            "*/g/V/1                                                                  % created::T   % false",
            "*/g/V/+                                                                  % #{+}::T   % true",
            //   "*/g/V/+                                                                  % rec{+}::T   % true",
            "*/g/V/1{2}                                                               % int{2}::T  % false",
            "*/g/V/1.-<[_,_]>-                                                        % person{2}::T  % true",
            "*/g/V/1.-<[_,_]>-                                                        % vrtx{2}::T  % true",
            "*/g/V/1.-<[_,_]>-                                                        % rec{2}::T  % true",
            "*/g/V/1.-<[_,_]>-                                                        % #{2}::T  % true",
            "*/g/V/1.-<[_,_]>-                                                        % str{2}::T  % false",
            //  "*/g/V/1{2}                                                               % elmt{2}::T  % true",
            "*/g/V/1{2}                                                               % person{2}::T % true",
            // "*/g/V/1                                                                  % rec{2}::T   % true",
            "*/g/V/1.-<[_,_]>-                                                        % rec{3}::T   % false",
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


    @ParameterizedTest
    @CsvSource(value = {
            "*/g/S.count()                                                                   % 1",
            "*/g/S>>pattern                                                                  % /m/grph/schema/modern/#",
            "*/g/S>>pattern.*(_).count()                                                     % 4",
            "*/g/S>>pattern.*_.count()                                                       % 4",
            //  "**/g/S/pattern.count()                                                      % 4",
            "*/g/S>>pattern.*(_).vid()                                                        % {/m/grph/schema/modern/person,/m/grph/schema/modern/software,/m/grph/schema/modern/created,/m/grph/schema/modern/knows}",
    }, delimiter = '%')
    public void testSchemaTraversal(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "*/g/V/1/OUT>>weight                                                             % {0.5000, 1.000, 0.4000}",
            "*/g/V/#>>name                                                                   % {\"marko\",\"josh\",\"peter\",\"lop\",\"vadas\",\"ripple\"}",
            //"*/g/V/+>>OUT/+/IN/name                                                         % {\"josh\",str{3}::\"lop\",\"vadas\",\"ripple\"}",
            "*/g/V/+/OUT/+/IN/name                                                          % {\"josh\",str{3}::\"lop\",\"vadas\",\"ripple\"}",
            "*/g/V/1/OUT/+/IN>>name                                                          % {\"josh\",\"lop\",\"vadas\"}",
            "*/g/V/1/OUT/+/IN/name                                                          % {\"josh\",\"lop\",\"vadas\"}",
            "*/g/V/1/name                                                                   % \"marko\"",
            "*/g/V/1.>>{name,age}                                                           % {\"marko\",29}",
            "*/g/V/1/OUT.dom()                                                              % {3}weight",
            "*/g/V/1/OUT/created.count()                                                    % 1",
            "*/g/V/1.out(created).count()                                                   % 1",
            "*/g/V/1/OUT/knows.count()                                                      % 2",
            "*/g/V/1.out(knows).count?int<=#{*}()                                           % 2",
            "*/g/V/1/OUT/created.count()                                                    % 1",
            "*/g/V/1/OUT/knows.count()                                                      % 2",
            "*/g/V/1/OUT/+.count()                                                          % 3",
            "*/g/V/1/OUT/knows.>>IN.count()                                                 % 2",
            "*/g/V/1/OUT/knows/IN.count()                                                   % 2",
            "*/g/V/1/OUT/knows.>>IN>>name                                                   % {\"vadas\",\"josh\"}",
            "*/g/V/1/OUT/knows/IN/name                                                      % {\"vadas\",\"josh\"}",
            //"*/g/V/1/OUT/knows.>>IN/name                                                    % {\"vadas\",\"josh\"}",
            "*/g/V/1/OUT/knows.>>IN.>>name                                                  % {\"vadas\",\"josh\"}",
            // "*/g/V/1.>>OUT.>>knows.>>IN.>>name                                                    % {\"vadas\",\"josh\"}",
            // "*/g/V/1.>>OUT/knows.>>IN/name                                                    % {\"vadas\",\"josh\"}",
            "*/g/V/+.count()                                                                % 6",
            "*/g/V/OUT.count()                                                              % 6",
            "*/g/V/1.outE().count()                                                         % 3",
            "*/g/V/1.outE(knows).count()                                                    % 2",
            "*/g/V/1.outE(created).count()                                                  % 1",
             "*/g/V/+.outE().count()                                                        % 6",
            // "*/g/V/1>>OUT/+.>>IN.count()                                                 % 3",
            "*/g/V/1/OUT/+/IN.count()                                                       % 3",
            //"*/g/V/1>>OUT/+>>IN.count()                                                      % 3",
            "*/g/V/1/OUT/+>>IN.count()                                                      % 3",
            "*/g/V/1/OUT/created/IN.count()                                                 % 1",
            "*/g/V/1/OUT/+/IN.count()                                                       % 3",
            //"*/g/V/1>>OUT/+.>>IN/OUT/+.>>IN.count()                                         % 2",
            //      "*/g/V/1>>OUT/+/IN/OUT/+/IN.count()                                             % 2",
            //"*/g/V/1>>OUT/+>>IN/OUT/+>>IN.count()                                             % 2",
            "*/g/V/1/OUT/+/IN/OUT/+/IN.count()                                              % 2",
            //   "*/g/V/1/OUT/+>>IN/OUT/+>>IN.count()                                            % 2",
            //"*/g/V/1>>OUT/+/IN/OUT/+/IN/OUT/+/IN.count()                                   % 0",
             "*/g/V/1/OUT/+/IN/OUT/+/IN/OUT/+/IN.count()                                    % 0",
            //"*/g/+.count()                                                                 % 12",
            "*/g/V/+.count()                                                                % 6",
            "*/g/V/#.count()                                                                % 18",
            "*/g/V/1.count()                                                                % 1",
            "*/g/E/+.count()                                                                % 6",
            "*/g/E/#.count()                                                                % 12",
            "*/g/E/1.count()                                                                % 0",
           // "*/g/V/+>>OUT/created.count()                                                   % 4",
           //"*/g/V/+>>OUT/knows.count()                                                     % 2",
           "*/g/V/+>>OUT/+.count()                                                          % 6",
           "*/g/V/+>>OUT/+>>+.count()                                                       % 6",
            "/g.-<[mult(V/+).*(_).count(),mult(E/+).*(_).count()]                           % [6,6]",
            "@/g/V/1.>>=[name=>'dr.marko']                                                  % person::[name=>'dr.marko',age=>29]@/g/V/1",
            "@/g/V/1.>>=[name=>123]                                                         % <ERROR>",
            "@/g/V/1.>>=[name=>123]                                                         % <ERROR>",
            "[!*/g/V/1,!*/g/V/3]>-.bothE().count()                                          % 6"
    }, delimiter = '%')
    public void testIdTraversals(final String code, final String expected) {
        LOG.debug(ObjmtronSerializer.parse(code).resolve(this.space));
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
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
            //"@/g/V/1>>=+[likes=>|!*/g/V/2]                                     % */g/V/1>>likes                   % */g/V/2",
            //"@/g/V/1>>=+[likes=>[!*/g/V/2,!*/g/V/3]]                           % */g/V/1>>likes>-                 % 1-<[*/g/V/2,*/g/V/3]>-",
            // "*/g/V/1>>=[worksWith=>|!*/g/V/3]                                  % */g/V/1                          % */g/V/3"

    }, delimiter = '%')
    public void testVertexUpdate(final String update, final String select, final String expected) {
        //LOG.warn(ObjmtronSerializer.parse(update).apply());
        AbstractMetatronTest.checkCodeEvaluate(LOG, update, select, expected);
    }

    @ParameterizedTest
    @Disabled
    @CsvSource(value = {
            "*/g/V/1.addE(likes,*/g/V/2)                                       % */g/V/1.out(likes)                     % */g/V/2",
    }, delimiter = '%')
    public void testAddVertex(final String update, final String select, final String expected) {
        AbstractMetatronTest.checkCodeEvaluate(LOG, update, select, expected);
    }

    // Disable all abstract tests - grphSpace is for graph traversals, not general CRUD
    @Override
    @Disabled
    public void testMonoReadWrite(String writeExpression, String readExpression, String expectedExpression) {
    }

    // Disable all abstract tests - grphSpace is for graph traversals, not general CRUD
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
    public void testStringCornerCases(String description, String value) {
    }

    @Override
    @Disabled
    public void testIntegerBoundaries(String description, long value) {
    }

    @Override
    @Disabled
    public void testRealBoundaries(String description, double value) {
    }

    @Override
    @Disabled
    public void testBooleanValues(String description, boolean value) {
    }

    @Override
    @Disabled
    public void testNonExistentAccess(String key) {
    }

    @Override
    @Disabled
    public void testSequentialUpdates(int iterations) {
    }

    @Override
    @Disabled
    public void testBasicCRUD(String description, String key, String valueStr) {
    }

    @Override
    @Disabled
    public void testTypePreservation(String description, Obj value) {
    }

    @Override
    @Disabled
    public void testNestedRecords(int depth) {
    }

    @Override
    @Disabled
    public void testListHandling(String description, studio.phaseshift.metatron.isa.m.type.Lst listValue, int expectedCount) {
    }

    @Override
    @Disabled
    public void testTypeChanges(String description, Obj initialValue, Obj updatedValue) {
    }

    @Override
    @Disabled
    public void testMultiFieldUpdates(int fieldCount) {
    }

    @Override
    @Disabled
    public void testSpecialStringValues(String description, String value) {
    }

    @Override
    @Disabled
    public void testEmptyRecords(int testNumber) {
    }
}
