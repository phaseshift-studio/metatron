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

package studio.phaseshift.metatron.isa;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;

/**
 * Space equality has two regimes, and the vid-less one used to be broken.
 * <p>
 * A space is identified by its <b>tid plus its vid</b> — its type and its position. A space with no vid has no
 * position, so there is nothing to compare it by and the answer is identity. Requiring both vids to be non-null
 * made the comparison non-reflexive: <code>space.equals(space)</code> was false for <em>every</em> vid-less space,
 * which is not a subtle edge — it silently breaks {@code Set}/{@code Map} membership and makes any assertion about
 * such a space meaningless. {@code InstSet.instset0()}, an anonymous space, and a frame union are all vid-less.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class SpaceEqualsTest extends AbstractMetatronTest {

    private static AbstractSpace<?> vidless() {
        return new AbstractSpace<>(new java.util.LinkedHashMap<>(),
                studio.phaseshift.metatron.util.CommonUtil.mutableMap(
                        studio.phaseshift.metatron.isa.m.type.impl.MUri.uri("pattern"),
                        studio.phaseshift.metatron.isa.m.type.impl.MUri.uri("#")),
                studio.phaseshift.metatron.furi.fURI.Singleton.f("instset"), null) {
        };
    }

    /**
     * the property that was missing: a space equals itself
     */
    @Test
    public void testSpaceEqualsItself() {
        final AbstractSpace<?> space = vidless();
        assertEquals(space, space, "a space must equal itself regardless of whether it has a vid");
        assertTrue(space.equals(space), "and reflexivity must hold directly, not only through the assertion");
    }

    /**
     * identity, not content: two distinct vid-less spaces are not the same space
     */
    @Test
    public void testDistinctVidlessSpacesAreNotEqual() {
        assertNotEquals(vidless(), vidless(), "two distinct vid-less spaces are distinct spaces");
    }

    /**
     * the consequence that motivated the fix — membership now works
     */
    @Test
    public void testVidlessSpaceIsFoundInASet() {
        final AbstractSpace<?> space = vidless();
        final Set<AbstractSpace<?>> spaces = new HashSet<>();
        spaces.add(space);
        assertTrue(spaces.contains(space), "a vid-less space must be findable in a set that holds it");
        assertEquals(1, spaces.size());
    }
}
