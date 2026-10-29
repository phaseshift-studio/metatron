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

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
/*
 "/t -> [a,[b,[c,d],e],f]                               % */

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.TestData;
import studio.phaseshift.metatron.algebra.AbstractAlgebraTest;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.LST_TYPE;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_from_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;


public class LstTest extends AbstractAlgebraTest {

    @Override
    protected Type carrier() {
        return LST_TYPE;
    }

    @Override
    protected Obj sample() {
        return lst(jnt(1), jnt(2), jnt(3));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "[1,2,3]>> -1                                                                 % 3",
            "[1]>> -1                                                                    % 1",
            "[1,2,3]>> -2                                                                % 2",
            "[1,2,3]>> -3                                                                % 1",
            "[1]>> -2                                                                    % noobj",
            "[1,2,3]>> -4                                                                % noobj",
            "[1,2,3,4]>> 345                                                             % noobj",
            "[1,2,3]>>{1,-2}                                                             % {2,2}",
            "[1,2,3]>>{-1,-2,-3}                                                         % {3,2,1}",
            "[1,2,3]>>{0,-1,2}                                                           % {1,3,3}",
            // existing negative-index cases
            "[a,b,c]>>a                                                                 % noobj",
            "[a,b,c]>>+                                                                 % {a,b,c}",
            "[a,b,c]>><+>                                                               % {a,b,c}",
            "[a,b,c]>>{0,0}                                                             % {2}a",
            "[a,b,c]>>(-1)                                                              % c",
            "[a,b,c]>>{-1,-2}                                                           % {c,b}",
            "[a,b,c]>>{-2,1}                                                            % {2}b",
            "[a,[b=>1,c=>2],c]>><1/b>                                                   % 1",
            "[a,[b=>1,c=>2],c]>><1/+>                                                   % {1,2}",
            "[a,[b=>[d=>3,e=>4],c=>2],c]>><1/+/e>                                       % 4",
            "[a,[b=>[d=>3,e=>4],c=>2],c]>><1/+/+>                                       % {3,4}",
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
            "{2}[a,b,c]>>{<0>,<1>,2}.<<                                                 % {6}<>",
            "{2}[a,b,{4}c]>>2                                                           % {8}c",
            // "{2}[a=>1,b=>2,c=>3]>>{a,{23}b/,c}.<<                                       % {25}[a=>1,b=>2,c=>3]", // TODO: review: is this the semantics we want?
    }, delimiter = '%')
    public void testAt(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            // Step walk vs path walk vs hybrid — all equivalent
            "[1,2,[3,4,[5,6,7]]]>>2>>2>>0                                            % 5",
            "[1,2,[3,4,[5,6,7]]]>><2/2/0>                                            % 5",
            "[1,2,[3,4,[5,6,7]]]>><2/2>>>0                                           % 5",
            "[1,2,[3,4,[5,6,7]]]>><2>>><2/0>                                         % 5",
            // Deep nesting
            "[\"deep\"].>><0>                                                     % \"deep\"",
            "[[\"deep\"]].>><0/0>                                                 % \"deep\"",
            "[[[\"deep\"]]].>><0/0/0>                                             % \"deep\"",
            "[[[[\"deep\"]]]].>><0/0/0/0>                                         % \"deep\"",
            "[[[[\"deep\"]]]].>><0/0>.>><0/0>                                     % \"deep\"",
            "[[[[\"deep\"]]]].>><0>.>><0>.>><0>.>><0>                             % \"deep\"",
            "[\"deep\"].==0                                                       % \"deep\"",
            "[[\"deep\"]].==0==0                                                  % \"deep\"",
            "[[[\"deep\"]]].==0==0==0                                             % \"deep\"",
            "[[[[\"deep\"]]]].==0==0==0==0                                        % \"deep\"",
            "[[[[\"deep\"]]]].==0==0==0==0                                        % \"deep\"",
            "[[[[\"deep\"]]]].==<0/0/0/0>                                         % \"deep\"",
            "[[[[\"deep\"]]]].==<0/+/+/0>                                         % \"deep\"",
            "[[[[\"deep\",\"seek\"]]]].==<0/0/0/+>                                % {\"deep\",\"seek\"}",
            "[[[[\"deep\"]]]].==<0/0/0>==0                                        % \"deep\"",
            "[[[[\"deep\"]]]].==0==<0/0>==0                                       % \"deep\"",
            "[[[[\"deep\"]]]].==<0>.==<0>.==<0>.==<0>                             % \"deep\"",
            //  "[[[[\"deep\"]]]].==<#>                                     % \"deep\"",
            // "[[[[[\"deep\"]]]]].>><0>.>><0>.>><0>.>><0>.>><0>                     % \"deep\"",
            // "[[[[[\"deep\"]]]]].>><0/0/0/0/0>                                     % \"deep\"",
            // "[[[[[[[\"deep\"]]]]]]]>><0/0>.>><0/0>.>><0>                          % \"deep\"",
            // Missing index
            "[1,2,3]>><2/0>                                                                % noobj",
            "[1,2,3]>><5>                                                                  % noobj",
            // The root has no segment to walk
            "[1,2,3]>></>                                                                  % noobj",
            // Leaf is not a poly — can't walk further
            "[1,2,3]>><0/0>                                                                % noobj",
            // Mixed — walk through rec nested in lst
            "[[a=>[b=>42]]]>><0/a/b>                                                       % 42",
            "[[a=>[b=>42]]]>><0>>>a>>b                                                     % 42",
            // Lst multi-segment URI path walks — parentheses needed so the
            // parser treats the full path as a single >> argument.
            "[1,2,[3,4,[5,6,7]]]>><2/2/0>                                                  % 5",
            "[1,2,[3,4,[5,6,7]]]>><2/2>                                                    % [5,6,7]",
            "[[3,4,[5,6,7]]]>><0/2>                                                        % [5,6,7]",
            "[1,2,3]>><0/0>                                                                % noobj",
    }, delimiter = '%')
    public void testPathWalking(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "[a,b,c,d,e]==[?<2 => * X, ?>=2 => * Y]                % [a/X,b/X,c/Y,d/Y,e/Y]",
            "[1,2,3,4,5].as(rec::T)?[_ => ?>6].rng()               % noobj",
            "[1,2,3,4,5].as(rec::T)?[_ => ?<6].rng()>-[,]          % [1,2,3,4,5]"
    }, delimiter = '%', quoteCharacter = '~')
    public void testSelect(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            // lst                                 | key                  | value
            "[a,[b,[c,d],e],f]                     | <>                   | noobj",
            "[a,[b,[c,d],e],f]                     | </>                  | noobj",
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
            "lst{10}::[1,2,3]._/sum?int<=int{*}()\\_.>-.-<?lst<=#{*}([_])._/sum?int<=int{*}()\\_     % [60]",
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
            // the lst fold reads a bulked lst into its slots, once -- a lst is slot-oriented, never a bag of copies
            "lst{2}::[1,2].sum()                                                                     % [{2}1,{2}2]",
            "[1,2]-<[_,_]>-.sum()                                                                    % [{2}1,{2}2]",
            "[1,2]-<[_,_,_]>-.sum()                                                                  % [{3}1,{3}2]",
            "lst{2}::[{3}1,2].sum()                                                                  % [{6}1,{2}2]",
            "lst{2}::[[1,2],3].sum()                                                                 % [{2}[1,2],{2}3]",
            "[[1,2],3]-<[_,_]>-.sum()                                                                % [{2}[1,2],{2}3]",
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
    @TestData(value = {"/sys/tmp/lst_test/0 -> [a=>1,b=>[a,b,c]]"}, oneTime = true)
    @CsvSource(value = {
            "*/sys/tmp/lst_test/0 >>= [a=>2]                                     % [a=>2,b=>[a,b,c]]",
            "*/sys/tmp/lst_test/0                                                % [a=>1,b=>[a,b,c]]",
            "@/sys/tmp/lst_test/0 >>= [a=>2]                                     % [a=>2,b=>[a,b,c]]",
            "*/sys/tmp/lst_test/0                                                % [a=>2,b=>[a,b,c]]",
            "*/sys/tmp/lst_test/0 >>= +[b=>2]                                    % [a=>2,b=>{2,[a,b,c]}]",
            "*/sys/tmp/lst_test/0 >>= -[b=>2]                                    % [a=>2,b=>[a,b,c]]",
            "@/sys/tmp/lst_test/0 >>= [b=>+[d,e]]                                % [a=>2,b=>[a,b,c,d,e]]",
            "*/sys/tmp/lst_test/0                                                % [a=>2,b=>[a,b,c,d,e]]",
            "@/sys/tmp/lst_test/0 >>= [b=>-[a,d]]                                % [a=>2,b=>[b,c,e]]",
            "*/sys/tmp/lst_test/0                                                % [a=>2,b=>[b,c,e]]",
            "@/sys/tmp/lst_test/0                                                % [a=>2,b=>[b,c,e]]@/sys/tmp/lst_test/0",
            // "@/sys/tmp/lst_test/0 >>= +[a=>3,b=>+[d,e]]                          % [a=>{2,3},b=>[b,c,e,d,e]]",
            // "*/sys/tmp/lst_test/0                                                % [a=>{2,3},b=>[b,c,e,d,e]]",
    }, delimiter = '%')
    public void testUpdate(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "[1,2,3].as(rec::T)                                                % [0=>1,1=>2,2=>3]",
            "[1,2,3,4,5,6].as(rec::T) >>= [5 => 'X']                           % [0=>1,1=>2,2=>3,3=>4,4=>5,5=>\'X\']",
            "[1,2,3,4,5,6].as(rec::T) >>= [5 => 'X'].as(lst::T)                % [(0=>(0=>1)),(1=>(1=>2)),(2=>(2=>3)),(3=>(3=>4)),(4=>(4=>5)),(5=>(5=>\'X\'))]",
            "[1,2,3,4,5,6].as(rec::T) == [is(gt(1)).is(lt(5)) => 'X']             % [2=>\'X\',3=>\'X\',4=>\'X\']",
            "[b=>2,a=>1,c=>3] >>= [c => \'X\'].as(lst::T)                         % [(0=>(c=>\'X\')),(1=>(b=>2)),(2=>(a=>1))]",
            "lst{10}::[1,2,3].as(rec::T)                                       % rec{10}::[0=>1,1=>2,2=>3]",
            "lst{10}::[1,2,3].as(rec::T).merge?rel{*}<=rec()                   % {rel{10}::(0=>1),rel{10}::(1=>2),rel{10}::(2=>3)}",
            "[1,2,3].as(rec::T).>-.isa(rel::T).count()                         % 3",
            "{10}[1,2,3].as(rec::T).>-.isa(rel::T).count()                     % 30",
            "[1,2,3].as(rec::T).>-.isa(rel::T).count()                         % 3",
            "[1,2,3].as(rec::T).>-.>>.isa(int::T).count()                      % 3",
            "[1,2,3].as(rec::T).>-.>>.sum()                                    % 6",
            "{10}[1,2,3].as(rec::T).>-.>>.sum()                                % 60",
            "{10}[{5}1,2,3].as(rec::T).>-.>>.sum()                             % 100",
            // "{10}[{5}1,{-10}2,{2}3].as(rec::T).>-.>>.sum()                     % -70",
            "[1,2,3].as(rec::T).as(lst::T)                                     % [(0=>(0=>1)),(1=>(1=>2)),(2=>(2=>3))]",
            "{35}[1,{2}2,3].as(rec::T).as(lst::T)                              % {35}[(0=>(0=>1)),(1=>(1=>{2}2)),(2=>(2=>3))]",
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
            // coefficients: the lhs's is the stream's, so it stays on the lst; the argument's is read into its slots, once
            "lst{2}::[1,2].plus([3])                                             % lst{2}::[1,2,3]",
            "[1].plus(lst{2}::[2,3])                                             % [1,{2}2,{2}3]",
            "[1].plus(lst{2}::[{3}2])                                            % [1,{6}2]",
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
            // AN INDEX'S COEFFICIENT IS THE MULTIPLICITY OF THE VALUE AT THAT INDEX, not another index:
            // lst[int{2},str] is a two-index list whose index 0 holds two ints, so {1,2} belongs there and a lone 1
            // does not. The boundary rows read the coefficient as a multiplicity: {n} exactly n, {n,m} a range, {+}
            // at least one, {?} zero or one (the absent form is the shorter list above), {*} zero or more.
            "[{1,2},'a']                                % lst[int{2},str]::T           % true",
            "[{2}1,'a']                                 % lst[int{2},str]::T           % true",
            "[1,'a']                                    % lst[int{2},str]::T           % false",
            "[{1,2,3},'a']                              % lst[int{2},str]::T           % false",
            "[{1,2},'a']                                % lst[int{2,3},str]::T         % true",
            "[{1,2,3},'a']                              % lst[int{2,3},str]::T         % true",
            "[{1,2,3,4},'a']                            % lst[int{2,3},str]::T         % false",
            "[{1,2},'a']                                % lst[int{+},str]::T           % true",
            "[{1,2},'a']                                % lst[int{*},str]::T           % true",
            "['a',{1,2}]                                % lst[str,int{?}]::T           % false",
            // a plain entry is exactly one value: two ints at index 0 are not an int
            "[{1,2},'a']                                % lst[int,str]::T              % false",
            // a trailing index that admits zero may be unfilled
            "['a']                                      % lst[str,int{?}]::T           % true",
    }, delimiter = '%')
    public void testPoly(final String list, final String type, final boolean matches) {
        AbstractMetatronTest.checkMatches(LOG, list, type, matches);
    }


    /**
     * A GENERIC list poly BINDS at construction: the poly written with a value is that value's shape position by
     * position, so {@code lst[A,B]::[1,'x']} binds {@code A=>int} and {@code B=>str} and the constructed value is
     * typed {@code lst[int,str]}. A variable used twice must resolve to ONE type ({@code lst[A,A]::[1,2]} is a pair of
     * ints) and two different types are a contradiction — failed by the same poly verdict, with the same message, as a
     * concrete poly that does not match ({@code lst[int,int]::[1,'x']}). Arity is the same rule taken literally: a
     * poly of length n is the type of an n-index value, so {@code lst[A,B]::[1]} has nothing to bind {@code B} from.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "lst[A,B]::[1,'x']                                      % lst[int,str]::[1,'x']",
            "lst[A,A]::[1,2]                                        % lst[int,int]::[1,2]",
            "lst[A,A]::[1,'x']                                      % <ERROR>",
            "lst[A,B]::[1]                                          % <ERROR>",
            "lst[int,int]::[1,'x']                                  % <ERROR>",
            "lst[int,int]::[1,2]                                    % lst[int,int]::[1,2]",
    }, delimiter = '%', quoteCharacter = '~')
    public void testGenericPolyBinding(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    /**
     * The bound poly is the VALUE's type, not just a verdict: {@code lst[A,B]::[1,'x']} carries
     * {@code lst[int,str]} as its tid, which is what {@code .type()} reports, while a concrete annotation keeps the
     * meaning it was written with.
     */
    @Test
    public void testGenericPolyBindingCarriesTheBoundPoly() {
        final Obj bound = mParser.m_obj().parse("lst[A,B]::[1,'x']").get();
        assertEquals(f("/m/lst[/m/int,/m/str]"), bound.tid());
        assertEquals(bound.tid(), bound.type().vidOrTid());
        final Obj concrete = mParser.m_obj().parse("lst[int,int]::[1,2]").get();
        assertEquals(f("/m/lst[/m/int,/m/int]"), concrete.tid());
        // .type() answers the same bound poly the tid carries
        AbstractMetatronTest.checkCodeParseApply(LOG, "lst[A,B]::[1,'x'].type()", "lst[int,str]::T");
    }

    /**
     * A LST POLY MISMATCH NAMES ITS INDICES — index by index, the way the rel path diff names its vertices. The
     * report pairs each element of the value with the type at its index ({@code ==>} where they agree, {@code X=>}
     * where they do not), so {@code lst[int,int,str]::[1,2,3.5]} names the third index and the value that failed
     * there.
     */
    @ParameterizedTest
    @CsvSource(value = {
            // code                              % the report pairs each index with its value
            "lst[int,int,str]::[1,2,3.5]         % [==>1,==>2,X=>3.5]",
            "lst[int,str]::[1,2]                 % [==>1,X=>2]",
    }, delimiter = '%')
    public void testPolyMismatchNamesTheFailingIndex(final String code, final String reported) {
        final Obj mismatch = ObjmtronSerializer.parse(code);
        assertTrue(mismatch.isFail(), "a lst poly mismatch must be reported: " + mismatch);
        assertTrue(mismatch.asFail().message().contains(reported.trim()),
                "each index is marked with its value: " + mismatch.asFail().message());
    }

    /**
     * A NAMED TENSOR TYPE READS ITS TID'S BRACKET like a base one, coefficients included.
     * {@code pair -> lst[person{2},str]::T@pair} is a two-index list whose index 0 holds two people, so a pair
     * holding one person is rejected. The coefficient of a predicated entry ({@code pilgrim}) is judged the same way
     * as that of a bare one ({@code person}). Its seed sits beside {@link #testUpdate}'s one-time seed: each test
     * method keeps its own loaded flag, so neither seed suppresses the other.
     */
    @ParameterizedTest
    @TestData(value = {
            "person -> rec::T@person",
            "pilgrim -> rec::T[?[name=>str::T]]@pilgrim",
            "pair -> lst[person{2},str]::T@pair",
    })
    @CsvSource(value = {
            "pair::[{person::[name=>'bob'],person::[name=>'kai']},'a']    % pair::[{person::[name=>'bob'],person::[name=>'kai']},'a']",
            "pair::[person::[name=>'bob'],'a']                            % <ERROR>",
            "pair::[{person::[name=>'bob'],person::[name=>'kai']},2]      % <ERROR>",
            "lst[person{2},str]::[person::[name=>'bob'],'a']              % <ERROR>",
            "lst[pilgrim{2},str]::[pilgrim::[name=>'bob'],'a']            % <ERROR>",
            "lst[pilgrim,str]::[pilgrim::[name=>'bob'],'a']               % lst[pilgrim,str]::[pilgrim::[name=>'bob'],'a']",
    }, delimiter = '%')
    public void testNamedTensorType(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    /**
     * A TENSOR'S INDICES, EDGE BY EDGE. Each index holds its own type in order; a missing index fails unless its entry
     * admits zero, and a present zero-admitting index is still typed; an index's coefficient is the multiplicity at that
     * index; and a poly at an index is read by its base ({@code lst}, {@code rec}, {@code rel}), not unpacked.
     */
    @ParameterizedTest
    @CsvSource(value = {
            // value                      % type                          % matches
            // ---- each index holds its own type, in order ----
            "[1,'a',true]                 % lst[int,str,bool]::T          % true",
            "[1,true,'a']                 % lst[int,str,bool]::T          % false",
            // ---- a missing index fails unless its entry admits zero ----
            "[1,'a']                      % lst[int,str,bool]::T          % false",
            "[1,'a']                      % lst[int,str,bool{?}]::T       % true",
            "[1]                          % lst[int,str{?},bool{?}]::T    % true",
            "[1,'a']                      % lst[int,str{?},bool{?}]::T    % true",
            // ---- a present zero-admitting index is still typed ----
            "[1,2]                        % lst[int,str{?},bool{?}]::T    % false",
            // ---- an index's coefficient is the multiplicity at that index ----
            "[{1,2,3}]                    % lst[int{3}]::T                % true",
            "[{1,2,3}]                    % lst[int{2,4}]::T              % true",
            "[{1,2,3,4,5}]                % lst[int{2,4}]::T              % false",
            "[{1,2},{'a','b','c'}]        % lst[int{+},str{+}]::T         % true",
            "[{1,2},'a']                  % lst[int{?},str]::T            % false",
            // ---- a poly at an index is read by its base, not unpacked ----
            "[[1,2],'a']                  % lst[lst,str]::T               % true",
            "[[1,2],'a']                  % lst[int,str]::T               % false",
            "[[[1]]]                      % lst[lst]::T                   % true",
            "[[a=>1],(1=>2)]              % lst[rec,rel]::T               % true",
            "[(1=>2),[a=>1]]              % lst[rec,rel]::T               % false",
    }, delimiter = '%', quoteCharacter = '~')
    public void testTensorIndexEdges(final String value, final String type, final boolean matches) {
        AbstractMetatronTest.checkMatches(LOG, value, type, matches);
    }

    /**
     * NAMED TENSORS NEST: depth comes from naming, since a bracket cannot hold a bracket. A {@code booking} is two
     * {@code ticket}s and an {@code itinerary} a {@code booking} with an optional {@code ticket}, so constructing a value
     * reads every level's indices in turn — untyped inner lsts are read as the tensor their index names — and reading
     * back down walks the same levels.
     */
    @ParameterizedTest
    @TestData(value = {
            "ticket -> lst[int,str]::T@ticket",
            "booking -> lst[ticket,ticket]::T@booking",
            "itinerary -> lst[booking,ticket{?}]::T@itinerary",
    })
    @CsvSource(value = {
            // ---- a named tensor at an index: its own indices are read in turn ----
            "booking::[[1,'a'],[2,'b']]                        % booking::[[1,'a'],[2,'b']]",
            "booking::[[1,'a'],[2,3]]                          % <ERROR>",
            "booking::[[1,'a']]                                % <ERROR>",
            "booking::[[1,'a'],[2,'b'],[3,'c']]                % booking::[[1,'a'],[2,'b'],[3,'c']]",
            // ---- three levels: an itinerary holds a booking, which holds tickets ----
            "itinerary::[[[1,'a'],[2,'b']]]                    % itinerary::[[[1,'a'],[2,'b']]]",
            "itinerary::[[[1,'a'],[2,'b']],[3,'c']]            % itinerary::[[[1,'a'],[2,'b']],[3,'c']]",
            "itinerary::[[[1,'a'],[2,'b']],[3,4]]              % <ERROR>",
            "itinerary::[[[1,'a'],['b',2]]]                    % <ERROR>",
            // ---- reading back down through the levels ----
            "itinerary::[[[1,'a'],[2,'b']],[3,'c']]>>0>>1>>1   % 'b'",
            "itinerary::[[[1,'a'],[2,'b']],[3,'c']]>>1>>0      % 3",
    }, delimiter = '%', quoteCharacter = '~')
    public void testNestedTensorTypes(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    /**
     * INSTRUCTIONS ON A TENSOR TYPE, OUTER AND INNER. An instruction whose domain is a named tensor applies to any lst
     * whose indices are that tensor's, typed or not, and to nothing else; the outer tensor's instruction hands back an
     * inner lst, which takes the inner tensor's instructions in turn; and the outer tensor is not the inner one.
     */
    @ParameterizedTest
    @TestData(value = {
            "ticket -> lst[int,str]::T@ticket",
            "booking -> lst[ticket,ticket]::T@booking",
            "ticket_name -> |inst?<=ticket(){ >>1 }",
            "booking_first -> |inst?<=booking(){ >>0 }",
    })
    @CsvSource(value = {
            "ticket::[7,'kai'].ticket_name()                          % 'kai'",
            "[7,'kai'].ticket_name()                                  % 'kai'",
            "[7,8].ticket_name()                                      % 8",
            "booking::[[1,'a'],[2,'b']].booking_first()               % [1,'a']",
            "booking::[[1,'a'],[2,'b']].booking_first().ticket_name() % 'a'",
            "booking::[[1,'a'],[2,'b']].ticket_name()                 % <ERROR>",
    }, delimiter = '%', quoteCharacter = '~')
    public void testTensorInstructions(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }


    @ParameterizedTest
    @CsvSource(value = {
            // MUTABLE set: should mutate original in-place
            "[1,2,3]   | 1   | 99  | 99 | 3   | true",     // middle element
            "[1,2,3]   | 0   | 99  | 99 | 3   | true",     // first element
            "[1,2,3]   | -1  | 99  | 99 | 3   | true",     // negative index (last)
            "[10,20]   | 0   | 5   | 5  | 2   | true",     // singleton result
    }, delimiter = '|')
    public void testMutableSet(final String lstStr, final int key, final int value,
                               final int expectedVal, final int expectedCount,
                               final boolean expectSame) {
        final Obj parsed = mParser.m_obj().parse(lstStr).get();
        assertTrue(parsed.isLst());
        final Lst original = parsed.asLst();
        final Lst result = original.at(jnt(key), jnt(value), Poly.MUTABLE);
        if (expectSame)
            assertSame(original, result, "MUTABLE should return same reference");
        assertEquals(jnt(expectedVal), original.at(jnt(key)), "MUTABLE should mutate original at key");
        assertEquals(expectedCount, original.count());
    }

    @ParameterizedTest
    @CsvSource(value = {
            // MUTABLE delete (value = noobj)
            "[10,20,30] | 1   | 2   | 10 | 30",     // delete middle
            "[10,20,30] | 0   | 2   | 20 | 30",     // delete first
            "[10,20,30] | -1  | 2   | 10 | 20",     // delete last via negative
            "[42]       | 0   | 0   |    |   ",     // delete only → empty
    }, delimiter = '|')
    public void testMutableDelete(final String lstStr, final int key, final int expectedCount,
                                  final String remaining0, final String remaining1) {
        final Obj parsed = mParser.m_obj().parse(lstStr).get();
        assertTrue(parsed.isLst());
        final Lst original = parsed.asLst();
        final Lst result = original.at(jnt(key), noobj(), Poly.MUTABLE);
        assertSame(original, result, "MUTABLE delete should return same reference");
        assertEquals(expectedCount, original.count());
        if (expectedCount > 0)
            assertTrue(mParser.m_obj().parse(remaining0).get().equals(original.at(jnt(0))));
        if (expectedCount > 1)
            assertTrue(mParser.m_obj().parse(remaining1).get().equals(original.at(jnt(1))));
    }

    @ParameterizedTest
    @CsvSource(value = {
            // IMMUTABLE set: should NOT mutate original
            "[1,2,3]   | 1   | 99  | 3   | 99",     // original unchanged, clone gets value
            "[1,2,3]   | -1  | 99  | 3   | 99",     // negative index
    }, delimiter = '|')
    public void testImmutableSet(final String lstStr, final int key, final int value,
                                 final int expectedOrigCount, final int expectedCloneVal) {
        final Obj parsed = mParser.m_obj().parse(lstStr).get();
        assertTrue(parsed.isLst());
        final Lst original = parsed.asLst();
        final Lst clone = original.at(jnt(key), jnt(value), Poly.IMMUTABLE);
        assertNotSame(original, clone, "IMMUTABLE should return new reference");
        assertEquals(expectedOrigCount, original.count());
        assertEquals(jnt(expectedCloneVal), clone.at(jnt(key)), "IMMUTABLE clone should have new value");
    }

    @ParameterizedTest
    @CsvSource(value = {
            // IMMUTABLE delete: should NOT mutate original
            "[10,20,30] | 1   | 3   | 2   | 30",     // delete middle
            "[10,20,30] | -1  | 3   | 2   | 20",     // delete last via negative
    }, delimiter = '|')
    public void testImmutableDelete(final String lstStr, final int key,
                                    final int expectedOrigCount, final int expectedCloneCount,
                                    final String cloneIdx1) {
        final Obj parsed = mParser.m_obj().parse(lstStr).get();
        assertTrue(parsed.isLst());
        final Lst original = parsed.asLst();
        final Lst clone = original.at(jnt(key), noobj(), Poly.IMMUTABLE);
        assertNotSame(original, clone, "IMMUTABLE delete should return new reference");
        assertEquals(expectedOrigCount, original.count());
        assertEquals(expectedCloneCount, clone.count());
        if (expectedCloneCount > 1)
            assertTrue(mParser.m_obj().parse(cloneIdx1).get().equals(clone.at(jnt(1))));
    }

    /**
     * at/atDirect correlate on lists (mirrors the Rec deep-keyed regression): when a
     * list element is itself a deep-keyed rec, the full uri path 0/Echo must surface
     * the raw !@ pointer under atDirect — the atToggle doAuto toggle must propagate
     * through the whole path, not just the first segment.  (Lst uri access wraps its
     * results in an objs, so check by streaming.)
     */
    @Test
    public void testAtDirectDeepPathRespectsRawToggle() {
        final Obj ptr = auto_from_(f("local:src/a_main")).tryToInst();
        final Lst l = lst(rec(uri("usr/marko/Echo"), ptr));
        final Obj direct = l.atDirect(uri("0/Echo"));
        LOG.info("atDirect(uri('0/Echo')) -> %s", direct);
        final boolean rawPointer = direct.stream()
                .map(Obj.Helper::getAutoPointer).anyMatch(Optional::isPresent);
        assertTrue(rawPointer,
                "atDirect('0/Echo') should surface the raw !@ inst of the deep element, got: " + direct);
    }

    /**
     * at()/atDirect() crossing the lst -> rec boundary and back: a lst whose element is
     * a rec whose value holds deeper structure (metatron tree layout).  Uri paths with
     * multiple segments may surface results wrapped in an objs, so assert over stream.
     */
    @Test
    public void testAtCrossesLstRecBoundary() {
        final Lst l = lst(rec(uri("x"), jnt(41)), jnt(7));
        // lst -> rec leaf
        assertTrue(l.at(uri("0/x")).stream().anyMatch(o -> o.equals(jnt(41))),
                "at(0/x) should reach the rec leaf, got: " + l.at(uri("0/x")));
        assertTrue(l.atDirect(uri("0/x")).stream().anyMatch(o -> o.equals(jnt(41))),
                "atDirect(0/x) should reach the raw rec leaf, got: " + l.atDirect(uri("0/x")));
        // lst -> rec -> lst deeper (rec value is another lst)
        final Lst nested = lst(rec(uri("m"), lst(jnt(9), jnt(8))), jnt(7));
        assertTrue(nested.at(uri("0/m/1")).stream().anyMatch(o -> o.equals(jnt(8))),
                "at(0/m/1) should cross lst -> rec -> lst, got: " + nested.at(uri("0/m/1")));
        // plain list element beside the rec
        assertTrue(l.at(uri("1")).stream().anyMatch(o -> o.equals(jnt(7))),
                "at(1) should reach the plain element, got: " + l.at(uri("1")));
    }
}
