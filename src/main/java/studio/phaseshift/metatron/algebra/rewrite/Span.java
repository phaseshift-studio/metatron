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

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * Span — an induced region of the code chain, named by {@link Kind} relative to the plan's single cut:
 * {@code before/at/after} the cut, or the {@code seed} (the chain's initial inst). The cut is the primitive;
 * the spans are the regions it induces — {@code law_shard} is the one-cut case.
 *
 * <p>A span carries the <em>atom obligation</em> its shape demands, which {@code create()} and the rewriter
 * check against the matched insts: {@code PRE} is the floatable run (Thm 5/6, {@code F_m ∪ F_f ∪ F_fm}), so
 * {@link #floatable()} marks it; the modifiers {@link #withoutSeed()} (replicate the run but not the seed —
 * the seed's bulk is partitioned instead) and {@link #partition()} (factor this span's data across the
 * route's fan-out) are what turn a local plan into a distributed one.
 *
 * <p>A Span is a {@code rec} — {@code [span => uri(kind), flags => lst(uri(flag))]}, the region named by
 * {@code kind} relative to the plan's cut.
 * The three booleans are encoded as a {@code flags} list of uri markers ({@code floatable}/{@code no_seed}/
 * {@code partition}) so the rec needs no per-boolean key. Every accessor reads {@code this.at(key)}; there
 * are no Java fields.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class Span extends MRec {

    /**
     * The region of the chain a span names.
     */
    public enum Kind {
        /** the floatable run before the cut — {@code F_m ∪ F_f ∪ F_fm}, Thm 5/6 */
        PRE,
        /** the cut itself — the blocked anchor, Thm 17 */
        AT,
        /** everything after the cut — stays home on the master's chain */
        POST,
        /** the run between two cuts — the pipeline case */
        BETWEEN,
        /** the seed — the chain's initial inst, whose bulk is the data that factors */
        SEED
    }

    public Span(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    public static Span before() {
        return of(Kind.PRE);
    }

    public static Span at() {
        return of(Kind.AT);
    }

    public static Span after() {
        return of(Kind.POST);
    }

    public static Span seed() {
        return of(Kind.SEED);
    }

    private static Span of(final Kind kind) {
        final Map<Obj, Obj> jvm = new LinkedHashMap<>();
        jvm.put(uri(SPAN), uri(kind.name().toLowerCase()));
        return new Span(jvm, REC_TID, null);
    }

    /**
     * Mark the span's atoms as the floatable run (Thm 6) — the obligation the rewriter enforces at match time.
     */
    public Span floatable() {
        return flag("floatable");
    }

    /**
     * Replicate this span's run without carrying the seed — the seed's bulk is partitioned instead.
     */
    public Span withoutSeed() {
        return flag("no_seed");
    }

    /**
     * Factor this span's data across the route's fan-out (the seed bulk in the one-cut shard).
     */
    public Span partition() {
        return flag("partition");
    }

    private Span flag(final String flag) {
        final Obj current = this.at(FLAGS);
        final List<Obj> flags = new ArrayList<>(current.isNoObj() ? List.of() : current.asLst().elements().toList());
        final Obj token = uri(flag);
        if (flags.stream().noneMatch(token::equals))
            flags.add(token);
        this.jvm().put(uri(FLAGS), lst(flags));
        return this;
    }

    public Kind kind() {
        return Kind.valueOf(this.at(SPAN).uriValue().name().toUpperCase());
    }

    public boolean isFloatable() {
        return flagged("floatable");
    }

    public boolean isWithoutSeed() {
        return flagged("no_seed");
    }

    public boolean isPartition() {
        return flagged("partition");
    }

    private boolean flagged(final String flag) {
        final Obj current = this.at(FLAGS);
        return !current.isNoObj() && current.asLst().elements().anyMatch(o -> o.uriValue().name().equals(flag));
    }

    /**
     * Reconstruct a Span from a raw {@code rec} — the read-side of the same {@code span/cut/flags} encoding.
     */
    public static Span of(final Rec rec) {
        return new Span(new LinkedHashMap<>(rec.jvm()), rec.tid(), rec.vid());
    }
}
