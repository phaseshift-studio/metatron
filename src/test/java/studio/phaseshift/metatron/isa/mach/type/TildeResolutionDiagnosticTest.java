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
import studio.phaseshift.metatron.furi.fURI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;

/**
 * WHAT {@code ~} RESOLVES TO, pinned. {@code ~} IS THE CURRENT MACHINE: every memory impl resolves it against
 * {@code Machine.current().vid()} BEFORE the relative/absolute split, so {@code ~/person} becomes the ordinary
 * absolute address {@code <current-vid>/person}. There is NO FALL-THROUGH to an enclosing frame — a parent's
 * ~-addressed value is INVISIBLE from a child, and so is a name.
 * <p>
 * That is the opposite of what a script-level probe appeared to show (a {@code *~/friend} row read the parent's
 * value from a grandchild), and the discrepancy is UNRESOLVED: the Java read here is the mechanism-level truth, so
 * the script reading is either a harness artifact or the mtron evaluator expands {@code ~} differently from
 * {@code Memory.read}. It matters because a vocabulary projection over {@code ~/inst}, {@code ~/type},
 * {@code ~/rewrite} and {@code ~/sugar} would NOT inherit for free — inheritance must be BUILT, by re-binding
 * {@code ~} at each level of the walk or by walking frames in the projection. This test must change when that
 * lands, which is exactly its job.
 */
public class TildeResolutionDiagnosticTest extends AbstractMetatronTest {

    @Test
    public void tildeInAChildDoesNotSeeAParentsTildeAddress() {
        final Machine root = Machine.current();
        final fURI rootVID = root.vid();
        Machine.write(f("~/person"), jnt(7));
        final fURI rootAddress = null == rootVID ? null : rootVID.extend(f("person")).resolve();

        final Machine child = root.push(f("diag"));
        final fURI childVID = child.vid();
        final fURI childAddress = null == childVID ? null : childVID.extend(f("person")).resolve();

        // the test logger prints the message verbatim (no slf4j {} substitution), so every line is concatenated
        LOG.info("TILDEDIAG rootVID=" + rootVID + " childVID=" + childVID + " currentVID=" + Machine.current().vid());
        LOG.info("TILDEDIAG tildeInChildResolvesTo=" + Machine.current().vid().extend(f("person")).resolve());
        LOG.info("TILDEDIAG read(~/person)=" + Machine.read(f("~/person"))
                + " read(/person)=" + Machine.read(rootAddress)
                + " read(childAddress)=" + (null == childAddress ? "n/a" : Machine.read(childAddress)));

        // THE FINDING, pinned as an invariant. `~` resolves against Machine.current().vid() and the result is then
        // an ORDINARY ABSOLUTE ADDRESS: there is no fall-through to an enclosing frame, so a parent's ~-addressed
        // value is INVISIBLE from a child. Inheritance therefore has to be BUILT — re-bind ~ at each level of the
        // walk, or walk frames in the projection — and does not come for free with the address walk. This test
        // must change when that lands, which is exactly its job.
        assertEquals(noobj(), Machine.read(f("~/person")),
                "in the child, ~/person resolves to this level's own /diag/person and MISSES — no fall-through");
        assertEquals(jnt(7), Machine.read(rootAddress),
                "the value is only at the address the WRITING frame's ~ resolved to: " + rootAddress);
    }
}
