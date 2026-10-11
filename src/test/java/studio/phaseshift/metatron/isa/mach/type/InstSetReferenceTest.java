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

package studio.phaseshift.metatron.isa.mach.type;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.machine.BasicInstSet;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.REFERENCE;
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
}
