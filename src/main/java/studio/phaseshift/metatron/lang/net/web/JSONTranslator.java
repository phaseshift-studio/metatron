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

package studio.phaseshift.metatron.lang.net.web;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import org.petitparser.context.Result;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.parser.mParser;
import studio.phaseshift.metatron.lang.core.m.obj.NoObj;
import studio.phaseshift.metatron.lang.core.m.type.*;
import studio.phaseshift.metatron.lang.sys.router.Router;
import studio.phaseshift.metatron.lang.util.serial.ObjSerializer;
import studio.phaseshift.metatron.lang.util.serial.ObjStringSerializer;
import studio.phaseshift.metatron.ui.*;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Translator;

import java.io.StringReader;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.furi.fURI.fnull;
import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.*;
import static studio.phaseshift.metatron.lang.core.m.obj.NoObj.noobj;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MBytes.bytes;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MType.T;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;

public record JSONTranslator(ObjSerializer<String> serializer) implements Translator<Obj, JsonElement> {

    public static final String TID_KEY = "_tid";
    public static final String VID_KEY = "_vid";
    public static final String BID_KEY = "_bid";
    public static final String VALUE_KEY = "_value";


    private static final GraphittyLogger LOG = Graphitty.log(JSONTranslator.class);

    private static final ObjSerializer<String> SERIALIZER = ObjStringSerializer
            .build()
            .simpleColon(true)
            // .hideTypesMatching(MTRON_CORE_TYPES)
            .palette(Palette.NO_COLOR)
            .prettyPrint(false)
            .ignoreRewrites(true)
            .create();

    public JSONTranslator() {
        this(SERIALIZER);
    }

    @Override
    public Obj translate(final JsonElement json) {
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
                obj = bool(jp.getAsBoolean(), tid, fnull);
            else if (jp.isNumber()) {
                if (jp.getAsString().contains("."))
                    obj = real(jp.getAsDouble(), tid, fnull);
                else
                    obj = jnt(jp.getAsLong(), tid, fnull);
            } else if (jp.isString()) {
                final String jpstr = jp.getAsString();
                try {
                    if (null != bid) {
                        if (bid.equals(BYTES_TID)) {
                            obj = bytes(ByteBuffer.wrap(jpstr.getBytes()), tid, fnull);
                        } else if (bid.equals(STR_TID)) {
                            obj = str(jpstr, tid, fnull);
                        } else if (bid.equals(CODE_TID)) {
                            obj = mParser.parse(jpstr);
                        } else if (bid.equals(INST_TID)) {
                            obj = mParser.parse(jpstr).<Call>as().tryToInst().vid(fnull);
                        } else if (bid.equals(FAIL_TID)) {
                            obj = fail(MTronException.of(jpstr));
                        }
                    }
                    if (null == obj)
                        obj = uri(f(jpstr), tid, fnull);
                } catch (Exception e) {
                    LOG.debug("ignoring unparseable element: %s", jp);
                    return noobj();
                }
            }
        } else if (value.isJsonArray()) {
            final JsonArray jp = (JsonArray) value;
            if (null != bid && bid.equals(REL_TID)) {
                obj = rel(translate(jp.get(0)), translate(jp.get(1)), tid, fnull);
            } else if (null != bid && bid.toString().equals("/m/type")) {
                obj = T(tid, (Call) translate(jp.get(0)), (Call) translate(jp.get(1)));
            } else {
                final List<Obj> list = new ArrayList<>();
                for (var j : jp.getAsJsonArray()) {
                    list.add(translate(j));
                }
                obj = null != bid && bid.equals(OBJS_TID) ?
                        objs(list) :
                        lst(list, tid, fnull);
            }
        } else if (value.isJsonObject()) {
            final JsonObject jp = (JsonObject) value;
            final Map<Obj, Obj> map = new LinkedHashMap<>();
            for (var kv : jp.getAsJsonObject().asMap().entrySet()) {
                final Obj v = translate(kv.getValue());
                final Uri k = uri(kv.getKey());
                if (!k.isNoObj() && !v.isNoObj())
                    map.put(k, v);
            }
            obj = rec(map, tid, fnull);
        }
        if (null == obj)
            throw new IllegalStateException("unknown type: " + json + "::" + json.getAsInt());
        return null == vid ? obj : obj.self(obj.jvm(), obj.tid(), vid);
    }

    @Override
    public JsonElement translate(final Obj obj) {
        JsonElement element;
        try {
            if (obj.isNoObj())
                return JsonNull.INSTANCE;
            else if (obj.isFail())
                element = new JsonPrimitive(obj.failValue().getMessage()); // todo: this is weak
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
                element = new JsonPrimitive(Graphitty.strip(SERIALIZER.write(obj.<Call>as().tryToInst())));
            else if (obj.isRel()) {
                final JsonArray array = new JsonArray();
                array.add(translate(obj.<Rel>as().first()));
                array.add(translate(obj.<Rel>as().second()));
                element = array;
            } else if (obj.isType()) {
                final JsonArray array = new JsonArray();
                //array.add(new JsonPrimitive(obj.tid().toString()));
                array.add(translate(obj.<Type>as().predicate()));
                array.add(translate(obj.<Type>as().constructor()));
                element = array;
            } else if (obj.isLst() || obj.isObjs()) {
                JsonArray array = new JsonArray();
                obj.<Iterable<Obj>>jvm().forEach(o -> array.add(translate(o)));
                element = array;
            } else if (obj.isRec()) {
                JsonObject object = new JsonObject();
                obj.recValue().forEach((key, value) -> object.add(key.uriValue().toString(), translate(value)));
                element = object;
            } else
                throw MTronException.of("could not parse %s to json: %s", obj.tid(), obj);
            /// ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            if (!obj.type().isBaseType() || obj.isObjs() || obj.isType() || obj.isStr() || obj.isObjCall() || obj.isFail() || obj.isRel()) {
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

    public Obj translateString(final String json) {
        try {
            final JsonReader reader = new JsonReader(new StringReader(json));
            reader.setStrictness(Strictness.LENIENT);
            return this.translate(JsonParser.parseReader(reader));
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }
}