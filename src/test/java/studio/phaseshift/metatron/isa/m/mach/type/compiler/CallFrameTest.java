/*
 * metatron: a distributed virtual machine and language
 *  Copyright (C) 2025- PhaseShift Studio, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.isa.m.mach.type.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE CALL FRAME (Marko, 2026-11-22): a call no signature matches is tried as its own application written as data.
 * {@code f(lhs=>x, args=>a)} is {@code x.f(a)} for every instruction (Selector.Helper#tryRawRecArgs, the specificity
 * selector's last resort), the args an ordered call's lst or a keyed call's rec. The frame runs {@code start(x).f(a)},
 * so its lhs replaces whatever the chain hands in, and it runs per monad, a constant function of the stream.
 */
public class CallFrameTest extends AbstractMetatronTest {

    /**
     * Each row: the frame % what it answers.
     */
    @ParameterizedTest
    @CsvSource(value = {
            // an ordered instruction: its args are the frame's lst
            "plus(lhs=>3,args=>[1])                                          % 4",
            "plus(lhs=>3,args=>[10])                                         % 13",
            "mult(lhs=>{1,2,3},args=>[2])                                    % {2,4,6}",
            // a keyed instruction: its args are the frame's rec
            "repeat(lhs=>{1,2,3},args=>[code=>plus(1),until=>loop()?>3])    % {5,6,7}",
            // the frame's lhs replaces the chain's, once per monad
            "5.plus(lhs=>3,args=>[1])                                        % 4",
            "{1,2}.plus(lhs=>3,args=>[1])                                    % {{2}4}",
            // a blocked instruction applied positionally is untouched
            "|plus(1).apply(3)                                               % 4",
    }, delimiter = '%')
    public void testAFrameIsItsCallWrittenAsData(final String frame, final String expected) {
        checkCodeParseApply(LOG, frame.trim(), expected.trim());
    }

    /**
     * A KEYED CALL THAT IS NO FRAME IS UNCHANGED: a signature that matches binds as it always did (repeat's own keys),
     * and a rec that is not exactly lhs and args is no frame.
     */
    @Test
    public void testOnlyAFrameIsAFrame() {
        checkCodeParseApply(LOG, "{1,2,3}.repeat(code=>plus(1),until=>loop()?>3)", "{5,6,7}");
        final Obj notAFrame = ObjmtronSerializer.eval("plus(lhs=>3)");
        assertTrue(notAFrame.isFail() || notAFrame.isNoObj(), "lhs alone is no frame: " + notAFrame);
    }
}
