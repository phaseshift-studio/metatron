/*
 * metatron: a distributed virtual machine and language
 *  Copyright (C) 2025- PhaseShift Studio, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.algebra.rewrite.plan;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mach.type.Network;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Rel;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.BRANCH_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.EVAL_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.REPEAT_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * law_bsp, THE SUPERSTEP RULE (Marko, 2026-11-22): a whole-frontier repeat ({@code repeat?<=A{*}}) whose code is pure
 * and floatable deals its frontier over the dialed peers every pass. Planned against a synthetic environment of two
 * dialed peers that serve {@code eval} (written the way the network writes a peer, as PlaceResolverTest does), so no
 * peer is dialed or forked: the rule is a function of the code and the snapshot.
 */
public class BspRuleTest extends AbstractMetatronTest {

    private static final fURI ALPHA = f("loop://alpha:8555");
    private static final fURI BETA = f("loop://beta:8555");
    private static final fURI MINE = f("loop://mantis:8555");

    @BeforeAll
    public static void importAllInstSets() {
        // the wildcard import declares the schemes' dialers, the declared half of the liveness a peer is admitted by
        InstSet.importInstSet(f("#"));
    }

    private static Code program(final String mtron) {
        return ObjmtronSerializer.parse(mtron).asCode();
    }

    private static Env dialed(final fURI... origins) {
        final Map<Obj, Obj> dials = new LinkedHashMap<>();
        for (int i = 0; i < origins.length; i++)
            dials.put(uri("dial/" + i), peer(origins[i]));
        return Env.of(rec(dials), rec(), Set.of(MINE));
    }

    /**
     * A dialed, live peer that serves {@code eval}: its origin, its host (it was dialed) and the client it holds.
     */
    private static Obj peer(final fURI origin) {
        final Rec overrides = rec();
        overrides.jvm().put(uri(EVAL_INST_TID), Network.Helper.override(EVAL_INST_TID, (lhs, inst) -> inst.arg(0).apply(lhs)));
        final Map<Obj, Obj> client = new LinkedHashMap<>();
        client.put(uri(HOST), uri(origin));
        client.put(uri(INST), overrides);
        client.put(uri(STATUS), Network.Helper.statusOf(() -> true));
        final Map<Obj, Obj> member = new LinkedHashMap<>();
        member.put(uri(ORIGIN), uri(origin));
        member.put(uri(HOST), uri(origin));
        member.put(uri(CLIENT), new MRec(client, REC_TID, null));
        return new MRec(member, REC_TID, null);
    }

    private static Inst repeatOf(final Code code) {
        return code.insts().stream().filter(i -> REPEAT_INST_TID.equals(i.tid().basePath())).findFirst().orElseThrow();
    }

    /**
     * THE DEAL: the repeat still gathers its frontier, and its code is now a branch that gathers each pass's frontier
     * and parts it over the two peers, each lane the code as written with {@code ?hop=0}, so a peer runs it and plans
     * nothing further. until is untouched: the loop stays home.
     */
    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource(value = {
            "{1,2,3}.repeat?<=int{*}(code=>plus(1),until=>loop()?>3)          % plus",
            // THE GRAPH WALK (Marko, 2026-11-22): a space read is untyped (#), and both() is declared pure, so it deals
            "*g:V/+.repeat?<=A{*}(code=>both(),until=>loop()?>4)              % both",
    }, delimiter = '%')
    public void testAWholeFrontierRepeatDealsItsCodeOverThePeers(final String mtron, final String step) {
        final Code before = program(mtron.trim());
        final Code after = Bsp.apply(before, dialed(ALPHA, BETA));
        LOG.debug("law_bsp: %s  |->  %s", before, after);
        final Inst repeat = repeatOf(after);
        assertTrue(repeat.isGather(), "the repeat still gathers its frontier: " + repeat);
        final List<Inst> code = repeat.arg(f(CODE), 1).asCall().insts();
        assertEquals(1, code.size(), "the code is one branch: " + code);
        final Inst branch = code.getFirst();
        assertEquals(BRANCH_INST_TID, branch.tid().basePath(), "the code is a branch: " + branch);
        assertTrue(branch.isGather(), "the branch gathers each pass's frontier: " + branch);
        assertEquals(PART, branch.tid().q(SPLIT), "the branch parts the frontier: " + branch);
        final List<Rel> lanes = branch.args().asRec().elements().toList();
        assertEquals(2, lanes.size(), "one lane per dialed peer: " + lanes);
        for (final Rel lane : lanes) {
            final Inst head = lane.second().asCall().insts().getFirst();
            assertEquals(f(step.trim()), f(head.tid().name()), "each lane runs the code as written: " + lane);
            assertEquals("0", head.tid().q(HOP), "each lane plans nothing further: " + lane);
        }
        assertEquals(repeatOf(before).arg(f(UNTIL), 2).toString(), repeat.arg(f(UNTIL), 2).toString(), "until stays home, as written");
    }

    /**
     * WHAT IS NOT DEALT IS LEFT AS WRITTEN: a per-monad repeat has no frontier to deal; a fold answers a partial per
     * lane (dealt, {1,2,3}.sum() would be two partials, not 6); an effect stays home; a repeat that states ?hop=0 opts
     * out.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "{1,2,3}.repeat(code=>plus(1),until=>loop()?>3)",
            "{1,2,3}.repeat?<=int{*}(code=>sum(),until=>loop()?>0)",
            "{1,2,3}.repeat?<=int{*}(code=>plus(1).print(_),until=>loop()?>3)",
            "{1,2,3}.repeat?<=int{*}&hop=0(code=>plus(1),until=>loop()?>3)",
    })
    public void testWhatIsNotDealtIsLeftAsWritten(final String mtron) {
        final Code before = program(mtron);
        assertSame(before, Bsp.apply(before, dialed(ALPHA, BETA)), "left as written: " + mtron);
    }

    /**
     * NO PEER, NO DEAL: a machine with nothing dialed keeps its frontier home. The roster is never dealt to: a frontier
     * goes only where a connection already stands.
     */
    @Test
    public void testNoDialedPeerNoDeal() {
        final Code before = program("{1,2,3}.repeat?<=int{*}(code=>plus(1),until=>loop()?>3)");
        assertSame(before, Bsp.apply(before, dialed()));
    }
}
