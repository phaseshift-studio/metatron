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

package studio.phaseshift.metatron.isa.m.math;

import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Real;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.type.Uri;
import studio.phaseshift.metatron.util.MTronException;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.as_;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.id_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Real.REAL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@JREService(vid = "/m/math")
public class mathInstSet extends AbstractInstSet {

    public static final fURI MATH_ISA_TID = M_ISA_TID.extend("math");
    public static final fURI MATH_INST_TID = MATH_ISA_TID.extend("inst");
    public static final fURI MATH_COS_INST_TID = MATH_INST_TID.extend("cos");
    public static final fURI MATH_SIN_INST_TID = MATH_INST_TID.extend("sin");
    public static final fURI MATH_TAN_INST_TID = MATH_INST_TID.extend("tan");
    public static final fURI MATH_SQRT_INST_TID = MATH_INST_TID.extend("sqrt");
    public static final fURI MATH_ATAN_INST_TID = MATH_INST_TID.extend("atan");
    public static final fURI MATH_ATAN2_INST_TID = MATH_INST_TID.extend("atan2");
    public static final fURI MATH_LOG_INST_TID = MATH_INST_TID.extend("log");
    public static final fURI MATH_LOG10_INST_TID = MATH_INST_TID.extend("log10");
    public static final fURI MATH_EXP_INST_TID = MATH_INST_TID.extend("exp");
    public static final fURI MATH_ABS_INST_TID = MATH_INST_TID.extend("abs");
    public static final fURI MATH_CEIL_INST_TID = MATH_INST_TID.extend("ceil");
    public static final fURI MATH_FLOOR_INST_TID = MATH_INST_TID.extend("floor");
    public static final fURI MATH_ROUND_INST_TID = MATH_INST_TID.extend("round");
    public static final fURI MATH_POW_INST_TID = MATH_INST_TID.extend("pow");
    public static final fURI MATH_DATA_TID = MATH_ISA_TID.extend("data");
    public static final fURI MATH_BYTE_TID = MATH_DATA_TID.extend("bB");
    public static final fURI MATH_KBYTE_TID = MATH_DATA_TID.extend("kB");
    public static final fURI MATH_MBYTE_TID = MATH_DATA_TID.extend("mB");
    public static final fURI MATH_GBYTE_TID = MATH_DATA_TID.extend("gB");
    public static final fURI MATH_TBYTE_TID = MATH_DATA_TID.extend("tB");
    public static final fURI MATH_PBYTE_TID = MATH_DATA_TID.extend("pB");
    public static final String MATH_BYTE_STRING = "/m/math/data/bB";
    public static final String MATH_KBYTE_STRING = "/m/math/data/kB";
    public static final String MATH_MBYTE_STRING = "/m/math/data/mB";
    public static final String MATH_GBYTE_STRING = "/m/math/data/gB";
    public static final String MATH_TBYTE_STRING = "/m/math/data/tB";
    public static final String MATH_PBYTE_STRING = "/m/math/data/pB";
    /// ///////////////////////
    public static final fURI MATH_TIME_TID = MATH_ISA_TID.extend("time");
    public static final fURI MATH_NOW_INST_TID = MATH_INST_TID.extend("now");
    public static final fURI MATH_MILLIS_TID = MATH_TIME_TID.extend("millis");
    public static final fURI MATH_SECOND_TID = MATH_TIME_TID.extend("second");
    public static final fURI MATH_MINUTE_TID = MATH_TIME_TID.extend("minute");
    public static final fURI MATH_HOUR_TID = MATH_TIME_TID.extend("hour");
    public static final String MATH_MILLIS_STRING = "/m/math/time/millis";
    public static final String MATH_SECOND_STRING = "/m/math/time/second";
    public static final String MATH_MINUTE_STRING = "/m/math/time/minute";
    public static final String MATH_HOUR_STRING = "/m/math/time/hour";
    /// ///////////////////////
    public static final fURI MATH_DATETIME_TID = MATH_ISA_TID.extend("datetime");
    public static final fURI MATH_DATETIME_NOW_INST_TID = MATH_INST_TID.extend("datetime_now");
    /// ///////////////////////
    public static final fURI MATH_CURRENCY_TID = f("/m/math/currency");
    public static final fURI MATH_USD_TID = MATH_CURRENCY_TID.extend("usd");
    public static final fURI MATH_EURO_TID = MATH_CURRENCY_TID.extend("euro");
    public static final Type MATH_CURRENCY_TYPE = Type.Builder.build()
            .tid(REAL_TID)
            .vid(MATH_CURRENCY_TID)
            .isaPredicate(REAL_TYPE) // TODO: fix
            .create();

    static {
        assert MATH_BYTE_STRING.equals(MATH_BYTE_TID.toString());
        assert MATH_KBYTE_STRING.equals(MATH_KBYTE_TID.toString());
        assert MATH_MBYTE_STRING.equals(MATH_MBYTE_TID.toString());
        assert MATH_GBYTE_STRING.equals(MATH_GBYTE_TID.toString());
        assert MATH_TBYTE_STRING.equals(MATH_TBYTE_TID.toString());
        assert MATH_PBYTE_STRING.equals(MATH_PBYTE_TID.toString());
    }


    public mathInstSet() {
        super(mutableMap(uri(PATTERN), uri(MATH_ISA_TID.extend(HASH_FURI))), INSTSET_TID, MATH_ISA_TID);
    }

    // TODO: date::T
    // //2006.01:23/01/32/34/999?tz=-0500
    /*public static final Type DATE_TYPE = Type.Builder.build()
            .tid(URI_TID)
            .vid(MATH_DATE_TID)
            .isaPredicate(uri("/${time}/${day}/${month}/${year}"))
            .create();*/

    public static final Type TIME_TYPE = Type.Builder.build()
            .tid(REAL_TID)
            .vid(MATH_TIME_TID)
            .predicate(id_().tryToInst())
            .create();

    public static final Type MILLIS_TYPE = Type.Builder.build()
            .tid(MATH_TIME_TID)
            .vid(MATH_MILLIS_TID)
            .constructor(arg -> {
                final String tid = arg.tid().toString();
                return switch (tid) {
                    case MATH_SECOND_STRING -> arg.jvm(arg.asReal().jvm() * 1000.0d);
                    case MATH_MINUTE_STRING -> arg.jvm(arg.asReal().jvm() * 1000.0d * 60.0d);
                    case MATH_HOUR_STRING -> arg.jvm(arg.asReal().jvm() * 1000.0d * 60.0d * 60.0d);
                    default -> arg;
                };
            }).create();

    public static final Type SECOND_TYPE = Type.Builder.build()
            .tid(MATH_TIME_TID)
            .vid(MATH_SECOND_TID)
            .constructor(arg -> {
                final String tid = arg.tid().toString();
                return switch (tid) {
                    case MATH_MILLIS_STRING -> arg.jvm(arg.asReal().jvm() / 1000.0d);
                    case MATH_MINUTE_STRING -> arg.jvm(arg.asReal().jvm() * 60.0d);
                    case MATH_HOUR_STRING -> arg.jvm(arg.asReal().jvm() * 60.0d * 60.0d);
                    default -> arg;
                };
            }).create();

    public static final Type MINUTE_TYPE = Type.Builder.build()
            .tid(MATH_TIME_TID)
            .vid(MATH_MINUTE_TID)
            .constructor(arg -> {
                final String tid = arg.tid().toString();
                return switch (tid) {
                    case MATH_MILLIS_STRING -> arg.jvm(arg.asReal().jvm() / 60.0d / 1000.0d);
                    case MATH_SECOND_STRING -> arg.jvm(arg.asReal().jvm() / 60.0d);
                    case MATH_HOUR_STRING -> arg.jvm(arg.asReal().jvm() * 60.0d);
                    default -> arg;
                };
            }).create();

    public static final Type HOUR_TYPE = Type.Builder.build()
            .tid(MATH_TIME_TID)
            .vid(MATH_HOUR_TID)
            .constructor(arg -> {
                final String tid = arg.tid().toString();
                return switch (tid) {
                    case MATH_MILLIS_STRING -> arg.jvm(arg.asReal().jvm() / 60.0d / 60.0d / 1000.0d);
                    case MATH_SECOND_STRING -> arg.jvm(arg.asReal().jvm() / 60.0d / 60.0d);
                    case MATH_MINUTE_STRING -> arg.jvm(arg.asReal().jvm() / 60.0d);
                    default -> arg;
                };
            }).create();

    /**
     * DateTime is a uri::T with the structure:
     * <pre>//yyyy.MM:dd/HH/mm/ss/SSS?tz=±HHmm</pre>
     * <ul>
     *   <li>host = year.month</li>
     *   <li>port = day (1–31)</li>
     *   <li>path[0] = hour (0–23)</li>
     *   <li>path[1] = minute (0–59)</li>
     *   <li>path[2] = second (0–59)</li>
     *   <li>path[3] = millisecond (0–999)</li>
     *   <li>query tz = timezone offset (e.g., -0500)</li>
     * </ul>
     */
    private static final Pattern DT_HOST_PATTERN = Pattern.compile("\\d{4}\\.\\d{2}");

    public static final Type DATETIME_TYPE = Type.Builder.build()
            .tid(URI_TID)
            .vid(MATH_DATETIME_TID)
            .predicate((lhs, inst) -> {
                final fURI dt = inst.arg(0).asUri().uriValue();
                if (dt.hasScheme() && dt.scheme() != null && !dt.scheme().isEmpty())
                    return noobj();
                if (!dt.hasHost() || !DT_HOST_PATTERN.matcher(dt.host()).matches())
                    return noobj();
                final int month = Integer.parseInt(dt.host().substring(5, 7));
                if (month < 1 || month > 12) return noobj();
                if (!dt.hasPort()) return noobj();
                final int day = dt.port();
                if (day < 1 || day > 31) return noobj();
                final List<String> path = dt.path();
                if (path.size() < 4) return noobj();
                try {
                    final int hour = Integer.parseInt(path.get(path.size() - 4));
                    if (hour < 0 || hour > 23) return noobj();
                    final int minute = Integer.parseInt(path.get(path.size() - 3));
                    if (minute < 0 || minute > 59) return noobj();
                    final int second = Integer.parseInt(path.get(path.size() - 2));
                    if (second < 0 || second > 59) return noobj();
                    Integer.parseInt(path.getLast()); // millis: any int OK
                } catch (NumberFormatException e) {
                    return noobj();
                }
                if (!dt.qMap().containsKey("tz")) return noobj();
                return inst.arg(0);
            })
            .create();

    /**
     * Creates a {@link Uri} representing the current system datetime.
     * Format: {@code //yyyy.MM:dd/HH/mm/ss/SSS?tz=±HHmm}
     */
    public static Uri nowDatetime() {
        final ZonedDateTime now = ZonedDateTime.now();
        final String year = String.format("%04d", now.getYear());
        final String month = String.format("%02d", now.getMonthValue());
        final String day = String.format("%02d", now.getDayOfMonth());
        final String hour = String.format("%02d", now.getHour());
        final String minute = String.format("%02d", now.getMinute());
        final String second = String.format("%02d", now.getSecond());
        final String millis = String.format("%03d", now.getNano() / 1_000_000);
        final String tz = now.getOffset().getId(); // "+HH:MM" or "-HH:MM"
        final String tzCompact = tz.replace(":", ""); // "+HHMM" or "-HHMM"
        final String host = year + "." + month;
        final fURI furi = fURI.of(
                null,          // scheme
                host,          // host = year.month
                Integer.parseInt(day),  // port = day
                List.of(hour, minute, second, millis),  // path
                null, null,    // coefficient, poly
                Map.of("tz", tzCompact),  // query
                null           // fragment
        );
        return uri(furi, MATH_DATETIME_TID, null);
    }

    private static final java.util.regex.Pattern DT_PARSE =
            java.util.regex.Pattern.compile(
                    "(\\d{4})-(\\d{2})-(\\d{2})[ T](\\d{2}):(\\d{2}):(\\d{2})" +  // date + time
                            "(?:\\.(\\d{1,3}))?" +                                            // optional .SSS
                            "\\s*(?:Z|([+-])(\\d{2}):?(\\d{2}))?");                         // Z or ±HH:MM or ±HHMM

    /**
     * Parse an ISO-8601 or Docker-format datetime string into a datetime URI.
     * Supports {@code "2026-08-01T23:37:33-06:00"}, {@code "2026-08-01 23:37:33 -0600 MDT"}, etc.
     */
    public static Uri parseDatetime(final String input) {
        try {
            final ZonedDateTime zdt = ZonedDateTime.parse(input);
            return buildDatetimeUri(zdt);
        } catch (final Exception e) { /* try other formats */ }
        try {
            // Date-only: "2024-12-25" → midnight UTC
            final java.time.LocalDate ld = java.time.LocalDate.parse(input);
            return buildDatetimeUri(ld.getYear(), ld.getMonthValue(), ld.getDayOfMonth(),
                    0, 0, 0, 0, "+0000");
        } catch (final Exception e) { /* try custom parse */ }
        final var m = DT_PARSE.matcher(input.trim());
        if (!m.find()) throw studio.phaseshift.metatron.util.MTronException.of("unable to parse datetime: %s", input);
        final int year = Integer.parseInt(m.group(1));
        final int month = Integer.parseInt(m.group(2));
        final int day = Integer.parseInt(m.group(3));
        final int hour = Integer.parseInt(m.group(4));
        final int minute = Integer.parseInt(m.group(5));
        final int second = Integer.parseInt(m.group(6));
        final int millis = m.group(7) != null ? Integer.parseInt(m.group(7)) : 0;
        final String tzSign = m.group(8) != null ? m.group(8) : "+";
        final String tzHour = m.group(9) != null ? m.group(9) : "00";
        final String tzMin = m.group(10) != null ? m.group(10) : "00";
        return buildDatetimeUri(year, month, day, hour, minute, second, millis,
                tzSign + String.format("%02d", Integer.parseInt(tzHour)) + String.format("%02d", Integer.parseInt(tzMin)));
    }

    /**
     * Build a datetime URI from components.
     */
    public static Uri buildDatetimeUri(final ZonedDateTime zdt) {
        String tz = zdt.getOffset().getId().replace(":", "");
        if ("Z".equals(tz)) tz = "+0000";
        return buildDatetimeUri(zdt.getYear(), zdt.getMonthValue(), zdt.getDayOfMonth(),
                zdt.getHour(), zdt.getMinute(), zdt.getSecond(), zdt.getNano() / 1_000_000, tz);
    }

    private static Uri buildDatetimeUri(final int year, final int month, final int day,
                                        final int hour, final int minute, final int second, final int millis, final String tz) {
        return uri(fURI.of(null,
                String.format("%04d.%02d", year, month), day,
                List.of(String.format("%02d", hour), String.format("%02d", minute),
                        String.format("%02d", second), String.format("%03d", millis)),
                null, null, Map.of("tz", tz), null), MATH_DATETIME_TID, null);
    }

    /**
     * Date-time formatter: Monday, August 9, 2026 02:14:24 PM +00:00
     */
    private static final DateTimeFormatter HUMAN_DTF = new DateTimeFormatterBuilder()
            .appendPattern("EEEE, MMMM d, yyyy hh:mm:ss a")
            .appendLiteral(' ')
            .appendOffset("+HH:MM", "+00:00")
            .toFormatter();

    /**
     * Convert a datetime URI to a human-readable string like
     * {@code Monday, August 9, 2026 10:14:24 AM UTC}.
     */
    public static String humanReadableDatetime(final studio.phaseshift.metatron.isa.m.type.Uri dt) {
        final fURI furi = dt.uriValue();
        final String[] hostParts = furi.host().split("\\.");
        final int year = Integer.parseInt(hostParts[0]);
        final int month = Integer.parseInt(hostParts[1]);
        final int day = furi.port();
        final var path = furi.path();
        // Leading / produces an empty first segment: skip it
        final int off = path.get(0).isEmpty() ? 1 : 0;
        final int hour = Integer.parseInt(path.get(off));
        final int minute = Integer.parseInt(path.get(off + 1));
        final int second = Integer.parseInt(path.get(off + 2));
        final int millis = Integer.parseInt(path.get(off + 3));
        final String tzStr = dt.uriValue().hasQ() && dt.uriValue().qMap().containsKey("tz")
                ? dt.uriValue().qMap().get("tz") : "+0000";
        final ZoneOffset offset = ZoneOffset.of(tzStr);
        final ZonedDateTime zdt = ZonedDateTime.of(year, month, day, hour, minute, second,
                millis * 1_000_000, offset);
        return zdt.format(HUMAN_DTF);
    }

    /**
     * Convert a {@code datetime::T} URI to epoch millis.  Supports the structured form
     * {@code //yyyy.MM:dd/HH/mm/ss/SSS?tz=±HHmm} and the millis shorthand
     * {@code datetime://<epoch_millis>}.
     */
    public static long datetimeToMillis(final Uri dt) {
        final fURI furi = dt.uriValue();
        if (furi.host() != null && furi.host().matches("\\d+"))
            return Long.parseLong(furi.host()); // millis shorthand
        final String[] hostParts = furi.host().split("\\.");
        final int year = Integer.parseInt(hostParts[0]);
        final int month = Integer.parseInt(hostParts[1]);
        final int day = furi.port();
        final var path = furi.path();
        final int off = path.get(0).isEmpty() ? 1 : 0;
        final int hour = Integer.parseInt(path.get(off));
        final int minute = Integer.parseInt(path.get(off + 1));
        final int second = Integer.parseInt(path.get(off + 2));
        final int millis = Integer.parseInt(path.get(off + 3));
        final String tzStr = furi.hasQ() && furi.qMap().containsKey("tz") ? furi.qMap().get("tz") : "+0000";
        return ZonedDateTime.of(year, month, day, hour, minute, second, millis * 1_000_000, ZoneOffset.of(tzStr))
                .toInstant().toEpochMilli();
    }

    /**
     * Convert a {@code time::T} (millis/second/minute/hour) to milliseconds.
     */
    private static double timeToMillis(final Obj time) {
        return switch (time.tid().basePath().toString()) {
            case MATH_MILLIS_STRING -> time.asReal().jvm();
            case MATH_SECOND_STRING -> time.asReal().jvm() * 1000.0d;
            case MATH_MINUTE_STRING -> time.asReal().jvm() * 1000.0d * 60.0d;
            case MATH_HOUR_STRING -> time.asReal().jvm() * 1000.0d * 60.0d * 60.0d;
            default -> throw MTronException.of("not a time unit: %s", time);
        };
    }

    /**
     * Normalizes a time {@link Real} to the most human-readable unit.
     * Cascades upward through the time hierarchy when the value crosses
     * a ~2× threshold of the next larger unit:
     * <pre>
     *   millis ≥ 2000  → seconds
     *   seconds ≥ 120  → minutes
     *   minutes ≥ 120  → hours
     * </pre>
     * Recurses until the value stabilizes in the appropriate unit.
     */
    public static Real normalizeTime(final Real time) {
        final String tid = time.tid().toString();
        final double value = time.realValue();

        if (tid.equals(MATH_MILLIS_STRING) && value >= 2000.0d)
            return normalizeTime(time.as(SECOND_TYPE).asReal());
        if (tid.equals(MATH_SECOND_STRING) && value >= 120.0d)
            return normalizeTime(time.as(MINUTE_TYPE).asReal());
        if (tid.equals(MATH_MINUTE_STRING) && value >= 120.0d)
            return normalizeTime(time.as(HOUR_TYPE).asReal());

        return time;
    }

    /**
     * Normalizes a data-size {@link Real} to the most human-readable unit.
     * Cascades upward through the data hierarchy when the value crosses
     * a ~2× threshold of the next larger unit:
     * <pre>
     *   bytes ≥ 2048  → kB
     *   kB    ≥ 2048  → mB
     *   mB    ≥ 2048  → gB
     *   gB    ≥ 2048  → tB
     *   tB    ≥ 2048  → pB
     * </pre>
     * Recurses until the value stabilizes in the appropriate unit.
     */
    public static Real normalizeData(final Real data) {
        final String tid = data.tid().toString();
        final double value = data.realValue();

        if (tid.equals(MATH_BYTE_STRING) && value >= 2048.0d)
            return normalizeData(data.as(KBYTE_TYPE).asReal());
        if (tid.equals(MATH_KBYTE_STRING) && value >= 2048.0d)
            return normalizeData(data.as(MBYTE_TYPE).asReal());
        if (tid.equals(MATH_MBYTE_STRING) && value >= 2048.0d)
            return normalizeData(data.as(GBYTE_TYPE).asReal());
        if (tid.equals(MATH_GBYTE_STRING) && value >= 2048.0d)
            return normalizeData(data.as(TBYTE_TYPE).asReal());
        if (tid.equals(MATH_TBYTE_STRING) && value >= 2048.0d)
            return normalizeData(data.as(PBYTE_TYPE).asReal());

        return data;
    }

    /// ////////////////////////////////////////////////////////////////////////

    public static final Type DATA_SIZE_TYPE = Type.Builder.build()
            .tid(REAL_TID)
            .vid(MATH_DATA_TID)
            .predicate(id_().tryToInst())
            .create();


    public static final Type BYTE_TYPE = Type.Builder.build()
            .tid(MATH_DATA_TID)
            .vid(MATH_BYTE_TID)
            .constructor(arg -> {
                final String tid = arg.tid().toString();
                return switch (tid) {
                    case MATH_KBYTE_STRING -> arg.jvm(arg.asReal().jvm() * 1024.0d);
                    case MATH_MBYTE_STRING -> arg.jvm(arg.asReal().jvm() * 1024.0d * 1024.0d);
                    case MATH_GBYTE_STRING -> arg.jvm(arg.asReal().jvm() * 1024.0d * 1024.0d * 1024.0d);
                    case MATH_TBYTE_STRING -> arg.jvm(arg.asReal().jvm() * 1024.0d * 1024.0d * 1024.0d * 1024.0d);
                    case MATH_PBYTE_STRING ->
                            arg.jvm(arg.asReal().jvm() * 1024.0d * 1024.0d * 1024.0d * 1024.0d * 1024.0d);
                    default -> arg;
                };
            }).create();

    public static final Type KBYTE_TYPE = Type.Builder.build()
            .tid(MATH_DATA_TID)
            .vid(MATH_KBYTE_TID)
            .constructor(arg -> {
                final String tid = arg.tid().toString();
                return switch (tid) {
                    case MATH_BYTE_STRING -> arg.jvm(arg.asReal().jvm() / 1024.0d);
                    case MATH_MBYTE_STRING -> arg.jvm(arg.asReal().jvm() * 1024.0d);
                    case MATH_GBYTE_STRING -> arg.jvm(arg.asReal().jvm() * 1024.0d * 1024.0d);
                    case MATH_TBYTE_STRING -> arg.jvm(arg.asReal().jvm() * 1024.0d * 1024.0d * 1024.0d);
                    case MATH_PBYTE_STRING -> arg.jvm(arg.asReal().jvm() * 1024.0d * 1024.0d * 1024.0d * 1024.0d);
                    default -> arg;
                };
            }).create();

    public static final Type MBYTE_TYPE = Type.Builder.build()
            .tid(MATH_DATA_TID)
            .vid(MATH_MBYTE_TID)
            .constructor(arg -> {
                final String tid = arg.tid().toString();
                return switch (tid) {
                    case MATH_BYTE_STRING -> arg.jvm(arg.asReal().jvm() / 1024.0d / 1024.0d);
                    case MATH_KBYTE_STRING -> arg.jvm(arg.asReal().jvm() / 1024.0d);
                    case MATH_GBYTE_STRING -> arg.jvm(arg.asReal().jvm() * 1024.0d);
                    case MATH_TBYTE_STRING -> arg.jvm(arg.asReal().jvm() * 1024.0d * 1024.0d);
                    case MATH_PBYTE_STRING -> arg.jvm(arg.asReal().jvm() * 1024.0d * 1024.0d * 1024.0d);
                    default -> arg;
                };
            }).create();

    public static final Type GBYTE_TYPE = Type.Builder.build()
            .tid(MATH_DATA_TID)
            .vid(MATH_GBYTE_TID)
            .constructor(arg -> {
                final String tid = arg.tid().toString();
                return switch (tid) {
                    case MATH_BYTE_STRING -> arg.jvm(arg.asReal().jvm() / 1024.0d / 1024.0d / 1024.0d);
                    case MATH_KBYTE_STRING -> arg.jvm(arg.asReal().jvm() / 1024.0d / 1024.0d);
                    case MATH_MBYTE_STRING -> arg.jvm(arg.asReal().jvm() / 1024.0d);
                    case MATH_TBYTE_STRING -> arg.jvm(arg.asReal().jvm() * 1024.0d);
                    case MATH_PBYTE_STRING -> arg.jvm(arg.asReal().jvm() * 1024.0d * 1024.0d);
                    default -> arg;
                };
            }).create();

    public static final Type TBYTE_TYPE = Type.Builder.build()
            .tid(MATH_DATA_TID)
            .vid(MATH_TBYTE_TID)
            .constructor(arg -> {
                final String tid = arg.tid().toString();
                return switch (tid) {
                    case MATH_BYTE_STRING -> arg.jvm(arg.asReal().jvm() / 1024.0d / 1024.0d / 1024.0d / 1024.0d);
                    case MATH_KBYTE_STRING -> arg.jvm(arg.asReal().jvm() / 1024.0d / 1024.0d / 1024.0d);
                    case MATH_MBYTE_STRING -> arg.jvm(arg.asReal().jvm() / 1024.0d / 1024.0d);
                    case MATH_GBYTE_STRING -> arg.jvm(arg.asReal().jvm() / 1024.0d);
                    case MATH_PBYTE_STRING -> arg.jvm(arg.asReal().jvm() * 1024.0d);
                    default -> arg;
                };
            }).create();

    public static final Type PBYTE_TYPE = Type.Builder.build()
            .tid(MATH_DATA_TID)
            .vid(MATH_PBYTE_TID)
            .constructor(arg -> {
                final String tid = arg.tid().toString();
                return switch (tid) {
                    case MATH_BYTE_STRING ->
                            arg.jvm(arg.asReal().jvm() / 1024.0d / 1024.0d / 1024.0d / 1024.0d / 1024.0d);
                    case MATH_KBYTE_STRING -> arg.jvm(arg.asReal().jvm() / 1024.0d / 1024.0d / 1024.0d / 1024.0d);
                    case MATH_MBYTE_STRING -> arg.jvm(arg.asReal().jvm() / 1024.0d / 1024.0d / 1024.0d);
                    case MATH_GBYTE_STRING -> arg.jvm(arg.asReal().jvm() / 1024.0d / 1024.0d);
                    case MATH_TBYTE_STRING -> arg.jvm(arg.asReal().jvm() / 1024.0d);
                    default -> arg;
                };
            }).create();

    public void setup() {
        this.jvm().putAll(mutableMap(
                uri(PATTERN), uri(MATH_ISA_TID.extend(ALL)),
                uri(TYPE), lst(DATA_SIZE_TYPE,
                        docWrap(BYTE_TYPE, "a byte of data"),
                        docWrap(KBYTE_TYPE, "a kilobyte (1024 bytes) of data"),
                        docWrap(MBYTE_TYPE, "a megabyte (1024 kilobytes) of data"),
                        docWrap(GBYTE_TYPE, "a gigabyte (1024 megabytes) of data"),
                        docWrap(TBYTE_TYPE, "a terabyte (1024 gigabytes) of data"),
                        docWrap(PBYTE_TYPE, "a petabyte (1024 terabytes) of data"),
                        docWrap(MATH_CURRENCY_TYPE, "a currency amount"),
                        docWrap(Type.Builder.build().tid(MATH_CURRENCY_TID).vid(MATH_USD_TID).create(), "united states currency"),
                        docWrap(Type.Builder.build().tid(MATH_CURRENCY_TID).vid(MATH_EURO_TID).create(), "european union currency"),
                        docWrap(MILLIS_TYPE, "a millisecond of time"),
                        docWrap(SECOND_TYPE, "a second of time (1000 millis)"),
                        docWrap(MINUTE_TYPE, "a minute of time (60 seconds)"),
                        docWrap(HOUR_TYPE, "an hour of time (60 minutes)"),
                        docWrap(DATETIME_TYPE, "a datetime as uri: //yyyy.MM:dd/HH/mm/ss/SSS?tz=+-HHmm")),
                uri(INST), lst(
                        instC(MATH_DATETIME_NOW_INST_TID.dom(ALL.maybe()).rng(MATH_DATETIME_TID), lst(), (lhs, inst) -> nowDatetime()),
                        // datetime arithmetic: datetime + time -> datetime, datetime - time -> datetime,
                        // datetime - datetime -> millis::T
                        instC(PLUS_INST_TID.dom(MATH_DATETIME_TID).rng(MATH_DATETIME_TID), lst(TIME_TYPE), (lhs, inst) ->
                                buildDatetimeUri(ZonedDateTime.ofInstant(Instant.ofEpochMilli(datetimeToMillis(lhs.asUri()) + (long) timeToMillis(inst.arg(0))), ZoneOffset.UTC))),
                        instC(MINUS_INST_TID.dom(MATH_DATETIME_TID).rng(MATH_DATETIME_TID), lst(TIME_TYPE), (lhs, inst) ->
                                buildDatetimeUri(ZonedDateTime.ofInstant(Instant.ofEpochMilli(datetimeToMillis(lhs.asUri()) - (long) timeToMillis(inst.arg(0))), ZoneOffset.UTC))),
                        instC(MINUS_INST_TID.dom(MATH_DATETIME_TID).rng(MATH_TIME_TID), lst(DATETIME_TYPE), (lhs, inst) ->
                                normalizeTime(real((double) (datetimeToMillis(lhs.asUri()) - datetimeToMillis(inst.arg(0).asUri())), MATH_MILLIS_TID, null))),
                        // uri → datetime identity cast (predicate validates in Type.apply)
                        instC(AS_INST_TID.dom(URI_TID).rng(MATH_DATETIME_TID), lst(URI_TYPE), (lhs, inst) -> lhs.asUri().tid(MATH_DATETIME_TID)),
                        instC(AS_INST_TID.dom(MATH_DATETIME_TID).rng(STR_TID), lst(STR_TYPE), (lhs, inst) -> str(humanReadableDatetime(lhs.asUri()))),
                        // str → datetime (parse ISO-8601 / Docker timestamps)
                        instC(AS_INST_TID.dom(STR_TID).rng(MATH_DATETIME_TID), lst(DATETIME_TYPE), (lhs, inst) -> parseDatetime(lhs.strValue())),
                        /*instC(MATH_NOW_INST_TID.dom(ALL.maybe()).rng(MATH_TIME_TID), lst(), (lhs, inst) -> real((double) System.currentTimeMillis(), MATH_TIME_TID, null)),
                        instC(AS_INST_TID.dom(MATH_TIME_TID).rng(STR_TID), lst(TIME_TYPE), (lhs, inst) -> {
                            Date date = new Date(lhs.realValue().intValue());
                            DateFormat formatter = new SimpleDateFormat("yyyy.MM.dd 'at' HH:mm:ss z");
                            formatter.setTimeZone(TimeZone.getTimeZone(ZoneId.systemDefault()));
                            return str(formatter.format(date));
                        }),*/
                        instC(MATH_TIME_TID.extend(INST).extend("normalize").dom(MATH_TIME_TID).rng(MATH_TIME_TID), lst(), (lhs, inst) -> normalizeTime(lhs.asReal())),
                        instC(MATH_DATA_TID.extend(INST).extend("normalize").dom(MATH_DATA_TID).rng(MATH_DATA_TID), lst(), (lhs, inst) -> normalizeData(lhs.asReal())),
                        instC(MATH_COS_INST_TID.dom(ALL.maybe()).rng(REAL_TID), lst(as_(REAL_TYPE).tryToInst()), (lhs, inst) -> real(Math.cos(inst.arg(0).realValue()))),
                        instC(MATH_SIN_INST_TID.dom(ALL.maybe()).rng(REAL_TID), lst(REAL_TYPE), (lhs, inst) -> real(Math.sin(inst.arg(0).realValue()))),
                        instC(MATH_TAN_INST_TID.dom(ALL.maybe()).rng(REAL_TID), lst(REAL_TYPE), (lhs, inst) -> real(Math.tan(inst.arg(0).realValue()))),
                        instC(MATH_SQRT_INST_TID.dom(ALL.maybe()).rng(REAL_TID), lst(REAL_TYPE), (lhs, inst) -> real(Math.sqrt(inst.arg(0).realValue()))),
                        instC(MATH_POW_INST_TID.dom(ALL.maybe()).rng(REAL_TID), lst(REAL_TYPE), (lhs, inst) -> real(Math.pow(lhs.realValue(), inst.arg(0).realValue()))),
                        instC(MATH_ATAN_INST_TID.dom(ALL.maybe()).rng(REAL_TID), lst(REAL_TYPE), (lhs, inst) -> real(Math.atan(inst.arg(0).realValue()))),
                        instC(MATH_ATAN2_INST_TID.dom(ALL.maybe()).rng(REAL_TID), lst(REAL_TYPE.c(cInt.of(2))), (lhs, inst) -> real(Math.atan2(inst.arg(0).take(cInt.ONE()).get0().realValue(), inst.arg(0).take(cInt.ONE()).get0().realValue()))),
                        instC(MATH_LOG_INST_TID.dom(ALL.maybe()).rng(REAL_TID), lst(REAL_TYPE), (lhs, inst) -> real(Math.log(inst.arg(0).realValue()))),
                        instC(MATH_LOG10_INST_TID.dom(ALL.maybe()).rng(REAL_TID), lst(REAL_TYPE), (lhs, inst) -> real(Math.log10(inst.arg(0).realValue()))),
                        instC(MATH_EXP_INST_TID.dom(ALL.maybe()).rng(REAL_TID), lst(REAL_TYPE), (lhs, inst) -> real(Math.exp(inst.arg(0).realValue()))),
                        instC(MATH_ABS_INST_TID.dom(ALL.maybe()).rng(REAL_TID), lst(REAL_TYPE), (lhs, inst) -> real(Math.abs(inst.arg(0).realValue()))),
                        instC(MATH_CEIL_INST_TID.dom(ALL.maybe()).rng(REAL_TID), lst(REAL_TYPE), (lhs, inst) -> real(Math.ceil(inst.arg(0).realValue()))),
                        instC(MATH_FLOOR_INST_TID.dom(ALL.maybe()).rng(REAL_TID), lst(REAL_TYPE), (lhs, inst) -> real(Math.floor(inst.arg(0).realValue()))),
                        instC(MATH_ROUND_INST_TID.dom(ALL.maybe()).rng(INT_TID), lst(REAL_TYPE), (lhs, inst) -> jnt(Math.round(inst.arg(0).realValue())))),
                uri(CONST), lst(real(Math.E, REAL_TID, MATH_ISA_TID.extend("e").constant()), real(Math.PI, REAL_TID, MATH_ISA_TID.extend("pi").constant()))));
        docWrap(this, "the collection of mathematical instructions, algebraic and numeric data types, and associated constants");
        super.setup();
    }

}
