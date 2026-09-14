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

package studio.phaseshift.metatron.isa.math;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractInstSetTest;
import studio.phaseshift.metatron.isa.m.math.mathInstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.type.impl.MType;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
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
        Router.writeToSpace(PROBE, noobj());
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
        Router.writeToSpace(probeType.vid(), probeType);
        final Type after = MType.T(MATH_DATETIME_TID);
        assertEquals(before, after,
                "datetime resolution must be stable across an unrelated registry write");
        assertTrue(after.hasPredicate(),
                "post-write resolution must retain the registered predicate: " + after);
    }
}
