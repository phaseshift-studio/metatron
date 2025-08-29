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

import org.javatuples.Triplet;
import org.petitparser.context.Result;
import org.petitparser.parser.Parser;
import org.petitparser.parser.combinators.ChoiceParser;
import org.petitparser.parser.combinators.EndOfInputParser;
import org.petitparser.parser.combinators.OptionalParser;
import org.petitparser.parser.combinators.SequenceParser;
import org.petitparser.parser.combinators.SettableParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.monoid.SMonoid.Monoid;
import studio.phaseshift.metatron.lang.obj.BObj;
import studio.phaseshift.metatron.lang.obj.SObj;
import studio.phaseshift.metatron.lang.obj.SObj.Code;
import studio.phaseshift.metatron.lang.obj.SObj.Inst;
import studio.phaseshift.metatron.lang.obj.SObj.Int;
import studio.phaseshift.metatron.lang.obj.SObj.Lst;
import studio.phaseshift.metatron.lang.obj.SObj.NoObj;
import studio.phaseshift.metatron.lang.obj.SObj.Obj;
import studio.phaseshift.metatron.lang.obj.SObj.Objs;
import studio.phaseshift.metatron.lang.obj.SObj.Real;
import studio.phaseshift.metatron.lang.obj.SObj.Rec;
import studio.phaseshift.metatron.lang.obj.SObj.Uri;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.petitparser.parser.primitive.CharacterParser.any;
import static org.petitparser.parser.primitive.CharacterParser.anyOf;
import static org.petitparser.parser.primitive.CharacterParser.digit;
import static org.petitparser.parser.primitive.CharacterParser.letter;
import static org.petitparser.parser.primitive.CharacterParser.of;
import static org.petitparser.parser.primitive.CharacterParser.word;
import static org.petitparser.parser.primitive.StringParser.of;
import static studio.phaseshift.metatron.lang.inst.SInst.START_URI;

public class ObjParser {

    private static final Logger LOG = LoggerFactory.getLogger(ObjParser.class);
    private static final SettableParser obj_parser = SettableParser.undefined();
    private static final SettableParser obj_no_code_parser = SettableParser.undefined();
    private static final SettableParser func_parser = SettableParser.undefined();
    private static final SettableParser lst_parser = SettableParser.undefined();
    private static final SettableParser rec_parser = SettableParser.undefined();
    private static final SettableParser inst_parser = SettableParser.undefined();

    static {
        obj_parser.set(new ChoiceParser(
                m_comment(),
                m_code(),
                m_objs(),
                m_noobj(),
                // m_rec(),
                m_typed(m_bool()),
                m_typed(m_real()),
                m_ref(m_typed(m_int())),
                m_typed(m_str()),
                m_inst(),
                //m_typed(m_func()),
                m_typed(m_uri()),
                m_typed(m_lst())));
        obj_no_code_parser.set(new ChoiceParser(
                m_comment(),
                m_noobj(),
                m_objs(),
                m_typed(m_rec()),
                m_typed(m_bool()),
                m_typed(m_real()),
                m_typed(m_int()),
                m_typed(m_str()),
                m_typed(m_uri()),
                m_typed(m_lst())));
        func_parser.set(new SequenceParser(m_obj(), of("=>").trim(), m_obj())
                .map(t -> {
                    System.out.println(t);
                    return new Rec((Map) ((List) t).stream()
                            .filter(o -> o instanceof List)
                            .flatMap(o -> ((List) o).stream())
                            .filter(o -> o instanceof List)
                            .reduce(new LinkedHashMap<>(), (a, b) -> {
                                ((Map) a).put(((List) b).get(0), ((List) b).get(2));
                                return a;
                            }));
                }));
        lst_parser.set(new SequenceParser(of('[').trim(), m_obj().separatedBy(of(',').trim()), of(']').trim()).pick(1)
                .map(t -> new Lst(((List) t).stream().filter(o -> o instanceof Obj).toList())));
        rec_parser.set(new SequenceParser(of('[').trim(), new SequenceParser(m_obj(), of("=>").trim(), m_obj()).separatedBy(of(',').trim()), of(']').trim())
                .map(t -> new Rec((Map) ((List) t).stream()
                        .filter(o -> o instanceof List)
                        .flatMap(o -> ((List) o).stream())
                        .filter(o -> o instanceof List)
                        .reduce(new LinkedHashMap<>(), (a, b) -> {
                            ((Map) a).put(((List) b).get(0), ((List) b).get(2));
                            return a;
                        }))));
        inst_parser.set(new SequenceParser(m_furi(), of('(').trim(), m_obj().separatedBy(of(',').trim()), of(')').trim())
                .map(t -> new Inst(new Triplet<>(
                        new Lst(((List) ((List) t).get(2)).stream().filter(x -> x instanceof Obj).toList()),
                        null,
                        NoObj.of()),
                        (fURI) ((List) t).get(0))));
    }

    public static Object parse(final String code) {
        if (code.trim().isEmpty())
            return NoObj.of();
        Result result = m_eval().or(m_obj()).end().parse(code);
        //LOG.info("{}==to==>{}", code, result.get().toString());
        return result.get();
    }

    public static Parser m_ref(final Parser an_obj_parser) {
        return new SequenceParser(an_obj_parser, new OptionalParser(new SequenceParser(of('@'), m_furi()), null).trim()).map(t -> {
            List list = (List) t;
            System.out.println(t);
            if (null == list.get(1))
                return list.get(0);
            else
                return ((Obj) list.get(0)).id((fURI) ((List)list.get(1)).get(1));
        });
    }

    public static Parser m_typed(final Parser an_obj_parser) {
        return new SequenceParser(new OptionalParser(new SequenceParser(m_furi(), of('[').trim()).pick(0), null), an_obj_parser, new OptionalParser(of(']'), ']').trim()).map(t -> {
            final List<Object> list = (List) t;
            fURI type = (fURI) list.get(0);
            return SObj.Obj.of(list.get(1), type);
        });
    }

    public static Parser m_comment() {
        return new SequenceParser(of('#').trim(), any().starGreedy(anyOf("\n\r").or(new EndOfInputParser("end of input")))).map(t -> NoObj.of());
    }

    public static Parser m_furi() {
        return new SequenceParser(letter(), word().or(anyOf(":?@+/.&")).star()).flatten().map(t -> new fURI(t.toString()));
    }

    public static Parser m_func() {
        return func_parser;
    }

    public static Parser m_obj() {
        return obj_parser;
    }

    public static Parser m_noobj() {
        return of("noobj").trim().map(t -> NoObj.of());
    }

    public static Parser m_objs() {
        return new SequenceParser(of('{').trim(), m_obj().separatedBy(of(',').trim()), of('}').trim()).pick(1).map(t -> new Objs(((List) t).stream().filter(x -> x instanceof Obj).toList()));
    }

    public static Parser m_bool() {
        return of("true").or(of("false")).trim().map(t -> t.equals("true") ? Obj.of(true) : Obj.of(false));
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

    public static Parser m_rec() {
        return rec_parser;
    }

    public static Parser m_inst() {
        return inst_parser;
    }

    public static Parser m_eval() {
        return new SequenceParser(obj_no_code_parser, of(".").trim(), m_code()).map(t -> {
            Obj start = (Obj) ((List) t).get(0);
            Code code = (Code) ((List) t).get(2);
            List<BObj.Inst> newCode = new ArrayList<>();
            newCode.add(new Inst(START_URI, start));
            newCode.addAll(code.value());
            return new Monoid(new Code(newCode));
        });
    }

    public static Parser m_code() {
        return new SequenceParser(/*new OptionalParser(obj_no_code_parser,NoObj.of()),*/ m_inst().separatedBy(of('.').trim())).map(t -> {
            //Obj start = (Obj)((List)t).get(0);
            return new Code((List) ((List<Object>) t).stream().flatMap(x -> x instanceof List ? ((List<?>) x).stream() : Stream.of(x)).filter(x -> x instanceof Inst).toList());
        });
    }
}
