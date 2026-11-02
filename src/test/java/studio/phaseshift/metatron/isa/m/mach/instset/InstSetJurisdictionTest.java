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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.mach.type.ComponentUnion;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.instset.BasicInstSet;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.INSTSET_TID;
import static studio.phaseshift.metatron.Tokens.PATTERN;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * INSTRUCTION SETS LIVE IN THE INSTSET (Marko, 2026-10-25): "Instsets shouldn't be in Memory as spaces (though they are
 * spaces). They should be in InstSet only." Memory's index holds none of them; its lookup weighs the libraries the
 * machine mounted against its data spaces, so "the most specific gains the write" — /m/math/cat/… goes to /m/math/cat,
 * /m/math/… to /m/math, the rest of /m/… to /m — and unmounting stops the routing but keeps the member. The
 * vocabulary's own pattern is the "lowest common denominator" of its members' ("thus /m/#").
 * <p>
 * The libraries here are throwaway instruction sets mounted inside a pushed frame, so the machine's real vocabulary is
 * never written and everything leaves with the frame.
 */
//@Isolated
//@Execution(ExecutionMode.SAME_THREAD)
public class InstSetJurisdictionTest extends AbstractMetatronTest {

    @AfterEach
    public void cleanup() {
        this.resetToRoot();
    }

    /**
     * a bare library, shaped as every JREService instset is: its own vid, its pattern beneath it
     */
    private static InstSet library(final String vid) {
        return new AbstractInstSet(mutableMap(uri(PATTERN), uri(f(vid).extend(ALL))), INSTSET_TID, f(vid)) {
        };
    }

    @Test
    public void testTheVocabularysPatternIsTheLowestCommonDenominator() {
        final BasicInstSet vocabulary = new BasicInstSet();
        vocabulary.refer(library("/t/lcd/alpha"));
        vocabulary.refer(library("/t/lcd/beta/gamma"));
        assertEquals(f("/t/lcd/#"), vocabulary.pattern(), "the deepest path every member lies beneath");
        vocabulary.refer(library("/u/elsewhere"));
        assertEquals(f("/#"), vocabulary.pattern(), "a member elsewhere widens it to the root");
        // the machine's own vocabulary holds every member it refers to
        final fURI jurisdiction = Machine.root().instset().pattern().retractPattern();
        Machine.root().instset().members().stream()
                .filter(member -> !(member instanceof ComponentUnion<?>) && null != member.vid())
                .forEach(member -> assertTrue(member.pattern().retractPattern().hasPrefix(jurisdiction),
                        member.vid() + " lies beneath the vocabulary's pattern " + jurisdiction));
    }

    @Test
    public void testTheMostSpecificInstSetGainsTheWrite() {
        final Machine child = Machine.current().push(f("isa_route"));
        try {
            final InstSet outer = library("/t/route");
            InstSet.mount(outer);
            final InstSet deep = library("/t/route/deep");
            InstSet.mount(deep);
            Machine.write(f("/t/route/deep/tally"), jnt(1));
            Machine.write(f("/t/route/tally"), jnt(2));
            checkEquality(LOG, jnt(1), deep.read(f("/t/route/deep/tally")), true);
            checkEquality(LOG, jnt(2), outer.read(f("/t/route/tally")), true);
            assertTrue(outer.read(f("/t/route/deep/tally")).isNoObj(), "the less specific instset never saw it");
            assertTrue(child.memory().findSpace(f("/t/route/deep/tally")) == deep,
                    "memory's lookup names the deeper instset: it routes the address, its index holds none of it");
            checkEquality(LOG, jnt(1), Machine.read(f("/t/route/deep/tally")), true);
            checkEquality(LOG, jnt(2), Machine.read(f("/t/route/tally")), true);
        } finally {
            child.pop();
        }
    }

    @Test
    public void testADataSpaceMoreSpecificThanAnInstSetKeepsItsAddresses() {
        final Machine child = Machine.current().push(f("isa_claim"));
        try {
            final InstSet claim = library("/t/claim");
            InstSet.mount(claim);
            // memory's own rule, kept: the more specific space answers, whichever kind it is
            final memSpace data = memSpace.of(f("/t/claim/data/#"), f("/t/claim/data/space"));
            Machine.write(f("/t/claim/data/tally"), jnt(3));
            checkEquality(LOG, jnt(3), data.read(f("/t/claim/data/tally")), true);
            assertTrue(claim.read(f("/t/claim/data/tally")).isNoObj(), "the instset does not take it");
            checkEquality(LOG, jnt(3), Machine.read(f("/t/claim/data/tally")), true);
        } finally {
            child.pop();
        }
    }

    @Test
    public void testAShortNameStillReachesItsInstruction() {
        final Machine machine = Machine.current();
        assertFalse(Machine.read(f("plus")).isNoObj(), "a short name reaches its instruction in its full spelling");
        assertTrue(machine.memory().findSpace(f("/m/inst/plus")) instanceof InstSet isa && f("/m").equals(isa.vid()),
                "memory's lookup names the instset holding the full spelling");
    }

    @Test
    public void testUnmountingStopsTheRoutingAndKeepsTheMember() {
        final Machine child = Machine.current().push(f("isa_unmount"));
        try {
            final InstSet lib = library("/t/unmount");
            InstSet.mount(lib);
            assertTrue(child.memory().findSpace(f("/t/unmount/tally")) == lib, "a mounted library is routed by address");
            child.memory().removeSpace(f("/t/unmount"));
            assertFalse(child.memory().findSpace(f("/t/unmount/tally")) == lib, "an unmounted one is not");
            assertTrue(child.instset().members().contains(lib), "but it is still a member of the vocabulary");
            InstSet.mount(lib);
            assertTrue(child.memory().findSpace(f("/t/unmount/tally")) == lib, "and a re-mount routes it again");
        } finally {
            child.pop();
        }
    }
}
