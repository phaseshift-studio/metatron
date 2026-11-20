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
import studio.phaseshift.metatron.furi.c.cInt;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * THE TOPOLOGY DRAWING, pinned against the one Marko sketched (2026-11-20): fifteen objs dealt to three peers as ten, two
 * and three, each lane folded to one, the three merged at home and folded to one. The sharded rows of
 * {@code DistributedComputingTest} read the coefficients a real plan leaves; this pins the drawing itself: an odd fan
 * puts its middle lane on the main line, and each column is padded to its widest coefficient.
 */
public class TopologyTest extends AbstractMetatronTest {

    @Training.SkipTraining(reason = "a Java-side drawing of hand-built lanes, not an mtron expression")
    @Test
    public void testTheDrawingMarkoSketched() {
        final String drawing = Topology.draw(List.of(
                new Topology.Run("{15}"),
                new Topology.Fan(List.of(
                        new Topology.Lane("//0", null, List.of(cInt.of(10), cInt.ONE())),
                        new Topology.Lane("//1", null, List.of(cInt.of(2), cInt.ONE())),
                        new Topology.Lane("//2", null, List.of(cInt.of(3), cInt.ONE())))),
                new Topology.Run("──//local─►{3}──{1}")));
        assertEquals(String.join("\n", "",
                "      ┌──//0─►{10}──{1}─┐",
                "{15}──┼──//1─►{2}───{1}─┼──//local─►{3}──{1}",
                "      └──//2─►{3}───{1}─┘", ""), drawing);
    }
}
