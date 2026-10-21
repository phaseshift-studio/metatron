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

package studio.phaseshift.metatron.isa.web.space.ws;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;

import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;

/**
 * The wire's history: a bounded, ordered window of exchanges, one lane per peer, with a detail window inside it.
 * <p>
 * Everything here is deterministic — exchanges are recorded by hand, so the window, the lanes, the eviction and
 * the detail policy are tested without a socket.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class WireTraceTest extends AbstractMetatronTest {

    private static final fURI TAG = f("/a1b2");

    @BeforeEach
    void resetTrace() {
        WireStats.reset();
        WireTrace.clear();
        WireTrace.pause(false);
    }

    private static void ok(final String peer, final String request) {
        WireTrace.exchange(peer, TAG, str(request), str("reply:" + request), System.currentTimeMillis(), 3L,
                WireTrace.Outcome.OK);
    }

    /**
     * The window forgets the oldest exchanges and says how many it forgot.
     */
    @Test
    void shouldBoundTheWindowAndCountWhatItDropped() {
        IntStream.range(0, WireTrace.MAX_EXCHANGES + 100).forEach(i -> ok("ws://peer:1/mtron", "r" + i));
        final WireTrace.Snapshot snapshot = WireTrace.snapshot(WireTrace.MAX_PER_PEER + 50);
        assertEquals(WireTrace.MAX_EXCHANGES + 100L, snapshot.exchanged(), "every exchange was seen");
        assertEquals(100L, snapshot.dropped(), "the window kept its size and counted the evictions");
        assertEquals(1, snapshot.lanes().size(), "one peer, one lane");
        assertEquals(WireTrace.MAX_PER_PEER, snapshot.lanes().getFirst().rows().size(),
                "a lane keeps at most MAX_PER_PEER rows");
    }

    /**
     * Lanes are bounded too: rows are capped per lane, but a lane per short-lived peer would otherwise accumulate
     * forever in a long session. The coldest lane goes when a new one would exceed the cap.
     */
    @Test
    void shouldBoundTheNumberOfLanesByDroppingTheColdest() {
        final int total = WireTrace.MAX_LANES + 5;
        IntStream.range(0, total).forEach(i -> ok("ws://peer" + i + ":1/mtron", "r" + i));
        final WireTrace.Snapshot snapshot = WireTrace.snapshot(5);
        assertEquals(WireTrace.MAX_LANES, snapshot.lanes().size(), "distinct peers are bounded like rows are");
        assertTrue(snapshot.lanes().stream().anyMatch(lane -> lane.peer().contains("peer" + (total - 1) + ":")),
                "the liveliest lane survives");
        assertFalse(snapshot.lanes().stream().anyMatch(lane -> lane.peer().contains("peer0:")),
                "and the coldest one is what went");
    }

    /**
     * A lane is the PEER. A socket that carries no dialed host is named with an identity hash so a log can tell
     * two sockets apart; that suffix must not become a lane of its own.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "ws://peer:1/mtron   % 1 % one dialed peer is one lane",
            "ws://peer:1/mtron#17 % 1 % the socket's identity hash does not split the lane",
    }, delimiter = '%')
    void shouldKeyLanesByPeerNotBySocket(final String peer, final int lanes, final String description) {
        ok("ws://peer:1/mtron", "first");
        ok(peer, "second");
        assertEquals(lanes, WireTrace.snapshot(10).lanes().size(), description);
        assertEquals(2L, WireTrace.snapshot(10).lanes().getFirst().exchanges(), description);
    }

    /**
     * Newest first, as every consumer of this reads it.
     */
    @Test
    void shouldOrderRowsNewestFirstPerLane() {
        IntStream.range(0, 3).forEach(i -> ok("ws://peer:1/mtron", "r" + i));
        final WireTrace.Row newest = WireTrace.snapshot(10).lanes().getFirst().rows().getFirst();
        assertTrue(newest.request().contains("r2"), "the newest exchange leads the lane, not the oldest");
        assertTrue(newest.reply().contains("reply:r2"), "the row carries the reply it was answered with");
        assertTrue(newest.ok());
    }

    /**
     * A timeout is a row, and a peer's lane counts its own.
     */
    @Test
    void shouldRecordATimeoutAsARowAndAPeerCount() {
        WireTrace.exchange("ws://slow:1/mtron", TAG, str("ping"), null, System.currentTimeMillis(),
                30_000L, WireTrace.Outcome.TIMEOUT);
        final WireTrace.Lane lane = WireTrace.snapshot(10).lanes().getFirst();
        assertEquals(1L, lane.timeouts(), "the lane counts the timeout");
        assertTrue(lane.rows().getFirst().reply().isEmpty(), "nothing came back, so there is no reply text");
        assertFalse(lane.rows().getFirst().ok(), "a timed-out exchange is not ok");
        assertEquals("timeout", WireTrace.snapshot(10).lanes().getFirst().rows().getFirst().kind(),
                "the row says what happened");
    }

    /**
     * An unsolicited inbound frame is a row of its own — it is the one inbound move this VM has no other view of.
     */
    @Test
    void shouldRecordAPushAsItsOwnRow() {
        WireTrace.push("ws://peer:1/mtron", str("listen=>true"));
        final WireTrace.Row row = WireTrace.snapshot(10).lanes().getFirst().rows().getFirst();
        assertEquals("push", row.kind(), "a push is neither a request nor a reply");
        assertTrue(row.request().isEmpty(), "nothing was asked");
        assertTrue(row.reply().contains("listen"), "the frame itself is the row's content");
    }

    /**
     * Detail objs are a window inside the window: the newest {@link WireTrace#DETAIL_DEPTH} keep their request and
     * reply as objs, the rest keep text only (objs pin a peer's frame graph alive).
     */
    @Test
    void shouldKeepDetailObjsOnlyForTheNewestExchanges() {
        final int total = WireTrace.DETAIL_DEPTH + 10;
        IntStream.range(0, total).forEach(i -> ok("ws://peer:1/mtron", "r" + i));
        final WireTrace.Lane lane = WireTrace.snapshot(total).lanes().getFirst();
        assertEquals(total, lane.rows().size());
        assertEquals(WireTrace.DETAIL_DEPTH,
                lane.rows().stream().takeWhile(WireTrace.Row::detail).count(),
                "the newest DETAIL_DEPTH rows still have their objs, and the ones behind them do not");
    }

    /**
     * Pausing holds the view still while the wire keeps moving: nothing new is recorded, nothing is lost.
     */
    @Test
    void shouldFreezeCaptureWhilePaused() {
        ok("ws://peer:1/mtron", "before");
        final long exchanged = WireTrace.snapshot(10).exchanged();
        WireTrace.pause(true);
        ok("ws://peer:1/mtron", "during");
        assertEquals(exchanged, WireTrace.snapshot(10).exchanged(), "a paused trace records nothing");
        WireTrace.pause(false);
        ok("ws://peer:1/mtron", "after");
        assertEquals(exchanged + 1, WireTrace.snapshot(10).exchanged(), "and resumes where it left off");
    }

    /**
     * The rendered text is one line and capped; a long value says it was cut rather than silently ending.
     */
    @Test
    void shouldRenderOneCappedLine() {
        final String longText = WireTrace.render(str("x".repeat(WireTrace.MAX_RENDER_CHARS * 2)));
        assertEquals(WireTrace.MAX_RENDER_CHARS, longText.length(), "a row is one capped line");
        assertTrue(longText.endsWith("…"), "and it says it was cut");
        final String plain = WireTrace.render(str("r0"));
        assertTrue(plain.contains("r0"), "a short value is shown as its own text");
        assertTrue(plain.chars().noneMatch(c -> c == '\n' || c == '\r'), "never a multi-line row");
    }

    /**
     * A cleared window starts over, including its sequence, so a view cannot show rows from before the clear.
     */
    @Test
    void shouldStartOverWhenCleared() {
        ok("ws://peer:1/mtron", "old");
        WireTrace.clear();
        assertTrue(WireTrace.snapshot(10).lanes().isEmpty(), "no lanes survive a clear");
        assertEquals(0L, WireTrace.snapshot(10).exchanged(), "and the window's count starts again");
    }

}
