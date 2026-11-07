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

package studio.phaseshift.metatron.isa.m.type;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;

/**
 * code::T's update() with names and template parameters ({@link Call.Helper#update}): a pattern element with a
 * relative vid names what it matched, and a change's {@code ${...}} argument is evaluated against what the run holds
 * in that place, reading the names. The plain update rows live with code::T's other operations in mInstSetTest.
 */
public class CodeUpdateTest extends AbstractMetatronTest {

    @ParameterizedTest
    @CsvSource(value = {
            // a template parameter is evaluated against what the run holds in its place: the 5
            "[plus(5).mult(2)]==[update([plus(int::T)=>plus(${+10})])]                                                % [plus(15).mult(2)]",
            // ... and a name reads it from anywhere in the change
            "[plus(5).mult(2)]==[update([plus(int::T@a)=>plus(${*a.plus(10)})])]                                      % [plus(15).mult(2)]",
            // two names combine: plus's monoid law as data
            "[plus(2).plus(3).mult(4)]==[update([plus(int::T@a).plus(int::T@b)=>plus(${*a.plus(*b)})])]               % [plus(5).mult(4)]",
            // a named refinement guards the fold: a -3 is no positive int, so nothing folds
            "[plus(2).plus(3).mult(4)]==[update([plus(int::T[?>0]@a).plus(int::T[?>0]@b)=>plus(${*a.plus(*b)})])]     % [plus(5).mult(4)]",
            "[plus(2).plus(-3).mult(4)]==[update([plus(int::T[?>0]@a).plus(int::T[?>0]@b)=>plus(${*a.plus(*b)})])]    % [plus(2).plus(-3).mult(4)]",
            // a name binds at depth, inside a nested code argument
            "[mult(plus(mult(3))).plus(1)]==[update([mult(#(mult(int::T@k)))=>mult(plus(mult(${*k.mult(2)})))])]      % [mult(plus(mult(6))).plus(1)]",
            // a nested rec's changes read the names its run bound
            "[plus(2).mult(3)]==[update([plus(int::T@a).mult(int::T)=>[mult(int::T)=>mult(${*a})]])]                  % [plus(2).mult(2)]",
            // everything outside a template parameter is literal
            "[plus(1).mult(2)]==[update([plus(int::T@a)=>plus(+5)])]                                                  % [plus(plus(5)).mult(2)]",
            // a parameter that cannot evaluate fails the update rather than staying in the code
            "[plus(5).mult(2)]==[update([plus(int::T)=>plus(${'x'.as(int::T)})])]>-                                   % <ERROR>",
    }, delimiter = '%')
    public void testNamesAndTemplates(final String code, final String expected) throws Exception {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }
}
