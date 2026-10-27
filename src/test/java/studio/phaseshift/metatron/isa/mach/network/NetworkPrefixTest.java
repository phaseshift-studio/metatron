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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * A NETWORK NAME IS DENOTATION, NOT REACH: {@code //self/a} denotes {@code loop://self/a} because a name was BOUND to
 * that origin, and the binding lives at the root so no frame can change what the spelling means.
 * <p>
 * Every row runs over the loop client — the identity peer, whose reads evaluate against this machine — so a name is
 * proved to denote exactly what its spelled-out twin denotes without a socket anywhere.
 */
public class NetworkPrefixTest extends AbstractMetatronTest {

    private static final fURI NAME = f("self");
    private static final fURI LOOP = f("loop://self");

    @BeforeEach
    void connect() {
        Machine.current().network().registerPrefix(NAME, LOOP);
        Machine.current().network().connect(LOOP);
    }

    @AfterEach
    void disconnect() {
        Machine.current().network().unregisterPrefix(NAME);
        Machine.current().network().disconnect(LOOP);
    }

    @Test
    void testTheNameDenotesTheOrigin() {
        assertEquals(Machine.read(f("loop://self/sys/thread/main")), Machine.read(f("//self/sys/thread/main")),
                "//self/a denotes loop://self/a");
    }

    @Test
    void testAnExplicitPortBeatsTheName() {
        // EXPLICIT ALWAYS BEATS NAMED: a port means the address names a service already, so the table must not touch it
        assertTrue(Machine.read(f("//self:8555/sys/thread/main")).isNoObj(), "an explicit port is taken literally");
    }

    @Test
    void testAnUnboundNameFailsClosed() {
        // knowledge is declared, never emergent: nothing may resolve a name just because a peer happens to answer
        assertTrue(Machine.read(f("//nobody/sys/thread/main")).isNoObj(), "an unbound name denotes nothing");
        // ... AND AN UNKNOWN SCHEME IS INERT: a spelling no table holds denotes nothing rather than exploding
        assertTrue(Machine.read(f("boo:")).isNoObj(), "an unbound scheme-only spelling is noobj");
        assertTrue(Machine.read(f("boo:/x")).isNoObj(), "an unbound scheme-only spelling is noobj");
    }

    /**
     * A SCHEME-ONLY SPELLING IS NOT A PEER, so a move to one must fail BY ADDRESS — never be shipped, and never reach
     * the origin arithmetic with no host to compute from (which is how it crashed before: "Index -1 out of bounds").
     */
    @Test
    void testAMoveToAnUnresolvedSpellingFailsCleanly() {
        final Obj moved = Machine.evaluate("move(boo:)");
        assertTrue(moved.isFail(), "an unresolvable move fails: " + moved);
        assertFalse(moved.toString().contains("Index"), "it must fail by address, not by list arithmetic: " + moved);
    }

    /**
     * PUSH SPEAKS THE SAME ADDRESS DIALECT AS MOVE, and it has no compile-time extractor to lean on — so the
     * canonicalization has to live in the frame arithmetic itself, or a named peer would be refused as "not a
     * descendant" instead of as what it is: another process.
     */
    @Test
    void testPushSeesTheSameResolvedName() {
        final Obj pushed = Machine.evaluate("push(<//self/a>)");
        assertTrue(pushed.isFail(), "a peer is not a frame: " + pushed);
        assertTrue(pushed.toString().contains("authority"), "it is refused as an authority, not as a bad descendant: " + pushed);
    }

    @Test
    void testTheSlotIsExactAndNeverRewritesAScheme() {
        // A REAL SCHEME IS NOT A NAME FOR A PROTOCOL: a binding called `self` must not touch `loop://self/...`, which
        // already says which transport it means. Only an address in the ADDRESSED slot -- no scheme, no port -- is a
        // name a binding may stand in for.
        Machine.current().network().registerPrefix(NAME, f("ws://gingersnap.local:8555"));
        assertEquals(f("loop://self/sys/thread/main"),
                Machine.Helper.align(Machine.current(), f("loop://self/sys/thread/main")),
                "a scheme-bearing address is taken literally");
        assertEquals(f("//self:8555/sys/thread/main"),
                Machine.Helper.align(Machine.current(), f("//self:8555/sys/thread/main")),
                "an explicit port is taken literally");
    }

    /**
     * THE DECLARATION IS A WRITE. A name is denotation, so it is bound where denotation lives — the network's own rec —
     * and the dial stays a separate act: {@code ws_client::[pattern= ><ws://…>]} connects, this names. The field is
     * also the unbinding door: writing {@code noobj} at it retracts every name at once, the universal close.
     */
    @Test
    void testTheNameIsDeclaredAndRetractedByAWrite() {
        this.script(
                "[STATE] </network/prefix/svc> -> <loop://self>",
                "//svc/sys/thread/main % loop://self/sys/thread/main % true",
                "[STATE] </network/prefix> -> noobj",
                "//svc/sys/thread/main % noobj % true");
    }

    @Test
    void testTheTableIsAddressableAndRetractable() {
        final Obj table = Machine.read(f("/network/prefix"));
        assertTrue(table.isRec(), "the network keeps its names: " + table);
        final Obj entry = table.asRec().at(uri("self"));
        assertTrue(entry.isUri(), "the row is the origin: " + entry);
        assertNotNull(entry.uriValue().host(), "the row names a host");
        // THE UNIVERSAL CLOSE, at the same address a peer is hung up at: a retracted name denotes nothing
        Machine.current().network().unregisterPrefix(NAME);
        assertTrue(Machine.read(f("//self/sys/thread/main")).isNoObj(), "a retracted name denotes nothing");
    }

    /**
     * THE ROOT MOVE — the whole point of a name. `move(<//boo/>)` ships the tail to the peer's ROOT machine: the
     * aligned path is empty, so the far side localizes the move to its own root (CUT's ROOT_MACHINE_VID branch).
     */
    @Test
    void testARootMoveShipsToThePeersRoot() {
        assertEquals(6, Machine.evaluate("5.move(<//self/>).plus(1)").intValue(),
                "a root move lands on the peer's root machine and the tail runs there");
    }

    /**
     * `//boo:a` IS NOT "NAME: PATH" — the colon is the PORT separator, and `a` is no port, so the spelling parses as
     * host `boo` with a path whose first segment is `:a`. A move to it must therefore FAIL BY ADDRESS. It used to crash
     * in the printer ("Index -1 out of bounds") when the resolved code rendered that segment as nothing, which hid the
     * diagnostic entirely; the failure itself is correct and unchanged.
     */
    @Test
    void testAColonAfterTheNameIsAPathSegmentNotAPort() {
        Machine.current().network().registerPrefix(f("boo"), f("ws://booger.local:8555"));
        final fURI colon = f("//boo:a");
        assertEquals("boo", colon.host(), "the name is the host");
        assertFalse(colon.hasPort(), "`a` is no port, so the colon lands in the path");
        final Obj moved = Machine.evaluate("move(//boo:a)");
        assertTrue(moved.isFail(), "a move to a path that is not a machine fails: " + moved);
        assertFalse(moved.toString().contains("Index"), "it must fail by address, not crash the printer: " + moved);
    }
}
