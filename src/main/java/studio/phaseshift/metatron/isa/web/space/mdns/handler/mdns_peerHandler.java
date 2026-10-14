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
import studio.phaseshift.metatron.isa.m.type.Type;
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

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashSet;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * DISCOVERY OVER mDNS — announces the authorities this machine answers to on the local network, and writes what it
 * hears from others into {@link Network#ROOT_ROSTER}. It serves no bytes: a peer list is its whole product, which
 * is why it feeds the roster rather than answering an address.
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
public class mdns_peerHandler extends MRec implements Closeable {

    public static final fURI MDNS_PEER_HANDLER_TID = webInstSet.WEB_ISA_TID.extend("mdns").extend("mdns_peer");
    public static final fURI DEFAULT_SERVICE = f("_metatron._tcp.local.");
    protected final GraphittyLogger LOG = Graphitty.log(this);

    public static final Type MDNS_PEER_HANDLER_TYPE = Type.Builder.build()
            .tid(webInstSet.MDNS_TID)
            .vid(MDNS_PEER_HANDLER_TID)
            .isaPredicate(rec(
                    uri(SERVICE).maybe().asUri(), URI_TYPE,
                    uri(INTERFACE).maybe().asUri(), URI_TYPE))
            .constructor(mdns -> new mdns_peerHandler(new LinkedHashMap<>(mdns.asRec().jvm()), mdns.asRec().vid()).start())
            .create();

    /**
     * the machine the handler was started in — a callback must re-establish it, never assume it
     */
    private final Machine owner = Machine.current();
    private final List<JmDNS> responders = new ArrayList<>();
    private boolean started = false;
    private boolean closed = false;

    public mdns_peerHandler(final Map<Obj, Obj> jvm, final fURI vid) {
        super(jvm, MDNS_PEER_HANDLER_TID, vid);
        // A SOURCE IS LIVE ONCE IT EXISTS, like a space registering with the router on construction: whoever
        // builds a handler wants discovery, not a handle they must remember to start.
    }

    /**
     * announce on every local address we can, and listen for the same from others. Idempotent, and a no-op with a
     * warning when nothing multicast-capable is present — a discovery handler must never be why a boot fails.
     */
    public mdns_peerHandler start() {
        if (this.started || this.closed)
            return this;
        this.started = true;
        // the WHOLE start runs as the machine that owns this handler: it reads authorities() and takes its name
        // from the network, and the thread it is started on is not that machine.
        ThreadExecutor.instance().execute(this::listen);
        return this;
    }

    private void listen() {
        final fURI service = this.service();
        for (final InetAddress address : this.localAddresses()) {
            try {
                final JmDNS mdns = JmDNS.create(address, this.hostName());
                Network.Helper.authorities().stream()
                        .filter(authority -> null != authority.host() && authority.port() > 0)
                        .forEach(authority -> this.announce(mdns, service, authority));
                mdns.addServiceListener(service.toString(), new PeerListener(mdns));
                this.responders.add(mdns);
                this.LOG.info("mdns: listening on %s as %s", address.getHostAddress(), this.hostName());
            } catch (final IOException e) {
                this.LOG.warn("mdns: cannot listen on %s (%s)", address.getHostAddress(), e.getMessage());
            }
        }
        if (this.responders.isEmpty())
            this.LOG.warn("mdns: no multicast-capable interface — discovery is inert here");
    }

    @Override
    public void close() {
        this.closed = true;
        this.responders.forEach(mdns -> {
            mdns.unregisterAllServices();
            CommonUtil.close(mdns);
        });
        this.responders.clear();
        this.started = false;
    }

    public fURI service() {
        final Obj declared = this.at(uri(SERVICE));
        return declared.isNoObj() ? DEFAULT_SERVICE : declared.uriValue();
    }

    /** the mDNS HOST name of this machine — what JmDNS names the interface, not a registered service */
    private String hostName() {
        return Machine.current().network().name().orElse("metatron");
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
        return "%s-%s-%d".formatted(this.hostName(), scheme(authority.scheme()), authority.port());
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
     * register the authority as a service instance, its route in the TXT so a browser can dial it
     */
    private void announce(final JmDNS mdns, final fURI service, final fURI authority) {
        try {
            // SEPARATE TXT ENTRIES, never one comma-joined string: getPropertyString returns everything after the
            // FIRST '=', so "route=/,version=0.1" comes back as route=[/,version=0.1] — measured.
            mdns.registerService(ServiceInfo.create(service.toString(), this.instanceName(authority), authority.port(), 0, 0,
                    Map.of(ROUTE, authority.localize().toString(), SCHEME, scheme(authority.scheme()),
                            VERSION, METATRON_VERSION)));
        } catch (final IOException e) {
            this.LOG.warn("mdns: cannot announce %s (%s)", authority, e.getMessage());
        }
    }

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
     */
    private void admit(final ServiceInfo info) {
        final String scheme = info.getPropertyString(SCHEME);
        final String route = info.getPropertyString(ROUTE);
        final fURI machine = machine(info);
        for (final InetAddress address : info.getInet4Addresses()) {
            if (this.ours(address, info.getPort())) {
                this.LOG.debug("mdns: heard our own %s", address.getHostAddress());
                continue;
            }
            final fURI dialable = dialable(address, info.getPort(), scheme, route);
            this.LOG.info("mdns: discovered %s at %s", machine, dialable);
            this.roster(machine, dialable);
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
     * The identity a responder is keyed by: the SRV TARGET — the machine mDNS names — scoped to the SURFACE that
     * advertised it, so one entry's values are all the same scheme and a consumer can dial without filtering.
     * <p>
     * Not the instance name: DNS-SD permits the same name on two hosts, so a name-keyed roster collapses every
     * responder onto one entry (measured). Not the bare machine either: http and ws on the same host:port are
     * DIFFERENT services, and an authority-only key lets a ws lookup return an http address.
     */
    static fURI machine(final ServiceInfo info) {
        return f("").scheme(scheme(info.getPropertyString(SCHEME))).host(segment(info.getServer()));
    }

    /** the roster as a mutable map. A rec cannot be ADDRESSED by a uri key, so it is read, amended, written back */
    private Map<Obj, Obj> entries() {
        final Obj current = Machine.read(Network.ROOT_ROSTER);
        return new LinkedHashMap<>(current.isRec() ? current.asRec().jvm() : Map.of());
    }

    /**
     * add ONE address to a machine's entry, keeping any it already had. Matching is by TEXT, never by Obj
     * identity: a uri that has round-tripped through the roster does not equal a freshly minted one, even when
     * the two print the same address.
     */
    private void roster(final fURI machine, final fURI address) {
        // STORED AS ITS BASE PATH: what a peer is is its address, and a coefficient, poly or ?query is call
        // semantics that arrives with the message rather than part of where the machine lives.
        final fURI canonical = address.basePath();
        final Map<Obj, Obj> entries = this.entries();
        final String key = machine.toString();
        final List<Obj> addresses = new ArrayList<>();
        entries.entrySet().stream()
                .filter(entry -> entry.getKey().isUri() && key.equals(entry.getKey().uriValue().toString()))
                .map(Map.Entry::getValue).findFirst().ifPresent(known -> known.stream().forEach(addresses::add));
        if (addresses.stream().noneMatch(known -> known.isUri() && sameAddress(known.uriValue(), canonical)))
            addresses.add(uri(canonical.toString()));
        entries.entrySet().removeIf(entry -> entry.getKey().isUri() && key.equals(entry.getKey().uriValue().toString()));
        entries.put(uri(key), objs(addresses.toArray(Obj[]::new)));
        Machine.write(Network.ROOT_ROSTER, rec(entries));
    }

    /**
     * THE ROSTER HOLDS URIs THAT ARE USABLE AS THEY STAND: a value can be handed straight to a client as its
     * host (read at an index from the roster) with nothing to take apart. Two things can corrupt
     * that — a responder's route is UNTRUSTED text, and a DNS-SD instance name is not necessarily a valid uri
     * segment (JmDNS disambiguates a collision as {@code "name (2)"}) — so both are normalized on the way in.
     */
    static fURI dialable(final InetAddress address, final int port, final String advertisedScheme,
                         final String advertisedRoute) {
        return f("").scheme(scheme(advertisedScheme)).host(address.getHostAddress()).port(port)
                .extend(route(advertisedRoute));
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
        final String slug = (null == instance ? "" : instance).replaceAll("[^A-Za-z0-9._-]", "-")
                .replaceAll("-{2,}", "-").replaceAll("^-+|-+$", "");
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
        Machine.current(this.owner, () -> {
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
            Machine.write(Network.ROOT_ROSTER, rec(entries));
            return noobj();
        });
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
            mdns_peerHandler.this.admit(event.getInfo());
        }

        @Override
        public void serviceRemoved(final ServiceEvent event) {
            mdns_peerHandler.this.retract(event.getInfo());
        }
    }
}
