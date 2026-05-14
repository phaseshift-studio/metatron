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

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.type.ui.Border;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Console;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.Panel;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.Table;

import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.m.mInstSet.M_ISA_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.NOOBJ_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_from_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.type.ui.console.Console.CONSOLE_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ColonMenu extends MRec {

    public static final fURI COLON_MENU_TID = CONSOLE_TID.extend("colon_menu");

    private final Console console;

    public ColonMenu(final Console console) {
        super(Map.of(uri("console"), auto_from_(console.vid()).tryToInst()), COLON_MENU_TID, console.vid().extend("colon_menu"));
        this.console = console;
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
                Graphitty.out(console.getTerminal().output(), helpText);
            }
            return noobj();
        }), MUTABLE);
    }


}