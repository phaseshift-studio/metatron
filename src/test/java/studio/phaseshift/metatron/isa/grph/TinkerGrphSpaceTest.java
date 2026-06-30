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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.grph.space.schema.modernSchema.MODERN_SCHEMA_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * Test suite for {@link studio.phaseshift.metatron.isa.grph.space.grphSpace}
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
@Disabled
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
                uri(ROUTE), rec(
                        uri("/g/V"), uri("V"),
                        uri("/g/E"), uri("E"),
                        uri("/g/S"), uri(MODERN_SCHEMA_TID)));

        // ── rewrite test space config (pattern /grt/#) ──
        rewriteTestConfigRec = rec(
                uri(PATTERN), uri("/grt/#"),
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
        LOG.warn(studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer.parse(code).resolve(space));
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

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
        LOG.debug(studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer.parse(code).resolve(this.space));
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
