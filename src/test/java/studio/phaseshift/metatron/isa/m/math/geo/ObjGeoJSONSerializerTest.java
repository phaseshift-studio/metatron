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
import static studio.phaseshift.metatron.isa.m.math.geo.geoInstSet.POLYGON_TID;

/**
 * The boundary: GeoJSON goes in, the values come out, and the wrapper does not survive the crossing.
 *
 * <p>A document's {@code type} is consumed (the tid says the kind), its {@code coordinates} become the value itself, a
 * {@code Multi*} becomes a bag, and a {@code Feature} becomes a rec with a {@code geo:} fiber.
 */
public class ObjGeoJSONSerializerTest extends AbstractInstSetTest {

    private static final Pos PORTLAND = new Pos(-123.13d, 44.47d);
    private static final Pos THE_DALLES = new Pos(-121.18d, 45.61d);

    public ObjGeoJSONSerializerTest() {
        super(geoInstSet::new);
    }

    @Test
    public void aPointIsItsPosition() {
        final Obj point = ObjGeoJSONSerializer.single().parse("{\"type\":\"Point\",\"coordinates\":[-123.13,44.47]}");
        assertEquals(GeoUtil.point(PORTLAND), point);
        // the wrapper comes back out, and only on the way out
        assertEquals("{\"type\":\"Point\",\"coordinates\":[-123.13,44.47]}", ObjGeoJSONSerializer.single().text(point));
    }

    @Test
    public void aLineIsAListOfPoints() {
        final String text = "{\"type\":\"LineString\",\"coordinates\":[[-123.13,44.47],[-121.18,45.61]]}";
        final Obj line = ObjGeoJSONSerializer.single().parse(text);
        assertEquals(GeoUtil.line(List.of(PORTLAND, THE_DALLES)), line);
        assertEquals(text, ObjGeoJSONSerializer.single().text(line));
    }

    @Test
    public void aPolygonIsAListOfLines() {
        final String text = "{\"type\":\"Polygon\",\"coordinates\":[[[-123.2,44.4],[-122.9,44.4],[-122.9,44.6],[-123.2,44.4]]]}";
        final Obj polygon = ObjGeoJSONSerializer.single().parse(text);
        assertEquals(POLYGON_TID, polygon.tid());
        assertEquals(text, ObjGeoJSONSerializer.single().text(polygon));
    }

    @Test
    public void aMultiPointIsABagOfPoints() {
        final String text = "{\"type\":\"MultiPoint\",\"coordinates\":[[-123.13,44.47],[-121.18,45.61]]}";
        final Obj bag = ObjGeoJSONSerializer.single().parse(text);
        assertFalse(bag.isNoObj());
        assertEquals(2, bag.stream().count());
    }

    @Test
    public void aFeatureIsARecWithAGeoFiber() {
        final String text = "{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[-123.13,44.47]},\"properties\":{\"name\":\"Dinagat Islands\"}}";
        final Obj feature = ObjGeoJSONSerializer.single().parse(text);
        assertTrue(feature.isRec());
        // the properties are IN the rec, and the geometry sits at a geo: fiber
        assertEquals(GeoUtil.point(PORTLAND), GeoUtil.payload(feature));
        assertTrue(feature.asRec().has(studio.phaseshift.metatron.isa.m.type.impl.MUri.uri("name")));
    }

    @Test
    public void aBagOfPointsIsAMultiPoint() {
        final Obj bag = studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs(
                java.util.List.of(GeoUtil.point(PORTLAND), GeoUtil.point(THE_DALLES)));
        assertEquals("{\"type\":\"MultiPoint\",\"coordinates\":[[-123.13,44.47],[-121.18,45.61]]}",
                ObjGeoJSONSerializer.single().text(bag));
        // and back: the document is a bag again, not a list (a bag has no order, a line does)
        final Obj read = ObjGeoJSONSerializer.single().parse(
                "{\"type\":\"MultiPoint\",\"coordinates\":[[-123.13,44.47],[-121.18,45.61]]}");
        assertEquals(2, read.stream().count());
        assertEquals(GeoUtil.point(PORTLAND), read.stream().toList().get(0));
    }

    @Test
    public void aRecWithAGeoFiberIsAFeature() {
        final Obj feature = studio.phaseshift.metatron.isa.m.type.impl.MRec.rec(
                studio.phaseshift.metatron.isa.m.type.impl.MUri.uri("geo:location"), GeoUtil.point(PORTLAND),
                studio.phaseshift.metatron.isa.m.type.impl.MUri.uri("name"),
                studio.phaseshift.metatron.isa.m.type.impl.MStr.str("Dinagat Islands"));
        final String text = ObjGeoJSONSerializer.single().text(feature);
        assertTrue(text.contains("\"type\":\"Feature\""));
        assertTrue(text.contains("\"geometry\""));
        assertTrue(text.contains("\"properties\""));
        assertTrue(text.contains("\"name\":\"Dinagat Islands\""));
        // the wire has no name for the geometry slot, so a read normalizes the fiber to `geo:geometry`; the DOCUMENT
        // round-trips, which is the contract that matters at a boundary
        assertEquals(text, ObjGeoJSONSerializer.single().text(ObjGeoJSONSerializer.single().parse(text)));
    }

    @Test
    public void wktIsTheOtherEncoding() {
        final Obj point = GeoUtil.point(PORTLAND);
        assertEquals("POINT (-123.13 44.47)", ObjGeoJSONSerializer.single().wkt(point));
        assertEquals(point, ObjGeoJSONSerializer.single().parse("POINT (-123.13 44.47)"));
    }
}
