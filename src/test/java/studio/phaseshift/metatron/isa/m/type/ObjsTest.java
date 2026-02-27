/*
 * Metatron: A Distributed Computing Language and Virtual Machine
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

package studio.phaseshift.metatron.isa.m.type;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.AbstractObjTest;
import studio.phaseshift.metatron.util.IteratorUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;

public class ObjsTest extends AbstractObjTest {

    @ParameterizedTest
    @CsvSource(value = {
            "{1,1,1,1}                      |int{0}       |{,}                            |{1,1,1,1}",
            "int{4}::1                      |int{4}       |int{4}::1                      |{,}",
            "{int{2}::1,int{2}::1}          |int{4}       |int{4}::1                      |{,}",
            "{int{2}::1,int{2}::1}          |int{0}       |{,}                            |{int{4}::1}",
            "{int{2}::1,int{2}::2}          |int{0}       |{,}                            |{int{2}::1,int{2}::2}",
            "{int{2}::1,int{2}::2}          |int{4}       |{int{2}::1,int{2}::2}          |{,}",
            "{1,2,3,4}                      |int{4}       |{1,2,3,4}                      |{,}",
            "{1,2,3,4,5,5,5,5,5}            |int{4}       |{1,2,3,4}                      |int{5}::5",
            "{1,2,3,'four',5,5,5,5,5}       |obj{4}       |{1,2,3,'four'}                 |int{5}::5",
    }, delimiter = '|')
    public void testTake(final String current, final String remove, final String retrieved, final String remaining) {
        super.testTake(current, remove, retrieved, remaining);
    }

    @Test
    public void testObjsGrowth() {
        Obj objs = noobj();
        for (int i = 0; i < 10000; i++) {
            objs = objs.append(jnt(i));
        }
        assertEquals(10000, objs.uniqueC().max().intValue());
        assertEquals(10000, objs.c().min().intValue());
        assertEquals(10000, objs.c().max().intValue());
        assertEquals(10000,objs.stream().count());
        assertEquals(10000,IteratorUtil.count(objs.<Iterable<Obj>>jvm()));
        /// ///////////////////////////////////////////////////////////////
        objs = noobj();
        for (int i = 0; i < 10000; i++) {
            objs = objs.append(jnt(1));
        }
        assertEquals(1, objs.uniqueC().max().intValue());
        assertEquals(10000, objs.c().min().intValue());
        assertEquals(10000, objs.c().max().intValue());
        assertEquals(1,objs.stream().count());
    }

    @ParameterizedTest
    @CsvSource(value = {
            "{1,2,3}.count()                                                     % 3",
            "{1}.count()                                                         % 1",
            "{}.count()                                                          % 0",
            "{1,1,1}.count()                                                     % 3",
            "{1,2,1,2}.count()                                                   % 4",
    }, delimiter = '%')
    public void testCount(final String code, final String expected) {
        AbstractMetatronTest.testCode(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "{1,2,3}.merge()                                                     % {1,2,3}",
            "{a,b,c}.merge()                                                     % {a,b,c}",
            "{}.merge()                                                          % {}",
    }, delimiter = '%')
    public void testMerge(final String code, final String expected) {
        AbstractMetatronTest.testCode(LOG, code, expected);
    }
}
