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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.isa.AbstractObjTest;
import studio.phaseshift.metatron.isa.m.type.impl.MObjs;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;
import studio.phaseshift.metatron.util.IteratorUtil;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_ISA_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;

public class ObjsTest extends AbstractObjTest {

    @BeforeAll
    public static void setup() {
        InstSet.importInstSet(MATH_ISA_TID);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "{1,1,1,1}                      |int{4}::T       |true",
            //    "{1,1,1,nat::1}                 |int{4}::T       |true",
            //    "{1,1,1,'hello'}                |#{4}::T         |true"
    }, delimiter = '|')
    public void testType(final String objs, final String expectedType, final boolean shouldEqual) {
        AbstractMetatronTest.checkEquality(LOG, ObjmtronSerializer.parse(expectedType), ObjmtronSerializer.parse(objs).type(), shouldEqual);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "{1,1,1,1}                      |int{0}       |{,}                            |{1,1,1,1}",
            "int{4}::1                      |int{4}       |int{4}::1                      |{,}",
            "{int{2}::1,int{2}::1}          |int{4}       |int{4}::1                      |{,}",
            "{int{2}::1,int{2}::1}          |int{0}       |{,}                            |{int{4}::1}",
            "{int{2}::1,int{2}::2}          |int{0}       |{,}                            |{int{2}::1,int{2}::2}",
            "{int{2}::1,int{2}::2}          |int{4}       |{int{2}::1,int{2}::2}          |{,}",
            "{1,2,3,4}                      |int{4}       |{1,2,3,4}                      |{,}",
            "{1,2,3,4,5,5,5,5,5}            |int{4}       |{1,2,3,4}                      |int{5}::5",
            "{1,2,3,'four',5,5,5,5,5}       |obj{4}       |{1,2,3,'four'}                 |int{5}::5",
    }, delimiter = '|')
    public void testTake(final String current, final String remove, final String retrieved, final String remaining) {
        super.testTake(current, remove, retrieved, remaining);
    }

    @Test
    public void testObjsGrowth() {
        Obj objs = noobj();
        for (int i = 0; i < 10000; i++) {
            objs = objs.append(jnt(i));
        }
        assertEquals(10000, objs.uniqueC().max().intValue());
        assertEquals(10000, objs.c().min().intValue());
        assertEquals(10000, objs.c().max().intValue());
        assertEquals(10000, objs.stream().count());
        assertEquals(10000, IteratorUtil.count(objs.<Iterable<Obj>>jvm()));
        /// ///////////////////////////////////////////////////////////////
        objs = noobj();
        for (int i = 0; i < 10000; i++) {
            objs = objs.append(jnt(1));
        }
        assertEquals(1, objs.uniqueC().max().intValue());
        assertEquals(10000, objs.c().min().intValue());
        assertEquals(10000, objs.c().max().intValue());
        assertEquals(1, objs.stream().count());
    }

    /**
     * COPY ON WRITE. A coefficient change is a new bag, never an edit of the bag it is asked of — that bag may be held
     * elsewhere (a rec value, a stored value, a parsed code's literal) — and the new bag counts its own total.
     */
    @Test
    public void testCoefficientCopyOnWrite() {
        final Obj bag = objs(jnt(1), jnt(2));
        final Obj tripled = bag.c(c -> c.mult(cInt.of(3)));
        AbstractMetatronTest.checkEquality(LOG, ObjmtronSerializer.parse("{1,2}"), bag, true);
        assertEquals(cInt.of(2), bag.c(), "the bag keeps its own total");
        AbstractMetatronTest.checkEquality(LOG, ObjmtronSerializer.parse("{int{3}::1,int{3}::2}"), tripled, true);
        assertEquals(cInt.of(6), tripled.c(), "the new bag counts its own total");
        assertSame(bag, bag.c(c -> c), "a change that changes nothing is the bag itself");
    }

    /**
     * The processor's compressed dispatch (Processor.invoke): a homogeneous bag is bulked by unique(), compressed to
     * its member (c(one)) for the instruction, and the result is multiplied by the BAG's total afterwards. Both must
     * hold at once — the compressed obj is the member, and the bag still holds and counts every copy.
     */
    @Test
    public void testCompressionKeepsMultiplicity() {
        final Objs bag = MObjs.objs0();
        bag.append(jnt(1));
        bag.append(jnt(1));
        bag.append(jnt(1));
        AbstractMetatronTest.checkEquality(LOG, bool(true), bool(bag.unique()), true);
        final Obj compressed = bag.c(cInt::one);
        AbstractMetatronTest.checkEquality(LOG, jnt(1), compressed, true);
        assertEquals(cInt.of(3), bag.c(), "the bag still counts every copy");
        assertEquals(List.of(jnt(1).c(cInt.of(3))), bag.stream().toList(), "the bag still holds every copy");
    }

    @ParameterizedTest
    @CsvSource(value = {
            "{1,2,3}.count()                                                     % 3",
            "{1}.count()                                                         % 1",
            "{}.count()                                                          % 0",
            "{1,1,1}.count()                                                     % 3",
            "{1,2,1,2}.count()                                                   % 4",
    }, delimiter = '%')
    public void testCount(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "{1,2,3}.merge()                                                     % {1,2,3}",
            "{a,b,c}.merge()                                                     % {a,b,c}",
            "{}.merge()                                                          % {}",
    }, delimiter = '%')
    public void testMerge(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

}
