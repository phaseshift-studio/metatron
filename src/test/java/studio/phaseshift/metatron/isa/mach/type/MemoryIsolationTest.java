package studio.phaseshift.metatron.isa.mach.type;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.space.variableStack;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;

/**
 * Memory isolation over SPACES — the atomic unit of access.
 * <p>
 * A space is created with {@code memSpace.of(pattern, vid)} and mounted with {@code memory().addSpace(...)}. A child
 * frame's memory is a {@code MemoryUnion}: {@code findSpace} answers own-first then the enclosing level, and
 * {@code addSpace} never evicts a parent space of the same pattern — it shadows it. The four assertions are proven
 * on the space operations themselves, not on the relative arg-stack bindings.
 */
public class MemoryIsolationTest extends AbstractMetatronTest {

    @AfterEach
    public void unwind() {
        Machine.current(Machine.current());
    }

    private static Space space(final String pattern) {
        return memSpace.of(f(pattern), f(pattern));
    }

    @Test
    public void testOwnAndInherit() {
        final Machine parent = Machine.defaultMachine();
        final Space outer = space("/mem/outer");
        parent.memory().addSpace(outer);

        final Machine child = parent.push();
        try {
            assertEquals(outer, child.memory().findSpace(f("/mem/outer")), "the child inherits the parent's space");

            final Space inner = space("/mem/inner");
            child.memory().addSpace(inner);
            assertEquals(inner, child.memory().findSpace(f("/mem/inner")), "the child sees its own space");
        } finally {
            child.pop();
        }
    }

    @Test
    public void testNoLeakUpAndNoLeakAcross() {
        final Machine parent = Machine.defaultMachine();
        final Space outer = space("/mem/outer");
        parent.memory().addSpace(outer);

        final Machine child = parent.push();
        try {
            // constructed inside the frame, so the space self-registers into the CHILD's memory
            child.memory().addSpace(space("/mem/inner"));
        } finally {
            child.pop();
        }
        // the child's /mem/inner must not leak up: nothing may answer with the /mem/inner pattern (the root's own
        // infra space, /#, is the catch-all that would otherwise serve the vid — not the child's space).
        assertNotEquals(f("/mem/inner"), parent.memory().findSpace(f("/mem/inner")).pattern(),
                "the child's space must not leak up to the parent");
        assertEquals(outer, parent.memory().findSpace(f("/mem/outer")), "and the parent keeps its own space");

        final Machine sibling = parent.push();
        try {
            assertNotEquals(f("/mem/inner"), sibling.memory().findSpace(f("/mem/inner")).pattern(),
                    "a sibling must not see the other child's space");
            assertEquals(outer, sibling.memory().findSpace(f("/mem/outer")), "but does inherit the parent's space");
        } finally {
            sibling.pop();
        }
    }

    @Test
    public void testFrameMemoryIsOwnedByFrameMachine() {
        final Machine parent = Machine.defaultMachine();
        final Machine child = parent.push();
        try {
            // The frame's memory is the frame machine's own component: machine() walks parent() and must land on
            // the child, not mach0(). This is what lets getSpaceFor/readAbsolute resolve /memory, /processor and
            // /network from the frame's own rec rather than the root's.
            assertEquals(child, child.memory().machine(),
                    "the frame's memory is owned by the frame's machine");
            assertEquals(child, Machine.current().memory().machine(),
                    "the current perspective's memory resolves the same frame machine");
        } finally {
            child.pop();
        }
        assertEquals(parent, parent.memory().machine(),
                "the root's memory is owned by the root machine");
    }

    @Test
    public void testArgStackIsNotAMachineSpace() {
        final Machine parent = Machine.defaultMachine();
        final Machine child = parent.push();
        try {
            // The arg stack is the thread's local variables, reached via Memory.argStack(), not a machine space.
            // Constructing one while a child is current must not put a +/# into the child's index — its +/# would
            // match /sys/log and /m/inst and shadow the parent's real spaces.
            new variableStack(f("+/#"));

            final boolean stackSpaceInIndex = child.memory().spaces().jvm().values().stream()
                    .map(o -> (Space) o)
                    .anyMatch(s -> f("+/#").equals(s.pattern()));
            assertFalse(stackSpaceInIndex, "the arg stack must not register +/# into the frame's space index");
        } finally {
            child.pop();
        }
    }

    @Test
    public void testTildeResolvesToCurrentMachine() {
        final Machine parent = Machine.defaultMachine();
        final Machine child = parent.push();
        try {
            // ~ resolves to the current machine's vid (~ → /xyz → the machine rec), before the relative/absolute split.
            assertEquals(child, Machine.read(f("~")), "~ resolves to the current machine");
        } finally {
            child.pop();
        }
        assertEquals(parent, Machine.read(f("~")), "and ~ resolves to the root once the frame is popped");
    }

    @Test
    public void testRoutesInheritedAcrossPush() {
        final Machine child = Machine.defaultMachine().push();
        try {
            // a short instruction name (count) is routed small→big by the PARENT's tables; the union must fall
            // through to previous when current has no route, else instruction resolution dies after push.
            final fURI count = Machine.current().memory().redirect(f("count"), true);
            assertFalse(count.equals(f("count")), "count redirects to /m/inst/count through the parent's routes");
            // and the full read path: *count dereferences the relative name via big() → /m/inst/count, not a throw.
            final Obj deref = ObjmtronSerializer.parse("*count").apply();
            assertFalse(deref.isFail(), "*count resolves through the parent's routes after push");
            assertFalse(deref.isNoObj(), "*count yields the count instruction");
        } finally {
            child.pop();
        }
    }

    @Test
    public void testThreadCountRootVsPushedChild() {
        // at root: /thread and ~/thread name the same set — no machine pushed, current is the root.
        final long root = evalCount("*/thread/+.count()");
        final long rootViaTilde = evalCount("*~/thread/+.count()");
        assertEquals(root, rootViaTilde, "/thread and ~/thread are the same set at the root");

        final Machine child = Machine.defaultMachine().push();
        try {
            // after push, ~/thread is the child's own (fresh) set; /thread is still the root's.
            final long rootAfterPush = evalCount("*/thread/+.count()");
            final long childThreads = evalCount("*~/thread/+.count()");
            assertTrue(rootAfterPush > childThreads,
                    "root threads (%d) should exceed the pushed child's (%d)".formatted(rootAfterPush, childThreads));
        } finally {
            child.pop();
        }
    }

    private static long evalCount(final String code) {
        final Obj result = ObjmtronSerializer.parse(code).apply();
        assertFalse(result.isFail(), "should not have failed: " + code);
        return result.asInt().jvm();
    }

    @Test
    public void testShadowingDoesNotEvict() {
        final Machine parent = Machine.defaultMachine();
        final Space outer = space("/mem/outer");
        parent.memory().addSpace(outer);

        final Machine child = parent.push();
        try {
            final Space shadow = memSpace.of(f("/mem/outer"), f("/mem/outer/child"));
            child.memory().addSpace(shadow);
            assertEquals(shadow, child.memory().findSpace(f("/mem/outer")), "the child's space shadows the parent's");
        } finally {
            child.pop();
        }
        assertEquals(outer, parent.memory().findSpace(f("/mem/outer")), "the parent still reads its own — shadowing never evicts");
    }
}
