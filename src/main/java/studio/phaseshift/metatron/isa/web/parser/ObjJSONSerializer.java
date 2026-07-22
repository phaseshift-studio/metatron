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

package studio.phaseshift.metatron.isa.web.parser;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.mach.io.type.AbstractObjSerializer;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.Code.CODE_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Inst.INST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Lst.LST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Type.TYPE_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MBytes.bytes;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

public class ObjJSONSerializer extends AbstractObjSerializer<JsonElement> {

    public static final String TID_KEY = "_tid";
    public static final String VID_KEY = "_vid";
    public static final String BID_KEY = "_bid";
    public static final String VALUE_KEY = "_value";

    public static final fURI OBJ_JSON_SERIALIZER_TID = studio.phaseshift.metatron.isa.web.webInstSet.OBJ_JSON_SERIALIZER_TID;
    public static final fURI OBJ_JSON_SERIALIZER_VID = OBJ_JSON_SERIALIZER_TID;

    private static final GraphittyLogger LOG = Graphitty.log(ObjJSONSerializer.class);
    private static final ObjmtronSerializer SERIALIZER = new ObjmtronSerializer();
    private static final Pattern HEX_PATTERN = Pattern.compile("^0x[0-9a-fA-F]+$");

    public enum Density {
        TRANSPARENT,
        OPAQUE
    }

    private static final fURI KEY_DENSITY = fURI.Singleton.f("density");
    private static final fURI KEY_WRAP_URI = fURI.Singleton.f("wrap_uri");
    private static final fURI KEY_BIAS_URI = fURI.Singleton.f("bias_towards_uri");
    private static final fURI KEY_BIAS_OBJS = fURI.Singleton.f("bias_towards_objs");

    private static final ObjJSONSerializer INSTANCE = new ObjJSONSerializer();

    public static ObjJSONSerializer single() {
        return INSTANCE;
    }

    /**
     * Returns a serializer configured with "Simple" defaults (URI-biased).
     */
    public static ObjJSONSerializer simple() {
        ObjJSONSerializer s = new ObjJSONSerializer();
        s.at(KEY_BIAS_URI, bool(true), Poly.MUTABLE);
        s.at(KEY_BIAS_OBJS, bool(false), Poly.MUTABLE);
        return s;
    }

    public ObjJSONSerializer() {
        super(OBJ_JSON_SERIALIZER_TID, OBJ_JSON_SERIALIZER_VID);
        this.at(KEY_DENSITY, str("OPAQUE"), Poly.MUTABLE);
        this.at(KEY_WRAP_URI, bool(true), Poly.MUTABLE);
        this.at(KEY_BIAS_URI, bool(true), Poly.MUTABLE);
        this.at(KEY_BIAS_OBJS, bool(false), Poly.MUTABLE);
    }

    private Density getDensity() {
        String d = this.at(KEY_DENSITY).orElse(str("OPAQUE")).strValue();
        return "TRANSPARENT".equalsIgnoreCase(d) ? Density.TRANSPARENT : Density.OPAQUE;
    }

    private boolean isWrapURI() {
        return this.at(KEY_WRAP_URI).orElse(bool(true)).boolValue();
    }

    private boolean biasTowardsUri() {
        return this.at(KEY_BIAS_URI).orElse(bool(true)).boolValue();
    }

    private boolean biasTowardsObjs() {
        return this.at(KEY_BIAS_OBJS).orElse(bool(false)).boolValue();
    }

    @Override
    public Obj read(final JsonElement json) {
        if (json.isJsonNull()) return noobj();

        fURI tid = null, vid = null, bid = null;
        fURI rawTid = null;
        if (json.isJsonObject()) {
            JsonObject jo = json.getAsJsonObject();
            if (jo.has(TID_KEY)) {
                rawTid = f(jo.get(TID_KEY).getAsString());
                tid = Router.global().redirect(rawTid, true);
            }
            if (jo.has(VID_KEY)) vid = f(jo.get(VID_KEY).getAsString());
            if (jo.has(BID_KEY)) bid = Router.global().redirect(f(jo.get(BID_KEY).getAsString()), true);
        }

        JsonElement value = json;
        if (json.isJsonObject() && json.getAsJsonObject().has(VALUE_KEY)) {
            value = json.getAsJsonObject().get(VALUE_KEY);
        } else if (json.isJsonObject() && (tid != null || vid != null || bid != null)) {
            value = json;
        }

        Obj obj = null;
        if (value.isJsonPrimitive()) {
            final JsonPrimitive jp = (JsonPrimitive) value;
            if (jp.isBoolean()) obj = bool(jp.getAsBoolean(), tid, null);
            else if (jp.isNumber()) {
                if (jp.getAsString().contains(".")) obj = real(jp.getAsDouble(), tid, null);
                else obj = jnt(jp.getAsLong(), tid, null);
            } else if (jp.isString()) {
                final String jpstr = jp.getAsString();
                if (HEX_PATTERN.matcher(jpstr).matches()) {
                    obj = bytes(ByteBuffer.wrap(HexFormat.of().parseHex(jpstr.substring(2))), tid, null);
                } else if (tid != null && T(tid).isRefinementOf(STR_TYPE)) {
                    obj = str(jpstr, tid, null);
                } else if (tid != null && T(tid).isRefinementOf(URI_TYPE)) {
                    String clean = (jpstr.startsWith("<") && jpstr.endsWith(">")) ? jpstr.substring(1, jpstr.length() - 1) : jpstr;
                    obj = uri(f(clean), tid, null);
                } else if (tid != null && T(tid).isRefinementOf(CODE_TYPE)) {
                    obj = ObjmtronSerializer.parse(jpstr);
                } else if ((bid != null && T(bid).isRefinementOf(INST_TYPE)) || (tid != null && T(tid).isRefinementOf(INST_TYPE))) {
                    obj = ObjmtronSerializer.parse(jpstr);
                } else {
                    try {
                        if ((jpstr.startsWith("<") && jpstr.endsWith(">")) || jpstr.startsWith("http:") || jpstr.startsWith("https:") || jpstr.startsWith("uri::")) {
                            String clean = (jpstr.startsWith("<") && jpstr.endsWith(">")) ? jpstr.substring(1, jpstr.length() - 1) :
                                    (jpstr.startsWith("uri::") ? jpstr.substring(5) : jpstr);
                            obj = uri(f(clean), tid, null);
                        } else if (biasTowardsUri() && !jpstr.contains(" ")) {
                            try {
                                obj = uri(f(jpstr), tid, null);
                            } catch (Exception e2) {
                                obj = ObjmtronSerializer.parse(jpstr).apply();
                            }
                        } else {
                            obj = ObjmtronSerializer.parse(jpstr).apply();
                        }
                    } catch (Exception e) {
                        obj = str(jpstr, tid, null);
                    }
                }
            }
        } else if (value.isJsonArray()) {
            final JsonArray ja = (JsonArray) value;
            if (bid != null && T(bid).isRefinementOf(REC_TYPE) &&
                    !ja.isEmpty() && ja.asList().stream().allMatch(e -> e.isJsonArray() && e.getAsJsonArray().size() == 2)) {
                obj = ja.asList().stream().map(e -> rel(read(e.getAsJsonArray().get(0)), read(e.getAsJsonArray().get(1)))).collect(new CommonUtil.RecCollector(bid, vid));
            } else if (ja.size() == 2 && bid != null && TYPE_TID.equals(bid.basePath())) {
                final fURI typeName = null != rawTid ? rawTid : tid;
                final Obj parsed = ObjmtronSerializer.parse(typeName.toString() + "::T");
                obj = parsed.isObjCall() ? ((Call) parsed).tryToInst() : parsed;
            } else {
                List<Obj> list = new ArrayList<>();
                for (var j : ja) list.add(read(j));
                final boolean isLst = (bid != null && LST_TID.equals(bid.basePath()))
                        || (bid == null && tid != null && LST_TID.equals(tid.basePath()));
                if (isLst) {
                    obj = lst(list, tid, null);
                } else if (tid != null || bid != null) {
                    obj = objs(list);
                } else {
                    obj = lst(list, tid, null);
                }
            }
        } else if (value.isJsonObject()) {
            final JsonObject jo = (JsonObject) value;
            Map<Obj, Obj> map = new LinkedHashMap<>();
            for (var entry : jo.entrySet()) {
                map.put(uri(f(entry.getKey())), read(entry.getValue()));
            }
            obj = rec(map, tid == null ? REC_TID : tid, null);
        }

        if (obj == null) return noobj();
        return null == vid ? obj : obj.self(obj.jvm(), obj.tid(), vid);
    }

    @Override
    public JsonElement write(final Obj obj) {
        if (obj.isNoObj()) return JsonNull.INSTANCE;

        JsonElement element;
        if (obj.isFail()) element = new JsonPrimitive(obj.failValue().getMessage());
        else if (obj.isBytes()) element = new JsonPrimitive(obj.<Bytes>as().toHexString());
        else if (obj.isBool()) element = new JsonPrimitive(obj.boolValue());
        else if (obj.isInt()) element = new JsonPrimitive(obj.intValue());
        else if (obj.isReal()) element = new JsonPrimitive(obj.realValue());
        else if (obj.isUri()) {
            String val = isWrapURI() ? "<" + obj.uriValue().toString() + ">" : obj.uriValue().toString();
            element = new JsonPrimitive(val);
        } else if (obj.isStr()) element = new JsonPrimitive(obj.strValue());
        else if (obj.isObjCall()) element = new JsonPrimitive(SERIALIZER.write(obj.<Call>as().tryToInst()));
        else if (obj.isRel()) {
            JsonArray arr = new JsonArray();
            arr.add(write(obj.<Rel>as().first()));
            arr.add(write(obj.<Rel>as().second()));
            element = arr;
        } else if (obj.isType()) {
            JsonArray arr = new JsonArray();
            arr.add(write(obj.<Type>as().predicate()));
            arr.add(write(obj.<Type>as().constructor()));
            element = arr;
        } else if (obj.isLst() || obj.isObjs()) {
            JsonArray arr = new JsonArray();
            obj.<Iterable<Obj>>jvm().forEach(o -> arr.add(write(o)));
            element = arr;
        } else if (obj.isRec()) {
            JsonObject jo = new JsonObject();
            obj.recValue().forEach((k, v) -> jo.add(k.uriValue().toString(), write(v)));
            element = jo;
        } else throw MTronException.of("unsupported type: %s", obj.tid());

        if (getDensity() == Density.OPAQUE && (obj.isObjs() || !obj.type().isBaseType() || obj.vid() != null)) {
            JsonObject envelope = new JsonObject();            // Use base type path without aggressive redirection to preserve shortcuts like '#'
            String bidStr = obj.baseType().basePath().toString();
            if (bidStr.isEmpty() || bidStr.equals("#"))
                envelope.add(BID_KEY, new JsonPrimitive(bidStr));
            else if (obj.isLst())
                envelope.add(BID_KEY, new JsonPrimitive(LST_TID.toString()));
            else if (obj.isObjs())
                envelope.add(BID_KEY, new JsonPrimitive(OBJS_TID.toString()));
            envelope.add(TID_KEY, new JsonPrimitive(obj.tid().big().toString()));
            envelope.add(VALUE_KEY, element);
            if (obj.vid() != null) envelope.add(VID_KEY, new JsonPrimitive(obj.vid().toString()));
            return envelope;
        }
        return element;
    }

    public static Obj parse(final String json) {
        JsonReader reader = new JsonReader(new StringReader(json));
        reader.setStrictness(Strictness.LENIENT);
        return single().read(JsonParser.parseReader(reader));
    }
    
    @Override
    public Obj inputBytes(ByteBuffer bytes) throws MTronException {
        return parse(new String(bytes.array(), StandardCharsets.UTF_8));
    }

    @Override
    public fURI vid() {
        return OBJ_JSON_SERIALIZER_VID;
    }
}
