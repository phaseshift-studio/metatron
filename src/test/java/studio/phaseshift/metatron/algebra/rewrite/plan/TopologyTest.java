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
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.Network;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.FLOW;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * THE TOPOLOGY DRAWING, pinned against the one Marko sketched (2026-11-20): fifteen objs dealt to three peers as ten,
 * two and three, each lane folded to one, the three merged at home and folded to one. The sharded rows of
 * {@code DistributedComputingTest} read the coefficients a real plan leaves; this pins the drawing itself: an odd fan
 * puts its middle lane on the main line, and each column is padded to its widest coefficient.
 */
public class TopologyTest extends AbstractMetatronTest {

    @Training.SkipTraining(reason = "a Java-side drawing of a hand-built flow, not an mtron expression")
    @Test
    public void testTheDrawingMarkoSketched() {
        final String drawing = Topology.draw(ObjmtronSerializer.parse("[['{15}'],"
                + "[[place=>'//0',stage=>['{10}','{1}']],[place=>'//1',stage=>['{2}','{1}']],"
                + "[place=>'//2',stage=>['{3}','{1}']]],"
                + "['{3}','{1}']]").asLst());
        assertEquals(String.join("\n", "",
                "      ┌─►//0─{10}─{1}─┐",
                "{15}──┼─►//1─{2}──{1}─┼─►//local─{3}─{1}",
                "      └─►//2─{3}──{1}─┘", ""), drawing);
    }

    /**
     * A TREE WITHIN THE TREE (Marko, 2026-11-20): a lane whose peer re-planned it carries the peer's topology, a flow
     * like any other, drawn inside the lane, its merge at the lane's place (//0). The fan stacks lanes of any height:
     * each joins the fork and the join on its own main row, the rows between the outer lanes carry a │, and the shorter
     * lane runs out to the join.
     */
    @Training.SkipTraining(reason = "a Java-side drawing of a hand-built flow, not an mtron expression")
    @Test
    public void testAChildsSubtreeIsDrawnInsideItsLane() {
        final String drawing = Topology.draw(ObjmtronSerializer.parse("[['{8}'],"
                + "[[place=>'//0',stage=>['{4}','{1}'],topology=>[flow=>[['{4}'],"
                + "[[place=>'//2',stage=>['{2}','{1}']],[place=>'//3',stage=>['{2}','{1}']]],['{2}','{1}']]]],"
                + "[place=>'//1',stage=>['{4}','{1}']]],"
                + "['{2}','{1}']]").asLst());
        assertEquals(String.join("\n", "",
                "                 ┌─►//2─{2}─{1}─┐",
                "     ┌─►//0─{4}──┤              ├─►//0─{2}─{1}─┐",
                "     │           └─►//3─{2}─{1}─┘              │",
                "{8}──┤                                         ├─►//local─{2}─{1}",
                "     └─►//1─{4}─{1}────────────────────────────┘", ""), drawing);
    }

    /**
     * A PLACE IS NAMED WHERE THE DRAWING IS MADE (Marko, 2026-11-20: //0, not //localhost:1111): explain() keeps a
     * lane's address in the flow, and the drawing names it by this machine's network prefixes. The prefix is registered
     * after the explanation is made, so only an address could take it: a peer named zero reads //zero, and a peer the
     * network names nothing keeps its authority.
     */
    @Training.SkipTraining(reason = "an explanation drawn by a prefix the test registers after it is made")
    @Test
    public void testAPlaceIsNamedWhereTheDrawingIsMade() {
        final Obj flow = ObjmtronSerializer.eval("branch?<={0}(<ws://localhost:1111> => start(1).plus(1),"
                + " <ws://localhost:1112> => start(1).plus(1)).explain()>>ext/topology/flow");
        final Network network = Machine.current().network();
        network.registerPrefix(f("zero"), f("ws://localhost:1111"));
        try {
            assertEquals(String.join("\n", "",
                    "     ┌─►//zero───────────{1}─{1}─┐",
                    "{1}──┤                           ├─►//local─{2}",
                    "     └─►//localhost:1112─{1}─{1}─┘", ""), Topology.draw(flow.asLst()));
        } finally {
            network.unregisterPrefix(f("zero"));
        }
    }

    /**
     * A LANE IS DRAWN AS ITS MACHINE PLANNED IT (the background reviews, 2026-11-20). A re-planned lane stacks in the
     * middle of an odd fan, a │ above and below it, and a lane its machine could not explain carries the message under
     * its stages. An address inside a subtree means what it meant to the machine that planned it: its home lane is that
     * machine (//g, not //local), and its localhost is that machine's host, so ws://localhost:1113 inside ginger's
     * subtree is ginger's port 1113 (//g3), not this machine's (//l3).
     */
    @Training.SkipTraining(reason = "a Java-side drawing of a hand-built flow, named by prefixes the test registers")
    @Test
    public void testALaneIsDrawnAsItsMachinePlannedIt() {
        final Network network = Machine.current().network();
        final Map<fURI, fURI> prefixes = Map.of(f("b"), f("ws://localhost:1112"), f("g"), f("ws://ginger:8555"),
                f("g3"), f("ws://ginger:1113"), f("l3"), f("ws://localhost:1113"), f("d"), f("ws://localhost:1114"));
        prefixes.forEach(network::registerPrefix);
        try {
            final String drawing = Topology.draw(ObjmtronSerializer.parse("[['{6}'],["
                    + "[place=><ws://localhost:1112>,stage=>['{2}','{1}']],"
                    + "[place=><ws://ginger:8555>,stage=>['{2}','{1}'],topology=>[flow=>[['{2}'],"
                    + "[[place=><~>,stage=>['{1}','{1}']],[place=><ws://localhost:1113>,stage=>['{1}','{1}']]],"
                    + "['{2}','{1}']]]],"
                    + "[place=><ws://localhost:1114>,stage=>['{2}','{1}'],topology=>'unreachable']],"
                    + "['{3}','{1}']]").asLst());
            assertEquals(String.join("\n", "",
                    "     ┌─►//b─{2}─{1}" + "─".repeat(29) + "┐",
                    "     │           ┌─►//g──{1}─{1}─┐              │",
                    "{6}──┼─►//g─{2}──┤               ├─►//g─{2}─{1}─┼─►//local─{3}─{1}",
                    "     │           └─►//g3─{1}─{1}─┘              │",
                    "     └─►//d─{2}─{1}" + "─".repeat(29) + "┘",
                    "            unreachable", ""), drawing);
        } finally {
            prefixes.keySet().forEach(network::unregisterPrefix);
        }
    }

    /**
     * A FAN OF NO LANES (a branch keyed by nothing) still sits on the main line: its seed runs through to the merge,
     * which takes none.
     */
    @Training.SkipTraining(reason = "a Java-side drawing of a hand-built flow, not an mtron expression")
    @Test
    public void testAFanOfNoLanes() {
        assertEquals(String.join("\n", "", "{8}───►//local─{0}", ""),
                Topology.draw(ObjmtronSerializer.parse("[['{8}'],[,],['{0}']]").asLst()));
    }

    /**
     * A REPEAT IS A LOOP (Marko, 2026-11-22): its body is drawn once, as any flow, and a back edge runs from where the
     * body leaves (┬ on the right, where the main line also goes on) down, back under the body, and up into where it
     * enters (▲ under the ┬ on the left). This body is a pass that holds a cut: the frontier dealt over //0 and //1,
     * merged home and folded there ({1}) before the next pass. law_bsp never writes one (its pass is the fan alone,
     * below), but a hand-written branch with a tail at home draws like this.
     */
    @Training.SkipTraining(reason = "a Java-side drawing of a hand-built flow, not an mtron expression")
    @Test
    public void testARepeatIsDrawnWithItsBackEdge() {
        final String drawing = Topology.draw(ObjmtronSerializer.parse("[['{4}'],"
                + "[loop=>[[[place=>'//0',stage=>['{2}','{1}']],[place=>'//1',stage=>['{2}','{1}']]],['{2}','{1}']]],"
                + "['{2}']]").asLst());
        assertEquals(String.join("\n", "",
                "       ┌─►//0─{2}─{1}─┐",
                "{4}─┬──┤              ├─►//local─{2}─{1}─┬─{2}",
                "    ▲  └─►//1─{2}─{1}─┘                  │",
                "    └────────────────────────────────────┘", ""), drawing);
    }

    /**
     * A ONE-ROW BODY STILL SHOWS WHICH WAY IT RUNS: a body of one lane is a single row, so the loop gives it a row
     * under its main line, where the ▲ stands.
     */
    @Training.SkipTraining(reason = "a Java-side drawing of a hand-built flow, not an mtron expression")
    @Test
    public void testAOneRowBodyShowsWhichWayItRuns() {
        final String drawing = Topology.draw(ObjmtronSerializer.parse("[['{4}'],"
                + "[loop=>[[[place=>'//0',stage=>['{4}','{4}']]],['{4}']]],"
                + "['{4}']]").asLst());
        assertEquals(String.join("\n", "",
                "{4}─┬────►//0─{4}─{4}───►//local─{4}─┬─{4}",
                "    ▲" + " ".repeat(32) + "│",
                "    └" + "─".repeat(32) + "┘", ""), drawing);
    }

    /**
     * A BODY THAT STARTS AT HOME joins the loop's entry through a dash, as a fan's own ── joins its fork: here the pass
     * runs one instruction at home ({3}) before it deals.
     */
    @Training.SkipTraining(reason = "a Java-side drawing of a hand-built flow, not an mtron expression")
    @Test
    public void testABodyThatStartsAtHomeJoinsTheEntryThroughADash() {
        final String drawing = Topology.draw(ObjmtronSerializer.parse("[['{3}'],"
                + "[loop=>[['{3}'],[[place=>'//0',stage=>['{3}','{3}']],[place=>'//1',stage=>['{3}','{3}']]],['{6}']]],"
                + "['{6}']]").asLst());
        assertEquals(String.join("\n", "",
                "           ┌─►//0─{3}─{3}─┐",
                "{3}─┬─{3}──┤              ├─►//local─{6}─┬─{6}",
                "    ▲      └─►//1─{3}─{3}─┘" + " ".repeat(14) + "│",
                "    └" + "─".repeat(36) + "┘", ""), drawing);
    }

    /**
     * THE LOOP IS READ OFF THE CHAIN, its pass as law_bsp writes it: a repeat whose code is a branch dealing the
     * frontier over two places, each lane running f where it points, explains to a flow with a loop segment around the
     * fan. Each member reaches a lane at most once ({3} times {?}), the lanes add where they merge, and the repeat
     * answers any number ({*}).
     */
    @Test
    public void testARepeatThatDealsExplainsToALoop() {
        final Obj topology = Topology.explain(ObjmtronSerializer.parse("{1,2,3}.repeat?<=int{*}(code=>"
                + "branch?<=A{*}&split=part(<//a> => plus?hop=0(1), <//b> => plus?hop=0(1)),"
                + "until=>loop()?>3)").asCode());
        assertTrue(topology.isRec(), "a dealing repeat has a topology: " + topology);
        assertEquals(String.join("\n", "",
                "       ┌─►//a─{0,3}─{0,3}─┐",
                "{3}─┬──┤                  ├─►//local─{0,6}─┬─{*}",
                "    ▲  └─►//b─{0,3}─{0,3}─┘                │",
                "    └──────────────────────────────────────┘", ""),
                Topology.draw(topology.asRec().at(uri(FLOW)).asLst()));
    }

    /**
     * A REPEAT THAT DEALS NOTHING RUNS IN ONE PLACE, so its chain has no topology: a loop alone is not a fan.
     */
    @Test
    public void testARepeatThatDealsNothingHasNoTopology() {
        checkEquality(LOG, noobj(), Topology.explain(ObjmtronSerializer.parse(
                "{1,2,3}.repeat?<=int{*}(code=>plus(1),until=>loop()?>3)").asCode()), true);
    }
}
