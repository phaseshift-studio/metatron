/*
 *   Metatron: A Distributed Virtual Machine
 *   Copyright (c) 2024 PhaseShift Studio, LLC
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.ui;

import studio.phaseshift.metatron.lang.obj.base.Obj;
import studio.phaseshift.metatron.util.MTronException;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.*;

import static studio.phaseshift.metatron.lang.obj.BObj.MTRON_CORE_TYPES;

public class Graphitty {
    public static final Map<String, String> COLOR_REWRITES = new LinkedHashMap<>();

    // TODO: cherry pick from: https://gist.github.com/jonlabelle/7a76ecd29976aeb30877be326c683979

    static {
        COLOR_REWRITES.put("X", "\033[m");  // reset
        COLOR_REWRITES.put("r", "\033[31m"); // red
        COLOR_REWRITES.put("g", "\033[32m"); // green
        COLOR_REWRITES.put("y", "\033[33m");  // yellow
        COLOR_REWRITES.put("b", "\033[34m"); // blue
        COLOR_REWRITES.put("m", "\033[35m"); // magenta
        COLOR_REWRITES.put("c", "\033[36m"); // cyan
        COLOR_REWRITES.put("w", "\033[37m"); // white
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

    public static final Map<String, String> CURSOR_REWRITES = new LinkedHashMap<>();

    static {
        CURSOR_REWRITES.put("@", "\033[H"); // home
        CURSOR_REWRITES.put("^", "\033[{{^}}A"); // up X
        CURSOR_REWRITES.put("v", "\033[{{v}}B"); // down X
        CURSOR_REWRITES.put(">", "\033[{{>}}C"); // right X
        CURSOR_REWRITES.put("<", "\033[{{<}}D"); // left X
        CURSOR_REWRITES.put("|", "\033[{{|}}G"); // column X
        CURSOR_REWRITES.put("-", "\033[{{-}}H"); // row X
        CURSOR_REWRITES.put("-X", "\033[2K");  // clear line
        CURSOR_REWRITES.put("*", "\0331b[?25h"); // show cursor
        CURSOR_REWRITES.put(".", "\0331b[?25l"); // hide cursor
        // CURSOR_REWRITES.put("X", "\033[{{<}}D");
    }

    public static final Map<String, String> OBJ_REWRITES = new LinkedHashMap<>();

    static {
        OBJ_REWRITES.put("DEBUG", "{{y}}");
        OBJ_REWRITES.put("INFO", "{{g}}");
        OBJ_REWRITES.put("WARN", "{{y}}");
        OBJ_REWRITES.put("ERROR", "{{r}}");
        OBJ_REWRITES.put("TYPE", "{{b}}");
        OBJ_REWRITES.put("VALUE", "{{y}}");
        OBJ_REWRITES.put("FORM1", "{{g}}");
        OBJ_REWRITES.put("FORM2", "{{m}}");
    }

    private OutputStream out;
    private final Map<String, String> rewrites;
    private boolean ansiOn = true;
    private final Stack<String> rewriteStack = new Stack<>();
    private static final Graphitty GRAPHITTY_STDOUT = new Graphitty(System.out);

    public static GraphittyLogger log(final Object source) {
        return new GraphittyLogger(source);
    }

    public static void out(final OutputStream out, final String s) {
        final Graphitty g = new Graphitty(out);
        g.print(s);
    }

    public static Graphitty stdout() {
        return GRAPHITTY_STDOUT;
    }

    public static String string(final String s) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final Graphitty temp = new Graphitty(out);
        temp.parseDSL(s);
        return out.toString();
    }

    public static String string(final Obj obj) {
        return "hello";
    }


    public Graphitty(final Map<String, String> rewrites, final OutputStream out) {
        this.out = out;
        this.rewrites = new HashMap<>();
        this.rewrites.putAll(Graphitty.COLOR_REWRITES);
        this.rewrites.putAll(Graphitty.CURSOR_REWRITES);
        this.rewrites.putAll(Graphitty.OBJ_REWRITES);
        this.rewrites.putAll(rewrites);
    }

    public Graphitty(final OutputStream out) {
        this(Map.of(), out);
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
                    final boolean end = buffer.charAt(i) == '/';
                    if (end) i++;
                    for (int m = i; m < bufferLength; m++) {
                        if (buffer.charAt(m) == '}' && buffer.charAt(m + 1) == '}') {
                            i += 2;
                            break;
                        }
                        rule.append(buffer.charAt(m));
                        i = m;
                    }
                    if (end) {
                        final String openRule = this.rewriteStack.pop();
                        if (!openRule.contentEquals(rule))
                            throw MTronException.of("closing doesn't match opening rule: %s != %s", openRule, rule.toString());
                        else {
                            String reset = this.rewriteStack.isEmpty() ? null : this.rewrites.get(this.rewriteStack.peek());
                            reset = null == reset ? this.rewrites.get("X") : reset.replace("\033[", "\033[0;");
                            if (null != reset)
                                this.parseDSL(reset);
                        }
                    } else {
                        this.rewriteStack.push(rule.toString());
                        String r = this.rewrites.get(rule.toString());
                        while (null != r && r.startsWith("{{") && r.endsWith("}}"))
                            r = this.rewrites.get(r.substring(2, r.length() - 2));
                        if (Set.of('^', 'v', '<', '>', '|').contains(rule.charAt(0))) {
                            if (!rule.substring(1).equals("0"))
                                r = this.rewrites.get("" + rule.charAt(0)).replace("{{" + rule.charAt(0) + "}}", rule.substring(1));
                        }
                        if (null == r)
                            throw new IllegalStateException("unknown rule: %s\n\t%s".formatted(rule, buffer));
                        this.parseDSL(r);
                    }

                } else {
                    this.out.write(buffer.charAt(i));
                }
                ////////////////////////////////// POSITION !^d = down
               /* else if ('^' == j) {
                    final char dir = buffer.charAt(i + 2);
                    String s = "";
                    for (int m = i + 3; m < bufferLength; m++) {
                        if (buffer.charAt(m) == '^')
                            break;
                        s += buffer.charAt(m);
                        i = m;
                    }
                    short steps = Short.parseShort(s);
                  /*  if (dir == 'S')
                        this.save_cursor(steps);
                    else if (dir == 'L')
                        this.load_cursor(steps);*/
                   /* if (dir == 'u')
                        this.up(steps);
                    else if (dir == 'l')
                        this.left(steps);
                    else if (dir == 'd')
                        this.down(steps);
                    else if (dir == 'r')
                        this.right(steps);
                    else if (dir == 't') {
                        String t = "";
                        for (int m = i + 3; m < bufferLength; m++) {
                            if (buffer.charAt(m) == '^')
                                break;
                            t += buffer.charAt(m);
                            i = m;
                        }
                        this.teleport(steps, Short.parseShort(t));
                    }
                }
                ////////////////////////////// FONT
                // else if('*' == j)
                //   this.background();
                else if ('_' == j)
                    this.underline();
                else if ('-' == j)
                    this.strike_through();
                else if ('~' == j)
                    this.italic();
                else if ('*' == j)
                    this.blink();
                else if ('X' == j)
                    this.clear();
                else if ('Q' == j)
                    this.top_left();
                else if ('Z' == j)
                    this.bottom_left();
                else if ('H' == j)
                    this.home();
                else if (!Character.isAlphabetic(j)) {
                    this.out.write(buffer.charAt(i));
                    decr = true;
                } else {
                    ////////////////////////////// COLOR
                    if (Character.isUpperCase(j))
                        this.bold();
                    final char jj = Character.toLowerCase(j);
                    if ('r' == jj)
                        this.red();
                    else if ('g' == jj)
                        this.green();
                    else if ('b' == jj)
                        this.blue();
                    else if ('m' == jj)
                        this.magenta();
                    else if ('c' == jj)
                        this.cyan();
                    else if ('w' == jj)
                        this.white();
                    else if ('y' == jj)
                        this.yellow();
                    else if ('d' == jj)
                        this.black();
                    else {
                        this.out.write(buffer.charAt(i));
                        decr = true;
                    }
                }
                if (!decr)
                    i++;
            } else{*/
                //         this.out.write(buffer.charAt(i));
                //   }

            }
            this.flush();
        } catch (final Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    public void ansi_switch(boolean turn_on) {
        this.ansiOn = turn_on;
    }

    public boolean is_ansi_on() {
        return this.ansiOn;
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
        if (c.length() > 0)
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


    /// ///////////////////////

    public void clear() {
        if (this.ansiOn)
            this.print("\033[2J");
    }

    public void italic() {
        if (this.ansiOn)
            this.print("\033[3m");
    }

    public void underline() {
        if (this.ansiOn)
            this.print("\033[4m");
    }

    public void strike_through() {
        if (this.ansiOn)
            this.print("\033[9m");
    }

    public void reverse() {
        if (this.ansiOn)
            this.print("\033[7m");
    }

    public void bold() {
        if (this.ansiOn)
            this.print("\033[1m");
    }

    public void blink() {
        if (this.ansiOn)
            this.print("\033[5m");
    }

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

// void background() {
//   if (this._on)
//     this.print("\033[40m");
// }

    /// /////// POSITIONING

    public void top_left() {
        if (this.ansiOn)
            this.print("\033[H");
    }

    public void bottom_left() {
        if (this.ansiOn)
            this.print("\033[F");
    }


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

    public void home() {
        if (this.ansiOn) {
            this.print("\033[H");
        }
    }

    public void move(final char direction, final int columns_or_rows) {
        if (this.ansiOn) {
            this.print("\033[");
            this.print((char) columns_or_rows);
            this.print(direction);
        }
    }

    public void cursor(final boolean visible) {
        if (this.ansiOn) {
            this.print(visible ? "\0331b[?25h" : "\0331b[?25l"); // use to be \x
        }
    }

    /*void save_cursor(final short slot =0) {
        if (this.ansi_on_) {
            if (0 == slot) {
                this.print("\033[s");
            } else {
                this.location(this.slots[slot]);
            }
        }
    }

    void load_cursor(final short slot) {
        if (this.ansi_on_) {
            if (0 == slot) {
                this.print("\033[u");
            } else {
                this.teleport(this.slots[slot][0], this.slots[slot][1]);
            }
        }
    }

    void location(int *pos) {
        if (this.ansi_on_) {
            this.print("\033[6n");
            char c = this.read(); // esc
            c = this.read(); // [
            final char a[] = {static_cast < char>(this.read())};
            pos[0] = atoi(a);
            c = this.read(); // ;
            final char b[] = {static_cast < char>(this.read())};
            pos[1] = atoi(b);
            c = this.read(); // R
        }
    }*/

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


    static String sillyPrint(final String text, final boolean rainbow, final boolean rollercoaster) {
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
}
