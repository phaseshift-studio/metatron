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

import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rel;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.Network;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static studio.phaseshift.metatron.Tokens.CLIENT;
import static studio.phaseshift.metatron.Tokens.STATUS;
import static studio.phaseshift.metatron.Tokens.SUPERVISOR;
import static studio.phaseshift.metatron.isa.m.mInstSet.MUTABLE;
import static studio.phaseshift.metatron.isa.mach.machInstSet.NETWORK_SUPERVISOR_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * THE NETWORK SUPERVISOR — one per network level, keeping the level's index TRUE between the {@code index} that
 * granted a member and the {@code drop} that retracts it. It knows no protocol: it asks each member whose client is
 * a {@link Network.Link} the link's three questions, and acts on the answers.
 * <ul>
 *     <li><b>connected</b> — nothing to do; a link that had been down is reported back online.</li>
 *     <li><b>hung up</b> — its owner closed it: the explicit shutdown. The member is dropped from the map. The
 *     owner's close normally retracts at once ({@link Network.Helper#retract}); the tick is the backstop.</li>
 *     <li><b>down</b> — a failure, whatever the far side did (restart, shutdown, crash, refusal). The member stays
 *     listed and is redialed IN PLACE on a capped exponential backoff: immediately, then {@link #BACKOFF_MIN_MS}
 *     doubling to {@link #BACKOFF_MAX_MS}, at most one attempt in flight, each attempt on its own virtual thread
 *     so one unreachable host cannot hold up the level's other links.</li>
 * </ul>
 * A request does not wait for this schedule: a link that is down when a request needs it redials on demand (the
 * protocol's own open-wait), and the next tick simply finds it connected.
 * <p>
 * It is the Java facade over the {@code network_supervisor::T} rec: {@code status} lives in the backing map,
 * read by {@link #status()}. The supervised {@code network}, the ticking thread and the redial schedule are the
 * engine, not mtron data — kept as Java fields so the rec's value stays acyclic (a {@code network → supervisor →
 * network} value cycle would never terminate {@code equals}).
 * <p>
 * CONSTRUCTION IS CONNECTION, DESTRUCTION IS DISCONNECT. The constructor starts the thread and files itself in the
 * level's {@code supervisor} field; a {@code noobj} write over that field (close-before-clear) stops it.
 * <p>
 * LIFETIME IS THE LEVEL'S. A level is supervised from the first link it indexes ({@link #watch}) until it closes
 * ({@link #release}, from {@code Network.close()} — the frame's pop, or the VM's shutdown). Each tick runs AS the
 * level's machine: this thread stands nowhere of its own, and a {@code ~}-relative read or a log line made from it
 * would otherwise resolve against the root — the trap the mDNS callbacks name.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class NetworkSupervisor extends MRec implements Closeable {

    /**
     * how often a level's links are looked at — a look is local (no round trip), so a short tick costs nothing
     */
    public static final long TICK_MS = 500L;
    /**
     * the wait after a link's first failed redial; every further failure doubles it ...
     */
    public static final long BACKOFF_MIN_MS = 500L;
    /**
     * ... up to this cap, so a peer that comes back is found again within it
     */
    public static final long BACKOFF_MAX_MS = 10_000L;

    private static final GraphittyLogger LOG = Graphitty.log(NetworkSupervisor.class);

    /**
     * THE SUPERVISED LEVELS. A supervisor is a running thread, not data, so it is also kept beside the object graph
     * — keyed by IDENTITY, because a network rec's equality is its contents: two levels holding the same peers are
     * equal, and must not share a supervisor. (The {@code supervisor} field is the addressable half; this map is the
     * enumeration half that {@link #retract}/{@link #closeAll} walk.)
     */
    private static final Map<Network, NetworkSupervisor> SUPERVISORS = Collections.synchronizedMap(new IdentityHashMap<>());

    /**
     * the machine the supervised level belongs to — the thread's frame of reference. The network itself is read
     * per tick as {@code Machine.current().network()}, never stored: the thread is spawned at this machine, and a
     * stored reference could only drift from it.
     */
    private final Machine machine;
    /**
     * the redial schedule of each link seen down, by identity (a client's equality is its rec's)
     */
    private final Map<Network.Link, Retry> retries = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Thread thread;
    private volatile boolean closed = false;

    private NetworkSupervisor(final Network network) {
        super(supervisorMap(), NETWORK_SUPERVISOR_TID, null);
        this.machine = network.machine();
        this.thread = Thread.ofVirtual()
                .name("metatron-network-supervisor-" + System.identityHashCode(network))
                .start(this::run);
        // construction is connection: file ourselves in the level's supervisor slot
        network.at(uri(SUPERVISOR), this, MUTABLE);
    }

    private static Map<Obj, Obj> supervisorMap() {
        final Map<Obj, Obj> jvm = new LinkedHashMap<>();
        jvm.put(uri(STATUS), uri("supervising"));
        return jvm;
    }

    /**
     * The supervised level — the machine's own network.
     */
    public Network network() {
        return Machine.current().network();
    }

    /**
     * True while this supervisor is ticking (not yet released).
     */
    public boolean supervising() {
        return !this.closed;
    }

    /**
     * The liveness marker — {@code supervising} / {@code closed}, the rec's observable state.
     */
    public String status() {
        return this.closed ? "closed" : "supervising";
    }

    /**
     * How many links are currently in a redial backoff.
     */
    public int retryCount() {
        return this.retries.size();
    }

    /**
     * Supervise this level — idempotent, so every index of a link may ask.
     */
    public static void watch(final Network network) {
        SUPERVISORS.computeIfAbsent(network, NetworkSupervisor::new);
    }

    /**
     * Stop supervising this level. Called as the level closes, BEFORE its members are hung up: closing a level is
     * a retraction, not a failure to repair.
     */
    public static void release(final Network network) {
        final NetworkSupervisor supervisor = SUPERVISORS.remove(network);
        if (null != supervisor)
            supervisor.close();
    }

    /**
     * @return true while this level is supervised
     */
    public static boolean watching(final Network network) {
        return SUPERVISORS.containsKey(network);
    }

    /**
     * @return how many levels are supervised right now
     */
    public static int supervised() {
        return SUPERVISORS.size();
    }

    /**
     * THE VM'S END IS EVERY LEVEL'S END. The root's close closes the root's own level, but a frame that dialed and was
     * never popped still holds its reach — and its supervisor would keep ticking into whatever boots next in this JVM
     * (the next test class, the next boot). So shutdown closes every level still supervised: each one released, then
     * every member it holds hung up.
     */
    public static void closeAll() {
        final List<Network> levels;
        synchronized (SUPERVISORS) {
            levels = new ArrayList<>(SUPERVISORS.keySet());
        }
        levels.forEach(level -> {
            try {
                level.close();
            } catch (final Exception e) {
                LOG.debug("closing a supervised level at shutdown failed: %s", e);
            } finally {
                release(level);
            }
        });
    }

    /**
     * A client its OWNER closed leaves every supervised level that holds it. Only a supervised level can hold a
     * link (indexing one is what starts the supervision), so these are all the places to look.
     */
    public static void retract(final Object client) {
        final List<Network> levels;
        synchronized (SUPERVISORS) {
            levels = new ArrayList<>(SUPERVISORS.keySet());
        }
        levels.forEach(level -> drop(level, client));
    }

    /**
     * Drop the level's member whose client IS this one: matched by identity and retracted under the level's lock,
     * so a member that has since replaced it (a fresh dial of the same service) is never the one dropped. The close
     * that {@code disconnect} performs happens inside the lock, which is safe only because the client is already
     * hung up — closing it again is a no-op that does not come back into the map.
     */
    private static void drop(final Network level, final Object client) {
        synchronized (level) {
            level.peers().elements()
                    .filter(kv -> kv.first().isUri() && kv.second().isRec()
                            && client == kv.second().asRec().<Obj>atDirect(uri(CLIENT)))
                    .map(kv -> kv.first().uriValue())
                    .toList()
                    .forEach(level::disconnect);
        }
    }

    @Override
    public void close() {
        if (this.closed)
            return;
        this.closed = true;
        this.at(STATUS, uri("closed"), MUTABLE);
        this.thread.interrupt();
    }

    private void run() {
        while (!this.closed) {
            try {
                this.tick();
            } catch (final Throwable e) {
                // a tick must never end the supervision: the next one looks again
                LOG.debug("network supervision tick failed: %s", e);
            }
            try {
                Thread.sleep(TICK_MS);
            } catch (final InterruptedException e) {
                return; // released
            }
        }
    }

    private void tick() {
        Machine.current(this.machine);
        final Network network = Machine.current().network();
        final Set<Network.Link> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (final Rel member : network.peers().elements().toList()) {
            final Obj peer = member.second();
            if (!peer.isRec() || !(peer.asRec().<Obj>atDirect(uri(CLIENT)) instanceof Network.Link link))
                continue;
            seen.add(link);
            if (link.hungUp()) {
                this.retries.remove(link);
                drop(network, link);
            } else if (link.connected()) {
                if (null != this.retries.remove(link))
                    LOG.info("peer %s is back online", member.first());
            } else
                this.redial(member.first(), link);
        }
        // a member that left the map takes its schedule with it
        synchronized (this.retries) {
            this.retries.keySet().retainAll(seen);
        }
    }

    private void redial(final Obj service, final Network.Link link) {
        final Retry retry = this.retries.computeIfAbsent(link, l -> {
            LOG.info("peer %s is offline: redialing", service);
            return new Retry();
        });
        if (!retry.due() || !retry.claim())
            return;
        final Machine owner = this.machine;
        Thread.ofVirtual().name("metatron-peer-redial-" + System.identityHashCode(link)).start(() -> {
            Machine.current(owner);
            try {
                if (this.closed || !link.redial())
                    retry.failed();
            } catch (final Throwable e) {
                LOG.debug("redial of %s failed: %s", service, e);
                retry.failed();
            } finally {
                retry.release();
            }
        });
    }

    /**
     * One link's redial schedule: the first attempt is due at once, every failure doubles the wait up to the cap,
     * and at most one attempt is in flight.
     */
    private static final class Retry {

        private final AtomicBoolean inFlight = new AtomicBoolean(false);
        private volatile long dueAt = System.currentTimeMillis();
        private volatile long delayMs = BACKOFF_MIN_MS;

        boolean due() {
            return System.currentTimeMillis() >= this.dueAt;
        }

        boolean claim() {
            return this.inFlight.compareAndSet(false, true);
        }

        void release() {
            this.inFlight.set(false);
        }

        void failed() {
            this.dueAt = System.currentTimeMillis() + this.delayMs;
            this.delayMs = Math.min(BACKOFF_MAX_MS, this.delayMs * 2);
        }
    }
}
