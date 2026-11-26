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
import studio.phaseshift.metatron.TestData;
import studio.phaseshift.metatron.Training;
import studio.phaseshift.metatron.algebra.AbstractAlgebraTest;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.URI_TID;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class UriTest extends AbstractAlgebraTest {

    @Override
    protected Type carrier() {
        return URI_TYPE;
    }

    @Override
    protected Obj sample() {
        return uri(f("/a/b"));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "bool::abc/def                                                | <ERROR>",
            "/m/bool::abc/def                                             | <ERROR>",
            "bytes::<abc/def>                                             | <ERROR>",
            "/m/bytes::<abc/def>                                          | <ERROR>",
            "int::<abc/def>                                               | <ERROR>",
            "/m/int::<abc/def>                                            | <ERROR>",
            "real::<abc/def>                                              | <ERROR>",
            "/m/real::<abc/def>                                           | <ERROR>",
            "str::<abc/def>                                               | <ERROR>",
            "/m/str::<abc/def>                                            | <ERROR>",
            "lst::<abc/def>                                               | <ERROR>",
            "/m/lst::<abc/def>                                            | <ERROR>",
            "lst::<abc/def>                                               | <ERROR>",
            "/m/lst::<abc/def>                                            | <ERROR>",
            "inst::<abc/def>                                              | <ERROR>",
            "/m/inst::<abc/def>                                           | <ERROR>",
            //  "code::<abc/def>                                          | <ERROR>",
            "uri::<http://webpage.com>                                    | <http://webpage.com>",
            "uri::<http://webpage.com>.type()                             | start(uri::T[])",
            "<http://webpage.com>.type()                                  | start(uri::T[])",
            "\"http://webpage.com\".type()                                | start(str::T[])",
            //"a/b.plus(c/d)                                              | {a/b,c/d}",
            // "a/b.plus(noobj)                                              | a/b",
            "a/b/c.plus(a/c/c)                                            | a/{b,c}/c",
            "a/{b,d}/c.plus(a/c/c)                                        | a/{b,c,d}/c",
            "a/{b,d}/c.plus(a/e/c)                                        | a/{b,d,e}/c",
            "a/{b,d,e}/c.plus(a/f/c)                                      | a/{b,d,e,f}/c",
            "a/{x,y,z}/p/q.plus(a/w/p/q)                                  | a/{w,x,y,z}/p/q",
            "a/{m,n}/c/d.plus(a/o/c/d)                                    | a/{m,n,o}/c/d",
            "a/b/c/d.plus(a/e/c/d)                                        | a/{b,e}/c/d",
            "a/c/c.plus(a/{b,d}/c)                                        | a/{b,c,d}/c",
            // a point the terms do not share stays in the branch, as each term's prefix
            "a/b/c{2,3}.plus(a/d/c{-3,-2})                                | a/{{2,3}b,{-3,-2}d}/c",
            "a/b/c{2}.plus(a/d/c{3})                                      | a/{{2}b,{3}d}/c",
            "a/{b,d}/c{2}.plus(a/e/c{3})                                  | a/{{2}b,{2}d,{3}e}/c",
            "a/e/c{2}.plus(a/{b,d}/c{3})                                  | a/{{3}b,{3}d,{2}e}/c",
            // a point every term shares stays in the value, while the uri's own coefficient floats over the sum
            "a/b/c{2}.plus(a/d/c{2})                                      | a/{b,d}/c{2}",
            "{2}a/b/c.plus(a/b/c)                                         | {2}a/b/c{2}",
            "a/b/c/d.plus(a/e/f/g)                                        | a/{b/c/d,e/f/g}",
            "<a/{b,d}/c>.mult(d/e).plus(a/c/c/d/e)                         | a/{b,c,d}/c/d/e",
            "a/b.mult(c/d)                                                | a/b/c/d",
            "a/b.mult(noobj)                                              | noobj",
            "a/b.one()                                                    | <>",
            "a.mult(<../b>)                                               | b",
            "a.mult(<../b/c>)                                             | b/c",
            "a.mult(<../../b>)                                            | <../b>"
    }, delimiter = '|')
    public void testCode(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "a/b/c.split(/)                          % [a,b,c]",
            // the regex must reach split as a uri (mtron has no f() to build one)
            "a/b/c.split('(a+)+$'.as(uri::T))        % <ERROR>",
            "a/b/c.split('(x?)*y'.as(uri::T))        % <ERROR>",
            "a/b/c.split('(.*)*'.as(uri::T))         % <ERROR>",
    }, delimiter = '%', quoteCharacter = '~')
    public void testSplitRegexGuard(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            // has and the select projection's q rules take a regex too, so they refuse the same shape split does
            "a/b/c.has('(a+)+$')                                        % <ERROR>",
            "<http://webpage.com/a?debug=true>.select([q=>['(d+)+'=>_]]) % <ERROR>",
            "a/b/c.has('b/c$')                                          % a/b/c",
            "a/b/c.has('^b')                                            % noobj",
            "<http://webpage.com/a?debug=true>.select([q=>['de.*'=>_]]) % <http://webpage.com/a?debug=true>",
    }, delimiter = '%', quoteCharacter = '~')
    public void testRegexGuard(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "a.pow(0)                         % <.>",
            "/a.pow(1)                        % /a",
            "/a/.pow(1)                       % /a/",
            "/a/.pow(2)                       % /a/a/",
            "a.pow(2)                         % a/a",
            "a.pow(3)                         % a/a/a",
            "a/b.pow(2)                       % a/b/a/b",
            "a/b.pow(3)                       % a/b/a/b/a/b",
            "a/b.pow(3)                       % a/b/a/b/a/b",
            "a/b/c.pow(2)                     % a/b/c/a/b/c",
            "a/b/c.pow(3)                     % a/b/c/a/b/c/a/b/c",
            "a/b/c/.pow(3)                    % a/b/c/a/b/c/a/b/c/",
            "/a/b/c/.pow(3)                   % /a/b/c/a/b/c/a/b/c/",
            "a/b/c/d.pow(2)                   % a/b/c/d/a/b/c/d",
            "a/b/c/d.pow(3)                   % a/b/c/d/a/b/c/d/a/b/c/d",
            "<a/b/../c/d>.pow(3)              % a/c/d/a/c/d/a/c/d",
    }, delimiter = '%')
    public void testMath(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    /**
     * THE URI RING'S ZERO IS {@code {1}noobj}, the uri of a hole (Marko, 2026-11-25; Uri.ZERO), as lst::T's is
     * {@code [noobj]}: it adds nothing on either side, a sum whose terms cancel answers it, it absorbs an extension on
     * either side, and it is its own negation. It is no noobj, so it flows as a left operand, where a zero address
     * ({@code {0}<>}) flows nothing. A negation counts against a term's own point, so a term and its negation cancel in
     * either order, and minus is the sum with the negated argument. A product distributes over a sum, extension keeping
     * a branch one segment
     */
    @ParameterizedTest
    @CsvSource(value = {
            "<a/b>.zero()                                % <noobj>",
            "<noobj>.plus(<a/b>)                         % <a/b>",
            "<a/b>.plus(<noobj>)                         % <a/b>",
            "<a/b>.mult(<noobj>)                         % <noobj>",
            "<noobj>.mult(<a/b>)                         % <noobj>",
            "<a/b>.plus(<a/b>.neg())                     % <noobj>",
            "<a/b>.neg().plus(<a/b>)                     % <noobj>",
            "<a/b>.minus(<a/b>)                          % <noobj>",
            "<noobj>.minus(<a/b>)                        % <a/b{-1}>",
            "<a/b>.plus(<c/d>).minus(<c/d>)              % <a/b>",
            "<a/b>.neg().neg()                           % <a/b>",
            "<noobj>.neg()                               % <noobj>",
            // a negative point on any segment negates its term (Marko: /a/b/c + /a/{-1}b/c), so the ring has its
            // inverses in the points, and the cancelled sum still flows: on a zero address, noobj, the /x would be lost.
            // neg answers the sum's normal form, the point written last: /a/{-1}b/c is another spelling of the same
            // term, which a sum reads alike and equality keeps apart until resolve() (Marko, 2026-11-08)
            "</a/b/c>.plus(</a/{-1}b/c>)                 % <noobj>",
            "</a/b/c>.plus(</a/{-1}b/c>).plus(</x>)      % </x>",
            "</a/b/c>.neg()                              % </a/b/c{-1}>",
            "</a/b/c>.neg().plus(</a/{-1}b/c>)           % </a/b/c{-2}>",
            // the obj's coefficient is not the address's point (Marko: {-1}<a/b/c> is different than <{-1}a/b/c>):
            // neg negates the point, while a coefficient on the lhs floats over the sum, so it cancels nothing
            "<a/b/c>.plus(<{-1}a/b/c>)                   % <noobj>",
            "<a/b/c>.neg().plus(<{-1}a/b/c>)             % <a/b/c{-2}>",
            "{-1}<a/b/c>.plus(<a/b/c>)                   % {-1}<a/b/c{2}>",
            // a product carries the argument's point (fURI's product), so it distributes over a sum whose terms
            // collapsed into one point, and x(-y) is -(xy)
            "<a>.mult(<b>.neg())                         % <a/b{-1}>",
            "<a>.neg().mult(<b>)                         % <a/b{-1}>",
            "<a>.mult(<b>.plus(<b>))                     % <a/b{2}>",
            "<a>.mult(<b>).plus(<a>.mult(<b>))           % <a/b{2}>",
            "<a/b{2}>.mult(<a/b{2}>)                     % <a/b/a/b{4}>",
            // only the one-segment uri is the hole: a path through a segment named noobj is an address, kept by the
            // unity and the negation as the rewriter keeps it; a hole inside a branch drops when the branch is summed
            "<a/noobj>.mult(<>)                          % <a/noobj>",
            "<a/noobj>.neg().neg()                       % <a/noobj>",
            "<a/noobj>.plus(<b>)                         % <{a/noobj,b}>",
            "<{a,noobj}>.plus(<b>)                       % <{a,b}>",
            "<a>.minus(<{a,noobj}>)                      % <noobj>",
            // the sum and the product of a stream know the zero, as plus and mult do
            "{<a/b>,<a/b{-1}>}.sum()                     % <noobj>",
            "{<a/b>,<noobj>}.sum()                       % <a/b>",
            "{<a/b>,<noobj>}.prod()                      % <noobj>",
            // plus and minus are each other's converse
            "<a/b>.plus(<c>).{-1}plus(<c>)               % <a/b>",
            "<a/b>.{-1}minus(<c>)                        % <{a/b,c}>",
            "<a/b>.mult(<c/d>.plus(<a/b>))               % <a/b/{a/b,c/d}>",
            "<a/b>.mult(<c/d>).plus(<a/b>.mult(<a/b>))   % <a/b/{a/b,c/d}>",
    }, delimiter = '%')
    public void testTheZeroIsTheUriOfAHole(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    /**
     * AT THE ZERO AN OPERAND KEEPS ITS VID AND TYPE, as every other answer of the ring's operations does, and as a
     * lst's do: the identity answers the argument's value under this uri's vid, and a cancelled sum and an absorbed
     * product the hole under it
     */
    @Test
    public void testTheZeroKeepsTheOperandsVid() {
        final fURI v = f("/v");
        AbstractMetatronTest.checkEquality(LOG, uri(f("c")).vid(v), Uri.ZERO.vid(v).plus(uri(f("c"))), true);
        AbstractMetatronTest.checkEquality(LOG, Uri.ZERO.vid(v), uri(f("a/b")).vid(v).plus(uri(f("a/b")).neg()), true);
        AbstractMetatronTest.checkEquality(LOG, Uri.ZERO.vid(v), uri(f("a/b")).vid(v).mult(Uri.ZERO), true);
        AbstractMetatronTest.checkEquality(LOG, Uri.ZERO.vid(v), Uri.ZERO.vid(v).neg(), true);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "a/b/c.-</                        % [a,b,c]",
            "ab/c.-</                         % [ab,c]",
            "abc.-</                          % [abc]",
            // no lhs: -</abc is split(/abc) applied to nothing, and nothing splits to nothing
            "-</abc                           % noobj",
            "<http://www.com/a/b/c>.-</       % [http:,<>,<www.com>,a,b,c]",
            "<////>.-</                       % [<>]",
            "<////a>.-</                      % [<>,<>,<>,<>,a]",
    }, delimiter = '%')
    public void testSplit(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @Training.SkipTraining(reason = "the rows dereference *test and *b, values bound by @TestData; the extractor splices @TestData entries newline-joined with no statement separator, so the write-then-read setup cannot be composed into one parseable input")
    @ParameterizedTest
    @TestData({
            "test -> <http://www.marko.com:90/a/b/c?w=abc&x=1&y=2&z=!*test>",
            "b -> 42"})
    @CsvSource(value = {
            "*test.as(rec::T)>>scheme                   % http",
            "*test.as(rec::T)>>authority                % <www.marko.com:90>",
            "*test.as(rec::T)>>host                      % <www.marko.com>",
            "*test.as(rec::T)>>sub                       % <www>",
            "*test.as(rec::T)>>{sub,port}                % {<www>,90}",
            "*test.as(rec::T)>>port                      % 90",
            //"*test.as(rec::T).>>path>-.as(uri::T).prod()          % </a/b/c>",
            "*test.as(rec::T)>>q                         % [w=><abc>,x=>1,y=>2,z=>!*test]",
            "*test.as(rec::T).>>q>>w                     % <abc>",
            "*test.as(rec::T).>>q>>x                     % 1",
            "*test.as(rec::T)>>q>>y                      % 2",
            "*test.as(rec::T).>>q>>z                     % *test",
            "*test.as(rec::T).>>path>>2.*(_)             % 42",
            "*test.as(rec::T)>>path>>1                   % a",
            "*test.as(rec::T)>>path>>{2,3}               % {b,c}",
            "*test.as(rec::T)>>path>>(-1)                % c",
            "*test.as(rec::T)>>path>>{-1,-3}             % {c,a}",
            "*test.as(rec::T)>>path>>{-2,-3}             % {b,a}",
            "*test.as(rec::T)>>path>>{-2,-5}             % b",
            "*test.as(rec::T)>>path>>{-100,100}          % noobj",
            //    "*test.as(rec::T)>>=[q=>none].as?uri<=rec(uri::T)                               % <http://www.marko.com:90/a/b/c>",
            //    "*test.as(rec::T)==[q=>none,_=>_].as?uri<=rec(uri::T)                           % <http://www.marko.com:90/a/b/c>",
            //"*test.as(rec::T)>>=[scheme=>none,q=>none,authority=>none].as?uri<=rec(uri::T)  % a/b/c"
    }, delimiter = '%')
    public void testGet(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    /**
     * THE CONVERSE AT RUN TIME IS {@code pow(-1)} (Marko, 2026-11-25): the span a literal's {@code <w>{-1}} realizes, so
     * a computed uri reverses its path the same way, and the bolt-on {@code reverse()} is retired.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "/a/b/c.pow(-1)                   % c/b/a/",
            "aaa/bbb/ccc.pow(-1)              % ccc/bbb/aaa",
            "<http://m.com/a/b/c>.pow(-1)     % <http://m.com/c/b/a>",
            "a.pow(-1)                        % a",
            "a/b.pow(-1)                      % b/a",
            "/a.pow(-1)                       % a/",
            "a/b.reverse()                    % <ERROR>",
    }, delimiter = '%')
    public void testReverse(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    /**
     * A SPAN AFTER THE BRACKETS IS A POWER (Marko, 2026-11-24): {@code <w>{n}} is the path concatenated n times, the
     * unit {@code <>} at zero, and a negative span its converse (ring-theoretic-distributed-structures, Definition 3),
     * which on paths with no host is an involution that reverses a product and distributes over a sum (its Claim 3).
     * It reverses the path alone (Marko, 2026-11-24), so a scheme and a host stay and a uri with a host keeps its root
     * in front, and a parent step the fold keeps has none. A uri retyped onto an exact span, as an answer is when it
     * takes its lhs's ({@code 'a.b'{3}.as(uri::T)}), holds the power the same way. {@code pow(n)} is the span at run
     * time, so {@code {-1}} is {@code pow(-1)} and {@code {n}} is {@code pow(n)}. The brackets mark the locus: the obj's point
     * before them is on the run ({@code c·aⁿ}), a point inside runs with the path ({@code (ca)ⁿ}, span-and-point
     * Theorems 7 and 13), and a converse is no inverse.
     */
    @ParameterizedTest
    @CsvSource(value = {
            // a power: n copies, the unit at zero
            "<a/b/c>{1}                  % a/b/c",
            "<a/b/c>{2}                  % a/b/c/a/b/c",
            "<a/b/c>{0}                  % <>",
            "</a/>{2}                    % /a/a/",
            // the converse: the path reversed, an absolute path's root moved to its far end, and its powers
            "<a/b/c>{-1}                 % c/b/a",
            "</a/b/c>{-1}                % c/b/a/",
            "<a/b/c>{-2}                 % c/b/a/c/b/a",
            "<a/{b,c}>{2}                % <a/{b,c}/a/{b,c}>",
            // the path alone reverses: a scheme and an authority stay, and a host keeps its root in front
            "<http://m.com/a/b/c>{-1}    % <http://m.com/c/b/a>",
            "<http://m.com/c/b/a>{-1}    % <http://m.com/a/b/c>",
            "<http://m.com/a/b/c>{-1}.pow(-1) % <http://m.com/a/b/c>",
            "<http://m.com/a/b/c/>{-1}   % <http://m.com/c/b/a/>",
            "<http://m.com/>{-1}         % <http://m.com/>",
            "<test:a/b/c>{-1}            % <test:c/b/a>",
            // an empty authority is an authority: file:/// keeps its root in front, as a host does
            "<file:///a/b/c>{-1}         % <file:///c/b/a>",
            "<file:///a/b/c/>{-1}        % <file:///c/b/a/>",
            // no converse: a parent step the fold keeps names no step to undo, and a span with no positive counterpart
            "<../a>.count()              % 1",
            "<../a>{-1}                  % <ERROR>",
            "<../a>.pow(-1)              % <ERROR>",
            // pow(n) declares no involution, so no rewrite cancels a pair before it runs: a kept .. has no converse,
            // reversed twice or once
            "<../a>.pow(-1).pow(-1)      % <ERROR>",
            "<a>{-9223372036854775808}   % <ERROR>",
            // the span at run time agrees: pow(n) is {n}, so pow(-1) is the converse
            "a/b/c.pow(-1).pow(-1)       % a/b/c",
            "<a/b/c>{-1}.pow(-1)         % a/b/c",
            "</a/b/c>{-1}.pow(-1)        % /a/b/c",
            "a/b/c.pow(2)                % a/b/c/a/b/c",
            "a/b/c.pow(-1)               % c/b/a",
            "a/b/c.pow(-2)               % c/b/a/c/b/a",
            // the converse reads the value, not the spelling (dot segments fold first), and it is additive
            "<a/x/../b/c>{-1}            % c/b/a",
            "<a/x/../b/c>.pow(-1)        % c/b/a",
            "<a/{b/c,d}>{-1}             % <{c/b,d}/a>",
            "<a/{x/../b,c}>{-1}          % <{b,c}/a>",
            "<a/{./b,c}>{-1}             % <{b,c}/a>",
            // a span reads only after the brackets: a bare uri keeps its trailing brace as its point
            "a/b{2}{3}                   % <ERROR>",
            // the converse reverses a product, and it is no inverse
            "<a/b/c/d>{-1}               % d/c/b/a",
            "<c/d>{-1}.mult(<a/b>{-1})   % d/c/b/a",
            "<a/b>.mult(<a/b>{-1})       % a/b/b/a",
            // the locus: the obj's point before the brackets is on the run, a point inside runs with the path
            "{2}<a/b>{-1}                % {2}b/a",
            "{2}<a/b>{2}                 % {2}a/b/a/b",
            "<{2}a/b>{2}                 % <{2}a/b/{2}a/b>",
            "{2}<a/b>{-2}                % {2}b/a/b/a",
            "<{2}a/b>{-2}                % <b/a/b/a{4}>",
            "<a/b{2}>{2}                 % <a/b/a/b{4}>",
            "<a/b{2}>{-1}                % <b/a{2}>",
            "<a/b{2}>{-2}                % <b/a/b/a{4}>",
            // inside the brackets a trailing {n} is still the path's own point
            "<a/b{2}>                    % <a/b{2}>",
            // a span a uri takes from its lhs is its power too, the lhs's point on the run
            "\"a.b\"{3}.as(uri::T)       % <a.b/a.b/a.b>",
            "\"ab\"{3}.as(uri::T)        % ab/ab/ab",
            "{2}\"ab\"{3}.as(uri::T)     % {2}ab/ab/ab",
            "5{3}.tid().eq(5.tid().pow(3)) % true",
    }, delimiter = '%')
    public void testASpanAfterTheBracketsIsAPower(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    /**
     * A URI RETYPED ONTO A SPAN HOLDS ITS POWER (Marko, 2026-11-24): retyped onto an exact span, as an answer is when
     * it takes its lhs's, a uri holds its path's power and its tid no span, so the span is raised once though a clone
     * sets its fields twice, and a negative span, which no value literal can carry onto a uri, is its converse.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "a/b              % 2  % a/b/a/b",
            "a/b/c            % -1 % c/b/a",
            "/a/b             % -1 % b/a/",
            "http://m.com/a/b % -1 % http://m.com/b/a",
            "a/b              % 1  % a/b",
    }, delimiter = '%')
    public void testAUriMadeWithASpanHoldsItsPower(final String path, final long span, final String power) {
        AbstractMetatronTest.checkEquality(LOG, uri(path).tid(URI_TID.b(cInt.of(span))), uri(power), true);
    }


    @ParameterizedTest
    @CsvSource(value = {
            "ab/cd.has('ab.*')                                                             % ab/cd",
            "ab/cd.has('bb')                                                               % noobj",
            "{abc/d,aaa}.has('a\\.')                                                       %  noobj",
            "{abc3d,aaa}.has('a.*')                                                        % {abc3d,aaa}",
            "{abc3d,aaa}.has('a(b)?(a|c).?')                                               % {abc3d,aaa}",
            "{abc3d,aaa}.has('b.*')                                                        % {abc3d}",
            "{abc3d,aaa}.has('c.*')                                                        % {abc3d}",
            "{abc3d,aaa}.has('d.*')                                                        % abc3d",
            "{abc3d,aaa}.has('d.?')                                                        % abc3d",
            "{abc3d,aaa}.has('e.*')                                                        % noobj"
            // "{'abc3d','aaa'}.where(not(has('e.')))                                          % {\"abc3d\",\"aaa\"}",
            // "{'abc3d','aaa'}.where(has('e.'))                                               % noobj",
    }, delimiter = '%')
    public void testHasInst(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }


    @ParameterizedTest
    @CsvSource(value = {
            "<a://b.com:123/c/d?x=1&y=2>                               % <a://b.com:123/c/d?x=1&y=2>",
            "<a://b.com:123/c/d?x=1&y=2>.as(rec::T)>>scheme            % a",
            "<a://b.com:123/c/d?x=1&y=2>.scheme(abc)                   % <abc://b.com:123/c/d?x=1&y=2>",
            "<a://b.com:123/c/d?x=1&y=2>.as(rec::T)>>port              % 123",
            "<a://b.com:123/c/d?x=1&y=2>.port(666)                     % <a://b.com:666/c/d?x=1&y=2>",
            "<a://b.com:123/c/d?x=1&y=2>.as(rec::T)>>host              % <b.com>",
            "<a://b.com:123/c/d?x=1&y=2>.host(<abc.org>)               % <a://abc.org:123/c/d?x=1&y=2>",
            // an empty authority's host is the empty uri in both views; a path with no authority has none
            "<file:///a/b>.as(rec::T)>>host                            % <>",
            "<file:///a/b>.host()                                      % <>",
            "<a/b>.host()                                              % noobj",
            "<a://b.com:123/c/d?x=1&y=2>.as(rec::T)>>q                 % [x=>1,y=>2]",
            "{23,56}<a://b.com:123/c/d?x=1&y=2>.as(rec::T)>>q          % {23,56}[x=>1,y=>2]",
            // "<a://b.com:123/c/d?x=1&y=2>.query(x=3)                 % <a://b.com:123/c/d?x=3&y=2>",
    }, delimiter = '%')
    public void testAsRec(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "<1>                                                        % <1>",
            "<1>.as(int::T)                                             % int::1",
            "<3>.as(int::T).plus(<6>.as(int::T))                        % int::9",
            "<3a>.as(int::T)                                            % <ERROR>",
    }, delimiter = '%')
    public void testAsInt(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "a/b/c.count()                                              % 1",
            "a.count()                                                  % 1",
            "/a/b.count()                                               % 1",
            //  "<http://example.com/a/b/c>.count()                         % 6",
    }, delimiter = '%')
    public void testCount(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "a/b.eq(a/b)                                                % true",
            "a/b.eq(b/a)                                                % false",
            "/a/b.eq(a/b)                                               % false",
            "<http://a.com>.eq(<http://a.com>)                          % true",
    }, delimiter = '%')
    public void testEquality(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @Test
    public void testHasTemplates() {
        // Templates contain ${...} expressions
        assertTrue(uri("http://example.com/${user}").uriValue().hasTemplates());
        assertTrue(uri("/api/${version}/users").uriValue().hasTemplates());
        assertTrue(uri("http://blah.com:${+10}").uriValue().hasTemplates());

        // Non-templates
        assertFalse(uri("http://example.com/static").uriValue().hasTemplates());
        assertFalse(uri("/api/v1/users").uriValue().hasTemplates());

        // fURI coefficients are NOT templates
        assertFalse(uri("/path{?}").uriValue().hasTemplates());  // {?} = maybe coefficient
        assertFalse(uri("/path{+}").uriValue().hasTemplates());  // {+} = some coefficient
        assertFalse(uri("/path{*}").uriValue().hasTemplates());  // {*} = maybe some coefficient
    }

    @Test
    public void testTemplateExtraction() {
        // Extract template expressions with component locations
        final var templates = uri("http://example.com:${+10}/${user}").uriValue().templates();
        assertEquals(2, templates.size());

        // First template is in PORT component
        assertEquals(fURI.Component.PORT, templates.get(0).get0());
        assertEquals("+10", templates.get(0).get1());

        // Second template is in PATH component
        assertEquals(fURI.Component.PATH, templates.get(1).get0());
        assertEquals("user", templates.get(1).get1());
    }

    /**
     * Test URI template expansion with ${expr} syntax.
     * Format: lhs_value.template_uri % expected_result
     * <p>
     * Examples:
     * - 70.<http://blah.com:${+10}> → evaluates ${+10} as 70.plus(10) = 80, coerced to int for PORT
     * - rec(user:alice).<http://example.com/${user}> → evaluates ${user} as rec.apply(uri("user")) = "alice"
     * - 5.<http://api.com/${*2}/data> → evaluates ${*2} as 5.mult(2) = 10
     */
    @ParameterizedTest
    @CsvSource(value = {
            // PORT component - must coerce to integer
            "a.-<[<${_}://${_}:${10}/${_}/${_}>]                                         % [<a://a:10/a/a>]",
            //  "a.-<[<${_}://${_}.${_}:${10}/${_}/${_}>]                                         % [<a://a.a:10/a/a>]",
            "70-<[<http://blah.com:${plus(10)}>]                                         % [<http://blah.com:80>]",
            "70.map(<http://blah.com:${plus(10)}>)                                       % <http://blah.com:80>",
            "100.map(<http://api.com:${minus(20)}>)                                      % <http://api.com:80>",

            // PATH component - toString coercion
            "5.map(<http://example.com/${mult(2)}/data>)                                  % <http://example.com/10/data>",
            "10.map(<http://api.com/v${plus(1)}/users>)                                   % <http://api.com/v11/users>",

            // Variable reference in PATH
            "[user=>alice].map(<http://example.com/${>>user}>)                             % <http://example.com/alice>",
            "[user=>alice].map(<http://example.com/${user}>)                               % <http://example.com/user>",
            "[id=>42].map(<http://api.com/users/${>>id}>)                                  % <http://api.com/users/42>",
            "[id=>42].map(<http://api.com/users/${id}>)                                    % <http://api.com/users/id>",
            // TODO: QUERY component templates need more parser work
            // The mParser needs to be updated to handle ${...} in query strings
            // "70.map(<http://api.com/search?${[q=>hello,lang=>en]}>                      % <http://api.com/search?q=hello&lang=en>",
            // "[x=>10,y=>20].map(<http://map.com/point?${x}>                              % <http://map.com/point?x=10>",

            // Multiple templates in same URI
            "5.map(<http://example.com:${plus(75)}/${mult(10)}>)                           % <http://example.com:80/50>",

            // Non-template URIs pass through unchanged
            "anything.map(<http://example.com/static>)                                   % <http://example.com/static>",
            "42.map(</api/v1/users>)                                                     % </api/v1/users>"
    }, delimiter = '%')
    public void testTemplateExpansion(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "<http://dev.local:8080/v1/search?q=metatron&debug=true>as(rec::T)>>=[host=><prod.net>,port=>443,path=>[api,v2],q=>[debug=>false]].as(uri::T)    % <http://prod.net:443/api/v2?q=metatron&debug=false>",
            "<http://api.local/v1/user/marko?auth=true&debug=true>.as(rec::T)>>=[path=>-<[>>0,>>1,profile,>>3],q=>[debug=>false]].as(uri::T)                 % <http://api.local/v1/profile/marko?debug=false&auth=true>",
            "<http://api.local/v1/user/marko?auth=true&debug=true>.as(rec::T)>>=[path=>-<[>>0,>>1,profile,>>3],q=>[auth=>xyz,debug=>false]].as(uri::T)       % <http://api.local/v1/profile/marko?debug=false&auth=xyz>",
            "<http://api.local/v1/user/marko?auth=true&debug=true>.as(rec::T)>>=[path=>-<[>>0,>>1,profile,>>3],q=>[auth=>none,debug=>false]].as(uri::T)      % <http://api.local/v1/profile/marko?debug=false>"
    }, delimiter = '%', quoteCharacter = '~')
    public void testSelect(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    // a/b/c.~rec~>
    @ParameterizedTest
    @CsvSource(value = {
            "<http://dev.local:8080/v1/search?q=metatron&debug=true>.as(rec::T)?[host=>_,port=>_,path=>_,q=>_].as(uri::T)                                % <http://dev.local:8080/v1/search?q=metatron&debug=true>",
            "<http://api.local/v1/user/marko?auth=true&debug=true>.as(rec::T)?[path=>-<[>>0,>>1,profile,>>3],q=>[debug=>is(not(_))]].as(uri::T)          % noobj",
            "<http://api.local/v1/user/marko?auth=true&debug=true>.as(rec::T)?[path=>-<[>>0,>>1,>>2,>>3],q=>[debug=>?_,_=>?_]].as(uri::T)                % <http://api.local/v1/user/marko?auth=true&debug=true>",
            "<http://api.local/v1/user/marko?auth=true&debug=true>.as(rec::T)?[path=>-<[>>0,>>1,profile,>>3],q=>[debug=>false,_=>none]].as(uri::T)       % noobj"
    }, delimiter = '%', quoteCharacter = '~')
    public void testIsA(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }


    /**
     * THE URI SUM is one uri whose value counts its terms (Marko 2026-11-08, option A): a point the terms share and a
     * point they do not both stay in the value, the argument counts as often as its coefficient, and the uri's own
     * coefficient floats over the sum (Thm 6). A split hands every term back with its point, so {@code (x + y).-<} is x
     * and y again. {@code *} concatenates the values and multiplies the coefficients.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "<{a}> + <>                     % <{,a}>",
            "<{a}> + <{b}>                  % <{a,b}>",
            "<> + <>                        % <{2}>",
            "<> + <> + <>                   % <{3}>",
            "/a + /a + /a                   % </a{3}>",
            "/a + /a + a/                   % <{{2}/a,a/}>",
            "{2}a/b/c + <> + <>             % {2}<{{2},a/b/c}>",
            "<{a,b}/c> + <e/c/f>            % <{a/c,b/c,e/c/f}>",
            "{2}/a/b + /a/c                 % {2}</a/{b,c}>",
            "/a/b + {2}/a/c                 % </a/{b,{2}c}>",
            "<http://a.com/a> + <ws://a.com/a> % <{http://a.com/a,ws://a.com/a}>",
            "<{a,b}> * c                    % <{a,b}/c>",
            "<{a,b}/c> * <>                 % <{a,b}/c>",
            "<> * <{a,b}/c>                 % <{a,b}/c>",
            "<a/b/{{2}c,d}> * {4}e          % {4}<a/b/{{2}c,d}/e>",
            "<a/b/{c,d}> * {10}<>           % {10}<a/b/{c,d}>",
            // a split hands every term back with its point, so a sum splits into what was added
            "</a/{{2}b,c}>.-<               % {/a/b{2},/a/c}",
            "/a/b{2}.plus(/a/c).-<          % {/a/b{2},/a/c}",
            "/a/b{2}.plus(/a/c{2}).-<       % {/a/b{2},/a/c{2}}",
            "{/a/b,/a/c,/a/b}.sum()         % </a/{{2}b,c}>",
            "{/a/b,/a/c}.sum()              % </a/{b,c}>",
            "{/a/b,{0}/a/c}.sum()           % /a/b",
    }, delimiter = '%')
    void testBasicAlgebra(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }
}
