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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.Rewriter;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.rewriter.PlanRewriter;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.MAX;
import static studio.phaseshift.metatron.Tokens.REWRITE;
import static studio.phaseshift.metatron.isa.m.mach.machInstSet.MACH_INST_TID;
import static studio.phaseshift.metatron.isa.m.mach.machInstSet.MACH_PLAN_REWRITER_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * {@code plan_rewriter::T} — the plan engine as a rewriter: a {@code fixpoint_rewriter::T} whose rules are the
 * law-driven plans, so the compiler's rewrite stage runs the algebra the instructions and types declare and nothing
 * else. This class mounts nothing past the boot instsets: run on its own, the machine has no {@code /m/math} and no
 * {@code /m/math/cat}, so every row below is the plan engine working without the category.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class PlanRewriterTest extends AbstractMetatronTest {

    /**
     * The type mints the engine, the refinement keeps {@code fixpoint_rewriter::T}'s convergence window, and the rules
     * are the plans — in the category's order, and named in the machine's own rewrite namespace, not the category's.
     */
    @Test
    void shouldConstructThePlanEngineFromItsType() {
        final Rewriter rewriter = ObjmtronSerializer.parse("plan_rewriter::[max=>3]");
        assertInstanceOf(PlanRewriter.class, rewriter, "plan_rewriter::[=>] mints the plan engine");
        assertEquals(MACH_PLAN_REWRITER_TID, rewriter.tid());
        assertEquals(jnt(3), rewriter.at(uri(MAX)), "a fixpoint_rewriter::T refinement keeps the convergence window");
        assertIterableEquals(Stream.of("theory_unit_removal", "branch_collapse", "branch_factor", "branch_inline", "law_involution",
                                "law_idempotent", "law_absorbing", "law_monoidic", "law_poset", "inverse_cancellation",
                                "form_map_unwrap", "derivation_contraction", "law_shard")
                        .map(name -> MACH_INST_TID.extend(REWRITE).extend(name)).toList(),
                rewriter.rewrites().stream().map(rule -> rule.tid().basePath()).toList(),
                "the rules are the plans, and only the plans");
    }

    /**
     * Each plan, applied by the rewriter the type mints: the chain % the chain the plans leave % what that rewrite
     * evaluates to. The rewrite is checked rather than the chain as written: without the category, {@code map(neg)}
     * emits the instruction itself, so {@code form_map_unwrap} gives that chain its meaning rather than optimizing it.
     */
    @ParameterizedTest
    @CsvSource(value = {
            // theory_unit_removal — the int ring's identities, and composition's own
            "5.plus(0)                        % start(5)                          % 5",
            "5.plus(0).mult(3)                % start(5).mult(3)                  % 15",
            "5.id()                           % start(5)                          % 5",
            // law_involution
            "5.neg().neg().plus(2)            % start(5).plus(2)                  % 7",
            // law_idempotent
            "2.zero().zero()                  % start(2).zero()                   % 0",
            "\"ab\".ucase().ucase()           % start(\"ab\").ucase()             % \"AB\"",
            // law_absorbing — and the filter before a strict absorber stays
            "5.plus(3).zero()                 % start(5).zero()                   % 0",
            "{1,2,3}.is(gt(1)).zero()         % start({1,2,3}).is(gt(1)).zero()   % {2}0",
            // law_monoidic
            "{1,2,3}.sum().sum()              % start({1,2,3}).sum()              % 6",
            // law_poset
            "{1,2,3,4,5}.is(gt(1)).is(gt(3))  % start({1,2,3,4,5}).is(gt(3))     % {4,5}",
            // inverse_cancellation — one-sided: int div truncates, so div·mult stays
            "5.plus(3).minus(3)               % start(5)                          % 5",
            "5.div(2).mult(2)                 % start(5).div(2).mult(2)           % 4",
            // form_map_unwrap: /m's mapper and map_nest rows; a stream's fold is no mapper, so its mapping stays
            "5.map(neg)                       % start(5).neg()                    % -5",
            "{1,2,3}.map(map(sum()))          % start({1,2,3}).map(sum())         % {1,2,3}",
            // derivation_contraction
            "6.plus(3.neg())                  % start(6).minus(3)                 % 3",
            // branch_collapse, then branch_inline on the lone survivor
            "1+2.branch(+2,+2,+2)             % start(1).plus(2).plus{3}(2)       % {3}5",
            "1.branch(+2.+2,+2.+2)            % start(1).plus(2).plus{2}(2)       % {2}5",
            // a member law_involution empties is the unit, which then collapses with the id() beside it
            "1.branch(id(),neg(),neg().neg()) % start(1).branch(id{2}(),neg())    % {{2}1,-1}",
            // ONLY THE PLANS RUN: /m's else_after_count is a rewrite of the instset, not a plan, so the else stays
            "{1,2,3}.count().else(0)          % start({1,2,3}).count().else(0)    % 3",
    }, delimiter = '%')
    void testPlans(final String code, final String expected, final String expectedResult) {
        final Rewriter rewriter = ObjmtronSerializer.parse("plan_rewriter::[=>]");
        final Code rewritten = rewriter.apply(ObjmtronSerializer.parse(code));
        checkEquality(LOG, ObjmtronSerializer.parse(expected), rewritten.tryToInst(), true);
        checkEquality(LOG, ObjmtronSerializer.parse(expectedResult), rewritten.apply(noobj()), true);
    }
}
