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

package studio.phaseshift.metatron.isa.m.web.space.mdns.handler;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.Network;
import studio.phaseshift.metatron.isa.m.sys.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.m.sys.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.isa.m.sys.type.ThreadExecutor;
import studio.phaseshift.metatron.isa.m.web.webInstSet;
import studio.phaseshift.metatron.isa.m.web.type.mcpMetatronBuilder;
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
 * DISCOVERY OVER mDNS — announces the MTRON ENDPOINTS this machine answers on to the local network (a listener's
 * origin extended by each route whose handler speaks mtron — an {@code mtron::T} carried by the socket's scheme, or
 * metatron's mcp server carried by {@code mcp}; never a backend it dials out to: see {@link #surfaces()}), and writes
 * what it hears, and what it announces, into the roster's machine table:
 * {@code /network/roster/machine/<host>/<protocol>/<transport>}.
 * It serves no bytes: a peer list is its whole product, which is why it feeds the roster rather than answering an
 * address.
 * <p>
 * ITS WHOLE INTERFACE IS ITS EXISTENCE. Construction starts it, {@code close()} stops it, and deleting it from
 * the roster's {@code discover} bag is how it is turned off — there is no {@code start()} to remember and no
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
     * THE ROSTER IS READ, AMENDED AND WRITTEN BACK — an address list at its own path, the discover bag with the roster
     * around it — and this handler GUARANTEES concurrency against itself: {@link #listen()} creates one JmDNS and one {@link PeerListener} PER LOCAL ADDRESS, so on a
     * multi-homed host two listeners hear the same peer on two threads, both read the same list, and the later write
     * drops the earlier one's address. Admission is serialized here.
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
     * THE PHYSICAL HOST, not a logical label a machine chooses for itself ({@code metatron}). mDNS names machines:
     * announcing a self-chosen label as the SRV target makes a remote peer key this machine by a name no box on the
     * network answers to, while our own row in {@code roster/machine} says something else. One machine, two keys,
     * forever.
     * <p>
     * Captured once, on the constructing thread: it cannot change under a running responder, and reading it per
     * call would make a published field depend on who asked.
     */
    private final String hostName;
    private boolean started = false;
    private boolean closed = false;
    /**
     * how often the announced set is re-read against the live surfaces — surfaces come and go for the VM's whole
     * life, and a look is local (no round trip), so a short tick costs nothing
     */
    private static final long TICK_MS = 1_000L;
    /**
     * the surfaces already announced — reconciliation announces what is new and retires what is gone
     */
    private final Map<String, fURI> announced = Collections.synchronizedMap(new HashMap<>());

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
     * The handler is MOUNTED at {@code /network/roster/discover}, a live member of the bag rather than data laid out
     * there, so the roster is read, the bag amended and the roster written back around it — every sibling kept —
     * idempotent in THIS instance, so mounting again is not a second entry. Two distinct handlers on the same service are left alone: closing
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
     * THE MACHINE A SURFACE IS FILED UNDER. Every surface is an endpoint on a listener of this machine — a backend's
     * host, such as a broker at {@code mqtt://other.local:1883}, is never one (see
     * {@link Network.Helper#authorities()}) — but not every surface spells this machine's name.
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
                surfaces().forEach((surface, transport) -> this.announce(mdns, service, surface, transport));
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
        if (this.responders.isEmpty()) {
            this.LOG.warn("mdns: no multicast-capable interface — discovery is inert here");
            return;
        }
        // what the initial sweep just announced is the baseline; the reconciliation loop below keeps it true as
        // spaces and services come and go for the VM's whole life
        surfaces().forEach((surface, transport) -> this.announced.put(surface.toString(), transport));
        while (!this.closed) {
            try {
                this.reconcile(service);
            } catch (final Throwable e) {
                // a tick must never end the discovery: the next one looks again
                this.LOG.debug("mdns: reconciliation tick failed: %s", e);
            }
            try {
                Thread.sleep(TICK_MS);
            } catch (final InterruptedException e) {
                return; // closed
            }
        }
    }

    /**
     * Re-read the live surfaces and reconcile the announced set against them: announce what is new, retire what is
     * gone. Runs AS the owning machine — the mDNS callbacks stand in no machine of their own.
     */
    private void reconcile(final fURI service) {
        final Machine current = Machine.current();
        Machine.current(this.owner);
        try {
            final Set<String> now = new HashSet<>();
            surfaces().forEach((surface, transport) -> {
                now.add(surface.toString());
                if (null == this.announced.putIfAbsent(surface.toString(), transport))
                    this.responders.forEach(mdns -> this.announce(mdns, service, surface, transport));
            });
            final List<Map.Entry<String, fURI>> gone = List.copyOf(this.announced.entrySet()).stream()
                    .filter(was -> !now.contains(was.getKey())).toList();
            for (final Map.Entry<String, fURI> was : gone) {
                this.announced.remove(was.getKey());
                this.responders.forEach(mdns -> this.retire(mdns, service, f(was.getKey()), was.getValue()));
            }
        } finally {
            Machine.current(current);
        }
    }

    /**
     * Stop announcing a surface that is gone — mDNS is soft state, so un-registering lets the remote rosters age it
     * out rather than shipping a dead address to a peer — and take it out of this machine's own row, which
     * {@link #announce} wrote it into.
     */
    private void retire(final JmDNS mdns, final fURI service, final fURI surface, final fURI transport) {
        mdns.unregisterService(ServiceInfo.create(service.toString(), instanceName(this.hostName, surface),
                surface.port(), 0, 0, txt(surface, transport)));
        this.retract(this.machineOf(surface), f(MTRON), transport, surface);
    }

    private fURI service() {
        final Obj declared = this.at(uri(SERVICE));
        return declared.isNoObj() ? DEFAULT_SERVICE : declared.uriValue();
    }

    /**
     * The DNS-SD instance name for one ENDPOINT: {@code <host name>-<scheme>-<port><route>}, the route as it stands
     * ({@code booger-ws-8555/mtron}). DNS-SD wants one name per registered instance, and an instance is an endpoint:
     * http and ws on the same port are two services, and so are two mtron routes on one socket. With the route left
     * out, or slugged so that {@code /a/b} and {@code /a-b} meet, JmDNS would rename the second ({@code "name (2)"}),
     * a name neither {@link #retire} nor a restart can find again. A route-less address keeps the name it always had.
     * <p>
     * DETERMINISTIC IN THE THING THAT OWNS IT, deliberately: a restart re-registers the SAME name, so its roster
     * entry is reclaimed in place. A minted id would be unique but never reclaimable — after a hard kill (no
     * goodbye) that entry could only age out, a 4500s PTR TTL, while still looking dialable.
     * <p>
     * The HOST is absent from it: the same name on two hosts is legal (the SRV target separates them), which is
     * why {@link #admit} keys the roster on the service uri rather than on this name.
     */
    static String instanceName(final String hostName, final fURI surface) {
        return "%s-%s-%d%s".formatted(hostName, scheme(surface.scheme()), surface.port(),
                surface.basePath().localize());
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
     * WHAT THIS MACHINE ANNOUNCES: every MTRON ENDPOINT it answers on, each reduced to the address a peer dials and
     * paired with its TRANSPORT, the client that carries mtron there (Marko, 2026-11-22: "[ mtron => [ mcp => { }, http
     * => {}, ws => {} ]"). An endpoint is a listener's origin extended by a route whose handler serves mtron:
     * <ul>
     *   <li>an {@code mtron::T} handler ({@code mtron_ws}, {@code mtron_http}) carries it over its own scheme;</li>
     *   <li>metatron's mcp server ({@code mcp_mtron}) carries it over {@code mcp}: its eval_mtron makes an mcp_client of
     *       it a node, from, to and eval.</li>
     * </ul>
     * A peer dials an endpoint, never a socket: a ws_server answers its bare origin with a banner of its routes, so a
     * roster row of {@code ws://host:8555} reaches nothing that speaks mtron. Any other route (another mcp server, a web
     * root, a data address) is not announced, and neither is a wildcard route, which names no address to dial.
     * <p>
     * PRESENCE IS INHERITED, walked as {@link Network.Helper#authorities()} walks it: the sockets of this machine and
     * of every ancestor, each with the routes its level keeps on it, the handler resolved in that level's instruction
     * set. A frame below the walk's start is not visited: its routes on a shared socket are the frame's, and a frame
     * is not a peer. Read on every tick: a boot may mount its listeners after constructing this handler, and a frame
     * may add a route at any time.
     * <p>
     * THE HANDLER IS READ FROM INSTRUCTION MEMORY ALONE ({@code readDeclared}), not through {@link Machine#read} as
     * the server resolves it: a route's target may be a data address ({@code /usr}, {@code docker:},
     * {@code mfs:docs/website/}), and a read of it would list that space on every tick. So a handler type kept in
     * data memory, or a route written as an instruction, is served but not announced.
     * <p>
     * THE ROOT ROUTE EXTENDS NOTHING, as in {@link #dialable}: {@code extend("/")} is a path of one empty segment that
     * prints as no path, so this machine's own row would read back unequal to it and grow on every announcement.
     * <p>
     * VISIBLE FOR TESTING, like {@link #roster}: this set is everything that leaves the box, so it is the surface a
     * leak would show on, and it is checkable without multicast.
     */
    static Map<fURI, fURI> surfaces() {
        final Map<fURI, fURI> endpoints = new LinkedHashMap<>();
        for (Obj at = Machine.current(); at instanceof Machine level; at = level.parent())
            level.network().listeners().values().forEach(entry -> {
                final fURI origin = Network.Helper.originOf(entry);
                if (null == origin)
                    return;
                entry.asRec().at(uri(ROUTE)).orElse(rec0()).elements().forEach(route -> {
                    if (!route.first().isUri() || route.first().uriValue().hasPattern())
                        return;
                    final Obj handler = route.second().isUri()
                            ? level.instset().readDeclared(Machine.Helper.align(level, route.second().uriValue()))
                            : route.second();
                    final fURI endpoint = announceable("/".equals(route.first().uriValue().toString())
                            ? origin
                            : origin.extend(route.first().uriValue()));
                    if (!handler.isType() || null == endpoint)
                        return;
                    if (handler.asType().test(webInstSet.MTRON_TYPE))
                        endpoints.putIfAbsent(endpoint, f(scheme(endpoint.scheme())));
                    else if (handler.asType().test(mcpMetatronBuilder.MCP_MTRON_SERVER_TYPE))
                        endpoints.putIfAbsent(endpoint, f(MCP));
                });
            });
        return endpoints;
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
     * <p>
     * WHAT IT IS AND WHAT CARRIES IT: {@code protocol=mtron} and the transport, so a hearer files the address under
     * {@code machine/<host>/<protocol>/<transport>} without guessing from a scheme that carries several.
     */
    static Map<String, String> txt(final fURI surface, final fURI transport) {
        return Map.of(ROUTE, surface.basePath().localize().toString(), SCHEME, scheme(surface.scheme()),
                PROTOCOL, MTRON, TRANSPORT, transport.toString(), VERSION, METATRON_VERSION);
    }

    /**
     * register one surface as a service instance, its route, protocol and transport in the TXT so a browser can dial it
     */
    private void announce(final JmDNS mdns, final fURI service, final fURI surface, final fURI transport) {
        try {
            mdns.registerService(ServiceInfo.create(service.toString(), instanceName(this.hostName, surface),
                    surface.port(), 0, 0, txt(surface, transport)));
            this.roster(this.machineOf(surface), f(MTRON), transport, surface);
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
     * one MACHINE, written into the roster as {@code <machine> => [<protocol> => [<transport> => uri{+}]]}. The key
     * is the SRV TARGET — the machine identity mDNS already carries — and below it are every address it answers at,
     * each under the protocol it speaks and the transport that carries it, so a machine reachable over http and ws,
     * or on three interfaces, is ONE row rather than three that overwrite.
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
            final fURI protocol = protocol(info);
            final fURI transport = transport(info);
            for (final InetAddress address : info.getInet4Addresses()) {
                if (this.ours(address, info.getPort())) {
                    this.LOG.debug("mdns: heard our own %s", address.getHostAddress());
                    continue;
                }
                final fURI dialable = dialable(address, info.getPort(), scheme, route);
                if (this.roster(machine, protocol, transport, dialable))
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
     * WHAT AN ADDRESS DOES, from the TXT: {@code mtron} when the announcer says nothing, which is what every build
     * before the key announced (an mtron endpoint), and the default for text that is no label, since a responder's TXT is
     * untrusted.
     */
    static fURI protocol(final ServiceInfo info) {
        return f(label(info.getPropertyString(PROTOCOL), MTRON));
    }

    /**
     * WHAT CARRIES IT, from the TXT: the transport an announcer names, else its scheme, which carried mtron for every
     * build before the key.
     */
    static fURI transport(final ServiceInfo info) {
        return f(label(info.getPropertyString(TRANSPORT), scheme(info.getPropertyString(SCHEME))));
    }

    /**
     * add ONE address to a machine's row, keeping any it already had there. The row is
     * {@code <host> => [<protocol> => [<transport> => uri{+}]]}, and only the list at
     * {@code /network/roster/machine/<host>/<protocol>/<transport>} is written: the space builds the levels above it,
     * so the host's other transports, the other hosts and {@code discover} are never rewritten.
     * <p>
     * ONE WRITE, BY THE STORE'S OWN RULE: a value written at a key that holds a stream is appended to it, and at a key
     * that holds one value replaces it ({@code Rec.at}). So an address is written alone onto a list of several, and as
     * {@code {old, new}} over a single one — the whole list written back onto a stream would hold every address
     * twice. A re-heard address is
     * matched by its base path, never by Obj identity: a uri that has round-tripped through the roster does not equal
     * a freshly minted one, even when the two print the same address.
     * <p>
     * VISIBLE FOR TESTING: this is the whole roster contribution of a discovery protocol, and it is testable
     * without multicast, a second host or a resolved {@code ServiceInfo} — which none of the admission path
     * above can be.
     */
    boolean roster(final fURI machine, final fURI protocol, final fURI transport, final fURI address) {
        final fURI canonical = address.basePath();
        // A MACHINE KEY IS A HOST, a path segment of the row's address, and the callers hand one over:
        // machine(ServiceInfo) names the SRV target through segment(), and machineOf(authority) reads host() off the
        // endpoint. A producer that passed a scheme, a port or a DNS root dot would split one machine into rows --
        // which is the producers' job to get right, not this method's to launder.
        final fURI at = Network.ROOT_ROSTER.extend(MACHINE).extend(machine).extend(protocol).extend(transport);
        synchronized (ROSTER) {
            final Obj held = Machine.read(at);
            // ALREADY THERE: a re-heard address is not news, and not a write either
            if (held.stream().anyMatch(known -> known.isUri() && sameAddress(known.uriValue(), canonical)))
                return false;
            final Obj heard = uri(canonical.toString());
            Machine.write(at, held.isObjs() ? heard : held.append(heard));
            return true;
        }
    }

    /**
     * take ONE address back out of a machine's row, the inverse of {@link #roster}: the list at
     * {@code /network/roster/machine/<host>/<protocol>/<transport>} keeps every other address, and the last one takes
     * with it the levels it alone held — its transport, its protocol, the host's row.
     * <p>
     * A write cannot shrink a stream (a value written onto one is appended), so the list is deleted and what is left
     * written into the emptied key. And the space keeps a level its last entry left, as an empty rec
     * ({@code [mtron=>[=>]]}), so an emptied protocol and host are deleted in turn.
     * <p>
     * VISIBLE FOR TESTING, like {@link #roster}.
     */
    boolean retract(final fURI machine, final fURI protocol, final fURI transport, final fURI address) {
        final fURI at = Network.ROOT_ROSTER.extend(MACHINE).extend(machine).extend(protocol).extend(transport);
        synchronized (ROSTER) {
            final List<Obj> addresses = Machine.read(at).stream().toList();
            final List<Obj> kept = addresses.stream()
                    .filter(known -> known.isUri() && !sameAddress(known.uriValue(), address))
                    .toList();
            if (kept.size() == addresses.size())
                return false;
            Machine.write(at, noobj());
            if (!kept.isEmpty())
                Machine.write(at, objs(kept.toArray(Obj[]::new)));
            else {
                final fURI protocolAt = at.retract(1);
                if (Machine.read(protocolAt).elements().findAny().isEmpty()) {
                    Machine.write(protocolAt, noobj());
                    final fURI hostAt = protocolAt.retract(1);
                    if (Machine.read(hostAt).elements().findAny().isEmpty())
                        Machine.write(hostAt, noobj());
                }
            }
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
        return "/".equals(path) ? authority : authority.extend(f(path));
    }

    /**
     * the advertised scheme, or ws — a metatron peer answers over websockets
     */
    static String scheme(final String advertised) {
        return label(advertised, WS);
    }

    /**
     * an advertised label that can stand as a key — a scheme, a protocol, a transport — or {@code otherwise}: a
     * responder's TXT is untrusted text, and a key it shaped would split or corrupt the machine table
     */
    static String label(final String advertised, final String otherwise) {
        return null != advertised && advertised.matches("[a-z][a-z0-9+.-]*") ? advertised : otherwise;
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
     * a withdrawn responder takes back only the addresses it advertised, under the protocol and transport it
     * advertised them ({@link #retract(fURI, fURI, fURI, fURI)})
     */
    private void retract(final ServiceInfo info) {
        final String scheme = info.getPropertyString(SCHEME);
        final String route = info.getPropertyString(ROUTE);
        final fURI machine = machine(info);
        final fURI protocol = protocol(info);
        final fURI transport = transport(info);
        final Machine current = Machine.current();
        Machine.current(this.owner);
        try {
            for (final InetAddress address : info.getInet4Addresses())
                this.retract(machine, protocol, transport, dialable(address, info.getPort(), scheme, route));
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
