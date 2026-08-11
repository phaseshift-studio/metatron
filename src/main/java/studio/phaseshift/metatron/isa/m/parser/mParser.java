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

package studio.phaseshift.metatron.isa.m.parser;

import org.petitparser.context.Result;
import org.petitparser.context.Token;
import org.petitparser.parser.Parser;
import org.petitparser.parser.combinators.ChoiceParser;
import org.petitparser.parser.combinators.OptionalParser;
import org.petitparser.parser.combinators.SequenceParser;
import org.petitparser.parser.combinators.SettableParser;
import org.petitparser.parser.primitive.CharacterParser;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Sugar;
import studio.phaseshift.metatron.isa.m.mInstSet;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.*;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.petitparser.parser.primitive.CharacterParser.any;
import static org.petitparser.parser.primitive.CharacterParser.anyOf;
import static org.petitparser.parser.primitive.CharacterParser.digit;
import static org.petitparser.parser.primitive.CharacterParser.noneOf;
import static org.petitparser.parser.primitive.CharacterParser.of;
import static org.petitparser.parser.primitive.CharacterParser.word;
import static org.petitparser.parser.primitive.StringParser.of;
import static studio.phaseshift.metatron.furi.fURI.Singleton.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.and_;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.or_;
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
import static studio.phaseshift.metatron.util.CommonUtil.mutableList;
import static studio.phaseshift.metatron.util.CommonUtil.splitOnNonQuotedSequence;
import static studio.phaseshift.metatron.util.Tuple.Triplet;

public class mParser {

    public static final SettableParser furi_parser = SettableParser.undefined();
    private static final GraphittyLogger LOG = Graphitty.log(mParser.class);
    private static final SettableParser obj_parser = SettableParser.undefined();
    private static final SettableParser obj_no_code_parser = SettableParser.undefined();
    private static final SettableParser obj_no_call_parser = SettableParser.undefined();
    private static final SettableParser lst_parser = SettableParser.undefined();
    private static final SettableParser rec_parser = SettableParser.undefined();
    public static final SettableParser inst_parser = SettableParser.undefined();
    private static final SettableParser rel_parser = SettableParser.undefined();
    private static final SettableParser obj_rel_back_parser = SettableParser.undefined();
    private static final SettableParser obj_rel_back_parser2 = SettableParser.undefined();
    private static final SettableParser and_or_parser = SettableParser.undefined();
    // private static final SettableParser branch_parser = SettableParser.undefined();
    // Space-aware operator parsing:
    // Sugars defined with spaces (e.g., " * ") require those spaces in the source code.
    // Sugars without spaces (e.g., "*") are space-insensitive (use .trim()).
    // This allows: *x → from(x) and x * y → x.mult(y) to coexist.
    private static final LinkedHashSet<Parser> PARSERS = new LinkedHashSet<>();
    private static Parser cachedSugarParser = null;
    private static Parser cachedMainParser = null;
    private static Parser cachedExpressionParser = null;

    /**
     * Maximum loop iterations in parseMulti() before throwing. Tests lower this.
     */
    public static int maxParseIterations = 10_000;

    //
    // Future: watchdog timer for exponential-backtracking detection
    //
    // The three existing guards catch structural loops (zero-progress spins,
    // iteration-cap violations, and stack overflow from left recursion).  They
    // do NOT catch the case where a single parse() call makes incremental progress
    // but traverses a combinatorially large search space — chewing CPU for
    // minutes without truly looping.
    //
    // Implementation sketch:
    //
    //   - A daemon ScheduledExecutorService with one thread.
    //   - parse() (and parseMulti()'s inner call) records its start time in a
    //     ThreadLocal<Long> before entering the parser.
    //   - The watchdog fires every ~500 ms, walks all live parse threads via
    //     Thread.getAllStackTraces(), checks elapsed time, and calls
    //     Thread.interrupt() on any thread that exceeds a configurable
    //     PARSE_TIMEOUT_MS threshold (default 0 = disabled).
    //   - The parse() guard block already catches StackOverflowError; it would
    //     additionally check Thread.interrupted() after PetitParser returns and
    //     throw a "PARSER TIMEOUT" MTronException if set.
    //
    //   Caveat: PetitParser does not check Thread.interrupted() internally, so
    //   the timeout only takes effect after the current parser combinator
    //   returns.  For true preemption we'd need to run the parse in a separate
    //   thread and Thread.stop() it (deprecated/unsafe) or fork a subprocess.
    //   The watchdog approach is "best effort" — it bounds the post-mortem
    //   delay rather than preempting a runaway parse mid-flight.
    //

    private static final String REDUCED_FURI_CHARS = "~/%$!#_-@+:*";
    private static final String FULL_FURI_CHARS = REDUCED_FURI_CHARS + ". ";

    static {
        new mInstSet().sugars().forEach(mParser::addSugar);
        // Cache the sugar parser after all sugars are loaded
        cachedSugarParser = PARSERS.isEmpty() ? null : choice(PARSERS.toArray(new Parser[0]));
        // Initialize all parsers first
        furi_parser.set(seq(word().or(seq(of("::").not(),
                        anyOf(REDUCED_FURI_CHARS))).plus().flatten(),
                opt(m_furi_poly_type(), null),
                opt(m_furi_coefficient(), null),
                opt(none(), null)).map(t -> f(pick(t, 0)).poly(pick(t, 1)).c(cInt.of((String) pick(t, 2))).qString(pick(t, 3))));
        rel_parser.set(seq(m_type_prefix(REL_TID), m_paren_wrap(seq(obj_rel_back_parser, of("=>").trim(), m_obj()))).map(t -> rel(Tuple.Pair.with(pick(pick(t, 1), 0), pick(pick(t, 1), 2)), pick(t, 0), null)));
        obj_no_call_parser.set(choice(
                m_comment(),
                m_type(),
                m_rec(),
                m_paren_wrap(m_rel(), true),
                m_fail(),
                m_noobj(),
                m_bytes(),
                m_bool(),
                m_real(),
                m_int(),
                m_str(),
                m_lst(),
                m_uri(),
                m_objs()));
        obj_no_code_parser.set(choice(
                m_comment(),
                m_type(),
                m_rec(),
                m_rel(),
                m_fail(),
                m_noobj(),
                m_bytes(),
                m_bool(),
                m_real(),
                m_int(),
                m_str(),
                m_lst(),
                m_inst(),
                m_uri(),
                m_objs()));
        obj_parser.set(choice(
                m_comment(),
                m_type(),
                m_rec(),
                m_rel(),
                m_fail(),
                m_noobj(),
                m_bytes(),
                m_bool(),
                m_real(),
                m_int(),
                m_str(),
                inst_parser,
                m_code(),
                m_lst(),
                m_uri(),
                m_objs()));
        obj_rel_back_parser.set(choice(
                m_comment(),
                m_type(),
                m_rec(),
                m_paren_wrap(m_rel(), true),
                m_fail(),
                m_noobj(),
                m_bytes(),
                m_bool(),
                m_real(),
                m_int(),
                m_str(),
                m_code(),
                m_lst(),
                m_uri(),
                m_objs()));
        obj_rel_back_parser2.set(choice(
                m_comment(),
                m_type(),
                m_rec(),
                m_paren_wrap(m_rel(), true),
                m_fail(),
                m_noobj(),
                m_bytes(),
                m_bool(),
                m_real(),
                m_int(),
                m_str(),
                m_inst(),
                m_lst(),
                m_uri(),
                m_objs()));
        lst_parser.set(seq(m_type_prefix(LST_TID),
                of('[').trim(),
                lst_internal(),
                of(']').trim(),
                m_vid_postfix())
                .map(t -> new MLst(pick(t, 2), pick(t, 0), pick(t, 4))));

        rec_parser.set(seq(m_type_prefix(REC_TID), of('[').trim(), rec_internal(obj_rel_back_parser, m_call_prefix(MAP_INST_TID)), of(']').trim(), m_vid_postfix()).trim().map(t -> rec((Map<Obj, Obj>) pick(t, 2), pick(t, 0), pick(t, 4))));
        inst_parser.set(choice(m_inst_b(), m_inst_c()));
        and_or_parser.set(seq(m_obj(), choice(of("||").trim(), of("&&").trim()), m_obj()).map(t -> {
            final Obj lhs = pick(t, 0);
            final Obj rhs = pick(t, 2);
            final String op = pick(t, 1).toString();
            return (op.equals("||") ? or_(lhs, rhs) : and_(lhs, rhs)).tryToInst();
        }));

        // Core expression parser (without .end()) — reusable for multi-statement parsing
        cachedExpressionParser = seq(
                m_comment().star(),
                choice(m_call_prefix(START_INST_TID), m_obj(false)),
                opt(m_comment().trim(), null)
        ).map(t -> {
            final Obj x = pick(t, 1);
            return null == x ? noobj() : x;
        });

        // Cache the main parser to avoid rebuilding it on every parse() call
        cachedMainParser = cachedExpressionParser.end();

        // Multi-statement parser: using expressionParser in a Java-level loop,
        // manually consuming ; between expressions.  Doing this at the Java
        // level instead of using PetitParser's possessive .star() because the
        // separator (;) can consume whitespace without the following expression
        // succeeding, and the possessive star won't backtrack.
    }

    public static LinkedHashSet<Parser> addSugar(final Sugar sugar) {
        final String startToken = sugar.getStartToken();
        final String endToken = sugar.getEndToken();

        // Check if the sugar definition has spaces (space-aware)
        final boolean startHasLeadingSpace = startToken.startsWith(" ");
        final boolean startHasTrailingSpace = startToken.endsWith(" ");
        final String trimmedStart = startToken.trim();

        if (sugar.getPosition() == Sugar.Position.INFIX) {
            PARSERS.add(generate_infix_sugar_parser(
                    sugar.getInstChain(),
                    trimmedStart
            ));
        } else if (startHasLeadingSpace || startHasTrailingSpace) {
            PARSERS.add(generate_space_aware_sugar_parser(
                    sugar.getInstChain(),
                    trimmedStart,
                    startHasLeadingSpace,
                    startHasTrailingSpace,
                    sugar.getArgCount(),
                    endToken
            ));
        } else {
            // Standard space-insensitive sugar (uses .trim())
            PARSERS.add(generate_sugar_parser(
                    sugar.getInstChain(),
                    of(startToken),
                    sugar.getArgCount(),
                    null == endToken ? null : of(endToken)
            ));
        }
        return PARSERS;
    }

    private static Parser generate_infix_sugar_parser(final List<fURI> instChain, final String token) {
        // Infix parser: m_obj_no_sugar() + token + m_obj_no_sugar()
        return seq(m_obj_no_sugar(), of(token).trim(), m_obj()).map(t -> {
            final Obj lhs = pick(t, 0);
            final Obj rhs = pick(t, 2);
            Obj current = lhs;
            for (final fURI tid : instChain) {
                current = instB(tid, lst(current, rhs));
            }
            return current;
        });
    }

    private static Parser generate_space_aware_sugar_parser(
            final List<fURI> instChain,
            final String token,
            final boolean requireLeadingSpace,
            final boolean requireTrailingSpace,
            final int argCount,
            final String endToken) {

        // Build the token parser with space requirements
        Parser tokenParser = of(token);
        if (requireLeadingSpace) {
            tokenParser = seq(CharacterParser.whitespace().plus(), tokenParser).map(t -> pick(t, 1));
        }
        if (requireTrailingSpace) {
            tokenParser = seq(tokenParser, CharacterParser.whitespace().plus()).map(t -> pick(t, 0));
        }

        // Generate the sugar parser with the space-aware token
        return generate_sugar_parser(
                instChain,
                tokenParser,
                argCount,
                null == endToken ? null : of(endToken)
        );
    }

    public static Parser m_inst_b() {
        return seq(
                m_inst_furi(), // 0 inst_tid
                seq(of('(').trim(), choice(rec_internal(m_furi().map(t -> ((fURI) t).toUri()), m_call_prefix(MAP_INST_TID)), lst_internal(), of("")).trim(), of(')').trim(), of('{').not()).pick(1), // 1 inst_args
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
                choice(m_inst_furi(), m_type_prefix(M_ISA_INST_TID)), // 0 inst_tid
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
        return seq(opt(objParser, null), opt(of(".").trim(), '.'), opt(m_code(), null), m_vid_postfix()).map(t -> {
            if (((List) t).get(0) instanceof List) {
                return noobj();
            }
            final Obj first = mParser.pick(t, 0);
            final Obj second = mParser.pick(t, 2);
            if (null == first)
                return second;
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
        return choice(of(','), m_call_prefix(MAP_INST_TID).separatedBy(of(',').trim())).map(t -> t.equals(',') ? mutableList() : mutableList(((List) t).stream().filter(o -> o instanceof Obj).toList()));
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

    public static <O extends Obj> O eval(final File source) {
        try {
            return eval(new String(Files.readAllBytes(source.toPath()), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw MTronException.of(e);
        }
    }

    public static <O extends Obj> O eval(final String code) {
        final AtomicReference<Obj> running = new AtomicReference<>(noobj());
        splitOnNonQuotedSequence(code.replaceAll("\\[==.*?=]", ""), ';', false).stream()
                .filter(s -> !s.trim().isEmpty())
                .map(s -> Arrays.stream(s.split("\n"))
                        .map(String::trim)
                        .filter(t -> !t.startsWith("[--"))
                        .reduce("", (a, b) -> a + b + "\n"))
                .map(s -> mParser.parse(s).apply())
                .filter(o -> !o.isNoObj())
                .forEach(o -> running.getAndUpdate(o::apply));
        return (O) running.get();
    }

    /**
     * Formats a PetitParser parse failure into a human-readable error message
     * with line:column, snippet context, and a simplified explanation of what
     * went wrong.
     */
    private static String formatParseError(final Result failure) {
        final String buffer = failure.getBuffer();
        final int pos = failure.getPosition();
        final int[] lc = Token.lineAndColumnOf(buffer, pos);
        final String rawMsg = failure.getMessage() != null ? failure.getMessage() : "unknown parse failure";

        // ── Build snippet context (~80 chars centered on failure) ──────
        final int contextRadius = 40;
        final int snippetStart = Math.max(0, pos - contextRadius);
        final int snippetEnd = Math.min(buffer.length(), pos + contextRadius);
        final String prefix = snippetStart > 0 ? "..." : "";
        final String suffix = snippetEnd < buffer.length() ? "..." : "";
        final String snippet = prefix
                + buffer.substring(snippetStart, snippetEnd).replace("\n", "\\n").replace("\r", "\\r")
                + suffix;

        // Caret position within the displayed snippet (adjust for "..." prefix)
        final int caretOffset = pos - snippetStart + (snippetStart > 0 ? 3 : 0);
        final String caretLine = " ".repeat(caretOffset) + "^";

        // ── Simplify the PetitParser message ───────────────────────────
        final String simplified = simplifyParseMessage(rawMsg, buffer, pos, snippet);

        return String.format("parse error at line %d, col %d:\n  %s\n  %s\n  %s",
                lc[0], lc[1], snippet, caretLine, simplified);
    }

    /**
     * Cleans up PetitParser's verbose ChoiceParser failure message into a
     * single actionable explanation.
     */
    private static String simplifyParseMessage(final String rawMsg, final String buffer,
                                               final int pos, final String snippet) {
        final boolean atEnd = pos >= buffer.length();
        final char atChar = atEnd ? '\0' : buffer.charAt(pos);
        final char prevChar = pos > 0 ? buffer.charAt(pos - 1) : '\0';

        // At end of input — scan backward for unclosed delimiters (reliable
        // because the full expression is present, nothing comes after)
        if (atEnd) {
            final String unclosed = findUnclosedBefore(buffer, pos);
            if (unclosed != null)
                return "incomplete expression — " + unclosed;
            return "incomplete expression — unexpected end of input";
        }

        // Position lands on an opener — look ahead for missing closer
        if (atChar == '[' || atChar == '(' || atChar == '<' || atChar == '{') {
            final char matchingCloser = switch (atChar) {
                case '[' -> ']';
                case '(' -> ')';
                case '<' -> '>';
                case '{' -> '}';
                default -> '\0';
            };
            if (!hasMatchingCloser(buffer, pos, atChar, matchingCloser))
                return "unclosed '" + atChar + "' — missing '" + matchingCloser + "'?";
        }

        // Position lands on a closer character without a matching opener
        if (atChar == ']')
            return "unexpected ']' — missing opening '[' or extra ']'?";
        if (atChar == '>')
            return "unexpected '>' — URI brackets don't match, or extra '>'?";
        if (atChar == ')')
            return "unexpected ')' — missing opening '(' or extra ')'?";
        if (atChar == '}' && prevChar == '}')
            return "unexpected '}}' — template expression missing '${{' or extra '}'?";
        if (atChar == '}')
            return "unexpected '}' — missing opening '{' or extra '}'?";

        // Generic: show what character couldn't be parsed, but also check from
        // the end of the full expression for unclosed delimiters — the real
        // problem is often an unclosed bracket earlier, not this character.
        final String unclosedFromEnd = findUnclosedBefore(buffer, buffer.length());
        if (unclosedFromEnd != null)
            return String.format("could not parse at '%s' — %s", atChar, unclosedFromEnd);
        return String.format("could not parse at '%s'", atChar);
    }

    /**
     * Characters that precede {@code <} or {@code >} in mtron operators
     * ({@code => -> -< <= ?< ?> << >>}), meaning the angle bracket is
     * part of an operator, not a standalone bracket.
     */
    private static boolean isOperatorChar(final char c) {
        return c == '=' || c == '-' || c == '?' || c == '<' || c == '>';
    }

    /**
     * Checks if {@code buffer} from position {@code from} has a matching closer
     * for the opener at {@code from}.  Handles nesting: a closer at depth → 0
     * resolves the opener.
     */
    private static boolean hasMatchingCloser(final String buffer, final int from,
                                             final char open, final char close) {
        int depth = 0;
        for (int i = from; i < buffer.length(); i++) {
            final char c = buffer.charAt(i);
            if (c == open) depth++;
            else if (c == close && depth > 0) depth--;
            if (depth == 0 && i > from) return true;
        }
        return depth == 0;
    }

    /**
     * Scans backward from {@code pos} looking for an unclosed delimiter.
     * Returns a human-readable description, or null if nothing obvious.
     */
    private static String findUnclosedBefore(final String buffer, final int pos) {
        int depth = 0;
        int angleDepth = 0;
        boolean inDoubleQuote = false;
        boolean inSingleQuote = false;
        boolean inTripleQuote = false;

        for (int i = pos - 1; i >= 0; i--) {
            final char c = buffer.charAt(i);

            // Handle triple-quote
            if (i >= 2 && buffer.substring(i - 2, i + 1).equals("\"\"\"")) {
                inTripleQuote = !inTripleQuote;
                i -= 2;
                continue;
            }

            if (inTripleQuote) continue;

            if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
                continue;
            }
            if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
                continue;
            }
            if (inDoubleQuote || inSingleQuote) continue;

            // Track bracket depth.  Angle brackets (< >) and square/curved/curly
            // brackets ([ ] ( ) { }) each get their own depth counter so that
            // a dangling '>' from a mtron operator doesn't mask an unclosed '['.
            if (c == ']') {
                depth++;
            } else if (c == '[') {
                if (depth == 0) return "unclosed '[' — missing ']'?";
                depth--;
            } else if (c == '>' && !isOperatorChar(i > 0 ? buffer.charAt(i - 1) : '\0')) {
                angleDepth++;
            } else if (c == '<' && !isOperatorChar(i > 0 ? buffer.charAt(i - 1) : '\0')) {
                if (angleDepth == 0) return "unclosed '<' — missing '>'?";
                angleDepth--;
            } else if (c == ')') {
                depth++;
            } else if (c == '(') {
                if (depth == 0) return "unclosed '(' — missing ')'?";
                depth--;
            } else if (c == '}') depth++;
            else if (c == '{') {
                if (depth == 0) return "unclosed '{' — missing '}'?";
                depth--;
            }
        }

        // Quote check
        if (inDoubleQuote) return "unclosed double-quote — missing closing '\"'?";
        if (inSingleQuote) return "unclosed single-quote — missing closing '''?";
        if (inTripleQuote) return "unclosed triple-quote — missing closing '\"\"\"'?";

        return null;
    }

    public static <O extends Obj> O parse(final String code) {
        final String trimmed = code.trim();
        if (trimmed.isEmpty())
            return (O) noobj();
        // Use cached parser instead of rebuilding on every call
        long start = System.nanoTime();
        final Result result;
        try {
            result = cachedMainParser.parse(trimmed);
        } catch (final StackOverflowError e) {
            throw MTronException.of("infinite recursion detected in parser: possible left recursion in '%s'", trimmed);
        }
        long parseTime = System.nanoTime() - start;

        if (result.isFailure()) {
            throw MTronException.of(formatParseError(result));
        }

        start = System.nanoTime();
        O obj = result.get();
        long getTime = System.nanoTime() - start;

        // Log timing for expressions (disable in production)
        // Log timing for expressions (disable in production)
        if (parseTime > 1_000_000) // > 1ms
            LOG.debug("Parse timing for '%s': parse=%dms, get=%dms", trimmed, parseTime / 1_000_000, getTime / 1_000_000);


        return obj;
    }

    /**
     * Non-throwing parse that returns a diagnostic record instead of throwing
     * on failure.  Use this in REPLs, IDE integrations, and anywhere you want
     * to probe validity without a try/catch.
     */
    public static ParseDiagnostic parseDiagnose(final String code) {
        final String trimmed = code.trim();
        if (trimmed.isEmpty())
            return ParseDiagnostic.ok(noobj());
        try {
            final Result result = cachedMainParser.parse(trimmed);
            if (result.isSuccess())
                return ParseDiagnostic.ok(result.get());
            final int[] lc = Token.lineAndColumnOf(result.getBuffer(), result.getPosition());
            final String simplified = simplifyParseMessage(
                    result.getMessage(), result.getBuffer(), result.getPosition(), "");
            return new ParseDiagnostic(noobj(), lc[0], lc[1],
                    result.getPosition(), result.getBuffer(), simplified, false);
        } catch (final StackOverflowError e) {
            return new ParseDiagnostic(noobj(), 1, 1, 0, trimmed,
                    "infinite recursion detected — possible left recursion", false);
        }
    }

    /**
     * Parses mtron code with support for {@code ;}-separated multi-statement input.
     * Unlike {@link #parse(String)} which expects exactly one expression,
     * this method allows {@code expr;expr;...} at the top level. The {@code ;}
     * separator maps to {@code end()} instructions between statements.
     * <p>
     * Implementation: uses {@link #parse(String)} in a loop, consuming one
     * expression at a time and manually trimming the {@code ;} separator.  This
     * avoids PetitParser's possessive-star issue where a separator can partially
     * match without the following expression succeeding.
     */
    public static <O extends Obj> O parseMulti(final String code) {
        final String trimmed = code.trim();
        if (trimmed.isEmpty())
            return (O) noobj();

        final List<Inst> allInsts = new ArrayList<>();
        String remaining = trimmed;
        boolean needsEnd = false;
        int iterations = 0;

        while (!remaining.isBlank()) {
            if (++iterations > maxParseIterations) {
                throw MTronException.of("infinite recursion detected in parser: parseMulti() exceeded %d iterations on '%s'", maxParseIterations, remaining);
            }
            remaining = remaining.trim();

            // Consume leading ; separators.  We defer the actual end()
            // insertion until an expression follows, so that trailing ; does
            // not produce a spurious end().
            if (remaining.startsWith(";")) {
                if (!allInsts.isEmpty()) needsEnd = true;
                remaining = remaining.substring(1).trim();
                continue;
            }

            final Result result;
            try {
                result = cachedExpressionParser.parse(remaining);
            } catch (final StackOverflowError e) {
                throw MTronException.of("infinite recursion detected in parser: possible left recursion in '%s'", remaining);
            }
            if (result.isFailure()) {
                if (!allInsts.isEmpty()) break;   // partial parse, return what we have
                throw MTronException.of(formatParseError(result));
            }

            final Obj parsed = result.get();
            final int consumed = result.getPosition();

            // Guard: if the parser succeeded but consumed zero characters we
            // would loop forever on the same remaining string.
            if (consumed == 0) {
                throw MTronException.of("infinite recursion detected in parser: parser consumed 0 characters at '%s'", remaining);
            }
            remaining = remaining.substring(consumed);

            // Skip comments — m_comment() returns noobj(), and a comment-only
            // segment should not insert an end() separator or become a statement.
            if (parsed.isNoObj()) continue;

            if (needsEnd) {
                allInsts.add(instB(END_INST_TID, lst()));
                needsEnd = false;
            }
            appendToInstList(allInsts, parsed);
        }

        // Strip trailing end() instructions — they come from trailing ;
        // that the parser consumed as sugar but have no subsequent expression.
        while (!allInsts.isEmpty()
                && END_INST_TID.equals(allInsts.get(allInsts.size() - 1).tid().basePath())) {
            allInsts.remove(allInsts.size() - 1);
        }

        if (allInsts.isEmpty())
            return (O) noobj();
        if (allInsts.size() == 1) {
            final Inst single = allInsts.get(0);
            // Unwrap start(x) wrappers introduced by appendToInstList for bare
            // values — a single expression should return the same shape as parse().
            if (START_INST_TID.equals(single.tid().basePath())
                    && single.args().count() == 1) {
                return (O) single.args().lstValue().get(0);
            }
            return (O) single;
        }
        return (O) MCode.of(allInsts, CODE_TID, null).tryToInst();
    }

    /**
     * Splits a parsed Code into independently executable segments at
     * {@code end()} instruction boundaries. {@code end()} instructions are
     * discarded — they serve only as separators between statements.
     */
    public static List<Code> splitCodeAtEnd(final Code code) {
        final List<Code> segments = new ArrayList<>();
        final List<Inst> current = new ArrayList<>();
        for (final Inst inst : code.jvm()) {
            if (END_INST_TID.equals(inst.tid().basePath())) {
                if (!current.isEmpty()) {
                    segments.add(MCode.of(new ArrayList<>(current)));
                    current.clear();
                }
            } else {
                current.add(inst);
            }
        }
        if (!current.isEmpty()) {
            segments.add(MCode.of(new ArrayList<>(current)));
        }
        return segments;
    }

    /**
     * Appends the instructions from an Obj (Code, Inst, or plain value) to the given list.
     */
    private static void appendToInstList(final List<Inst> insts, final Obj obj) {
        if (null == obj || obj.isNoObj()) return;
        if (obj.isCode()) {
            insts.addAll(obj.asCode().codeValue());
        } else if (obj.isInst()) {
            insts.add(obj.as());
        } else {
            insts.add(instB(START_INST_TID, lst(obj)));
        }
    }

    public static Parser m_comment() {
        return choice(
                seq(of("[--").trim(), any().starGreedy(of("--]")), of("--]").trim()),
                seq(of("[==").trim(), any().starGreedy(of("==]")), of("==]").trim())).trim().map(t -> noobj());
    }

    public static Parser m_furi() {
        return m_furi(FULL_FURI_CHARS, true, true, true);
    }

    public static Parser m_furi_no_query() {
        return m_furi(FULL_FURI_CHARS, false, true, false);
    }

    private static Parser m_furi_internal(final String furiCharacterSet, final boolean polynomial, final boolean coefficient, final boolean query) {
        // Content parser: matches words, template expressions ${...}, or furi characters
        // m_furi_template() is tried first to capture ${...} as atomic units
        final Parser contentParser = seq(word().or(m_furi_template()).or(seq(of("::").not(), of("->").not(), anyOf(furiCharacterSet)))).plus().flatten();
        return seq(of('-').not(), contentParser,
                opt(polynomial ? m_furi_poly_type() : none(), null),
                opt(coefficient ? m_furi_coefficient() : none(), null),
                opt(query ? m_furi_query() : none(), null)).map(t -> f(pick(t, 1)).poly(pick(t, 2)).c(cInt.of((String) pick(t, 3))).qString(pick(t, 4)));
    }

    public static Parser m_furi(final String furiCharacterSet, final boolean polynomial, final boolean coefficient, final boolean query) {
        return choice(
                of("{0}").trim().map(t -> NOOBJ),
                seq(of('<'), m_furi_internal(FULL_FURI_CHARS, polynomial, coefficient, query), of('>')).pick(1),
                seq(of("<>").trim()).map(x -> empty()),
                m_furi_internal(furiCharacterSet, polynomial, coefficient, query));
    }

    public static Parser m_furi_poly_type() {
        return seq(of('[').trim(),
                seq(m_furi(REDUCED_FURI_CHARS, false, true, false), opt(seq(of("=>").trim(), m_furi(REDUCED_FURI_CHARS, false, true, false)), "")).flatten()
                        .separatedBy(of(',').trim()),
                of(']').trim())
                .map(t -> ((List) (pick(t, 1))).stream().filter(c -> !c.equals(',')).map(Object::toString).toList());
    }

    public static Parser m_furi_coefficient() {
        return seq(of('{'), choice(
                        seq(opt(seq(opt(of('-'), ""), digit().plus()), ""), of(','), opt(seq(opt(of('-'), ""), digit().plus()), "")).flatten(),
                        seq(opt(of('-'), ""), digit().plus()).flatten().map(t -> t + "," + t),
                        of(","),
                        of("**"),
                        of("-*"),
                        of("-?"),
                        of('*'),
                        of('+'),
                        of('-'),
                        of("??"),
                        of('?')),
                of('}')).map(t -> pick(t, 1).toString());
    }

    /**
     * Parser for template expressions ${...} within fURIs.
     * Matches ${expr} where expr can be any Metatron expression (including function calls).
     * Returns the full ${...} string to be preserved in the fURI.
     */
    public static Parser m_furi_template() {
        // Match ${ followed by any characters (including nested parens) until }
        // We need to handle balanced braces, but for simplicity we'll match non-} chars
        // For expressions with nested braces like ${[a=>b]}, we'd need more complex parsing
        return seq(of("${"), noneOf("}").star().flatten(), of("}"))
                .map(t -> "${" + pick(t, 1) + "}");
    }

    public static Parser m_furi_query() {
        // Query parser: handles dom<=rng, key=value pairs, and ${...} template expressions
        return seq(of("?"), seq(
                opt(m_furi_inst_dom_rng(), ""),
                opt(of("&"), ""),
                opt(choice(
                        m_furi_template(), // Template expressions like ${[q=>hello]}
                        seq((word().or(anyOf("+#_-"))).plus(), opt(seq(of("="), choice(m_furi_no_query(), word().or(anyOf(FULL_FURI_CHARS)).star())), ""))
                ).separatedBy(of("&")), "").flatten())
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
                .map(t -> mParser.<fURI>pick(t, 0).qString(mParser.pick(t, 1)));
    }

    public static Parser m_obj(final boolean allowParens) {
        return allowParens ? m_paren_wrap(obj_parser) : obj_parser;
    }

    public static Parser m_obj() {
        return mParser.m_obj(true);
    }

    public static Parser m_obj_no_sugar() {
        return obj_no_call_parser.or(inst_parser);
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
        return (null == baseType) ? opt(seq(m_furi(REDUCED_FURI_CHARS, true, true, true), of("://").not(), of(":").repeat(2, Integer.MAX_VALUE)).pick(0), baseType) :
                opt(choice(seq(m_furi(REDUCED_FURI_CHARS, true, true, true), of("://").not(), of(":").repeat(2, Integer.MAX_VALUE)).pick(0), m_furi_coefficient().map(t -> {
                    try {
                        return baseType.c(cInt.of((String) t));
                    } catch (Exception e) {
                        return null;
                    }
                })), baseType);
    }

    public static Parser m_vid_postfix() {
        return opt(seq(of('@'), m_furi(REDUCED_FURI_CHARS, true, false, false)).map(t -> pick(t, 1)), null);
    }

    public static Parser m_fail() {
        return seq(choice(of("fail"), of(FAIL_TID.toString())), of("::"), seq(of('[').trim(), m_obj(), of(']').trim()).map(t -> pick(t, 1)).plus(), m_vid_postfix())
                .map(t -> {
                    final Object test = pick(t, 2);
                    final List<Obj> objs = test instanceof List ? ((List) test) : (List) List.of(test);
                    Fail root = null;
                    for (final Obj obj : objs) {
                        root = null == root ? fail(MTronException.of(obj.toString())) : fail(MTronException.of(obj.toString()), root);
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
                map(t -> bytes(ByteBuffer.wrap(HexFormat.of().parseHex(mParser.<String>pick(t, 2))), pick(t, 0), pick(t, 3)));
    }

    public static Parser m_int() {
        return seq(m_type_prefix(INT_TID), seq(opt(of('-'), '+'), choice(of('0'), digit().plus()))
                .flatten().trim(), m_vid_postfix())
                .map(t -> jnt(Long.parseLong(pick(t, 1).toString()), pick(t, 0), pick(t, 2)));
    }

    public static Parser m_real() {
        return seq(m_type_prefix(REAL_TID), seq(opt(of('-'), '+'), choice(of('0'), digit().plus()), of('.'), digit().plus(), opt(seq(of("E"), opt(of("-"), ""), digit().plus()), ""))
                .flatten().trim(), m_vid_postfix())
                .map(t -> new MReal(Double.parseDouble(pick(t, 1).toString()), pick(t, 0), pick(t, 2)));
    }

    public static Parser m_str() {
        final Parser sqInner = noneOf("'\\").or(of("\\").seq(any()));
        final Parser dqInner = noneOf("\"\\").or(of("\\").seq(any()));
        final Parser singleQuote = seq(of('\''), sqInner.starLazy(of('\'')), of('\'')).flatten().map(t -> t.toString().substring(1, t.toString().length() - 1));
        final Parser doubleQuote = seq(of('"'), dqInner.starLazy(of('"')), of('"')).flatten().map(t -> t.toString().substring(1, t.toString().length() - 1));
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
        return seq(m_type_prefix(null), of('T'),
                opt(seq(of('['), opt(m_obj(), null), of(']')).map(t -> pick(t, 1)), null),
                opt(seq(of('['), opt(m_obj(), null), of(']')).map(t -> pick(t, 1)), null),
                m_vid_postfix())
                .map(t -> T(Tuple.Pair.with(pick(t, 2), pick(t, 3)), pick(t, 0), pick(t, 4)));
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
        // Use cached sugar parser to avoid creating new array on every call
        return cachedSugarParser == null ? inst_parser : cachedSugarParser.or(inst_parser);
    }

    /// //////////////////////////////////////////////////////////////////////////////////////////
    /// ///////////////////////////////////// SUGAR PARSERS //////////////////////////////////////
    /// //////////////////////////////////////////////////////////////////////////////////////////

    private static Parser sugar_args(boolean endToken) {
        if (endToken)
            return m_paren_wrap(obj_rel_back_parser);
        return choice(seq(of("(").trim(), obj_rel_back_parser, of(")").trim()).map(t -> pick(t, 1)), obj_rel_back_parser2);
    }

    private static Parser generate_sugar_parser(final fURI tid, final Parser startToken, final int argCount) {
        return generate_sugar_parser(tid, startToken, argCount, null);
    }

    private static Parser generate_sugar_parser(final List<fURI> instChain, final Parser startToken, final int argCount, final Parser endToken) {
        // TODO: look into ExpressionBuilder for handling paren wrapping properly.
        if (instChain.size() == 1) {
            if (null != endToken && argCount > 1) {
                return seq(startToken.trim(), opt(seq(of('?'), m_furi_inst_dom_rng()).map(t -> pick(t, 1)), null), m_paren_wrap(obj_rel_back_parser), endToken.trim(), m_paren_wrap(obj_rel_back_parser))
                        .map(t -> instB(instChain.getFirst().qString(pick(t, 1)), lst(rec(mParser.<Obj>pick(t, 2), mParser.<Obj>pick(t, 4)))));
            } else
                return null == endToken ?
                        generate_sugar_parser(instChain.getFirst(), startToken, argCount) :
                        generate_sugar_parser(instChain.getFirst(), startToken, argCount, endToken);
        }
        return (argCount == 0 ?
                seq(startToken.trim(), opt(seq(of('?'), m_furi_inst_dom_rng()).map(t -> pick(t, 1)), null)).map(t -> instB(instChain.getFirst(), lst(MInst.instA(instChain.get(1).qString(pick(t, 1)))))) :
                seq(startToken.trim(), opt(seq(of('?'), m_furi_inst_dom_rng()).map(t -> pick(t, 1)), null), sugar_args(null != endToken), null == endToken ? of("") : endToken.trim())
                        .map(t -> instB(instChain.getFirst(), lst(instB(instChain.get(1).qString(pick(t, 1)), lst(mParser.<Obj>pick(t, 2))))))).trim();
    }

    private static Parser generate_sugar_parser(final fURI tid, final Parser startToken, final int argCount, final Parser endToken) {
        // TODO: look into ExpressionBuilder for handling paren wrapping properly.
        return (argCount == 0 ?
                seq(startToken.trim(), opt(seq(of('?'), m_furi_inst_dom_rng()).map(t -> pick(t, 1)), null)).map(t -> MInst.instA(tid.qString(pick(t, 1)))) :
                seq(startToken.trim(), opt(seq(of('?'), m_furi_inst_dom_rng()).map(t -> pick(t, 1)), null), sugar_args(null != endToken), null == endToken ? of("") : endToken.trim())
                        .map(t -> instB(tid.qString(pick(t, 1)), lst(mParser.<Obj>pick(t, 2)))));
    }


    public static final Pattern BLOCK_COMMENT_PATTERN = Pattern.compile("(\\[==).*?(==])", Pattern.DOTALL);
    public static final Pattern LINE_COMMENT_PATTERN = Pattern.compile("(\\[--).*?(--])", Pattern.DOTALL);

    public static String removeBlockComments(final String source) {
        return BLOCK_COMMENT_PATTERN.matcher(source).replaceAll("");
    }

    public static String removeLineComments(final String line) {
        return LINE_COMMENT_PATTERN.matcher(line).replaceAll("");
    }

    public record FileParseError(int lineNumber, String lineString, Exception parseException) {
    }

    /**
     * Result of a non-throwing parse via {@link #parseDiagnose(String)}.
     * On success, {@code result} holds the parsed object.  On failure,
     * {@code line}/{@code column}/{@code position} locate the error,
     * {@code buffer} contains the full input, and {@code message} is a
     * simplified human-readable explanation.
     */
    public record ParseDiagnostic(Obj result, int line, int column, int position,
                                  String buffer, String message, boolean success) {
        public static ParseDiagnostic ok(final Obj result) {
            return new ParseDiagnostic(result, 0, 0, -1, "", "", true);
        }

        /**
         * Returns the formatted error string (same format as
         * {@link #parse(String)} throws), or {@code null} on success.
         */
        public String formatted() {
            return success ? null : formatParseError(this);
        }
    }

    /**
     * Formats a {@link ParseDiagnostic} failure the same way
     * {@link #parse(String)} formats parse errors.
     */
    private static String formatParseError(final ParseDiagnostic diag) {
        final int contextRadius = 40;
        final int snippetStart = Math.max(0, diag.position - contextRadius);
        final int snippetEnd = Math.min(diag.buffer.length(), diag.position + contextRadius);
        final String prefix = snippetStart > 0 ? "..." : "";
        final String suffix = snippetEnd < diag.buffer.length() ? "..." : "";
        final String snippet = prefix
                + diag.buffer.substring(snippetStart, snippetEnd).replace("\n", "\\n").replace("\r", "\\r")
                + suffix;
        final int caretOffset = diag.position - snippetStart + (snippetStart > 0 ? 3 : 0);
        final String caretLine = " ".repeat(caretOffset) + "^";
        return String.format("parse error at line %d, col %d:\n  %s\n  %s\n  %s",
                diag.line, diag.column, snippet, caretLine, diag.message);
    }

    private static String aggregateTillBlock(final List<String> lines, final int start, final String prefix) {
        final StringBuilder builder = new StringBuilder();
        for (final String line : lines.subList(start, lines.size() - 1)) {
            builder.append(prefix).append(line).append("\n");
            if (line.contains(";"))
                break;
        }
        if (builder.isEmpty())
            builder.append("\n");
        return builder.deleteCharAt(builder.length() - 1).toString();
    }

    public static Stream<Obj> eval(final File file, final Consumer<FileParseError> exhandler) throws IOException {
        final List<String> lines = Files.readAllLines(file.toPath());
        final String source = lines.stream().reduce("", (a, b) -> a + b + "\n");
        final AtomicInteger lineNumber = new AtomicInteger(0);
        return splitOnNonQuotedSequence(source, ';', false).stream()
                .map(s -> Arrays.stream(s.split("\n"))
                        .peek(x -> lineNumber.incrementAndGet())
                        .map(mParser::removeBlockComments)
                        .map(mParser::removeLineComments)
                        .filter(x -> !x.isBlank())
                        .reduce("", (a, b) -> a + b + "\n"))
                .peek(s -> LOG.debug("evaluating line: %s", s))
                .map(s -> {
                    try {
                        return mParser.parse(s).apply();
                    } catch (final Exception e) {
                        exhandler.accept(new FileParseError(lineNumber.get(), aggregateTillBlock(lines, lineNumber.get(), "{{r}}> "), e));
                        return noobj();
                    }
                })
                .filter(o -> !o.isNoObj());
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

    public static ChoiceParser choice(final List<Parser> parsers) {
        return new ChoiceParser(parsers.toArray(new Parser[parsers.size()]));
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
