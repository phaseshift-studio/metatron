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

package studio.phaseshift.metatron.furi.q;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.TestData;
import studio.phaseshift.metatron.furi.QProc;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static studio.phaseshift.metatron.furi.q.QCollection.LINEQ_TID;

public interface LineQTest {

    Space getSpace();

    String make(final String expression);
    
    @ParameterizedTest
    @TestData({
            "$$/xyz -> \"\"\"this is\na long\nmulti-line\nstring\"\"\""
    })
    @CsvSource(value = {
            "*$$/xyz               % '''this is\na long\nmulti-line\nstring'''",
            "*$$/xyz?lineq=0       % '''this is'''",
            "*$$/xyz?lineq=0-1     % '''this is\na long'''",
            "*$$/xyz?lineq=0-2     % '''this is\na long\nmulti-line'''",
            "*$$/xyz?lineq=0-3     % '''this is\na long\nmulti-line\nstring'''",
            "*$$/xyz?lineq=0-4     % '''this is\na long\nmulti-line\nstring'''",
            "*$$/xyz?lineq=0-10    % '''this is\na long\nmulti-line\nstring'''",
    }, delimiter = '%')
    default void testLineQRead(String code, String expected) {
        final Space space = this.getSpace();
        if (getSpace().qs().lstValue().stream().noneMatch(x -> ((QProc) x).pattern().equals(LINEQ_TID))) {
            space.logger().warn("manually adding lineq to %s", space.vidOrTid());
            space.addQ(QCollection.lineq());
        }
        final Obj resultObj = ObjmtronSerializer.parse(make(code)).apply();
        final Obj expectedObj = ObjmtronSerializer.parse(make(expected)).apply();
        assertEquals(expectedObj, resultObj);
    }

}
