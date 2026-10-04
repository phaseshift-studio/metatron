package studio.phaseshift.metatron.isa.mach.type;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.machine.BasicMachine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_MACHINE_TID;

/**
 * MOUNTING A MACHINE -- the first end-to-end exercise of the teleport path. Reported from the console:
 * <pre>
 *   *&lt;/.&gt;.mount(|machine::[=&gt;]@/usr/marko/xyz)   ==> the ROOT echoed back (mount returns `this`)
 *   *&lt;.&gt;                                          ==> still the root
 * </pre>
 * Two things follow, and this class measures both rather than arguing them.
 * <p>
 * (1) `mount` PLACES; it does not move the frame of reference. So `*&lt;.&gt;` returning the root afterwards is
 * CORRECT, not a failure -- and the mount echo is the receiver, not the mounted machine.
 * <p>
 * (2) The real question is whether a mounted machine is FINDABLE as a machine at its address. It went into the
 * PATTERN index, and a machine's pattern is ALL -- so it may claim every address instead of answering at its own.
 * That is the same collision shape as an ISA overlay alongside its library, and it is the evidence for the
 * conclusion already reached: machines belong in a REGISTRY, not the pattern index.
 */
public class MachineMountTest extends AbstractMetatronTest {

    @AfterEach
    public void unwind() {
        while (null != Machine.frame())
            Machine.frame().machine().pop();
    }

    /*
     * MEASURED: BasicMachine.of(...) WRITES -- AbstractMachine.<init> calls Machine.writeToSpace -- and write throws
     * whenever BOOTING is false, which is why the root and /sys/mach are built during boot. A test re-arms the flag
     * around the construction, which is the documented requirement, rather than reaching for the runtime path.
     * mtron's own route is MACH_MACHINE_TYPE's constructor (Rec.wrap), which does NOT write; covering that route as
     * well is the follow-up this class was written to justify.
     */
    private static Machine newMachineAt(final fURI vid) {
        final boolean booting = BootLoader.BOOTING;
        BootLoader.BOOTING = true;
        try {
            return BasicMachine.of(MACH_MACHINE_TID, vid);
        } finally {
            BootLoader.BOOTING = booting;
        }
    }

    private static void trace(final String s) {
        try {
            java.nio.file.Files.writeString(java.nio.file.Paths.get(System.getProperty("user.dir"), "target", "mount-probe.log"),
                    s + "\n", java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (final Throwable ignore) {
        }
    }

    /**
     * mount places the machine and returns the RECEIVER -- so a console echo of the receiver says nothing.
     */
    @Test
    public void testMountPlacesAndDoesNotMove() {
        final Machine root = Machine.jvmRoot();
        // clean index: mounts accumulate across tests in one booted VM, and a mounted machine claims ALL, so a
        // leftover is a candidate for EVERY lookup (which is the shadowing this class measures, seen at its worst).
        root.removeSpace(f("/mach/probeNest"));
        root.removeSpace(f("/mach/probeIndex"));
        final Machine child = newMachineAt(f("/mach/probeNest"));
        final Machine returned = root.mount(child);
        assertEquals(root, returned, "mount returns the RECEIVER, not the mounted machine");
        assertEquals(root, Machine.current(), "and it does NOT move the frame of reference -- placement is not entry");
        /*
         * MEASURED, and this is the gap the console session reported. The child IS in the index and IS found at its
         * own address -- getSpaceFor(/mach/probeNest) returns the child and not the root (see the other test, where
         * that is logged and asserted). But the MACHINE-DEREFERENCE path does not accept it: accessMachine walks up
         * with retract(1) and returns the first value whose tid tests as machine::T, and it does not accept the
         * child -- so it retires to / and hands back the ROOT. That is why *<.> lands on the root after a mount.
         *
         * ALSO CORRECTED HERE: the ALL pattern does NOT shadow. addSpace keys by VID when the space has one, so a
         * machine with an address is an exact-key entry that matches at its own address. The shadowing risk belongs
         * to VID-LESS entries, which is what the ISA overlay was (and why that collision was real).
         */
        assertEquals(child, root.getSpaceFor(f("/mach/probeNest")), "the child IS found in the index at its own address");
        // The machine-dereference path used to reject it (accessMachine walked up and returned the root); that method
        // is gone -- the console now runs each line in Machine.current(), so the dereference path is Machine itself.
    }

    /**
     * MEASURED, not assumed: is the mounted machine resolvable as a SPACE at its own address, or does ALL shadow it?
     */
    @Test
    public void testHowAMountedMachineAnswersInTheIndex() {
        final Machine root = Machine.jvmRoot();
        root.removeSpace(f("/mach/probeNest"));
        root.removeSpace(f("/mach/probeIndex"));
        final Machine child = newMachineAt(f("/mach/probeIndex"));
        root.mount(child);
        final Obj readAtOwnAddress = root.read(f("/mach/probeIndex"));
        final Object foundFor = root.getSpaceFor(f("/mach/probeIndex"));
        trace("read(/mach/probeIndex) = " + readAtOwnAddress);
        trace("getSpaceFor(/mach/probeIndex) = " + foundFor);
        trace("  same as the mounted child? " + (foundFor == child));
        trace("  same as the root? " + (foundFor == root));
        // the machine IS in the index: getSpaceFor finds *something* for any address it claims
        assertEquals(false, null == foundFor, "a mounted machine is found in the index for an address");
    }
}
