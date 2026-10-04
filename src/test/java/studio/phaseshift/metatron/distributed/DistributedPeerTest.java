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

package studio.phaseshift.metatron.distributed;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.Network;
import studio.phaseshift.metatron.isa.web.space.ws.handler.mtron_wsHandler;
import studio.phaseshift.metatron.isa.web.space.ws.wsSpace;
import studio.phaseshift.metatron.util.MTronException;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * The authority guard's own test: one peer VM, one socket, no shared {@code Router}.
 * <p>
 * It pins the claims the guard is built on:
 * <ol>
 *   <li>a read addressed to a peer's authority returns <em>the peer's</em> value — and a value only the peer
 *       ever held, so a broken roster cannot fake a pass;</li>
 *   <li>that value is nowhere in the local store, so it genuinely crossed a boundary;</li>
 *   <li>our own authority — addressed through a loopback alias that differs from the bound host — resolves
 *       locally rather than being delegated (the {@code 0.0.0.0} vs {@code localhost} trap);</li>
 *   <li>an authority that is neither ours nor a declared peer falls through untouched, rather than dialing;</li>
 *   <li>the roster itself persists, keyed by authority, with the transport inst intact.</li>
 * </ol>
 * The transport is supplied by {@link PeerCluster} as an ordinary inst in the declared roster, which is what
 * keeps the {@code Router} free of any protocol dependency.
 * <p>
 * The wider language-construct tables live in {@link DistributedmtronTest}.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class DistributedPeerTest extends AbstractMetatronTest {

    private static final String LOCAL_STORE = "/a/#";

    private static PeerCluster cluster;
    private static int selfPort;

    // ========================================================================
    // cluster setup — a peer VM, and the roster that names it
    // ========================================================================

    @BeforeAll
    public static void setup() throws Exception {
        selfPort = freePort();
        // our own store: deliberately a namespace the peer does NOT own, so anything readable at a peer path
        // can only have arrived over the socket
        memSpace.of(rec(uri(PATTERN), uri(LOCAL_STORE)), f("/sys/space/distributed/a"));
        // our own ws server — this is what declares our authority, bound to the wildcard host on purpose
        wsSpace.of(mutableMap(
                        uri(PATTERN), uri("ws://#"),
                        uri(HOST), uri("ws://0.0.0.0:" + selfPort),
                        uri(ROUTE), rec(uri("/mtron"), uri(mtron_wsHandler.WS_MTRON_HANDLER_TID.toString()))),
                f("/sys/space/distributed/ws"));
        cluster = PeerCluster.of(1).store(PeerCluster.DEFAULT_ROOT).seed(PeerCluster.DEFAULT_SEED_MATRIX).start();
        cluster.connect();
    }

    @AfterAll
    public static void teardown() {
        if (null != cluster) {
            cluster.close();
            cluster = null;
        }
    }

    /**
     * the one peer's uri prefix: {@code ws://localhost:<port>/n}
     */
    private static fURI remote() {
        return cluster.prefix(1);
    }

    private static fURI remoteVid(final String path) {
        return remote().extend(path);
    }

    // ========================================================================
    // the claims
    // ========================================================================

    /**
     * The false-positive-proof cross-host read: the value was seeded by the peer before it became addressable
     * and has never been written locally. With no roster entry, {@code ws://localhost:peerPort/n/probe} silently
     * resolves to this VM's <em>own</em> wildcard-host ws space and returns noobj — so a broken roster makes this
     * fail rather than quietly pass. That is not hypothetical; the first version of this test passed while
     * answering entirely from local state.
     */
    @Test
    public void testReadsAValueOnlyThePeerHas() {
        assertEquals(str(PeerCluster.probe(1)), Machine.readFromSpace(remoteVid("/probe")),
                "a value only the peer ever held must be readable across the boundary");
    }

    @Test
    public void testWriteLandsOnThePeer() {
        final fURI remote = remoteVid("/sent");
        Machine.writeToSpace(remote, str("node-a-sent-this"));
        assertEquals(str("node-a-sent-this"), Machine.readFromSpace(remote),
                "a write addressed to the peer must land in the peer's store and read back from there");
    }

    @Test
    public void testPeerValueIsNotInTheLocalStore() {
        assertEquals(jnt(PeerCluster.seedA(1)), Machine.readFromSpace(remoteVid("/a")));
        // we own /a/# and nothing else, so the peer's namespace has no home here at all — the local read
        // cannot even resolve it, which is stronger than resolving to noobj
        assertThrows(MTronException.class, () -> Machine.readFromSpace(f(PeerCluster.DEFAULT_ROOT + "/a")),
                "the peer's namespace must not appear in the local store (we own " + LOCAL_STORE + " only)");
    }

    @Test
    public void testSelfAuthorityResolvesLocally() {
        final fURI self = f("ws://localhost:" + selfPort + "/a/x");
        Machine.writeToSpace(self, str("node-a-value"));
        assertEquals(str("node-a-value"), Machine.readFromSpace(f("/a/x")),
                "writing to our own authority should land in our own store");
        assertEquals(str("node-a-value"), Machine.readFromSpace(self),
                "the loopback alias of our own authority must resolve locally, not be delegated");
    }

    @Test
    public void testOwnershipAndPeerClassification() {
        assertTrue(Machine.root().own(f("ws://localhost:" + selfPort + "/a/x")),
                "localhost:ourPort is the loopback alias of the wildcard host we bound");
        assertFalse(Machine.root().own(remoteVid("/a")), "the peer's authority is not ours");

        assertFalse(Machine.root().isPeer(f("ws://localhost:" + selfPort + "/a/x")),
                "our own authority is ours, not a peer");
        assertTrue(Machine.root().isPeer(remoteVid("/a")), "the declared peer is a peer");
        assertFalse(Machine.root().isPeer(f("http://example.com/")),
                "a foreign authority that was never declared must not be treated as a metatron peer");
    }

    @Test
    public void testUndeclaredAuthorityDoesNotReachAPeer() {
        // port 1 has no listener: if the guard dialed, this would fail or hang rather than return promptly
        final Obj result = Machine.readFromSpace(f("ws://localhost:1/n/a"));
        assertFalse(result.isStr(), "an undeclared authority must not yield a value from a peer");
    }

    @Test
    public void testPeerReadThroughMtronDereference() {
        checkCodeParseApply(LOG, "*<" + remote() + "/probe>", "\"" + PeerCluster.probe(1) + "\"");
    }

    /**
     * Regression guard for the bug this slice actually hit: an authority claimed by a no-op writer drops the
     * roster silently, and the symptom is a peer that appears to work because the read resolves to the local
     * wildcard-host space instead. So assert the roster is really there, keyed by the peer's authority, with the
     * transport <em>inst</em> intact — a lambda in a stored rec survives (its {@code f} lives in the inst's jvm
     * triplet), and if that ever stops being true the transport degrades silently rather than failing loudly.
     */
    @Test
    public void testDeclaredRosterPersists() {
        final Obj roster = Machine.readFromSpace(Network.Helper.peerRosterPath());
        assertTrue(roster.isRec(), "the declared roster must persist at " + Network.Helper.peerRosterPath());
        final Map<Obj, Obj> entries = roster.asRec().jvm();
        assertEquals(1, entries.size(), "exactly the one declared peer");
        final Map.Entry<Obj, Obj> entry = entries.entrySet().iterator().next();
        assertTrue(entry.getKey().isUri(), "a roster key is the peer's authority as a uri");
        assertTrue(Network.Helper.sameAuthority(entry.getKey().uriValue().authority(), "localhost:" + cluster.port(1)),
                "the roster is keyed by the peer's authority");
        assertTrue(entry.getValue().isObjInst(), "the roster value is the transport inst, not a dropped write");
    }

    // ========================================================================
    // plumbing
    // ========================================================================

    private static int freePort() throws IOException {
        try (final ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
