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

import java.util.Objects;

import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * Partition — the {@code split} axis of a distribution decision: <em>what counts as one place's share</em>
 * (§4 of {@code distributed-computing-qparams.md}). The plan has a cut (how partials recombine) and a place set
 * (where the shares go); this is the third noun the decision rec never had — the algebra's own {@code split}, the
 * partition function every distributive fold factors through ({@code h = map(g) · split}).
 *
 * <p>The {@link Kind kinds} are the design's vocabulary, not new words: {@code stream} (the default, stride division),
 * {@code block} (contiguous runs, needing order), {@code key} (bucketed, needing a key), {@code owner} (one part per
 * address owner), {@code index} (ranges of an index space, where nothing ships), and {@code copy} (the whole seed on
 * every lane, {@code stream{1}} in the coefficient reading).
 *
 * <p>The {@link #parts()} count is a {@link cInt}, read the same way as every other coefficient (§5): a stated
 * coefficient is the part count ({@code {2}block}), and the <em>bare</em> kind is that kind's default — as many parts
 * as lanes ({@code cInt.ANY()}), which is what the emitter does today. A part count below the lane count makes the
 * surplus lanes carry the same part, which is exactly hedging; {@code copy} is that at one part. (A zero part count,
 * "decline", has no spelling: a zero coefficient is {@code noobj}.)
 *
 * <p>One kind earns its place in the obligations derivation: a {@code key} partition co-locates equal keys, so a
 * verdict is folded per bucket and never merged across a place boundary — it demands {@code monoidic} alone. Every
 * other kind — {@code block}, {@code owner}, {@code index}, {@code copy} — ships shares that are still gathered and
 * recombined at home through the same combiner, so it demands the off-machine merge's two laws. {@code index} ships
 * no <em>data</em>, but the code still ships to the range holders and their partials still recombine: it is derived
 * like a fan-out, deliberately, because the gate that demands the laws is the one that must fail closed.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class Partition {

    /**
     * The partition functions (§4). The value token is the kind's own name, lowercased — the same singular vocabulary
     * the keys use.
     */
    public enum Kind {
        /** stride division — any exact division; the default, and today's emitter */
        STREAM,
        /** contiguous runs, recombined in order — needs order */
        BLOCK,
        /** bucketed by key; equal keys co-locate — the keyed fan-out */
        KEY,
        /** one part per address owner — needs an addressed seed */
        OWNER,
        /** ranges of an index space — the body generates the members, nothing ships */
        INDEX,
        /** the whole seed on every lane — one part, {@code stream{1}} */
        COPY
    }

    private final Kind kind;
    private final cInt parts;

    private Partition(final Kind kind, final cInt parts) {
        this.kind = Objects.requireNonNull(kind, "a partition states a kind");
        this.parts = Objects.requireNonNull(parts, "a partition states a part count");
    }

    /**
     * A partition of {@code kind} into what {@code parts} allows.
     */
    public static Partition of(final Kind kind, final cInt parts) {
        return new Partition(kind, parts);
    }

    /**
     * The default: stride division into as many parts as there are lanes — today's emitter, and the bare
     * {@code stream} spelling.
     */
    public static Partition stream() {
        return new Partition(Kind.STREAM, cInt.ANY());
    }

    /**
     * A stride partition into {@code parts} parts.
     */
    public static Partition stream(final cInt parts) {
        return new Partition(Kind.STREAM, parts);
    }

    /**
     * The keyed partition — equal keys co-locate, so the folds stay per bucket.
     */
    public static Partition key() {
        return new Partition(Kind.KEY, cInt.ANY());
    }

    /**
     * The whole seed on every lane — one part, the design's {@code stream{1}}. The kind carries that count itself, so
     * a bare {@code copy} reads back as one part (a bare kind is otherwise the default, as many parts as lanes).
     */
    public static Partition copy() {
        return new Partition(Kind.COPY, cInt.ONE());
    }

    /**
     * Read the axis from the value a {@code split} key holds: a counted uri whose name is the kind and whose
     * coefficient is the part count ({@code {2}block}, {@code {2,4}stream}).
     *
     * <p><b>A bare kind is that kind's OWN default, never "one":</b> {@code stream} is as many parts as lanes (today's
     * emitter, the design's default) and so is every other kind that partitions a stream — except {@code copy}, whose
     * whole meaning is one part, the seed whole on every lane. So a bare spelling can never turn a default into a
     * broadcast: one part is the {@code copy} kind, and a stated one on another kind has no spelling at all
     * ({@link #toObj()} refuses it) because mtron normalizes a coefficient of one away.
     */
    public static Partition of(final Obj value) {
        if (!value.isUri())
            throw MTronException.of("a split is a counted uri whose name is the partition kind: %s", value);
        final String name = value.uriValue().name();
        final Kind kind;
        try {
            kind = Kind.valueOf(name.toUpperCase());
        } catch (final IllegalArgumentException e) {
            throw MTronException.of("a partition kind is %s, not %s",
                    String.join("/", java.util.Arrays.stream(Kind.values()).map(one -> one.name().toLowerCase()).toList()), name);
        }
        final cInt parts = !value.c().isOne() ? value.c() : Kind.COPY == kind ? cInt.ONE() : cInt.ANY();
        return new Partition(kind, parts);
    }

    /** What counts as one place's share. */
    public Kind kind() {
        return this.kind;
    }

    /** How many parts the seed is divided into; {@code cInt.ANY()} is as many parts as there are lanes. */
    public cInt parts() {
        return this.parts;
    }

    /**
     * The value a {@code split} key holds: the bare kind for its own default count ({@code stream} is as many parts
     * as lanes, {@code copy} the whole seed on every lane), the counted uri for a stated count ({@code {2}block},
     * {@code {2,4}stream}). A stated ONE on a stream-family kind is unspellable — {@code {1}stream} normalizes to
     * {@code stream}, which is the default — so it is refused and named for what it is: the {@code copy} kind.
     */
    public Obj toObj() {
        if (this.parts.isAny() || (Kind.COPY == this.kind && this.parts.isOne()))
            return uri(this.kind.name().toLowerCase());
        if (this.parts.isOne())
            throw MTronException.of("a stated one part is the copy kind (the whole seed on every lane): a coefficient of "
                    + "one normalizes away in mtron, so `{1}%s` would read back as `%s`, the default", this.kind.name().toLowerCase(), this.kind.name().toLowerCase());
        final Obj value = uri(this.kind.name().toLowerCase()).c(this.parts);
        if (value.isNoObj())
            throw MTronException.of("a part count of %s has no spelling: a zero coefficient is noobj in mtron", this.parts);
        return value;
    }

    @Override
    public boolean equals(final Object other) {
        return other instanceof Partition that && this.kind == that.kind && this.parts.equals(that.parts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.kind, this.parts);
    }

    @Override
    public String toString() {
        return this.parts.isAny() ? this.kind.name().toLowerCase() : "{" + this.parts + "}" + this.kind.name().toLowerCase();
    }
}
