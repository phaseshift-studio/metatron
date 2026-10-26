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

package studio.phaseshift.metatron.isa.mach.network;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.TestData;
import studio.phaseshift.metatron.isa.mach.type.Machine;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;

/**
 * move(uri) with an authority is a scatter: the move-extractor packages the tail as the move's blocked-code
 * argument, the move ships {@code start(lhs).move(clipped).<tail>} through the peer's eval, and the reply scatters.
 * The loop peer is the identity client, so a shipped program evaluates against the local machine.
 */
public class MoveRemoteTest extends AbstractMetatronTest {

    @BeforeEach
    void connect() {
        Machine.current().network().connect(f("loop://self"));
    }

    @AfterEach
    void disconnect() {
        Machine.current().network().disconnect(f("loop://self"));
    }

    @TestData(value = {
            "push(a)",
            "push(<../b>)",
            "move(</.>)",
            "push(c)"}, oneTime = true)
    @ParameterizedTest
    @CsvSource(value = {
            "5.move(<loop://self/a>).plus(1).mult(2)                                                                                  % start(5).cut(<loop://self/a>,plus(1).mult(2))                                                                                  % 12",
            "5.move(<loop://self/a>).mult(2).plus(1)                                                                                  % start(5).cut(<loop://self/a>,mult(2).plus(1))                                                                                  % 11",
            "{1,2,3}.move(<loop://self/a>).mult(2).plus(1).sum()                                                                      % start({1,2,3}).cut(<loop://self/a>,mult(2).plus(1).sum())                                                                      % 15",
            "{1,2,3}.move(<loop://self/a>).mult(2).plus(1).sum().move(<loop://self/b>).plus(7).plus(1)                                % start({1,2,3}).cut(<loop://self/a>,mult(2).plus(1).sum().move(<loop://self/b>).plus(7).plus(1))                                % 23",
            "{1,2,3}.move(<loop://self/a>).branch(move(<loop://self/b>).mult(2),plus(7).move(<loop://self/c>).plus(1)).plus(8).prod() % start({1,2,3}).cut(<loop://self/a>,branch(move(<loop://self/b>).mult(2),plus(7).move(<loop://self/c>).plus(1)).plus(8).prod()) % 9767520",
    }, delimiter = '%')
    void testMoveRewrite(final String code, final String expected, final String expectedResult) {
        checkCodeRewrite(LOG, code, expected, expectedResult, false);
    }
}
