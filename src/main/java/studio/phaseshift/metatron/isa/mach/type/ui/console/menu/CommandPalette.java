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
import org.slf4j.event.Level;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.Tracer;
import studio.phaseshift.metatron.TypeCheck;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.LogObj;
import studio.phaseshift.metatron.isa.mach.type.ui.Border;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Console;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Editor;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Highlighter;
import studio.phaseshift.metatron.isa.mach.type.ui.console.StatusLine;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.isa.mach.type.ui.tmux.Pane;
import studio.phaseshift.metatron.isa.mach.type.ui.tmux.SplitLayout;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.PanelWidget;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.SubsWidget;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.TableWidget;

import java.io.PrintStream;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.M_ISA_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.NOOBJ_TID;
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
    private final GraphittyLogger LOG = Graphitty.log(this);
    private final Console console;

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
                    .addRow(List.of(cc(":check [stages]"), "show or enable/disable type checking stages (+stage/-stage)"))
                    .addRow(List.of(kc("<alt>+t") + "  " + cc(":cycle-check"), "cycle type check activations"))
                    .addRow(List.of(cc(":trace [on|off]"), "toggle Java stack trace dump on fail"))
                    /// ///////////////////////////////////////////////////////////////////////////////////////
                    .addRow(List.of("{{[g]&w}}console", "{{[g]&w}}"))
                    .addRow(List.of(kc("<ctrl>+q") + "  " + cc(":quit"), "exit the console"))
                    .addRow(List.of(cc(":reset"), "reboot the metatron vm"))
                    .addRow(List.of(cc(":clear"), "clear the console"))
                    .addRow(List.of(cc(":header [name]"), "print random or named metatron header"))
                    .addRow(List.of(cc(":log [level] [pane]"), "show or set log level and target pane"))
                    .addRow(List.of(cc(":redirect/input [inst]"), "redirect console input via inst (noobj for default)"))
                    .addRow(List.of(cc(":prefix [text]"), "prefix input with text"))
                    .addRow(List.of(cc(":postfix [text]"), "postfix input with text"))
                    .addRow(List.of(kc("<alt>+f") + "  " + cc(":format"), "pretty-print current buffer"))
                    .addRow(List.of(kc("<alt>+e") + "  " + cc(":editor"), "full screen nano editor with current buffer"))
                    //.addRow(List.of(kc("<ctrl>+d") + "  " + cc(":stop-agents"), "stop all agent threads"))
                    .addRow(List.of(kc("<alt>+l") + "  " + cc(":line"), "add a new chat overlay line (\\_)"))
                    .addRow(List.of(cc(":lang [mtron|gremlin|sql]"), "switch console language"))
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

        // ===== reset =====
        this.at("reset", instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(NOOBJ_TID), lst(), (lhs, inst) -> {
            BootLoader.RESET = true;
            console.close();
            System.exit(BootLoader.EXIT_RESET);
            return noobj();
        }), MUTABLE);

        // ===== clear =====
        this.at("clear", instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(NOOBJ_TID), lst(), (lhs, inst) -> {
            Graphitty.out(Console.getTerminal().output(), "{{XX}}");
            console.getStatus().refresh();
            return noobj();
        }), MUTABLE);
        // ===== log =====
        this.at("log", instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(NOOBJ_TID), lst(), (lhs, inst) -> {
            if (lhs.isStr() && !lhs.strValue().isBlank()) {
                final String[] args = lhs.strValue().split(" ");
                LogObj.setSLF4J(args[0]);
                if (args.length > 1)
                    GraphittyLogger.setDefaultTargetPane(Integer.parseInt(args[1]));
            }
            LOG.none("log level: %s [target pane: %s]\n", LogObj.getSLF4J().toString().toLowerCase(), GraphittyLogger.getDefaultTargetPane());
            return noobj();
        }), MUTABLE);

        // ===== check =====
        this.at("check", instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(NOOBJ_TID), lst(), (lhs, inst) -> {
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
            LOG.info("type check stages {{%s}}%s{{X}}", TypeCheck.colorLevel(), TypeCheck.getEnabled());
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
            LOG.info("%s justifying nested polys", leftJustify ? "{{y}}left{{X}}" : "{{y}}right{{X}}");
            return noobj();
        }), MUTABLE);

        // ===== state =====
        this.at("state", instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(NOOBJ_TID), lst(), (lhs, inst) -> {
            console.getStatus().setState(Level.valueOf(lhs.isStr() ? lhs.strValue().toUpperCase() : ""));
            return noobj();
        }), MUTABLE);

        // ===== trace =====
        this.at("trace", instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(NOOBJ_TID), lst(), (lhs, inst) -> {
            final boolean newState = lhs.isStr() && !lhs.strValue().isBlank()
                    ? lhs.strValue().trim().equalsIgnoreCase("on")
                    : !Tracer.stack.enabled();
            if (newState) Tracer.enable(Tracer.stack);
            else Tracer.disable(Tracer.stack);
            LOG.info("trace {{%s}}%s{{X}}", newState ? "g" : "r", newState ? "ON" : "OFF");
            return noobj();
        }), MUTABLE);

        // ===== lang =====
        this.at("lang", instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(NOOBJ_TID), lst(), (lhs, inst) -> {
            final String langName = lhs.isStr() ? lhs.strValue().toLowerCase() : "";
            try {
                final Console.Language newLang = Console.Language.valueOf(langName.toUpperCase());
                console.setLanguage(newLang);
            } catch (IllegalArgumentException e) {
                LOG.error("unknown language: {{r}}%s{{X}}. Available: mtron, gremlin, sql", langName);
            }
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
                LOG.error(e.getMessage());
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
                LOG.info("panes: %s, active: {{y}}%d{{X}}",
                        console.getAllPanes().stream().map(p -> String.valueOf(p.id())).toList(),
                        console.getActivePane().id());
            } else {
                try {
                    final int paneId = Integer.parseInt(arg);
                    console.focusPane(paneId);
                    if (console.isSplitMode()) console.renderPanes();
                } catch (NumberFormatException e) {
                    LOG.error("invalid pane id: {{r}}%s{{X}}", arg);
                }
            }
            return noobj();
        }), MUTABLE);

        // ===== panes =====
        this.at("panes", instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(NOOBJ_TID), lst(), (lhs, inst) -> {
            final List<Pane> panes = console.getAllPanes();
            LOG.info("{{y}}%d{{X}} pane(s):", panes.size());
            for (final Pane p : panes) {
                final String active = (p == console.getActivePane()) ? " {{g}}[active]{{X}}" : "";
                LOG.info("  [{{y}}%d{{X}}] %s, %d lines%s",
                        p.id(), p.language().name, p.outputBuffer().size(), active);
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
                LOG.error("unable to find active pane: %d", console.getActivePane().id());
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

        // ===== cycle-check (Ctrl+T) =====
        this.at("cycle-check", instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(NOOBJ_TID), lst(), (lhs, inst) -> {
            if (TypeCheck.level() == 0)
                TypeCheck.enable(TypeCheck.values());
            else
                TypeCheck.disable(TypeCheck.getEnabled().stream().toList().getFirst());
            StatusLine.message(str("typer: " + TypeCheck.getEnabled()));
            return noobj();
        }), MUTABLE);

        // ===== editor (Ctrl+Y) =====
        this.at("editor", instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(NOOBJ_TID), lst(), (lhs, inst) -> {
            Editor.of(console, console.getReader().getBuffer().toString());
            return noobj();
        }), MUTABLE);

        // ===== line (Alt+L) — add a new \_ chat overlay line below the buffer =====
        this.at("line", instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(NOOBJ_TID), lst(), (lhs, inst) -> {
            final Buffer buffer = console.getReader().getBuffer();
            final String text = buffer.toString();
            int depth = 0;
            int idx = -1;
            while ((idx = text.indexOf("\\_ ", idx + 1)) >= 0) {
                depth++;
            }
            final int promptWidth = Highlighter.visualLength(console.getCurrentLanguage().prompt);
            buffer.cursor(buffer.length());
            buffer.write("\n" + " ".repeat(promptWidth - 2 + depth) + "\\_ ");
            console.getReader().setVariable(LineReader.SECONDARY_PROMPT_PATTERN,
                    Graphitty.string("{{-X-}}{{v1&^1&m}}"));
            return noobj();
        }), MUTABLE);
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

}
