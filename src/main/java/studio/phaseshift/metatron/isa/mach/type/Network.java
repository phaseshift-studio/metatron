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

package studio.phaseshift.metatron.isa.mach.type;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Addressable;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Rel;
import studio.phaseshift.metatron.util.CommonUtil;

import java.io.Closeable;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * The <b>Network</b> component of a Machine: the peers this frame can reach, and the authority arithmetic that
 * decides what "reachable" and "mine" mean.
 * <p>
 * It carries two maps, deliberately not one: {@code roster} is what the system <b>knows</b> — connectionless
 * endpoints, read at {@link #ROOT_ROSTER} so every machine reads the one listing — and {@code peer} is what
 * <b>this</b> machine has dialed. Knowledge is inherited, reach is not.
 * <p>
 * Like Memory and the ISA it is <em>meant</em> to accumulate, so that a frame wraps it with a
 * {@link ComponentUnion} rather than replacing it and a frame that dials a peer adds to its own level while
 * inheriting the rest. <b>It does not accumulate yet</b>: a declaration made while standing in a child is written
 * at the address, and the address is one address at every depth, so the child's is the parent's. The union is the
 * change that gives a child a level of its own.
 * <p>
 * Membership is also a <em>capability</em>, unlike an address or a vocabulary. Memory's union is sound because an
 * address means the same thing at every depth, so inheriting is pure extension; read-through on a roster instead
 * answers "may this child dial the parent's peer" with a silent yes. Expressing a frame deliberately NARROWER than
 * its parent is therefore the union's open question, and it needs a shadow-and-deny arm rather than a copy of
 * {@link studio.phaseshift.metatron.isa.mach.type.memory.MemoryUnion}.
 * <p>
 * The authority algebra lives here rather than on the Router because it is not routing — it is what the network
 * <em>is</em>. Every question about whether two addresses denote the same service, or whether a host names this
 * machine, is a question about reachability.
 * <p>
 * <b>Denotation is not scoped by this component.</b> Scoping decides which peers a frame can reach; it never
 * changes what an absolute URI means. An unreachable authority fails loudly rather than resolving elsewhere.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Network extends Addressable, Machine.Component, Closeable {

    studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger WIRE_LOG = studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty.log(Network.class);

    /**
     * this machine's connections, and the root's — {@code ~} follows {@code move}.
     */
    fURI CURRENT_PEER = f("~/network/peer");
    fURI ROOT_PEER = f("/network/peer");
    /**
     * known endpoints: knowledge is the root's, reach is this machine's.
     */
    fURI CURRENT_ROSTER = f("~/network/roster");
    fURI ROOT_ROSTER = f("/network/roster");

    /**
     * where a machine DECLARES its discovery sources: {@code <mechanism> => <handler vid>}
     */
    fURI ROOT_SOURCE = f("/network/source");

    default Optional<String> name() {
        return Optional.ofNullable(this.at(NAME).isNoObj() ? null : this.at(NAME).strValue());
    }

    default Rec roster() {
        return Machine.read(ROOT_ROSTER).orElse(rec());
    }

    /**
     * The discovery sources this machine declares: {@code <mechanism> => <handler vid>}. A source is how knowledge
     * arrives without anyone dialing anything — and nothing here starts them; {@link Machine#bootstrap()} does,
     * for the root, once.
     */
    default Rec source() {
        return this.at(SOURCE).orElse(rec());
    }

    default Rec peers() {
        return this.at(PEER).orElse(rec());
    }

    /**
     * Is this address a <b>peer</b>? A connection this machine holds, or one the roster declares.
     */
    default boolean isPeer(final fURI vid) {
        return vid.hasAuthority()
                && (Helper.peerAuthorities(this).stream().anyMatch(peer -> Helper.sameService(peer, vid))
                || Stream.concat(this.roster().keys(), this.roster().values())
                .filter(Obj::isUri).map(Obj::uriValue).anyMatch(known -> Helper.sameService(known, vid)));
    }

    /**
     * Is this address <b>mine</b>? Alias-aware, because the boot binds a wildcard while a caller addresses
     * loopback — and getting that wrong makes the boundary forward a request to itself.
     */
    default boolean own(final fURI vid) {
        return vid.hasAuthority() && Helper.authorities().stream().anyMatch(mine -> Helper.sameService(mine, vid));
    }

    /**
     * The <b>reach</b> for an authority: this machine's own connection first, the known-server roster second. A
     * connection's value is a {@code peer::T} rec carrying its transport; a roster entry's value <em>is</em> the
     * transport. {@code noobj} means the authority is neither dialed nor declared, and the caller falls through to
     * local memory.
     */
    default Obj transportOf(final fURI authority) {
        final Obj declared = Stream.concat(this.peers().elements(), this.roster().elements())
                .filter(kv -> kv.first().isUri() && Helper.sameService(kv.first().uriValue(), authority))
                .map(Rel::second)
                .map(reach -> reach.isRec() ? reach.asRec().at(TRANSPORT) : reach)
                .filter(reach -> !reach.isNoObj())
                .findFirst().orElse(noobj());
        if (!declared.isNoObj())
            return declared;
        // a DISCOVERED machine is keyed by the machine, so its addresses are the VALUES — one or more of them
        return this.roster().elements().flatMap(kv -> kv.second().stream())
                .filter(Obj::isUri)
                .filter(address -> Helper.sameService(address.uriValue(), authority)
                        && java.util.Objects.equals(address.uriValue().scheme(), authority.scheme()))
                .findFirst().orElse(noobj());
    }


    /**
     * CONNECT — the network's MOUNT op, the third copy of memory's {@code addSpace} and the ISA's
     * {@code refer}: take a connectionless description and make it a live member of this machine's reach.
     * ENSURE PEERED, never rebuild: an authority this machine has already dialed answers its existing
     * {@code peer::T} — connect is an optimization, never a protection, because a re-dial could not change
     * the far side. A fresh connection is a disconnect first, exactly as a fresh instruction set is an
     * unmount first.
     * <p>
     * The dial uri may carry the protocol ROUTE ({@code ws://host:port/mtron}); the peer keys on the
     * SERVICE authority alone, so data reads address {@code ws://host:port/<path>} while the peer's
     * {@code host} field remembers where it was dialed, route included — peer::T's own words.
     * <p>
     * The handler names the DIALER, resolved through instruction memory at dial time (which is what lets
     * this component stay below {@code /m/web} while web's protocol clients do the dialing); {@code loop:}
     * needs none. No dialer answers {@code noobj}: the authority stays known, not reachable from here.
     */
    default Obj connect(final fURI dialURI) {
        return this.connect(dialURI, null);
    }

    default Obj connect(final fURI dialURI, final fURI handler) {
        // CONNECT IS SUGAR. The idiomatic act is CONSTRUCTION: ws_client::[pattern=><ws://host/mtron?clientq=me>]
        // — the client's constructor dials, and on success REGISTERS ITSELF with the network, exactly as a
        // space's constructor indexes itself into Memory (memspace::[pattern=>xyz/#]). connect() remains for
        // the caller that has a uri and a dialer vid rather than a type literal: it constructs through
        // Helper.dial and lets register() do what the constructor would have done (register is ensure-peered,
        // so a self-registered client is found, not double-minted).
        final fURI service = Helper.serviceOf(dialURI);
        final Obj existing = this.peerOf(service);
        if (!existing.isNoObj())
            return existing;
        final Obj dialed = Helper.dial(dialURI, handler);
        WIRE_LOG.debug("connect %s handler=%s net=%s dialed=%s", dialURI, handler, System.identityHashCode(this), dialed);
        if (dialed.isNoObj() || dialed.isFail())
            return noobj();
        final Obj peer = this.index(dialed, dialURI);
        if (peer.isRec() && null != handler)
            peer.asRec().at(uri(HANDLER), uri(handler), MUTABLE);
        return peer;
    }

    /**
     * INDEX — {@link studio.phaseshift.metatron.isa.Addressable#index}, the network's arm: a protocol client that has CONNECTED
     * writes itself here, and the peer::T is minted FROM the client. Ensure-peered: an authority already
     * held answers its existing peer, so a constructor's background indexing and a connect() over the same
     * client converge on one entry. The claim is read from the client itself — {@code pattern} (retracted)
     * first, {@code host} second — so {@code index(client)} needs nothing but the client.
     */
    @Override
    default Obj index(final Obj client) {
        final fURI claim = Helper.claimOf(client);
        return null == claim ? noobj() : this.index(client, claim);
    }

    default Obj index(final Obj client, final fURI dialURI) {
        final fURI service = Helper.serviceOf(dialURI);
        final Obj existing = this.peerOf(service);
        if (!existing.isNoObj())
            return existing;
        // a client answers either the transport itself or a rec whose send_recv is the transport (the
        // client is kept on the peer so disconnect can hang up) -- adapted in ONE place, Helper.asTransport.
        final Obj transport = Helper.asTransport(client);
        WIRE_LOG.debug("index %s transport=%s isObjInst=%s", dialURI, transport, transport.isObjInst());
        if (!transport.isObjInst())
            return noobj();
        final Obj peer = rec(
                uri(AUTHORITY), uri(service),
                uri(HOST), uri(dialURI),
                uri(TRANSPORT), transport);
        if (client.isRec() && client != transport)
            peer.asRec().at(uri(CLIENT), client, MUTABLE);
        peer.selfTID(studio.phaseshift.metatron.isa.mach.machInstSet.PEER_TID);
        this.at(uri(PEER), this.peers().plus(rec(uri(service), peer)), MUTABLE);
        return peer;
    }

    /**
     * The peer this level holds for a service, or noobj — the ensure-peered scan, in one place.
     */
    default Obj peerOf(final fURI service) {
        return this.peers().elements()
                .filter(kv -> kv.first().isUri() && Helper.sameService(kv.first().uriValue(), service))
                .map(Rel::second)
                .filter(Obj::isRec)
                .findFirst().orElse(noobj());
    }

    /**
     * ACCEPT — the CO-MOUNT: the listener's half of {@link #connect(fURI)}. An INBOUND connection mints a
     * peer exactly as an outbound dial does (server : peer :: constructor : instance), keyed by the
     * remote's socket authority, with the session's reply channel as the transport. The inbound peer is
     * ANONYMOUS until an identity exchange upgrades it — no {@code host} field, because an ephemeral
     * client port is not a place to dial back to: reach lives exactly as long as the socket, and the
     * listener retracts it with {@link #disconnect(fURI)} when the socket closes.
     */
    default Obj accept(final fURI remote, final Obj session) {
        // IDENTITY RIDES THE DIAL: ws://host:port/mtron?clientq=builder2 — the ?clientq processor on
        // the connection uri IS the identity exchange (no announce message, no anonymous state to
        // upgrade), so a server that exposes the full connection uri mints a NAMED peer on accept.
        final String name = remote.qMap().get(CLIENTQ);
        final fURI service = remote.path(List.of()).removeQ(CLIENTQ);
        final Obj existing = this.peers().elements()
                .filter(kv -> kv.first().isUri() && Helper.sameService(kv.first().uriValue(), service))
                .map(Rel::second)
                .filter(Obj::isRec)
                .findFirst().orElse(noobj());
        if (!existing.isNoObj())
            return existing;
        final Obj transport = Helper.asTransport(session);
        if (!transport.isObjInst())
            return noobj();
        final Obj peer = rec(
                uri(AUTHORITY), uri(service),
                uri(TRANSPORT), transport);
        if (null != name && !name.isEmpty())
            peer.asRec().at(uri(NAME), str(name), MUTABLE);
        if (session.isRec() && session != transport)
            peer.asRec().at(uri(CLIENT), session, MUTABLE);
        peer.selfTID(studio.phaseshift.metatron.isa.mach.machInstSet.PEER_TID);
        this.at(uri(PEER), this.peers().plus(rec(uri(service), peer)), MUTABLE);
        return peer;
    }

    /**
     * The inverse of {@link #connect(fURI)}: retract THIS level's dial for the authority — hang up its
     * client, remove the member. An authority that was never dialed is a no-op answering {@code noobj}.
     * Nothing left dialed removes the {@code peer} field entirely: an EMPTY rec is not the same value as
     * an absent one (the component predicate's map idiom rejects the empty rec's noobj entry — measured).
     */
    /**
     * {@link studio.phaseshift.metatron.isa.Addressable#drop} — the network's arm of the one retraction
     * verb: {@code id -> noobj} over the peer map, with {@code disconnect} the typed workhorse underneath.
     */
    @Override
    default Obj drop(final fURI id) {
        return this.disconnect(id);
    }

    default Obj disconnect(final fURI authority) {
        final fURI service = authority.path(List.of()).removeQ(CLIENTQ);
        final Rec peers = this.peers();
        final Obj keyed = peers.elements()
                .filter(kv -> kv.first().isUri() && Helper.sameService(kv.first().uriValue(), service))
                .map(kv -> (Obj) kv)
                .findFirst().orElse(noobj());
        if (keyed.isNoObj())
            return noobj();
        final Obj peer = keyed.asRel().second();
        // the member is a rec and a rec is not closeable -- its VALUES hold the closeables (the
        // protocol client), so the displaced values are what the contract receives, then the peer.
        if (peer.isRec())
            peer.asRec().values().forEach(v -> Helper.writeComplete(noobj(), v));
        Helper.writeComplete(noobj(), peer);
        final List<Obj> kept = peers.elements().filter(kv -> !kv.equals(keyed)).map(kv -> (Obj) kv).toList();
        this.at(uri(PEER), kept.isEmpty() ? noobj() : rec(kept.stream().map(Obj::asRel)), MUTABLE);
        return peer;
    }

    /**
     * the {@code status} method, as an <b>auto</b> inst that closes over <em>this</em> network.
     * <p>
     * A static method reference would report on whatever network happened to be global; closing over the receiver
     * means the view reports on the component it was minted from.
     * <p>
     * An {@code auto} inst, so the field behaves as a method rather than as data: dereferencing it evaluates it
     * ({@code *x/status} yields the report). The <em>fluent</em> spelling does not work for a stored instance —
     * {@code *x.status()} goes through the resolver, which looks for an {@code inst-f} on the stored inst and
     * reports {@code unable to locate inst-f}. That is not a tid choice: it fails identically with a plain inst
     * (measured). Invoke it from Java, or read it by path.
     * <p>
     * rng is {@code ALL_STAR}, not {@code peer::T}: the report is a <em>multiplicity</em> of peers, one per
     * declared peer, and a single-peer rec rng fails the range check as soon as a second peer is declared.
     */
    default Inst status() {
        return auto_(instLambda(this::status)).tryToInst().asInst();
    }

    /**
     * Ask each declared peer whether it is answering, and hand back one {@code peer::T} per peer carrying its own
     * status. A peer that is declared but silent reports {@code down} rather than disappearing — the roster is
     * what was configured, and a report that hid the failures would be worse than no report.
     * <p>
     * Reads its own {@code peer} field first and falls back to the roster path, so the method works both when it
     * is invoked on the stored singleton and when it is applied as a bare inst.
     */
    default Obj status(final Obj lhs) {
        final Obj field = lhs.isRec() ? lhs.asRec().at(uri(PEER)) : noobj();
        final Obj roster = field.isRec() ? field : this.roster();
        if (!roster.isRec())
            return noobj(); // nothing declared: an empty cluster, not an error
        return objs(roster.asRec().jvm().entrySet().stream()
                .filter(e -> e.getKey().isUri() && !e.getValue().isNoObj())   // a hollow key is not a machine
                .map(e -> Helper.probe(e.getKey(), e.getValue())));
    }

    /**
     * Authority dispatch — resolve a read against a <em>declared peer</em> rather than the local memory. An
     * address without an authority, or whose authority is not in the roster, answers {@code noobj} so the
     * caller falls through to {@code memory()}. A declared peer receives the destination-relative address
     * ({@code localize()}) and answers through its transport.
     */
    default Obj read(final fURI vid) {
        // THE GATE IS THE HOST, NOT THE PORT. hasAuthority() demands host AND port, but an address is
        // elsewhere-shaped the moment it names a host -- ws://host/path on the default port and
        // loop://self are both another machine's to answer, and the port-blind form sent them silently
        // to local memory instead of the peer (measured: the loop peer's reads fell through). An
        // undeclared host still answers noobj below, so index-mounted web spaces keep catching their
        // own patterns on the fall-through exactly as before.
        if (null == vid.host())
            return noobj();
        final Obj transport = this.transportOf(vid.path(List.of()));
        WIRE_LOG.debug("read %s net=%s transport=%s", vid, System.identityHashCode(this), transport.isNoObj() ? "NONE" : "inst");
        if (transport.isNoObj())
            return noobj(); // authority not declared: not a peer this frame may reach
        // A ws:// ADDRESS IS A WIRE ADDRESS, NOT A PLACE IN THIS MACHINE. Reading one asks the PEER for that path —
        // the frame that stands in the peer has a local address of its own (/network/peer/<scheme>/<host>/<port>/<ns>,
        // derived on push), and moving to it is an ordinary local read of a machine. So nothing here maps an
        // authority to a frame: this is the data path, and only the data path.
        final Obj message = from_(vid.localize().toUri()).tryToInst();
        final Obj answer = transport.apply(message);
        WIRE_LOG.debug("read %s message=%s answer=%s", vid, message, answer);
        return answer;
    }

    /**
     * Authority dispatch — write {@code obj} to a declared peer's destination-relative address via its
     * transport. Same local/undeclared fall-through as {@link #read(fURI)}.
     */
    default Obj write(final fURI vid, final Obj obj) {
        // host-gated, exactly as read -- see the note there
        if (null == vid.host())
            return noobj();
        // A FAIL IS NOT SHIPPABLE DATA. Its serialization is a diagnostic rendering (fail::[...]), not mtron
        // source, so the value round trip below cannot rebuild it — and a fail stack belongs to the JVM that
        // raised it besides. Answering noobj sends the write down the local path, which is where a fail belongs.
        if (obj.isFail())
            return noobj();
        final Obj transport = this.transportOf(vid.path(List.of()));
        if (transport.isNoObj())
            return noobj();
        final Obj message;
        try {
            message = start_(obj).to_(vid.localize().toUri()).tryToInst();
        } catch (final Exception e) {
            // not mtron-parseable source: there is nothing to ship, so it is not a peer's to answer
            return noobj();
        }
        return transport.apply(message);
    }

    default void close() {
        this.peers().values().forEach(CommonUtil::close);
    }

    class Helper {

        /**
         * Hosts that all denote "this machine" — a server bound to the first is reachable at the others.
         */
        private static final Set<String> LOOPBACK_HOSTS = Set.of("0.0.0.0", "127.0.0.1", "localhost", "::1", "[::1]");

        private Helper() {
            // static only
        }

        public static Set<fURI> peerAuthorities(final Network network) {
            return network.peers().keys().map(Obj::uriValue).collect(Collectors.toSet());
        }

        public static Set<fURI> peerAuthorities() {
            return Helper.peerAuthorities(Machine.current().network());
        }

        /**
         * The authorities this machine <b>answers to</b>, from the {@code host} every mounted space declares.
         * The announce side, and the other half of {@link Network#own(fURI)} — deliberately NOT
         * {@link #peerAuthorities()}: that is who we have dialed, this is who we are.
         */
        public static Set<fURI> authorities() {
            final Set<fURI> mine = new LinkedHashSet<>();
            for (Memory memory = Machine.current().memory(); null != memory; memory = memory.previous())
                memory.spaces().jvm().values().stream()
                        .filter(Obj::isRec)
                        .map(space -> space.asRec().<Obj>at(uri(HOST)))
                        .map(host -> host.isUri() ? host : (host.isInst() ? host.apply() : noobj()))
                        .filter(Obj::isUri)
                        .map(Obj::uriValue)
                        .forEach(mine::add);
            return mine;
        }

        /**
         * The local address of the machine that stands in a peer, anchored on the root.
         */
        public static fURI peerPath(final fURI authority) {
            final fURI base = ROOT_PEER.extend(authority.scheme() == null ? "ws" : authority.scheme())
                    .extend(authority.host()).extend(authority.port());
            return authority.localize().isEmpty() ? base : base.extend(authority.localize());
        }

        /**
         * Two uris denote the same service: the same authority, or two loopback aliases at the same port.
         */
        /**
         * INDEXING IS A BACKGROUND BEHAVIOR. Network, Memory and InstSet are INDICES over objs that have
         * their own existence — a client is an obj whether or not any index observed it — so a protocol
         * client's constructor calls this after a successful construction and nothing else: indexing can
         * never fail the construction, and an environment with no machine (early boot, a bare test rig)
         * simply has no index watching. The space analog is a space's constructor indexing into Memory.
         */
        public static void index(final Obj client) {
            try {
                final Machine machine = Machine.current();
                if (null != machine && null != machine.network())
                    machine.network().index(client);
            } catch (final Exception e) {
                // the obj exists either way; the index just didn't observe it
            }
        }

        /**
         * The service KEY for any client-facing uri: bare authority, with exactly the processor the dial
         * spends (?clientq) retracted — every other ?q rides through.
         */
        public static fURI serviceOf(final fURI uri) {
            return uri.path(java.util.List.of()).removeQ(studio.phaseshift.metatron.Tokens.CLIENTQ);
        }

        /**
         * A client's CLAIM, read from the client itself: {@code pattern} (retracted to the dialable
         * address) first, {@code host} second — the same precedence mcpClient dials by.
         */
        public static fURI claimOf(final Obj client) {
            if (!client.isRec())
                return null;
            final Obj pattern = client.asRec().at(uri(studio.phaseshift.metatron.Tokens.PATTERN));
            if (pattern.isUri())
                return pattern.uriValue().retractPattern();
            final Obj host = client.asRec().at(uri(HOST));
            return host.isUri() ? host.uriValue() : null;
        }

        public static boolean sameService(final fURI a, final fURI b) {
            return a.authority().equals(b.authority())
                    || (isLoopbackHost(a.host()) && isLoopbackHost(b.host()) && a.port() == b.port());
        }


        /**
         * A SESSION'S REACH, adapted to the transport convention: a transport is applied with the message
         * as the LHS — the MONAD seat, code riding quoted in the arg seat (x.send_recv(|f)) — while a
         * protocol session's send_recv takes its payload as arg(0). One adapter serves both directions of
         * the dial: connect's client and accept's inbound session. Unadapted, send_recv shipped an empty
         * arg and answered noobj — measured.
         */
        /**
         * THE CLOSE-BEFORE-CLEAR CONTRACT, the network's copy of {@code Space.Helper.writeComplete}:
         * writing {@code noobj} over a live value closes what it displaces, so "write to noobj" IS the
         * universal hang-up and no separate disconnect machinery exists. Today the Java-side retraction
         * paths route through it; once the peer map is mounted as a writable space, the space write path
         * calls its own copy and a match-write ({@code ==[<x> => close()]}) hangs up the same way.
         */
        public static Obj writeComplete(final Obj newObj, final Obj currentObj) {
            if (newObj.isNoObj())
                currentObj.stream().forEach(CommonUtil::close);
            return currentObj;
        }

        public static Obj asTransport(final Obj session) {
            if (session.isObjInst())
                return session;
            final Obj reach = session.isRec() ? session.asRec().<Obj>at(uri(SEND_RECV)) : noobj();
            if (reach.isObjInst())
                // the message rides as send_recv's ARG: a bare inst survives computeArgs raw (it is
                // not an obj-call, so percolation hands it back untouched) and the client's
                // send_recv applies or ships it whole. a user's `|` quoting percolates against the
                // REAL monad lhs before this adapter ever runs -- so the adapter adds nothing.
                return instLambda(message ->
                        reach.asInst().args(studio.phaseshift.metatron.isa.m.type.impl.MLst.lst(message)).apply(noobj()));
            return session.isRec() ? session.asRec().<Obj>at(uri(TRANSPORT)) : noobj();
        }

        /**
         * THE DIAL TABLE, scheme- then handler-routed — the step a description takes to become a peer.
         * {@code loop:} is built here rather than resolved: the machine's own loopback needs no web
         * dependency and no socket — its transport applies the shipped message against the LOCAL machine,
         * so a {@code from_(path)} message reads the path here and a {@code start_(obj).to_(path)} message
         * writes it here. That makes "a read through a loop peer equals the local read" an executable
         * contract, which is the property every real transport must then preserve one authority over.
         * <p>
         * Any other scheme needs a HANDLER — a vocabulary vid resolved through instruction memory at dial
         * time. A protocol CLIENT TYPE is a dialer whose constructor takes {@code [host=>...]} (the
         * constructor IS the dial — ws_client, mcp_client); a handler that resolves to an inst is applied
         * to the dial uri instead. No handler, or one that declines: {@code noobj}, and the authority
         * stays known rather than reachable.
         */
        public static Obj dial(final fURI dialURI, final fURI handler) {
            // ONE dial path for every protocol: resolve the DIALER through instruction memory, and a
            // protocol client type's constructor IS the dial ([host=>...] in, live client out). The only
            // scheme default the table carries is loop: -> id_client::T, the identity element of the
            // client family (its send_recv applies the shipped message against the local machine) --
            // discovery supplies dialers per description for everything else (step 5).
            final fURI dialer = null != handler ? handler
                    : "loop".equals(dialURI.scheme()) ? studio.phaseshift.metatron.isa.mach.machInstSet.ID_CLIENT_TID
                    : null;
            if (null == dialer)
                return noobj();
            final Obj resolved = Machine.read(dialer);
            WIRE_LOG.debug("dial %s dialer=%s resolved=%s isType=%s hasCtor=%s", dialURI, dialer, resolved.toShortString(), resolved.isType(), resolved.isType() && resolved.asType().hasConstructor());
            if (resolved.isType() && resolved.asType().hasConstructor())
                return resolved.asType().constructor().asInst()
                        .args(studio.phaseshift.metatron.isa.m.type.impl.MLst.lst(rec(uri(HOST), uri(dialURI))))
                        .apply(noobj());
            return resolved.isObjInst() ? resolved.asInst().apply(uri(dialURI)) : noobj();
        }

        /**
         * True when {@code host} names this machine. A server declared as {@code 0.0.0.0:8555} (all local
         * interfaces) is the same service as {@code localhost:8555} — and getting this wrong is not cosmetic:
         * the boot binds the wildcard while a peer addresses the loopback name, so an alias-blind comparison
         * makes the Router forward a request to <em>itself</em>.
         */
        public static boolean isLoopbackHost(final String host) {
            return null != host && LOOPBACK_HOSTS.contains(host);
        }

        /**
         * the liveness payload — cheap, total, and free of side effects.
         */
        private static final Obj PROBE = start_(jnt(1)).plus_(jnt(2));

        /**
         * one declared peer, asked once: {@code [authority, transport, status=>[kind=>up|down, time=>ms]]}
         */
        static Rec probe(final Obj authority, final Obj transport) {
            final long start = System.currentTimeMillis();
            // AN ADDRESS IS NOT A TRANSPORT. A discovered machine is written as a uri (or a uri{+}), so applying
            // the PROBE inst to it answers noobj and reports a LIVE machine down. It has simply not been dialed:
            // that is "known", and "down" stays reserved for a transport that was tried and did not answer.
            final boolean address = !transport.isNoObj() && !transport.isObjInst();
            boolean up = false;
            if (!address) {
                try {
                    // a silent peer does NOT answer noobj — the declared transports report it as a fail, so both
                    // spellings of "nothing came back" have to count as down or a dead peer reads as healthy
                    final Obj answer = transport.isObjInst() ? transport.apply(PROBE) : noobj();
                    up = !answer.isNoObj() && !answer.isFail();
                } catch (final Exception ex) {
                    up = false;
                }
            }
            final long time = System.currentTimeMillis() - start;
            return rec(
                    uri(AUTHORITY), authority,
                    uri(TRANSPORT), transport,
                    uri(STATUS), rec(uri(KIND), str(address ? "known" : up ? "up" : "down"), uri(TIME), jnt(time)));
        }
    }
}
