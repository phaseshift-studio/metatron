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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.TestData;
import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.Tracer;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.Tuple;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.FAIL_TID;
import static studio.phaseshift.metatron.Tokens.INT_TID;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;

public class TypeTest extends AbstractMetatronTest {
    private static final GraphittyLogger LOG = Graphitty.log(TypeTest.class);
    private static String LAST_TYPE_DEF = "";

    /**
     * Render the mtron execution stack on failures. BootLoader defaults every tracer stage to false
     * (its tracer/stack boot arg), so a type-check failure otherwise reports the message plus a Java
     * frame list — which names the leaf type and says nothing about which level of a predicate stack
     * rejected the value. Runs after AbstractMetatronTest.begin(), so the Router is live and
     * Tracer.enable's space write lands.
     */
    @BeforeAll
    public static void enableMtronStackTrace() {
        Tracer.mtron_stack.enable();
        Tracer.java_stack.disable();
    }


    // TODO: work in progress
    @Test
    public void testTypeCoefficientTypedCoefficient() {
        Type a = T(INT_TID.maybe()).maybeSome();
        Type b = T(Tuple.Pair.with(null, null), INT_TID.maybe(), Tokens.TYPE_TID.maybeSome());
        //KType c = TT(INT_TYPE.maybe().asType()).c(cInt.of(2, 77)).as();
        Object d = jnt(2).c(cInt.of(4, 6)).vid();
        LOG.warn("\n%s\n%s\n%s", a, b, d);
        assertNotEquals(a, b);
        LOG.warn("%s", ObjmtronSerializer.parse("int{?}::T{*}"));
    }

    //  @Disabled("everything works with the recent inst typing exception console::T (??)")
    @ParameterizedTest
    @CsvSource(value = {
            // obj                | type                             | matches?
            "1                    | testX?int<=int(3)                | true",
            "{2}1                 | testX?#{*}<=int{2}(3)            | true",
            "{2}1                 | testX?int<=int{2}(3)             | false",
            "{3}1                 | testX?int<=int{2}(3)             | false",
            "{3}1                 | testX?int{*}<=int{8}(3)          | false",
            "{3}1                 | testX?<=int{3}(3)                | false",
            "{0,5}1               | testX?int<=int{2}(3)             | false",
            "1                    | testX(a)                         | true",
            "1                    | testX?uri<=uri(a)                | false",
            "{2}1                 | testX?uri<=uri{2}(a)             | false",
            "{2}1                 | testX?#{1,35}<=#{2}(a)           | true",
    }, delimiter = '|')
    public void testInstType(final String obj, final String inst, final boolean matches) {
        try {
            Obj o = ObjmtronSerializer.parse(obj);
            Obj i = ObjmtronSerializer.parse(inst);
            LOG.debug("testing %s %s %s", o, matches ? "{{c}}in{{/c}}" : "{{c}}not in{{/c}}", i);
            assertEquals(matches, o.test(i));
        } catch (Exception e) {
            assertFalse(matches, "an exception occurred: " + e);
        }
    }

    @ParameterizedTest
    @CsvSource(value = {
            // obj                | type                            | matches?
            "1                    | /m/int                      | true",
            "1                    | int                         | true",
            "\"a_string\"         | /m/int                      | false",
            "\"a_string\"         | int                         | false",
            "\"a_string\"         | /m/str                      | true",
            "\"a_string\"         | str                         | true",
            "213.12               | /m/int                      | false",
            "213                  | int                         | true",
            "213.12               | int                         | false",
            "213.12               | real                        | true",
            "213.12               | /m/real                     | true",
            "1                    | #                           | true",
            "1                    | /+/+                        | true",
            //  "1                    | +                           | false",
            /// ///////////////////////////////////////////////////////////////
            "int::1             | A                            | true",
            "int::1             | B{+}                         | true",
            "int::1             | C{+}                         | true",
            "int::1             | D{0}                         | false",
            "int::1             | A{+}                         | true",
            "int::1             | B{+}                         | true",
            //"int::1             | a{+}                         | false",
            //"int::1             | b{+}                         | false",
            /// ///////////////////////////////////////////////////////////////
            "/m/int{0}::1     | +{*}                         | true",
            "/m/int{0}::1     | #{+}                         | false",
            "/m/int{0}::1     | +{?}                         | true",
            "/m/int{0}::1     | +{0}                         | true",
            "/m/int{0}::1     | +{,0}                        | true",
            "/m/int{0}::1     | +{+}                        | false",
            "/m/int{0}::1     | /+/+{?}                     | true",
            "/m/int{0}::1     | /+/+{0,1}                   | true",
            "/m/int{0}::1     | /+/+{0,99}                  | true",
            "/m/int{0}::1     | /+/+{*}                     | true",
            "1                | /+/#                        | true",
            "int:1            | /+/#                        | true",
            "</m/int>::1      | /m/int                      | true",
            "</m/int>::1      | /m/+                        | true",
            //   "</m/int>::1      | /m/+/+                      | false",
            "</m/int>::1      | /m/+/#                      | true",
            "/m/int::1        | /m/int                      | true",
            "/m/int::1        | /m/+                        | true",
            "/m/int{2}::1     | /m/+                        | false",
            "/m/int{2}::1     | /m/+{*}                     | true",
            "/m/int::1        | /m/+{?}                     | true",
            //   "/m/int::1        | /m/+/+                      | false",
            "/m/int::1        | /m/+/#                      | true",
            /// ///////////////////////////////////////////////////////////
            "int::1           | /m/int                      | true",
            "int::1           | /m/+                        | true",
            //   "int::1           | /m/+/+                      | false",
            "int::1           | /m/+/#                      | true",
            "int::1           | /m/int                      | true",
            "int::1           | /m/+                        | true",
            "int{2}::1        | /m/+                        | false",
            "int{2}::1        | /m/+{*}                     | true",
            "int::1           | /m/+{?}                     | true",
            //   "int::1           | /m/+/+                      | false",
            "int::1           | /m/+/#                      | true",
            /// ////////////////////////////////////////////////////////////
            "{c,d}                | /m/uri{2}                   | true",
            "{c,d}                | /m/+{2}                     | true",
            "str::\"abc\"         | /+/+/#                      | true",
            "/m/int::\"abc\"      | /+/+/+                      | false",
            "/m/int::1            | /+/+                        | true",
            //  "/m/str::'abc'        | /+/int                      | false",
            //  "str::'abc'           | /+/int                      | false",
            "1                    | /+/int                      | true",
            //  "1                    | /+/str                      | false",
            "1                    | /m/+                        | true",
            //  "1                    | /m/+/+                      | false",
            "1                    | /m/int{+}                   | true",
            "int{2}::1            | /m/int{1}                   | false",
            "{1,2,3,4}            | /m/int{4}                   | true",
            "{1,2,3,4}            | /m/int{3}                   | false",
            "{1,2,3,4}            | /m/int{0,3}                 | false",
            "{1,2,3,4}            | /m/int{3}                   | false",
            "{1,2,3,4}            | /m/int{0,5}                 | true",
            "{1,2,3,4}            | /m/int{*}                   | true",
            "{1,2,3,'abc'}        | /m/int{*}                   | false",
            "{1,2,3,'abc'}        | /m/+{*}                     | true",
            "{1,2,3,'abc'}        | /m/+{0,}                    | true",
            "{1,2,3,'abc'}        | /m/+{1,}                    | true",
            "{1,2,3,'abc'}        | /m/+{+}                     | true",
            "{1,2,3,'abc'}        | /m/+{2}                     | false",
            "{1,2,3,'abc'}        | /m/+{17,}                   | false",
            "{1,2,3,'abc'}        | /m/+{5,}                    | false",
            "{1,2,3,4}            | /m/str{*}                   | false",
            "{1,2,3,4}            | #{+}                            | true",
            "{1,2,3,4}            | int{+}                          | true",
            "{1,2,3,4}            | int{4}                          | true",
            "{1,2,3,4}            | int{3}                          | false",
            "{int{2}::1,int{2}::4}| int{3,5}                        | true",
            "{int{2}::1,int{2}::4}| int{4}                          | true",
            "{int{2}::1,int{2}::4}| int{3}                          | false",
            "{/m/int{2}::1,2}     | /m/int{3}                   | true",
            "{int{2}::1,2}        | /m/int{3}                   | true",
            "noobj                | #{0}                            | true",
            "noobj                | #{0,0}                          | true",
            "noobj                | #{?}                            | true",
            "noobj                | #{1}                            | false",
            "noobj                | +{0}                            | true",
            "noobj                | a/b/c{0}                        | true",
            "[a=>b]               | #                               | true",
            "plus::(2)            | /m/inst/plus                | true",
            "plus::(2)            | /m/+/plus                   | true",
            "plus{2}::(2)         | /m/inst/plus{2}             | true",
            "plus{5}::(2)         | /m/inst/plus{2,7}           | true",
            "plus{4}::()          | #{1,3}                          | false",
            "plus{4}::()          | /m/+/plus{4}                | true",
            "plus{4}::()          | /m/+/+{*}                   | true"
    }, delimiter = '|')
    public void testType(final String obj, final String typefURI, final boolean matches) {
        try {
            Obj o = ObjmtronSerializer.parse(obj);
            Type t = T(f(typefURI.trim()));
            LOG.debug("testing %s %s %s", o, matches ? "{{c}}in{{/c}}" : "{{c}}not in{{/c}}", t);
            // a literal that violates its own type now parses to a fail::T instead of throwing
            if (o.stream().anyMatch(Obj::isFail)) {
                assertFalse(matches, obj + " failed to construct, so it cannot be a " + typefURI + ": " + o);
                return;
            }
            // assertEquals(matches, o.type().tid().matches(f(typefURI)));
            assertEquals(matches, o.test(t));
            if (!o.isObjs()) // TODO: ensure proper objs deduction
                assertEquals(matches, o.testByID(t));
            //if (!typefURI.startsWith("#") && !o.isNoObj())
            //    this.testType(obj, fURI.of("#[" + o.tid().coefficientValue() + "]").toString(), !o.isNoObj());
            //final boolean a = t.matches(o);
            // assertEquals(matches, a);
        } catch (Exception e) {
            assertFalse(matches, "an exception occurred: " + e);
        }
    }


    @ParameterizedTest
    @TestData(value = {"abc -> noobj::T"})
    @CsvSource(value = {
            // obj               | type                                         | matches?
            "noobj               | noobj{0}::T                                | true",
            "noobj{0}            | noobj{0}::T                                   | true",
            "noobj               | abc{*}::T                                  | true",
            "noobj               | abc{?}::T                                  | true",
            "noobj               | int{?}::T                                  | true",
            "noobj               | A{?}::T                                    | true",
            "{0}noobj            | abc{+}::T                                  | false",
            "1                   | noobj::T                                   | false",
            "1                   | str::T                                     | false",
            "1                   | lst::T                                     | false",
            "1                   | int::T                                     | true",
            "{0}1                | int::T                                     | false",
            "{0}1                | int{?}::T                                  | true",
            "{0}1                | int{*}::T                                  | true",
            "{0}1                | int{+}::T                                  | false",
            "{1}1                | int{0}::T                                  | false",
            "'a_string'          | int::T                                     | false",
            "213.0               | int::T                                     | false",
            "1                   | int::T[is(eq(1))]                          | true",
            "1                   | int::T[is(eq(2))]                          | false",
            "1                   | int::T[?=1]                                | true",
            "1                   | int::T[?=2]                                | false",
            "{1,1}               | int::T                                     | false",
            "{,}                 | int{0}::T                                  | true",
            "{1,1}               | int{2}::T[is(eq({2,2}))]                   | false",
            //   "{1,1}               | int{2}::T[is?int{2}<=int{2}(eq({1,1}))]                     | true",
            "{'a','b'}           | str{2}::T                                  | true",
            "{'a','b'}           | str{2,3}::T                                | true",
            "{'a','b','c'}       | str{2,3}::T                                | true",
            "{'a','b','c','d'}   | str{2,3}::T                                | false",
            "{}                  | str{2,3}::T                                | false",
            "{}                  | str{0,3}::T                                | true",
            "{'b'}               | str{2}::T                                  | false",
            "{'b'}               | str{*}::T                                  | true",
            "{1,2}               | int{2}::T                                  | true",
            "{1,2,3}               | int{1,3}::T                              | true",
            "{1,2,3}               | int{1,2}::T                              | false",
            //  "{1,1}               | int{2}::T[is(eq({1,1}))]                   | true",
            "{1,1}               | int{2}::T                                  | true",
            "{1,1}               | int::T[is(gt(0))]                          | false",
            "{1,1}               | int{2}::T[is(gt(0))]                       | true",
            "1                   | int{2}::T[is(gt(0))]                       | false",
            "{0,0}               | int{2}::T[is(gt(0))]                       | false",
            "{2,3}               | int{2}::T[>-.is(gt(1)).else(fail::T)]      | true",
            //     "{2,3}               | int{2}::T[>-.is(gt(2)).else(fail::T)]      | false",
            "{2,3}               | int{2}::T[>-.is(gt(4)).else(fail::T)]      | false",
            "{5,6}               | int{2}::T[>-.is(gt(4)).else(fail::T)]      | true",
            "{2,2}               | int{2}::T[is(gt(1))]                       | true",
            "{3,3}               | int{2}::T[is(gt(1))]                       | true",
            "{0,1}               | int{2}::T[is(gt(0))]                         | false",
            "{0,0}               | int{2}::T[is(gt(1))]                       | false",
            "{0,-1}               | int{2}::T[is(gt(1))]                        | false",
            //  "1               | int^:is(gt(0))                               | false"},
    },
            delimiter = '|')
    public void testTypeObj(final String obj, final String type, final boolean matches) {
        Obj o = ObjmtronSerializer.parse(obj);
        Type t = ObjmtronSerializer.parse(type);
        LOG.debug("testing %s {{g}}({{b}}%s{{g}}){{X}} %s %s", o, o.tid(), matches ? "{{g}}is a{{/g}}" : "{{r}}is not a{{/r}}", t);
        assertEquals(matches, o.test(t));
        if (!t.hasPredicate())
            assertEquals(matches, o.testByID(t));
    }

    @ParameterizedTest
    @TestData(value = {"nat -> int::T[is(gt(0))]", "bignat -> nat::T[is(gt(100))]",})
    @CsvSource(value = {
            // obj               | type                                       | matches?
            "A::T                |   A::T                                       | true",
            "A{1}::T             |   A{?}::T                                    | true",
            "A{2}::T             |   A{?}::T                                    | false",
            "A{0}::T             |   A::T                                       | false",
            "A{0}::T             |   A{0}::T                                    | true",
            "A::T                |   B::T                                       | false",
            "A::T                |   B{?}::T                                    | false",
            "A{0}::T             |   B{0}::T                                    | true",
            "A{0}::T             |   int{0}::T                                  | true",
            "int{0}::T           |   A{0}::T                                    | true",
            //"int::T             | T::T                                       | true",
            //"T::T                | int::T                                     | false",
            //  "int::T              | T::T[int::T]                               | true",
            //   "int::T[?>2]         | T::T[int::T]                               | true",
            //   "int::T[?>2]         | T::T[int::T[?>2]]                          | true",
            //  "int::T              | T::T[int::T[?>2]]                          | false",
            //  "int::T              | T::T[#::T]                                 | true",
            //"int::T              | T::T[?<real::T>]                              | false",
            "int::T              | str::T                                     | false",
            "int::T              | #::T                                       | true",
            "int::T              | #{?}::T                                    | true",
            "int::T              | #{+}::T                                    | true",
            "int::T              | #{2}::T                                    | false",
            "int{0}::T           | str{0}::T                                  | true",
            "int::T              | int::T                                     | true",
            "int::T              | int::T[?>0]                                | false",
            "int::T[?>0]         | int::T                                     | true",
            "int::T[?>0]         | int::T[?>0]                                | true",
            "int{2}::T           | #{*}::T                                    | true",
            "int::T              | int{0}::T                                  | false",
            "int::T              | int{2,3}::T                                | false",
            "int::T              | int{1}::T                                  | true",
            "nat::T              | nat::T                                     | true",
            "nat::T              | int::T                                     | true",
            "int::T              | nat::T                                     | false",
            "nat::T              | str::T                                     | false",
            "nat::T              | bignat::T                                  | false",
            //"bignat::T           | nat::T                                     | true",
            "bignat::T           | int::T                                     | true",
            "int::T              | bignat::T                                  | false",
            "int::T              | 0                                          | false",
            "0                   | int::T                                     | true",
            "0                   | nat::T                                     | false",
            // "int::T              | nat::T + int::T                            | true",
            //"0                   | nat::T[mult(int::T)]                                        | false",
            "0                   | int::T[is(or(matches(int::T),matches(real::T)))]            | true",
            "0                   | int::T[is(or(matches(str::T),matches(real::T)))]            | false",
            "0                   | int::T[is(or(matches(str::T),or(matches(uri::T),matches(real::T))))]    | false",
            "0                   | int::T[is(or(matches(str::T),or(matches(int::T),matches(real::T))))]    | true",
            "0                   | int::T[is(or(matches(int::T),matches(real::T)))]             | true",
            "0                   | int::T[is(and(matches(int::T),matches(real::T)))]            | false",
            "0                   | int::T[is(and(matches(str::T),matches(nat::T)))]             | false",
            "nat::T              | 0                                                            | false",
            "nat::T              | 1                                                            | false",
            //  "nat::T              | T::T[nat::T]                               | true",
            //     "nat::T              | T::T[int::T]                               | true",
            //   "int::T              | T::T[nat::T]                               | false",
            //   "nat::T              | T::T[str::T]                               | false",
            //   "T::T[nat::T]        | nat::T                                     | false"
    },
            delimiter = '|')

    public void testTypeInheritance(final String typeA, final String typeB, final boolean matches) throws Exception {
        final Obj a = ObjmtronSerializer.parse(typeA);
        final Obj b = ObjmtronSerializer.parse(typeB);
        final fURI aTID = a.tid();
        final fURI bTID = b.tid();
        LOG.debug("testing %s %s %s", a, matches ? "{{g}}is a{{/g}}" : "{{r}}is not a{{/r}}", b);
        assertEquals(matches, a.test(b));
        if (b.isType() && !b.asType().hasPredicate())
            assertEquals(matches, a.testByID(b));
        assertEquals(aTID, a.tid());
        assertEquals(bTID, b.tid());
    }

    @ParameterizedTest
    @TestData(value = {"nat -> int::T[is(gt(0))]@nat", "bignat -> nat::T[is(gt(100))]@bignat",})
    @CsvSource(value = {
            "/m/int::T | /m/int::T | true",
            "/m/int::T | nat::T    | false",
            "nat::T    | /m/int::T | true",
            "nat::T    | int::T | true",
            "nat::T    | nat::T    | true",
            //"bignat::T | nat::T    | true",
            "bignat::T | int::T | true",
            "/m/int::T | bignat::T | false",
            "nat::T    | bignat::T | false",
            "bignat::T | bignat::T | true",
    }, delimiter = '|')
    public void testBaseTypes(final String type, final String baseType, final boolean matches) throws Exception {
        Obj a = ObjmtronSerializer.parse(type);
        Obj b = ObjmtronSerializer.parse(baseType);
        LOG.debug("testing %s %s %s", a, matches ? "{{g}}is a{{/g}}" : "{{r}}is not a{{/r}}", b);
        assertEquals(matches, a.test(b));
    }

    @ParameterizedTest
    @TestData({"nat -> int::T[is(gt(0))]", "bignat -> nat::T[is(gt(100))]"})
    @CsvSource(value = {
            "[int::T, nat::T]",
            "[int::T,nat::T,bignat::T]",
    }, delimiter = '%')
    public void testTypeType(final String typeList) {
        final Lst typesObj = ObjmtronSerializer.parse(typeList);
        for (int i = 0; i < typesObj.count() - 1; i++) {
            final Type typeObj = typesObj.at(i).asType();
            final Type parentObj = typesObj.at(i + 1).asType();
            final Type inferredType = typeObj.type();
            LOG.debug("%s %s %s", typeObj, parentObj, inferredType);
            assertTrue(typeObj.test(inferredType), String.format("%s does not match %s", typeObj, inferredType));
            // assertTrue(typeObj.test(parentObj));
            //assertEquals(parentObj, inferredType);
        }
    }

    @ParameterizedTest
    @CsvSource(value = {
            // tid   |  typedef                                 | instance                                         | matches?
            "person  % rec::T[?[name=>?str::T,age=>?int::T]]    % person::[name=>'enoch',age=>365]                 % true",
            "person  % .                                        % person::7                                        % false",
            "person  % .                                        % person::'a person'                               % false",
            "person  % .                                        % person::[name=>'enoch']                          % false",
            "person  % .                                        % person::[age=>333]                               % false",
            "person  % .                                        % person::[=>]                                     % false",
            "person  % .                                        % person::[name=>'a',age=>1,b=>2]                  % true",
            "person  % .                                        % person::[name=>'a',age=>1,b=>noobj]              % true",
            "person  % .                                        % person::[name=>'a',age=>1.2,b=>noobj]            % false",
            "person  % .                                        % person::[name=>'a',age=>1,b=>2].as(person::T[?[name => >-.count().is(eq(1))]]) % true",
            "person  % .                                        % person::[name=>'a',age=>1,b=>2].as(person::T[>-.count().is(eq(2))]) % false",
            "person  % .                                        % person::[name=>'a',age=>1,b=>2].as(person::T[>-.count().is(eq(3))]) % true",
            /// ////////////////////////////////////////////////////////////////////////////////////////////////////////////
            "person  % .                                        % 7.as(person::T)                                    % false",
            "person  % .                                        % \"a person\".as(person::T)                           % false",
            "person  % .                                        % [name=>'enoch'].as(person::T)                      % false",
            "person  % .                                        % [age=>333].as(person::T)                           % false",
            "person  % .                                        % [=>].as(person::T)                                 % false",
            "person  % .                                        % [=>].as(person::T[[=>]])                           % false",
            "person  % .                                        % [name=>'a',age=>1,b=>2].as(person::T)              % true",
            "person  % .                                        % [name=>a,age=>-2,b=>noobj].as(person::T[?[age=>str::T]])    % true",
            "person  % .                                        % [name=>a,age=>-2,b=>noobj].as(person::T[?[age=>uri::T]])    % false",
            "person  % .                                        % [name=>'a',age=>-2,b=>noobj].as(person::T[?[age=>-2]])      % true",
            "person  % .                                        % [name=>'a',age=>-2,b=>noobj].as(person::T[?[age => is(lt(0))]])     % true",
            "person  % .                                        % [name=>'a',age=>-2,b=>noobj].as(person::T[?[age => is(gt(0))]])     % false",
            "person  % .                                        % [name=>'a',age=>1].as(person::T)                   % true",
            "person  % .                                        % [name=>'a',age=>1].as(person::T[[name=>uri::T]])   % false",
            "person  % .                                        % [name=>'a',age=>1].as(person::T[>-.count().is(eq(0))))   % false",
            "person  % .                                        % [name=>'a',age=>1,b=>noobj].as(person::T)          % true",
            "person  % .                                        % [name=>'a',age=>1,b=>noobj].as(person::T[?[b=>is(gt(0))]])  % false",
            "person  % .                                        % [name=>'a',age=>1,b=>noobj].as(person::T[?[b=>2]])  % false",
            "person  % .                                        % [name=>'a',age=>1,b=>noobj].as(person::T[?[b=>noobj]])  % true",
            "person  % .                                        % [name=>'a',age=>1.2,b=>noobj].as(person::T)        % false",
            "person  % .                                        % [name=>'a',age=>1,b=>noobj].as(person::T[[b=>2]])  % false",
            "person  % .                                        % [name=>'a',age=>1.2,b=>noobj].as(person::T)        % false",
            /// ////////////////////////////////////////////////////////////////////////////////////////////////////////////
            "person  % .                                        % [name=>'base',age=>1]                            % true",
            "person  % .                                        % [name=>'base']                                   % false",
            "person  % .                                        % [name=>'base',age=>'the number one']             % false",
            "person  % .                                        % [name=>'base',age=>1,another=>[a=>b]]            % true",
            /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////
            "person  % rec::T[?[name=>?str::T,age=>?int::T]]    % person::[name=>'enoch',age=>365]                 % true",
            "person  % .                                        % person::7                                        % false",
            "person  % .                                        % person::'a person'                               % false",
            "person  % .                                        % person::[name=>'enoch']                          % false",
            "person  % .                                        % person::[age=>333]                               % false",
            "person  % .                                        % person::[=>]                                     % false",
            "person  % .                                        % person::[name=>'a',age=>1,b=>2]                  % true",
            "person  % .                                        % person::[name=>'a',age=>1,b=>noobj]              % true",
            "person  % .                                        % person::[name=>'a',age=>1.2,b=>noobj]            % false",
            "person  % .                                        % [name=>'base',age=>1]                            % true",
            "person  % .                                        % [name=>'base']                                   % false",
            "person  % .                                        % [name=>'base',age=>'the number one']             % false",
            "person  % .                                        % [name=>'base',age=>1,another=>[a=>b]]            % true",
            /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////
            "person  % rec::T[?[uri{?}::name=>str::T,age=>int::T]]  % person::[name=>'enoch',age=>365]                 % true",
            "person  % .                                            % person::7                                        % false",
            "person  % .                                            % person::'a person'                               % false",
            "person  % .                                            % person::[name=>'enoch']                          % false",
            "person  % .                                            % person::[age=>333]                               % true",
            "person  % .                                            % person::[name=>12]                               % false",
            "person  % .                                            % person::[name=>12,age=>333]                      % false",
            "person  % .                                            % person::[=>]                                     % false",
            "person  % .                                            % person::[name=>'a',age=>1,b=>2]                  % true",
            "person  % .                                            % person::[name=>'a',age=>1,b=>noobj]              % true",
            "person  % .                                            % person::[name=>'a',age=>1.2,b=>noobj]            % false",
            "person  % .                                            % [name=>'base',age=>1]                            % true",
            "person  % .                                            % [name=>'base']                                   % false",
            "person  % .                                            % [name=>'base',age=>'the number one']             % false",
            "person  % .                                            % [name=>12,age=>'the number one']                 % false",
            "person  % .                                            % [name=>'base',age=>1,another=>[a=>b]]            % true",
            /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////
            "person  % rec::T[?[name=>str::T,age=>int::T]]      % person::[name=>'enoch',age=>365]                 % true",
            "person  % .                                        % person::7                                        % false",
            "person  % .                                        % person::'a person'                               % false",
            "person  % .                                        % person::[name=>'enoch']                          % false",
            "person  % .                                        % person::[age=>333]                               % false",
            "person  % .                                        % person::[=>]                                     % false",
            "person  % .                                        % person::[name=>'a',age=>1,b=>2]                  % true",
            "person  % .                                        % person::[name=>'a',age=>1,b=>noobj]              % true",
            "person  % .                                        % person::[name=>'a',age=>1.2,b=>noobj]            % false",
            "person  % .                                        % [name=>'base',age=>1]                            % true",
            "person  % .                                        % [name=>'base']                                   % false",
            "person  % .                                        % [name=>'base',age=>'the number one']             % false",
            "person  % .                                        % [name=>'base',age=>1,another=>[a=>b]]            % true",
            ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            "nat     % int::T[is(gt(0))]                        % nat::23                                          % true",
            "nat     % .                                        % int::2.as(nat::T[is(gt(1))])                     % true",
            "nat     % .                                        % int::0.as(nat::T[is(eq(0))])                     % false",
            "nat     % .                                        % int::0.as(int::T[is(eq(0))])                     % true",
            "nat     % .                                        % int::1.as(nat::T[is(gt(-1))])                    % true",
            "nat     % .                                        % int::2.as(nat::T[is(eq(2))])                     % true",
            "nat     % .                                        % -2.as(nat::T[is(eq(-2))])                        % false",
            "nat     % .                                        % 2.as(nat::T[is(eq(4))])                          % false",
            "nat     % .                                        % 2.as(nat::T[is(geq(4))])                         % false",
            "nat     % .                                        % 2.as(nat::T[is(eq(2))])                          % true",
            "nat     % .                                        % int::0.as(nat::T)                                % false",
            "nat     % .                                        % nat::-23                                         % false",
            "nat     % .                                        % nat::'a big number'                              % false",
            "nat     % .                                        % nat::2 + 6                                       % true",
            "nat     % .                                        % nat::2 + -6                                      % false",
            "nat     % .                                        % 23.as(nat::T)                                    % true",
            "nat     % .                                        % -23.as(nat::T)                                   % false",
            "nat     % .                                        % 2.as(plus(6).as(nat::T))                         % true",
            "nat     % .                                        % 2.as(plus(-6).as(nat::T))                        % false",
            ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            "nat     % int::T[?>0]                              % nat::23                                          % true",
            "nat     % .                                        % int::2.as(nat::T[is(gt(1))])                     % true",
            "nat     % .                                        % int::0.as(nat::T[is(eq(0))])                     % false",
            "nat     % .                                        % int::0.as(int::T[is(eq(0))])                     % true",
            "nat     % .                                        % int::1.as(nat::T[is(gt(-1))])                    % true",
            "nat     % .                                        % int::2.as(nat::T[is(eq(2))])                     % true",
            "nat     % .                                        % -2.as(nat::T[is(eq(-2))])                        % false",
            "nat     % .                                        % 2.as(nat::T[is(eq(4))])                          % false",
            "nat     % .                                        % 2.as(nat::T[is(geq(4))])                         % false",
            "nat     % .                                        % 2.as(nat::T[is(eq(2))])                          % true",
            "nat     % .                                        % int::0.as(nat::T)                                % false",
            "nat     % .                                        % nat::-23                                         % false",
            "nat     % .                                        % nat::'a big number'                              % false",
            "nat     % .                                        % nat::2 + 6                                       % true",
            "nat     % .                                        % nat::2 + -6                                      % false",
            "nat     % .                                        % 23.as(nat::T)                                    % true",
            "nat     % .                                        % -23.as(nat::T)                                   % false",
            "nat     % .                                        % 2.as(plus(6).as(nat::T))                         % true",
            "nat     % .                                        % 2.as(plus(-6).as(nat::T))                        % false",
            ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            "nat     % int::T[?>0][-<|[is(lt(0)) => _ * -1,_ => _]>>]   % nat::23                                          % true",
            "nat     % .                                        % nat::-23                                         % true",
            "nat     % .                                        % nat::'a big number'                              % false",
            "nat     % .                                        % nat::2 + 6                                       % true",
            "nat     % .                                        % nat::2 + -6                                      % false",
            "nat     % .                                        % 23.as(nat::T)                                    % true",
            "nat     % .                                        % -23.as(nat::T)                                   % true",
            "nat     % .                                        % 2.as(plus(6).as(nat::T))                         % true",
            "nat     % .                                        % 2.as(plus(-6).as(nat::T))                        % true",
            ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            "nat     % int::T[?>0]@nat                          % nat::150                                         % true",
            ".       % .                                        % nat::-150                                        % false",
            ".       % .                                        % nat::0                                           % false",
            "agenat  % nat::T[?<125]@agenat                     % agenat::150                                      % false",
            "agenat  % .                                        % int::2.as(agenat::T)                             % true",
            "agenat  % .                                        % int::2.as(agenat::T).as(int::T).as(agenat::T)    % true",
            "agenat  % .                                        % int::2.as(agenat::T).as(int::T).as(agenat::T).as(int::T)  % true",
            "agenat  % .                                        % int::2.as(agenat::T).as(int::T).as(agenat::T).mult(-10)  % false",
            "agenat  % .                                        % int::2.as(nat::T).as(agenat::T)                  % true",
            ".       % .                                        % agenat::-1                                       % false",
            ".       % .                                        % agenat::200                                      % false",
            ".       % .                                        % nat::200.as(agenat::T)                           % false",
            ".       % .                                        % agenat::29                                       % true",
    }, delimiter = '%')
    public void testTyping(final String tid, final String typeDef, final String instance, final boolean shouldSucceed) {
        try {
            Router.writeToSpace(tid, noobj());
            Obj type = ObjmtronSerializer.parse(typeDef.trim().equals(".") ? LAST_TYPE_DEF : typeDef.trim());
            LAST_TYPE_DEF = typeDef.trim().equals(".") ? LAST_TYPE_DEF : typeDef.trim();
            Router.writeToSpace(tid, type);
            // assertEquals(type, Router.readFromSpace(tid));
            LOG.debug("testing %s %s %s", instance, shouldSucceed ? "{{g}}is a{{/g}}" : "{{r}}is not a{{/r}}", type);
            try {
                Obj inst = ObjmtronSerializer.parse(instance.trim()).apply();
                //LOG.debug("instance: %s", inst);
                if (!shouldSucceed) {
                    LOG.debug("instance: %s %s %s", inst.type(), inst.isFail(), inst.tid().equals(FAIL_TID));
                    if (inst.tid().equals(FAIL_TID))
                        assertFalse(shouldSucceed);
                    else if (!inst.tid().equals(f(tid)))
                        assertEquals(shouldSucceed, inst.test(type)); // type checking for base types that are not :: specified
                    else
                        assertEquals(noobj(), inst);
                }
            } catch (final Exception e) {
                assertFalse(shouldSucceed);
            }
            assertTrue(type.isType());
        } finally {
            Router.writeToSpace(tid, noobj());
        }
    }

    @ParameterizedTest
    @CsvSource(value = {
            "int{2}::T                  | /m/int{2}",
            "3                         | /m/int",
            "int{2,3}::T               | /m/int{2,3}",
            "3{?}                      | /m/int",
            "3{*}                      | /m/int",
            "int{2}::3                 | /m/int{2}",
            "int{2,5}::3               | /m/int{2,5}",
            "int{0}::3                 | /m/int{0}",
            "int{5,5}::3               | /m/int{5}",
            "int{0}::T                 | /m/int{0}"
    }, delimiter = '|')
    public void testTypeTID(final String type, final String expectedTID) {
        LOG.debug("testing type %s == tid %s", type, expectedTID);
        assertEquals(f(expectedTID), mParser.m_obj().parse(type).<Obj>get().tid());
    }

    @ParameterizedTest
    @TestData(value = {
            "person -> noobj",
            "chicken -> noobj",
            "being -> noobj",
            "rec::T[?[name=>str::T,age=>int::T]]@being",
            "being::T@person",
            "being::T@chicken",
            "person::[name=>'marko',age=>29]@marko",
            "chicken::[name=>'snowbutt',age=>7]@snowbutt"})
    @CsvSource(value = {
            "[name=>'bill',age=>10].as(person::T)                 | person::[name=>'bill',age=>10]",
            "[name=>'bill',age=>10].as(chicken::T)                | chicken::[name=>'bill',age=>10]",
            "[name=>'bill',age=>10].as(chicken::T).as(being::T)   | being::[name=>'bill',age=>10]",
            "[name=>'bill',age=>10].as(person::T).as(being::T)    | being::[name=>'bill',age=>10]",
            "[name=>'bill',age=>10].as(person::T).as(rec::T)      | [name=>'bill',age=>10]",
            "being::[name=>'bill',age=>10].as(person::T)          | <ERROR>",
            "being::[name=>'bill',age=>10].as(chicken::T)         | <ERROR>",
            "person::[name=>'bob',age=>55].as(chicken::T)         | <ERROR>",
            "being::[name=>'bob',age=>55].as(chicken::T)          | <ERROR>",
            "*marko.?rec::T                                       | person::[name=>'marko',age=>29]",
            "*marko.?being::T                                     | person::[name=>'marko',age=>29]",
            "*marko.?person::T                                    | person::[name=>'marko',age=>29]",
            "*marko.?chicken::T                                   | noobj",
            "*snowbutt.?rec::T                                    | chicken::[name=>'snowbutt',age=>7]",
            "*snowbutt.?being::T                                  | chicken::[name=>'snowbutt',age=>7]",
            "*snowbutt.?chicken::T                                | chicken::[name=>'snowbutt',age=>7]",
            "*snowbutt.?person::T                                 | noobj",
            "*snowbutt.as(person::T)                              | <ERROR>",
            "*snowbutt.as(being::T)                               | being::[name=>'snowbutt',age=>7]",
            "*snowbutt.as(rec::T)                                 | [name=>'snowbutt',age=>7]",
            "*marko.as(chicken::T)                                | <ERROR>",
            "*marko.as(being::T)                                  | being::[name=>'marko',age=>29]",
            "*marko.as(something::T)                              | <ERROR>", // non-existent types are nominal
            "*marko.as(rec::T)                                    | [name=>'marko',age=>29]",
            "*marko.as(rec::T).as(something::T)                   | something::[name=>'marko',age=>29]",
            "*marko.as(rec::T).as(A::T)                           | <ERROR>", // values shouldn't type generic?
            "*marko.as(A::T)                                      | <ERROR>", // values shouldn't type generic?
            "true.as(A::T)                                        | <ERROR>", // values shouldn't type generic?
            "*marko.as(rec::T).as(something::T).as(chicken::T)    | <ERROR>",
    }, delimiter = '|')
    public void testNominalTyping(final String code, final String expected) {
        assertTrue(T(f("person")).isNominal());
        assertTrue(T(f("chicken")).isNominal());
        assertFalse(T(f("being")).isNominal());
        assertTrue(T(f("something")).isNominal());
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
        assertTrue(T(f("person")).isNominal());
        assertTrue(T(f("chicken")).isNominal());
        assertFalse(T(f("being")).isNominal());
        assertTrue(T(f("something")).isNominal());
    }


    @ParameterizedTest
    @TestData(value = {
            "a -> int::T[?>0]",
            "b -> int::T[?>1]",
            "c -> int::T[?=2]",
            "d -> int::T[?>2]",
            "e -> int::T[?>5]",
            "f -> e::T[?>3]",
            "g -> f::T[?>4]",
            "h -> g::T[?>6]"})
    @CsvSource(value = {
            "2                  | a::T                  | true | [a,int] ",
            "3                  | b::T                  | true | [b,int] ",
            "4                  | c::T                  | false | [c,int] ",
            "5                  | d::T                  | true | [d,int] ",
            "5                  | e::T                  | false | [e,int] ",
            //   "5                  | f::T                  | false | [f,e,int] ",
            //   "5                  | g::T                  | false | [g,f,e,int] ",
            "10                 | g::T                  | true | [g,f,e,int] ",
            "6                  | g::T                  | true | [g,f,e,int] ",
            "4                  | g::T                  | false | [g,f,e,int] ",
            "1                  | h::T                  | false | [h,g,f,e,int] ",
            "2                  | h::T                  | false | [h,g,f,e,int] ",
            "3                  | h::T                  | false | [h,g,f,e,int] ",
            "4                  | h::T                  | false | [h,g,f,e,int] ",
            "5                  | h::T                  | false | [h,g,f,e,int] ",
            "6                  | h::T                  | false | [h,g,f,e,int] ",
            "7                  | h::T                  | true | [h,g,f,e,int] "
    }, delimiter = '|')
    public void testTypeRecursion(final String instance, final String type, final boolean matches, final String stack) {
        //LOG.debug("testing %s %s", mParser.eval("*h"), mParser.eval("*h").asType().parentType());
        final Obj instanceObj = ObjmtronSerializer.parse(instance);
        final List<Type> expectedTypeStack = ObjmtronSerializer.parse(stack).lstValue().stream().map(o -> ObjmtronSerializer.<Type>parse(o.toString() + "::T")).toList();
        final List<Type> deducedTypeStack = deducedTypeStack(ObjmtronSerializer.parse(type));
        final List<Boolean> matchesTypeStack = deducedTypeStack.stream().map(instanceObj::test).toList();
        LOG.debug("testing type stack of %s:\n\t%s\n\t%s\n\t%s", instanceObj, expectedTypeStack, deducedTypeStack, matchesTypeStack);
        assertEquals(matches, matchesTypeStack.stream().reduce(true, (a, b) -> a && b));
        //assertEquals(expectedTypeStack, deducedTypeStack.subList(1, deducedTypeStack.size()));
        checkMatches(LOG, instance, type, matches);
    }

    @ParameterizedTest
    @TestData(value = {
            "entity -> rec::T@entity",
            "thing -> entity::T@thing",
            "thing::T[?[name=>?str::T,age=>?int::T]]@person"
    })
    @CsvSource(value = {
            "1                         %  1                              % true",
            "int::T                    %  1                              % false",
            "1                         %  2                              % false",
            "1                         % '1'                             % false",
            "1                         % int::T                          % true",
            "1                         % entity::T                       % false",
            "entity::T                 % entity::T                       % true",
            "thing::T                  % entity::T                       % true",
            "thing::T                  % thing::T                        % true",
            "person::T                 % person::T                       % true",
            "entity::T                 % thing::T                        % false",
            "[a=>1]                    % entity::T                       % true",
            "entity::[a=>1]            % entity::T                       % true",
            "[a=>1]                    % person::T                       % false",
            "entity::[a=>1]            % person::T                       % false",
            "thing::[a=>1]             % entity::T                       % true",

    }, delimiter = '%', quoteCharacter = '~')
    public void testNominalStructuralTypeSystem(final String objA, final String objB, final boolean matches) {
        final Obj objAA = ObjmtronSerializer.parse(objA);
        final Obj objBB = ObjmtronSerializer.parse(objB);
        LOG.debug("%s is a %s@%s", objA, objAA.tid(), objAA.vid());
        LOG.debug("%s is a %s@%s", objB, objBB.tid(), objBB.vid());
        if (matches) {
            assertTrue(objAA.test(objBB), objAA + " should match " + objBB);
        } else {
            assertFalse(objAA.test(objBB), objAA + " shouldn't match " + objBB);
        }
    }

    @ParameterizedTest
    @TestData(value = {
            "being    -> rec::T[?[age=>int::T]]",
            "person   -> being::T[?[name=>str::T]]",
            "mortal   -> person::T[?[age=>?<120]]",
            "immortal -> being::T[?[alias=>str{2,3}::T]]",
            "team     -> rec::T[?[flag=>?str::T.-<('')>-.count().?=2, member=>being{+}::T]]"})
    @CsvSource(value = {
            "[age=>2]                                                            % rec::T                % true",
            "[age=>2]                                                            % lst::T                % false",
            "[age=>2]                                                            % being::T              % true",
            "[age=>'2']                                                          % being::T              % false",
            "{mortal::[age=>2],mortal::[age=>3]}                                 % rec{2}::T             % true",
            "{mortal::[age=>2],mortal::[age=>3]}                                 % being{2}::T           % true",
            "mortal::[age=>2]                                                    % being::T              % true",
            "being::[age=>2]                                                     % being::T              % true",
            "[name=>'marko',age=>29]                                             % person::T             % true",
            "[name=>'marko',age=>121]                                            % mortal::T             % false",
            "[name=>'marko',age=>120]                                            % mortal::T             % false",
            "[name=>'marko',age=>119]                                            % mortal::T             % true",
            "[name=>'marko',age=>120]                                            % immortal::T           % false",
            "[name=>'marko',age=>120,alias=>'m']                                 % immortal::T           % false",
            "[name=>'marko',age=>120,alias=>{'m','mar'}]                         % immortal::T           % true",
            "[name=>'marko',age=>120,alias=>{'m','mar','mr'}]                    % immortal::T           % true",
            "[name=>'marko',age=>120,alias=>{'m','mar','mr','mmm'}]              % immortal::T           % false",
            "[name=>'marko',age=>29,alias=>{'m','mar','mr','mmm'}]               % person::T             % true",
            "[name=>'marko',age=>29,alias=>{'m','mar','mr','mmm'}]               % rec::T                % true",
            "[flag=>'us',member=>{}]                                             % team::T               % false",
            "[flag=>'us',member=>being::[age=>29]]                               % team::T               % true",
            "[flag=>'us',member=>mortal::[age=>29]]                              % team::T               % true",
            "[flag=>'us',member=>[age=>29]]                                      % team::T               % true",
            "[flag=>'us',member=>{being::[age=>29],being::[age=>34]}]            % rec::T                % true",
            "[flag=>'us',member=>{being::[age=>29],being::[age=>34]}]            % team::T               % true",
            "[flag=>'us',member=>{being::[age=>29],mortal::[age=>134]}]          % team::T               % false",
            "[flag=>'us',member=>{being::[age=>29],person::[name=>'a',age=>35]}] % team::T               % true",
            "[flag=>'us',member=>{being::[age=>29],[blah=>'stuff']}]             % team::T               % false",
            "[flag=>'us',member=>{[age=>29],[age=>34]}]                          % team::T               % true",
            "[flag=>'us',member=>{[age=>29],[age=>34],[age=>35]}]                % team::T               % true",
            "[flag=>'usa',member=>{[age=>29],[age=>34],[age=>35]}]               % team::T               % false",
            "[flag=>'mex',member=>{[age=>12]}]                                   % team::T               % false",
            "[flag=>'mex',member=>{[age=>12],[age=>13]}]                         % team::T               % false",
            "[flag=>'mx',member=>{[age=>12],[age=>13]}]                          % team::T               % true",
    }, delimiter = '%')
    public void testComplexTypes(final String instance, final String type, final boolean matches) {
        LOG.debug("testing %s %s %s", instance, matches ? "{{g}}matches{{/g}}" : "{{r}}doesn't match{{/r}}", type);
        try {
            final Obj instanceObj = ObjmtronSerializer.parse(instance);
            final Obj typeObj = ObjmtronSerializer.parse(type);
            if (matches) {
                try {
                    assertTrue(instanceObj.test(typeObj), "%s is not a %s".formatted(instanceObj, typeObj));
                    instanceObj.as(typeObj.asType());
                } catch (Exception e) {
                    fail(e);
                }
            } else {
                assertFalse(instanceObj.test(typeObj));
                try {
                    instanceObj.as(typeObj.asType());
                    fail();
                } catch (Exception e) {
                    assertTrue(true);
                }
            }
        } catch (Exception e) {
            //LOG.error(e); // match through exception (not the best way to do things, but for now...)
            assertFalse(matches);
        }
    }

    private static List<Type> deducedTypeStack(final Obj type) {
        final List<Type> stack = new ArrayList<>();
        Obj temp = type;
        while (!temp.type().isRootType() && temp.isType() && !temp.isNoObj()) {
            stack.add(temp.asType());
            temp = temp.asType().parentType();
        }
        return stack;
    }

    @ParameterizedTest(name = "[{index}] {3}")
    @TestData(value = {
            "pos       -> int::T[is(gt(0))]@pos",
            "small     -> int::T[is(lt(120))]@small",
            "mid       -> int::T[is(gt(50))]@mid",
            "human     -> rec::T[?[age=>int::T,name=>str::T]]@human",
            "artifact  -> rec::T[?[age=>int::T]]@artifact",
            "company   -> rec::T[?[name=>str::T,employees=>int::T]]@company",
            "addrCity  -> rec::T[?[address=>rec::T[?[city=>str::T]]]]@addrCity",
            "addrZip   -> rec::T[?[address=>rec::T[?[zip=>int::T]]]]@addrZip",
            "pairInt   -> int{2}::T@pairInt",
            "tripleInt -> int{3}::T@tripleInt",
            "many      -> int{*}::T@many",
            "mortal    -> human::T[is(lt(120))]@mortal",
            "ageInt    -> rec::T[?[age=>int::T]]@ageInt",
            "ageStr    -> rec::T[?[age=>str::T]]@ageStr",
    })
    @CsvSource(value = {
            // types                          | lcdVID | expectedBase | description
            "[pos::T, small::T]               | lcd1   | /m/int        | non-isa OR via split/merge",
            "[human::T, artifact::T]         | lcd2   | /m/rec        | isa structural field merge",
            "[int::T, int::T]                 | lcd3   | /m/int        | predicate-less (same base type)",
            "[int::T, str::T]                 | lcd4   | #             | disjoint hierarchies fall back to ALL",
            "[pos::T]                         | lcd5   | /m/int        | single type preserves structure",
            "[pairInt::T, tripleInt::T]       | lcd6   | /m/int        | coefficient span (2,3 → 2,3)",
            "[pairInt::T, many::T]            | lcd7   | /m/int        | coefficient span with unbounded (2,*→*)",
            "[addrCity::T, addrZip::T]        | lcd8   | /m/rec        | nested isa structural merge",
            "[pos::T, small::T, int::T]       | lcd9   | /m/int        | mixed predicate + predicate-less",
            "[pos::T, small::T, mid::T]       | lcd10  | /m/int        | three-way non-isa OR",
            "[human::T, artifact::T, company::T] | lcd11 | /m/rec     | three isa records (shared+unique fields)",
            "[human::T, mortal::T]           | lcd12  | human         | multi-level stack (isa + non-isa from child)",
            "[ageInt::T, ageStr::T]           | lcd13  | /m/rec        | conflicting field types (age→ALL)",
    }, delimiter = '|')
    public void testGenerateLCD(final String typeList, final String lcdVID, final String expectedBase,
                                final String description) {
        final Lst typesLst = ObjmtronSerializer.parse(typeList);
        final Set<Type> types = new LinkedHashSet<>();
        for (int i = 0; i < typesLst.count(); i++) {
            final Obj t = typesLst.at(i);
            assertTrue(t.isType(), "element should be a type: " + t);
            types.add(t.asType());
        }

        final Type lcd = Type.Helper.generateLCD(types, f(lcdVID));

        assertNotNull(lcd, "LCD should not be null");
        assertEquals(f(expectedBase), lcd.tid().basePath(),
                "LCD TID mismatch for: " + description);

        // Each input type must be a refinement of the LCD
        for (final Type type : types) {
            assertTrue(type.isRefinementOf(lcd),
                    () -> type.namedType() + " should be a refinement of LCD " + lcd.namedType()
                            + " (" + description + ")");
        }
    }

    @ParameterizedTest(name = "[{index}] {2}")
    @TestData(value = {
            "pos       -> int::T[is(gt(0))]@pos",
            "human     -> rec::T[?[age=>int::T,name=>str::T]]@human",
            "mortal    -> human::T[is(lt(120))]@mortal",
            "namedNoPred -> int::T@namedNoPred",
    })
    @CsvSource(value = {
            // type                | isNominal | description
            "int::T                | false     | base types are nominal types",
            "namedNoPred::T        | true      | named, no predicate, hasVID, not base, no pattern",
            "pos::T                | false     | structural: has non-isa predicate",
            "human::T              | false     | structural: has isa predicate",
            "mortal::T             | false     | structural: inherits isa + adds non-isa",
            "#::T                  | false     | ALL_TYPE excluded (isBaseType via isRootType)",
    }, delimiter = '|')
    public void testIsNominal(final String typeStr, final boolean expectedNominal, final String description) {
        final Type type = ObjmtronSerializer.<Type>parse(typeStr);
        assertEquals(expectedNominal, type.isNominal(), description);
    }

    @ParameterizedTest(name = "[{index}] {3}")
    @TestData(value = {
            "pos       -> int::T[is(gt(0))]@pos",
            "small     -> int::T[is(lt(120))]@small",
            "human     -> rec::T[?[age=>int::T,name=>str::T]]@human",
            "artifact  -> rec::T[?[age=>int::T]]@artifact",
            "mortal    -> human::T[is(lt(120))]@mortal",
    })
    @CsvSource(value = {
            // typeA               | typeB         | isStructuralRefinement? | description
            "pos::T                | int::T        | true                    | struct refines bare base (B has no predicate)",
            "pos::T                | pos::T        | true                    | same predicate = structural refinement",
            "pos::T                | small::T      | false                   | different non-isa predicates",
            "human::T              | rec::T        | true                    | isa type refines its own base (rec has no pred)",
            "human::T              | int::T        | false                   | different base branches (human→rec, int)",
            "human::T              | artifact::T   | false                   | different isa records (human has name field)",
            "mortal::T             | human::T      | true                    | child stack includes parent's isa predicate",
            "mortal::T             | int::T        | false                   | different base branches (mortal→rec, int)",
    }, delimiter = '|')
    public void testIsStructuralRefinementOf(final String typeAStr, final String typeBStr,
                                             final boolean expected, final String description) {
        final Type typeA = ObjmtronSerializer.<Type>parse(typeAStr);
        final Type typeB = ObjmtronSerializer.<Type>parse(typeBStr);
        assertEquals(expected, typeA.isStructuralRefinementOf(typeB), description);
        assertEquals(expected, typeA.testNominally(typeB), description);
    }

    @ParameterizedTest(name = "[{index}] {1}")
    @TestData(value = {
            "pos       -> int::T[is(gt(0))]@pos",
            "human     -> rec::T[?[age=>int::T,name=>str::T]]@human",
            "mortal    -> human::T[is(lt(120))]@mortal",
    })
    @CsvSource(value = {
            // type         | description
            "int::T         | base type has no predicate stack",
            "pos::T         | single non-isa predicate",
            "human::T       | single isa predicate",
            "mortal::T      | two-level stack: non-isa + inherited isa",
    }, delimiter = '|')
    public void testCombinedPredicate(final String typeStr, final String description) {
        final Type type = ObjmtronSerializer.<Type>parse(typeStr);
        final Call combined = type.combinedPredicate();
        final List<Call> stack = type.predicateStack();

        if (stack.isEmpty()) {
            assertNull(combined, description + ": combinedPredicate should be null");
        } else {
            assertNotNull(combined, description + ": combinedPredicate should not be null");
            // Combined should have at least as many insts as the first stack entry
            final int combinedInstCount = combined.insts().size();
            final int firstStackInstCount = stack.get(0).insts().size();
            assertTrue(combinedInstCount >= firstStackInstCount,
                    description + ": combined should have >= insts than first stack entry");
        }
    }

    @ParameterizedTest(name = "[{index}] {2}")
    @TestData(value = {
            "pairInt   -> int{2}::T@pairInt",
            "tripleInt -> int{3}::T@tripleInt",
            "many      -> int{*}::T@many",
    })
    @CsvSource(value = {
            // typeA               | typeB         | description
            "pairInt::T            | tripleInt::T  | coefficient span: {2,2} span {3,3} = {2,3} not {5,5}",
            "pairInt::T            | many::T       | coefficient span: {2,2} span {0,*} = {0,*}",
            "int::T                | int::T        | coefficient span: {1,1} span {1,1} = {1,1}",
    }, delimiter = '|')
    public void testFindLCDCoefficientSpan(final String typeAStr, final String typeBStr,
                                           final String description) {
        final Type typeA = ObjmtronSerializer.<Type>parse(typeAStr);
        final Type typeB = ObjmtronSerializer.<Type>parse(typeBStr);
        final Type lcd = Type.Helper.findLCD(List.of(typeA, typeB));

        assertNotNull(lcd, description);
        // LCD coefficient must contain both input coefficients
        assertTrue(typeA.c().within(lcd.c()),
                description + ": typeA coeff " + typeA.c() + " within LCD coeff " + lcd.c());
        assertTrue(typeB.c().within(lcd.c()),
                description + ": typeB coeff " + typeB.c() + " within LCD coeff " + lcd.c());
    }

    @ParameterizedTest
    @TestData(value = {
            "being -> rec::T[?[age=>int::T]]@being",
            "person -> being::T[?[name=>str::T]]@person"
    })
    @CsvSource(value = {
            // typeA                                 | success
            "being::[name=>34]                       | false",
            "being::[name=>'marko']                  | false",
            "being::[=>]                             | false",
            "being::[age=>34]                        | true",
            "being::[auge=>6]                        | false",
            "being::[age=>'34']                      | false",
            "person::3                               | false",
            "person::[=>]                            | false",
            "person::[age=>34]                       | false",
            "person::[name=>'marko',age=>'x']        | false",
            "person::[name=>'marko',age=>29]         | true"
    }, delimiter = '|')
    public void testPredicateConstructionChain(final String obj, final boolean success) {
        final Obj v = ObjmtronSerializer.parse(obj);
        // a construction-time type violation is a returned fail::T, not a throw
        assertEquals(success, v.stream().noneMatch(Obj::isFail),
                "%s should%s be a valid value, got: %s".formatted(obj, success ? "" : " not", v));
    }

    /**
     * Type-resolution scaffolding: a hierarchy that is deliberately BOTH deep and branching.
     * <p>
     * Depth exercises the parent chain (stockholmare is four levels down). Branching exercises sibling
     * comparison — human and animal disagree on required keys, while swedish and french share a parent
     * and disagree only on a value predicate.
     *
     * <pre>
     *   /m/type_test/creature          rec::T[?[age=>int::T]]
     *   |- /m/type_test/human          creature::T[?[name=>str::T]]
     *   |  |- /m/type_test/swedish     human::T[?[name=>?str::T.has('son')]]
     *   |  |  |- /m/type_test/stockholmare  swedish::T[?[city=>str::T]]          depth 4
     *   |  |- /m/type_test/french      human::T[?[name=>?str::T.has('eau')]]      sibling branch
     *   |- /m/type_test/animal         creature::T[?[species=>str::T]]
     *      |- /m/type_test/dog         animal::T[?[legs=>int::T]]
     * </pre>
     */
    @ParameterizedTest
    @TestData(value = {
            // ---- the hierarchy: deep (creature -> human -> swedish -> stockholmare) and branching ----
            "rec::T[?[age=>int::T]]@/m/type_test/creature",
            "/m/type_test/creature::T[?[name=>str::T]]@/m/type_test/human",
            "/m/type_test/human::T[?[name=>?str::T.has('son')]]@/m/type_test/swedish",
            "/m/type_test/swedish::T[?[city=>str::T]]@/m/type_test/stockholmare",
            "/m/type_test/human::T[?[name=>?str::T.has('eau')]]@/m/type_test/french",
            "/m/type_test/creature::T[?[species=>str::T]]@/m/type_test/animal",
            "/m/type_test/animal::T[?[legs=>int::T]]@/m/type_test/dog",
            // ---- within the chain, both directions: each pair a candidate coupling ----
            "as?rng=/m/type_test/creature&dom=/m/type_test/human(/m/type_test/creature::T)@/m/type_test/as/1",
            "as?rng=/m/type_test/human&dom=/m/type_test/creature(/m/type_test/human::T)@/m/type_test/as/2",
            "as?rng=/m/type_test/human&dom=/m/type_test/swedish(/m/type_test/human::T)@/m/type_test/as/3",
            "as?rng=/m/type_test/swedish&dom=/m/type_test/human(/m/type_test/swedish::T)@/m/type_test/as/4",
            "as?rng=/m/type_test/swedish&dom=/m/type_test/stockholmare(/m/type_test/swedish::T)@/m/type_test/as/5",
            "as?rng=/m/type_test/stockholmare&dom=/m/type_test/swedish(/m/type_test/stockholmare::T)@/m/type_test/as/6",
            "as?rng=/m/type_test/human&dom=/m/type_test/french(/m/type_test/human::T)@/m/type_test/as/7",
            "as?rng=/m/type_test/french&dom=/m/type_test/human(/m/type_test/french::T)@/m/type_test/as/8",
            "as?rng=/m/type_test/creature&dom=/m/type_test/animal(/m/type_test/creature::T)@/m/type_test/as/9",
            "as?rng=/m/type_test/animal&dom=/m/type_test/creature(/m/type_test/animal::T)@/m/type_test/as/10",
            "as?rng=/m/type_test/animal&dom=/m/type_test/dog(/m/type_test/animal::T)@/m/type_test/as/11",
            "as?rng=/m/type_test/dog&dom=/m/type_test/animal(/m/type_test/dog::T)@/m/type_test/as/12",
            // ---- across the fork: human and animal share a parent, neither refines the other ----
            "as?rng=/m/type_test/animal&dom=/m/type_test/human(/m/type_test/animal::T)@/m/type_test/as/13",
            "as?rng=/m/type_test/human&dom=/m/type_test/animal(/m/type_test/human::T)@/m/type_test/as/14",
            // ---- sibling branch: swedish and french are both human, neither refines the other ----
            "as?rng=/m/type_test/swedish&dom=/m/type_test/french(/m/type_test/swedish::T)@/m/type_test/as/15",
            // ---- deep across the fork: two levels down on each side ----
            "as?rng=/m/type_test/dog&dom=/m/type_test/stockholmare(/m/type_test/dog::T)@/m/type_test/as/16"})
    @CsvSource(value = {
            // instance                                          % type                            % matches
            // ---- depth: every ancestor accepts through the chain ----
            "[age=>40, name=>'andersson', city=>'stockholm']    % /m/type_test/creature::T        % true",
            "[age=>40, name=>'andersson', city=>'stockholm']    % /m/type_test/human::T           % true",
            "[age=>40, name=>'andersson', city=>'stockholm']    % /m/type_test/swedish::T         % true",
            "[age=>40, name=>'andersson', city=>'stockholm']    % /m/type_test/stockholmare::T    % true",
            // ---- branching: swedish and french share a parent, disagree on the name predicate ----
            "[age=>40, name=>'andersson']                       % /m/type_test/swedish::T         % true",
            "[age=>40, name=>'andersson']                       % /m/type_test/french::T          % false",
            "[age=>40, name=>'moreau']                          % /m/type_test/french::T          % true",
            "[age=>40, name=>'moreau']                          % /m/type_test/swedish::T         % false",
            // ---- branching at the root: animal is a sibling of human, and neither contains the other ----
            "[age=>40, species=>'primate', legs=>4]             % /m/type_test/dog::T             % true",
            "[age=>40, species=>'primate']                      % /m/type_test/dog::T             % false",
            "[age=>40, name=>'andersson']                       % /m/type_test/animal::T          % false",
            "[age=>40, species=>'primate']                      % /m/type_test/human::T           % false",
            // ---- the parent is strictly weaker than either child ----
            "[age=>40]                                          % /m/type_test/creature::T        % true",
            "[age=>'40']                                        % /m/type_test/creature::T        % false",
            "[name=>'andersson']                                % /m/type_test/creature::T        % false",
            "[age=>40, species=>'primate']                      % /m/type_test/creature::T        % true"},
            delimiter = '%')
    public void testTypeTestHierarchy(final String instance, final String type, final boolean matches) {
        checkMatches(LOG, instance, type, matches);
    }

    /**
     * 1a. Ancestry through depth, implicit side: a value is accepted by every ancestor on its nominal
     * chain, and by nothing below it. This is the same move dom admission makes during dispatch
     * (queriedDom.pathIncludes(i.dom())), tested without any as() instruction in play.
     */
    @ParameterizedTest
    @TestData(value = {
            "rec::T[?[age=>int::T]]@/m/type_test/creature",
            "/m/type_test/creature::T[?[name=>str::T]]@/m/type_test/human",
            "/m/type_test/human::T[?[name=>?str::T.has('son')]]@/m/type_test/swedish",
            "/m/type_test/swedish::T[?[city=>str::T]]@/m/type_test/stockholmare",
            "/m/type_test/human::T[?[name=>?str::T.has('eau')]]@/m/type_test/french",
            "/m/type_test/creature::T[?[species=>str::T]]@/m/type_test/animal",
            "/m/type_test/animal::T[?[legs=>int::T]]@/m/type_test/dog"})
    @CsvSource(value = {
            // instance                                          % type                            % matches
            // ---- four levels down, accepted at every level above it ----
            "[age=>40, name=>'andersson', city=>'stockholm']    % /m/type_test/stockholmare::T    % true",
            "[age=>40, name=>'andersson', city=>'stockholm']    % /m/type_test/swedish::T         % true",
            "[age=>40, name=>'andersson', city=>'stockholm']    % /m/type_test/human::T           % true",
            "[age=>40, name=>'andersson', city=>'stockholm']    % /m/type_test/creature::T        % true",
            "[age=>40, name=>'andersson', city=>'stockholm']    % rec::T                          % true",
            // ---- but not downward: a human is not a stockholmare, and dropping a key does not help ----
            "[age=>40, name=>'andersson']                       % /m/type_test/stockholmare::T    % false",
            "[age=>40, name=>'andersson', city=>'stockholm']    % /m/type_test/dog::T             % false",
            // ---- the other branch, three levels down ----
            "dog::[age=>4, species=>'canine', legs=>4]          % /m/type_test/dog::T             % true",
            "dog::[age=>4, species=>'canine', legs=>4]          % /m/type_test/animal::T          % true",
            "dog::[age=>4, species=>'canine', legs=>4]          % /m/type_test/creature::T        % true",
            // ---- the fork is a real fork: a dog is not on the human chain anywhere ----
            "dog::[age=>4, species=>'canine', legs=>4]          % /m/type_test/human::T           % false",
            "dog::[age=>4, species=>'canine', legs=>4]          % /m/type_test/swedish::T         % false",
            "dog::[age=>4, species=>'canine', legs=>4]          % rec::T                          % true"},
            delimiter = '%')
    public void testTypeTestAncestry(final String instance, final String type, final boolean matches) {
        checkMatches(LOG, instance, type, matches);
    }

    /**
     * 1b. Ancestry through depth, explicit side: the same ascent, but driven by as() one level at a
     * time. The implicit move is granted by dom admission; the explicit one goes through as()
     * dispatch and reads the as-graph. They can disagree, which is the point of splitting them.
     */
    @ParameterizedTest
    @TestData(value = {
            "rec::T[?[age=>int::T]]@/m/type_test/creature",
            "/m/type_test/creature::T[?[name=>str::T]]@/m/type_test/human",
            "/m/type_test/human::T[?[name=>?str::T.has('son')]]@/m/type_test/swedish",
            "/m/type_test/swedish::T[?[city=>str::T]]@/m/type_test/stockholmare",
            "/m/type_test/human::T[?[name=>?str::T.has('eau')]]@/m/type_test/french",
            "/m/type_test/creature::T[?[species=>str::T]]@/m/type_test/animal",
            "/m/type_test/animal::T[?[legs=>int::T]]@/m/type_test/dog",
            "as?rng=/m/type_test/creature&dom=/m/type_test/human(/m/type_test/creature::T)@/m/type_test/as/1",
            "as?rng=/m/type_test/human&dom=/m/type_test/swedish(/m/type_test/human::T)@/m/type_test/as/3",
            "as?rng=/m/type_test/swedish&dom=/m/type_test/stockholmare(/m/type_test/swedish::T)@/m/type_test/as/5",
            "as?rng=/m/type_test/creature&dom=/m/type_test/animal(/m/type_test/creature::T)@/m/type_test/as/9",
            "as?rng=/m/type_test/animal&dom=/m/type_test/dog(/m/type_test/animal::T)@/m/type_test/as/11"})
    @CsvSource(value = {
            // code                                                                     % expected
            // ---- one explicit hop, then the next: each rung names its own target ----
            "[age=>40, name=>'andersson', city=>'stockholm'].as(/m/type_test/swedish::T)              % /m/type_test/swedish::[age=>40, name=>'andersson', city=>'stockholm']",
            "[age=>40, name=>'andersson', city=>'stockholm'].as(/m/type_test/swedish::T).as(/m/type_test/human::T)   % /m/type_test/human::[age=>40, name=>'andersson', city=>'stockholm']",
            "[age=>40, name=>'andersson', city=>'stockholm'].as(/m/type_test/swedish::T).as(/m/type_test/human::T).as(/m/type_test/creature::T)   % /m/type_test/creature::[age=>40, name=>'andersson', city=>'stockholm']",
            // ---- the other branch ----
            "dog::[age=>4, species=>'canine', legs=>4].as(/m/type_test/animal::T)                     % /m/type_test/animal::[age=>4, species=>'canine', legs=>4]"},
            delimiter = '%')
    public void testTypeTestAncestryExplicit(final String code, final String expected) {
        checkCodeParseApply(LOG, code, expected);
    }

    /**
     * 2a. Sibling predicates under one parent. The pair is NOT exclusive: a name carrying both 'son'
     * and 'eau' satisfies swedish and french at once, and the same value is also nordic. This is the
     * value-level witness that the sibling predicates overlap, which is what makes 2b's label
     * interesting rather than academic.
     */
    @ParameterizedTest
    @TestData(value = {
            "rec::T[?[age=>int::T]]@/m/type_test/creature",
            "/m/type_test/creature::T[?[name=>str::T]]@/m/type_test/human",
            "/m/type_test/human::T[?[name=>?str::T.has('son')]]@/m/type_test/swedish",
            "/m/type_test/human::T[?[name=>?str::T.has('eau')]]@/m/type_test/french",
            "/m/type_test/human::T[?[name=>?str::T.has('an')]]@/m/type_test/nordic"})
    @CsvSource(value = {
            // instance                        % type                       % matches
            "[age=>40, name=>'andersson']       % /m/type_test/swedish::T   % true",
            "[age=>40, name=>'andersson']       % /m/type_test/french::T    % false",
            "[age=>40, name=>'andersson']       % /m/type_test/nordic::T    % true",
            "[age=>40, name=>'moreau']          % /m/type_test/french::T    % true",
            "[age=>40, name=>'moreau']          % /m/type_test/swedish::T   % false",
            "[age=>40, name=>'moreau']          % /m/type_test/nordic::T    % false",
            // ---- the witness: one name, two siblings satisfied ----
            "[age=>40, name=>'soneau']          % /m/type_test/swedish::T   % true",
            "[age=>40, name=>'soneau']          % /m/type_test/french::T    % true",
            "[age=>40, name=>'soneau']          % /m/type_test/nordic::T    % false"},
            delimiter = '%')
    public void testTypeTestSiblingOverlap(final String instance, final String type, final boolean matches) {
        checkMatches(LOG, instance, type, matches);
    }

    /**
     * 2b. The labels checkAsGraph reports for those same sibling pairs, on a graph we control: three
     * casts into one rng (human), from three incomparable doms. 2a showed the value sets genuinely
     * overlap, so AMBIGUOUS is the semantically right answer for each pair — the question this test
     * pins down is whether the label is reached for the right reason.
     */
    @ParameterizedTest
    @TestData(value = {
            "rec::T[?[age=>int::T]]@/m/type_test/creature",
            "/m/type_test/creature::T[?[name=>str::T]]@/m/type_test/human",
            "/m/type_test/human::T[?[name=>?str::T.has('son')]]@/m/type_test/swedish",
            "/m/type_test/human::T[?[name=>?str::T.has('eau')]]@/m/type_test/french",
            "/m/type_test/human::T[?[name=>?str::T.has('an')]]@/m/type_test/nordic",
            "as?rng=/m/type_test/human&dom=/m/type_test/swedish(/m/type_test/human::T)@/m/type_test/as/3",
            "as?rng=/m/type_test/human&dom=/m/type_test/french(/m/type_test/human::T)@/m/type_test/as/7",
            "as?rng=/m/type_test/human&dom=/m/type_test/nordic(/m/type_test/human::T)@/m/type_test/as/17"})
    @CsvSource(value = {
            // siblingA                        % siblingB                       % label
            "/m/type_test/swedish              % /m/type_test/french            % AMBIGUOUS",
            "/m/type_test/swedish              % /m/type_test/nordic            % AMBIGUOUS",
            "/m/type_test/french               % /m/type_test/nordic            % AMBIGUOUS"},
            delimiter = '%')
    public void testTypeTestSiblingLabels(final String siblingA, final String siblingB, final String label) {
        final List<String> labels = Inst.Helper.checkAsGraph().stream()
                .filter(v -> null != v.type() && v.insts().size() > 1)
                .filter(v -> v.insts().stream().anyMatch(i -> i.tid().dom().basePath().equals(f(siblingA)))
                        && v.insts().stream().anyMatch(i -> i.tid().dom().basePath().equals(f(siblingB))))
                .map(v -> v.type().name())
                .distinct()
                .toList();
        LOG.warn("sibling pair %s / %s => %s", siblingA, siblingB, labels);
        assertTrue(labels.contains(label), siblingA + " / " + siblingB + " should be labelled " + label
                + " but was labelled " + labels);
    }

    /**
     * The predicate stack, stressed. stockholmare carries a four-deep stack —
     * creature[age:int] -> human[name:str] -> swedish[name has 'son'] -> stockholmare[city:str] — and
     * each value below violates exactly ONE level while satisfying every other.
     * <p>
     * The assertion is the intended semantics: testing against a type applies that type's WHOLE
     * inherited stack, so every value fails. That is what makes it a proof the stack is walked rather
     * than only the leaf being examined — if only the leaf were applied, V1/V2/V3 would pass against
     * stockholmare (all three carry a str city), and if only the root were applied, V4 would pass.
     *
     * <pre>
     *   V1  age is a str        violates creature only
     *   V2  name is an int      violates human only
     *   V3  name lacks 'son'    violates swedish only
     *   V4  no city             violates stockholmare only
     *   V5  clean control
     * </pre>
     * <p>
     * DISABLED because six rows fail, and they fail for a reason worth keeping visible: only the
     * type's OWN predicate is applied. Testing V1 (a str age) against human, swedish or stockholmare
     * is ACCEPTED — every descendant ignores the predicate stack above it, so a value can be tagged
     * stockholmare without ever satisfying creature. The stack itself is real and four deep (asserted
     * below); predicateStack() is consumed by isStructuralRefinementOf() and by the union/LCD merge in
     * Type.Helper, but the value path (Type.Helper.typeCheck) applies rhs.predicate() alone. The sixth
     * failure is separate: swedish accepts name=>7, i.e. its own ?str::T.has('son') guard does not
     * reject a non-str.
     */
    @ParameterizedTest
    @TestData(value = {
            "rec::T[?[age=>int::T]]@/m/type_test/creature",
            "/m/type_test/creature::T[?[name=>str::T]]@/m/type_test/human",
            "/m/type_test/human::T[?[name=>?str::T.has('son')]]@/m/type_test/swedish",
            "/m/type_test/swedish::T[?[city=>str::T]]@/m/type_test/stockholmare"})
    @CsvSource(value = {
            // instance                                          % type                            % matches
            // ---- V1: violates the ROOT only; every level below it is satisfied ----
            "[age=>'forty', name=>'andersson', city=>'stockholm'] % /m/type_test/creature::T      % false",
            "[age=>'forty', name=>'andersson', city=>'stockholm'] % /m/type_test/human::T         % false",
            "[age=>'forty', name=>'andersson', city=>'stockholm'] % /m/type_test/swedish::T       % false",
            "[age=>'forty', name=>'andersson', city=>'stockholm'] % /m/type_test/stockholmare::T  % false",
            // ---- V2: violates human only ----
            "[age=>40, name=>7, city=>'stockholm']                % /m/type_test/creature::T      % true",
            "[age=>40, name=>7, city=>'stockholm']                % /m/type_test/human::T         % false",
            "[age=>40, name=>7, city=>'stockholm']                % /m/type_test/swedish::T       % false",
            "[age=>40, name=>7, city=>'stockholm']                % /m/type_test/stockholmare::T  % false",
            // ---- V3: violates swedish only ----
            "[age=>40, name=>'moreau', city=>'stockholm']         % /m/type_test/creature::T      % true",
            "[age=>40, name=>'moreau', city=>'stockholm']         % /m/type_test/human::T         % true",
            "[age=>40, name=>'moreau', city=>'stockholm']         % /m/type_test/swedish::T       % false",
            "[age=>40, name=>'moreau', city=>'stockholm']         % /m/type_test/stockholmare::T  % false",
            // ---- V4: violates the LEAF only ----
            "[age=>40, name=>'andersson']                         % /m/type_test/creature::T      % true",
            "[age=>40, name=>'andersson']                         % /m/type_test/human::T         % true",
            "[age=>40, name=>'andersson']                         % /m/type_test/swedish::T       % true",
            "[age=>40, name=>'andersson']                         % /m/type_test/stockholmare::T  % false",
            // ---- V5: the control, every level satisfied ----
            "[age=>40, name=>'andersson', city=>'stockholm']      % /m/type_test/creature::T      % true",
            "[age=>40, name=>'andersson', city=>'stockholm']      % /m/type_test/human::T         % true",
            "[age=>40, name=>'andersson', city=>'stockholm']      % /m/type_test/swedish::T       % true",
            "[age=>40, name=>'andersson', city=>'stockholm']      % /m/type_test/stockholmare::T  % true"},
            delimiter = '%')
    public void testTypeTestPredicateStack(final String instance, final String type, final boolean matches) {
        assertEquals(4, T(f("/m/type_test/stockholmare")).predicateStack().size(),
                "stockholmare should carry a four-deep predicate stack");
        checkMatches(LOG, instance, type, matches);
    }

    /**
     * NESTED predicate stacks. Two independent chains have to be walked for one value:
     * <pre>
     *   personhood (outer rec):  creature[age:int] -> human[name:str] -> swedish[name has 'son']
     *                            -> stockholmare[city:str] -> {youth|fortyish|elder}[age: &lt;band&gt;::T]
     *   age band (inner int):    int -> nat[?>0] -> young[?<25]
     *                                          -> midbase[?>25] -> middle[?<50]
     *                                          -> old[?>50]
     * </pre>
     * The outer types constrain the age KEY to an inner type whose own stack must then be walked, so
     * resolving "an old swedish person" means creature, human, swedish, stockholmare AND elder outside,
     * and nat and old inside. The inner chain is also tested on bare scalars, where the two levels are
     * separable — notably -3, which satisfies young's own ?&lt;25 but violates nat's ?&gt;0, so the inner
     * ROOT is what rejects it (under25 carries the same leaf predicate with no nat parent, and accepts it).
     */
    @ParameterizedTest
    @TestData(value = {
            // ---- the age band: int -> nat -> {young, midbase -> middle, old} ----
            "int::T[?>0]@/m/type_test/nat",
            "/m/type_test/nat::T[?<25]@/m/type_test/young",
            "/m/type_test/nat::T[?>25]@/m/type_test/midbase",
            "/m/type_test/midbase::T[?<50]@/m/type_test/middle",
            "/m/type_test/nat::T[?>50]@/m/type_test/old",
            // young's leaf predicate alone, with no nat parent -- isolates the inner root
            "int::T[?<25]@/m/type_test/under25",
            // ---- personhood ----
            "rec::T[?[age=>int::T]]@/m/type_test/creature",
            "/m/type_test/creature::T[?[name=>str::T]]@/m/type_test/human",
            "/m/type_test/human::T[?[name=>?str::T.has('son')]]@/m/type_test/swedish",
            "/m/type_test/swedish::T[?[city=>str::T]]@/m/type_test/stockholmare",
            // ---- personhood whose age key is typed by the inner chain ----
            "/m/type_test/stockholmare::T[?[age=>/m/type_test/young::T]]@/m/type_test/youth",
            "/m/type_test/stockholmare::T[?[age=>/m/type_test/middle::T]]@/m/type_test/fortyish",
            "/m/type_test/stockholmare::T[?[age=>/m/type_test/old::T]]@/m/type_test/elder"})
    @CsvSource(value = {
            // instance                                          % type                            % matches
            // ---- the inner chain alone, on bare scalars ----
            "22                                                   % /m/type_test/young::T           % true",
            "22                                                   % /m/type_test/nat::T             % true",
            "22                                                   % /m/type_test/old::T             % false",
            "22                                                   % /m/type_test/middle::T          % false",
            "40                                                   % /m/type_test/midbase::T         % true",
            "40                                                   % /m/type_test/middle::T          % true",
            "55                                                   % /m/type_test/old::T             % true",
            "55                                                   % /m/type_test/midbase::T         % true",
            "55                                                   % /m/type_test/middle::T          % false",
            // ---- the inner ROOT rejects what the inner leaf would accept ----
            "-3                                                   % /m/type_test/under25::T         % true",
            "-3                                                   % /m/type_test/nat::T             % false",
            "-3                                                   % /m/type_test/young::T           % false",
            // ---- the outer stack alone ----
            "[age=>55, name=>'andersson', city=>'stockholm']      % /m/type_test/stockholmare::T    % true",
            "[age=>55, name=>'moreau', city=>'stockholm']         % /m/type_test/stockholmare::T    % false",
            "[age=>55, name=>'andersson']                         % /m/type_test/stockholmare::T    % false",
            // ---- NESTED: personhood outside, age band inside ----
            "[age=>55, name=>'andersson', city=>'stockholm']      % /m/type_test/elder::T           % true",
            "[age=>22, name=>'andersson', city=>'stockholm']      % /m/type_test/elder::T           % false",
            "[age=>55, name=>'moreau', city=>'stockholm']         % /m/type_test/elder::T           % false",
            "[age=>55, name=>'andersson']                         % /m/type_test/elder::T           % false",
            "[age=>22, name=>'andersson', city=>'stockholm']      % /m/type_test/youth::T           % true",
            "[age=>55, name=>'andersson', city=>'stockholm']      % /m/type_test/youth::T           % false",
            "[age=>-3, name=>'andersson', city=>'stockholm']      % /m/type_test/youth::T           % false",
            "[age=>40, name=>'andersson', city=>'stockholm']      % /m/type_test/fortyish::T        % true",
            "[age=>55, name=>'andersson', city=>'stockholm']      % /m/type_test/fortyish::T        % false"},
            delimiter = '%')
    public void testTypeTestNestedPredicateStack(final String instance, final String type, final boolean matches) {
        // the nesting is real: an inner band type carries its own stack, and the outer types carry theirs
        assertEquals(2, T(f("/m/type_test/young")).predicateStack().size(), "young -> nat");
        assertEquals(3, T(f("/m/type_test/middle")).predicateStack().size(), "middle -> midbase -> nat");
        assertEquals(5, T(f("/m/type_test/elder")).predicateStack().size(), "elder -> stockholmare -> swedish -> human -> creature");
        checkMatches(LOG, instance, type, matches);
    }

    /**
     * The construction-time failure path. A typed literal that violates its type throws out of the
     * PARSER (Obj.Helper.objTypeCheck, reached via MObj.<init> from mParser), not from evaluation — so
     * it is now converted to a returned fail::T carrying the value, the declaring type, the level of the
     * predicate stack that rejected it, and the source it was written in. There is no mtron stack to
     * render on this path — no ExecutionStack frame is live at parse time — which is exactly why the
     * source fragment stands in for it.
     * <p>
     * -3 is the case worth watching: it satisfies young's own ?&lt;25 and violates nat's ?&gt;0, and the
     * message names only the leaf.
     */
    @ParameterizedTest
    @TestData(value = {
            "int::T[?>0]@/m/type_test/nat",
            "/m/type_test/nat::T[?<25]@/m/type_test/young",
            "/m/type_test/nat::T[?>50]@/m/type_test/old"})
    @CsvSource(value = {
            // typed literal                    % rejected % rejecting predicate-stack level (- = the type itself)
            "/m/type_test/young::-3             % true     % nat",
            "/m/type_test/nat::-3               % true     % -",
            "/m/type_test/old::22               % true     % -",
            "/m/type_test/young::22             % false    % -",
            "/m/type_test/old::55               % false    % -"},
            delimiter = '%')
    public void testTypeTestConstructionFailure(final String literal, final boolean rejected, final String level) {
        final Obj parsed = ObjmtronSerializer.parse(literal);
        final boolean isFail = parsed.stream().anyMatch(Obj::isFail);
        final String rendered = parsed.toString();
        LOG.warn("construction of %s => %s", literal, rendered);
        assertEquals(rejected, isFail,
                "%s should%s have been rejected, got: %s".formatted(literal, rejected ? "" : " not", rendered));
        if (!rejected)
            return;
        // the failure is a value carrying its source (no ExecutionStack frame is live at parse time, so
        // there is no mtron stack to render — the source fragment and offset stand in for it)
        assertTrue(rendered.contains("while parsing: " + literal),
                "the failure should name the source it came from: " + rendered);
        // and it names the level that rejected it, rather than printing the leaf predicate that passed
        if ("-".equals(level))
            assertFalse(rendered.contains("rejected at the "),
                    "no level clause is needed when the type's own predicate is the one that rejected: " + rendered);
        else
            assertTrue(rendered.contains("rejected at the " + level + " level of the predicate stack"),
                    "the failure should attribute the rejection to the " + level + " level: " + rendered);
        // A construction violation is STATIC, so the affordance is the diagnostic rather than a runtime
        // handler: the failure returns from the parse of the whole expression, so a .catch() written after
        // the offending literal is never parsed and does not attach. Pinned here so the limit stays visible.
        final Obj chained = ObjmtronSerializer.parse(literal + ".catch(0)");
        LOG.warn("chained .catch(0) => %s", chained);
        assertTrue(chained.stream().anyMatch(Obj::isFail),
                "a .catch() chained after a parse-time failure cannot attach, so the fail survives: " + chained);
    }

    /**
     * Mutating a typed obj into an illegal form. The subject is a young_person — age 10, satisfying
     * young[?&lt;25] nested inside nat[?&gt;0] — and the update adds 50, landing at 60, which violates
     * young's own predicate. This asks whether &gt;&gt;= re-checks the obj against the type it carries,
     * and whether a stored value can keep a type it can no longer satisfy.
     */
    @Test
    @TestData(value = {
            "int::T[?>0]@/m/type_test/nat",
            "/m/type_test/nat::T[?<25]@/m/type_test/young",
            "/m/type_test/nat::T[?>50]@/m/type_test/old",
            "rec::T[?[age=>int::T]]@/m/type_test/creature",
            "/m/type_test/creature::T[?[name=>str::T]]@/m/type_test/human",
            "/m/type_test/human::T[?[name=>?str::T.has('son')]]@/m/type_test/swedish",
            "/m/type_test/swedish::T[?[city=>str::T]]@/m/type_test/stockholmare",
            "/m/type_test/stockholmare::T[?[age=>/m/type_test/young::T]]@/m/type_test/young_person",
            "/m/type_test/young_person::[name=>'andersson', age=>10, city=>'stockholm']@/m/type_test/subject"})
    public void testTypeTestIllegalMutation() {
        final Obj before = ObjmtronSerializer.parse("*/m/type_test/subject").apply(noobj());
        LOG.warn("subject before:\n\t%s\n\ttid=%s", before, before.tid());
        assertTrue(before.test(T(f("/m/type_test/young_person"))), "the subject should start out a young_person");

        final Obj mutation = ObjmtronSerializer.parse("*/m/type_test/subject >>= [age=>+50]").apply(noobj());
        final boolean mutationFailed = mutation.stream().anyMatch(Obj::isFail);
        LOG.warn("mutation returned:\n\t%s\n\tisFail=%s", mutation, mutationFailed);

        final Obj after = ObjmtronSerializer.parse("*/m/type_test/subject").apply(noobj());
        LOG.warn("subject after:\n\t%s\n\ttid=%s\n\tstill a young_person? %s",
                after, after.tid(), after.test(T(f("/m/type_test/young_person"))));

        // >>= re-checks the obj against the type it already carries: the update that would produce
        // age=>60 is refused, and the stored value is left untouched at age=>10. What must not happen
        // is a value that still claims young_person while violating young[?<25].
        assertTrue(mutationFailed, "the illegal mutation should have been refused, got: " + mutation);
        assertEquals(before, after, "a refused mutation should leave the subject untouched");
        assertTrue(after.test(T(f("/m/type_test/young_person"))),
                "the subject should still be a young_person after a refused mutation: " + after);
    }

    /**
     * An illegal mutation that is CAUGHT and recovered from in-language. The update to age=>60 violates
     * young[?&lt;25], so &gt;&gt;= raises a catchable fail::T (unlike a typed literal violating its type at
     * construction, which throws out of the parser where no in-language handler can see it). catch() then
     * performs a legal update, +5, on the untouched subject — so the value ends at age=>15, still a
     * young_person. The last assertion is what distinguishes recovery from silent success: +50 would have
     * landed on 60, and +5 on 15.
     */
    @Test
    @TestData(value = {
            "int::T[?>0]@/m/type_test/nat",
            "/m/type_test/nat::T[?<25]@/m/type_test/young",
            "rec::T[?[age=>int::T]]@/m/type_test/creature",
            "/m/type_test/creature::T[?[name=>str::T]]@/m/type_test/human",
            "/m/type_test/human::T[?[name=>?str::T.has('son')]]@/m/type_test/swedish",
            "/m/type_test/swedish::T[?[city=>str::T]]@/m/type_test/stockholmare",
            "/m/type_test/stockholmare::T[?[age=>/m/type_test/young::T]]@/m/type_test/young_person",
            "/m/type_test/young_person::[name=>'andersson', age=>10, city=>'stockholm']@/m/type_test/bill"})
    public void testTypeTestCaughtMutation() {
        final Obj recovered = ObjmtronSerializer.parse(
                "*/m/type_test/bill >>= [age=>+50].catch(@/m/type_test/bill >>= [age=>+5])").apply(noobj());
        final boolean failed = recovered.stream().anyMatch(Obj::isFail);
        LOG.warn("caught mutation returned:\n\t%s\n\tisFail=%s", recovered, failed);

        final Obj after = ObjmtronSerializer.parse("*/m/type_test/bill").apply(noobj());
        LOG.warn("subject after:\n\t%s\n\ttid=%s", after, after.tid());

        assertFalse(failed, "catch() should have absorbed the illegal update: " + recovered);
        assertTrue(after.test(T(f("/m/type_test/young_person"))),
                "the subject should still be a young_person after the caught mutation: " + after);
        // +5 applied (15), +50 did not (60) — this is what distinguishes recovery from silent success
        checkCodeParseApply(LOG, "*/m/type_test/bill>>age", "15");
    }

    /**
     * union() as a coproduct. The declared type names the sum of the age bands, and a value is admitted
     * when ANY band accepts it — union is a disjunction guard (Obj.java:1235) that returns the lhs
     * unchanged, so the predicate itself discards nothing.
     * <p>
     * What discards the member is the INJECTION: as(T) re-tags the value to T's vid, so a young that
     * enters the coproduct comes back tagged generation and the band is gone from the vid. The value
     * itself survives, though, so the member is recoverable by re-testing the members — which is the
     * case-analysis the tag would otherwise have given you, and it is exact while the members are
     * disjoint.
     * <p>
     * The base the coproduct is declared on also matters: declared on rec, a sum of int bands predicates
     * nothing (22 is not a rec[union(...)]), so the usable form is int::T[union(...)].
     */
    @Test
    @TestData(value = {
            "int::T[?>0]@/m/type_test/nat",
            "/m/type_test/nat::T[?<25]@/m/type_test/young",
            "/m/type_test/nat::T[?>25]@/m/type_test/midbase",
            "/m/type_test/midbase::T[?<50]@/m/type_test/middle",
            "/m/type_test/nat::T[?>50]@/m/type_test/old",
            // the coproduct, declared on int — the bands are int refinements
            "int::T[union(/m/type_test/old::T,/m/type_test/middle::T,/m/type_test/young::T)]@/m/type_test/generation",
            // the same sum declared on rec, which is what the sketch used
            "rec::T[union(/m/type_test/old::T,/m/type_test/middle::T,/m/type_test/young::T)]@/m/type_test/generation_rec"})
    public void testTypeTestUnionCoproduct() {
        final Type generation = T(f("/m/type_test/generation"));

        // ---- membership: any band admits the value ----
        assertTrue(jnt(22).test(generation), "young should be a generation");
        assertTrue(jnt(40).test(generation), "middle should be a generation");
        assertTrue(jnt(60).test(generation), "old should be a generation");
        assertFalse(jnt(-3).test(generation), "-3 satisfies no band, so it is not a generation");
        assertFalse(jnt(0).test(generation), "0 violates nat's ?>=0, so it is no band");

        // ---- injection flattens: the band is lost from the vid ----
        checkCodeParseApply(LOG, "/m/type_test/young::22.as(/m/type_test/generation::T)",
                "/m/type_test/generation::22");
        checkCodeParseApply(LOG, "22.as(/m/type_test/generation::T)",
                "/m/type_test/generation::22");

        // ---- but the member is not lost: the value still satisfies its band, so a projection out of the
        //      coproduct is available by re-testing the members (exact while they are disjoint) ----
        final Obj injected = ObjmtronSerializer.parse(
                "/m/type_test/young::22.as(/m/type_test/generation::T)").apply(noobj());
        LOG.warn("injected => %s [vid=%s]", injected, injected.vid());
        assertTrue(injected.test(T(f("/m/type_test/young"))),
                "the injected value still satisfies young, so the band is recoverable: " + injected);
        assertFalse(injected.test(T(f("/m/type_test/middle"))), "22 is not a middle: " + injected);
        assertFalse(injected.test(T(f("/m/type_test/old"))), "22 is not an old: " + injected);

        // ---- the base the sum is declared on is not incidental ----
        final Obj recBased = ObjmtronSerializer.parse("/m/type_test/generation_rec::22").apply(noobj());
        LOG.warn("rec-based coproduct on 22 => %s", recBased);
        assertTrue(recBased.stream().anyMatch(Obj::isFail),
                "a sum of int bands declared on rec predicates nothing: " + recBased);
    }

    /**
     * Coefficient types. The sketch reads a coefficient as part of a nominal type's name —
     * human{3} is "three humans", so one name can describe a group of a known size — and the write form
     * supports that: human{3}::T@small_group and human{3,10}::T@medium_group both register.
     * <p>
     * The coefficient is the type's COMPOSITION: human{3}::T@small_group says one small_group is made of
     * three humans. So the coefficient belongs to the tid — what the named type is built from — while the
     * named type is a single thing, its vid coefficient one.
     * <p>
     * Both halves of the write form register. What the resolved type CARRIES is the question: querying
     * small_group::T returns human::T@small_group with vid.c == 1, which is right (one group), but with
     * tid.c == 1 as well — so the three it is composed of is not there, and the composition never reaches
     * the checker. The type consequently behaves like a bare human: a single lone human satisfies
     * small_group, while a multiplicity of three does not, because a multiplicity is checked against the
     * type's own coefficient, which is one. medium_group's {3,10} span behaves the same way.
     * <p>
     * test() and isa() also disagree here: the isa guard returns the lhs without consulting coefficient
     * bounds, so it accepts the 3-element multiplicity that test() rejects (asserted at the end).
     */
    @ParameterizedTest
    @TestData(value = {
            "rec::T[?[name=>str::T]]@/m/type_test/human",
            "/m/type_test/human{3}::T@/m/type_test/small_group",
            "/m/type_test/human{3,10}::T@/m/type_test/medium_group"})
    @CsvSource(value = {
            // instance                                              % type                             % matches
            // ---- the composition is not checked, so a lone human satisfies a three-human group ----
            "{[name=>'a']}                                          % /m/type_test/small_group::T      % true",
            // ---- a multiplicity IS checked, against the type's own coefficient of one, so a real group fails ----
            "{[name=>'a'],[name=>'b'],[name=>'c']}                  % /m/type_test/small_group::T      % false",
            "{[name=>'a'],[name=>'b'],[name=>'c'],[name=>'d'],[name=>'e']} % /m/type_test/small_group::T % false",
            "{[name=>'a'],[name=>'b']}                              % /m/type_test/medium_group::T     % false",
            "{[name=>'a'],[name=>'b'],[name=>'c'],[name=>'d'],[name=>'e']} % /m/type_test/medium_group::T % false"},
            delimiter = '%')
    public void testTypeTestCoefficientType(final String instance, final String type, final boolean matches) {
        final Type smallGroup = T(f("/m/type_test/small_group"));
        final Type mediumGroup = T(f("/m/type_test/medium_group"));
        LOG.warn("small_group resolves to %s [tid=%s tid.c=%s vid.c=%s]",
                smallGroup, smallGroup.tid(), smallGroup.tid().c(), smallGroup.vid().c());
        LOG.warn("medium_group resolves to %s [tid=%s tid.c=%s]", mediumGroup, mediumGroup.tid(), mediumGroup.tid().c());
        // one small_group: the vid coefficient is one, as it should be
        assertEquals(cInt.ONE(), smallGroup.vid().c(),
                "small_group is a single group, so its vid coefficient is one: " + smallGroup);
        // but the tid should carry the composition — three humans — and does not
        assertEquals(cInt.ONE(), smallGroup.tid().c(),
                "the tid should be human{3} (one small_group is composed of three); it resolves to " + smallGroup.tid());
        checkMatches(LOG, instance, type, matches);
        // the isa guard does not consult coefficient bounds, so it accepts what test() just rejected
        checkCodeParseApply(LOG, "{[name=>'a'],[name=>'b'],[name=>'c']}.isa(/m/type_test/small_group::T)",
                "{[name=>'a'],[name=>'b'],[name=>'c']}");

        // The coefficient IS stored — a first query for small_group{3} resolves to human{3}::T@small_group
        // with tid.c == 3 — so the loss is not in registration. But a later query for the same type resolves
        // to tid.c == 1, so which count a coefficient type resolves with depends on what was queried before
        // it. That is left asserted only as the observation below rather than pinned as a contract.
        LOG.warn("small_group{3} resolves to %s [tid.c=%s]",
                T(f("/m/type_test/small_group{3}")), T(f("/m/type_test/small_group{3}")).tid().c());
    }

    /**
     * Coefficient placement in a polynomial — the bracket list is the SLOT SIGNATURE, and where the
     * coefficient sits decides what it multiplies:
     *
     * <pre>
     *   lst[int,int,int,int]   four slots, each an int
     *   lst[int{4}]            ONE slot, whose value is itself an int{4}   (slot 0 holds four ints)
     *   lst[{4}int]            the list is COMPOSED OF four ints           (four int slots)
     * </pre>
     *
     * The distinction is what lets one construct serve both as a generic (List&lt;Integer&gt;) and as a
     * tuple, because position is significant and each slot carries its own type: lst[age,zipcode] is a
     * two-slot signature, not a bag of two either-or values.
     * <p>
     * Two things are pinned here because they came out differently than the sketch implies. The container
     * spelling lst[{4}int] does not PARSE — the leading brace inside the brackets is read as a
     * rec/multiplicity, and the parser fails at the '[' (asserted below). And the slot signature is checked
     * as CONTAINMENT rather than equality: fewer slots than the signature fails, but MORE pass, so a
     * four-element list satisfies a two-slot signature.
     */
    @ParameterizedTest
    @TestData(value = {
            "age     -> int::T@age",
            "zipcode -> str::T@zipcode"})
    @CsvSource(value = {
            // instance          % type                        % matches
            // ---- the slot signature is exhaustive and ordered ----
            "[1,2,3,4]           % lst[int,int,int,int]::T     % true",
            "[1,2,3]             % lst[int,int,int,int]::T     % false",
            // ---- but MORE slots pass: the signature is a containment, not an equality ----
            "[1,2,3,4]           % lst[int,int]::T             % true",
            // ---- the element coefficient: slot 0 must itself be an int{4} ----
            "[1,2,3,4]           % lst[int{4}]::T              % false",
            // ---- a tuple is a simply typed list: position carries the slot type ----
            "[29,'94103']        % lst[age,zipcode]::T         % true",
            "[29,'94103']        % lst[zipcode,age]::T         % false",
            "[29,94103]          % lst[age,zipcode]::T         % false"},
            delimiter = '%')
    public void testTypeTestCoefficientPolynomial(final String instance, final String type, final boolean matches) {
        checkMatches(LOG, instance, type, matches);
        // the container spelling — "the list is composed of four ints" — does not parse: a leading brace
        // inside the brackets is taken for a rec/multiplicity literal and the parser fails at the '['
        final MTronException e = assertThrows(MTronException.class, () -> ObjmtronSerializer.parse("lst[{4}int]::T"));
        LOG.warn("lst[{4}int]::T => %s", e.getMessage());
    }

    /**
     * Where the slot-signature check goes. Per-slot types are enforced — lst[str,int] takes ['marko',29]
     * and rejects [12,29] — and the arity follows the OPEN WORLD assumption: a type mandates the slots it
     * LISTS and says nothing at all about the slots it does not. lst[int,int] is therefore not "a list of
     * two ints" but "a list whose slot 0 is an int and whose slot 1 is an int" — a longer value is not a
     * violation, because the surplus slots are outside what the type speaks to, while a shorter one IS,
     * because a slot the type requires has nothing in it.
     * <p>
     * The RANGING forms range over the ELEMENT, not over the slot list. A coefficient inside a bracket is
     * always the multiplicity of the value in that slot, checked with within(), so a plain value only
     * qualifies when it falls in the range by itself: a lone 1 is an int{1}, which is out of {2,4} but
     * inside {*}. Supplying a multiplicity instead — see testTypeTestElementCoefficient — is what satisfies
     * the bounded form. No spelling here expresses "between 2 and 4 ints IN THE LIST", which is what
     * ranging var args and ranging generics would need.
     */
    @ParameterizedTest
    @CsvSource(value = {
            // instance              % type                        % matches
            // ---- per-slot types work ----
            "['marko',29]            % lst[str,int]::T             % true",
            "[12,29]                 % lst[str,int]::T             % false",
            "['marko','x']           % lst[str,int]::T             % false",
            // ---- open world: the type mandates the slots it lists, and is silent about the rest ----
            "[1,2,3,4]               % lst[int,int]::T             % true",
            "[1,2,3]                 % lst[int,int,int,int]::T     % false",
            // ---- the slot holds an ELEMENT COEFFICIENT, so a plain value is out of range: a lone 1 is
            //      an int{1}, which is not within {2,4}. The slot wants a multiplicity, not a value. ----
            "[1,2,3]                 % lst[int{2,4}]::T            % false",
            "[1,2]                   % lst[int{2,4}]::T            % false",
            "[1,2,3,4]               % lst[int{2,4}]::T            % false",
            // ---- and an unbounded one accepts a plain value, because 1 IS within {*} ----
            "[1,2,3]                 % lst[int{*}]::T              % true",
            "[1,2,3]                 % lst[int{+}]::T              % true"},
            delimiter = '%')
    public void testTypeTestSlotSignature(final String instance, final String type, final boolean matches) {
        checkMatches(LOG, instance, type, matches);
    }

    /**
     * The open-world arity read as CONSTRUCTION rather than test. A type requires a value for every slot it
     * lists, so a signature one slot wider than the list is unsatisfiable; a list one value wider than the
     * signature is fine, because the type does not speak to the surplus. Rejections come back as fail::T
     * because a typed literal that violates its type is converted rather than thrown.
     */
    @Test
    public void testTypeTestSlotArity() {
        final Obj exact = ObjmtronSerializer.parse("lst[int,int,int,int]::[1,2,3,4]");
        LOG.warn("lst[int,int,int,int]::[1,2,3,4]     => %s", exact);
        assertTrue(exact.stream().noneMatch(Obj::isFail), "four values satisfy four slots: " + exact);

        final Obj fifthSlot = ObjmtronSerializer.parse("lst[int,int,int,int,int]::[1,2,3,4]");
        LOG.warn("lst[int,int,int,int,int]::[1,2,3,4] => %s", fifthSlot);
        assertTrue(fifthSlot.stream().anyMatch(Obj::isFail),
                "the fifth slot has no value to fill it: " + fifthSlot);

        final Obj fifthValue = ObjmtronSerializer.parse("lst[int,int]::[1,2,3,4]");
        LOG.warn("lst[int,int]::[1,2,3,4]             => %s", fifthValue);
        assertTrue(fifthValue.stream().noneMatch(Obj::isFail),
                "surplus slots are outside what the type speaks to, so they are permitted: " + fifthValue);
    }

    /**
     * The type's OWN coefficient. Three coefficient positions share one {..} syntax, which is most of why
     * this area is hard to read:
     * <pre>
     *   int{2,4}          the BASE type's coefficient range  — 2 to 4 ints
     *   int{2,4}::T{5}    the TYPE's own coefficient        — five of that type
     *   lst[int{4}]       the SLOT's element coefficient    — slot 0 holds an int{4}
     * </pre>
     * The first is implemented and tested; this pins what the second one actually carries.
     */
    @Test
    public void testTypeTestTypeCoefficient() {
        final Obj ranged = ObjmtronSerializer.parse("int{2,4}::T");
        LOG.warn("int{2,4}::T     => %s%n\tisType=%s tid=%s tid.c=%s c=%s",
                ranged, ranged.isType(), ranged.tid(), ranged.tid().c(), ranged.c());

        final Obj typed = ObjmtronSerializer.parse("int{2,4}::T{5}");
        LOG.warn("int{2,4}::T{5}  => %s%n\tisType=%s tid=%s tid.c=%s c=%s vid=%s",
                typed, typed.isType(), typed.tid(), typed.tid().c(), typed.c(), typed.vid());

        assertTrue(typed.isType(), "int{2,4}::T{5} should be a type: " + typed);
        // the base range is untouched — it rides on the TID
        assertEquals(cInt.of(2, 4), typed.tid().c(), "the base range is untouched by the type's coefficient");
        // the {5} is recorded on the VID, whose base is /m/type: it counts the type as a member of the
        // type-of-types, which is the reading "five int{2,4} types"
        assertEquals(cInt.of(5, 5), typed.vid().c(), "the type-level coefficient rides on the vid");
        assertEquals(Tokens.TYPE_TID, typed.vid().basePath(), "the vid of a bare ::T is /m/type");
        // and c() reports the TID's coefficient, so the type-level one is NOT reachable through c() —
        // anything asking a type "how many of you" gets the base range instead
        assertEquals(cInt.of(2, 4), typed.c(), "c() surfaces the tid coefficient, not the type-level one");
    }

    /**
     * CLOSING a type, the counterpart to the open-world arity. A slot signature constrains a typed prefix
     * and says nothing about the tail; a predicate on the count closes it:
     *
     * <pre>
     *   lst::T[>-.count()?=2]@lst2      -- merge().count().is(eq(2)) -- exactly two elements
     * </pre>
     *
     * Two spellings read the count and one does not, and the difference is a single character:
     *
     * <pre>
     *   &gt;-.count()?=2     merge().count().is(eq(2))        discriminates
     *   &gt;&gt;.count()?=2    rshift().count().is(eq(2))       discriminates
     *   &gt;&gt;count()?=2     rshift(count()).is(eq(2))        VACUOUS — accepts both
     * </pre>
     *
     * The two working forms accept [1,2] and reject [1,2,3]; the third accepts both. The dot is a real parse
     * difference, not a cosmetic one. mInstSet.sugars() registers two prefix rules for &gt;&gt; — one taking
     * an argument (line 910) and one taking none (line 911) — and without the dot the one-argument rule
     * applies, so count() IS rshift's argument: rshift indexes the list by a call and the count is never
     * taken. With the dot the zero-argument rule applies, and count() is a sibling instruction on the same
     * level, so the count is taken and the predicate discriminates.
     * <p>
     * What does not work either way is CONSTRUCTION. Building a value through the closed type fails with
     * "unable to convert MCode to Lst" — the predicate is stored as a one-element lst whose element is code,
     * and the construction path casts it to a Lst — so it fails before the count is ever consulted. That is
     * why lst2::[1,2,3] fails, and it is not evidence of the count being enforced: lst2::[1,2] fails the same
     * way, as does either spelling.
     */
    @Test
    @TestData(value = {
            "lst::T[>-.count()?=2]@lst2",
            "lst::T[>>.count()?=2]@lst2r",
            "lst::T[>>count()?=2]@lst2v"})
    public void testTypeTestClosedArity() {
        final Obj two = ObjmtronSerializer.parse("[1,2].isa(lst2::T)").apply(noobj());
        final Obj three = ObjmtronSerializer.parse("[1,2,3].isa(lst2::T)").apply(noobj());
        final Obj one = ObjmtronSerializer.parse("[1].isa(lst2::T)").apply(noobj());
        LOG.warn("[1,2].isa(lst2::T)   => %s", two);
        LOG.warn("[1,2,3].isa(lst2::T) => %s", three);
        LOG.warn("[1].isa(lst2::T)     => %s", one);
        // the count predicate closes the type: exactly two, in either direction
        assertTrue(!two.isNoObj() && two.stream().noneMatch(Obj::isFail), "two elements satisfy lst2: " + two);
        assertTrue(three.isNoObj() || three.stream().anyMatch(Obj::isFail), "three elements do not: " + three);
        assertTrue(one.isNoObj() || one.stream().anyMatch(Obj::isFail), "one element does not: " + one);

        // >>.count() — rshift with no key, THEN count — also reads the count
        final Obj twoR = ObjmtronSerializer.parse("[1,2].isa(lst2r::T)").apply(noobj());
        final Obj threeR = ObjmtronSerializer.parse("[1,2,3].isa(lst2r::T)").apply(noobj());
        LOG.warn("[1,2].isa(lst2r::T)   => %s", twoR);
        LOG.warn("[1,2,3].isa(lst2r::T) => %s", threeR);
        assertTrue(!twoR.isNoObj() && twoR.stream().noneMatch(Obj::isFail), "two passes >>.count(): " + twoR);
        assertTrue(threeR.isNoObj() || threeR.stream().anyMatch(Obj::isFail),
                ">>.count() discriminates: three is rejected: " + threeR);

        // >>count() without the dot passes count() as rshift's KEY, so the count is never read and the
        // predicate is vacuous — three is accepted exactly as two is
        final Obj twoV = ObjmtronSerializer.parse("[1,2].isa(lst2v::T)").apply(noobj());
        final Obj threeV = ObjmtronSerializer.parse("[1,2,3].isa(lst2v::T)").apply(noobj());
        LOG.warn("[1,2].isa(lst2v::T)   => %s", twoV);
        LOG.warn("[1,2,3].isa(lst2v::T) => %s", threeV);
        assertTrue(!threeV.isNoObj() && threeV.stream().noneMatch(Obj::isFail),
                ">>count() is vacuous — [1,2,3] is accepted, so the count is never read: " + threeV);

        // but constructing THROUGH the closed type breaks before the count is ever consulted
        for (final String literal : List.of("lst2::[1,2]", "lst2::[1,2,3]")) {
            final Obj built = ObjmtronSerializer.parse(literal);
            LOG.warn("%s => %s", literal, built);
            assertTrue(built.stream().anyMatch(Obj::isFail),
                    "construction through a closed type fails today: " + built);
        }
    }

    /**
     * The element coefficient satisfied properly. lst[int{2,4}] does not range the number of slots — it
     * requires each slot to HOLD a multiplicity in 2..4. So the slot is filled with a multiplicity rather
     * than a value, and then the bounded range is satisfied: {3}2 is three copies of 2, an int{3}, which is
     * within {2,4}. A plain 2 is an int{1} and is out of range.
     */
    @Test
    public void testTypeTestElementCoefficient() {
        final Obj inRange = ObjmtronSerializer.parse("lst[int{2,4}]::[{3}2]");
        LOG.warn("lst[int{2,4}]::[{3}2] => %s", inRange);
        assertTrue(inRange.stream().noneMatch(Obj::isFail),
                "a slot holding an int{3} is within {2,4}: " + inRange);

        final Obj outOfRange = ObjmtronSerializer.parse("lst[int{2,4}]::[2]");
        LOG.warn("lst[int{2,4}]::[2]   => %s", outOfRange);
        assertTrue(outOfRange.stream().anyMatch(Obj::isFail),
                "a slot holding a plain 2 is an int{1}, out of {2,4}: " + outOfRange);

        // and the multiplicity on its own, to show what the slot is being given
        final Obj spread = ObjmtronSerializer.parse("{3}2");
        LOG.warn("{3}2                 => %s [c=%s]", spread, spread.c());
    }

    /**
     * The predicate's OPERAND type. A count predicate evaluates correctly against a value — the projection
     * works standalone and the type discriminates — but the operand is never propagated from the type being
     * defined into the predicate, so anything that has to RESOLVE the predicate rather than apply it breaks,
     * and breaks differently depending on whether the type knows its shape:
     *
     * <pre>
     *   [1,2,3,4]&gt;&gt;.count()?=4               => 4      the projection, standalone
     *   [1,2,3,4]&gt;-.count()?=4               => 4      merge, standalone
     *   lst::T[&gt;-.count()?=4]@lst4           registers, and [1,2,3,4].isa(lst4::T) is accepted
     *   lst4::[1,2,3,4]                      FAILS: unable to convert MCode to Lst — the predicate is
     *                                        still code, and the construction path casts it to a Lst
     *   lst[int,int,int,int]::T[&gt;-.count()?=4]@lst4b   the type now carries its shape ...
     *   lst4b::[1,2,3,4]                     ... and construction fails EARLIER, at parse time, with a
     *                                        left-recursion error rather than reaching the cast
     * </pre>
     *
     * So giving the type a polynomial moves the failure rather than fixing it: the operand is still not
     * known to the predicate, and the shape only changes which stage notices.
     */
    @Test
    @TestData(value = {
            "lst::T[>-.count()?=4]@lst4",
            "lst[int,int,int,int]::T[>-.count()?=4]@lst4b"})
    public void testTypeTestPredicateOperand() {
        // the projections work standalone
        assertEquals(jnt(4), ObjmtronSerializer.parse("[1,2,3,4]>>.count()?=4").apply(noobj()), "rshift().count()");
        assertEquals(jnt(4), ObjmtronSerializer.parse("[1,2,3,4]>-.count()?=4").apply(noobj()), "merge().count()");

        // and the count predicate discriminates when applied to a value
        final Obj four = ObjmtronSerializer.parse("[1,2,3,4].isa(lst4::T)").apply(noobj());
        final Obj three = ObjmtronSerializer.parse("[1,2,3].isa(lst4::T)").apply(noobj());
        LOG.warn("[1,2,3,4].isa(lst4::T) => %s", four);
        LOG.warn("[1,2,3].isa(lst4::T)   => %s", three);
        assertTrue(!four.isNoObj() && four.stream().noneMatch(Obj::isFail), "four is accepted: " + four);
        assertTrue(three.isNoObj() || three.stream().anyMatch(Obj::isFail), "three is not: " + three);

        // but resolving it — which is what constructing through the type does — breaks
        final Obj built = ObjmtronSerializer.parse("lst4::[1,2,3,4]");
        LOG.warn("lst4::[1,2,3,4]  => %s", built);
        assertTrue(built.stream().anyMatch(Obj::isFail),
                "construction through a count-predicated type fails on the unresolved predicate: " + built);

        // and with a polynomial on the type it fails earlier still, at parse time
        final MTronException e = assertThrows(MTronException.class,
                () -> ObjmtronSerializer.parse("lst4b::[1,2,3,4]"));
        LOG.warn("lst4b::[1,2,3,4] => %s", e.getMessage());
    }

}
