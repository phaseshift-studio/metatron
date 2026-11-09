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

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.m.mach.type.Memory;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.type.Obj;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

/**
 * PENDING WRITES — the {@code (address, obj)} pairs a builder has decided to write but could not, because there was
 * nothing to write them to yet.
 *
 * <p>WHY A QUEUE AT ALL: creating a type or an instruction ultimately means WRITING {@code (address, obj)} through
 * {@link Machine#write(fURI, Obj)}, because the instruction set that owns the address is what indexes it — an
 * instruction lands in its set's inst table, a type in its type table, and a declaration in the q-proc region beside
 * the thing it describes. A builder, however, runs during its set's static initialization: the root machine does not
 * exist yet (so {@code Machine.loaded()} is false) and no space is mounted in the frame that owns the address. A write
 * made then has nowhere to land, and dropping it would lose the type or the instruction the whole boot is building.
 * So the write is remembered here, in arrival order, and REPLAYED once a machine and that address's owner exist.
 *
 * <p>THE ADDRESSED FORM IS THE STORE OF RECORD: a declaration is written at {@link #decq(fURI)} and an
 * instruction's documentation at {@link #docq(fURI)} — one object address plus the {@code ?decq} or {@code ?docq} q
 * that names the region beside it. That is why the queue holds addresses and values rather than a typed declaration
 * structure: the boot's job is to perform the same write the builder would have performed had the machine been up, so
 * a mount is indistinguishable from a builder running against a live space, and a later boot reads the declaration
 * back from the same place.
 *
 * <p>NOBODY ELSE PUBLISHES: this class never touches a space, never consults a q-proc and never logs. {@code
 * Machine.write} is the only publisher, and a drain is its only caller — the queue stages and replays, nothing more.
 * {@link #drainUnder(fURI)} is called by the owning instruction set when it mounts, so each set publishes every write
 * in its own jurisdiction, and {@link #drainRest()} is the boot's sweep for whatever no set claimed — an address whose
 * owner never mounts, or one staged after the last mount.
 *
 * <p>A DECLARATION IS NOT A ONE-SHOT, so THE LOG IS RETAINED: a drain PUBLISHES and does not remove. Re-mounting an
 * instruction set mounts a FRESH space ({@code InstSet.importInstSet}), and that space's {@code ?decq} region is
 * empty, so a declaration that lived only in a drained queue would be lost the moment the set was re-added. Keeping
 * the staged write is what lets every mount re-publish the same {@code (address, obj)} pairs: a DECLARATION write
 * ({@code ?decq}/{@code ?docq}) stores the same rec at the same address, so replaying it is a no-op. The log is
 * therefore the ONE staging structure — builders keep no second registry — while {@code ?decq}/{@code ?docq} remain
 * the store of record.
 *
 * <p>AN OBJECT WRITE IS NOT A DECLARATION, AND REPLAYING IT IS NOT FREE: re-publishing an INSTRUCTION or a TYPE at its
 * contract address re-registers it in the table that owns that address, and the second registration is not a no-op
 * (measured 2026-11-02: a boot-time re-publish of the base set's instruction writes left an argument wrapper binding
 * its own parameter references, so {@code 5.band(-10,10)} answered a fail where it must answer noobj). A MOUNT is
 * safe because it publishes into a FRESH space; a sweep is not, because it publishes into spaces that are already
 * populated. So the two drains differ: {@link #drainUnder(fURI)} replays its whole jurisdiction on every mount, which
 * is what a re-mounted set needs, while {@link #drainRest()} publishes the DECLARATIONS ({@code ?decq}/{@code ?docq})
 * only — the facet whose write is a value replace and therefore idempotent, and the one the jurisdiction rule can
 * miss.
 *
 * <p>NO MACHINE, NO DRAIN: both drains answer 0 while {@code Machine.loaded()} is false, and neither throws. They must
 * not throw, because a drain is reached from a mount that a nested boot or a test may perform before the root machine
 * is installed; and they must not drop the write, because a write dropped then is a type or an instruction that never
 * arrives, which turns a boot-ordering accident into a silent and unexplainable absence. The writes simply wait, and
 * the drain that runs once the machine is up publishes them.
 *
 * <p>STATIC STATE, NOT AN INSTANCE: there is one boot in progress in a process, hence one queue, and it is
 * process-wide exactly as the registry it feeds is. An instance would have to be threaded through builders that run
 * before anyone could receive it.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class PendingWrites {

    /**
     * THE WRITES, keyed by ADDRESS — one write per address, so the map is where "last one wins" lives. A staged write
     * carries its own address, which means a reader never has to pair a key with a separate value and the two can
     * never disagree.
     */
    private static final Map<fURI, Write> WRITES = new ConcurrentHashMap<>();

    /**
     * THE SAME WRITES INDEXED BY ADDRESS TEXT, for the one question a mount asks: which writes are under me? A
     * prefix query on this map is O(log n + k); scanning the whole retained log per mount was O(n) and made every
     * per-test instset setup pay for every write ever staged.
     */
    private static final NavigableMap<String, Write> INDEX = new ConcurrentSkipListMap<>();


    /**
     * THE ORDER the addresses arrived in — the position a rewrite keeps. A hash map has no order a caller may depend
     * on, so the order is recorded beside it, and every drain replays the log in it. Nothing leaves this queue: the log
     * is retained, so a re-mount re-publishes the same writes in the same order.
     */
    private static final Queue<fURI> ORDER = new ConcurrentLinkedQueue<>();

    /**
     * THE REVISION counter — bumped AFTER a write lands, never before, so a reader that sees a revision has also seen
     * everything that revision counts. It is how the cached ordered view learns that the log changed without the log
     * being copied on every read.
     */
    private static final AtomicLong REVISION = new AtomicLong();

    /**
     * The ordered READ-ONLY snapshot of the log, rebuilt only when the revision moves: every drain reads one list
     * rather than copying the log per read.
     */
    private static volatile List<Write> VIEW = List.of();

    private static volatile long VIEW_REVISION = -1;

    /**
     * A registry, never a value — the constructor exists only to say so.
     */
    private PendingWrites() {
    }

    /**
     * ONE PENDING WRITE: the address it will land at and the value it will land with. A record rather than a live
     * action, because the value must survive EXACTLY as the builder made it — it is already stamped with the vid, tid
     * and any q the declaration carries, and translating it on the way out would publish something other than what
     * was declared.
     */
    public record Write(fURI address, Obj value) {
    }

    /**
     * STAGE a write and answer it. The address is the object's WRITE address — for a declaration that is
     * {@link #decq(fURI)} or {@link #docq(fURI)}, not the bare object address — and the value is the obj the builder
     * would have handed to {@code Machine.write} had the machine been up.
     * <p>
     * ONE WRITE PER ADDRESS, LAST ONE WINS, FIRST ARRIVAL KEEPS ITS POSITION: rewriting an address replaces its value
     * and leaves its place untouched, so the replay order is the order addresses were first seen rather than the order
     * they were last touched. Re-staging is ordinary — a builder that runs twice makes the same claim twice, and a
     * type may be declared once by its own set and once by a set that refines it — and a queue that grew on every
     * re-run would replay one declaration an unbounded number of times.
     * <p>
     * NULL IS NOT A WRITE: a null address or a null value stages nothing and answers null rather than throwing. This is
     * the path every instruction set's static initialization runs through, where a missing address is a builder bug
     * worth finding but not worth failing a boot over, and a null value has nothing to publish at any address.
     *
     * @param address the address the write will land at
     * @param value the obj the write will land with
     * @return the staged write, or null when either argument was null
     */
    public static Write write(final fURI address, final Obj value) {
        if (null == address || null == value)
            return null;
        final Write write = new Write(address, value);
        // the FIRST arrival is the one that adds the position; every later write to this address only replaces the
        // value. put answers the replaced write, and it answers null exactly once per pending address
        if (null == WRITES.put(address, write))
            ORDER.add(address);
        INDEX.put(address.toString(), write);
        REVISION.incrementAndGet();
        return write;
    }

    /**
     * EVERY PENDING WRITE, in arrival order, as an UNMODIFIABLE snapshot — the log a drain replays and the residue a
     * boot-complete check inspects. It is a snapshot, not a live view: a write that lands while a caller walks it is
     * not in that walk (it belongs to the next drain), and nothing can be written through the list.
     *
     * @return the pending writes, in the order their addresses first arrived
     */
    public static List<Write> writes() {
        final long revision = REVISION.get();
        if (VIEW_REVISION == revision)
            return VIEW;
        final List<Write> view = new ArrayList<>(ORDER.size());
        for (final fURI address : ORDER) {
            final Write write = WRITES.get(address);
            if (null != write)
                view.add(write);
        }
        final List<Write> snapshot = List.copyOf(view);
        // publish the snapshot only if nothing landed while it was being built: a write that landed after the
        // revision was read must not be frozen out by a view that claims to be current
        if (REVISION.get() == revision) {
            VIEW = snapshot;
            VIEW_REVISION = revision;
        }
        return snapshot;
    }

    /**
     * WHETHER A WRITE IS PENDING AT THIS ADDRESS — the question a builder asks before staging a second claim it has
     * already made, and the question a mount asks of the addresses it is about to own.
     *
     * @param address the address to test
     * @return true when a write waits at that exact address
     */
    public static boolean has(final fURI address) {
        return null != address && WRITES.containsKey(address);
    }

    /**
     * THE WRITE STAGED AT AN EXACT ADDRESS, or null when nothing was staged there — for a declaration, the claim a
     * builder queued ({@link #decq(fURI)} of the instruction's operation address). It is a plain hash read of the log,
     * answered before the claim has a space of its own: once the claim is published on first read, {@code ?decq}
     * answers the same claim from a space.
     *
     * @param address the exact address to read
     * @return the value staged at that address, or null when nothing is staged there
     */
    public static Obj find(final fURI address) {
        if (null == address)
            return null;
        final Write write = WRITES.get(address);
        return null == write ? null : write.value();
    }

    /**
     * HOW MANY DISTINCT ADDRESSES ARE STAGED — not how many times a builder ran, so a re-staged claim does not inflate
     * the count. The log is retained, so this is the size of the whole staged set, not a residue: a drain publishes
     * the set without shortening it.
     *
     * @return the number of distinct addresses in the log
     */
    public static int size() {
        return WRITES.size();
    }

    /**
     * PUBLISH every write the prefix owns, answering how many were published, and KEEP them staged. A write belongs to
     * the prefix when it is AT the prefix or UNDER it — {@code address.equals(prefix)} or {@code
     * address.toString().startsWith(prefix + "/")} — which is the jurisdiction rule, so an instruction set that owns
     * {@code /m} never publishes a declaration staged at {@code /m2/...} merely because the text starts the same way.
     * <p>
     * A drain is a REPLAY, not a translation: for each admitted write it calls {@code Machine.write(address, value)},
     * in arrival order. Nothing is removed — see the class javadoc: a re-mounted set mounts a fresh space and must be
     * able to publish the same declarations again, and a write of the same value at the same address is idempotent, so
     * a second drain publishes the same set again rather than a residue. The count answered is the number of writes in
     * the jurisdiction, so it is identical on every drain — it is a report of what was published, not of what is left.
     * <p>
     * ZERO WHEN NO MACHINE IS UP: this is the call an instruction set makes from its mount, and a mount can happen
     * before the root machine exists. It answers 0 without throwing, so the writes wait for the drain that runs once
     * the machine is installed (see the class javadoc).
     *
     * @param prefix the address whose jurisdiction is being drained
     * @return the number of writes published, 0 when no machine is up or the prefix is null
     */

    /**
     * A FACET ALREADY ANSWERED IS ALREADY PUBLISHED. {@code ?docq}/{@code ?decq} live in the q-proc's own space,
     * which outlives a set's mount; a fresh mount (a test builds a new instset per method) therefore has nothing to
     * republish. A fresh BOOT has a fresh q-proc, answers noobj, and publishes normally -- so this dedupe needs no
     * boot identity to be correct.
     */
    /**
     * INSTALL THE HOOKS: a {@code ?docq}/{@code ?decq} is published by the first reader that misses it, and a
     * {@code ?decq} write evicts the algebra memos ({@link TheoryHelper#evict}, {@link Law.Helper#evict}). The
     * hooks are installed by the BOOT, never from a static initializer -- {@link PendingWrites} is first touched from
     * {@code mTypes}' class initialization, and forcing {@code QCollection} from there closes the documented
     * {@code mTypes -> mInstSet.AS_INST_TID} cycle.
     */
    public static void install() {
        QCollection.LAZY_PUBLISH = PendingWrites::publish;
        // the admitting hook: a declaration that states a law outside law::T, or an equation that cannot run both ways, is
        // refused before the store holds it
        QCollection.VALIDATE = Law.Helper::validate;
        // and the writer hook: a declaration that lands evicts what the algebra memos derived from the one it replaced
        QCollection.EVICT = (address, declaration) -> {
            TheoryHelper.evict(address);
            Law.Helper.evict(address);
        };
    }

    public static boolean publish(final fURI address) {
        if (null == address || !Machine.loaded())
            return false;
        // A DECLARATION PUBLISHES WITH ITS SIBLINGS: a read at ANY address of an operation (bare, subsumed, or a
        // contract's own) publishes every contract the log stages under the operation's base path. The store answers a
        // contract it holds no claim for by subsumption, so a sibling left staged would be pre-empted for good: once a
        // specialization is published, it answers its generic's own address, and the generic's claim never lands (a
        // since-removed int order did this to order?A{*}).
        if (address.hasQ(QCollection.DECQ)) {
            final fURI base = address.removeQ(QCollection.DECQ).basePath();
            boolean published = false;
            for (final Write staged : writes())
                if (staged.address().hasQ(QCollection.DECQ)
                        && staged.address().removeQ(QCollection.DECQ).basePath().equals(base)) {
                    Machine.write(staged.address(), staged.value());
                    published = true;
                }
            return published;
        }
        final Write write = WRITES.get(address);
        if (null != write)
            Machine.write(write.address(), write.value());
        return null != write;
    }

    private static boolean isFacet(final fURI address) {
        return address.hasQ(QCollection.DECQ) || address.hasQ(QCollection.DOCQ);
    }

    public static int drainUnder(final fURI prefix) {
        if (null == prefix)
            return 0;
        // a drain before boot must not throw: the writes wait for the machine
        if (!Machine.loaded())
            return 0;
        final String text = prefix.toString();
        int drained = 0;
        // the RANGE narrows the candidates in O(log n + k); `under` keeps the exact jurisdiction rule (the address
        // itself, or a CHILD segment -- a text prefix alone would let /m/mathx answer /m/math)
        for (final Write write : INDEX.subMap(text, true, text + '\uffff', false).values()) {
            // A FACET IS NOT PUBLISHED AT MOUNT. ?docq/?decq live in the q-proc's own store and are published
            // lazily by the reader that misses them (QCollection's preRead), so a fresh mount costs nothing for
            // the thousands of declarations a set carries.
            if (!under(write.address(), prefix) || isFacet(write.address()))
                continue;
            // A LIVE REGISTRATION WINS OVER A STAGED COPY. The log is process-wide, so the type/inst objects in it
            // belong to the boot that STAGED them; replaying one into a later space replaces that boot's own
            // registration with an object of a dead machine -- address-equal, identity-different -- and every type
            // test that closes over the address then disagrees. Publish only what the space does not answer.
            if (!Machine.read(write.address()).isNoObj())
                continue;
            Machine.write(write.address(), write.value());
            drained++;
        }
        return drained;
    }

    /**
     * PUBLISH every staged DECLARATION ({@code ?decq}/{@code ?docq}), answering how many were published — the boot's
     * sweep for the declaration residue: an address no instruction set claimed, because its owner never mounts or
     * because it lies outside every mounted prefix (a leaf-vid set's {@code …?decq} write is BESIDE its vid, not under
     * it, so no {@code drainUnder} reaches it).
     * <p>
     * WHY ONLY DECLARATIONS: a declaration write is a value replace in the owning space's {@code ?decq}/{@code ?docq}
     * region, so replaying it is a no-op and the same set may be swept again. An INSTRUCTION or TYPE write has no such
     * idempotence — a second registration in the table that owns the address changes behaviour (see the class javadoc)
     * — and publishing one into a space that is already populated is not something a sweep may do. A mount can,
     * because it publishes into a fresh space.
     *
     * @return the number of declaration writes published, 0 when no machine is up or the log stages no declaration
     */
    public static int drainRest() {
        if (!Machine.loaded())
            return 0;
        final var memory = Machine.current().memory();
        // THE RESIDUE IS WHAT NO MOUNTED SPACE CLAIMED. A set publishes its own jurisdiction at mount
        // (drainUnder), so re-publishing a facet whose address already belongs to a mounted space is the same
        // write again -- once per retained write, on every boot. That was the per-test molasses: thousands of
        // doc/declaration writes per boot, and a boot per test class.
        // ONLY AN INSTRUCTION SET'S MOUNT PUBLISHES ITS OWN JURISDICTION; a plain space (a scratch space, a
        // test's memSpace) never drains, so its facets are residue and must still be published here.
        // THE SWEEP IS THE OBJECT RESIDUE: a facet is published by its reader (lazy); an OBJECT is published
        // when the space does not answer it yet. That is what lets a declaration written AFTER a set mounted
        // reach that set's own write door -- which is where it is indexed into the set's `inst` list.
        return drain(write -> !isFacet(write.address())
                && memory.hasSpaceFor(write.address())
                && Machine.read(write.address()).isNoObj());
    }

    /**
     * RESET THE WHOLE STAGING PIPELINE — the log, its address index, the insertion order, the snapshot and the
     * revision. This is what a test (or a console {@code :reset}) calls to forget every staged write; the boot calls
     * it through {@code BootLoader.resetCaches()} so a fresh VM never inherits another boot's queue. Unlike
     * {@link #clear()} it also resets the revision and the snapshot, so a reader cannot see a stale view.
     */
    public static void reset() {
        WRITES.clear();
        INDEX.clear();
        ORDER.clear();
        VIEW = List.of();
        VIEW_REVISION = -1;
        REVISION.incrementAndGet();
    }

    /**
     * DROP EVERY STAGED WRITE — TESTS ONLY. The queue is process-wide, so a test stages into the same one the VM's
     * boot fills; this is how a test starts from an empty queue and how it leaves none of its own writes behind. It
     * publishes nothing and touches no space: {@code ?decq} and {@code ?docq} are the record, and they are not
     * touched.
     */
    public static void clear() {
        WRITES.clear();
        INDEX.clear();
        ORDER.clear();
        REVISION.incrementAndGet();
        VIEW = List.of();
        VIEW_REVISION = REVISION.get();
    }

    /**
     * THE DECLARATION WRITE ADDRESS FOR AN OBJECT ADDRESS — {@code address?decq}, the store-of-record spelling of "the
     * declaration this type or instruction makes". The queue stages the write at this address, and a later boot reads
     * the declaration back through the same q, so the address is the identity of the claim. A null address answers
     * null, matching {@link #write(fURI, Obj)}'s tolerance.
     *
     * @param address the type's or instruction's own address
     * @return the address the declaration is written at
     */
    public static fURI decq(final fURI address) {
        return null == address ? null : address.addQ(QCollection.DECQ);
    }

    /**
     * THE DOC WRITE ADDRESS FOR AN OBJECT ADDRESS — {@code address?docq}, the store-of-record spelling of "the
     * documentation beside this instruction or obj". It is the sibling of {@link #decq(fURI)}: the declaration is data
     * written beside the thing it describes, and this is the address that data is written at. A null address answers
     * null, matching {@link #write(fURI, Obj)}'s tolerance.
     *
     * @param address the instruction's or obj's own address
     * @return the address the documentation is written at
     */
    public static fURI docq(final fURI address) {
        return null == address ? null : address.addQ(QCollection.DOCQ);
    }

    /**
     * THE JURISDICTION RULE, in one place: a write belongs to a prefix when it is AT it or UNDER it. The trailing
     * separator is what makes {@code /m} and {@code /m2} different jurisdictions rather than one a text prefix of the
     * other.
     */
    private static boolean under(final fURI address, final fURI prefix) {
        return address.equals(prefix) || address.toString().startsWith(prefix + "/");
    }

    /**
     * THE DRAIN ITSELF, so the two public forms cannot drift: the predicate is the whole difference. One pass over a
     * snapshot, in arrival order, publishing each admitted write. Nothing is removed — the log is retained, which is
     * what lets a mount replay its jurisdiction into the fresh space it just built.
     */
    private static int drain(final Predicate<Write> jurisdiction) {
        // a drain before boot must not throw: the writes wait for the machine
        if (!Machine.loaded())
            return 0;
        int drained = 0;
        for (final Write write : writes()) {
            if (!jurisdiction.test(write) || isFacet(write.address()))
                continue;
            Machine.write(write.address(), write.value());
            drained++;
        }
        return drained;
    }
}
