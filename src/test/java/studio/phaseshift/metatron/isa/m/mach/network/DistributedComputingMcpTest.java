/*
 * metatron: a distributed virtual machine and language
 *  Copyright (C) 2025- PhaseShift Studio, LLC
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.isa.m.mach.network;

import org.junit.jupiter.api.BeforeAll;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.ai.llm.type.mcp.mcpClient;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;

import static studio.phaseshift.metatron.Tokens.HOST;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.m.web.webInstSet.MCP_CLIENT_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * THE MENU OVER MCP: each peer is dialed at its {@code /mcp} endpoint (mcp_mtron, on the same socket as {@code /mtron})
 * by an {@code mcp_client}, whose from, to and eval are mcp_mtron's tools (Marko, 2026-11-22: "any peer::T can have
 * custom implementation of common instructions ... from/to/eval being what is needed for universal computing as a
 * metatron node"). The rows are {@link AbstractDistributedComputingTest}'s, unchanged: a lane reaches its peer through
 * whatever the peer's client declares.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class DistributedComputingMcpTest extends AbstractDistributedComputingTest {

    @BeforeAll
    static void connect() throws Exception {
        startPeers(new Protocol() {
            @Override
            public fURI endpoint(final int oneBased) {
                return f("").scheme("ws").host("localhost").port(cluster.port(oneBased)).extend(f("/mcp"));
            }

            /**
             * an {@code mcp_client} rec: a value typed but never constructed, so building it dials nothing. Instantiated
             * rather than retyped: a clone into a type with a constructor constructs it (Obj.Helper.objClone), and here
             * that would dial every edge's target from this machine.
             */
            @Override
            public Obj clientRec(final int oneBased) {
                return new MRec(mutableMap(uri(HOST), uri(this.endpoint(oneBased))), MCP_CLIENT_TID, null);
            }

            /**
             * the constructor dials, lists the server's tools, declares from, to and eval, and indexes itself
             */
            @Override
            public AutoCloseable dial(final int oneBased) {
                return new mcpClient(mutableMap(uri(HOST), uri(this.endpoint(oneBased))), MCP_CLIENT_TID, null);
            }
        });
    }
}
