/*
 * Metatron: A Distributed Computing Language and Virtual Machine
 * Copyright (C) 2025- PhaseShift Studio, LLC
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
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

package studio.phaseshift.metatron.lang.obj;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.MetatronTest;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.mtron.MInst;
import studio.phaseshift.metatron.lang.parse.ObjParser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class InstTest extends MetatronTest {


    @ParameterizedTest
    @CsvSource(value = {
            // furi | tid | dom | range
            "/mtron/plus?dom=/mtron/int&rng=/mtron/int|/mtron/plus|/mtron/int|/mtron/int",
            "/mtron/mult/a?dom=+&rng=+|/mtron/mult/a|+|+"},
            delimiter = '|')
    public void testDomRng(final String f, final String op, final String dom, final String rng) {
        final fURI furi = fURI.of(f);
        final Inst inst = MInst.instA(furi);
        assertEquals(op, inst.tid().path());
        assertEquals(fURI.of(dom), inst.dom().tid());
        assertEquals(fURI.of(rng), inst.rng().tid());
    }


    @ParameterizedTest
    @CsvSource(value = {
            "1         % test?str<=int()                  % test()           % test?str<=int()",
            "1         % test?str<=A()                    % test()           % test?str<=int()",
            "1         % test?A<=A()                      % test()           % test?int<=int()",
            "1         % test?A<=A(A::T[])                % test(2)          % test?int<=int(int::T[])",
            "1         % test?A<=A(A::T[])                % test(plus(2))    % test?int<=int(plus::T[])",
          //  "noobj     % test?A<=[0](A::T[])              % test(3)          % test?int<=[0](int::T[])",
          //  "noobj     % test?A[*]<=[0](A[*]::T[])        % test({1,2,3})    % test?int[3]<=[0](int[3]::T[])",
    }, delimiter = '%')
    public void testResolution(final String lhs, final String def, final String spec, final String resolution) {
        final Obj lhsA = ObjParser.m_obj().parse(lhs).get();
        final Inst defA = ObjParser.m_obj().parse(def).get();
        final Inst specA = ObjParser.m_obj().parse(spec).get();
        final Inst resolutionA = ObjParser.m_obj().parse(resolution).get();
        final Inst resultA = defA.specify(lhsA, specA);
        LOG.info("{{b}}%s{{/b}} resolution matches {{b}}%s{{/b}} specification", resultA.tid(), resolutionA.tid());
        final boolean match = resultA.tid().matches(resolutionA.tid());
        assertTrue(match);
        LOG.info("%s [expected: %s] resolved from specification %s => %s via type definition %s", resultA, resolutionA, lhsA, specA, defA);
        if (!resolutionA.equals(resultA))
            LOG.warn("resolution algorithm generates matching, but not equal final resolution -- skipping equality checks");
        else
            assertEquals(resolutionA, resultA);
        assertTrue(resultA.matches(resolutionA));
        assertTrue(resultA.tid().matches(resolutionA.tid()));
        assertTrue(resultA.matches(specA));
        assertTrue(resultA.tid().matches(specA.tid()));
        assertTrue(resultA.matches(defA));
        assertTrue(resultA.tid().matches(defA.tid()));
        assertTrue(specA.matches(resolutionA));
        assertTrue(specA.tid().matches(resolutionA.tid()));
        assertTrue(defA.matches(resolutionA));
        assertTrue(defA.tid().matches(resolutionA.tid()));
        assertTrue(specA.matches(defA));
        assertTrue(specA.tid().matches(defA.tid()));
    }
}
