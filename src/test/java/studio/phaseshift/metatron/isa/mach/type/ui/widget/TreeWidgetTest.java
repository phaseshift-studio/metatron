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

import org.jline.utils.AttributedString;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Highlighter;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.map_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.ui.uiInstSet.UI_TREE_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class TreeWidgetTest extends AbstractMetatronTest {

    private static final GraphittyLogger LOG = Graphitty.log(TreeWidgetTest.class);

    @BeforeAll
    static void setUp() {
        AbstractMetatronTest.begin();
        final memSpace space = memSpace.of(rec(uri(PATTERN), uri("local:#")), f("/sys/space/local"));
        Router.global().addSpace(space);
        // Build a deterministic tree in memory
        Router.writeToSpace(f("local:docs"), str("docs/"));
        Router.writeToSpace(f("local:docs/index.adoc"), str("= docs"));
        Router.writeToSpace(f("local:projects"), str("projects/"));
        Router.writeToSpace(f("local:projects/metatron"), str("metatron/"));
        Router.writeToSpace(f("local:projects/metatron/src"), str("src/"));
        Router.writeToSpace(f("local:projects/metatron/src/main"), str("main/"));
        Router.writeToSpace(f("local:projects/metatron/src/main/Main.java"), str("// main"));
        Router.writeToSpace(f("local:projects/metatron/src/main/Config.java"), str("// config"));
        Router.writeToSpace(f("local:projects/metatron/src/test"), str("test/"));
        Router.writeToSpace(f("local:projects/metatron/src/test/MainTest.java"), str("// test"));
        Router.writeToSpace(f("local:projects/metatron/README.md"), str("# metatron"));
        Router.writeToSpace(f("local:projects/other"), str("other/"));
        Router.writeToSpace(f("local:projects/other/notes.txt"), str("notes"));
        // A pure folder chain (no file children until the leaf folder) — for flatten tests.
        Router.writeToSpace(f("local:chain"), str("chain/"));
        Router.writeToSpace(f("local:chain/a"), str("a/"));
        Router.writeToSpace(f("local:chain/a/b"), str("b/"));
        Router.writeToSpace(f("local:chain/a/b/c"), str("c/"));
        Router.writeToSpace(f("local:chain/a/b/c/File.java"), str("// file"));
        Router.writeToSpace(f("local:chain/a/b/c/Other.java"), str("// other"));
    }

    @Test
    public void testTreeWidgetFormat() {
        final TreeWidget tree = new TreeWidget(mutableMap(
                uri(ROOT), uri("local:"),
                uri(MAX), jnt(5),
                uri(CODE), instLambda((lhs, inst) -> jnt(lhs.as(STR_TYPE).strValue().length())).tryToInst()), UI_TREE_TID, null);
        LOG.none("\n" + tree.format() + "\n");

    }

    @Test
    public void testTreeWidgetDefaultNoFlatten() {
        final TreeWidget tree = new TreeWidget(mutableMap(
                uri(ROOT), uri("local:chain"),
                uri(MAX), jnt(5),
                uri(CODE), instLambda((lhs, inst) -> noobj()).tryToInst()), UI_TREE_TID, null);
        final String expected = "chain\n"
                + "└─ a\n"
                + "    └─ b\n"
                + "        └─ c\n"
                + "            ├─ File.java\n"
                + "            └─ Other.java";
        assertEquals(expected, tree.format());
    }

    @Test
    public void testTreeWidgetFlatten() {
        final TreeWidget tree = new TreeWidget(mutableMap(
                uri(ROOT), uri("local:chain"),
                uri(MAX), jnt(5),
                uri(CODE), instLambda((lhs, inst) -> noobj()).tryToInst(),
                uri("flatten"), bool(true)), UI_TREE_TID, null);
        final String expected = "chain\n"
                + "└─ a/b/c\n"
                + "    ├─ File.java\n"
                + "    └─ Other.java";
        assertEquals(expected, tree.format());
    }

    /**
     * Diagnostic: find where box-drawing glyphs (├ ─ │) get downgraded to ASCII
     * (+ - |) on the `.as(str::T)` echo path.  `.display()` writes format()
     * raw; the REPL echo of an as-str passes through ObjConsoleSerializer and
     * then Highlighter (JLine SyntaxHighlighter).
     */
    /**
     * JLine's {@code AttributedString.toAnsi()} downgrades box-drawing glyphs
     * (├ ─ │) to ASCII (+ - |) when the terminal lacks alternate-charset
     * support.  Highlighter must not mangle pre-formatted tree content.
     */
    @Test
    public void testHighlighterPreservesBoxDrawing() {
        final String tree = "chain\n└─ a/b/c\n    ├─ File.java\n    └─ Other.java";
        final String highlighted = Highlighter.format(str(tree));
        assertTrue(highlighted.contains("├"), "Highlighter must preserve ├ (was: " + highlighted + ")");
        assertTrue(highlighted.contains("└─"));
    }

    /**
     * Sanity: the raw JLine toAnsi() conversion really is the mangler, so the
     * fix above is load-bearing rather than a no-op.
     */
    @Test
    public void testJLineToAnsiDowngradesBoxDrawing() {
        assertFalse(new AttributedString("├─ │ └").toAnsi().contains("├"));
        assertTrue(new AttributedString("├─ │ └").toAnsi().contains("+"));
    }
}
