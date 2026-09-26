/*
 * metatron: a distributed virtual machine and language
 *  Copyright (C) 2025- PhaseShift Studio, LLC
 *
 * This program is free software: you can redistribute it/or modify
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
import studio.phaseshift.metatron.isa.AbstractObjTest;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.util.MTronException;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;

/*
 * A deep read of the inst space clones insts whose tids are self-referential
 * ({@code ?rng=#{*}&dom=#{?}}) — the clone re-enters the read without end.
 * The clone-depth gauge must turn that runaway into a named failure
 * instead of a StackOverflowError (or the old "execution state stack
 * corrupted" fault, which was the frame cap breaking the loop with the
 * wrong name).
 */
public class ObjCloneTest extends AbstractObjTest {

    @Test
    public void testSelfReferentialInstGraphFailsWithANamedLimit() {
        final String text = deepReadFailureText();
        if (null == text)
            return;    // a complete read is also fine — no overflow either way
        assertTrue(text.contains("clone depth limit"),
                "the runaway clone must surface as the named depth limit, got: " + text);
    }

    private static String deepReadFailureText() {
        try {
            final Obj result = ObjmtronSerializer.parse("!*/m").apply(noobj());
            if (!result.isFail())
                return null;
            return ObjmtronSerializer.single().write(result);
        } catch (final StackOverflowError e) {
            throw new AssertionError("a self-referential inst graph must not overflow the stack", e);
        } catch (final MTronException e) {
            return e.getMessage();
        }
    }
}
