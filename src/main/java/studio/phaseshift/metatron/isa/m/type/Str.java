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

package studio.phaseshift.metatron.isa.m.type;

import studio.phaseshift.metatron.algebra.PlusMonoid;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.impl.MStr;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.ProjectionFailureException;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Bytes.BYTES_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Int.INT_TYPE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Real.REAL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Str.Helper.REGEX_CACHE;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MBytes.bytes;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

public interface Str extends Mono, PlusMonoid.O<Str> {
    Pattern STR_TEMPLATE_PATTERN = Pattern.compile("\\$\\{([^}]+)}");
    Type STR_TYPE = Type.Builder.build().tid(STR_TID).vid(STR_TID).create();
    Str ZERO = str("");

    @Override
    Str clone(final Object jvm, final fURI tid, final fURI vid);

    @Override
    String jvm();

    default Str jvm(final String jvm) {
        return this.clone(jvm, this.tid(), this.vid());
    }

    default Str tid(final fURI tid) {
        return this.clone(this.jvm(), tid, this.vid());
    }

    default Str vid(final fURI vid) {
        return this.clone(this.jvm(), this.tid(), vid);
    }

    /**
     * Apply template expansion if this string contains ${...} patterns.
     * Each ${expr} is parsed, the lhs is applied to the expr, and the result's
     * string value replaces the ${expr} placeholder.
     */
    @Override
    default Obj apply(final Obj lhs) {
        if (!this.jvm().contains("${"))
            return this;
        final Matcher matcher = STR_TEMPLATE_PATTERN.matcher(this.strValue());
        final StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            final String exprStr = matcher.group(1);
            try {
                final Obj templateResult = ObjmtronSerializer.parse(exprStr).apply(lhs);
                final String strVal = templateResult.isStr() ? templateResult.strValue() : templateResult.toString();
                matcher.appendReplacement(result, Matcher.quoteReplacement(strVal));
            } catch (Exception e) {
                throw MTronException.of("failed to expand template ${%s}: %s", exprStr, e.getMessage());
                /// // On error, leave template unchanged
                /// matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        matcher.appendTail(result);
        return this.jvm(result.toString());
    }

    @Override
    default Str zero() {
        return ZERO;
    }

    static Str str0() {
        return ZERO;
    }

    class Helper {
        private Helper() {
            // do nothing
        }

        public final static Map<String, Pattern> REGEX_CACHE = new ConcurrentHashMap<>();

        public static Obj toUriOrStr(final String string, final boolean uriFallback) {
            if (string.startsWith("<") && string.endsWith(">"))
                return uri(string.substring(1, string.length() - 1));
            else if (string.contains(" ") ||
                    string.contains("\n") ||
                    string.contains("\r") ||
                    string.contains("\"") ||
                    string.contains("'"))
                return str(string);
            return uriFallback ? uri(string) : str(string);
        }

        public static String cleanString(final Obj obj) {
            return cleanString(obj, false);
        }

        public static String cleanString(final Obj obj, boolean stripQuotes) {
            String temp;
            if (obj.isStr()) {
                temp = obj.strValue();
                if (stripQuotes) {
                    while (temp.startsWith("'") || temp.startsWith("\"")) {
                        temp = temp.substring(1);
                    }
                    while (temp.endsWith("'") || temp.endsWith("\"")) {
                        temp = temp.substring(0, temp.length() - 1);
                    }
                }
            } else if (obj.isUri()) {
                temp = obj.uriValue().toString();
                if (temp.startsWith("<") && temp.endsWith(">"))
                    temp = temp.substring(1, temp.length() - 1);
            } else {
                temp = "" + obj.jvm();
            }
            return temp;
        }

        public static String project(final String input, final Obj rulesObj) {
            if (rulesObj == null || rulesObj.isNoObj()) return "";

            if (rulesObj.isObjCall()) {
                final Obj result = rulesObj.apply(str(input));
                // CRITICAL: If the instruction explicitly fails or returns false,
                // we must halt the entire projection immediately.
                if (result == null || result.isNothing()) {
                    throw ProjectionFailureException.instance();
                }
                return Str.Helper.cleanString(result);
            }

            if (rulesObj.isRec()) {
                String current = input;
                for (Map.Entry<Obj, Obj> entry : rulesObj.asRec().recValue().entrySet()) {
                    final String regex = Str.Helper.cleanString(entry.getKey());
                    final Obj replacement = entry.getValue();

                    final Pattern pattern = REGEX_CACHE.computeIfAbsent(regex, Pattern::compile);
                    final Matcher matcher = pattern.matcher(current);
                    final StringBuilder sb = new StringBuilder();
                    int lastEnd = 0;

                    while (matcher.find()) {
                        sb.append(current, lastEnd, matcher.start());
                        String matchText = matcher.group();

                        // RECURSION: This will now propagate the ProjectionFailureException upwards
                        sb.append(project(matchText, replacement));
                        lastEnd = matcher.end();
                    }
                    sb.append(current.substring(lastEnd));
                    current = sb.toString();
                }
                return current;
            }

            return Str.Helper.cleanString(rulesObj);
        }
    }

    class StrType {
        public static Set<Inst> insts() {
            return new LinkedHashSet<>(List.of(
                    instC(AS_INST_TID.dom(STR_TID).rng(STR_TID), lst(STR_TYPE), (lhs, inst) -> lhs.tid(inst.arg(0).vidOrTid().c(c -> c.mult(lhs.c())))),
                    instC(AS_INST_TID.dom(STR_TID).rng(BYTES_TID), lst(BYTES_TYPE), (lhs, inst) -> bytes(ByteBuffer.wrap(lhs.strValue().getBytes()), inst.arg(0).vidOrTid().c(c -> c.mult(lhs.c())), lhs.vid())),
                    instC(AS_INST_TID.dom(STR_TID).rng(BOOL_TID), lst(BOOL_TYPE), (lhs, inst) -> bool(lhs.strValue().equalsIgnoreCase("true"), inst.arg(0).vidOrTid().c(c -> c.mult(lhs.c())), lhs.vid())),
                    instC(AS_INST_TID.dom(STR_TID).rng(INT_TID), lst(INT_TYPE), (lhs, inst) -> jnt(Long.parseLong(lhs.strValue()), inst.arg(0).vidOrTid().c(c -> c.mult(lhs.c())), lhs.vid())),
                    instC(AS_INST_TID.dom(STR_TID).rng(REAL_TID), lst(REAL_TYPE), (lhs, inst) -> real(Double.parseDouble(lhs.strValue()), inst.arg(0).vidOrTid().c(c -> c.mult(lhs.c())), lhs.vid())),
                    instC(AS_INST_TID.dom(STR_TID).rng(URI_TID), lst(URI_TYPE), (lhs, inst) -> uri(f(lhs.strValue()), inst.arg(0).vidOrTid().c(c -> c.mult(lhs.c())), lhs.vid())),
                    instC(REVERSE_INST_TID.dom(STR_TID).rng(STR_TID), lst(), (lhs, inst) -> lhs.jvm(new StringBuilder(lhs.strValue()).reverse().toString())),
                    instC(ZERO_INST_TID.dom(STR_TID).rng(STR_TID), lst(), (lhs, inst) -> lhs.asStr().zero()),
                    docWrap(instC(HAS_INST_TID.dom(STR_TID).rng(STR_TID.maybe()), lst(T(STR_TID)), (lhs, inst) -> REGEX_CACHE.compute(inst.arg(0).strValue(), (k, v) -> null == v ? Pattern.compile(k) : v).matcher(lhs.strValue()).find() ? lhs : noobj()),
                            "an str to check", "whether the domain matches arg", Map.of(jnt(0), "the regex for matching"), "check whether the lhs str matches the regex arg"),
                    // docWrap(instC(SPLIT_INST_TID.dom(STR_TID).rng(LST_TID), lst(T(STR_TID)), (lhs, inst) ->
                    //                 lst(Arrays.stream(lhs.strValue().split(inst.arg(0).strValue())).map(MStr::str).map(Obj::<Obj>as).toList())),
                    //         "a str to split", "the components of the split lhs str", Map.of(jnt(0), "a token to split on"), "split the lhs string according to the token arg and emit a stream of splits"),
                    docWrap(instC(SPLIT_INST_TID.dom(STR_TID).rng(LST_TID), lst(T(STR_TID)), (lhs, inst) -> lst((List) Arrays.stream(lhs.strValue().split(inst.arg(0).strValue())).map(MStr::str).toList())),
                            "a str to split", "lst encoded components of the split lhs str", Map.of(jnt(0), "a token to split on"), "split the lhs string according to the token arg and insert components into an ordered lst"),
                    docWrap(instC(MERGE_INST_TID.dom(STR_TID.maybeSome()).rng(STR_TID), lst(T(STR_TID)), (lhs, inst) -> str(lhs.stream().map(Obj::<String>jvmAs).reduce((a, b) -> a + inst.arg(0).strValue() + b).orElse(""))),
                            "an str barrier", "the join of the str barrier", Map.of(jnt(0), "the join token"), "join the barrier given the str arg"),
                    docWrap(instC(REGEX_INST_TID.dom(STR_TID).rng(LST_TID), lst(T(STR_TID)), (lhs, inst) -> {
                                final Pattern pattern = REGEX_CACHE.compute(inst.arg(0).strValue(), (k, v) -> null == v ? Pattern.compile(k) : v);
                                final Matcher matcher = pattern.matcher(lhs.strValue());
                                if (matcher.groupCount() == 0) {
                                    return lst(matcher.results().map(MatchResult::group).map(MStr::str).map(Obj::<Obj>as).toList());
                                } else {
                                    return lst(matcher.results().map(mr -> {
                                        final List<Obj> groups = new ArrayList<>();
                                        groups.add(str(mr.group())); // full match
                                        for (int i = 1; i <= mr.groupCount(); i++) {
                                            final String g = mr.group(i);
                                            groups.add(str(g != null ? g : ""));
                                        }
                                        return (Obj) lst(groups);
                                    }).toList());
                                }
                            }),
                            "a str to split by regex", "the regex capture groups (or full matches) of the lhs str", Map.of(jnt(0), "regex"), "split the lhs str by regex matches; if the regex has capture groups, each match is a lst of [fullMatch, group1, group2, ...], otherwise a flat lst of full matches",
                            "'abc.cde'.regex('[^.]+') [-- ['abc','cde'] --]",
                            "'abc.cde'.regex('.\\..') [-- ['c.c'] --]",
                            "'241G'.regex('(\\d+)([KMGT])') [-- [['241G','241','G']] --]"),

                    instC(GT_INST_TID.dom(STR_TID).rng(BOOL_TID), lst(T(STR_TID)), (lhs, inst) -> bool(Inst.Helper.alignLHSType(lhs, inst.arg(0)).filter(l -> l.strValue().compareTo(inst.arg(0).strValue()) > 0).isPresent())),
                    instC(GTE_INST_TID.dom(STR_TID).rng(BOOL_TID), lst(T(STR_TID)), (lhs, inst) -> bool(Inst.Helper.alignLHSType(lhs, inst.arg(0)).filter(l -> l.strValue().compareTo(inst.arg(0).strValue()) >= 0).isPresent())),
                    instC(LT_INST_TID.dom(STR_TID).rng(BOOL_TID), lst(T(STR_TID)), (lhs, inst) -> bool(Inst.Helper.alignLHSType(lhs, inst.arg(0)).filter(l -> l.strValue().compareTo(inst.arg(0).strValue()) < 0).isPresent())),
                    instC(LTE_INST_TID.dom(STR_TID).rng(BOOL_TID), lst(T(STR_TID)), (lhs, inst) -> bool(Inst.Helper.alignLHSType(lhs, inst.arg(0)).filter(l -> l.strValue().compareTo(inst.arg(0).strValue()) <= 0).isPresent())),
                    instC(PLUS_INST_TID.dom(STR_TID).rng(STR_TID), lst(T(STR_TID)), (lhs, inst) -> lhs.jvm(lhs.strValue() + inst.arg(0).strValue())),
                    instC(SUM_INST_TID.dom(STR_TID.maybeSome()).rng(STR_TID), lst(T(STR_TID.maybe())), (lhs, inst) -> str(lhs.stream().map(Obj::strValue).reduce(inst.arg(0).orElse(str("")).strValue(), (a, b) -> a + b))),
                    instC(UCASE_INST_TID.dom(STR_TID).rng(STR_TID), lst(), (lhs, inst) -> lhs.jvm(lhs.strValue().toUpperCase())),
                    instC(LCASE_INST_TID.dom(STR_TID).rng(STR_TID), lst(), (lhs, inst) -> lhs.jvm(lhs.strValue().toLowerCase())),
                    instC(SELECT_INST_TID.dom(STR_TID).rng(STR_TID), lst(REC_TYPE), (lhs, inst) -> str(Str.Helper.project(lhs.strValue(), inst.arg(0)), lhs.tid(), lhs.vid())),
                    instC(WHERE_INST_TID.dom(STR_TID).rng(STR_TID.maybe()), lst(REC_TYPE), (lhs, inst) -> ProjectionFailureException.predicateThrow(lhs, a -> Str.Helper.project(lhs.strValue(), inst.arg(0).asRec()))),

                    instC(WITHIN_INST_TID.dom(STR_TID).rng(B), lst(T(B)), (lhs, inst) -> Arrays.stream(lhs.strValue().split("")).map(s -> inst.arg(0).apply(str(s))).
                            map(o -> (PlusMonoid.O) o).
                            reduce((a, b) -> (PlusMonoid.O) a.plus(b)).
                            map(Obj::<Obj>as).
                            orElse(noobj()))));

        }
    }


}