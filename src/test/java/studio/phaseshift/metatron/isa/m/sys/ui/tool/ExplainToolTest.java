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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
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
}
