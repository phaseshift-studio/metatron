/*
 * Metatron: A Distributed Computing Language and Virtual Machine
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

package studio.phaseshift.metatron.isa.m.parser;

import org.petitparser.context.Result;
import org.petitparser.parser.Parser;
import org.petitparser.parser.combinators.*;
import org.petitparser.parser.primitive.CharacterParser;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.*;
import studio.phaseshift.metatron.isa.m.mInstSet;
import studio.phaseshift.metatron.isa.sys.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.sys.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.petitparser.parser.primitive.CharacterParser.any;
import static org.petitparser.parser.primitive.CharacterParser.anyOf;
import static org.petitparser.parser.primitive.CharacterParser.digit;
import static org.petitparser.parser.primitive.CharacterParser.of;
import static org.petitparser.parser.primitive.CharacterParser.word;
import static org.petitparser.parser.primitive.StringParser.of;
import static studio.phaseshift.metatron.furi.fURI.fnull;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.from_;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MBytes.bytes;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.Tuple.Triplet;

public class mParser {

    public static final SettableParser furi_parser = SettableParser.undefined();
    private static final GraphittyLogger LOG = Graphitty.log(mParser.class);
    private static final SettableParser obj_parser = SettableParser.undefined();
    private static final SettableParser obj_no_code_parser = SettableParser.undefined();
    private static final SettableParser lst_parser = SettableParser.undefined();
    private static final SettableParser rec_parser = SettableParser.undefined();
    public static final SettableParser inst_parser = SettableParser.undefined();
    private static final SettableParser rel_parser = SettableParser.undefined();
    private static final SettableParser obj_rel_back_parser = SettableParser.undefined();
    private static final SettableParser branch_parser = SettableParser.undefined();
    private static final Parser[] PARSERS;

    private static final String REDUCED_FURI_CHARS = "~/%$!#_-@+:";
    private static final String FULL_FURI_CHARS = REDUCED_FURI_CHARS + ". ";

    static {
        final List<Parser> list =
                new ArrayList<>(mInstSet.create().sugars()
                        .stream()
                        .map(triplet ->
                                generate_sugar_parser(triplet.get1(), of(triplet.get0().get0()),
                                        triplet.get2(), null == triplet.get0().get1() ?
                                                null :
                                                of(triplet.get0().get1()))).toList());
        list.addFirst(seq(of('*').trim(), digit().plus().flatten()).map(t -> from_(uri(pick(t, 1).toString())))); // sugar for *0 vs. *<0>
        PARSERS = new Parser[list.size()];
        list.toArray(PARSERS);
        furi_parser.set(seq(word().or(seq(of("::").not(),
                        anyOf(REDUCED_FURI_CHARS))).plus().flatten(),
                opt(m_furi_poly_type(), null),
                opt(m_furi_coefficient(), null),
                opt(none(), null)).map(t -> new fURI(pick(t, 0)).poly(pick(t, 1)).c(pick(t, 2)).query(pick(t, 3))));

        rel_parser.set(seq(m_type_prefix(REL_TID), m_paren_wrap(seq(obj_rel_back_parser, of("=>").trim(), m_obj()))).map(t -> rel(Tuple.Pair.with(pick(pick(t, 1), 0), pick(pick(t, 1), 2)), pick(t, 0), fnull)));
        obj_no_code_parser.set(choice(
                m_comment(),
                m_rec(),
                m_rel(),
                m_fail(),
                m_type(),
                m_noobj(),
                m_bytes(),
                m_bool(),
                m_real(),
                m_int(),
                m_str(),
                m_objs(),
                m_lst(),
                m_inst(),
                m_uri()));
        obj_parser.set(choice(
                m_comment(),
                m_rec(),
                m_rel(),
                m_fail(),
                m_type(),
                m_noobj(),
                m_bytes(),
                m_bool(),
                m_real(),
                m_int(),
                m_str(),
                inst_parser,
                m_code(),
                m_objs(),
                m_lst(),
                m_uri()));
        obj_rel_back_parser.set(choice(
                m_comment(),
                m_rec(),
                m_paren_wrap(m_rel(),true),
                m_type(),
                m_fail(),
                m_noobj(),
                m_bytes(),
                m_bool(),
                m_real(),
                m_int(),
                m_str(),
                m_code(),
                m_objs(),
                m_lst(),
                m_uri()));
        lst_parser.set(seq(m_type_prefix(LST_TID),
                of('[').trim(),
                lst_internal(),
                of(']').trim(),
                m_vid_postfix())
                .map(t -> new MLst(pick(t, 2), pick(t, 0), pick(t, 4))));

        rec_parser.set(seq(m_type_prefix(), of('[').trim(), rec_internal(obj_rel_back_parser, m_call_prefix(MAP_INST_TID)), of(']').trim(), m_vid_postfix()).trim().map(t -> rec((Map<Obj,Obj>)pick(t, 2), pick(t,0), pick(t, 4))));
        inst_parser.set(choice(m_inst_c(), m_inst_b()));

    }

    public static Parser m_inst_b() {
        return seq(
                m_inst_furi(), // 0 inst_tid
                seq(of('(').trim(), choice(rec_internal(m_furi().map(t -> ((fURI) t).toUri()), m_call_prefix(MAP_INST_TID)), lst_internal(), of("")).trim(), of(')').trim()).pick(1), // 1 inst_args
                m_vid_postfix()) //  inst_code
                // inst_seed []
                .map(t -> (Inst) new MInst(Triplet.with(
                        pick(t, 1).equals("") ? lst() : pick(t, 1) instanceof List ?
                                lst(mParser.<List<Obj>>pick(t, 1)) :
                                rec(mParser.<Map<Obj, Obj>>pick(t, 1)),
                        null,
                        noobj()), // todo: encode seed in parser
                        pick(t, 0), pick(t, 2)));
    }

    public static Parser m_inst_c() {
        return seq(
                choice(m_inst_furi(), m_type_prefix(INST_TID)), // 0 inst_tid
                seq(of('(').trim(), choice(rec_internal(m_furi().map(t -> ((fURI) t).toUri()), m_call_prefix(MAP_INST_TID)), lst_internal(), of("")).trim(), of(')').trim()).pick(1), // 1 inst_args
                seq(of('{').trim(), choice(
                                of('?').map(t -> null),
                                of("<j>").map(t -> null),
                                m_call_prefix(MAP_INST_TID)),
                        of('}').trim()).pick(1),
                m_vid_postfix()) //  inst_code
                // inst_seed []
                .map(t -> (Inst) new MInst(Triplet.with(
                        pick(t, 1).equals("") ? lst() : pick(t, 1) instanceof List ?
                                lst(mParser.<List<Obj>>pick(t, 1)) :
                                rec(mParser.<Map<Obj, Obj>>pick(t, 1)),
                        Inst.f.of(mParser.<Obj>pick(t, 2)),
                        noobj()), // todo: encode seed in parser
                        pick(t, 0), pick(t, 3)));
    }

    public static Parser m_paren_wrap(final Parser parser) {
        return m_paren_wrap(parser, false);
    }

    public static Parser m_paren_wrap(final Parser parser, boolean forced) {
        return forced ? seq(of('(').trim(), parser, of(')').trim()).map(t -> pick(t, 1)) : choice(seq(of('(').trim(), parser, of(')').trim()).map(t -> pick(t, 1)), parser);
    }

    public static Parser m_call_prefix(final fURI headTID) {
        return m_call_prefix(m_paren_wrap(obj_no_code_parser), headTID);
    }

    public static Parser m_call_prefix(final Parser objParser, final fURI headTID) {
        return seq(opt(objParser, noobj()), opt(of(".").trim(), '.'), opt(m_code(), null), m_vid_postfix()).map(t -> {
            final Obj first = mParser.pick(t, 0);
            final Obj second = mParser.pick(t, 2);
            if (null == second)
                return first;
            final List<Inst> newCode = new ArrayList<>();
            if (first.isNoObj() || !first.isInst())
                newCode.add(instB(headTID, lst(first.isInst() ? noobj() : first)));
            else if (first.isInst()) newCode.add(first.as());
            newCode.addAll(mParser.<Call>pick(t, 2).insts());
            return MCode.of(newCode, CODE_TID, pick(t, 3)).tryToInst();
        });
    }

    public static Parser lst_internal() {
        return choice(of(','), m_call_prefix(MAP_INST_TID).separatedBy(of(',').trim())).map(t -> t.equals(',') ? List.of() : ((List) t).stream().filter(o -> o instanceof Obj).toList());
    }

    public static Parser rec_internal(final Parser keyParser, final Parser valueParser) {
        return choice(of("=>").trim(),
                /*choice(seq(of('(').trim(), m_obj(), of("=>").trim(), m_obj(), of(')').trim()),*/
                seq(keyParser, of("=>").trim(), valueParser).separatedBy(of(',').trim()))
                .map(t -> t.equals("=>") ?
                        Map.of() :
                        ((List) t).stream()
                                .filter(o -> o instanceof List)
                                //.map(l -> (((List) l).get(0) instanceof Obj) ? l : ((List) l).subList(1, ((List) l).size() - 1))
                                .collect(Collectors.toMap(kv -> pick(kv, 0), kv -> pick(kv, 2), Obj::append, LinkedHashMap::new)));
    }

    public static <O extends Obj> O eval(final String code) {
        return (O) objs(Arrays.stream(code.split(";"))
                .filter(s -> !s.trim().isEmpty())
                .map(s -> Arrays.stream(s.split("\n"))
                        .map(String::trim)
                        .filter(t -> !t.startsWith("[--"))
                        .reduce("", (a, b) -> a + b + "\n"))
                .map(s -> mParser.parse(s).apply())
                .filter(o -> !o.isNoObj())
                .map(Obj::as));
    }

    public static <O extends Obj> O parseByLine(final String code) {
        return (O) objs(Arrays.stream(code.split(";"))
                .filter(s -> !s.trim().isEmpty())
                .map(s -> Arrays.stream(s.split("\n"))
                        .map(String::trim)
                        .filter(t -> !t.startsWith("[--"))
                        .reduce("", (a, b) -> a + b + "\n"))
                .map(s -> mParser.parse(s).<Obj>as())
                .filter(o -> !o.isNoObj())
                .map(Obj::as));
    }

    public static <O extends Obj> O parse(final String code) {
        if (code.trim().isEmpty())
            return (O) noobj();
        final Result result = seq(choice(m_call_prefix(START_INST_TID), m_obj(false)), opt(m_comment(), null)).map(t -> pick(t, 0)).end().parse(code.trim());
        if (result.isFailure()) {
            LOG.except(result.getBuffer() + "\n" + " ".repeat(result.getPosition()) + "^ " + result.getMessage() + "\n");
        }
        return result.get();
    }

    public static Parser m_comment() {
        return choice(
                seq(of("[--").trim(), any().starGreedy(anyOf("\n\r").or(new EndOfInputParser("end of input")))),
                seq(of("[==").trim(), any().starGreedy(of("==]")), of("==]"))).map(t -> noobj());
    }

    public static Parser m_furi() {
        return m_furi(FULL_FURI_CHARS, true, true, true);
    }

    public static Parser m_furi_no_query() {
        return m_furi(FULL_FURI_CHARS, false, true, false);
    }

    private static Parser m_furi_internal(final String furiCharacterSet, final boolean polynomial, final boolean coefficient, final boolean query) {
        return seq(word().or(seq(of("::").not(), of("[").not(), of("(").not(),
                        anyOf(furiCharacterSet))).plus().flatten(),
                opt(polynomial ? m_furi_poly_type() : none(), null),
                opt(coefficient ? m_furi_coefficient() : none(), null),
                opt(query ? m_furi_query() : none(), null)).map(t -> new fURI(pick(t, 0)).poly(pick(t, 1)).c(pick(t, 2)).query(pick(t, 3)));
    }

    public static Parser m_furi(final String furiCharacterSet, final boolean polynomial, final boolean coefficient, final boolean query) {
        return choice(
                seq(of('<'), m_furi_internal(FULL_FURI_CHARS, polynomial, coefficient, query), of('>')).pick(1),
                seq(of("<>")).map(t -> new fURI("")),
                m_furi_internal(furiCharacterSet, polynomial, coefficient, query));
    }

    public static Parser m_furi_poly_type() {
        return seq(of('['), furi_parser.separatedBy(of(',')), of(']'))
                .map(t -> ((List) (pick(t, 1))).stream().filter(c -> !c.equals(',')).map(Object::toString).toList());
    }

    public static Parser m_furi_coefficient() {
        return seq(of('{'), choice(
                        seq(opt(seq(opt(of('-'), ""), digit().plus()), ""), of(','), opt(seq(opt(of('-'), ""), digit().plus()), "")).flatten(),
                        seq(opt(of('-'), ""), digit().plus()).flatten().map(t -> t + "," + t),
                        of(","),
                        of("**"),
                        of("*"),
                        of("+"),
                        of("??"),
                        of("?")),
                of('}')).map(t -> pick(t, 1));
    }

    public static Parser m_furi_query() {
        return seq(of('?'), seq(
                opt(m_furi_inst_dom_rng(), ""),
                opt(of('&'), ""),
                opt(seq(word().plus(), opt(seq(of('='), choice(m_furi_no_query(), word().or(anyOf(FULL_FURI_CHARS)).star())), "")).separatedBy(of('&')), "").flatten())
        ).map(t -> mParser.<List<String>>pick(t, 1).stream().reduce((a, b) -> a + b).orElse(""));
    }

    public static Parser m_furi_inst_dom_rng() {
        return seq(
                opt(m_furi(REDUCED_FURI_CHARS, true, true, false), null),
                of("<=").trim(),
                opt(m_furi(REDUCED_FURI_CHARS, true, true, false), null))
                .map(t -> {
                    String domrng = "";
                    final fURI dom = pick(t, 2);
                    final fURI rng = pick(t, 0);
                    if (null != dom)
                        domrng = "dom=" + dom;
                    if (null != rng) {
                        if (null != dom)
                            domrng = domrng + "&";
                        domrng = domrng + "rng=" + rng;
                    }
                    return domrng;
                });


    }


    public static Parser m_inst_furi() {
        return seq(m_furi(REDUCED_FURI_CHARS, true, true, false), opt(m_furi_query(), ""), opt(of("::").trim(), "::"))
                .map(t -> mParser.<fURI>pick(t, 0).query(mParser.pick(t, 1)));
    }

    public static Parser m_obj(final boolean allowParens) {
        return allowParens ? m_paren_wrap(obj_parser) : obj_parser;
    }

    public static Parser m_obj() {
        return mParser.m_obj(true);
    }

    public static Parser m_noobj() {
        return seq(of("noobj"), opt(m_furi_coefficient(), null)).trim().map(t -> noobj());
    }

    public static Parser m_objs() {
        return choice(
                seq(of('{').trim(), of(',').trim(), of('}').trim()),
                seq(of('{').trim(), m_call_prefix(MAP_INST_TID).separatedBy(of(',').trim()), of('}').trim()).pick(1))
                .map(t -> objs(((List) t).stream().filter(x -> x instanceof Obj).toList()));
    }

    public static Parser m_type_prefix(final fURI baseType) {
        return opt(seq(m_furi(REDUCED_FURI_CHARS, true, true, true), of("://").not(), of("::")).pick(0), baseType);
    }

    public static Parser m_type_prefix() {
        return m_type_prefix(null);
    }

    public static Parser m_vid_postfix() {
        return opt(seq(of('@'), m_furi(REDUCED_FURI_CHARS, true, false, false)).map(t -> pick(t, 1)), null);
    }

    public static Parser m_fail() {
        return seq(choice(of("fail"), of(FAIL_TID.toString())), opt(of("::").trim(), "::"), seq(of('[').trim(), m_obj(), of(']').trim()).map(t -> pick(t, 1)).plus(), m_vid_postfix())
                .map(t -> {
                    final Object test = pick(t, 2);
                    final List<Obj> objs = test instanceof List ? ((List) test) : (List) List.of(test);
                    Fail root = null;
                    for (final Obj obj : objs) {
                        final Fail f = fail(MTronException.of(obj.toString()));
                        root = root == null ? f : root.plus(f);
                    }
                    return root.vid(pick(t, 3));
                });
    }

    public static Parser m_bool() {
        return seq(m_type_prefix(BOOL_TID), of("true").trim().or(of("false").trim()), m_vid_postfix())
                .map(t -> pick(t, 1).equals("true") ?
                        bool(true, pick(t, 0), pick(t, 2)) :
                        bool(false, pick(t, 0), pick(t, 2)));
    }

    public static Parser m_bytes() {
        return seq(m_type_prefix(BYTES_TID),
                of("0x"), choice(digit(), anyOf("abcdefABCDEF")).plus().flatten(), m_vid_postfix()).
                map(t ->  bytes(ByteBuffer.wrap(HexFormat.of().parseHex(mParser.<String>pick(t, 2))), pick(t, 0), pick(t, 3)));
    }

    public static Parser m_int() {
        return seq(m_type_prefix(INT_TID), seq(opt(of('-'), '+'), choice(of('0'), digit().plus()))
                .flatten().trim(), m_vid_postfix())
                .map(t -> jnt(Long.parseLong(pick(t, 1).toString()), pick(t, 0), pick(t, 2)));
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
                .map(t -> new MStr(mParser.pick(t, 1), pick(t, 0), pick(t, 2)));
    }

    public static Parser m_uri() {
        return seq(m_type_prefix(URI_TID), m_furi(REDUCED_FURI_CHARS, true, true, true), m_vid_postfix()).map(t -> mParser.<fURI>pick(t, 0).isZero() ? noobj() : new MUri(pick(t, 1), pick(t, 0), pick(t, 2)));
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
        return seq(m_type_prefix(TYPE_INST_TID), of("T"),
                opt(seq(of("["), opt(m_obj(), null), of("]")).map(t -> pick(t, 1)), null),
                opt(seq(of("["), opt(m_obj(), null), of("]")).map(t -> pick(t, 1)), null),
                m_vid_postfix())
                .map(t -> new MType(Tuple.Pair.<Call,Call>with(pick(t, 2), pick(t, 3)),pick(t, 0),pick(t, 4)));
    }

    public static Parser m_code() {
        return seq(m_type_prefix(CODE_TID), opt(of("|["), "|["), m_inst().separatedBy(opt(of('.').trim(), '.')), opt(of("]|"), "]|"), m_vid_postfix())
                .map(t -> ((List<Object>) pick(t, 2)).size() == 1 ?
                        ((List<Inst>) pick(t, 2)).get(0) :
                        new MCode((List) ((List<Object>) pick(t, 2))
                                .stream()
                                .filter(x -> x instanceof Inst)
                                .toList(), pick(t, 0), pick(t, 4)));
    }

    public static Parser m_inst() {
        return choice(PARSERS).or(inst_parser);
    }

    /// //////////////////////////////////////////////////////////////////////////////////////////
    /// ///////////////////////////////////// SUGAR PARSERS //////////////////////////////////////
    /// //////////////////////////////////////////////////////////////////////////////////////////

    private static Parser generate_sugar_parser(final fURI tid, final Parser startToken, final int argCount) {
        return generate_sugar_parser(tid, startToken, argCount, null);
    }

    private static Parser generate_sugar_parser(final List<fURI> instChain, final Parser startToken, final int argCount, final Parser endToken) {
        // TODO: look into ExpressionBuilder for handling paren wrapping properly.
        if (instChain.size() == 1) {
            return null == endToken ? generate_sugar_parser(instChain.getFirst(), startToken, argCount) : generate_sugar_parser(instChain.getFirst(), startToken, argCount, endToken);
        }
        return (argCount == 0 ?
                seq(startToken.trim(), opt(seq(of('?'), m_furi_inst_dom_rng()).map(t -> pick(t, 1)), null)).map(t -> instB(instChain.getFirst(), lst(MInst.instA(instChain.get(1).query(pick(t, 1)))))) :
                seq(startToken.trim(), opt(seq(of('?'), m_furi_inst_dom_rng()).map(t -> pick(t, 1)), null), choice(
                        m_paren_wrap(m_obj()),
                        m_obj()), null == endToken ? of("") : endToken.trim())
                        .map(t -> instB(instChain.getFirst(), lst(instB(instChain.get(1).query(pick(t, 1)), lst(mParser.<Obj>pick(t, 2))))))).trim();
    }

    private static Parser generate_sugar_parser(final fURI tid, final Parser startToken, final int argCount, final Parser endToken) {
        // TODO: look into ExpressionBuilder for handling paren wrapping properly.
        return (argCount == 0 ?
                seq(startToken.trim(), opt(seq(of('?'), m_furi_inst_dom_rng()).map(t -> pick(t, 1)), null)).map(t -> MInst.instA(tid.query(pick(t, 1)))) :
                seq(startToken.trim(), opt(seq(of('?'), m_furi_inst_dom_rng()).map(t -> pick(t, 1)), null), choice(
                        m_paren_wrap(m_obj()),
                        m_obj()), null == endToken ? of("") : endToken.trim())
                        .map(t -> instB(tid.query(pick(t, 1)), lst(mParser.<Obj>pick(t, 2)))));
    }


    public static final Pattern BLOCK_COMMENT_PATTERN = Pattern.compile("(\\[==).*?(==])", Pattern.DOTALL);
    public static final Pattern LINE_COMMENT_PATTERN = Pattern.compile("(\\[--).*");

    public static String removeBlockComments(final String source) {
        return BLOCK_COMMENT_PATTERN.matcher(source).replaceAll("");
    }

    public static String removeLineComments(final String line) {
        return LINE_COMMENT_PATTERN.matcher(line).replaceAll("");
    }
    
    public static Stream<Obj> eval(final File file, final Consumer<Exception> exhandler) throws IOException {
        try (final FileReader read = new FileReader(file)) {
            try (final BufferedReader reader = new BufferedReader(read)) {
                final List<String> lines = reader.lines().toList();
                final String source = removeBlockComments(lines.stream().reduce("", (a, b) -> a + b + "\n"));
                return Arrays.stream(source.split(";"))
                        .map(mParser::removeLineComments)
                        .filter(s -> !s.trim().isEmpty())
                        .map(s -> Arrays.stream(s.split("\n"))
                                .map(String::trim)
                                .reduce("", (a, b) -> a + b + "\n"))
                        .map(s -> {
                            try {
                                return mParser.parse(s).apply();
                            } catch (final Exception e) {
                                exhandler.accept(e);
                                return noobj();
                            }
                        })
                        .filter(o -> !o.isNoObj());
            }
        }
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
