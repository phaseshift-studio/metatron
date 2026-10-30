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
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.Network;
import studio.phaseshift.metatron.isa.mach.type.network.Listener;
import studio.phaseshift.metatron.util.MTronException;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.Poly.MUTABLE;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.LISTENER_TID;
import static studio.phaseshift.metatron.isa.mach.machInstSet.PEER_TYPE;

/**
 * THE FUNNEL ITSELF: {@link Network#index(Obj)} and {@link Network#drop(fURI)} are the two Addressable doors every
 * member enters and leaves through, so they get their own contract tests rather than being covered by the behaviors
 * that happen to call them. Every test names one rule of the funnel:
 * <ul>
 *     <li>index's PEER arm — claim, path-encoded key, service origin, idempotence, replacement, refusals;</li>
 *     <li>index's LISTENER arm — the typed entry is the handle; the mount validates and shares; a claimed route is
 *     refused; the look-alike is not a socket (see NetworkListenerTest, which pins nominal dispatch);</li>
 *     <li>drop — reach by key/surface/service, presence by key, the two maps' ownership rules, the answer shape, and
 *     the empty-field discipline.</li>
 * </ul>
 */
public class NetworkFunnelTest extends AbstractMetatronTest {

    /**
     * a listener with no socket behind it: it counts how often it was released
     */
    private static final class Probe extends Listener {
        private final AtomicInteger unbinds = new AtomicInteger(0);

        private Probe(final fURI origin, final Network owner) {
            super(origin, REC_TID, owner);
        }

        @Override
        protected void unbind() {
            this.unbinds.incrementAndGet();
        }
    }

    @AfterEach
    public void cleanup() {
        this.resetToRoot();
    }

    // ======================== index: the peer arm ========================

    @Test
    public void testIndexMintsAPeerAtThePathEncodingOfTheDial() {
        final Network network = Machine.current().network();
        final fURI dial = f("ws://212.7.1.9:28581/mtron");
        final Obj client = client(dial.toString());
        final Obj peer = network.index(client);
        try {
            assertTrue(peer.isRec(), "a client with an inst is a peer");
            assertSame(peer, network.peer(dial.pathEncoding()), "the key is the dial's path encoding");
            assertSame(client, peer.asRec().at(uri(CLIENT)), "the client IS the connection, held by identity");
            assertEquals(uri("ws://212.7.1.9:28581"), peer.asRec().at(uri(ORIGIN)), "the origin field is the service");
            assertEquals(uri(dial.toString()), peer.asRec().at(uri(HOST)), "the host field is where it was dialed");
            assertTrue(peer.testNominally(PEER_TYPE), "and the entry is a peer::T");
        } finally {
            network.drop(dial.pathEncoding());
        }
    }

    @Test
    public void testIndexIsIdempotentForTheSameClient() {
        final Network network = Machine.current().network();
        final fURI dial = f("ws://212.7.1.9:28582/mtron");
        final Obj client = client(dial.toString());
        try {
            final Obj first = network.index(client);
            final Obj again = network.index(client);
            assertSame(first, again, "indexing the same client answers the member it already holds");
            assertEquals(1L, network.peers().elements().count(), "never a second entry for one client");
        } finally {
            network.drop(dial.pathEncoding());
        }
    }

    @Test
    public void testIndexReplacesADifferentClientAtTheSameKey() {
        final Network network = Machine.current().network();
        final fURI dial = f("ws://212.7.1.9:28583/mtron");
        final Obj first = client(dial.toString());
        final Obj second = client(dial.toString());
        try {
            network.index(first);
            final Obj replaced = network.index(second);
            assertSame(second, replaced.asRec().at(uri(CLIENT)), "a fresh construction at a held key takes its place");
            assertEquals(1L, network.peers().elements().count(), "the displaced member left the map");
        } finally {
            network.drop(dial.pathEncoding());
        }
    }

    @Test
    public void testIndexDeclinesAClientWithNoInst() {
        final Network network = Machine.current().network();
        final fURI dial = f("ws://212.7.1.9:28584/mtron");
        final Obj noInst = rec(uri(PATTERN), uri(dial.toString()));
        assertTrue(network.index(noInst).isNoObj(), "a client that declares no inst is not a peer");
        assertTrue(network.peer(dial.pathEncoding()).isNoObj(), "and nothing was stored");
    }

    @Test
    public void testIndexDeclinesANonRec() {
        final Network network = Machine.current().network();
        assertTrue(network.index(studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt(7)).isNoObj(),
                "only a rec can claim an address");
    }

    @Test
    public void testIndexDropsTheQFromTheKeyAndTheOrigin() {
        final Network network = Machine.current().network();
        final fURI dialled = f("ws://212.7.1.9:28585/mtron?clientq=x");
        try {
            final Obj peer = network.index(client(dialled.toString()));
            assertSame(peer, network.peer(f("ws://212.7.1.9:28585/mtron").pathEncoding()),
                    "the key is the path encoding, no q");
            assertEquals(uri("ws://212.7.1.9:28585"), peer.asRec().at(uri(ORIGIN)),
                    "the service is the origin: scheme, host, port — no path, no q");
        } finally {
            network.drop(f("ws://212.7.1.9:28585/mtron").pathEncoding());
        }
    }

    // ======================== index: the listener arm ========================

    @Test
    public void testIndexMountsAListenerEntry() {
        final Network network = Machine.current().network();
        final fURI origin = f("ws://0.0.0.0:28586");
        final Probe server = new Probe(origin, network);
        final Obj entry = listener(server, rec(uri("/m"), uri("m")));
        assertSame(entry, network.index(entry), "the typed entry is the handle, and index answers it");
        assertSame(entry, network.listener(origin.pathEncoding()), "mounted at its origin's path encoding");
        assertSame(server, Network.Helper.listenerFor(origin), "the entry holds the server itself");
        assertTrue(Network.Helper.authorities().contains(origin), "and the origin is now an authority");
        assertEquals(rec(uri("/m"), uri("m")), entry.asRec().at(uri(ROUTE)), "this level's routes ride the entry");
        network.drop(origin.pathEncoding());
    }

    @Test
    public void testIndexRefusesANonUriRouteKey() {
        final Network network = Machine.current().network();
        final fURI origin = f("ws://0.0.0.0:28587");
        final Probe server = new Probe(origin, network);
        final Obj entry = listener(server, rec(studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt(1), uri("m")));
        assertThrows(MTronException.class, () -> network.index(entry), "a route is keyed by the path it serves");
        assertTrue(network.listener(origin.pathEncoding()).isNoObj(), "a refused mount stores nothing");
    }

    @Test
    public void testIndexRefusesARouteAnotherLevelClaims() {
        final Machine root = Machine.current();
        final fURI origin = f("ws://0.0.0.0:28588");
        final Probe server = new Probe(origin, root.network());
        final Obj rootEntry = listener(server, rec(uri("/m"), uri("m")));
        root.network().index(rootEntry);
        final Machine child = root.push(f("funnel_child"));
        root.network(); // the root level, explicitly
        try {
            final Obj childEntry = listener(server, rec(uri("/m"), uri("hijack")));
            assertThrows(MTronException.class, () -> child.network().index(childEntry),
                    "a key another level already serves on this socket has no rightful owner");
            assertTrue(child.network().listener(origin.pathEncoding()).isNoObj(), "the refused mount stored nothing");
            assertSame(rootEntry, root.network().listener(origin.pathEncoding()), "the holder is untouched");
        } finally {
            Machine.current(root);
            child.pop();
            root.network().drop(origin.pathEncoding());
        }
    }

    @Test
    public void testIndexDeclinesAListenerWithoutAnOriginOrServer() {
        final Network network = Machine.current().network();
        final Probe server = new Probe(f("ws://0.0.0.0:28589"), network);
        final Obj noOrigin = rec(uri(SERVER), server);
        noOrigin.selfTID(LISTENER_TID);
        assertTrue(network.index(noOrigin).isNoObj(), "no origin, no socket");
        final Obj noServer = rec(uri(ORIGIN), uri("ws://0.0.0.0:28589"));
        noServer.selfTID(LISTENER_TID);
        assertTrue(network.index(noServer).isNoObj(), "no server, no socket");
    }

    @Test
    public void testIndexIsIdempotentForTheSameListenerEntry() {
        final Network network = Machine.current().network();
        final fURI origin = f("ws://0.0.0.0:28590");
        final Probe server = new Probe(origin, network);
        final Obj entry = listener(server, rec(uri("/m"), uri("m")));
        network.index(entry);
        try {
            assertSame(entry, network.index(entry), "listening again replaces this level's routes, not its entry");
            assertEquals(1L, network.listeners().elements().count(), "one entry");
            assertEquals(1, server.sharers().size(), "and the level shares the socket once");
        } finally {
            network.drop(origin.pathEncoding());
        }
    }

    // ======================== drop: reach, by key / surface / service ========================

    @Test
    public void testDropByKeyRetractsExactlyOnePeerSurface() {
        final Network network = Machine.current().network();
        final fURI a = f("ws://212.7.1.9:28591/a");
        final fURI b = f("ws://212.7.1.9:28591/b");
        final Obj pa = network.index(client(a.toString()));
        final Obj pb = network.index(client(b.toString()));
        try {
            assertSame(pa, network.drop(a.pathEncoding()), "a key names that member, and the answer is it");
            assertTrue(network.peer(a.pathEncoding()).isNoObj(), "the named surface is gone");
            assertSame(pb, network.peer(b.pathEncoding()), "its sibling is untouched");
        } finally {
            network.drop(f("ws://212.7.1.9:28591"));
        }
    }

    @Test
    public void testDropByWireRouteRetractsThatSurface() {
        final Network network = Machine.current().network();
        final fURI a = f("ws://212.7.1.9:28592/a");
        final fURI b = f("ws://212.7.1.9:28592/b");
        final Obj pa = network.index(client(a.toString()));
        final Obj pb = network.index(client(b.toString()));
        try {
            assertSame(pa, network.drop(a), "a wire uri with a route names that surface");
            assertTrue(network.peer(a.pathEncoding()).isNoObj(), "the surface is gone");
            assertSame(pb, network.peer(b.pathEncoding()), "the service's other surface is untouched");
        } finally {
            network.drop(f("ws://212.7.1.9:28592"));
        }
    }

    @Test
    public void testDropByOriginRetractsEverySurfaceOfTheService() {
        final Network network = Machine.current().network();
        final fURI origin = f("ws://212.7.1.9:28593");
        network.index(client(origin.extend("a").toString()));
        network.index(client(origin.extend("b").toString()));
        final Obj dropped = network.drop(origin);
        assertEquals(2L, dropped.stream().count(), "a bare origin names every surface of the service");
        assertTrue(network.peers().elements().noneMatch(rel -> rel.first().isUri()), "the service's reach is gone");
    }

    // ======================== drop: presence, by key ========================

    @Test
    public void testDropByOriginLeavesTheSocketServing() {
        final Network network = Machine.current().network();
        final fURI origin = f("ws://0.0.0.0:28594");
        final Probe server = new Probe(origin, network);
        final Obj entry = listener(server, rec(uri("/m"), uri("m")));
        network.index(entry);
        network.index(client(origin.extend("mtron").toString()));
        try {
            final Obj dropped = network.drop(origin);
            assertTrue(dropped.stream().noneMatch(entry::equals), "a bare origin is REACH: the socket is not retracted");
            assertSame(entry, network.listener(origin.pathEncoding()), "the socket still serves");
            assertEquals(0, server.unbinds.get(), "and was never released");
        } finally {
            network.drop(origin.pathEncoding());
        }
    }

    @Test
    public void testDropByListenerKeyStopsTheSocket() {
        final Network network = Machine.current().network();
        final fURI origin = f("ws://0.0.0.0:28595");
        final Probe server = new Probe(origin, network);
        final Obj entry = listener(server, rec(uri("/m"), uri("m")));
        network.index(entry);
        assertSame(entry, network.drop(origin.pathEncoding()), "a socket is named by its key, and the answer is it");
        assertTrue(network.listener(origin.pathEncoding()).isNoObj(), "the entry is gone");
        assertEquals(1, server.unbinds.get(), "the owner's socket is released");
        assertTrue(server.closed(), "and closed");
        assertFalse(Network.Helper.authorities().contains(origin), "the origin is no longer an authority");
    }

    @Test
    public void testDropByKeyRetractsAPeerAndASocketSharingTheKey() {
        final Network network = Machine.current().network();
        final fURI origin = f("ws://0.0.0.0:28596");
        final Probe server = new Probe(origin, network);
        final Obj entry = listener(server, rec(uri("/m"), uri("m")));
        network.index(entry);
        network.index(client(origin.toString())); // a route-less dial: same key as the socket
        try {
            final Obj dropped = network.drop(origin.pathEncoding());
            assertEquals(2L, dropped.stream().count(), "the key names the member slot: a peer and a socket both live there");
            assertTrue(network.peer(origin.pathEncoding()).isNoObj(), "the peer is gone");
            assertTrue(network.listener(origin.pathEncoding()).isNoObj(), "and the socket with it");
        } finally {
            network.drop(origin.pathEncoding());
        }
    }

    @Test
    public void testDropAnswersNoobjWhenNothingNamed() {
        final Network network = Machine.current().network();
        assertTrue(network.drop(f("ws://212.7.1.9:28597/absent").pathEncoding()).isNoObj(), "nothing there");
        assertTrue(network.drop(f("ws://212.7.1.9:28597")).isNoObj(), "no reach, no presence, no answer");
        assertTrue(network.drop(f("ws://212.7.1.9:28597/x")).isNoObj(), "and dropping again answers noobj");
    }

    @Test
    public void testDropBlanksTheFieldWhenTheLastEntryGoes() {
        final Network network = Machine.current().network();
        final fURI dial = f("ws://212.7.1.9:28598/mtron");
        network.index(client(dial.toString()));
        network.drop(dial.pathEncoding());
        assertTrue(network.atDirect(uri(PEER)).isNoObj(), "an emptied peer map is an ABSENT field, not an empty rec");
        final fURI origin = f("ws://0.0.0.0:28598");
        final Probe server = new Probe(origin, network);
        network.index(listener(server, rec(uri("/m"), uri("m"))));
        network.drop(origin.pathEncoding());
        assertTrue(network.atDirect(uri(LISTENER)).isNoObj(), "and so is an emptied listener map");
    }

    // ======================== helpers ========================

    /**
     * THE MINIMAL CLIENT: a claim (its pattern) and an inst — what the index asks a client for.
     */
    private static Obj client(final String claim) {
        return rec(uri(PATTERN), uri(claim), uri(INST), rec());
    }

    /**
     * THE HANDLE {@code listen} builds: a listener::T entry carrying the socket, its origin and this level's routes.
     */
    private static Obj listener(final Listener server, final Rec routes) {
        final Obj entry = rec(uri(ORIGIN), uri(server.origin()), uri(SERVER), server);
        if (!routes.isEmpty())
            entry.asRec().at(uri(ROUTE), routes, MUTABLE);
        entry.selfTID(LISTENER_TID);
        return entry;
    }
}
