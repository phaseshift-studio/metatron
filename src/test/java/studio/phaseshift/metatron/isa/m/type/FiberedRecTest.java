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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.TestData;
import studio.phaseshift.metatron.Training;

/**
 * {@link FiberedRec}: a call about a fiber's value answers the bundle with that fiber moved (the LIFT), a call that
 * changes a fiber's type answers its results (the PROJECTION), and a call about the bundle itself is the bundle's
 * alone. A type can't be overwritten, so every world first writes {@code noobj} at the names it declares.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class FiberedRecTest extends AbstractMetatronTest {

    /**
     * THE LIFT KEEPS THE BUNDLE (Marko, 2026-11-09). {@code person[int]} exposes the int at {@code age}, so
     * {@code plus(1)} is about that int, and the answer is the person with its age moved — not the {@code 30} the
     * fiber computed:
     * <pre>
     * person[int]::[name=>'marko',age=>29].plus(1)   =>   person[int]::[name=>'marko',age=>30]
     * </pre>
     * The bundle survives the call, so a chain keeps lifting; navigating to the fiber ({@code >>age}) is how a call
     * comes down. Every fiber the call is about runs it — the parallel fibers — and the answer is still one bundle,
     * with a field that is no fiber ({@code tag}, {@code currency}, {@code device}) carried as it was, and the
     * bundle's own multiplicity kept ({@code {2}person}).
     */
    @Training(
            instruction = "given the definitions in the test data, what does {{{code}}} evaluate to?",
            input = "{{{@TestData}}}; {{{code}}}",
            output = "{{{expected}}}")
    @ParameterizedTest
    @TestData(value = {
            "person -> noobj",
            "person -> rec[age]::T[?[name=>str::T,age=>int::T]]@person",
            "pair -> noobj",
            "pair -> rec[a,b]::T[?[a=>int::T,b=>int::T,tag=>str::T]]@pair",
            "money -> noobj",
            "money -> rec[amount]::T[?[amount=>real::T,currency=>str::T]]@money",
            "reading -> noobj",
            "reading -> rec[value]::T[?[value=>real::T,device=>str::T]]@reading",
            "marko -> person[int]::[name=>'marko',age=>29]@marko",
            "tick -> |inst?<=int(){ plus(1) }",
    })
    @CsvSource(value = {
            "person[int]::[name=>'marko',age=>29].plus(1)                            % person[int]::[name=>'marko',age=>30]",
            "person[int]::[name=>'marko',age=>29] + 1                                % person[int]::[name=>'marko',age=>30]",
            "person[int]::[name=>'marko',age=>29].plus(1).plus(1)                    % person[int]::[name=>'marko',age=>31]",
            "{2}person[int]::[name=>'marko',age=>29].plus(1)                         % {2}person[int]::[name=>'marko',age=>30]",
            "*marko.plus(1)                                                          % person[int]::[name=>'marko',age=>30]",
            // an instruction declared on the fiber's type lifts like a built-in one
            "*marko.tick()                                                           % person[int]::[name=>'marko',age=>30]",
            "*marko.plus(1)>>age                                                     % 30",
            "*marko.plus(1).type()                                                   % rec[age]::T[?[name=>str::T,age=>int::T]]@person[int]",
            // the parallel fibers: both ints move, in one bundle, and tag (a field, not a fiber) stays
            "pair[int,int]::[a=>1,b=>2,tag=>'x'].plus(10)                            % pair[int,int]::[a=>11,b=>12,tag=>'x']",
            "pair[int,int]::[a=>8,b=>4,tag=>'x'].div(2)                              % pair[int,int]::[a=>4,b=>2,tag=>'x']",
            // the fields that are no fiber ride along: a currency is never stripped, a reading keeps its device
            "money[real]::[amount=>9.99,currency=>'USD'].mult(2.0)                   % money[real]::[amount=>19.98,currency=>'USD']",
            "reading[real]::[value=>21.5,device=>'thermo-7'].mult(1.8).plus(32.0)    % reading[real]::[value=>70.7,device=>'thermo-7']",
    }, delimiter = '%', quoteCharacter = '~')
    public void testTheLiftKeepsTheBundle(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    /**
     * {@code @} WRITES BACK, {@code *} DOES NOT. A lift keeps the address its bundle was read with, and a result that
     * carries an address is written back to it — so a call on {@code @marko} is a state transition (two hits take the
     * {@code hp} from 100 to 54), while the same call on the copy {@code *marko} is a value and the stored marko is
     * untouched. A projection answers its results and writes nothing back: after {@code hurt(23).gt(50)} the stored
     * {@code hp} is the hurt's 77.
     */
    @Training.SkipTraining(reason = "each row is an evaluation followed by a separate read of the stored marko, not one expression->value pair")
    @ParameterizedTest
    @TestData(value = {
            "human -> noobj",
            "human -> rec[hp]::T[?[name=>str::T,age=>int::T,hp=>int::T]]@human",
            "marko -> human[int]::[name=>'marko',age=>29,hp=>100]@marko",
            "|inst?int<=int(int::T){ minus(*0) }@hurt",
    })
    @CsvSource(value = {
            // evaluate                     % read back      % stored
            "@marko.hurt(23)                % *marko>>hp     % 77",
            "@marko.hurt(23).hurt(23)       % *marko>>hp     % 54",
            "*marko.hurt(23)                % *marko>>hp     % 100",
            "@marko.hurt(23).gt(50)         % *marko>>hp     % 77",
    }, delimiter = '%', quoteCharacter = '~')
    public void testAnAnchoredLiftWritesBack(final String evaluate, final String fetch, final String stored) {
        AbstractMetatronTest.checkCodeEvaluate(LOG, evaluate, fetch, stored);
    }

    /**
     * THE BUNDLE TAKES ITS OWN INSTRUCTIONS. A call about the bundle itself — a typer, a generic domain, the rec
     * form, or the bundle's own name — runs on the bundle alone, and its fibers are not asked: an untyped read
     * answers the bundle's own field, so a key only the fiber carries is not the bundle's. Naming the fiber's type
     * ({@code ?<=ninja}) reaches into it. {@code plus} is both: a rec argument merges into the bundle (the rec form is
     * the bundle's), an int argument lifts onto the int fiber.
     */
    @Training(
            instruction = "given the definitions in the test data, what does {{{code}}} evaluate to?",
            input = "{{{@TestData}}}; {{{code}}}",
            output = "{{{expected}}}")
    @ParameterizedTest
    @TestData(value = {
            "person -> noobj",
            "person -> rec[age]::T[?[name=>str::T,age=>int::T]]@person",
            "ninja -> noobj",
            "ninja -> rec::T@ninja",
            "squad -> noobj",
            "squad -> rec[lead]::T[?[lead=>rec::T,name=>str::T]]@squad",
            "kai -> squad[ninja]::[name=>'alpha',lead=>ninja::[name=>'kai',rank=>3]]@kai",
            "muster -> |inst?<=squad(){ 'mustered' }",
    })
    @CsvSource(value = {
            "*kai.type()                                                     % rec[lead]::T[?[lead=>rec::T,name=>str::T]]@squad[ninja]",
            "*kai.muster()                                                   % 'mustered'",
            "*kai>>name                                                      % 'alpha'",
            "*kai>>rank                                                      % noobj",
            "*kai>>lead>>rank                                                % 3",
            "*kai>>?<=ninja(rank)                                            % 3",
            "person[int]::[name=>'marko',age=>29].plus([city=>'santafe'])   % person[int]::[name=>'marko',age=>29,city=>'santafe']",
            "person[int]::[name=>'marko',age=>29].plus(1)                    % person[int]::[name=>'marko',age=>30]",
    }, delimiter = '%', quoteCharacter = '~')
    public void testTheBundleTakesItsOwnInstructions(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    /**
     * THE LIFT IS ONE LEVEL DEEP. A fiber resolves the call against the object it holds, so a bundle at a fiber is
     * applied to as itself and never lifted again: {@code troop[person,int]} moves its {@code size} on a
     * {@code plus(1)}, while the int at its person's {@code age} — two fibers down — stays. One {@code >>} makes the
     * person the bundle, and its age lifts.
     */
    @Training(
            instruction = "given the definitions in the test data, what does {{{code}}} evaluate to?",
            input = "{{{@TestData}}}; {{{code}}}",
            output = "{{{expected}}}")
    @ParameterizedTest
    @TestData(value = {
            "person -> noobj",
            "person -> rec[age]::T[?[name=>str::T,age=>int::T]]@person",
            "troop -> noobj",
            "troop -> rec[unit,size]::T[?[unit=>rec::T,size=>int::T]]@troop",
            "nest -> troop[person,int]::[unit=>person[int]::[name=>'marko',age=>29],size=>4]@nest",
    })
    @CsvSource(value = {
            "*nest.plus(1)          % troop[person,int]::[unit=>person[int]::[name=>'marko',age=>29],size=>5]",
            "*nest>>unit.plus(1)    % person[int]::[name=>'marko',age=>30]",
    }, delimiter = '%', quoteCharacter = '~')
    public void testTheLiftIsOneLevelDeep(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    /**
     * A CALL THAT CHANGES A FIBER'S TYPE IS A PROJECTION (Marko, 2026-11-09): it answers the instruction's results,
     * not the rec::T. {@code gt(18)} turns the int at {@code age} into a bool, so it answers {@code true} — and a
     * {@code money} compared is a bool, not a money; {@code rank} turns the ninja at {@code lead} into an int, so it
     * answers {@code 11}. ONE fiber that changes is enough: the filter {@code big} keeps both ints of
     * {@code [a=>8,b=>9]} (a lift, the pair comes back) but drops the {@code 4} of {@code [a=>8,b=>4]} ({@code noobj}
     * is a change of type), so that call projects to the {@code 8} it kept. A fail at a fiber is not the identity: it
     * propagates — {@code div(0)} has no int answer, and a str {@code power} breaks {@code rank}'s range — or a call
     * that broke on a fiber would read as a success.
     */
    @Training(
            instruction = "given the definitions in the test data, what does {{{code}}} evaluate to?",
            input = "{{{@TestData}}}; {{{code}}}",
            output = "{{{expected}}}")
    @ParameterizedTest
    @TestData(value = {
            "person -> noobj",
            "person -> rec[age]::T[?[name=>str::T,age=>int::T]]@person",
            "ninja -> noobj",
            "ninja -> rec::T@ninja",
            "squad -> noobj",
            "squad -> rec[lead]::T[?[lead=>rec::T,name=>str::T]]@squad",
            "pair -> noobj",
            "pair -> rec[a,b]::T[?[a=>int::T,b=>int::T,tag=>str::T]]@pair",
            "money -> noobj",
            "money -> rec[amount]::T[?[amount=>real::T,currency=>str::T]]@money",
            "rank -> |inst?rng=int&dom=ninja(){ >>power }",
            "|inst?int{?}<=int(){ is(gt(5)) }@big",
    })
    @CsvSource(value = {
            "person[int]::[name=>'marko',age=>29].gt(18)                                       % true",
            "money[real]::[amount=>9.99,currency=>'USD'].gt(5.0)                               % true",
            "squad[ninja]::[name=>'alpha',lead=>ninja::[name=>'kai',power=>11]].rank()         % 11",
            "pair[int,int]::[a=>8,b=>9,tag=>'x'].big()                                         % pair[int,int]::[a=>8,b=>9,tag=>'x']",
            "pair[int,int]::[a=>8,b=>4,tag=>'x'].big()                                         % 8",
            "person[int]::[name=>'marko',age=>29].div(0)                                       % <ERROR>",
            "squad[ninja]::[name=>'alpha',lead=>ninja::[name=>'kai',power=>'feeble']].rank()   % <ERROR>",
    }, delimiter = '%', quoteCharacter = '~')
    public void testATypeChangingCallIsAProjection(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    /**
     * WHERE THE LIFT DOES NOT REACH YET. Each row pins today's answer, and its TODO says what it should become, so a fix
     * flips it:
     * <ul>
     *   <li>A bundle passed as an ARGUMENT is not lowered to its fiber: {@code person[int]} is accepted where an int is
     *       expected, but nothing hands the int over, so {@code 5.plus(*marko)} fails.</li>
     *   <li>An instance WITHOUT a bracket names no fiber types ({@link Type#recFibers()} is empty), so an untyped call
     *       does not lift, while the same call typed ({@code ?<=int}) does.</li>
     *   <li>A mapper over a MULTIPLICITY path fiber ({@code rec[geo/+]}) answers {@code noobj}, where mapping each
     *       value and writing the lst back would lift.</li>
     * </ul>
     */
    @Training.SkipTraining(reason = "the rows pin open gaps, not the answers mtron should give")
    @ParameterizedTest
    @TestData(value = {
            "person -> noobj",
            "person -> rec[age]::T[?[name=>str::T,age=>int::T]]@person",
            "marko -> person[int]::[name=>'marko',age=>29]@marko",
            "trail -> noobj",
            "trail -> lst::T[?[real::T,real::T]]@trail",
            "hiker -> noobj",
            "hiker -> rec[geo/+]::T[?[name=>str::T,geo=>trail::T]]@hiker",
            "rambler -> hiker[real{2}]::[name=>'marko',geo=>trail::[2.0,3.5]]@rambler",
    })
    @CsvSource(value = {
            "5.plus(*marko)                                   % <ERROR>",   // TODO: 34
            "person::[name=>'marko',age=>29].plus(1)          % <ERROR>",   // TODO: person::[name=>'marko',age=>30]
            "person::[name=>'marko',age=>29].plus?<=int(1)    % person::[name=>'marko',age=>30]",
            "*rambler.plus(1.0)                               % noobj",     // TODO: hiker[real{2}]::[name=>'marko',geo=>trail::[3.0,4.5]]
    }, delimiter = '%', quoteCharacter = '~')
    public void testWhereTheLiftDoesNotReachYet(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }
}
