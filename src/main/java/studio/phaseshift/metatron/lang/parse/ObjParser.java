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
import org.petitparser.context.*;
import org.petitparser.parser.*;
import org.petitparser.parser.combinators.*;
import org.slf4j.*;
import studio.phaseshift.metatron.lang.*;
import studio.phaseshift.metatron.lang.obj.*;
import studio.phaseshift.metatron.lang.obj.SObj.*;

import java.util.*;

import static org.petitparser.parser.primitive.CharacterParser.*;
import static org.petitparser.parser.primitive.CharacterParser.of;
import static org.petitparser.parser.primitive.StringParser.of;

public class ObjParser {

    private static final Logger LOG = LoggerFactory.getLogger(ObjParser.class);
    private static SettableParser obj_parser = SettableParser.undefined();
    private static SettableParser lst_parser = SettableParser.undefined();
    private static SettableParser inst_parser = SettableParser.undefined();

    static {
        obj_parser.set(new ChoiceParser(m_comment(), m_noobj(), m_bool(), m_real(), m_int(), m_str(), lst_parser, m_code(), m_inst(), m_uri()));
        lst_parser.set(new SequenceParser(of('['), obj_parser.separatedBy(of(',')), of(']'))
                .map(t -> new Lst(((List) t).stream()
                        .filter(o -> o instanceof List)
                        .flatMap(o -> ((List) o).stream())
                        .filter(o -> o instanceof Obj)
                        .toList())));
        inst_parser.set(new SequenceParser(m_furi(), of('('), obj_parser.separatedBy(of(',')), of(')'))
                .map(t -> new Inst(new Triplet<>(
                        new Lst((List<BObj.Obj>) ((List) ((List) t).get(2)).stream().filter(x -> !x.equals(',')).toList()),
                        (a, b) -> a,
                        NoObj.of()),
                        (fURI) ((List) t).get(0))));
    }

    public static Obj parse(final String code) {
        Result result = m_obj().end().parse(code);
        //LOG.info("{}==to==>{}", code, result.get().toString());
        return Obj.of(result.get());
    }

    public static Parser m_comment() {
        return new SequenceParser(of('#'), any().starGreedy(anyOf("\n\r"))).map(t -> NoObj.of());
    }

    public static Parser m_furi() {
        return new SequenceParser(letter(), word().or(anyOf(":?@=+/.&")).star()).flatten().map(t -> new fURI(t.toString()));
    }

    public static Parser m_obj() {
        return obj_parser;
    }

    public static Parser m_noobj() {
        return of("noobj").map(t -> NoObj.of());
    }

    public static Parser m_bool() {
        return of("true").or(of("false")).map(t -> t.equals("true") ? Obj.of(true) : Obj.of(false));
    }

    public static Parser m_int() {
        return new SequenceParser(new OptionalParser(of('-'), '+'), new ChoiceParser(of('0'), digit().plus()))
                .flatten()
                .map(t -> new Int(Integer.parseInt(t.toString())));
    }

    public static Parser m_real() {
        return new SequenceParser(new OptionalParser(of('-'), '+'), new ChoiceParser(of('0'), digit().plus()), of('.'), digit().plus())
                .flatten()
                .map(t -> new Real(Double.parseDouble(t.toString())));
    }

    public static Parser m_str() {
        return of('\'').seq(any().starLazy(of('\'')), of('\'')).pick(1).flatten();
    }

    public static Parser m_uri() {
        return new SequenceParser(new OptionalParser(of('<'), '<'), m_furi(), new OptionalParser(of('>'), '>'))
                .map(t -> new Uri(fURI.create(((List) t).get(1).toString())));
    }

    public static Parser m_lst() {
        return lst_parser;
    }

    public static Parser m_inst() {
        return inst_parser;
    }

    public static Parser m_code() {
        return new SequenceParser(m_inst(), of('.'), m_inst().separatedBy(of('.'))).map(t -> {
            final List<Object> objs = (List<Object>) t;
            final List<BObj.Inst> insts = new ArrayList<>();
            insts.add((Inst) objs.get(0));
            for (int i = 0; i < ((List) objs.get(2)).size(); i = i + 2) {
                insts.add((Inst) ((List) objs.get(2)).get(i));
            }
            return new Code(insts);
        });
    }
}
