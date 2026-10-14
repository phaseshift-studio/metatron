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

package studio.phaseshift.metatron.isa.mach;

import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.StatefulCSVSource;
import studio.phaseshift.metatron.StatefulParametrizedTest;

/**
 * MACHINE navigation as a stateful script — the movement half of the machine-isolation family, beside
 * {@code MemoryIsolationScriptTest}, {@code InstSetIsolationScriptTest} and {@code NetworkIsolationScriptTest},
 * deliberately in the same script form so they read side by side. Where those ask what a frame HOLDS, this asks
 * where a frame IS.
 * <p>
 * The three frame instructions speak ONE address dialect, and these scripts stress it: {@code ~} is the frame you
 * stand in, {@code ~/a} (or the bare {@code a}) is its child, {@code ..} is its parent, {@code ../b} is the
 * sibling {@code b}, and {@code ../../..} walks as far up as asked. What separates the three is only what they do
 * with an address:
 * <ol>
 *     <li><b>push MINTS the address</b> — it resolves the address and mints its LEAF from the frame at its
 *     PARENT, so {@code push(<../b>)} is exactly {@code move(<..>).push(<b>)}: a sibling is minted from the
 *     shared parent, and its unions come from there because that is the machine push is called on.</li>
 *     <li><b>move WALKS to the address and closes nothing</b> — this is the distinction the whole motif exists
 *     for: {@code pop()} destroys the frame it leaves, {@code move(<..>)} leaves it standing and reachable at its
 *     own address. A tree of siblings is therefore possible at all.</li>
 *     <li><b>pop DISCARDS the address you stand in</b> — {@code pop()} and {@code pop(<~>)} are one
 *     instruction, and anything that does not resolve to where you stand is refused rather than silently
 *     ignored.</li>
 * </ol>
 * <p>
 * Every script walks back to the root with {@code move(/.)} so a method cannot leave the shared VM standing in a
 * frame another method's rows would then be read from.
 */
public class MachineIsolationScriptTest extends AbstractMetatronTest {

    /* ============================================================================================
     * WHY THERE IS NO TEST HERE FOR "A MACHINE NOTHING HOLDS" THAT IS NOT THE ROOT.
     *
     * The guard in POP asks about CONTAINMENT — `!(here.parent() instanceof Machine)` — and there are nominally
     * two ways to have no container: the root, and a machine minted at an address rather than pushed
     * (`machine::[=>]@/random/location`). Only the FIRST is a state a program can be in, and the second branch is
     * defensive. Measured, on this suite:
     *
     *   - a machine minted with NO instset is detached from the LANGUAGE as well as the tree: standing in it,
     *     even `vid()` fails ("unable to locate inst-f … vid()"), because the machine that would resolve the
     *     instruction is the one that has no instructions;
     *   - given a borrowed instset it gets further and dies resolving the TYPE ("unable to locate inst-f
     *     machine::T => vid()"), and a variant of this fixture HUNG the suite.
     *
     * The reason is structural, not incidental: `Machine.Component.machine()` finds a component's machine by
     * WALKING the containment link, and answers mach0() when there is none. The containment link is therefore what
     * makes a frame's components resolvable at all — a machine nothing holds cannot run its own code, so "a
     * functioning frame that nothing holds" is not a state this VM has. The reachable no-parent machine is the
     * ROOT, which is what the script below pins.
     *
     * The containment test remains the right INVARIANT (it says why a pop is refused, and it degrades correctly
     * rather than silently no-op'ing if coherent orphan frames ever exist); the second branch is belt-and-braces.
     * What is NOT true is that it can be reached and exercised by a test today.
     * ============================================================================================ */

    /**
     * THE DISTINCTION THE MOTIF EXISTS FOR. push/up/push builds siblings that are ALL still alive, because the
     * up-move is not a pop: the frame it walks away from keeps its address, and the next push mints a second
     * child of the same parent. The same shape written with pop() would have destroyed the first sibling — which
     * is exactly why this could not be expressed before.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "[STATE] *~.push(<nav-alpha>)",
            "*~.vid() % /nav-alpha",
            // up, WITHOUT closing what we walked away from
            "[STATE] move(<..>)",
            "*~.vid() % /.",
            // ... so the frame is still a machine at its own address
            "*/nav-alpha.tid().eq(/m/mach/machine) % true",
            // and you can walk back into it
            "[STATE] move(<nav-alpha>)",
            "*~.vid() % /nav-alpha",
            // POP, by contrast, destroys: the cursor lands on the frame it discarded
            "[STATE] pop()",
            "*~.vid() % /.",
            "[STATE] move(/.)"
    })
    void testMoveLeavesTheFrameStandingWherePopDiscardsIt() {
    }

    /**
     * SIBLINGS, the shape the motif was asked for: one parent, three children, all live at once. Each child is
     * minted from the parent (never from its sibling) and then crossed to BY NAME, which is the only lateral
     * movement a space without an order can honestly offer — there is no "next" sibling, there is "that" sibling.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "[STATE] *~.push(<sib-a>)",
            "[STATE] move(<..>)",
            "[STATE] *~.push(<sib-b>)",
            "[STATE] move(<..>)",
            "[STATE] *~.push(<sib-c>)",
            "*~.vid() % /sib-c",
            // three siblings alive simultaneously — each up-move left its frame standing
            "*/sib-a.tid().eq(/m/mach/machine) % true",
            "*/sib-b.tid().eq(/m/mach/machine) % true",
            "*/sib-c.tid().eq(/m/mach/machine) % true",
            // lateral by name: `..` is the parent, then the sibling's name
            "[STATE] move(<../sib-a>)",
            "*~.vid() % /sib-a",
            // and across the other way, skipping the middle one entirely
            "[STATE] move(<../sib-c>)",
            "*~.vid() % /sib-c",
            "[STATE] move(<../sib-b>)",
            "*~.vid() % /sib-b",
            "[STATE] move(/.)"
    })
    void testSiblingsAreBuiltByMovingUpNotByPushingSideways() {
    }

    /**
     * THE ARITHMETIC, at depth: one step up, several steps up, a composed path DOWN in a single instruction, and
     * a {@code ~}-based displacement. The last two are the ones that pin the resolution order — {@code ~} has to
     * be consumed BEFORE the fold, or the fold pops the base itself and {@code ~/../..} collapses instead of
     * naming an ancestor.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "[STATE] *~.push(<dep1>)",
            "[STATE] *~.push(<dep2>)",
            "[STATE] *~.push(<dep3>)",
            "*~.vid() % /dep1/dep2/dep3",
            // one step up
            "[STATE] move(<..>)",
            "*~.vid() % /dep1/dep2",
            // two steps up, in one instruction
            "[STATE] move(<../..>)",
            "*~.vid() % /.",
            // down a composed path in one instruction, from the root
            "[STATE] move(<dep1/dep2/dep3>)",
            "*~.vid() % /dep1/dep2/dep3",
            // a `~`-based displacement: the frame base survives the fold
            "[STATE] move(<~/../..>)",
            "*~.vid() % /dep1",
            "[STATE] move(<..>)",
            "*~.vid() % /.",
            // `~` alone is here — no movement at all
            "[STATE] move(<~>)",
            "*~.vid() % /.",
            // and `~/x` is the same place as the bare child name
            "[STATE] move(<~/dep1>)",
            "*~.vid() % /dep1",
            "[STATE] move(/.)"
    })
    void testUpDownAndAcrossComposeInOneAddressArithmetic() {
    }

    /**
     * ABOVE THE ROOT. A displacement ({@code ../../..}) is a direction, not a place, and an instruction that
     * chooses where code runs has to stand somewhere — so a base popped past the root SATURATES at the root
     * rather than escaping the tree. It is a no-op, not a teardown: the frame it climbed from is intact, and
     * bare {@code move()} is the same instruction as {@code move(<..>)}.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            // already at the root: `..` has nowhere to go and stays put
            "[STATE] move(<..>)",
            "*~.vid() % /.",
            "[STATE] move(<../..>)",
            "*~.vid() % /.",
            "[STATE] *~.push(<sat>)",
            "*~.vid() % /sat",
            // three steps up from a depth-1 frame is two past the root: it saturates
            "[STATE] move(<../../..>)",
            "*~.vid() % /.",
            // saturation walked up, so the frame it left is a live machine
            "*/sat.tid().eq(/m/mach/machine) % true",
            // bare move() IS move(<..>) — down is push, up is move, up-and-destroy is pop
            "[STATE] move(<sat>)",
            "*~.vid() % /sat",
            "[STATE] move()",
            "*~.vid() % /.",
            "[STATE] move(/.)"
    })
    void testAPathThatEscapesTheRootSaturatesAtTheRoot() {
    }

    /**
     * THE POP CONTRACT, both halves. {@code pop(<~>)} names the frame you stand in, so it is exactly
     * {@code pop()} — and it lands on {@code ..}. Everything else is REFUSED, loudly: a frame is discarded by the
     * one that holds it, so a sibling (or the parent) is not yours to discard from where you stand, and popping it
     * by address would be a destructive instruction that silently does nothing.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "[STATE] *~.push(<pop1>)",
            "[STATE] *~.push(<pop2>)",
            "*~.vid() % /pop1/pop2",
            // pop(~) and pop() are one instruction, and both land on the parent
            "[STATE] pop(<~>)",
            "*~.vid() % /pop1",
            "[STATE] pop()",
            "*~.vid() % /.",
            // a frame reachable only BY ADDRESS is not yours to discard from here: refused
            "[STATE] *~.push(<pop3>)",
            "[STATE] move(<..>)",
            "pop(<pop3>) % <ERROR>",
            // ... and the refusal is not a move: the cursor is exactly where it was
            "*~.vid() % /.",
            // the parent is refused too — the stack discipline is one direction, so `..` names an ancestor,
            // and an ancestor cannot be discarded out from under the frame that stands in it
            "[STATE] move(<pop3>)",
            "pop(<..>) % <ERROR>",
            "*~.vid() % /pop3",
            // discarding it is done by standing in it
            "[STATE] pop()",
            "*~.vid() % /.",
            "[STATE] move(/.)"
    })
    void testPopNamesTheFrameYouStandInAndRefusesTheRest() {
    }

    /**
     * PUSH AT AN ADDRESS IS move-to-its-PARENT THEN push-its-NAME — the algebra {@code push(<../b>) ==
     * move(<..>).push(<b>)}. A sibling is therefore minted FROM the shared parent, never from beside it, and its
     * unions (memory + instset) come from that parent because that is the machine push is called ON. So push can
     * CROSS a level as well as descend into one, and it leaves what it crossed standing exactly as move does.
     * <p>
     * This is also the sharpest test that the unions land in the right place: a frame with no vocabulary cannot
     * resolve even {@code vid()}, so the {@code *~.vid()} rows below would fail for a sibling minted from the
     * WRONG parent (or from no parent at all).
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "[STATE] *~.push(<sib-a>)",
            "*~.vid() % /sib-a",
            // `../sib-b` resolves to /sib-b, whose PARENT is the root — so the sibling is minted from the ROOT,
            // which is exactly move(<..>).push(<sib-b>)
            "[STATE] *~.push(<../sib-b>)",
            "*~.vid() % /sib-b",
            // both are children of the root, and BOTH are live: neither push disturbed the other
            "*/sib-a.tid().eq(/m/mach/machine) % true",
            "*/sib-b.tid().eq(/m/mach/machine) % true",
            // the two-verb spelling of the same crossing, for contrast: up, then down
            "[STATE] move(<..>)",
            "[STATE] *~.push(<sib-c>)",
            "*~.vid() % /sib-c",
            // a sibling of a NESTED frame: standing in /sib-a, `../deep` is a child of the root
            "[STATE] move(<sib-a>)",
            "[STATE] *~.push(<../deep>)",
            "*~.vid() % /deep",
            // one level down, everything minted so far is still a live machine
            "*/sib-a.tid().eq(/m/mach/machine) % true",
            "*/sib-b.tid().eq(/m/mach/machine) % true",
            "*/deep.tid().eq(/m/mach/machine) % true",
            "[STATE] move(/.)"
    })
    void testPushAtAnAddressMintsFromThatAddressesParent() {
    }

    /**
     * NAVIGATION AND DATA TOGETHER: the frames you walk through are the frames that hold the bindings, so moving
     * is what decides which binding a relative name reaches — and, the other way round, navigation is the ONLY way
     * into a frame's own level. A sibling's interior is deliberately not addressable from beside it (that is the
     * no-leak-across isolation), while the sibling FRAME is addressable — which is precisely what makes
     * {@code move(<../name>)} the way to reach it.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "[STATE] *~.push(<nav1>)",
            "[STATE] *~.push(<nav2>)",
            "[STATE] ~/iso -> 41",
            // the binding answers where it was made
            "*/nav1/nav2/iso % 41",
            // leave the whole nest and come back down the composed path: it is still there
            "[STATE] move(<../..>)",
            "*~.vid() % /.",
            "[STATE] move(<nav1/nav2>)",
            "*~/iso % 41",
            // one level up, then mint a SIBLING of nav2 under the same parent
            "[STATE] move(<~/..>)",
            "*~.vid() % /nav1",
            "[STATE] *~.push(<nav3>)",
            "*~.vid() % /nav1/nav3",
            // the sibling inherits nothing of its brother's binding ...
            "*~/iso % noobj % true",
            // ... and reaches it by neither name nor address: a frame's own level is its own
            "*/nav1/nav2/iso % noobj % true",
            // the sibling FRAME is addressable all the same — `..` + the name is how you get to it ...
            "*/nav1/nav2.vid() % /nav1/nav2",
            // ... and moving in is how you reach the binding it holds
            "[STATE] move(<../nav2>)",
            "*~.vid() % /nav1/nav2",
            "*~/iso % 41",
            "[STATE] move(/.)"
    })
    void testWalkingTheTreeIsTheOnlyWayIntoAFramesOwnLevel() {
    }

    /**
     * THE ROOT HAS NO {@code ..}, so {@code pop()} refuses there instead of silently answering as if it had
     * discarded something. The test is CONTAINMENT — nothing holds the root — and the refusal is not a move: the
     * cursor is exactly where it was, and the frame a refused pop walked up from is untouched. Closing the root is
     * the SHUTDOWN path (it owns the address space), so the honest instruction for that is a reset, which is why
     * the message says so rather than closing anything.
     * <p>
     * The last block is the contrast that makes the refusal meaningful: the SAME {@code pop()} works one level
     * down, where a frame does hold the cursor — it closes that frame and lands on the root.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "[STATE] move(/.)",
            // the root is not held by a frame, so there is no frame to release and nowhere to land
            "pop() % <ERROR>",
            // pop(~) is pop(), so it refuses for the same reason — and refuses BEFORE it looks at the argument
            "pop(<~>) % <ERROR>",
            // refusing is not moving: the cursor never left
            "*~.vid() % /.",
            // and it holds at the root however you arrived — mint a child, walk up, then try
            "[STATE] *~.push(<root-pop>)",
            "[STATE] move(<..>)",
            "*~.vid() % /.",
            "pop() % <ERROR>",
            // a refused pop closes NOTHING: the frame we walked up from is still a live machine
            "*/root-pop.tid().eq(/m/mach/machine) % true",
            // the way back down is a move ...
            "[STATE] move(<root-pop>)",
            "*~.vid() % /root-pop",
            // ... and here pop() works, because THIS frame is held: it closes and lands on the root
            "[STATE] pop()",
            "*~.vid() % /.",
            "[STATE] move(/.)"
    })
    void testPopRefusesAtTheRootBecauseNothingHoldsIt() {
    }

    /**
     * ARBITRARY DEPTH, ACROSS BRANCHES — Marko's pattern, verbatim:
     * <pre>
     *   push(a).push(aa).move(&lt;/.&gt;).push(b).push(bb).push(&lt;../../a/aa/aaa&gt;)
     * </pre>
     * The last push stands in {@code /b/bb} and mints a THIRD-level node under the OTHER branch. Nothing about it
     * is local: the address is composed through two displacements ({@code ../../}), the container it resolves to
     * is a frame two levels deep in a branch we are not standing in, and only the leaf {@code aaa} is new. That is
     * the strongest statement of the rule — {@code push(<t>)} mints {@code name(t)} from the frame at
     * {@code parent(t)}, wherever that frame is — and it is why the depth of a mint is not bounded by where you
     * happen to be standing.
     * <p>
     * It works because a frame's ADDRESS is registered where every frame is registered, so another branch's frame
     * can be reached by address even though its INTERIOR is isolated (that is pinned separately, in
     * {@link #testWalkingTheTreeIsTheOnlyWayIntoAFramesOwnLevel()}). Reading a frame across branches is the
     * enabler; reading what it holds is not, and needs a move.
     */
    @StatefulParametrizedTest
    @StatefulCSVSource({
            "[STATE] push(a).push(aa).move(</.>).push(b).push(bb).push(<../../a/aa/aaa>)",
            "*~.vid() % /a/aa/aaa",
            // every frame the chain minted is a live machine, in both branches
            "*/a.tid().eq(/m/mach/machine) % true",
            "*/a/aa.tid().eq(/m/mach/machine) % true",
            "*/a/aa/aaa.tid().eq(/m/mach/machine) % true",
            "*/b.tid().eq(/m/mach/machine) % true",
            "*/b/bb.tid().eq(/m/mach/machine) % true",
            // the deep frame was minted FROM /a/aa, so that is where `..` goes — not back to where we minted it
            "[STATE] move(<..>)",
            "*~.vid() % /a/aa",
            // and it is a working frame, not an address that merely resolves: it runs code
            "[STATE] move(<aaa>)",
            "*~.vid() % /a/aa/aaa",
            // four steps up from depth three is past the root: saturation, not a displacement
            "[STATE] move(<../../..>)",
            "*~.vid() % /.",
            "[STATE] move(/.)"
    })
    void testPushReachesArbitraryDepthAcrossBranches() {
    }
}
