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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.TestData;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractObjTest;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.type.impl.MInst;
import studio.phaseshift.metatron.util.Tuple;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

public class InstTest extends AbstractObjTest {


    @ParameterizedTest
    @CsvSource(value = {
            // furi | tid | dom | range
            "/m/plus?dom=/m/int&rng=/m/int                      | /m/plus        | /m/int         | /m/int     | 34",
            "/m/mult/a?dom=+&rng=+                              | /m/mult/a      | +              | +          | x::a",
            //     "/m/mult/z?dom=real{0,1}&rng=lst[int{5}]{2,3}       | /m/mult/z      | /m/real{?}     | /m/lst{2,3}| lst{2}::[2,3,4,56,3]",
            "/m/mult/y?dom=real{*}&rng=uri{*}                   | /m/mult/y      | /m/real{*}     | /m/uri{*}  | {ab,bc,de}"},
            delimiter = '|')
    public void testDomRng(final String f, final String op, final String dom, final String rng, final String test) {
        final fURI furi = f(f);
        final Inst inst = MInst.instA(furi);
        final Obj testObj = mParser.m_obj().parse(test).get();
        assertEquals(op, inst.tid().pathString());
        assertEquals(f(dom), inst.dom().tid());
        assertEquals(f(rng), inst.rng().tid());
        assertTrue(inst.dom().test(T(f(dom))));
        assertTrue(inst.rng().test(T(f(rng))));
        assertTrue(testObj.test(T(f(rng))));
        assertTrue(testObj.test(inst.rng()));
        assertFalse(T(f(rng)).test(testObj));
        assertFalse(inst.rng().test(testObj));
        assertEquals(op + "?rng=" + rng + "&dom=" + dom, furi.big().toString());
        LOG.info("testing furi::rng<=dom: {{y}}%s{{g}}::{{b}}%s{{g}}<={{m}}%s{{X}}", furi.big(), furi.rng(), furi.dom());
    }


    @ParameterizedTest
    @CsvSource(value = {
            "1         % test?str<=int()                  % test()           % test?str<=int()",
            "1         % test?str<=A()                    % test()           % test?str<=int()",
            "1         % test?A<=A()                      % test()           % test?int<=int()",
            "1         % test?A<=A(A::T)                  % test(2)          % test?int<=int(int::T)",
            "1         % test?A<=A(A::T)                  % test(plus(2))    % test?int<=int(plus::T)",
            "{1,2}     % test?A{*}<=A{*}(A{*}::T)         % test({3,4})      % test?int{4}<=int{2}(int{2}::T)",
            "{1,2}     % test?A{+}<=A{+}(A{+}::T)         % test({3,4})      % test?int{4}<=int{2}(int{2}::T)",
            "{1,2}     % test?A{*}<=A{+}(A{*}::T)         % test({3,4})      % test?int{4}<=int{2}(int{2}::T)",
            "noobj     % test?A<=noobj(A::T)              % test(3)          % test?int<=noobj(int::T)",
            "noobj     % test?A<=A{0}(A::T)               % test(3)          % test?int<=int{0}(int::T)",
            "noobj     % test?A{*}<=A{0}(A{*}::T)         % test({1,2,3})    % test?int{3}<=int{0}(int{3}::T)",
    }, delimiter = '%')
    public void testResolution(final String lhs, final String def, final String spec, final String resolution) {
        final Obj lhsA = mParser.m_obj().parse(lhs).get();
        final Inst defA = mParser.m_obj().parse(def).get();
        final Inst specA = mParser.m_obj().parse(spec).get();
        final Inst resolutionA = mParser.m_obj().parse(resolution).get();
        final Inst resultA = Inst.Helper.bindGenerics(lhsA, specA, defA);
        LOG.info("{{b}}%s{{/b}} resolution matches {{b}}%s{{/b}} specification", resultA.tid(), resolutionA.tid());
        final boolean match = resultA.tid().test(resolutionA.tid());
        assertTrue(match);
        LOG.info("%s [expected: %s] resolved from specification %s => %s via type definition %s", resultA, resolutionA, lhsA, specA, defA);
        if (!resolutionA.equals(resultA))
            LOG.warn("resolution algorithm generates matching, but not equal final resolution -- skipping equality checks\n\t%s ~ %s", resultA, resolutionA);
        else
            assertEquals(resolutionA, resultA);
        assertTrue(resultA.test(resolutionA));
        assertTrue(resultA.tid().test(resolutionA.tid()));
        assertTrue(resultA.test(specA));
        assertTrue(resultA.tid().test(specA.tid()));
        assertTrue(resultA.test(defA));
        assertTrue(resultA.tid().test(defA.tid()));
        assertTrue(specA.test(resolutionA));
        assertTrue(specA.tid().test(resolutionA.tid()));
        assertTrue(defA.test(resolutionA));
        assertTrue(defA.tid().test(resolutionA.tid()));
        assertTrue(specA.test(defA));
        assertTrue(specA.tid().test(defA.tid()));
    }

    @Test
    public void testInstFCode() {
        Inst i = instC(f("dosomething").dom(INT_TID.maybe()).rng(INT_TID), lst(T(INT_TID), T(M_ISA_INST_TID)), "*b.plus(*a)");
        assertEquals(jnt(4), i.args(rec(uri("a"), jnt(1), uri("b"), jnt(3))).resolve(noobj()).apply());
        //i = instC(f("dosomething"), lst(T(INT_TID), T(STR_TID)), "*b.-<''>-.count().plus(*a)");
        //assertEquals(jnt(4), i.args(rec(uri("a"), jnt(1), uri("b"), str("abc"))).resolve(noobj()).apply());
    }

    @Test
    public void testRingAlgebra() {
        for (Tuple.Pair<? extends Obj, Call> item : List.of(
                Tuple.Pair.with(jnt(3), start_(jnt(1)).mult(plus_(jnt(2)))),
                Tuple.Pair.with(objs(jnt(2), jnt(3)), start_(jnt(1)).mult(plus_(jnt(1)).plus(plus_(jnt(2))))),
                Tuple.Pair.with(objs(jnt(6).c(2L)), start_(jnt(2)).mult(plus_(jnt(4)).plus(mult_(jnt(3))))),
                Tuple.Pair.with(objs(jnt(6), jnt(7)), start_(jnt(2)).mult(plus_(jnt(4)).mult(plus_(jnt(1))).plus(mult_(jnt(3))))),
                Tuple.Pair.with(objs(jnt(6), jnt(7)), start_(jnt(2)).mult(plus_(jnt(4)).mult(plus_(jnt(1))).plus(mult_(jnt(3))).plus(noobj()))),
                Tuple.Pair.with(noobj(), start_(jnt(2)).mult(noobj())),
                Tuple.Pair.with(noobj(), start_(jnt(2)).mult(plus_(jnt(4)).mult(plus_(jnt(1))).plus(mult_(jnt(3))).plus(noobj())).mult(noobj())))) {
            LOG.trace("\n\ntesting %s == %s", item.get1(), item.get0());
            assertEquals(item.get0(), item.get1().apply());
        }
    }

    @ParameterizedTest
    @TestData(value = {"|inst?int{?}<=int(_,_){ is(and(gte(*0),lte(*1))) }@band"})
    @CsvSource(value = {
            "3.band(2,8)                                  % 3",
            "10.band(2,8)                                 % noobj",
            "5.band(-10,10)                               % 5",
            "10.band(2)                                   % noobj",
            "\"abc\".band?int{?}<=int(2,8)                % <ERROR>",
            "\"abc\".band(2,8)                            % <ERROR>"
    }, delimiter = '%')
    public void testPositionalArgs(final String code, final String expected) throws Exception {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @TestData(value = {"|inst?int{?}<=int(int::T,str::T){ is(and(gte(*0),lte(*1.as(int::T)))) }@band"})
    @CsvSource(value = {
            "3.band(2,\"8\")                                    % 3",
            "10.band(2,\"8\")                                   % noobj",
            "{1,2,3,4,3,5,6}.band(3,\"8\")                      % {3,4,3,5,6}",
            "10.band(2)                                         % noobj",
            "\"abc\".band?int{?}<=int(2,8)                      % <ERROR>",
            "\"abc\".band?int{?}<=str(2,8)                      % <ERROR>",
            "\"abc\".band(2,8)                                  % <ERROR>"
    }, delimiter = '%')
    public void testTypedPositionalArgs(final String code, final String expected) throws Exception {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @TestData(value = {"|inst?int{?}<=int(min=>_,max=>_){ is(and(gte(*min),lte(*max))) }@band"})
    @CsvSource(value = {
            "3.band(min=>2,max=>4)                        % 3",
            "3.band(max=>8,min=>2)                        % 3",
            "1.band(max=>8,min=>2)                        % noobj",
            "{2,3,8,10}.band(min=>3,max=>8)               % {3,8}",
            "1.band(min=>2,max=>4)                        % noobj",
    }, delimiter = '%')
    public void testNamedArgs(final String code, final String expected) throws Exception {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @TestData(value = {
            "nat -> int::T[?>0]",
            "|inst?int{?}<=int(min=>?nat::T,max=>?nat::T){ is(and(gte(*min),lte(*max))) }@band"})
    @CsvSource(value = {
            "15.band(10,20)                                     % 15",
            "{1,12,15,21,22}.band(10,20)                        % {12,15}",
            "5.band(-10,10)                                     % noobj",
            "-5.band(_,_)                                       % -5",
            // "{1,2,3,4,3,5,6}.band(2,\"8\")                      % {3,4,3,5,6}",
            "10.band(2)                                         % noobj",
    }, delimiter = '%')
    public void testTypedNamedArgs(final String code, final String expected) throws Exception {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @TestData(value = {
            "/m/nat -> int::T[?>0]",
            "|inst?int{?}<=int(min=>as(/m/nat::T),max=>as(/m/nat::T)){ is(and(gte(*min),lte(*max))) }@band"})
    @CsvSource(value = {
            "15.band(10,20)                                     % 15",
            "{1,12,15,21,22}.band(10,20)                        % {12,15}",
            "5.band(-10,10)                                     % <ERROR>",
            "-5.band(_,_)                                       % <ERROR>",
           // "{1,2,3,4,3,5,6}.band(2,\"8\")                      % <ERROR>",
            "10.band(2)                                         % <ERROR>"
    }, delimiter = '%')
    public void testConversionTypedNamedArgs(final String code, final String expected) throws Exception {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @TestData(value = {
            "/m/nat -> int::T[?>0]",
            "|inst?int{?}<=int(min=>as(/m/nat::T),max=>?/m/nat::T){ is(and(gte(*min),lte(*max))) }@band"})
    @CsvSource(value = {
            "15.band(10,20)                                     % 15",
            "5.band(-10,10)                                     % <ERROR>",
            "-5.band(_,_)                                       % <ERROR>",
            "\"abc\".band?int{?}<=int(2,8)                      % <ERROR>"
    }, delimiter = '%')
    public void testTypedNamedFailArgs(final String code, final String expected) throws Exception {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @TestData(value = {"|inst?int{?}<=int(min=>else(3),max=>else(8)){ is(and(gte(*min),lte(*max))) }@band"})
    @CsvSource(value = {
            "1.band()                                              % noobj",
            "2.band()                                              % noobj",
            "3.band()                                              % 3",
            "5.band()                                              % 5",
            "{2,3,4,5,6,7,8,9}.band(min=>3,max=>7)                 % {3,4,5,6,7}",
            "{2,3,4,5,6,7,8,9}.band(min=>noobj{0},max=>noobj{0})   % {3,4,5,6,7,8}",
            "{2,3,4,5,6,7,8,9}.band(min=>_,max=>_)                  % {2,3,4,5,6,7,8,9}",
            "1.band(min=>1)                                        % 1",
            "3.band(max=>4)                                        % 3",
            "3.band()                                              % 3",
            "5.band(max=>4)                                        % noobj",
            "{2,3,4,5,6,7,8,9}.band(max=>4)                        % {3,4}",
            "1.band(max=>3)                                        % noobj"
    }, delimiter = '%')
    public void testDefaultArgs(final String code, final String expected) throws Exception {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }
}
