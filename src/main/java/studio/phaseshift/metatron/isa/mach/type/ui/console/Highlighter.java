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

package studio.phaseshift.metatron.isa.mach.type.ui.console;

import org.jline.builtins.ConfigurationPath;
import org.jline.builtins.SyntaxHighlighter;
import org.jline.reader.LineReader;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjSerializer;
import studio.phaseshift.metatron.isa.mach.io.type.ObjLinkSerializer;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.web.parser.ObjPlainTextSerializer;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class Highlighter implements org.jline.reader.Highlighter {

    private final SyntaxHighlighter syntaxHighlighter;
    private final Pattern GRAPHITTY_PATTERN = Pattern.compile("\\{\\{.*?}}");
    private final Graphitty graphitty;
    private final static ConfigurationPath configurations = new ConfigurationPath(
            Paths.get("conf/nanorc"),                               // application-wide settings
            Paths.get(System.getProperty("user.home"), ".metatron") // user-specific settings
    );
    private ObjSerializer<String> serializer;
    private Terminal terminal;

    private static final Highlighter INSTANCE = new Highlighter(SyntaxHighlighter.build(Highlighter.configurations.getConfig("jnanorc"), "mtron"));

    /**
     * The instance that colors the user's OWN text: built without graphitty, so a rule in it is
     * the characters they typed rather than an instruction.
     */
    private static final Highlighter LINE =
            new Highlighter(new ObjmtronSerializer(true), true);

    /**
     * The user's own text — the line they are editing, and the echo of the line they submitted —
     * colored as syntax and nothing else.
     * <p>
     * Graphitty markup in their line IS the characters they typed and has to stay that way.
     * Resolving it there rewrites what they see: {@code {{link}}/m/inst/+{{/link}}} collapses to
     * {@code /m/inst/+}, a half-typed tag eats the line under the cursor, and a clear-screen rule
     * wipes the screen out from under them.  Their own words are never markup, before or after
     * the <enter> that submits them.
     * <p>
     * Output is the other way round — a result, a banner, a widget body is the console's to
     * decorate, and that is {@link #format(Object)}.  This instance has the same syntax config as
     * the reader's own highlighter (also built with {@code ignoreGraphitty}), so the echo, the
     * console's redraw of the live line, and jline's redraw of it all agree.
     */
    public static String line(final String text) {
        if (null == text || text.isEmpty()) return text;
        return LINE.syntaxHighlighter.highlight(text).toAnsi();
    }

    public static Highlighter single() {
        return INSTANCE;
    }

    public static Highlighter create() {
        return new Highlighter(INSTANCE.syntaxHighlighter);
    }

    /**
     * How many highlighted lines are remembered.  A widget re-renders its whole
     * body on every pass (a scroll, a resize, a click), so without this the same
     * unchanged lines are re-highlighted hundreds of times a second: a 60 row
     * body cost ~38 ms per pass, which is exactly what a mouse wheel notch or a
     * page key felt like.
     */
    private static final int LINE_CACHE_LIMIT = 4096;

    /**
     * Highlighted lines, keyed by {@code language + '\0' + text}.  Shared
     * across widgets on purpose: a widget is re-hydrated into a fresh instance
     * by every update, so a per-widget memo would be cold exactly when a live
     * widget needs it.  Bounded, oldest-first.
     */
    private static final Map<String, String> LINE_CACHE =
            java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<>(512, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(final Map.Entry<String, String> eldest) {
                    return size() > LINE_CACHE_LIMIT;
                }
            });

    /**
     * Highlight one line of a body, memoized — the entry point for widgets that
     * colorize their own body lines (see {@code Style.highlight}).
     *
     * <p>Highlighting is a full syntax pass per line; a widget cannot afford one
     * per line per render, so unchanged lines are served from the memo.  The
     * terminal-less form is used (as it is for a freshly built highlighter), so
     * a cached line is identical whatever terminal it is finally drawn on.
     *
     * <p>Two lines are handed back untouched.  A line with no syntax
     * ({@code txt}, or a token naming no nanorc) is markup, and the caller — every
     * caller turns its result into Graphitty markup — expands it in the pass that
     * already renders the whole body.  And a line carrying a block tag
     * ({@code {{syntax:java}}}, {@code {{/syntax:java}}}) belongs to a wrap over
     * SEVERAL lines: one line of it cannot be expanded on its own (the tag would be
     * dropped, or its close would find nothing to close), so it too is left for the
     * pass that sees the whole body {@link Graphitty} renders.
     *
     * @param language the syntax to use — a {@code conf/nanorc} token
     *                 (e.g. {@code mtron}, {@code java}, {@code txt}); see
     *                 {@link #syntaxName(String)}
     * @param line     the line to colorize
     */
    public static String highlightLine(final String language, final String line) {
        if (null == line || line.isEmpty()) return "";
        final String token = null == language || language.isEmpty() ? "txt" : language;
        final String syntax = syntaxName(token);
        if (null == syntax || containsSyntaxTag(line)) return line;
        final String key = syntax + '\u0000' + line;
        final String cached = LINE_CACHE.get(key);
        if (null != cached) return cached;
        final String highlighted = new Highlighter(syntax).highlight(line);
        LINE_CACHE.put(key, highlighted);
        return highlighted;
    }

    /**
     * True when the line carries a tag of a {@code {{syntax:…}}} block.  Such a
     * line is part of a wrap over several lines and is therefore rendered by the
     * pass that sees the whole body, never line by line.
     */
    private static boolean containsSyntaxTag(final String line) {
        return line.contains("{{" + Graphitty.SYNTAX_RULE_PREFIX)
                || line.contains("{{/" + Graphitty.SYNTAX_RULE_PREFIX)
                || Graphitty.isFenceLine(line);
    }

    /**
     * Highlighted blocks, keyed by {@code syntax + '\0' + code} — the multi-line
     * counterpart of {@link #LINE_CACHE}.  A block is ONE highlight pass: jline
     * carries its start/end rule state across the block's lines, which is what
     * keeps a {@code /* … *}{@code /} comment colored to its close, so a block
     * cannot be memoized per line.  Bounded, oldest-first.
     */
    private static final int BLOCK_CACHE_LIMIT = 256;

    /**
     * Blocks longer than this are highlighted but not memoized — a memo of whole
     * source files would pin megabytes of text for one frame.
     */
    private static final int BLOCK_CACHE_MAX_CHARS = 64 * 1024;

    private static final Map<String, String> BLOCK_CACHE =
            java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(final Map.Entry<String, String> eldest) {
                    return size() > BLOCK_CACHE_LIMIT;
                }
            });

    /**
     * The number of memoized highlighted blocks (for tests and diagnostics).
     */
    static int highlightedBlockCacheSize() {
        return BLOCK_CACHE.size();
    }

    /**
     * The jline syntax name a language token maps to, or {@code null} for plain
     * text.
     *
     * <p>jline matches a syntax name by EXACT equality, and {@code jnanorc}
     * includes the system nanorcs, so a token passed straight through is a
     * coincidence away from the right rules: {@code java} is not the name
     * {@code conf/nanorc/java.nanorc} declares ({@code Java}) and would resolve
     * to a same-named system nanorc instead — or, on a host without one, to no
     * highlighting at all.  A token therefore names the FILE ({@code java} →
     * {@code conf/nanorc/java.nanorc}, user config first) and the name jline
     * needs is the {@code syntax "…"} declaration inside it.
     *
     * <p>The lookup is on the lowercased token, so a declared name works as a
     * token too ({@code Java} and {@code java} are the same file).
     * {@code txt}, {@code text}, {@code plain}, {@code none} and any token with
     * no nanorc of its own mean "no highlighting" — a language the machine
     * happens to have a system nanorc for is NOT picked up, because that would
     * render differently on a host that has /usr/share/nano and one that does
     * not.
     *
     * @param language the language token (e.g. {@code java}, {@code mtron})
     * @return the jline syntax name, or {@code null} for plain text
     */
    public static String syntaxName(final String language) {
        if (null == language) return null;
        final String token = language.trim();
        if (token.isEmpty()) return null;
        return SYNTAX_NAMES.computeIfAbsent(token.toLowerCase(Locale.ROOT), Highlighter::resolveSyntax).orElse(null);
    }

    /**
     * Resolved tokens, including the misses: an unknown token costs one
     * filesystem probe, not one per line.
     */
    private static final Map<String, Optional<String>> SYNTAX_NAMES = new ConcurrentHashMap<>();

    /**
     * The short forms of the conf/nanorc file names.
     */
    private static final Map<String, String> LANGUAGE_ALIASES = Map.of(
            "js", "javascript", "ts", "javascript", "py", "python",
            "yml", "yaml", "md", "markdown", "htm", "html");

    /**
     * The {@code syntax "Java"} declaration opening a nanorc file.
     */
    private static final Pattern SYNTAX_DECLARATION = Pattern.compile("^syntax\\s+\"?([^\"\\s]+)");

    private static Optional<String> resolveSyntax(final String token) {
        if (token.equals("txt") || token.equals("text") || token.equals("plain") || token.equals("none"))
            return Optional.empty();
        final Path nanorc = Highlighter.configurations.getConfig(
                LANGUAGE_ALIASES.getOrDefault(token, token) + ".nanorc");
        // no nanorc of that name: the token means plain text, never a system syntax
        return null == nanorc ? Optional.empty() : Optional.ofNullable(declaredSyntaxName(nanorc));
    }

    private static String declaredSyntaxName(final Path nanorc) {
        try {
            for (final String line : Files.readAllLines(nanorc)) {
                final Matcher declaration = SYNTAX_DECLARATION.matcher(line.trim());
                if (declaration.find()) return declaration.group(1);
            }
        } catch (final Exception e) {
            // a nanorc that cannot be read means no highlighting — never a broken render
        }
        return null;
    }

    /**
     * Highlight a whole block of source in one pass, terminal-less (a memoized
     * block is then identical whatever terminal it is finally drawn on).
     *
     * <p>Unlike {@link #highlightLine(String, String)} this never routes the text
     * through {@link Graphitty}: a block is foreign source, and source containing
     * {@code {{…}}} must reach the terminal with its braces intact.  The
     * multi-line form is also what makes a block comment stay a comment to its
     * close, and it is the reason a block is memoized whole rather than per line.
     *
     * @param language the syntax to use — a conf/nanorc token (e.g. {@code java})
     * @param code     the block to colorize
     * @return the colorized block, or the block itself when the token has no syntax
     */
    public static String highlightBlock(final String language, final String code) {
        if (null == code || code.isEmpty()) return "";
        final String syntax = syntaxName(language);
        if (null == syntax) return code;
        final String key = syntax + '\u0000' + code;
        final String cached = BLOCK_CACHE.get(key);
        if (null != cached) return cached;
        final String highlighted = colorize(syntax, code);
        if (code.length() <= BLOCK_CACHE_MAX_CHARS)
            BLOCK_CACHE.put(key, highlighted);
        return highlighted;
    }

    /**
     * A colorizer for ONE block whose multi-line state carries from call to call.
     *
     * <p>A block is normally colorized in one pass, but its text can reach the caller in
     * pieces: a widget draws its border and colours into the middle of the block it is
     * rendering.  Highlighting each piece on its own would reset jline's state at every
     * seam — a comment opened on one line of the block would stop being a comment on the
     * next — and a memo keyed by text alone would be wrong for the same reason, so a
     * block that is split is colorized through this instead.
     *
     * <p>{@link #highlight(String)} locks the shared highlighter for the duration of the
     * call: an instance is not thread safe, and the console render thread shares it with
     * the loggers.
     */
    public static Block block(final String language) {
        return new Block(syntaxName(language));
    }

    public static final class Block {
        private final SyntaxHighlighter highlighter;

        private Block(final String syntax) {
            this.highlighter = null == syntax ? null : highlighterFor(syntax);
        }

        /**
         * Colorize the next piece of the block.  A block whose token names no syntax is
         * handed back as it is, so a caller never has to special-case plain text.
         */
        public String highlight(final String code) {
            if (null == this.highlighter || null == code || code.isEmpty()) return code;
            synchronized (this.highlighter) {
                return this.highlighter.highlight(code).toAnsi();
            }
        }
    }

    /**
     * Highlighters are built once per syntax and reused: a build walks the whole nanorc
     * include tree, and a block is re-highlighted on every render pass of the widget
     * holding it.
     */
    private static SyntaxHighlighter highlighterFor(final String syntax) {
        return SYNTAX_HIGHLIGHTERS.computeIfAbsent(syntax,
                name -> SyntaxHighlighter.build(Highlighter.configurations.getConfig("jnanorc"), name));
    }

    private static final Map<String, SyntaxHighlighter> SYNTAX_HIGHLIGHTERS = new ConcurrentHashMap<>();

    /**
     * One whole text, one pass: the shared highlighter is reset first, so a block never
     * inherits the state of the block before it.
     */
    private static String colorize(final String syntax, final String code) {
        try {
            final SyntaxHighlighter highlighter = highlighterFor(syntax);
            synchronized (highlighter) {
                return highlighter.reset().highlight(code).toAnsi();
            }
        } catch (final Exception e) {
            return code;   // a syntax library must never break a render
        }
    }

    /**
     * The number of memoized highlighted lines (for tests and diagnostics).
     */
    static int highlightedLineCacheSize() {
        return LINE_CACHE.size();
    }

    private Highlighter(final SyntaxHighlighter syntaxHighlighter) {
        this.syntaxHighlighter = syntaxHighlighter;
        this.graphitty = new Graphitty(Map.of(), new ByteArrayOutputStream());
        // the link serializer, not the plain one: this is the instance every renderer formats
        // objs with, and a uri reaches writeUri only if the serializer that draws it tags it
        this.serializer = new ObjLinkSerializer();
    }

    public Highlighter(final ObjSerializer<String> serializer) {
        this.syntaxHighlighter = SyntaxHighlighter.build(Highlighter.configurations.getConfig("jnanorc"), "mtron");
        this.graphitty = new Graphitty(Map.of(), new ByteArrayOutputStream());
        this.serializer = serializer;
    }

    public Highlighter(final String language) {
        this.syntaxHighlighter = SyntaxHighlighter.build(Highlighter.configurations.getConfig("jnanorc"), language);
        this.graphitty = new Graphitty(Map.of(), new ByteArrayOutputStream());
        this.serializer = new ObjPlainTextSerializer();
    }

    public Highlighter(final ObjSerializer<String> serializer, final boolean ignoreGraphitty) {
        this.syntaxHighlighter = SyntaxHighlighter.build(Highlighter.configurations.getConfig("jnanorc"), "mtron");
        this.graphitty = ignoreGraphitty ? null : new Graphitty(Map.of(), new ByteArrayOutputStream());
        this.serializer = serializer;
    }

    public Highlighter justify(final boolean leftJustify) {
        this.serializer = new ObjmtronSerializer(leftJustify);
        return this;
    }

    public String write(final Object object) {
        return this.highlight(object);
    }

    public static String format(final Object object) {
        return INSTANCE.highlight(object);
    }

    public static String format(final String f, final Object... args) {
        return INSTANCE.highlight(f.formatted(args));
    }

    public static String unformat(final String string) {
        return Graphitty.strip(string);
    }

    /**
     * The display columns {@code string} occupies once its markup is stripped — the
     * measure the widgets lay themselves out with.  Delegates to
     * {@link Graphitty#viewLength}, which counts columns and not characters: a CJK
     * glyph is two of them, a combining mark or variation selector none, an emoji one
     * glyph across two chars.
     */
    public static int visualLength(final String string) {
        return Graphitty.viewLength(string);
    }

    public void setTerminal(final Terminal terminal) {
        this.terminal = terminal;
    }

    /**
     * The line colored as syntax with its graphitty rules masked, ready for graphitty to resolve.
     * <p>
     * The highlighter cannot colour a line that still contains rules — it tokenizes the rule itself
     * and graphitty then sees no rule at all — so each rule is replaced by a run of spaces: blanks
     * occupy the same columns and leave the surrounding tokens alone, which a filler character does
     * not (a control character broke every rule but the uri one, so a result came out with blue
     * uris and everything else white).  The line is then coloured in one pass, and each masked span
     * is put back as the rule it stood for.
     */
    private String colorMarkupMasked(final String str) {
        final java.util.regex.Matcher m = this.GRAPHITTY_PATTERN.matcher(str);
        final StringBuilder masked = new StringBuilder(str.length());
        final java.util.List<int[]> spans = new java.util.ArrayList<>();   // visible start, width, source start
        int last = 0;
        while (m.find()) {
            masked.append(str, last, m.start());
            spans.add(new int[]{masked.length(), m.end() - m.start(), m.start()});
            masked.append(" ".repeat(m.end() - m.start()));
            last = m.end();
        }
        if (spans.isEmpty()) return this.highlight(null, str).toAnsi();
        masked.append(str, last, str.length());
        return this.spliceRules(this.highlight(null, masked.toString()).toAnsi(), spans, str);
    }

    /** The index just past the SGR sequence starting at {@code from} (see {@link #spliceRules}). */
    private static int escapeEnd(final String text, final int from) {
        final int end = text.indexOf('m', from);
        return end < 0 ? text.length() : end + 1;
    }

    /**
     * Put each rule back where its mask was: the coloured text is walked by visible column — escapes
     * are copied through and do not count — and each masked span is replaced by the source it stood
     * for, so the rules reach graphitty intact.
     */
    private String spliceRules(final String colored, final java.util.List<int[]> spans, final String source) {
        final StringBuilder out = new StringBuilder(colored.length() + 64);
        int visible = 0;
        int at = 0;
        int span = 0;
        while (at < colored.length()) {
            while (span < spans.size() && visible == spans.get(span)[0]) {
                final int[] s = spans.get(span);
                // The colouring pass styles the mask's columns too, and that styling belongs to the
                // label against it: it is what carries the label's own color when the tokenizer
                // attached the color to the mask rather than to the word (a label came out magenta
                // where it should be blue, inheriting the color that preceded the link).
                //
                // It is written BEFORE the rule, never between the rules: anything inside
                // {{link}}…{{/link}} is part of what that rule captures, and styling there is what
                // left the span unlinkable so a click had nothing to resolve.
                final StringBuilder kept = new StringBuilder();
                int skipped = 0;
                while (at < colored.length() && skipped < s[1]) {
                    if ('\033' == colored.charAt(at)) {
                        final int stop = escapeEnd(colored, at);
                        kept.append(colored, at, stop);
                        at = stop;
                        continue;
                    }
                    at++;
                    skipped++;
                }
                out.append(kept);
                out.append(source, s[2], s[2] + s[1]);
                visible += s[1];
                span++;
            }
            if (at >= colored.length()) break;
            if ('\033' == colored.charAt(at)) {
                final int end = colored.indexOf('m', at);
                out.append(colored, at, end < 0 ? colored.length() : end + 1);
                at = end < 0 ? colored.length() : end + 1;
                continue;
            }
            out.append(colored.charAt(at++));
            visible++;
        }
        return out.toString();
    }

    public String highlight(final Object object) {
        try {
            if (object instanceof Obj) {
                // Serialize, then format that text the way any other text is formatted.  An obj used
                // to have a path of its own, and it went wrong in two ways: it short-circuited to
                // graphitty whenever the serialization carried markup ({{…}} rules are how the mtron
                // serializer draws plenty of values), so the tokens between the rules — a uri among
                // them — were never colored; and it never tagged a uri, because the serializer it
                // asked was the plain one.  The String path below does both: it lifts the markup
                // out, colors what is between it, puts the rules back and lets graphitty resolve
                // them, {{link}} included.
                return this.highlight(this.serializer.write((Obj) object));
            } else {
                final String str = object.toString();
                if (containsBoxDrawing(str))
                    return this.preserveBoxDrawing(str);
                if (null == this.graphitty)
                    return this.highlight(null, str).toAnsi();
                if (Graphitty.hasFence(str))
                    return this.graphitty.writeToString(str);   // a fence is graphitty's to interpret
                if (this.GRAPHITTY_PATTERN.matcher(str).find())
                    // Markup AND color wanted: color the line in one pass with the rules masked, then
                    // put them back and let graphitty resolve them.  Coloring each fragment between
                    // rules on its own loses the color in the AttributedString round-trip, and letting
                    // graphitty draw the raw text skips coloring altogether -- which is how a result
                    // row came out white with nothing in it but link spans.
                    return this.graphitty.writeToString(this.colorMarkupMasked(str));
                return this.highlight(null, str).toAnsi();
            }
        } catch (final Exception e) {
            return object.toString();
        }
    }

    /**
     * JLine's {@link AttributedString#toAnsi()} maps box-drawing glyphs
     * (U+2500–U+257F) to VT100 alternate-charset codepoints or ASCII
     * ({@code ├ ─ │} → {@code + - |}) depending on terminal capabilities,
     * mangling pre-formatted content such as tree widgets.  Such content is
     * already terminal-ready UTF-8 — return it verbatim (expanding any
     * Graphitty markup) rather than passing it through the ANSI converter.
     */
    private String preserveBoxDrawing(final String string) {
        return null != this.graphitty
                && (this.GRAPHITTY_PATTERN.matcher(string).find() || Graphitty.hasFence(string))
                ? this.graphitty.writeToString(string)
                : string;
    }

    private static boolean containsBoxDrawing(final String string) {
        for (int i = 0; i < string.length(); i++) {
            final char c = string.charAt(i);
            if (c >= 0x2500 && c <= 0x257F) return true;
        }
        return false;
    }

    @Override
    public AttributedString highlight(final LineReader reader, final String buffer) {
        // The reader's line is the user's OWN text, so it is coloured as syntax and nothing else.
        // Resolving graphitty here rewrites what they are typing as they type it: {{link}} stops
        // being the characters they see (a name in braces is swallowed as a rule, a tag they have
        // not closed yet changes the line under the cursor), and the line re-flows mid-word.  The
        // markup still applies to whatever they SUBMIT — that is the echo and the results, which
        // go through format() instead.
        // reset first: the highlighter is a streaming one, and a line coloured on a highlighter left
        // mid-construct (the shared instance is used by renderers on many threads) comes back
        // uncoloured — which is how a result row rendered white while the echo of the same text,
        // coloured through another instance, looked right
        synchronized (this.syntaxHighlighter) {
            return this.syntaxHighlighter.reset().highlight(buffer);
        }
    }
}
