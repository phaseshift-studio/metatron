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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.impl.MBool;
import studio.phaseshift.metatron.isa.m.web.parser.ObjJSONSerializer;
import studio.phaseshift.metatron.isa.m.web.type.AbstractObjSerializer;
import studio.phaseshift.metatron.util.MTronException;

import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.math.geo.geoInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * The GeoJSON (RFC 7946) translator: a {@link JsonElement} into a typed geo rec, and back.
 *
 * <p>This is the door {@code /m} has to the spatial world, and it is a <b>retyping</b> door, not a transcription:
 * the JSON text's one number type becomes mtron's {@code real} everywhere (a coordinate written {@code -123} reads
 * as {@code -123.0}, or the type check would reject half of the world's GeoJSON), {@code null} becomes {@code noobj},
 * and every object is stamped with its geo type — {@code geo_point}, {@code geo_polygon}, ... — so what comes back is
 * a member of the hierarchy rather than a lookalike rec.
 *
 * <h2>The tag is consumed on read and derived on write</h2>
 *
 * <p>The wire's {@code type} key is the same fact the value's tid carries, and the model stores it ONCE — in the tid.
 * Reading consumes the key (it selects the kind, then it is dropped); writing derives it from the tid. So a geo rec
 * carries no {@code type} key at all ({@code geo:point::[coordinates=>...]}), and a document written from it is
 * byte-for-byte the shape the RFC asks for.
 *
 * <p>A {@code Feature}'s absent geometry is written back as an explicit {@code null}: {@code noobj} is the empty
 * multiplicity, so a rec literal <em>drops</em> the key, and GeoJSON has no absent-geometry spelling other than
 * {@code null}. This is the one place the translation is asymmetric, and it is deliberate.
 *
 * <p>Foreign members (RFC 7946 §6.1) have nowhere to go in a closed geo shape: a feature's extras belong in
 * {@code properties}. A member the shape does not declare — including the {@code type} key itself — is dropped on
 * read.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ObjGeoJSONSerializer extends AbstractObjSerializer<JsonElement> {

    public static final fURI OBJ_GEOJSON_SERIALIZER_TID = GEO_ISA_TID.extend("serializer").extend("geojson");
    public static final fURI OBJ_GEOJSON_SERIALIZER_VID = OBJ_GEOJSON_SERIALIZER_TID;

    private static final ObjGeoJSONSerializer INSTANCE = new ObjGeoJSONSerializer();

    /**
     * The tag each geo type answers to — the one table both directions read, so a kind cannot be spelled one way on
     * the way in and another on the way out.
     */
    /** the rec key a feature's geometry lives at: a geo:-prefixed fiber */
    private static final String GEO_GEOMETRY = "geo:geometry";

    private static final Map<String, fURI> TYPES_BY_TAG = new LinkedHashMap<>();

    static {
        TYPES_BY_TAG.put(GeoUtil.BBOX_TAG, BBOX2D_TID);
        TYPES_BY_TAG.put("Point", POINT_TID);
        TYPES_BY_TAG.put("LineString", LINE_TID);
        TYPES_BY_TAG.put("Polygon", POLYGON_TID);
    }

    public static ObjGeoJSONSerializer single() {
        return INSTANCE;
    }

    public ObjGeoJSONSerializer() {
        super(OBJ_GEOJSON_SERIALIZER_TID, OBJ_GEOJSON_SERIALIZER_VID);
    }

    /**
     * Read a GeoJSON element into the typed geo rec it denotes. A {@code null} JSON value is {@code noobj}; a bare
     * array or primitive reads as its plain mtron counterpart (a coordinate payload read on its own is a list of
     * reals).
     */
    @Override
    public Obj read(final JsonElement json) {
        if (null == json || json.isJsonNull())
            return noobj();
        if (json.isJsonPrimitive()) {
            final JsonPrimitive primitive = json.getAsJsonPrimitive();
            if (primitive.isBoolean())
                return MBool.bool(primitive.getAsBoolean());
            if (primitive.isNumber())
                return real(primitive.getAsDouble());
            return str(primitive.getAsString());
        }
        if (json.isJsonArray()) {
            final List<Obj> values = new ArrayList<>();
            json.getAsJsonArray().forEach(element -> values.add(this.read(element)));
            return lst(values);
        }
        final JsonObject object = json.getAsJsonObject();
        if (!object.has(TYPE) || !object.get(TYPE).isJsonPrimitive())
            return this.generic(object);
        final String tag = object.get(TYPE).getAsString();
        return switch (tag) {
            // the wire's wrapper is consumed: what comes back is the geometry itself, stamped with its kind
            case "Point", "LineString", "Polygon" ->
                    this.stampGeom(TYPES_BY_TAG.get(tag), this.element(object, COORDINATES), this.element(object, BBOX));
            // MultiPoint / MultiLineString / MultiPolygon: a BAG of the member kind — a coefficient, not a type
            case "MultiPoint" -> bag("Point", object);
            case "MultiLineString" -> bag("LineString", object);
            case "MultiPolygon" -> bag("Polygon", object);
            // a collection is a bag of geometries, each read on its own terms
            case "GeometryCollection" -> bag(null, object);
            // a feature is a rec with a geometry fiber; its properties come back INTO the rec
            case GeoUtil.FEATURE_TAG -> this.featureRec(object);
            case "FeatureCollection" -> bag(GeoUtil.FEATURE_TAG, object);
            default -> throw MTronException.of("unknown GeoJSON type: %s", tag);
        };
    }

    /**
     * A BAG (a GeoJSON "Multi" or a collection): the members are read as their own kind, and the result is a
     * multiplicity — {@code point{2,}::T} — because that is what a bag is and GeoJSON has no way to say it.
     */
    private Obj bag(final String tag, final JsonObject object) {
        final JsonElement members = object.has(GEOMETRIES) ? object.get(GEOMETRIES)
                : object.has(FEATURES) ? object.get(FEATURES)
                : object.get(COORDINATES);
        if (null == members || !members.isJsonArray())
            return noobj();
        final List<Obj> values = new ArrayList<>();
        for (final JsonElement member : members.getAsJsonArray()) {
            if (null == tag) {
                values.add(this.read(member));
            } else if ("Point".equals(tag) || "LineString".equals(tag) || "Polygon".equals(tag)) {
                values.add(this.stampGeom(TYPES_BY_TAG.get(tag), this.read(member), noobj()));
            } else {
                values.add(this.read(member));
            }
        }
        return objs(values);
    }

    /**
     * A FEATURE is a rec with a geometry fiber: {@code rec[geo:+]::T}. The document's {@code geometry} becomes the
     * {@code geo:geometry} fiber and its {@code properties} are FLATTENED into the rec — the model has no properties
     * key, because properties are just the rec's other fields.
     */
    private Obj featureRec(final JsonObject object) {
        final Map<Obj, Obj> fields = new LinkedHashMap<>();
        // the geometry fiber is the one reserved key; a null geometry is ABSENT (write() puts the null back)
        if (object.has(GEOMETRY) && !object.get(GEOMETRY).isJsonNull())
            fields.put(uri(GEO_GEOMETRY), this.element(object, GEOMETRY));
        if (object.has(PROPERTIES) && object.get(PROPERTIES).isJsonObject())
            object.getAsJsonObject(PROPERTIES).asMap().forEach((key, value) -> fields.put(uri(key), this.read(value)));
        if (object.has(ID)) {
            final JsonElement id = object.get(ID);
            // a GeoJSON id is a str or a number; an integral number stays an int, a fractional one a real
            fields.put(uri(ID), id.isJsonPrimitive() && id.getAsJsonPrimitive().isNumber()
                    ? this.idNumber(id.getAsDouble())
                    : this.read(id));
        }
        if (object.has(BBOX))
            fields.put(uri(BBOX), this.element(object, BBOX));
        return rec(fields);
    }

    private Obj idNumber(final double value) {
        return value == Math.rint(value) ? jnt((long) value) : real(value);
    }

    /**
     * A JSON object with no GeoJSON tag is a plain rec (a feature's {@code properties}, most often) — every member
     * read, nothing stamped.
     */
    private Obj generic(final JsonObject object) {
        final Map<Obj, Obj> fields = new LinkedHashMap<>();
        object.entrySet().forEach(entry -> fields.put(uri(entry.getKey()), this.read(entry.getValue())));
        return rec(fields);
    }

    private Obj element(final JsonObject object, final String key) {
        return object.has(key) ? this.read(object.get(key)) : noobj();
    }

    /**
     * Stamp a wire-shaped rec with its geo type. The type's constructor is applied directly — never through the
     * {@code as} instruction — because the {@code as} instruction for {@code rec → geo} calls back into this reader,
     * and the two doors calling each other would not terminate.
     */
    /**
     * STAMP THE PAYLOAD WITH ITS KIND: the value IS the geometry, so the document's wrapper is dropped rather than
     * stored. A bbox is the one thing a bare value cannot hold, so when the wire carries one the payload is wrapped in
     * a rec — the only rec a geometry ever needs.
     */
    private Obj stampGeom(final fURI tid, final Obj payload, final Obj bbox) {
        if (payload.isNoObj())
            return noobj();
        // the payload is CANONICALIZED into stamped points and lines, so a value read off the wire is the same value
        // the type's own constructor would build: a point is a point, a ring is a line
        if (POINT_TID.equals(tid))
            return GeoUtil.point(GeoUtil.position(payload));
        if (LINE_TID.equals(tid))
            return GeoUtil.line(GeoUtil.toPositions(payload));
        if (POLYGON_TID.equals(tid)) {
            final List<Obj> rings = new ArrayList<>();
            payload.asLst().lstValue().forEach(ring -> rings.add(GeoUtil.line(GeoUtil.toPositions(ring))));
            return lst(rings).selfTID(POLYGON_TID);
        }
        return T(tid).constructor().apply(payload).selfTID(tid);
    }

    private Obj stamp(final fURI tid, final Obj value) {
        return T(tid).constructor().apply(value);
    }

    @Override
    public JsonElement write(final Obj obj) {
        if (null == obj || obj.isNoObj())
            return JsonNull.INSTANCE;
        if (obj.isStr())
            return new JsonPrimitive(obj.strValue());
        if (obj.isBool())
            return new JsonPrimitive(obj.boolValue());
        if (obj.isInt())
            return new JsonPrimitive(obj.intValue());
        if (obj.isReal())
            return this.number(obj.realValue());
        if (obj.isUri())
            return new JsonPrimitive(obj.uriValue().toString());
        if (null != GeoUtil.tagOf(obj) && obj.isLst())
            return this.writeGeometry(obj);
        if (obj.isLst()) {
            final JsonArray array = new JsonArray();
            obj.asLst().lstValue().forEach(element -> array.add(this.write(element)));
            return array;
        }
        if (obj.isObjs())
            return this.writeBag(obj);
        if (obj.isRec()) {
            // a rec with a geometry fiber IS a Feature: the fiber is its geometry, everything else is properties
            if (!GeoUtil.geometryFibers(obj).isEmpty())
                return this.writeFeature(obj);
            return this.writeRec(obj);
        }
        throw MTronException.of("cannot write %s as GeoJSON", obj.tid());
    }

    /**
     * A BAG: a multiplicity of one kind is the GeoJSON "Multi" of that kind, and a mixed bag is a GeometryCollection.
     * This is the one place where the bag/list distinction is read — a bag has no order, so the wire says "multiple"
     * where a single ordered list would say LineString or Polygon.
     */
    private JsonElement writeBag(final Obj bag) {
        final List<Obj> members = bag.stream().toList();
        if (members.isEmpty())
            return new JsonArray();
        final String tag = GeoUtil.tagOf(members.get(0));
        if (null != tag && members.stream().allMatch(member -> tag.equals(GeoUtil.tagOf(member))))
            switch (tag) {
                case "Point": return this.writeMulti("MultiPoint", members);
                case "LineString": return this.writeMulti("MultiLineString", members);
                case "Polygon": return this.writeMulti("MultiPolygon", members);
                default: break;
            }
        if (members.stream().allMatch(member -> member.isRec() && !GeoUtil.geometryFibers(member).isEmpty())) {
            final JsonObject out = new JsonObject();
            out.add(TYPE, new JsonPrimitive("FeatureCollection"));
            final JsonArray features = new JsonArray();
            members.forEach(member -> features.add(this.writeFeature(member)));
            out.add(FEATURES, features);
            return out;
        }
        // a heterogeneous bag is a GeometryCollection: every member written as its own document
        final JsonObject out = new JsonObject();
        out.add(TYPE, new JsonPrimitive("GeometryCollection"));
        final JsonArray geometries = new JsonArray();
        members.forEach(member -> geometries.add(this.write(member)));
        out.add(GEOMETRIES, geometries);
        return out;
    }

    private JsonElement writeMulti(final String type, final List<Obj> members) {
        final JsonObject out = new JsonObject();
        out.add(TYPE, new JsonPrimitive(type));
        final JsonArray coordinates = new JsonArray();
        members.forEach(member -> coordinates.add(this.writeCoordinates(GeoUtil.payload(member))));
        out.add(COORDINATES, coordinates);
        return out;
    }

    /**
     * A FEATURE: a rec with a `geo:`-prefixed fiber. One fiber is the document's geometry; several become a
     * GeometryCollection (the wire has exactly one slot, and dropping all but the first would lose data); every
     * non-geo field is the properties bag.
     */
    private JsonElement writeFeature(final Obj rec) {
        final JsonObject out = new JsonObject();
        out.add(TYPE, new JsonPrimitive(GeoUtil.FEATURE_TAG));
        final List<Obj> fibers = GeoUtil.geometryFibers(rec);
        if (1 == fibers.size()) {
            out.add(GEOMETRY, this.write(fibers.get(0)));
        } else {
            final JsonObject collection = new JsonObject();
            collection.add(TYPE, new JsonPrimitive("GeometryCollection"));
            final JsonArray geometries = new JsonArray();
            fibers.forEach(fiber -> geometries.add(this.write(fiber)));
            collection.add(GEOMETRIES, geometries);
            out.add(GEOMETRY, collection);
        }
        final JsonObject properties = new JsonObject();
        rec.asRec().jvm().forEach((key, value) -> {
            if (!(key.isUri() && key.uriValue().toString().startsWith(GeoUtil.GEO_FIBER_PREFIX)))
                properties.add(key.isUri() ? key.uriValue().toString() : key.toString(), this.write(value));
        });
        out.add(PROPERTIES, properties);
        return out;
    }

    /**
     * A GEOMETRY ON THE WIRE: {@code {"type": <kind>, "coordinates": <the value itself>}}. The coordinates are written
     * as RAW numbers and arrays — the nested points and lines carry tids that JSON has no place for and no need of.
     */
    private JsonElement writeGeometry(final Obj geometry) {
        final JsonObject out = new JsonObject();
        out.add(TYPE, new JsonPrimitive(GeoUtil.tagOf(geometry)));
        out.add(COORDINATES, this.writeCoordinates(GeoUtil.payload(geometry)));
        return out;
    }

    /** the payload as nested JSON arrays, stripping every tid on the way */
    private JsonElement writeCoordinates(final Obj obj) {
        if (obj.isLst()) {
            final JsonArray array = new JsonArray();
            for (final Obj element : obj.asLst().lstValue())
                array.add(GeoUtil.isPoint(element) ? this.writeCoordinates(element) : this.writeCoordinates(element));
            return array;
        }
        if (obj.isReal())
            return this.number(obj.realValue());
        if (obj.isInt())
            return new JsonPrimitive(obj.intValue());
        return this.write(obj);
    }

    private JsonElement writeRec(final Obj obj) {
        final String tag = GeoUtil.tagOf(obj);
        if (null == tag)
            return this.writeGeneric(obj);
        final JsonObject out = new JsonObject();
        out.add(TYPE, new JsonPrimitive(tag));
        switch (tag) {
            case "Point", "MultiPoint", "LineString", "MultiLineString", "Polygon", "MultiPolygon" -> {
                // the value IS the payload: Json has to carry it under `coordinates`, metatron does not
                out.add(COORDINATES, this.write(GeoUtil.payload(obj)));
                this.writeBbox(obj, out);
            }
            case "GeometryCollection" -> {
                out.add(GEOMETRIES, this.write(GeoUtil.payload(obj)));
                this.writeBbox(obj, out);
            }
            case GeoUtil.FEATURE_TAG -> {
                // GeoJSON's absent geometry is `null`, and a rec cannot hold an absent key — put the null back
                final Obj geometry = obj.asRec().at(uri(GEOMETRY));
                out.add(GEOMETRY, geometry.isNoObj() ? JsonNull.INSTANCE : this.write(geometry));
                final Obj properties = obj.asRec().at(uri(PROPERTIES));
                if (!properties.isNoObj())
                    out.add(PROPERTIES, this.write(properties));
                final Obj id = obj.asRec().at(uri(ID));
                if (!id.isNoObj())
                    out.add(ID, this.write(id));
                this.writeBbox(obj, out);
            }
            case "FeatureCollection" -> {
                out.add(FEATURES, this.write(obj.asRec().at(uri(FEATURES))));
                this.writeBbox(obj, out);
            }
            default -> throw MTronException.of("unknown geo type: %s", tag);
        }
        return out;
    }

    private void writeBbox(final Obj obj, final JsonObject out) {
        final Obj bbox = obj.asRec().at(uri(BBOX));
        if (!bbox.isNoObj())
            out.add(BBOX, this.write(bbox));
    }

    private JsonElement writeGeneric(final Obj obj) {
        final JsonObject out = new JsonObject();
        obj.asRec().recValue().forEach((key, value) -> out.add(key.toString(), this.write(value)));
        return out;
    }

    /**
     * A real as a JSON number in plain notation: {@code 0.1} stays {@code 0.1} rather than the {@code 1.0E-1} that
     * {@link JsonPrimitive#JsonPrimitive(Number)} can produce, because geo payloads are read by parsers that do not
     * all accept exponent notation in a coordinate.
     */
    private JsonElement number(final double value) {
        return new JsonPrimitive(new BigDecimal(Double.toString(value)));
    }

    /// ////////////////////////////////////////////////////////////////////////////////////////////
    // the string encodings beside the element form

    /**
     * Parse GeoJSON TEXT or Well-Known Text — the two string encodings a geo payload arrives in. The leading
     * character decides: {@code {} or [} is JSON, a letter is WKT.
     */
    public Obj parse(final String text) {
        final String trimmed = text.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("["))
            return this.read(this.reader(trimmed));
        final Obj wkt = GeoUtil.parseWkt(trimmed);
        return this.stamp(TYPES_BY_TAG.get(GeoUtil.tagOf(wkt)), wkt);
    }

    private JsonElement reader(final String text) {
        final JsonReader reader = new JsonReader(new StringReader(text));
        reader.setStrictness(Strictness.LENIENT);
        return JsonParser.parseReader(reader);
    }

    /**
     * The GeoJSON text of a geo value — what {@code x.as(json::T)} answers for a geo rec.
     *
     * <p>A value that is NOT a geo object (a plain rec, a str, a type) is handed to the ordinary JSON writer: the cast
     * this answers is {@code as(json::T)}, and a json document of some non-geo value is not a geo question. Without
     * that fall-through, a geo cast shadowing the web set's {@code rec -> json} would make every unrelated
     * {@code as(json::T)} fail.
     */
    public String text(final Obj obj) {
        // a geo value, a Feature (a rec with a geo fiber), and a bag of either all go through the GeoJSON writer;
        // anything else falls back to the generic web JSON serializer
        final boolean geo = null != GeoUtil.tagOf(obj)
                || (obj.isRec() && !GeoUtil.geometryFibers(obj).isEmpty())
                || (obj.isObjs() && obj.stream().anyMatch(member -> null != GeoUtil.tagOf(member)
                        || (member.isRec() && !GeoUtil.geometryFibers(member).isEmpty())));
        return geo ? this.write(obj).toString()
                : new String(ObjJSONSerializer.web().outputBytes(obj).array(), StandardCharsets.UTF_8);
    }

    /** the Well-Known Text of a geometry — what {@code x.geo_wkt()} answers */
    public String wkt(final Obj obj) {
        return GeoUtil.wkt(obj);
    }

    /**
     * Retype a value onto a geo type through the wire form: write it, read it back, and answer it only when the kind
     * that came back is the kind asked for. This is the {@code rec → geo} and {@code str → geo} CAST — it normalizes
     * (a coordinate written as an int becomes a real) and it refuses a mismatch instead of restamping it.
     */
    public Obj readAs(final Obj obj, final fURI tid) {
        if (obj.isStr())
            return this.retype(this.parse(obj.strValue()), tid);
        final JsonElement json = this.write(obj);
        // a rec carries no `type` key (the kind is its tid), so the requested type is what tells the reader which
        // shape this payload is meant to be — without this, an envelope-less rec reads as a plain rec
        if (json.isJsonObject()) {
            final JsonObject object = json.getAsJsonObject();
            if (!object.has(TYPE) && null != tagOf(tid))
                object.add(TYPE, new JsonPrimitive(tagOf(tid)));
        }
        return this.retype(this.read(json), tid);
    }

    /**
     * Accept a parsed value only as the requested kind, and answer it STAMPED: a structural match alone would hand
     * back a look-alike rec with a base tid, which no geo instruction would answer to.
     */
    private Obj retype(final Obj value, final fURI tid) {
        if (value.isNoObj() || !value.test(T(tid)))
            return noobj();
        return T(tid).constructor().apply(value);
    }

    /** the GeoJSON tag a tid stands for — the inverse of the reader's dispatch table */
    private static String tagOf(final fURI tid) {
        for (final Map.Entry<String, fURI> entry : TYPES_BY_TAG.entrySet())
            if (entry.getValue().equals(tid.big()))
                return entry.getKey();
        return null;
    }

    @Override
    public Obj inputBytes(final ByteBuffer bytes) throws MTronException {
        return this.read(this.reader(new String(bytes.array(), StandardCharsets.UTF_8)));
    }

    @Override
    public fURI vid() {
        return OBJ_GEOJSON_SERIALIZER_VID;
    }

    /**
     * The type table, tag → tid, for callers that need to answer "which kinds exist?" without reaching into the
     * reader's internals.
     */
    public static Map<String, fURI> typesByTag() {
        return Map.copyOf(TYPES_BY_TAG);
    }
}
