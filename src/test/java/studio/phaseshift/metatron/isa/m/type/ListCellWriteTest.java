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

package studio.phaseshift.metatron.isa.m.type;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.Poly.MUTABLE;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * The two halves of addressable list cells: a write to a cell's own address must SET it (not append
 * beside the list), and a structured update must never change a list's arity.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ListCellWriteTest extends AbstractMetatronTest {

    /**
     * A deep write into a space-held list.  {@code resolveWrite}'s list branch used to
     * {@code append(obj)} to the base list, so {@code …/grid/0/1 <- 'Z'} left the grid alone and the
     * parent read back as a multiplicity ({@code {grid,'Z'}}).  The rec branch just above it has
     * always set by key; the list branch is the one that has to set by index.
     */
    @Test
    void shouldSetACellOfAListStoredInASpace() {
        final fURI spaceVid = f("/usr/deepwrite");
        final memSpace space = memSpace.of(rec(uri(Tokens.PATTERN), uri("/usr/deepwrite/#")), spaceVid);
        Machine.current().memory().addSpace(space);
        try {
            Machine.write(f("/usr/deepwrite/grid"),
                    lst(lst(str("a"), str("b"), str("c")), lst(str("d"), str("e"), str("f"))));
            Machine.write(f("/usr/deepwrite/grid/0/1"), str("Z"));
            assertEquals(lst(lst(str("a"), str("Z"), str("c")), lst(str("d"), str("e"), str("f"))),
                    Machine.read(f("/usr/deepwrite/grid")),
                    "a deep write sets the addressed cell and leaves every other cell alone");
            assertFalse(Machine.read(f("/usr/deepwrite")).isObjs(),
                    "and it does not leave the parent holding the grid beside the written value");
        } finally {
            Machine.current().memory().removeSpace(spaceVid);
        }
    }

    /**
     * The same cell write through the SUGAR the console uses ({@code @path -> v}).  The plain
     * {@link Machine#write} spelling above goes straight to resolveWrite's base-poly branch; the
     * sugar reaches the same method with a VID-carrying lhs, and its read RESOLVES the deep address
     * through the value — so resolveWrite's first branch used to take that for "the space holds this
     * key" and write the leaf beside the structure instead of setting the cell.
     */
    @Test
    void shouldSetACellThroughTheSugarSpelling() {
        final fURI spaceVid = f("/usr/sugarwrite");
        final memSpace space = memSpace.of(rec(uri(Tokens.PATTERN), uri("/usr/sugarwrite/#")), spaceVid);
        Machine.current().memory().addSpace(space);
        try {
            final Obj clean = lst(lst(str("a"), str("b"), str("c")), lst(str("d"), str("e"), str("f")), lst(str("g"), str("h"), str("i")));
            ObjmtronSerializer.parse("[['a','b','c'],['d','e','f'],['g','h','i']]@/usr/sugarwrite/grid").apply();
            assertEquals(clean, Machine.read(f("/usr/sugarwrite/grid")).vid(null), "the grid stores clean");
            Machine.read(f("/usr/sugarwrite/grid/0/1"));                        // a PLAIN read, no @ inst at all
            assertEquals(clean, Machine.read(f("/usr/sugarwrite/grid")).vid(null),
                    "a READ of a cell must not change the structure (this is where the vid comes from if it fails)");
            ObjmtronSerializer.parse("@/usr/sugarwrite/grid/0/1 -> 'Z'").apply();
            assertEquals(lst(lst(str("a"), str("Z"), str("c")), lst(str("d"), str("e"), str("f")), lst(str("g"), str("h"), str("i"))),
                    Machine.read(f("/usr/sugarwrite/grid")).vid(null),
                    "the sugar spelling sets the addressed cell — no stray key, no vid left behind");
        } finally {
            Machine.current().memory().removeSpace(spaceVid);
        }
    }

    /**
     * The same cell write through a FRAME binding — the console's {@code grid -> [[…]]} case.  A
     * relative address lands in the frame's own bindings, and the frame is a space too, so the fix
     * in {@code resolveWrite}'s list branch covers both spellings.
     */
    @Test
    void shouldSetACellOfAListBoundInAFrame() {
        Machine.write(f("frame_grid"), lst(lst(str("a"), str("b")), lst(str("c"), str("d"))));
        Machine.write(f("frame_grid/0/1"), str("Z"));
        assertEquals(lst(lst(str("a"), str("Z")), lst(str("c"), str("d"))),
                Machine.read(f("frame_grid")),
                "a frame-bound list sets the addressed cell, exactly like a space-held one");
    }


    /**
     * The same cell write through a FRAME binding — the console's {@code grid -> [[…]]} case.  A
     * relative address lands in the frame's own bindings, and the frame is a space too, so the fix
     * in {@code resolveWrite}'s list branch covers both spellings.
     */
    /**
     * A structured update addresses a PREFIX of a list; the elements it does not mention survive.
     * Building the result from the common prefix alone truncated the tail to the rhs's length.
     */
}
