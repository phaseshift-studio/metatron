/*
 * Regression tests for the inst_dom/inst_rng nominal-label fast-out.
 */

package studio.phaseshift.metatron.isa.m.type;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.TestData;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class TypeLabelFastOutTest extends AbstractMetatronTest {

    @ParameterizedTest
    @TestData(value = {
            "person -> rec::T[?[name=>str::T,age=>int::T]]@person"
    })
    @CsvSource(value = {
            "person::[name=>'marko',age=>29].matches(person::T)   % true",
            "[name=>'marko',age=>29].matches(person::T)           % true",
            "person::[name=>'marko']                              % <ERROR>",
            "person::[name=>'marko',age=>'x']                     % <ERROR>",
            "person::[name=>'marko',age=>29].isa(person::T)       % person::[name=>'marko',age=>29]",
    }, delimiter = '%')
    public void personConstructionAndMatch(final String code, final String expected) {
        checkCodeParseApply(LOG, code, expected);
    }

}
