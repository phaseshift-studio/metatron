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

package studio.phaseshift.metatron.lang.core.m.type;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.MetatronTest;
import studio.phaseshift.metatron.lang.core.m.parser.mParser;
import studio.phaseshift.metatron.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.ui.graphitty.GraphittyLogger;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.*;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class NoObjTest extends MetatronTest {
    
    @ParameterizedTest
    @CsvSource(value = {
            "noobj               | noobj                 |true",
            "noobj               | 10                    |false",
            "noobj               | int{0}::10            |true",
            "noobj{2}            |noobj{1233}            |true",
            "noobj{3}            |noobj                  |true",
            "noobj{4}            |str{4}::'meta'         |false",
            "noobj{4}            |str{0}::'tron'         |true",
            "str{4}::'meta'      |str{0}::'tron'         |false",
            "'meta'              |'meta'                 |true",
            "'meta'              |str{0}::'meta'         |false",
            "noobj               |#{0}                   |true"},
            delimiter = '|')
    public void testNoObjEquality(final String o1, final String o2, final boolean match) {
        final Obj obj1 = mParser.m_obj().parse(o1).get();
        final Obj obj2 = mParser.m_obj().parse(o2).get();
        LOG.trace("testing %s %s %s", obj1, match ? "{{g}}equals{{/g}}" : "{{r}}not equals{{/r}}", obj2);
        if (match) {
            assertEquals(obj1, obj2);
            assertEquals(obj2, obj1);
        } else {
            assertNotEquals(obj1, obj2);
            assertNotEquals(obj2, obj1);
        }
    }

    @ParameterizedTest
    @CsvSource(value = {
            "noobj               | noobj                 |true",
            "noobj               | 10                    |false",
            "noobj               | int{0}::10            |true",
            "noobj{2}            |noobj{1233}            |true",
            "noobj{3}            |noobj                  |true",
            "noobj{4}            |str{4}::'meta'         |false",
            "noobj{4}            |str{0}::'tron'         |true",
            "str{4}::'meta'      |str{0}::'tron'         |false",
            "'meta'              |'meta'                 |true",
            "'meta'              |str{0}::'meta'         |false",
            "noobj               |#{0}                   |true",
            "noobj               |#{?}::a                |true",
            "#{?}::a             |noobj                  |true",
            "noobj{0}            |#{?}::a                |true",
            "#{?}::a             |noobj{0}               |true",
            "noobj               |#{0}::T                |true",
            "noobj               |#{?}::T                |true",
            "#{?}::T             |noobj                  |true",
            "noobj{0}            |#{?}::T                |true",
            "#{?}::T             |noobj{0}               |true",
            "#{+}::T             |noobj                  |false",
            "noobj               |#{+}::T                |false",
            "#{*}::T             |noobj                  |true",
            "noobj               |#{*}::T                |true"},
            delimiter = '|')
    public void testNoObjMatches(final String o1, final String o2, final boolean match) {
        final Obj obj1 = mParser.parse(o1);
        final Obj obj2 = mParser.parse(o2);
        LOG.info("testing %s{%s} %s %s{%s}", obj1, obj1.c(), match ? "{{g}}matches{{/g}}" : "{{r}}doesn't match{{/r}}", obj2, obj2.c());
        if (match) {
            Assertions.assertTrue(obj1.matches(obj2));
        } else {
            Assertions.assertFalse(obj1.matches(obj2));
        }
    }
}
