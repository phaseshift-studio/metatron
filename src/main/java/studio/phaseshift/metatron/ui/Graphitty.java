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

import studio.phaseshift.metatron.lang.obj.BObj;
import studio.phaseshift.metatron.lang.obj.Palette;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;

import static org.jline.jansi.Ansi.Color.*;
import static org.jline.jansi.Ansi.ansi;
import static studio.phaseshift.metatron.lang.obj.BObj.MTRON_CORE_TYPES;

public class Graphitty implements ObjSerializer<String> {

    private String buffer = "";
    private final BufferedOutputStream printer;
    private OutputStream out;
    private final ObjSerializer<String> serializer;
    private boolean ansiOn = true;
    private boolean printerOn = true;

    private static final Graphitty DEFAULT = new Graphitty(
            ObjStringSerializer
                    .build()
                    .palette(Palette.STANDARD)
                    .hideTypesMatching(MTRON_CORE_TYPES)
                    .simpleColon(true)
                    .create(), System.out);
    private static Graphitty GRAPHITTY = DEFAULT;

    public static void init(final ObjSerializer<String> serializer, final OutputStream out) {
        GRAPHITTY = new Graphitty(serializer, out);
    }


    public static Graphitty global() {
        return GRAPHITTY;
    }

    public String parse(final String s) {
        this.out = new ByteArrayOutputStream();
        this.parseDSL(s);
        return this.out.toString();
    }

    public Graphitty(final ObjSerializer<String> serializer, final OutputStream out) {
        this.serializer = serializer;
        this.out = out;
        this.printer = new BufferedOutputStream(out);
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
                        this.printer.write(buffer.charAt(i));
                    }
                } else if (buffer.charAt(i) == '!') {
                    boolean decr = false;
                    final char j = buffer.charAt(i + 1);
                    if ('!' == j)
                        this.normal();
                    ////////////////////////////////// POSITION !^d = down
                    else if ('^' == j) {
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
                        if (dir == 'u')
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
                        this.printer.write(buffer.charAt(i));
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
                            this.printer.write(buffer.charAt(i));
                            decr = true;
                        }
                    }
                    if (!decr)
                        i++;
                } else {
                    this.printer.write(buffer.charAt(i));
                }
            }

            this.printer.write(this.buffer.getBytes(StandardCharsets.UTF_8));
            this.flush();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void ansi_switch(boolean turn_on) {
        this.ansiOn = turn_on;
    }

    public void printer_switch(boolean turn_on) {
        this.printerOn = turn_on;
    }

    public boolean is_ansi_on() {
        return this.ansiOn;
    }

    public boolean is_printer_on() {
        return this.printerOn;
    }

    public void print(final char c) {
        if (this.printerOn)
            this.parseDSL(Objects.toString(c));
    }

    public void print(final String c) {
        if (this.printerOn)
            this.parseDSL(c);
    }

    public void println(final String c) {
        if (this.printerOn) {
            if (c.length() > 0)
                this.print(c);
            this.print('\n');
        }
    }

    public void flush() {
        try {
            this.printer.flush();
            this.buffer = new String();
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
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

    public void normal() {
        if (this.ansiOn)
            this.print(ansi().reset().toString());
    }

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


    /// /////// COLORING
    public void black() {
        this.print(ansi().fg(BLACK).toString());
    }

    public void red() {
        this.print(ansi().fg(RED).toString());
    }

    public void green() {
        this.print(ansi().fg(GREEN).toString());
    }

    public void yellow() {
        this.print(ansi().fg(YELLOW).toString());
    }

    public void blue() {
        this.print(ansi().fg(BLUE).toString());
    }


    public void magenta() {
        this.print(ansi().fg(MAGENTA).toString());
    }

    public void cyan() {
        this.print(ansi().fg(CYAN).toString());
    }

    public void white() {
        this.print(ansi().fg(WHITE).toString());
    }

    /// //////////// CURSOR MOVEMENT ///////////////

    public void left(final int columns) {
        this.move('D', columns);
    }

    public void right(final int columns) {
        this.move('C', columns);
    }

    public void down(final int rows) {
        this.move('B', rows);
    }

    public void up(final int rows) {
        this.move('A', rows);
    }

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

    public void to_column(final int column) {
        this.move('G', column);
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
                ret.append("!").append(colors.charAt(random.nextInt() % colors.length()));
            ret.append((rollercoaster ? (random.nextBoolean() ?
                    ("" + text.charAt(i)).toLowerCase(Locale.ROOT) :
                    ("" + text.charAt(i)).toUpperCase(Locale.ROOT)) : text.charAt(i)));
        }
        if (rainbow)
            ret.append("!!");
        return ret.toString();
    }

    @Override
    public String write(final BObj.Obj obj) throws IllegalStateException {
        return this.serializer.write(obj);
    }

    @Override
    public BObj.Obj read(final String data) throws IllegalStateException {
        return this.serializer.read(data);
    }
}
