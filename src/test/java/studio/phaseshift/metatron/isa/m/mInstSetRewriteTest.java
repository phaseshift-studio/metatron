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

package studio.phaseshift.metatron.isa.m;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractInstSetTest;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.Network;
import studio.phaseshift.metatron.isa.m.math.cat.catInstSet;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rel;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.HOP;
import static studio.phaseshift.metatron.Tokens.PEER;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.BRANCH_INST_TID;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_ISA_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MCode.code;

/**
 * THE REWRITE POOL, ONE RULE AT A TIME — each rewrite of the machine's pool ({@code Machine.current().instset().rewrites()}),
 * found by its name and applied once to a chain, exactly as the compiler's rewriter applies it: no plan and no plan
 * interpreter in between. /m's rules (the unit, inverse and derivation laws, the law, form and plain rows, the branch
 * factor) and the category's (branch_collapse, branch_inline and law_shard, which are moving to /m) all run here.
 * <p>
 * The cases are PlanBuilderTest's rewrite cases, carried from the plans to the pool: its emitted-code snapshot, and its
 * hop-budget cases, read off the code law_shard emits rather than off the plan. PlanBuilderTest's other cases test the
 * plan vocabulary itself (Cut, Span, Placement, PlaceSet, Hop, Partition, bind) and stay with it.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mInstSetRewriteTest extends AbstractInstSetTest {

    /**
     * The one loop peer law_shard fans out over: a fold is distributed only where a peer it can ship to is dialed.
     */
    private static final fURI LOOP_PEER = f("loop://localhost:8555");

    public mInstSetRewriteTest() {
        // the category's rules are still the category's: it is mounted per test beside /m, so its rules join the pool
        super(catInstSet::new);
    }

    @BeforeAll
    public static void importMath() {
        // cmplx's derivations are /m/math's
        InstSet.importInstSet(MATH_ISA_TID);
    }

    @BeforeEach
    public void clearPeers() {
        // a dial another class left in the fork's root would make this lone machine shard
        Machine.current().network().write(f(PEER), noobj());
    }

    /**
     * What law_shard answers for {@code code} with {@link #LOOP_PEER} dialed, the dial dropped after.
     */
    private static Obj shard(final Obj code) {
        Network.Helper.connect(Machine.current().network(), LOOP_PEER);
        try {
            return rewrite("law_shard", code);
        } finally {
            Machine.current().network().drop(LOOP_PEER);
        }
    }

    /**
     * What the rewrite of the machine's pool named {@code rule} answers, applied once to {@code code}: the code it leaves,
     * or the fail its body refused with (the processor answers a body's exception as a fail).
     */
    private static Obj rewrite(final String rule, final Obj code) {
        assertTrue(code.isCode(), () -> "the chain does not parse: " + code);
        return Machine.current().instset().rewrites().stream()
                .filter(rewrite -> rule.equals(rewrite.tid().name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no rewrite named " + rule + " in the machine's pool"))
                .apply(code);
    }

    /**
     * Every message along a refusal's cause chain: the processor wraps the body's exception in its own.
     */
    private static String refusal(final Obj answered) {
        assertTrue(answered.isFail(), () -> "a refusal answers a fail, not " + answered);
        return Stream.iterate(answered.asFail().jvm(), Objects::nonNull, Throwable::getCause)
                .map(Throwable::getMessage)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" | "));
    }

    /**
     * EACH RULE, APPLIED ONCE. The first eleven rows are PlanBuilderTest's emitted-code snapshot: the chain each plan
     * lowered to is the chain the pool's rewrite of the same name leaves. The rest pin where a rule holds and where it
     * must not. The unit law compares the operand, and end is composition's zero, never its identity. A declared inverse
     * cancels either way round, but it is one-sided (int's div truncates) and a type's zero never cancels. A derivation
     * folds back only where its contract runs. Rules that no plan ever named are in the pool too. A rule leaves the
     * instances of other rules as they stand, and law_shard leaves a fold where it stands on a lone machine or under
     * {@code ?hop=0}. Each row: the rule % the chain % the chain the rule leaves.
     */
    @ParameterizedTest
    @CsvSource(value = {
            // PlanBuilderTest's snapshot, one row per rule
            "theory_unit_removal     % 5.plus(0)                          % start(5)",
            "law_involution          % 5.neg().neg()                       % start(5)",
            "law_idempotent          % \"ab\".ucase().ucase()                % start(\"ab\").ucase()",
            "law_absorbing           % 5.plus(3).zero()                    % start(5).zero()",
            "law_monoidic            % {1,2,3}.sum().sum()                 % start({1,2,3}).sum()",
            "inverse_cancellation    % 5.plus(3).minus(3)                  % start(5)",
            "form_map_unwrap         % 5.map(neg)                          % start(5).neg()",
            "derivation_contraction  % 6.plus(3.neg())                     % start(6).minus(3)",
            "law_poset               % {1,2,3,4,5}.is(gt(1)).is(gt(3))     % start({1,2,3,4,5}).is(gt(3))",
            "branch_inline           % 1.branch(+2)                        % start(1).plus(2)",
            "branch_collapse         % 1.branch(+2,+2)                     % start(1).branch(plus{2}(2))",
            // the unit law: every declared monoid's identity drops, and its operand is compared, not type-tested
            "theory_unit_removal     % 5.mult(1)                           % start(5)",
            "theory_unit_removal     % 5.plus(0).mult(3)                   % start(5).mult(3)",
            "theory_unit_removal     % \"ab\".plus(\"\")                       % start(\"ab\")",
            "theory_unit_removal     % true.mult(true)                     % start(true)",
            "theory_unit_removal     % 5.id()                              % start(5)",
            "theory_unit_removal     % 5.plus(1)                           % start(5).plus(1)",
            "theory_unit_removal     % 5.mult(0)                           % start(5).mult(0)",
            // ... and end is the additive group's id, composition's zero: never the identity
            "theory_unit_removal     % 5.end()                             % start(5).end()",
            "theory_unit_removal     % 5.id().end()                        % start(5).end()",
            // the inverse law: either order cancels, an unequal pair stays, the declared inverse is one-sided (int's div
            // truncates, so neither div·mult nor mult·div cancels on int), real's does, and a zero never cancels
            "inverse_cancellation    % 5.minus(3).plus(3)                  % start(5)",
            "inverse_cancellation    % 5.plus(3).minus(2)                  % start(5).plus(3).minus(2)",
            "inverse_cancellation    % 5.div(2).mult(2)                    % start(5).div(2).mult(2)",
            "inverse_cancellation    % 6.mult(2).div(2)                    % start(6).mult(2).div(2)",
            "inverse_cancellation    % 5.as(real::T).mult(2).div(2)        % start(5).as(real::T)",
            "inverse_cancellation    % 5.0.mult(0.0).div(0.0)              % start(5.0).mult(0.0).div(0.0)",
            // a derivation folds back where its contract runs: real's div, never an int's or a lst's
            "derivation_contraction  % 6.0.mult(2.0.inv())                 % start(6.0).div(2.0)",
            "derivation_contraction  % 7.mult(2.inv())                     % start(7).mult(2.inv())",
            "derivation_contraction  % [1,2,3].plus([2].neg())             % start([1,2,3]).plus([2].neg())",
            "derivation_contraction  % nat::6.plus(3.neg())                % start(nat::6).minus(3)",
            "derivation_contraction  % {'a','b'}.map(1).sum()              % start({'a','b'}).count()",
            "derivation_contraction  % cmplx::[1.0,2.0].mult(cmplx::[1.0,1.0].inv())  % start(cmplx::[1.0,2.0]).div(cmplx::[1.0,1.0])",
            // rules no plan ever named: the pool is the table
            "else_after_count        % {1,2,3}.count().else(0)             % start({1,2,3}).count()",
            "range_skip_take         % {1,2,3,4,5}.range(1,4)              % start({1,2,3,4,5}).skip(1).take(3)",
            "branch_factor           % 1.branch(plus(1).mult(2),plus(1).mult(3))  % start(1).plus(1).branch(mult(2),mult(3))",
            // a rule is no other rule: each leaves the others' instances as they stand
            "law_involution          % 5.plus(0)                           % start(5).plus(0)",
            "theory_unit_removal     % 5.neg().neg()                       % start(5).neg().neg()",
            "inverse_cancellation    % 6.plus(3.neg())                     % start(6).plus(3.neg())",
            "derivation_contraction  % 5.plus(3).minus(3)                  % start(5).plus(3).minus(3)",
            "form_map_unwrap         % {1,2,3}.map(sum())                  % start({1,2,3}).map(sum())",
            "branch_collapse         % 1.branch(+2,+4)                     % start(1).branch(plus(2),plus(4))",
            // law_shard: a lone machine does not fan out
            "law_shard               % {1,2,3,4}.plus(1).sum()             % start({1,2,3,4}).plus(1).sum()",
    }, delimiter = '%')
    void testEachRuleRewritesItsChain(final String rule, final String chain, final String rewritten) {
        final Obj answered = rewrite(rule, ObjmtronSerializer.parse(chain));
        assertTrue(answered.isCode(), () -> rule + " answers code, not " + answered);
        checkEquality(LOG, ObjmtronSerializer.parse(rewritten), answered.asCode().tryToInst(), true);
    }

    /**
     * THE POOL TO A FIXPOINT: the machine's own rewriter ({@code code.rewrite()}, fixpoint_rewriter::T over the whole
     * pool) leaves each chain in its normal form, and the normal form means what the chain meant. Rules meet here: a
     * member law_involution empties is the unit, which branch_collapse then sums with the id() beside it, and
     * branch_inline unwraps what the collapse leaves alone. These rows were PlanRewriterTest's, when plan_rewriter::T ran
     * the plans alone; the pool runs every rule, so else_after_count drops the else after a total count, where the
     * plans kept it. Each row: the chain % the chain the pool leaves % what it evaluates to.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "5.plus(0)                        % start(5)                          % 5",
            "5.plus(0).mult(3)                % start(5).mult(3)                  % 15",
            "5.id()                           % start(5)                          % 5",
            "5.neg().neg().plus(2)            % start(5).plus(2)                  % 7",
            "2.zero().zero()                  % start(2).zero()                   % 0",
            "\"ab\".ucase().ucase()           % start(\"ab\").ucase()             % \"AB\"",
            "5.plus(3).zero()                 % start(5).zero()                   % 0",
            "{1,2,3}.is(gt(1)).zero()         % start({1,2,3}).is(gt(1)).zero()   % {2}0",
            "{1,2,3}.sum().sum()              % start({1,2,3}).sum()              % 6",
            "{1,2,3,4,5}.is(gt(1)).is(gt(3))  % start({1,2,3,4,5}).is(gt(3))     % {4,5}",
            "5.plus(3).minus(3)               % start(5)                          % 5",
            "5.div(2).mult(2)                 % start(5).div(2).mult(2)           % 4",
            "5.map(neg)                       % start(5).neg()                    % -5",
            "{1,2,3}.map(map(sum()))          % start({1,2,3}).map(sum())         % {1,2,3}",
            "6.plus(3.neg())                  % start(6).minus(3)                 % 3",
            "1+2.branch(+2,+2,+2)             % start(1).plus(2).plus{3}(2)       % {3}5",
            "1.branch(+2.+2,+2.+2)            % start(1).plus(2).plus{2}(2)       % {2}5",
            "1.branch(id(),neg(),neg().neg()) % start(1).branch(id{2}(),neg())    % {{2}1,-1}",
            "{1,2,3}.count().else(0)          % start({1,2,3}).count()            % 3",
    }, delimiter = '%')
    void testThePoolRewritesToAFixpoint(final String chain, final String rewritten, final String value) {
        final Code normal = ObjmtronSerializer.<Code>parse(chain).rewrite();
        checkEquality(LOG, ObjmtronSerializer.parse(rewritten), normal.tryToInst(), true);
        checkEquality(LOG, ObjmtronSerializer.parse(value), normal.apply(noobj()), true);
    }

    /**
     * LAW_SHARD RESTATES ITS HOP, ONE HEIGHT DOWN. The budget is read in chain order: {@code ?hop}, or its deprecated
     * read-only alias {@code ?hops}, on any instruction of the fold. The earliest statement wins, and the new spelling is
     * preferred at one position. A chain that states neither takes the plan's one level, the star. Each lane's fold
     * restates the budget one height down, so the lane is planned again where it lands, and a height of one leaves the
     * leaf's bare {@code 0}. The counted spelling's leading coefficient is the arity, and it rides along. A loop peer is
     * dialed, since a silent machine has no place of its own to shard over. PlanBuilderTest read the budget off the plan
     * ({@code PlanInterpreter.hop}); here it is read off the code the rewrite emits. Each row: the chain % the budget
     * every lane's fold states.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "start?hop=2({1,2,3,4}).plus(1).sum()             % 1",
            "start?hops=2({1,2,3,4}).plus(1).sum()            % 1",
            "start?hop=2&hops=3({1,2,3,4}).plus(1).sum()      % 1",
            "start?hops=2({1,2,3,4}).plus(1).sum?hop=3()      % 1",
            "start?hop=4({1,2,3,4}).plus(1).sum()             % 3",
            "start?hop={2}4({1,2,3,4}).plus(1).sum()          % {2}3",
            "start?hops={2}4({1,2,3,4}).plus(1).sum()         % {2}3",
            "{1,2,3,4}.plus(1).sum()                          % 0",
    }, delimiter = '%')
    void testTheShardRestatesItsHop(final String chain, final String budget) {
        final Obj answered = shard(ObjmtronSerializer.parse(chain));
        assertTrue(answered.isCode(), () -> "law_shard answers code, not " + answered);
        final Inst branch = answered.asCode().insts().stream()
                .filter(inst -> BRANCH_INST_TID.equals(inst.tid().basePath()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("law_shard emitted no branch for " + chain));
        final List<String> folds = branch.args().asRec().<Rel>elements()
                .map(lane -> lane.second().asCode().insts().getLast().tid().q(HOP))
                .toList();
        assertFalse(folds.isEmpty(), "the branch has lanes: " + branch);
        assertEquals(Collections.nCopies(folds.size(), budget), folds, "every lane's fold restates the budget: " + branch);
    }

    /**
     * WHERE A PEER IS DIALED AND THE FOLD STILL STAYS WHERE IT STANDS: a zero hop opts the fold out, on the seed or on the
     * reducer; a chain with no fold has nothing to distribute; a seed of one member has nothing to part; and a split the
     * shard does not lower ({@code block}) declines to the chain as written, never a plausible other layout. Each row:
     * the chain law_shard leaves as it is.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "start?hop=0({1,2,3,4}).plus(1).sum()",
            "start({1,2,3,4}).plus(1).sum?hop=0()",
            "start({1,2,3,4}).plus(1)",
            "start(1).plus(1).sum()",
            "start?split=block({1,2,3,4}).plus(1).sum()",
    }, delimiter = '%')
    void testTheShardLeavesAFoldWhereItStands(final String chain) {
        final Obj answered = shard(ObjmtronSerializer.parse(chain));
        assertTrue(answered.isCode(), () -> "law_shard answers code, not " + answered);
        checkEquality(LOG, ObjmtronSerializer.parse(chain), answered.asCode().tryToInst(), true);
    }

    /**
     * A HOP BUDGET THAT CANNOT BE READ IS REFUSED, LOUDLY, never answered {@code 0}: {@code hop} is the tree's height, and
     * a silent zero plans no distribution at all. These are the shapes the object grammar leaves behind: a bare
     * non-numeric uri ({@code abc}), a list that is not the pair form ({@code [1,2,3]}; the pair
     * {@code [height, arity]} is a hop), a quoted str where a value was required, a backslash value the parser cannot
     * read, and a trailing range on a number ({@code 4{1,2}}, a number's trailing coefficient being its span). The
     * rewrite answers the refusal as a fail. Each row: the chain % the text the refusal must name.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "start?hop=abc({1,2,3,4}).plus(1).sum()          % ?hop=abc",
            "start?hop=[1,2,3]({1,2,3,4}).plus(1).sum()      % ?hop=[1,2,3]",
            "start?hop=\"ab\"({1,2,3,4}).plus(1).sum()       % ?hop=\"ab\"",
            "start?hop=\\x({1,2,3,4}).plus(1).sum()          % ?hop=\\x",
            "start?hop=4{1,2}({1,2,3,4}).plus(1).sum()       % ?hop=4{1,2}",
    }, delimiter = '%')
    void testTheShardRefusesAnUnreadableHop(final String chain, final String stated) {
        final String refusal = refusal(shard(ObjmtronSerializer.parse(chain)));
        assertTrue(refusal.contains(stated), () -> "the refusal names the key and the text it could not read: " + refusal);
    }

    /**
     * AN EMPTY STATED BUDGET IS REFUSED TOO. The source grammar cannot spell it ({@code ?hop=} is a pair with no value),
     * so the key is stated on the instruction directly.
     */
    @Test
    void testTheShardRefusesAnEmptyStatedHop() {
        final List<Inst> insts = new ArrayList<>(ObjmtronSerializer.<Code>parse("start({1,2,3,4}).plus(1).sum()").insts());
        insts.set(0, insts.getFirst().tid(insts.getFirst().tid().q(HOP, "")));
        final String refusal = refusal(shard(code(insts)));
        assertTrue(refusal.contains("?hop="), () -> "the refusal names the key: " + refusal);
    }
}
