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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.TypeCheck;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_ISA_TID;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_DATETIME_TID;
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
        TypeCheck.disable(TypeCheck.code_resolve);
        InstSet.importInstSet(MATH_ISA_TID);
        InstSet.importInstSet(dckrInstSet.DCKR_ISA_TID, f("d"));
    }

    private static Obj field(final String json, final String key) {
        return S.read(json).asRec().at(uri(key));
    }

    // ===================================================================
    // Sentinel → noobj
    // ===================================================================

    @ParameterizedTest(name = "[{index}] {1}")
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

    @ParameterizedTest(name = "[{index}] {4}")
    @CsvSource(value = {
        "{\"v\":\"227MB\"}     % /m/math/data/mB % 227.0   % MB → mB",
        "{\"v\":\"4.61GB\"}    % /m/math/data/gB % 4.61    % GB → gB",
        "{\"v\":\"1kB\"}       % /m/math/data/kB % 1.0     % kB → kB",
        "{\"v\":\"4096B\"}     % /m/math/data/bB % 4096.0  % plain bytes",
        "{\"v\":\"0.5TB\"}     % /m/math/data/tB % 0.5     % TB → tB",
        "{\"v\":\"0B\"}        % /m/math/data/bB % 0.0     % zero bytes",
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

    @ParameterizedTest(name = "[{index}] {3}")
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

    @ParameterizedTest(name = "[{index}] {1}")
    @CsvSource(value = {
        "{\"v\":\"2026-08-01T23:37:33-06:00\"}  % ISO with colon offset",
        "{\"v\":\"2024-12-25T09:00:00Z\"}       % UTC / Zulu",
        "{\"v\":\"2026-08-01 23:37:33 -0600\"}  % Docker space-separated",
        "{\"v\":\"2024-12-25\"}                  % date-only",
    }, delimiter = '%')
    void testDatetimes(final String json, final String description) {
        final Obj v = field(json, "v");
        assertTrue(v.isUri(), "should be uri: " + v);
        assertEquals(MATH_DATETIME_TID.toString(), v.tid().toString(), "should have datetime TID");
    }

    // ===================================================================
    // CamelCase → snake_case keys
    // ===================================================================

    @ParameterizedTest(name = "[{index}] {3}")
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
        assertEquals("my-stack", labels.asRec().at(uri("com.docker.project")).uriValue().name());
        assertEquals("NGINX", labels.asRec().at(uri("maintainer")).uriValue().name());
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
}
