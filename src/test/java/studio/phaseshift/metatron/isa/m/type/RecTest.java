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
import studio.phaseshift.metatron.Training;
import studio.phaseshift.metatron.algebra.AbstractAlgebraTest;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.sys.ui.graphitty.Graphitty;

import java.util.Map;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TYPE;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_from_;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.update_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Poly.IMMUTABLE;
import static studio.phaseshift.metatron.isa.m.type.Poly.MUTABLE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

public class RecTest extends AbstractAlgebraTest {

    @Override
    protected Type carrier() {
        return REC_TYPE;
    }

    @Override
    protected Obj sample() {
        return rec(uri("a"), jnt(1), uri("b"), jnt(2), uri("c"), jnt(3));
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

    @Training(
            instruction = "when the {{{rec}}} rec is rshifted by the {{{key}}} key, what is the result?",
            input = "{{{rec}}}>>{{{key}}}",
            output = "{{{value}}}")
    @ParameterizedTest
    @CsvSource(value = {
            // rec                                 % key                  % value
            "[a=>b]                                % c                    % noobj",
            "[a=>b]                                % </>                  % noobj",
            "[a=>b]                                % a                    % b",
            "[a=>b]                                % a                    % /m/uri::b",
            "[a=>b]                                % a/                   % a=>b",
            "[a=>{b,c}]                            % a/                   % a=>{b,c}",
            "[a=>noobj]                            % a/                   % noobj", // (a=>noobj) TODO:
            "[a=>noobj]                            % a                    % noobj",
            "[=>]                                  % a                    % noobj",
            "[1=>[2=>3]]                           % 1                    % [2=>3]",
            "[1=>[2=>3]]                           % 2                    % noobj",
            "[a=>[b=>c],a/b/c=>[e=>f]]             % a/b/c                % [e=>f]",
            "[a=>[b=>c],a/b/c=>[e=>f]]             % a/b                  % c",
            // "[a=>[b=>c],a/b=>c]               % a/b                  % {c,c}",
            // "[a=>[b=>c],a/b/c=>[e=>f]]             % a/b/c/               % [a/b/c=>[e=>f]].>-{,}",
            "[a=>[b=>c,d=>[e=>f]]]                 % a                    % [b=>c,d=>[e=>f]]",
            "[a=>[b=>c,d=>[e=>f]]]                 % a/                   % a=>[b=>c,d=>[e=>f]]",
            "[a=>[b=>c,d=>[e=>f]]]                 % a/b                  % c",
            "[a=>[b=>c,d=>[e=>f]]]                 % a/d                  % [e=>f]",
            "[a=>[b=>c,d=>[e=>f]]]                 % a/d/e                % f",
            // "[a=>[b=>c,d=>[e=>f]]]                 % a/d/e/               % /m/rel::a/d/e=>f",
            "[a=>[b=>c,d=>[e=>f]]]                 % a/#                  % {c,[e=>f]}",
            "[a=>[b=>c,d=>[e=>f]]]                 % a/+                  % {c,[e=>f]}",
            "[a=>[b=>c,d=>[e=>f]]]                 % a/+/e                % f",
            "[a=>[b=>c,d=>[e=>{1,2,3,4}]]]         % a/+/e                % {1,2,3,4}"
    }, delimiter = '%')
    public void testKeyValue(final String rec, final String key, final String value) {
        Rec r = ObjmtronSerializer.parse(rec);
        Obj k = ObjmtronSerializer.parse(key);
        Obj v = ObjmtronSerializer.parse(value);
        Obj actual = r.at(k);
        LOG.debug("testing %s at %s is %s [expected:%s]", k, r, actual, v);
        assertTrue(r.isRec());
        assertEquals(v, actual);
    }

    @Training(
            instruction = "does the {{{recB}}} rec match the {{{recA}}} rec?",
            output = "{{{matches}}}")
    @ParameterizedTest
    @CsvSource(value = {
            // rec                                 % key                                        % value
            "[=>]                                  % [a=>b]                                     % false",
            "[a=>b]                                % [=>]                                       % true",
            "[a=>b]                                % [a=>b]                                     % true",
            "[a=>b,c=>d]                           % [a=>b]                                     % true",
            "[a=>b,c=>d]                           % [a=>b,c=>e]                                % false",
            "[a=>b,c=>[d=>2]]                      % [a=>b,c=>[d=>2]]                           % true",
            "[a=>b,c=>[d=>[a=>b]]]                 % [a=>b,c=>[d=>get(a).is(eq(b))]]            % true",
            "[a=>b,c=>[d=>2]]                      % [a=>b,c=>[d=>is(gt(0))]]                   % true",
            "[a=>b,c=>[d=>2]]                      % [a=>b,c=>[d=>is(gt(3))]]                   % false",
            "[a=>b,c=>[d=>2]]                      % [a=>b,c=>[d=>isa(int::T[is(gt(0))])]]      %   true",
            "[a=>b,c=>[d=>2]]                      % [a=>b,c=>[d=>is(gt(10))]]                  % false",
            "[a=>b,c=>[d=>2]]                      % [a=>b,c=>[d=>isa(int::T[is(gt(10))])]]     % false",
            "[a=>b,c=>[d=>2]]                      % [a=>b,c=>[d=>int::T[is(gt(10))]]]          % false",
            "[a=>b,c=>[d=>2]]                      % [a=>b,c=>[d=>int::T[is(gt(1))]]]           % true",
            "[a=>b,c=>[d=>2]]                      % [a=>b,c=>[d=>isa(int::T)]]                 % true",
            "[a=>b,c=>[d=>2]]                      % [a=>b,c=>[d=>isa(str::T)]]                 % false",
            "[a=>b,c=>[d=>2]]                      % [a=>b,c=>rec::T]                           % true",
            "[a=>b,c=>[d=>2]]                      % [a=>uri::T,c=>rec::T]                      % true",
            "[a=>b,c=>[d=>2]]                      % [a=>uri::T[is(eq(b))],c=>rec::T]           % true",
            "[a=>b,c=>[d=>2]]                      % [a=>str::T,c=>rec::T]                      % false",
            "[a=>b,c=>[d=>2]]                      % rec::T                                     % true",
            "[a=>b,c=>[d=>2]]                      % str::T                                     % false",
            "[a=>b,c=>[d=>2]]                      % rec::T[is(rng().count().eq(2))]            % true",
            "[a=>b,c=>[d=>2]]                      % rec::T[is(rng().count().eq(3))]            % false",
            "noobj                                 % ?str::T                                    % false",
            "noobj                                 % str{?}::T                                  % true",
            "[a=>2]                                % [a=>int::T,b=>?str::T]                     % false",
            "[a=>2]                                % [a=>int::T,uri{?}::b=>str::T]              % true",
            "[=>]                                  % [a=>int::T,uri{?}::b=>str::T]              % false",
            "[=>]                                  % [uri{?}::a=>int::T,uri{?}::b=>str::T]      % true",
            "[a=>'bad']                            % [uri{?}::a=>int::T,uri{?}::b=>str::T]      % false",
            "[a=>2,b=>0]                           % [uri{?}::a=>int::T,uri{?}::b=>str::T]      % false",

    }, delimiter = '%')
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
            "[a=>1,b=>2,c=>3]>></>                                                      % noobj",
            // The root has no segments, so a # key no longer catches it (it used to answer #=>b)
            "[#=>b]>></>                                                                % noobj",
            "[a=>1,b=>2,c=>3]>>{15}a                                                    % {15}1",
            "{2}[a=>{3}1,b=>2,c=>3]>>{5}a                                               % {30}1",
            "{1,2}[a=>{2,3}1,b=>2,c=>3]>>{4,5}a                                         % {8,30}1",
            "{1,2}[a=>{2,3}1,b=>2,c=>3]>>{{4,5}a,a,a}                                   % {12,42}1",
            "{1,2}[a=>{-2,3}1,b=>2,c=>3]>>?#{**}<=rec({{4,5}a,a,a})                     % {-12,42}1",
            // "{-1,2}[a=>{-2,3}1,b=>2,c=>3]>>?#{**}<=rec{**}({{4,5}a,a,a})               % {12,42}1",
            // "{-1,2}[a=>{-2,3}1,b=>2,c=>3]>>?#{**}<=rec{**}({{4,5}a,a,{-1}a})           % {8,30}1",
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
            "[a=>is(gt(0)),a=>is(gt(2)),b=>3]                                                        % [a=>branch(is(gt(0)),is(gt(2))),b=>3]",
            "2-<[a=>is(gt(0)),a=>is(gt(2)),b=>3]                                                     % [a=>2,b=>3]",
            "[a=>2,b=>5]==[a=>is(gt(0)),a=>is(gt(2)),b=>3]                                           % [a=>2,b=>3]",
            "2-<[a=>is(gt(0)),b=>3]                                                                  % [a=>2,b=>3]",
            "2-<[a=>is{2}(gt(0)),b=>3]                                                               % [a=>int{2}::2,b=>3]",
            "[a=>is{2}(gt(0)),a=>noobj]                                                              % [a=>is{2}(gt(0))]",
            "[a=>is{3}(gt(0)),a=>is{2}(gt(0)),a=>noobj]                                              % [a=>is{5}(gt(0))]",
            "2-<[a=>is{3}(gt(0)),a=>is{2}(gt(0)),a=>noobj]                                           % [a=>int{5}::2]",
            "2-<[a=>is{3}(gt(0)),a=>is{2}(gt(0)),a=>noobj].rng()                                     % int{5}::2",
            "{2,0}.-<[a=>is{3}(gt(0)),a=>is{2}(gt(0)),a=>noobj].rng()                                % int{5}::2",
            "{2,5}.-<[a=>is{3}(gt(0)),a=>is{2}(gt(0)),a=>noobj].rng()                                % {int{5}::5, int{5}::2}",
            "{2,5,0,0}-<[a=>is{3}(gt(0)),a=>is{2}(gt(0)),a=>noobj].rng()                             % {int{5}::5, int{5}::2}",
            //  "{2,5}.barrier([a=>is{3}(gt(0)),a=>is{2}(gt(0)),a=>noobj]))                          % [a=>{int{5}::5, int{5}::2}]",
            // "{2,5,5,5,0}-<[a=>is{3}(gt(0)),a=>is{2}(gt(0)),a=>noobj])                             % [a=>{int{15}::5, int{5}::2}]",
            //  "{2,2,5,-1}-<[a=>is{3}(gt(0)),a=>is{2}(gt(0)),a=>noobj])                             % [a=>{int{5}::5, int{10}::2}]",
            "2-<[a=>is(gt(0)),a=>is(gt(0)),b=>3]                                                     % [a=>int{2}::2,b=>3]",
            "2-<[a=>is(gt(0)),a=>is{-1}(gt(0)),b=>3]                                                 % [b=>3]",
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
            "[a=>1,b=>2,c=>3]==[a=>plus(2),b=>+10]==[a=>_,b=>-<[count(),sum()]>-.sum?int<=int{*}().sum()]     % [a=>3,b=>13]",
            "[1,2,3]-<[>-.?>2=>>-.?>1>-?<=int{*}[,],>-.?>1=>_/id()\\_]                          % [3=>[2,3],{2,3}=>[1,2,3]]",
    }, delimiter = '%')
    public void testCode(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    /**
     * COPY ON WRITE through the deep merge: a bulked rec's values are read through their rels, the rec's coefficient
     * multiplied in — and a bag read that way is a new bag, so the rec it came from keeps its own.
     */
    @Test
    public void testMergeLeavesSourceBagsAlone() {
        final Rec source = rec(uri("a"), objs(jnt(1), jnt(2)));
        final Rec merged = rec().plus(source.c(cInt.of(2)).asRec());
        AbstractMetatronTest.checkEquality(LOG, ObjmtronSerializer.parse("[a=>{1,2}]"), source, true);
        AbstractMetatronTest.checkEquality(LOG, ObjmtronSerializer.parse("[a=>{int{2}::1,int{2}::2}]"), merged, true);
    }

    /**
     * A parsed fold evaluated twice answers the same both times: evaluating it never edits the literals it was parsed
     * with — here the bag inside a bulked rec, which the deep merge multiplies as it reads.
     */
    @Test
    public void testFoldIsRepeatable() {
        final Obj code = ObjmtronSerializer.parse("{[a=>{1,2}],[a=>{1,2}]}.sum()");
        final Obj first = code.apply();
        final Obj second = code.apply();
        AbstractMetatronTest.checkEquality(LOG, ObjmtronSerializer.parse("[a=>{int{2}::1,int{2}::2}]"), first, true);
        AbstractMetatronTest.checkEquality(LOG, first, second, true);
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

    @Training.SkipTraining(reason = "too complicated to express easily")
    @ParameterizedTest
    @CsvSource(value = {
            // MUTABLE set: mutate original in-place
            "[a=>1,b=>2,c=>3]   % a   % 99  % 3   % 99  % true",     // existing key
            "[a=>1,b=>2]        % c   % 42  % 3   % 42  % true",     // new key
            "[x=>10]            % x   % -1  % 1   % -1  % true",     // overwrite
    }, delimiter = '%')
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

    @Training.SkipTraining(reason = "too complicated to express easily")
    @ParameterizedTest
    @CsvSource(value = {
            // IMMUTABLE delete: return new rec, original untouched
            "[a=>10,b=>20,c=>30]  % b   % 3   % 2",     // delete middle
            "[x=>42]              % x   % 1   % 0",     // delete only
    }, delimiter = '%')
    public void testImmutableDelete(final String recStr, final String key,
                                    final int expectedOrigCount, final int expectedCloneCount) {
        final Rec original = ObjmtronSerializer.parse(recStr);
        final Rec clone = original.at(uri(key), noobj(), IMMUTABLE);
        assertNotSame(original, clone, "IMMUTABLE delete should return new reference");
        assertEquals(expectedOrigCount, original.count());
        assertEquals(expectedCloneCount, clone.count());
        assertTrue(original.has(uri(key)), "original should still have key");
        assertTrue(clone.at(uri(key)).isNoObj(), "clone should not have deleted key");
        assertNotEquals(original.at(key), (Obj) clone.at(key), "original and clone have different keys");
        assertNotEquals(original, clone, "original and clone have different keys");
    }

    @ParameterizedTest
    @Training(instruction = "when {{{key}}} is removed from {{{recStr}}, what is the result?", output = "{{{expectedCount}}}")
    @CsvSource(value = {
            // MUTABLE delete: remove from original in-place
            "[a=>10,b=>20,c=>30]  % b   % 2",     // delete middle
            "[a=>10,b=>20,c=>30]  % a   % 2",     // delete first
            "[x=>42]              % x   % 0",     // delete only → empty
    }, delimiter = '%')
    public void testMutableDelete(final String recStr, final String key,
                                  final int expectedCount) {
        final Rec original = ObjmtronSerializer.parse(recStr);
        final Rec result = original.at(uri(key), noobj(), MUTABLE);
        assertSame(original, result, "MUTABLE delete should return same reference");
        assertEquals(expectedCount, original.count());
        assertTrue(original.at(uri(key)).isNoObj(), "MUTABLE delete should remove key");
    }

    @ParameterizedTest
    @Training(instruction = "does the mtron expression {{{code}}} yield a result?", output = "{{{matches}}}")
    @CsvSource(value = {
            "[a=>1,b=>2].isa([a=>?>0])                              % true",
            "[a=>1,b=>2].isa([a=>?>1])                              % false",
            "[a=>1,b=>2].isa([a=>?>0,b=>?>1])                       % true",
            "[a=>1,b=>2].isa([a=>?>0]).isa([b=>?>2])                % false",
            "[a=>1,b=>2].isa([a=>?>0,b=>?>2])                       % false",
            "[a=>1,b=>2].isa([a=>?>0,b=>?>0])                       % true",
            "[a=>1,b=>2].isa([a=>?>0,b=>?>0,c=>?>0])                % false",
            "[=>].isa([a=>?>0])                                     % false",
            /// ///////////////////////////////////////////////////////
            "[a=>1,b=>2].isa([a=>?>0])                            % true",
            "[a=>1,b=>2].isa([a=>?>1])                            % false",
            "[a=>1,b=>2].isa([a=>?>0,b=>?>1])                     % true",
            "[a=>1,b=>2].isa([a=>?>0,b=>?>2])                     % false",
            "[a=>1,b=>2].isa([a=>?>0,b=>?>0])                     % true",
            "[a=>1,b=>2].isa([a=>?>0,b=>?>0,c=>?>0])              % false",
            "[a=>[b=>1,c=>2]].isa([a=>isa(rec::T)])               % true",
            "[a=>[b=>1,c=>2]].isa([a=>isa(lst::T)])               % false",
            ////////////////////////////////////////////////////////////
            "[a=>1,b=>2]?[a=>?>0]                                 % true",
            "[a=>1,b=>2]?[a=>?>1]                                 % false",
            "[a=>1,b=>2]?[a=>?>0,b=>?>1]                          % true",
            "[a=>1,b=>2]?[a=>?>0]?[b=>?>2]                        % false",
            "[a=>1,b=>2]?[a=>?>0,b=>?>2]                          % false",
            "[a=>1,b=>2]?[a=>?>0,b=>?>0]                          % true",
            "[a=>1,b=>2]?[a=>?>0,b=>?>0,c=>?>0]                   % false",
            "[=>]?[a=>?>0]                                        % false",
            /// ///////////////////////////////////////////////////////
            "[a=>1,b=>2]?[a=>?>0]                            % true",
            "[a=>1,b=>2]?[a=>?>1]                            % false",
            "[a=>1,b=>2]?[a=>?>0,b=>?>1]                     % true",
            "[a=>1,b=>2]?[a=>?>0,b=>?>2]                     % false",
            "[a=>1,b=>2]?[a=>?>0,b=>?>0]                     % true",
            "[a=>1,b=>2]?[a=>?>0,b=>?>0,c=>?>0]              % false",
            "[a=>[b=>1,c=>2]]?[a=>rec::T]                    % true",
            "[a=>[b=>1,c=>2]]?[a=>lst::T]                    % false",
            // filter: keeps matching
            "[a=>1,b=>2]?[a=>1]                              % true",
            // filter: non-matching → noobj
            "[a=>1]?[a=>2]                                   % false",
            // filter with instruction
            "[a=>1]?[a=>?1]                                  % true",
            "[a=>2]?[a=>?1]                                  % false",
    }, delimiter = '%', quoteCharacter = '~')
    public void testIsA(final String code, final boolean matches) {
        final Obj codeObj = ObjmtronSerializer.parse(code);
        LOG.debug("testing has %s [expected:%s]", codeObj, matches);
        if (matches)
            assertFalse(codeObj.apply().isNoObj());
        else
            assertTrue(codeObj.apply().isNoObj());
    }

    @ParameterizedTest
    @Training(instruction = "does {{{a}}} match {{{b}}}?", output = "{{{matches}}}")
    @Training(instruction = "what is the result of {{{a}}}.is({{{b}}})?", output = "{{{matches}}}")
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
        LOG.warn("testing match difference:\n%s", Poly.Helper.diffObjRecursion(aobj, bobj));
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
    @Training(instruction = "given {{{@TestData}}}, what is the result of the mtron expression {{{code}}}?", output = "{{{expected}}}")
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
    @Training(instruction = "given {{{@TestData}}}, what is the result of the mtron expression {{{code}}}?", output = "{{{expected}}}")
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
            // a bulked rec is the copies it stands for: its coefficient is read into every value it holds
            "[a=>2]-<[_,_]>-.sum()                                                       % [a=>{2}2]",
            "[a=>2]-<[_,_,_]>-.sum()                                                     % [a=>{3}2]",
            "[a=>1,b=>2]-<[_,_]>-.sum()                                                  % [a=>{2}1,b=>{2}2]",
            "{[a=>1],[a=>1],[a=>2]}.sum()                                                % [a=>{1,1,2}]",
            "{[a=>{2}1],[a=>{2}1]}.sum()                                                 % [a=>{4}1]",
            "[a=>2]-<[_,_]>-.sum()==[_=>sum()]                                           % [a=>4]",
            // ... all the way down, so a bulked fold is the deep merge of its copies
            "[a=>[b=>2]]-<[_,_]>-.sum()                                                  % [a=>[b=>{2}2]]",
            "[a=>[b=>2]]-<[_,_]>-.sum()                                                  % [a=>[b=>2]]+[a=>[b=>2]]",
            "{[a=>[b=>1]],[a=>[b=>1]],[a=>[c=>2]]}.sum()                                 % [a=>[b=>{2}1,c=>2]]",
            "[a=>[b=>[c=>1]],d=>3]-<[_,_]>-.sum()                                        % [a=>[b=>[c=>{2}1]],d=>{2}3]",
            "[a=>[b=>[c=>1]],d=>3]-<[_,_,_]>-.sum()                                      % [a=>[b=>[c=>1]],d=>3]+[a=>[b=>[c=>1]],d=>3]+[a=>[b=>[c=>1]],d=>3]",
            // a lst is an atom of the merge: colliding lsts bag whole, and a bulked lst is its copies in the bag
            "{[a=>[1,2,3]],[a=>[2,3]],[a=>[1,2,3]]}.sum()                                % [a=>{[1,2,3],[2,3],[1,2,3]}]",
            "[a=>[1,2]]-<[_,_]>-.sum()                                                   % [a=>{2}[1,2]]",
            "[a=>[1,2]]-<[_,_]>-.sum()                                                   % [a=>[1,2]]+[a=>[1,2]]",
            "[a=>[b=>[1,2]]]-<[_,_]>-.sum()                                              % [a=>[b=>{2}[1,2]]]",
    }, delimiter = '%')
    public void testSum(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @Training(instruction = "does {{{record}}} match {{{type}}}?", output = "{{{matches}}}")
    @CsvSource(value = {
            "[a=>1,b=>2,c=>3]                            % rec::T                       % true",
            //"[a=>1]                                      % rec[uri=>int]::T             % true",
            //"[a=>1,b=>2,c=>3]                            % rec[uri=>int]::T             % true",
            //"[=>]                                        % rec[#{?}=>#{?}]::T           % true",
            //"[a=>1]                                      % rec[#{?}=>#{?}]::T           % true",
            //"[a=>1]                                      % rec[uri=>#]::T               % true"
    }, delimiter = '%')
    public void testPoly(final String record, final String type, final boolean matches) {
        final Type t = ObjmtronSerializer.parse(type);
        LOG.debug("%s", t);
        AbstractMetatronTest.checkMatches(LOG, record, type, matches);
    }

    /**
     * A FIBERED REC VALUE IS ACCEPTED WHERE ITS BASE OR THE OBJECT AT ONE OF ITS FIBERS IS. A value typed
     * {@code person[ninja]} holding a ninja at {@code role} is acceptable wherever {@code ninja::T} or
     * {@code person::T} is expected, and nowhere else ({@code guru::T} rejects it). An absent fiber is no object, so
     * it is not accepted where an optional type ({@code ninja{?}::T}) is expected. The type-level counterpart is
     * {@code TypeTest.testFiberedRecIsAcceptedWhereAFiberIs}, and a bracket on an instance of a declaration that
     * names no fiber ({@code drifter}) is an error.
     */
    @ParameterizedTest
    @TestData(value = {
            "person -> rec[role]::T[?[name=>?str::T,age=>?int::T,role=>?rec::T]]@person",
            "drifter -> rec::T[?[name=>?str::T,age=>?int::T]]@drifter",
            "ninja -> rec::T@ninja",
    })
    @CsvSource(value = {
            // value                                                                     % type          % matches
            "person[ninja]::[name=>'marko',age=>29,role=>ninja::[name=>'dark_assassin']]  % ninja::T      % true",
            "person[ninja]::[name=>'marko',age=>29,role=>ninja::[name=>'dark_assassin']]  % person::T     % true",
            "person[ninja]::[name=>'marko',age=>29,role=>ninja::[name=>'dark_assassin']]  % guru::T       % false",
            // ---- an absent fiber is no object: it does not answer an optional type ----
            "person[ninja]::[name=>'marko',age=>29]                                       % ninja{?}::T   % false",
            // ---- a bracket entry types a fiber, and drifter names none ----
            "drifter[ninja]::[name=>'marko',age=>29]                                      % ninja::T      % <ERROR>",
    }, delimiter = '%')
    public void testFiberedRecValueIsAcceptedWhereAFiberIs(final String value, final String type, final String matches) {
        if (matches.trim().equals("<ERROR>"))
            checkCodeParseApply(LOG, value, matches);
        else
            checkMatches(LOG, value, type, Boolean.parseBoolean(matches.trim()));
    }

    /**
     * A PREDICATED TYPE IS ASKED OF THE OBJECT AT THE FIBER. {@code person[ninja]} gives its {@code role} fiber a
     * ninja, so a value is acceptable where {@code ninja::T[?[name=>str::T]]} is expected when {@code wrapper>>role}
     * passes that predicate. The wrapper below declares only {@code role} and carries no {@code name} of its own:
     * <pre>
     * person[ninja]::[role=>ninja::[name=>'dark_assassin']]   1   the ninja at `role` has the name
     * person[ninja]::[role=>[name=>7]]                        0   the fiber value's name is an int, not a str
     * person[ninja]::[role=>[age=>29]]                        0   the fiber value has no name at all
     * </pre>
     * A predicate-free type ({@code spectre -> rec::T@spectre}) asks nothing of the shape, so any rec at the fiber
     * answers it.
     */
    @ParameterizedTest
    @TestData(value = {
            "person -> rec[role]::T[?[role=>rec::T]]@person",
            "ninja -> rec::T[?[name=>str::T]]@ninja",
            "baker -> rec::T[?[name=>int::T]]@baker",
            "spectre -> rec::T@spectre",
    })
    @CsvSource(value = {
            // value                                                         % type        % matches
            // ---- the object at the fiber passes the predicate; the wrapper carries none of its shape ----
            "person[ninja]::[role=>ninja::[name=>'dark_assassin']]            % ninja::T    % true",
            // ---- ...and the wrapper is a person ----
            "person[ninja]::[role=>ninja::[name=>'dark_assassin']]            % person::T   % true",
            // ---- a wrapper that also carries the field is still the same answer ----
            "person[ninja]::[name=>'marko',role=>ninja::[name=>'dark_assassin']] % ninja::T % true",
            // ---- the object at the fiber has an int name: it is not a ninja ----
            "person[ninja]::[role=>[name=>7]]                                 % ninja::T    % false",
            // ---- the object at the fiber has no name: it is not a ninja ----
            "person[ninja]::[role=>[age=>29]]                                 % ninja::T    % false",
            // ---- the object at the fiber has a str name, and a baker's name is an int ----
            "person[baker]::[role=>[name=>'dark_assassin']]                   % baker::T    % false",
            // ---- a type no fiber holds is not accepted ----
            "person[ninja]::[role=>ninja::[name=>'dark_assassin']]            % guru::T     % false",
            // ---- a predicate-free type asks nothing of the shape ----
            "person[spectre]::[role=>[name=>7]]                               % spectre::T  % true",
    }, delimiter = '%', quoteCharacter = '~')
    public void testPredicatedTypeIsAskedOfTheFiberValue(final String value, final String type, final boolean matches) {
        checkMatches(LOG, value, type, matches);
    }

    /**
     * THE REC POLY NAMES ITS DECLARED FIELDS. {@code rec[k1,k2]} declares the keys {@code k1} and {@code k2}, so a
     * literal carrying only {@code k1} fails its type while one carrying both passes. A missing key still READS
     * as {@code noobj} — the last row is that read, not a type test — which is exactly why a declared field cannot
     * be silent. Absence is legal only where the field's coefficient admits zero ({@code rec[k{?},j]}).
     */
    @Training(
            instruction = "does {{{literal}}} carry the fields its rec poly declares?",
            output = "{{{result}}}")
    @ParameterizedTest
    @CsvSource(value = {
            // a declared field at coefficient one is required: k2 is declared and absent
            "rec[k1,k2]::[k1=>a]                                     % <ERROR>",
            // ... carried, the literal is accepted
            "rec[k1,k2]::[k1=>a,k2=>b]                               % rec[k1,k2]::[k1=>a,k2=>b]",
            // the failure is per field: the middle of three is the absent one
            "rec[k1,k2,k3]::[k1=>a,k3=>c]                            % <ERROR>",
            // a field whose own coefficient admits zero ({?}) may be absent
            "rec[k1{?},k2,k3]::[k2=>b,k3=>c]                         % rec[k1{?},k2,k3]::[k2=>b,k3=>c]",
            // an absent key still READS as noobj: the read is untouched, only the type test judges absence
            "[k1=>a]>>k2                                             % noobj",
            // ... and that holds for an accepted literal reading a key its declaration never named
            "rec[k1,k2]::[k1=>a,k2=>b]>>k3                           % noobj",
    }, delimiter = '%')
    public void testRecPolyDeclaredFields(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @Disabled("rec[#=>#] / rec[int=>int] / rec[#=>int] are keyed polys — a category error since the poly ruling (poly types parameter positions: lst indices, rec fields, rel vertices, object instantiation). Re-express against a fibered declaration whose predicate types each field.")
    @ParameterizedTest
    @Training(instruction = "does {{{record}}} match {{{type}}}?", output = "{{{matches}}}")
    @CsvSource(value = {
            "[a=>1]                                      % rec[#=>#]::T                 % true",
            "[a=>1]                                      % rec[int=>int]::T             % false",
            "[a=>1]                                      % rec[#=>int]::T               % true",
    }, delimiter = '%')
    public void testKeyedPolyTypeMatching(final String record, final String type, final boolean matches) {
        final Type t = ObjmtronSerializer.parse(type);
        LOG.debug("%s", t);
        AbstractMetatronTest.checkMatches(LOG, record, type, matches);
    }

    @ParameterizedTest
    @TestData(value = {"xyz -> [x=>[y=>1,z=>2]]@xyz"})
    @Training(instruction = "given {{{@TestData}}}, what is the result of {{{original}}}>>={{{update}}}?", output = "{{{expected}}}")
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

    @ParameterizedTest
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
            // nested recs — deep structural merge (recursion into sub-recs)
            "[a=>[b=>1]] + [a=>[c=>2]]                         % [a=>[b=>1,c=>2]]",
            "[a=>[b=>1,c=>2]] + [a=>[b=>3]]                    % [a=>[b=>{1,3},c=>2]]",
            // multi-key
            "[a=>1] + [b=>2] + [c=>3]                          % [a=>1,b=>2,c=>3]",
            // wildcard key _ (doesn't match any literal key → added as new field)
            "[a=>1,b=>2,c=>3] + [_=>+2]                        % [a=>1,b=>2,c=>3,id()=>plus(2)]",
            // ── deep nesting ──
            "[a=>[b=>1]] + [a=>[b=>2]]                         % [a=>[b=>{1,2}]]",
            "[a=>[b=>[c=>1,d=>2]]] + [a=>[b=>[e=>3]]]           % [a=>[b=>[c=>1,d=>2,e=>3]]]",
            "[a=>[b=>[c=>1]]] + [a=>[b=>[c=>2]]]                % [a=>[b=>[c=>{1,2}]]]",
            "[a=>[b=>1,c=>2]] + [a=>[b=>3,d=>4]]               % [a=>[b=>{1,3},c=>2,d=>4]]",
            "[a=>[b=>1]] + [a=>[b=>+2]]                         % [a=>[b=>{1,plus(2)}]]",
            // ── coefficients ── the lhs's is the stream's, so it stays on the result; a coefficient the merge
            // multiplies is read into what it holds; a value nothing multiplies is kept as it is, from either side
            "[a=>1]-<[_,_]>-.plus([b=>2])                       % {2}[a=>1,b=>2]",
            "[a=>{2}1] + [a=>1]                                 % [a=>{3}1]",
            "[a=>1] + [a=>{2}1]                                 % [a=>{3}1]",
            "[a=>{2}[b=>1]] + [a=>[b=>2]]                       % [a=>[b=>{1,1,2}]]",
            "[a=>[b=>2]] + [a=>{2}[b=>1]]                       % [a=>[b=>{2,1,1}]]",
            "[a=>{2}[b=>1]] + [c=>3]                            % [a=>{2}[b=>1],c=>3]",
            "[c=>3] + [a=>{2}[b=>1]]                            % [c=>3,a=>{2}[b=>1]]",
            // a lst is an atom of the merge: colliding lsts make a bag of lsts, each kept whole -- concatenating them
            // is a select's + ([a=>[1,2,3]]==[a=>+[2,3]]), said by the expression
            "[a=>[1,2,3]] + [a=>[2,3]] + [a=>[1,2,3]]           % [a=>{[1,2,3],[2,3],[1,2,3]}]",
            "[a=>[1,2,3]] + [a=>[2,3]]                          % [a=>{[1,2,3],[2,3]}]",
            "[a=>[b=>[1]]] + [a=>[b=>[2],c=>[3]]]               % [a=>[b=>{[1],[2]},c=>[3]]]",
            "[a=>[1,2,3]]==[a=>+[2,3]]                          % [a=>[1,2,3,2,3]]",
    }, delimiter = '%')
    public void testRecPlus(final String expression, final String expected) {
        final Obj result = ObjmtronSerializer.parse(expression).apply();
        final Obj expectedObj = ObjmtronSerializer.parse(expected);
        assertEquals(expectedObj, result, expression);
    }

    // ── == (SELECT) ── matches keys in BOTH LHS and RHS, replaces values,
    // drops LHS-only keys, drops RHS-only keys, returns noobj when nothing matches.
    // RHS values that are instructions compute against the LHS value.

    @ParameterizedTest
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
            "[a=>[b=>[c=>1]]] >>= [a=>+[b=>[e=>3]]]              % [a=>[b=>[c=>1,e=>3]]]",
            "[a=>[b=>[c=>1]]] >>= [a=>[b=>+[e=>3]]]              % [a=>[b=>[c=>1,e=>3]]]",
    }, delimiter = '%')
    public void testUpdateOperator(final String expression, final String expected) {
        final Obj result = ObjmtronSerializer.parse(expression).apply();
        final Obj expectedObj = ObjmtronSerializer.parse(expected);
        assertEquals(expectedObj, result, expression);
    }

    // ── >- (MERGE) ── coalesces Objs into a poly, returns Objs if not coalescable

    @ParameterizedTest
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

    @ParameterizedTest
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

    // ── Combo operators ──

    @ParameterizedTest
    @CsvSource(value = {
            // =?= filter then >>= update
            "[a=>1,b=>2].?[a=>1] >>= [a=>+10]               % [a=>11,b=>2]",
            "[a=>1,b=>2].?[a=>2] >>= [a=>+10]               % noobj",
            // split then merge
            "[a=>1,b=>2] -<[>>a +1,>>b +1] >-               % {2,3}",
            // =?= then -<
            "[a=>1,b=>2].?[a=>1] -<[>>a,>>b]                % [1,2]",
    }, delimiter = '%')
    public void testCombos(final String expression, final String expected) {
        final Obj result = ObjmtronSerializer.parse(expression).apply();
        final Obj expectedObj = ObjmtronSerializer.parse(expected);
        assertEquals(expectedObj, result, expression);
    }

    // ── Space-backed mutation probes ──────────────────────────────────────────
    // Each row writes its own state (anchored via `@`), mutates via `>>=`, then
    // reads the whole rec back and compares. Deeper nesting + mixed data types.

    @ParameterizedTest
    @Training(instruction = "when {{{state}}} has been defined and then {{{update}}} is called, what is the result of the {{{fetch}}} dereference?", output = "{{{expected}}}")
    @CsvSource(value = {
            // state                     % update           % fetch % expected
            "[l=>[1,2,3]]@x             % @x/l >>= [9,8]   % *x    % [l=>[9,8]]@x",
            "[l=>['a','b','c']]@x        % @x/l >>= ['x','y'] % *x  % [l=>['x','y']]@x",
            "[l=>[1,2,3]]@x             % @x/l >>= +[4,5]   % *x    % [l=>[1,2,3,4,5]]@x",
            "[l=>['a','b']]@x            % @x/l >>= +['c','d'] % *x % [l=>['a','b','c','d']]@x",
            "[l=>[1,2,3]]@x             % @x/l >>= [,]      % *x    % [l=>[,]]@x",
            "[l=>[1,'a',2.5]]@x          % @x/l >>= [true,false] % *x % [l=>[true,false]]@x",
    }, delimiter = '%')
    void control_lstInRec(final String state, final String update, final String fetch, final String expected) {
        final Obj stored = Machine.read(f(fetch));
        LOG.info("stored %s => %s", fetch, stored);
        AbstractMetatronTest.checkSpaceMutation(LOG, state, update, Map.ofEntries(entry(f("x"), expected)));
    }

    @ParameterizedTest
    @Training(instruction = "when {{{state}}} has been defined and then {{{update}}} is called, what is the result of the {{{fetch}}} dereference?", output = "{{{expected}}}")
    @CsvSource(value = {
            // state                       % update                 % fetch % expected
            "[l=>[1,[1,2,3],3]]@x          % @x/l/1 >>= +[11,22,33] % *x    % [l=>[1,[1,2,3,11,22,33],3]]@x",
            "[l=>['a',['b','c'],'d']]@x     % @x/l/1 >>= +['e','f']  % *x    % [l=>['a',['b','c','e','f'],'d']]@x",
            "[l=>[1,[2,[3,4],5],6]]@x       % @x/l/1/1 >>= +[7,8]    % *x    % [l=>[1,[2,[3,4,7,8],5],6]]@x",
            "[l=>[1,[r=>[2,3]],4]]@x        % @x/l/1/r >>= +[5]       % *x    % [l=>[1,[r=>[2,3,5]],4]]@x",
    }, delimiter = '%')
    void nested_lstInLstInRec_append(final String state, final String update, final String fetch, final String expected) {
        final Obj stored = Machine.read(f(fetch));
        LOG.info("stored %s => %s", fetch, stored);
        AbstractMetatronTest.checkSpaceMutation(LOG, state, update, Map.ofEntries(entry(f("x"), expected)));
    }

    @ParameterizedTest
    @Training(instruction = "when {{{state}}} has been defined and then {{{update}}} is called, what is the result of the {{{fetch}}} dereference?", output = "{{{expected}}}")
    @CsvSource(value = {
            // state                       % update                % fetch % expected
            "[m=>[[1,2,3]]]@y              % @y/m/0/1 >>= +9       % *y    % [m=>[[1,11,3]]]@y",
            "[m=>[[[1,2,3]]]]@y            % @y/m/0/0/1 >>= +10    % *y    % [m=>[[[1,12,3]]]]@y",
            "[m=>[[1,2,3]]]@y              % @y/m/0/1 >>= 99       % *y    % [m=>[[1,99,3]]]@y",
            "[m=>[['a','b','c']]]@y        % @y/m/0/1 >>= 'z'      % *y    % [m=>[['a','z','c']]]@y",
            "[m=>[[r=>[a=>1,b=>2]]]]@y     % @y/m/0/r/b >>= +5     % *y    % [m=>[[r=>[a=>1,b=>7]]]]@y",
    }, delimiter = '%')
    void nested_lstInLstInLstInRec_append(final String state, final String update, final String fetch, final String expected) {
        final Obj stored = Machine.read(f(fetch));
        LOG.info("stored %s => %s", fetch, stored);
        AbstractMetatronTest.checkSpaceMutation(LOG, state, update, Map.ofEntries(entry(f("y"), expected)));
    }

    @ParameterizedTest
    @Training(instruction = "when {{{state}}} has been defined and then {{{update}}} is called, what is the result of the {{{fetch}}} dereference?", output = "{{{expected}}}")
    @CsvSource(value = {
            // state                       % update                    % fetch % expected
            "[m=>[[1,2,3]]]@z              % @z/m >>= [0=>[9,8]]       % *z    % [m=>[[9,8]]]@z",
            "[m=>[[1,2,3],[4,5,6]]]@z       % @z/m >>= [1=>[9,8]]       % *z    % [m=>[[1,2,3],[9,8]]]@z",
            "[m=>[[1,2,3],[4,5,6]]]@z       % @z/m >>= [0=>[7],1=>[8,9]] % *z  % [m=>[[7],[8,9]]]@z",
            "[m=>[[1,2,3]]]@z              % @z/m >>= [0=>99]           % *z    % [m=>[99]]@z",
            "[m=>[['a','b'],['c','d']]]@z   % @z/m >>= [1=>['x','y']]    % *z    % [m=>[['a','b'],['x','y']]]@z",
    }, delimiter = '%')
    void nested_innerReplaceByKey(final String state, final String update, final String fetch, final String expected) {
        final Obj stored = Machine.read(f(fetch));
        LOG.info("stored %s => %s", fetch, stored);
        AbstractMetatronTest.checkSpaceMutation(LOG, state, update, Map.ofEntries(entry(f("z"), expected)));
    }

    @ParameterizedTest
    @Training(instruction = "when {{{state}}} has been defined, what is the result of {{{update}}}?", output = "{{{expected}}}")
    @CsvSource(value = {
            // state                       % update                  % expected
            "[l=>[1,[1,2,3],3]]@r          % @r/l/1 >>= +[11,22,33]  % [1,2,3,11,22,33]",
            "[l=>[1,[2,[3,4],5],6]]@r       % @r/l/1/1 >>= +[7,8]     % [3,4,7,8]",
            "[l=>['a',['b','c'],'d']]@r     % @r/l/1 >>= +['e']        % ['b','c','e']",
            "[l=>[1,[1,2,3],3]]@r          % @r/l/1 >>= [99]           % [99]",
    }, delimiter = '%')
    void nested_updateResultValue(final String state, final String update, final String expected) {
        // isolate: the update RESULT (vs. what gets stored)
        ObjmtronSerializer.parse(state).apply();
        AbstractMetatronTest.checkCodeParseApply(LOG, update, expected);
    }

    /**
     * Regression: atDirect(key) with a single-segment key on a rec whose map keys are
     * deeper (multi-segment) uris must still return the raw stored value (e.g. a raw
     * !* / !@ auto pointer) rather than falling into atToggle's pattern-collection
     * branch and returning an objs/derived wrapper.
     */
    /**
     * atDirect on a rec keyed by full (multi-segment) uris: a single-segment key that
     * uniquely names a stored key's leaf must return that raw stored value — it must
     * not silently return noobj / a pattern wrapper.  (Tree-style loaders key recs by
     * the child's full path, and callers address children by node name.)
     */
    @Test
    public void testAtDirectSingleSegmentOnDeepKeyedRec() {
        final Obj ptr = auto_from_(f("local:src/a_main")).tryToInst();
        final Rec r = rec(uri("usr/marko/Echo"), ptr);
        final Obj direct = r.atDirect(uri("Echo"));
        LOG.info("atDirect(uri('Echo')) on deep-keyed rec -> %s", direct);
        assertTrue(Obj.Helper.getAutoPointer(direct).isPresent(),
                "atDirect(uri('Echo')) should find the unique deep key 'usr/marko/Echo', got: " + direct);
        // the full-path key form must keep working too
        assertTrue(Obj.Helper.getAutoPointer(r.atDirect(uri("usr/marko/Echo"))).isPresent(),
                "atDirect(full path key) should return the raw !@ inst, got: " + r.atDirect(uri("usr/marko/Echo")));
    }

    /**
     * at()/atDirect() crossing the rec -> lst boundary and back: a rec whose value is a
     * lst whose element is a rec (the metatron tree layout, e.g. members lists inside
     * class recs).  Uri paths with multiple segments may surface results wrapped in an
     * objs (a poly may address several matches), so assert over the stream.
     */
    @Test
    public void testAtCrossesRecLstBoundary() {
        final Rec r = rec(uri("m"), lst(rec(uri("x"), jnt(41)), jnt(7)));
        // rec -> lst -> rec leaf
        assertTrue(r.at(uri("m/0/x")).stream().anyMatch(o -> o.equals(jnt(41))),
                "at(m/0/x) should reach the rec leaf inside the lst, got: " + r.at(uri("m/0/x")));
        assertTrue(r.atDirect(uri("m/0/x")).stream().anyMatch(o -> o.equals(jnt(41))),
                "atDirect(m/0/x) should reach the raw leaf inside the lst, got: " + r.atDirect(uri("m/0/x")));
        // rec -> lst plain element
        assertTrue(r.at(uri("m/1")).stream().anyMatch(o -> o.equals(jnt(7))),
                "at(m/1) should reach the plain lst element, got: " + r.at(uri("m/1")));
        assertTrue(r.atDirect(uri("m/1")).stream().anyMatch(o -> o.equals(jnt(7))),
                "atDirect(m/1) should reach the raw lst element, got: " + r.atDirect(uri("m/1")));
        // rec -> lst -> lst deeper
        final Rec deep = rec(uri("m"), lst(lst(jnt(9), jnt(8)), jnt(7)));
        assertTrue(deep.at(uri("m/0/1")).stream().anyMatch(o -> o.equals(jnt(8))),
                "at(m/0/1) should reach the nested lst element, got: " + deep.at(uri("m/0/1")));
    }

    /**
     * DELEGATION — applying an instruction to a wrapper is applying it to a stream of two objects:
     * <pre>
     * person[ninja]::[…].xyz()   ==   { person::[…], ninja::[…] }.xyz()
     * </pre>
     * A WRAPPER is a rec whose type INSTANTIATES a declaration that names the fields the wrapper exposes:
     * {@code rec[role]::T[?[…,role=>rec::T]]@person} names {@code role} (the declaration's tid poly is the FIELD
     * NAMES) and {@code person[ninja]} names its type (the instance's vid poly is the COMPONENT TYPES). So
     * {@code person[ninja]::[role=>ninja::[…]]} is two objects to an instruction — the person-typed wrapper and the
     * ninja-typed component it exposes — and {@code Processor.Helper.apply} fans an instruction out over both,
     * summing the results as a multiplicity. {@code noobj} is the identity ({@code {noobj,29} = 29}), so an untyped
     * read sees BOTH levels while a field only one level carries still answers alone:
     * <pre>
     * *marko>>name       [-- {'marko','dark_assassin'}     --]
     * *marko>>weapon     [-- 'bulldozer' (no weapon above) --]
     * </pre>
     * A call whose DOMAIN NAMES a type ({@code fight?dom=ninja}) reaches only the candidate whose OWN type is
     * that name — the ninja component, not the wrapper. There is no selection step: each candidate is resolved
     * and invoked on its own, and the type system says which one the domain is about. The wrapper is accepted
     * where a {@code ninja} is expected (the object at a fiber answers for it),
     * which is type acceptance, not dispatch identity; the ninja is the object the definition was written for, so a
     * body reading {@code >>weapon} sees the ninja's. A domain naming no component type ({@code ponder?dom=person})
     * answers on the wrapper.
     * <p>
     * A {@code typer} ({@code as}, {@code type}, {@code vid}, {@code tid}) is OPAQUE: it decides what the value IS,
     * so it cannot be distributed over the old basis and applies to the wrapper alone — one result, poly erased.
     * <p>
     * One level only, and only for a wrapper: a candidate's own components do not join
     * ({@link #testDelegationIsOneLevelDeep}), and a {@code rec[k1,k2]} FIELD declaration is not a wrapper
     * ({@link #testFieldDeclarationIsNotAWrapper}).
     */
    @ParameterizedTest
    @TestData(value = {
            "person -> rec[role]::T[?[role=>rec::T,name=>?str::T]]@person",
            "ninja -> rec::T@ninja",
            "marko -> person[ninja]::[role=>ninja::[name=>'dark_assassin',weapon=>'bulldozer'],name=>'marko']@marko",
            "fight -> |inst?<=ninja(){ \"${>>name} is your name and ${>>weapon} is your weapon\" }",
            "inst?<=ninja(){ \"${>>weapon} fun\" }@love",
            "ponder -> |inst?<=person(){ 'a person ponders' }",
    })
    @CsvSource(value = {
            // the human's case: the call's domain is ninja, so it reaches the ninja COMPONENT -- the body's
            // >>weapon reads the ninja's weapon, not the wrapper's absent one
            "*marko.fight()            % 'dark_assassin is your name and bulldozer is your weapon'",
            // ... and the same call spelled with its type explicitly reaches the same candidate
            "*marko.fight?<=ninja()    % 'dark_assassin is your name and bulldozer is your weapon'",
            // a domain that names no component type answers on the wrapper, unchanged
            "*marko.ponder()           % 'a person ponders'",
            // untyped: both levels answer, summed as a multiplicity
            "*marko>>name              % {'marko','dark_assassin'}",
            // ... and noobj is the identity, so the one level that has a weapon answers alone
            "*marko>>weapon            % 'bulldozer'",
            //
            "*marko.love()             % 'bulldozer fun'",
            // a typer is opaque: one result, the instantiation erased
            "*marko.as(person::T)      % person::[role=>ninja::[name=>'dark_assassin',weapon=>'bulldozer'],name=>'marko']",
            "*marko.type()             % rec[role]::T[?[role=>rec::T,name=>?str::T]]@person[ninja]",
    }, delimiter = '%', quoteCharacter = '~')
    public void testWrapperDelegation(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    /**
     * ONE LEVEL DEEP: the candidates are the wrapper and the components its DECLARATION names — nothing else. A
     * component that is itself a wrapper is applied to as the object it is, so its own {@code role} does not join
     * the stream and {@code 'deep'} (the inner wrapper's component) never answers.
     */
    @ParameterizedTest
    @TestData(value = {
            "person -> rec[role]::T[?[role=>rec::T,name=>?str::T,age=>int::T]]@person",
            "ninja -> rec::T@ninja",
            "nested -> person[ninja]::[role=>person[ninja]::[role=>ninja::[name=>'deep'],name=>'middle',age=>32],name=>'outer',age=>29]@nested",
    })
    @CsvSource(value = {
            "*nested>>name                         % {'outer','middle'}",
            "*nested>>age                          % {32,29}",
    }, delimiter = '%')
    public void testDelegationIsOneLevelDeep(final String code, final String expected) {
        checkCodeParseApply(LOG, code, expected);
    }

    /**
     * A FIELD DECLARATION IS NOT A WRAPPER. {@code rec[k1,k2]} names the keys a literal carries — its bracket is a
     * field list, not an instantiation — so it exposes no fibers and {@code k1}'s rec is not a candidate. The
     * narrowing matters: fanning out here would apply the instruction to {@code [a=>1]} under a domain of
     * {@code rec[k1,k2]}, which the component does not satisfy, and the read would fail instead of answering.
     */
    @Test
    public void testFieldDeclarationIsNotAWrapper() {
        AbstractMetatronTest.checkCodeParseApply(LOG, "rec[k1,k2]::[k1=>[a=>1],k2=>2]>>k1", "[a=>1]");
    }

    /**
     * THE SUM IS A MULTIPLICITY, AND A FAIL IS NOT THE IDENTITY. The wrapper exposes two components of the same
     * type, so a call whose domain is that type reaches both and their answers are summed ({@code {11,3}}). When one
     * of them genuinely
     * errors — the sidekick's {@code power} is a str where the instruction's range demands an int — the fail
     * PROPAGATES: the other candidate's {@code 11} must not be returned in its place, or a broken delegation would
     * read as a successful one.
     */
    @ParameterizedTest
    @TestData(value = {
            "person -> rec[role,sidekick]::T[?[role=>rec::T,sidekick=>rec::T,name=>?str::T]]@person",
            "ninja -> rec::T@ninja",
            "twin -> person[ninja,ninja]::[role=>ninja::[name=>'kunoichi',power=>11],sidekick=>ninja::[name=>'apprentice',power=>3],name=>'marko']@twin",
            "lame -> person[ninja,ninja]::[role=>ninja::[name=>'kunoichi',power=>11],sidekick=>ninja::[name=>'apprentice',power=>'feeble'],name=>'marko']@lame",
            "rank -> |inst?rng=int&dom=ninja(){ >>power }",
    })
    @CsvSource(value = {
            "*twin.rank()    % {11,3}",
            "*lame.rank()    % <ERROR>",
    }, delimiter = '%')
    public void testDelegationSumsCandidatesAndPropagatesFail(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    /**
     * A WRAPPER MAY EXPOSE TWO COMPONENTS. {@code duo_person} declares {@code rec[role,position]} — two fibers — and
     * an instance names a type for each ({@code duo_person[duo_ninja,duo_position]}), so the wrapper is a stream of
     * three objects to an instruction: itself, its {@code role} (a {@code duo_ninja}) and its {@code position} (a
     * {@code duo_position}). This test pins the DECLARATION and the VALUE:
     * <pre>
     * *marko                         round-trips, both components carried
     * *marko.type()                  the instantiation keeps both type arguments
     * *marko>>role, >>position       each component reads out as the type the instantiation named for it
     * </pre>
     * A fiber at coefficient one is REQUIRED: the same literal with {@code position} omitted is not a
     * {@code duo_person[duo_ninja,duo_position]} and fails at parse time. Absence would be legal only for
     * {@code rec[role,position{?}]} (or a {@code position} the declaration types as optional).
     */
    @ParameterizedTest
    @TestData(value = {
            "duo_position -> rec::T@duo_position",
            "duo_person -> rec[role,position]::T[?[name=>str::T,age=>nat::T,role=>rec::T,position=>rec::T]]@duo_person",
            "duo_ninja -> rec::T@duo_ninja",
            "marko -> duo_person[duo_ninja,duo_position]::[name=>'marko',age=>29,role=>duo_ninja::[name=>'dark_assassin'],position=>duo_position::[title=>'sniper',rank=>5]]@marko",
    })
    @CsvSource(value = {
            // the value round-trips: the same rec under the same instantiated type
            "*marko               % duo_person[duo_ninja,duo_position]::[name=>'marko',age=>29,role=>duo_ninja::[name=>'dark_assassin'],position=>duo_position::[title=>'sniper',rank=>5]]",
            // ... and its type round-trips its DECLARATION and both type arguments
            "*marko.type()        % rec[role,position]::T[?[name=>str::T,age=>nat::T,role=>rec::T,position=>rec::T]]@duo_person[duo_ninja,duo_position]",
            // each component is carried intact, under the type the instantiation named for its fiber
            "*marko>>role         % duo_ninja::[name=>'dark_assassin']",
            "*marko>>position     % duo_position::[title=>'sniper',rank=>5]",
            // a fiber at coefficient one is required: position is declared and absent
            "duo_person[duo_ninja,duo_position]::[name=>'marko',age=>29,role=>duo_ninja::[name=>'dark_assassin']] % <ERROR>",
    }, delimiter = '%', quoteCharacter = '~')
    public void testTwoComponentWrapperConstruction(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    /**
     * EITHER FIBER ANSWERS. A {@code duo_person[duo_ninja,duo_position]} value holds a ninja at {@code role} and a
     * position at {@code position}, and the object at a fiber answers for it, so it is acceptable where
     * {@code duo_ninja::T}, where {@code duo_position::T} and where its own {@code duo_person::T} is expected — and
     * nowhere else ({@code duo_baker::T}, an unrelated declared type, rejects it).
     */
    @ParameterizedTest
    @TestData(value = {
            "duo_position -> rec::T@duo_position",
            "duo_person -> rec[role,position]::T[?[name=>str::T,age=>nat::T,role=>rec::T,position=>rec::T]]@duo_person",
            "duo_ninja -> rec::T@duo_ninja",
            "duo_baker -> rec::T@duo_baker",
    })
    @CsvSource(value = {
            // value                                                    % type           % matches
            "duo_person[duo_ninja,duo_position]::[name=>'marko',age=>29,role=>duo_ninja::[name=>'dark_assassin'],position=>duo_position::[title=>'sniper',rank=>5]] % duo_ninja::T     % true",
            "duo_person[duo_ninja,duo_position]::[name=>'marko',age=>29,role=>duo_ninja::[name=>'dark_assassin'],position=>duo_position::[title=>'sniper',rank=>5]] % duo_position::T  % true",
            "duo_person[duo_ninja,duo_position]::[name=>'marko',age=>29,role=>duo_ninja::[name=>'dark_assassin'],position=>duo_position::[title=>'sniper',rank=>5]] % duo_person::T    % true",
            "duo_person[duo_ninja,duo_position]::[name=>'marko',age=>29,role=>duo_ninja::[name=>'dark_assassin'],position=>duo_position::[title=>'sniper',rank=>5]] % duo_baker::T     % false",
    }, delimiter = '%', quoteCharacter = '~')
    public void testTwoFiberWrapperIsAcceptedWhereEitherFiberIs(final String value, final String type, final boolean matches) {
        checkMatches(LOG, value, type, matches);
    }

    /**
     * THE FAN-OUT IS DISJOINT PER KEY. Delegation hands the SAME read to every candidate, and {@code noobj} is the
     * identity, so a key only one level carries answers ALONE while a key two levels carry is summed:
     * <pre>
     * *marko>>title   [-- 'sniper'                   --]   the position component alone
     * *marko>>rank    [-- 5                          --]   the position component alone
     * *marko>>age     [-- 29                         --]   the wrapper alone (no component has an age)
     * *marko>>name    [-- {'marko','dark_assassin'} --]  the wrapper AND the role component
     * </pre>
     * A single-valued expectation is also a multiplicity claim: {@code >>title} answering {@code 'sniper'} rather
     * than {@code {'sniper','sniper'}} is the statement that the read reached ONE component.
     */
    @ParameterizedTest
    @TestData(value = {
            "duo_position -> rec::T@duo_position",
            "duo_person -> rec[role,position]::T[?[name=>str::T,age=>nat::T,role=>rec::T,position=>rec::T]]@duo_person",
            "duo_ninja -> rec::T@duo_ninja",
            "marko -> duo_person[duo_ninja,duo_position]::[name=>'marko',age=>29,role=>duo_ninja::[name=>'dark_assassin'],position=>duo_position::[title=>'sniper',rank=>5]]@marko",
    })
    @CsvSource(value = {
            "*marko>>title     % 'sniper'",
            "*marko>>rank      % 5",
            "*marko>>age       % 29",
            "*marko>>name      % {'marko','dark_assassin'}",
    }, delimiter = '%', quoteCharacter = '~')
    public void testTwoComponentFanOutIsDisjointPerKey(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    /**
     * A NAMED DOMAIN REACHES THE COMPONENT THAT IS THAT TYPE. There is no selection step: each candidate is
     * resolved and invoked on its own, and the type system says which candidate the domain is about (see
     * {@code Processor.Helper#applicable}). {@code duo_aim} is declared {@code dom=duo_position}, so its body's
     * {@code >>title} reads the position component's title, 'sniper' — the wrapper is not a position by identity,
     * it is only accepted where one is expected.
     * <p>
     * The same shape declared {@code dom=duo_ninja} does NOT fire on the position component: it reaches the {@code
     * role} component, whose own {@code >>title} is absent — hence the fail, not 'sniper'. {@code duo_shadow}
     * is the positive control for that half: a {@code dom=duo_ninja} body reading {@code >>name} answers with the
     * role component's name, 'dark_assassin'.
     */
    @ParameterizedTest
    @TestData(value = {
            "duo_position -> rec::T@duo_position",
            "duo_person -> rec[role,position]::T[?[name=>str::T,age=>nat::T,role=>rec::T,position=>rec::T]]@duo_person",
            "duo_ninja -> rec::T@duo_ninja",
            "marko -> duo_person[duo_ninja,duo_position]::[name=>'marko',age=>29,role=>duo_ninja::[name=>'dark_assassin'],position=>duo_position::[title=>'sniper',rank=>5]]@marko",
            "duo_aim -> |inst?<=duo_position(){ >>title }",
            "duo_strike -> |inst?<=duo_ninja(){ >>title }",
            "duo_shadow -> |inst?<=duo_ninja(){ >>name }",
    })
    @CsvSource(value = {
            // dom=position reaches the position component, and its body sees the position's title
            "*marko.duo_aim()                   % 'sniper'",
            // ... the same call spelled with its type explicitly reaches the same candidate
            "*marko.duo_aim?<=duo_position()    % 'sniper'",
            // dom=ninja reaches the ROLE component; its own >>title is absent, so the body fails rather than
            // answering the position component's 'sniper'
            "*marko.duo_strike()                % <ERROR>",
            // ... and the positive control: the same domain reads the role component's own name
            "*marko.duo_shadow()                % 'dark_assassin'",
    }, delimiter = '%', quoteCharacter = '~')
    public void testTwoComponentTypedSelection(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    /**
     * AN INSTRUCTION BOTH COMPONENTS DECLARE ANSWERS BOTH, PER CANDIDATE. {@code ninja} and {@code position} each
     * define {@code describe} ({@code describe?dom=duo_ninja} and {@code describe?dom=duo_position}), so an untyped
     * {@code *marko.describe()} answers BOTH components (multiplicity two), and
     * {@code *marko.describe?<=duo_position()} answers one:
     * <pre>
     * *marko>>role.describe()            [-- 'a shadow'                     --]
     * *marko>>position.describe()        [-- 'sniper report'               --]
     * *marko.describe()                  [-- {'a shadow','sniper report'}  --]
     * *marko.describe?<=duo_position()   [-- 'sniper report'               --]
     * </pre>
     * This used to fail with {@code unable to determine inst function}: the selector's identity gate dropped both
     * overloads for the wrapper, and the single-candidate fallback then handed the processor an unresolved inst
     * whose failure propagated before any component was tried. Per-candidate resolution fixes it — the wrapper's
     * unresolved inst is not applicable and contributes the identity, while each component resolves its own
     * overload. {@link #testTwoComponentTypedSelection} is the same rule with only one overload in play.
     * <p>
     * A bare {@code describe -> ...} write would not declare a second overload at all (the address is the same, so
     * the second write REPLACES the first); the query-bearing address used here is what keeps both.
     */
    @ParameterizedTest
    @TestData(value = {
            "duo_position -> rec::T@duo_position",
            "duo_person -> rec[role,position]::T[?[name=>str::T,age=>nat::T,role=>rec::T,position=>rec::T]]@duo_person",
            "duo_ninja -> rec::T@duo_ninja",
            "marko -> duo_person[duo_ninja,duo_position]::[name=>'marko',age=>29,role=>duo_ninja::[name=>'dark_assassin'],position=>duo_position::[title=>'sniper',rank=>5]]@marko",
            "describe?dom=duo_ninja -> |inst?<=duo_ninja(){ 'a shadow' }",
            "describe?dom=duo_position -> |inst?<=duo_position(){ 'sniper report' }",
    })
    @CsvSource(value = {
            // each component answers its own overload when the call is addressed to it
            "*marko>>role.describe()                  % 'a shadow'",
            "*marko>>position.describe()              % 'sniper report'",
            // on the WRAPPER each call reaches the component(s) that declare a body -- the named domain reaches
            // exactly the component it names
            "*marko.describe()                        % {'a shadow','sniper report'}",
            "*marko.describe?<=duo_position()         % 'sniper report'",
    }, delimiter = '%', quoteCharacter = '~')
    public void testTwoComponentSharedInstructionName(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    /**
     * ONE LEVEL DEEP, WITH TWO PARTS. The candidates are the wrapper and the components its DECLARATION names —
     * nothing else. Here the {@code role} fiber itself holds a wrapper, so the role candidate is applied to AS THE
     * OBJECT IT IS: its own fields join the read ({@code 'middle'}, {@code 32}) and its components' fields do not
     * ({@code 'deep'} never answers, and neither does the inner wrapper's position title {@code 'inner_title'}):
     * <pre>
     * *nested>>name    [-- {'outer','middle'}  --]   not 'deep'
     * *nested>>age     [-- {29,32}             --]
     * *nested>>title   [-- 'outer_title'       --]   not 'inner_title'
     * *nested>>rank    [-- 5                   --]   not {5,7}
     * </pre>
     */
    @ParameterizedTest
    @TestData(value = {
            "duo_position -> rec::T@duo_position",
            "duo_person -> rec[role,position]::T[?[name=>str::T,age=>nat::T,role=>rec::T,position=>rec::T]]@duo_person",
            "duo_ninja -> rec::T@duo_ninja",
            "nested -> duo_person[duo_ninja,duo_position]::[name=>'outer',age=>29,role=>duo_person[duo_ninja,duo_position]::[name=>'middle',age=>32,role=>duo_ninja::[name=>'deep'],position=>duo_position::[title=>'inner_title',rank=>7]],position=>duo_position::[title=>'outer_title',rank=>5]]@nested",
    })
    @CsvSource(value = {
            "*nested>>name     % {'outer','middle'}",
            "*nested>>age      % {29,32}",
            "*nested>>title    % 'outer_title'",
            "*nested>>rank     % 5",
    }, delimiter = '%', quoteCharacter = '~')
    public void testTwoComponentDelegationIsOneLevelDeep(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    /**
     * DISPATCH IS "GO TO THE FIELD, GET THE VALUE, APPLY THE INSTRUCTION" — the instantiation's poly is NOT the
     * boundary. {@code loaf} is a {@code person[baker]}, so its poly names {@code baker}, but its {@code role} field
     * actually holds a {@code ninja}. A call whose domain is {@code ninja} resolves against that FIELD'S VALUE and
     * runs on it; nothing about the declared instantiation enters the decision, although {@code ninja} is not one
     * of {@code person[baker]}'s entries: per-candidate resolution plus the identity question is the whole rule.
     * <p>
     * A single-valued expectation is also a multiplicity claim: {@code *loaf.fight()} answering ONE string says the
     * wrapper did not take the call (it would have contributed a second answer), while {@code ponder?dom=person}
     * shows the wrapper still answers its own type.
     */
    @ParameterizedTest
    @TestData(value = {
            "person -> rec[role]::T[?[role=>rec::T,name=>?str::T]]@person",
            "ninja -> rec::T@ninja",
            "baker -> rec::T@baker",
            "loaf -> person[baker]::[role=>ninja::[name=>'dark_assassin',weapon=>'bulldozer'],name=>'loaf']@loaf",
            "fight -> |inst?<=ninja(){ \"${>>name} is your name and ${>>weapon} is your weapon\" }",
            "ponder -> |inst?<=person(){ 'a person ponders' }",
    })
    @CsvSource(value = {
            // the ROLE holds a ninja: resolution runs on the field's value and the instruction fires there
            "*loaf.fight()            % 'dark_assassin is your name and bulldozer is your weapon'",
            "*loaf.fight?<=ninja()    % 'dark_assassin is your name and bulldozer is your weapon'",
            // ... and the wrapper answers the domain that names ITS type
            "*loaf.ponder()           % 'a person ponders'",
            // the untyped read still sums the levels the wrapper exposes
            "*loaf>>name              % {'loaf','dark_assassin'}",
    }, delimiter = '%', quoteCharacter = '~')
    public void testDelegationReachesTheFieldValueNotTheInstantiation(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    /**
     * RECURSIVE FIBRATION: a fiber may hold a fibered rec. A {@code dojo[master]} holds a {@code master} at its
     * {@code head}, and that {@code master[form]} holds a {@code form} at its {@code style}, so the dojo is accepted
     * where a master is expected (the object at its fiber) and where a form is expected (the object at its fiber's
     * fiber). Only fibers answer: {@code name} is a field, not a fiber, so the dojo is not a str.
     */
    @ParameterizedTest
    @TestData(value = {
            "form -> rec::T@form",
            "master -> rec[style]::T[?[name=>str::T,style=>rec::T]]@master",
            "dojo -> rec[head]::T[?[name=>str::T,head=>rec::T]]@dojo",
            "rival -> rec::T@rival",
    })
    @CsvSource(value = {
            // value                                                                                          % type       % matches
            "dojo[master]::[name=>'kodokan',head=>master[form]::[name=>'kano',style=>form::[moves=>7]]]  % dojo::T    % true",
            "dojo[master]::[name=>'kodokan',head=>master[form]::[name=>'kano',style=>form::[moves=>7]]]  % master::T  % true",
            "dojo[master]::[name=>'kodokan',head=>master[form]::[name=>'kano',style=>form::[moves=>7]]]  % form::T    % true",
            "dojo[master]::[name=>'kodokan',head=>master[form]::[name=>'kano',style=>form::[moves=>7]]]  % rival::T   % false",
            "dojo[master]::[name=>'kodokan',head=>master[form]::[name=>'kano',style=>form::[moves=>7]]]  % rec::T     % true",
            "dojo[master]::[name=>'kodokan',head=>master[form]::[name=>'kano',style=>form::[moves=>7]]]  % str::T     % false",
    }, delimiter = '%', quoteCharacter = '~')
    public void testRecursiveFibration(final String value, final String type, final boolean matches) {
        checkMatches(LOG, value, type, matches);
    }

    /**
     * INSTRUCTIONS ON THE OUTER AND INNER TYPES OF A RECURSIVE FIBRATION. Dispatch is one level deep: the dojo's
     * candidates are itself and the master at its {@code head}, so a dojo instruction runs on the dojo and a master
     * instruction on the master, while a form instruction finds no candidate that is a form (the form is two fibers
     * down) and answers {@code noobj}. Reached through {@code >>head}, the master is a wrapper in its own right, and its
     * form answers. Untyped reads fan out one level the same way.
     */
    @ParameterizedTest
    @TestData(value = {
            "form -> rec::T@form",
            "master -> rec[style]::T[?[name=>str::T,style=>rec::T]]@master",
            "dojo -> rec[head]::T[?[name=>str::T,head=>rec::T]]@dojo",
            "kodokan -> dojo[master]::[name=>'kodokan',head=>master[form]::[name=>'kano',style=>form::[moves=>7]]]@kodokan",
            "visit -> |inst?<=dojo(){ 'a dojo visit' }",
            "bow -> |inst?<=master(){ >>name }",
            "drill -> |inst?<=form(){ >>moves }",
    })
    @CsvSource(value = {
            // ---- the outer type's instruction runs on the wrapper ----
            "*kodokan.visit()                 % 'a dojo visit'",
            // ---- the fiber's type: dispatch reaches the master at head ----
            "*kodokan.bow()                   % 'kano'",
            // ---- one level deep: the form two fibers down is no candidate of the dojo ----
            "*kodokan.drill()                 % noobj",
            // ---- ...but it is a candidate of the master that holds it ----
            "*kodokan>>head.drill()         % 7",
            "*kodokan>>head.bow()           % 'kano'",
            // ---- untyped reads fan out one level: the dojo's and the master's names, never the form's moves ----
            "*kodokan>>name                   % {'kodokan','kano'}",
            "*kodokan>>head>>moves          % 7",
            "*kodokan>>moves                  % noobj",
    }, delimiter = '%', quoteCharacter = '~')
    public void testRecursiveFibrationInstructions(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    /**
     * A REC BRACKET'S FIELDS MUST BE THERE. Every field a bracket names is present unless its entry admits zero; extra
     * fields are free. A named bracket at a field ({@code pin}'s {@code at} is a {@code coord}) is read in turn, so a
     * value missing a field one level down fails the outer type as well.
     */
    @ParameterizedTest
    @TestData(value = {
            "coord -> rec[x,y]::T@coord",
            "pin -> rec[aa,bb]::T[?[aa=>coord::T]]@pin",
    })
    @CsvSource(value = {
            // value                        % type                % matches
            "[a=>1,b=>2]                    % rec[a,b]::T         % true",
            "[a=>1]                         % rec[a,b]::T         % false",
            "[a=>1,b=>2,c=>3]               % rec[a,b]::T         % true",
            "[a=>1]                         % rec[a,b{?}]::T      % true",
            "[c=>1]                         % rec[a{?},b{?}]::T   % true",
            "[x=>1,y=>2]                    % coord::T            % true",
            "[x=>1]                         % coord::T            % false",
            "[aa=>[x=>1,y=>2],bb=>3]        % pin::T              % true",
            "[aa=>[x=>1],bb=>3]             % pin::T              % false",
            "[aa=>[x=>1,y=>2]]              % pin::T              % false",
    }, delimiter = '%', quoteCharacter = '~')
    public void testFibrationFields(final String value, final String type, final boolean matches) {
        checkMatches(LOG, value, type, matches);
    }

    /**
     * AN ANONYMOUS REC BRACKET NAMES FIBERS TOO ({@code Type#isFibered}): the value at one of its fields answers for
     * the rec, so {@code rec[k1,k2]::[k1=>scout::[…],k2=>2]} is accepted where a scout or an int is expected. A plain
     * rec names no fibers, so its fields never answer for it.
     */
    @ParameterizedTest
    @TestData(value = {"scout -> rec::T@scout"})
    @CsvSource(value = {
            // value                                          % type       % matches
            "rec[k1,k2]::[k1=>scout::[name=>'kai'],k2=>2]     % scout::T   % true",
            "rec[k1,k2]::[k1=>scout::[name=>'kai'],k2=>2]     % int::T     % true",
            "rec[k1,k2]::[k1=>scout::[name=>'kai'],k2=>2]     % str::T     % false",
            "[k1=>scout::[name=>'kai'],k2=>2]                 % int::T     % false",
    }, delimiter = '%', quoteCharacter = '~')
    public void testAnonymousFibration(final String value, final String type, final boolean matches) {
        checkMatches(LOG, value, type, matches);
    }

    /**
     * A FIBER MAY HOLD A MULTIPLICITY. A {@code troupe}'s {@code crew} holds any number of recs, so a troupe holding two
     * scouts is accepted where two scouts are expected and not where one is; a troupe holding one scout is one scout.
     */
    @ParameterizedTest
    @TestData(value = {
            "scout -> rec::T@scout",
            "troupe -> rec[crew]::T[?[crew=>rec{*}::T]]@troupe",
    })
    @CsvSource(value = {
            // value                                                              % type          % matches
            "troupe[scout]::[crew=>{scout::[name=>'a'],scout::[name=>'b']}]       % scout{2}::T   % true",
            "troupe[scout]::[crew=>{scout::[name=>'a'],scout::[name=>'b']}]       % scout{*}::T   % true",
            "troupe[scout]::[crew=>{scout::[name=>'a'],scout::[name=>'b']}]       % scout::T      % false",
            "troupe[scout]::[crew=>scout::[name=>'a']]                            % scout::T      % true",
    }, delimiter = '%', quoteCharacter = '~')
    public void testFiberMultiplicity(final String value, final String type, final boolean matches) {
        checkMatches(LOG, value, type, matches);
    }

    /**
     * A FIBER MAY HOLD A MONO. {@code rec[o]::T[?[o=>int::T]]@ex} is a rec whose fiber {@code o} holds an int, so an
     * {@code ex} is both a rec and an int: it is accepted where an int is expected, and an int instruction reaches the
     * int at {@code o} (the wrapper itself is a rec, not an int). A domain no candidate has answers {@code noobj}.
     */
    @ParameterizedTest
    @TestData(value = {"rec[o]::T[?[o=>int::T]]@ex"})
    @CsvSource(value = {
            "ex::[o=>12].plus?<=int(45)           % 57",
            "ex::[o=>12].plus(45)                 % <ERROR>",
            "ex::[o=>12]>>o                       % 12",
            "ex::[o=>12].plus?<=str('x')          % noobj",
    }, delimiter = '%', quoteCharacter = '~')
    public void testMonoFiberInstructions(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    /**
     * The same {@code ex} judged as a value: a rec, an {@code ex}, and an int through its fiber, but not a str.
     */
    @ParameterizedTest
    @TestData(value = {"rec[o]::T[?[o=>int::T]]@ex1"})
    @CsvSource(value = {
            // value          % type      % matches
            "ex1::[o=>12]      % int::T    % true",
            "ex1::[o=>12]      % ex1::T     % true",
            "ex1::[o=>12]      % rec::T    % true",
            "ex1::[o=>12]      % str::T    % false",
    }, delimiter = '%', quoteCharacter = '~')
    public void testMonoFiberAcceptance(final String value, final String type, final boolean matches) {
        checkMatches(LOG, value, type, matches);
    }
}
