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

package studio.phaseshift.metatron.ui;

import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.sys.router.Router;
import studio.phaseshift.metatron.lang.util.serial.ObjStringSerializer;
import studio.phaseshift.metatron.util.MTronException;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.stream.Stream;

public class Graphitty {
    public static final Map<String, String> COLOR_REWRITES = new LinkedHashMap<>();

    // TODO: cherry pick from: https://gist.github.com/jonlabelle/7a76ecd29976aeb30877be326c683979

    public static final String RULE_SEPARATOR = "&";
    public static final Map<String, String> CURSOR_REWRITES = new LinkedHashMap<>();
    private static final Graphitty GRAPHITTY_STDOUT = new Graphitty(System.out);

    static {
        COLOR_REWRITES.put("X", "\033[m");  // reset
        COLOR_REWRITES.put("k", "\033[30m"); // red
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
        COLOR_REWRITES.put("[d]", "\033[49m"); // default
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
        CURSOR_REWRITES.put("XX", "\033[2J"); // clear screen
        CURSOR_REWRITES.put("*", "\0331b[?25h"); // show cursor
        CURSOR_REWRITES.put(".", "\0331b[?25l"); // hide cursor
        // CURSOR_REWRITES.put("X", "\033[{{<}}D");
    }

    private final OutputStream out;
    private final Map<String, String> rewrites;
    private final Stack<String> rewriteStack = new Stack<>();
    private boolean ansiOn = true;

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

    public static void out(final OutputStream out, final String f, final Object... args) {
        final Graphitty g = new Graphitty(out);
        g.print(Graphitty.string(f, args));
    }

    public static Graphitty stdout() {
        return GRAPHITTY_STDOUT;
    }

    public static String string(final String f, final Object... args) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            final Graphitty temp = new Graphitty(out);
            temp.parseDSL(f.formatted(args));
            return out.toString();
        } catch (final Exception e) {
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                final Graphitty temp = new Graphitty(out);
                temp.parseDSL(f.replace("%", "%%").formatted(args));
                return out.toString();
            } catch (final Exception e2) {
                System.out.println("graphitty error processing: " + f);
                throw MTronException.of(e);
            }
        }
    }

    public static String string(final Obj obj) {
        return ObjStringSerializer.build().create().write(obj);
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
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final Graphitty temp = new Graphitty(out);
        temp.ansiOn = false;
        temp.parseDSL(string);
        return out.toString();
    }

    public void removeRewrites(final Map<String, String> deadRewrites) {
        deadRewrites.forEach((k, v) -> {
            this.rewrites.remove(k);
        });
    }

    public void addRewrites(final Map<String, String> newRewrites) {
        this.rewrites.putAll(newRewrites);
    }

    public void clearRewrites() {
        this.rewrites.clear();
    }

    private void parseDSL(final String buffer) {
        try {
            final int bufferLength = buffer.length();
            for (int i = 0; i < bufferLength; i++) {
                if (buffer.charAt(i) > 126)
                    continue;
                if (buffer.charAt(i) == '\\') {
                    final char j = buffer.charAt(i + 1);
                    if ('n' == j) {
                        this.newLine();
                        i++;
                    } else if ('t' == j) {
                        this.htab();
                        i++;
                    } else {
                        this.out.write(buffer.charAt(i));
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
                        if (buffer.charAt(m) == '}' && buffer.charAt(m + 1) == '}') {
                            i += 2;
                            break;
                        }
                        rule.append(buffer.charAt(m));
                        i = m;
                    }
                    if (this.ansiOn) {
                        Stream.of(rule.toString().split(RULE_SEPARATOR)).forEach(rulePiece -> {
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
                    this.out.write(buffer.charAt(i));
                }
            }
            this.flush();
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

   /*static String strip(final String s) {
        var a = new String();
        final var b = StringPrinter(a);
        var ansi = Ansi < StringPrinter > (b);
        ansi.ansi_switch(false);
        ansi.print(s.c_str());
        ansi.flush();
        var ret = String(ansi.get_printer().get());
        return ret;
    }*/

    public Graphitty flush() {
        try {
            this.out.flush();
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    /// ///////////////////////

    public void clearLine() {
        if (this.ansiOn)
            this.print("\033[2K");
    }

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

    /*
    ESC[H	moves cursor to home position (0, 0)
    ESC[{line};{column}H
    ESC[{line};{column}f	moves cursor to line #, column #
    ESC[#A	moves cursor up # lines
    ESC[#B	moves cursor down # lines
    ESC[#C	moves cursor right # columns
    ESC[#D	moves cursor left # columns
    ESC[#E	moves cursor to beginning of next line, # lines down
    ESC[#F	moves cursor to beginning of previous line, # lines up
    ESC[#G	moves cursor to column #
    ESC[6n	request cursor position (reports as ESC[#;#R)
    ESC M	moves cursor one line up, scrolling if needed
    ESC 7	save cursor position (DEC)
    ESC 8	restores the cursor to the last saved position (DEC)
    ESC[s	save cursor position (SCO)
    ESC[u	restores the cursor to the last saved position (SCO)*/

    /// //////////// CURSOR MOVEMENT ///////////////

    public void teleport(final int row, final int column) {
        if (this.ansiOn) {
            this.print("\033[");
            this.print(Objects.toString(row));
            this.print(';');
            this.print(Objects.toString(column));
            this.print('H');
        }
    }
}
