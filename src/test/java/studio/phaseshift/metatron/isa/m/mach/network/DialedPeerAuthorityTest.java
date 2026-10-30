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

package studio.phaseshift.metatron.isa.m.mach.network;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.Training;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.Network;
import studio.phaseshift.metatron.isa.m.web.space.ws.WebSocketRec;
import studio.phaseshift.metatron.isa.m.web.space.ws.WebSocketRecClient;
import studio.phaseshift.metatron.isa.m.web.type.MIME;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static studio.phaseshift.metatron.Tokens.HOST;
import static studio.phaseshift.metatron.Tokens.IN;
import static studio.phaseshift.metatron.Tokens.OUT;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.from_;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.start_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.m.web.space.ws.wsServer.WS_CLIENT_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * THE DIALED PEER, ACROSS A REAL PROCESS BOUNDARY. A console dials with a {@code ws_client}, whose peer is reached
 * through the instructions that client declares (its {@code inst}). The adapter that once stood between them used to
 * compute the outbound message HERE — a read
 * answered this machine's memory and a write landed in it — and no loopback suite could see it, because inside
 * one JVM the near store and the far store are the same store. A forked peer is the only witness, and a value
 * planted on each side of the same path is the only proof of which side answered.
 */
public class DialedPeerAuthorityTest extends AbstractMetatronTest {

    private static PeerCluster cluster;
    private static WebSocketRecClient client;

    @BeforeAll
    static void startPeer() throws Exception {
        // one peer, forked in its own JVM, holding $index/$index*10/$index*100 at /n/{a,b,c}
        cluster = PeerCluster.of(1).seed(PeerCluster.DEFAULT_SEED_MATRIX).start();
        // dialed as a console dials: the ws_client constructor indexes a peer::T into /network/peer
        final WebSocketRec wsRec = new WebSocketRec(mutableMap(
                uri(HOST), uri("ws://localhost:" + cluster.port(1) + PeerCluster.MTRON_ROUTE),
                uri(IN), uri(MIME.MIMEType.APPLICATION_MTRON.value),
                uri(OUT), uri(MIME.MIMEType.APPLICATION_MTRON.value)), WS_CLIENT_TID, null);
        client = new WebSocketRecClient(wsRec);
        Network.Helper.index(client);
    }

    @AfterAll
    static void stopPeer() {
        if (null != client)
            client.close();
        if (null != cluster)
            cluster.close();
    }

    /**
     * this machine's own address for a peer path: the same path, no authority
     */
    private static fURI here(final String path) {
        return f(cluster.root()).extend(path);
    }

    /**
     * the peer's address for a path, as a console spells it: ws://host:port/root/path
     */
    private static fURI there(final String path) {
        return cluster.prefix(1).extend(path);
    }

    /**
     * what the peer itself holds, asked over the cluster's own transport — one that has always shipped raw
     */
    private static Obj heldThere(final String path) {
        return cluster.transport(1).apply(from_(here(path)).tryToInst());
    }

    @Training.SkipTraining(reason = "the rows are store paths on a forked peer, not mtron expressions")
    @ParameterizedTest
    @CsvSource(value = {
            "a  % 1",   // $index
            "b  % 10",  // $index * 10
            "c  % 100", // $index * 100
    }, delimiter = '%')
    void testReadAnswersFromTheDialedPeer(final String path, final int expected) {
        assertEquals(jnt(expected), Machine.read(there(path)),
                "a ws:// read through a dialed peer must answer the peer's store, not this machine's");
    }

    @Test
    void testWriteLandsOnTheDialedPeer() {
        Machine.write(there("d"), jnt(42));
        assertEquals(jnt(42), heldThere("d"), "a ws:// write through a dialed peer must land in the peer's store");
        assertEquals(noobj(), Machine.read(here("d")), "and must leave this machine's own store untouched");
    }

    @Test
    void testReadNeverAnswersThisMachinesValue() {
        // the same path holds a different value on each side: only the peer's may come back
        Machine.write(here("e"), str("here"));
        try {
            cluster.transport(1).apply(start_(str("there")).to_(here("e")).tryToInst());
            assertEquals(str("there"), Machine.read(there("e")),
                    "a ws:// read must answer the peer's value, never this machine's at the same path");
        } finally {
            Machine.write(here("e"), noobj());
        }
    }
}
