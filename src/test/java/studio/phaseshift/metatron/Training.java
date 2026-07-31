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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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
                    .replace("\r", "\\r") + "\"";
        }
    }

    /**
     * A column-mapping run: which CSV columns form the lhs/rhs pair.
     */
    record Run(String desc, String mapDesc, int lhs, int rhs) {
        public static List<Run> runs(final Training training) {
            final List<Run> runs = new ArrayList<>();
            if (training.map1()[0] != -1) {
                runs.add(new Run(training.value(), training.mapDesc()[0], training.map1()[0], training.map1()[1]));
            }
            if (training.map2()[0] != -1) {
                runs.add(new Run(training.value(), training.mapDesc()[1], training.map2()[0], training.map2()[1]));
            }
            if (training.map3()[0] != -1) {
                runs.add(new Run(training.value(), training.mapDesc()[2], training.map3()[0], training.map3()[1]));
            }
            return runs;
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
                        entries.add(new Entry(
                                run.desc() + ": " + run.mapDesc(),
                                clean(parts[run.lhs()].trim()),
                                clean(parts[run.rhs()].trim()),
                                methodKey
                        ));
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

                    entries.add(new Entry(
                            fallbackInstruction(clean(lhs), clean(rhs)),
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
            return s.replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                    .trim();
        }
    }
}