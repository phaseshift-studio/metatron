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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.Network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static studio.phaseshift.metatron.Tokens.ORIGIN;
import static studio.phaseshift.metatron.Tokens.KIND;
import static studio.phaseshift.metatron.Tokens.MACHINE;
import static studio.phaseshift.metatron.Tokens.STATUS;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * THE AUTHORITY DISPATCH ON {@code Network}, end to end: an address written against a peer's authority
 * ({@code ws://localhost:PORT/...}) is handed to the client this VM dialed for that peer and answered over the wire, instead of
 * resolving against the local memory. Peers are real OS processes ({@link PeerCluster}), so the only thing
 * between this VM and a peer's store is the socket.
 * <p>
 * Two behaviors pinned, both of which a local read cannot show:
 * <ul>
 *   <li>a {@code ws://} READ reaches the peer's store and returns what the peer holds;</li>
 *   <li>a {@code ws://} WRITE lands on the peer and reads back from there — not from this VM.</li>
 * </ul>
 */
public class NetworkTest extends AbstractMetatronTest {

    private static PeerCluster cluster;

    @BeforeAll
    static void startPeers() throws Exception {
        // one peer, forked in its own JVM, holding $index/$index*10/$index*100 at /n/{a,b,c}
        cluster = PeerCluster.of(1).seed(PeerCluster.DEFAULT_SEED_MATRIX).start();
        // declare the roster (ws://localhost:PORT => its address) and dial the peer: Network.read/write dispatch
        // through the ws_client the dial constructs
        cluster.dial();
    }

    @AfterAll
    static void stopPeers() {
        if (null != cluster)
            cluster.close();
    }

    @ParameterizedTest
    @CsvSource(value = {
            "a  % 1",   // $index
            "b  % 10",  // $index * 10
            "c  % 100", // $index * 100
    }, delimiter = '%')
    void testReadAcrossPeer(final String path, final int expected) {
        final Obj value = Machine.read(cluster.prefix(1).extend(path));
        assertEquals(jnt(expected), value, "a ws:// read dispatches to the peer's store");
    }

    @Test
    void testWriteAcrossPeer() {
        final fURI vid = cluster.prefix(1).extend("d");
        Machine.write(vid, jnt(42));
        assertEquals(jnt(42), Machine.read(vid), "a ws:// write lands on the peer and reads back");
    }

    /**
     * A DISCOVERED machine is an ADDRESS in the roster, not a dialed client, so probing it must not report a
     * live machine as down. "known" is that third state; "down" stays reserved for a client that was asked and
     * is not live.
     */
    @Test
    void testAnAddressInTheRosterIsKnownNotDown() {
        final Obj declared = Machine.read(Network.ROOT_ROSTER);   // this class's own peer cluster lives here
        try {
            Machine.write(Network.ROOT_ROSTER, rec(mutableMap(uri(MACHINE), rec(mutableMap(
                    uri("ws://metatron.local."), uri("ws://10.0.0.1:8777/"))))));
            final Obj report = Machine.current().network().status(noobj());
            final Rec entry = report.stream().map(Obj::asRec)
                    .filter(one -> "ws://metatron.local.".equals(one.at(uri(ORIGIN)).uriValue().toString()))
                    .findFirst().orElse(null);
            assertNotNull(entry, "an address entry must still be reported, not omitted: " + report);
            assertEquals("known", entry.at(uri(STATUS)).asRec().at(uri(KIND)).strValue(),
                    "an undialed address is known, never down: " + entry);
        } finally {
            Machine.write(Network.ROOT_ROSTER, declared);
        }
    }

    /**
     * the roster FIELD and the ADDRESS it is written through must denote one map — the field is read off the
     * component while a declaration arrives by address, and nothing else keeps the two from drifting apart
     */
    @Test
    void testTheRosterIsItsAddressNotACopy() {
        assertEquals(Machine.read(Network.ROOT_ROSTER).toString(), Machine.current().network().roster().toString(),
                "the roster field and its address must be one map, not two");
    }

    /**
     * The status METHOD is reachable by path and reports one entry per declared peer. The fluent spelling
     * ({@code *x.status()}) is deliberately NOT asserted: it resolves the stored inst and looks for an
     * {@code inst-f} that a stored instance does not carry — measured to fail identically for an auto and a
     * plain inst, so it is a resolver limitation, not a tid choice.
     */
    @Test
    void testStatusIsReachableAndReportsPerPeerFromThePath() {
        final Obj report = ObjmtronSerializer.parse("*/network/status").apply();
        assertFalse(report.isFail(), "the path spelling should not fail, got " + report);
        assertFalse(report.isNoObj(), "one declared peer should yield one entry, got noobj");
        assertEquals(1, report.stream().count(), "one declared peer should yield one entry: " + report);
    }

    /** the status inst, applied, yields one entry per declared peer — and the live one reports up */
    @Test
    void testStatusIsReachableAndReportsPerPeer() {
        final Obj status = Machine.current().network().status();
        assertFalse(status.isNoObj(), "the status inst must be reachable from the component");
        final Obj report = status.apply(Machine.current().network());
        assertFalse(report.isFail(), "applying status to the network must not fail: " + report);
        final Obj one = report.stream().findFirst().orElse(null);
        assertNotNull(one, "one declared peer should yield one entry, got " + report);
        assertEquals("up", one.asRec().at(uri(STATUS)).asRec().at(uri(KIND)).strValue(),
                "the live peer should report up: " + one);
    }
}
