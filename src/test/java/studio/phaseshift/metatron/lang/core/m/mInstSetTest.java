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

package studio.phaseshift.metatron.lang.core.m;

/*
@author Marko A. Rodriguez (http://markorodriguez.com)
*/

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.furi.Q;
import studio.phaseshift.metatron.furi.q.DocQTest;
import studio.phaseshift.metatron.lang.InstSetTest;
import studio.phaseshift.metatron.lang.core.m.inst.mInstSet;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.furi.fURI.f;

public class mInstSetTest extends InstSetTest {


    public mInstSetTest() {
        super(mInstSet.create());
    }

    @Test
    public void testDocs() {
        assertTrue(mInstSet.create().qs().elements().anyMatch(q -> q.<Q>as().pattern().equals(f("doc"))));
        new DocQTest().analyzeDocs(mInstSet.create());


    }

    @ParameterizedTest
    @CsvSource(value = {
            "a=>b=>c.>>                                                                     % b=>c",
            "a=>b=>c.<<                                                                     % a",
            "a=>b=>c>-                                                                      % {a,b=>c}",
            "a=>b=>c>-.>-                                                                   % {a,b,c}",
            "a=>b=>c.count()                                                                % 1",
            "1=>2=>c.>>                                                                     % 2=>c"
    }, delimiter = '%')
    public void testRelCode(final String code, final String expected) {
        super.testCode(code, expected);
    }


    @ParameterizedTest
    @CsvSource(value = {
            "'123'.regex('\\d')                                                             % ['1','2','3']",
            "'abcd'.regex('[a-z]{2}')                                                       % ['ab','cd']",
            "'ab3cd'.regex('([a-z]+)(\\d?)([a-z]?)')                                        % ['ab3c','d']",
            "'ab3cd'.regex('(?<a>[a-z]+)(?<b>\\d?)(?<c>[a-z]?)')                            % ['ab3c','d']",
            "'ab3cd'.regex('\\d*')                                                          % ['','','3','','','']",
            "'ab3cd'.regex('\\d+')                                                          % ['3']",
            "'ab3cd'.regex('\\d{2}')                                                        % [,]",
    }, delimiter = '%')
    public void testStrCode(final String code, final String expected) {
        super.testCode(code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "{1,2,3,4}.take(0)                                                              % {,}",
            "{1,2,3,4}.take(1)                                                              % {1}",
            "{1,2,3,4}.take(2)                                                              % {1,2}",
            "{1,2,3,4}.take(3)                                                              % {1,2,3}",
            "{1,2,3,4}.take(4)                                                              % {1,2,3,4}",
            "{1,2,3,4}.take(5)                                                              % {1,2,3,4}",
            "{int{4}::1,2,3,4}.take(5)                                                      % {int{4}::1,2}",
            /// /////////////////////////////////////////////////////////////////////////////////////////////
            "{1,2,3,4}.skip(0)                                                              % {1,2,3,4}",
            "{1,2,3,4}.skip(1)                                                              % {2,3,4}",
            "{1,2,3,4}.skip(2)                                                              % {3,4}",
            "{1,2,3,4}.skip(3)                                                              % {4}",
            "{1,2,3,4}.skip(4)                                                              % {,}",
            "{1,2,3,4}.skip(5)                                                              % {,}",
            "{int{4}::1,2,3,4}.skip(5)                                                      % {3,4}",
            "{int{4}::1,2,3,4,5}.skip(5)                                                    % {3,4,5}",
            /// ////////////////////////////////////////////////////////////////////////////////////////////
            "{1,2,3,4,5}.skip(2).take(2)                                                    % {3,4}",
            "{int{2}::1,2,3,4,5}.skip(2).take(2)                                            % {2,3}",
            "{int{3}::1,2,3,4,5}.skip(2).take(2).count()                                    % 2",
            /// ////////////////////////////////////////////////////////////////////////////////////////////
            "{1,1}.inst?int<=int{2}(){ sum() }                                              % 2",
            "{1,1,1,1}.inst?int<=int{2}(){ sum() }                                          % int{2}::2",
            "{1,1,2,3}.inst?int<=int{2}(){ sum() }                                          % {2,5}",
            "{1,1,2,2,3,5}.inst?int<=int{2}(){ sum() }                                      % {2,4,8}",
            "{1,1,2,2,3,5,7}.inst?int<=int{2}(){ sum() }.catch(10)                          % {2,4,8,10}",
            "{1,1,2,2,3,3,4,4}.inst?int<=int{2}(){ sum() }.catch(10)                        % {2,4,6,8}",
    }, delimiter = '%')
    public void testSkipLimitCode(final String code, final String expected) {
        super.testCode(code, expected);
    }


    @ParameterizedTest
    @CsvSource(value = {
            // "1.plus?int{?}<=int(int{-1}::1)                                         % noobj",
            "1.plus(_)                                                              % 2",
            "1.plus(2)                                                              % 3",
            "1.plus(1.plus(1))                                                      % 3",
            "{1,2,3}._                                                              % {1,2,3}",
            "{1,2,3}.plus(2)                                                        % {3,4,5}",
            "{int{10}::1}.plus(_)                                                   % int{10}::2",
            "int{10}::2.plus(_)                                                     % int{10}::4",
            "{1,2,3}>-._.plus(_)                                                    % {2,4,6}",
            "{1,2,3}>-.plus(_)                                                      % {2,4,6}",
            "{1,2,3}._.plus(_)                                                      % {2,4,6}",
            "{1,2,3}.plus(2.plus(3))                                                % {6,7,8}",
            "{1,2,3}.plus(1.plus(3.plus(1)))                                        % {6,7,8}",
            "{1,2,3}.plus(_)                                                        % {2,4,6}",
            "{1,2,3}.plus(id())                                                     % {2,4,6}",
            "{1,2,3}.plus(mult(1))                                                  % {2,4,6}",
            "{1,2,3}.plus(plus(1))                                                  % {3,5,7}",
            "{1,2,3}.plus(plus(map(1)))                                             % {3,5,7}",
            "{1,2,3}.plus(plus(_))                                                  % {3,6,9}",
            "{1,2,3}.plus(plus(mult(1)))                                            % {3,6,9}",
            // MERGE ///
            "{1,2,3}>-                                                              % {1,2,3}",
            "{1,1,2,2,2,3}>-                                                        % {1,1,2,2,2,3}",
            "{1,2,3}>-[,]                                                           % [1,2,3]",
            "[1=>2,2=>3,3=>4]>-[=>]                                                 % [1=>2,2=>3,3=>4]",
            "[1=>2,2=>3,3=>4]>-.>-[=>]                                              % [1=>2,2=>3,3=>4]",
            "{1,2,3}>-noobj                                                         % {1,2,3}",
            "{1,2,3}>-[noobj]                                                       % [1,2,3,noobj]",
            //"[1=>2,2=>3,3=>4]>-                                                     % {1=>2,2=>3,3=>4}",
            "[1=>2,2=>3,3=>4].type()                                                % start(rec::T)",
            "[1=>2,2=>3,3=>4]>-.type()                                              % start(rel{3}::T)",
            "[1=>2,2=>3,3=>4]>-.>-[noobj=>noobj]                                    % [1=>2,2=>3,3=>4]",
            "[z=>c,2=>3,a=>f]>-.>-[noobj=>noobj]                                    % [z=>c,2=>3,a=>f]",
            "{1,2}>-[3,4]                                                           % [1,2,3,4]",
            "{1,2,3,4}>-{,}                                                         % {1,2,3,4}",
            "{1,2,2,2,3,3,4}>-{,}                                                   % {1,2,2,2,3,3,4}",
            "{3,4}>-{1,2}                                                           % {1,2,3,4}",
            "{2,3,4}>-{1,2,2}                                                       % {1,2,2,2,3,4}",
            "{1,2,3}>-1                                                             % {1,1,2,3}",
            "[a=>1,b=>2]>-.>-[=>]                                                   % [a=>1,b=>2]",
            "[a=>1]>-.>-[b=>2]                                                      % [a=>1,b=>2]",
            "[b=>2]>-.>-[a=>1]                                                      % [b=>2,a=>1]",
            // SPLIT //
            "{1,2,3}-<{,}                                                           % noobj",
            "{1,2,3}-<?lst<=int{*}([,])                                             % [,]",
            "{1,2,3}-<?lst{*}<=int([_])                                             % {[1],[2],[3]}",
            "{1,2,3}-<?lst<=int{*}([_])                                             % [{1,2,3}]",
            //"{1,2,3}-<?rec<=int{3}([=>])                                            % [=>]",
            //"{1,2,3}-<?rec<=int{*}([=>])                                            % [=>]",
            "{1,2,3}-<noobj                                                         % noobj",
            "{1,2,3}-<[noobj]                                                       % [noobj]",
            "{1,2,3}-<[noobj=>noobj]                                                % [=>]",
            "{1,2,3}.map?int<=real(1)                                               % <ERROR>",
            "{1,2,3}.inst?int<=int{3}(){1}                                          % 1",
            "{1,2,3}.inst{3}?int<=int{3}(){1}                                       % int{3}::1",
            "{int{2}::1,int{2}::2,int{2}::3}.inst{3}?int<=int{3}(){1}               % int{6}::1",
            "{1,2,3}.map?int<=int(1)                                                % int{3}::1",
            "{1,2,3}-<1                                                             % 1",
            "{1,2,3}-<1                                                             % 1",
            "{1,2,3}-<[is(gt(1))=>_, is(gt(2))=>_]                                  % [is(gt(1))=>{2,3},is(gt(2))=>3]",
            "{1,2,3}-<{is(gt(1)), is(gt(2))}                                        % {2,3,3}",
            "{1,2,3}.split({is(gt(1)), is(gt(2))})                                  % {int{2}::3,2}",
            "{1,2,3}.split({is(gt(1)), is(gt(2)), 3})                               % {2,int{5}::3}",
            "{1,2,3}-<?lst{0,3}<=int([is(gt(1)), is(gt(2))])                        % {[noobj,noobj],[2,noobj],[3,3]}",
            "{1,2,3}-<?lst{0,3}<=int([is(gt(1)), is(gt(2))])>-                      % {2,3,3}",
            "{1,2,3}-<?lst{0,3}<=int([is(gt(1)), is(gt(2))])>-.>-[,]                % [2,int{2}::3]",
            "{1,2,3}.>-{3,3,2}                                                      % {3,3,2,3,2,1}",
            "{1,2,3}.>-{3,3,2}                                                      % {int{1}::1,int{2}::2,int{3}::3}",
            "{1,2,3}>-.-<[_,_,_]                                                    % [{1,2,3},{1,2,3},{1,2,3}]",
            "{1,2,3}>-.-<lst::[_,_,_]                                               % [{1,2,3},{1,2,3},{1,2,3}]",
            "{1,2,3}>-.-<[_,_,_]_/sum()\\_                                          % [18]",
            "{1,2,3}>-.-<[_,_,_]_/sum()\\_>-                                        % 18",
            "{1,2,3}>-.-<[_,_,_]_/sum()\\_>-{,}                                     % 18",
            "{1,2,3}>-.id().-<[_,_,_]                                               % [{1,2,3},{1,2,3},{1,2,3}]",
            "{1,2,3}>-.id().-<lst::[_,_,_]                                          % [{1,2,3},{1,2,3},{1,2,3}]",
            "{1,2,3}>-.id().-<[_,_,_]_/sum()\\_                                     % [18]",
            "{1,2,3}>-.id().-<[_,_,_]_/sum()\\_>-                                   % 18",
            "{1,2,3}>-.id().-<[_,_,_]_/sum()\\_>-{,}                                % 18",
            "{1,2,3}>-.id?A<=A().-<[_,_,_]                                          % {[1,1,1],[2,2,2],[3,3,3]}",
            "[a=>1,b=>2,c=>3]>-.-<|[<<=>>>.is(gt(0))]                               % [a=>1,b=>2,c=>3]>-",
            "[a=>1,b=>2,c=>3]>-.-<|[<<=>>>.is(gt(2))]                               % [c=>3]>-",
            "{1,2,3}.-<?lst<=int([_,_,_])                                           % {[1,1,1],[2,2,2],[3,3,3]}",
            // MULT //
            "{1,2,3}.mult(10)                                                       % {int{1}::10,int{1}::20,int{1}::30}",
            "{int{2}::1,int{3}::2,int{4}::3}.mult(10)                               % {int{2}::10,int{3}::20,int{4}::30}",
            "int{50}::10.mult(10)                                                   % int{50}::100",
            // COUNT/SUM //
            "{1,2,3}.sum().prod()                                                   % 6",
            "{1,2,3}.prod().sum()                                                   % 6",
            //"{1,2,3}.sum().sum()                                                  % 6",
            //"{1,2,3}._.sum()._.sum()._.sum()                                      % 6",
            "{1,2,3,4}.id{5}().count()                                              % 20",
            "{1,2,3,4}.id{3}().count()                                              % 12",
            "{1,2,3,4}.is(gt(5)).count()                                            % 0",
            "{1,2,3,4}.count()                                                      % 4",
            "{1,2,3,4}.count{2}().sum()                                             % 8",
            "{1,2,3,4}.sum{2}()                                                     % int{2}::10",
            "{1,2,3,4}.sum{2}().count()                                             % 2",
            "{1,2,3,4,5,6}.sum?int<=int{2}().count()                                % 3",
            "{1,2,3,4}.sum?int<=int{+}()                                            % 10",
            "{1,2,3,4}.count?int<=int()                                             % int{4}::1",
            "{1,2,3,4}.count?int<=int{2}()                                          % int{2}::2",
            "{1,2,3,4}.count?int<=int{4}()                                          % int::4",
            "{1,2,3,4}.count?int<=int{3,10}()                                       % int::4",
            "{1,2,3,4}.sum?int<=int{1,7}()                                          % 10",
            "{1,2,3,4}.sum{2}?int<=int{1,7}()                                       % int{2}::10",
            "{1,2,3,4}.sum{2}?int<=int{1,7}().sum()                                 % int::20",
            "{1,2,3,4}.sum{2}?int<=int{1,7}().sum()-<[_,_]>-.sum?int<=int{2}()      % int::40",
            "{1,2,3,4}.sum{2}?int<=int{1,7}().sum()-<[_,_].>-                       % int{2}::20",
            "{1,2,3,4}.sum{2}?int<=int{1,7}().sum()-<[_,_].select([_,_])>-          % int{2}::20",
            "{1,2,3,4}.sum{2}?int<=int{1,7}().sum().id{2}()                         % int{2}::20",
            "{1,2,3,4,5,6}.sum?int<=int()                                           % {1,2,3,4,5,6}",
            "{1,2,3,4,5,6}-<[_]>-.sum()                                             % 21",
            "{1,2,3,4,5,6}-<[_]>-.sum{2}()                                          % int{2}::21",
            "{1,int{2}::2,3,4,5,6}-<[_]>-.sum{2}()                                  % int{2}::23",
            "{int{2}::1,int{2}::2,int{2}::3}.sum?int<=int{2}()                      % {2,4,6}",
            "{int{2}::1,int{2}::2,int{2}::3}.sum?int<=int{1,2}()                    % {2,4,6}",
            "{1,2,3,4}-<{count(),count()}                                           % int{2}::4",
            "{1,2,3,4}.is(gt(2)).count()                                            % 2",
            "int{50}::10.mult(10).count()                                           % int{1}::50",
            "int{50}::10.mult(10).sum()                                             % 5000",
            "int{50}::10-<{mult(10),mult(1)}                                        % {int{50}::100,int{50}::10}",
            "int{50}::10-<{mult(10),mult(1)}.count()                                % 100",
            "int{50}::10-<{mult(10),mult(1)}.sum()                                  % 5500",
            "{int{50}::10}-<{mult(10),mult(1)}                                      % {int{50}::100,int{50}::10}",
            "{int{50}::10}-<{mult(10),mult(1)}.count()                              % 100",
            "{int{50}::10}-<{mult(10),mult(1)}.sum()                                % 5500",
            "{int{50}::10,int{10}::5}-<{mult(10),mult(1)}                           % {int{50}::100,int{10}::50,int{50}::10,int{10}::5}",
            "{int{50}::10,int{10}::5}-<{mult(10),mult(1)}.count()                   % 120",
            "{int{50}::10,int{10}::5}-<{mult(10),mult(1)}.sum()                     % 6050",
            "{int{50}::10,int{10}::5}-<{mult(10),mult(1)}.sum{2}().sum()            % 12100",
            "{[1,2],[3,4,5],[6,7,8]}>-[,]                                           % [[1,2],[3,4,5],[6,7,8]]",
            "{[1,2],[3,4,5],[6,7,8]}>-[,]==[>-,>-,>-]                               % [{1,2},{3,4,5},{6,7,8}]",
            "{[1,2],[3,4,5],[6,7,8]}>-[,]==[>-,>-,>-]>-                             % {{1,2},{3,4,5},{6,7,8}}",
            "{[1,2],[3,4,5],[6,7,8]}>-[,]==[>-,>-,>-]>-                             % {1,2,3,4,5,6,7,8}",
            "{[1,2],[3,4,5],[6,7,8]}>-[,]==[>-,>-,>-]>-.count()                     % 8",
            //"*/m/inst/#.count()-<[is(gt(0))=>true,is(eq(0))=>false]>>-              % true",
    }, delimiter = '%')
    public void testSplitMergeCode(final String code, final String expected) {
        super.testCode(code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            //"{1,1.plus?int<=int(1),1.plus?int<=int(2),4}.prod()                   % 24",
            "{1,2,3,4}.prod()                                                       % 24",
            "1.prod()                                                               % 1",
            "int{5}::3.prod()                                                       % 15",
            "{a,b,c}.prod()                                                         % a/b/c",
            "{a/b,c/d,e/f}.prod()                                                   % a/b/c/d/e/f",
            // "{a/b,c/d,e/f}.sum()                                                    % <+/+{3}>",
            "{10,5}-<{mult{3}(10),mult(1)}.sum{2}().sum()                           % 930",
            "{[1,2],[3,4,5],[6,7,8]}.sum()                                          % [1,2,3,4,5,6,7,8]",
            "{[1,2],[3,4,5],[6,7,8]}.sum().>-                                       % {1,2,3,4,5,6,7,8}",
            "{[1,2],[3,4,5],[6,7,8]}.sum().>-.count()                               % 8",
            "{[1],[2],[3]}.sum()                                                    % [1,2,3]",
            "{[1],[2],[3]}.sum{10}()                                                % lst[int,int,int]{10}::[1,2,3]",
            "{[1],[2],[3]}.sum()._/sum()\\_                             % [6]",
            "{[1],[2,3],[1,3]}.sum()._/sum()\\_.>-                      % 10",
            "{[1],[2,3],[1,3]}.sum()._/sum()\\_.merge{3,7}()            % int{3,7}::10",
            "{[1],[2,3],[1,3]}.sum()._/sum{2}()\\_.>-                   % int{2}::10",
            "{[1],[2,3],[1,3]}.sum()._/sum{2}()\\_.>-.sum()             % 20",
            "{[1,2],[3,4,5],[6,7,8]}.sum()._/sum()\\_.>-.sum{2}()       % int{2}::36",
            "{[1,2],[3,4,5],[6,7,8]}.sum()._/sum()\\_.>-.sum{2}().sum() % 72",
            "{[1],[2],[3]}.sum()._/sum()\\_                                         % [6]",
            "{[1],[2,3],[1,3]}.sum()._/sum()\\_.>-                                  % 10",
            "{[1],[2,3],[1,3]}.sum()._/sum()\\_.merge{3,7}()                        % int{3,7}::10",
            "{[1],[2,3],[1,3]}.sum()._/sum{2}()\\_.>-                               % int{2}::10",
            "{[1],[2,3],[1,3]}.sum()._/sum{2}()\\_.>-.sum()                         % 20",
            "{[1,2],[3,4,5],[6,7,8]}.sum()._/sum()\\_.>-.sum{2}()                   % int{2}::36",
            "{[1,2],[3,4,5],[6,7,8]}.sum()._/sum()\\_.>-.sum{2}().sum()             % 72",
            ///
            "{1,2,3,4,5}.reduce(|plus(0))                                           % 15",
            "{,}.reduce(|plus(0))                                                   % 0",
            "reduce(|mult(0))                                                       % 0",
            "{1,2,3,4,5}.reduce(|mult(2))                                           % 240",
            "{1,2,3,4,5}.reduce(|mult(1))                                           % 120",
            "{1,2,3,4,5}.reduce(|inst(2){ mult(*<0>) })                             % 240",
            "{1,2,3,4,5}.reduce(|inst(1){ mult(*<0>) })                             % 120",
            "{1,2,3,4,5}.reduce(|inst(0){ plus(*<0>) })                             % 15",
            "{'a','b','c'}.>-' '                                                    % \"a b c\"",
            "{'a','b','c'}>-' '                                                     % \"a b c\"",
            "'a b c'-<' '                                                           % {\"a\", \"b\", \"c\"}",
            "'a b c'-<' '>-' '                                                      % \"a b c\"",
            "'a b c'.split(' ').merge(' ')                                          % \"a b c\"",
            "{a,b,c}.>-/                                                            % a/b/c",
            "{a,b,c}>-/                                                             % a/b/c",
            "a/b/c.-</                                                              % {a, b, c}",
            "a/b/c.-</.>-/                                                          % a/b/c",
            "a/b/c.-</>-/                                                           % a/b/c",
            "a/b/c.split(/).merge(/)                                                % a/b/c",
            "a/b/c.split(/).merge(/).mult(<.>)                                      % a/b/c",
            "a/b/c.split(/).merge(/).mult(<..>)                                     % a/b",
    }, delimiter = '%')
    public void testReductions(final String code, final String expected) {
        super.testCode(code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "math('1+2')                                                            % 3.0",
            "10.to(a).math('a^2')                                                   % 100.0",
            "10.to(a).plus(10).to(b).math('a+b')                                    % 30.0",
            "10.to(a).plus(10).to(b).math('(a+b)^2')                                % 900.0",
            "1-<[plus(1).to(a),plus(2).to(b)]>-.math('a*b')                         % real{2}::6.0",
            "1-<[to(a).math('a+a'),to(c).math('c+5')]                               % [2.0,6.0]",
            "1-<[to(a).math('a+a'),to(c).math('c+5')]>-.sum?real<=real{*}()         % 8.0",
    }, delimiter = '%')
    public void testMath(final String code, final String expected) {
        super.testCode(code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "1-<|[is(gt(0))=>plus(6),_=>plus(100)].rng()                              % 7",
            "1-<|[is(gt(1))=>plus(6),_=>plus(100)].rng()                              % 101",
            "1-<[is(gt(0))=>plus(6),_=>plus(100)].rng()                               % {7,101}",
            "{1,2,3}-<|[is(gt(1))=>1.plus(5),_=>9.plus(89.plus(3))].rng()             % {101,6,6}",
            "{1,2,3}-<|[is(gt(1))=>plus(6),_=>plus(100)].rng()                        % {101,8,9}",
            "{1,2,3}-<[is(gt(1))=>plus(6),_=>plus(100)].rng()                         % {101,8,102,9,103}",
    }, delimiter = '%')
    public void testBranches(final String code, final String expected) {
        super.testCode(code, expected);
    }


    @ParameterizedTest
    @CsvSource(value = {
            "1.repeat(plus(1),10))                                                                % 11",
            "1.repeat(plus(1),10).repeat(minus(1),11)                                             % 0",
            "1.repeat(10,10)                                                                      % 10",
            "1.repeat(10,0)                                                                       % 1",
            "{1,2}.repeat(plus(1),10)                                                             % {11,12}",
    }, delimiter = '%')
    public void testRepeat(final String code, final String expected) {
        super.testCode(code, expected);
    }
    
    @ParameterizedTest
    @CsvSource(value = {
            "fail::['bad']['really bad']['oh no'].catch('okay now')                   % \"okay now\"",
            "fail::['bad']['really bad']['oh no'].plus('okay now')                    % fail::['bad']['really bad']['oh no']",
            "1.plus(1).failure('bad').plus(2).plus(3)                                 % fail::['bad']",
            "1.plus(1).failure('bad').plus(2).catch(34).plus(3)                       % 37",
            "1.plus('a').catch(cause())                                               % noobj",
            "1.plus('a').catch(failure('bad'))                                        % fail::['bad']",
            "1.plus(mult(failure('bad'))).mult(23).catch(34).plus(2)                  % 36",
    }, delimiter = '%')
    public void testFailureCatch(final String code, final String expected) {
        super.testCode(code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "a -> [b=>[c=>d]]              % a/b/c -> 34                     % a            % [b=>[c=>34]]",
            "a -> [b=>[c=>d]]              % a/b/c -> [f=>g]                 % a            % [b=>[c=>[f=>g]]]",
            "a -> [b=>[c=>d]]              % a/b/c -> [f=>g]                 % a/b          % [c=>[f=>g]]",
            "a -> [b=>[c=>d]]              % a/b/c -> [f=>g]                 % a/b/c        % [f=>g]",
            "a -> [b=>[c=>d]]              % a/b/c -> [f=>g]                 % a/b/c/f      % g",
            "a -> [b=>[c=>d]]              % <a/b/..> -> 22                  % a            % 22",
            "a -> [b=>[c=>{1,2,3}]]        % <a/b/c> -> {4,5,6}              % a/b/c        % {1,2,3,4,5,6}",
            "a -> [b=>[c=>{1,2,3}]]        % <a/b/c> -> {4,5,6}              % a            % [b=>[c=>{1,2,3,4,5,6}]]",
            "a -> noobj                    % a -> noobj                      % a            % noobj",
            "a -> {1,2,3,4}                % a -> {5,6,7}                    % a            % {1,2,3,4,5,6,7}",
            "a -> noobj                    % a -> noobj                      % a            % noobj",
            "a -> 1                        % a -> {2,3,4}                    % a            % {1,2,3,4}",
            "a -> noobj                    % a -> noobj                      % a            % noobj",
            "a -> {1,2,3}                  % a -> 4                          % a            % {1,2,3,4}",
            "a -> noobj                    % a -> noobj                      % a            % noobj",
    }, delimiter = '%')
    public void testPolySpace(final String stateCode, final String mutationCode, final String vid, final String expected) {
        super.testSpace(stateCode, mutationCode, Map.of(f(vid), expected));

    }


    @ParameterizedTest
    @CsvSource(value = {
            "[a=>1,b=>2,c=>3].group([_=>_])                                               % [[a=>1,b=>2,c=>3]=>[a=>1,b=>2,c=>3]]",
            "{1,2,3}.group([_=>+10])                                                      % [1=>11,2=>12,3=>13]",
            "{1,2,3}.group([_=>8])                                                        % [1=>8,2=>8,3=>8]",
            "{1,2,3}.group([noobj=>_])                                                    % [=>]",
            "{1,2,3}.group([_=>noobj])                                                    % [=>]",
            //"{[a,b],[c,d],[a,b]}.group([>-.prod()=>>-.count()])                           % [a/b=>4,c/d=>2]",
            "{[a,b],[c,d],[a,b]}.group([>-.prod?uri<=uri{*}()=>>-.count()])               % [a/b=>4,c/d=>2]", // should be uri{2}
            "[a=>1,b=>c,c=>3]==[is(eq(a))=>plus(1)]                                       % [a=>2]",
            // dummy without ending comma so it's easier to add more test cases
            "1.plus(1)                                                              % 2"
    }, delimiter = '%')
    public void testGroup(final String code, final String expected) {
        super.testCode(code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "[a=>1,b=>2,c=>3].select([a=>_])                                                                                             % [a=>1]",
            "{[a=>1],[b=>2],[c=>3]}.select?rec{1}<=rec{1}([_=>_])                                                                        % {[a=>1],[b=>2],[c=>3]}",
            "{[a=>1],[b=>2],[c=>3]}.select([_=>_]).where([_=>_])                                                                         % {[a=>1],[b=>2],[c=>3]}",
            "{[a=>1],[b=>2],[c=>3]}.select([_=>_]).where([_=>is(gt(1))])                                                                 % {[b=>2],[c=>3]}",
            "{[a=>1],[2=>2],[c=>3]}.select([_=>_]).where([isa(uri::T[])=>_])                                                             % {[a=>1],[c=>3]}",
            "{[a=>1],[2=>2],[c=>3]}.select([_=>_]).where([isa(uri::T[])=>is(gt(1))])                                                     % {[c=>3]}",
            "{[a=>1],[2=>2],[c=>3]}.select([_=>_]).where(noobj)                                                                          % noobj",
            "{[a=>1],[2=>2],[c=>3]}.select([_=>_]).where()                                                                               % noobj",
            "{[a=>1],[2=>2],[c=>3]}.select([_=>_]).where([noobj=>is(gt(1))])                                                             % {[a=>1],[2=>2],[c=>3]}",
            "{[a=>1],[2=>2],[c=>3]}.select([_=>_]).where([=>])                                                                           % {[a=>1],[2=>2],[c=>3]}",
            "[a=>1,b=>2,c=>3].select([z=>_])                                                                                             % noobj",
            "[a=>1,b=>2,c=>3].select([isa(uri::T[])=>_])                                                                                 % [a=>1,b=>2,c=>3]",
            "[a=>1,b=>2,c=>3].select([isa(uri::T[])=>-<[_,_]])                                                                           % [a=>[1,1],b=>[2,2],c=>[3,3]]",
            "[a=>1,b=>2,c=>3].select([isa(uri::T[])=>-<[_,_]>-])                                                                         % [a=>{1,1},b=>{2,2},c=>{3,3}]",
            "[a=>1,b=>2,c=>3].select([isa(uri::T[])=>-<[_,_]>-.sum()])                                                                   % [a=>2,b=>4,c=>6]",
            "[a=>1,b=>2,c=>3].select([isa(uri::T[])=>-<[_,_]>-.sum()]).where([a=>is(gt(2))])                                             % noobj",
            "[a=>1,b=>2,c=>3].select([isa(uri::T[])=>-<[_,_]>-.sum()]).where([a=>is(gt(1))])                                             % [a=>2,b=>4,c=>6]",
            /// //////////////////////////////////////////////////////////////////////////////////                                       ////////////////////
            "[1,2,3].select([_,noobj,_])                                                                                                 % [1,noobj,3]",
            "[1,2,3].select([_,plus(5),_])                                                                                               % [1,7,3]",
            "[1,2,3].select([_,plus(5),_]).where([_,is(gt(5)),_])                                                                        % [1,7,3]",
            "[1,2,3].select([_,plus(5),_]).where([_,is(gt(15)),_])                                                                       % noobj",
            "[1,[2=>5],3].select([_,select[_=>plus(2)],_])                                                                               % [1,[2=>7],3]",
            "[1,[2=>5],3].select([_,[_=>plus(2)],_])                                                                                     % [1,[2=>7],3]", // TODO: should select()/where() be recurssive
            "[1,[2=>5],3].select([_,select[_=>plus(2)],_]).where([_,[_=>is(gt(6))],_])                                                   % [1,[2=>7],3]",
            "[1,[2=>5],3].select([_,select[_=>plus(2)],_]).where([_,[_=>is(gt(6))],is(gt(1))])                                           % [1,[2=>7],3]",
            "[1,[2=>5],3].select([_,select[_=>plus(2)],_]).where([_,[_=>is(gt(6))],is(gt(3))])                                           % noobj",
            "[1,[2=>5],3].select([_,select[_=>plus(2)],_]).where([_,[_=>is(gt(10))],_])                                                  % noobj",
            "[1,[2=>5],3].select([-<[_,_],select[_=>plus(2)],plus(7)]).where([isa(lst::T[]),[_=>is(gt(6))],is(gt(3))])                   % [[1,1],[2=>7],10]",
            "[1,[2=>5],3].select([-<[_,_]>-.sum(),select[_=>plus(2)],plus(7)]).where([isa(lst::T[]),[_=>is(gt(6))],is(gt(3))])           % noobj",
            "[1,[2=>5],3].select([-<[_,_]>-.sum()-<[_],select[_=>plus(2)],plus(7)]).where([isa(lst::T[]),[_=>is(gt(6))],is(gt(3))])      % [[2],[2=>7],10]",
            //"[1,[2=>5],3].select([-<[_,_]>-.sum()-<[_]>-,select[_=>plus(2)],plus(7)]).where([isa(int::T[]),[_=>is(gt(6))],is(gt(3))])  % [2,[2=>7],10]",
            //"[a=>1,b=>2,c=>3]>-.-<[=>].where([_=>is(gt(1))])                                                                 % {[b=>4],[c=>6]}",
    }, delimiter = '%')
    public void testSelectWhere(final String code, final String expected) {
        super.testCode(code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "0x12345678<<                                                                                                % 0x345678",
            "0x12345678>>                                                                                                % 0x123456",
            "0x12345678<<1                                                                                               % 0x345678",
            "0x12345678>>1                                                                                               % 0x123456",
            "0x12345678<<2                                                                                               % 0x5678",
            "0x12345678>>2                                                                                               % 0x1234",
            "0x12345678<<3                                                                                               % 0x78",
            "0x12345678>>3                                                                                               % 0x12",
            "0x123456.plus(0x00)                                                                                         % 0x12345600",
            "0x00.plus(0x123456)                                                                                         % 0x00123456",
            "0x123456.plus(0xaf)                                                                                         % 0x123456af",
            "0xaf.plus(0x123456)                                                                                         % 0xaf123456",
            "0x123456.plus(0xaf01)                                                                                       % 0x123456af01",
            "0xaf01.plus(0x123456)                                                                                       % 0xaf01123456",
            /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            "a/b/c<<                                                                                                   % b/c",
            "a/b/c>>                                                                                                   % a/b",
            "a/b/c<<2                                                                                                  % c",
            "a/b/c>>2                                                                                                  % a",
            "a/b/c<<3                                                                                                  % <.>",
            "a/b/c>>3                                                                                                  % <.>",
            "a/b/c<<4                                                                                                  % <.>",
            "a/b/c>>4                                                                                                  % <.>",
            /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            "12<<                                                                                                      % noobj",
            "12>>                                                                                                      % noobj",
            "12<<2                                                                                                     % noobj",
            "12>>2                                                                                                     % noobj",
            "'abc'<<                                                                                                   % noobj",
            "'abc'>>                                                                                                   % noobj",
            "true<<4                                                                                                   % noobj",
            "false>>4                                                                                                  % noobj",
            /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            "[1,2,[a=>3],4]<<                                                                                               % [2,[a=>3],4]",
            "[1,2,[a=>3],4]>>                                                                                               % [1,2,[a=>3]]",
            "[1,2,[a=>3],4]<<1                                                                                              % [2,[a=>3],4]",
            "[1,2,[a=>3],4]>>1                                                                                              % [1,2,[a=>3]]",
            "[1,2,[a=>3],4]<<2                                                                                              % [[a=>3],4]",
            "[1,2,[a=>3],4]>>2                                                                                              % [1,2]",
            "[1,2,[a=>3],4]<<3                                                                                              % [4]",
            "[1,2,[a=>3],4]>>3                                                                                              % [1]",
            "[1,2,[a=>3],4]<<4                                                                                              % [,]",
            "[1,2,[a=>3],4]>>4                                                                                              % [,]",
            "[1,2,[a=>3],4]<<5                                                                                              % [,]",
            "[1,2,[a=>3],4]>>5                                                                                              % [,]",
            /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            "[a=>1,b=>2,c=>[d=>3,e=>[f=>4]]]<<                                                                         % {a,b,c}",
            "[a=>1,b=>2,c=>[d=>3,e=>[f=>4]]]>>                                                                         % {1,2,[d=>3,e=>[f=>4]]}",
            "[a=>1,b=>2,c=>[d=>3,e=>[f=>4]]]<<1                                                                        % {a,b,c}",
            "[a=>1,b=>2,c=>[d=>3,e=>[f=>4]]]>>1                                                                        % {1,2,[d=>3,e=>[f=>4]]}",
            "[a=>1,b=>2,c=>[d=>3,e=>[f=>4]]]<<2                                                                        % {,}",
            "[a=>1,b=>2,c=>[d=>3,e=>[f=>4]]]>>2                                                                        % {3,[f=>4]}",
            "[a=>1,b=>2,c=>[d=>3,e=>[f=>4]]]<<3                                                                        % {,}",
            "[a=>1,b=>2,c=>[d=>3,e=>[f=>4]]]>>3                                                                        % 4",
            "[a=>1,b=>2,c=>[d=>3,e=>[f=>4]]]<<4                                                                        % {,}",
            "[a=>1,b=>2,c=>[d=>3,e=>[f=>4]]]>>4                                                                        % {,}",
    }, delimiter = '%')
    public void testShift(final String code, final String expected) {
        super.testCode(code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "1.as(int::T)                                                                                                % 1",
            "1.as(str::T)                                                                                                % \"1\"",
            "1.as(real::T)                                                                                               % 1.0",
            /// /////////////////////////////////////
            "1.0.as(int::T)                                                                                              % 1",
            "1.23.as(int::T)                                                                                             % 1",
            /// /////////////////////////////////////
            "\"/a/b/c\".as(uri::T)                                                                                       % /a/b/c",
            "\"1\".as(int::T)                                                                                            % 1",
            /// /////////////////////////////////////
            "/a/b/c.as(str::T)                                                                                           % \"/a/b/c\""
    }, delimiter = '%')
    public void testAs(final String code, final String expected) {
        super.testCode(code, expected);
    }
}
