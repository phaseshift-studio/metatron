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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.Typer;
import studio.phaseshift.metatron.isa.m.math.mathInstSet;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.sys.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;

import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.ACTION;
import static studio.phaseshift.metatron.Tokens.ACTOR;
import static studio.phaseshift.metatron.Tokens.ATTRIBUTES;
import static studio.phaseshift.metatron.Tokens.EXIT;
import static studio.phaseshift.metatron.Tokens.NAME;
import static studio.phaseshift.metatron.Tokens.OBJ;
import static studio.phaseshift.metatron.Tokens.STR_TID;
import static studio.phaseshift.metatron.Tokens.TIME;
import static studio.phaseshift.metatron.Tokens.TYPE;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.dev.dckr.dckrInstSet.DOCKER_EVENT_TID;
import static studio.phaseshift.metatron.isa.m.dev.dckr.dckrInstSet.DOCKER_EVENT_TYPE;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_DATETIME_TID;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_ISA_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * Tests for {@link ObjDockerSerializer}.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ObjDockerSerializerTest extends AbstractMetatronTest {

    private static final ObjDockerSerializer S = ObjDockerSerializer.single();

    @BeforeAll
    public static void setup() {
        Typer.Helper.set(Typer.CODE_RESOLVE, false);
        InstSet.importInstSet(MATH_ISA_TID);
        InstSet.importInstSet(dckrInstSet.DCKR_ISA_TID, f("d"));
    }

    private static Obj field(final String json, final String key) {
        return S.read(json).asRec().at(uri(key));
    }

    // ===================================================================
    // Sentinel → noobj
    // ===================================================================

    @ParameterizedTest
    @CsvSource(value = {
            "{\"v\":\"N/A\"}             % N/A",
            "{\"v\":\"none\"}            % none (lowercase)",
            "{\"v\":\"None\"}            % None (mixed case)",
            "{\"v\":\"\"}                % empty string",
            "{\"v\":\"n/a\"}             % n/a (lowercase)",
    }, delimiter = '%')
    void testSentinels(final String json, final String description) {
        assertEquals(noobj(), field(json, "v"), description);
    }

    // ===================================================================
    // Size → data::T (real with math TID)
    // ===================================================================

    @ParameterizedTest
    @CsvSource(value = {
            "{\"v\":\"227MB\"}     % /m/math/datasize/mB % 227.0   % MB → mB",
            "{\"v\":\"4.61GB\"}    % /m/math/datasize/gB % 4.61    % GB → gB",
            "{\"v\":\"1kB\"}       % /m/math/datasize/kB % 1.0     % kB → kB",
            "{\"v\":\"4096B\"}     % /m/math/datasize/bB % 4096.0  % plain bytes",
            "{\"v\":\"0.5TB\"}     % /m/math/datasize/tB % 0.5     % TB → tB",
            "{\"v\":\"0B\"}        % /m/math/datasize/bB % 0.0     % zero bytes",
    }, delimiter = '%')
    void testSizes(final String json, final String expectedVid,
                   final double expectedValue, final String description) {
        final Obj v = field(json, "v");
        assertTrue(v.isReal(), "should be real: " + v);
        assertEquals(expectedValue, v.realValue().doubleValue(), 0.001);
        assertEquals(expectedVid, v.tid().toString(), "TID mismatch: " + v);
    }

    // ===================================================================
    // Integer detection
    // ===================================================================

    @ParameterizedTest
    @CsvSource(value = {
            "{\"v\":\"0\"}         % 0     % zero",
            "{\"v\":\"1\"}         % 1     % one",
            "{\"v\":\"42\"}        % 42    % positive",
            "{\"v\":\"-5\"}        % -5    % negative",
    }, delimiter = '%')
    void testIntegers(final String json, final long expected, final String description) {
        final Obj v = field(json, "v");
        assertTrue(v.isInt(), "should be int: " + v);
        assertEquals(expected, v.intValue().longValue());
    }

    // ===================================================================
    // Datetime
    // ===================================================================

    @ParameterizedTest
    @CsvSource(value = {
            "{\"v\":\"2026-08-01T23:37:33-06:00\"}  % ISO with colon offset",
            "{\"v\":\"2024-12-25T09:00:00Z\"}       % UTC / Zulu",
            "{\"v\":\"2026-08-01 23:37:33 -0600\"}  % Docker space-separated",
            "{\"v\":\"<2024-12-25>\"}                  % date-only",
    }, delimiter = '%')
    void testDatetimes(final String json, final String description) {
        final Obj v = field(json, "v");
        assertTrue(v.isUri(), "should be uri: " + v);
        assertEquals(MATH_DATETIME_TID.toString(), v.tid().toString(), "should have datetime TID");
    }

    // ===================================================================
    // CamelCase → snake_case keys
    // ===================================================================

    @ParameterizedTest
    @CsvSource(value = {
            "{\"CreatedAt\":\"x\"}       % created_at       % simple CamelCase",
            "{\"SharedSize\":\"x\"}      % shared_size      % two words",
            "{\"ID\":\"x\"}              % id               % all-caps acronym",
            "{\"Repository\":\"x\"}      % repository       % single word unchanged",
            "{\"LocalVolumes\":\"x\"}    % local_volumes    % mid-word camel",
    }, delimiter = '%')
    void testSnakeCaseKeys(final String json, final String expectedKey,
                           final String description) {
        final Rec rec = S.read(json).asRec();
        assertFalse(rec.at(uri(expectedKey)).isNoObj(),
                "should have key '" + expectedKey + "' in " + rec.jvm().keySet());
    }

    // ===================================================================
    // Labels → Rec
    // ===================================================================

    @Test
    void testLabelsParsing() {
        final Obj labels = field("{\"Labels\":\"com.docker.project=my-stack,maintainer=NGINX\"}", "labels");
        assertTrue(labels.isRec(), "labels should be a rec");
        // A LABEL VALUE IS A str::T: docker labels are arbitrary text, never mtron
        assertEquals("my-stack", labels.asRec().at(uri("com.docker.project")).strValue());
        assertEquals("NGINX", labels.asRec().at(uri("maintainer")).strValue());
        assertEquals(STR_TID, labels.asRec().at(uri("com.docker.project")).tid());
    }

    /**
     * A LABEL IS TEXT, AND READING IT AS TEXT COSTS NOTHING. Every label value used to be speculatively parsed as
     * mtron (twice), and a value that is not an mtron literal — a version, a license, a description, a timestamp, a
     * source url — produced a parse ERROR. A parse error is a fail, and a fail files itself in the machine's fail
     * stack, so reading one image's labels added a dozen entries to a machine that had never failed. The text also
     * survived better: {@code 11.2.6} is not a real, and a number renders bare, so the version was unrecoverable.
     */
    @Test
    void testLabelValuesAreStringsAndKeepTheirText() {
        final Obj labels = field("{\"Labels\":\"org.opencontainers.image.version=11.2.6,"
                + "org.opencontainers.image.licenses=GPL-2.0,"
                + "description=MariaDB Database for relational SQL,"
                + "com.docker.compose.project.config_files=/home/me/stack/compose.yaml\"}", "labels");
        assertTrue(labels.isRec(), "labels should be a rec");
        labels.asRec().elements().forEach(kv -> {
            assertTrue(kv.second().isStr(), Graphitty.string("%s must be a str, got %s", kv.first(), kv.second().tid()));
            assertFalse(kv.second().isFail(), Graphitty.string("%s must not be a fail", kv.first()));
        });
        assertEquals("11.2.6", labels.asRec().at(uri("org.opencontainers.image.version")).strValue());
        assertEquals("GPL-2.0", labels.asRec().at(uri("org.opencontainers.image.licenses")).strValue());
        assertEquals("MariaDB Database for relational SQL", labels.asRec().at(uri("description")).strValue());
        assertEquals("/home/me/stack/compose.yaml",
                labels.asRec().at(uri("com.docker.compose.project.config_files")).strValue());
    }

    @Test
    void testLabelsSentinel() {
        assertEquals(noobj(), field("{\"Labels\":\"\"}", "labels"), "empty → noobj");
        assertEquals(noobj(), field("{\"Labels\":\"none\"}", "labels"), "none → noobj");
    }

    // ===================================================================
    // Nested objects
    // ===================================================================

    @Test
    void testNestedRec() {
        final Rec result = S.read(
                "{\"NetworkSettings\":{\"Networks\":{\"bridge\":{\"IPAddress\":\"172.17.0.2\"}}}}").asRec();
        LOG.info("nested result: {{b}}%s", result);
        // Verify snake_case at each level
        final Obj level1 = result.at(uri("network_settings"));
        assertFalse(level1.isNoObj(), "network_settings should exist");
        assertTrue(level1.isRec(), "network_settings should be rec: " + level1.type());
        final Obj level2 = level1.asRec().at(uri("networks"));
        assertFalse(level2.isNoObj(), "networks should exist");
        assertTrue(level2.isRec(), "networks should be rec: " + level2.type());
        final Obj level3 = level2.asRec().at(uri("bridge"));
        assertFalse(level3.isNoObj(), "bridge should exist");
    }

    // ===================================================================
    // Lists
    // ===================================================================

    @Test
    void testListRecursion() {
        final Obj ports = field("{\"Ports\":[\"8080:80\",\"443:443\"]}", "ports");
        assertTrue(ports.isLst());
        assertEquals(2, ports.asLst().elements().count());
    }

    // ===================================================================
    // Full docker ps row (integration)
    // ===================================================================

    @Test
    void testFullDockerPsRow() {
        final String json = "{"
                + "\"Command\":\"\\\"nginx -g\\\"\","
                + "\"CreatedAt\":\"2026-08-01 23:37:33 -0600\","
                + "\"ID\":\"a983e60013da\","
                + "\"Image\":\"nginx\","
                + "\"Labels\":\"maintainer=NGINX Docker Maintainers\","
                + "\"LocalVolumes\":\"0\","
                + "\"Mounts\":\"\","
                + "\"Names\":\"web-server\","
                + "\"Networks\":\"bridge\","
                + "\"Ports\":\"0.0.0.0:8080->80/tcp\","
                + "\"State\":\"running\","
                + "\"Status\":\"Up 2 hours\","
                + "\"Size\":\"0B\""
                + "}";
        final Rec rec = S.read(json).asRec();

        // snake_case keys
        assertFalse(rec.at(uri("created_at")).isNoObj());
        assertFalse(rec.at(uri("local_volumes")).isNoObj());
        assertFalse(rec.at(uri("names")).isNoObj());

        // sentinels
        assertEquals(noobj(), rec.at(uri("mounts")), "empty mounts → noobj");

        // integer
        assertTrue(rec.at(uri("local_volumes")).isInt());

        // datetime TID
        assertTrue(rec.at(uri("created_at")).isUri());
        assertEquals(MATH_DATETIME_TID.toString(),
                rec.at(uri("created_at")).tid().toString());

        // labels parsed
        assertTrue(rec.at(uri("labels")).isRec(), "labels is rec");
    }

    // ===================================================================
    // Edge cases
    // ===================================================================

    @Test
    void testWhitespaceTrimmed() {
        assertEquals(noobj(), field("{\"v\":\" N/A \"}", "v"), "surrounding spaces");
    }

    @Test
    void testUriPassthrough() {
        // Plain string becomes URI
        final Obj v = field("{\"v\":\"nginx\"}", "v");
        assertTrue(v.isUri());
        assertEquals("nginx", v.uriValue().name());
    }

    @Test
    void testUnparseableStaysString() {
        // String with : and special chars that can't be a URI stays str
        final Obj v = field("{\"v\":\"0.0.0.0:8080->80/tcp, [::]:8080->80/tcp\"}", "v");
        assertTrue(v.isStr() || v.isUri(), "should be str or uri: " + v.type());
    }

    // ===================================================================
    // Numeric-looking strings are strings (2026-09-13)
    // ===================================================================

    /**
     * Real rows from {@code docker image ls --format json}, where every field is a JSON string — including a tag
     * that looks numeric. The conversion used to hand each untyped string to the mtron parser, which read
     * {@code "11.2"} as a <b>real</b>: rendered {@code 11.2000} and keyed {@code <mariadb:11.2000>}, so the image
     * could not be addressed by the tag docker reports. It was invisible from the JSON face, where a number and a
     * string both render as a literal.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "{\"Tag\":\"11.2\"}  % 11.2  % a version-looking tag is a tag",
            "{\"Tag\":\"24.04\"} % 24.04 % an ubuntu-style version is not a number either",
            "{\"Tag\":\"16\"}    % 16    % nor is a numeric tag",
    }, delimiter = '%')
    void testNumericLookingTagsStayStrings(final String json, final String expected, final String desc) {
        final Obj tag = field(json, "tag");
        LOG.info("tag => %s [%s]", tag, tag.tid());
        assertFalse(tag.isReal(), desc + ": must not be a real, got " + tag);
        assertFalse(tag.isInt(), desc + ": must not be an int, got " + tag);
        assertEquals(expected, tag.isStr() ? tag.strValue() : tag.uriValue().toString(), desc);
    }

    /**
     * The same path legitimately types other fields, so keeping tags as tags must not stop a size becoming a
     * datasize or a count an integer.
     */
    @Test
    void testDeclaredConversionsSurvive() {
        assertEquals("mB", field("{\"Size\":\"405MB\"}", "size").tid().name());
        assertEquals("gB", field("{\"Size\":\"1.02GB\"}", "size").tid().name());
        assertEquals(1, field("{\"Containers\":\"1\"}", "containers").intValue().intValue());
    }

    // ===================================================================
    // docker events — dckrevent::T
    // ===================================================================

    private static String eventLine(final String action, final String exitCode) {
        final String attrs = exitCode == null
                ? "\"image\":\"nginx:alpine\",\"name\":\"web-server\""
                : "\"exitCode\":\"" + exitCode + "\",\"image\":\"nginx:alpine\",\"name\":\"web-server\"";
        return "{\"status\":\"" + action + "\",\"id\":\"a1b2c3d4e5f6\",\"from\":\"nginx:alpine\","
                + "\"Type\":\"container\",\"Action\":\"" + action + "\","
                + "\"Actor\":{\"ID\":\"a1b2c3d4e5f6\",\"Attributes\":{" + attrs + "}},"
                + "\"scope\":\"local\",\"time\":1699000000,\"timeNano\":1699000000000000000}";
    }

    @ParameterizedTest
    @CsvSource(value = {
            "create    % -   % create emits no exit",
            "start     % -   % start emits no exit",
            "die       % 0   % clean exit",
            "die       % 137 % killed exit",
            "exec_die  % 1   % exec exit",
    }, delimiter = '%')
    void testEventShape(final String action, final String exitCode, final String desc) {
        final Rec rec = S.readEvent(eventLine(action, "-".equals(exitCode) ? null : exitCode)).asRec();
        assertEquals(DOCKER_EVENT_TID, rec.tid(), desc + ": typed dckrevent");
        assertEquals("container", rec.at(uri(TYPE)).uriValue().toString(), desc + ": type");
        assertTrue(rec.at(uri(ACTION)).isStr(), desc + ": action is str");
        assertEquals(action, rec.at(uri(ACTION)).strValue(), desc + ": action text");
        assertTrue(rec.at(uri(TIME)).isUri(), desc + ": time is uri (datetime)");
        assertEquals(MATH_DATETIME_TID, rec.at(uri(TIME)).tid(), desc + ": time is datetime::T");
        final Obj objF = rec.atDirect(uri(OBJ));
        assertTrue(Obj.Helper.isAutoPointer(objF),
                desc + ": obj is !* (raw=" + objF + ")");
        assertEquals("docker:container/web-server", Obj.Helper.autoPointerVID(objF).toString(),
                desc + ": obj is name-keyed");
        if ("-".equals(exitCode))
            assertTrue(rec.at(uri(EXIT)).isNoObj(), desc + ": no exit");
        else
            assertEquals(Long.parseLong(exitCode), rec.at(uri(EXIT)).intValue().longValue(), desc + ": exit");
    }

    /**
     * A docker event's {@code time} must be a true {@code datetime::T} — not an epoch int that merely looks typed —
     * or a refinement like {@code ?>(datetime_now() - hour::1)} will not type-check.
     */
    @Test
    void testEventTimeIsTrueDatetime() {
        final Obj time = S.readEvent(eventLine("die", "0")).asRec().at(uri(TIME));
        assertFalse(time.isInt(), "time must not be an epoch int");
        assertEquals(MATH_DATETIME_TID, time.tid(), "time carries datetime::T");
    }

    /**
     * The actor attribute {@code exitCode} is text on the wire, but the first-class {@code exit} it names is the int
     * you filter on — {@code *docker:event/+.?[exit => ?>(0)]}.
     */
    @Test
    void testExitCodeIsSurfacedAsInt() {
        final Rec rec = S.readEvent(eventLine("die", "0")).asRec();
        assertEquals(0L, rec.at(uri(EXIT)).intValue().longValue(), "first-class exit is int");
    }

    /**
     * The typed rec matches its own type, so {@code .matches(docker_event::T)} and a type-headed filter are the same
     * act and the tid survives a filter.
     */
    @Test
    void testEventMatchesType() {
        final Rec rec = S.readEvent(eventLine("die", "137")).asRec();
        assertTrue(rec.test(DOCKER_EVENT_TYPE), "a die event rec matches docker_event::T");
    }

    /**
     * The datetime VALUE must be the epoch instant, not merely a tid. {@code timeNano} is full epoch nanos, so
     * {@code ofEpochSecond(0, timeNano)} — not {@code ofEpochSecond(time, timeNano)}, which doubles the epoch.
     */
    @Test
    void testEventTimeValue() {
        final long nanos = 1700000000L * 1_000_000_000L;
        final String line = "{\"status\":\"die\",\"id\":\"a1\",\"from\":\"nginx\",\"Type\":\"container\","
                + "\"Action\":\"die\",\"Actor\":{\"ID\":\"a1\",\"Attributes\":{\"exitCode\":\"0\",\"name\":\"web-server\"}},"
                + "\"scope\":\"local\",\"time\":1700000000,\"timeNano\":" + nanos + "}";
        final Obj time = S.readEvent(line).asRec().at(uri(TIME));
        final Obj expected = mathInstSet.buildDatetimeUri(Instant.ofEpochSecond(0, nanos).atZone(ZoneId.systemDefault()));
        assertEquals(expected, time, "datetime value must be the epoch instant");
    }

    /**
     * A filter over a seeded space: {@code *evt/+.?[action=>?=die]} answers exactly the die recs, each still typed
     * {@code docker_event::T}. This proves typing is load-bearing (not decorative) and that the filter runs without a
     * daemon.
     */
    @Test
    void testEventFilterByAction() {
        final memSpace space = memSpace.of(f("evt:#"), f("/sys/space/evt"));
        space.write(f("evt:0"), S.readEvent(eventLine("die", "137")));
        space.write(f("evt:1"), S.readEvent(eventLine("create", null)));
        space.write(f("evt:2"), S.readEvent(eventLine("die", "0")));
        space.write(f("evt:3"), S.readEvent(eventLine("exec_die", "1")));

        final Obj all = ObjmtronSerializer.eval("*evt:+");
        assertEquals(4, all.stream().count(), "seed read returns 4 recs");

        final Obj dies = ObjmtronSerializer.eval("*evt:+.?[action => 'die']");
        assertEquals(2, dies.stream().count(), "two die events");
        dies.stream().forEach(d -> assertEquals(DOCKER_EVENT_TID, d.tid(), "filter preserves docker_event::T"));
    }

    /**
     * {@code action} must tolerate STRINGS: docker puts the exec command into the action field for
     * {@code exec_start}/{@code exec_die}, so {@code action => "exec_start: node -e …"} is not an address. The rec
     * must still match {@code docker_event::T} and the text must survive intact.
     */
    @Test
    void testExecStartActionSurvivesAsStr() {
        final String cmd = "exec_start: node -e Promise.all([fetch('http://127.0.0.1:3080')])";
        final String line = "{\"status\":\"exec_start\",\"id\":\"a1\",\"from\":\"nginx\",\"Type\":\"container\","
                + "\"Action\":\"" + cmd + "\","
                + "\"Actor\":{\"ID\":\"a1\",\"Attributes\":{\"name\":\"web-server\"}},"
                + "\"scope\":\"local\",\"time\":1700000000,\"timeNano\":1700000000000000000}";
        final Rec rec = S.readEvent(line).asRec();
        assertTrue(rec.test(DOCKER_EVENT_TYPE), "exec_start event still matches docker_event::T");
        assertTrue(rec.at(uri(ACTION)).isStr(), "action is str");
        assertEquals(cmd, rec.at(uri(ACTION)).strValue(), "action text survives intact");
    }

    /**
     * A computed numeric refinement: {@code exit => ?>(0)} (is-greater-than) runs per candidate and answers only the
     * events with a positive exit code.
     */
    @Test
    void testEventFilterNumericRefinement() {
        final memSpace space = memSpace.of(f("evt2:#"), f("/sys/space/evt2"));
        space.write(f("evt2:0"), S.readEvent(eventLine("die", "137")));
        space.write(f("evt2:1"), S.readEvent(eventLine("create", null)));
        space.write(f("evt2:2"), S.readEvent(eventLine("die", "0")));
        space.write(f("evt2:3"), S.readEvent(eventLine("exec_die", "1")));

        final Obj positive = ObjmtronSerializer.eval("*evt2:+.?[exit => ?>(0)]");
        assertEquals(2, positive.stream().count(), "exit>0 = the 137 and the 1");
        positive.stream().forEach(d -> assertEquals(DOCKER_EVENT_TID, d.tid(), "refinement preserves docker_event::T"));
    }
}
