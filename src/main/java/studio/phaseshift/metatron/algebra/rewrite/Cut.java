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
import studio.phaseshift.metatron.isa.m.Law;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * Cut — the anchor of a rewrite plan: a position in the code chain classified by {@link Inst.Form}
 * (the stream-ring's function subring) together with the {@link Law laws} it must declare for the
 * plan to be sound. The cut's <em>position</em> is computed (Thm 17: the first {@code blocked} form),
 * never hand-named; the cut <em>declares</em> what sits there and what it must obey.
 *
 * <p>The two halves of Thm 19's hypothesis are read off the plan's shape at {@code create()}-time, not
 * here: a cut that a plan ships to a non-local target and re-applies at home must declare
 * {@link Law#monoidic} (partials regroup, {@code c² = c}) and {@link Law#commutative} (order-free merge,
 * {@code ⟨X,⊕⟩} a commutative monoid).
 *
 * <p>A Cut is a {@code rec} — its state lives in the rec's map, in one of three encodings: a form and the
 * laws demanded of it, {@code [form => uri(form.name()), law => lst(uri(law.name()), ...)]}; a theory role,
 * {@code [role => uri(role)]}; or process laws alone, {@code [law => lst(uri(law.name()), ...)]}. Every
 * accessor reads {@code this.at(key)}; there are no Java fields.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class Cut extends MRec {

    public Cut(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    /**
     * A reduce anchor — {@code F_r}, many-to-one, the article's {@code reduce} (Thm 16/17).
     */
    public static Cut reducer(final Law... laws) {
        return of(Inst.Form.reducer, laws);
    }

    /**
     * A join anchor — the reduce-like merge the code groups with {@code reducer} as a fold.
     */
    public static Cut join(final Law... laws) {
        return of(Inst.Form.join, laws);
    }

    /**
     * A gather anchor — {@code F_b}, the barrier near-ring (many-to-many), the pipeline cut.
     */
    public static Cut gather(final Law... laws) {
        return of(Inst.Form.gather, laws);
    }

    /**
     * An anchor identified by a theory role — {@code id} names the identity element (the unit law's cut). The
     * role is read off {@code object::T.law}, not declared here; {@code role} names which role to cut on.
     */
    public static Cut role(final String role) {
        final Map<Obj, Obj> jvm = new LinkedHashMap<>();
        jvm.put(uri(ROLE), uri(role));
        return new Cut(jvm, REC_TID, null);
    }

    /**
     * An anchor over a declared process law only — no form and no role: the predicate the anchor must declare
     * (e.g. {@code involution}, {@code idempotent}), read back via {@link #laws()}.
     */
    public static Cut law(final Law... laws) {
        final Map<Obj, Obj> jvm = new LinkedHashMap<>();
        jvm.put(uri(LAW), lst(Arrays.stream(laws).map(l -> (Obj) uri(l.name())).toList()));
        return new Cut(jvm, REC_TID, null);
    }

    /**
     * An anchor over an arbitrary {@link Inst.Form}, with the laws the plan demands of it.
     */
    public static Cut of(final Inst.Form form, final Law... laws) {
        final Map<Obj, Obj> jvm = new LinkedHashMap<>();
        jvm.put(uri(FORM), uri(form.name()));
        // a form-only cut declares no laws: omit the key rather than write an empty lst, which the plan::T
        // predicate's lst(law::T) rejects (an empty lst reads as noobj)
        if (0 < laws.length)
            jvm.put(uri(LAW), lst(Arrays.stream(laws).map(l -> (Obj) uri(l.name())).toList()));
        return new Cut(jvm, REC_TID, null);
    }

    /**
     * Reconstruct a Cut from a raw {@code rec} — the read-side of the same {@code form/law} encoding.
     */
    public static Cut of(final Rec rec) {
        return new Cut(new LinkedHashMap<>(rec.jvm()), rec.tid(), rec.vid());
    }

    /**
     * The anchor's function subring — read back from the {@code form} uri, or null when the cut is role/law-
     * classified and carries no form.
     */
    public Inst.Form formOrNull() {
        final Obj form = this.at(FORM);
        return form.isNoObj() ? null : Inst.Form.valueOf(form.uriValue().name());
    }

    /**
     * The anchor's function subring — read back from the {@code form} uri.
     */
    public Inst.Form form() {
        return Inst.Form.valueOf(this.at(FORM).uriValue().name());
    }

    /**
     * The laws the plan demands of the anchor — read back from the {@code law} uri list.
     */
    public Set<Law> laws() {
        final Obj laws = this.at(LAW);
        if (laws.isNoObj())
            return Set.of();
        return laws.asLst().elements()
                .map(o -> Law.valueOf(o.uriValue().name()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Whether the cut declares the given process law — the guard {@code create()} validates against.
     */
    public boolean declares(final Law law) {
        return this.laws().contains(law);
    }

    /**
     * The theory role this cut identifies the anchor by, or null when the cut is form/law-classified.
     */
    public String role() {
        final Obj role = this.at(ROLE);
        return role.isNoObj() ? null : role.uriValue().name();
    }
}
