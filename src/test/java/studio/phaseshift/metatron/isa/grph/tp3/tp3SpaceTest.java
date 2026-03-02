/*
 * Metatron: A Distributed Computing Language and Virtual Machine
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

package studio.phaseshift.metatron.isa.grph.tp3;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.TestSkip;
import studio.phaseshift.metatron.isa.AbstractSpaceTest;
import studio.phaseshift.metatron.isa.grph.tp3.space.tp3Space;
import studio.phaseshift.metatron.isa.m.parser.mParser;

import static org.apache.tinkerpop.gremlin.LoadGraphWith.GraphData.MODERN;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.isa.grph.grphInstSet.GRPH_ISA_TID;
import static studio.phaseshift.metatron.isa.grph.tp3.tp3InstSet.TP3_ISA_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@TestSkip(testClass = AbstractSpaceTest.class, testMethods = {"testMonoReadWrite"})
public class tp3SpaceTest extends AbstractSpaceTest {
    public tp3SpaceTest() {
        super(f("/g/"), () -> {
            BootLoader.loadInstSetProvider(GRPH_ISA_TID);
            BootLoader.loadInstSetProvider(TP3_ISA_TID);
            return tp3Space.of(rec(
                    PATTERN, uri("/g/#"),
                    REWRITE, rel(uri("/g/+/"), uri("")),
                    NATIVE, rec(uri(LOAD), uri(MODERN.name().toLowerCase()))), f("/sys/space/tp3")); // GRATEFUL.name().toLowerCase()
        });


    }

    @Test
    @Disabled
    public void testProfiling() {
        BootLoader.TYPE_CHECK = false;
        mParser.eval("*/g/V/+.out().>|.out().>|.out().>|.out().>|.out().count()").stream().forEach(v -> LOG.error("%s", v));
        BootLoader.TYPE_CHECK = true;
    }

    @ParameterizedTest
    @CsvSource(value = {
            "*/g/S.count()                                                                  % 1",
            "*/g/S/pattern                                                                  % /m/grph/inst/schema/modern/#",
            "*(*/g/S/pattern).count()                                                       % 4",
            //  "**/g/S/pattern.count()                                                       % 4",
            "*(*/g/S/pattern).vid()                                                         % {/m/grph/inst/schema/modern/person,/m/grph/inst/schema/modern/software,/m/grph/inst/schema/modern/created,/m/grph/inst/schema/modern/knows}",
    }, delimiter = '%')
    public void testSchemaTraversal(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "*/g/V/#../name                                                                 % {\"marko\",\"josh\",\"peter\",\"lop\",\"vadas\",\"ripple\"}",
            "*/g/V/+../OUT/+/IN/name                                                        % {\"josh\",str{3}::\"lop\",\"vadas\",\"ripple\"}",
            "*/g/V/+.count()                                                                % 6",
            "*/g/V/+.outE().count()                                                         % 6",
            "*/g/V/1../OUT/+/IN.count()                                                     % 3",
            "*/g/V/1../OUT/+/IN/OUT/+/IN.count()                                            % 2",
            "*/g/V/1../OUT/+/IN/OUT/+/IN/OUT/+/IN.count()                                   % 0",
            "*/g/V/#.count()                                                                % 6",
            "*/g/E/+.count()                                                                % 6",
            "*/g/S/+.count()                                                                % 2",
            "*/g/V/1.count()                                                                % 1",
            "*/g/E/#.count()                                                                % 6",
            "*/g/E/1.count()                                                                % 0",
            "*/g/V/+../OUT/+/+.count()                                                      % 6",
            "/g/V/1 -> noobj; /g.-<[mult(V/+).*(_).count(),mult(E/+).*(_).count()]          % [5,3]",
            "*/g/V/1.update[name=>'dr.marko']                                               % person::[name=>'dr.marko',age=>29]",
            "*/g/V/1.update[name=>123]                                                      % <ERROR>",
            "*/g/V/1.update[name=>123]                                                      % <ERROR>"
    }, delimiter = '%')
    public void testIdTraversals(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }
}
