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
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

package studio.phaseshift.metatron.lang.translate;

import com.google.gson.*;
import studio.phaseshift.metatron.lang.obj.NoObj;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.lang.obj.mtron.*;
import studio.phaseshift.metatron.ui.ObjSerializer;
import studio.phaseshift.metatron.ui.ObjStringSerializer;
import studio.phaseshift.metatron.ui.Palette;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JSONTranslator implements Translator<Obj, JsonElement> {
    private static final ObjSerializer<String> SERIALIZER = ObjStringSerializer
            .build()
            .simpleColon(true)
            // .hideTypesMatching(MTRON_CORE_TYPES)
            .palette(Palette.NO_COLOR)
            .ignoreRewrites(true)
            .create();

    @Override
    public Obj translate(final JsonElement json) {
        if (json.isJsonNull())
            return NoObj.single();
        else if (json.isJsonPrimitive()) {
            final JsonPrimitive jp = (JsonPrimitive) json;
            if (jp.isBoolean())
                return new MBool(jp.getAsBoolean());
            else if (jp.isNumber()) {
                if (jp.getAsString().contains("."))
                    return new MReal(jp.getAsDouble());
                else

                    return new MInt(jp.getAsLong());
            } else if (jp.isString()) {
                final String jpstr = jp.getAsString();
                try {
                    return new MUri(URI.create(jpstr).toString());
                } catch (Exception e) {
                    return new MStr(jpstr);
                }
            }
        } else if (json.isJsonArray()) {
            final JsonArray jp = (JsonArray) json;
            final List<Obj> list = new ArrayList<>();
            for (var j : jp.getAsJsonArray()) {
                list.add(translate(j));
            }
            return new MLst(list);
        } else if (json.isJsonObject()) {
            final JsonObject jp = (JsonObject) json;
            final Map<Obj, Obj> map = new LinkedHashMap<>();
            for (var kv : jp.getAsJsonObject().asMap().entrySet()) {
                map.put(new MUri(kv.getKey()), translate(kv.getValue()));
            }
            return new MRec(map);
        }
        throw new IllegalStateException("unknown type: " + json + "::" + json.getAsInt());
    }

    @Override
    public JsonElement translate(final Obj obj) {
        try {
            if (!obj.isPoly()) {
                return JsonParser.parseString(SERIALIZER.write(obj));
            } else if (obj.isLst()) {
                JsonArray array = new JsonArray();
                obj.lstValue().forEach(o -> array.add(translate(o)));
                return array;
            } else if (obj.isRec()) {
                JsonObject object = new JsonObject();
                obj.recValue().forEach((key, value) -> object.add(translate(key).toString(), translate(value)));
                return object;
            } else {
                throw new IllegalArgumentException("unable to jsonify " + obj);
            }
        } catch (final Exception e) {
            throw new IllegalArgumentException("could not parse %s to json".formatted(SERIALIZER.write(obj)));
        }
    }

    public Obj translateString(final String json) {
        try {
            return this.translate(JsonParser.parseString(json));
        } catch (final Exception e) {
            throw new IllegalArgumentException(json, e);
        }
    }
}
