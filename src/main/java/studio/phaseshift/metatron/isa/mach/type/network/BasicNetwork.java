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

package studio.phaseshift.metatron.isa.mach.type.network;

import studio.phaseshift.metatron.furi.QProc;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.AbstractSpace;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.Network;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.net.InetAddress;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_NETWORK_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * BasicNetwork — one level of a machine's network, and that level's address space.
 * <p>
 * It holds <b>no state in Java fields</b>: the component <em>is</em> this rec — the peers this machine has dialed, the
 * sockets it serves through, its roster and the rest are its own fields — so mtron reads and writes the network of a
 * frame directly and nothing the boundary acts on is hidden from reflection.
 * <p>
 * <b>THE COMPONENT ANSWERS ITS OWN BRANCH.</b> {@link #mount} stamps it as {@code <vid>/network/#} (each machine's, at
 * bootstrap), and the machine's own sphere ({@link studio.phaseshift.metatron.isa.mach.type.machine.MachineSpace} —
 * the first thing {@code Machine.read}/{@code write} ask, never a space in memory) hands every address under the branch
 * here, where it is answered from this rec as it stands: the space STORES NOTHING beside it. A field sits at
 * {@code <vid>/network/<field>} and a member of a map the
 * network owns at its key's path; everything deeper is the unrolling every space gets ({@link Space.Helper#resolveRead}):
 * <pre>
 *   *</network/peer/ws/localhost/8555/mtron>             [-- the peer::T --]
 *   *</network/peer/ws/localhost/8555/mtron/origin>   [-- one of its fields --]
 *   *</network/peer/ws/#>                                [-- every ws member --]
 *   </network/peer/ws/localhost/8555/mtron> -> noobj     [-- hang up: the member leaves the map --]
 *   *</network/listener/ws/localhost/8555/route>         [-- this machine's routes on its socket --]
 *   </network/listener/ws/localhost/8555> -> noobj       [-- stop serving: the routes leave, an owned socket closes --]
 * </pre>
 * <b>A MEMBER IS CONSTRUCTED, NOT WRITTEN.</b> A client indexes itself at its key and a server listens at its socket,
 * so the only write the {@code peer} and {@code listener} branches take is the deletion — the network's own retraction
 * ({@link Network#disconnect}, {@link Network#unlisten}). A value written there (a field edit included) is refused,
 * loudly, rather than stored beside the member it would contradict. Every other field is written onto this rec IN
 * PLACE, which is what makes the root's {@code /network/roster} the root network's own field: the generic write path
 * rebuilds the poly above a missing address as a copy, and the machine holds this one.
 * <p>
 * <b>ONE READ, TWO JURISDICTIONS.</b> An address under the mounted branch is the space's; every other address is the
 * authority dispatch ({@link Network#read}, {@link Network#write}) — the remote ladder {@code Machine.read} hands each
 * host-bearing address before memory sees it. The gate is the branch, not the host: a machine pushed to a wire
 * address carries a host in its own vid.
 * <p>
 * Closing the level ends its reach and presence ({@link Network#close()}) and empties it; unmounting is the frame's.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class BasicNetwork extends AbstractSpace<Object> implements Network {

    /**
     * the pattern a network carries until it is mounted: the network branch of the machine it serves, unresolved. It
     * claims no address (a resolved vid never begins with {@code ~}), and {@link #mount} stamps the resolved branch.
     */
    private static final fURI UNMOUNTED = f("~/network/#");

    /**
     * A map the network OWNS: its members as the component holds them, the retraction a member's deletion is, and why
     * a value written there is refused.
     */
    private record Branch(String field, Function<Network, Rec> members, BiConsumer<Network, fURI> retract,
                          String refusal) {
    }

    private static final List<Branch> BRANCHES = List.of(
            new Branch(PEER, Network::peers, Network::disconnect,
                    "a peer is constructed, not written: %s -> %s refused (dial a client: ws_client::[pattern=>...])"),
            new Branch(LISTENER, Network::listeners, BasicNetwork::unlisten,
                    "a listener is constructed, not written: %s -> %s refused (a server listens at its socket)"));

    public BasicNetwork() {
        this(mutableMap());
    }

    private static Map<Obj, Obj> addFields(final Map<Obj, Obj> jvm) {
        try {
            jvm.putIfAbsent(uri(HOST), uri(InetAddress.getLocalHost().getCanonicalHostName()));
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
        jvm.putIfAbsent(uri(PATTERN), uri(UNMOUNTED));
        return jvm;
    }

    public BasicNetwork(final Map<Obj, Obj> jvm) {
        // never self-registered: a network is mounted by the machine it serves (mount), not by being constructed --
        // most are built before their machine exists
        super(null, addFields(jvm), MACH_NETWORK_TID, null, false);
        // the status METHOD is a field of the component, not a Java-only accessor: `*/network/status` must reach
        // it, so the inst is installed on the rec it closes over (and invoked there with `()`). On the rec's own
        // map: the space saved its pattern through a fresh one, so the map handed in is no longer the rec's.
        this.jvm().putIfAbsent(uri(STATUS), this.status());
        // THE Q-PROCS A NETWORK SUPPORTS. subq is the one that matters: a subscription to a peer's namespace is
        // LOCAL bookkeeping — `ws://peer/x/#?subq -> code` must register here and fire here — and the layer
        // (QProc.Layer, applied by Machine.write) is what consumes the registration before it can reach the wire.
        this.addQ(QCollection.subQ());
    }

    /**
     * Mount this level as {@code machine}'s network branch, {@code <vid>/network/#}: the pattern is stamped resolved, and
     * the branch is answered through the machine's own sphere
     * ({@link studio.phaseshift.metatron.isa.mach.type.machine.MachineSpace}), which hands every address under it to
     * this level — the network is not a space in memory. A pushed frame's network answers for that frame alone and
     * leaves with it, as its sphere does.
     */
    public BasicNetwork mount(final Machine machine) {
        this.at(uri(PATTERN), uri(machine.vid().resolve().extend(NETWORK).extend(ALL)), MUTABLE);
        return this;
    }

    /**
     * the branch this level answers for, as its pattern says — the construction-time pattern once the level is
     * closed, so the index that still lists it can name it
     */
    @Override
    public fURI pattern() {
        final Obj pattern = this.at(PATTERN);
        return pattern.isUri() ? pattern.uriValue() : this.pattern;
    }

    /**
     * Release what this level dialed and serves. The reach and presence go first ({@link Network#close()}: supervision
     * released, every member disconnected — which is what hangs up a client, since a {@code peer::T} is a rec and
     * closing the rec closes nothing it holds — and every route withdrawn), then the level's own fields.
     */
    @Override
    public void close() {
        Network.super.close();
        this.jvm().values().forEach(CommonUtil::close);
        this.jvm().clear();
    }

    // ──────────────────────────────────────────────
    // one read, two jurisdictions
    // ──────────────────────────────────────────────

    @Override
    public Obj read(final fURI vid) {
        final Obj read = this.answers(vid) ? super.read(vid) : Network.super.read(vid);
        // A NAME IS SPELLED //ginger AND STORED ginger: the door answers with the spelling the table is WRITTEN with,
        // so what `*</network/prefix>` shows is what may be written back. Network#prefixes reads the stored form --
        // that is the key resolution asks for -- so the two accessors are one strip apart and neither drifts.
        return this.isPrefixTable(vid) && read.isRec() ? Network.Helper.markedTable(read.asRec()) : read;
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        if (!this.answers(vid))
            return Network.super.write(vid, obj);
        // THE DIALECT, TRANSLATED AT THE ONE DOOR. A prefix is spelled //ginger and stored ginger, so the table the
        // caller wrote is STRIPPED here, once, and a row that carries no marker is REFUSED rather than stored under a
        // spelling resolution will never ask for -- which is exactly the silent, unresolvable table reported from the
        // console. A noobj write is the universal close and passes through untouched.
        final boolean table = !obj.isNoObj() && this.isPrefixTable(vid);
        final Optional<Rec> stored = table ? Network.Helper.storedTable(obj) : Optional.empty();
        if (table && stored.isEmpty()) {
            LOG.warn("a network prefix is spelled //name: %s -> %s refused (a //host, no scheme, no port)", vid, obj);
            return noobj();
        }
        // WHAT IS STORED IS THE STRIPPED TABLE THE CALLER WROTE -- never the table already here: writing this rec
        // onto itself would accept every write and store none of them, which is the other half of the reported
        // silence (the write echoed its obj while the table stayed empty).
        final Obj value = table ? stored.get() : obj;
        // the space's q-procs and its layer, as every space write — but onto THIS rec: Space.Helper.resolveWrite
        // rebuilds the poly above a missing address as a copy, and the machine holds this one
        QProc.Helper.checkQProcs(this, vid);
        // THE LAYER SEES WHAT THE CALLER WROTE, the rec sees the form it stores, and the ANSWER is the caller's obj:
        // `x -> y` reports y, so the table keeps its markers on the way out as well as on the way in.
        return QProc.Layer.write(this, vid, obj, () -> {
            if (vid.hasPattern())
                this.readStream(vid).toList().forEach(at -> this.directWriter().apply(at.furi(), value));
            else
                this.directWriter().apply(vid, value);
            return obj;
        });
    }

    /**
     * whether an address is the name TABLE rather than one of its rows: {@code <vid>/network/prefix}.
     */
    private boolean isPrefixTable(final fURI vid) {
        return this.branch().extend(PREFIX).equals(vid.qLess().resolve().asNode());
    }

    /**
     * the branch an address must fall under to be this space's ({@code <vid>/network})
     */
    private fURI branch() {
        return this.pattern().retractPattern().asNode();
    }

    private boolean answers(final fURI vid) {
        return vid.qLess().resolve().hasPrefix(this.branch());
    }

    // ──────────────────────────────────────────────
    // the branch, answered from the rec as it stands
    // ──────────────────────────────────────────────

    @Override
    public Function<fURI, Iterator<IdObj>> directReader() {
        return qpattern -> {
            final fURI asked = qpattern.qLessExceptDomRng().resolve();
            // a relative address is one beneath the branch: navigating the component (*~/network>>peer) hands the
            // space the step, not an address
            final fURI pattern = (asked.isAbsolute() || asked.hasScheme() ? asked : this.branch().extend(asked)).asNode();
            return this.addresses().filter(at -> at.furi().test(pattern)).iterator();
        };
    }

    /**
     * every address this level answers at: itself at its branch, each field beneath it, and each member of a map it
     * owns at its key's path — the key is one path of several segments, which no unrolling of the map would find
     */
    private Stream<IdObj> addresses() {
        final fURI branch = this.branch();
        return Stream.concat(Stream.of(IdObj.of(branch, this)), this.jvm().entrySet().stream()
                .filter(field -> field.getKey().isUri() && !field.getValue().isNoObj())
                .flatMap(field -> {
                    final fURI at = branch.extend(field.getKey().uriValue());
                    final Stream<IdObj> itself = Stream.of(IdObj.of(at, field.getValue()));
                    return null == owned(field.getKey().uriValue().toString()) || !field.getValue().isRec()
                            ? itself
                            : Stream.concat(itself, field.getValue().asRec().elements()
                            .filter(member -> member.first().isUri())
                            .map(member -> IdObj.of(at.extend(member.first().uriValue()), member.second())));
                }));
    }

    @Override
    public BiFunction<fURI, Obj, Obj> directWriter() {
        return (vid, obj) -> {
            final fURI at = vid.qLess().resolve().asNode();
            final fURI branch = this.branch();
            if (at.equals(branch)) {
                LOG.warn("a network is its machine's component, not a value: %s -> %s refused (write its fields)", at, obj);
                return noobj();
            }
            final fURI field = at.removePrefix(branch);
            final Branch owned = owned(field.segments().getFirst());
            if (null == owned) {
                // a field this level keeps, set in place; a deletion closes what it displaces, as a space's does
                synchronized (this) {
                    Network.Helper.writeComplete(obj, this.atDirect(uri(field)));
                    this.at(uri(field), obj, MUTABLE);
                }
                return obj;
            }
            if (!obj.isNoObj()) {
                LOG.warn(owned.refusal(), at, obj);
                return noobj();
            }
            final fURI root = branch.extend(owned.field());
            if (at.equals(root))
                owned.members().apply(this).keys().filter(Obj::isUri).map(Obj::uriValue).toList()
                        .forEach(key -> owned.retract().accept(this, key));
            else
                owned.retract().accept(this, at.removeSubpath(root).toPath());
            return obj;
        };
    }

    /**
     * the map the network owns at a field, or null for a field it merely keeps
     */
    private static Branch owned(final String field) {
        for (final Branch branch : BRANCHES)
            if (branch.field().equals(field))
                return branch;
        return null;
    }

    /**
     * a listener's deletion: its key names this level's entry, and the entry's authority is what the level stops
     * serving on
     */
    private static void unlisten(final Network network, final fURI key) {
        final fURI authority = Network.Helper.originOf(network.listenerAt(key));
        if (null != authority)
            network.unlisten(authority);
    }
}
