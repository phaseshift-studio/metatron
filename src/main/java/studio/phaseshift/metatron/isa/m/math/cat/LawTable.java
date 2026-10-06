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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec0;
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
     * A process-law row: the declared laws, the operation's inverse (null when none), and its derivation
     * (null when primitive — not definable as a composition of other instructions).
     */
    public record Entry(Lst laws, fURI inverse, Derivation derivation) {
    }

    private final Map<fURI, Entry> table = new HashMap<>();
    private final Map<fURI, Rec> typeLawsCache = new ConcurrentHashMap<>();

    /**
     * The registered tables, keyed by their instset tid — {@link #typeLawsOf(Type)} dispatches a type to the
     * table of the instset that owns it (longest-prefix wins, so {@code /m/math/nat} routes to {@code /m/math}
     * rather than {@code /m}).
     */
    private static final Map<fURI, LawTable> TABLES = new LinkedHashMap<>();

    /**
     * Self-register this table under the instset whose types and insts it declares laws for.
     */
    protected LawTable(final fURI instsetTid) {
        TABLES.put(instsetTid, this);
    }

    /**
     * Register an inst's declared process laws and inverse.
     */
    protected final void entry(final fURI inst, final fURI inverse, final Law... declared) {
        this.table.put(inst, new Entry(laws(declared), inverse, null));
    }

    /**
     * Register an inst's declared process laws, inverse, and derivation.
     */
    protected final void entry(final fURI inst, final fURI inverse, final Derivation derivation, final Law... declared) {
        this.table.put(inst, new Entry(laws(declared), inverse, derivation));
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
     * The structural theories of the type, resolved from the law table of the instset that owns the type — the
     * dispatcher that lets several sibling law tables share the category without one knowing the others.
     */
    public static Rec typeLawsOf(final Type type) {
        final String vid = type.vid().basePath().toString();
        LawTable owner = null;
        int best = -1;
        for (final Map.Entry<fURI, LawTable> e : TABLES.entrySet()) {
            final String prefix = e.getKey().toString();
            if ((vid.equals(prefix) || vid.startsWith(prefix + "/")) && prefix.length() > best) {
                owner = e.getValue();
                best = prefix.length();
            }
        }
        return null == owner ? rec0() : owner.typeLaws(type);
    }

    /**
     * All registered derivations across every table — the composition rules (lhs ↦ rhs) a rewrite can apply.
     */
    public static List<Derivation> derivations() {
        final List<Derivation> out = new ArrayList<>();
        for (final LawTable table : TABLES.values())
            for (final Entry entry : table.table.values())
                if (null != entry.derivation())
                    out.add(entry.derivation());
        return out;
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
