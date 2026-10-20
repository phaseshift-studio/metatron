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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.StatefulCSVSource;
import studio.phaseshift.metatron.StatefulParametrizedTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.machInstSet;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.Network;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.*;
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
 *   [PROBE_ROSTER] [PROBE_PEER uri] [PROBE_OWN uri] [PROBE_MINE authority] [PROBE_STATUS]
 *                                  write a Java-side answer to /m/networkProbe/&lt;name&gt;, so the script can
 *                                  assert on a fact mtron cannot yet see
 * </pre>
 * <p>
 * These scripts drive the KNOWN-SERVER ROSTER ({@code /network/roster}) — the list of machines the system knows about,
 * one shared address rather than per-machine state. The CONNECTIONS a machine holds are a different question and
 * live on the component ({@code network.peer}); that is what {@code MachPushTest} pins.
 * <p>
 * The invariants proven here:
 * <ol>
 *     <li><b>declaration is explicit</b> — the roster is a rec mtron writes and reads, and the cluster is exactly
 *         the servers it names.</li>
 *     <li><b>never emergent</b> — addressing a stranger answers {@code noobj} and leaves the roster untouched: a
 *         uri cannot make itself known merely by being addressed.</li>
 *     <li><b>the cluster view is live</b> — its {@code peer} field IS the roster, so a server declared after the
 *         view exists is visible through it; it is a pointer, never a snapshot.</li>
 *     <li><b>a silent peer is reported, not omitted</b> — {@code status} yields one entry per DECLARED peer, and
 *         reports it {@code down}; silence and absence are different facts.</li>
 *     <li><b>mine is derived and disjoint from peer</b> — a mounted space's host is an authority this machine
 *         answers to, aliases collapse, and a declared peer is never also "mine".</li>
 * </ol>
 */
@Isolated
public class NetworkIsolationScriptTest extends AbstractMetatronTest {

    /**
     * where a PROBE directive leaves its answer, so a Java-side fact becomes a mtron observation.
     */
    private static final fURI PROBE = f("/m/networkProbe");

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
        // Map.of caps at ten pairs, which this suite has reached — the overflow lives in a mutable copy
        final Map<String, Directive> all = new java.util.LinkedHashMap<>(Map.<String, Directive>of(
                "ROSTER_CLEAR", args -> Machine.write(Network.ROOT_ROSTER, rec(mutableMap())),
                "CLUSTER", args -> {
                    this.closeCluster();
                    this.cluster = PeerCluster.detached(Integer.parseInt(args[0]), "/n", Integer.parseInt(args[1])).connect();
                },
                "CLUSTER_CLOSE", args -> this.closeCluster(),
                "MOUNT_AUTHORITY", args -> mountAuthority(f(args[0]), f(args[1])),
                // INTO roster/machine: the roster is a container ([discover, machine]) and a reach declared at
                // its root lands where no reader looks
                "DECLARE_SILENT", args -> Machine.write(Network.ROOT_ROSTER, rec(mutableMap(
                        uri(MACHINE), rec(mutableMap(uri(args[0]), noAnswerTransport()))))),
                // THE MACHINE TABLE, not the container: roster().jvm().size() counts the container's own fields,
                // which is 1 whatever the cluster size -- the declared servers are its machine rows
                "PROBE_ROSTER", args -> this.probe("roster", jnt(this.network().machines().jvm().size())),
                "PROBE_PEER", args -> this.probe("peer", jnt(this.network().isPeer(f(args[0])) ? 1 : 0)),
                "PROBE_OWN", args -> this.probe("own", jnt(this.network().own(f(args[0])) ? 1 : 0)),
                "PROBE_MINE", args -> this.probe("mine", jnt(Network.Helper.authorities().stream()
                        .anyMatch(mine -> args[0].equals(mine.authority())) ? 1 : 0)),
                "PROBE_STATUS", args -> {
                    final Obj report = this.network().status(noobj());
                    final Obj first = report.stream().findFirst().orElse(noobj());
                    final Obj peerStatus = first.isRec() ? (Obj) first.asRec().at(uri(STATUS)) : noobj();
                    this.probe("status", peerStatus.isRec() ? (Obj) peerStatus.asRec().at(uri(KIND)) : noobj());
                }));
        all.put("PROBE_PEERCOUNT", args -> {
            LOG.debug("probe peercount machine=%s net=%s count=%s", System.identityHashCode(Machine.current()), System.identityHashCode(this.network()), this.network().peers().elements().count());
            this.probe("peercount", jnt(this.network().peers().elements().count()));
        });
        all.put("DISCONNECT", args -> this.network().disconnect(f(args[0])));
        // the java face of connect: the mtron inst resolves in NetworkWireScriptTest's boot but not in
        // this suite's (suite-dependent inst resolution -- an open item pinned there, not here), and this
        // test's subject is reach-vs-knowledge across frames, so the dial goes through the proven path.
        all.put("CONNECT", args -> this.network().connect(f(args[0])));
        return all;
    }

    @AfterEach
    public void clearNetwork() {
        this.closeCluster();
        // the roster is an ADDRESS, not a Java field, so a test that wrote into it clears it by address too
        Machine.write(ROSTER_PATH, rec(mutableMap()));
    }

    /**
     * the roster's home as an address — read through the accessor so a re-point cannot leave this test behind
     */
    private static final fURI ROSTER_PATH = Network.ROOT_ROSTER;

    /**
     * A REC IN SPACE IS ITS PARTS. The roster is an address rather than a field on the component, so a
     * discovery handler declares a peer by writing {@code <roster>/<name>} and the roster reads back as the
     * map — the same "a read recurses into what was written underneath and rebuilds the tree" that a
     * distributed row depends on. Both spellings must land in ONE map, or a handler writing entry-by-entry
     * would be invisible to a reader that got the whole rec.
     * <p>
     * This is pinned rather than assumed because the competing behaviour is SILENT: a writer that no-ops
     * (the machine claims part of /sys and its default writer drops writes) leaves a roster that reads
     * empty forever, with nothing to see in a log.
     */
    @Test
    void testTheRosterIsAnAddressAndItsPartsAreItsFields() {
        Machine.write(ROSTER_PATH, rec(uri("whole"), uri("ws://192.168.1.42:8555")));
        Machine.write(ROSTER_PATH.extend("byfield"), uri("ws://192.168.1.43:8555"));
        final Obj roster = Machine.read(ROSTER_PATH);
        LOG.debug("roster at %s is %s", ROSTER_PATH, roster);
        assertTrue(roster.isRec(), "reading the roster must reconstruct the rec, got " + roster);
        assertEquals(uri("ws://192.168.1.42:8555"), roster.asRec().at(uri("whole")),
                "the entry written as a whole rec is a field of the roster");
        assertEquals(uri("ws://192.168.1.43:8555"), roster.asRec().at(uri("byfield")),
                "and the entry written by address lands in the SAME map");
    }

    private void closeCluster() {
        if (null != this.cluster) {
            this.cluster.close();
            this.cluster = null;
        }
        Machine.write(Network.ROOT_ROSTER, rec(mutableMap()));
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
     * The roster is a rec mtron writes and reads, and the cluster is exactly the known servers it names.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "[ROSTER_CLEAR]",
            "[PROBE_ROSTER]",
            "*/m/networkProbe/roster % 0               [-- nothing declared: no known servers --]",
            "[CLUSTER 2 21001]                             [-- two server addresses, declared by the cluster --]",
            "[PROBE_ROSTER]",
            "*/m/networkProbe/roster % 2              [-- the roster's keys ARE the declared servers --]",
            "[CLUSTER_CLOSE]                               [-- and un-declaring removes them --]",
            "[PROBE_ROSTER]",
            "*/m/networkProbe/roster % 0"
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
            "[PROBE_ROSTER]",
            "*/m/networkProbe/roster % 0               [-- addressing it did not declare it --]",
            "[PROBE_PEER ws://never-declared:19999/x]",
            "*/m/networkProbe/peer % 0",
            "[PROBE_OWN ws://never-declared:19999/x]",
            "*/m/networkProbe/own % 0                   [-- nor is it mine --]"
    })
    void testMembershipIsNeverEmergent() {
    }

    /**
     * {@code status} asks every DECLARED peer and reports one entry each — a peer that is declared but silent is
     * reported {@code down}, never dropped from the report. Silence and absence are different facts, and the report
     * is worth nothing if it hides the failures.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "[DECLARE_SILENT ws://silent-host:21005]       [-- declared, and it can never answer --]",
            "[PROBE_ROSTER]",
            "*/m/networkProbe/roster % 1               [-- declared, so it is known --]",
            "[PROBE_STATUS]",
            "*/m/networkProbe/status % \"down\"           [-- DOWN, not absent --]"
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
            "*/m/networkProbe/mine % 1                  [-- a mounted host IS an authority I answer to --]",
            "[PROBE_OWN ws://localhost:19191/x]",
            "*/m/networkProbe/own % 1                   [-- localhost and 0.0.0.0 are one service --]",
            "[PROBE_OWN ws://localhost:19192/x]",
            "*/m/networkProbe/own % 0                   [-- a different port is a different service --]",
            "[CLUSTER 1 21007]",
            "[PROBE_PEER ws://localhost:21007/x]",
            "*/m/networkProbe/peer % 1                  [-- declared, so a peer --]",
            "[PROBE_OWN ws://localhost:21007/x]",
            "*/m/networkProbe/own % 0                   [-- and NOT mine: the two never both answer yes --]"
    })
    void testMineIsDerivedAndDisjointFromPeers() {
    }

    /**
     * A COMPONENT PREDICATE CONSTRAINS WHAT IS PRESENT AND NOTHING MORE, so every partial shape passes.
     * This is worth rows of its own because the failure it guards is not a wrong answer but a machine that
     * cannot be constructed at all: with `name` and `status` declared without {@code .maybe()}, the empty
     * rec a deferred component slot holds was rejected, and MachPushTest lost 8 of its 9 rows — every one
     * of them reporting {@code obj does not match /m/mach/component/network::T}. A row here names the
     * shape that broke, in one place, instead of surfacing eight tests away.
     */
    @ParameterizedTest
    @CsvSource(value = {
            // shape                                                                        % accepted % why
            "[=>]                                                                            % true  % the empty rec a deferred slot holds",
            "[name => 'local']                                                               % true  % a name alone",
            "[roster => [b => <ws://192.168.1.42:8555>]]                                     % true  % known endpoints alone",
            "[source => [mdns => </m/web/mdns>]]                                             % true  % a discovery source alone",
            "[source => [mdns-lan => </m/web/mdns>, mdns-bridge => </m/web/mdns>]]            % true  % ONE MECHANISM, TWO SEGMENTS: the key is a free label, so a source per LAN segment (different interfaces, different trust) sits side by side",
            "[peer => [<ws://192.168.1.42:8555> => [authority => <ws://192.168.1.42:8555>]]] % true  % a connection alone",
            "[name => 'local', roster => [b => <ws://192.168.1.42:8555>], source => [mdns => </m/web/mdns>]] % true % name, roster and source together",
            "[name => 5]                                                                     % false % a name that is not a str",
            "[source => [mdns => 5]]                                                         % false % a source names a handler tid, not a value",
    }, delimiter = '%')
    void testTheNetworkPredicateConstrainsOnlyWhatIsPresent(final String shape, final boolean accepted,
                                                            final String why) {
        final Obj network = Machine.read(machInstSet.MACH_NETWORK_TID);
        assertTrue(network.isType(), "the network type must be reachable from its instruction set, got " + network);
        final Obj candidate = ObjmtronSerializer.parse(shape);
        LOG.debug("testing %s against network::T [expected:%s]", candidate, accepted);
        assertEquals(accepted, candidate.test(network), why);
    }

    /**
     * ONE TYPE, TWO HONEST STATES: the authority is the whole requirement and it is sufficient. A peer
     * whose host, handler and transport are all absent is a reference that crossed a wire — known here
     * and not reachable from here — not a broken peer, which is why none of them is required.
     */
    @ParameterizedTest
    @CsvSource(value = {
            // peer                                                                                                                                    % accepted % why
            "[authority => <ws://192.168.1.42:8555>]                                                                                                  % true  % known: the authority is the connection key",
            "[authority => <ws://192.168.1.42:8555>, host => <ws://192.168.1.42:8555/mtron>, handler => </m/web/ws/mtron_ws>, name => 'builder-2']    % true  % reachable: host and surface filled in",
            "[name => 'builder-2']                                                                                                                    % false % a peer with no authority is not a peer",
    }, delimiter = '%')
    void testThePeerPredicateIsAnAuthorityAndNothingMore(final String shape, final boolean accepted,
                                                         final String why) {
        final Obj peer = Machine.read(machInstSet.PEER_TID);
        assertTrue(peer.isType(), "the peer type must be reachable from its instruction set, got " + peer);
        final Obj candidate = ObjmtronSerializer.parse(shape);
        LOG.debug("testing %s against peer::T [expected:%s]", candidate, accepted);
        assertEquals(accepted, candidate.test(peer), why);
    }

    /**
     * REACH AND KNOWLEDGE ACROSS THE FRAME STACK — the network\u0027s two laws, asserted where they live.
     * REACH IS NEVER SHARED: push mints the child its own {@code NetworkUnion} level (the same parade as
     * MemoryUnion and the child instset), peers are neither inherited from the parent (not even shadowed)
     * nor shared with children, a pop drops exactly the popped machine\u0027s dials, and a sibling never sees
     * them. KNOWLEDGE IS SHARED: the roster is ONE listing at the root address — like Memory\u0027s base
     * frame, but machine-global — so a declaration made standing anywhere is every frame\u0027s fact.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "[ROSTER_CLEAR]",
            "[CONNECT loop://root-self]",
            "[PROBE_PEERCOUNT]",
            "*/m/networkProbe/peercount % 1                  [-- root\u0027s own reach: one identity peer --]",
            "[STATE] *~.push(machine_b)",
            "*~/network.?/m/mach/component/network::T.count() % 1      [-- the child has a network LEVEL of its own --]",
            "[PROBE_PEERCOUNT]",
            "*/m/networkProbe/peercount % 0                  [-- REACH IS NOT INHERITED: the parent\u0027s dials are not the child\u0027s --]",
            "[CONNECT loop://machine_b]",
            "[PROBE_PEERCOUNT]",
            "*/m/networkProbe/peercount % 1                  [-- the child\u0027s dial lands on the child\u0027s level --]",
            "[DECLARE_SILENT ws://declared-inside-b:1111]",
            "[PROBE_ROSTER]",
            "*/m/networkProbe/roster % 1                     [-- knowledge declared while standing in machine_b --]",
            "[STATE] *~.pop(_)",
            "[PROBE_PEERCOUNT]",
            "*/m/networkProbe/peercount % 1                  [-- pop dropped exactly machine_b\u0027s dials; the root\u0027s stand --]",
            "[STATE] *~.push(machine_a)",
            "[PROBE_PEERCOUNT]",
            "*/m/networkProbe/peercount % 0                  [-- a sibling never sees another machine\u0027s reach --]",
            "[PROBE_ROSTER]",
            "*/m/networkProbe/roster % 1                     [-- but knowledge IS shared: one roster, whoever declared it --]",
            "[STATE] *~.pop(_)",
            "[DISCONNECT loop://root-self]",
            "[PROBE_PEERCOUNT]",
            "*/m/networkProbe/peercount % 0",
            "[ROSTER_CLEAR]"
    })
    void testReachIsFrameLocalAndKnowledgeIsShared() {
    }
}
