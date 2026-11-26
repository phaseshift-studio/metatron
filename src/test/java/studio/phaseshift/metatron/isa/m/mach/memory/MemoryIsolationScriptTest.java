/*
 * metatron: a distributed virtual machine and language
 *  Copyright (C) 2025- PhaseShift Studio, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.isa.m.mach.memory;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.StatefulCSVSource;
import studio.phaseshift.metatron.StatefulParametrizedTest;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;

/**
 * MEMORY isolation as a stateful script — mutation and its observation are one line each, in order. The
 * {@code [STATE]} lines push/pop mid-script, which a plain {@code @CsvSource} cannot express.
 * <p>
 * This is the MEMORY half of the machine-isolation pair; {@code NetworkIsolationScriptTest} is the network half,
 * deliberately in the same script form so the two read side by side.
 * <p>
 * The invariants proven here, each as a script of mtron observations:
 * <ol>
 *     <li><b>thread count</b> — {@code /thread} is the root's own set, {@code ~/thread} is the current machine's.</li>
 *     <li><b>tilde tracks the machine</b> — {@code ~} and {@code /.} name a {@code machine::T} wherever you stand.</li>
 *     <li><b>write isolation</b> — a child's write is its own; it does not leak up or across.</li>
 *     <li><b>executor is global</b> — one funnel at {@code /sys/thread/executor}, not a per-machine component.</li>
 *     <li><b>the nest is a stack</b> — a name is bound in the machine that bound it, so each level reads its own.</li>
 *     <li><b>space mounts</b> — a space mounted in a child keeps its type when read back at its mount address, and its
 *         data is visible to its own machine and every descendant, never to a parent or a sibling.</li>
 * </ol>
 */
public class MemoryIsolationScriptTest extends AbstractMetatronTest {

    @StatefulParametrizedTest
    @StatefulCSVSource({
            // at root: /thread and ~/thread name the same set — no machine pushed, current is the root
            "*/sys/thread/+.count() % *~/sys/thread/+.count()        % true",
            // push a child: /thread is still the root's own set; ~/thread is now the child's own (empty)
            "[STATE] *~.push(xyz)",
            "*/sys/thread/+.count().gt(*~/sys/thread/+.count())      % true",
            "*/xyz/sys/thread/+.count() % *~/sys/thread/+.count()    % true",
            // pop back to root: the two agree again
            "[STATE] *~.pop(_)",
            "*/sys/thread/+.count() % *~/sys/thread/+.count()        % true"
    })
    void testThreadCountAcrossPushPop() {
    }

    @StatefulParametrizedTest
    @StatefulCSVSource({
            // ~ and /. both dereference to a machine::T
            "*~.tid().eq(/m/mach/machine)                    % true",
            "*</.>.tid().eq(/m/mach/machine)                 % true",
            // inside a child, ~ still names a machine (the child itself)
            "[STATE] *~.push(xyz)",
            "*~.eq(*/xyz)                                    % true",
            "*</.>.eq(*/xyz)                                 % false",
            "*~.tid().eq(/m/mach/machine)                    % true",
            "*~.pop(_).*<~>.eq(*</.>)                        % true"
    })
    void testTildeTracksTheCurrentMachine() {
    }

    @StatefulParametrizedTest
    @StatefulCSVSource({
            // a write into the child's own namespace lands under /xyz, not /
            "[STATE] *~.push(xyz)",
            "[STATE] ~/iso -> 1",
            "*/xyz/iso % *~/iso          % true",
            "*/xyz/iso % 1               % true",
            // after pop, the child's write is gone from the root's view — no leak up
            "[STATE] *~.pop(_)",
            "*/xyz/iso % noobj           % true"
    })
    void testWriteIsolationNoLeakUp() {
    }

    @StatefulParametrizedTest
    @StatefulCSVSource({
            // child a writes into its own namespace
            "[STATE] *~.push(a)",
            "[STATE] ~/iso -> 1",
            "*/a/iso                   % 1 % true",
            "[STATE] *~.pop(_)",
            // a sibling b does not see a's write — no leak across
            "[STATE] *~.push(b)",
            "*/b/iso % noobj           % true",
            "[STATE] *~.pop(_)"
    })
    void testWriteIsolationNoLeakAcross() {
    }

    @StatefulParametrizedTest
    @StatefulCSVSource({
            // the executor is a single global rec, not a per-machine component
            "*/sys/thread/executor % noobj % false"
    })
    void testThreadExecutorIsGlobal() {
    }

    @StatefulParametrizedTest
    @StatefulCSVSource({
            // at root: the short name and the absolute path dereference to the SAME instruction
            "*count % */m/inst/count",
            // inside a child: the short name still resolves to that SAME instruction via the union's routing tables
            "[STATE] *~.push(xyz)",
            "*count                                  % */m/inst/count",
            "[STATE] inst(a=>int::T){ + *a}@blah",
            "1.blah(2)                               % 3",
            "[STATE] *~.pop(_)",
            "1.blah(2)                               % <ERROR>",
            "[STATE] *~.push(xyz)",
            "1.blah(2)                               % <ERROR>",
            "[STATE] inst(a=>int::T){ + *a}@blah",
            "10.blah(22)                             % 32",
            "[STATE] move(/.)",
            "1.blah(2)                               % <ERROR>",
            "[STATE] move(/xyz)",
            "2.blah(7)                               % 9",
    })
    void testInstructionResolutionAcrossPush() {
    }

    @StatefulParametrizedTest
    @StatefulCSVSource({
            // `~` is the frame base and `/.` is the root (Marko: "~ is the new ."), so these pairs assert that the
            // two spellings denote the same place AT THE ROOT — where they coincide by construction. Inside a frame
            // they must DIVERGE, and the `% false` rows in the child block below pin that: a component belongs to the
            // machine that holds it, so BOTH the accessor and an address read of the slot answer with that machine's
            // component — the frame's, not the root's. MachineInstSetIsolationTest proves the same property at the
            // component level (no leak up, no leak across).
            "*</.>     % *<~>                 % true",
            "*~/memory % *</./memory> % true",
            "*~/instset % *</./instset> % true",
            "*~/network % *</./network> % true",
            "*~/compiler % *</./compiler> % true",
            "*~/processor % *</./processor> % true",
            // the machine's five components are all reachable through ~ (the machine's own slots)
            "*~/memory % noobj % false",
            "*~/instset % noobj % false",
            "*~/network % noobj % false",
            "*~/compiler % noobj % false",
            "*~/processor % noobj % false",
            // a child has its own components, reachable through the same ~
            "[STATE] *~.push(xyz)",
            "*~/memory % *</xyz/memory> % true",
            "*~/instset % *</xyz/instset> % true",
            "*~/network % *</xyz/network> % true",
            "*~/compiler % *</xyz/compiler> % true",
            "*~/processor % *</xyz/processor> % true",
            // THE FRAME'S COMPONENT IS NOT THE ROOT'S. A slot read is per-machine now rather than an alias of the
            // parent's — for the components a machine actually mints: memory, the instruction set, and the processor,
            // which indexes the machine's computers and holds its fail stacks. The other two are still the enclosing
            // machine's INSTANCE: the network's roster is shared by design today (the open shadow-and-deny question),
            // and the compiler is a stateless axis whose accessor clones per fetch, so a row for it would assert the
            // aliasing rather than the ownership.
            "*~/memory % *</memory> % false",
            "*</xyz/memory> % *</memory> % false",
            "*~/instset % *</instset> % false",
            "*</xyz/instset> % *</instset> % false",
            "*~/processor % *</processor> % false",
            "*</xyz/processor> % *</processor> % false",
            "*</.>     % *<~>                 % false",
            "[STATE] move(/xyz)",
            "~/memory() % /memory() % false",
            "*~/memory % *</memory> % false",
            "*~/instset % *</instset> % false",
            "*~/network % *</network> % false",
            "*~/compiler % *</compiler> % true", // compiler propagates through
            "*~/processor % *</processor> % false",
            "*~/memory % *</./memory> % false",
            "*~/instset % *</./instset> % false",
            "*~/network % *</./network> % false",
            "*~/compiler % *</./compiler> % true", // compiler propagates through
            "*~/processor % *</./processor> % false",
            "*~/memory % noobj % false",
            "*~/instset % noobj % false",
            "*~/network % noobj % false",
            "*~/compiler % noobj % false",
            "*~/processor % noobj % false",
            "[STATE] *~.pop(_)"
    })
    void testComponentIsolation() {
    }

    @StatefulParametrizedTest
    @StatefulCSVSource({
            // child 1 writes into its own namespace
            "[STATE] *~.push(xyz1)",
            "[STATE] ~/iso -> 1",
            "*/xyz1/iso % 1 % true",
            // move back to the root and push a sibling — xyz1 stays alive (move, not pop)
            "[STATE] move(/.)",
            "[STATE] *~.push(xyz2)",
            "*/xyz2/iso % noobj % true",
            // move between the siblings: each sees only its own state
            "[STATE] move(/xyz1)",
            "*/xyz1/iso % 1 % true",
            "[STATE] move(/xyz2)",
            "*/xyz2/iso % noobj % true",
            // and the root sees neither child's write
            "[STATE] move(/.)",
            "*/xyz1/iso % noobj % true"
    })
    void testSiblingIsolationAcrossMove() {
    }

    @StatefulParametrizedTest
    @StatefulCSVSource({
            // build a 7-machine hierarchy — root → {a→{a1,a2}, b→{b1}, c} — each machine stamps its root frame with `who`
            "[STATE] ~/sys/who -> 0; who -> 0;",
            "[STATE] *~.push(a)", "[STATE] ~/sys/who -> 1; who -> 11",
            "[STATE] *~.push(a1)", "[STATE] ~/sys/who -> 2; who -> 22",
            "[STATE] move(/a)",
            "[STATE] *~.push(a2)", "[STATE] ~/sys/who -> 3; who -> 33",
            "[STATE] move(/.)",
            "[STATE] *~.push(b)", "[STATE] ~/sys/who -> 4; who -> 44",
            "[STATE] *~.push(b1)", "[STATE] ~/sys/who -> 5; who -> 55",
            "[STATE] move(/.)",
            "[STATE] *~.push(c)", "[STATE] ~/sys/who -> 6; who -> 66",
            "[STATE] move(/.)",
            // downward: root → a → a1
            "[STATE] move(/a)", "*~/sys/who % 1 % true", "*who % 11 % true",
            "[STATE] move(/a/a1)", "*~/sys/who % 2 % true", "*who % 22 % true",
            // horizontal (sibling): a1 → a2
            "[STATE] move(/a/a2)", "*~/sys/who % 3 % true", "*who % 33 % true",
            // upward: a2 → a → root
            "[STATE] move(/a)", "*~/sys/who % 1 % true", "*who % 11 % true",
            "[STATE] move(/.)", "*~/sys/who % 0 % true", "*who % 0 % true",
            // horizontal (other branch): root → b → b1
            "[STATE] move(/b)", "*~/sys/who % 4 % true", "*who % 44 % true",
            "[STATE] move(/b/b1)", "*~/sys/who % 5 % true", "*who % 55 % true",
            // deep cross-branch: b1 → c
            "[STATE] move(/c)", "*~/sys/who % 6 % true", "*who % 66 % true",
            // deep horizontal: c → a1 → b1
            "[STATE] move(/a/a1)", "*~/sys/who % 2 % true", "*who % 22 % true",
            "[STATE] move(/b/b1)", "*~/sys/who % 5 % true", "*who % 55 % true",
    })
    void testHierarchyMoves() {
    }

    // ======================== space mounts ========================

    /**
     * A space mounted in a non-root machine keeps its type when read back through either spelling of its mount
     * address — the relative {@code ~/memory/space/<name>} and the fully resolved {@code /a/memory/space/<name>}.
     * The read answers the LIVE space (tid {@code memspace::T}), not a flattened copy (tid {@code /m/rec}).
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "[STATE] *~.push(a)",
            "[STATE] memspace::[pattern=>/stuff/#]@~/memory/space/stuff",
            "*~/memory/space/stuff.tid()                         % /m/space/memspace",
            "*/a/memory/space/stuff.tid()                        % /m/space/memspace",
            "[STATE] /stuff/x -> 1",
            "*/stuff/x                                            % 1     % true",
            "[STATE] *~.pop(_)"
    })
    void testNonRootSpaceKeepsItsType() {
    }

    /**
     * A parent cannot reach data held in a space its child mounted: the child's space lives in the child's own index,
     * so once the parent is current again the address answers nothing.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "[STATE] *~.push(a)",
            "[STATE] memspace::[pattern=>/child/#]@~/memory/space/child",
            "[STATE] /child/x -> 1",
            "*/child/x                                            % 1     % true",
            "[STATE] move(/.)",
            "*/child/x                                            % noobj % true"
    })
    void testParentCannotReachChildSpaceData() {
    }

    /**
     * A sibling cannot reach data held in a space another sibling mounted: the space is in its owner's index alone.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "[STATE] *~.push(a)",
            "[STATE] memspace::[pattern=>/aspace/#]@~/memory/space/aspace",
            "[STATE] /aspace/x -> 1",
            "*/aspace/x                                           % 1     % true",
            "[STATE] move(/.)",
            "[STATE] *~.push(b)",
            "*/aspace/x                                           % noobj % true",
            "[STATE] *~.pop(_)"
    })
    void testSiblingCannotReachSiblingSpaceData() {
    }

    /**
     * A child reads data held in a space its parent mounted: the union walks outward and the parent's space answers.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "[STATE] memspace::[pattern=>/parent/#]@/memory/space/parent",
            "[STATE] /parent/x -> 1",
            "[STATE] *~.push(a)",
            "*/parent/x                                           % 1     % true",
            "[STATE] *~.pop(_)"
    })
    void testChildReachesParentSpaceData() {
    }

    /**
     * A descendant at arbitrary depth reads data held in a space the root mounted: the union walk recurses to the
     * root, so a grandchild sees what a child sees.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "[STATE] memspace::[pattern=>/grand/#]@/memory/space/grand",
            "[STATE] /grand/x -> 1",
            "[STATE] *~.push(a)",
            "*/grand/x                                            % 1     % true",
            "[STATE] *~.push(a1)",
            "*/grand/x                                            % 1     % true",
            "[STATE] *~.pop(_)",
            "[STATE] *~.pop(_)"
    })
    void testArbitraryDepthChildReachesParentSpaceData() {
    }

    /**
     * A child's space of the same pattern shadows the parent's for the child alone; the parent keeps its own — the
     * union installs alongside, it never evicts.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "[STATE] memspace::[pattern=>/shadow/#]@/memory/space/shadow",
            "[STATE] /shadow/x -> 1",
            "[STATE] *~.push(a)",
            "[STATE] memspace::[pattern=>/shadow/#]@~/memory/space/shadow-child",
            "[STATE] /shadow/x -> 2",
            "*/shadow/x                                           % 2     % true",
            "[STATE] move(/.)",
            "*/shadow/x                                           % 1     % true"
    })
    void testShadowingDoesNotEvictTheParentSpace() {
    }

    // ======================== thread isolation ========================

    /**
     * {@code Machine.current()} is a THREAD-LOCAL: two threads can stand in different submachines at the same time.
     * Each thread pushes its own chain, mounts its own {@code memspace::T} and writes into it; neither sees the other's
     * space, and neither leaks up to the root — exactly as if the two had run one after the other instead of side by
     * side.
     */
    @Test
    void testTwoThreadsStandInDifferentSubmachinesWithoutLeakingState() throws Exception {
        final CountDownLatch bothAtTheirMachine = new CountDownLatch(2);
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final AtomicReference<Machine> first = new AtomicReference<>();
        final AtomicReference<Machine> second = new AtomicReference<>();

        final Thread one = worker("t-one", "iso-one", 11, first, bothAtTheirMachine, failure);
        final Thread two = worker("t-two", "iso-two", 22, second, bothAtTheirMachine, failure);
        one.start();
        two.start();
        one.join();
        two.join();
        if (null != failure.get())
            throw new AssertionError("a worker thread failed", failure.get());

        // the two threads stood in different submachines
        assertNotEquals(first.get().vid().resolve(), second.get().vid().resolve(),
                "thread one and thread two stood in different machines");

        // each thread's machine answers only its own space — no leak across the threads
        Machine.current(first.get());
        assertEquals(11, Machine.read(f("/iso-one/value")).intValue(), "thread one reads its own space");
        assertEquals(noobj(), Machine.read(f("/iso-two/value")), "thread one does not read thread two's space");
        assertEquals(f("/m/space/memspace"), Machine.read(f("~/memory/space/iso-one")).tid(),
                "thread one's mount is a live memspace::T");

        Machine.current(second.get());
        assertEquals(22, Machine.read(f("/iso-two/value")).intValue(), "thread two reads its own space");
        assertEquals(noobj(), Machine.read(f("/iso-one/value")), "thread two does not read thread one's space");
        assertEquals(f("/m/space/memspace"), Machine.read(f("~/memory/space/iso-two")).tid(),
                "thread two's mount is a live memspace::T");

        // neither leaked up to the root
        Machine.current(Machine.root());
        assertEquals(noobj(), Machine.read(f("/iso-one/value")), "the root does not read thread one's space");
        assertEquals(noobj(), Machine.read(f("/iso-two/value")), "the root does not read thread two's space");

        // pop the two chains so they do not linger in the root's store for the next test
        Machine.current(first.get());
        Machine.current().pop();
        Machine.current().pop();
        Machine.current(second.get());
        Machine.current().pop();
        Machine.current().pop();
    }

    /**
     * A worker that pushes its own submachine chain, mounts a {@code memspace::T}, writes a value, then records the
     * machine it stands in once both workers have done so — proving the other worker's push never moved it.
     */
    private static Thread worker(final String leaf, final String pattern, final int value,
                                 final AtomicReference<Machine> sink, final CountDownLatch barrier,
                                 final AtomicReference<Throwable> failure) {
        return new Thread(() -> {
            try {
                Machine.current().push(f(leaf));   // root -> /t-<leaf>
                Machine.current().push(f("deep")); // -> /t-<leaf>/deep
                Machine.evaluate("memspace::[pattern=>/" + pattern + "/#]@~/memory/space/" + pattern);
                Machine.evaluate("/" + pattern + "/value -> " + value);
            } catch (final Throwable t) {
                failure.compareAndSet(null, t);
            } finally {
                barrier.countDown(); // never strand the other worker
            }
            try {
                if (!barrier.await(30, TimeUnit.SECONDS))
                    failure.compareAndSet(null, new AssertionError(leaf + " timed out waiting for the other thread"));
                sink.set(Machine.current());
            } catch (final Throwable t) {
                failure.compareAndSet(null, t);
            }
        }, leaf);
    }

    /**
     * The same no-leak property as the live-thread test above, expressed as named CURSORS instead of live threads: a
     * script runs on one JVM thread, so {@code [THREAD …]} is a saved machine position, not a concurrent thread. Each
     * named thread pushes its own submachines and mounts its own {@code memspace::T}; switching between them verifies
     * each reads only its own space.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "[THREAD CREATE a]",
            "*~.vid() % </.>",
            "[STATE] *~.push(t-a)",
            "[STATE] *~.push(deep)",
            "[STATE] memspace::[pattern=>/iso-a/#]@~/memory/space/iso-a",
            "[STATE] /iso-a/value -> 11",

            "[THREAD CREATE b]",
            "*~.vid() % </.>",
            "[STATE] *~.push(t-b)",
            "[STATE] *~.push(deep)",
            "[STATE] memspace::[pattern=>/iso-b/#]@~/memory/space/iso-b",
            "[STATE] /iso-b/value -> 22",

            // back onto a: it reads its own space, and none of b's
            "[THREAD a]",
            "*/iso-a/value % 11 % true",
            "*/iso-b/value % noobj % true",
            "*~/memory/space/iso-a.tid() % /m/space/memspace",

            // and b reads its own, none of a's
            "[THREAD b]",
            "*/iso-b/value % 22 % true",
            "*/iso-a/value % noobj % true",
            "*~/memory/space/iso-b.tid() % /m/space/memspace",

            // b moves to a-side, then pops off machines
            "*~.vid() % </t-b/deep>",
            "[STATE] move(/t-a/deep)",
            "*/iso-a/value % 11 % true",
            "*~.vid() % </t-a/deep>",
            "[STATE] pop()",
            "*~.vid() % </t-a>",
            "[STATE] pop()",
            "*~.vid() % </.>",

            // b is machine-less due to pop
            // moves to a-side, then pops off machines
            "[THREAD a]",
            "*~.vid() % noobj",
            "[STATE] move(/)",
            "*~.vid() % </.>",
            "[STATE] move(/t-b/deep)",
            "*/iso-b/value % 22 % true",
            "*~.vid() % </t-b/deep>",
            "[STATE] pop()",
            "*~.vid() % </t-b>",
            "[STATE] pop()",
            "*~.vid() % </.>",


    })
    void testThreadPerspectivesDoNotLeakAcross() {
    }
}
