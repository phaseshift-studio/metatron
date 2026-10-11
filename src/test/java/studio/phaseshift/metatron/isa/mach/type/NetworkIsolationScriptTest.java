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

package studio.phaseshift.metatron.isa.mach.type;

import org.junit.jupiter.api.AfterEach;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.StatefulCSVSource;
import studio.phaseshift.metatron.StatefulParametrizedTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.network.PeerCluster;

import java.util.Map;

import static studio.phaseshift.metatron.Tokens.HOST;
import static studio.phaseshift.metatron.Tokens.KIND;
import static studio.phaseshift.metatron.Tokens.PATTERN;
import static studio.phaseshift.metatron.Tokens.STATUS;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * NETWORK isolation as a stateful script — the network half of the machine-isolation pair
 * ({@code MemoryIsolationScriptTest} is the memory half), in the same script form so the two read side by side.
 * <p>
 * A mtron expression cannot start a peer cluster, mount a space that claims an authority, or ask a Java-side
 * predicate such as {@code own} / {@code isPeer}. That is what a DIRECTIVE is for, and they are named in CAPS and
 * bracketed like {@code [STATE]} so it is obvious at a glance which lines leave mtron:
 *
 * <pre>
 *   [ROSTER_CLEAR]                 remove every declared peer
 *   [CLUSTER n port]               declare n peer addresses (detached: no processes, no sockets)
 *   [CLUSTER_CLOSE]                close that cluster and clear the roster
 *   [MOUNT_AUTHORITY vid host]     mount a space claiming an authority (the only source of "mine")
 *   [DECLARE_SILENT uri]           declare one peer that can never answer — no socket is opened
 *   [PROBE_PEERS] [PROBE_PEER uri] [PROBE_OWN uri] [PROBE_MINE authority] [PROBE_STATUS]
 *                                  write a Java-side answer to /sys/network/probe/&lt;name&gt;, so the script can
 *                                  assert on a fact mtron cannot yet see
 * </pre>
 * <p>
 * The invariants proven here:
 * <ol>
 *     <li><b>membership is declared</b> — the roster is a rec mtron writes and reads, and the cluster is exactly
 *         the peers it names.</li>
 *     <li><b>never emergent</b> — addressing a stranger answers {@code noobj} and leaves the roster untouched: a
 *         uri cannot make itself a peer merely by being addressed.</li>
 *     <li><b>the cluster view is live</b> — its {@code peer} field IS the roster, so a peer declared after the
 *         view exists is visible through it; it is a pointer, never a snapshot.</li>
 *     <li><b>a silent peer is reported, not omitted</b> — {@code status} yields one entry per DECLARED peer, and
 *         reports it {@code down}; silence and absence are different facts.</li>
 *     <li><b>mine is derived and disjoint from peer</b> — a mounted space's host is an authority this machine
 *         answers to, aliases collapse, and a declared peer is never also "mine".</li>
 * </ol>
 */
public class NetworkIsolationScriptTest extends AbstractMetatronTest {

    /**
     * where a PROBE directive leaves its answer, so a Java-side fact becomes a mtron observation.
     */
    private static final fURI PROBE = f("/sys/network/probe");

    /**
     * the detached cluster a script started, if any — closed after each script so no roster leaks onward
     */
    private PeerCluster cluster;

    private Network network() {
        return Machine.current().network();
    }

    /**
     * A DETACHED cluster is deliberate where peers are only being COUNTED or the roster inspected: real peer
     * addresses and a roster written by the same {@link PeerCluster#connect()} production uses, but no processes
     * and no sockets. Where a peer is instead ASKED something ({@code status}), a no-answer transport is declared
     * — a real address with nothing listening would make the probe dial and log a connection refusal, and that
     * case is about the report, not the refusal. Reaching a peer at all is a later slice: {@code NetworkTest}.
     */
    @Override
    protected Map<String, Directive> directives() {
        return Map.of(
                "ROSTER_CLEAR", args -> Machine.write(Network.Helper.peerRosterPath(), rec(mutableMap())),
                "CLUSTER", args -> {
                    this.closeCluster();
                    this.cluster = PeerCluster.detached(Integer.parseInt(args[0]), "/n", Integer.parseInt(args[1])).connect();
                },
                "CLUSTER_CLOSE", args -> this.closeCluster(),
                "MOUNT_AUTHORITY", args -> mountAuthority(f(args[0]), f(args[1])),
                "DECLARE_SILENT", args -> Machine.write(Network.Helper.peerRosterPath(), rec(mutableMap(
                        uri(args[0]), noAnswerTransport()))),
                "PROBE_PEERS", args -> this.probe("peers", jnt(this.network().peers().size())),
                "PROBE_PEER", args -> this.probe("peer", jnt(this.network().isPeer(f(args[0])) ? 1 : 0)),
                "PROBE_OWN", args -> this.probe("own", jnt(this.network().own(f(args[0])) ? 1 : 0)),
                "PROBE_MINE", args -> this.probe("mine", jnt(this.network().authorities().contains(args[0]) ? 1 : 0)),
                "PROBE_STATUS", args -> {
                    final Obj report = this.network().status(Machine.read(Network.CLUSTER_PATH));
                    final Obj first = report.stream().findFirst().orElse(noobj());
                    final Obj peerStatus = first.isRec() ? (Obj) first.asRec().at(uri(STATUS)) : noobj();
                    this.probe("status", peerStatus.isRec() ? (Obj) peerStatus.asRec().at(uri(KIND)) : noobj());
                });
    }

    @AfterEach
    public void clearNetwork() {
        this.closeCluster();
    }

    private void closeCluster() {
        if (null != this.cluster) {
            this.cluster.close();
            this.cluster = null;
        }
        Machine.write(Network.Helper.peerRosterPath(), rec(mutableMap()));
    }

    private void probe(final String name, final Obj value) {
        Machine.write(PROBE.extend(name), value);
    }

    /**
     * A declared peer that cannot answer — the spelling a dead peer produces, and the one that keeps a probe from
     * opening a socket. The same helper {@code ClusterAwarenessTest} uses.
     */
    private static Obj noAnswerTransport() {
        return instLambda((lhs, inst) -> noobj());
    }

    /**
     * Mount a space that CLAIMS an authority — the only place an authority of this machine comes from. Registering
     * writes the space at its own vid, which needs an owner for the pattern, hence the BOOTING guard.
     */
    private static void mountAuthority(final fURI vid, final fURI host) {
        final boolean booting = BootLoader.BOOTING;
        BootLoader.BOOTING = true;
        try {
            Machine.current().memory().addSpace(memSpace.of(rec(
                    uri(PATTERN), uri(vid + "/#"),
                    uri(HOST), uri(host.toString())), vid));
        } finally {
            BootLoader.BOOTING = booting;
        }
    }

    /**
     * The roster is a rec mtron writes and reads, and the cluster is exactly the peers it names.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "[ROSTER_CLEAR]",
            "[PROBE_PEERS]",
            "*/sys/network/probe/peers % 0                 [-- nothing declared: no peers --]",
            "[CLUSTER 2 21001]                             [-- two peer addresses, declared by the cluster --]",
            "[PROBE_PEERS]",
            "*/sys/network/probe/peers % 2                 [-- the roster's keys ARE the peers --]",
            "[CLUSTER_CLOSE]                               [-- and un-declaring removes them --]",
            "[PROBE_PEERS]",
            "*/sys/network/probe/peers % 0"
    })
    void testMembershipIsDeclared() {
    }

    /**
     * A uri cannot make itself a peer. Addressing a stranger answers {@code noobj} and leaves the roster exactly as
     * it was — the fail-closed rule, observed rather than asserted in Java.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "[ROSTER_CLEAR]",
            "[STATE] *<ws://never-declared:19999/x>        [-- address a host nobody declared --]",
            "[PROBE_PEERS]",
            "*/sys/network/probe/peers % 0                 [-- addressing it did not make it a peer --]",
            "[PROBE_PEER ws://never-declared:19999/x]",
            "*/sys/network/probe/peer % 0",
            "[PROBE_OWN ws://never-declared:19999/x]",
            "*/sys/network/probe/own % 0                   [-- nor is it mine --]"
    })
    void testMembershipIsNeverEmergent() {
    }

    /**
     * The cluster's {@code peer} field is a live pointer at the roster, not a copy — so a peer declared AFTER the
     * view was first read is visible through it. A snapshot would show the empty roster forever.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "[ROSTER_CLEAR]",
            "*/sys/cluster/peer % */sys/peer % true        [-- the field IS the roster, not a copy of it --]",
            "[CLUSTER 1 21003]",
            "*/sys/cluster/peer % */sys/peer % true        [-- still the same thing, now populated --]",
            "[PROBE_PEERS]",
            "*/sys/network/probe/peers % 1                 [-- and the view saw the later declaration --]"
    })
    void testClusterViewIsLive() {
    }

    /**
     * {@code status} asks every DECLARED peer and reports one entry each — a peer that is declared but silent is
     * reported {@code down}, never dropped from the report. Silence and absence are different facts, and the report
     * is worth nothing if it hides the failures.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "[DECLARE_SILENT ws://silent-host:21005]       [-- declared, and it can never answer --]",
            "[PROBE_PEERS]",
            "*/sys/network/probe/peers % 1                 [-- declared, so it is a peer --]",
            "*/sys/cluster/status.count() % 1              [-- and the report names it rather than dropping it --]",
            "[PROBE_STATUS]",
            "*/sys/network/probe/status % \"down\"           [-- DOWN, not absent --]"
    })
    void testStatusReportsASilentPeer() {
    }

    /**
     * "Mine" is derived from what is MOUNTED, never declared twice; aliases collapse; and a declared peer is never
     * also mine — so the two questions cannot both answer yes.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "[MOUNT_AUTHORITY /sys/space/network-script/one ws://0.0.0.0:19191]",
            "[PROBE_MINE 0.0.0.0:19191]",
            "*/sys/network/probe/mine % 1                  [-- a mounted host IS an authority I answer to --]",
            "[PROBE_OWN ws://localhost:19191/x]",
            "*/sys/network/probe/own % 1                   [-- localhost and 0.0.0.0 are one service --]",
            "[PROBE_OWN ws://localhost:19192/x]",
            "*/sys/network/probe/own % 0                   [-- a different port is a different service --]",
            "[CLUSTER 1 21007]",
            "[PROBE_PEER ws://localhost:21007/x]",
            "*/sys/network/probe/peer % 1                  [-- declared, so a peer --]",
            "[PROBE_OWN ws://localhost:21007/x]",
            "*/sys/network/probe/own % 0                   [-- and NOT mine: the two never both answer yes --]"
    })
    void testMineIsDerivedAndDisjointFromPeers() {
    }
}
