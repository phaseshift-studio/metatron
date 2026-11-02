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

package studio.phaseshift.metatron.isa.m.mach.type.compiler.resolver;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.Resolver;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static studio.phaseshift.metatron.Tokens.BINDER;
import static studio.phaseshift.metatron.Tokens.SELECTOR;
import static studio.phaseshift.metatron.isa.m.mach.machInstSet.MACH_SCORING_RESOLVER_TID;
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
        if (this.at(SELECTOR).isNoObj())
            this.at(SELECTOR, SpecificitySelector.single(), MUTABLE);
        if (this.at(BINDER).isNoObj())
            this.at(BINDER, GenericBinder.single(), MUTABLE);
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
}
