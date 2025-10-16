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
 */

package studio.phaseshift.metatron.lang.obj;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
/*
 "/t -> [a,[b,[c,d],e],f]                               % */

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.MetatronTest;


public class LstTest extends MetatronTest {

    @Override
    @ParameterizedTest
    @CsvSource(value = {
            "[a,[b,[c,d],e],f]                                                                       % [a,[b,[c,d],e],f]",
            "[a,[b,[c,d],e],f].get(<0>)                                                              % a",
            "[a,[b,[c,d],e],f].get(<1/0>)                                                            % b",
            "[a,[b,[c,d],e],f].get(<1/1/0>)                                                          % c",
            "[a,[b,[c,d],e],f].get(<1/1/1>)                                                          % d",
            "[a,[b,[c,d],e],f].get(<1/1/+>)                                                          % {c,d}",
            "[a,[b,[c,d],e],f].get(<1/+/+>)                                                          % {c,d}",
            "[a,[b,[c,d],[e,f]],g].get(<1/+/+>)                                                      % {c,d,e,f}",
            "[a,[b,[c,d],[e,[f,g]]],h].get(<1/+/+>)                                                  % {c,d,e,[f,g]}",
            "[a,[b,[c,d],e],f].get(<1/+>)                                                            % {b,[c,d],e}",
            "[a,[b,[c,d],e],f].get(<1/+>)                                                            % {b,[c,d],e}",
            "[a,[b,[c,d],e],f].get(<#>)                                                              % {a,[b,[c,d],e],f}" // TODO: should this be unrolled?

    }, delimiter = '%')
    public void testCode(final String code, final String expected) {
        super.testCode(code, expected);
    }


}
