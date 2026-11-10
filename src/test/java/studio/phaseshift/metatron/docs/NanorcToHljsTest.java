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

import org.jline.builtins.ConfigurationPath;
import org.jline.builtins.SyntaxHighlighter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.util.MTronException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE NANORC IS THE STANDARD OF TRUTH — the question these assertions answer is whether the
 * generated browser grammar means what the terminal engine means.
 * <p>
 * The pipeline is pinned at both ends: the regex dialect is jline's posix-to-java fixup
 * ({@code fixRegexes}/{@code replaceClass}), and the applied result is the span structure the
 * terminal's own jline highlighter produces for probe lines — the browser grammar's emitted rules,
 * reversed and simulated with highlight.js's first-mode-wins scan, must paint the same spans in the
 * same nano colors.
 */
public class NanorcToHljsTest extends AbstractMetatronTest {

    private static final Path NANORC = Path.of("conf/nanorc/mtron.nanorc");
    private static final Path OVERLAY = Path.of("conf/nanorc/mtron-overlay.js");
    private static final Path OUTPUT = Path.of("docs/website/highlight/languages/mtron.min.js");

    // ============================================================================================
    // the jline posix dialect
    // ============================================================================================

    @ParameterizedTest(name = "fixRegexes [{0}] -> [{1}]")
    @CsvSource(delimiter = '%', value = {
            "'[[:alnum:]_]+X=>' % '[\\p{Alnum}_]+X=>'",
            "'[^][]' % '[^\\]\\[]'",
            "'[]a]' % '[\\]a]'",
            "'\\<true\\>' % '\\btrue\\b'",
            "'[:digit:]+' % '\\p{Digit}+'",
            "'[:space:]x' % '\\sx'",
            "'&?(|#|\\+|[a-z]|-|_|\\{.)+(&|=|<=)(?!>)' % '&?(|#|\\+|[a-z]|-|_|\\{.)+(&|=|<=)(?!>)'",
            "'(^|[^/])(true|false)([^/]|$)' % '(^|[^/])(true|false)([^/]|$)'"
    })
    public void thePosixDialectFixesLikeJline(final String posix, final String expected) {
        assertEquals(expected, NanorcToHljs.fixRegexes(posix));
    }

    @ParameterizedTest(name = "fixRegexes rejects [{0}]")
    @CsvSource(delimiter = '%', value = {
            "'abc['",
            "'abc\\'",
            "'[:bogus:]'"
    })
    public void brokenPosixRegexesAreRejected(final String posix) {
        assertThrows(MTronException.class, () -> NanorcToHljs.fixRegexes(posix));
    }

    @ParameterizedTest(name = "replaceClass [{0}] -> [{1}]")
    @CsvSource(delimiter = '%', value = {
            "'alnum' % '\\p{Alnum}'",
            "'digit' % '\\p{Digit}'",
            "'space' % '\\s'",
            "'xdigit' % '\\p{XDigit}'"
    })
    public void thePosixClassTableIsJlines(final String name, final String expected) {
        assertEquals(expected, NanorcToHljs.replaceClass(name));
    }

    @Test
    public void aPosixClassJlineDoesNotKnowIsRejected() {
        assertThrows(MTronException.class, () -> NanorcToHljs.replaceClass("wibble"));
    }

    @Test
    public void aNanorcClauseTokenizesIntoWordsAndQuotedRegexes() {
        assertEquals(List.of("color", "yellow,underline", "0x"), NanorcToHljs.tokenize("color yellow,underline \"0x\""));
        // the quoted regex keeps its escapes verbatim — \" is jline's problem, not the tokenizer's
        assertEquals(List.of("color", "yellow", "start=", "\\\"\\\"\\\"", "end=", "\\\"\\\"\\\""),
                NanorcToHljs.tokenize("color yellow start=\"\\\"\\\"\\\"\" end=\"\\\"\\\"\\\"\""));
    }

    // ============================================================================================
    // parsing the real nanorc
    // ============================================================================================

    @Test
    public void theRealNanorcParsesIntoItsFullRuleList() throws IOException {
        final List<NanorcToHljs.Rule> rules = NanorcToHljs.parse(Files.readAllLines(NANORC));
        assertEquals(89, rules.size());
        assertEquals(3, rules.stream().filter(r -> null != r.start()).count());
        // the design principle comment says so: assume everything is a uri — the first rule paints
        // the whole line blue, and the last rule is the final repaint
        assertEquals(List.of("blue"), rules.get(0).styles());
        assertEquals(".*", rules.get(0).regex());
        assertEquals(List.of("magenta"), rules.get(rules.size() - 1).styles());
        assertEquals("=>", rules.get(rules.size() - 1).regex());
        // every regex the file declares — the dead one included — is a java regex jline can compile
        for (final NanorcToHljs.Rule rule : rules) {
            final String regex = null == rule.start() ? rule.regex() : rule.start() + rule.end();
            Pattern.compile(NanorcToHljs.fixRegexes(regex), rule.ignoreCase() ? Pattern.CASE_INSENSITIVE : 0);
        }
    }

    // ============================================================================================
    // java -> browser regex
    // ============================================================================================

    @ParameterizedTest(name = "toJsRegex [{0}] -> [{1}]")
    @CsvSource(delimiter = '%', value = {
            "'[\\p{Alnum}_]+X=>' % '[A-Za-z0-9_]+X=>'",
            "'\\p{Digit}+' % '[0-9]+'",
            "'/=>' % '\\/=>'",
            "'\\sx' % '\\sx'"
    })
    public void theBrowserGetsClassesItHas(final String javaRegex, final String expected) {
        assertEquals(expected, NanorcToHljs.toJsRegex(javaRegex));
    }

    // ============================================================================================
    // the generated file
    // ============================================================================================

    @Test
    public void noResultSubsetRuleMatchesThePromptStart() throws IOException {
        final List<NanorcToHljs.Rule> rules = NanorcToHljs.parse(Files.readAllLines(NANORC));
        final List<NanorcToHljs.Rule> result = NanorcToHljs.resultRules(rules);
        // the uri-default .* is not in the subset at all — under consumption it would swallow every
        // result line before any token rule could act
        assertFalse(result.stream().anyMatch(r -> ".*".equals(r.regex())));
        // the invariant that lets the ===> region close at the next expression: no subset rule,
        // guarded or not, matches at the START of a prompt line
        for (final NanorcToHljs.Rule rule : result) {
            final String regex = null == rule.start() ? rule.regex() : rule.start();
            assertFalse(pattern(rule, regex).matcher("mtron>").lookingAt(),
                    "subset rule matches the prompt start: " + regex);
        }
        // the rules that WOULD match the prompt are kept in their guarded form — a bare identifier
        // inside a result is blue in the terminal and must stay blue in the browser
        assertTrue(result.stream().anyMatch(r -> null != r.regex() && r.regex().startsWith("(?!^mtron>)")));
    }

    @Test
    public void theGeneratedFileCarriesEveryRuleReversedAndTheOverlay() throws IOException {
        final Path probe = Files.createTempFile("mtron", ".min.js");
        try {
            NanorcToHljs.generate(NANORC, OVERLAY, probe);
            final String js = Files.readString(probe);
            // one mode per nanorc rule, and the result-body subset is every rule except the
            // uri default (prompt-matchers in their guarded form)
            final String full = js.split("const MTRON_RULES = \\[")[1].split("];")[0];
            final String subset = js.split("const MTRON_RESULT_RULES = \\[")[1].split("];")[0];
            assertEquals(88, full.split("className: 'mtron-nanorc-").length - 1);
            assertEquals(88, subset.split("className: 'mtron-nanorc-").length - 1);
            assertTrue(subset.contains("(?!^mtron>)"));
            // BOTH arrays resolve in the reversed (later-wins) order — the subset lost its reversal
            // once and the int rule beat the green 0x inside every result body
            assertTrue(subset.trim().startsWith("{ className: 'mtron-nanorc-magenta', begin: /=>/"));
            assertTrue(full.trim().startsWith("{ className: 'mtron-nanorc-magenta', begin: /=>/"));
            // reversed: the LAST nanorc rule (magenta =>) is the FIRST browser mode — nano's
            // later-wins becomes highlight.js's first-mode-wins
            assertTrue(js.contains("const MTRON_RULES = [\n"
                    + "                { className: 'mtron-nanorc-magenta', begin: /=>/, relevance: 0 },"));
            // the posix class expanded to what the browser engine has, and no java leftovers
            assertTrue(js.contains("[A-Za-z0-9_]+X=>"));
            assertFalse(js.contains("[[:alnum:]]"));
            assertFalse(js.contains("\\p{"));
            // anchored rules get the m flag: jline highlights line by line, the browser buffer-wide
            assertTrue(js.contains("(true|false)([^\\/]|$)/m"));
            // the presentation overlay is spliced in and referenced
            assertTrue(js.contains("PhaseShift Studio Presents"));
            assertTrue(js.contains("\\u200B[0-9]+\\u200B"));
            assertTrue(js.contains("const MTRON_OVERLAY = ["));
            assertTrue(js.contains("...MTRON_OVERLAY,"));
            assertTrue(js.contains("...MTRON_RULES"));
            // the result-body subset: the .* default appears once (top level only), and the result
            // region re-highlights with the prompt-safe subset so it always closes at the next mtron>
            assertTrue(js.contains("const MTRON_RESULT_RULES = ["));
            // the uri-default .* is a paint-model rule and is NOT emitted — under hljs's
            // consumption engine it would swallow every line before any token rule could act
            assertFalse(full.contains("begin: /.*/,"));
            assertFalse(subset.contains("begin: /.*/,"));
            assertTrue(js.contains("MTRON_RESULT_RULES,"));
            assertTrue(js.endsWith("    hljs.registerLanguage('mtron', mtron_grammar);\n})();\n"));
        } finally {
            Files.deleteIfExists(probe);
        }
    }

    @Test
    public void theCommittedGrammarIsWhatTheGeneratorProduces() throws IOException {
        // the drift guard: a nanorc edit that does not regenerate the committed artifact fails here
        final Path probe = Files.createTempFile("mtron", ".min.js");
        try {
            NanorcToHljs.generate(NANORC, OVERLAY, probe);
            assertEquals(Files.readString(OUTPUT), Files.readString(probe));
        } finally {
            Files.deleteIfExists(probe);
        }
    }

    // ============================================================================================
    // terminal parity: the jline engine is the oracle
    // ============================================================================================

    /** the terminal's own highlighter — same build as console.Highlighter, through jnanorc */
    private static final SyntaxHighlighter TERMINAL = SyntaxHighlighter.build(
            new ConfigurationPath(Path.of("conf/nanorc"), Path.of(System.getProperty("user.home"), ".metatron"))
                    .getConfig("jnanorc"), "mtron");

    /** the ANSI colors jline's default theme emits for the nanorc color names */
    private static final Map<String, String> SGR_TO_COLOR = Map.of(
            "34", "blue", "33", "yellow", "31", "red", "32", "green", "36", "cyan", "35", "magenta", "90", "bright-black");

    private record Span(int start, int end, String color, boolean underline) {
    }

    @ParameterizedTest(name = "terminal parity [{0}]")
    @CsvSource(delimiter = '%', value = {
            "'PREFIXX=>'",
            "'nat::29@/usr/marko/age'",
            "'noobj'",
            "'[-- comment --]'",
            "'\"\"\"[== multi ==]\"\"\"'",
            "'0x2A => 42'",
            "'\"str\" and ''str'''",
            "'a -> b'",
            "'1 + 2.5E-3'",
            "'f?g<=h'",
            "'!*auto'",
            "'x = y,z'",
            "'<http://a.com>'",
            "'x{3}::T'",
            "'a => b'"
    })
    public void theBrowserRulesPaintWhatTheTerminalPaints(final String probe) throws IOException {
        final String ansi = TERMINAL.reset().highlight(probe).toAnsi();
        final List<Span> terminal = parseAnsi(ansi);
        final List<Span> browser = simulate(NanorcToHljs.parse(Files.readAllLines(NANORC)), probe);
        assertEquals(browser, terminal, "jline ANSI: " + ansi.replace("\033", "<ESC>"));
    }

    /**
     * the paint model of jline's highlighter: every rule paints all its matches, later rules
     * repaint. A repaint replaces the fg color but never clears an underline a prior rule laid
     * down (jline merges attributes), and a rule whose color jline's style compiler cannot resolve
     * never paints at all — "gray" is the nanorc's own dead example (probe-proven, 4.4.8).
     */
    private static List<Span> simulate(final List<NanorcToHljs.Rule> rules, final String text) {
        final String[] color = new String[text.length()];
        final boolean[] underline = new boolean[text.length()];
        for (final NanorcToHljs.Rule rule : rules) {
            if (!JLINE_COLORS.contains(rule.styles().get(0))) continue;
            if (null == rule.start()) {
                final Matcher m = pattern(rule, rule.regex()).matcher(text);
                while (m.find()) paint(color, underline, m.start(), m.end(), rule);
            } else {
                final Matcher begin = pattern(rule, rule.start()).matcher(text);
                while (begin.find()) {
                    final int body = begin.end();
                    final Matcher end = pattern(rule, rule.end()).matcher(text);
                    final int to = end.region(body, text.length()).find() ? end.end() : text.length();
                    paint(color, underline, begin.start(), to, rule);
                }
            }
        }
        final List<Span> spans = new ArrayList<>();
        for (int i = 0; i < text.length(); i++) {
            final Span span = new Span(i, i + 1, color[i], underline[i]);
            if (spans.isEmpty() || !sameStyle(spans.get(spans.size() - 1), span)) spans.add(span);
            else spans.set(spans.size() - 1,
                    new Span(spans.get(spans.size() - 1).start(), i + 1, color[i], underline[i]));
        }
        return spans;
    }

    /**
     * the color names jline 4.4.8's nanorc style compiler resolves — "gray" and "grey" are NOT
     * among them (probe-proven: rules carrying them never paint); "bright-black" (or the
     * unhyphenated "brightblack") is the spelling that works
     */
    private static final java.util.Set<String> JLINE_COLORS = java.util.Set.of(
            "blue", "yellow", "red", "green", "cyan", "magenta", "bright-black", "brightblack");

    private static void paint(final String[] color, final boolean[] underline,
                              final int from, final int to, final NanorcToHljs.Rule rule) {
        final String name = rule.styles().get(0);
        final boolean under = rule.styles().contains("underline");
        for (int i = from; i < to && i < color.length; i++) {
            color[i] = name;
            if (under) underline[i] = true;
        }
    }

    private static boolean sameStyle(final Span a, final Span b) {
        return a.color().equals(b.color()) && a.underline() == b.underline();
    }

    // ============================================================================================
    // browser parity: the consumption engine the parity probes above cannot see
    // ============================================================================================

    /**
     * The BROWSER engine model: highlight.js begin modes are position-anchored, resolved in
     * emission order (the reversed nanorc list — nano's later-wins), and each mode CONSUMES its
     * match; regions consume to their end. The paint-model parity probes cannot see this side.
     * These probes pin the nanorc's own documented repaint contracts (mtron.nanorc:94): the cyan
     * X=> repaint beats the earlier red rule, a bare X=> stays red, and a comment is one gray
     * region (gray works in the browser even though jline's compiler refuses the name).
     */
    @ParameterizedTest(name = "consumption [{0}] -> [{1}]")
    @CsvSource(delimiter = '%', value = {
            "'PREFIXX=>' % 'cyan 0 9'",
            "'X=>' % 'red 0 3'",
            "'[-- comment --]' % 'bright-black 0 15'",
            "'a/b/c' % 'blue 0 5'",
            "'int::T' % 'blue 0 3;magenta 3 6'",
            "'a=>1' % 'cyan 0 1;magenta 1 3;yellow 3 4'",
            "'0x616263' % 'green 0 2;yellow 2 8'",
            "'*/path/#' % 'yellow 0 1;blue 1 7;green 7 8'",
            "'<//2024.12:25/09/00/00/000?tz=-0500>' % 'green 0 1;blue 1 26;yellow 26 27;cyan 27 29;magenta 29 30;blue 30 35;green 35 36'",
            "'[_=>_]' % 'green 0 1;cyan 1 2;magenta 2 4;blue 4 5;green 5 6'"
    })
    public void theBrowserEngineHonoursTheLaterWinsContracts(final String probe, final String expected) throws IOException {
        final List<Span> spans = consume(NanorcToHljs.parse(Files.readAllLines(NANORC)), probe);
        final String[] pieces = expected.split(";");
        assertEquals(pieces.length, spans.size(), "spans: " + spans);
        for (int i = 0; i < pieces.length; i++) {
            final String[] parts = pieces[i].split(" ");
            assertEquals(new Span(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), parts[0], false), spans.get(i));
        }
    }

    /** the consumption scan: at each position the first mode in emission order whose regex matches wins */
    private static List<Span> consume(final List<NanorcToHljs.Rule> rules, final String text) {
        final List<Span> spans = new ArrayList<>();
        int pos = 0;
        while (pos < text.length()) {
            int end = -1;
            String color = null;
            boolean underline = false;
            for (int i = rules.size() - 1; i >= 0; i--) {
                final NanorcToHljs.Rule rule = rules.get(i);
                // the uri default is not emitted — and not simulated: under consumption it would
                // swallow every unstyled remainder
                if (".*".equals(rule.regex())) continue;
                final Matcher m = pattern(rule, null == rule.start() ? rule.regex() : rule.start()).matcher(text);
                // transparent bounds: a lookbehind (e.g. the bytes body's (?<=0x)) must see the
                // text before the scan position, which the real engine lets it do
                if (!m.region(pos, text.length()).useTransparentBounds(true).lookingAt()) continue;
                color = rule.styles().get(0);
                underline = rule.styles().contains("underline");
                if (null == rule.start()) {
                    end = m.end();
                } else {
                    final Matcher e = pattern(rule, rule.end()).matcher(text);
                    end = e.region(m.end(), text.length()).useTransparentBounds(true).find() ? e.end() : text.length();
                }
                break;
            }
            if (end < 0 || end <= pos) {
                // no match — or an EMPTY one, which the real engine also steps over
                pos++;
                continue;
            }
            spans.add(new Span(pos, end, color, underline));
            pos = end;
        }
        return spans;
    }

    private static Pattern pattern(final NanorcToHljs.Rule rule, final String regex) {
        return Pattern.compile(NanorcToHljs.fixRegexes(regex), rule.ignoreCase() ? Pattern.CASE_INSENSITIVE : 0);
    }

    /**
     * runs of (start, end, fg color, underline) out of jline's toAnsi output. The spans are
     * measured in VISIBLE-TEXT coordinates — the escape sequences themselves consume no positions —
     * so they line up with the plain-text spans the simulation produces.
     */
    private static List<Span> parseAnsi(final String ansi) {
        final List<Span> spans = new ArrayList<>();
        final Matcher m = Pattern.compile("\\u001B\\[([0-9;]*)m").matcher(ansi);
        int ansiPos = 0;
        int textPos = 0;
        String fg = null;
        boolean underline = false;
        while (m.find()) {
            final int len = m.start() - ansiPos;
            if (len > 0) {
                add(spans, textPos, textPos + len, fg, underline);
                textPos += len;
            }
            for (final String param : m.group(1).split(";")) {
                if (param.isEmpty()) continue;
                switch (Integer.parseInt(param)) {
                    case 0 -> { fg = null; underline = false; }
                    case 4 -> underline = true;
                    case 24 -> underline = false;
                    case 39 -> fg = null;
                    case 30, 31, 32, 33, 34, 35, 36, 37, 90, 91, 92, 93, 94, 95, 96, 97 -> fg = param;
                    case 38 -> fg = "38";   // 38;5;N — the 256-color form, keyed plainly
                    default -> { /* 48;… background and the like: not what nanorc colors use */ }
                }
            }
            ansiPos = m.end();
        }
        if (ansiPos < ansi.length()) add(spans, textPos, textPos + ansi.length() - ansiPos, fg, underline);
        return spans.stream()
                .map(s -> new Span(s.start(), s.end(), null == s.color() ? null : SGR_TO_COLOR.getOrDefault(s.color(), s.color()), s.underline()))
                .toList();
    }

    private static void add(final List<Span> spans, final int from, final int to, final String fg, final boolean underline) {
        final Span span = new Span(from, to, fg, underline);
        if (!spans.isEmpty() && sameStyle(spans.get(spans.size() - 1), span)) {
            final Span last = spans.get(spans.size() - 1);
            spans.set(spans.size() - 1, new Span(last.start(), to, last.color(), last.underline()));
        } else spans.add(span);
    }
}
