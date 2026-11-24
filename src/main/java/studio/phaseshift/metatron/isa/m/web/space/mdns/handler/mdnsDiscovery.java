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

import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.Network;
import studio.phaseshift.metatron.isa.m.sys.type.ThreadExecutor;
import studio.phaseshift.metatron.isa.m.sys.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.m.sys.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Rel;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.m.web.type.mcpMetatronBuilder;
import studio.phaseshift.metatron.isa.m.web.webInstSet;
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
 * what it hears, and what it announces at an address ({@link #ownRow}: never its wildcard or loopback bind), into the
 * roster's machine table: {@code /network/roster/machine/<host>/<protocol>/<transport>}.
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
    private volatile boolean closed = false;
    /**
     * how often the announced set is re-read against the live surfaces — surfaces come and go for the VM's whole
     * life, and a look is local (no round trip), so a short tick costs nothing
     */
    private static final long TICK_MS = 1_000L;
    /**
     * the surfaces already announced — reconciliation announces what is new and retires what is gone
     */
    private final Map<String, fURI> announced = Collections.synchronizedMap(new HashMap<>());
    /**
     * THE ROWS EACH INSTANCE FILED, by its key: a heard service's ({@link ServiceInfo#getKey()}) or one of this
     * machine's surfaces (its address). A removal names only the instance — JmDNS builds the {@code serviceRemoved}
     * event from the PTR record alone, with no server, addresses or TXT, for a goodbye and a cache expiry alike
     * (jmdns 3.6.4/3.6.5, {@code DNSRecord.Pointer.getServiceEvent}) — so what an instance takes out of the roster is what it put
     * in, never what a removal could re-derive.
     * <p>
     * GUARDED BY THE ROSTER'S OWN ADDRESS ({@link Network#ROOT_ROSTER}), the one monitor every writer holds that reads,
     * merges and writes the roster back: this handler, whose JmDNS per local address hear one peer on several threads,
     * and PeerSpawn. So no Java writer drops a row another wrote between its read and its write. A Java monitor binds
     * only Java writers: an mtron write to the roster takes none, and one that lands between a read and its write is
     * dropped.
     */
    private final Map<String, List<Obj>> filed = new HashMap<>();
    /**
     * WHAT EACH RESPONDER LAST RESOLVED FOR AN INSTANCE: {@code <instance key> => <responder> => rows}. Each JmDNS
     * resolves from its own cache, and where a socket hears only its own link (macOS, BSD) two responders can see one
     * instance at different addresses: the instance files the union, and one responder's removal takes back only what
     * no other responder still resolves. Guarded like {@link #filed}.
     */
    private final Map<String, Map<InetAddress, List<Obj>>> heard = new HashMap<>();

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
        synchronized (Network.ROOT_ROSTER) {
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
        // A CLOSED HANDLER TAKES BACK WHAT IT FILED: JmDNS's close sends no removal, so nothing else ever would, and a
        // handler mounted again would file every row over this one's. Its responders are closed above, so nothing
        // files behind it. Run as the owner, as every roster write of this handler is.
        final Machine current = Machine.current();
        Machine.current(this.owner);
        try {
            synchronized (Network.ROOT_ROSTER) {
                List.copyOf(this.filed.keySet()).forEach(instance -> this.roster(instance, List.of()));
                this.heard.clear();
            }
        } finally {
            Machine.current(current);
        }
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
                mdns.addServiceListener(service.toString(), new PeerListener(mdns, address));
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
        // what the initial sweep just announced is the baseline, and this machine's own row files it once; the
        // reconciliation loop below keeps both true as spaces and services come and go for the VM's whole life
        surfaces().forEach((surface, transport) -> {
            this.announced.put(surface.toString(), transport);
            this.roster(surface.toString(), this.ownRow(surface, transport));
        });
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
     * THIS MACHINE IN THE ONE MACHINE TABLE. {@code roster/machine} is the consolidated index of every machine any
     * discovery protocol knows about, this one included, so where an endpoint was learned is nobody's business, and a
     * consumer generating peers reads one table instead of searching across per-protocol listings. Only ENDPOINTS go in
     * it: the interfaces a responder bound are not endpoints (a bare {@code 203.0.113.7} has no scheme and no port, so
     * nothing can be dialed at it), and they are visible where they belong, in the {@code listening} log line.
     * <p>
     * THIS MACHINE'S OWN ROW for one surface, {@code [machine=>[<host>=>[mtron=>[<transport>=><surface>]]]]}, keyed by
     * the host the bind names (an address rather than a name when that is all the uri carries), filed through
     * {@link #roster} once per surface (by {@link #listen}'s baseline and {@link #reconcile}), never once per responder
     * that announces it. NO ROW when the surface's host is the bind wildcard, a loopback ({@link fURI#isLoopbackHost()})
     * or absent. None of them is an address: {@code 0.0.0.0} has to be resolved to the interfaces behind it
     * ({@link #localAddresses()}), and a reader that dials every roster address would reach this machine as its own
     * peer. Other machines learn the surface at the interface addresses the responders announce. Filing no row is
     * filing nothing, so the retire of such a surface takes nothing back.
     * <p>
     * VISIBLE FOR TESTING, like {@link #roster}.
     */
    List<Obj> ownRow(final fURI surface, final fURI transport) {
        final String host = surface.host();
        return null == host || host.isBlank() || surface.isLoopbackHost() ? List.of() : List.of(rec(uri(MACHINE),
                rec(uri(f(host)), rec(uri(MTRON), rec(uri(transport), uri(surface.toString()))))));
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
                if (null == this.announced.putIfAbsent(surface.toString(), transport)) {
                    this.responders.forEach(mdns -> this.announce(mdns, service, surface, transport));
                    // THIS MACHINE'S OWN ROW, filed once per surface — never once per responder that announces it
                    this.roster(surface.toString(), this.ownRow(surface, transport));
                }
            });
            final List<Map.Entry<String, fURI>> gone = List.copyOf(this.announced.entrySet()).stream()
                    .filter(was -> !now.contains(was.getKey())).toList();
            for (final Map.Entry<String, fURI> was : gone) {
                this.announced.remove(was.getKey());
                this.responders.forEach(mdns -> this.retire(mdns, service, f(was.getKey()), was.getValue()));
                this.roster(was.getKey(), List.of());
            }
        } finally {
            Machine.current(current);
        }
    }

    /**
     * Stop announcing a surface that is gone — mDNS is soft state, so un-registering lets the remote rosters age it
     * out rather than shipping a dead address to a peer. This machine's own row is taken back once, by
     * {@link #reconcile}, not once per responder.
     */
    private void retire(final JmDNS mdns, final fURI service, final fURI surface, final fURI transport) {
        mdns.unregisterService(ServiceInfo.create(service.toString(), instanceName(this.hostName, surface),
                surface.port(), 0, 0, txt(surface, transport)));
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
     * why a heard instance is tracked by its {@code ServiceInfo.getKey()} and filed under its SRV target, never by
     * this name alone.
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
     * <p>
     * VISIBLE FOR TESTING, like {@link #roster}: a test hands in a {@code ServiceInfoImpl} that carries its addresses.
     */
    void admit(final InetAddress responder, final ServiceInfo info) {
        final Machine current = Machine.current();
        Machine.current(this.owner);
        try {
            if (!info.getPropertyNames().hasMoreElements()) {
                this.LOG.debug("mdns: %s resolved without a TXT record — waiting for a complete one", info.getName());
                return;
            }
            // A RESOLUTION WITHOUT ADDRESSES IS NOT ONE THAT HAS NONE: the A records arrive on their own, and filing
            // what this one names would take back everything the instance filed before
            final InetAddress[] addresses = info.getInet4Addresses();
            if (0 == addresses.length) {
                this.LOG.debug("mdns: %s resolved without an address — waiting for a complete one", info.getName());
                return;
            }
            final String scheme = info.getPropertyString(SCHEME);
            final String route = info.getPropertyString(ROUTE);
            final fURI machine = machine(info);
            final fURI protocol = protocol(info);
            final fURI transport = transport(info);
            final List<Obj> rows = new ArrayList<>();
            for (final InetAddress address : addresses) {
                if (this.ours(address, info.getPort())) {
                    this.LOG.debug("mdns: heard our own %s", address.getHostAddress());
                    continue;
                }
                rows.add(rec(uri(MACHINE), rec(uri(machine), rec(uri(protocol), rec(uri(transport),
                        uri(dialable(address, info.getPort(), scheme, route).basePath().toString()))))));
            }
            synchronized (Network.ROOT_ROSTER) {
                // A CLOSED HANDLER HEARS NOTHING: a resolution JmDNS queued before the close still runs after it
                if (this.closed)
                    return;
                final Map<InetAddress, List<Obj>> responders = this.heard.computeIfAbsent(info.getKey(), key -> new HashMap<>());
                responders.put(responder, rows);
                if (this.roster(info.getKey(), responders.values().stream().flatMap(List::stream).toList()) > 0)
                    LOG.status(DEBUG, "mdns: discovered %s: %s", machine, rows);
            }
        } finally {
            Machine.current(current);
        }
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
     * THE ROWS ONE INSTANCE FILES IN THE ROSTER, made so: each row it newly files is added, each it filed before and
     * files no more is taken back, and an instance that files nothing has left. A row is
     * {@code [machine=>[<host>=>[<protocol>=>[<transport>=><address>]]]]}, merged as the ring merges it: the roster is
     * read, {@code Rec.plus(row)} or {@code Rec.minus(row)} (a deep merge with the row's coefficients inverted), and
     * written back. The roster keeps the count, so an address two instances file stays until both take it back, and
     * a read nets the terms ({@link Network#machines()}).
     * <p>
     * AN INSTANCE TAKES BACK ONLY WHAT IT FILED, and only while it is still listed: a row taken back twice — or after
     * the table was wiped — leaves a negative count that the next filing cancels, and the address would be filed and
     * absent at once. Every row it takes back and files lands in ONE write.
     * <p>
     * The levels a host's last address leaves stay, an empty rec: its addresses net to nothing, and
     * {@link Network#machines()} lists no machine without one.
     * <p>
     * VISIBLE FOR TESTING: this is the whole roster contribution of a discovery protocol, testable without multicast
     * or a second host.
     *
     * @return how many rows were newly filed
     */
    int roster(final String instance, final List<Obj> rows) {
        synchronized (Network.ROOT_ROSTER) {
            // ONCE CLOSED, NOTHING IS FILED: close() took back what this handler filed, and a filing after it -- a
            // resolution JmDNS had queued, the baseline of a listen() the close overtook -- would never be taken back.
            // close() sets the flag before it takes this monitor, so whatever runs after its take-back sees it.
            final List<Obj> now = this.closed ? List.of() : rows.stream().distinct().toList();
            final List<Obj> before = this.filed.getOrDefault(instance, List.of());
            final List<Obj> added = now.stream().filter(row -> !before.contains(row)).toList();
            Rec merged = Machine.read(Network.ROOT_ROSTER).orElse(rec0()).asRec();
            boolean changed = !added.isEmpty();
            for (final Obj row : before) {
                if (now.contains(row))
                    continue;
                // TAKE BACK ONLY WHAT IS STILL LISTED. A level wiped since (the machine table written noobj), or a row
                // someone else took back, holds no count of this address: a take-back there would leave a negative
                // count -- or, into an absent level, a sign on the level's rec -- that the next filing cancels. A row
                // has one key per level, walked down the roster beside it.
                Obj filedAt = row;
                Obj listedAt = merged;
                while (filedAt.isRec()) {
                    final Rel step = filedAt.asRec().<Rel>elements().findFirst().orElseThrow();
                    filedAt = step.second();
                    listedAt = !listedAt.isRec() ? noobj() : listedAt.asRec().<Rel>elements()
                            .filter(rel -> rel.first().isUri() && step.first().uriValue().toString().equals(rel.first().uriValue().toString()))
                            .map(Rel::second).findFirst().orElse(noobj());
                }
                final String address = filedAt.uriValue().toString();
                if (objs(listedAt.stream().toList()).stream()
                        .anyMatch(known -> known.isUri() && address.equals(known.uriValue().toString()) && known.c().gt(cInt.ZERO()))) {
                    merged = merged.minus(row.asRec());
                    changed = true;
                }
            }
            for (final Obj row : added)
                merged = merged.plus(row.asRec());
            // ONE WRITE for every row the instance takes back and files: a reader never sees the instance between them
            if (changed)
                Machine.write(Network.ROOT_ROSTER, merged);
            if (now.isEmpty())
                this.filed.remove(instance);
            else
                this.filed.put(instance, now);
            return added.size();
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
     * a withdrawn instance takes back what it filed. The removal names only the instance ({@link #filed}), so nothing
     * is re-derived from it: what goes is what this responder's resolution put in and no other responder still
     * resolves.
     * <p>
     * VISIBLE FOR TESTING, like {@link #admit}: {@code ServiceInfo.create} builds exactly the address-less info a
     * removal carries.
     */
    void retract(final InetAddress responder, final ServiceInfo info) {
        final Machine current = Machine.current();
        Machine.current(this.owner);
        try {
            synchronized (Network.ROOT_ROSTER) {
                final Map<InetAddress, List<Obj>> responders = this.heard.getOrDefault(info.getKey(), new HashMap<>());
                responders.remove(responder);
                if (responders.isEmpty())
                    this.heard.remove(info.getKey());
                this.roster(info.getKey(), responders.values().stream().flatMap(List::stream).toList());
            }
        } finally {
            Machine.current(current);
        }
    }

    private final class PeerListener implements ServiceListener {

        private final JmDNS mdns;
        /**
         * the local address this listener's responder is bound to: what tells one responder's resolution of an
         * instance from another's
         */
        private final InetAddress responder;

        private PeerListener(final JmDNS mdns, final InetAddress responder) {
            this.mdns = mdns;
            this.responder = responder;
        }

        @Override
        public void serviceAdded(final ServiceEvent event) {
            // added names the instance; the SRV/A only arrive with the resolve, so ask for it explicitly
            this.mdns.requestServiceInfo(event.getType(), event.getName(), true);
        }

        @Override
        public void serviceResolved(final ServiceEvent event) {
            mdnsDiscovery.this.admit(this.responder, event.getInfo());
        }

        @Override
        public void serviceRemoved(final ServiceEvent event) {
            mdnsDiscovery.this.retract(this.responder, event.getInfo());
        }
    }
}
