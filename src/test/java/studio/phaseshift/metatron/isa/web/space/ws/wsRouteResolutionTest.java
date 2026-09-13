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

package studio.phaseshift.metatron.isa.web.space.ws;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * Unit characterization of {@code wsSpace#resolveRoute} — all four lanes, called directly rather than through a
 * connection, so the {@code NONE} lane (the {@code "websocket handler type required"} case) is covered too.
 * <p>
 * The routes are deliberately empty — this test exercises resolution only, and never opens a socket.
 */
class wsRouteResolutionTest extends AbstractMetatronTest {

    private static final GraphittyLogger LOG = Graphitty.log(wsRouteResolutionTest.class);
    private static wsSpace space;

    @BeforeAll
    public static void bootEmptySpace() {
        InstSet.importInstSet(f("#"));
        space = wsSpace.of(mutableMap(
                uri(PATTERN), uri(f("ws://#")),
                uri(HOST), uri(f("ws://127.0.0.1:" + generatePort())),
                uri(ROUTE), rec()),
                f("/sys/space/test/wsRouteResolution"));
    }

    @AfterAll
    public static void closeEmptySpace() {
        if (null != space)
            space.close();
    }

    @ParameterizedTest
    @CsvSource(value = {
            "mcp_mtron     % MCP       % an mcp_server type is materialized into an instance, then wrapped",
            "mtron_ws      % CONSTRUCT % a handler type with a constructor is built per connection",
            "web_socket    % RETAG     % a handler type without a constructor is re-tagged with the session vid",
            "/m/web/helper % NONE      % anything else: the websocket handler type required case",
    }, delimiter = '%')
    void testResolveRoute(final String value, final String expectedKind, final String desc) {
        final Obj input = ObjmtronSerializer.parse(value);
        final wsSpace.RouteLane lane = space.resolveRoute(input);
        LOG.info("resolveRoute(%s) => %s", value, lane.kind());
        assertEquals(expectedKind, lane.kind().name(), desc);
    }

    /**
     * Resolving a ws route value against a connection — the handshake uri is the value's lhs, so a templated
     * value addresses per connection. Both rows are the <em>same</em> template answered for two handshakes, and
     * they land in different lanes ({@code CONSTRUCT} vs {@code RETAG}); a single value resolved once could only
     * ever produce one of them, which is what makes this per-connection rather than per-mount.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "</m/web/ws/${name()}> % /chat/mtron_ws  % CONSTRUCT % one connection: name() selects a handler type with a constructor",
            "</m/web/ws/${name()}> % /chat/web_socket % RETAG     % another connection: the same template selects one without, so the lane differs",
    }, delimiter = '%')
    void testResolveRouteAgainstConnection(final String value, final String requestUri, final String expectedKind,
                                          final String desc) {
        final wsSpace.RouteLane lane = space.resolveRoute(ObjmtronSerializer.parse(value), f(requestUri));
        LOG.info("resolveRoute(%s, %s) => %s [target=%s]", value, requestUri, lane.kind(), lane.target().toShortString());
        assertEquals(expectedKind, lane.kind().name(), desc);
    }
}
