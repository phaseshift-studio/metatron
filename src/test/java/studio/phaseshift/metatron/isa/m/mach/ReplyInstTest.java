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

package studio.phaseshift.metatron.isa.m.mach;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.INT_TID;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * THE ROUND-TRIP ANCHOR, at the language level — the half a forked peer cannot show.
 * <p>
 * {@code anchor} is wire vocabulary: a round-tripping client WRAPS the program it ships, so the frame's root is the
 * anchor and the program is its blocking argument. Two things follow, and both are pinned here. The anchor applies
 * the tag to the ANSWER, so an absent answer still leaves with an address — as {@link machInstSet#NOOBJ_SENTINEL},
 * because {@code noobj} is the coefficient-zero singleton and no writer will render it with a vid. And it addresses
 * the answer by CLONING it ({@code clone().selfVID}), never by {@code vid()}, which clones and then WRITES the obj at
 * the address ({@code Obj.Helper.objCheckAndSave} → {@code Machine.write}): on a far side that write is one
 * bookkeeping entry per round trip on its variableStack, and beside the answer it is a value the peer never meant to
 * store.
 */
public class ReplyInstTest extends AbstractMetatronTest {

    /**
     * THE TAG ADDRESSES THE ANSWER — whatever the answer is. The last row is the one that matters to a scatter: a
     * {@code noobj} produced INSIDE the payload kills the monad at the next coefficient-1 inst, yet the answer the
     * anchor is handed in its body is still nothing, and nothing is exactly what it can now address.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "reply(start(3).plus(1), </sys/anchor/row-a>)                        % 4@</sys/anchor/row-a>",
            "reply(start(3), </sys/anchor/row-b>)                          % 3@</sys/anchor/row-b>",
            "reply({3}34, </sys/anchor/row-b2>)                             % {3}34@</sys/anchor/row-b2>",
            // a negative point is an answer like any other: the range is any coefficient, so with inst_rng checked the
            // answer was a range fail ("expected #{*}") where the program answered {-5}5. A span rides in the program:
            // as reply's own argument it would unroll along the argument axis
            "reply({-5}5, </sys/anchor/row-b3>)                             % {-5}5@</sys/anchor/row-b3>",
            "reply(start({-1}5{4}), </sys/anchor/row-b4>)                   % {-1}5{4}@</sys/anchor/row-b4>",
            "reply(start({-2,3}5), </sys/anchor/row-b5>)                    % {-2,3}5@</sys/anchor/row-b5>",
            "reply(noobj, </sys/anchor/row-c>)                             % </wire/noobj>@/sys/anchor/row-c",
            "reply(4.map(noobj).plus(1), </sys/anchor/row-d>)               % </wire/noobj>@/sys/anchor/row-d",
            // an answer with an address of its own keeps it: the tag rides as its last segment, never replacing it
            "reply(5.vid(</sys/anchor/own>), <0xe1>)                        % 5@</sys/anchor/own/0xe1>",
            // a tag that is no hex literal comes from a client built before the hex segment (it minted a bare 8-hex
            // id): it replaces the vid, because that client's router waits on the tag alone
            "reply(6.vid(</sys/anchor/own2>), <a1f30b7c>)                   % 6@a1f30b7c",
    }, delimiter = '%')
    void testTheAnchorAddressesTheAnswer(final String code, final String expected) {
        checkCodeParseApply(LOG, code, expected);
    }

    /**
     * THE ANCHOR DOES NOT STORE ITS BOOKKEEPING. Addressing the answer must cost the far side nothing. With
     * {@code vid(<tag>)} the answer would be written at the trip's address ({@code objClone} → {@code objCheckAndSave}
     * → {@code Machine.write}).
     */
    @Test
    void testTheAnchorDoesNotWriteItsAnswer() {
        final fURI trip = f("/sys/anchor/trip-a");
        final Obj answer = Machine.evaluate("reply(start(3).plus(1), </sys/anchor/trip-a>)");
        checkEquality(LOG, jnt(4).selfVID(trip), answer, true);
        checkEquality(LOG, noobj(), Machine.read(trip), true);
    }

    /**
     * THE CONTRAST, so nobody "simplifies" the anchor's body back to {@code vid()}: {@code vid(<uri>)} IS a
     * write-back — {@code @} semantics, "store this value at this address" — which is why it cannot double as
     * transport bookkeeping.
     */
    @Test
    void testVidWritesBackWhereTheAnchorDoesNot() {
        final fURI trip = f("/sys/anchor/trip-b");
        final Obj answer = Machine.parse("3.plus(1).vid(</sys/anchor/trip-b>)").apply(noobj());
        checkEquality(LOG, jnt(4).selfVID(trip), answer, true);
        // a value stored at an address answers ADDRESSED: the read hands back the obj at its vid
        checkEquality(LOG, jnt(4).selfVID(trip), Machine.read(trip), true);
        Machine.write(trip, noobj());
    }

    /**
     * THE MARK SURVIVES THE WIRE AS ITSELF: the sentinel the anchor mints is written with its vid and read back as
     * the same obj, so the router on the other side still recognizes it.
     */
    @Test
    void testTheMarkSurvivesTheWireAsItself() {
        final Obj marked = uri(machInstSet.NOOBJ_SENTINEL);
        final String frame = ObjmtronSerializer.single().write(marked);
        LOG.debug("the mark on the wire: %s", frame);
        final Obj read = ObjmtronSerializer.single().read(frame);
        checkEquality(LOG, marked, read, true);
        assertTrue(machInstSet.isNoobjSentinel(read), "the mark must still be recognized after a wire round trip: " + read);
    }

    /**
     * THE NEGATIVE CONTROL, and the reason the mark exists: {@code noobj} cannot carry a vid. A coefficient-zero obj
     * IS {@code noobj} by the VM's own definition ({@code isNoObj()} is {@code c().isZero()}), so the serializer's
     * root writer flattens it before a vid could be rendered. If this ever starts carrying its tag, the mark is
     * unnecessary and this row says so.
     */
    @Test
    void testAnAddressedNoobjCollapsesToNoobj() {
        final Obj addressed = jnt(0, INT_TID.zero(), f("/sys/anchor/trip-c"));
        LOG.debug("a {0} int carrying a vid: %s", ObjmtronSerializer.single().write(addressed));
        assertEquals("noobj", ObjmtronSerializer.single().write(addressed),
                "a zero-coefficient obj is noobj: the writer must not render its vid");
        checkEquality(LOG, noobj(), addressed, true);
    }

    /**
     * The client's reply read: the frame evaluated with {@link Obj.Helper#SELF_VID} bound, as
     * {@code WebSocketRecClient.onMessage} reads it ({@code mtron_wsHandlerEvaluationTest} pins that the client binds it).
     */
    private static Obj replyRead(final String frame) {
        return ScopedValue.where(Obj.Helper.SELF_VID, true).call(() -> ObjmtronSerializer.single().read(frame));
    }

    /**
     * A REPLY READ STORES NOTHING, starting with the trip's tag. A client evaluates the reply frames it reads, and an
     * addressed literal stores its value at its vid: a reply carries its trip's tag as the vid's hex last segment, and
     * storing it wrote a peer's answer into the client's own spaces, a vertex into the master's graph at
     * {@code V/1/0x…} (Marko, 2026-11-22). Read as a reply, the literal keeps its vid and stores nothing.
     */
    @Test
    void testATaggedAnswerIsReadWithoutAWrite() {
        final fURI tagged = f("/sys/anchor/stored/0xe2");
        checkEquality(LOG, jnt(5).selfVID(tagged), replyRead("5@</sys/anchor/stored/0xe2>"), true);
        checkEquality(LOG, noobj(), Machine.read(tagged), true);
    }

    /**
     * AN ANSWER'S MEMBERS ARE READ WITHOUT A WRITE. A lane that answers several objs ({@code //1 => @g:V/+}) comes back
     * as one answer whose root carries the tag while each member keeps its own vid, so a rule about the tag never saw
     * them: each member was written into the client's spaces (the master's graph at {@code V/1}, {@code V/2}, ...).
     * The far side's copies are deleted before the read, so only the read could put them back.
     */
    @Test
    void testAnAnswersMembersAreReadWithoutAWrite() {
        final fURI a = f("/sys/anchor/members/a");
        final fURI b = f("/sys/anchor/members/b");
        Machine.write(a, jnt(5));
        Machine.write(b, jnt(6));
        final String frame = ObjmtronSerializer.single().write(Machine.evaluate("reply(@</sys/anchor/members/+>, <0xe4>)"));
        LOG.debug("an answer of two addressed members on the wire: %s", frame);
        assertTrue(frame.contains("/sys/anchor/members/a") && frame.contains("/sys/anchor/members/b"),
                "the members must travel addressed, or this test proves nothing: " + frame);
        Machine.write(a, noobj());
        Machine.write(b, noobj());
        final Obj read = replyRead(frame);
        LOG.debug("the members read back: %s", read);
        assertTrue(read.toString().contains("/sys/anchor/members/a") && read.toString().contains("/sys/anchor/members/b"),
                "the members keep their own vids: " + read);
        checkEquality(LOG, noobj(), Machine.read(a), true);
        checkEquality(LOG, noobj(), Machine.read(b), true);
    }

    /**
     * A CONSTRUCTED ANSWER IS READ AS BUILT, WITHOUT A WRITE. A literal whose type has a constructor
     * ({@code int::T[?>0][plus(1)]}: the first bracket the predicate, the second the constructor) travels as type plus
     * value: the far side built {@code wnat::3} from 2 and answers it as it stands. Read through
     * {@code Obj.Helper.construct}, the constructor ran again and the client saw {@code wnat::4}. That path also stores
     * outside {@code objCheckAndSave}, so a guard in the save step alone never saw it.
     */
    @Test
    void testAConstructedAnswerIsReadWithoutAWrite() {
        Machine.evaluate("int::T[?>0][plus(1)]@wnat");
        final fURI tagged = f("/sys/anchor/ctor/0xe3");
        final Obj read = replyRead("wnat::3@</sys/anchor/ctor/0xe3>");
        LOG.debug("a constructed answer read back: %s", read);
        assertEquals(3, read.asInt().intValue(), "the far side built wnat::3, and a reply read must not build it again: " + read);
        assertEquals("wnat", read.tid().name(), "the answer keeps its type: " + read);
        assertEquals(tagged, read.vid(), "the answer keeps its vid: " + read);
        checkEquality(LOG, noobj(), Machine.read(tagged), true);
    }

    /**
     * A CONSTRUCTED ANSWER AT THE PREDICATE'S EDGE IS READ, NOT REFUSED. With {@code int::T[?<10][plus(1)]} the far side
     * builds 9 from 8 and answers {@code enat::9}. Rebuilt on read, 9 built 10, which the predicate refuses: the read
     * threw, and the literal carrying the trip's tag was lost, so the trip waited out its deadline.
     */
    @Test
    void testAConstructedAnswerAtThePredicatesEdgeIsReadAsBuilt() {
        Machine.evaluate("int::T[?<10][plus(1)]@enat");
        final fURI tagged = f("/sys/anchor/ctor/0xe5");
        final Obj read = replyRead("enat::9@</sys/anchor/ctor/0xe5>");
        LOG.debug("a constructed answer at the predicate's edge read back: %s", read);
        assertFalse(read.isFail() || read.isCaughtFail(), "the read rebuilt 9 into 10, which ?<10 refuses: " + read);
        assertEquals(9, read.asInt().intValue(), "the far side built enat::9: " + read);
        checkEquality(LOG, noobj(), Machine.read(tagged), true);
    }

    /**
     * THE CONTRAST: outside a reply read, {@code @} stores, as the language says. That holds for an address whose last
     * segment is a hex literal too (an i2c register, {@code </i2c/0x48>}): the tag is recognized by the client's air
     * lock, never by the save step, so no real address is silently left unstored.
     */
    @Test
    void testOutsideAReplyReadAnAddressedLiteralStores() {
        final fURI plain = f("/sys/anchor/stored/plain");
        ObjmtronSerializer.single().read("6@</sys/anchor/stored/plain>");
        checkEquality(LOG, jnt(6).selfVID(plain), Machine.read(plain), true);
        Machine.write(plain, noobj());
        final fURI register = f("/sys/anchor/i2c/0x48");
        ObjmtronSerializer.single().read("7@</sys/anchor/i2c/0x48>");
        checkEquality(LOG, jnt(7).selfVID(register), Machine.read(register), true);
        Machine.write(register, noobj());
    }
}
