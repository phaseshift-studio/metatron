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

package studio.phaseshift.metatron.algebra.rewrite.plan;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.Training;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;


import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * THE TOPOLOGY DRAWING, pinned against the one Marko sketched (2026-11-20): fifteen objs dealt to three peers as ten, two
 * and three, each lane folded to one, the three merged at home and folded to one. The sharded rows of
 * {@code DistributedComputingTest} read the coefficients a real plan leaves; this pins the drawing itself: an odd fan
 * puts its middle lane on the main line, and each column is padded to its widest coefficient.
 */
public class TopologyTest extends AbstractMetatronTest {

    @Training.SkipTraining(reason = "a Java-side drawing of a hand-built flow, not an mtron expression")
    @Test
    public void testTheDrawingMarkoSketched() {
        final String drawing = Topology.draw(ObjmtronSerializer.parse("[['{15}'],"
                + "[[place=>'//0',stage=>['{10}','{1}']],[place=>'//1',stage=>['{2}','{1}']],[place=>'//2',stage=>['{3}','{1}']]],"
                + "['{3}','{1}']]").asLst());
        assertEquals(String.join("\n", "",
                "      ┌─//0─►{10}─{1}─┐",
                "{15}──┼─//1─►{2}──{1}─┼──//local─►{3}─{1}",
                "      └─//2─►{3}──{1}─┘", ""), drawing);
    }

    /**
     * A CHILD DRAWS ITS OWN SUBTREE: a lane whose peer re-planned it carries the peer's topology, a flow like any other,
     * and its drawing goes below the root's, under the peer's name and indented.
     */
    @Training.SkipTraining(reason = "a Java-side drawing of a hand-built flow, not an mtron expression")
    @Test
    public void testAChildDrawsItsOwnSubtreeUnderItsName() {
        final String drawing = Topology.draw(ObjmtronSerializer.parse("[['{8}'],"
                + "[[place=>'//0',stage=>['{4}','{1}'],topology=>[flow=>[['{4}'],"
                + "[[place=>'//2',stage=>['{2}','{1}']],[place=>'//3',stage=>['{2}','{1}']]],['{2}','{1}']]]],"
                + "[place=>'//1',stage=>['{4}','{1}']]],"
                + "['{2}','{1}']]").asLst());
        assertEquals(String.join("\n", "",
                "     ┌─//0─►{4}─{1}─┐",
                "{8}──┤              ├──//local─►{2}─{1}",
                "     └─//1─►{4}─{1}─┘",
                "//0:",
                "        ┌─//2─►{2}─{1}─┐",
                "   {4}──┤              ├──//local─►{2}─{1}",
                "        └─//3─►{2}─{1}─┘", ""), drawing);
    }
}
