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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.Network;
import studio.phaseshift.metatron.isa.mach.type.thread.FutureObj;
import studio.phaseshift.metatron.isa.web.type.MIME;
import studio.phaseshift.metatron.util.CommonUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.HOST;
import static studio.phaseshift.metatron.Tokens.IN;
import static studio.phaseshift.metatron.Tokens.OUT;
import static studio.phaseshift.metatron.Tokens.PATTERN;
import static studio.phaseshift.metatron.Tokens.ROUTE;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.space.ws.handler.mtron_wsHandler.WS_MTRON_HANDLER_TID;
import static studio.phaseshift.metatron.isa.web.space.ws.wsServer.WS_CLIENT_TID;
import static studio.phaseshift.metatron.isa.web.webInstSet.WEB_ISA_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * The TAP, not the ring: a real {@code sendRecv} against a real loopback peer must land in the trace as one
 * exchange in that peer's lane, carrying the request that was sent and the reply that came back.
 * <p>
 * {@link WireTraceTest} proves the window; this proves that the window is fed by the wire it claims to watch.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
public class WireTraceTapTest extends AbstractMetatronTest {

    @Test
    void shouldRecordARealRoundTripInItsPeerLane() {
        InstSet.importInstSet(WEB_ISA_TID);
        final int port = generatePort();
        Listener space = null;
        try {
            space = wsServer.of(rec(mutableMap(
                            uri(HOST), uri("ws://localhost:" + port),
                            uri(ROUTE), rec(uri("/mtron"), uri(WS_MTRON_HANDLER_TID.toString())))));
            CommonUtil.sleepThread(500);
            final WebSocketRec wsRec = new WebSocketRec(mutableMap(
                    uri(HOST), uri("ws://localhost:" + port + "/mtron"),
                    uri(IN), uri(MIME.MIMEType.APPLICATION_MTRON.value),
                    uri(OUT), uri(MIME.MIMEType.APPLICATION_MTRON.value)), WS_CLIENT_TID, null);
            final WebSocketRecClient client = new WebSocketRecClient(wsRec);
            Network.Helper.index(client);
            CommonUtil.sleepThread(300);
            // from here on, whatever crosses the wire is this test's
            WireStats.reset();
            WireTrace.clear();

            final Obj reply = FutureObj.resolveFuture(wsRec.sendRecv(ObjmtronSerializer.parse("1.plus(1)")));
            assertFalse(reply.isNoObj(), "the peer answered");
            assertFalse(reply.isFail(), "the peer answered without failing: " + reply);

            final WireTrace.Snapshot snapshot = WireTrace.snapshot(20);
            final WireTrace.Lane lane = snapshot.lanes().stream()
                    .filter(l -> l.peer().contains("localhost:" + port))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no lane for the dialed peer in " + snapshot.lanes()));
            assertEquals(1L, lane.exchanges(), "one round trip, one exchange in the lane");
            assertEquals(1L, snapshot.roundTrips(), "and one completed round trip in the window's count");
            // A SELF-LOOP SENDS BOTH HALVES FROM THIS VM: the request leaves the client and the reply leaves the
            // server, and each also arrives here. So a wire ledger sees two frames each way for one conversation —
            // which is what a FRAME count means, and why the panel's unit is the exchange, not the frame.
            assertEquals(2L, WireStats.sent(), "the request left, and so did the reply (same VM)");
            assertEquals(2L, WireStats.received(), "the request arrived, and so did the reply (same VM)");

            final WireTrace.Row row = lane.rows().getFirst();
            assertTrue(row.ok(), "the row records a completed trip");
            assertEquals("send_recv", row.kind(), "as a round trip, not a push");
            assertTrue(row.request().contains("plus"), "the row holds the request that was sent: " + row.request());
            // the reply is anchored to the tag it was asked under: `2@<tag>` — the answer, then its correlation
            assertTrue(row.reply().startsWith("2@"), "and the answer the peer computed, anchored to its tag: " + row.reply());
            assertNotNull(row.tag(), "the tag is the address the request was correlated by");
            assertEquals(0L, lane.timeouts(), "nothing timed out");
        } finally {
            Machine.current().network().disconnect(f("ws://localhost:" + port));
            if (null != space) {
                space.close();
            }
        }
    }
}
