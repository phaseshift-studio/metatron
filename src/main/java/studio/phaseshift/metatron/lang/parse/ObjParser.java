/*
 * Metatron: A Distributed Computing Language and Virtual Machine
 * Copyright (C) 2025- PhaseShift Studio, LLC
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

package studio.phaseshift.metatron.lang.parse;

import org.petitparser.context.Result;
import org.petitparser.parser.Parser;
import org.petitparser.parser.combinators.*;
import org.petitparser.parser.primitive.CharacterParser;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.monoid.mtron.MMonoid;
import studio.phaseshift.metatron.lang.obj.Call;
import studio.phaseshift.metatron.lang.obj.Inst;
import studio.phaseshift.metatron.lang.obj.NoObj;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.lang.obj.mtron.*;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.petitparser.parser.primitive.CharacterParser.any;
import static org.petitparser.parser.primitive.CharacterParser.anyOf;
import static org.petitparser.parser.primitive.CharacterParser.digit;
import static org.petitparser.parser.primitive.CharacterParser.of;
import static org.petitparser.parser.primitive.CharacterParser.word;
import static org.petitparser.parser.primitive.StringParser.of;
import static studio.phaseshift.metatron.lang.obj.mtron.MObjs.objs;
import static studio.phaseshift.metatron.lang.obj.mtron.mtronFluent.StartLess.split;
import static studio.phaseshift.metatron.lang.obj.mtron.mtronInstSet.*;
import static studio.phaseshift.metatron.util.Tuple.Triplet;

public class ObjParser {

    private static final GraphittyLogger LOG = Graphitty.log(ObjParser.class);
    private static final SettableParser obj_parser = SettableParser.undefined();
    private static final SettableParser obj_no_code_parser = SettableParser.undefined();
    private static final SettableParser lst_parser = SettableParser.undefined();
    private static final SettableParser rec_parser = SettableParser.undefined();
    private static final SettableParser inst_parser = SettableParser.undefined();
    private static final SettableParser rel_parser = SettableParser.undefined();
    private static final SettableParser obj_rel_back_parser = SettableParser.undefined();
    private static final SettableParser branch_parser = SettableParser.undefined();

    static {
        branch_parser.set(seq(opt(of("-<"),""),of('{').trim(), m_code().separatedBy(of(',').trim()), of('}').trim()).pick(2)
                .map(t -> split(objs(((List) t).stream().filter(x -> x instanceof Call).toList())).tryToInst()));
        rel_parser.set(seq(m_type_prefix_opt_colon(REL_TID), obj_rel_back_parser, of("=>").trim(), m_obj(), m_vid_postfix())
                .map(t -> new MRel(Tuple.Pair.with(pick(t, 1), pick(t, 3)), pick(t, 0), pick(t, 4))));
        obj_parser.set(choice(
                m_comment(),
                m_type(),
                m_noobj(),
                m_bool(),
                m_real(),
                m_int(),
                m_str(),
                m_call(),
                m_rec(),
                m_objs(),
                m_rel(),
                m_lst(),
                m_uri()));
        obj_no_code_parser.set(choice(
                m_comment(),
                m_type(),
                m_noobj(),
                m_bool(),
                m_real(),
                m_int(),
                m_str(),
                m_rec(),
                m_objs(),
                m_rel(),
                m_lst(),
                m_uri()));
        obj_rel_back_parser.set(choice(
                m_comment(),
                m_type(),
                m_noobj(),
                m_bool(),
                m_real(),
                m_int(),
                m_str(),
                m_call(),
                //m_call(),
                m_rec(),
                m_objs(),
                m_lst(),
                m_uri()));
        lst_parser.set(seq(m_type_prefix_opt_colon(LST_TID),
                of('[').trim(),
                lst_internal(),
                of(']').trim(),
                m_vid_postfix())
                .map(t -> new MLst(pick(t, 2), pick(t, 0), pick(t, 4))));

        rec_parser.set(seq(m_type_prefix_opt_colon(REC_TID), of('[').trim(), rec_internal(), of(']'), m_vid_postfix()).trim().map(t -> new MRec(pick(t, 2), pick(t, 0), pick(t, 4))));

        inst_parser.set(choice(branch_parser,seq(
                choice(m_inst_furi(), m_type_prefix_opt_colon(INST_TID)), // 0 inst_tid
                seq(of('(').trim(), choice(rec_internal(), lst_internal(), of("")), of(')').trim()).pick(1), // 1 inst_args
                opt(seq(of('{').trim(), m_code(), of('}').trim()).pick(1), null),
                m_vid_postfix()) //  inst_code
                // inst_seed []
                .map(t -> (Inst) new MInst(Triplet.with(
                        pick(t, 1).equals("") ? MLst.of() : pick(t, 1) instanceof List ?
                                MLst.of(ObjParser.<List<Obj>>pick(t, 1)) :
                                MRec.of(ObjParser.<Map<Obj, Obj>>pick(t, 1)),
                        Inst.f.of(ObjParser.<Obj>pick(t, 2)),
                        NoObj.single()), // todo: encode seed in parser
                        pick(t, 0), pick(t, 3)))));
    }

    public static Parser lst_internal() {
        return choice(of(','), m_obj().separatedBy(of(',').trim())).map(t -> t.equals(',') ? List.of() : ((List) t).stream().filter(o -> o instanceof Obj).toList());
    }

    public static Parser rec_internal() {
        return choice(of("=>").trim(),
                /*choice(seq(of('(').trim(), m_obj(), of("=>").trim(), m_obj(), of(')').trim()),*/
                seq(m_obj(), of("=>").trim(), m_obj()).separatedBy(of(',').trim()))
                .map(t -> t.equals("=>") ?
                        Map.of() :
                        ((List) t).stream()
                                .filter(o -> o instanceof List)
                                //.map(l -> (((List) l).get(0) instanceof Obj) ? l : ((List) l).subList(1, ((List) l).size() - 1))
                                .collect(Collectors.toMap(kv -> pick(kv, 0), kv -> pick(kv, 2), Obj::append, LinkedHashMap::new)));
    }

    public static <O extends Obj> Iterator<O> eval(final String code) {
        return (Iterator) MMonoid.of(parse(code)).apply(NoObj.single()).iterator();
    }

    public static <O extends Obj> O parse(final String code) {
        if (code.trim().isEmpty())
            return (O) NoObj.single();
        final Result result = choice(sugar_code(), m_obj()).end().parse(code.trim());
        if (result.isFailure())
            LOG.except(result.getBuffer() + "\n" +
                    String.format("%" + (result.getPosition() + "[ERROR] [Console] ".length() + 3) + "s", "") +
                    "{{b}}^ {{r}}" +
                    result.getMessage() + "{{X}}\n");
        return result.get();
    }

    public static Parser m_comment() {
        return new SequenceParser(of("---").trim(), any().starGreedy(anyOf("\n\r").or(new EndOfInputParser("end of input")))).map(t -> NoObj.single());
    }

    private static final String FULL_FURI_CHARS = "/%!#_@+.:";
    private static final String REDUCED_FURI_CHARS = "/%!#_@+:";

    public static Parser m_furi() {
        return m_furi(FULL_FURI_CHARS, true, true);
    }

    public static Parser m_furi_no_query() {
        return m_furi(FULL_FURI_CHARS, true, false);
    }

    public static Parser m_furi(final String furiCharacterSet, final boolean coefficient, final boolean query) {
        final Supplier<Parser> internal = () -> seq(word().or(seq(of("::").not(),
                        anyOf(furiCharacterSet))).plus().flatten(),
                opt(coefficient ? m_furi_coefficient() : none(), null),
                opt(query ? m_furi_query() : none(), null)).map(t -> new fURI(pick(t, 0)).big().c(pick(t, 1)).query(pick(t, 2)));
        final Supplier<Parser> internal2 = () -> seq(word().or(seq(of("::").not(),
                        anyOf(FULL_FURI_CHARS))).plus().flatten(),
                opt(coefficient ? m_furi_coefficient() : none(), null),
                opt(query ? m_furi_query() : none(), null)).map(t -> new fURI(pick(t, 0)).big().c(pick(t, 1)).query(pick(t, 2)));
        return choice(seq(of('<'), internal2.get(), of('>')).pick(1), internal.get());
    }

    /*public static Parser m_furi_base_path(final String furiCharacterSet) {
        return m_furi(furiCharacterSet, false, false);
    }*/

    public static Parser m_furi_coefficient() {
        return seq(of('['), choice(
                        seq(opt(seq(opt(of('-'), ""), digit().plus()), ""), of(','), opt(seq(opt(of('-'), ""), digit().plus()), "")).flatten(),
                        seq(opt(of('-'), ""), digit().plus()).flatten().map(t -> t + "," + t),
                        of('*').map(t -> "0,"),
                        of('+').map(t -> "1,"),
                        of('?').map(t -> "0,1")),
                of(']')).map(t -> pick(t, 1));
    }

    public static Parser m_furi_query() {
        return seq(of('?'), seq(
                opt(m_furi_inst_dom_rng(), ""),
                opt(of('&'), ""),
                opt(seq(word().plus(), opt(seq(of('='), choice(m_furi_no_query(), word().or(anyOf(FULL_FURI_CHARS)).star())), "")).separatedBy(of('&')), "").flatten())
        ).map(t -> ObjParser.<List<String>>pick(t, 1).stream().reduce((a, b) -> a + b).orElse(""));
    }

    public static Parser m_furi_inst_dom_rng() {
        return seq(
                m_furi(REDUCED_FURI_CHARS, true, false),
                of("<=").trim(),
                m_furi(REDUCED_FURI_CHARS, true, false))
                .map(t -> "dom=%s&rng=%s".formatted(pick(t, 2).toString(), pick(t, 0).toString()));
    }


    public static Parser m_inst_furi() {
        return seq(m_furi(REDUCED_FURI_CHARS, true, false), opt(m_furi_query(), ""), opt(of("::").trim(), "::"))
                .map(t -> ObjParser.<fURI>pick(t, 0).query(ObjParser.pick(t, 1)));
    }


    public static Parser m_call() {
        return choice(seq(of('('), m_code(), of(')')).map(t -> pick(t, 1)), m_code());
    }

    public static Parser m_obj() {
        return obj_parser;
    }

    public static Parser m_noobj() {
        return of("noobj").trim().map(t -> NoObj.single());
    }

    public static Parser m_objs() {
        return choice(
                seq(of('{').trim(), of(',').trim(), of('}').trim()),
                seq(of('{').trim(), m_obj().separatedBy(of(',').trim()), of('}').trim()).pick(1))
                .map(t -> objs(((List) t).stream().filter(x -> x instanceof Obj).toList()));
    }

    public static Parser m_type_prefix(final fURI baseType) {
        return opt(seq(m_furi(), of("::")).pick(0), baseType);
    }

    public static Parser m_vid_postfix() {
        return opt(seq(of('@'), m_furi(REDUCED_FURI_CHARS, false, false)).map(t -> pick(t, 1)), null);
    }

    public static Parser m_type_prefix_opt_colon(final fURI baseType) {
        return opt(seq(m_furi(REDUCED_FURI_CHARS, true, true), opt(of("::").trim(), "::")).pick(0), baseType);
    }

    public static Parser m_bool() {
        return seq(m_type_prefix(BOOL_TID), of("true").trim().or(of("false").trim()), m_vid_postfix())
                .map(t -> pick(t, 1).equals("true") ?
                        new MBool(true, pick(t, 0), pick(t, 2)) :
                        new MBool(false, pick(t, 0), pick(t, 2)));
    }

    public static Parser m_int() {
        return seq(m_type_prefix(INT_TID), seq(opt(of('-'), '+'), choice(of('0'), digit().plus()))
                .flatten().trim(), m_vid_postfix())
                .map(t -> new MInt(Long.parseLong(pick(t, 1).toString()), pick(t, 0), pick(t, 2)));
    }

    public static Parser m_real() {
        return seq(m_type_prefix(REAL_TID), seq(opt(of('-'), '+'), choice(of('0'), digit().plus()), of('.'), digit().plus())
                .flatten().trim(), m_vid_postfix())
                .map(t -> new MReal(Double.parseDouble(pick(t, 1).toString()), pick(t, 0), pick(t, 2)));
    }

    public static Parser m_str() {
        final Parser singleQuote = seq(of('\''), (of("\\'").or(any())).starLazy(of('\'')), of('\'')).flatten().map(t -> t.toString().substring(1, t.toString().length() - 1));
        final Parser doubleQuote = seq(of('"'), (of("\\\"").or(any())).starLazy(of('"')), of('"')).flatten().map(t -> t.toString().substring(1, t.toString().length() - 1));
        final Parser tripleQuote = seq(
                of('"').repeat(3, 3),
                any().starLazy(of('"').repeat(3, 3)),
                of('"').repeat(3, 3)).flatten().map(t -> t.toString().substring(3, t.toString().length() - 3));
        return seq(m_type_prefix(STR_TID), choice(tripleQuote, singleQuote, doubleQuote), m_vid_postfix())
                .map(t -> new MStr(ObjParser.pick(t, 1), pick(t, 0), pick(t, 2)));
    }

    public static Parser m_uri() {
        return seq(m_type_prefix(URI_TID), m_furi(REDUCED_FURI_CHARS, true, true), m_vid_postfix()).map(t -> new MUri(pick(t, 1), pick(t, 0), pick(t, 2)));
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

    public static Parser m_type() {
        return seq(m_type_prefix(TYPE_TID), of("T["), opt(m_obj(), null), of("]")).map(t -> MType.of(pick(t, 2), pick(t, 0)));
    }

    public static Parser sugar_code() {
        return seq(opt(obj_no_code_parser, NoObj.single()), opt(of(".").trim(), '.'), m_code(), m_vid_postfix()).map(t -> {
            final List<Inst> newCode = new ArrayList<>();
            newCode.add(new MInst(Triplet.with(MLst.of(ObjParser.<Obj>pick(t, 0)), Inst.f.UNKNOWN, NoObj.single()), START_TID, fURI.NULL));
            newCode.addAll(ObjParser.<Call>pick(t, 2).insts());
            return new MCode(newCode, CODE_TID, pick(t, 3));
        });
    }

    public static Parser m_code_or_obj() {
        return choice(sugar_code(), m_obj());
    }

    public static Parser m_code() {
        return seq(m_inst().separatedBy(opt(of('.').trim(), '.')), m_vid_postfix())
                .map(t -> ((List<Object>) pick(t, 0)).size() == 1 ?
                        ((List<Inst>) pick(t, 0)).get(0) :
                        new MCode((List) ((List<Object>) pick(t, 0))
                                .stream()
                                .filter(x -> x instanceof Inst)
                                .toList(), CODE_TID, pick(t, 1)));
    }

    public static Parser m_inst() {
        return choice(ordered_sugar_parsers()).or(inst_parser);
    }

    /// //////////////////////////////////////////////////////////////////////////////////////////
    /// ///////////////////////////////////// SUGAR PARSERS //////////////////////////////////////
    /// //////////////////////////////////////////////////////////////////////////////////////////
    private static Parser[] ordered_sugar_parsers() {
        return new Parser[]{
                branch_parser,
                generate_sugar_parser(VID_TID, of('@'), 1),
                generate_sugar_parser(BARRIER_TID, of("-|"), 1),
                generate_sugar_parser(BLOCK_TID, of('|'), 1),
                generate_sugar_parser(WITHIN_TID, of("_/"), 1, of("\\_")),
                generate_sugar_parser(ID_TID, of('_'), 0),
                generate_sugar_parser(FROM_TID, of('*'), 1),
                generate_sugar_parser(MERGE_TID, of(">-"), 1),
                generate_sugar_parser(MERGE_TID, of(">-"), 0),
                generate_sugar_parser(SPLIT_TID, of("-<"), 1),
                generate_sugar_parser(REF_TID, of("->"), 1),
                generate_sugar_parser(PLUS_TID, of('+'), 1),
                generate_sugar_parser(END_TID, of(';'), 0),
                generate_sugar_parser(CROSS_TID, of("=="), 1)};
    }

    private static Parser generate_sugar_parser(final fURI tid, final Parser startToken, final int argCount) {
        return generate_sugar_parser(tid, startToken, argCount, null);
    }

    private static Parser generate_sugar_parser(final fURI tid, final Parser startToken, final int argCount, final Parser endToken) {
        // TODO: look into ExpressionBuilder for handling paren wrapping properly.
        return (argCount == 0 ?
                seq(startToken.trim(),opt(seq(of('?'),m_furi_inst_dom_rng()).map(t->pick(t,1)),null)).map(t -> MInst.instA(tid.query(pick(t,1)))) :
                seq(startToken.trim(),opt(seq(of('?'),m_furi_inst_dom_rng()).map(t->pick(t,1)),null), choice(
                        seq(of('('), m_obj(), of(')')).map(t -> ObjParser.<Obj>pick(t, 1)),
                        m_obj()), null == endToken ? of("") : endToken.trim())
                        .map(t -> MInst.instB(tid.query(pick(t,1)), MLst.of(ObjParser.<Obj>pick(t, 2)))));
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
        try {
            return (O) ((List) list).get(index);
        } catch (final Exception e) {
            throw MTronException.of(e, "%s - unexpected %s[%d]", e, list, index);
        }
    }
}
