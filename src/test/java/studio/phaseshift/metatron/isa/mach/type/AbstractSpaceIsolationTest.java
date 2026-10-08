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

import studio.phaseshift.metatron.isa.Space;

import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;

import java.util.concurrent.atomic.AtomicReference;

/**
 * SPACE ISOLATION AND COORDINATE SEMANTICS, proven once for any Space a machine or frame presents.
 * <p>
 * The operations are the space API itself — {@code write(fURI, Obj)} and {@code read(fURI)} — because everything a
 * machine presents IS a Space: its memory, its instruction set, a space mounted at a frame's level. So the properties
 * are asserted ONCE here rather than per component, and an instance supplies only:
 * <ul>
 *     <li>{@link #view(Machine)} — the Space in effect at this machine (frame-aware: the frame's own level when one
 *     is live);</li>
 *     <li>{@link #key(String)} — an address IN THAT SPACE's jurisdiction. Not a workaround: a memory holds relative
 *     names (the {@code +/#} catch-all) while an ISA holds {@code /m/...}, and the jurisdiction each one claims is
 *     exactly its {@code pattern}. One API, per-space addressing.</li>
 *     <li>{@link #value(int)} — something distinguishable to introduce.</li>
 * </ul>
 * Four isolation assertions (own, inherit, no-leak-up, no-leak-across) plus the coordinate semantics that belong to
 * every frame of reference: {@code .} naming the frame's interior, the audience widening with the mount level, and a
 * sibling THREAD seeing the machine's level but never the frame's.
 */
public abstract class AbstractSpaceIsolationTest extends AbstractMetatronTest {

    @AfterEach
    public void unwind() {
        Machine.current(Machine.current());
    }

    /**
     * The Space in effect at this machine — frame-aware, so a live frame's own level answers.
     */
    protected abstract Space view(Machine machine);

    /**
     * An address in this Space's jurisdiction (relative for a memory, absolute for an ISA).
     */
    protected abstract fURI key(String name);

    /**
     * A value distinguishable from the others, so a shadow cannot be mistaken for inheritance.
     */
    protected abstract Obj value(int n);

    // ------------------------------------------------------------------ the four isolation assertions

    @Test
    public void testOwnAndInherit() {
        final Machine machine = Machine.defaultMachine();
        writeInto(machine, "outer", value(1));
        assertEquals(value(1), readFrom(machine, "outer"), "a write is visible where it was made");

        final Machine child = machine.push();
        try {
            writeInto(child, "inner", value(2));
            assertEquals(value(2), readFrom(child, "inner"), "THE FRAME sees its own write");
            assertEquals(value(1), readFrom(child, "outer"), "AND inherits the enclosing level's");
        } finally {
            child.pop();
        }
    }

    @Test
    public void testNoLeakUpAndNoLeakAcross() {
        final Machine machine = Machine.defaultMachine();
        writeInto(machine, "outer", value(1));

        final Machine child = machine.push();
        try {
            writeInto(child, "inner", value(2));
        } finally {
            child.pop();
        }
        // asserted OUTSIDE the child's frame: reading it from inside would be a read FROM the child (own-first)
        assertEquals(noobj(), readFrom(machine, "inner"), "the child's write must not leak up to the enclosing level");
        assertEquals(value(1), readFrom(machine, "outer"), "and the enclosing level keeps its own");

        final Machine sibling = machine.push();
        try {
            assertEquals(noobj(), readFrom(sibling, "inner"), "a sibling must not see the other child's write");
            assertEquals(value(1), readFrom(sibling, "outer"), "but does inherit the enclosing level's");
        } finally {
            sibling.pop();
        }
    }

    // ------------------------------------------------------------------ coordinate semantics

    /**
     * The frame's IDENTITY: {@code .} names where you stand. Inside a frame an unqualified {@code .} must resolve to
     * that frame's machine, not to the enclosing machine — this is what makes a frame of reference a place rather
     * than a name, and it is the property the perspective model rests on.
     */
    @Test
    public void testHereIsTheFrameYouStandIn() {
        final Machine machine = Machine.defaultMachine();
        final Machine child = machine.push();
        try {
            // `.` is a MACHINE notion, not a component address: it is resolved by hereVID(), which asks where this
            // thread stands. Asking a component view for it is a category error (a memory's `.` and an ISA's
            // `/m/.` are not the same question), so this one assertion reads through the machine itself -- valid
            // for every instance of this base.
            final Obj here = child.memory().read(f("."));
            assertEquals(false, here.isNoObj(), "`.` resolves inside a frame");
            assertEquals(child.vid(), here.vid(), "and it names THIS frame's machine, not the enclosing one");
        } finally {
            child.pop();
        }
    }

    /**
     * The AUDIENCE widens with the mount level, and the walk is the machine chain: a grandchild must see what its
     * own level holds, then its parent's, then the machine's — in that order of precedence.
     */
    @Test
    public void testAudienceAcrossDepth() {
        final Machine machine = Machine.defaultMachine();
        writeInto(machine, "deep", value(1));

        final Machine child = machine.push();
        try {
            writeInto(child, "deep", value(2));
            final Machine grandchild = child.push();
            try {
                writeInto(grandchild, "deep", value(3));
                assertEquals(value(3), readFrom(grandchild, "deep"), "the innermost level wins");
            } finally {
                grandchild.pop();
            }
            assertEquals(value(2), readFrom(child, "deep"), "one level out, the child's own wins again");
        } finally {
            child.pop();
        }
        assertEquals(value(1), readFrom(machine, "deep"), "and the machine keeps its own");
    }

    /**
     * The THREAD axis — orthogonal to the frame axis, and the one the monad work will lean on hardest. A frame is
     * per-thread state, so a second thread sees the MACHINE's level and never the frame's.
     */
    @Test
    public void testAFrameIsInvisibleToAnotherThread() throws Exception {
        final Machine machine = Machine.defaultMachine();
        writeInto(machine, "shared", value(1));

        final Machine child = machine.push();
        try {
            writeInto(child, "private", value(2));
            assertEquals(value(2), readFrom(child, "private"), "this thread's frame sees its own write");

            final AtomicReference<Obj> otherThreadPrivate = new AtomicReference<>();
            final AtomicReference<Obj> otherThreadShared = new AtomicReference<>();
            final Thread other = new Thread(() -> {
                otherThreadPrivate.set(readFrom(machine, "private"));
                otherThreadShared.set(readFrom(machine, "shared"));
            });
            other.start();
            other.join();

            assertEquals(noobj(), otherThreadPrivate.get(),
                    "another thread must NOT see this thread's frame — frames are per-thread");
            assertEquals(value(1), otherThreadShared.get(),
                    "but it does see the machine's own level, which is shared");
        } finally {
            child.pop();
        }
    }

    // ------------------------------------------------------------------ helpers

    protected void writeInto(final Machine machine, final String name, final Obj value) {
        this.view(machine).write(this.key(name), value);
    }

    protected Obj readFrom(final Machine machine, final String name) {
        return this.view(machine).read(this.key(name));
    }
}
