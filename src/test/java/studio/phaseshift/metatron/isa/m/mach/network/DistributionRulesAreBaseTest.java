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

package studio.phaseshift.metatron.isa.m.mach.network;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.Network;
import studio.phaseshift.metatron.isa.m.type.Bool;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static studio.phaseshift.metatron.Tokens.CODE;
import static studio.phaseshift.metatron.Tokens.M_ISA_REWRITE_TID;
import static studio.phaseshift.metatron.Tokens.PRE;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.BRANCH_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.REPEAT_INST_TID;
import static studio.phaseshift.metatron.isa.m.mach.machInstSet.MOVE_INST_TID;
import static studio.phaseshift.metatron.isa.m.mach.machInstSet.ROOT_MACHINE_VID;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MCode.code;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * THE DISTRIBUTION RULES ARE /m's (Marko, 2026-11-24). A machine that never imported {@code /m/math/cat} still has
 * {@code law_shard} and {@code law_bsp}, so a lane it is sent with hop budget left plans over its own peers: a
 * harvested node boots bare, and a server whose boot imports {@code /m/math} alone ({@code boot/boot.mtron}) ran every
 * lane it was sent flat. Nothing here imports the category; the peer is a {@code loop} transport standing in for
 * another machine.
 */
public class DistributionRulesAreBaseTest extends AbstractMetatronTest {

    private static final fURI SIBLING = f("loop://sibling:8555");

    @AfterEach
    void hangUp() {
        Machine.current().network().drop(SIBLING);
    }

    /**
     * the rewrite of the machine's pool named {@code name}
     */
    private static Inst rule(final String name) {
        return Machine.current().instset().rewrites().stream()
                .filter(rule -> name.equals(rule.tid().name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no rewrite named " + name + " in the machine's pool"));
    }

    /**
     * the chain as this machine's rewriter leaves it
     */
    private static Code planned(final String mtron) {
        return Machine.current().compiler().rewriter().apply(ObjmtronSerializer.parse(mtron).asCode()).asCode();
    }

    /**
     * where the chain first runs {@code op}, or -1
     */
    private static int indexOf(final Code code, final fURI op) {
        return IntStream.range(0, code.insts().size())
                .filter(i -> op.equals(code.insts().get(i).tid().basePath())).findFirst().orElse(-1);
    }

    /**
     * whether a stretch of the chain holds a branch, the form law_shard plans a run into
     */
    private static Bool branches(final List<Inst> insts) {
        return bool(insts.stream().anyMatch(inst -> BRANCH_INST_TID.equals(inst.tid().basePath())));
    }

    /**
     * Both rules are filed in /m's rewrite folder, ranked after every database pushdown.
     */
    @Test
    void testBothRulesAreBaseRewrites() {
        final Set<fURI> pool = Machine.current().instset().rewrites().stream()
                .map(rule -> rule.tid().basePath())
                .collect(Collectors.toSet());
        for (final String name : List.of("law_shard", "law_bsp")) {
            checkEquality(LOG, bool(true), bool(pool.contains(M_ISA_REWRITE_TID.extend(name))), true);
            assertEquals(List.of(f("/m/db/+/inst/rewrite")), rule(name).tid().qValue(PRE, List.class),
                    name + " runs after every database pushdown");
        }
    }

    /**
     * law_shard plans a fold over a dialed peer, and the fold still answers its value.
     */
    @Test
    void testLawShardPlansWithoutTheCategory() {
        Network.Helper.connect(Machine.current().network(), SIBLING);
        checkEquality(LOG, bool(true), branches(planned("{1,2,3,4}.plus(1).sum()").insts()), true);
        checkCodeParseApply(LOG, "{1,2,3,4}.plus(1).sum()", "14");
    }

    /**
     * law_bsp deals a whole-frontier repeat over a dialed peer: each pass's code is one branch.
     */
    @Test
    void testLawBspDealsWithoutTheCategory() {
        Network.Helper.connect(Machine.current().network(), SIBLING);
        final Code written =
                ObjmtronSerializer.parse("{1,2,3}.repeat?<=int{*}(code=>plus(1),until=>loop()?>3)").asCode();
        final Code dealt = rule("law_bsp").apply(written).asCode();
        final List<Inst> pass = dealt.insts().get(indexOf(dealt, REPEAT_INST_TID)).arg(f(CODE), 1).asCall().insts();
        checkEquality(LOG, jnt(1), jnt(pass.size()), true);
        checkEquality(LOG, bool(true), branches(pass), true);
    }

    /**
     * law_bsp deals only what runs here: a whole-frontier repeat past a move away is the other machine's to deal.
     */
    @Test
    void testLawBspDealsOnlyWhatRunsHere() {
        Network.Helper.connect(Machine.current().network(), SIBLING);
        final Code written = ObjmtronSerializer
                .parse("{1,2,3}.move(<a>).repeat?<=int{*}(code=>plus(1),until=>loop()?>3)").asCode();
        checkEquality(LOG, written, rule("law_bsp").apply(written), true);
    }

    /**
     * A FOLD BEFORE A MOVE AWAY IS PLANNED, AND THE REST IS LEFT AS WRITTEN: the move and everything after it are the
     * machine it lands on's to re-plan over its own peers.
     */
    @Test
    void testLawShardPlansUpToAMoveAway() {
        Network.Helper.connect(Machine.current().network(), SIBLING);
        final String mtron = "{1,2,3,4}.plus(1).sum().move(<..>).plus(1)";
        final Code written = ObjmtronSerializer.parse(mtron).asCode();
        final Code planned = planned(mtron);
        final int move = indexOf(planned, MOVE_INST_TID);
        checkEquality(LOG, bool(true), branches(planned.insts().subList(0, move)), true);
        checkEquality(LOG, code(written.insts().subList(indexOf(written, MOVE_INST_TID), written.insts().size())),
                code(planned.insts().subList(move, planned.insts().size())), true);
    }

    /**
     * A FRAME CHANGE BEFORE THE FOLD LEAVES THE WHOLE CHAIN AS WRITTEN: a move up, a move down and a push each hand the
     * fold to another machine, so nothing of it is planned here (MoveSeamsTest's and MoveRemoteTest's chains failed
     * this way, shipping their tails over peers the machine they moved to never dialed).
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "{1,2,3,4}.move(<..>).plus(1).sum()",
            "{1,2,3,4}.move(<a>).plus(1).sum()",
            "{1,2,3,4}.push(<a>).plus(1).sum()"})
    void testAFoldPastAFrameChangeIsNotPlannedHere(final String mtron) {
        Network.Helper.connect(Machine.current().network(), SIBLING);
        checkEquality(LOG, ObjmtronSerializer.parse(mtron).asCode(), planned(mtron), true);
    }

    /**
     * A CUT'S PAYLOAD IS RE-PLANNED WHERE IT LANDS: a move to another machine ships
     * {@code start(lhs).move(</.>).<tail>}, and at that machine's root the re-home stays where it is, so the tail is
     * dealt over the peers that machine dialed (the quine rule).
     */
    @Test
    void testACutsPayloadIsPlannedWhereItLands() {
        Network.Helper.connect(Machine.current().network(), SIBLING);
        final Code planned = planned("start({1,2,3,4}).move(</.>).plus(1).sum()");
        final int move = indexOf(planned, MOVE_INST_TID);
        checkEquality(LOG, uri(ROOT_MACHINE_VID), planned.insts().get(move).arg(0), true);
        checkEquality(LOG, bool(true), branches(planned.insts().subList(move + 1, planned.insts().size())), true);
    }
}
