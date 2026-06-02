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

package studio.phaseshift.metatron.furi;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.furi.QProc.Helper.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class QProcPipelineTest extends AbstractMetatronTest {

    private Lst emptyQs;
    private fURI noQ;
    private fURI readQ;
    private fURI writeQ;

    @BeforeEach
    public void setup() {
        emptyQs = lst();
        noQ = f("/test/key");
        readQ = f("/test/key?read");
        writeQ = f("/test/key?write");
    }

    // ── Empty QProc list ──

    @Test
    public void testEmptyQProcList_preRead() {
        assertTrue(processPreRead(emptyQs, readQ).isEmpty());
    }

    @Test
    public void testEmptyQProcList_preWrite() {
        assertTrue(processPreWrite(emptyQs, readQ, str("x")).isEmpty());
    }

    @Test
    public void testEmptyQProcList_postRead() {
        assertTrue(processPostRead(emptyQs, readQ, str("x")).isEmpty());
    }

    @Test
    public void testEmptyQProcList_postWrite() {
        assertTrue(processPostWrite(emptyQs, readQ, str("x")).isEmpty());
    }

    @Test
    public void testEmptyQProcList_qlessWrite() {
        assertTrue(processQlessWrite(emptyQs, readQ, str("x")).isEmpty());
    }

    // ── URI with no query params ──

    @Test
    public void testNoQueryParams_preRead() {
        final Lst qs = lst(readQProc("read", vid -> str("got")));
        assertTrue(processPreRead(qs, noQ).isEmpty());
    }

    @Test
    public void testNoQueryParams_preWrite() {
        final Lst qs = lst(writeQProc("write", (vid, obj) -> str("got")));
        assertTrue(processPreWrite(qs, noQ, str("x")).isEmpty());
    }

    @Test
    public void testNoQueryParams_postRead() {
        final Lst qs = lst(readQProc("read", null, (vid, obj) -> str("got")));
        assertTrue(processPostRead(qs, noQ, str("x")).isEmpty());
    }

    // ── Matching QProc: preRead / preWrite ──

    @Test
    public void testMatchingQProc_preRead() {
        final Lst qs = lst(readQProc("read", vid -> str("cached")));
        final Optional<Obj> result = processPreRead(qs, readQ);
        assertTrue(result.isPresent());
        assertEquals(str("cached"), result.get());
    }

    @Test
    public void testMatchingQProc_preWrite() {
        final Lst qs = lst(writeQProc("write", (vid, obj) -> str("intercepted")));
        final Optional<Obj> result = processPreWrite(qs, writeQ, str("original"));
        assertTrue(result.isPresent());
        assertEquals(str("intercepted"), result.get());
    }

    // ── Post-read transformation ──

    @Test
    public void testPostReadTransform() {
        final Lst qs = lst(readQProc("read", null, (vid, obj) -> jnt(99)));
        final Optional<Obj> result = processPostRead(qs, readQ, str("original"));
        assertTrue(result.isPresent());
        assertEquals(jnt(99), result.get());
    }

    // ── QlessWrite fires regardless of query params ──

    @Test
    public void testQlessWriteIgnoresQueryParams() {
        final Lst qs = lst(qlessQProc("write", (vid, obj) -> str("qless-result")));
        final Optional<Obj> result = processQlessWrite(qs, noQ, str("x"));
        assertTrue(result.isPresent());
        assertEquals(str("qless-result"), result.get());
    }

    @Test
    public void testQlessWriteSkipsQProcWithoutHandler() {
        final Lst qs = lst(writeQProc("write", (vid, obj) -> str("pre-only")));
        final Optional<Obj> result = processQlessWrite(qs, readQ, str("x"));
        assertTrue(result.isEmpty());
    }

    // ── Multiple matching QProcs accumulate ──

    @Test
    public void testMultipleMatchingQProcs_preRead() {
        final Lst qs = lst(
                readQProc("read", vid -> str("first")),
                readQProc("read", vid -> str("second"))
        );
        final Optional<Obj> result = processPreRead(qs, readQ);
        assertTrue(result.isPresent());
        Obj combined = str("first").append(str("second"));
        assertEquals(combined, result.get());
    }

    // ── Helpers ──

    private static QProc readQProc(final String pattern,
                                   final java.util.function.Function<fURI, Obj> preRead) {
        return QProc.Helper.build(REC_TID, f(pattern)).preRead(preRead).create();
    }

    private static QProc readQProc(final String pattern,
                                   final java.util.function.Function<fURI, Obj> preRead,
                                   final java.util.function.BiFunction<fURI, Obj, Obj> postRead) {
        return QProc.Helper.build(REC_TID, f(pattern)).preRead(preRead).postRead(postRead).create();
    }

    private static QProc writeQProc(final String pattern,
                                    final java.util.function.BiFunction<fURI, Obj, Obj> preWrite) {
        return QProc.Helper.build(REC_TID, f(pattern)).preWrite(preWrite).create();
    }

    private static QProc qlessQProc(final String pattern,
                                    final java.util.function.BiFunction<fURI, Obj, Obj> qlessWrite) {
        return QProc.Helper.build(REC_TID, f(pattern)).qlessWrite(qlessWrite).create();
    }
}
