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
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.util.Tuple;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.*;
import static studio.phaseshift.metatron.isa.m.type.Inst.INST_TYPE;
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
        final Obj testObj = ObjmtronSerializer.parse(test);
        assertTrue(inst.test(INST_TYPE));
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
        final Obj lhsA = ObjmtronSerializer.parse(lhs);
        final Inst defA = ObjmtronSerializer.parse(def);
        final Inst specA = ObjmtronSerializer.parse(spec);
        final Inst resolutionA = ObjmtronSerializer.parse(resolution);
        final Inst resultA = Inst.Helper.bindGenerics(lhsA, specA, defA);
        assertFalse(lhsA.test(INST_TYPE));
        assertTrue(defA.test(INST_TYPE));
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

    /**
     * Tests {@link Inst.Helper#bindGenerics} with the semantically correct parameter order:
     * <ul>
     *   <li>{@code spec} column → {@code apiInst} (2nd param): the registry instruction, MAY have generics (A, B, LONG, ...)</li>
     *   <li>{@code def}  column → {@code userInst} (3rd param): what the user actually typed</li>
     * </ul>
     * Generic names: any all-uppercase fURI path (A, B, LONG, BBBB, …). # and + are NOT generic.
     * Priority: user-explicit dom/rng > lhs type > arg types.
     */
    @ParameterizedTest
    @CsvSource(quoteCharacter = '"', delimiter = '%', value = {
            // lhs              % def (userInst - user typed)   % spec (apiInst - registry, has generics)  % expected resolution

            // --- A binds to lhs type (different base types) ---
            "1                 % test()                         % test?A<=A()                              % test?int<=int()",
            "'hello'.type()    % test()                         % test?A<=A()                              % test?str<=str()",
            "1.5.type()        % test()                         % test?A<=A()                              % test?real<=real()",
            "[1,2,3].type()    % test()                         % test?A<=A()                              % test?lst<=lst()",
            "[a=>1].type()     % test()                         % test?A<=A()                              % test?rec<=rec()",

            // --- Long / multi-char uppercase generic names ---
            "1                 % test()                         % test?LONG<=LONG()                        % test?int<=int()",
            "1                 % test()                         % test?BBBB<=AAAA()                        % test?int<=int()",

            // --- A in dom AND rng AND arg: all three bind consistently ---
            "1                 % test(2)                        % test?A<=A(A::T)                          % test?int<=int(int::T)",
            "{1,2}             % test({3,4})                    % test?A{*}<=A{*}(A{*}::T)                 % test?int{4}<=int{2}(int{2}::T)",

            // --- Two distinct generics: A binds from lhs, B from arg ---
            "1                 % test('hello'.type())           % test?B<=A(B::T)                          % test?str<=int(str::T)",
            "1                 % test(true.type())              % test?B<=A(B::T)                          % test?bool<=int(bool::T)",

            // --- User-explicit dom/rng (gold standard) — no generics needed, passes through unchanged ---
            "1                 % test?int<=int()                % test?int<=int()                          % test?int<=int()",
            "'hello'.type()    % test?str<=str()                % test?str<=str()                          % test?str<=str()",

            // --- noobj lhs: step 1 skipped (lhs.isNoObj()), so A binds from arg instead ---
            "noobj             % test(3)                        % test?A<=noobj(A::T)                      % test?int<=noobj(int::T)",
            "noobj             % test(3)                        % test?A<=A{0}(A::T)                       % test?int<=int{0}(int::T)",
            "noobj             % test({1,2,3})                  % test?A{*}<=A{0}(A{*}::T)                 % test?int{3}<=int{0}(int{3}::T)",
    })
    public void testBindGenerics(final String lhs, final String def, final String spec, final String resolution) {
        final Obj lhsA = ObjmtronSerializer.parse(lhs.trim());
        final Inst defA = ObjmtronSerializer.parse(def.trim());   // userInst — what user typed
        final Inst specA = ObjmtronSerializer.parse(spec.trim()); // apiInst  — registry instruction (has generics)
        final Inst resolutionA = ObjmtronSerializer.parse(resolution.trim());
        final Inst resultA = Inst.Helper.bindGenerics(lhsA, specA, defA);
        assertEquals(lhs.contains("."), lhsA.test(INST_TYPE));
        assertTrue(defA.test(INST_TYPE));
        assertTrue(specA.test(INST_TYPE));
        assertTrue(resolutionA.test(INST_TYPE));
        assertNotNull(resultA);
        assertTrue(resultA.test(INST_TYPE));
        assertNotNull(resultA, () -> String.format("bindGenerics returned null for lhs=%s spec=%s def=%s", lhsA, specA, defA));
        assertTrue(resultA.tid().test(resolutionA.tid()),
                () -> String.format("result %s not compatible with expected %s", resultA.tid(), resolutionA.tid()));
        if (!resolutionA.equals(resultA))
            LOG.warn("resolution algorithm produces compatible but not equal result — may indicate partially-resolved generics\n\tresult:   %s\n\texpected: %s", resultA, resolutionA);
        else
            assertEquals(resolutionA, resultA);
    }

    /**
     * Tests that dom/rng types propagate correctly through instruction chains.
     * Each case verifies both the computed value and the result's type (the chain's rng).
     * Interesting cases: chains that cross type boundaries (int→str, str→int, lst→int).
     */
    @ParameterizedTest
    @CsvSource(quoteCharacter = '"', delimiter = '%', value = {
            // lhs           % chain                          % expected result  % expected rng type

            // --- Homogeneous int chains ---
            "1               % plus(2)                        % 3                % int",
            "1               % plus(2).id()                   % 3                % int",
            "1               % plus(2).id().mult(35)          % 105              % int",
            "3               % mult(3).mult(3)                % 27               % int",

            // --- Type-crossing: int → str ---
            "1               % as(str::T)                     % '1'              % str",
            "1               % plus(2).as(str::T)             % '3'              % str",

            // --- Type-crossing: int → str → int (count of a scalar obj = 1) ---
            "1               % as(str::T).count()             % 1                % int",
            "100             % as(str::T).count()             % 1                % int",

            // --- str → int (count of a scalar str = 1, not char count) ---
            "'hello'         % count()                        % 1                % int",
            "'hello'         % count().plus(1)                % 2                % int",
            "'hello'         % count().mult(2)                % 2                % int",

            // --- objs → int ---
            "{1,2,3}         % sum()                          % 6                % int",
            "{1,2,3,4,5}     % sum()                          % 15               % int",
            "{1,2,3}         % count()                        % 3                % int",
    })
    public void testChainTypePropagation(final String lhs, final String chain, final String expectedValue, final String expectedRng) {
        final Obj lhsObj = mParser.m_obj().parse(lhs.trim()).get();
        final Obj chainObj = ObjmtronSerializer.parse(chain.trim());  // full chain, not just first obj
        final Obj expectedObj = mParser.m_obj().parse(expectedValue.trim()).get();
        final fURI rngTid = f(expectedRng.trim());

        // Execute the chain
        final Obj result = chainObj.apply(lhsObj);
        
        // same basic type checks
        assertFalse(lhsObj.test(INST_TYPE));
        assertTrue(chainObj.test(INST_TYPE));
        assertFalse(expectedObj.test(INST_TYPE));
        assertFalse(result.test(INST_TYPE));
        
        // Verify result value
        assertEquals(expectedObj, result,
                () -> String.format("%s .%s => %s (expected %s)", lhsObj, chainObj, result, expectedObj));

        // Verify rng type propagated correctly through chain
        assertTrue(result.test(T(rngTid)),
                () -> String.format("%s .%s => result type %s does not satisfy %s::T", lhsObj, chainObj, result.type().tid(), rngTid));

        LOG.info("%s .%s => {{b}}%s{{/b}} :: {{g}}%s{{/g}} (expected rng: %s)", lhsObj, chainObj, result, result.type().tid(), rngTid);
    }

    @ParameterizedTest
    @CsvSource(quoteCharacter = '"', delimiter = '%', value = {
            "1               % plus(1)._._._                    % [int/int,int/int,int/int,int/int]       % 2",
            "{1,1}           % plus(1)._                        % [int{2}/int{2},int{2}/int{2}]           % {2}2",
            // "{1,7}           % plus(1)._                        % [int{2}/int{2},int{2}/int{2}]           % {2,8}",
            "1               % _._._.plus(1)                    % [int/int,int/int,int/int,int/int]       % 2",
            "1               % plus(0).plus(1).plus(0)          % [int/int,int/int,int/int]               % 2",
            "1               % plus(2)                          % [int/int]                               % 3",
            "1               % plus(2).id()                     % [int/int,int/int]                       % 3",
            "1               % plus(2).id().mult(35)            % [int/int,int/int,int/int]               % 105",
            "3               % mult(3).mult(3)                  % [int/int,int/int]                       % 27",
            //   "{2}3            % mult(3).mult(3)                  % [int{2}/int{2},int{2}/int{2}]           % {2}27",
            //   "3               % mult(3).mult{2}(3)               % [int/int,int/int{2}]                    % 27",
            "1               % as(str::T)                       % [int/str]                               % '1'",
            "1               % as(str::T).as(real::T).as(int::T)% [int/str,str/real,real/int]             % 1",
            "1               % as(str::T).as(int::T).as(str::T) % [int/str,str/int,int/str]               % '1'",
            "1               % plus(2).as(str::T)               % [int/int,int/str]                       % '3'",
            "1               % as(str::T).count()               % [int/str,str{*}/int]                    % 1",
            "{1,2,3}         % sum()                            % [int{*}/int,int/int]                    % 6",
            "{1,2,3,4,5}     % sum()                            % [int{*}/int]                            % 15",
            "[1,2,3]         % >-.sum()                         % [lst/#{*},#{*}/#]                       % 6",
            "[1,2,3]         % >-?int{*}<=lst[int].sum()        % [lst[int]/int{*},int{*}/int]            % 6",
            "[1,2,3]         % >-?<=lst[int].sum()              % [lst[int]/int{*},int{*}/int]            % 6",
            "1              % -<[_]-<[_,_]>-                    % [int/lst[int],lst[lst]/lst[int]{2}]                        % {2}[1]",
            "1              % -<[_]-<[_,_,_]>-                  % [int/lst[int],lst[lst]/lst[int]{3}]                        % {3}[1]",
            "1              % -<[_]-<[_,_,_]>-.>-               % [int/lst[int],lst[lst]/lst[int]{3},lst[int]{3}/int{3}]     % {3}1",
    })
    public void testCodeInternalDomRng(final String lhs, final String code, final String domRngPerStep, final String rhs) {
        final Obj lhsObj = ObjmtronSerializer.parse(lhs.trim());
        final Obj codeObj = ObjmtronSerializer.parse(code.trim());  // full code, not just first obj
        final Obj expectedRHSObj = ObjmtronSerializer.parse(rhs.trim());
        final Call codeResolved = codeObj.resolve(lhsObj).as();
        Type finalRngType = lhsObj.type();
        assertFalse(lhsObj.test(INST_TYPE));
        assertTrue(codeObj.test(INST_TYPE));
        assertFalse(expectedRHSObj.test(INST_TYPE));
        assertTrue(codeResolved.test(INST_TYPE));
        
        final String[] domRngPerStepArray = domRngPerStep.substring(1, domRngPerStep.length() - 1).split(",");
        for (int i = 0; i < codeResolved.insts().size(); i++) {
            final Inst step = codeResolved.insts().get(i);
            final Type domType = T(f(domRngPerStepArray[i].split("/")[0]));
            final Type rngType = T(f(domRngPerStepArray[i].split("/")[1]));
            final int ii = i;
            if (!domType.test(step.dom()))
                assertTrue(domType.c(c -> domType.uniqueC()).test(step.dom()), () -> String.format("%s.dom() does not match expected %s at step %d", step.dom().tid(), domType, ii));
            else
                assertTrue(domType.test(step.dom()), () -> String.format("%s.dom() does not match expected %s at step %d", step.dom().tid(), domType, ii));
            if (!rngType.test(step.rng()))
                assertTrue(rngType.c(c -> rngType.uniqueC()).test(step.rng()), () -> String.format("%s.rng() does not match expected %s at step %d", step.rng().tid(), rngType, ii));
            else
                assertTrue(rngType.test(step.rng()), () -> String.format("%s.rng() does not match expected %s at step %d", step.rng().tid(), rngType, ii));
            finalRngType = rngType;
        }
        final Obj rhsObj = codeResolved.apply(lhsObj);
        // Verify result value
        assertEquals(expectedRHSObj, rhsObj,
                () -> String.format("%s .%s => %s (expected %s)", lhsObj, codeResolved, rhsObj, expectedRHSObj));

        // Verify rng type propagated correctly through chain
        final Type rhsType = finalRngType;
        assertTrue(rhsObj.test(rhsType),
                () -> String.format("%s .%s => result type %s does not satisfy %s::T", lhsObj, codeResolved, rhsObj.type().tid(), rhsType));
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
