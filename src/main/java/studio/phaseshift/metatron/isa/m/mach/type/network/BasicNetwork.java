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

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.AbstractSpace;
import studio.phaseshift.metatron.isa.m.mach.type.Component;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.Network;
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
 * BasicNetwork — one level of a machine's network, and that level's address space: a {@link memSpace} {@code cache}
 * behind a space that intercepts what goes through it (the {@code dckrSpace} pattern).
 * <p>
 * <b>EVERYTHING THE LEVEL KNOWS IS AN ENTRY IN ITS CACHE</b>, at an address relative to the level — so it is held
 * before the level has a machine — and is read as any memSpace's data is ({@code locateBasePoly}, {@code unrollPoly},
 * containers, q-procs). {@link #mount} stamps the branch, {@code <vid>/network/#}, and the machine's own space
 * ({@link studio.phaseshift.metatron.isa.m.mach.type.machine.MachineSpace}) hands every address below it here:
 * <pre>
 *   *</network/roster>                                   [-- what the system knows --]
 *   *<~/network/peer/ws/localhost/8555/mtron>            [-- the peer::T --]
 *   *<~/network/peer/ws/localhost/8555/mtron/origin>     [-- one of its fields --]
 *   </network/peer/ws/localhost/8555/mtron> -> noobj      [-- hang up: the member leaves the cache --]
 *   </network/peer/#> -> noobj                            [-- hang up every member --]
 *   *<~/network/listener/ws/localhost/8555/route>        [-- this machine's routes on its socket --]
 *   <~/network/listener/ws/localhost/8555> -> noobj       [-- stop serving --]
 * </pre>
 * <b>WHAT IT INTERCEPTS.</b> An address that is not this level's — another machine's, carrying a host — is the
 * authority dispatch ({@link Network#read}, {@link Network#write}). A MEMBER (a peer, a listener) is constructed,
 * never written: a client indexes itself and a server listens, each putting a typed member at its key, LITERALLY,
 * because a key is an encoded origin and may be a wildcard (the web gateway's {@code http/#}); and a member's deletion
 * is its retraction, since a member is a rec and closing a rec closes nothing it holds. The NAME TABLE is a door: it
 * reads what the frame sees and stores what the caller declares (AGENTS.md: a door reads meaning, a write declares
 * differences).
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
     * what this level knows, at addresses relative to it: unregistered, so it is no mount of its own, and speaking this
     * level's q-procs
     */
    private final memSpace cache;

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
        // never self-registered: a network is answered through its machine's space (mount), not by being constructed --
        // most are built before their machine exists
        super(null, addFields(jvm), MACH_NETWORK_TID, null, false);
        this.cache = memSpace.unregistered(ALL, null);
        // THE Q-PROCS A NETWORK SUPPORTS. subq is the one that matters: a subscription to a peer's namespace is LOCAL
        // bookkeeping -- `ws://peer/x/#?subq -> code` must register here and fire here -- and the layer (QProc.Layer,
        // applied by Machine.write) is what consumes the registration before it can reach the wire. The cache speaks
        // the same ones, so a subscription below the branch is kept in the same registry.
        this.addQ(QCollection.subQ());
        this.qs().lstValue().forEach(q -> this.cache.addQ(q.as()));
        // THE REC IS THE SPACE'S CONFIGURATION; what the level was constructed with goes into the cache, each field at
        // its own address and through the door every other write takes
        final Set<fURI> config = Set.of(f(PATTERN), f(QPROC));
        this.jvm().entrySet().stream()
                .filter(field -> field.getKey().isUri() && !config.contains(field.getKey().uriValue()))
                .toList()
                .forEach(field -> {
                    this.jvm().remove(field.getKey());
                    this.write(field.getKey().uriValue(), field.getValue());
                });
        // the status METHOD is an entry like any other: `*/network/status` evaluates it, closing over this level
        this.write(f(STATUS), this.status());
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
     * the cache's address for {@code vid}: a relative one as it stands (the form the level's own methods use), one
     * below the branch with the branch taken off, or null for an address that is not this level's — another
     * machine's, or {@code ~}, which is the machine's address and not the component's
     */
    private fURI cached(final fURI vid) {
        if (vid.isRelative())
            return vid.hasPrefix(f("~")) ? null : vid;
        final fURI at = vid.qLess().resolve();
        final fURI branch = this.branch();
        return at.hasPrefix(branch) && !at.equals(branch) ? at.removePrefix(branch).q(vid.qMap()) : null;
    }

    /**
     * the address the caller asked in: a cache address named below the branch again, unless the caller asked relative
     */
    private fURI asked(final fURI vid, final fURI at) {
        return vid.isRelative() ? at : this.branch().extend(at);
    }

    // ──────────────────────────────────────────────
    // reads: the cache's, but for the name table's door
    // ──────────────────────────────────────────────

    @Override
    public Obj read(final fURI vid) {
        final fURI at = this.cached(vid);
        if (null == at)
            return Network.super.read(vid);
        // THE TABLE ANSWERS WHAT THIS FRAME SEES, shadowing included -- the question a reader standing in a frame
        // actually asks: the frame's OWN table may be empty while the root's names resolve there. Marked, because a
        // name is spoken `//ginger`; the stored spelling is one strip away (prefixes()), and the write below makes
        // read-then-write a no-op by dropping inherited rows.
        if (f(PREFIX).equals(at))
            return Network.Helper.markedTable(Component.Prefix.Helper.effectivePrefixes(this));
        if (at.hasPrefix(f(PEER))) {
            if (at.pathLength() == 1)
                return this.peers();
            else
                return this.peers().at(at.pretract(1));
        }
        final Obj read = this.cache.read(at);
        // a branch read answers rels at the cache's addresses: they are named where the caller asked (what @ anchors with)
        return vid.isBranch() && !vid.isRelative()
                ? objs(read.stream().map(kv -> rel(uri(this.asked(vid, kv.asRel().first().uriValue())), kv.asRel().second())))
                : read;
    }

    @Override
    public Function<fURI, Iterator<IdObj>> directReader() {
        return vid -> {
            final fURI at = this.cached(vid);
            return null == at ? IteratorUtil.of() : IteratorUtil.map(this.cache.directReader().apply(at), id -> IdObj.of(this.asked(vid, id.furi()), id.obj()));
        };
    }

    // ──────────────────────────────────────────────
    // writes: intercepted, then the cache's
    // ──────────────────────────────────────────────

    @Override
    public BiFunction<fURI, Obj, Obj> directWriter() {
        return this::write;
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        final fURI at = this.cached(vid);
        if (null == at)
            return Network.super.write(vid, obj);
        // a q-proc's registration is the cache's, which speaks this level's q-procs
        if (at.hasQ())
            return this.cache.write(at, obj);
        final String field = at.segments().isEmpty() ? "" : at.segments().getFirst();
        final boolean members = PEER.equals(field) || LISTENER.equals(field);
        if (obj.isNoObj()) {
            // A MEMBER'S DELETION IS ITS RETRACTION. The member stored at exactly this address is the one it names
            // (literal first: the gateway's http/# is a key, not a pattern); else every member the address matches as a
            // pattern, or, for the bare map, every member it holds. Each leaves the cache, then what it held is
            // released -- clear, then close.
            (this.cache.sjvm().containsKey(at)
                    ? (members ? List.of(at) : List.<fURI>of())
                    : Stream.of(PEER, LISTENER)
                    .flatMap(map -> this.cache.sjvm().match(f(map).extend(ALL)).stream()
                            .map(Map.Entry::getKey)
                            .filter(member -> !member.equals(f(map))))
                    .filter(member -> member.test(at)
                            || (members && 1 == at.segments().size() && field.equals(member.segments().getFirst())))
                    .toList())
                    .forEach(member -> {
                        final Obj held = this.cache.sjvm().remove(member);
                        if (null != held)
                            Network.Helper.release(this, held);
                    });
            // a member map takes nothing but its members' retraction (a field of a member is not deleted from mtron)
            return members ? obj : this.cache.write(at, obj);
        }
        if (members) {
            // A MEMBER IS CONSTRUCTED, NOT WRITTEN: a client indexes itself at its key and a server listens at its
            // socket, each putting its typed member there as it stands -- never fanned out, for a key may be a wildcard.
            // Anything else written here (a field edit included) is refused rather than stored beside the member it
            // would contradict.
            if (1 == at.segments().size() || !obj.testNominally(PEER.equals(field) ? PEER_TYPE : LISTENER_TYPE)) {
                LOG.warn("a %s is constructed, not written: %s -> %s refused", field, vid, obj);
                return noobj();
            }
            this.cache.sjvm().put(at, obj);
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
            this.cache.write(at, own.isEmpty() ? noobj() : own);
            return obj;
        }
        return this.cache.write(at, obj);
    }

    // ──────────────────────────────────────────────
    // the level's own maps, read off the cache's store
    // ──────────────────────────────────────────────

    @Override
    public Rec peers() {
        return this.members(PEER);
    }

    @Override
    public Rec listeners() {
        return this.members(LISTENER);
    }

    /**
     * one of this level's maps as {@code <key> => member}: every entry stored below it, keyed by its path there, in key
     * order — the store is hashed and promises none, and a fan-out lays its lanes out in the order it reads them. Read
     * off the store, not by a {@code #} read, which would unroll each member's fields beside it.
     */
    private Rec members(final String map) {
        final fURI root = f(map);
        return rec(this.cache.sjvm().match(root.extend(ALL)).stream()
                .filter(entry -> !entry.getKey().equals(root))
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(fURI::toString)))
                .map(entry -> rel(uri(entry.getKey().removePrefix(root)), entry.getValue())));
    }

    @Override
    public Rec prefixes() {
        final Obj table = this.cache.sjvm().get(f(PREFIX));
        return null != table && table.isRec() ? table.asRec() : rec();
    }

    /**
     * Release what this level dialed and serves. The reach and presence go first ({@link Network#close()}: supervision
     * released, every member retracted — which is what hangs up a client and withdraws a route), then what the cache
     * still holds.
     */
    @Override
    public void close() {
        Network.super.close();
        this.cache.sjvm().forEach((at, value) -> CommonUtil.close(value));
        this.cache.sjvm().clear();
    }
}
