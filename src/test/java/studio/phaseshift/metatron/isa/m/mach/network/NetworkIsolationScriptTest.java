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

package studio.phaseshift.metatron.isa.m.mach.network;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.StatefulCSVSource;
import studio.phaseshift.metatron.StatefulParametrizedTest;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.m.mach.machInstSet;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * NETWORK isolation as a stateful script — the network half of the machine-isolation pair
 * ({@code MemoryIsolationScriptTest} is the memory half), in the same script form so the two read side by side.
 * <p>
 * The script vocabulary itself — the directives ({@code [CLUSTER]}, {@code [DECLARE]}, {@code [DIAL_SILENT]}, the
 * {@code [PROBE_*]} family), the probe address and the per-script teardown — lives in {@link NetworkScriptTest}.
 * What remains here is what isolation CLAIMS.
 * <p>
 * The identity questions ("is this address mine / a declared peer") are NOT here: they do not route anything, so
 * they are asked directly in {@code NetworkIdentityTest} rather than scripted. These scripts drive the
 * KNOWN-SERVER ROSTER ({@code /network/roster}) — the list of machines the system knows about, one shared address
 * rather than per-machine state. The CONNECTIONS a machine holds are a different question and live on the
 * component ({@code network.peer}); that is what {@code NetworkWireScriptTest} pins.
 * <p>
 * The invariants proven here:
 * <ol>
 *     <li><b>declaration is explicit</b> — the roster is a rec mtron writes and reads, and the cluster is exactly
 *         the servers it names.</li>
 *     <li><b>never emergent</b> — addressing a stranger answers {@code noobj} and leaves the roster untouched: a
 *         uri cannot make itself known merely by being addressed.</li>
 *     <li><b>knowledge is one listing</b> — the roster is written and read through ONE root address, so a
 *         declaration made standing anywhere is every frame's fact. (A "cluster view" whose {@code peer} field
 *         pointed at it does not exist: the cluster was folded into the network, and the design doc's
 *         {@code cluster().status()} predates that — the roster address itself is what is live.)</li>
 *     <li><b>reach is frame-local</b> — a child's dials are its own (not inherited, not shadowed), a pop drops
 *         exactly the popped machine's, and a sibling never sees another machine's.</li>
 *     <li><b>a silent peer is reported, not omitted</b> — {@code status} yields one entry per DIALED peer, and
 *         reports one whose client is not live {@code down}; silence and absence are different facts, and an
 *         undialed roster address is the third: {@code known}.</li>
 * </ol>
 */
public class NetworkIsolationScriptTest extends NetworkScriptTest {

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

    /**
     * The roster is a rec mtron writes and reads, the cluster is exactly the known servers it names, and a
     * declared server is a peer this frame may reach — until it is un-declared.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "[ROSTER_CLEAR]",
            "[PROBE_MACHINES]",
            "*/m/networkProbe/machines % 0               [-- nothing declared: no known servers --]",
            "[CLUSTER 2 21001]                             [-- two server addresses, declared by the cluster --]",
            "[PROBE_MACHINES]",
            "*/m/networkProbe/machines % 2              [-- the machine table's rows ARE the declared servers --]",
            "[PROBE_PEER ws://localhost:21001/x]",
            "*/m/networkProbe/peer % 1                [-- declared, so a peer this frame may reach --]",
            "[CLUSTER_CLOSE]                               [-- and un-declaring removes them --]",
            "[PROBE_MACHINES]",
            "*/m/networkProbe/machines % 0",
            "[PROBE_PEER ws://localhost:21001/x]",
            "*/m/networkProbe/peer % 0                [-- no longer known, so no longer a peer --]"
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
            "[PROBE_MACHINES]",
            "*/m/networkProbe/machines % 0               [-- addressing it did not declare it --]",
            "[PROBE_PEER ws://never-declared:19999/x]",
            "*/m/networkProbe/peer % 0                  [-- nor is it a peer --]"
    })
    void testMembershipIsNeverEmergent() {
    }

    /**
     * {@code status} asks every DIALED peer's client and reports one entry each — a peer whose client is not live is
     * reported {@code down}, never dropped from the report. Silence and absence are different facts, and the report
     * is worth nothing if it hides the failures. A roster row is the third fact: an address, known and never asked.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "[DIAL_SILENT ws://silent-host:21005]          [-- dialed, and its client is never live --]",
            "[PROBE_PEERCOUNT]",
            "*/m/networkProbe/peercount % 1              [-- dialed, so it is a peer --]",
            "[PROBE_STATUS]",
            "*/m/networkProbe/status % \"down\"           [-- DOWN, not absent --]",
            "[DISCONNECT ws://silent-host:21005]",
            "[DECLARE ws://known-host:21006]              [-- an address: knowledge, nothing dialed --]",
            "[PROBE_MACHINES]",
            "*/m/networkProbe/machines % 1               [-- declared, so it is known --]",
            "[PROBE_STATUS]",
            "*/m/networkProbe/status % \"known\"          [-- KNOWN, never asked: not down --]"
    })
    void testStatusReportsASilentPeer() {
    }

    /**
     * A COMPONENT PREDICATE CONSTRAINS WHAT IS PRESENT AND NOTHING MORE, so every partial shape passes.
     * This is worth rows of its own because the failure it guards is not a wrong answer but a machine that
     * cannot be constructed at all: a declared field without {@code .maybe()} rejected the empty rec a
     * deferred component slot holds, and MachPushTest lost 8 of its 9 rows — every one of them reporting
     * {@code obj does not match /m/mach/component/network::T}. A row here names the shape that broke, in
     * one place, instead of surfacing eight tests away.
     */
    @ParameterizedTest
    @CsvSource(value = {
            // shape                                                                        % accepted % why
            "[=>]                                                                            % true  % the empty rec a deferred slot holds",
            "[pattern => </branch_child/network/#>]                                          % true  % the branch alone",
            "[roster => [b => <ws://192.168.1.42:8555>]]                                     % true  % known endpoints alone",
            "[peer => [<ws/192/168/1/42/8555> => [origin => <ws://192.168.1.42:8555>]]] % true  % a connection alone, keyed by the dial's path encoding",
            "[roster => 5]                                                                   % false % a roster names a rec of addresses, not a value",
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
            "[origin => <ws://192.168.1.42:8555>]                                                                                                  % true  % known: the authority is the connection key",
            "[origin => <ws://192.168.1.42:8555>, host => <ws://192.168.1.42:8555/mtron>, handler => </m/web/ws/mtron_ws>, name => 'builder-2']    % true  % reachable: host and surface filled in",
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
            "[DECLARE ws://declared-inside-b:1111]",
            "[PROBE_MACHINES]",
            "*/m/networkProbe/machines % 1                     [-- knowledge declared while standing in machine_b --]",
            "[STATE] *~.pop(_)",
            "[PROBE_PEERCOUNT]",
            "*/m/networkProbe/peercount % 1                  [-- pop dropped exactly machine_b\u0027s dials; the root\u0027s stand --]",
            "[STATE] *~.push(machine_a)",
            "[PROBE_PEERCOUNT]",
            "*/m/networkProbe/peercount % 0                  [-- a sibling never sees another machine\u0027s reach --]",
            "[PROBE_MACHINES]",
            "*/m/networkProbe/machines % 1                     [-- but knowledge IS shared: one roster, whoever declared it --]",
            "[STATE] *~.pop(_)",
            "[DISCONNECT loop://root-self]",
            "[PROBE_PEERCOUNT]",
            "*/m/networkProbe/peercount % 0",
            "[ROSTER_CLEAR]"
    })
    void testReachIsFrameLocalAndKnowledgeIsShared() {
    }
}
