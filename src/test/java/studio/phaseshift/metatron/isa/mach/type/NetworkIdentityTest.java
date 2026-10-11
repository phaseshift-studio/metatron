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

package studio.phaseshift.metatron.isa.mach.type;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.network.PeerCluster;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.AUTHORITY;
import static studio.phaseshift.metatron.Tokens.COMPILER;
import static studio.phaseshift.metatron.Tokens.HOST;
import static studio.phaseshift.metatron.Tokens.MEMORY;
import static studio.phaseshift.metatron.Tokens.NETWORK;
import static studio.phaseshift.metatron.Tokens.PATTERN;
import static studio.phaseshift.metatron.Tokens.PROCESSOR;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
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
 *   <li><b>the roster is the source of peers</b>, and a declared peer is never also "mine"</li>
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
        final Network network = Machine.current().network();
        assertFalse(network.authorities().contains("0.0.0.0:19191"),
                "nothing has claimed that authority yet");
        mountSpaceWithHost(f("/sys/space/network-identity"), f("ws://0.0.0.0:19191"));
        assertTrue(network.authorities().contains("0.0.0.0:19191"),
                "a mounted space's declared host IS an authority this machine answers to: " + network.authorities());
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
     * Peers come from the cluster roster — the same declaration production writes — and a declared peer is never
     * also "mine", so the two questions cannot both answer yes.
     * <p>
     * A DETACHED cluster is used on purpose: it has real peer addresses and a real roster written by
     * {@link PeerCluster#connect()}, but no processes and no sockets, so this pins membership and identity without
     * going near the wire. Reaching a peer is the next slice, and that is what {@code NetworkTest} asserts.
     */
    @Test
    public void testPeersComeFromTheClusterRoster() throws Exception {
        final Network network = Machine.current().network();
        assertTrue(network.peers().isEmpty(), "nothing is declared yet: " + network.peers());
        final PeerCluster cluster = PeerCluster.detached(2, "/n", 21001).connect();
        try {
            assertEquals(Set.of("localhost:21001", "localhost:21002"), network.peers(),
                    "the roster's keys ARE the peers — declared by the cluster, not by traffic");
            assertTrue(network.isPeer(f("ws://localhost:21001/usr/x")));
            assertFalse(network.own(f("ws://localhost:21001/usr/x")), "a declared peer is not mine");
        } finally {
            cluster.close();
            Machine.write(Network.Helper.peerRosterPath(), rec(mutableMap()));
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
     * The roster is ONE SHARED ADDRESS today, not per-machine state. {@code /sys/peer} is an absolute address in a
     * space a child reaches through its unioned memory, so a declaration made while standing in a child IS the
     * declaration the parent reads.
     * <p>
     * This is the reason a {@code NetworkUnion} is not yet meaningful, and it is worth pinning so the assumption
     * breaks loudly rather than silently: a union composes a component's OWN content across the frame chain, and
     * the roster's content does not live on the component — it lives in a shared space. Membership becomes
     * per-machine only when the roster moves onto the component's {@code peer} field, and the union is precisely
     * what makes that inherited-but-not-shared. Moving the home without the union would make membership
     * per-machine and <em>unreachable from a child</em>; the union without the move would compose nothing.
     */
    @Test
    public void testTheRosterIsOneSharedAddressToday() {
        final Network parent = Machine.current().network();
        Machine.write(Network.Helper.peerRosterPath(), rec(mutableMap()));
        final Machine child = Machine.current().push(f("network-roster-scope"));
        Machine.write(Network.Helper.peerRosterPath(), rec(mutableMap(
                uri("ws://declared-in-child:21011"),
                rec(mutableMap(uri(AUTHORITY), uri("ws://declared-in-child:21011"))))));
        child.pop();
        try {
            assertTrue(parent.peers().contains("declared-in-child:21011"),
                    "the roster is one shared address: a declaration made while standing in a child is the "
                            + "parent's too — so membership is global, not per-machine: " + parent.peers());
        } finally {
            Machine.write(Network.Helper.peerRosterPath(), rec(mutableMap()));
        }
    }
}
