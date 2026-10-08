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
 * The general form of SPACE MIRRORING, parameterized over WHICH Space is mounted.
 * <p>
 * A Space that declares a {@code PATTERN} claims a jurisdiction. Mount that space on a machine and it answers for
 * that jurisdiction FROM THERE — which is the mirror. The root is not special in this: it is simply the machine
 * with nothing above it, so this is the same four assertions as every other machine guarantee, one level up:
 * <ol>
 *     <li><b>shadow</b> — a space mounted in a FRAME claims the same pattern and answers there, shadowing the
 *     enclosing machine's space for that pattern;</li>
 *     <li><b>inherit</b> — where no inner space claims the address, the read falls through to the enclosing space;</li>
 *     <li><b>no leak up</b> — the enclosing machine still reads its OWN space;</li>
 *     <li><b>no leak across</b> — a sibling frame reads the enclosing machine's space and never the other
 *     sibling's.</li>
 * </ol>
 * An instance supplies two operations and a value: how to mount a space claiming a pattern on a machine, how to
 * read an address through that machine's frame of reference, and a distinguishable value. The assertions are
 * Space-agnostic on purpose — that is the point of the generalization.
 */
public abstract class AbstractMachineSpaceMirrorTest extends AbstractMetatronTest {

    @AfterEach
    public void unwind() {
        Machine.current(Machine.current());
    }

    /**
     * Mount on {@code machine} a space that claims {@code pattern} and holds {@code address => marker}.
     */
    protected abstract void mountInto(Machine machine, String pattern, String address, Obj marker);

    /**
     * Read {@code address} through this machine's frame of reference — noobj when nothing covers it.
     */
    protected abstract Obj readThrough(Machine machine, String address);

    /**
     * A value distinguishable from the others, so a shadow cannot be mistaken for inheritance.
     */
    protected abstract Obj value(int n);

    @Test
    public void testAMirroredSpaceShadowsAndStaysLocal() {
        final Machine parent = Machine.defaultMachine();
        mountInto(parent, "/mirror/#", "/mirror/x", value(1));
        assertEquals(value(1), readThrough(parent, "/mirror/x"), "the machine reads its own mounted space");

        // shadow: a space mounted inside a FRAME claims the same pattern and answers there
        final Machine child = parent.push();
        try {
            mountInto(child, "/mirror/#", "/mirror/x", value(2));
            assertEquals(value(2), readThrough(child, "/mirror/x"),
                    "the frame's own space SHADOWS the enclosing machine's for the same pattern");
        } finally {
            child.pop();
        }
        // Only NOW can the enclosing machine be read as itself: reading it from inside the child's frame would be a
        // read at that address FROM THE CHILD (own-first resolution), which answers with the child's space by design.
        // "The parent still reads its own" is a statement about a context the child's frame is not live in.
        assertEquals(value(1), readThrough(parent, "/mirror/x"),
                "once the child's frame is gone, the enclosing machine reads its own again");

        // no leak across: a sibling reads the machine's space, never the other child's
        final Machine sibling = parent.push();
        try {
            assertEquals(value(1), readThrough(sibling, "/mirror/x"),
                    "a sibling must not see the other child's mirrored space");   // read THROUGH the sibling

        } finally {
            sibling.pop();
        }

        assertEquals(value(1), readThrough(parent, "/mirror/x"), "the machine's space survives the pops");
    }
}
