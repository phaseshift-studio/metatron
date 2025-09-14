package studio.phaseshift.metatron.lang.obj;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.mtron.MType;
import studio.phaseshift.metatron.lang.parse.ObjParser;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TypeTest {
    @ParameterizedTest
    @CsvSource(value = {
            // obj             | type        | matches?
            "1                 | /mtron/int  | true",
            "'a_string'        | /mtron/int  | false",
            "213.0             | /mtron/int  | false",
            "1                 | #           | true",
            "1                 | /+/+        | true",
            "1                 | +           | false",
            "1                 | /+/#        | true",
            "int:1             | /+/#        | true",
            "</mtron/int>:1    | /mtron/int  | true",
            "</mtron/int>:1    | /mtron/+    | true",
            "</mtron/int>:1    | /mtron/+/+  | false",
            "</mtron/int>:1    | /mtron/+/#  | true",
            "str:'abc'         | /+/#        | true",
            "/mtron/int:1      | /+/+        | true",
            "/mtron/str:'abc'  | /+/int      | false",
            "str:'abc'         | /+/int      | false",
            "1                 | /+/int      | true",
            "1                 | /+/str      | false",
            "1                 | /mtron/+    | true",
            "1                 | /mtron/+/+  | false" },
            delimiter = '|')
    public void testType(final String obj, final String typefURI, final boolean matches) {
        Obj o = ObjParser.m_obj().parse(obj).get();
        Type t = MType.of(fURI.of(typefURI.trim()));
        assertEquals(matches, o.matches(t));
    }

    @ParameterizedTest
    @CsvSource(value = {
            // obj      | type             | matches?
            "1          | 1                | true",
            "'a_string' | 1                | false",
            "213.0      | 1                | false",
            "1          | 1                | true" },
          //  "1          | int^:is(gt(0))   | false"},
            delimiter = '|')
    public void testTypeObj(final String obj, final String type, final boolean matches) {
        Obj o = ObjParser.m_obj().parse(obj).get();
        Type t = ObjParser.m_obj().parse(type).<Obj>get().type();
        assertEquals(matches, o.matches(t));
    }
}
