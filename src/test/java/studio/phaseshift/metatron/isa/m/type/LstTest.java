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

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
/*
 "/t -> [a,[b,[c,d],e],f]                               % */

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.algebra.AbstractAlgebraTest;
import studio.phaseshift.metatron.isa.m.parser.mParser;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.algebra.Form.PLUS_MONOID;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;


public class LstTest extends AbstractAlgebraTest<Lst> {

    public LstTest() {
        super(lst(jnt(1), jnt(2), jnt(3)), Set.of(PLUS_MONOID));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "[a,b,c]>>a                                                                 % noobj",
            "[a,b,c]>>+                                                                 % {a,b,c}",
            "[a,b,c]>><+>                                                               % {a,b,c}",
            "[a,b,c]>>{0,0}                                                             % {2}a",
            "[{5,8}a,b,{7}c]>>{{4,6}0,0}                                                % {25,56}a", // two zeros collapse on the key select
            "{2,3}[{5,8}a,b,{7}c]>>{{4,6}0,0}                                           % {50,168}a", // two zeros collapse on the key select
            "[{5,8}a,b,{7}c]>>{{4,6}0,0}                                                % {25,56}a", // two zeros collapse on the key select
            "{2,3}[{5,8}a,b,{-7}c]>>{{4,6}0,0}                                          % {50,168}a", // two zeros collapse on the key select
          //  "{2,3}[{-5,8}a,b,{-7}c]>>?#{*}<=lst{{4,6}0,0}                              % {-50,168}a", // two zeros collapse on the key select
           // "{-2,3}[{-5,8}a,b,{-7}c]>>?#{**}<=lst{**}{{4,6}0,0}                             % {50,168}a", // two zeros collapse on the key select
            "[a,b,c]>>{<0>,<0>}                                                         % {2}a",
            "[a,b,c]>>{<0>,0}                                                           % {2}a",
            "[a,b,c]>>{<+>,0}                                                           % {{2}a,b,c}",
            "[a,b,c]>>{<+>,{15}<0>}                                                     % {{16}a,b,c}",
            "[a,b,c]>>{{2}<+>,{15}<0>}                                                  % {{17}a,{2}b,{2}c}",
            "[a,b,c]>>{{2}<+/>,{15}<0>}                                                 % {{15}a,{2}0=>a,{2}1=>b,{2}2=>c}", // should this be uri keyed? (using indexed stream)
            "[a,b,c]>>{{2}<+/>,{15}<0>}>>                                               % {{2}a,{2}b,{2}c}",
            "[a,{3}b,c]>>{{2}<+/>,{15}<0>}>>                                            % {{2}a,{6}b,{2}c}",
            "[a,{3,5}b,c]>>{{2}<+/>,{15}<0>}>>                                          % {{2}a,{6,10}b,{2}c}",
            "{10}[a,b,c]>>{{2}<+/>,{15}<0>}>>                                           % {{20}a,{20}b,{20}c}",
            "[a,b,c]>>{{2}<+/>,{15}<0>}>>.cc().sum()                                    % 6",
            "[a,b,c]>><+/>                                                              % {0=>a,1=>b,2=>c}", // should this be uri keyed? (using indexed stream)
            "[a,b,c]>><#>                                                               % {a,b,c}",
            "[a,b,c]>><#/>                                                              % {0=>a,1=>b,2=>c}", // should this be uri keyed? (using indexed stream)
            "[a,b,c]>>0                                                                 % a",
            "[a,b,c]>>1                                                                 % b",
            "[a,b,c]>>2                                                                 % c",
            "[a,b,c]>>3                                                                 % noobj",
            "[a,b,c]>><0/>                                                              % <0>=>a",
            "[a,b,c]>><1/>                                                              % <1>=>b",
            "[a,b,c]>><2/>                                                              % <2>=>c",
            "[a,b,c]>><3/>                                                              % noobj",
            "[a,b,c]>>{1,2}                                                             % {b,c}",
            "[a,b,c]>>{1,2,3}                                                           % {b,c}",
            "[a,b,c]>>{0,1}                                                             % {a,b}",
            "[a,b,c]>>{0,1,2}                                                           % {a,b,c}",
            "[a,b,c]>>{0,1,2,3}                                                         % {a,b,c}",
            "[a,b,c]>>{0,1,2,3,4}                                                       % {a,b,c}",
            "[a,b,c]>>{<0/>,<1/>}                                                       % {<0>=>a,<1>=>b}",
            "[a,b,c]>>{<0/>,<1/>,<2/>}                                                  % {<0>=>a,<1>=>b,<2>=>c}",
            "[a,b,c]>>{<0/>,<1/>,<2/>,<3/>}                                             % {<0>=>a,<1>=>b,<2>=>c}",
            "[a,b,c]>>{<0/>,<1/>,2,<3>}                                                 % {<0>=>a,<1>=>b,c}",
            "[a,b,c]>>{0,<1/>,<2>}                                                      % {a,<1>=>b,c}",
            "[a,b,c]>>{<0>,{23}<1/>,2}                                                  % {a,{23}<1>=>b,c}",
            "[a,b,c]>>{0,{23}<1/>,2}>>                                                  % {23}b",
            "{2}[a,b,c]>>{<100>,{23}<1>,34}                                             % {46}b",
            "{2}[a,b,c]>>{<0>,{23}<1/>,<2>}                                             % {{2}a,{46}<1>=>b,{2}c}",
            "{2}[a,b,c]>>{<0>,{23}<1/>,<2>}                                             % {2}[a,{23}<1>=>b,c]>-",
            "{2}[a,b,c]>>{<0>,<1>,2}.<<                                                 % {6}[a,b,c]",
            "{2}[a,b,{4}c]>>2                                                           % {8}c",
            // "{2}[a=>1,b=>2,c=>3]>>{a,{23}b/,c}.<<                                       % {25}[a=>1,b=>2,c=>3]", // TODO: review: is this the semantics we want?
    }, delimiter = '%')
    public void testAt(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            // lst                                 | key                  | value
            "[a,[b,[c,d],e],f]                     | <>                   | noobj",
            "[a,[b,[c,d],e],f]                     | <0>                  | a",
            "[a,[b,[c,d],e],f]                     | <1/0>                | b",
            "[a,[b,[c,d],e],f]                     | <1/1/0>              | c",
            "[a,[b,[c,d],e],f]                     | <1/1/1>              | d",
            "[a,[b,[c,d],e],f]                     | <1/1/+>              | {c,d}",
            "[a,[b,[c,d],e],f]                     | <1/+/+>              | {c,d}",
            "[a,[b,[c,d],[e,f]],g]                 | <1/+/+>              | {c,d,e,f}",
            "[a,[b,[c,d],[e,[f,g]]],h]             | <1/+/+>              | {c,d,e,[f,g]}",
            "[a,[b,[c,d],e],f]                     | <1/+>                | {b,[c,d],e}",
            "[a,[b,[c,d],e],f]                     | <1/+>                | {b,[c,d],e}",
            "[a,[b,[c,d],e],f]                     | <#>                  | {a,[b,[c,d],e],f}" // TODO: should this be unrolled?
    }, delimiter = '|')
    public void testKeyValue(final String lst, final String key, final String value) {
        Lst r = mParser.m_obj().parse(lst).get();
        Obj k = mParser.m_obj().parse(key).get();
        Obj v = mParser.m_obj().parse(value).get();
        Obj actual = r.at(k);
        LOG.debug("testing %s at %s is %s [expected:%s]", k, r, actual, v);
        assertTrue(r.isLst());
        assertEquals(v, actual);
    }


    @ParameterizedTest
    @CsvSource(value = {
            "[a,[b,[c,d],e],f]                                                                   % [a,[b,[c,d],e],f]",
            "[a,[b,[c,d],e],f]>>0                                                                % a",
            "[a,[b,[c,d],e],f]>><1/0>                                                            % b",
            "[a,[b,[c,d],e],f]>><1/1/0>                                                          % c",
            "[a,[b,[c,d],e],f]>><1/1/1>                                                          % d",
            "[a,[b,[c,d],e],f]>><1/1/+>                                                          % {c,d}",
            "[a,[b,[c,d],e],f]>><1/+/+>                                                          % {c,d}",
            "[a,[b,[c,d],[e,f]],g]>><1/+/+>                                                      % {c,d,e,f}",
            "[a,[b,[c,d],[e,[f,g]]],h]>><1/+/+>                                                  % {c,d,e,[f,g]}",
            "[a,[b,[c,d],e],f]>><1/+>                                                            % {b,[c,d],e}",
            "[a,[b,[c,d],e],f]>><1/+>                                                            % {b,[c,d],e}",
            "[a,[b,[c,d],e],f]>><#>                                                              % {a,[b,[c,d],e],f}" // TODO: should this be unrolled?

    }, delimiter = '%')
    public void testCode(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "lst{10}::[1,2,3]                                                                        % lst{10}::[1,2,3]",
            "lst{10}::[1,2,3]>-                                                                      % {int{10}::1,int{10}::2,int{10}::3}",
            "lst{10}::[1,2,3]._/sum()\\_                                                             % lst{10}::[6]",
            "lst{10}::[1,2,3]._/sum()\\_._/sum()\\_                                                  % lst{10}::[6]",
            "lst{10}::[1,2,3]._/sum()\\_.>-.-<?lst<=#{*}([_])._/sum()\\_                             % [60]",
            "lst{10}::[1,2,3]._/sum()\\_.-<?lst<=#{*}[_]                                             % [{10}[6]]",
            "lst{10}::[1,2,3]._/sum()\\_.-<[_]                                                       % {10}[[6]]",
            "lst{10}::[1,2,3]._/sum()\\_.-<?lst<=#{*}[-<[_]]                                         % [{10}[[6]]]",
            "lst{10}::[1,2,3]._/sum()\\_.-<?lst<=#{*}[-<[_]-<[_]]                                    % [{10}[[[6]]]]",
            "lst{10}::[1,2,3]._/sum()\\_.-<[-<[_]-<[_]]                                              % {10}[[[[6]]]]",
            "lst{10}::[1,2,3]._/sum()\\_.-<[-<[-<[_]>-]]                                             % {10}[[[6]]]",
            //   "lst{10}::[1,2,3]._/sum()\\_.-<?lst<=#{*}[-<[-<[_]>-]]                                   % [[{10}[6]]]", //TODO: this is strange
            "[1,2,3]._/sum()\\_.-<?lst<=#{*}[-<[-<[_]>-]]                                            % [[[6]]]",
            "lst{10}::[1,2,3]._/sum()\\_-<[-<[_]>-]>-                                                % lst{10}::[6]",
            "lst{10}::[1,2,3]._/sum()\\_-<[-<[_]]>-.>-                                               % lst{10}::[6]",
            "lst{10}::[1,2,3]._/sum()\\_-<[-<[_]]>-.>-.>-                                            % int{10}::6",
            "lst{10}::[1,2,3]._/sum()\\_-<[-<[_]>-]>-.>-                                             % int{10}::6",
            "lst{10}::[1,2,3]._/sum()\\_-<[-<[_]>-.>-].>-                                            % int{10}::6",
            "lst::[1,2,3]._/sum()\\_-<[-<[_]>-.>-].>-                                                % int::6",
            "lst{10}::[1,2,3]._/sum()\\_-<[-<[_]]                                                    % {10}[[[6]]]",
            "lst{10}::[1,2,3]._/sum()\\_-<[-<[_]>-.>-.>-]                                            % {10}[6]",
            "lst{10}::[1,2,3]>-.sum?int<=int{*}()                                                    % 60",
            "lst{10}::[1,2,3]>-.sum().sum()                                                          % 60",
            "lst{10}::[1,2,3]>-.sum()                                                                % 60",
            "lst::[1,2,3]>-.sum()                                                                    % 6",
            "lst::[1,2,3]>-.sum?int<=int{*}()                                                        % 6",
            "lst::[1,2,3]>-.sum().sum()                                                              % 6",
            "lst::[1,2,3]>-.sum()                                                                    % 6",
    }, delimiter = '%')
    public void testCoefficients(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    /*
       "lst{10}::[1,2,3]._/sum()\\_.>-.-<?lst<=#{*}([_])._/sum()\\_                                  % [60]",
            "lst{10}::[1,2,3]._/sum()\\_.-<?lst<=#{*}[_]                                             % [lst{10}::[6]]",
            "lst{10}::[1,2,3]._/sum()\\_.-<?lst<=#{*}[-<[_]]                                         % [lst{10}::[[6]]]",
            "lst{10}::[1,2,3]._/sum()\\_.-<?lst<=#{*}[-<[_]-<[_]]                                    % [lst{10}::[[[6]]]]",
            "lst{10}::[1,2,3]._/sum()\\_.-<?lst<=#{*}[-<[-<[_]>-]]                                   % [lst{10}::[[6]]]",
            "lst{10}::[1,2,3]._/sum()\\_-<[-<[_]>-]>-                                                % lst{10}::[6]",
            "lst{10}::[1,2,3]._/sum()\\_-<[-<[_]]>-.>-                                               % lst{10}::[6]",
            "lst{10}::[1,2,3]._/sum()\\_-<[-<[_]]>-.>-.>-                                            % int{10}::6",
            "lst{10}::[1,2,3]._/sum()\\_-<[-<[_]>-]>-.>-                                             % int{10}::6",
            "lst{10}::[1,2,3]._/sum()\\_-<[-<[_]>-.>-].>-                                            % int{10}::6",
            //"lst{10}::[1,2,3]._/sum()\\_-<[-<[_]>-.>-.>-]                                            % [int{10}::6]",
     */

    @ParameterizedTest
    @CsvSource(value = {
            "[1,2,3].as(rec::T)                                                % [0=>1,1=>2,2=>3]",
            "lst{10}::[1,2,3].as(rec::T)                                       % rec{10}::[0=>1,1=>2,2=>3]",
            "lst{10}::[1,2,3].as(rec::T).merge?rel{*}<=rec()                   % {rel{10}::(0=>1),rel{10}::(1=>2),rel{10}::(2=>3)}",
            "[1,2,3].as(rec::T).>-.isa(rel::T).count()                         % 3",
            "[1,2,3].as(rec::T).>-.isa(rel::T).count()                         % 3",
            "[1,2,3].as(rec::T).>-.>>.isa(int::T).count()                      % 3",
            "[1,2,3].as(rec::T).>-.>>.sum()                                    % 6",
            "[1,2,3].as(rec::T).as(lst::T)                                     % [(0=>(0=>1)),(1=>(1=>2)),(2=>(2=>3))]",
            "[1,2,3].as(rec::T).as(rec::T)                                     % [0=>1,1=>2,2=>3]",
            "[1,2,3].as(rec::T).as(lst::T).as(rec::T)                          % [0=>(0=>(0=>1)),1=>(1=>(1=>2)),2=>(2=>(2=>3))]",

    }, delimiter = '%')
    public void testAs(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "[a,b,c].reverse()                                                   % [c,b,a]",
            "[12,bac,25,45,\"_245\"].reverse()                                   % [\"_245\",45,25,bac,12]",
            "[abc,def,ghi].reverse()                                             % [ghi,def,abc]",
            "['abc','def','ghi'].reverse()==[reverse(),reverse(),reverse()]      % ['ihg','fed','cba']",
            "[a,[b,c],[d,e]].reverse()                                           % [[d,e],[b,c],a]",
            "[a,[b,c],[d,e]].reverse()==[reverse(),reverse(),reverse()]          % [[e,d],[c,b],a]",
            "[a,[b,c],[d,e]].reverse()==[reverse(),>-.count(),reverse()]         % [[e,d],2,a]",
            "[,].reverse()                                                       % [,]",
            "[a].reverse()                                                       % [a]",
            "[a,b].reverse().reverse()                                           % [a,b]",
             "[a,[b,c],[d,e]].reverse()==[reverse(),reverse(),reverse()]         % [[e,d],[c,b],a]",
    }, delimiter = '%')
    public void testReverse(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "[1,2,3].merge()                                                     % {1,2,3}",
            "[a,b,c].merge()                                                     % {a,b,c}",
            "[,].merge()                                                         % {,}",
            "[[a,b],[c,d]].merge()                                               % {[a,b],[c,d]}",
    }, delimiter = '%')
    public void testMerge(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "[1,2,3]>-.count()                                                     % 3",
            "[a,b]>-.count()                                                       % 2",
            "[a]>-.count()                                                         % 1",
            "[,]>-.count()                                                         % 0",
            "[[a,b],c,[d,e,f]]>-.count()                                           % 3",
    }, delimiter = '%')
    public void testCount(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "[1,2,3].plus([4,5])                                                 % [1,2,3,4,5]",
            "[a,b].plus([c])                                                     % [a,b,c]",
            "[,].plus([a])                                                       % [a]",
            "[a].plus([,])                                                       % [a]",
            "[,].plus([,])                                                       % [,]",
    }, delimiter = '%')
    public void testPlus(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "[1,2,3]                                     % lst::T                       % true",
            "[1,2,3]                                     % lst[int,int,int]::T          % true",
            "[1]                                         % lst[int,int]::T              % false",
            "[1]                                         % lst[int,int{0}]::T           % true",
            "[1]                                         % lst[int,int{?}]::T           % true",
            "[,]                                         % lst[int{?},int{?}]::T        % true",
            "[,]                                         % lst[int{0},int{0}]::T        % true",
            "[,]                                         % lst[int{0},int]::T           % false",
            "[a=>1,b=>2]                                 % lst[str,str]::T              % false",
            "{1,2,3}                                     % lst[int,int,int]::T          % false",
            "['a',1,['b',2]]                             % lst[str,int,lst]::T          % true",
            "['a',{2}1]                                  % lst[str{?},int{2,3}]::T      % true",
            "['a',{4}1]                                  % lst[str{?},int{2,3}]::T      % false",
            "[{0}1,1,['b',2]]                            % lst[int{?},int,lst]::T       % true",
            "[{0}1,1,['b',2]]                            % lst[int,int,lst]::T          % false",
            "[{1}1,{5}'a',2.01234]                       % lst[int,str{+},real]::T      % true",
            "[{1}1,{5}'a',real::T]                       % lst[int,str{+},real]::T      % true",
            "lst[int,str{+},real]::T                     % lst[int,str{+},real]::T      % true",
            "lst[int,str,real]::T                        % lst[int,str{+},real]::T      % true",
            "lst[int,str{0},real]::T                     % lst[int,str{+},real]::T      % false",
            "[{1,2,3},{'a','b'}]                         % lst[int{3},str{+}]::T        % true",
            "[{1,2,3},{'a','b'}]                         % lst[int{*}]::T               % true",
    }, delimiter = '%')
    public void testPoly(final String list, final String type, final boolean matches) {
        AbstractMetatronTest.checkMatches(LOG, list, type, matches);
    }
}
