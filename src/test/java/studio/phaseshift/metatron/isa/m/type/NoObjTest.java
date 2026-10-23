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
import static studio.phaseshift.metatron.Tokens.NOOBJ_TID;
import static studio.phaseshift.metatron.Tokens.URI_TID;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.ALL_TYPE;
import static studio.phaseshift.metatron.isa.m.mInstSet.NONE;
import static studio.phaseshift.metatron.isa.m.mInstSet.NOOBJ_TYPE;
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
                noobjRec(),                                               // MRec: Rec.REC_NOOBJ
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
    public void testEveryZeroTypeIsTheNoObjType() {
        // nothing refines to nothing: a {0} tid or vid resolves to the one noobj type, whatever it is named
        assertSame(NOOBJ_TYPE, T(NOOBJ_TID));
        assertSame(NOOBJ_TYPE, T(INT_TID.zero()));
        assertSame(NOOBJ_TYPE, T(f("notnoobj").zero()));
        assertSame(NOOBJ_TYPE, T(INT_TID.zero(), f("person"), null, null));   // a zero tid under a name
        assertSame(NOOBJ_TYPE, T(INT_TID, f("person").zero(), null, null));   // a zero vid
        assertSame(NOOBJ_TYPE, noobj().type());
        assertSame(noobj().rng(), noobj().type());
        assertSame(NOOBJ_TYPE, jnt(5).c(cInt.ZERO()).type());
        // zeroable is not zero: int{?} still admits one int
        assertNotSame(NOOBJ_TYPE, T(INT_TID.maybe()));
        assertNotSame(NOOBJ_TYPE, T(INT_TID));
    }

    @Test
    public void testEveryInterfaceHasItsTypedNoObj() {
        // noobj typed as each interface -- a real Int, Str, Rec, ... with coefficient {0} -- so a method can answer
        // that type or nothing without an <O extends Obj> cast. each is the one zero.
        assertTypedNoObj(Int.int_noobj(), Int.INT_NOOBJ, Int.class);
        assertTypedNoObj(Real.real_noobj(), Real.REAL_NOOBJ, Real.class);
        assertTypedNoObj(Str.str_noobj(), Str.STR_NOOBJ, Str.class);
        assertTypedNoObj(Bool.bool_noobj(), Bool.BOOL_NOOBJ, Bool.class);
        assertTypedNoObj(Bytes.bytes_noobj(), Bytes.BYTES_NOOBJ, Bytes.class);
        assertTypedNoObj(Uri.uri_noobj(), Uri.URI_NOOBJ, Uri.class);
        assertTypedNoObj(Lst.lst_noobj(), Lst.LST_NOOBJ, Lst.class);
        assertTypedNoObj(Rec.rec_noobj(), Rec.REC_NOOBJ, Rec.class);
        assertTypedNoObj(Rel.rel_noobj(), Rel.REL_NOOBJ, Rel.class);
        assertTypedNoObj(Code.code_noobj(), Code.CODE_NOOBJ, Code.class);
        assertSame(NOOBJ_TYPE, Type.type_noobj());
    }

    @Test
    public void testAsXOfAZeroIsItsTypedNoObj() {
        // the asX() bridge: noobj asked to be an X answers X's noobj, a real X that is still nothing
        assertSame(Int.INT_NOOBJ, noobj().asInt());
        assertSame(Real.REAL_NOOBJ, noobj().asReal());
        assertSame(Str.STR_NOOBJ, noobj().asStr());          // was "" (coefficient 1): an absent str is not the empty str
        assertSame(Bool.BOOL_NOOBJ, noobj().asBool());        // was a failed cast
        assertSame(Bytes.BYTES_NOOBJ, noobj().asBytes());     // was a failed cast
        assertSame(Uri.URI_NOOBJ, noobj().asUri());
        assertSame(Lst.LST_NOOBJ, noobj().asLst());          // was [] (coefficient 1): an absent lst is not the empty lst
        assertSame(Rec.REC_NOOBJ, noobj().asRec());
        assertSame(Rel.REL_NOOBJ, noobj().asRel());
        // noobj is an Inst, and Call.asCode() casts an inst to its one-step code: [noobj] has coefficient 1, a program
        // that yields nothing, not the zero program. a zero that is no Call (was a failed cast) is Code's noobj
        assertFalse(noobj().asCode().isNoObj());
        assertSame(Code.CODE_NOOBJ, jnt(5).c(cInt.ZERO()).asCode());
        assertSame(NOOBJ_TYPE, noobj().asType());             // was a failed cast
        // every zero is the one zero: a zero int asked to be a str is the str's noobj
        assertSame(Str.STR_NOOBJ, jnt(5).c(cInt.ZERO()).asStr());
        // a Type casts to itself, zero or not: the zero root is still the root parentType() walks up to
        final Type zeroRoot = ALL_TYPE.c(cInt.ZERO()).asType();
        assertTrue(zeroRoot.isRootType());
        assertSame(zeroRoot, zeroRoot.parentType());
        // the empty values are values, present and empty, not zeros
        assertFalse(str("").asStr().isNoObj());
        assertFalse(lst().asLst().isNoObj());
    }

    private static void assertTypedNoObj(final Obj zero, final Obj constant, final Class<? extends Obj> iface) {
        assertSame(constant, zero, iface.getSimpleName() + "'s accessor answers its constant");
        assertTrue(iface.isInstance(zero), zero.getClass().getSimpleName() + " is a " + iface.getSimpleName());
        assertTrue(zero.isNoObj(), iface.getSimpleName() + "'s zero has coefficient {0}");
        assertEquals(noobj(), zero);
        assertEquals(zero, noobj());
        assertEquals(noobj().hashCode(), zero.hashCode());
        assertSame(NOOBJ_TYPE, zero.type(), iface.getSimpleName() + "'s zero is of the noobj type");
        assertEquals("noobj", zero.toString());
    }

    @ParameterizedTest
    @CsvSource(value = {
            // plus(int::T) takes one int: nothing fills it, whatever payload the zero carries. type checking stays on
            // kind and type ({2}3 still fills it); computed operands are checked when computed. nothing is spelled noobj
            // here: in an argument, {0} is the stream holding one 0, so 1.plus({0}) is 1.plus(0)
            "1.plus(2)                  % 3",
            "1.plus(plus(1))            % 3",
            "1.plus({2}3)               % 4",
            "1.plus({0}2)               % <ERROR>",
            "1.plus(noobj)              % <ERROR>",
            "1.mult({0}2)               % <ERROR>",
            "1.minus({0}2)              % <ERROR>",
            "2.div(noobj)               % <ERROR>",
            "'''a''.plus({0}''b'')'     % <ERROR>"},
            delimiter = '%')
    public void testNothingFillsAnOperandSlot(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @Test
    public void testNoObjRecIsTheZeroRec() {
        // Rec.REC_NOOBJ once initialized through asRec(), which answers a zero with noobjRec() -- the field itself,
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
