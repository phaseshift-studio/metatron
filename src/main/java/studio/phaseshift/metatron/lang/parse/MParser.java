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

import org.javatuples.*;
import org.parboiled.*;
import org.parboiled.buffers.*;
import org.parboiled.errors.*;
import org.parboiled.matchers.*;
import org.parboiled.parserunners.*;
import org.parboiled.support.*;
import org.parboiled.transform.*;
import studio.phaseshift.metatron.lang.obj.*;
import studio.phaseshift.metatron.lang.obj.SObj.Lst;
import studio.phaseshift.metatron.lang.obj.SObj.NoObj;

import java.net.*;
import java.util.*;

import static studio.phaseshift.metatron.lang.obj.BObj.*;

public class MParser extends BaseParser<BObj.Obj> implements ParseRunner<BObj.Obj> {

    final BasicParseRunner<BObj.Obj> runner;

    public MParser() {
        this.runner = new BasicParseRunner<>(Start());
    }

    public static BObj.Obj parse(final String code) {
        ParsingResult<BObj.Obj> result = Parboiled.createParser(MParser.class).run(code);
        return null == result || result.resultValue == null ? NoObj.of() : result.resultValue;
    }


    Rule Start() {
        return Sequence(Obj(), WS(), EOI);
    }

    /// ////////////////////////////////////////////////////////////
    /// //////////////////////// OBJS //////////////////////////////
    /// ////////////////////////////////////////////////////////////

    Rule Obj() {
        final StringVar typeVariable = new StringVar("");
        return Sequence(Optional(Sequence(Type(), '['), typeVariable.append(match().replace("[", ""))),
                FirstOf(Bool(), Real(), Int(), Str(), Code(), Inst(), Lst(), Uri()), Optional(']'), WS(),
                push(typeVariable.isEmpty() ? pop() : SObj.Obj.of(pop().value(), URI.create(typeVariable.get()))));
    }

    Rule Type() {
        return Sequence(Letter(), ZeroOrMore(UriCharacter()));
    }

    /*Rule Code() {
        return Sequence(ZeroOrMore(Inst()))
    }*/

    Rule Lst() {
        return Sequence('[', push(Lst.of()), ZeroOrMore(Sequence(Obj(), WS(), ',', WS(), new BaseAction("LstStack") {
            @Override
            public boolean run(Context context) {
                final Context<BObj.Obj> typedContext = (Context<BObj.Obj>) context;
                final BObj.Obj lstElement = typedContext.getValueStack().pop();
                final BObj.Lst lstObj = typedContext.getValueStack().pop().<BObj.Lst>as();
                final List<BObj.Obj> list = new ArrayList<>(lstObj.value());
                list.add(lstElement);
                typedContext.getValueStack().push(Lst.of(list));
                return true;
            }
        })), ']');
    }

    static class InstArgAction extends BaseAction {
        final List<BObj.Obj> instArgs = new ArrayList<>();

        public InstArgAction() {
            super("InstArgsAction");
        }

        @Override
        public boolean run(Context context) {
            this.instArgs.add(((Context<BObj.Obj>) context).getValueStack().pop());
            return true;
        }

        public List<BObj.Obj> getArgs() {
            return this.instArgs;
        }
    }

    static class InstCompleteAction extends BaseAction {
        final List<BObj.Obj> instArgs;

        public InstCompleteAction(List<BObj.Obj> instArgs) {
            super("InstCompleteAction");
            this.instArgs = instArgs;
        }

        @Override
        public boolean run(Context context) {
            context.getValueStack().push(
                    new SObj.Inst(new Triplet<>(new SObj.Lst(this.instArgs), (a, b) -> a, NoObj.of()), INST_URI));
            return true;
        }
    }

    Rule Inst() {
        final InstArgAction argPushAction = new InstArgAction();
        return Sequence(Sequence('(',
                        Optional(Obj(), argPushAction),
                        WS(),
                        ZeroOrMore(Sequence(',', Obj(), argPushAction, WS())),
                        ')'),
                new InstCompleteAction(argPushAction.getArgs()));
    }

    /// ///////////////////////////////////////////////////////////////////////////

    static class CodeInstAction extends BaseAction {
        final List<BObj.Inst> instArgs = new ArrayList<>();

        public CodeInstAction() {
            super("CodeInstAction");
        }

        @Override
        public boolean run(Context context) {
            this.instArgs.add(((Context<BObj.Inst>) context).getValueStack().pop());
            return true;
        }

        public List<BObj.Inst> getArgs() {
            return this.instArgs;
        }
    }

    static class CodeCompleteAction extends BaseAction {
        final List<BObj.Inst> instArgs;

        public CodeCompleteAction(List<BObj.Inst> instArgs) {
            super("CodeCompleteAction");
            this.instArgs = instArgs;
        }

        @Override
        public boolean run(Context context) {
            context.getValueStack().push(new SObj.Code(this.instArgs));
            return true;
        }
    }

    Rule Code() {
        final CodeInstAction instPushAction = new CodeInstAction();
        return Sequence(Inst(), instPushAction, ZeroOrMore(Sequence('.', Inst(), instPushAction)),
                new CodeCompleteAction(instPushAction.getArgs()));
    }

    Rule Bool() {
        return Sequence(Sequence(FirstOf(
                        Sequence("true", TestNot(LetterOrDigit())),
                        Sequence("false", TestNot(LetterOrDigit()))), WS()),
                push(match().trim().equals("true") ?
                        SObj.Bool.of(true) :
                        SObj.Bool.of(false)));
    }

    Rule Int() {
        return Sequence(Sequence(OneOrMore(Digit()), WS()), push(SObj.Int.of(Integer.parseInt(match().trim()))));
    }

    Rule Real() {
        return Sequence(Sequence(
                        OneOrMore(Digit()),
                        new CharMatcher('.'),
                        OneOrMore(Digit()), WS()),
                push(SObj.Real.of(Double.parseDouble(match().trim()))));
    }


    Rule Str() {
        return Sequence(Sequence("'",
                        ZeroOrMore(FirstOf(Sequence(TestNot(FirstOf("'",
                                '\\', '\n', '\r')), ANY), ECHAR())), "'", WS()),
                push(SObj.Str.of(match().replace("'", ""))));
    }

    Rule Uri() {
        return Sequence(FirstOf(Sequence('<', Type(), '>'), Type()),
                push(SObj.Uri.of(URI.create(match().replace("<", "").replace(">", "")))));
    }


    /// ////////////////////////////////////////////////////////////
    /// ////////////////////// UTILITIES ///////////////////////////
    /// ////////////////////////////////////////////////////////////


    Rule Digit() {
        return CharRange('0', '9');
    }

    Rule Letter() {
        return FirstOf(Sequence('\\', UnicodeEscape()), CharRange('a', 'z'), CharRange('A', 'Z'));
    }

    Rule UriCharacter() {
        return FirstOf(Letter(), Digit(), '/', '.', ':', '@', '?', '=', '-', '+', '!', '%', ';', '~', '&');
    }

    Rule LetterOrDigit() {
        return FirstOf(Digit(), Letter());
    }

    Rule HexDigit() {
        return FirstOf(CharRange('a', 'f'), CharRange('A', 'F'), CharRange('0', '9'));
    }

    Rule UnicodeEscape() {
        return Sequence(OneOrMore('u'), HexDigit(), HexDigit(), HexDigit(), HexDigit());
    }

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


