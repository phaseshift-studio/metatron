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
     * A ONE-INSTRUCTION CODE IS ITS INSTRUCTION, from either side of a test, and nothing longer or emptier is: asserted in
     * Java because the parser reads {@code plus(2)} as the instruction itself, never as a one-instruction code.
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
        assertFalse(code(List.of()).test(plusPattern), "the empty code is no instruction");
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
