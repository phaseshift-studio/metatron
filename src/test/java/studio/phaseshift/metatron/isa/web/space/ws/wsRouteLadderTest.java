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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * Characterization of {@code wsSpace.createServer}'s route dispatch — the ws twin of
 * {@code httpRouteLadderTest}, pinned *before* the ladder is replaced by protocol projections (P1).
 * <p>
 * The ws ladder is separate code with its own semantics: it matches route keys by <em>prefix</em>
 * ({@code routePath.qLess().hasPrefix(key)}), takes the <em>first</em> match in map-iteration order (so {@code /}
 * can shadow a longer key), resolves the value through {@code Space.Helper.resolveApply} →
 * {@code Router.readFromSpace}, materializes an {@code mcp_server} type, and throws
 * {@code "websocket handler type required"} for any value that is neither a {@code Type} nor an
 * {@code mcpServer} instance.
 * <p>
 * Two lanes are characterized, being the ones P1 must preserve:
 * <ul>
 *   <li>{@code /mcp} — an {@code mcp_server} type is materialized and wrapped in {@code mcp_wsHandler}, so a
 *   json-rpc request over the socket gets a json-rpc reply;</li>
 *   <li>{@code /mtron} — a handler {@code Type} with a constructor is built per connection, so an mtron
 *   expression sent over the socket evaluates and returns. This lane exists only on ws, and it also re-verifies
 *   that {@code WS_MTRON_HANDLER_TYPE}'s re-parenting onto {@code mtron::T} left it constructible.</li>
 * </ul>
 * Runs on the standard ws integration scaffolding (ephemeral port, settled server, latched client), so it never
 * collides with a running server.
 */
@Timeout(60)
class wsRouteLadderTest extends AbstractWebSocketServerIntegrationTest {

    private static final String INITIALIZE =
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}";

    @Override
    protected wsSpace createWSSpace() {
        return wsSpace.of(mutableMap(
                uri(PATTERN), uri(f("ws://#")),
                uri(HOST), uri(f("ws://127.0.0.1:" + generatePort())),
                uri(ROUTE), rec(
                        uri(f("/mcp")), uri(f("mcp_mtron")),
                        uri(f("/mtron")), uri(f("mtron_ws")))), 
                f("/sys/space/test/wsRouteLadder"));
    }

    /**
     * The inherited 5s latch is marginal for this class: the {@code /mcp} lane materializes an {@code mcp_server}
     * on the first connection, and under a loaded container (a batch of a dozen classes) that has been observed to
     * cross 5s — which surfaces as a null response and an NPE on {@code response.contains(...)}, not as anything
     * that names the cause. Observed once, at 5.016s, passing in isolation; the http integration base allows 10s
     * for the same reason.
     */
    @Override
    protected int getWsTimeoutSeconds() {
        return 15;
    }

    /** an mcp_server type is materialized and wrapped in mcp_wsHandler */
    @Test
    void testMcpLaneHandshakes() throws Exception {
        connectToServer("/mcp");
        final String response = sendAndReceive(INITIALIZE);
        assertEquals(true, response.contains("protocolVersion"), response);
    }

    /** a handler Type with a constructor is built per connection: mtron expressions evaluate */
    @Test
    void testMtronLaneEvaluates() throws Exception {
        connectToServer("/mtron");
        assertEquals("3", sendAndReceive("1.plus(2)"));
    }
}
