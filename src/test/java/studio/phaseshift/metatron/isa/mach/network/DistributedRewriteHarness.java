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

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.impl.MCode;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.processor.SwarmProcessor;
import studio.phaseshift.metatron.isa.mach.type.thread.FutureObj;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.MTronException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.AbstractMetatronTest.checkEquality;
import static studio.phaseshift.metatron.Tokens.BARRIER;
import static studio.phaseshift.metatron.isa.m.mInstSet.BARRIER_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.START_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.SUM_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.TO_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.COMPUTE;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_HOME;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * A reusable harness that decomposes one distributed-rewrite run into FOUR first-class phases, each an explicit
 * method rather than an inline, one-off assertion: {@link #compile} (structure), {@link #execute} (process),
 * {@link #assertProvenance} (data), and {@link #assertResult} (fold). {@link #declare} / {@link #clear} bracket the
 * cluster so a test declares a topology and cleans it up without leaking state between cases.
 *
 * <p>The distribution rewrite under test is the one the machine ISA ships: given a monoidic reducer (a {@code sum}),
 * the compiler slices the seed bulk across the declared peers, ships each peer a worker form to its {@code recv}
 * inbox, and rewrites the home's reducer into {@code barrier(<mailbox>)... · sum} so the home gathers each peer's
 * partial before re-applying the reducer. What a result alone cannot show — that the home actually gathered per peer,
 * that each worker ran, and that each partial is a function of <em>its own</em> slice rather than some other peer's —
 * is what the four phases pin down.
 */
public final class DistributedRewriteHarness {

    private DistributedRewriteHarness() {
    }

    /** the namespace the cluster is declared in and each peer's worker form is shipped to */
    public static final fURI COMPUTE_ROOT = COMPUTE;

    // ======================== setup / teardown ========================

    /**
     * Declare a cluster: the home plus the named peers. Each peer is written as an empty branch under the home's
     * barrier, because the barrier key IS the peer — what lands under a machine (its reports) nests inside it.
     */
    public static void declare(final String... peers) {
        final Map<Obj, Obj> boxes = new LinkedHashMap<>();
        for (final String peer : peers)
            boxes.put(uri(peer), rec(mutableMap()));
        Machine.write(COMPUTE_ROOT, rec(mutableMap(
                uri(MACH_HOME), rec(mutableMap(uri(BARRIER), rec(boxes))))));
    }

    /**
     * Clear the cluster so a later case starts from an unclustered machine. Overwriting with {@code noobj} stands the
     * rewrite's guard down: no peers declared, so nothing is gathered from.
     */
    public static void clear() {
        Machine.write(COMPUTE_ROOT, noobj());
    }

    // ======================== phase 1: compile (structural) ========================

    /**
     * Compile the source against the declared cluster and pin the FORMS the rewrite produced: the master (the home's
     * code, carrying one gather per peer and re-applying the reducer) and, per peer, the shipped worker (starting
     * with that peer's own slice and ending by reporting to its mailbox). The peers must already be declared with
     * {@link #declare} — compiling is what ships the worker forms, and it reads the roster out of the space.
     */
    public static Compilation compile(final GraphittyLogger LOG, final String source, final String... peers) {
        final List<String> sorted = Arrays.stream(peers).sorted().toList();

        final Code master = Machine.current().compiler().apply(ObjmtronSerializer.parse(source)).asCode();
        LOG.debug("distributed-rewrite: home form %s", master);

        // the home gathers once per peer, each at that peer's mailbox — never at the home itself, which is in the
        // roster but is not a peer of itself. Order is the rewriter's (sorted peer names).
        final List<String> expectedBoxes = sorted.stream()
                .map(peer -> COMPUTE_ROOT.extend(MACH_HOME).extend(BARRIER).extend(peer).toString())
                .toList();
        final List<String> gathers = master.insts().stream()
                .filter(inst -> inst.tid().basePath().equals(BARRIER_INST_TID))
                .map(inst -> inst.arg(0).toString())
                .toList();
        assertEquals(expectedBoxes, gathers, "one gather per peer, addressed at that peer's mailbox");

        // the reducer is re-applied at home, behind the gathers it now waits on
        assertTrue(master.insts().stream().anyMatch(inst -> inst.tid().basePath().equals(SUM_INST_TID)),
                "the home re-applies the reducer: " + master);

        final Map<String, Code> slaves = new LinkedHashMap<>();
        final Map<String, List<Obj>> slices = new LinkedHashMap<>();
        final Map<String, fURI> mailboxes = new LinkedHashMap<>();
        for (final String peer : sorted) {
            final fURI mailbox = COMPUTE_ROOT.extend(MACH_HOME).extend(BARRIER).extend(peer);
            final Code slave = Machine.read(COMPUTE_ROOT.extend(peer).extend("recv")).asCode();
            LOG.debug("distributed-rewrite: shard %s form %s", peer, slave);

            final List<Inst> insts = slave.insts();
            assertEquals(START_INST_TID, insts.getFirst().tid().basePath(),
                    "shard " + peer + " arrives carrying its own data, as an isInitial start");
            assertEquals(TO_INST_TID, insts.getLast().tid().basePath(),
                    "shard " + peer + " ends by reporting");
            assertEquals(mailbox.toString(), insts.getLast().arg(0).toString(),
                    "shard " + peer + " reports to the very mailbox the home gathers from");
            assertTrue(insts.stream().anyMatch(inst -> inst.tid().basePath().equals(SUM_INST_TID)),
                    "shard " + peer + " reduces its own slice before reporting: " + slave);

            final Obj raw = insts.getFirst().arg(0);
            final List<Obj> slice = raw.isLst() ? raw.asLst().elements().toList() : raw.stream().toList();
            slaves.put(peer, slave);
            slices.put(peer, slice);
            mailboxes.put(peer, mailbox);
        }
        return new Compilation(source, sorted, master, slaves, slices, mailboxes);
    }

    // ======================== phase 2: execute (process) ========================

    /**
     * Run each worker form on its own thread, then the master, and assert each machine actually RAN — an explicit
     * per-machine signal (a thread-safe set each worker populates after it finishes) rather than an inference from a
     * filled mailbox. The workers are serialized one at a time (joined before the next starts) because two peers
     * writing two keys under one parent concurrently loses one of them; the serialization keeps the suite
     * deterministic while leaving the VM bug visible.
     */
    public static Execution execute(final GraphittyLogger LOG, final Compilation compilation) {
        final Set<String> ran = ConcurrentHashMap.newKeySet();
        final List<Thread> shards = new ArrayList<>();
        for (final String peer : compilation.peers()) {
            final Code slave = compilation.slaves().get(peer);
            final Thread shard = new Thread(() -> {
                try {
                    slave.apply(noobj());
                    LOG.debug("distributed-rewrite: shard %s ran %s", peer, slave);
                    ran.add(peer);
                } catch (final Throwable t) {
                    LOG.warn("distributed-rewrite: shard " + peer + " FAILED", t);
                }
            }, "shard-" + peer);
            shard.start();
            shards.add(shard);
            try {
                shard.join();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw MTronException.of(e);
            }
        }
        // asserted BEFORE the master runs: a worker that died silently would otherwise read as a hang, not a failure.
        assertEquals(Set.copyOf(compilation.peers()), ran, "every shard ran, not merely left a mailbox behind");

        final Machine home = Machine.current();
        final SwarmProcessor processor = home.processor().code(compilation.master()).as();
        final FutureObj<Obj> future = processor.applyAsync(noobj());
        final Obj halted;
        try {
            halted = future.get(30, TimeUnit.SECONDS);
        } catch (final Exception e) {
            LOG.warn("distributed-rewrite: the home never finished (%s); %s holds %s",
                    e, COMPUTE_ROOT, Machine.read(COMPUTE_ROOT));
            throw MTronException.of(e);
        }
        final Obj result = halted.stream().iterator().next();
        LOG.debug("distributed-rewrite: home halted with %s", result);

        final Map<String, Obj> partials = new LinkedHashMap<>();
        for (final String peer : compilation.peers())
            partials.put(peer, Machine.read(compilation.mailboxes().get(peer)));
        return new Execution(ran, partials, result);
    }

    // ======================== phase 3: provenance (data) ========================

    /**
     * Pin each partial to the slice that produced it: re-derive the partial from the worker's OWN seed slice (its
     * {@code start} bulk run through the same floatable prefix and reducer, with the trailing {@code to} dropped) and
     * assert the mailbox holds exactly that. This ties slave {@code i}'s output to slave {@code i}'s input — not to
     * some other peer's — instead of only checking that a mailbox ended up non-empty.
     * <p>
     * The recompute runs against a CLEARED cluster, then the cluster is re-declared: applying a bare reducer chain
     * while peers are still declared would re-distribute it (ship workers again) rather than fold the slice in place.
     */
    public static void assertProvenance(final GraphittyLogger LOG, final Compilation compilation,
                                        final Execution execution) {
        clear();
        try {
            for (final String peer : compilation.peers()) {
                final List<Inst> insts = compilation.slaves().get(peer).insts();
                final Code probe = MCode.of(new ArrayList<>(insts.subList(0, insts.size() - 1)));
                final Obj expected = probe.apply();
                final Obj partial = execution.partials().get(peer);
                LOG.debug("distributed-rewrite: shard %s consumed %s -> partial %s [mailbox %s]",
                        peer, compilation.slices().get(peer), expected, partial);
                checkEquality(LOG, expected, partial, true);
            }
        } finally {
            declare(compilation.peers().toArray(String[]::new));
        }
    }

    // ======================== phase 4: result ========================

    /**
     * The folded total, compared against the expected value. This is the number the user wrote the code for — the
     * one thing distribution must leave unchanged however the seed is sliced.
     */
    public static void assertResult(final GraphittyLogger LOG, final Execution execution, final String expected) {
        checkEquality(LOG, ObjmtronSerializer.parse(expected), execution.result, true);
    }

    /**
     * The whole run, all four phases, with the topology declared up front and cleared no matter what.
     */
    public static void verify(final GraphittyLogger LOG, final String source, final String expected,
                              final String... peers) {
        declare(peers);
        try {
            final Compilation compilation = compile(LOG, source, peers);
            final Execution execution = execute(LOG, compilation);
            assertProvenance(LOG, compilation, execution);
            assertResult(LOG, execution, expected);
        } finally {
            clear();
        }
    }

    // ======================== the phases' values ========================

    /**
     * What compiling produced: the master (home) form, each peer's shipped worker form, the seed slice each worker
     * arrived carrying, and the mailbox each worker reports to.
     */
    public record Compilation(String source, List<String> peers, Code master, Map<String, Code> slaves,
                              Map<String, List<Obj>> slices, Map<String, fURI> mailboxes) {
    }

    /**
     * What running produced: the set of peers that demonstrably ran, the partial each peer reported to its mailbox,
     * and the home's folded total.
     */
    public record Execution(Set<String> ran, Map<String, Obj> partials, Obj result) {
    }
}
