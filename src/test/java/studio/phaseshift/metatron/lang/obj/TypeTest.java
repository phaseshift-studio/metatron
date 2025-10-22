/*
 * Metatron: A Distributed Computing Language and Virtual Machine
 * Copyright (C) 2025- PhaseShift Studio, LLC
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

package studio.phaseshift.metatron.lang.obj;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.MetatronTest;
import studio.phaseshift.metatron.lang.translate.ObjParser;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static studio.phaseshift.metatron.lang.fURI.f;
import static studio.phaseshift.metatron.lang.obj.mtron.MType.T;

public class TypeTest extends MetatronObjTest {
    private static final GraphittyLogger LOG = Graphitty.log(TypeTest.class);


    @ParameterizedTest
    @CsvSource(value = {
            // obj                | type                            | matches?
            "1                    | /m/int                      | true",
            "\"a_string\"         | /m/int                      | false",
            "213.0                | /m/int                      | false",
            "1                    | #                               | true",
            "1                    | /+/+                            | true",
            "1                    | +                               | false",
            "/m/int{0}::1     | {0}                             | true",
            "/m/int{0}::1     | {,0}                           | true",
            "/m/int{0}::1     | +{+}                            | false",
            "/m/int{0}::1     | /+/+{?}                         | true",
            "/m/int{0}::1     | /+/+{0,1}                       | true",
            "/m/int{0}::1     | /+/+{0,99}                      | true",
            "/m/int{0}::1     | /+/+{*}                         | true",
            "1                    | /+/#                            | true",
            "int:1                | /+/#                            | true",
            "</m/int>::1      | /m/int                      | true",
            "</m/int>::1      | /m/+                        | true",
            "</m/int>::1      | /m/+/+                      | false",
            "</m/int>::1      | /m/+/#                      | true",
            "/m/int::1        | /m/int                      | true",
            "/m/int::1        | /m/+                        | true",
            "/m/int{2}::1     | /m/+                        | false",
            "/m/int{2}::1     | /m/+{*}                     | true",
            "/m/int::1        | /m/+{?}                     | true",
            "/m/int::1        | /m/+/+                      | false",
            "/m/int::1        | /m/+/#                      | true",
            "{c,d}                | /m/uri{2}                   | true",
            "{c,d}                | /m/+{2}                     | true",
            "str::\"abc\"         | /+/+/#                          | true",
            "/m/int::\"abc\"  | /+/+/+                          | false",
            "/m/int::1        | /+/+                            | true",
            "/m/str::'abc'    | /+/int                          | false",
            "str::'abc'           | /+/int                          | false",
            "1                    | /+/int                          | true",
            "1                    | /+/str                          | false",
            "1                    | /m/+                        | true",
            "1                    | /m/+/+                      | false",
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
            "{/m/int{2}::1,2} | /m/int{3}                   | true", // TODO: think this through more carefully
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
            Obj o = ObjParser.m_obj().parse(obj).get();
            Type t = T(f(typefURI.trim()));
            LOG.debug("testing %s %s %s", o, matches ? "{{c}}in{{/c}}" : "{{c}}not in{{/c}}", t);
            assertEquals(matches, o.matches(t));
            //if (!typefURI.startsWith("#") && !o.isNoObj())
            //    this.testType(obj, fURI.of("#[" + o.tid().coefficientValue() + "]").toString(), !o.isNoObj());
            //final boolean a = t.matches(o);
            // assertEquals(matches, a);
        } catch (Exception e) {
            assertFalse(matches, "an exception occurred: " + e);
        }
    }

    @ParameterizedTest
    @CsvSource(value = {
            // obj               | type                                         | matches?
            "noobj               | noobj{0}::T[]                                | true",
            "noobj               | abc{*}::T[]                                  | true",
            "noobj               | abc{?}::T[]                                  | true",
            "noobj               | abc{+}::T[]                                  | false",
            "1                   | noobj::T[]                                   | false",
            "1                   | str::T[]                                     | false",
            "1                   | lst::T[]                                     | false",
            "1                   | int::T[]                                     | true",
            "'a_string'          | int::T[]                                     | false",
            "213.0               | int::T[]                                     | false",
            "1                   | int::T[is(eq(1))]                            | true",
            "1                   | int::T[is(eq(2))]                            | false",
            "{1,1}               | int{2}::T[is(eq({2,2}))]                     | false",
           // "{1,1}               | int{2}::T[is(eq({1,1}))]                   | true",
            "{1,1}               | int{2}::T[]                                  | true",
            "{1,1}               | int::T[is(gt(0))]                            | false",
            "{1,1}               | int{2}::T[is(gt(0))]                         | true",
            "1                   | int{2}::T[is(gt(0))]                         | false",
            "{0,0}               | int{2}::T[is(gt(0))]                         | false",
            // "{1,2}               | int{2}::T[is(gt(0))]                         | false",
            //  "1               | int^:is(gt(0))                               | false"},
    },
            delimiter = '|')
    public void testTypeObj(final String obj, final String type, final boolean matches) {
        Obj o = ObjParser.m_obj().parse(obj).get();
        Type t = ObjParser.m_obj().parse(type).get();
        LOG.trace("testing %s %s %s", o, matches ? "{{g}}is a{{/g}}" : "{{r}}is not a{{/r}}", t);
        assertEquals(matches, o.matches(t));
    }
}
