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

import studio.phaseshift.metatron.isa.m.math.cat.LawTable;
import studio.phaseshift.metatron.isa.m.math.cat.Law;
import studio.phaseshift.metatron.isa.m.type.Inst;

import java.util.ArrayList;
import java.util.List;

/**
 * InstPattern — one slot of a CATEGORICAL match: a predicate over an instruction's classification
 * (its declared process {@link Law laws}, optionally its {@link Inst.Form form}) rather
 * than its tid, with a multiplicity. A sequence of slots is a regular expression whose alphabet is
 * the category's morphism classifications — the abstract form of {@code RewriterBuilder}'s
 * inst-specific chain match, and the match side of a distribution rule:
 * <pre>{@code
 *   [ InstPattern.any(),                       // initial
 *     InstPattern.law(Law.floatable).star(),   // {*} the prefix the coefficient floats through
 *     InstPattern.law(Law.blocked) ]           // the anchor: a reduce blocks the float (Thm 17)
 * }</pre>
 * Law lookups go through {@link LawTable#declares}, which is keyed by address and so is
 * PRE-RESOLUTION SAFE — a pattern never fires for an inst whose operand does not declare the law
 * (the same bridge every declared rewrite uses). {@code form} is an optional refinement for
 * post-resolution matching, where the n-tid coefficient shape is known.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class InstPattern {

    private final List<Law> laws = new ArrayList<>();
    private Inst.Form form = null;
    private int min = 1;
    private boolean unbounded = false;

    private InstPattern() {
    }

    public static InstPattern any() {
        return new InstPattern();
    }

    public static InstPattern law(final Law... laws) {
        final InstPattern pattern = new InstPattern();
        pattern.laws.addAll(List.of(laws));
        return pattern;
    }

    public static InstPattern form(final Inst.Form form) {
        final InstPattern pattern = new InstPattern();
        pattern.form = form;
        return pattern;
    }

    public InstPattern and(final Law... laws) {
        this.laws.addAll(List.of(laws));
        return this;
    }

    /**
     * Kleene star: zero or more — the multiplicity is the coefficient, {@code {*}} in the mtron spelling.
     */
    public InstPattern star() {
        this.min = 0;
        this.unbounded = true;
        return this;
    }

    /**
     * at least {@code n} — {@code {n,}} in the mtron spelling.
     */
    public InstPattern min(final int n) {
        this.min = n;
        this.unbounded = true;
        return this;
    }

    public int minCount() {
        return this.min;
    }

    public boolean isUnbounded() {
        return this.unbounded;
    }

    /**
     * Does one instruction satisfy this slot? Laws are declared facts — an inst whose table declares
     * nothing matches no law-bearing slot, fail closed, never guess. The two form-derived laws are the
     * exception: {@code floatable} (Thm 6, {@code F_m ∪ F_f ∪ F_fm}) and {@code blocked} (Thm 17,
     * {@code F_r ∪ F_b}) are read off the inst's {@link Inst.Form form}, since they are declared nowhere
     * in the table. The inst is expected to carry its declared (dom/rng-qualified) tid, so the form and
     * the declared-law lookups both resolve pre-resolution.
     */
    public boolean matches(final Inst inst) {
        if (null != this.form && Inst.Form.of(inst) != this.form)
            return false;
        for (final Law required : this.laws)
            if (!satisfies(inst, required))
                return false;
        return true;
    }

    private static boolean satisfies(final Inst inst, final Law law) {
        return switch (law) {
            case floatable -> Inst.Form.of(inst).isFloatable();
            case blocked -> Inst.Form.of(inst).isBlocked();
            default -> LawTable.declares(inst.tid(), law);
        };
    }

    /**
     * Match a slot sequence against an instruction chain, whole-chain, greedy per unbounded slot.
     * Answers one capture list per slot (empty for an unbounded slot that matched zero), or
     * {@code null} when the chain does not satisfy the sequence.
     */
    public static List<List<Inst>> match(final List<InstPattern> pattern, final List<Inst> chain) {
        final List<List<Inst>> captures = new ArrayList<>();
        int cursor = 0;
        for (int s = 0; s < pattern.size(); s++) {
            final InstPattern slot = pattern.get(s);
            final List<Inst> run = new ArrayList<>();
            if (slot.unbounded) {
                // greedy, but never past what the REMAINING bounded slots need
                final int reserve = pattern.subList(s + 1, pattern.size()).stream()
                        .mapToInt(InstPattern::minCount).sum();
                while (cursor < chain.size() - reserve && slot.matches(chain.get(cursor))) {
                    run.add(chain.get(cursor));
                    cursor++;
                }
                if (run.size() < slot.min)
                    return null;
            } else {
                for (int i = 0; i < slot.min; i++) {
                    if (cursor >= chain.size() || !slot.matches(chain.get(cursor)))
                        return null;
                    run.add(chain.get(cursor));
                    cursor++;
                }
            }
            captures.add(run);
        }
        return cursor == chain.size() ? captures : null;
    }
}
