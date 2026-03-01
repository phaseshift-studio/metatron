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

package studio.phaseshift.metatron.isa.m.type;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.AbstractObjTest;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class FailTest extends AbstractObjTest {

    @ParameterizedTest
    @CsvSource(value = {
            "fail::[a][b][c][d].catch()                                                                      % noobj",
            "fail::[a][b][c][d].catch(_)                                                                     % fail::[a][b][c][d]",
            "fail::[a][b][c][d].catch(_).cause()                                                             % fail::[a][b][c]", // a caught fail is no longer lifted
            "fail::[a][b][c][d].catch(34)                                                                    % 34",
            "fail::[a][b][c][d].catch(_).map?int<=#{?}(34)                                                   % 34",
            "fail::[a][b][c][d].catch(_).map(34)                                                             % 34",
            "{fail::[a],fail::[b]}.catch(34)                                                                 % {2}34",
            "{fail::[a],fail::[a]}.catch(34)                                                                 % {2}34",
            "{fail::[a],fail::[a]}.dedup().catch(34)                                                         % 34",
            // "fail::[a][b][c][d].catch(-<[_,_]>-).map?int<=#(34)                                              % {2}34",
            "fail::[a][b][c][d].catch(cause())                                                               % fail::[a][b][c]",
            "fail::[a][b][c][d].catch(cause().cause())                                                       % fail::[a][b]",
            "fail::[a][b][c][d].catch(cause().cause().cause())                                               % fail::[a]",
            "fail::[a][b][c][d].catch(cause().cause().cause().cause())                                       % noobj",
            "fail::[a][b][c][d].cause()                                                                      % fail::[a][b][c][d]", // need to catch it to operate on it
            "fail::[a][b][c][d].catch(cause()).cause()                                                       % fail::[a][b]", // a caught fail is no longer lifted
            "fail::[a][b][c][d].catch(cause().cause()).cause()                                               % fail::[a]",
            "fail::[a][b][c][d].catch(cause().cause().cause()).cause()                                       % noobj",
            //   "fail::[a][b][c][d].catch(fail::[e])                                                             % fail::[a][b][c][d][e]" // TODO: need a way to denote a caught fail in mtron
    }, delimiter = '%')
    public void testCause(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

}
