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

package studio.phaseshift.metatron.isa.mach.io.type;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Bytes;
import studio.phaseshift.metatron.isa.m.type.NoObj;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.web.webInstSet;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.isa.m.mInstSet.LST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_TRUE;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * Bidirectional YAML serializer using SnakeYAML.
 * <p>
 * Write direction: Obj → Java Map/List/scalar → YAML string.
 * Read direction: YAML string → Java Map/List/scalar → Obj.
 * <p>
 * Rec keys are emitted as plain strings (not URI-quoted) for
 * compatibility with tools that consume standard YAML (docker-compose, K8s, etc.).
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ObjYAMLSerializer extends AbstractObjSerializer<String> {

    public static final fURI OBJ_YAML_SERIALIZER_TID = webInstSet.OBJ_YAML_SERIALIZER_TID;

    private static final ObjYAMLSerializer INSTANCE = new ObjYAMLSerializer();

    private final DumperOptions dumperOptions;

    public static ObjYAMLSerializer single() {
        return INSTANCE;
    }

    public ObjYAMLSerializer(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
        this.dumperOptions = new DumperOptions();
        this.dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        this.dumperOptions.setIndent(jvm.getOrDefault(uri("indent"), jnt(2)).intValue().intValue());
        this.dumperOptions.setPrettyFlow(jvm.getOrDefault(uri("pretty"), BOOL_TRUE).boolValue());
    }

    public ObjYAMLSerializer() {
        this(mutableMap(uri("indent"), jnt(2), uri("pretty"), BOOL_TRUE), OBJ_YAML_SERIALIZER_TID, OBJ_YAML_SERIALIZER_TID);
    }

    public static ObjYAMLSerializer of(final Map<Obj, Obj> jvm) {
        return new ObjYAMLSerializer(jvm, OBJ_YAML_SERIALIZER_TID, null);
    }

    // =======================================================================
    // Output: Obj → YAML String
    // =======================================================================

    @Override
    public String write(final Obj obj) {
        if (obj.isNoObj())
            return null;
        return new Yaml(dumperOptions).dump(toJava(obj)).trim();
    }

    /**
     * Convert an mtron Obj tree into a Java object tree (Map, List, scalar)
     * suitable for SnakeYAML's {@code dump()}.
     */
    private static Object toJava(final Obj obj) {
        if (obj.isNoObj())
            return null;
        if (obj.isRec()) {
            final Map<String, Object> map = new LinkedHashMap<>();
            obj.asRec().jvm().forEach((key, value) -> {
                final String k = key.isUri() ? key.asUri().uriValue().name() : key.toString();
                map.put(k, toJava(value));
            });
            return map;
        }
        if (obj.isLst()) {
            final List<Object> list = new ArrayList<>();
            obj.asLst().elements().forEach(e -> list.add(toJava(e)));
            return list;
        }
        if (obj.isRel())
            return List.of(toJava(obj.asRel().first()), toJava(obj.asRel().second()));
        if (obj.isStr())
            return obj.strValue();
        if (obj.isInt())
            return obj.intValue();
        if (obj.isReal())
            return obj.realValue();
        if (obj.isBool())
            return obj.boolValue();
        if (obj.isUri())
            return obj.uriValue().toString();
        if (obj.isBytes())
            return obj.<Bytes>as().toHexString();
        // fallback: string representation
        return obj.toString();
    }

    // =======================================================================
    // Input: YAML String → Obj
    // =======================================================================

    @Override
    public Obj read(final String yamlStr) {
        if (yamlStr == null || yamlStr.isBlank())
            return NoObj.noobj();
        final Object java = new Yaml().load(yamlStr);
        return fromJava(java);
    }

    @Override
    public Obj inputBytes(final ByteBuffer bytes) {
        return read(StandardCharsets.UTF_8.decode(bytes).toString());
    }

    @Override
    public ByteBuffer outputBytes(final Obj obj) {
        final String yaml = write(obj);
        if (yaml == null)
            return ByteBuffer.allocate(0);
        return ByteBuffer.wrap(yaml.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Convert a Java object tree (from SnakeYAML's {@code load()}) back into
     * an mtron Obj tree.
     */
    @SuppressWarnings("unchecked")
    private static Obj fromJava(final Object java) {
        if (java == null)
            return NoObj.noobj();
        if (java instanceof Map) {
            final Map<Obj, Obj> map = new LinkedHashMap<>();
            ((Map<String, Object>) java).forEach((k, v) ->
                    map.put(uri(k), fromJava(v)));
            return rec(map, REC_TID, null);
        }
        if (java instanceof List) {
            final List<Obj> list = new ArrayList<>();
            ((List<Object>) java).forEach(e -> list.add(fromJava(e)));
            return lst(list, LST_TID, null);
        }
        if (java instanceof Boolean)
            return bool((Boolean) java);
        if (java instanceof Integer || java instanceof Long)
            return jnt(((Number) java).longValue());
        if (java instanceof Double || java instanceof Float)
            return real(((Number) java).doubleValue());
        if (java instanceof String)
            return str((String) java);
        return str(java.toString());
    }

}
