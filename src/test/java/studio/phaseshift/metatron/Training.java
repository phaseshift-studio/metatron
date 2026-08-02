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

package studio.phaseshift.metatron;

import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Call;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.resolver.InstResolver;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static studio.phaseshift.metatron.Tokens.DESC;
import static studio.phaseshift.metatron.furi.q.QCollection.DOCQ;
import static studio.phaseshift.metatron.furi.q.QCollection.hasDocs;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;

/**
 * Marks a {@code @ParameterizedTest} method for multi-column training data extraction.
 * <p>
 * Each CSV row can produce multiple training entries via {@code map1/map2/map3}
 * column-pair mappings. The static {@link Extractor#from(Method, CsvSource)} method
 * handles both annotated and fallback (two-column) methods.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@Target({ElementType.ANNOTATION_TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Training {

    String value();

    String[] mapDesc();

    int[] map1() default {-1};

    int[] map2() default {-1};

    int[] map3() default {-1};

    // ── Companion logic ──────────────────────────────────────────────────────

    /**
     * A single training data entry: instruction, input, output, and optional source method tag.
     */
    record Entry(String instruction, String input, String output, String sourceMethod) {

        public String toJson() {
            return String.format(
                    "{\"instruction\": %s, \"input\": %s, \"output\": %s}",
                    escapeJson(instruction),
                    escapeJson(input),
                    escapeJson(output)
            );
        }

        private static String escapeJson(String s) {
            return "\"" + s.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t") + "\"";
        }
    }

    /**
     * A column-mapping run: which CSV columns form the lhs/rhs pair.
     */
    record Run(String desc, String mapDesc, int lhs, int rhs, int other) {
        public static List<Run> runs(final Training training) {
            final List<Run> runs = new ArrayList<>();
            if (training.map1()[0] != -1) {
                runs.add(new Run(training.value(), training.mapDesc()[0], training.map1()[0], training.map1()[1], training.map1().length == 3 ? training.map1()[2] : -1));
            }
            if (training.map2()[0] != -1) {
                runs.add(new Run(training.value(), training.mapDesc()[1], training.map2()[0], training.map2()[1], training.map2().length == 3 ? training.map2()[2] : -1));
            }
            if (training.map3()[0] != -1) {
                runs.add(new Run(training.value(), training.mapDesc()[2], training.map3()[0], training.map3()[1], training.map3().length == 3 ? training.map3()[2] : -1));
            }
            return runs;
        }

        public boolean has3rd() {
            return this.other != -1;
        }
    }

    /**
     * Extracts training entries from a method. Handles {@code @Training} annotated
     * methods (multi-column mappings) and plain two-column fallback.
     */
    final class Extractor {

        private static final String[] FALLBACK_TEMPLATES = {
                "evaluate: %s",
                "what does %s yield?",
                "compute: %s",
                "%s = ?",
                "the result of %s is:",
                "solve: %s",
                "evaluate %s:",
                "what is %s?",
                "compute %s =",
                "%s evaluates to:"
        };

        private static final Random RANDOM = new Random();

        private Extractor() {
        }

        /**
         * Produces training entries from a test method.
         */
        public static List<Entry> from(Method method, CsvSource csv) {
            final List<Entry> entries = new ArrayList<>();
            final String methodKey = method.getDeclaringClass().getSimpleName() + "." + method.getName();
            final String delimiter = String.valueOf(csv.delimiter());

            if (method.isAnnotationPresent(Training.class)) {
                // ── Annotated: use explicit column mappings ──
                final Training training = method.getAnnotation(Training.class);
                for (final Run run : Run.runs(training)) {
                    for (final String row : csv.value()) {
                        final String[] parts = row.split(java.util.regex.Pattern.quote(delimiter));
                        final String opCtx = extractOperatorContext(clean(parts[run.lhs()].trim()));
                        if (run.has3rd()) {
                            entries.add(new Entry(
                                    run.desc() + ": " + run.mapDesc() + (opCtx.isEmpty() ? "" : " " + opCtx),
                                    "<<lhs>> " + clean(parts[run.lhs()].trim()) + " <<rhs>> " + clean(parts[run.rhs()].trim()),
                                    clean(parts[2].trim()),
                                    methodKey
                            ));
                        } else {
                            entries.add(new Entry(
                                    run.desc() + ": " + run.mapDesc() + (opCtx.isEmpty() ? "" : " " + opCtx),
                                    clean(parts[run.lhs()].trim()),
                                    clean(parts[run.rhs()].trim()),
                                    methodKey
                            ));
                        }
                    }
                }
            } else {
                // ── Fallback: infer two-column lhs/rhs ──
                final int paramCount = method.getParameterCount();

                for (final String row : csv.value()) {
                    final String[] parts = row.split(java.util.regex.Pattern.quote(delimiter));

                    if (parts.length < 2) continue;

                    final String lhs, rhs;

                    if (paramCount >= 3 && parts.length >= 3) {
                        // Multi-column: likely trailing description — use second-to-last as rhs
                        lhs = parts[0].trim();
                        rhs = parts[parts.length - 2].trim();
                    } else {
                        // Classic two-column
                        lhs = parts[0].trim();
                        rhs = parts[parts.length - 1].trim();
                    }

                    final String opCtx = extractOperatorContext(clean(lhs));
                    entries.add(new Entry(
                            fallbackInstruction(clean(lhs), clean(rhs)) + (opCtx.isEmpty() ? "" : " " + opCtx),
                            clean(lhs),
                            clean(rhs),
                            methodKey
                    ));
                }
            }
            return entries;
        }

        private static String fallbackInstruction(String input, String output) {
            String template = FALLBACK_TEMPLATES[RANDOM.nextInt(FALLBACK_TEMPLATES.length)];
            return template.replace("%s", input);
        }

        private static String clean(String s) {
            if (s == null) return "";
            return s.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t")
                    .trim();
        }

        /**
         * Extracts operator descriptions from an mtron expression by parsing it,
         * finding instructions, and resolving their {@code ?docq>>desc} documentation.
         */
        private static String extractOperatorContext(String expression) {
            if (expression == null || expression.isBlank()) return "";
            try {
                final Obj obj = ObjmtronSerializer.parse(expression);
                if (!obj.isCall()) return "";
                // Resolve instruction types: resolveCode for Code chains, unresolved for Inst
                List<Inst> insts;
                try {
                    insts = obj.isCode() ? InstResolver.get().resolveCode(noobj(), obj.asCode()).insts() : obj.asCall().insts();
                } catch (final Throwable e) {
                    insts = ((Call) obj).insts();  // fallback
                }
                final StringBuilder ctx = new StringBuilder();
                for (final Inst inst : insts) {
                    try {
                        final fURI docQID = inst.tid().addQ(DOCQ);
                        Rec doc = Router.readFromSpace(docQID).orElse(rec());
                        if (!hasDocs(doc))
                            doc = Router.readFromSpace(docQID.basePath().addQ(DOCQ)).orElse(rec());
                        if (hasDocs(doc)) {
                            final String desc = doc.at(DESC).strValue();
                            if (!ctx.isEmpty()) ctx.append("; ");
                            ctx.append(inst.tid().name()).append(": ").append(desc);
                        }
                    } catch (final Exception ignored) {
                        // docq lookup failed for this instruction — skip
                    }
                }
                return ctx.isEmpty() ? "" : "(" + ctx + ")";
            } catch (Exception e) {
                return "";
            }
        }
    }
}
