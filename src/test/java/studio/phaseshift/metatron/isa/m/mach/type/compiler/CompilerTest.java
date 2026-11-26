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

package studio.phaseshift.metatron.isa.m.mach.type.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.mach.type.Compiler;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.resolver.FirstFindSelector;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.resolver.SpecificitySelector;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Poly;
import studio.phaseshift.metatron.isa.m.type.impl.MCode;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.BRANCH_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.START_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_FALSE;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_TRUE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;

/**
 * EVERY MACHINE OWNS ITS COMPILER (Marko, 2026-11-10). push gives the child a deep clone of its parent's compiler,
 * stages and all: the child compiles as its parent did at the push, a write under the child's {@code ~/compiler}
 * stays the child's, and a write the parent makes after the push stays the parent's.
 */
public class CompilerTest extends AbstractMetatronTest {

    @Test
    public void testAPushedMachineOwnsItsCompiler() {
        // a parent of the test's own: a fork's classes share its root, and a root whose typer this test turned off
        // would type their code too
        final Machine parent = Machine.current().push(f("own_parent"));
        try {
            final Selector selector = parent.compiler().resolver().selector();
            // a check point the parent turns off before the push, which the child's clone carries
            parent.compiler().typer().at("check/inst_dom", BOOL_FALSE, Poly.MUTABLE);
            final Machine child = parent.push(f("own_compiler"));
            try {
                final Compiler own = child.compiler();
                assertNotSame(parent.compiler().resolver(), own.resolver(), "the child's resolver is its own");
                assertNotSame(parent.compiler().typer(), own.typer(), "and so is its typer");
                assertFalse(Typer.Helper.instDom(), "both start as the parent's were at the push");

                // the typer first: a first-find selector, once in force, checks every later compiler write first-match
                Machine.write(f("~/compiler/typer/check/inst_dom"), BOOL_TRUE);
                Machine.write(f("~/compiler/resolver/selector"), new FirstFindSelector());
                assertInstanceOf(FirstFindSelector.class, child.compiler().resolver().selector(), "the child's write is the child's");
                assertTrue(Typer.Helper.instDom(), "its typer's included");
                assertSame(selector, parent.compiler().resolver().selector(), "the parent keeps its selector");
                assertEquals(BOOL_FALSE, parent.compiler().typer().at("check/inst_dom"), "and its check point");

                parent.compiler().typer().at("check/type_pred", BOOL_FALSE, Poly.MUTABLE);
                assertTrue(Typer.Helper.typePred(), "a write the parent makes after the push never reaches the child");
            } finally {
                child.pop();
            }
        } finally {
            parent.pop();
        }
    }

    /**
     * ONE CODE, TWO MACHINES, TWO RESOLUTIONS: the resolve cache keys a code by the selector and binder that resolve it,
     * so a child whose selector differs resolves a code its parent already resolved, never reading the parent's back.
     */
    @Test
    public void testACodeIsResolvedByTheMachineThatResolvesIt() {
        final AtomicInteger asked = new AtomicInteger();
        final Code code = ObjmtronSerializer.parse("start(1).plus(2)");
        final Machine parent = Machine.current();
        final Code byParent = parent.compiler().resolver().apply(code);
        assertTrue(Resolver.Helper.RESOLVE_CACHE.containsValue(byParent), "the parent's resolution is cached");
        final Machine child = parent.push(f("own_resolution"));
        try {
            Machine.write(f("~/compiler/resolver/selector"), new SpecificitySelector() {
                @Override
                public Inst apply(final Obj lhs, final Inst userInst, final Binder binder) {
                    asked.incrementAndGet();
                    return super.apply(lhs, userInst, binder);
                }
            });
            final Code byChild = child.compiler().resolver().apply(code);
            assertNotSame(byParent, byChild, "the child resolves the code itself");
            assertTrue(0 < asked.get(), "through its own selector");
            assertEquals(byParent, byChild, "to the same contracts, its selector being specificity's");
        } finally {
            child.pop();
        }
    }

    /**
     * A BRANCH'S OWN MEMBERS COMPILE WITH IT (Marko, 2026-11-25): the resolver holds a positional member of a branch
     * that is no layout resolved and numbered in the branch's args, and route runs it as compiled.
     */
    @Test
    public void testABranchsMembersCompileWithIt() {
        final Inst branch = Machine.current().compiler().apply(ObjmtronSerializer.parse("5.branch(plus(1).mult(2),plus(3).mult(4),neg())"))
                .insts().stream().filter(inst -> BRANCH_INST_TID.equals(inst.tid().basePath())).findFirst().orElseThrow();
        final List<Obj> members = branch.args().lstValue();
        assertEquals(3, members.size(), "the branch keeps its three members");
        for (final Obj member : members.subList(0, 2)) {
            assertTrue(member.asCode().isResolved(true), "a chain member is held resolved: " + member);
            assertTrue(Resolver.Helper.isNumbered(member.asCode()), "and numbered: " + member);
        }
    }

    /**
     * A member whose carrier is not the type of each obj it is fed is held as written, and compiles per obj as before: a
     * stream of two types, a carrier read at run time, a member that reads its walked path, a batching branch's bag, and
     * a spanned branch's later runs, which it feeds its own output.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "{2,'ab'}.branch(plus(_).plus(_),plus(_))",
            "*quarry.branch(plus(_).plus(_),plus(_))",
            "1.branch(plus(2).path(),mult(3).path())",
            "{3,2}.branch?<=int{*}(plus(1).mult(2),count().plus(1))",
            "5.{2}{as(str::T).plus(_),plus(_).plus(_)}",
    }, delimiter = '%', quoteCharacter = '~')
    public void testAMemberItsCarrierDoesNotFitIsAsWritten(final String code) {
        final Inst branch = Machine.current().compiler().apply(ObjmtronSerializer.parse(code))
                .insts().stream().filter(inst -> BRANCH_INST_TID.equals(inst.tid().basePath())).findFirst().orElseThrow();
        for (final Obj member : branch.args().lstValue())
            assertFalse(member.isCode() && Resolver.Helper.isNumbered(member.asCode()), "held as written: " + member);
    }

    /**
     * ONE COMPILE, HOWEVER MANY OBJS: route runs a held member as compiled, so no obj that reaches the branch has the
     * member wrapped in a start(lhs) and compiled again. A stream four times as long asks the selector for start no more
     * often (Code.apply asked once per obj and member).
     */
    @Test
    public void testAHeldMemberIsNotCompiledAgainPerObj() {
        final AtomicInteger starts = new AtomicInteger();
        final Machine child = Machine.current().push(f("held_members"));
        try {
            Machine.write(f("~/compiler/resolver/selector"), new SpecificitySelector() {
                @Override
                public Inst apply(final Obj lhs, final Inst userInst, final Binder binder) {
                    if (START_INST_TID.equals(userInst.tid().basePath()))
                        starts.incrementAndGet();
                    return super.apply(lhs, userInst, binder);
                }
            });
            final List<Integer> asked = new ArrayList<>();
            for (final String stream : List.of("{1,2,3,4,5}", "{1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20}")) {
                starts.set(0);
                assertTrue(0 < ObjmtronSerializer.parse(stream + ".branch(plus(1).mult(2),plus(3).mult(4))").apply().stream().count(), "the branch answers");
                asked.add(starts.get());
            }
            assertEquals(asked.get(0), asked.get(1), "start is asked for as often over 20 objs as over 5: " + asked);
        } finally {
            child.pop();
        }
    }

    /**
     * A compiled member answers what the member compiled for each obj answered: the carrier it is resolved against is
     * the one flowing into the branch, a carrier read at run time leaves it to compile per obj, the coefficient of the
     * obj it runs on multiplies its answer once, a filter in it drops the obj alone, a reducer in it still reads one obj
     * at a time, a member that is a branch compiles its own members in turn, and the members a carrier does not fit (a
     * batching branch's, a spanned branch's, one that reads its path) answer as Code.apply made them.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "5.branch(plus(1).mult(2),plus(3).mult(4))                               % {12,32}",
            "{1,2,3}.branch(plus(1).mult(2),plus(3).mult(4))                         % {4,16,6,20,8,24}",
            "5.plus(1).branch(plus(1).mult(2),mult(3)).plus(1)                       % {15,19}",
            "'meta'.branch(plus('tr').plus('on'),plus('l'))                          % {'metatron','metal'}",
            "5.as(str::T).branch(plus('a').plus('b'),plus('c'))                      % {'5ab','5c'}",
            // a branch fed by a branch of two types: each obj runs the member as its own type would
            "5.branch(plus(1),'a').branch(plus(_).plus(_),plus(_))                   % {24,12,'aaaa','aa'}",
            "ingot -> 'ab'; *ingot.branch(plus(_).plus(_),plus(_))                   % {'abababab','abab'}",
            "flint -> 'ab'; 5.map(*flint).branch(plus(_).plus(_),plus(_))            % {'abababab','abab'}",
            "int{2}::5.branch(plus(1).mult(2),plus(3).mult(4))                       % {int{2}::12,int{2}::32}",
            "{1,6}.branch(is(gt(3)).plus(1),mult(2).plus(1))                         % {3,7,13}",
            "{1,2}.branch(plus(1).count(),plus(2).count())                           % {4}1",
            "5.branch(plus(1).branch(mult(2).plus(1),mult(3).plus(1)),plus(2).mult(2)) % {13,19,14}",
            "{3,2}.branch?<=int{*}(plus(1).mult(2),count().plus(1))                  % {8,6,3}",
            "5.{2}{as(str::T).plus(_),plus(_).plus(_)}                               % {'5555','55555555','2020',80}",
            "1.branch(plus(2).path()>>.count(),mult(3).path()>>.count())             % {2}4",
    }, delimiter = '%', quoteCharacter = '~')
    public void testACompiledMemberAnswersAsItsChain(final String code, final String expected) {
        checkCodeParseApply(LOG, code, expected);
    }

    /**
     * NO TWO SHARE A NUMBER: {@code Code.nextInst} finds an instruction by its vid, so a chain that repeats one, as a
     * rewrite that splices a compiled member into its branch's chain does ({@code start(1)@<0>.plus(1)@<0>.mult{3}(3)@<1>}
     * from {@code branch_collapse}), is not compile-once, and the resolver numbers it again.
     */
    @Test
    public void testAChainThatRepeatsANumberIsNumberedAgain() {
        final Resolver resolver = Machine.current().compiler().resolver();
        final Code compiled = resolver.apply(ObjmtronSerializer.parse("start(1).plus(1).mult(3)"));
        assertTrue(Resolver.Helper.isNumbered(compiled), "a compiled chain is numbered");
        final List<Inst> spliced = new ArrayList<>(compiled.insts());
        spliced.set(1, spliced.get(1).clone().selfVID(spliced.get(0).vid()).as());
        assertFalse(Resolver.Helper.isNumbered(MCode.of(spliced)), "a repeated number is no numbering");
        final Code renumbered = resolver.apply(MCode.of(spliced));
        assertTrue(Resolver.Helper.isNumbered(renumbered), "the resolver numbers it again");
        checkEquality(LOG, jnt(6), renumbered.apply(noobj()), true);
    }
}
