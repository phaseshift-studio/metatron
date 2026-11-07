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

package studio.phaseshift.metatron.algebra;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.Obj;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.LAW;
import static studio.phaseshift.metatron.Tokens.OBJ;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * THE PENDING-WRITE LOG'S CONTRACT — what a builder may stage before a machine or a space exists, what a reader is
 * promised while those writes wait, and what a drain hands over to {@code Machine.write}.
 *
 * <p>The properties under test are the ones a mount and the boot's sweep depend on: one write per address with the
 * last value winning and the first arrival keeping its position, nulls staging nothing, the drain jurisdiction being
 * the address tree rather than its text, the drained write actually landing where a read can answer it, and — because
 * a declaration is not a one-shot — the log being RETAINED, so a re-mount re-publishes the same set instead of finding
 * an empty queue. The log is process-wide, so every test starts and ends by emptying it, and the writes are aimed at a
 * scratch space this class alone mounts — nothing here leans on, or leaves anything behind in, the rest of the VM's
 * address tree.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class PendingWritesTest extends AbstractMetatronTest {

    /**
     * The address the scratch space is indexed at. It is a vid rather than a data path: it names the space itself, and
     * nothing in the tree mounts under it.
     */
    private static final fURI SCRATCH_VID = f("/sys/space/test/pendingwrites");

    /**
     * The address root the drains publish under — a real, writable jurisdiction, so a drained write can be read back
     * instead of merely leaving the queue.
     */
    private static final fURI SCRATCH_PATTERN = f("/pendingwrites/#");

    /**
     * Mount the space the drains write into, then empty the queue: the queue is process-wide, so a test that ran
     * earlier in the same fork must not leave an address behind for this one to count.
     * <p>
     * THE SCRATCH SPACE CARRIES A {@code ?decq} Q-PROC, because a DECLARATION is written at {@code …?decq} and a space
     * that has no such q refuses the write — the property the residue sweep is exercised on, and the same q-proc every
     * instruction set's space registers.
     */
    @BeforeEach
    void mountScratchSpaceAndClearTheQueue() {
        PendingWrites.clear();
        memSpace.of(SCRATCH_PATTERN, SCRATCH_VID).addQ(QCollection.decQ());
    }

    /**
     * Empty the queue and unmount the scratch space, in that order in spirit: a queued address outliving the space
     * would be published into nothing by a later drain, and a space outliving its test would answer reads it does not
     * own.
     */
    @AfterEach
    void unmountScratchSpaceAndClearTheQueue() {
        PendingWrites.clear();
        Machine.current().memory().removeSpace(SCRATCH_VID);
    }

    /**
     * ONE WRITE PER ADDRESS, LAST VALUE WINS, FIRST ARRIVAL KEEPS ITS POSITION. A rewrite replaces the value at the
     * address it names and does not append a second entry, so the replay order is the order addresses were first seen
     * rather than the order they were last touched.
     */
    @Test
    void aRewriteKeepsItsPositionAndCarriesTheLastValue() {
        final fURI ledger = f("/pendingwrites/ledger");
        final fURI label = f("/pendingwrites/label");
        final Obj first = rec(uri("count"), jnt(1));
        final Obj rewritten = rec(uri("count"), jnt(9));
        final Obj tag = rec(uri("name"), str("apron"));

        final PendingWrites.Write staged = PendingWrites.write(ledger, first);
        assertNotNull(staged, "a write with both parts is staged");
        assertEquals(ledger, staged.address(), "the staged write names the address it was given");
        assertEquals(first, staged.value(), "the staged write carries the value it was given");
        assertTrue(PendingWrites.has(ledger), "the address is pending the moment it is staged");
        assertEquals(1, PendingWrites.size(), "one address is pending");

        PendingWrites.write(label, tag);
        PendingWrites.write(ledger, rewritten);

        assertEquals(2, PendingWrites.size(), "a rewrite replaces the value at its address instead of adding one");
        assertEquals(List.of(new PendingWrites.Write(ledger, rewritten), new PendingWrites.Write(label, tag)),
                PendingWrites.writes(),
                "the rewritten address keeps its first position and carries the value of the last write");
        assertThrows(UnsupportedOperationException.class,
                () -> PendingWrites.writes().add(new PendingWrites.Write(f("/pendingwrites/other"), tag)),
                "the snapshot a reader holds is read-only");
    }

    /**
     * NULL IS NOT A WRITE: a null address, a null value or both stage nothing and answer null rather than throwing.
     * This is the path every instruction set's static initialization runs through, where a missing address is not
     * worth failing a boot over.
     */
    @Test
    void nullsStageNothing() {
        assertNull(PendingWrites.write(null, rec(uri("count"), jnt(1))), "a null address stages no write");
        assertNull(PendingWrites.write(f("/pendingwrites/ledger"), null), "a null value stages no write");
        assertNull(PendingWrites.write(null, null), "two nulls stage no write");

        assertEquals(0, PendingWrites.size(), "nulls leave the queue empty");
        assertTrue(PendingWrites.writes().isEmpty(), "nulls leave no ordered entry either");
        assertFalse(PendingWrites.has(f("/pendingwrites/ledger")), "nothing waits at the address a null write named");
    }

    /**
     * THE STORE-OF-RECORD SPELLINGS: a declaration is written at the object address plus {@code ?decq} and
     * documentation at the same address plus {@code ?docq}. The q is ADDED to the address as spelled, so a signature
     * the address already carries survives, and stripping the q gives the object address back — the round trip a later
     * boot relies on to find the declaration beside the thing it describes.
     */
    @Test
    void theDeclarationAndDocAddressesAreTheStoreOfRecordSpellings() {
        final fURI neg = f("/m/inst/neg");
        assertEquals(f("/m/inst/neg?decq"), PendingWrites.decq(neg), "the declaration address is the object address plus ?decq");
        assertEquals(f("/m/inst/neg?docq"), PendingWrites.docq(neg), "the doc address is the object address plus ?docq");

        final fURI signature = f("/m/inst/neg?rng=/m/int");
        assertEquals(f("/m/inst/neg?rng=/m/int&decq"), PendingWrites.decq(signature),
                "the law q is added after the signature the address already carries");
        assertTrue(PendingWrites.decq(signature).hasQ(QCollection.DECQ),
                "the q name is the store's own constant, not a second spelling of it");
        assertTrue(PendingWrites.docq(signature).hasQ(QCollection.DOCQ),
                "the doc q name is the store's own constant too");
        assertEquals(signature, PendingWrites.decq(signature).removeQ(QCollection.DECQ),
                "removing the q answers the object address the declaration is about");
        assertEquals(signature, PendingWrites.docq(signature).removeQ(QCollection.DOCQ),
                "removing the doc q answers the object address as well");
        assertNull(PendingWrites.decq(null), "a null address has no declaration address");
        assertNull(PendingWrites.docq(null), "a null address has no doc address");
    }

    /**
     * A STAGED VALUE IS READABLE AT ITS ADDRESS BEFORE ANY DRAIN — a plain hash read of the log, answered before a
     * declaration a builder queued has a space of its own. The answer is the LAST value staged at that address, and
     * null where nothing is staged, so a reader can tell "not declared" from "declared empty".
     */
    @Test
    void findAnswersTheValueStagedAtAnAddress() {
        final fURI ledger = f("/pendingwrites/ledger");
        final Obj first = rec(uri("count"), jnt(1));
        final Obj rewritten = rec(uri("count"), jnt(2));

        assertNull(PendingWrites.find(ledger), "nothing is staged at an address nothing was written to");
        assertNull(PendingWrites.find(null), "a null address answers nothing");
        PendingWrites.write(ledger, first);
        assertEquals(first, PendingWrites.find(ledger), "the staged value is answerable the moment it is written");
        PendingWrites.write(ledger, rewritten);
        assertEquals(rewritten, PendingWrites.find(ledger), "a rewrite is what a reader sees, exactly as writes() reports");
        assertNull(PendingWrites.find(f("/pendingwrites/other")), "a neighbour address is not answered");
    }

    /**
     * A PREFIX IS A JURISDICTION, NOT A TEXT FRAGMENT: draining {@code /pendingwrites} publishes the write at the
     * prefix and the writes under it, leaves {@code /pendingwrites2} alone even though its text starts the same way,
     * and the published values are readable at their addresses because the drain went through {@code Machine.write}.
     * NOTHING LEAVES THE LOG: the same address set is answered again by a second drain.
     */
    @Test
    void drainUnderPublishesOnlyItsJurisdiction() {
        final fURI root = f("/pendingwrites");
        final fURI deep = f("/pendingwrites/ledger/total");
        final fURI label = f("/pendingwrites/label");
        final fURI sibling = f("/pendingwrites2/ledger");
        final Obj count = rec(uri("count"), jnt(11));
        final Obj tag = rec(uri("name"), str("ledger"));
        final Obj outside = rec(uri("count"), jnt(12));

        PendingWrites.write(root, count);       // AT the prefix
        PendingWrites.write(deep, count);       // two segments under it
        PendingWrites.write(label, tag);        // one segment under it
        PendingWrites.write(sibling, outside);  // a sibling: starts with "/pendingwrites" but not "/pendingwrites/"

        assertEquals(4, PendingWrites.size(), "four distinct addresses are staged");
        assertEquals(3, PendingWrites.drainUnder(root),
                "the write at the prefix and the two under it are published, the sibling is not");
        assertEquals(4, PendingWrites.size(), "the log is retained, so every address is still staged");
        assertTrue(PendingWrites.has(sibling), "the sibling keeps its write");
        assertTrue(PendingWrites.has(deep), "a published address stays in the log for the next mount to replay");
        assertTrue(PendingWrites.has(label), "every published address stays in the log");
        // a read of a node answers its subtree, so each published value is read at a LEAF that was written whole
        assertEquals(count.vid(null), Machine.read(deep).vid(null),
                "the drained write is published through Machine.write, so the space answers it now");
        assertEquals(tag.vid(null), Machine.read(label).vid(null),
                "every drained write is published, not only the first in arrival order");
        assertEquals(0, PendingWrites.drainUnder(root),
                "a second drain publishes nothing: the space already answers every one of them, and a live "
                        + "registration must never be replaced by the staged copy");
        assertEquals(4, PendingWrites.size(), "and still removes nothing");
    }

    /**
     * THE BOOT'S SWEEP PUBLISHES THE DECLARATION RESIDUE — the {@code ?decq}/{@code ?docq} writes no mount's
     * jurisdiction reached — and answers how much it published. It never publishes an INSTRUCTION or TYPE write: a
     * declaration write is a value replace and replaying it is a no-op, while a second registration of an object is
     * not (see {@link PendingWrites}'s class javadoc), so the sweep stays on the facet it can replay. A second sweep
     * publishes the same declarations again, with the same values, and the log keeps every address.
     */
    @Test
    void drainRestPublishesTheDeclarationResidue() {
        final fURI plain = f("/pendingwrites/plain");
        final Obj third = rec(uri("count"), jnt(3));
        final Obj declaration = rec(mutableMap(uri(OBJ), str("one"), uri(LAW), lst(uri("pure"))), QCollection.DECS_TID, null);
        PendingWrites.write(f("/pendingwrites/one?decq"), declaration);
        PendingWrites.write(f("/elsewhere/two?docq"), rec(uri("count"), jnt(2)));
        PendingWrites.write(f("/pendingwrites/three?docq"), third);
        PendingWrites.write(plain, third);              // an OBJECT write: the sweep must leave it alone

        // A FACET IS NOT SWEPT: ?docq/?decq live in the q-proc's own store and are published by the first READER
        // that misses them, not by the boot sweep (which would repay every declaration on every mount). The sweep
        // publishes the OBJECT residue alone.
        assertEquals(1, PendingWrites.drainRest(), "the sweep publishes the object residue, whatever its prefix");
        assertEquals(declaration, Machine.read(f("/pendingwrites/one?decq")),
                "a declaration is staged, not swept: the first READER publishes it, and the read answers it");
        assertEquals(4, PendingWrites.size(), "the log is retained, so no address left the stage");
        assertEquals(4, PendingWrites.writes().size(), "the ordered snapshot agrees with the count");
        assertEquals(third, Machine.read(plain),
                "the OBJECT residue is what the sweep publishes: no mount claimed this address");
        assertEquals(0, PendingWrites.drainRest(),
                "a published object is ANSWERED by its space, so a second sweep finds no residue");
        assertEquals(4, PendingWrites.size(), "and still removes nothing");
        PendingWrites.clear();
        assertEquals(0, PendingWrites.drainRest(), "a sweep over an empty log is a no-op answering 0");
    }
}
