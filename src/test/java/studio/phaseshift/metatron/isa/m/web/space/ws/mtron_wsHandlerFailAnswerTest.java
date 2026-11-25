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
import studio.phaseshift.metatron.isa.m.mach.type.network.Listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.m.web.space.ws.handler.mtron_wsHandler.WS_MTRON_HANDLER_TID;

/**
 * A FRAME IS ALWAYS ANSWERED, AND THE SESSION SURVIVES THE ANSWER.
 * <p>
 * A failure that escapes the evaluation as an {@code Error} — a {@code StackOverflowError} from a
 * description that re-entered itself, an OOM — is not an {@code Exception}, so every guard on the way
 * out ({@code mtron_on_message}, {@code WebSocketRec.onMessage}, {@code mtron_wsHandler.evaluate})
 * stepped aside and it reached the worker's own guard, whose documented job is to DROP the connection:
 * "nothing may reach the socket's worker thread". The client then saw a closed socket (1006) and no
 * reason at all. That is how a remote {@code sys_stat()} whose {@code free_jvm} was a real wearing
 * {@code percent::T} looked like a dead machine: the type failure's own MESSAGE popped the stack
 * before any fail could be built.
 * <p>
 * The frame answers {@code fail::T} instead, and the next frame is served on the same session. This
 * is a top-level class on purpose: a static nested subclass of
 * {@link AbstractWebSocketServerIntegrationTest} is not selected by Surefire's file-name scan, so it
 * would never run.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mtron_wsHandlerFailAnswerTest extends AbstractWebSocketServerIntegrationTest {

    @Override
    protected Listener createWSServer() {
        return wsServer.of(rec(
                uri(NAME), uri("mtron-fail-answer"),
                uri(ORIGIN), uri("ws://localhost:" + generatePort()),
                uri(ROUTE), rec(uri("/mtron"), uri(WS_MTRON_HANDLER_TID.toString()))));
    }

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
