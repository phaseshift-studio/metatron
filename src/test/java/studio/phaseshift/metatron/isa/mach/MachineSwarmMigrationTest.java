package studio.phaseshift.metatron.isa.mach;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.machine.BasicMachine;
import studio.phaseshift.metatron.isa.mach.type.processor.SwarmProcessor;
import studio.phaseshift.metatron.isa.mach.type.thread.FutureObj;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static studio.phaseshift.metatron.Tokens.PATTERN;
import static studio.phaseshift.metatron.Tokens.QPROC;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_MACHINE_TID;

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
                    uri(QPROC), lst(QCollection.subq())), f("/usr/marko"));
            for (final Machine machine : machines)
                machine.addSpace(mailboxes);
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
        final Machine a = Machine.root();
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
            LOG.warn("  a/barrier/b holds %s", Machine.readFromSpace(f("/usr/marko/a/barrier/b")));
            LOG.warn("  a/barrier/c holds %s", Machine.readFromSpace(f("/usr/marko/a/barrier/c")));
            LOG.warn("  halted: a=%s b=%s c=%s", pa.halted(), pb.halted(), pc.halted());
            throw e;
        }
        assertEquals(jnt(774), result.stream().iterator().next(),
                "A folded its own partition with the partials B and C each reported to the mailbox it waits on");
    }
}
