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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * THE WIRE'S LEDGER — what actually crossed a socket, and how long a round trip took.
 * <p>
 * It is tapped where frames are: {@link WebSocketObj#send} (out), {@link WebSocketRec#onMessage} (in, which is
 * the single inbound funnel for both a server's rec and a client's) and {@link WebSocketObj#sendRecv} (a trip's
 * completion). That is deliberately BELOW the q-proc layer: a {@code ?subq} on the network sees only the writes
 * this VM ADDRESSES at a peer, so it cannot see a reply (there is no {@code qless_read}) and it cannot see an
 * inbound push at all. Counters that claim to be about the wire therefore cannot be fed by subq — they have to be
 * tapped where the wire is.
 * <p>
 * The counters are hot-path cheap: atomics on the frame path, and {@link #listen} listeners are only called when
 * someone is actually listening (so an unattended tap costs one {@code isEmpty} check per frame).
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class WireStats {

    /**
     * ONE FRAME on the wire. {@code peer} is the socket's name (its dialed host where it has one — see
     * {@link WebSocketObj#peerName()}), and {@code vid} is the address the frame carried, which may be null for
     * a frame that names nothing.
     */
    public record Frame(String peer, studio.phaseshift.metatron.furi.fURI vid, boolean inbound) {
    }

    private static final AtomicLong SENT = new AtomicLong();
    private static final AtomicLong RECEIVED = new AtomicLong();
    private static final AtomicLong ROUND_TRIPS = new AtomicLong();
    private static final AtomicLong TIMEOUTS = new AtomicLong();
    /**
     * the most recent completed round trip, in milliseconds; -1 when none has completed
     */
    private static final AtomicLong LAST_ROUND_TRIP_MS = new AtomicLong(-1L);

    private static final List<Consumer<Frame>> LISTENERS = new CopyOnWriteArrayList<>();

    private WireStats() {
        // static only
    }

    /**
     * Observe frames as they cross the wire. A listener must never break the wire: a throw is swallowed.
     *
     * @param listener called on the sending or receiving thread, for every frame
     */
    public static void listen(final Consumer<Frame> listener) {
        LISTENERS.add(listener);
    }

    public static void sent(final String peer, final studio.phaseshift.metatron.furi.fURI vid) {
        SENT.incrementAndGet();
        publish(new Frame(peer, vid, false));
    }

    public static void received(final String peer, final studio.phaseshift.metatron.furi.fURI vid) {
        RECEIVED.incrementAndGet();
        publish(new Frame(peer, vid, true));
    }

    public static void roundTrip(final long millis) {
        ROUND_TRIPS.incrementAndGet();
        LAST_ROUND_TRIP_MS.set(millis);
    }

    public static void timeout() {
        TIMEOUTS.incrementAndGet();
    }

    public static long sent() {
        return SENT.get();
    }

    public static long received() {
        return RECEIVED.get();
    }

    public static long roundTrips() {
        return ROUND_TRIPS.get();
    }

    public static long timeouts() {
        return TIMEOUTS.get();
    }

    public static long lastRoundTripMs() {
        return LAST_ROUND_TRIP_MS.get();
    }

    /**
     * Zero the ledger — the counters describe a window that starts now.
     */
    public static void reset() {
        SENT.set(0);
        RECEIVED.set(0);
        ROUND_TRIPS.set(0);
        TIMEOUTS.set(0);
        LAST_ROUND_TRIP_MS.set(-1L);
    }

    private static void publish(final Frame frame) {
        // the no-listener case is every frame of a VM that never watches: do not even build the frame
        if (LISTENERS.isEmpty())
            return;
        for (final Consumer<Frame> listener : LISTENERS) {
            try {
                listener.accept(frame);
            } catch (final Exception ignored) {
                // a debugging aid that can break the wire it watches is worse than no aid
            }
        }
    }
}
