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

package studio.phaseshift.metatron.isa.mach.type;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;

/**
 * The frame wrapper's properties — and the third is the one the whole model rests on.
 * <p>
 * A {@link ComponentUnion} holds what a frame <em>introduced</em> beside what it <em>inherited</em> and reads
 * through, so a name is visible for exactly as long as the frame that introduced it is on the stack. Nothing is
 * copied, which makes release structural: an inherited value is not in {@code current()}, so it cannot be closed
 * from here — a guarantee rather than a convention, and the reason there is no reference count.
 * <p>
 * Tested against a double rather than a real ISA, because what is under test is the <em>union</em>: who is asked,
 * in what order, and lazily. Real {@code InstSet} resolution keys instructions by their own tid and admits them by
 * domain, which would obscure the delegation being asserted here.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class InstSetUnionTest extends AbstractMetatronTest {

    /**
     * an ISA that records what was asked of it and what it holds
     */
    private static final class Tracked extends AbstractInstSet {

        private final Set<fURI> held = new LinkedHashSet<>();
        private final Map<fURI, Obj> written = new LinkedHashMap<>();
        private int reads = 0;
        private boolean closed = false;

        private Tracked() {
            super(true);
        }

        private Tracked holding(final String pattern) {
            this.held.add(f(pattern));
            return this;
        }

        @Override
        public Obj read(final fURI pattern) {
            this.reads++;
            return this.held.contains(pattern) ? this : noobj();
        }

        @Override
        public Obj write(final fURI vid, final Obj obj) {
            this.written.put(vid, obj);
            return obj;
        }

        @Override
        public void close() {
            this.closed = true;
        }

        @Override
        public Set<Inst> insts() {
            return new LinkedHashSet<>(this.written.values().stream().filter(Obj::isInst).map(Obj::asInst).toList());
        }

        @Override
        public Set<Type> types() {
            return new LinkedHashSet<>(this.written.values().stream().filter(Obj::isType).map(Obj::asType).toList());
        }

        @Override
        public Set<Obj> consts() {
            return new LinkedHashSet<>(this.written.values().stream().filter(o -> !o.isInst() && !o.isType()).toList());
        }

        @Override
        public Set<Inst> rewrites() {
            return new LinkedHashSet<>();
        }
    }

    private static Obj anInst(final String source) {
        return ObjmtronSerializer.parse(source);
    }

    /**
     * A name the frame introduced is answered by the frame; a name it did not is answered by the parent — and the
     * parent is <b>not consulted</b> when the frame already has the answer.
     * <p>
     * That last clause is the point. {@code Obj.orElse(Obj)} takes a value, so the natural-looking
     * {@code current().read(p).orElse(parent().read(p))} would read the parent on <em>every</em> hit — the wrong
     * default on the resolution path. This asserts the fallback is lazy.
     */
    @Test
    public void testReadPrefersTheFrameAndFallsThroughLazily() {
        final Tracked parent = new Tracked().holding("/m/union/from-parent");
        final Tracked current = new Tracked().holding("/m/union/from-frame");
        final InstSetUnion union = new InstSetUnion(parent, current);

        // assertSame, not assertEquals: Rec equality is not reflexive for these objs, so assertEquals fails
        // even against the identical instance
        assertSame(current, union.read(f("/m/union/from-frame")),
                "the frame's own name is answered by the frame");
        assertEquals(0, parent.reads,
                "the parent must not be consulted when the frame already has the answer");

        assertSame(parent, union.read(f("/m/union/from-parent")),
                "a name the frame does not hold falls through to the parent");
        assertEquals(1, parent.reads, "and the parent is consulted exactly once");
    }

    /**
     * a frame writes only what it introduced — the parent is never touched
     */
    @Test
    public void testWriteLandsInTheFrameOnly() {
        final Tracked parent = new Tracked();
        final Tracked current = new Tracked();
        final InstSetUnion union = new InstSetUnion(parent, current);
        union.write(f("/m/union/written"), anInst("id()"));

        assertTrue(parent.written.isEmpty(), "a frame write must not reach the parent");
        assertEquals(1, current.written.size(), "the frame's own write stays in the frame");
    }

    /**
     * the property everything else depends on: popping a frame releases what the frame opened and leaves what it
     * inherited untouched.
     * <p>
     * This holds only because {@link InstSetUnion#close()} overrides {@code AbstractInstSet.close()} explicitly —
     * an inherited class method shadows an interface default, so without that override
     * {@link ComponentUnion#close()} never runs and the frame leaks everything it introduced.
     */
    @Test
    public void testCloseReleasesOnlyWhatTheFrameOwns() {
        final Tracked parent = new Tracked();
        final Tracked current = new Tracked();
        final InstSetUnion union = new InstSetUnion(parent, current);

        union.close();

        assertTrue(current.closed, "the frame's own ISA must be released");
        assertFalse(parent.closed, "an inherited ISA must never be released by the frame that borrowed it");
    }

    /**
     * the accumulating accessors see both sides, frame first, so a frame's instruction wins a name contest
     */
    @Test
    public void testAccessorsUnionBothSides() {
        final Tracked parent = new Tracked();
        final Tracked current = new Tracked();
        parent.write(f("/m/union/p"), anInst("id()"));
        current.write(f("/m/union/c"), anInst("count()"));
        final InstSetUnion union = new InstSetUnion(parent, current);

        assertEquals(2, union.insts().size(), "both sides belong to the union: " + union.insts());
        assertEquals(current.insts().iterator().next(), union.insts().iterator().next(),
                "the frame's own instruction comes first, so it wins a name contest");
    }
}
