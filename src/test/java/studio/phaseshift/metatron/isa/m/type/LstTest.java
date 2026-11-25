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

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
/*
 "/t -> [a,[b,[c,d],e],f]                               % */

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.TestData;
import studio.phaseshift.metatron.Training;
import studio.phaseshift.metatron.algebra.AbstractAlgebraTest;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;
import studio.phaseshift.metatron.util.MTronException;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.LST_TYPE;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_from_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;


public class LstTest extends AbstractAlgebraTest {

    @Override
    protected Type carrier() {
        return LST_TYPE;
    }

    @Override
    protected Obj sample() {
        return lst(jnt(1), jnt(2), jnt(3));
    }

    /// THE WORD RING (Marko, 2026-11-24 and 11-25; Claims 19 and 20 of ring-theoretic-distributed-structures.md): a lst
    /// is a word of objects, a timeline. It multiplies by concatenation, with the empty lst as its unity and noobj as its
    /// zero, and it adds as a path adds: the prefix and suffix its words share around one brace of what lies between,
    /// sorted, so lsts that share no moment are one lst whose slot branches them. Neither operation applies a letter's
    /// own: letters are read only by equality. A sum keeps its mass on its letters, as a lst is multilinear in them
    /// (Claim 24), and the lhs's own point floats over it (Theorem 3).
    @ParameterizedTest
    @CsvSource(value = {
            // the product: concatenation, its unity [,], and the zero absorbing
            "[1,2,3] * [4,5,6]                                                            % [1,2,3,4,5,6]",
            "[1,2,3].mult([4,5,6])                                                        % [1,2,3,4,5,6]",
            "[,] * [1,2]                                                                  % [1,2]",
            "[1,2] * [,]                                                                  % [1,2]",
            "[1,2].one()                                                                  % [,]",
            "['a','b'].one()                                                              % [1,2].one()",
            "[1,2] * noobj                                                                % noobj",
            "[1,2].zero()                                                                 % noobj",
            "[1,2].mult([3]).mult([4,5])                                                  % [1,2].mult([3].mult([4,5]))",
            "[1,{2,3}] * [{4,5},6]                                                        % [1,{2,3},{4,5},6]",
            "[a,b] * {2}[c]                                                               % [{2}a,b,c]",
            // never a letter's own product
            "[2,3] * [4,5]                                                                % [2,3,4,5]",
            "[true] * [false]                                                             % [true,false]",
            "[(a=>2)].mult([(2=>x)])                                                      % [(a=>2),(2=>x)]",
            "[[a=>b],c] * [[b=>e]]                                                        % [[a=>b],c,[b=>e]]",
            // the sum: the shared prefix and suffix around a brace of what lies between
            "[a,b,c] + [a,d,c]                                                            % [a,{b,d},c]",
            "[a,b] + [c,b]                                                                % [{a,c},b]",
            "[a,b] + [a,c]                                                                % [a,{b,c}]",
            "[1,2,3] + [4,5,6]                                                            % [{[1,2,3],[4,5,6]}]",
            "[1,2,3] + [3,2,1]                                                            % [{[1,2,3],[3,2,1]}]",
            "[a] + [a,b]                                                                  % [a,{[,],b}]",
            "[1,2,3] + [2,3]                                                              % [{[,],1},2,3]",
            "[a,{b,d},c] + [a,e,c]                                                        % [a,{b,d,e},c]",
            "[{a,b},{c,d}] + [x]                                                          % [{[a,c],[a,d],[b,c],[b,d],x}]",
            "[x,{[a,b],c},y] + [x,d,y]                                                    % [x,{[a,b],c,d},y]",
            "[1,2] + noobj                                                                % [1,2]",
            // commutative: the brace is sorted, a middle before the middles it begins
            "[a,d,c] + [a,b,c]                                                            % [a,{b,d},c]",
            "[4,5,6] + [1,2,3]                                                            % [{[1,2,3],[4,5,6]}]",
            "[a,b,a,b] + [a,b]                                                            % [a,b,{[,],[a,b]}]",
            // never a letter's own sum
            "[1] + [2]                                                                    % [{1,2}]",
            "['a'] + ['b']                                                                % [{'a','b'}]",
            "[[a=>b],c,d] + [[a=>b],d,e]                                                  % [[a=>b],{[c,d],[d,e]}]",
            "[plus(1),mult(2)] + [plus(1),plus(3)]                                        % [plus(1),{mult(2),plus(3)}]",
            // a lst letter stays a letter: alone in a middle, it is a sub-word of one letter
            "[[1,2]] + [3]                                                                % [{3,[[1,2]]}]",
            "[[1,2],3] + [[1,2],4]                                                        % [[1,2],{3,4}]",
            "[x,{[a,b]},y] + [x,c,y]                                                      % [x,{[[a,b]],c},y]",
            // a brace whose mass bulks to one holds one sub-word at point one, so it is that one-member brace (open)
            "[x,{{2}[a,b],{-1}[a,b]}]                                                     % [x,{[a,b]}]",
            // a sum's mass on its letters (Claim 24): the argument counts by its point, and the lhs's floats
            "[a,b] + [a,b]                                                                % [{2}a,b]",
            "[a,b] + [a,b] + [a,b]                                                        % [{3}a,b]",
            "[a,b,c] + {2}[a,d,c]                                                         % [a,{b,{2}d},c]",
            "[{2}a,b] + [a,{2}b]                                                          % [{4}a,b]",
            "[1,2] + {2}[1,2]                                                             % [{3}1,2]",
            "[a] + [a] + [a,b]                                                            % [a,{{2}[,],b}]",
            "{2}[1,2] + [1,2]                                                             % {2}[{2}1,2]",
            "lst{2}::[1,2].plus([3])                                                      % lst{2}::[{[1,2],3}]",
            "[1].plus(lst{2}::[2,3])                                                      % [{1,{2}[2,3]}]",
            "[1].plus([{3}2])                                                             % [{1,{3}2}]",
            // the additive inverse: -1 on the first letter, so a lst and its negation sum to the zero
            "[1,2,3].neg()                                                                % [{-1}1,2,3]",
            "[1,2,3].neg().neg()                                                          % [1,2,3]",
            "[{a,b},c].neg().neg()                                                        % [{a,b},c]",
            "[{a,b},c].plus([{a,b},c].neg())                                              % noobj",
            "[1,2,3].plus([1,2,3].neg())                                                  % noobj",
            "[{-1}1,2] + [1,2]                                                            % noobj",
            "[a,b] + [a,{-1}b]                                                            % noobj",
            "[a,b,c] + [a,d,c] + [a,{-1}d,c]                                              % [a,b,c]",
            "[{-1}a] + [{-1}b]                                                            % [{a,b}].neg()",
            "[{-1}a] + [{-1}b] + [a]                                                      % [{-1}b]",
            // a hole is the zero letter, so no word runs through it
            "[1,noobj] + [2]                                                              % [2]",
            // the unity carries its mass as the sub-word it is, as <> + <> is <{2}>, so its multiples sum and multiply
            // associatively
            "[,] + [,]                                                                    % [{2}[,]]",
            "[,] + [,] + [a]                                                              % [{{2}[,],a}]",
            "[,].plus([,].plus([a]))                                                      % [{{2}[,],a}]",
            "[,] * {2}[,]                                                                 % [{2}[,]]",
            "[,].mult({2}[,]).mult([a])                                                   % [{2}[,],a]",
            "[,].mult({2}[,]).mult([a]).plus(noobj)                                       % [{2}a]",
            "[,].mult({2}[,].mult([a]))                                                   % [{2}a]",
            "[,].neg()                                                                    % [{-1}[,]]",
            "[,].neg().neg()                                                              % [,]",
            "[,].plus([,].neg())                                                          % noobj",
            // a brace of equal sub-words is bulked into one lst with a point, a sub-word with that mass (Definition 11)
            "[x,{[a,b],[a,b]}]                                                            % [x,{2}[a,b]]",
            "[x,{[a,b],[a,b]}] + [x,a,b]                                                  % [{3}x,a,b]",
            "[x,{2}[a,b]] + [x,{-2}[a,b]]                                                 % noobj",
            "[x,{[a,b],[a,b]},y] + [x,c,y]                                                % [x,{{2}[a,b],c},y]",
            // the mass never lands on a lst letter: the letter is wrapped as the sub-word that holds it
            "[[1,2],3] + [[1,2],3]                                                        % [{2}[[1,2]],3]",
            "[[1,2],3] + [[1,2],3] + [{-1}[[1,2]],3]                                      % [[1,2],3]",
            "[[1,2],3].plus([[1,2],3].plus([{-1}[[1,2]],3]))                              % [[1,2],3]",
            "[[1,2],3].neg()                                                              % [{-1}[[1,2]],3]",
            "[[1,2],3].neg().neg()                                                        % [[1,2],3]",
            "[{-1}[a,b],c].neg()                                                          % [a,b,c]",
    }, delimiter = '%')
    public void testTheWordRing(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    /// A LST'S SPAN IS ITS POWER (Marko, 2026-11-25; Definition 11): a span after the closing bracket is the lst
    /// concatenated n times, the unity at zero, and a negative span the converse's power; a range is the sum of its
    /// powers. pow(n) is the same span at run time. A span on a letter inside the brackets still repeats that letter
    /// in place (Lst.Helper.laid). The copies are bounded by the typer's unfold for lsts, a range's summed over its
    /// powers, and an open side over a lst that holds a word never ends, so it is refused at the bound.
    @ParameterizedTest
    @CsvSource(value = {
            "[1,2]{2}                                                                     % [1,2,1,2]",
            "[1,2]{1}                                                                     % [1,2]",
            "[1,2]{0}                                                                     % [,]",
            "[1,2,3]{-1}                                                                  % [3,2,1]",
            "[1,2]{-2}                                                                    % [2,1,2,1]",
            "[,]{3}                                                                       % [,]",
            "[,]{-1}                                                                      % [,]",
            "[a,{b,c}]{2}                                                                 % [a,{b,c},a,{b,c}]",
            "[{2}a,b]{2}                                                                  % [{2}a,b,{2}a,b]",
            // a range is the sum of its powers
            "[a]{1,2}                                                                     % [a,{[,],a}]",
            "[a]{0,2}                                                                     % [{[,],a,[a,a]}]",
            "[1,2]{-1,1}                                                                  % [{[,],[1,2],[2,1]}]",
            "[1,2]{1,3}                                                                   % [1,2,{[,],[1,2],[1,2,1,2]}]",
            "[noobj]{*}                                                                   % [,]",
            "[noobj]{1,2}                                                                 % noobj",
            "[,]{1,2}                                                                     % [{2}[,]]",
            // a bulked brace reverses as its sub-word
            "[x,{[a,b],[a,b]}]{-1}                                                        % [{2}[b,a],x]",
            // the point before the brackets counts copies of the whole power, the vid names it, and the zero stays
            "{2}[1,2]{2}                                                                  % {2}[1,2,1,2]",
            "[1,2]{2}@x                                                                   % [1,2,1,2]@x",
            "lst::[1,2]{2}                                                                % [1,2,1,2]",
            // a span on a letter inside the brackets repeats it in place, and the brackets' span powers the lst
            "[3{2}]                                                                       % [3,3]",
            "[3{2}]{2}                                                                    % [3,3,3,3]",
            "[{2}3]                                                                       % [{2}3]",
            "[[1,2]{2}]                                                                   % [[1,2,1,2]]",
            "[[1,2]]{2}                                                                   % [[1,2],[1,2]]",
            // the bound
            "[1,2]{100}>-.count()                                                         % 200",
            "[1,2]{101}                                                                   % <ERROR>",
            "[1,2]{-101}                                                                  % <ERROR>",
            "[1]{1,13}.count()                                                            % 1",
            "[1]{1,14}                                                                    % <ERROR>",
            "[1]{-14,-1}                                                                  % <ERROR>",
            "[1]{0,10}.count()                                                            % 1",
            "[1]{-10,10}                                                                  % <ERROR>",
            "[1,2]{*}                                                                     % <ERROR>",
            "[1,2]{+}                                                                     % <ERROR>",
            "[1]{-9223372036854775808}                                                    % <ERROR>",
            // a sum's braces multiply its words, measured before any is built and bounded in copies of the letters the
            // lst writes, a nested brace's among them, whatever order its letters and braces come in
            "[{a,b},{c,d},{e,f},{g,h},{i,j},{k,l},{m,n}].plus([x]).count()                % 1",
            "[{a,b},{c,d},{e,f},{g,h},{i,j},{k,l},{m,n},{o,p},{q,r},{s,t},{u,v},{w,x}].plus([y]) % <ERROR>",
            "[{a,b}]{12}.plus([y])                                                        % <ERROR>",
            "[{[{a,b},{c,d},{e,f},{g,h},{i,j},{k,l},{m,n},{o,p},{q,r},{s,t},{u,v}],z},{w1,x1},{w2,x2},{w3,x3},{w4,x4},{w5,x5},{w6,x6}].plus([y]) % <ERROR>",
            "[{[{a,b},{c,d}],z},{e,f}].plus([y]).count()                                  % 1",
            "[0,1,2,3,4,5,6,7,8,9]{10}.mult([{a,b},{c,d},{e,f},{g,h},{i,j},{k,l},{m,n},{o,p},{q,r},{s,t},{u,v},{w,x}]).plus([y]) % <ERROR>",
            "[{a,b},{c,d},{e,f},{g,h},{i,j},{k,l},{m,n},{o,p},{q,r},{s,t},{u,v},{w,x}].mult([0,1,2,3,4,5,6,7,8,9]{10}).plus([y]) % <ERROR>",
            // a range sums its powers' words, each bounded before it is built: its seventh power's braces are refused
            "[0{10},1{10},2{10},3{10},4{10},5{10},6{10},7{10},8{10},9{10},{a,b}]{1,11}    % <ERROR>",
            // a lst without braces is one word, built once: its sum is linear in its length
            "[0,1,2,3,4,5,6,7,8,9]{100}.pow(100).plus([x]).count()                        % 1",
            "[0,1,2,3,4,5,6,7,8,9]{100}.pow(100).plus([0,1,2,3,4,5,6,7,8,9]{100}.pow(100).neg()) % noobj",
            // a range is bounded by its farthest power, then by the copies all of them hold, before any is built
            "[1]{1,10000000000}                                                           % <ERROR>",
            // the span at run time
            "[1,2].pow(2)                                                                 % [1,2,1,2]",
            "[1,2,3].pow(-1)                                                              % [3,2,1]",
            "[1,2].pow(0)                                                                 % [,]",
            "[1,2].pow(101)                                                               % <ERROR>",
    }, delimiter = '%')
    public void testSpans(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    /// THE FOLD IS THE RING HOMOMORPHISM (Claim 22): it runs a lst as the program its letters denote, a value as the
    /// constant that answers it and an instruction as itself, so fold(u v) = fold(u) fold(v), fold(u + v) =
    /// fold(u) + fold(v), and fold([,]) is the identity. A brace is a branch, and a lst in it a sub-word, folded whole.
    @ParameterizedTest
    @CsvSource(value = {
            "[1,plus(2),plus(3)]>|                                                        % 6",
            "{[1,plus(2),plus(3)],[1,mult(2),plus(5)]}>|                                  % {6,7}",
            "[1,{plus(2),mult(2)},{plus(3),plus(5)}]>|                                    % {6,8,5,7}",
            "[1,{[plus(2),plus(3)],[mult(2),plus(5)]}]>|                                  % {6,7}",
            "[1,plus(2),plus(3)].plus([1,mult(2),plus(5)])                                % [1,{[mult(2),plus(5)],[plus(2),plus(3)]}]",
            "[1,{[mult(2),plus(5)],[plus(2),plus(3)]}]>|                                  % {7,6}",
            "[1].mult([plus(2),plus(3)])>|                                                % 6",
            "[1,{[,],plus(1)}]>|                                                          % {1,2}",
            "[1,{{2}[plus(1)],plus(10)}]>|                                                % {{2}2,11}",
            "[{1,2},plus(1)]>|                                                            % {2,3}",
            "[1,[plus(1)]]>|                                                              % [plus(1)]",
            "[1,noobj,plus(1)]>|                                                          % noobj",
            "[,]>|                                                                        % [,]",
            "[1,{[plus(1)],[plus(1)]}]>|                                                  % {2}2",
            // an instruction runs over what reached it as its code runs over a stream: a map on each obj, a reducer on all
            "[1,{plus(1),plus(2)},sum()]>|                                                % 5",
            "[1,{plus(1),plus(2)},count()]>|                                              % 2",
            "[1,{plus(1),plus(2)},{[sum()],[count()]}]>|                                  % {5,2}",
            "[1,{plus(1),plus(1)},count()]>|                                              % 2",
            // records are steps (Claim 28): a record that meets a record is their join, whatever they hold, so a lst of
            // records folds to their product, a record's input joins too, and the record unity [_=>_] passes the walk on
            "[[a=>b],[b=>e]]>|                                                            % [a=>b=>e]",
            "[[a=>b],[b=>e],[e=>f]]>|                                                     % [a=>b=>e=>f]",
            "[[a=>b],[c=>d]]>|                                                            % [=>]",
            "[[x=>a],{[a=>b],[a=>c]},[b=>e]]>|                                            % [x=>a=>b=>e]",
            "[[x=>a],{[a=>b],[a=>c]},[c=>e]]>|                                            % [x=>a=>c=>e]",
            "[[x=>a],{[b=>c],[d=>e]}]>|                                                   % [=>]",
            "[[x=>a],{[b=>c],[d=>e]}]>|                                                   % [x=>a].mult([b=>c,d=>e])",
            "[[a=>b],{[b=>c],[x=>y]},5]>|                                                 % 5",
            "[[a=>5],[nat::T=>x]]>|                                                       % [a=>5] * [nat::T=>x]",
            "[[a=>5],[?>=0=>x]]>|                                                         % [a=>5] * [?>=0=>x]",
            "[[a=>5],[?>=0=>x]]>|                                                         % [a=>5=>x]",
            "[[x=>a],[a=>plus(1)]]>|                                                      % [x=>a] * [a=>plus(1)]",
            "[[x=>a],[a=>plus(1)]]>|                                                      % [x=>a=>plus(1)]",
            "[x=>a]>|[[a=>b],[b=>e]]                                                      % [x=>a=>b=>e]",
            "[[a=>b],[_=>_]]>|                                                            % [a=>b]",
            "[[a=>b],5]>|                                                                 % 5",
            "[2,3,4]>|                                                                    % 4",
            // a rec that holds code joins as the product does, running none of it: the projection -< runs its code
            "[a=>1]>|[[x=>>>a]]                                                           % [a=>1] * [x=>>>a]",
            "[a=>1]>|[[x=>[>>a]]]                                                         % [a=>1] * [x=>[>>a]]",
            "[x=>1]>|[[1=>>>a]]                                                           % [x=>1] * [1=>>>a]",
            "[a=>1]-<[x=>>>a]                                                             % [x=>1]",
    }, delimiter = '%')
    public void testTheFold(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    /// A LIVE REC IN A FOLD holds no walks, so it applies as itself, running whatever its apply runs: a space, the
    /// network among them, passes a value on, and a parser parses a str and passes anything else on.
    @Test
    public void testALiveRecInAFoldRunsItsOwnApply() {
        final Obj network = Machine.parse("*~/network").apply(noobj());
        final Obj parser = Machine.parse("*~/compiler/parser").apply(noobj());
        checkEquality(LOG, jnt(1), Lst.Helper.fold(lst(jnt(1), network), noobj()), true);
        checkEquality(LOG, ObjmtronSerializer.parse("[a,b]"), Lst.Helper.fold(lst(str("[a,b]"), parser), noobj()), true);
        checkEquality(LOG, jnt(1), Lst.Helper.fold(lst(jnt(1), parser), noobj()), true);
    }

    /// URI IS THE ORACLE (Marko, 2026-11-24): uri::T is already the word ring over segments, and a lst of atoms spells
    /// a path (its as(uri), a brace the sum of its members), so the spelling commutes with the ring:
    /// (l + m).as(uri) is l.as(uri) + m.as(uri), and (l * m).as(uri) is l.as(uri) * m.as(uri). Each side is read in the
    /// uri sum's normal form, sum(), since a uri holds a mass in two places: its product puts the argument's point on
    /// the uri, {2}<a/b/c>, and its sum in the path, <a/b/c{2}>, which is where a lst's spelling puts it.
    @ParameterizedTest
    @CsvSource(value = {
            "[a,b,c]       % [a,d,c]",
            "[a,b,c]       % [x,y]",
            "[a]           % [a,b]",
            "[a,b]         % [c,b]",
            "[a,b,c]       % [a,b,c]",
            "[a,{b,d},c]   % [a,e,c]",
            "[{a,b},c]     % [a,d]",
            "[a,{[b,c],d}] % [a,e]",
            "[a,b]         % [,]",
            "[a,b]         % {2}[c]",
            "{2}[a,b]      % [c]",
            "[a,{2}b]      % {3}[a,b]",
            "[x,{2}[a,b]]  % [x,a,b]",
            "[,]           % [,]",
    }, delimiter = '%')
    public void testUriIsTheOracle(final String lhs, final String rhs) {
        AbstractMetatronTest.checkCodeParseApply(LOG, lhs + ".plus(" + rhs + ").as(uri::T).sum()", lhs + ".as(uri::T).plus(" + rhs + ".as(uri::T)).sum()");
        AbstractMetatronTest.checkCodeParseApply(LOG, lhs + ".mult(" + rhs + ").as(uri::T).sum()", lhs + ".as(uri::T).mult(" + rhs + ".as(uri::T)).sum()");
        AbstractMetatronTest.checkCodeParseApply(LOG, lhs + ".plus(" + rhs + ").as(uri)", lhs + ".plus(" + rhs + ").as(uri::T)");
    }

    /// THE SUM'S BOUND IS ITS BRACES', NOT ITS SIZE: a brace of many members is as many words, linear in the lst, so it
    /// sums however wide it is, while braces that multiply the words are refused before the words are built.
    @Test
    public void testTheSumIsBoundedByItsBracesNotItsSize() {
        final Lst wide = lst(objs(java.util.stream.IntStream.rangeClosed(1, 150).mapToObj(i -> (Obj) jnt(i)).toList()));
        final Lst sum = wide.plus(lst(jnt(151)));
        assertEquals(151L, sum.lstValue().getFirst().stream().count(), () -> "a brace of 150 and a word sum to 151 members: " + sum);
        final Lst narrow = lst(java.util.stream.IntStream.range(0, 12).mapToObj(i -> objs(jnt(2L * i), jnt(2L * i + 1L))).toList());
        final MTronException refused = assertThrows(MTronException.class, () -> narrow.plus(lst(jnt(-1))), "twelve braces of two are 4096 words of 12 letters, written in 24");
        final String words = "2048 copies unfold past the typer's bound for lst";
        assertEquals(words, refused.getMessage().substring(0, Math.min(words.length(), refused.getMessage().length())));
        // a range is bounded by its farthest power before any is built, never walked power by power
        final MTronException farthest = assertThrows(MTronException.class, () -> Lst.Helper.power(lst(jnt(1)), cInt.of(1L, 10_000_000_000L)));
        final String far = "10000000000 copies unfold past the typer's bound for lst";
        assertEquals(far, farthest.getMessage().substring(0, Math.min(far.length(), farthest.getMessage().length())));
        final Machine machine = Machine.current();
        final Lst unbounded = lst(lst(java.util.stream.IntStream.range(0, 1024).mapToObj(i -> objs(jnt(2L * i), jnt(2L * i + 1L))).toList()).d(cInt.of(2L)));
        final MTronException beyond = assertTimeoutPreemptively(Duration.ofSeconds(10), () -> Machine.Helper.evalAt(machine,
                () -> assertThrows(MTronException.class, () -> unbounded.plus(lst(jnt(-1))))), "1024 braces of two in a bulked brace");
        final String saturated = Long.MAX_VALUE / 2048L + 1L + " copies unfold past the typer's bound for lst";
        assertEquals(saturated, beyond.getMessage().substring(0, Math.min(saturated.length(), beyond.getMessage().length())));
        // a lst without braces is one word, extended in place: its sum is linear in its length, where a copy of the word
        // per slot took minutes at this length
        final Lst longest = lst(java.util.stream.IntStream.range(0, 200_000).mapToObj(i -> (Obj) jnt(i % 10)).toList());
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> Machine.Helper.evalAt(machine, () -> longest.plus(lst(jnt(-1)))),
                "a sum over 200,000 letters is linear in them");
    }

    /// THE WORDS ARE BOUNDED BEFORE THEY ARE BUILT, in whatever order a hole, a mass-only {2}[,] and an unbounded
    /// sub-word come, a sub-word of a brace that is no word included, as the sum, as(uri::T), a range's powers and the
    /// converse reach them, and nested spans, whose copies share one sub-word, are bounded by the letters they hold,
    /// each shared sub-word once, so five spans of 100 over [x,y] are 2·10^10 letters held in 200. Built, each would
    /// exhaust the heap, so each runs under a timeout, the measure, the expansion and the converse stopping when it
    /// interrupts them. <BOUND> is the refusal of the typer's bound for lsts.
    @ParameterizedTest
    @CsvSource(value = {
            "[{a,b}]{100}.pow(11).mult([{2}[,]]).plus([y])                                % <BOUND>",
            "[{a,b}]{100}.pow(11).mult([noobj]).plus([y])                                 % [y]",
            "[{a,b}]{100}.pow(11).mult([y,noobj]).plus([y])                               % [y]",
            "[{2}[{2}[{a,b}]{100}]{11}] + [y]                                             % <BOUND>",
            "[{2}[,],{2}[{2}[{a,b}]{100}]{11}] + [y]                                      % <BOUND>",
            "[noobj,{2}[{2}[{a,b}]{100}]{11}] + [y]                                       % [y]",
            "[{[{2}[{a,b}]{50},noobj],c}] + [y]                                           % [{c,y}]",
            "[{2}[{2}[{a,b}]{100}]{11}].as(uri::T)                                        % <BOUND>",
            "[{2}[{2}[{a,b}]{100}]{11}]{1,2}                                              % <BOUND>",
            "[{-1}[{-1}[{-1}[{-1}[x,y]{100}]{100}]{100}]{100}]{100} + [y]                 % <BOUND>",
            "[{-1}[{-1}[{-1}[{-1}[x,y]{100}]{100}]{100}]{100}]{100}.as(uri::T)            % <BOUND>",
            "[{-1}[{-1}[{-1}[{-1}[x,y]{100}]{100}]{100}]{100}]{1,2}                       % <BOUND>",
            "[{-1}[{-1}[{-1}[{-1}[x,y]{100}]{100}]{100}]{100}]{100}.pow(-1) + [y]         % <BOUND>",
            "[{-1}[{-1}[{-1}[{-1}[x,y]{100}]{100}]{100}]{100}]{100}.pow(-1).count()       % 1",
            // a shared sub-word is held once: 200 letters held in 100 expand, while 250,000 held in 100 are refused
            "[{-1}[x,y]{50}]{2}.plus([{-1}[x,y]{50}]{2}.neg())                            % noobj",
            "[{-1}[{-1}[x,y]{50}]{50}]{50} + [y]                                          % <BOUND>",
    }, delimiter = '%')
    public void testTheWordsAreBoundedBeforeTheyAreBuilt(final String code, final String expected) {
        final Machine machine = Machine.current();
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> Machine.Helper.evalAt(machine, () -> {
            if (!"<BOUND>".equals(expected.trim()))
                AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
            else {
                String refusal;
                try {
                    refusal = Machine.parse(code).apply(noobj()).stream().map(o -> ObjmtronSerializer.single().write(o)).toList().toString();
                } catch (final Exception e) {
                    refusal = String.valueOf(e.getMessage());
                }
                final String bound = refusal;
                assertTrue(bound.contains(" copies unfold past the typer's bound for lst"), () -> code + " => " + bound);
            }
            return noobj();
        }), code);
    }

    /// THE WORD RING KEEPS ITS LAWS on lsts that hold braces, sub-words, points, holes and letters of every kind, each
    /// side compared in the sum's normal form (a product concatenates as written): + is associative and commutative with
    /// the zero as its identity, * is associative with [,] as its unity and the zero absorbing, * distributes over + on
    /// both sides, the converse is an involution that reverses a product and distributes over a sum, and a span's powers
    /// add, a range being the sum of its powers.
    @Test
    public void testTheWordRingKeepsItsLaws() {
        final List<Lst> samples = Stream.of("[a,b,c]", "[a,d,c]", "[1,2]", "[x,{[a,b],c},y]", "[{2}a,b]", "[[1,2],3]",
                "[plus(1),{a,b}]", "[a]", "[b,{[,],c}]", "[1,noobj]", "[,]", "[x,{[a,b],[a,b]}]").map(code -> ObjmtronSerializer.parse(code).asLst()).toList();
        final Function<Lst, Lst> normal = x -> Lst.Helper.sum(Stream.of(x));
        final Function<Lst, Lst> converse = x -> Lst.Helper.power(x, cInt.of(-1L));
        final Lst one = lst();
        final Lst zero = lst().zero();
        for (final Lst x : samples) {
            checkEquality(LOG, normal.apply(x), normal.apply(x.plus(zero)), true);
            checkEquality(LOG, normal.apply(x), normal.apply(zero.plus(x)), true);
            checkEquality(LOG, x, x.mult(one), true);
            checkEquality(LOG, x, one.mult(x), true);
            checkEquality(LOG, zero, x.mult(zero), true);
            checkEquality(LOG, zero, zero.mult(x), true);
            checkEquality(LOG, x, converse.apply(converse.apply(x)), true);
            checkEquality(LOG, Lst.Helper.power(x, cInt.of(3L)), Lst.Helper.power(x, cInt.of(2L)).mult(x), true);
            checkEquality(LOG, Lst.Helper.power(x, cInt.of(-2L)), converse.apply(Lst.Helper.power(x, cInt.of(2L))), true);
            checkEquality(LOG, Lst.Helper.power(x, cInt.of(1L, 2L)), x.plus(x.mult(x)), true);
            for (final Lst y : samples) {
                checkEquality(LOG, x.plus(y), y.plus(x), true);
                checkEquality(LOG, normal.apply(converse.apply(x.mult(y))), normal.apply(converse.apply(y).mult(converse.apply(x))), true);
                checkEquality(LOG, normal.apply(converse.apply(x.plus(y))), normal.apply(converse.apply(x).plus(converse.apply(y))), true);
                for (final Lst z : samples) {
                    checkEquality(LOG, x.plus(y).plus(z), x.plus(y.plus(z)), true);
                    checkEquality(LOG, x.mult(y).mult(z), x.mult(y.mult(z)), true);
                    checkEquality(LOG, normal.apply(x.mult(y.plus(z))), x.mult(y).plus(x.mult(z)), true);
                    checkEquality(LOG, normal.apply(y.plus(z).mult(x)), y.mult(x).plus(z.mult(x)), true);
                }
            }
        }
        // a lst that carries its own point: * concatenates as written, its lhs's point floating and its argument's on the
        // product's first letter, so the product's unity and associativity hold in the sum's normal form, where the mass
        // of [{2}[,],a] and of [{2}a] is the same 2a
        final List<Lst> pointed = Stream.of("{2}[,]", "{2}[a,b]", "{-1}[x]", "[,]", "[a]", "[x,{[a,b],[a,b]}]", "{3}[{a,b},c]")
                .map(code -> ObjmtronSerializer.parse(code).asLst()).toList();
        for (final Lst x : pointed) {
            checkEquality(LOG, normal.apply(x), normal.apply(x.mult(one)), true);
            checkEquality(LOG, normal.apply(x), normal.apply(one.mult(x)), true);
            for (final Lst y : pointed)
                for (final Lst z : pointed)
                    checkEquality(LOG, normal.apply(x.mult(y).mult(z)), normal.apply(x.mult(y.mult(z))), true);
        }
    }

    @ParameterizedTest
    @CsvSource(value = {
            "[1,2,3]>> -1                                                                 % 3",
            "[1]>> -1                                                                    % 1",
            "[1,2,3]>> -2                                                                % 2",
            "[1,2,3]>> -3                                                                % 1",
            "[1]>> -2                                                                    % noobj",
            "[1,2,3]>> -4                                                                % noobj",
            "[1,2,3,4]>> 345                                                             % noobj",
            "[1,2,3]>>{1,-2}                                                             % {2,2}",
            "[1,2,3]>>{-1,-2,-3}                                                         % {3,2,1}",
            "[1,2,3]>>{0,-1,2}                                                           % {1,3,3}",
            // existing negative-index cases
            "[a,b,c]>>a                                                                 % noobj",
            "[a,b,c]>>+                                                                 % {a,b,c}",
            "[a,b,c]>><+>                                                               % {a,b,c}",
            "[a,b,c]>>{0,0}                                                             % {2}a",
            "[a,b,c]>>(-1)                                                              % c",
            "[a,b,c]>>{-1,-2}                                                           % {c,b}",
            "[a,b,c]>>{-2,1}                                                            % {2}b",
            "[a,[b=>1,c=>2],c]>><1/b>                                                   % 1",
            "[a,[b=>1,c=>2],c]>><1/+>                                                   % {1,2}",
            "[a,[b=>[d=>3,e=>4],c=>2],c]>><1/+/e>                                       % 4",
            "[a,[b=>[d=>3,e=>4],c=>2],c]>><1/+/+>                                       % {3,4}",
            "[{5,8}a,b,{7}c]>>{{4,6}0,0}                                                % {25,56}a", // two zeros collapse on the key select
            "{2,3}[{5,8}a,b,{7}c]>>{{4,6}0,0}                                           % {50,168}a", // two zeros collapse on the key select
            "[{5,8}a,b,{7}c]>>{{4,6}0,0}                                                % {25,56}a", // two zeros collapse on the key select
            "{2,3}[{5,8}a,b,{-7}c]>>{{4,6}0,0}                                          % {50,168}a", // two zeros collapse on the key select
            //  "{2,3}[{-5,8}a,b,{-7}c]>>?#{*}<=lst{{4,6}0,0}                              % {-50,168}a", // two zeros collapse on the key select
            // "{-2,3}[{-5,8}a,b,{-7}c]>>?#{**}<=lst{**}{{4,6}0,0}                             % {50,168}a", // two zeros collapse on the key select
            "[a,b,c]>>{<0>,<0>}                                                         % {2}a",
            "[a,b,c]>>{<0>,0}                                                           % {2}a",
            "[a,b,c]>>{<+>,0}                                                           % {{2}a,b,c}",
            "[a,b,c]>>{<+>,{15}<0>}                                                     % {{16}a,b,c}",
            "[a,b,c]>>{{2}<+>,{15}<0>}                                                  % {{17}a,{2}b,{2}c}",
            "[a,b,c]>>{{2}<+/>,{15}<0>}                                                 % {{15}a,{2}0=>a,{2}1=>b,{2}2=>c}", // should this be uri keyed? (using indexed stream)
            "[a,b,c]>>{{2}<+/>,{15}<0>}>>                                               % {{2}a,{2}b,{2}c}",
            "[a,{3}b,c]>>{{2}<+/>,{15}<0>}>>                                            % {{2}a,{6}b,{2}c}",
            "[a,{3,5}b,c]>>{{2}<+/>,{15}<0>}>>                                          % {{2}a,{6,10}b,{2}c}",
            "{10}[a,b,c]>>{{2}<+/>,{15}<0>}>>                                           % {{20}a,{20}b,{20}c}",
            "[a,b,c]>>{{2}<+/>,{15}<0>}>>.cc().sum()                                    % 6",
            "[a,b,c]>><+/>                                                              % {0=>a,1=>b,2=>c}", // should this be uri keyed? (using indexed stream)
            // a trailing '/' names the relation of the whole read, keyed by the whole address (Marko, 2026-11-25), so a read
            // that runs through a slot is one rel, into a rec, out of a rec, or through lsts alone. A pattern's read keys
            // each match by the step it ends at, as a rec's does, where a space keys it by the whole address
            "[[a=>[b=>1]]]>><0/a/b/>                                                    % <0/a/b>=>1",
            "[a=>[[b=>1]]]>>(a/0/b/)                                                    % <a/0/b>=>1",
            "[[1,2]]>><0/1/>                                                            % <0/1>=>2",
            "[[1,2]]>><0/5/>                                                            % noobj",
            "[[1,2],[3,4]]>><+/1/>                                                      % {<1>=>2,<1>=>4}",
            "[[1,2]]>><0/+/>                                                            % {0=>1,1=>2}",
            "[a,b,c]>><#>                                                               % {a,b,c}",
            "[a,b,c]>><#/>                                                              % {0=>a,1=>b,2=>c}", // should this be uri keyed? (using indexed stream)
            "[a,b,c]>>0                                                                 % a",
            "[a,b,c]>>1                                                                 % b",
            "[a,b,c]>>2                                                                 % c",
            "[a,b,c]>>3                                                                 % noobj",
            "[a,b,c]>><0/>                                                              % <0>=>a",
            "[a,b,c]>><1/>                                                              % <1>=>b",
            "[a,b,c]>><2/>                                                              % <2>=>c",
            "[a,b,c]>><3/>                                                              % noobj",
            "[a,b,c]>>{1,2}                                                             % {b,c}",
            "[a,b,c]>>{1,2,3}                                                           % {b,c}",
            "[a,b,c]>>{0,1}                                                             % {a,b}",
            "[a,b,c]>>{0,1,2}                                                           % {a,b,c}",
            "[a,b,c]>>{0,1,2,3}                                                         % {a,b,c}",
            "[a,b,c]>>{0,1,2,3,4}                                                       % {a,b,c}",
            "[a,b,c]>>{<0/>,<1/>}                                                       % {<0>=>a,<1>=>b}",
            "[a,b,c]>>{<0/>,<1/>,<2/>}                                                  % {<0>=>a,<1>=>b,<2>=>c}",
            "[a,b,c]>>{<0/>,<1/>,<2/>,<3/>}                                             % {<0>=>a,<1>=>b,<2>=>c}",
            "[a,b,c]>>{<0/>,<1/>,2,<3>}                                                 % {<0>=>a,<1>=>b,c}",
            "[a,b,c]>>{0,<1/>,<2>}                                                      % {a,<1>=>b,c}",
            "[a,b,c]>>{<0>,{23}<1/>,2}                                                  % {a,{23}<1>=>b,c}",
            "[a,b,c]>>{0,{23}<1/>,2}>>                                                  % {23}b",
            "{2}[a,b,c]>>{<100>,{23}<1>,34}                                             % {46}b",
            "{2}[a,b,c]>>{<0>,{23}<1/>,<2>}                                             % {{2}a,{46}<1>=>b,{2}c}",
            "{2}[a,b,c]>>{<0>,{23}<1/>,<2>}                                             % {2}[a,{23}<1>=>b,c]>-",
            "{2}[a,b,c]>>{<0>,<1>,2}.<<                                                 % {6}<>",
            "{2}[a,b,{4}c]>>2                                                           % {8}c",
            // an element reached by >> ascends to its lst, and << takes no depth (a uri's << does)
            "[1,2,[a=>3],4]>>2.<<                                                       % [1,2,[a=>3],4]",
            "[1,2,[a=>3],4]>>0.<<                                                       % [1,2,[a=>3],4]",
            "[1,2,[a=>3],4]>>0.<<5                                                      % noobj",
            // a ..-led !* reads the lst holding it, by index
            "[2,!*<../0>]>>1                                                              % 2",
            "[[b=>2,c=>!*<../b>]]>>0>>c                                                   % 2",
            // "{2}[a=>1,b=>2,c=>3]>>{a,{23}b/,c}.<<                                       % {25}[a=>1,b=>2,c=>3]", // TODO: review: is this the semantics we want?
    }, delimiter = '%')
    public void testAt(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            // Step walk vs path walk vs hybrid — all equivalent
            "[1,2,[3,4,[5,6,7]]]>>2>>2>>0                                            % 5",
            "[1,2,[3,4,[5,6,7]]]>><2/2/0>                                            % 5",
            "[1,2,[3,4,[5,6,7]]]>><2/2>>>0                                           % 5",
            "[1,2,[3,4,[5,6,7]]]>><2>>><2/0>                                         % 5",
            // Deep nesting
            "[\"deep\"].>><0>                                                     % \"deep\"",
            "[[\"deep\"]].>><0/0>                                                 % \"deep\"",
            "[[[\"deep\"]]].>><0/0/0>                                             % \"deep\"",
            "[[[[\"deep\"]]]].>><0/0/0/0>                                         % \"deep\"",
            "[[[[\"deep\"]]]].>><0/0>.>><0/0>                                     % \"deep\"",
            "[[[[\"deep\"]]]].>><0>.>><0>.>><0>.>><0>                             % \"deep\"",
            "[\"deep\"].==0                                                       % \"deep\"",
            "[[\"deep\"]].==0==0                                                  % \"deep\"",
            "[[[\"deep\"]]].==0==0==0                                             % \"deep\"",
            "[[[[\"deep\"]]]].==0==0==0==0                                        % \"deep\"",
            "[[[[\"deep\"]]]].==0==0==0==0                                        % \"deep\"",
            "[[[[\"deep\"]]]].==<0/0/0/0>                                         % \"deep\"",
            "[[[[\"deep\"]]]].==<0/+/+/0>                                         % \"deep\"",
            "[[[[\"deep\",\"seek\"]]]].==<0/0/0/+>                                % {\"deep\",\"seek\"}",
            "[[[[\"deep\"]]]].==<0/0/0>==0                                        % \"deep\"",
            "[[[[\"deep\"]]]].==0==<0/0>==0                                       % \"deep\"",
            "[[[[\"deep\"]]]].==<0>.==<0>.==<0>.==<0>                             % \"deep\"",
            //  "[[[[\"deep\"]]]].==<#>                                     % \"deep\"",
            // "[[[[[\"deep\"]]]]].>><0>.>><0>.>><0>.>><0>.>><0>                     % \"deep\"",
            // "[[[[[\"deep\"]]]]].>><0/0/0/0/0>                                     % \"deep\"",
            // "[[[[[[[\"deep\"]]]]]]]>><0/0>.>><0/0>.>><0>                          % \"deep\"",
            // Missing index
            "[1,2,3]>><2/0>                                                                % noobj",
            "[1,2,3]>><5>                                                                  % noobj",
            // The root has no segment to walk
            "[1,2,3]>></>                                                                  % noobj",
            // Leaf is not a poly — can't walk further
            "[1,2,3]>><0/0>                                                                % noobj",
            // Mixed — walk through rec nested in lst
            "[[a=>[b=>42]]]>><0/a/b>                                                       % 42",
            "[[a=>[b=>42]]]>><0>>>a>>b                                                     % 42",
            // Lst multi-segment URI path walks — parentheses needed so the
            // parser treats the full path as a single >> argument.
            "[1,2,[3,4,[5,6,7]]]>><2/2/0>                                                  % 5",
            "[1,2,[3,4,[5,6,7]]]>><2/2>                                                    % [5,6,7]",
            "[[3,4,[5,6,7]]]>><0/2>                                                        % [5,6,7]",
            "[1,2,3]>><0/0>                                                                % noobj",
    }, delimiter = '%')
    public void testPathWalking(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "[a,b,c,d,e]==[?<2 => * X, ?>=2 => * Y]                % [a/X,b/X,c/Y,d/Y,e/Y]",
            "[1,2,3,4,5].as(rec::T)?[_ => ?>6].rng()               % noobj",
            "[1,2,3,4,5].as(rec::T)?[_ => ?<6].rng()>-[,]          % [1,2,3,4,5]"
    }, delimiter = '%', quoteCharacter = '~')
    public void testSelect(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @Training.SkipTraining(reason = "the row is a Java-side Lst.at(key) read with key and value in their own columns; the empty-root key <> has no >> step the test asserts, so one {{{lst}}}>>{{{key}}} template cannot cover every row")
    @ParameterizedTest
    @CsvSource(value = {
            // lst                                 | key                  | value
            "[a,[b,[c,d],e],f]                     | <>                   | noobj",
            "[a,[b,[c,d],e],f]                     | </>                  | noobj",
            "[a,[b,[c,d],e],f]                     | <0>                  | a",
            "[a,[b,[c,d],e],f]                     | <1/0>                | b",
            "[a,[b,[c,d],e],f]                     | <1/1/0>              | c",
            "[a,[b,[c,d],e],f]                     | <1/1/1>              | d",
            "[a,[b,[c,d],e],f]                     | <1/1/+>              | {c,d}",
            "[a,[b,[c,d],e],f]                     | <1/+/+>              | {c,d}",
            "[a,[b,[c,d],[e,f]],g]                 | <1/+/+>              | {c,d,e,f}",
            "[a,[b,[c,d],[e,[f,g]]],h]             | <1/+/+>              | {c,d,e,[f,g]}",
            "[a,[b,[c,d],e],f]                     | <1/+>                | {b,[c,d],e}",
            "[a,[b,[c,d],e],f]                     | <1/+>                | {b,[c,d],e}",
            "[a,[b,[c,d],e],f]                     | <#>                  | {a,[b,[c,d],e],f}" // TODO: should this be unrolled?
    }, delimiter = '|')
    public void testKeyValue(final String lst, final String key, final String value) {
        Lst r = mParser.m_obj().parse(lst).get();
        Obj k = mParser.m_obj().parse(key).get();
        Obj v = mParser.m_obj().parse(value).get();
        Obj actual = r.at(k);
        LOG.debug("testing %s at %s is %s [expected:%s]", k, r, actual, v);
        assertTrue(r.isLst());
        assertEquals(v, actual);
    }


    @ParameterizedTest
    @CsvSource(value = {
            "[a,[b,[c,d],e],f]                                                                   % [a,[b,[c,d],e],f]",
            "[a,[b,[c,d],e],f]>>0                                                                % a",
            "[a,[b,[c,d],e],f]>><1/0>                                                            % b",
            "[a,[b,[c,d],e],f]>><1/1/0>                                                          % c",
            "[a,[b,[c,d],e],f]>><1/1/1>                                                          % d",
            "[a,[b,[c,d],e],f]>><1/1/+>                                                          % {c,d}",
            "[a,[b,[c,d],e],f]>><1/+/+>                                                          % {c,d}",
            "[a,[b,[c,d],[e,f]],g]>><1/+/+>                                                      % {c,d,e,f}",
            "[a,[b,[c,d],[e,[f,g]]],h]>><1/+/+>                                                  % {c,d,e,[f,g]}",
            "[a,[b,[c,d],e],f]>><1/+>                                                            % {b,[c,d],e}",
            "[a,[b,[c,d],e],f]>><1/+>                                                            % {b,[c,d],e}",
            "[a,[b,[c,d],e],f]>><#>                                                              % {a,[b,[c,d],e],f}" // TODO: should this be unrolled?

    }, delimiter = '%')
    public void testCode(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "lst{10}::[1,2,3]                                                                        % lst{10}::[1,2,3]",
            "lst{10}::[1,2,3]>-                                                                      % {int{10}::1,int{10}::2,int{10}::3}",
            "lst{10}::[1,2,3]._/sum()\\_                                                             % lst{10}::[6]",
            "lst{10}::[1,2,3]._/sum()\\_._/sum()\\_                                                  % lst{10}::[6]",
            "lst{10}::[1,2,3]._/sum()\\_.>-.-<?lst<=#{*}([_])._/sum()\\_                             % [60]",
            "lst{10}::[1,2,3]._/sum?int<=int{*}()\\_.>-.-<?lst<=#{*}([_])._/sum?int<=int{*}()\\_     % [60]",
            "lst{10}::[1,2,3]._/sum()\\_.-<?lst<=#{*}[_]                                             % [{10}[6]]",
            "lst{10}::[1,2,3]._/sum()\\_.-<[_]                                                       % {10}[[6]]",
            "lst{10}::[1,2,3]._/sum()\\_.-<?lst<=#{*}[-<[_]]                                         % [{10}[[6]]]",
            "lst{10}::[1,2,3]._/sum()\\_.-<?lst<=#{*}[-<[_]-<[_]]                                    % [{10}[[[6]]]]",
            "lst{10}::[1,2,3]._/sum()\\_.-<[-<[_]-<[_]]                                              % {10}[[[[6]]]]",
            "lst{10}::[1,2,3]._/sum()\\_.-<[-<[-<[_]>-]]                                             % {10}[[[6]]]",
            //   "lst{10}::[1,2,3]._/sum()\\_.-<?lst<=#{*}[-<[-<[_]>-]]                                   % [[{10}[6]]]", //TODO: this is strange
            "[1,2,3]._/sum()\\_.-<?lst<=#{*}[-<[-<[_]>-]]                                            % [[[6]]]",
            "lst{10}::[1,2,3]._/sum()\\_-<[-<[_]>-]>-                                                % lst{10}::[6]",
            "lst{10}::[1,2,3]._/sum()\\_-<[-<[_]]>-.>-                                               % lst{10}::[6]",
            "lst{10}::[1,2,3]._/sum()\\_-<[-<[_]]>-.>-.>-                                            % int{10}::6",
            "lst{10}::[1,2,3]._/sum()\\_-<[-<[_]>-]>-.>-                                             % int{10}::6",
            "lst{10}::[1,2,3]._/sum()\\_-<[-<[_]>-.>-].>-                                            % int{10}::6",
            "lst::[1,2,3]._/sum()\\_-<[-<[_]>-.>-].>-                                                % int::6",
            "lst{10}::[1,2,3]._/sum()\\_-<[-<[_]]                                                    % {10}[[[6]]]",
            "lst{10}::[1,2,3]._/sum()\\_-<[-<[_]>-.>-.>-]                                            % {10}[6]",
            "lst{10}::[1,2,3]>-.sum?int<=int{*}()                                                    % 60",
            "lst{10}::[1,2,3]>-.sum().sum()                                                          % 60",
            "lst{10}::[1,2,3]>-.sum()                                                                % 60",
            "lst::[1,2,3]>-.sum()                                                                    % 6",
            "lst::[1,2,3]>-.sum?int<=int{*}()                                                        % 6",
            "lst::[1,2,3]>-.sum().sum()                                                              % 6",
            "lst::[1,2,3]>-.sum()                                                                    % 6",
            // a bulked lst sums as the copies it stands for, the word ring keeping their mass on the first letter
            "lst{2}::[1,2].sum()                                                                     % [{2}1,2]",
            "[1,2]-<[_,_]>-.sum()                                                                    % [{2}1,2]",
            "[1,2]-<[_,_,_]>-.sum()                                                                  % [{3}1,2]",
            "lst{2}::[{3}1,2].sum()                                                                  % [{6}1,2]",
            // a lst letter takes no point, since a lst with a point is a brace: it is wrapped as the sub-word that holds it
            "lst{2}::[[1,2],3].sum()                                                                 % [{2}[[1,2]],3]",
            "[[1,2],3]-<[_,_]>-.sum()                                                                % [{2}[[1,2]],3]",
            // and its product concatenates the copies
            "lst{2}::[1,2].prod()                                                                    % [1,2,1,2]",
            "[1,2]-<[_,_,_]>-.prod()                                                                 % [1,2,1,2,1,2]",
    }, delimiter = '%')
    public void testCoefficients(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    /*
       "lst{10}::[1,2,3]._/sum()\\_.>-.-<?lst<=#{*}([_])._/sum()\\_                                  % [60]",
            "lst{10}::[1,2,3]._/sum()\\_.-<?lst<=#{*}[_]                                             % [lst{10}::[6]]",
            "lst{10}::[1,2,3]._/sum()\\_.-<?lst<=#{*}[-<[_]]                                         % [lst{10}::[[6]]]",
            "lst{10}::[1,2,3]._/sum()\\_.-<?lst<=#{*}[-<[_]-<[_]]                                    % [lst{10}::[[[6]]]]",
            "lst{10}::[1,2,3]._/sum()\\_.-<?lst<=#{*}[-<[-<[_]>-]]                                   % [lst{10}::[[6]]]",
            "lst{10}::[1,2,3]._/sum()\\_-<[-<[_]>-]>-                                                % lst{10}::[6]",
            "lst{10}::[1,2,3]._/sum()\\_-<[-<[_]]>-.>-                                               % lst{10}::[6]",
            "lst{10}::[1,2,3]._/sum()\\_-<[-<[_]]>-.>-.>-                                            % int{10}::6",
            "lst{10}::[1,2,3]._/sum()\\_-<[-<[_]>-]>-.>-                                             % int{10}::6",
            "lst{10}::[1,2,3]._/sum()\\_-<[-<[_]>-.>-].>-                                            % int{10}::6",
            //"lst{10}::[1,2,3]._/sum()\\_-<[-<[_]>-.>-.>-]                                            % [int{10}::6]",
     */

    @Training.SkipTraining(reason = "rows are a stateful sequence over a one-time @TestData seed: each @...>>= mutates the stored lst and the next row's read asserts that mutation, so no row is independently (expression -> value)")
    @ParameterizedTest
    @TestData(value = {"/sys/tmp/lst_test/0 -> [a=>1,b=>[a,b,c]]"}, oneTime = true)
    @CsvSource(value = {
            "*/sys/tmp/lst_test/0 >>= [a=>2]                                     % [a=>2,b=>[a,b,c]]",
            "*/sys/tmp/lst_test/0                                                % [a=>1,b=>[a,b,c]]",
            "@/sys/tmp/lst_test/0 >>= [a=>2]                                     % [a=>2,b=>[a,b,c]]",
            "*/sys/tmp/lst_test/0                                                % [a=>2,b=>[a,b,c]]",
            "*/sys/tmp/lst_test/0 >>= +[b=>2]                                    % [a=>2,b=>{2,[a,b,c]}]",
            // REC MINUS IS THE INVERSE OF REC PLUS (Marko, a50f20251): an entry the rec does not hold is not removed but
            // owed -- the copy above wrote nothing back, so b holds no 2 to cancel -- and a written plus then minus cancels
            "*/sys/tmp/lst_test/0 >>= -[b=>2]                                    % [a=>2,b=>{[a,b,c],{-1}2}]",
            "@/sys/tmp/lst_test/0 >>= +[b=>2]                                    % [a=>2,b=>{2,[a,b,c]}]",
            "@/sys/tmp/lst_test/0 >>= -[b=>2]                                    % [a=>2,b=>[a,b,c]]",
            "@/sys/tmp/lst_test/0 >>= [b=>_ * [d,e]]                             % [a=>2,b=>[a,b,c,d,e]]",
            "*/sys/tmp/lst_test/0                                                % [a=>2,b=>[a,b,c,d,e]]",
            "@/sys/tmp/lst_test/0 >>= [b=>-[a,d]]                                % [a=>2,b=>[b,c,e]]",
            "*/sys/tmp/lst_test/0                                                % [a=>2,b=>[b,c,e]]",
            "@/sys/tmp/lst_test/0                                                % [a=>2,b=>[b,c,e]]@/sys/tmp/lst_test/0",
            // "@/sys/tmp/lst_test/0 >>= +[a=>3,b=>_ * [d,e]]                       % [a=>{2,3},b=>[b,c,e,d,e]]",
            // "*/sys/tmp/lst_test/0                                                % [a=>{2,3},b=>[b,c,e,d,e]]",
    }, delimiter = '%')
    public void testUpdate(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "[1,2,3].as(rec::T)                                                % [0=>1,1=>2,2=>3]",
            "[1,2,3,4,5,6].as(rec::T) >>= [5 => 'X']                           % [0=>1,1=>2,2=>3,3=>4,4=>5,5=>\'X\']",
            "[1,2,3,4,5,6].as(rec::T) >>= [5 => 'X'].as(lst::T)                % [(0=>(0=>1)),(1=>(1=>2)),(2=>(2=>3)),(3=>(3=>4)),(4=>(4=>5)),(5=>(5=>\'X\'))]",
            "[1,2,3,4,5,6].as(rec::T) == [is(gt(1)).is(lt(5)) => 'X']             % [2=>\'X\',3=>\'X\',4=>\'X\']",
            "[b=>2,a=>1,c=>3] >>= [c => \'X\'].as(lst::T)                         % [(0=>(c=>\'X\')),(1=>(b=>2)),(2=>(a=>1))]",
            "lst{10}::[1,2,3].as(rec::T)                                       % rec{10}::[0=>1,1=>2,2=>3]",
            "lst{10}::[1,2,3].as(rec::T).merge?rel{*}<=rec()                   % {rel{10}::(0=>1),rel{10}::(1=>2),rel{10}::(2=>3)}",
            "[1,2,3].as(rec::T).>-.isa(rel::T).count()                         % 3",
            "{10}[1,2,3].as(rec::T).>-.isa(rel::T).count()                     % 30",
            "[1,2,3].as(rec::T).>-.isa(rel::T).count()                         % 3",
            "[1,2,3].as(rec::T).>-.>>.isa(int::T).count()                      % 3",
            "[1,2,3].as(rec::T).>-.>>.sum()                                    % 6",
            "{10}[1,2,3].as(rec::T).>-.>>.sum()                                % 60",
            "{10}[{5}1,2,3].as(rec::T).>-.>>.sum()                             % 100",
            // "{10}[{5}1,{-10}2,{2}3].as(rec::T).>-.>>.sum()                     % -70",
            "[1,2,3].as(rec::T).as(lst::T)                                     % [(0=>(0=>1)),(1=>(1=>2)),(2=>(2=>3))]",
            "{35}[1,{2}2,3].as(rec::T).as(lst::T)                              % {35}[(0=>(0=>1)),(1=>(1=>{2}2)),(2=>(2=>3))]",
            "[1,2,3].as(rec::T).as(rec::T)                                     % [0=>1,1=>2,2=>3]",
            "[1,2,3].as(rec::T).as(lst::T).as(rec::T)                          % [0=>(0=>(0=>1)),1=>(1=>(1=>2)),2=>(2=>(2=>3))]",

    }, delimiter = '%')
    public void testAs(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    /// THE CONVERSE (Claim 21), the span -1 after the brackets and pow(-1) at run time (Marko, 2026-11-25: the bolt-on
    /// reverse() is retired): the letters in the reverse order, each held as it is, a nested lst being a letter, and a
    /// brace's sub-words reversed with it. It is an involution and reverses a product, but it is no inverse.
    @ParameterizedTest
    @CsvSource(value = {
            "[a,b,c]{-1}                                                         % [c,b,a]",
            "[12,bac,25,45,\"_245\"]{-1}                                         % [\"_245\",45,25,bac,12]",
            "[abc,def,ghi].pow(-1)                                               % [ghi,def,abc]",
            "['abc','def','ghi'].pow(-1)==[reverse(),reverse(),reverse()]        % ['ihg','fed','cba']",
            "[a,[b,c],[d,e]]{-1}                                                 % [[d,e],[b,c],a]",
            "[a,[b,c],[d,e]].pow(-1)==[pow(-1),pow(-1),pow(-1)]                  % [[e,d],[c,b],a]",
            "[a,[b,c],[d,e]].pow(-1)==[pow(-1),>-.count(),pow(-1)]               % [[e,d],2,a]",
            "[,]{-1}                                                             % [,]",
            "[a]{-1}                                                             % [a]",
            "[a,b].pow(-1).pow(-1)                                               % [a,b]",
            "[x,{[a,b],[c,d]},y]{-1}                                             % [y,{[b,a],[d,c]},x]",
            "[{2}a,b]{-1}                                                        % [b,{2}a]",
            "[plus(1),mult(2)]{-1}                                               % [mult(2),plus(1)]",
            "[a,b].mult([a,b].pow(-1))                                           % [a,b,b,a]",
            "[a,b].mult([c]).pow(-1)                                             % [c].pow(-1).mult([a,b].pow(-1))",
            "[a,b].reverse()                                                     % <ERROR>",
    }, delimiter = '%')
    public void testReverse(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }


    @ParameterizedTest
    @CsvSource(value = {
            "[1,2,3].merge()                                                     % {1,2,3}",
            "[a,b,c].merge()                                                     % {a,b,c}",
            "[,].merge()                                                         % {,}",
            "[[a,b],[c,d]].merge()                                               % {[a,b],[c,d]}",
            // AN INDEX HOLDS ANY MASS (Marko, 2026-11-24: merge?A{*}<=lst[{**}A{*}]): the 5s interfere, constructively
            // and destructively. Under {*}, zero to unbounded, the domain gate refused the negative point
            "[{2}5,{3}5,7].merge()                                               % {{5}5,7}",
            "[{2}5,{-2}5,7].merge()                                              % 7",
            "[{2}5,{-2}5,7]>-                                                    % 7",
            // the A the lst binds reaches the reducer after it (two objs fold to one), and a total cancellation unrolls
            // to nothing
            "[{2}5,{-1}5,7]>-.sum()                                              % 12",
            "[{2}5,{-2}5]>-                                                      % noobj",
            // the range stays A{*} (Marko, 2026-11-24, domain only), so a negative total is a range fail until stream
            // doms take {**}: an A{**} range left the reducer after a merge unbound ([1,2,3]>-.sum() folded per obj)
            // "[{-2}5].merge()                                                  % {-2}5",
    }, delimiter = '%')
    public void testMerge(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "[1,2,3]>-.count()                                                     % 3",
            "[a,b]>-.count()                                                       % 2",
            "[a]>-.count()                                                         % 1",
            "[,]>-.count()                                                         % 0",
            "[[a,b],c,[d,e,f]]>-.count()                                           % 3",
    }, delimiter = '%')
    public void testCount(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    /// PLUS IS THE WORD RING'S SUM, never an append (Marko, 2026-11-25): an append is the product. The argument counts by
    /// its point, as a uri's does, which retires the 11-17 refusal of a bulked argument, while a stream of lsts is still
    /// no argument for one plus.
    @ParameterizedTest
    @CsvSource(value = {
            "[1,2,3].plus([4,5])                                                 % [{[1,2,3],[4,5]}]",
            "[a,b].plus([c])                                                     % [{[a,b],c}]",
            "[,].plus([a])                                                       % [{[,],a}]",
            "[a].plus([,])                                                       % [{[,],a}]",
            "[1,2,3].mult([4,5])                                                 % [1,2,3,4,5]",
            "[,].mult([,])                                                       % [,]",
            "lst{2}::[1,2].plus([3])                                             % lst{2}::[{[1,2],3}]",
            "[1].plus(lst{2}::[{3}2])                                            % [{1,{6}2}]",
            "[1].plus({[2,3],[4]})                                               % <ERROR>",
    }, delimiter = '%')
    public void testPlus(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @Training.SkipTraining(reason = "rows assert Obj.test on a parsed value; one row's lhs is a multiplicity ({1,2,3}), which an mtron expression evaluates one obj at a time rather than as the one value the test asserts on")
    @ParameterizedTest
    @CsvSource(value = {
            "[1,2,3]                                     % lst::T                       % true",
            "[1,2,3]                                     % lst[int,int,int]::T          % true",
            "[1]                                         % lst[int,int]::T              % false",
            "[1]                                         % lst[int,{0}int]::T           % true",
            "[1]                                         % lst[int,{?}int]::T           % true",
            "[,]                                         % lst[{?}int,{?}int]::T        % true",
            "[,]                                         % lst[{0}int,{0}int]::T        % true",
            "[,]                                         % lst[{0}int,int]::T           % false",
            "[a=>1,b=>2]                                 % lst[str,str]::T              % false",
            "{1,2,3}                                     % lst[int,int,int]::T          % false",
            "['a',1,['b',2]]                             % lst[str,int,lst]::T          % true",
            "['a',{2}1]                                  % lst[{?}str,{2,3}int]::T      % true",
            "['a',{4}1]                                  % lst[{?}str,{2,3}int]::T      % false",
            "[{0}1,1,['b',2]]                            % lst[{?}int,int,lst]::T       % true",
            "[{0}1,1,['b',2]]                            % lst[int,int,lst]::T          % false",
            "[{1}1,{5}'a',2.01234]                       % lst[int,{+}str,real]::T      % true",
            "[{1}1,{5}'a',real::T]                       % lst[int,{+}str,real]::T      % true",
            "lst[int,{+}str,real]::T                     % lst[int,{+}str,real]::T      % true",
            "lst[int,str,real]::T                        % lst[int,{+}str,real]::T      % true",
            "lst[int,{0}str,real]::T                     % lst[int,{+}str,real]::T      % false",
            "[{1,2,3},{'a','b'}]                         % lst[{3}int,{+}str]::T        % true",
            "[{1,2,3},{'a','b'}]                         % lst[{*}int]::T               % true",
            // AN INDEX'S LEFT COEFFICIENT IS THE MULTIPLICITY OF THE VALUE AT THAT INDEX, not another index: a bracket
            // reads its types as values (Marko, 2026-11-18), so lst[{2}int,str] is a two-index list whose index 0 holds
            // two ints, {1,2} belongs there and a lone 1 does not, while lst[int{2},str] is three indices. The boundary
            // rows read the left coefficient as a multiplicity: {n} exactly n, {n,m} a range, {+} at least one, {?} zero
            // or one (the absent form is the shorter list above), {*} zero or more.
            "[{1,2},'a']                                % lst[{2}int,str]::T           % true",
            "[{2}1,'a']                                 % lst[{2}int,str]::T           % true",
            "[1,'a']                                    % lst[{2}int,str]::T           % false",
            "[{1,2,3},'a']                              % lst[{2}int,str]::T           % false",
            "[{1,2},'a']                                % lst[{2,3}int,str]::T         % true",
            "[{1,2,3},'a']                              % lst[{2,3}int,str]::T         % true",
            "[{1,2,3,4},'a']                            % lst[{2,3}int,str]::T         % false",
            "[{1,2},'a']                                % lst[{+}int,str]::T           % true",
            "[{1,2},'a']                                % lst[{*}int,str]::T           % true",
            "['a',{1,2}]                                % lst[str,{?}int]::T           % false",
            // a plain entry is exactly one value: two ints at index 0 are not an int
            "[{1,2},'a']                                % lst[int,str]::T              % false",
            // a trailing index that admits zero may be unfilled
            "['a']                                      % lst[str,{?}int]::T           % true",
    }, delimiter = '%')
    public void testPoly(final String list, final String type, final boolean matches) {
        AbstractMetatronTest.checkMatches(LOG, list, type, matches);
    }

    /**
     * A LST TESTS A LST INDEX BY INDEX (Marko, 2026-11-17). An index is an address that is not the obj, so a point is
     * mass at an index and a span a run of indices: {@code [{2}3]} is two 3s at index 0, a lst of one index, and
     * {@code [3{2}]} a 3 at each of indices 0 and 1, which a lst lays as {@code [3,3]}. On an axis a type's and an
     * instruction's span is extension too (Marko, 2026-11-18): {@code [{2}int::T]} is {@code [int::T,int::T]}, and a
     * zero span lays nothing, while a type's point is mass. A range cannot lay, so it is a run, as in a bracket
     * ({@code [str::T,{?}int::T]} reads as {@code lst[str,int{?}]}). An index passes when its mass is within the
     * pattern's and its copies are the pattern's. Past the last pattern index the lhs is open, even inside a value's run,
     * and a pattern index the lhs leaves empty passes only if its mass admits zero. Each row is
     * {@code lhs.isa?#{*}<=#{*}(rhs)}: the whole lhs comes back on a match, noobj otherwise.
     */
    @ParameterizedTest
    @TestData(value = {"import(/m/math)", "age -> int::T@age"})
    @CsvSource(value = {
            // ---- Marko's relations: a value's span lays indices, its point is mass at one ----
            "[3{2},5].isa?#{*}<=#{*}([int::T,int::T,int::T])                                       % [3,3,5]",
            "[3{2},5{2}].isa?#{*}<=#{*}([int::T,int::T,int::T])                                    % [3,3,5,5]",
            "[3{2}].isa?#{*}<=#{*}([int::T,int::T,int::T])                                         % noobj",
            "[3{2}].isa?#{*}<=#{*}([int::T])                                                       % [3,3]",
            "[3{2}].isa?#{*}<=#{*}([{2}int::T])                                                    % [3,3]",
            "[3{2}].isa?#{*}<=#{*}([{2}3])                                                         % noobj",
            "[3{2}].isa?#{*}<=#{*}([int{2}::T])                                                    % noobj",
            "[{2}3].isa?#{*}<=#{*}([int{2}::T])                                                    % [{2}3]",
            "[{2}3].isa?#{*}<=#{*}([{2}3])                                                         % [{2}3]",
            "[{2}3,5].isa?#{*}<=#{*}([int::T,int::T,int::T])                                       % noobj",
            "['a',1,1].isa?#{*}<=#{*}([str::T,int{2}::T])                                          % noobj",
            "['a',1,1].isa?#{*}<=#{*}([str::T,{2}int::T])                                          % ['a',1,1]",
            "['a',1,'b'].isa?#{*}<=#{*}([str::T,{2}int::T])                                        % noobj",
            "['a',1,1].isa?#{*}<=#{*}([str::T,{2}int{2}::T])                                       % noobj",
            "['a',{2}1,{2}1].isa?#{*}<=#{*}([str::T,{2}int{2}::T])                                 % ['a',{2}1,{2}1]",
            // ---- the span is extension, the point parallel: {2}int{3}::T is two indices of mass 3, never one of 6 ----
            "[{3}6,{3}6].isa?#{*}<=#{*}([{2}int{3}::T])                                            % [{3}6,{3}6]",
            "[{9}6].isa?#{*}<=#{*}([{2}int{3}::T])                                                 % noobj",
            // ---- a value's span is its run written out ----
            "[{2}1{3}].isa?#{*}<=#{*}([{2}1,{2}1,{2}1])                                            % [{2}1,{2}1,{2}1]",
            "[1,1].isa?#{*}<=#{*}([1{2}])                                                          % [1,1]",
            "[1{2}].isa?#{*}<=#{*}([1,1])                                                          % [1,1]",
            "[{2}1].isa?#{*}<=#{*}([1,1])                                                          % noobj",
            "[1,1].isa?#{*}<=#{*}([{2}1])                                                          % noobj",
            // ---- on an axis a type's span is extension, as a value's is; its point is mass ----
            "[{3}int::T].isa?#{*}<=#{*}([int::T])                                                  % [{3}int::T]",
            "[{3}int::T].isa?#{*}<=#{*}([int::T,int::T,int::T])                                    % [int::T,int::T,int::T]",
            "[int::T,int::T,int::T].isa?#{*}<=#{*}([{3}int::T])                                    % [int::T,int::T,int::T]",
            "[int::T,int::T].isa?#{*}<=#{*}([{3}int::T])                                           % noobj",
            "[{3}int::T].isa?#{*}<=#{*}([int{2}::T,int::T])                                        % noobj",
            "[int{3}::T].isa?#{*}<=#{*}([int{3}::T])                                               % [int{3}::T]",
            "[int{3}::T].isa?#{*}<=#{*}([int::T])                                                  % noobj",
            "[5,6].isa?#{*}<=#{*}([{2}nat::T])                                                     % [5,6]",
            "[5,6].isa?#{*}<=#{*}([nat::T,nat::T])                                                 % [5,6]",
            "[5,-1].isa?#{*}<=#{*}([nat::T,nat::T])                                                % noobj",
            "[5,-1].isa?#{*}<=#{*}([nat::T,{0}nat::T])                                             % [5,-1]",
            "[5,6].isa?#{*}<=#{*}([age::T,age::T])                                                 % [5,6]",
            "[{2}nat::T].isa?#{*}<=#{*}([str::T,str::T])                                           % noobj",
            // ---- a stream at an index is mass there, and each of its copies is tested ----
            "[{1,2},'a'].isa?#{*}<=#{*}([int{2}::T,str::T])                                        % [{1,2},'a']",
            "[{1,'b'},'a'].isa?#{*}<=#{*}([int{2}::T,str::T])                                      % noobj",
            // ---- mass zero is an empty index, and an index the lhs leaves out is one ----
            "[int{0}::T,[3=>'x',5=>'stuff']].isa?#{*}<=#{*}([noobj,[int::T=>str::T]])              % [int{0}::T,[3=>'x',5=>'stuff']]",
            "[,].isa?#{*}<=#{*}([int{?}::T])                                                       % [,]",
            "[,].isa?#{*}<=#{*}([int::T])                                                          % noobj",
            "['a'].isa?#{*}<=#{*}([str::T,int{?}::T])                                              % ['a']",
            "['a'].isa?#{*}<=#{*}([str::T,int::T])                                                 % noobj",
            "[1,2].isa?#{*}<=#{*}([int::T,noobj])                                                  % noobj",
            "[{2}1].isa?#{*}<=#{*}([int{?}::T])                                                    % noobj",
            "[{2}1].isa?#{*}<=#{*}([int{+}::T])                                                    % [{2}1]",
            // ---- on an axis a zero span lays nothing: the pattern is gone (id() is the no-axis reading) ----
            "['a'].isa?#{*}<=#{*}([{0}int::T])                                                     % ['a']",
            "['a',1].isa?#{*}<=#{*}([{0}int::T,int::T])                                            % noobj",
            "[1,'a'].isa?#{*}<=#{*}([{0}int::T,int::T])                                            % [1,'a']",
            "['a',1].isa?#{*}<=#{*}([{0}int{2}::T,int::T])                                         % noobj",
            "[{2}'a',1].isa?#{*}<=#{*}([{0}int{2}::T,int::T])                                      % noobj",
            "[,].isa?#{*}<=#{*}([{0}int::T])                                                       % [,]",
            "['a',1].isa?#{*}<=#{*}([int{0}::T,int::T])                                            % noobj",
            // ---- an instruction at an index is a predicate each copy meets ----
            "[1,5].isa?#{*}<=#{*}([_,is(gt(3))])                                                   % [1,5]",
            "[1,3].isa?#{*}<=#{*}([_,is(gt(3))])                                                   % noobj",
            // ---- past the last pattern index the lhs is open ----
            "[int::T,str::T].isa?#{*}<=#{*}([int::T])                                              % [int::T,str::T]",
            "[1,'a',2].isa?#{*}<=#{*}([{*}int::T,str::T])                                          % [1,'a',2]",
            "['a',1,'b'].isa?#{*}<=#{*}([str::T,{?}int::T])                                        % ['a',1,'b']",
            "['a','b'].isa?#{*}<=#{*}([str::T,{?}int::T])                                          % ['a','b']",
            "[1,2,'a'].isa?#{*}<=#{*}([{2}int::T,{2}str::T])                                       % noobj",
            "[{*}int::T].isa?#{*}<=#{*}([{*}int::T,str::T])                                        % noobj",
    }, delimiter = '%')
    public void testIndices(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    /**
     * BOTH WALKS JUDGE AN INDEX ALIKE (Marko, 2026-11-18: one funnel, not implementations that may conflict). A lst
     * meets a bracket through the run walk ({@link Type.Helper#runs}) and a lst of patterns through the index walk
     * ({@link Lst.Helper#testIndices}), and both ask one index the same question, {@link Type.Helper#holds}. Each row
     * puts one value at index 0 and asks both: the bracket entry is written as a value ({@code {2}int} is mass 2 at
     * one index), the pattern as a type literal ({@code int{2}::T}, the same mass).
     */
    @ParameterizedTest
    @TestData(value = {"import(/m/math)"})
    @CsvSource(value = {
            // value at the index | bracket entry | pattern     | holds
            "{2}5                  % {2}int        % int{2}::T   % true",
            "5                     % {2}int        % int{2}::T   % false",
            "{1,2}                 % {2}int        % int{2}::T   % true",
            "{1,'b'}               % {2}int        % int{2}::T   % false",
            "{3}5                  % int           % int::T      % false",
            "5                     % nat           % nat::T      % true",
            "-1                    % nat           % nat::T      % false",
            "\"a\"                   % int           % int::T      % false",
    }, delimiter = '%')
    public void testBothWalksJudgeAnIndexAlike(final String value, final String entry, final String pattern, final boolean holds) {
        final Obj lst = ObjmtronSerializer.parse("[" + value + "]");
        final Obj bracket = ObjmtronSerializer.parse("lst[" + entry + "]::T");
        final Obj patterns = ObjmtronSerializer.parse("[" + pattern + "]");
        assertEquals(holds, lst.test(bracket), lst + " ? " + bracket + " (the run walk)");
        assertEquals(holds, lst.test(patterns), lst + " ? " + patterns + " (the index walk)");
        assertEquals(holds, Type.Helper.holds(lst.lstValue().getFirst(), patterns.lstValue().getFirst()),
                "holds(" + value + ", " + pattern + ")");
    }

    /**
     * A SPAN ON AN AXIS IS EXTENSION, WITH NO AXIS A POWER (Marko, 2026-11-18). A type is an obj that performs a
     * computation, and with no axis its span makes code of it: {@code {4}int::T} is {@code int::T.int::T.int::T.int::T},
     * which is {@code int::T}. On an axis, a lst or an argument list, the span is the obj's extension along it, for a
     * value, a type and an instruction alike: {@code [{4}int::T]} is {@code [int::T,int::T,int::T,int::T]}, so
     * {@code 1-<[{4}int::T]} is {@code [1,1,1,1]} as {@code 1-<[int{4}::T]}, the point, is {@code [{4}1]}, and
     * {@code 1-<[{2}plus(1),{3}mult(2)]} maps one obj over a lst of instructions. {@code ?} is isa, and its operand is
     * isa's argument list: {@code 1?{4}int::T} is {@code 1.isa(int::T,int::T,int::T,int::T)}, which isa reads as
     * {@code 1.isa(int::T)}. A chain step and a branch's term have no axis, so there a span is the instruction's run.
     */
    @ParameterizedTest
    @TestData(value = {"import(/m/math)"})
    @CsvSource(value = {
            // extension on a lst
            "1-<[int::T]                        % [1]",
            "1-<[{4}int::T]                     % [1,1,1,1]",
            "1-<[int{4}::T]                     % [{4}1]",
            "1-<[{2}int{3}::T]                  % [{3}1,{3}1]",
            "1-<[{2}plus(1)]                    % [2,2]",
            "1-<[{2}plus(1),{3}mult(2)]         % [2,2,2,2,2]",
            "start([{4}int::T])                 % [int::T,int::T,int::T,int::T]",
            "start([{2}plus(1)])                % [plus(1),plus(1)]",
            "start([{0}int::T])                 % [,]",
            // an instruction's zero span lays no slot on a lst either (Marko, 2026-11-20); its point has no slot to sit at
            "start([{0}plus(1)])                % [,]",
            "start([{0}plus(1),mult(2)])        % [mult(2)]",
            "start([{0}plus{3}(1)])             % [,]",
            "1-<[{0}plus(1),mult(2)]            % [2]",
            "[1,2,3]==[{0}plus(1),_]            % [1]",
            "{2,1,4,3}.order()==[{0}_]          % [,]",
            // extension on an argument list, a sugar's too
            "1.isa({4}int::T)                   % 1",
            "1?{4}int::T                        % 1",
            "3?{2}int::T                        % 3",
            "1.map({2}plus(1))                  % 2",
            // no axis: the span is the instruction's run
            "1.{2}plus(1)                       % 3",
            "1.branch({2}plus(1),mult(5))       % {3,5}",
            // no axis: a zero span is the identity, f to the zero, in a chain and in a branch's term alike
            "1.{0}plus(1).mult(5)               % 5",
            "1.branch({0}plus(1),mult(5))       % {1,5}",
    }, delimiter = '%')
    public void testASpanOnAnAxisIsExtension(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    /**
     * A LST IS THE AXIS A VALUE'S SPAN UNFOLDS ALONG (Marko, 2026-11-17): {@code [3{2}]} is {@code [3,3]}, so a
     * select's rules meet two indices and two lsts written one way and the other are equal, while a stream has no axis
     * and keeps the span curled, and a point stays mass at its index.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "[3{2}]                                       % [3,3]",
            "[1,3,2,3{2}]                                 % [1,3,2,3,3]",
            "[{2}1{3}]                                    % [{2}1,{2}1,{2}1]",
            "[3{2}]==[plus(1),plus(2)]                    % [4,5]",
            "[1,3,2,3,3].eq([1,3,2,3{2}])                 % true",
            "[{2}3]==[plus(1),plus(2)]                    % [{2}4]",
            "[{2}3,5]==[id(),plus(1)]                     % [{2}3,6]",
            "[3]==[branch(id(),id())]                     % [{2}3]",
            "[{2}1,{2}2].count()                          % 1",
            "({2}[1,2]).count()                           % 2",
            "{{2}1,{2}2}.count()                          % 4",
    }, delimiter = '%')
    public void testAValuesSpanLaysIndices(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }


    /**
     * A GENERIC list poly BINDS at construction: the poly written with a value is that value's shape position by
     * position, so {@code lst[A,B]::[1,'x']} binds {@code A=>int} and {@code B=>str} and the constructed value is
     * typed {@code lst[int,str]}. A variable used twice must resolve to ONE type ({@code lst[A,A]::[1,2]} is a pair of
     * ints) and two different types are a contradiction — failed by the same poly verdict, with the same message, as a
     * concrete poly that does not match ({@code lst[int,int]::[1,'x']}). Arity is the same rule taken literally: a
     * poly of length n is the type of an n-index value, so {@code lst[A,B]::[1]} has nothing to bind {@code B} from.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "lst[A,B]::[1,'x']                                      % lst[int,str]::[1,'x']",
            "lst[A,A]::[1,2]                                        % lst[int,int]::[1,2]",
            "lst[A,A]::[1,'x']                                      % <ERROR>",
            "lst[A,B]::[1]                                          % <ERROR>",
            "lst[int,int]::[1,'x']                                  % <ERROR>",
            "lst[int,int]::[1,2]                                    % lst[int,int]::[1,2]",
            // a run binds through the same walk the bracket is tested by (Lst.LstType.bindings): its variable takes
            // the type of every index its run claims, and the bound bracket keeps the run as written
            "lst[A{*}]::[1,2,3]                                     % lst[int{*}]::[1,2,3]",
            "lst[str,A{*}]::['x',1,2]                               % lst[str,int{*}]::['x',1,2]",
            // a lst is open past a bounded last index, so binding takes what the test takes (Marko, 2026-11-18)
            "lst[A,B]::[1,2,3]                                      % lst[int,int]::[1,2,3]",
    }, delimiter = '%', quoteCharacter = '~')
    public void testGenericPolyBinding(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    /**
     * A LST BRACKET'S VARIABLES BIND THROUGH THE WALK ITS TEST TAKES ({@link Lst.LstType#bindings}): each entry claims
     * a run of the value's indices the run funnel admits ({@link Type.Helper#runs}), every index is claimed, and a
     * variable's run holds one type, the type it binds. An earlier entry claims as much as agrees with its variable,
     * so {@code lst[A{*},B{?}]} over {@code [1,1,'hello']} leaves {@code 'hello'} to B. Each row: the value & the
     * bracket & its bindings, null when the value does not match (Marko, 2026-11-18, the first four rows).
     */
    @ParameterizedTest
    @CsvSource(value = {
            "[1,2,'hello']   & lst[A{2},str]::T      & [A=>int::T]",
            "[1,'hello']     & lst[A{2},str]::T      & null",
            "[1,1,'hello']   & lst[A{*},B{?}]::T     & [A=>int::T,B=>str::T]",
            "[1,1]           & lst[A{*},B{?}]::T     & [A=>int::T]",
            "[1,'x']         & lst[A,B]::T           & [A=>int::T,B=>str::T]",
            "[1,2]           & lst[A,A]::T           & [A=>int::T]",
            "[1,'x']         & lst[A,A]::T           & null",
            "[1]             & lst[A,B]::T           & null",
            "[1,2,3]         & lst[A,B]::T           & [A=>int::T,B=>int::T]", // open past B: lst[int,int], lst[int{2}]
            "['x',1,2]       & lst[str,A{*}]::T      & [A=>int::T]",
            "[1,'x']         & lst[A{*}]::T          & null",
            "[{2}1,3]        & lst[{*}A{*}]::T       & [A=>int::T]",
            "[{2}1,3]        & lst[A{*}]::T          & null",
            "[1,2,3]         & lst[int{*}]::T        & [=>]",
    }, delimiter = '&', quoteCharacter = '~')
    public void testGenericBindings(final String value, final String type, final String expected) {
        final Obj lst = ObjmtronSerializer.parse(value.trim());
        final Obj bracket = ObjmtronSerializer.parse(type.trim());
        final java.util.Map<studio.phaseshift.metatron.furi.fURI, studio.phaseshift.metatron.furi.fURI> bindings =
                Lst.LstType.bindings(lst, bracket.tid());
        if ("null".equals(expected.trim())) {
            assertNull(bindings, lst + " does not match " + bracket + ", so nothing binds");
            return;
        }
        assertNotNull(bindings, lst + " matches " + bracket);
        final java.util.Map<Obj, Obj> bound = new java.util.LinkedHashMap<>();
        bindings.forEach((variable, bindingType) -> bound.put(uri(variable), studio.phaseshift.metatron.isa.m.type.impl.MType.T(bindingType)));
        assertEquals(ObjmtronSerializer.parse(expected.trim()), rec(bound), lst + " over " + bracket);
    }

    /**
     * The bound poly is the VALUE's type, not just a verdict: {@code lst[A,B]::[1,'x']} carries
     * {@code lst[int,str]} as its tid, which is what {@code .type()} reports, while a concrete annotation keeps the
     * meaning it was written with.
     */
    @Test
    public void testGenericPolyBindingCarriesTheBoundPoly() {
        final Obj bound = mParser.m_obj().parse("lst[A,B]::[1,'x']").get();
        assertEquals(f("/m/lst[/m/int,/m/str]"), bound.tid());
        assertEquals(bound.tid(), bound.type().vidOrTid());
        final Obj concrete = mParser.m_obj().parse("lst[int,int]::[1,2]").get();
        assertEquals(f("/m/lst[/m/int,/m/int]"), concrete.tid());
        // .type() answers the same bound poly the tid carries
        AbstractMetatronTest.checkCodeParseApply(LOG, "lst[A,B]::[1,'x'].type()", "lst[int,str]::T");
    }

    /**
     * A LST POLY MISMATCH NAMES ITS INDICES — index by index, the way the rel path diff names its vertices. The
     * report pairs each element of the value with the type at its index ({@code ==>} where they agree, {@code X=>}
     * where they do not), so {@code lst[int,int,str]::[1,2,3.5]} names the third index and the value that failed
     * there.
     */
    @Training(instruction = "what does the mtron expression {{{code}}} evaluate to?", input = "{{{code}}}", output = "<ERROR>")
    @ParameterizedTest
    @CsvSource(value = {
            // code                              % the report pairs each index with its value
            "lst[int,int,str]::[1,2,3.5]         % [==>1,==>2,X=>3.5]",
            "lst[int,str]::[1,2]                 % [==>1,X=>2]",
    }, delimiter = '%')
    public void testPolyMismatchNamesTheFailingIndex(final String code, final String reported) {
        final Obj mismatch = ObjmtronSerializer.parse(code);
        assertTrue(mismatch.isFail(), "a lst poly mismatch must be reported: " + mismatch);
        assertTrue(mismatch.asFail().message().contains(reported.trim()),
                "each index is marked with its value: " + mismatch.asFail().message());
    }

    /**
     * A NAMED TENSOR TYPE READS ITS TID'S BRACKET like a base one, coefficients included, the left one being the mass
     * at an index (Marko, 2026-11-18). {@code pair -> lst[{2}person,str]::T@pair} is a two-index list whose index 0 holds
     * two people, so a pair
     * holding one person is rejected. The coefficient of a predicated entry ({@code pilgrim}) is judged the same way
     * as that of a bare one ({@code person}). Its seed sits beside {@link #testUpdate}'s one-time seed: each test
     * method keeps its own loaded flag, so neither seed suppresses the other.
     */
    @Training(instruction = "what does the mtron expression {{{code}}} evaluate to?", input = "{{{code}}}", output = "{{{expected}}}")
    @ParameterizedTest
    @TestData(value = {
            "person -> rec::T@person",
            "pilgrim -> rec::T[?[name=>str::T]]@pilgrim",
            "pair -> lst[{2}person,str]::T@pair",
    })
    @CsvSource(value = {
            "pair::[{person::[name=>'bob'],person::[name=>'kai']},'a']    % pair::[{person::[name=>'bob'],person::[name=>'kai']},'a']",
            "pair::[person::[name=>'bob'],'a']                            % <ERROR>",
            "pair::[{person::[name=>'bob'],person::[name=>'kai']},2]      % <ERROR>",
            "lst[{2}person,str]::[person::[name=>'bob'],'a']              % <ERROR>",
            "lst[{2}pilgrim,str]::[pilgrim::[name=>'bob'],'a']            % <ERROR>",
            "lst[pilgrim,str]::[pilgrim::[name=>'bob'],'a']               % lst[pilgrim,str]::[pilgrim::[name=>'bob'],'a']",
    }, delimiter = '%')
    public void testNamedTensorType(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    /**
     * A TENSOR'S INDICES, EDGE BY EDGE. Each index holds its own type in order; a missing index fails unless its entry
     * admits zero, and a present zero-admitting index is still typed; an index's left coefficient is the multiplicity at
     * that index (Marko, 2026-11-18); and a poly at an index is read by its base ({@code lst}, {@code rec}, {@code rel}), not unpacked.
     */
    @Training(instruction = "does the lst {{{value}}} match the tensor type {{{type}}}?", input = "{{{value}}}.isa({{{type}}}).count().gt(0)", output = "{{{matches}}}")
    @ParameterizedTest
    @CsvSource(value = {
            // value                      % type                          % matches
            // ---- each index holds its own type, in order ----
            "[1,'a',true]                 % lst[int,str,bool]::T          % true",
            "[1,true,'a']                 % lst[int,str,bool]::T          % false",
            // ---- a missing index fails unless its entry admits zero ----
            "[1,'a']                      % lst[int,str,bool]::T          % false",
            "[1,'a']                      % lst[int,str,{?}bool]::T       % true",
            "[1]                          % lst[int,{?}str,{?}bool]::T    % true",
            "[1,'a']                      % lst[int,{?}str,{?}bool]::T    % true",
            // ---- a present zero-admitting index is still typed ----
            "[1,2]                        % lst[int,{?}str,{?}bool]::T    % false",
            // ---- an index's left coefficient is the multiplicity at that index ----
            "[{1,2,3}]                    % lst[{3}int]::T                % true",
            "[{1,2,3}]                    % lst[{2,4}int]::T              % true",
            "[{1,2,3,4,5}]                % lst[{2,4}int]::T              % false",
            "[{1,2},{'a','b','c'}]        % lst[{+}int,{+}str]::T         % true",
            "[{1,2},'a']                  % lst[{?}int,str]::T            % false",
            // ---- a poly at an index is read by its base, not unpacked ----
            "[[1,2],'a']                  % lst[lst,str]::T               % true",
            "[[1,2],'a']                  % lst[int,str]::T               % false",
            "[[[1]]]                      % lst[lst]::T                   % true",
            "[[a=>1],(1=>2)]              % lst[rec,rel]::T               % true",
            "[(1=>2),[a=>1]]              % lst[rec,rel]::T               % false",
    }, delimiter = '%', quoteCharacter = '~')
    public void testTensorIndexEdges(final String value, final String type, final boolean matches) {
        AbstractMetatronTest.checkMatches(LOG, value, type, matches);
    }

    /**
     * A BRACKET READS ITS TYPES AS VALUES (Marko, 2026-11-18): "for lst[] the types in the brackets follow the same
     * coefficient convention as values. {4}int is an int with mass of {4}; int{4} is an int with span of {4}." A left
     * coefficient is the mass at one index and a right one a run of indices, so {@code [3{4}]}, which is
     * {@code [3,3,3,3]}, is a {@code lst[int{2},int,int]}, while {@code [{4}3]}, four ints at index 0, is not: mass never
     * spreads over extent, and indices never pool into mass. One run walk reads every bracket
     * ({@link Type.Helper#testTensor}), so a neighbour's coefficient never changes how an entry reads, an unbounded
     * trailing run claims the rest, and a run that lays nothing leaves the tail open.
     */
    @ParameterizedTest
    @CsvSource(value = {
            // value                      % type                              % matches
            // ---- spans add up along the axis ----
            "[3{4}]                       % lst[int{2},int,int]::T            % true",
            "[3{4}]                       % lst[int{2},int{2}]::T             % true",
            "[3{4}]                       % lst[int{2},int,int,int]::T        % false",
            "[3{4}]                       % lst[int{2}]::T                    % true",
            // ---- mass is not extent ----
            "[{4}3]                       % lst[int{2},int,int]::T            % false",
            "[{4}3]                       % lst[int{4}]::T                    % false",
            "[{4}3]                       % lst[{4}int]::T                    % true",
            "[{2}5]                       % lst[{2}int]::T                    % true",
            "[5,5]                        % lst[{2}int]::T                    % false",
            "[{2}3{2}]                    % lst[{2}int{2}]::T                 % true",
            "[{2}3{2}]                    % lst[int{2}]::T                    % false",
            "[{2}3{3}]                    % lst[{2}int{2},{2}int]::T          % true",
            "[{2}3,{3}3]                  % lst[{*}int{2}]::T                 % true",
            // ---- a neighbour's coefficient never changes how an entry reads ----
            "[3,3,'a','a']                % lst[int{2},str{2}]::T             % true",
            "[3,3,{2}'a']                 % lst[int{2},{2}str]::T             % true",
            "[3,3,'a','a']                % lst[int{2},{2}str]::T             % false",
            "[{2}3,'a','a']               % lst[int{2},str{2}]::T             % false",
            // ---- an unbounded trailing run claims the rest, and a run that lays nothing leaves the tail open ----
            "[3,'a']                      % lst[int{*}]::T                    % false",
            "['a',3,'b']                  % lst[str,int{*}]::T                % false",
            "['a','b']                    % lst[str,int{?}]::T                % true",
            "['a',1]                      % lst[str,int,bytes{*}]::T          % true",
            "['a',1,3]                    % lst[str,int,bytes{*}]::T          % false",
            "['a',1,0x01,3]               % lst[str,int,bytes{*}]::T          % false",
            "[1,2]                        % lst[int{2},{0}#{*}]::T            % true",
            "[1,2,3]                      % lst[int{2},{0}#{*}]::T            % false",
            // ---- a zero span lays no index, a zero mass holds no value ----
            "[3]                          % lst[int{0}]::T                    % true",
            "[3]                          % lst[{0}int]::T                    % false",
    }, delimiter = '%')
    public void testABracketReadsItsTypesAsValues(final String value, final String type, final boolean matches) {
        AbstractMetatronTest.checkMatches(LOG, value, type, matches);
    }

    /**
     * NAMED TENSORS NEST: depth comes from naming, since a bracket cannot hold a bracket. A {@code booking} is two
     * {@code ticket}s and an {@code itinerary} a {@code booking} with an optional {@code ticket}, so constructing a value
     * reads every level's indices in turn — untyped inner lsts are read as the tensor their index names — and reading
     * back down walks the same levels.
     */
    @Training(instruction = "what does the mtron expression {{{code}}} evaluate to?", input = "{{{code}}}", output = "{{{expected}}}")
    @ParameterizedTest
    @TestData(value = {
            "ticket -> lst[int,str]::T@ticket",
            "booking -> lst[ticket,ticket]::T@booking",
            "itinerary -> lst[booking,{?}ticket]::T@itinerary",
    })
    @CsvSource(value = {
            // ---- a named tensor at an index: its own indices are read in turn ----
            "booking::[[1,'a'],[2,'b']]                        % booking::[[1,'a'],[2,'b']]",
            "booking::[[1,'a'],[2,3]]                          % <ERROR>",
            "booking::[[1,'a']]                                % <ERROR>",
            "booking::[[1,'a'],[2,'b'],[3,'c']]                % booking::[[1,'a'],[2,'b'],[3,'c']]",
            // ---- three levels: an itinerary holds a booking, which holds tickets ----
            "itinerary::[[[1,'a'],[2,'b']]]                    % itinerary::[[[1,'a'],[2,'b']]]",
            "itinerary::[[[1,'a'],[2,'b']],[3,'c']]            % itinerary::[[[1,'a'],[2,'b']],[3,'c']]",
            "itinerary::[[[1,'a'],[2,'b']],[3,4]]              % <ERROR>",
            "itinerary::[[[1,'a'],['b',2]]]                    % <ERROR>",
            // ---- reading back down through the levels ----
            "itinerary::[[[1,'a'],[2,'b']],[3,'c']]>>0>>1>>1   % 'b'",
            "itinerary::[[[1,'a'],[2,'b']],[3,'c']]>>1>>0      % 3",
    }, delimiter = '%', quoteCharacter = '~')
    public void testNestedTensorTypes(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    /**
     * INSTRUCTIONS ON A TENSOR TYPE, OUTER AND INNER. An instruction whose domain is a named tensor applies to any lst
     * whose indices are that tensor's, typed or not, and to nothing else; the outer tensor's instruction hands back an
     * inner lst, which takes the inner tensor's instructions in turn; and the outer tensor is not the inner one.
     */
    @Training(instruction = "what does the mtron expression {{{code}}} evaluate to?", input = "{{{code}}}", output = "{{{expected}}}")
    @ParameterizedTest
    @TestData(value = {
            "ticket -> lst[int,str]::T@ticket",
            "booking -> lst[ticket,ticket]::T@booking",
            "ticket_name -> |inst?<=ticket(){ >>1 }",
            "booking_first -> |inst?<=booking(){ >>0 }",
    })
    @CsvSource(value = {
            "ticket::[7,'kai'].ticket_name()                          % 'kai'",
            "[7,'kai'].ticket_name()                                  % 'kai'",
            "[7,8].ticket_name()                                      % 8",
            "booking::[[1,'a'],[2,'b']].booking_first()               % [1,'a']",
            "booking::[[1,'a'],[2,'b']].booking_first().ticket_name() % 'a'",
            "booking::[[1,'a'],[2,'b']].ticket_name()                 % <ERROR>",
    }, delimiter = '%', quoteCharacter = '~')
    public void testTensorInstructions(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }


    @Training.SkipTraining(reason = "Java API assertion on Lst.at(key, value, Poly.MUTABLE): it checks reference identity (assertSame) and in-place mutation of the source lst, which no mtron expression states")
    @ParameterizedTest
    @CsvSource(value = {
            // MUTABLE set: should mutate original in-place
            "[1,2,3]   | 1   | 99  | 99 | 3   | true",     // middle element
            "[1,2,3]   | 0   | 99  | 99 | 3   | true",     // first element
            "[1,2,3]   | -1  | 99  | 99 | 3   | true",     // negative index (last)
            "[10,20]   | 0   | 5   | 5  | 2   | true",     // singleton result
    }, delimiter = '|')
    public void testMutableSet(final String lstStr, final int key, final int value,
                               final int expectedVal, final int expectedCount,
                               final boolean expectSame) {
        final Obj parsed = mParser.m_obj().parse(lstStr).get();
        assertTrue(parsed.isLst());
        final Lst original = parsed.asLst();
        final Lst result = original.at(jnt(key), jnt(value), Poly.MUTABLE);
        if (expectSame)
            assertSame(original, result, "MUTABLE should return same reference");
        assertEquals(jnt(expectedVal), original.at(jnt(key)), "MUTABLE should mutate original at key");
        assertEquals(expectedCount, original.count());
    }

    @Training.SkipTraining(reason = "Java API assertion on Lst.at(key, noobj(), Poly.MUTABLE): it checks that the source lst is mutated in place and then reads the shifted remaining elements, not a single evaluated value")
    @ParameterizedTest
    @CsvSource(value = {
            // MUTABLE delete (value = noobj)
            "[10,20,30] | 1   | 2   | 10 | 30",     // delete middle
            "[10,20,30] | 0   | 2   | 20 | 30",     // delete first
            "[10,20,30] | -1  | 2   | 10 | 20",     // delete last via negative
            "[42]       | 0   | 0   |    |   ",     // delete only → empty
    }, delimiter = '|')
    public void testMutableDelete(final String lstStr, final int key, final int expectedCount,
                                  final String remaining0, final String remaining1) {
        final Obj parsed = mParser.m_obj().parse(lstStr).get();
        assertTrue(parsed.isLst());
        final Lst original = parsed.asLst();
        final Lst result = original.at(jnt(key), noobj(), Poly.MUTABLE);
        assertSame(original, result, "MUTABLE delete should return same reference");
        assertEquals(expectedCount, original.count());
        if (expectedCount > 0)
            assertTrue(mParser.m_obj().parse(remaining0).get().equals(original.at(jnt(0))));
        if (expectedCount > 1)
            assertTrue(mParser.m_obj().parse(remaining1).get().equals(original.at(jnt(1))));
    }

    @Training.SkipTraining(reason = "Java API assertion on Lst.at(key, value, Poly.IMMUTABLE): it checks that the original lst is untouched and the clone (a distinct reference, assertNotSame) holds the new value")
    @ParameterizedTest
    @CsvSource(value = {
            // IMMUTABLE set: should NOT mutate original
            "[1,2,3]   | 1   | 99  | 3   | 99",     // original unchanged, clone gets value
            "[1,2,3]   | -1  | 99  | 3   | 99",     // negative index
    }, delimiter = '|')
    public void testImmutableSet(final String lstStr, final int key, final int value,
                                 final int expectedOrigCount, final int expectedCloneVal) {
        final Obj parsed = mParser.m_obj().parse(lstStr).get();
        assertTrue(parsed.isLst());
        final Lst original = parsed.asLst();
        final Lst clone = original.at(jnt(key), jnt(value), Poly.IMMUTABLE);
        assertNotSame(original, clone, "IMMUTABLE should return new reference");
        assertEquals(expectedOrigCount, original.count());
        assertEquals(jnt(expectedCloneVal), clone.at(jnt(key)), "IMMUTABLE clone should have new value");
    }

    @Training.SkipTraining(reason = "Java API assertion on Lst.at(key, noobj(), Poly.IMMUTABLE): it asserts both counts and reads the clone's remaining elements element by element, not one evaluated value")
    @ParameterizedTest
    @CsvSource(value = {
            // IMMUTABLE delete: should NOT mutate original
            "[10,20,30] | 1   | 3   | 2   | 30",     // delete middle
            "[10,20,30] | -1  | 3   | 2   | 20",     // delete last via negative
    }, delimiter = '|')
    public void testImmutableDelete(final String lstStr, final int key,
                                    final int expectedOrigCount, final int expectedCloneCount,
                                    final String cloneIdx1) {
        final Obj parsed = mParser.m_obj().parse(lstStr).get();
        assertTrue(parsed.isLst());
        final Lst original = parsed.asLst();
        final Lst clone = original.at(jnt(key), noobj(), Poly.IMMUTABLE);
        assertNotSame(original, clone, "IMMUTABLE delete should return new reference");
        assertEquals(expectedOrigCount, original.count());
        assertEquals(expectedCloneCount, clone.count());
        if (expectedCloneCount > 1)
            assertTrue(mParser.m_obj().parse(cloneIdx1).get().equals(clone.at(jnt(1))));
    }

    /**
     * at/atDirect correlate on lists (mirrors RecTest#testAtDirectReadsADeepKeyedRecByItsPath): when a list element is
     * itself a deep-keyed rec, its full uri path 0/usr/marko/Echo must surface the raw !@ pointer under atDirect, the
     * atToggle doAuto toggle propagating through the whole path, not just the first segment. The element's leaf name
     * alone, 0/Echo, names no key, as on the nested form. (Lst uri access wraps its results in an objs, so check by
     * streaming.)
     */
    @Test
    public void testAtDirectDeepPathRespectsRawToggle() {
        final Obj ptr = auto_from_(f("local:src/a_main")).tryToInst();
        final Lst l = lst(rec(uri("usr/marko/Echo"), ptr));
        final Obj direct = l.atDirect(uri("0/usr/marko/Echo"));
        LOG.info("atDirect(uri('0/usr/marko/Echo')) -> %s", direct);
        final boolean rawPointer = direct.stream()
                .map(Obj.Helper::getAutoPointer).anyMatch(Optional::isPresent);
        assertTrue(rawPointer,
                "atDirect('0/usr/marko/Echo') should surface the raw !@ inst of the deep element, got: " + direct);
        assertTrue(l.atDirect(uri("0/Echo")).stream().noneMatch(obj -> Obj.Helper.getAutoPointer(obj).isPresent()),
                "atDirect('0/Echo') names no key of the deep element, got: " + l.atDirect(uri("0/Echo")));
    }

    /**
     * at()/atDirect() crossing the lst -> rec boundary and back: a lst whose element is
     * a rec whose value holds deeper structure (metatron tree layout).  Uri paths with
     * multiple segments may surface results wrapped in an objs, so assert over stream.
     */
    @Test
    public void testAtCrossesLstRecBoundary() {
        final Lst l = lst(rec(uri("x"), jnt(41)), jnt(7));
        // lst -> rec leaf
        assertTrue(l.at(uri("0/x")).stream().anyMatch(o -> o.equals(jnt(41))),
                "at(0/x) should reach the rec leaf, got: " + l.at(uri("0/x")));
        assertTrue(l.atDirect(uri("0/x")).stream().anyMatch(o -> o.equals(jnt(41))),
                "atDirect(0/x) should reach the raw rec leaf, got: " + l.atDirect(uri("0/x")));
        // lst -> rec -> lst deeper (rec value is another lst)
        final Lst nested = lst(rec(uri("m"), lst(jnt(9), jnt(8))), jnt(7));
        assertTrue(nested.at(uri("0/m/1")).stream().anyMatch(o -> o.equals(jnt(8))),
                "at(0/m/1) should cross lst -> rec -> lst, got: " + nested.at(uri("0/m/1")));
        // plain list element beside the rec
        assertTrue(l.at(uri("1")).stream().anyMatch(o -> o.equals(jnt(7))),
                "at(1) should reach the plain element, got: " + l.at(uri("1")));
    }
}
