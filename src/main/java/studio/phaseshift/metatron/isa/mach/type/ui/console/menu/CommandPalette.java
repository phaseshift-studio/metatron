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

package studio.phaseshift.metatron.isa.mach.type.ui.console.menu;

import org.jline.builtins.Commands;
import org.jline.builtins.TTop;
import org.jline.reader.Buffer;
import org.jline.reader.LineReader;
import org.jline.reader.Widget;
import org.jline.terminal.MouseEvent;
import org.jline.utils.InfoCmp;
import org.jline.widget.Widgets;
import org.slf4j.event.Level;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.Tracer;
import studio.phaseshift.metatron.TypeCheck;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.LogObj;
import studio.phaseshift.metatron.isa.mach.type.ui.Border;
import studio.phaseshift.metatron.isa.mach.type.ui.console.*;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.isa.mach.type.ui.tmux.Pane;
import studio.phaseshift.metatron.isa.mach.type.ui.tmux.SplitLayout;
import studio.phaseshift.metatron.isa.mach.type.ui.tool.ExplainTool;
import studio.phaseshift.metatron.isa.mach.type.ui.tool.InstSelectorTool;
import studio.phaseshift.metatron.isa.mach.type.ui.tool.fURISelectorTool;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.*;

import java.io.PrintStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.jline.keymap.KeyMap.*;
import static studio.phaseshift.metatron.Tokens.M_ISA_INST_TID;
import static studio.phaseshift.metatron.Tokens.NOOBJ_TID;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_from_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.ui.uiInstSet.UI_CONSOLE_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class CommandPalette extends MRec {

    public static final fURI COMMAND_PALETTE_TID = UI_CONSOLE_TID.extend("command_palette");
    private final Console console;

    /**
     * Simple 1:1 key bindings that just dispatch to the named command.
     */
    private final List<Map.Entry<String, String>> simpleKeys = new ArrayList<>();

    public Rec attach(final Rec menuRec, final String... menuItemsToAdd) {
        for (final String item : menuItemsToAdd.length == 0 ? this.getMenuItems() : menuItemsToAdd) {
            menuRec.at(uri(item), this.at(uri(item)).clone(), MUTABLE);
        }
        return this.console.at(uri("menu"), menuRec, MUTABLE);
    }

    public String[] getMenuItems() {
        return this.keys().map(x -> x.uriValue().toString()).toArray(String[]::new);
    }

    public CommandPalette(final Console console) {
        super(mutableMap(uri("console"), auto_from_(console.vid()).tryToInst()), COMMAND_PALETTE_TID, console.vid().extend("command_palette"));
        this.console = console;

        // ===== help =====
        this.at("help", instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(NOOBJ_TID), lst(), (lhs, inst) -> {
            final String helpText = new PanelWidget("{{c}}metatron console help{{X}}", new TableWidget(
                    List.of("action", "description"))
                    /// ///////////////////////////////////////////////////////////////////////////////////////
                    .addRow(List.of("{{[g]&w}}mtron", "{{[g]&w}}"))
                    .addRow(List.of(kc("<tab>"), "tabular view of the current code"))
                    .addRow(List.of(cc(":typer [stages]"), "show or enable/disable type checking stages (+stage/-stage)"))
                    .addRow(List.of(kc("<alt>+t") + "  " + cc(":typer-cycle"), "cycle type check activations"))
                    .addRow(List.of(cc(":tracer [on|off]"), "toggle Java stack trace dump on fail"))
                    /// ///////////////////////////////////////////////////////////////////////////////////////
                    .addRow(List.of("{{[g]&w}}console", "{{[g]&w}}"))
                    .addRow(List.of(kc("<ctrl>+q") + "  " + cc(":quit"), "exit the console"))
                    .addRow(List.of(cc(":reset"), "reboot the metatron vm"))
                    .addRow(List.of(cc(":clear"), "clear the console"))
                    .addRow(List.of(cc(":header [name]"), "print random or named metatron header"))
                    .addRow(List.of(cc(":logger [level] [pane]"), "show or set log level and target pane"))
                    .addRow(List.of(cc(":redirect/input [inst]"), "redirect console input via inst (noobj for default)"))
                    .addRow(List.of(cc(":prefix [text]"), "prefix input with text"))
                    .addRow(List.of(cc(":postfix [text]"), "postfix input with text"))
                    .addRow(List.of(kc("<alt>+f") + "  " + cc(":format"), "pretty-print current buffer"))
                    .addRow(List.of(kc("<alt>+e") + "  " + cc(":editor"), "full screen nano editor with current buffer"))
                    .addRow(List.of(kc("<alt>+b") + "  " + cc(":bg"), "background the job holding the console — keys return"))
                    .addRow(List.of(cc(":bg [stop]"), "list (or stop) jobs backgrounded with " + kc("<alt>+b")))
                    //.addRow(List.of(kc("<ctrl>+d") + "  " + cc(":stop-agents"), "stop all agent threads"))
                    .addRow(List.of(kc("<alt>+l") + "  " + cc(":line"), "add a new chat overlay line (\\_)"))

                    .addRow(List.of(kc("<shift>+<left/right>"), "jump word left/right"))
                    .addRow(List.of(kc("<alt>+<backspace>"), "delete previous word"))
                    .addRow(List.of(kc("<alt>+k [char]"), "erase buffer back to first occurrence of char"))
                    /// ///////////////////////////////////////////////////////////////////////////////////////
                    .addRow(List.of("{{[g]&w}}panes", "{{[g]&w}}"))
                    .addRow(List.of(kc("<alt>+<right>") + "  " + cc(":split v"), "split pane side-by-side"))
                    .addRow(List.of(kc("<alt>+<up>") + "  " + cc(":split h"), "split pane stacked"))
                    .addRow(List.of(kc("<alt>+n") + "  " + cc(":next-pane"), "cycle to next pane"))
                    .addRow(List.of(kc("<alt>+p") + "  " + cc(":prev-pane"), "cycle to previous pane"))
                    .addRow(List.of(kc("<alt>+<") + "  " + cc(":shrink"), "shrink active pane"))
                    .addRow(List.of(kc("<alt>+>") + "  " + cc(":grow"), "grow active pane"))
                    .addRow(List.of(cc(":focus [id]"), "focus pane by id (no arg shows current)"))
                    .addRow(List.of(cc(":panes"), "list all panes"))
                    .addRow(List.of(cc(":close"), "close active pane"))
                    /// ///////////////////////////////////////////////////////////////////////////////////////
                    .addRow(List.of("{{[g]&w}}floating widgets", "{{[g]&w}}"))
                    .addRow(List.of(kc("<alt>+w"), "cycle focus between floating widgets"))
                    .addRow(List.of(kc("<alt>+^") + "/" + kc("<alt>+v"), "grow/shrink focused widget height"))
                    .addRow(List.of(kc("<alt>+>") + "/" + kc("<alt>+<"), "grow/shrink focused widget width"))
                    .addRow(List.of(kc("<alt>+u") + "/" + kc("<alt>+d"), "scroll focused widget up/down"))
                    .addRow(List.of(kc("<alt>+,") + "/" + kc("<alt>+."), "scroll focused widget left/right"))
                    .addRow(List.of(kc("<wheel>") + "/" + kc("<alt>+0"), "scroll focused widget"))
                    .addRow(List.of(kc("<click>"), "focus widget; click empty terminal to unfocus"))
                    .addRow(List.of(kc("<drag>"), "select terminal text (row by row)"))
                    .addRow(List.of(kc("<shift>+<drag>"), "select terminal text while a widget holds the mouse"))
                    .addRow(List.of(kc("<ctrl>+<shift>+<drag>"), "column/block select terminal text (like an IDE)"))
                    .addRow(List.of(cc(":widgets") + "  " + cc(":focus-widget [off]"), "list floating widgets / clear (or set) the focus"))
                    //.addRow(List.of(cc(":scroll [up|down|pageup|pagedown|left|right|top|bottom] [n]"), "move (or report) the focused widget's viewport"))
                    .addRow(List.of(cc(":keymap"), "who currently owns the builtin shortcut keys (builtin vs shadowed)"))
                    /// ///////////////////////////////////////////////////////////////////////////////////////
                    .addRow(List.of("{{[g]&w}}completion", "{{[g]&w}}"))
                    .addRow(List.of(kc("<tab>") + " at / or :", "fURI path auto-complete"))
                    .addRow(List.of(kc("<tab>") + " at .", "instruction auto-complete"))
                    .addRow(List.of(kc("<tab>") + " on expression", "interactive compilation menu"))
                    /// ///////////////////////////////////////////////////////////////////////////////////////
                    .addRow(List.of("{{[g]&w}}tools", "{{[g]&w}}"))
                    .addRow(List.of(cc(":subs"), "browse active subscriptions"))
                    .addRow(List.of(cc(":subq [uri]"), "change pane subscription URI"))
                    .addRow(List.of(cc(":justify [left|right]"), "justify nested poly output"))
                    .addRow(List.of(cc(":top"), "system process monitor"))
                    .addRow(List.of(cc(":less"), "obj string pager"))
                    .addRow(List.of(cc(":state [level]"), "set status line state (trace|debug|info|warn|error)"))
                    .style().headerDivider("{{[b]&w}}│").divider("{{g}}│").margin(0, 0, 0, 0).applyStyle().format()).style().margin(0, 0, 0, 0).border(Border.continuous.foreground("{{b}}")).applyStyle().format();
            if (console.isSplitMode() && console.getActivePane() != null) {
                console.getActivePane().appendOutput(helpText);
            } else {
                Graphitty.out(Console.getTerminal().output(), helpText);
            }
            return noobj();
        }), MUTABLE);

        // ===== redirect/input =====
        this.at("redirect/input", instLambda((lhs, inst) -> {
            if (lhs.isStr() && !lhs.strValue().isBlank()) {
                this.console.input = ObjmtronSerializer.parse(lhs.strValue());
                this.console.logger().info("redirecting console input to %s", this.console.input);
            } else {
                this.console.logger().info("console input currently redirected to %s", this.console.input);
            }
            return noobj();
        }), MUTABLE);

        // ===== header =====
        this.at("header", instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(NOOBJ_TID), lst(), (lhs, inst) -> {
            console.outputHeader(lhs.isStr() ? lhs.strValue() : "");
            return noobj();
        }), MUTABLE);

        // ===== quit =====
        this.at("quit", instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(NOOBJ_TID), lst(), (lhs, inst) -> {
            console.close();
            System.exit(0);
            return noobj();
        }), MUTABLE);
        bindKey("quit", ctrl('q'));

        // ===== reset =====
        this.at("reset", instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(NOOBJ_TID), lst(), (lhs, inst) -> {
            BootLoader.RESET = true;
            console.close();
            System.exit(BootLoader.EXIT_RESET);
            return noobj();
        }), MUTABLE);

        // ===== clear =====
        this.at("clear", instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(NOOBJ_TID), lst(), (lhs, inst) -> {
            // through the screen, so the display and the screen's model of it are wiped together
            console.clearTranscript();
            console.getStatus().refresh();
            return noobj();
        }), MUTABLE);
        // ===== logger =====
        this.at("logger", instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(NOOBJ_TID), lst(), (lhs, inst) -> {
            if (lhs.isStr() && !lhs.strValue().isBlank()) {
                final String[] args = lhs.strValue().split(" ");
                LogObj.setSLF4J(args[0]);
                if (args.length > 1)
                    GraphittyLogger.setDefaultTargetPane(Integer.parseInt(args[1]));
            }
            this.console.logger().info("logger level: %s [target pane: %s]", LogObj.getSLF4J().toString().toLowerCase(), GraphittyLogger.getDefaultTargetPane());
            return noobj();
        }), MUTABLE);

        // ===== check =====
        this.at("typer", instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(NOOBJ_TID), lst(), (lhs, inst) -> {
            if (lhs.isStr() && !lhs.strValue().isBlank()) {
                Arrays.stream(lhs.strValue().split(" ")).forEach(s -> {
                    if (!s.trim().isEmpty()) {
                        if (s.startsWith("-"))
                            TypeCheck.disable(TypeCheck.valueOf(s.substring(1).toLowerCase()));
                        else
                            TypeCheck.enable(TypeCheck.valueOf(s.toLowerCase()));
                    }
                });
            }
            this.console.logger().info("typer stages {{%s}}%s{{X}}", TypeCheck.colorLevel(), TypeCheck.getEnabled());
            return noobj();
        }), MUTABLE);

        // ===== top =====
        this.at("top", instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(NOOBJ_TID), lst(), (lhs, inst) -> {
            try {
                TTop.ttop(Console.getTerminal(), new PrintStream(Console.getTerminal().output()), System.err, new String[0]);
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
            return noobj();
        }), MUTABLE);

        // ===== less =====
        this.at("less", instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(NOOBJ_TID), lst(), (lhs, inst) -> {
            try {
                Commands.less(Console.getTerminal(), Console.getTerminal().input(), new PrintStream(Console.getTerminal().output()), System.err, Paths.get(""), new String[0]);
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
            return noobj();
        }), MUTABLE);

        // ===== bg — jobs detached with <alt>+b =====
        this.at("bg", instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(NOOBJ_TID), lst(), (lhs, inst) -> {
            final String arg = lhs.isStr() ? lhs.strValue().trim() : "";
            if (arg.startsWith("stop")) {
                final int stopped = console.stopBackgroundJobs();
                this.console.logger().info("stopped {{y}}%d{{X}} detached job%s", stopped, 1 == stopped ? "" : "s");
            } else if (console.backgroundJobVids().isEmpty()) {
                this.console.logger().info("no detached jobs — {{y}}<" + Hotkeys.DETACH_COMBO + ">{{X}} backgrounds whatever holds the console");
            } else {
                console.backgroundJobVids().forEach(vid ->
                        this.console.logger().info("{{y}}%s{{X}} {{k}}running{{X}}", vid));
                this.console.logger().info("{{m}}:bg stop{{X}} stops them all");
            }
            return noobj();
        }), MUTABLE);

        // ===== subs =====
        this.at("subs", instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(NOOBJ_TID), lst(), (lhs, inst) -> {
            final SubsWidget selector = new SubsWidget(console);
            selector.run();
            selector.close();
            return noobj();
        }), MUTABLE);

        // ===== justify =====
        this.at("justify", instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(NOOBJ_TID), lst(), (lhs, inst) -> {
            final boolean leftJustify = lhs.isStr() && lhs.strValue().equalsIgnoreCase("left");
            ((Highlighter) console.getReader().getHighlighter()).justify(leftJustify);
            this.console.logger().info("%s justifying nested polys", leftJustify ? "{{y}}left{{X}}" : "{{y}}right{{X}}");
            return noobj();
        }), MUTABLE);

        // ===== state =====
        this.at("state", instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(NOOBJ_TID), lst(), (lhs, inst) -> {
            console.getStatus().setState(Level.valueOf(lhs.isStr() ? lhs.strValue().toUpperCase() : ""));
            return noobj();
        }), MUTABLE);

        // ===== tracer =====
        this.at("tracer", instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(NOOBJ_TID), lst(), (lhs, inst) -> {
            final boolean newState = lhs.isStr() && !lhs.strValue().isBlank()
                    ? lhs.strValue().trim().equalsIgnoreCase("on")
                    : !Tracer.java_stack.enabled();
            if (newState) Tracer.enable(Tracer.java_stack);
            else Tracer.disable(Tracer.java_stack);
            this.console.logger().info("tracer {{%s}}%s{{X}}", newState ? "g" : "r", newState ? "ON" : "OFF");
            return noobj();
        }), MUTABLE);

        // ===== prefix =====
        this.at("prefix", instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(NOOBJ_TID), lst(), (lhs, inst) -> {
            String text = lhs.isStr() ? lhs.strValue() : "";
            if (text.startsWith("\"")) text = text.substring(1);
            if (text.endsWith("\"")) text = text.substring(0, text.length() - 1);
            console.prefix = text;
            return noobj();
        }), MUTABLE);

        // ===== postfix =====
        this.at("postfix", instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(NOOBJ_TID), lst(), (lhs, inst) -> {
            String text = lhs.isStr() ? lhs.strValue() : "";
            if (text.startsWith("\"")) text = text.substring(1);
            if (text.endsWith("\"")) text = text.substring(0, text.length() - 1);
            console.postfix = text;
            return noobj();
        }), MUTABLE);

        // ===== split =====
        this.at("split", instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(NOOBJ_TID), lst(), (lhs, inst) -> {
            final String arg = lhs.isStr() ? lhs.strValue() : "";
            try {
                final SplitLayout direction = arg.isEmpty()
                        ? SplitLayout.VERTICAL
                        : SplitLayout.parse(arg);
                console.split(direction);
                console.renderPanes();
            } catch (IllegalArgumentException e) {
                this.console.logger().error(e.getMessage());
            }
            return noobj();
        }), MUTABLE);

        // ===== close =====
        this.at("close", instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(NOOBJ_TID), lst(), (lhs, inst) -> {
            console.closeActivePane();
            if (console.isSplitMode()) {
                console.renderPanes();
            } else {
                Graphitty.out(Console.getTerminal().output(), "{{XX}}");
            }
            return noobj();
        }), MUTABLE);

        // ===== focus =====
        this.at("focus", instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(NOOBJ_TID), lst(), (lhs, inst) -> {
            final String arg = lhs.isStr() ? lhs.strValue() : "";
            if (arg.isEmpty()) {
                this.console.logger().info("panes: %s, active: {{y}}%d{{X}}",
                        console.getAllPanes().stream().map(p -> String.valueOf(p.id())).toList(),
                        console.getActivePane().id());
            } else {
                try {
                    final int paneId = Integer.parseInt(arg);
                    console.focusPane(paneId);
                    if (console.isSplitMode()) console.renderPanes();
                } catch (NumberFormatException e) {
                    this.console.logger().error("invalid pane id: {{r}}%s{{X}}", arg);
                }
            }
            return noobj();
        }), MUTABLE);

        // ===== panes =====
        this.at("panes", instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(NOOBJ_TID), lst(), (lhs, inst) -> {
            final List<Pane> panes = console.getAllPanes();
            this.console.logger().info("{{y}}%d{{X}} pane(s):", panes.size());
            for (final Pane p : panes) {
                final String active = (p == console.getActivePane()) ? " {{g}}[active]{{X}}" : "";
                this.console.logger().info("  [{{y}}%d{{X}}] %d lines%s",
                        p.id(), p.outputBuffer().size(), active);
            }
            return noobj();
        }), MUTABLE);

        // ===== subq =====
        this.at("subq", instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(NOOBJ_TID), lst(), (lhs, inst) -> {
            final String strip = lhs.isStr() ? lhs.strValue() : "";
            final fURI subURI = strip.isEmpty()
                    ? console.vid().extend("pane").extend(console.getActivePane().id() + "")
                    : f(strip);
            final Pane pane = console.getAllPanes().stream()
                    .filter(p -> p.id() == console.getActivePane().id())
                    .findFirst().orElse(null);
            if (pane == null) {
                this.console.logger().error("unable to find active pane: %d", console.getActivePane().id());
            } else {
                pane.unsubscribe();
                pane.vid(subURI);
                pane.subscribe();
            }
            return noobj();
        }), MUTABLE);

        // ===== stop-agents (Ctrl+D) =====
        /*this.at("stop-agents", instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(NOOBJ_TID), lst(), (lhs, inst) -> {
            try {
                BootLoader.getExecutor().elements()
                        .filter(r -> Router.readFromSpace(r.first().uriValue().q(DOCQ_PATTERN)).toString().contains("agent"))
                        .forEach(r -> ((mThread) r.second()).stop());
            } catch (final Exception ignored) {
                // readFromSpace can fail if no space supports the pattern
            }
            return noobj();
        }), MUTABLE);*/

        // ===== format (Ctrl+F) =====
        this.at("format", instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(NOOBJ_TID), lst(), (lhs, inst) -> {
            final String current = console.getReader().getBuffer().toString();
            try {
                if (current.contains("\n")) {
                    console.getReader().getBuffer().clear();
                    console.getReader().getBuffer().write(current.replace("\n", ""));
                } else {
                    final String formatted = ObjmtronSerializer.parse(current).toString();
                    console.getReader().getBuffer().clear();
                    console.getReader().getBuffer().write(formatted);
                }
            } catch (final Exception e) {
                // do nothing (most likely unparsable buffer)
            }
            return noobj();
        }), MUTABLE);
        bindKey("format", alt('f'));

        // ===== next-pane (Ctrl+W) =====
        this.at("next-pane", instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(NOOBJ_TID), lst(), (lhs, inst) -> {
            if (console.isSplitMode()) {
                console.nextPane();
            }
            return noobj();
        }), MUTABLE);

        // ===== prev-pane (Alt+W) =====
        this.at("prev-pane", instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(NOOBJ_TID), lst(), (lhs, inst) -> {
            if (console.isSplitMode()) {
                console.prevPane();
            }
            return noobj();
        }), MUTABLE);

        // ===== next-widget =====
        this.at("next-widget", instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(NOOBJ_TID), lst(), (lhs, inst) -> {
            console.nextWidget();
            return noobj();
        }), MUTABLE);

        // ===== prev-widget =====
        this.at("prev-widget", instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(NOOBJ_TID), lst(), (lhs, inst) -> {
            console.prevWidget();
            return noobj();
        }), MUTABLE);

        // ===== focus-widget [name | off] =====
        this.at("focus-widget", instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(NOOBJ_TID), lst(), (lhs, inst) -> {
            final String arg = lhs.isStr() ? lhs.strValue().trim() : "";
            if (arg.isEmpty() || arg.equalsIgnoreCase("off") || arg.equalsIgnoreCase("none")) {
                console.focusWidget(null);
                return noobj();
            }
            studio.phaseshift.metatron.isa.mach.type.ui.Widget<?> match = null;
            for (final studio.phaseshift.metatron.isa.mach.type.ui.Widget<?> w : console.getFloatingWidgets()) {
                if (arg.equalsIgnoreCase(FloatingSurface.widgetKey(w))) {
                    match = w;
                    break;
                }
            }
            if (null == match)
                this.console.logger().error("no floating widget named {{r}}%s{{X}}", arg);
            else
                console.focusWidget(match);
            return noobj();
        }), MUTABLE);

        // ===== widgets (list floating widgets, marked like :panes) =====
        this.at("widgets", instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(NOOBJ_TID), lst(), (lhs, inst) -> {
            final List<studio.phaseshift.metatron.isa.mach.type.ui.Widget<?>> widgets = console.getFloatingWidgets();
            final studio.phaseshift.metatron.isa.mach.type.ui.Widget<?> active = console.getActiveWidget();
            final String activeKey = null == active ? null : FloatingSurface.widgetKey(active);
            this.console.logger().info("{{y}}%d{{X}} floating widget(s):", widgets.size());
            for (final studio.phaseshift.metatron.isa.mach.type.ui.Widget<?> w : widgets) {
                final String key = FloatingSurface.widgetKey(w);
                final String activeMark = key.equals(activeKey) ? " {{g}}[active]{{X}}" : "";
                final String scrollInfo = console.getFloatingSurface().scrollInfo(w);
                final String scrollMark = scrollInfo.isEmpty() ? "" : " {{m}}" + scrollInfo + "{{X}}";
                this.console.logger().info("  [%s] %s%s%s", key, w.getClass().getSimpleName(), scrollMark, activeMark);
            }
            if (widgets.isEmpty())
                this.console.logger().info("  (none)");
            return noobj();
        }), MUTABLE);

        // ===== links (how a clickable uri is drawn, and what the console believes about it) =====
        this.at("links", instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(NOOBJ_TID), lst(), (lhs, inst) -> {
            final String arg = lhs.isStr() ? lhs.strValue().trim() : "";
            if (arg.isEmpty()) {
                // the report is the diagnostic: a uri that is drawn but not clickable is either a
                // screen the console does not own the rows of, or a pointer the terminal kept
                this.console.logger().info("{{y}}links{{X}}: %s", this.console.linkReport());
                return noobj();
            }
            switch (arg.toLowerCase()) {
                case "on", "true", "yes" ->
                        studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty.linkUnderline(true);
                case "off", "false", "no" ->
                        studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty.linkUnderline(false);
                default -> {
                    this.console.logger().info("usage: :links [on|off] — the underline a clickable uri is drawn with");
                    return noobj();
                }
            }
            this.console.logger().info("link underline {{y}}%s{{X}} — a uri is still clickable either way",
                    studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty.linkUnderline() ? "on" : "off");
            return noobj();
        }), MUTABLE);

        // ===== scroll (move the focused widget's viewport over its own text) =====
        this.at("scroll", instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(NOOBJ_TID), lst(), (lhs, inst) -> {
            final String arg = lhs.isStr() ? lhs.strValue().trim() : "";
            if (arg.isEmpty()) {
                final String info = console.activeWidgetScrollInfo();
                if (info.isEmpty())
                    this.console.logger().info("focused floating widget has nothing off its viewport");
                else
                    this.console.logger().info("{{y}}%s{{X}}", info);
                return noobj();
            }
            final String[] parts = arg.split("\\s+");
            final int amount = parts.length > 1 && parts[1].matches("-?\\d+")
                    ? Integer.parseInt(parts[1]) : Integer.MIN_VALUE;
            switch (parts[0].toLowerCase()) {
                case "up", "back" ->
                        console.scrollActiveWidget(0, -(amount == Integer.MIN_VALUE ? 1 : Math.abs(amount)));
                case "down", "forward" ->
                        console.scrollActiveWidget(0, amount == Integer.MIN_VALUE ? 1 : Math.abs(amount));
                case "pageup", "page-up", "pgup" -> console.pageActiveWidget(-1);
                case "pagedown", "page-down", "pgdn" -> console.pageActiveWidget(1);
                case "left" -> console.scrollActiveWidget(-1, 0);
                case "right" -> console.scrollActiveWidget(1, 0);
                case "top", "home" -> console.scrollActiveWidgetTo(0);
                case "bottom", "tail", "end" -> console.tailActiveWidget();
                case "line" -> {
                    final int row = (parts.length > 1 && parts[1].matches("-?\\d+")) ? Integer.parseInt(parts[1]) : 0;
                    console.scrollActiveWidgetTo(row);
                }
                default ->
                        this.console.logger().error("{{r}}%s{{X}} is not a scroll action (up, down, pageup, pagedown, left, right, top, bottom, line N)",
                                parts[0]);
            }
            return noobj();
        }), MUTABLE);

        // ===== keymap (who currently owns the builtin shortcut keys) =====
        this.at("keymap", instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(NOOBJ_TID), lst(), (lhs, inst) -> {
            final org.jline.keymap.KeyMap<?> keyMap = console.getWidgets().getKeyMap();
            final long escCount = keyMap.getBoundKeys().entrySet().stream()
                    .filter(e -> e.getKey().startsWith("\033")).count();
            this.console.logger().info("{{y}}%d{{X}} escape-sequence bindings in the active keymap", escCount);
            this.console.logger().info("built-in shortcut keys ({{g}}builtin{{X}} = held by the console, reasserted each prompt):");
            for (final String sequence : console.builtinKeySequences()) {
                final Object bound = console.boundKeyHandler(sequence);
                final Object ours = console.builtinKeyHandler(sequence);
                final boolean oursActive = (null != ours) && (ours == bound);
                final String label = this.builtinKeyLabels.getOrDefault(sequence, org.jline.keymap.KeyMap.display(sequence));
                this.console.logger().info("  %-8s -> %s %s", label, (null == bound) ? "(unbound)" : (oursActive ? "{{g}}builtin{{X}}" : "{{r}}shadowed{{X}}"), (oursActive || null == bound) ? "" : "; owner: " + bound);
            }
            return noobj();
        }), MUTABLE);

        // ===== shrink (Alt+<) =====
        this.at("shrink", instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(NOOBJ_TID), lst(), (lhs, inst) -> {
            if (console.isSplitMode()) {
                console.resizeActivePane(-0.05f);
                console.renderPanes();
            }
            return noobj();
        }), MUTABLE);

        // ===== grow (Alt+>) =====
        this.at("grow", instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(NOOBJ_TID), lst(), (lhs, inst) -> {
            if (console.isSplitMode()) {
                console.resizeActivePane(0.05f);
                console.renderPanes();
            }
            return noobj();
        }), MUTABLE);

        // ===== typer-cycle (Ctrl+T) =====
        this.at("typer-cycle", instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(NOOBJ_TID), lst(), (lhs, inst) -> {
            if (TypeCheck.level() == 0)
                TypeCheck.enable(TypeCheck.values());
            else
                TypeCheck.disable(TypeCheck.getEnabled().stream().toList().getFirst());
            StatusLine.message(str("typer: " + TypeCheck.getEnabled()));
            return noobj();
        }), MUTABLE);
        bindKey("typer-cycle", alt('t'));

        // ===== editor (Ctrl+Y) =====
        this.at("editor", instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(NOOBJ_TID), lst(), (lhs, inst) -> {
            Editor.of(console, console.getReader().getBuffer().toString());
            return noobj();
        }), MUTABLE);
        bindKey("editor", alt('e'));

        // ===== line (Alt+L) — add a new \_ chat overlay line below the buffer =====
        this.at("line", instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(NOOBJ_TID), lst(), (lhs, inst) -> {
            final Buffer buffer = console.getReader().getBuffer();
            final String text = buffer.toString();
            int depth = 0;
            int idx = -1;
            while ((idx = text.indexOf("\\_ ", idx + 1)) >= 0) {
                depth++;
            }
            final int promptWidth = Highlighter.visualLength(Console.PROMPT);
            buffer.cursor(buffer.length());
            buffer.write("\n" + " ".repeat(promptWidth - 2 + depth) + "\\_ ");
            console.getReader().setVariable(LineReader.SECONDARY_PROMPT_PATTERN,
                    Graphitty.string("{{-X-}}{{v1&^1&m}}"));
            return noobj();
        }), MUTABLE);
        bindKey("line", alt('l'));
    }

    /**
     * Wrap a keyboard shortcut in its color code (yellow).
     */
    private static String kc(final String text) {
        return "{{y}}" + text + "{{X}}";
    }

    /**
     * Wrap a colon command in its color code (magenta).
     */
    private static String cc(final String text) {
        return "{{m}}" + text + "{{X}}";
    }

    /**
     * Register a simple 1:1 key binding that dispatches to the named command.
     * Call this right after the corresponding {@code this.at(name, ...)} definition.
     */
    private void bindKey(final String command, final String keySequence) {
        this.simpleKeys.add(Map.entry(command, keySequence));
    }

    /**
     * Bind a built-in shortcut and register it with the console so the
     * console reasserts it before every prompt — a later binder (menu line
     * key, tool) can shadow it for its own turn, but never across prompts.
     */
    private void bindBuiltin(final Widgets widgets, final String sequence,
                             final String label, final Widget handler) {
        widgets.getKeyMap().bind(handler, sequence);
        this.console.registerBuiltinKey(sequence, handler);
        this.builtinKeyLabels.put(sequence, label);
    }

    /**
     * Rows scrolled per mouse-wheel notch.
     */
    private static final int MOUSE_WHEEL_ROWS = 3;

    /**
     * A widget that scrolls the console's focused floating widget by
     * {@code (dx, dy)} — and reports "not handled" (false) when there is no
     * focused widget, or the focused one does not scroll, so a chained binding
     * can fall through to whatever owned the key before.
     */
    private static Widget scrollBy(final Console console, final int dx, final int dy) {
        return () -> console.scrollActiveWidget(dx, dy);
    }

    /**
     * Chain a widget in front of whatever currently owns a key: when the
     * override does not handle the key, the previous binding runs instead, so
     * a scroll shortcut never steals a key from the rest of the console.  The
     * previous binding is captured at bind time — and a builtin is reasserted
     * on every prompt, so a later binder cannot make the chain point at itself.
     */
    private Widget chained(final Widgets widgets, final String sequence, final Widget override) {
        final Object prior = widgets.getKeyMap().getBound(sequence);
        return () -> {
            if (override.apply()) return true;
            if (prior instanceof Widget previous && previous != null) return previous.apply();
            if (prior instanceof CharSequence text) {
                this.console.getReader().getBuffer().write(text.toString());
                return true;
            }
            return true;
        };
    }

    /**
     * sequence -&gt; human label for the builtin shortcuts (e.g. "\033<" -&gt; "alt+<")
     */
    private final java.util.Map<String, String> builtinKeyLabels = new java.util.LinkedHashMap<>();

    // ========== Keyboard Shortcuts ==========

    /**
     * Register all keyboard shortcuts on the given {@link Widgets} instance.
     * Each widget is a thin dispatcher — the action logic lives in this palette's
     * {@code :command} entries.
     */
    public void bindKeys(final Widgets widgets) {
        // Simple 1:1 dispatch — declared alongside the command via key(name, sequence)
        for (final Map.Entry<String, String> binding : this.simpleKeys) {
            widgets.getKeyMap().bind((Widget) () -> {
                this.at(binding.getKey()).apply(noobj());
                return true;
            }, binding.getValue());
        }

        // stop-agents — still inline (command temporarily commented out)
        widgets.getKeyMap().bind((Widget) () -> {
            this.at("stop-agents").apply(noobj());
            return true;
        }, ctrl('d'));

        // -------------------------------------------------------
        // Panes
        // selection: hand the pointer back to the terminal.  While the console holds the mouse a
        // click can be resolved to a row (a link, a widget) but the terminal's own selection is
        // unavailable, so this gives it back on request — re-armed at the next prompt, which makes
        // the cost one key rather than a mode.
        widgets.getKeyMap().bind((Widget) () -> {
            console.releasePointer();
            console.syncWidgetMouseTracking(true);   // write the mode now, not at the next tick
            console.logger().info("{{y}}pointer released{{X}} — the terminal has its mouse back (re-armed at the next prompt)");
            return true;
        }, alt('s'));
        widgets.getKeyMap().bind((Widget) () -> {
            if (console.isSplitMode()) {
                this.at("next-pane").apply(noobj());
                console.getReader().getBuffer().clear();
                widgets.callWidget("accept-line");
            }
            return true;
        }, alt('n'));
        widgets.getKeyMap().bind((Widget) () -> {
            if (console.isSplitMode()) {
                this.at("prev-pane").apply(noobj());
                console.getReader().getBuffer().clear();
                widgets.callWidget("accept-line");
            } else {
                console.getReader().getBuffer().up();
                console.getReader().getBuffer().write("\n");
            }
            return true;
        }, alt('p'));
        // -------------------------------------------------------
        // Floating widgets — a focused widget takes the resize keys;
        // panes keep their existing grow/shrink when nothing is focused.
        // (alt+> grows / alt+< shrinks, in both worlds — one muscle memory.)
        //
        // These five are REASSERTED builtins: the console reclaims them
        // before every prompt, so no later binder (menu line key, tool,
        // anything) can shadow a shortcut across turns.
        //
        // NOTE: the jline fork pre-binds \e< and \e> to
        // beginning-of-history / end-of-history — silent, no output.  That
        // is why an unshadowed-by-us alt+< / alt+> "does nothing" (it is
        // quietly jumping the prompt history).
        {
            final Widget shrinkWidth = () -> {
                if (console.getActiveWidget() != null) {
                    console.shrinkActiveWidgetWidth();
                } else if (console.isSplitMode()) {
                    this.at("shrink").apply(noobj());
                    console.redrawBuffer();
                }
                return true;
            };
            bindBuiltin(widgets, "\033<", "alt+<", shrinkWidth);
            final Widget growWidth = () -> {
                if (console.getActiveWidget() != null) {
                    console.growActiveWidgetWidth();
                } else if (console.isSplitMode()) {
                    this.at("grow").apply(noobj());
                    console.redrawBuffer();
                }
                return true;
            };
            bindBuiltin(widgets, "\033>", "alt+>", growWidth);
            final Widget nextFocus = () -> {
                if (console.hasFloatingWidgets()) {
                    console.nextWidget();
                }
                return true;
            };
            bindBuiltin(widgets, alt('w'), "alt+w", nextFocus);
            final Widget shrinkHeight = () -> {
                if (console.getActiveWidget() != null) {
                    // v = down = shrink height (free edge moves per anchor)
                    console.shrinkActiveWidgetHeight();
                }
                return true;
            };
            bindBuiltin(widgets, alt('v'), "alt+v", shrinkHeight);
            final Widget growHeight = () -> {
                if (console.getActiveWidget() != null) {
                    // ^ = up = grow height (free edge moves per anchor)
                    console.growActiveWidgetHeight();
                }
                return true;
            };
            bindBuiltin(widgets, "\033^", "alt+^", growHeight);
        }

        // -------------------------------------------------------
        // Widget scrolling — the focused widget's viewport over its own
        // text.  A pinned widget draws through a viewport, so what does not
        // fit is off the viewport, not gone: these keys move the viewport
        // (the mouse wheel does the same while a widget has something to
        // scroll).  They are chained builtins: when no scrollable widget is
        // focused the key falls back to whatever bound it before (jline's own
        // page/history bindings), so the console behaves as it always did.
        // -------------------------------------------------------
        {
            bindBuiltin(widgets, "\033u", "alt+u", chained(widgets, "\033u", scrollBy(console, 0, -1)));
            bindBuiltin(widgets, "\033d", "alt+d", chained(widgets, "\033d", scrollBy(console, 0, 1)));
            bindBuiltin(widgets, "\033,", "alt+,", chained(widgets, "\033,", scrollBy(console, -1, 0)));
            bindBuiltin(widgets, "\033.", "alt+.", chained(widgets, "\033.", scrollBy(console, 1, 0)));
            bindBuiltin(widgets, "\0330", "alt+0", chained(widgets, "\0330", () -> {
                if (null == console.getActiveWidget()) return false;
                console.tailActiveWidget();
                return true;
            }));
            // PageUp/PageDown are jline's history keys; they only become scroll
            // keys while a widget that HAS content off its viewport is focused,
            // and fall back to jline otherwise.
            for (final String[] page : List.of(new String[]{"\033[5~", "-1"}, new String[]{"\033[6~", "1"})) {
                final int direction = Integer.parseInt(page[1]);
                bindBuiltin(widgets, page[0], direction < 0 ? "pageup" : "pagedown",
                        chained(widgets, page[0], () -> {
                            if (!console.activeWidgetScrolls()) return false;
                            console.pageActiveWidget(direction);
                            return true;
                        }));
            }
        }

        // -------------------------------------------------------
        // Mouse wheel — scrolls the widget under the pointer (or the focused
        // one) while terminal mouse tracking is on.  The console turns tracking
        // on while any widget is on screen (see Console.syncWidgetMouseTracking),
        // so a click can always focus — or re-focus — a widget.
        // -------------------------------------------------------
        {
            final Widget mouse = () -> {
                try {
                    final org.jline.terminal.MouseEvent event = console.getReader().readMouseEvent();
                    if (null == event) return true;
                    // jline reports the pointer 0-BASED (a terminal's own
                    // coordinates are 1-based, so a click at column 5 arrives as
                    // x=4); widget geometry and the console's gestures are
                    // 1-based, so the conversion happens exactly here.
                    final int row = event.getY() + 1;
                    final int col = event.getX() + 1;
                    final FloatingSurface surface = console.getFloatingSurface();
                    final var hovered = surface.widgetAt(row, col);
                    if (event.getType() == org.jline.terminal.MouseEvent.Type.Wheel) {
                        // the wheel follows the pointer: over a widget it
                        // scrolls that widget, over empty terminal it hands the
                        // pointer back so the wheel scrolls the terminal's own
                        // scrollback (re-armed on the next prompt or alt+w)
                        if (null != hovered) {
                            final int step = event.getButton() == org.jline.terminal.MouseEvent.Button.WheelUp
                                    ? -MOUSE_WHEEL_ROWS : MOUSE_WHEEL_ROWS;
                            surface.scroll(hovered, 0, step);
                            if (console.getActiveWidget() != hovered) console.focusWidget(hovered);
                        } else if (Console.screenMode()) {
                            // The console owns its rows, so the wheel scrolls THEM: there is
                            // no terminal scrollback holding this transcript to reach, and
                            // handing the pointer over took the mouse away from the widgets —
                            // after which clicking one needed alt+w to get it back.
                            console.scrollScreen(event.getButton() == org.jline.terminal.MouseEvent.Button.WheelUp
                                    ? -MOUSE_WHEEL_ROWS : MOUSE_WHEEL_ROWS);
                        } else {
                            console.releasePointer();
                        }
                    } else if (event.getType() == org.jline.terminal.MouseEvent.Type.Pressed) {
                        // A press on the focused widget's chevron takes hold of it to
                        // drag; anything else is a click: focus what is under the
                        // pointer, let the widget work its own affordances, or — on
                        // empty terminal — drop the focus entirely.  A held control key
                        // is carried through: on a link it means "follow it now".
                        console.mousePressed(row, col,
                                event.getModifiers().contains(MouseEvent.Modifier.Control));
                    } else if (event.getType() == org.jline.terminal.MouseEvent.Type.Dragged) {
                        console.mouseDragged(row, col);
                    } else if (event.getType() == org.jline.terminal.MouseEvent.Type.Released) {
                        console.mouseReleased(row, col);
                    }
                } catch (final Exception e) {
                    // a malformed event must never break the input loop
                    this.console.logger().error(e);
                }
                return true;
            };
            for (final String sequence : org.jline.terminal.impl.MouseSupport.keys())
                bindBuiltin(widgets, sequence, "mouse", mouse);
        }
        widgets.getKeyMap().bind((Widget) () -> {
            this.at("split").apply(str("v"));
            console.redrawBuffer();
            return true;
        }, "\033[1;3C");  // Alt+<right>
        widgets.getKeyMap().bind((Widget) () -> {
            this.at("split").apply(str("h"));
            console.redrawBuffer();
            return true;
        }, "\033[1;3A");  // Alt+<up>
        // Alt+char fallback if CSI sequences cause terminal issues:
        // widgets.getKeyMap().bind((Widget) () -> {
        //     this.at("split").apply(str("v"));
        //     console.redrawBuffer();
        //     return true;
        // }, alt('v'));
        // widgets.getKeyMap().bind((Widget) () -> {
        //     this.at("split").apply(str("h"));
        //     console.redrawBuffer();
        //     return true;
        // }, alt('h'));

        // -------------------------------------------------------
        // Word navigation
        // -------------------------------------------------------
        widgets.getKeyMap().bind((Widget) () -> {
            widgets.callWidget("backward-word");
            return true;
        }, "\033[1;2D");  // Shift+<left>
        widgets.getKeyMap().bind((Widget) () -> {
            widgets.callWidget("forward-word");
            return true;
        }, "\033[1;2C");  // Shift+<right>

        // -------------------------------------------------------
        // ESC — cancel deepest chat level, or clear buffer
        // -------------------------------------------------------
        widgets.getKeyMap().bind((Widget) () -> {
            final Buffer buffer = console.getReader().getBuffer();
            final String text = buffer.toString();
            int lastChat = -1, pos = -1;
            while ((pos = text.indexOf("\\_ ", pos + 1)) >= 0) {
                lastChat = pos;
            }
            if (lastChat >= 0) {
                int lineStart = text.lastIndexOf('\n', lastChat - 1);
                if (lineStart < 0) lineStart = 0;
                buffer.cursor(lineStart);
                buffer.delete(buffer.length() - lineStart);
                if (!buffer.toString().contains("\\_ ")) {
                    console.getReader().setVariable(LineReader.SECONDARY_PROMPT_PATTERN,
                            Graphitty.string("{{-X&v1&^1&m}}     {{g}}| {{X}}"));
                }
            } else {
                buffer.clear();
            }
            return true;
        }, "\033");

        // -------------------------------------------------------
        // Tab — explain / dot-completion / furi selector
        // -------------------------------------------------------
        widgets.getKeyMap().bind((Widget) () -> {
            try {
                final String bufferText = console.getReader().getBuffer().toString();
                if (bufferText.trim().startsWith(":")) {
                    // colon menu selector widget
                } else {
                    if (bufferText.trim().endsWith(".")) {
                        final Obj parsed = ObjmtronSerializer.parse(bufferText.trim().substring(0, bufferText.trim().length() - 1));
                        if (parsed.isCode()) {
                            Console.getTerminal().writer().write("\n");
                            final InstSelectorTool selector = new InstSelectorTool(parsed.resolve(noobj()).as(), bufferText);
                            if (selector.hasItems()) {
                                if (console.isSplitMode() && console.getActivePane() != null) {
                                    final int[] pos = console.calculatePanePosition(console.getActivePane());
                                    if (pos != null) selector.setPaneBounds(pos[0], pos[1], pos[2], pos[3]);
                                }
                                Utilities.runCursorLessWidget(selector, true);
                                if (console.isSplitMode()) {
                                    console.renderPanesNow();
                                }
                            }
                        }
                    } else if (bufferText.trim().startsWith("*") && (bufferText.trim().endsWith("/") || bufferText.trim().endsWith(":"))) {
                        Console.getTerminal().writer().write("\n");
                        final fURISelectorTool selector = new fURISelectorTool(bufferText);
                        if (selector.hasItems()) {
                            if (console.isSplitMode() && console.getActivePane() != null) {
                                final int[] pos = console.calculatePanePosition(console.getActivePane());
                                if (pos != null) selector.setPaneBounds(pos[0], pos[1], pos[2], pos[3]);
                            }
                            Utilities.runCursorLessWidget(selector, true);
                            if (console.isSplitMode()) {
                                console.renderPanesNow();
                            }
                        }
                    } else {
                        final Obj code = ObjmtronSerializer.parse(bufferText);
                        if (code.isCode()) {
                            Console.getTerminal().writer().write("\n");
                            final ExplainTool explain = new ExplainTool(code.as());
                            if (console.isSplitMode() && console.getActivePane() != null) {
                                final int[] pos = console.calculatePanePosition(console.getActivePane());
                                if (pos != null) explain.setPaneBounds(pos[0], pos[1], pos[2], pos[3]);
                            }
                            Utilities.runCursorLessWidget(explain, true);
                            if (console.isSplitMode()) {
                                console.renderPanesNow();
                            }
                            console.redrawBuffer();
                        }
                    }
                }
            } catch (final Exception e) {
                this.console.logger().error(e);
            }
            return true;
        }, key(Console.getTerminal(), InfoCmp.Capability.tab));

        // -------------------------------------------------------
        // Alt+K — erase buffer back to first occurrence of char
        // -------------------------------------------------------
        for (char c = 32; c <= 126; c++) {
            final char targetChar = c;
            final String altKSequence = "\033k" + c;
            widgets.getKeyMap().bind((Widget) () -> {
                eraseBackToChar(console.getReader(), targetChar);
                return true;
            }, altKSequence);
        }
    }

    /**
     * Erase the buffer back to (and including) the first occurrence of {@code targetChar}.
     */
    private static void eraseBackToChar(final LineReader reader, final char targetChar) {
        final Buffer buffer = reader.getBuffer();
        final String currentText = buffer.toString();
        final int cursorPos = buffer.cursor();
        if (cursorPos == 0) return;
        int targetPos = -1;
        for (int i = cursorPos - 1; i >= 0; i--) {
            if (currentText.charAt(i) == targetChar) {
                targetPos = i;
                break;
            }
        }
        if (targetPos == -1) return;
        buffer.cursor(targetPos);
        buffer.delete(cursorPos - targetPos);
    }

}
