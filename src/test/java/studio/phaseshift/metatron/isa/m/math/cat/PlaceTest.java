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

package studio.phaseshift.metatron.isa.m.math.cat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.isa.AbstractInstSetTest;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.Network;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.MACHINE;
import static studio.phaseshift.metatron.Tokens.PEER;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.MUTABLE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * {@code place::T} — the location vertex, exercised through the {@link Place} facade. A roster host lifts to a place
 * with its transports; a dialed peer is the place's outbound edge ({@code linked_to}) and flips its liveness from
 * {@code known} to {@code up}; and two loopback aliases of one address are one place (the {@code sameService}
 * quotient).
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class PlaceTest extends AbstractInstSetTest {

    public PlaceTest() {
        super(catInstSet::new);
    }

    @BeforeAll
    public static void importAllInstSets() {
        InstSet.importInstSet(f("#"));
        InstSet.importInstSet(f("/m/math"));
    }

    @BeforeEach
    public void clearNetwork() {
        Machine.write(Network.ROOT_ROSTER, rec(mutableMap()));
        Machine.current().network().at(uri(PEER), noobj(), MUTABLE);
    }

    /**
     * A roster host lifts to a place: {@code obj} is the machine address, {@code authority} its transports, and with
     * nothing dialed it is {@code known} with no linked edges.
     */
    @Test
    public void testRosterHostLiftsToPlace() {
        Machine.write(Network.ROOT_ROSTER, rec(mutableMap(uri(MACHINE), rec(mutableMap(
                uri("ginger.local"), objs(uri("http://192.168.1.250:8777"), uri("http://192.168.1.72:8777")))))));

        final Place place = (Place) Places.place(uri("//ginger.local"));
        assertEquals(f("//ginger.local"), place.obj());
        assertEquals(List.of(f("http://192.168.1.250:8777"), f("http://192.168.1.72:8777")), place.authorities());
        assertEquals("known", place.status());
        assertEquals(0, place.linkedTo().stream().count(), "nothing dialed: " + place.linkedTo());
        assertTrue(place.test(catInstSet.PLACE_TYPE), "a place is a place::T: " + place);
    }

    /**
     * A dialed peer whose service matches the place is its outbound edge, and the liveness reads {@code up} — the
     * machine is reachable, not merely known.
     */
    @Test
    public void testDialedPeerLinksAndReadsUp() {
        Machine.write(Network.ROOT_ROSTER, rec(mutableMap(uri(MACHINE), rec(mutableMap(
                uri("ginger.local"), uri("ws://localhost:8555"))))));
        Network.Helper.connect(Machine.current().network(), f("loop://localhost:8555"));

        final Place place = (Place) Places.place(uri("//ginger.local"));
        assertEquals("up", place.status());
        assertEquals(1, place.linkedTo().stream().count(), "one linked peer: " + place.linkedTo());
    }

    /**
     * Two loopback spellings of one service are one place: a peer dialed at {@code 127.0.0.1} links the place whose
     * roster row says {@code localhost} — the {@code sameService} alias quotient.
     */
    @Test
    public void testLoopbackAliasesAreOnePlace() {
        Machine.write(Network.ROOT_ROSTER, rec(mutableMap(uri(MACHINE), rec(mutableMap(
                uri("ginger.local"), uri("ws://localhost:8555"))))));
        Network.Helper.connect(Machine.current().network(), f("loop://127.0.0.1:8555"));

        final Place place = (Place) Places.place(uri("//ginger.local"));
        assertEquals("up", place.status());
        assertEquals(1, place.linkedTo().stream().count(),
                "127.0.0.1 and localhost are one place: " + place.linkedTo());
    }

    /**
     * A machine known only by a dial is still a place: the peer map is part of its authority set. No roster row —
     * the dial alone realizes the machine's transport.
     */
    @Test
    public void testDialedOnlyMachineIsAPlace() {
        Network.Helper.connect(Machine.current().network(), f("loop://ginger.local:8555"));

        final Place place = (Place) Places.place(uri("//ginger.local"));
        assertEquals(f("//ginger.local"), place.obj());
        assertEquals(List.of(f("loop://ginger.local:8555")), place.authorities());
        assertEquals("up", place.status());
        assertEquals(1, place.linkedTo().stream().count(), "the dial is the edge: " + place.linkedTo());
    }

    /**
     * An unknown host (no roster row, no dial) is not a place.
     */
    @Test
    public void testUnknownHostIsNotAPlace() {
        checkEquality(LOG, noobj(), Places.place(uri("//nobody.local")), true);
    }

    /**
     * The {@code as(place::T)} lift is registered: a machine address lifts through the {@code as} inst and the
     * derived fields resolve through {@code >>}.
     */
    @Test
    public void testAsLiftIsRegistered() {
        Machine.write(Network.ROOT_ROSTER, rec(mutableMap(uri(MACHINE), rec(mutableMap(
                uri("ginger.local"), uri("ws://localhost:8555"))))));
        checkCodeParseApply(LOG, "<//ginger.local>.as(place::T)>>obj", "<//ginger.local>");
        checkCodeParseApply(LOG, "<//ginger.local>.as(place::T)>>authority", "[<ws://localhost:8555>]");
        checkCodeParseApply(LOG, "<//ginger.local>.as(place::T)>>status", "known");
    }

    /**
     * The machine's own place (the {@code path.origin} source) requires an authority: a silent machine — nothing
     * listens — has no origin, so a program on it is not placeable.
     */
    @Test
    public void testSelfPlaceRequiresAuthority() {
        checkEquality(LOG, noobj(), Places.self(), true);
    }
}
