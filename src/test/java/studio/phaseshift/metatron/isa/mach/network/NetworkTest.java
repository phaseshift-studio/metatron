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
import studio.phaseshift.metatron.isa.mach.type.Machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;

/**
 * THE AUTHORITY DISPATCH ON {@code Network}, end to end: an address written against a peer's authority
 * ({@code ws://localhost:PORT/...}) is handed to that peer's transport and answered over the wire, instead of
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
        // declare the roster: ws://localhost:PORT => transport, which is what Network.read/write dispatch through
        cluster.connect();
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
}
