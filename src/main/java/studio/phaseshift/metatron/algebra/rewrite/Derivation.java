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
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;

/**
 * A derivation — the declaration that an instruction is definable as a composition of other instructions,
 * expressed as a rewrite pair over the generic fURIs ({@code A}…{@code G}).
 *
 * <p>For example, {@code div} derives from {@code mult ∘ inv}: the {@code lhs} {@code div(B)} is the
 * derived instruction and the {@code rhs} {@code mult(inv(B))} is its primitive expansion. The
 * category only <em>declares</em> that the rule is possible; an actual rewrite instruction applies it.
 *
 * <p>A derivation that defines a FOLD — a pointwise run, then a fold, as
 * {@code count ↦ map(1)·sum} — is also where the fold's combiner comes from: its partials recombine through the
 * defining fold ({@link Law.Helper#combiner}), so the combiner is read off the definition rather than declared.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public record Derivation(Code lhs, Code rhs) {

    /**
     * The defining fold of {@code derived} — the rhs's last instruction, its generics bound from {@code derived}'s own
     * arguments — or null when {@code derived} is not this derivation's derived instruction.
     */
    public Inst fold(final Inst derived) {
        final Map<fURI, Obj> bindings = this.bind(derived);
        return null == bindings ? null : (Inst) Inst.Helper.substituteGenerics(this.rhs.insts().getLast(), bindings);
    }

    /**
     * Bind the derived instruction — the lhs's last instruction — against {@code inst}: the same op address, and the
     * arguments matched position by position ({@link #matchArgs}). Null when they do not match.
     */
    private Map<fURI, Obj> bind(final Inst inst) {
        final Inst derived = this.lhs.insts().getLast();
        final Map<fURI, Obj> bindings = new HashMap<>();
        return derived.tid().basePath().equals(inst.tid().basePath()) && matchArgs(derived, inst, bindings) ? bindings : null;
    }

    /**
     * Match a generic pattern against a source, binding the generic fURIs ({@code A}, {@code B}, …) by name as they are
     * met. A generic uri matches anything (binding it), and an absent argument (noobj) only where the pattern admits
     * zero; an inst matches by tid and recurses into its args.
     */
    public static boolean matchGenerics(final Obj pattern, final Obj source, final Map<fURI, Obj> bindings) {
        if (pattern.isUri() && pattern.uriValue().isGeneric()) {
            // AN ABSENT ARGUMENT BINDS ONLY WHERE THE PATTERN ADMITS ZERO: order(B{?}) takes a bare order()
            if (source.isNoObj() && !pattern.uriValue().c().isZeroable())
                return false;
            final fURI key = pattern.uriValue().one();
            final Obj prior = bindings.get(key);
            if (null == prior) {
                bindings.put(key, source);
                return true;
            }
            return prior.equals(source);
        }
        // an absent argument against anything else: only an unstructured pattern that admits zero takes it
        if (source.isNoObj())
            return !pattern.isInst() && !pattern.isCode() && source.test(pattern);
        if (pattern.isInst() && source.isInst()) {
            final Inst p = pattern.asInst();
            final Inst s = source.asInst();
            return s.tid().test(p.tid()) && matchArgs(p, s, bindings);
        }
        if (pattern.isCode() && source.isCode()) {
            final List<Inst> ps = pattern.asCode().insts();
            final List<Inst> ss = source.asCode().insts();
            if (ps.size() != ss.size())
                return false;
            for (int i = 0; i < ps.size(); i++)
                if (!matchGenerics(ps.get(i), ss.get(i), bindings))
                    return false;
            return true;
        }
        // a structural pattern (inst/code) never matches a non-structural source — falling through to
        // source.test(pattern) here would let a scalar pass a nested-composition pattern
        if (pattern.isInst() || pattern.isCode() || source.isInst() || source.isCode())
            return false;
        return source.test(pattern);
    }

    /**
     * A PATTERN'S ARGUMENTS AGAINST AN INSTRUCTION'S, POSITION BY POSITION: an argument the instruction does not pass is
     * noobj, which only a pattern argument admitting zero takes (noobj is within {@code B{0,1}}), and an argument the
     * pattern does not name matches nothing.
     */
    private static boolean matchArgs(final Inst pattern, final Inst source, final Map<fURI, Obj> bindings) {
        if (source.args().count() > pattern.args().count())
            return false;
        for (int i = 0; i < pattern.args().count(); i++)
            if (!matchGenerics(pattern.arg(i), i < source.args().count() ? source.arg(i) : noobj(), bindings))
                return false;
        return true;
    }
}
