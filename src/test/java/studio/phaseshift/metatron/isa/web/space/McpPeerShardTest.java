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

package studio.phaseshift.metatron.isa.web.space;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.TypeCheck;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.type.mcp.mcpClient;
import studio.phaseshift.metatron.isa.m.math.cat.CatTheoryCompiler;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.web.space.http.httpSpace;
import studio.phaseshift.metatron.isa.web.type.mcpServer;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.EVAL_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.webInstSet.MCP_SERVER_TID;
import static studio.phaseshift.metatron.isa.web.webInstSet.WEB_ISA_TID;

/**
 * THE MCP SERVER AS A DISTRIBUTED PEER. An {@link mcpServer} exposing the {@code eval} instruction (the code on the
 * lhs) is dialed by an {@link mcpClient} whose {@code inst_map} wires that tool onto {@code /m/inst/eval}; the client
 * is indexed as a peer, so {@code law_shard} ships its slave through {@code eval(<target>)} and the MCP server
 * evaluates it — the same role a {@code ws_client} plays, over the MCP tool transport instead of a raw socket.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
public class McpPeerShardTest extends AbstractMetatronTest {

    private static final int PORT = 18877;

    private httpSpace space;
    private mcpClient client;
    private mcpServer server;

    @BeforeEach
    void setup() {
        TypeCheck.disable(TypeCheck.values());
        InstSet.importInstSet(WEB_ISA_TID);
        InstSet.importInstSet(f("/m/math"));
        InstSet.importInstSet(f("/m/math/cat"));
        Machine.current().compiler(CatTheoryCompiler.single());
        // a server whose one tool IS the eval instruction (the code on the lhs), mounted as the mcp transport target
        this.server = new mcpServer(rec(uri(TOOL), lst(Machine.read(f("eval")))).jvm(), MCP_SERVER_TID, f("/test/mcp/shard"));
        Machine.write(f("/test/mcp/shard"), this.server);
        this.space = httpSpace.of(rec(
                uri(HOST), uri("http://localhost:" + PORT),
                uri(PATTERN), uri("http://#"),
                uri(ROUTE), rec(uri("/mcp"), uri("/test/mcp/shard"))), f("/sys/space/http/mcp-shard/test"));
        // the client: dial the server, and declare its eval tool IS the eval instruction
        this.client = new mcpClient(rec(
                uri(HOST), uri("http://localhost:" + PORT + "/mcp"),
                uri("inst_map"), rec(uri(EVAL_INST_TID), str("m_inst_eval"))).jvm(), null, null);
    }

    @AfterEach
    void teardown() {
        if (null != this.client) {
            this.client.close();
            this.client = null;
        }
        if (null != this.space) {
            Machine.current().memory().removeSpace(this.space.vid());
            this.space.close();
            this.space = null;
        }
    }

    @Test
    void testLawShardShipsToAnMcpPeer() {
        final Obj result = Machine.current().compiler().rewriter()
                .apply(ObjmtronSerializer.parse("{1,2,3,4,5,6}.plus(1).sum()").asCode())
                .apply();
        checkEquality(LOG, ObjmtronSerializer.parse("27"), result, true);
    }
}
