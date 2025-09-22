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

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.petitparser.parser.primitive.CharacterParser.any;
import static org.petitparser.parser.primitive.CharacterParser.anyOf;
import static org.petitparser.parser.primitive.CharacterParser.digit;
import static org.petitparser.parser.primitive.CharacterParser.of;
import static org.petitparser.parser.primitive.CharacterParser.word;
import static org.petitparser.parser.primitive.StringParser.of;
import static studio.phaseshift.metatron.lang.obj.mtron.MInstSet.*;

public class ObjParser {

    private static final GraphittyLogger LOG = Graphitty.log(ObjParser.class);
    private static final SettableParser obj_parser = SettableParser.undefined();
    private static final SettableParser obj_no_code_parser = SettableParser.undefined();
    private static final SettableParser lst_parser = SettableParser.undefined();
    private static final SettableParser rec_parser = SettableParser.undefined();
    private static final SettableParser inst_parser = SettableParser.undefined();
    private static final SettableParser rel_parser = SettableParser.undefined();
    private static final SettableParser obj_rel_back_parser = SettableParser.undefined();

    private static final Map<fURI, fURI> baseResolution = new HashMap<>() {{
        put(fURI.of("int"), INT_TID);
        put(fURI.of("real"), REAL_TID);
        put(fURI.of("str"), STR_TID);
    }};

    static {
        rel_parser.set(seq(m_type_prefix_opt_colon(REL_TID), obj_rel_back_parser, of("=>").trim(), m_obj())
                .map(t -> MRel.of(pick(t, 1), pick(t, 3), pick(t, 0))));
        obj_parser.set(choice(
                m_comment(),
                m_type(),
                m_noobj(),
                // m_rel(),
                m_bool(),
                m_real(),
                m_int(),
                m_str(),
                m_code(),
                m_objs(),
                m_inst(),
                m_rec(),
                m_lst(),
                m_uri()));
        obj_no_code_parser.set(choice(
                m_comment(),
                m_type(),
                m_noobj(),
                //  m_rel(),
                m_bool(),
                m_real(),
                m_int(),
                m_str(),
                m_objs(),
                m_rec(),
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
                m_code(),
                m_objs(),
                m_rec(),
                m_inst(),
                m_rec(),
                m_lst(),
                m_uri()));
        lst_parser.set(seq(m_type_prefix_opt_colon(LST_TID),
                of('[').trim(),
                lst_internal(),
                of(']').trim())
                .map(t -> MLst.of(pick(t, 2), pick(t, 0))));

        rec_parser.set(seq(m_type_prefix_opt_colon(REC_TID), of('[').trim(), rec_internal(), of(']')).trim().map(t -> MRec.of(pick(t, 2), pick(t, 0))));

        inst_parser.set(seq(
                choice(m_inst_furi(), m_type_prefix_opt_colon(INST_TID)), // 0 inst_tid
                seq(of('(').trim(), choice(rec_internal(), lst_internal(), of("")), of(')').trim()).pick(1), // 1 inst_args
                opt(seq(of('{').trim(), m_code(), of('}').trim()).pick(1), null)) //  inst_code
                // inst_seed []
                .map(t -> {
                    return (Inst) new MInst(new Triplet<>(
                            pick(t, 1).equals("") ? MLst.of() : pick(t, 1) instanceof List ?
                                    MLst.of(ObjParser.<List<Obj>>pick(t, 1)) :
                                    MRec.of(ObjParser.<Map<Obj, Obj>>pick(t, 1)),
                            Inst.f.of(ObjParser.<Obj>pick(t, 2)),
                            NoObj.single()), // todo: encode seed in parser
                            pick(t, 0), fURI.NULL);
                }));
    }

    public static Parser lst_internal() {
        return choice(of(','), m_obj().separatedBy(of(',').trim())).map(t -> t.equals(',') ? List.of() : ((List) t).stream().filter(o -> o instanceof Obj).toList());
    }

    public static Parser rec_internal() {
        return choice(of("=>").trim(),
                seq(m_obj(), of("=>").trim(), m_obj()).separatedBy(of(',').trim())).map(t ->
                t.equals("=>") ? Map.of() : ((List) t)
                        .stream()
                        .filter(o -> o instanceof List)
                        .collect(Collectors.toMap(kv -> pick(kv, 0), kv -> pick(kv, 2), (a, b) -> b, LinkedHashMap::new)));
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

    public static Parser m_furi(final String furiCharacterSet) {
        final Supplier<Parser> internal = () -> seq(word().or(seq(of("::").not(),
                anyOf(furiCharacterSet))).plus().flatten(), m_furi_coefficient(), m_furi_query()).map(t -> new fURI(pick(t, 0)).coefficient(pick(t, 1)).query(pick(t, 2)));
        final Supplier<Parser> internal2 = () -> seq(word().or(seq(of("::").not(),
                anyOf(FULL_FURI_CHARS))).plus().flatten(), m_furi_coefficient(), m_furi_query()).map(t -> new fURI(pick(t, 0)).coefficient(pick(t, 1)).query(pick(t, 2)));
        return choice(seq(of('<'), internal2.get(), of('>')).pick(1), internal.get());
    }

    public static Parser m_furi_base_path(final String furiCharacterSet) {
        final Supplier<Parser> internal = () -> seq(word().or(seq(of("::").not(),
                anyOf(furiCharacterSet))).plus().flatten()).map(t -> new fURI(pick(t, 0)));
        final Supplier<Parser> internal2 = () -> seq(word().or(seq(of("::").not(),
                anyOf(FULL_FURI_CHARS))).plus().flatten()).map(t -> new fURI(pick(t, 0)));
        return choice(seq(of('<'), internal2.get(), of('>')).pick(1), internal.get());
    }

    public static Parser m_furi() {
        return m_furi(FULL_FURI_CHARS);
    }


    public static Parser m_furi_coefficient() {
        return opt(seq(of('['), choice(
                        seq(digit().plus(), of(','), digit().plus()).flatten(),
                        digit().plus().flatten().map(t -> t + "," + t),
                        of('*').map(t -> "0,"),
                        of('+').map(t -> "1,"),
                        of('?').map(t -> "0,1")),
                of(']')).map(t -> pick(t, 1)), null);
    }

    public static Parser m_furi_query() {
        return opt(seq(of('?'), seq(word().plus(), opt(seq(of('='), word().or(anyOf(REDUCED_FURI_CHARS)).plus()), "")).separatedBy(of('&')).flatten()).map(t -> ObjParser.<String>pick(t, 1)), null);
    }

    public static Parser m_furi_inst_dom_rng() {
        return seq(
                m_furi(REDUCED_FURI_CHARS),
                of("<=").trim(),
                m_furi(REDUCED_FURI_CHARS))
                .map(t -> Stream.of(
                        List.of("rng", pick(t, 0).toString()),
                        List.of("dom", pick(t, 2).toString())).collect(Collectors.toMap(k -> k.get(0), v -> v.get(1), (v1, v2) -> v2, LinkedHashMap::new)));
    }


    public static Parser m_inst_furi() {
        return seq(m_furi_base_path(REDUCED_FURI_CHARS), opt(seq(of('?'), m_furi_inst_dom_rng()).map(t -> ObjParser.pick(t, 1)), null), opt(of("::").trim(), "::"))
                .map(t -> ObjParser.<fURI>pick(t, 0).queryMap(ObjParser.pick(t, 1)));
    }


    public static Parser m_call() {
        return choice(m_code(), m_inst());
    }

    public static Parser m_obj() {
        return obj_parser;
    }

    public static Parser m_noobj() {
        return of("noobj").trim().map(t -> NoObj.single());
    }

    public static Parser m_objs() {
        return seq(of('{').trim(), m_obj().separatedBy(of(',').trim()), of('}').trim()).pick(1)
                .map(t -> new MObjs(((List) t).stream().filter(x -> x instanceof Obj).toList(), OBJS_TID, fURI.NULL));
    }

    public static Parser m_type_prefix(final fURI baseType) {
        return opt(seq(m_furi(), of("::")).pick(0), baseType);
    }

    public static Parser m_type_prefix_opt_colon(final fURI baseType) {
        return opt(seq(m_furi(REDUCED_FURI_CHARS), opt(of("::").trim(), "::")).pick(0), baseType);
    }

    public static Parser m_bool() {
        return seq(m_type_prefix(BOOL_TID), of("true").trim().or(of("false").trim()))
                .map(t -> pick(t, 1).equals("true") ?
                        new MBool(true, pick(t, 0), fURI.NULL) :
                        new MBool(false, pick(t, 0), fURI.NULL));
    }

    public static Parser m_int() {
        return seq(m_type_prefix(INT_TID), seq(opt(of('-'), '+'), choice(of('0'), digit().plus()))
                .flatten().trim())
                .map(t -> new MInt(Long.parseLong(pick(t, 1).toString()), pick(t, 0), fURI.NULL));
    }

    public static Parser m_real() {
        return seq(m_type_prefix(REAL_TID), seq(opt(of('-'), '+'), choice(of('0'), digit().plus()), of('.'), digit().plus())
                .flatten().trim())
                .map(t -> new MReal(Double.parseDouble(pick(t, 1).toString()), pick(t, 0), fURI.NULL));
    }

    public static Parser m_str() {
        final Parser singleQuote = seq(of('\''), (of("\\'").or(any())).starLazy(of('\'')), of('\''));
        final Parser doubleQuote = seq(of('"'), (of("\\\"").or(any())).starLazy(of('"')), of('"'));
        final Parser tripleQuote = seq(
                of('"').repeatLazy(of('"').not(), 3, 3),
                any().starLazy(of('"').repeatLazy(of('"').not(), 3, 3)),
                of('"').repeatLazy(of('"').not(), 3, 3));
        return seq(m_type_prefix(STR_TID), choice(tripleQuote, singleQuote, doubleQuote)
                .pick(1)
                .flatten())
                .map(t -> new MStr(ObjParser.<String>pick(t, 1).substring(1, ObjParser.<String>pick(t, 1).length() - 1), pick(t, 0), fURI.NULL));
    }

    public static Parser m_uri() {
        return seq(m_type_prefix(URI_TID), m_furi(REDUCED_FURI_CHARS)).map(t -> new MUri(pick(t, 1), pick(t, 0), fURI.NULL));
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
        return seq(opt(obj_no_code_parser, NoObj.single()), opt(of(".").trim(), '.'), m_call()).map(t -> {
            final List<Inst> newCode = new ArrayList<>();
            newCode.add(new MInst(Triplet.with(MLst.of(ObjParser.<Obj>pick(t, 0)), Inst.f.UNKNOWN, NoObj.single()), START_TID, fURI.NULL));
            newCode.addAll(ObjParser.<Call>pick(t, 2).insts());
            return new MCode(newCode, CODE_TID, fURI.NULL);
        });
    }

    public static Parser m_code() {
        return m_inst().separatedBy(opt(of('.').trim(), '.'))
                .map(t -> ((List<Object>) t).size() == 1 ?
                        ((List<Inst>) t).get(0) :
                        new MCode((List) ((List<Object>) t)
                                .stream()
                                .filter(x -> x instanceof Inst)
                                .toList(), CODE_TID, fURI.NULL));
    }

    public static Parser m_inst() {
        return choice(ordered_sugar_parsers()).or(inst_parser);
    }

    /// //////////////////////////////////////////////////////////////////////////////////////////
    /// ///////////////////////////////////// SUGAR PARSERS //////////////////////////////////////
    /// //////////////////////////////////////////////////////////////////////////////////////////
    private static Parser[] ordered_sugar_parsers() {
        return new Parser[]{
                sugar_barrier(),
                sugar_block(),
                sugar_within(),
                sugar_identity(),
                sugar_from(),
                sugar_merge(),
                sugar_split(),
                sugar_ref(),
                sugar_plus(),
                sugar_end()};
    }

    private static Parser generate_sugar_parser(final fURI tid, final String sugarOp, final int argCount) {
        return argCount == 0 ?
                of(sugarOp).trim().map(t -> MInst.instA(tid)) :
                seq(of(sugarOp).trim(), opt(of('('), '('), m_obj(), opt(of(')'), ')')).map(t -> MInst.instB(tid, MLst.of(ObjParser.<Obj>pick(t, 2))));
    }

    public static Parser sugar_identity() {
        return generate_sugar_parser(ID_TID, "_", 0);
    }

    public static Parser sugar_barrier() {
        return generate_sugar_parser(BARRIER_TID, "-|", 1);
    }

    public static Parser sugar_from() {
        return generate_sugar_parser(FROM_TID, "*", 1);
    }

    public static Parser sugar_plus() {
        return generate_sugar_parser(PLUS_TID, "+", 1);
    }

    public static Parser sugar_block() {
        return generate_sugar_parser(BLOCK_TID, "|", 1);
    }

    public static Parser sugar_ref() {
        return generate_sugar_parser(REF_TID, "->", 1);
    }

    public static Parser sugar_merge() {
        return generate_sugar_parser(MERGE_TID, ">-", 0);
    }

    public static Parser sugar_split() {
        return generate_sugar_parser(SPLIT_TID, "-<", 1);
    }

    public static Parser sugar_within() {
        return generate_sugar_parser(WITHIN_TID, "_/", 1);
    }

    public static Parser sugar_end() {
        return generate_sugar_parser(END_TID, ";", 0);
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
            throw MTronException.of("%s - unexpected %s[%d]", e, list, index);
        }
    }
}
