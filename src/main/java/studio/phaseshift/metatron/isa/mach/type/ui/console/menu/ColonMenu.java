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
import org.slf4j.event.Level;
import studio.phaseshift.metatron.TypeCheck;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.type.mModel;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.impl.MInst;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.m.type.reflect.JRec;
import studio.phaseshift.metatron.isa.mach.type.LogObj;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.Border;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Console;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Highlighter;
import studio.phaseshift.metatron.isa.mach.type.ui.console.SubsWidget;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.Pane;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.Panel;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.SplitLayout;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.Table;
import studio.phaseshift.metatron.util.MTronException;

import java.io.PrintStream;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.SUBQ;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.M_ISA_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.NOOBJ_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_from_;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.block_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.type.ui.console.Console.CONSOLE_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class ColonMenu extends MRec {

    public static final fURI COLON_MENU_TID = CONSOLE_TID.extend("colon_menu");
    private final GraphittyLogger LOG = Graphitty.log(this);
    private final Console console;

    public Rec attach(final Rec menuRec, final String... menuItemsToAdd) {
        for (final String item : menuItemsToAdd.length == 0 ? this.getMenuItems() : menuItemsToAdd) {
            menuRec.at(item, this.at(item).clone(), MUTABLE);
        }
        return this.console.at("menu", menuRec, MUTABLE);
    }

    public String[] getMenuItems() {
        return this.keys().map(x -> x.uriValue().toString()).toArray(String[]::new);
    }
    
    public ColonMenu(final Console console) {
        super(mutableMap(uri("console"), auto_from_(console.vid()).tryToInst()), COLON_MENU_TID, console.vid().extend("colon_menu"));
        this.console = console;

        // ===== help =====
        this.at("help", instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(NOOBJ_TID), lst(), (lhs, inst) -> {
            final String helpText = new Panel("{{c}}metatron console help{{X}}", new Table(
                    List.of("name", "short", "description"))
                    /// ///////////////////////////////////////////////////////////////////////////////////////
                    .addRow(List.of("{{[g]&w}}mtron", "{{[g]&w}}", "{{[g]&w}}"))
                    .addRow(List.of("explain", "<tab>", "a tabular view of the current code"))
                    .addRow(List.of("type check", ":check [-| ] [ |type_ctor|obj_write|inst_rng|inst_dom|code_resolve]", "show or enable/disable type checking stages"))
                    .addRow(List.of("cycle type check", "<ctrl>+t", "cycle type check activations"))
                    /// ///////////////////////////////////////////////////////////////////////////////////////
                    .addRow(List.of("{{[g]&w}}console", "{{[g]&w}}", "{{[g]&w}}"))
                    .addRow(List.of("quit", ":quit | <ctrl>+q", "exit the console"))
                    .addRow(List.of("clear", ":clear", "clear the console"))
                    .addRow(List.of("header", ":header [ |<name>]", "print random or named metatron header"))
                    .addRow(List.of("log", ":log [ |trace|debug|info|warn|error] [ |int]", "show or set log level (and target a output to a pane)"))
                    .addRow(List.of("word jump", "<shift>+<left/right>", "jump to start/end of a word"))
                    .addRow(List.of("word delete", "<ctrl>+<backspace>", "delete previous word"))
                    .addRow(List.of("prefix", ":prefix \"<text>\"", "prefix input with text"))
                    .addRow(List.of("postfix", ":postfix \"<text>\"", "postfix input with text"))
                    .addRow(List.of("back erase", "<alt>+k <char>", "erase buffer back to first occurrence of char"))
                    .addRow(List.of("format buffer", "<ctrl>+f", "pretty-print current buffer (legal syntax only)"))
                    /// ///////////////////////////////////////////////////////////////////////////////////////
                    .addRow(List.of("{{[g]&w}}panes", "{{[g]&w}}", "{{[g]&w}}"))
                    .addRow(List.of("split horizontal", ":split v | <ctrl>+<up>", "split current pane horizontally"))
                    .addRow(List.of("split vertical", ":split h | <ctrl>+<right>", "split current pane vertically"))
                    .addRow(List.of("focus", ":focus <id>", "focus pane by id"))
                    .addRow(List.of("panes", ":panes", "list all panes"))
                    .addRow(List.of("close", ":close", "close active pane"))
                    .addRow(List.of("next pane", "<ctrl>+w", "cycle to next pane"))
                    .addRow(List.of("prev pane", "<alt>+w", "cycle to previous pane"))
                    .addRow(List.of("shrink pane", "<alt>+<", "make active pane smaller"))
                    .addRow(List.of("grow pane", "<alt>+>", "make active pane larger"))
                    .style().headerDivider("{{[b]&w}}|").margin(0, 0, 0, 0).apply().format()).style().margin(0, 0, 0, 0).border(Border.simple.foreground("{{b}}")).apply().format();
            if (console.isSplitMode() && console.getActivePane() != null) {
                console.getActivePane().appendOutput(helpText);
            } else {
                Graphitty.out(Console.getTerminal().output(), helpText);
            }
            return noobj();
        }), MUTABLE);

        // ===== chat =====
        this.at("chat", instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(NOOBJ_TID), lst(), (lhs, inst) -> {
            final String chatLine = lhs.isStr() ? lhs.strValue() : "";
            Rec.wrap(Router.global().read("testy"), mModel.class).chat(chatLine);
            return noobj();
        }), MUTABLE);

        // ===== connect =====
        this.at("connect", instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(NOOBJ_TID), lst(), (lhs, inst) -> {
            Router.writeToSpace("abc", block_(MInst.instLambda((lhs2, inst2) -> {
                console.getReader().getBuffer().write(lhs2.asLst().at(1).strValue());
                return lhs2;
            })));
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
            if (lhs.isStr()) {
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
            } catch (Exception e) {
                throw MTronException.of(e);
            }
            return noobj();
        }), MUTABLE);

        // ===== less =====
        this.at("less", instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(NOOBJ_TID), lst(), (lhs, inst) -> {
            try {
                Commands.less(Console.getTerminal(), Console.getTerminal().input(), new PrintStream(Console.getTerminal().output()), System.err, Paths.get(""), new String[0]);
            } catch (Exception e) {
                throw MTronException.of(e);
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

        // ===== unsplit / close =====
        this.at("unsplit", instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(NOOBJ_TID), lst(), (lhs, inst) -> {
            console.closeActivePane();
            if (console.isSplitMode()) {
                console.renderPanes();
            } else {
                Graphitty.out(Console.getTerminal().output(), "{{XX}}");
            }
            return noobj();
        }), MUTABLE);
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
    }

}
