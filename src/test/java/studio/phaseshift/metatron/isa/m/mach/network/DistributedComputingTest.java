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
import studio.phaseshift.metatron.isa.m.mach.type.Network;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.web.space.ws.WebSocketRec;
import studio.phaseshift.metatron.isa.m.web.space.ws.WebSocketRecClient;
import studio.phaseshift.metatron.isa.m.web.type.MIME;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.m.web.space.ws.wsServer.WS_CLIENT_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * THE MENU OVER {@code mtron_ws}: each peer is dialed at its {@code /mtron} endpoint by a {@code ws_client}, whose from,
 * to and eval are the wire protocol itself ({@code WebSocketRec}). The rows are
 * {@link AbstractDistributedComputingTest}'s.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class DistributedComputingTest extends AbstractDistributedComputingTest {

    @BeforeAll
    static void connect() throws Exception {
        startPeers(new Protocol() {
            @Override
            public fURI endpoint(final int oneBased) {
                return cluster.dialURI(oneBased);
            }

            /**
             * a {@code ws_client} rec: data, so building it dials nothing
             */
            @Override
            public Obj clientRec(final int oneBased) {
                return new WebSocketRec(mutableMap(
                        uri(HOST), uri(this.endpoint(oneBased)),
                        uri(IN), uri(MIME.MIMEType.APPLICATION_MTRON.value),
                        uri(OUT), uri(MIME.MIMEType.APPLICATION_MTRON.value)), WS_CLIENT_TID, null);
            }

            /**
             * the constructor dials, and indexing mints the {@code peer::T} into {@code /network/peer}: the idiomatic
             * construction, spelled {@code ws_client::[host=>…]} in mtron
             */
            @Override
            public AutoCloseable dial(final int oneBased) {
                final WebSocketRecClient client = new WebSocketRecClient((WebSocketRec) this.clientRec(oneBased));
                Network.Helper.index(client);
                return client::close;
            }
        });
    }
}
