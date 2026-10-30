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
            "reply(noobj, </sys/anchor/row-c>)                             % </wire/noobj>@/sys/anchor/row-c",
            "reply(4.map(noobj).plus(1), </sys/anchor/row-d>)               % </wire/noobj>@/sys/anchor/row-d",
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
}
