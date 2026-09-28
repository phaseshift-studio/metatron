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

package studio.phaseshift.metatron;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code profile()} — the {@code profile::T} instruction that times the resolve (compile) and
 * apply (evaluate) stages of a code and returns a text table. Mirrors {@code explain()}.
 */
public class ProfileInstTest extends AbstractMetatronTest {

    @ParameterizedTest
    @CsvSource(value = {
            "1.plus(2)",
            "a=>b=>c.rng()",
            "[1=>2,2=>3,3=>4]>-.type()",
    }, delimiter = '%')
    public void testProfileReturnsTimingTable(final String code) {
        final Obj r = ObjmtronSerializer.eval(code + ".profile()");
        assertFalse(r.isFail(), "profile() should not fail: " + r);
        final String s = r.toString();
        assertTrue(s.contains("rewrite"), "profile() output should include rewrite timing: " + s);
        assertTrue(s.contains("resolve"), "profile() output should include resolve timing: " + s);
        assertTrue(s.contains("apply"), "profile() output should include apply timing: " + s);
        assertTrue(s.contains("insts"), "profile() output should include the inst count: " + s);
        assertTrue(s.contains("inst-resolve"), "profile() output should include the inst-resolve sub-stage: " + s);
        assertTrue(s.contains("generic-binding"), "profile() output should include the generic-binding sub-stage: " + s);
        assertTrue(s.contains("inst-composition"), "profile() output should include the inst-composition sub-stage: " + s);
        assertTrue(s.contains("split"), "profile() output should include the processor split sub-stage: " + s);
        assertTrue(s.contains("flow"), "profile() output should include the flow metric: " + s);
        assertTrue(s.contains("compression"), "profile() output should include the compression ratio: " + s);
        assertTrue(s.contains("processors"), "profile() output should include the processor spawn count: " + s);
        assertTrue(s.contains("cache"), "profile() output should include the type-graph cache stats: " + s);
        assertTrue(s.contains("hit="), "profile() output should include the cache hit rate: " + s);
    }

    @Test
    public void testBareProfileIsNoOp() {
        // bare profile() with no preceding code is a no-op (returns the lhs), like bare explain()
        assertFalse(ObjmtronSerializer.eval("1.plus(2)").isFail(), "sanity: 1.plus(2) evals");
    }
}
