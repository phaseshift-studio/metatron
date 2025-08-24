/*
 *   Metatron: A Distributed Virtual Machine
 *   Copyright (c) 2024 PhaseShift Studio, LLC
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.lang.parse;

import org.junit.jupiter.api.Test;
import org.parboiled.parserunners.BasicParseRunner;
import org.parboiled.parserunners.RecoveringParseRunner;
import studio.phaseshift.metatron.lang.obj.BObj;
import studio.phaseshift.metatron.lang.obj.SObj;

import static org.junit.jupiter.api.Assertions.*;

public class MParserTest {

    @Test
    public void testIntParse() {
        assertTrue(new BasicParseRunner<BObj.Obj>(MParser.generate().Start()).run("1234").matched);
        assertEquals(SObj.Int.of(1234), new BasicParseRunner<>(MParser.generate().Start()).run("1234").resultValue);
        assertEquals(SObj.NoObj.of(), new BasicParseRunner<BObj.Obj>(MParser.generate().Int()).run("abc").resultValue);
    }

    @Test
    public void testStrParse() {
        assertTrue(new BasicParseRunner<BObj.Obj>(MParser.generate().Start()).run("'abc'").matched);
        assertEquals(SObj.Str.of("abc"), new BasicParseRunner<BObj.Obj>(MParser.generate().Start()).run("'abc'").resultValue);
        assertFalse(new BasicParseRunner<BObj.Obj>(MParser.generate().Start()).run("\"abc\"").matched);
    }
}
