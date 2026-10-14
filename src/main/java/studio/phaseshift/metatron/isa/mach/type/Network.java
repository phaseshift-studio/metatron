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
        if (!vid.hasAuthority())
            return noobj();
        final Obj transport = this.transportOf(vid.path(List.of()));
        if (transport.isNoObj())
            return noobj(); // authority not declared: not a peer this frame may reach
        // A ws:// ADDRESS IS A WIRE ADDRESS, NOT A PLACE IN THIS MACHINE. Reading one asks the PEER for that path —
        // the frame that stands in the peer has a local address of its own (/network/peer/<scheme>/<host>/<port>/<ns>,
        // derived on push), and moving to it is an ordinary local read of a machine. So nothing here maps an
        // authority to a frame: this is the data path, and only the data path.
        final Obj message = from_(vid.localize().toUri()).tryToInst();
        return transport.apply(message);
    }

    /**
     * Authority dispatch — write {@code obj} to a declared peer's destination-relative address via its
     * transport. Same local/undeclared fall-through as {@link #read(fURI)}.
     */
    default Obj write(final fURI vid, final Obj obj) {
        if (!vid.hasAuthority())
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
        public static boolean sameService(final fURI a, final fURI b) {
            return a.authority().equals(b.authority())
                    || (isLoopbackHost(a.host()) && isLoopbackHost(b.host()) && a.port() == b.port());
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
