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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.TestData;
import studio.phaseshift.metatron.algebra.AbstractAlgebraTest;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.algebra.Form.PLUS_MONOID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.update_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Poly.IMMUTABLE;
import static studio.phaseshift.metatron.isa.m.type.Poly.MUTABLE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

public class RecTest extends AbstractAlgebraTest<Rec> {

    public RecTest() {
        super(rec(uri("a"), jnt(1), uri("b"), jnt(2), uri("c"), jnt(3)), Set.of(PLUS_MONOID));
    }


    @Test
    @Disabled
    public void testDereference() {
        final Rec r = ObjmtronSerializer.parse("[a=>1@x,b=>2]");
        assertEquals(Long.valueOf(1), r.at("a").jvm());
        assertEquals(Long.valueOf(2), r.at("b").jvm());
        assertEquals(Long.valueOf(3), ObjmtronSerializer.parse("@x + 2").apply().jvm());
        assertEquals(Long.valueOf(3), r.at("a").jvm());
        assertEquals(Long.valueOf(2), r.at("b").jvm());
    }


    @ParameterizedTest
    @CsvSource(value = {
            // rec                                 | key                  | value
            "[a=>b]                                | c                    | noobj",
            "[a=>b]                                | a                    | b",
            "[a=>b]                                | a                    | /m/uri::b",
            "[a=>b]                                | a/                   | a=>b",
            "[a=>{b,c}]                            | a/                   | a=>{b,c}",
            // "[a=>noobj]                            | a/                   | noobj",
            "[a=>noobj]                            | a                    | noobj",
            "[=>]                                  | a                    | noobj",
            "[1=>[2=>3]]                           | 1                    | [2=>3]",
            "[1=>[2=>3]]                           | 2                    | noobj",
            "[a=>[b=>c],a/b/c=>[e=>f]]             | a/b/c                | [e=>f]",
            "[a=>[b=>c],a/b/c=>[e=>f]]             | a/b                  | c",
            // "[a=>[b=>c],a/b=>c]               | a/b                  | {c,c}",
            // "[a=>[b=>c],a/b/c=>[e=>f]]             | a/b/c/               | [a/b/c=>[e=>f]].>-{,}",
            "[a=>[b=>c,d=>[e=>f]]]                 | a                    | [b=>c,d=>[e=>f]]",
            "[a=>[b=>c,d=>[e=>f]]]                 | a/                   | a=>[b=>c,d=>[e=>f]]",
            "[a=>[b=>c,d=>[e=>f]]]                 | a/b                  | c",
            "[a=>[b=>c,d=>[e=>f]]]                 | a/d                  | [e=>f]",
            "[a=>[b=>c,d=>[e=>f]]]                 | a/d/e                | f",
            // "[a=>[b=>c,d=>[e=>f]]]                 | a/d/e/               | /m/rel::a/d/e=>f",
            "[a=>[b=>c,d=>[e=>f]]]                 | a/#                  | {c,[e=>f]}",
            "[a=>[b=>c,d=>[e=>f]]]                 | a/+                  | {c,[e=>f]}",
            "[a=>[b=>c,d=>[e=>f]]]                 | a/+/e                | f",
            "[a=>[b=>c,d=>[e=>{1,2,3,4}]]]         | a/+/e                | {1,2,3,4}"
    }, delimiter = '|')
    public void testKeyValue(final String rec, final String key, final String value) {
        Rec r = ObjmtronSerializer.parse(rec);
        Obj k = ObjmtronSerializer.parse(key);
        Obj v = ObjmtronSerializer.parse(value);
        Obj actual = r.at(k);
        LOG.debug("testing %s at %s is %s [expected:%s]", k, r, actual, v);
        assertTrue(r.isRec());
        assertEquals(v, actual);
    }


    @ParameterizedTest
    @CsvSource(value = {
            // rec                                 | key                                        | value
            "[=>]                                  | [a=>b]                                     | false",
            "[a=>b]                                | [=>]                                       | true",
            "[a=>b]                                | [a=>b]                                     | true",
            "[a=>b,c=>d]                           | [a=>b]                                     | true",
            "[a=>b,c=>d]                           | [a=>b,c=>e]                                | false",
            "[a=>b,c=>[d=>2]]                      | [a=>b,c=>[d=>2]]                           | true",
            "[a=>b,c=>[d=>[a=>b]]]                 | [a=>b,c=>[d=>get(a).is(eq(b))]]            | true",
            "[a=>b,c=>[d=>2]]                      | [a=>b,c=>[d=>is(gt(0))]]                   | true",
            "[a=>b,c=>[d=>2]]                      | [a=>b,c=>[d=>is(gt(3))]]                   | false",
            "[a=>b,c=>[d=>2]]                      | [a=>b,c=>[d=>isa(int::T[is(gt(0))])]]      |   true",
            "[a=>b,c=>[d=>2]]                      | [a=>b,c=>[d=>is(gt(10))]]                  | false",
            "[a=>b,c=>[d=>2]]                      | [a=>b,c=>[d=>isa(int::T[is(gt(10))])]]     | false",
            "[a=>b,c=>[d=>2]]                      | [a=>b,c=>[d=>int::T[is(gt(10))]]]          | false",
            "[a=>b,c=>[d=>2]]                      | [a=>b,c=>[d=>int::T[is(gt(1))]]]           | true",
            "[a=>b,c=>[d=>2]]                      | [a=>b,c=>[d=>isa(int::T)]]                 | true",
            "[a=>b,c=>[d=>2]]                      | [a=>b,c=>[d=>isa(str::T)]]                 | false",
            "[a=>b,c=>[d=>2]]                      | [a=>b,c=>rec::T]                           | true",
            "[a=>b,c=>[d=>2]]                      | [a=>uri::T,c=>rec::T]                      | true",
            "[a=>b,c=>[d=>2]]                      | [a=>uri::T[is(eq(b))],c=>rec::T]           | true",
            "[a=>b,c=>[d=>2]]                      | [a=>str::T,c=>rec::T]                      | false",
            "[a=>b,c=>[d=>2]]                      | rec::T                                     | true",
            "[a=>b,c=>[d=>2]]                      | str::T                                     | false",
            "[a=>b,c=>[d=>2]]                      | rec::T[is(rng().count().eq(2))]            | true",
            "[a=>b,c=>[d=>2]]                      | rec::T[is(rng().count().eq(3))]            | false",
            "noobj                                 | ?str::T                                    | false",
            "noobj                                 | str{?}::T                                  | true",
            "[a=>2]                                | [a=>int::T,b=>?str::T]                     | false",
            "[a=>2]                                | [a=>int::T,uri{?}::b=>str::T]              | true",
            "[=>]                                  | [a=>int::T,uri{?}::b=>str::T]              | false",
            "[=>]                                  | [uri{?}::a=>int::T,uri{?}::b=>str::T]      | true",
            "[a=>'bad']                            | [uri{?}::a=>int::T,uri{?}::b=>str::T]      | false",
            "[a=>2,b=>0]                           | [uri{?}::a=>int::T,uri{?}::b=>str::T]      | false",

    }, delimiter = '|')
    public void testMatches(final String recA, final String recB, final boolean matches) {
        AbstractMetatronTest.checkMatches(LOG, recA, recB, matches);
    }


    @ParameterizedTest
    @CsvSource(value = {
            "[a=>1,b=>2,c=>3].as(lst::T)                                                % [(0=>a=>1),(1=>b=>2),(2=>c=>3)]",
            "[a=>1,b=>2,c=>3].as(lst::T).>-.isa(rel::T).count()                         % 3",
            "[a=>1,b=>2,c=>3].as(lst::T).>-.>>.isa(rel::T).count()                      % 3",
            "[a=>1,b=>2,c=>3].as(lst::T).>-.>>.rng().isa(int::T).count()                % 3",
            "[a=>1,b=>2,c=>3].as(lst::T).>-.>>.rng().sum()                              % 6",
            "[a=>1,b=>2,c=>3].as(rec::T)                                                % [a=>1,b=>2,c=>3]",
            "[a=>1,b=>2,c=>3].as(rec::T).as(lst::T)                                     % [(0=>(a=>1)),(1=>(b=>2)),(2=>(c=>3))]",
            "[a=>1,b=>2,c=>3].as(lst::T).as(rec::T)                                     % [0=>(0=>(a=>1)),1=>(1=>(b=>2)),2=>(2=>(c=>3))]",
            "[=>].as(lst::T)                                                            % [,]",
            "[=>].as(rec::T)                                                            % [=>]",
    }, delimiter = '%')
    public void testAs(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "[a=>1,b=>2,c=>3]>>d                                                        % noobj",
            "[a=>1,b=>2,c=>3]>>{15}a                                                    % {15}1",
            "{2}[a=>{3}1,b=>2,c=>3]>>{5}a                                               % {30}1",
            "{1,2}[a=>{2,3}1,b=>2,c=>3]>>{4,5}a                                         % {8,30}1",
            "{1,2}[a=>{2,3}1,b=>2,c=>3]>>{{4,5}a,a,a}                                   % {12,42}1",
            "{1,2}[a=>{-2,3}1,b=>2,c=>3]>>?#{**}<=rec{{4,5}a,a,a}                       % {-12,42}1",
            // "{-1,2}[a=>{-2,3}1,b=>2,c=>3]>>?#{**}<=rec{**}{{4,5}a,a,a}                  % {12,42}1",
            // "{-1,2}[a=>{-2,3}1,b=>2,c=>3]>>?#{**}<=rec{**}{{4,5}a,a,{-1}a}              % {8,30}1",
            "[a=>1,b=>2,c=>3]>>a                                                        % 1",
            "[a=>1,b=>2,c=>3]>>{a,b}                                                    % {1,2}",
            "[a=>1,b=>2,c=>3]>>{a,b,c}                                                  % {1,2,3}",
            "[a=>1,b=>2,c=>3]>>{a,b,c,d}                                                % {1,2,3}",
            "[a=>1,b=>2,c=>3]>>{a,b,c,a}                                                % {int{2}::1,2,3}",
            "[a=>1,b=>2,c=>3]>>a/                                                       % a=>1",
            "[a=>1,b=>2,c=>3]>>b/                                                       % b=>2",
            "[a=>1,b=>2,c=>3]>>{a/,b/}                                                  % {a=>1,b=>2}",
            "[a=>1,b=>2,c=>3]>>{a/,b/,c/}                                               % {a=>1,b=>2,c=>3}",
            "[a=>1,b=>2,c=>3]>>{a/,b/,c/,d/}                                            % {a=>1,b=>2,c=>3}",
            "[a=>1,b=>2,c=>3]>>{a/,b/,c,d}                                              % {a=>1,b=>2,3}",
            "[a=>1,b=>2,c=>3]>>{a,b/,c}                                                 % {1,b=>2,3}",
            "[a=>1,b=>2,c=>3]>>{a,{23}b/,c}                                             % {1,{23}b=>2,3}",
            "[a=>1,b=>2,c=>3]>>{a,{23}b/,c}>>                                           % {23}2",
            "{2}[a=>1,b=>2,c=>3]>>{a,{23}b/,c}>>                                        % {46}2",
            "{2}[a=>1,b=>2,c=>3]>>{a,{23}b/,c}                                          % {{2}1,{46}b=>2,{2}3}",
            "{2}[a=>1,b=>2,c=>3]>>{a,{23}b/,c}                                          % {2}[{1,{23}b=>2,3}]>-",
            "{2}[a=>1,b=>2,c=>3]>>{a,b,c}.<<                                            % {6}[a=>1,b=>2,c=>3]",
            "{2}[a=>1,b=>2,c=>{4}3]>>c                                                  % {8}3",
            // "{2}[a=>1,b=>2,c=>3]>>{a,{23}b/,c}.<<                                       % {25}[a=>1,b=>2,c=>3]", // TODO: review: is this the semantics we want?
    }, delimiter = '%')
    public void testAt(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            // Step walk vs path walk vs hybrid — all equivalent
            "[a=>[b=>[c=>[d=>e]]]]>>a>>b>>c>>d                                        % e",
            "[a=>[b=>[c=>[d=>e]]]]>>a/b/c/d                                           % e",
            "[a=>[b=>[c=>[d=>e]]]]>>a/b>>c>>d                                         % e",
            "[a=>[b=>[c=>[d=>e]]]]>>a>>b/c/d                                          % e",
            "[a=>[b=>[c=>[d=>e]]]]>>a/b/c>>d                                          % e",
            // Deep nesting
            "[a=>[b=>[c=>[d=>[e=>[f=>\"found!\"]]]]]]>>a/b/c/d/e/f                      % \"found!\"",
            "[a=>[b=>[c=>[d=>[e=>[f=>\"found!\"]]]]]]>>a>>b>>c>>d>>e>>f                 % \"found!\"",
            "[a=>[b=>[c=>[d=>[e=>[f=>\"found!\"]]]]]]>>a/b/c>>d/e>>f                    % \"found!\"",
            // Missing mid-path
            "[a=>[b=>[c=>d]]]>>a/b/x                                                    % noobj",
            "[a=>[b=>[c=>d]]]>>a/x/c                                                    % noobj",
            // Leaf is not a poly — can't walk further
            "[a=>[b=>42]]>>a/b/c                                                        % noobj",
            // Mixed poly types — walk through lst in the middle
            "[a=>[b=>[1,2,[3,4,[5,6,7]]]]]>>a/b/2/2/0                                  % 5",
            "[a=>[b=>[1,2,[3,4,[5,6,7]]]]]>>a/b>>2>>2>>0                               % 5",
            // Inter-nesting: path segments traverse into nested poly values
            "[x=>[y=>[z=>value]]]>>x/y/z                                                 % value",
            "[x=>[y=>[z=>value]]]>>x>>y>>z                                               % value",
            // Sibling branches — same depth, different leaves
            "[left=>[a=>[b=>1]],right=>[a=>[b=>2]]]>>left/a/b                           % 1",
            "[left=>[a=>[b=>1]],right=>[a=>[b=>2]]]>>right/a/b                          % 2",
            // Path walks through a value that is itself a rec from a key lookup
            "[outer=>[inner=>[a=>[b=>42]]]]>>outer/inner/a/b                            % 42",
            "[outer=>[inner=>[a=>[b=>42]]]]>>outer>>inner>>a>>b                         % 42",
    }, delimiter = '%')
    public void testPathWalking(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "[a=>[knows=>[b=>[knows=>c]]]]>><a/+/b/knows>                                            % c",
            "(a=>(knows=>(b=>(knows=>c))))>><a/+/b/knows>                                            % c",
            "[a=>[knows=>[b=>[knows=>c]]]]>><a/+/b>                                                  % [knows=>c]",
            "(a=>(knows=>(b=>(knows=>c))))>><a/+/b>                                                  % knows=>c",
            "[a=>[knows=>[b=>[knows=>c]]]]>><a/+>                                                    % [b=>[knows=>c]]",
            "(a=>(knows=>(b=>(knows=>c))))>><a/+>                                                    % b=>knows=>c",
            "[a=>[knows=>[b=>[knows=>c]]]]>><a>                                                      % [knows=>[b=>[knows=>c]]]",
            "(a=>(knows=>(b=>(knows=>c))))>><a>                                                      % knows=>b=>knows=>c",


    }, delimiter = '%')
    public void testRecRelBehaviors(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "[noobj=>noobj]                                                                          % [=>]",
            "[7=>noobj]                                                                              % [=>]",
            "[noobj=>7]                                                                              % [=>]",
            "[a=>1,a=>1,b=>2,a=>1,b=>2]                                                              % [a=>int{3}::1,b=>int{2}::2]",
            "[a=>1,a=>1,b=>2,a=>1,b=>2,b=>3]                                                         % [a=>int{3}::1,b=>{int{2}::2,3}]",
            "[a=>1,a=>1,b=>[1=>2],a=>1,b=>[1=>2],b=>[2=>3]]                                          % [a=>int{3}::1,b=>{rec{2}::[1=>2],[2=>3]}]",
            "[a=>1,a=>1,b=>[1=>2],a=>1,b=>[1=>2],b=>[2=>3]]                                          % [a=>int{3}::1,b=>{rec{2}::[1=>2],[2=>3]}]",
            "[a=>1,a=>1,b=>[1=>2],a=>1,b=>[1=>2],b=>[2=>3],b=>[1=>'a']]                              % [a=>int{3}::1,b=>{rec{2}::[1=>2],[2=>3],[1=>'a']}]",
            "[a=>int{3}::1,b=>[1=>2],b=>[1=>2],b=>[2=>3],b=>[1=>'a']]                                % [a=>int{3}::1,b=>{rec{2}::[1=>2],[2=>3],[1=>'a']}]",
            //"[a=>int{3}::1,b=>[1=>[2=>'a']],b=>[1=>[2=>'b']],b=>[1=>[2=>'c']],b=>[1=>[7=>7]]]        % [a=>int{3}::1,b=>[1=>[2=>{'a','b','c'},7=>7]]]",
            //"[a=>int{3}::1,b=>[1=>[2=>'b']],b=>[1=>[2=>'c']],b=>[1=>[7=>7]],b=>[1=>[7=>int{-1}::7]]] % [a=>int{3}::1,b=>[1=>[2=>{'b','c'}]]]",
            "[a=>is(gt(0)),a=>is(gt(2)),b=>3]                                                        % [a=>-<{is(gt(0)),is(gt(2))},b=>3]",
            "2-<[a=>is(gt(0)),a=>is(gt(2)),b=>3]                                                     % [a=>2,b=>3]",
            "[a=>2,b=>5]==[a=>is(gt(0)),a=>is(gt(2)),b=>3]                                           % [a=>2,b=>3]",
            "2-<[a=>is(gt(0)),b=>3]                                                                  % [a=>2,b=>3]",
            "2-<[a=>is{2}(gt(0)),b=>3]                                                               % [a=>int{2}::2,b=>3]",
            "[a=>is{2}(gt(0)),a=>noobj]                                                              % [a=>is{2}(gt(0))]",
            "[a=>is{3}(gt(0)),a=>is{2}(gt(0)),a=>noobj]                                              % [a=>is{5}(gt(0))]",
            "2-<[a=>is{3}(gt(0)),a=>is{2}(gt(0)),a=>noobj]                                           % [a=>int{5}::2]",
            "2-<[a=>is{3}(gt(0)),a=>is{2}(gt(0)),a=>noobj].rng()                                     % int{5}::2",
            "{2,0}.-<[a=>is{3}(gt(0)),a=>is{2}(gt(0)),a=>noobj].rng()                                 % int{5}::2",
            "{2,5}.-<[a=>is{3}(gt(0)),a=>is{2}(gt(0)),a=>noobj].rng()                                 % {int{5}::5, int{5}::2}",
            "{2,5,0,0}-<[a=>is{3}(gt(0)),a=>is{2}(gt(0)),a=>noobj].rng()                             % {int{5}::5, int{5}::2}",
            //  "{2,5}.barrier([a=>is{3}(gt(0)),a=>is{2}(gt(0)),a=>noobj]))                                      % [a=>{int{5}::5, int{5}::2}]",
            // "{2,5,5,5,0}-<[a=>is{3}(gt(0)),a=>is{2}(gt(0)),a=>noobj])                                % [a=>{int{15}::5, int{5}::2}]",
            //  "{2,2,5,-1}-<[a=>is{3}(gt(0)),a=>is{2}(gt(0)),a=>noobj])                                 % [a=>{int{5}::5, int{10}::2}]",
            "2-<[a=>is(gt(0)),a=>is(gt(0)),b=>3]                                                     % [a=>int{2}::2,b=>3]",
            "2-<[a=>is(gt(0)),a=>is(gt(1)),b=>3]                                                     % [a=>int{2}::2,b=>3]",
            "[1,2,3]-<[>-.is(gt(2)) => >-.is(gt(1)), >-.is(gt(1)) => >-._]                           % [3=>{2,3},{2,3}=>{1,2,3}]",
            "[a=>1,b=>2,c=>3]==[a=>plus(2),b=>_]                                                     % [a=>3,b=>2]",
            "[a=>1,b=>2,c=>3]==[a=>plus(2),b=>plus(10)]                                         % [a=>3,b=>12]",
            "[a=>1,b=>2,c=>3]==[a=>plus(2),b=>plus(10)]==[a=>_,b=>sum()]                        % [a=>3,b=>12]",
            "[a=>1,b=>2,c=>3]==[a=>plus(2),b=>plus(10)]==[a=>_,b=>(-<{count(),sum()})]          % [a=>3,b=>{1,12}]",
            "[a=>1,b=>2,c=>3]==[a=>plus(2),b=>plus(10)]==[a=>_,b=>-<[count(),sum()]]            % [a=>3,b=>[1,12]]",
            "[a=>1,b=>2,c=>3]==[a=>plus(2),b=>plus(10)]==[a=>_,b=>-<[count(),sum()]>-]          % [a=>3,b=>{1,12}]",
            "[a=>1,b=>2,c=>3]==[a=>plus(2),b=>plus(10)]==[a=>_,b=>-<[count(),sum()]>-.count()]  % [a=>3,b=>2]",
            //"[a=>1,b=>2,c=>3]==[a=>plus(2),map(b)=>plus(10)]==[a=>_,b=>(-<[count(),sum()]>-.sum().sum())]    % [a=>3,b=>39]",
            //"[a=>1,b=>2,c=>3]==[a=>plus(2),map(b)=>plus(10)]==[a=>_,b=>-<[count(),sum()]>-.sum().sum()]     % [a=>3,b=>39]",
            "[1,2,3].-<[>-.is(gt(2)) => >-.is(gt(1))>-?<=int{*}[,], >-.is(gt(1)) => _/id()\\_]       % [3=>[2,3],{2,3}=>[1,2,3]]",
    }, delimiter = '%')
    public void testCode(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @Test
    public void testRecJavaAPI() {
        Rec r = rec(uri("a"), jnt(1), uri("b"), rec(uri("c"), jnt(3)));
        Graphitty.log(this).trace(r);
        assertEquals(jnt(1), r.at("a"));
        assertEquals(2, r.count());
        assertEquals(1, r.<Rec>at("b").count());
        assertEquals(jnt(3), r.<Rec>at("b").at("c"));
        /// //
        r = r.at("b/c", str("fhat"));
        Graphitty.log(this).trace(r);
        assertEquals(jnt(1), r.at("a"));
        assertEquals(2, r.count());
        assertEquals(1, r.<Rec>at("b").count());
        assertEquals(str("fhat"), r.<Rec>at("b").at("c"));
        /// ///
        r = r.at("d", real(1.0));
        assertEquals(1.0, r.at("d").realValue(), 0.001);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "[a=>1,b=>2,c=>3].reverse()                                                      % [c=>3,b=>2,a=>1]",
            "[x=>12,y=>bac,z=>25].reverse()                                                  % [z=>25,y=>bac,x=>12]",
            "[first=>abc,second=>def,third=>ghi].reverse()                                   % [third=>ghi,second=>def,first=>abc]",
            "[a=>'abc',b=>'def',c=>'ghi'].reverse()==[_=>reverse()]                          % [c=>'ihg',b=>'fed',a=>'cba']",
            "[a=>x,b=>[c=>1,d=>2],e=>[f=>3,g=>4]].reverse()                                   % [e=>[f=>3,g=>4],b=>[c=>1,d=>2],a=>x]",
            "[a=>x,b=>[c=>1,d=>2],e=>[f=>3,g=>4]].reverse()==[_=>reverse()]                   % [e=>[g=>4,f=>3],b=>[d=>2,c=>1],a=>x]",
            "[a=>x,b=>[c=>1,d=>2],e=>y].reverse()==[_=>reverse()]                            % [e=>y,b=>[d=>2,c=>1],a=>x]",
            "[=>].reverse()                                                                  % [=>]",
            "[a=>1].reverse()                                                                % [a=>1]",
            //   "[a=>1,b=>2].reverse().reverse()                                                 % [a=>1,b=>2]",
    }, delimiter = '%')
    public void testReverse(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @Test
    public void testMutableImmutable() {
        Rec r1 = rec(uri("a"), jnt(1), uri("b"), rec(uri("c"), jnt(3)));
        Rec r2 = r1.at(uri("b"), jnt(22), IMMUTABLE);
        Rec r3 = r1.at(uri("b")).<Rec>as().at(uri("d"), jnt(33), IMMUTABLE);
        Rec r4 = r1.at(uri("b"), r1.at(uri("b")).<Rec>as().at(uri("d"), jnt(33)), IMMUTABLE);
        AbstractMetatronTest.checkEquality(LOG, rec(uri("a"), jnt(1), uri("b"), rec(uri("c"), jnt(3))), r1, true);
        AbstractMetatronTest.checkEquality(LOG, rec(uri("a"), jnt(1), uri("b"), jnt(22)), r2, true);
        AbstractMetatronTest.checkEquality(LOG, rec(uri("c"), jnt(3), uri("d"), jnt(33)), r3, true);
        AbstractMetatronTest.checkEquality(LOG, rec(uri("a"), jnt(1), uri("b"), rec(uri("c"), jnt(3), uri("d"), jnt(33))), r4, true);
        /// //
        Rec rr1 = rec(uri("a"), jnt(1), uri("b"), rec(uri("c"), jnt(3)));
        Rec s1 = rec(uri("a"), jnt(1), uri("b"), rec(uri("c"), jnt(3)));
        AbstractMetatronTest.checkEquality(LOG, r1, s1, true);
        Rec s2 = r1.at(uri("b"), jnt(22), MUTABLE);
        AbstractMetatronTest.checkEquality(LOG, r2, s2, true);
        Rec s3 = s1.at(uri("b")).<Rec>as().at(uri("d"), jnt(33), MUTABLE);
        AbstractMetatronTest.checkEquality(LOG, r3, s3, true);
        Rec s4 = rr1.clone().<Rec>as().at(uri("b"), rr1.at(uri("b")).clone().<Rec>as().at(uri("d"), jnt(33), IMMUTABLE), MUTABLE);
        AbstractMetatronTest.checkEquality(LOG, r4, s4, true);
        AbstractMetatronTest.checkEquality(LOG, rec(uri("a"), jnt(1), uri("b"), rec(uri("c"), jnt(3))), rr1, true);
        AbstractMetatronTest.checkEquality(LOG, rec(uri("a"), jnt(1), uri("b"), jnt(22)), s2, true);
        AbstractMetatronTest.checkEquality(LOG, rec(uri("c"), jnt(3), uri("d"), jnt(33)), s3, true);
        AbstractMetatronTest.checkEquality(LOG, rec(uri("a"), jnt(1), uri("b"), rec(uri("c"), jnt(3), uri("d"), jnt(33))), s4, true);
    }

    @ParameterizedTest
    @CsvSource(value = {
            // MUTABLE set: mutate original in-place
            "[a=>1,b=>2,c=>3]   | a   | 99  | 3   | 99  | true",     // existing key
            "[a=>1,b=>2]        | c   | 42  | 3   | 42  | true",     // new key
            "[x=>10]            | x   | -1  | 1   | -1  | true",     // overwrite
    }, delimiter = '|')
    public void testMutableSet(final String recStr, final String key, final int value,
                               final int expectedCount, final int expectedVal,
                               final boolean expectSame) {
        final Rec original = ObjmtronSerializer.parse(recStr);
        final Rec result = original.at(uri(key), jnt(value), MUTABLE);
        if (expectSame)
            assertSame(original, result, "MUTABLE should return same reference");
        assertEquals(expectedCount, original.count());
        assertEquals(jnt(expectedVal), original.at(uri(key)), "MUTABLE should mutate original");
    }

    @ParameterizedTest
    @CsvSource(value = {
            // IMMUTABLE delete: return new rec, original untouched
            "[a=>10,b=>20,c=>30]  | b   | 3   | 2",     // delete middle
            "[x=>42]              | x   | 1   | 0",     // delete only
    }, delimiter = '|')
    public void testImmutableDelete(final String recStr, final String key,
                                    final int expectedOrigCount, final int expectedCloneCount) {
        final Rec original = ObjmtronSerializer.parse(recStr);
        final Rec clone = original.at(uri(key), noobj(), IMMUTABLE);
        assertNotSame(original, clone, "IMMUTABLE delete should return new reference");
        assertEquals(expectedOrigCount, original.count());
        assertEquals(expectedCloneCount, clone.count());
        assertTrue(original.at(uri(key)).equals(original.at(uri(key))), "original should still have key");
        assertTrue(clone.at(uri(key)).isNoObj(), "clone should not have deleted key");
    }

    @ParameterizedTest
    @CsvSource(value = {
            // MUTABLE delete: remove from original in-place
            "[a=>10,b=>20,c=>30]  | b   | 2",     // delete middle
            "[a=>10,b=>20,c=>30]  | a   | 2",     // delete first
            "[x=>42]              | x   | 0",     // delete only → empty
    }, delimiter = '|')
    public void testMutableDelete(final String recStr, final String key,
                                  final int expectedCount) {
        final Rec original = ObjmtronSerializer.parse(recStr);
        final Rec result = original.at(uri(key), noobj(), MUTABLE);
        assertSame(original, result, "MUTABLE delete should return same reference");
        assertEquals(expectedCount, original.count());
        assertTrue(original.at(uri(key)).isNoObj(), "MUTABLE delete should remove key");
    }

    @ParameterizedTest
    @CsvSource(value = {
            "[a=>1,b=>2].where([a=>?>0])                              % true",
            "[a=>1,b=>2].where([a=>?>1])                              % false",
            "[a=>1,b=>2].where([a=>?>0,b=>?>1])                       % true",
            "[a=>1,b=>2].where([a=>?>0]).where([b=>?>2])              % false",
            "[a=>1,b=>2].where([a=>?>0,b=>?>2])                       % false",
            "[a=>1,b=>2].where([a=>?>0,b=>?>0])                       % true",
            "[a=>1,b=>2].where([a=>?>0,b=>?>0,c=>?>0])                % false",
            "[=>].where([a=>?>0])                                     % false",
            /// ///////////////////////////////////////////////////////
            "[a=>1,b=>2].where([a=>?>0])                            % true",
            "[a=>1,b=>2].where([a=>?>1])                            % false",
            "[a=>1,b=>2].where([a=>?>0,b=>?>1])                     % true",
            "[a=>1,b=>2].where([a=>?>0,b=>?>2])                     % false",
            "[a=>1,b=>2].where([a=>?>0,b=>?>0])                     % true",
            "[a=>1,b=>2].where([a=>?>0,b=>?>0,c=>?>0])              % false",
            "[a=>[b=>1,c=>2]].where([a=>isa(rec::T)])               % true",
            "[a=>[b=>1,c=>2]].where([a=>isa(lst::T)])               % false",
    }, delimiter = '%', quoteCharacter = '~')
    public void testHas(final String code, final boolean matches) {
        final Obj codeObj = ObjmtronSerializer.parse(code);
        LOG.debug("testing has %s [expected:%s]", codeObj, matches);
        if (matches)
            assertFalse(codeObj.apply().isNoObj());
        else
            assertTrue(codeObj.apply().isNoObj());
    }

    @ParameterizedTest
    @CsvSource(value = {
            "a                                                  % a                       % true",
            "1                                                  % 1.0                     % false",
            "[1,2,3]                                            % [1,2,3]                 % true",
            "[a=>1]                                             % [a=>1,b=>[c=>2,d=>5]]   % false",
            "[a=>1,b=>[c=>2,d=>5]]                              % [a=>1,b=>[c=>2,d=>5]]   % true",
            "[a=>1,b=>[c=>2,e=>3]]                              % [a=>1,b=>[c=>3,d=>5]]   % false",
            "[a=>1,b=>[2,3]]                                    % [a=>1,b=>[2,3,4]]       % false"
    }, delimiter = '%')
    public void testDiffRecRecursion(final String a, final String b, final boolean matches) {
        final Obj aobj = mParser.m_obj().parse(a).get();
        final Obj bobj = mParser.m_obj().parse(b).get();
        LOG.debug("testing match difference:\n%s", Poly.Helper.diffObjRecursion(aobj, bobj));
        if (matches)
            assertFalse(Poly.Helper.diffObjRecursion(aobj, bobj).toString().contains("X=>"));
        else
            assertTrue(Poly.Helper.diffObjRecursion(aobj, bobj).toString().contains("X=>"));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "[a=>1,b=>2,c=>3].merge()                                                    % {(a=>1),(b=>2),(c=>3)}",
            "[a=>1,b=>2].merge()                                                         % {(a=>1),(b=>2)}",
            "[=>].merge()                                                                % {,}",
            "[a=>[b=>1,c=>2]].merge()                                                    % {(a=>[b=>1,c=>2])}",
    }, delimiter = '%')
    public void testMerge(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "[a=>1,b=>2,c=>3].dom()                                                      % {a,b,c}",
            "[a=>1,b=>2].dom()                                                           % {a,b}",
            "[=>].dom()                                                                  % {,}",
            "[x=>10,y=>20,z=>30].dom()                                                   % {x,y,z}",
    }, delimiter = '%')
    public void testDom(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "[a=>1,b=>2,c=>3].rng()                                                      % {1,2,3}",
            "[a=>1,b=>2].rng()                                                           % {1,2}",
            "[=>].rng()                                                                  % {,}",
            "[x=>10,y=>20,z=>30].rng()                                                   % {10,20,30}",
            "[a=>[b=>1],c=>[d=>2]].rng()                                                 % {[b=>1],[d=>2]}",
    }, delimiter = '%')
    public void testRng(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "[a=>1,b=>2].plus([c=>3])                                                    % [a=>1,b=>2,c=>3]",
            "[a=>1,b=>2].plus([b=>3,c=>4])                                               % [a=>1,b=>{2,3},c=>4]",
            "[a=>1].plus([a=>2])                                                         % [a=>{1,2}]",
            "[=>].plus([a=>1])                                                           % [a=>1]",
            "[a=>1].plus([=>])                                                           % [a=>1]",
            "[=>].plus([=>])                                                             % [=>]",
    }, delimiter = '%')
    public void testPlus(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "[a=>1,b=>2,c=>3]>-.count()                                                    % 3",
            "[a=>1,b=>2]>-.count()                                                         % 2",
            "[a=>1].count()                                                                % 1",
            "[=>]>-.count()                                                                % 0",
            "[a=>[b=>1,c=>2],d=>3]>-.count()                                               % 2",
    }, delimiter = '%')
    public void testCount(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @TestData(value = {
            "x -> [address/home/city=>\"santa fe\",address/work/city=>\"nomansland\"]",
            "y -> [address/home/city=>\"santa fe\",address/work/city=>\"santa fe\"]"})
    //  "y -> [address/home/city=>\"santa fe\",address/work/city=>>>(address/home/city)]"})
    @CsvSource(value = {
            "[a=>1,b=>2,c=>3].select([a=>_,b=>_])                                          % [a=>1,b=>2]",
            "[a=>1,b=>2,c=>3].select([a=>_])                                               % [a=>1]",
            "[a=>1,b=>2,c=>3].select([d=>_])                                               % noobj",
            "[a=>1,b=>2,c=>3].select([a=>_,d=>_])                                          % [a=>1]",
            "*x==address/home/city                                                         % \"santa fe\"",
            "*x==address/work/city                                                         % \"nomansland\"",
            "*x==<address/+/city>                                                          % {\"nomansland\",\"santa fe\"}",
            "*x==<address/+/city/>                                                         % {address/home/city=>\"santa fe\",address/work/city=>\"nomansland\"}",
            "*x==<address/+/#/>                                                            % {address/home/city=>\"santa fe\",address/work/city=>\"nomansland\"}",
            "*x==#/                                                                        % {address/home/city=>\"santa fe\",address/work/city=>\"nomansland\"}",
            "*x==[address/+/city=>_]                                                  % [address/+/city=>{\"nomansland\",\"santa fe\"}]",
            "*x==[address/+/+=>_]                                                     % [address/+/+=>{\"nomansland\",\"santa fe\"}]",
            "*x==[address/#=>_]                                                       % [address/#=>{\"nomansland\",\"santa fe\"}]",
            "*x==[#=>_]                                                               % [#=>{\"nomansland\",\"santa fe\"}]",
            "*x==[address/+/city/=>_]                                                 % [address/+/city/=>{address/work/city=>\"nomansland\",address/home/city=>\"santa fe\"}]",
            "*x==[<address/work/city/../../home/city>=>_].rng()                       % \"santa fe\"",
            "*x==[<address/work/home/../../work/city>=>_]>>                           % \"nomansland\"",
            "*x==[<address/work/city/../../+/city>=>_]>>                              % {\"nomansland\",\"santa fe\"}",
            "*x==[<address/work/city/../../+/city/../../../#>=>_]>>                   % {\"nomansland\",\"santa fe\"}",
            "*y==address/work/city                                                         % \"santa fe\""
    }, delimiter = '%')
    public void testSelect(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @TestData(value = {
            "x -> [a => [b => {!*y,!*z}]]",
            "y -> [c => [d => [1,2,3]]]",
            "z -> [c => [d => [4,5,[e,f,g]]]]"})
    @CsvSource(value = {
            // Inter-poly path traversal with objs fan-out at b
            "*x/a/b/c/d/0                                                                   % {1,4}",
            "*x/a/b/c/d/2                                                                   % {3,[e,f,g]}",
            "*x/a/b/c/d                                                                     % {[1,2,3],[4,5,[e,f,g]]}",
            "*x/a/b/c                                                                       % {[d=>[1,2,3]],[d=>[4,5,[e,f,g]]]}",
            // Step-walk equivalent
            "*x==a==b==c==d==0                                                              % {1,4}",
            "*x==a/b/c/d==0                                                                 % {1,4}",
            "*x==a/b/c/d/0                                                                  % {1,4}",
            "*x==a/b==c/d==0                                                                % {1,4}",
            "*x>>a>>b>>c>>d>>2                                                              % {3,[e,f,g]}",
            "*x>>a>>b>>c>>d>>2>>0                                                           % e",
    }, delimiter = '%')
    public void testInterPolyTraversal(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "[a=>1,b=>2,c=>3].sum()                                                      % [a=>1,b=>2,c=>3]",
            "{[a=>1],[b=>2]}.sum()                                                       % [a=>1,b=>2]",
            "{[a=>1],[a=>2]}.sum()                                                       % [a=>{1,2}]",
            "{[a=>1],[a=>2],[a=>3,b=>4]}.sum()                                           % [a=>{1,2,3},b=>4]",
            "{[a=>1,b=>2],[c=>3,d=>4]}.sum()                                             % [a=>1,b=>2,c=>3,d=>4]",
    }, delimiter = '%')
    public void testSum(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "[a=>1,b=>2,c=>3]                            % rec::T                       % true",
            //"[a=>1]                                      % rec[uri=>int]::T             % true",
            //"[a=>1,b=>2,c=>3]                            % rec[uri=>int]::T             % true",
            //"[=>]                                        % rec[#{?}=>#{?}]::T           % true",
            //"[a=>1]                                      % rec[#{?}=>#{?}]::T           % true",
            "[a=>1]                                      % rec[#=>#]::T                 % true",
            "[a=>1]                                      % rec[int=>int]::T             % false",
            "[a=>1]                                      % rec[#=>int]::T               % true",
            //"[a=>1]                                      % rec[uri=>#]::T               % true"
    }, delimiter = '%')
    public void testPoly(final String record, final String type, final boolean matches) {
        final Type t = ObjmtronSerializer.parse(type);
        LOG.debug("%s", t);
        AbstractMetatronTest.checkMatches(LOG, record, type, matches);
    }

    @ParameterizedTest
    @TestData(value = {"xyz -> [x=>[y=>1,z=>2]]@xyz"})
    @CsvSource(value = {
            "[a=>1,b=>2]                            % [a=>3]                %  [a=>3,b=>2]",
            "[a=>1,b=>2]                            % [a=>+3]               %  [a=>4,b=>2]",
            "[a=>1,b=>2]                            % [a=>+7,b=>+1]         %  [a=>8,b=>3]",
            "[a=>1,b=>[c=>3]]                       % [a=>+2,b=>[c=>+10]]   %  [a=>3,b=>[c=>13]]",
            "[a=>1,b=>[c=>3]]                       % [b=>_]                %  [a=>1,b=>[c=>3]]",
            "[a=>1,b=>[c=>3]]                       % [b=>3]                %  [a=>1,b=>3]",
            "[a=>1,b=>[c=>3]]                       % [b=>[c=>[d=>e]]]      %  [a=>1,b=>[c=>[d=>e]]]",
            "[a=>!*xyz]                             % [a=>_]                %  [a=>[x=>[y=>1,z=>2]]]",
            "[a=>1,b=>[c=>!*xyz]]                   % [b=>_]                %  [a=>1,b=>[c=>!*xyz]]",
            "[a=>1,b=>[c=>!*xyz]]                   % [b=>[c=>2]]           %  [a=>1,b=>[c=>2]]",
            // TODO: equality issue    "[a=>1,b=>[c=>!*xyz]]                   % [a=>1,b=>[c=>^*]]     %  [a=>1,b=>[c=>!*xyz]]",
            // "[a=>1,b=>[c=>!*xyz]]                   % [b=>[c=>[!*xyz]]]     %  [a=>1,[b=>[c=>[!*xyz]]]]",
            "[a=>1,b=>[c=>2]]                       % [b=>[c=>[!*xyz]]]     %  [a=>1,b=>[c=>[!*xyz]]]",
            "[a=>1,b=>[c=>2]]                       % [b=>[c=>!*xyz]]       %  [a=>1,b=>[c=>[x=>[y=>1,z=>2]]]]",
            "[a=>1,b=>[c=>[1,2,3]]]                 % [b=>[c=>[2,_,_]]]     %  [a=>1,b=>[c=>[2,2,3]]]",
            "[a=>1,b=>[c=>[1,2,3]]]                 % [b=>[c=>[2,+23,+2]]]  %  [a=>1,b=>[c=>[2,25,5]]]",
            "[a=>1,b=>[c=>[1,!*xyz,3]]]             % [b=>[c=>[2,_,+2]]]     %  [a=>1,b=>[c=>[2,[x=>[y=>1,z=>2]],5]]]",
            "[a=>1,b=>[c=>[1,!*xyz,3]]]             % [b=>[c=>[2,7,+2]]]     %  [a=>1,b=>[c=>[2,7,5]]]",
            "[a=>1,b=>[c=>[1,_,3]]]                 % [b=>[c=>[2,!*xyz,+2]]] %  [a=>1,b=>[c=>[2,[x=>[y=>1,z=>2]],5]]]",
            // "[a=>1,b=>[c=>3]]                       % [b=>noobj]            %  [a=>1]"
    }, delimiter = '%')
    public void testUpdate(final String original, final String update, final String expected) {
        final Rec originalRec = ObjmtronSerializer.parse(original);
        final Rec updateRec = ObjmtronSerializer.parse(update);
        final Rec expectedRec = ObjmtronSerializer.parse(expected);
        final Rec actualRec = update_(updateRec).apply(originalRec).as();
        AbstractMetatronTest.checkEquality(LOG, expectedRec, actualRec, true);
    }

    // =========================================================================
    //  Algebraic Operator Matrix — in-memory rec operations (no spaces)
    // =========================================================================

    // ── + (PLUS) ── structural merge, never computes ──

    @ParameterizedTest(name = "[{index}] + : {0}  =>  {1}")
    @CsvSource(value = {
            // overlap: same-key same-type → Objs
            "[a=>1] + [a=>2]                                   % [a=>{1,2}]",
            "[a=>1] + [a=>1]                                   % [a=>{1,1}]",
            // overlap: same-key diff-type → Objs (instruction computed)
            "[a=>1] + [a=>+3]                                  % [a=>{1,plus(3)}]",
            "[a=>1] + [a=>_]                                   % [a=>{1,id()}]",
            "[a=>1]>>=[a=>+3]                                  % [a=>4]",
            "[a=>1]>>=[a=>_]                                   % [a=>1]",
            "[a=>1]>>=+[a=>+3]                                 % [a=>{1,plus(3)}]",
            "[a=>1]>>=+[a=>_]                                  % [a=>{1,id()}]",
            // overlap: Objs + Objs → flat merge
            "[a=>{1,2}] + [a=>{3,4}]                           % [a=>{1,2,3,4}]",
            "[a=>{1}] + [a=>{2,3}]                             % [a=>{1,2,3}]",
            // no overlap → field-add
            "[a=>1] + [b=>2]                                   % [a=>1,b=>2]",
            "[a=>1,b=>2] + [c=>3,d=>4]                         % [a=>1,b=>2,c=>3,d=>4]",
            // mix overlap + new fields
            "[a=>1,b=>2] + [b=>3,c=>4]                         % [a=>1,b=>{2,3},c=>4]",
            "[a=>1,b=>2] + [a=>3,c=>4]                         % [a=>{1,3},b=>2,c=>4]",
            // empty recs
            "[=>] + [a=>1]                                     % [a=>1]",
            "[a=>1] + [=>]                                     % [a=>1]",
            "[=>] + [=>]                                       % [=>]",
            // nested recs — structural merge, no deep recursion
            "[a=>[b=>1]] + [a=>[c=>2]]                         % [a=>{[b=>1],[c=>2]}]",
            "[a=>[b=>1,c=>2]] + [a=>[b=>3]]                    % [a=>{[b=>1,c=>2],[b=>3]}]",
            // multi-key
            "[a=>1] + [b=>2] + [c=>3]                          % [a=>1,b=>2,c=>3]",
            // wildcard key _ (doesn't match any literal key → added as new field)
            "[a=>1,b=>2,c=>3] + [_=>+2]                        % [a=>1,b=>2,c=>3,id()=>plus(2)]",
            // ── deep nesting ──
            "[a=>[b=>1]] + [a=>[b=>2]]                         % [a=>{[b=>1],[b=>2]}]",
            "[a=>[b=>[c=>1,d=>2]]] + [a=>[b=>[e=>3]]]           % [a=>{[b=>[c=>1,d=>2]],[b=>[e=>3]]}]",
            "[a=>[b=>[c=>1]]] + [a=>[b=>[c=>2]]]                % [a=>{[b=>[c=>1]],[b=>[c=>2]]}]",
            "[a=>[b=>1,c=>2]] + [a=>[b=>3,d=>4]]               % [a=>{[b=>1,c=>2],[b=>3,d=>4]}]",
            "[a=>[b=>1]] + [a=>[b=>+2]]                         % [a=>{[b=>1],[b=>plus(2)]}]",
    }, delimiter = '%')
    public void testRecPlus(final String expression, final String expected) {
        final Obj result = ObjmtronSerializer.parse(expression).apply();
        final Obj expectedObj = ObjmtronSerializer.parse(expected);
        assertEquals(expectedObj, result, expression);
    }

    // ── == (SELECT) ── matches keys in BOTH LHS and RHS, replaces values,
    // drops LHS-only keys, drops RHS-only keys, returns noobj when nothing matches.
    // RHS values that are instructions compute against the LHS value.

    @ParameterizedTest(name = "[{index}] == : {0}  =>  {1}")
    @CsvSource(value = {
            // literal match → value replaced with RHS value
            "[a=>1] == [a=>1]                                  % [a=>1]",
            "[a=>1] == [a=>2]                                  % [a=>2]",
            "[a=>1,b=>2] == [a=>1]                             % [a=>1]",
            // instruction on matching field → computes
            "[a=>1] == [a=>+3]                                 % [a=>4]",
            "[a=>1] == [a=>+1]                                 % [a=>2]",
            // wildcard key _ matches all → instruction on every field
            "[a=>1] == [_=>+2]                                 % [a=>3]",
            "[a=>1,b=>2,c=>3] == [_=>+1]                       % [a=>2,b=>3,c=>4]",
            "[a=>1,b=>2,c=>3] == [_=>+2]                       % [a=>3,b=>4,c=>5]",
            // no matching keys → noobj
            "[a=>1] == [b=>2]                                  % noobj",
            "[a=>1,b=>2] == [c=>3]                             % noobj",
            "[=>] == [a=>1]                                    % noobj",
            "[a=>1] == [=>]                                    % noobj",
            // partial match (LHS-only keys dropped)
            "[a=>1,b=>2,c=>3] == [a=>+1,c=>+10]                % [a=>2,c=>13]",
            // nested SELECT: LHS-only keys dropped at each level
            "[a=>[b=>1,c=>2]] == [a=>[b=>+1]]                  % [a=>[b=>2]]",
            "[a=>[b=>1,c=>[d=>2]]] == [a=>[c=>[d=>+3]]]         % [a=>[c=>[d=>5]]]",
            // string compute
            "[a=>'hello'] == [a=>+' world']                    % [a=>'hello world']",
            // ── deep nesting ──
            "[a=>[b=>[c=>1,d=>2]]] == [a=>[b=>[c=>+1]]]         % [a=>[b=>[c=>2]]]",
            "[a=>[b=>[c=>1,d=>2]]] == [a=>[b=>[c=>+1,d=>+10]]]   % [a=>[b=>[c=>2,d=>12]]]",
            "[a=>[b=>[c=>1]]] == [a=>[b=>[c=>_]]]                % [a=>[b=>[c=>1]]]",
            // wildcard at depth
            "[a=>[b=>1,c=>2]] == [a=>[_=>+1]]                   % [a=>[b=>2,c=>3]]",
    }, delimiter = '%')
    public void testRecSelect(final String expression, final String expected) {
        final Obj result = ObjmtronSerializer.parse(expression).apply();
        final Obj expectedObj = ObjmtronSerializer.parse(expected);
        assertEquals(expectedObj, result, expression);
    }

    // ── >>= (UPDATE) ── SELECT semantics, same as == for in-memory ──

    @ParameterizedTest(name = "[{index}] >>= : {0}  =>  {1}")
    @CsvSource(value = {
            // same as SELECT for matching fields
            "[a=>1] >>= [a=>+3]                                % [a=>4]",
            "[a=>1,b=>2] >>= [a=>+1]                           % [a=>2,b=>2]",
            "[a=>1,b=>2,c=>3] >>= [b=>+10]                     % [a=>1,b=>12,c=>3]",
            // RHS-only field dropped (SELECT semantics)
            "[a=>1] >>= [b=>2]                                 % [a=>1]",
            "[a=>1,b=>2] >>= [c=>3]                            % [a=>1,b=>2]",
            // + prefix on RHS → merge bypasses SELECT
            "[a=>1] >>= +[b=>2]                                % [a=>1,b=>2]",
            "[a=>0] >>= +[a=>1]                                % [a=>{0,1}]",
            "[a=>0,c=>3] >>= +[a=>1,b=>2]                      % [a=>{0,1},b=>2,c=>3]",
            // field delete via none
            "[a=>1,b=>2] >>= [a=>none]                         % [b=>2]",
            "[a=>1,b=>2] >>= [a=>none,b=>none]                 % [=>]",
            // no matching keys → no-op
            "[a=>1] >>= [b=>+1]                                % [a=>1]",
            "[a=>1] >>= [=>]                                   % [a=>1]",
            // nested update
            "[a=>[b=>0]] >>= [a=>[b=>+1]]                      % [a=>[b=>1]]",
            "[a=>[b=>0,c=>2]] >>= [a=>[b=>+1]]                 % [a=>[b=>1,c=>2]]",
            // nested + on sub-rec → merge at inner level
            "[a=>[c=>2]] >>= [a=>+[b=>65]]                      % [a=>[b=>65,c=>2]]",
            // + on sub-rec with overlapping key that's an instruction
            "[a=>[b=>0,c=>2]] >>= [a=>+[b=>+1]]                % [a=>[b=>{0,plus(1)},c=>2]]",
            "[a=>[b=>3,c=>2]] >>= [a=>[b=>+1]]                 % [a=>[b=>4,c=>2]]",
            // string compute
            "[a=>'hello'] >>= [a=>+' world']                   % [a=>'hello world']",
            "[a=>'hello'] >>= +[a=>' world']                   % [a=>{'hello',' world'}]",
            "[a=>'hello'] >>= +[a=>' world'] >>= [a=>sum()]    % [a=>'hello world']",
            // ── deep nesting ──
            "[a=>[b=>[c=>1,d=>2]]] >>= [a=>[b=>[c=>+1]]]         % [a=>[b=>[c=>2,d=>2]]]",
            "[a=>[b=>[c=>1,d=>2]]] >>= [a=>[b=>[c=>+1,d=>+10]]]  % [a=>[b=>[c=>2,d=>12]]]",
            "[a=>[b=>[c=>1,d=>2]]] >>= [a=>[b=>[e=>3]]]          % [a=>[b=>[c=>1,d=>2]]]",
            "[a=>[b=>[c=>1]]] >>= [a=>+[b=>[e=>3]]]              % [a=>[b=>{[c=>1],[e=>3]}]]",
            "[a=>[b=>[c=>1]]] >>= [a=>[b=>+[e=>3]]]              % [a=>[b=>[c=>1,e=>3]]]",
    }, delimiter = '%')
    public void testUpdateOperator(final String expression, final String expected) {
        final Obj result = ObjmtronSerializer.parse(expression).apply();
        final Obj expectedObj = ObjmtronSerializer.parse(expected);
        assertEquals(expectedObj, result, expression);
    }

    // ── >- (MERGE) ── coalesces Objs into a poly, returns Objs if not coalescable

    @ParameterizedTest(name = "[{index}] >- : {0}  =>  {1}")
    @CsvSource(value = {
            // merge coalesces objs — the result is an Objs, not an Lst
            "{1,2} >-                                         % {1,2}",
            "{[a=>1],[b=>2]} >-                                % {a=>1,b=>2}",
            // empty
            "noobj >-                                          % noobj",
    }, delimiter = '%')
    public void testRecMerge(final String expression, final String expected) {
        final Obj result = ObjmtronSerializer.parse(expression).apply();
        final Obj expectedObj = ObjmtronSerializer.parse(expected);
        assertEquals(expectedObj, result, expression);
    }

    // ── -< (SPLIT) ──

    @ParameterizedTest(name = "[{index}] -< : {0}  =>  {1}")
    @CsvSource(value = {
            // split applies branches independently
            "[a=>1,b=>2]-<[>>a,>>b]                            % [1,2]",
            "[a=>1,b=>2]-<{>>a,>>b}                            % {1,2}",
            "[a=>1,b=>2]-<[>>a.+1,>>b.+1]                      % [2,3]",
            "[a=>1,b=>2]-<{>>a.+1,>>b.+1}                      % {2,3}",
            // split nested branches
            "[a=>1,b=>[c=>3]]-<[>>a +1,>>b/c.+2]               % [2,5]",
            "[a=>1,b=>[c=>3]]-<[>>a +1,>>b>>c.+2]              % [2,5]",
            "[a=>1,b=>[c=>3]]-<[>>a +1,>>.>>.+2]              % [2,5]",
            "[a=>1,b=>[c=>3]]-<{>>a +1,>>b/c +2}               % {2,5}",
            // single branch
            "[a=>1]-<[>>a +1]                                   % [2]",
            "[a=>1]-<?lst[int]<=rec([>>a.+1])                   % [2]",
            "[a=>1]-<?lst[int{+}]<=rec([>>a.+1])                % [2]",
            "[a=>1]-<?lst[int{*}]<=rec([>>a.+1])                % [2]",
            "[a=>1]-<?lst[int{0}]<=rec([>>a.+1])                % <ERROR>",
            // "[a=>1]-<?int{*}<=rec({>>a.+1})                     % 2",
    }, delimiter = '%')
    public void testSplit(final String expression, final String expected) {
        final Obj result = ObjmtronSerializer.parse(expression).apply();
        final Obj expectedObj = ObjmtronSerializer.parse(expected);
        if (expected.equals("<ERROR>")) {
            assertTrue(result.isFail());
        } else
            assertEquals(expectedObj, result, expression);
    }

    // ── =?= (WHERE) ──

    @ParameterizedTest(name = "[{index}] =?= : {0}  =>  {1}")
    @CsvSource(value = {
            // filter: keeps matching
            "[a=>1,b=>2] =?= [a=>1]                             % [a=>1,b=>2]",
            // filter: non-matching → noobj
            "[a=>1] =?= [a=>2]                                  % noobj",
            // filter with instruction
            "[a=>1] =?= [a=>?1]                                % [a=>1]",
            "[a=>2] =?= [a=>?1]                                % noobj",
    }, delimiter = '%')
    public void testWhere(final String expression, final String expected) {
        final Obj result = ObjmtronSerializer.parse(expression).apply();
        final Obj expectedObj = ObjmtronSerializer.parse(expected);
        assertEquals(expectedObj, result, expression);
    }

    // ── Combo operators ──

    @ParameterizedTest(name = "[{index}] combo : {0}  =>  {1}")
    @CsvSource(value = {
            // =?= filter then >>= update
            "[a=>1,b=>2] =?= [a=>1] >>= [a=>+10]               % [a=>11,b=>2]",
            "[a=>1,b=>2] =?= [a=>2] >>= [a=>+10]               % noobj",
            // split then merge
            "[a=>1,b=>2] -<[>>a +1,>>b +1] >-                  % {2,3}",
            // =?= then -<
            "[a=>1,b=>2] =?= [a=>1] -<[>>a,>>b]               % [1,2]",
    }, delimiter = '%')
    public void testCombos(final String expression, final String expected) {
        final Obj result = ObjmtronSerializer.parse(expression).apply();
        final Obj expectedObj = ObjmtronSerializer.parse(expected);
        assertEquals(expectedObj, result, expression);
    }
}
