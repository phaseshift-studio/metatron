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
import studio.phaseshift.metatron.util.MTronException;

import java.lang.annotation.*;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static studio.phaseshift.metatron.Tokens.DESC;
import static studio.phaseshift.metatron.furi.q.QCollection.DOCQ;
import static studio.phaseshift.metatron.furi.q.QCollection.hasDocs;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;

/**
 * Marks a {@code @ParameterizedTest} method for template-based training data extraction.
 * <p>
 * Each of {@code instruction}, {@code input}, and {@code output} is a template in which a
 * {@code {{{param}}}} hole is replaced by that column's row value (only when the hole's
 * content matches a method parameter name; anything else is emitted verbatim). Repeat the
 * annotation to emit multiple entries per CSV row. The static
 * {@link Extractor#from(Method, CsvSource)} method handles both annotated and fallback
 * (two-column) methods.
 * <p>
 * Per the original Stanford Alpaca format:
 * <p>
 * instruction — the task/directive: what to do. It's the verb, and it's usually generic/reusable across many examples.
 * input — the material/context: what to do it to. It's the object — the specific instance data. Optional (empty for self-contained tasks).
 * output — the answer.
 * The cleanest mental model: instruction is the operation, input is the operand.
 * <p>
 * Concrete Alpaca examples:
 * <p>
 * json
 * Copy
 * { "instruction": "Translate the following sentence to French.", "input": "The weather is nice.", "output": "Le temps est beau." }
 * { "instruction": "Summarize the passage.",                 "input": "<long text>",       "output": "<summary>" }
 * { "instruction": "Answer the question.",                   "input": "What is 2+2?",     "output": "4" }
 * { "instruction": "Give three tips for healthy eating.",    "input": "",                 "output": "1. …" }
 * Note the pattern: the same instruction ("Translate…", "Summarize…") is reused across many different inputs — that stable/generic instruction is what lets the model learn "for this kind of task, map input → output".
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@Target({ElementType.ANNOTATION_TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(Training.Trainings.class)
public @interface Training {

    String instruction() default "";

    String input() default "";

    String output();

    String TEST_DATA_ACCESSOR = "@TestData";

    @Target({ElementType.ANNOTATION_TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @interface SkipTraining {
        String reason() default "";
    }

    @Target({ElementType.ANNOTATION_TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @interface Trainings {
        Training[] value();
    }

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
            // skill training entry extraction if explicitly stated
            if (method.isAnnotationPresent(Training.SkipTraining.class))
                return List.of();
            final List<Entry> entries = new ArrayList<>();
            final String methodKey = method.getDeclaringClass().getSimpleName() + "." + method.getName();
            final String delimiter = String.valueOf(csv.delimiter());

            final Training[] trainings = method.getAnnotationsByType(Training.class);
            final String testData = method.isAnnotationPresent(TestData.class) ? String.join("\n", method.getAnnotation(TestData.class).value()) : null;
            if (trainings.length > 0) {
                // ── Annotated: render instruction/input/output templates against the row's columns ──
                final Map<String, Integer> paramToCol = paramNameToColumn(method);
                for (final Training training : trainings) {
                    for (final String row : csv.value()) {
                        final String[] parts = row.split(java.util.regex.Pattern.quote(delimiter));
                        entries.add(new Entry(
                                render(training.instruction(), parts, paramToCol, testData),
                                render(training.input(), parts, paramToCol, testData),
                                render(training.output(), parts, paramToCol, testData),
                                methodKey));
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

        private static final Pattern TEMPLATE_HOLE = Pattern.compile("\\{\\{\\{([^{}]+)\\}\\}\\}");

        /**
         * Maps each method parameter name to its column index (requires {@code -parameters}).
         */
        private static Map<String, Integer> paramNameToColumn(final Method method) {
            final Map<String, Integer> map = new HashMap<>();
            final Parameter[] params = method.getParameters();
            for (int i = 0; i < params.length; i++) {
                map.put(params[i].getName(), i);
            }
            if (method.isAnnotationPresent(TestData.class))
                map.put(TEST_DATA_ACCESSOR, -1);
            return map;
        }

        /**
         * Renders a template by replacing {@code {{{name}}}} holes whose content matches a
         * parameter name with that column's value; any other {@code {{{...}}}} is left verbatim.
         */
        private static String render(final String template, final String[] parts, final Map<String, Integer> paramToCol, final String testData) {
            if (template == null || template.isEmpty()) return "";
            final Matcher m = TEMPLATE_HOLE.matcher(template);
            final StringBuilder sb = new StringBuilder();
            while (m.find()) {
                final String name = m.group(1).trim();
                if (name.equals(TEST_DATA_ACCESSOR)) {
                    if (null == testData || null == paramToCol.get(TEST_DATA_ACCESSOR))
                        throw MTronException.of("attempting to access non-existent @TestData: %s", template);
                    m.appendReplacement(sb, Matcher.quoteReplacement(testData));
                } else {
                    final Integer col = paramToCol.get(name);
                    final String replacement = (col != null && col < parts.length) ? parts[col].trim() : m.group(0);
                    m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
                }
            }
            m.appendTail(sb);
            return sb.toString();
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
                final Obj obj = ObjmtronSerializer.parse(expression.replace("\\\\", "\\"));
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
