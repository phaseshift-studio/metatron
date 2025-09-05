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

import org.javatuples.Pair;
import org.javatuples.Triplet;
import org.petitparser.context.Result;
import org.petitparser.parser.Parser;
import org.petitparser.parser.combinators.*;
import org.petitparser.parser.primitive.CharacterParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.monoid.SMonoid.Monoid;
import studio.phaseshift.metatron.lang.obj.BObj;
import studio.phaseshift.metatron.lang.obj.SObj;
import studio.phaseshift.metatron.ui.Graphitty;

import java.util.ArrayList;
import java.util.List;

import static org.petitparser.parser.primitive.CharacterParser.any;
import static org.petitparser.parser.primitive.CharacterParser.anyOf;
import static org.petitparser.parser.primitive.CharacterParser.digit;
import static org.petitparser.parser.primitive.CharacterParser.letter;
import static org.petitparser.parser.primitive.CharacterParser.of;
import static org.petitparser.parser.primitive.CharacterParser.word;
import static org.petitparser.parser.primitive.StringParser.of;
import static studio.phaseshift.metatron.lang.inst.SInst.*;
import static studio.phaseshift.metatron.lang.obj.SObj.*;

public class ObjParser {

    private static final Logger LOG = LoggerFactory.getLogger(ObjParser.class);
    private static final SettableParser obj_parser = SettableParser.undefined();
    private static final SettableParser obj_no_code_parser = SettableParser.undefined();
    private static final SettableParser func_parser = SettableParser.undefined();
    private static final SettableParser lst_parser = SettableParser.undefined();
    private static final SettableParser rec_parser = SettableParser.undefined();
    private static final SettableParser inst_parser = SettableParser.undefined();
    private static final SettableParser rel_parser = SettableParser.undefined();

    static {
        rel_parser.set(seq(m_type_prefix_opt_colon(REL_URI),
                seq(of('[').trim(), m_obj(), of("=>").trim(), m_obj(), of(']').trim()))
                .map(t -> new SObj.Rel(Pair.with(pick(pick(t, 1), 1), pick(pick(t, 1), 3)), pick(t, 0), null)));
        obj_parser.set(choice(
                m_comment(),
                m_noobj(),
                m_rel(),
                m_bool(),
                m_real(),
                m_int(),
                m_str(),
                m_code(),
                m_objs(),
                // m_rec(),
                m_inst(),
                m_lst(),
                m_uri()));
        obj_no_code_parser.set(choice(
                m_comment(),
                m_noobj(),
                m_rel(),
                m_bool(),
                m_real(),
                m_int(),
                m_str(),
                m_objs(),
                // m_rec(),
                m_lst(),
                m_uri()));
        lst_parser.set(seq(m_type_prefix_opt_colon(LST_URI), seq(of('[').trim(), m_obj().separatedBy(of(',').trim()), of(']').trim()).pick(1))
                .map(t -> new SObj.Lst(ObjParser.<List>pick(t, 1).stream().filter(o -> o instanceof BObj.Obj).toList(), pick(t, 0), null)));
      /*  rec_parser.set(seq(of('[').trim(), seq(m_obj(), of("=>").trim(), m_obj()).separatedBy(of(',').trim()), of(']').trim())
                .map(t -> new SObj.Rec((Map) ((List) t).stream()
                        .filter(o -> o instanceof List)
                        .flatMap(o -> ((List) o).stream())
                        .filter(o -> o instanceof List)
                        .reduce(new LinkedHashMap<>(), (a, b) -> {
                            ((Map) a).put(((List) b).get(0), ((List) b).get(2));
                            return a;
                        }))));*/
        inst_parser.set(seq(
                m_type_prefix_opt_colon(INST_URI),
                seq(of('(').trim(), opt(m_obj().separatedBy(of(',').trim()), List.of()), of(')').trim()).pick(1),
                opt(seq(of('[').trim(), m_code(), of(']').trim()).pick(1), null))
                .map(t -> new SObj.Inst(new Triplet<>(
                        new SObj.Lst(((List) pick(t, 1)).stream().filter(x -> x instanceof BObj.Obj).toList(), LST_URI, null),
                        InstF.of(ObjParser.<BObj.Obj>pick(t, 2)),
                        BObj.NoObj.of()),
                        pick(t, 0), null)));

    }


    public static <O> O parse(final String code) {
        if (code.trim().isEmpty())
            return (O) BObj.NoObj.of();
        final Result result = m_eval().or(m_obj()).end().parse(code);
        ///System.out.println(result.<Monoid>get());
        //LOG.info("{}==to==>{}", code, result.get().toString());
        if (result.isFailure())
            throw new IllegalStateException(
                    Graphitty.string(result.getBuffer() + "\n" +
                            String.format("%" + (result.getPosition() + "[ERROR] ".length() + 5) + "s", "") +
                            "!b^ !r" +
                            result.getMessage() + "!!\n"));
        return result.get();
    }

    public static BObj.Obj compute(final String code, final BObj.Obj lhs) {
        if (code.trim().isEmpty())
            return lhs;
        Result result = m_code().end().parse(code);
        return result.<BObj.Code>get().apply(lhs);
    }

    public static Parser m_comment() {
        return new SequenceParser(of("---").trim(), any().starGreedy(anyOf("\n\r").or(new EndOfInputParser("end of input")))).map(t -> BObj.NoObj.of());
    }

    public static Parser m_furi() {
        return m_furi("");
    }

    public static Parser m_furi(final String moreChars) {
        return seq(letter().or(anyOf("/%!#_" + moreChars)), word().or(seq(of("=>").not(), anyOf("=?@+/.&%!#_" + moreChars))).star()).flatten().map(t -> new fURI(t.toString()));
    }

    public static Parser m_obj() {
        return obj_parser;
    }

    public static Parser m_noobj() {
        return of("noobj").trim().map(t -> BObj.NoObj.of());
    }

    public static Parser m_objs() {
        return new SequenceParser(of('{').trim(), m_obj().separatedBy(of(',').trim()), of('}').trim()).pick(1).map(t -> new SObj.Objs(((List) t).stream().filter(x -> x instanceof BObj.Obj).toList(), OBJS_URI, null));
    }

    public static Parser m_type_prefix(final fURI baseType) {
        return opt(seq(m_furi(), of(':')).pick(0), baseType);
    }

    public static Parser m_type_prefix_opt_colon(final fURI baseType) {
        return opt(seq(m_furi(), opt(of(':'), ':')).pick(0), baseType);
    }

    public static Parser m_bool() {
        return seq(m_type_prefix(BOOL_URI), of("true").trim().or(of("false").trim()))
                .map(t -> pick(t, 1).equals("true") ?
                        new SObj.Bool(true, pick(t, 0), null) :
                        new SObj.Bool(false, pick(t, 0), null));
    }

    public static Parser m_int() {
        return seq(m_type_prefix(INT_URI), seq(opt(of('-'), '+'), choice(of('0'), digit().plus()))
                .flatten().trim())
                .map(t -> new SObj.Int(Integer.parseInt(pick(t, 1).toString()), pick(t, 0), null));
    }

    public static Parser m_real() {
        return seq(m_type_prefix(REAL_URI), seq(opt(of('-'), '+'), choice(of('0'), digit().plus()), of('.'), digit().plus())
                .flatten().trim())
                .map(t -> new SObj.Real(Double.parseDouble(pick(t, 1).toString()), pick(t, 0), null));
    }

    public static Parser m_str() {
        return seq(m_type_prefix(STR_URI), seq(choice(of('\''), of('"')), any().starLazy(choice(of('\''), of('"'))),
                choice(of('\''), of('"')))
                .pick(1)
                .flatten())
                .map(t -> new SObj.Str(ObjParser.<String>pick(t, 1).substring(1, ObjParser.<String>pick(t, 1).length() - 1), pick(t, 0), null));
    }

    public static Parser m_uri() {
        return seq(m_type_prefix(URI_URI), choice(seq(of('<'), m_furi(":{}"), of('>')).pick(1), m_furi(":{}")))
                .map(t -> new SObj.Uri(pick(t, 1), pick(t, 0), null));
    }

    public static Parser m_rel() {
        return rel_parser;
    }

    public static Parser m_lst() {
        return lst_parser;
    }

    public static Parser m_rec() {
        return rec_parser;
    }

    public static Parser m_inst() {
        return sugar_identity().or(sugar_block(), sugar_plus(), sugar_from(), inst_parser);
    }

    public static Parser m_eval() {
        return seq(opt(obj_no_code_parser, BObj.NoObj.of()), of(".").trim(), m_code()).map(t -> {
            final List<BObj.Inst> newCode = new ArrayList<>();
            newCode.add(new SObj.Inst(START_URI, ObjParser.<BObj.Obj>pick(t, 0)));
            newCode.addAll(ObjParser.<BObj.Code>pick(t, 2).value());
            return new Monoid(new SObj.Code(newCode, CODE_URI, null));
        });
    }

    public static Parser m_code() {
        return m_inst().separatedBy(of('.').trim()).map(t -> new SObj.Code((List) ((List<Object>) t).stream().filter(x -> x instanceof BObj.Inst).toList(), CODE_URI, null));
    }

    /// //////////////////////////////////////////////////////////////////////////////////////////
    /// ///////////////////////////////////// SUGAR PARSERS //////////////////////////////////////
    /// //////////////////////////////////////////////////////////////////////////////////////////
    private static BObj.Inst IDENTITY_INST = new SObj.Inst(IDENTITY_URI);

    public static Parser sugar_identity() {
        return of('_').map(t -> IDENTITY_INST);
    }

    public static Parser sugar_from() {
        return seq(of('*').trim(), m_obj()).map(t -> new SObj.Inst(FROM_URI, ObjParser.<BObj.Obj>pick(t, 1)));
    }

    public static Parser sugar_plus() {
        return seq(of('+').trim(), m_obj()).map(t -> new SObj.Inst(PLUS_URI, ObjParser.<BObj.Obj>pick(t, 1)));
    }

    public static Parser sugar_block() {
        return seq(of('|').trim(), m_obj()).map(t -> new SObj.Inst(BLOCK_URI, ObjParser.<BObj.Obj>pick(t, 1)));
    }

    /// //////////////////////////////////////////////////////////////////////////////////////////
    /// //////////////////////////// PARSER HELPER UTILITY METHODS ///////////////////////////////
    /// //////////////////////////////////////////////////////////////////////////////////////////

    public static SequenceParser seq(final Parser... parsers) {
        return new SequenceParser(parsers);
    }

    public static OptionalParser opt(final Parser check, final Object otherwise) {
        return new OptionalParser(check, otherwise);
    }

    public static ChoiceParser choice(final Parser... parsers) {
        return new ChoiceParser(parsers);
    }

    public static CharacterParser none() {
        return CharacterParser.none();
    }

    public static <O> O pick(final Object list, int index) {
        return (O) ((List) list).get(index);
    }
}
