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

package studio.phaseshift.metatron.isa.m.type.impl;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.type.Obj;

import static org.junit.Assert.assertEquals;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.LazyObjs.lazyObjs;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class LazyObjsTest extends AbstractMetatronTest {

    @ParameterizedTest
    @CsvSource(value = {
            "{1,1,1}                                                %     int{3}          % 3",
            "{1,2,3,4}                                              %     int{4}         % 4",
            "{1,2,3,4,4}                                            %     int{5}         % 5",
            "{1,2,3,4,4}                                            %     int{5}         % 5",
            "{int{0}::1,int::2,int{-1}::2}                          %     int{0}         % 0",
            "{int{0}::1,int::2,int{2}::2}                           %     int{3}         % 3",
            "{1,2,int{10}::3,4,4}                                   %     int{14}        % 14",
            "{1,'a',int{10}::3,4,4}                                 %     #{14}         % 14",
    }, delimiter = '%')
    public void testLazyObjs(final String objs, final String tid, final String coefficient) {
        final Obj o = mParser.parse(objs);
        final Obj lo = lazyObjs(o.clone().iterator());
        final fURI t = f(tid).big();
        assertEquals(t, o.tid());
        LOG.warn("lazyObjs tid: %s [expected: %s]", lo.tid(), t);
        assertEquals(o, lo);
        // assertEquals(o.c(), lo.c()); //TODO: non-reversible
        /// ////////////////////////////////////////////////////////////////////////////////////////////
        final Obj lo2 = lazyObjs(o.clone().iterator());
        final cInt c = cInt.of(coefficient);
        assertEquals(c, lo2.c());
        assertEquals(c, lo2.c());
        /// ////////////////////////////////////////////////////////////////////////////////////////////
        //assertEquals(o.c(), lo.c());
    }
}
