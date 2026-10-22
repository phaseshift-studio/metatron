/*
 * metatron: a distributed virtual machine and language
 *  Copyright (C) 2025- PhaseShift Studio, LLC
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.isa.web.space.mdns.handler;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.Network;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.isa.sys.type.ThreadExecutor;
import studio.phaseshift.metatron.isa.web.webInstSet;
import studio.phaseshift.metatron.util.CommonUtil;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;
import java.io.Closeable;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.*;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * DISCOVERY OVER mDNS — announces the listeners this machine answers on to the local network (never a backend it
 * dials out to: see {@link #surfaces()}), and writes what it hears from others into {@link Network#ROOT_ROSTER}.
 * It serves no bytes: a peer list is its whole product, which is why it feeds the roster rather than answering an
 * address.
 * <p>
 * ITS WHOLE INTERFACE IS ITS EXISTENCE. Construction starts it, {@code close()} stops it, and deleting it from
 * the roster's {@code discovery} bag is how it is turned off — there is no {@code start()} to remember and no
 * {@code scan()} to drive.
 * <p>
 * A PASSIVE BACKGROUND OBJECT. It publishes ONE field, {@code status} → {@link #status()}: the health bit, the
 * only thing {@code discovery::T} requires of any protocol, so
 * {@code ~/network/roster/discover/+.?[status=>false]} answers "what is down" without knowing what mDNS is.
 * Wrapped in {@code auto_} so a READ resolves it — a bare inst comes back as an inst, and that filter would then
 * compare a Call against a bool and never match — and declaring its RANGE, since an undeclared Call answers
 * {@code ?rng=#{*}} and satisfies every predicate vacuously.
 * <p>
 * NOTHING ELSE IS PUBLISHED, because a discovery protocol's product is ROWS IN {@code roster/machine} rather than
 * fields on itself. No {@code host}, {@code local}, {@code authority} or {@code machine} field for a consumer to
 * search across, and no {@code close} field, since a destructor is assumed in the same way a constructor is. Many
 * protocols can run at once and they collaborate on that one index; a listing of its own would make each of them
 * something to search instead of something to read.
 * <p>
 * Everything else is private or static: the open world lets a value carry more than its type requires, but only
 * what is published is anyone's business.
 * <p>
 * TWO THINGS ARE LOAD-BEARING AND NEITHER IS OPTIONAL:
 * <ul>
 *   <li><b>admission is explicit.</b> Discovery is not membership — a responder is only written once it passes
 *       {@link #ours}, so a uri cannot make itself a peer merely by being heard.</li>
 *   <li><b>the callback thread is not the handler's.</b> JmDNS delivers on its own thread, so the roster write
 *       runs inside {@link Machine#current(Machine, java.util.function.Supplier)} against the machine that started
 *       the handler. A {@code ~}-relative address there would resolve against whatever machine that thread happens
 *       to stand in, silently.</li>
 * </ul>
 */
public class mdnsDiscovery extends MRec implements Closeable {

    public static final fURI MDNS_DISCOVERY_TID = webInstSet.WEB_ISA_TID.extend("mdns").extend("mdns_discovery");
    public static final fURI DEFAULT_SERVICE = f("_metatron._tcp.local.");
    protected final GraphittyLogger LOG = Graphitty.log(this);
    /**
     * close() and listen()'s add contend for the responder list; JmDNS.create() is slow enough to guarantee it
     */
    private final Object lifecycle = new Object();

    // THE TYPE IS DECLARED AT ITS REGISTRATION SITE, in webInstSet beside mdns::T and the other protocol
    // surfaces, so the schema a user reads and the schema the instruction set publishes are the same text.
    // What is left here is the identity it is registered under.

    /**
     * ROOT_ROSTER is read, amended and written back, and this handler GUARANTEES concurrency against itself:
     * {@link #listen()} creates one JmDNS and one {@link PeerListener} PER LOCAL ADDRESS, so on a multi-homed
     * host two listeners hear the same peer on two threads, both read the same rec, and the later write drops the
     * earlier one's address. Admission is serialized here.
     * <p>
     * A lock in this class is a stopgap and not the fix: it cannot cover a second discovery protocol writing the
     * same rec. That wants the roster to own an {@code admit(uri)} of its own, with the self-test, the
     * canonicalization and this serialization behind it, and the handlers merely proposing.
     */
    private static final Object ROSTER = new Object();

    /**
     * the machine the handler was started in — a callback must re-establish it, never assume it
     */
    private final Machine owner = Machine.current();
    private final List<JmDNS> responders = new ArrayList<>();
    /**
     * THE PHYSICAL HOST, not {@code network().name()}. mDNS names machines, and {@code network().name()} is a
     * logical label a machine chooses for itself ({@code metatron}) — announcing that as the SRV target makes a
     * remote peer key this machine by a name no box on the network answers to, while our own row in
     * {@code roster/machine} says something else. One machine, two keys, forever.
     * <p>
     * Captured once, on the constructing thread: it cannot change under a running responder, and reading it per
     * call would make a published field depend on who asked.
     */
    private final String hostName;
    private boolean started = false;
    private boolean closed = false;

    public mdnsDiscovery(final Map<Obj, Obj> jvm, final fURI vid) {
        super(jvm, MDNS_DISCOVERY_TID, vid);
        this.hostName = localHostName();
        // ONE FIELD PER PUBLIC METHOD, each an inst so the published value is computed on READ rather than
        // remembered -- a status written once is a lie by the time an interface drops. Guarded, so a caller who
        // supplied a field of their own keeps it.
        //
        // EVERY ONE DECLARES ITS RANGE. An undeclared Call answers ?rng=#{*}, which satisfies any predicate for
        // free, so the declaration is the only thing making discovery::T's `status=>bool::T` mean anything. And
        // status is auto_: a read has to RESOLVE it, or ?[status=>false] compares a Call against a bool.
        // STATUS IS THE ONLY DERIVED FIELD, and auto_ is what makes it readable as a value: a bare inst comes
        // back as an inst, so ?[status=>false] would compare a Call against a bool and never match. It declares
        // its range because an undeclared Call answers ?rng=#{*} and satisfies any predicate for free.
        this.publish(STATUS, auto_(instLambda(ALL.maybe(), BOOL_TID, (lhs, inst) -> bool(this.status()))).tryToInst());
        // HOST IS A VALUE: it is fixed at construction, so there is nothing to recompute on read.
        // Nothing else is published: what this handler PRODUCES are rows in roster/machine, written as each
        // responder binds and each authority is announced.
        this.register();
        // A SOURCE IS LIVE ONCE IT EXISTS, like a space registering with the router on construction: ...
        this.start();
    }

    /**
     * REGISTER IN THE ROSTER'S discover BAG. Being constructed is being turned on, and a handler nobody can see
     * is not on in any useful sense: {@code ~/network/roster/discover} is where a user looks for what is
     * discovering and where the status query reads from.
     * <p>
     * Keeps every sibling for the same reason {@link #commit} does, and is idempotent in THIS instance so a
     * re-registration is not a second entry. Two distinct handlers on the same service are left alone: closing
     * someone else's responders on a name collision is a policy, not a mechanism.
     * <p>
     * {@link #close()} deliberately does NOT undo this. A closed handler that stayed in the bag reads
     * {@code status=>false}, which is exactly what {@code discover/+.?[status=>false]} exists to find; removing
     * itself would make a dead protocol indistinguishable from one that was never registered. Turning it off by
     * DELETION is the container's business, and the container closes what it evicts.
     */
    private void register() {
        synchronized (ROSTER) {
            final Obj roster = Machine.read(Network.ROOT_ROSTER);
            final Map<Obj, Obj> fields = new LinkedHashMap<>(roster.isRec() ? roster.asRec().jvm() : Map.of());
            final Obj current = roster.isRec() ? roster.asRec().at(uri(DISCOVER)) : noobj();
            final List<Obj> handlers = new ArrayList<>();
            if (!current.isNoObj())
                current.stream().forEach(handlers::add);
            if (handlers.stream().noneMatch(known -> known == this))
                handlers.add(this);
            fields.put(uri(DISCOVER), objs(handlers.toArray(Obj[]::new)));
            Machine.write(Network.ROOT_ROSTER, rec(fields));
        }
    }

    // ========================================================================
    // the published surface
    // ========================================================================

    /**
     * THE HEALTH BIT — is this handler actually discovering? Derived on every read, never stored: a handler that
     * bound no multicast-capable interface is inert from birth, and one whose interfaces went away is inert from
     * then on, and neither event announces itself.
     * <p>
     * {@code true} means responders exist and this handler has not been closed. JmDNS does not report an
     * interface dying underneath it, so this is the honest upper bound on liveness, not a probe.
     */
    public boolean status() {
        return this.started && !this.closed && !this.responders.isEmpty();
    }

    @Override
    public void close() {
        final List<JmDNS> closing;
        synchronized (this.lifecycle) {
            if (this.closed)
                return;
            this.closed = true;
            closing = new ArrayList<>(this.responders);
            this.responders.clear();
            this.started = false;
        }
        closing.forEach(mdns -> {
            mdns.unregisterAllServices();
            CommonUtil.close(mdns);
        });
    }

    // ========================================================================
    // becoming live
    // ========================================================================

    /**
     * install a published field unless the caller already supplied one of that name
     */
    private void publish(final String field, final Obj value) {
        if (!this.jvm().containsKey(uri(field)))
            this.jvm().put(uri(field), value);
    }

    /**
     * GROW A PUBLISHED FIELD BY ONE: noobj → mono → objs. There is no zero- or one-element objs, so a field with
     * nothing in it is simply absent, one interface answers the uri itself, and several answer a bag — which is
     * why these are values rather than Calls over a Java list kept in step with them.
     */
    /**
     * THIS MACHINE IN THE ONE MACHINE TABLE. {@code roster/machine} is the consolidated index of every machine
     * any discovery protocol knows about, THIS ONE INCLUDED — so where an endpoint was learned is nobody's
     * business, and a consumer generating peers reads one table instead of searching across per-protocol
     * listings.
     * <p>
     * Only ENDPOINTS go in it. The interfaces a responder bound are not endpoints — a bare {@code 192.168.1.61}
     * has no scheme and no port, so nothing can be dialed at it and no peer can be made from it. They are
     * visible where they belong, in the {@code listening} log line, and nowhere else.
     * <p>
     * Written through {@link #roster}, so an endpoint deduplicates exactly as a heard address does — which is
     * what stops the announce loop, registering every authority on every responder, from writing one endpoint
     * once per interface.
     */
    private fURI exposed() {
        // NO TRAILING DOT. DNS writes a fully-qualified name with the root dot and JmDNS hands back
        // "<host>.local.", but roster keys are matched by TEXT -- so a machine learned from an announcement
        // (dotted) and the same machine learned from an authority (undotted) would be two rows for one box.
        // One convention, and it is the one a human types.
        return f(this.hostName + ".local");
    }

    /**
     * THE MACHINE A SURFACE IS FILED UNDER. Every surface is a listener of this machine — a backend's host, such as
     * a broker at {@code mqtt://other.local:1883}, is never one (see {@link Network.Helper#authorities()}) — but
     * not every surface spells this machine's name.
     * <p>
     * A wildcard or loopback bind IS this machine: {@code 0.0.0.0} means every local address, so it keys to
     * {@link #exposed()}. A bind to one interface keys by the host it names, which is the only machine identity the
     * uri carries — an address rather than a name when that is all it has.
     */
    private fURI machineOf(final fURI authority) {
        final String host = authority.host();
        return null == host || host.isBlank() || "0.0.0.0".equals(host) || "::".equals(host)
                || "localhost".equals(host) || "127.0.0.1".equals(host)
                ? this.exposed()
                : f(host);
    }

    /**
     * THE NAME THE BOX ANSWERS TO, as mDNS means it: the host label alone, no domain. A host named
     * {@code <label>.home.arpa} and one named {@code <label>} both announce as {@code <label>}, which is what
     * {@code .local.} is appended to.
     * <p>
     * Falls back to {@code localhost} when the host cannot be resolved, because a discovery handler must never
     * be why a boot fails — with a warning, since every name this handler advertises is built from it. NOT the
     * logical {@code network/name}: that is the mistake this method exists to avoid, and a wrong name is worse
     * than an obviously local one.
     */
    private static String localHostName() {
        try {
            final String name = InetAddress.getLocalHost().getHostName();
            if (null != name && !name.isBlank() && !"localhost".equals(name))
                return name.split("\\.")[0];
        } catch (final Exception e) {
            Graphitty.log(mdnsDiscovery.class).warn("mdns: cannot resolve this host's name (%s)", e.getMessage());
        }
        return "localhost";
    }

    /**
     * announce on every local address we can, and listen for the same from others. Idempotent, and a no-op with a
     * warning when nothing multicast-capable is present — a discovery handler must never be why a boot fails.
     */
    private void start() {
        if (this.started || this.closed)
            return;
        this.started = true;
        // the WHOLE start runs as the machine that owns this handler: it reads authorities() and takes its name
        // from the network, and the thread it is started on is not that machine.
        ThreadExecutor.instance().execute(this::listen);
    }

    private void listen() {
        final fURI service = this.service();
        for (final InetAddress address : this.localAddresses()) {
            try {
                final JmDNS mdns = JmDNS.create(address, this.hostName);
                this.surfaces().forEach(surface -> this.announce(mdns, service, surface));
                mdns.addServiceListener(service.toString(), new PeerListener(mdns));
                mdns.addServiceListener(service.toString(), new PeerListener(mdns));
                synchronized (this.lifecycle) {
                    // CLOSED WHILE WE WERE DIALING -- own this responder here and stop, or it outlives the
                    // handler and status() reads true again after close
                    if (this.closed) {
                        mdns.unregisterAllServices();
                        CommonUtil.close(mdns);
                        break;
                    }
                    this.responders.add(mdns);
                }
                this.LOG.info("mdns: listening on %s as %s", address.getHostAddress(), this.hostName);
            } catch (final IOException e) {
                this.LOG.warn("mdns: cannot listen on %s (%s)", address.getHostAddress(), e.getMessage());
            }
        }
        if (this.responders.isEmpty())
            this.LOG.warn("mdns: no multicast-capable interface — discovery is inert here");
    }

    private fURI service() {
        final Obj declared = this.at(uri(SERVICE));
        return declared.isNoObj() ? DEFAULT_SERVICE : declared.uriValue();
    }

    /**
     * The DNS-SD instance name for one SURFACE: {@code <host name>-<scheme>-<port>}. A machine can answer on more
     * than one surface — http and ws on the same port are two services — and DNS-SD wants one name per registered
     * instance, so the surface belongs in the name.
     * <p>
     * DETERMINISTIC IN THE THING THAT OWNS IT, deliberately: a restart re-registers the SAME name, so its roster
     * entry is reclaimed in place. A minted id would be unique but never reclaimable — after a hard kill (no
     * goodbye) that entry could only age out, a 4500s PTR TTL, while still looking dialable.
     * <p>
     * The HOST is absent from it: the same name on two hosts is legal (the SRV target separates them), which is
     * why {@link #admit} keys the roster on the service uri rather than on this name.
     */
    private String instanceName(final fURI authority) {
        return "%s-%s-%d".formatted(this.hostName, scheme(authority.scheme()), authority.port());
    }

    /**
     * The addresses to advertise on: the configured interface's IPv4s, else every one that is up and not loopback.
     * A wildcard bind ({@code 0.0.0.0}) is NOT an address — it has to be resolved to the interfaces behind it,
     * which is also what makes the announcement dialable.
     */
    private List<InetAddress> localAddresses() {
        final Obj declared = this.at(uri(INTERFACE));
        final String only = declared.isNoObj() ? "" : declared.strValue();
        final List<InetAddress> addresses = new ArrayList<>();
        try {
            final Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                final NetworkInterface iface = ifaces.nextElement();
                if (iface.isLoopback() || !iface.isUp() || (!only.isBlank() && !iface.getName().equals(only)))
                    continue;
                final Enumeration<InetAddress> members = iface.getInetAddresses();
                while (members.hasMoreElements()) {
                    final InetAddress member = members.nextElement();
                    if (member instanceof Inet4Address)
                        addresses.add(member);
                }
            }
        } catch (final Exception e) {
            this.LOG.warn("mdns: cannot enumerate interfaces (%s)", e.getMessage());
        }
        return addresses;
    }

    /**
     * WHAT THIS MACHINE ANNOUNCES: every listener it answers on, each reduced to the address a peer dials. Read per
     * responder, as the authorities always were — a boot may mount its listeners after constructing this handler.
     * <p>
     * VISIBLE FOR TESTING, like {@link #roster}: this set is everything that leaves the box, so it is the surface a
     * leak would show on, and it is checkable without multicast.
     */
    List<fURI> surfaces() {
        return Network.Helper.authorities().stream()
                .map(mdnsDiscovery::announceable)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    /**
     * THE ADDRESS A PEER DIALS, and nothing a space's config carried along with it: scheme, host, port and path,
     * the {@code ?query} dropped. A listener's query is configuration — a parameter today, a token tomorrow — and
     * what is announced is multicast to every box on the link, cached by each of them and filed in the roster.
     * <p>
     * USERINFO IS REFUSED, NOT STRIPPED. A fURI has no userinfo component, so a {@code user:pass@} lands welded
     * into another one: {@code archivist@0.0.0.0} parses as the host, and in {@code archivist:7341@0.0.0.0:8555}
     * the password parses as the PORT. Nothing can be stripped back out of that, so an address carrying an
     * {@code @} is not announced at all — and neither is one without a host and a port, which nothing can dial.
     *
     * @return the address to announce, or {@code null} when there is none
     */
    static fURI announceable(final fURI authority) {
        final fURI address = authority.basePath();
        return null == address.host() || address.port() <= 0 || address.toString().contains("@") ? null : address;
    }

    /**
     * THE TXT RECORD of one surface. Its route is the surface's PATH — {@code basePath()} before
     * {@code localize()}, because localize keeps the query — so the record cannot carry one even if handed an
     * address that was never made {@link #announceable}.
     * <p>
     * SEPARATE TXT ENTRIES, never one comma-joined string: getPropertyString returns everything after the FIRST
     * '=', so "route=/,version=0.1" comes back as route=[/,version=0.1] — measured.
     */
    static Map<String, String> txt(final fURI surface) {
        return Map.of(ROUTE, surface.basePath().localize().toString(), SCHEME, scheme(surface.scheme()),
                VERSION, METATRON_VERSION);
    }

    /**
     * register one surface as a service instance, its route in the TXT so a browser can dial it
     */
    private void announce(final JmDNS mdns, final fURI service, final fURI surface) {
        try {
            mdns.registerService(ServiceInfo.create(service.toString(), this.instanceName(surface), surface.port(), 0, 0,
                    txt(surface)));
            this.roster(this.machineOf(surface), surface);
        } catch (final IOException e) {
            this.LOG.warn("mdns: cannot announce %s (%s)", surface, e.getMessage());
        }
    }

    // ========================================================================
    // hearing others
    // ========================================================================

    /**
     * is this the machine talking about itself? A wildcard bind answers on EVERY local address, so the test is
     * local-address membership at a port we serve — the alias set alone cannot see that.
     */
    private boolean ours(final InetAddress address, final int port) {
        return this.localAddresses().stream().anyMatch(local -> local.equals(address))
                && Network.Helper.authorities().stream().anyMatch(mine -> mine.port() == port);
    }

    /**
     * one MACHINE, written into the roster as {@code <machine> => uri{+}}. The key is the SRV TARGET — the
     * machine identity mDNS already carries — and the values are every address it answers at, so a machine
     * reachable over http and ws, or on three interfaces, is ONE entry rather than three that overwrite.
     * <p>
     * The per-surface instance NAME is deliberately not the key: DNS-SD permits the same name on two hosts, so a
     * name-keyed roster silently collapses every responder onto a single entry (measured, and the bug that
     * started this).
     * <p>
     * THE WHOLE BODY RUNS AS {@link #owner}: {@link #ours} reads this machine's authorities and the write lands
     * in this machine's roster, and the JmDNS callback thread stands in neither.
     */
    private void admit(final ServiceInfo info) {
        final Machine current = Machine.current();
        Machine.current(this.owner);
        try {
            if (!info.getPropertyNames().hasMoreElements()) {
                this.LOG.debug("mdns: %s resolved without a TXT record — waiting for a complete one", info.getName());
                return;
            }
            final String scheme = info.getPropertyString(SCHEME);
            final String route = info.getPropertyString(ROUTE);
            final fURI machine = machine(info);
            for (final InetAddress address : info.getInet4Addresses()) {
                if (this.ours(address, info.getPort())) {
                    this.LOG.debug("mdns: heard our own %s", address.getHostAddress());
                    continue;
                }
                final fURI dialable = dialable(address, info.getPort(), scheme, route);
                if (this.roster(machine, dialable))
                    this.LOG.info("mdns: discovered %s at %s", machine, dialable);
                else
                    this.LOG.debug("mdns: %s at %s again", machine, dialable);
            }
        } finally {
            Machine.current(current);
        }
    }

    /**
     * Two uris are the same ADDRESS when their BASE PATHS agree. {@link fURI#basePath()} keeps scheme, host, port
     * and path and resets the coefficient to one, empties the poly and drops the {@code ?query} — so it is the
     * address itself, canonical, and {@code equals} can be trusted on it. Comparing the rendered form instead is
     * how one address once got stored twice (a path of {@code []} and {@code [""]} print the same).
     */
    private static boolean sameAddress(final fURI one, final fURI other) {
        return one.basePath().equals(other.basePath());
    }

    /**
     * The identity a responder is keyed by: the SRV TARGET — the machine mDNS names — as a BARE HOST, with no
     * scheme and no port. A machine is one row however many surfaces it answers on, and the row's VALUES carry
     * their own schemes and ports, so a consumer picking an endpoint filters on the value rather than the key.
     * <p>
     * Not the instance name: DNS-SD permits the same name on two hosts, so a name-keyed roster collapses every
     * responder onto one entry (measured). And not scheme-scoped, which this once was: it split one physical
     * machine into a row per surface, exactly the per-protocol searching the single table exists to avoid.
     */
    static fURI machine(final ServiceInfo info) {
        return f(segment(info.getServer()));
    }

    /**
     * the roster's {@code machine} map as a mutable map. A rec cannot be ADDRESSED by a uri key, so it is read,
     * amended, written back.
     * <p>
     * THE MACHINE FIELD, NOT THE ROSTER. {@code roster} carries {@code discover} beside {@code machine} now, and
     * writing a flat machine map at {@link Network#ROOT_ROSTER} would erase the registered discovery handlers on
     * the first peer heard — the roster is read down to its machine field here and rebuilt around it in
     * {@link #commit}.
     */
    private Map<Obj, Obj> entries() {
        final Obj roster = Machine.read(Network.ROOT_ROSTER);
        final Obj machines = roster.isRec() ? roster.asRec().at(uri(MACHINE)) : noobj();
        return new LinkedHashMap<>(machines.isRec() ? machines.asRec().jvm() : Map.of());
    }

    /**
     * write the machine map back, KEEPING EVERY SIBLING the roster carries. The whole roster is rewritten rather
     * than its machine field addressed directly, so a roster that does not exist yet is created rather than
     * failing on a missing parent.
     */
    private void commit(final Map<Obj, Obj> entries) {
        final Obj roster = Machine.read(Network.ROOT_ROSTER);
        final Map<Obj, Obj> fields = new LinkedHashMap<>(roster.isRec() ? roster.asRec().jvm() : Map.of());
        fields.put(uri(MACHINE), rec(entries));
        Machine.write(Network.ROOT_ROSTER, rec(fields));
    }

    /**
     * add ONE address to a machine's entry, keeping any it already had. Matching is by TEXT, never by Obj
     * identity: a uri that has round-tripped through the roster does not equal a freshly minted one, even when
     * the two print the same address.
     * <p>
     * VISIBLE FOR TESTING: this is the whole roster contribution of a discovery protocol, and it is testable
     * without multicast, a second host or a resolved {@code ServiceInfo} — which none of the admission path
     * above can be.
     */
    boolean roster(final fURI machine, final fURI address) {
        final fURI canonical = address.basePath();
        // A MACHINE KEY IS A HOST, and the callers hand one over: machine(ServiceInfo) names the SRV target
        // through segment(), and machineOf(authority) reads host() off the endpoint. Keys are matched by TEXT, so
        // a producer that passed a scheme, a port or a DNS root dot would split one machine into rows -- which is
        // the producers' job to get right, not this method's to launder.
        final String key = machine.toString();
        synchronized (ROSTER) {
            final Map<Obj, Obj> entries = this.entries();
            final List<Obj> addresses = new ArrayList<>();
            entries.entrySet().stream()
                    .filter(entry -> entry.getKey().isUri() && key.equals(entry.getKey().uriValue().toString()))
                    .map(Map.Entry::getValue).findFirst().ifPresent(known -> known.stream().forEach(addresses::add));
            // ALREADY THERE: a re-heard address is not news, and not a write either
            if (addresses.stream().anyMatch(known -> known.isUri() && sameAddress(known.uriValue(), canonical)))
                return false;
            addresses.add(uri(canonical.toString()));
            entries.entrySet().removeIf(entry -> entry.getKey().isUri() && key.equals(entry.getKey().uriValue().toString()));
            entries.put(uri(key), objs(addresses.toArray(Obj[]::new)));
            this.commit(entries);
            return true;
        }
    }

    /**
     * THE ROSTER HOLDS URIs THAT ARE USABLE AS THEY STAND: a value can be handed straight to a client as its
     * host (read at an index from the roster) with nothing to take apart. Two things can corrupt
     * that — a responder's route is UNTRUSTED text, and a DNS-SD instance name is not necessarily a valid uri
     * segment (JmDNS disambiguates a collision as {@code "name (2)"}) — so both are normalized on the way in.
     */
    static fURI dialable(final InetAddress address, final int port, final String advertisedScheme,
                         final String advertisedRoute) {
        final fURI authority = f("").scheme(scheme(advertisedScheme)).host(address.getHostAddress()).port(port);
        final String path = route(advertisedRoute);
        // THE ROOT ROUTE EXTENDS NOTHING. extend("/") is a path of ONE EMPTY SEGMENT, which prints the same as no
        // path but does not equal it -- so a peer announcing route=/ is appended to its own entry on every
        // re-announcement and accumulates a coefficient instead of deduplicating ({12}, measured).
        return "/".equals(path) ? authority : authority.extend(path);
    }

    /**
     * the advertised scheme, or ws — a metatron peer answers over websockets
     */
    static String scheme(final String advertised) {
        return null != advertised && advertised.matches("[a-z][a-z0-9+.-]*") ? advertised : "ws";
    }

    /**
     * a route we can put in an address: a path, optionally queried — never metadata welded onto it
     */
    static String route(final String advertised) {
        if (null == advertised)
            return "/";
        if (advertised.matches("/[^,<>\\s]*"))
            return advertised;
        // An announcer that packed several TXT properties into ONE entry means the route it MEANT is the text
        // before the first comma: "route=/,version=0.1" arrives here as "/,version=0.1". Recover it; if even that
        // is not a path, refuse the text outright rather than let a foreign responder shape our address.
        final String head = advertised.split(",", 2)[0];
        return head.matches("/[^,<>\\s]*") ? head : "/";
    }

    /**
     * an instance name as ONE valid uri segment
     */
    static String segment(final String instance) {
        // the trailing '.' goes with the leading and trailing '-': a DNS root dot is qualification, not part of
        // the name, and leaving it on makes "<host>.local." and "<host>.local" two different roster keys
        final String slug = (null == instance ? "" : instance).replaceAll("[^A-Za-z0-9._-]", "-")
                .replaceAll("-{2,}", "-").replaceAll("^[-.]+|[-.]+$", "");
        return slug.isEmpty() ? "peer" : slug;
    }

    /**
     * a withdrawn responder takes back only the addresses it advertised. The machine keeps its entry while any
     * other surface or interface still answers for it, and is dropped when the last one goes.
     */
    private void retract(final ServiceInfo info) {
        final String scheme = info.getPropertyString(SCHEME);
        final String route = info.getPropertyString(ROUTE);
        final String key = machine(info).toString();
        final List<fURI> gone = new ArrayList<>();
        for (final InetAddress address : info.getInet4Addresses())
            gone.add(dialable(address, info.getPort(), scheme, route));
        final Machine current = Machine.current();
        Machine.current(this.owner);
        try {
            synchronized (ROSTER) {
                final Map<Obj, Obj> entries = this.entries();
                entries.entrySet().stream()
                        .filter(entry -> entry.getKey().isUri() && key.equals(entry.getKey().uriValue().toString()))
                        .findFirst().ifPresent(entry -> {
                            final List<Obj> kept = new ArrayList<>();
                            entry.getValue().stream()
                                    .filter(known -> known.isUri()
                                            && gone.stream().noneMatch(was -> sameAddress(was, known.uriValue())))
                                    .forEach(kept::add);
                            entries.remove(entry.getKey());
                            if (!kept.isEmpty())
                                entries.put(entry.getKey(), objs(kept.toArray(Obj[]::new)));
                        });
                this.commit(entries);
            }
        } finally {
            Machine.current(current);
        }
    }

    private final class PeerListener implements ServiceListener {

        private final JmDNS mdns;

        private PeerListener(final JmDNS mdns) {
            this.mdns = mdns;
        }

        @Override
        public void serviceAdded(final ServiceEvent event) {
            // added names the instance; the SRV/A only arrive with the resolve, so ask for it explicitly
            this.mdns.requestServiceInfo(event.getType(), event.getName(), true);
        }

        @Override
        public void serviceResolved(final ServiceEvent event) {
            mdnsDiscovery.this.admit(event.getInfo());
        }

        @Override
        public void serviceRemoved(final ServiceEvent event) {
            mdnsDiscovery.this.retract(event.getInfo());
        }
    }
}
