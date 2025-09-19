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

package studio.phaseshift.metatron.space;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.NoObj;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.lang.obj.mtron.MLst;
import studio.phaseshift.metatron.lang.parse.ObjParser;
import studio.phaseshift.metatron.ui.Graphitty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static studio.phaseshift.metatron.lang.fURI.f;
import static studio.phaseshift.metatron.lang.obj.mtron.MLst.lst;
import static studio.phaseshift.metatron.lang.obj.mtron.MUri.uri;

public abstract class SpaceTest {

    public Space space;

    @BeforeEach
    public void beforeEach() {
        assertNotNull(this.space);
        this.space.write("#", NoObj.single());
        //BootLoader.logger().put(uri("level/INFO"), lst(uri("#")));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "1.to(a)                     % 1.from(a)                  % 1", // todo: 1.from is a hack
    }, delimiter = '%')
    void testMonoReadWrite(final String writeExpression, final String readExpression, final String resultExpression) {
        final Obj writeObj = ObjParser.parse(writeExpression).apply();
        final Obj readObj = ObjParser.parse(readExpression).apply();
        final Obj resultObj = ObjParser.parse(resultExpression);
        Graphitty.log(this.space).debug("write [%s => %s] | read [%s => %s] | result [%s => %s]",
                writeExpression, writeObj,
                readExpression, readObj,
                resultExpression, resultObj);
        assertEquals(resultObj, readObj);

    }

  /*  public void testMonoSpace() {
        space.
    }
*/
}
