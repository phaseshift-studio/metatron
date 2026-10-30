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

package studio.phaseshift.metatron.isa.mach.network;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.Memory;
import studio.phaseshift.metatron.isa.mach.type.Network;
import studio.phaseshift.metatron.util.MTronException;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.CUT_INST_TID;
import studio.phaseshift.metatron.isa.mach.type.Component;

/**
 * THE SEAM MATRIX: every invariant that decides whether a {@code move} stays here, where the boundary is when it does
 * not, and what the boundary is made of. The FEATURE suites pin the mechanisms — {@link MoveRemoteTest} the rewrite and
 * its values, {@link NetworkPrefixTest} the name table's storage door, {@code NetworkConnectTest} the client contract —
 * and this file pins the SEAMS BETWEEN them, one test per row, so a change to any one of the four can be read off the
 * table instead of rediscovered:
 *
 * <table border="1">
 *   <caption>the seams</caption>
 *   <tr><th>seam</th><th>condition</th><th>what must be true</th><th>test</th></tr>
 *   <tr>
 *     <td rowspan="2">crossing</td>
 *     <td>target has <b>no host</b> ({@code a}, {@code ..}, {@code loop:a})</td>
 *     <td>no cut, no shipped tail; a host-less move re-homes THIS thread and persists</td>
 *     <td>{@link #aSchemeWithNoHostIsNotCut}, {@link #aHostlessMoveRehomesAndPersists}</td>
 *   </tr>
 *   <tr>
 *     <td>target has a <b>host</b> ({@code loop://…}, {@code //name}, {@code maria://h})</td>
 *     <td>cut + ship; the cut's target is the RESOLVED address</td>
 *     <td>{@link #aDialedOriginShips}, {@link MoveRemoteTest}</td>
 *   </tr>
 *   <tr>
 *     <td>refusal</td>
 *     <td>host target, nothing dialed there — including a scheme that can never be dialed</td>
 *     <td>fails loudly, names the origin; NEVER evaluates locally and calls it a shipment</td>
 *     <td>{@link #aCrossingMoveWithoutADialIsRefused}</td>
 *   </tr>
 *   <tr>
 *     <td rowspan="3">boundary</td>
 *     <td>perspective</td>
 *     <td>the caller is where it stood; a local move inside the tail stays inside</td>
 *     <td>{@link #aMoveDoesNotRehomeTheCaller}</td>
 *   </tr>
 *   <tr>
 *     <td>arg frames ({@code Memory.ARG_STACK})</td>
 *     <td>swapped for a fresh stack, no binding crosses either way, restored by reference</td>
 *     <td>{@link #aMoveDoesNotCarryTheCallersArgFrames}</td>
 *   </tr>
 *   <tr>
 *     <td>thread object</td>
 *     <td>the far side is inside no thread</td>
 *     <td>{@link #aMoveDoesNotCarryTheCallersThread}</td>
 *   </tr>
 *   <tr>
 *     <td rowspan="4">names</td>
 *     <td>child declares a label the root also declares</td>
 *     <td>the child shadows; the root's row is untouched (shadow, never overwrite)</td>
 *     <td>{@link #aChildsRowShadowsTheRoots}</td>
 *   </tr>
 *   <tr>
 *     <td>a frame below the declaring one</td>
 *     <td>inherits the nearest declaration on its ancestor chain</td>
 *     <td>{@link #aDescendantInheritsTheNearestRow}</td>
 *   </tr>
 *   <tr>
 *     <td>a sibling branch</td>
 *     <td>never sees the other branch's row; falls back to the common ancestor's</td>
 *     <td>{@link #aSiblingNeverSeesTheOtherBranchesRow}</td>
 *   </tr>
 *   <tr>
 *     <td>the shadowing row removed</td>
 *     <td>the parent's meaning comes back</td>
 *     <td>{@link #unshadowingGivesTheParentsMeaningBack}</td>
 *   </tr>
 *   <tr>
 *     <td rowspan="4">{@code evalAt}</td>
 *     <td>target names no machine</td>
 *     <td>throws, and the caller's perspective is still restored</td>
 *     <td>{@link #evalAtRestoresWhenTheTargetNamesNoMachine}</td>
 *   </tr>
 *   <tr>
 *     <td>relative target</td>
 *     <td>folds against the caller's machine, as {@code move} does</td>
 *     <td>{@link #evalAtFoldsARelativeTargetAgainstTheCaller}</td>
 *   </tr>
 *   <tr>
 *     <td>the body throws</td>
 *     <td>the caller's perspective is restored anyway</td>
 *     <td>{@link #evalAtRestoresWhenTheBodyThrows}</td>
 *   </tr>
 *   <tr>
 *     <td>nesting</td>
 *     <td>restores LIFO, one frame per scope</td>
 *     <td>{@link #nestedEvalAtRestoresInOrder}</td>
 *   </tr>
 * </table>
 *
 * <p>
 * TWO THINGS THE TABLE DELIBERATELY DOES NOT CLAIM. <b>Data is not isolated</b>: the loop peer is this VM, so the
 * shipped program shares the machine tree, its memory and its spaces — the boundary scopes THREAD state (perspective,
 * arg frames, thread object), never the data. And <b>diagnostics cross</b>: {@code ExecutionStack} is left alone so a
 * trace can span a move; only the semantic state is scoped. The wire half of the boundary (a move over a socket cannot re-home the
 * caller, because the far side is another VM) is asserted in the live-wire harness, not here.
 */
public class MoveSeamsTest extends AbstractMetatronTest {

    /**
     * The client half of the crossing rule needs a peer; the loops below dial the identity client at the root. Tests
     * that need the REFUSAL use origins this never dials.
     */
    @BeforeEach
    void dial() {
        Network.Helper.connect(Machine.current().network(), f("loop://self"));
    }

    @AfterEach
    void undial() {
        Machine.current().network().drop(f("loop://self"));
    }

    // ======================== the crossing rule ========================

    /**
     * NEGATIVE HALF: a target with a scheme but NO HOST names no service, so the extractor leaves the chain alone — and
     * the program then dies by address. The assertion is on the REWRITE (no {@code cut}), because a cut here would ship
     * a tail to an origin nothing could ever dial.
     */
    @Test
    void aSchemeWithNoHostIsNotCut() {
        final Code parsed = ObjmtronSerializer.parse("5.move(<loop:a>).plus(1)");
        final var rewritten = parsed.rewrite().tryToInst();
        assertTrue(rewritten.insts().stream().noneMatch(inst -> CUT_INST_TID.equals(inst.tid().basePath())),
                "a host-less target is not cut: " + rewritten);
        final Obj answer = parsed.apply(noobj());
        assertTrue(answer.isFail(), "and it fails by address instead of shipping: " + answer);
    }

    /**
     * A HOST-LESS MOVE IS NOT A BOUNDARY: it re-homes THIS thread and it persists past the expression. That is the
     * state a boundary ISOLATES (where the other half keeps it), which is why the distinction is worth a test at all.
     */
    @Test
    void aHostlessMoveRehomesAndPersists() {
        final Machine before = Machine.current();
        Machine.current().push(f("persist"));
        Machine.current(before);
        try {
            assertEquals(jnt(5), Machine.evaluate("5.move(<persist>)"));
            assertEquals(f("/persist"), Machine.current().vid().resolve(),
                    "a host-less move leaves the calling thread re-homed");
        } finally {
            Machine.current().pop();
            Machine.current(before);
        }
    }

    /**
     * POSITIVE HALF, the control: with a peer dialed at the origin, the same host-bearing move ships and answers.
     */
    @Test
    void aDialedOriginShips() {
        assertEquals(jnt(6), Machine.evaluate("5.move(<loop://self>).plus(1)"));
    }

    // ======================== the refusal ========================

    /**
     * REFUSAL, for every shape that can cross: an undialed origin, an unbound NAME (no scheme, so nothing is even
     * dialable), and SPACE schemes ({@code maria:}, {@code mqtt:}, {@code docker:}). A space scheme has a host, so the
     * extractor cuts for it — and the point of the row is that the guard then refuses rather than letting the tail
     * evaluate locally and call it a shipment.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "5.move(<loop://nobody/a>).plus(1)   % loop://nobody",
            "5.move(<//nobody/a>).plus(1)        % //nobody",
            "1.move(<maria://h>).plus(1)         % maria://h",
            "1.move(<mqtt://h>).plus(1)          % mqtt://h",
            "1.move(<docker://h>).plus(1)        % docker://h",
    }, delimiter = '%')
    void aCrossingMoveWithoutADialIsRefused(final String code, final String origin) {
        final Obj answer = Machine.evaluate(code);
        assertTrue(answer.isFail(), "a crossing move with no peer must fail, never answer locally: " + answer);
        assertTrue(answer.asFail().message().contains("no peer is dialed"),
                "the refusal says what is missing: " + answer.asFail().message());
        assertTrue(answer.asFail().message().contains(origin),
                "and it names the origin it could not reach: " + answer.asFail().message());
    }

    /**
     * THE REFUSAL NAMES WHERE THE DIAL LIVES. The root dialed the peer; a frame that did not is refused — and the
     * message says so, because "not reachable from /a" alone cannot be told apart from "nobody dialed it anywhere".
     * That ambiguity is what made the loop client's first live failure read like a network fault.
     */
    @Test
    void theRefusalNamesTheLevelThatHoldsTheDial() {
        final Machine root = Machine.root();
        final Machine child = root.push(f("noreach"));
        Machine.current(root);
        try {
            final Obj answer = Machine.Helper.evalAt(f("/noreach"),
                    () -> Machine.evaluate("5.move(<loop://self>).plus(1)"));
            assertTrue(answer.isFail(), "a crossing move from a frame that dialed nothing must fail: " + answer);
            final String message = answer.asFail().message();
            assertTrue(message.contains("no peer is dialed"), "the refusal says what is missing: " + message);
            assertTrue(message.contains("dialed at"), "and names the level that holds the dial: " + message);
        } finally {
            Machine.current(root);
            Machine.Helper.evalAt(f("/noreach"), () -> Machine.current().pop());
            Machine.current(root);
        }
    }

    // ======================== the boundary ========================

    /**
     * PERSPECTIVE: the caller is not re-homed by a crossing move, however far the tail wanders. The landing frame is
     * minted here so the assertion has teeth — move to the root and the caller looks right even when the boundary leaks,
     * because it was standing there already.
     */
    @Test
    void aMoveDoesNotRehomeTheCaller() {
        final Machine before = Machine.current();
        Machine.current().push(f("hopland"));
        Machine.current(before);
        try {
            assertEquals(jnt(6), Machine.evaluate("5.move(<loop://self/hopland>).plus(1)"));
            assertEquals(before.vid(), Machine.current().vid(), "the caller is where it stood");
        } finally {
            Machine.current().move(f("/hopland")).pop();
            Machine.current(before);
        }
    }

    /**
     * ARG FRAMES: a binding pushed on the caller's {@code +/#} stack is invisible to the far side, and the far side's
     * stack is a DIFFERENT object (restored by reference), so nothing it binds can come back. This is the half a wire
     * peer gets for free and the loop peer has to scope by hand.
     */
    @Test
    void aMoveDoesNotCarryTheCallersArgFrames() {
        final var callerFrames = Memory.argStack();
        // TWO LAYERS, because ARG_STACK's top entry is the args of the inst IN FLIGHT and its reader starts at
        // size()-2: a base binding plus an args layer is what a running expression actually looks like.
        Memory.argStack().push(rec(uri("hopvar"), jnt(11)));
        Memory.argStack().push(rec(uri("inflight"), jnt(1)));
        try {
            assertEquals(jnt(11), Memory.argStack().read(f("hopvar")), "the caller sees its own frame");
            final AtomicReference<Obj> seenFarSide = new AtomicReference<>();
            final AtomicReference<Object> framesFarSide = new AtomicReference<>();
            Network.Helper.boundary(Machine.root(), () -> {
                framesFarSide.set(Memory.argStack());
                seenFarSide.set(Memory.argStack().read(f("hopvar")));
                return noobj();
            });
            assertTrue(seenFarSide.get().isNoObj(), "the far side does not see the caller's arg frame: " + seenFarSide.get());
            assertFalse(callerFrames == framesFarSide.get(), "the far side runs on its own frames");
            assertEquals(jnt(11), Memory.argStack().read(f("hopvar")), "and the caller's frame is back, whole");
        } finally {
            Memory.argStack().pop();
            Memory.argStack().pop();
        }
    }

    /**
     * THREAD OBJECT: a far side with no process of its own still has no thread object — it is not "inside" whatever the
     * caller was executing.
     */
    @Test
    void aMoveDoesNotCarryTheCallersThread() {
        final AtomicReference<Object> farSide = new AtomicReference<>();
        Network.Helper.boundary(Machine.root(), () -> {
            farSide.set(BootLoader.CURRENT_THREAD.get());
            return noobj();
        });
        assertNull(farSide.get(), "the far side is inside no thread object");
    }

    /**
     * THE WIRE HALF. A move over a socket cannot re-home the caller for the plainest reason: the far side is another VM on another
     * thread, so nothing it does to ITS perspective is visible here. Proving it needs a socket, which is the live-wire
     * harness's job ({@code NetworkWireScriptTest}); it is registered here so the table is not silently missing a row.
     */
    @Test
    @Disabled("needs a live socket: belongs in the wire harness (NetworkWireScriptTest)")
    void aWireMoveDoesNotRehomeTheCaller() {
        assertEquals(jnt(6), Machine.evaluate("5.move(<ws://localhost:8555/mtron>).plus(1)"));
    }

    // ======================== the name table that decides ========================

    /** Every {@code label=>origin} row of a table, as bare labels. */
    private static Set<String> labels(final Rec rows) {
        return rows.jvm().keySet().stream()
                .filter(Obj::isUri)
                .map(key -> key.uriValue().toString())
                .collect(Collectors.toSet());
    }

    /**
     * SHADOW, NEVER OVERWRITE: the child's row wins where it stands, and the root's row is still the root's.
     */
    @Test
    void aChildsRowShadowsTheRoots() {
        final Machine root = Machine.root();
        root.network().registerPrefix(f("sh"), f("loop://rooted"));
        final Machine child = Machine.current().push(f("shadow"));
        Machine.current(root);
        try {
            child.network().registerPrefix(f("sh"), f("loop://shadowed"));
            assertEquals("loop://rooted/sh", root.network().alignPrefix(f("//sh/sh")).toString());
            assertEquals("loop://shadowed/sh", child.network().alignPrefix(f("//sh/sh")).toString());
            assertTrue(labels(root.network().prefixes()).contains("sh"), "the root keeps its row: " + root.network().prefixes());
            assertTrue(labels(child.network().prefixes()).contains("sh"), "the child has its own: " + child.network().prefixes());
        } finally {
            Machine.current(root);
            root.network().unregisterPrefix(f("sh"));
        }
    }

    /**
     * THE CHAIN INHERITS: a frame below the declaring machine resolves the nearest row on its ancestor path.
     */
    @Test
    void aDescendantInheritsTheNearestRow() {
        final Machine root = Machine.root();
        final Machine child = Machine.current().push(f("chain"));
        child.network().registerPrefix(f("na"), f("loop://chained"));
        Machine.current(root);
        try {
            final Machine leaf = Machine.Helper.evalAt(f("/chain"), () -> Machine.current().push(f("deep")));
            Machine.current(root);
            assertEquals("loop://chained/na", leaf.network().alignPrefix(f("//na/na")).toString());
        } finally {
            Machine.current(root);
            Machine.Helper.evalAt(f("/chain/deep"), () -> Machine.current().pop());
            child.network().unregisterPrefix(f("na"));
            Machine.current(root);
        }
    }

    /**
     * SIBLINGS ARE ISOLATED: the walk is upward, so a row declared in one branch is invisible to another, which falls
     * back to the nearest COMMON ancestor (here, the root).
     */
    @Test
    void aSiblingNeverSeesTheOtherBranchesRow() {
        final Machine root = Machine.root();
        root.network().registerPrefix(f("si"), f("loop://common"));
        final Machine left = Machine.current().push(f("left"));
        left.network().registerPrefix(f("si"), f("loop://leftonly"));
        Machine.current(root);
        final Machine right = Machine.current().push(f("right"));
        Machine.current(root);
        try {
            assertEquals("loop://leftonly/left", left.network().alignPrefix(f("//si/left")).toString());
            assertEquals("loop://common/right", right.network().alignPrefix(f("//si/right")).toString(),
                    "the sibling sees the common ancestor's row, never the other branch's");
        } finally {
            Machine.current(root);
            root.network().unregisterPrefix(f("si"));
        }
    }

    /**
     * UNSHADOWING GIVES THE PARENT'S MEANING BACK — the deletion side is per-level, like the declaration.
     */
    @Test
    void unshadowingGivesTheParentsMeaningBack() {
        final Machine root = Machine.root();
        root.network().registerPrefix(f("un"), f("loop://parent"));
        final Machine child = Machine.current().push(f("unshadow"));
        Machine.current(root);
        try {
            child.network().registerPrefix(f("un"), f("loop://childdo"));
            assertEquals("loop://childdo/un", child.network().alignPrefix(f("//un/un")).toString());
            child.network().unregisterPrefix(f("un"));
            assertEquals("loop://parent/un", child.network().alignPrefix(f("//un/un")).toString(),
                    "with its own row gone, the frame inherits again");
            assertEquals("loop://parent/un", root.network().alignPrefix(f("//un/un")).toString(),
                    "and the parent's row was never touched");
        } finally {
            Machine.current(root);
            root.network().unregisterPrefix(f("un"));
        }
    }

    /**
     * THE VIEW, NOT THE STORAGE. A frame sees its own rows AND every ancestor's, nearest winning — the question the
     * console session asked, where {@code //ginger} resolved in a pushed frame while {@code *~/network/prefix} printed
     * nothing. {@code prefixes()} stays the frame's own rows; {@code Component.Prefix.Helper.effectivePrefixes} is the vocabulary it has.
     */
    @Test
    void theEffectiveViewMergesTheAncestorChain() {
        final Machine root = Machine.root();
        root.network().registerPrefix(f("ev"), f("loop://effectiveparent"));
        final Machine child = Machine.current().push(f("effectiveview"));
        child.network().registerPrefix(f("cv"), f("loop://effectivechild"));
        Machine.current(root);
        try {
            assertEquals(Set.of("ev", "cv"), labels(Component.Prefix.Helper.effectivePrefixes(child.network())),
                    "the frame sees its own row and the ancestor's");
            assertEquals(Set.of("ev"), labels(Component.Prefix.Helper.effectivePrefixes(root.network())),
                    "the root sees only the row it declares");
        } finally {
            Machine.current(root);
            root.network().unregisterPrefix(f("ev"));
            Machine.Helper.evalAt(f("/effectiveview"), () -> Machine.current().pop());
            Machine.current(root);
        }
    }

    /**
     * NEAREST WINS, in the view as in resolution: the same label shadowed by a frame means the frame's origin there.
     */
    @Test
    void theEffectiveViewShadowsNearestFirst() {
        final Machine root = Machine.root();
        root.network().registerPrefix(f("nv"), f("loop://viewparent"));
        final Machine child = Machine.current().push(f("viewshadow"));
        child.network().registerPrefix(f("nv"), f("loop://viewchild"));
        Machine.current(root);
        try {
            assertEquals(f("loop://viewchild"), Component.Prefix.Helper.effectivePrefix(child.network(), f("nv")).uriValue(),
                    "the frame's own row is the meaning it sees");
            assertEquals(f("loop://viewparent"), Component.Prefix.Helper.effectivePrefix(root.network(), f("nv")).uriValue(),
                    "the root is untouched");
        } finally {
            Machine.current(root);
            root.network().unregisterPrefix(f("nv"));
            Machine.Helper.evalAt(f("/viewshadow"), () -> Machine.current().pop());
            Machine.current(root);
        }
    }

    /**
     * The view follows an unshadowed row back to the parent's meaning — the view has no state of its own.
     */
    @Test
    void theEffectiveViewFollowsAnUnshadowedRow() {
        final Machine root = Machine.root();
        root.network().registerPrefix(f("uv"), f("loop://viewback"));
        final Machine child = Machine.current().push(f("viewunshadow"));
        child.network().registerPrefix(f("uv"), f("loop://viewgone"));
        Machine.current(root);
        try {
            child.network().unregisterPrefix(f("uv"));
            assertEquals(f("loop://viewback"), Component.Prefix.Helper.effectivePrefix(child.network(), f("uv")).uriValue(),
                    "with its row gone the frame sees the ancestor's meaning again");
        } finally {
            Machine.current(root);
            root.network().unregisterPrefix(f("uv"));
            Machine.Helper.evalAt(f("/viewunshadow"), () -> Machine.current().pop());
            Machine.current(root);
        }
    }

    /**
     * THE ANTI-DRIFT ROW: every row the view shows must be the origin resolution actually reaches for that label.
     * The view and the lookup walk the same chain; this is what keeps them one walk rather than two.
     */
    @Test
    void theEffectiveViewAgreesWithResolution() {
        final Machine root = Machine.root();
        root.network().registerPrefix(f("av"), f("loop://viewagrees"));
        final Machine child = Machine.current().push(f("viewagrees"));
        child.network().registerPrefix(f("bv"), f("loop://viewals"));
        Machine.current(root);
        try {
            Component.Prefix.Helper.effectivePrefixes(child.network()).jvm().forEach((label, origin) -> {
                final String name = label.uriValue().toString();
                assertEquals(origin.uriValue().origin(),
                        child.network().alignPrefix(f("//" + name + "/p")).origin(),
                        "the view's row for " + name + " is the origin resolution reaches");
            });
        } finally {
            Machine.current(root);
            root.network().unregisterPrefix(f("av"));
            Machine.Helper.evalAt(f("/viewagrees"), () -> Machine.current().pop());
            Machine.current(root);
        }
    }

    /**
     * THE CONSOLE QUESTION, and the exact demo: the root declares a name, and a pushed frame reads
     * {@code *~/network/prefix} — the door answers every row the frame SEES, shadowing included, even though the frame
     * itself declares nothing (the Java accessor {@code prefixes()} still answers its OWN rows).
     */
    @Test
    void theDoorShowsWhatAFrameSees() {
        final Machine root = Machine.root();
        root.network().registerPrefix(f("ginger"), f("loop://ginger.local:8555"));
        final Machine child = Machine.current().push(f("seesnames"));
        Machine.current(root);
        try {
            final Obj seen = Machine.Helper.evalAt(f("/seesnames"), () -> Machine.read(f("~/network/prefix")));
            assertTrue(seen.isRec(), "the door answers the frame's vocabulary: " + seen);
            assertEquals(Set.of("//ginger"), labels(seen.asRec()), "the ancestor's row, marked for display: " + seen);
            assertTrue(child.network().prefixes().jvm().isEmpty(), "while the frame still DECLARES nothing");
        } finally {
            Machine.current(root);
            root.network().unregisterPrefix(f("ginger"));
            Machine.Helper.evalAt(f("/seesnames"), () -> Machine.current().pop());
            Machine.current(root);
        }
    }

    /**
     * READ-THEN-WRITE IS A NO-OP. The door shows the merged view, so a table written straight back carries the
     * ancestor's rows too — they are dropped, because keeping one would copy an ancestor's declaration into this level
     * and shadow it from then on. A row the frame genuinely declares survives the same round trip.
     */
    @Test
    void writingTheSeenTableBackCopiesNothing() {
        final Machine root = Machine.root();
        root.network().registerPrefix(f("rtg"), f("loop://roundtrip"));
        final Machine child = Machine.current().push(f("roundtrip"));
        Machine.current(root);
        try {
            // 1. an untouched frame: writing back what it sees stores nothing
            final Obj seen = Machine.Helper.evalAt(f("/roundtrip"), () -> Machine.read(f("~/network/prefix")));
            Machine.Helper.evalAt(f("/roundtrip"), () -> Machine.write(f("~/network/prefix"), seen));
            assertTrue(child.network().prefixes().jvm().isEmpty(),
                    "the inherited row was not copied in: " + child.network().prefixes());
            // 2. after a declaration: the frame's own row survives, the inherited one still does not
            Machine.Helper.evalAt(f("/roundtrip"),
                    () -> Machine.current().network().registerPrefix(f("new"), f("loop://fresh")));
            final Obj withNew = Machine.Helper.evalAt(f("/roundtrip"), () -> Machine.read(f("~/network/prefix")));
            Machine.Helper.evalAt(f("/roundtrip"), () -> Machine.write(f("~/network/prefix"), withNew));
            assertEquals(Set.of("new"), labels(child.network().prefixes()),
                    "the declared row is re-stored, the inherited one is not: " + child.network().prefixes());
        } finally {
            Machine.current(root);
            root.network().unregisterPrefix(f("rtg"));
            Machine.Helper.evalAt(f("/roundtrip"), () -> Machine.current().pop());
            Machine.current(root);
        }
    }

    // ======================== evalAt, the scoped perspective ========================

    @Test
    void evalAtRestoresWhenTheTargetNamesNoMachine() {
        final Machine before = Machine.current();
        assertThrows(MTronException.class, () -> Machine.Helper.evalAt(f("/no/such/machine"), () -> noobj()));
        assertEquals(before.vid(), Machine.current().vid(), "a failed resolution must not move the caller");
    }

    @Test
    void evalAtFoldsARelativeTargetAgainstTheCaller() {
        final Machine before = Machine.current();
        Machine.current().push(f("folded"));
        Machine.current(before);
        try {
            final AtomicReference<fURI> inside = new AtomicReference<>();
            Machine.Helper.evalAt(f("folded"), () -> {
                inside.set(Machine.current().vid().resolve());
                return noobj();
            });
            assertEquals(f("/folded"), inside.get(), "a relative target folds against the caller, like move");
        } finally {
            Machine.Helper.evalAt(f("/folded"), () -> Machine.current().pop());
            Machine.current(before);
        }
    }

    @Test
    void evalAtRestoresWhenTheBodyThrows() {
        final Machine before = Machine.current();
        Machine.current().push(f("throwing"));
        Machine.current(before);
        try {
            assertThrows(IllegalStateException.class, () -> Machine.Helper.evalAt(f("/throwing"), () -> {
                throw new IllegalStateException("boom");
            }));
            assertEquals(before.vid(), Machine.current().vid(), "the finally still restores");
        } finally {
            Machine.Helper.evalAt(f("/throwing"), () -> Machine.current().pop());
            Machine.current(before);
        }
    }

    @Test
    void nestedEvalAtRestoresInOrder() {
        final Machine before = Machine.current();
        Machine.current().push(f("outer"));
        Machine.current().push(f("inner"));
        Machine.current(before);
        try {
            final AtomicReference<fURI> afterInner = new AtomicReference<>();
            Machine.Helper.evalAt(f("/outer"), () -> {
                Machine.Helper.evalAt(f("/outer/inner"), () -> noobj());
                afterInner.set(Machine.current().vid().resolve());
                return noobj();
            });
            assertEquals(f("/outer"), afterInner.get(), "the inner scope restores the outer, not the root");
            assertEquals(before.vid(), Machine.current().vid(), "and the outer restores the caller");
        } finally {
            Machine.Helper.evalAt(f("/outer/inner"), () -> Machine.current().pop());
            Machine.Helper.evalAt(f("/outer"), () -> Machine.current().pop());
            Machine.current(before);
        }
    }
}
