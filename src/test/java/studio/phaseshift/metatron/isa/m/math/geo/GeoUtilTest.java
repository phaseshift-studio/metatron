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

package studio.phaseshift.metatron.isa.m.math.geo;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.isa.AbstractInstSetTest;
import studio.phaseshift.metatron.isa.m.type.Obj;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.isa.m.math.geo.GeoUtil.Pos;
import static studio.phaseshift.metatron.isa.m.math.geo.geoInstSet.BBOX2D_TID;
import static studio.phaseshift.metatron.isa.m.math.geo.geoInstSet.POLYGON_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;

/**
 * The value layer: what a shape is, what it measures, and what it answers.
 *
 * <p>Every value here is BARE — a point is a list of reals, a line is a list of points, a polygon is a list of lines.
 * There is no {@code coordinates} key to construct and none to read.
 */
public class GeoUtilTest extends AbstractInstSetTest {

    private static final Pos PORTLAND = new Pos(-123.13d, 44.47d);
    private static final Pos THE_DALLES = new Pos(-121.18d, 45.61d);

    public GeoUtilTest() {
        super(geoInstSet::new);
    }

    @Test
    public void theShapesAreTheDeclarations() {
        assertTrue(GeoUtil.isPoint(lst(real(1.0d), real(2.0d))));
        assertTrue(GeoUtil.isPoint(lst(real(1.0d), real(2.0d), real(3.0d))));
        assertFalse(GeoUtil.isPoint(lst(real(1.0d))));
        // mtron's int is not a real: the reader normalizes, the validator does not coerce
        assertFalse(GeoUtil.isPoint(lst(real(1.0d), studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt(2))));
        assertTrue(GeoUtil.isLine(lst(lst(real(1.0d), real(2.0d)), lst(real(3.0d), real(4.0d)))));
        assertFalse(GeoUtil.isLine(lst(lst(real(1.0d)))));
        assertTrue(GeoUtil.isPolygon(lst(lst(lst(real(1.0d), real(2.0d)), lst(real(3.0d), real(4.0d))))));
        assertFalse(GeoUtil.isPolygon(lst(lst(real(1.0d), real(2.0d)))));
        assertTrue(GeoUtil.isBbox2d(lst(real(1.0d), real(2.0d), real(3.0d), real(4.0d))));
        assertFalse(GeoUtil.isBbox2d(lst(real(1.0d), real(2.0d), real(3.0d))));
        assertTrue(GeoUtil.isBbox3d(lst(real(1.0d), real(2.0d), real(3.0d), real(4.0d), real(5.0d), real(6.0d))));
    }

    @Test
    public void aGeometryIsItsPositions() {
        assertEquals(List.of(PORTLAND), GeoUtil.positions(GeoUtil.point(PORTLAND)));
        assertEquals(List.of(PORTLAND, THE_DALLES), GeoUtil.positions(GeoUtil.line(List.of(PORTLAND, THE_DALLES))));
        // the payload of a rec is its geo: fiber — what makes a rec a feature without a feature type existing
        final Obj line = GeoUtil.line(List.of(PORTLAND, THE_DALLES));
        final Obj rec = studio.phaseshift.metatron.isa.m.type.impl.MRec.rec(
                studio.phaseshift.metatron.isa.m.type.impl.MUri.uri("geo:geometry"), line,
                studio.phaseshift.metatron.isa.m.type.impl.MUri.uri("name"),
                studio.phaseshift.metatron.isa.m.type.impl.MStr.str("Dinagat"));
        assertEquals(line, GeoUtil.payload(rec));
        assertEquals(1, GeoUtil.geometryFibers(rec).size());
    }

    @Test
    public void distanceIsGeodesicAndZeroWhenOverlapping() {
        final Obj a = GeoUtil.point(PORTLAND), b = GeoUtil.point(THE_DALLES);
        assertEquals(GeoUtil.haversine(PORTLAND, THE_DALLES), GeoUtil.distance(a, b), 1e-6);
        assertEquals(0.0d, GeoUtil.distance(a, a), 1e-9);
    }

    @Test
    public void lengthAndAreaAndCentroid() {
        final Obj line = GeoUtil.line(List.of(PORTLAND, THE_DALLES));
        assertEquals(GeoUtil.haversine(PORTLAND, THE_DALLES), GeoUtil.length(line), 1e-6);
        assertEquals(0.0d, GeoUtil.area(line), 1e-9);
        final Obj box = box();
        assertTrue(GeoUtil.area(box) > 0.0d);
        final Pos centroid = GeoUtil.centroid(box);
        assertEquals(-123.05d, centroid.lon(), 1e-6);
        assertEquals(44.5d, centroid.lat(), 1e-6);
    }

    @Test
    public void predicates() {
        final Obj box = box();
        assertTrue(GeoUtil.contains(box, GeoUtil.point(new Pos(-123.05d, 44.5d))));
        assertFalse(GeoUtil.contains(box, GeoUtil.point(new Pos(-120.0d, 44.5d))));
        assertTrue(GeoUtil.within(GeoUtil.point(new Pos(-123.05d, 44.5d)), box));
        assertTrue(GeoUtil.within(GeoUtil.point(new Pos(-123.05d, 44.5d)),
                lst(real(-123.2d), real(44.4d), real(-122.9d), real(44.6d))));
        assertTrue(GeoUtil.intersects(box, GeoUtil.point(new Pos(-123.05d, 44.5d))));
    }

    @Test
    public void theEnvelopeIsTheBoxAroundEveryPosition() {
        final Obj line = GeoUtil.line(List.of(PORTLAND, THE_DALLES));
        assertEquals(lst(real(-123.13d), real(44.47d), real(-121.18d), real(45.61d)).selfTID(BBOX2D_TID),
                GeoUtil.bbox(line));
    }

    @Test
    public void wktRoundTrips() {
        final Obj point = GeoUtil.point(PORTLAND);
        assertEquals("POINT (-123.13 44.47)", GeoUtil.wkt(point));
        assertEquals(point, GeoUtil.parseWkt(GeoUtil.wkt(point)));
        final Obj line = GeoUtil.line(List.of(PORTLAND, THE_DALLES));
        assertEquals(line, GeoUtil.parseWkt(GeoUtil.wkt(line)));
        final Obj polygon = box();
        assertEquals(polygon, GeoUtil.parseWkt(GeoUtil.wkt(polygon)));
    }

    /** a 1/10° box around the Willamette valley: [-123.2, 44.4] to [-122.9, 44.6] */
    private static Obj box() {
        // a closed rectangle ring: the shell repeats its first position, as the RFC requires
        final Obj ring = GeoUtil.line(List.of(
                new Pos(-123.2d, 44.4d), new Pos(-122.9d, 44.4d),
                new Pos(-122.9d, 44.6d), new Pos(-123.2d, 44.6d), new Pos(-123.2d, 44.4d)));
        return lst(ring).selfTID(POLYGON_TID);
    }
}
