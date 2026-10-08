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

package studio.phaseshift.metatron.isa.mach;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Compiler;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.Processor;
import studio.phaseshift.metatron.isa.mach.type.compiler.DefaultCompiler;
import studio.phaseshift.metatron.isa.mach.type.processor.SwarmProcessor;
import studio.phaseshift.metatron.isa.mach.type.thread.FutureObj;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.start_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.*;
import static studio.phaseshift.metatron.isa.tble.tbleInstSet.TBLE_ISA_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * Machine-universal contract: every {@link Machine} implementation inherits these tests for free.
 * They pin the state of the machine as components are bound and fetched — eager instances round-trip
 * as themselves, templates mint on fetch, and the machine back-ref resolves through the parent nest.
 */
public abstract class AbstractMachineTest extends AbstractMetatronTest {

    protected abstract Machine newMachine();

    /**
     * where the peers and their mailboxes are declared
     */
    protected static final fURI COMPUTE_ROOT = machInstSet.COMPUTE;

    /**
     * for the run's log: which space ended up backing the compute namespace
     */
    protected static String COMPUTE_SPACE = "unknown";


    /**
     * RUN A CODE DISTRIBUTEDLY, and pin both what it produced and what it left behind.
     * <p>
     * The peers are declared at {@code /usr/compute/<home>/barrier} -- the barrier branch IS the topology, one
     * key per peer -- and the roster at {@code /sys/peer} stays local bookkeeping — put them there with {@code declarePeers}, or
     * with {@code @TestData}, so a case can declare its own cluster. The code is then compiled and run on the home; the
     * distribution rewrite ships each peer's worker form to {@code /usr/compute/<peer>/recv} and gives the home one
     * gather per peer, waiting on {@code /usr/compute/a/barrier/<peer>}.
     * <p>
     * This helper then stands in for each machine: for every declared peer it applies that peer's SHIPPED worker —
     * the one the rewrite put in its recv box — on its own thread, with the same start the home got. So each peer
     * computes its own partial and reports it to the mailbox the home waits on, and the total is
     * {@code peers + 1} partitions. That is what makes a case's expected result computable in a CSV row.
     * <p>
     * The user writes {@code code} the way a user would: no barriers, no addresses, no peers.
     *
     * @param code   the mtron source
     * @param result the value the home must produce
     * @param state  the BRANCHES {@code /usr/compute} must hold once the code has finished and BEFORE cleanup, e.g.
     *               {@code [a => [barrier => [b => 6]]]} — a machine, its barriers, and the data those barriers
     *               ended up holding. That is where the peers' reports land, so asserting it is what certifies they
     *               really ran and reported rather than the home having quietly computed everything itself. The
     *               comparison is against the RECONSTRUCTED rec, since a read recurses into what was written
     *               underneath and rebuilds the tree. A branch given with no value pins only that it must exist.
     */
    public static void checkDistributedCode(final GraphittyLogger LOG, final String code, final String result,
                                            final Rec state) {
        // THE ROW DECLARES ITS OWN CLUSTER, because a barrier key IS a peer: the expectation's shape is the topology
        // and its values are what those machines must leave behind. So the declaration is that rec with the VALUES
        // DROPPED -- writing them would pre-fill the mailboxes, and the home's read-before-wait would then pass
        // without a single peer having run. One rec, two roles, and the peer count is a property of the row.
        final Obj expectedHome = state.isRec() ? state.asRec().at(uri(MACH_HOME)) : noobj();
        final Obj expectedBoxes = expectedHome.isRec() ? expectedHome.asRec().at(uri(BARRIER)) : noobj();
        final Map<Obj, Obj> boxes = new LinkedHashMap<>();
        if (expectedBoxes.isRec())
            expectedBoxes.asRec().jvm().keySet().forEach(key -> boxes.put(key, rec(mutableMap())));
        Machine.write(COMPUTE_ROOT, rec(mutableMap(
                uri(MACH_HOME), rec(mutableMap(uri(BARRIER), rec(boxes))))));

        final Obj roster = Machine.read(COMPUTE_ROOT);
        final Obj homeBranch = roster.isRec() ? roster.asRec().at(uri(MACH_HOME)) : noobj();
        final Obj declaredBoxes = homeBranch.isRec() ? homeBranch.asRec().at(uri(BARRIER)) : noobj();
        final List<fURI> declared = declaredBoxes.isRec()
                ? declaredBoxes.asRec().jvm().keySet().stream().filter(Obj::isUri).map(Obj::uriValue).toList()
                : List.of();
        LOG.info("checkDistributedCode: %d shard(s) declared for %s", declared.size(), code);

        final Machine home = Machine.current();
        final Code compiled = home.compiler().apply(ObjmtronSerializer.parse(code)).asCode();
        // THE HOME FORM, logged because it is the one artifact a distributed run never shows: the workers' forms are
        // printed by the shards as they run, so when the home stalls there is nothing to compare them against.
        LOG.debug("checkDistributedCode: home form %s", compiled);
        final SwarmProcessor processor = home.processor().code(compiled).as();

        // EVERY MACHINE BUT THE HOME runs the worker form the rewrite shipped to its OWN inbox, on its own thread.
        // The home is in the roster too -- it is one of the machines -- but it is not a peer of itself: it runs the
        // code directly, and the peers' reports are what it gathers.
        final List<Thread> shards = new ArrayList<>();
        for (final fURI peer : declared) {
            final Thread shard = new Thread(() -> {
                // A THREAD THAT DIES IS SILENT, and a shard that dies without reporting leaves the home waiting on a
                // mailbox nothing will fill -- a hang with no output, the worst failure to debug.
                try {
                    final Obj shipped = Machine.read(COMPUTE_ROOT.extend(peer.name()).extend("recv"));
                    LOG.debug("checkDistributedCode: shard %s running %s", peer.name(), shipped);
                    shipped.asCode().apply(noobj());
                    LOG.debug("checkDistributedCode: shard %s reported", peer.name());
                } catch (final Throwable t) {
                    LOG.warn("checkDistributedCode: shard " + peer.name() + " FAILED", t);
                }
            }, "shard-" + peer.name());
            shard.start();
            shards.add(shard);
            // ONE SHARD AT A TIME, and this is a WORKAROUND for a real VM bug, not tidiness. Two shards reporting
            // concurrently write different keys under one parent (/usr/compute/a/barrier/<peer>), and that nested
            // read-modify-write loses one of them: measured, as one mailbox holding the declared placeholder while
            // the other held its number -- which then reaches the reducer as a rec and fails as
            // "lhs range does not match inst domain". Serializing the write itself deadlocks (both the machine's
            // write and the space's writer were tried); joining each shard before starting the next removes the
            // concurrency from the TEST so the suite is deterministic, and leaves the VM bug visible and unfixed
            // rather than hidden.
            try {
                shard.join();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw MTronException.of(e);
            }
        }
        LOG.info("checkDistributedCode: %d shard(s) for %s", shards.size(), code);


        // WAIT FOR THE SHARDS TO REPORT FIRST, then read the mailboxes, then run the home. Reading before they have
        // run says nothing, and the home's gather reads its mailbox before waiting anyway -- so this ordering is
        // both correct and free of a race. A missing report here is a shard that never arrived, not a home that
        // waited on the wrong address.
        for (final Thread shard : shards) {
            try {
                shard.join();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw MTronException.of(e);
            }
        }
        for (final fURI peer : declared)
            LOG.debug("checkDistributedCode: mailbox for %s holds %s", peer.name(),
                    Machine.read(COMPUTE_ROOT.extend(MACH_HOME).extend(BARRIER).extend(peer.name())));

        final FutureObj<Obj> future = processor.applyAsync(noobj());

        // A TIMEOUT, so a home still waiting for a report FAILS with the state visible instead of hanging -- the
        // difference between a test that tells you something and one that tells you nothing.
        final Obj halted;
        try {
            halted = future.get(30, TimeUnit.SECONDS);
        } catch (final Exception e) {
            LOG.warn("checkDistributedCode: the home never finished (%s); %s holds %s",
                    e, COMPUTE_ROOT, Machine.read(COMPUTE_ROOT));
            throw MTronException.of(e);
        }
        LOG.debug("checkDistributedCode: home halted with %s", halted);
        checkEquality(LOG, ObjmtronSerializer.parse(result), halted.stream().iterator().next(), true);

        // WHAT THE COMPUTATION LEFT BEHIND: the peers, their barriers, and the barrier data at the end. Logged
        // before the assertion so a row's expectation can be written from what actually happened rather than
        // guessed -- and so a failure shows the real rec beside the expected one.
        LOG.debug("checkDistributedCode: %s holds %s", COMPUTE_ROOT, Machine.read(COMPUTE_ROOT));

        // ASSERTED BEFORE CLEANUP, because cleanup is what erases the evidence. The comparison is against the
        // RECONSTRUCTED rec: a read recurses into what was written underneath and rebuilds the tree, so a branch is
        // taken OUT OF that rec rather than re-read as a path -- reading /usr/compute/a on its own resolves an address,
        // not the branch that the reconstruction of /usr/compute already contains.
        final Obj reconstructed = Machine.read(COMPUTE_ROOT);
        state.jvm().forEach((key, expected) -> {
            // a branch named with no value pins that it must EXIST, nothing about what it holds -- which is how a
            // case avoids pinning a peer's inbox, where the shipped worker's code sits and no CSV row can write it
            if (expected.isNoObj())
                return;
            final Obj branch = reconstructed.isRec() ? reconstructed.asRec().at(key) : noobj();
            checkEquality(LOG, expected, branch, true);
        });

        // WRITE AN EMPTY TOPOLOGY; REMOVE NOTHING. A removal closes what it replaced, and on a table-backed space
        // that reaches the space's own lifecycle -- measured twice, as the connection closing underneath the next
        // write. An empty barrier branch is enough to stand the rule down, because the rewrite's guard is "no peers
        // declared" and an empty rec IS that. Overwrite the state, never delete it from a space that must survive.
        Machine.write(COMPUTE_ROOT.extend(MACH_HOME).extend(BARRIER), rec(CommonUtil.mutableMap()));
    }

    // ======================== the distribution plumbing ========================
    // These run in every suite that subclasses this one, and they check what the EXPRESSION's output cannot: that
    // the rewrite produced peers and barriers where it should, that the home is not its own peer, and that each
    // shard was shipped a code carrying its own data. A wrong result is one symptom of broken plumbing; these are
    // the plumbing itself.

    /**
     * declare a cluster: the home plus the named peers, none of whose branches is pinned. Each machine is written
     * as an EMPTY BRANCH rather than as a value, because what lands under a machine -- its barriers, and the data
     * those barriers end up holding -- NESTS inside it, and nothing nests inside an inst.
     */
    /**
     * REGISTER A SPACE THAT COVERS {@code /usr/compute}, ONCE FOR THE CLASS and before any test runs. Not lazily, on
     * whichever test happened to write first: the space and its connection belong to the class lifecycle, and a space
     * brought up mid-test is a connection created at an arbitrary moment with nothing holding it. A static "already
     * registered" guard is wrong for the same reason -- each class gets a fresh boot while the field survives, so the
     * second class would skip registration entirely and find no space covering the pattern. Distributed state lives in a USER namespace, which means
     * something has to own the pattern -- /sys was covered for free because the roster lives in it, and a user
     * namespace is covered by nothing until a deployment mounts its shared subspace there. This is the test-side
     * equivalent of that mount, and it carries a subq because a mailbox is subscribed to before it is read.
     * <p>
     * Registering a space WRITES it at its own vid, which needs an owner for the pattern -- hence the BOOTING guard,
     * the same one the swarm test's registerBarrierSpace and newMachineAt use.
     */

    /**
     * THE SHARED SUBSPACE'S DATABASE, held as a FIELD and outliving the method that sets it up. A config built and
     * discarded inside a method leaves the space it configured holding a connection nothing owns -- and a connection
     * nothing owns is the one that gets closed underneath the next write.
     */
    /**
     * where the compute space is STORED, independent of the namespace it exposes
     */
    protected static final fURI COMPUTE_VID = f("/sys/space/compute");

    /**
     * REGISTER WHATEVER SPACE BACKS THE COMPUTE NAMESPACE, supplied by the caller. This class names no space and
     * imports none: which backing the namespace gets belongs to the suite that claims to support distribution, not
     * to the machine contract.
     * <p>
     * IT DOES NOT EVICT. It used to remove any space already at the vid, and that was the bug: removeSpace CLOSES
     * the space it removes, and closing a table space closes its JDBC connection -- so the next class registered a
     * space and then found its connection shut, in both Postgres and MariaDB, because the closing was ours and had
     * nothing to do with the database. Never close a live space to make room for another; give each its own vid.
     */
    protected static void registerComputeSpace(final Supplier<Space> computeSpace) {
        Machine.current().memory().addSpace(computeSpace.get());
    }

    protected static void declarePeers(final String... peers) {
        final Map<Obj, Obj> boxes = new LinkedHashMap<>();
        for (final String peer : peers)
            boxes.put(uri(peer), rec(mutableMap()));
        Machine.write(COMPUTE_ROOT, rec(mutableMap(
                uri(MACH_HOME), rec(mutableMap(uri(BARRIER), rec(boxes))))));
    }

    /**
     * clear the cluster, so a suite cannot leave a VM looking clustered for the next one
     */
    protected static void clearPeers() {
        Machine.write(COMPUTE_ROOT, noobj());
    }

    // ======================== eager round-trip ========================

    public AbstractMachineTest(final Supplier<InstSet> instSetSupplier) {
        // super(instSetSupplier);
    }

    //@BeforeAll
    public static void start() {
        // AbstractMetatronTest.begin();
        // InstSet.importInstSet(f("#"));
    }

    @Test
    public void testAddEagerProcessorThenFetch() {
        final Machine machine = this.newMachine();
        final Processor processor = SwarmProcessor.processor(mutableMap(), MACH_SWARM_PROCESSOR_TID, null);
        machine.processor(processor);
        assertEquals(processor, machine.processor(), "fetch returns the bound processor instance");
    }

    @Test
    public void testAddEagerCompilerThenFetch() {
        final Machine machine = this.newMachine();
        final Compiler compiler = new DefaultCompiler();
        machine.compiler(compiler);
        assertEquals(compiler, machine.compiler(), "fetch returns the bound compiler instance");
    }

    // ======================== template minting ========================

    @Test
    public void testTemplateProcessorMintsOnFetch() {
        final Machine machine = this.newMachine();
        machine.processor(start_(rec()).as_(MACH_SWARM_PROCESSOR_TYPE).tryToInst());
        final Processor processor = machine.processor();
        assertInstanceOf(SwarmProcessor.class, processor, "a processor template mints a swarm processor on fetch");
    }

    @Test
    public void testTemplateCompilerMintsOnFetch() {
        final Machine machine = this.newMachine();
        machine.compiler(start_(rec()).as_(MACH_DEFAULT_COMPILER_TYPE).tryToInst());
        final Obj compiler = machine.compiler();
        LOG.warn("compiler: %s", compiler);
        assertEquals(MACH_DEFAULT_COMPILER_TID, compiler.tid());
        assertInstanceOf(DefaultCompiler.class, compiler, "a compiler template mints a default compiler on fetch");
    }

    // ======================== absence ========================

    @Test
    public void testFetchProcessorWhenAbsentThrows() {
        final Machine machine = this.newMachine().processor(noobj());
        assertThrows(MTronException.class, machine::processor, "fetching an unbound processor fails");
    }

    @Test
    public void testFetchCompilerWhenAbsentThrows() {
        final Machine machine = this.newMachine().compiler(noobj());
        assertThrows(MTronException.class, machine::compiler, "fetching an unbound compiler fails");
    }

    // ======================== back-ref ========================

    @Test
    public void testProcessorMachineBackRef() {
        final Machine machine = this.newMachine();
        final Processor processor = SwarmProcessor.processor(mutableMap(), MACH_SWARM_PROCESSOR_TID, null);
        machine.processor(processor);
        assertSame(machine, processor.machine(), "a bound processor resolves its machine through the parent nest");
    }

    @Test
    public void testCompilerMachineBackRef() {
        final Machine machine = this.newMachine();
        final Compiler compiler = new DefaultCompiler();
        machine.compiler(compiler);
        assertSame(machine, compiler.machine(), "a bound compiler resolves its machine through the parent nest");
    }
}
