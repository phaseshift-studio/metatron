/*
 *   Metatron: A Distributed Virtual Machine
 *   Copyright (c) 2024 PhaseShift Studio, LLC
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.lang.parse;

import org.parboiled.*;
import org.parboiled.buffers.*;
import org.parboiled.errors.*;
import org.parboiled.matchers.*;
import org.parboiled.parserunners.*;
import org.parboiled.support.*;
import studio.phaseshift.metatron.lang.obj.*;

import java.util.*;

public class MParser extends BaseParser<BObj.Obj> implements ParseRunner<BObj.Obj> {

    final BasicParseRunner<BObj.Obj> runner;

    public MParser() {
        this.runner = new BasicParseRunner<>(this.Start());
    }

    public static ParsingResult<BObj.Obj> parse(final String code) {
        return Parboiled.createParser(MParser.class).run(code);
    }


    Rule Start() {
        return Obj();
    }

    /// ////////////////////////////////////////////////////////////
    /// //////////////////////// OBJS //////////////////////////////
    /// ////////////////////////////////////////////////////////////

    Rule Obj() {
        return Sequence(FirstOf(Bool(), Real(), Int(), Str()), EOI);
    }

    Rule Bool() {
        return Sequence(FirstOf(
                        "true",
                        "false"),
                push(match().trim().equals("true") ?
                        SObj.Bool.of(true) :
                        SObj.Bool.of(false)));
    }

    Rule Int() {
        return Sequence(OneOrMore(Digit()), push(SObj.Int.of(Integer.parseInt(match()))));
    }

    Rule Real() {
        return Sequence(Sequence(
                        OneOrMore(Digit()),
                        new CharMatcher('.'),
                        OneOrMore(Digit())),
                push(SObj.Real.of(Double.parseDouble(match()))));
    }


    Rule Str() {
        return Sequence(Sequence("'",
                        ZeroOrMore(FirstOf(Sequence(TestNot(FirstOf("'",
                                '\\', '\n', '\r')), ANY), ECHAR())), "'", WS()),
                push(SObj.Str.of(match().replace("'", ""))));
    }

    Rule Digit() {
        return CharRange('0', '9');
    }

    /// ////////////////////////////////////////////////////////////
    /// ////////////////////// UTILITIES ///////////////////////////
    /// ////////////////////////////////////////////////////////////

    public Rule ECHAR() {
        return Sequence('\\', AnyOf("tbnrf\\\"\'"));
    }

    public Rule WS() {
        return ZeroOrMore(FirstOf(COMMENT(), WS_NO_COMMENT()));
    }

    public Rule WS_NO_COMMENT() {
        return FirstOf(Ch(' '), Ch('\t'), Ch('\f'), EOL());
    }

    public Rule COMMENT() {
        return Sequence('#', ZeroOrMore(Sequence(TestNot(EOL()), ANY)), EOL());
    }

    public Rule EOL() {
        return AnyOf("\n\r");
    }

    public Rule StringIgnoreCaseWS(String string) {
        return Sequence(IgnoreCase(string), WS());
    }

    /// ////////////////////////////////////////////////////////////
    /// //////////////////////// RUNNER ////////////////////////////
    /// ////////////////////////////////////////////////////////////

    @Override
    public ParseRunner<BObj.Obj> withParseErrors(final List<ParseError> parseErrors) {
        return this.runner.withParseErrors(parseErrors);
    }

    @Override
    public ParseRunner<BObj.Obj> withValueStack(final ValueStack<BObj.Obj> valueStack) {
        return this.runner.withValueStack(valueStack);
    }

    @Override
    public ParsingResult<BObj.Obj> run(final String input) {
        return this.runner.run(input);
    }

    @Override
    public ParsingResult<BObj.Obj> run(final char[] input) {
        return this.run(new String(input));
    }

    @Override
    public ParsingResult<BObj.Obj> run(final InputBuffer inputBuffer) {
        return this.run(inputBuffer.toString());
    }
}


