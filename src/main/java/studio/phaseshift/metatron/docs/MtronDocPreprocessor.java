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

package studio.phaseshift.metatron.docs;

import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;

/**
 * Processes mtron code blocks within AsciiDoc content.
 *
 * <p>Scans for {@code <!-- 🐖 -->} blocks wrapped in {@code ++++} passthrough
 * markers, evaluates each mtron expression via {@code ObjmtronSerializer.parse().apply()},
 * and replaces the block with {@code [source,mtron]----...----} output.</p>
 *
 * <p>All result formatting delegates entirely to {@link ObjmtronSerializer} —
 * no manual string-surgery on evaluated output.</p>
 *
 * <h3>Directives</h3>
 * <ul>
 *   <li>{@code [HIDDEN]} — evaluate, suppress from output</li>
 *   <li>{@code [HEADER] text} — insert header before source block</li>
 *   <li>{@code [NO_HEADER]} — suppress {@code [source,mtron]} wrapper</li>
 *   <li>{@code /} at end-of-line — continue on next line</li>
 * </ul>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class MtronDocPreprocessor {

    // ── Patterns ────────────────────────────────────────────────────

    /**
     * Picks out a full pig block: {@code ++++ • <!-- 🐖 [role="X"]? • body • --> • ++++}.
     * Leading whitespace is tolerated so blocks work inside nested containers.
     * An optional {@code role="name"} after 🐖 is captured and transferred to
     * the output {@code [source,mtron,role="name"]} block for tabbed-code rendering.
     */
    private static final Pattern PIG = Pattern.compile(
            "\\+\\+\\+\\+\\h*\\R" +
                    "\\h*<!-- \\uD83D\\uDC16(?:\\h+([^\\r\\n]+))?\\R" +
                    "(.*?)" +
                    "\\R\\h*-->\\R\\h*\\+\\+\\+\\+",
            Pattern.DOTALL);

    // Pattern: captures the number from [-- <N> --], and the replacement removes it                                                
    private static final Pattern CALLOUT = Pattern.compile("\\[--\\s*<([0-9]+)>\\s*--]\\s*$");
    private static final Pattern HIDDEN = Pattern.compile("\\[HIDDEN]");
    private static final Pattern HEADER = Pattern.compile("\\[HEADER]\\s*(.*)");
    private static final Pattern NOHDR = Pattern.compile("\\[NO_HEADER]");
    private static final Pattern ERROR = Pattern.compile("\\[ERROR]");
    private static final Pattern NOOUT = Pattern.compile("\\[NO_OUTPUT]");
   

    private static final ObjmtronSerializer SER = ObjmtronSerializer.single();

    // ── Rainbow metatron ─────────────────────────────────────────────

    private static final List<String> COLORS = List.of(
            "red", "blue", "lime", "yellow", "fuchsia", "aqua", "green", "orange"
    );

    // ── Public API ──────────────────────────────────────────────────

    /**
     * Process AsciiDoc text, evaluating all mtron code blocks.
     *
     * @param content     lines of the .adoc file
     * @param prefixLines optional prefix whose blocks run first (output
     *                    discarded; used for {@code [HIDDEN]} setup)
     * @param verbose     dump block output to stdout
     * @return processed lines
     */
    public List<String> process(final File file,
                                final List<String> content,
                                final List<String> prefixLines,
                                final boolean verbose) {
        // Evaluate prefix blocks for side-effects
        if (prefixLines != null)
            evalAll(PIG.matcher(String.join("\n", prefixLines)));

        final String text = String.join("\n", content);
        final Matcher m = PIG.matcher(text);
        final StringBuilder out = new StringBuilder();
        while (m.find()) {
            final String role = m.group(1);  // optional role="primary" etc.
            m.appendReplacement(out, Matcher.quoteReplacement(evalBody(file,m.group(2), role, verbose)));
        }
        m.appendTail(out);

        // [metatron] → rainbow only in non-block text
        final String result = out.toString().contains("[metatron]")
                ? out.toString().replace("[metatron]", Graphitty.sillyPrint("metatron", true, true))
                : out.toString();

        return List.of(result.split("\n", -1));
    }

    // ── Block evaluation ────────────────────────────────────────────

    /**
     * Evaluate a single pig body → {@code [source,mtron]----…----}.
     *
     * @param role optional role attribute captured from the 🐖 marker line,
     *             e.g. {@code role="primary"}; emitted as
     *             {@code [source,mtron,role="primary"]} for tabbed-code
     */
    private static String evalBody(final File file, final String body, final String role, final boolean verbose) {
        final var headers = new ArrayList<String>();
        final var lines = new ArrayList<String>();
        boolean noHeader = false;
        final StringBuilder acc = new StringBuilder();

        for (String raw : body.split("\n")) {
            final Matcher cm = CALLOUT.matcher(raw.stripTrailing());
            String calloutNumber = null;
            if (cm.find()) {
                calloutNumber = cm.group(1);  // captures "1", "2", "3"                                                                           
                raw = cm.replaceAll("");  // strips [-- <N> --]                                                                             
            }
            // Directives
            final Matcher hm = HEADER.matcher(raw);
            if (hm.matches()) {
                headers.add(hm.group(1));
                continue;
            }
            boolean error = false;
            boolean noOutput = false;
            if (NOHDR.matcher(raw).find()) noHeader = true;
            if (ERROR.matcher(raw).find()) error = true;
            if (NOOUT.matcher(raw).find()) noOutput = true;
            final boolean hidden = HIDDEN.matcher(raw).find();
            // Line continuation
            if (raw.endsWith("/")) {
                acc.append(raw.substring(0, raw.length() - 1).stripTrailing()).append("\n       ");
                continue;
            }
            
            acc.append(raw);
            String expr = HIDDEN.matcher(acc).replaceAll("").replace("%", "").strip();
            expr = ERROR.matcher(expr).replaceAll("");
            expr = CALLOUT.matcher(expr).replaceAll("");
            acc.setLength(0);
            if (expr.isEmpty()) continue;

            if (!hidden) {
                String newLine = "mtron> " + expr;
                if(null != calloutNumber)
                    //newLine += " ".repeat(5) + "<" + calloutNumber + ">";
                    lines.add(newLine);
            }

            try {
                final Obj result = ObjmtronSerializer.parse(expr).apply();
                if (result.isFail() && !error) {
                    System.err.printf("ERROR in docs with no [ERROR] specifier (docs are buggy): %s [%s]\n%s\n", expr, result,file.getName());
                    System.exit(1);
                }
                if (!hidden && !noOutput && !result.isNoObj())
                    lines.add("==>" + SER.write(result));
                else if (noOutput)
                    lines.add(Graphitty.sillyPrint("...", true, true));
                // Clear fail stack so errors don't leak across blocks
                if (!hidden) ObjmtronSerializer.parse("/sys/fail/+ -> noobj").apply();
            } catch (final Exception e) {
                if (!hidden) lines.add("==>ERROR: " + e.getMessage());
                // TODO if(!error) System.exit(0);
            }
        }

        if (verbose) System.out.println(lines);

        final StringBuilder sb = new StringBuilder();
        if (!noHeader) {
            if (!headers.isEmpty()) sb.append('\n');
            headers.forEach(h -> sb.append(h).append('\n'));
            sb.append("[source,mtron");
            if (role != null)
                sb.append(',').append(role);
            sb.append("]\n");
        }
        sb.append("----\n");
        lines.forEach(l -> sb.append(l).append('\n'));
        sb.append("----");
        return sb.toString();
    }

    /**
     * Evaluate pig blocks purely for side-effects (prefix).
     */
    private static void evalAll(final Matcher m) {
        while (m.find()) {
            for (String raw : m.group(2).split("\n")) {
                final String expr = HIDDEN.matcher(raw).replaceAll("").replace("%", "").strip();
                if (!expr.isEmpty()) {
                    try {
                        ObjmtronSerializer.parse(expr).apply();
                    } catch (final Exception ignored) {
                    }
                }
            }
        }
    }

    // ── Rainbow ─────────────────────────────────────────────────────

    private static String rainbow() {
        final String chars = "metatron";
        final Random rng = new Random();
        final var avail = new ArrayList<>(COLORS);
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chars.length(); i++) {
            if (avail.isEmpty()) avail.addAll(COLORS);
            final String color = avail.remove(rng.nextInt(avail.size()));
            final boolean upper = rng.nextBoolean();
            final String sep = rng.nextBoolean() ? "#" : "*";
            final char c = upper ? Character.toUpperCase(chars.charAt(i)) : chars.charAt(i);
            sb.append('[').append(color).append(']').append(sep).append(c).append(sep);
        }
        return sb.toString();
    }
}
