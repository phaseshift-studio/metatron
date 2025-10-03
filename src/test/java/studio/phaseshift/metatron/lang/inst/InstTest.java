/*
 * Metatron: A Distributed Computing Language and Virtual Machine
 * Copyright (C) 2025- PhaseShift Studio, LLC
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
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

package studio.phaseshift.metatron.lang.inst;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.Inst;
import studio.phaseshift.metatron.lang.obj.mtron.MInst;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class InstTest {

    @BeforeAll
    public static void setup() {
        BootLoader.load();
    }

    @ParameterizedTest
    @CsvSource(value = {
            // furi | tid | dom | range
            "/mtron/plus?dom=/mtron/int&rng=/mtron/int|/mtron/plus|/mtron/int|/mtron/int",
            "/mtron/mult/a?dom=+&rng=+|/mtron/mult/a|+|+"},
            delimiter = '|')
    public void testDomRng(final String f, final String op, final String dom, final String rng) {
        final fURI furi = fURI.of(f);
        final Inst inst = MInst.instA(furi);
        assertEquals(op, inst.tid().path());
        assertEquals(fURI.of(dom), inst.dom().tid());
        assertEquals(fURI.of(rng), inst.rng().tid());
    }

    @Test
    public void testInstObj() {
        // assertEquals(PLUS_TID.query(DOM,INT_TID).query(RNG,INT_TID),  new MInstSet(fURI.of("/mnt/mtron")).resolve(MInt.of(2),MInst.instA(fURI.of("plus"))).tid());
        // assertEquals(START_TID.query(DOM,NOOBJ_TID).query(RNG, ANY),  new MInstSet().resolve(NoObj.single(),MInst.instA(fURI.of("start"))).tid());
        //System.out.println(i);
    }
}
