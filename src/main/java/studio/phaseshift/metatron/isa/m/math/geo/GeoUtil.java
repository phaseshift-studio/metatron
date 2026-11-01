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

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.util.MTronException;

import java.util.ArrayList;
import java.util.List;

import static studio.phaseshift.metatron.isa.m.math.geo.geoInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;

/**
 * The value layer of the geo instruction set: shapes, coordinates, and the algorithms over them.
 *
 * <p><b>The value IS the geometry.</b> A point is a list of two or three reals, a line is a list of points, a polygon is
 * a list of lines, and a bbox is a list of four (or six) reals. Nothing here reads a {@code coordinates} key, because
 * the model has none: that key, and the {@code type} key beside it, exist only in {@link ObjGeoJSONSerializer}.
 *
 * <p>Distances are geodesic (haversine over the IUGG mean radius), areas are planar shoelace scaled by the mean
 * latitude, and every predicate is exact. Those are the honest labels on the approximations.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class GeoUtil {

    /** the IUGG mean earth radius, in meters */
    public static final double EARTH_RADIUS_METERS = 6371008.8d;

    /** one position: longitude, latitude, and an altitude that is absent (NaN) when the wire did not carry one */
    public record Pos(double lon, double lat, double alt) {
        public Pos(final double lon, final double lat) {
            this(lon, lat, Double.NaN);
        }

        public boolean hasAlt() {
            return !Double.isNaN(this.alt);
        }
    }

    private GeoUtil() {
    }

    /// ////////////////////////////////////////////////////////////////////////////////////////////
    // shapes — the declarations in geoTypes call these

    /** a point: two or more reals (two or three per the RFC, further elements allowed after the altitude) */
    public static boolean isPoint(final Obj obj) {
        return obj.isLst() && 2 <= obj.asLst().count() && obj.asLst().lstValue().stream().allMatch(Obj::isReal);
    }

    /** a line: one or more points, in order */
    public static boolean isLine(final Obj obj) {
        return obj.isLst() && 0 < obj.asLst().count() && obj.asLst().lstValue().stream().allMatch(GeoUtil::isPoint);
    }

    /** a polygon: one or more lines (a shell, then the holes) */
    public static boolean isPolygon(final Obj obj) {
        return obj.isLst() && 0 < obj.asLst().count() && obj.asLst().lstValue().stream().allMatch(GeoUtil::isLine);
    }

    public static boolean isBbox2d(final Obj obj) {
        return obj.isLst() && 4 == obj.asLst().count() && obj.asLst().lstValue().stream().allMatch(Obj::isReal);
    }

    public static boolean isBbox3d(final Obj obj) {
        return obj.isLst() && 6 == obj.asLst().count() && obj.asLst().lstValue().stream().allMatch(Obj::isReal);
    }

    /** the namespace a geometry fiber's key lives in: {@code geo:country} is a geometry at {@code country} */
    public static final String GEO_FIBER_PREFIX = "geo:";

    /**
     * THE GEOMETRY A VALUE CARRIES: the value itself. The one exception is a rec, whose geometry is the value at a
     * {@code geo:}-prefixed fiber — that is what makes a plain rec a GeoJSON Feature without a feature type existing.
     */
    public static Obj payload(final Obj obj) {
        if (!obj.isRec())
            return obj;
        for (final var entry : obj.asRec().jvm().entrySet())
            if (entry.getKey().isUri() && entry.getKey().uriValue().toString().startsWith(GEO_FIBER_PREFIX))
                return entry.getValue();
        return obj;
    }

    /** every {@code geo:}-prefixed fiber of a rec, in stored order — a feature's geometry (or geometries) */
    public static List<Obj> geometryFibers(final Obj rec) {
        final List<Obj> fibers = new ArrayList<>();
        if (!rec.isRec())
            return fibers;
        for (final var entry : rec.asRec().jvm().entrySet())
            if (entry.getKey().isUri() && entry.getKey().uriValue().toString().startsWith(GEO_FIBER_PREFIX))
                fibers.add(entry.getValue());
        return fibers;
    }

    /**
     * THE KIND, read from the value's own TYPE NAME. The kinds share a {@code lst} tid, so the name is the only thing
     * that distinguishes a line from a polygon — which is exactly what GeoJSON's {@code type} key is for.
     */
    public static String tagOf(final Obj obj) {
        if (null == obj || obj.isNoObj())
            return null;
        final Type type = obj.isType() ? obj.asType() : obj.type();
        final fURI name = type.vidOrTid();
        return switch (name.name()) {
            case "point" -> "Point";
            case "line" -> "LineString";
            case "polygon" -> "Polygon";
            case "bbox", "bbox2d", "bbox3d" -> BBOX_TAG;
            default -> null;
        };
    }

    /** the tag a bbox carries; a bbox is not a GeoJSON object, it is a field on one */
    public static final String BBOX_TAG = "bbox";
    /** the tag a feature carries; a feature is a rec with a geometry fiber, never a type */
    public static final String FEATURE_TAG = "Feature";

    /// ////////////////////////////////////////////////////////////////////////////////////////////
    // coordinates

    /** a position obj (a stamped point) from a value-layer position */
    public static Obj point(final Pos position) {
        final List<Obj> reals = new ArrayList<>(3);
        reals.add(real(position.lon()));
        reals.add(real(position.lat()));
        if (position.hasAlt())
            reals.add(real(position.alt()));
        return lst(reals).selfTID(POINT_TID);
    }

    /** a line obj (a stamped line) from value-layer positions */
    public static Obj line(final List<Pos> positions) {
        final List<Obj> points = new ArrayList<>();
        positions.forEach(p -> points.add(point(p)));
        return lst(points).selfTID(LINE_TID);
    }

    /** every position a geometry stores, in stored order (the payload is the value; the kind is the nesting depth) */
    public static List<Pos> positions(final Obj geometry) {
        final List<Pos> positions = new ArrayList<>();
        collectPositions(payload(geometry), positions);
        return positions;
    }

    private static void collectPositions(final Obj obj, final List<Pos> positions) {
        if (null == obj || obj.isNoObj() || !obj.isLst())
            return;
        if (isPoint(obj)) {
            positions.add(position(obj));
            return;
        }
        obj.asLst().lstValue().forEach(element -> collectPositions(element, positions));
    }

    /** a point value's position */
    public static Pos position(final Obj point) {
        final List<Obj> reals = point.asLst().lstValue();
        if (2 > reals.size())
            throw MTronException.of("%s is not a position", point);
        return 3 > reals.size()
                ? new Pos(reals.get(0).realValue(), reals.get(1).realValue())
                : new Pos(reals.get(0).realValue(), reals.get(1).realValue(), reals.get(2).realValue());
    }

    /** a nested list as its value-layer positions: {@code [[x,y],[x,y]]} -> two positions */
    public static List<Pos> toPositions(final Obj obj) {
        final List<Pos> positions = new ArrayList<>();
        if (null != obj && obj.isLst())
            obj.asLst().lstValue().forEach(element -> positions.add(position(element)));
        return positions;
    }

    /// ////////////////////////////////////////////////////////////////////////////////////////////
    // measurement

    /**
     * The distance between two geometries, in meters: zero when they intersect, else the shortest vertex-to-vertex
     * great-circle distance (the exact answer on the surface when they do not overlap).
     */
    public static double distance(final Obj a, final Obj b) {
        if (intersects(a, b))
            return 0.0d;
        final List<Pos> pa = positions(a), pb = positions(b);
        if (pa.isEmpty() || pb.isEmpty())
            throw MTronException.of("a geometry with no positions has no distance");
        double best = Double.MAX_VALUE;
        for (final Pos p : pa)
            for (final Pos q : pb)
                best = Math.min(best, haversine(p, q));
        return best;
    }

    /** the great-circle distance between two positions, in meters */
    public static double haversine(final Pos a, final Pos b) {
        final double lat1 = Math.toRadians(a.lat()), lat2 = Math.toRadians(b.lat());
        final double dLat = lat2 - lat1;
        final double dLon = Math.toRadians(b.lon() - a.lon());
        final double h = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1.0d, Math.sqrt(h)));
    }

    /** the geodesic length of a line or of every polygon boundary, in meters; zero for a point */
    public static double length(final Obj geometry) {
        final Obj payload = payload(geometry);
        double total = 0.0d;
        for (final List<Pos> run : runs(payload))
            total += runLength(run, isPolygon(payload));
        return total;
    }

    private static double runLength(final List<Pos> run, final boolean closed) {
        double total = 0.0d;
        for (int i = 1; i < run.size(); i++)
            total += haversine(run.get(i - 1), run.get(i));
        if (closed && 2 < run.size())
            total += haversine(run.get(run.size() - 1), run.get(0));
        return total;
    }

    /** every maximal run of positions: a line's one run; a polygon's shell and holes */
    private static List<List<Pos>> runs(final Obj payload) {
        final List<List<Pos>> runs = new ArrayList<>();
        if (isLine(payload)) {
            runs.add(toPositions(payload));
        } else if (isPolygon(payload)) {
            payload.asLst().lstValue().forEach(line -> runs.add(toPositions(line)));
        }
        return runs;
    }

    /**
     * The area of a polygon, in square meters: the planar shoelace formula scaled by {@code R²·cos(φ̄)} at the mean
     * latitude of every stored position. Exact for a rectangle whose sides run along parallels and meridians, an
     * approximation otherwise.
     */
    public static double area(final Obj geometry) {
        final Obj payload = payload(geometry);
        if (isPoint(payload) || isLine(payload))
            return 0.0d;
        if (!isPolygon(payload))
            throw MTronException.of("%s is not a polygon", geometry);
        final List<Pos> all = positions(payload);
        final double meanLat = Math.toRadians(all.stream().mapToDouble(Pos::lat).average().orElse(0.0d));
        final double scale = EARTH_RADIUS_METERS * EARTH_RADIUS_METERS * Math.cos(meanLat);
        double total = 0.0d;
        for (int ring = 0; ring < payload.asLst().count(); ring++) {
            final List<Pos> run = toPositions(payload.asLst().lstValue().get(ring));
            double sum = 0.0d;
            for (int i = 0; i < run.size(); i++) {
                final Pos p = run.get(i), q = run.get((i + 1) % run.size());
                sum += Math.toRadians(p.lon()) * Math.toRadians(q.lat()) -
                        Math.toRadians(q.lon()) * Math.toRadians(p.lat());
            }
            final double ringArea = Math.abs(sum / 2) * scale;
            // the shell counts positive, holes negative
            total += 0 == ring ? ringArea : -ringArea;
        }
        return total;
    }

    /** the vertex mean of a line, the area-weighted vertex mean of a polygon, the position of a point */
    public static Pos centroid(final Obj geometry) {
        final Obj payload = payload(geometry);
        if (isPoint(payload))
            return position(payload);
        final List<Pos> all = positions(payload);
        if (all.isEmpty())
            throw MTronException.of("%s has no positions", geometry);
        if (isLine(payload))
            return new Pos(all.stream().mapToDouble(Pos::lon).average().orElse(0.0d),
                    all.stream().mapToDouble(Pos::lat).average().orElse(0.0d));
        double lonSum = 0.0d, latSum = 0.0d, weight = 0.0d;
        for (int ring = 0; ring < payload.asLst().count(); ring++) {
            final List<Pos> run = toPositions(payload.asLst().lstValue().get(ring));
            final double area = Math.abs(ringArea(run));
            if (0.0d == area)
                continue;
            // the shoelace centroid of the ring: the area-weighted mean of its edges, not of its vertices
            double cx = 0.0d, cy = 0.0d;
            for (int i = 0; i < run.size(); i++) {
                final Pos p = run.get(i), q = run.get((i + 1) % run.size());
                final double cross = p.lon() * q.lat() - q.lon() * p.lat();
                cx += (p.lon() + q.lon()) * cross;
                cy += (p.lat() + q.lat()) * cross;
            }
            final double signed = ringArea(run);
            cx /= 6 * signed;
            cy /= 6 * signed;
            final double w = 0 == ring ? area : -area;
            lonSum += cx * w;
            latSum += cy * w;
            weight += w;
        }
        return 0.0d == weight ? new Pos(lonSum, latSum) : new Pos(lonSum / weight, latSum / weight);
    }

    private static double ringArea(final List<Pos> run) {
        double sum = 0.0d;
        for (int i = 0; i < run.size(); i++) {
            final Pos p = run.get(i), q = run.get((i + 1) % run.size());
            sum += p.lon() * q.lat() - q.lon() * p.lat();
        }
        return sum / 2;
    }

    /** the smallest axis-aligned box holding every position */
    public static Obj bbox(final Obj geometry) {
        final List<Pos> all = positions(geometry);
        if (all.isEmpty())
            throw MTronException.of("%s has no positions", geometry);
        return lst(real(all.stream().mapToDouble(Pos::lon).min().orElseThrow()),
                real(all.stream().mapToDouble(Pos::lat).min().orElseThrow()),
                real(all.stream().mapToDouble(Pos::lon).max().orElseThrow()),
                real(all.stream().mapToDouble(Pos::lat).max().orElseThrow())).selfTID(BBOX2D_TID);
    }

    /// ////////////////////////////////////////////////////////////////////////////////////////////
    // predicates

    /** every position of {@code a} inside {@code b} — a ring set, or an envelope */
    public static boolean within(final Obj a, final Obj b) {
        if (isBbox2d(b) || isBbox3d(b))
            return withinBox(a, b);
        return contains(b, a);
    }

    /** every position of {@code geometry} inside the closed box */
    public static boolean withinBox(final Obj geometry, final Obj box) {
        if (!box.isLst())
            return false;
        final List<Obj> edges = box.asLst().lstValue();
        final double west = edges.get(0).realValue(), south = edges.get(1).realValue();
        final double east = 4 == edges.size() ? edges.get(2).realValue() : edges.get(3).realValue();
        final double north = 4 == edges.size() ? edges.get(3).realValue() : edges.get(4).realValue();
        final List<Pos> positions = positions(geometry);
        if (positions.isEmpty())
            return false;
        for (final Pos position : positions)
            if (position.lon() < west || position.lon() > east || position.lat() < south || position.lat() > north)
                return false;
        return true;
    }

    /** {@code a} contains {@code b} */
    public static boolean contains(final Obj a, final Obj b) {
        final Obj pa = payload(a);
        if (isBbox2d(pa) || isBbox3d(pa))
            return withinBox(b, pa);
        final Obj pb = payload(b);
        if (isPoint(pb))
            return containsPoint(a, position(pb));
        return positions(pb).stream().allMatch(position -> containsPoint(a, position));
    }

    private static boolean containsPoint(final Obj geometry, final Pos probe) {
        final Obj payload = payload(geometry);
        if (isPoint(payload))
            return samePosition(position(payload), probe);
        if (isLine(payload))
            return toPositions(payload).stream().anyMatch(p -> samePosition(p, probe));
        return crossingCount(payload, probe) % 2 == 1;
    }

    private static boolean samePosition(final Pos a, final Pos b) {
        return Math.abs(a.lon() - b.lon()) < 1e-12 && Math.abs(a.lat() - b.lat()) < 1e-12;
    }

    /** a ray along the meridian, counting the ring crossings that lie east of the probe */
    private static int crossingCount(final Obj polygon, final Pos probe) {
        int crossings = 0;
        for (int ring = 0; ring < polygon.asLst().count(); ring++) {
            final List<Pos> run = toPositions(polygon.asLst().lstValue().get(ring));
            for (int i = 0; i < run.size(); i++) {
                final Pos p = run.get(i), q = run.get((i + 1) % run.size());
                if ((p.lat() > probe.lat()) != (q.lat() > probe.lat())) {
                    final double x = p.lon() + (probe.lat() - p.lat()) / (q.lat() - p.lat()) * (q.lon() - p.lon());
                    if (probe.lon() < x)
                        crossings++;
                }
            }
        }
        return crossings;
    }

    /** the boundaries touch or cross (a point inside counts) */
    public static boolean intersects(final Obj a, final Obj b) {
        final Obj pa = payload(a), pb = payload(b);
        if (isPoint(pa) && isPoint(pb))
            return samePosition(position(pa), position(pb));
        if (isPoint(pa))
            return containsPoint(b, position(pa));
        if (isPoint(pb))
            return containsPoint(a, position(pb));
        for (final Pos position : positions(pa))
            if (containsPoint(b, position))
                return true;
        for (final Pos position : positions(pb))
            if (containsPoint(a, position))
                return true;
        return false;
    }

    /// ////////////////////////////////////////////////////////////////////////////////////////////
    // WKT — an encoding, the same kind of thing GeoJSON is

    /** the WKT text of a geometry */
    public static String wkt(final Obj geometry) {
        final Obj payload = payload(geometry);
        final String tag = tagOf(geometry);
        if (null == tag)
            throw MTronException.of("%s is not a geometry", geometry);
        if ("Point".equals(tag))
            return "POINT " + wktPosition(position(payload));
        if ("LineString".equals(tag))
            return "LINESTRING " + wktPositions(toPositions(payload));
        if ("Polygon".equals(tag)) {
            final StringBuilder text = new StringBuilder("POLYGON (");
            for (int ring = 0; ring < payload.asLst().count(); ring++) {
                if (0 < ring)
                    text.append(", ");
                text.append(wktPositions(toPositions(payload.asLst().lstValue().get(ring))));
            }
            return text.append(")").toString();
        }
        throw MTronException.of("%s has no WKT form", geometry);
    }

    private static String wktPosition(final Pos position) {
        return "(" + position.lon() + " " + position.lat() + (position.hasAlt() ? " " + position.alt() : "") + ")";
    }

    private static String wktPositions(final List<Pos> positions) {
        final StringBuilder text = new StringBuilder("(");
        for (int i = 0; i < positions.size(); i++) {
            if (0 < i)
                text.append(", ");
            text.append(wktCoords(positions.get(i)));
        }
        return text.append(")").toString();
    }

    /** the bare coordinate pair a WKT ring lists: {@code x y} inside the parens its caller supplies */
    private static String wktCoords(final Pos position) {
        return position.lon() + " " + position.lat() + (position.hasAlt() ? " " + position.alt() : "");
    }

    /** read WKT, stamping the kind so the value is a point, a line or a polygon */
    public static Obj parseWkt(final String text) {
        final String trimmed = text.trim();
        final int space = trimmed.indexOf(' ');
        if (0 > space)
            throw MTronException.of("not WKT: %s", text);
        final String kind = trimmed.substring(0, space).toUpperCase();
        final String body = trimmed.substring(space + 1).trim();
        // strip the outer parents: what remains is one group (a point, a line) or several ring groups
        final String inner = body.startsWith("(") && body.endsWith(")")
                ? body.substring(1, body.length() - 1).trim() : body;
        return switch (kind) {
            case "POINT" -> point(positionsOf(inner).get(0));
            case "LINESTRING" -> line(positionsOf(inner));
            case "POLYGON" -> {
                final List<Obj> rings = new ArrayList<>();
                for (final String ring : inner.split("\\)\\s*,\\s*\\("))
                    rings.add(line(positionsOf(ring)));
                yield lst(rings).selfTID(POLYGON_TID);
            }
            default -> throw MTronException.of("unsupported WKT kind: %s", kind);
        };
    }

    private static List<Pos> positionsOf(final String group) {
        final List<Pos> positions = new ArrayList<>();
        for (final String token : group.replace("(", " ").replace(")", " ").split(",")) {
            final String[] numbers = token.trim().split("\\s+");
            if (2 > numbers.length || numbers[0].isBlank())
                continue;
            positions.add(3 < numbers.length || 2 == numbers.length
                    ? new Pos(Double.parseDouble(numbers[0]), Double.parseDouble(numbers[1]))
                    : new Pos(Double.parseDouble(numbers[0]), Double.parseDouble(numbers[1]), Double.parseDouble(numbers[2])));
        }
        return positions;
    }
}
