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

package studio.phaseshift.metatron.lang.translator;

import com.google.gson.*;
import studio.phaseshift.metatron.lang.obj.BObj;
import studio.phaseshift.metatron.lang.obj.SObj;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JSONTranslator implements Translator<BObj.Obj, JsonElement> {
    @Override
    public BObj.Obj translate(final JsonElement json) {
        if (json.isJsonNull())
            return BObj.NoObj.of();
        else if (json.isJsonPrimitive()) {
            final JsonPrimitive jp = (JsonPrimitive) json;

            if (jp.isBoolean())
                return new SObj.Bool(jp.getAsBoolean());
            else if (jp.isNumber()) {
                return new SObj.Int(jp.getAsLong());
                /* if (jp.getAsNumber() instanceof Float)
                    return new SObj.Real(jp.getAsFloat());
                else if (jp.getAsNumber() instanceof Double)
                    return new SObj.Real(jp.getAsDouble());*/
            } else if (jp.isString())
                return new SObj.Str(jp.getAsString());
        } else if (json.isJsonArray()) {
            final JsonArray jp = (JsonArray) json;
            final List<BObj.Obj> list = new ArrayList<>();
            for (var j : jp.getAsJsonArray()) {
                list.add(translate(j));
            }
            return new SObj.Lst(list);
        } else if (json.isJsonObject()) {
            final JsonObject jp = (JsonObject) json;
            final Map<BObj.Obj, BObj.Obj> map = new LinkedHashMap<>();
            for (var kv : jp.getAsJsonObject().asMap().entrySet()) {
                map.put(new SObj.Uri(kv.getKey()), translate(kv.getValue()));
            }
            return new SObj.Rec(map);
        }
        throw new IllegalStateException("unknown type: " + json + "::" + json.getAsInt());
    }

    @Override
    public JsonElement translate(final BObj.Obj obj) {
        return null;
    }

    public BObj.Obj translateString(final String json) {
        return this.translate(JsonParser.parseString(json));
    }
}
