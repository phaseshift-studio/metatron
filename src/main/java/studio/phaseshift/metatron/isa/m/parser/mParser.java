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

import org.petitparser.context.Context;
import org.petitparser.context.Failure;
import org.petitparser.context.Result;
import org.petitparser.context.Token;
import org.petitparser.parser.Parser;
import org.petitparser.parser.combinators.ChoiceParser;
import org.petitparser.parser.combinators.OptionalParser;
import org.petitparser.parser.combinators.SequenceParser;
import org.petitparser.parser.combinators.SettableParser;
import org.petitparser.parser.primitive.CharacterParser;
import org.petitparser.utils.FailureJoiner;
import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Sugar;
import studio.phaseshift.metatron.isa.m.mInstSet;
import studio.phaseshift.metatron.isa.m.math.cat.catInstSet;
import studio.phaseshift.metatron.isa.m.type.Call;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.impl.*;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.petitparser.parser.primitive.CharacterParser.any;
import static org.petitparser.parser.primitive.CharacterParser.anyOf;
import static org.petitparser.parser.primitive.CharacterParser.digit;
import static org.petitparser.parser.primitive.CharacterParser.noneOf;
import static org.petitparser.parser.primitive.CharacterParser.of;
import static org.petitparser.parser.primitive.CharacterParser.word;
import static org.petitparser.parser.primitive.StringParser.of;
import static studio.phaseshift.metatron.Tokens.NOOBJ_TID;
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
import static studio.phaseshift.metatron.util.Tuple.Triplet;

public class mParser {

    private static final SettableParser furi_parser = SettableParser.undefined();
    private static final GraphittyLogger LOG = Graphitty.log(mParser.class);

    /**
     * The choice failure that got the farthest.  The child that came closest
     * to succeeding is where the attempt died, so its failure — position
     * and message (labeled or not) — is what survives to the reporter,
     * instead of the arbitrary last branch's.
     * Declared before the static parser build so the initializers see a
     * live joiner.
     */
    /**
     * Every expectation label the grammar declares (see {@link #labeled}). Declared before the
     * static rule build: fields initialize in textual order, and the rules register their labels
     * as they are built.
     */
    private static final Set<String> LABELS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * The closers are labels too, but they say only "this token was expected here". When a closer and
     * a declared expectation fail at the same offset, the expectation is the real explanation
     * ("a value for the query parameter"), so the join below prefers it.
     */
    private static final Set<String> CLOSERS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static final FailureJoiner FURTHEST = (final Failure a, final Failure b) -> {
        if (a.getPosition() != b.getPosition())
            return a.getPosition() > b.getPosition() ? a : b;
        final boolean aExpectation = LABELS.contains(a.getMessage()) && !CLOSERS.contains(a.getMessage());
        final boolean bExpectation = LABELS.contains(b.getMessage()) && !CLOSERS.contains(b.getMessage());
        if (aExpectation != bExpectation)
            return aExpectation ? a : b;
        return a;
    };
    /**
     * What may follow a complete expression: another obj — which includes noobj, so it also covers
     * the source ending here. Stated as the expectation rather than as a token.
     */
    private static final String EXPECTED_OBJ = "expected an obj";

    private static final SettableParser obj_parser = new MemoizedSettableParser();
    private static final SettableParser obj_no_code_parser = new MemoizedSettableParser();
    private static final SettableParser obj_no_call_parser = new MemoizedSettableParser();
    private static final SettableParser lst_parser = new MemoizedSettableParser();
    private static final SettableParser rec_parser = new MemoizedSettableParser();
    public static final SettableParser inst_parser = new MemoizedSettableParser();
    private static final SettableParser rel_parser = new MemoizedSettableParser();
    private static final SettableParser obj_rel_back_parser = new MemoizedSettableParser();
    private static final SettableParser obj_rel_back_parser2 = SettableParser.undefined();
    private static final SettableParser and_or_parser = new MemoizedSettableParser();
    // private static final SettableParser branch_parser = SettableParser.undefined();
    // Space-aware operator parsing:
    // Sugars defined with spaces (e.g., " * ") require those spaces in the source code.
    // Sugars without spaces (e.g., "*") are space-insensitive (use .trim()).
    // This allows: *x → from(x) and x * y → x.mult(y) to coexist.
    private static final LinkedHashSet<Parser> PARSERS = new LinkedHashSet<>();
    private static Parser cachedSugarParser = null;
    private static Parser cachedExpressionParser = null;

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
    //   - parse() records its start time in a
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
    private static final String QMAP_VALUE_CHARS = FULL_FURI_CHARS + "\"\\'";
    private static final String QMAP_POLY_VALUE_CHARS = QMAP_VALUE_CHARS + ">,=";

    static {
        LABELS.add(EXPECTED_OBJ);
        new mInstSet().sugars().forEach(mParser::addSugar);
        new catInstSet().sugars().forEach(mParser::addSugar);
        // Cache the sugar parser after all sugars are loaded
        cachedSugarParser = PARSERS.isEmpty() ? null : choice(PARSERS.toArray(new Parser[0]));
        // Initialize all parsers first
        furi_parser.set(seq(word().or(seq(of("::").not(),
                        anyOf(REDUCED_FURI_CHARS))).plus().flatten(),
                opt(m_furi_poly_type(), null),
                opt(m_furi_coefficient(), null),
                opt(none(), null)).map(t -> f(pick(t, 0)).poly(pick(t, 1)).c(coefficientOrOne(pick(t, 2))).qString(pick(t, 3))));
        rel_parser.set(seq(m_type_prefix(Tokens.REL_TID), m_paren_wrap(seq(obj_rel_back_parser, of("=>").trim(), m_obj()))).map(t -> rel(Tuple.Pair.with(pick(pick(t, 1), 0), pick(pick(t, 1), 2)), pick(t, 0), null)));
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
        lst_parser.set(seq(m_type_prefix(Tokens.LST_TID),
                of('[').trim(),
                labeled(lst_internal(), "an lst entry — a value, or an inst"),
                labeledCloser(of(']').trim(), "']' to close the lst"),
                m_vid_postfix())
                .map(t -> new MLst(pick(t, 2), pick(t, 0), pick(t, 4))));

        rec_parser.set(seq(m_type_prefix(Tokens.REC_TID), of('[').trim(), labeled(rec_internal(obj_rel_back_parser, m_call_prefix(MAP_INST_TID)), "a poly entry (a lst or a rec) — a value, key=>value, ',' or ']'"), labeledCloser(of(']').trim(), "']' to close the rec"), m_vid_postfix()).trim().map(t -> rec((Map<Obj, Obj>) pick(t, 2), pick(t, 0), pick(t, 4))));
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

        // A source is a sequence of expressions, and ';' does NOT separate them in the
        // grammar — it is a prefix sugar for end() (mInstSet: Sugar.prefix(";", ...)), so the
        // expression that precedes it consumes it and the statement boundary is already
        // decided by the time the expression returns. There is therefore nothing here for a
        // rule to own: the walk over expressions in parse() IS the source rule,
        // and it stays a walk only until ';' becomes a separator token rather than an
        // expression.
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
        return seq(m_obj_no_sugar(), of(token).trim(),
                labeled(m_obj(), "a right operand after '%s'".formatted(token))).map(t -> {
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
                seq(of('(').trim(), choice(rec_internal(m_furi().map(t -> ((fURI) t).toUri()), m_call_prefix(MAP_INST_TID)), lst_internal(), of("")).trim(), labeledCloser(of(')').trim(), "')' to close the inst args"), of('{').not()).pick(1), // 1 inst_args
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
                choice(m_inst_furi(), m_type_prefix(Tokens.M_ISA_INST_TID)), // 0 inst_tid
                seq(of('(').trim(), choice(rec_internal(m_furi().map(t -> ((fURI) t).toUri()), m_call_prefix(MAP_INST_TID)), lst_internal(), of("")).trim(), labeledCloser(of(')').trim(), "')' to close the inst args")).pick(1), // 1 inst_args
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

    private static final Pattern MTRON_COMMENT = Pattern.compile(
            "\\[--.*?(--]|$)|\\[==[\\s\\S]*?==]", Pattern.MULTILINE);

    private static List<int[]> commentRegions(final String input) {
        // Blank the interiors of quoted spans in a same-length copy so the
        // comment regex cannot see comment markers that live inside string
        // literals; the matched regions then delete from the original.
        // Quote spans follow the same rules as the splitter this replaced:
        // an odd run opens a span ('...', "...", """..."""), an even run is
        // empty-string literals ("") and opens nothing.
        final char[] masked = input.toCharArray();
        int quoteCount = 0;
        char quoteChar = 0;
        boolean escaped = false;
        final int length = masked.length;
        for (int i = 0; i < length; i++) {
            final char c = masked[i];
            if (escaped) {
                if (quoteCount != 0)
                    masked[i] = ' ';
                escaped = false;
                continue;
            }
            if (c == '\\') {
                if (quoteCount != 0)
                    masked[i] = ' ';
                escaped = true;
                continue;
            }
            if (c == '\'' || c == '"') {
                int run = 0;
                while (i + run < length && masked[i + run] == c)
                    run++;
                if (quoteCount == 0) {
                    if (run % 2 != 0) {
                        quoteCount = run;
                        quoteChar = c;
                    }
                } else if (quoteChar == c && run >= quoteCount) {
                    quoteCount = 0;
                    quoteChar = 0;
                }
                i += run - 1;
                continue;
            }
            if (quoteCount != 0)
                masked[i] = ' ';
        }
        // Find comment regions on the masked text (identical indices).
        final List<int[]> regions = new ArrayList<>();
        final Matcher matcher = MTRON_COMMENT.matcher(new String(masked));
        while (matcher.find())
            regions.add(new int[]{matcher.start(), matcher.end()});
        return regions;
    }

    /**
     * {@code input} with its comment regions deleted. Every index after the first comment
     * shifts, so this is a buffer for <em>parsing</em>, never for locating anything in the
     * source — use {@link #prepare(String)} when a position has to be reported.
     */
    public static String stripComments(final String input) {
        final List<int[]> regions = commentRegions(input);
        if (regions.isEmpty())
            return input;
        final StringBuilder result = new StringBuilder(input);
        for (int r = regions.size() - 1; r >= 0; r--)
            result.delete(regions.get(r)[0], regions.get(r)[1]);
        return result.toString();
    }

    /**
     * The source as the parser consumes it, plus the map back to the text it came from.
     * The grammar reads a buffer with comments deleted and the ends trimmed, which shifts
     * every index past the first comment; a diagnostic reported in those coordinates points
     * at the wrong place in the file, and no caller can recover the difference afterwards.
     * So the preparation keeps the association: {@link #toOriginal(int)} turns any index the
     * grammar produced back into a source offset.
     *
     * @param original  the source exactly as the caller handed it over
     * @param text      what the grammar parses — comments deleted, ends trimmed
     * @param offsets   {@code offsets[i]} is the original index of {@code text[i]}
     * @param endOffset the original index just past the last prepared character
     */
    private record Prepared(String original, String text, int[] offsets, int endOffset) {

        /**
         * A prepared source whose text is already the coordinate space — for a buffer a
         * combinator produced itself, where there is nothing to map back to.
         */
        static Prepared identity(final String buffer) {
            final int[] offsets = new int[buffer.length()];
            for (int i = 0; i < offsets.length; i++)
                offsets[i] = i;
            return new Prepared(buffer, buffer, offsets, buffer.length());
        }

        /**
         * The source offset of {@code index} in {@link #text}. An index at or past the end
         * of the prepared text is clamped to {@link #endOffset}, so a failure at end of input
         * lands where the last character of the expression sat.
         */
        int toOriginal(final int index) {
            return index < this.offsets.length ? this.offsets[index] : this.endOffset;
        }
    }

    /**
     * Prepare {@code code} for parsing — the same text {@code trim()} and
     * {@link #stripComments(String)} produced before, now carrying the index map.
     */
    private static Prepared prepare(final String code) {
        PARSE_GENERATION.incrementAndGet();   // a new source → a new memo epoch
        final List<int[]> regions = commentRegions(code);
        int start = 0;
        int end = code.length();
        while (start < end && Character.isWhitespace(code.charAt(start)))
            start++;
        while (end > start && Character.isWhitespace(code.charAt(end - 1)))
            end--;
        final StringBuilder text = new StringBuilder(end - start);
        final int[] offsets = new int[end - start];
        int kept = 0;
        int region = 0;
        for (int i = start; i < end; i++) {
            while (region < regions.size() && regions.get(region)[1] <= i)
                region++;
            if (region < regions.size() && regions.get(region)[0] <= i)
                continue;                                    // inside a comment — skip it
            text.append(code.charAt(i));
            offsets[kept++] = i;
        }
        final int[] keptOffsets = kept == offsets.length ? offsets : Arrays.copyOf(offsets, kept);
        // THE TRIM COMES AFTER THE COMMENTS. `x   [-- note --]` ends in ']', so the raw trim above
        // does nothing; once the comment is deleted, the spaces before it are still in the text and
        // reach the grammar. Trim the prepared text again, and report end-of-input at the last KEPT
        // character — the raw end is inside the comment that was deleted.
        int from = 0;
        int to = kept;
        while (from < to && Character.isWhitespace(text.charAt(from)))
            from++;
        while (to > from && Character.isWhitespace(text.charAt(to - 1)))
            to--;
        final int endOffset = 0 == to ? end : keptOffsets[to - 1] + 1;
        return new Prepared(code, text.substring(from, to),
                Arrays.copyOfRange(keptOffsets, from, to), endOffset);
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
        return seq(opt(objParser, null), opt(of(".").trim(), '.'),
                opt(labeled(m_code(), "an instruction name after '.' (e.g. plus, map, from)"), null),
                m_vid_postfix()).map(t -> {
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
            return MCode.of(newCode, Tokens.CODE_TID, pick(t, 3)).tryToInst();
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
                        collectRec((List) t));
    }

    /**
     * Turns the separatedBy pair list into a rec.  A pair with a null key or value
     * is a malformed entry (a partial parse left a hole behind) — previously this
     * fell through to Collectors.toMap and died with a bare NullPointerException
     * (null message) deep inside HashMap.merge; now it surfaces as a diagnostic
     * naming the offending entry instead.
     */
    private static Map<Obj, Obj> collectRec(final List<?> pairs) {
        final Map<Obj, Obj> m = new LinkedHashMap<>();
        for (final Object o : pairs) {
            if (!(o instanceof List kv))
                continue;
            final Obj key = pick(kv, 0);
            final Obj value = pick(kv, 2);
            if (null != key && null != value) {
                m.merge(key, value, Obj::append);
                //throw MTronException.of("rec entry malformed (missing key or value): %s", kv);
            }

        }
        return m;
    }

    /**
     * Formats a PetitParser failure against the source it came from: the snippet, the caret,
     * and the line:column are all in {@code prepared.original()} coordinates, while the
     * explanation is still derived from the prepared text the grammar actually read — the
     * position heuristics below only know about the characters the parser saw, and feeding them
     * the comment-bearing original would make them reason about text the grammar never read.
     */
    private static String formatParseError(final Result failure, final Prepared prepared) {
        final String buffer = prepared.original();
        final int pos = prepared.toOriginal(failure.getPosition());
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
        final String simplified = simplifyParseMessage(rawMsg, failure.getBuffer(), failure.getPosition(), snippet);

        return String.format("parse error at line %d, col %d:\n  %s\n  %s\n  %s",
                lc[0], lc[1], snippet, caretLine, simplified);
    }

    /**
     * Failure labels written at expectation points ({@link #labeled}).
     * These are already the designed text — simplifyParseMessage passes them
     * through verbatim; the position heuristics that follow would otherwise
     * rewrite them into generic bracket talk.
     */

    private static String simplifyParseMessage(final String rawMsg, final String buffer,
                                               final int pos, final String snippet) {
        // a rule that declared what it expected has already said the best thing it can say
        if (null != rawMsg && LABELS.contains(rawMsg))
            return rawMsg;
        if (pos >= buffer.length())
            return "unexpected end of source — the expression is incomplete";
        return "unexpected '%s'".formatted(buffer.charAt(pos));
    }
    /**
    /**
     * The one door: mtron source in, an {@link Obj} out.
     * <p>
     * A source is one expression or a {@code ;}-separated sequence of them: one statement parses to that statement's obj, several parse to
     * a {@code code::T} whose statements are separated by {@code end()}. Nothing else splits a
     * source — a file, a command argument, a REPL line and a boot script all arrive here.
     * <p>
     * Failure is a value, not an exception: {@link Obj#isFail()} is true on the result, and its
     * message is a position-oriented explanation — source line and column, the offending
     * text, a caret, and what was expected. {@link #faultOf(Obj)} returns that same information
     * as data. It is a {@code fail::T} rather than a throw because the rest of the VM already
     * carries failure that way (a fail propagates through application and is caught at a
     * boundary); the parser was the one component that rendered its failure into a sentence at
     * the point of failure, where nothing downstream could use it.
     *
     * @param mtron source text — an expression, or a source of them
     * @return the parsed obj, or a {@code fail::T} describing where and why it stopped
     */
    /** A brace directly on a type name, followed by {@code ::T}: a coefficient, never a branch. */
    private static final Pattern TYPE_COEFFICIENT =
            Pattern.compile("([A-Za-z0-9_\\-\\.]+)\\{([^{}]*)\\}\\s*::\\s*T");

    /** One step of the walk: the statement, where the next one starts, or the fault. */
    private record Step(Obj statement, int next, Obj fault) {
    }

    /**
     * Parse ONE statement from {@code position}, given the statement before it. The adjacency rule
     * lives here: two expressions may only follow one another when the first one ended a statement
     * (the ';' sugar records that as an {@code end()} instruction).
     */
    private static Step step(final Prepared prepared, final int position, final Obj previous) {
        final String text = prepared.text();
        final Result result;
        try {
            result = cachedExpressionParser.parseOn(new Context(text, position));
        } catch (final StackOverflowError e) {
            return new Step(null, position,
                    parseFail(prepared, position, "infinite recursion in the parser — possible left recursion"));
        } catch (final MTronException e) {
            // a construction-time type violation (nat::-3): the grammar read the value and the
            // value's type rejected it. It is already a mtron failure; keep its text, name the
            // source it came from, and locate it.
            return new Step(null, position, parseFail(prepared, position,
                    "%s%n\twhile parsing: %s".formatted(e.getMessage(), text)));
        }
        if (result.isFailure())
            return new Step(null, position, parseFail(prepared, result));
        if (result.getPosition() <= position) {
            // m_call_prefix is a sequence of OPTIONAL parts, so it can "succeed" without consuming
            // anything. Ask the value rule what it objected to and report that instead.
            final Result probe = m_obj(false).parseOn(new Context(text, position));
            return new Step(null, position, parseFail(prepared, probe.isFailure() ? probe : result));
        }
        if (null != previous && !endsStatement(previous))
            return new Step(null, position, parseFail(prepared, new Context(text, position)
                    .failure(EXPECTED_OBJ, position)));
        return new Step(result.get(), result.getPosition(), null);
    }

    /*
     * THE WALK IS SHARED. parse() collects every statement and assembles one obj; the boot runner
     * reads one statement, applies it, then reads the next — which it must, because a statement's
     * CONSTRUCTION can have effects (a `@vid` write mounts a space) that the next statement needs.
     */
    public static Obj parse(final String mtron) {
        final Prepared prepared = prepare(mtron);
        final String text = prepared.text();
        if (text.isEmpty())
            return noobj();
        // the ::T says this is a type position, so the brace on the type name is a coefficient: a
        // body cInt does not recognize is a malformed coefficient, not a branch segment
        final Matcher coefficients = TYPE_COEFFICIENT.matcher(text);
        while (coefficients.find()) {
            try {
                // an empty body is not a coefficient either: there is no "the coefficient is nothing"
                if (coefficients.group(2).isBlank())
                    throw new IllegalArgumentException("empty coefficient body");
                cInt.of(coefficients.group(2));
            } catch (final Exception e) {
                return parseFail(prepared, prepared.toOriginal(coefficients.start(2)),
                        "a coefficient body — {int,int}, {,int}, {int,}, {int}, or a sugar encoding such as {*}, {**}, {?}, {??}, {+}");
            }
        }
        final List<Obj> statements = new ArrayList<>();
        Obj previous = null;
        int position = 0;
        while (position < text.length()) {
            final Step step = step(prepared, position, previous);
            if (null != step.fault())
                return step.fault();
            statements.add(step.statement());
            previous = step.statement();
            position = step.next();
        }
        return assemble(statements);
    }

    /**
     * Read a source one statement at a time. The boundaries are the parser's own — a statement ends
     * where the grammar ended it, which is where {@code ;} put an {@code end()} — so a {@code ';'}
     * inside a {@code str::T}, a comment or a uri never cuts one in half.
     * <p>
     * This is what a caller that must interleave parsing with execution loops over: parse one
     * statement, apply it, then read the next. Parsing a whole source first is only safe when
     * construction has no effects, which is not yet true in metatron.
     */
    public static final class Incremental {

        private final Prepared prepared;
        private int position;
        private Obj previous;
        private Obj fault;

        private Incremental(final String source) {
            this.prepared = prepare(source);
            this.position = 0;
            this.previous = null;
            this.fault = null;
        }

        public boolean hasNext() {
            return null == this.fault && this.position < this.prepared.text().length();
        }

        /** The next statement, or {@code noobj} when the source is exhausted or has faulted. */
        public Obj next() {
            if (!hasNext())
                return noobj();
            final Step step = step(this.prepared, this.position, this.previous);
            if (null != step.fault()) {
                this.fault = step.fault();
                return this.fault;
            }
            this.position = step.next();
            this.previous = step.statement();
            return step.statement();
        }

        /** The fault that stopped the walk, or {@code null}. */
        public Obj fault() {
            return this.fault;
        }
    }

    public static Incremental incremental(final String source) {
        return new Incremental(source);
    }

    /**
     * Whether a parsed statement consumed the {@code ;} that ends it — the ';' sugar appends an
     * {@code end()} instruction, so a statement is closed when its last instruction is one.
     */
    private static boolean endsStatement(final Obj statement) {
        if (null == statement)
            return false;
        if (statement.isCode()) {
            final List<Inst> insts = statement.asCode().jvm();
            return !insts.isEmpty()
                    && END_INST_TID.equals(insts.get(insts.size() - 1).tid().basePath());
        }
        return statement.isInst()
                && END_INST_TID.equals(statement.asInst().tid().basePath());
    }

    /**
     * The parse result as a {@code fail::T} carrying a {@link ParseFault}. Transient on purpose:
     * a parse fault is a value the caller inspects, not a frame on the machine's fail stack, and
     * the parser has to work before a machine is loaded (a boot file, an IDE, a test harness).
     */
    private static Obj parseFail(final Prepared prepared, final Result failure) {
        final int position = prepared.toOriginal(failure.getPosition());
        final int[] lc = Token.lineAndColumnOf(prepared.original(), position);
        return parseFail(new ParseFault(
                formatParseError(failure, prepared), prepared.original(), lc[0], lc[1], position));
    }

    private static Obj parseFail(final Prepared prepared, final int position, final String message) {
        final int[] lc = Token.lineAndColumnOf(prepared.original(), position);
        return parseFail(new ParseFault(
                "parse error at line %d, col %d: %s".formatted(lc[0], lc[1], message),
                prepared.original(), lc[0], lc[1], position));
    }

    /**
     * Mint the parse fail the way every other fail is minted — a frame in the machine's fail
     * space, addressable and catchable like any other obj. A fail is an Obj, so the parser hands
     * back an ordinary one rather than a special parser-only value. Only before a machine exists
     * (a boot file mid-load, an editor with no VM attached) is a transient fail the best
     * available value, because there is no fail space yet to put it in.
     */
    private static Obj parseFail(final ParseFault fault) {
        return Machine.loaded() ? MFail.fail(fault) : MFail.transientFail(fault);
    }

    /**
     * The structured fault behind a {@link #parse(String)} failure, or {@code null} when the obj
     * is not a parse fail. A reader of the door's output, not another door.
     */
    public static ParseFault faultOf(final Obj obj) {
        return null != obj && obj.isFail() && obj.asFail().jvm() instanceof ParseFault fault ? fault : null;
    }

    /**
     * Assemble parsed statements: one is itself (the shape a single expression always had),
     * several are a code with {@code end()} between them (the shape the deleted {@code parseMulti}
     * built by hand).
     */
    private static Obj assemble(final List<Obj> statements) {
        if (statements.isEmpty())
            return noobj();
        // One expression is that expression. Several are the chain the grammar built, joined
        // as parsed: ';' is sugar for an end() INST — an Obj with a place in the chain — so
        // nothing is stripped, inserted, or normalized away here. A ';' that produced an
        // end() stays in the program for whoever applies it.
        if (statements.size() == 1)
            return statements.get(0);
        final List<Inst> insts = new ArrayList<>();
        for (final Obj statement : statements)
            appendToInstList(insts, statement);
        return MCode.of(insts, Tokens.CODE_TID, null).tryToInst();
    }

    /**
     * Where a failed parse stopped, and the same explanation its message carries. Reachable from
     * the fail itself ({@code fail.jvm()}), so a caller that needs to place a marker in a
     * source does not have to read prose.
     */
    public static final class ParseFault extends MTronException {

        private final String source;
        private final int line;
        private final int column;
        private final int position;

        private ParseFault(final String message, final String source,
                           final int line, final int column, final int position) {
            super(message, null, true);
            this.source = source;
            this.line = line;
            this.column = column;
            this.position = position;
        }

        public String source() {
            return this.source;
        }

        public int line() {
            return this.line;
        }

        public int column() {
            return this.column;
        }

        public int position() {
            return this.position;
        }
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
        // noobj is an Obj (the zero), not the absence of one: a statement that parsed to it
        // stays in the program as start(noobj) rather than vanishing from the chain
        if (null == obj) return;
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

    /**
     * An RFC 3986 IP-literal host, read together with the {@code //} it follows: {@code //[::1]}. Everywhere else a
     * {@code [} opens the poly annotation, so the literal is only taken right after {@code //}, where no poly can
     * stand, and only when it holds a colon and nothing but hex digits, colons and dots (plus an optional zone,
     * {@code %eth0}). The text passes through unchanged; {@code fURI}'s own pattern reads it as the host.
     */
    public static Parser m_furi_ip_literal() {
        final Parser hexOrDot = anyOf("0123456789abcdefABCDEF.");
        return seq(of("//"), of('['), hexOrDot.star(), of(':'), hexOrDot.or(of(':')).star(),
                opt(seq(of('%'), word().or(anyOf(".~-%")).plus()), null), of(']')).flatten();
    }

    private static Parser m_furi_internal(final String furiCharacterSet, final boolean polynomial, final boolean coefficient, final boolean query, final boolean allowLeadingBranch) {
        final Parser element = m_furi_ip_literal().or(word()).or(m_furi_template()).or(m_furi_branch_literal()).or(m_furi_coefficient_marked_atom()).or(seq(of("::").not(), of("->").not(), anyOf(furiCharacterSet)));
        final Parser first = allowLeadingBranch ? m_furi_leading_coefficient_marked_atom().or(element) : element;
        final Parser contentParser = seq(first, element.star()).flatten();
        final Parser leadingBraceGuard = allowLeadingBranch ? of("") : of('{').not();
        return seq(of('-').not(), leadingBraceGuard, contentParser,
                opt(polynomial ? m_furi_poly_type() : none(), null),
                opt(coefficient ? m_furi_coefficient() : none(), null),
                opt(query ? m_furi_query() : none(), null)).map(t -> f(pick(t, 2)).poly(pick(t, 3)).c(coefficientOrOne(pick(t, 4))).qString(pick(t, 5)));
    }

    public static Parser m_furi(final String furiCharacterSet, final boolean polynomial, final boolean coefficient, final boolean query) {
        return choice(
                of("{0}").trim().map(t -> NOOBJ),
                seq(of("<>").trim()).map(x -> empty()),
                seq(of('<'), m_furi_internal(FULL_FURI_CHARS, polynomial, coefficient, query, true), labeledCloser(of('>'), "'>' to close the uri")).pick(1),
                m_furi_bare(furiCharacterSet, polynomial, coefficient, query));
    }

    /**
     * A bare uri may lead with a coefficient — {@code {4}e} is the uri {@code e} with coefficient {@code {4}}
     * ({@code uri{4}::e}), not a stream. Parse the coefficient first, then the flat uri, and attach the coefficient.
     * A leading {@code {…}} that is NOT a coefficient (a branch) is rejected — it is a stream (objs), not a uri.
     */
    private static Parser m_furi_bare(final String furiCharacterSet, final boolean polynomial, final boolean coefficient, final boolean query) {
        return seq(opt(m_furi_coefficient(), null), m_furi_internal(furiCharacterSet, polynomial, coefficient, query, false))
                .map(t -> {
                    final String c = pick(t, 0);
                    final fURI f = pick(t, 1);
                    return null == c ? f : f.c(coefficientOrOne(c));
                });
    }

    public static Parser m_furi_poly_type() {
        return seq(of('[').trim(),
                seq(opt(m_furi_coefficient(), cInt.ONE()), m_furi(REDUCED_FURI_CHARS, false, true, false), opt(seq(of("=>").trim(), m_furi(REDUCED_FURI_CHARS, false, true, false)), "")).flatten()
                        .separatedBy(of(',').trim()),
                labeledCloser(of(']').trim(),
                        "']' to close the uri poly — a uri poly can not nest (an element is a type, not another poly)"))
                .map(t -> ((List) (pick(t, 1))).stream().filter(c -> !c.equals(',')).map(Object::toString).toList());
    }

    /**
     * Parses a branch segment — {@code {c,d}} or {@code {{2}c,d}} — the value-level stream ({@code +}, branching)
     * and the type-level union ({@code /m/{str,int}::T}). Mirrors {@link #m_furi_poly_type()}, which is the same
     * element list over {@code [ ]} (the poly, {@code ·}, extension); this one is {@code { }} and carries no
     * {@code =>} pair (branches are lst-like, not rec-like). The coefficient parser runs first, so {@code {2}c}
     * is a coefficient-marked atom and {@code c} is a plain atom.
     */
    public static Parser m_furi_branch() {
        return seq(of('{').trim(),
                labeled(seq(of('}').not(),
                        seq(opt(m_furi_coefficient(), cInt.ONE()), m_furi(REDUCED_FURI_CHARS, false, true, false)).flatten())
                        .map(t -> pick(t, 1)),
                        "a coefficient body — {int,int}, {,int}, {int,}, {int}, or a sugar encoding such as {*}, {+}, {?}")
                        .separatedBy(of(',').trim()),
                of('}').trim())
                .map(t -> ((List) (pick(t, 1))).stream().filter(c -> !c.equals(',')).map(Object::toString).toList());
    }

    /**
     * Parses a branch segment as its raw string — {@code {c,d}} or {@code {{2}c,d}} — for the path content, so it is
     * re-read by {@code f(...)}/the regex. A {@code {cInt}} is NOT a branch: the negative lookahead defers it to
     * {@link #m_furi_coefficient()} (so {@code a/b/c{2}} keeps {@code {2}} as the coefficient, not a segment).
     * Handles one level of nesting (a coefficient inside a branch).
     */
    public static Parser m_furi_branch_literal() {
        final Parser nested = seq(of('{'), noneOf("}").star(), of('}')).flatten();
        final Parser body = nested.or(noneOf("}")).plus().flatten();
        return seq(m_furi_coefficient().not(), of('{'),
                labeled(body, "a coefficient body — {int,int}, {,int}, {int,}, {int}, or a sugar encoding such as {*}, {+}, {?}"),
                of('}'))
                .map(t -> "{" + pick(t, 2) + "}");
    }

    /**
     * Parses a coefficient-marked atom — {@code /{2}c} — a path segment that carries a multiplicity on the atom. The
     * leading {@code /} is mandatory, so the marker is only read at a segment boundary: {@code a/b/c{2}} ends in the
     * coefficient {@code {2}} (it follows the atom {@code c}, not a slash), while {@code a/b/{2}c} is the segment
     * {@code {2}c}. This is the coefficient-in-branch encoding: the {@code {2}} records the 2-fold branch that carried
     * the shared suffix.
     */
    private static Parser m_furi_coefficient_marked_atom() {
        return seq(of("/"), m_furi_coefficient(), word().or(m_furi_template()))
                .map(t -> "/" + "{" + pick(t, 1) + "}" + pick(t, 2));
    }

    /**
     * A leading coefficient-marked atom — {@code {0}a} — valid only at the very start of a {@code < >}-quoted uri
     * (there is no preceding slash to mark the segment boundary; the quote itself does). Mirrors
     * {@link #m_furi_coefficient_marked_atom()} without the mandatory slash.
     */
    private static Parser m_furi_leading_coefficient_marked_atom() {
        return seq(m_furi_coefficient(), word().or(m_furi_template()))
                .map(t -> "{" + pick(t, 0) + "}" + pick(t, 1));
    }

    /**
     * {@link cInt#of(String)} is the authority on coefficient bodies and throws on a malformed one.
     * The parser may not throw, so a malformed coefficient degrades to the neutral coefficient here;
     * the checks that can name the problem (the type-position check, the label registry) are what
     * report it as a failure.
     */
    private static cInt coefficientOrOne(final Object raw) {
        try {
            return cInt.of(null == raw ? null : raw.toString());
        } catch (final Exception e) {
            return cInt.ONE();
        }
    }

    public static Parser m_furi_coefficient() {
        return seq(of('{'), labeled(choice(
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
                        "a coefficient body — {int,int}, {,int}, {int,}, {int}, or a sugar encoding such as {*}, {**}, {?}, {??}, {+}"),
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
                        seq((word().or(anyOf("+#_-"))).plus(),
                                opt(seq(of("="), labeled(choice(
                                        seq(of("["), word().or(anyOf(QMAP_POLY_VALUE_CHARS)).star(), of("]")),
                                        m_furi_no_query(),
                                        word().or(anyOf(QMAP_VALUE_CHARS)).plus()),
                                        "a value for the query parameter ('=' must be followed by one)")), ""))
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

    /**
     * A type's brace is a COEFFICIENT, not a branch: {@code int{3}::T} is three ints, and
     * {@code int{BAD}::T} is a malformed coefficient rather than a type with a branch segment. The
     * {@code ::T} is what tells the parser it is in a type position, so the check runs over the
     * type text: a trailing {@code {...}} that is NOT preceded by {@code /} (a path segment) is
     * handed to {@link cInt}, the authority on coefficient bodies, and a rejection becomes a
     * positioned parse failure instead of a constructed type.
     */
    private static final class TypeCoefficientParser extends Parser {

        private final Parser inner;

        private TypeCoefficientParser(final Parser inner) {
            this.inner = inner;
        }

        @Override
        public Result parseOn(final Context ctx) {
            final Result result = this.inner.parseOn(ctx);
            if (result.isFailure())
                return result;
            final String text = ctx.getBuffer().substring(ctx.getPosition(), result.getPosition());
            final int close = text.lastIndexOf('}');
            if (close < 0)
                return result;
            final int open = text.lastIndexOf('{', close);
            if (open < 0 || (open > 0 && text.charAt(open - 1) == '/'))
                return result;                                   // a path branch, not a coefficient
            try {
                cInt.of(text.substring(open + 1, close));
            } catch (final Exception e) {
                return ctx.failure(
                        "a coefficient body — {int,int}, {,int}, {int,}, {int}, or a sugar encoding such as {*}, {**}, {?}, {??}, {+}",
                        ctx.getPosition() + open + 1);
            }
            return result;
        }

        @Override
        public Parser copy() {
            return new TypeCoefficientParser(this.inner.copy());
        }

        @Override
        public List<Parser> getChildren() {
            return List.of(this.inner);
        }

        @Override
        public void replace(final Parser source, final Parser target) {
            // immutable: the inner parser is fixed at construction
        }
    }

    public static Parser m_type_prefix(final fURI baseType) {
        return (null == baseType) ? seq(m_furi(REDUCED_FURI_CHARS, true, true, true), of("://").not(), of(":").repeat(2, Integer.MAX_VALUE)).pick(0) :
                opt(choice(seq(m_furi(REDUCED_FURI_CHARS, true, true, true), of("://").not(), of(":").repeat(2, Integer.MAX_VALUE)).pick(0), m_furi_coefficient().map(t -> {
                    try {
                        return baseType.c(coefficientOrOne(t));
                    } catch (Exception e) {
                        return null;
                    }
                })), baseType);
    }

    public static Parser m_vid_postfix() {
        return opt(seq(of('@'), m_furi(REDUCED_FURI_CHARS, true, false, false)).map(t -> pick(t, 1)), null);
    }

    public static Parser m_fail() {
        // fail::[outermost][middle]...[root cause]@vid — the brackets are
        // the failure stack, left to right: the leftmost bracket is the
        // fail's own message, each following bracket is one level of cause
        // (reading right gets you to the source of the fail). This mirrors
        // writeFail's rendering, so parse(write(fail)) preserves the stack.
        return seq(choice(of("fail"), of(Tokens.FAIL_TID.toString())), of("::"), seq(of('[').trim(), m_obj(), of(']').trim()).map(t -> pick(t, 1)).plus(), m_vid_postfix())
                .map(t -> {
                    final Object test = pick(t, 2);
                    final List<Obj> objs = test instanceof List ? ((List) test) : List.of((Obj) test);
                    final int n = objs.size();
                    // build inside-out: the last bracket is the root cause;
                    // each preceding bracket becomes the outer fail of the
                    // one built so far
                    MTronException chain = MTronException.of(objs.get(n - 1).toString());
                    for (int i = n - 2; i >= 0; i--)
                        chain = MTronException.of(chain, objs.get(i).toString());
                    return fail(chain).vid(pick(t, 3));
                });
    }

    public static Parser m_bool() {
        return seq(m_type_prefix(Tokens.BOOL_TID), of("true").trim().or(of("false").trim()), m_vid_postfix())
                .map(t -> pick(t, 1).equals("true") ?
                        bool(true, pick(t, 0), pick(t, 2)) :
                        bool(false, pick(t, 0), pick(t, 2)));
    }

    public static Parser m_bytes() {
        return seq(m_type_prefix(Tokens.BYTES_TID),
                of("0x"), choice(digit(), anyOf("abcdefABCDEF")).plus().flatten(), m_vid_postfix()).
                map(t -> bytes(ByteBuffer.wrap(HexFormat.of().parseHex(mParser.<String>pick(t, 2))), pick(t, 0), pick(t, 3)));
    }

    public static Parser m_int() {
        return seq(m_type_prefix(Tokens.INT_TID), seq(opt(of('-'), '+'), choice(of('0'), digit().plus()))
                .flatten().trim(), m_vid_postfix())
                .map(t -> jnt(Long.parseLong(pick(t, 1).toString()), pick(t, 0), pick(t, 2)));
    }

    public static Parser m_real() {
        return seq(m_type_prefix(Tokens.REAL_TID), seq(opt(of('-'), '+'), choice(of('0'), digit().plus()), of('.'), digit().plus(), opt(seq(of("E"), opt(of("-"), ""), digit().plus()), ""))
                .flatten().trim(), m_vid_postfix())
                .map(t -> new MReal(Double.parseDouble(pick(t, 1).toString()), pick(t, 0), pick(t, 2)));
    }

    public static Parser m_str() {
        final Parser sqInner = noneOf("'\\").or(of("\\").seq(any()));
        final Parser dqInner = noneOf("\"\\").or(of("\\").seq(any()));
        final Parser singleQuote = labeled(
                seq(of('\''), sqInner.starLazy(of('\'')), of('\'')).flatten(),
                "unclosed single-quote — missing closing '''")
                .map(t -> t.toString().substring(1, t.toString().length() - 1));
        final Parser doubleQuote = labeled(
                seq(of('"'), dqInner.starLazy(of('"')), of('"')).flatten(),
                "unclosed double-quote — missing closing '\"'")
                .map(t -> t.toString().substring(1, t.toString().length() - 1));
        final Parser tripleQuote = labeled(seq(
                        of('"').repeat(3, 3),
                        any().starLazy(of('"').repeat(3, 3)),
                        of('"').repeat(3, 3)).flatten(),
                "unclosed triple-quote — missing closing '\"\"\"'")
                .map(t -> t.toString().substring(3, t.toString().length() - 3));
        return seq(m_type_prefix(Tokens.STR_TID), choice(tripleQuote, singleQuote, doubleQuote), m_vid_postfix())
                .map(t -> new MStr(mParser.pick(t, 1), pick(t, 0), pick(t, 2)));
    }

    public static Parser m_uri() {
        return seq(m_type_prefix(Tokens.URI_TID), m_furi(REDUCED_FURI_CHARS, true, true, true), m_vid_postfix()).map(t -> mParser.<fURI>pick(t, 0).isZero() ? noobj() : new MUri(pick(t, 1), pick(t, 0), pick(t, 2)));
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
        return seq(new TypeCoefficientParser(m_type_prefix(null)), of('T'),
                opt(seq(of('['), opt(m_obj(), null), of(']')).map(t -> pick(t, 1)), null),
                opt(seq(of('['), opt(m_obj(), null), of(']')).map(t -> pick(t, 1)), null),
                choice(m_furi_coefficient().map(t -> Tokens.TYPE_TID.c(coefficientOrOne(t))), m_vid_postfix()))
                .map(t -> T(Tuple.Pair.with(pick(t, 2), pick(t, 3)), pick(t, 0).toString().equals("noobj") ? NOOBJ_TID : pick(t, 0), pick(t, 4)));
    }

    /**
     * One element of a fluent chain: an instruction, optionally introduced by the dot that
     * separates it — or a bare value after a dot, which is a {@code map} of that value.
     * <p>
     * {@code 1.plus(2).445.plus(4)} parses to {@code start(1).plus(2).map(445).plus(4)}. A value
     * in the middle of a chain has nowhere else to land, and "apply this constant" is exactly
     * what {@code map} means, so the surface form needs no new concept — only a place in the
     * chain for a non-inst obj. The dot is REQUIRED on that branch: whitespace adjacency is not
     * a chain, so {@code 1 2} must stay an error rather than becoming {@code start(1).map(2)}.
     */
    private static Parser m_chain_element() {
        return choice(
                seq(opt(of('.').trim(), null), m_inst()).map(t -> pick(t, 1)),
                // '.' is the ring's multiplication; '{a,b}' is the ring's addition. A brace after
                // the dot is therefore a BRANCH, on the same footing as the dot rather than a
                // sugar: 1.plus(2).{plus(4),plus(10)} is 1.plus(2).branch(plus(4),plus(10)).
                seq(of('.').trim(), of('{').trim(),
                        labeled(m_obj().separatedBy(of(',').trim()),
                                "a branch — objs separated by ',' inside '{...}'"),
                        of('}').trim())
                        .map(t -> instB(BRANCH_INST_TID, lst(((List<Object>) pick(t, 2)).stream()
                                .filter(part -> part instanceof Obj)
                                .map(part -> (Obj) part)
                                .toList()))),
                seq(of('.').trim(), m_obj(false)).map(t -> {
                    final Obj value = pick(t, 1);
                    // branch one already had its chance: never double-wrap an inst a wider
                    // grammar change may now allow here
                    return value.isInst() || value.isCode()
                            ? value
                            : instB(MAP_INST_TID, lst(value));
                }));
    }

    public static Parser m_code() {
        return seq(m_type_prefix(Tokens.CODE_TID), opt(of("|["), "|["), m_chain_element().plus(), opt(of("]|"), "]|"), m_vid_postfix())
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
        return new ChoiceParser(FURTHEST, parsers);
    }

    public static ChoiceParser choice(final List<Parser> parsers) {
        return new ChoiceParser(FURTHEST, parsers.toArray(new Parser[parsers.size()]));
    }

    /**
     * A parser that reports its own failure: the message written at the
     * expectation point — not the combinators' — is what survives to the
     * reporter, while the inner failure's position is preserved, so line:col
     * still points at where the attempt actually stopped.
     */
    /**
     * Declare what a rule expected. The message is the diagnostic: a {@link LabeledParser} reports
     * it at the position where the attempt actually died, so the farthest label is what reaches the
     * reader. Registering it here is what lets the formatter tell a designed sentence from a
     * PetitParser combinator dump WITHOUT a hardcoded list that silently stops matching when a new
     * label is added.
     */
    public static Parser labeled(final Parser inner, final String message) {
        LABELS.add(message);
        return new LabeledParser(inner, message);
    }

    /** A closer: expected only because the rule needs its delimiter. Loses an equal-offset tie. */
    public static Parser labeledCloser(final Parser inner, final String message) {
        LABELS.add(message);
        CLOSERS.add(message);
        return new LabeledParser(inner, message);
    }

    private static final class LabeledParser extends Parser {
        private final Parser inner;
        private final String message;

        private LabeledParser(final Parser inner, final String message) {
            this.inner = inner;
            this.message = message;
        }

        @Override
        public Result parseOn(final Context ctx) {
            final Result r = this.inner.parseOn(ctx);
            if (r.isFailure())
                return ctx.failure(this.message, r.getPosition());
            return r;
        }

        @Override
        public Parser copy() {
            return new LabeledParser(this.inner.copy(), this.message);
        }
    }

    public static CharacterParser none() {
        return CharacterParser.none();
    }

    public static <O> O pick(final Object list, int index) {
        try {
            return (O) ((List) list).get(index);
        } catch (final Exception e) {
            // designed text: the raw IndexOutOfBounds + a java List dump said
            // nothing about what went wrong in the parse structure
            throw MTronException.of(e, "token list end — asked for token %d of %d (a malformed parse structure)",
                    index, ((List) list).size());
        }
    }

    // ── Packrat memoization ───────────────────────────────────────
    // petitparser-java 2.4.0 has no built-in .memoized(); we memoize the recursive
    // rules (the SettableParsers above) by (position) within a single parse run.
    // This turns the otherwise-exponential backtracking on nested rec/lst literals
    // into guaranteed-linear time (packrat parsing), without changing the grammar.
    // The cache is keyed by position and cleared by PARSE_GENERATION — bumped once per
    // top-level parse (prepare) — so a new parse run never sees a previous run's result,
    // even when two runs happen to share the same source string.
    public static boolean MEMOIZE = true;
    private static final AtomicInteger PARSE_GENERATION = new AtomicInteger();

    private static final class MemoizedSettableParser extends SettableParser {
        private final ThreadLocal<State> state = ThreadLocal.withInitial(State::new);

        MemoizedSettableParser() {
            super(SettableParser.undefined());
        }

        @Override
        public Result parseOn(final org.petitparser.context.Context context) {
            if (!MEMOIZE)
                return super.parseOn(context);
            final State s = this.state.get();
            final int generation = PARSE_GENERATION.get();
            // clear on a new top-level parse (the generation, bumped in prepare) OR a different buffer (a direct
            // m_obj().parse(...) call that never went through prepare) — either is a fresh parse run.
            if (s.generation != generation || s.buffer != context.getBuffer()) {
                s.cache.clear();
                s.generation = generation;
                s.buffer = context.getBuffer();
            }
            final int position = context.getPosition();
            final Result cached = s.cache.get(position);
            if (null != cached)
                return cached;
            final Result result = super.parseOn(context);
            s.cache.put(position, result);
            return result;
        }

        private static final class State {
            private int generation = -1;
            private String buffer;
            private final Map<Integer, Result> cache = new HashMap<>();
        }
    }
}
