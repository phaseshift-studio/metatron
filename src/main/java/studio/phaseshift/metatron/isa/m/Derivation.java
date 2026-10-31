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

package studio.phaseshift.metatron.isa.m;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.math.cat.LawTable;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A derivation — the declaration that an instruction is definable as a composition of other instructions,
 * expressed as a rewrite pair over the generic fURIs ({@code A}…{@code G}).
 *
 * <p>For example, {@code div} derives from {@code mult ∘ inv}: the {@code lhs} {@code start(A).div(B)} is the
 * derived instruction and the {@code rhs} {@code start(A).mult(inv(B))} is its primitive expansion. The
 * category only <em>declares</em> that the rule is possible; an actual rewrite instruction applies it.
 *
 * <p>A derivation that defines a FOLD — {@code start(A)}, a pointwise run, then a fold, as
 * {@code count ↦ map(1)·sum} — is also where the fold's combiner comes from: its partials recombine through the
 * defining fold ({@link LawTable#combiner}), so the combiner is read off the definition rather than declared.
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
     * Bind the derived instruction — the lhs's last instruction — against {@code inst}: the same op address, and each
     * argument matched with {@link #matchGenerics}. Null when they do not match.
     */
    private Map<fURI, Obj> bind(final Inst inst) {
        final Inst derived = this.lhs.insts().getLast();
        if (!derived.tid().basePath().equals(inst.tid().basePath()) || derived.args().count() != inst.args().count())
            return null;
        final Map<fURI, Obj> bindings = new HashMap<>();
        for (int i = 0; i < derived.args().count(); i++)
            if (!matchGenerics(derived.arg(i), inst.arg(i), bindings))
                return null;
        return bindings;
    }

    /**
     * Match a generic pattern against a source, binding the generic fURIs ({@code A}, {@code B}, …) as they are
     * met. A generic uri matches anything (binding it); an inst matches by tid and recurses into its args.
     */
    public static boolean matchGenerics(final Obj pattern, final Obj source, final Map<fURI, Obj> bindings) {
        if (pattern.isUri() && pattern.uriValue().isGeneric()) {
            final fURI key = pattern.uriValue();
            final Obj prior = bindings.get(key);
            if (null == prior) {
                bindings.put(key, source);
                return true;
            }
            return prior.equals(source);
        }
        if (pattern.isInst() && source.isInst()) {
            final Inst p = pattern.asInst();
            final Inst s = source.asInst();
            if (!s.tid().test(p.tid()))
                return false;
            if (p.args().count() != s.args().count())
                return false;
            for (int i = 0; i < p.args().count(); i++)
                if (!matchGenerics(p.arg(i), s.arg(i), bindings))
                    return false;
            return true;
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
}
