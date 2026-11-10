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

package studio.phaseshift.metatron.isa.m.sys.ui.console;

import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.Status;
import org.slf4j.event.Level;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.MachinePerspective;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.Typer;
import studio.phaseshift.metatron.isa.m.sys.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.m.sys.ui.widget.MiniWidget;
import studio.phaseshift.metatron.isa.m.sys.ui.widget.MiniWidget.SpanUI;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.reflect.TypedRec;
import studio.phaseshift.metatron.isa.m.web.space.ws.WireStats;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.slf4j.event.Level.ERROR;
import static org.slf4j.event.Level.INFO;
import static org.slf4j.event.Level.WARN;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Poly.MUTABLE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.m.type.reflect.TypedRec.typedRec;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class StatusLine implements Runnable, AutoCloseable {

    /**
     * How many messages are retained — the history popup's window. The marquee draws only the newest
     * {@link #BANNER_MESSAGES} of them: they are all it can ever show, and building the rest into every repaint is
     * work the bar cannot display. The oldest scroll off the tail.
     */
    static final int MESSAGE_HISTORY = 50;
    /**
     * How many of the retained messages the banner's marquee draws. The terminal edge clips whatever does not fit
     * (a handful of messages), so drawing the whole retained window only spends width the bar cannot show.
     */
    static final int BANNER_MESSAGES = 7;
    /**
     * The banner's envelope glyph — also the history popup's click target, so the drawn glyph and the hit-test
     * width are measured from one constant rather than retyped.
     */
    public static final String ENVELOPE = "✉️";
    public static final String MACHINE = "\uD83D\uDDA5️";
    /**
     * Divides one banner message from the next: a bullet rather than the hairline
     * {@code ·}, since a status line is read at a glance.  It wears the color of
     * the messages it divides — the banner's history — so the one thing that
     * stands brighter than the history is the newest message.  Bigger
     * alternatives that still measure one column: {@code ●} U+25CF,
     * {@code ⬤} U+2B24 — both ambiguous-width, so a CJK-wide terminal renders
     * them two columns and the bar outgrows the row.
     */
    public static final String MESSAGE_DOT = "•";

    /**
     * The type-check widget's name — both its segment key and, through {@code CommandPalette.cycleTyper}, what a
     * click on that segment does.
     */
    private static final String TYPE_CHECK = "type_check";

    private List<AttributedString> line = new ArrayList<>();
    /**
     * The segments on the last drawn bar, in draw order. A click maps a terminal column against these; the list
     * is swapped whole on each repaint, so the pointer thread never walks a half-built one.
     */
    private volatile List<SpanUI> segments = List.of();

    /**
     * What a click on one segment does. {@code columns} bounds the hot spot to the first N columns of the widget's
     * label (0 = the whole label), so a glyph can be the affordance while the text behind it stays inert; the
     * action receives the span it was clicked on, for a popup that must align with the clicked glyph.
     */
    private record SegmentAction(Consumer<SpanUI> action, int columns) {
    }

    /**
     * The segment keys that answer a click; a widget with no action is drawn, and inert to the pointer.
     */
    private final Map<String, SegmentAction> segmentActions = new LinkedHashMap<>();
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
        this.addWidget(f(TYPE_CHECK), () -> "{{w&[%s]}} T {{X}}".formatted(Typer.Helper.colorLevel()));
        // the strictness level is the bar's first segment, and a click on it is the pointer's alt+t
        this.onSegment(TYPE_CHECK, CommandPalette::cycleTyper);
        // the wire, not the machine's io counters: bytes-in/out mixed LLM tokens, MQTT payloads and database reads
        // (see Stats.IOStats), and the widget froze them at construction besides. see WireStats
        this.addWidget(f("net"), () -> netMarkup(getForegroundColor(), getBackgroundColor()));
        // the wire is the panel's subject, so the gauge is the pointer's way in
        this.onSegment("net", CommandPalette::showNetworkPanel);
        // THE TIME THE CURRENT EVALUATION IS TAKING — the stopwatch the console has always shown, not a wall
        // clock: it sits at the last job's duration between jobs and climbs while one runs, and that is the fact
        // this widget exists for (a clock is on the terminal's own status line, or the user's shell).
        this.addWidget(f("time"), () -> "{{%s}}⏳ {{%s}}%s ".formatted(this.runningTime() > 10000 ? "r" : "w", getForegroundColor(), timeFormat(this.runningTime())));
        this.addWidget(f("tokens"), () -> "\uD83E\uDD16 %s ".formatted(StatusLine.widgetData.at("tokens").orElse((Obj) jnt(0)).toCleanString()));
        // THE PERSPECTIVE, NOT THIS THREAD'S OWN. Machine.current() on the status line's thread is the root
        // forever (the thread never moves), which is why this widget used to say "./" while an evaluation stood in
        // a peer's frame; the published perspective is where the VM actually is (see Machine.perspective()).
        this.addWidget(f("machine"), () -> "{{w}}\uD83D\uDDA5️ {{%s}}%s ".formatted(getForegroundColor(),
                machineText(MachinePerspective.vid())));
        // THE FRAME IS THE TREE'S SUBJECT: the bar names the machine you stand in, so it is also the way to walk
        // to another one (see CommandPalette.showMachineTree). The whole label answers (columns 0), unlike the
        // envelope's narrow target, because the address behind the glyph is as good a place to aim as the glyph.
        this.onSegment("machine", 0, CommandPalette::showMachineTree);
        this.addWidget(f("message"), () -> StatusLine.bannerMarkup(getForegroundColor(), getBackgroundColor()));
        // THE ENVELOPE ALONE IS THE HISTORY'S DOOR: the glyph is the hot spot, the banner text behind it stays
        // inert, so a click on a message never opens the list by accident.
        this.onSegment("message", Highlighter.visualLength(ENVELOPE), CommandPalette::showMessageHistory);
        
        /*this.addWidget(f("run"), () -> "{{w}}run:{{y}}%d".formatted(Machine.authority().stats().monadicStats().runningMonads()));
        this.addWidget(f("halt"), () -> "{{w}}halt:{{y}}%d".formatted(Machine.authority().stats().monadicStats().haltedMonads()));
        this.addWidget(f("kill"), () -> "{{w}}kill:{{y}}%d".formatted(Machine.authority().stats().monadicStats().killedMonads()));
        this.addWidget(f("barrier"), () -> "{{w}}barrier:{{y}}%d".formatted(Machine.authority().stats().monadicStats().barrierMonads()));
        this.addWidget(f("ws"), () -> "{{w}}ws:{{w&[g]}}[%d]{{[%s]}} %s".formatted(Machine.authority().stats().ioStats().connections(), this.getColor(), formatMessage(Machine.authority().stats().ioStats().lastMessage())));*/
        Machine.write(console.vid().extend(STATUS).addQ(SUBQ), instLambda((lhs, inst) -> {
            message(lhs.asRec().at(OBJ));
            return noobj();
        }));
        Machine.write(console.vid().extend(STATUS).extend("widget").extend("#").addQ(SUBQ), instLambda((lhs, inst) -> {
            message(f(lhs.asRec().at(TARGET).uriValue().name()), lhs.asRec().at(OBJ));
            return noobj();
        }));
    }

    @Override
    public void close() {
        this.status.hide();
        this.status.close();
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
        int shown = 0;
        for (final String message : messages) {
            // THE MARQUEE DRAWS ITS NEWEST WINDOW, not the whole retained history: the terminal clips a handful of
            // messages at most, and the rest are the history popup's (see history()).
            if (shown++ >= BANNER_MESSAGES)
                break;
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
        return "{{[%s]}}%s {{[%s]}} %s".formatted(StatusLine.lastMessageColor, ENVELOPE, back, banner);
    }

    /**
     * The retained messages, newest first — the history popup's source (the marquee draws its newest
     * {@link #BANNER_MESSAGES}). A copy, so the caller can render it while the status thread keeps posting.
     *
     * @return up to {@link #MESSAGE_HISTORY} messages, newest first
     */
    public static synchronized List<String> history() {
        return List.copyOf(messages);
    }

    /**
     * The wire's gauge: completed round trips, frames received, frames sent.
     * <p>
     * It replaces the bytes-in/out widgets, which read {@code Stats.IOStats} — a counter fed by LLM token bytes,
     * MQTT payloads and database reads as well as sockets, so it never measured the network — and which were
     * computed once at construction and never changed again.
     * <p>
     * A wire that is answering is the normal state, so it reads quietly; a timeout tints the trip count red for
     * as long as the window holds one. A pure function of {@link WireStats}, so it is tested like the banner.
     */
    static String netMarkup(final String fore, final String back) {
        // a timeout tints the trip count and nothing else: the bar is a glance, the timeouts themselves are the
        // panel's to itemize (see NetworkPanelTool)
        return " {{W}}⇄{{%s&[%s]}}%d {{W}}↓{{%s&[%s]}}%d {{W}}↑{{%s&[%s]}}%d".formatted(
                WireStats.timeouts() > 0 ? "r" : fore, back, WireStats.roundTrips(),
                fore, back, WireStats.received(),
                fore, back, WireStats.sent());
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

    /**
     * Make a bar widget's segment answer a pointer click: clicking its columns runs {@code action}. The bar is
     * one line (see {@link #compileWidgets()}), so a segment is the widget's LABEL columns on it — the separator
     * before it is not part of the segment — and a widget with no action stays drawn but inert.
     *
     * @param widget the name the widget was added under
     * @param action what a click on its segment does
     * @return this status line (fluent)
     */
    public StatusLine onSegment(final String widget, final Runnable action) {
        return this.onSegment(widget, 0, span -> action.run());
    }

    /**
     * As {@link #onSegment(String, Runnable)}, but the segment answers only its first {@code columns} columns
     * ({@code 0} = the whole label): the message segment's envelope is the history popup's target while the
     * banner text behind it stays inert. The action receives the clicked span, so a popup can line itself up with
     * it.
     *
     * @param widget  the name the widget was added under
     * @param columns the first columns of the label that answer the click (0 = all of them)
     * @param action  what a click on those columns does
     * @return this status line (fluent)
     */
    public StatusLine onSegment(final String widget, final int columns, final Consumer<SpanUI> action) {
        this.segmentActions.put(widget, new SegmentAction(action, columns));
        return this;
    }

    /**
     * The bar's hot spots, in draw order — the geometry {@link #clickAt} maps a terminal column against.
     *
     * @return the segments of the last compiled bar
     */
    List<SpanUI> segments() {
        return this.segments;
    }

    /**
     * Whether any segment on the bar answers a pointer click. The console arms terminal mouse tracking from
     * this: the bar is always on screen, and a click on {@code [ T ]} or the wire gauge must work from the first
     * prompt — not only once some widget has been floated (widgets used to be the only thing that justified
     * owning the mouse).
     *
     * @return true when at least one segment has been given an action
     */
    public boolean clickable() {
        return !this.segmentActions.isEmpty();
    }

    /**
     * A pointer click at a terminal cell: a segment answers when the cell is on the status row and that segment
     * has an action. The bar lives on the terminal's LAST row, and a click's column is 1-based while a segment's
     * columns are line-relative — hence the row test and the {@code col - 1}.
     *
     * @param row 1-based terminal row of the click
     * @param col 1-based terminal column of the click
     * @return true when a segment consumed the click
     */
    public boolean clickAt(final int row, final int col) {
        final Terminal terminal = Console.getTerminal();
        if (null == terminal || row != terminal.getHeight())
            return false;
        final java.util.Optional<SpanUI> segment = MiniWidget.at(this.segments, col - 1);
        if (segment.isEmpty())
            return false;
        final SegmentAction action = this.segmentActions.get(segment.get().key());
        if (null == action)
            return false;
        // a prefix-bounded segment answers only its first columns (the envelope, not the banner behind it)
        if (action.columns() > 0 && col - 1 >= segment.get().start() + action.columns())
            return false;
        try {
            action.action().accept(segment.get());
        } catch (final Exception e) {
            message(str("status segment failed: " + e.getMessage()));
        }
        return true;
    }

    synchronized void compileWidgets() {
        final String back = this.getBackgroundColor();
        final String fore = this.getForegroundColor();
        final int width = Console.getTerminal().getWidth();
        if (0 == StatusLine.lastMessageTime || (System.currentTimeMillis() - StatusLine.lastMessageTime) > 5000)
            StatusLine.lastMessageColor = back;
        this.line.clear();
        final List<SpanUI> spans = new ArrayList<>();
        boolean capped = false;
        int used = 0;   // the display columns already spent on this line
        for (final Map.Entry<Uri, Call> ws : this.widgets.jvmTyped().entrySet()) {
            final String w = this.renderWidget(ws);
            // a widget with nothing to say (the job stopwatch while idle) takes no separator and no columns
            if (null == w || w.isEmpty())
                continue;
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
            final int start = used;
            // the separator belongs to the bar's chrome, not to the segment: a segment starts at its own label
            final int labelStart = start + Highlighter.visualLength(cap);
            final AttributedString widget = clip(AttributedString.fromAnsi(Graphitty.string("{{%s&[%s]}}%s%s{{[%s]}}", fore, back, cap, w, back)), width - used);
            used += widget.columnLength();
            this.line.add(widget);
            // THE BAR'S SEGMENTS: a widget's label columns on the line, keyed by its name so a pointer hit can ask
            // the host what it means (see clickAt). A widget clipped to nothing has no columns to hit.
            if (used > labelStart)
                spans.add(new SpanUI(labelStart, used, ws.getKey().uriValue().name(), noobj()));
        }
        this.segments = spans;
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

    /**
     * One widget's text, with a broken widget contained: an exception renders as a red {@code !name} instead of
     * ending the render thread — which is what it used to do, freezing the bar for the rest of the session with
     * nothing on screen to say why.
     */
    private String renderWidget(final Map.Entry<Uri, Call> widget) {
        try {
            return widget.getValue().apply(noobj()).strValue().replace("\n", " ");
        } catch (final Exception e) {
            return "{{r}}!" + widget.getKey().uriValue().name();
        }
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

    /**
     * How many columns the machine widget may spend — a bar has better uses for its width than a full nested path,
     * and {@code /network/peer/ws/host/port/ns} is longer than anything it could usefully displace.
     */
    private static final int MACHINE_BUDGET = 28;

    /**
     * The machine vid as the bar shows it: the published frame's LOCAL address (a frame standing in a peer is at a
     * local path, never at the wire address), shortened from the LEFT when long, because the tail is what identifies
     * the frame.
     */
    static String machineText(final fURI vid) {
        final String text = null == vid ? "." : vid.toString();
        if (text.length() <= MACHINE_BUDGET)
            return text;
        final String[] segments = text.split("/");
        final StringBuilder tail = new StringBuilder();
        for (int i = segments.length - 1; i >= 0; i--) {
            final String candidate = 0 == tail.length() ? segments[i] : segments[i] + "/" + tail;
            if (candidate.length() + 2 > MACHINE_BUDGET)
                break;
            tail.setLength(0);
            tail.append(candidate);
        }
        return 0 == tail.length() ? "\u2026" : "\u2026/" + tail;
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
            if (!Machine.loaded()) {
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
