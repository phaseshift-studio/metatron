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

package studio.phaseshift.metatron.isa.mach.type.ui.widget;

import org.jline.terminal.Terminal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.reflect.JRec;
import studio.phaseshift.metatron.isa.m.type.reflect.JRecElement;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.Border;
import studio.phaseshift.metatron.isa.mach.type.ui.Stylable;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Console;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Highlighter;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.SUBSCRIPTION_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.NOOBJ_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.type.ui.console.Console.CONSOLE_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * A Pane is a leaf node in the pane tree - an actual terminal region with:
 * - Output buffer (thread-safe, for parallel output from background threads)
 * - Language mode (mtron, gremlin, sql)
 * - Machine reference (for interruption)
 * - Style support (border, foreground color, etc.)
 *
 * <pre>
 * ┌─────────────────────────────────┐
 * │ [0] Pane Output                 │ ← outputBuffer rendered here
 * │ ==> result1                     │
 * │ ==> result2                     │
 * │                                 │
 * │ mtron> ___                      │ ← prompt (when active)
 * └─────────────────────────────────┘
 * </pre>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class Pane extends JRec<Pane> implements PaneNode, Stylable<Pane> {

    private static final AtomicInteger ID_COUNTER = new AtomicInteger(0);
    private static final int DEFAULT_MAX_OUTPUT_LINES = 1000;
    private static final Logger log = LoggerFactory.getLogger(Pane.class);

    private final int id;
    private Console.Language language;
    private Machine machine;
    private final List<String> outputBuffer;
    private final int maxOutputLines;
    private volatile boolean needsRedraw = false;

    // Style support
    private Style<Pane> style = Style.empty();

    // Reference to console for redraw requests
    private Console console;

    /**
     * Optional listener fired on every {@link #appendOutput} call.
     * Registered by Console so it can redraw the pane without Pane needing to
     * know anything about rendering, throttling, or threading.
     */
    private Consumer<Pane> outputListener;

    public Pane() {
        this(Console.Language.MTRON, DEFAULT_MAX_OUTPUT_LINES);
    }

    public Pane(final Console.Language language, final int maxOutputLines) {
        super(mutableMap(), CONSOLE_TID.extend("pane"), Console.LOCAL_INSTANCE.vid().extend("pane").extend("" + ID_COUNTER.getAndIncrement()));
        this.id = ID_COUNTER.get() - 1;
        this.language = language;
        this.maxOutputLines = maxOutputLines;
        this.outputBuffer = Collections.synchronizedList(new ArrayList<>());
        this.machine = null;
        // Default to simple border style (ASCII: +, |, -) for visibility
        this.style = this.style().border(Border.simple).apply().getStyle();
        //  this.subscribe();
    }

    @Override
    public Pane style(final Style<Pane> style) {
        this.style = style;
        return this;
    }

    @Override
    public Style<Pane> getStyle() {
        return this.style;
    }

    public Pane vid(final fURI vid) {
        this.vid = vid;
        return this;
    }

    public fURI vid() {
        return this.vid;
    }

    public void setConsole(final Console console) {
        this.console = console;
    }

    /**
     * Register a listener that is called after every {@link #appendOutput} call.
     */
    public void setOutputListener(final Consumer<Pane> listener) {
        this.outputListener = listener;
    }

    public void unsubscribe() {
        if (this.vid() == null)
            return;
        Router.global().write(this.vid().extend(IN).addQ(SUBQ), noobj());
        Router.global().write(this.vid().extend(OUT).addQ(SUBQ), noobj());
    }

    public void subscribe() {
        if (this.vid() == null) {
            LOG.warn("console has no vid. unable to support pane subscriptions.");
            return;
        }
        Router.global().write(this.vid().extend(IN).addQ(SUBQ), rec(mutableMap(
                uri(TARGET), uri(this.vid().extend(IN)),
                uri(ON_RECV), instC(f("in_pane").dom(ALL).rng(NOOBJ_TID.zero()), lst(), (lhs, inst) -> {
                    this.appendInput(lhs.asLst().at(1));
                    this.console.renderPanes();
                    return noobj();
                })), SUBSCRIPTION_TID, null));
        Router.global().write(this.vid().extend(OUT).addQ(SUBQ), rec(mutableMap(
                uri(TARGET), uri(this.vid().extend(OUT)),
                uri(ON_RECV), instC(f("out_pane").dom(ALL).rng(NOOBJ_TID.zero()), lst(), (lhs, inst) -> {
                    this.appendResult(lhs.asLst().at(1));
                    this.console.renderPanes();
                    return noobj();
                })), SUBSCRIPTION_TID, null));
    }

    public int id() {
        return this.id;
    }

    public Console.Language language() {
        return this.language;
    }

    public Pane language(final Console.Language language) {
        this.language = language;
        return this;
    }

    public Machine machine() {
        return this.machine;
    }

    public Pane machine(final Machine machine) {
        this.machine = machine;
        return this;
    }

    public void clearMachine() {
        this.machine = null;
    }

    /**
     * Append output to this pane's buffer. Thread-safe.
     * Lines exceeding maxOutputLines are trimmed from the top.
     * Triggers a redraw request.
     */
    public void appendOutput(final String text, final boolean newline) {
        if (text == null) return;
        // Split on newlines and add each
        if (newline)
            this.outputBuffer.addAll(Arrays.asList(text.split("\n", -1)));
        else if (this.outputBuffer.isEmpty())
            this.outputBuffer.add(text);
        else
            this.outputBuffer.add(this.outputBuffer.removeLast().concat(text));
        // Trim old lines
        while (this.outputBuffer.size() > this.maxOutputLines) {
            this.outputBuffer.removeFirst();
        }
        this.needsRedraw = true;
        if (this.outputListener != null) {
            this.outputListener.accept(this);
        } else if (this.console != null) {
            this.console.requestRedraw();
        }
    }

    public void appendOutput(final String text) {
        this.appendOutput(text, true);
    }

    /**
     * Append a result object to output (formatted with ==> and syntax highlighted). Thread-safe.
     */
    @JRecElement(key = "prompt", rng = "noobj{0}", mimic = JRecElement.Mimic.METHOD)
    public void appendInput(final Obj input) {
        this.appendOutput(this.prompt() + Highlighter.format(input.isStr() ? input.strValue() : input.toString()));
    }

    /**
     * Append a result object to output (formatted with ==> and syntax highlighted). Thread-safe.
     */
    @JRecElement(key = "stream", rng = "noobj{0}", mimic = JRecElement.Mimic.METHOD)
    public void streamInput(final Obj input) {
        this.appendOutput(Highlighter.format(input.isStr() ? input.strValue() : input.toString()), false);
    }

    /**
     * Append a result object to output (formatted with ==> and syntax highlighted). Thread-safe.
     */
    @JRecElement(key = "result", rng = "noobj{0}", mimic = JRecElement.Mimic.METHOD)
    public void appendResult(final Obj result) {
        result.stream().forEach(o -> {
            // Use Highlighter for syntax highlighting
            this.appendOutput(Graphitty.string("{{m}}=={{g}}>{{X}}") + Highlighter.format(o));
        });
    }

    /**
     * Write formatted output (for prompts, messages). Thread-safe.
     */
    public void write(final String text) {
        this.appendOutput(text);
    }

    public List<String> outputBuffer() {
        return new ArrayList<>(this.outputBuffer); // Return copy for thread safety
    }

    /**
     * Get the last N lines of output that fit in the given height.
     */
    public List<String> visibleOutput(final int height) {
        final List<String> buffer = this.outputBuffer();
        final int visibleLines = Math.min(buffer.size(), Math.max(0, height - 2)); // -2 for border/prompt
        if (visibleLines <= 0) return List.of();
        return buffer.subList(buffer.size() - visibleLines, buffer.size());
    }

    /**
     * Generate the prompt string for this pane (pane ID shown in top border, not needed here).
     */
    public String prompt() {
        return Graphitty.string(this.language.prompt);
    }

    // ========== PaneNode interface ==========

    @Override
    public void render(final Terminal terminal, final int startRow, final int startCol,
                       final int height, final int width, final Pane activePane) {
        final boolean isActive = this == activePane;
        final List<String> visible = this.visibleOutput(height);

        // Get border style - use foreground color based on active state
        final Border border = this.style.border;
        final String borderColor = isActive ? "{{g}}" : "{{b}}";

        // Build the pane content
        final StringBuilder sb = new StringBuilder();

        // Top border with pane ID
        final String header = isActive
                ? Graphitty.string("{{[g]}}[%s]{{X}}".formatted(this.vid))
                : Graphitty.string("{{[b]}}[%s]{{X}}".formatted(this.vid));
        sb.append("\u001b[").append(startRow).append(";").append(startCol).append("H"); // Move cursor
        sb.append(Graphitty.string(borderColor));
        sb.append(border.topLeftCorner());
        sb.append("{{X}}");
        sb.append(header);
        sb.append(Graphitty.string(borderColor));
        sb.append(border.topSide().repeat(Math.max(0, width - 4 - this.vid().toString().length()))); // -5 for corners + [id]
        sb.append(border.topRightCorner());
        sb.append("{{X}}");

        // Content lines with left and right borders
        int row = startRow + 1;
        final int contentWidth = width - 2; // -2 for left border + right border

        for (final String line : visible) {
            if (row >= startRow + height - 1) break; // Leave room for bottom
            sb.append("\u001b[").append(row).append(";").append(startCol).append("H");
            sb.append(Graphitty.string(borderColor));
            sb.append(border.leftSide());
            sb.append("{{X}}");

            // Truncate line to fit content width
            final String stripped = Graphitty.strip(line);
            final String truncated = stripped.length() > contentWidth
                    ? stripped.substring(0, contentWidth - 3) + "..."
                    : line;
            sb.append(truncated);

            // Pad to fill content width
            final int padding = contentWidth - Graphitty.strip(truncated).length();
            if (padding > 0) sb.append(" ".repeat(padding));

            sb.append(Graphitty.string(borderColor));
            sb.append(border.rightSide());
            sb.append("{{X}}");
            row++;
        }

        // Clear remaining rows with borders
        while (row < startRow + height - 1) {
            sb.append("\u001b[").append(row).append(";").append(startCol).append("H");
            sb.append(Graphitty.string(borderColor));
            sb.append(border.leftSide());
            sb.append("{{X}}");
            sb.append(" ".repeat(contentWidth));
            sb.append(Graphitty.string(borderColor));
            sb.append(border.rightSide());
            sb.append("{{X}}");
            row++;
        }

        // Bottom border
        sb.append("\u001b[").append(startRow + height - 1).append(";").append(startCol).append("H");
        sb.append(Graphitty.string(borderColor));
        sb.append(border.bottomLeftCorner());
        sb.append(border.bottomSide().repeat(Math.max(0, width - 2))); // -2 for corners
        sb.append(border.bottomRightCorner());
        sb.append("{{X}}");

        terminal.writer().print(Graphitty.string(sb.toString()));
        this.needsRedraw = false;
    }

    /**
     * Render only the content rows of this pane (rows between the borders), in-place.
     * <p>
     * Unlike {@link #render} this does NOT redraw the top/bottom borders and does NOT
     * clear the rest of the screen, so calling it during live output causes zero flicker.
     * The caller is responsible for repositioning the cursor afterwards.
     */
    public void renderContentOnly(final Terminal terminal, final int startRow, final int startCol,
                                  final int height, final int width) {
        final Border border = this.style.border;
        final String borderColor = "{{b}}"; // non-active colour; active border was drawn by full render
        final List<String> visible = this.visibleOutput(height);
        final int contentWidth = width - 2; // -2 for left/right border chars

        final StringBuilder sb = new StringBuilder();
        int row = startRow + 1;

        for (final String line : visible) {
            if (row >= startRow + height - 1) break;
            sb.append("\u001b[").append(row).append(";").append(startCol).append("H");
            sb.append(Graphitty.string(borderColor));
            sb.append(border.leftSide());
            sb.append("{{X}}");

            final String stripped = Graphitty.strip(line);
            final String truncated = stripped.length() > contentWidth
                    ? stripped.substring(0, contentWidth - 3) + "..."
                    : line;
            sb.append(truncated);

            final int padding = contentWidth - Graphitty.strip(truncated).length();
            if (padding > 0) sb.append(" ".repeat(padding));

            sb.append(Graphitty.string(borderColor));
            sb.append(border.rightSide());
            sb.append("{{X}}");
            row++;
        }

        // Clear any leftover rows (e.g. after output was trimmed)
        while (row < startRow + height - 1) {
            sb.append("\u001b[").append(row).append(";").append(startCol).append("H");
            sb.append(Graphitty.string(borderColor));
            sb.append(border.leftSide());
            sb.append("{{X}}");
            sb.append(" ".repeat(contentWidth));
            sb.append(Graphitty.string(borderColor));
            sb.append(border.rightSide());
            sb.append("{{X}}");
            row++;
        }

        terminal.writer().print(Graphitty.string(sb.toString()));
        terminal.writer().flush();
        this.needsRedraw = false;
    }

    @Override
    public boolean isLeaf() {
        return true;
    }

    @Override
    public List<Pane> getAllPanes() {
        return List.of(this);
    }

    @Override
    public Pane findPane(final int id) {
        return this.id == id ? this : null;
    }

    @Override
    public boolean replaceChild(final Pane oldPane, final PaneNode newNode) {
        // Leaf nodes have no children to replace
        return false;
    }

    @Override
    public PaneNode removePane(final Pane pane) {
        // If removing self, return null (parent will handle)
        return this == pane ? null : this;
    }

    @Override
    public String toString() {
        return "Pane[%d, %s, lines=%d]".formatted(this.id, this.language.name, this.outputBuffer.size());
    }
}
