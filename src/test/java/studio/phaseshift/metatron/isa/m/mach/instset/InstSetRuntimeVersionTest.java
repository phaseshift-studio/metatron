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

package studio.phaseshift.metatron.isa.m.mach.instset;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.instset.BasicInstSet;
import studio.phaseshift.metatron.isa.m.space.noobjSpace;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.util.MTronException;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.REWRITE;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * The vocabulary version — {@link InstSet#runtimeVersion()} — and the doors that advance it.
 * <p>
 * The claim being pinned is narrow and load-bearing: a version must ADVANCE at every door that can change what a
 * level contributes to a union, because a memoized derived view (a union's merged rule list is the one that matters)
 * decides whether to trust itself by comparing the version it stored with the version it now sees. A door that
 * forgets to bump does not fail loudly — it serves an answer derived from tables that have since changed, which is
 * how a rewriter silently stops firing a rule.
 * <p>
 * The stores here are throwaway levels ({@code new BasicInstSet()}), never a mounted ISA: an instruction set's
 * rewrite table has no removal door ({@code write} only puts), so a probe rule written into {@code /m} would be a
 * permanent rule of the whole test JVM.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class InstSetRuntimeVersionTest extends AbstractMetatronTest {

    /** A rewrite rule, minted the way an instset declares one: code in, code out, named by its tid. */
    private static Inst rule(final String name) {
        return InstSet.Helper.rewriter(f(name), code -> code);
    }

    /** A mounted library to refer — any real ISA level, which this test only ever reads. */
    private static InstSet library() {
        return Machine.current().instset().members().stream()
                .filter(BasicInstSet::isLibrary)
                .findFirst()
                .orElseThrow(() -> MTronException.of("no library is mounted to refer"));
    }

    /**
     * A level that has contributed nothing reports the SAME version forever, and {@code noobjSpace} — a store whose
     * every kind accessor is the empty set — reports that constant honestly rather than claiming "unknown".
     */
    @Test
    void shouldStartAStoreAtTheSameVersionEveryTime() {
        final InstSet level = new BasicInstSet();
        assertEquals(0, level.runtimeVersion(), "a level with no tables written starts at zero");
        assertEquals(level.runtimeVersion(), level.runtimeVersion(), "and reads the same until something changes");
        assertEquals(0, level.rewrites().size(), "nothing is there to contribute yet");
        final InstSet nothing = noobjSpace.single();
        assertEquals(0, nothing.runtimeVersion(), "a store with nothing to fill never claims a change");
    }

    /**
     * A write into a table is the door the version exists for. The const is deliberate: whatever table a write lands
     * in ({@code write} files a code-dom rule as an INST — see the prompt), the version must advance with it.
     */
    @Test
    void shouldAdvanceTheVersionWhenATableIsWritten() {
        final InstSet level = new BasicInstSet();
        final int before = level.runtimeVersion();
        level.write(f("/probe/const/quebec"), jnt(42));
        assertEquals(1, level.consts().size(), "the write landed in the const table");
        final int written = level.runtimeVersion();
        assertNotEquals(before, written, "a write into a table is a change the version must report");
        level.write(f("/probe/const/romeo"), jnt(43));
        assertEquals(2, level.consts().size(), "the second write landed too");
        assertNotEquals(written, level.runtimeVersion(), "and a second write is a second change");
    }

    /**
     * MEMBERSHIP IS CONTRIBUTION. A union level's view is its own tables PLUS what it refers, so a refer, an
     * unroute and a reparent all change what it answers — three doors that never touch a table and would otherwise
     * be invisible to a memo.
     */
    @Test
    void shouldAdvanceTheVersionWhenTheMembershipChanges() {
        final BasicInstSet level = new BasicInstSet();
        final InstSet library = library();
        final int before = level.runtimeVersion();
        level.refer(library);
        final int referred = level.runtimeVersion();
        assertNotEquals(before, referred, "a refer changes what the level answers");
        assertEquals(1, level.ownReferVids().size(), "and the membership records it");
        level.unroute(library.vid());
        final int unrouted = level.runtimeVersion();
        assertNotEquals(referred, unrouted, "an unroute changes it again");
        level.previous(new BasicInstSet());
        assertNotEquals(unrouted, level.runtimeVersion(), "and so does reparenting: the enclosing level is part of the answer");
    }

    /**
     * The mount-time door: {@code setup} fills the tables directly, migrates a rule into the level that owns it and
     * drains the builders' algebra — all of it landing in a level that is ALREADY a union member. That is the case a
     * membership-only version cannot see, so it is the bump that has to be explicit.
     */
    @Test
    void shouldAdvanceTheVersionWhenTheLevelIsSetUp() {
        final InstSet level = new AbstractInstSet(
                mutableMap(uri(REWRITE), lst(rule("zulu"))), f("/probe/setup"), f("/probe/setup")) {
        };
        final int before = level.runtimeVersion();
        level.setup();
        assertEquals(1, level.rewrites().size(), "setup populated the rewrite table");
        assertNotEquals(before, level.runtimeVersion(), "the mount-time population is a change of the vocabulary");
    }

    /**
     * Monotone, and it never repeats a value it has published. A memo compares for equality, so a version that could
     * go back — or stand still across a change — is the stale hit the whole design exists to prevent.
     */
    @Test
    void shouldNeverAnswerAVersionItAlreadyAnswered() {
        final BasicInstSet level = new BasicInstSet();
        final InstSet library = library();
        int previous = level.runtimeVersion();
        level.write(f("/probe/const/quebec"), jnt(42));
        assertTrue(level.runtimeVersion() > previous, "a write advances");
        previous = level.runtimeVersion();
        level.refer(library);
        assertTrue(level.runtimeVersion() > previous, "a refer advances");
        previous = level.runtimeVersion();
        level.unroute(library.vid());
        assertTrue(level.runtimeVersion() > previous, "an unroute advances");
    }
}
