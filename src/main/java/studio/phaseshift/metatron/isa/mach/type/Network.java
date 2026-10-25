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

import studio.phaseshift.metatron.furi.QProc;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Addressable;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.math.cat.Signatures;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Rel;
import studio.phaseshift.metatron.isa.mach.type.network.Listener;
import studio.phaseshift.metatron.isa.m.type.resolver.Binder;
import studio.phaseshift.metatron.util.CommonUtil;

import java.io.Closeable;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.noobjRec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * The <b>Network</b> component of a Machine: the peers this frame can reach, and the authority arithmetic that
 * decides what "reachable" and "mine" mean.
 * <p>
 * It carries two maps, deliberately not one: {@code roster} is what the system <b>knows</b> — connectionless
 * endpoints, read at {@link #ROOT_ROSTER} so every machine reads the one listing — and {@code peer} is what
 * <b>this</b> machine has dialed. Knowledge is inherited, reach is not. A third field, {@code listener}, is the
 * machine's <b>presence</b>: the sockets it serves through. A port binds once per physical machine, so presence is
 * inherited down to every child — but each machine keeps its own routes on a socket, and they leave with it.
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
 * <p>
 * <b>The index is kept true.</b> A member whose client is a {@link Link} is supervised for as long as it is
 * indexed: a connection that fails is redialed in place (on a capped backoff, and at once when a request needs it),
 * and only its OWNER's hang-up takes it out of the map — the client closed or deleted, {@link #drop} /
 * {@link #disconnect}, the frame popped. Connection on creation, destruction on deletion: whatever the far side
 * does is a failure, and the peer stays listed, offline, until it answers again.
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

    /**
     * This level's members: {@code <key> => peer::T}, where the KEY is the dial's own address as a valid local path,
     * one per surface ({@link Helper#keyOf}: {@code ws://localhost:8555/mtron} is {@code ws/localhost/8555/mtron}). So a
     * member is addressable at {@code ~/network/peer/<key>} — read field by field, deleted to hang it up — and the
     * service it reaches is the {@code authority} it carries, never its key.
     */
    default Rec peers() {
        return this.at(PEER).orElse(rec());
    }

    /**
     * The member at a peer KEY, or noobj.
     */
    default Obj peerAt(final fURI key) {
        return this.peers().elements()
                .filter(kv -> kv.first().isUri() && kv.first().uriValue().equals(key))
                .map(Rel::second)
                .filter(Obj::isRec)
                .findFirst().orElse(noobj());
    }

    /**
     * This level's PRESENCE: {@code <key> => listener::T}, one per socket this machine serves through — one it opened,
     * or one inherited from above that it keeps its own routes on — keyed like a peer ({@link Helper#keyOf}:
     * {@code ws://0.0.0.0:8555} is {@code ws/localhost/8555}, a wildcard bind being a loopback alias) and so addressable at {@code ~/network/listener/<key>}.
     * The entries are this level's alone; a machine reaches the sockets above it through {@link Helper#listenerFor}.
     */
    default Rec listeners() {
        return this.at(LISTENER).orElse(rec());
    }

    /**
     * The listener::T entry at a KEY, or noobj.
     */
    default Obj listenerAt(final fURI key) {
        return this.listeners().elements()
                .filter(kv -> kv.first().isUri() && kv.first().uriValue().equals(key))
                .map(Rel::second)
                .filter(Obj::isRec)
                .findFirst().orElse(noobj());
    }

    /**
     * LISTEN — serve through a socket from this level, with this level's own routes: a socket this machine opened
     * (its level is the {@link Listener#owner()}), or one it inherited ({@link Helper#listenerFor}). The routes are this
     * level's: a key another level already serves on the socket is refused, and the routes leave with the level
     * ({@link #close()}). Listening again replaces this level's routes on the socket.
     */
    default Obj listen(final Listener server, final Rec routes) {
        final fURI key = Helper.keyOf(server.authority());
        final Obj entry = rec(uri(AUTHORITY), uri(server.authority()), uri(SERVER), server);
        // absent rather than empty: the map idiom of the predicate rejects an empty rec
        if (!routes.isEmpty())
            entry.asRec().at(uri(ROUTE), routes, MUTABLE);
        entry.selfTID(studio.phaseshift.metatron.isa.mach.machInstSet.LISTENER_TID);
        // the claim check and the share are one step per socket, or two levels mounting the same key at once would
        // both pass the check; the level's own lock serializes its map, as index does
        synchronized (server) {
            server.refuseClaimed(this, routes);
            synchronized (this) {
                final Rec kept = Helper.without(this.listeners(), kv -> kv.first().uriValue().equals(key));
                this.at(uri(LISTENER), kept.isEmpty() ? rec(uri(key), entry) : kept.plus(rec(uri(key), entry)), MUTABLE);
            }
            server.share(this);
        }
        return entry;
    }

    /**
     * The inverse of {@link #listen}: this level stops serving through the socket at {@code authority}. Its routes
     * leave the socket, and a socket this level OPENED is released with them. Answers the entry, or noobj when this
     * level served nothing there.
     */
    default Obj unlisten(final fURI authority) {
        final fURI key = Helper.keyOf(authority);
        final Obj entry;
        synchronized (this) {
            entry = this.listenerAt(key);
            if (entry.isNoObj())
                return noobj();
            final Rec kept = Helper.without(this.listeners(), kv -> kv.first().uriValue().equals(key));
            this.at(uri(LISTENER), kept.isEmpty() ? noobj() : kept, MUTABLE);
        }
        // CLEAR, THEN CLOSE, as disconnect does: the entry is gone from the map before the socket is touched
        Helper.stopListening(this, entry);
        return entry;
    }

    /**
     * THE MACHINES the roster knows: {@code <host> => reach{+}}, one row per machine and its reaches — an
     * endpoint uri to dial or the transport that reaches it.
     * <p>
     * The roster is a CONTAINER, not the listing: it carries {@code discover} beside {@code machine}, so a
     * consumer that wants machines has to say so. Reading {@code roster()} as the listing walks
     * {@code {discover, machine}} and probes a discovery handler as though it were a peer.
     */
    default Rec machines() {
        final Obj field = this.roster().at(uri(MACHINE));
        return field.isRec() ? field.asRec() : rec();
    }

    /**
     * Is this address a <b>peer</b>? A connection this machine holds, or one the roster declares.
     */
    default boolean isPeer(final fURI vid) {
        return vid.hasAuthority()
                && (Helper.peerAuthorities(this).stream().anyMatch(peer -> Helper.sameService(peer, vid))
                || this.machines().elements()
                .flatMap(kv -> Stream.concat(Stream.of(kv.first()), kv.second().stream()))
                .filter(Obj::isUri).map(Obj::uriValue).anyMatch(known -> Helper.sameService(known, vid)));
    }

    /**
     * The <b>reach</b> for an authority: this machine's own connection first, the known-server roster second. A
     * connection's value is a {@code peer::T} rec carrying its transport; a roster entry's value <em>is</em> the
     * transport. {@code noobj} means the authority is neither dialed nor declared, and the caller falls through to
     * local memory.
     */
    default Obj transportOf(final fURI authority) {
        // A DIALED PEER ANSWERS BY THE AUTHORITY IT CARRIES -- its key is a local path, and two surfaces of one
        // service may both be members: the first that carries a transport answers the wire
        final Obj dialed = this.peers().values()
                .filter(peer -> Helper.sameService(Helper.authorityOf(peer), authority))
                .map(peer -> peer.asRec().<Obj>at(TRANSPORT))
                .filter(reach -> !reach.isNoObj())
                .findFirst().orElse(noobj());
        if (!dialed.isNoObj())
            return dialed;
        final Obj declared = this.machines().elements()
                .filter(kv -> kv.first().isUri() && Helper.sameService(kv.first().uriValue(), authority))
                .map(Rel::second)
                .map(reach -> reach.isRec() ? reach.asRec().at(TRANSPORT) : reach)
                .filter(reach -> !reach.isNoObj())
                .findFirst().orElse(noobj());
        if (!declared.isNoObj())
            return declared;
        // a DISCOVERED machine is keyed by the machine, so its addresses are the VALUES — one or more of them
        return this.machines().elements().flatMap(kv -> kv.second().stream())
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
        // CONNECT IS SUGAR. The idiomatic act is CONSTRUCTION: ws_client::[pattern=><ws://host/mtron?name=me>]
        // — the client's constructor dials, and on success REGISTERS ITSELF with the network, exactly as a
        // space's constructor indexes itself into Memory (memspace::[pattern=>xyz/#]). connect() remains for
        // the caller that has a uri and a dialer vid rather than a type literal: it constructs through
        // Helper.dial and lets register() do what the constructor would have done (register is ensure-peered,
        // so a self-registered client is found, not double-minted).
        // ENSURE-PEERED, as an optimization: a surface this level already holds is not dialed twice. A member its
        // owner hung up is not one to answer -- it is retracted, and the dial below replaces it.
        final Obj existing = this.peerAt(Helper.keyOf(dialURI));
        if (!existing.isNoObj() && !Helper.hungUp(existing))
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
     * INDEX — {@link studio.phaseshift.metatron.isa.Addressable#index}, the network's arm: a protocol client that has
     * CONNECTED writes itself here, at its KEY ({@link Helper#keyOf}), and the peer::T is minted FROM the client. NO
     * TWO EQUIVALENT CONNECTIONS: a fresh construction at a key the level already holds closes the member there and
     * takes its place, while indexing the SAME client again (a constructor's background index, then connect() over
     * that client) is idempotent. The claim is read from the client itself — {@code pattern} (retracted) first,
     * {@code host} second — so {@code index(client)} needs nothing but the client.
     */
    @Override
    default Obj index(final Obj client) {
        final fURI claim = Helper.claimOf(client);
        return null == claim ? noobj() : this.index(client, claim);
    }

    default Obj index(final Obj client, final fURI dialURI) {
        final fURI key = Helper.keyOf(dialURI);
        final Obj peer;
        final Obj displaced;
        // ONE WRITER AT A TIME: the peer map is read, extended and written back, and a dial, a listener's accept and
        // the supervisor's retraction can arrive on three threads at once -- unserialized, the later write drops the
        // earlier one's member
        synchronized (this) {
            displaced = this.peerAt(key);
            if (displaced.isRec() && client == displaced.asRec().<Obj>atDirect(uri(CLIENT)))
                return displaced;
            // a client answers either the transport itself or a rec whose eval override derives the transport (the
            // client is kept on the peer so disconnect can hang up) -- adapted in ONE place, Helper.asTransport.
            final Obj transport = Helper.asTransport(client);
            final Obj inst = client.isRec() ? client.asRec().at(uri(INST)) : noobj();
            WIRE_LOG.debug("index %s key=%s transport=%s inst=%s", dialURI, key, transport.isNoObj() ? "NONE" : "inst", inst.isRec() ? "set" : "none");
            // a peer is reachable by a transport OR by at least one instruction override — a read-only client has no
            // eval, hence no transport, yet still answers reads through its `from` override
            if (!transport.isObjInst() && !inst.isRec())
                return noobj();
            peer = rec(
                    uri(AUTHORITY), uri(Helper.serviceOf(dialURI)),
                    uri(HOST), uri(dialURI));
            if (transport.isObjInst())
                peer.asRec().at(uri(TRANSPORT), transport, MUTABLE);
            // the client IS the connection: its `inst` (the instructions you talk to it with) stays on the client —
            // the peer holds the client, and overrideOf reads the capability through it. No copy, no divergence.
            if (client.isRec() && client != transport)
                peer.asRec().at(uri(CLIENT), client, MUTABLE);
            peer.selfTID(studio.phaseshift.metatron.isa.mach.machInstSet.PEER_TID);
            final Rec kept = Helper.without(this.peers(), kv -> kv.first().uriValue().equals(key));
            this.at(uri(PEER), kept.isEmpty() ? rec(uri(key), peer) : kept.plus(rec(uri(key), peer)), MUTABLE);
        }
        // THE CONNECTION THIS ONE REPLACES IS CLOSED -- outside the lock, because closing a link is a hang-up and a
        // hang-up retracts (it finds nothing: its member is already gone). Close-before-clear, as everywhere.
        if (displaced.isRec())
            Helper.release(displaced);
        // A LINK IS SUPERVISED FROM THE MOMENT IT IS INDEXED: what the index granted, the index keeps true -- a
        // failed connection is redialed, an owner's hang-up is retracted. Idempotent per level.
        if (client instanceof Link)
            studio.phaseshift.metatron.isa.mach.type.network.NetworkSupervisor.watch(this);
        return peer;
    }

    /**
     * The first member this level holds for a SERVICE — matched by the authority it carries, so any of its surfaces —
     * or noobj. {@link #peerAt} is the exact lookup, by key.
     */
    default Obj peerOf(final fURI service) {
        return this.peers().values()
                .filter(peer -> Helper.sameService(Helper.authorityOf(peer), service))
                .findFirst().orElse(noobj());
    }

    /**
     * THE DISPATCH PRIMITIVE — the peer's OVERRIDE for an instruction, by the authority that owns it. A peer
     * overrides instructions with its own wire bodies (its mini-instset, {@code inst => { tid => override }}); this
     * answers "does the peer at {@code authority} implement {@code instTID}, and with what body". An override IS the
     * instruction it replaces with another f(): the same signature, within the type system's acceptance
     * ({@link Helper#accepts}), so a call reaches it by swapping f() and nothing else ({@link Helper#dispatch}). The
     * value is that inst, or noobj when the peer does not override the instruction — and an inst of a signature the
     * instruction's own does not accept is not an override of it (the caller then derives it, or falls through to the
     * local definition).
     */
    default Obj overrideOf(final fURI authority, final fURI instTID) {
        final Obj peer = this.peerOf(authority);
        if (!peer.isRec())
            return noobj();
        // the capability lives on the CLIENT — the connection — not on the peer: the client's `inst` is the set of
        // instructions you use when talking to that connection.
        return Helper.overrideIn(peer.asRec().at(uri(CLIENT)), instTID);
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
        // SELF-LOOP: a loopback dialer is THIS machine. The outbound half of the dial — the ws_client
        // constructor's index — already records the peer (keyed by OUR authority, with a dial-back host
        // and an initiate transport). Minting a second peer keyed by the dialer's ephemeral source port
        // would leave one socket as two peers: the twin is anonymous, undialable, and a fan-out target
        // that no one answers. Decline the co-mount; the dialer records the peer.
        if (Helper.isLoopbackHost(remote.host()))
            return noobj();
        // IDENTITY RIDES THE DIAL: ws://host:port/mtron?name=builder2 — the ?name parameter on
        // the connection uri IS the identity exchange (no announce message, no anonymous state to
        // upgrade), so a server that exposes the full connection uri mints a NAMED peer on accept.
        final String name = remote.qMap().get(NAME);
        final fURI service = remote.path(List.of()).removeQ(NAME);
        // the co-mount is keyed like a dial: the remote's socket authority and the route it dialed, as a path
        final fURI key = Helper.keyOf(remote);
        // serialized with index and disconnect: the listener accepts on its own worker threads
        synchronized (this) {
            final Obj existing = this.peerAt(key);
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
            this.at(uri(PEER), this.peers().plus(rec(uri(key), peer)), MUTABLE);
            return peer;
        }
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

    /**
     * Retract members, by what the address names: a peer KEY ({@code ws/localhost/8555/mtron}) names that member; a
     * wire uri with a route ({@code ws://localhost:8555/mtron}) names that surface; a bare authority
     * ({@code ws://localhost:8555}) names every surface of the service. Answers what was dropped — the member, or a bag
     * of them — or noobj when the address held nothing here.
     */
    default Obj disconnect(final fURI address) {
        final java.util.function.Predicate<Rel> named = Helper.naming(address);
        final List<Obj> dropped;
        synchronized (this) {
            dropped = this.peers().elements().filter(kv -> kv.first().isUri() && named.test(kv)).map(Rel::second).toList();
            if (dropped.isEmpty())
                return noobj();
            // the zero rec is noobj as a value, but a field written with one keeps it: write noobj so the field goes
            final Rec kept = Helper.without(this.peers(), named);
            this.at(uri(PEER), kept.isEmpty() ? noobj() : kept, MUTABLE);
        }
        // CLEAR, THEN CLOSE. The members leave the map under the lock and what they held is closed outside it:
        // closing a link is its owner's hang-up, and a hang-up retracts -- which would come straight back into
        // this map.
        dropped.forEach(Helper::release);
        return objs(dropped.toArray(Obj[]::new));
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
     * is invoked on the stored singleton and when it is applied as a bare inst. A dialed peer is probed through the
     * transport its {@code peer::T} carries (see {@link Helper#probe}).
     */
    default Obj status(final Obj lhs) {
        final Obj field = lhs.isRec() ? lhs.asRec().at(uri(PEER)) : noobj();
        final Obj roster = field.isRec() ? field : this.machines();
        if (!roster.isRec())
            return noobj(); // nothing declared: an empty cluster, not an error
        return objs(roster.asRec().jvm().entrySet().stream()
                .filter(e -> e.getKey().isUri() && !e.getValue().isNoObj())   // a hollow key is not a machine
                // a member reports the authority it reaches; its key is only where it sits in this map
                .map(e -> Helper.probe(null == Helper.authorityOf(e.getValue()) ? e.getKey() : uri(Helper.authorityOf(e.getValue())),
                        e.getValue())));
    }

    /**
     * SUBMIT — the eval verb: evaluate {@code code} on the peer that owns {@code authority}, and answer its result.
     * This is the general "ship mtron, get the result" that the distribution rewrite and any parallel fan-out use: a
     * peer that declares an {@code eval} override answers {@code eval(code)} with its own f() — the code BLOCKED, so it
     * rides raw as the payload ({@link Helper#evaluate}); one that does not falls back to its transport.
     */
    default Obj submit(final fURI authority, final Obj code) {
        final Obj eval = this.overrideOf(authority, EVAL_INST_TID);
        if (eval.isObjInst())
            return Helper.evaluate(eval.asInst(), code);
        final Obj transport = this.transportOf(authority);
        return transport.isNoObj() ? noobj() : transport.apply(code);
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
        final fURI authority = vid.path(List.of());
        // DISPATCH LADDER: the peer's native `from` override first (a read-only client), then `eval` (the lowering —
        // the read message IS code), then the legacy transport. Each rung is the instruction's own call with the peer's
        // f(): from(<vid>) — the address exactly as read — on its from, eval(from(<localized>)) on its eval. A
        // read-only peer (from, no eval) has no transport and is reached only through its `from` override here.
        final Obj from = this.overrideOf(authority, FROM_INST_TID);
        if (from.isObjInst())
            return Helper.dispatch(from.asInst(), instB(FROM_INST_TID, lst(vid.toUri())), noobj());
        final Obj message = from_(vid.localize().toUri()).tryToInst();
        final Obj eval = this.overrideOf(authority, EVAL_INST_TID);
        if (eval.isObjInst())
            return Helper.evaluate(eval.asInst(), message);
        final Obj transport = this.transportOf(authority);
        if (transport.isNoObj()) {
            // AUTO-CONNECT: a read of an http(s) address that is neither dialed nor declared constructs its client on
            // demand — the read IS the dial — so a bare *<http://…> becomes a peer instead of falling through to a
            // mounted http space. The client declares {from => GET} and is indexed by connect.
            if ("http".equals(authority.scheme()) || "https".equals(authority.scheme())) {
                final Obj peer = this.connect(authority);
                if (peer.isRec()) {
                    final Obj from2 = this.overrideOf(authority, FROM_INST_TID);
                    if (from2.isObjInst())
                        return Helper.dispatch(from2.asInst(), instB(FROM_INST_TID, lst(vid.toUri())), noobj());
                }
            }
            return noobj(); // authority not declared: not a peer this frame may reach
        }
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
        final fURI authority = vid.path(List.of());
        // DISPATCH LADDER: the peer's native `to` override first (a write-only sink), then `eval` (the write message IS
        // code), then the legacy transport. Each rung is the instruction's own call with the peer's f(): obj.to(<vid>)
        // — the obj the lhs, the address the arg, exactly as the native to — on its to, eval(start(obj).to(<localized>))
        // on its eval. A write-only peer (to, no eval) has no transport and is reached only through its `to` override.
        final Obj to = this.overrideOf(authority, TO_INST_TID);
        if (to.isObjInst())
            return Helper.dispatch(to.asInst(), instB(TO_INST_TID, lst(vid.toUri())), obj);
        final Obj message;
        try {
            message = start_(obj).to_(vid.localize().toUri()).tryToInst();
        } catch (final Exception e) {
            // not mtron-parseable source: there is nothing to ship, so it is not a peer's to answer
            return noobj();
        }
        final Obj eval = this.overrideOf(authority, EVAL_INST_TID);
        if (eval.isObjInst())
            return Helper.evaluate(eval.asInst(), message);
        final Obj transport = this.transportOf(authority);
        if (transport.isNoObj())
            return noobj();
        return transport.apply(message);
    }

    /**
     * Close this level: its reach ends with it. Supervision stops FIRST -- hanging up the members below is a
     * retraction, not a failure to repair -- and then every member is disconnected, which is what actually hangs up
     * a client: a peer::T is a rec, and closing the rec would close nothing it holds.
     */
    default void close() {
        studio.phaseshift.metatron.isa.mach.type.network.NetworkSupervisor.release(this);
        this.peers().keys().filter(Obj::isUri).map(Obj::uriValue).toList().forEach(this::disconnect);
        // PRESENCE ENDS WITH THE LEVEL: its routes leave every socket it served through, and a socket it opened is
        // released -- a child's pop never closes the port its parent serves on
        this.listeners().values().filter(Obj::isRec).toList().forEach(entry -> Helper.stopListening(this, entry));
    }

    /**
     * A LINK — what a protocol client that HOLDS a live connection tells the network about it. The network never
     * learns a protocol: it asks these three questions of any member whose client answers them, and supervises
     * by the answers ({@link studio.phaseshift.metatron.isa.mach.type.network.NetworkSupervisor}).
     * <ul>
     *     <li>{@link #connected()} — is the connection up right now? Local and cheap: no round trip.</li>
     *     <li>{@link #hungUp()} — did the client's OWNER close it? That is the one explicit shutdown, and it is
     *     final: a hung-up member is dropped from the peer map and never redialed. Everything else that ends a
     *     connection — the far side restarting, shutting down, crashing, refusing — is a failure.</li>
     *     <li>{@link #redial()} — re-establish the connection IN PLACE, bounded, answering whether it came back.</li>
     * </ul>
     * IN PLACE, NOT REBUILT. The client is the connection's identity: a handle stored at an address must stay the
     * live connection, so that deleting it there hangs up what is actually connected. A failure is repaired inside
     * the object rather than by constructing a new one beside it — which would leave the stored handle dead and the
     * deletion closing nothing.
     * <p>
     * A member whose client is not a link — a loop client, a connectionless transport, a listener's co-mount — is
     * not supervised: it has no connection of its own to lose, or (the co-mount) its dialer owns it.
     */
    interface Link {

        /**
         * @return true when the connection is up right now
         */
        boolean connected();

        /**
         * @return true once the client's owner closed it — the explicit shutdown, never redialed
         */
        boolean hungUp();

        /**
         * Re-establish the connection in place, within the link's own connect timeout. A hung-up link declines.
         *
         * @return true when the connection is up afterwards
         */
        boolean redial();
    }

    /**
     * A PEER'S BODY, BOUND INTO A CALL — the f() of a from/to resolved against a dialed peer
     * ({@link Helper#atPeer}). It runs the peer's override inside the network's q-proc layer, because that layer is
     * the one place a vid is handed to a source ({@link Machine#read}, {@link Machine#write}): a local
     * {@code ws://peer/x/#?subq} subscription must still see a write that compiled straight to the peer's {@code to}.
     * Named rather than a lambda so the compiled code says what it bound — explain reports this class, and the
     * string is the authority whose body runs.
     */
    record AtPeer(Network network, fURI authority, Inst.f body, boolean read) implements BiFunction<Obj, Inst, Obj> {
        @Override
        public Obj apply(final Obj lhs, final Inst inst) {
            final fURI vid = inst.arg(0).uriValue();
            return this.read
                    ? QProc.Layer.read(this.network, vid, () -> this.body.apply(lhs, inst))
                    : QProc.Layer.write(this.network, vid, lhs, () -> this.body.apply(lhs, inst));
        }

        @Override
        public String toString() {
            return this.authority.toString();
        }
    }

    class Helper {

        /**
         * Hosts that all denote "this machine" — a server bound to the first is reachable at the others.
         */
        // a socket reports IPv6 loopback uncompressed (and fURI.hostOf brackets it), so both spellings are here
        private static final Set<String> LOOPBACK_HOSTS = Set.of("0.0.0.0", "127.0.0.1", "localhost", "::1", "[::1]",
                "0:0:0:0:0:0:0:1", "[0:0:0:0:0:0:0:1]");

        private Helper() {
            // static only
        }

        public static Set<fURI> peerAuthorities(final Network network) {
            return network.peers().values().map(Helper::authorityOf).filter(Objects::nonNull).collect(Collectors.toSet());
        }

        public static Set<fURI> peerAuthorities() {
            return Helper.peerAuthorities(Machine.current().network());
        }

        /**
         * The authorities this machine <b>answers on</b>: the {@code host} of every mounted space that
         * {@link Space#listens() listens}. What discovery announces — deliberately NOT
         * {@link #peerAuthorities()}: that is who we have dialed, this is who we are.
         * <p>
         * A LISTENER'S HOST, NEVER A BACKEND'S. Every space may declare a {@code host}, but only a listener's is
         * an address this machine answers on: a tblespace's is the database it dials OUT to, and it carries the
         * database login ({@code boot/space/tble/mariadb.mtron} puts {@code ?user=…&password=…} on it). Reading
         * every space's host made that login part of who this machine is, and mDNS multicast it.
         */
        public static Set<fURI> authorities() {
            final Set<fURI> mine = new LinkedHashSet<>();
            // PRESENCE IS INHERITED: a machine answers on the sockets it opened and on every ancestor's, up to the
            // root -- a port binds once per physical machine, so a child serves through the sockets above it
            for (Obj at = Machine.current(); at instanceof Machine machine; at = machine.parent())
                machine.network().listeners().values()
                        .map(Helper::authorityOf)
                        .filter(Objects::nonNull)
                        .forEach(mine::add);
            // the listening spaces (httpspace, wsspace), until they are retired
            for (Memory memory = Machine.current().memory(); null != memory; memory = memory.previous())
                memory.spaces().jvm().values().stream()
                        .filter(space -> space instanceof Space listener && listener.listens())
                        .map(space -> space.asRec().<Obj>at(uri(HOST)))
                        .map(host -> host.isUri() ? host : (host.isInst() ? host.apply() : noobj()))
                        .filter(Obj::isUri)
                        .map(Obj::uriValue)
                        .forEach(mine::add);
            return mine;
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
         * RETRACTION IS A BACKGROUND BEHAVIOR TOO — the dual of {@link #index(Obj)}. A client its OWNER closed calls
         * this from its close, and every level that indexed it drops the member: destruction on deletion, so a
         * client deleted at the address that held it ({@code /usr/peer/x -> noobj}, close-before-clear) leaves no
         * dead peer behind. Like indexing it can never fail the close: the client is closed either way.
         */
        public static void retract(final Obj client) {
            try {
                studio.phaseshift.metatron.isa.mach.type.network.NetworkSupervisor.retract(client);
            } catch (final Exception e) {
                // the client is closed either way; the index just didn't observe it
            }
        }

        /**
         * Is this member REACHABLE right now? A {@link Link} answers for itself, locally — no round trip. A member
         * without one (a loop client, a connectionless transport, a listener's co-mount) is reachable exactly when
         * it carries a transport: it holds no connection of its own to lose.
         */
        public static boolean online(final Obj peer) {
            if (!peer.isRec())
                return false;
            return peer.asRec().<Obj>atDirect(uri(CLIENT)) instanceof Link link
                    ? link.connected()
                    : peer.asRec().<Obj>atDirect(uri(TRANSPORT)).isObjInst();
        }

        /**
         * Did this member's OWNER hang it up? The one explicit shutdown: such a member is dropped, never redialed.
         */
        public static boolean hungUp(final Obj peer) {
            return peer.isRec() && peer.asRec().<Obj>atDirect(uri(CLIENT)) instanceof Link link && link.hungUp();
        }

        /**
         * The peer map without the members the predicate names — the zero rec once nothing is left, so the field
         * reads absent rather than empty: an EMPTY rec is not the same value as an absent one (the component
         * predicate's map idiom rejects the empty rec's noobj entry — measured).
         */
        static Rec without(final Rec peers, final java.util.function.Predicate<Rel> named) {
            final List<Rel> kept = peers.elements()
                    .filter(kv -> !kv.first().isUri() || !named.test(kv))
                    .toList();
            return kept.isEmpty() ? noobjRec() : rec(kept.stream());
        }

        /**
         * THE PEER KEY — the dial's own address as a LEGAL local path, one per surface: {@link fURI#toPath()} of the
         * dial, so {@code ws://peer.local:8555/mtron?name=me} is {@code ws/peer/local/8555/mtron}. Scheme, every label
         * of the host, port, then the route, and never a {@code ?q}: the identity the dial spends and the serializers
         * that ride the session name no connection (a {@code ?clientq} once leaked into the key this way — measured).
         * <p>
         * LEGAL MEANS NO DOTS AND NO COLONS IN A SEGMENT. A dotted first segment reads as a host, so a host is split
         * into its labels in order ({@code 10.0.0.7} is {@code 10/0/0/7}) — never kept whole, and never merged, since a
         * dropped boundary lets two machines share a key and the redial of one would hang up the other. The one step
         * {@code toPath()} does not take is the alias: a loopback host is spelled {@code localhost} first, so
         * {@code 127.0.0.1} and {@code [::1]} key the connection {@code localhost} already holds.
         */
        public static fURI keyOf(final fURI dial) {
            final String host = dial.host();
            return (null != host && isLoopbackHost(host) ? dial.host("localhost") : dial).toPath();
        }

        /**
         * The authority a member reaches — the service it carries, never its key — or null for a value that is not
         * a member (a roster's bare transport, a discovered address).
         */
        public static fURI authorityOf(final Obj peer) {
            if (!peer.isRec())
                return null;
            final Obj authority = peer.asRec().atDirect(uri(AUTHORITY));
            return authority.isUri() ? authority.uriValue() : null;
        }

        /**
         * What a retraction's address names (see {@link Network#disconnect}): a key that member, a wire uri with a
         * route that surface, a bare authority every surface of the service.
         */
        static java.util.function.Predicate<Rel> naming(final fURI address) {
            if (null == address.host())
                return kv -> kv.first().uriValue().equals(address);
            final boolean surface = address.path().stream().anyMatch(segment -> !segment.isEmpty());
            if (surface) {
                final fURI key = keyOf(address);
                return kv -> kv.first().uriValue().equals(key);
            }
            final fURI service = serviceOf(address);
            return kv -> sameService(authorityOf(kv.second()), service);
        }

        /**
         * Close what a member held — the displaced half of close-before-clear. A member is a rec and a rec is not
         * closeable: its VALUES hold the closeables (the protocol client), so the values are what the contract
         * receives, then the member.
         */
        static void release(final Obj peer) {
            if (peer.isRec())
                peer.asRec().values().forEach(value -> Helper.writeComplete(noobj(), value));
            Helper.writeComplete(noobj(), peer);
        }

        /**
         * The socket serving {@code authority} as the current machine sees it: one its own level holds, or one
         * inherited from the machines above it, up to the root. A port binds once per physical machine, so a machine
         * that wants to serve at an authority an ancestor already listens on serves through that socket. null when no
         * machine in the chain holds one.
         */
        public static Listener listenerFor(final fURI authority) {
            final fURI key = keyOf(authority);
            for (Obj at = Machine.current(); at instanceof Machine machine; at = machine.parent()) {
                final Obj entry = machine.network().listenerAt(key);
                if (entry.isRec() && entry.asRec().atDirect(uri(SERVER)) instanceof Listener listener && !listener.closed())
                    return listener;
            }
            return null;
        }

        /**
         * A level stops serving through a socket: its routing table leaves the socket, and the socket itself is
         * released when the level is its owner. A sharer's close never touches the port — not {@link #release}, which
         * would close every value the entry holds, the shared server included.
         */
        static void stopListening(final Network level, final Obj entry) {
            final Obj server = entry.isRec() ? entry.asRec().atDirect(uri(SERVER)) : noobj();
            if (!(server instanceof Listener listener))
                return;
            listener.unshare(level);
            if (listener.owner() == level)
                CommonUtil.close(listener);
        }

        /**
         * The service KEY for any client-facing uri: bare authority, with exactly the processor the dial
         * spends (?name) retracted — every other ?q rides through.
         */
        public static fURI serviceOf(final fURI uri) {
            return uri.path(java.util.List.of()).removeQ(studio.phaseshift.metatron.Tokens.NAME);
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
            if (null == a || null == b)
                return false;
            return Objects.equals(a.authority(), b.authority())
                    || (isLoopbackHost(a.host()) && isLoopbackHost(b.host()) && a.port() == b.port());
        }


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

        /**
         * A SESSION'S TRANSPORT — the one inst that ships a message and answers the peer's result, which is what
         * {@code peer::T.transport}, the probe and a roster entry still speak (the message as the transport's LHS).
         * It is DERIVED, never declared: a session's {@code eval} override is exactly that arrow, so the transport
         * evaluates the message on it ({@link #evaluate}). A session that is itself an inst, or a rec that carries a
         * {@code transport}, answers as it stands. A client reaches the network through its mini-instset alone —
         * {@code send_recv} is no reach (2026-10-24: every client provides its own {@code to}, {@code from} and
         * {@code eval}); it survives only as a ws client's raw wire instruction.
         */
        public static Obj asTransport(final Obj session) {
            if (session.isObjInst())
                return session;
            final Obj eval = Helper.overrideIn(session, EVAL_INST_TID);
            if (eval.isObjInst())
                return instLambda(message -> Helper.evaluate(eval.asInst(), message));
            return session.isRec() ? session.asRec().<Obj>at(uri(TRANSPORT)) : noobj();
        }

        /**
         * The override a client declares for {@code instTID} in its mini-instset ({@code inst => { tid => override }})
         * — or noobj: absent, or of a signature the type system does not accept as that instruction's ({@link #accepts}).
         * Read flat, by key equality: {@code at()} would resolve the absolute tid key into nested path segments.
         */
        public static Obj overrideIn(final Obj client, final fURI instTID) {
            final Obj inst = client.isRec() ? client.asRec().at(uri(INST)) : noobj();
            if (!inst.isRec())
                return noobj();
            final Obj override = inst.asRec().elements()
                    .filter(kv -> kv.first().isUri() && kv.first().uriValue().equals(instTID))
                    .map(Rel::second)
                    .findFirst().orElse(noobj());
            if (!override.isObjInst())
                return noobj();
            if (!Helper.accepts(instTID, override.asInst())) {
                WIRE_LOG.debug("override %s rejected: its signature is not %s's", override, instTID);
                return noobj();
            }
            return override;
        }

        /**
         * The NATIVE definition of an instruction — its signature: the tid's dom/rng and its argument types — read
         * where an instruction comes from (a read of instruction memory, never the wire: an instruction tid names no
         * host), unaddressed so no copy of it stands at the definition's address; null when nothing is declared there.
         */
        public static Inst nativeOf(final fURI instTID) {
            return Machine.read(instTID).stream()
                    .filter(Obj::isObjInst)
                    .map(Obj::asInst)
                    .map(Inst.Helper::unaddressed)
                    .findFirst().orElse(null);
        }

        /**
         * SAME SIGNATURE, WITHIN THE TYPE SYSTEM'S ACCEPTANCE — may {@code override} stand in for the instruction at
         * {@code instTID}? Its native definition must accept it as itself ({@link Inst#test}: the base, the dom, the
         * rng, every argument type), the function aside: the function is the one thing an override changes. An
         * instruction nothing native declares (a tool only the peer serves) is defined by its override, which is then
         * accepted as it stands.
         */
        public static boolean accepts(final fURI instTID, final Inst override) {
            final Inst definition = Helper.nativeOf(instTID);
            return null == definition || definition.f(null).test(override.f(null));
        }

        /**
         * DECLARE AN OVERRIDE — the native instruction at {@code instTID} with {@code body} as its f(): the same
         * signature by construction, so the network reaches it by swapping f() alone. Answers noobj when nothing native
         * is declared there (a bare rig with no instruction memory): such a client declares no override.
         */
        public static Obj override(final fURI instTID, final BiFunction<Obj, Inst, Obj> body) {
            return Helper.override(instTID, Inst.f.of(body));
        }

        public static Obj override(final fURI instTID, final Inst.f body) {
            final Inst definition = Helper.nativeOf(instTID);
            return null == definition ? noobj() : definition.f(body);
        }

        /**
         * DISPATCH — {@code user}, the instruction as a program issues it ({@code from(<uri>)}, {@code to(<uri>)},
         * {@code eval?block(code)}), applied to {@code lhs} with the override's body. The call is the NATIVE one: the
         * user instruction bound to its native definition by the machine's {@link Binder}, exactly as resolution binds
         * it (generics bound to the lhs, args resolved, the call's own q-procs carried) — and then f() is the one thing
         * swapped. No argument is repacked and no seat moves, because the override has the signature of the instruction
         * it replaces. An instruction nothing native declares binds to its override's own signature instead.
         */
        public static Obj dispatch(final Inst override, final Inst user, final Obj lhs) {
            final Inst definition = Helper.nativeOf(user.tid().basePath());
            final Inst call = Binder.get().apply(lhs, user, null == definition ? override : definition);
            return null == call
                    ? fail("unable to bind %s to its definition at %s", user, lhs)
                    : call.f(override.f()).apply(lhs);
        }

        /**
         * EVALUATE — {@code code} on a peer's {@code eval} override: the native call {@code eval?block(code)} with the
         * override's body.
         * <p>
         * THE CODE IS A PAYLOAD, NOT A COMPUTATION. An inst's args are computed against the lhs before its function
         * runs, so unless the call BLOCKS the code is evaluated HERE and only its result is shipped: a read answers this
         * machine's value and a write lands in this machine's memory (measured across two hosts: on ginger,
         * *<ws://booger.local:8555/x/y> answered the value planted at ginger's own /x/y). ?block keeps the args raw --
         * the processor's own switch, exactly as bindQ applies it -- so the far side is the one that evaluates what it
         * is sent. {@code from} and {@code to} need no block: their argument is an address, which computes to itself.
         */
        public static Obj evaluate(final Inst eval, final Obj code) {
            final Inst user = instB(EVAL_INST_TID, lst(code));
            return Helper.dispatch(eval, user.tid(user.tid().addQ(BLOCK)), noobj());
        }

        /**
         * COMPILE-TIME BINDING (Marko, 2026-10-24) — {@code resolved}, a call the resolver has just bound to its native
         * definition, with the PEER'S body when its address names a dialed peer that overrides it: the address
         * instructions, {@code from(<uri>)} and {@code to(<uri>)}, whose literal uri argument carries an authority. The
         * signature, the binding and the typing are the native resolution's, untouched; f() is the one thing swapped,
         * so the compiled code shows which peer's body runs, and nothing is decided later inside {@link Network#read}
         * or {@link Network#write}. The body runs inside the network's q-proc layer ({@link AtPeer}). Every other call — another
         * instruction, an address computed at run time, an authority no peer here overrides — answers as resolved,
         * and a computed address still reaches the peer through the run-time dispatch.
         */
        public static Inst atPeer(final Inst resolved) {
            final fURI base = resolved.tid().basePath();
            final boolean read = FROM_INST_TID.equals(base);
            if (!read && !TO_INST_TID.equals(base))
                return resolved;
            final Obj address = resolved.arg(0);
            if (!address.isUri() || null == address.uriValue().host())
                return resolved;
            final Machine machine = Machine.current();
            if (null == machine || null == machine.network())
                return resolved;
            final fURI authority = address.uriValue().path(List.of());
            final Obj override = machine.network().overrideOf(authority, base);
            if (!override.isObjInst())
                return resolved;
            WIRE_LOG.debug("bound %s to the override %s declares", resolved, authority);
            return resolved.f(Inst.f.of(new AtPeer(machine.network(), authority, override.asInst().f(), read)));
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
            // client family (its eval override evaluates the shipped code against the local machine) --
            // discovery supplies dialers per description for everything else (step 5).
            final String scheme = dialURI.scheme();
            final String dialerTid = null == scheme ? null : Signatures.dialerOf(scheme);
            final fURI dialer = null != handler ? handler
                    : null == dialerTid ? null : f(dialerTid);
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
        static Rec probe(final Obj authority, final Obj reach) {
            final long start = System.currentTimeMillis();
            // A DIALED PEER IS NOT ITS TRANSPORT. The peer map's value is a peer::T that CARRIES one, and handing the
            // rec over as though it were the transport reported every dialed peer `known` (measured): the probe asks
            // what the peer::T holds. A peer whose link says it is down is down -- no round trip to learn it.
            final boolean dialed = reach.isRec()
                    && studio.phaseshift.metatron.isa.mach.machInstSet.PEER_TID.equals(reach.tid().basePath());
            final Obj transport = dialed ? reach.asRec().<Obj>atDirect(uri(TRANSPORT)) : reach;
            // AN ADDRESS IS NOT A TRANSPORT. A discovered machine is written as a uri (or a uri{+}), so applying
            // the PROBE inst to it answers noobj and reports a LIVE machine down. It has simply not been dialed:
            // that is "known", and "down" stays reserved for a transport that was tried and did not answer. A
            // peer::T with no transport is the same fact in the other spelling: an authority alone, known here and
            // not reachable from here.
            final boolean address = dialed ? !transport.isObjInst() : !transport.isNoObj() && !transport.isObjInst();
            boolean up = false;
            if (!address && (!dialed || Helper.online(reach))) {
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
