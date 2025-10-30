/*
 * Metatron: A Distributed Computing Language and Virtual Machine
 * Copyright (C) 2025- PhaseShift Studio, LLC 
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

package studio.phaseshift.metatron.lang.mweb;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import org.petitparser.context.Result;
import studio.phaseshift.metatron.lang.mtron.mtronParser;
import studio.phaseshift.metatron.lang.mtron.type.NoObj;
import studio.phaseshift.metatron.lang.mtron.type.Obj;
import studio.phaseshift.metatron.ui.*;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Translator;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.lang.mtron.type.impl.MBool.bool;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MReal.real;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MRec.rec;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MStr.str;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MUri.uri;

public class JSONTranslator implements Translator<Obj, JsonElement> {

    private static final GraphittyLogger LOG = Graphitty.log(JSONTranslator.class);

    private static final ObjSerializer<String> SERIALIZER = ObjStringSerializer
            .build()
            .simpleColon(true)
            // .hideTypesMatching(MTRON_CORE_TYPES)
            .palette(Palette.NO_COLOR)
            .prettyPrint(false)
            .ignoreRewrites(true)
            .create();

    private final ObjSerializer<String> serializer;

    public JSONTranslator() {
        this(SERIALIZER);
    }

    public JSONTranslator(final ObjSerializer<String> serializer) {
        this.serializer = serializer;
    }

    @Override
    public Obj translate(final JsonElement json) {
        if (json.isJsonNull())
            return NoObj.noobj();
        else if (json.isJsonPrimitive()) {
            final JsonPrimitive jp = (JsonPrimitive) json;
            if (jp.isBoolean())
                return bool(jp.getAsBoolean());
            else if (jp.isNumber()) {
                if (jp.getAsString().contains("."))
                    return real(jp.getAsDouble());
                else
                    return jnt(jp.getAsLong());
            } else if (jp.isString()) {
                final String jpstr = jp.getAsString();
                try {
                    final Result r = mtronParser.m_call().parse(jpstr);
                    return r.isSuccess() ? r.get() : mtronParser.parse(jpstr);
                    // if (jpstr.charAt(0) == '"' && jpstr.charAt(jpstr.length() - 1) == '"')
                    //     jpstr = jpstr.substring(1, jpstr.length() - 1);
                } catch (Exception e) {
                    return str(jpstr);
                }
            }
        } else if (json.isJsonArray()) {
            final JsonArray jp = (JsonArray) json;
            final List<Obj> list = new ArrayList<>();
            for (var j : jp.getAsJsonArray()) {
                list.add(translate(j));
            }
            return lst(list);
        } else if (json.isJsonObject()) {
            final JsonObject jp = (JsonObject) json;
            final Map<Obj, Obj> map = new LinkedHashMap<>();
            for (var kv : jp.getAsJsonObject().asMap().entrySet()) {
                map.put(uri(kv.getKey()), translate(kv.getValue()));
            }
            return rec(map);
        }
        throw new IllegalStateException("unknown type: " + json + "::" + json.getAsInt());
    }

    @Override
    public JsonElement translate(final Obj obj) {
        try {
            if (obj.isNoObj())
                return JsonNull.INSTANCE;
            if (obj.isUri())
                return JsonParser.parseString(obj.uriValue().toString());
            if (obj.isStr())
                return JsonParser.parseString(obj.strValue());
            if (!obj.isPoly() && !obj.isCall())
                return JsonParser.parseString(this.serializer.write(obj));
            if (obj.isCall())
                return new JsonPrimitive(Graphitty.strip(SERIALIZER.write(obj)));
            if (obj.isLst()) {
                JsonArray array = new JsonArray();
                obj.lstValue().forEach(o -> array.add(translate(o)));
                return array;
            }
            if (obj.isRec()) {
                JsonObject object = new JsonObject();
                obj.recValue().forEach((key, value) -> object.add(key.uriValue().toString(), translate(value)));
                return object;
            } else
                throw MTronException.of("could not parse %s to json", obj);

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
            // LOG.error(e);
            // return NoObj.single();
            throw new IllegalArgumentException(json, e);
        }
    }
}
