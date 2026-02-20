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
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.type.Call;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Fail;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.LogObj;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.Tuple;

import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.START_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.io.ioInstSet.IO_ISA_TID;

public abstract class mTest {

    protected GraphittyLogger LOG = Graphitty.log(this);

    @BeforeAll
    public static void begin() {
        BootLoader.BOOTING = true;
        BootLoader.load(rec(uri("log"), uri(LogObj.getSLF4J().toString().toLowerCase())));
        BootLoader.loadInstSetProvider(IO_ISA_TID);
    }

    @AfterAll
    public static void end() {
        BootLoader.close();
    }

    public static void testMatches(final GraphittyLogger LOG, final String lhs, final String rhs, final boolean matches) {
        final Obj a = mParser.m_obj().parse(lhs).get();
        final Obj b = mParser.m_obj().parse(rhs).get();
        final boolean m = a.test(b);
        LOG.debug("testing %s matches %s: %s [expected:%s]", a, b, m, matches);
        assertEquals(matches, m);
    }

    public static void testCode(final GraphittyLogger LOG, final String lhs, final String code, final String expected) {
        final Obj a = mParser.m_obj().parse(lhs).get();
        final Obj b = mParser.m_obj().parse(code).get();
        final Obj ex = mParser.m_obj().parse(expected).get();
        final Obj actual = b.apply(a);
        LOG.debug("testing %s.%s => %s [expected:%s]", a, b, actual, ex);
        assertEquals(ex, actual);
    }

    public static void evaluate(final GraphittyLogger LOG, final String lhs, final String expected) {
        final Obj a = mParser.eval(lhs);
        final Obj b = mParser.eval(expected);
        final Obj actual = b.apply(a);
        LOG.debug("testing %s => %s [expected:%s]", a, b, actual);
        assertEquals(b, actual);
    }


    public static void testEquals(final GraphittyLogger LOG, final Obj a, final Obj b, final boolean equals) {
        LOG.debug("testing %s == %s [expected:%s]", a, b, equals);
        if (equals)
            assertEquals(a, b);
        else
            assertNotEquals(a, b);
    }

    public static void testSpace(final GraphittyLogger LOG, final String stateCode, final String mutationCode, final Map<fURI, String> expected) {
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

    public static Tuple.Quartet<Obj, Long, Obj, Long> testParseEvalPerformance(final GraphittyLogger LOG, final Supplier<Obj> lhs, final Supplier<Obj> rhs) {
        final Tuple.Pair<Obj, Long> parseResult = CommonUtil.clock(lhs);
        assertInstanceOf(Call.class, parseResult.get0());
        final Tuple.Pair<Obj, Long> evalResult = CommonUtil.clock(parseResult.get0(), rhs.get());
        assertEquals(jnt(3), evalResult.get0());
        return Tuple.Quartet.with(parseResult.get0(), parseResult.get1(), evalResult.get0(), evalResult.get1());
    }

    public static void testRewrite(final GraphittyLogger LOG, final String code, final String expected, final String expectedResult) {
        final Code firstStage = mParser.parse(code);
        final Obj secondStage = mParser.parse(expected);
        final Obj compilation = firstStage.rewrite().tryToInst();
        final Obj result = mParser.parse(expectedResult);
        LOG.debug("testing compilation %s => %s [expected:%s]", firstStage, secondStage, compilation);
        assertEquals(secondStage, compilation);
        Obj actual = firstStage.apply(noobj());
        LOG.debug("testing evaluation 1 %s => %s [expected:%s]", firstStage, actual, result);
        assertEquals(result, actual);
        actual = secondStage.apply(noobj());
        LOG.debug("testing evaluation 2 %s => %s [expected:%s]", secondStage, actual, result);
        assertEquals(result, actual);
        actual = compilation.apply(noobj());
        LOG.debug("testing evaluation 3 %s => %s [expected:%s]", compilation, actual, result);
        assertEquals(result, actual);
    }

    public static void testCode(final GraphittyLogger LOG, final String code, final String expected) {
        if (expected.trim().equals("<ERROR>")) {
            try {
                final Obj cd = code.contains(";") ? mParser.eval(code) : mParser.m_call_prefix(START_INST_TID).parse(code).get();
                final Obj actual2 = cd.apply(noobj());
                LOG.debug("testing %s <= %s", cd, actual2.type());
                actual2.stream().forEach(actual -> {
                    if (!(cd.isFail() || actual.isFail())) {
                        if (cd.isFail())
                            cd.<Fail>as().message().printStackTrace();
                        if (actual.isFail())
                            actual.<Fail>as().message().printStackTrace();
                        fail(Graphitty.string("testing %s => %s [expected:%s]", cd, actual, expected));

                    }
                });
            } catch (final Exception e) {
                LOG.debug("testing %s => %s", code, e.getMessage());
            }
        } else {
            final Obj cd = code.contains(";") ? mParser.eval(code) : mParser.m_call_prefix(START_INST_TID).parse(code).get();
            final Obj ex = mParser.eval(expected);
            final Obj actual = cd.apply(noobj());
            LOG.debug("testing %s => %s => %s [expected:%s]", cd, code, actual, ex);
            if (!actual.equals(ex) && actual.stream().anyMatch(Obj::isFail))
                LOG.error("expectation led to failure: %s", actual);
            assertEquals(ex, actual);
            
          /*  final Obj acd = serializer.read(serializer.write(cd));
            final Obj aex = serializer.read(serializer.write(ex));
            final Obj aactual = serializer.read(serializer.write(actual));
            LOG.debug("testing (de)serialization %s => %s [expected:%s]", acd, aactual, aex);
            assertEquals(aex, aactual); */
        }
    }

}
