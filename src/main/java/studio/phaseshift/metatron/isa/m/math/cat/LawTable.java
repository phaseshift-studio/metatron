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

package studio.phaseshift.metatron.isa.m.math.cat;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.math.cat.catInstSet.Law;
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * A per-instset law table — the declared laws of one instruction-set family, collocated in one place.
 *
 * <p>The table has two axes, matching the two blocks the category lifts:
 * <ul>
 *   <li><b>process laws</b> — the {@code law::T} labels a <em>morphism</em> obeys, keyed by the inst
 *       ({@link #lookup(fURI)}). These are proved once and registered per family; they cannot be derived.
 *       The operation's inverse is a relation, not a law, so it is a paired field on {@link Entry} rather
 *       than mixed into the law list.</li>
 *   <li><b>structural laws</b> — the algebraic theories an <em>object</em> models, keyed by the type
 *       ({@link #typeLaws(Type)}). These are the theory types ({@code ring_theory::T}, {@code group_theory::T},
 *       …) on {@code object::T}, not process laws.</li>
 * </ul>
 *
 * <p>A table is a singleton built per family: it registers its process laws in its constructor and implements
 * {@link #typeLawsUncached(Type)} for its object laws. See {@link mInstSetLawTable} for the base types'
 * instance.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public abstract class LawTable {

    /**
     * A process-law row: the declared laws, and the operation's inverse (null when none).
     */
    public record Entry(Lst laws, fURI inverse) {
    }

    private final Map<fURI, Entry> table = new HashMap<>();
    private final Map<fURI, Rec> typeLawsCache = new ConcurrentHashMap<>();

    /**
     * Register an inst's declared process laws and inverse.
     */
    protected final void entry(final fURI inst, final fURI inverse, final Law... declared) {
        this.table.put(inst, new Entry(laws(declared), inverse));
    }

    /**
     * The declared process laws and inverse of the inst, or null when the inst is not in the table.
     */
    public final Entry lookup(final fURI instTid) {
        return this.table.get(instTid);
    }

    /**
     * The structural theories the type models, keyed by each theory instance's name.
     */
    public final Rec typeLaws(final Type type) {
        return this.typeLawsCache.computeIfAbsent(type.tid().basePath(), k -> this.typeLawsUncached(type));
    }

    /**
     * The uncached object-law rec for the type — the theory instances the type models.
     */
    protected abstract Rec typeLawsUncached(final Type type);

    /**
     * The authoring helper: a lst of law-label uris for a declaration — {@code laws(Law.commutative, Law.right_distributive)}.
     */
    protected static Lst laws(final Law... laws) {
        return lst(Arrays.stream(laws).map(l -> (Obj) uri(l.name())).toList());
    }
}
