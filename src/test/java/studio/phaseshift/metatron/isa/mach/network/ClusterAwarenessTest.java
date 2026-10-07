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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Network;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.Network;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * Cluster awareness: the {@code /sys/cluster} singleton and its {@code status} method.
 * <p>
 * This is the first thing that makes a cluster <em>observable</em> rather than merely configured — the roster
 * says who was declared, and {@code status()} says who is answering. The distinction is the whole point: a
 * declared-but-silent peer must appear in the report as {@code down}, not vanish from it.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ClusterAwarenessTest extends AbstractMetatronTest {

    private static PeerCluster cluster;

    @BeforeAll
    public static void setup() throws Exception {
        cluster = PeerCluster.of(1)
                .store(PeerCluster.DEFAULT_ROOT)
                .seed(PeerCluster.DEFAULT_SEED_MATRIX)
                .start()
                .connect();
    }

    @AfterAll
    public static void teardown() {
        if (null != cluster) {
            cluster.close();
            cluster = null;
        }
    }

    /**
     * the singleton exists where every /sys registry lives, with both fields
     */
    @Test
    public void testClusterSingletonIsRegistered() {
        final Obj cluster = Machine.read(Network.CLUSTER_PATH);
        assertTrue(cluster.isRec(), "/sys/cluster should be a rec, got " + cluster);
        assertTrue(cluster.asRec().has(uri(PEER)), "a cluster has a peer field");
        assertTrue(cluster.asRec().has(uri(STATUS)), "a cluster has a status method");
    }

    /**
     * the peer field is a live pointer at the declared roster, not a copy — so declaring a peer after boot is
     * visible without touching the cluster
     */
    @Test
    public void testPeerFieldIsLiveNotACopy() {
        final Obj peerField = Machine.read(Network.CLUSTER_PATH).asRec().at(uri(PEER));
        assertTrue(peerField.isRec(), "the peer field should resolve to the roster rec, got " + peerField);
        assertEquals(Machine.read(Network.Helper.peerRosterPath()).toString(), peerField.toString(),
                "the peer field must be the roster itself, not a snapshot of it");
    }

    /**
     * Both field-as-method spellings, and the reason the first attempt at this failed.
     * <p>
     * The inst must be built as {@code auto_(instLambda(f))}. {@code auto_()} does not take a lambda — it wraps
     * an inst as <b>arg 0</b> of an {@code AUTO_INST_TID} inst ({@code instB}), which is exactly the structure
     * {@code !inst?(){...}} has. Building the AUTO inst directly ({@code instC(AUTO_INST_TID, lst(), lambda)})
     * still dereferences, because the auto inst evaluates and calls its own {@code f} — but the fluent spelling
     * has no inner inst to resolve and reports {@code unable to locate inst-f rec::T => status()}. The tid being
     * AUTO is necessary but not sufficient; the wrapping is what makes it a method.
     */
    @Test
    public void testStatusIsInvokableBothWays() {
        for (final String code : new String[]{
                "*/sys/cluster/status",
                "*/sys/cluster.status()"}) {
            final Obj report = ObjmtronSerializer.parse(code).apply();
            assertFalse(report.isFail(), code + " should not fail, got " + report);
            assertFalse(report.isNoObj(), code + " should produce a report, got noobj");
            assertEquals(1, report.stream().count(), code + " should report the one declared peer: " + report);
        }
    }

    /**
     * The status method is retrievable and, applied to the cluster rec, produces one entry per declared peer.
     * <p>
     * Note the receiver: an explicit deref plus an explicit receiver, which is the spelling that works when the
     * rec is in hand. See {@link #testStatusIsInvokableByTheAutoFieldSpellings} for the path and fluent forms.
     */
    @Test
    public void testStatusIsReachableAndReportsPerPeer() {
        final Obj clusterRec = Machine.read(Network.CLUSTER_PATH);
        final Obj status = Machine.read(Network.CLUSTER_PATH.extend("status"));
        assertFalse(status.isNoObj(), "the status inst must be retrievable at /sys/cluster/status");
        final Obj report = status.apply(clusterRec);
        assertFalse(report.isFail(), "applying status to the cluster must not fail: " + report);
        assertFalse(report.isNoObj(), "one declared peer should yield one entry, got noobj");
        final Obj one = report.stream().findFirst().orElse(null);
        assertNotNull(one, "one declared peer should yield one entry, got " + report);
        assertEquals("up", one.asRec().at(uri(STATUS)).asRec().at(uri(KIND)).strValue(),
                "the live peer should report up: " + one);
    }

    /**
     * A peer that is declared but silent reports <b>down</b> rather than dropping out of the report. This is the
     * assertion that keeps the roster and the health of the roster from being confused for one another.
     */
    @Test
    public void testStatusReportsASilentPeerDown() {
        final Obj roster = Machine.read(Network.Helper.peerRosterPath());
        final Map<Obj, Obj> declared = new LinkedHashMap<>(roster.asRec().jvm());
        // a declared peer whose transport cannot answer: the report must name it, and must not call it up
        declared.put(uri("ws://localhost:1"), noAnswerTransport());
        Machine.write(Network.Helper.peerRosterPath(), rec(declared));

        final Obj report = Machine.read(Network.CLUSTER_PATH.extend("status"))
                .apply(Machine.read(Network.CLUSTER_PATH));
        assertEquals(2, report.stream().count(), "both the live and the silent peer belong in the report: " + report);
        assertTrue(report.stream().anyMatch(entry ->
                        "down".equals(entry.asRec().at(uri(STATUS)).asRec().at(uri(KIND)).strValue())),
                "the silent peer must be reported down, not omitted: " + report);
    }

    /**
     * a transport that answers nothing at all — the spelling a dead peer produces
     */
    private static Obj noAnswerTransport() {
        return studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda((lhs, inst) ->
                studio.phaseshift.metatron.isa.m.type.NoObj.noobj());
    }

}
