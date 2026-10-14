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
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.*;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.Poly.MUTABLE;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * The NETWORK's two membership questions, kept apart.
 * <p>
 * "Is this address <em>mine</em>?" and "is this address a <em>declared peer</em>?" are different questions with
 * different answers, and blurring them is what left the authority guard with no answer at all. This suite pins:
 *
 * <ul>
 *   <li><b>mine is derived, not declared</b> — the authorities this machine answers to come from the {@code host}
 *       every mounted space declares, so there is no self-authority declaration to drift out of step</li>
 *   <li><b>aliases collapse</b> — a server bound to {@code 0.0.0.0} is reachable as {@code localhost}, and getting
 *       that wrong makes the boundary forward a request to itself</li>
 *   <li><b>membership is never emergent</b> — a uri cannot make itself a peer by being addressed, and an undeclared
 *       authority is answered {@code noobj} rather than dialed</li>
 *   <li><b>a CONNECTION is the source of peers</b>, the known-server roster is the source of "may I reach it", and
 *       neither makes an address "mine"</li>
 * </ul>
 */
public class NetworkIdentityTest extends AbstractMetatronTest {

    /**
     * Register a space that CLAIMS a host, which is the only place an authority of this machine comes from.
     * Registration writes the space at its own vid, which needs an owner for the pattern — hence the BOOTING
     * guard the other machine suites use.
     */
    private static void mountSpaceWithHost(final fURI vid, final fURI host) {
        final boolean booting = BootLoader.BOOTING;
        BootLoader.BOOTING = true;
        try {
            Machine.current().memory().addSpace(memSpace.of(rec(
                    uri(PATTERN), uri(vid.toString() + "/#"),
                    uri(HOST), uri(host.toString())), vid));
        } finally {
            BootLoader.BOOTING = booting;
        }
    }

    /**
     * "Mine" is a fact about what is mounted, never a second declaration — so mounting a server is what makes this
     * machine answer to its authority.
     */
    @Test
    public void testAuthoritiesAreDerivedFromMountedSpaces() {
        assertFalse(Network.Helper.authorities().contains(f("ws://0.0.0.0:19191")),
                "nothing has claimed that authority yet");
        mountSpaceWithHost(f("/sys/space/network-identity"), f("ws://0.0.0.0:19191"));
        assertTrue(Network.Helper.authorities().contains(f("ws://0.0.0.0:19191")),
                "a mounted space's declared host IS an authority this machine answers to: " + Network.Helper.authorities());
    }

    /**
     * The boot binds the wildcard while a peer addresses loopback. If those are not one service, the boundary
     * forwards a request to itself — so this is a loop guard, not a nicety.
     */
    @Test
    public void testOwnIsAliasAware() {
        final Network network = Machine.current().network();
        mountSpaceWithHost(f("/sys/space/network-identity-alias"), f("ws://0.0.0.0:19193"));
        assertTrue(network.own(f("ws://localhost:19193/x")), "localhost and 0.0.0.0 are one service");
        assertTrue(network.own(f("ws://127.0.0.1:19193/x")), "and so is 127.0.0.1");
        assertFalse(network.own(f("ws://localhost:19194/x")), "a different port is a different service");
        assertFalse(network.own(f("ws://elsewhere:19193/x")), "and a different host certainly is");
    }

    /**
     * Membership is declared, never emergent: addressing a host does not make it a peer, and an undeclared
     * authority is answered {@code noobj} — no dial, no throw. That is the fail-closed rule, behaviourally.
     */
    @Test
    public void testMembershipIsNeverEmergentAndFailsClosed() {
        final Network network = Machine.current().network();
        final fURI stranger = f("ws://never-declared:19999/x");
        assertFalse(network.isPeer(stranger), "a uri must not make itself a peer merely by being addressed");
        assertFalse(network.own(stranger), "and it is not mine either");
        final Obj read = network.read(stranger);
        assertTrue(read.isNoObj(), "an undeclared authority resolves to noobj rather than dialing: " + read);
    }

    /**
     * TWO QUESTIONS, TWO HOMES. The known-server roster ({@code /sys/peer}) answers "may this frame reach that
     * address at all"; the CONNECTIONS the machine holds ({@code network.peer}) are what {@link Network#peers()}
     * reports, declared and never emergent.
     * <p>
     * A DETACHED cluster is used on purpose: it has real server addresses and a real roster written by
     * {@link PeerCluster#connect()}, but no processes and no sockets, so this pins membership and identity without
     * going near the wire. Reaching a peer is the next slice, and that is what {@code NetworkTest} asserts.
     */
    @Test
    public void testPeersAreTheMachinesConnections() throws Exception {
        final Network network = Machine.current().network();
        final Obj wasConnected = network.atDirect(uri(PEER));
        assertTrue(Network.Helper.peerAuthorities().isEmpty(), "nothing is declared yet: " + Network.Helper.peerAuthorities());
        final PeerCluster cluster = PeerCluster.detached(2, "/n", 21001).connect();
        try {
            assertTrue(network.isPeer(f("ws://localhost:21001/usr/x")),
                    "a server the roster declares IS a peer this frame may dial");
            assertTrue(Network.Helper.peerAuthorities().isEmpty(),
                    "but the roster is the known servers, NOT the connections the machine holds: " + Network.Helper.peerAuthorities());
            // THE CONNECTION IS THE MACHINE'S OWN REACH, and declaring one is what peers() reports — the same act
            // PeerSpawn performs for a pushed peer (Machine.push builds the frame's own level the same way).
            network.at(uri(PEER), rec(mutableMap(
                    uri("ws://localhost:21001"),
                    rec(mutableMap(uri(AUTHORITY), uri("ws://localhost:21001/n"))))), MUTABLE);
            assertEquals(Set.of(f("ws://localhost:21001")), Network.Helper.peerAuthorities(),
                    "the connection keys ARE the peers — declared, not emergent");
            assertFalse(network.own(f("ws://localhost:21001/usr/x")), "a declared peer is not mine");
        } finally {
            cluster.close();
            Machine.write(Network.ROOT_ROSTER, rec(mutableMap()));
            // "no connections" is the ABSENT field, not an empty rec: writing [] as the connections map fails the
            // network's positional predicate (measured — an empty rec's key is noobj, which is not a uri::T), and
            // `at` with noobj removes the entry, which is the state a machine with nothing dialed is actually in.
            network.at(uri(PEER), wasConnected, MUTABLE);
        }
    }

    /**
     * The slot holds the COMPONENT, not a template. A deferred seed — an inst the accessor has to apply — is what
     * forced the machine's isaPredicate to admit an INST alongside a network, made the slot unresolvable while the
     * machine was being constructed (a Call needs a compiler, and the root machine has none yet), and kept the
     * network slot from reading as the roster. Memory and network are bound as themselves; nothing is applied to
     * hand them back.
     */
    @Test
    public void testComponentSlotsAreBoundNotDeferred() {
        final Machine machine = Machine.current();
        final Obj network = machine.atDirect(uri(NETWORK));
        final Obj memory = machine.atDirect(uri(MEMORY));
        final Obj compiler = machine.atDirect(uri(COMPILER));
        final Obj processor = machine.atDirect(uri(PROCESSOR));
        assertFalse(network.isInst(), "the network slot is not a template: " + network);
        assertFalse(memory.isInst(), "the memory slot is not a template: " + memory);
        assertFalse(compiler.isInst(), "the compiler slot is not a template: " + compiler);
        assertFalse(processor.isInst(), "the processor slot is not a template: " + processor);
        assertTrue(network instanceof Network, "the network slot holds the roster itself: " + network);
        assertTrue(memory instanceof Memory, "the memory slot holds the memory itself: " + memory);
        assertTrue(compiler instanceof Compiler, "the compiler slot holds the compiler itself: " + compiler);
        assertTrue(processor instanceof Processor, "the processor slot holds the processor itself: " + processor);
    }

    /**
     * The KNOWN-SERVER ROSTER is ONE SHARED ADDRESS, not per-machine state. {@code /sys/peer} is an absolute address
     * in a space a child reaches through its unioned memory, so a declaration made while standing in a child IS the
     * declaration the parent reads.
     * <p>
     * That is now the deliberate difference between the two homes: the CONNECTIONS a machine holds live on the
     * component's {@code peer} field (which is why a {@code NetworkUnion} is what will make them
     * inherited-but-not-shared), while the known servers stay one list for the whole system. Pinned so the split
     * stays a decision rather than an accident.
     */
    @Test
    public void testTheRosterIsOneSharedAddressToday() {
        final Network parent = Machine.current().network();
        Machine.write(Network.ROOT_ROSTER, rec(mutableMap()));
        final Machine child = Machine.current().push(f("network-roster-scope"));
        Machine.write(Network.ROOT_ROSTER, rec(mutableMap(
                uri("ws://declared-in-child:21011"),
                rec(mutableMap(uri(AUTHORITY), uri("ws://declared-in-child:21011"))))));
        child.pop();
        try {
            assertTrue(parent.roster().jvm().keySet().stream()
                            .anyMatch(key -> key.isUri()
                                    && "ws://declared-in-child:21011".equals(key.uriValue().toString())),
                    "the roster is one shared address: a declaration made while standing in a child is the "
                            + "parent's too — so the known servers are global, not per-machine: " + parent.roster());
        } finally {
            Machine.write(Network.ROOT_ROSTER, rec(mutableMap()));
        }
    }
}
