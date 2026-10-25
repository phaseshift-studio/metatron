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

package studio.phaseshift.metatron.isa.web.space.mdns.handler;

import studio.phaseshift.metatron.isa.mach.type.network.Listener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.Network;
import studio.phaseshift.metatron.isa.web.space.ws.wsServer;
import studio.phaseshift.metatron.util.CommonUtil;

import java.net.InetAddress;
import java.util.ArrayList;
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
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
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
 *       rather than onto itself, so a consumer reads ONE consolidated index and never searches per-protocol</li>
 *   <li><b>no row ever duplicates</b> — {@code listen()} announces every authority on every responder, so
 *       anything written inside that loop arrives once per interface</li>
 *   <li><b>a heard machine lands under its own key</b> as {@code <machine> => uri{+}}, several addresses in ONE
 *       entry, a re-heard address not duplicating, and {@code discover} surviving every machine write</li>
 *   <li><b>only a listener's dialable address leaves the box</b> — a backend's host and its login, a listener's
 *       query and any userinfo reach neither the TXT record nor the roster</li>
 * </ul>
 * The admission path above {@link mdnsDiscovery#roster} is deliberately NOT exercised here: it needs multicast,
 * a second host and a resolved {@code ServiceInfo}, none of which a unit test can fabricate. {@code
 * bin/test/mdns_analyzer.py} is the vehicle for that, end to end.
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
        try {
            return InetAddress.getByAddress(new byte[]{10, 0, 0, 1});
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
    }
    private static final fURI FIRST = f("ws://203.0.113.7:9999/mtron");
    private static final fURI SECOND = f("ws://198.51.100.4:9999/mtron");

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
            machines.asRec().jvm().forEach((machine, endpoints) -> {
                final long total = endpoints.stream().count();
                final long distinct = endpoints.stream().map(Obj::toString).distinct().count();
                assertEquals(distinct, total, "every endpoint of " + machine + " appears once: " + endpoints);
            });
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
        mdnsDiscovery.txt(surface).values().forEach(entry -> {
            assertFalse(entry.contains("?"), desc + " — no query reaches the TXT record: " + entry);
            assertFalse(entry.contains("@"), desc + " — nor any userinfo: " + entry);
        });
    }

    /**
     * A BACKEND'S LOGIN NEVER LEAVES THE BOX. A credentialed space mounted beside a listener — the pair
     * {@code boot/space/tble/mariadb.mtron} and a web server make — reaches neither the announced set, nor a TXT
     * record, nor the roster, while the listener reaches all three. The listener is the CONTROL: without it, a
     * handler that announced nothing at all would pass.
     */
    @Test
    public void testABackendsLoginIsNeverAnnounced() {
        final fURI listening = f("ws://0.0.0.0:" + generatePort());
        final fURI backend = f("/sys/space/mdns-backend");
        // patterned as the boot's are: a <vid>/# pattern would retract onto the vid, and the cache a web space
        // indexes beside itself would evict the listener from the index
        final Listener listener = wsServer.of(rec(mutableMap(
                uri(HOST), uri(listening.toString()))));
        mountBackend(backend);
        final mdnsDiscovery handler = handler();
        try {
            // THE ANNOUNCED SET needs no multicast: it is what every responder registers
            final List<String> surfaces = handler.surfaces().stream().map(fURI::toString).toList();
            assertTrue(surfaces.contains(listening.toString()), "the listener is announced: " + surfaces);
            surfaces.forEach(surface -> assertFalse(leaks(surface), "a backend is never a surface: " + surfaces));
            handler.surfaces().forEach(surface -> mdnsDiscovery.txt(surface).values().forEach(entry ->
                    assertFalse(leaks(entry), "no TXT entry carries the backend: " + entry)));
            // THE ROSTER is written as each responder announces, so it needs one
            assumeTrue(awaitStatus(handler, true, 20000L), "no multicast-capable interface here — mdns is inert");
            assertTrue(machineRows().contains(listening.toString()),
                    "the listener's own row is the control: " + roster());
            machineRows().forEach(row -> assertFalse(leaks(row), "no roster row carries the backend: " + roster()));
        } finally {
            handler.close();
            Machine.current().memory().removeSpace(backend);
            listener.close();
        }
    }

    @Test
    public void testADiscoveredMachineLandsInRosterMachine() {
        final mdnsDiscovery handler = handler();
        try {
            handler.roster(PEER, FIRST);
            assertTrue(holds(PEER, FIRST), "a heard address lands under its machine: " + roster());

            handler.roster(PEER, SECOND);
            assertTrue(holds(PEER, FIRST) && holds(PEER, SECOND),
                    "one machine on two interfaces is ONE entry with two addresses: " + roster());
            assertEquals(2L, addresses(PEER).stream().count(), roster().toString());

            handler.roster(PEER, FIRST);
            assertEquals(2L, addresses(PEER).stream().count(),
                    "re-hearing an address does not duplicate it: " + roster());
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

            handler.roster(PEER, FIRST);
            assertTrue(holds(PEER, FIRST), "the address landed: " + roster());
            assertFalse(discover().isNoObj(), "writing machine must not erase discover: " + roster());

            handler.roster(PEER, SECOND);
            assertFalse(discover().isNoObj(),
                    "discover survives every machine write, not just the first: " + roster());
        } finally {
            handler.close();
        }
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
     * every key and every address in {@code roster/machine}, as uri TEXT — an Obj's own rendering would carry its
     * vid, and a roster write can hand one out
     */
    private static List<String> machineRows() {
        final Obj machines = machines();
        final List<String> rows = new ArrayList<>();
        if (machines.isRec())
            machines.asRec().jvm().forEach((machine, addresses) -> {
                rows.add(machine.isUri() ? machine.uriValue().toString() : machine.toString());
                addresses.stream().map(address -> address.isUri() ? address.uriValue().toString() : address.toString())
                        .forEach(rows::add);
            });
        return rows;
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
        final Obj roster = roster();
        return roster.isRec() ? roster.asRec().at(uri(DISCOVER)) : noobj();
    }

    /**
     * BY TEXT, never by Obj equality — a uri that has round-tripped through the roster does not equal a freshly
     * minted one even when the two print the same address
     */
    private static Obj machines() {
        final Obj roster = roster();
        return roster.isRec() ? roster.asRec().at(uri(MACHINE)) : noobj();
    }

    private static Obj addresses(final fURI machine) {
        final Obj machines = machines();
        if (!machines.isRec())
            return noobj();
        final String key = machine.toString();
        return machines.asRec().jvm().entrySet().stream()
                .filter(entry -> entry.getKey().isUri() && key.equals(entry.getKey().uriValue().toString()))
                .map(Map.Entry::getValue).findFirst().orElse(noobj());
    }

    /**
     * BY BASE PATH — the roster stores an address canonically, so the stored form and a freshly built one agree
     * only once both are reduced to scheme, host, port and path
     */
    private static boolean holds(final fURI machine, final fURI address) {
        return addresses(machine).stream()
                .anyMatch(known -> known.isUri() && known.uriValue().basePath().equals(address.basePath()));
    }
}
