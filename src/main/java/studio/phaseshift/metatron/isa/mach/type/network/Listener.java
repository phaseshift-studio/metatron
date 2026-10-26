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

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Rel;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.Network;
import studio.phaseshift.metatron.util.MTronException;

import java.io.Closeable;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * A LISTENER — a protocol server holding a socket: a machine's presence at an authority.
 * <p>
 * A port binds once per physical machine, so the network level that opens a socket OWNS it, and that level's close
 * (its machine's pop) releases the port. Every machine below the owner inherits the socket and serves through it by
 * keeping its OWN routing table in its own level — a {@code listener::T} at {@code ~/network/listener/<key>} — so a pop
 * takes that machine's routes with its level and touches nothing else. The listener only remembers WHICH levels keep a
 * table on it; the tables stay in the levels and are read live, so a route written there is served at once.
 * <p>
 * The network never learns a protocol. A concrete server (ws, http) binds the socket, and per connection asks
 * {@link #route(fURI)}: the walk over every sharer's table, answered by the MOST SPECIFIC route
 * ({@link #SPECIFICITY}). No two machines can claim one key on a socket — the second is refused when it mounts
 * ({@link #refuseClaimed}) — so the walk never has to choose between equals.
 */
public abstract class Listener extends MRec implements Closeable {

    /**
     * Where a connection lands: the level whose table holds the matched route, the route's key, and its target. The
     * connection is that level's machine's: the peer it makes joins that level, and its route is resolved there.
     */
    public record Route(Network level, fURI key, Obj target) {
        public Machine machine() {
            return this.level.machine();
        }
    }

    /**
     * Ascending specificity: more literal segments, then fewer wildcards, then — two different keys of the same shape,
     * such as {@code /a/#} and {@code /+/b} — the smaller key, so the walk answers the same route every time.
     */
    public static final Comparator<fURI> SPECIFICITY = Comparator
            .comparingLong((fURI key) -> key.segments().stream().filter(Listener::literal).count())
            .thenComparingLong(key -> -key.segments().stream().filter(Listener::wildcard).count())
            .thenComparing(Comparator.comparing(fURI::toString).reversed());

    private final fURI origin;
    private final Network owner;
    /**
     * the levels keeping a routing table on this socket, by IDENTITY: a network level is a rec, and two levels with
     * the same contents (two fresh frames) are equal as recs while being different machines
     */
    private final Set<Network> sharers = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
    private final AtomicBoolean closed = new AtomicBoolean(false);

    protected Listener(final fURI origin, final fURI tid, final Network owner) {
        super(mutableMap(uri(ORIGIN), uri(origin)), tid, null);
        this.origin = origin;
        this.owner = owner;
        // THE STATUS every server answers — live until the socket is released, derived on read (Network.Helper.status)
        this.jvm().put(uri(STATUS), Network.Helper.statusOf(() -> !this.closed()));
    }

    /**
     * the service uri the socket is bound to, {@code scheme://host:port}
     */
    public fURI origin() {
        return this.origin;
    }

    /**
     * the network level that opened the socket — its close releases the port
     */
    public Network owner() {
        return this.owner;
    }

    /**
     * a level keeps a routing table on this socket (see {@link Network#listen})
     */
    public void share(final Network level) {
        this.sharers.add(level);
    }

    /**
     * a level's routing table leaves this socket
     */
    public void unshare(final Network level) {
        this.sharers.remove(level);
    }

    /**
     * the levels keeping a routing table on this socket, at this moment
     */
    public List<Network> sharers() {
        synchronized (this.sharers) {
            return List.copyOf(this.sharers);
        }
    }

    /**
     * Refuse routes another level already serves on this socket. Siblings share an inherited socket with no order
     * between them, and an outside dialer stands in no machine, so a duplicate key has no rightful owner: the later
     * mount fails rather than shadowing the earlier one (which also keeps a frame from taking over its parent's routes).
     */
    public void refuseClaimed(final Network level, final Rec routes) {
        for (final Network sharer : this.sharers()) {
            if (sharer == level)
                continue;
            final Rec claimed = this.tableOf(sharer);
            routes.keys().filter(Obj::isUri).map(Obj::uriValue).forEach(key -> {
                if (claimed.keys().filter(Obj::isUri).map(Obj::uriValue).anyMatch(key::equals))
                    throw MTronException.of("route %s on %s is already served by %s", key, this.origin, sharer.machine().vid());
            });
        }
    }

    /**
     * The route a request lands on: the most specific key, across every sharer's table, that the request's path falls
     * under. A literal key matches by whole segments ({@code /mcp} is no prefix of {@code /mcpx}); a wildcard key
     * matches the paths it describes.
     */
    public Optional<Route> route(final fURI request) {
        final fURI path = request.qLess();
        Route best = null;
        for (final Network level : this.sharers()) {
            for (final Rel route : this.tableOf(level).elements().toList()) {
                if (!route.first().isUri() || !path.hasPrefix(route.first().uriValue()))
                    continue;
                final Route candidate = new Route(level, route.first().uriValue(), route.second());
                if (null == best || SPECIFICITY.compare(candidate.key(), best.key()) > 0)
                    best = candidate;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * a level's routing table on this socket, read live from its {@code listener::T} entry — empty when it keeps none
     */
    private Rec tableOf(final Network level) {
        final Obj entry = level.listenerAt(Network.Helper.keyOf(this.origin));
        final Obj table = entry.isRec() ? entry.asRec().at(uri(ROUTE)) : noobj();
        return table.isRec() ? table.asRec() : rec();
    }

    /**
     * release the socket — the protocol's half of {@link #close()}, called once
     */
    protected abstract void unbind();

    public boolean closed() {
        return this.closed.get();
    }

    /**
     * Release the socket, and withdraw it from every level that served through it. Idempotent.
     * <p>
     * DESTRUCTION IS DISCONNECTION, in both directions: deleting a level's entry closes the socket it opened
     * ({@link Network#unlisten}), and a socket closed by hand leaves no level holding an entry for it. The withdrawal
     * comes back here through {@code unlisten}, and finds the socket already closed.
     */
    @Override
    public void close() {
        if (this.closed.compareAndSet(false, true)) {
            final List<Network> levels = this.sharers();
            this.sharers.clear();
            this.unbind();
            levels.forEach(level -> level.unlisten(this.origin));
        }
    }

    /**
     * Run a callback as the machine whose route the connection took, then stand back where the thread stood: a socket
     * thread is in no machine, and {@code Machine.current()} there answers the root.
     */
    protected static void as(final Machine machine, final Runnable body) {
        final Machine previous = Machine.CURRENT.get();
        Machine.current(machine);
        try {
            body.run();
        } finally {
            if (null == previous)
                Machine.CURRENT.remove();
            else
                Machine.CURRENT.set(previous);
        }
    }

    private static boolean literal(final String segment) {
        return !segment.isEmpty() && !wildcard(segment) && !segment.contains("${");
    }

    private static boolean wildcard(final String segment) {
        return "#".equals(segment) || "+".equals(segment);
    }
}
