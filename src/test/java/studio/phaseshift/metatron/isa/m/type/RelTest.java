/*
 * Metatron: A Distributed Computing Language and Virtual Machine
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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.algebra.AbstractAlgebraTest;

import java.util.Set;

import static studio.phaseshift.metatron.algebra.Form.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

public class RelTest extends AbstractAlgebraTest<Rel> {

    public RelTest() {
        super(rel(uri("a"), jnt(1)), Set.of(PLUS_MONOID,MULT_MONOID));
    }

///////////////////////////////////////////////////////////////////////////
// BASIC CONSTRUCTION AND ACCESSORS

    /// ////////////////////////////////////////////////////////////////////////

    @ParameterizedTest
    @CsvSource(value = {
            "(a=>b).dom()                                                  % a",
            "(a=>b).rng()                                                  % b",
            "((a=>b)=>(c=>d)).dom()                                      % (a=>b)",
            "((a=>b)=>(c=>d)).rng()                                     % (c=>d)",
            "(1=>(2=>3)).dom()                                           % 1",
            "(1=>(2=>3)).rng()                                          % (2=>3)",
    }, delimiter = '%')
    public void testRelBasicAccessors(final String code, final String expected) {
        AbstractMetatronTest.checkCodeEvaluate(LOG, code, expected);
    }

///////////////////////////////////////////////////////////////////////////
// TYPE CONVERSIONS

    /// ////////////////////////////////////////////////////////////////////////

    @ParameterizedTest
    @CsvSource(value = {
            "(a=>b).as(rec::T)                                             % [a=>b]",
            "(a=>b).as(rec::T).as(lst::T).>>                               % [(a=>b)]",
            "(a=>b).as(lst::T)                                             % [a,b]",
            "(1=>2).as(lst::T)                                             % [1,2]",
            "((a=>b)=>(c=>d)).as(lst::T)                                   % [(a=>b),(c=>d)]",
    }, delimiter = '%')
    public void testRelTypeConversions(final String code, final String expected) {
        AbstractMetatronTest.checkCodeEvaluate(LOG, code, expected);
    }

///////////////////////////////////////////////////////////////////////////
// EQUALITY AND COMPARISON

    /// ////////////////////////////////////////////////////////////////////////

    @ParameterizedTest
    @CsvSource(value = {
            "(a=>b).eq((a=>b))                                             % true",
            "(a=>b).eq((b=>a))                                             % false",
            "(a=>b).eq((a=>c))                                             % false",
            "(1=>2).eq((1=>2))                                             % true",
            "(1=>2).eq((2=>1))                                             % false",
            "((a=>b)=>(c=>d)).eq(((a=>b)=>(c=>d)))                         % true",
            "((a=>b)=>(c=>d)).eq(((c=>d)=>(a=>b)))                         % false",
    }, delimiter = '%')
    public void testRelEquality(final String code, final String expected) {
        AbstractMetatronTest.checkCodeEvaluate(LOG, code, expected);
    }

///////////////////////////////////////////////////////////////////////////
// SPLIT AND SHIFT OPERATIONS

    /// ////////////////////////////////////////////////////////////////////////

    @ParameterizedTest
    @CsvSource(value = {
            "(a=>b)-<(_=>_)                                                % ((a=>b)=>(a=>b))",
            "(a=>b)-<(_=>_)>-                                              % {rel{2}::(a=>b)}",
            "(a=>b)-<(_=>_)>-.>-                                           % {uri{2}::a,uri{2}::b}",
            "(a=>b)-<(dom()=>rng())                                        % (a=>b)",
            "(a=>b)-<(dom().as(str::T)=>rng())                             % \"a\"=>b",
            "(1=>2)-<(dom().plus(10)=>-<(rng().mult(5)))                   % (11=>10)",
    }, delimiter = '%')
    public void testRelSplitShift(final String code, final String expected) {
        AbstractMetatronTest.checkCodeEvaluate(LOG, code, expected);
    }

///////////////////////////////////////////////////////////////////////////
// SELECT AND WHERE (PATTERN MATCHING AND FILTERING)

    /// ////////////////////////////////////////////////////////////////////////

    @ParameterizedTest
    @CsvSource(value = {
            "(a=>b).select((_=>b))                                         % (a=>b)",
            "(a=>1).select((_=>plus(10)))                                  % (a=>11)",
            "(2=>1).select((mult(4)=>plus(5)))                             % (8=>6)",
            "(1=>(2=>3)).select((mult(4)=>(_=>plus(10))))                  % (4=>(2=>13))",
            "1=>2=>3.select((mult(4)=>(_=>plus(10)))).where((_=>(_=>14))) % (4=>(2=>14))",
            "1=>2=>3.select((mult(4)=>(_=>plus(10)))).where((_=>(_=>13))) % noobj",
            "(a=>b).select((a=>_))                                         % (a=>b)",
            "(a=>b).select((c=>_))                                         % noobj",
            "(a=>b).select((_=>b))                                         % (a=>b)",
            "(a=>b).select((_=>c))                                         % noobj",
    }, delimiter = '%')
    public void testRelSelectWhere(final String code, final String expected) {
        AbstractMetatronTest.checkCodeEvaluate(LOG, code, expected);
    }

///////////////////////////////////////////////////////////////////////////
// ARITHMETIC OPERATIONS (PLUS)

    /// ////////////////////////////////////////////////////////////////////////

    @ParameterizedTest
    @CsvSource(value = {
            "(a=>1).plus((b=>2))                                           % {(a=>1),(b=>2)}",
            "(a=>1).plus((a=>2))                                           % {(a=>1),(a=>2)}",
            "(1=>2).plus((3=>4))                                           % {(1=>2),(3=>4)}",
            "(a=>b).plus((c=>d)).plus((e=>f))                              % {(a=>b),(c=>d),(e=>f)}",
    }, delimiter = '%')
    public void testRelPlus(final String code, final String expected) {
        AbstractMetatronTest.checkCodeEvaluate(LOG, code, expected);
    }

///////////////////////////////////////////////////////////////////////////
// NESTED RELATIONS

    /// ////////////////////////////////////////////////////////////////////////

    @ParameterizedTest
    @CsvSource(value = {
            "(a=>(b=>c)).dom()                                           % a",
            "(a=>(b=>c)).rng()                                          % (b=>c)",
            "(a=>(b=>c)).rng().dom()                                  % b",
            "(a=>(b=>c)).rng().rng()                                 % c",
            "((a=>b)=>(c=>d)).dom()                              % a",
            "((a=>b)=>(c=>d)).rng()                             % (c=>d)",
            "((a=>b)=>(c=>d)).rng().dom()                             % c",
            "((a=>b)=>(c=>d)).rng().rng()                            % d",
            "(1=>(2=>(3=>4))).rng().rng()                            % (3=>4)",
            "(1=>(2=>(3=>4))).rng().rng().dom()                    % 3",
            "(1=>(2=>(3=>4))).rng().rng().rng()                   % 4",
    }, delimiter = '%')
    public void testRelNested(final String code, final String expected) {
        AbstractMetatronTest.checkCodeEvaluate(LOG, code, expected);
    }

///////////////////////////////////////////////////////////////////////////
// COEFFICIENTS ON RELATIONS

    /// ////////////////////////////////////////////////////////////////////////

    @ParameterizedTest
    @CsvSource(value = {
            "{2}(a=>b).dom()                                             % {2}a",
            "{2}(a=>b).rng()                                            % {2}b",
            "{3}(1=>2).dom()                                             % 3",
            "{3}(1=>2).rng()                                            % 6",
            "{2,5}(a=>b).dom()                                           % {2,5}a",
            "{2,5}(a=>b).rng()                                          % {2,5}b",
    }, delimiter = '%')
    public void testRelCoefficients(final String code, final String expected) {
        AbstractMetatronTest.checkCodeEvaluate(LOG, code, expected);
    }

///////////////////////////////////////////////////////////////////////////
// RELATIONS WITH DIFFERENT TYPES

    /// ////////////////////////////////////////////////////////////////////////

    @ParameterizedTest
    @CsvSource(value = {
            "(1=>\"hello\").dom()                                        % 1",
            "(1=>\"hello\").rng()                                       % \"hello\"",
            "(\"key\"=>42).dom()                                         % \"key\"",
            "(\"key\"=>42).rng()                                        % 42",
            "([a,b]=>[c,d]).dom()                                        % [a,b]",
            "([a,b]=>[c,d]).rng()                                       % [c,d]",
            "([a=>b]=>[c=>d]).dom()                                      % [a=>b]",
            "([a=>b]=>[c=>d]).rng()                                     % [c=>d]",
    }, delimiter = '%')
    public void testRelMixedTypes(final String code, final String expected) {
        AbstractMetatronTest.checkCodeEvaluate(LOG, code, expected);
    }

///////////////////////////////////////////////////////////////////////////
// RELATION COMPOSITION AND CHAINING

    /// ////////////////////////////////////////////////////////////////////////

    @ParameterizedTest
    @CsvSource(value = {
            "a=>b=>(c=>d)                                                  % ((a=>b)=>(c=>d))",
            "a=>b=>c                                                       % (a=>(b=>c))",
            "a=>b=>c=>d                                                    % (a=>(b=>(c=>d)))",
            "1=>2=>3=>4                                                    % (1=>(2=>(3=>4)))",
    }, delimiter = '%')
    public void testRelComposition(final String code, final String expected) {
        AbstractMetatronTest.checkCodeEvaluate(LOG, code, expected);
    }

///////////////////////////////////////////////////////////////////////////
// RELATION PATTERN MATCHING WITH WILDCARDS

    /// ////////////////////////////////////////////////////////////////////////

    @ParameterizedTest
    @CsvSource(value = {
            "(a=>b).select((_=>_))                                         % (a=>b)",
            "(1=>2).select((_=>_))                                         % (1=>2)",
            "(a=>b).select((a=>_))                                         % (a=>b)",
            "(a=>b).select((_=>b))                                         % (a=>b)",
            "(x=>y).select((a=>_))                                         % noobj",
            "(x=>y).select((_=>b))                                         % noobj",
    }, delimiter = '%')
    public void testRelWildcardMatching(final String code, final String expected) {
        AbstractMetatronTest.checkCodeEvaluate(LOG, code, expected);
    }

///////////////////////////////////////////////////////////////////////////
// RELATION TRANSFORMATIONS

    /// ////////////////////////////////////////////////////////////////////////

    @ParameterizedTest
    @CsvSource(value = {
            "(a=>b).select((dom().as(str::T)=>rng()))                      % (\"a\"=>b)",
            "(1=>2).select((dom().plus(10)=>rng()))                        % (11=>2)",
            "(1=>2).select((dom()=>-<(rng().mult(5))))                     % (1=>10)",
            "(1=>2).select((dom().plus(10)=>-<(rng().mult(5))))            % (11=>10)",
            "(a=>1).select((dom()=>-<(rng().plus(100))))                   % (a=>101)",
    }, delimiter = '%')
    public void testRelTransformations(final String code, final String expected) {
        AbstractMetatronTest.checkCodeEvaluate(LOG, code, expected);
    }

///////////////////////////////////////////////////////////////////////////
// EDGE CASES AND SPECIAL VALUES

    /// ////////////////////////////////////////////////////////////////////////

    @ParameterizedTest
    @CsvSource(value = {
            "(noobj=>b).dom()                                            % noobj",
            "(a=>noobj).rng()                                           % noobj",
            "(0=>0).dom()                                                % 0",
            "(0=>0).rng()                                               % 0",
            "\"\"=>(\"\")                                                % (\"\"=>\"\")",
            "[]=>[]                                                       % ([]=>[])",
    }, delimiter = '%')
    public void testRelEdgeCases(final String code, final String expected) {
        AbstractMetatronTest.checkCodeEvaluate(LOG, code, expected);
    }

///////////////////////////////////////////////////////////////////////////
// RELATION COUNT AND STRUCTURE

    /// ////////////////////////////////////////////////////////////////////////

    @ParameterizedTest
    @CsvSource(value = {
            "(a=>b).cc()                                                   % 2",
            "((a=>b)=>(c=>d)).cc()                                         % 2",
            "(1=>(2=>3)).cc()                                              % 2",
    }, delimiter = '%')
    public void testRelCount(final String code, final String expected) {
        AbstractMetatronTest.checkCodeEvaluate(LOG, code, expected);
    }

///////////////////////////////////////////////////////////////////////////
// RELATIONS AS GRAPH EDGES

    /// ////////////////////////////////////////////////////////////////////////

    @ParameterizedTest
    @CsvSource(value = {
            "(marko=>knows).dom()                                        % marko",
            "(marko=>knows).rng()                                       % knows",
            "(marko=>(knows=>claude)).rng()                             % (knows=>claude)",
            "(marko=>(knows=>[claude,ollama])).rng().rng()           % [claude,ollama]",
    }, delimiter = '%')
    public void testRelAsGraphEdges(final String code, final String expected) {
        AbstractMetatronTest.checkCodeEvaluate(LOG, code, expected);
    }

///////////////////////////////////////////////////////////////////////////
// RELATION STREAMS AND ELEMENTS

    /// ////////////////////////////////////////////////////////////////////////

    @ParameterizedTest
    @CsvSource(value = {
            "(a=>b)>>+                                                     % {a,b}",
            "(1=>2)>>+                                                     % 3",
            "((a=>b)=>(c=>d))>>+                                           % {(a=>b),(c=>d)}",
    }, delimiter = '%')
    public void testRelStreams(final String code, final String expected) {
        AbstractMetatronTest.checkCodeEvaluate(LOG, code, expected);
    }

///////////////////////////////////////////////////////////////////////////
// RING STRUCTURE - MULTIPLICATIVE IDENTITY (ONE)

    /// ////////////////////////////////////////////////////////////////////////

    @ParameterizedTest
    @CsvSource(value = {
            "(a=>b).one()                                                  % (id()=>id())",
            "(1=>2).one()                                                  % (id()=>id())",
            "(a=>b).one().?=(one())                                        % true",
            "(a=>b).?=(one())                                              % false",
            "(id()=>id()).?=(one())                                        % true",
    }, delimiter = '%')
    public void testRelMultiplicativeIdentity(final String code, final String expected) {
        AbstractMetatronTest.checkCodeEvaluate(LOG, code, expected);
    }

///////////////////////////////////////////////////////////////////////////
// RING STRUCTURE - ADDITIVE IDENTITY (ZERO)

    /// ////////////////////////////////////////////////////////////////////////

    @ParameterizedTest
    @CsvSource(value = {
            "(a=>b).zero()                                                 % (noobj=>noobj)",
            "(1=>2).zero()                                                 % (noobj=>noobj)",
            "(a=>b).zero().?=(zero())                                      % true",
            "(a=>b).?=(zero())                                             % false",
            "(noobj=>noobj).?=(zero())                                     % true",
    }, delimiter = '%')
    public void testRelAdditiveIdentity(final String code, final String expected) {
        AbstractMetatronTest.checkCodeEvaluate(LOG, code, expected);
    }

///////////////////////////////////////////////////////////////////////////
// RING STRUCTURE - MULTIPLICATION (COMPOSITION)

    /// ////////////////////////////////////////////////////////////////////////

    @ParameterizedTest
    @CsvSource(value = {
            "(a=>b).mult((b=>c))                                           % (a=>c)",
            "(1=>2).mult((2=>3))                                           % (1=>3)",
            "(a=>b).mult((b=>c)).mult((c=>d))                              % (a=>d)",
            "(x=>y).mult((y=>z)).mult((z=>w))                              % (x=>w)",
            // Identity laws
            "(a=>b).mult((id()=>id()))                                     % (a=>b)",
            "(id()=>id()).mult((a=>b))                                     % (a=>b)",
            // Non-composable relations return zero
            "(a=>b).mult((c=>d))                                           % (noobj=>noobj)",
            "(1=>2).mult((3=>4))                                           % (noobj=>noobj)",
            // Zero laws
            "(a=>b).mult((noobj=>noobj))                                   % (noobj=>noobj)",
            "(noobj=>noobj).mult((a=>b))                                   % (noobj=>noobj)",
    }, delimiter = '%')
    public void testRelMultiplication(final String code, final String expected) {
        AbstractMetatronTest.checkCodeEvaluate(LOG, code, expected);
    }

///////////////////////////////////////////////////////////////////////////
// RING STRUCTURE - NEGATION (INVERSE/SWAP)

    /// ////////////////////////////////////////////////////////////////////////

    @ParameterizedTest
    @CsvSource(value = {
            "(a=>b).neg()                                                  % (b=>a)",
            "(1=>2).neg()                                                  % (2=>1)",
            // "(a=>b).neg().neg()                                            % (a=>b)",
            "((a=>b)=>(c=>d)).neg()                                        % ((c=>d)=>(a=>b))",
            "(noobj=>noobj).neg()                                          % (noobj=>noobj)",
            "(id()=>id()).neg()                                            % (id()=>id())",
    }, delimiter = '%')
    public void testRelNegation(final String code, final String expected) {
        AbstractMetatronTest.checkCodeEvaluate(LOG, code, expected);
    }

///////////////////////////////////////////////////////////////////////////
// RING STRUCTURE - ADDITION (CREATES OBJS)

    /// ////////////////////////////////////////////////////////////////////////

    @ParameterizedTest
    @CsvSource(value = {
            "(a=>b).plus((c=>d))                                           % {(a=>b),(c=>d)}",
            "(1=>2).plus((3=>4))                                           % {(1=>2),(3=>4)}",
            "(a=>b).plus((c=>d)).plus((e=>f))                              % {(a=>b),(c=>d),(e=>f)}",
            // Adding zero
            "(a=>b).plus((noobj=>noobj))                                   % {(a=>b),(noobj=>noobj)}",
    }, delimiter = '%')
    public void testRelAddition(final String code, final String expected) {
        AbstractMetatronTest.checkCodeEvaluate(LOG, code, expected);
    }

///////////////////////////////////////////////////////////////////////////
// RING STRUCTURE - SUBTRACTION

    /// ////////////////////////////////////////////////////////////////////////

    @ParameterizedTest
    @Disabled
    @CsvSource(value = {
            "(a=>b).minus((c=>d))                                          % {(a=>b),(d=>c)}",
            "(1=>2).minus((3=>4))                                          % {(1=>2),(4=>3)}",
            "(a=>b).minus((a=>b))                                          % {(a=>b),(b=>a)}",
    }, delimiter = '%')
    public void testRelSubtraction(final String code, final String expected) {
        AbstractMetatronTest.checkCodeEvaluate(LOG, code, expected);
    }

///////////////////////////////////////////////////////////////////////////
// RING AXIOMS - ASSOCIATIVITY

    /// ////////////////////////////////////////////////////////////////////////

    @ParameterizedTest
    @CsvSource(value = {
            // Multiplicative associativity: (a*b)*c = a*(b*c)
            "(a=>b).mult((b=>c)).mult((c=>d))                              % (a=>d)",
            "(a=>b).mult((b=>c).mult((c=>d)))                              % (a=>d)",
            "(1=>2).mult((2=>3)).mult((3=>4))                              % (1=>4)",
            "(1=>2).mult((2=>3).mult((3=>4)))                              % (1=>4)",
    }, delimiter = '%')
    public void testRelAssociativity(final String code, final String expected) {
        AbstractMetatronTest.checkCodeEvaluate(LOG, code, expected);
    }

///////////////////////////////////////////////////////////////////////////
// RING AXIOMS - IDENTITY LAWS

    /// ////////////////////////////////////////////////////////////////////////

    @ParameterizedTest
    @CsvSource(value = {
            // Right identity: r * 1 = r
            "(a=>b).mult((a=>b).one())                                     % (a=>b)",
            "(1=>2).mult((1=>2).one())                                     % (1=>2)",
            // Left identity: 1 * r = r
            "(a=>b).one().mult((a=>b))                                     % (a=>b)",
            "(1=>2).one().mult((1=>2))                                     % (1=>2)",
    }, delimiter = '%')
    public void testRelIdentityLaws(final String code, final String expected) {
        AbstractMetatronTest.checkCodeEvaluate(LOG, code, expected);
    }

///////////////////////////////////////////////////////////////////////////
// RING AXIOMS - ZERO LAWS

    /// ////////////////////////////////////////////////////////////////////////

    @ParameterizedTest
    @CsvSource(value = {
            // Right zero: r * 0 = 0
            "(a=>b).mult((a=>b).zero())                                    % (noobj=>noobj)",
            "(1=>2).mult((1=>2).zero())                                    % (noobj=>noobj)",
            // Left zero: 0 * r = 0
            "(a=>b).zero().mult((a=>b))                                    % (noobj=>noobj)",
            "(1=>2).zero().mult((1=>2))                                    % (noobj=>noobj)",
    }, delimiter = '%')
    public void testRelZeroLaws(final String code, final String expected) {
        AbstractMetatronTest.checkCodeEvaluate(LOG, code, expected);
    }

///////////////////////////////////////////////////////////////////////////
// RING STRUCTURE - COMPOSITION CHAINS

    /// ////////////////////////////////////////////////////////////////////////

    @ParameterizedTest
    @CsvSource(value = {
            // Long composition chains
            "(a=>b).mult((b=>c)).mult((c=>d)).mult((d=>e))                 % (a=>e)",
            "(1=>2).mult((2=>3)).mult((3=>4)).mult((4=>5))                 % (1=>5)",
            // Composition with identity in the middle
            "(a=>b).mult((id()=>id())).mult((b=>c))                        % (a=>c)",
            // Mixed composable and non-composable
            "(a=>b).mult((b=>c)).mult((x=>y))                              % (noobj=>noobj)",
    }, delimiter = '%')
    public void testRelCompositionChains(final String code, final String expected) {
        AbstractMetatronTest.checkCodeEvaluate(LOG, code, expected);
    }

///////////////////////////////////////////////////////////////////////////
// RING STRUCTURE - INVERSE PROPERTIES

    /// ////////////////////////////////////////////////////////////////////////

    @ParameterizedTest
    @CsvSource(value = {
            // Double negation: -(-r) = r
            //"(a=>b).neg().neg()                                            % (a=>b)",
            //"(1=>2).neg().neg()                                            % (1=>2)",
            // Negation of composition: -(a*b) vs (-a)*(-b)
            "(a=>b).mult((b=>c)).neg()                                     % (c=>a)",
            // Composition of inverses (reverse order)
            "(a=>b).neg().mult((c=>a).neg())                               % (b=>c)",
    }, delimiter = '%')
    public void testRelInverseProperties(final String code, final String expected) {
        AbstractMetatronTest.checkCodeEvaluate(LOG, code, expected);
    }

///////////////////////////////////////////////////////////////////////////
// RING STRUCTURE - NESTED RELATION COMPOSITION

    /// ////////////////////////////////////////////////////////////////////////

    @ParameterizedTest
    @CsvSource(value = {
            "(a=>(b=>c)).mult(((b=>c)=>d))                                 % (a=>d)",
            "((x=>y)=>z).neg()                                             % (z=>(x=>y))",
            "(x=>y=>z).neg()                                             % (z=>(x=>y))",
            "(1=>(2=>3)).mult(((2=>3)=>4))                                 % (1=>4)",
    }, delimiter = '%')
    public void testRelNestedComposition(final String code, final String expected) {
        AbstractMetatronTest.checkCodeEvaluate(LOG, code, expected);
    }

///////////////////////////////////////////////////////////////////////////
// RING STRUCTURE - COEFFICIENTS WITH RING OPERATIONS

    /// ////////////////////////////////////////////////////////////////////////

    @ParameterizedTest
    @CsvSource(value = {
            "{2}(a=>b).mult((b=>c))                                        % {2}(a=>c)",
            "{3}(1=>2).mult((2=>3))                                        % {3}(1=>3)",
            "rel{3}::(1=>2).mult(rel{4}::(2=>3))                           % rel{12}::(1=>3)",
            "{3}(1=>2).mult({4}(2=>3))                                     % {12}(1=>3)",
            "(1=>2).mult({(2=>3),{4}(2=>4)})                               % {{3}(1=>3),{4}(1=>4)}",
            "{3}(1=>2).mult({(2=>3),{4}(2=>4)})                            % {{3}(1=>3),{12}(1=>4)}",
            "{2}(a=>b).neg()                                               % {2}(b=>a)",
            "{5}(a=>b).plus((c=>d))                                        % {{5}(a=>b),(c=>d)}",
          //  "{5}(a=>b).plus({(c=>d),{6}({7}c=>{2}e)})                      % {{5}(a=>b),(c=>d),{6}({7}c=>{2}e)}",
            "{5}(a=>b).plus({3}(c=>d))                                     % {{5}(a=>b),{3}(c=>d)}",
    }, delimiter = '%')
    public void testRelRingWithCoefficients(final String code, final String expected) {
        AbstractMetatronTest.checkCodeEvaluate(LOG, code, expected);
    }
}
