package studio.phaseshift.metatron.lang.obj;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.MetatronTest;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.mtron.MType;
import studio.phaseshift.metatron.lang.parse.ObjParser;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class TypeTest extends MetatronTest {
    private static final GraphittyLogger LOG = Graphitty.log(TypeTest.class);


    @ParameterizedTest
    @CsvSource(value = {
            // obj                | type                 | matches?
            "1                    | /mtron/int           | true",
            "\"a_string\"         | /mtron/int           | false",
            "213.0                | /mtron/int           | false",
            "1                    | #                    | true",
            "1                    | /+/+                 | true",
            "1                    | +                    | false",
            "/mtron/int[0]::1     | [0]                  | true",
            "/mtron/int[0]::1     | [0,0]                | true",
            "/mtron/int[0]::1     | +[+]                 | false",
            "/mtron/int[0]::1     | /+/+[?]              | true",
            "/mtron/int[0]::1     | /+/+[0,1]            | true",
            "/mtron/int[0]::1     | /+/+[0,99]           | true",
            "/mtron/int[0]::1     | /+/+[*]              | true",
            "1                    | /+/#                 | true",
            "int:1                | /+/#                 | true",
            "</mtron/int>::1      | /mtron/int           | true",
            "</mtron/int>::1      | /mtron/+             | true",
            "</mtron/int>::1      | /mtron/+/+           | false",
            "</mtron/int>::1      | /mtron/+/#           | true",
            "/mtron/int::1        | /mtron/int           | true",
            "/mtron/int::1        | /mtron/+             | true",
            "/mtron/int[2]::1     | /mtron/+             | false",
            "/mtron/int[2]::1     | /mtron/+[*]          | true",
            "/mtron/int::1        | /mtron/+[?]          | true",
            "/mtron/int::1        | /mtron/+/+           | false",
            "/mtron/int::1        | /mtron/+/#           | true",
            // "/mtron/+[2]::{c,d}   | /mtron/+[2]          | true",
            "str::\"abc\"         | /+/+/#               | true",
            "/mtron/int::\"abc\"  | /+/+/+               | false",
            "/mtron/int::1        | /+/+                 | true",
            "/mtron/str::'abc'    | /+/int               | false",
            "str::'abc'           | /+/int               | false",
            "1                    | /+/int               | true",
            "1                    | /+/str               | false",
            "1                    | /mtron/+             | true",
            "1                    | /mtron/+/+           | false",
            "{1,2,3,4}            | /mtron/int[4]        | true",
            "{1,2,3,4}            | /mtron/int[3]        | false",
            "{1,2,3,4}            | /mtron/int[0,3]      | false",
            "{1,2,3,4}            | /mtron/int[3]        | false",
            "{1,2,3,4}            | /mtron/int[0,5]      | true",
            "{1,2,3,4}            | /mtron/int[*]        | true",
            "{1,2,3,'abc'}        | /mtron/int[*]        | false",
            "{1,2,3,'abc'}        | /mtron/+[*]          | true",
            "{1,2,3,'abc'}        | /mtron/+[0,]         | true",
            "{1,2,3,'abc'}        | /mtron/+[1,]         | true",
            "{1,2,3,'abc'}        | /mtron/+[+]          | true",
            "{1,2,3,'abc'}        | /mtron/+[2]          | false",
            "{1,2,3,'abc'}        | /mtron/+[17,]        | false",
            "{1,2,3,'abc'}        | /mtron/+[5,]         | false",
            "{1,2,3,4}            | /mtron/str[*]        | false",
            "{int[2]::1,int[2]::4}| int[3,5]             | true",
            "{/mtron/int[2]::1,2} | /mtron/int[3]        | true", // TODO: think this through more carefully
            "noobj                | #[0]                 | true",
            "noobj                | #[0,0]               | true",
            "noobj                | #[?]                 | true",
            "noobj                | #[1]                 | false",
            "noobj                | +[0]                 | true",
            "noobj                | a/b/c[0]             | true",
            "[a=>b]               | #                    | true",
            "plus::(2)            | /mtron/inst/plus     | true"
    }, delimiter = '|')
    public void testType(final String obj, final String typefURI, final boolean matches) {
        try {
            Obj o = ObjParser.m_obj().parse(obj).get();
            Type t = MType.of(fURI.of(typefURI.trim()));
            LOG.debug("testing %s %s %s", o, matches ? "{{c}}in{{/c}}" : "{{c}}not in{{/c}}", t);
            assertEquals(matches, o.matches(t));
            //if (!typefURI.startsWith("#") && !o.isNoObj())
            //    this.testType(obj, fURI.of("#[" + o.tid().coefficientValue() + "]").toString(), !o.isNoObj());
            final boolean a = t.matches(o);
            assertEquals(matches, a);
        } catch (Exception e) {
            assertFalse(matches, "an exception occurred: " + e);
        }
    }

    @ParameterizedTest
    @CsvSource(value = {
            // obj        | type               | matches?
            "1            | 1                  | true",
            "'a_string'   | 1                  | false",
            "213.0        | 1                  | false",
            "1            | 1                  | true",
            "1            | 2                  | true",
            "{1,1}        | {2,2}              | true"
            //  "1            | int^:is(gt(0))     | false"},
    },
            delimiter = '|')
    public void testTypeObj(final String obj, final String type, final boolean matches) {
        Obj o = ObjParser.m_obj().parse(obj).get();
        Type t = ObjParser.m_obj().parse(type).<Obj>get().type();
        assertEquals(matches, o.matches(t));
    }
}
