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

package studio.phaseshift.metatron.algebra.rewrite;

import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.util.MTronException;

import java.util.List;
import java.util.Objects;

import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;

/**
 * Hop — the {@code hop} axis of a distribution decision: <em>how</em> the application tree is shaped, as its
 * {@link #height()} (how many times the rule may re-apply to the code it produced) and its {@link #arity()} (how many
 * children one level may fan out to). The two numbers are the design's one coefficient reading applied to depth
 * (§3 and §5 of {@code distributed-computing-qparams.md}): {@code {2}4} is a tree of height 4 and arity 2.
 *
 * <p>Three truncations of the one recursive expansion, which is what replaces the topology enum:
 * arity zero is the point, arity {@code cInt.ANY()} (the default, "the receiver's local out-degree") the
 * free/irregular tree, and arity one the chain — which is what the emitter does today at every height.
 *
 * <p>The axis earns its place in the obligations derivation: an arity of exactly one means one child per level, so
 * each level's merge is <em>sequential</em> — order is preserved and no order-free barrier is crossed
 * ({@link #sequential()}). That is the chain, and it is why a chain demands {@code monoidic} alone while a fan-out
 * demands {@code monoidic} and {@code commutative}.
 *
 * <p><b>The spelling is one coefficient reading, with a fallback for the two coefficients mtron cannot render.</b>
 * A bare int is the DEFAULT — today's semantics, height stated and arity "the receiver's local out-degree"
 * ({@code cInt.ANY()}), which is what every {@code ?hop=n} in the tree means — and a coefficient is the stated arity
 * ({@code {2}4} is a binary tree of height 4, {@code {2,4}4} a band). Two arities have no coefficient spelling at all:
 * {@code {0}n} is {@code noobj} (a zero coefficient is no object) and {@code {1}n} normalizes to the bare {@code n}
 * (mtron renders a coefficient of one away). Both are written as the pair {@code [height, arity]} instead
 * ({@link #toObj()}), so every truncation of the tree — including the <em>chain</em>, {@code [n,1]}, whose
 * {@link #sequential()} reading the obligations derivation depends on — has a spelling. The fallback is the grammar's
 * constraint, not a second vocabulary: {@code ?hop=n{0}} and {@code ?hop=n{1}} cannot be written as q values either.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class Hop {

    private final int height;
    private final cInt arity;

    private Hop(final int height, final cInt arity) {
        if (0 > height)
            throw MTronException.of("a hop height is zero or more, not %d", height);
        this.height = height;
        this.arity = Objects.requireNonNull(arity, "a hop states an arity");
    }

    /**
     * A tree of {@code height} levels, each fanning out to what {@code arity} allows.
     */
    public static Hop of(final int height, final cInt arity) {
        return new Hop(height, arity);
    }

    /** No depth — the point: the fold runs where it stands. */
    public static Hop point() {
        return new Hop(0, cInt.ANY());
    }

    /** {@code height} levels, each bound by the receiver's own out-degree — the free tree (today's behavior). */
    public static Hop tree(final int height) {
        return new Hop(height, cInt.ANY());
    }

    /**
     * {@code height} levels, each with one child — sequential forwards, the chain (<em>not</em> an order-free
     * fan-out). This is the axis value that makes a decision demand {@code monoidic} alone.
     */
    public static Hop chain(final int height) {
        return new Hop(height, cInt.ONE());
    }

    /**
     * Read the axis from the value a {@code hop} key holds: a counted int (the value is the height, the coefficient
     * the arity) or the pair {@code [height, arity]} for the two coefficients the grammar cannot render.
     *
     * <p><b>A bare int is TODAY, not one child:</b> no coefficient means "the receiver's local out-degree"
     * ({@code cInt.ANY()}) — every {@code ?hop=n} in the tree means exactly that — so this reader must not turn it into
     * a chain. A stated arity of one and a stated zero ride the pair form instead ({@code [n,1]}, {@code [n,0]}).
     */
    public static Hop of(final Obj value) {
        if (value.isLst()) {
            final List<Obj> pair = value.asLst().elements().toList();
            if (2 != pair.size() || !pair.get(0).isInt() || !pair.get(1).isInt())
                throw MTronException.of("a hop pair is [height, arity], two whole numbers: %s", value);
            return new Hop(pair.get(0).asInt().intValue().intValue(), cInt.of(pair.get(1).asInt().intValue()));
        }
        if (!value.isInt())
            throw MTronException.of("a hop is a counted int (the value is the height, the coefficient the arity) or a [height, arity] pair: %s", value);
        // a bare int is today's spelling: the height stated, the arity unstated (the local out-degree)
        return new Hop(value.asInt().intValue().intValue(), value.c().isOne() ? cInt.ANY() : value.c());
    }

    /** How many times the rule may re-apply to the code it produced. */
    public int height() {
        return this.height;
    }

    /** How many children one level may fan out to; {@code cInt.ANY()} is the receiver's local out-degree. */
    public cInt arity() {
        return this.arity;
    }

    /**
     * Whether one level has exactly one child — a sequential forward, so the merge preserves order and no
     * order-free barrier is crossed. The chain's shape, and the reason a chain demands {@code monoidic} alone.
     */
    public boolean sequential() {
        final Long exact = this.arity.min();
        return null != exact && exact.equals(this.arity.max()) && 1L == exact;
    }

    /** Whether this hop has no depth at all — the point, where the fold runs where it stands. */
    public boolean isPoint() {
        return 0 == this.height;
    }

    /**
     * The value a {@code hop} key holds: the bare height for the default arity (today's spelling, {@code 2}), the
     * counted int for a stated arity the grammar can render ({@code {2}4}, {@code {2,4}4}), and the pair
     * {@code [height, arity]} for the two it cannot ({@code [n,1]} a chain, {@code [n,0]} a point per level).
     */
    public Obj toObj() {
        if (this.arity.isAny())
            return jnt(this.height);
        if (this.arity.isExact() && 2 > this.arity.min())
            return lst(jnt(this.height), jnt(this.arity.min()));
        final Obj value = jnt(this.height).c(this.arity);
        if (value.isNoObj())
            throw MTronException.of("a hop arity of %s has no spelling: a zero coefficient is noobj in mtron, and the pair form states only whole numbers", this.arity);
        return value;
    }

    @Override
    public boolean equals(final Object other) {
        return other instanceof Hop that && this.height == that.height && this.arity.equals(that.arity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.height, this.arity);
    }

    @Override
    public String toString() {
        return this.arity.isAny() ? String.valueOf(this.height) : "{" + this.arity + "}" + this.height;
    }
}
