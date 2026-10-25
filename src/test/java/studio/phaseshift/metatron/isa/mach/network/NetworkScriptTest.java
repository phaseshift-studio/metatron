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
import static studio.phaseshift.metatron.isa.m.mInstSet.EVAL_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.TO_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * THE NETWORK SCRIPT HARNESS — the directives and configuration a network stateful-script suite is written
 * against, so that a suite proves a NETWORK claim instead of re-implementing the plumbing.
 * {@code NetworkIsolationScriptTest} is the roster-half suite built on it.
 * <p>
 * A mtron expression cannot dial a peer that can never answer, start a detached cluster, or ask a Java-side
 * predicate such as {@code isPeer}. That is what a DIRECTIVE is for, and they are named in CAPS and bracketed
 * like {@code [STATE]} so it is obvious at a glance which lines leave mtron. A PROBE directive writes its answer
 * to {@code /m/networkProbe/<name>}, so a Java-side fact becomes a mtron observation the script asserts on — the
 * observation stays in the script.
 *
 * <pre>
 *   [ROSTER_CLEAR]                 remove every declared peer
 *   [CLUSTER n port]               declare n peer addresses (detached: no processes, no sockets)
 *   [CLUSTER_CLOSE]                close that cluster and clear the roster
 *   [DECLARE uri]                  declare one machine's address in the roster — knowledge, nothing is dialed
 *   [DIAL_SILENT uri]              dial one peer whose client is never live — no socket is opened
 *   [CONNECT uri [client]]         dial through the java face
 *   [DISCONNECT uri]               hang up one reach
 *   [PROBE_MACHINES] [PROBE_PEER uri] [PROBE_STATUS] [PROBE_PEERCOUNT]
 *                                  write a Java-side answer to /m/networkProbe/&lt;name&gt;
 * </pre>
 * <p>
 * "Is this address MINE?" is deliberately NOT a directive. {@link Network.Helper#authorities()} still answers it —
 * the mounted listeners, which is what mDNS announces — but nothing ROUTES on it: the listener
 * declines a self co-mount with its loopback test ({@code Network.accept}), and reach is decided by the dialed
 * peers' service comparison ({@code peerOf}). The identity half therefore belongs to {@code NetworkIdentityTest},
 * which asks the predicates directly — and a suite that needs a mounted authority can add its own directive;
 * {@code NetworkIdentityTest}'s {@code mountListener} is the recipe.
 * <p>
 * THE ROSTER IS A CONTAINER, AND THAT IS A TRAP WORTH NAMING. The address {@code /network/roster} holds
 * {@code [discover, machine]}, so {@code network().roster()} answers the CONTAINER — not the list of machines
 * anyone means by "the roster". The listing is {@code network().machines()}, its {@code machine} field. The
 * directive is therefore named {@code [PROBE_MACHINES]} and counts machine rows; naming it after
 * {@code roster()} would promise the container and deliver a machine count — the kind of word/semantics mismatch
 * that costs a reader a wrong number. Every row is {@code <key> => address{+}} — a declared machine
 * ({@code PeerCluster.connect}, {@code [DECLARE]}) keyed by its authority, a discovered one (mDNS) by its host — so
 * the count is "rows in the machine table", not "servers declared by this script": a suite that wants the latter
 * clears the roster first ({@code [ROSTER_CLEAR]}) and runs no discovery source, which is what these suites do.
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

    /**
     * the never-live peers a script dialed ({@code [DIAL_SILENT]}) — hung up after each script
     */
    private final java.util.List<fURI> silent = new java.util.ArrayList<>();

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
        // no sockets. Where a peer is instead ASKED something (status), a never-live client is dialed
        // (DIAL_SILENT) — a real address with nothing listening would make a dial wait out its connect timeout and
        // log a refusal, and that case is about the report, not the refusal.
        all.put("CLUSTER", args -> {
            this.closeCluster();
            this.cluster = PeerCluster.detached(Integer.parseInt(args[0]), "/n", Integer.parseInt(args[1])).connect();
        });
        all.put("CLUSTER_CLOSE", args -> this.closeCluster());
        // KNOWLEDGE: an address row, INTO roster/machine — the roster is a container ([discover, machine]) and a
        // row declared at its root lands where no reader looks
        all.put("DECLARE", args -> Machine.write(Network.ROOT_ROSTER, rec(mutableMap(
                uri(MACHINE), rec(mutableMap(uri(args[0]), uri(args[0])))))));
        // REACH THAT IS DOWN: a client that declares what a peer is talked to with, and whose status says it is not
        // live — the client a dead link leaves. Indexed as a constructor indexes itself; hung up after the script.
        all.put("DIAL_SILENT", args -> {
            this.silent.add(f(args[0]));
            this.network().index(silentClient(f(args[0])));
        });
        // THE LISTING, NOT THE CONTAINER. machines() is roster().at(machine) and roster() reads
        // Machine.read(ROOT_ROSTER), so it is the roster's machine table -- named machines, not roster,
        // because roster() itself answers the container [discover, machine]. The count is over every row
        // (declared <authority> => address, discovered <host> => address{+}), so a suite that wants "what
        // this script declared" clears the roster first and runs no discovery source.
        all.put("PROBE_MACHINES", args -> this.probe("machines", jnt(this.network().machines().jvm().size())));
        all.put("PROBE_PEER", args -> this.probe("peer", jnt(this.network().isPeer(f(args[0])) ? 1 : 0)));
        all.put("PROBE_STATUS", args -> {
            // the status method's own receiver: the network, whose dialed peers it reports (the roster when none)
            final Obj report = this.network().status(this.network());
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
        this.silent.forEach(this.network()::disconnect);
        this.silent.clear();
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
     * A dialed client that cannot answer — what a dead link leaves behind: it declares the instructions a peer is
     * talked to with (each answering a fail), and its status says it is not live. No socket is opened.
     */
    protected static Obj silentClient(final fURI host) {
        return rec(
                uri(HOST), uri(host),
                uri(INST), rec(
                        uri(EVAL_INST_TID), Network.Helper.override(EVAL_INST_TID, (lhs, inst) -> fail("%s does not answer", host)),
                        uri(TO_INST_TID), Network.Helper.override(TO_INST_TID, (lhs, inst) -> fail("%s does not answer", host))),
                uri(STATUS), Network.Helper.statusOf(() -> false));
    }
}
