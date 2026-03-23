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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.TestData;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.FAIL_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;

public class TypeTest extends AbstractMetatronTest {
    private static final GraphittyLogger LOG = Graphitty.log(TypeTest.class);
    private static String LAST_TYPE_DEF = "";

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
            "int::1             | a{+}                         | true",
            "int::1             | b{+}                         | true",
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
            Obj o = mParser.m_obj().parse(obj).get();
            Type t = T(f(typefURI.trim()));
            LOG.debug("testing %s %s %s", o, matches ? "{{c}}in{{/c}}" : "{{c}}not in{{/c}}", t);
            // assertEquals(matches, o.type().tid().matches(f(typefURI)));
            assertEquals(matches, o.test(t));
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
            "noobj               | noobj::T                                   | true",
            "noobj               | abc{*}::T                                  | true",
            "noobj               | abc{?}::T                                  | true",
            "noobj               | abc{+}::T                                  | false",
            "1                   | noobj::T                                   | false",
            "1                   | str::T                                     | false",
            "1                   | lst::T                                     | false",
            "1                   | int::T                                     | true",
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
            "{2,3}               | int{2}::T[>-.is(gt(2)).else(fail::T)]      | false",
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
        Obj o = mParser.m_obj().parse(obj).get();
        Type t = mParser.m_obj().parse(type).get();
        LOG.debug("testing %s {{g}}({{b}}%s{{g}}){{X}} %s %s", o, o.tid(), matches ? "{{g}}is a{{/g}}" : "{{r}}is not a{{/r}}", t);
        assertEquals(matches, o.test(t));
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
            //  "int::T              | T::T                                       | true",
            // "T::T                | int::T                                     | false",
            //  "int::T              | T::T[int::T]                               | true",
            //   "int::T[?>2]         | T::T[int::T]                               | true",
            //   "int::T[?>2]         | T::T[int::T[?>2]]                          | true",
            //  "int::T              | T::T[int::T[?>2]]                          | false",
            //  "int::T              | T::T[#::T]                                 | true",
            //"int::T              | T::T[?<real::T>]                              | false",
            "int::T              | str::T                                     | false",
            "int::T              | #::T                                       | true",
            "int::T              | #{?}::T                                    | true",
            "int::T              | #::T{?}                                    | true",
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
        Obj a = mParser.m_obj().parse(typeA).get();
        Obj b = mParser.m_obj().parse(typeB).get();
        LOG.trace("testing %s %s %s", a, matches ? "{{g}}is a{{/g}}" : "{{r}}is not a{{/r}}", b);
        //assertEquals(matches, Objects.equals(a.vid(), b.vid()) || a.vid().matches(b.tid()));
        assertEquals(matches, a.test(b));
        //assertEquals(matches, a.fastMatch(b));
    }

    @ParameterizedTest
    @TestData(value = {"nat -> int::T[is(gt(0))]", "bignat -> nat::T[is(gt(100))]",})
    @CsvSource(value = {
            "/m/int | /m/int | true",
            "/m/int | nat    | false",
            "nat    | /m/int | true",
            "nat    | nat    | true",
            // "bignat | nat    | true",
            "bignat | /m/int | true",
            "/m/int | bignat | false",
            "nat    | bignat | false",
            "bignat | bignat | true",
    }, delimiter = '|')
    public void testBaseTypes(final String type, final String baseType, final boolean matches) throws Exception {
        Obj a = mParser.eval("*" + type);
        Obj b = mParser.eval("*" + baseType);
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
        final Lst typesObj = mParser.parse(typeList);
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
            "nat     % int::T[?>0][-<|[is(lt(0)) => _⋅-1,_ => _]>>]   % nat::23                                          % true",
            "nat     % .                                        % nat::-23                                         % true",
            "nat     % .                                        % nat::'a big number'                              % false",
            "nat     % .                                        % nat::2 + 6                                       % true",
            "nat     % .                                        % nat::2 + -6                                      % false",
            "nat     % .                                        % 23.as(nat::T)                                    % true",
            "nat     % .                                        % -23.as(nat::T)                                   % true",
            "nat     % .                                        % 2.as(plus(6).as(nat::T))                         % true",
            "nat     % .                                        % 2.as(plus(-6).as(nat::T))                        % true",
            ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            "nat     % int::T[?>0]                              % nat::150                                         % true",
            ".       % .                                        % nat::-150                                        % false",
            ".       % .                                        % nat::0                                           % false",
            "agenat  % nat::T[?<125]                            % agenat::150                                      % false",
            "agenat  % .                                        % int::2.as(agenat::T)                             % true",
            "agenat  % .                                        % int::2.as(agenat::T).as(int::T).as(agenat::T)    % true",
            //   "agenat  % .                                        % int::2.as(agenat::T).as(int::T).as(agenat::T).as(int::T)  % false",
            "agenat  % .                                        % int::2.as(nat::T).as(agenat::T)                  % true",
            //  ".       % .                                        % agenat::-1                                       % false",
            ".       % .                                        % agenat::200                                      % false",
            ".       % .                                        % nat::200.as(agenat::T)                           % false",
            ".       % .                                        % agenat::29                                       % true",
    }, delimiter = '%')
    public void testTyping(final String tid, final String typeDef, final String instance, final boolean shouldSucceed) {
        try {
            Router.writeToSpace(tid, noobj());
            Obj type = mParser.parse(typeDef.trim().equals(".") ? LAST_TYPE_DEF : typeDef.trim());
            LAST_TYPE_DEF = typeDef.trim().equals(".") ? LAST_TYPE_DEF : typeDef.trim();
            Router.writeToSpace(tid, type);
            assertEquals(type, Router.readFromSpace(tid));
            LOG.debug("testing %s %s %s", instance, shouldSucceed ? "{{g}}is a{{/g}}" : "{{r}}is not a{{/r}}", type);
            try {
                Obj inst = mParser.eval(instance.trim());
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
        final Obj instanceObj = mParser.m_obj().parse(instance).get();
        final List<Type> expectedTypeStack = mParser.parse(stack).lstValue().stream().map(o -> mParser.<Type>parse(o.toString() + "::T")).toList();
        final List<Type> deducedTypeStack = deducedTypeStack(mParser.m_obj().parse(type).get());
        final List<Boolean> matchesTypeStack = deducedTypeStack.stream().map(instanceObj::test).toList();
        LOG.debug("testing type stack of %s:\n\t%s\n\t%s\n\t%s", instanceObj, expectedTypeStack, deducedTypeStack, matchesTypeStack);
        assertEquals(matches, matchesTypeStack.stream().reduce(true, (a, b) -> a && b));
        //assertEquals(expectedTypeStack, deducedTypeStack.subList(1, deducedTypeStack.size()));
        checkMatches(LOG, instance, type, matches);
    }

    @ParameterizedTest
    @TestData(value = {
            "being    -> rec::T[?[age=>int::T]]",
            "person   -> being::T[?[name=>str::T]]",
            "mortal   -> person::T[?[age=>?<120]]",
            "immortal -> being::T[?[alias=>str{2,3}::T]]",
            "team     -> rec::T[?[flag=>str::T.-<''>-.count().?=2, member=>being{+}::T]]"})
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
         //   "[flag=>'us',member=>{being::[age=>29],being::[age=>34]}]            % team::T               % true",
            "[flag=>'us',member=>{being::[age=>29],mortal::[age=>134]}]          % team::T               % false",
         //   "[flag=>'us',member=>{being::[age=>29],person::[name=>'a',age=>35]}] % team::T               % true",
            "[flag=>'us',member=>{being::[age=>29],[blah=>'stuff']}]             % team::T               % false",
          //  "[flag=>'us',member=>{[age=>29],[age=>34]}]                          % team::T               % true",
         //   "[flag=>'us',member=>{[age=>29],[age=>34],[age=>35]}]                % team::T               % true",
          //  "[flag=>'usa',member=>{[age=>29],[age=>34],[age=>35]}]               % team::T               % false",
            "[flag=>'mex',member=>{[age=>12]}]                                   % team::T               % false",
            "[flag=>'mex',member=>{[age=>12],[age=>13]}]                         % team::T               % false",
          //  "[flag=>'mx',member=>{[age=>12],[age=>13]}]                          % team::T               % true",
    }, delimiter = '%')
    public void testComplexTypes(final String instance, final String type, final boolean matches) {
        LOG.debug("testing %s %s %s", instance, matches ? "{{g}}matches{{/g}}" : "{{r}}doesn't match{{/r}}", type);
        try {
            final Obj instanceObj = mParser.parse(instance);
            final Obj typeObj = mParser.parse(type);
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
        while (null != temp && temp.isType() && !temp.isNoObj()) {
            stack.add(temp.asType());
            Obj parent = temp.asType().parentType();
            temp = parent == temp ? null : parent;
        }
        return stack;
    }

}