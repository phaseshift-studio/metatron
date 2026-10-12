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
 *     <li>{@link #key(String)} — an address IN THAT SPACE's jurisdiction, spelled so the FRAME owns it: an ISA holds
 *     {@code /m/...}, and a frame's own interior is reached through the frame base ({@code ~/...}). The jurisdiction a
 *     space claims is exactly its {@code pattern}; one API, per-space addressing. See {@link #key(String)} for the
 *     binding-versus-address trap and for what is still missing on the memory side.</li>
 *     <li>{@link #value(int)} — something distinguishable to introduce.</li>
 * </ul>
 * Four isolation assertions (own, inherit, no-leak-up, no-leak-across) plus the coordinate semantics that belong to
 * every frame of reference: {@code ~} naming the frame you stand in, the audience widening with the mount level, and a
 * component shared by every thread evaluating in the same machine (a component belongs to the MACHINE, not the
 * thread; only the {@code variableStack} is per-thread).
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
     * An address in this Space's jurisdiction — and, the part that is easy to get wrong, an address the FRAME can
     * actually own. A bare relative name is a BINDING, not an address: {@code Memory.write} routes it to the argument
     * stack, which is thread-local and is NOT inherited across a push, so a memory instance keyed by a plain name
     * fails inheritance for reasons that have nothing to do with memory. Spell it with the frame base
     * ({@code ~/name}) so the address names THIS frame's interior rather than a binding — though reaching the frame's
     * own interior is not the same as INHERITING the enclosing one; see the measurement below.
     * <p>
     * Measured with the frame base (a throwaway memory instance: {@code view = memory()}, {@code key = ~/name}): own,
     * the frame-identity assertion, the depth walk, thread-invisibility and no-leak-up all hold — but INHERITANCE does
     * not, because {@code ~} resolves once to {@code /thisFrame/name} and a miss at that absolute address never
     * re-bases to the enclosing frame's {@code /name}. Making a frame-relative address re-base on a miss (the frame
     * supplies the base, then walks outward "till it finds it or noobjs") is the frame's-own-interior work
     * (t-murdnd4i). Until that lands, an ISA instance fits this harness — an ISA's jurisdiction is absolute,
     * {@code /m/...} — while memory's frame isolation stays proven the mounted-space way, in MemoryIsolationTest.
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
     * The frame's IDENTITY: {@code ~} names where you stand. Inside a frame {@code ~} must resolve to that frame's
     * machine, not to the enclosing machine — this is what makes a frame of reference a place rather than a name,
     * and it is the property the perspective model rests on.
     * <p>
     * {@code ~} is the frame base and the dot is not (Marko, 2026-10-12: "<b>~ is the new .</b>"): the dot goes back
     * to being the zero displacement — a path's identity, which {@code resolve()} drops — while {@code ~} is
     * resolved against {@code Machine.current().vid()} before the relative/absolute split (Memory.read,
     * BasicMemory.read, MemoryUnion.read/write).
     */
    @Test
    public void testHereIsTheFrameYouStandIn() {
        final Machine machine = Machine.defaultMachine();
        final Machine child = machine.push();
        try {
            // `~` is a MACHINE notion, not a component address: Memory resolves it against Machine.current().vid()
            // BEFORE the relative/absolute split, so it asks where this THREAD stands. A memory's `~` and an ISA's
            // `~` are the same question — unlike a per-space address — which is why this assertion reads through the
            // machine's own memory and is valid for every instance of this base.
            final Obj here = child.memory().read(f("~"));
            assertEquals(false, here.isNoObj(), "`~` resolves inside a frame");
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
     * The THREAD axis. A component belongs to its MACHINE, not to the thread: every thread evaluating in a machine
     * sees the same {@code instset()}, {@code network()}, {@code compiler()}, {@code processor()} and {@code memory()}
     * (Marko, 2026-10-12 — "the machine is the object frame of reference"), and those are not necessarily the same as
     * the machine's parents' or children's. So the component assertion is the CONVERSE of the one a per-thread store
     * would make: a write made in one thread IS visible to another thread evaluating in the same machine.
     * <p>
     * What is per-thread is the {@code variableStack} — a separate concern, and the reason this base no longer
     * asserts per-thread privacy: that property belongs to the stack (whose BASE FRAME is nonetheless the machine's)
     * and asserting it here would attribute it to the component.
     */
    @Test
    public void testAComponentIsSharedByThreadsOfTheSameMachine() throws Exception {
        final Machine machine = Machine.defaultMachine();
        final Machine child = machine.push();
        try {
            writeInto(child, "shared", value(1));
            assertEquals(value(1), readFrom(child, "shared"), "this thread sees its own write");

            final AtomicReference<Obj> fromOtherThread = new AtomicReference<>();
            final Thread other = new Thread(() -> fromOtherThread.set(
                    Machine.current(child, () -> readFrom(child, "shared"))));
            other.start();
            other.join();

            assertEquals(value(1), fromOtherThread.get(),
                    "another thread evaluating in the SAME machine must see it — a component belongs to the machine");
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
