/*
 * Metatron: A Distributed Computing Language and Virtual Machine
 * Copyright (C) 2025- PhaseShift Studio, LLC
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

package studio.phaseshift.metatron;

import org.junit.jupiter.api.BeforeAll;
import studio.phaseshift.metatron.lang.obj.NoObj;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.lang.translate.ObjParser;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class MetatronTest {

    protected GraphittyLogger LOG = Graphitty.log(this);

    @BeforeAll
    public static void begin() {
        BootLoader.load();
    }

    public void testMatches(final String lhs, final String rhs, final boolean matches) {
        final Obj a = ObjParser.m_obj().parse(lhs).get();
        final Obj b = ObjParser.m_obj().parse(rhs).get();
        final boolean m = a.matches(b);
        LOG.debug("testing %s matches %s: %s [expected:%s]", a, b, m, matches);
        assertEquals(matches, m);
    }

    public void testCode(final String lhs, final String code, final String expected) {
        final Obj a = ObjParser.m_obj().parse(lhs).get();
        final Obj b = ObjParser.m_obj().parse(code).get();
        final Obj ex = ObjParser.m_obj().parse(expected).get();
        final Obj actual = b.apply(a);
        LOG.debug("testing %s.%s => %s [expected:%s]", a, b, actual, ex);
        assertEquals(ex, actual);
    }

    public void testCode(final String code, final String expected) {
        if (expected.trim().equals("<ERROR>")) {
            try {
                final Obj cd = ObjParser.sugar_code().parse(code).get();
                final Obj actual = cd.apply(NoObj.single());
                fail(Graphitty.string("testing %s => %s [expected:%s]", cd, actual, expected));
            } catch (final Exception e) {
                LOG.debug("testing %s => %s", code, e.getMessage());
            }
        } else {
            final Obj cd = ObjParser.m_code_or_obj().parse(code).get();
            final Obj ex = ObjParser.m_obj().parse(expected).get();
            final Obj actual = cd.apply(NoObj.single());
            LOG.debug("testing %s => %s [expected:%s]", cd, actual, ex);
            assertEquals(ex, actual);
        }
    }

}
