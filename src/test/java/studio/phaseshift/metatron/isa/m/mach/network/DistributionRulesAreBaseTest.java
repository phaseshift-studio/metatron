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
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.algebra.rewrite.plan.Shard;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.Network;
import studio.phaseshift.metatron.isa.m.type.Bool;
import studio.phaseshift.metatron.isa.m.type.Call;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.CODE;
import static studio.phaseshift.metatron.Tokens.M_ISA_REWRITE_TID;
import static studio.phaseshift.metatron.Tokens.PRE;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.BRANCH_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.REPEAT_INST_TID;
import static studio.phaseshift.metatron.isa.m.mach.machInstSet.CUT_INST_TID;
import static studio.phaseshift.metatron.isa.m.mach.machInstSet.MOVE_INST_TID;
import static studio.phaseshift.metatron.isa.m.mach.machInstSet.POP_INST_TID;
import static studio.phaseshift.metatron.isa.m.mach.machInstSet.PUSH_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
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

    /**
     * the instructions that change the current machine
     */
    private static final Set<fURI> NOMADIC = Set.of(MOVE_INST_TID, PUSH_INST_TID, POP_INST_TID);

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
     * A MACHINE CHANGE IS A COMPILATION BOUNDARY ({@code Law.nomadic}): the nomadic extractor cuts the chain at its
     * first move, push or pop, ahead of every rule, so neither law_shard nor law_bsp sees the tail. A move becomes the
     * cut to its target; a push or a pop stays and is followed by the cut to {@code ~}. The tail is the cut's argument,
     * exactly as written, and is compiled where it lands. Each row: the chain % its compiled form's instructions before
     * the cut % the cut's target.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "{1,2,3,4}.move(<..>).plus(1).sum()                                % 1 % ..",
            "{1,2,3,4}.move(<a>).plus(1).sum()                                 % 1 % a",
            "{1,2,3,4}.push(<a>).plus(1).sum()                                 % 2 % ~",
            "{1,2,3,4}.pop().plus(1).sum()                                     % 2 % ~",
            "{1,2,3}.move(<a>).repeat?<=int{*}(code=>plus(1),until=>loop()?>3) % 1 % a",
            // a bare move is a move up
            "{1,2,3,4}.move().plus(1).sum()                                    % 1 % ..",
            // the extractor runs first: a tail no rule of this machine has touched
            "5.move(<a>).plus(0)                                               % 1 % a",
            // a move to ~ is stay(), and a target computed when the move runs is not known here: the move stays
            "{1,2,3,4}.move(<~>).plus(1).sum()                                 % 2 % ~",
            "{1,2,3,4}.move(_).plus(1).sum()                                   % 2 % ~",
    }, delimiter = '%')
    void testAMachineChangeCutsTheChainAsWritten(final String mtron, final int kept, final String target) {
        Network.Helper.connect(Machine.current().network(), SIBLING);
        final Code written = ObjmtronSerializer.parse(mtron.trim()).asCode();
        final Code planned = planned(mtron.trim());
        // the tail is everything after the first nomadic instruction, as written
        final int nomadic = IntStream.range(0, written.insts().size())
                .filter(i -> NOMADIC.contains(written.insts().get(i).tid().basePath())).findFirst().orElseThrow();
        checkEquality(LOG, jnt(kept + 1), jnt(planned.insts().size()), true);
        checkEquality(LOG, code(written.insts().subList(0, kept)), code(planned.insts().subList(0, kept)), true);
        final Inst cut = planned.insts().get(kept);
        checkEquality(LOG, uri(CUT_INST_TID), uri(cut.tid().basePath()), true);
        checkEquality(LOG, uri(target.trim()), cut.arg(0), true);
        final Code tail = code(written.insts().subList(nomadic + 1, written.insts().size()));
        checkEquality(LOG, tail.tryToInst(), cut.arg(1), true);
    }

    /**
     * A RESOLVED CHAIN IS CUT AT THE SAME PLACE: the console resolved a line before compiling it, and a resolved tid
     * carries its range and domain, so the extractor matches a nomadic instruction by its base path. Each row: the
     * chain % its compiled form's instructions before the cut % the cut's target.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "5.move(<..>).plus(1).mult(2) % 1 % ..",
            "5.push(<a>).plus(1).mult(2)  % 2 % ~",
    }, delimiter = '%')
    void testAResolvedChainIsCutAtTheSamePlace(final String mtron, final int kept, final String target) {
        final Code resolved = ObjmtronSerializer.parse(mtron.trim()).asCode().resolve(noobj()).asCode();
        // the console's input: the machine change is bound, and its tid carries the range and domain that hid it
        final Inst nomadic = resolved.insts().stream()
                .filter(inst -> NOMADIC.contains(inst.tid().basePath())).findFirst().orElseThrow();
        assertTrue(nomadic.isResolved(false) && nomadic.hasDomOrRng(), "resolution binds the machine change: " + nomadic.tid());
        final Code planned = Machine.current().compiler().rewriter().apply(resolved).asCode();
        checkEquality(LOG, jnt(kept + 1), jnt(planned.insts().size()), true);
        final Inst cut = planned.insts().get(kept);
        checkEquality(LOG, uri(CUT_INST_TID), uri(cut.tid().basePath()), true);
        checkEquality(LOG, uri(target.trim()), cut.arg(0), true);
    }

    /**
     * A NOMADIC INSTRUCTION THAT ENDS THE CHAIN has no tail and stays as written, except a move to a host: the move
     * instruction never leaves this server, so the move becomes a cut with nothing to run where it lands. Each row:
     * the chain % whether it ends in a cut.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "5.move(<loop://sibling:8555>) % true",
            "5.move(<..>)                  % false",
            "5.move(<~>)                   % false",
            "5.move(_)                     % false",
            "5.push(<a>)                   % false",
            "5.pop()                       % false",
    }, delimiter = '%')
    void testANomadicInstructionThatEndsTheChain(final String mtron, final boolean cuts) {
        Network.Helper.connect(Machine.current().network(), SIBLING);
        final Code written = ObjmtronSerializer.parse(mtron.trim()).asCode();
        final Code planned = planned(mtron.trim());
        checkEquality(LOG, jnt(written.insts().size()), jnt(planned.insts().size()), true);
        final Inst last = planned.insts().getLast();
        checkEquality(LOG, uri(cuts ? CUT_INST_TID : written.insts().getLast().tid().basePath()), uri(last.tid().basePath()), true);
        if (cuts) {
            checkEquality(LOG, uri(SIBLING), last.arg(0), true);
            checkEquality(LOG, jnt(0), jnt(last.arg(1).asCall().insts().size()), true);
        }
    }

    /**
     * THE COMPILER LEAVES A CUT'S TAIL TO ITS TARGET: compiled here, the cut is bound and its tail is not, and the chain
     * has nothing left open here (a cut's tail is not this machine's to resolve), so the console reports no dynamic
     * resolution for a move.
     */
    @Test
    void testTheCompilerLeavesACutsTailToItsTarget() {
        final Code compiled = Machine.current().compiler().apply(ObjmtronSerializer.parse("{1,2,3,4}.move(<..>).plus(1).sum()")).asCode();
        final Inst cut = compiled.insts().getLast();
        checkEquality(LOG, uri(CUT_INST_TID), uri(cut.tid().basePath()), true);
        checkEquality(LOG, bool(true), bool(cut.isResolved(false)), true);
        checkEquality(LOG, bool(false), bool(cut.arg(1).isResolved(false)), true);
        checkEquality(LOG, jnt(0), jnt(Call.Helper.getUnresolvedInsts(compiled).size()), true);
    }

    /**
     * THE RETURN HOME (Marko, 2026-11-24): {@code explain()} and {@code profile()} end the stretch a machine change
     * cuts off and stay after the cut, so they analyze it where the expression began. A cut is nomadic itself, so a
     * chain written past one is cut again. The rule is applied twice, as the fixpoint applies it, and leaves its own
     * output alone. Each row: the chain % what the nomadic extractor leaves.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "5.move(<a>).plus(1).explain()       % start(5).cut(<a>,plus(1)).explain()",
            "5.move(<a>).plus(1).profile()       % start(5).cut(<a>,plus(1)).profile()",
            "5.move(<a>).plus(1).explain()>>format % start(5).cut(<a>,plus(1)).explain()>>format",
            "5.push(<a>).plus(1).explain()       % start(5).push(<a>).cut(<~>,plus(1)).explain()",
            "5.move(<a>).explain()               % start(5).move(<a>).explain()",
            "5.cut(<a>,plus(1)).mult(2)          % start(5).cut(<a>,plus(1)).cut(<~>,mult(2))",
            "5.cut(<a>,plus(1)).explain()        % start(5).cut(<a>,plus(1)).explain()",
    }, delimiter = '%')
    void testExplainAndProfileAreTheReturnHome(final String mtron, final String extracted) {
        final Inst rule = rule("nomadic_extract");
        final Code once = rule.apply(ObjmtronSerializer.parse(mtron.trim()).asCode()).asCode();
        checkEquality(LOG, ObjmtronSerializer.parse(extracted.trim()).asCode(), once, true);
        checkEquality(LOG, once, rule.apply(once).asCode(), true);
    }

    /**
     * A MACHINE CHANGE ENDS WHAT IS PLANNED HERE: a cut before a profile() stays in the chain, and law_shard plans only
     * the stretch before it, so the cut is never copied into a lane (a profiled run is otherwise planned up to the
     * profile, and each lane would run the machine change at its peer). Each row: the chain % whether the stretch
     * before the cut is planned.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "{1,2,3,4}.plus(1).sum().move(<..>).plus(1).profile() % true",
            "5.move(<a>).plus(1).profile()                         % false",
    }, delimiter = '%')
    void testLawShardPlansOnlyUpToACut(final String mtron, final boolean plansBefore) {
        Network.Helper.connect(Machine.current().network(), SIBLING);
        final Code extracted = rule("nomadic_extract").apply(ObjmtronSerializer.parse(mtron.trim()).asCode()).asCode();
        final Code planned = rule("law_shard").apply(extracted).asCode();
        final int cut = indexOf(planned, CUT_INST_TID);
        // the cut and what follows it are as the extractor left them
        checkEquality(LOG, code(extracted.insts().subList(indexOf(extracted, CUT_INST_TID), extracted.insts().size())),
                code(planned.insts().subList(cut, planned.insts().size())), true);
        checkEquality(LOG, bool(plansBefore), branches(planned.insts().subList(0, cut)), true);
    }

    /**
     * NOTHING IS LEFT OPEN HERE FOR CODE THAT RUNS ELSEWHERE OR LATER: a blocking instruction applies its arguments
     * itself (a cut's tail where it lands, a profile's program on each timed run), so the compiled line has no
     * unresolved instruction and the console reports no dynamic resolution. Each row: the line.
     */
    @ParameterizedTest
    @CsvSource({
            "{1,2,3,4}.move(<..>).plus(1).sum()",
            "1.plus(2).profile()",
    })
    void testACompiledLineLeavesNothingOpenHere(final String mtron) {
        final Code compiled = Machine.current().compiler().apply(ObjmtronSerializer.parse(mtron.trim())).asCode();
        checkEquality(LOG, jnt(0), jnt(Call.Helper.getUnresolvedInsts(compiled).size()), true);
    }

    /**
     * law_shard plans the fold before a cut, and the cut follows with its tail as written.
     */
    @Test
    void testLawShardPlansTheFoldBeforeACut() {
        Network.Helper.connect(Machine.current().network(), SIBLING);
        final Code planned = planned("{1,2,3,4}.plus(1).sum().move(<..>).plus(1)");
        final Inst cut = planned.insts().getLast();
        checkEquality(LOG, uri(CUT_INST_TID), uri(cut.tid().basePath()), true);
        checkEquality(LOG, ObjmtronSerializer.parse("plus(1)").asCode().tryToInst(), cut.arg(1), true);
        checkEquality(LOG, bool(true), branches(planned.insts().subList(0, planned.insts().size() - 1)), true);
    }

    /**
     * THE TAIL IS COMPILED WHERE IT LANDS: a cut into this machine's own root compiles plus(1).sum() when it runs, and
     * that compilation deals it over the peer the root dialed; the fold answers its value either way.
     */
    @Test
    void testACutsTailCompilesWhereItLands() {
        Network.Helper.connect(Machine.current().network(), SIBLING);
        checkCodeParseApply(LOG, "{1,2,3,4}.move(</.>).plus(1).sum()", "14");
    }

    /**
     * A LONE MACHINE HAS NO PEER AND STATES NO PLACE SET ({@code Shard.lone}), the one rule law_shard's pool pre-check
     * and {@code Shard.apply} share: a stated {@code ?place} lets a peerless chain through, and so does a dial. Each
     * row: the chain % whether a peer is dialed % whether the machine is lone for it.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "start({1,2,3,4}).plus(1).sum()                                % false % true",
            "start?place=[local,local,local,local]({1,2,3,4}).plus(1).sum() % false % false",
            "start({1,2,3,4}).plus(1).sum()                                % true  % false",
    }, delimiter = '%')
    void testALoneMachineHasNoPeerAndStatesNoPlace(final String mtron, final boolean dialed, final boolean lone) {
        if (dialed)
            Network.Helper.connect(Machine.current().network(), SIBLING);
        final Code code = ObjmtronSerializer.parse(mtron.trim()).asCode();
        checkEquality(LOG, bool(lone), bool(Shard.lone(Machine.current().network().peers(), code)), true);
    }
}
