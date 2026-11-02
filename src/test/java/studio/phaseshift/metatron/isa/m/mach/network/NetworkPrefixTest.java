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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.PREFIX;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Poly.MUTABLE;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import studio.phaseshift.metatron.isa.m.mach.type.Network;

/**
 * A NETWORK NAME IS DENOTATION, NOT REACH: {@code //self/a} denotes {@code loop://self/a} because a name was BOUND to
 * that origin, and the binding lives at the root so no frame can change what the spelling means.
 * <p>
 * A NAME IS SPELLED {@code //name} AND STORED {@code name}: the door reads and writes the spelling a peer is called by,
 * and the table keeps the label resolution asks for. What is read is therefore what may be written back, and a row
 * spelled any other way is refused rather than stored under an address no read would ever ask for.
 * <p>
 * Every row runs over the loop client — the identity peer, whose reads evaluate against this machine — so a name is
 * proved to denote exactly what its spelled-out twin denotes without a socket anywhere.
 */
public class NetworkPrefixTest extends AbstractMetatronTest {

    private static final fURI NAME = f("//self");
    private static final fURI LOOP = f("loop://self");
    /**
     * the reported spelling, bound to a ws origin and never dialed: alignment is pure, so the whole tail can be proved
     * without a socket anywhere.
     */
    private static final fURI GINGER = f("//ginger");
    private static final fURI GINGER_WS = f("ws://ginger.local:8555");

    @BeforeEach
    void connect() {
        Machine.current().network().registerPrefix(NAME, LOOP);
        Machine.current().network().registerPrefix(GINGER, GINGER_WS);
        Network.Helper.connect(Machine.current().network(), LOOP);
    }

    @AfterEach
    void disconnect() {
        Machine.current().network().unregisterPrefix(NAME);
        Machine.current().network().unregisterPrefix(GINGER);
        // a row a test declared is not the next test's: the class proves the door's dialect, so it must not inherit one
        Machine.write(f("/network/prefix"), noobj());
        Machine.current().network().drop(LOOP);
    }

    /**
     * The keys a rec holds, by text — a {@code //self} key has no path segment, so it cannot be {@code at()}'d; only
     * walked.
     */
    private static Set<String> keys(final Rec rec) {
        return rec.jvm().keySet().stream()
                .filter(Obj::isUri)
                .map(key -> key.uriValue().toString())
                .collect(Collectors.toSet());
    }

    @Test
    void testTheNameDenotesTheOrigin() {
        assertEquals(Machine.read(f("loop://self/sys/thread/main")), Machine.read(f("//self/sys/thread/main")),
                "//self/a denotes loop://self/a");
    }

    /**
     * THE WHOLE TAIL RIDES: a name resolves the ORIGIN and nothing else, so every segment after the name is the far
     * side's to serve — {@code //ginger/a/b/c} is {@code ws://ginger.local:8555/a/b/c}, never the origin's root.
     * <p>
     * Asserted at {@link Machine.Helper#align} — the ONE door {@code read}, {@code write} and the move-extractor all
     * ask — because alignment is pure: the far machine need not exist to prove what the spelling denotes.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '%', value = {
            "//ginger/a                 % ws://ginger.local:8555/a",
            "//ginger/a/b/c             % ws://ginger.local:8555/a/b/c",
            "//ginger/a/b/c?depth=3     % ws://ginger.local:8555/a/b/c?depth=3",
            // EXPLICIT AND SCHEMED SPELLINGS ARE NOT NAMES: a port names a service, a scheme names its transport
            "//ginger:8555/a            % //ginger:8555/a",
            "loop://ginger/a            % loop://ginger/a",
            "http://ginger/a/b/c        % http://ginger/a/b/c",
            // AN UNBOUND NAME IS ANSWERED AS WRITTEN, and fails closed downstream
            "//nobody/a/b/c             % //nobody/a/b/c",
            // A DOTTED HOST IS A HOSTNAME, never a name a binding may shadow
            "//ginger.local/a           % //ginger.local/a",
    })
    void testTheNameResolvesTheWholeTail(final String written, final String resolved) {
        assertEquals(f(resolved), Machine.Helper.align(Machine.current(), f(written)), written);
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

    /**
     * THE TABLE IS READ IN THE SPELLING IT IS WRITTEN IN, and stored in the one resolution asks for. A row is
     * {@code //self => origin} when read through the door and {@code self => origin} in the component's own rec —
     * that strip IS the lookup: {@code //self/a} asks the table for {@code self}.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '%', value = {
            // the spelling a peer is called by | the label the table stores | the origin it denotes
            "//self                     % self                   % loop://self",
            "//ginger                   % ginger                 % ws://ginger.local:8555",
    })
    void testTheDoorShowsTheMarkerAndTheTableStoresTheLabel(final String shownKey, final String storedKey,
                                                            final String origin) {
        final Obj shown = Machine.read(f("/network/prefix"));
        assertTrue(shown.isRec(), "the network keeps its names: " + shown);
        assertTrue(keys(shown.asRec()).contains(shownKey), "the door reads the spelling a peer is called by: " + shown);
        assertTrue(keys(Machine.current().network().prefixes()).contains(storedKey),
                "the table stores the label resolution asks for: " + Machine.current().network().prefixes());
        // AN ENTRY IS ADDRESSED BY THE LABEL -- the only form a path can carry
        final Obj entry = Machine.read(f("/network/prefix/" + storedKey));
        assertTrue(entry.isUri(), "the entry is the row: " + entry);
        assertEquals(origin, entry.uriValue().toString(), "the entry is the origin it denotes");
    }

    @Test
    void testTheTableIsAddressableAndRetractable() {
        final Obj table = Machine.read(f("/network/prefix"));
        assertTrue(table.isRec(), "the network keeps its names: " + table);
        final Obj entry = table.asRec().elements()
                .filter(row -> row.first().isUri() && row.first().uriValue().toString().equals("//self"))
                .map(row -> row.second()).findFirst().orElse(noobj());
        assertTrue(entry.isUri(), "the row is the origin: " + entry);
        assertNotNull(entry.uriValue().host(), "the row names a host");
        // THE UNIVERSAL CLOSE, at the same address a peer is hung up at: a retracted name denotes nothing
        Machine.current().network().unregisterPrefix(NAME);
        assertTrue(Machine.read(f("//self/sys/thread/main")).isNoObj(), "a retracted name denotes nothing");
    }

    /**
     * THE DOOR TAKES THE SPELLING, NOT THE KEY. A table written with markers is stripped once, on the way in, so the
     * row it creates is one resolution will ask for — and reading the door back answers exactly what was written.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '%', value = {
            // the key as written | the label it is stored under
            "//svc                  % svc",
            "//ginger2              % ginger2",
    })
    void testTheWholeTableDoorStripsTheMarker(final String written, final String stored) {
        final Obj answer = Machine.write(f("/network/prefix"), rec(uri(written), uri(LOOP.toString())));
        assertFalse(answer.isNoObj(), written + " is a prefix and is accepted: " + answer);
        assertTrue(keys(answer.asRec()).contains(written), "the write answers the spelling it was given: " + answer);
        assertTrue(keys(Machine.read(f("/network/prefix")).asRec()).contains(written), "what is written is what is read");
        assertTrue(keys(Machine.current().network().prefixes()).contains(stored), "the row is stored as its label");
        assertFalse(Machine.read(f(written + "/sys/thread/main")).isNoObj(), "the row the door wrote resolves");
    }

    /**
     * THE REPORTED CONSOLE LINE, END TO END: the expression form binds the row, echoes the spelling it was written in,
     * and the name then reads through to the origin the row names.
     */
    @Test
    void testTheConsoleLineBindsAndEchoesTheMarkedSpelling() {
        final Obj echo = Machine.evaluate("/network/prefix -> [//svc => <loop://self>]");
        assertTrue(echo.isRec(), "the write answers the table it wrote: " + echo);
        assertTrue(keys(echo.asRec()).contains("//svc"), "the echo keeps the spelling: " + echo);
        assertTrue(keys(Machine.current().network().prefixes()).contains("svc"), "the table keeps the label");
        assertFalse(Machine.read(f("//svc/sys/thread/main")).isNoObj(), "and the named tail reads through");
    }

    /**
     * ANYTHING THAT IS NOT A NAME IS REFUSED — not repaired. An unprefixed key, a scheme, an explicit port, a dotted
     * host: storing one would be the silent, unaddressable table this dialect exists to prevent, and the refusal must
     * leave the table it declined to write untouched.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '%', value = {
            "svc",           // no marker: not a name, however label-shaped it looks
            "loop://svc",    // a scheme is a transport, already named
            "//svc:8555",    // an explicit port names a service: explicit always beats named
            "ginger.local",  // a dotted host is a HOSTNAME, which a binding must not shadow
    })
    void testAKeyThatIsNotANameIsRefused(final String written) {
        final Obj refused = Machine.write(f("/network/prefix"), rec(uri(written), uri(LOOP.toString())));
        assertTrue(refused.isNoObj(), written + " is not a prefix: " + refused);
        assertTrue(Machine.read(f("//svc/sys/thread/main")).isNoObj(), "nothing was stored under it");
        assertEquals(Machine.read(f("loop://self/sys/thread/main")), Machine.read(f("//self/sys/thread/main")),
                "the bindings the write declined to replace are still there");
    }

    /**
     * A ROW WRITTEN BEFORE THE MARKER BECAME THE SPELLING IS REPAIRED BY THE ROUND TRIP. A table holding the address
     * spelling as its key ({@code //svc => origin}, stored beside the component rather than through the door) is the
     * silent, unresolvable shape that was reported: it reads back, it prints, and no read ever asks for it. Writing
     * back what the door SHOWS strips the marker and makes the same row live — no migration, no lost table.
     */
    @Test
    void testAStaleRowIsRepairedByWritingBackWhatTheDoorShows() {
        // the legacy shape, put on the component's own rec -- under the door, which would have refused it
        Machine.current().network().at(uri(PREFIX), rec(uri("//svc"), uri(LOOP.toString())), MUTABLE);
        assertTrue(Machine.read(f("//svc/sys/thread/main")).isNoObj(), "a marked key is not the key resolution asks for");
        Machine.write(f("/network/prefix"), Machine.read(f("/network/prefix")));
        assertFalse(Machine.read(f("//svc/sys/thread/main")).isNoObj(), "writing back what was read repairs the row");
        assertTrue(keys(Machine.current().network().prefixes()).contains("svc"), "the row is now stored as its label");
    }

    /**
     * THE JAVA ACCESSORS SPEAK THE SAME DIALECT AS THE DOOR: a name is given spelled {@code //name} or as the label
     * the table stores, and either way ONE row is bound — not a second key beside the first.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '%', value = {
            // registered by | unbound by: each row crosses the two spellings on purpose
            "//bound        % bound",
            "bound          % //bound",
    })
    void testTheAccessorsTakeEitherSpellingAndBindOneRow(final String registered, final String unbound) {
        Machine.current().network().registerPrefix(f(registered), LOOP);
        assertEquals(1, keys(Machine.current().network().prefixes()).stream().filter("bound"::equals).count(),
                "the label and its spelling are one row: " + Machine.current().network().prefixes());
        Machine.current().network().unregisterPrefix(f(unbound));
        assertTrue(Machine.read(f("//bound/sys/thread/main")).isNoObj(),
                "unbinding by either spelling removes the one row");
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
