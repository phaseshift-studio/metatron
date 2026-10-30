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

package studio.phaseshift.metatron.isa.iot.space.mqtt;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.mach.io.type.ObjSerializer;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.Memory;
import studio.phaseshift.metatron.isa.mach.type.Network;
import studio.phaseshift.metatron.isa.mach.type.network.Listener;
import studio.phaseshift.metatron.isa.mach.type.thread.CoreThread;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.isa.sys.type.ThreadExecutor;
import studio.phaseshift.metatron.isa.web.parser.ObjJSONSerializer;
import studio.phaseshift.metatron.util.MTronException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.SUBQ_PATTERN;
import static studio.phaseshift.metatron.furi.q.QCollection.SUBQ_PUB_TID;
import static studio.phaseshift.metatron.isa.iot.iotInstSet.IOT_ISA_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.LISTENER_TID;

/**
 * MQTT_SERVER — an mqtt broker as a machine's PRESENCE: a {@link Listener} on an {@code mqtt://host:port} socket. The
 * constructor serves (binds), as {@code ws_server}'s and {@code http_server}'s do. A PUBLISH is a write to the space
 * graph (its topic a leading-slash path), and a SUBSCRIBE registers a {@code ?subq} write-detector whose code hands the
 * {@code pub::[topic, payload]} — paired with the subscriber's vid — onto this server's queue; the dispatch thread
 * drains the queue and writes each delivery back to its session.
 * <p>
 * The broker's OWN state is the mqtt-reserved {@code $SYS} namespace — served not from the space but from this
 * server's own {@code SYS} rec (computable leaves as auto-resolving calls, derived on read; data leaves as plain
 * values), remapped by the server's {@code route} the same way an {@code http_server}'s routes its paths, and
 * dropped, not written, when a client publishes there.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class mqttServer extends Listener {

    public static final fURI MQTT_SERVER_TID = IOT_ISA_TID.extend("mqtt").extend("mqtt_server");

    /**
     * the address base a session's vid hangs off — one segment per connected client
     */
    private static final fURI SESSION_VIDS = f("/sys/mqtt/session");

    /**
     * the broker's own namespace — mqtt-reserved: its state is served from this server's SYS rec, never from the space
     */
    public static final fURI SYS_NAMESPACE = f("$SYS/broker");

    private static final Uri SYS_KEY = uri("SYS");
    private static final String DOLLAR_SYS_KEY = "$SYS";

    private static final String VERSION_TEXT = "mqtt 5.0 / metatron";

    private static final int CONNECT_TIMEOUT_MS = 5_000;

    private static final GraphittyLogger LOG = Graphitty.log(mqttServer.class);

    public static final Type MQTT_SERVER_TYPE = Type.Builder.build()
            .tid(LISTENER_TID)
            .vid(MQTT_SERVER_TID)
            .isaPredicate(rec(SYS_KEY.maybe().asUri(), rec(URI_TYPE, T(ALL))))
            .constructor(config -> mqttServer.of(config.asRec().apply().asRec()))
            .create();

    private final ServerSocket socket;
    private final CoreThread acceptor;
    /**
     * the machine that owns this socket — captured at construction, where {@code Machine.current()} is the root;
     * the component's {@code machine()} walk can resolve to the zero machine, which would write nowhere
     */
    private final Machine machine;
    /**
     * the payload serializer — how an mqtt payload becomes an {@code Obj} and back; json by default, overridable later
     */
    private final ObjSerializer<?> serializer = ObjJSONSerializer.simple();
    /**
     * the live sessions, by their sockets — held here, never at an address
     */
    private final Map<Socket, mqttSession> sessions = Collections.synchronizedMap(new IdentityHashMap<>());
    /**
     * the live sessions by their vids, so a delivery pair's {@code uri} resolves back to its session
     */
    private final Map<fURI, mqttSession> sessionsByVid = new ConcurrentHashMap<>();
    private final AtomicInteger sessionCounter = new AtomicInteger(0);
    /**
     * the moment the socket was bound — the root of the $SYS uptime
     */
    private final Instant started = Instant.now();
    /**
     * the $SYS data a !inst leaf derives: packets and messages, in and out
     */
    private final AtomicInteger packetsIn = new AtomicInteger(0);
    private final AtomicInteger packetsOut = new AtomicInteger(0);
    private final AtomicInteger messagesIn = new AtomicInteger(0);
    private final AtomicInteger messagesOut = new AtomicInteger(0);
    /**
     * the live $SYS subscribers, by session, with the filters they earned — re-pushed at the moments that change the state
     */
    private final Map<mqttSession, List<fURI>> sysSubs = Collections.synchronizedMap(new IdentityHashMap<>());
    /**
     * the pending deliveries as {@code lst[pub, uri]} pairs — held in this rec's map (under {@code queue}) so it is
     * metatron-readable; the dispatch thread drains it
     */
    private final MqttQueue queue = new MqttQueue();
    private final CoreThread dispatcher;

    private mqttServer(final fURI origin, final Network owner) throws IOException {
        super(origin, MQTT_SERVER_TID, owner);
        this.machine = Machine.current();
        this.socket = new ServerSocket();
        this.socket.setReuseAddress(true);
        this.socket.bind(address(origin));
        this.jvm().put(SYS_KEY, buildSys(this));
        this.acceptor = CoreThread.core(instLambda((_, _) -> {
            this.acceptLoop();
            return noobj();
        }));//, this.machine.vid().extend("network").extend("mqtt-server-" + origin));
        ThreadExecutor.instance().execute(this.acceptor);
        this.dispatcher = CoreThread.core(instLambda((lhs, inst) -> {
            this.dispatchLoop();
            return noobj();
        }));//, this.machine.vid().extend("network").extend("mqtt-server-" + origin + "-dispatcher"));
        ThreadExecutor.instance().execute(this.dispatcher);
        LOG.info("mqtt_server listening: %s", origin);
    }

    /**
     * the broker's own state rec — auto-resolving leaves ({@code !inst?…{…}}) derived on read, a plain value where the
     * state is data: the tree a $SYS subscriber hears
     */
    private static Rec buildSys(final mqttServer server) {
        final Rec sys = rec(
                uri("broker"), rec(
                        uri(VERSION), str(VERSION_TEXT),
                        uri("uptime"), sysCall("mqtt_uptime", () -> (int) (Instant.now().getEpochSecond() - server.started.getEpochSecond())),
                        uri(LOAD), rec(
                                uri("clients"), sysCall("mqtt_clients", server::sessionCount)),
                        uri(STORE), rec(
                                uri("messages"), sysCall("mqtt_pending", () -> server.queue.jvm().size()),
                                uri("queue"), server.queue),
                        uri("packets"), rec(
                                uri("received"), sysCall("mqtt_packets_rx", server::packetsReceived),
                                uri("sent"), sysCall("mqtt_packets_tx", server::packetsSent)),
                        uri("messages"), rec(
                                uri("received"), sysCall("mqtt_messages_rx", server::messagesReceived),
                                uri("sent"), sysCall("mqtt_messages_tx", server::messagesSent))));
        sys.jvm().putAll(server.at(SYS_KEY).orElse(rec()).jvm());
        return sys;
    }

    /**
     * An auto-resolving leaf — the statusOf shape, named: a call on read, never a stored stale value
     */
    private static Obj sysCall(final String name, final Supplier<Integer> value) {
        return auto_(instLambda(name, ALL.maybe(), INT_TID, (_, _) -> jnt(value.get()))).tryToInst();
    }

    private int packetsReceived() {
        return this.packetsIn.get();
    }

    private int packetsSent() {
        return this.packetsOut.get();
    }

    private int messagesReceived() {
        return this.messagesIn.get();
    }

    private int messagesSent() {
        return this.messagesOut.get();
    }

    /**
     * An mqtt_server from its configuration — {@code mqtt_server::[origin=><mqtt://0.0.0.0:1883>,route=>[...]]}: the
     * optional route table the broker serves, plus the broker's own {@code $SYS/broker} route added by the broker.
     */
    public static Listener of(final Rec config) {
        final Obj host = config.at(uri(ORIGIN));
        if (!host.isUri())
            throw MTronException.of("an mqtt_server serves a socket: host=><mqtt://host:port> required, got %s", host);
        final Obj routes = config.at(uri(ROUTE));
        return serve(host.uriValue(), routes.isRec() ? routes.asRec() : rec());
    }

    /**
     * SERVE through the socket at {@code host}, from the current machine: join the socket a machine at or above this
     * one already holds, or bind it here.
     */
    public static Listener serve(final fURI origin) {
        return serve(origin, rec());
    }

    /**
     * SERVE with a route table — the httpServer shape: the table lands on the level's listener entry, where
     * {@link Listener#route(fURI)} walks it live, and on this server's rec, where it is introspectable.
     */
    public static Listener serve(final fURI origin, final Rec routes) {
        final Network level = Machine.current().network();
        final Listener held = Network.Helper.listenerFor(origin);
        if (null != held)
            return held;
        final mqttServer server;
        try {
            server = new mqttServer(origin, level);
        } catch (final IOException e) {
            throw MTronException.of(e, "unable to bind mqtt server %s", origin);
        }
        final Rec table = withSysRoute(server, routes);
        if (!table.isEmpty())
            server.jvm().put(uri(ROUTE), table);
        Network.Helper.listen(level, server, table);
        return server;
    }

    /**
     * The broker's own {@code $SYS/broker} route is part of the table the broker serves — it remaps the reserved
     * namespace onto the server's SYS rec — added unless the config's table already claims the {@code $SYS} namespace.
     */
    private static Rec withSysRoute(final mqttServer server, final Rec routes) {
        final boolean claimsSys = routes.keys()
                .filter(Obj::isUri)
                .map(Obj::uriValue)
                .anyMatch(key -> "$SYS".equals(key.path().stream().filter(segment -> !segment.isEmpty()).findFirst().orElse("")));
        if (claimsSys)
            return routes;
        return routes.plus(rec(uri(SYS_NAMESPACE), server.sys()));
    }

    /**
     * the broker's own state rec — the target the $SYS route names and the tree served to a $SYS subscriber
     */
    public Rec sys() {
        return this.atDirect(SYS_KEY).asRec();
    }

    @Override
    protected void unbind() {
        try {
            this.socket.close();
        } catch (final IOException e) {
            LOG.debug("closing mqtt server socket: %s", e);
        }
        this.dispatcher.stop();
        final List<mqttSession> live;
        synchronized (this.sessions) {
            live = new ArrayList<>(this.sessions.values());
            this.sessions.clear();
        }
        this.sessionsByVid.clear();
        live.forEach(mqttSession::close);
    }

    private void acceptLoop() {
        while (!this.closed()) {
            final Socket conn;
            try {
                conn = this.socket.accept();
            } catch (final IOException e) {
                if (!this.closed())
                    LOG.debug("mqtt accept failed: %s", e.getMessage());
                continue;
            }
            this.handle(conn);
        }
    }

    private void handle(final Socket conn) {
        final Thread connect = new Thread(() -> this.connected(conn), "mqtt-connect");
        connect.setDaemon(true);
        connect.start();
    }

    /**
     * The first exchange a connection makes: CONNECT in, CONNACK out, then a session owns the socket's read loop.
     */
    private void connected(final Socket conn) {
        try {
            conn.setSoTimeout(CONNECT_TIMEOUT_MS);
            final MqttPacket first = MqttCodec.read(conn.getInputStream());
            if (!(first instanceof MqttPacket.Connect connect)) {
                LOG.debug("mqtt connection from %s dropped: first packet was %s", conn.getRemoteSocketAddress(), first);
                conn.close();
                return;
            }
            conn.setSoTimeout(0);
            MqttCodec.write(conn.getOutputStream(), new MqttPacket.ConnAck(false, 0));
            final fURI vid = SESSION_VIDS.extend(this.sessionCounter.incrementAndGet());
            final mqttSession session = new mqttSession(conn, connect, this, vid);
            this.sessions.put(conn, session);
            this.sessionsByVid.put(vid, session);
            session.start();
            this.pushSys(); // a session opening is a moment: the client count its $SYS watchers cover has moved
        } catch (final Exception e) {
            LOG.debug("mqtt connection from %s dropped: %s", conn.getRemoteSocketAddress(), e.getMessage());
            try {
                conn.close();
            } catch (final IOException ignored) {
                // already closing
            }
        }
    }

    /**
     * A session its socket is gone: take it out of both registries. Package-private, called by {@link mqttSession#close()}.
     */
    void retire(final mqttSession session) {
        this.sessions.remove(session.socket());
        this.sessionsByVid.remove(session.vid());
        synchronized (this.sysSubs) {
            this.sysSubs.remove(session);
        }
        this.pushSys(); // the remaining watchers see the count the retirement leaves behind
    }

    /**
     * The payload serializer an mqtt publish is read with and an mqtt delivery is written with.
     */
    ObjSerializer<?> serializer() {
        return this.serializer;
    }

    /**
     * THE PROJECTION'S WRITE HALF: an mqtt topic IS a metatron address, so a PUBLISH is a write to the space graph,
     * resolved by {@link Machine#write} to whichever space claims the address. Runs as the machine that owns this
     * socket, where a session thread otherwise stands in no machine.
     */
    void publish(final fURI topic, final Obj obj) {
        // the $SYS namespace belongs to the broker — a client publish there is counted, never served
        if (underSys(topic)) {
            LOG.debug("mqtt $SYS publish dropped: %s is the broker's own namespace", topic);
            return;
        }
        this.messagesIn.incrementAndGet();
        this.machine.memory().write(topic, obj);
        this.pushSys();
    }

    /**
     * packet counters the $SYS leaves derive — a session's read and write each pass through one
     */
    void packetIn() {
        this.packetsIn.incrementAndGet();
    }

    void packetOut() {
        this.packetsOut.incrementAndGet();
    }

    /**
     * SUBSCRIBE — register a client's topic filter, or refuse it. The space covering the address must declare {@code
     * subq}: only such a space fans a write out to its subscribers, so a filter over a space without it answers
     * {@code 0x80} (failure) in the SUBACK rather than a silent no-op. The subscription's {@code code} hands the
     * {@code pub::[topic, payload]} it is fired with — paired with the subscriber's vid — onto this server's queue; the
     * dispatch thread writes it back. Runs as the machine that owns this socket.
     *
     * @return the granted qos (0 for now), or 0x80 when the address is not subscribable
     */
    int subscribe(final fURI filter, final mqttSession session) {
        // the broker's own namespace never touches the space — a $SYS filter is served from this rec
        if (underSys(filter))
            return this.subscribeSys(filter, session);
        final Machine current = Machine.current();
        Machine.current(this.machine);
        try {
            final fURI topic = filter;
            final Memory memory = this.machine.memory();
            // only a space that DECLARES subq fans a write out — a filter the machine does not back, or one over a
            // space without it, is refused, not mutated
            if (!topic.equals(ALL) && (!memory.hasSpaceFor(topic) || !memory.getSpaceFor(topic).hasQ(SUBQ_PATTERN)))
                return 0x80;
            // the subscription's code is a TYPED instruction (pub in, noobj out) and nothing else — untyped code
            // under full TypeCheck can be tested as a predicate, and only a signature, not an execution, may pass.
            // it carries the subscriber's vid as its argument, pairs a fired pub with it, and hands the [pub, uri]
            // obs to the queue; the dispatch thread drains the queue and writes each pub back to its session
            final Obj code = instC(MQTT_SERVER_TID.extend("deliver").dom(LST_TID).rng(NOOBJ_TID),
                    lst(uri(session.vid())),
                    (lhs, inst) -> {
                        this.queue.offer(objs(lhs, inst.arg(0)));
                        return noobj();
                    });
            if (filter.equals(ALL)) {
                this.machine.memory().spaces()
                        .elements()
                        .map(Rel::second)
                        .filter(second -> second.<Space>as().hasQ(SUBQ_PATTERN))
                        .map(Obj::<Space>as)
                        .forEach(s -> {
                            Machine.write(s.pattern().addQ(SUBQ), code.clone());
                        });
            } else {
                Machine.write(topic.addQ(SUBQ), code);
            }
            return 0x00;
        } finally {
            Machine.current(current);
        }
    }

    /**
     * The live session count — a test in this package polls it to see a client retire.
     */
    int sessionCount() {
        synchronized (this.sessions) {
            return this.sessions.size();
        }
    }

    // ======================== the $SYS namespace: the broker's own state ========================

    /**
     * a $SYS leaf to serve — its topic, and the node-and-key it hangs from, so its value is derived at read time
     */
    private record SysLeaf(fURI topic, Rec node, Obj key) {
    }

    private static boolean underSys(final fURI topic) {
        final List<String> path = topic.path();
        if (path.isEmpty())
            return false;
        final int first = path.get(0).isEmpty() ? 1 : 0;
        return first < path.size() && mqttServer.DOLLAR_SYS_KEY.equals(path.get(first));
    }

    /**
     * the $SYS/broker leaves a filter covers — the filter's path past $SYS/broker names a subtree, and a trailing
     * wildcard (or no path at all) widens it to every leaf beneath
     */
    private List<SysLeaf> sysLeaves(final fURI filter) {
        final List<String> path = filter.path();
        final int start = path.isEmpty() || !path.get(0).isEmpty() ? 0 : 1;
        final List<String> rel = path.size() <= start + 2 ? List.of() : path.subList(start + 2, path.size());
        final Obj broker = this.sys().atDirect(uri("broker"));
        if (broker.isNoObj() || !broker.isRec())
            return List.of();
        final List<SysLeaf> leaves = new ArrayList<>();
        this.walk(broker.asRec(), rel, SYS_NAMESPACE, leaves);
        return leaves;
    }

    private void walk(final Rec node, final List<String> rel, final fURI topic, final List<SysLeaf> leaves) {
        if (rel.isEmpty() || "#".equals(rel.get(0))) {
            collectAll(node, topic, leaves);
            return;
        }
        final String head = rel.get(0);
        if ("+".equals(head))
            return; // a single-segment wildcard names no key in a flat rec
        final Obj next = node.atDirect(uri(head));
        if (next.isNoObj())
            return;
        final List<String> rest = rel.subList(1, rel.size());
        final fURI headTopic = topic.extend(head);
        if (next.isRec())
            this.walk(next.asRec(), rest, headTopic, leaves);
        else if (rest.isEmpty())
            leaves.add(new SysLeaf(headTopic, node, uri(head)));
    }

    private static void collectAll(final Rec node, final fURI topic, final List<SysLeaf> leaves) {
        for (final Obj key : node.jvm().keySet()) {
            if (!key.isUri())
                continue;
            final Obj value = node.jvm().get(key);
            final fURI leaf = topic.extend(key.uriValue().asNode());
            if (value.isRec())
                collectAll(value.asRec(), leaf, leaves);
            else
                leaves.add(new SysLeaf(leaf, node, key));
        }
    }

    /**
     * serve a $SYS filter: the covered leaves are read (and the auto-resolving ones derived) this moment, handed to
     * the session over the ordinary queue, and the filter is registered for the live push the moments that change
     * the state earn
     */
    private int subscribeSys(final fURI filter, final mqttSession session) {
        final List<SysLeaf> leaves = this.sysLeaves(filter);
        if (leaves.isEmpty())
            return 0x80; // a $SYS path the broker does not serve is refused, not a silent no-op
        synchronized (this.sysSubs) {
            this.sysSubs.computeIfAbsent(session, x -> new ArrayList<>()).add(filter);
        }
        for (final SysLeaf leaf : leaves)
            this.deliverSys(leaf, session);
        return 0x00;
    }

    /**
     * hand one $SYS leaf to its subscriber over the ordinary delivery path — the pub::[topic, value] obs is offered
     * to the queue and the dispatch thread writes it as a PUBLISH; the value is read with at(), so a !inst leaf
     * derives on read and is never stale
     */
    private void deliverSys(final SysLeaf leaf, final mqttSession session) {
        try {
            final Obj value = leaf.node().at(leaf.key());
            final List<Obj> pubPair = new ArrayList<>(2);
            pubPair.add(uri(leaf.topic()));
            pubPair.add(value);
            this.queue.offer(objs(lst(pubPair, SUBQ_PUB_TID, null), uri(session.vid())));
        } catch (final Exception e) {
            LOG.debug("mqtt $SYS delivery to %s failed: %s", session.vid(), e.getMessage());
        }
    }

    /**
     * the moments that change the state — a session opens, a session retires, a message lands — re-derive each
     * $SYS subscriber's leaves and push them. A delivery is not a moment: pushing on delivery would push on push
     */
    void pushSys() {
        final Map<mqttSession, List<fURI>> snapshot;
        synchronized (this.sysSubs) {
            snapshot = new IdentityHashMap<>(this.sysSubs);
        }
        for (final Map.Entry<mqttSession, List<fURI>> entry : snapshot.entrySet()) {
            final mqttSession session = entry.getKey();
            if (null == this.sessionsByVid.get(session.vid()))
                continue; // retired since
            for (final fURI filter : entry.getValue())
                for (final SysLeaf leaf : this.sysLeaves(filter))
                    this.deliverSys(leaf, session);
        }
    }

    /**
     * The dispatch: drain one delivery pair — a {@code [pub, uri]} obs whose {@code stream()} yields the members — and
     * hand the pub to the session its uri names. a pair whose session already retired is dropped, not fatal.
     */
    private void dispatchLoop() {
        while (!this.closed()) {
            final Obj pair;
            try {
                pair = this.queue.takeNext();
            } catch (final InterruptedException e) {
                return; // closed
            }
            final List<Obj> args = pair.stream().toList();
            if (args.size() < 2) {
                LOG.debug("mqtt delivery dropped: %s names no session", pair);
                continue;
            }
            final mqttSession session = this.sessionsByVid.get(args.get(1).uriValue());
            if (null == session) {
                LOG.debug("mqtt delivery dropped: session %s is gone", args.get(1));
                continue;
            }
            try {
                session.deliver(args.get(0).asLst());
                this.messagesOut.incrementAndGet();
            } catch (final Exception e) {
                LOG.debug("mqtt delivery to %s failed: %s", session.vid(), e.getMessage());
            }
        }
    }

    private static InetSocketAddress address(final fURI origin) {
        if (null == origin.host() || origin.port() < 0)
            throw MTronException.of("an mqtt_server binds a host and a port: %s", origin);
        return new InetSocketAddress(origin.host(), origin.port());
    }
}
