package studio.phaseshift.metatron.isa.mach.type;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_MACHINE_TID;

/**
 * THE REAL PIPELINE, in-process: parse `*<uri>` the way the console does and apply it, then ask where the thread
 * stands. My earlier test called Machine.dereference directly, which proved the helper but NOT that the language's
 * dereference reaches it -- and the console trace showed it does not.
 */
public class MachineDerefPipelineTest extends AbstractMetatronTest {

    private static Machine newMachineAt(final fURI vid) {
        final boolean booting = studio.phaseshift.metatron.BootLoader.BOOTING;
        studio.phaseshift.metatron.BootLoader.BOOTING = true;
        try {
            return studio.phaseshift.metatron.isa.mach.type.machine.BasicMachine.of(MACH_MACHINE_TID, vid);
        } finally {
            studio.phaseshift.metatron.BootLoader.BOOTING = booting;
        }
    }

    @AfterEach
    public void unwind() {
        while (null != Machine.frame())
            Machine.frame().machine().pop();
        Machine.withPerspective(Machine.jvmRoot());
    }

    private void trace(final String s) {
        try {
            java.nio.file.Files.writeString(java.nio.file.Paths.get(System.getProperty("user.dir"), "target", "deref-pipeline.log"),
                    s + "\n", java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (final Throwable ignore) {
        }
    }

    @Test
    public void testTheLanguageDereferenceMovesTheFrameOfReference() {
        try {
            java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(System.getProperty("user.dir"), "target", "deref-pipeline.log"));
        } catch (final Throwable ignore) {
        }
        final Machine root = Machine.jvmRoot();
        root.removeSpace(f("/mach/derefB"));
        final Machine b = newMachineAt(f("/mach/derefB"));
        root.mount(b);
        trace("before: current=" + Machine.current().vid() + "  parsed form of the expression:");

        final Obj parsed = ObjmtronSerializer.parseMulti("*</mach/derefB>");
        trace("  parsed = " + parsed + "   tid=" + parsed.tid());
        assertEquals(root, Machine.current(), "still root before the dereference");
        b.apply(parsed.asCode(), noobj());   // applying b is where you land
        trace("after: current=" + Machine.current().vid());
        // APPLYING A MACHINE MOVES THE FRAME OF REFERENCE AND LEAVES IT MOVED: `b.apply(...)` is you standing in b.
        // No console hack, no auto_xxx special case -- this is the rule, and the console simply reads it.
        assertEquals(b, Machine.current(), "THE LANGUAGE DEREFERENCE LEAVES THE THREAD IN b");
    }
}
