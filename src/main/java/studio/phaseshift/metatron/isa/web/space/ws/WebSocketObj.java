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

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.Handshakedata;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.mach.type.Network;
import studio.phaseshift.metatron.isa.mach.type.thread.FutureObj;
import studio.phaseshift.metatron.isa.web.type.MIME;
import studio.phaseshift.metatron.util.CommonUtil;

import java.io.Closeable;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.BLOCK_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.VID_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MCode.code;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface WebSocketObj extends Rec, Closeable {

    record IO(MIME.MIMEType input, MIME.MIMEType output) {
        public static IO of(final Rec obj, final MIME.MIMEType defaultType) {
            final MIME.MIMEType input = obj.has(IN) ? MIME.MIMEType.of(obj.at(IN).uriValue().toString(), defaultType) : defaultType;
            final MIME.MIMEType output = obj.has(OUT) ? MIME.MIMEType.of(obj.at(OUT).uriValue().toString(), defaultType) : defaultType;
            return new IO(input, output);
        }
    }

    default boolean state(final WebSocket conn) {
        return conn.isOpen();
    }

    void onOpen(final WebSocket conn, final Handshakedata handshake);

    void onClose(final WebSocket conn, final int code, final String reason, final boolean remote);

    void onMessage(final WebSocket conn, final Obj message);

    /**
     * Text-frame entry point: parse the raw frame through the configured input
     * serializer and delegate to {@link #onMessage(WebSocket, Obj)}.  Handlers that
     * need the raw string (e.g. schema-aware MCP argument parsing) override this.
     * <p>
     * An uncaught fail is propagated through the handler's {@code ON_MESSAGE} inst by
     * the processor (a fail lhs skips a non-catch inst's function), so it is caught here
     * first — otherwise the handler never runs and no reply is sent.
     */
    default void onMessage(final WebSocket conn, final String message) {
        final Obj obj = this.getIO().input().fromBytes(message);
        this.onMessage(conn, obj.isFail() ? obj.asFail().caughtTransient() : obj);
    }

    void onError(final WebSocket conn, final Exception ex);

    WebSocket getWebSocket();

    void setWebSocket(final WebSocket socket);

    default fURI getThisVID() {
        return null == this.getWebSocket() || this.getWebSocket().isClosed() ?
                null : this.getWebSocket().getAttachment();
    }

    /**
     * The other end of this socket, as a {@code ws://host:port} authority. It is BUILT from the remote's host and
     * port, never parsed out of the socket address's {@code toString()}: that is {@code hostname/ip:port}, which a
     * fURI reads as a path, and for an IPv6 peer ({@code /[0:0:0:0:0:0:0:1]:45960}) is no uri at all, so every
     * frame from such a peer failed in {@link #peerName()} before {@code on_message} could run.
     */
    default fURI getOtherVID() {
        if (null == this.getWebSocket() || this.getWebSocket().isClosed() || !this.getWebSocket().isOpen())
            return null;
        final InetSocketAddress remote = this.getWebSocket().getRemoteSocketAddress();
        return null == remote ? null : f("").scheme("ws").host(fURI.hostOf(remote)).port(remote.getPort());
    }

    IO getIO();

    @Override
    default void close() {
        // the trips still in flight are answered before their slots go: a closed socket delivers no reply
        this.abandon("closed");
        WAITERS.remove(this);       // identity-keyed, so released with the socket
        ROUTED.remove(this);
        try {
            if (null != this.getWebSocket() && !this.getWebSocket().isClosed()) {
                this.logger().debug("closing %s", this.vidOrTid());
                this.getWebSocket().close();
            }
        } catch (final Exception e) {
            this.logger().error("error closing websocket: %s", e);
        }
    }

    default void send(final Obj message) {
        try {
            if (null == this.getWebSocket()) {
                if (BootLoader.TESTING)
                    return;
                return;
            }
            final MIME.MIMEType outType = this.getIO().output();
            final ByteBuffer bytes = outType.serializer().outputBytes(message);
            if (outType.isText()) {
                // send as a websocket text frame so clients using onText listeners receive it
                final String outgoing = new String(bytes.array(), StandardCharsets.UTF_8);
                this.getWebSocket().send(outgoing);
                WireStats.sent(this.peerName(), message.vid());
                this.logger().status(DEBUG, "sent %s to %s", message, this.getWebSocket().getRemoteSocketAddress());
            } else {
                this.getWebSocket().send(bytes);
                WireStats.sent(this.peerName(), message.vid());
            }
        } catch (final Exception e) {
            this.logger().status(ERROR, "error sending %s to %s: %s", message, this.getWebSocket().getRemoteSocketAddress(), e);
        }
    }

    /**
     * THE DEFAULT ROUND-TRIP DEADLINE, in milliseconds — the wait a {@link #sendRecv(Obj)} with no stated
     * deadline uses. It is finite on purpose: an unbounded inner wait outlives the future that is waiting on
     * it, leaving a parked worker thread holding an installed {@code ON_MESSAGE} handler for the life of the
     * process. A caller that needs longer states it.
     */
    long DEFAULT_SEND_RECV_MS = 30_000L;

    /**
     * A ROUND TRIP IN FLIGHT — the slot its reply lands in, keyed by the vid the request was sent under.
     */
    record Waiter(AtomicReference<Obj> obj, CountDownLatch latch) {
        static Waiter of() {
            return new Waiter(new AtomicReference<>(noobj()), new CountDownLatch(1));
        }

        void fill(final Obj reply) {
            this.obj.set(reply);
            this.latch.countDown();
        }
    }

    /**
     * THE OUTSTANDING ROUND TRIPS — per socket, keyed by REQUEST VID. Correlation is identity, so no tag
     * field, envelope or wrapper obj is added to the wire: an obj already carries its address, and {@code 3@a}
     * already serializes with it. A request is sent under its own vid when it has one and under a minted
     * RELATIVE vid when it does not, so the identifier lives on the receiving thread's variableStack and is
     * space-agnostic — it names nothing anywhere. The peer anchors its answer to the same vid (the answer to a
     * question belongs where the question was) and {@link #router()} hands the frame to this slot.
     * <p>
     * The map is keyed by socket first because a relative vid is only unique within a thread: two sockets could
     * mint the same one, and the arrival-order fallback must count THIS socket's outstanding trips, not every
     * socket's.
     */
    Map<WebSocketObj, Map<fURI, Waiter>> WAITERS = Collections.synchronizedMap(new IdentityHashMap<>());

    /**
     * The sockets whose {@link #router()} is installed — the handler is installed once, on first use, and never
     * swapped, so no two round trips can shadow each other's handler or restore out of order.
     */
    Map<WebSocketObj, Obj> ROUTED = Collections.synchronizedMap(new IdentityHashMap<>());

    /**
     * ANSWER EVERY ROUND TRIP IN FLIGHT with a fail: the connection under them is gone, so no reply can arrive, and
     * waiting out each deadline would only turn a known loss into an anonymous timeout. The slots themselves stay —
     * each trip removes its own as it returns.
     *
     * @param why what happened to the connection, for the fail's text
     */
    default void abandon(final String why) {
        final Map<fURI, Waiter> waiters = WAITERS.get(this);
        if (null == waiters || waiters.isEmpty())
            return;
        final String peer = this.peerName();
        this.logger().debug("abandoning %s round trip(s) on %s: %s", waiters.size(), peer, why);
        waiters.values().forEach(waiter -> waiter.fill(fail("connection to %s %s before its reply arrived", peer, why)));
    }

    /**
     * A NAME FOR THIS SOCKET, for a fail a human can act on: the host it dialed, else the remote it accepted
     * from, else the type address. {@code vidOrTid()} alone is useless — a client rec has no vid, so it answers
     * {@code /m/web/ws/ws_client} for every socket alike.
     */
    default String peerName() {
        final Obj host = this.atDirect(uri(HOST));
        if (!host.isNoObj())
            return host.toString();
        final fURI other = this.getOtherVID();
        return (null == other ? String.valueOf(this.vidOrTid()) : other.toString()) + "#" + System.identityHashCode(this);
    }

    /**
     * Send a message and answer the {@link FutureObj} that will hold the peer's reply — the send happens on a
     * virtual thread, so nothing blocks until something inspects the future. Uses
     * {@link #DEFAULT_SEND_RECV_MS}.
     *
     * @param message the Obj to send
     * @return a future holding the reply, or a {@code fail::T} when the deadline passed or the wait was
     * interrupted (never {@code noobj}: a lost reply must poison a reduction, not vanish from it)
     */
    default Obj sendRecv(final Obj message) {
        return this.sendRecv(message, DEFAULT_SEND_RECV_MS);
    }

    /**
     * Send a message and answer the {@link FutureObj} that will hold the peer's reply, waiting up to
     * {@code timeoutMs} for it.
     * <p>
     * The round trip runs on its own virtual thread: it registers a slot under the request's vid, sends, and
     * waits out the deadline while {@link #router()} fills the slot from the inbound frame. The deadline is the
     * inner wait's, and the future's own timeout is set from it, so the thread cannot outlive the waiter.
     *
     * @param message   the Obj to send
     * @param timeoutMs max wait in milliseconds; {@code <= 0} is read as {@link #DEFAULT_SEND_RECV_MS}
     * @return a future holding the reply, or a {@code fail::T} on deadline / interrupt
     */
    default Obj sendRecv(final Obj message, final long timeoutMs) {
        final long deadlineMs = 0L < timeoutMs ? timeoutMs : DEFAULT_SEND_RECV_MS;
        // THE KEY IS THE SOCKET OBJECT ITSELF, by identity: a client rec carries no vid, so vidOrTid() is the
        // TYPE address -- the same value for every ws_client -- and keying by it collapses every socket onto one
        // waiter map and one ROUTED entry (the first client installs the router; later ones install nothing and
        // their replies reach no waiter). Rec equality would collide too, hence identity.
        final String peer = this.peerName();
        // IDENTITY IS THE CORRELATION: an addressed request needs no tag -- it already is one. A request with
        // no vid is minted one for the trip and the reply is handed back unanchored, so a caller sees the value
        // it would have seen, never this transport's bookkeeping.
        final boolean minted = !message.hasVID();
        // a RELATIVE vid: it resolves on the receiving thread's variableStack rather than in anyone's space,
        // and the collision retry reads this thread's stack -- the only place it could clash
        final fURI tag = minted ? CommonUtil.mintShortUUID(null, true) : message.vid();
        final Obj payload = minted ? this.tagged(message, tag) : message;
        this.router();
        final Waiter waiter = Waiter.of();
        final Map<fURI, Waiter> waiters = WAITERS.computeIfAbsent(this, k -> new ConcurrentHashMap<>());
        waiters.put(tag, waiter);
        this.logger().debug("awaiting: tag=%s minted=%s deadline=%s outstanding=%s on %s", tag, minted, deadlineMs, waiters.size(), peer);
        return FutureObj.async(instLambda(outbound -> {
                    try {
                        if (!this.awaitOpen(deadlineMs)) {
                            this.logger().warn("not connected: tag=%s on %s -- nothing sent (%s)", tag, peer, this.socketState());
                            return fail("socket to %s is not connected", peer);
                        }
                        this.send(outbound);
                        // send() swallows its own failure, so the socket is re-checked: a frame that never left must
                        // fail the trip now rather than at the far end of the deadline
                        final WebSocket live = this.getWebSocket();
                        if (null == live || !live.isOpen()) {
                            this.logger().warn("unable to send: tag=%s on %s -- socket closed mid-send", tag, peer);
                            return fail("send to %s failed: socket closed", peer);
                        }
                        this.logger().debug("sent tag=%s on %s, awaiting latch", tag, peer);
                        final long startedMs = System.currentTimeMillis();
                        if (!waiter.latch().await(deadlineMs, TimeUnit.MILLISECONDS)) {
                            this.logger().warn("connection timeout: tag=%s on %s after %s ms (outstanding=%s)", tag, peer, deadlineMs, waiters.size());
                            WireTrace.exchange(peer, tag, message, null, startedMs, deadlineMs, WireTrace.Outcome.TIMEOUT);
                            return fail("no reply from %s within %s ms", peer, deadlineMs);
                        }
                        final long elapsedMs = System.currentTimeMillis() - startedMs;
                        // ONE ROW PER ROUND TRIP, recorded where both halves exist: the request that was sent and
                        // the reply the latch released (fill() sets the obj before it counts down)
                        WireTrace.exchange(peer, tag, message, waiter.obj().get(), startedMs, elapsedMs, WireTrace.Outcome.OK);
                        this.logger().debug("filled tag=%s on %s", tag, peer);
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return fail("interrupted awaiting a reply from %s", peer);
                    } finally {
                        waiters.remove(tag);        // a slot outlives neither its deadline nor its reply
                    }
                    final Obj reply = waiter.obj().get();
                    return minted ? reply.clone().selfVID(null) : reply;
                }), payload)
                // the future gives up AFTER the inner wait, so the deadline is reported as the fail the round
                // trip raised rather than as a bare inspection timeout
                .timeout((int) Math.min(Integer.MAX_VALUE, deadlineMs + DEFAULT_SEND_RECV_SLACK_MS));
    }

    /**
     * THE TAG RIDES THE QUESTION — a request is not labelled, it is asked to label its own answer. Appending
     * {@code vid(<tag>)} (an endo {@code A → A}) to the shipped chain means the peer anchors its result by
     * simply evaluating the code it was handed: {@code … .sum().vid(<a3f91c2b>)} answers {@code 14@a3f91c2b}.
     * So correlation needs no envelope, no wire field and no cooperation from the far side — a peer that knows
     * nothing about round-trip matching tags correctly by accident, because tagging IS the computation.
     * <p>
     * A message that is not code has no chain to append to, so it carries the vid on itself instead.
     */
    default Obj tagged(final Obj message, final fURI tag) {
        final Inst anchor = instB(VID_INST_TID, lst(uri(tag)));
        // DESCEND THE BLOCKS FIRST. A payload may arrive quoted -- a user's `|` holds a chain back for the far
        // side -- and the peer's deserialization evaluates what it is sent, spending a quote. (A network read or
        // write arrives unquoted: the client's own from/to/eval hand their message over raw, start(...).to(...) and
        // all, so the anchor simply ends that chain.) The anchor must ride the chain the PEER evaluates, so it
        // goes on the INNERMOST code and is re-quoted to the same depth; appended outside, it would tag the quote
        // rather than the chain the peer runs.
        int blocks = 0;
        Obj inner = message;
        while (inner.isObjInst()
                && BLOCK_INST_TID.equals(inner.asInst().tid().basePath())
                && 1 == inner.asInst().args().count()) {
            inner = inner.asInst().arg(0);
            blocks++;
        }
        Obj tagged;
        if (inner.isCode()) {
            final List<Inst> insts = new ArrayList<>(inner.asCode().insts());
            insts.add(anchor);
            tagged = code(insts);
        } else if (inner.isObjInst())
            // a single instruction is a one-element chain: compose rather than anchor, so the vid lands on
            // what the instruction ANSWERS
            tagged = code(List.of(inner.asInst(), anchor));
        else {
            // a bare value has nothing to evaluate, so it carries the vid itself
            this.logger().debug("tagging a value message %s (tid %s) -- not a chain", inner, inner.tid());
            tagged = inner.clone().selfVID(tag);
        }
        for (int i = 0; i < blocks; i++)
            tagged = instB(BLOCK_INST_TID, lst(tagged));
        this.logger().debug("tagged %s blocks deep with %s", blocks, tag);
        return tagged;
    }

    /**
     * WAIT FOR THE HANDSHAKE, bounded. The send runs on a virtual thread, so it can outrun the dial, and a
     * {@code send} on a half-open socket raises {@code WebsocketNotConnectedException} — which
     * {@link #send(Obj)} catches and logs, leaving the round trip to wait out its whole deadline for a reply
     * that was never asked for. Answering "not connected" must be immediate, so the socket gets a bounded
     * window to come up and no more.
     *
     * @return true when the socket is open and writable
     */
    default boolean awaitOpen(final long deadlineMs) {
        final long endTime = System.currentTimeMillis() + Math.min(deadlineMs, DEFAULT_OPEN_WAIT_MS);
        // A LINK THAT IS DOWN IS ASKED TO COME BACK — NOW. A request is the moment reach matters, so it does not
        // wait out the supervisor's backoff: a dialed connection its owner did not hang up redials on demand, within
        // the link's own connect timeout, and the request goes out on the connection that answers. A redial that is
        // refused leaves the socket closed, and the loop below says so at once.
        if (this.getWebSocket() instanceof Network.Link link && !link.connected() && !link.hungUp())
            link.redial();
        while (System.currentTimeMillis() < endTime) {
            final WebSocket socket = this.getWebSocket();
            if (null != socket && socket.isOpen())
                return true;
            if (null != socket && socket.isClosed())
                return false;       // closed for good: waiting cannot help
            try {
                Thread.sleep(10L);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        final WebSocket socket = this.getWebSocket();
        return null != socket && socket.isOpen();
    }

    /**
     * How long a round trip waits for its socket's handshake before calling it unreachable.
     */
    long DEFAULT_OPEN_WAIT_MS = 5_000L;

    /**
     * What this socket says about itself — identity, class and ready state. {@code isOpen()} and the
     * connection {@code send} actually writes to can disagree, so a trip that fails to send prints both.
     */
    default String socketState() {
        final WebSocket socket = this.getWebSocket();
        if (null == socket)
            return "socket=null";
        return String.format("socket=%s#%s open=%s closing=%s closed=%s ready=%s",
                socket.getClass().getSimpleName(), System.identityHashCode(socket),
                socket.isOpen(), socket.isClosing(), socket.isClosed(), socket.getReadyState());
    }

    /**
     * THE ROUTER — installed once per socket and never swapped: an inbound frame is handed to the round trip
     * whose vid it carries, and anything else is passed to the handler that was already there (an unsolicited
     * push must still reach the socket's own {@code ON_MESSAGE}).
     * <p>
     * The one-waiter fallback is what lets an un-anchored peer keep working: a reply that carries no vid, with
     * exactly one trip outstanding, can only be that trip's -- the same arrival-order match the one-shot
     * handler used to make, now narrowed to the case where it cannot be wrong. With several outstanding and no
     * vid to tell them apart, the frame is NOT guessed at; it falls through, and the trips take their deadlines.
     */
    default void router() {
        if (ROUTED.containsKey(this))
            return;
        synchronized (ROUTED) {
            if (ROUTED.containsKey(this))
                return;
            // atDirect: on_message is typically an INST, and at() auto-resolves -- reading it through at()
            // applies the handler instead of handing it back
            final Obj previous = this.atDirect(uri(ON_MESSAGE));
            ROUTED.put(this, previous);
            this.logger().debug("WIRE router installed on %s (previous=%s)", this.peerName(), previous.isNoObj() ? "none" : previous.tid());
            this.at(uri(ON_MESSAGE), instLambda((lhs, inst) -> {
                final Map<fURI, Waiter> waiters = WAITERS.computeIfAbsent(this, k -> new ConcurrentHashMap<>());
                final fURI vid = lhs.vid();
                Waiter waiter = null == vid ? null : waiters.remove(vid);
                final boolean byVid = null != waiter;
                if (null == waiter && null == vid && 1 == waiters.size())
                    waiter = waiters.remove(waiters.keySet().iterator().next());
                this.logger().debug("WIRE inbound vid=%s byVid=%s matched=%s outstanding=%s on %s", vid, byVid, null != waiter, waiters.size(), this.peerName());
                if (null != waiter) {
                    waiter.fill(lhs);
                    return lhs;
                }
                // a frame no round trip was waiting for: an unsolicited push, and the only inbound move this VM
                // has no other way to see (its arrival is a socket event, not a write)
                WireTrace.push(this.peerName(), lhs);
                this.logger().debug("WIRE unmatched frame passed to previous handler on %s", this.peerName());
                return previous.isNoObj() ? lhs : previous.apply(lhs);
            }), MUTABLE);
        }
    }

    /**
     * The margin by which a future's inspection deadline exceeds the round trip's own, so the inner wait is
     * always the one that expires first and the caller sees the round trip's {@code fail::T} rather than a
     * timeout raised over its head.
     */
    long DEFAULT_SEND_RECV_SLACK_MS = 2_000L;
}
