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

package studio.phaseshift.metatron.isa.m.web.space.mdns.handler;

import studio.phaseshift.metatron.isa.m.mach.type.network.Listener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.Network;
import studio.phaseshift.metatron.isa.m.web.space.ws.wsServer;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import javax.jmdns.ServiceInfo;
import javax.jmdns.impl.ServiceInfoImpl;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_FALSE;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_TRUE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec0;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * A DISCOVERY PROTOCOL'S WHOLE CONTRACT, pinned: it is live because it exists, dead because it was closed, its
 * published surface is exactly what the type declares, and its only product is {@code roster/machine}.
 * <ul>
 *   <li><b>construction starts it</b> — nobody calls {@code start()}, so {@code status} must go true on its own,
 *       which is the whole claim behind "turning them on is construction"</li>
 *   <li><b>construction registers it</b> in {@code roster/discover}, holding the handler itself rather than a
 *       copy of it</li>
 *   <li><b>close stops it</b> — there is no published {@code close} field, because a destructor is assumed the
 *       way a constructor is, and the status field follows liveness in BOTH directions</li>
 *   <li><b>the published surface is exactly the declared one</b> — the case that would have caught an
 *       {@code authority} field holding this machine's own authorities, four times over, and that fails the
 *       moment a field is added to the value without being added to the type</li>
 *   <li><b>this machine is a row in the table too</b> — the handler writes itself into {@code roster/machine}
 *       rather than onto itself, so a consumer reads ONE consolidated index and never searches per-protocol; a
 *       wildcard or loopback bind files no row, being no address anyone dials</li>
 *   <li><b>no row ever duplicates</b> — {@code listen()} announces every authority on every responder, so
 *       anything written inside that loop arrives once per interface</li>
 *   <li><b>a heard machine lands under its own key</b> as {@code <machine> => [<protocol> => [<transport> =>
 *       uri{+}]]}, several addresses in ONE list, each filed under what carries it, a re-heard instance filing
 *       nothing, its removal taking back exactly what it filed (an address another instance files stays), and
 *       {@code discover} surviving every machine write</li>
 *   <li><b>only a listener's dialable address leaves the box</b> — a backend's host and its login, a listener's
 *       query and any userinfo reach neither the TXT record nor the roster</li>
 *   <li><b>only an mtron endpoint is announced</b> — a listener's origin extended by a route whose handler speaks
 *       mtron (an {@code mtron::T} over the socket's scheme, metatron's mcp server over {@code mcp}), carried
 *       through the TXT record — protocol and transport included — to the dial address a hearer files and the
 *       keys it files it under; a route of another protocol, a wildcard route and a socket that serves no route
 *       stay home, and a frame's route is the frame's</li>
 * </ul>
 * The admission path ({@link mdnsDiscovery#admit}, {@link mdnsDiscovery#retract}) is exercised with resolutions built
 * as JmDNS builds them — a {@code ServiceInfoImpl} carrying its TXT record and addresses — and removals as JmDNS hands
 * them, the instance's name alone. What needs multicast and a second host is {@code bin/test/mdns_analyzer.py}'s, end
 * to end.
 */
public class mdnsDiscoveryTest extends AbstractMetatronTest {

    /**
     * ITS OWN SERVICE TYPE, never {@code _metatron._tcp.local.}: a test that browses the real service name finds
     * whatever metatron happens to be running on the developer's LAN, and then its assertions depend on the room
     * it is run in.
     */
    private static final fURI TEST_SERVICE = f("_metatron_test._tcp.local.");

    /** a machine key is a HOST — what both producers hand to {@link mdnsDiscovery#roster} */
    private static final fURI PEER = f("peerbox.local");

    private static final String PEER_HOST = "10.0.0.1";
    private static final int PEER_PORT = 8555;

    private static InetAddress peer() {
        return ip(10, 0, 0, 1);
    }
    private static final fURI FIRST = f("ws://203.0.113.7:9999/mtron");
    private static final fURI SECOND = f("ws://198.51.100.4:9999/mtron");
    /** the same box's metatron mcp server: mtron again, carried by mcp rather than by its socket's scheme */
    private static final fURI THIRD = f("ws://203.0.113.7:9999/mcp");
    /** a second machine, which no write to the first may touch */
    private static final fURI OTHER = f("otherbox.local");

    /**
     * A BACKEND'S HOST as {@code boot/space/tble/mariadb.mtron} writes one: the database login rides the query
     */
    private static final String BACKEND = "mariadb://127.0.0.1:3306/metatron?user=archivist&password=quill-and-ink";

    @AfterEach
    public void clearRoster() {
        // the roster is machine-global state, and these cases write to it
        try {
            Machine.write(Network.ROOT_ROSTER, rec());
        } catch (final Exception e) {
            this.LOG.warn("could not clear the roster after the case (%s)", e.getMessage());
        }
    }

    @Test
    public void testConstructionStartsTheResponders() {
        final mdnsDiscovery handler = handler();
        try {
            assumeTrue(awaitStatus(handler, true), "no multicast-capable interface here — mdns is inert");
            // status true IS the listener running: it is started && !closed && a responder bound, so nobody
            // called start() and the responders are up
            assertTrue(handler.status(), "live without anyone calling start()");
        } finally {
            handler.close();
        }
    }

    @Test
    public void testConstructionRegistersItselfInRosterDiscover() {
        final mdnsDiscovery handler = handler();
        try {
            final Obj discover = discover();
            assertFalse(discover.isNoObj(), "construction registers in roster/discover: " + roster());
            assertEquals(1L, discover.stream().count(), "one construction, one registration: " + roster());
            assertTrue(discover.stream().anyMatch(known -> known == handler),
                    "the roster holds the handler ITSELF, not a copy: " + roster());
        } finally {
            handler.close();
        }
    }

    /**
     * THE STATUS FIELD FOLLOWS LIVENESS BOTH WAYS. Asserted through the field rather than {@link
     * mdnsDiscovery#status()}, because the field is what the roster and a {@code ?[status=>false]} query read —
     * a Java method that is right while a published field is stale would pass a weaker test.
     */
    @Test
    public void testTheStatusFieldTracksLiveness() {
        final mdnsDiscovery handler = handler();
        try {
            assumeTrue(awaitStatus(handler, true), "no multicast-capable interface here — mdns is inert");
            final Obj field = handler.atDirect(uri(STATUS));
            assertFalse(field.isNoObj(), "status is published");
            assertEquals(BOOL_TRUE, field.apply(handler), "live while its responders are up");
            // AND THROUGH A READ, which is the path a query takes: auto_ is what makes
            // ~/network/roster/discover/+.?[status=>false] compare a bool instead of a Call
            assertEquals(BOOL_TRUE, handler.at(uri(STATUS)), "a read resolves it — auto_ is doing its job");
            handler.close();
            assertEquals(BOOL_FALSE, field.apply(handler), "and false once closed — derived, never stored");
            assertEquals(BOOL_FALSE, handler.at(uri(STATUS)), "and the read follows it down");
        } finally {
            handler.close();
        }
    }

    @Test
    public void testCloseStopsIt() {
        final mdnsDiscovery handler = handler();
        try {
            assumeTrue(awaitStatus(handler, true), "no multicast-capable interface here — mdns is inert");
            // NO close FIELD: a destructor is assumed the way a constructor is, so this is the whole contract
            handler.close();
            assertFalse(handler.status(), "a closed handler is not discovering");
            handler.close();
            assertFalse(handler.status(), "close is idempotent");
        } finally {
            handler.close();
        }
    }

    /**
     * THE VALUE MAY NOT CARRY WHAT THE TYPE DOES NOT DECLARE. The open world lets a rec hold more than its type
     * requires, but a discovery handler's surface is its whole interface, so drift between the two is a defect
     * rather than a liberty — and it is the kind that only shows up as a console surprise.
     */
    /**
     * DELETION IS HOW IT IS TURNED OFF, so writing {@code noobj} over its address has to close it. If eviction
     * does not close what it evicts, the responders outlive the entry: the roster says no discovery is running
     * while four JmDNS instances are still announcing on the wire.
     */
    @Test
    public void testWritingNoObjToItsAddressClosesIt() {
        final fURI address = f("/sys/test/mdns_discovery");
        final Map<Obj, Obj> jvm = new LinkedHashMap<>();
        jvm.put(uri(SERVICE), uri(TEST_SERVICE.toString()));
        final mdnsDiscovery handler = new mdnsDiscovery(jvm, address);
        try {
            assumeTrue(awaitStatus(handler, true), "no multicast-capable interface here — mdns is inert");
            Machine.write(address, handler);
            assertTrue(handler.status(), "still live once written to its address");
            Machine.write(address, noobj());
            assertTrue(awaitStatus(handler, false),
                    "mdns_uri -> noobj closes it: eviction must close what it evicts");
        } finally {
            handler.close();
        }
    }

    @Test
    public void testThePublishedSurfaceIsExactlyTheDeclaredOne() {
        final mdnsDiscovery handler = handler();
        try {
            assumeTrue(awaitStatus(handler, true), "no multicast-capable interface here — mdns is inert");
            final Map<Obj, Obj> fields = handler.jvm();
            assertTrue(fields.containsKey(uri(STATUS)), "status: " + fields.keySet());
            assertFalse(fields.containsKey(uri(HOST)),
                    "the host name is private — a passive background object publishes only its liveness: "
                            + fields.keySet());
            assertFalse(fields.containsKey(uri(LOCAL)),
                    "where this machine answers is a ROW in roster/machine, not a field: " + fields.keySet());
            assertFalse(fields.containsKey(uri(AUTHORITY)),
                    "what this machine exposes is a ROW in roster/machine, not a field: " + fields.keySet());
            assertFalse(fields.containsKey(uri(MACHINE)),
                    "there is one machine table and it is at roster/machine: " + fields.keySet());
            assertFalse(fields.containsKey(uri(CLOSE)),
                    "a destructor is assumed, not published: " + fields.keySet());
            assertEquals(2, fields.size(),
                    "service and status — and nothing else: " + fields.keySet());
        } finally {
            handler.close();
        }
    }

    /**
     * NO ROW IN THE MACHINE TABLE EVER DUPLICATES. {@code listen()} walks the local addresses and announces every
     * authority on every responder, so anything written inside that loop arrives once PER INTERFACE — which put
     * twelve entries where there were three endpoints before the write became idempotent.
     */
    @Test
    public void testTheMachineTableNeverDuplicates() {
        final mdnsDiscovery handler = handler();
        try {
            assumeTrue(awaitStatus(handler, true), "no multicast-capable interface here — mdns is inert");
            final Obj machines = machines();
            // a VM with no authorities declared and nothing heard yet has no table to check, and that is not a
            // failure -- only a populated table can be wrong
            assumeTrue(machines.isRec(), "nothing in the machine table yet");
            // ONCE PER LIST: an address is a duplicate where it is filed twice under one protocol and transport
            machines.asRec().jvm().forEach((machine, protocols) ->
                    protocols.orElse(rec0()).asRec().jvm().forEach((protocol, transports) ->
                            transports.orElse(rec0()).asRec().jvm().forEach((transport, endpoints) ->
                                    assertTrue(once(objs(endpoints.stream().toList())), "every endpoint of " + machine
                                            + " under " + protocol + "/" + transport + " is counted once: " + endpoints))));
        } finally {
            handler.close();
        }
    }

    /**
     * A DNS-SD INSTANCE NAME IS NOT NECESSARILY A VALID URI SEGMENT, and the key has to be one — so the name is
     * slugged on the way in. The root dot goes the way the stray dashes do: it is qualification, not name.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "metatron        % metatron       % a plain instance name is already a segment",
            "probe-155476    % probe-155476   % dots and dashes are legal in a segment",
            "fakepeer.local. % fakepeer.local % the DNS root dot is qualification, not part of the name",
            "metatron (2)    % metatron-2     % JmDNS disambiguates a collision with a space and parens",
            "lab a/b         % lab-a-b        % anything else becomes a dash rather than a broken uri",
    }, delimiter = '%')
    public void testAnInstanceNameBecomesAValidSegment(final String instance, final String expected,
                                                       final String desc) {
        assertEquals(expected, mdnsDiscovery.segment(instance), desc);
    }

    @Test
    public void testASegmentIsNeverEmpty() {
        assertEquals("peer", mdnsDiscovery.segment("..."), "a name that is all qualification");
        assertEquals("peer", mdnsDiscovery.segment(""));
        assertEquals("peer", mdnsDiscovery.segment(null));
    }

    /**
     * A RESPONDER'S ROUTE IS UNTRUSTED TEXT and the roster value has to be dialable as it stands, so the route is
     * normalized on the way in. The ROOT route extends nothing: {@code extend("/")} is a path of one empty
     * segment, which prints the same as no path and does not equal it — and a peer announcing {@code route=/} was
     * appended to its own row on every re-announcement, accumulating a coefficient instead of deduplicating.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "/                           % ws://10.0.0.1:8555                   % the root route extends nothing",
            "/mtron                      % ws://10.0.0.1:8555/mtron             % a real route is kept",
            "/,version=0.1-SNAPSHOT      % ws://10.0.0.1:8555                   % an announcer that welded its TXT together: recover the route it meant",
            "/mtron,version=0.1-SNAPSHOT % ws://10.0.0.1:8555/mtron             % ...and the same when a real route precedes the junk",
            "mtron                       % ws://10.0.0.1:8555                   % a route must be a PATH, so a bare name is refused",
            "/mtron?out=mtron            % ws://10.0.0.1:8555/mtron?out=mtron   % a query legitimately belongs to the route",
            "/a b                        % ws://10.0.0.1:8555                   % whitespace is refused",
            "/mtron/mcp                  % ws://10.0.0.1:8555/mtron/mcp         % a deeper route is kept whole",
    }, delimiter = '%')
    public void testAnAdvertisedRouteBecomesAUsableAddress(final String route, final String expected,
                                                           final String desc) {
        assertEquals(expected, mdnsDiscovery.dialable(peer(), PEER_PORT, "ws", route).toString(), desc);
    }

    /**
     * the CONTRACT itself, stated directly: whatever the roster holds can be dialed as-is
     */
    @Test
    public void testTheValueNeedsNoParsingToBeDialed() {
        // the worst advertisement seen in the wild: no scheme, and a route with the TXT welded onto it
        final fURI value = mdnsDiscovery.dialable(peer(), PEER_PORT, null, "/,version=0.1-SNAPSHOT");
        assertTrue(value.hasAuthority(), "a roster value must carry an authority: " + value);
        assertEquals(PEER_HOST, value.host(), "the resolved address is the host: " + value);
        assertEquals(PEER_PORT, value.port(), "the SRV port is the port: " + value);
        assertFalse(value.toString().contains(","), "no metadata may be welded into the address: " + value);
        assertFalse(value.toString().contains(VERSION), "no TXT property may leak into the address: " + value);
    }

    /**
     * PRODUCER-AGNOSTIC: whatever wrote a row, its key is clean. This is the case that keeps the next discovery
     * protocol from reintroducing the trailing dot.
     */
    @Test
    public void testNoRosterKeyCarriesATrailingDot() {
        final mdnsDiscovery handler = handler();
        try {
            assumeTrue(awaitStatus(handler, true), "no multicast-capable interface here — mdns is inert");
            final Obj machines = machines();
            assumeTrue(machines.isRec(), "nothing in the machine table yet");
            machines.asRec().jvm().keySet().forEach(machine -> {
                final String key = machine.isUri() ? machine.uriValue().toString() : machine.toString();
                assertFalse(key.endsWith("."), "a machine key carries no DNS root dot: " + key);
                assertFalse(key.isEmpty(), "a machine key is never empty: " + roster());
                // AN AUTHORITY, not a scheme+authority: one machine is one row however many surfaces it
                // answers on, and the schemes live in the values
                assertFalse(key.contains("://"), "a machine key carries no scheme: " + key);
                assertFalse(key.contains(":"), "nor a port — the key is a bare host: " + key);
            });
        } finally {
            handler.close();
        }
    }

    /**
     * ONLY THE ADDRESS A PEER DIALS IS ANNOUNCED, and the TXT record is built from it alone. What is announced is
     * multicast to every box on the link and filed in the roster, so a listener's query — configuration, a
     * parameter today and a token tomorrow — is dropped, and an address carrying userinfo is refused outright: a
     * fURI has no userinfo component, so {@code user@} welds into the host and a numeric password parses as the
     * PORT, and nothing can be stripped back out of that.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "ws://0.0.0.0:8555                                 % ws://0.0.0.0:8555       % a bare listener is announced as it stands",
            "ws://0.0.0.0:8555/mtron                           % ws://0.0.0.0:8555/mtron % its path is the route a peer dials",
            "ws://0.0.0.0:8555/mtron?out=application/x-mtron   % ws://0.0.0.0:8555/mtron % a query is configuration, never announced",
            "http://0.0.0.0:8777?user=archivist&password=quill % http://0.0.0.0:8777     % so a login in the query goes with it",
            "ws://archivist@0.0.0.0:8555                       % null                    % userinfo welds into the host: refused, not stripped",
            "ws://archivist:7341@0.0.0.0:8555                  % null                    % a numeric password parses as the PORT: refused",
            "ws://archivist:quill@0.0.0.0:8555                 % null                    % any other password leaves no port at all",
            "ws://0.0.0.0                                      % null                    % no port is nothing a peer can dial",
    }, delimiter = '%', nullValues = "null")
    public void testOnlyTheDialableAddressIsAnnounced(final String authority, final String expected,
                                                      final String desc) {
        final fURI surface = mdnsDiscovery.announceable(f(authority));
        if (null == expected) {
            assertNull(surface, desc + ": " + surface);
            return;
        }
        assertEquals(expected, surface.toString(), desc);
        mdnsDiscovery.txt(surface, f(surface.scheme())).values().forEach(entry -> {
            assertFalse(entry.contains("?"), desc + " — no query reaches the TXT record: " + entry);
            assertFalse(entry.contains("@"), desc + " — nor any userinfo: " + entry);
        });
    }

    /**
     * A BACKEND'S LOGIN NEVER LEAVES THE BOX. A credentialed space mounted beside a listener — the pair
     * {@code boot/space/tble/mariadb.mtron} and a web server make — reaches neither the announced set, nor a TXT
     * record, nor the roster, while the listener's mtron endpoint reaches all three. The endpoint is the CONTROL:
     * without it, a handler that announced nothing at all would pass.
     */
    @Test
    public void testABackendsLoginIsNeverAnnounced() throws SocketException {
        // THE CONTROL LISTENS ON A ROUTABLE ADDRESS when there is one: this machine's own row files no wildcard or
        // loopback surface (no address anyone dials), so the endpoint the roster must carry binds where mdns announces,
        // the first up, non-loopback IPv4 (mdnsDiscovery.localAddresses). Without one, the wildcard still answers the
        // announced set and the TXT records, which need no multicast, and only the roster half is skipped
        final String routable = NetworkInterface.networkInterfaces()
                .filter(iface -> {
                    try {
                        return iface.isUp() && !iface.isLoopback();
                    } catch (final SocketException e) {
                        return false;
                    }
                })
                .flatMap(NetworkInterface::inetAddresses)
                .filter(address -> address instanceof Inet4Address)
                .map(InetAddress::getHostAddress)
                .findFirst().orElse(null);
        final fURI listening = f("").scheme(WS).host(null == routable ? "0.0.0.0" : routable).port(generatePort());
        final fURI endpoint = listening.extend(f("/mtron"));
        final fURI backend = f("/memory/space/mdns-backend");
        // patterned as the boot's are: a <vid>/# pattern would retract onto the vid, and the cache a web space
        // indexes beside itself would evict the listener from the index
        final Listener listener = wsServer.of(rec(mutableMap(
                uri(ORIGIN), uri(listening.toString()),
                uri(ROUTE), rec(uri("/mtron"), uri("mtron_ws")))));
        mountBackend(backend);
        final mdnsDiscovery handler = handler();
        try {
            // THE ANNOUNCED SET needs no multicast: it is what every responder registers
            final List<String> surfaces = mdnsDiscovery.surfaces().keySet().stream().map(fURI::toString).toList();
            assertTrue(surfaces.contains(endpoint.toString()), "the mtron endpoint is announced: " + surfaces);
            surfaces.forEach(surface -> assertFalse(leaks(surface), "a backend is never a surface: " + surfaces));
            mdnsDiscovery.surfaces().forEach((surface, transport) -> mdnsDiscovery.txt(surface, transport).values()
                    .forEach(entry -> assertFalse(leaks(entry), "no TXT entry carries the backend: " + entry)));
            // THE ROSTER is filed after the initial sweep, and status turns true at the first responder: the own row
            // is awaited, never assumed to be there the moment the handler is live
            assumeTrue(null != routable, "no routable interface here — this machine files no own row");
            assumeTrue(awaitStatus(handler, true, 20000L), "no multicast-capable interface here — mdns is inert");
            for (final long until = System.currentTimeMillis() + 5000L;
                 !machineRows().contains(endpoint.toString()) && System.currentTimeMillis() < until; )
                CommonUtil.sleepThread(25);
            assertTrue(machineRows().contains(endpoint.toString()),
                    "the endpoint's own row is the control: " + roster());
            machineRows().forEach(row -> assertFalse(leaks(row), "no roster row carries the backend: " + roster()));
        } finally {
            handler.close();
            Machine.current().memory().removeSpace(backend);
            listener.close();
        }
    }

    /**
     * A WILDCARD OR LOOPBACK SURFACE FILES NO OWN ROW. {@code 0.0.0.0} is a bind, not an address, and a loopback is this
     * machine; filed in its own row, either is what a reader dialing every roster address
     * ({@code *~/network/roster/machine/+/mtron/ws.-<[host=>_].as(ws_client::T)}) dials as this machine's own peer. Each
     * row: the surface % the rows its own row files.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "ws://0.0.0.0:8555/mtron     % 0",
            "ws://127.0.0.1:8555/mtron   % 0",
            "ws://localhost:8555/mtron   % 0",
            "ws://[::]:8555/mtron        % 0",
            "ws://[0:0:0:0:0:0:0:0]:8555/mtron % 0",
            "ws://203.0.113.7:8555/mtron % 1",
    }, delimiter = '%')
    void testAWildcardOrLoopbackSurfaceFilesNoOwnRow(final String surface, final int rows) {
        final mdnsDiscovery handler = handler();
        try {
            assertEquals(rows, handler.ownRow(f(surface.trim()), f(WS)).size(), "the own row of " + surface);
        } finally {
            handler.close();
        }
    }

    /**
     * ONLY AN MTRON ENDPOINT IS ANNOUNCED: a listener's origin extended by a route whose handler speaks mtron — an
     * {@code mtron::T}, carried by the socket's scheme, or metatron's mcp server, carried by {@code mcp} (each row
     * reads {@code <route>:<transport>}). A peer dials an endpoint, never a socket (a ws_server answers its bare
     * origin with a banner of its routes), and a route of any other protocol is nobody's peer. Each announced
     * endpoint is then carried through its TXT record to the address a hearer files and the protocol and transport it
     * files it under — the round trip a remote roster row depends on, checked without multicast. The handler is written as the boot files write it (a bare name) and as a forked node mounts
     * it (a full tid); the data routes beside it ({@code /usr}, a web root) must resolve to nothing. Every announced
     * address must also read back as itself: this machine's own row stores its text and deduplicates against it.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "ws   % ws_server::[origin=><{{socket}}>,route=>[/mtron=>mtron_ws]]                          % /mtron:ws           % the mtron route is the endpoint a peer dials",
            "ws   % ws_server::[origin=><{{socket}}>,route=>[/=>mtron_ws]]                               % /:ws                % an mtron handler on the root route is the bare socket",
            "ws   % ws_server::[origin=><{{socket}}>,route=>[/mtron=>mtron_ws,/mcp=>mcp_mtron]]          % /mcp:mcp,/mtron:ws  % metatron's mcp server speaks mtron too, carried by mcp",
            "ws   % ws_server::[origin=><{{socket}}>,route=>[/mtron=>mtron_ws,/agent=>mtron_ws]]         % /agent:ws,/mtron:ws % every mtron route is its own endpoint",
            "ws   % ws_server::[origin=><{{socket}}>,route=>[/mtron=></m/web/ws/mtron_ws>]]              % /mtron:ws           % a handler named by its full tid, as a forked node mounts it",
            "ws   % ws_server::[origin=><{{socket}}>,route=>[/mcp=>mcp_mtron,/usr=>/usr]]                % /mcp:mcp            % a socket whose only mtron rides mcp announces that, and its data route nothing",
            "ws   % ws_server::[origin=><{{socket}}>,route=>[/mcp=>mcp_emulator_ws]]                     % none                % an mcp server that is not metatron's speaks no mtron",
            "ws   % ws_server::[origin=><{{socket}}>,route=>[/a/#=>mtron_ws]]                            % none                % a wildcard route names no address to dial",
            "ws   % ws_server::[origin=><{{socket}}>]                                                    % none                % nor does a socket that serves no route",
            "http % http_server::[origin=><{{socket}}>,route=>[/mtron=>mtron_http,/=>fs:docs/website]] % /mtron:http         % mtron over http is an mtron endpoint too",
            "http % http_server::[origin=><{{socket}}>,route=>[/mcp=>mcp_mtron]]                       % /mcp:mcp            % and metatron's mcp server on an http socket is carried by mcp",
    }, delimiter = '%')
    public void testOnlyAnMtronRouteIsAnnounced(final String scheme, final String construction, final String expected,
                                                final String desc) {
        final int port = generatePort();
        final fURI socket = f("").scheme(scheme).host("0.0.0.0").port(port);
        try {
            Machine.evaluate(construction.replace("{{socket}}", socket.toString()));
            assertTrue(Machine.current().network().listener(socket.pathEncoding()).isRec(),
                    desc + " — the server was constructed: " + Machine.current().network().listeners());
            assertEquals(expected, announced(port).isEmpty() ? "none" : String.join(",", announced(port)), desc);
            mdnsDiscovery.surfaces().forEach((surface, transport) -> {
                if (surface.port() != port)
                    return;
                assertEquals(surface.basePath(), f(surface.toString()).basePath(), desc
                        + " — an announced address reads back as itself, or this machine's row grows on every announcement: "
                        + surface);
                final Map<String, String> txt = mdnsDiscovery.txt(surface, transport);
                final fURI filed = mdnsDiscovery.dialable(peer(), port, txt.get(SCHEME), txt.get(ROUTE));
                assertEquals(List.of(scheme, PEER_HOST, port, surface.path()),
                        List.of(filed.scheme(), filed.host(), filed.port(), filed.path()),
                        desc + " — a hearer files the endpoint, route and all: " + txt);
                final ServiceInfo heard = ServiceInfo.create(TEST_SERVICE.toString(), "probe", port, 0, 0, txt);
                assertEquals(List.of(MTRON, transport.toString()),
                        List.of(mdnsDiscovery.protocol(heard).toString(), mdnsDiscovery.transport(heard).toString()),
                        desc + " — under the protocol and transport it was announced with: " + txt);
            });
        } finally {
            Machine.current().network().unlisten(socket);
        }
    }

    /**
     * PRESENCE IS INHERITED, AND A FRAME'S ROUTE IS THE FRAME'S. A pushed machine serving through its parent's socket
     * with a route of its own announces both endpoints, each handler resolved in the level that keeps it. The parent's
     * walk never descends into the frame, so it announces only its own: a frame is not a peer of the LAN.
     */
    @Test
    public void testAFrameAnnouncesWhatItInherits() {
        final int port = generatePort();
        final fURI socket = f("").scheme("ws").host("0.0.0.0").port(port);
        final Machine root = Machine.current();
        Machine.evaluate("ws_server::[origin=><" + socket + ">,route=>[/mtron=>mtron_ws]]");
        try {
            final Machine frame = root.push(f("mdns_frame"));
            try {
                Machine.evaluate("ws_server::[origin=><" + socket + ">,route=>[/agent=>mtron_ws]]");
                assertEquals(List.of("/agent:ws", "/mtron:ws"), announced(port), "the frame announces its own route and its parent's");
                Machine.current(root);
                try {
                    assertEquals(List.of("/mtron:ws"), announced(port), "the parent's walk never descends into the frame");
                } finally {
                    Machine.current(frame);
                }
            } finally {
                frame.pop();
            }
            assertEquals(List.of("/mtron:ws"), announced(port), "the frame's route left with the frame");
        } finally {
            Machine.current().network().unlisten(socket);
        }
    }

    /**
     * ONE DNS-SD INSTANCE PER ENDPOINT, named deterministically from it: a restart re-registers the same name, and
     * distinct routes on one socket are distinct names, the route kept as it stands (JmDNS renames a collision
     * {@code "name (2)"}, which neither a retirement nor a restart can find again). A route-less address keeps the
     * name it always had.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "ws://0.0.0.0:8555/mtron                         % lighthouse-ws-8555/mtron   % the route is part of the name",
            "ws://0.0.0.0:8555/agent                         % lighthouse-ws-8555/agent   % so a second mtron route on the socket is a second name",
            "ws://0.0.0.0:8555/a/b                           % lighthouse-ws-8555/a/b     % a deeper route is kept whole...",
            "ws://0.0.0.0:8555/a-b                           % lighthouse-ws-8555/a-b     % ...so it never meets the route a slug would fold it into",
            "http://0.0.0.0:8777/mtron                       % lighthouse-http-8777/mtron % scheme and port still tell surfaces apart",
            "ws://0.0.0.0:8555/mtron?out=application/x-mtron % lighthouse-ws-8555/mtron   % a listener's query is configuration, never part of the name",
            "ws://0.0.0.0:8555                               % lighthouse-ws-8555         % a route-less address keeps the name it had",
    }, delimiter = '%')
    public void testAnEndpointIsOneNamedInstance(final String surface, final String expected, final String desc) {
        assertEquals(expected, mdnsDiscovery.instanceName("lighthouse", f(surface)), desc);
    }

    @Test
    public void testADiscoveredMachineLandsInRosterMachine() {
        final mdnsDiscovery handler = handler();
        try {
            assertEquals(1, handler.roster("peer-ws", List.of(row(PEER, f(WS), FIRST))), "one row newly filed: " + roster());
            assertTrue(holds(PEER, f(WS), FIRST), "a heard address lands under its machine, protocol and transport: " + roster());

            handler.roster("peer-ws", List.of(row(PEER, f(WS), FIRST), row(PEER, f(WS), SECOND)));
            assertEquals(sorted(FIRST, SECOND), texts(addresses(PEER, f(MTRON), f(WS))),
                    "one machine on two interfaces is ONE list with two addresses: " + roster());

            assertEquals(0, handler.roster("peer-ws", List.of(row(PEER, f(WS), FIRST), row(PEER, f(WS), SECOND))),
                    "re-hearing an instance files nothing: " + roster());
            assertTrue(once(addresses(PEER, f(MTRON), f(WS))), "each address is counted once: " + roster());
        } finally {
            handler.close();
        }
    }

    @Test
    public void testTheMachineWriteKeepsDiscover() {
        final mdnsDiscovery handler = handler();
        try {
            // what belongs in discover IS a handler, so a clobbering write shows up as a missing registration
            Machine.write(Network.ROOT_ROSTER, rec(uri(DISCOVER), handler));
            assertFalse(discover().isNoObj(), "the seed itself landed: " + roster());

            handler.roster("peer-ws", List.of(row(PEER, f(WS), FIRST)));
            assertTrue(holds(PEER, f(WS), FIRST), "the address landed: " + roster());
            assertTrue(discover().stream().anyMatch(known -> known == handler),
                    "a machine write keeps discover, the handler itself: " + roster());

            handler.roster("peer-ws", List.of());
            assertTrue(discover().stream().anyMatch(known -> known == handler),
                    "and so does taking a row back: " + roster());
        } finally {
            handler.close();
        }
    }

    /**
     * ONE MACHINE, EACH ADDRESS UNDER WHAT CARRIES IT. Metatron's mcp server on a box is filed beside its ws endpoint,
     * never merged into it, and the scheme-keyed view ({@link Network#machines()}) holds only what a scheme's own
     * client speaks: an mcp endpoint on a ws socket is no address a ws_client can dial, so it is read at its own
     * address.
     */
    @Test
    public void testEachAddressIsFiledUnderItsTransport() {
        final mdnsDiscovery handler = handler();
        try {
            handler.roster("peer", List.of(row(PEER, f(WS), FIRST), row(PEER, f(MCP), THIRD)));
            assertTrue(holds(PEER, f(WS), FIRST) && holds(PEER, f(MCP), THIRD), "each under its transport: " + roster());
            assertFalse(holds(PEER, f(WS), THIRD), "an mcp endpoint is never filed as ws: " + roster());
            final Network network = Machine.current().network();
            assertEquals(List.of(FIRST.toString()), texts(network.machines().at(uri(PEER))),
                    "the scheme-keyed view holds what a ws_client dials, and no mcp endpoint: " + network.machines());
            assertEquals(List.of(THIRD.toString()), texts(addresses(PEER, f(MTRON), f(MCP))),
                    "the mcp endpoint is read at its own address: " + roster());
        } finally {
            handler.close();
        }
    }

    /**
     * A REMOVAL NAMES ONLY ITS INSTANCE, and takes back exactly what that instance filed: JmDNS builds the removal
     * from the PTR record alone, so nothing can be re-derived from it. The roster keeps a count, so an address a
     * second instance files stays until that one goes too, and another host and {@code discover} are never touched.
     */
    @Test
    public void testARemovalTakesBackWhatItsInstanceFiled() {
        final mdnsDiscovery handler = handler();
        try {
            handler.roster("a", List.of(row(PEER, f(WS), FIRST), row(PEER, f(WS), SECOND)));
            handler.roster("b", List.of(row(PEER, f(WS), FIRST)));
            handler.roster("c", List.of(row(OTHER, f(WS), FIRST)));
            handler.roster("a", List.of());
            assertEquals(List.of(FIRST.toString()), texts(addresses(PEER, f(MTRON), f(WS))),
                    "the address another instance files stays, the other goes: " + roster());
            handler.roster("b", List.of());
            checkEquality(LOG, noobj(), addresses(PEER, f(MTRON), f(WS)), true);
            checkEquality(LOG, noobj(), Machine.current().network().machines().at(uri(PEER)), true);
            assertTrue(holds(OTHER, f(WS), FIRST), "another host is never touched: " + roster());
            assertFalse(discover().isNoObj(), "nor is discover: " + roster());
        } finally {
            handler.close();
        }
    }

    /**
     * A RE-RESOLUTION REPLACES WHAT ITS INSTANCE FILED: an address it no longer names is taken back (a DHCP lease that
     * moved), and the one it names now is filed.
     */
    @Test
    public void testAReResolutionReplacesWhatItsInstanceFiled() {
        final mdnsDiscovery handler = handler();
        try {
            handler.roster("a", List.of(row(PEER, f(WS), FIRST)));
            handler.roster("a", List.of(row(PEER, f(WS), SECOND)));
            assertEquals(List.of(SECOND.toString()), texts(addresses(PEER, f(MTRON), f(WS))),
                    "the moved address replaces the old one: " + roster());
        } finally {
            handler.close();
        }
    }

    /**
     * AN INSTANCE TAKES BACK ONLY WHAT IT FILED, however often it is removed: a row taken back twice would leave a
     * negative count, and the next instance's filing would cancel against it instead of listing the address.
     */
    @Test
    public void testAnInstanceTakesBackOnlyWhatItFiled() {
        final mdnsDiscovery handler = handler();
        try {
            handler.roster("a", List.of());
            handler.roster("a", List.of(row(PEER, f(WS), FIRST)));
            handler.roster("a", List.of());
            handler.roster("a", List.of());
            handler.roster("b", List.of(row(PEER, f(WS), FIRST)));
            assertEquals(List.of(FIRST.toString()), texts(addresses(PEER, f(MTRON), f(WS))),
                    "a filing after removals is whole, no negative count behind it: " + roster());
            assertTrue(once(addresses(PEER, f(MTRON), f(WS))), "and counted once: " + roster());
        } finally {
            handler.close();
        }
    }

    /**
     * A REMOVAL AS JMDNS HANDS IT: built from the PTR record alone, it names the instance and nothing else, and takes
     * back exactly what the instance's resolution filed.
     */
    @Test
    public void testARemovalByNameTakesBackWhatTheResolutionFiled() {
        final mdnsDiscovery handler = handler();
        try {
            handler.admit(responder(1), resolved("peerbox-ws-8555/mtron", peer()));
            assertEquals(List.of(heardAt(peer()).toString()), texts(addresses(f("peer"), f(MTRON), f(WS))),
                    "the resolution files its address: " + roster());
            handler.retract(responder(1), removal("peerbox-ws-8555/mtron"));
            checkEquality(LOG, noobj(), addresses(f("peer"), f(MTRON), f(WS)), true);
        } finally {
            handler.close();
        }
    }

    /**
     * A RESOLUTION THAT IS NOT YET WHOLE FILES NOTHING AND TAKES NOTHING BACK: a re-resolution of the instance without
     * addresses (the A records arrive on their own), or without a TXT record though it carries one, leaves what the
     * instance filed as it was.
     */
    @Test
    public void testAnIncompleteResolutionFilesNothing() {
        final mdnsDiscovery handler = handler();
        try {
            handler.admit(responder(1), resolved("peerbox-ws-8555/mtron", peer()));
            handler.admit(responder(1), resolved("peerbox-ws-8555/mtron"));
            handler.admit(responder(1), resolution("peerbox-ws-8555/mtron", Map.of(), other()));
            assertEquals(List.of(heardAt(peer()).toString()), texts(addresses(f("peer"), f(MTRON), f(WS))),
                    "neither the address-less nor the TXT-less re-resolution moves what was filed: " + roster());
        } finally {
            handler.close();
        }
    }

    /**
     * EACH RESPONDER RESOLVES FROM ITS OWN CACHE, and where a socket hears only its own link two of them can see one
     * instance at different addresses: the instance files the union, and one responder's removal takes back only what
     * no other responder still resolves.
     */
    @Test
    public void testOneRespondersRemovalKeepsWhatAnotherResolves() {
        final mdnsDiscovery handler = handler();
        try {
            handler.admit(responder(1), resolved("peerbox-ws-8555/mtron", peer()));
            handler.admit(responder(2), resolved("peerbox-ws-8555/mtron", other()));
            assertEquals(sorted(heardAt(peer()), heardAt(other())), texts(addresses(f("peer"), f(MTRON), f(WS))),
                    "the instance files what both responders resolve: " + roster());
            handler.retract(responder(1), removal("peerbox-ws-8555/mtron"));
            assertEquals(List.of(heardAt(other()).toString()), texts(addresses(f("peer"), f(MTRON), f(WS))),
                    "one responder's removal keeps what the other resolves: " + roster());
            handler.retract(responder(2), removal("peerbox-ws-8555/mtron"));
            checkEquality(LOG, noobj(), addresses(f("peer"), f(MTRON), f(WS)), true);
        } finally {
            handler.close();
        }
    }

    /**
     * TWO RESPONDERS THAT RESOLVE ONE ADDRESS KEEP IT UNTIL BOTH GO, counted once: on Linux every responder hears every
     * link, so this is the usual case, and a removal that took back what its responder resolved would drop an address
     * the other still resolves.
     */
    @Test
    public void testAnAddressTwoRespondersResolveStaysUntilBothGo() {
        final mdnsDiscovery handler = handler();
        try {
            handler.admit(responder(1), resolved("peerbox-ws-8555/mtron", peer()));
            handler.admit(responder(2), resolved("peerbox-ws-8555/mtron", peer()));
            handler.retract(responder(1), removal("peerbox-ws-8555/mtron"));
            assertEquals(List.of(heardAt(peer()).toString()), texts(addresses(f("peer"), f(MTRON), f(WS))),
                    "the address the other responder resolves stays: " + roster());
            assertTrue(once(addresses(f("peer"), f(MTRON), f(WS))), "and is counted once: " + roster());
            handler.retract(responder(2), removal("peerbox-ws-8555/mtron"));
            checkEquality(LOG, noobj(), addresses(f("peer"), f(MTRON), f(WS)), true);
        } finally {
            handler.close();
        }
    }

    /**
     * A CLOSED HANDLER TAKES BACK WHAT IT FILED, AND FILES NOTHING AFTER: JmDNS's close sends no removal, and a
     * resolution it queued before the close still runs after it. A handler mounted again files over nothing.
     */
    @Test
    public void testAClosedHandlerTakesBackWhatItFiled() {
        final mdnsDiscovery first = handler();
        first.roster("a", List.of(row(PEER, f(WS), FIRST)));
        first.close();
        checkEquality(LOG, noobj(), addresses(PEER, f(MTRON), f(WS)), true);
        first.admit(responder(1), resolved("peerbox-ws-8555/mtron", peer()));
        first.roster("b", List.of(row(PEER, f(WS), SECOND)));
        checkEquality(LOG, noobj(), addresses(f("peer"), f(MTRON), f(WS)), true);
        checkEquality(LOG, noobj(), addresses(PEER, f(MTRON), f(WS)), true);
        final mdnsDiscovery second = handler();
        try {
            second.roster("a", List.of(row(PEER, f(WS), FIRST)));
            assertTrue(once(addresses(PEER, f(MTRON), f(WS))), "a handler mounted again files over nothing: " + roster());
        } finally {
            second.close();
        }
    }

    /**
     * A TAKE-BACK INTO A WIPED TABLE LEAVES NO SIGN: the machine table written noobj holds no count of the address,
     * so the instance's later take-back is skipped rather than stored as a negative that the next filing cancels.
     */
    @Test
    public void testATakeBackIntoAWipedTableLeavesNoSign() {
        final mdnsDiscovery handler = handler();
        try {
            handler.roster("a", List.of(row(PEER, f(WS), FIRST)));
            Machine.write(Network.ROOT_ROSTER.extend(MACHINE), noobj());
            handler.roster("a", List.of());
            checkEquality(LOG, noobj(), Machine.current().network().machines().at(uri(PEER)), true);
            handler.roster("b", List.of(row(PEER, f(WS), FIRST)));
            assertEquals(List.of(FIRST.toString()), texts(addresses(PEER, f(MTRON), f(WS))),
                    "the next filing is whole, no sign left behind: " + roster());
        } finally {
            handler.close();
        }
    }

    /**
     * WHAT A HEARER FILES AN ADDRESS UNDER comes from the TXT, which is untrusted text: the protocol and transport an
     * announcer names, else mtron carried by its scheme — what every build announced before the keys existed — and a
     * value that is no label is refused the same way, so a foreign responder cannot shape a key of the machine table.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "mtron % mcp  % ws   % mtron % mcp  % an announcer that names both is taken at its word",
            "null  % null % ws   % mtron % ws   % a build before the keys: mtron, carried by its scheme",
            "null  % null % http % mtron % http % ...whatever that scheme is",
            "null  % null % null % mtron % ws   % and with no scheme either, a peer answers over ws",
            "MTRON % a/b  % http % mtron % http % text that is no label is refused, never filed",
    }, delimiter = '%', nullValues = "null")
    public void testAHearerFilesWhatTheTxtNames(final String protocol, final String transport, final String scheme,
                                                final String filedProtocol, final String filedTransport,
                                                final String desc) {
        final Map<String, String> txt = new HashMap<>(Map.of(ROUTE, "/mtron"));
        if (null != protocol)
            txt.put(PROTOCOL, protocol);
        if (null != transport)
            txt.put(TRANSPORT, transport);
        if (null != scheme)
            txt.put(SCHEME, scheme);
        final ServiceInfo heard = ServiceInfo.create(TEST_SERVICE.toString(), "probe", PEER_PORT, 0, 0, txt);
        assertEquals(List.of(filedProtocol, filedTransport),
                List.of(mdnsDiscovery.protocol(heard).toString(), mdnsDiscovery.transport(heard).toString()), desc);
    }

    // ========================================================================

    private static mdnsDiscovery handler() {
        final Map<Obj, Obj> jvm = new LinkedHashMap<>();
        jvm.put(uri(SERVICE), uri(TEST_SERVICE.toString()));
        return new mdnsDiscovery(jvm, null);
    }

    /**
     * a space that CARRIES a host without listening on it — the shape of every backend space. BOOTING-guarded as
     * the machine suites mount theirs: registration writes the space at its own vid.
     */
    private static void mountBackend(final fURI vid) {
        final boolean booting = BootLoader.BOOTING;
        BootLoader.BOOTING = true;
        try {
            Machine.current().memory().addSpace(memSpace.of(rec(
                    uri(PATTERN), uri(vid.toString() + "/#"),
                    uri(HOST), uri(BACKEND)), vid));
        } finally {
            BootLoader.BOOTING = booting;
        }
    }

    /**
     * the backend, its login, or a query — none of which may appear in anything announced
     */
    private static boolean leaks(final String text) {
        return Stream.of("mariadb", "archivist", "quill-and-ink", "password", "?").anyMatch(text::contains);
    }

    /**
     * every machine key and every address in {@code roster/machine}, as uri TEXT
     */
    private static List<String> machineRows() {
        final List<String> rows = new ArrayList<>();
        machines().orElse(rec0()).asRec().jvm().forEach((machine, protocols) -> {
            rows.add(text(machine));
            protocols.orElse(rec0()).asRec().jvm().values().forEach(transports ->
                    transports.orElse(rec0()).asRec().jvm().values().forEach(addresses ->
                            objs(addresses.stream().toList()).stream().map(mdnsDiscoveryTest::text).forEach(rows::add)));
        });
        return rows;
    }

    /**
     * the endpoints announced on one port as {@code <route>:<transport>}, the root route as {@code /}, in order
     */
    private static List<String> announced(final int port) {
        return mdnsDiscovery.surfaces().entrySet().stream()
                .filter(entry -> entry.getKey().port() == port)
                .map(entry -> {
                    final String path = entry.getKey().basePath().localize().toString();
                    return (path.isEmpty() ? "/" : path) + ":" + entry.getValue();
                })
                .sorted()
                .toList();
    }

    private static boolean awaitStatus(final mdnsDiscovery handler, final boolean expected) {
        return awaitStatus(handler, expected, 5000L);
    }

    /**
     * the start is handed to the ThreadExecutor, so liveness is awaited rather than asserted outright. A handler
     * with surfaces to register takes longer to go live: each registration is probed before it is announced.
     */
    private static boolean awaitStatus(final mdnsDiscovery handler, final boolean expected, final long millis) {
        final long endTime = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < endTime) {
            if (expected == handler.status())
                return true;
            CommonUtil.sleepThread(25);
        }
        return expected == handler.status();
    }

    private static Obj roster() {
        return Machine.read(Network.ROOT_ROSTER);
    }

    private static Obj discover() {
        return Machine.read(Network.ROOT_ROSTER.extend(DISCOVER));
    }

    private static Obj machines() {
        return Machine.read(Network.ROOT_ROSTER.extend(MACHINE));
    }

    /**
     * the NET of the list at {@code /network/roster/machine/<machine>/<protocol>/<transport>}, read at its own
     * address: a filer adds its row and takes it back as the ring does, so the list holds every signed term
     */
    private static Obj addresses(final fURI machine, final fURI protocol, final fURI transport) {
        return objs(Machine.read(Network.ROOT_ROSTER.extend(MACHINE).extend(machine).extend(protocol).extend(transport))
                .stream().toList());
    }

    /**
     * one row as a discovery protocol files it: {@code [machine=>[<machine>=>[mtron=>[<transport>=><address>]]]]}
     */
    private static Obj row(final fURI machine, final fURI transport, final fURI address) {
        return rec(uri(MACHINE), rec(uri(machine), rec(uri(MTRON), rec(uri(transport), uri(address.toString())))));
    }

    /**
     * every address in a net list counted exactly once
     */
    private static boolean once(final Obj net) {
        return net.stream().allMatch(address -> 1L == address.c().max());
    }

    /**
     * a resolution as JmDNS hands one, with its TXT record and its addresses (the SRV target left empty: the host files
     * as {@code peer})
     */
    private static ServiceInfo resolved(final String name, final InetAddress... addresses) {
        return resolution(name, Map.of(ROUTE, "/mtron", SCHEME, WS, PROTOCOL, MTRON, TRANSPORT, WS), addresses);
    }

    /**
     * a resolution built as JmDNS builds one: a {@code ServiceInfoImpl} carrying the TXT record and the addresses
     */
    private static ServiceInfo resolution(final String name, final Map<String, String> txt, final InetAddress... addresses) {
        return new ServiceInfoImpl(TEST_SERVICE.toString(), name, "", PEER_PORT, 0, 0, false, txt) {
            @Override
            public Inet4Address[] getInet4Addresses() {
                return Stream.of(addresses).map(Inet4Address.class::cast).toArray(Inet4Address[]::new);
            }
        };
    }

    /**
     * a removal as JmDNS hands one: the instance's name, and nothing else
     */
    private static ServiceInfo removal(final String name) {
        return ServiceInfo.create(TEST_SERVICE.toString(), name, 0, 0, 0, Map.of());
    }

    /**
     * the local address a responder is bound to, the key its resolutions are kept under
     */
    private static InetAddress responder(final int index) {
        return ip(127, 0, 0, 10 + index);
    }

    /**
     * an IPv4 address from its four octets
     */
    private static InetAddress ip(final int a, final int b, final int c, final int d) {
        try {
            return InetAddress.getByAddress(new byte[]{(byte) a, (byte) b, (byte) c, (byte) d});
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }

    /** where a hearer files a resolution's address */
    private static fURI heardAt(final InetAddress address) {
        return mdnsDiscovery.dialable(address, PEER_PORT, WS, "/mtron");
    }

    private static InetAddress other() {
        return ip(10, 0, 0, 2);
    }

    private static List<String> sorted(final fURI... addresses) {
        return Stream.of(addresses).map(fURI::toString).sorted().toList();
    }

    /**
     * BY BASE PATH — the roster stores an address canonically, so the stored form and a freshly built one agree
     * only once both are reduced to scheme, host, port and path
     */
    private static boolean holds(final fURI machine, final fURI transport, final fURI address) {
        return addresses(machine, f(MTRON), transport).stream()
                .anyMatch(known -> known.isUri() && known.uriValue().basePath().equals(address.basePath()));
    }

    /**
     * a list's addresses as uri TEXT, in order — an Obj's own rendering would carry its vid, and a roster write can
     * hand one out
     */
    private static List<String> texts(final Obj addresses) {
        return addresses.stream().map(mdnsDiscoveryTest::text).sorted().toList();
    }

    private static String text(final Obj obj) {
        return obj.isUri() ? obj.uriValue().toString() : obj.toString();
    }
}
