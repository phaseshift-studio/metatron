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

package studio.phaseshift.metatron.isa.mach.io.type;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import org.petitparser.context.Result;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.MTronException;

import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.BASE_TYPES;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MBytes.bytes;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.io.ioInstSet.OBJ_SIMPLE_JSON_SERIALIZER_VID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ObjSimpleJSONSerializer extends AbstractObjSerializer<JsonElement> {

    private static final GraphittyLogger LOG = Graphitty.log(ObjSimpleJSONSerializer.class);
    private static final Pattern HEX_PATTERN = Pattern.compile("^0x[0-9a-fA-F]+$");
    private static final String _TID = "_tid";
    private static final String _VID = "_vid";

    private final boolean biasTowardsURI = true;
    private final boolean biasTowardsObjs = false;
    private final boolean embedCandQ = false;

    private static JsonReader makeReader(final String json) {
        final JsonReader r = new JsonReader(new StringReader(json));
        r.setStrictness(Strictness.LENIENT);
        return r;
    }

    private static final ObjSimpleJSONSerializer INSTANCE = new ObjSimpleJSONSerializer();

    public static ObjSimpleJSONSerializer single() {
        return INSTANCE;
    }

    public ObjSimpleJSONSerializer() {
    }

    public static Obj parse(final String json) {
        return new ObjSimpleJSONSerializer().read(JsonParser.parseReader(makeReader(json)));
    }

    @Override
    public fURI vid() {
        return OBJ_SIMPLE_JSON_SERIALIZER_VID;
    }

    @Override
    public fURI jvm() {
        return OBJ_SIMPLE_JSON_SERIALIZER_VID;
    }

    @Override
    public ByteBuffer outputBytes(final Obj obj) throws MTronException {
        try {
            return ByteBuffer.wrap(this.write(obj).toString().getBytes(StandardCharsets.UTF_8));
        } catch (final Exception e) {
            LOG.warn("ignoring json write problem with %s: %s", obj, e);
            return ByteBuffer.wrap(JsonNull.INSTANCE.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    @Override
    public Obj inputBytes(ByteBuffer bytes) throws MTronException {
        try {
            return this.read(JsonParser.parseReader(ObjSimpleJSONSerializer.makeReader(new String(bytes.array(), StandardCharsets.UTF_8))));
        } catch (final Exception e) {
            LOG.warn("ignoring json parse problem with %s: %s", new String(bytes.array(), StandardCharsets.UTF_8), e);
            return NoObj.noobj();
        }
    }

    @Override
    public Obj read(final JsonElement json) throws MTronException {
        try {
            if (json.isJsonNull())
                return uri("null");
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
                    if (HEX_PATTERN.matcher(jp.getAsString()).matches())
                        return bytes(ByteBuffer.wrap(HexFormat.of().parseHex(jp.getAsString().substring(2))));
                    final String jpstr = jp.getAsString();
                    try {
                        if (jpstr.startsWith("<") && jpstr.endsWith(">"))
                            return uri(jpstr.substring(1, jpstr.length() - 1));
                        else if (!jpstr.contains(" ") && jpstr.contains("/") && !jpstr.contains("^") && !jpstr.contains("|"))
                            return uri(jpstr);
                        else if (this.biasTowardsURI && !jpstr.contains(" ") && !jpstr.contains("^") && !jpstr.contains("|"))
                            return uri(jpstr);
                    } catch (Exception e) {
                        // do nothiing
                    }
                    try {
                        final Result r = ObjmtronSerializer.parse(jpstr);
                        return r.isSuccess() ? r.get() : ObjmtronSerializer.parse(jpstr);
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
                    list.add(this.read(j));
                }
                return this.biasTowardsObjs ? objs(list) : lst(list);
            } else if (json.isJsonObject()) {
                fURI vid = null;
                fURI tid = null;
                final JsonObject jp = (JsonObject) json;
                if (this.embedCandQ) {
                    for (var kv : jp.getAsJsonObject().asMap().entrySet()) {
                        if (kv.getKey().equals(_TID))
                            tid = f(kv.getValue().getAsString());
                        else if (kv.getKey().equals(_VID))
                            vid = f(kv.getValue().getAsString());
                    }
                }
                if (null != tid || null != vid)
                    LOG.info("embedded tid/vid: %s/%s", tid, vid);
                final Map<Obj, Obj> map = new LinkedHashMap<>();
                for (var kv : jp.getAsJsonObject().asMap().entrySet()) {
                    if (!kv.getKey().equals(_TID) && !kv.getKey().equals(_VID))
                        map.put(uri(kv.getKey()), this.read(kv.getValue()));
                }
                return null == tid ? rec(map) : rec(map, tid, vid);
            }
            throw new IllegalStateException("unknown type: " + json + "::" + json.getAsInt());
        } catch (final Exception e) {
            try {
                return ObjmtronSerializer.parse(json.getAsString()).apply();
            } catch (final Exception e2) {
                LOG.warn("ignoring json parse problem with %s: %s", json, e);
                return NoObj.noobj();
            }
        }
    }

    @Override
    public JsonPrimitive writeBytes(final Bytes bytes) {
        return new JsonPrimitive(bytes.toHexString());
    }

    @Override
    public JsonNull writeNoObj(final NoObj noobj) {
        return JsonNull.INSTANCE;

    }

    @Override
    public JsonPrimitive writeBool(final Bool dool) {
        return new JsonPrimitive(dool.jvm());
    }

    @Override
    public JsonPrimitive writeFail(final Fail fail) {
        return new JsonPrimitive(fail.message().getMessage());
    }

    @Override
    public JsonPrimitive writeStr(final Str str) {
        final String string = str.jvm();
        //   final String quotes = string.contains("\n") ? "\"\"\"" : string.contains("'") ? "\"" : "'";
        return new JsonPrimitive(string);
    }

    @Override
    public JsonPrimitive writeInt(final Int jnt) {
        return new JsonPrimitive(jnt.jvm());
    }

    @Override
    public JsonPrimitive writeReal(final Real real) {
        return new JsonPrimitive(real.jvm());
    }

    @Override
    public JsonPrimitive writeUri(final Uri uri) {
        return new JsonPrimitive(uri.uriValue().toString());
    }

    @Override
    public JsonArray writeLst(final Lst lst) {
        JsonArray array = new JsonArray();
        // if (embedCandQ && !BASE_TYPES.contains(lst.tid()))
        //     array.add(new JsonPrimitive(lst.tid().toString()));
        lst.lstValue().forEach(o -> array.add(this.write(o)));
        return array;
    }

    @Override
    public JsonArray writeRel(final Rel rel) {
        JsonArray array = new JsonArray();
        array.add(this.write(rel.jvm().get0()));
        array.add(this.write(rel.jvm().get1()));
        return array;
    }

    @Override
    public JsonObject writeRec(final Rec rec) {
        JsonObject object = new JsonObject();
        rec.elements().forEach(rel -> object.add(rel.relValue().get0().toString(), this.write(rel.relValue().get1())));
        if (this.embedCandQ && (!BASE_TYPES.contains(rec.tid()) || rec.vid() != null)) {
            object.addProperty("_tid", rec.tid().toString());
            if (rec.vid() != null)
                object.addProperty("_vid", rec.vid().toString());
        }
        return object;
    }

    @Override
    public JsonPrimitive writeInst(final Inst inst) {
        return new JsonPrimitive(inst.toString());
    }

    @Override
    public JsonPrimitive writeCode(final Code code) {
        return new JsonPrimitive(code.toString());
    }

    @Override
    public JsonArray writeObjs(final Objs objs) {
        JsonArray array = new JsonArray();
        //if (embedCandQ)
        //    array.add(new JsonPrimitive(OBJS_TID.toString()));
        objs.stream().forEach(o -> array.add(this.write(o)));
        return array;
    }

    @Override
    public JsonPrimitive writeType(final Type type) {
        return new JsonPrimitive(type.toString());
    }


}
