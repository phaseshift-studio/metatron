package studio.phaseshift.metatron.lang.obj;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.MetatronTest;
import studio.phaseshift.metatron.lang.parse.ObjParser;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static studio.phaseshift.metatron.lang.fURI.f;
import static studio.phaseshift.metatron.lang.obj.mtron.MType.T;

public class TypeTest extends MetatronTest {
    private static final GraphittyLogger LOG = Graphitty.log(TypeTest.class);


    @ParameterizedTest
    @CsvSource(value = {
            // obj                | type                            | matches?
            "1                    | /mtron/int                      | true",
            "\"a_string\"         | /mtron/int                      | false",
            "213.0                | /mtron/int                      | false",
            "1                    | #                               | true",
            "1                    | /+/+                            | true",
            "1                    | +                               | false",
            "/mtron/int[0]::1     | [0]                             | true",
            "/mtron/int[0]::1     | [0,0]                           | true",
            "/mtron/int[0]::1     | +[+]                            | false",
            "/mtron/int[0]::1     | /+/+[?]                         | true",
            "/mtron/int[0]::1     | /+/+[0,1]                       | true",
            "/mtron/int[0]::1     | /+/+[0,99]                      | true",
            "/mtron/int[0]::1     | /+/+[*]                         | true",
            "1                    | /+/#                            | true",
            "int:1                | /+/#                            | true",
            "</mtron/int>::1      | /mtron/int                      | true",
            "</mtron/int>::1      | /mtron/+                        | true",
            "</mtron/int>::1      | /mtron/+/+                      | false",
            "</mtron/int>::1      | /mtron/+/#                      | true",
            "/mtron/int::1        | /mtron/int                      | true",
            "/mtron/int::1        | /mtron/+                        | true",
            "/mtron/int[2]::1     | /mtron/+                        | false",
            "/mtron/int[2]::1     | /mtron/+[*]                     | true",
            "/mtron/int::1        | /mtron/+[?]                     | true",
            "/mtron/int::1        | /mtron/+/+                      | false",
            "/mtron/int::1        | /mtron/+/#                      | true",
            // "/mtron/+[2]::{c,d}   | /mtron/+[2]                  | true",
            "str::\"abc\"         | /+/+/#                          | true",
            "/mtron/int::\"abc\"  | /+/+/+                          | false",
            "/mtron/int::1        | /+/+                            | true",
            "/mtron/str::'abc'    | /+/int                          | false",
            "str::'abc'           | /+/int                          | false",
            "1                    | /+/int                          | true",
            "1                    | /+/str                          | false",
            "1                    | /mtron/+                        | true",
            "1                    | /mtron/+/+                      | false",
            "{1,2,3,4}            | /mtron/int[4]                   | true",
            "{1,2,3,4}            | /mtron/int[3]                   | false",
            "{1,2,3,4}            | /mtron/int[0,3]                 | false",
            "{1,2,3,4}            | /mtron/int[3]                   | false",
            "{1,2,3,4}            | /mtron/int[0,5]                 | true",
            "{1,2,3,4}            | /mtron/int[*]                   | true",
            "{1,2,3,'abc'}        | /mtron/int[*]                   | false",
            "{1,2,3,'abc'}        | /mtron/+[*]                     | true",
            "{1,2,3,'abc'}        | /mtron/+[0,]                    | true",
            "{1,2,3,'abc'}        | /mtron/+[1,]                    | true",
            "{1,2,3,'abc'}        | /mtron/+[+]                     | true",
            "{1,2,3,'abc'}        | /mtron/+[2]                     | false",
            "{1,2,3,'abc'}        | /mtron/+[17,]                   | false",
            "{1,2,3,'abc'}        | /mtron/+[5,]                    | false",
            "{1,2,3,4}            | /mtron/str[*]                   | false",
            "{int[2]::1,int[2]::4}| int[3,5]                        | true",
            "{/mtron/int[2]::1,2} | /mtron/int[3]                   | true", // TODO: think this through more carefully
            "noobj                | #[0]                            | true",
            "noobj                | #[0,0]                          | true",
            "noobj                | #[?]                            | true",
            "noobj                | #[1]                            | false",
            "noobj                | +[0]                            | true",
            "noobj                | a/b/c[0]                        | true",
            "[a=>b]               | #                               | true",
            "plus::(2)            | /mtron/inst/plus                | true",
            "plus::(2)            | /mtron/+/plus                   | true",
            "plus[2]::(2)         | /mtron/inst/plus[2]             | true",
            "plus[5]::(2)         | /mtron/inst/plus[2,7]           | true",
            "plus[4]::()          | #[1,3]                          | false",
            //  "plus[4]::()          | /mtron/+/plus[4]                | true", TODO: weird? (more detailed failed tests in fURITest)
            "plus[4]::()          | /mtron/+/+[*]                   | true"
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
            // obj        | type                          | matches?
            // "noobj        | noobj::T[]                  | true",
            "noobj        | abc[*]::T[]                   | true",
            "noobj        | abc[?]::T[]                   | true",
            "noobj        | abc[+]::T[]                   | false",
            "1            | noobj::T[]                    | false",
            "1            | str::T[]                      | false",
            "1            | lst::T[]                      | false",
            "1            | int::T[]                      | true",
            "1            | int::T[1]                     | true",
            "'a_string'   | int::T[]                      | false",
            "213.0        | int::T[]                      | false",
            "1            | int::T[1]                     | true",
            "1            | int::T[2]                     | false",
            "{1,1}        | int[2]::T[{2,2}]              | false",
            "{1,1}        | int[2]::T[{1,1}]              | true",
            "{1,1}        | int::T[is(gt(0))]             | false",
            // "{1,1}        | int[2]::T[is(gt(0))]          | true", //TODO: MAY BE TOO HARD TO COMPUTE GIVEN THE RECURSSIVE NATURE OF RESOLUTION
            "{1,2}        | int[2]::T[is(gt(1))]          | false",
            //  "1            | int^:is(gt(0))                | false"},
    },
            delimiter = '|')
    public void testTypeObj(final String obj, final String type, final boolean matches) {
        Obj o = ObjParser.m_obj().parse(obj).get();
        Type t = ObjParser.m_obj().parse(type).get();
        LOG.trace("testing %s %s %s", o, matches ? "{{g}}is a{{/g}}" : "{{r}}is not a{{/r}}", t);
        assertEquals(matches, o.matches(t));
    }
}
