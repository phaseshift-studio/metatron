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

package studio.phaseshift.metatron.lang.core.m.type;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.MetatronTest;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class UriTest extends MetatronTest {

    @Override
    @ParameterizedTest
    @CsvSource(value = {
            "bool::abc/def                                                | <ERROR>",
            "bytes::<abc/def>                                             | <ERROR>",
            "int::<abc/def>                                               | <ERROR>",
            "real::<abc/def>                                              | <ERROR>",
            "str::<abc/def>                                               | <ERROR>",
            "lst::<abc/def>                                               | <ERROR>",
            "lst::<abc/def>                                               | <ERROR>",
            "inst::<abc/def>                                              | <ERROR>",
            //  "code::<abc/def>                                            | <ERROR>",
            "uri::<http://webpage.com>                                    | <http://webpage.com>",
            "uri::<http://webpage.com>.type()                             | start(uri::T[])",
            "<http://webpage.com>.type()                                  | start(uri::T[])",
            "'http://webpage.com'.type()                                  | start(str::T[])",
            //"a/b.plus(c/d)                                                | {a/b,c/d}",
            "a/b.plus(noobj)                                              | a/b",
            "a/b.mult(c/d)                                                | a/b/c/d",
            "a/b.mult(noobj)                                              | noobj",
            "a.mult(<../b>)                                               | b",
            "a.mult(<../b/c>)                                             | b/c",
            "a.mult(<../../b>)                                            | <../b>"
    }, delimiter = '|')
    public void testCode(final String code, final String expected) {
        super.testCode(code, expected);
    }
    
    @ParameterizedTest
    @CsvSource(value = {
            "a.pow(0)                         % <.>",
            "/a.pow(1)                        % /a",
            "/a/.pow(1)                       % /a/",
            "/a/.pow(2)                       % /a/a/",
            "a.pow(2)                         % a/a",
            "a.pow(3)                         % a/a/a",
            "a/b.pow(2)                       % a/b/a/b",
            "a/b.pow(3)                       % a/b/a/b/a/b",
            "a/b.pow(3)                       % a/b/a/b/a/b",
            "a/b/c.pow(2)                     % a/b/c/a/b/c",
            "a/b/c.pow(3)                     % a/b/c/a/b/c/a/b/c",
            "a/b/c/.pow(3)                    % a/b/c/a/b/c/a/b/c/",
            "/a/b/c/.pow(3)                   % /a/b/c/a/b/c/a/b/c/",
            "a/b/c/d.pow(2)                   % a/b/c/d/a/b/c/d",
            "a/b/c/d.pow(3)                   % a/b/c/d/a/b/c/d/a/b/c/d",
            "<a/b/../c/d>.pow(3)              % a/c/d/a/c/d/a/c/d",
    }, delimiter = '%')
    public void testMath(final String code, final String expected) {
        super.testCode(code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "<a://b.com:123/c/d>                                 % <a://b.com:123/c/d>",
            "<a://b.com:123/c/d>.scheme()                        % a",
            "<a://b.com:123/c/d>.scheme(abc)                     % <abc://b.com:123/c/d>",
            "<a://b.com:123/c/d>.port()                          % 123",
            "<a://b.com:123/c/d>.port(666)                       % <a://b.com:666/c/d>",
            "<a://b.com:123/c/d>.host()                          % <b.com>",
            "<a://b.com:123/c/d>.host(<abc.org>)                 % <a://abc.org:123/c/d>",
    }, delimiter = '%')
    public void testComponents(final String code, final String expected) {
        super.testCode(code, expected);
    }
    
}
