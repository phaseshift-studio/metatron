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
import studio.phaseshift.metatron.lang.mtron.type.Fail;
import studio.phaseshift.metatron.lang.mtron.type.NoObj;
import studio.phaseshift.metatron.lang.mtron.type.Obj;
import studio.phaseshift.metatron.lang.mtron.mtronParser;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MRec.rec;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MUri.uri;

public class MetatronTest {

    protected GraphittyLogger LOG = Graphitty.log(this);

    @BeforeAll
    public static void begin() {
        BootLoader.load(rec(uri("mode"), uri("testing")));
    }

    public void testMatches(final String lhs, final String rhs, final boolean matches) {
        final Obj a = mtronParser.m_obj().parse(lhs).get();
        final Obj b = mtronParser.m_obj().parse(rhs).get();
        final boolean m = a.matches(b);
        LOG.debug("testing %s matches %s: %s [expected:%s]", a, b, m, matches);
        assertEquals(matches, m);
    }

    public void testCode(final String lhs, final String code, final String expected) {
        final Obj a = mtronParser.m_obj().parse(lhs).get();
        final Obj b = mtronParser.m_obj().parse(code).get();
        final Obj ex = mtronParser.m_obj().parse(expected).get();
        final Obj actual = b.apply(a);
        LOG.debug("testing %s.%s => %s [expected:%s]", a, b, actual, ex);
        assertEquals(ex, actual);
    }

    public void testCode(final String code, final String expected) {
        if (expected.trim().equals("<ERROR>")) {
            try {
                final Obj cd = mtronParser.sugar_code().parse(code).get();
                final Obj actual = cd.apply(NoObj.noobj());
                if (!(cd.isFail() || actual.isFail())) {
                    if(cd.isFail())
                        cd.<Fail>as().jvm().printStackTrace();
                    if(actual.isFail())
                        actual.<Fail>as().jvm().printStackTrace();
                    fail(Graphitty.string("testing %s => %s [expected:%s]", cd, actual, expected));
                    
                }
            } catch (final Exception e) {
                LOG.debug("testing %s => %s", code, e.getMessage());
            }
        } else {
            final Obj cd = mtronParser.m_code_or_obj().parse(code).get();
            final Obj ex = mtronParser.m_obj().parse(expected).get();
            final Obj actual = cd.apply(NoObj.noobj());
            LOG.debug("testing %s => %s [expected:%s]", cd, actual, ex);
            assertEquals(ex, actual);
        }
    }

}
