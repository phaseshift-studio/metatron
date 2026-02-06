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

package studio.phaseshift.metatron.isa.grph.space;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.isa.SpaceTest;
import studio.phaseshift.metatron.isa.grph.grphInstSet;
import studio.phaseshift.metatron.isa.grph.space.tp3.tp3Space;
import studio.phaseshift.metatron.mTest;

import static studio.phaseshift.metatron.Tokens.PATTERN;
import static studio.phaseshift.metatron.Tokens.REWRITE;
import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.isa.grph.space.tp3.tp3Space.NATIVE_LOAD;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@Disabled
public class tp3SpaceTest extends SpaceTest {
    public tp3SpaceTest() {
        super(f("/g/"), () -> tp3Space.of(rec(
                PATTERN, uri("/g/#"),
                REWRITE, rel(uri("/g/+/"), uri("")),
                NATIVE_LOAD, uri("modern")), f("/sys/space/tp3")));
        grphInstSet.create();
    }

    @Override
    public void testMonoReadWrite(final String writeExpression, final String readExpression, final String expectedExpression) {
        LOG.warn("ignoring testMonoReadWrite: %s => %s => %s", writeExpression, readExpression, expectedExpression);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "*/g/V/#                                                                        % 6",
            "*/g/V/+.count()                                                                % 6",
            "*/g/V/#.count()                                                                % 6",
            "*/g/E/+.count()                                                                % 6",
            "*/g/S/+.count()                                                                % 1",
            "*/g/V/1.count()                                                                % 1",
            "*/g/E/1.count()                                                                % 1",
            "*/g/V/+/OUT/+.count()                                                          % 6",
            "a=>b=>c.>>                                                                     % b=>c",
            "a=>b=>c.<<                                                                     % a",
            "a=>b=>c>-                                                                      % {a,b=>c}",
            "a=>b=>c>-.>-                                                                   % {a,b,c}",
            "a=>b=>c.count()                                                                % 1",
            "1=>2=>c.>>                                                                     % 2=>c",
            /// ////////////////////////////////////////////////////////////////////////////////////////
            "(a=>(b=>c)).>>                                                                     % b=>c",
    }, delimiter = '%')
    public void testCoreGraphTraversals(final String code, final String expected) {
        mTest.testCode(LOG, code, expected);
    }
}
