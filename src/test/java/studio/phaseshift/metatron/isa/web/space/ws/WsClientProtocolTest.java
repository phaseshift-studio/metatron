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

import studio.phaseshift.metatron.isa.mach.type.network.Listener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.util.CommonUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.PRINT_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.space.ws.handler.mtron_wsHandler.WS_MTRON_HANDLER_TID;
import static studio.phaseshift.metatron.isa.web.webInstSet.WEB_ISA_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * A {@code ws_client} with a protocol hat: {@code protocol => mtron_ws} installs the mtron facade's
 * apply-and-respond {@code on_message} (instead of the {@code print} default), so the client can answer a
 * server-initiated request over the established socket — the client half of a symmetric peer.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
public class WsClientProtocolTest extends AbstractMetatronTest {

    private Listener space;
    private int port;

    @AfterEach
    void tearDown() {
        if (null != this.space) {
            Machine.current().network().disconnect(f("ws://localhost:" + this.port));
            this.space.close();
            this.space = null;
        }
    }

    /**
     * Boot a local mtron server and dial it with the given {@code ws_client} expression, answering the client.
     */
    private Obj dial(final String clientExpr) {
        InstSet.importInstSet(WEB_ISA_TID);
        this.port = generatePort();
        this.space = wsServer.of(rec(mutableMap(
                        uri(HOST), uri("ws://localhost:" + this.port),
                        uri(ROUTE), rec(uri("/mtron"), uri(WS_MTRON_HANDLER_TID.toString())))));
        CommonUtil.sleepThread(500);
        return ObjmtronSerializer.parse(clientExpr.replace("{{port}}", String.valueOf(this.port))).apply();
    }

    @Test
    void testProtocolMtronWsInstallsApplyAndRespondHandler() {
        final Obj client = this.dial("ws_client::[host=><ws://localhost:{{port}}/mtron>,protocol=>mtron_ws]@self");
        final java.util.Map<Obj, Obj> jvm = client.asRec().jvm();
        // the full mtron surface, not just the on_message lambda
        assertNotEquals(PRINT_INST_TID, jvm.get(uri(ON_MESSAGE)).tid().basePath(),
                "protocol=>mtron_ws must replace the print default on_message, got tid " + jvm.get(uri(ON_MESSAGE)).tid());
        assertTrue(jvm.containsKey(uri(ON_ERROR)), "protocol=>mtron_ws must install the on_error callback");
        assertTrue(jvm.containsKey(uri(STATE)), "protocol=>mtron_ws must install the state callback");
    }

    @Test
    void testNoProtocolKeepsPrintDefault() {
        final Obj client = this.dial("ws_client::[host=><ws://localhost:{{port}}/mtron>]@self");
        final Obj onMessage = client.asRec().jvm().get(uri(ON_MESSAGE));
        assertEquals(PRINT_INST_TID, onMessage.tid().basePath(),
                "a protocol-less ws_client must keep the print default on_message");
    }
}
