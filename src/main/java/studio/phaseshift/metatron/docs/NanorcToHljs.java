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

package studio.phaseshift.metatron.docs;

import studio.phaseshift.metatron.util.MTronException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Generates the highlight.js grammar {@code docs/website/highlight/languages/mtron.min.js} from
 * {@code conf/nanorc/mtron.nanorc}. The nanorc is the standard of truth for mtron syntax
 * highlighting: the terminal console colors itself with the very same file (jline's nanorc
 * highlighter, see {@code console.Highlighter}), and the browser grammar is a translation of it.
 * <p>
 * The translation has five steps. (1) parse the nanorc's flat {@code color}/{@code icolor} rules
 * and {@code start=}/{@code end=} regions. (2) fix each regex the way jline does — posix classes to
 * {@code \p{…}}, {@code \<}/{@code \>} to {@code \b}, the bracket-expression escape quirks — so the
 * browser grammar matches what the terminal actually matches. (3) expand the {@code \p{…}} names to
 * classes the browser regex engine has (it has no {@code Alnum} property escape). (4) emit the
 * rules with their declaration order REVERSED: nano's "later rules win" is highlight.js's "first
 * mode wins", so the last nanorc rule becomes the first browser mode. (5) splice in the
 * presentation overlay {@code conf/nanorc/mtron-overlay.js} — the REPL transcript chrome, doc
 * callouts and log tags the flat rule model cannot express.
 * <p>
 * {@link #fixRegexes} and {@link #replaceClass} are ports of jline's
 * {@code SyntaxHighlighter} nanorc preprocessing
 * (Copyright (c) 2002-2024, the original author or authors, BSD-3-Clause), so a nanorc regex means
 * the same thing in both engines by construction.
 */
public class NanorcToHljs {

    /** one nanorc color clause — a plain regex rule, or a start/end region when {@code start} is set */
    record Rule(List<String> styles, String regex, String start, String end, boolean ignoreCase) {
        Rule {
            styles = List.copyOf(styles);
        }
    }

    private static final Path DEFAULT_NANORC = Path.of("conf/nanorc/mtron.nanorc");
    private static final Path DEFAULT_OVERLAY = Path.of("conf/nanorc/mtron-overlay.js");
    private static final Path DEFAULT_OUTPUT = Path.of("docs/website/highlight/languages/mtron.min.js");

    public static void main(final String[] args) throws IOException {
        Path nanorc = DEFAULT_NANORC;
        Path overlay = DEFAULT_OVERLAY;
        Path output = DEFAULT_OUTPUT;
        int i = 0;
        while (i < args.length) {
            switch (args[i]) {
                case "-n", "--nanorc" -> nanorc = Path.of(args[++i]);
                case "--overlay" -> overlay = Path.of(args[++i]);
                case "-o", "--output" -> output = Path.of(args[++i]);
                default -> throw MTronException.of("unknown argument: %s", args[i]);
            }
            i++;
        }
        generate(nanorc, overlay, output);
    }

    public static void generate(final Path nanorc, final Path overlay, final Path output) throws IOException {
        final List<Rule> rules = parse(Files.readAllLines(nanorc));
        final String overlayText = Files.readString(overlay);
        Files.writeString(output, emit(rules, overlayText));
        System.out.println("NanorcToHljs: " + rules.size() + " nanorc rules -> " + output);
    }

    // ============================================================================================
    // nanorc parsing
    // ============================================================================================

    /**
     * Parse the flat rule list of one nanorc file. A {@code color} line contributes one rule per
     * regex argument (a {@code start="…" end="…"} pair contributes one region rule); {@code icolor}
     * sets the case-insensitive flag. {@code syntax}, {@code include} and comment lines are not
     * rules, and any other nano directive is skipped so a nano-only keyword added to the file never
     * breaks generation.
     */
    static List<Rule> parse(final List<String> lines) {
        final List<Rule> rules = new ArrayList<>();
        for (final String raw : lines) {
            final String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("syntax ") || line.startsWith("include "))
                continue;
            final List<String> tokens = tokenize(line);
            final String command = tokens.get(0);
            if (!"color".equals(command) && !"icolor".equals(command)) continue;
            final List<String> styles = List.of(tokens.get(1).split(","));
            final boolean ignoreCase = "icolor".equals(command);
            String start = null;
            String end = null;
            for (int i = 2; i < tokens.size(); i++) {
                final String token = tokens.get(i);
                if (token.equals("start=")) {
                    if (++i >= tokens.size()) throw MTronException.of("start= without a regex: %s", line);
                    start = tokens.get(i);
                } else if (token.equals("end=")) {
                    if (++i >= tokens.size()) throw MTronException.of("end= without a regex: %s", line);
                    end = tokens.get(i);
                } else {
                    rules.add(new Rule(styles, token, null, null, ignoreCase));
                }
            }
            if (null != start || null != end)
                rules.add(new Rule(styles, null, start, end, ignoreCase));
        }
        return rules;
    }

    /**
     * The tokens of one nanorc clause: bare words (the command, the color names, {@code start=},
     * {@code end=}) and quoted regex strings. A quoted regex keeps its backslash escapes verbatim —
     * {@code "\"""} stays {@code \"} — because the regex content is handed to jline's fixer exactly
     * as the nanorc writes it.
     */
    static List<String> tokenize(final String line) {
        final List<String> tokens = new ArrayList<>();
        final StringBuilder current = new StringBuilder();
        int i = 0;
        while (i < line.length()) {
            final char c = line.charAt(i);
            if ('"' == c) {
                // a quoted regex can sit right against a bare token (start="…"): flush first
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                final StringBuilder quoted = new StringBuilder();
                i++;
                while (i < line.length()) {
                    final char q = line.charAt(i);
                    if ('\\' == q && i + 1 < line.length()) {
                        quoted.append(q).append(line.charAt(i + 1));
                        i += 2;
                        continue;
                    }
                    if ('"' == q) {
                        i++;
                        break;
                    }
                    quoted.append(q);
                    i++;
                }
                tokens.add(quoted.toString());
            } else if (Character.isWhitespace(c)) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                i++;
            } else {
                current.append(c);
                i++;
            }
        }
        if (!current.isEmpty()) tokens.add(current.toString());
        return tokens;
    }

    // ============================================================================================
    // the jline nanorc regex dialect (ports of jline's SyntaxHighlighter preprocessing, BSD-3)
    // ============================================================================================

    /**
     * Fix up a posix nanorc regex into the java regex jline compiles — the dialect the terminal
     * engine actually runs. The bracket-expression escape quirks are jline's; posix classes like
     * {@code [:space:]} or {@code [:digit:]} become java classes, inside and outside a bracket
     * expression; {@code \<} and {@code \>} become {@code \b}.
     */
    static String fixRegexes(final String posix) {
        final int len = posix.length();
        final StringBuilder java = new StringBuilder();
        boolean inBracketExpression = false;
        int i = 0;
        char next;
        try {
            for (; i < len; i++) {
                final char c = posix.charAt(i);
                switch (c) {
                    case '\\' -> {
                        next = posix.charAt(++i);
                        // don't translate anything after the \ character escape
                        if (inBracketExpression && ']' == next) {
                            inBracketExpression = false;
                            java.append("\\\\").append(next);
                        } else {
                            // translate '\<' and '\>' to '\b'
                            if ('<' == next || '>' == next) next = 'b';
                            java.append(c).append(next);
                        }
                    }
                    case '[' -> {
                        if (i == len - 1)
                            throw MTronException.of("lone [ at the end of (index %d): %s", i, posix);
                        // handle "single bracket" posix "classes" like [:space:] or [:digit:]
                        if (':' == posix.charAt(i + 1)) {
                            final int afterClass = nextAfterClass(posix, i + 2);
                            if (!posix.regionMatches(afterClass, ":]", 0, 2)) {
                                java.append("[:");
                                i++;
                                inBracketExpression = true;
                            } else {
                                final String className = posix.substring(i + 2, afterClass);
                                java.append(replaceClass(className));
                                i = afterClass + 1;
                            }
                        } else if (inBracketExpression) {
                            // translate lone [ to \[
                            java.append('\\').append(c);
                        } else {
                            inBracketExpression = true;
                            java.append(c);
                            next = posix.charAt(i + 1);
                            if (']' == next) {
                                i++;
                                java.append("\\]");
                            } else if ('^' == next && ']' == posix.charAt(i + 2)) {
                                i += 2;
                                java.append("^\\]");
                            }
                        }
                    }
                    case ']' -> {
                        inBracketExpression = false;
                        java.append(c);
                    }
                    default -> java.append(c);
                }
            }
        } catch (final Exception e) {
            throw MTronException.of("posix-to-java regex translation failed around index %d of: %s", i, posix);
        }
        return java.toString();
    }

    /** the index of the {@code :} that opens the {@code :]} closing a posix class, or -1 when there is none */
    private static int nextAfterClass(final String posix, final int from) {
        for (int i = from; i < posix.length() - 1; i++)
            if (':' == posix.charAt(i) && ']' == posix.charAt(i + 1)) return i;
        return -1;
    }

    /** the java class each posix class name compiles to (jline's table, ported verbatim) */
    static String replaceClass(final String name) {
        return switch (name) {
            case "alnum" -> "\\p{Alnum}";
            case "alpha" -> "\\p{Alpha}";
            case "blank" -> "\\p{Blank}";
            case "cntrl" -> "\\p{Cntrl}";
            case "digit" -> "\\p{Digit}";
            case "graph" -> "\\p{Graph}";
            case "lower" -> "\\p{Lower}";
            case "print" -> "\\p{Print}";
            case "punct" -> "\\p{Punct}";
            case "space" -> "\\s";
            case "upper" -> "\\p{Upper}";
            case "xdigit" -> "\\p{XDigit}";
            default -> throw MTronException.of("unknown posix class: %s", name);
        };
    }

    // ============================================================================================
    // java regex -> browser regex
    // ============================================================================================

    /**
     * The browser has no {@code \p{Alnum}} property escapes, so the java classes replaceClass
     * produced are expanded to explicit character classes — inside a bracket expression bare, outside
     * one wrapped in brackets. Only these names occur (replaceClass produces no other), and the
     * hex-escaped expansions keep every member literal. {@code /} is escaped for the regex literal.
     */
    private static final Map<String, String> JS_POSIX = Map.ofEntries(
            Map.entry("Alnum", "A-Za-z0-9"),
            Map.entry("Alpha", "A-Za-z"),
            Map.entry("Blank", " \\t"),
            Map.entry("Cntrl", "\\x00-\\x1F\\x7F"),
            Map.entry("Digit", "0-9"),
            Map.entry("Graph", "\\x21-\\x7E"),
            Map.entry("Lower", "a-z"),
            Map.entry("Print", "\\x20-\\x7E"),
            Map.entry("Punct", "\\x21-\\x2F\\x3A-\\x40\\x5B-\\x60\\x7B-\\x7E"),
            Map.entry("Upper", "A-Z"),
            Map.entry("XDigit", "0-9A-Fa-f"));

    static String toJsRegex(final String javaRegex) {
        final StringBuilder js = new StringBuilder();
        boolean inBracket = false;
        for (int i = 0; i < javaRegex.length(); i++) {
            final char c = javaRegex.charAt(i);
            if ('\\' == c && i + 3 < javaRegex.length() && 'p' == javaRegex.charAt(i + 1) && '{' == javaRegex.charAt(i + 2)) {
                final int close = javaRegex.indexOf('}', i + 3);
                final String name = javaRegex.substring(i + 3, close);
                final String expansion = JS_POSIX.get(name);
                if (null == expansion) throw MTronException.of("unexpected java posix class: %s", name);
                if (!inBracket) js.append('[');
                js.append(expansion);
                if (!inBracket) js.append(']');
                i = close;
            } else if ('[' == c) {
                inBracket = true;
                js.append(c);
            } else if (']' == c) {
                inBracket = false;
                js.append(c);
            } else if ('/' == c) {
                js.append("\\/");
            } else {
                js.append(c);
            }
        }
        return js.toString();
    }

    // ============================================================================================
    // emission
    // ============================================================================================

    /**
     * The generated file. The nanorc rules are emitted in REVERSED declaration order — nano's
     * "later rules win" becomes highlight.js's "first mode wins" — under one class per nano color
     * ({@code mtron-nanorc-<style>}), then the overlay fragment is spliced in and referenced by the
     * top-level {@code contains}.
     */
    static String emit(final List<Rule> rules, final String overlay) {
        final StringBuilder js = new StringBuilder();
        js.append(HEADER).append('\n');
        js.append("(function () {\n");
        js.append("    var mtron_grammar = (function () {\n");
        js.append("        'use strict';\n\n");
        js.append("        /*\n");
        js.append("            Language: mtron\n");
        js.append("            Author: Marko A. Rodriguez (http://markorodriguez.com)\n");
        js.append("        */\n\n");
        js.append("        /** @type LanguageFn */\n");
        js.append("        function mtron(hljs) {\n");
        js.append("            /* ---- generated rules: nanorc declaration order REVERSED (later wins -> first wins) ---- */\n");
        js.append("            const MTRON_RULES = [\n");
        for (int i = rules.size() - 1; i >= 0; i--) {
            final Rule rule = rules.get(i);
            if (!uriDefault(rule)) js.append(mode(rule));
        }
        js.append("            ];\n");
        js.append("            /* ---- the result-body subset: every rule except the uri default, prompt-matchers\n");
        js.append("                 in a (?!^mtron>) guarded form so a ===> region always closes at the next\n");
        js.append("                 expression while result bodies keep their token colors ---- */\n");
        js.append("            const MTRON_RESULT_RULES = [\n");
        final List<Rule> subset = resultRules(rules);
        for (int i = subset.size() - 1; i >= 0; i--) js.append(mode(subset.get(i)));
        js.append("            ];\n");
        js.append("            /* ---- presentation overlay: conf/nanorc/mtron-overlay.js ---- */\n");
        js.append(overlay);
        js.append("\n            return {\n");
        js.append("                name: 'mtron',\n");
        js.append("                contains: [\n");
        js.append("                    ...MTRON_OVERLAY,\n");
        js.append("                    ...MTRON_RULES\n");
        js.append("                ]\n");
        js.append("            };\n");
        js.append("        }\n\n");
        js.append("        return mtron;\n");
        js.append("    })();\n\n");
        js.append("    hljs.registerLanguage('mtron', mtron_grammar);\n");
        js.append("})();\n");
        return js.toString();
    }

    /**
     * One generated mode. A plain rule is a single-span {@code begin} with no {@code end} — the
     * shape the hand-written grammar used for every one-span rule, and it is load-bearing: hljs
     * {@code match} modes are resolved by an earliest-match scan across the buffer, not anchored at
     * the scan position, so a {@code match} rule whose pattern is the {@code .*} default would grab
     * every line at position 0 before any position-anchored rule (a comment, an expression) could
     * act — the whole grammar went blue. A {@code begin} mode is anchored and resolved in list
     * order, which is nano's later-wins semantics with the reversed rule list. A region is
     * {@code begin} with an {@code end}. The {@code i} flag carries {@code icolor}; {@code m} is
     * added when the rule is anchored, because highlight.js matches the whole buffer while jline
     * matches line by line — the {@code m} flag restores the per-line meaning of {@code ^} and
     * {@code $}.
     */
    private static String mode(final Rule rule) {
        final String className = "mtron-nanorc-" + String.join("-", rule.styles());
        final String flags = (rule.ignoreCase() ? "i" : "") + (anchored(rule) ? "m" : "");
        if (null == rule.start())
            return "                { className: '" + className + "', begin: /" + toJsRegex(fixRegexes(rule.regex())) + "/" + flags + ", relevance: 0 },\n";
        return "                { className: '" + className + "', begin: /" + toJsRegex(fixRegexes(rule.start())) + "/" + flags + ", end: /" + toJsRegex(fixRegexes(rule.end())) + "/" + flags + " },\n";
    }

    /**
     * the rules a {@code ===>} result region may re-highlight its body with — the full rule list
     * minus the uri default, with every rule that would match a prompt line re-emitted in a
     * prompt-guarded form ({@code (?!^mtron>)} prefixed). A rule matching the prompt would consume
     * the next expression line from inside the region and the region would never close — but
     * dropping the rule outright loses its color in the body too: a bare identifier like
     * {@code plus} inside a result is blue in the terminal and must be blue in the browser. The
     * guard blocks only the start of a prompt line; the {@code ^} it introduces earns the
     * {@code m} flag, which the guard needs to see line starts.
     */
    static List<Rule> resultRules(final List<Rule> rules) {
        final List<Rule> result = new ArrayList<>();
        for (final Rule rule : rules) {
            if (uriDefault(rule)) continue;
            result.add(promptSafe(rule) ? rule : promptGuarded(rule));
        }
        return result;
    }

    /** whether a rule can paint inside a result region without swallowing the next prompt line */
    private static boolean promptSafe(final Rule rule) {
        final String regex = null == rule.start() ? rule.regex() : rule.start();
        final Pattern pattern = Pattern.compile(fixRegexes(regex), rule.ignoreCase() ? Pattern.CASE_INSENSITIVE : 0);
        return !pattern.matcher("mtron>").find();
    }

    /** the prompt-guarded variant of a rule that would otherwise match the prompt line */
    private static Rule promptGuarded(final Rule rule) {
        final String guarded = "(?!^mtron>)" + (null == rule.start() ? rule.regex() : rule.start());
        return new Rule(rule.styles(), null == rule.start() ? guarded : rule.regex(),
                null == rule.start() ? null : guarded, rule.end(), rule.ignoreCase());
    }

    /**
     * the nanorc's uri default ({@code color blue ".*"} — "assume everything is a uri") is a
     * paint-model rule: in the terminal every later rule repaints over it token by token, but
     * highlight.js consumes — a {@code .*} mode would swallow the rest of every line before any
     * token rule could act, leaving every expression solidly blue. The browser grammar therefore
     * carries no default and leaves unmatched text unstyled, exactly as the hand-written grammar
     * did.
     */
    private static boolean uriDefault(final Rule rule) {
        return ".*".equals(rule.regex());
    }

    /** whether a rule uses {@code ^} or {@code $} outside an escape — the sign its regex needs the {@code m} flag */
    private static boolean anchored(final Rule rule) {
        final String regex = null == rule.start() ? rule.regex() : rule.start() + rule.end();
        boolean escaped = false;
        for (int i = 0; i < regex.length(); i++) {
            final char c = regex.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if ('\\' == c) {
                escaped = true;
                continue;
            }
            if ('^' == c || '$' == c) return true;
        }
        return false;
    }

    private static final String HEADER = """
            /*! `mtron` grammar for Highlight.js 11.10.0
             * generated by NanorcToHljs from conf/nanorc/mtron.nanorc — do not edit by hand
             * the nanorc is the standard of truth: it drives the terminal (jline nanorc
             * highlighter) and, through this generator, the browser grammar
             */""";
}
