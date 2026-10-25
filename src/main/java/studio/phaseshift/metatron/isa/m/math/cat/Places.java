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

package studio.phaseshift.metatron.isa.m.math.cat;

import studio.phaseshift.metatron.algebra.rewrite.Placement;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Rel;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.Network;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static studio.phaseshift.metatron.Tokens.AUTHORITY;
import static studio.phaseshift.metatron.Tokens.CLIENT;
import static studio.phaseshift.metatron.Tokens.HOST;
import static studio.phaseshift.metatron.Tokens.LINKED_FROM;
import static studio.phaseshift.metatron.Tokens.LINKED_TO;
import static studio.phaseshift.metatron.Tokens.OBJ;
import static studio.phaseshift.metatron.Tokens.STATUS;
import static studio.phaseshift.metatron.isa.m.math.cat.catInstSet.PLACE_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * Places — the builder of {@link Place}: the read-through projection of the network's two tables into a
 * {@code place::T} vertex. The roster's {@code machine} field is the machine's <em>identity</em> (host → addresses);
 * the peer map is its <em>reach</em> (this machine's dials). {@link #place} reads {@code obj} and {@code authority}
 * from the roster once, then hangs three derived {@code auto} insts ({@code linked_to}/{@code linked_from}/
 * {@code status}) off the rec that re-read the live peer map each dereference — the {@code object::T.morphed_to}
 * pattern. The {@link Place} facade is what the graph hands out; this class only builds it.
 *
 * <p>The alias quotient is {@link Network.Helper#sameService}: a peer dialed at {@code 127.0.0.1} matches a place
 * whose roster row says {@code 0.0.0.0}, because both are the loopback service at the one port.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class Places {

    private Places() {
        // static only
    }

    /**
     * The {@code place::T} constructor: lift a machine address (a {@code //host[/path]} uri) into a {@link Place},
     * read its authorities from the roster and the peer map, and hang the three derived auto insts off it.
     * {@code noobj} when the argument is not a {@code //host[/path]} uri or nothing reaches that host.
     */
    public static Obj place(final Obj arg) {
        final Rec raw = arg.isRec() && arg.asRec().has(uri(OBJ)) ? arg.asRec() : rec(uri(OBJ), arg);
        final Obj obj = raw.atDirect(OBJ);
        final fURI machine = obj.isUri() ? obj.uriValue() : null;
        final Lst authorities = authorities(machine);
        if (0 == authorities.count())
            return noobj();
        final Map<Obj, Obj> jvm = new LinkedHashMap<>(raw.jvm());
        jvm.put(uri(OBJ), uri(machine));
        jvm.put(uri(AUTHORITY), authorities);
        jvm.put(uri(LINKED_TO), auto_(instLambda((ignore, i) -> linkedTo(machine))).tryToInst());
        jvm.put(uri(LINKED_FROM), auto_(instLambda((ignore, i) -> linkedFrom(machine))).tryToInst());
        jvm.put(uri(STATUS), auto_(instLambda((ignore, i) -> status(machine))).tryToInst());
        return new Place(jvm, PLACE_TID, null);
    }

    /**
     * The transports that reach a machine — the union of the two tables: the roster's {@code machine} row (the
     * addresses discovery announced, if any) and the peer map (the authorities this machine actually dialed to that
     * host). A machine known only by a dial is still a place: the dial *is* one of its transports. Deduped by
     * {@link fURI#basePath()} and sorted — a transport set has no order.
     */
    private static Lst authorities(final fURI machine) {
        if (null == machine || null == machine.host())
            return lst();
        final String host = machine.host();
        final Network network = Machine.current().network();
        final List<fURI> transports = new ArrayList<>();
        // the roster (known): a discovered row's addresses
        network.machines().elements()
                .filter(kv -> kv.first().isUri() && host.equals(kv.first().uriValue().toString()))
                .map(Rel::second)
                .forEach(value -> value.stream().filter(Obj::isUri).map(Obj::uriValue).forEach(transports::add));
        // the peer map (realized): the authorities this machine dialed to that host
        network.peers().values()
                .filter(Obj::isRec)
                .map(Network.Helper::authorityOf)
                .filter(Objects::nonNull)
                .filter(authority -> host.equals(authority.host()))
                .forEach(transports::add);
        final List<Obj> uris = transports.stream()
                .map(fURI::basePath)
                .distinct()
                .sorted(Comparator.comparing(fURI::toString))
                .map(a -> (Obj) uri(a))
                .toList();
        return lst(uris);
    }

    /**
     * This machine's own place — the {@code origin} a located {@code path::T} carries: the transports it answers on
     * (its listeners), never its dials. {@code noobj} when nothing listens (a program on a silent machine is not
     * placeable). The identity is the first authority's host as {@code //host}, the root frame.
     */
    public static Obj self() {
        final List<fURI> authorities = Network.Helper.authorities().stream()
                .sorted(Comparator.comparing(fURI::toString))
                .toList();
        if (authorities.isEmpty())
            return noobj();
        final String host = authorities.getFirst().host();
        if (null == host)
            return noobj();
        final Map<Obj, Obj> jvm = new LinkedHashMap<>();
        jvm.put(uri(OBJ), uri("//" + host));
        jvm.put(uri(AUTHORITY), lst(authorities.stream().map(a -> (Obj) uri(a)).toList()));
        return new Place(jvm, PLACE_TID, null);
    }

    /**
     * The places this machine can distribute code to — its dialed peers, one place per distinct host. {@code noobj}
     * when nothing is dialed (a distribution rule has no reach). The origin is deliberately NOT here: it is where the
     * code starts, not where it can go.
     */
    public static Obj reachable() {
        final Set<String> hosts = new LinkedHashSet<>();
        // the roster (known): every machine discovery declared, dialed or not — the candidate set is roster ∪ peers
        Machine.current().network().machines().elements()
                .filter(kv -> kv.first().isUri())
                .forEach(kv -> hosts.add(kv.first().uriValue().toString()));
        // the peer map (realized): the authorities this machine actually dialed (a dialed peer carries a host)
        Machine.current().network().peers().values()
                .filter(Obj::isRec)
                .filter(peer -> peer.asRec().has(uri(HOST)))
                .map(Network.Helper::authorityOf)
                .filter(Objects::nonNull)
                .map(fURI::host)
                .filter(Objects::nonNull)
                .forEach(hosts::add);
        final List<Obj> places = hosts.stream()
                .map(host -> place(uri("//" + host)))
                .filter(p -> !p.isNoObj())
                .toList();
        return places.isEmpty() ? noobj() : lst(places);
    }

    /**
     * The declared-liveness test (§4): a place is an admissible candidate when some authority's scheme has a dialer
     * that resolves in this machine's ISA. No dial is attempted — it reads the place's authority schemes and the ISA,
     * nothing live.
     */
    public static boolean admissible(final Obj place) {
        final Place p = Place.of(place.asRec());
        return p.authorities().stream().anyMatch(a -> Signatures.resolves(a.scheme()));
    }

    /**
     * The realized half of the liveness test (§4) — {@link #admissible} is the declared half: a dialed {@code peer}
     * can take {@code placement} when its client SERVES every instruction the placement requires
     * ({@link Placement#inst()}: a distributing shape ships through {@code to(<target>)} a program the target applies,
     * so it must serve {@code to} and {@code eval}) and is ONLINE — the client's own {@code status}
     * ({@link Network.Helper#online}). Serving is the client's mini-instset declaring an override the type system
     * accepts ({@link Network.Helper#overrideIn}). Read off this peer's own client — not the first peer of its service.
     */
    public static boolean serves(final Obj peer, final Placement placement) {
        if (!peer.isRec())
            return false;
        // what the client DECLARES first — a map read — and only then whether it is live: a status may be a round
        // trip (an http client's HEAD), and a client that cannot serve the placement is never worth asking
        final Obj client = peer.asRec().at(uri(CLIENT));
        return placement.inst().stream().allMatch(instTID -> Network.Helper.overrideIn(client, instTID).isObjInst())
                && Network.Helper.online(peer);
    }

    /**
     * The outbound edges — the peers this machine dialed to this place. A dialed peer carries a {@code host} (where
     * to dial back), which an inbound accept does not, so the {@code host} field is the out/in marker.
     */
    private static Obj linkedTo(final fURI machine) {
        return peersOf(machine, true);
    }

    /**
     * The inbound edges — the peers this machine accepted from this place (no {@code host}, an ephemeral dial-back).
     */
    private static Obj linkedFrom(final fURI machine) {
        return peersOf(machine, false);
    }

    private static Obj peersOf(final fURI machine, final boolean outbound) {
        final Lst authorities = authorities(machine);
        final List<Obj> peers = Machine.current().network().peers().values()
                .filter(Obj::isRec)
                .filter(peer -> outbound == peer.asRec().has(uri(HOST)))
                .filter(peer -> authorities.elements().anyMatch(a ->
                        Network.Helper.sameService(Network.Helper.authorityOf(peer), a.uriValue())))
                .toList();
        return objs(peers.toArray(Obj[]::new));
    }

    /**
     * The liveness lattice: {@code known} when the roster declares the machine but no dial reaches it, else
     * {@code up}/{@code down} from the dialed peer's own {@link Network.Helper#online}.
     */
    private static Obj status(final fURI machine) {
        final Lst authorities = authorities(machine);
        final Obj peer = Machine.current().network().peers().values()
                .filter(Obj::isRec)
                .filter(p -> authorities.elements().anyMatch(a ->
                        Network.Helper.sameService(Network.Helper.authorityOf(p), a.uriValue())))
                .findFirst().orElse(noobj());
        return peer.isNoObj() ? uri("known") : uri(Network.Helper.online(peer) ? "up" : "down");
    }
}
