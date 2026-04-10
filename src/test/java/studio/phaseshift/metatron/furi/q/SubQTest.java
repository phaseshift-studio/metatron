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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.TestCategory;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.util.CommonUtil;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.furi.q.QCollection.SUBQ_TID;
import static studio.phaseshift.metatron.furi.q.QCollection.SUBSCRIPTION_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface SubQTest {


    Space getSpace();

    String make(final String expression);

    @TestCategory.Crud
    @TestCategory.Concurrent
    @ParameterizedTest(name = "[{index}] String: {0}")
    @Disabled
    @CsvSource(value = {
            "$$/xyz?subq     ->(||(>>1.to($$/abc)))             % $$/xyz -> 32            % *$$/abc.eq(32)",
            "$$/xyz?subq     ->(||(>>1.plus(10).to($$/abc)))    % $$/xyz -> 12            % *$$/abc.eq(22)",
            "$$/xyz/a?subq   ->(||(>>0.to($$/abc)))             % $$/xyz/a -> 12          % *$$/abc.eq($$/xyz/a)",
            "$$/xyz/#?subq   ->(||(>>0.to($$/abc)))             % $$/xyz/a -> 12          % *$$/abc.eq($$/xyz/a)",
            "$$/xyz/+/+?subq ->(||(>>0.to($$/abc)))             % $$/xyz/a -> 12          % *$$/abc.else(true)",
            "$$/xyz/+/+?subq ->(||(>>1.to($$/abc)))             % $$/xyz/a/b -> 12        % *$$/abc.eq(12)"
    }, delimiter = '%')
    default void testSubQ(String subscription, String writing, String expecting) {
        getSpace().qs().lstValue().stream().filter(x -> x.tid().equals(SUBQ_TID)).findAny().orElseThrow();
        final Obj sub = mParser.eval(make(subscription));
        assertEquals(SUBSCRIPTION_TID, sub.tid());
        final Obj writeObj = mParser.eval(make(writing));
        assertNotEquals(sub, writeObj);
        CommonUtil.sleepThread(1500);
        assertTrue(mParser.eval(make(expecting)).boolValue());
    }
}
