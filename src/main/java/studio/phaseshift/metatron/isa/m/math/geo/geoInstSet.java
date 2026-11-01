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
import static studio.phaseshift.metatron.isa.m.mInstSet.ALL_TYPE;
import static studio.phaseshift.metatron.isa.m.mInstSet.AS_INST_TID;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_ISA_TID;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_METER_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.m.web.webInstSet.WEB_JSON_TID;
import static studio.phaseshift.metatron.isa.m.web.webInstSet.WEB_JSON_TYPE;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * The geo instruction set at {@code /m/math/geo}: the GeoJSON (RFC 7946) data model as mtron types, the common spatial
 * algorithms as instructions, and the encodings that turn a geometry into text and back.
 *
 * <p>A sibling of {@code /m/math/cat} under {@link studio.phaseshift.metatron.isa.m.math.mathInstSet}. The names carry
 * no {@code geo_} prefix — the instruction set's NAMESPACE is the prefix — so, imported as
 * {@code import(/m/math/geo,geo)}, the types read {@code geo:point::T}, {@code geo:polygon::T}, … and the instructions
 * read {@code geo:distance()}, {@code geo:inside()}, … .
 *
 * <h2>Types</h2>
 *
 * <p>There is no "plural" type: "one or more positions" is the COEFFICIENT {@code position{+}}, spelled
 * {@code lst[position{+}]} where a JSON array needs a list. The kinds declare their payload inline.
 *
 * <p>The eight geometry kinds of RFC 7946 §3.1 (Point, MultiPoint, LineString, MultiLineString, Polygon, MultiPolygon,
 * GeometryCollection, and the Position they are built from), plus {@link #FEATURE_TID} and
 * {@link #FEATURECOLLECTION_TID}, which the RFC's §3.2/§3.3 add and without which no real document can be read. Every
 * kind is present because every kind is typical: the multi- forms are not exotica — a parcel layer, a lake layer and a
 * boundary layer are routinely MultiPolygon. What the RFC's §3.1 lists and this model does NOT type:
 *
 * <ul>
 *   <li><b>§3.1.9 Antimeridian Cutting</b> — a rule about how a producer writes a ring that crosses \(\pm 180^\circ\)
 *       (and how a renderer splits it), not a kind. The model reads a ring as its coordinates spell it; a
 *       {@code geo:cut()} would be an instruction, and is not implemented.</li>
 *   <li><b>§3.1.10 Uncertainty and Precision</b> — there is nothing to declare: a position is a double. Rounding is
 *       an instruction's business, never a refinement (a rounded position is still a position).</li>
 * </ul>
 *
 * <p><b>The kind is the tid</b>: a geo rec carries no {@code type} key. The wire's discriminator is derived from the tid
 * on the way out and consumed on the way in, so the value's type is the only statement of its kind.
 *
 * <h2>Instructions, and why they exist at all</h2>
 *
 * <p>A space with native spatial support — PostGIS behind {@code /m/db/tble}, MongoDB's {@code $geoWithin} behind
 * {@code /m/db/dcmnt}, a graph database with a geo predicate — should never run these: its planner turns
 * {@code from(<path>).geo:inside(<polygon>)} into the native operator and the payload never enters the VM. That is the
 * rewrite's job, and {@link geoRewrite} is the door those spaces register their native operators through.
 *
 * <p>These VM implementations are for every space that has NO spatial support: a {@code memSpace}, a filesystem read, a
 * plain {@code lst} of features, a {@code rec} that arrived over a wire. The algorithms are honest about their frames
 * of reference — see {@link GeoUtil} — because a distance computed on the wrong sphere is worse than no distance.
 *
 * <h2>The encodings</h2>
 *
 * <p>Three text forms map onto the rec model, and each is an {@code as} instruction — the same door the web
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
    public static final fURI CENTROID_INST_TID = GEO_INST_TID.extend("centroid");
    public static final fURI ENVELOPE_INST_TID = GEO_INST_TID.extend("envelope");
    public static final fURI CONTAINS_INST_TID = GEO_INST_TID.extend("contains");
    public static final fURI INSIDE_INST_TID = GEO_INST_TID.extend("inside");
    public static final fURI INTERSECTS_INST_TID = GEO_INST_TID.extend("intersects");
    public static final fURI COORDINATES_INST_TID = GEO_INST_TID.extend("coordinates");
    public static final fURI GEOMETRIES_INST_TID = GEO_INST_TID.extend("geometries");
    public static final fURI KIND_INST_TID = GEO_INST_TID.extend("kind");
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
        final Obj types = geoTypes.registered(this);
        this.jvm().putAll(mutableMap(
                uri(PATTERN), uri(GEO_ISA_TID.extend(ALL)),
                uri(TYPE), types,
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
                "the geodesic distance \\(d(a,b) = 2R\\arcsin\\sqrt{\\sin^2\\frac{\\Delta\\varphi}{2} + \\cos\\varphi_a\\cos\\varphi_b\\sin^2\\frac{\\Delta\\lambda}{2}}\\), and \\(0\\) when the geometries intersect, so a point inside a polygon is at distance zero rather than at the distance to its nearest vertex",
                "geo:point::[coordinates=>[-123.13,44.47]].geo:distance(geo:point::[coordinates=>[-122.68,45.52]])   [-- meter::121998.59… (the Columbia gorge, about 122 km) --]",
                "geo:polygon::[...].geo:distance(geo:point::[...])   [-- meter::0.0 when the point is inside --]"));

        insts.add(docWrap(instC(LENGTH_INST_TID.dom(GEOMETRY_TID).rng(MATH_METER_TID), lst(),
                        (lhs, inst) -> real(GeoUtil.length(lhs), MATH_METER_TID, null)),
                "the lhs geometry",
                "its measured length as a \\(\\mathrm{meter}::T\\) — every polyline and every polygon boundary, summed geodesically",
                Map.of(),
                "the \\(1\\)-dimensional measure of a geometry: \\(\\sum_i d(p_i, p_{i+1})\\) over each part's consecutive positions; a multipoint has length zero",
                "geo:linestring::[coordinates=>[[-123.13,44.47],[-122.68,45.52]]].geo:length()   [-- meter::121998.59… --]"));

        insts.add(docWrap(instC(AREA_INST_TID.dom(GEOMETRY_TID).rng(REAL_TID), lst(),
                        (lhs, inst) -> real(GeoUtil.area(lhs))),
                "the lhs geometry",
                "its area in square meters, as a plain \\(\\mathrm{real}::T\\) (there is no squared-length unit type in \\(\\mathrm{math}\\))",
                Map.of(),
                "the \\(2\\)-dimensional measure of the polygonal part: shells positive, holes negative, projected equirectangularly at the polygon's mean latitude — an approximation that is tight for parcels and districts, wrong for continents",
                "geo:polygon::[coordinates=>[[[-123.2,44.4],[-122.9,44.4],[-122.9,44.6],[-123.2,44.4]]]].geo:area()   [-- about 2.65e8 --]"));

        insts.add(docWrap(instC(CENTROID_INST_TID.dom(GEOMETRY_TID).rng(POINT_TID), lst(),
                        (lhs, inst) -> point(GeoUtil.centroid(lhs))),
                "the lhs geometry",
                "its representative point as a \\(\\mathrm{geo{:}point}::T\\)",
                Map.of(),
                "the area centroid when the geometry is polygonal, else the length-weighted midpoint of its polylines, else the mean of its positions — \\(\\bar{p} = \\frac{1}{A}\\int_A p\\,\\mathrm{d}A\\) for the polygonal case",
                "geo:linestring::[coordinates=>[[-123.13,44.47],[-122.98,44.52]]].geo:centroid()   [-- geo:point::[coordinates=>[-123.055,44.4950]] --]"));

        insts.add(docWrap(instC(ENVELOPE_INST_TID.dom(GEOMETRY_TID).rng(BBOX2D_TID), lst(),
                        (lhs, inst) -> GeoUtil.bbox(lhs)),
                "the lhs geometry",
                "its envelope as a \\(\\mathrm{geo{:}bbox}::T\\)",
                Map.of(),
                "the axis-aligned envelope \\([\\lambda_{\\min}, \\varphi_{\\min}, \\lambda_{\\max}, \\varphi_{\\max}]\\) over every stored position — the cheap rejection test every spatial index performs before an exact predicate",
                "geo:linestring::[coordinates=>[[-123.13,44.47],[-122.98,44.52]]].geo:envelope()   [-- geo:bbox::[-123.13,44.47,-122.98,44.52] --]"));

        insts.add(docWrap(instC(CONTAINS_INST_TID.dom(GEOMETRY_TID).rng(BOOL_TID), lst(GEOMETRY_TYPE),
                        (lhs, inst) -> bool(GeoUtil.contains(lhs, inst.arg(0)))),
                "the lhs geometry",
                "a \\(\\mathrm{bool}::T\\): whether the lhs contains the arg",
                Map.of(GEOMETRY_TYPE, "the geometry that may be contained"),
                "the containment predicate \\(a \\sqsupset b\\): every position of \\(b\\) inside \\(a\\) and no part of \\(b\\) properly crossing \\(a\\)'s boundary — exact for a point in a polygon, conservative for a polygon in a polygon",
                "geo:polygon::[...].geo:contains(geo:point::[...])   [-- true --]"));

        insts.add(docWrap(instC(INSIDE_INST_TID.dom(GEOMETRY_TID).rng(BOOL_TID), lst(ALL_TYPE),
                        (lhs, inst) -> bool(GeoUtil.within(lhs, inst.arg(0)))),
                "the lhs geometry",
                "a \\(\\mathrm{bool}::T\\): whether the lhs lies within the arg — a geometry, or an envelope",
                Map.of(GEOMETRY_TYPE, "the containing geometry, or a \\(\\mathrm{geo{:}bbox}::T\\) envelope"),
                "the converse of containment, \\(a \\sqsubset b \\iff b \\sqsupset a\\) — the predicate a spatial index answers natively, and the inverse of {@code geo:contains} in the family's law table. The envelope form (\\texttt{inside} a bbox) is the query an index answers from the box alone: every stored position must fall inside \\([\\lambda_{\\min}, \\varphi_{\\min}, \\lambda_{\\max}, \\varphi_{\\max}]\\).",
                "geo:point::[...].geo:inside(geo:polygon::[...])   [-- true --]",
                "geo:point::[...].geo:inside(geo:bbox::[-123.2,44.4,-122.9,44.6])   [-- true --]"));

        insts.add(docWrap(instC(INTERSECTS_INST_TID.dom(GEOMETRY_TID).rng(BOOL_TID), lst(GEOMETRY_TYPE),
                        (lhs, inst) -> bool(GeoUtil.intersects(lhs, inst.arg(0)))),
                "the lhs geometry",
                "a \\(\\mathrm{bool}::T\\): whether the lhs touches or overlaps the arg",
                Map.of(GEOMETRY_TYPE, "the other geometry"),
                "the overlap predicate \\(a \\cap b \\neq \\emptyset\\): a bbox rejection first, then a shared position, then a position inside the other's polygon, then a segment crossing — touching counts as intersecting",
                "geo:polygon::[...].geo:intersects(geo:polygon::[...])   [-- true --]"));

        insts.add(docWrap(instC(COORDINATES_INST_TID.dom(GEOMETRY_TID).rng(LST_TID), lst(),
                        (lhs, inst) -> lst(GeoUtil.positions(lhs).stream().map(GeoUtil::point).toList())),
                "the lhs geometry",
                "every position it stores, in document order, as a list of \\(\\mathrm{geo{:}position}::T\\) — a geometry collection is walked depth-first",
                Map.of(),
                "the flattening: \\(\\mathrm{positions}(g)\\), the coordinates any geometry is ultimately made of",
                "geo:linestring::[coordinates=>[[-123.13,44.47],[-122.98,44.52]]].geo:coordinates()   [-- [geo:position::[-123.13,44.47],geo:position::[-122.98,44.52]] --]"));

        insts.add(docWrap(instC(GEOMETRIES_INST_TID.dom(GEOMETRY_TID).rng(LST_TID), lst(),
                        (lhs, inst) -> GeoUtil.payload(lhs)),
                "the lhs geometry",
                "its immediate parts as a list of \\(\\mathrm{geo{:}geometry}::T\\) — a collection's members, or the geometry itself",
                Map.of(),
                "the \\(1\\)-step decomposition: the list the GeometryCollection kind nests, and a singleton for every other kind, so a fold over a geometry's parts needs no shape test",
                "geo:geometrycollection::[geometries=>[...]].geo:geometries()   [-- [geo:point::[...],geo:linestring::[...]] --]"));

        insts.add(docWrap(instC(KIND_INST_TID.dom(GEOMETRY_TID).rng(URI_TID), lst(),
                        (lhs, inst) -> uri(GeoUtil.tagOf(lhs))),
                "the lhs geometry",
                "its GeoJSON kind as a \\(\\mathrm{uri}::T\\) — \\(\\mathrm{Point}\\), \\(\\mathrm{Polygon}\\), …",
                Map.of(),
                "the tag the wire format discriminates on, as a uri so it can be matched and dispatched: the same fact the tid carries, addressed",
                "geo:point::[coordinates=>[-123.13,44.47]].geo:kind()   [-- Point --]"));

        insts.add(docWrap(instC(WKT_INST_TID.dom(GEOMETRY_TID).rng(STR_TID), lst(),
                        (lhs, inst) -> str(GeoUtil.wkt(lhs))),
                "the lhs geometry",
                "its Well-Known Text",
                Map.of(),
                "the other string encoding — the one every spatial database's \\(\\mathrm{ST\\_GeomFromText}\\) takes, so a geometry can be handed to a native operator. There is no \\(\\mathrm{wkt}::T\\) document type to cast onto, so this is an instruction rather than an \\(\\mathrm{as}\\)",
                "geo:point::[coordinates=>[-123.13,44.47]].geo:wkt()   [-- 'POINT (-123.13 44.47)' --]"));
        return insts;
    }

    /// ////////////////////////////////////////////////////////////////////////////////////////////
    // the casts — text and rec into the hierarchy, and the geo value out as a json document

    /**
     * The {@code as} instructions: a GeoJSON or WKT string onto any geo type, a plain rec onto any geo type (the
     * normalization door — an integer coordinate becomes a real), a bare coordinate list onto its coordinate type, and
     * a geo value onto a {@code json::T} document — because GeoJSON IS a json document, and {@code as(json::T)} is how
     * every other document type in this system is written (the web instruction set's {@code html::T}, {@code xml::T},
     * {@code markdown::T} family).
     */
    private List<Obj> casts() {
        final List<Obj> insts = new ArrayList<>();
        // the list-shaped coordinate types are cast from a lst, not a rec: a payload with no envelope about it
        final Map<fURI, Type> coordinates = new LinkedHashMap<>();
        coordinates.put(POINT_TID, POINT_TYPE);
        coordinates.put(LINE_TID, LINE_TYPE);
        coordinates.put(POLYGON_TID, POLYGON_TYPE);
        coordinates.put(BBOX2D_TID, BBOX2D_TYPE);
        coordinates.put(BBOX3D_TID, BBOX3D_TYPE);
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
    private static Map<fURI, Type> castableOut() {
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
    }

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
