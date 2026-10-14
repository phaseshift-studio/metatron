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

package studio.phaseshift.metatron.isa.mach;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.Network;
import studio.phaseshift.metatron.isa.mach.type.network.PeerSpawn;
import studio.phaseshift.metatron.isa.mach.type.network.RemoteMachine;
import studio.phaseshift.metatron.util.MTronException;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.AUTHORITY;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * {@code push(<uri>)} and {@code move(<uri>)} ACROSS A PROCESS BOUNDARY: an address carrying an authority that is
 * still on this physical machine forks a peer mtron instance (its own JVM), records the connection in the frame's
 * OWN {@code network.peer} — {@code <service-uri> => peer::T[authority, transport]} — while still declaring the
 * address in the {@code /sys/peer} roster, and answers with a {@link RemoteMachine} — a frame that is thin because
 * the code evaluated in it is <b>executed by the peer</b>.
 * <p>
 * The behaviors pinned are the ones a same-JVM alias cannot show:
 * <ul>
 *   <li>the fork actually happens, and the node answers for its own namespace;</li>
 *   <li>a write under the authority lands on the node and NOT in local memory — the false green this feature
 *       would otherwise be;</li>
 *   <li>after {@code move()}, an expression is evaluated where the data lives, not here;</li>
 *   <li>the frame's lifetime is the process's: popping retracts the roster entry and reaps the node;</li>
 *   <li>an authority on another machine is declared, never forked.</li>
 * </ul>
 */
public class MachPushTest extends AbstractMetatronTest {

    /**
     * keep a failed test from leaking a forked node into the rest of the class's shared VM
     */
    @AfterEach
    void backToTheRoot() {
        this.resetToRoot();
    }

    @Test
    void testPushSpawnsAPeerAndDispatchesAcrossIt() {
        final int port = freePort();
        final fURI authority = f("").scheme("ws").host("localhost").port(port).extend("worker");
        final Machine parent = Machine.current();
        final Machine frame = parent.push(authority);
        try {
            assertEquals(Network.Helper.peerPath(authority), frame.vid(),
                    "the frame lives at the LOCAL address derived from the pushed authority");
            assertInstanceOf(RemoteMachine.class, frame, "a pushed peer is a remote frame, not a local machine copy");
            assertTrue(PeerSpawn.alive(authority), "the pushed authority must be served by a forked process");
            assertTrue(rosterAuthorities().contains("localhost:" + port), "the push must declare the peer");
            assertTrue(Network.Helper.peerAuthorities(Machine.current().network()).stream()
                            .anyMatch(peer -> peer.port() == port && "localhost".equals(peer.host())),
                    "the frame's network holds the connection it stands in: " + Network.Helper.peerAuthorities(Machine.current().network()));
            // a write crosses the socket
            final fURI address = authority.extend("d");
            Machine.write(address, jnt(42));
            assertEquals(jnt(42), Machine.read(address), "a write under the authority must reach the node");
            assertTrue(parent.memory().read(address).isNoObj(),
                    "the write must land on the node, not be captured by local memory");
        } finally {
            frame.pop();
        }
        assertFalse(PeerSpawn.alive(authority), "popping the frame must reap the node");
        assertFalse(rosterAuthorities().contains("localhost:" + port),
                "popping the frame must retract the roster entry it declared");
    }

    /**
     * the {@code push} INST is the contract — this drives it as mtron rather than through the Java API.
     */
    @Test
    void testPushInstForksAnAuthority() {
        final int port = freePort();
        final fURI address = f("").scheme("ws").host("localhost").port(port).extend("worker");
        final Obj pushed = ObjmtronSerializer.parse("*~.push(<" + address + ">)").apply();
        assertTrue(pushed.isMachine(), "the push inst must hand back a machine");
        try {
            assertTrue(PeerSpawn.alive(address), "the inst must fork the node");
        } finally {
            ObjmtronSerializer.parse("*~.pop(_)").apply();
        }
        assertFalse(PeerSpawn.alive(address), "the pop inst must reap the node");
    }

    /**
     * The execution half: after {@code move()}, code is evaluated <em>where the data lives</em>. The deref below
     * cannot resolve locally — the value was written straight to the peer and this VM's memory is empty — so a
     * correct answer is only possible if the peer ran it.
     */
    @Test
    void testMoveExecutesCodeOnThePeer() {
        final int port = freePort();
        final fURI authority = f("").scheme("ws").host("localhost").port(port).extend("worker");
        Machine.current().push(authority);
        try {
            final fURI d = authority.extend("d");
            Machine.write(d, jnt(42));
            assertTrue(Machine.current().memory().read(d).isNoObj(), "the value is not in this VM");
            // a single inst is an application too: it must be shipped, not resolved here. eval() is the path that does
            // it — it wraps a lone inst as a ONE-INSTRUCTION CODE, which is the shape Machine.applyCode hands to the
            // frame's shipping processor. A bare parse(...).apply() goes to the static per-inst runtime instead and
            // resolves HERE (the open half noted in RemoteMachine.applyCode).
            assertEquals(jnt(42), ObjmtronSerializer.eval("*/worker/d"),
                    "the deref found the value, so the PEER evaluated it");
            // and a code chain likewise
            assertEquals(jnt(52), ObjmtronSerializer.parse("*/worker/d.plus(10)").apply(),
                    "a code chain must be evaluated by the peer as well");
        } finally {
            this.resetToRoot();
        }
        assertFalse(PeerSpawn.alive(authority), "leaving the frame must reap the node");
    }

    /**
     * {@code move()} enters the peer's frame from the ROSTER entry, never from a machine in local memory.
     */
    @Test
    void testMoveInstEntersThePeerFrame() {
        final int port = freePort();
        final fURI authority = f("").scheme("ws").host("localhost").port(port).extend("worker");
        Machine.current().push(authority);
        try {
            // MOVE TO THE FRAME'S OWN ADDRESS. A ws:// uri is a wire address, not a place in this machine, so the
            // frame's address is the local path push derived — and moving to it is an ordinary read of a machine.
            final fURI local = Network.Helper.peerPath(authority);
            final Obj moved = ObjmtronSerializer.parse("move(<" + local + ">)").apply();
            assertTrue(moved.isMachine(), "move() answers the machine it moved to");
            assertInstanceOf(RemoteMachine.class, Machine.current(), "move() stands in the peer's frame");
            Machine.write(authority.extend("d"), jnt(7));
            assertEquals(jnt(7), ObjmtronSerializer.eval("*/worker/d"), "code there runs on the peer");
        } finally {
            this.resetToRoot();
        }
        assertFalse(PeerSpawn.alive(authority), "leaving the frame must reap the node");
    }

    @Test
    void testPushingTheSameAuthorityTwiceFindsThePeerItAlreadyDeclared() {
        final fURI authority = f("").scheme("ws").host("localhost").port(freePort()).extend("worker");
        final Machine first = Machine.current().push(authority);
        try {
            // the constructor's PEER guard, and an idempotent spawn: a second frame for a declared address
            // materialises the peer that exists rather than forking a twin
            final Machine second = Machine.current().push(authority);
            assertEquals(first.vid(), second.vid(), "both frames stand in the same peer");
            assertTrue(PeerSpawn.alive(authority), "the second push must not have disturbed the peer");
        } finally {
            first.pop();
        }
        assertFalse(PeerSpawn.alive(authority), "the surviving frame still owns and reaps the node");
    }

    @Test
    void testPushWithNoPortAllocatesOne() {
        final Machine frame = Machine.current().push(f("").scheme("ws").host("localhost").extend("worker"));
        try {
            // the FRAME's vid is a local path (/network/peer/ws/localhost/<port>/worker), so the allocated port is
            // on the CONNECTION's authority — which is what names the wire address — not on the path.
            assertTrue(authorityOf(frame).hasPort(), "the connection must carry the port the node actually landed on");
            assertTrue(PeerSpawn.alive(authorityOf(frame)), "the node must be serving the allocated port");
        } finally {
            frame.pop();
        }
        assertFalse(PeerSpawn.alive(authorityOf(frame)), "the allocated-port node must be reaped too");
    }

    @Test
    void testForeignAuthorityIsDeclaredNotForked() {
        final fURI foreign = f("ws://metatron.invalid:9/worker");
        final Machine frame = Machine.current().push(foreign);
        try {
            assertEquals("metatron.invalid:9", authorityOf(frame).authority());
            assertTrue(rosterAuthorities().contains("metatron.invalid:9"),
                    "an authority on another machine is declared as a peer");
            assertTrue(Network.Helper.peerAuthorities().stream()
                            .anyMatch(peer -> "metatron.invalid".equals(peer.host()) && peer.port() == 9),
                    "and it is still a connection this frame holds: " + Network.Helper.peerAuthorities());
            assertFalse(PeerSpawn.alive(foreign), "an authority on another machine must not be forked");
        } finally {
            frame.pop();
        }
        assertFalse(rosterAuthorities().contains("metatron.invalid:9"),
                "the declaration goes with the frame that made it");
    }

    @Test
    void testPushWithNoNamespacePathFails() {
        assertThrows(MTronException.class, () -> Machine.current().push(f("ws://localhost:1234")),
                "a path-less authority would have to claim the node's /sys — the store needs a namespace");
    }

    /**
     * A fail is this VM's diagnostic, not a payload: its serialization is a rendering ({@code fail::[...]}), not
     * mtron source, so shipping one used to raise a parse error about its own text. Writing one at a peer address
     * must simply be answered locally.
     */
    @Test
    void testAFailIsNeverShipped() {
        final fURI authority = f("").scheme("ws").host("localhost").port(freePort()).extend("worker");
        final Machine frame = Machine.current().push(authority);
        try {
            final Obj failure = fail(MTronException.of("boom"));
            assertTrue(Machine.current().network().write(authority.extend("fail/_"), failure).isNoObj(),
                    "a fail must not be handed to a transport");
            assertDoesNotThrow(() -> Machine.write(authority.extend("fail/_"), failure));
        } finally {
            frame.pop();
        }
    }

    // ========================================================================

    /**
     * The peer's WIRE address, read off the connection the frame's OWN network carries. The frame's own vid is a
     * local path ({@code /network/peer/<scheme>/<host>/<port>/<ns>}), so it no longer names the peer — the
     * connection rec does ({@code network.peer} is {@code <service-uri> => peer::T[authority, transport]}).
     */
    private static fURI authorityOf(final Machine frame) {
        return frame.network().peers().jvm().values().stream()
                .filter(Obj::isRec)
                .map(connection -> connection.asRec().<Obj>at(uri(AUTHORITY)))
                .filter(Obj::isUri)
                .map(Obj::uriValue)
                .findFirst()
                .orElseThrow();
    }

    private static Set<String> rosterAuthorities() {
        final Obj roster = Machine.read(Network.ROOT_ROSTER);
        if (!roster.isRec())
            return Set.of();
        return roster.asRec().jvm().keySet().stream()
                .filter(Obj::isUri)
                .map(key -> key.uriValue().authority())
                .collect(Collectors.toSet());
    }

    private static int freePort() {
        try (final ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (final IOException e) {
            throw MTronException.of(e);
        }
    }
}
