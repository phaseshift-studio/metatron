package studio.phaseshift.metatron.isa.mach.network;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.furi.QProc;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.machine.BasicMachine;
import studio.phaseshift.metatron.isa.mach.type.processor.SwarmProcessor;
import studio.phaseshift.metatron.isa.mach.type.thread.FutureObj;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static studio.phaseshift.metatron.Tokens.PATTERN;
import static studio.phaseshift.metatron.Tokens.QPROC;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.MEM_SPACE_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_MACHINE_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * THE HAND-WIRED CLUSTER, same JVM: the distributed forms written by hand rather than produced by the rewrite, run
 * on separate Machine instances, with each machine's data supplied through START rather than carried in its code.
 * <p>
 * Here for the shape the rewrite cannot generate -- a MIDDLE MACHINE that is both waiter and reporter, so a depth-3
 * cascade -- and for the two things the generated rows cannot reach: the mailbox as a REGISTERED SPACE carrying a
 * subq rather than the machine answering for it, and genuinely separate machines rather than one shared root.
 * <p>
 * NO PACING ANYWHERE: the chains carry no print or log calls, because a log on the success path is not
 * instrumentation but synchronization, and one here once hid a race. The only logging is on the failure path -- a
 * bounded wait whose catch dumps the mailboxes and what each machine halted with, which is what turns a hang into a
 * diagnosis instead of a wait nobody can explain.
 */
//@Isolated
public class MachineSwarmMigrationTest extends AbstractMetatronTest {

    private static Machine newMachineAt(final studio.phaseshift.metatron.furi.fURI vid) {
        final boolean booting = BootLoader.BOOTING;
        BootLoader.BOOTING = true;
        try {
            return BasicMachine.of(MACH_MACHINE_TID, vid);
        } finally {
            BootLoader.BOOTING = booting;
        }
    }

    /**
     * The barrier's mailbox is a space, not machinery. It must own the pattern AND carry a subq qproc, because the
     * barrier SUBSCRIBES to the mailbox and the peers WRITE to it. Registering a space writes it at its own vid,
     * which needs an owner for /usr/marko -- hence the BOOTING guard, the same one newMachineAt uses.
     */
    private static void registerBarrierSpace(final Machine... machines) {
        final boolean booting = BootLoader.BOOTING;
        BootLoader.BOOTING = true;
        try {
            // ONE shared mailbox space, visible to every machine that participates. A write resolves against the
            // machine the writing thread stands in, so a space registered only on root is invisible to a peer's
            // processor thread -- and its report lands where nobody is subscribed.
            final memSpace mailboxes = memSpace.of(rec(
                    uri(PATTERN), uri("/usr/marko/#"),
                    uri(QPROC), lst(QCollection.subQ())), f("/usr/marko"));
            for (final Machine machine : machines)
                machine.memory().addSpace(mailboxes);
        } finally {
            BootLoader.BOOTING = booting;
        }
    }

    /**
     * THE CASCADE, and the one thing the rewrite cannot produce: a MIDDLE MACHINE that is both waiter and reporter.
     * B waits on C, folds what arrives with its own, and reports up to A -- {@code barrier(uri).sum().to(uri)} -- so a
     * tree of any depth is built from the same two instructions and a cluster of clusters needs nothing new. The
     * generated cluster is a STAR (a home and its leaves), so nothing else exercises this shape.
     *
     * <p>It also covers what the generated rows do not: the distributed forms written BY HAND rather than produced
     * by the rewrite, the mailbox as a REGISTERED SPACE carrying a subq (rather than the machine answering for it),
     * and data supplied per machine through START rather than carried in the code as a start(...).
     */
    @Test
    public void testTheCascadeAcrossThreeMachines() throws Exception {
        final Machine a = Machine.current();
        final Machine b = newMachineAt(f("/mach/swarmB"));
        final Machine c = newMachineAt(f("/mach/swarmC"));
        registerBarrierSpace(a, b, c);

        // A gathers from B and C and reduces; B gathers from C, folds, and reports up; C is a leaf that reports.
        final Code codeA = a.compiler().apply(ObjmtronSerializer.parse(
                "plus(2).plus(10).barrier(/usr/marko/a/barrier/b).barrier(/usr/marko/a/barrier/c).sum()")).asCode();
        final Code codeB = b.compiler().apply(ObjmtronSerializer.parse(
                "plus(2).plus(10).sum().to(/usr/marko/a/barrier/b)")).asCode();
        final Code codeC = c.compiler().apply(ObjmtronSerializer.parse(
                "plus(2).plus(10).sum().to(/usr/marko/a/barrier/c)")).asCode();

        final SwarmProcessor pa = a.processor().code(codeA).as();
        final SwarmProcessor pb = b.processor().code(codeB).as();
        final SwarmProcessor pc = c.processor().code(codeC).as();

        final FutureObj<Obj> aResult = pa.applyAsync(objs(jnt(1), jnt(2), jnt(3)));
        pb.applyAsync(objs(jnt(10), jnt(20), jnt(30)));
        pc.applyAsync(objs(jnt(100), jnt(200), jnt(300)));

        // No threads and no ordering: all three start their chains at once and the barriers rendezvous among them.
        // C writing before A has even reached its second barrier is exactly the case subscribe-then-read exists for
        // -- the read finds the report, so a late subscriber loses nothing.
        //
        // 774 = A's 42, plus B's 96, plus C's 636, folded through the cascade. THE WAIT IS BOUNDED so that a stall
        // says WHAT IT IS instead of hanging the run: whether a report was never written, or was written and the
        // barrier never released. Those are different bugs and the mailboxes tell them apart.
        final Obj result;
        try {
            result = aResult.get(30, TimeUnit.SECONDS);
        } catch (final Exception e) {
            LOG.warn("cascade stalled: %s", e);
            LOG.warn("  a/barrier/b holds %s", Machine.read(f("/usr/marko/a/barrier/b")));
            LOG.warn("  a/barrier/c holds %s", Machine.read(f("/usr/marko/a/barrier/c")));
            LOG.warn("  halted: a=%s b=%s c=%s", pa.halted(), pb.halted(), pc.halted());
            throw e;
        }
        assertEquals(jnt(774), result,
                "A folded its own partition with the partials B and C each reported to the mailbox it waits on");
    }

    /**
     * The mailbox space with a seam at the one step of subscribe-then-read a barrier does not expose: its READ of the
     * mailbox. {@code beforeRead} runs as that read arrives and {@code afterRead} once it has its answer, which is how a
     * test puts a report exactly where a race once put it -- after the read, or between the subscribe and the read.
     */
    private static final class WatchedMailboxes extends memSpace {

        private final fURI mailbox;
        private final Runnable beforeRead;
        private final Runnable afterRead;

        private WatchedMailboxes(final fURI root, final fURI mailbox, final Runnable beforeRead, final Runnable afterRead,
                                 final QProc... qs) {
            super(mutableMap(rec(uri(PATTERN), uri(root.extend("#")), uri(QPROC), lst(qs)).jvm()), MEM_SPACE_TID, root, true);
            this.mailbox = mailbox;
            this.beforeRead = beforeRead;
            this.afterRead = afterRead;
        }

        /**
         * Registering writes the space at its own vid, which needs an owner -- the BOOTING guard registerBarrierSpace
         * uses. The space registers itself on the machine it is constructed in.
         */
        private static void register(final fURI root, final fURI mailbox, final Runnable beforeRead,
                                     final Runnable afterRead, final QProc... qs) {
            final boolean booting = BootLoader.BOOTING;
            BootLoader.BOOTING = true;
            try {
                new WatchedMailboxes(root, mailbox, beforeRead, afterRead, qs);
            } finally {
                BootLoader.BOOTING = booting;
            }
        }

        @Override
        public Obj read(final fURI vid) {
            // the seams are not set while the super constructor registers the space
            final boolean watched = null != this.mailbox && !vid.hasQ() && vid.equals(this.mailbox);
            if (watched)
                this.beforeRead.run();
            final Obj read = super.read(vid);
            if (watched)
                this.afterRead.run();
            return read;
        }
    }

    /**
     * A's chain from the cascade with one mailbox: its own partition folds to 42, so with a report of 96 the barrier
     * releases 138 -- and a report counted twice would be 234.
     */
    private static FutureObj<Obj> barrierOn(final fURI mailbox) {
        final Code code = Machine.current().compiler().apply(ObjmtronSerializer.parse(
                "plus(2).plus(10).barrier(" + mailbox + ").sum()")).asCode();
        final SwarmProcessor processor = Machine.current().processor().code(code).as();
        return processor.applyAsync(objs(jnt(1), jnt(2), jnt(3)));
    }

    private static void await(final CountDownLatch latch, final String what) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS))
                throw MTronException.of("timed out waiting until %s", what);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw MTronException.of(e);
        }
    }

    /**
     * A REPORT THAT ARRIVES AFTER THE BARRIER READ releases it through the subscription -- the case the subscription
     * exists for. It once never fired: the barrier subscribed with a plain rec, which subq takes for the code itself,
     * so the broadcast applied a rec (which answers itself) and the barrier waited forever. Only a report ALREADY
     * stored when the barrier read could release it -- which is why the cascade passed on an idle machine and stalled
     * under load, with both reports sitting in their mailboxes.
     */
    @Test
    public void testAReportThatArrivesAfterTheReadReleasesTheBarrier() throws Exception {
        final fURI mailbox = f("/usr/relay/late/report");
        final CountDownLatch read = new CountDownLatch(1);
        WatchedMailboxes.register(f("/usr/relay/late"), mailbox, () -> {
        }, read::countDown, QCollection.subQ());
        final FutureObj<Obj> released = barrierOn(mailbox);
        await(read, "the barrier subscribed and read its empty mailbox");
        Machine.write(mailbox, jnt(96));
        assertEquals(jnt(138), released.get(10, TimeUnit.SECONDS),
                "the report published after the read released the barrier");
    }

    /**
     * A REPORT IN FLIGHT -- past the broadcast, not yet stored -- while the barrier subscribes and reads. The guard
     * holds the report where a preempted writer used to sit: declared after subq, it ran after a broadcast that
     * preceded the store, so the publication went to nobody and the store landed after the read. A broadcast that
     * waits for the store sees the subscription instead.
     */
    @Test
    public void testAReportInFlightAcrossTheSubscribeAndReadReleasesTheBarrier() throws Exception {
        final fURI mailbox = f("/usr/relay/flight/report");
        final CountDownLatch inFlight = new CountDownLatch(1);
        final CountDownLatch read = new CountDownLatch(1);
        final QProc hold = QProc.Helper.build(QProc.QPROC_TID.extend("holdq"), f("holdq"))
                .qlessWrite((vid, obj) -> {
                    if (vid.equals(mailbox)) {
                        inFlight.countDown();
                        await(read, "the barrier subscribed and read its empty mailbox");
                    }
                    return noobj();
                }).create();
        WatchedMailboxes.register(f("/usr/relay/flight"), mailbox, () -> {
        }, read::countDown, QCollection.subQ(), hold);
        Thread.ofVirtual().start(() -> Machine.write(mailbox, jnt(96)));
        await(inFlight, "the report is held short of the store");
        final FutureObj<Obj> released = barrierOn(mailbox);
        assertEquals(jnt(138), released.get(10, TimeUnit.SECONDS),
                "the report stored after the read was published to the subscription made before it");
    }

    /**
     * A REPORT STORED BETWEEN THE SUBSCRIBE AND THE READ reaches the barrier twice -- published to the subscription,
     * and found by the read -- and is ONE report. The pause lets the publication land first, so a barrier that counted
     * both deliveries would show it.
     */
    @Test
    public void testAReportDeliveredBothWaysIsCountedOnce() throws Exception {
        final fURI mailbox = f("/usr/relay/twice/report");
        final AtomicBoolean reported = new AtomicBoolean();
        WatchedMailboxes.register(f("/usr/relay/twice"), mailbox, () -> {
            if (reported.compareAndSet(false, true)) {
                Machine.write(mailbox, jnt(96));
                CommonUtil.sleepThread(250);
            }
        }, () -> {
        }, QCollection.subQ());
        assertEquals(jnt(138), barrierOn(mailbox).get(10, TimeUnit.SECONDS),
                "a report both published and read is folded in once");
    }
}
