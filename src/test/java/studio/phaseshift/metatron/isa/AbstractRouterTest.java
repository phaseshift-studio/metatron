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

package studio.phaseshift.metatron.isa;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.TestData;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.mach.type.Router;

import static org.junit.Assert.assertEquals;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public abstract class AbstractRouterTest extends AbstractMetatronTest {

    final Router router;

    protected AbstractRouterTest(final Router router) {
        this.router = router;
    }

    @ParameterizedTest
    @CsvSource(value = {
            "math:pi   | /m/math/pi",
            "math:e    | /m/math/e",
            "math:e{3} | /m/math/e{3}",
    }, delimiter = '|', nullValues = "null")
    @TestData(value = {
            "print('loading test data');",
            "print(/m/inst/import(/m/math,<math:>))",
            "print(*/m/math/inst)"})
    public void testPrefix(final String small, final String big) {
        final fURI s = f(small);
        final fURI b = f(big);
        assertEquals(Router.readFromSpace(b), Router.readFromSpace(s));
        LOG.debug("testing %s prefix %s is %s", s, b, b);
    }


}
