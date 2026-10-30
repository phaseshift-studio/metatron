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
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.Network;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.CLIENT;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.EVAL_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * THE WRITE-ROUTING RULE FOR A COMPONENT METHOD: a method on a NETWORK mutates THAT network's level — not wherever the
 * calling thread happens to stand. The rule needs its own pins because the gatekeeper ({@code Machine.write}) resolves
 * an address against {@code Machine.current()}, which is the shape that let {@code registerPrefix} silently file a
 * frame's name in the parent until it was routed through the network's own space.
 * <p>
 * AUDIT (2026-10-29), the methods that take a network in hand:
 * <table border="1">
 *   <caption>where each method's write lands</caption>
 *   <tr><th>method</th><th>write path</th><th>routing</th></tr>
 *   <tr>
 *     <td>{@code registerPrefix} / {@code unregisterPrefix}</td>
 *     <td>{@code this.write(Network.Helper.prefixTable(this), …)}</td>
 *     <td>THIS level, thread-independent ({@link MoveSeamsTest} pins the shadowing)</td>
 *   </tr>
 *   <tr>
 *     <td>{@code connect} / {@code index} / {@code disconnect}</td>
 *     <td>{@code this.peers()} / {@code this.at(PEER, …)}</td>
 *     <td>THIS level, thread-independent ({@link #aDialAskedOnAFramesNetworkIsHeldByThatFrame},
 *     {@link #aRetractionAskedOnAFramesNetworkRetractsOnlyThatFrame})</td>
 *   </tr>
 *   <tr>
 *     <td>the client constructor's background index</td>
 *     <td>{@code Network.Helper.index} → {@code Machine.current().network()}</td>
 *     <td>POSITION-DEPENDENT BY DESIGN: constructed HERE, so indexed HERE — even when the dial was asked for a frame
 *     ({@link #aConstructedClientAlsoIndexesWhereItWasConstructed}). One client, two records, one connection.</td>
 *   </tr>
 * </table>
 * <p>
 * A caller that needs the write to be perspective-free must either call a method on the object in hand (as every row
 * above does) or bracket a gatekeeper call with
 * {@link Machine.Helper#evalAt(Machine, java.util.function.Supplier)} — the same door the test checks use.
 */
public class NetworkWriteRoutingTest extends AbstractMetatronTest {

    private static final fURI SELF = f("loop://self");

    private static Obj clientOf(final Obj peer) {
        return peer.isRec() ? peer.asRec().at(uri(CLIENT)) : noobj();
    }

    private static void hangUp(final Network network) {
        if (!network.peers().isEmpty())
            network.drop(SELF);
    }

    /**
     * A DIAL ASKED FOR A FRAME LANDS IN THAT FRAME, wherever the caller stands. {@code connect} reads and writes
     * {@code this.peers()}, so the level that answers is the one the method was called on — not {@code current()}.
     */
    @Test
    void aDialAskedOnAFramesNetworkIsHeldByThatFrame() {
        final Machine root = Machine.current();
        final Machine child = root.push(f("routedDial"));
        Machine.current(root);
        try {
            assertTrue(Network.Helper.connect(child.network(), SELF).isRec(), "the dial answers the member it made");
            assertTrue(child.network().overrideOf(SELF, EVAL_INST_TID).isObjInst(),
                    "the frame that asked holds a peer it can eval through");
        } finally {
            hangUp(child.network());
            hangUp(root.network());
            Machine.Helper.evalAt(f("/routedDial"), () -> Machine.current().pop());
            Machine.current(root);
        }
    }

    /**
     * THE ONE POSITION-DEPENDENT WRITE, deliberately: the client's CONSTRUCTOR indexes it as a background behaviour at
     * {@code Machine.current()}, so a dial asked for a frame is observed BOTH at the level that asked ({@code connect}'s
     * own index) and at the level it was constructed on. The two records carry the SAME client, so it is one
     * connection and two observations — worth pinning, because a future change to constructor indexing would otherwise
     * silently move a peer between levels.
     */
    @Test
    void aConstructedClientAlsoIndexesWhereItWasConstructed() {
        final Machine root = Machine.current();
        final Machine child = root.push(f("routedConstruct"));
        Machine.current(root);
        try {
            Network.Helper.connect(child.network(), SELF);
            final Obj atRoot = root.network().peer(SELF.pathEncoding());
            final Obj atChild = child.network().peer(SELF.pathEncoding());
            assertTrue(atRoot.isRec(), "the constructor observed the client where it was constructed: " + atRoot);
            assertTrue(atChild.isRec(), "... and connect filed the frame's own record: " + atChild);
            assertEquals(clientOf(atRoot), clientOf(atChild), "one client, two records");
        } finally {
            hangUp(child.network());
            hangUp(root.network());
            Machine.Helper.evalAt(f("/routedConstruct"), () -> Machine.current().pop());
            Machine.current(root);
        }
    }

    /**
     * A RETRACTION ASKED FOR A FRAME RETRACTS ONLY THAT FRAME'S ROW. The caller-level record made by the constructor is
     * a different level's member and is not the frame's to hang up — per-level membership, both directions.
     */
    @Test
    void aRetractionAskedOnAFramesNetworkRetractsOnlyThatFrame() {
        final Machine root = Machine.current();
        final Machine child = root.push(f("routedRetract"));
        Machine.current(root);
        try {
            Network.Helper.connect(child.network(), SELF);
            final Obj client = clientOf(child.network().peer(SELF.pathEncoding()));
            assertFalse(client.isNoObj(), "the frame holds the dial to retract");
            child.network().drop(SELF);
            assertTrue(child.network().peers().isEmpty(), "the frame's row is gone");
            // PER-LEVEL MEMBERSHIP, NOT PER-LEVEL LIVENESS: the caller-level record was made by the constructor and
            // still references the SAME client, so retracting a frame's row does not retract that record -- and for
            // a wire client, releasing the shared client would leave the other record holding a hung-up link.
            assertEquals(client, clientOf(root.network().peer(SELF.pathEncoding())),
                    "the caller-level record still holds the shared client");
        } finally {
            hangUp(child.network());
            hangUp(root.network());
            Machine.Helper.evalAt(f("/routedRetract"), () -> Machine.current().pop());
            Machine.current(root);
        }
    }

    /**
     * THE TABLE'S ONE UNSYNCHRONIZED WRITE. {@code registerPrefix} reads the level's rows, adds one, and writes the
     * whole table back — a read-merge-write with no lock around it, unlike {@code index} and {@code disconnect} which
     * both {@code synchronized (this)}. Two declarations racing therefore can each read the table before the other's
     * write and the later write drops the earlier row. That is data loss no single-threaded test can see, so it is
     * asked here directly.
     */
    @Test
    void concurrentPrefixRegistrationsKeepEveryRow() throws Exception {
        final Network network = Machine.current().network();
        final int rows = 16;
        final ExecutorService pool = Executors.newFixedThreadPool(rows);
        final CountDownLatch gate = new CountDownLatch(1);
        try {
            final List<Future<?>> declared = new ArrayList<>();
            for (int i = 0; i < rows; i++) {
                final int id = i;
                declared.add(pool.submit(() -> {
                    gate.await();
                    network.registerPrefix(f("cc" + id), f("loop://concurrent" + id));
                    return null;
                }));
            }
            gate.countDown();
            for (final Future<?> one : declared)
                one.get(60, TimeUnit.SECONDS);
            for (int i = 0; i < rows; i++) {
                final int id = i;
                assertTrue(network.prefixes().jvm().keySet().stream()
                                .filter(Obj::isUri)
                                .map(key -> key.uriValue().toString())
                                .anyMatch(("cc" + id)::equals),
                        "row cc" + id + " survived the concurrent declarations: " + network.prefixes());
            }
        } finally {
            pool.shutdownNow();
            for (int i = 0; i < rows; i++)
                network.unregisterPrefix(f("cc" + i));
        }
    }

    /**
     * The table above claims a prefix row is thread-independent; the shadowing file proves it end to end.
     */
    @Test
    void aPrefixRowIsThisLevelsWhateverTheCallerStandsIn() {
        final Machine root = Machine.current();
        final Machine child = root.push(f("routedPrefix"));
        Machine.current(root);
        try {
            child.network().registerPrefix(f("rt"), f("loop://routed"));
            assertEquals("loop://routed/rt", child.network().alignPrefix(f("//rt/rt")).toString());
            assertTrue(root.network().prefixes().jvm().keySet().stream()
                            .filter(Obj::isUri)
                            .map(key -> key.uriValue().toString())
                            .noneMatch("rt"::equals),
                    "no row leaked to the caller's level: " + root.network().prefixes());
        } finally {
            child.network().unregisterPrefix(f("rt"));
            Machine.Helper.evalAt(f("/routedPrefix"), () -> Machine.current().pop());
            Machine.current(root);
        }
    }
}
