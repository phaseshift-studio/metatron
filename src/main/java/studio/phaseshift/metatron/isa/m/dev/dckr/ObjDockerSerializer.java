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

package studio.phaseshift.metatron.isa.m.dev.dckr;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.math.mathInstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Str;
import studio.phaseshift.metatron.isa.m.web.parser.ObjJSONSerializer;
import studio.phaseshift.metatron.isa.m.web.type.AbstractObjSerializer;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static studio.phaseshift.metatron.Tokens.ACTION;
import static studio.phaseshift.metatron.Tokens.ACTOR;
import static studio.phaseshift.metatron.Tokens.ATTRIBUTES;
import static studio.phaseshift.metatron.Tokens.EXIT;
import static studio.phaseshift.metatron.Tokens.ID;
import static studio.phaseshift.metatron.Tokens.NAME;
import static studio.phaseshift.metatron.Tokens.OBJ;
import static studio.phaseshift.metatron.Tokens.TIME;
import static studio.phaseshift.metatron.Tokens.TYPE;
import static studio.phaseshift.metatron.isa.m.dev.dckr.dckrInstSet.DCKR_ISA_TID;
import static studio.phaseshift.metatron.isa.m.dev.dckr.dckrInstSet.DOCKER_EVENT_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.AUTO_FROM_INST_TID;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_DATASIZE_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
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
    private static final ObjJSONSerializer OBJ_JSON_SERIALIZER = ObjJSONSerializer.literal();

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
            return clean(OBJ_JSON_SERIALIZER.inputBytes(jsonLine));
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
        return clean(obj, null);
    }

    /**
     * The field a value came from is the type information this conversion needs: docker reports every field as a
     * JSON string, so nothing about {@code "16"} says whether it is a count or a tag. Only the fields that are
     * counts are read as numbers (see {@link #COUNT_FIELDS}); everything else keeps its text.
     */
    private static Obj clean(final Obj obj, final String field) {
        if (obj.isStr())
            return cleanString(Str.Helper.cleanString(obj, true).trim(), field);
        if (obj.isUri())
            return cleanString(obj.uriValue().toString(), field);
        if (obj.isInt())
            return obj;  // keep Docker integer values (e.g. container count) as-is
        if (obj.isRec()) {
            // actor.Attributes is a flat string map (the same shape as docker labels) — re-stringify from the
            // raw value, never coerce: literal() already uri-ified "24.04" and "7", and re-typing them again
            // would lose the text exactly as the label bug did.
            if ("attributes".equals(field))
                return stringMap(obj.asRec());
            final Map<Obj, Obj> cleaned = new LinkedHashMap<>();
            obj.asRec().jvm().forEach((k, v) -> {
                final String rawKey = k.isUri() ? k.uriValue().name() : k.toString();
                final String key = toSnakeCase(rawKey);
                Obj value = clean(v, key);
                if ("labels".equals(key) && value.isStr())
                    value = parseLabels(Str.Helper.cleanString(value, true));
                else if ("labels".equals(key) && value.isUri())
                    value = parseLabels(value.uriValue().toString());
                else if (("mounts".equals(key) || "networks".equals(key)) && (value.isStr() || value.isUri()))
                    value = splitCsv(value, true);
                else if ("ports".equals(key) && (value.isStr() || value.isUri()))
                    value = splitCsv(value, false);
                cleaned.put(uri(key), value);
            });
            return rec(cleaned);
        }
        if (obj.isLst())
            return lst(obj.asLst().elements().map(ObjDockerSerializer::clean).toList());
        return obj;
    }

    /**
     * Fields whose value is a name rather than a number, so a numeric-looking one keeps its text: docker reports
     * {@code "Tag":"16"} for postgres:16, and reading that as the integer 16 loses the tag.
     */
    private static final java.util.Set<String> NAME_FIELDS =
            java.util.Set.of("tag", "repository", "id", "image", "names", "name", "status", "state", "command",
                    "mounts", "mount", "ports", "networks", "network", "created_since", "digest");

    // TODO: this should go to ObjDockerSerializer
    private static Obj cleanString(final String s, final String field) {
        if (s.isEmpty() || "none".equalsIgnoreCase(s) || "N/A".equalsIgnoreCase(s))
            return noobj();
        // Docker size: "227MB" → mB::227.0
        final var m = SIZE_PATTERN.matcher(s);
        if (m.matches()) {
            final double val = Double.parseDouble(m.group(1));
            final String raw = m.group(2).toLowerCase();
            return real(val, MATH_DATASIZE_TID.extend(raw.charAt(0) + "B"), null);
        }
        // Integer count — for the fields that *are* counts. A tag stays text even when it is numeric: postgres:16
        // is a name, and reading it as 16 loses that exactly as reading "11.2" as a real did (a number renders
        // bare, so afterwards the two cannot be told apart).
        // a value inside a list has no field name of its own
        final boolean named = null != field && NAME_FIELDS.contains(field);
        if (!named && CommonUtil.isInt(s))
            return jnt(Long.parseLong(s));
        // Datetime
        try {
            return mathInstSet.parseDatetime(s);
        } catch (final Exception e1) { /* continue */ }
        if (s.contains(" ") || s.contains("\n"))
            return str(s);
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
     * <p>
     * A LABEL VALUE IS A {@code str::T}. Docker labels are arbitrary text — image descriptions, licenses, versions,
     * timestamps, source urls, compose file paths — and reading them as mtron was wrong twice over. It was LOSSY (a
     * version like {@code 11.2.6} is not a real, a tag like {@code 16} is not the integer 16, and a number renders
     * bare, so the text could not be recovered afterwards), and it was EXPENSIVE IN THE LEDGER: a value that is not an
     * mtron literal produced a parse ERROR, a parse error is a fail, and a fail FILES ITSELF in the machine's fail
     * stack ({@code MFail.fail} → {@code incrStackWrap} writes {@code ~/sys/fail/_?incrq}). Two attempts per label,
     * re-read on every refresh, is how a machine that had never failed showed a fail stack in the thousands. A label
     * is a string; reading it as one says exactly what docker said.
     */
    private static Rec parseLabels(final String content) {
        final Rec labels = rec();
        for (final String pair : content.split(",")) {
            final int eq = pair.indexOf('=');
            if (eq > 0)
                labels.at(uri(pair.substring(0, eq).trim()), str(pair.substring(eq + 1).trim()), MUTABLE);
        }
        return labels;
    }

    /**
     * A docker attribute map is a flat string map — the same shape as {@link #parseLabels(String)} but already a
     * rec on the wire ({@code actor.Attributes}). Every value is text: an image tag, a name, an exit code, a
     * source path. Re-stringify from the raw value rather than coerce it, so {@code 24.04} stays {@code 24.04} and
     * {@code 7} stays {@code 7} — the first-class {@code exit} field is the one place a number is surfaced as an int.
     */
    private static Rec stringMap(final Rec attributes) {
        final Map<Obj, Obj> out = new LinkedHashMap<>();
        attributes.jvm().forEach((k, v) -> out.put(k, str(Str.Helper.cleanString(v, true))));
        return rec(out);
    }

    /**
     * Docker reports {@code Mounts}/{@code Networks}/{@code Ports} as comma-joined strings. Split them into a stream
     * here — this is parsing, so it belongs in the serializer, not the space. A path-like source becomes a uri; a
     * port spec (which carries {@code ->}) stays text.
     */
    private static Obj splitCsv(final Obj csv, final boolean toUri) {
        final List<Obj> parts = new ArrayList<>();
        for (final String part : Str.Helper.cleanString(csv, true).split(",")) {
            final String p = part.trim();
            if (p.isEmpty()) continue;
            if (toUri) {
                try {
                    parts.add(uri(p));
                } catch (final Exception e) {
                    parts.add(str(p));
                }
            } else
                parts.add(str(p));
        }
        return objs(parts);
    }

    /**
     * Read one NDJSON line of {@code docker events --format json} as a lean, typed {@code dckrevent::T}.
     * <p>
     * The event's meaning is {@code type} (uri), {@code action} (a str — docker puts the exec command here for
     * {@code exec_start}/{@code exec_die}, so it is not an address), {@code exit} (the exit code — inline because
     * only the event carries pass/fail), and {@code time} (a true {@code datetime::T}). {@code obj} is an
     * auto-dereferencing {@code !*} reference into the container, keyed by NAME (the space's container key), so a
     * filter walks straight off the event into the subject's graph. Everything else docker reports is dropped.
     */
    public Obj readEvent(final String jsonLine) {
        final Rec rec = (Rec) read(jsonLine);
        final Obj type = rec.at(uri(TYPE));
        final Obj actionRaw = rec.at(uri(ACTION));
        final Obj action = actionRaw.isNoObj() ? noobj() : str(Str.Helper.cleanString(actionRaw, true));
        final Obj actor = rec.at(uri(ACTOR));
        Obj exit = noobj();
        String actorName = null;
        if (actor.isRec()) {
            final Obj attrs = actor.asRec().at(uri(ATTRIBUTES));
            if (attrs.isRec()) {
                final Obj name = attrs.asRec().at(uri(NAME));
                if (!name.isNoObj())
                    actorName = Str.Helper.cleanString(name, true);
                final Obj exitCode = attrs.asRec().at(uri("exitCode"));
                final String exitText = exitCode.isNoObj() ? null : Str.Helper.cleanString(exitCode, true);
                if (null != exitText && CommonUtil.isInt(exitText))
                    exit = jnt(Long.parseLong(exitText));
            }
        }
        // time: docker reports `time` (epoch seconds) and `timeNano` (full epoch nanos); the datetime comes from
        // the nanos so sub-second precision survives — timeNano is NOT the nanosecond-of-second.
        final long nanos = rec.at(uri("time_nano")).isInt() ? rec.at(uri("time_nano")).intValue()
                : rec.at(uri(TIME)).isInt() ? rec.at(uri(TIME)).intValue() * 1_000_000_000L : 0L;
        final Obj time = mathInstSet.buildDatetimeUri(Instant.ofEpochSecond(0, nanos).atZone(ZoneId.systemDefault()));
        Obj obj = noobj();
        if (null != actorName)
            obj = instB(AUTO_FROM_INST_TID, lst(uri("docker:container/" + actorName)));
        final Map<Obj, Obj> map = new LinkedHashMap<>();
        map.put(uri(TYPE), type);
        map.put(uri(ACTION), action);
        if (!exit.isNoObj())
            map.put(uri(EXIT), exit);
        map.put(uri(TIME), time);
        if (!obj.isNoObj())
            map.put(uri(OBJ), obj);
        return rec(map, DOCKER_EVENT_TID, null);
    }

    /**
     * The event's {@code timeNano} (full epoch nanos) — for the space's watermark, not the event's meaning. It is
     * deliberately NOT part of {@link #readEvent(String)}'s rec: the watermark needs nanosecond precision, but the
     * event's {@code time} is a human/query-facing {@code datetime::T}.
     */
    public long eventTimeNano(final String jsonLine) {
        final Obj parsed = OBJ_JSON_SERIALIZER.inputBytes(jsonLine);
        final Obj timeNano = parsed.isRec() ? parsed.asRec().at(uri("timeNano")) : noobj();
        return timeNano.isInt() ? timeNano.intValue() : 0L;
    }

    // ===================================================================
    // Output direction (write mtron → Docker JSON string) — stub
    // ===================================================================

    @Override
    public String write(final Obj obj) {
        return new String(OBJ_JSON_SERIALIZER.outputBytes(obj).array());
    }
}
