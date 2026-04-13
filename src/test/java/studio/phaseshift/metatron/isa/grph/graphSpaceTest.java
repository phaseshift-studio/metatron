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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.AbstractSpaceTest;
import studio.phaseshift.metatron.isa.grph.space.graphSpace;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.impl.MObjFactory;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.Tuple;

import static org.apache.tinkerpop.gremlin.LoadGraphWith.GraphData.MODERN;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.grph.grphInstSet.EDGE_TID;
import static studio.phaseshift.metatron.isa.grph.grphInstSet.GRPH_ISA_TID;
import static studio.phaseshift.metatron.isa.grph.space.schema.modernSchema.MODERN_SCHEMA_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * Test suite for graphSpace demonstrating support for any TinkerPop3-compliant graph database.
 * <p>
 * The tests use TinkerGraph with the "modern" dataset, but the same configuration pattern
 * works with any TP3-enabled graph (JanusGraph, Neo4j, Neptune, etc.).
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class graphSpaceTest extends AbstractSpaceTest {

    public graphSpaceTest() {
        super(() -> {
            // Example: TinkerGraph with modern dataset (legacy format - still supported)
            return graphSpace.of(rec(
                            PATTERN, uri("/g/#"),
                            ROUTE, rec(
                                    uri("/g/V"), uri("V"),
                                    uri("/g/E"), uri("E"),
                                    uri("/g/S"), uri(MODERN_SCHEMA_TID)),
                            // GRAPH, rec(
                            //         uri("gremlin.graph"),
                            //         str("org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph")),
                            NATIVE, rec(
                                    uri("factory"), MObjFactory.single(),
                                    uri(LOAD), uri(MODERN.name().toLowerCase()))),
                    f("/sys/space/test"));

            /* Alternative: New-style configuration (explicitly using GraphFactory)
            return graphSpace.of(rec(
                    PATTERN, uri("/g/#"),
                    ROUTE, rec(
                            uri("/g/V"), uri("V"),
                            uri("/g/E"), uri("E"),
                            uri("/g/S"), uri(MODERN_SCHEMA_TID)),
                    GRAPH, rec(
                            uri("gremlin.graph"),
                                str("org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph")),
                    NATIVE, rec(
                            uri("factory"), MObjFactory.single(),
                            uri(LOAD), uri(MODERN.name().toLowerCase()))),
                    f("/sys/space/test"));
            */
        });
    }

    @BeforeAll
    public static void setupInstSet() {
        InstSet.importInstSet(GRPH_ISA_TID);
        //  InstSet.importInstSet(MODERN_SCHEMA_TID);
    }

    @Test
    @Disabled
    public void testProfiling() {
        //  BootLoader.TYPE_CHECK = false;
        final Tuple.Pair<Obj, Long> mtronResult = CommonUtil.clock(() -> {
            final Obj result = mParser.eval("*/g/V/+.out().>|.out().>|.out().>|.out().count()");
            // Force any lazy evaluation by consuming the result
            final String s = result.toString();
            return result;
        });
        LOG.error("mtron>   %s [%s ms]", mtronResult.get0(), mtronResult.get1());
        final Tuple.Pair<Obj, Long> gremlinResult = CommonUtil.clock(() -> {
            final Obj result = mParser.eval("*</sys/space/test>.gremlin?#<=#('g.V().out().out().out().out().count().next()')");
            final String s = result.toString();
            return result;
        });
        LOG.error("gremlin> %s [%s ms]", gremlinResult.get0(), gremlinResult.get1());
        // BootLoader.TYPE_CHECK = true;
    }

    @ParameterizedTest
    @CsvSource(value = {
            "*/g/S.count()                                                                  % 1",
            "*/g/S>>pattern                                                                  % /m/grph/schema/modern/#",
            "*/g/S>>pattern.*(_).count()                                                       % 5",
            "*/g/S>>pattern.*_.count()                                                       % 5",
            //  "**/g/S/pattern.count()                                                       % 4",
            "*/g/S>>pattern.*(_).vid()                                                         % {/m/grph/schema/modern, /m/grph/schema/modern/person,/m/grph/schema/modern/software,/m/grph/schema/modern/created,/m/grph/schema/modern/knows}",
    }, delimiter = '%')
    public void testSchemaTraversal(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "*/g/V/#>>name                                                                 % {\"marko\",\"josh\",\"peter\",\"lop\",\"vadas\",\"ripple\"}",
            //   "*/g/V/+>>OUT/+/IN/name                                                         % {\"josh\",str{3}::\"lop\",\"vadas\",\"ripple\"}",
            "*/g/V/+>>OUT/+>>IN/name                                                        % {\"josh\",str{3}::\"lop\",\"vadas\",\"ripple\"}",
            //   "*/g/V/+/OUT/+/IN/name                                                          % {\"josh\",str{3}::\"lop\",\"vadas\",\"ripple\"}",
            "*/g/V/1/name                                                                   % \"marko\"",
            "*/g/V/1/OUT.dom()                                                              % {created,{2}knows}",
            "*/g/V/1/OUT/created.count()                                                    % 1",
            "*/g/V/1/OUT/knows.count()                                                      % 2",
            // "*/g/V/1/OUT/+.count()                                                          % 3",
            "*/g/V/1/OUT/knows.>>IN.count()                                                 % 2",
            "*/g/V/1/OUT/knows.>>IN>>name                                                   % {\"vadas\",\"josh\"}",
            "*/g/V/1/OUT/knows.>>IN/name                                                    % {\"vadas\",\"josh\"}",
            "*/g/V/+.count()                                                                % 6",
            "*/g/V/+.outE().count()                                                         % 6",
            "*/g/V/1>>OUT/+.>>IN.count()                                                    % 3",
            //   "*/g/V/1>>OUT/+/IN.count()                                                      % 3",
            "*/g/V/1>>OUT/+>>IN.count()                                                      % 3",
            //"*/g/V/1/OUT/+>>IN.count()                                                      % 3",
            "*/g/V/1/OUT/created/IN.count()                                                 % 1",
            //    "*/g/V/1/OUT/+/IN.count()                                                       % 3",
            "*/g/V/1>>OUT/+.>>IN/OUT/+.>>IN.count()                                         % 2",
            //      "*/g/V/1>>OUT/+/IN/OUT/+/IN.count()                                             % 2",
            "*/g/V/1>>OUT/+>>IN/OUT/+>>IN.count()                                             % 2",
            //     "*/g/V/1/OUT/+/IN/OUT/+/IN.count()                                              % 2",
            //   "*/g/V/1/OUT/+>>IN/OUT/+>>IN.count()                                            % 2",
            "*/g/V/1>>OUT/+/IN/OUT/+/IN/OUT/+/IN.count()                                   % 0",
           // "*/g/V/1/OUT/+/IN/OUT/+/IN/OUT/+/IN.count()                                     % 0",
            "*/g/V/#.count()                                                                % 6",
            "*/g/E/+.count()                                                                % 6",
            "*/g/V/1.count()                                                                % 1",
            "*/g/E/#.count()                                                                % 6",
            "*/g/E/1.count()                                                                % 0",
            // "*/g/V/+>>OUT/+/+.count()                                                       % 6",
            "*/g/V/+>>OUT/+>>+.count()                                                      % 6",
            "/g/V/1 -> noobj; /g.-<[mult(V/+).*(_).count(),mult(E/+).*(_).count()]          % [5,3]",
            "*/g/V/1.update[name=>'dr.marko']                                               % person::[name=>'dr.marko',age=>29]@/g/V/1",
            "*/g/V/1.update[name=>123]                                                      % <ERROR>",
            "*/g/V/1.update[name=>123]                                                      % <ERROR>"
    }, delimiter = '%')
    public void testIdTraversals(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "*/g/V/1>>=[age=>23]                                               % */g/V/1>>age                     % 23",
            "*/g/V/1>>=[age=>+10]                                              % */g/V/1>>age                     % 39",
            "*/g/V/1                                                           % */g/V/1>>age                     % 29",
            "*/g/V/1>>=[age=>/noobj]                                           % */g/V/1>>age                     % noobj",
            "*/g/V/1>>=[age=>-<[_]>-]                                          % */g/V/1>>age                     % 29",
            "*/g/V/1>>=[age=>_]                                                % */g/V/1>>age                     % 29",
            "*/g/V/1>>=[name=>-<[_,<<.>>age.as(str::T)]>-.sum?str<=str{*}()]   % */g/V/1>>name                    % \"marko29\"",
            "*/g/V/1>>=[age=><<>>name]                                         % */g/V/1>>age                     % \"marko\"",
            "*/g/V/1>>=[age=>'hello']                                          % */g/V/1>>age                     % \"hello\"",
            "*/g/V/1>>=[likes=>food]                                           % */g/V/1>>likes                   % food",
            "*/g/V/1>>=[likes=>|!*/g/V/2]                                      % */g/V/1>>likes                   % */g/V/2",
            "*/g/V/1>>=[likes=>[!*/g/V/2,!*/g/V/3]]                            % */g/V/1>>likes>-                 % 1-<{*/g/V/2,*/g/V/3}",
            // "*/g/V/1>>=[worksWith=>|!*/g/V/3]                                  % */g/V/1                          % */g/V/3"

    }, delimiter = '%')
    public void testVertexUpdate(final String update, final String select, final String expected) {
        mParser.eval(update);
        AbstractMetatronTest.checkCodeParseApply(LOG, select, expected);
    }

    @ParameterizedTest
    @Disabled
    @CsvSource(value = {
            "*/g/V/1.addE(likes,*/g/V/2)                                       % */g/V/1.out(likes)                     % */g/V/2",
    }, delimiter = '%')
    public void testAddVertex(final String update, final String select, final String expected) {
        assertTrue(mParser.eval(update).test(T(EDGE_TID)));
        AbstractMetatronTest.checkCodeParseApply(LOG, select, expected);
    }

    // Disable all abstract tests - graphSpace is for graph traversals, not general CRUD
    @Override
    @Disabled
    public void testMonoReadWrite(String writeExpression, String readExpression, String expectedExpression) {
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
