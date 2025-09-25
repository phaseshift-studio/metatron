package studio.phaseshift.metatron.lang.obj;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.MetatronTest;

public class IntTest extends MetatronTest {
    @Override
    @ParameterizedTest
    @CsvSource(value = {
            // a                                 | b                            | matches
            "1                                   | 1                            | true",
            "1                                   | int::T[]                     | true",
            "1                                   | str::T[]                     | false"
    }, delimiter = '|')
    public void testMatches(final String lhs, final String rhs, final boolean matches) {
        super.testMatches(lhs, rhs, matches);
    }

    @Override
    @ParameterizedTest
    @CsvSource(value = {
            // a                                 | b                            | matches
            "1                                   | plus(2)                      | 3",
            "1                                   | plus(mult(10))               | 11",
            "1                                   | gt(0)                        | true",
            "1                                   | is(gt(0))                    | 1",
            "1                                   | in(int::T[])                 | true",
            "1                                   | is(in(int::T[]))             | 1",
            "1                                   | in(str::T[])                 | false",
            "1                                   | is(in(str::T[]))             | noobj"
    }, delimiter = '|')
    public void testCode(final String lhs, final String code, final String expected) {
        super.testCode(lhs, code, expected);
    }

    @Override
    @ParameterizedTest
    @CsvSource(value = {
            "str::1                              | <ERROR>",
            "lst::1                              | <ERROR>"
    }, delimiter = '|')
    public void testCode(final String code, final String expected) {
        super.testCode(code, expected);
    }
}
