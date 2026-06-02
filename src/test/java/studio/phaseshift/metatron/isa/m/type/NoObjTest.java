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

package studio.phaseshift.metatron.isa.m.type;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.parser.mParser;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.NONE;
import static studio.phaseshift.metatron.isa.m.type.Obj.none;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class NoObjTest extends AbstractMetatronTest {

    @Test
    public void testNone() {
        assertEquals(NONE, none());
        assertTrue(NONE.isNone());
        assertFalse(jnt(0).isNone());
        assertTrue(none().isNone());
        assertTrue(uri("none").isNone());
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
            "noobj{0}            |noobj{0}               |true",
            "noobj{1}            |noobj{1}               |true"},
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
            "noobj               |#{*}::T                |true",
            "noobj               |age                    |false",
            "uri::noobj          |age{?}                 |false",
            "uri::noobj{0}       |age{?}                 |true",
            "noobj               |age{?}                 |false",
            "noobj{0}            |age{?}                 |false",
            "noobj               |uri{?}::age            |true",
            "noobj{0}            |uri{0}::age            |true",
            "noobj               |noobj::T               |true",
            "noobj               |noobj{1}::T            |true",
            "age{0}              |age{?}                 |true",
            "age                 |age{?}                 |true",
            "noobj               |int{?}::T              |true",
            "noobj               |str{?}::T              |true",
            "noobj{0}            |#{0}::T                |true"},
            delimiter = '|')
    public void testNoObjMatches(final String o1, final String o2, final boolean match) {
        final Obj obj1 = mParser.m_obj().parse(o1).get();
        final Obj obj2 = mParser.m_obj().parse(o2).get();
        LOG.info("testing %s{%s} %s %s{%s}", obj1, obj1.c(), match ? "{{g}}matches{{/g}}" : "{{r}}doesn't match{{/r}}", obj2, obj2.c());
        if (match) {
            Assertions.assertTrue(obj1.test(obj2));
        } else {
            Assertions.assertFalse(obj1.test(obj2));
        }
    }
}
