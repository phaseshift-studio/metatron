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
import studio.phaseshift.metatron.isa.m.type.Call;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Rel;
import studio.phaseshift.metatron.isa.m.type.Type;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.m.mInstSet.GROUP_INST_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.id_;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.sum_;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
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
     * An inst's declared entry together with the n-tid it was declared under — the address a rewrite
     * retypes an unresolved chain instruction with so the instruction's coefficient shape is legible.
     */
    public record Declared(fURI tid, Entry entry) {
    }

    /**
     * The owner table of an address — the table whose instset tid is the longest prefix. Null when none.
     */
    private static LawTable ownerOf(final fURI vid) {
        if (null == vid || null == vid.basePath())
            return null;
        final String base = vid.basePath().toString();
        LawTable owner = null;
        int best = -1;
        for (final Map.Entry<fURI, LawTable> e : TABLES.entrySet()) {
            final String prefix = e.getKey().toString();
            if ((base.equals(prefix) || base.startsWith(prefix + "/")) && prefix.length() > best) {
                owner = e.getValue();
                best = prefix.length();
            }
        }
        return owner;
    }

    /**
     * The declared entry of an op address, disambiguated by the operand type. The rewriter sees code
     * <em>before</em> resolution, so a chain instruction carries only its op address ({@code /m/inst/plus},
     * no dom/rng) and the law table's keys carry the endpoints. When several entries share the op address
     * ({@code plus} over int, real, str, bool, …) the operand's type picks the right one; when nothing
     * matches, null rather than a guess — a law-driven rewrite must never fire on the wrong type.
     * <p>
     * A declaration over EVERY carrier — its dom is {@code #} or a generic ({@code A}) — is not a guess: it holds for
     * this operand too ({@code count} over a stream of anything). A carrier-specific declaration still wins, so an op
     * with typed overloads resolves to its own.
     */
    public static Declared declared(final fURI op, final Type operand) {
        final LawTable owner = ownerOf(op);
        if (null == owner || null == op.basePath())
            return null;
        final String base = op.basePath().toString();
        final String type = (null == operand || null == operand.vid()) ? null : operand.vid().basePath().toString();
        Declared first = null;
        Declared universal = null;
        for (final Map.Entry<fURI, Entry> e : owner.table.entrySet()) {
            if (!e.getKey().basePath().toString().equals(base))
                continue;
            final Declared declared = new Declared(e.getKey(), e.getValue());
            if (null == first)
                first = declared;
            if (null == universal && isUniversal(e.getKey().dom()))
                universal = declared;
            if (null != type && (e.getKey().dom().basePath().toString().equals(type) || e.getKey().rng().basePath().toString().equals(type)))
                return declared;
        }
        return null == type ? first : universal;
    }

    /**
     * Whether a declared dom ranges over every carrier: the universal type {@code #} or a generic variable.
     */
    private static boolean isUniversal(final fURI dom) {
        return null != dom && null != dom.basePath() && (dom.basePath().equals(ALL.basePath()) || dom.basePath().isGeneric());
    }

    /**
     * A chain instruction keyed with its declared n-tid for the carrier it folds — the form {@link #combiner},
     * {@code Form.of} and every declared-law lookup read before resolution. A fold whose argument holds folds,
     * {@code group([k⇒h])}, has those keyed too, since its combiner is computed from theirs. The instruction itself
     * when nothing declares it: an undeclared op matches no law-bearing slot, fail closed.
     */
    public static Inst keyed(final Inst inst, final Type carrier) {
        final Declared declared = declared(inst.tid().basePath(), carrier);
        if (null == declared)
            return inst;
        final Inst typed = inst.tid(declared.tid());
        if (!GROUP_INST_TID.equals(typed.tid().basePath()) || 1 != typed.args().count() || !typed.arg(0).isRec())
            return typed;
        final Map<Obj, Obj> spec = new LinkedHashMap<>();
        typed.arg(0).asRec().elements().forEach(kv -> spec.put(kv.jvm().get0(),
                kv.secondDirect().isInst() ? keyed(kv.secondDirect().asInst(), carrier) : kv.secondDirect()));
        return typed.args(lst(rec(spec)));
    }

    /**
     * THE COMBINER of a fold, computed and never declared: the fold its partials recombine through, so that
     * {@code (a ⊎ b)·r = (a·r ⊎ b·r)·combiner(r)} — Thm 19 with the re-application read off the theory. A stream is a
     * bag, so this is the universal property of the free commutative monoid: a fold that distributes is
     * {@code map(g)} followed by the fold of a commutative monoid, and its partials recombine through that fold.
     * <ul>
     *   <li>a fold declaring the laws itself is its own combiner — {@code sum}, {@code prod} — and the answer is
     *       {@code keyed} itself;</li>
     *   <li>a fold DEFINED as a pointwise map into one — its declared {@link Derivation}, {@code r ↦ map(g)·f} —
     *       recombines through {@code f}, read off {@code r}'s target object (the carrier {@code f} folds), with the
     *       generics bound from {@code keyed}'s own arguments — {@code count ↦ map(1)·sum},
     *       {@code order(B) ↦ split([_])·kmerge(B)};</li>
     *   <li>a fold whose argument holds folds recombines POINTWISE through theirs — {@code group([k⇒h])}, see
     *       {@link #pointwise} — and its combiner is a chain rather than one instruction.</li>
     * </ul>
     * {@code keyed} carries its declared n-tid ({@link #keyed}). Null when the fold has none: {@code mean}
     * (magmadic), {@code str} {@code sum} (not commutative), an undeclared or non-reducing instruction.
     */
    public static Call combiner(final Inst keyed, final Law... laws) {
        final Entry entry = lawsOf(keyed.tid());
        if (null == entry || Inst.Form.reducer != Inst.Form.of(keyed))
            return null;
        if (declaresAll(entry, laws))
            return keyed;
        if (GROUP_INST_TID.equals(keyed.tid().basePath()))
            return pointwise(keyed, laws);
        if (null == entry.derivation())
            return null;
        final Inst fold = entry.derivation().fold(keyed);
        if (null == fold)
            return null;
        // the definition's last step must itself be a FOLD of the target, declaring the laws -- a mapper that
        // happens to be monoidic and commutative (bool plus) recombines nothing
        final Declared declared = declared(fold.tid().basePath(), keyed.rng());
        return null != declared && Inst.Form.reducer == Inst.Form.of(fold.tid(declared.tid())) && declaresAll(declared.entry(), laws) ? fold : null;
    }

    /**
     * THE POINTWISE COMBINER. {@code group([k⇒h])} is a homomorphism from the bag into the recs of {@code h}'s monoid,
     * keyed by {@code k}: the partials merge by key, and each key's partials fold through {@code h}'s own combiner. So
     * it is computed from that combiner, never named — {@code sum()==[_=>combiner(h)]}: the rec {@code sum} (the deep
     * merge, which appends colliding partials into a bag — a lst kept whole, so an {@code order}'s sorted runs reach
     * {@code kmerge} as runs — and reads a bulked partial as its copies), then a per-key fold. Null, so nothing shards,
     * when a value is not a fold with a combiner ({@code mean}, a constant), or when the spec's entries fold differently
     * (a key keeps no record of the entry that claimed it, and the claim falls differently in every part, so even folds
     * with one combiner would mix).
     */
    private static Call pointwise(final Inst keyed, final Law... laws) {
        if (1 != keyed.args().count() || !keyed.arg(0).isRec())
            return null;
        final List<Obj> folds = keyed.arg(0).asRec().elements().map(Rel::secondDirect).toList();
        final Obj fold = folds.isEmpty() ? null : folds.getFirst();
        if (null == fold || !fold.isInst() || !folds.stream().allMatch(f -> f.isInst() && catLiveness.same(f.asInst(), fold.asInst())))
            return null;
        final Obj perKey = perKey(fold, laws);
        return null == perKey ? null : sum_().select_(rec(id_().tryToInst(), perKey));
    }

    /**
     * The per-key fold of one spec value: its combiner, a self-combining fold emitted as the chain wrote it (its op
     * address, resolved where it runs). Null when the value is no fold with a combiner.
     */
    private static Obj perKey(final Obj value, final Law... laws) {
        if (!value.isInst())
            return null;
        final Call fold = combiner(value.asInst(), laws);
        if (null == fold)
            return null;
        return fold == value ? value.asInst().tid(value.asInst().tid().basePath()) : fold;
    }

    /**
     * The combiner under Thm 19's hypothesis — a partial recombines through a commutative monoid's fold.
     */
    public static Call combiner(final Inst keyed) {
        return combiner(keyed, Law.monoidic, Law.commutative);
    }

    /**
     * The combiner as the category names it — the declared n-tid of the edge the partials recombine through, an
     * address as {@code analysis.inverse} is one — or null when the fold has no combiner, or recombines through a
     * chain ({@link #pointwise}) that no single edge names.
     */
    public static fURI combinerOf(final Inst keyed) {
        final Call fold = combiner(keyed);
        if (null == fold || !fold.isInst())
            return null;
        if (fold == keyed)
            return keyed.tid();
        final Declared declared = declared(fold.asInst().tid().basePath(), keyed.rng());
        return null == declared ? null : declared.tid();
    }

    private static boolean declaresAll(final Entry entry, final Law... laws) {
        return Arrays.stream(laws).allMatch(law -> entry.laws().lstValue().stream().anyMatch(l -> l.uriValue().name().equals(law.name())));
    }

    /**
     * The entry of an inst, resolved from the law table of the instset that owns it — the sibling of
     * {@link #typeLawsOf(Type)}, and the oracle a rewrite consults to learn an instruction's declared
     * process laws before firing. Longest-prefix wins, so an inst address under {@code /m/math} routes to
     * the math table rather than the {@code /m} table. Null when no table declares the inst.
     */
    public static Entry lawsOf(final fURI instTid) {
        if (null == instTid)
            return null;
        final String vid = instTid.basePath().toString();
        LawTable owner = null;
        int best = -1;
        for (final Map.Entry<fURI, LawTable> e : TABLES.entrySet()) {
            final String prefix = e.getKey().toString();
            if ((vid.equals(prefix) || vid.startsWith(prefix + "/")) && prefix.length() > best) {
                owner = e.getValue();
                best = prefix.length();
            }
        }
        return null == owner ? null : owner.lookup(instTid);
    }

    /**
     * Whether the inst declares the given process law — the guard a law-driven rewrite tests before firing.
     */
    public static boolean declares(final fURI instTid, final Law law) {
        final Entry entry = lawsOf(instTid);
        return null != entry && entry.laws().lstValue().stream()
                .anyMatch(l -> l.uriValue().name().equals(law.name()));
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
        if (null == type.vid())
            return rec0();                      // a type with no address is listed in no table
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
