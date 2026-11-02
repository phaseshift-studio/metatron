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

import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.type.impl.TypeBuilder;
import studio.phaseshift.metatron.util.MTronException;

import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.m.mInstSet.AS_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.REAL_TYPE;
import static studio.phaseshift.metatron.isa.m.math.geo.geoInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.web.webInstSet.WEB_JSON_TID;

/**
 * The {@code /type} registrations of the geo instruction set — and the whole of its data model.
 *
 * <p>The names carry no {@code geo_} prefix: the instruction set's namespace is the prefix. Imported as
 * {@code import(/m/math/geo,geo)}, the types read {@code geo:point::T}, {@code geo:line::T}, … .
 *
 * <h2>GeoJSON's vocabulary is a JSON vocabulary</h2>
 *
 * <p>{@code type}, {@code coordinates}, {@code geometry}, {@code properties} and {@code features} exist because JSON
 * cannot say what a value is. A point is not "a value whose {@code type} is {@code Point} and whose {@code coordinates}
 * are a position" — a point IS its position. So the model is the list, and the KIND is which list:
 *
 * <pre>
 * lst::T@geometry                                            any list: a shape on the earth
 * geometry::T[?[real::T,real::T,real{?}::T]]@point           a coordinate pair or triple
 * geometry::T[?[point{+}::T]]@line                           an ORDERED list of points
 * geometry::T[?[line{+}::T]]@polygon                         a shell line plus hole lines
 * </pre>
 *
 * <p>There is no {@code position}, no {@code multipoint}, no {@code multilinestring}, no {@code multipolygon} and no
 * {@code geometrycollection}. A multi-point is a BAG of points ({@code point{2,}::T}), a multi-line is a bag of lines,
 * and a collection is a bag of geometries — coefficients, not types. The bag/list distinction is real and the wire has
 * no way to say it, which is exactly the mapping {@link ObjGeoJSONSerializer} owns.
 *
 * <p>The same is true of the document container: a <b>feature</b> is a rec with a {@code geo:}-prefixed fiber
 * ({@code rec[geo:+]::T}) — see the serializer's documentation — so no feature type is declared here, and none is
 * needed.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class geoTypes {

    private geoTypes() {
    }

    /**
     * The registered type blocks — the {@code lst} the instset's setup puts at {@code /type}.
     *
     * @param self the registering instset
     * @return the docwrapped type registrations
     */
    public static studio.phaseshift.metatron.isa.m.type.Lst registered(final InstSet self) {
        final studio.phaseshift.metatron.isa.m.type.Lst types = lst(
                // ── the bases: NOMINAL, no poly. A refinement declares its own shape ──────────────────────────────
                GEOMETRY_TYPE = TypeBuilder.build()
                                .tid(LST_TID)
                                .vid(GEOMETRY_TID, "the nominal base of every geometry: a list of reals at whatever depth its kind nests. It declares no poly — a point, a line and a polygon each declare their own")
                                .create(),
                BBOX_TYPE = TypeBuilder.build()
                                .tid(LST_TID)
                                .vid(BBOX_TID, "the nominal base of an envelope. It declares no poly: geo:bbox2d and geo:bbox3d do")
                                .create(),
                // ── the shapes ────────────────────────────────────────────────────────────────────────────────
                docWrap(POINT_TYPE = shapeType(POINT_TID, GEOMETRY_TID,
                                lst(REAL_TYPE, REAL_TYPE, REAL_TYPE.c(cInt.MAYBE())), GeoUtil::isPoint,
                                arg -> GeoUtil.isLine(GeoUtil.payload(arg)) || GeoUtil.isPolygon(GeoUtil.payload(arg))
                                        ? GeoUtil.point(GeoUtil.centroid(arg)) : null),
                        Map.of(jnt(0), "the x (longitude) coordinate",
                                jnt(1), "the y (latitude) coordinate",
                                jnt(2).maybe(), "the z (altitude) coordinate"),
                        "a point — a coordinate pair, or a triple with an altitude. \\([\\lambda, \\varphi[, h]]\\)",
                        "geo:point::[-123.13,44.47]", "geo:point::[-123.13,44.47,1240.0]"),
                docWrap(LINE_TYPE = shapeType(LINE_TID, GEOMETRY_TID,
                                lst(T(POINT_TID).c(cInt.SOME())), GeoUtil::isLine),
                        Map.of(),
                        "a line — an ORDERED list of points. A multi-point is a bag, a line is a sequence, and the wire has no way to say the difference",
                        "geo:line::[[-123.13,44.47],[-122.98,44.52]]"),
                docWrap(POLYGON_TYPE = shapeType(POLYGON_TID, GEOMETRY_TID,
                                lst(T(LINE_TID).c(cInt.SOME())), GeoUtil::isPolygon),
                        Map.of(),
                        "a polygon — a shell line followed by zero or more hole lines (a ring is a line whose first and last points coincide, which the RFC requires and this type does not enforce)",
                        "geo:polygon::[[[-123.2,44.4],[-122.9,44.4],[-122.9,44.6],[-123.2,44.4]]]"),
                // ── the envelopes ─────────────────────────────────────────────────────────────────────────────
                docWrap(BBOX2D_TYPE = shapeType(BBOX2D_TID, BBOX_TID,
                                lst(REAL_TYPE, REAL_TYPE, REAL_TYPE, REAL_TYPE), GeoUtil::isBbox2d,
                                arg -> GeoUtil.isBbox3d(arg) ? GeoUtil.bbox2dOf(arg)
                                        : GeoUtil.isLine(GeoUtil.payload(arg)) || GeoUtil.isPolygon(GeoUtil.payload(arg))
                                        || GeoUtil.isPoint(GeoUtil.payload(arg)) ? GeoUtil.bbox(arg) : null),
                        Map.of(jnt(0), "the minimum x", jnt(1), "the minimum y",
                                jnt(2), "the maximum x", jnt(3), "the maximum y"),
                        "a 2D envelope — four reals, \\([\\lambda_{\\min}, \\varphi_{\\min}, \\lambda_{\\max}, \\varphi_{\\max}]\\)",
                        "geo:bbox2d::[-123.2,44.4,-122.9,44.6]"),
                docWrap(BBOX3D_TYPE = shapeType(BBOX3D_TID, BBOX_TID,
                                lst(REAL_TYPE, REAL_TYPE, REAL_TYPE, REAL_TYPE, REAL_TYPE, REAL_TYPE), GeoUtil::isBbox3d),
                        Map.of(jnt(0), "the minimum x", jnt(1), "the minimum y", jnt(2), "the minimum z",
                                jnt(3), "the maximum x", jnt(4), "the maximum y", jnt(5), "the maximum z"),
                        "a 3D envelope — six reals, \\([\\lambda_{\\min}, \\varphi_{\\min}, h_{\\min}, \\lambda_{\\max}, \\varphi_{\\max}, h_{\\max}]\\)",
                        "geo:bbox3d::[-123.2,44.4,0.0,-122.9,44.6,1300.0]"));
        publish();
        return types;
    }

    /** publish the assigned types to the instset that names them (a static import cannot be assigned) */
    private static void publish() {
        geoInstSet.GEOMETRY_TYPE = GEOMETRY_TYPE;
        geoInstSet.POINT_TYPE = POINT_TYPE;
        geoInstSet.LINE_TYPE = LINE_TYPE;
        geoInstSet.POLYGON_TYPE = POLYGON_TYPE;
        geoInstSet.BBOX_TYPE = BBOX_TYPE;
        geoInstSet.BBOX2D_TYPE = BBOX2D_TYPE;
        geoInstSet.BBOX3D_TYPE = BBOX3D_TYPE;
    }

    /**
     * A KIND: a nominal refinement of a base ({@code geometry} or {@code bbox}) that DECLARES its list shape. The poly
     * is the whole type — no rec, no {@code coordinates} key — and the constructor only refuses a value the declaration
     * would not accept.
     */
    private static Type shapeType(final fURI vid, final fURI base, final Obj shape, final Predicate<Obj> valid) {
        return shapeType(vid, base, shape, valid, null);
    }

    /**
     * As above, plus a REDUCTION: a constructive cast the type accepts from another shape ({@code as(point::T)} on a
     * plane is the centroid, {@code as(bbox2d::T)} on a line is the envelope). Returns {@code null} when the value is
     * not reducible, so the constructor can refuse it.
     */
    private static Type shapeType(final fURI vid, final fURI base, final Obj shape, final Predicate<Obj> valid,
                                  final Function<Obj, Obj> reduce) {
        // the text and document doors belong to the GEOJSON kinds: a bbox is not a GeoJSON object, it is a field on
        // one, so it has no document form to be read from or written to
        final boolean geojson = GEOMETRY_TID.equals(base);
        final TypeBuilder builder = TypeBuilder.build()
                .tid(base)
                .vid(vid)
                .isaPredicate(shape)
                .constructor(arg -> {
                    if (valid.test(arg))
                        return arg.selfTID(vid);
                    if (null != reduce) {
                        final Obj reduced = reduce.apply(arg);
                        if (null != reduced && !reduced.isNoObj())
                            return reduced;
                    }
                    return fail(MTronException.of("%s is not a %s", arg, vid.name()));
                });
        if (geojson)
            builder.inst(castInFromText(vid)).inst(castInFromJson(vid)).inst(castInFromRec(vid))
                    .inst(castOutToJson(vid));
        return builder.create();
    }


    /// ////////////////////////////////////////////////////////////////////////////////////////////
    // THE `as` CONTRACTS A KIND DECLARES — at /m/inst/as, which is /m's address. Declared here, so the
    // contract is documented on /m's page (its address) AND on this set's page (its author), and the geo
    // types it names are what says which instruction set it is meant to be used with.
    /// ////////////////////////////////////////////////////////////////////////////////////////////

    /** read a GeoJSON or Well-Known Text document as {@code vid} — the two text dialects a payload arrives in */
    private static Inst castInFromText(final fURI vid) {
        return docWrap(instC(AS_INST_TID.dom(STR_TID).rng(vid), lst(T(vid)), (lhs, inst) -> geoInstSet.cast(lhs, vid)),
                "the GeoJSON or Well-Known Text document",
                "a \\(\\mathrm{" + vid.name() + "}::T\\)",
                Map.of(),
                "read a GeoJSON or WKT document as " + vid.name() + "::T",
                wktExample(vid));
    }

    /** read a GeoJSON document (a json::T) as {@code vid}, normalizing its numbers onto the type */
    private static Inst castInFromJson(final fURI vid) {
        return docWrap(instC(AS_INST_TID.dom(WEB_JSON_TID).rng(vid), lst(T(vid)), (lhs, inst) -> geoInstSet.cast(lhs, vid)),
                "a GeoJSON document",
                "the " + vid.name() + "::T it normalizes to",
                Map.of(),
                "read a GeoJSON document as " + vid.name() + "::T — an integer coordinate becomes a real, so the value is normalized onto the type rather than merely retyped",
                "json::[\"type\"=>\"Point\",\"coordinates\"=>[-123,44]].as(geo:" + vid.name() + "::T)");
    }

    /**
     * read a REC as {@code vid}: a GeoJSON document read the generic way ({@code as(rec::T)}) is a rec holding
     * {@code type} and {@code coordinates}, and a Feature document is a rec holding {@code geometry} — both are read
     * through the same serializer, so the rec spelling and the json spelling answer alike.
     */
    private static Inst castInFromRec(final fURI vid) {
        return docWrap(instC(AS_INST_TID.dom(REC_TID).rng(vid), lst(T(vid)), (lhs, inst) -> geoInstSet.cast(lhs, vid)),
                "a rec holding the geometry — a GeoJSON document read the generic way",
                "a \\(\\mathrm{" + vid.name() + "}::T\\)",
                Map.of(),
                "read a rec as " + vid.name() + "::T — the rec's own {@code type}/{@code coordinates} shape, or, when it is a Feature, the geometry at its {@code geometry} field",
                "json::\"\"\"{\"type\":\"Polygon\",\"coordinates\":[…]}\"\"\".as(rec::T).as(" + vid.name() + "::T)");
    }

    /** write {@code vid} as a GeoJSON document — GeoJSON IS a json document */
    private static Inst castOutToJson(final fURI vid) {
        return docWrap(instC(AS_INST_TID.dom(vid).rng(WEB_JSON_TID), lst(T(WEB_JSON_TID)), (lhs, inst) -> geoInstSet.json(lhs)),
                "the " + vid.name() + "::T to write",
                "the GeoJSON document it is, as json::T",
                Map.of(),
                "write the " + vid.name() + "::T as a GeoJSON document — GeoJSON IS a json document, so this is the same door every other document type in this system is written through",
                "geo:" + vid.name() + "::[-123.13,44.47].as(json::T)");
    }

    /** a WKT literal of THIS kind's shape — a POINT document cannot be read as a box or a polygon */
    private static String wktExample(final fURI vid) {
        final String wkt = switch (vid.name()) {
            case "point" -> "POINT (-123.13 44.47)";
            case "line" -> "LINESTRING (-123.13 44.47, -121.18 45.61)";
            case "polygon" -> "POLYGON ((-123.2 44.4, -122.9 44.4, -122.9 44.6, -123.2 44.4))";
            default -> "POINT (-123.13 44.47)";
        };
        return "\"" + wkt + "\".as(geo:" + vid.name() + "::T)";
    }

    /// ////////////////////////////////////////////////////////////////////////////////////////////

    static Type GEOMETRY_TYPE;
    static Type POINT_TYPE;
    static Type LINE_TYPE;
    static Type POLYGON_TYPE;
    static Type BBOX_TYPE;
    static Type BBOX2D_TYPE;
    static Type BBOX3D_TYPE;
}
