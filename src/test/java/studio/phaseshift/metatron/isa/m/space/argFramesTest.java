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

package studio.phaseshift.metatron.isa.m.space;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Memory;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * The argument frames: a <b>sigil-addressed</b> namespace, resolved by walking outward.
 * <p>
 * The walk skips the head, because the head is the instruction <b>now applying</b> while an argument reference
 * belongs to the instruction whose <em>body</em> is running. That is the old {@code size()-2} rule, preserved
 * deliberately — it is not "hide the current scope", and it is not shadowing. Arguments were never reachable as
 * names: an unbound bare word is a URI, and {@code plus(a)} is {@code plus(uri a)}.
 * <p>
 * What did change: {@code write} binds <em>in place</em> in the applying frame, where the old implementation's
 * write was a no-op rescued by a flat {@code root} spill. So a binding now dies with its frame instead of
 * outliving every pop — the difference the old characterization tests existed to record.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class argFramesTest extends AbstractMetatronTest {

    private static argFrames frames() {
        return Memory.argStack();
    }

    @AfterEach
    public void unwind() {
        Memory.argStack().clear();
    }

    /** the applying frame is skipped: a lone frame answers nothing for a variable read */
    @Test
    public void testTheApplyingFrameIsSkipped() {
        frames().push(rec(uri("lonely"), jnt(1)));
        assertEquals(noobj(), frames().read(f("lonely")),
                "the only frame is the one applying, and the walk starts below it");

        frames().push(rec());
        assertEquals(1, frames().read(f("lonely")).intValue(),
                "with a frame above it, the enclosing one answers");
    }

    /** the walk proceeds outward, so the nearest enclosing frame wins */
    @Test
    public void testTheWalkGoesOutward() {
        frames().push(rec(uri("outer"), jnt(1)));
        frames().push(rec(uri("outer"), jnt(2)));
        frames().push(rec());
        assertEquals(2, frames().read(f("outer")).intValue(),
                "the nearest enclosing frame answers, not the outermost");
    }

    /** {@code args} is the enclosing instruction's args, not the applying one's */
    @Test
    public void testArgsIsTheEnclosingFrame() {
        frames().push(rec(uri("marker"), jnt(1)));
        frames().push(rec(uri("marker"), jnt(2)));

        final Obj args = frames().read(Inst.ARGS_FURI);
        assertFalse(args.isNoObj(), "args resolves to the enclosing frame");
        assertEquals(1, args.asRec().at(uri("marker")).intValue(),
                "the instruction whose body is running — not the one applying");
    }

    /** a write binds in the applying frame, in place */
    @Test
    public void testAWriteBindsInPlace() {
        final studio.phaseshift.metatron.isa.m.type.Rec applying = rec();
        frames().push(applying);
        frames().write(f("bound"), jnt(9));

        assertEquals(9, applying.at(uri("bound")).intValue(),
                "the write reached the applying frame — the old implementation discarded it");
    }

    /** no root, so a binding dies with the frame that made it */
    @Test
    public void testABindingDiesWithItsFrame() {
        frames().push(rec());
        frames().write(f("transient"), jnt(7));
        frames().pop();

        assertTrue(frames().isEmpty(), "the frame stack is empty");
        assertEquals(noobj(), frames().read(f("transient")),
                "and nothing survives it — there is no flat store behind the frames");
    }

    /** with no monad in flight there is nothing to bind in and nothing to find */
    @Test
    public void testNoFrameMeansNoBinding() {
        assertTrue(frames().isEmpty(), "no monad is being evaluated");
        assertEquals(noobj(), frames().read(f("unbound")), "nothing is in scope");
        assertEquals(noobj(), frames().write(f("unbound"), jnt(1)), "and there is no frame to bind in");
    }
}
