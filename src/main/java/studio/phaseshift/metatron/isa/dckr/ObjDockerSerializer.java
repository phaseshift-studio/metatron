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

package studio.phaseshift.metatron.isa.dckr;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.math.mathInstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Str;
import studio.phaseshift.metatron.isa.mach.io.type.AbstractObjSerializer;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.web.parser.ObjJSONSerializer;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static studio.phaseshift.metatron.isa.dckr.dckrInstSet.DCKR_ISA_TID;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_DATA_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * Serializer that converts Docker NDJSON output into clean mtron {@link Rec} objects.
 * <p>
 * Docker-specific transformations:
 * <ul>
 *   <li>Keys: CamelCase → snake_case ({@code CreatedAt} → {@code created_at})</li>
 *   <li>Sentinel strings: {@code none}, {@code N/A} → {@code noobj}</li>
 *   <li>Timestamps: ISO-8601 / Docker format → {@code datetime::T}</li>
 *   <li>Sizes: {@code 227MB} → {@code mB::227.0}, {@code 4.61GB} → {@code gB::4.61}</li>
 *   <li>Labels: {@code key=val,key=val} → {@code rec::T}</li>
 *   <li>Plain strings: converted to URIs when possible, kept as {@code str::T} otherwise</li>
 * </ul>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ObjDockerSerializer extends AbstractObjSerializer<String> {

    public static final fURI OBJ_DOCKER_SERIALIZER_TID = DCKR_ISA_TID.extend("serializer/obj_docker");

    private static final ObjDockerSerializer INSTANCE = new ObjDockerSerializer();
    private static final ObjJSONSerializer JSON = ObjJSONSerializer.simple();

    private static final Pattern SIZE_PATTERN =
            Pattern.compile("^([\\d.]+)\\s*([KMGT]?B)$", Pattern.CASE_INSENSITIVE);

    public static ObjDockerSerializer single() {
        return INSTANCE;
    }

    public ObjDockerSerializer() {
        super(OBJ_DOCKER_SERIALIZER_TID, OBJ_DOCKER_SERIALIZER_TID);
    }

    // ===================================================================
    // Input: Docker NDJSON String → Obj
    // ===================================================================

    @Override
    public Obj read(final String jsonLine) {
        try {
            return clean(JSON.inputBytes(jsonLine));
        } catch (final Exception e) {
            throw MTronException.of(e, "unable to parse docker json: %s", jsonLine);
        }
    }

    @Override
    public Obj inputBytes(final ByteBuffer bytes) {
        return read(StandardCharsets.UTF_8.decode(bytes).toString());
    }

    // ===================================================================
    // Recursive cleaning
    // ===================================================================

    private static Obj clean(final Obj obj) {
        if (obj.isStr())
            return cleanString(Str.Helper.cleanString(obj, true).trim());
        if (obj.isUri())
            return cleanString(obj.uriValue().toString());
        if (obj.isInt())
            return obj;  // keep Docker integer values (e.g. container count) as-is
        if (obj.isRec()) {
            final Map<Obj, Obj> cleaned = new LinkedHashMap<>();
            obj.asRec().jvm().forEach((k, v) -> {
                final String rawKey = k.isUri() ? k.uriValue().name() : k.toString();
                final String key = toSnakeCase(rawKey);
                Obj value = clean(v);
                if ("labels".equals(key) && value.isStr())
                    value = parseLabels(Str.Helper.cleanString(value, true));
                else if ("labels".equals(key) && value.isUri())
                    value = parseLabels(value.uriValue().toString());
                cleaned.put(uri(key), value);
            });
            return rec(cleaned);
        }
        if (obj.isLst())
            return lst(obj.asLst().elements().map(ObjDockerSerializer::clean).toList());
        return obj;
    }

    private static Obj cleanString(final String s) {
        if (s.isEmpty() || "none".equalsIgnoreCase(s) || "N/A".equalsIgnoreCase(s))
            return noobj();
        // Docker size: "227MB" → mB::227.0
        final var m = SIZE_PATTERN.matcher(s);
        if (m.matches()) {
            final double val = Double.parseDouble(m.group(1));
            final String raw = m.group(2).toLowerCase();
            return real(val, MATH_DATA_TID.extend(raw.charAt(0) + "B"), null);
        }
        // Integer count: "0" → 0
        if (CommonUtil.isInt(s))
            return jnt(Long.parseLong(s));
        // Datetime
        try {
            return mathInstSet.parseDatetime(s);
        } catch (final Exception e1) { /* continue */ }
        // URI
        try {
            return uri(s);
        } catch (final Exception e2) {
            return str(s);
        }
    }

    // ===================================================================
    // Key & label helpers
    // ===================================================================

    /**
     * Convert CamelCase to snake_case: "CreatedAt" → "created_at".
     */
    static String toSnakeCase(final String camel) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < camel.length(); i++) {
            final char c = camel.charAt(i);
            if (Character.isUpperCase(c) && i > 0 && !Character.isUpperCase(camel.charAt(i - 1)))
                sb.append('_');
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    /**
     * Parse Docker label string "key=val,key=val" into a Rec.
     */
    private static Rec parseLabels(final String content) {
        final Rec labels = rec();
        for (final String pair : content.split(",")) {
            final int eq = pair.indexOf('=');
            if (eq > 0) {
                Obj p;
                try {
                    p = ObjmtronSerializer.parse(pair.substring(eq + 1).trim());
                } catch (final Exception e) {
                    p = Str.Helper.toUriOrStr(pair.substring(eq + 1).trim(), true);
                }
                labels.at(uri(pair.substring(0, eq).trim()), p, MUTABLE);
            }
        }
        return labels;
    }

    // ===================================================================
    // Output direction (write mtron → Docker JSON string) — stub
    // ===================================================================

    @Override
    public String write(final Obj obj) {
        return new String(JSON.outputBytes(obj).array());
    }
}
