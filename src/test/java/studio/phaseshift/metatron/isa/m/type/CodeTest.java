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

package studio.phaseshift.metatron.isa.m.type;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.Training;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.parser.mFluent;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.isa.m.mInstSet.ID_INST_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.id_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MCode.code;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instA;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class CodeTest extends AbstractMetatronTest {

    @Training.SkipTraining(reason = "the second column states whether the code fully RESOLVES (a static-analysis result), not what the code evaluates to; e.g. 1.plus(2) is asserted resolved=true while it evaluates to 3")
    @ParameterizedTest
    @CsvSource(value = {
            "1.plus(2)                                          % true",
            "1.plus(\"abc\")                                    % false",
            "{1,2,3}.plus(2)                                    % true",
            "3.plus(2)                                          % true",
            "{1,2,3}.plus(\"abc\")                              % false",
            "{\"1\",\"2\",\"3\"}.plus(\"abc\")                  % true",
            "*abc.plus(2)                                       % false",
            "*?int<=(abc).plus(2)                               % true",
            "1.-<[_,_]                                          % true",
            "{1,2,3}.-<[_,_]                                    % true",
            "1.-<[_,_]>-                                        % true",     // TODO:this resolves because of ring algebra
            "{1,2,3}.>-                                         % true",
            "{1,2,3}.plus(34).sum()                             % true",
            "1.plus(34).sum()                                   % true",
            "'a'.plus('b').sum()                                % true",
            "{'a','b','c'}.plus('def').sum()                    % true",
            "{'a','b','c'}.plus?str<=str('def').sum()           % true",
            "1._                                                % true",
            "1.to(a).plus(4).from(a)                            % false",  // TODO: requires variable tracking in compilation
            "1.to(a).plus(from(a))                              % false",  // TODO: requires variable tracking in compilation
            "*abc._                                             % true",
            "1.*abc._                                           % true"
    }, delimiter = '%', quoteCharacter = '^')
    public void testDomRng(final String code, final boolean resolved) {
        Code obj = ObjmtronSerializer.parse(code);
        LOG.debug("testing code resolution %s %s resolve", obj, resolved ? "{{g}}should{{X}}" : "{{r}}should not{{X}}");
        assertFalse(obj.isResolved(true));
        obj = obj.resolve(noobj());
        if (resolved) assertTrue(obj.isResolved(true));
        else assertFalse(obj.isResolved(true));
    }

    /**
     * \(\varepsilon\) — THE CODE MONOID'S IDENTITY, asserted here because it cannot be asserted at the console:
     * every other unit has a literal (\(0\), \(1\), {@code ""}, {@code <>}, {@code .}) but the chain of ZERO
     * instructions is typed by typing nothing. It is nonetheless a value the rewriter produces — a law-driven
     * rule returns {@code code(kept)}, and a rule that eliminates a whole chain leaves {@code kept} empty — so
     * which element \(\varepsilon\) is decides whether elimination is sound: the identity passes its lhs
     * through (\(\varepsilon \cdot x = x\)), the ANNIHILATOR would destroy it. The bare identity operation
     * ({@code id}, sugar {@code _}) is \(\varepsilon\)'s utterable proxy, so the two must agree.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "5",
            "'abc'",
            "{1,2,3}"
    }, delimiter = '%', quoteCharacter = '^')
    public void testEmptyCodeIsTheIdentityMorphism(final String mtron) {
        final Obj lhs = ObjmtronSerializer.parse(mtron).apply();
        assertFalse(lhs.isNoObj(), "the lhs under test must be a value");
        final Code empty = code(List.of());                             // ε — what code(kept) answers when all of kept dropped
        final Code identity = code(List.of(instA(ID_INST_TID)));         // its utterable proxy — `_`
        LOG.debug("testing the code monoid identity against lhs %s", lhs);
        assertEquals(lhs, empty.apply(lhs), "ε is the identity morphism, never the annihilator");
        assertEquals(lhs, identity.apply(lhs), "a bare id is the identity morphism");
        assertEquals(empty.apply(lhs), identity.apply(lhs), "code([]) and code([id]) are the same morphism");
    }

    /**
     * AN EMPTY CODE IS THE UNIT (Marko, 2026-11-24): f⁰, the composition of no instruction, is the instruction
     * {@code id()} with the code's coefficient as its point ({@link Call#tryToInst}), so it is one when its point is, and
     * only the code of coefficient {0} is the zero, noobj. It was noobj at every point, so isOne() said the unit was not
     * one. The empty code has no literal (a code is its chain, one instruction at least), so each row builds it in Java.
     * Each row: the code's point % the instruction it is % whether it is one.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "1   % id()      % true",
            "3   % id{3}()   % false",
            "-1  % id{-1}()  % false",
            "0   % noobj     % false",
    }, delimiter = '%')
    public void testAnEmptyCodeIsTheUnit(final long point, final String inst, final boolean one) {
        final Code empty = code(List.of()).c(_ -> cInt.of(point)).as();
        final Obj expected = ObjmtronSerializer.parse(inst);
        assertEquals(List.of(), empty.codeValue(), "the code under test holds no instruction");
        assertEquals(cInt.of(point), empty.c(), "the code under test counts its point");
        assertFalse(expected.isFail(), "the instruction it is must parse");
        checkEquality(LOG, expected, empty.tryToInst(), true);
        assertEquals(one, empty.isOne(), empty + (one ? " is one" : " is not one"));
        assertEquals(0 == point, empty.isZero(), empty + (0 == point ? " is the zero" : " is not the zero"));
    }

    /**
     * AN EMPTY CODE APPLIES AS THE UNIT IT IS: it passes its lhs on, and its point goes on the answer as {@code id{c}()}'s
     * does, so the code and the instruction answer alike. It ignored its point: {3}ε at 5 answered 5, where the text it
     * is written as, id{3}(), answers {3}5. Each row: the code's point % the lhs % the instruction it is, on the lhs %
     * what both answer.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "1   % 5       % 5.id()          % 5",
            "3   % 5       % 5.id{3}()       % {3}5",
            "2   % 'abc'   % 'abc'.id{2}()   % {2}'abc'",
            "3   % {1,2}   % {1,2}.id{3}()   % {{3}1,{3}2}",
            "-1  % 5       % 5.id{-1}()      % {-1}5",
            "0   % 5       % 5.id{0}()       % noobj",
    }, delimiter = '%', quoteCharacter = '^')
    public void testAnEmptyCodeAppliesAsTheUnit(final long point, final String lhs, final String unit, final String answer) {
        final Code empty = code(List.of()).c(_ -> cInt.of(point)).as();
        final Obj value = ObjmtronSerializer.parse(lhs).apply();
        final Obj expected = ObjmtronSerializer.parse(answer);
        assertFalse(value.isNoObj() || value.isFail(), "the lhs under test must be a value");
        assertFalse(expected.isFail(), "the answer must parse");
        assertEquals(cInt.of(point), empty.c(), "the code under test counts its point");
        checkEquality(LOG, expected, ObjmtronSerializer.parse(unit).apply(), true);
        checkEquality(LOG, expected, empty.apply(value), true);
    }

    /**
     * A CODE THE REWRITER EMPTIES IS THE UNIT: neg().neg() and plus(0) do nothing to an obj, so their rewrite keeps no
     * instruction, and that code is id(), one, as a lone id() is (the rewriter keeps it), never noobj, the zero that kills
     * the path it stands on. With id() they are the members of 1.branch(id(),neg(),neg().neg()), whose rewrite keeps the
     * emptied member as id() and bulks it with the id() beside it (FixPointRewriter.descend reads the unit off
     * tryToInst()). Each row: the code % the instruction its rewrite is % whether that is one.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "neg().neg()                        % id()                              % true",
            "plus(0)                            % id()                              % true",
            "id()                               % id()                              % true",
            "neg()                              % neg()                             % false",
            "branch(id(),neg(),neg().neg())     % branch(id{2}(),neg())             % false",
            "1.branch(id(),neg(),neg().neg())   % start(1).branch(id{2}(),neg())    % false",
    }, delimiter = '%')
    public void testACodeTheRewriterEmptiesIsTheUnit(final String code, final String inst, final boolean one) {
        final Obj parsed = ObjmtronSerializer.parse(code);
        final Obj expected = ObjmtronSerializer.parse(inst);
        assertFalse(parsed.isNoObj() || parsed.isFail(), "the code under test must parse");
        assertFalse(expected.isNoObj() || expected.isFail(), "the instruction it rewrites to must parse");
        final Code rewritten = parsed.asCode().rewrite();
        checkEquality(LOG, expected, rewritten.tryToInst(), true);
        assertEquals(one, rewritten.isOne(), rewritten + (one ? " is one" : " is not one"));
    }

    /**
     * A PROGRAM THE COMPILER EMPTIES ANSWERS AS THE UNIT ON NOTHING: compiled, a startless neg().neg() keeps no
     * instruction (each row checks that it keeps none), and the machine runs that empty code as the instruction it is,
     * id(), which passes noobj on. As noobj it applied the zero, which answered the same, so these rows hold on either side
     * of the ruling: they guard the machine's dispatch of the unit (Machine.evaluate) from refusing a program with nothing
     * left to run. Each row: the program % what it answers.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "neg().neg()            % noobj",
            "plus(0)                % noobj",
            "neg().neg().plus(0)    % noobj",
    }, delimiter = '%')
    public void testAProgramTheCompilerEmptiesAnswersAsTheUnit(final String program, final String answer) {
        final Obj compiled = Machine.current().compiler().apply(str(program));
        assertFalse(compiled.isFail(), "the program must compile");
        assertEquals(List.of(), compiled.asCode().codeValue(), "the compiler leaves no instruction to run");
        checkEquality(LOG, ObjmtronSerializer.parse(answer), Machine.evaluate(program), true);
    }

    @ParameterizedTest
    @CsvSource(value = {
            // a code tests a code pattern instruction by instruction, one binding of its generics for the whole pattern
            "[plus(2).mult(2)]==[?(plus(int::T@A).mult(A::T))]                 % [plus(2).mult(2)]",
            "[plus(2).mult('age')]==[?(plus(int::T@A).mult(A::T))]             % [noobj]",
            // a use before its declaration answers to it too, and a nested instruction's declaration binds the pattern
            "[plus(2).mult(3)]==[?(plus(A::T).mult(int::T@A))]                 % [plus(2).mult(3)]",
            "[plus('age').mult(3)]==[?(plus(A::T).mult(int::T@A))]             % [noobj]",
            "[plus(mult(2)).mult(2)]==[?(plus(mult(int::T@A)).mult(A::T))]     % [plus(mult(2)).mult(2)]",
            "[plus(mult(2)).mult('age')]==[?(plus(mult(int::T@A)).mult(A::T))] % [noobj]",
            // with no declaration, the first use binds the generic
            "[plus(2).mult(2)]==[?(plus(A::T).mult(A::T))]                     % [plus(2).mult(2)]",
            "[plus(2).mult('age')]==[?(plus(A::T).mult(A::T))]                 % [noobj]",
            // the ops and the length must agree
            "[plus(2).plus(2)]==[?(plus(int::T@A).mult(A::T))]                 % [noobj]",
            "[plus(2).mult(2).plus(1)]==[?(plus(int::T@A).mult(A::T))]         % [noobj]",
            // a pattern that declares one generic as two types matches nothing
            "[plus(2).mult('age')]==[?(plus(int::T@A).mult(str::T@A))]         % [noobj]",
            // a code and an inst test as the inst the code is: two instructions are not one
            "[plus(2).mult(2)]==[?(plus(int::T))]                              % [noobj]",
            "[plus(2)]==[?(plus(int::T).mult(int::T))]                         % [noobj]",
            "[plus(2)]==[?(plus(int::T))]                                      % [plus(2)]",
            // an arg's span is a run of args: plus({3}int::T) is plus(int::T,int::T,int::T), and the args are closed
            "[plus(1,2,3)]==[?plus({3}int::T)]                                 % [plus(1,2,3)]",
            "[plus(1,2,3)]==[?(plus({3}int::T))]                               % [plus(1,2,3)]",
            "[plus(1,2,3)]==[?plus({3}/m/int{1}::T)]                           % [plus(1,2,3)]",
            "[plus(1,2)]==[?plus({3}int::T)]                                   % [noobj]",
            "[plus(1,2,3,4)]==[?plus({3}int::T)]                               % [noobj]",
            "[plus(1,'a',3)]==[?plus({3}int::T)]                               % [noobj]",
            "[plus(1,2,3,4)]==[?plus({*}int::T)]                               % [plus(1,2,3,4)]",
            "[plus(1,'a','b')]==[?plus(int::T,{2}str::T)]                      % [plus(1,'a','b')]",
            "[plus(1,'a','b',2)]==[?plus(int::T,{2}str::T)]                    % [noobj]",
            "[plus('a','b')]==[?plus({?}int::T,{2}str::T)]                     % [plus('a','b')]",
            "[plus(1,'a','b')]==[?plus({?}int::T,{2}str::T)]                   % [plus(1,'a','b')]",
            // a point is how many at one arg, not a run of args
            "[plus(1,2,3)]==[?plus(int{3}::T)]                                 % [noobj]",
            "[plus({3}1)]==[?plus(int{3}::T)]                                  % [plus({3}1)]",
            // a spanned type is a whole run, so a signature tests a signature
            "[plus({3}int::T)]==[?plus({3}int::T)]                             % [plus({3}int::T)]",
            "[plus({3}int::T)]==[?plus({*}int::T)]                             % [plus({3}int::T)]",
            "[plus({*}int::T)]==[?plus({3}int::T)]                             % [noobj]",
            // and a code pattern binds a run instruction by instruction
            "[plus(1,2,3).mult(2)]==[?(plus({3}int::T).mult(int::T))]          % [plus(1,2,3).mult(2)]",
            "[plus(1,2).mult(2)]==[?(plus({3}int::T).mult(int::T))]            % [noobj]",
            // a lst select keeps the slot its filter rejects, as for any value
            "[1]==[?(str::T)]                                                  % [noobj]",
    }, delimiter = '%', quoteCharacter = '^')
    public void testCodePattern(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    /**
     * AN INSTRUCTION IS {span}inst{point}, as a type is (Marko, 2026-11-09): a leading coefficient is a run of the
     * instruction and a trailing one its point, how many it emits. The leading one was read as the code's coefficient
     * (and lost) at the head of a chain, and as the point mid-chain. The point is parallel, the span serial (Marko,
     * 2026-11-17): {@code {2}plus{3}(34)} is three lanes of the run {@code plus(34).plus(34)}, the point once, so
     * {@code plus(34).plus{3}(34)}; a point on every step of the run compounds, as a branch does.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "1.{2}plus{3}(34)                % {3}69",
            "1.plus(34).plus{3}(34)          % {3}69",
            "1.plus{3}(34).plus{3}(34)       % {9}69",
            "1.{2}plus{3}(34).count()        % 3",
            "1.{2}plus(34)                   % 69",
            "1.{3}plus(1)                    % 4",
            "1.{1}plus(1)                    % 2",
            "1.{0}plus(1)                    % 1",
            "1.{2}plus(1).mult(2)            % 6",
            "1.plus(1).{2}mult(2)            % 8",
            "1.plus(1).mult{2}(2)            % {2}4",
            "1.{2}plus(1).{2}mult(2)         % 12",
            "{1,2}.{2}plus(10)               % {21,22}",
    }, delimiter = '%')
    public void testAnInstructionsSpanIsARun(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    /**
     * A ZERO POINT ANNIHILATES (Marko, 2026-11-10): an instruction's output, times zero, is nothing, so the monad it
     * reaches dies. The rest of its chain never runs, a reducer after it counts nothing, a branch adds its zero, and
     * the next statement still runs. The swarm read a zero-pointed instruction as the end of the code, so the obj the
     * monad held joined the answer: {@code 2.plus{0}(2)} was {@code 2}.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "2.plus{0}(2)                    % noobj",
            "2.id{0}()                       % noobj",
            "2.plus{0}(2).plus(1)            % noobj",
            "2.id{2}().id{0}()               % noobj",
            "2.{2}id{0}()                    % noobj",
            "{1,2,3}.plus{0}(1)              % noobj",
            "2.branch(id{0}(),id())          % 2",
            "2.branch(id{0}(),id{0}())       % noobj",
            "2.id{0}().count()               % 0",
            "2.plus{0}(2); 5                 % 5",
    }, delimiter = '%')
    public void testAZeroPointAnnihilates(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    /**
     * A ONE-INSTRUCTION CODE IS ITS INSTRUCTION, from either side of a test, and nothing longer is, while the empty code
     * is the unit, id() (Marko, 2026-11-24): asserted in Java because the parser reads {@code plus(2)} as the instruction
     * itself, never as a one-instruction code, and has no literal for the empty code.
     */
    @Test
    public void testACodeAndAnInstTestAsTheInstTheCodeIs() {
        final Code user = ObjmtronSerializer.parse("plus(2).mult(3)");
        final Code pattern = ObjmtronSerializer.parse("plus(int::T).mult(int::T)");
        final Inst plus = user.inst(0);
        final Inst plusPattern = pattern.inst(0);
        assertTrue(code(List.of(plus)).test(plusPattern), "a one-instruction code tests as its instruction");
        assertTrue(plus.test(code(List.of(plusPattern))), "an inst tests a one-instruction code as its instruction");
        assertFalse(user.test(plusPattern), "two instructions are not one");
        assertFalse(plus.test(pattern), "nor against two instructions");
        assertFalse(code(List.of()).test(plusPattern), "the empty code is id(), not plus");
        final Inst unit = ObjmtronSerializer.parse("id()");
        assertTrue(code(List.of()).test(unit), "the empty code tests as the instruction it is, id()");
        assertTrue(unit.test(code(List.of())), "and id() tests the empty code as itself");
        assertTrue(user.test(pattern), "a code tests a code pattern");
        final Obj bound = ObjmtronSerializer.parse("plus(int::T@A).mult(A::T)");
        assertTrue(ObjmtronSerializer.parse("plus(2).mult(2)").test(bound), "A is int for the whole pattern");
        assertFalse(ObjmtronSerializer.parse("plus(2).mult('age')").test(bound), "so mult's 'age' is not an A");
        assertFalse(pattern.test(user), "a pattern's types do not test as values");
    }

    /**
     * A CODE THAT IS A REFERENCE IS ASSUMED TO BE WHAT IT REFERS TO: a test can neither dereference {@code !*} nor
     * evaluate the code to see what it would be. A category morphism's src and trgt are such references.
     */
    @Test
    public void testAReferenceCodeIsAssumedLegal() {
        final Code patterns = ObjmtronSerializer.parse("mult(int::T).mult(int::T)");
        assertTrue(ObjmtronSerializer.<Code>parse("!*abc.plus(1)").test(patterns), "!* against a code pattern");
        assertTrue(ObjmtronSerializer.<Code>parse("!*abc.plus(1)").test(patterns.inst(0)), "!* against an instruction");
        assertTrue(ObjmtronSerializer.<Code>parse("!@abc.plus(1)").test(patterns), "!@ too");
        assertTrue(ObjmtronSerializer.<Code>parse("!(plus(2)).plus(1)").test(patterns.inst(0)), "and !");
        assertFalse(ObjmtronSerializer.<Code>parse("plus(1).plus(1)").test(patterns.inst(0)), "a code that is no reference is still tested");
    }

    /**
     * A CODE'S TID IS GENERATIVE: its dom is its head instruction's dom and its rng its tail instruction's rng, read
     * afresh each time, so a fluent's tid follows the instruction it appends.
     */
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void testACodesTidIsGatheredFromItsHeadAndTail() {
        final Code code = ObjmtronSerializer.parse("plus?str<=str('a').count?int<=str{*}()");
        assertEquals(code.inst(0).tid().dom(), code.tid().dom(), "the dom is the head's");
        assertEquals(code.inst(1).tid().rng(), code.tid().rng(), "the rng is the tail's");
        assertNotEquals(code.tid().dom().basePath(), code.tid().rng().basePath(), "two different ends, so the test tells them apart");
        final mFluent fluent = id_();
        fluent.addInst(code.inst(1));
        assertEquals(code.inst(1).tid().rng(), fluent.tid().rng(), "an appended tail gives the rng");
    }
}
