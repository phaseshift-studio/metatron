/*
 *   Metatron: A Distributed Virtual Machine
 *   Copyright (c) 2024 PhaseShift Studio, LLC
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.lang.translate;

import com.google.gson.*;
import studio.phaseshift.metatron.lang.obj.BObj;
import studio.phaseshift.metatron.lang.obj.Palette;
import studio.phaseshift.metatron.lang.obj.SObj;
import studio.phaseshift.metatron.ui.ObjSerializer;
import studio.phaseshift.metatron.ui.ObjStringSerializer;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.lang.obj.BObj.MTRON_CORE_TYPES;

public class JSONTranslator implements Translator<BObj.Obj, JsonElement> {
    private static final ObjSerializer<String> SERIALIZER = ObjStringSerializer
            .build()
            .simpleColon(true)
            .hideTypesMatching(MTRON_CORE_TYPES)
            .palette(Palette.NO_COLOR)
            .create();

    @Override
    public BObj.Obj translate(final JsonElement json) {
        if (json.isJsonNull())
            return BObj.NoObj.of();
        else if (json.isJsonPrimitive()) {
            final JsonPrimitive jp = (JsonPrimitive) json;
            if (jp.isBoolean())
                return SObj.Bool.of(jp.getAsBoolean());
            else if (jp.isNumber()) {
                if (jp.getAsString().contains("."))
                    return SObj.Real.of(jp.getAsDouble());
                else
                    return SObj.Int.of(jp.getAsLong());
            } else if (jp.isString()) {
                final String jpstr = jp.getAsString();
                try {
                    return SObj.Uri.of(URI.create(jpstr).toString());
                } catch (Exception e) {
                    return SObj.Str.of(jpstr);
                }
            }
        } else if (json.isJsonArray()) {
            final JsonArray jp = (JsonArray) json;
            final List<BObj.Obj> list = new ArrayList<>();
            for (var j : jp.getAsJsonArray()) {
                list.add(translate(j));
            }
            return SObj.Lst.of(list);
        } else if (json.isJsonObject()) {
            final JsonObject jp = (JsonObject) json;
            final Map<BObj.Obj, BObj.Obj> map = new LinkedHashMap<>();
            for (var kv : jp.getAsJsonObject().asMap().entrySet()) {
                map.put(SObj.Uri.of(kv.getKey()), translate(kv.getValue()));
            }
            return SObj.Rec.of(map);
        }
        throw new IllegalStateException("unknown type: " + json + "::" + json.getAsInt());
    }

    @Override
    public JsonElement translate(final BObj.Obj obj) {
        try {
            if (obj.isMono()) {
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

    public BObj.Obj translateString(final String json) {
        try {
            return this.translate(JsonParser.parseString(json));
        } catch (final Exception e) {
            throw new IllegalArgumentException(json, e);
        }
    }
}
