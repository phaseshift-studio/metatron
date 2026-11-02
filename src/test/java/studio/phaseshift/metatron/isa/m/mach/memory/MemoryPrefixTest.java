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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.Memory;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * MEMORY IS A PREFIXED COMPONENT TOO, and this pins its arm of the one algorithm: {@code registerPrefix} is the
 * redirect table's spelling, {@code prefixes()} is this level's rows, {@code alignPrefix} resolves a short name to its
 * home, and the parental view ({@link studio.phaseshift.metatron.isa.m.mach.type.Component.Prefix.Helper}) inherits,
 * shadows and gives back exactly as it does for a network's authorities and an ISA's scheme aliases. The generic
 * contract test {@code AbstractMetatronTest#testParentalChildPrefix} is the shared proof; these are the memory-specific
 * seams it does not name.
 */
public class MemoryPrefixTest extends AbstractMetatronTest {

    private static final fURI HOME = f("/m/memory_prefix_lab");
    private static final fURI OTHER = f("/m/memory_prefix_other");

    @AfterEach
    public void cleanup() {
        this.resetToRoot();
    }

    /**
     * THE CONTRACT'S SPELLING IS THE REDIRECT: a registerPrefix writes the name index the resolver reads, and the view
     * reports exactly that row.
     */
    @Test
    public void testRegisterPrefixIsTheRedirect() {
        final Memory memory = Machine.current().memory();
        final fURI name = f("mem" + Math.abs(RANDOM.nextInt(1_000_000)));
        try {
            assertEquals(uri(HOME), memory.registerPrefix(name, HOME), "the answer is the stored home");
            assertEquals(HOME, memory.redirect(name, true), "and a short name now resolves to it");
            assertEquals(uri(HOME), memory.prefixes().at(name), "the prefix view reports this level's row");
        } finally {
            memory.unregisterPrefix(name);
        }
    }

    /**
     * ALIGN RESOLVES A NAME AND ECHOES A SPELLING IT DOES NOT KNOW — the same contract the network and the ISA keep.
     */
    @Test
    public void testAlignPrefixResolvesANameAndEchoesAnUnknown() {
        final Memory memory = Machine.current().memory();
        final fURI name = f("mem" + Math.abs(RANDOM.nextInt(1_000_000)));
        final fURI unknown = f("unknown" + Math.abs(RANDOM.nextInt(1_000_000)));
        try {
            memory.registerPrefix(name, HOME);
            assertEquals(HOME, memory.alignPrefix(name), "a short name aligns to its home path");
            assertEquals(unknown.one(), memory.alignPrefix(unknown), "an unknown spelling passes through, coefficient one");
        } finally {
            memory.unregisterPrefix(name);
        }
    }

    /**
     * A RELATIVE HOME IS NOT A ROW: the redirect table maps names to PATHS, and a relative value names nothing outside
     * its own level — so the declaration answers noobj and the view stays empty.
     */
    @Test
    public void testARelativeHomeIsNotARow() {
        final Memory memory = Machine.current().memory();
        final fURI name = f("mem" + Math.abs(RANDOM.nextInt(1_000_000)));
        assertTrue(memory.registerPrefix(name, f("just_a_relative_path")).isNoObj(), "nothing was stored");
        assertTrue(memory.prefixes().at(name).isNoObj(), "and the view agrees");
    }

    /**
     * THE PARENTAL VIEW OVER MEMORY: the child sees the parent's row, its own declaration shadows, and withdrawing the
     * shadow gives the parent's meaning back — the same walk the generic contract test runs for every component.
     */
    @Test
    public void testTheParentalViewShadowsAndGivesBack() {
        final Machine root = Machine.current();
        final Memory rootMemory = root.memory();
        final fURI name = f("mem" + Math.abs(RANDOM.nextInt(1_000_000)));
        rootMemory.registerPrefix(name, HOME);
        final Machine child = root.push(f("mem_prefix_child"));
        final Memory childMemory = child.memory();
        Machine.current(root);
        try {
            assertEquals(uri(HOME), studio.phaseshift.metatron.isa.m.mach.type.Component.Prefix.Helper
                    .effectivePrefix(childMemory, name), "the child inherits the parent's name");
            childMemory.registerPrefix(name, OTHER);
            assertEquals(uri(OTHER), studio.phaseshift.metatron.isa.m.mach.type.Component.Prefix.Helper
                    .effectivePrefix(childMemory, name), "its own declaration shadows");
            assertEquals(uri(HOME), studio.phaseshift.metatron.isa.m.mach.type.Component.Prefix.Helper
                    .effectivePrefix(rootMemory, name), "the parent is untouched");
            childMemory.unregisterPrefix(name);
            assertEquals(uri(HOME), studio.phaseshift.metatron.isa.m.mach.type.Component.Prefix.Helper
                    .effectivePrefix(childMemory, name), "and withdrawing the shadow gives it back");
        } finally {
            Machine.current(root);
            studio.phaseshift.metatron.isa.m.mach.type.Machine.Helper.evalAt(f("/mem_prefix_child"), () -> Machine.current().pop());
            Machine.current(root);
            rootMemory.unregisterPrefix(name);
        }
    }

    /**
     * ANTI-DRIFT: every row the view shows must be the home {@code redirect} resolves for that name — the view and the
     * resolver are one table, not two.
     */
    @Test
    public void testPrefixesAgreesWithRedirect() {
        final Memory memory = Machine.current().memory();
        final fURI name = f("mem" + Math.abs(RANDOM.nextInt(1_000_000)));
        try {
            memory.registerPrefix(name, HOME);
            memory.prefixes().jvm().forEach((label, home) ->
                    assertEquals(home.uriValue().basePath(), memory.redirect(label.uriValue(), true).basePath(),
                            "the view's row for " + label + " is the home resolution reaches"));
        } finally {
            memory.unregisterPrefix(name);
        }
    }

    /**
     * A CHILD'S WITHDRAWAL NEVER TOUCHES THE PARENT'S BINDING (the redirect tables are per level).
     */
    @Test
    public void testAChildsWithdrawalLeavesTheParentsRow() {
        final Machine root = Machine.current();
        final Memory rootMemory = root.memory();
        final fURI name = f("mem" + Math.abs(RANDOM.nextInt(1_000_000)));
        rootMemory.registerPrefix(name, HOME);
        final Machine child = root.push(f("mem_prefix_withdraw"));
        final Memory childMemory = child.memory();
        Machine.current(root);
        try {
            childMemory.unregisterPrefix(name);
            assertEquals(uri(HOME), rootMemory.prefixes().at(name), "the parent still declares the name");
            assertEquals(uri(HOME), studio.phaseshift.metatron.isa.m.mach.type.Component.Prefix.Helper
                    .effectivePrefix(childMemory, name), "and the child still inherits it");
        } finally {
            Machine.current(root);
            Machine.Helper.evalAt(f("/mem_prefix_withdraw"), () -> Machine.current().pop());
            Machine.current(root);
            rootMemory.unregisterPrefix(name);
        }
    }
}
