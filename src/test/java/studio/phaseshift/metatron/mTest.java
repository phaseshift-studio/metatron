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

package studio.phaseshift.metatron;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.io.serial.ObjCleanStringSerializer;
import studio.phaseshift.metatron.isa.m.mInstSet;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.type.Fail;
import studio.phaseshift.metatron.isa.m.type.NoObj;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.sys.type.LogObj;
import studio.phaseshift.metatron.isa.sys.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.sys.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.lang.sys.router.Router;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.START_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

public abstract class mTest {

    protected GraphittyLogger LOG = Graphitty.log(this);
    protected ObjCleanStringSerializer serializer = new ObjCleanStringSerializer();

    @BeforeAll
    public static void begin() {
        BootLoader.load(rec(uri("log"), uri(LogObj.getSLF4J().toString().toLowerCase())));
        mInstSet.create();

    }

    @AfterAll
    public static void end() {
        Router.global().close();
        BootLoader.close();
    }

    public void testMatches(final String lhs, final String rhs, final boolean matches) {
        final Obj a = mParser.m_obj().parse(lhs).get();
        final Obj b = mParser.m_obj().parse(rhs).get();
        final boolean m = a.matches(b);
        LOG.debug("testing %s matches %s: %s [expected:%s]", a, b, m, matches);
        assertEquals(matches, m);
    }

    public void testCode(final String lhs, final String code, final String expected) {
        final Obj a = mParser.m_obj().parse(lhs).get();
        final Obj b = mParser.m_obj().parse(code).get();
        final Obj ex = mParser.m_obj().parse(expected).get();
        final Obj actual = b.apply(a);
        LOG.debug("testing %s.%s => %s [expected:%s]", a, b, actual, ex);
        assertEquals(ex, actual);
    }

    public void evaluate(final String lhs, final String expected) {
        final Obj a = mParser.eval(lhs);
        final Obj b = mParser.eval(expected);
        final Obj actual = b.apply(a);
        LOG.debug("testing %s => %s [expected:%s]", a, b, actual);
        assertEquals(b, actual);
    }


    public void testEquals(final Obj a, final Obj b, final boolean equals) {
        LOG.debug("testing %s == %s [expected:%s]", a, b, equals);
        if (equals)
            assertEquals(a, b);
        else
            assertNotEquals(a, b);
    }

    public void testSpace(final String stateCode, final String mutationCode, final Map<fURI, String> expected) {
        final Obj stateResult = mParser.eval(stateCode);
        final Obj mutationResult = mParser.eval(mutationCode);
        LOG.debug("testing %s <= %s", stateResult, mutationResult);
        expected.forEach((k, v) -> {
            final Obj actual = Router.readFromSpace(k);
            final Obj desired = mParser.eval(v);
            LOG.debug("\t%s [expected] == %s [actual]", desired, actual);
            assertEquals(desired, actual);
        });
    }

    public void testCode(final String code, final String expected) {
        if (expected.trim().equals("<ERROR>")) {
            try {
                final Obj cd = mParser.m_call_prefix(START_INST_TID).parse(code).get();
                final Obj actual = cd.apply(NoObj.noobj());
                if (!(cd.isFail() || actual.isFail())) {
                    if (cd.isFail())
                        cd.<Fail>as().message().printStackTrace();
                    if (actual.isFail())
                        actual.<Fail>as().message().printStackTrace();
                    fail(Graphitty.string("testing %s => %s [expected:%s]", cd, actual, expected));

                }
            } catch (final Exception e) {
                LOG.debug("testing %s => %s", code, e.getMessage());
            }
        } else {
            final Obj cd = mParser.m_call_prefix(START_INST_TID).parse(code).get();
            final Obj ex = mParser.eval(expected);
            final Obj actual = cd.apply(NoObj.noobj());
            LOG.debug("testing %s => %s [expected:%s]", cd, actual, ex);
            assertEquals(ex, actual);
            
          /*  final Obj acd = serializer.read(serializer.write(cd));
            final Obj aex = serializer.read(serializer.write(ex));
            final Obj aactual = serializer.read(serializer.write(actual));
            LOG.debug("testing (de)serialization %s => %s [expected:%s]", acd, aactual, aex);
            assertEquals(aex, aactual); */
        }
    }

}
