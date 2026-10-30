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
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractObjTest;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;
import studio.phaseshift.metatron.util.MTronException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

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

    /** A deep clone exists so a container handed to someone else cannot be mutated out from under its owner. */
    @Test
    public void testDeepCloneGivesIndependentContainers() {
        final Rec inner = rec(uri("b"), lst(jnt(1), jnt(2)));
        final Rec outer = rec(uri("a"), inner);
        final Rec copy = Obj.Helper.deepClone(outer).asRec();
        assertNotSame(inner, copy.at(uri("a")), "a nested container is rebuilt, not shared");
        // mutating the copy's OWN list in place must not reach the original
        copy.at(uri("a")).<Rec>as().at(uri("b")).<List<Obj>>jvm().add(jnt(3));
        assertEquals(2, inner.at(uri("b")).<List<Obj>>jvm().size(), "the original's list is untouched by the copy");
    }

    /** {@code depth} counts the container levels that get their own container. */
    @Test
    public void testDeepCloneDepthBoundsTheRebuild() {
        final Obj innerList = lst(jnt(1), jnt(2));
        final Rec inner = rec(uri("b"), innerList);
        final Rec outer = rec(uri("a"), inner);
        assertSame(inner, Obj.Helper.deepClone(outer, 0).asRec().at(uri("a")),
                "depth 0 rebuilds the outer rec only — what it holds is shared");
        final Obj oneDeep = Obj.Helper.deepClone(outer, 1).asRec().at(uri("a"));
        assertNotSame(inner, oneDeep, "depth 1 rebuilds the child rec");
        assertSame(innerList, oneDeep.<Rec>as().at(uri("b")), "depth 1 still shares the child's own contents");
    }

    /** An unbounded deep clone of a self-referential structure must trip the named gauge, not the stack. */
    @Test
    public void testSelfReferentialDeepCloneFailsWithANamedLimit() {
        final Rec self = rec(uri("self"), jnt(1));
        self.selfJVM(mutableMap(uri("self"), self));   // a rec that holds itself
        try {
            Obj.Helper.deepClone(self);
            throw new AssertionError("a self-referential rec must not clone without end");
        } catch (final StackOverflowError e) {
            throw new AssertionError("deepClone must name the runaway, not overflow the stack", e);
        } catch (final MTronException e) {
            assertTrue(e.getMessage().contains("deep clone depth limit"),
                    "the runaway must surface as the named depth limit, got: " + e.getMessage());
        }
    }

    /** A copy is anonymous — keeping the vid would anchor a later write-back to the source address. */
    @Test
    public void testDeepCloneIsUnanchored() {
        final fURI address = f("/a/b");
        final Rec anchored = rec(uri("a"), rec(uri("b"), lst(jnt(1)))).selfVID(address).asRec();
        final Rec clone = Obj.Helper.deepClone(anchored).asRec();
        assertEquals(address, anchored.vid(), "the original keeps its anchor");
        assertNull(clone.vid(), "the clone is unanchored — a * copy, not an @ reference");
        assertNull(clone.at(uri("a")).vid(), "the anchor is dropped at every level, not just the top");
    }
}
