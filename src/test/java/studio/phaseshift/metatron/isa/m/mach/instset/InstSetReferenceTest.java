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
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.instset.BasicInstSet;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.REFERENCE;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * THE MACHINE'S INSTRUCTION SET IS ONE REFLECTIVE UNIT: its own structure layered over the ISAs it imported,
 * held BY REFERENCE in import order.
 * <p>
 * The point of this suite is the word <em>reflective</em>. The union existed before, but its membership lived in
 * a Java field — so mtron could not answer "what are my insts, rewrites, sugars and types" without walking a
 * flurry of space mounts, which is the only reason anyone wanted the union in the first place. The membership now
 * lives in the rec, under {@code reference}, and these are the properties that follow:
 *
 * <ul>
 *   <li><b>the membership is readable from the rec</b> — it is an entry, not a field, so introspection sees what
 *       resolution sees;</li>
 *   <li><b>a reference is a vid, never a copy</b> — referring to an ISA must not re-parent it into this machine,
 *       and reading the rec must not inline a whole ISA;</li>
 *   <li><b>every referred vid resolves</b> to the instset that holds it — a reference list that cannot be walked
 *       is a list of strings.</li>
 * </ul>
 */
public class InstSetReferenceTest extends AbstractMetatronTest {

    @Test
    public void testTheMachinesInstsetRefersToWhatItImported() {
        final InstSet instset = Machine.current().instset();
        assertInstanceOf(BasicInstSet.class, instset, "the machine's own instset is the n-ary union");
        final BasicInstSet union = (BasicInstSet) instset;

        final List<Obj> referred = union.referVids();
        assertFalse(referred.isEmpty(), "the machine's instset refers to the ISAs it imported: " + referred);
        assertTrue(referred.stream().allMatch(Obj::isUri),
                "a reference is a vid, never a copy of the ISA: " + referred);
        assertFalse(union.atDirect(uri(REFERENCE)).isNoObj(),
                "the membership is IN THE REC, which is what makes the union reflective: " + union);

        assertEquals(referred.size(), union.references().size(),
                "every referred vid resolves to its instset, in import order: " + referred);
    }

    /**
     * THE MEMBERSHIP IS A SET OF VIDS, so every member carries the coefficient ONE.
     * <p>
     * Saving the entry through {@code at(..., MUTABLE)} appended each whole list to the one already stored, and a
     * multiplicity's arithmetic adds coefficients: every earlier member doubled on every import — measured as
     * {@code 1}, then {@code {2}}, then {@code {4,2}}, then {@code {4,2,1}}, which reaches 1024 after twelve
     * imports. Those coefficients are not data; nothing may inflate them.
     */
    @Test
    public void testEveryReferenceStaysAtCoefficientOne() {
        final BasicInstSet union = (BasicInstSet) Machine.current().instset();
        InstSet.importInstSet(f("/m/math"));
        InstSet.importInstSet(f("/m/db/vec"));
        InstSet.importInstSet(f("/m/web"));

        final List<Obj> referred = union.referVids();
        referred.forEach(reference -> assertEquals(cInt.ONE(), reference.c(),
                "a reference is a vid at coefficient one, never a cardinality: " + reference));
        assertEquals(referred.stream().map(Object::toString).distinct().count(), referred.size(),
                "importing the same ISA twice must not duplicate it: " + referred);
    }

    /**
     * THE CORE INSTRUCTION SETS ARE MEMBERS, and the union therefore answers an address in the base language the way
     * Memory does. They are mounted by hand in {@code BootLoader}; until that mount went through the same door as an
     * import, {@code instset()} could not answer for {@code /m} at all.
     */
    @Test
    public void testTheCoreInstructionSetsAreMembersAndAnswer() {
        final BasicInstSet union = (BasicInstSet) Machine.current().instset();
        final List<String> vids = union.ownReferVids().stream().map(Object::toString).toList();
        assertTrue(vids.contains("/m"), "the base instruction set is a member: " + vids);
        assertTrue(vids.contains("/m/sys"), "/m/sys is a member: " + vids);
        assertTrue(vids.contains("/m/mach"), "/m/mach is a member: " + vids);

        // the point of the membership: the union answers an ADDRESS, exactly as Memory does
        assertFalse(union.read(f("/m/inst/count")).isNoObj(),
                "the machine's own instset answers an address in /m: " + union.read(f("/m/inst/count")));
        assertFalse(Machine.read(f("/m/inst/count")).isNoObj(), "and Memory agrees");
    }

    /**
     * A CHILD'S MEMBERSHIP IS THE UNION. Its own level is EMPTY — the rec stays lazy, the parent's list lives in
     * {@code previous} rather than being copied — yet every inherited reference RESOLVES and is reflectively
     * readable. That is what makes {@code instset()} one locus instead of one per level: measured before this, a
     * child reported {@code referVids()} = 4 but {@code references()} = 0, and {@code *~/instset/reference} was
     * {@code noobj}, so the union could be used but not asked about inside a frame.
     */
    @Test
    public void testAChildsMembershipIsTheUnionAndReadsThroughPrevious() {
        final Machine root = Machine.current();
        final int inherited = ((BasicInstSet) root.instset()).referVids().size();
        assertTrue(inherited > 0, "the root imported something to inherit: " + root.instset());

        final Machine child = root.push(f("xyz"));
        try {
            final BasicInstSet union = (BasicInstSet) child.instset();
            assertTrue(union.ownReferVids().isEmpty(),
                    "the child introduced nothing of its own: " + union.ownReferVids());
            assertEquals(inherited, union.referVids().size(), "the union's membership is inherited");
            assertEquals(union.referVids().size(), union.references().size(),
                    "every inherited vid RESOLVES — references() is the union's, not the own level's");
            assertFalse(union.at(uri(REFERENCE)).isNoObj(),
                    "the membership is reflectively readable inside a frame: " + union.at(uri(REFERENCE)));
            assertFalse(Machine.read(f("~/instset/reference")).isNoObj(),
                    "and so is the address spelling inside a frame");
        } finally {
            child.pop();
        }
    }
}
