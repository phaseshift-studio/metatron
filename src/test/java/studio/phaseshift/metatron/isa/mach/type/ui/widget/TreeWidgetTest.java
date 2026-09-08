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
import studio.phaseshift.metatron.isa.m.type.Obj;
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
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_from_;
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
        // xref fixture: canonical leaves under local:xref/src/* and two alias leaves
        // (mainRef, helpRef) whose raw values are !@ auto pointers to those canonicals.
        Router.writeToSpace(f("local:xref/src"), str("src/"));
        Router.writeToSpace(f("local:xref/src/a_main"), str("// a"));
        Router.writeToSpace(f("local:xref/src/b_help"), str("// b"));
        Router.writeToSpace(f("local:xref/mainRef"), auto_from_(f("local:xref/src/a_main")).tryToInst());
        Router.writeToSpace(f("local:xref/helpRef"), auto_from_(f("local:xref/src/b_help")).tryToInst());
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

    /* ================================================================
     * xref (cross-reference) rendering
     * ================================================================ */

    private static TreeWidget xrefTree(final Integer xrefMax, final Obj xrefCode) {
        final Obj xrefRec;
        if (null == xrefCode)
            xrefRec = rec(uri(MAX), jnt(xrefMax));
        else
            xrefRec = rec(uri(MAX), jnt(xrefMax), uri(CODE), xrefCode);
        return new TreeWidget(mutableMap(
                uri(ROOT), uri("local:xref"),
                uri(MAX), jnt(4),
                uri(XREF), xrefRec), UI_TREE_TID, null);
    }

    @Test
    public void testXrefAbsentNoDecoration() {
        final TreeWidget tree = new TreeWidget(mutableMap(
                uri(ROOT), uri("local:xref"),
                uri(MAX), jnt(4)), UI_TREE_TID, null);
        final String expected = "xref\n"
                + "├─ helpRef\n"
                + "├─ mainRef\n"
                + "└─ src\n"
                + "    ├─ a_main\n"
                + "    └─ b_help";
        assertEquals(expected, tree.format());
    }

    /**
     * Two alias siblings (helpRef, mainRef) both point under local:xref/src, so
     * with xref.max=>2 the parent row folds them into one ──(2)──> rail and the
     * rendered canonical folder reports the inbound count.
     */
    @Test
    public void testXrefFoldAboveThreshold() {
        final TreeWidget tree = xrefTree(2, null);
        final String expected = "xref  ──(2)──> src\n"
                + "├─ helpRef\n"
                + "├─ mainRef\n"
                + "└─ src  ⇇2\n"
                + "    ├─ a_main\n"
                + "    └─ b_help";
        assertEquals(expected, tree.format());
    }

    /**
     * With xref.max=>3 the cluster of 2 is below the fold threshold: alias rows
     * keep per-leaf »target suffixes and each rendered canonical leaf reports ⇇1.
     */
    @Test
    public void testXrefLeafAnnotationsBelowThreshold() {
        final TreeWidget tree = xrefTree(3, null);
        final String expected = "xref\n"
                + "├─ helpRef  »b_help\n"
                + "├─ mainRef  »a_main\n"
                + "└─ src\n"
                + "    ├─ a_main  ⇇1\n"
                + "    └─ b_help  ⇇1";
        assertEquals(expected, tree.format());
    }

    /**
     * A user-supplied xref.code call replaces the default tail-segment label.
     */
    @Test
    public void testXrefCustomCodeLabel() {
        final TreeWidget tree = xrefTree(2, instLambda((lhs, inst) -> str("member-cluster")).tryToInst());
        final String expected = "xref  ──(2)──> member-cluster\n"
                + "├─ helpRef\n"
                + "├─ mainRef\n"
                + "└─ src  ⇇2\n"
                + "    ├─ a_main\n"
                + "    └─ b_help";
        assertEquals(expected, tree.format());
    }

    /**
     * Scratch-style index: an idx/Echo rec (canonical members under a deep code/...
     * path) whose field/method leaves are raw !@ auto pointers.  Rendering with
     * xref must fold the two sibling clusters into ──(2)──> rails on field/method.
     */
    @Test
    public void testXrefScratchStyleIndex() {
        Router.writeToSpace(f("local:rx/code/0/classes/Echo/0/members/0/PREFIX"), str("// prefix"));
        Router.writeToSpace(f("local:rx/code/0/classes/Echo/0/members/1/name"), str("// name"));
        Router.writeToSpace(f("local:rx/code/0/classes/Echo/0/members/4/speak"), str("// speak"));
        Router.writeToSpace(f("local:rx/code/0/classes/Echo/0/members/6/name"), str("// name"));
        final Obj idx = rec(uri("field"), rec(
                        uri("PREFIX"), auto_from_(f("local:rx/code/0/classes/Echo/0/members/0/PREFIX")).tryToInst(),
                        uri("name"), auto_from_(f("local:rx/code/0/classes/Echo/0/members/1/name")).tryToInst()),
                uri("method"), rec(
                        uri("speak"), auto_from_(f("local:rx/code/0/classes/Echo/0/members/4/speak")).tryToInst(),
                        uri("name"), auto_from_(f("local:rx/code/0/classes/Echo/0/members/6/name")).tryToInst()));
        Router.writeToSpace(f("local:rx/idx/Echo"), idx);
        final TreeWidget tree = new TreeWidget(mutableMap(
                uri(ROOT), uri("local:rx/idx/Echo"),
                uri(MAX), jnt(5),
                uri(XREF), rec(uri(MAX), jnt(2))), UI_TREE_TID, null);
        final String out = tree.format();
        LOG.none("\n" + out + "\n");
        assertTrue(out.contains("field  ──(2)──> members"), "field row should fold its aliases, got:\n" + out);
        assertTrue(out.contains("method  ──(2)──> members"), "method row should fold its aliases, got:\n" + out);
        assertFalse(out.contains("»"), "folded aliases should not carry leaf markers, got:\n" + out);
    }
}
