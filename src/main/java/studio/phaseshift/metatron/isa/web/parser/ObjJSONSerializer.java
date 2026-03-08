/*
 * Metatron: A Distributed Computing Language and Virtual Machine
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
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.mach.io.type.AbstractObjSerializer;
import studio.phaseshift.metatron.isa.mach.io.type.ObjCleanStringSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.MTronException;

import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_from_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MBytes.bytes;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.io.ioInstSet.OBJ_SERIALIZER_TID;

public class ObjJSONSerializer extends AbstractObjSerializer<JsonElement> {

    public static final String TID_KEY = "_tid";
    public static final String VID_KEY = "_vid";
    public static final String BID_KEY = "_bid";
    public static final String VALUE_KEY = "_value";

    public static final fURI OBJ_JSON_SERIALIZER_VID = OBJ_SERIALIZER_TID.extend("json");

    private static final GraphittyLogger LOG = Graphitty.log(ObjJSONSerializer.class);

    private static final ObjCleanStringSerializer SERIALIZER = new ObjCleanStringSerializer();

    public ObjJSONSerializer() {
    }

    @Override
    public Obj read(final JsonElement json) {
        if (json.isJsonNull())
            return noobj();
        Obj obj = null;
        final fURI tid = json.isJsonObject() && json.getAsJsonObject().has(TID_KEY) ? Router.global().rewrite(f(json.getAsJsonObject().get(TID_KEY).getAsString()), true) : null;
        final fURI bid = json.isJsonObject() && json.getAsJsonObject().has(BID_KEY) ? Router.global().rewrite(f(json.getAsJsonObject().get(BID_KEY).getAsString()), true) : null == tid ? null : tid.basePath();
        final fURI vid = json.isJsonObject() && json.getAsJsonObject().has(VID_KEY) ? f(json.getAsJsonObject().get(VID_KEY).getAsString()) : null;
        final JsonElement value = null == bid ? json : json.getAsJsonObject().get(VALUE_KEY);
        if (value.isJsonPrimitive()) {
            final JsonPrimitive jp = (JsonPrimitive) value;
            if (jp.isBoolean())
                obj = bool(jp.getAsBoolean(), tid, null);
            else if (jp.isNumber()) {
                if (jp.getAsString().contains("."))
                    obj = real(jp.getAsDouble(), tid, null);
                else
                    obj = jnt(jp.getAsLong(), tid, null);
            } else if (jp.isString()) {
                final String jpstr = jp.getAsString();
                try {
                    if (null != bid) {
                        if (bid.equals(BYTES_TID)) {
                            obj = bytes(ByteBuffer.wrap(jpstr.getBytes()), tid, null);
                        } else if (bid.equals(STR_TID)) {
                            obj = str(jpstr, tid, null);
                        } else if (bid.equals(CODE_TID)) {
                            obj = mParser.parse(jpstr);
                        } else if (bid.equals(INST_TID)) {
                            obj = mParser.parse(jpstr).<Call>as().tryToInst().vid(null);
                            if (null != tid) {
                                if (tid.equals(AUTO_FROM_INST_TID)) {
                                    obj = auto_from_(obj.asUri()).tryToInst();
                                } else if (tid.equals(AUTO_INST_TID)) {
                                    obj = auto_(obj).tryToInst();
                                }
                            }
                        } else if (bid.equals(FAIL_TID)) {
                            obj = fail(MTronException.of(jpstr));
                        }
                    }
                    if (null == obj) {
                        if (jpstr.contains(" ") || jpstr.contains(")") || jpstr.contains("("))
                            obj = str(jpstr, tid, null);
                        else
                            obj = uri(f(jpstr), tid, null);
                    }
                } catch (Exception e) {
                    LOG.debug("ignoring unparseable element: %s", jp);
                    return noobj();
                }
            }
        } else if (value.isJsonArray()) {
            final JsonArray jp = (JsonArray) value;
            if (null != bid && bid.equals(REL_TID)) {
                obj = rel(read(jp.get(0)), read(jp.get(1)), tid, null);
            } else if (null != bid && bid.equals(TYPE_TID)) {
                obj = T(tid, null, (Call) read(jp.get(0)), (Call) read(jp.get(1)));
            } else {
                final List<Obj> list = new ArrayList<>();
                for (var j : jp.getAsJsonArray()) {
                    list.add(read(j));
                }
                obj = null != bid && bid.equals(OBJS_TID) ?
                        objs(list) :
                        lst(list, tid, null);
            }
        } else if (value.isJsonObject()) {
            final JsonObject jp = (JsonObject) value;
            final Map<Obj, Obj> map = new LinkedHashMap<>();
            for (var kv : jp.getAsJsonObject().asMap().entrySet()) {
                final Uri k = uri(kv.getKey());
                final Obj v = read(kv.getValue());
                if (!k.isNoObj() && !v.isNoObj())
                    map.put(k, v);
            }
            obj = rec(map, tid, null);
        }
        if (null == obj)
            throw new IllegalStateException("unknown type: " + json + "::" + json.getAsInt());
        return null == vid ? obj : obj.self(obj.jvm(), obj.tid(), vid);
    }

    @Override
    public JsonElement write(final Obj obj) {
        JsonElement element;
        try {
            if (obj.isNoObj())
                return JsonNull.INSTANCE;
            else if (obj.isFail())
                element = new JsonPrimitive(obj.failValue().get0().getMessage()); // todo: this is weak
            else if (obj.isBytes())
                element = new JsonPrimitive(obj.<Bytes>as().toHexString());
            else if (obj.isBool())
                element = new JsonPrimitive(obj.boolValue());
            else if (obj.isInt())
                element = new JsonPrimitive(obj.intValue());
            else if (obj.isReal())
                element = new JsonPrimitive(obj.realValue());
            else if (obj.isUri())
                element = new JsonPrimitive(obj.uriValue().toString());
            else if (obj.isStr())
                element = new JsonPrimitive(obj.strValue());
                //else if (!obj.isPoly() && !obj.isCall())
                //    element = JsonParser.parseString(this.serializer.write(obj));
            else if (obj.isCall())
                element = new JsonPrimitive(SERIALIZER.write(obj.<Call>as().tryToInst()));
            else if (obj.isRel()) {
                final JsonArray array = new JsonArray();
                array.add(write(obj.<Rel>as().first()));
                array.add(write(obj.<Rel>as().second()));
                element = array;
            } else if (obj.isType()) {
                final JsonArray array = new JsonArray();
                //array.add(new JsonPrimitive(obj.tid().toString()));
                array.add(write(obj.<Type>as().predicate()));
                array.add(write(obj.<Type>as().constructor()));
                element = array;
            } else if (obj.isLst() || obj.isObjs()) {
                JsonArray array = new JsonArray();
                obj.<Iterable<Obj>>jvm().forEach(o -> array.add(write(o)));
                element = array;
            } else if (obj.isRec()) {
                JsonObject object = new JsonObject();
                obj.recValue().forEach((key, value) -> object.add(key.uriValue().toString(), write(value)));
                element = object;
            } else
                throw MTronException.of("could not parse %s to json: %s", obj.tid(), obj);
            /// ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            if (!obj.type().isBaseType() || obj.isObjs() || obj.isType() || obj.isObjCall() || obj.isFail() || obj.isRel()) {
                final JsonObject typedObj = new JsonObject();
                typedObj.add(BID_KEY, new JsonPrimitive(Router.global().rewrite(obj.isType() ? TYPE_TID : (obj.isObjs() ? OBJS_TID : (obj.isCode() ? CODE_TID : (obj.isInst() ? INST_TID : obj.baseType().basePath()))), true).toString()));
                // if (!obj.type().isBaseType())
                typedObj.add(TID_KEY, new JsonPrimitive(Router.global().rewrite(obj.tid(), true).toString()));
                typedObj.add(VALUE_KEY, element);
                if (null != obj.vid())
                    typedObj.add(VID_KEY, new JsonPrimitive(obj.vid().toString()));
                return typedObj;
            } else {
                return element;
            }
        } catch (final Exception e) {
            throw MTronException.of(e, "could not parse to json: %s", obj);
        }
    }

    public static Obj parse(final String json) {
        try {
            final JsonReader reader = new JsonReader(new StringReader(json));
            reader.setStrictness(Strictness.LENIENT);
            return new ObjJSONSerializer().read(JsonParser.parseReader(reader));
        } catch (final Exception e) {
            throw MTronException.of("unable to parse: %s (%s)", json, e);
        }
    }

    @Override
    public Obj inputBytes(final ByteBuffer bytes) throws MTronException {
        return ObjJSONSerializer.parse(new String(bytes.array(), StandardCharsets.UTF_8));
    }


    @Override
    public fURI vid() {
        return OBJ_JSON_SERIALIZER_VID;
    }
}