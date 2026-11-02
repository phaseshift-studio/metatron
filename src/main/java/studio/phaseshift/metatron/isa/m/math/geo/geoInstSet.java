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
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.util.MTronException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_ISA_TID;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_METER_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.m.web.webInstSet.WEB_JSON_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * The geo instruction set at {@code /m/math/geo}: the GeoJSON (RFC 7946) data model as mtron types, the common spatial
 * algorithms as instructions, and the encodings that turn a geometry into text and back.
 *
 * <p>A sibling of {@code /m/math/cat} under {@link studio.phaseshift.metatron.isa.m.math.mathInstSet}. The names carry
 * no {@code geo_} prefix &mdash; the instruction set's NAMESPACE is the prefix &mdash; so, imported as
 * {@code import(/m/math/geo,geo)}, the types read {@code geo:point::T}, {@code geo:polygon::T}, &hellip; and the instructions
 * read {@code geo:distance()}, {@code geo:inside()}, &hellip; .
 *
 * <h2>Types</h2>
 *
 * <p>There is no "plural" type: "one or more positions" is the COEFFICIENT {@code position{+}}, spelled
 * {@code lst[position{+}]} where a JSON array needs a list. The kinds declare their payload inline.
 *
 * <p>The eight geometry kinds of RFC 7946 &sect;3.1 (Point, MultiPoint, LineString, MultiLineString, Polygon, MultiPolygon,
 * GeometryCollection, and the Position they are built from),
 * which the RFC's &sect;3.2/&sect;3.3 add and without which no real document can be read. Every
 * kind is present because every kind is typical: the multi- forms are not exotica &mdash; a parcel layer, a lake layer and a
 * boundary layer are routinely MultiPolygon. What the RFC's &sect;3.1 lists and this model does NOT type:
 *
 * <ul>
 *   <li><b>&sect;3.1.9 Antimeridian Cutting</b> &mdash; a rule about how a producer writes a ring that crosses \(\pm 180^\circ\)
 *       (and how a renderer splits it), not a kind. The model reads a ring as its coordinates spell it; a
 *       {@code geo:cut()} would be an instruction, and is not implemented.</li>
 *   <li><b>&sect;3.1.10 Uncertainty and Precision</b> &mdash; there is nothing to declare: a position is a double. Rounding is
 *       an instruction's business, never a refinement (a rounded position is still a position).</li>
 * </ul>
 *
 * <p><b>The kind is the tid</b>: a geo rec carries no {@code type} key. The wire's discriminator is derived from the tid
 * on the way out and consumed on the way in, so the value's type is the only statement of its kind.
 *
 * <h2>Instructions, and why they exist at all</h2>
 *
 * <p>A space with native spatial support &mdash; PostGIS behind {@code /m/db/tble}, MongoDB's {@code $geoWithin} behind
 * {@code /m/db/dcmnt}, a graph database with a geo predicate &mdash; should never run these: its planner turns
 * {@code from(<path>).geo:inside(<polygon>)} into the native operator and the payload never enters the VM. That is the
 * rewrite's job, and {@link geoRewrite} is the door those spaces register their native operators through.
 *
 * <p>These VM implementations are for every space that has NO spatial support: a {@code memSpace}, a filesystem read, a
 * plain {@code lst} of features, a {@code rec} that arrived over a wire. The algorithms are honest about their frames
 * of reference &mdash; see {@link GeoUtil} &mdash; because a distance computed on the wrong sphere is worse than no distance.
 *
 * <h2>The encodings</h2>
 *
 * <p>Three text forms map onto the rec model, and each is an {@code as} instruction &mdash; the same door the web
 * instruction set uses for its document types (<em>a document is reified by its content type</em>):
 *
 * <ul>
 *   <li><b>in</b>: {@code "<text>".as(geo:point::T)} accepts GeoJSON text ({@code {...}}) and Well-Known Text
 *       ({@code POINT (...)}), the two dialects a geo payload arrives in;</li>
 *   <li><b>out</b>: {@code x.as(json::T)} is the GeoJSON document, because GeoJSON <em>is</em> a JSON document and
 *       {@code json::T} is how this system names one (the web set's {@code html::T}/{@code xml::T}/{@code markdown::T}
 *       family, of which this is one more row);</li>
 *   <li><b>out, the other dialect</b>: {@code x.geo:wkt()} answers Well-Known Text, the form every spatial database's
 *       {@code ST_GeomFromText} takes. There is no {@code wkt::T} document type to cast onto, so WKT is an instruction
 *       rather than a cast.</li>
 * </ul>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@InstSet.JREService(vid = "/m/math/geo")
public class geoInstSet extends AbstractInstSet {

    public static final fURI GEO_ISA_TID = MATH_ISA_TID.extend("geo");
    public static final fURI GEO_INST_TID = GEO_ISA_TID.extend("inst");
    public static final fURI GEO_REWRITE_TID = GEO_INST_TID.extend("rewrite");

    /// //////////////////////////////////////////////////////////////
    // the shapes — GeoJSON's `type`/`coordinates` nesting is JSON's, not the model's: a point IS a position,
    // a line IS an ordered list of points, and every Multi* form is a coefficient the serializer reads off

    public static final fURI GEOMETRY_TID = GEO_ISA_TID.extend("geometry");
    public static final fURI POINT_TID = GEO_ISA_TID.extend("point");
    public static final fURI LINE_TID = GEO_ISA_TID.extend("line");
    public static final fURI POLYGON_TID = GEO_ISA_TID.extend("polygon");

    /// //////////////////////////////////////////////////////////////
    // the envelopes

    public static final fURI BBOX_TID = GEO_ISA_TID.extend("bbox");
    public static final fURI BBOX2D_TID = GEO_ISA_TID.extend("bbox2d");
    public static final fURI BBOX3D_TID = GEO_ISA_TID.extend("bbox3d");

    /// //////////////////////////////////////////////////////////////
    // the instructions

    public static final fURI DISTANCE_INST_TID = GEO_INST_TID.extend("distance");
    public static final fURI LENGTH_INST_TID = GEO_INST_TID.extend("length");
    public static final fURI AREA_INST_TID = GEO_INST_TID.extend("area");
    public static final fURI INSIDE_INST_TID = GEO_INST_TID.extend("inside");
    public static final fURI COORDINATES_INST_TID = GEO_INST_TID.extend("coordinates");
    public static final fURI WKT_INST_TID = GEO_INST_TID.extend("wkt");

    /// //////////////////////////////////////////////////////////////
    // the registered types (assigned by geoTypes.registered, read by the instructions below)

    public static Type GEOMETRY_TYPE;
    public static Type POINT_TYPE;
    public static Type LINE_TYPE;
    public static Type POLYGON_TYPE;
    public static Type BBOX_TYPE;
    public static Type BBOX2D_TYPE;
    public static Type BBOX3D_TYPE;

    public geoInstSet() {
        super(new LinkedHashMap<>(Map.<Obj, Obj>of(uri(PATTERN), uri(GEO_ISA_TID.extend(ALL)), uri(QPROC), lst())), INSTSET_TID, GEO_ISA_TID);
    }

    @Override
    public void setup() {
        this.selfTID(GEO_ISA_TID);
        // the types must exist before the instructions that name them are built, so register them off the same call
        this.jvm().putAll(mutableMap(
                uri(PATTERN), uri(GEO_ISA_TID.extend(ALL)),
                uri(TYPE), geoTypes.registered(this),
                uri(INST), lst(this.instructions()),
                uri(REWRITE), lst()));
        // register the family's process laws before anything resolves a morphism through the router
        geoInstSetLawTable.INSTANCE.getClass();
        docWrap(this, "the GeoJSON (RFC 7946) data model: geometry types, geodesic and planar algorithms, and the GeoJSON/WKT encodings",
                "import(/m/math/geo,geo)   [-- types read geo:point, geo:polygon, ...; insts read geo:distance(), geo:inside(), ... --]");
        super.setup();
    }

    /// ////////////////////////////////////////////////////////////////////////////////////////////
    // the algorithms
    private List<Obj> instructions() {
        final List<Obj> insts = new ArrayList<>();
        insts.add(docWrap(instC(DISTANCE_INST_TID.dom(GEOMETRY_TID).rng(MATH_METER_TID), lst(GEOMETRY_TYPE),
                        (lhs, inst) -> real(GeoUtil.distance(lhs, inst.arg(0)), MATH_METER_TID, null)),
                "the lhs geometry",
                "the distance to the arg geometry as a \\(\\mathrm{meter}::T\\)",
                Map.of(GEOMETRY_TYPE, "the geometry to measure to"),
                "the geodesic distance \\(d(a,b)\\), and \\(0\\) when the geometries intersect — a point inside a polygon is at distance zero, not at the distance to its nearest vertex",
                "geo:point::[-123.13,44.47].geo:distance(geo:point::[-122.68,45.52])   [-- meter::121998.59… (the Columbia gorge, about 122 km) --]"));

        insts.add(docWrap(instC(LENGTH_INST_TID.dom(GEOMETRY_TID).rng(MATH_METER_TID), lst(),
                        (lhs, inst) -> real(GeoUtil.length(lhs), MATH_METER_TID, null)),
                "the lhs geometry",
                "its measured length as a \\(\\mathrm{meter}::T\\) — every polyline and every polygon boundary, summed geodesically",
                Map.of(),
                "the \\(1\\)-dimensional measure: the geodesic distance along each part's consecutive points; a point has length zero",
                "geo:line::[[-123.13,44.47],[-121.18,45.61]].geo:length()   [-- meter::198845.05… (Portland to The Dalles) --]"));

        insts.add(docWrap(instC(AREA_INST_TID.dom(GEOMETRY_TID).rng(REAL_TID), lst(),
                        (lhs, inst) -> real(GeoUtil.area(lhs))),
                "the lhs geometry",
                "its area in square meters, as a plain \\(\\mathrm{real}::T\\) (\\(\\mathrm{math}\\) has no squared-length unit type)",
                Map.of(),
                "the \\(2\\)-dimensional measure of the polygonal part: shells positive, holes negative, by the planar shoelace formula scaled at the polygon's mean latitude — tight for parcels and districts, wrong for continents",
                "geo:polygon::[[[-123.2,44.4],[-122.9,44.4],[-122.9,44.6],[-123.2,44.6],[-123.2,44.4]]].geo:area()   [-- about 5.3e8 --]"));

        insts.add(docWrap(instC(COORDINATES_INST_TID.dom(GEOMETRY_TID).rng(LST_TID), lst(),
                        (lhs, inst) -> lst(GeoUtil.positions(lhs).stream().map(GeoUtil::point).toList())),
                "the lhs geometry",
                "every position it stores, in document order, as stamped \\(\\mathrm{geo{:}point}::T\\) values",
                Map.of(),
                "the flattening: whatever the nesting depth, the points a geometry is ultimately made of",
                "geo:line::[[-123.13,44.47],[-121.18,45.61]].geo:coordinates()   [-- [geo:point::[-123.13,44.47],geo:point::[-121.18,45.61]] --]"));

        insts.add(docWrap(instC(WKT_INST_TID.dom(GEOMETRY_TID).rng(STR_TID), lst(),
                        (lhs, inst) -> str(GeoUtil.wkt(lhs))),
                "the lhs geometry",
                "its Well-Known Text",
                Map.of(),
                "the other string encoding — the one every spatial database's \\(\\mathrm{ST\\_GeomFromText}\\) takes. There is no \\(\\mathrm{wkt}::T\\) document type to cast onto, so this is an instruction rather than an \\(\\mathrm{as}\\)",
                "geo:point::[-123.13,44.47].geo:wkt()   [-- 'POINT (-123.13 44.47)' --]"));

        // ── the one predicate: `has` answers the object itself when it holds ─────────────────────────────────────
        insts.add(docWrap(instC(HAS_INST_TID.dom(GEOMETRY_TID).rng(GEOMETRY_TID.maybe()), lst(ALL_TYPE),
                        (lhs, inst) -> GeoUtil.within(inst.arg(0), lhs) ? lhs : noobj()),
                "the lhs geometry (the container)",
                "the lhs itself when it holds the arg, otherwise \\(\\mathrm{noobj}\\) — a filter, not a bool",
                Map.of(ALL_TYPE, "what to look for: a geometry (inside the lhs), or a \\(\\mathrm{geo{:}bbox2d}::T\\) envelope"),
                "the containment predicate as a filter: \\(a \\sqsupset b\\) when every position of \\(b\\) lies inside \\(a\\). Because it answers the object rather than a bool, a fibered rec lifts it — \\(*marko.has(region)\\) is marko, or a miss",
                "geo:polygon::[[[-123.2,44.4],[-122.9,44.4],[-122.9,44.6],[-123.2,44.6],[-123.2,44.4]]].geo:has(geo:point::[-123.05,44.5])   [-- the polygon --]",
                "geo:point::[-123.05,44.5].geo:has(geo:bbox2d::[-123.2,44.4,-122.9,44.6])   [-- noobj: a point holds nothing --]"));
        // a box is a container too: BOTH envelopes answer `has` against a geometry
        for (final fURI box : java.util.List.of(BBOX2D_TID, BBOX3D_TID))
            insts.add(docWrap(instC(HAS_INST_TID.dom(box).rng(box.maybe()), lst(ALL_TYPE),
                            (lhs, inst) -> GeoUtil.within(inst.arg(0), lhs) ? lhs : noobj()),
                    "the lhs envelope",
                    "the envelope itself when the arg lies inside it, otherwise \\(\\mathrm{noobj}\\)",
                    Map.of(ALL_TYPE, "the geometry to look for"),
                    "the box form of {@code has}: the query a spatial index answers from the envelope alone",
                    "geo:bbox2d::[-123.2,44.4,-122.9,44.6].geo:has(geo:point::[-123.05,44.5])   [-- the box --]"));

        // the mirror: `inside` answers the CONTAINED object when it lies in the arg. The two rows are not
        // redundant — `has` preserves the container (`region.has(point)` is the region), `inside` preserves the
        // contained (`*marko.inside(region)` is marko), and that is what a fibered record needs.
        insts.add(docWrap(instC(INSIDE_INST_TID.dom(GEOMETRY_TID).rng(GEOMETRY_TID.maybe()), lst(ALL_TYPE),
                        (lhs, inst) -> GeoUtil.within(lhs, inst.arg(0)) ? lhs : noobj()),
                "the lhs geometry (the contained thing)",
                "the lhs itself when it lies in the arg, otherwise \\(\\mathrm{noobj}\\)",
                Map.of(ALL_TYPE, "the container: a geometry, or a \\(\\mathrm{geo{:}bbox2d}::T\\) envelope"),
                "the mirror of {@code has}: the same containment predicate, answering the CONTAINED object. \\(a.inside(b) \\iff b.has(a)\\), but the two answer different subjects, and only this one returns the wrapper of a fibered rec",
                "geo:point::[-123.05,44.5].geo:inside(geo:bbox2d::[-123.2,44.4,-122.9,44.6])   [-- the point --]"));
        /**
         * The {@code as} instructions: a GeoJSON or WKT string onto any geo type, a plain rec onto any geo type (the
         * normalization door — an integer coordinate becomes a real), a bare coordinate list onto its coordinate type, and
         * a geo value onto a {@code json::T} document — because GeoJSON IS a json document, and {@code as(json::T)} is how
         * every other document type in this system is written (the web instruction set's {@code html::T}, {@code xml::T},
         * {@code markdown::T} family).
         */

        // the list-shaped coordinate types are cast from a lst, not a rec: a payload with no envelope about it
        final Map<fURI, Type> coordinates = new LinkedHashMap<>();
        coordinates.put(POINT_TID, POINT_TYPE);
        coordinates.put(LINE_TID, LINE_TYPE);
        coordinates.put(POLYGON_TID, POLYGON_TYPE);
        coordinates.put(BBOX2D_TID, BBOX2D_TYPE);
        coordinates.put(BBOX3D_TID, BBOX3D_TYPE);
        /////////////////////////////////////////////////////////////////
        coordinates.forEach((tid, type) -> insts.add(docWrap(
                instC(AS_INST_TID.dom(LST_TID).rng(tid), lst(ALL_TYPE),
                        (lhs, inst) -> cast(lhs, inst.arg(0).vidOrTid())),
                "a list of numbers", "the coordinate value retyped onto " + tid.name(), Map.of(type, "the target type"),
                "normalize an integer-valued coordinate list onto " + tid.name(),
                "[-123,44].as(%s::T)".formatted(tid.name()))));
        return insts;
    }

    /**
     * The type each cast targets, keyed by tid. Built INSIDE setup (the type fields are assigned by
     * {@link geoTypes#registered}), never in a static initializer — a static reference would resolve the types before
     * the machine that holds them exists.
     */
    /**
     * The kinds that answer a GeoJSON document: every geometry kind, plus the two containers (a document is as often
     * a feature or a collection as it is a bare geometry).
     */
    /*private static Map<fURI, Type> castableOut() {
        final Map<fURI, Type> types = new LinkedHashMap<>(castable());
        types.remove(BBOX2D_TID);
        types.remove(BBOX3D_TID);
        return types;
    }

    private static Map<fURI, Type> castable() {
        final Map<fURI, Type> types = new LinkedHashMap<>();
        types.put(POINT_TID, POINT_TYPE);
        types.put(LINE_TID, LINE_TYPE);
        types.put(POLYGON_TID, POLYGON_TYPE);
        types.put(BBOX2D_TID, BBOX2D_TYPE);
        types.put(BBOX3D_TID, BBOX3D_TYPE);
        return types;
    }*/

    /**
     * The one cast: through the wire form, then a kind check. A mismatch is a {@code fail}, never a silent
     * {@code noobj} — a cast the caller asked for and did not get must say so, or a typo in a type name reads as
     * "no data".
     */
    static Obj cast(final Obj lhs, final fURI tid) {
        // a Type IS a Rec; a call/code/inst/rel is not a geometry payload either. Such a value is not this row's
        // business, so the cast is inapplicable rather than failed.
        if (lhs.isType() || lhs.isCode() || lhs.isInst() || lhs.isRel())
            return noobj();
        final Obj value = ObjGeoJSONSerializer.single().readAs(lhs, tid);
        return value.isNoObj() ? fail(MTronException.of("%s is not a %s", lhs, tid.name())) : value;
    }

    /**
     * The GeoJSON document of a geo value, as a {@code json::T} string — the {@code as(json::T)} target. Declared on
     * each type (see {@link geoTypes}) with the exact {@code json::T} argument, so it answers that cast and no other.
     */
    static Obj json(final Obj lhs) {
        // a generic cast target (`as(A::T)`) accepts any rng, so this row can be reached with a value that is not a
        // geo object at all: that is a fail, never a silent document (a cast the caller asked for and did not get
        // has to say so)
        if (null == GeoUtil.tagOf(lhs))
            return fail(MTronException.of("%s is not a geo value", lhs));
        return str(ObjGeoJSONSerializer.single().text(lhs), WEB_JSON_TID, null);
    }

    /**
     * A point rec built from a position — the shape {@code geo:centroid()} answers.
     */
    private static Obj point(final GeoUtil.Pos position) {
        return GeoUtil.point(position);
    }
}
