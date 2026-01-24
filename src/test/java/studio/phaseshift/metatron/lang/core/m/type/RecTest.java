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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.lang.mObjTest;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.sys.type.ui.graphitty.Graphitty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.isa.m.type.Poly.IMMUTABLE;
import static studio.phaseshift.metatron.isa.m.type.Poly.MUTABLE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

public class RecTest extends mObjTest {

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
        Rec r = mParser.m_obj().parse(rec).get();
        Obj k = mParser.m_obj().parse(key).get();
        Obj v = mParser.m_obj().parse(value).get();
        Obj actual = r.at(k);
        LOG.debug("testing %s at %s is %s [expected:%s]", k, r, actual, v);
        assertTrue(r.isRec());
        assertEquals(v, actual);
    }


    @Override
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
            "[a=>b,c=>[d=>2]]                      | [a=>b,c=>[d=>isan(str::T)]]                | false",
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
        super.testMatches(recA, recB, matches);
    }


    @ParameterizedTest
    @CsvSource(value = {
            "[a=>1,b=>2,c=>3].as(lst::T)                                                % [(0=>a=>1),(1=>b=>2),(2=>c=>3)]",
            "[a=>1,b=>2,c=>3].as(lst::T).>-.isa(rel::T).count()                         % 3",
            "[a=>1,b=>2,c=>3].as(lst::T).>-.>>.isa(rel::T).count()                      % 3",
            "[a=>1,b=>2,c=>3].as(lst::T).>-.>>.>>.isa(int::T).count()                   % 3",
            "[a=>1,b=>2,c=>3].as(lst::T).>-.>>.>>.sum()                                 % 6",
            "[a=>1,b=>2,c=>3].as(rec::T)                                                % [a=>1,b=>2,c=>3]",
            "[a=>1,b=>2,c=>3].as(rec::T).as(lst::T)                                     % [(0=>(a=>1)),(1=>(b=>2)),(2=>(c=>3))]",
            "[a=>1,b=>2,c=>3].as(lst::T).as(rec::T)                                     % [0=>(0=>(a=>1)),1=>(1=>(b=>2)),2=>(2=>(c=>3))]",

    }, delimiter = '%')
    public void testAs(final String code, final String expected) {
        super.testCode(code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "[a=>[knows=>[b=>[knows=>c]]]]../<a/+/b/knows>                                           % c",
            "(a=>(knows=>(b=>(knows=>c))))../<a/+/b/knows>                                           % c"
    }, delimiter = '%')
    public void testRecRelBehaviors(final String code, final String expected) {
        super.testCode(code, expected);
    }

    @Override
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
            "{2,0}-<[a=>is{3}(gt(0)),a=>is{2}(gt(0)),a=>noobj].rng()                                 % int{5}::2",
            "{2,5}-<[a=>is{3}(gt(0)),a=>is{2}(gt(0)),a=>noobj].rng()                                 % {int{5}::5, int{5}::2}",
            "{2,5,0,0}-<[a=>is{3}(gt(0)),a=>is{2}(gt(0)),a=>noobj].rng()                             % {int{5}::5, int{5}::2}",
            "{2,5}-<[a=>is{3}(gt(0)),a=>is{2}(gt(0)),a=>noobj])                                      % [a=>{int{5}::5, int{5}::2}]",
            "{2,5,5,5,0}-<[a=>is{3}(gt(0)),a=>is{2}(gt(0)),a=>noobj])                                % [a=>{int{15}::5, int{5}::2}]",
            "{2,2,5,-1}-<[a=>is{3}(gt(0)),a=>is{2}(gt(0)),a=>noobj])                                 % [a=>{int{5}::5, int{10}::2}]",
            "2-<[a=>is(gt(0)),a=>is(gt(0)),b=>3]                                                     % [a=>int{2}::2,b=>3]",
            "2-<[a=>is(gt(0)),a=>is(gt(1)),b=>3]                                                     % [a=>int{2}::2,b=>3]",
            "[1,2,3]-<[>-.is(gt(2)) => >-.is(gt(1)), >-.is(gt(1)) => >-._]                           % [3=>{2,3},{2,3}=>{1,2,3}]",
            "[a=>1,b=>2,c=>3]==[a=>plus(2),b=>_]                                                     % [a=>3,b=>2]",
            "[a=>1,b=>2,c=>3]==[a=>plus(2),map(b)=>plus(10)]                                         % [a=>3,b=>12]",
            "[a=>1,b=>2,c=>3]==[a=>plus(2),map(b)=>plus(10)]==[a=>_,b=>sum()]                        % [a=>3,b=>12]",
            "[a=>1,b=>2,c=>3]==[a=>plus(2),map(b)=>plus(10)]==[a=>_,b=>(-<{count(),sum()})]          % [a=>3,b=>{1,12}]",
            "[a=>1,b=>2,c=>3]==[a=>plus(2),map(b)=>plus(10)]==[a=>_,b=>-<[count(),sum()]]            % [a=>3,b=>[1,12]]",
            "[a=>1,b=>2,c=>3]==[a=>plus(2),map(b)=>plus(10)]==[a=>_,b=>-<[count(),sum()]>-]          % [a=>3,b=>{1,12}]",
            "[a=>1,b=>2,c=>3]==[a=>plus(2),map(b)=>plus(10)]==[a=>_,b=>-<[count(),sum()]>-.count()]  % [a=>3,b=>2]",
            //"[a=>1,b=>2,c=>3]==[a=>plus(2),map(b)=>plus(10)]==[a=>_,b=>(-<[count(),sum()]>-.sum().sum())]    % [a=>3,b=>39]",
            //"[a=>1,b=>2,c=>3]==[a=>plus(2),map(b)=>plus(10)]==[a=>_,b=>-<[count(),sum()]>-.sum().sum()]     % [a=>3,b=>39]",
            "[1,2,3].-<[>-.is(gt(2)) => >-.is(gt(1))>-?<=int{*}[,], >-.is(gt(1)) => _/id()\\_]       % [3=>[2,3],{2,3}=>[1,2,3]]",
    }, delimiter = '%')
    public void testCode(final String code, final String expected) {
        super.testCode(code, expected);
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
        r = r.put("b/c", str("fhat"));
        Graphitty.log(this).trace(r);
        assertEquals(jnt(1), r.at("a"));
        assertEquals(2, r.count());
        assertEquals(1, r.<Rec>at("b").count());
        assertEquals(str("fhat"), r.<Rec>at("b").at("c"));
        /// ///
        r = r.put("d", real(1.0));
        assertEquals(1.0, r.at("d").realValue(), 0.001);
    }

    @Test
    public void testMutableImmutable() {
        Rec r1 = rec(uri("a"), jnt(1), uri("b"), rec(uri("c"), jnt(3)));
        Rec r2 = r1.put(uri("b"), jnt(22), IMMUTABLE);
        Rec r3 = r1.at(uri("b")).<Rec>as().put(uri("d"), jnt(33), IMMUTABLE);
        Rec r4 = r1.put(uri("b"), r1.at(uri("b")).<Rec>as().put(uri("d"), jnt(33)), IMMUTABLE);
        super.testEquals(rec(uri("a"), jnt(1), uri("b"), rec(uri("c"), jnt(3))), r1, true);
        super.testEquals(rec(uri("a"), jnt(1), uri("b"), jnt(22)), r2, true);
        super.testEquals(rec(uri("c"), jnt(3), uri("d"), jnt(33)), r3, true);
        super.testEquals(rec(uri("a"), jnt(1), uri("b"), rec(uri("c"), jnt(3), uri("d"), jnt(33))), r4, true);
        /// //
        Rec rr1 = rec(uri("a"), jnt(1), uri("b"), rec(uri("c"), jnt(3)));
        Rec s1 = rec(uri("a"), jnt(1), uri("b"), rec(uri("c"), jnt(3)));
        super.testEquals(r1, s1, true);
        Rec s2 = r1.put(uri("b"), jnt(22), MUTABLE);
        super.testEquals(r2, s2, true);
        Rec s3 = s1.at(uri("b")).<Rec>as().put(uri("d"), jnt(33), MUTABLE);
        super.testEquals(r3, s3, true);
        Rec s4 = rr1.clone().<Rec>as().put(uri("b"), rr1.at(uri("b")).clone().<Rec>as().put(uri("d"), jnt(33), IMMUTABLE), MUTABLE);
        super.testEquals(r4, s4, true);
        super.testEquals(rec(uri("a"), jnt(1), uri("b"), rec(uri("c"), jnt(3))), rr1, true);
        super.testEquals(rec(uri("a"), jnt(1), uri("b"), jnt(22)), s2, true);
        super.testEquals(rec(uri("c"), jnt(3), uri("d"), jnt(33)), s3, true);
        super.testEquals(rec(uri("a"), jnt(1), uri("b"), rec(uri("c"), jnt(3), uri("d"), jnt(33))), s4, true);


    }
}
