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

package studio.phaseshift.metatron.isa.mach.network;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.Network;
import studio.phaseshift.metatron.isa.web.space.ws.WebSocketRec;
import studio.phaseshift.metatron.isa.web.space.ws.WebSocketRecClient;
import studio.phaseshift.metatron.isa.web.space.ws.wsSpace;
import studio.phaseshift.metatron.isa.web.type.MIME;
import studio.phaseshift.metatron.util.CommonUtil;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.space.ws.handler.mtron_wsHandler.WS_MTRON_HANDLER_TID;
import static studio.phaseshift.metatron.isa.web.space.ws.wsSpace.WS_CLIENT_TID;
import static studio.phaseshift.metatron.isa.web.webInstSet.WEB_ISA_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * A self-loop — a {@code ws_client} dialing this machine's own server — must hold ONE peer, not two. The dialer's
 * constructor indexes the OUTBOUND peer (keyed by our own authority); the listener must not co-mount a second
 * inbound peer keyed by the dialer's ephemeral source port, or sharding would fan out over a socket nobody answers.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
public class SelfLoopPeerTest extends AbstractMetatronTest {

    @Test
    void testSelfLoopDialMintsOnePeer() {
        InstSet.importInstSet(WEB_ISA_TID);
        final int port = generatePort();
        wsSpace space = null;
        try {
            space = wsSpace.of(mutableMap(
                            uri(PATTERN), uri("ws://#"),
                            uri(HOST), uri("ws://localhost:" + port),
                            uri(ROUTE), rec(uri("/mtron"), uri(WS_MTRON_HANDLER_TID.toString()))),
                    f("/sys/space/ws/selfloop"));
            CommonUtil.sleepThread(500);

            // the idiomatic self-loop dial (ws_client::[host=><ws://localhost:<port>/mtron>]): the constructor
            // dials, then indexes the OUTBOUND peer; the listener must decline its own co-mount.
            final WebSocketRec wsRec = new WebSocketRec(mutableMap(
                    uri(HOST), uri("ws://localhost:" + port + "/mtron"),
                    uri(IN), uri(MIME.MIMEType.APPLICATION_MTRON.value),
                    uri(OUT), uri(MIME.MIMEType.APPLICATION_MTRON.value)), WS_CLIENT_TID, null);
            final WebSocketRecClient client = new WebSocketRecClient(wsRec);
            Network.Helper.index(client);
            CommonUtil.sleepThread(500);

            final Set<fURI> peers = Network.Helper.peerAuthorities(Machine.current().network());
            assertEquals(1, peers.size(), "a self-loop must mint exactly one peer, got: " + peers);
            final fURI only = peers.iterator().next();
            assertTrue("localhost".equals(only.host()) && only.port() == port,
                    "the sole peer must be the authority we dialed (our own), got: " + only);
        } finally {
            if (null != space) {
                Machine.current().network().disconnect(f("ws://localhost:" + port));
                Machine.current().memory().removeSpace(space.vid());
                space.close();
            }
        }
    }
}
