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

package studio.phaseshift.metatron.isa.m.mach.type.network;

import studio.phaseshift.metatron.furi.QProc;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.AbstractSpace;
import studio.phaseshift.metatron.isa.m.mach.type.Component;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.Network;
import studio.phaseshift.metatron.isa.m.mach.type.analyzer.Analyzer;
import studio.phaseshift.metatron.isa.m.mach.type.analyzer.BasicAnalyzer;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.net.InetAddress;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mach.machInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * BasicNetwork — one level of a machine's network, and that level's address space: a {@link memSpace} is its store
 * (its {@code sjvm}), behind a space that intercepts what goes through it (the {@code dckrSpace} pattern).
 * <p>
 * <b>EVERYTHING THE LEVEL KNOWS IS AN ENTRY IN ITS STORE</b>, at an address relative to the level — so it is held
 * before the level has a machine — and is read as any memSpace's data is ({@code locateBasePoly}, {@code unrollPoly},
 * containers, q-procs): {@code host}, {@code status}, the root's {@code roster}, the {@code prefix} table, and the two
 * member maps, {@code peer} and {@code listener}, each ONE entry keyed by its members' path encodings
 * ({@code ws/localhost/8555/mtron => peer::T}). A path-keyed rec is addressed through its keys as in any space, so a
 * member and its fields are read at their keys' paths and the uri tree walks a map a segment at a time.
 * {@link #mount} stamps the branch, {@code <vid>/network/#}, and the machine's own space
 * ({@link studio.phaseshift.metatron.isa.m.mach.type.machine.MachineSpace}) hands every address below it here:
 * <pre>
 *   *</network/roster>                                   [-- what the system knows --]
 *   *<~/network/peer>                                    [-- what this machine reaches: <key> => peer::T --]
 *   *<~/network/peer/ws/localhost/8555/mtron>            [-- the peer::T --]
 *   *<~/network/peer/ws/localhost/8555/mtron/origin>     [-- one of its fields --]
 *   </network>.>>.>>.>>                                   [-- the level's uri tree, through the maps' keys --]
 *   </network/peer/ws/localhost/8555/mtron> -> noobj      [-- hang up: the member leaves the map --]
 *   </network/peer/#> -> noobj                            [-- hang up every member --]
 *   *<~/network/listener/ws/localhost/8555/route>        [-- this machine's routes on its socket --]
 *   <~/network/listener/ws/localhost/8555> -> noobj       [-- stop serving --]
 * </pre>
 * <b>WHAT IT INTERCEPTS.</b> An address that is not this level's — another machine's, carrying a host — is the
 * authority dispatch ({@link Network#read}, {@link Network#write}). A MEMBER (a peer, a listener) is constructed,
 * never written: a client indexes itself and a server listens, each putting a typed member into its map at its key,
 * LITERALLY, because a key is an encoded origin and may be a wildcard (the web gateway's {@code http/#}); and a
 * member's deletion is its retraction, since a member is a rec and closing a rec closes nothing it holds. The NAME
 * TABLE is a door: it reads what the frame sees and stores what the caller declares (AGENTS.md: a door reads meaning,
 * a write declares differences).
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class BasicNetwork extends AbstractSpace<memSpace> implements Network {

    /**
     * the pattern a network carries until it is mounted: the network branch of the machine it serves, unresolved. It
     * claims no address (a resolved vid never begins with {@code ~}), and {@link #mount} stamps the resolved branch.
     */
    private static final fURI UNMOUNTED = f("~/network/#");

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
        // THE WIRE'S METERS: every level carries its own analyzer, exactly as a processor does. The frame taps count
        // into it where the frame crosses (WebSocketObj.send; wsServer.evaluate and WebSocketRecClient.readFrame as a
        // frame arrives), and the machine's space answers it at
        // ~/network/analyzer. It is a CONSTRUCTED component field, never a config the caller supplies -- a network
        // with no meters could not record the sockets it holds, and every wire read would throw in the tap.
        jvm.putIfAbsent(uri(ANALYZER), new BasicAnalyzer(NETWORK));
        return jvm;
    }

    public BasicNetwork(final Map<Obj, Obj> jvm) {
        // never self-registered: a network is answered through its machine's space (mount), not by being constructed --
        // most are built before their machine exists. Its STORE is a memSpace of its own, unregistered for the same
        // reason: what this level knows, at addresses relative to it
        super(memSpace.unregistered(ALL, null), addFields(jvm), MACH_NETWORK_TID, null, false);
        // THE Q-PROCS A NETWORK SUPPORTS. subq is the one that matters: a subscription to a peer's namespace is LOCAL
        // bookkeeping -- `ws://peer/x/#?subq -> code` must register here and fire here -- and the layer (QProc.Layer,
        // applied by Machine.write) is what consumes the registration before it can reach the wire. The store speaks
        // the same ones, so a subscription below the branch is kept in the same registry.
        this.addQ(QCollection.subQ());
        this.qs().lstValue().forEach(q -> this.sjvm.addQ(q.as()));
        // THE REC IS THE SPACE'S CONFIGURATION; what the level was constructed with goes into the store, each field at
        // its own address and through the door every other write takes. The ANALYZER stays in the rec, as every
        // component's does: the wire taps count into it per frame, and the machine's space answers it
        final Set<fURI> config = Set.of(f(PATTERN), f(QPROC), f(ANALYZER));
        this.jvm().entrySet().stream()
                .filter(field -> field.getKey().isUri() && !config.contains(field.getKey().uriValue()))
                .toList()
                .forEach(field -> {
                    this.jvm().remove(field.getKey());
                    this.write(field.getKey().uriValue(), field.getValue());
                });
        // the status METHOD is an entry like any other: `*/network/status` evaluates it, closing over this level
        this.write(f(STATUS), this.status());
        this.analyzer(this.analyzer());
    }

    /**
     * THE PEERS THIS LEVEL HOLDS, a gauge sampled whenever the analysis is read, registered with each analyzer the level
     * is given (one written at {@code ~/network/analyzer} after it was switched off too): a peer comes and goes by a
     * dial, a drop, a redial, an inbound co-mount or the supervisor, and a sampled gauge can miss none of them -- so a
     * bare connect, before any frame crosses, already shows in the analysis
     */
    @Override
    public Analyzer.Aggregator analyzer(final Analyzer analyzer) {
        Network.super.analyzer(analyzer);
        Analyzer.Helper.gauge(analyzer, PEER_GAUGE, () -> this.peers().elements().count());
        return this;
    }

    /**
     * Mount this level as {@code machine}'s network branch, {@code <vid>/network/#}: the pattern is stamped resolved, and
     * the branch is answered through the machine's own space
     * ({@link studio.phaseshift.metatron.isa.m.mach.type.machine.MachineSpace}), which hands every address below it to
     * this level — the network is not a space in memory. A pushed frame's network answers for that frame alone and
     * leaves with it, as its space does.
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
     * the address below which this level answers ({@code <vid>/network})
     */
    private fURI branch() {
        return this.pattern().retractPattern().asNode();
    }

    /**
     * the store's address for {@code vid}: a relative one as it stands (the form the level's own methods use), one
     * below the branch with the branch taken off, or null for an address that is not this level's — another
     * machine's, or {@code ~}, which is the machine's address and not the component's
     */
    protected fURI stored(final fURI vid) {
        if (vid.isRelative())
            return vid.hasPrefix(f("~")) ? null : vid;
        final fURI at = vid.qLess().resolve();
        final fURI branch = this.branch();
        return at.hasPrefix(branch) && !at.equals(branch) ? at.removePrefix(branch).q(vid.qMap()) : null;
    }

    /**
     * the address the caller asked in: a store address named below the branch again, unless the caller asked relative
     */
    protected fURI asked(final fURI vid, final fURI at) {
        return vid.isRelative() ? at : this.branch().extend(at);
    }

    // ──────────────────────────────────────────────
    // reads: the store's, but for the name table's door
    // ──────────────────────────────────────────────

    @Override
    public Obj read(final fURI vid) {
        final fURI at = this.stored(vid);
        if (null == at)
            return this.reach(vid, () -> Network.super.read(vid), listener -> listener.read(vid));
        // THE TABLE ANSWERS WHAT THIS FRAME SEES, shadowing included -- the question a reader standing in a frame
        // actually asks: the frame's OWN table may be empty while the root's names resolve there. Marked, because a
        // name is spoken `//ginger`; the stored spelling is one strip away (prefixes()), and the write below makes
        // read-then-write a no-op by dropping inherited rows.
        if (f(PREFIX).equals(at))
            return Network.Helper.markedTable(Component.Prefix.Helper.effectivePrefixes(this));
        // THE q-PROCS ARE THE REC'S, not the store's: they are a config field (addQ put them at `q`), so
        // `@~/network/q` must read the list the layer actually consults
        if (f(QPROC).equals(at))
            return this.at(uri(QPROC));
        // EVERYTHING ELSE IS THE STORE'S, read as any space's data is: a member map is one entry keyed by its members'
        // path encodings, so the map, a member and a member's field are each read at their own address
        // (Space.Helper#unrollPoly walks a path key like any other)
        final Obj read = this.sjvm.read(at);
        // a branch read answers rels at the store's addresses: they are named where the caller asked (what @ anchors with)
        return vid.isBranch() && !vid.isRelative()
                ? objs(read.stream().map(kv -> rel(uri(this.asked(vid, kv.asRel().first().uriValue())), kv.asRel().second())))
                : read;
    }

    /**
     * A WIRE ADDRESS THIS LEVEL DOES NOT KEEP (see {@link #stored}) — what answers it, in order: a query the local
     * socket DECLARES (a socket's own meters are not a peer's to answer), then a declared PEER (an explicit reach into
     * the origin, which is what {@code ws://host:port/path} has always meant), then a socket THIS machine binds at that
     * origin (what no peer serves is the local server's). That last rung is why {@code *<origin>/#?analq} is the
     * server's own analysis rather than a noobj. The address never dials — a read never dials, and the local socket is
     * the authority standing at it. An address with no host is nobody's wire and declines untouched.
     */
    private Obj reach(final fURI vid, final Supplier<Obj> authority, final Function<Listener, Obj> local) {
        if (null == vid.host())
            return authority.get();
        final Listener listener = Network.Helper.listenerFor(vid.origin());
        // A QUERY THE SOCKET DECLARES IS THE SOCKET'S TO ANSWER, before any peer is asked: *<origin>/#?analq asks THIS
        // machine's server for its own analysis, and shipping the question to a peer would be asking the peer for the
        // meters of a socket it does not hold.
        if (null != listener && vid.hasQ() && QProc.Helper.declaresQProcs(listener, vid))
            return local.apply(listener);
        final Obj dispatched = authority.get();
        if (!dispatched.isNoObj() || null == listener)
            return dispatched;
        return local.apply(listener);
    }

    @Override
    public Function<fURI, Iterator<IdObj>> directReader() {
        return vid -> {
            final fURI at = this.stored(vid);
            return null == at ? IteratorUtil.of() : IteratorUtil.map(this.sjvm.directReader().apply(at), id -> IdObj.of(this.asked(vid, id.furi()), id.obj()));
        };
    }

    // ──────────────────────────────────────────────
    // writes: intercepted, then the store's
    // ──────────────────────────────────────────────

    @Override
    public BiFunction<fURI, Obj, Obj> directWriter() {
        return this::write;
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        final fURI at = this.stored(vid);
        if (null == at)
            return obj.isFail() // a fail is not shippable, and no socket serves it either: it stays local
                    ? noobj()
                    : this.reach(vid, () -> Network.super.write(vid, obj), listener -> listener.write(vid, obj));
        // a q-proc's registration is the store's, which speaks this level's q-procs
        if (at.hasQ())
            return this.sjvm.write(at, obj);
        // THE q-PROCS ARE THE REC'S: `@~/network/q >>= …` declares the level's processors, and the layer reads them
        // from here. The store mirrors them so a registration below a branch speaks the same set.
        if (f(QPROC).equals(at)) {
            this.at(uri(QPROC), obj, MUTABLE);
            this.sjvm.at(uri(QPROC), obj, MUTABLE);
            return obj;
        }
        final String field = at.segments().isEmpty() ? "" : at.segments().getFirst();
        final boolean members = PEER.equals(field) || LISTENER.equals(field);
        if (obj.isNoObj()) {
            // A MEMBER'S DELETION IS ITS RETRACTION, from the map that holds it. An address the map's own address
            // matches (the bare map, or a pattern over the level) names every member the map holds; else the member at
            // exactly this key is the one it names (literal first: the gateway's http/# is a key, not a pattern); else
            // every member the address matches as a pattern. Each leaves its map under the level's lock -- the one the
            // peer mint and the listener mount hold -- and what it held is released outside it: clear, then close.
            final List<Obj> retracted = new ArrayList<>();
            synchronized (this) {
                for (final String map : List.of(PEER, LISTENER)) {
                    final fURI root = f(map);
                    final Map<Obj, Obj> kept = new LinkedHashMap<>(this.map(map).jvm());
                    final boolean whole = root.test(at);
                    final boolean exact = kept.keySet().stream().anyMatch(key -> root.extend(key.uriValue()).equals(at));
                    if (kept.entrySet().removeIf(row -> {
                        final fURI member = root.extend(row.getKey().uriValue());
                        return (whole || (exact ? member.equals(at) : member.test(at))) && retracted.add(row.getValue());
                    })) {
                        // AN EMPTY MAP IS AN ABSENT FIELD
                        if (kept.isEmpty())
                            this.sjvm.sjvm().remove(root);
                        else
                            this.sjvm.sjvm().put(root, rec(kept));
                    }
                }
            }
            retracted.forEach(member -> Network.Helper.release(this, member));
            // a member map takes nothing but its members' retraction (a field of a member is not deleted from mtron)
            return members ? obj : this.sjvm.write(at, obj);
        }
        if (members) {
            // A MEMBER IS CONSTRUCTED, NOT WRITTEN: a client indexes itself at its key and a server listens at its
            // socket, each putting its typed member into its map there AS IT STANDS -- a key is an encoded origin and may
            // be a wildcard (the web gateway's http/#), so it is a key of the map and never a path the store fans out.
            // The map is kept in key order, because a fan-out lays its lanes out in the order it reads them. Anything
            // else written here (a field edit included) is refused rather than stored beside the member it would
            // contradict.
            if (1 == at.segments().size() || !obj.testNominally(PEER.equals(field) ? PEER_TYPE : LISTENER_TYPE)) {
                LOG.warn("a %s is constructed, not written: %s -> %s refused", field, vid, obj);
                return noobj();
            }
            final fURI root = f(field);
            final fURI key = at.removePrefix(root);
            synchronized (this) {
                final Map<Obj, Obj> rows = new LinkedHashMap<>();
                Stream.concat(this.map(field).jvm().entrySet().stream().filter(row -> !row.getKey().uriValue().equals(key)),
                                Stream.of(Map.entry((Obj) uri(key), obj)))
                        .sorted(Comparator.comparing((Map.Entry<Obj, Obj> row) -> row.getKey().uriValue().toString()))
                        .forEach(row -> rows.put(row.getKey(), row.getValue()));
                this.sjvm.sjvm().put(root, rec(rows));
            }
            return obj;
        }
        if (f(PREFIX).equals(at)) {
            // THE DIALECT, TRANSLATED AT THE ONE DOOR. A prefix is spelled //ginger and stored ginger, so the table the
            // caller wrote is STRIPPED here, once, and a row that carries no marker is REFUSED rather than stored under
            // a spelling resolution will never ask for. A row that merely RESTATES an inherited meaning is dropped: the
            // door reads the effective view, so a table written back carries every ancestor's row, and storing one
            // would shadow the ancestor's declaration from then on.
            final Optional<Rec> stored = Network.Helper.storedTable(obj);
            if (stored.isEmpty()) {
                LOG.warn("a network prefix is spelled //name: %s -> %s refused (a //host, no scheme, no port)", vid, obj);
                return noobj();
            }
            final Rec own = rec(stored.get().elements()
                    .filter(row -> !row.second().equals(Component.Prefix.Helper.inheritedPrefix(this, row.first().uriValue()))));
            // AN EMPTY TABLE IS AN ABSENT FIELD. The answer is the caller's obj: `x -> y` reports y, markers and all.
            this.sjvm.write(at, own.isEmpty() ? noobj() : own);
            return obj;
        }
        return this.sjvm.write(at, obj);
    }

    // ──────────────────────────────────────────────
    // the level's own maps, read off its store
    // ──────────────────────────────────────────────

    @Override
    public Rec peers() {
        return this.map(PEER);
    }

    @Override
    public Rec listeners() {
        return this.map(LISTENER);
    }

    @Override
    public Rec prefixes() {
        return this.map(PREFIX);
    }

    /**
     * one of this level's maps as its store holds it — {@code peer} and {@code listener} as {@code <key> => member} in
     * key order, {@code prefix} as this level's own rows — or the empty rec when the level holds none. Read off the
     * store and not through the space's read, because the accessors answer this level's entry, never a door's view.
     */
    private Rec map(final String field) {
        final Obj map = this.sjvm.sjvm().get(f(field));
        return null != map && map.isRec() ? map.asRec() : rec();
    }

    /**
     * Release what this level dialed and serves. The reach and presence go first ({@link Network#close()}: supervision
     * released, every member retracted — which is what hangs up a client and withdraws a route), then what the store
     * still holds.
     */
    @Override
    public void close() {
        Network.super.close();
        this.sjvm.sjvm().forEach((at, value) -> CommonUtil.close(value));
        this.sjvm.sjvm().clear();
    }
}
