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

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class UriTest extends AbstractMetatronTest {

    @ParameterizedTest
    @CsvSource(value = {
            "bool::abc/def                                                | <ERROR>",
            "/m/bool::abc/def                                             | <ERROR>",
            "bytes::<abc/def>                                             | <ERROR>",
            "/m/bytes::<abc/def>                                          | <ERROR>",
            "int::<abc/def>                                               | <ERROR>",
            "/m/int::<abc/def>                                            | <ERROR>",
            "real::<abc/def>                                              | <ERROR>",
            "/m/real::<abc/def>                                           | <ERROR>",
            "str::<abc/def>                                               | <ERROR>",
            "/m/str::<abc/def>                                            | <ERROR>",
            "lst::<abc/def>                                               | <ERROR>",
            "/m/lst::<abc/def>                                            | <ERROR>",
            "lst::<abc/def>                                               | <ERROR>",
            "/m/lst::<abc/def>                                            | <ERROR>",
            "inst::<abc/def>                                              | <ERROR>",
            "/m/inst::<abc/def>                                           | <ERROR>",
            //  "code::<abc/def>                                          | <ERROR>",
            "uri::<http://webpage.com>                                    | <http://webpage.com>",
            "uri::<http://webpage.com>.type()                             | start(uri::T[])",
            "<http://webpage.com>.type()                                  | start(uri::T[])",
            "\"http://webpage.com\".type()                                | start(str::T[])",
            //"a/b.plus(c/d)                                              | {a/b,c/d}",
            "a/b.plus(noobj)                                              | a/b",
            "a/b.mult(c/d)                                                | a/b/c/d",
            "a/b.mult(noobj)                                              | noobj",
            "a.mult(<../b>)                                               | b",
            "a.mult(<../b/c>)                                             | b/c",
            "a.mult(<../../b>)                                            | <../b>"
    }, delimiter = '|')
    public void testCode(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
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
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "a/b/c.-</                        % [a,b,c]",
            "ab/c.-</                         % [ab,c]",
            "abc.-</                          % [abc]",
            "-</abc                           % <ERROR>",
            "<http://www.com/a/b/c>.-</       % [http:,<>,<www.com>,a,b,c]",
            "<////>.-</                       % [,]",
            "<////a>.-</                      % [<>,<>,<>,<>,<>,a]",
    }, delimiter = '%')
    public void testSplit(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @TestData({
            "test -> <http://www.marko.com:90/a/b/c?w=abc&x=1&y=2&z=!*test>",
            "b -> 42"})
    @CsvSource(value = {
            "*test.>>scheme                   % http",
            "*test.>>authority                % <www.marko.com:90>",
            "*test>>host                      % <www.marko.com>",
            "*test.>>port                     % 90",
            "*test.>>path                     % </a/b/c>",
            "*test>>q                         % [w=><abc>,x=>1,y=>2,z=>!*test]",
            "*test.>>q>>w                     % <abc>",
            "*test.>>q>>x                     % 1",
            "*test>>q>>y                      % 2",
            "*test.>>q>>z                     % *test",
            "*test.>>path>>1.*(_)             % 42"
    }, delimiter = '%')
    public void testGet(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "/a/b/c.reverse()                 % c/b/a/",
            "aaa/bbb/ccc.reverse()            % ccc/bbb/aaa",
            "<http://m.com/a/b/c>.reverse()   % <http://m.com/c/b/a>",
            "a.reverse()                      % a",
            "a/b.reverse()                    % b/a",
            "/a.reverse()                     % a/",
    }, delimiter = '%')
    public void testReverse(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }


    @ParameterizedTest
    @CsvSource(value = {
            "ab/cd.has('ab.*')                                                             % ab/cd",
            "ab/cd.has('bb')                                                               % noobj",
            "{abc/d,aaa}.has('a\\.')                                                       %  noobj",
            "{abc3d,aaa}.has('a.*')                                                        % {abc3d,aaa}",
            "{abc3d,aaa}.has('a(b)?(a|c).?')                                               % {abc3d,aaa}",
            "{abc3d,aaa}.has('b.*')                                                        % {abc3d}",
            "{abc3d,aaa}.has('c.*')                                                        % {abc3d}",
            "{abc3d,aaa}.has('d.*')                                                        % abc3d",
            "{abc3d,aaa}.has('d.?')                                                        % abc3d",
            "{abc3d,aaa}.has('e.*')                                                        % noobj"
            // "{'abc3d','aaa'}.where(not(has('e.')))                                          % {\"abc3d\",\"aaa\"}",
            // "{'abc3d','aaa'}.where(has('e.'))                                               % noobj",
    }, delimiter = '%')
    public void testHasInst(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }


    @ParameterizedTest
    @CsvSource(value = {
            "<a://b.com:123/c/d?x=1&y=2>                                 % <a://b.com:123/c/d?x=1&y=2>",
            "<a://b.com:123/c/d?x=1&y=2>.as(rec::T)../scheme                        % a",
            "<a://b.com:123/c/d?x=1&y=2>.scheme(abc)                     % <abc://b.com:123/c/d?x=1&y=2>",
            "<a://b.com:123/c/d?x=1&y=2>.as(rec::T)../port                          % 123",
            "<a://b.com:123/c/d?x=1&y=2>.port(666)                       % <a://b.com:666/c/d?x=1&y=2>",
            "<a://b.com:123/c/d?x=1&y=2>.as(rec::T)../host                         % <b.com>",
            "<a://b.com:123/c/d?x=1&y=2>.host(<abc.org>)                 % <a://abc.org:123/c/d?x=1&y=2>",
            "<a://b.com:123/c/d?x=1&y=2>.as(rec::T)../q                         % [x=><1>,y=><2>]",
            // "<a://b.com:123/c/d?x=1&y=2>.query(x=3)                 % <a://b.com:123/c/d?x=3&y=2>",
    }, delimiter = '%')
    public void testAsRec(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "<1>                                                        % <1>",
            "<1>.as(int::T)                                             % int::1",
            "<3>.as(int::T).plus(<6>.as(int::T))                        % int::9",
            "<3a>.as(int::T)                                            % <ERROR>",
    }, delimiter = '%')
    public void testAsInt(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "a/b/c.count()                                              % 1",
            "a.count()                                                  % 1",
            "/a/b.count()                                               % 1",
          //  "<http://example.com/a/b/c>.count()                         % 6",
    }, delimiter = '%')
    public void testCount(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "a/b.eq(a/b)                                                % true",
            "a/b.eq(b/a)                                                % false",
            "/a/b.eq(a/b)                                               % false",
            "<http://a.com>.eq(<http://a.com>)                          % true",
    }, delimiter = '%')
    public void testEquality(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }


}
