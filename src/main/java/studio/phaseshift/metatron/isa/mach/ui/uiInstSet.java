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

package studio.phaseshift.metatron.isa.mach.ui;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.dcmnt.schema.storage.ObjBSONSerializer;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.io.type.ObjByteBufferSerializer;
import studio.phaseshift.metatron.isa.mach.io.type.ObjSimpleJSONSerializer;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.ui.Border;
import studio.phaseshift.metatron.isa.mach.type.ui.Stylable;
import studio.phaseshift.metatron.isa.mach.type.ui.Widget;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Console;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Editor;
import studio.phaseshift.metatron.isa.mach.type.ui.console.menu.ColonMenu;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.*;
import studio.phaseshift.metatron.util.MTronException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.type.Lst.LST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_ISA_TID;
import static studio.phaseshift.metatron.isa.mach.type.thread.VirtualThread.virtual;

import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@InstSet.JREService(vid = "/m/mach/ui")
public class uiInstSet extends AbstractInstSet {

    public static final fURI UI_ISA_TID = MACH_ISA_TID.extend("ui");
    public static final fURI UI_INST_TID = UI_ISA_TID.extend("inst");

    public static final fURI UI_WIDGET_TID = UI_ISA_TID.extend("widget");
    public static Type UI_WIDGET_TYPE;
    public static final fURI UI_STYLE_TID = UI_ISA_TID.extend("style");
    public static Type UI_STYLE_TYPE;
    public static final fURI UI_ACCORDIAN_TID = UI_ISA_TID.extend("accordian");
    public static Type UI_ACCORDIAN_TYPE;
    public static final fURI UI_TABLE_TID = UI_WIDGET_TID.extend("table");
    public static Type UI_TABLE_TYPE;
    public static final fURI UI_SELECTOR_TID = UI_WIDGET_TID.extend("selector");
    public static Type UI_SELECTOR_TYPE;
    public static final fURI UI_PANEL_TID = UI_WIDGET_TID.extend("panel");
    public static Type UI_PANEL_TYPE;
    public static final fURI UI_CONSOLE_TID = UI_ISA_TID.extend("console");
    public static Type UI_CONSOLE_TYPE;


    public uiInstSet() {
        super(mutableMap(uri(PATTERN), uri(UI_ISA_TID.extend(ALL))), INSTSET_TID, UI_ISA_TID);
    }

    @Override
    public void setup() {
        this.jvm().putAll(new LinkedHashMap<>(Map.of(
                uri(TYPE), lst(
                        docWrap(UI_CONSOLE_TYPE = Type.Builder.build()
                                .tid(REC_TID)
                                .vid(UI_CONSOLE_TID)
                                .isaPredicate(rec())
                                .constructor(instC(INST_CTOR_TID.dom(ALL.maybe()).rng(UI_CONSOLE_TID), lst(T(REC_TID)), (lhs, inst) -> {
                                    final Console console = new Console(inst.arg(0).as(), inst.arg(0).vid());
                                    new ColonMenu(console).attach(rec());
                                    docWrap(virtual(instLambda((_lhs, inst2) -> {
                                        console.run();
                                        return noobj();
                                    })), "console repl").applyAsync();
                                    return docWrap(console, "a user terminal repl", ":help");
                                })).create(), "a terminal user interface"),
                        docWrap(UI_STYLE_TYPE = Type.Builder.build()
                                .tid(REC_TID)
                                .vid(UI_STYLE_TID)
                                .isaPredicate(rec(
                                        uri("border").maybe().asUri(), URI_TYPE,
                                        uri("background").maybe(), STR_TYPE,
                                        uri("foreground").maybe(), STR_TYPE,
                                        uri("divider").maybe(), STR_TYPE,
                                        uri("headerDivider").maybe(), STR_TYPE,
                                        uri("pointer").maybe(), STR_TYPE,
                                        uri("leftMargin").maybe(), STR_TYPE,
                                        uri("rightMargin").maybe(), STR_TYPE,
                                        uri("topMargin").maybe(), STR_TYPE,
                                        uri("bottomMargin").maybe(), STR_TYPE))
                                .constructor(instC(INST_CTOR_TID.dom(ALL.maybe()).rng(UI_STYLE_TID), lst(T(REC_TID)), (lhs, inst) -> Stylable.Style.from(inst.arg(0).asRec())))
                                .create(), "maybe an obj", "a style obj", Map.of(
                                uri("border").maybe().asUri(), "the border style of the widget (e.g. border::none, border::simple, etc.)",
                                uri("background").maybe(), "the background color of the widget",
                                uri("foreground").maybe(), "the foreground color of the widget",
                                uri("divider").maybe(), "the divider character used in the widget",
                                uri("headerDivider").maybe(), "the header divider character used in the widget",
                                uri("pointer").maybe(), "the pointer character used in the widget",
                                uri("leftMargin").maybe(), "the left margin of the widget",
                                uri("rightMargin").maybe(), "the right margin of the widget",
                                uri("topMargin").maybe(), "the top margin of the widget",
                                uri("bottomMargin").maybe(), "the bottom margin of the widget"), "a UI style specification"),
                        docWrap(UI_WIDGET_TYPE = Type.Builder.build()
                                .tid(REC_TID)
                                .vid(UI_WIDGET_TID)
                                .isaPredicate(rec(uri("style").maybe().asUri(), UI_STYLE_TYPE))
                                .create(), "rec", null, Map.of(uri("style"), "the style specification for the widget"), "the base UI widget type"),
                        docWrap(UI_ACCORDIAN_TYPE = Type.Builder.build()
                                .tid(UI_WIDGET_TID)
                                .vid(UI_ACCORDIAN_TID)
                                .isaPredicate(rec(
                                        uri(TITLE).maybe().asUri(), STR_TYPE,
                                        uri(BODY).maybe(), T(STR_TID.maybeSome())))
                                .constructor(instC(INST_CTOR_TID.dom(ALL.maybe()).rng(UI_ACCORDIAN_TID),
                                        lst(T(REC_TID)), (lhs, inst) -> {
                                            final AccordionWidget a = new AccordionWidget(inst.arg(0).asRec().jvm(), UI_ACCORDIAN_TID, inst.arg(0).vid());
                                            //Graphitty.out(Console.getTerminal().output(), a.format() + "\n");
                                            return a;
                                        })).create(), "maybe an obj", "an accordian obj", Map.of(
                                uri(TITLE).maybe().asUri(), "the title of the accordion",
                                uri(BODY).maybe(), "the body content of the accordion"), "an expandable/collapsible accordion widget"),
                        docWrap(UI_TABLE_TYPE = Type.Builder.build()
                                .tid(UI_WIDGET_TID)
                                .vid(UI_TABLE_TID)
                                .isaPredicate(rec(
                                        uri("header").maybe().asUri(), LST_TYPE,
                                        uri("row").maybe(), T(LST_TID.maybeSome())))
                                .constructor(instC(INST_CTOR_TID.dom(ALL.maybe()).rng(UI_TABLE_TID),
                                        lst(T(REC_TID)), (lhs, inst) -> {
                                            final TableWidget t = new TableWidget(inst.arg(0).asRec().jvm(), UI_TABLE_TID, inst.arg(0).vid());
                                            //Graphitty.out(Console.getTerminal().output(), t.format() + "\n");
                                            return t;
                                        })).create(), "rec", "table", Map.of(), "a tabular data widget"),
                        docWrap(UI_SELECTOR_TYPE = Type.Builder.build()
                                .tid(UI_WIDGET_TID)
                                .vid(UI_SELECTOR_TID)
                                .isaPredicate(rec())
                                .constructor(instC(INST_CTOR_TID.dom(ALL.maybe()).rng(UI_SELECTOR_TID),
                                        lst(T(REC_TID)), (lhs, inst) -> {
                                            final Selector s = new Selector(inst.arg(0).asRec().jvm(), UI_SELECTOR_TID, inst.arg(0).vid());
                                            return s;
                                        })).create(), "rec", "selector", Map.of(), "an interactive item selector widget"),
                        docWrap(UI_PANEL_TYPE = Type.Builder.build()
                                .tid(UI_WIDGET_TID)
                                .vid(UI_PANEL_TID)
                                .isaPredicate(rec(
                                        uri(TITLE).maybe().asUri(), STR_TYPE,
                                        uri(BODY).maybe(), T(STR_TID.maybeSome())))
                                .constructor(instC(INST_CTOR_TID.dom(ALL.maybe()).rng(UI_PANEL_TID),
                                        lst(T(REC_TID)), (lhs, inst) -> new PanelWidget(inst.arg(0).asRec().jvm(), UI_PANEL_TID, inst.arg(0).vid())))
                                .create(), "rec", "panel", Map.of(
                                uri(TITLE), "the title of the panel",
                                uri(BODY), "the body content of the panel"), "a simple bordered UI panel widget")),
                uri(INST), lst(
                        docWrap(instC(UI_INST_TID.extend("display").dom(UI_WIDGET_TID).rng(UI_WIDGET_TID), lst(), (lhs, inst) -> {
                            final Widget widget = ((Widget) lhs);
                            if (widget instanceof Selector)
                                Utilities.runCursorLessWidget(widget, true);
                            else
                                Graphitty.out(Console.getTerminal().output(), widget.format() + "\n");
                            return lhs;
                        }), "display the widget on the terminal"),
                        docWrap(instC(UI_INST_TID.extend("nano").dom(ALL.maybe()).rng(ALL.maybe()), lst(), (lhs, inst) -> {
                            try {
                                final File file = Editor.createObjFile(lhs);
                                Editor.of(Console.LOCAL_INSTANCE, file);
                                return ObjmtronSerializer.parse(Files.readString(file.toPath()).trim());
                            } catch (final IOException e) {
                                throw MTronException.of(e);
                            }
                        }), "open a nano-like editor for the obj"),
                        docWrap(instC(UI_INST_TID.extend("less").dom(STR_TID).rng(NOOBJ_TID.zero()), lst(isa_(T(INT_TID)).else_(jnt(10))), (lhs, inst) -> {
                            Scanner scanner = new Scanner(System.in);
                            final int pageSize = inst.arg(0).orElse(jnt(100)).intValue().intValue();
                            final AtomicInteger page = new AtomicInteger(0);
                            final AtomicInteger counter = new AtomicInteger(0);
                            Arrays.stream(lhs.strValue().split("\n")).forEach(line -> {
                                if (counter.getAndIncrement() < pageSize) {
                                    LOG.none(line + "\n");
                                } else {
                                    LOG.none("{{g}}<{{m}}page %s{{g}}>{{X}}\n", page.incrementAndGet());
                                    scanner.nextLine();
                                    LOG.none("{{^2&-X-&v1}}");
                                    counter.set(0);
                                }
                            });
                            return noobj();
                        }), "an str to page", "noobj terminal", Map.of(jnt(0), "number of lines per page"), "a \\[ f(\\tt{x}) \\rightarrow \\emptyset \\] terminal page through the lines of an str"))
        )));
        super.setup();
    }
}
