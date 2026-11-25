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

package studio.phaseshift.metatron.isa.m.web.space.ws;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.network.Listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.m.web.space.ws.handler.mtron_wsHandler.WS_MTRON_HANDLER_TID;

/**
 * THE mtron_ws ENDPOINT OVER THE WIRE: a session is held by its socket, and a frame is always answered.
 * <p>
 * TOP LEVEL ON PURPOSE. This class used to be {@code mtron_wsHandlerTest.mtron_wsHandlerIntegrationTest}, a
 * static nested subclass of {@link AbstractWebSocketServerIntegrationTest} — and Surefire's file-name scan never
 * selects a nested class, so {@link #testASessionIsNoAddress()} had never run. A nested integration test is a
 * test that does not exist.
 * <p>
 * The second contract is the boundary: a failure that escapes an evaluation as an {@code Error} is not an
 * {@code Exception}, so every {@code catch (Exception)} on the way out steps aside and the worker's own guard
 * drops the connection — the client saw code 1006 and no reason. The frame answers {@code fail::T} instead, and
 * the session keeps serving.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mtron_wsHandlerIntegrationTest extends AbstractWebSocketServerIntegrationTest {

    @Override
    protected Listener createWSServer() {
        return wsServer.of(rec(
                uri(NAME), uri("mtron-test"),
                uri(ORIGIN), uri("ws://localhost:" + generatePort()),
                uri(ROUTE), rec(uri("/mtron"), uri(WS_MTRON_HANDLER_TID.toString()))));
    }

    /**
     * a session is held by its socket, not an address: it answers over the wire, and nothing under the server's
     * authority reads it back (a wsspace cached its sessions at ws://host:port/...)
     */
    @Test
    public void testASessionIsNoAddress() throws Exception {
        this.connectToServer(this.primaryRoutePath());
        // an answer proves the server has opened the session: the client's open can lead the server's
        assertNotNull(this.sendAndReceive("1+2"), "the session should answer");
        checkEquality(LOG, Machine.read(f(WS + "://" + this.wsHost + ":" + this.wsPort + "/#")), noobj(), true);
    }

    /**
     * A FAILING FRAME IS ANSWERED, AND THE SESSION SURVIVES THE ANSWER. A parse error is the everyday case: it
     * used to be quiet on this endpoint, and the error-typed answer is the contract a client reads.
     */
    @Test
    public void testAFailingFrameIsAnsweredAndTheSessionSurvives() throws Exception {
        this.connectToServer(this.primaryRoutePath());
        final String failed = this.sendAndReceive("1.+");
        assertNotNull(failed, "a failing frame must still be answered");
        assertTrue(failed.contains("fail::"), "the answer is a fail: " + failed);
        final String after = this.sendAndReceive("1+2");
        assertNotNull(after, "the session keeps serving after a failure");
        assertEquals("3", after.trim(), "and answers the next frame");
    }
}
