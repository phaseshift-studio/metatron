package studio.phaseshift.metatron.lang.parse;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.util.ObjUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CodeParseTest {

    @BeforeAll
    public static void setup() {
        BootLoader.load();
    }

    @ParameterizedTest
    @CsvSource(value = {
            "1.plus(2)% 3",
            "2.plus(plus(14))% 18",
            "1.map(2)% 2",
            "{1,2,3}.plus(2)% {3,4,5}",
            "{1}.plus(2)% 3",
            "{22,33}.plus(plus(_))% {66,99}",
            "/mtron/int::2.plus(/mtron/int::5)% /mtron/int::7"
    }, delimiter = '%')
    void testStandardExpressions(final String expression, final String expectedResult) {
        assertEquals(ObjParser.m_obj().parse(expectedResult).<Obj>get(), ObjUtil.oneNoneOrAll(ObjParser.eval(expression)));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "1-<[_,plus(1),3]% [1,2,3]",
    }, delimiter = '%')
    void testSugarExpressions(final String expression, final String expectedResult) {
        assertEquals(ObjParser.m_obj().parse(expectedResult).get(), ObjParser.eval(expression).next());
    }


}
