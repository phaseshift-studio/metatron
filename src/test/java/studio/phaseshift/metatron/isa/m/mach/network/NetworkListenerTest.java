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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.Network;
import studio.phaseshift.metatron.isa.m.mach.type.network.Listener;
import studio.phaseshift.metatron.util.MTronException;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.m.mach.machInstSet.LISTENER_TID;

/**
 * A machine's PRESENCE — the sockets it serves through, in its network's {@code listener} field.
 * <p>
 * A port binds once per physical machine, so a socket is opened by one machine (its level owns it, and its pop releases
 * the port) and inherited by every machine below; each machine serving through it keeps its own routes in its own
 * level, so a pop takes exactly that machine's routes. A request is answered by the most specific route across every
 * machine sharing the socket, and that route's machine is the one the connection belongs to.
 * <p>
 * No socket is bound here: {@link Recorder} is the protocol half, and it only counts its releases.
 */
//@Isolated
//@Execution(ExecutionMode.SAME_THREAD)
public class NetworkListenerTest extends AbstractMetatronTest {

    private static final fURI SOCKET = f("ws://0.0.0.0:28555");
    private static final fURI CHILD_SOCKET = f("ws://0.0.0.0:28556");

    /**
     * a listener with no socket behind it: it counts how often it was released
     */
    private static final class Recorder extends Listener {
        private final AtomicInteger unbinds = new AtomicInteger(0);

        private Recorder(final fURI authority, final Network owner) {
            super(authority, REC_TID, owner);
        }

        @Override
        protected void unbind() {
            this.unbinds.incrementAndGet();
        }
    }

    @AfterEach
    public void cleanup() {
        this.resetToRoot();
        Machine.current().network().unlisten(SOCKET);
        Machine.current().network().unlisten(CHILD_SOCKET);
    }

    @Test
    public void testAListenerIsAnAddressableEntry() {
        final Machine root = Machine.current();
        final Recorder server = new Recorder(SOCKET, root.network());
        Network.Helper.listen(root.network(), server, rec(uri("/mtron"), uri("mtron_ws")));
        final Obj entry = root.network().listener(SOCKET.pathEncoding());
        assertEquals(LISTENER_TID, entry.tid(), "a listener entry is a listener::T");
        checkEquality(LOG, entry.asRec().at(uri(ORIGIN)), uri(SOCKET), true);
        assertSame(server, Network.Helper.listenerFor(SOCKET), "the entry holds the server itself");
        assertTrue(Network.Helper.authorities().contains(SOCKET), "a listener is an authority this machine answers on");
        // the entry sits at its key's path, and each of its fields at its own address beneath it
        this.script("*<~/network/listener/ws/localhost/28555/origin> % <ws://0.0.0.0:28555>");
        checkEquality(LOG, Machine.read(f("~/network/listener/ws/localhost/28555/route")), rec(uri("/mtron"), uri("mtron_ws")), true);
        // the owner's unlisten releases the port, and the entry goes
        root.network().unlisten(SOCKET);
        assertEquals(1, server.unbinds.get());
        assertTrue(server.closed());
        checkEquality(LOG, root.network().listener(SOCKET.pathEncoding()), noobj(), true);
        assertFalse(Network.Helper.authorities().contains(SOCKET));
    }

    /**
     * NOMINAL, NOT STRUCTURAL. {@code index} dispatches on the handle's TYPE: a rec that merely carries the fields a
     * listener has (they are maybe(), so a structural test would admit it) is NOT mounted — only a listener::T, or a
     * refinement of it, is a socket.
     */
    @Test
    public void testAListenerLookAlikeIsNotMounted() {
        final Machine root = Machine.current();
        final Recorder server = new Recorder(SOCKET, root.network());
        final Obj lookAlike = rec(uri(ORIGIN), uri(SOCKET), uri(SERVER), server);
        assertTrue(root.network().index(lookAlike).isNoObj(), "the fields alone do not make a socket");
        checkEquality(LOG, root.network().listener(SOCKET.pathEncoding()), noobj(), true);
    }

    /**
     * a listener is constructed by a server, never written: a value at its address is refused, and the deletion is
     * the network's own retraction — the routes leave, and the socket its owner opened is released
     */
    @Test
    public void testDeletingAListenersAddressStopsServing() {
        final Machine root = Machine.current();
        final Recorder server = new Recorder(SOCKET, root.network());
        Network.Helper.listen(root.network(), server, rec(uri("/mtron"), uri("mtron_ws")));
        this.script("[STATE] <~/network/listener/ws/localhost/28555/origin> -> <ws://0.0.0.0:9999>");
        this.script("*<~/network/listener/ws/localhost/28555/origin> % <ws://0.0.0.0:28555>");
        assertFalse(server.closed(), "a refused write touches nothing");
        this.script("[STATE] <~/network/listener/ws/localhost/28555> -> noobj");
        checkEquality(LOG, root.network().listener(SOCKET.pathEncoding()), noobj(), true);
        assertEquals(1, server.unbinds.get(), "the owner's deletion releases the port");
        assertTrue(server.closed());
        assertNull(Network.Helper.listenerFor(SOCKET));
    }

    /**
     * a child that deletes its own entry on an inherited socket withdraws its routes, and only them
     */
    @Test
    public void testAChildsDeletionWithdrawsItsRoutesNotTheSocket() {
        final Machine root = Machine.current();
        final Recorder server = new Recorder(SOCKET, root.network());
        Network.Helper.listen(root.network(), server, rec(uri("/"), uri("site:")));
        final Machine child = root.push(f("listener_child"));
        try {
            Network.Helper.listen(child.network(), server, rec(uri("/drstynx"), uri("drstynx:")));
            assertSame(child, server.route(f("/drstynx")).orElseThrow().machine());
            this.script("[STATE] <~/network/listener> -> noobj");
            checkEquality(LOG, child.network().listener(SOCKET.pathEncoding()), noobj(), true);
            assertSame(root, server.route(f("/drstynx")).orElseThrow().machine(), "the child's route left with its entry");
            assertEquals(0, server.unbinds.get(), "a sharer's deletion never releases the owner's socket");
            assertFalse(root.network().listener(SOCKET.pathEncoding()).isNoObj(), "the parent's entry is its own");
        } finally {
            child.pop();
        }
    }

    /**
     * destruction is disconnection in both directions: a socket closed by hand leaves no level holding an entry for it
     */
    @Test
    public void testClosingTheServerWithdrawsEveryEntry() {
        final Machine root = Machine.current();
        final Recorder server = new Recorder(SOCKET, root.network());
        Network.Helper.listen(root.network(), server, rec(uri("/"), uri("site:")));
        final Machine child = root.push(f("listener_child"));
        try {
            Network.Helper.listen(child.network(), server, rec(uri("/drstynx"), uri("drstynx:")));
            server.close();
            assertEquals(1, server.unbinds.get(), "the socket is released once");
            checkEquality(LOG, child.network().listener(SOCKET.pathEncoding()), noobj(), true);
            checkEquality(LOG, root.network().listener(SOCKET.pathEncoding()), noobj(), true);
            assertNull(Network.Helper.listenerFor(SOCKET));
        } finally {
            child.pop();
        }
    }

    @Test
    public void testPresenceIsInheritedTheEntryIsNot() {
        final Machine root = Machine.current();
        final Recorder server = new Recorder(SOCKET, root.network());
        Network.Helper.listen(root.network(), server, rec(uri("/"), uri("site:")));
        final Machine child = root.push(f("listener_child"));
        try {
            assertSame(server, Network.Helper.listenerFor(SOCKET), "a child serves through its parent's socket");
            assertTrue(Network.Helper.authorities().contains(SOCKET), "a child answers on its parent's socket");
            checkEquality(LOG, child.network().listener(SOCKET.pathEncoding()), noobj(), true);
        } finally {
            child.pop();
        }
    }

    /**
     * the root serves a catch-all, a route, and a wildcard; a child serving through the root's socket adds its own
     * routes, and each request lands on the most specific route across both tables
     */
    @ParameterizedTest
    @CsvSource(value = {
            "/index.html    % root  % /",
            "/mcp           % root  % /mcp",
            "/mcp/sse       % root  % /mcp",
            "/mcpx          % root  % /",
            "/drstynx       % child % /drstynx",
            "/drstynx/tools % child % /drstynx",
            "/people/bob    % root  % /people/#",
            "/people/alice  % child % /people/alice",
    }, delimiter = '%')
    public void testMostSpecificRouteAnswers(final String request, final String owner, final String key) {
        final Machine root = Machine.current();
        final Recorder server = new Recorder(SOCKET, root.network());
        Network.Helper.listen(root.network(), server, rec(
                uri("/"), uri("site:"),
                uri("/mcp"), uri("mcp_mtron"),
                uri("/people/#"), uri("people:")));
        final Machine child = root.push(f("listener_child"));
        try {
            Network.Helper.listen(child.network(), Network.Helper.listenerFor(SOCKET), rec(
                    uri("/drstynx"), uri("drstynx:"),
                    uri("/people/alice"), uri("alice:")));
            final Listener.Route route = server.route(f(request)).orElseThrow();
            assertSame("root".equals(owner) ? root : child, route.machine(), "the connection's machine for " + request);
            assertEquals(f(key), route.key(), "the route for " + request);
        } finally {
            child.pop();
        }
    }

    @Test
    public void testAKeyServedAboveIsRefused() {
        final Machine root = Machine.current();
        final Recorder server = new Recorder(SOCKET, root.network());
        Network.Helper.listen(root.network(), server, rec(uri("/mcp"), uri("mcp_mtron")));
        final Machine child = root.push(f("listener_child"));
        try {
            assertThrows(MTronException.class, () -> Network.Helper.listen(child.network(), server, rec(uri("/mcp"), uri("hijack:"))));
            checkEquality(LOG, child.network().listener(SOCKET.pathEncoding()), noobj(), true);
            assertSame(root, server.route(f("/mcp")).orElseThrow().machine(), "the route stays its first claimant's");
        } finally {
            child.pop();
        }
    }

    @Test
    public void testPopRetractsTheChildsRoutesNotTheSocket() {
        final Machine root = Machine.current();
        final Recorder server = new Recorder(SOCKET, root.network());
        Network.Helper.listen(root.network(), server, rec(uri("/"), uri("site:")));
        final Machine child = root.push(f("listener_child"));
        Network.Helper.listen(child.network(), server, rec(uri("/drstynx"), uri("drstynx:")));
        assertSame(child, server.route(f("/drstynx")).orElseThrow().machine());
        assertEquals(2, server.sharers().size());
        child.pop();
        final Listener.Route after = server.route(f("/drstynx")).orElseThrow();
        assertSame(root, after.machine(), "the child's route left with the child");
        assertEquals(f("/"), after.key());
        assertEquals(1, server.sharers().size());
        assertEquals(0, server.unbinds.get(), "a child's pop never releases its parent's socket");
        assertFalse(server.closed());
    }

    @Test
    public void testTheOwnersPopReleasesTheSocket() {
        final Machine root = Machine.current();
        final Machine child = root.push(f("listener_child"));
        final Recorder server = new Recorder(CHILD_SOCKET, child.network());
        Network.Helper.listen(child.network(), server, rec(uri("/compute"), uri("compute:")));
        assertTrue(Network.Helper.authorities().contains(CHILD_SOCKET));
        child.pop();
        assertEquals(1, server.unbinds.get(), "the owner's pop releases the port");
        assertTrue(server.closed());
        assertNull(Network.Helper.listenerFor(CHILD_SOCKET), "nothing above the popped child holds the socket");
        assertFalse(Network.Helper.authorities().contains(CHILD_SOCKET));
    }
}
