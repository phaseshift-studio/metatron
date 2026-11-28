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

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.Handshakedata;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Fail;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.impl.MFail;
import studio.phaseshift.metatron.isa.m.mach.machInstSet;
import studio.phaseshift.metatron.furi.QProc;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.Network;
import studio.phaseshift.metatron.isa.m.mach.type.analyzer.Analyzer;
import studio.phaseshift.metatron.isa.m.mach.type.network.Listener;
import studio.phaseshift.metatron.isa.m.mach.type.thread.FutureObj;
import studio.phaseshift.metatron.isa.m.web.type.MIME;
import studio.phaseshift.metatron.util.CommonUtil;

import java.io.Closeable;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.m.mach.machInstSet.REPLY_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;

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

    /**
     * THE SERVER THIS SESSION ARRIVED ON, when it arrived on one — the socket whose metrics its frames are counted
     * in. A session is held by its socket, so the server is a JAVA reference and never a field of the rec: a client
     * session carries none, and its frames are the network's wire, counted there.
     */
    default Listener listener() {
        return null;
    }

    /**
     * THE WIRE ADDRESS this session's frames travel at — the route it took, under the socket's origin
     * ({@code ws://host:port/mtron}). null for a client, which serves no route.
     */
    default fURI wire() {
        return null;
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
        // A FAIL LEAVING THIS MACHINE IS BEING REPORTED, so it gets the address it did not get at minting (see
        // MFail.transientFail / MFail.reported). An addressed one — a trip's answer, a re-homed reply — keeps it.
        final Obj frame = MFail.reported(message);
        try {
            if (null == this.getWebSocket()) {
                return;
            }
            final MIME.MIMEType outType = this.getIO().output();
            final ByteBuffer bytes = outType.serializer().outputBytes(frame);
            if (outType.isText()) {
                // send as a websocket text frame so clients using onText listeners receive it
                final String outgoing = new String(bytes.array(), StandardCharsets.UTF_8);
                this.getWebSocket().send(outgoing);
                this.logger().status(DEBUG, "sent %s to %s", frame, this.getWebSocket().getRemoteSocketAddress());
            } else {
                this.getWebSocket().send(bytes);
            }
            // THE WIRE IS COUNTED WHERE IT IS: below the q-procs, which see only the writes this machine addresses at a
            // peer — never a reply, never an inbound push — so a ?subq could not feed these meters
            Analyzer.Helper.incr(Machine.current().network().analyzer(), Network.WIRE_SENT, 1L);
            // ... AND A SERVER COUNTS ITS OWN REPLY AS A WRITE AT THE ROUTE IT SERVED, in the socket's own analyzer:
            // the network's wire meters say what crossed this vm's sockets, the listener's say what one server did
            final Listener served = this.listener();
            if (null != served)
                QProc.Layer.write(served, this.wire(), frame, () -> frame);
        } catch (final Exception e) {
            this.logger().status(ERROR, "error sending %s to %s: %s", frame, this.getWebSocket().getRemoteSocketAddress(), e);
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
     * A ROUND TRIP IN FLIGHT — the slot its reply lands in, keyed by the vid the request was sent under, and whether an
     * observation made it ({@link Analyzer#OBSERVING}, read where the trip is made): its reply is then the observer's work.
     */
    record Waiter(AtomicReference<Obj> obj, CountDownLatch latch, boolean observing) {
        static Waiter of() {
            return new Waiter(new AtomicReference<>(noobj()), new CountDownLatch(1), Analyzer.OBSERVING.get());
        }

        void fill(final Obj reply) {
            this.obj.set(reply);
            this.latch.countDown();
        }
    }

    /**
     * THE OUTSTANDING ROUND TRIPS — per socket, keyed by REQUEST VID. Correlation is identity, so no tag
     * field, envelope or wrapper obj is added to the wire: an obj already carries its address, and {@code 3@a}
     * already serializes with it. A request is sent under its own vid when it has one and under a minted tag
     * when it does not: a hex literal ({@code 0xa3f91c2b}) that the peer appends to its answer's vid as the last
     * segment ({@code g:V/1/0xa3f91c2b}, or the tag alone for an answer with no vid). A client reads every frame with
     * {@code Obj.Helper.SELF_VID} bound, so nothing in a reply is stored, {@link #router()} hands the frame to this
     * slot by its tag, and the air lock ({@link #untagged}) retracts the tag before the caller sees the answer.
     * <p>
     * The map is keyed by socket first because a relative vid is only unique within a thread: two sockets could
     * mint the same one, and the arrival-order fallback must count THIS socket's outstanding trips, not every
     * socket's.
     */
    Map<WebSocketObj, Map<fURI, Waiter>> WAITERS = Collections.synchronizedMap(new IdentityHashMap<>());

    /**
     * WHETHER AN OBSERVER'S ROUND TRIP IS OUTSTANDING ON {@code socket}. A frame arrives on the socket library's own
     * thread, which no executor marked, and which frame it is is known only once it is read: while such a trip is
     * outstanding, every frame is counted, read and handed on under {@code Analyzer.REPLY}, so the trip's reply never
     * announces the trip that asked for it. A frame that is no reply, arriving in that window, is announced with the
     * next change, and what it starts runs unmarked. A reply that arrives after its trip gave up (its deadline) is no
     * longer the observer's, and announces.
     */
    static boolean observed(final WebSocketObj socket) {
        final Map<fURI, Waiter> waiters = WAITERS.get(socket);
        return null != waiters && waiters.values().stream().anyMatch(Waiter::observing);
    }

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
        // no vid is minted a tag for the trip, and the reply comes back as the far side addressed it with the tag
        // retracted, so a caller sees the answer it would have seen, never this transport's bookkeeping.
        final boolean minted = !message.hasVID();
        // a HEX segment, 0x and the minted id, which the air lock recognizes as the trip on the way back (untagged)
        final fURI tag = minted ? CommonUtil.mintShortUUID(null, true, true) : message.vid();
        final Obj payload = minted ? this.tagged(message, tag) : message;
        this.router();
        final Waiter waiter = Waiter.of();
        final Map<fURI, Waiter> waiters = WAITERS.computeIfAbsent(this, k -> new ConcurrentHashMap<>());
        waiters.put(tag, waiter);
        this.logger().debug("awaiting: tag=%s minted=%s deadline=%s outstanding=%s on %s", tag, minted, deadlineMs, waiters.size(), peer);
        // THE ROUND TRIP ANSWERS ANY COEFFICIENT (#{**}): instLambda's default range, #{*}, made a negative answer a range
        // fail while inst_rng is checked. The domain stays #{?}, so a counted VALUE message travels as one copy and its
        // count goes back on the answer here; a program's answer brings its count across the wire
        return FutureObj.async(instLambda(ALL.maybe(), ALL.any(), outbound -> {
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
                        final long startedNanos = System.nanoTime();
                        if (!waiter.latch().await(deadlineMs, TimeUnit.MILLISECONDS)) {
                            this.logger().warn("connection timeout: tag=%s on %s after %s ms (outstanding=%s)", tag, peer, deadlineMs, waiters.size());
                            WireTrace.exchange(peer, tag, message, null, startedMs, deadlineMs, WireTrace.Outcome.TIMEOUT);
                            return fail("no reply from %s within %s ms", peer, deadlineMs);
                        }
                        final long elapsedNanos = System.nanoTime() - startedNanos;
                        // ONE ROW PER ROUND TRIP, recorded where both halves exist: the request that was sent and
                        // the reply the latch released (fill() sets the obj before it counts down)
                        WireTrace.exchange(peer, tag, message, waiter.obj().get(), startedMs,
                                TimeUnit.NANOSECONDS.toMillis(elapsedNanos), WireTrace.Outcome.OK);
                        this.logger().debug("filled tag=%s on %s", tag, peer);
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return fail("interrupted awaiting a reply from %s", peer);
                    } finally {
                        waiters.remove(tag);        // a slot outlives neither its deadline nor its reply
                    }
                    final Obj reply = waiter.obj().get();
                    // a fail keeps the address its stack gave it: stripping the vid is for a VALUE whose vid is
                    // only the trip's minted tag, and a fail's address is never that (see router())
                    return minted && !reply.isFail() && !reply.isCaughtFail() ? untagged(reply) : reply;
                }), payload)
                // the future gives up AFTER the inner wait, so the deadline is reported as the fail the round
                // trip raised rather than as a bare inspection timeout
                .timeout((int) Math.min(Integer.MAX_VALUE, deadlineMs + DEFAULT_SEND_RECV_SLACK_MS));
    }

    /**
     * THE TAG RIDES THE QUESTION — a request is not labelled, it is asked to label its own answer. The shipped
     * program is wrapped as {@code reply(<program>, <tag>)}, so the peer addresses its result by simply evaluating the
     * code it was handed: {@code reply(….sum(), <0xa3f91c2b>)} answers {@code 14@0xa3f91c2b}, and an answer with an
     * address of its own keeps it, the tag riding as its last segment: {@code g:V/1/0xa3f91c2b}. So correlation needs
     * no envelope and no wire field, and the tag never replaces an answer's vid ({@link #untagged} strips it).
     * <p>
     * A message that is not code has no chain to wrap, so it carries the tag as its vid instead.
     */
    default Obj tagged(final Obj message, final fURI tag) {
        return message.isCall() ? instB(REPLY_INST_TID, lst(message, uri(tag))) : message.clone().selfVID(tag);
    }

    /**
     * THE TRIP THAT AN INBOUND FRAME ANSWERS: its vid's last segment when that is a hex literal ({@code g:V/1/0xa3f91c2b},
     * or the bare {@code 0xa3f91c2b} of an answer with no vid), else the vid itself (an addressed request is correlated
     * by its own address, which the far side answers under). Null for a frame with no vid.
     */
    static fURI tripOf(final fURI vid) {
        return null == vid ? null : CommonUtil.isHex(vid.name()) ? f(vid.name()) : vid;
    }

    /**
     * THE VID A FRAME'S TRIP RIDES ON: its own, or, as no spelling puts a vid on a rel, the trip tag where a rel's lands,
     * on its last element (a=>b@<0x…> reads back with the vid on b). Only a hex tag is read off an element, where
     * {@link #untagged} takes it. Read off the frame alone, a rel answer matched its trip only while it was its socket's
     * one outstanding trip (2026-11-28).
     */
    static fURI vidOf(final Obj frame) {
        if (null != frame.vid() || frame.isNoObj() || !(frame.isRel() || frame.isObjs()))
            return frame.vid();
        // a rel's tag on its last element, and a bag's on its members, the first standing for all (reply tags each)
        final fURI found = vidOf(frame.isRel() ? frame.asRel().second() : frame.stream().findFirst().orElse(noobj()));
        return null != found && CommonUtil.isHex(found.name()) ? found : null;
    }

    /**
     * THE AIR LOCK: the trip's tag comes off and the answer's own vid stays. A minted trip's reply carries its tag as
     * the vid's last segment, a hex literal (Marko, 2026-11-22); retracting it hands the caller the answer as the far
     * side addressed it, {@code g:V/1} for a graph element and no vid for an answer that had none. A frame whose vid
     * has no hex segment carries only the trip, and leaves with no vid.
     */
    static Obj untagged(final Obj reply) {
        // A REL'S TAG LANDS ON ITS LAST ELEMENT: the peer writes the tagged answer a=>b@<0x…>, which reads back with the vid
        // on b, as no spelling puts one on a rel, so the trip's tag is taken off the end it landed on (Marko, 2026-11-27:
        // "you are getting back the send/recv tag vid ... its leaking")
        // ... AND A BAG'S ON EACH MEMBER, as reply tags a bag member by member: each is untagged as one answer is
        if (reply.isObjs() && null == reply.vid())
            return objs(reply.stream().map(WebSocketObj::untagged).toList());
        if (reply.isRel() && null == reply.vid()) {
            final Obj last = reply.asRel().second();
            return last.isRel() || (null != last.vid() && CommonUtil.isHex(last.vid().name()))
                    ? rel(reply.asRel().first(), untagged(last)).c(reply.c()) : reply;
        }
        final fURI vid = reply.vid();
        if (null == vid || !CommonUtil.isHex(vid.name()))
            return reply.clone().selfVID(null);
        final fURI own = vid.retract(1);
        return reply.clone().selfVID(own.isEmpty() ? null : own);
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
            // THE ROUTER TAKES ANY COEFFICIENT (#{**}), as mtron_on_message does. Declared #{?}, instLambda's default,
            // a reply with a coefficient did not match it, so while inst_dom is checked the processor compressed the
            // coefficient away before the body ran: {2}2@<0x…> filled its waiter as 2@<0x…>, and only the lambda's own
            // return regained the {2}, which no caller reads. A bulked member lost its count on every remote lane that
            // answered it alone (2026-11-22: a peer re-planning {{2}1,2} dealt {2}1 to one leaf, and {{2}2,3} came home
            // as {2,3}). Declared #{*}, zero to unbounded, a negative point did not match it, checked or not:
            // {-5}5@<0x…> filled its waiter as 5@<0x…>.
            this.at(uri(ON_MESSAGE), instLambda(ALL.any(), ALL.any(), (lhs, inst) -> {
                final Map<fURI, Waiter> waiters = WAITERS.computeIfAbsent(this, k -> new ConcurrentHashMap<>());
                final fURI vid = vidOf(lhs);
                final fURI trip = tripOf(vid);
                Waiter waiter = null == trip ? null : waiters.remove(trip);
                // an addressed request whose own address happens to end in a hex segment is correlated by the whole vid
                if (null == waiter && null != vid && !vid.equals(trip))
                    waiter = waiters.remove(vid);
                final boolean byVid = null != waiter;
                if (null == waiter && null == vid && 1 == waiters.size())
                    waiter = waiters.remove(waiters.keySet().iterator().next());
                this.logger().debug("WIRE inbound vid=%s byVid=%s matched=%s outstanding=%s on %s", vid, byVid, null != waiter, waiters.size(), this.peerName());
                if (null != waiter) {
                    // A FAIL IS RE-HOMED, NOT RE-ADDRESSED. The vid the frame carries is the TRIP's tag — the
                    // address the transport used to find this slot — and it belongs to the connection, not to the
                    // failure. Handing it to the caller would make a transport tag the fail's identity, so it is
                    // stripped and the fail is thrown into THIS machine's fail stack, which stamps it with the next
                    // incr id (~/processor/fail/_?incrq, MFail.failStackPattern) — the address a locally raised fail gets.
                    if (lhs.isFail() || lhs.isCaughtFail()) {
                        // THE FACTORY IS THE FILER: fail(...) mints an UNCAUGHT fail and writes it at the machine's
                        // fail stack (~/processor/fail/_?incrq), so the fail the caller receives is addressed where THIS
                        // machine raises its fails — never at the tag the connection minted to find the slot, and
                        // never as a caught fail (which is what the wire carries, since an uncaught frame would skip
                        // the handler's function). The peer's message and cause chain ride through unchanged.
                        final Fail rehomed = MFail.fail(lhs.asFail().jvm());
                        this.logger().debug("WIRE inbound fail re-homed at %s on %s", rehomed.vid(), this.peerName());
                        waiter.fill(rehomed);
                        return rehomed;
                    }
                    // AN ABSENT ANSWER ARRIVES AS THE MARK, NOT AS noobj. Nothing can carry an address (noobj is the
                    // coefficient-zero singleton, which no writer renders with a vid), so a trip whose answer was
                    // nothing is answered with the mark that stands for it — see machInstSet#NOOBJ_SENTINEL. The
                    // slot is filled with what the peer actually answered, never with the wire's spelling of it.
                    waiter.fill(machInstSet.isNoobjSentinel(lhs) ? noobj() : lhs);
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
