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
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.*;
import static studio.phaseshift.metatron.isa.m.type.Int.INT_TYPE;
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
    public static final fURI NAT_TID = MATH_ISA_TID.extend("nat");
    public static final fURI MATH_DATASIZE_TID = MATH_ISA_TID.extend("datasize");
    public static final fURI MATH_BYTE_TID = MATH_DATASIZE_TID.extend("bB");
    public static final fURI MATH_KBYTE_TID = MATH_DATASIZE_TID.extend("kB");
    public static final fURI MATH_MBYTE_TID = MATH_DATASIZE_TID.extend("mB");
    public static final fURI MATH_GBYTE_TID = MATH_DATASIZE_TID.extend("gB");
    public static final fURI MATH_TBYTE_TID = MATH_DATASIZE_TID.extend("tB");
    public static final fURI MATH_PBYTE_TID = MATH_DATASIZE_TID.extend("pB");
    public static final String MATH_BYTE_STRING = "/m/math/datasize/bB";
    public static final String MATH_KBYTE_STRING = "/m/math/datasize/kB";
    public static final String MATH_MBYTE_STRING = "/m/math/datasize/mB";
    public static final String MATH_GBYTE_STRING = "/m/math/datasize/gB";
    public static final String MATH_TBYTE_STRING = "/m/math/datasize/tB";
    public static final String MATH_PBYTE_STRING = "/m/math/datasize/pB";
    /// ///////////////////////
    public static final fURI MATH_TIME_TID = MATH_ISA_TID.extend("time");
    public static final fURI MATH_MILLIS_TID = MATH_TIME_TID.extend("millis");
    public static final fURI MATH_SECOND_TID = MATH_TIME_TID.extend("second");
    public static final fURI MATH_MINUTE_TID = MATH_TIME_TID.extend("minute");
    public static final fURI MATH_HOUR_TID = MATH_TIME_TID.extend("hour");
    public static final fURI MATH_DAY_TID = MATH_TIME_TID.extend("day");
    public static final String MATH_MILLIS_STRING = "/m/math/time/millis";
    public static final String MATH_SECOND_STRING = "/m/math/time/second";
    public static final String MATH_MINUTE_STRING = "/m/math/time/minute";
    public static final String MATH_HOUR_STRING = "/m/math/time/hour";
    public static final String MATH_DAY_STRING = "/m/math/time/day";
    /// ///////////////////////
    public static final fURI MATH_METRIC_TID = MATH_ISA_TID.extend("metric");
    public static final fURI MATH_MM_TID = MATH_METRIC_TID.extend("mm");
    public static final fURI MATH_CM_TID = MATH_METRIC_TID.extend("cm");
    public static final fURI MATH_DM_TID = MATH_METRIC_TID.extend("dm");
    public static final fURI MATH_METER_TID = MATH_METRIC_TID.extend("meter");
    public static final fURI MATH_KM_TID = MATH_METRIC_TID.extend("km");
    public static final String MATH_MM_STRING = "/m/math/metric/mm";
    public static final String MATH_CM_STRING = "/m/math/metric/cm";
    public static final String MATH_DM_STRING = "/m/math/metric/dm";
    public static final String MATH_METER_STRING = "/m/math/metric/meter";
    public static final String MATH_KM_STRING = "/m/math/metric/km";
    public static final fURI MATH_IMPERIAL_TID = MATH_ISA_TID.extend("imperial");
    public static final fURI MATH_INCH_TID = MATH_IMPERIAL_TID.extend("inch");
    public static final fURI MATH_FOOT_TID = MATH_IMPERIAL_TID.extend("foot");
    public static final fURI MATH_YARD_TID = MATH_IMPERIAL_TID.extend("yard");
    public static final fURI MATH_MILE_TID = MATH_IMPERIAL_TID.extend("mile");
    public static final String MATH_INCH_STRING = "/m/math/imperial/inch";
    public static final String MATH_FOOT_STRING = "/m/math/imperial/foot";
    public static final String MATH_YARD_STRING = "/m/math/imperial/yard";
    public static final String MATH_MILE_STRING = "/m/math/imperial/mile";
    /// ///////////////////////
    public static final fURI MATH_DATETIME_TID = MATH_ISA_TID.extend("datetime");
    /// ///////////////////////
    public static final fURI MATH_CURRENCY_TID = f("/m/math/currency");
    public static final fURI MATH_USD_TID = MATH_CURRENCY_TID.extend("usd");
    public static final fURI MATH_EURO_TID = MATH_CURRENCY_TID.extend("euro");
    public static final Type MATH_CURRENCY_TYPE = Type.Builder.build()
            .tid(REAL_TID)
            .vid(MATH_CURRENCY_TID)
            .create();

    static {
        assert MATH_BYTE_STRING.equals(MATH_BYTE_TID.toString());
        assert MATH_KBYTE_STRING.equals(MATH_KBYTE_TID.toString());
        assert MATH_MBYTE_STRING.equals(MATH_MBYTE_TID.toString());
        assert MATH_GBYTE_STRING.equals(MATH_GBYTE_TID.toString());
        assert MATH_TBYTE_STRING.equals(MATH_TBYTE_TID.toString());
        assert MATH_PBYTE_STRING.equals(MATH_PBYTE_TID.toString());
        assert MATH_MILLIS_STRING.equals(MATH_MILLIS_TID.toString());
        assert MATH_SECOND_STRING.equals(MATH_SECOND_TID.toString());
        assert MATH_MINUTE_STRING.equals(MATH_MINUTE_TID.toString());
        assert MATH_HOUR_STRING.equals(MATH_HOUR_TID.toString());
        assert MATH_DAY_STRING.equals(MATH_DAY_TID.toString());
        assert MATH_MM_STRING.equals(MATH_MM_TID.toString());
        assert MATH_CM_STRING.equals(MATH_CM_TID.toString());
        assert MATH_DM_STRING.equals(MATH_DM_TID.toString());
        assert MATH_METER_STRING.equals(MATH_METER_TID.toString());
        assert MATH_KM_STRING.equals(MATH_KM_TID.toString());
        assert MATH_INCH_STRING.equals(MATH_INCH_TID.toString());
        assert MATH_FOOT_STRING.equals(MATH_FOOT_TID.toString());
        assert MATH_YARD_STRING.equals(MATH_YARD_TID.toString());
        assert MATH_MILE_STRING.equals(MATH_MILE_TID.toString());
    }


    public mathInstSet() {
        super(mutableMap(uri(PATTERN), uri(MATH_ISA_TID.extend(HASH_FURI))), INSTSET_TID, MATH_ISA_TID);
    }

    public static final Type TIME_TYPE = Type.Builder.build()
            .tid(REAL_TID)
            .vid(MATH_TIME_TID)
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
                    case MATH_DAY_STRING -> arg.jvm(arg.asReal().jvm() * 1000.0d * 60.0d * 60.0d * 24.0d);
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
                    case MATH_DAY_STRING -> arg.jvm(arg.asReal().jvm() * 60.0d * 60.0d * 24.0d);
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
                    case MATH_DAY_STRING -> arg.jvm(arg.asReal().jvm() * 60.0d * 24.0d);
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
                    case MATH_DAY_STRING -> arg.jvm(arg.asReal().jvm() * 24.0d);
                    default -> arg;
                };
            }).create();

    public static final Type DAY_TYPE = Type.Builder.build()
            .tid(MATH_TIME_TID)
            .vid(MATH_DAY_TID)
            .constructor(arg -> {
                final String tid = arg.tid().toString();
                return switch (tid) {
                    case MATH_MILLIS_STRING -> arg.jvm(arg.asReal().jvm() / 60.0d / 60.0d / 24.0d / 1000.0d);
                    case MATH_SECOND_STRING -> arg.jvm(arg.asReal().jvm() / 60.0d / 60.0d / 24.0d);
                    case MATH_MINUTE_STRING -> arg.jvm(arg.asReal().jvm() / 60.0d / 24.0d);
                    case MATH_HOUR_STRING -> arg.jvm(arg.asReal().jvm() / 24.0d);
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
     * Convert a {@code time::T} (millis/second/minute/hour/day) to milliseconds.
     * Time units require real-backed values — an int-backed time is a type
     * violation and {@code asReal()} rejects it.
     */
    public static double timeToMillis(final Obj time) {
        return switch (time.tid().basePath().toString()) {
            case MATH_MILLIS_STRING -> time.asReal().jvm();
            case MATH_SECOND_STRING -> time.asReal().jvm() * 1000.0d;
            case MATH_MINUTE_STRING -> time.asReal().jvm() * 1000.0d * 60.0d;
            case MATH_HOUR_STRING -> time.asReal().jvm() * 1000.0d * 60.0d * 60.0d;
            case MATH_DAY_STRING -> time.asReal().jvm() * 1000.0d * 60.0d * 60.0d * 24.0d;
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
     *   hours   ≥ 48   → days
     * </pre>
     * Recurses until the value stabilizes in the appropriate unit.
     */
    public static Real normalizeTime(final Real time) {
        final double value = time.realValue();
        if (time.tid().test(MATH_MILLIS_TID) && value >= 2000.0d)
            return normalizeTime(time.as(SECOND_TYPE).asReal());
        if (time.tid().test(MATH_SECOND_TID) && value >= 120.0d)
            return normalizeTime(time.as(MINUTE_TYPE).asReal());
        if (time.tid().test(MATH_MINUTE_TID) && value >= 120.0d)
            return normalizeTime(time.as(HOUR_TYPE).asReal());
        if (time.tid().test(MATH_HOUR_TID) && value >= 48.0d)
            return normalizeTime(time.as(DAY_TYPE).asReal());

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

    /**
     * One inch is exactly 25.4 millimeters — the bridge between the
     * imperial and metric distance systems.
     */
    public static final double MM_PER_INCH = 25.4d;

    public static boolean isMetricUnit(final String tid) {
        return switch (tid) {
            case MATH_MM_STRING, MATH_CM_STRING, MATH_DM_STRING, MATH_METER_STRING, MATH_KM_STRING -> true;
            default -> false;
        };
    }

    public static boolean isImperialUnit(final String tid) {
        return switch (tid) {
            case MATH_INCH_STRING, MATH_FOOT_STRING, MATH_YARD_STRING, MATH_MILE_STRING -> true;
            default -> false;
        };
    }

    private static final double MM_PER(final String tid) {
        return switch (tid) {
            case MATH_CM_STRING -> 10.0d;
            case MATH_DM_STRING -> 100.0d;
            case MATH_METER_STRING -> 1000.0d;
            case MATH_KM_STRING -> 1000.0d * 1000.0d;
            default -> 1.0d;
        };
    }

    private static final double INCH_PER(final String tid) {
        return switch (tid) {
            case MATH_FOOT_STRING -> 12.0d;
            case MATH_YARD_STRING -> 36.0d;
            case MATH_MILE_STRING -> 63360.0d;
            default -> 1.0d;
        };
    }

    /**
     * Converts a distance (metric or imperial, any unit) to the target unit.
     * metric ↔ metric and imperial ↔ imperial conversions use exact integer
     * ratios; a cross-system conversion is bridged through the exact
     * {@value #MM_PER_INCH} millimeters per inch.  A bare real, or a target
     * of a supertype ({@code metric::T}/{@code imperial::T}), is re-labeled
     * only — its value is preserved.
     */
    public static Real convertTo(final Obj lhs, final fURI target) {
        final String tid = lhs.tid().toString();
        final String t = target.toString();
        final double value = lhs.asReal().jvm();
        if (t.equals(MATH_METRIC_TID) || t.equals(MATH_IMPERIAL_TID)
                || (!isMetricUnit(tid) && !isImperialUnit(tid)))
            return real(value, target, null);
        if (isMetricUnit(tid)) {
            final double mm = value * MM_PER(tid);
            return real(isMetricUnit(t) ? mm / MM_PER(t) : mm / MM_PER_INCH / INCH_PER(t), target, null);
        }
        final double inches = value * INCH_PER(tid);
        return real(isImperialUnit(t) ? inches / INCH_PER(t) : inches * MM_PER_INCH / MM_PER(t), target, null);
    }

    /// ////////////////////////////////////////////////////////////////////////

    public static final Type DATA_SIZE_TYPE = Type.Builder.build()
            .tid(REAL_TID)
            .vid(MATH_DATASIZE_TID)
            .create();

    public static final Type NAT_TYPE = Type.Builder.build()
            .tid(INT_TID)
            .vid(NAT_TID)
            .predicate(is_(gt_(jnt(0))).tryToInst())
            .create();

    public static final Type BYTE_TYPE = Type.Builder.build()
            .tid(MATH_DATASIZE_TID)
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
            .tid(MATH_DATASIZE_TID)
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
            .tid(MATH_DATASIZE_TID)
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
            .tid(MATH_DATASIZE_TID)
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
            .tid(MATH_DATASIZE_TID)
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
            .tid(MATH_DATASIZE_TID)
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

    public static final Type METRIC_TYPE = Type.Builder.build()
            .tid(REAL_TID)
            .vid(MATH_METRIC_TID)
            .create();

    public static final Type MM_TYPE = Type.Builder.build()
            .tid(MATH_METRIC_TID)
            .vid(MATH_MM_TID)
            .constructor(arg -> {
                final String tid = arg.tid().toString();
                return switch (tid) {
                    case MATH_CM_STRING -> arg.jvm(arg.asReal().jvm() * 10.0d);
                    case MATH_DM_STRING -> arg.jvm(arg.asReal().jvm() * 100.0d);
                    case MATH_METER_STRING -> arg.jvm(arg.asReal().jvm() * 1000.0d);
                    case MATH_KM_STRING -> arg.jvm(arg.asReal().jvm() * 1000.0d * 1000.0d);
                    case MATH_INCH_STRING -> arg.jvm(arg.asReal().jvm() * MM_PER_INCH);
                    case MATH_FOOT_STRING -> arg.jvm(arg.asReal().jvm() * 12.0d * MM_PER_INCH);
                    case MATH_YARD_STRING -> arg.jvm(arg.asReal().jvm() * 36.0d * MM_PER_INCH);
                    case MATH_MILE_STRING -> arg.jvm(arg.asReal().jvm() * 63360.0d * MM_PER_INCH);
                    default -> arg;
                };
            }).create();

    public static final Type CM_TYPE = Type.Builder.build()
            .tid(MATH_METRIC_TID)
            .vid(MATH_CM_TID)
            .constructor(arg -> {
                final String tid = arg.tid().toString();
                return switch (tid) {
                    case MATH_MM_STRING -> arg.jvm(arg.asReal().jvm() / 10.0d);
                    case MATH_DM_STRING -> arg.jvm(arg.asReal().jvm() * 10.0d);
                    case MATH_METER_STRING -> arg.jvm(arg.asReal().jvm() * 100.0d);
                    case MATH_KM_STRING -> arg.jvm(arg.asReal().jvm() * 100.0d * 1000.0d);
                    case MATH_INCH_STRING -> arg.jvm(arg.asReal().jvm() * MM_PER_INCH / 10.0d);
                    case MATH_FOOT_STRING -> arg.jvm(arg.asReal().jvm() * 12.0d * MM_PER_INCH / 10.0d);
                    case MATH_YARD_STRING -> arg.jvm(arg.asReal().jvm() * 36.0d * MM_PER_INCH / 10.0d);
                    case MATH_MILE_STRING -> arg.jvm(arg.asReal().jvm() * 63360.0d * MM_PER_INCH / 10.0d);
                    default -> arg;
                };
            }).create();

    public static final Type DM_TYPE = Type.Builder.build()
            .tid(MATH_METRIC_TID)
            .vid(MATH_DM_TID)
            .constructor(arg -> {
                final String tid = arg.tid().toString();
                return switch (tid) {
                    case MATH_MM_STRING -> arg.jvm(arg.asReal().jvm() / 100.0d);
                    case MATH_CM_STRING -> arg.jvm(arg.asReal().jvm() / 10.0d);
                    case MATH_METER_STRING -> arg.jvm(arg.asReal().jvm() * 10.0d);
                    case MATH_KM_STRING -> arg.jvm(arg.asReal().jvm() * 10.0d * 1000.0d);
                    case MATH_INCH_STRING -> arg.jvm(arg.asReal().jvm() * MM_PER_INCH / 100.0d);
                    case MATH_FOOT_STRING -> arg.jvm(arg.asReal().jvm() * 12.0d * MM_PER_INCH / 100.0d);
                    case MATH_YARD_STRING -> arg.jvm(arg.asReal().jvm() * 36.0d * MM_PER_INCH / 100.0d);
                    case MATH_MILE_STRING -> arg.jvm(arg.asReal().jvm() * 63360.0d * MM_PER_INCH / 100.0d);
                    default -> arg;
                };
            }).create();

    public static final Type METER_TYPE = Type.Builder.build()
            .tid(MATH_METRIC_TID)
            .vid(MATH_METER_TID)
            .constructor(arg -> {
                final String tid = arg.tid().toString();
                return switch (tid) {
                    case MATH_MM_STRING -> arg.jvm(arg.asReal().jvm() / 1000.0d);
                    case MATH_CM_STRING -> arg.jvm(arg.asReal().jvm() / 100.0d);
                    case MATH_DM_STRING -> arg.jvm(arg.asReal().jvm() / 10.0d);
                    case MATH_KM_STRING -> arg.jvm(arg.asReal().jvm() * 1000.0d);
                    case MATH_INCH_STRING -> arg.jvm(arg.asReal().jvm() * MM_PER_INCH / 1000.0d);
                    case MATH_FOOT_STRING -> arg.jvm(arg.asReal().jvm() * 12.0d * MM_PER_INCH / 1000.0d);
                    case MATH_YARD_STRING -> arg.jvm(arg.asReal().jvm() * 36.0d * MM_PER_INCH / 1000.0d);
                    case MATH_MILE_STRING -> arg.jvm(arg.asReal().jvm() * 63360.0d * MM_PER_INCH / 1000.0d);
                    default -> arg;
                };
            }).create();

    public static final Type KM_TYPE = Type.Builder.build()
            .tid(MATH_METRIC_TID)
            .vid(MATH_KM_TID)
            .constructor(arg -> {
                final String tid = arg.tid().toString();
                return switch (tid) {
                    case MATH_MM_STRING -> arg.jvm(arg.asReal().jvm() / 1000.0d / 1000.0d);
                    case MATH_CM_STRING -> arg.jvm(arg.asReal().jvm() / 1000.0d / 100.0d);
                    case MATH_DM_STRING -> arg.jvm(arg.asReal().jvm() / 1000.0d / 10.0d);
                    case MATH_METER_STRING -> arg.jvm(arg.asReal().jvm() / 1000.0d);
                    case MATH_INCH_STRING -> arg.jvm(arg.asReal().jvm() * MM_PER_INCH / 1000.0d / 1000.0d);
                    case MATH_FOOT_STRING -> arg.jvm(arg.asReal().jvm() * 12.0d * MM_PER_INCH / 1000.0d / 1000.0d);
                    case MATH_YARD_STRING -> arg.jvm(arg.asReal().jvm() * 36.0d * MM_PER_INCH / 1000.0d / 1000.0d);
                    case MATH_MILE_STRING -> arg.jvm(arg.asReal().jvm() * 63360.0d * MM_PER_INCH / 1000.0d / 1000.0d);
                    default -> arg;
                };
            }).create();

    public static final Type IMPERIAL_TYPE = Type.Builder.build()
            .tid(REAL_TID)
            .vid(MATH_IMPERIAL_TID)
            .create();

    public static final Type INCH_TYPE = Type.Builder.build()
            .tid(MATH_IMPERIAL_TID)
            .vid(MATH_INCH_TID)
            .constructor(arg -> {
                final String tid = arg.tid().toString();
                return switch (tid) {
                    case MATH_FOOT_STRING -> arg.jvm(arg.asReal().jvm() * 12.0d);
                    case MATH_YARD_STRING -> arg.jvm(arg.asReal().jvm() * 36.0d);
                    case MATH_MILE_STRING -> arg.jvm(arg.asReal().jvm() * 63360.0d);
                    case MATH_CM_STRING -> arg.jvm(arg.asReal().jvm() * 10.0d / MM_PER_INCH);
                    case MATH_DM_STRING -> arg.jvm(arg.asReal().jvm() * 100.0d / MM_PER_INCH);
                    case MATH_METER_STRING -> arg.jvm(arg.asReal().jvm() * 1000.0d / MM_PER_INCH);
                    case MATH_KM_STRING -> arg.jvm(arg.asReal().jvm() * 1000.0d * 1000.0d / MM_PER_INCH);
                    default -> arg;
                };
            }).create();

    public static final Type FOOT_TYPE = Type.Builder.build()
            .tid(MATH_IMPERIAL_TID)
            .vid(MATH_FOOT_TID)
            .constructor(arg -> {
                final String tid = arg.tid().toString();
                return switch (tid) {
                    case MATH_INCH_STRING -> arg.jvm(arg.asReal().jvm() / 12.0d);
                    case MATH_YARD_STRING -> arg.jvm(arg.asReal().jvm() * 3.0d);
                    case MATH_MILE_STRING -> arg.jvm(arg.asReal().jvm() * 5280.0d);
                    case MATH_CM_STRING -> arg.jvm(arg.asReal().jvm() * 10.0d / MM_PER_INCH / 12.0d);
                    case MATH_DM_STRING -> arg.jvm(arg.asReal().jvm() * 100.0d / MM_PER_INCH / 12.0d);
                    case MATH_METER_STRING -> arg.jvm(arg.asReal().jvm() * 1000.0d / MM_PER_INCH / 12.0d);
                    case MATH_KM_STRING -> arg.jvm(arg.asReal().jvm() * 1000.0d * 1000.0d / MM_PER_INCH / 12.0d);
                    default -> arg;
                };
            }).create();

    public static final Type YARD_TYPE = Type.Builder.build()
            .tid(MATH_IMPERIAL_TID)
            .vid(MATH_YARD_TID)
            .constructor(arg -> {
                final String tid = arg.tid().toString();
                return switch (tid) {
                    case MATH_INCH_STRING -> arg.jvm(arg.asReal().jvm() / 36.0d);
                    case MATH_FOOT_STRING -> arg.jvm(arg.asReal().jvm() / 3.0d);
                    case MATH_MILE_STRING -> arg.jvm(arg.asReal().jvm() * 1760.0d);
                    case MATH_CM_STRING -> arg.jvm(arg.asReal().jvm() * 10.0d / MM_PER_INCH / 36.0d);
                    case MATH_DM_STRING -> arg.jvm(arg.asReal().jvm() * 100.0d / MM_PER_INCH / 36.0d);
                    case MATH_METER_STRING -> arg.jvm(arg.asReal().jvm() * 1000.0d / MM_PER_INCH / 36.0d);
                    case MATH_KM_STRING -> arg.jvm(arg.asReal().jvm() * 1000.0d * 1000.0d / MM_PER_INCH / 36.0d);
                    default -> arg;
                };
            }).create();

    public static final Type MILE_TYPE = Type.Builder.build()
            .tid(MATH_IMPERIAL_TID)
            .vid(MATH_MILE_TID)
            .constructor(arg -> {
                final String tid = arg.tid().toString();
                return switch (tid) {
                    case MATH_INCH_STRING -> arg.jvm(arg.asReal().jvm() / 63360.0d);
                    case MATH_FOOT_STRING -> arg.jvm(arg.asReal().jvm() / 5280.0d);
                    case MATH_YARD_STRING -> arg.jvm(arg.asReal().jvm() / 1760.0d);
                    case MATH_CM_STRING -> arg.jvm(arg.asReal().jvm() * 10.0d / MM_PER_INCH / 63360.0d);
                    case MATH_DM_STRING -> arg.jvm(arg.asReal().jvm() * 100.0d / MM_PER_INCH / 63360.0d);
                    case MATH_METER_STRING -> arg.jvm(arg.asReal().jvm() * 1000.0d / MM_PER_INCH / 63360.0d);
                    case MATH_KM_STRING -> arg.jvm(arg.asReal().jvm() * 1000.0d * 1000.0d / MM_PER_INCH / 63360.0d);
                    default -> arg;
                };
            }).create();

    /**
     * Normalizes a metric distance {@link Real} to the most human-readable unit.
     * Cascades upward through the metric hierarchy when the value crosses
     * a ~2× threshold of the next larger unit:
     * <pre>
     *   mm      ≥ 20     → cm
     *   cm      ≥ 20     → dm
     *   dm      ≥ 20     → meter
     *   meter   ≥ 2000   → km
     * </pre>
     * Recurses until the value stabilizes in the appropriate unit.
     */
    public static Real normalizeMetric(final Real metric) {
        final double value = metric.realValue();
        if (metric.tid().test(MATH_MM_TID) && value >= 20.0d)
            return normalizeMetric(metric.as(CM_TYPE).asReal());
        if (metric.tid().test(MATH_CM_TID) && value >= 20.0d)
            return normalizeMetric(metric.as(DM_TYPE).asReal());
        if (metric.tid().test(MATH_DM_TID) && value >= 20.0d)
            return normalizeMetric(metric.as(METER_TYPE).asReal());
        if (metric.tid().test(MATH_METER_TID) && value >= 2000.0d)
            return normalizeMetric(metric.as(KM_TYPE).asReal());

        return metric;
    }

    /**
     * Normalizes an imperial distance {@link Real} to the most human-readable unit.
     * Cascades upward through the imperial hierarchy when the value crosses
     * a ~2× threshold of the next larger unit:
     * <pre>
     *   inch  ≥ 24     → foot
     *   foot  ≥ 6      → yard
     *   yard  ≥ 3520   → mile
     * </pre>
     * Recurses until the value stabilizes in the appropriate unit.
     */
    public static Real normalizeImperial(final Real imperial) {
        final double value = imperial.realValue();
        if (imperial.tid().test(MATH_INCH_TID) && value >= 24.0d)
            return normalizeImperial(imperial.as(FOOT_TYPE).asReal());
        if (imperial.tid().test(MATH_FOOT_TID) && value >= 6.0d)
            return normalizeImperial(imperial.as(YARD_TYPE).asReal());
        if (imperial.tid().test(MATH_YARD_TID) && value >= 3520.0d)
            return normalizeImperial(imperial.as(MILE_TYPE).asReal());

        return imperial;
    }

    public void setup() {
        this.jvm().putAll(mutableMap(
                uri(PATTERN), uri(MATH_ISA_TID.extend(ALL)),
                uri(TYPE), lst(
                        docWrap(NAT_TYPE, "a positive integer"),
                        DATA_SIZE_TYPE,
                        docWrap(BYTE_TYPE, "a byte of data"),
                        docWrap(KBYTE_TYPE, "a kilobyte (1024 bytes) of data"),
                        docWrap(MBYTE_TYPE, "a megabyte (1024 kilobytes) of data"),
                        docWrap(GBYTE_TYPE, "a gigabyte (1024 megabytes) of data"),
                        docWrap(TBYTE_TYPE, "a terabyte (1024 gigabytes) of data"),
                        docWrap(PBYTE_TYPE, "a petabyte (1024 terabytes) of data"),
                        docWrap(MATH_CURRENCY_TYPE, "a currency amount"),
                        docWrap(Type.Builder.build().tid(MATH_CURRENCY_TID).vid(MATH_USD_TID).create(), "united states currency"),
                        docWrap(Type.Builder.build().tid(MATH_CURRENCY_TID).vid(MATH_EURO_TID).create(), "european union currency"),
                        docWrap(TIME_TYPE, "the nominal base type of time"),
                        docWrap(MILLIS_TYPE, "a millisecond of time"),
                        docWrap(SECOND_TYPE, "a second of time (1000 millis)"),
                        docWrap(MINUTE_TYPE, "a minute of time (60 seconds)"),
                        docWrap(HOUR_TYPE, "an hour of time (60 minutes)"),
                        docWrap(DAY_TYPE, "a day of time (24 hours)"),
                        docWrap(DATETIME_TYPE, "a datetime as uri: <//yyyy.MM:dd/HH/mm/ss/SSS?tz=+-HHmm>"),
                        docWrap(METRIC_TYPE, "the nominal base type of metric distance"),
                        docWrap(MM_TYPE, "a millimeter of distance"),
                        docWrap(CM_TYPE, "a centimeter of distance (10 millimeters)"),
                        docWrap(DM_TYPE, "a decimeter of distance (10 centimeters)"),
                        docWrap(METER_TYPE, "a meter of distance (100 centimeters)"),
                        docWrap(KM_TYPE, "a kilometer of distance (1000 meters)"),
                        docWrap(IMPERIAL_TYPE, "the nominal base type of imperial distance"),
                        docWrap(INCH_TYPE, "an inch of distance (25.4 millimeters)"),
                        docWrap(FOOT_TYPE, "a foot of distance (12 inches)"),
                        docWrap(YARD_TYPE, "a yard of distance (3 feet)"),
                        docWrap(MILE_TYPE, "a mile of distance (1760 yards)")),
                uri(INST), lst(
                        instC(MATH_INST_TID.extend("datetime_now").dom(ALL.maybe()).rng(MATH_DATETIME_TID), lst(), (lhs, inst) -> nowDatetime()),
                        // datetime arithmetic: datetime + time -> datetime, datetime - time -> datetime,
                        // datetime - datetime -> millis::T
                        instC(PLUS_INST_TID.dom(MATH_TIME_TID).rng(MATH_TIME_TID), lst(TIME_TYPE), (lhs, inst) -> {
                            final fURI normalizedTID = inst.arg(0).tid().basePath().equals(REAL_TID) ? lhs.tid().basePath() : MATH_MILLIS_TID;
                            return real(lhs.tid(normalizedTID).realValue() +
                                    inst.arg(0).tid(normalizedTID).realValue(), normalizedTID, lhs.vid()).tid(lhs.tid());
                        }),
                        instC(PLUS_INST_TID.dom(MATH_METRIC_TID).rng(MATH_METRIC_TID), lst(METRIC_TYPE), (lhs, inst) -> {
                            final fURI normalizedTID = inst.arg(0).tid().basePath().equals(REAL_TID) ? lhs.tid().basePath() : MATH_METER_TID;
                            return real(lhs.tid(normalizedTID).realValue() +
                                    inst.arg(0).tid(normalizedTID).realValue(), normalizedTID, lhs.vid()).tid(lhs.tid());
                        }),
                        instC(PLUS_INST_TID.dom(MATH_IMPERIAL_TID).rng(MATH_IMPERIAL_TID), lst(IMPERIAL_TYPE), (lhs, inst) -> {
                            final fURI normalizedTID = inst.arg(0).tid().basePath().equals(REAL_TID) ? lhs.tid().basePath() : MATH_FOOT_TID;
                            return real(lhs.tid(normalizedTID).realValue() +
                                    inst.arg(0).tid(normalizedTID).realValue(), normalizedTID, lhs.vid()).tid(lhs.tid());
                        }),
                        instC(AS_INST_TID.dom(MATH_TIME_TID).rng(MATH_TIME_TID), lst(TIME_TYPE), (lhs, inst) -> lhs.tid(inst.arg(0).vid())),
                        instC(PLUS_INST_TID.dom(MATH_DATETIME_TID).rng(MATH_DATETIME_TID), lst(TIME_TYPE), (lhs, inst) ->
                                buildDatetimeUri(ZonedDateTime.ofInstant(Instant.ofEpochMilli(datetimeToMillis(lhs.asUri()) + (long) timeToMillis(inst.arg(0))), ZoneOffset.UTC))),
                        instC(MINUS_INST_TID.dom(MATH_DATETIME_TID).rng(MATH_DATETIME_TID), lst(TIME_TYPE), (lhs, inst) ->
                                buildDatetimeUri(ZonedDateTime.ofInstant(Instant.ofEpochMilli(datetimeToMillis(lhs.asUri()) - (long) timeToMillis(inst.arg(0))), ZoneOffset.UTC))),
                        instC(MINUS_INST_TID.dom(MATH_DATETIME_TID).rng(MATH_TIME_TID), lst(DATETIME_TYPE), (lhs, inst) ->
                                normalizeTime(real((double) (datetimeToMillis(lhs.asUri()) - datetimeToMillis(inst.arg(0).asUri())), MATH_MILLIS_TID, null))),
                        // uri → datetime identity cast (predicate validates in Type.apply)
                        instC(AS_INST_TID.dom(URI_TID).rng(MATH_DATETIME_TID), lst(URI_TYPE), (lhs, inst) -> lhs.asUri().tid(MATH_DATETIME_TID)),
                        instC(AS_INST_TID.dom(MATH_DATETIME_TID).rng(INT_TID), lst(INT_TYPE), (lhs, inst) -> jnt(datetimeToMillis(lhs.asUri()))),
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
                        instC(MATH_INST_TID.extend("normalize").dom(MATH_TIME_TID).rng(MATH_TIME_TID), lst(), (lhs, inst) -> normalizeTime(lhs.asReal())),
                        instC(MATH_INST_TID.extend("normalize").dom(MATH_DATASIZE_TID).rng(MATH_DATASIZE_TID), lst(), (lhs, inst) -> normalizeData(lhs.asReal())),
                        instC(MATH_INST_TID.extend("normalize").dom(MATH_METRIC_TID).rng(MATH_METRIC_TID), lst(), (lhs, inst) -> normalizeMetric(lhs.asReal())),
                        instC(MATH_INST_TID.extend("normalize").dom(MATH_IMPERIAL_TID).rng(MATH_IMPERIAL_TID), lst(), (lhs, inst) -> normalizeImperial(lhs.asReal())),
                        // metric / imperial distance: within-system casts, and the two cross-system
                        // mappings (metric ↔ imperial) bridged through 25.4 mm per inch
                        instC(AS_INST_TID.dom(MATH_METRIC_TID).rng(MATH_METRIC_TID), lst(METRIC_TYPE), (lhs, inst) -> convertTo(lhs, inst.arg(0).vid())),
                        instC(AS_INST_TID.dom(MATH_IMPERIAL_TID).rng(MATH_IMPERIAL_TID), lst(IMPERIAL_TYPE), (lhs, inst) -> convertTo(lhs, inst.arg(0).vid())),
                        instC(AS_INST_TID.dom(MATH_METRIC_TID).rng(MATH_IMPERIAL_TID), lst(IMPERIAL_TYPE), (lhs, inst) -> convertTo(lhs, inst.arg(0).vid())),
                        instC(AS_INST_TID.dom(MATH_IMPERIAL_TID).rng(MATH_METRIC_TID), lst(METRIC_TYPE), (lhs, inst) -> convertTo(lhs, inst.arg(0).vid())),
                        instC(MATH_COS_INST_TID.dom(ALL.maybe()).rng(REAL_TID), lst(as_(REAL_TYPE).tryToInst()), (lhs, inst) -> real(Math.cos(inst.arg(0).realValue()))),
                        instC(MATH_SIN_INST_TID.dom(ALL.maybe()).rng(REAL_TID), lst(REAL_TYPE), (lhs, inst) -> real(Math.sin(inst.arg(0).realValue()))),
                        instC(MATH_TAN_INST_TID.dom(ALL.maybe()).rng(REAL_TID), lst(REAL_TYPE), (lhs, inst) -> real(Math.tan(inst.arg(0).realValue()))),
                        instC(MATH_SQRT_INST_TID.dom(ALL.maybe()).rng(REAL_TID), lst(REAL_TYPE), (lhs, inst) -> real(Math.sqrt(inst.arg(0).realValue()))),
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
