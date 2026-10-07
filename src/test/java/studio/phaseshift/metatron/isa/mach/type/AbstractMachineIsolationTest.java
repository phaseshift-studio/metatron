package studio.phaseshift.metatron.isa.mach.type;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.util.MTronException;
import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.MUTABLE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;

/**
 * The general form of a Machine ISOLATION guarantee, parameterized over WHICH component is being isolated.
 * <p>
 * Every component of a machine — memory, instset, network, compiler, processor — is reached through the same
 * frame-aware view, so the isolation claim is the same claim each time and this class states it once:
 * <ol>
 *     <li><b>own</b> — a write inside a frame is visible in that frame, and the frame reads through to the parent's</li>
 *     <li><b>no leak up</b> — a parent does NOT see what a child wrote</li>
 *     <li><b>no leak across</b> — a sibling does NOT see what another child wrote</li>
 * </ol>
 * A component becomes an instance by supplying three operations and nothing else: how to write into it, how to read
 * from it (noobj when it does not hold the key), and how to build a distinguishable value. The assertions are
 * component-agnostic on purpose — that is the point of the framework, and it is why a new component needs no new
 * test logic, only a new instance.
 */
public abstract class AbstractMachineIsolationTest extends AbstractMetatronTest {

    @AfterEach
    public void unwind() {
        // a failed assertion must not leave a frame on this thread's stack
        Machine.current(Machine.root());
    }

    /** Write {@code value} into this machine's component under {@code key}. */
    protected abstract void writeInto(Machine machine, String key, Obj value);

    /** Read {@code key} back from this machine's component — noobj when it does not hold it. */
    protected abstract Obj readFrom(Machine machine, String key);

    /** A value distinguishable from the others a test writes, so a leak cannot be mistaken for inheritance. */
    protected abstract Obj value(int n);   // compared with equals(), so any Obj works — not just an int

    @Test
    public void testComponentIsolation() {
        final Machine parent = Machine.defaultMachine();
        writeInto(parent, "outer", value(1));

        // own + inherit: the first frame is a child of the machine, so its view is the machine's over its own
        final Machine child = parent.push();
        try {
            writeInto(child, "inner", value(2));
            assertEquals(value(2), readFrom(child, "inner"), "a frame sees its own write");
            assertEquals(value(1), readFrom(child, "outer"), "and inherits the parent's");
        } finally {
            child.pop();
        }

        // no leak up: the write the child made must be gone, and the parent's own must have survived
        assertTrue(readFrom(parent, "inner").isNoObj(), "the child's write must not leak up to the parent");
        assertEquals(value(1), readFrom(parent, "outer"), "and the parent's own write survives the pop");

        // no leak across: a sibling is a SECOND frame off the same parent, so it inherits the parent and nothing else
        final Machine sibling = parent.push();
        try {
            assertTrue(readFrom(sibling, "inner").isNoObj(), "a sibling must not see the other child's write");
            assertEquals(value(1), readFrom(sibling, "outer"), "but does inherit the parent's");
        } finally {
            sibling.pop();
        }
    }
}
