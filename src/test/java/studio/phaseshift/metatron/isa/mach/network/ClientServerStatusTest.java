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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.Network;
import studio.phaseshift.metatron.isa.mach.type.network.Listener;
import studio.phaseshift.metatron.isa.web.type.mcpServer;
import studio.phaseshift.metatron.util.MTronException;

import java.util.LinkedHashMap;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.webInstSet.MCP_SERVER_TID;

/**
 * EVERY CLIENT AND EVERY SERVER ANSWERS {@code status} (Marko, 2026-10-24: "client.status() and server.status() should
 * exist"). It is a bool, true while the obj is live, DERIVED ON READ and never stored: the constructor installs it
 * ({@link Network.Helper#statusOf}), and the network reads it ({@link Network.Helper#status}) wherever it once asked a
 * peer's transport — {@code online}, the shard targets, the status report.
 * <p>
 * The protocol clients are pinned where their far sides live — the ws client in {@code NetworkWsDialTest}, the http and
 * loop clients in {@code NetworkConnectTest}. This suite pins the server half (a listener), the transport-agnostic mcp
 * server, the field as mtron reads it, and the reader's own rules.
 */
public class ClientServerStatusTest extends AbstractMetatronTest {

    private static final fURI LOOP = f("loop://statusq");

    @AfterEach
    void hangUp() {
        Machine.current().network().drop(LOOP);
    }

    /**
     * a listener with no socket behind it — the protocol-agnostic half every server (ws_server, http_server) extends
     */
    private static final class Socketless extends Listener {
        private Socketless(final fURI authority, final Network owner) {
            super(authority, REC_TID, owner);
        }

        @Override
        protected void unbind() {
            // no socket to release
        }
    }

    @Test
    void testAServerIsLiveUntilItIsClosed() {
        final Listener server = new Socketless(f("ws://localhost:41999"), Machine.current().network());
        checkEquality(LOG, bool(true), bool(Network.Helper.status(server)), true);
        server.close();
        // derived on read: the same field answers false the moment the socket is released
        checkEquality(LOG, bool(false), bool(Network.Helper.status(server)), true);
    }

    @Test
    void testAnMcpServerIsLiveWhileItsDispatchAnswers() {
        // a transport-agnostic server holds no socket of its own: its status asks its own dispatch a json-rpc ping
        final Obj server = new mcpServer(new LinkedHashMap<>(), MCP_SERVER_TID, null);
        checkEquality(LOG, bool(true), bool(Network.Helper.status(server)), true);
    }

    @Test
    void testTheStatusFieldReadsAsItsValue() {
        // the auto wrapper makes the field a value: reading it answers the bool, not the inst that computes it
        final Obj client = Network.Helper.connect(Machine.current().network(), LOOP).asRec().at(uri(CLIENT));
        checkEquality(LOG, bool(true), client.asRec().at(uri(STATUS)), true);
    }

    @Test
    void testOnlyAStatusThatSaysTrueIsLive() {
        // a stored bool is read as it stands
        checkEquality(LOG, bool(true), bool(Network.Helper.status(rec(uri(STATUS), bool(true)))), true);
        checkEquality(LOG, bool(false), bool(Network.Helper.status(rec(uri(STATUS), bool(false)))), true);
        // no status at all: the obj has not vouched for itself, and a link would have been asked instead
        checkEquality(LOG, bool(false), bool(Network.Helper.status(rec(uri(HOST), uri(LOOP)))), true);
        // a status that cannot be computed is not live, and neither is a status that is not a bool
        checkEquality(LOG, bool(false), bool(Network.Helper.status(rec(uri(STATUS), Network.Helper.statusOf(() -> {
            throw MTronException.of("the far side is gone");
        })))), true);
        checkEquality(LOG, bool(false), bool(Network.Helper.status(rec(uri(STATUS), jnt(1)))), true);
        // and nothing that is not a rec has a status
        checkEquality(LOG, bool(false), bool(Network.Helper.status(jnt(1))), true);
    }
}
