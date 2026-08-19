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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.TestData;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.FAIL_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;

public class TypeTest extends AbstractMetatronTest {
    private static final GraphittyLogger LOG = Graphitty.log(TypeTest.class);
    private static String LAST_TYPE_DEF = "";


    @Disabled("everything works with the recent inst typing exception console::T (??)")
    @ParameterizedTest
    @CsvSource(value = {
            // obj                | type                            | matches?
            "1                    | plus?int<=int(3)                | true",
            "{2}1                 | plus?#{*}<=int{2}(3)            | true",
            "{2}1                 | plus?int<=int{2}(3)             | false",
            "{3}1                 | plus?int<=int{2}(3)             | false",
            "{0,5}1               | plus?int<=int{2}(3)             | false",
            "1                    | plus(a)                         | true",
            "1                    | plus?uri<=uri(a)                | false",
            "{2}1                 | plus?uri<=uri{2}(a)             | false",
            "{2}1                 | plus?<=#{2}(a)                  | true",
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
            //"bignat::T           | int::T                                     | true",
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
            //   "agenat  % .                                        % int::2.as(agenat::T).as(int::T).as(agenat::T).as(int::T)  % false",
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
            // "*marko.as(rec::T).as(A::T)                           | <ERROR>", // values shouldn't type generic?
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
            "team     -> rec::T[?[flag=>?str::T.-<''>-.count().?=2, member=>being{+}::T]]"})
    @CsvSource(value = {
            "[age=>2]                                                            % rec::T                % true",
            "[age=>2]                                                            % lst::T                % false",
            "[age=>2]                                                            % being::T              % true",
            "[age=>'2']                                                          % being::T              % false",
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
            "[name=>'marko',age=>29,alias=>{'m','mar','mr','mmm'}]               % rec::T             % true",
            "[flag=>'us',member=>{}]                                             % team::T               % false",
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
                    assertTrue(instanceObj.test(typeObj));
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
        try {
            final Obj v = ObjmtronSerializer.parse(obj);
            assertTrue(success, "%s should be a valid value".formatted(v));
        } catch (final Exception e) {
            assertFalse(success, "%s is not a valid value");
        }
    }

}