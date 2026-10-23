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
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.isa.m.parser.mParser;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.INT_TID;
import static studio.phaseshift.metatron.Tokens.LST_TID;
import static studio.phaseshift.metatron.Tokens.URI_TID;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.NONE;
import static studio.phaseshift.metatron.isa.m.mInstSet.PLUS_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Obj.none;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs0;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.noobjRec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec0;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
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
            "noobj{1}            |noobj{1}               |true",
            // the only zero is {0}: every zero-coefficient obj is the one zero, whatever its type or label
            "int{0}::10          |str{0}::'tron'         |true",
            "real{0}::1.5        |noobj                  |true",
            "notnoobj{0}         |noobj                  |true",
            "notnoobj{0}         |int{0}::10             |true",
            "0                   |noobj                  |false",
            "0                   |int{0}::0              |false"},
            delimiter = '|')
    public void testNoObjEquality(final String o1, final String o2, final boolean match) {
        final Obj obj1 = mParser.m_obj().parse(o1).get();
        final Obj obj2 = mParser.m_obj().parse(o2).get();
        LOG.trace("testing %s %s %s", obj1, match ? "{{g}}equals{{/g}}" : "{{r}}not equals{{/r}}", obj2);
        if (match) {
            assertEquals(obj1, obj2);
            assertEquals(obj2, obj1);
            assertEquals(obj1.hashCode(), obj2.hashCode(), "equal objs must hash alike");
        } else {
            assertNotEquals(obj1, obj2);
            assertNotEquals(obj2, obj1);
        }
    }

    @Test
    public void testEveryZeroIsTheOneZero() {
        // one zero-coefficient obj per class that implements equals/hashCode itself (or through Obj.Helper)
        final List<Obj> zeros = List.of(
                noobj(),                                                  // NoObj
                jnt(10).c(cInt.ZERO()),                                   // MInt
                str("tron").c(cInt.ZERO()),                               // MStr
                real(1.5).c(cInt.ZERO()),                                 // MReal
                uri("notnoobj").c(cInt.ZERO()),                           // MUri
                lst(List.<Obj>of(jnt(1), jnt(2)), LST_TID.zero(), null),  // MLst: a coefficient on a lst goes into its slots
                rec(Map.<Obj, Obj>of(uri("a"), jnt(1))).c(cInt.ZERO()),   // MRec
                noobjRec(),                                               // MRec: Rec.NOOBJ_REC
                instB(PLUS_INST_TID, lst(jnt(1))).c(cInt.ZERO()),         // MInst
                T(INT_TID).c(cInt.ZERO()),                                // MType
                objs0(),                                                  // MObjs: zero members
                fail("no object").c(cInt.ZERO()));                        // MFail
        zeros.forEach(zero -> assertTrue(zero.isNoObj(), zero + " should have coefficient {0}, has " + zero.c()));
        for (final Obj a : zeros) {
            for (final Obj b : zeros) {
                assertEquals(a, b, a + " and " + b + " are both {0}");
                assertEquals(a.hashCode(), b.hashCode(), a + " and " + b + " are equal, so they must hash alike");
            }
        }
        final Set<Obj> set = new HashSet<>(zeros);
        assertEquals(1, set.size(), "every zero is the one zero: " + set);
        zeros.forEach(zero -> assertTrue(set.contains(zero)));
        // the value 0 has coefficient 1: it is a number, not the zero object
        assertFalse(jnt(0).isNoObj());
        assertNotEquals(noobj(), jnt(0));
        assertFalse(set.contains(jnt(0)));
    }

    @Test
    public void testZeroContentIsNoObjWhateverItsName() {
        // {0} is the only zero, not the label noobj. self() swaps content in place, past the MUri constructor's
        // zero rule, so the uri keeps coefficient 1: only its content says zero
        final Obj named = uri("a").self(f("noobj").zero(), URI_TID, null);
        final Obj unnamed = uri("a").self(f("notnoobj").zero(), URI_TID, null);
        for (final Obj zero : List.of(named, unnamed)) {
            assertFalse(zero.c().isZero(), zero + " keeps its own coefficient");
            assertTrue(zero.isNoObj(), zero + " holds a zero address");
            assertEquals(noobj(), zero);
            assertEquals(noobj().hashCode(), zero.hashCode());
        }
        // the name alone is an ordinary uri: noobj (c=1) names the concept, it is not the zero
        assertFalse(uri("noobj").isNoObj());
        assertFalse(uri("notnoobj").isNoObj());
    }

    @Test
    public void testNoObjRecIsTheZeroRec() {
        // Rec.NOOBJ_REC once initialized through asRec(), which answers a zero with noobjRec() -- the field itself,
        // still null mid-initialization -- so noobjRec() and noobj().asRec() were null
        assertNotNull(noobjRec());
        assertTrue(noobjRec().isRec());
        assertTrue(noobjRec().isNoObj());
        assertEquals(noobj(), noobjRec());
        assertNotNull(noobj().asRec());
        assertTrue(noobj().asRec().isRec());
        assertTrue(noobj().asRec().isNoObj());
        // the empty rec is a value: present, with no fields
        assertFalse(rec0().isNoObj());
        assertNotEquals(noobj(), rec0());
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
