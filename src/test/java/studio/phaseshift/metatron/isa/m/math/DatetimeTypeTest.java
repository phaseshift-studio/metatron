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

package studio.phaseshift.metatron.isa.m.math;

import java.time.ZonedDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractInstSetTest;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Uri;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.type.impl.MType;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Machine;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.DATETIME_TYPE;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_DATETIME_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * regression for the datetime object-identity bug: the {@code datetime} name must
 * resolve to its <em>registered</em> type (predicate-bearing, a refinement of
 * {@code uri}) -- never to a synthesized name-only shape -- so that nominal
 * checks walk the parent chain as far as {@code uri} and the cast
 * ({@code datetime::<uri>}) predicate actually applies.
 */
public class DatetimeTypeTest extends AbstractInstSetTest {

    public DatetimeTypeTest() {
        super(mathInstSet::new);
    }

    private static final fURI PROBE = f("/m/datetimeProbe");

    @AfterEach
    public void teardown() {
        Machine.write(PROBE, noobj());
    }

    @Test
    public void testNameResolutionIsRegisteredType() {
        final Type resolved = MType.T(MATH_DATETIME_TID);
        assertTrue(resolved.hasPredicate(),
                "T(" + MATH_DATETIME_TID + ") resolved to a synthesized name-only type (no predicate); " +
                        "expected the registered datetime type: " + resolved);
        assertEquals(DATETIME_TYPE, resolved,
                "name resolution must return the registered datetime type object");
    }

    @Test
    public void testNominalChainReachesUri() {
        final Type resolved = MType.T(MATH_DATETIME_TID);
        assertTrue(resolved.testNominally(URI_TYPE),
                "registered datetime type must nominally test true against uri (parent chain must reach uri): " + resolved);
        assertTrue(DATETIME_TYPE.testNominally(URI_TYPE),
                "DATETIME_TYPE must nominally test true against uri");
        assertFalse(uri("a/b/c").testNominally(DATETIME_TYPE),
                "an arbitrary uri must not nominally test true against datetime");
    }

    @ParameterizedTest
    @CsvSource(value = {
            "<//2024.12:25/09/00/00/000?tz=-0500>.matches(datetime::T)   % true",
            "<http://example.com>.matches(datetime::T)                   % false"
    }, delimiter = '%')
    public void testCastPredicateApplies(final String expr, final boolean expected) {
        final Obj apply = ObjmtronSerializer.parse(expr).apply();
        assertEquals(expected, apply.apply().boolValue(),
                "matches " + expr + " should evaluate to " + expected + ", but applied to " + apply);
    }

    @Test
    public void testCastSurvivesApply() {
        final Obj parse = ObjmtronSerializer.parse("datetime::<//2024.12:25/09/00/00/000?tz=-0500>").apply();
        assertFalse(parse.isNoObj(), "a valid datetime cast must apply: " + parse);
        assertTrue(parse.testNominally(DATETIME_TYPE),
                "applied datetime cast must nominally test true against DATETIME_TYPE: " + parse);
        assertTrue(parse.testNominally(URI_TYPE),
                "applied datetime cast must nominally test true against uri: " + parse);
    }

    @Test
    public void testReboundResolutionKeepsIdentity() {
        final Type before = MType.T(MATH_DATETIME_TID);
        // an unrelated registry write bumps the type graph generation --
        // datetime must still resolve to the registered type afterwards
        final Obj probeType = ObjmtronSerializer.parse("int::T@" + PROBE).apply();
        Machine.write(probeType.vid(), probeType);
        final Type after = MType.T(MATH_DATETIME_TID);
        assertEquals(before, after,
                "datetime resolution must be stable across an unrelated registry write");
        assertTrue(after.hasPredicate(),
                "post-write resolution must retain the registered predicate: " + after);
    }

    /*
     * The datetime ENCODING, pinned because it puts meaning in places nobody would guess and nothing asserted it:
     *
     *     <//2026.08:09/14/30/00/000?tz=+0000>
     *          year.month  day   h  m  s  ms
     *          └─ host ──┘ port   └── path ──┘
     *
     * A datetime's value lives in the AUTHORITY — year.month in the HOST (dot included) and the day in the PORT —
     * with only hour/min/sec/millis as path segments. That is why datetimeToMillis reads host then port then path.
     * A future normalization of the authority (stripping the dot from 2026.08, or dropping the port) corrupts every
     * datetime SILENTLY, and the symptom appears somewhere unrelated — an expiry that reads as past, a lock that
     * stops guarding — rather than here. Hence the assertions below.
     */
    private static final ZonedDateTime ROUND_TRIP = ZonedDateTime.of(2026, 8, 9, 14, 30, 0, 0, ZoneOffset.UTC);

    @Test
    public void testDatetimeEncodingIsAuthorityBearing() {
        final fURI furi = mathInstSet.buildDatetimeUri(ROUND_TRIP).uriValue();
        LOG.debug("datetime %s encodes as %s", ROUND_TRIP, furi);
        assertEquals("2026.08", furi.host(), "year.month is encoded in the HOST, dot included");
        assertEquals(9, furi.port(), "the day is encoded in the PORT");
        assertTrue(furi.path().contains("14"), "the hour is a path segment");
        assertTrue(furi.path().contains("30"), "the minute is a path segment");
    }

    @Test
    public void testDatetimeRoundTripIsLossless() {
        final Uri dt = mathInstSet.buildDatetimeUri(ROUND_TRIP);
        assertEquals(ROUND_TRIP.toInstant().toEpochMilli(), mathInstSet.datetimeToMillis(dt),
                "a datetime must survive encode/decode: " + dt.uriValue());
    }

    @Test
    public void testDatetimeIsUnaffectedByResolve() {
        final fURI dt = mathInstSet.buildDatetimeUri(ROUND_TRIP).uriValue();
        assertEquals(dt, dt.resolve(), "resolve() must be the identity on a datetime: its value lives in the "
                + "authority, and its path segments (hour/minute/second/millis) contain no `.` or `..`");
        assertEquals(dt.hashCode(), dt.resolve().hashCode());
    }


    /*
     * The bug this catches, and it was not cosmetic: a datetime with a RELATIVE path on an authority-bearing uri
     * renders without the authority/path separator, gluing the day (port) to the hour — day 22 hour 21 became
     * `//2026.10:221/26/…`. The console and every serializer round trip a datetime through its string, and the
     * GLUED form reparses as a different instant. A lock whose expire was written correctly, one second in the
     * future, came back as an expiry in the past — so the lock never blocked, silently.
     */
    @Test
    public void testDatetimeSurvivesAStringRoundTrip() {
        final Uri dt = mathInstSet.buildDatetimeUri(ROUND_TRIP);
        final fURI reparsed = f(dt.uriValue().toString());
        LOG.debug("datetime %s round trips as %s", dt.uriValue(), reparsed);
        assertEquals(dt.uriValue(), reparsed, "a datetime must survive its own string form");
        assertEquals(ROUND_TRIP.toInstant().toEpochMilli(), mathInstSet.datetimeToMillis(uri(reparsed)),
                "...and still decode to the same instant after being reparsed");
    }

}
