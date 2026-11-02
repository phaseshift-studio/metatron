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

package studio.phaseshift.metatron.isa.m.web.space.ws;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * THE WIRE'S HISTORY — a bounded, ordered window of the exchanges that crossed this VM's peers, one lane per peer.
 * <p>
 * {@link WireStats} answers "how much"; this answers "what". The unit is the {@link Exchange}: one
 * {@code sendRecv} round trip (a request, the reply it was answered with, and how long that took), or a frame that
 * arrived with no round trip waiting for it (a push). Frames are deliberately NOT the unit: a frame list reads as
 * protocol noise, while an exchange is what a human means by "a message going back and forth".
 * <p>
 * <b>It is lossy and private, on purpose.</b> A ring keeps the newest {@link #MAX_EXCHANGES} exchanges
 * ({@link #MAX_PER_PEER} per lane) and forgets the rest, counting them in {@link Snapshot#dropped()}. That is what
 * makes it affordable on the wire path — a store write per frame would put the hottest path in the VM through the
 * memory index — and it is why the durable record, when one is wanted, is a space the caller writes, not this.
 * <p>
 * <b>Peer, not socket.</b> A lane is keyed by the peer a frame was addressed to (see {@link #laneKey(String)}),
 * because a dialed peer may be a fresh socket per message and would otherwise shatter into hundreds of lanes.
 * <p>
 * <b>Detail is a window within the window.</b> Only the newest {@link #DETAIL_DEPTH} exchanges keep their request
 * and reply as objs; older ones keep only their rendered text. Objs pin a peer's frame graph alive, so a trace that
 * kept every one of them would be a memory leak wearing a debugging aid's clothes.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class WireTrace {

    /**
     * how many exchanges the global window keeps
     */
    public static final int MAX_EXCHANGES = 500;
    /**
     * how many exchanges a single peer's lane keeps
     */
    public static final int MAX_PER_PEER = 100;
    /**
     * how many DISTINCT PEERS the window remembers. Rows are bounded per lane, but lanes themselves are not: a
     * long session dialing short-lived peers (or a listener accepting sockets from ephemeral ports) would mint a
     * lane per peer and keep it forever. The coldest lane is dropped when a new one would exceed this.
     */
    public static final int MAX_LANES = 32;
    /**
     * how many of the newest exchanges keep their request/reply as objs (see the class note)
     */
    public static final int DETAIL_DEPTH = 25;
    /**
     * the rendered width of a request or reply shown in a row
     */
    public static final int MAX_RENDER_CHARS = 200;
    /**
     * an exchange whose rendered request or reply is wider than this keeps no detail objs: the text already says
     * the shape, and the obj would pin something enormous
     */
    public static final int MAX_DETAIL_CHARS = 1024;

    /**
     * how an exchange ended.
     */
    public enum Outcome {
        /**
         * a request and the reply it was answered with
         */
        OK,
        /**
         * a request whose deadline passed with no reply (the reply text says so)
         */
        TIMEOUT,
        /**
         * a frame that arrived with no round trip waiting for it — an unsolicited push
         */
        PUSH
    }

    /**
     * one exchange in the window.
     */
    public static final class Exchange {

        private final long seq;
        private final String peer;
        private final fURI tag;
        private final String request;
        private final String reply;
        private final long startedMs;
        private final long elapsedMs;
        private final Outcome outcome;
        private Obj requestDetail;
        private Obj replyDetail;

        private Exchange(final long seq, final String peer, final fURI tag, final String request, final String reply,
                         final long startedMs, final long elapsedMs, final Outcome outcome,
                         final Obj requestDetail, final Obj replyDetail) {
            this.seq = seq;
            this.peer = peer;
            this.tag = tag;
            this.request = request;
            this.reply = reply;
            this.startedMs = startedMs;
            this.elapsedMs = elapsedMs;
            this.outcome = outcome;
            this.requestDetail = requestDetail;
            this.replyDetail = replyDetail;
        }

        public long seq() {
            return this.seq;
        }

        public String peer() {
            return this.peer;
        }

        public fURI tag() {
            return this.tag;
        }

        public String request() {
            return this.request;
        }

        public String reply() {
            return this.reply;
        }

        public long startedMs() {
            return this.startedMs;
        }

        public long elapsedMs() {
            return this.elapsedMs;
        }

        public Outcome outcome() {
            return this.outcome;
        }

        /**
         * @return the request as an obj, or null once the exchange has left the detail window
         */
        public Obj requestDetail() {
            return this.requestDetail;
        }

        /**
         * @return the reply as an obj, or null once the exchange has left the detail window
         */
        public Obj replyDetail() {
            return this.replyDetail;
        }

        private void forgetDetail() {
            this.requestDetail = null;
            this.replyDetail = null;
        }
    }

    /**
     * one row of a lane, as a view renders it.
     */
    public record Row(long seq, String peer, String kind, String tag, String request, String reply,
                      long elapsedMs, boolean ok, long startedMs, boolean detail) {
    }

    /**
     * one peer's lane: its aggregates and its newest rows.
     */
    public record Lane(String peer, long exchanges, long timeouts, long lastLatencyMs, long lastActivityMs,
                       List<Row> rows) {
    }

    /**
     * the whole window at one instant: the counters ({@link WireStats}) plus every lane and its rows.
     */
    public record Snapshot(long sent, long received, long roundTrips, long timeouts, long dropped,
                           long lastLatencyMs, long exchanged, List<Lane> lanes) {
    }

    private record LaneState(ArrayDeque<Exchange> rows, long exchanges, long timeouts, long lastActivityMs) {
    }

    private static final Object LOCK = new Object();
    private static final ArrayDeque<Exchange> EXCHANGES = new ArrayDeque<>();
    private static final Map<String, LaneState> LANES = new LinkedHashMap<>();
    private static final ArrayDeque<Exchange> DETAILS = new ArrayDeque<>();
    private static final AtomicLong SEQ = new AtomicLong();
    private static final AtomicLong DROPPED = new AtomicLong();
    private static volatile boolean paused = false;

    private WireTrace() {
        // static only
    }

    /**
     * THE PEER A FRAME BELONGS TO. {@link WebSocketObj#peerName()} appends {@code #<identityHash>} when a socket
     * has no dialed host (a client rec carries no vid, so the name falls back to the remote address); that suffix
     * exists to tell two sockets apart in a log, and keeping it here would give every accepted socket of one peer
     * its own lane. So the hash is stripped and the address itself becomes the key.
     *
     * @param peer a socket's {@code peerName()}
     * @return the lane key
     */
    static String laneKey(final String peer) {
        if (null == peer)
            return "unknown";
        final int hash = peer.lastIndexOf('#');
        if (hash < 0 || hash == peer.length() - 1)
            return peer;
        final String suffix = peer.substring(hash + 1);
        return suffix.chars().allMatch(c -> Character.digit(c, 16) >= 0) ? peer.substring(0, hash) : peer;
    }

    /**
     * Render an obj for a row: one line, stripped of markup, capped at {@link #MAX_RENDER_CHARS}.
     *
     * @param obj the obj to render, may be null
     * @return the rendered text, never null
     */
    public static String render(final Obj obj) {
        if (null == obj || obj.isNoObj())
            return "";
        try {
            final String text = obj.toShortString().replace("\n", " ").replace("\r", " ").trim();
            return text.length() <= MAX_RENDER_CHARS ? text : text.substring(0, MAX_RENDER_CHARS - 1) + "…";
        } catch (final Exception e) {
            // a debugging aid must never break the wire it watches
            return "<unrenderable>";
        }
    }

    /**
     * Record a completed round trip.
     *
     * @param peer      the socket's peer name
     * @param tag       the vid the request was correlated by
     * @param request   the request sent
     * @param reply     the reply received, or null when none came
     * @param startedMs when the wait began (epoch ms)
     * @param elapsedMs how long it took (or the deadline, on a timeout)
     * @param outcome   how it ended
     */
    public static void exchange(final String peer, final fURI tag, final Obj request, final Obj reply,
                                final long startedMs, final long elapsedMs, final Outcome outcome) {
        // THE COUNTERS DESCRIBE THE WIRE, which never pauses: a frozen VIEW still reports the live truth. They are
        // incremented here, on the one path that records an exchange, so the window's rows and its counts can
        // never disagree — including for a caller that records one by hand (a test, a replayed trace).
        if (Outcome.OK == outcome)
            WireStats.roundTrip(elapsedMs);
        else if (Outcome.TIMEOUT == outcome)
            WireStats.timeout();
        if (paused)
            return;
        final String requestText = render(request);
        final String replyText = render(reply);
        final boolean detail = requestText.length() <= MAX_DETAIL_CHARS && replyText.length() <= MAX_DETAIL_CHARS;
        record(new Exchange(SEQ.getAndIncrement(), peer, tag, requestText, replyText, startedMs, elapsedMs, outcome,
                detail ? request : null, detail ? reply : null));
    }

    /**
     * Record a frame that arrived with no round trip waiting for it.
     *
     * @param peer  the socket's peer name
     * @param frame the frame's obj
     */
    public static void push(final String peer, final Obj frame) {
        if (paused)
            return;
        final String text = render(frame);
        record(new Exchange(SEQ.getAndIncrement(), peer, null == frame ? null : frame.vid(), "", text,
                System.currentTimeMillis(), 0L, Outcome.PUSH,
                text.length() <= MAX_DETAIL_CHARS ? frame : null, null));
    }

    /**
     * Append to the global window, the peer's lane, and the detail window.
     */
    private static void record(final Exchange exchange) {
        synchronized (LOCK) {
            EXCHANGES.addFirst(exchange);
            if (EXCHANGES.size() > MAX_EXCHANGES) {
                EXCHANGES.removeLast();
                DROPPED.incrementAndGet();
            }
            final String key = laneKey(exchange.peer());
            final LaneState previous = LANES.get(key);
            final ArrayDeque<Exchange> laneRows = null == previous ? new ArrayDeque<>() : previous.rows();
            laneRows.addFirst(exchange);
            if (laneRows.size() > MAX_PER_PEER)
                laneRows.removeLast();
            LANES.put(key, new LaneState(laneRows,
                    (null == previous ? 0 : previous.exchanges()) + 1,
                    (null == previous ? 0 : previous.timeouts()) + (Outcome.TIMEOUT == exchange.outcome() ? 1 : 0),
                    exchange.startedMs()));
            if (LANES.size() > MAX_LANES) {
                String coldest = null;
                long oldest = Long.MAX_VALUE;
                for (final Map.Entry<String, LaneState> lane : LANES.entrySet())
                    if (!lane.getKey().equals(key) && lane.getValue().lastActivityMs() < oldest) {
                        oldest = lane.getValue().lastActivityMs();
                        coldest = lane.getKey();
                    }
                if (null != coldest)
                    LANES.remove(coldest);
            }
            if (null != exchange.requestDetail() || null != exchange.replyDetail()) {
                DETAILS.addFirst(exchange);
                if (DETAILS.size() > DETAIL_DEPTH)
                    DETAILS.removeLast().forgetDetail();
            }
        }
    }

    /**
     * @return the whole window at this instant (counters from {@link WireStats})
     */
    public static Snapshot snapshot(final int rowsPerLane) {
        final List<Lane> lanes = new ArrayList<>();
        synchronized (LOCK) {
            for (final Map.Entry<String, LaneState> entry : LANES.entrySet()) {
                final LaneState state = entry.getValue();
                final List<Row> rows = new ArrayList<>(Math.min(rowsPerLane, state.rows().size()));
                for (final Exchange exchange : state.rows()) {
                    if (rows.size() >= rowsPerLane)
                        break;
                    rows.add(new Row(exchange.seq(), exchange.peer(), kindOf(exchange), tagOf(exchange),
                            exchange.request(), exchange.reply(), exchange.elapsedMs(),
                            Outcome.OK == exchange.outcome(), exchange.startedMs(),
                            null != exchange.requestDetail() || null != exchange.replyDetail()));
                }
                lanes.add(new Lane(entry.getKey(), state.exchanges(), state.timeouts(),
                        lastLatency(state), state.lastActivityMs(), rows));
            }
        }
        // the busiest peer leads the view: a lane is shown so a human can read what is happening NOW
        lanes.sort((a, b) -> Long.compare(b.lastActivityMs(), a.lastActivityMs()));
        return new Snapshot(WireStats.sent(), WireStats.received(), WireStats.roundTrips(), WireStats.timeouts(),
                DROPPED.get(), WireStats.lastRoundTripMs(), SEQ.get(), lanes);
    }

    private static long lastLatency(final LaneState state) {
        for (final Exchange exchange : state.rows()) {
            if (Outcome.OK == exchange.outcome())
                return exchange.elapsedMs();
        }
        return -1L;
    }

    private static String kindOf(final Exchange exchange) {
        return switch (exchange.outcome()) {
            case OK -> "send_recv";
            case TIMEOUT -> "timeout";
            case PUSH -> "push";
        };
    }

    private static String tagOf(final Exchange exchange) {
        return null == exchange.tag() ? "" : exchange.tag().toString();
    }

    /**
     * STOP OR RESUME CAPTURE. A paused trace keeps what it has and records nothing new — the view must hold still
     * while a human inspects it, even though the wire keeps moving.
     *
     * @param paused true to stop capturing
     */
    public static void pause(final boolean paused) {
        WireTrace.paused = paused;
    }

    public static boolean paused() {
        return paused;
    }

    /**
     * Forget every exchange (the counters in {@link WireStats} are theirs to reset).
     */
    public static void clear() {
        synchronized (LOCK) {
            EXCHANGES.clear();
            LANES.clear();
            DETAILS.clear();
            DROPPED.set(0);
            SEQ.set(0);
        }
    }

    /**
     * Forget ONE PEER's exchanges — its lane and every exchange the global and detail windows still hold for it.
     * The counters in {@link WireStats} are global and theirs to reset, so they are untouched: clearing a peer's
     * MESSAGES leaves the machine's totals alone (a cleared lane simply stops contributing to them).
     *
     * @param peer the peer's name, or a socket name carrying an identity-hash suffix (folded to its lane key)
     */
    public static void clear(final String peer) {
        final String key = laneKey(peer);
        synchronized (LOCK) {
            LANES.remove(key);
            EXCHANGES.removeIf(exchange -> key.equals(laneKey(exchange.peer())));
            DETAILS.removeIf(exchange -> key.equals(laneKey(exchange.peer())));
        }
    }
}
