package studio.phaseshift.metatron.isa.mach.type;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.space.memSpace;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        // findSpace (not hasSpaceFor): the root's # catch-all covers everything, so the leak check is "does the
        // parent resolve /mem/inner to the child's own space" — it must not.
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
