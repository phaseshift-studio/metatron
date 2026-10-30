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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.TestData;
import studio.phaseshift.metatron.algebra.AbstractAlgebraTest;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.REL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Poly.IMMUTABLE;
import static studio.phaseshift.metatron.isa.m.type.Poly.MUTABLE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

public class RelTest extends AbstractAlgebraTest {

    @Override
    protected Type carrier() {
        return REL_TYPE;
    }

    /// NOTE (kept from the interface-based guard this class used to carry): rel-arrow is a pair of monoids but not
    /// a rig — plus has the noobj sink as a live zero (0+a = a = a+0) while mult composes over that sink only
    /// partially (0*a => noobj=>noobj), so the rig annihilator 0*a = 0 does not hold for arrows; union over full
    /// relations lives at the lst-of-arrows level. rel::T declares no theory instance of its own (yet), so the derived
    /// test above skips it by name rather than going silently green.

    @Override
    protected Obj sample() {
        return rel(uri("a"), jnt(1));
    }

///////////////////////////////////////////////////////////////////////////
// BASIC CONSTRUCTION AND ACCESSORS

    /// ////////////////////////////////////////////////////////////////////////

    ///
    /// zero-sink laws — zero of rel::T is the sink rel (noobj=>noobj), a proper member of the
    /// type.  Pinned in the space-bearing ring syntax (spaceless * is pointer-deref, not mult):
    ///   (a=>b) + (noobj=>noobj) = (a=>b)         plus-identity, zero on the right
    ///   (noobj=>noobj) + (a=>b) = (a=>b)         plus-identity, zero on the left
    ///   (a=>b) * (noobj=>noobj) = (noobj=>noobj) annihilation to zero (zero on the right, green)
    ///
    /// KNOWN CANONICALIZATION TARGET (red, not pinned as a law): zero on the *left* through a
    /// member, zero().mult(a), renders the zero as noobj=>noobj while the bare zero prints noobj —
    /// a zero-refinement render split (zero().zero() != zero() as a render).  Value is identical
    /// (Rel.zero() is receiver-static); Int/Lst do not split.  Fix = canonical zero-tid render
    /// on rel, which touches the type system (JObjFactory reads tid.isZero()), so it is
    /// isolated here rather than forced green.
    @ParameterizedTest
    @CsvSource(value = {
            "(a=>b) + (noobj=>noobj)                                         % (a=>b)",
            "(noobj=>noobj) + (a=>b)                                         % (a=>b)",
            "(a=>b) * (noobj=>noobj)                                         % (noobj=>noobj)",
    }, delimiter = '%')
    public void testRelZeroSink(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    ///
    /// canonical-zero divergence probe — does zero resolve identically via a deref'd element
    /// (*a) vs a literal rel (a=>1)?  If these disagree, the zero-of-rel is routing through a
    /// non-rel dispatch (bare noobj) on one path and the rel sink (noobj=>noobj) on the other.
    @ParameterizedTest
    @CsvSource(value = {
            "*a.zero()                                                    % (a=>1).zero()",
            "*a.zero().type()                                             % rel::T",
            "*a.zero() + *a                                              % *a",
            "*a * (noobj=>noobj)                                         % (noobj=>noobj)",
            "*a.zero().tid()                                             % (a=>1).zero().tid()",
            "*a.zero().type()                                            % (a=>1).zero().type()",
    }, delimiter = '%')
    public void testRelZeroCanonical(final String code, final String expected) {
        Machine.current().memory().write("a", rel(uri("a"), jnt(1)));
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    ///
    /// Is noobj the universal zero?  Decide per type: is X.zero() the same element as bare noobj?
    ///   int : 5.zero()  = 0          (?== noobj)
    ///   rel : (a=>1).zero() = noobj=>noobj (?== noobj)
    ///   lst : [,].zero()? / [1].zero() = [,] (?== noobj)
    /// and does noobj act as an additive identity across types: noobj + 5 = 5 ?
    /// (These are EXPECTED-TO-DIVERGE probes: they pin, type by type, whether noobj IS the zero
    ///  or merely the value a zero renders/carries.)
    @ParameterizedTest
    @Disabled
    @CsvSource(value = {
            "5.zero()                                                    % 0",
            "5.zero()                                                    % noobj",
            "(a=>1).zero()                                               % noobj",
            "[1,2].zero()                                               % [,]",
            "[1,2].zero()                                               % noobj",
            "noobj + 5                                                   % 5",
    }, delimiter = '%')
    public void testNoobjIsUniversalZero(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

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
            "1=>2=>3.select((mult(4)=>(_=>plus(10)))).isa((_=>(_=>14)))    % (4=>(2=>14))",
            "1=>2=>3.select((mult(4)=>(_=>plus(10)))).isa((_=>(_=>13)))    % noobj",
            "(a=>b).select((a=>_))                                         % (a=>b)",
            "(a=>b).select((c=>_))                                         % noobj",
            "(a=>b).select((_=>b))                                         % (a=>b)",
            "(a=>b).select((_=>c))                                         % noobj",
    }, delimiter = '%')
    public void testRelSelectIsA(final String code, final String expected) {
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
            "(a=>b).neg().neg()                                            % (a=>b)",
            "(1=>2).neg().neg()                                            % (1=>2)",
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
            "{5}(a=>b).plus({(c=>d),{6}({7}c=>{2}e)})                      % {{5}(a=>b),(c=>d),{6}({7}c=>{2}e)}",
            "{5}(a=>b).plus({3}(c=>d))                                     % {{5}(a=>b),{3}(c=>d)}",
    }, delimiter = '%')
    public void testRelRingWithCoefficients(final String code, final String expected) {
        AbstractMetatronTest.checkCodeEvaluate(LOG, code, expected);
    }

    /// ////////////////////////////////////////////////////////////////////////
    // MUTABILITY — at(first, second, operation)
    @ParameterizedTest
    @CsvSource(value = {
            // MUTABLE set: mutate same reference
            "(a=>b)  | c   | d   | true",
            "(1=>2)  | 3   | 4   | true",
    }, delimiter = '|')
    public void testMutableSet(final String relStr, final String first, final String second,
                               final boolean expectSame) {
        final Rel original = ObjmtronSerializer.parse(relStr).asRel();
        final Obj f = ObjmtronSerializer.parse(first);
        final Obj s = ObjmtronSerializer.parse(second);
        final Rel result = (Rel) original.at(f, s, MUTABLE);
        if (expectSame)
            assertSame(original, result, "MUTABLE should return same reference");
        assertEquals(f, result.first());
        assertEquals(s, result.second());
    }

    @ParameterizedTest
    @CsvSource(value = {
            // IMMUTABLE set: return new reference, original untouched
            "(a=>b)  | c   | d",
            "(1=>2)  | 3   | 4",
    }, delimiter = '|')
    public void testImmutableSet(final String relStr, final String first, final String second) {
        final Rel original = ObjmtronSerializer.parse(relStr).asRel();
        final Obj f = ObjmtronSerializer.parse(first);
        final Obj s = ObjmtronSerializer.parse(second);
        final Rel clone = (Rel) original.at(f, s, IMMUTABLE);
        assertNotSame(original, clone, "IMMUTABLE should return new reference");
        // original unchanged
        final Rel originalRef = ObjmtronSerializer.parse(relStr).asRel();
        assertEquals(originalRef.first(), original.first());
        assertEquals(originalRef.second(), original.second());
        // clone has new values
        assertEquals(f, clone.first());
        assertEquals(s, clone.second());
    }

///////////////////////////////////////////////////////////////////////////
// PATH — the rel poly is the path's VERTEX TYPES

    /// ////////////////////////////////////////////////////////////////////////
    ///
    /// `rel[V0,V1,…,Vn-1]` reads the right-nested chain `V0 => (V1 => … => Vn-1)`: the rel's `first` is the
    /// `V0` vertex, each `second` that is a rel consumes the next vertex, and a `second` that is an ordinary obj
    /// closes the path — it is typed by the vertex entry it reaches, and every entry the poly names after it must be
    /// nullable (a trailing `{?}` is a vertex the path omits, not one it failed to supply). So the poly is exhausted
    /// at the closing vertex up to a nullable tail: a chain with more vertices than the poly (or fewer, where the
    /// missing ones are required) is an ARITY mismatch while a vertex type that disagrees at its position is a
    /// POSITION mismatch. Both are the one failure the type system already reports ("obj does not match … predicate
    /// apply failed on poly"), so both are `<ERROR>` here.
    ///
    /// NO INFERENCE: only an EXPLICITLY written poly is judged — the unannotated chain keeps `rel::T`, and nothing
    /// computes a poly from the value.
    @ParameterizedTest
    @CsvSource(value = {
            // TWO vertices: one hop
            "rel[A,B]::(a=>b)                                    % rel[A,B]::(a=>b)",
            "rel[int,str]::(1=>'tally')                         % rel[int,str]::(1=>'tally')",
            // THREE vertices: two hops
            "rel[A,B,C]::(a=>(b=>c))                             % rel[A,B,C]::(a=>(b=>c))",
            "rel[int,str,bool]::(1=>('tally'=>true))             % rel[int,str,bool]::(1=>('tally'=>true))",
            // FOUR vertices: three hops
            "rel[A,B,C,D]::(a=>(b=>(c=>d)))                      % rel[A,B,C,D]::(a=>(b=>(c=>d)))",
            // the poly SURVIVES on the value's type: the value is typed by the declaration it was written with
            "(rel[A,B]::(a=>b)).type()                           % rel[A,B]::T",
            "(rel[A,B,C]::(a=>(b=>c))).type()                    % rel[A,B,C]::T",
            "(rel[A,B,C,D]::(a=>(b=>(c=>d)))).type()             % rel[A,B,C,D]::T",
            // ARITY: the poly names three vertices, the chain has two
            "rel[A,B,C]::(a=>b)                                  % <ERROR>",
            // ARITY: the poly names two vertices, the chain has three
            "rel[A,B]::(a=>(b=>c))                               % <ERROR>",
            "rel[A,B]::(a=>(b=>(c=>d)))                          % <ERROR>",
            // POSITION: the closing vertex's type disagrees with the LAST entry
            "rel[int,str]::(1=>2)                                % <ERROR>",
            // POSITION: the opening vertex's type disagrees with V0
            "rel[str,int]::(1=>2)                                % <ERROR>",
            // POSITION: the middle vertex disagrees while both ends agree
            "rel[int,str,int]::(1=>(2=>3))                       % <ERROR>",
            // a path is not any of its vertices: the same type written without the poly accepts the chain
            "rel::(a=>(b=>c))                                    % rel::(a=>(b=>c))",
            "(rel::(a=>(b=>c))).type()                           % rel::T",
            // NO INFERENCE: an unannotated chain stays rel::T whatever its shape
            "(marko=>comida=>taco).type()                        % rel::T",
    }, delimiter = '%')
    public void testRelPolyIsThePath(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    /**
     * A PATH HAS AT LEAST ONE HOP, SO {@code rel[V]} IS ILLEGAL. A rel poly names the vertices of a chain, and a
     * chain of one vertex is not a chain: the declaration is rejected where the poly is VALIDATED — at type
     * construction, before any value can be held to it — so both spellings fail.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "rel[A]::T                   % <ERROR>",
            "rel[A]::(a=>b)              % <ERROR>",
            "rel[int]::(1=>2)            % <ERROR>",
            // a NAMED type's bracket is on its tid only, and is validated the same way
            "rel[A]::T@stub              % <ERROR>",
    }, delimiter = '%')
    public void testRelPolyNeedsAPath(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    /**
     * A ZERO-ADMITTING VERTEX ENTRY IS OPTIONAL, AND ITS COEFFICIENT IS THE VALUE'S MULTIPLICITY THERE. No
     * coefficient and {@code {1}} are exactly one value at that vertex, and {@code {?}} is ZERO OR ONE, so
     * {@code rel[A,B{?},C]} accepts both {@code A=>C} (B skipped) and {@code A=>B=>C} (B present). The multiplicity
     * reading is pinned by {@link #testVertexCoefficientIsTheValueMultiplicity}.
     */
    @ParameterizedTest
    @CsvSource(value = {
            // the optional vertex is PRESENT
            "rel[A,B{?},C]::(a=>(b=>c))                          % rel[A,B{?},C]::(a=>(b=>c))",
            // ... and ABSENT: the chain closes straight from A to C
            "rel[A,B{?},C]::(a=>c)                               % rel[A,B{?},C]::(a=>c)",
            // an explicit {1} is exactly one, so the same poly spelled with {1} still insists on the middle vertex
            "rel[A,B{1},C]::(a=>(b=>c))                          % rel[A,B{1},C]::(a=>(b=>c))",
            "rel[A,B{1},C]::(a=>c)                               % <ERROR>",
            "rel[A,B{?},C]::(a=>c)                               % rel[uri,uri]::(a=>c)",
            "rel[A,B{*},C]::(a=>(b=>c))                          % rel[uri,uri,uri]::(a=>(b=>c))",
            // the optional vertex is still TYPED when present: a wrong type at that depth is a position mismatch
            "rel[int,str{?},bool]::(1=>(2=>true))                % <ERROR>",
            // ... and the closed form is held to the LAST vertex
            "rel[int,str{?},bool]::(1=>'tally')                  % <ERROR>",
            "rel[int,str{?},bool]::(1=>true)                     % rel[int,str{?},bool]::(1=>true)",
            // A TRAILING OPTIONAL VERTEX IS OMITTED, NOT MISSING: the chain closes at the last vertex present, and
            // the {?} entries the poly still names after it admit zero
            "rel[int,int,str{?}]::(1=>2)                          % rel[int,int,str{?}]::(1=>2)",
            "rel[int,int,str{?}]::(1=>2=>'xyz')                   % rel[int,int,str{?}]::(1=>2=>'xyz')",
            // ... but a PRESENT trailing vertex of the wrong type is not an absent one
            "rel[int,int,str{?}]::(1=>2=>3.5)                     % <ERROR>",
            // the middle optional vertex is still skipped by the ordinary closing read
            "rel[int,int{?},str]::(1=>'xyz')                      % rel[int,int{?},str]::(1=>'xyz')",
            // a REQUIRED trailing vertex is not skippable, so the tail is still an arity mismatch
            "rel[int,int,str]::(1=>2)                             % <ERROR>",
            // a LEADING optional vertex may be skipped: the path starts at the next one
            "rel[int{?},str,bool]::('x'=>true)                    % rel[int{?},str,bool]::('x'=>true)",
            "rel[int{?},str,bool]::(1=>('x'=>true))               % rel[int{?},str,bool]::(1=>('x'=>true))",
            // TWO optional vertices in a row: neither, either or both may be skipped
            "rel[int,str{?},real{?},bool]::(1=>true)                  % rel[int,str{?},real{?},bool]::(1=>true)",
            "rel[int,str{?},real{?},bool]::(1=>(2.5=>true))           % rel[int,str{?},real{?},bool]::(1=>(2.5=>true))",
            "rel[int,str{?},real{?},bool]::(1=>('x'=>true))           % rel[int,str{?},real{?},bool]::(1=>('x'=>true))",
            "rel[int,str{?},real{?},bool]::(1=>('x'=>(2.5=>true)))    % rel[int,str{?},real{?},bool]::(1=>('x'=>(2.5=>true)))",
            // ... but a present vertex is still typed by its position: a bool is not a str or a real
            "rel[int,str{?},real{?},bool]::(1=>(true=>2.5))           % <ERROR>",
    }, delimiter = '%')
    public void testRelOptionalVertex(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    /**
     * A VERTEX COEFFICIENT IS THE MULTIPLICITY OF THE VALUE AT THAT VERTEX — not a hop count and not a repetition of
     * the vertex. {@code rel[int,person{2}]} is a TWO-vertex chain whose SECOND vertex holds two people
     * ({@code 1=>{person::[…],person::[…]}}), the same reading the tensor gives an index ({@code lst[int{2},str]}
     * holds two ints at index 0). Matching therefore compares the ENTRY's coefficient against the value's own
     * multiplicity at that vertex — {@code {n}} exactly n, {@code {n,m}} an inclusive range, {@code {+}} at least
     * one, no coefficient and {@code {1}} exactly one — while the vertex's TYPE still types each of those values
     * ({@code person{2}} types two people, not one person twice).
     * <p>
     * A nominal vertex type is matched by name, so the {@code person{2}} rows below (one person rejected, two
     * accepted) pin that the value test still judges its multiplicity; the {@code pilgrim} rows pin the same for a
     * predicated vertex type.
     * <p>
     * A ZERO-ADMITTING ENTRY IS NULLABLE, and that is the only repeat reading this path takes: {@code {?}} (zero or
     * one) and {@code {*}} (zero or more) may be OMITTED, so a middle vertex spelled either way is a vertex a
     * shorter chain legitimately skips. Whether a repeated vertex means a longer CHAIN is not settled, so no row
     * here counts hops — {@code {*}} accepts a two-value vertex as a MULTIPLICITY of two. The poly splitter keeps
     * {@code {2,3}} (comma and all) as one entry, which is why the type is readable at all.
     */
    @ParameterizedTest
    @TestData(value = {
            "person -> rec::T@person",
            "pilgrim -> rec::T[?[name=>str::T]]@pilgrim",
    })
    @CsvSource(value = {
            // ---- {n}: exactly n values at that vertex ----
            "rel[int,uri{2}]::(1=>{bob,marko})                                          % rel[int,uri{2}]::1=>{bob,marko}",
            "rel[int,uri{2}]::(1=>bob)                                                  % <ERROR>",
            "rel[int,uri{2}]::(1=>{a,b,c})                                              % <ERROR>",
            // ... and the same rule where a same-vid type match cannot see the coefficient: a rec vertex
            "rel[int,person{2}]::(1=>{person::[name=>'bob'],person::[name=>'marko']})   % rel[int,person{2}]::1=>{person::[name=>'bob'],person::[name=>'marko']}",
            "rel[int,person{2}]::(1=>person::[name=>'bob'])                             % <ERROR>",
            // ---- no coefficient and {1}: exactly one ----
            "rel[int,uri{1}]::(1=>bob)                                                  % rel[int,uri]::1=>bob",
            "rel[int,uri{1}]::(1=>{bob,marko})                                          % <ERROR>",
            "rel[int,uri]::(1=>{bob,marko})                                             % <ERROR>",
            // ---- {n,m}: an inclusive range ----
            "rel[int,person{2,3}]::(1=>{person::[name=>'a'],person::[name=>'b']})       % rel[int,person{2,3}]::1=>{person::[name=>'a'],person::[name=>'b']}",
            "rel[int,person{2,3}]::(1=>{person::[name=>'a'],person::[name=>'b'],person::[name=>'c']}) % rel[int,person{2,3}]::1=>{person::[name=>'a'],person::[name=>'b'],person::[name=>'c']}",
            "rel[int,person{2,3}]::(1=>{person::[name=>'a'],person::[name=>'b'],person::[name=>'c'],person::[name=>'d']}) % <ERROR>",
            // ---- {+}: at least one — one suffices, and the vertex is still REQUIRED (the absent row follows) ----
            "rel[int,person{+}]::(1=>person::[name=>'a'])                               % rel[int,person{+}]::1=>person::[name=>'a']",
            "rel[int,person{+}]::(1=>{person::[name=>'a'],person::[name=>'b']})         % rel[int,person{+}]::1=>{person::[name=>'a'],person::[name=>'b']}",
            "rel[int,person{+},str]::(1=>'tally')                                       % <ERROR>",
            "rel[int,person{+},str]::(1=>(person::[name=>'a']=>'tally'))                % rel[int,person{+},str]::1=>(person::[name=>'a']=>'tally')",
            // ---- {?}: zero or one — absent, or ONE value that is still its type ----
            "rel[int,person{?},str]::(1=>'tally')                                       % rel[int,person{?},str]::1=>'tally'",
            "rel[int,person{?}]::(1=>person::[name=>'a'])                               % rel[int,person{?}]::1=>person::[name=>'a']",
            "rel[int,person{?}]::(1=>{person::[name=>'a'],person::[name=>'b']})         % <ERROR>",
            // ---- {*}: zero or more — absent is legal, and a multiplicity is that multiplicity, not a repeat ----
            "rel[int,person{*},str]::(1=>'tally')                                       % rel[int,person{*},str]::1=>'tally'",
            "rel[int,person{*}]::(1=>{person::[name=>'a'],person::[name=>'b']})         % rel[int,person{*}]::1=>{person::[name=>'a'],person::[name=>'b']}",
            // ... at a MIDDLE vertex too: the multiplicity is the value there, and the path goes on
            "rel[int,person{*},str]::(1=>({person::[name=>'a'],person::[name=>'b']}=>'tally'))   % rel[int,person{*},str]::(1=>({person::[name=>'a'],person::[name=>'b']}=>'tally'))",
            // ---- a PREDICATED vertex type: the multiplicity is judged as for a bare one ----
            "rel[int,pilgrim{2}]::(1=>pilgrim::[name=>'bob'])                           % <ERROR>",
            "rel[int,pilgrim]::(1=>pilgrim::[name=>'bob'])                              % rel[int,pilgrim]::(1=>pilgrim::[name=>'bob'])",
            // ---- the LEADING vertex obeys the same rule: its coefficient is the value there, not a hop count ----
            "rel[int{2},uri]::({1,2}=>bob)                                              % rel[int{2},uri]::{1,2}=>bob",
            "rel[int{2},uri]::(1=>bob)                                                  % <ERROR>",
    }, delimiter = '%', quoteCharacter = '~')
    public void testVertexCoefficientIsTheValueMultiplicity(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    /**
     * THE REPORT SAYS WHAT FAILED. A bare `<ERROR>` row proves only that something failed, so this pins the
     * diagnostics themselves: a path that does not match its poly is the ordinary poly verdict ("obj does not match
     * … predicate apply failed on poly"); a NEGATIVE vertex coefficient is not a multiplicity, so it is named and
     * rejected where the type is constructed; and the illegal one-vertex poly is rejected at construction too, so
     * it fails where no value is involved, on a named type as on a base one.
     */
    @ParameterizedTest
    @CsvSource(value = {
            // code                                 % the report contains
            "rel[A,B,C]::(a=>b)                     % obj does not match",
            "rel[A,B,C]::(a=>b)                     % predicate apply failed on poly",
            "rel[int,str{-2}]::(1=>'tally')         % {-2}",
            "rel[int,str{-2}]::(1=>'tally')         % not yet matched",
            "rel[int,str{-2}]::T                    % not yet matched",
            "rel[A]::T                              % rel[A]",
            "rel[A]::T                              % at least two vertices",
            "rel[A]::T@lane                         % at least two vertices",
    }, delimiter = '%')
    public void testPathErrorsNameTheirCause(final String code, final String reported) {
        final Obj rejected = ObjmtronSerializer.parse(code);
        assertTrue(rejected.isFail(), "the path must be rejected: " + rejected);
        assertTrue(rejected.asFail().message().contains(reported.trim()),
                "the report names its cause: " + rejected.asFail().message());
    }

    /**
     * A PATH IS NOT ITS VERTICES: {@code rel[V0,V1]} is not a {@code V0} and not a {@code V1}. Only a fibered rec is
     * accepted where a type its bracket names is expected ({@code Type#recFibers}); a rel's bracket names
     * the vertices of a path, so a relation is only acceptable where {@code rel::T} is expected. The vertex types
     * are BASE types here on purpose: a base type's verdict does not route through the nominal walk's `#` fallback,
     * so the assertion is about the rel reading rather than about that pre-existing looseness.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "(rel[int,int]::(1=>2))   % int::T    % false",
            "(rel[int,str]::(1=>'tally'))  % str::T   % false",
            "rel[int,str]::T           % int::T    % false",
            "rel[int,str]::T           % str::T    % false",
            // ... and it IS acceptable where a rel is expected: the poly narrows the rel, it does not stop being one
            "rel[int,str]::T           % rel::T    % true",
            "(rel[int,str]::(1=>'tally'))  % rel::T   % true",
            // a multiplicity coefficient is a well-formed poly: it is judged when a value is held to it
            "rel[int{2,3},str]::T      % rel::T    % true",
    }, delimiter = '%')
    public void testPathIsNotItsVertices(final String value, final String type, final boolean matches) {
        AbstractMetatronTest.checkMatches(LOG, value, type, matches);
    }

    /**
     * A NAMED PATH TYPE READS ITS TID'S BRACKET AS A PATH. {@code route -> rel[int,str]::T@route} is the path
     * {@code int => str}: a route is a chain whose first vertex is an int and whose closing vertex is a str, and a
     * chain of other vertices is rejected, exactly as for the base {@code rel[int,str]}.
     */
    @ParameterizedTest
    @TestData(value = {
            "route -> rel[int,str]::T@route",
            "detour -> rel[int,str{?},bool]::T@detour",
    }, oneTime = true)
    @CsvSource(value = {
            "route::(1=>'tally')                    % route::(1=>'tally')",
            "route::(1=>'tally').type()             % route::T",
            "route::(1=>2)                          % <ERROR>",
            "route::('tally'=>'tally')              % <ERROR>",
            "route::(1=>('tally'=>true))            % <ERROR>",
            // an optional vertex of a named path is skipped as on a base one
            "detour::(1=>true)                      % detour::(1=>true)",
            "detour::(1=>('tally'=>true))           % detour::(1=>('tally'=>true))",
            "detour::(1=>('tally'=>2))              % <ERROR>",
    }, delimiter = '%')
    public void testNamedPathType(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    /**
     * A PATH CONFERS NO LIFTING EITHER. A rec wrapper's declared components join the stream an instruction is applied
     * to ({@code person[ninja]} answers a {@code dom=ninja} call through its {@code role}), but a path is not a
     * wrapper: an instruction whose domain names a vertex type does NOT reach that vertex through the chain. The
     * same instruction DOES fire on the vertex when the vertex is the lhs, so the silence is the path's, not the
     * instruction's.
     */
    @ParameterizedTest
    @TestData(value = {
            "path_site -> uri::T@path_site",
            "path_hop -> uri::T@path_hop",
            "path_mark -> |inst?<=path_site(){ 7 }",
            "path_chain -> rel[path_site,path_hop]::(path_site::a=>path_hop::b)",
    })
    @CsvSource(value = {
            // no fan-out: the vertex is not a candidate of the chain, so nothing answers
            "*path_chain.path_mark()            % noobj",
            // ... while the vertex itself is an ordinary candidate for the very same instruction
            "path_site::a.path_mark()           % 7",
    }, delimiter = '%')
    public void testPathDoesNotLift(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    /**
     * LONG PATHS AND RUNS OF OPTIONAL VERTICES. Each vertex types the value at its depth, so a path may neither stop
     * early nor run long, and a vertex out of order is a position mismatch however deep it sits. Any run of
     * zero-admitting vertices may be skipped in part or in whole, while a present one is still typed. A vertex
     * coefficient is the multiplicity of the value there, at the opening and closing vertex alike.
     */
    @ParameterizedTest
    @CsvSource(value = {
            // ---- four and five vertices: every depth is typed ----
            "rel[int,str,bool,real]::(1=>('a'=>(true=>2.5)))                % rel[int,str,bool,real]::(1=>('a'=>(true=>2.5)))",
            "rel[int,int,int,int,int]::(1=>(2=>(3=>(4=>5))))              % rel[int,int,int,int,int]::(1=>(2=>(3=>(4=>5))))",
            // ... a path that stops early or runs long is an arity mismatch
            "rel[int,str,bool,real]::(1=>('a'=>true))                       % <ERROR>",
            "rel[int,int,int,int,int]::(1=>(2=>(3=>4)))                     % <ERROR>",
            "rel[int,str,bool,real]::(1=>('a'=>(true=>(2.5=>3))))           % <ERROR>",
            // ... and two vertices out of order fail at their depth
            "rel[int,str,bool,real]::(1=>('a'=>(2.5=>true)))                % <ERROR>",
            // ---- a run of optional vertices: skip neither, either or both ----
            "rel[int,str{?},bool{?},real]::(1=>2.5)                         % rel[int,str{?},bool{?},real]::(1=>2.5)",
            "rel[int,str{?},bool{?},real]::(1=>(true=>2.5))                 % rel[int,str{?},bool{?},real]::(1=>(true=>2.5))",
            "rel[int,str{?},bool{?},real]::(1=>('a'=>2.5))                  % rel[int,str{?},bool{?},real]::(1=>('a'=>2.5))",
            "rel[int,str{?},bool{?},real]::(1=>('a'=>(true=>2.5)))          % rel[int,str{?},bool{?},real]::(1=>('a'=>(true=>2.5)))",
            // ... but the run keeps its order
            "rel[int,str{?},bool{?},real]::(1=>(2.5=>true))                 % <ERROR>",
            // ... a run of three skipped down to the two required ends
            "rel[int,str{?},bool{?},real{?},uri]::(1=>a)                    % rel[int,str{?},bool{?},real{?},uri]::(1=>a)",
            "rel[int,str{?},bool{?},real{?},uri]::(1=>(true=>a))            % rel[int,str{?},bool{?},real{?},uri]::(1=>(true=>a))",
            // ---- a trailing run: the path may close at any optional vertex, but what closes it is typed ----
            "rel[int,str{?},bool{?}]::(1=>true)                             % rel[int,str{?},bool{?}]::(1=>true)",
            "rel[int,str{?},bool{?}]::(1=>'a')                              % rel[int,str{?},bool{?}]::(1=>'a')",
            "rel[int,str{?},bool{?}]::(1=>2)                                % <ERROR>",
            // ---- a vertex coefficient is the multiplicity there ----
            "rel[int,str{+}]::(1=>{'a','b'})                                % rel[int,str{+}]::(1=>{'a','b'})",
            "rel[int{2,3},str]::({1,2,3}=>'a')                              % rel[int{2,3},str]::({1,2,3}=>'a')",
            "rel[int{2,3},str]::({1,2,3,4}=>'a')                            % <ERROR>",
            "rel[int{2,3},str]::(1=>'a')                                    % <ERROR>",
    }, delimiter = '%', quoteCharacter = '~')
    public void testLongPathsAndOptionalRuns(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    /**
     * A VERTEX MAY BE A NAMED TYPE, AND IS READ AS ONE. A {@code leg} ends at a {@code ticket}, so the list at its
     * closing vertex is held to the ticket's indices; a {@code trek} runs through a {@code scout} and a {@code ticket}
     * to a bool, so each of its vertices is held to its own type at its own depth. A plain rec passes for a scout (a
     * nominal rec with nothing to check). A path's vertices are not paths: a rel where the {@code stride} vertex of a
     * {@code journey} is expected continues the path instead, so it never reaches the stride.
     */
    @ParameterizedTest
    @TestData(value = {
            "ticket -> lst[int,str]::T@ticket",
            "scout -> rec::T@scout",
            "leg -> rel[int,ticket]::T@leg",
            "trek -> rel[scout,ticket,bool]::T@trek",
            "stride -> rel[int,str]::T@stride",
            "journey -> rel[bool,stride]::T@journey",
    })
    @CsvSource(value = {
            // ---- a named tensor at the closing vertex ----
            "leg::(1=>[2,'b'])                                      % leg::(1=>[2,'b'])",
            "leg::(1=>[2,3])                                        % <ERROR>",
            "leg::(1=>[2])                                          % <ERROR>",
            "leg::('x'=>[2,'b'])                                    % <ERROR>",
            // ---- a named rec, a named tensor and a mono, each at its depth ----
            "trek::(scout::[name=>'kai']=>([1,'a']=>true))          % trek::(scout::[name=>'kai']=>([1,'a']=>true))",
            "trek::([name=>'kai']=>([1,'a']=>true))                 % trek::([name=>'kai']=>([1,'a']=>true))",
            "trek::(scout::[name=>'kai']=>([1,'a']=>3))             % <ERROR>",
            "trek::(scout::[name=>'kai']=>([1,2]=>true))            % <ERROR>",
            "trek::(scout::[name=>'kai']=>true)                     % <ERROR>",
            "trek::(1=>([1,'a']=>true))                             % <ERROR>",
            // ---- a rel where a path-typed vertex is expected is more path, not the vertex ----
            "journey::(true=>(1=>'a'))                              % <ERROR>",
    }, delimiter = '%', quoteCharacter = '~')
    public void testNamedVertices(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    /**
     * A NAMED PATH TYPE JUDGES A PLAIN REL BY ITS PATH. An unannotated chain is accepted where a named path is expected
     * exactly when its vertices are that path's, the named vertex types included; a typed path is still a rel, and it
     * is not any of its vertices.
     */
    @ParameterizedTest
    @TestData(value = {
            "ticket -> lst[int,str]::T@ticket",
            "scout -> rec::T@scout",
            "leg -> rel[int,ticket]::T@leg",
            "trek -> rel[scout,ticket,bool]::T@trek",
            "stride -> rel[int,str]::T@stride",
            "journey -> rel[bool,stride]::T@journey",
    })
    @CsvSource(value = {
            // value                                          % type          % matches
            "(1=>[2,'b'])                                     % leg::T        % true",
            "(1=>['b',2])                                     % leg::T        % false",
            "(scout::[name=>'kai']=>([1,'a']=>true))          % trek::T       % true",
            "(scout::[name=>'kai']=>([1,'a']=>true))          % leg::T        % false",
            "(1=>'a')                                         % stride::T     % true",
            "(1=>'a')                                         % journey::T    % false",
            "(true=>(1=>'a'))                                 % journey::T    % false",
            // ---- a typed path is a rel, and not any of its vertices ----
            "leg::(1=>[2,'b'])                                % rel::T        % true",
            "leg::(1=>[2,'b'])                                % ticket::T     % false",
            "leg::(1=>[2,'b'])                                % int::T        % false",
    }, delimiter = '%', quoteCharacter = '~')
    public void testNamedPathMatching(final String value, final String type, final boolean matches) {
        AbstractMetatronTest.checkMatches(LOG, value, type, matches);
    }

    /**
     * INSTRUCTIONS ON A PATH TYPE, AND ON A VERTEX TYPE. An instruction whose domain is a named path runs on a value
     * typed by that path and on a plain chain whose vertices are the path's, and on nothing that is not a rel. An
     * instruction whose domain is a vertex type runs on that vertex's value, and a path holding such a value does not
     * answer for it.
     */
    @ParameterizedTest
    @TestData(value = {
            "ticket -> lst[int,str]::T@ticket",
            "leg -> rel[int,ticket]::T@leg",
            "pace -> rel[int,str]::T@pace",
            "pace_kind -> |inst?<=pace(){ 'a pace' }",
            "ticket_tag -> |inst?<=ticket(){ >>1 }",
            "a_leg -> leg::(1=>[2,'b'])",
    })
    @CsvSource(value = {
            "pace::(1=>'a').pace_kind()              % 'a pace'",
            "(1=>'a').pace_kind()                    % 'a pace'",
            "5.pace_kind()                           % <ERROR>",
            // ---- the vertex's own instruction runs on the vertex value, not through the path ----
            "[2,'b'].ticket_tag()                    % 'b'",
            "*a_leg.ticket_tag()                     % noobj",
    }, delimiter = '%', quoteCharacter = '~')
    public void testPathTypeInstructions(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }
}
