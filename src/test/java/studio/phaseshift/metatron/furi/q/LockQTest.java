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

package studio.phaseshift.metatron.furi.q;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.TestData;
import studio.phaseshift.metatron.isa.m.math.mathInstSet;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.util.CommonUtil;

import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.LOCKQ_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface LockQTest extends QProcTest {

    @BeforeAll
    static void loadInstSets() {
        InstSet.importInstSet(f("/m/math"));
    }

    @ParameterizedTest(name = "[{index}] String: {0}")
    @TestData(value = {
            "$$/xyz/#?lockq -> lock::[usr=>/usr/agent1]"
    })
    @CsvSource(value = {
            // a write into the locked region throws
            "$$/xyz/a -> 1   % <ERROR>",
            // a write outside the locked region succeeds
            "$$/abc -> 1     % 1",
    }, delimiter = '%')
    default void testLockQ(String writing, String expecting) {
        this.attachQ(LOCKQ_TID);
        if (expecting.trim().equals("<ERROR>")) {
            // a write into the locked region is blocked: the write yields a fail
            final Obj result = ObjmtronSerializer.parse(make(writing)).apply();
            assertTrue(result.isFail(), "a write into the locked region must yield a fail");
        } else {
            final Obj expected = ObjmtronSerializer.parse(make(expecting)).apply();
            final Obj actual = ObjmtronSerializer.parse(make(writing)).apply();
            assertEquals(expected, actual);
        }
    }

    @ParameterizedTest(name = "[{index}] String: {0}")
    @TestData(value = {
            "$$/xyz/#?lockq -> lock::[usr=>/usr/agent1,expire=>datetime::<//2000.01:01/00/00/00/000?tz=+0000>]"
    })
    @CsvSource(value = {
            // an expired lock never blocks
            "$$/xyz/a -> 1   % 1",
    }, delimiter = '%')
    default void testLockQExpired(String writing, String expecting) {
        this.attachQ(LOCKQ_TID);
        final Obj expected = ObjmtronSerializer.parse(make(expecting)).apply();
        final Obj actual = ObjmtronSerializer.parse(make(writing)).apply();
        assertEquals(expected, actual);
    }

    @Test
    default void testLockQExpiresAfterTtl() {
        this.attachQ(LOCKQ_TID);
        // lock the region with a ~1 second expiry — the future datetime is built on the Java
        // side (the mtron datetime arithmetic inst isn't resolving in the test suite's /m/math
        // load; it works in the console).  A literal datetime URI stores cleanly in the lock.
        final String expire = mathInstSet.buildDatetimeUri(ZonedDateTime.now().plusSeconds(1)).uriValue().toString();
        ObjmtronSerializer.parse(make("$$/xyz/abc?lockq -> lock::[usr=>/usr/agent1,expire=>datetime::<" + expire + ">]")).apply();
        // while the lock is live, a write into it is blocked
        final Obj blocked = ObjmtronSerializer.parse(make("$$/xyz/abc -> 1")).apply();
        assertTrue(blocked.isFail(), "a write into a live lock must yield a fail");
        // once the TTL elapses, the lock has expired — the write passes
        CommonUtil.sleepThread(2500);
        final Obj passed = ObjmtronSerializer.parse(make("$$/xyz/abc -> 1")).apply();
        assertEquals(ObjmtronSerializer.parse(make("1")).apply(), passed);
    }

    @Test
    @TestData(value = {
            "$$/xyz/#?lockq -> lock::[usr=>/usr/agent1]"
    })
    default void testLockQRelease() {
        this.attachQ(LOCKQ_TID);
        for (final String line : new String[]{
                "$$/xyz/#?lockq -> noobj   % noobj",
                "$$/xyz/a -> 1              % 1"}) {
            final Obj resultObj = ObjmtronSerializer.parse(make(line.split("%")[0])).apply();
            final Obj expectedObj = ObjmtronSerializer.parse(make(line.split("%")[1])).apply();
            assertEquals(expectedObj, resultObj);
        }
    }

    @Test
    @TestData(value = {
            "$$/xyz/#?lockq -> lock::[usr=>/usr/agent1]"
    })
    default void testLockQReadListsMatchingLocks() {
        this.attachQ(LOCKQ_TID);
        final Obj result = ObjmtronSerializer.parse(make("*$$/xyz/#?lockq")).apply();
        assertFalse(result.isNoObj(), "reading ?lockq must surface the held lock");
    }
}
