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
import org.junit.jupiter.api.extension.ExtendWith;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.LogObj;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.Tuple;

import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.HOST;
import static studio.phaseshift.metatron.Tokens.LOGG;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.START_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.io.ioInstSet.IO_ISA_TID;

@ExtendWith(TestSkip.TestSkipExtension.class)
@ExtendWith(TestData.TestDataExtension.class)
public abstract class AbstractMetatronTest {
    static {
        BootLoader.TESTING = true;
    }

    protected static final Random RANDOM = new Random();
    protected GraphittyLogger LOG = Graphitty.log(this);
    protected static GraphittyLogger STATIC_LOG = Graphitty.log(AbstractMetatronTest.class);

    public static int generatePort() {
        return RANDOM.nextInt(10000, 65000);
    }

    @BeforeAll
    public static void begin() {
        memSpace.of(f("/sys/#"), null);
        TypeCheck.enable(TypeCheck.values());
        TypeCheck.disable(TypeCheck.code_resolve);
        BootLoader.BOOTING = true;
        BootLoader.TESTING = true;
        BootLoader.load(rec(uri(LOGG), uri(LogObj.getSLF4J().toString().toLowerCase())));
        InstSet.importInstSet(IO_ISA_TID);
    }

    @AfterAll
    public static void end() {
        BootLoader.close();
    }


    public static void checkMatches(final GraphittyLogger LOG, final String lhs, final String rhs, final boolean matches) {
        final Obj a = ObjmtronSerializer.parse(lhs);
        final Obj b = ObjmtronSerializer.parse(rhs);
        final boolean m = a.test(b);
        LOG.debug("testing %s matches %s: %s [expected:%s]", a, b, m, matches);
        assertEquals(matches, m);
    }

    public static void checkMatchesByID(final GraphittyLogger LOG, final String lhs, final String rhs, final boolean matches) {
        final Obj a = ObjmtronSerializer.parse(lhs).apply(jnt(1));
        final Obj b = ObjmtronSerializer.parse(rhs);
        final boolean m = a.testByID(b);
        LOG.debug("testing %s matches by ID %s: %s [expected:%s]", a, b, m, matches);
        assertEquals(matches, m);
    }

    public static void checkCodeEvaluate(final GraphittyLogger LOG, final String lhs, final String expected) {
        final Obj a = ObjmtronSerializer.parse(lhs);
        final Obj b = ObjmtronSerializer.parse(expected);
        final Obj actual = b.apply(a);
        LOG.debug("testing %s => %s [expected:%s]", a, b, actual);
        if (!expected.trim().equals("<ERROR>")) {
            boolean noFails = a.stream().noneMatch(Obj::isFail);
            if (!noFails)
                a.stream().map(f -> f.asFail().caught()).forEach(LOG::error);
            assertTrue(noFails, "should not have failed");
            assertTrue(actual.stream().noneMatch(Obj::isFail), "should not have failed");
            assertEquals(b, actual);
        } else {
            final boolean fails = actual.stream().anyMatch(Obj::isFail);
            if (!fails)
                a.elements().forEach(LOG::error);
            assertTrue(fails, "should have failed");
        }

    }

    public static void checkCodeEvaluate(final GraphittyLogger LOG, final String evaluate, final String fetchResult, final String expectedResult) {
        final Obj evaluation = ObjmtronSerializer.parse(evaluate).apply();
        final Obj actual = ObjmtronSerializer.parse(fetchResult).apply();
        final Obj expected = ObjmtronSerializer.parse(expectedResult).apply();
        LOG.debug("testing %s; %s [expected:%s]", evaluation, actual, expected);
        if (!expectedResult.trim().equals("<ERROR>")) {
            boolean noFails = evaluation.stream().noneMatch(Obj::isFail);
            if (!noFails)
                evaluation.stream().map(f -> f.asFail().caught()).forEach(LOG::error);
            assertTrue(noFails, "evaluation should not have failed");
            noFails = actual.stream().noneMatch(Obj::isFail);
            if (!noFails)
                actual.stream().map(f -> f.asFail().caught()).forEach(LOG::error);
            assertTrue(noFails, "actual should not have failed");
            assertEquals(expected, actual);
        } else {
            boolean fails = evaluation.stream().anyMatch(Obj::isFail);
            if (!fails) {
                fails = actual.stream().anyMatch(Obj::isFail);
                if (!fails) {
                    evaluation.elements().forEach(LOG::error);
                    actual.elements().forEach(LOG::error);
                }
                assertTrue(fails, "should have failed");
            }
            assertTrue(fails, "should have failed");
        }

    }

    public static void checkEquality(final GraphittyLogger LOG, final Obj a, final Obj b, final boolean equals) {
        LOG.debug("testing %s == %s [expected:%s]", a, b, equals);
        if (equals)
            assertEquals(a, b, Graphitty.string("failed %s != %s", a, b));
        else
            assertNotEquals(a, b, Graphitty.string("failed %s == %s", a, b));
    }

    public static void checkSpaceMutation(final GraphittyLogger LOG, final String stateCode, final String mutationCode, final Map<fURI, String> expected) {
        final Obj stateResult = ObjmtronSerializer.parse(stateCode).apply();
        final Obj mutationResult = ObjmtronSerializer.parse(mutationCode).apply();
        LOG.debug("testing %s <= %s", stateResult, mutationResult);
        expected.forEach((k, v) -> {
            final Obj actual = Router.readFromSpace(k);
            final Obj desired = ObjmtronSerializer.parse(v).apply();
            LOG.debug("\t%s [expected] == %s [actual]", desired, actual);
            assertEquals(desired, actual);
        });
    }

    public static Tuple.Quartet<Obj, Long, Obj, Long> checkParsePerformance(final GraphittyLogger LOG, final Supplier<Obj> lhs, final Supplier<Obj> rhs) {
        final Tuple.Pair<Obj, Long> parseResult = CommonUtil.clock(lhs);
        assertInstanceOf(Call.class, parseResult.get0());
        final Tuple.Pair<Obj, Long> evalResult = CommonUtil.clock(parseResult.get0(), rhs.get());
        assertEquals(jnt(3), evalResult.get0());
        return Tuple.Quartet.with(parseResult.get0(), parseResult.get1(), evalResult.get0(), evalResult.get1());
    }

    public static void checkCodeRewrite(final GraphittyLogger LOG, final String code, final String expected, final String expectedResult, boolean checkTIDs) {
        final Code firstStage = ObjmtronSerializer.parse(code);
        final Call secondStage = ObjmtronSerializer.parse(expected);
        final Call compilation = firstStage.rewrite().tryToInst();
        final Obj result = ObjmtronSerializer.parse(expectedResult);
        if (checkTIDs) {
            assertFalse(secondStage.insts().isEmpty());
            assertEquals(secondStage.insts().size(), compilation.insts().size());
            for (int i = 0; i < compilation.insts().size(); i++) {
                assertEquals(compilation.insts().get(i).tid().basePath(), secondStage.insts().get(i).tid().basePath());
            }
            assertEquals(result, firstStage.apply(noobj()));
        } else {

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
    }

    public static void checkCodeParseApply(final GraphittyLogger LOG, final String lhs, final String code, final String expected) {
        if (expected.trim().equals("<ERROR>")) {
            try {
                final Obj a = ObjmtronSerializer.parse(lhs);
                final Obj b = ObjmtronSerializer.parse(code);
                final Obj actual = b.apply(a);
                LOG.debug("testing %s.%s => %s [expected:%s]", a, b, actual, expected);
                fail("expected error but got " + actual);
            } catch (final Exception e) {
                LOG.debug("testing %s.%s => %s [expected:%s]", lhs, code, e.getMessage(), expected);
            }
        } else {
            final Obj a = ObjmtronSerializer.parse(lhs);
            final Obj b = ObjmtronSerializer.parse(code);
            final Obj ex = ObjmtronSerializer.parse(expected);
            final Obj actual = b.apply(a);
            LOG.debug("testing %s.%s => %s [expected:%s]", a, b, actual, ex);
            checkEquality(LOG, ex, actual, true);
        }
    }

    public static void checkCodeParseApply(final GraphittyLogger LOG, final String code, final String expected) {
        if (expected.trim().equals("<ERROR>")) {
            try {
                final Obj cd = code.contains(";") ? ObjmtronSerializer.parse(code).apply() : ObjmtronSerializer.parse(code);
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
                LOG.error("testing %s => %s", code, e.getMessage());
            }
        } else {
            final Obj cd = ObjmtronSerializer.parse(code);
            final Obj ex = ObjmtronSerializer.parse(expected).apply();
            final Obj actual = cd.apply(noobj());
            LOG.debug("testing %s => %s => %s [expected:%s]", cd, code, actual, ex);
            if (!actual.equals(ex) && actual.stream().anyMatch(Obj::isFail))
                LOG.error("expectation led to failure: %s", actual);
            if (ex.tid().hasPoly()) ///  TODO: how to handle generalization of a polynomial as it relates to equality
                assertTrue(actual.test(ex));
            else
                assertEquals(ex, actual);
            
          /*  final Obj acd = serializer.read(serializer.write(cd));
            final Obj aex = serializer.read(serializer.write(ex));
            final Obj aactual = serializer.read(serializer.write(actual));
            LOG.debug("testing (de)serialization %s => %s [expected:%s]", acd, aactual, aex);
            assertEquals(aex, aactual); */
        }
    }

}
