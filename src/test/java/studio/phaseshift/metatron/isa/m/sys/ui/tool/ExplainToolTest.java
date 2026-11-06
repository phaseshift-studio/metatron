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

package studio.phaseshift.metatron.isa.m.sys.ui.tool;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Inst;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer.parse;

/**
 * The classification behind {@link ExplainTool}'s recursive drill-down.
 * <p>
 * A code value — a multi-instruction {@code Code} or a lone {@code Inst} — is
 * drillable and must open a nested ExplainLevel; a non-empty {@code rec}/{@code lst}
 * is drillable as a selection frame; an empty poly or a scalar (an empty code is a
 * noobj, hence scalar) is not.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ExplainToolTest extends AbstractMetatronTest {

    @Test
    public void testCodeAndLoneInstAreDrillable() {
        final Code code = parse("[1,2,3].map(x=>x+1)");
        final Inst loneInst = code.codeValue().getFirst();
        assertEquals(ExplainTool.DrillKind.CODE, ExplainTool.drillKind(code));
        assertEquals(ExplainTool.DrillKind.CODE, ExplainTool.drillKind(loneInst),
                "a single-instruction argument is still code (isObjCall, not isCode)");
    }

    @Test
    public void testPolyIsDrillableAndEmptyPolyIsNot() {
        assertEquals(ExplainTool.DrillKind.POLY, ExplainTool.drillKind(lst(jnt(1), jnt(2))));
        assertEquals(ExplainTool.DrillKind.POLY, ExplainTool.drillKind(rec(uri("a"), jnt(1))));
        assertEquals(ExplainTool.DrillKind.EMPTY_POLY, ExplainTool.drillKind(lst()));
        assertEquals(ExplainTool.DrillKind.EMPTY_POLY, ExplainTool.drillKind(rec()));
    }

    @Test
    public void testScalarsAreNotDrillable() {
        assertEquals(ExplainTool.DrillKind.SCALAR, ExplainTool.drillKind(jnt(3)));
        assertEquals(ExplainTool.DrillKind.SCALAR, ExplainTool.drillKind(str("hello")));
        assertEquals(ExplainTool.DrillKind.SCALAR, ExplainTool.drillKind(bool(true)));
        assertEquals(ExplainTool.DrillKind.SCALAR, ExplainTool.drillKind(noobj()));
    }

    @Test
    public void testArgsCellKeepsTokensFixedAndSlides() {
        final List<String> tokens = List.of("a", "b", "c", "d", "e");

        // Full list fits: the selector replaces the slot (sel 0) or a comma-space (sel 1),
        // so every token stays in the same column — no wobble.
        assertEquals(">a, b, c, d, e" + " ".repeat(10), ExplainTool.argsCell(tokens, 0, 24, "", ">"));
        assertEquals(" a,>b, c, d, e" + " ".repeat(10), ExplainTool.argsCell(tokens, 1, 24, "", ">"));

        // A narrow cell clips both sides and keeps the selector on the selected token.
        assertEquals(">…c, d… ", ExplainTool.argsCell(tokens, 2, 8, "", ">"));
        assertEquals(" …d,>e  ", ExplainTool.argsCell(tokens, 4, 8, "", ">"));
    }

    @Test
    public void testArgsCellSpawnUnderlinesSelectedArg() {
        final List<String> tokens = List.of("a", "b", "c");
        // the drilled arg is wrapped in underline markup; the others stay plain
        assertEquals(" {{_}}a{{/_}}, b, c" + " ".repeat(16), ExplainTool.argsCellSpawn(tokens, 0, 24));
        assertEquals(" a, {{_}}b{{/_}}, c" + " ".repeat(16), ExplainTool.argsCellSpawn(tokens, 1, 24));
    }

    @Test
    public void testArgsSummaryIsFixedWidth() {
        final String summary = ProfileTool.argsSummary(lst(jnt(1), jnt(2)), 24);
        assertEquals(24, summary.length());
        assertEquals(" 1, 2", summary.substring(0, 5));   // leading pointer slot + "1, 2"
    }

    @Test
    public void testRenderPolyPlacesSelectorOnBracketAndComma() {
        // element 0 → the selector replaces the opening bracket; element 1 → it replaces the comma
        assertEquals(">1,2]", ExplainTool.renderPoly(lst(jnt(1), jnt(2)), List.of(0), "", ">"));
        assertEquals("[1>2]", ExplainTool.renderPoly(lst(jnt(1), jnt(2)), List.of(1), "", ">"));
    }

    @Test
    public void testRelArgIsRenderedAsKeyValue() {
        // a rel is a single key=>value pair, not a rec — render it without recValue()
        final List<String> tokens = ProfileTool.argTokens(rel(uri("//0"), jnt(1)));
        assertEquals(1, tokens.size());
        assertEquals("//0=>", tokens.get(0).substring(0, 5));
    }

    @Test
    public void testArgsWidthScalesWithTerminal() {
        assertEquals(4, ProfileTool.argsWidth(80, 0, 3));     // tiny args → floor at the min
        assertEquals(11, ProfileTool.argsWidth(100, 0, 11));  // content-sized: 11 fits, no clip
        assertEquals(35, ProfileTool.argsWidth(100, 0, 100)); // long args: capped at 100 - 65
        assertEquals(51, ProfileTool.argsWidth(120, 4, 100)); // nested long: 120 - 4 - 65
    }
}
