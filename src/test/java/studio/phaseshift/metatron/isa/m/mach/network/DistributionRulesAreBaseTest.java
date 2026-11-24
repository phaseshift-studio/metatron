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
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.Network;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.CODE;
import static studio.phaseshift.metatron.Tokens.M_ISA_REWRITE_TID;
import static studio.phaseshift.metatron.Tokens.PRE;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.BRANCH_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.REPEAT_INST_TID;
import static studio.phaseshift.metatron.isa.m.mach.machInstSet.MOVE_INST_TID;

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
     * Both rules are filed in /m's rewrite folder, ranked after the local laws and after every database pushdown.
     */
    @Test
    void testBothRulesAreBaseRewrites() {
        final Set<fURI> pool = Machine.current().instset().rewrites().stream()
                .map(rule -> rule.tid().basePath())
                .collect(Collectors.toSet());
        for (final String name : List.of("law_shard", "law_bsp")) {
            assertTrue(pool.contains(M_ISA_REWRITE_TID.extend(name)), () -> name + " is a rewrite of /m: " + pool);
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
        final Code fold = ObjmtronSerializer.parse("{1,2,3,4}.plus(1).sum()").asCode();
        final DistributedSweep.Layout layout =
                DistributedSweep.layout(Machine.current().compiler().rewriter().apply(fold).asCode());
        assertFalse(layout.lanes().isEmpty(), () -> "the fold is planned across the peer: " + layout.emitted());
        checkCodeParseApply(LOG, "{1,2,3,4}.plus(1).sum()", "14");
    }

    /**
     * law_bsp deals a whole-frontier repeat over a dialed peer: each pass's code is a branch.
     */
    @Test
    void testLawBspDealsWithoutTheCategory() {
        Network.Helper.connect(Machine.current().network(), SIBLING);
        final Obj dealt = rule("law_bsp")
                .apply(ObjmtronSerializer.parse("{1,2,3}.repeat?<=int{*}(code=>plus(1),until=>loop()?>3)").asCode());
        assertTrue(dealt.isCode(), () -> "law_bsp answers code: " + dealt);
        final Inst repeat = dealt.asCode().insts().stream()
                .filter(inst -> REPEAT_INST_TID.equals(inst.tid().basePath())).findFirst().orElseThrow();
        final List<Inst> code = repeat.arg(f(CODE), 1).asCall().insts();
        assertEquals(1, code.size(), () -> "the pass's code is one branch: " + code);
        assertEquals(BRANCH_INST_TID, code.getFirst().tid().basePath(), () -> "the pass's code is a branch: " + code);
    }

    /**
     * A MOVE HANDS THE REST OF THE CHAIN TO ANOTHER MACHINE: law_shard plans the fold before it and leaves the move and
     * everything after it as written, for the machine it lands on to re-plan over its own peers.
     */
    @Test
    void testLawShardPlansOnlyUpToAMove() {
        Network.Helper.connect(Machine.current().network(), SIBLING);
        final Code planned = Machine.current().compiler().rewriter()
                .apply(ObjmtronSerializer.parse("{1,2,3,4}.plus(1).sum().move(<..>).plus(1)").asCode()).asCode();
        final List<Inst> insts = planned.insts();
        final int move = IntStream.range(0, insts.size())
                .filter(i -> MOVE_INST_TID.equals(insts.get(i).tid().basePath())).findFirst().orElse(-1);
        assertTrue(0 < move, () -> "the move survives: " + planned);
        assertTrue(insts.subList(0, move).stream().anyMatch(inst -> BRANCH_INST_TID.equals(inst.tid().basePath())),
                () -> "the fold before the move is planned across the peer: " + planned);
        assertTrue(insts.subList(move, insts.size()).stream()
                        .noneMatch(inst -> BRANCH_INST_TID.equals(inst.tid().basePath())),
                () -> "nothing after the move is planned here: " + planned);
    }
}
