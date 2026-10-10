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

package studio.phaseshift.metatron.isa.mach.type.compiler.resolver;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.m.type.resolver.Binder;
import studio.phaseshift.metatron.isa.m.type.resolver.Resolver;
import studio.phaseshift.metatron.isa.m.type.resolver.Selector;
import studio.phaseshift.metatron.util.MTronException;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static studio.phaseshift.metatron.Tokens.BINDER;
import static studio.phaseshift.metatron.Tokens.SELECTOR;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_BINDER_TID;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_SCORING_RESOLVER_TID;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_SELECTOR_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * ScoringResolver — the concrete {@code scoring_resolver::T}: the resolution stage of
 * {@code compiler::T}, and the default composition of its two collaborators. It owns the walk —
 * threading each inst's range into the next inst's domain
 * ({@link Resolver.Helper#resolveCode}) — and delegates the per-inst work to a {@link Selector}
 * (fetch and order the candidates) and a {@link Binder} (make the chosen candidate concrete, or
 * reject it).
 *
 * Both are rec entries, so a compiler composes its own resolution by wiring either one:
 * {@code resolver::[selector => firstfind_selector::T, binder => generic_binder::T]}. The defaults
 * are {@code specificity_selector::T} and {@code generic_binder::T}.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ScoringResolver extends MRec implements Resolver {

    private static final ScoringResolver INSTANCE = new ScoringResolver(mutableMap(), MACH_SCORING_RESOLVER_TID, null);

    public static ScoringResolver single() {
        return INSTANCE;
    }

    public ScoringResolver() {
        this(mutableMap(), MACH_SCORING_RESOLVER_TID, null);
    }

    public ScoringResolver(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    // fine-grained resolve-stage timing (read by the profile() instruction). times accumulate
    // across all resolutions; resetTimings() zeroes them before a profiling window. The binder
    // advances them, so the profile() report is unchanged by the selector/binder split.
    public static final AtomicLong T_RESOLVE = new AtomicLong(0);
    public static final AtomicLong T_BIND = new AtomicLong(0);
    public static final AtomicLong T_COMPOSE = new AtomicLong(0);

    public static void resetTimings() {
        T_RESOLVE.set(0);
        T_BIND.set(0);
        T_COMPOSE.set(0);
    }

    /**
     * The selector this resolver delegates per-instruction selection to — its {@code selector} rec
     * entry, defaulting to {@code specificity_selector::T}.
     * <p>
     * The entry is read into an {@code Obj} before it is narrowed: {@code at()} is generic and
     * unchecked, so naming the concrete type at the read site ({@code at(k).orElse(Specificity...)})
     * makes the retrieval itself cast to that type and blows up when the entry is a different
     * strategy.
     */
    public Selector selector() {
        final Obj entry = this.at(uri(SELECTOR));
        if (null == entry || entry.isNoObj())
            return SpecificitySelector.single();
        if (!(entry instanceof Selector))
            // the structural predicate declares this field as selector::T; type_pred does not yet
            // gate it (its per-machine assertions are a TODO on TypeTyper), so the accessor is the
            // gate — and it names the mistake instead of surfacing as a bare CCE.
            throw MTronException.of("resolver %s: %s is not a %s", this, entry, MACH_SELECTOR_TID);
        return entry.as();
    }

    /**
     * The binder this resolver hands to its selector — its {@code binder} rec entry, defaulting to
     * {@code generic_binder::T} (read into an {@code Obj} first, as above).
     */
    public Binder binder() {
        final Obj entry = this.at(uri(BINDER));
        if (null == entry || entry.isNoObj())
            return GenericBinder.single();
        if (!(entry instanceof Binder))
            throw MTronException.of("resolver %s: %s is not a %s", this, entry, MACH_BINDER_TID);
        return entry.as();
    }

    @Override
    public Code apply(final Obj code) {
        return Resolver.Helper.resolveCode(noobj(), code.asCode(), this.selector(), this.binder());
    }

    /**
     * Resolve a full instruction chain with the globally active selector and binder — the
     * whole-code threading of {@link Resolver.Helper#resolveCode}.
     */
    public static Code resolveCode(final Obj lhs, final Code code) {
        return Resolver.Helper.resolveCode(lhs, code, Selector.get(), Binder.get());
    }

    /**
     * Resolve a full instruction chain with an explicit selector and binder.
     */
    public static Code resolveCode(final Obj lhs, final Code code, final Selector selector, final Binder binder) {
        return Resolver.Helper.resolveCode(lhs, code, selector, binder);
    }
}
