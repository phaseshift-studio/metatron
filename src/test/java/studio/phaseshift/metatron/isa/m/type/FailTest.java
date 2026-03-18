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
import studio.phaseshift.metatron.TestData;
import studio.phaseshift.metatron.isa.AbstractObjTest;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class FailTest extends AbstractObjTest {

    @ParameterizedTest
    @CsvSource(value = {
            "fail::[a][b][c][d].catch()                                                                      % noobj",
            "fail::[a][b][c][d].catch(_)                                                                     % fail::[a][b][c][d].catch(_)",
            "fail::[a][b][c][d].catch(_).cause()                                                             % fail::[a][b][c].catch(_)", // a caught fail is no longer lifted
            "fail::[a][b][c][d].catch(34)                                                                    % 34",
            "fail::[a][b][c][d].catch(_).map?int<=#{?}(34)                                                   % 34",
            "fail::[a][b][c][d].catch(_).map(34)                                                             % 34",
            "{fail::[a],fail::[b]}.catch(34)                                                                 % {2}34",
            "{fail::[a],fail::[a]}.catch(34)                                                                 % {2}34",
            "{fail::[a],fail::[a]}.dedup().catch(34)                                                         % 34",
            // "fail::[a][b][c][d].catch(-<[_,_]>-).map?int<=#(34)                                              % {2}34",
            "fail::[a][b][c][d].catch(cause())                                                               % fail::[a][b][c].catch(_)",
            "fail::[a][b][c][d].catch(cause().cause())                                                       % fail::[a][b].catch(_)",
            "fail::[a][b][c][d].catch(cause().cause().cause())                                               % fail::[a].catch(_)",
            "fail::[a][b][c][d].catch(cause().cause().cause().cause())                                       % noobj",
            "fail::[a][b][c][d].cause().catch(_)                                                             % fail::[a][b][c][d].catch(_)", // need to catch it to operate on it
            "fail::[a][b][c][d].catch(_).cause()                                                             % fail::[a][b][c].catch(_)", // need to catch it to operate on it
            "fail::[a][b][c][d].catch(cause())                                                               % fail::[a][b][c].catch(_)", // need to catch it to operate on it
            "fail::[a][b][c][d].catch(cause()).cause()                                                       % fail::[a][b].catch(_)", // a caught fail is no longer lifted
            "fail::[a][b][c][d].catch(cause().cause()).cause()                                               % fail::[a].catch(_)",
            "fail::[a][b][c][d].catch(cause().cause().cause()).cause()                                       % noobj",
            //   "fail::[a][b][c][d].catch(fail::[e])                                                             % fail::[a][b][c][d][e]" // TODO: need a way to denote a caught fail in mtron
    }, delimiter = '%')
    public void testCause(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @TestData(value = {"1.plus(b)"}, oneTime = true)
    @CsvSource(value = {
            "*/sys/fail/+.count().to(xyzabc).gt(0)                                                                % true",
            "*/sys/fail/+.count()                                                                                 % *xyzabc",
            "*/sys/fail/+.catch(_).count().eq(*xyzabc)                                                            % true",
            "*/sys/fail/+.catch(_).count()                                                                        % 0",
    }, delimiter = '%')
    public void testFailStackAndCatch(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

}
