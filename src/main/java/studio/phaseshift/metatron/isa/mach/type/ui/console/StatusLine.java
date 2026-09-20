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

import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.Status;
import org.slf4j.event.Level;
import studio.phaseshift.metatron.TypeCheck;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.math.mathInstSet;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.reflect.TypedRec;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.util.*;
import java.util.function.Supplier;

import static org.slf4j.event.Level.ERROR;
import static org.slf4j.event.Level.INFO;
import static org.slf4j.event.Level.WARN;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_BYTE_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Poly.MUTABLE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.m.type.reflect.TypedRec.typedRec;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class StatusLine implements Runnable {

    /**
     * How many banner messages are retained; the oldest scroll off the tail.
     */
    static final int MESSAGE_HISTORY = 8;
    /**
     * Divides one banner message from the next: a bullet rather than the hairline
     * {@code ·}, since a status line is read at a glance.  It wears the color of
     * the messages it divides — the banner's history — so the one thing that
     * stands brighter than the history is the newest message.  Bigger
     * alternatives that still measure one column: {@code ●} U+25CF,
     * {@code ⬤} U+2B24 — both ambiguous-width, so a CJK-wide terminal renders
     * them two columns and the bar outgrows the row.
     */
    private static final String MESSAGE_DOT = "•";

    private List<AttributedString> line = new ArrayList<>();
    private Level state = INFO;
    private long startTime = 0;
    private long lastExecutionTime = 0;
    private final Status status;
    private static final TypedRec<Uri, Call> widgets = typedRec();
    private static final Rec widgetData = rec();
    /**
     * The banner's messages, newest first — a new message shifts the rest right.
     */
    private static final Deque<String> messages = new ArrayDeque<>();
    private static long lastMessageTime = 0L;
    private static String lastMessageColor = "b";

    public StatusLine(final Console console) {
        this.line = new ArrayList<>();
        this.status = Status.getStatus(Console.getTerminal());
        final Real inBytes = mathInstSet.normalizeData(real((double) (Router.global().stats().ioStats().bytesRecv()), MATH_BYTE_TID, null));
        final Real outBytes = mathInstSet.normalizeData(real((double) (Router.global().stats().ioStats().bytesSent()), MATH_BYTE_TID, null));
        this.addWidget(f("type_check"), () -> "{{w&[%s]}} T {{X}}".formatted(TypeCheck.colorLevel()));
        this.addWidget(f("in_bytes"), () -> " {{w}}\uD83D\uDCE5 {{%s}}%s::%.2f ".formatted(getForegroundColor(), inBytes.tid().name(), inBytes.realValue()));
        this.addWidget(f("out_bytes"), () -> "{{w}}\uD83D\uDCE4 {{%s}}%s::%.2f ".formatted(getForegroundColor(), outBytes.tid().name(), outBytes.realValue()));
        this.addWidget(f("time"), () -> "{{%s}}⏳{{%s}}%s ".formatted(this.runningTime() > 10000 ? "r" : "w", getForegroundColor(), timeFormat(this.runningTime())));
        this.addWidget(f("tokens"), () -> "\uD83E\uDD16 %s ".formatted(StatusLine.widgetData.at("tokens").orElse((Obj) jnt(0)).toCleanString()));
        this.addWidget(f("message"), () -> StatusLine.bannerMarkup(getForegroundColor(), getBackgroundColor()));
        
        /*this.addWidget(f("run"), () -> "{{w}}run:{{y}}%d".formatted(Router.global().stats().monadicStats().runningMonads()));
        this.addWidget(f("halt"), () -> "{{w}}halt:{{y}}%d".formatted(Router.global().stats().monadicStats().haltedMonads()));
        this.addWidget(f("kill"), () -> "{{w}}kill:{{y}}%d".formatted(Router.global().stats().monadicStats().killedMonads()));
        this.addWidget(f("barrier"), () -> "{{w}}barrier:{{y}}%d".formatted(Router.global().stats().monadicStats().barrierMonads()));
        this.addWidget(f("ws"), () -> "{{w}}ws:{{w&[g]}}[%d]{{[%s]}} %s".formatted(Router.global().stats().ioStats().connections(), this.getColor(), formatMessage(Router.global().stats().ioStats().lastMessage())));*/
        Router.writeToSpace(console.vid().extend(STATUS).addQ(SUBQ), instLambda((lhs, inst) -> {
            message(lhs.asRec().at(OBJ));
            return noobj();
        }));
        Router.writeToSpace(console.vid().extend(STATUS).extend("widget").extend("#").addQ(SUBQ), instLambda((lhs, inst) -> {
            message(f(lhs.asRec().at(TARGET).uriValue().name()), lhs.asRec().at(OBJ));
            return noobj();
        }));
    }

    /**
     * Post a message to the status banner.  A message never replaces the one
     * before it: it is pushed onto the head of the banner and the messages
     * already there shift right, divided by {@link #MESSAGE_DOT} — a
     * scrolling banner whose newest entry is always the leftmost one and whose
     * oldest entries fall off the terminal's right edge (see {@link #clip}).
     * A repeat of the message already at the head is the same sighting, not a
     * new one, so it refreshes the banner's accent without duplicating itself.
     * An empty message clears the banner.
     */
    public synchronized static void message(final Obj message) {
        final String text = formatMessage(Str.Helper.cleanString(message));
        if (text.isEmpty()) {
            messages.clear();
        } else if (messages.isEmpty() || !messages.peekFirst().equals(text)) {
            messages.addFirst(text);
            while (messages.size() > MESSAGE_HISTORY)
                messages.removeLast();
        }
        StatusLine.lastMessageTime = System.currentTimeMillis();
        StatusLine.lastMessageColor = text.toLowerCase().contains("error") ? "r" : "g";
    }

    public synchronized static void message(final fURI widget, final Obj message) {
        StatusLine.widgetData.at(uri(widget), message, MUTABLE);
    }

    /**
     * The message widget's markup: the envelope (tinted by the banner's accent color),
     * the banner (newest message leftmost), and between one message and the next a bullet.
     * Brightness is what marks age: the newest message wears the line's color turned up,
     * and every message it pushed right wears that color as the line has it — so the eye
     * lands on what just happened, with the banner reading as a history behind it.
     */
    synchronized static String bannerMarkup(final String fore, final String back) {
        final StringBuilder banner = new StringBuilder();
        boolean newest = true;
        for (final String message : messages) {
            if (newest)
                // the palette spells a bold color as its uppercase letter (y -> Y)
                banner.append("{{%s&[%s]}}".formatted(fore.toUpperCase(Locale.ROOT), back));
            else
                // the reset is what drops the head's bold — without it every message behind
                // the newest would wear it too (and it hands back the bar's background)
                banner.append("{{X&[%s]}}{{%s}} %s ".formatted(back, fore, MESSAGE_DOT));
            banner.append(message);
            newest = false;
        }
        return "{{[%s]}}✉️ {{[%s]}} %s".formatted(StatusLine.lastMessageColor, back, banner);
    }

    /**
     * Clip a line to the display columns it has left, so the banner's tail is
     * what falls off the terminal's right edge.  Measured in display columns
     * rather than chars — jline knows a glyph's width where {@code String} does
     * not — and clipped through jline as well, so a wide glyph is never cut in
     * half and the styling of whatever survives is preserved.
     */
    static AttributedString clip(final AttributedString line, final int columns) {
        return line.columnLength() <= columns ? line : line.columnSubSequence(0, Math.max(0, columns));
    }

    private static String formatMessage(final String message) {
        String newMessage = Graphitty.strip(message.trim());
        while (newMessage.startsWith("\""))
            newMessage = newMessage.substring(1);
        while (newMessage.endsWith("\""))
            newMessage = newMessage.substring(0, newMessage.length() - 1);
        newMessage = newMessage.replace("\\n", "\\").trim();
        while (newMessage.endsWith("\\"))
            newMessage = newMessage.substring(0, newMessage.length() - 1);
        return newMessage;
    }


    public void addWidget(final fURI name, final Supplier<String> widget) {
        this.widgets.at(uri(name), instC(name.prepend("status.").dom(ALL.maybe()).rng(STR_TID), lst(), (lhs, inst) -> str(widget.get())), MUTABLE);
    }

    public void addWidget(final Uri name, final Call widgetText) {
        this.widgets.at(uri(name.uriValue()), instC(name.uriValue().prepend("status.").dom(ALL.maybe()).rng(STR_TID), lst(), (lhs, inst) -> widgetText.apply()), MUTABLE);
    }

    private void compileWidgets() {
        final String back = this.getBackgroundColor();
        final String fore = this.getForegroundColor();
        final int width = Console.getTerminal().getWidth();
        if (0 == StatusLine.lastMessageTime || (System.currentTimeMillis() - StatusLine.lastMessageTime) > 5000)
            StatusLine.lastMessageColor = back;
        this.line.clear();
        boolean capped = false;
        int used = 0;   // the display columns already spent on this line
        for (final Map.Entry<Uri, Call> ws : this.widgets.jvmTyped().entrySet()) {
            final String w = ws.getValue().apply(noobj()).strValue().replace("\n", " ");
            final String cap;
            if (capped || ws.getKey().uriValue().toString().endsWith("_")) {
                cap = "";
                capped = true;
            } else {
                cap = "▎";
            }
            if (!ws.getKey().uriValue().toString().endsWith("_"))
                capped = false;
            // The line ends at the terminal's edge, so a banner wide enough to overflow
            // gives way there rather than wrapping — which is what makes the oldest
            // message scroll off the right.  fromAnsi, rather than the bare AttributedString
            // constructor, is what makes the measurement honest: the constructor keeps the
            // escapes as literal chars and would charge the widget for their columns.
            final AttributedString widget = clip(AttributedString.fromAnsi(Graphitty.string("{{%s&[%s]}}%s%s{{[%s]}}", fore, back, cap, w, back)), width - used);
            used += widget.columnLength();
            this.line.add(widget);
        }
        // The bar's background has to reach the terminal's edge, and jline will not do it
        // for us: Status.update pads a short line with spaces built from a FRESH builder,
        // so its fill is unstyled (no background), and an over-long line gets an equally
        // unstyled ellipsis in the last column.  Either way the hack of overshooting the
        // width shows.  Filling the exact remainder with the bar's own colors leaves jline
        // nothing to pad or clip — the line lands on the width it was drawn for.
        if (used < width)
            this.line.add(AttributedString.fromAnsi(Graphitty.string("{{%s&[%s]}}%s{{X}}".formatted(fore, back, " ".repeat(width - used)))));

        final AttributedStringBuilder builder = new AttributedStringBuilder();
        for (final AttributedString s : this.line) {
            builder.append(s);
        }
        this.line.clear();
        this.line.add(builder.toAttributedString());
    }

    private String getBackgroundColor() {
        final String color;
        if (this.state.equals(WARN))
            color = "y";
        else if (this.state.equals(ERROR))
            color = "r";
        else
            color = "b";
        return color;
    }

    private String getForegroundColor() {
        final String color;
        if (this.state.equals(WARN))
            color = "b";
        else if (this.state.equals(ERROR))
            color = "w";
        else
            color = "y";
        return color;
    }

    public void refresh() {
        this.status.update(List.of());
        this.status.update(this.line);
    }

    public void startTimer() {
        this.startTime = System.currentTimeMillis();
    }

    public void stopTimer() {
        this.lastExecutionTime = System.currentTimeMillis() - this.startTime;
        this.startTime = 0;
    }

    private long runningTime() {
        if (0 == this.startTime) {
            return this.lastExecutionTime > 100000000 ? 0 : this.lastExecutionTime;
        } else {
            final long time = System.currentTimeMillis() - this.startTime;
            return time < 0 ? 0 : time;
        }
    }

    public void setState(final Level state) {
        this.state = state;
    }

    public Level getState() {
        return this.state;
    }

    private static String bytesFormat(final long bytes) {
        if (bytes < 1024)
            return bytes + "B";
        else if (bytes < 1024 * 1024)
            return String.format("%.2fkB", bytes / 1024.0);
        else if (bytes < 1024 * 1024 * 1024)
            return String.format("%.2fmB", bytes / (1024.0 * 1024.0));
        else if (bytes < 1024L * 1024L * 1024L * 1024L)
            return String.format("%.2fgB", bytes / (1024.0 * 1024.0 * 1024.0));
        else if (bytes < 1024L * 1024L * 1024L * 1024L * 1024L)
            return String.format("%.2ftB", bytes / (1024.0 * 1024.0 * 1024.0 * 1024.0));
        else
            return String.format("%.2fpB", bytes / (1024.0 * 1024.0 * 1024.0 * 1024.0 * 1024.0));
    }

    private static String timeFormat(final long millis) {
        if (millis < 1000)
            return String.format("%dms", millis);
        else if (millis < 60000)
            return String.format("%.2fs", millis / 1000.0);
        else if (millis < 3600000)
            return String.format("%.2fmin", millis / (60000.0));
        else if (millis < 86400000)
            return String.format("%.2fhr", millis / (3600000.0));
        else
            return String.format("%.2fd", millis / (86400000.0));
    }

    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            if (!Router.loaded()) {
                this.setState(ERROR);
            } else {
                this.compileWidgets();
                this.status.update(this.line);
            }
            try {
                CommonUtil.sleepThread(250);
            } catch (final MTronException e) {
                // do nothing
            }
        }
        this.status.close();
    }
}
