package studio.phaseshift.metatron.lang.parse;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CodeParseTest {
    @ParameterizedTest
    @CsvSource(value = {
            "1.plus(2)% 3",
            "2.plus(plus(14))% 18",
            "1.map(2)% 2"
    }, delimiter = '%')
    void testStandardExpressions(final String expression, final String expectedResult) {
        assertEquals(ObjParser.m_obj().parse(expectedResult).get(), ObjParser.eval(expression).next());
    }

    @ParameterizedTest
    @CsvSource(value = {
            "1-<[_,plus(1),3]% [1,2,3]",
    }, delimiter = '%')
    void testSugarExpressions(final String expression, final String expectedResult) {
        assertEquals(ObjParser.m_obj().parse(expectedResult).get(), ObjParser.eval(expression).next());
    }


}
