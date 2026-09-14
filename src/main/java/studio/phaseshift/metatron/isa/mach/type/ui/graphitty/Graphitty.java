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

package studio.phaseshift.metatron.isa.mach.type.ui.graphitty;

import org.jline.utils.AttributedString;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Highlighter;
import studio.phaseshift.metatron.util.MTronException;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;

public class Graphitty {
    public static final Map<String, String> COLOR_REWRITES = new LinkedHashMap<>();

    // TODO: cherry pick from: https://gist.github.com/jonlabelle/7a76ecd29976aeb30877be326c683979

    public static final String RULE_SEPARATOR = "&";

    /**
     * {@code {{syntax:java}}} … {@code {{/syntax:java}}} — everything between the
     * tags is foreign source: captured verbatim (the DSL is inert inside, so code
     * carrying {@code {{…}}} or a {@code \{\}} arrives intact) and colorized by
     * {@link Highlighter#highlightBlock(String, String)}, which maps the language
     * token to a {@code conf/nanorc} syntax file.  The end tag must name the same
     * language as the open tag.
     */
    public static final String SYNTAX_RULE_PREFIX = "syntax:";
    public static final Map<String, String> CURSOR_REWRITES = new LinkedHashMap<>();
    private static final Graphitty GRAPHITTY_STDOUT = new Graphitty(System.out);

    static {
        COLOR_REWRITES.put("X", "\033[m");  // reset
        COLOR_REWRITES.put("k", "\033[30m"); // black
        COLOR_REWRITES.put("r", "\033[31m"); // red
        COLOR_REWRITES.put("g", "\033[32m"); // green
        COLOR_REWRITES.put("y", "\033[33m");  // yellow
        COLOR_REWRITES.put("b", "\033[34m"); // blue
        COLOR_REWRITES.put("m", "\033[35m"); // magenta
        COLOR_REWRITES.put("c", "\033[36m"); // cyan
        COLOR_REWRITES.put("w", "\033[37m"); // white
        COLOR_REWRITES.put("d", "\033[39m"); // default
        /// //
        COLOR_REWRITES.put("[k]", "\033[40m"); // red
        COLOR_REWRITES.put("[r]", "\033[41m"); // red
        COLOR_REWRITES.put("[g]", "\033[42m"); // green
        COLOR_REWRITES.put("[y]", "\033[43m");  // yellow
        COLOR_REWRITES.put("[b]", "\033[44m"); // blue
        COLOR_REWRITES.put("[m]", "\033[45m"); // magenta
        COLOR_REWRITES.put("[c]", "\033[46m"); // cyan
        COLOR_REWRITES.put("[w]", "\033[47m"); // white
        COLOR_REWRITES.put("[X]", "\033[49m"); // default
        /// //
        COLOR_REWRITES.put("R", "\033[1;31m"); // bold red
        COLOR_REWRITES.put("G", "\033[1;32m"); // bold green
        COLOR_REWRITES.put("Y", "\033[1;33m"); // bold yellow
        COLOR_REWRITES.put("B", "\033[1;34m"); // bold blue
        COLOR_REWRITES.put("M", "\033[1;35m"); // bold magenta
        COLOR_REWRITES.put("C", "\033[1;36m"); // bold cyan
        COLOR_REWRITES.put("W", "\033[1;37m"); // bold white
        COLOR_REWRITES.put("~", "\033[3m"); // italics
        COLOR_REWRITES.put("_", "\033[4m"); // underline
        COLOR_REWRITES.put("-", "\033[9m"); // strikethrough
        COLOR_REWRITES.put("BEL", "\\a");
    }

    static {
        CURSOR_REWRITES.put("@", "\033[H"); // home
        CURSOR_REWRITES.put("v<", "\033[{{v<}}E"); // move cursor to beginning of next X line
        CURSOR_REWRITES.put("^<", "\033[{{v<}}F"); // move cursor to beginning of previous X line
        CURSOR_REWRITES.put("^+", "<redo>");
        CURSOR_REWRITES.put("^", "\033[{{^}}A"); // up X
        CURSOR_REWRITES.put("v", "\033[{{v}}B"); // down X
        CURSOR_REWRITES.put(">", "\033[{{>}}C"); // right X
        CURSOR_REWRITES.put("<", "\033[{{<}}D"); // left X
        CURSOR_REWRITES.put("|", "\033[{{|}}G"); // column X
        CURSOR_REWRITES.put("-", "\033[{{-}}H"); // row X
        CURSOR_REWRITES.put("X-", "\033[0K");  // clear line right
        CURSOR_REWRITES.put("-X", "\033[1K");  // clear line left
        CURSOR_REWRITES.put("-X-", "\033[2K");  // clear line
        CURSOR_REWRITES.put("Xv", "\033[0J"); // clear to bottom of screen
        CURSOR_REWRITES.put("X^", "\033[1J"); // clear to top of screen
        CURSOR_REWRITES.put("(s)", "\033[s"); // save
        CURSOR_REWRITES.put("(e)", "\033[u"); // load
        CURSOR_REWRITES.put("XX", "\033[2J"); // clear screen
        CURSOR_REWRITES.put("*", "\033[?25h"); // show cursor
        CURSOR_REWRITES.put(".", "\033[?25l"); // hide cursor
        // CURSOR_REWRITES.put("X", "\033[{{<}}D");
    }

    private final OutputStream out;
    private final Map<String, String> rewrites;
    private final Stack<String> rewriteStack = new Stack<>();
    private boolean ansiOn = true;

    /**
     * Language of the {@code {{syntax:lang}}} block currently being captured, or
     * {@code null} when not inside one.
     */
    private String syntaxLanguage;

    /**
     * Literal text of the block being captured, or {@code null} when not inside one —
     * the buffer itself is the "capturing" state.  Only LITERAL text lands here: the
     * markup of the surrounding document (a widget's borders, a colour, a cursor
     * code) still applies inside a block, which is what lets a widget decorate the
     * lines of a block it is drawing.  See {@link #flushSyntax(boolean)}.
     */
    private StringBuilder syntaxBlock;

    /** Colorizer of the block being captured, carrying jline's multi-line state across the seams. */
    private Highlighter.Block syntaxSession;

    /** Set once markup split a block into pieces, so the block is no longer one text. */
    private boolean syntaxFragmented;

    /**
     * {@link #parseDSL} recursion depth (a rule rewrite re-enters it).  A block
     * left open belongs to the outermost call, which flushes it: an unterminated
     * block renders, it does not throw.
     */
    private int parseDepth;

    public Graphitty(final Map<String, String> rewrites, final OutputStream out) {
        this.out = out;
        this.rewrites = new HashMap<>();
        this.rewrites.putAll(Graphitty.COLOR_REWRITES);
        this.rewrites.putAll(Graphitty.CURSOR_REWRITES);
        this.rewrites.putAll(rewrites);
    }

    public Graphitty(final OutputStream out) {
        this(Map.of(), out);
    }

    public static GraphittyLogger log(final Object source) {
        return source instanceof Obj && !(source instanceof Router) ? new GraphittyObjLogger((Obj) source) : new GraphittyLogger(source);
    }

    /*    public static GraphittyLogger log(final Object source, final Level level) {
        final GraphittyLogger logger = source instanceof Obj && !(source instanceof Router) ? new GraphittyObjLogger((Obj) source) : new GraphittyLogger(source);
    }*/

    public static void out(final OutputStream out, final String f, final Object... args) {
        // Route terminal writes through the FloatingSurface render thread
        // so widget push/pop cursor sequences are never interleaved with
        // console output.
        final java.util.function.Consumer<String> writer = terminalWriter;
        if (writer != null && out == terminalOutput) {
            writer.accept(Graphitty.string(f, args));
            return;
        }
        synchronized (out) {
            final Graphitty g = new Graphitty(out);
            g.print(Graphitty.string(f, args));
        }
    }

    /**
     * The terminal OutputStream registered by Console at startup.
     */
    private static volatile OutputStream terminalOutput;

    /**
     * Optional bridge: when set, ALL terminal-bound writes are routed
     * through this consumer, serializing them on the FloatingSurface
     * render thread so widget cursor save/restore is never interleaved
     * with console output.
     */
    private static volatile java.util.function.Consumer<String> terminalWriter;

    /**
     * Register the terminal output stream. Called once by Console at startup.
     */
    public static void init(final OutputStream terminalOutput) {
        Graphitty.terminalOutput = terminalOutput;
    }

    /**
     * Register a terminal-writer bridge for serialized rendering.
     */
    public static void setTerminalWriter(final java.util.function.Consumer<String> writer) {
        Graphitty.terminalWriter = writer;
    }
    
   /* public static Graphitty stdout() {
        return GRAPHITTY_STDOUT;
    }

    public static String erase(int depth) {
        return "{{X-&v1}}".repeat(Math.max(0, depth)) + "{{^" + depth + "}}";
    }

    public static String floating(final String f) {
        final String strip = Graphitty.strip(f);
        List<Integer> backs = Arrays.stream(strip.split("\n")).map(String::length).toList();
        StringBuilder ret = new StringBuilder();
        int i = 0;
        for (final String line : f.split("\n")) {
            ret.append(line).append("{{v1&<").append(backs.get(i)).append("}}");
        }
        ret.append("{{^").append(backs.size()).append("}}");
        return ret.toString();
    }*/

    public String writeToString(final String f, final Object... args) {
        this.parseDSL(f.formatted(args));
        final String result = new String(((ByteArrayOutputStream) this.out).toByteArray(), StandardCharsets.UTF_8);
        ((ByteArrayOutputStream) this.out).reset();
        return result;
    }

    public static String string(final String f, final Object... args) {
        // The format call belongs INSIDE the try: a literal percent in the text
        // (a payload, an exception message, a log line) raises
        // UnknownFormatConversionException, and the fallback below — which escapes
        // percents and formats again — is what makes it literal.  Hoisting the
        // call out of the try makes that fallback unreachable (GraphittyLogger's
        // "logging must never break its caller" tests catch exactly this).
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            final Graphitty temp = new Graphitty(out);
            temp.parseDSL(f.formatted(args));
            return out.toString(StandardCharsets.UTF_8);
        } catch (final Exception e) {
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                final Graphitty temp = new Graphitty(out);
                temp.parseDSL(f.replace("%", "%%").formatted(args));
                return out.toString(StandardCharsets.UTF_8);
            } catch (final Exception e2) {
                System.out.println("graphitty error processing: " + f);
                throw MTronException.of(e);
            }
        }
    }

    public static String sillyPrint(final String text, final boolean rainbow, final boolean rollercoaster) {
        final Random random = new Random();
        final String colors = "rgbmcy";
        final StringBuilder ret = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (rainbow)
                ret.append("{{").append(colors.charAt(random.nextInt(colors.length()))).append("}}");
            ret.append((rollercoaster ? (random.nextBoolean() ?
                    ("" + text.charAt(i)).toLowerCase(Locale.ROOT) :
                    ("" + text.charAt(i)).toUpperCase(Locale.ROOT)) : text.charAt(i)));
        }
        if (rainbow)
            ret.append("{{X}}");
        return ret.toString();
    }

    public static String strip(final String string) {
        if (null == string || string.isEmpty()) return "";
        // Fast path: a line with no DSL codes, no ANSI escapes and nothing
        // outside ASCII has nothing to strip, and `strip` is called per line by
        // every widget that measures its own text — building a parser for each
        // of those lines cost ~2.4us, which a big widget paid hundreds of times
        // per render pass.
        if (!needsParsing(string)) return string;
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final Graphitty temp = new Graphitty(out);
        temp.ansiOn = false;
        temp.parseDSL(AttributedString.stripAnsi(string));
        return out.toString(StandardCharsets.UTF_8);
    }

    /**
     * True when {@link #strip(String)} has work to do: the string carries a
     * DSL code ({@code {{…}}}), an ANSI escape, or a non-ASCII character (the
     * parser treats those specially so their display width stays right).
     */
    private static boolean needsParsing(final String string) {
        for (int i = 0; i < string.length(); i++) {
            final char c = string.charAt(i);
            if (c == '{' || c == '\u001b' || c > 126) return true;
        }
        return false;
    }

    public static int viewLength(final String string) {
        return strip(string).length();
    }

    private void parseDSL(final String buffer) {
        this.parseDepth++;
        try {
            final int bufferLength = buffer.length();
            for (int i = 0; i < bufferLength; i++) {
                if (buffer.charAt(i) > 126) {
                    // Characters above ASCII 126 are not Graphitty control codes
                    // ({}, {{}}, \n, \t are all <= 126).  Write them as UTF-8 so
                    // they survive the ByteArrayOutputStream → toString() round-trip.
                    // Surrogate pairs (emoji) must be written as ONE code point —
                    // a lone UTF-16 surrogate encodes to a '?' replacement via
                    // getBytes(UTF_8), so a char-by-char write corrupts 🐿 to "??".
                    final int cp = buffer.codePointAt(i);
                    final int width = Character.charCount(cp);
                    this.emit(new String(Character.toChars(cp)));
                    i += width - 1;
                    continue;
                }
                if (buffer.charAt(i) == '\\' && i + 1 < bufferLength) {
                    final char j = buffer.charAt(i + 1);
                    if ('n' == j) {
                        this.newLine();
                        i++;
                    } else if ('t' == j) {
                        this.htab();
                        i++;
                    } else if ('{' == j) {
                        this.print("{");
                        i++;
                    } else if ('}' == j) {
                        this.print("}");
                        i++;
                    } else {
                        this.emit(buffer.charAt(i));
                    }
                } else if (i + 4 < buffer.length() &&
                        buffer.charAt(i) == '{' &&
                        buffer.charAt(i + 1) == '{' &&
                        buffer.charAt(i + 2) != '{') {
                    i = i + 2;
                    final StringBuilder rule = new StringBuilder();
                    // final boolean end = buffer.charAt(i) == '/';
                    // if (end) i++;
                    for (int m = i; m < bufferLength; m++) {
                        if (m + 1 < bufferLength && buffer.charAt(m) == '}' && buffer.charAt(m + 1) == '}') {
                            i += 2;
                            break;
                        }
                        rule.append(buffer.charAt(m));
                        i = m;
                    }
                    // {{syntax:java}} … {{/syntax:java}}: the literal text between the tags
                    // is source code, colorized from that language's conf/nanorc file.  The
                    // tags are handled in BOTH modes — rendering captures the code to colorize
                    // it, measuring captures it to keep it, so strip() measures what a terminal
                    // shows.  Inside a block the rest of the markup still applies; a rule
                    // therefore flushes the code captured so far BEFORE it is emitted, which
                    // keeps code and decoration in buffer order.  A rule only opens a block
                    // when it STARTS with the prefix: {{/syntax:java}} (and any other rule
                    // merely containing it) names no language.
                    if (null != this.syntaxBlock) {
                        if (rule.indexOf("/" + SYNTAX_RULE_PREFIX) == 0) {
                            if (("/" + SYNTAX_RULE_PREFIX + this.syntaxLanguage).contentEquals(rule)) {
                                this.closeSyntax();
                                continue;   // the end tag closes the block here and is not a rule of its own
                            }
                            throw MTronException.of("unmatched syntax wrap: %s != %s",
                                    rule, "/" + SYNTAX_RULE_PREFIX + this.syntaxLanguage);
                        }
                        this.flushSyntax(true);
                    } else if (rule.indexOf(SYNTAX_RULE_PREFIX) >= 0) {
                        final String language = syntaxPiece(rule.toString());
                        if (null != language)
                            this.openSyntax(language);
                    }
                    if (this.ansiOn) {
                        Stream.of(rule.toString().split(RULE_SEPARATOR))
                                .filter(p -> !p.isEmpty())
                                .forEach(rulePiece -> {
                                    if (rulePiece.charAt(0) == '/') {
                                        final String closeRule = rulePiece.substring(1);
                                        final String openRule = this.rewriteStack.pop();
                                        if (!openRule.equals(closeRule))
                                            throw MTronException.of("unmatched rule wrap: %s != %s [buffer: %s]", openRule, closeRule, buffer.replace("{{", "").replace("}}", ""));
                                        else {
                                            String reset = this.rewriteStack.isEmpty() ? null : this.rewrites.get(this.rewriteStack.peek());
                                            reset = null == reset ? this.rewrites.get("X") : reset.replace("\033[", "\033[0;");
                                            if (null != reset)
                                                this.parseDSL(reset);
                                        }
                                    } else {
                                        // {{:beer:}} → GitHub shortcode → Unicode emoji.  Emitted as
                                        // whole-string UTF-8 (emoji are surrogate pairs in Java, so
                                        // the per-char >126 branch would corrupt them).  No rewrite-
                                        // stack push, so {{:beer:&b}} composes with color rules.
                                        if (rulePiece.length() > 2
                                                && rulePiece.charAt(0) == ':'
                                                && rulePiece.charAt(rulePiece.length() - 1) == ':') {
                                            final String name = rulePiece.substring(1, rulePiece.length() - 1);
                                            final String emoji = EmojiTable.get(name);
                                            try {
                                                // Unknown shortcodes render the literal ":name:" text
                                                // (Slack/GitHub convention) so typos stay visible.
                                                this.out.write((null != emoji ? emoji : rulePiece).getBytes(StandardCharsets.UTF_8));
                                            } catch (final Exception e) {
                                                throw MTronException.of(e);
                                            }
                                            return;
                                        }
                                        // a block's own tag stays OFF the rewrite stack: markup
                                        // inside the block pushes and pops above whatever encloses
                                        // it, and the end tag is matched by name instead
                                        if (!rulePiece.startsWith(SYNTAX_RULE_PREFIX))
                                            this.rewriteStack.push(rulePiece);
                                        String r = this.rewrites.get(rulePiece);
                                        while (null != r && r.startsWith("{{") && r.endsWith("}}"))
                                            r = this.rewrites.get(r.substring(2, r.length() - 2));

                                        if (rulePiece.length() > 2 && Set.of("^<", "v<").contains(rulePiece.substring(0, 2))) {
                                            if (!rulePiece.substring(2).equals("0"))
                                                r = this.rewrites.get(rulePiece.substring(0, 2)).replace("{{" + rulePiece.substring(0, 2) + "}}", rulePiece.substring(2));
                                        } else if (Set.of('^', 'v', '<', '>', '|').contains(rulePiece.charAt(0))) {
                                            if (!rulePiece.substring(1).equals("0"))
                                                r = this.rewrites.get("" + rulePiece.charAt(0)).replace("{{" + rulePiece.charAt(0) + "}}", rulePiece.substring(1));
                                        }
                                        if (null != r) this.parseDSL(r);
                                    }
                                });
                    }

                } else {
                    this.emit(buffer.charAt(i));
                }
            }
            if (1 == this.parseDepth && null != this.syntaxLanguage)
                this.closeSyntax();   // {{syntax:…}} left open: flush what was captured
            this.flush();
        } catch (final Exception e) {
            throw MTronException.of(e);
        } finally {
            this.parseDepth--;
        }
    }

    /**
     * Enter a block: the literal text that follows is source code of {@code language}.
     * Nothing else changes — the markup of the surrounding document keeps working
     * inside the block, which is how a widget draws its border through one.
     */
    private void openSyntax(final String language) {
        this.syntaxLanguage = language.trim();
        this.syntaxBlock = new StringBuilder();
        // stateful: a block's text can reach us in pieces (a widget decorates its rows),
        // and jline's multi-line rule state — a comment opened on an earlier line — must
        // survive those seams
        this.syntaxSession = this.ansiOn ? Highlighter.block(this.syntaxLanguage) : null;
        this.syntaxFragmented = false;
    }

    /**
     * Emit the code captured so far, colorized.
     *
     * <p>A block's text is split whenever the surrounding document states markup, so this
     * is called mid-block as well as at its end.  Trailing whitespace is dropped from the
     * colorized part and emitted as it is: at a markup boundary it is layout (the padding
     * a widget puts before its border), and colouring it would paint the padding.
     *
     * @param midBlock {@code true} when decoration follows and the block continues — the
     *                 block is then no longer a single text and needs the stateful
     *                 colorizer rather than a memoized whole-block pass
     */
    private void flushSyntax(final boolean midBlock) {
        if (null == this.syntaxBlock || this.syntaxBlock.isEmpty()) return;
        final String pending = this.syntaxBlock.toString();
        this.syntaxBlock.setLength(0);
        if (midBlock) this.syntaxFragmented = true;
        final int end = trimTrailingWhitespace(pending);
        final String code = pending.substring(0, end);
        if (!code.isEmpty())
            this.writeRaw(null == this.syntaxSession ? code : this.syntaxSession.highlight(code));
        if (end < pending.length())
            this.writeRaw(pending.substring(end));
    }

    private static int trimTrailingWhitespace(final String string) {
        int end = string.length();
        while (end > 0 && Character.isWhitespace(string.charAt(end - 1))) end--;
        return end;
    }

    /**
     * Close the block: emit what it captured, then restore the rule that encloses it —
     * the same stack discipline {@code {{/rule}}} follows, so a block inside
     * {@code {{c}}…{{/c}}} leaves the cyan running after it.
     */
    private void closeSyntax() {
        final String language = this.syntaxLanguage;
        this.syntaxLanguage = null;
        final Highlighter.Block session = this.syntaxSession;
        final boolean fragmented = this.syntaxFragmented;
        this.syntaxSession = null;
        this.syntaxFragmented = false;
        if (!this.ansiOn) {
            this.flushSyntax(false);
            this.syntaxBlock = null;
            return;
        }
        if (fragmented) {
            this.syntaxSession = session;
            this.flushSyntax(false);
        } else {
            // nothing drew inside the block: the whole text is one pass, and a pass
            // repeated on every render of a widget is what the block memo exists for
            final String code = this.syntaxBlock.toString();
            if (!code.isEmpty())
                this.writeRaw(Highlighter.highlightBlock(language, code));
        }
        this.syntaxBlock = null;
        // what resumes after the block is the rule the stack leads with — the same restore
        // {{/rule}} performs, and the reason a block inside {{c}}…{{/c}} stays cyan
        String reset = this.rewriteStack.isEmpty() ? null : this.rewrites.get(this.rewriteStack.peek());
        reset = null == reset ? this.rewrites.get("X") : reset.replace("\033[", "\033[0;");
        if (null != reset)
            this.parseDSL(reset);
    }

    /**
     * The language a rule names ({@code r&syntax:java} → {@code java}), or {@code null}
     * when the rule names none — a close tag, or any rule merely containing the prefix.
     */
    private static String syntaxPiece(final String rule) {
        return Stream.of(rule.split(RULE_SEPARATOR))
                .filter(piece -> piece.startsWith(SYNTAX_RULE_PREFIX))
                .findFirst()
                .map(piece -> piece.substring(SYNTAX_RULE_PREFIX.length()))
                .orElse(null);
    }

    /**
     * Emit one literal character: into the block being captured when there is one (it is
     * code), otherwise straight to the output stream.
     */
    private void emit(final char c) {
        if (null != this.syntaxBlock) {
            this.syntaxBlock.append(c);
            return;
        }
        try {
            this.out.write(c);
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }

    /** Emit literal text (a code point above ASCII, written as UTF-8). */
    private void emit(final String string) {
        if (null != this.syntaxBlock)
            this.syntaxBlock.append(string);
        else
            this.writeRaw(string);
    }

    /**
     * Write text that is already rendered (ANSI escapes and all) without parsing it as
     * DSL — colorized code is terminal-bound, not markup.
     */
    private void writeRaw(final String string) {
        try {
            this.out.write(string.getBytes(StandardCharsets.UTF_8));
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }

    public Graphitty print(final char c) {
        this.parseDSL(Objects.toString(c));
        return this;
    }

    public Graphitty print(final String c) {
        this.parseDSL(c);
        return this;
    }

    public Graphitty println(final String c) {
        if (!c.isEmpty())
            this.print(c);
        this.print('\n');
        return this;
    }

    public Graphitty flush() {
        try {
            this.out.flush();
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    /// ///////////////////////

    public void newLine() {
        if (this.ansiOn)
            this.print('\n');
        else
            this.print("\n");
    }

    public void htab() {
        if (this.ansiOn)
            this.print('\t');
        else
            this.print("\t");
    }
}
