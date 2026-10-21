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

package studio.phaseshift.metatron.isa.mach.network;

import org.junit.jupiter.api.AfterEach;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.Network;

import java.util.LinkedHashMap;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * THE NETWORK SCRIPT HARNESS — the directives and configuration a network stateful-script suite is written
 * against, so that a suite proves a NETWORK claim instead of re-implementing the plumbing.
 * {@code NetworkIsolationScriptTest} is the roster-half suite built on it.
 * <p>
 * A mtron expression cannot declare a peer that can never answer, start a detached cluster, or ask a Java-side
 * predicate such as {@code isPeer}. That is what a DIRECTIVE is for, and they are named in CAPS and bracketed
 * like {@code [STATE]} so it is obvious at a glance which lines leave mtron. A PROBE directive writes its answer
 * to {@code /m/networkProbe/<name>}, so a Java-side fact becomes a mtron observation the script asserts on — the
 * observation stays in the script.
 *
 * <pre>
 *   [ROSTER_CLEAR]                 remove every declared peer
 *   [CLUSTER n port]               declare n peer addresses (detached: no processes, no sockets)
 *   [CLUSTER_CLOSE]                close that cluster and clear the roster
 *   [DECLARE_SILENT uri]           declare one peer that can never answer — no socket is opened
 *   [CONNECT uri [client]]         dial through the java face
 *   [DISCONNECT uri]               hang up one reach
 *   [PROBE_MACHINES] [PROBE_PEER uri] [PROBE_STATUS] [PROBE_PEERCOUNT]
 *                                  write a Java-side answer to /m/networkProbe/&lt;name&gt;
 * </pre>
 * <p>
 * "Is this address MINE?" is deliberately NOT a directive. {@code own()} and {@link Network.Helper#authorities()}
 * still answer it, and {@code authorities()} is still what mDNS announces, but nothing ROUTES on it: the listener
 * declines a self co-mount with its loopback test ({@code Network.accept}), and reach is decided by
 * {@code transportOf}'s service comparison. The identity half therefore belongs to {@code NetworkIdentityTest},
 * which asks the predicates directly — and a suite that needs a mounted authority can add its own directive;
 * {@code NetworkIdentityTest}'s mount helper is the eight-line recipe.
 * <p>
 * THE ROSTER IS A CONTAINER, AND THAT IS A TRAP WORTH NAMING. The address {@code /network/roster} holds
 * {@code [discover, machine]}, so {@code network().roster()} answers the CONTAINER — not the list of machines
 * anyone means by "the roster". The listing is {@code network().machines()}, its {@code machine} field. The
 * directive is therefore named {@code [PROBE_MACHINES]} and counts machine rows; naming it after
 * {@code roster()} would promise the container and deliver a machine count — the kind of word/semantics mismatch
 * that costs a reader a wrong number. Those rows carry TWO conventions in one table:
 * {@code <authority> => transport} for a declared/dialed peer ({@code PeerCluster.connect},
 * {@code [DECLARE_SILENT]}), and {@code <host> => address{+}} for a discovered machine (mDNS). So the count is
 * "rows in the machine table", not "servers declared by this script" — a suite that wants the latter clears the
 * roster first ({@code [ROSTER_CLEAR]}) and runs no discovery source, which is what these suites do.
 * <p>
 * These scripts drive the KNOWN-SERVER ROSTER at {@code /network/roster} — one shared address rather than
 * per-machine state. The CONNECTIONS a machine holds are a different question and live on the component
 * ({@code network.peer}); {@code NetworkWireScriptTest} is what pins those.
 * <p>
 * A suite extends the vocabulary by overriding {@link #directives()} on a copy of this one —
 * {@code new LinkedHashMap<>(super.directives())} — and adding its own names, so every script speaks one
 * vocabulary whatever the suite. The cluster and the roster are torn down after each script, so no declaration
 * leaks into the next test.
 */
public abstract class NetworkScriptTest extends AbstractMetatronTest {

    /**
     * where a PROBE directive leaves its answer, so a Java-side fact becomes a mtron observation
     */
    protected static final fURI PROBE = f("/m/networkProbe");

    /**
     * the roster's home as an address — read through the accessor so a re-point cannot leave this harness behind
     */
    protected static final fURI ROSTER_PATH = Network.ROOT_ROSTER;

    /**
     * the detached cluster a script started, if any — closed after each script so no roster leaks onward
     */
    private PeerCluster cluster;

    protected Network network() {
        return Machine.current().network();
    }

    /**
     * The network vocabulary. A suite that needs more names copies this map and adds its own (see the class
     * javadoc), which keeps the base a fixed, readable set rather than a per-suite grab bag.
     */
    @Override
    protected Map<String, Directive> directives() {
        final Map<String, Directive> all = new LinkedHashMap<>();
        all.put("ROSTER_CLEAR", args -> Machine.write(Network.ROOT_ROSTER, rec(mutableMap())));
        // A DETACHED cluster is deliberate where peers are only being COUNTED or the roster inspected: real peer
        // addresses and a roster written by the same PeerCluster.connect() production uses, but no processes and
        // no sockets. Where a peer is instead ASKED something (status), a no-answer transport is declared
        // (DECLARE_SILENT) — a real address with nothing listening would make the probe dial and log a
        // connection refusal, and that case is about the report, not the refusal.
        all.put("CLUSTER", args -> {
            this.closeCluster();
            this.cluster = PeerCluster.detached(Integer.parseInt(args[0]), "/n", Integer.parseInt(args[1])).connect();
        });
        all.put("CLUSTER_CLOSE", args -> this.closeCluster());
        // INTO roster/machine: the roster is a container ([discover, machine]) and a reach declared at
        // its root lands where no reader looks
        all.put("DECLARE_SILENT", args -> Machine.write(Network.ROOT_ROSTER, rec(mutableMap(
                uri(MACHINE), rec(mutableMap(uri(args[0]), noAnswerTransport()))))));
        // THE LISTING, NOT THE CONTAINER. machines() is roster().at(machine) and roster() reads
        // Machine.read(ROOT_ROSTER), so it is the roster's machine table -- named machines, not roster,
        // because roster() itself answers the container [discover, machine]. The count is over BOTH row
        // conventions (declared <authority> => transport, discovered <host> => address{+}), so a suite
        // that wants "what this script declared" clears the roster first and runs no discovery source.
        all.put("PROBE_MACHINES", args -> this.probe("machines", jnt(this.network().machines().jvm().size())));
        all.put("PROBE_PEER", args -> this.probe("peer", jnt(this.network().isPeer(f(args[0])) ? 1 : 0)));
        all.put("PROBE_STATUS", args -> {
            final Obj report = this.network().status(noobj());
            final Obj first = report.stream().findFirst().orElse(noobj());
            final Obj peerStatus = first.isRec() ? (Obj) first.asRec().at(uri(STATUS)) : noobj();
            this.probe("status", peerStatus.isRec() ? (Obj) peerStatus.asRec().at(uri(KIND)) : noobj());
        });
        all.put("PROBE_PEERCOUNT", args -> {
            LOG.debug("probe peercount machine=%s net=%s count=%s", System.identityHashCode(Machine.current()),
                    System.identityHashCode(this.network()), this.network().peers().elements().count());
            this.probe("peercount", jnt(this.network().peers().elements().count()));
        });
        all.put("DISCONNECT", args -> this.network().disconnect(f(args[0])));
        // the java face of connect: the mtron inst resolves in some boots and not others (suite-dependent inst
        // resolution -- an open item pinned in NetworkWireScriptTest), so the dial goes through the proven path
        all.put("CONNECT", args -> {
            if (args.length > 1)
                this.network().connect(f(args[0]), f(args[1]));
            else
                this.network().connect(f(args[0]));
        });
        return all;
    }

    @AfterEach
    public void clearNetworkScript() {
        this.closeCluster();
        // the roster is an ADDRESS, not a Java field, so a test that wrote into it clears it by address too
        Machine.write(ROSTER_PATH, rec(mutableMap()));
    }

    protected void closeCluster() {
        if (null != this.cluster) {
            this.cluster.close();
            this.cluster = null;
        }
        Machine.write(Network.ROOT_ROSTER, rec(mutableMap()));
    }

    protected void probe(final String name, final Obj value) {
        Machine.write(PROBE.extend(name), value);
    }

    /**
     * A declared peer that cannot answer — the spelling a dead peer produces, and the one that keeps a probe from
     * opening a socket.
     */
    protected static Obj noAnswerTransport() {
        return instLambda((lhs, inst) -> noobj());
    }
}
